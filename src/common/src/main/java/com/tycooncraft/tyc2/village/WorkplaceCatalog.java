package com.tycooncraft.tyc2.village;

import com.tycooncraft.tyc2.api.TyC2RegistryApi;
import com.tycooncraft.tyc2.core.GoodsCategory;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.TyC2Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Three-layer extensible catalog of aided villager workplaces combining:
 * <ol>
 *   <li>All 13 built-in vanilla Minecraft villager professions</li>
 *   <li>Datapack JSON ({@code data/&lt;ns&gt;/tyc2/workplaces/*.json}) and external config
 *       ({@code config/tyc2/custom_workplaces.json}) overrides/additions</li>
 *   <li>Programmatic Java API registrations ({@link TyC2RegistryApi#registerWorkplace})</li>
 *   <li>Dynamic third-party modded profession discovery ({@link #registerModdedProfession})</li>
 * </ol>
 */
public final class WorkplaceCatalog {

    /** Default small-building base price (IC) for dynamically discovered modded professions. */
    public static final long DEFAULT_MODDED_BASE_COST_SMALL_IC = 450L;

    /** Default large-workshop cost multiplier relative to {@code baseCostSmallIc}. */
    public static final double DEFAULT_LARGE_COST_MULTIPLIER = 2.2;

    private final Map<String, WorkplaceSpec> configuredSpecs = new LinkedHashMap<>();
    private final Map<String, WorkplaceSpec> dynamicModdedSpecs = new LinkedHashMap<>();

    /**
     * Creates an empty catalog. Use {@link #createDefault()} for the 13 built-in vanilla professions.
     */
    public WorkplaceCatalog() {
    }

    /**
     * Creates a catalog pre-populated with all 13 built-in vanilla Minecraft professions.
     *
     * @return populated default workplace catalog
     */
    public static WorkplaceCatalog createDefault() {
        WorkplaceCatalog catalog = new WorkplaceCatalog();
        catalog.registerBuiltInDefaults();
        return catalog;
    }

    /**
     * Registers all 13 built-in vanilla villager professions with canonical costs and trade categories.
     */
    public void registerBuiltInDefaults() {
        register(vanilla("minecraft:farmer", "minecraft:composter", WorkplaceArchetype.FARMER,
                250L, List.of(GoodsCategory.AGRICULTURE, GoodsCategory.FOOD_BASIC)));
        register(vanilla("minecraft:fisherman", "minecraft:barrel", WorkplaceArchetype.FISHERMAN,
                260L, List.of(GoodsCategory.FOOD_BASIC, GoodsCategory.AGRICULTURE)));
        register(vanilla("minecraft:shepherd", "minecraft:loom", WorkplaceArchetype.SHEPHERD,
                280L, List.of(GoodsCategory.TEXTILE)));
        register(vanilla("minecraft:fletcher", "minecraft:fletching_table", WorkplaceArchetype.FLETCHER,
                280L, List.of(GoodsCategory.FORESTRY, GoodsCategory.MONSTER_DROP)));
        register(vanilla("minecraft:butcher", "minecraft:smoker", WorkplaceArchetype.BUTCHER,
                320L, List.of(GoodsCategory.FOOD_BASIC, GoodsCategory.FOOD_FINE)));
        register(vanilla("minecraft:leatherworker", "minecraft:cauldron", WorkplaceArchetype.LEATHERWORKER,
                320L, List.of(GoodsCategory.TEXTILE)));
        register(vanilla("minecraft:mason", "minecraft:stonecutter", WorkplaceArchetype.MASON,
                360L, List.of(GoodsCategory.BUILDING, GoodsCategory.MINING)));
        register(vanilla("minecraft:cartographer", "minecraft:cartography_table", WorkplaceArchetype.CARTOGRAPHER,
                400L, List.of(GoodsCategory.BUILDING, GoodsCategory.FORESTRY)));
        register(vanilla("minecraft:toolsmith", "minecraft:smithing_table", WorkplaceArchetype.TOOLSMITH,
                450L, List.of(GoodsCategory.METAL, GoodsCategory.MINING, GoodsCategory.INDUSTRIAL)));
        register(vanilla("minecraft:weaponsmith", "minecraft:grindstone", WorkplaceArchetype.WEAPONSMITH,
                480L, List.of(GoodsCategory.METAL, GoodsCategory.RARE_ALLOY, GoodsCategory.MONSTER_DROP)));
        register(vanilla("minecraft:armorer", "minecraft:blast_furnace", WorkplaceArchetype.ARMORER,
                520L, List.of(GoodsCategory.METAL, GoodsCategory.RARE_ALLOY, GoodsCategory.INDUSTRIAL)));
        register(vanilla("minecraft:cleric", "minecraft:brewing_stand", WorkplaceArchetype.CLERIC,
                550L, List.of(GoodsCategory.MONSTER_DROP, GoodsCategory.ENDGAME)));
        register(vanilla("minecraft:librarian", "minecraft:lectern", WorkplaceArchetype.LIBRARIAN,
                600L, List.of(GoodsCategory.ENCHANTED_BOOK)));
    }

    private static WorkplaceSpec vanilla(
            String professionId,
            String workstationBlockId,
            WorkplaceArchetype archetype,
            long baseCostSmallIc,
            List<GoodsCategory> tradeCategories) {
        return new WorkplaceSpec(
                professionId,
                workstationBlockId,
                archetype,
                baseCostSmallIc,
                DEFAULT_LARGE_COST_MULTIPLIER,
                1,
                3,
                tradeCategories,
                true,
                false,
                "");
    }

    /**
     * Explicitly registers or replaces a configured {@link WorkplaceSpec}.
     *
     * @param spec workplace spec to register
     */
    public void register(WorkplaceSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        configuredSpecs.put(spec.professionId(), spec);
    }

    /**
     * Removes a configured workplace entry by profession ID.
     *
     * @param professionId namespaced profession ID
     * @return {@code true} if removed
     */
    public boolean unregister(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return false;
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        boolean removed = configuredSpecs.remove(norm) != null;
        return dynamicModdedSpecs.remove(norm) != null || removed;
    }

    /**
     * Clears all dynamically discovered modded professions (e.g., before re-scanning registries on reload).
     */
    public void clearDynamicModdedProfessions() {
        dynamicModdedSpecs.clear();
    }

    /**
     * Dynamically registers a third-party modded villager profession discovered from game registries
     * using the {@link WorkplaceArchetype#GENERIC} workshop template (450 IC small, 2.2x large, Lv 1/Lv 3).
     *
     * <p>Respects {@link TyC2Config#enableModdedProfessions()} and {@link TyC2Config#moddedProfessionBlacklist()},
     * ignores {@code minecraft:none} / {@code minecraft:nitwit}, and NEVER overwrites explicitly configured
     * entries (built-in, datapack, external config, or {@link TyC2RegistryApi}).
     *
     * @param professionId namespaced modded profession ID
     * @param workstationBlockId default POI workstation block ID
     * @param displayNameFallback localized or human-readable display name fallback
     * @param cfg active configuration (null falls back to defaults)
     * @return {@code true} if newly registered as a dynamic modded workplace
     */
    public boolean registerModdedProfession(
            String professionId,
            String workstationBlockId,
            String displayNameFallback,
            TyC2Config cfg) {
        TyC2Config effectiveCfg = cfg == null ? TyC2Config.defaults() : cfg;
        if (!effectiveCfg.enableModdedProfessions()) {
            return false;
        }
        if (professionId == null || professionId.isBlank()
                || workstationBlockId == null || workstationBlockId.isBlank()) {
            return false;
        }
        String normProf = professionId.trim().toLowerCase(Locale.ROOT);
        String normBlock = workstationBlockId.trim().toLowerCase(Locale.ROOT);
        if (normProf.equals("minecraft:none")
                || normProf.equals("none")
                || normProf.equals("minecraft:nitwit")
                || normProf.equals("nitwit")
                || normBlock.equals("minecraft:air")) {
            return false;
        }
        if (effectiveCfg.isModdedProfessionBlacklisted(normProf)) {
            return false;
        }
        if (configuredSpecs.containsKey(normProf)) {
            return false;
        }
        for (WorkplaceSpec apiSpec : TyC2RegistryApi.getRegisteredWorkplaces()) {
            if (apiSpec.professionId().equals(normProf)) {
                return false;
            }
        }
        String fallback = (displayNameFallback == null || displayNameFallback.isBlank())
                ? deriveFallbackName(normProf)
                : displayNameFallback.trim();
        WorkplaceSpec spec = new WorkplaceSpec(
                normProf,
                normBlock,
                WorkplaceArchetype.GENERIC,
                DEFAULT_MODDED_BASE_COST_SMALL_IC,
                DEFAULT_LARGE_COST_MULTIPLIER,
                1,
                3,
                List.of(),
                true,
                true,
                fallback);
        dynamicModdedSpecs.put(normProf, spec);
        return true;
    }

    /**
     * Merges workplace specs from datapack or external config JSON.
     * Supports {@code { "replace": bool, "workplaces": [...] }}, a top-level array {@code [...]},
     * or a single workplace JSON object.
     *
     * @param json JSON text
     * @return number of entries processed
     */
    public int mergeFromJson(String json) {
        Object parsed = JsonLite.parse(json);
        if (parsed instanceof List<?>) {
            return applyWorkplacesList(JsonLite.asArray(parsed));
        }
        Map<String, Object> obj = JsonLite.asObject(parsed);
        if (obj == null) {
            throw new IllegalArgumentException("Workplaces JSON must be an object or array");
        }
        if (JsonLite.getBoolean(obj, "replace", false)) {
            configuredSpecs.clear();
        }
        if (obj.containsKey("workplaces")) {
            List<Object> list = JsonLite.asArray(obj.get("workplaces"));
            return list == null ? 0 : applyWorkplacesList(list);
        }
        return applySingleEntry(obj) ? 1 : 0;
    }

    /**
     * Loads and merges {@code config/tyc2/custom_workplaces.json}, optionally creating a starter template if absent.
     *
     * @param path file path
     * @param writeTemplateIfMissing whether to write a starter template if absent
     * @return number of entries processed
     * @throws IOException if file I/O fails
     */
    public int loadExternalConfig(Path path, boolean writeTemplateIfMissing) throws IOException {
        if (!Files.exists(path)) {
            if (writeTemplateIfMissing) {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, defaultCustomConfigTemplate() + "\n", StandardCharsets.UTF_8);
            }
            return 0;
        }
        return mergeFromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /**
     * Returns a clean starter JSON template for {@code config/tyc2/custom_workplaces.json}.
     *
     * @return pretty-printed JSON template
     */
    public static String defaultCustomConfigTemplate() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_comment",
                "Add or override aided villager workplaces below. Set 'enabled': false to disable an existing profession.");
        root.put("replace", false);
        root.put("workplaces", Collections.emptyList());
        return JsonLite.toPrettyJson(root);
    }

    /**
     * Serializes all explicitly configured workplaces to pretty-printed JSON.
     *
     * @return JSON string
     */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>(configuredSpecs.size());
        for (WorkplaceSpec spec : configuredSpecs.values()) {
            list.add(spec.toDataMap());
        }
        root.put("workplaces", list);
        return JsonLite.toPrettyJson(root);
    }

    /**
     * Looks up a workplace spec by profession ID across configured entries, {@link TyC2RegistryApi},
     * and dynamically discovered modded professions.
     *
     * @param professionId namespaced profession ID
     * @return optional workplace spec
     */
    public Optional<WorkplaceSpec> getByProfessionId(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return Optional.empty();
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        for (WorkplaceSpec apiSpec : TyC2RegistryApi.getRegisteredWorkplaces()) {
            if (apiSpec.professionId().equals(norm)) {
                return Optional.of(apiSpec);
            }
        }
        WorkplaceSpec configured = configuredSpecs.get(norm);
        if (configured != null) {
            return Optional.of(configured);
        }
        return Optional.ofNullable(dynamicModdedSpecs.get(norm));
    }

    /**
     * Returns the associated {@link GoodsCategory} list for {@code professionId} (empty if unknown).
     *
     * @param professionId namespaced profession ID
     * @return unmodifiable list of trade categories
     */
    public List<GoodsCategory> tradeCategoriesFor(String professionId) {
        return getByProfessionId(professionId)
                .map(WorkplaceSpec::tradeCategories)
                .orElse(List.of());
    }

    /**
     * Returns all enabled workplaces whose workstation block exists in the live game registry,
     * merging built-in/datapack/config specs, {@link TyC2RegistryApi#getRegisteredWorkplaces()},
     * and dynamically discovered modded professions.
     *
     * @param blockExists predicate checking whether a workstation block ID exists in the block registry
     * @return unmodifiable ordered list of available workplaces
     */
    public List<WorkplaceSpec> getAvailableWorkplaces(Predicate<String> blockExists) {
        Objects.requireNonNull(blockExists, "blockExists predicate must not be null");
        Map<String, WorkplaceSpec> merged = new LinkedHashMap<>(configuredSpecs);
        for (WorkplaceSpec apiSpec : TyC2RegistryApi.getRegisteredWorkplaces()) {
            merged.put(apiSpec.professionId(), apiSpec);
        }
        for (Map.Entry<String, WorkplaceSpec> dyn : dynamicModdedSpecs.entrySet()) {
            merged.putIfAbsent(dyn.getKey(), dyn.getValue());
        }
        List<WorkplaceSpec> available = new ArrayList<>();
        for (WorkplaceSpec spec : merged.values()) {
            if (spec.enabled() && blockExists.test(spec.workstationBlockId())) {
                available.add(spec);
            }
        }
        return Collections.unmodifiableList(available);
    }

    /**
     * Returns all explicitly configured specs (unfiltered).
     *
     * @return unmodifiable list of configured workplaces
     */
    public List<WorkplaceSpec> allConfiguredWorkplaces() {
        return Collections.unmodifiableList(new ArrayList<>(configuredSpecs.values()));
    }

    /**
     * Quotes the CPI-adjusted aid construction price in IC for {@code spec} and {@code size}:
     * {@code max(1, round(baseCostSmallIc * (LARGE ? largeCostMultiplier : 1.0) * clamp(cpi, minMult, maxMult)))}.
     *
     * @param spec workplace spec
     * @param size requested building size
     * @param cpi current global macro-economy CPI index
     * @param cfg active configuration (null falls back to defaults)
     * @return quoted cost in IC (>= 1)
     */
    public static long quoteCostIc(WorkplaceSpec spec, WorkplaceSize size, double cpi, TyC2Config cfg) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(size, "size");
        TyC2Config effectiveCfg = cfg == null ? TyC2Config.defaults() : cfg;
        double sizeMult = size == WorkplaceSize.LARGE ? spec.largeCostMultiplier() : 1.0;
        double safeCpi = Double.isFinite(cpi) ? cpi : 1.0;
        double clampedCpi = Math.max(
                effectiveCfg.minPriceMultiplier(),
                Math.min(effectiveCfg.maxPriceMultiplier(), safeCpi));
        return Math.max(1L, Math.round(spec.baseCostSmallIc() * sizeMult * clampedCpi));
    }

    /**
     * Builds the biome- and profession-styled bill of materials displayed during villager background
     * material gathering, always including the exact profession workstation(s) (1 for SMALL, 3 for LARGE).
     *
     * @param spec workplace specification
     * @param size building size
     * @param style village biome architectural style
     * @return unmodifiable insertion-ordered map of {@code itemId -> requiredCount}
     */
    public static Map<String, Integer> requiredMaterials(
            WorkplaceSpec spec, WorkplaceSize size, BiomeStyle style) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(style, "style");
        BiomePalette p = style.palette();
        boolean large = size == WorkplaceSize.LARGE;

        Map<String, Integer> mats = new LinkedHashMap<>();
        addMat(mats, spec.workstationBlockId(), size.workstationCount());
        addMat(mats, p.log(), large ? 24 : 14);
        addMat(mats, p.planks(), large ? 64 : 36);
        addMat(mats, large ? p.accentStone() : p.baseStone(), large ? 40 : 22);
        addMat(mats, p.glassPane(), large ? 12 : 6);
        addMat(mats, p.lampLight(), large ? 4 : 2);

        switch (spec.archetype()) {
            case FARMER -> addMat(mats, "minecraft:hay_block", large ? 6 : 3);
            case FISHERMAN -> addMat(mats, p.slab(), large ? 10 : 5);
            case SHEPHERD -> addMat(mats, "minecraft:white_wool", large ? 12 : 6);
            case FLETCHER -> addMat(mats, "minecraft:target", large ? 2 : 1);
            case BUTCHER -> addMat(mats, "minecraft:bricks", large ? 16 : 8);
            case LEATHERWORKER -> addMat(mats, p.fence(), large ? 8 : 4);
            case MASON -> addMat(mats, "minecraft:polished_andesite", large ? 16 : 8);
            case CARTOGRAPHER -> addMat(mats, "minecraft:bookshelf", large ? 6 : 3);
            case TOOLSMITH, WEAPONSMITH, ARMORER -> {
                addMat(mats, "minecraft:iron_bars", large ? 8 : 4);
                addMat(mats, "minecraft:anvil", 1);
            }
            case CLERIC -> addMat(mats, "minecraft:purple_stained_glass_pane", large ? 8 : 4);
            case LIBRARIAN -> addMat(mats, "minecraft:bookshelf", large ? 14 : 6);
            case GENERIC -> addMat(mats, "minecraft:chest", large ? 2 : 1);
        }
        return Collections.unmodifiableMap(mats);
    }

    private int applyWorkplacesList(List<Object> list) {
        int count = 0;
        for (Object item : list) {
            Map<String, Object> map = JsonLite.asObject(item);
            if (map != null && applySingleEntry(map)) {
                count++;
            }
        }
        return count;
    }

    private boolean applySingleEntry(Map<String, Object> map) {
        if (JsonLite.getBoolean(map, "remove", false)) {
            String profId = JsonLite.getString(map, "professionId", null);
            return profId != null && unregister(profId);
        }
        // Allow partial disable entries like {"professionId": "minecraft:cleric", "enabled": false}
        if (map.containsKey("professionId") && !map.containsKey("workstationBlockId")
                && map.containsKey("enabled")) {
            String profId = JsonLite.getString(map, "professionId", "").trim().toLowerCase(Locale.ROOT);
            WorkplaceSpec existing = configuredSpecs.get(profId);
            if (existing != null) {
                register(existing.withEnabled(JsonLite.getBoolean(map, "enabled", true)));
                return true;
            }
            return false;
        }
        register(WorkplaceSpec.fromDataMap(map));
        return true;
    }

    private static void addMat(Map<String, Integer> mats, String itemId, int qty) {
        if (qty > 0) {
            mats.merge(itemId, qty, Integer::sum);
        }
    }

    private static String deriveFallbackName(String professionId) {
        int colon = professionId.indexOf(':');
        String path = colon >= 0 ? professionId.substring(colon + 1) : professionId;
        if (path.isEmpty()) {
            return professionId;
        }
        String[] parts = path.split("[_-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
