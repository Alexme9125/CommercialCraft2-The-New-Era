package com.tycooncraft.tyc2.core;

import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.SupplyCycleClock;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for weekly supply refresh cadence ({@link SupplyCycleClock}),
 * weekly batch quantity scaling, early/mid municipal treasury balance (working capital,
 * target reserve curve, and weekly municipal stipend), geographical biome exclusivity,
 * biome shortage demand bonuses, and player-friendly seasonal supply/demand rules.
 */
class WeeklySupplyAndBiomeSeasonTest {

    private static Predicate<String> vanillaItems() {
        return id -> id.startsWith("minecraft:");
    }

    private static Predicate<String> allItems() {
        return id -> true;
    }

    @Test
    void weeklySupplyClockDayTimeCadenceBackwardJumpEarlyRestockAndLowUnemployment() {
        assertEquals(1_000L, SupplyCycleClock.TICKS_PER_HOUR);
        assertEquals(24_000L, SupplyCycleClock.TICKS_PER_DAY);
        assertEquals(7, SupplyCycleClock.DAYS_PER_WEEK);
        assertEquals(168_000L, SupplyCycleClock.TICKS_PER_WEEK);

        TyC2Config cfg = TyC2Config.defaults();
        assertEquals(168_000, cfg.demandRefreshTicks());

        // Standard 7-day cycle based on dayTime (sleeping jumps dayTime forward)
        long anchor = 24_000L; // Day 1 dawn
        assertEquals(168_000L, SupplyCycleClock.remainingRefreshTicks(anchor, anchor, 168_000));
        assertEquals(7L, SupplyCycleClock.remainingWholeDays(168_000L));
        assertEquals(0L, SupplyCycleClock.remainingWholeHoursInDay(168_000L));
        assertFalse(SupplyCycleClock.isRefreshDue(anchor + 167_999L, anchor, 168_000, false));
        assertTrue(SupplyCycleClock.isRefreshDue(anchor + 168_000L, anchor, 168_000, false));
        assertEquals(0L, SupplyCycleClock.remainingRefreshTicks(anchor + 168_000L, anchor, 168_000));
        assertEquals(0L, SupplyCycleClock.remainingRefreshTicks(anchor + 500_000L, anchor, 168_000));

        // Partial day breakdown: 5 days + 9 hours + 230 ticks -> 5d 9h
        assertEquals(5L, SupplyCycleClock.remainingWholeDays(129_230L));
        assertEquals(9L, SupplyCycleClock.remainingWholeHoursInDay(129_230L));
        assertEquals(129L, SupplyCycleClock.remainingTotalHours(129_230L));

        // C1: Only first-time initialization (neverInitialized == true) triggers before full weekly period
        assertTrue(SupplyCycleClock.isRefreshDue(anchor, -1L, 168_000, true));
        assertTrue(SupplyCycleClock.isRefreshDue(anchor, anchor, 168_000, true));
        assertFalse(SupplyCycleClock.isRefreshDue(anchor, anchor, 168_000, false));

        // Backward time jump (/time set 0) and old saves where lastRefreshTick > currentDayTime
        assertEquals(0L, SupplyCycleClock.normalizeRefreshAnchor(0L, 450_000L));
        assertEquals(5_000L, SupplyCycleClock.normalizeRefreshAnchor(5_000L, 450_000L));
        assertEquals(168_000L, SupplyCycleClock.remainingRefreshTicks(5_000L, 450_000L, 168_000));
        assertEquals(0L, SupplyCycleClock.remainingRefreshTicks(5_000L, 0L, 0));

        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Clocktown",
                new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS);
        village.setLastDemandRefreshTick(900_000L);
        assertTrue(village.normalizeDemandRefreshAnchor(12_000L));
        assertEquals(12_000L, village.getLastDemandRefreshTick());
        assertFalse(village.normalizeDemandRefreshAnchor(12_000L));
        assertFalse(SupplyCycleClock.isRefreshDue(12_000L + 167_999L, village.getLastDemandRefreshTick(), 168_000, false));
        assertTrue(SupplyCycleClock.isRefreshDue(12_000L + 168_000L, village.getLastDemandRefreshTick(), 168_000, false));

        // Task 2.3: Low-unemployment bonus keeps weekly restock pinned at full 168,000 ticks (7 MC days)
        VillageEmploymentStats fullEmp = new VillageEmploymentStats(8, 8, 0, true, Map.of(), 0L);
        int fullEmpPeriod = fullEmp.effectiveDemandRefreshTicks(cfg);
        assertEquals(168_000, fullEmpPeriod);
        assertEquals(1.0, fullEmp.restockSpeedMultiplier(cfg), 1e-9);
        assertFalse(SupplyCycleClock.isRefreshDue(anchor + 167_999L, anchor, fullEmpPeriod, false));
        assertTrue(SupplyCycleClock.isRefreshDue(anchor + 168_000L, anchor, fullEmpPeriod, false));

        VillageEmploymentStats nearThreshold = new VillageEmploymentStats(100, 76, 24, true, Map.of(), 0L);
        assertEquals(168_000, nearThreshold.effectiveDemandRefreshTicks(cfg));
    }

    @Test
    void weeklyStipendGateAndOneTimeWorkingCapitalResignCooldown() {
        TyC2Config cfg = TyC2Config.defaults();
        int period = cfg.demandRefreshTicks();
        VillageRecord v = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "AntiExploitBurg",
                new BlockPos3i(10, 64, 10), BiomeStyle.PLAINS);

        UUID owner = UUID.randomUUID();
        long day0 = 24_000L;
        assertFalse(v.isInitialWorkingCapitalGranted());
        long topUp1 = v.establishPartnershipWithWorkingCapital(owner, null, 100L, 500L);
        assertEquals(400L, topUp1);
        assertEquals(500L, v.getVillageBalance());
        assertTrue(v.isInitialWorkingCapitalGranted());
        v.anchorStipendIfUnset(day0);

        // Give active orders and simulate draining balance to 0
        v.setActiveOrders(List.of(new VillageTradeOrder(
                "ord-1", OrderSide.VILLAGE_SELLS, "minecraft:wheat", "mnera:wheat", "",
                10, 10, 2L, day0, day0 + period, false, false, false
        )));
        v.setLastDemandRefreshTick(day0);
        v.setVillageBalance(0L);

        // C1: Weekly stipend MUST NOT trigger before full weekly period elapses
        assertEquals(0L, v.applyWeeklyMunicipalStipendIfDue(cfg.weeklyMunicipalStipendRatio(), day0 + period - 1L, period));
        assertEquals(0L, v.getVillageBalance());
        // Triggers exactly once at day0 + period, then refuses immediate second trigger
        assertEquals(150L, v.applyWeeklyMunicipalStipendIfDue(cfg.weeklyMunicipalStipendRatio(), day0 + period, period));
        assertEquals(150L, v.getVillageBalance());
        v.setVillageBalance(0L);
        assertEquals(0L, v.applyWeeklyMunicipalStipendIfDue(cfg.weeklyMunicipalStipendRatio(), day0 + period + 100L, period));
        assertEquals(150L, v.applyWeeklyMunicipalStipendIfDue(cfg.weeklyMunicipalStipendRatio(), day0 + 2L * period, period));

        // C2: Terminate partnership preserves active orders, clears hubDeployed, starts monotonic 1-week resign cooldown
        v.setHubDeployed(true);
        v.setVillageBalance(0L);
        long gameTime0 = 500_000L;
        v.terminatePartnership(gameTime0);
        assertFalse(v.isPartnered());
        assertFalse(v.isHubDeployed());
        assertEquals(gameTime0, v.getLastTerminationTick());
        assertEquals(1, v.getActiveOrders().size(), "Active orders must be preserved across terminate to stop reroll");
        assertTrue(v.hasInitialOrdersGenerated());
        assertEquals(period, v.remainingResignCooldownTicks(gameTime0, period));
        assertEquals(1L, v.remainingResignCooldownTicks(gameTime0 + period - 1L, period));
        assertEquals(0L, v.remainingResignCooldownTicks(gameTime0 + period, period));

        // C2: /time set 0 (dayTime backward jump) MUST NOT mutate monotonic lastTerminationTick or cause lockout
        v.normalizeDemandRefreshAnchor(0L);
        assertEquals(gameTime0, v.getLastTerminationTick(),
                "normalizeDemandRefreshAnchor(dayTime) must not overwrite monotonic gameTime lastTerminationTick");
        assertEquals(period, v.remainingResignCooldownTicks(gameTime0, period));
        // Legacy save guard: if stored tick > currentGameTime, never permanently lock out re-signing
        assertEquals(0L, v.remainingResignCooldownTicks(100L, period));

        // C2: Re-signing after cooldown clears lastTerminationTick and MUST NOT mint working capital top-up a second time
        long topUp2 = v.establishPartnershipWithWorkingCapital(owner, null, 100L, 500L);
        assertEquals(0L, topUp2);
        assertEquals(100L, v.getVillageBalance());
        assertEquals(-1L, v.getLastTerminationTick(), "Re-signing must clear lastTerminationTick back to -1");
        assertEquals(0L, v.remainingResignCooldownTicks(gameTime0, period));
    }

    @Test
    void weeklyQuantityScalingStandard35xRare175xAndTreasuryBalanceHelpers() {
        // 1. Target reserve consistency across MacroEconomyEngine and VillageLevelTable
        for (int lv = 1; lv <= 10; lv++) {
            double expected = 350.0 * Math.pow(lv, 1.8);
            assertEquals(expected, VillageLevelTable.targetReserve(lv), 1e-6);
            assertEquals(expected, MacroEconomyEngine.targetVillageReserve(lv), 1e-6);
        }
        assertEquals(350.0, VillageLevelTable.targetReserve(0), 1e-6);
        assertEquals(350.0, MacroEconomyEngine.targetVillageReserve(0), 1e-6);
        assertEquals(VillageLevelTable.targetReserve(10), MacroEconomyEngine.targetVillageReserve(99), 1e-6);
        assertEquals(1219L, Math.round(VillageLevelTable.targetReserve(2)));
        assertEquals(2529L, Math.round(VillageLevelTable.targetReserve(3)));
        assertEquals(4244L, Math.round(VillageLevelTable.targetReserve(4)));

        // 2. Initial working capital floor & weekly municipal stipend prevent 0-IC buy-order deadlock
        TyC2Config cfg = TyC2Config.defaults();
        VillageRecord lv1 = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Brookside",
                new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS);
        long topUp = lv1.establishPartnershipWithWorkingCapital(
                UUID.randomUUID(), null, cfg.negotiationCostIc(), cfg.initialVillageWorkingCapital());
        assertEquals(400L, topUp);
        assertEquals(500L, lv1.getVillageBalance());
        assertEquals(0L, lv1.ensureInitialWorkingCapital(500L));

        // Healthy Lv 1 village above 45% of 350 (158 IC) receives 0 stipend
        assertEquals(0L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));

        // Broke Lv 1 village (0 IC) receives min(158, max(150, 105)) = 150 IC, then remaining 8 IC next week
        lv1.setVillageBalance(0L);
        assertEquals(150L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));
        assertEquals(150L, lv1.getVillageBalance());
        assertEquals(8L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));
        assertEquals(158L, lv1.getVillageBalance());
        assertEquals(0L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));

        // Broke Lv 2 village (target ~1218.78, threshold=548, cap=366)
        lv1.setLevel(2);
        lv1.setVillageBalance(0L);
        assertEquals(366L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));
        assertEquals(366L, lv1.getVillageBalance());
        assertEquals(182L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));
        assertEquals(548L, lv1.getVillageBalance());
        assertEquals(0L, lv1.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio()));

        // Stipend disabled when ratio == 0.0
        lv1.setVillageBalance(0L);
        assertEquals(0L, lv1.applyWeeklyMunicipalStipend(0.0));
        assertEquals(0L, lv1.getVillageBalance());

        // 3. Weekly batch quantity scaling: 3.5x standard goods, 1.75x rare goods (ENCHANTED_BOOK, ENDGAME)
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        MacroEconomyEngine econ = new MacroEconomyEngine(cfg);

        TyC2Config unitCfg = TyC2Config.defaults()
                .setWeeklyQuantityMultiplier(1.0)
                .setWeeklyRareQuantityMultiplier(1.0);
        DemandGenerator unitGen = new DemandGenerator(catalog, unitCfg);
        DemandGenerator weeklyGen = new DemandGenerator(catalog, cfg);

        DemandGenerator.GeneratedDemand unitLv3 = unitGen.generate(
                3, "minecraft:plains", 500_000L, IndustrialTier.TIER_2, Season.NONE,
                econ, vanillaItems(), new Random(77L));
        DemandGenerator.GeneratedDemand weeklyLv3 = weeklyGen.generate(
                3, "minecraft:plains", 500_000L, IndustrialTier.TIER_2, Season.NONE,
                econ, vanillaItems(), new Random(77L));

        int unitSellSum = unitLv3.sellOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        int weeklySellSum = weeklyLv3.sellOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        double sellRatio = (double) weeklySellSum / unitSellSum;
        assertTrue(sellRatio >= 3.35 && sellRatio <= 3.65,
                "Standard weekly sell volume must scale ~3.5x, got ratio " + sellRatio);

        int unitBuySum = unitLv3.buyOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        int weeklyBuySum = weeklyLv3.buyOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        double buyRatio = (double) weeklyBuySum / unitBuySum;
        assertTrue(buyRatio >= 3.35 && buyRatio <= 3.65,
                "Standard weekly buy volume must scale ~3.5x, got ratio " + buyRatio);

        // Compare rare goods directly using a single-item catalog so RNG rolls are identical
        GoodsCatalog rareOnly = new GoodsCatalog();
        rareOnly.register(catalog.getById("mnera:book_mending").orElseThrow());
        rareOnly.register(catalog.getById("mnera:totem_of_undying").orElseThrow());
        DemandGenerator unitRareGen = new DemandGenerator(rareOnly, unitCfg);
        DemandGenerator weeklyRareGen = new DemandGenerator(rareOnly, cfg);

        int unitRareTotal = 0;
        int weeklyRareTotal = 0;
        for (int seed = 0; seed < 30; seed++) {
            unitRareTotal += unitRareGen.generate(
                    10, "minecraft:plains", 500_000L, IndustrialTier.TIER_5, Season.NONE,
                    econ, allItems(), new Random(seed)).sellOrders().stream()
                    .mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
            weeklyRareTotal += weeklyRareGen.generate(
                    10, "minecraft:plains", 500_000L, IndustrialTier.TIER_5, Season.NONE,
                    econ, allItems(), new Random(seed)).sellOrders().stream()
                    .mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        }
        double rareRatio = (double) weeklyRareTotal / unitRareTotal;
        assertTrue(rareRatio >= 1.55 && rareRatio <= 1.95,
                "Rare weekly quantity must scale ~1.75x, got ratio " + rareRatio);
    }

    @Test
    void tradeGoodMetadataRoundTripAndBiomeSeasonPricingFactors() {
        TradeGood custom = TradeGood.builder("test:peat_fuel", "test:peat_fuel", GoodsCategory.MINING, 12)
                .levelRange(1, 5)
                .sellable(false)
                .buyable(true)
                .biomeAffinities("swamp")
                .biomeExclusive(true)
                .demandBiomes("snowy", "desert")
                .demandSeasons(Season.WINTER, Season.AUTUMN)
                .quantityRange(8, 24)
                .build();

        assertTrue(custom.isBiomeExclusive());
        assertTrue(custom.hasDemandBiome("minecraft:snowy_taiga"));
        assertTrue(custom.hasDemandBiome("minecraft:badlands"));
        assertFalse(custom.hasDemandBiome("minecraft:plains"));
        assertTrue(custom.hasDemandSeason(Season.WINTER));
        assertFalse(custom.hasDemandSeason(Season.SUMMER));
        assertFalse(custom.hasDemandSeason(Season.NONE));

        // JSON data-map round-trip and withHarvestSeasons preservation
        TradeGood parsed = TradeGood.fromDataMap(custom.toDataMap());
        assertEquals(custom, parsed);

        TradeGood overlaid = custom.withHarvestSeasons(EnumSet.of(Season.SPRING));
        assertTrue(overlaid.isBiomeExclusive());
        assertEquals(custom.demandBiomes(), overlaid.demandBiomes());
        assertEquals(custom.demandSeasons(), overlaid.demandSeasons());

        // GoodsCatalog.findOrderGood distinguishes buy-side oak_log_demand (#minecraft:logs) from sell-side oak_log
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        TradeGood buyLogs = catalog.findOrderGood("mnera:oak_log_demand", "minecraft:oak_log", true).orElseThrow();
        assertEquals("mnera:oak_log_demand", buyLogs.id());
        assertEquals("#minecraft:logs", buyLogs.optionalTag());
        assertTrue(buyLogs.isBuyableByVillage());

        TradeGood sellLogs = catalog.findOrderGood("mnera:oak_log", "minecraft:oak_log", false).orElseThrow();
        assertEquals("mnera:oak_log", sellLogs.id());
        assertTrue(sellLogs.isSellableByVillage());

        // MacroEconomyEngine biome & seasonal factors
        MacroEconomyEngine econ = new MacroEconomyEngine();
        TradeGood sand = catalog.getById("mnera:sand").orElseThrow();
        TradeGood coal = catalog.getById("mnera:coal").orElseThrow();
        TradeGood bread = catalog.getById("mnera:bread").orElseThrow();
        TradeGood fdStew = catalog.getById("mnera:fd_beef_stew").orElseThrow();

        // Sell matching biome -> 0.88 (-12% discount); non-matching -> 1.00
        assertEquals(0.88, econ.computeBiomeFactor(sand, "minecraft:desert", false), 1e-6);
        assertEquals(1.00, econ.computeBiomeFactor(sand, "minecraft:plains", false), 1e-6);

        // Buy matching demandBiomes -> 1.15 (+15% shortage bonus); non-matching -> 1.00
        assertEquals(1.15, econ.computeBiomeFactor(coal, "minecraft:snowy_plains", true), 1e-6);
        assertEquals(1.00, econ.computeBiomeFactor(coal, "minecraft:desert", true), 1e-6);

        // Non-crop seasonal demand boost (1.20x) ONLY when season.isActive() && buying
        assertEquals(1.20, econ.computeSeasonFactor(coal, Season.WINTER, true), 1e-6);
        assertEquals(1.20, econ.computeSeasonFactor(bread, Season.WINTER, true), 1e-6);
        assertEquals(1.20, econ.computeSeasonFactor(fdStew, Season.WINTER, true), 1e-6);
        assertEquals(1.00, econ.computeSeasonFactor(coal, Season.SUMMER, true), 1e-6);
        assertEquals(1.00, econ.computeSeasonFactor(coal, Season.WINTER, false), 1e-6);
        assertEquals(1.00, econ.computeSeasonFactor(coal, Season.NONE, true), 1e-6);

        // EcC2 methane ice anti-arbitrage still holds under -12% (0.88x) biome discount
        TradeGood methaneIce = catalog.findByItemId("ecera:methane_ice_cluster").orElseThrow();
        TradeGood naturalGas = catalog.findByItemId("ecera:natural_gas_cell").orElseThrow();
        assertTrue(methaneIce.basePrice() * 3L * 0.88 > naturalGas.basePrice());
    }

    @Test
    void biomeExclusivityLocalSpecialtyGuaranteeAndDesertTimberDemand() {
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        MacroEconomyEngine econ = new MacroEconomyEngine();
        DemandGenerator gen = new DemandGenerator(catalog, TyC2Config.defaults());

        Map<String, Set<String>> exclusiveOwners = Map.ofEntries(
                Map.entry("minecraft:sand", Set.of("desert")),
                Map.entry("minecraft:sandstone", Set.of("desert")),
                Map.entry("minecraft:cactus", Set.of("desert")),
                Map.entry("minecraft:snow_block", Set.of("snowy")),
                Map.entry("minecraft:packed_ice", Set.of("snowy")),
                Map.entry("minecraft:cocoa_beans", Set.of("jungle")),
                Map.entry("minecraft:bamboo", Set.of("jungle")),
                Map.entry("minecraft:jungle_log", Set.of("jungle")),
                Map.entry("minecraft:slime_ball", Set.of("swamp")),
                Map.entry("minecraft:lily_pad", Set.of("swamp")),
                Map.entry("minecraft:acacia_log", Set.of("savanna")),
                Map.entry("minecraft:spruce_log", Set.of("taiga", "snowy")),
                Map.entry("minecraft:sweet_berries", Set.of("taiga", "snowy")),
                Map.entry("minecraft:oak_log", Set.of("plains", "swamp"))
        );

        // Verify universal staples remain non-exclusive
        for (String universalId : List.of("mnera:white_wool", "mnera:leather", "mnera:string", "mnera:bone",
                "mnera:gunpowder", "mnera:wheat", "mnera:carrot", "mnera:potato", "mnera:book_mending",
                "mnera:totem_of_undying")) {
            assertFalse(catalog.getById(universalId).orElseThrow().isBiomeExclusive(),
                    "Universal staple must NOT be biomeExclusive: " + universalId);
        }

        boolean desertBoughtLogs = false;
        for (BiomeStyle style : BiomeStyle.values()) {
            String biome = style.id();
            for (int lv = 1; lv <= 5; lv++) {
                for (int seed = 0; seed < 15; seed++) {
                    DemandGenerator.GeneratedDemand demand = gen.generate(
                            lv, biome, 20_000L, IndustrialTier.TIER_2, Season.NONE,
                            econ, vanillaItems(), new Random(lv * 1000L + seed * 17L + style.ordinal()));

                    // Pure vanilla (Season.NONE): ZERO seasonal highlights
                    for (DemandGenerator.GeneratedOrder o : demand.allOrders()) {
                        assertFalse(o.seasonalHighlight(), "Season.NONE must never mark seasonalHighlight: " + o);
                    }

                    // At least 1 local biome specialty sell order guaranteed at Lv 1-5 in every biome
                    assertTrue(demand.sellOrders().stream().anyMatch(DemandGenerator.GeneratedOrder::biomeSpecialty),
                            "Biome " + biome + " Lv " + lv + " must guarantee at least 1 local biome specialty sell order");

                    // Never sell another biome's geographical exclusives
                    Set<String> soldItems = new HashSet<>();
                    for (DemandGenerator.GeneratedOrder sell : demand.sellOrders()) {
                        soldItems.add(sell.itemId());
                        Set<String> owners = exclusiveOwners.get(sell.itemId());
                        if (owners != null) {
                            assertTrue(owners.contains(biome),
                                    "Biome " + biome + " illegally sold exclusive item " + sell.itemId());
                        }
                    }

                    // Never buy an item being sold in the same cycle; oak_log_demand strictly desert-only
                    for (DemandGenerator.GeneratedOrder buy : demand.buyOrders()) {
                        assertFalse(soldItems.contains(buy.itemId()),
                                "Village must not simultaneously sell and buy " + buy.itemId());
                        if ("mnera:oak_log_demand".equals(buy.goodId())) {
                            assertEquals("desert", biome, "Only desert villages should request mnera:oak_log_demand");
                            assertTrue(buy.biomeSpecialty());
                            desertBoughtLogs = true;
                        }
                    }
                }
            }
        }
        assertTrue(desertBoughtLogs, "Desert villages should generate mnera:oak_log_demand buy orders");
    }

    @Test
    void playerFriendlySeasonalGuardrailsPreventImpossibleCropAsksAndGuaranteeStaples() {
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        MacroEconomyEngine econ = new MacroEconomyEngine();
        DemandGenerator gen = new DemandGenerator(catalog, TyC2Config.defaults());

        Set<GoodsCategory> stapleCategories = EnumSet.of(
                GoodsCategory.MINING, GoodsCategory.METAL, GoodsCategory.BUILDING, GoodsCategory.FOOD_BASIC);

        boolean sawJungleWinterCocoaBuy = false;
        boolean sawWinterFuelHighlight = false;

        for (BiomeStyle style : BiomeStyle.values()) {
            String biome = style.id();
            for (Season season : Season.allFourSeasons()) {
                for (int lv = 1; lv <= 10; lv++) {
                    int maxOffSeasonAllowed = (lv <= 4) ? 1 : 2;
                    for (int seed = 0; seed < 12; seed++) {
                        DemandGenerator.GeneratedDemand demand = gen.generate(
                                lv, biome, 200_000L, IndustrialTier.TIER_5, season,
                                econ, allItems(), new Random(lv * 5000L + season.ordinal() * 200L + seed));

                        int offSeasonCrops = 0;
                        boolean hasAccessibleStaple = false;
                        for (DemandGenerator.GeneratedOrder buy : demand.buyOrders()) {
                            TradeGood good = catalog.getById(buy.goodId()).orElseThrow();
                            if (good.isSeasonalCrop() && good.isOffSeason(season)) {
                                offSeasonCrops++;
                                // MUST NOT ask for another biome's exclusive crop!
                                assertTrue(!good.isBiomeExclusive() || good.hasBiomeAffinity(biome),
                                        "Biome " + biome + " in " + season + " illegally demanded exclusive off-season crop: "
                                                + good.id());
                                if ("jungle".equals(biome) && season == Season.WINTER
                                        && "minecraft:cocoa_beans".equals(good.itemId())) {
                                    sawJungleWinterCocoaBuy = true;
                                }
                            }
                            if (stapleCategories.contains(buy.category())
                                    && !(good.isSeasonalCrop() && good.isOffSeason(season))) {
                                hasAccessibleStaple = true;
                            }
                            if (season == Season.WINTER && !good.isSeasonalCrop()
                                    && good.hasDemandSeason(Season.WINTER) && buy.seasonalHighlight()) {
                                sawWinterFuelHighlight = true;
                            }
                        }

                        assertTrue(offSeasonCrops <= maxOffSeasonAllowed,
                                "Off-season crop buy orders exceeded cap (" + maxOffSeasonAllowed + ") at Lv " + lv
                                        + " in " + biome + "/" + season + ": got " + offSeasonCrops);

                        if (lv <= 6) {
                            assertTrue(hasAccessibleStaple,
                                    "Lv " + lv + " in " + biome + "/" + season
                                            + " MUST include at least 1 locally accessible staple buy order");
                        }
                    }
                }
            }
        }

        assertTrue(sawWinterFuelHighlight, "Winter demand must highlight non-crop winter fuel/warm-food orders");
        assertTrue(sawJungleWinterCocoaBuy, "Jungle village in Winter may still request native off-season cocoa beans");
    }

    @Test
    void villageTradeOrderBiomeSpecialtyPreservedAcrossMutationsAndLegacyCtor() {
        VillageTradeOrder legacy = new VillageTradeOrder(
                "ord-legacy", OrderSide.VILLAGE_SELLS, "minecraft:wheat", "mnera:wheat", "",
                40, 0, 3L, 0L, 168_000L, false, true);
        assertFalse(legacy.biomeSpecialty());

        VillageTradeOrder specialty = new VillageTradeOrder(
                "ord-spec", OrderSide.VILLAGE_BUYS, "minecraft:coal", "mnera:coal", "",
                64, 0, 5L, 24_000L, 192_000L, false, true, true);
        assertTrue(specialty.biomeSpecialty());
        assertTrue(specialty.seasonalBonus());

        VillageTradeOrder partial = specialty.withAddedFulfillment(16);
        assertEquals(16, partial.fulfilledQty());
        assertTrue(partial.biomeSpecialty());
        assertTrue(partial.seasonalBonus());

        VillageTradeOrder toggled = partial.withAutoFulfill(true);
        assertTrue(toggled.autoFulfill());
        assertTrue(toggled.biomeSpecialty());
        assertTrue(toggled.seasonalBonus());
    }

    @Test
    void generatedDatapackGoodsMatchGoodsCatalogExactlyAndPreserveBiomeSeasonMetadata(@TempDir Path tempDir) throws Exception {
        Path repoRoot = resolveRepoRoot();
        Path outputDir = tempDir.resolve("generated-pack");

        Process proc = new ProcessBuilder(
                "python3",
                repoRoot.resolve("src/datagen/generate_resources.py").toString(),
                "--target", "neoforge-1.21.1",
                "--output", outputDir.toString()
        ).directory(repoRoot.toFile()).redirectErrorStream(true).start();
        String procOut = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, proc.waitFor(), "generate_resources.py failed: " + procOut);

        Path goodsDir = outputDir.resolve("data/mnera/goods");
        assertTrue(Files.isDirectory(goodsDir), "Missing generated data/mnera/goods directory");

        GoodsCatalog defaults = GoodsCatalog.createDefault();
        GoodsCatalog mergedOnDefaults = GoodsCatalog.createDefault();
        GoodsCatalog datapackOnly = new GoodsCatalog();

        List<Path> jsonFiles;
        try (Stream<Path> s = Files.list(goodsDir)) {
            jsonFiles = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(5, jsonFiles.size(), "Expected 5 generated goods JSON files (vanilla, ic2, ecc2, fd, kc)");

        for (Path jsonFile : jsonFiles) {
            String text = Files.readString(jsonFile, StandardCharsets.UTF_8);
            mergedOnDefaults.mergeFromJson(text);
            datapackOnly.mergeFromJson(text);
        }

        // Exact 1:1 parity: every single TradeGood (including biomeExclusive, demandBiomes, demandSeasons,
        // optionalTag, level ranges, prices, quantities) must match between GoodsCatalog.java and datagen JSON!
        Map<String, TradeGood> expectedById = new LinkedHashMap<>();
        for (TradeGood g : defaults.allGoods()) {
            expectedById.put(g.id(), g);
        }
        Map<String, TradeGood> datapackById = new LinkedHashMap<>();
        for (TradeGood g : datapackOnly.allGoods()) {
            datapackById.put(g.id(), g);
        }
        assertEquals(expectedById.keySet(), datapackById.keySet(),
                "Generated data/mnera/goods/*.json IDs must match GoodsCatalog.createDefault() 1:1");
        for (Map.Entry<String, TradeGood> e : expectedById.entrySet()) {
            assertEquals(e.getValue(), datapackById.get(e.getKey()),
                    "Datapack TradeGood drifted from GoodsCatalog.java for " + e.getKey());
            assertEquals(e.getValue(), mergedOnDefaults.getById(e.getKey()).orElseThrow(),
                    "Merging datapack JSON over defaults altered TradeGood " + e.getKey());
        }

        // Spot-check critical regression guards: biomeExclusive, demandBiomes, demandSeasons, tyc2:oak_log_demand
        for (String exclusiveId : List.of("mnera:cactus", "mnera:sand", "mnera:sandstone", "mnera:snow_block",
                "mnera:packed_ice", "mnera:cocoa_beans", "mnera:bamboo", "mnera:jungle_log",
                "mnera:slime_ball", "mnera:lily_pad", "mnera:oak_log", "mnera:spruce_log",
                "mnera:acacia_log", "mnera:sweet_berries", "mnera:oak_log_demand")) {
            assertTrue(mergedOnDefaults.getById(exclusiveId).orElseThrow().isBiomeExclusive(),
                    "Merged datapack wiped biomeExclusive on " + exclusiveId);
        }
        TradeGood oakLogDemand = mergedOnDefaults.getById("mnera:oak_log_demand").orElseThrow();
        assertEquals("#minecraft:logs", oakLogDemand.optionalTag());
        assertTrue(oakLogDemand.hasDemandBiome("desert"));
        assertTrue(oakLogDemand.hasDemandSeason(Season.WINTER));

        TradeGood coal = mergedOnDefaults.getById("mnera:coal").orElseThrow();
        assertTrue(coal.hasDemandBiome("snowy"));
        assertTrue(coal.hasDemandSeason(Season.WINTER));

        // Regression test: Plains village using datapack-merged catalog MUST NEVER sell cactus!
        DemandGenerator gen = new DemandGenerator(mergedOnDefaults, TyC2Config.defaults());
        MacroEconomyEngine econ = new MacroEconomyEngine();
        for (int seed = 0; seed < 40; seed++) {
            DemandGenerator.GeneratedDemand demand = gen.generate(
                    2, "minecraft:plains", 2_000L, IndustrialTier.TIER_1, Season.NONE,
                    econ, vanillaItems(), new Random(seed));
            for (DemandGenerator.GeneratedOrder sell : demand.sellOrders()) {
                assertNotEquals("minecraft:cactus", sell.itemId(),
                        "Plains village with merged datapack MUST NEVER sell desert-exclusive cactus!");
            }
        }
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("settings.gradle")) && Files.isDirectory(cwd.resolve("src/datagen"))) {
            return cwd;
        }
        Path candidate = cwd;
        for (int i = 0; i < 4 && candidate != null; i++) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("src/datagen"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate TyC2 repo root from " + cwd);
    }
}
