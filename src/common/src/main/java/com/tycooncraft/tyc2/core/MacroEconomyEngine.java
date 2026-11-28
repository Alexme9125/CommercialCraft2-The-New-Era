package com.tycooncraft.tyc2.core;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Macro-economic simulation engine modeling global money supply, rolling goods
 * circulation (EMA), inflation/deflation Consumer Price Index (CPI), village
 * liquidity pricing, per-item supply-demand elasticity, seasonal arbitrage
 * pricing, biome specialty discounts, and a 48-entry CPI history ring buffer.
 */
public final class MacroEconomyEngine {

    /** Maximum number of CPI trend snapshots retained in the ring buffer. */
    public static final int MAX_HISTORY_SNAPSHOTS = 48;

    /** Reference batch size used to normalize per-item trade volumes into supply flow units. */
    private static final double ITEM_FLOW_BATCH_SIZE = 32.0;

    /** Minimum per-item supply elasticity factor. */
    public static final double MIN_SUPPLY_FACTOR = 0.55;

    /** Maximum per-item supply elasticity factor. */
    public static final double MAX_SUPPLY_FACTOR = 1.85;

    /** Safety margin applied to gross minting face value when computing village sell arbitrage floors (+10%). */
    public static final double MINT_ARBITRAGE_FLOOR_MARGIN = 1.10;

    private record PrecursorMapping(String targetItemId, int ratio) {
    }

    private record TagPrecursorMapping(String tagSpec, String targetItemId, int ratio) {
    }

    /** Smeltable or craftable precursors whose 1-step transformation produces a mintable metal/alloy. */
    private static final Map<String, PrecursorMapping> MINT_PRECURSOR_ALIASES;

    /** Common-tag precursors protecting modded raw ores and iridium ingots from mint arbitrage. */
    private static final List<TagPrecursorMapping> TAG_PRECURSOR_ALIASES = List.of(
            new TagPrecursorMapping("#raw_materials/iron", "minecraft:iron_ingot", 1),
            new TagPrecursorMapping("#raw_materials/gold", "minecraft:gold_ingot", 1),
            new TagPrecursorMapping("#raw_materials/copper", "minecraft:copper_ingot", 1),
            new TagPrecursorMapping("#raw_materials/tin", "mhera:tin_ingot", 1),
            new TagPrecursorMapping("#raw_materials/silver", "mhera:silver_ingot", 1),
            new TagPrecursorMapping("#raw_materials/lead", "mhera:lead_ingot", 1),
            new TagPrecursorMapping("#ingots/iridium", "mhera:iridium", 1)
    );

    /** Intrinsic per-unit reference values (IC) used to distinguish primary vs secondary ingredients in dual mint recipes. */
    private static final Map<String, Double> DEFAULT_INTRINSIC_UNIT_VALUES;

    static {
        Map<String, PrecursorMapping> m = new LinkedHashMap<>();
        m.put("minecraft:ancient_debris", new PrecursorMapping("minecraft:netherite_scrap", 1));
        m.put("mhera:iridium_shard", new PrecursorMapping("mhera:iridium_ore", 9));
        m.put("ecera:iridium_ingot", new PrecursorMapping("mhera:iridium", 1));
        m.put("minecraft:raw_iron", new PrecursorMapping("minecraft:iron_ingot", 1));
        m.put("minecraft:raw_gold", new PrecursorMapping("minecraft:gold_ingot", 1));
        m.put("minecraft:raw_copper", new PrecursorMapping("minecraft:copper_ingot", 1));
        m.put("mhera:raw_tin", new PrecursorMapping("mhera:tin_ingot", 1));
        m.put("mhera:raw_silver", new PrecursorMapping("mhera:silver_ingot", 1));
        m.put("mhera:raw_lead", new PrecursorMapping("mhera:lead_ingot", 1));
        MINT_PRECURSOR_ALIASES = Collections.unmodifiableMap(m);

        Map<String, Double> iv = new LinkedHashMap<>();
        for (TradeGood g : GoodsCatalog.createDefault().allGoods()) {
            iv.putIfAbsent(g.itemId().trim().toLowerCase(Locale.ROOT), (double) g.basePrice());
        }
        iv.putIfAbsent("minecraft:emerald", 100.0);
        iv.putIfAbsent("mhera:iridium", 1_000.0);
        iv.putIfAbsent("mhera:iridium_ore", 1_000.0);
        iv.putIfAbsent("mhera:mixed_metal_ingot", 100.0);
        iv.putIfAbsent("minecraft:netherite_ingot", 1_000.0);
        iv.putIfAbsent("mhera:refined_iron_ingot", 20.0);
        DEFAULT_INTRINSIC_UNIT_VALUES = Collections.unmodifiableMap(iv);
    }

    private static final MintRecipeRegistry DEFAULT_MINT_REGISTRY = MintRecipeRegistry.createDefault();
    private static volatile MintRecipeRegistry activeMintRecipeRegistry;
    private static volatile MintIngredient.TagMatcher activeMintTagMatcher;

    private MintRecipeRegistry instanceMintRecipeRegistry;
    private MintIngredient.TagMatcher instanceMintTagMatcher;

    /**
     * Qualitative macro-economic regime for GUI indicators and localization.
     */
    public enum InflationState {
        DEFLATION,
        STABLE,
        INFLATION,
        HYPERINFLATION
    }

    /**
     * Historical point recorded once per demand cycle for rendering the CPI trend chart.
     *
     * @param timestamp world tick or epoch millis
     * @param cpiIndex computed global CPI index
     * @param moneySupply total active money supply M
     * @param goodsCirculation rolling goods circulation Q
     */
    public record PriceSnapshot(
            long timestamp,
            double cpiIndex,
            long moneySupply,
            double goodsCirculation
    ) {
        /**
         * Exports this snapshot into a JSON-compatible map.
         *
         * @return map representation
         */
        public Map<String, Object> toDataMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("timestamp", timestamp);
            map.put("cpiIndex", cpiIndex);
            map.put("moneySupply", moneySupply);
            map.put("goodsCirculation", goodsCirculation);
            return map;
        }

        /**
         * Parses a snapshot from a JSON-compatible map.
         *
         * @param map source map
         * @return parsed snapshot
         */
        public static PriceSnapshot fromDataMap(Map<String, Object> map) {
            double cpi = JsonLite.getDouble(map, "cpiIndex", 1.0);
            double goods = JsonLite.getDouble(map, "goodsCirculation", 0.0);
            return new PriceSnapshot(
                    JsonLite.getLong(map, "timestamp", 0L),
                    Double.isFinite(cpi) && cpi > 0.0 ? cpi : 1.0,
                    Math.max(0L, JsonLite.getLong(map, "moneySupply", 0L)),
                    Double.isFinite(goods) && goods >= 0.0 ? goods : 0.0
            );
        }
    }

    /**
     * Detailed breakdown of a quoted unit price including every economic factor.
     *
     * @param finalUnitPrice final clamped unit price in IC (>= 1)
     * @param baseUnitPrice catalog base unit price in IC
     * @param combinedMultiplier final clamped combined multiplier
     * @param cpiIndex global CPI factor
     * @param villageFactor village liquidity factor
     * @param supplyFactor per-item supply-demand elasticity factor
     * @param seasonFactor seasonal arbitrage factor
     * @param biomeFactor biome specialty discount factor
     */
    public record PriceQuote(
            long finalUnitPrice,
            long baseUnitPrice,
            double combinedMultiplier,
            double cpiIndex,
            double villageFactor,
            double supplyFactor,
            double seasonFactor,
            double biomeFactor
    ) {
    }

    private TyC2Config config;
    private long totalMintedNet;
    private long playerAndGuildBankTotal;
    private long villageBankTotal;
    private long cumulativeMinted;
    private long cumulativeSinks;
    private double rollingGoodsCirculation;
    private int partneredVillageCount = 1;
    private long lastDemandCycleTick = Long.MIN_VALUE;
    private final Map<String, Double> netSupplyFlow = new LinkedHashMap<>();
    private final Deque<PriceSnapshot> history = new ArrayDeque<>(MAX_HISTORY_SNAPSHOTS);

    /**
     * Creates a macro-economy engine using default {@link TyC2Config}.
     */
    public MacroEconomyEngine() {
        this(TyC2Config.defaults());
    }

    /**
     * Creates a macro-economy engine with the supplied configuration.
     *
     * @param config active configuration
     */
    public MacroEconomyEngine(TyC2Config config) {
        this.config = config == null ? TyC2Config.defaults() : config;
    }

    /**
     * Updates the configuration reference used for guardrails and decay factors.
     *
     * @param config active configuration
     */
    public void setConfig(TyC2Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /** @return active configuration */
    public TyC2Config config() {
        return config;
    }

    /**
     * Records newly minted coins entering circulation from a Minting Machine.
     * Increases {@code totalMintedNet} and global money supply {@code M}, applying inflationary pressure.
     *
     * @param faceValueTotal total face value minted in IC (ignored if {@code <= 0})
     */
    public void recordMinting(long faceValueTotal) {
        if (faceValueTotal <= 0L) {
            return;
        }
        totalMintedNet = saturatedAdd(totalMintedNet, faceValueTotal);
        cumulativeMinted = saturatedAdd(cumulativeMinted, faceValueTotal);
    }

    /**
     * Shifts physical cash deposited into an ATM from unbanked circulation into bank balances
     * without changing total active money supply {@code M}.
     *
     * @param amount positive IC amount deposited
     */
    public void recordCashDepositedToBank(long amount) {
        if (amount <= 0L) {
            return;
        }
        totalMintedNet = Math.max(0L, totalMintedNet - amount);
        playerAndGuildBankTotal = saturatedAdd(playerAndGuildBankTotal, amount);
    }

    /**
     * Shifts bank balance withdrawn as cash at an ATM back into unbanked circulation
     * without changing total active money supply {@code M}.
     *
     * @param amount positive IC amount withdrawn
     */
    public void recordCashWithdrawnFromBank(long amount) {
        if (amount <= 0L) {
            return;
        }
        playerAndGuildBankTotal = Math.max(0L, playerAndGuildBankTotal - amount);
        totalMintedNet = saturatedAdd(totalMintedNet, amount);
    }

    /**
     * Shifts physical cash paid by a player into a village treasury (e.g. cash partnership signup
     * or cash sell-order purchase) from {@code totalMintedNet} into {@code villageBankTotal}
     * without distorting total active money supply {@code M}.
     *
     * @param amount positive IC amount paid in cash to a village
     */
    public void recordCashPaidToVillage(long amount) {
        if (amount <= 0L) {
            return;
        }
        totalMintedNet = Math.max(0L, totalMintedNet - amount);
        villageBankTotal = saturatedAdd(villageBankTotal, amount);
    }

    /**
     * Shifts currency paid in physical cash by a village to a player (e.g. cash buy-order payout)
     * from {@code villageBankTotal} back into {@code totalMintedNet} without distorting {@code M}.
     *
     * @param amount positive IC amount paid in cash by a village
     */
    public void recordVillagePaidCash(long amount) {
        if (amount <= 0L) {
            return;
        }
        villageBankTotal = Math.max(0L, villageBankTotal - amount);
        totalMintedNet = saturatedAdd(totalMintedNet, amount);
    }

    /**
     * Synchronizes tracked bank and village account totals from live SavedData state.
     *
     * @param playerAndGuildTotal combined balances of all personal and company bank accounts
     * @param villageTotal combined balances of all partnered villages
     * @param partneredVillages count of currently partnered villages
     */
    public void syncAccountTotals(long playerAndGuildTotal, long villageTotal, int partneredVillages) {
        this.playerAndGuildBankTotal = Math.max(0L, playerAndGuildTotal);
        this.villageBankTotal = Math.max(0L, villageTotal);
        this.partneredVillageCount = Math.max(1, partneredVillages);
    }

    /**
     * Sets the count of partnered villages used to scale baseline reference money supply {@code M_base}.
     *
     * @param partneredVillages partnered village count (clamped >= 1)
     */
    public void setPartneredVillageCount(int partneredVillages) {
        this.partneredVillageCount = Math.max(1, partneredVillages);
    }

    /**
     * Records a deflationary money sink burning currency from circulation
     * (such as village level-up capital expenditure, expansion construction cost, or municipal upkeep).
     *
     * <p>Reduces {@code totalMintedNet} first, then {@code villageBankTotal} if needed, so
     * both standalone cash sinks and village-reserve sinks visibly reduce {@code M}.
     *
     * @param amount positive IC burned
     */
    public void recordMoneySink(long amount) {
        if (amount <= 0L) {
            return;
        }
        cumulativeSinks = saturatedAdd(cumulativeSinks, amount);
        long remaining = amount;
        if (totalMintedNet > 0L) {
            long fromCash = Math.min(totalMintedNet, remaining);
            totalMintedNet -= fromCash;
            remaining -= fromCash;
        }
        if (remaining > 0L && villageBankTotal > 0L) {
            long fromVillage = Math.min(villageBankTotal, remaining);
            villageBankTotal -= fromVillage;
        }
    }

    /**
     * Records a deflationary burn explicitly paid out of a village's balance.
     *
     * @param amount positive IC burned from village treasury
     */
    public void recordVillageMoneySink(long amount) {
        if (amount <= 0L) {
            return;
        }
        cumulativeSinks = saturatedAdd(cumulativeSinks, amount);
        villageBankTotal = Math.max(0L, villageBankTotal - amount);
    }

    /**
     * Records a deflationary burn explicitly paid out of a player or guild bank account
     * (such as bank-funded village aid construction) without altering unbanked cash {@code totalMintedNet}.
     *
     * @param amount positive IC burned from player/company bank balance
     */
    public void recordBankMoneySink(long amount) {
        if (amount <= 0L) {
            return;
        }
        cumulativeSinks = saturatedAdd(cumulativeSinks, amount);
        playerAndGuildBankTotal = Math.max(0L, playerAndGuildBankTotal - amount);
    }

    /**
     * Reverses a previously recorded deflationary cash money sink (e.g. when a player cancels
     * an aid construction project prior to block placement and receives a full refund),
     * decrementing {@code cumulativeSinks} and restoring {@code totalMintedNet} so global money
     * supply {@code M} remains conserved and never drops below 0.
     *
     * @param amount positive IC amount refunded (ignored if {@code <= 0})
     */
    public void refundMoneySink(long amount) {
        if (amount <= 0L) {
            return;
        }
        long reversed = Math.min(cumulativeSinks, amount);
        cumulativeSinks = Math.max(0L, cumulativeSinks - reversed);
        totalMintedNet = saturatedAdd(totalMintedNet, reversed);
    }

    /**
     * Reverses a previously recorded bank-funded deflationary money sink, restoring
     * {@code playerAndGuildBankTotal} and decrementing {@code cumulativeSinks} without inflating
     * unbanked cash {@code totalMintedNet}.
     *
     * @param amount positive IC amount refunded to a bank account (ignored if {@code <= 0})
     */
    public void refundBankMoneySink(long amount) {
        if (amount <= 0L) {
            return;
        }
        long reversed = Math.min(cumulativeSinks, amount);
        cumulativeSinks = Math.max(0L, cumulativeSinks - reversed);
        playerAndGuildBankTotal = saturatedAdd(playerAndGuildBankTotal, reversed);
    }

    /**
     * Reverses a previously recorded aid money sink into either unbanked cash ({@code refundedToCash = true})
     * or tracked player/company bank balances ({@code refundedToCash = false}).
     *
     * @param amount positive IC amount refunded
     * @param refundedToCash {@code true} for cash coin refund, {@code false} for bank deposit refund
     */
    public void refundMoneySink(long amount, boolean refundedToCash) {
        if (refundedToCash) {
            refundMoneySink(amount);
        } else {
            refundBankMoneySink(amount);
        }
    }

    /**
     * Records a completed trade transaction, increasing rolling goods circulation {@code Q}
     * and shifting per-item {@code netSupplyFlow}.
     *
     * @param itemId traded item ID
     * @param quantity item count traded (>= 1)
     * @param baseUnitPrice catalog base unit price in IC (>= 1)
     * @param playerSoldToVillage {@code true} if player sold into village (increases supply -> lowers price);
     *                            {@code false} if player bought from village (increases scarcity -> raises price)
     */
    public void recordTrade(String itemId, int quantity, long baseUnitPrice, boolean playerSoldToVillage) {
        if (quantity <= 0 || baseUnitPrice <= 0L) {
            return;
        }
        double tradeBaseValue = (double) quantity * baseUnitPrice;
        if (Double.isFinite(tradeBaseValue) && tradeBaseValue > 0.0) {
            double nextQ = rollingGoodsCirculation + tradeBaseValue;
            rollingGoodsCirculation = Double.isFinite(nextQ) ? Math.min(nextQ, 1.0e15) : 1.0e15;
        }

        if (itemId != null && !itemId.isBlank()) {
            String key = itemId.trim().toLowerCase(Locale.ROOT);
            double normalizedDelta = quantity / ITEM_FLOW_BATCH_SIZE;
            double signedDelta = playerSoldToVillage ? normalizedDelta : -normalizedDelta;
            netSupplyFlow.merge(key, signedDelta, (a, b) -> {
                double sum = a + b;
                return Double.isFinite(sum) ? clamp(sum, -250.0, 250.0) : 0.0;
            });
        }
    }

    /**
     * Executes end-of-cycle economic decay only when at least {@code minIntervalTicks} have elapsed
     * since the last global demand cycle, preventing N staggered partnered villages from decaying
     * global {@code Q} N times per cycle.
     *
     * @param timestamp current world game tick
     * @param minIntervalTicks minimum tick interval between global decays (e.g. {@code config.demandRefreshTicks()})
     * @return recorded snapshot if decay ran, otherwise empty
     */
    public Optional<PriceSnapshot> advanceDemandCycleIfDue(long timestamp, long minIntervalTicks) {
        long interval = Math.max(1L, minIntervalTicks);
        if (lastDemandCycleTick != Long.MIN_VALUE
                && timestamp >= lastDemandCycleTick
                && (timestamp - lastDemandCycleTick) < interval) {
            return Optional.empty();
        }
        return Optional.of(advanceDemandCycle(timestamp));
    }

    /**
     * Executes end-of-cycle economic decay (EMA decay on {@code Q}, 10% mean reversion on per-item
     * {@code netSupplyFlow}) and appends a new {@link PriceSnapshot} to the 48-entry ring buffer.
     *
     * @param timestamp world tick or epoch millis
     * @return newly recorded snapshot
     */
    public PriceSnapshot advanceDemandCycle(long timestamp) {
        lastDemandCycleTick = timestamp;
        if (!Double.isFinite(rollingGoodsCirculation) || rollingGoodsCirculation < 0.0) {
            rollingGoodsCirculation = 0.0;
        }
        rollingGoodsCirculation *= (1.0 - config.goodsCirculationDecay());
        if (!Double.isFinite(rollingGoodsCirculation) || rollingGoodsCirculation < 1e-4) {
            rollingGoodsCirculation = 0.0;
        }

        double retain = 1.0 - config.itemSupplyFlowDecay();
        Iterator<Map.Entry<String, Double>> it = netSupplyFlow.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Double> entry = it.next();
            Double rawVal = entry.getValue();
            double decayed = (rawVal == null || !Double.isFinite(rawVal)) ? 0.0 : rawVal * retain;
            if (!Double.isFinite(decayed) || Math.abs(decayed) < 1e-4) {
                it.remove();
            } else {
                entry.setValue(decayed);
            }
        }
        return recordSnapshot(timestamp);
    }

    /**
     * Appends the current macro-economic state into the 48-entry history ring buffer.
     *
     * @param timestamp world tick or epoch millis
     * @return recorded snapshot
     */
    public PriceSnapshot recordSnapshot(long timestamp) {
        PriceSnapshot snap = new PriceSnapshot(
                timestamp,
                computeCpi(),
                totalMoneySupply(),
                rollingGoodsCirculation
        );
        while (history.size() >= MAX_HISTORY_SNAPSHOTS) {
            history.removeFirst();
        }
        history.addLast(snap);
        return snap;
    }

    /**
     * Returns total active money supply {@code M = totalMintedNet + playerAndGuildBankTotal + villageBankTotal}.
     *
     * @return total active money supply in IC
     */
    public long totalMoneySupply() {
        long sum = Math.max(0L, totalMintedNet);
        sum = saturatedAdd(sum, Math.max(0L, playerAndGuildBankTotal));
        sum = saturatedAdd(sum, Math.max(0L, villageBankTotal));
        return sum;
    }

    /**
     * Returns baseline reference money supply {@code M_base = baselineMoneyPerVillage * max(1, partneredVillageCount)}.
     *
     * @return reference money supply
     */
    public double baselineMoneySupply() {
        return config.baselineMoneyPerVillage() * Math.max(1, partneredVillageCount);
    }

    /**
     * Computes global Consumer Price Index (CPI):
     * {@code clamp(((M + 0.5 * M_base) / (Q + 0.5 * M_base))^0.55, minMultiplier, maxMultiplier)}.
     *
     * @return clamped global CPI index
     */
    public double computeCpi() {
        double m = totalMoneySupply();
        double q = Double.isFinite(rollingGoodsCirculation) ? Math.max(0.0, rollingGoodsCirculation) : 0.0;
        double damper = Math.max(1.0, 0.5 * baselineMoneySupply());
        double ratio = (m + damper) / (q + damper);
        if (!Double.isFinite(ratio) || ratio <= 0.0) {
            return clamp(1.0, config.minPriceMultiplier(), config.maxPriceMultiplier());
        }
        double raw = Math.pow(ratio, config.cpiExponent());
        return clamp(raw, config.minPriceMultiplier(), config.maxPriceMultiplier());
    }

    /**
     * Classifies the current CPI into a macro-economic regime for GUI display.
     *
     * @return current {@link InflationState}
     */
    public InflationState getInflationState() {
        double cpi = computeCpi();
        if (cpi < 0.92) {
            return InflationState.DEFLATION;
        }
        if (cpi <= 1.08) {
            return InflationState.STABLE;
        }
        if (cpi <= 1.80) {
            return InflationState.INFLATION;
        }
        return InflationState.HYPERINFLATION;
    }

    /**
     * Computes target municipal reserve {@code R_target(level) = 350 * clamped(level)^1.8}.
     *
     * @param villageLevel village level (1..10)
     * @return target reserve in IC
     */
    public static double targetVillageReserve(int villageLevel) {
        int clamped = Math.max(1, Math.min(10, villageLevel));
        return 350.0 * Math.pow(clamped, 1.8);
    }

    /**
     * Computes the village liquidity pricing factor {@code F_village(v)}:
     * <ul>
     *   <li>Village Buy Orders: {@code [0.75, 1.25]} anchored at {@code 1.00} when {@code balance == R_target}.</li>
     *   <li>Village Sell Orders: {@code [0.80, 1.20]} anchored at {@code 1.00} when {@code balance == R_target}.</li>
     * </ul>
     *
     * @param villageLevel village level (1..10)
     * @param villageBalance current village balance in IC
     * @param isVillageBuying {@code true} for Village Buy Orders, {@code false} for Village Sell Orders
     * @return liquidity multiplier
     */
    public double computeVillageLiquidityFactor(int villageLevel, long villageBalance, boolean isVillageBuying) {
        double ratio = Math.max(0L, villageBalance) / targetVillageReserve(villageLevel);
        if (isVillageBuying) {
            return clamp(0.75 + 0.25 * ratio, 0.75, 1.25);
        }
        return clamp(0.80 + 0.20 * ratio, 0.80, 1.20);
    }

    /**
     * Computes per-item supply-demand elasticity factor {@code F_supply(i) = clamp(exp(-0.08 * netFlow), 0.55, 1.85)}.
     *
     * @param itemId item identifier
     * @return elasticity multiplier
     */
    public double computeSupplyElasticityFactor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 1.0;
        }
        double flow = netSupplyFlow.getOrDefault(itemId.trim().toLowerCase(Locale.ROOT), 0.0);
        return clamp(Math.exp(-0.08 * flow), MIN_SUPPLY_FACTOR, MAX_SUPPLY_FACTOR);
    }

    /**
     * Computes seasonal arbitrage and seasonal demand pricing factor {@code F_season(i, s)}:
     * <ul>
     *   <li>In-season seasonal crop sell by village: {@code 0.75x} harvest glut discount.</li>
     *   <li>In-season seasonal crop buy by village: {@code 0.90x} normal fresh demand.</li>
     *   <li>Off-season seasonal crop buy by village: {@code 1.40x..1.65x} scarcity premium ({@code 1.65x} in Winter,
     *       {@code 1.55x} for single-season specialty crops, {@code 1.40x} otherwise).</li>
     *   <li>Non-crop goods with {@link TradeGood#hasDemandSeason(Season)} when village is buying:
     *       {@code 1.20x} seasonal demand bonus (e.g., winter fuel and hearty warm food).</li>
     *   <li>Season {@link Season#NONE} or seasonal arbitrage disabled: {@code 1.00x}.</li>
     * </ul>
     *
     * @param good trade good
     * @param currentSeason active season
     * @param isVillageBuying {@code true} for Village Buy Order, {@code false} for Village Sell Order
     * @return seasonal multiplier
     */
    public double computeSeasonFactor(TradeGood good, Season currentSeason, boolean isVillageBuying) {
        if (good == null || !config.enableSeasonalArbitrage() || currentSeason == null || !currentSeason.isActive()) {
            return 1.0;
        }
        if (!good.isSeasonalCrop()) {
            if (isVillageBuying && good.hasDemandSeason(currentSeason)) {
                return 1.20;
            }
            return 1.0;
        }
        if (good.isInSeason(currentSeason)) {
            return isVillageBuying ? 0.90 : 0.75;
        }
        // Off-season scarcity premium (1.40x..1.65x)
        if (isVillageBuying) {
            if (currentSeason == Season.WINTER) {
                return 1.65;
            }
            if (good.harvestSeasons().size() == 1) {
                return 1.55;
            }
            return 1.40;
        }
        return 1.40;
    }

    /**
     * Computes biome specialty pricing factor {@code F_biome(i, v)}:
     * <ul>
     *   <li>Village Sell Orders matching {@link TradeGood#hasBiomeAffinity(String)}: {@code 0.88x} (-12% local specialty discount).</li>
     *   <li>Village Buy Orders matching {@link TradeGood#hasDemandBiome(String)}: {@code 1.15x} (+15% local shortage bonus).</li>
     *   <li>Otherwise: {@code 1.00x}.</li>
     * </ul>
     *
     * @param good trade good
     * @param biomeId village biome ID or style key
     * @param isVillageBuying {@code true} for Village Buy Order, {@code false} for Village Sell Order
     * @return biome factor
     */
    public double computeBiomeFactor(TradeGood good, String biomeId, boolean isVillageBuying) {
        if (good == null) {
            return 1.0;
        }
        if (isVillageBuying) {
            return good.hasDemandBiome(biomeId) ? 1.15 : 1.0;
        }
        return good.hasBiomeAffinity(biomeId) ? 0.88 : 1.0;
    }

    /**
     * Quotes the complete dynamic unit price for {@code good} at a specific village and season,
     * clamping the combined multiplier to {@code [config.minPriceMultiplier, config.maxPriceMultiplier]}
     * and ensuring a minimum unit price of {@code 1 IC}.
     *
     * @param good trade good
     * @param villageLevel village level (1..10)
     * @param villageBalance village balance in IC
     * @param biomeId village biome ID or style key
     * @param currentSeason current season
     * @param isVillageBuying {@code true} for Village Buy Order, {@code false} for Village Sell Order
     * @return complete {@link PriceQuote}
     */
    public PriceQuote quotePrice(
            TradeGood good,
            int villageLevel,
            long villageBalance,
            String biomeId,
            Season currentSeason,
            boolean isVillageBuying
    ) {
        Objects.requireNonNull(good, "good must not be null");
        double cpi = computeCpi();
        double fVillage = computeVillageLiquidityFactor(villageLevel, villageBalance, isVillageBuying);
        double fSupply = computeSupplyElasticityFactor(good.itemId());
        double fSeason = computeSeasonFactor(good, currentSeason, isVillageBuying);
        double fBiome = computeBiomeFactor(good, biomeId, isVillageBuying);

        double rawCombined = cpi * fVillage * fSupply * fSeason * fBiome;
        double clamped = clamp(rawCombined, config.minPriceMultiplier(), config.maxPriceMultiplier());
        long finalPrice = Math.max(1L, Math.round(good.basePrice() * clamped));
        if (!isVillageBuying) {
            long mintFloor = computeMintingArbitrageFloor(good.itemId());
            if (mintFloor > finalPrice) {
                finalPrice = mintFloor;
            }
        }
        return new PriceQuote(finalPrice, good.basePrice(), clamped, cpi, fVillage, fSupply, fSeason, fBiome);
    }

    /**
     * Computes periodic municipal upkeep (deflationary burn) on excess village reserves above {@code R_target}.
     *
     * @param villageLevel village level (1..10)
     * @param villageBalance current village balance in IC
     * @return upkeep amount to burn in IC (>= 0)
     */
    public long computeMunicipalUpkeep(int villageLevel, long villageBalance) {
        long target = Math.round(targetVillageReserve(villageLevel));
        if (villageBalance <= target || config.municipalUpkeepRate() <= 0.0) {
            return 0L;
        }
        return Math.max(1L, Math.round((villageBalance - target) * config.municipalUpkeepRate()));
    }

    /** @return net unbanked minted cash in circulation */
    public long totalMintedNet() {
        return totalMintedNet;
    }

    /** @return tracked combined player and guild bank balances */
    public long playerAndGuildBankTotal() {
        return playerAndGuildBankTotal;
    }

    /** @return tracked combined village balances */
    public long villageBankTotal() {
        return villageBankTotal;
    }

    /** @return cumulative coins ever minted */
    public long cumulativeMinted() {
        return cumulativeMinted;
    }

    /** @return cumulative currency burned in deflationary sinks */
    public long cumulativeSinks() {
        return cumulativeSinks;
    }

    /** @return rolling goods circulation EMA Q */
    public double rollingGoodsCirculation() {
        return rollingGoodsCirculation;
    }

    /** @return tracked partnered village count (>= 1) */
    public int partneredVillageCount() {
        return partneredVillageCount;
    }

    /**
     * Returns current net supply flow for an item ID.
     *
     * @param itemId item ID
     * @return net supply flow (0.0 if unseen)
     */
    public double getNetSupplyFlow(String itemId) {
        if (itemId == null) {
            return 0.0;
        }
        return netSupplyFlow.getOrDefault(itemId.trim().toLowerCase(Locale.ROOT), 0.0);
    }

    /** @return unmodifiable view of per-item supply flows */
    public Map<String, Double> allNetSupplyFlows() {
        return Collections.unmodifiableMap(netSupplyFlow);
    }

    /** @return unmodifiable chronological list of up to 48 historical CPI snapshots */
    public List<PriceSnapshot> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * Exports macro-economy state into a structured pure-Java map for SavedData/JSON persistence.
     *
     * @return structured data map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalMintedNet", totalMintedNet);
        map.put("playerAndGuildBankTotal", playerAndGuildBankTotal);
        map.put("villageBankTotal", villageBankTotal);
        map.put("cumulativeMinted", cumulativeMinted);
        map.put("cumulativeSinks", cumulativeSinks);
        map.put("rollingGoodsCirculation", Double.isFinite(rollingGoodsCirculation) ? rollingGoodsCirculation : 0.0);
        map.put("partneredVillageCount", partneredVillageCount);
        if (lastDemandCycleTick != Long.MIN_VALUE) {
            map.put("lastDemandCycleTick", lastDemandCycleTick);
        }
        map.put("netSupplyFlow", new LinkedHashMap<>(netSupplyFlow));

        List<Map<String, Object>> snaps = new ArrayList<>(history.size());
        for (PriceSnapshot snap : history) {
            snaps.add(snap.toDataMap());
        }
        map.put("history", snaps);
        return map;
    }

    /**
     * Serializes macro-economy state into compact JSON.
     *
     * @return JSON string
     */
    public String toJson() {
        return JsonLite.toJson(toDataMap());
    }

    /**
     * Restores a {@link MacroEconomyEngine} from a persisted map.
     *
     * @param map persisted data map
     * @param config active configuration
     * @return restored engine
     */
    public static MacroEconomyEngine fromDataMap(Map<String, Object> map, TyC2Config config) {
        MacroEconomyEngine engine = new MacroEconomyEngine(config);
        if (map == null) {
            return engine;
        }
        engine.totalMintedNet = Math.max(0L, JsonLite.getLong(map, "totalMintedNet", 0L));
        engine.playerAndGuildBankTotal = Math.max(0L, JsonLite.getLong(map, "playerAndGuildBankTotal", 0L));
        engine.villageBankTotal = Math.max(0L, JsonLite.getLong(map, "villageBankTotal", 0L));
        engine.cumulativeMinted = Math.max(0L, JsonLite.getLong(map, "cumulativeMinted", 0L));
        engine.cumulativeSinks = Math.max(0L, JsonLite.getLong(map, "cumulativeSinks", 0L));
        double loadedQ = JsonLite.getDouble(map, "rollingGoodsCirculation", 0.0);
        engine.rollingGoodsCirculation = (Double.isFinite(loadedQ) && loadedQ >= 0.0) ? loadedQ : 0.0;
        engine.partneredVillageCount = Math.max(1, JsonLite.getInt(map, "partneredVillageCount", 1));
        if (map.containsKey("lastDemandCycleTick")) {
            engine.lastDemandCycleTick = JsonLite.getLong(map, "lastDemandCycleTick", Long.MIN_VALUE);
        }

        Map<String, Object> flows = JsonLite.asObject(map.get("netSupplyFlow"));
        if (flows != null) {
            for (Map.Entry<String, Object> entry : flows.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    double v = ((Number) entry.getValue()).doubleValue();
                    if (Double.isFinite(v)) {
                        engine.netSupplyFlow.put(entry.getKey(), clamp(v, -250.0, 250.0));
                    }
                }
            }
        }

        List<Object> snaps = JsonLite.asArray(map.get("history"));
        if (snaps != null) {
            for (Object sObj : snaps) {
                Map<String, Object> sMap = JsonLite.asObject(sObj);
                if (sMap != null) {
                    while (engine.history.size() >= MAX_HISTORY_SNAPSHOTS) {
                        engine.history.removeFirst();
                    }
                    engine.history.addLast(PriceSnapshot.fromDataMap(sMap));
                }
            }
        }
        return engine;
    }

    /**
     * Restores a {@link MacroEconomyEngine} from JSON text.
     *
     * @param json JSON object text
     * @param config active configuration
     * @return restored engine
     */
    public static MacroEconomyEngine fromJson(String json, TyC2Config config) {
        return fromDataMap(JsonLite.parseObject(json), config);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return Math.max(min, Math.min(max, 1.0));
        }
        return Math.max(min, Math.min(max, value));
    }

    private static long saturatedAdd(long a, long b) {
        if (b <= 0L) {
            return Math.max(0L, a + b);
        }
        if (a <= 0L) {
            return b;
        }
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    /**
     * Binds the active runtime {@link MintRecipeRegistry} and optional platform {@link MintIngredient.TagMatcher}
     * so dynamic village sell-price arbitrage floors stay synchronized with datapacks and external configs.
     *
     * @param registry active mint recipe registry (null resets to built-in default fallback)
     * @param tagMatcher optional platform tag matcher (nullable)
     */
    public static void setActiveMintRecipeRegistry(
            MintRecipeRegistry registry,
            MintIngredient.TagMatcher tagMatcher) {
        activeMintRecipeRegistry = registry;
        activeMintTagMatcher = tagMatcher;
    }

    /**
     * Overrides the {@link MintRecipeRegistry} and optional {@link MintIngredient.TagMatcher} for this
     * engine instance (primarily for isolated testing and custom environments).
     *
     * @param registry per-instance mint recipe registry (null delegates to active/default registry)
     * @param tagMatcher optional tag matcher (nullable)
     * @return this engine
     */
    public MacroEconomyEngine setMintRecipeRegistry(
            MintRecipeRegistry registry,
            MintIngredient.TagMatcher tagMatcher) {
        this.instanceMintRecipeRegistry = registry;
        this.instanceMintTagMatcher = tagMatcher;
        return this;
    }

    /**
     * Computes a dynamic hard minimum village sell price for any mintable metal, alloy, or precursor
     * from the active {@link MintRecipeRegistry} using exact integer ceiling arithmetic:
     * {@code ceil((grossValue * 11) / (denom * 10))} (a +10% safety margin with zero IEEE-754
     * rounding distortion).
     *
     * <p>All 1-ingredient recipes apply their gross floor directly. Dual-ingredient recipes sum both
     * slot counts when the same item fills both slots (e.g. 2x Iron), and otherwise apply the gross
     * floor to the primary (higher intrinsic value) ingredient — or to both ingredients when their
     * per-unit intrinsic values are within 2x of each other (so cheap co-ingredients such as
     * {@code ic2:alloy} alongside {@code ic2:iridium}, or {@code minecraft:gold_ingot} alongside
     * {@code minecraft:netherite_scrap}, are not artificially inflated to the rare metal's floor).
     *
     * <p>Covers all built-in recipes, smeltable/craftable precursors (e.g., {@code ancient_debris},
     * {@code ic2:iridium_shard}, {@code ecc2:iridium_ingot}), {@link com.tycooncraft.tyc2.api.TyC2RegistryApi}
     * recipes, and datapack/custom mint recipes, falling back safely to built-in defaults when no
     * runtime registry has been bound.
     *
     * @param itemId candidate village-sold item ID
     * @return minimum safe unit sell price in IC ({@code >= 1})
     */
    public long computeMintingArbitrageFloor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 1L;
        }
        String norm = itemId.trim().toLowerCase(Locale.ROOT);
        MintRecipeRegistry registry = this.instanceMintRecipeRegistry != null
                ? this.instanceMintRecipeRegistry
                : (activeMintRecipeRegistry != null ? activeMintRecipeRegistry : DEFAULT_MINT_REGISTRY);
        MintIngredient.TagMatcher matcher = this.instanceMintTagMatcher != null
                ? this.instanceMintTagMatcher
                : activeMintTagMatcher;

        long floor = computeDynamicGrossFloor(registry, norm, 1, matcher);
        PrecursorMapping precursor = MINT_PRECURSOR_ALIASES.get(norm);
        if (precursor != null) {
            long precursorFloor = computeDynamicGrossFloor(
                    registry, precursor.targetItemId(), precursor.ratio(), matcher
            );
            if (precursorFloor > floor) {
                floor = precursorFloor;
            }
        }
        if (matcher != null) {
            for (TagPrecursorMapping tp : TAG_PRECURSOR_ALIASES) {
                if (CommonTagAliases.matchesTagSpec(norm, tp.tagSpec(), matcher)) {
                    long tagFloor = computeDynamicGrossFloor(
                            registry, tp.targetItemId(), tp.ratio(), matcher
                    );
                    if (tagFloor > floor) {
                        floor = tagFloor;
                    }
                }
            }
        }
        return Math.max(1L, floor);
    }

    private static long computeDynamicGrossFloor(
            MintRecipeRegistry registry,
            String itemId,
            int precursorRatio,
            MintIngredient.TagMatcher matcher) {
        long best = 1L;
        long safeRatio = Math.max(1L, precursorRatio);
        List<MintRecipe> allRecipes = registry.allRecipes();
        Map<String, Double> soloUnitValues = buildSoloMintUnitValues(allRecipes);

        for (MintRecipe recipe : allRecipes) {
            long grossValue = recipe.totalFaceValue();
            if (grossValue <= 0L) {
                continue;
            }
            List<MintIngredient> ings = recipe.ingredients();
            if (ings.size() == 1) {
                MintIngredient only = ings.get(0);
                if (only.matchesItem(itemId, matcher)) {
                    long candidate = ceilGrossWithTenPercentMargin(grossValue, (long) only.count() * safeRatio);
                    if (candidate > best) {
                        best = candidate;
                    }
                }
            } else if (ings.size() == 2) {
                MintIngredient a = ings.get(0);
                MintIngredient b = ings.get(1);
                boolean mA = a.matchesItem(itemId, matcher);
                boolean mB = b.matchesItem(itemId, matcher);
                if (!mA && !mB) {
                    continue;
                }
                if (mA && mB) {
                    long totalCount = (long) a.count() + (long) b.count();
                    long candidate = ceilGrossWithTenPercentMargin(grossValue, totalCount * safeRatio);
                    if (candidate > best) {
                        best = candidate;
                    }
                } else {
                    MintIngredient matched = mA ? a : b;
                    MintIngredient other = mA ? b : a;
                    if (shouldApplyDualGrossFloor(matched, other, grossValue, soloUnitValues, allRecipes, matcher)) {
                        long candidate = ceilGrossWithTenPercentMargin(grossValue, (long) matched.count() * safeRatio);
                        if (candidate > best) {
                            best = candidate;
                        }
                    }
                }
            } else {
                long totalCount = 0L;
                for (MintIngredient ing : ings) {
                    if (ing.matchesItem(itemId, matcher)) {
                        totalCount += ing.count();
                    }
                }
                if (totalCount > 0L) {
                    long candidate = ceilGrossWithTenPercentMargin(grossValue, totalCount * safeRatio);
                    if (candidate > best) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static Map<String, Double> buildSoloMintUnitValues(List<MintRecipe> allRecipes) {
        Map<String, Double> solo = new LinkedHashMap<>();
        for (MintRecipe recipe : allRecipes) {
            if (recipe.ingredients().size() != 1 || recipe.totalFaceValue() <= 0L) {
                continue;
            }
            MintIngredient ing = recipe.ingredients().get(0);
            double unitVal = recipe.totalFaceValue() / (double) ing.count();
            solo.merge(ing.spec(), unitVal, Math::max);
            solo.merge(ing.representativeItemId(), unitVal, Math::max);
        }
        return solo;
    }

    private static double estimateIngredientIntrinsicUnitValue(
            MintIngredient ing,
            Map<String, Double> soloUnitValues,
            List<MintRecipe> allRecipes,
            MintIngredient.TagMatcher matcher) {
        Double fromSpec = soloUnitValues.get(ing.spec());
        if (fromSpec != null && fromSpec > 0.0) {
            return fromSpec;
        }
        String rep = ing.representativeItemId();
        Double fromRep = soloUnitValues.get(rep);
        if (fromRep != null && fromRep > 0.0) {
            return fromRep;
        }
        if (matcher != null) {
            double matchedSolo = -1.0;
            for (MintRecipe r : allRecipes) {
                if (r.ingredients().size() == 1 && r.totalFaceValue() > 0L) {
                    MintIngredient rIng = r.ingredients().get(0);
                    if (rIng.matchesItem(rep, matcher)) {
                        double u = r.totalFaceValue() / (double) rIng.count();
                        if (u > matchedSolo) {
                            matchedSolo = u;
                        }
                    }
                }
            }
            if (matchedSolo > 0.0) {
                return matchedSolo;
            }
        }
        Double defaultSpec = DEFAULT_INTRINSIC_UNIT_VALUES.get(ing.spec());
        if (defaultSpec != null && defaultSpec > 0.0) {
            return defaultSpec;
        }
        Double defaultRep = DEFAULT_INTRINSIC_UNIT_VALUES.get(rep);
        if (defaultRep != null && defaultRep > 0.0) {
            return defaultRep;
        }
        return -1.0;
    }

    private static boolean shouldApplyDualGrossFloor(
            MintIngredient matched,
            MintIngredient other,
            long grossValue,
            Map<String, Double> soloUnitValues,
            List<MintRecipe> allRecipes,
            MintIngredient.TagMatcher matcher) {
        double vMatched = estimateIngredientIntrinsicUnitValue(matched, soloUnitValues, allRecipes, matcher);
        double vOther = estimateIngredientIntrinsicUnitValue(other, soloUnitValues, allRecipes, matcher);
        if (vMatched <= 0.0 && vOther <= 0.0) {
            return true;
        }
        if (vMatched <= 0.0) {
            vMatched = grossValue / (double) matched.count();
        }
        if (vOther <= 0.0) {
            vOther = grossValue / (double) other.count();
        }
        return vMatched > 0.0 && vOther < vMatched * 2.0;
    }

    private static long ceilGrossWithTenPercentMargin(long grossValue, long denom) {
        if (grossValue <= 0L || denom <= 0L) {
            return 1L;
        }
        if (grossValue > Long.MAX_VALUE / 11L || denom > Long.MAX_VALUE / 10L) {
            BigInteger num = BigInteger.valueOf(grossValue).multiply(BigInteger.valueOf(11L));
            BigInteger den = BigInteger.valueOf(denom).multiply(BigInteger.valueOf(10L));
            BigInteger[] qr = num.divideAndRemainder(den);
            BigInteger res = qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0];
            return res.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                    ? Long.MAX_VALUE
                    : Math.max(1L, res.longValue());
        }
        long num = grossValue * 11L;
        long den = denom * 10L;
        long q = num / den;
        long r = num % den;
        return Math.max(1L, r > 0L ? q + 1L : q);
    }
}
