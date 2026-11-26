package com.tycooncraft.tyc2.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification tests covering {@code ic2:copper_ingot} cleanup in {@link MintIngredient},
 * optional EcoCraft 2 ({@code ecc2}) trade goods registration and graceful absence filtering,
 * economic arbitrage guardrails, and 4-target EMI/WTHIT/localization compat wiring.
 */
final class Ecc2AndCompatWiringTest {

    private static final Set<String> EXPECTED_ECC2_ITEMS = Set.of(
            "ecc2:sunnarium",
            "ecc2:sunnarium_alloy",
            "ecc2:iridium_ingot",
            "ecc2:natural_gas_cell",
            "ecc2:water_gas_cell",
            "ecc2:culture_medium_cell",
            "ecc2:methane_ice_cluster"
    );

    @Test
    void mintIngredientExcludesIc2CopperIngotAndMatchesVanillaPlusStandardTags() {
        MintIngredient copper = MintIngredient.ofTag("#ingots/copper", 1);

        // Vanilla copper ingot & standard c:/forge: tag expansion remain intact
        assertEquals("minecraft:copper_ingot", copper.representativeItemId());
        assertEquals(List.of("c:ingots/copper", "forge:ingots/copper"), copper.expandedTagIds());
        assertTrue(copper.matchesItem("minecraft:copper_ingot", null));

        // Legacy ic2:copper_ingot must NOT match built-in fallback
        assertFalse(copper.matchesItem("ic2:copper_ingot", null),
                "ic2:copper_ingot must be removed from MintIngredient built-in copper fallback");

        MintRecipeRegistry registry = MintRecipeRegistry.createDefault();
        assertTrue(registry.findMatch("ic2:copper_ingot", 1, null, 0, null, mod -> true, false).isEmpty(),
                "MintRecipeRegistry must not accept ic2:copper_ingot without a tag matcher");
        assertTrue(registry.findMatch("ic2:copper_ingot", 1, null, 0, null, mod -> true, true).isEmpty(),
                "Primitive Mint must not accept ic2:copper_ingot without a tag matcher");
        assertTrue(registry.findMatch("minecraft:copper_ingot", 1, null, 0, null, mod -> false, true).isPresent(),
                "minecraft:copper_ingot must still match tyc2:mint_copper_1");

        // Third-party ingots tagged c:ingots/copper or forge:ingots/copper still match cleanly
        MintIngredient.TagMatcher cTag = (item, tag) -> "create:copper_ingot".equals(item) && "c:ingots/copper".equals(tag);
        MintIngredient.TagMatcher forgeTag = (item, tag) -> "thermal:copper_ingot".equals(item) && "forge:ingots/copper".equals(tag);
        assertTrue(copper.matchesItem("create:copper_ingot", cTag));
        assertTrue(copper.matchesItem("thermal:copper_ingot", forgeTag));

        // Ensure no built-in MintRecipe or TradeGood references retired item IDs
        Set<String> bannedIds = Set.of("ic2:copper_ingot", "ecc2:uranium_ingot", "ecc2:biogas_cell");
        for (MintRecipe recipe : registry.allRecipes()) {
            for (MintIngredient ing : recipe.ingredients()) {
                assertFalse(bannedIds.contains(ing.spec()), "Retired item ID in MintRecipe " + recipe.id() + ": " + ing.spec());
            }
        }
        for (TradeGood good : GoodsCatalog.createDefault().allGoods()) {
            assertFalse(bannedIds.contains(good.itemId()), "Retired item ID in GoodsCatalog " + good.id() + ": " + good.itemId());
        }
    }

    @Test
    void optionalEcc2GoodsAreRegisteredBalancedAndGracefullyFilteredWhenAbsent() {
        GoodsCatalog catalog = GoodsCatalog.createDefault();

        // All 7 expected EcC2 trade goods exist and are guarded by requiredModId="ecc2"
        List<TradeGood> ecc2Goods = catalog.allGoods().stream()
                .filter(g -> "ecc2".equals(g.requiredModId()))
                .toList();
        Set<String> registeredEcc2Items = ecc2Goods.stream()
                .map(TradeGood::itemId)
                .collect(Collectors.toSet());
        assertEquals(EXPECTED_ECC2_ITEMS, registeredEcc2Items);

        for (TradeGood good : ecc2Goods) {
            assertEquals("ecc2", good.requiredModId());
            assertTrue(good.basePrice() > 0L, "Positive basePrice required for " + good.id());
            assertTrue(good.isSellableByVillage() || good.isBuyableByVillage(),
                    "EcC2 good must be sellable or buyable: " + good.id());
        }

        // Verify Iridium Ingot arbitrage floor & methane ice -> natural gas anti-arbitrage balance
        TradeGood iridium = catalog.findByItemId("ecc2:iridium_ingot").orElseThrow();
        assertEquals(GoodsCategory.ENDGAME, iridium.category());
        assertEquals(IndustrialTier.TIER_5, iridium.industrialTierRequired());
        assertEquals("#ingots/iridium", iridium.optionalTag());
        assertTrue(iridium.isSellableByVillage());

        MacroEconomyEngine deflated = new MacroEconomyEngine();
        deflated.recordTrade("minecraft:wheat", 100_000, 100L, true);
        assertTrue(deflated.quotePrice(iridium, 10, 0L, "plains", Season.NONE, false).finalUnitPrice() >= 1_050L,
                "ecc2:iridium_ingot must respect minting arbitrage floor under extreme deflation");

        TradeGood methaneIce = catalog.findByItemId("ecc2:methane_ice_cluster").orElseThrow();
        TradeGood naturalGas = catalog.findByItemId("ecc2:natural_gas_cell").orElseThrow();
        assertTrue(methaneIce.basePrice() * 3L * 0.90 > naturalGas.basePrice(),
                "3x discounted methane_ice_cluster must exceed 1x natural_gas_cell basePrice (no infinite loop)");

        // Graceful absence: when ecc2 is NOT loaded (via modLoaded OR itemExists), ZERO ecc2 items appear
        List<TradeGood> withoutEcc2ByMod = catalog.getAvailableGoods(id -> true, mod -> !"ecc2".equals(mod));
        assertTrue(withoutEcc2ByMod.stream().noneMatch(g -> g.itemId().startsWith("ecc2:")),
                "modLoaded=false for ecc2 must filter out all EcC2 goods");

        List<TradeGood> withoutEcc2ByItem = catalog.getAvailableGoods(id -> !id.startsWith("ecc2:"));
        assertTrue(withoutEcc2ByItem.stream().noneMatch(g -> g.itemId().startsWith("ecc2:")),
                "itemExists=false for ecc2:* must filter out all EcC2 goods");

        DemandGenerator generator = new DemandGenerator(catalog, TyC2Config.defaults());
        MacroEconomyEngine economy = new MacroEconomyEngine();
        for (int lv = 1; lv <= 10; lv++) {
            DemandGenerator.GeneratedDemand demand = generator.generate(
                    lv, "minecraft:snowy_plains", 200_000L, IndustrialTier.TIER_5,
                    Season.NONE, economy, id -> id.startsWith("minecraft:"), new Random(lv * 31L)
            );
            for (DemandGenerator.GeneratedOrder order : demand.allOrders()) {
                assertFalse(order.itemId().startsWith("ecc2:"),
                        "EcC2 item leaked into trade orders when ecc2 is absent: " + order.itemId());
            }
        }

        // When ecc2 IS loaded, EcC2 goods become available and appear in Lv 4-10 demand generation
        List<TradeGood> withEcc2 = catalog.getAvailableGoods(
                id -> id.startsWith("ecc2:"),
                "ecc2"::equals
        );
        assertEquals(EXPECTED_ECC2_ITEMS.size(), withEcc2.size());

        Set<String> generatedEcc2Items = new HashSet<>();
        for (int lv : new int[]{4, 6, 8, 10}) {
            for (int seed = 0; seed < 40; seed++) {
                DemandGenerator.GeneratedDemand demand = generator.generate(
                        lv, "minecraft:snowy_plains", 500_000L, IndustrialTier.TIER_5,
                        Season.NONE, economy,
                        id -> id.startsWith("minecraft:") || id.startsWith("ecc2:"),
                        new Random(lv * 1000L + seed)
                );
                for (DemandGenerator.GeneratedOrder order : demand.allOrders()) {
                    if (order.itemId().startsWith("ecc2:")) {
                        generatedEcc2Items.add(order.itemId());
                    }
                }
            }
        }
        assertTrue(generatedEcc2Items.contains("ecc2:iridium_ingot"), "Lv 10 Tier 5 must sell ecc2:iridium_ingot when EcC2 is loaded");
        assertTrue(generatedEcc2Items.contains("ecc2:methane_ice_cluster"), "Snowy Lv 4-8 must sell ecc2:methane_ice_cluster");
        assertTrue(generatedEcc2Items.stream().anyMatch(id -> id.endsWith("_cell")),
                "Mid-tier villages must buy EcC2 gas/culture cells when EcC2 is loaded");
        assertTrue(generatedEcc2Items.contains("ecc2:sunnarium") || generatedEcc2Items.contains("ecc2:sunnarium_alloy"),
                "High-tier cities must buy EcC2 Sunnarium goods when EcC2 is loaded");
    }

    @Test
    void compatMetadataWthitEmiPluginsAndLocalizationParityAreWiredAcrossAllFourTargets() throws Exception {
        Path repoRoot = resolveRepoRoot();

        // 1. Optional ecc2 dependency declared (and NOT mandatory/required) in both NeoForge & Forge 1.19.2 mods.toml
        String neoToml = Files.readString(repoRoot.resolve("src/neoforge/metadata/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        String forgeToml = Files.readString(repoRoot.resolve("forge-1.19.2/src/main/resources/META-INF/mods.toml"), StandardCharsets.UTF_8);
        assertTrue(neoToml.contains("modId=\"ecc2\"") && neoToml.contains("type=\"optional\""),
                "neoforge.mods.toml must declare optional ecc2 dependency");
        assertTrue(forgeToml.contains("modId=\"ecc2\"") && forgeToml.contains("mandatory=false"),
                "forge-1.19.2 mods.toml must declare optional ecc2 dependency");

        // 2. WTHIT plugin descriptors & Java classes exist for all 4 targets
        Map<String, Object> neoWthit = JsonLite.parseObject(
                Files.readString(repoRoot.resolve("src/neoforge/metadata/wthit_plugins.json"), StandardCharsets.UTF_8));
        Map<String, Object> neoEntry = JsonLite.asObject(JsonLite.asObject(neoWthit.get("tyc2:telemetry")).get("entrypoints"));
        assertEquals("com.tycooncraft.tyc2.compat.TyC2WthitCommonPlugin", neoEntry.get("common"));
        assertEquals("com.tycooncraft.tyc2.compat.TyC2WthitClientPlugin", neoEntry.get("client"));
        assertTrue(Files.isRegularFile(repoRoot.resolve("src/neoforge/shared/src/main/java/com/tycooncraft/tyc2/compat/TyC2WthitCommonPlugin.java")));
        assertTrue(Files.isRegularFile(repoRoot.resolve("src/neoforge/shared/src/main/java/com/tycooncraft/tyc2/compat/TyC2WthitClientPlugin.java")));
        assertTrue(Files.isRegularFile(repoRoot.resolve("src/neoforge/shared/src/main/java/com/tycooncraft/tyc2/compat/TyC2ProbeHelper.java")));

        Map<String, Object> forgeWthit = JsonLite.parseObject(
                Files.readString(repoRoot.resolve("forge-1.19.2/src/main/resources/wthit_plugins.json"), StandardCharsets.UTF_8));
        Map<String, Object> forgeEntry = JsonLite.asObject(JsonLite.asObject(forgeWthit.get("tyc2:telemetry")).get("entrypoints"));
        assertEquals("com.tycooncraft.tyc2.forge.compat.TyC2WthitPlugin", forgeEntry.get("common"));
        assertEquals("com.tycooncraft.tyc2.forge.compat.TyC2WthitPlugin", forgeEntry.get("client"));
        assertTrue(Files.isRegularFile(repoRoot.resolve("forge-1.19.2/src/main/java/com/tycooncraft/tyc2/forge/compat/TyC2WthitPlugin.java")));
        assertTrue(Files.isRegularFile(repoRoot.resolve("forge-1.19.2/src/main/java/com/tycooncraft/tyc2/forge/compat/TyC2ProbeHelper.java")));

        // 3. EMI plugin entrypoints exist for 1.21.1, 26.x (26.1.2 + 26.2), and Forge 1.19.2
        List<Path> emiPlugins = List.of(
                repoRoot.resolve("src/neoforge/1.21.1/src/main/java/com/tycooncraft/tyc2/compat/TyC2EmiPlugin.java"),
                repoRoot.resolve("src/neoforge/26.x/src/main/java/com/tycooncraft/tyc2/compat/TyC2EmiPlugin.java"),
                repoRoot.resolve("forge-1.19.2/src/main/java/com/tycooncraft/tyc2/forge/compat/TyC2EmiPlugin.java")
        );
        for (Path emiPath : emiPlugins) {
            assertTrue(Files.isRegularFile(emiPath), "Missing EMI plugin: " + emiPath);
            String src = Files.readString(emiPath, StandardCharsets.UTF_8);
            assertTrue(src.contains("@EmiEntrypoint"), "EMI plugin must be annotated @EmiEntrypoint: " + emiPath);
            assertTrue(src.contains("village_trade") && src.contains("primitive_minting") && src.contains("minting"),
                    "EMI plugin must register primitive_minting, minting, and village_trade: " + emiPath);
        }

        // 4. Localization parity between en_us.json and zh_cn.json including all new EMI/WTHIT/category keys
        Map<String, Object> en = JsonLite.parseObject(
                Files.readString(repoRoot.resolve("assets/tyc2/lang/en_us.json"), StandardCharsets.UTF_8));
        Map<String, Object> zh = JsonLite.parseObject(
                Files.readString(repoRoot.resolve("assets/tyc2/lang/zh_cn.json"), StandardCharsets.UTF_8));
        assertEquals(en.keySet(), zh.keySet(), "en_us.json and zh_cn.json must have identical key sets");

        for (String requiredKey : List.of(
                "config.waila.plugin_tyc2",
                "hud.tyc2.hub.warehouse",
                "emi.category.tyc2.minting",
                "emi.category.tyc2.primitive_minting",
                "emi.category.tyc2.village_trade",
                "emi.info.tyc2.ecc2_trade",
                "emi.info.tyc2.comm_terminal",
                "emi.info.tyc2.cooperation_hub",
                "emi.tyc2.trade.village_buys",
                "emi.tyc2.trade.village_sells",
                "emi.tyc2.trade.price_level",
                "emi.tyc2.trade.tier_category"
        )) {
            assertTrue(en.containsKey(requiredKey), "Missing en_us key: " + requiredKey);
            assertTrue(zh.containsKey(requiredKey), "Missing zh_cn key: " + requiredKey);
        }
        for (GoodsCategory cat : GoodsCategory.values()) {
            String key = "category.tyc2." + cat.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(en.containsKey(key), "Missing category lang key: " + key);
        }

        // 5. Datagen script exposes GOODS_ECC2 with all 7 items and zero retired item IDs
        String datagen = Files.readString(repoRoot.resolve("src/datagen/generate_resources.py"), StandardCharsets.UTF_8);
        assertTrue(datagen.contains("GOODS_ECC2") && datagen.contains("data(\"goods/ecc2\", GOODS_ECC2)"));
        for (String ecc2Id : EXPECTED_ECC2_ITEMS) {
            assertTrue(datagen.contains(ecc2Id), "generate_resources.py missing EcC2 trade good: " + ecc2Id);
        }
        assertFalse(datagen.contains("ic2:copper_ingot"));
        assertFalse(datagen.contains("ecc2:uranium_ingot"));
        assertFalse(datagen.contains("ecc2:biogas_cell"));
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("settings.gradle"))) {
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
