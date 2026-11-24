package com.tycooncraft.tyc2.village;

import com.tycooncraft.tyc2.core.GoodsCategory;
import com.tycooncraft.tyc2.core.JsonLite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable catalog definition of an aided villager workplace (vanilla, datapack/config override,
 * API-registered, or dynamically discovered modded profession).
 *
 * @param professionId namespaced villager profession ID (e.g. {@code "minecraft:farmer"})
 * @param workstationBlockId namespaced workstation block ID (e.g. {@code "minecraft:composter"})
 * @param archetype architectural blueprint archetype (one of the 13 vanilla styles or {@link WorkplaceArchetype#GENERIC})
 * @param baseCostSmallIc base aid construction price in IC for a {@link WorkplaceSize#SMALL} building (>= 1)
 * @param largeCostMultiplier cost multiplier for a {@link WorkplaceSize#LARGE} workshop (default {@code 2.2})
 * @param minVillageLevelSmall minimum village level required for SMALL ({@code 1..10})
 * @param minVillageLevelLarge minimum village level required for LARGE ({@code minVillageLevelSmall..10})
 * @param tradeCategories trade goods categories gently weighted when villagers hold this profession
 * @param enabled whether this profession is available for player-funded aid construction
 * @param moddedDynamic {@code true} if automatically discovered from third-party mod registries
 * @param displayNameFallback fallback display name used for dynamically discovered modded professions
 */
public record WorkplaceSpec(
        String professionId,
        String workstationBlockId,
        WorkplaceArchetype archetype,
        long baseCostSmallIc,
        double largeCostMultiplier,
        int minVillageLevelSmall,
        int minVillageLevelLarge,
        List<GoodsCategory> tradeCategories,
        boolean enabled,
        boolean moddedDynamic,
        String displayNameFallback) {

    /**
     * Validates and normalizes all fields.
     */
    public WorkplaceSpec {
        if (professionId == null || professionId.isBlank()) {
            throw new IllegalArgumentException("professionId must not be null or blank");
        }
        if (workstationBlockId == null || workstationBlockId.isBlank()) {
            throw new IllegalArgumentException("workstationBlockId must not be null or blank");
        }
        professionId = professionId.trim().toLowerCase(Locale.ROOT);
        workstationBlockId = workstationBlockId.trim().toLowerCase(Locale.ROOT);
        archetype = archetype == null ? WorkplaceArchetype.GENERIC : archetype;
        if (baseCostSmallIc < 1L) {
            throw new IllegalArgumentException("baseCostSmallIc must be >= 1, got " + baseCostSmallIc);
        }
        if (!Double.isFinite(largeCostMultiplier) || largeCostMultiplier <= 0.0) {
            throw new IllegalArgumentException(
                    "largeCostMultiplier must be finite and > 0, got " + largeCostMultiplier);
        }
        minVillageLevelSmall = Math.max(
                VillageLevelTable.MIN_LEVEL, Math.min(VillageLevelTable.MAX_LEVEL, minVillageLevelSmall));
        minVillageLevelLarge = Math.max(
                minVillageLevelSmall, Math.min(VillageLevelTable.MAX_LEVEL, minVillageLevelLarge));
        tradeCategories = tradeCategories == null ? List.of() : List.copyOf(tradeCategories);
        displayNameFallback = displayNameFallback == null ? "" : displayNameFallback.trim();
    }

    /**
     * Returns the minimum village level required to commission a workplace of the given {@code size}.
     *
     * @param size workplace size
     * @return minimum village level ({@code 1..10})
     */
    public int minVillageLevel(WorkplaceSize size) {
        Objects.requireNonNull(size, "size");
        return size == WorkplaceSize.LARGE ? minVillageLevelLarge : minVillageLevelSmall;
    }

    /**
     * Returns whether a village at {@code villageLevel} can commission this workplace at {@code size}.
     *
     * @param villageLevel current village level
     * @param size requested size
     * @return {@code true} if unlocked
     */
    public boolean isUnlockedAtLevel(int villageLevel, WorkplaceSize size) {
        return villageLevel >= minVillageLevel(size);
    }

    /**
     * Returns the unadjusted (CPI = 1.0) base price in IC for {@code size}.
     *
     * @param size workplace size
     * @return base cost in IC (>= 1)
     */
    public long baseCostIc(WorkplaceSize size) {
        Objects.requireNonNull(size, "size");
        double mult = size == WorkplaceSize.LARGE ? largeCostMultiplier : 1.0;
        return Math.max(1L, Math.round(baseCostSmallIc * mult));
    }

    /**
     * Returns a copy of this spec with {@link #enabled()} set to {@code value}.
     *
     * @param value enabled flag
     * @return updated spec
     */
    public WorkplaceSpec withEnabled(boolean value) {
        if (this.enabled == value) {
            return this;
        }
        return new WorkplaceSpec(
                professionId,
                workstationBlockId,
                archetype,
                baseCostSmallIc,
                largeCostMultiplier,
                minVillageLevelSmall,
                minVillageLevelLarge,
                tradeCategories,
                value,
                moddedDynamic,
                displayNameFallback);
    }

    /**
     * Serializes this spec into a JSON-compatible map matching the datapack/config schema.
     *
     * @return ordered map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("professionId", professionId);
        map.put("workstationBlockId", workstationBlockId);
        map.put("archetype", archetype.name());
        map.put("baseCostSmallIc", baseCostSmallIc);
        map.put("largeCostMultiplier", largeCostMultiplier);
        map.put("minVillageLevelSmall", minVillageLevelSmall);
        map.put("minVillageLevelLarge", minVillageLevelLarge);
        List<String> cats = new ArrayList<>(tradeCategories.size());
        for (GoodsCategory cat : tradeCategories) {
            cats.add(cat.name());
        }
        map.put("tradeCategories", cats);
        map.put("enabled", enabled);
        if (!displayNameFallback.isEmpty()) {
            map.put("displayNameFallback", displayNameFallback);
        }
        return map;
    }

    /**
     * Parses a {@link WorkplaceSpec} from a JSON map matching the datapack/config schema.
     *
     * @param map JSON object map
     * @return parsed {@link WorkplaceSpec}
     */
    public static WorkplaceSpec fromDataMap(Map<String, Object> map) {
        Objects.requireNonNull(map, "WorkplaceSpec map must not be null");
        String professionId = JsonLite.requireString(map, "professionId");
        String workstationBlockId = JsonLite.requireString(map, "workstationBlockId");
        WorkplaceArchetype archetype = WorkplaceArchetype.fromNameOrDefault(
                JsonLite.getString(map, "archetype", WorkplaceArchetype.GENERIC.name()),
                WorkplaceArchetype.GENERIC);
        long baseCostSmall = Math.max(1L, JsonLite.getLong(
                map, "baseCostSmallIc", WorkplaceCatalog.DEFAULT_MODDED_BASE_COST_SMALL_IC));
        double largeMult = JsonLite.getDouble(
                map, "largeCostMultiplier", WorkplaceCatalog.DEFAULT_LARGE_COST_MULTIPLIER);
        if (!Double.isFinite(largeMult) || largeMult <= 0.0) {
            largeMult = WorkplaceCatalog.DEFAULT_LARGE_COST_MULTIPLIER;
        }
        int minSmall = JsonLite.getInt(map, "minVillageLevelSmall", 1);
        int minLarge = JsonLite.getInt(map, "minVillageLevelLarge", 3);

        List<GoodsCategory> categories = new ArrayList<>();
        for (String catName : JsonLite.getStringList(map, "tradeCategories")) {
            if (catName == null || catName.isBlank()) {
                continue;
            }
            try {
                categories.add(GoodsCategory.valueOf(catName.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Skip unrecognized categories cleanly for forward compatibility
            }
        }

        boolean enabled = JsonLite.getBoolean(map, "enabled", true);
        if (JsonLite.getBoolean(map, "disabled", false)) {
            enabled = false;
        }
        boolean moddedDynamic = JsonLite.getBoolean(map, "moddedDynamic", false);
        String displayFallback = JsonLite.getString(map, "displayNameFallback", "");

        return new WorkplaceSpec(
                professionId,
                workstationBlockId,
                archetype,
                baseCostSmall,
                largeMult,
                minSmall,
                minLarge,
                categories,
                enabled,
                moddedDynamic,
                displayFallback);
    }
}
