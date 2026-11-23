package com.tycooncraft.tyc2.village;

import java.util.Objects;

/**
 * Complete architectural block palette for a single {@link BiomeStyle}.
 * All block IDs are cross-version vanilla identifiers valid across Forge 1.19.2 and NeoForge 1.21.1/26.x.
 *
 * @param log structural log or corner column block ID
 * @param planks primary plank or dressed wall block ID
 * @param strippedLog stripped log or decorative trim pillar block ID
 * @param slab standard slab block ID
 * @param stairs standard staircase block ID
 * @param baseStone foundation and plinth stone block ID
 * @param accentStone accent stone or terracotta block ID
 * @param wallBlock primary exterior wall infill block ID
 * @param roofStairs pitched roof slope stair block ID
 * @param roofSlab roof ridge or overhang slab block ID
 * @param door wooden door block ID
 * @param glassPane window glass pane block ID
 * @param fence fence railing block ID
 * @param fenceGate fence gate block ID
 * @param roadPrimary primary paved road surface block ID
 * @param roadBorder road curb and border block ID
 * @param lampPost streetlamp upright post block ID
 * @param lampLight streetlamp light fixture block ID
 * @param bed villager bed block ID
 * @param cropBlock farm crop block ID
 * @param farmland tilled farmland block ID
 * @param floorBlock interior floor block ID
 * @param waterPierLog waterfront pier piling and edge beam block ID
 * @param helipadBlock open-sky helipad apron block ID
 */
public record BiomePalette(
        String log,
        String planks,
        String strippedLog,
        String slab,
        String stairs,
        String baseStone,
        String accentStone,
        String wallBlock,
        String roofStairs,
        String roofSlab,
        String door,
        String glassPane,
        String fence,
        String fenceGate,
        String roadPrimary,
        String roadBorder,
        String lampPost,
        String lampLight,
        String bed,
        String cropBlock,
        String farmland,
        String floorBlock,
        String waterPierLog,
        String helipadBlock) {

    /**
     * Validates that every palette block ID is non-null and non-blank.
     */
    public BiomePalette {
        requireBlockId(log, "log");
        requireBlockId(planks, "planks");
        requireBlockId(strippedLog, "strippedLog");
        requireBlockId(slab, "slab");
        requireBlockId(stairs, "stairs");
        requireBlockId(baseStone, "baseStone");
        requireBlockId(accentStone, "accentStone");
        requireBlockId(wallBlock, "wallBlock");
        requireBlockId(roofStairs, "roofStairs");
        requireBlockId(roofSlab, "roofSlab");
        requireBlockId(door, "door");
        requireBlockId(glassPane, "glassPane");
        requireBlockId(fence, "fence");
        requireBlockId(fenceGate, "fenceGate");
        requireBlockId(roadPrimary, "roadPrimary");
        requireBlockId(roadBorder, "roadBorder");
        requireBlockId(lampPost, "lampPost");
        requireBlockId(lampLight, "lampLight");
        requireBlockId(bed, "bed");
        requireBlockId(cropBlock, "cropBlock");
        requireBlockId(farmland, "farmland");
        requireBlockId(floorBlock, "floorBlock");
        requireBlockId(waterPierLog, "waterPierLog");
        requireBlockId(helipadBlock, "helipadBlock");
    }

    /**
     * Checks whether a block ID supports the Minecraft {@code axis} block-state property.
     *
     * @param blockId namespaced block ID
     * @return {@code true} if the block is a rotated pillar (log, wood, stem, hay, etc.)
     */
    public static boolean supportsAxisProperty(String blockId) {
        Objects.requireNonNull(blockId, "blockId");
        return blockId.endsWith("_log")
                || blockId.endsWith("_wood")
                || blockId.endsWith("_stem")
                || blockId.endsWith("_pillar")
                || blockId.equals("minecraft:hay_block");
    }

    /**
     * Formats a block ID with an {@code [axis=<axis>]} property if supported, or returns the plain block ID otherwise.
     *
     * @param blockId namespaced block ID
     * @param axis axis value ({@code "x"}, {@code "y"}, or {@code "z"})
     * @return valid block-state spec
     */
    public static String withAxisIfSupported(String blockId, String axis) {
        Objects.requireNonNull(axis, "axis");
        return supportsAxisProperty(blockId) ? blockId + "[axis=" + axis + "]" : blockId;
    }

    /**
     * Returns the built-in canonical {@link BiomePalette} for the given {@link BiomeStyle}.
     *
     * @param style target biome architectural style
     * @return biome palette
     */
    public static BiomePalette forStyle(BiomeStyle style) {
        Objects.requireNonNull(style, "style");
        return switch (style) {
            case PLAINS -> PLAINS_PALETTE;
            case DESERT -> DESERT_PALETTE;
            case SAVANNA -> SAVANNA_PALETTE;
            case TAIGA -> TAIGA_PALETTE;
            case SNOWY -> SNOWY_PALETTE;
        };
    }

    private static final BiomePalette PLAINS_PALETTE = new BiomePalette(
            "minecraft:oak_log",
            "minecraft:oak_planks",
            "minecraft:stripped_oak_log",
            "minecraft:oak_slab",
            "minecraft:oak_stairs",
            "minecraft:cobblestone",
            "minecraft:stone_bricks",
            "minecraft:oak_planks",
            "minecraft:oak_stairs",
            "minecraft:oak_slab",
            "minecraft:oak_door",
            "minecraft:glass_pane",
            "minecraft:oak_fence",
            "minecraft:oak_fence_gate",
            "minecraft:dirt_path",
            "minecraft:stone_bricks",
            "minecraft:oak_fence",
            "minecraft:lantern",
            "minecraft:white_bed",
            "minecraft:wheat",
            "minecraft:farmland",
            "minecraft:oak_planks",
            "minecraft:oak_log",
            "minecraft:smooth_stone");

    private static final BiomePalette DESERT_PALETTE = new BiomePalette(
            "minecraft:cut_sandstone",
            "minecraft:smooth_sandstone",
            "minecraft:chiseled_sandstone",
            "minecraft:smooth_sandstone_slab",
            "minecraft:smooth_sandstone_stairs",
            "minecraft:sandstone",
            "minecraft:orange_terracotta",
            "minecraft:smooth_sandstone",
            "minecraft:sandstone_stairs",
            "minecraft:cut_sandstone_slab",
            "minecraft:birch_door",
            "minecraft:orange_stained_glass_pane",
            "minecraft:birch_fence",
            "minecraft:birch_fence_gate",
            "minecraft:smooth_sandstone",
            "minecraft:cut_sandstone",
            "minecraft:sandstone_wall",
            "minecraft:lantern",
            "minecraft:orange_bed",
            "minecraft:wheat",
            "minecraft:farmland",
            "minecraft:cut_sandstone",
            "minecraft:birch_log",
            "minecraft:smooth_sandstone");

    private static final BiomePalette SAVANNA_PALETTE = new BiomePalette(
            "minecraft:acacia_log",
            "minecraft:acacia_planks",
            "minecraft:stripped_acacia_log",
            "minecraft:acacia_slab",
            "minecraft:acacia_stairs",
            "minecraft:cobblestone",
            "minecraft:red_terracotta",
            "minecraft:acacia_planks",
            "minecraft:acacia_stairs",
            "minecraft:acacia_slab",
            "minecraft:acacia_door",
            "minecraft:glass_pane",
            "minecraft:acacia_fence",
            "minecraft:acacia_fence_gate",
            "minecraft:dirt_path",
            "minecraft:stripped_acacia_log",
            "minecraft:acacia_fence",
            "minecraft:lantern",
            "minecraft:red_bed",
            "minecraft:carrots",
            "minecraft:farmland",
            "minecraft:acacia_planks",
            "minecraft:acacia_log",
            "minecraft:polished_andesite");

    private static final BiomePalette TAIGA_PALETTE = new BiomePalette(
            "minecraft:spruce_log",
            "minecraft:spruce_planks",
            "minecraft:stripped_spruce_log",
            "minecraft:spruce_slab",
            "minecraft:spruce_stairs",
            "minecraft:mossy_cobblestone",
            "minecraft:cobblestone",
            "minecraft:spruce_planks",
            "minecraft:spruce_stairs",
            "minecraft:spruce_slab",
            "minecraft:spruce_door",
            "minecraft:glass_pane",
            "minecraft:spruce_fence",
            "minecraft:spruce_fence_gate",
            "minecraft:coarse_dirt",
            "minecraft:cobblestone",
            "minecraft:spruce_fence",
            "minecraft:lantern",
            "minecraft:green_bed",
            "minecraft:potatoes",
            "minecraft:farmland",
            "minecraft:spruce_planks",
            "minecraft:spruce_log",
            "minecraft:stone_bricks");

    private static final BiomePalette SNOWY_PALETTE = new BiomePalette(
            "minecraft:spruce_log",
            "minecraft:spruce_planks",
            "minecraft:stripped_spruce_log",
            "minecraft:spruce_slab",
            "minecraft:spruce_stairs",
            "minecraft:stone_bricks",
            "minecraft:light_blue_terracotta",
            "minecraft:snow_block",
            "minecraft:spruce_stairs",
            "minecraft:spruce_slab",
            "minecraft:spruce_door",
            "minecraft:light_blue_stained_glass_pane",
            "minecraft:spruce_fence",
            "minecraft:spruce_fence_gate",
            "minecraft:cobblestone",
            "minecraft:packed_ice",
            "minecraft:spruce_fence",
            "minecraft:lantern",
            "minecraft:blue_bed",
            "minecraft:beetroots",
            "minecraft:farmland",
            "minecraft:spruce_planks",
            "minecraft:spruce_log",
            "minecraft:stone_bricks");

    private static void requireBlockId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BiomePalette field '" + field + "' must not be null or blank");
        }
    }
}
