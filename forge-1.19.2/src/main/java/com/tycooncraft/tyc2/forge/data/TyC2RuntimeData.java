package com.tycooncraft.tyc2.forge.data;

import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.GoodsCatalog;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.MintRecipeRegistry;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.util.TyC2VillagerHelpers;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central runtime holder and datapack reload listener for TyC2's 3-layer extensibility:
 * <ol>
 *   <li>Built-in defaults + Datapack JSON ({@code data/<ns>/mint_recipes}, {@code goods},
 *       {@code seasons}, {@code levels}, plus {@code data/<ns>/tyc2/...} third-party paths)</li>
 *   <li>External config folder ({@code config/tyc2/general.json}, {@code custom_goods.json},
 *       {@code custom_mint_recipes.json}) auto-created with clean starter templates</li>
 *   <li>Public Java API ({@link com.tycooncraft.tyc2.api.TyC2RegistryApi})</li>
 * </ol>
 */
public final class TyC2RuntimeData {

    private static final Logger LOGGER = LoggerFactory.getLogger("tyc2");

    private static volatile TyC2Config config = TyC2Config.defaults();
    private static volatile MintRecipeRegistry mintRecipes = MintRecipeRegistry.createDefault();
    private static volatile GoodsCatalog goodsCatalog = GoodsCatalog.createDefault();
    private static volatile VillageLevelTable levelTable = VillageLevelTable.DEFAULT;
    private static volatile WorkplaceCatalog workplaceCatalog = WorkplaceCatalog.createDefault();

    private TyC2RuntimeData() {
    }

    /** Returns the active configuration snapshot. */
    public static TyC2Config config() {
        return config;
    }

    /** Returns the active minting recipe registry. */
    public static MintRecipeRegistry mintRecipes() {
        return mintRecipes;
    }

    /** Returns the active trade goods catalog. */
    public static GoodsCatalog goodsCatalog() {
        return goodsCatalog;
    }

    /** Returns the active Lv 1-10 village level progression table. */
    public static VillageLevelTable levelTable() {
        return levelTable;
    }

    /** Returns the active aided villager workplace catalog. */
    public static WorkplaceCatalog workplaceCatalog() {
        return workplaceCatalog;
    }

    /** Creates a {@link DemandGenerator} bound to the current catalog and config. */
    public static DemandGenerator demandGenerator() {
        return new DemandGenerator(goodsCatalog, config);
    }

    /** Returns the {@code config/tyc2/} directory path. */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("tyc2");
    }

    /** Loads external config files on startup, generating clean starter files if missing. */
    public static void bootstrapExternalConfig() {
        Path dir = configDir();
        try {
            config = TyC2Config.loadOrCreate(dir.resolve("general.json"));
        } catch (Exception e) {
            LOGGER.warn("Failed to load config/tyc2/general.json, using defaults: {}", e.getMessage());
            config = TyC2Config.defaults();
        }
        MintRecipeRegistry freshMint = MintRecipeRegistry.createDefault();
        GoodsCatalog freshGoods = GoodsCatalog.createDefault();
        WorkplaceCatalog freshWorkplaces = WorkplaceCatalog.createDefault();
        loadExternalOverrides(dir, freshMint, freshGoods, freshWorkplaces);
        mintRecipes = freshMint;
        goodsCatalog = freshGoods;
        workplaceCatalog = freshWorkplaces;
    }

    /** Scans live Forge registries for third-party modded villager professions and merges them into the catalog. */
    public static void refreshModdedProfessions() {
        WorkplaceCatalog catalog = workplaceCatalog;
        mergeDiscoveredModdedProfessions(catalog, config);
    }

    /** Reloads external {@code config/tyc2/*.json} overrides on top of current registries. */
    public static void reloadExternalConfigOnly() {
        Path dir = configDir();
        try {
            config = TyC2Config.loadOrCreate(dir.resolve("general.json"));
        } catch (Exception e) {
            LOGGER.warn("Failed to reload config/tyc2/general.json: {}", e.getMessage());
        }
        loadExternalOverrides(dir, mintRecipes, goodsCatalog, workplaceCatalog);
        mergeDiscoveredModdedProfessions(workplaceCatalog, config);
    }

    private static void loadExternalOverrides(
            Path dir,
            MintRecipeRegistry targetMint,
            GoodsCatalog targetGoods,
            WorkplaceCatalog targetWorkplaces
    ) {
        try {
            targetMint.loadExternalConfig(dir.resolve("custom_mint_recipes.json"), true);
        } catch (Exception e) {
            LOGGER.warn("Failed to load config/tyc2/custom_mint_recipes.json: {}", e.getMessage());
        }
        try {
            targetGoods.loadExternalConfig(dir.resolve("custom_goods.json"), true);
        } catch (Exception e) {
            LOGGER.warn("Failed to load config/tyc2/custom_goods.json: {}", e.getMessage());
        }
        try {
            targetWorkplaces.loadExternalConfig(dir.resolve("custom_workplaces.json"), true);
        } catch (Exception e) {
            LOGGER.warn("Failed to load config/tyc2/custom_workplaces.json: {}", e.getMessage());
        }
    }

    private static void mergeDiscoveredModdedProfessions(WorkplaceCatalog catalog, TyC2Config cfg) {
        catalog.clearDynamicModdedProfessions();
        if (!cfg.enableModdedProfessions()) {
            return;
        }
        try {
            int added = 0;
            for (TyC2VillagerHelpers.DiscoveredProfession dp : TyC2VillagerHelpers.discoverModdedProfessions()) {
                if (catalog.registerModdedProfession(
                        dp.professionId(), dp.workstationBlockId(), dp.displayNameFallback(), cfg)) {
                    added++;
                }
            }
            if (added > 0) {
                LOGGER.info("TyC2 discovered {} dynamic modded villager professions.", added);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to scan dynamic modded villager professions: {}", e.getMessage());
        }
    }

    /** Creates the Forge {@link SimplePreparableReloadListener} registered on {@code AddReloadListenerEvent}. */
    public static SimplePreparableReloadListener<PreparedPayload> createReloadListener() {
        return new ReloadListener();
    }

    /** Immutable prepared JSON payload collected asynchronously off the server tick thread. */
    public record PreparedPayload(
            List<Map.Entry<ResourceLocation, String>> mintRecipeFiles,
            List<Map.Entry<ResourceLocation, String>> goodsFiles,
            List<Map.Entry<ResourceLocation, String>> seasonFiles,
            List<Map.Entry<ResourceLocation, String>> levelFiles,
            List<Map.Entry<ResourceLocation, String>> workplaceFiles
    ) {
    }

    private static final class ReloadListener extends SimplePreparableReloadListener<PreparedPayload> {

        @Override
        protected PreparedPayload prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            List<Map.Entry<ResourceLocation, String>> mintFiles = new ArrayList<>();
            collectJson(resourceManager, "mint_recipes", mintFiles);
            collectJson(resourceManager, "tyc2/mint_recipes", mintFiles);

            List<Map.Entry<ResourceLocation, String>> goodsFiles = new ArrayList<>();
            collectJson(resourceManager, "goods", goodsFiles);
            collectJson(resourceManager, "tyc2/goods", goodsFiles);

            List<Map.Entry<ResourceLocation, String>> seasonFiles = new ArrayList<>();
            collectJson(resourceManager, "seasons", seasonFiles);
            collectJson(resourceManager, "tyc2/seasons", seasonFiles);

            List<Map.Entry<ResourceLocation, String>> levelFiles = new ArrayList<>();
            collectJson(resourceManager, "levels", levelFiles);
            collectJson(resourceManager, "tyc2/levels", levelFiles);
            collectSingleJson(resourceManager, "tyc2/levels.json", levelFiles);

            List<Map.Entry<ResourceLocation, String>> workplaceFiles = new ArrayList<>();
            collectJson(resourceManager, "workplaces", workplaceFiles);
            collectJson(resourceManager, "tyc2/workplaces", workplaceFiles);

            return new PreparedPayload(mintFiles, goodsFiles, seasonFiles, levelFiles, workplaceFiles);
        }

        @Override
        protected void apply(PreparedPayload payload, ResourceManager resourceManager, ProfilerFiller profiler) {
            Path dir = configDir();
            try {
                config = TyC2Config.loadOrCreate(dir.resolve("general.json"));
            } catch (Exception e) {
                LOGGER.warn("Failed to load config/tyc2/general.json during reload: {}", e.getMessage());
            }

            MintRecipeRegistry freshMint = MintRecipeRegistry.createDefault();
            for (Map.Entry<ResourceLocation, String> entry : payload.mintRecipeFiles()) {
                try {
                    freshMint.mergeFromJson(entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid TyC2 mint recipe JSON {}: {}", entry.getKey(), e.getMessage());
                }
            }

            GoodsCatalog freshGoods = GoodsCatalog.createDefault();
            for (Map.Entry<ResourceLocation, String> entry : payload.goodsFiles()) {
                try {
                    freshGoods.mergeFromJson(entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid TyC2 goods JSON {}: {}", entry.getKey(), e.getMessage());
                }
            }
            for (Map.Entry<ResourceLocation, String> entry : payload.seasonFiles()) {
                try {
                    freshGoods.mergeSeasonsOverlayJson(entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid TyC2 seasons JSON {}: {}", entry.getKey(), e.getMessage());
                }
            }

            VillageLevelTable freshLevels = VillageLevelTable.DEFAULT;
            for (Map.Entry<ResourceLocation, String> entry : payload.levelFiles()) {
                try {
                    VillageLevelTable parsed = parseLevelTable(entry.getValue());
                    if (parsed != null) {
                        freshLevels = parsed;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid TyC2 levels JSON {}: {}", entry.getKey(), e.getMessage());
                }
            }

            WorkplaceCatalog freshWorkplaces = WorkplaceCatalog.createDefault();
            for (Map.Entry<ResourceLocation, String> entry : payload.workplaceFiles()) {
                try {
                    freshWorkplaces.mergeFromJson(entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid TyC2 workplaces JSON {}: {}", entry.getKey(), e.getMessage());
                }
            }

            // External config overrides take final priority over datapack presets
            loadExternalOverrides(dir, freshMint, freshGoods, freshWorkplaces);
            mergeDiscoveredModdedProfessions(freshWorkplaces, config);

            mintRecipes = freshMint;
            goodsCatalog = freshGoods;
            levelTable = freshLevels;
            workplaceCatalog = freshWorkplaces;
            LOGGER.info(
                    "TyC2 data reloaded: {} mint recipes, {} trade goods, {} village levels, {} workplaces.",
                    freshMint.allRecipes().size(),
                    freshGoods.allGoods().size(),
                    freshLevels.allLevels().size(),
                    freshWorkplaces.allConfiguredWorkplaces().size()
            );
        }

        private static void collectJson(
                ResourceManager manager,
                String folder,
                List<Map.Entry<ResourceLocation, String>> out
        ) {
            Map<ResourceLocation, Resource> found = manager.listResources(
                    folder, rl -> rl.getPath().endsWith(".json")
            );
            List<ResourceLocation> sortedKeys = new ArrayList<>(found.keySet());
            sortedKeys.sort(Comparator.comparing(ResourceLocation::toString));
            for (ResourceLocation rl : sortedKeys) {
                // Only load files under tyc2 namespace for bare folder names (mint_recipes/goods/seasons/levels)
                if (!folder.startsWith("tyc2/") && !"tyc2".equals(rl.getNamespace())) {
                    continue;
                }
                String text = readResource(found.get(rl));
                if (text != null) {
                    out.add(Map.entry(rl, text));
                }
            }
        }

        private static void collectSingleJson(
                ResourceManager manager,
                String pathSuffix,
                List<Map.Entry<ResourceLocation, String>> out
        ) {
            Map<ResourceLocation, Resource> found = manager.listResources(
                    "tyc2", rl -> rl.getPath().equals(pathSuffix)
            );
            for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
                String text = readResource(entry.getValue());
                if (text != null) {
                    out.add(Map.entry(entry.getKey(), text));
                }
            }
        }

        private static String readResource(Resource resource) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } catch (IOException e) {
                LOGGER.warn("Failed to read TyC2 datapack resource: {}", e.getMessage());
                return null;
            }
        }

        private static VillageLevelTable parseLevelTable(String json) {
            Map<String, Object> root = JsonLite.parseObject(json);
            List<Object> rawLevels = JsonLite.asArray(root.get("levels"));
            if (rawLevels == null || rawLevels.isEmpty()) {
                return null;
            }
            Map<Integer, VillageLevelTable.LevelSpec> byLevel = new LinkedHashMap<>();
            for (VillageLevelTable.LevelSpec def : VillageLevelTable.DEFAULT.allLevels()) {
                byLevel.put(def.level(), def);
            }
            for (Object elem : rawLevels) {
                Map<String, Object> m = JsonLite.asObject(elem);
                if (m == null) {
                    continue;
                }
                int lv = JsonLite.getInt(m, "level", -1);
                if (lv < VillageLevelTable.MIN_LEVEL || lv > VillageLevelTable.MAX_LEVEL) {
                    continue;
                }
                VillageLevelTable.LevelSpec base = byLevel.get(lv);
                byLevel.put(lv, new VillageLevelTable.LevelSpec(
                        lv,
                        JsonLite.getInt(m, "minPopulation", base.minPopulation()),
                        JsonLite.getLong(m, "minTradeVolume", base.minTradeVolume()),
                        JsonLite.getLong(m, "minVillageBalance", base.minVillageBalance()),
                        JsonLite.getLong(m, "upgradeCostSink", base.upgradeCostSink()),
                        JsonLite.getInt(m, "sellOrderCount", base.sellOrderCount()),
                        JsonLite.getInt(m, "buyOrderCount", base.buyOrderCount()),
                        JsonLite.getDouble(m, "qtyScale", base.qtyScale()),
                        JsonLite.getInt(m, "maxPopulationCap", base.maxPopulationCap()),
                        JsonLite.getInt(m, "expansionRadiusMax", base.expansionRadiusMax())
                ));
            }
            return VillageLevelTable.of(new ArrayList<>(byLevel.values()));
        }
    }
}
