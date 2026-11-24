package com.tycooncraft.tyc2.core;

import com.tycooncraft.tyc2.api.TyC2RegistryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class JsonConfigAndApiTest {

    @AfterEach
    void resetApi() {
        TyC2RegistryApi.reset();
    }

    @Test
    void jsonLiteRfc8259RoundTripAndValidation() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "商业时代2: \"New Era\"\nLine2");
        doc.put("intVal", 42L);
        doc.put("negVal", -100L);
        doc.put("floatVal", 0.55);
        doc.put("flag", true);
        doc.put("nothing", null);
        doc.put("items", List.of("ic2:coin", "tyc2:coin_1000", 123L, false));

        String compact = JsonLite.toJson(doc);
        String pretty = JsonLite.toPrettyJson(doc);

        assertEquals(doc, JsonLite.parseObject(compact));
        assertEquals(doc, JsonLite.parseObject(pretty));

        // Unicode escape decoding
        assertEquals("中文", JsonLite.parse("\"\\u4e2d\\u6587\""));

        // Strict RFC-8259 syntax rejection
        assertThrows(JsonLite.JsonParseException.class, () -> JsonLite.parse("{a: 1}"));
        assertThrows(JsonLite.JsonParseException.class, () -> JsonLite.parse("[1, 2,]"));
        assertThrows(JsonLite.JsonParseException.class, () -> JsonLite.parse("01"));
        assertThrows(JsonLite.JsonParseException.class, () -> JsonLite.parse("{\"a\":1} trailing"));
        assertThrows(JsonLite.JsonParseException.class, () -> JsonLite.parse("\"unterminated"));
    }

    @Test
    void configLoadSaveAndGuardrailClamping(@TempDir Path tempDir) throws Exception {
        Path cfgPath = tempDir.resolve("config/tyc2/general.json");
        TyC2Config created = TyC2Config.loadOrCreate(cfgPath);
        assertTrue(Files.exists(cfgPath));
        assertTrue(created.disableIc2MetalFormerCoinRecipe());
        assertTrue(created.enforceIndustrialTierCap());
        assertEquals(0.25, created.minPriceMultiplier(), 1e-6);
        assertEquals(4.00, created.maxPriceMultiplier(), 1e-6);
        assertEquals(12_000, created.demandRefreshTicks());
        assertEquals(18_000, created.expansionIntervalTicks());
        assertEquals(100L, created.negotiationCostIc());

        created.setMinPriceMultiplier(0.40)
                .setMaxPriceMultiplier(3.50)
                .setDisableIc2MetalFormerCoinRecipe(false)
                .setNegotiationCostIc(250L);
        created.save(cfgPath);

        TyC2Config reloaded = TyC2Config.loadOrCreate(cfgPath);
        assertFalse(reloaded.disableIc2MetalFormerCoinRecipe());
        assertEquals(0.40, reloaded.minPriceMultiplier(), 1e-6);
        assertEquals(3.50, reloaded.maxPriceMultiplier(), 1e-6);
        assertEquals(250L, reloaded.negotiationCostIc());

        // Extreme out-of-range JSON values are safely clamped
        TyC2Config clamped = TyC2Config.fromJson(
                "{\"minPriceMultiplier\": -5, \"maxPriceMultiplier\": 999, \"demandRefreshTicks\": 1,"
                        + "\"aidGatherTicksSmall\": 0, \"maxQueuedAidProjects\": 99,"
                        + "\"lowUnemploymentThreshold\": 5.0, \"lowUnempRestockMinMult\": 0.5,"
                        + "\"lowUnempRestockMaxMult\": 0.2, \"lowUnempQtyMinMult\": 0.1,"
                        + "\"lowUnempQtyMaxMult\": 99.0}");
        assertEquals(0.05, clamped.minPriceMultiplier(), 1e-6);
        assertEquals(20.0, clamped.maxPriceMultiplier(), 1e-6);
        assertEquals(200, clamped.demandRefreshTicks());
        assertEquals(20, clamped.aidGatherTicksSmall());
        assertEquals(16, clamped.maxQueuedAidProjects());
        assertEquals(1.0, clamped.lowUnemploymentThreshold(), 1e-6);
        assertEquals(1.0, clamped.lowUnempRestockMinMult(), 1e-6);
        assertEquals(1.0, clamped.lowUnempRestockMaxMult(), 1e-6);
        assertEquals(1.0, clamped.lowUnempQtyMinMult(), 1e-6);
        assertEquals(10.0, clamped.lowUnempQtyMaxMult(), 1e-6);

        // Aid & employment config round-trip persistence
        created.setAidGatherTicksSmall(2_400)
                .setAidGatherTicksLarge(4_800)
                .setAidLaborSpeedPerUnemployed(0.2)
                .setAidMaxLaborSpeedBonus(1.5)
                .setMaxQueuedAidProjects(5)
                .setLowUnemploymentThreshold(0.20)
                .setLowUnempRestockMinMult(1.30)
                .setLowUnempRestockMaxMult(1.60)
                .setLowUnempQtyMinMult(1.20)
                .setLowUnempQtyMaxMult(1.45)
                .setEnableModdedProfessions(false)
                .setModdedProfessionBlacklist(List.of("modx:guard", "badmod:*"));
        created.save(cfgPath);

        TyC2Config reloadedAid = TyC2Config.loadOrCreate(cfgPath);
        assertEquals(2_400, reloadedAid.aidGatherTicksSmall());
        assertEquals(4_800, reloadedAid.aidGatherTicksLarge());
        assertEquals(0.2, reloadedAid.aidLaborSpeedPerUnemployed(), 1e-6);
        assertEquals(1.5, reloadedAid.aidMaxLaborSpeedBonus(), 1e-6);
        assertEquals(5, reloadedAid.maxQueuedAidProjects());
        assertEquals(0.20, reloadedAid.lowUnemploymentThreshold(), 1e-6);
        assertEquals(1.30, reloadedAid.lowUnempRestockMinMult(), 1e-6);
        assertEquals(1.60, reloadedAid.lowUnempRestockMaxMult(), 1e-6);
        assertEquals(1.20, reloadedAid.lowUnempQtyMinMult(), 1e-6);
        assertEquals(1.45, reloadedAid.lowUnempQtyMaxMult(), 1e-6);
        assertFalse(reloadedAid.enableModdedProfessions());
        assertTrue(reloadedAid.isModdedProfessionBlacklisted("modx:guard"));
        assertTrue(reloadedAid.isModdedProfessionBlacklisted("badmod:anything"));
        assertFalse(reloadedAid.isModdedProfessionBlacklisted("create:engineer"));
    }

    @Test
    void goodsCatalogJsonMergeAndSeasonalOverlay(@TempDir Path tempDir) throws Exception {
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        Path customGoodsPath = tempDir.resolve("custom_goods.json");
        assertEquals(0, catalog.loadExternalConfig(customGoodsPath, true));
        assertTrue(Files.exists(customGoodsPath));

        String datapackGoods = """
                {
                  "goods": [
                    {
                      "id": "modx:truffle",
                      "itemId": "modx:truffle",
                      "category": "FOOD_FINE",
                      "basePrice": 75,
                      "minLevel": 7,
                      "maxLevel": 10,
                      "industrialTierRequired": "TIER_1",
                      "harvestSeasons": ["AUTUMN"],
                      "isSellableByVillage": false,
                      "isBuyableByVillage": true,
                      "baseMinQty": 2,
                      "baseMaxQty": 6
                    }
                  ]
                }
                """;
        assertEquals(1, catalog.mergeFromJson(datapackGoods));
        TradeGood truffle = catalog.getById("modx:truffle").orElseThrow();
        assertEquals(75L, truffle.basePrice());
        assertTrue(truffle.isInSeason(Season.AUTUMN));
        assertTrue(truffle.isOffSeason(Season.WINTER));

        // Seasonal overlay overrides harvest seasons for wheat
        String overlay = "{\"seasons\": {\"minecraft:wheat\": [\"SPRING\", \"WINTER\"]}}";
        assertTrue(catalog.mergeSeasonsOverlayJson(overlay) >= 1);
        TradeGood wheat = catalog.getById("tyc2:wheat").orElseThrow();
        assertTrue(wheat.isInSeason(Season.WINTER));
        assertTrue(wheat.isOffSeason(Season.SUMMER));
    }

    @Test
    void registryApiExtendsGoodsMintRecipesSeasonsAndIndustrialTiers() {
        // 1. Custom TradeGood
        TradeGood customGood = TradeGood.builder("ext:quantum_tea", "ext:quantum_tea", GoodsCategory.FOOD_FINE, 120)
                .levelRange(7, 10)
                .industrialTier(IndustrialTier.TIER_1)
                .sellable(false)
                .buyable(true)
                .quantityRange(2, 5)
                .build();
        TyC2RegistryApi.registerTradeGood(customGood);

        GoodsCatalog catalog = GoodsCatalog.createDefault();
        assertTrue(catalog.getAvailableGoods(id -> true).stream().anyMatch(g -> g.id().equals("ext:quantum_tea")));
        assertFalse(catalog.getAvailableGoods(id -> !id.equals("ext:quantum_tea")).stream().anyMatch(g -> g.id().equals("ext:quantum_tea")));

        // 2. Custom MintRecipe
        MintRecipe customMint = new MintRecipe(
                "ext:brass_mint",
                List.of(MintIngredient.ofItem("create:brass_ingot", 1)),
                CurrencyDenomination.X20,
                1,
                700,
                80,
                null
        );
        TyC2RegistryApi.registerMintRecipe(customMint);
        MintRecipeRegistry mintRegistry = MintRecipeRegistry.createDefault();
        var matched = mintRegistry.findMatch("create:brass_ingot", 1, null, 0, null, mod -> true);
        assertTrue(matched.isPresent());
        assertEquals("ext:brass_mint", matched.get().recipe().id());

        // 3. Custom SeasonProvider
        assertEquals(Season.NONE, TyC2RegistryApi.resolveSeason("minecraft:overworld"));
        TyC2RegistryApi.registerSeasonProvider("custom_cal", dim ->
                "minecraft:overworld".equals(dim) ? Season.AUTUMN : Season.NONE);
        assertEquals(Season.AUTUMN, TyC2RegistryApi.resolveSeason("minecraft:overworld"));
        assertEquals(Season.NONE, TyC2RegistryApi.resolveSeason("minecraft:the_nether"));

        // 4. Custom IndustrialTierProvider
        assertEquals(IndustrialTier.TIER_0, IndustrialTier.detectFromItemId("mekanism:atomic_disassembler", false));
        TyC2RegistryApi.registerIndustrialTierProvider("mekanism", (itemId, ic2Loaded) ->
                "mekanism:atomic_disassembler".equals(itemId) ? IndustrialTier.TIER_4 : IndustrialTier.TIER_0);
        assertEquals(IndustrialTier.TIER_4, IndustrialTier.detectFromItemId("mekanism:atomic_disassembler", false));

        // Verify DemandGenerator seamlessly sees API-registered good
        DemandGenerator gen = new DemandGenerator(catalog, TyC2Config.defaults());
        DemandGenerator.GeneratedDemand demand = gen.generate(
                8, "plains", 50_000L, IndustrialTier.TIER_4, Season.AUTUMN,
                new MacroEconomyEngine(), "ext:quantum_tea"::equals, new Random(7)
        );
        assertEquals(1, demand.buyOrders().size());
        assertEquals("ext:quantum_tea", demand.buyOrders().get(0).itemId());
    }
}
