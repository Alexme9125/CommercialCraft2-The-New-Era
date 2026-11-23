package com.tycooncraft.tyc2.village;

import java.util.Comparator;
import java.util.Objects;

/**
 * Single relative block placement inside a {@link StructureBlueprint}.
 *
 * @param dx local X offset from structure origin
 * @param dy local Y offset from structure origin
 * @param dz local Z offset from structure origin
 * @param blockStateSpec Minecraft block ID with optional bracketed properties (e.g. {@code "minecraft:oak_stairs[facing=east,half=bottom]"})
 */
public record BlockPlacement(int dx, int dy, int dz, String blockStateSpec) {

    /** Bottom-up canonical ordering: Y ascending, then Z ascending, then X ascending. */
    public static final Comparator<BlockPlacement> BOTTOM_UP_ORDER = Comparator
            .comparingInt(BlockPlacement::dy)
            .thenComparingInt(BlockPlacement::dz)
            .thenComparingInt(BlockPlacement::dx);

    /**
     * Validates non-blank block-state specification.
     */
    public BlockPlacement {
        if (blockStateSpec == null || blockStateSpec.isBlank()) {
            throw new IllegalArgumentException("blockStateSpec must not be null or blank");
        }
    }

    /**
     * Translates this relative placement into an absolute world-space placement.
     *
     * @param origin world-space origin coordinate
     * @return world block placement
     */
    public WorldBlockPlacement toWorld(BlockPos3i origin) {
        Objects.requireNonNull(origin, "origin");
        return new WorldBlockPlacement(origin.offset(dx, dy, dz), blockStateSpec);
    }
}
