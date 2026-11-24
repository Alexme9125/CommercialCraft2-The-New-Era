package com.tycooncraft.tyc2.village;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Village construction project categories supported by the procedural architecture
 * catalog and collision-safe expansion planner, including player-funded aided workplaces.
 */
public enum ProjectType {
    /** 3-wide paved road segment with curbs and biome-styled streetlamps. */
    ROAD_AND_LAMPS(1, 0, 0, false, false, false, 80L),
    /** Fenced 7x7 irrigated farmland plot with center water, composter, and corner lanterns. */
    CROP_FARM(1, 0, 0, false, false, false, 120L),
    /** Fenced 9x7 livestock pen with gate, hay bales, water trough, and sheltered roof. */
    ANIMAL_RANCH(2, 0, 4, false, false, false, 180L),
    /** Cozy 7x7x6 starter cottage with 1 bed (+1 villager on completion). */
    SMALL_COTTAGE(1, 1, 0, false, false, false, 220L),
    /** Two-room 9x9x8 residence with chimney, balcony overhang, and 2 beds (+2 villagers). */
    MEDIUM_RESIDENCE(3, 2, 0, false, false, false, 480L),
    /** Multi-story 11x9x13 townhouse with stone arcade, interior stairs, balcony, dormer, and 3 beds (+3 villagers). */
    MULTI_STORY_TOWNHOUSE(5, 3, 0, false, false, false, 1_100L),
    /** 9x7 waterfront pier with open 5x5 deck and mooring posts sized for {@code lc2:smart_port}. */
    LC2_WATERFRONT_PIER(3, 0, 0, true, true, true, 360L),
    /** 9x9 open-sky stone plaza with corner beacons sized for {@code lc2:cargo_station} and drones. */
    LC2_HELIPAD_PLAZA(4, 0, 0, true, false, true, 450L),
    /** Player-funded 7x6x7 small aided workplace with 1 profession workstation and 0 beds. */
    AID_WORKPLACE_SMALL(1, 0, 0, false, false, false, 0L),
    /** Player-funded 9x7x9 large aided workshop with 3 profession workstations and 0 beds. */
    AID_WORKPLACE_LARGE(3, 0, 0, false, false, false, 0L);

    private final int minVillageLevel;
    private final int villagerSpawnCount;
    private final int animalSpawnCount;
    private final boolean lc2LogisticsPad;
    private final boolean requiresWaterEdge;
    private final boolean requiresOpenSky;
    private final long defaultBudgetCost;

    ProjectType(
            int minVillageLevel,
            int villagerSpawnCount,
            int animalSpawnCount,
            boolean lc2LogisticsPad,
            boolean requiresWaterEdge,
            boolean requiresOpenSky,
            long defaultBudgetCost) {
        this.minVillageLevel = minVillageLevel;
        this.villagerSpawnCount = villagerSpawnCount;
        this.animalSpawnCount = animalSpawnCount;
        this.lc2LogisticsPad = lc2LogisticsPad;
        this.requiresWaterEdge = requiresWaterEdge;
        this.requiresOpenSky = requiresOpenSky;
        this.defaultBudgetCost = defaultBudgetCost;
    }

    /**
     * Returns the minimum village level required to propose this project.
     *
     * @return minimum village level ({@code 1..10})
     */
    public int minVillageLevel() {
        return minVillageLevel;
    }

    /**
     * Returns how many villagers spawn when this project finishes construction.
     *
     * @return villager spawn count ({@code 0..3})
     */
    public int villagerSpawnCount() {
        return villagerSpawnCount;
    }

    /**
     * Returns how many livestock animals spawn when this project finishes construction.
     *
     * @return animal spawn count
     */
    public int animalSpawnCount() {
        return animalSpawnCount;
    }

    /**
     * Returns whether this project adds residential housing (spawns villagers).
     *
     * @return {@code true} if residential
     */
    public boolean isResidential() {
        return villagerSpawnCount > 0;
    }

    /**
     * Returns whether this project reserves a dedicated LC2 logistics pad coordinate.
     *
     * @return {@code true} for {@link #LC2_WATERFRONT_PIER} and {@link #LC2_HELIPAD_PLAZA}
     */
    public boolean isLc2LogisticsPad() {
        return lc2LogisticsPad;
    }

    /**
     * Returns whether this project requires an adjacent water edge.
     *
     * @return {@code true} for {@link #LC2_WATERFRONT_PIER}
     */
    public boolean requiresWaterEdge() {
        return requiresWaterEdge;
    }

    /**
     * Returns whether this project requires unobstructed open sky above its deck/pad.
     *
     * @return {@code true} when open sky is required
     */
    public boolean requiresOpenSky() {
        return requiresOpenSky;
    }

    /**
     * Returns whether this project is a player-funded aided workplace rather than automatic expansion.
     *
     * @return {@code true} for {@link #AID_WORKPLACE_SMALL} and {@link #AID_WORKPLACE_LARGE}
     */
    public boolean isAidWorkplace() {
        return this == AID_WORKPLACE_SMALL || this == AID_WORKPLACE_LARGE;
    }

    /**
     * Returns the default municipal budget sink cost (IC) charged to {@code villageBalance}.
     *
     * @return budget cost in IC
     */
    public long defaultBudgetCost() {
        return defaultBudgetCost;
    }

    /**
     * Generates the default required material bill of quantities tailored to {@code biomeStyle}.
     *
     * @param biomeStyle village architectural style
     * @return unmodifiable map of {@code itemId -> requiredCount}
     */
    public Map<String, Integer> defaultRequiredMaterials(BiomeStyle biomeStyle) {
        Objects.requireNonNull(biomeStyle, "biomeStyle");
        BiomePalette p = biomeStyle.palette();
        Map<String, Integer> mats = new LinkedHashMap<>();
        switch (this) {
            case ROAD_AND_LAMPS -> {
                addMat(mats, p.baseStone(), 24);
                addMat(mats, p.planks(), 12);
                addMat(mats, p.lampLight(), 2);
            }
            case CROP_FARM -> {
                addMat(mats, "minecraft:dirt", 24);
                addMat(mats, p.log(), 16);
                addMat(mats, p.fence(), 24);
                addMat(mats, p.lampLight(), 4);
            }
            case ANIMAL_RANCH -> {
                addMat(mats, p.log(), 16);
                addMat(mats, p.planks(), 24);
                addMat(mats, p.fence(), 28);
                addMat(mats, "minecraft:hay_block", 4);
            }
            case SMALL_COTTAGE -> {
                addMat(mats, p.log(), 16);
                addMat(mats, p.planks(), 48);
                addMat(mats, p.baseStone(), 32);
                addMat(mats, p.glassPane(), 8);
                addMat(mats, "minecraft:white_wool", 3);
            }
            case MEDIUM_RESIDENCE -> {
                addMat(mats, p.accentStone(), 48);
                addMat(mats, p.log(), 24);
                addMat(mats, p.planks(), 64);
                addMat(mats, p.glassPane(), 16);
                addMat(mats, p.lampLight(), 4);
            }
            case MULTI_STORY_TOWNHOUSE -> {
                addMat(mats, p.accentStone(), 80);
                addMat(mats, p.log(), 40);
                addMat(mats, p.planks(), 96);
                addMat(mats, p.glassPane(), 24);
                addMat(mats, p.lampLight(), 8);
            }
            case LC2_WATERFRONT_PIER -> {
                addMat(mats, p.waterPierLog(), 28);
                addMat(mats, p.planks(), 48);
                addMat(mats, p.baseStone(), 24);
                addMat(mats, p.lampLight(), 4);
            }
            case LC2_HELIPAD_PLAZA -> {
                addMat(mats, p.accentStone(), 48);
                addMat(mats, p.helipadBlock(), 48);
                addMat(mats, "minecraft:iron_ingot", 8);
                addMat(mats, p.lampLight(), 4);
            }
            case AID_WORKPLACE_SMALL -> {
                addMat(mats, p.log(), 16);
                addMat(mats, p.planks(), 40);
                addMat(mats, p.baseStone(), 24);
                addMat(mats, p.glassPane(), 6);
                addMat(mats, p.lampLight(), 2);
            }
            case AID_WORKPLACE_LARGE -> {
                addMat(mats, p.log(), 28);
                addMat(mats, p.planks(), 72);
                addMat(mats, p.accentStone(), 40);
                addMat(mats, p.glassPane(), 12);
                addMat(mats, p.lampLight(), 4);
            }
        }
        return Collections.unmodifiableMap(mats);
    }

    private static void addMat(Map<String, Integer> map, String itemId, int qty) {
        map.merge(itemId, qty, Integer::sum);
    }
}
