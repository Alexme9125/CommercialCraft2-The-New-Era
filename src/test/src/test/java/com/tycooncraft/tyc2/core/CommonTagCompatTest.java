package com.tycooncraft.tyc2.core;

import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive verification suite for TyC2 Common Item Tag compatibility (Wave 0 + 1 + 2):
 * <ul>
 *   <li>{@link CommonTagAliases} cross-loader ({@code c:} &harr; {@code forge:}) and
 *       cross-version (Forge 1.19.2 &harr; NeoForge 1.21+/26.x) alias expansion &amp; wrench checks</li>
 *   <li>{@link MintRecipeRegistry} &amp; {@link MintIngredient} gem/iridium common-tag support</li>
 *   <li>{@link GoodsCatalog} &amp; {@link DemandGenerator} tag-backed activation without IC2/FD,
 *       deduplication across overlapping mod goods, and {@link VillageTradeOrder#optionalTag()}</li>
 *   <li>{@link ExpansionDesire} exact-first then controlled-tag delivery (Forge 1.19.2 swallow regression)</li>
 *   <li>{@link TriModePowerMath} ATM tag-based fuel matching</li>
 *   <li>{@link MacroEconomyEngine} tag-based raw-ore/iridium anti-arbitrage floors</li>
 *   <li>{@link IndustrialTier} common-tag milestone detection (IC2 &amp; Vanilla modes)</li>
 *   <li>4-target {@code generate_resources.py} recipe, tag path, namespace, and syntax checks</li>
 * </ul>
 */
final class CommonTagCompatTest {

    @Test
    void commonTagAliasesExpandCrossNamespaceAndVersionRenames() {
        // Bare tag expands deterministically to c: then forge:
        assertEquals(List.of("c:ingots/tin", "forge:ingots/tin"),
                CommonTagAliases.expandTagCandidates("#ingots/tin"));

        // Explicit forge: or c: tag cross-expands so Forge 1.19.2 & NeoForge 1.21+ datapacks interoperate
        List<String> fromForge = CommonTagAliases.expandTagCandidates("#forge:ingots/tin");
        assertTrue(fromForge.contains("forge:ingots/tin"));
        assertTrue(fromForge.contains("c:ingots/tin"));

        List<String> fromC = CommonTagAliases.expandTagCandidates("#c:ingots/tin");
        assertTrue(fromC.contains("c:ingots/tin"));
        assertTrue(fromC.contains("forge:ingots/tin"));

        // Non-common namespaces (e.g. minecraft:) must NOT expand into c: or forge:
        assertEquals(List.of("minecraft:logs"), CommonTagAliases.expandTagCandidates("#minecraft:logs"));
        assertEquals(List.of("minecraft:stone_bricks"), CommonTagAliases.expandTagCandidates("#minecraft:stone_bricks"));

        // Forge 1.19.2 <-> NeoForge 1.21+ standard tag renames
        Map<String, String> renamePairs = new LinkedHashMap<>();
        renamePairs.put("stone", "stones");
        renamePairs.put("cobblestone", "cobblestones");
        renamePairs.put("obsidian", "obsidians");
        renamePairs.put("glass", "glass_blocks");
        renamePairs.put("sand", "sands");
        renamePairs.put("gravel", "gravels");
        renamePairs.put("slimeballs", "slime_balls");
        renamePairs.put("leather", "leathers");
        renamePairs.put("string", "strings");
        renamePairs.put("gunpowder", "gunpowders");
        renamePairs.put("netherrack", "netherracks");
        renamePairs.put("shears", "tools/shear");
        renamePairs.put("bread", "foods/bread");
        renamePairs.put("cooked_pork", "foods/cooked_pork");
        renamePairs.put("cooked_beef", "foods/cooked_beef");

        for (Map.Entry<String, String> pair : renamePairs.entrySet()) {
            String forgePath = pair.getKey();
            String neoPath = pair.getValue();
            List<String> expandedFromNeo = CommonTagAliases.expandTagCandidates("#" + neoPath);
            assertTrue(expandedFromNeo.contains("c:" + neoPath), "Missing c:" + neoPath);
            assertTrue(expandedFromNeo.contains("forge:" + forgePath), "Missing forge:" + forgePath);

            List<String> expandedFromForge = CommonTagAliases.expandTagCandidates("#forge:" + forgePath);
            assertTrue(expandedFromForge.contains("forge:" + forgePath), "Missing forge:" + forgePath);
            assertTrue(expandedFromForge.contains("c:" + neoPath), "Missing c:" + neoPath);

            assertEquals("#forge:" + forgePath, CommonTagAliases.displayTagLabel("#" + neoPath, true));
            assertEquals("#c:" + neoPath, CommonTagAliases.displayTagLabel("#" + forgePath, false));
        }

        // Wrench tag checks cover tools/wrench, tools/wrenches, wrenches across c: and forge:
        assertTrue(CommonTagAliases.isWrenchTag("create:wrench",
                (item, tag) -> "create:wrench".equals(item) && "c:tools/wrench".equals(tag)));
        assertTrue(CommonTagAliases.isWrenchTag("mekanism:configurator",
                (item, tag) -> "mekanism:configurator".equals(item) && "forge:wrenches".equals(tag)));
        assertTrue(CommonTagAliases.isWrenchTag("ae2:certus_quartz_wrench",
                (item, tag) -> "ae2:certus_quartz_wrench".equals(item) && "c:tools/wrenches".equals(tag)));
        assertFalse(CommonTagAliases.isWrenchTag("minecraft:stick", (item, tag) -> false));

        // Display label preserves minecraft: tags unchanged
        assertEquals("#minecraft:logs", CommonTagAliases.displayTagLabel("#minecraft:logs", true));
        assertEquals("#minecraft:logs", CommonTagAliases.displayTagLabel("#minecraft:logs", false));
    }

    @Test
    void mintRecipesAcceptCommonTagsForGemsAndIridium() {
        MintRecipeRegistry registry = MintRecipeRegistry.createDefault();

        // Check recipe ingredient specs were migrated to common tags
        MintRecipe emeraldRec = registry.getById("tyc2:mint_gold_emerald_100").orElseThrow();
        assertEquals("#gems/emerald", emeraldRec.ingredients().get(1).spec());

        MintRecipe diamondRec = registry.getById("tyc2:mint_diamond_gold_200").orElseThrow();
        assertEquals("#gems/diamond", diamondRec.ingredients().get(0).spec());

        MintRecipe iridiumRec = registry.getById("tyc2:mint_iridium_alloy_1000").orElseThrow();
        assertEquals("#ingots/iridium", iridiumRec.ingredients().get(0).spec());
        assertEquals("ic2:iridium", iridiumRec.ingredients().get(0).representativeItemId());

        // Built-in fallback matches ic2:iridium and ecc2:iridium_ingot even without live TagMatcher
        assertTrue(registry.findMatch("ecc2:iridium_ingot", 1, "ic2:alloy", 1, null, mod -> true, false).isPresent());

        // Third-party modded iridium and modded gems match via TagMatcher
        MintIngredient.TagMatcher thirdPartyMatcher = (item, tag) ->
                ("techreborn:iridium_ingot".equals(item) && "c:ingots/iridium".equals(tag))
                        || ("modgem:super_diamond".equals(item) && "c:gems/diamond".equals(tag))
                        || ("modgem:super_emerald".equals(item) && "forge:gems/emerald".equals(tag));

        assertTrue(registry.findMatch("techreborn:iridium_ingot", 1, "ic2:alloy", 1,
                thirdPartyMatcher, mod -> true, false).isPresent());
        assertTrue(registry.findMatch("modgem:super_diamond", 1, "minecraft:gold_ingot", 2,
                thirdPartyMatcher, mod -> false, false).isPresent());
        assertTrue(registry.findMatch("minecraft:gold_ingot", 2, "modgem:super_emerald", 2,
                thirdPartyMatcher, mod -> false, false).isPresent());
    }

    @Test
    void goodsCatalogActivatesTagBackedGoodsWithoutHardcodedModAndDeduplicatesOverlaps() {
        GoodsCatalog catalog = GoodsCatalog.createDefault();

        // Simulate environment WITHOUT IC2, EcC2, FD, or KC installed,
        // but WITH third-party mods providing #ingots/tin and #crops/tomato
        Set<String> vanillaOnlyItems = Set.of(
                "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:iron_ingot",
                "minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:coal", "minecraft:oak_log",
                "mekanism:ingot_tin", "croptopia:tomato"
        );
        List<TradeGood> available = catalog.getAvailableGoods(
                vanillaOnlyItems::contains,
                mod -> false,
                tagSpec -> {
                    if ("#ingots/tin".equals(tagSpec)) {
                        return Optional.of("mekanism:ingot_tin");
                    }
                    if ("#crops/tomato".equals(tagSpec)) {
                        return Optional.of("croptopia:tomato");
                    }
                    return Optional.empty();
                }
        );

        // mekanism:ingot_tin should activate ic2_tin_ingot via #ingots/tin even though ic2 is unloaded!
        List<TradeGood> tinGoods = available.stream()
                .filter(g -> "mekanism:ingot_tin".equals(g.itemId()))
                .toList();
        assertEquals(1, tinGoods.size(), "Expected exactly 1 tag-activated tin trade good");
        assertEquals("#ingots/tin", tinGoods.get(0).optionalTag());

        // croptopia:tomato should activate ONLY ONCE even though both fd_tomato and kc_tomato share #crops/tomato!
        List<TradeGood> tomatoGoods = available.stream()
                .filter(g -> "croptopia:tomato".equals(g.itemId()))
                .toList();
        assertEquals(1, tomatoGoods.size(),
                "Overlapping fd_tomato and kc_tomato must deduplicate when activated via #crops/tomato");
        assertEquals("#crops/tomato", tomatoGoods.get(0).optionalTag());

        // When no resolver is provided (legacy 2-arg call), unloaded mod goods stay absent
        List<TradeGood> withoutResolver = catalog.getAvailableGoods(vanillaOnlyItems::contains, mod -> false);
        assertTrue(withoutResolver.stream().noneMatch(g -> "mekanism:ingot_tin".equals(g.itemId())));

        // DemandGenerator 12-arg overload propagates optionalTag onto GeneratedOrder -> VillageTradeOrder
        DemandGenerator gen = new DemandGenerator(catalog, TyC2Config.defaults());
        MacroEconomyEngine econ = new MacroEconomyEngine();
        boolean sawTagOnOrder = false;
        for (int seed = 0; seed < 30 && !sawTagOnOrder; seed++) {
            DemandGenerator.GeneratedDemand demand = gen.generate(
                    3, "minecraft:plains", 5_000L, IndustrialTier.TIER_2, Season.AUTUMN,
                    econ, vanillaOnlyItems::contains, mod -> false,
                    tagSpec -> "#ingots/tin".equals(tagSpec) ? Optional.of("mekanism:ingot_tin") : Optional.empty(),
                    VillageEmploymentStats.EMPTY, WorkplaceCatalog.createDefault(), new Random(seed));
            for (DemandGenerator.GeneratedOrder go : demand.buyOrders()) {
                if (!go.optionalTag().isBlank()) {
                    sawTagOnOrder = true;
                    VillageTradeOrder order = new VillageTradeOrder(
                            "ord_test", OrderSide.VILLAGE_BUYS, go.itemId(), go.goodId(), "",
                            go.quantity(), 0, go.unitPrice(), 1L, 20_000L, false, false, false, go.optionalTag());
                    assertEquals(go.optionalTag(), order.optionalTag());
                    assertEquals(go.optionalTag(), order.withAddedFulfillment(1).optionalTag());
                    assertEquals(go.optionalTag(), order.withAutoFulfill(true).optionalTag());
                    break;
                }
            }
        }
        assertTrue(sawTagOnOrder, "Expected at least one buy order with non-blank optionalTag");

        // Backward-compatible 12-arg and 13-arg VillageTradeOrder constructors default optionalTag to ""
        VillageTradeOrder legacy12 = new VillageTradeOrder(
                "ord_12", OrderSide.VILLAGE_BUYS, "minecraft:iron_ingot", "tyc2:iron_ingot", "",
                10, 0, 20L, 1L, 1000L, false, false);
        assertEquals("", legacy12.optionalTag());
    }

    @Test
    void expansionDesirePrefersExactMatchThenControlledTagWithoutSwallowingItems() {
        ExpansionDesire desire = new ExpansionDesire(
                "exp_house", ProjectType.SMALL_COTTAGE,
                Map.of("minecraft:oak_log", 10, "minecraft:iron_ingot", 4, "minecraft:white_bed", 1),
                Map.of(), 500L, false, false);

        MintIngredient.TagMatcher tagMatcher = (item, tag) ->
                ("biomesoplenty:fir_log".equals(item) && "minecraft:logs".equals(tag))
                        || ("create:iron_ingot".equals(item) && "c:ingots/iron".equals(tag))
                        || ("minecraft:red_bed".equals(item) && "minecraft:beds".equals(tag));

        // Logs and iron ingot allow controlled tag substitutes; beds stay exact-only (null optionalTag)
        assertEquals("#minecraft:logs", ExpansionDesire.optionalTagForMaterial("minecraft:oak_log"));
        assertEquals("#ingots/iron", ExpansionDesire.optionalTagForMaterial("minecraft:iron_ingot"));
        assertNull(ExpansionDesire.optionalTagForMaterial("minecraft:white_bed"));

        assertTrue(ExpansionDesire.matchesMaterial("biomesoplenty:fir_log", "minecraft:oak_log", tagMatcher));
        assertTrue(ExpansionDesire.matchesMaterial("create:iron_ingot", "minecraft:iron_ingot", tagMatcher));
        assertFalse(ExpansionDesire.matchesMaterial("minecraft:red_bed", "minecraft:white_bed", tagMatcher));

        // Put 3 exact oak_log + 10 bop:fir_log + 4 create:iron_ingot + 1 minecraft:white_bed in Inbound
        CooperationHubStorage storage = new CooperationHubStorage();
        storage.insertExternal("minecraft:oak_log", 3, "", 64, false);
        storage.insertExternal("biomesoplenty:fir_log", 10, "", 64, false);
        storage.insertExternal("create:iron_ingot", 4, "", 64, false);
        storage.insertExternal("minecraft:white_bed", 1, "", 1, false);

        assertEquals(13, ExpansionDesire.countAvailableInInbound(storage, "minecraft:oak_log", tagMatcher));
        assertEquals(4, ExpansionDesire.countAvailableInInbound(storage, "minecraft:iron_ingot", tagMatcher));

        // Pull must consume all 3 exact oak_log FIRST, then exactly 7 fir_log (leaving 3 fir_log untouched!),
        // plus 4 create:iron_ingot and 1 white_bed — ZERO swallowed items!
        ExpansionDesire updated = desire.pullAvailableFromInbound(storage, tagMatcher);
        assertTrue(updated.allMaterialsDelivered(), "All materials must be delivered via exact + controlled tag pull");
        assertEquals(0, storage.countInInbound("minecraft:oak_log"), "Exact oak_log must be consumed first");
        assertEquals(3, storage.countInInbound("biomesoplenty:fir_log"),
                "Only remaining 7 fir_log should be pulled, leaving 3 untouched");
        assertEquals(0, storage.countInInbound("create:iron_ingot"));
        assertEquals(0, storage.countInInbound("minecraft:white_bed"));
    }

    @Test
    void atmFuelsArbitrageFloorsAndIndustrialTierRespectCommonTags() {
        // 1. TriModePowerMath ATM fuels
        MintIngredient.TagMatcher fuelMatcher = (item, tag) ->
                ("thermal:coal_coke_block".equals(item) && "c:storage_blocks/coal".equals(tag))
                        || ("mod:super_coal".equals(item) && "minecraft:coals".equals(tag))
                        || ("mod:red_dust".equals(item) && "forge:dusts/redstone".equals(tag))
                        || ("mod:fire_rod".equals(item) && "c:rods/blaze".equals(tag));

        assertEquals(14_400, TriModePowerMath.getFuelEnergyEu("thermal:coal_coke_block", fuelMatcher));
        assertEquals(1_600, TriModePowerMath.getFuelEnergyEu("mod:super_coal", fuelMatcher));
        assertEquals(800, TriModePowerMath.getFuelEnergyEu("mod:red_dust", fuelMatcher));
        assertEquals(2_400, TriModePowerMath.getFuelEnergyEu("mod:fire_rod", fuelMatcher));
        assertTrue(TriModePowerMath.isFuelItem("mod:super_coal", fuelMatcher));
        assertFalse(TriModePowerMath.isFuelItem("minecraft:stick", fuelMatcher));

        // 2. MacroEconomyEngine anti-arbitrage floor for modded raw ores & iridium via TagMatcher
        MintIngredient.TagMatcher oreMatcher = (item, tag) ->
                ("mekanism:raw_tin".equals(item) && "c:raw_materials/tin".equals(tag))
                        || ("techreborn:iridium_ingot".equals(item) && "c:ingots/iridium".equals(tag));
        MacroEconomyEngine engine = new MacroEconomyEngine();
        engine.setMintRecipeRegistry(MintRecipeRegistry.createDefault(), oreMatcher);

        long ic2RawTinFloor = engine.computeMintingArbitrageFloor("ic2:raw_tin");
        long mekRawTinFloor = engine.computeMintingArbitrageFloor("mekanism:raw_tin");
        assertEquals(ic2RawTinFloor, mekRawTinFloor,
                "Modded #raw_materials/tin must receive the same anti-arbitrage floor as ic2:raw_tin");
        assertTrue(mekRawTinFloor >= 6L, "Expected positive tin raw-material floor");

        long trIridiumFloor = engine.computeMintingArbitrageFloor("techreborn:iridium_ingot");
        assertEquals(1_100L, trIridiumFloor,
                "Modded #ingots/iridium must receive the same 1,100 IC anti-arbitrage floor as ic2:iridium");

        // 3. IndustrialTier common-tag milestones in IC2 and Vanilla modes
        MintIngredient.TagMatcher tierMatcher = (item, tag) ->
                ("mekanism:ingot_steel".equals(item) && "c:ingots/steel".equals(tag))
                        || ("techreborn:advanced_circuit".equals(item) && "forge:circuits/advanced".equals(tag))
                        || ("techreborn:iridium_ingot".equals(item) && "c:ingots/iridium".equals(tag))
                        || ("modgem:super_diamond".equals(item) && "c:gems/diamond".equals(tag));

        assertEquals(IndustrialTier.TIER_2, IndustrialTier.detectFromItemId("mekanism:ingot_steel", true, tierMatcher));
        assertEquals(IndustrialTier.TIER_2, IndustrialTier.detectFromItemId("techreborn:advanced_circuit", true, tierMatcher));
        assertEquals(IndustrialTier.TIER_5, IndustrialTier.detectFromItemId("techreborn:iridium_ingot", true, tierMatcher));
        assertEquals(IndustrialTier.TIER_2, IndustrialTier.detectFromItemId("modgem:super_diamond", false, tierMatcher));

        IndustrialTier.Tracker tracker = new IndustrialTier.Tracker();
        UUID playerUuid = UUID.randomUUID();
        assertTrue(tracker.recordItemMilestone(playerUuid, "mekanism:ingot_steel", true, tierMatcher));
        assertEquals(IndustrialTier.TIER_2, tracker.getTier(playerUuid));
    }

    @Test
    void datagenProducesCompliantTagsAndRecipesAcrossAllFourTargets(@TempDir Path tempDir) throws Exception {
        Path repoRoot = resolveRepoRoot();
        Path script = repoRoot.resolve("src/datagen/generate_resources.py");
        assertTrue(Files.isRegularFile(script), "Missing generate_resources.py");

        List<String> targets = List.of("forge-1.19.2", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2");
        Set<String> bannedHardcodedItems = Set.of(
                "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
                "minecraft:redstone", "minecraft:glass_pane", "minecraft:chest",
                "minecraft:iron_block", "minecraft:redstone_block", "minecraft:diamond",
                "minecraft:stone_bricks", "ic2:iron_plate", "ic2:circuit"
        );

        for (String target : targets) {
            boolean isForge = "forge-1.19.2".equals(target);
            boolean is26x = target.startsWith("neoforge-26.");
            Path outDir = tempDir.resolve(target);
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", script.toString(),
                    "--target", target,
                    "--output", outDir.toString()
            );
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, proc.waitFor(), "Datagen failed for " + target + ": " + output);

            String ns = isForge ? "forge" : "c";
            String blockTagDir = isForge ? "tags/blocks" : "tags/block";
            String itemTagDir = isForge ? "tags/items" : "tags/item";
            String recipeDir = isForge ? "recipes" : "recipe";

            // 1. Published common tags exist in correct loader paths
            Path relocTag = outDir.resolve("data/" + ns + "/" + blockTagDir + "/relocation_not_supported.json");
            Path coinsTag = outDir.resolve("data/" + ns + "/" + itemTagDir + "/coins.json");
            assertTrue(Files.isRegularFile(relocTag), target + ": missing " + relocTag);
            assertTrue(Files.isRegularFile(coinsTag), target + ": missing " + coinsTag);

            Map<String, Object> relocMap = JsonLite.parseObject(Files.readString(relocTag, StandardCharsets.UTF_8));
            List<Object> relocValues = JsonLite.asArray(relocMap.get("values"));
            assertNotNull(relocValues);
            assertTrue(relocValues.contains("tyc2:mint_machine"));
            assertTrue(relocValues.contains("tyc2:primitive_mint_machine"));
            assertTrue(relocValues.contains("tyc2:atm_machine"));
            assertTrue(relocValues.contains("tyc2:cooperation_hub"));

            Map<String, Object> coinsMap = JsonLite.parseObject(Files.readString(coinsTag, StandardCharsets.UTF_8));
            List<Object> coinValues = JsonLite.asArray(coinsMap.get("values"));
            assertNotNull(coinValues);
            assertEquals(10, coinValues.size(), target + ": expected 9 tyc2 coins + 1 optional ic2:coin");

            // 2. Zero forbidden paths/namespaces
            if (isForge) {
                assertFalse(Files.exists(outDir.resolve("data/c")), "forge-1.19.2 must not emit data/c/");
            } else {
                assertFalse(Files.exists(outDir.resolve("data/forge")), target + " must not emit data/forge/");
                assertFalse(Files.exists(outDir.resolve("data/c/tags/items")), target + " must not emit plural tags/items");
                assertFalse(Files.exists(outDir.resolve("data/c/tags/blocks")), target + " must not emit plural tags/blocks");
            }

            // 3. Validate all 5 shaped recipes
            String wrongNs = isForge ? "c:" : "forge:";
            for (String recipeName : List.of("primitive_mint_machine", "mint_machine_ic2",
                    "mint_machine_standalone", "atm_machine", "comm_terminal")) {
                Path recipePath = outDir.resolve("data/tyc2/" + recipeDir + "/" + recipeName + ".json");
                assertTrue(Files.isRegularFile(recipePath), target + ": missing recipe " + recipeName);
                Map<String, Object> rec = JsonLite.parseObject(Files.readString(recipePath, StandardCharsets.UTF_8));
                Map<String, Object> keys = JsonLite.asObject(rec.get("key"));
                assertNotNull(keys);
                for (Map.Entry<String, Object> entry : keys.entrySet()) {
                    Object val = entry.getValue();
                    if (is26x) {
                        assertInstanceOf(String.class, val, target + " " + recipeName + " must use 26.x string ingredients");
                        String s = (String) val;
                        assertFalse(bannedHardcodedItems.contains(s), target + " " + recipeName + " hardcodes " + s);
                        assertFalse(s.startsWith("#" + wrongNs), target + " " + recipeName + " wrong namespace " + s);
                    } else {
                        Map<String, Object> obj = JsonLite.asObject(val);
                        assertNotNull(obj, target + " " + recipeName + " must use object ingredients");
                        String item = JsonLite.getString(obj, "item", "");
                        String tag = JsonLite.getString(obj, "tag", "");
                        assertFalse(bannedHardcodedItems.contains(item), target + " " + recipeName + " hardcodes " + item);
                        assertFalse(tag.startsWith(wrongNs), target + " " + recipeName + " wrong namespace " + tag);
                    }
                }
            }

            // 4. Validate Forge 1.19.2 uses forge:* while NeoForge 1.21+/26.x uses c:* across recipes
            String primText = Files.readString(
                    outDir.resolve("data/tyc2/" + recipeDir + "/primitive_mint_machine.json"), StandardCharsets.UTF_8);
            String atmText = Files.readString(
                    outDir.resolve("data/tyc2/" + recipeDir + "/atm_machine.json"), StandardCharsets.UTF_8);
            String ic2Text = Files.readString(
                    outDir.resolve("data/tyc2/" + recipeDir + "/mint_machine_ic2.json"), StandardCharsets.UTF_8);
            String stdText = Files.readString(
                    outDir.resolve("data/tyc2/" + recipeDir + "/mint_machine_standalone.json"), StandardCharsets.UTF_8);
            if (isForge) {
                assertTrue(primText.contains("\"forge:ingots/iron\""), "forge-1.19.2 must use forge:ingots/iron");
                assertTrue(primText.contains("\"minecraft:stone_bricks\""), "forge-1.19.2 must use minecraft:stone_bricks");
                assertTrue(atmText.contains("\"forge:chests/wooden\""), "forge-1.19.2 must use forge:chests/wooden");
                assertTrue(atmText.contains("\"forge:glass_panes\""), "forge-1.19.2 must use forge:glass_panes");
                assertTrue(ic2Text.contains("\"forge:plates/iron\""), "forge-1.19.2 must use forge:plates/iron");
                assertTrue(ic2Text.contains("\"forge:circuits/basic\""), "forge-1.19.2 must use forge:circuits/basic");
                assertTrue(stdText.contains("\"forge:storage_blocks/iron\""), "forge-1.19.2 must use forge:storage_blocks/iron");
            } else {
                assertTrue(primText.contains("c:ingots/iron"), target + " must use c:ingots/iron");
                assertTrue(primText.contains("minecraft:stone_bricks"), target + " must use minecraft:stone_bricks");
                assertTrue(atmText.contains("c:chests/wooden"), target + " must use c:chests/wooden");
                assertTrue(atmText.contains("c:glass_panes"), target + " must use c:glass_panes");
                assertTrue(ic2Text.contains("c:plates/iron"), target + " must use c:plates/iron");
                assertTrue(ic2Text.contains("c:circuits/basic"), target + " must use c:circuits/basic");
                assertTrue(stdText.contains("c:storage_blocks/iron"), target + " must use c:storage_blocks/iron");
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
