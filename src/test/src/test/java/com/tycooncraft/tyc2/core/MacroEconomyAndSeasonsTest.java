package com.tycooncraft.tyc2.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class MacroEconomyAndSeasonsTest {

    private static Predicate<String> vanillaOnlyItems() {
        return id -> id.startsWith("minecraft:");
    }

    private static Predicate<String> allModItems() {
        return id -> true;
    }

    @Test
    void inflationOnOverMintingDeflationOnSinksAndGuardrails() {
        MacroEconomyEngine engine = new MacroEconomyEngine();
        assertEquals(1.0, engine.computeCpi(), 1e-6);
        assertEquals(MacroEconomyEngine.InflationState.STABLE, engine.getInflationState());

        TradeGood iron = GoodsCatalog.createDefault().getById("tyc2:iron_ingot").orElseThrow();
        long baselinePrice = engine.quotePrice(iron, 1, 200L, "plains", Season.NONE, true).finalUnitPrice();

        // Over-minting drives CPI up (inflation)
        engine.recordMinting(15_000L);
        assertTrue(engine.computeCpi() > 1.5, "Heavy minting must drive CPI > 1.5");
        long inflatedPrice = engine.quotePrice(iron, 1, 200L, "plains", Season.NONE, true).finalUnitPrice();
        assertTrue(inflatedPrice > baselinePrice, "Inflation must increase item prices");

        // Depositing cash into ATM preserves total money supply M
        long mBeforeDeposit = engine.totalMoneySupply();
        engine.recordCashDepositedToBank(5_000L);
        assertEquals(mBeforeDeposit, engine.totalMoneySupply());

        // Extreme over-minting is strictly clamped by guardrail maxPriceMultiplier (4.0)
        engine.recordMinting(10_000_000L);
        assertEquals(4.0, engine.computeCpi(), 1e-6);
        MacroEconomyEngine.PriceQuote extremeHigh = engine.quotePrice(iron, 10, 1_000_000L, "plains", Season.NONE, true);
        assertTrue(extremeHigh.combinedMultiplier() <= 4.0 + 1e-9);

        // Deflation scenario: modest money supply + heavy trading + money sinks
        MacroEconomyEngine defl = new MacroEconomyEngine();
        defl.recordMinting(4_000L);
        defl.recordMoneySink(3_500L);
        defl.recordTrade("minecraft:iron_ingot", 500, 20L, true);
        assertTrue(defl.computeCpi() < 0.85, "Heavy goods circulation + sinks must drive deflation");
        assertEquals(MacroEconomyEngine.InflationState.DEFLATION, defl.getInflationState());

        // Guardrail floor: combined multiplier >= 0.25 and unit price >= 1 IC
        MacroEconomyEngine.PriceQuote extremeLow = defl.quotePrice(
                GoodsCatalog.createDefault().getById("tyc2:rotten_flesh").orElseThrow(),
                1, 0L, "plains", Season.NONE, false
        );
        assertTrue(extremeLow.combinedMultiplier() >= 0.25 - 1e-9);
        assertTrue(extremeLow.finalUnitPrice() >= 1L);

        // Per-item supply-demand elasticity & mean reversion
        MacroEconomyEngine elastic = new MacroEconomyEngine();
        elastic.recordTrade("minecraft:wheat", 128, 3L, true); // player dumps 4 stacks into villages
        double glutFactor = elastic.computeSupplyElasticityFactor("minecraft:wheat");
        assertTrue(glutFactor < 0.85, "Oversupply must drop elasticity factor");
        elastic.advanceDemandCycle(12_000L);
        assertTrue(elastic.computeSupplyElasticityFactor("minecraft:wheat") > glutFactor,
                "Mean reversion must decay flow toward 0 each cycle");

        // 48-entry ring buffer cap & JSON persistence
        for (int i = 0; i < 60; i++) {
            elastic.advanceDemandCycle(24_000L + i * 12_000L);
        }
        assertEquals(MacroEconomyEngine.MAX_HISTORY_SNAPSHOTS, elastic.history().size());
        MacroEconomyEngine restored = MacroEconomyEngine.fromJson(elastic.toJson(), TyC2Config.defaults());
        assertEquals(elastic.history().size(), restored.history().size());
        assertEquals(elastic.totalMoneySupply(), restored.totalMoneySupply());
        assertEquals(elastic.computeCpi(), restored.computeCpi(), 1e-6);

        // Municipal upkeep burns 2% of excess reserve above R_target (Lv1 R_target = 350)
        assertEquals(0L, engine.computeMunicipalUpkeep(1, 350L));
        assertEquals(20L, engine.computeMunicipalUpkeep(1, 1_350L));
    }

    @Test
    void seasonalArbitrageAcrossAllFourSeasonsAndMods() {
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        MacroEconomyEngine economy = new MacroEconomyEngine();
        DemandGenerator generator = new DemandGenerator(catalog, TyC2Config.defaults());

        // Check pricing directly on seasonal crops across Vanilla, IC2, FD, KC
        TradeGood wheat = catalog.getById("tyc2:wheat").orElseThrow();
        TradeGood melon = catalog.getById("tyc2:melon_slice").orElseThrow();
        TradeGood beetroot = catalog.getById("tyc2:beetroot").orElseThrow();
        TradeGood ic2Hops = catalog.getById("tyc2:ic2_hops").orElseThrow();
        TradeGood fdOnion = catalog.getById("tyc2:fd_onion").orElseThrow();
        TradeGood fdCabbage = catalog.getById("tyc2:fd_cabbage").orElseThrow();
        TradeGood kcChili = catalog.getById("tyc2:kc_chili").orElseThrow();
        TradeGood kcTea = catalog.getById("tyc2:kc_tea_leaf").orElseThrow();

        // In-season sell discount (0.75x) vs off-season buy premium (1.40x..1.65x)
        assertEquals(0.75, economy.computeSeasonFactor(wheat, Season.SPRING, false), 1e-6);
        assertEquals(1.65, economy.computeSeasonFactor(wheat, Season.WINTER, true), 1e-6);
        assertEquals(0.75, economy.computeSeasonFactor(melon, Season.SUMMER, false), 1e-6);
        assertEquals(1.55, economy.computeSeasonFactor(melon, Season.SPRING, true), 1e-6);
        assertEquals(0.75, economy.computeSeasonFactor(beetroot, Season.WINTER, false), 1e-6);
        assertEquals(0.75, economy.computeSeasonFactor(fdCabbage, Season.WINTER, false), 1e-6);
        assertEquals(1.65, economy.computeSeasonFactor(fdOnion, Season.WINTER, true), 1e-6);
        assertEquals(1.40, economy.computeSeasonFactor(ic2Hops, Season.SPRING, true), 1e-6);
        assertEquals(0.75, economy.computeSeasonFactor(kcChili, Season.SUMMER, false), 1e-6);
        assertEquals(0.75, economy.computeSeasonFactor(kcTea, Season.SPRING, false), 1e-6);

        // Across 30 Winter trials, agrarian villages MUST NEVER sell off-season crops
        // AND MUST generate premium off-season BUY orders!
        boolean sawOffSeasonBuyOrder = false;
        for (int seed = 0; seed < 30; seed++) {
            DemandGenerator.GeneratedDemand demand = generator.generate(
                    3, "minecraft:plains", 5_000L, IndustrialTier.TIER_2,
                    Season.WINTER, economy, allModItems(), new Random(seed)
            );
            assertEquals(DemandGenerator.sellOrderCountForLevel(3), demand.sellOrders().size());
            assertEquals(DemandGenerator.buyOrderCountForLevel(3), demand.buyOrders().size());

            for (DemandGenerator.GeneratedOrder sell : demand.sellOrders()) {
                TradeGood good = catalog.getById(sell.goodId()).orElseThrow();
                assertFalse(good.isSeasonalCrop() && good.isOffSeason(Season.WINTER),
                        "Village MUST NOT sell off-season crop in WINTER: " + good.itemId());
            }
            for (DemandGenerator.GeneratedOrder buy : demand.buyOrders()) {
                if (buy.seasonalHighlight()) {
                    sawOffSeasonBuyOrder = true;
                    assertTrue(buy.unitPrice() > buy.baseUnitPrice(),
                            "Off-season buy order must offer a scarcity premium: " + buy);
                }
            }
        }
        assertTrue(sawOffSeasonBuyOrder, "Winter demand must generate off-season arbitrage buy orders");

        // Verify Summer vs Autumn sell exclusion too (e.g., pumpkin never sold in SUMMER)
        for (int seed = 0; seed < 20; seed++) {
            DemandGenerator.GeneratedDemand summer = generator.generate(
                    2, "minecraft:plains", 2_000L, IndustrialTier.TIER_1,
                    Season.SUMMER, economy, allModItems(), new Random(100 + seed)
            );
            for (DemandGenerator.GeneratedOrder sell : summer.sellOrders()) {
                assertNotEquals("minecraft:pumpkin", sell.itemId());
                assertNotEquals("minecraft:apple", sell.itemId());
                assertNotEquals("farmersdelight:onion", sell.itemId());
                assertNotEquals("kaleidoscope_cookery:lettuce", sell.itemId());
            }
        }
    }

    @Test
    void runtimeRegistryFilterIndustrialTierGatingAndProgressionCurve() {
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        MacroEconomyEngine economy = new MacroEconomyEngine();
        DemandGenerator generator = new DemandGenerator(catalog, TyC2Config.defaults());

        // 1. Uninstalled mod items NEVER appear when itemExists allows only minecraft:*
        for (int level = 1; level <= 10; level++) {
            DemandGenerator.GeneratedDemand vanillaDemand = generator.generate(
                    level, "minecraft:plains", 100_000L, IndustrialTier.TIER_5,
                    Season.AUTUMN, economy, vanillaOnlyItems(), new Random(level * 17L)
            );
            assertEquals(DemandGenerator.sellOrderCountForLevel(level), vanillaDemand.sellOrders().size());
            assertEquals(DemandGenerator.buyOrderCountForLevel(level), vanillaDemand.buyOrders().size());
            for (DemandGenerator.GeneratedOrder order : vanillaDemand.allOrders()) {
                assertTrue(order.itemId().startsWith("minecraft:"),
                        "Uninstalled mod item leaked into pure Vanilla demand: " + order.itemId());
            }
        }

        // 2. Industrial tier milestone detection in both IC2 (verified 1.0.5 IDs) and Vanilla modes + company aggregation
        assertEquals("ic2:resin", catalog.getById("tyc2:ic2_sticky_resin").orElseThrow().itemId());
        assertEquals(IndustrialTier.TIER_1, IndustrialTier.detectFromItemId("ic2:macerator", true));
        assertEquals(IndustrialTier.TIER_1, IndustrialTier.detectFromItemId("ic2:machine", true));
        assertEquals(IndustrialTier.TIER_2, IndustrialTier.detectFromItemId("ic2:centrifuge", true));
        assertEquals(IndustrialTier.TIER_2, IndustrialTier.detectFromItemId("ic2:advanced_machine", true));
        assertEquals(IndustrialTier.TIER_4, IndustrialTier.detectFromItemId("ic2:uranium_fuel_rod", true));
        assertEquals(IndustrialTier.TIER_5, IndustrialTier.detectFromItemId("ic2:iridium_shard", true));
        assertEquals(IndustrialTier.TIER_5, IndustrialTier.detectFromItemId("ic2:iridium_neutron_reflector", true));
        assertEquals(IndustrialTier.TIER_2, IndustrialTier.detectFromItemId("minecraft:diamond_pickaxe", false));
        assertEquals(IndustrialTier.TIER_5, IndustrialTier.detectFromItemId("minecraft:elytra", false));

        IndustrialTier.Tracker tracker = new IndustrialTier.Tracker();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        tracker.recordItemMilestone(p1, "ic2:batbox", true);
        tracker.recordItemMilestone(p2, "ic2:mfe", true);
        assertEquals(IndustrialTier.TIER_3, tracker.getMaxTier(List.of(p1, p2)));

        // 3. Industrial tier cap keeps endgame Iridium locked until Player Tier 5 + Village Level 10
        assertEquals(0, IndustrialTier.effectiveUnlockedTier(3, IndustrialTier.TIER_1, true));
        assertEquals(3, IndustrialTier.effectiveUnlockedTier(10, IndustrialTier.TIER_4, true));
        assertEquals(5, IndustrialTier.effectiveUnlockedTier(10, IndustrialTier.TIER_5, true));

        for (int seed = 0; seed < 25; seed++) {
            DemandGenerator.GeneratedDemand capped = generator.generate(
                    10, "minecraft:plains", 200_000L, IndustrialTier.TIER_4,
                    Season.NONE, economy, allModItems(), new Random(500 + seed)
            );
            for (DemandGenerator.GeneratedOrder order : capped.allOrders()) {
                TradeGood g = catalog.getById(order.goodId()).orElseThrow();
                assertTrue(g.industrialTierRequired().level() <= 3,
                        "Tier 4 player must not bypass tier cap into Tier 4/5 items: " + g);
            }
        }

        // 4. Tier 5 player at Lv 10 unlocks Iridium Shards, Rare Alloys, Enchanted Books, AND Fine Cuisine (FD/KC)
        Set<String> seenSellItems = new HashSet<>();
        Set<GoodsCategory> seenBuyCategories = new HashSet<>();
        Set<String> seenFineMealNamespaces = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            DemandGenerator.GeneratedDemand metro = generator.generate(
                    10, "minecraft:snowy_plains", 500_000L, IndustrialTier.TIER_5,
                    Season.NONE, economy, allModItems(), new Random(1000 + seed)
            );
            for (DemandGenerator.GeneratedOrder sell : metro.sellOrders()) {
                seenSellItems.add(sell.itemId());
            }
            for (DemandGenerator.GeneratedOrder buy : metro.buyOrders()) {
                seenBuyCategories.add(buy.category());
                if (buy.category() == GoodsCategory.FOOD_FINE) {
                    seenFineMealNamespaces.add(buy.itemId().substring(0, buy.itemId().indexOf(':')));
                }
            }
        }
        assertTrue(seenSellItems.contains("ic2:iridium_shard"), "Lv 10 Tier 5 village must sell ic2:iridium_shard");
        assertTrue(seenSellItems.contains("minecraft:enchanted_book"), "Lv 10 village must sell rare enchanted books");
        assertTrue(seenSellItems.contains("ic2:alloy"), "Lv 10 village must sell rare alloys");
        assertTrue(seenBuyCategories.contains(GoodsCategory.FOOD_FINE), "Lv 10 village must buy fine cuisine");
        assertTrue(seenFineMealNamespaces.contains("farmersdelight"), "Lv 10 must buy Farmer's Delight meals when loaded");
        assertTrue(seenFineMealNamespaces.contains("kaleidoscope_cookery"), "Lv 10 must buy Kaleidoscope Cookery dishes when loaded");
    }
}
