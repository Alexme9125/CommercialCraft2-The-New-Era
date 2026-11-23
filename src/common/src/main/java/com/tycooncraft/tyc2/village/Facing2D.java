package com.tycooncraft.tyc2.village;

import java.util.Locale;
import java.util.Objects;

/**
 * Cardinal horizontal orientation with clockwise rotation math for 3D structure placement
 * and directional block-state strings.
 */
public enum Facing2D {
    /** Negative Z direction (canonical 0-degree blueprint orientation). */
    NORTH(0, -1, 0, "north"),
    /** Positive X direction (90 degrees clockwise from NORTH). */
    EAST(1, 0, 1, "east"),
    /** Positive Z direction (180 degrees clockwise from NORTH). */
    SOUTH(0, 1, 2, "south"),
    /** Negative X direction (270 degrees clockwise from NORTH). */
    WEST(-1, 0, 3, "west");

    private static final Facing2D[] BY_STEPS = {NORTH, EAST, SOUTH, WEST};

    private final int stepX;
    private final int stepZ;
    private final int clockwiseSteps;
    private final String serializedName;

    Facing2D(int stepX, int stepZ, int clockwiseSteps, String serializedName) {
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.clockwiseSteps = clockwiseSteps;
        this.serializedName = serializedName;
    }

    /**
     * Returns the X unit vector component ({@code -1, 0, 1}).
     *
     * @return unit X step
     */
    public int stepX() {
        return stepX;
    }

    /**
     * Returns the Z unit vector component ({@code -1, 0, 1}).
     *
     * @return unit Z step
     */
    public int stepZ() {
        return stepZ;
    }

    /**
     * Returns the number of 90-degree clockwise rotations from {@link #NORTH} ({@code 0..3}).
     *
     * @return clockwise quarter-turn count
     */
    public int clockwiseSteps() {
        return clockwiseSteps;
    }

    /**
     * Returns the lowercase Minecraft directional property name.
     *
     * @return {@code "north"}, {@code "east"}, {@code "south"}, or {@code "west"}
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Returns the facing 90 degrees clockwise from this facing.
     *
     * @return clockwise facing
     */
    public Facing2D clockwise() {
        return BY_STEPS[(clockwiseSteps + 1) & 3];
    }

    /**
     * Returns the facing 90 degrees counter-clockwise from this facing.
     *
     * @return counter-clockwise facing
     */
    public Facing2D counterClockwise() {
        return BY_STEPS[(clockwiseSteps + 3) & 3];
    }

    /**
     * Returns the opposite horizontal facing (180 degrees).
     *
     * @return opposite facing
     */
    public Facing2D opposite() {
        return BY_STEPS[(clockwiseSteps + 2) & 3];
    }

    /**
     * Rotates another facing clockwise by this facing's rotation relative to {@link #NORTH}.
     *
     * @param base original facing in canonical NORTH-oriented blueprint space
     * @return rotated facing
     */
    public Facing2D rotate(Facing2D base) {
        Objects.requireNonNull(base, "base");
        return BY_STEPS[(base.clockwiseSteps + this.clockwiseSteps) & 3];
    }

    /**
     * Returns whether this facing swaps local X and Z extents relative to {@link #NORTH}.
     *
     * @return {@code true} for {@link #EAST} and {@link #WEST}
     */
    public boolean swapsHorizontalAxes() {
        return (clockwiseSteps & 1) == 1;
    }

    /**
     * Returns the rotated bounding box X extent for a blueprint of size {@code (width, depth)}.
     *
     * @param width canonical X width
     * @param depth canonical Z depth
     * @return rotated X extent
     */
    public int rotatedWidth(int width, int depth) {
        return swapsHorizontalAxes() ? depth : width;
    }

    /**
     * Returns the rotated bounding box Z extent for a blueprint of size {@code (width, depth)}.
     *
     * @param width canonical X width
     * @param depth canonical Z depth
     * @return rotated Z extent
     */
    public int rotatedDepth(int width, int depth) {
        return swapsHorizontalAxes() ? width : depth;
    }

    /**
     * Rotates a relative vector around the origin {@code (0, 0, 0)} clockwise by {@link #clockwiseSteps()}.
     *
     * @param dx local X offset
     * @param dy local Y offset
     * @param dz local Z offset
     * @return rotated relative offset
     */
    public BlockPos3i rotateOffset(int dx, int dy, int dz) {
        return switch (this) {
            case NORTH -> new BlockPos3i(dx, dy, dz);
            case EAST -> new BlockPos3i(-dz, dy, dx);
            case SOUTH -> new BlockPos3i(-dx, dy, -dz);
            case WEST -> new BlockPos3i(dz, dy, -dx);
        };
    }

    /**
     * Rotates a local coordinate inside a {@code [0, width-1] x [0, depth-1]} blueprint box so
     * that all rotated coordinates stay normalized inside {@code [0, rotatedWidth-1] x [0, rotatedDepth-1]}.
     *
     * @param dx local X in {@code [0, width-1]}
     * @param dy local Y
     * @param dz local Z in {@code [0, depth-1]}
     * @param width canonical box width along X
     * @param depth canonical box depth along Z
     * @return normalized rotated local coordinate
     */
    public BlockPos3i rotateInBox(int dx, int dy, int dz, int width, int depth) {
        return switch (this) {
            case NORTH -> new BlockPos3i(dx, dy, dz);
            case EAST -> new BlockPos3i(depth - 1 - dz, dy, dx);
            case SOUTH -> new BlockPos3i(width - 1 - dx, dy, depth - 1 - dz);
            case WEST -> new BlockPos3i(dz, dy, width - 1 - dx);
        };
    }

    /**
     * Rotates directional block-state properties (such as {@code facing=north}, {@code axis=x},
     * or cardinal connection keys {@code north=true}) inside a Minecraft block-state spec string.
     *
     * @param blockStateSpec block ID optionally followed by {@code [prop=val,...]}
     * @return rotated block-state spec string
     */
    public String rotateBlockStateSpec(String blockStateSpec) {
        Objects.requireNonNull(blockStateSpec, "blockStateSpec");
        if (this == NORTH) {
            return blockStateSpec;
        }
        int bracket = blockStateSpec.indexOf('[');
        if (bracket < 0 || !blockStateSpec.endsWith("]")) {
            return blockStateSpec;
        }
        String blockId = blockStateSpec.substring(0, bracket);
        String propsBody = blockStateSpec.substring(bracket + 1, blockStateSpec.length() - 1);
        if (propsBody.isEmpty()) {
            return blockStateSpec;
        }
        String[] parts = propsBody.split(",");
        StringBuilder out = new StringBuilder(blockStateSpec.length() + 8);
        out.append(blockId).append('[');
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            String part = parts[i];
            int eq = part.indexOf('=');
            if (eq <= 0) {
                out.append(part);
                continue;
            }
            String key = part.substring(0, eq).trim();
            String val = part.substring(eq + 1).trim();
            if ("facing".equals(key)) {
                Facing2D parsed = parseOrNull(val);
                out.append("facing=").append(parsed != null ? rotate(parsed).serializedName() : val);
            } else if ("axis".equals(key) && swapsHorizontalAxes()) {
                if ("x".equals(val)) {
                    out.append("axis=z");
                } else if ("z".equals(val)) {
                    out.append("axis=x");
                } else {
                    out.append(part);
                }
            } else {
                Facing2D keyFacing = parseOrNull(key);
                if (keyFacing != null) {
                    out.append(rotate(keyFacing).serializedName()).append('=').append(val);
                } else {
                    out.append(key).append('=').append(val);
                }
            }
        }
        out.append(']');
        return out.toString();
    }

    /**
     * Picks the cardinal facing that best points along the given horizontal delta vector.
     *
     * @param dx horizontal delta X
     * @param dz horizontal delta Z
     * @return closest cardinal facing (defaults to {@link #NORTH} when {@code dx == 0 && dz == 0})
     */
    public static Facing2D fromDelta(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return NORTH;
        }
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? EAST : WEST;
        }
        return dz > 0 ? SOUTH : NORTH;
    }

    /**
     * Parses a facing by name (case-insensitive), falling back to {@code defaultFacing} if unrecognized.
     *
     * @param name facing string (may be null)
     * @param defaultFacing fallback value
     * @return parsed facing or fallback
     */
    public static Facing2D fromNameOrDefault(String name, Facing2D defaultFacing) {
        Facing2D parsed = parseOrNull(name);
        return parsed != null ? parsed : Objects.requireNonNull(defaultFacing, "defaultFacing");
    }

    private static Facing2D parseOrNull(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> NORTH;
            case "east" -> EAST;
            case "south" -> SOUTH;
            case "west" -> WEST;
            default -> null;
        };
    }
}
