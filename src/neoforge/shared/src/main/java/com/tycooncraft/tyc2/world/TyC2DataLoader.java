package com.tycooncraft.tyc2.world;

import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.GoodsCatalog;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.MintRecipeRegistry;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.platform.DiscoveredProfession;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages TyC2's 3-layer extensibility lifecycle:
 * <ol>
 *   <li>Built-in defaults & datapack JSON reload ({@code data/tyc2/{mint_recipes,goods,seasons,levels}/*.json}
 *       and {@code data/<ns>/tyc2/...})</li>
 *   <li>External config folder ({@code config/tyc2/general.json}, {@code custom_goods.json},
 *       {@code custom_mint_recipes.json}), auto-generated with starter templates on first launch</li>
 *   <li>Programmatic Java API ({@link com.tycooncraft.tyc2.api.TyC2RegistryApi})</li>
 * </ol>
 */
public final class TyC2DataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TyC2DataLoader.class);

    private static volatile TyC2Config config = TyC2Config.defaults();
    private static volatile MintRecipeRegistry mintRecipes = MintRecipeRegistry.createDefault();
    private static volatile GoodsCatalog goodsCatalog = GoodsCatalog.createDefault();
    private static volatile VillageLevelTable levelTable = VillageLevelTable.DEFAULT;
    private static volatile WorkplaceCatalog workplaceCatalog = WorkplaceCatalog.createDefault();
    private static volatile DemandGenerator demandGenerator = new DemandGenerator(goodsCatalog, config);

    // Working buffers used during a single datapack reload pass
    private static MintRecipeRegistry stagingRecipes;
    private static GoodsCatalog stagingGoods;
    private static VillageLevelTable stagingLevels;
    private static WorkplaceCatalog stagingWorkplaces;
    private static List<String> stagingSeasonOverlays;

    private TyC2DataLoader() {
    }

    /** Loads external configs on startup and registers the server datapack reload listener. */
    public static void install(IEventBus gameBus) {
        reloadExternalConfigsOnly();
        TyC2Native.registerReloadListener(
                gameBus,
                TyC2DataLoader::beginDatapackReload,
                TyC2DataLoader::acceptDatapackJson,
                TyC2DataLoader::finishDatapackReload
        );
    }

    /** Returns the active configuration loaded from {@code config/tyc2/general.json}. */
    public static TyC2Config config() {
        return config;
    }

    /** Returns the active Minting Machine recipe registry. */
    public static MintRecipeRegistry mintRecipes() {
        return mintRecipes;
    }

    /** Returns the active village trade goods catalog. */
    public static GoodsCatalog goodsCatalog() {
        return goodsCatalog;
    }

    /** Returns the active Lv 1-10 village progression table. */
    public static VillageLevelTable levelTable() {
        return levelTable;
    }

    /** Returns the active aided villager workplace catalog (built-in + datapack + external + modded). */
    public static WorkplaceCatalog workplaceCatalog() {
        return workplaceCatalog;
    }

    /** Returns the active demand generator bound to current catalog & config. */
    public static DemandGenerator demandGenerator() {
        return demandGenerator;
    }

    /** Reloads {@code config/tyc2/*.json} immediately (e.g. startup or {@code /tyc2 reload}). */
    public static synchronized void reloadExternalConfigsOnly() {
        Path configDir = configDirectory();
        try {
            config = TyC2Config.loadOrCreate(configDir.resolve("general.json"));
        } catch (IOException ex) {
            LOGGER.warn("Failed to load config/tyc2/general.json, using defaults: {}", ex.getMessage());
            config = TyC2Config.defaults();
        }

        MintRecipeRegistry recipes = MintRecipeRegistry.createDefault();
        GoodsCatalog goods = GoodsCatalog.createDefault();
        WorkplaceCatalog workplaces = WorkplaceCatalog.createDefault();
        applyExternalOverrides(configDir, recipes, goods, workplaces);
        mergeModdedProfessions(workplaces, config);
        mintRecipes = recipes;
        goodsCatalog = goods;
        workplaceCatalog = workplaces;
        demandGenerator = new DemandGenerator(goodsCatalog, config);
        MacroEconomyEngine.setActiveMintRecipeRegistry(mintRecipes, TyC2Native::itemIdHasTag);
    }

    private static synchronized void beginDatapackReload() {
        stagingRecipes = MintRecipeRegistry.createDefault();
        stagingGoods = GoodsCatalog.createDefault();
        stagingLevels = VillageLevelTable.DEFAULT;
        stagingWorkplaces = WorkplaceCatalog.createDefault();
        stagingSeasonOverlays = new ArrayList<>();
    }

    private static synchronized void acceptDatapackJson(String key, String json) {
        int sep = key.indexOf('|');
        String category = sep >= 0 ? key.substring(0, sep) : "";
        String id = sep >= 0 ? key.substring(sep + 1) : key;
        try {
            switch (category) {
                case "mint_recipes" -> stagingRecipes.mergeFromJson(json);
                case "goods" -> stagingGoods.mergeFromJson(json);
                case "seasons" -> stagingSeasonOverlays.add(json);
                case "workplaces" -> stagingWorkplaces.mergeFromJson(json);
                case "levels" -> {
                    VillageLevelTable parsed = parseLevelsJson(json);
                    if (parsed != null) {
                        stagingLevels = parsed;
                    }
                }
                default -> {
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to parse TyC2 datapack JSON {} ({}): {}", id, category, ex.getMessage());
        }
    }

    private static synchronized void finishDatapackReload() {
        if (stagingSeasonOverlays != null) {
            for (String overlay : stagingSeasonOverlays) {
                try {
                    stagingGoods.mergeSeasonsOverlayJson(overlay);
                } catch (Exception ex) {
                    LOGGER.warn("Failed to apply TyC2 season overlay: {}", ex.getMessage());
                }
            }
        }

        Path configDir = configDirectory();
        try {
            config = TyC2Config.loadOrCreate(configDir.resolve("general.json"));
        } catch (IOException ex) {
            LOGGER.warn("Failed to reload config/tyc2/general.json: {}", ex.getMessage());
        }

        if (stagingWorkplaces == null) {
            stagingWorkplaces = WorkplaceCatalog.createDefault();
        }
        applyExternalOverrides(configDir, stagingRecipes, stagingGoods, stagingWorkplaces);
        mergeModdedProfessions(stagingWorkplaces, config);

        mintRecipes = stagingRecipes;
        goodsCatalog = stagingGoods;
        levelTable = stagingLevels;
        workplaceCatalog = stagingWorkplaces;
        demandGenerator = new DemandGenerator(goodsCatalog, config);
        MacroEconomyEngine.setActiveMintRecipeRegistry(mintRecipes, TyC2Native::itemIdHasTag);

        LOGGER.info(
                "Loaded TyC2 data: {} mint recipes, {} trade goods, {} available workplaces, disableIc2MetalFormerCoinRecipe={}",
                mintRecipes.allRecipes().size(),
                goodsCatalog.allGoods().size(),
                workplaceCatalog.getAvailableWorkplaces(TyC2Native::blockExists).size(),
                config.disableIc2MetalFormerCoinRecipe()
        );
    }

    private static void applyExternalOverrides(
            Path configDir,
            MintRecipeRegistry recipes,
            GoodsCatalog goods,
            WorkplaceCatalog workplaces
    ) {
        try {
            recipes.loadExternalConfig(configDir.resolve("custom_mint_recipes.json"), true);
        } catch (Exception ex) {
            LOGGER.warn("Failed to load config/tyc2/custom_mint_recipes.json: {}", ex.getMessage());
        }
        try {
            goods.loadExternalConfig(configDir.resolve("custom_goods.json"), true);
        } catch (Exception ex) {
            LOGGER.warn("Failed to load config/tyc2/custom_goods.json: {}", ex.getMessage());
        }
        try {
            workplaces.loadExternalConfig(configDir.resolve("custom_workplaces.json"), true);
        } catch (Exception ex) {
            LOGGER.warn("Failed to load config/tyc2/custom_workplaces.json: {}", ex.getMessage());
        }
    }

    private static void mergeModdedProfessions(WorkplaceCatalog workplaces, TyC2Config cfg) {
        workplaces.clearDynamicModdedProfessions();
        if (cfg == null || !cfg.enableModdedProfessions()) {
            return;
        }
        for (DiscoveredProfession dp : TyC2Native.discoverModdedProfessions()) {
            workplaces.registerModdedProfession(
                    dp.professionId(),
                    dp.workstationBlockId(),
                    dp.displayNameFallback(),
                    cfg
            );
        }
    }

    private static VillageLevelTable parseLevelsJson(String json) {
        Map<String, Object> root = JsonLite.parseObject(json);
        List<Object> arr = JsonLite.asArray(root.get("levels"));
        if (arr == null || arr.size() != VillageLevelTable.MAX_LEVEL) {
            return null;
        }
        List<VillageLevelTable.LevelSpec> specs = new ArrayList<>(VillageLevelTable.MAX_LEVEL);
        for (Object elem : arr) {
            Map<String, Object> m = JsonLite.asObject(elem);
            if (m == null) {
                return null;
            }
            specs.add(new VillageLevelTable.LevelSpec(
                    JsonLite.getInt(m, "level", 1),
                    JsonLite.getInt(m, "minPopulation", 2),
                    JsonLite.getLong(m, "minTradeVolume", 0L),
                    JsonLite.getLong(m, "minVillageBalance", 0L),
                    JsonLite.getLong(m, "upgradeCostSink", 0L),
                    JsonLite.getInt(m, "sellOrderCount", 3),
                    JsonLite.getInt(m, "buyOrderCount", 2),
                    JsonLite.getDouble(m, "qtyScale", 1.0),
                    JsonLite.getInt(m, "maxPopulationCap", 8),
                    JsonLite.getInt(m, "expansionRadiusMax", 24)
            ));
        }
        return VillageLevelTable.of(specs);
    }

    private static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("tyc2");
    }
}
