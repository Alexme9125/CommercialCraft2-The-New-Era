package com.tycooncraft.tyc2.village;

import java.util.List;
import java.util.Objects;

/**
 * Procedural 3D architectural blueprint catalog for player-funded aided villager workplaces.
 *
 * <p>Covers all 13 vanilla {@link WorkplaceArchetype} styles plus {@link WorkplaceArchetype#GENERIC}
 * for third-party modded professions across both {@link WorkplaceSize#SMALL} ({@code 7x6x7}, 1 workstation)
 * and {@link WorkplaceSize#LARGE} ({@code 9x7x9}, 3 workstations) in all 7 {@link BiomeStyle} palettes.
 *
 * <p>Architectural guarantees:
 * <ul>
 *   <li>Zero beds and zero villager spawn markers (workplaces create jobs, never population bloat).</li>
 *   <li>Every workstation block uses exact {@link WorkplaceSpec#workstationBlockId()} and emits a
 *       matching {@link SpawnMarker.MarkerKind#WORKSTATION} marker with {@code hint = spec.professionId()}.</li>
 *   <li>All block IDs are cross-version safe across Forge 1.19.2 and NeoForge 1.21.1 / 26.1.2 / 26.2.</li>
 * </ul>
 */
public final class WorkplaceArchitectureCatalog {

    private static final WorkplaceSpec GENERIC_FALLBACK_SPEC = new WorkplaceSpec(
            "tyc2:generic",
            "minecraft:crafting_table",
            WorkplaceArchetype.GENERIC,
            WorkplaceCatalog.DEFAULT_MODDED_BASE_COST_SMALL_IC,
            WorkplaceCatalog.DEFAULT_LARGE_COST_MULTIPLIER,
            1,
            3,
            List.of(),
            true,
            false,
            "Workshop");

    private WorkplaceArchitectureCatalog() {
    }

    /**
     * Builds the canonical {@link Facing2D#NORTH}-facing {@link StructureBlueprint} for the given
     * workplace spec, size, and village biome style.
     *
     * @param spec workplace specification (provides archetype, profession ID, and exact workstation block ID)
     * @param size building scale ({@link WorkplaceSize#SMALL} or {@link WorkplaceSize#LARGE})
     * @param style village biome architectural style
     * @return immutable bottom-up sorted blueprint
     */
    public static StructureBlueprint getBlueprint(
            WorkplaceSpec spec, WorkplaceSize size, BiomeStyle style) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(style, "style");
        return size == WorkplaceSize.LARGE
                ? buildLargeWorkplace(spec, style)
                : buildSmallWorkplace(spec, style);
    }

    /**
     * Builds the {@link StructureBlueprint} rotated to {@code facing}.
     *
     * @param spec workplace specification
     * @param size building scale
     * @param style village biome style
     * @param facing desired placement orientation
     * @return immutable rotated blueprint
     */
    public static StructureBlueprint getBlueprint(
            WorkplaceSpec spec, WorkplaceSize size, BiomeStyle style, Facing2D facing) {
        Objects.requireNonNull(facing, "facing");
        return getBlueprint(spec, size, style).rotated(facing);
    }

    /**
     * Builds a canonical generic workshop blueprint for the given size and biome style
     * (used as fallback by {@link VillageArchitectureCatalog} for {@code AID_WORKPLACE_*} project types).
     *
     * @param size building scale
     * @param style village biome style
     * @return generic workshop blueprint
     */
    public static StructureBlueprint getGenericBlueprint(WorkplaceSize size, BiomeStyle style) {
        return getBlueprint(GENERIC_FALLBACK_SPEC, size, style);
    }

    /**
     * 7x6x7 single-workstation aided workplace with archetype-specific architecture and 0 beds.
     */
    private static StructureBlueprint buildSmallWorkplace(WorkplaceSpec spec, BiomeStyle style) {
        BiomePalette p = style.palette();
        WorkplaceArchetype arch = spec.archetype();
        StructureBlueprint.Builder b = StructureBlueprint.builder(
                ProjectType.AID_WORKPLACE_SMALL, style, 7, 6, 7);

        boolean stoneHeavy = isStoneHeavyArchetype(arch);

        // Foundation & floor (dy=0)
        b.fill(0, 0, 0, 6, 0, 6, stoneHeavy ? p.accentStone() : p.baseStone());
        b.fill(1, 0, 1, 5, 0, 5, stoneHeavy ? p.baseStone() : p.floorBlock());

        // Walls (dy=1..3)
        String lowerWall = stoneHeavy ? p.baseStone() : p.accentStone();
        String upperWall = stoneHeavy ? p.accentStone() : p.wallBlock();
        for (int y = 1; y <= 3; y++) {
            String mat = (y == 1) ? lowerWall : upperWall;
            for (int x = 1; x <= 5; x++) {
                b.set(x, y, 0, mat);
                b.set(x, y, 6, mat);
            }
            for (int z = 1; z <= 5; z++) {
                b.set(0, y, z, mat);
                b.set(6, y, z, mat);
            }
        }

        // Corner pillars
        String cornerMat = stoneHeavy ? p.accentStone() : p.log();
        b.pillar(0, 1, 3, 0, cornerMat);
        b.pillar(6, 1, 3, 0, cornerMat);
        b.pillar(0, 1, 3, 6, cornerMat);
        b.pillar(6, 1, 3, 6, cornerMat);

        // Front entrance & default windows
        b.setDoor(3, 1, 0, p.door(), Facing2D.NORTH);
        String windowBlock = windowBlockFor(arch, p);
        b.set(1, 2, 0, windowBlock);
        b.set(5, 2, 0, windowBlock);
        b.set(0, 2, 3, windowBlock);
        b.set(6, 2, 3, windowBlock);
        b.set(3, 2, 6, windowBlock);

        // Ceiling deck & pitched gable roof (dy=4..5)
        b.fill(2, 4, 1, 4, 4, 5, p.planks());
        for (int x = 2; x <= 4; x++) {
            b.set(x, 4, 0, stoneHeavy ? p.accentStone() : p.planks());
            b.set(x, 4, 6, stoneHeavy ? p.accentStone() : p.planks());
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

        // Interior lighting
        b.set(3, 3, 1, "minecraft:wall_torch[facing=south]");
        b.set(3, 3, 3, p.lampLight() + "[hanging=true]");

        // Archetype-specific exterior/interior styling (never blocking central aisle x=3, z=1..4)
        decorateSmallArchetype(b, arch, p);

        // Exact profession workstation & WORKSTATION marker at accessible center-rear (3, 1, 4)
        b.set(3, 1, 4, spec.workstationBlockId());
        b.clear(3, 2, 4);
        b.addMarker(SpawnMarker.MarkerKind.WORKSTATION, 3, 1, 4, spec.professionId());

        return b.roadAttach(3, 0).build();
    }

    /**
     * 9x7x9 three-workstation aided workshop with front porch, distinctive archetype features, and 0 beds.
     */
    private static StructureBlueprint buildLargeWorkplace(WorkplaceSpec spec, BiomeStyle style) {
        BiomePalette p = style.palette();
        WorkplaceArchetype arch = spec.archetype();
        StructureBlueprint.Builder b = StructureBlueprint.builder(
                ProjectType.AID_WORKPLACE_LARGE, style, 9, 7, 9);

        boolean stoneHeavy = isStoneHeavyArchetype(arch);

        // Foundation, interior floor, and front porch apron (dy=0)
        b.fill(1, 0, 1, 7, 0, 8, stoneHeavy ? p.accentStone() : p.baseStone());
        b.fill(2, 0, 2, 6, 0, 7, stoneHeavy ? p.baseStone() : p.floorBlock());
        b.fill(2, 0, 0, 6, 0, 0, p.baseStone());
        b.set(4, 0, 0, p.stairs() + "[facing=south,half=bottom]");

        // Front porch lanterns
        b.set(2, 1, 0, p.lampPost());
        b.set(2, 2, 0, p.lampLight() + "[hanging=false]");
        b.set(6, 1, 0, p.lampPost());
        b.set(6, 2, 0, p.lampLight() + "[hanging=false]");

        // Main perimeter walls (dx=1..7, dz=1..8, dy=1..4)
        String lowerWall = stoneHeavy ? p.baseStone() : p.accentStone();
        String upperWall = stoneHeavy ? p.accentStone() : p.wallBlock();
        for (int y = 1; y <= 4; y++) {
            String mat = (y == 1) ? lowerWall : upperWall;
            for (int x = 2; x <= 6; x++) {
                b.set(x, y, 1, mat);
                b.set(x, y, 8, mat);
            }
            for (int z = 2; z <= 7; z++) {
                b.set(1, y, z, mat);
                b.set(7, y, z, mat);
            }
        }

        // Corner pillars
        String cornerMat = stoneHeavy ? p.accentStone() : p.log();
        b.pillar(1, 1, 4, 1, cornerMat);
        b.pillar(7, 1, 4, 1, cornerMat);
        b.pillar(1, 1, 4, 8, cornerMat);
        b.pillar(7, 1, 4, 8, cornerMat);

        // Front entrance & windows
        b.setDoor(4, 1, 1, p.door(), Facing2D.NORTH);
        String windowBlock = windowBlockFor(arch, p);
        b.set(2, 2, 1, windowBlock);
        b.set(6, 2, 1, windowBlock);
        b.set(1, 2, 3, windowBlock);
        b.set(1, 2, 6, windowBlock);
        b.set(7, 2, 3, windowBlock);
        b.set(7, 2, 6, windowBlock);
        b.set(3, 2, 8, windowBlock);
        b.set(5, 2, 8, windowBlock);

        // Ceiling deck (dy=4) & gable walls (dy=5)
        b.fill(2, 4, 2, 6, 4, 7, p.planks());
        b.fill(2, 5, 1, 6, 5, 1, stoneHeavy ? p.accentStone() : p.planks());
        b.fill(2, 5, 8, 6, 5, 8, stoneHeavy ? p.accentStone() : p.planks());

        // Overhanging pitched workshop roof (dy=4..6)
        for (int z = 0; z <= 8; z++) {
            b.set(0, 4, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(8, 4, z, p.roofStairs() + "[facing=west,half=bottom]");

            b.set(1, 5, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(7, 5, z, p.roofStairs() + "[facing=west,half=bottom]");

            b.set(2, 6, z, p.roofStairs() + "[facing=east,half=bottom]");
            b.set(3, 6, z, p.roofSlab() + "[type=bottom]");
            b.set(4, 6, z, p.roofSlab() + "[type=bottom]");
            b.set(5, 6, z, p.roofSlab() + "[type=bottom]");
            b.set(6, 6, z, p.roofStairs() + "[facing=west,half=bottom]");
        }

        // Interior lighting
        b.set(4, 3, 2, "minecraft:wall_torch[facing=south]");
        b.set(3, 3, 4, p.lampLight() + "[hanging=true]");
        b.set(5, 3, 4, p.lampLight() + "[hanging=true]");

        // Archetype-specific exterior/interior styling (never blocking hall x=3..5,z=2..5 or workstations)
        decorateLargeArchetype(b, arch, p);

        // Exact 3 profession workstations & matching WORKSTATION markers
        int[][] stationCoords = {{2, 1, 4}, {4, 1, 6}, {6, 1, 4}};
        for (int[] c : stationCoords) {
            b.set(c[0], c[1], c[2], spec.workstationBlockId());
            b.clear(c[0], c[1] + 1, c[2]);
            b.addMarker(SpawnMarker.MarkerKind.WORKSTATION, c[0], c[1], c[2], spec.professionId());
        }

        return b.roadAttach(4, 0).build();
    }

    private static void decorateSmallArchetype(
            StructureBlueprint.Builder b, WorkplaceArchetype arch, BiomePalette p) {
        switch (arch) {
            case FARMER -> {
                // Barn hay stacks and timber grain storage
                b.set(1, 1, 5, "minecraft:hay_block[axis=y]");
                b.set(1, 2, 5, "minecraft:hay_block[axis=y]");
                b.set(2, 1, 5, "minecraft:hay_block[axis=y]");
                b.set(5, 1, 5, "minecraft:chest[facing=west]");
                b.set(1, 3, 0, p.slab() + "[type=top]");
                b.set(5, 3, 0, p.slab() + "[type=top]");
            }
            case FISHERMAN -> {
                // Recessed indoor water catch basin and tackle chest
                b.set(1, 0, 4, "minecraft:water");
                b.set(1, 0, 5, "minecraft:water");
                b.set(2, 0, 4, p.baseStone());
                b.set(2, 0, 5, p.baseStone());
                b.set(5, 1, 5, "minecraft:chest[facing=west]");
                b.set(5, 1, 2, p.fence());
                b.set(5, 2, 2, p.lampLight() + "[hanging=false]");
            }
            case SHEPHERD -> {
                // Colorful wool awning trim and interior cloth bolts
                b.set(1, 3, 0, "minecraft:red_wool");
                b.set(2, 3, 0, "minecraft:white_wool");
                b.set(4, 3, 0, "minecraft:white_wool");
                b.set(5, 3, 0, "minecraft:blue_wool");
                b.set(1, 1, 5, "minecraft:white_wool");
                b.set(1, 2, 5, "minecraft:yellow_wool");
                b.set(5, 1, 5, "minecraft:blue_wool");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
            }
            case FLETCHER -> {
                // Archery target on rear wall, timber arrow rack, and workbench
                b.set(3, 2, 6, "minecraft:target");
                b.set(1, 1, 5, BiomePalette.withAxisIfSupported(p.strippedLog(), "y"));
                b.set(1, 2, 5, p.fence());
                b.set(5, 1, 5, "minecraft:crafting_table");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
            }
            case BUTCHER -> {
                // Brick chimney stack and clean smooth-stone counter
                for (int y = 1; y <= 5; y++) {
                    b.set(6, y, 5, "minecraft:bricks");
                }
                b.set(1, 1, 5, "minecraft:smooth_stone");
                b.set(2, 1, 5, "minecraft:smooth_stone_slab[type=bottom]");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
            }
            case LEATHERWORKER -> {
                // Stone splash floor and wooden hide drying rack
                b.fill(1, 0, 1, 5, 0, 5, p.baseStone());
                b.set(1, 1, 5, p.fence());
                b.set(1, 2, 5, p.fence());
                b.set(2, 2, 5, p.slab() + "[type=top]");
                b.set(5, 1, 5, "minecraft:chest[facing=west]");
                b.set(5, 1, 2, "minecraft:crafting_table");
            }
            case MASON -> {
                // Dressed stone studio with polished andesite blocks and sample shelf
                b.set(1, 1, 5, "minecraft:polished_andesite");
                b.set(1, 2, 5, "minecraft:smooth_stone_slab[type=bottom]");
                b.set(2, 1, 5, "minecraft:stone_bricks");
                b.set(5, 1, 5, "minecraft:polished_andesite");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
                b.set(3, 4, 0, p.accentStone());
            }
            case CARTOGRAPHER -> {
                // Wide daylight windows, reference bookshelves, and drafting table
                b.set(0, 2, 2, p.glassPane());
                b.set(0, 2, 4, p.glassPane());
                b.set(6, 2, 2, p.glassPane());
                b.set(6, 2, 4, p.glassPane());
                b.set(1, 1, 5, "minecraft:bookshelf");
                b.set(1, 2, 5, "minecraft:bookshelf");
                b.set(5, 1, 5, "minecraft:bookshelf");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
            }
            case TOOLSMITH -> {
                // Semi-open forge screen, anvil, furnace hearth, and stone chimney
                b.set(0, 2, 2, "minecraft:iron_bars");
                b.set(6, 2, 2, "minecraft:iron_bars");
                b.set(1, 1, 5, "minecraft:furnace[facing=east]");
                b.set(5, 1, 5, "minecraft:anvil[facing=west]");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
                for (int y = 1; y <= 5; y++) {
                    b.set(0, y, 5, p.accentStone());
                }
            }
            case WEAPONSMITH -> {
                // Fortified iron-bar armory with anvil, weapon rack chest, and stone hearth
                b.set(0, 2, 2, "minecraft:iron_bars");
                b.set(0, 2, 4, "minecraft:iron_bars");
                b.set(6, 2, 2, "minecraft:iron_bars");
                b.set(6, 2, 4, "minecraft:iron_bars");
                b.set(1, 1, 5, "minecraft:anvil[facing=east]");
                b.set(5, 1, 5, "minecraft:furnace[facing=west]");
                b.set(5, 1, 2, "minecraft:chest[facing=west]");
            }
            case ARMORER -> {
                // Heavy foundry masonry, tall chimney, iron vents, and anvil
                for (int y = 1; y <= 5; y++) {
                    b.set(6, y, 5, p.baseStone());
                }
                b.set(1, 1, 5, "minecraft:anvil[facing=east]");
                b.set(2, 1, 5, "minecraft:smooth_stone");
                b.set(5, 1, 4, "minecraft:chest[facing=west]");
                b.set(3, 4, 0, "minecraft:iron_bars");
            }
            case CLERIC -> {
                // Stained-glass apothecary chapel with altar steps and pinnacle ridge
                b.set(3, 4, 0, "minecraft:magenta_stained_glass_pane");
                b.set(3, 5, 3, p.accentStone());
                b.set(1, 1, 5, p.accentStone());
                b.set(1, 2, 5, p.lampLight() + "[hanging=false]");
                b.set(5, 1, 5, p.accentStone());
                b.set(5, 2, 5, p.lampLight() + "[hanging=false]");
                b.set(2, 1, 5, "minecraft:chest[facing=north]");
            }
            case LIBRARIAN -> {
                // Wrap-around bookshelves and bright reading hall
                b.set(1, 1, 5, "minecraft:bookshelf");
                b.set(1, 2, 5, "minecraft:bookshelf");
                b.set(2, 1, 5, "minecraft:bookshelf");
                b.set(4, 1, 5, "minecraft:bookshelf");
                b.set(5, 1, 5, "minecraft:bookshelf");
                b.set(5, 2, 5, "minecraft:bookshelf");
                b.set(1, 1, 2, "minecraft:bookshelf");
                b.set(5, 1, 2, "minecraft:chest[facing=west]");
            }
            case GENERIC -> {
                // Clean biome-styled artisan workshop for modded professions
                b.set(1, 1, 5, "minecraft:crafting_table");
                b.set(5, 1, 5, "minecraft:chest[facing=west]");
                b.set(1, 1, 2, p.slab() + "[type=bottom]");
                b.set(5, 1, 2, p.slab() + "[type=bottom]");
            }
        }
    }

    private static void decorateLargeArchetype(
            StructureBlueprint.Builder b, WorkplaceArchetype arch, BiomePalette p) {
        switch (arch) {
            case FARMER -> {
                // Stacked barn hay bales in rear corners and produce chests
                b.set(2, 1, 7, "minecraft:hay_block[axis=y]");
                b.set(2, 2, 7, "minecraft:hay_block[axis=y]");
                b.set(3, 1, 7, "minecraft:hay_block[axis=y]");
                b.set(6, 1, 7, "minecraft:hay_block[axis=y]");
                b.set(6, 2, 7, "minecraft:hay_block[axis=y]");
                b.set(5, 1, 7, "minecraft:hay_block[axis=y]");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:crafting_table");
            }
            case FISHERMAN -> {
                // Indoor water trough along rear corners, drying rack, and storage chests
                b.set(2, 0, 7, "minecraft:water");
                b.set(3, 0, 7, "minecraft:water");
                b.set(5, 0, 7, "minecraft:water");
                b.set(6, 0, 7, "minecraft:water");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
                b.set(2, 1, 6, p.fence());
                b.set(6, 1, 6, p.fence());
            }
            case SHEPHERD -> {
                // Colorful wool textile frieze and interior dyed wool bales
                b.set(2, 3, 1, "minecraft:red_wool");
                b.set(3, 3, 1, "minecraft:yellow_wool");
                b.set(5, 3, 1, "minecraft:yellow_wool");
                b.set(6, 3, 1, "minecraft:blue_wool");
                b.set(2, 1, 7, "minecraft:white_wool");
                b.set(2, 2, 7, "minecraft:red_wool");
                b.set(6, 1, 7, "minecraft:white_wool");
                b.set(6, 2, 7, "minecraft:blue_wool");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:crafting_table");
            }
            case FLETCHER -> {
                // Dual archery targets on rear wall and fletching benches
                b.set(3, 2, 8, "minecraft:target");
                b.set(5, 2, 8, "minecraft:target");
                b.set(2, 1, 7, BiomePalette.withAxisIfSupported(p.strippedLog(), "y"));
                b.set(2, 2, 7, p.fence());
                b.set(6, 1, 7, BiomePalette.withAxisIfSupported(p.strippedLog(), "y"));
                b.set(6, 2, 7, p.fence());
                b.set(2, 1, 2, "minecraft:crafting_table");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
            }
            case BUTCHER -> {
                // Exterior brick smoke chimney rising above roof + clean prep counters
                b.set(8, 0, 5, "minecraft:bricks");
                for (int y = 1; y <= 6; y++) {
                    b.set(8, y, 5, "minecraft:bricks");
                }
                b.set(2, 1, 7, "minecraft:smooth_stone");
                b.set(3, 1, 7, "minecraft:smooth_stone_slab[type=bottom]");
                b.set(5, 1, 7, "minecraft:smooth_stone_slab[type=bottom]");
                b.set(6, 1, 7, "minecraft:smooth_stone");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
            }
            case LEATHERWORKER -> {
                // Stone tannery floor, twin drying racks, and hide chests
                b.fill(2, 0, 2, 6, 0, 7, p.baseStone());
                b.set(2, 1, 7, p.fence());
                b.set(2, 2, 7, p.fence());
                b.set(3, 2, 7, p.slab() + "[type=top]");
                b.set(6, 1, 7, p.fence());
                b.set(6, 2, 7, p.fence());
                b.set(5, 2, 7, p.slab() + "[type=top]");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:crafting_table");
            }
            case MASON -> {
                // Stone gallery with polished andesite pillars and stone slab benches
                b.set(2, 1, 7, "minecraft:polished_andesite");
                b.set(2, 2, 7, "minecraft:smooth_stone_slab[type=bottom]");
                b.set(3, 1, 7, "minecraft:stone_bricks");
                b.set(5, 1, 7, "minecraft:stone_bricks");
                b.set(6, 1, 7, "minecraft:polished_andesite");
                b.set(6, 2, 7, "minecraft:smooth_stone_slab[type=bottom]");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:crafting_table");
            }
            case CARTOGRAPHER -> {
                // Panoramic studio windows and archival bookshelves
                b.set(1, 2, 4, p.glassPane());
                b.set(1, 2, 5, p.glassPane());
                b.set(7, 2, 4, p.glassPane());
                b.set(7, 2, 5, p.glassPane());
                b.set(2, 1, 7, "minecraft:bookshelf");
                b.set(2, 2, 7, "minecraft:bookshelf");
                b.set(6, 1, 7, "minecraft:bookshelf");
                b.set(6, 2, 7, "minecraft:bookshelf");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:crafting_table");
            }
            case TOOLSMITH -> {
                // Forge hearth, exterior stone chimney, iron grille windows, and anvil
                b.set(8, 0, 5, p.accentStone());
                for (int y = 1; y <= 6; y++) {
                    b.set(8, y, 5, p.accentStone());
                }
                b.set(2, 1, 7, "minecraft:furnace[facing=east]");
                b.set(6, 1, 7, "minecraft:furnace[facing=west]");
                b.set(2, 1, 2, "minecraft:anvil[facing=east]");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
            }
            case WEAPONSMITH -> {
                // Armory hall with dual iron grilles, twin anvils, and forge hearth
                b.set(1, 2, 4, "minecraft:iron_bars");
                b.set(7, 2, 4, "minecraft:iron_bars");
                b.set(2, 1, 7, "minecraft:anvil[facing=east]");
                b.set(6, 1, 7, "minecraft:anvil[facing=west]");
                b.set(3, 1, 7, "minecraft:furnace[facing=north]");
                b.set(5, 1, 7, "minecraft:furnace[facing=north]");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
            }
            case ARMORER -> {
                // Heavy blast-foundry with tall stone exhaust stack, iron vents, and anvil
                b.set(8, 0, 5, p.baseStone());
                for (int y = 1; y <= 6; y++) {
                    b.set(8, y, 5, p.baseStone());
                }
                b.set(4, 5, 1, "minecraft:iron_bars");
                b.set(2, 1, 7, "minecraft:anvil[facing=east]");
                b.set(6, 1, 7, "minecraft:smooth_stone");
                b.set(3, 1, 7, "minecraft:smooth_stone");
                b.set(5, 1, 7, "minecraft:smooth_stone");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
            }
            case CLERIC -> {
                // Stained-glass apothecary hall with stone altar columns and ridge lantern
                b.set(4, 5, 1, "minecraft:magenta_stained_glass_pane");
                b.set(4, 6, 4, p.accentStone());
                b.set(2, 1, 7, p.accentStone());
                b.set(2, 2, 7, p.lampLight() + "[hanging=false]");
                b.set(6, 1, 7, p.accentStone());
                b.set(6, 2, 7, p.lampLight() + "[hanging=false]");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, "minecraft:chest[facing=west]");
            }
            case LIBRARIAN -> {
                // Grand library lined with two-tier bookshelves
                b.set(2, 1, 7, "minecraft:bookshelf");
                b.set(2, 2, 7, "minecraft:bookshelf");
                b.set(3, 1, 7, "minecraft:bookshelf");
                b.set(3, 2, 7, "minecraft:bookshelf");
                b.set(5, 1, 7, "minecraft:bookshelf");
                b.set(5, 2, 7, "minecraft:bookshelf");
                b.set(6, 1, 7, "minecraft:bookshelf");
                b.set(6, 2, 7, "minecraft:bookshelf");
                b.set(2, 1, 2, "minecraft:bookshelf");
                b.set(2, 2, 2, "minecraft:bookshelf");
                b.set(6, 1, 2, "minecraft:bookshelf");
                b.set(6, 2, 2, "minecraft:bookshelf");
            }
            case GENERIC -> {
                // Versatile biome-styled artisan workshop for modded professions
                b.set(2, 1, 7, "minecraft:crafting_table");
                b.set(6, 1, 7, "minecraft:chest[facing=west]");
                b.set(2, 1, 2, "minecraft:chest[facing=east]");
                b.set(6, 1, 2, p.slab() + "[type=bottom]");
            }
        }
    }

    private static boolean isStoneHeavyArchetype(WorkplaceArchetype arch) {
        return arch == WorkplaceArchetype.MASON
                || arch == WorkplaceArchetype.TOOLSMITH
                || arch == WorkplaceArchetype.WEAPONSMITH
                || arch == WorkplaceArchetype.ARMORER
                || arch == WorkplaceArchetype.CLERIC;
    }

    private static String windowBlockFor(WorkplaceArchetype arch, BiomePalette p) {
        return switch (arch) {
            case TOOLSMITH, WEAPONSMITH, ARMORER -> "minecraft:iron_bars";
            case CLERIC -> "minecraft:purple_stained_glass_pane";
            default -> p.glassPane();
        };
    }
}
