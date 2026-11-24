package com.tycooncraft.tyc2.core;

import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dynamic village trade order generator honoring Village Level progression (1–10),
 * player/company {@link IndustrialTier} gating, {@link MacroEconomyEngine} dynamic
 * pricing, biome specialties, runtime item-registry existence filtering, and
 * cross-season agricultural arbitrage.
 */
public final class DemandGenerator {

    private static final int[] SELL_ORDER_COUNTS = {0, 3, 4, 4, 5, 6, 6, 7, 8, 9, 10};
    private static final int[] BUY_ORDER_COUNTS = {0, 2, 3, 4, 4, 5, 6, 6, 7, 8, 9};
    private static final double[] QTY_SCALES = {0.0, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.5, 8.0};
    private static final double PROFESSION_WEIGHT_STEP = 0.12;
    private static final double MAX_PROFESSION_WEIGHT_MULT = 1.40;
    private static final WorkplaceCatalog DEFAULT_WORKPLACES = WorkplaceCatalog.createDefault();

    /**
     * Side of a generated village trade order.
     */
    public enum OrderSide {
        /** Village sells goods to player (player pays IC, receives items in Outbound zone). */
        VILLAGE_SELLS,
        /** Village buys goods from player (player delivers items to Inbound zone, receives IC). */
        VILLAGE_BUYS
    }

    /**
     * Immutable trade order produced by {@link DemandGenerator}.
     *
     * @param orderId unique order ID within the cycle
     * @param side whether the village is selling or buying
     * @param goodId catalog good ID
     * @param itemId Minecraft item registry ID
     * @param optionalTag optional item tag spec (may be {@code null})
     * @param optionalEnchantmentSpec optional enchantment spec for enchanted books (may be {@code null})
     * @param category economic category
     * @param quantity total order quantity (>= 1)
     * @param unitPrice dynamic unit price in IC (>= 1)
     * @param baseUnitPrice catalog base unit price in IC
     * @param seasonalHighlight {@code true} if discounted in-season harvest or premium off-season buy order
     * @param biomeSpecialty {@code true} if matched village biome specialty
     */
    public record GeneratedOrder(
            String orderId,
            OrderSide side,
            String goodId,
            String itemId,
            String optionalTag,
            String optionalEnchantmentSpec,
            GoodsCategory category,
            int quantity,
            long unitPrice,
            long baseUnitPrice,
            boolean seasonalHighlight,
            boolean biomeSpecialty
    ) {
        /** @return total order IC value ({@code quantity * unitPrice}) */
        public long totalValue() {
            return Math.multiplyExact(unitPrice, quantity);
        }
    }

    /**
     * Bundle of Sell Orders and Buy Orders generated for one village demand cycle.
     *
     * @param sellOrders orders where the village sells to the player
     * @param buyOrders orders where the village buys from the player
     */
    public record GeneratedDemand(
            List<GeneratedOrder> sellOrders,
            List<GeneratedOrder> buyOrders
    ) {
        /** @return combined list of sell orders followed by buy orders */
        public List<GeneratedOrder> allOrders() {
            List<GeneratedOrder> combined = new ArrayList<>(sellOrders.size() + buyOrders.size());
            combined.addAll(sellOrders);
            combined.addAll(buyOrders);
            return Collections.unmodifiableList(combined);
        }
    }

    private final GoodsCatalog catalog;
    private final TyC2Config config;

    /**
     * Creates a demand generator backed by the default {@link GoodsCatalog} and {@link TyC2Config}.
     */
    public DemandGenerator() {
        this(GoodsCatalog.createDefault(), TyC2Config.defaults());
    }

    /**
     * Creates a demand generator backed by a custom catalog and configuration.
     *
     * @param catalog goods catalog
     * @param config active configuration
     */
    public DemandGenerator(GoodsCatalog catalog, TyC2Config config) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Returns the configured Sell Order count for a village level (1..10).
     *
     * @param villageLevel village level
     * @return target sell order count
     */
    public static int sellOrderCountForLevel(int villageLevel) {
        return SELL_ORDER_COUNTS[clampLevel(villageLevel)];
    }

    /**
     * Returns the configured Buy Order count for a village level (1..10).
     *
     * @param villageLevel village level
     * @return target buy order count
     */
    public static int buyOrderCountForLevel(int villageLevel) {
        return BUY_ORDER_COUNTS[clampLevel(villageLevel)];
    }

    /**
     * Returns the configured quantity scale multiplier for a village level (1..10).
     *
     * @param villageLevel village level
     * @return quantity scale factor
     */
    public static double quantityScaleForLevel(int villageLevel) {
        return QTY_SCALES[clampLevel(villageLevel)];
    }

    /**
     * Generates Sell Orders and Buy Orders for a partnered village using the full
     * progression, tier-gating, seasonal arbitrage, biome affinity, and macro-economy rules.
     *
     * @param villageLevel village level (1..10)
     * @param biomeId village biome ID or style key (e.g. {@code "minecraft:plains"})
     * @param villageBalance current village balance in IC
     * @param playerIndustrialTier owner player or company highest industrial tier
     * @param currentSeason current season (or {@link Season#NONE})
     * @param economy live macro-economy engine
     * @param itemExists runtime predicate verifying an item ID is registered in Minecraft
     * @param random RNG source
     * @return generated sell and buy orders
     */
    public GeneratedDemand generate(
            int villageLevel,
            String biomeId,
            long villageBalance,
            IndustrialTier playerIndustrialTier,
            Season currentSeason,
            MacroEconomyEngine economy,
            Predicate<String> itemExists,
            Random random
    ) {
        return generate(
                villageLevel,
                biomeId,
                villageBalance,
                playerIndustrialTier,
                currentSeason,
                economy,
                itemExists,
                VillageEmploymentStats.EMPTY,
                DEFAULT_WORKPLACES,
                random
        );
    }

    /**
     * Generates Sell Orders and Buy Orders for a partnered village, additionally factoring in
     * {@link VillageEmploymentStats} low-unemployment trade quantity bonuses ({@code 1.25x..1.50x},
     * moderated {@code 1.10x..1.20x} on rare categories) and gentle active-profession category weighting
     * ({@code <= 1.40x}) while strictly preserving all village level and {@link IndustrialTier} caps.
     *
     * @param villageLevel village level (1..10)
     * @param biomeId village biome ID or style key (e.g. {@code "minecraft:plains"})
     * @param villageBalance current village balance in IC
     * @param playerIndustrialTier owner player or company highest industrial tier
     * @param currentSeason current season (or {@link Season#NONE})
     * @param economy live macro-economy engine
     * @param itemExists runtime predicate verifying an item ID is registered in Minecraft
     * @param employmentStats village employment snapshot (null treated as {@link VillageEmploymentStats#EMPTY})
     * @param workplaceCatalog workplace catalog mapping professions to trade categories (null uses default)
     * @param random RNG source
     * @return generated sell and buy orders
     */
    public GeneratedDemand generate(
            int villageLevel,
            String biomeId,
            long villageBalance,
            IndustrialTier playerIndustrialTier,
            Season currentSeason,
            MacroEconomyEngine economy,
            Predicate<String> itemExists,
            VillageEmploymentStats employmentStats,
            WorkplaceCatalog workplaceCatalog,
            Random random
    ) {
        Objects.requireNonNull(economy, "economy must not be null");
        Objects.requireNonNull(itemExists, "itemExists must not be null");
        Random rng = random == null ? new Random() : random;
        VillageEmploymentStats stats = employmentStats == null ? VillageEmploymentStats.EMPTY : employmentStats;
        WorkplaceCatalog workplaces = workplaceCatalog == null ? DEFAULT_WORKPLACES : workplaceCatalog;

        int level = clampLevel(villageLevel);
        Season season = currentSeason == null ? Season.NONE : currentSeason;
        boolean seasonalActive = config.enableSeasonalArbitrage() && season.isActive();
        int maxUnlockedTier = IndustrialTier.effectiveUnlockedTier(
                level,
                playerIndustrialTier == null ? IndustrialTier.TIER_0 : playerIndustrialTier,
                config.enforceIndustrialTierCap()
        );
        double qtyScale = quantityScaleForLevel(level);
        double empQtyMult = stats.tradeQuantityMultiplier(config);
        double empRareQtyMult = stats.rareTradeQuantityMultiplier(config);
        Map<GoodsCategory, Integer> profCountByCategory = countProfessionsByCategory(stats, workplaces);
        List<TradeGood> liveGoods = catalog.getAvailableGoods(itemExists);

        List<GeneratedOrder> sellOrders = generateSellOrders(
                level, biomeId, villageBalance, maxUnlockedTier, season, seasonalActive,
                qtyScale, empQtyMult, empRareQtyMult, profCountByCategory, economy, liveGoods, rng
        );
        List<GeneratedOrder> buyOrders = generateBuyOrders(
                level, biomeId, villageBalance, maxUnlockedTier, season, seasonalActive,
                qtyScale, empQtyMult, empRareQtyMult, profCountByCategory, economy, liveGoods, rng
        );

        return new GeneratedDemand(
                Collections.unmodifiableList(sellOrders),
                Collections.unmodifiableList(buyOrders)
        );
    }

    private List<GeneratedOrder> generateSellOrders(
            int level,
            String biomeId,
            long villageBalance,
            int maxUnlockedTier,
            Season season,
            boolean seasonalActive,
            double qtyScale,
            double empQtyMult,
            double empRareQtyMult,
            Map<GoodsCategory, Integer> profCountByCategory,
            MacroEconomyEngine economy,
            List<TradeGood> liveGoods,
            Random rng
    ) {
        int targetCount = sellOrderCountForLevel(level);
        List<WeightedCandidate> pool = new ArrayList<>();

        for (TradeGood good : liveGoods) {
            if (!good.isSellableByVillage()) {
                continue;
            }
            if (!good.matchesLevel(level)) {
                continue;
            }
            if (good.industrialTierRequired().level() > maxUnlockedTier) {
                continue;
            }
            // Strict Seasonal Arbitrage Rule: agrarian villages NEVER sell off-season crops!
            if (seasonalActive && good.isSeasonalCrop() && good.isOffSeason(season)) {
                continue;
            }

            double weight = 1.0;
            boolean inSeasonCrop = seasonalActive && good.isSeasonalCrop() && good.isInSeason(season);
            if (inSeasonCrop) {
                weight *= 2.0;
            }
            boolean biomeMatch = good.hasBiomeAffinity(biomeId);
            if (biomeMatch) {
                weight *= 1.5;
            }
            if (level <= 3 && (good.category() == GoodsCategory.TEXTILE
                    || good.category() == GoodsCategory.MONSTER_DROP
                    || good.category() == GoodsCategory.AGRICULTURE
                    || good.category() == GoodsCategory.FORESTRY)) {
                weight *= 1.4;
            } else if (level >= 7) {
                if (good.category() == GoodsCategory.ENDGAME) {
                    weight *= (level == 10 ? 3.2 : 2.2);
                } else if (good.category() == GoodsCategory.ENCHANTED_BOOK
                        || good.category() == GoodsCategory.RARE_ALLOY) {
                    weight *= 2.2;
                }
            }
            weight *= professionCategoryWeight(good.category(), profCountByCategory);
            pool.add(new WeightedCandidate(good, weight, inSeasonCrop, biomeMatch));
        }

        List<WeightedCandidate> chosen = pickWeightedDistinct(pool, targetCount, rng, false);
        List<GeneratedOrder> orders = new ArrayList<>(chosen.size());
        int idx = 1;
        for (WeightedCandidate wc : chosen) {
            TradeGood good = wc.good;
            MacroEconomyEngine.PriceQuote quote = economy.quotePrice(
                    good, level, villageBalance, biomeId, season, false
            );
            int qty = rollQuantity(good, qtyScale, wc.seasonalHighlight, empQtyMult, empRareQtyMult, rng);
            orders.add(new GeneratedOrder(
                    "sell_" + level + "_" + idx++,
                    OrderSide.VILLAGE_SELLS,
                    good.id(),
                    good.itemId(),
                    good.optionalTag(),
                    good.optionalEnchantmentSpec(),
                    good.category(),
                    qty,
                    quote.finalUnitPrice(),
                    good.basePrice(),
                    wc.seasonalHighlight,
                    wc.biomeMatch
            ));
        }
        return orders;
    }

    private List<GeneratedOrder> generateBuyOrders(
            int level,
            String biomeId,
            long villageBalance,
            int maxUnlockedTier,
            Season season,
            boolean seasonalActive,
            double qtyScale,
            double empQtyMult,
            double empRareQtyMult,
            Map<GoodsCategory, Integer> profCountByCategory,
            MacroEconomyEngine economy,
            List<TradeGood> liveGoods,
            Random rng
    ) {
        int targetCount = buyOrderCountForLevel(level);

        // Deduplicate by itemId when both a rural crop entry and a metro buy entry share the same item
        Map<String, WeightedCandidate> byItem = new LinkedHashMap<>();
        for (TradeGood good : liveGoods) {
            if (good.industrialTierRequired().level() > maxUnlockedTier) {
                continue;
            }
            boolean normalBuy = good.isBuyableByVillage() && good.matchesLevel(level);
            boolean offSeasonArbitrage = seasonalActive
                    && good.isSeasonalCrop()
                    && good.isOffSeason(season)
                    && good.minLevel() <= level;

            if (!normalBuy && !offSeasonArbitrage) {
                continue;
            }

            double weight = 1.0;
            if (offSeasonArbitrage) {
                weight *= 2.4;
            }
            if (level <= 3 && (good.category() == GoodsCategory.MINING || good.category() == GoodsCategory.METAL)) {
                weight *= 1.6;
            } else if (level >= 7 && good.category() == GoodsCategory.FOOD_FINE) {
                weight *= 2.5;
            }
            weight *= professionCategoryWeight(good.category(), profCountByCategory);

            MacroEconomyEngine.PriceQuote preview = economy.quotePrice(
                    good, level, villageBalance, biomeId, season, true
            );
            if (villageBalance > 0L && preview.finalUnitPrice() > villageBalance) {
                weight *= 0.05;
            }

            WeightedCandidate candidate = new WeightedCandidate(good, weight, offSeasonArbitrage, good.hasBiomeAffinity(biomeId));
            WeightedCandidate existing = byItem.get(good.itemId());
            if (existing == null || (good.matchesLevel(level) && !existing.good.matchesLevel(level))) {
                byItem.put(good.itemId(), candidate);
            }
        }

        List<WeightedCandidate> pool = new ArrayList<>(byItem.values());
        List<WeightedCandidate> chosen = new ArrayList<>(targetCount);
        Set<String> usedGoods = new HashSet<>();

        // Guarantee at least 1 off-season premium buy order when active season creates off-season scarcity
        if (seasonalActive && targetCount > 0) {
            List<WeightedCandidate> offSeasonPool = new ArrayList<>();
            for (WeightedCandidate wc : pool) {
                if (wc.seasonalHighlight) {
                    offSeasonPool.add(wc);
                }
            }
            if (!offSeasonPool.isEmpty()) {
                WeightedCandidate guaranteed = pickOneWeighted(offSeasonPool, rng);
                chosen.add(guaranteed);
                usedGoods.add(guaranteed.good.id());
            }
        }

        // At Lv 7-10, guarantee strong representation of Fine Cuisine / High-Quality Food
        if (level >= 7 && chosen.size() < targetCount) {
            List<WeightedCandidate> fineFoodPool = new ArrayList<>();
            for (WeightedCandidate wc : pool) {
                if (wc.good.category() == GoodsCategory.FOOD_FINE && !usedGoods.contains(wc.good.id())) {
                    fineFoodPool.add(wc);
                }
            }
            int fineFoodGuarantees = Math.min(2, Math.min(fineFoodPool.size(), targetCount - chosen.size()));
            List<WeightedCandidate> guaranteedFoods = pickWeightedDistinct(fineFoodPool, fineFoodGuarantees, rng, false);
            for (WeightedCandidate wc : guaranteedFoods) {
                chosen.add(wc);
                usedGoods.add(wc.good.id());
            }
        }

        if (chosen.size() < targetCount) {
            List<WeightedCandidate> remainingPool = new ArrayList<>();
            for (WeightedCandidate wc : pool) {
                if (!usedGoods.contains(wc.good.id())) {
                    remainingPool.add(wc);
                }
            }
            chosen.addAll(pickWeightedDistinct(remainingPool, targetCount - chosen.size(), rng, false));
        }

        List<GeneratedOrder> orders = new ArrayList<>(chosen.size());
        int idx = 1;
        for (WeightedCandidate wc : chosen) {
            TradeGood good = wc.good;
            MacroEconomyEngine.PriceQuote quote = economy.quotePrice(
                    good, level, villageBalance, biomeId, season, true
            );
            int qty = rollQuantity(good, qtyScale, false, empQtyMult, empRareQtyMult, rng);
            // Respect village liquidity: village cannot offer a Buy Order exceeding its balance when funded
            if (villageBalance > 0L) {
                long maxAffordable = villageBalance / quote.finalUnitPrice();
                if (maxAffordable >= 1L) {
                    qty = (int) Math.min(qty, Math.min(Integer.MAX_VALUE, maxAffordable));
                } else {
                    qty = 1;
                }
            }
            orders.add(new GeneratedOrder(
                    "buy_" + level + "_" + idx++,
                    OrderSide.VILLAGE_BUYS,
                    good.id(),
                    good.itemId(),
                    good.optionalTag(),
                    good.optionalEnchantmentSpec(),
                    good.category(),
                    qty,
                    quote.finalUnitPrice(),
                    good.basePrice(),
                    wc.seasonalHighlight,
                    wc.biomeMatch
            ));
        }
        return orders;
    }

    private static Map<GoodsCategory, Integer> countProfessionsByCategory(
            VillageEmploymentStats stats, WorkplaceCatalog workplaces) {
        Map<String, Integer> activeProfs = stats.activeProfessionCounts();
        if (activeProfs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<GoodsCategory, Integer> counts = new EnumMap<>(GoodsCategory.class);
        for (Map.Entry<String, Integer> entry : activeProfs.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0) {
                continue;
            }
            for (GoodsCategory cat : workplaces.tradeCategoriesFor(entry.getKey())) {
                counts.merge(cat, count, Integer::sum);
            }
        }
        return counts;
    }

    private static double professionCategoryWeight(
            GoodsCategory category, Map<GoodsCategory, Integer> profCountByCategory) {
        int matchingCount = profCountByCategory.getOrDefault(category, 0);
        if (matchingCount <= 0) {
            return 1.0;
        }
        return Math.min(MAX_PROFESSION_WEIGHT_MULT, 1.0 + PROFESSION_WEIGHT_STEP * matchingCount);
    }

    private static int rollQuantity(
            TradeGood good,
            double qtyScale,
            boolean inSeasonDouble,
            double empQtyMult,
            double empRareQtyMult,
            Random rng) {
        int min = good.baseMinQty();
        int max = good.baseMaxQty();
        int baseRoll = min == max ? min : (min + rng.nextInt(max - min + 1));
        boolean rare = good.category() == GoodsCategory.ENCHANTED_BOOK
                || good.category() == GoodsCategory.ENDGAME;
        // Rare single-unit items (Enchanted Books, Endgame relics) scale gently rather than x8 stacks
        double effectiveScale = rare
                ? Math.min(2.0, 1.0 + (qtyScale - 1.0) * 0.15)
                : qtyScale;
        double empMult = rare ? empRareQtyMult : empQtyMult;
        double scaled = baseRoll * effectiveScale * (inSeasonDouble ? 2.0 : 1.0) * empMult;
        return Math.max(1, (int) Math.round(scaled));
    }

    private static List<WeightedCandidate> pickWeightedDistinct(
            List<WeightedCandidate> pool,
            int count,
            Random rng,
            boolean allowDuplicates
    ) {
        if (pool.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        List<WeightedCandidate> available = new ArrayList<>(pool);
        List<WeightedCandidate> picked = new ArrayList<>(Math.min(count, available.size()));
        while (picked.size() < count && !available.isEmpty()) {
            WeightedCandidate chosen = pickOneWeighted(available, rng);
            picked.add(chosen);
            if (!allowDuplicates) {
                available.remove(chosen);
            }
        }
        return picked;
    }

    private static WeightedCandidate pickOneWeighted(List<WeightedCandidate> pool, Random rng) {
        double totalWeight = 0.0;
        for (WeightedCandidate wc : pool) {
            totalWeight += Math.max(0.001, wc.weight);
        }
        double roll = rng.nextDouble() * totalWeight;
        double acc = 0.0;
        for (WeightedCandidate wc : pool) {
            acc += Math.max(0.001, wc.weight);
            if (roll <= acc) {
                return wc;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static int clampLevel(int villageLevel) {
        return Math.max(1, Math.min(10, villageLevel));
    }

    private static final class WeightedCandidate {
        final TradeGood good;
        final double weight;
        final boolean seasonalHighlight;
        final boolean biomeMatch;

        WeightedCandidate(TradeGood good, double weight, boolean seasonalHighlight, boolean biomeMatch) {
            this.good = good;
            this.weight = weight;
            this.seasonalHighlight = seasonalHighlight;
            this.biomeMatch = biomeMatch;
        }
    }
}
