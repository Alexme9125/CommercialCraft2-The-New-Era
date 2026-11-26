package com.tycooncraft.tyc2.village;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * Single world-space block placement executed by a {@link ConstructionJob}.
 *
 * @param pos absolute world block position
 * @param blockStateSpec Minecraft block ID with optional bracketed properties
 */
public record WorldBlockPlacement(BlockPos3i pos, String blockStateSpec) {

    /**
     * Bottom-up canonical ordering: Y ascending, then structural solid blocks before fragile,
     * wall-attached, two-part, or fluid blocks on the same Y layer, then Z ascending, then X ascending.
     */
    public static final Comparator<WorldBlockPlacement> BOTTOM_UP_ORDER = Comparator
            .comparingInt((WorldBlockPlacement p) -> p.pos().y())
            .thenComparingInt(p -> isFragileOrAttached(p.blockStateSpec()) ? 1 : 0)
            .thenComparingInt(p -> p.pos().z())
            .thenComparingInt(p -> p.pos().x());

    /**
     * Checks whether {@code blockStateSpec} represents a fragile, wall/ceiling-attached, two-part,
     * crop, or fluid block that should be placed after structural walls/floors/rims on the same Y
     * layer and placed without destructive neighbor update cascades during gradual construction.
     *
     * @param blockStateSpec Minecraft block ID with optional bracketed properties
     * @return {@code true} if fragile, attached, two-part, crop, or fluid
     */
    public static boolean isFragileOrAttached(String blockStateSpec) {
        if (blockStateSpec == null || blockStateSpec.isBlank()) {
            return false;
        }
        int bracket = blockStateSpec.indexOf('[');
        String id = (bracket >= 0 ? blockStateSpec.substring(0, bracket) : blockStateSpec)
                .trim()
                .toLowerCase(Locale.ROOT);
        return id.endsWith("_door")
                || id.endsWith("_bed")
                || id.contains("torch")
                || id.contains("lantern")
                || id.equals("minecraft:water")
                || id.equals("minecraft:wheat")
                || id.equals("minecraft:carrots")
                || id.equals("minecraft:potatoes")
                || id.equals("minecraft:beetroots")
                || id.endsWith("_carpet")
                || id.endsWith("_sign")
                || id.endsWith("_pressure_plate")
                || id.endsWith("_button")
                || id.endsWith("_banner")
                || id.contains("ladder")
                || id.contains("rail")
                || id.contains("lever")
                || id.contains("redstone_wire")
                || id.contains("flower_pot")
                || id.contains("vine")
                || id.endsWith("_sapling")
                || id.contains("bush");
    }

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
