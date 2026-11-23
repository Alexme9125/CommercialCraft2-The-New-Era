package com.tycooncraft.tyc2.village;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Procedural 3D architectural blueprint catalog covering all 5 {@link BiomeStyle}s and all 8
 * {@link ProjectType}s (40 cached canonical blueprints with on-demand 4-way {@link Facing2D} rotation).
 */
public final class VillageArchitectureCatalog {

    private static final Map<BiomeStyle, Map<ProjectType, StructureBlueprint>> CACHE = buildCache();

    private VillageArchitectureCatalog() {
    }

    /**
     * Returns the canonical {@link Facing2D#NORTH}-facing {@link StructureBlueprint} for the given
     * biome style and project type.
     *
     * @param biomeStyle village architectural style
     * @param projectType structure category
     * @return immutable bottom-up sorted blueprint
     */
    public static StructureBlueprint getBlueprint(BiomeStyle biomeStyle, ProjectType projectType) {
        Objects.requireNonNull(biomeStyle, "biomeStyle");
        Objects.requireNonNull(projectType, "projectType");
        return CACHE.get(biomeStyle).get(projectType);
    }

    /**
     * Returns the {@link StructureBlueprint} for the given biome style and project type rotated
     * to {@code facing}.
     *
     * @param biomeStyle village architectural style
     * @param projectType structure category
     * @param facing desired placement orientation
     * @return immutable rotated blueprint
     */
    public static StructureBlueprint getBlueprint(
            BiomeStyle biomeStyle, ProjectType projectType, Facing2D facing) {
        Objects.requireNonNull(facing, "facing");
        return getBlueprint(biomeStyle, projectType).rotated(facing);
    }

    private static Map<BiomeStyle, Map<ProjectType, StructureBlueprint>> buildCache() {
        Map<BiomeStyle, Map<ProjectType, StructureBlueprint>> outer = new EnumMap<>(BiomeStyle.class);
        for (BiomeStyle style : BiomeStyle.values()) {
            Map<ProjectType, StructureBlueprint> inner = new EnumMap<>(ProjectType.class);
            for (ProjectType type : ProjectType.values()) {
                inner.put(type, generate(style, type));
            }
            outer.put(style, inner);
        }
        return outer;
    }

    private static StructureBlueprint generate(BiomeStyle style, ProjectType type) {
        BiomePalette p = style.palette();
        return switch (type) {
            case ROAD_AND_LAMPS -> buildRoadAndLamps(style, p);
            case CROP_FARM -> buildCropFarm(style, p);
            case ANIMAL_RANCH -> buildAnimalRanch(style, p);
            case SMALL_COTTAGE -> buildSmallCottage(style, p);
            case MEDIUM_RESIDENCE -> buildMediumResidence(style, p);
            case MULTI_STORY_TOWNHOUSE -> buildMultiStoryTownhouse(style, p);
            case LC2_WATERFRONT_PIER -> buildLc2WaterfrontPier(style, p);
            case LC2_HELIPAD_PLAZA -> buildLc2HelipadPlaza(style, p);
        };
    }

    /**
     * 5x5x7 paved road segment: 3-wide road surface with stone/log curbs and alternating
     * biome-styled streetlamps spaced 6 blocks apart.
     */
    private static StructureBlueprint buildRoadAndLamps(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(ProjectType.ROAD_AND_LAMPS, style, 5, 5, 7);
        for (int z = 0; z < 7; z++) {
            b.set(0, 0, z, p.roadBorder());
            b.set(1, 0, z, p.roadPrimary());
            b.set(2, 0, z, p.roadPrimary());
            b.set(3, 0, z, p.roadPrimary());
            b.set(4, 0, z, p.roadBorder());
        }
        // Lamp post 1 at (0, *, 0)
        b.set(0, 1, 0, p.baseStone());
        b.set(0, 2, 0, p.lampPost());
        b.set(0, 3, 0, p.lampPost());
        b.set(0, 4, 0, p.lampLight() + "[hanging=false]");
        // Lamp post 2 at (4, *, 6), 6 blocks down the road on opposite curb
        b.set(4, 1, 6, p.baseStone());
        b.set(4, 2, 6, p.lampPost());
        b.set(4, 3, 6, p.lampPost());
        b.set(4, 4, 6, p.lampLight() + "[hanging=false]");

        return b.roadAttach(2, 0).build();
    }

    /**
     * 7x4x7 fenced irrigated farmland plot with center water source, composter, corner lanterns,
     * front fence gate, and biome-matched crops.
     */
    private static StructureBlueprint buildCropFarm(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(ProjectType.CROP_FARM, style, 7, 4, 7);
        String logY = BiomePalette.withAxisIfSupported(p.log(), "y");

        // Ground layer: framed perimeter + hydrated farmland + central water
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean border = (x == 0 || x == 6 || z == 0 || z == 6);
                if (border) {
                    b.set(x, 0, z, logY);
                } else {
                    b.set(x, 0, z, p.farmland() + "[moisture=7]");
                }
            }
        }
        b.set(3, 0, 3, "minecraft:water");
        b.set(3, 0, 0, p.baseStone()); // stable doorstep under gate

        // Layer 1: perimeter fences, front gate, composter, and planted crops
        for (int x = 0; x < 7; x++) {
            b.set(x, 1, 0, p.fence());
            b.set(x, 1, 6, p.fence());
        }
        for (int z = 1; z < 6; z++) {
            b.set(0, 1, z, p.fence());
            b.set(6, 1, z, p.fence());
        }
        b.set(3, 1, 0, p.fenceGate() + "[facing=north,open=false]");
        b.set(5, 1, 0, "minecraft:composter");

        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 3 && z == 3) {
                    b.set(x, 1, z, p.slab() + "[type=bottom]");
                } else {
                    b.set(x, 1, z, p.cropBlock() + "[age=2]");
                }
            }
        }

        // Corner lantern posts
        int[][] corners = {{0, 0}, {6, 0}, {0, 6}, {6, 6}};
        for (int[] c : corners) {
            b.set(c[0], 2, c[1], p.lampPost());
            b.set(c[0], 3, c[1], p.lampLight() + "[hanging=false]");
        }

        return b.roadAttach(3, 0).build();
    }

    /**
     * 9x5x7 fenced livestock ranch pen with double entrance gate, stone-rimmed water trough,
     * stacked hay bales, covered rear shelter roof, lantern posts, and 4 animal spawn markers.
     */
    private static StructureBlueprint buildAnimalRanch(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(ProjectType.ANIMAL_RANCH, style, 9, 5, 7);

        // Ground layer: grass pasture in front, plank bedding under rear shelter, stone perimeter curb
        b.fill(0, 0, 0, 8, 0, 6, "minecraft:grass_block");
        b.fill(1, 0, 4, 7, 0, 5, p.floorBlock());
        for (int x = 0; x < 9; x++) {
            b.set(x, 0, 0, p.baseStone());
            b.set(x, 0, 6, p.baseStone());
        }
        for (int z = 0; z < 7; z++) {
            b.set(0, 0, z, p.baseStone());
            b.set(8, 0, z, p.baseStone());
        }
        // Enclosed water trough on west side
        b.set(1, 0, 2, "minecraft:water");
        b.set(1, 0, 3, "minecraft:water");
        b.set(2, 0, 2, p.baseStone());
        b.set(2, 0, 3, p.baseStone());

        // Perimeter fence and double gate
        for (int x = 0; x < 9; x++) {
            b.set(x, 1, 0, p.fence());
            b.set(x, 1, 6, p.fence());
        }
        for (int z = 1; z < 6; z++) {
            b.set(0, 1, z, p.fence());
            b.set(8, 1, z, p.fence());
        }
        b.set(4, 1, 0, p.fenceGate() + "[facing=north,open=false]");
        b.set(5, 1, 0, p.fenceGate() + "[facing=north,open=false]");

        // Front corner lanterns
        b.set(0, 2, 0, p.lampPost());
        b.set(0, 3, 0, p.lampLight() + "[hanging=false]");
        b.set(8, 2, 0, p.lampPost());
        b.set(8, 3, 0, p.lampLight() + "[hanging=false]");

        // Rear covered shelter structure (dx=1..7, dz=4..6)
        b.pillar(1, 1, 3, 4, p.log());
        b.pillar(7, 1, 3, 4, p.log());
        b.pillar(1, 1, 3, 6, p.log());
        b.pillar(7, 1, 3, 6, p.log());
        b.fill(2, 1, 6, 6, 3, 6, p.planks());
        b.fill(1, 1, 5, 1, 2, 5, p.planks());
        b.fill(7, 1, 5, 7, 2, 5, p.planks());

        // Hay bale feed stack
        b.set(5, 1, 5, "minecraft:hay_block[axis=y]");
        b.set(6, 1, 5, "minecraft:hay_block[axis=y]");
        b.set(6, 2, 5, "minecraft:hay_block[axis=y]");

        // Sloped shelter roof
        for (int x = 1; x <= 7; x++) {
            b.set(x, 4, 4, p.roofStairs() + "[facing=south,half=bottom]");
            b.set(x, 4, 5, p.roofSlab() + "[type=bottom]");
            b.set(x, 4, 6, p.roofSlab() + "[type=bottom]");
        }

        // Livestock spawn markers
        b.addMarker(SpawnMarker.MarkerKind.ANIMAL, 3, 1, 2, "minecraft:cow");
        b.addMarker(SpawnMarker.MarkerKind.ANIMAL, 6, 1, 2, "minecraft:sheep");
        b.addMarker(SpawnMarker.MarkerKind.ANIMAL, 3, 1, 4, "minecraft:pig");
        b.addMarker(SpawnMarker.MarkerKind.ANIMAL, 5, 1, 3, "minecraft:chicken");

        return b.roadAttach(4, 0).build();
    }

    /**
     * Cozy 7x6x7 starter cottage with stone foundation, timber-framed walls, windows, front door,
     * pitched gable roof, interior lighting, crafting table, chest, and 1 bed (+1 villager).
     */
    private static StructureBlueprint buildSmallCottage(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(ProjectType.SMALL_COTTAGE, style, 7, 6, 7);

        // Foundation & floor
        b.fill(0, 0, 0, 6, 0, 6, p.baseStone());
        b.fill(1, 0, 1, 5, 0, 5, p.floorBlock());

        // Walls (dy=1..3)
        for (int y = 1; y <= 3; y++) {
            String wallMat = (y == 1) ? p.accentStone() : p.wallBlock();
            for (int x = 1; x <= 5; x++) {
                b.set(x, y, 0, wallMat);
                b.set(x, y, 6, wallMat);
            }
            for (int z = 1; z <= 5; z++) {
                b.set(0, y, z, wallMat);
                b.set(6, y, z, wallMat);
            }
        }
        // Corner pillars
        b.pillar(0, 1, 3, 0, p.log());
        b.pillar(6, 1, 3, 0, p.log());
        b.pillar(0, 1, 3, 6, p.log());
        b.pillar(6, 1, 3, 6, p.log());

        // Door & windows
        b.setDoor(3, 1, 0, p.door(), Facing2D.NORTH);
        b.set(1, 2, 0, p.glassPane());
        b.set(5, 2, 0, p.glassPane());
        b.set(0, 2, 3, p.glassPane());
        b.set(6, 2, 3, p.glassPane());
        b.set(3, 2, 6, p.glassPane());

        // Interior furnishings & bed
        b.setBed(1, 1, 4, p.bed(), Facing2D.SOUTH);
        b.set(5, 1, 5, "minecraft:crafting_table");
        b.set(5, 1, 4, "minecraft:chest[facing=west]");
        b.set(3, 3, 1, "minecraft:wall_torch[facing=south]");
        b.set(3, 3, 3, p.lampLight() + "[hanging=true]");

        // Pitched gable roof (dy=4..5)
        b.fill(2, 4, 1, 4, 4, 5, p.planks()); // ceiling deck supporting hanging lantern
        for (int x = 2; x <= 4; x++) {
            b.set(x, 4, 0, p.planks());
            b.set(x, 4, 6, p.planks());
        }
        for (int z = 0; z <= 6; z++) {
            b.set(0, 4, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(1, 4, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(5, 4, z, p.roofStairs() + "[facing=west,half=bottom]");
            b.set(6, 4, z, p.roofStairs() + "[facing=west,half=bottom]");

            b.set(2, 5, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(3, 5, z, p.roofSlab() + "[type=bottom]");
            b.set(4, 5, z, p.roofStairs() + "[facing=west,half=bottom]");
        }

        b.addMarker(SpawnMarker.MarkerKind.VILLAGER, 3, 1, 3, "minecraft:villager");
        return b.roadAttach(3, 0).build();
    }

    /**
     * 9x8x9 two-room medium residence with front porch lanterns, side stone chimney, pitched
     * overhang roof, windows, door, furnace/chest/crafting decor, and 2 beds (+2 villagers).
     */
    private static StructureBlueprint buildMediumResidence(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(ProjectType.MEDIUM_RESIDENCE, style, 9, 8, 9);

        // Foundation, floor, front porch, and chimney base
        b.fill(1, 0, 1, 7, 0, 7, p.baseStone());
        b.fill(2, 0, 2, 6, 0, 6, p.floorBlock());
        b.fill(2, 0, 0, 6, 0, 0, p.baseStone());
        b.set(4, 0, 0, p.stairs() + "[facing=south,half=bottom]");
        b.set(8, 0, 5, p.accentStone());

        // Main body walls (dx=1..7, dz=1..7, dy=1..4)
        for (int y = 1; y <= 4; y++) {
            String wallMat = (y == 1) ? p.accentStone() : p.wallBlock();
            for (int x = 2; x <= 6; x++) {
                b.set(x, y, 1, wallMat);
                b.set(x, y, 7, wallMat);
            }
            for (int z = 2; z <= 6; z++) {
                b.set(1, y, z, wallMat);
                b.set(7, y, z, wallMat);
            }
        }
        // Corner pillars
        b.pillar(1, 1, 4, 1, p.log());
        b.pillar(7, 1, 4, 1, p.log());
        b.pillar(1, 1, 4, 7, p.log());
        b.pillar(7, 1, 4, 7, p.log());

        // Front porch lantern posts
        b.set(2, 1, 0, p.lampPost());
        b.set(2, 2, 0, p.lampLight() + "[hanging=false]");
        b.set(6, 1, 0, p.lampPost());
        b.set(6, 2, 0, p.lampLight() + "[hanging=false]");

        // Door & windows
        b.setDoor(4, 1, 1, p.door(), Facing2D.NORTH);
        b.set(2, 2, 1, p.glassPane());
        b.set(6, 2, 1, p.glassPane());
        b.set(1, 2, 3, p.glassPane());
        b.set(1, 2, 5, p.glassPane());
        b.set(7, 2, 3, p.glassPane());
        b.set(3, 2, 7, p.glassPane());
        b.set(5, 2, 7, p.glassPane());

        // Two-room interior divider & furnishings
        b.fill(4, 1, 5, 4, 3, 6, p.planks());
        b.setBed(2, 1, 5, p.bed(), Facing2D.SOUTH);
        b.setBed(6, 1, 5, p.bed(), Facing2D.SOUTH);
        b.set(6, 1, 4, "minecraft:furnace[facing=west]");
        b.set(2, 1, 2, "minecraft:crafting_table");
        b.set(2, 1, 3, "minecraft:chest[facing=east]");

        // Ceiling deck & hanging lanterns
        b.fill(2, 4, 2, 6, 4, 6, p.planks());
        b.set(3, 3, 3, p.lampLight() + "[hanging=true]");
        b.set(5, 3, 3, p.lampLight() + "[hanging=true]");

        // Pitched overhang roof (dy=4..7)
        for (int z = 0; z <= 8; z++) {
            b.set(0, 4, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(8, 4, z, p.roofStairs() + "[facing=west,half=bottom]");

            b.set(1, 5, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(7, 5, z, p.roofStairs() + "[facing=west,half=bottom]");

            b.set(2, 6, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(6, 6, z, p.roofStairs() + "[facing=west,half=bottom]");

            b.set(3, 7, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(4, 7, z, p.roofSlab() + "[type=bottom]");
            b.set(5, 7, z, p.roofStairs() + "[facing=west,half=bottom]");
        }
        // Gable triangles at dz=1 and dz=7
        b.fill(2, 5, 1, 6, 5, 1, p.planks());
        b.fill(2, 5, 7, 6, 5, 7, p.planks());
        b.fill(3, 6, 1, 5, 6, 1, p.planks());
        b.fill(3, 6, 7, 5, 6, 7, p.planks());

        // Exterior stone chimney rising above the roof
        for (int y = 1; y <= 6; y++) {
            b.set(8, y, 5, p.accentStone());
        }
        b.set(8, 7, 5, p.baseStone());

        b.addMarker(SpawnMarker.MarkerKind.VILLAGER, 3, 1, 4, "minecraft:villager");
        b.addMarker(SpawnMarker.MarkerKind.VILLAGER, 5, 1, 3, "minecraft:villager");
        return b.roadAttach(4, 0).build();
    }

    /**
     * Impressive 11x13x9 multi-story townhouse with ground-floor stone arcade & shopfront,
     * interior staircase to second floor, cantilevered front balcony, pitched roof with front
     * dormer & lanterns, and 3 beds (+3 villagers).
     */
    private static StructureBlueprint buildMultiStoryTownhouse(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(
                ProjectType.MULTI_STORY_TOWNHOUSE, style, 11, 13, 9);

        // Ground foundation & interior floor (dy=0)
        b.fill(1, 0, 0, 9, 0, 7, p.accentStone());
        b.fill(2, 0, 2, 8, 0, 6, p.floorBlock());

        // Ground-floor stone masonry walls & front arcade (dy=1..3)
        for (int y = 1; y <= 3; y++) {
            String mat = (y == 3) ? p.accentStone() : p.baseStone();
            for (int x = 1; x <= 9; x++) {
                b.set(x, y, 7, mat);
            }
            for (int z = 2; z <= 6; z++) {
                b.set(1, y, z, mat);
                b.set(9, y, z, mat);
            }
        }
        // Front arcade columns & shopfront facade (dz=1)
        int[] arcadeCols = {1, 3, 7, 9};
        for (int colX : arcadeCols) {
            for (int y = 1; y <= 3; y++) {
                b.set(colX, y, 1, p.accentStone());
            }
        }
        b.fill(2, 3, 1, 8, 3, 1, p.accentStone());
        b.fill(4, 1, 1, 4, 2, 1, p.baseStone());
        b.fill(6, 1, 1, 6, 2, 1, p.baseStone());
        b.set(2, 1, 1, p.glassPane());
        b.set(2, 2, 1, p.glassPane());
        b.set(8, 1, 1, p.glassPane());
        b.set(8, 2, 1, p.glassPane());
        b.setDoor(5, 1, 1, p.door(), Facing2D.NORTH);

        // Ground-floor side & back windows
        b.set(1, 2, 4, p.glassPane());
        b.set(9, 2, 4, p.glassPane());
        b.set(3, 2, 7, p.glassPane());
        b.set(7, 2, 7, p.glassPane());

        // Shopfront interior counters & fixtures
        b.set(2, 1, 3, "minecraft:crafting_table");
        b.set(2, 1, 4, "minecraft:furnace[facing=east]");
        b.set(8, 1, 3, "minecraft:chest[facing=west]");
        b.set(8, 1, 4, "minecraft:barrel[facing=up]");
        b.set(3, 3, 3, p.lampLight() + "[hanging=true]");
        b.set(7, 3, 3, p.lampLight() + "[hanging=true]");

        // Second-floor timber deck & cantilevered front balcony (dy=4)
        b.fill(1, 4, 1, 9, 4, 7, p.floorBlock());
        b.fill(2, 4, 0, 8, 4, 0, p.planks());

        // Interior staircase along back wall dz=6 rising to second floor
        b.set(3, 1, 6, p.stairs() + "[facing=east,half=bottom]");
        b.set(4, 2, 6, p.stairs() + "[facing=east,half=bottom]");
        b.set(5, 3, 6, p.stairs() + "[facing=east,half=bottom]");
        b.clear(4, 4, 6);
        b.clear(5, 4, 6);
        b.set(6, 4, 6, p.stairs() + "[facing=east,half=bottom]");

        // Balcony railing & corner lanterns (dy=5..6)
        b.fill(2, 5, 0, 8, 5, 0, p.fence());
        b.set(2, 6, 0, p.lampLight() + "[hanging=false]");
        b.set(8, 6, 0, p.lampLight() + "[hanging=false]");

        // Second-floor timber-framed walls (dy=5..7)
        for (int y = 5; y <= 7; y++) {
            for (int x = 2; x <= 8; x++) {
                b.set(x, y, 1, p.wallBlock());
                b.set(x, y, 7, p.wallBlock());
            }
            for (int z = 2; z <= 6; z++) {
                b.set(1, y, z, p.wallBlock());
                b.set(9, y, z, p.wallBlock());
            }
        }
        b.pillar(1, 5, 7, 1, p.log());
        b.pillar(9, 5, 7, 1, p.log());
        b.pillar(1, 5, 7, 7, p.log());
        b.pillar(9, 5, 7, 7, p.log());

        // Balcony door & upper windows
        b.setDoor(5, 5, 1, p.door(), Facing2D.NORTH);
        b.set(3, 6, 1, p.glassPane());
        b.set(7, 6, 1, p.glassPane());
        b.set(1, 6, 4, p.glassPane());
        b.set(9, 6, 4, p.glassPane());
        b.set(3, 6, 7, p.glassPane());
        b.set(7, 6, 7, p.glassPane());

        // Three resident beds & upper furnishings
        b.setBed(2, 5, 2, p.bed(), Facing2D.EAST);
        b.setBed(8, 5, 2, p.bed(), Facing2D.WEST);
        b.setBed(8, 5, 5, p.bed(), Facing2D.WEST);
        b.set(2, 5, 5, "minecraft:chest[facing=east]");
        b.set(4, 7, 3, p.lampLight() + "[hanging=true]");
        b.set(6, 7, 3, p.lampLight() + "[hanging=true]");

        // Attic ceiling & pitched gable roof (dy=8..12)
        b.fill(1, 8, 1, 9, 8, 7, p.planks());
        for (int x = 0; x <= 10; x++) {
            b.set(x, 8, 0, p.roofStairs() + "[facing=south,half=bottom]");
            b.set(x, 8, 8, p.roofStairs() + "[facing=north,half=bottom]");

            b.set(x, 9, 1, p.roofStairs() + "[facing=south,half=bottom]");
            b.set(x, 9, 7, p.roofStairs() + "[facing=north,half=bottom]");

            b.set(x, 10, 2, p.roofStairs() + "[facing=south,half=bottom]");
            b.set(x, 10, 6, p.roofStairs() + "[facing=north,half=bottom]");

            b.set(x, 11, 3, p.roofStairs() + "[facing=south,half=bottom]");
            b.set(x, 11, 5, p.roofStairs() + "[facing=north,half=bottom]");

            b.set(x, 12, 4, p.roofSlab() + "[type=bottom]");
        }
        // Side gable walls (dx=1 and dx=9)
        b.fill(1, 9, 2, 1, 9, 6, p.wallBlock());
        b.fill(9, 9, 2, 9, 9, 6, p.wallBlock());
        b.fill(1, 10, 3, 1, 10, 5, p.wallBlock());
        b.fill(9, 10, 3, 9, 10, 5, p.wallBlock());
        b.set(1, 11, 4, p.wallBlock());
        b.set(9, 11, 4, p.wallBlock());

        // Front dormer window & overhanging dormer lantern
        b.set(4, 9, 1, p.planks());
        b.set(6, 9, 1, p.planks());
        b.set(4, 10, 1, p.planks());
        b.set(6, 10, 1, p.planks());
        b.set(5, 9, 1, p.glassPane());
        b.set(5, 10, 1, p.glassPane());
        b.fill(4, 11, 1, 6, 11, 2, p.roofSlab() + "[type=bottom]");
        b.set(5, 10, 0, p.roofSlab() + "[type=top]");
        b.set(5, 9, 0, p.lampLight() + "[hanging=true]");

        // 3 villager spawn markers
        b.addMarker(SpawnMarker.MarkerKind.VILLAGER, 5, 1, 3, "minecraft:villager");
        b.addMarker(SpawnMarker.MarkerKind.VILLAGER, 3, 5, 4, "minecraft:villager");
        b.addMarker(SpawnMarker.MarkerKind.VILLAGER, 6, 5, 4, "minecraft:villager");

        return b.roadAttach(5, 0).build();
    }

    /**
     * 9x5x7 waterfront logistics pier extending from shore ({@code dz=0}) over water ({@code dz=6})
     * with a flat unobstructed 5x5 wooden deck, mooring posts, and lanterns sized for {@code lc2:smart_port}.
     */
    private static StructureBlueprint buildLc2WaterfrontPier(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(
                ProjectType.LC2_WATERFRONT_PIER, style, 9, 5, 7);
        String pierLog = BiomePalette.withAxisIfSupported(p.waterPierLog(), "y");

        // Shore stone abutment
        b.fill(0, 0, 0, 8, 0, 0, p.baseStone());
        // Open wooden pier deck
        b.fill(1, 0, 1, 7, 0, 5, p.planks());
        // Timber pier border beams
        for (int z = 1; z <= 6; z++) {
            b.set(0, 0, z, pierLog);
            b.set(8, 0, z, pierLog);
        }
        for (int x = 1; x <= 7; x++) {
            b.set(x, 0, 6, pierLog);
        }

        // Four corner mooring posts with beacon lanterns
        int[][] mooringPosts = {{0, 2}, {8, 2}, {0, 6}, {8, 6}};
        for (int[] mp : mooringPosts) {
            b.set(mp[0], 1, mp[1], pierLog);
            b.set(mp[0], 2, mp[1], pierLog);
            b.set(mp[0], 3, mp[1], p.lampLight() + "[hanging=false]");
        }

        // Side safety rails leaving the waterfront edge (dz=6) and 5x5 deck (dx=2..6, dz=1..5) open
        for (int z = 3; z <= 5; z++) {
            b.set(0, 1, z, p.fence());
            b.set(8, 1, z, p.fence());
        }

        b.addMarker(SpawnMarker.MarkerKind.LC2_PORT_PAD, 4, 1, 3, "lc2:smart_port");
        return b.roadAttach(4, 0).build();
    }

    /**
     * 9x4x9 open-sky stone plaza with illuminated corner beacons and center landing pad markings
     * specifically sized for placing an {@code lc2:cargo_station} and automated cargo drones.
     */
    private static StructureBlueprint buildLc2HelipadPlaza(BiomeStyle style, BiomePalette p) {
        StructureBlueprint.Builder b = StructureBlueprint.builder(
                ProjectType.LC2_HELIPAD_PLAZA, style, 9, 4, 9);

        // Stone-brick perimeter frame & flat apron
        b.fill(0, 0, 0, 8, 0, 8, p.accentStone());
        b.fill(1, 0, 1, 7, 0, 7, p.helipadBlock());

        // High-visibility helipad corner markers and center 'H' pad
        b.set(2, 0, 2, "minecraft:yellow_terracotta");
        b.set(6, 0, 2, "minecraft:yellow_terracotta");
        b.set(2, 0, 6, "minecraft:yellow_terracotta");
        b.set(6, 0, 6, "minecraft:yellow_terracotta");
        for (int z = 3; z <= 5; z++) {
            b.set(3, 0, z, "minecraft:yellow_terracotta");
            b.set(5, 0, z, "minecraft:yellow_terracotta");
        }
        b.set(4, 0, 4, "minecraft:yellow_terracotta");

        // Illuminated corner beacons
        int[][] corners = {{0, 0}, {8, 0}, {0, 8}, {8, 8}};
        for (int[] c : corners) {
            b.set(c[0], 1, c[1], p.accentStone());
            b.set(c[0], 2, c[1], p.lampPost());
            b.set(c[0], 3, c[1], p.lampLight() + "[hanging=false]");
        }

        // Low side/rear perimeter curb slabs leaving front entry and 7x7 sky corridor open
        for (int i = 3; i <= 5; i++) {
            b.set(i, 1, 8, p.slab() + "[type=bottom]");
            b.set(0, 1, i, p.slab() + "[type=bottom]");
            b.set(8, 1, i, p.slab() + "[type=bottom]");
        }

        b.addMarker(SpawnMarker.MarkerKind.LC2_HELIPAD_CENTER, 4, 1, 4, "lc2:cargo_station");
        return b.roadAttach(4, 0).build();
    }
}
