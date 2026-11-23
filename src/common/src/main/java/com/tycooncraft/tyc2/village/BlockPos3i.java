package com.tycooncraft.tyc2.village;

import java.util.Objects;

/**
 * Immutable 3D integer block coordinate used across the pure-Java village engine.
 *
 * @param x X coordinate
 * @param y Y coordinate
 * @param z Z coordinate
 */
public record BlockPos3i(int x, int y, int z) {

    /**
     * Origin coordinate {@code (0, 0, 0)}.
     */
    public static final BlockPos3i ZERO = new BlockPos3i(0, 0, 0);

    /**
     * Returns a new position offset by the given deltas.
     *
     * @param dx delta along X
     * @param dy delta along Y
     * @param dz delta along Z
     * @return translated block position
     */
    public BlockPos3i offset(int dx, int dy, int dz) {
        return new BlockPos3i(x + dx, y + dy, z + dz);
    }

    /**
     * Returns a new position stepped horizontally along the given facing.
     *
     * @param facing horizontal direction
     * @param distance number of blocks to move
     * @return translated block position
     */
    public BlockPos3i offset(Facing2D facing, int distance) {
        Objects.requireNonNull(facing, "facing");
        return new BlockPos3i(x + facing.stepX() * distance, y, z + facing.stepZ() * distance);
    }

    /**
     * Returns the position directly above this block ({@code y + 1}).
     *
     * @return position one block above
     */
    public BlockPos3i above() {
        return new BlockPos3i(x, y + 1, z);
    }

    /**
     * Returns the position {@code n} blocks above this block.
     *
     * @param n vertical offset
     * @return position {@code n} blocks above
     */
    public BlockPos3i above(int n) {
        return new BlockPos3i(x, y + n, z);
    }

    /**
     * Returns the position directly below this block ({@code y - 1}).
     *
     * @return position one block below
     */
    public BlockPos3i below() {
        return new BlockPos3i(x, y - 1, z);
    }

    /**
     * Returns the position {@code n} blocks below this block.
     *
     * @param n vertical offset downward
     * @return position {@code n} blocks below
     */
    public BlockPos3i below(int n) {
        return new BlockPos3i(x, y - n, z);
    }

    /**
     * Computes the squared horizontal (XZ) Euclidean distance to another position.
     *
     * @param other target position
     * @return squared horizontal distance
     */
    public long horizontalDistanceSq(BlockPos3i other) {
        Objects.requireNonNull(other, "other");
        long dx = (long) this.x - other.x;
        long dz = (long) this.z - other.z;
        return dx * dx + dz * dz;
    }

    /**
     * Computes the horizontal (XZ) Euclidean distance to another position.
     *
     * @param other target position
     * @return horizontal distance
     */
    public double horizontalDistance(BlockPos3i other) {
        return Math.sqrt(horizontalDistanceSq(other));
    }

    /**
     * Computes the squared 3D Euclidean distance to another position.
     *
     * @param other target position
     * @return squared 3D distance
     */
    public long distanceSq(BlockPos3i other) {
        Objects.requireNonNull(other, "other");
        long dx = (long) this.x - other.x;
        long dy = (long) this.y - other.y;
        long dz = (long) this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Computes the 3D Manhattan distance to another position.
     *
     * @param other target position
     * @return Manhattan distance
     */
    public int manhattanDistance(BlockPos3i other) {
        Objects.requireNonNull(other, "other");
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y) + Math.abs(this.z - other.z);
    }
}
