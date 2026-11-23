package com.tycooncraft.tyc2.village;

import java.util.Comparator;
import java.util.Objects;

/**
 * Single world-space block placement executed by a {@link ConstructionJob}.
 *
 * @param pos absolute world block position
 * @param blockStateSpec Minecraft block ID with optional bracketed properties
 */
public record WorldBlockPlacement(BlockPos3i pos, String blockStateSpec) {

    /** Bottom-up canonical ordering: Y ascending, then Z ascending, then X ascending. */
    public static final Comparator<WorldBlockPlacement> BOTTOM_UP_ORDER = Comparator
            .comparingInt((WorldBlockPlacement p) -> p.pos().y())
            .thenComparingInt(p -> p.pos().z())
            .thenComparingInt(p -> p.pos().x());

    /**
     * Validates position and block-state spec.
     */
    public WorldBlockPlacement {
        Objects.requireNonNull(pos, "pos");
        if (blockStateSpec == null || blockStateSpec.isBlank()) {
            throw new IllegalArgumentException("blockStateSpec must not be null or blank");
        }
    }
}
