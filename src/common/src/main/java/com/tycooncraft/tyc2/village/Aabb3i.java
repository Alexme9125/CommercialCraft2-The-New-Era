package com.tycooncraft.tyc2.village;

import java.util.Objects;

/**
 * Immutable 3D integer axis-aligned bounding box with inclusive minimum and maximum block coordinates.
 *
 * @param minX minimum X coordinate (inclusive)
 * @param minY minimum Y coordinate (inclusive)
 * @param minZ minimum Z coordinate (inclusive)
 * @param maxX maximum X coordinate (inclusive)
 * @param maxY maximum Y coordinate (inclusive)
 * @param maxZ maximum Z coordinate (inclusive)
 */
public record Aabb3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * Validates that minimum coordinates do not exceed maximum coordinates.
     */
    public Aabb3i {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "Invalid Aabb3i bounds: (" + minX + "," + minY + "," + minZ + ")..("
                            + maxX + "," + maxY + "," + maxZ + ")");
        }
    }

    /**
     * Creates a normalized bounding box spanning any two corner coordinates.
     *
     * @param x1 first X
     * @param y1 first Y
     * @param z1 first Z
     * @param x2 second X
     * @param y2 second Y
     * @param z2 second Z
     * @return normalized bounding box
     */
    public static Aabb3i of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Aabb3i(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * Creates a bounding box from an inclusive minimum origin and strictly positive dimensions.
     *
     * @param origin minimum corner block position
     * @param sizeX extent along X (must be {@code >= 1})
     * @param sizeY extent along Y (must be {@code >= 1})
     * @param sizeZ extent along Z (must be {@code >= 1})
     * @return bounding box covering {@code sizeX * sizeY * sizeZ} blocks
     */
    public static Aabb3i fromOriginAndSize(BlockPos3i origin, int sizeX, int sizeY, int sizeZ) {
        Objects.requireNonNull(origin, "origin");
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException(
                    "Dimensions must be >= 1, got (" + sizeX + "," + sizeY + "," + sizeZ + ")");
        }
        return new Aabb3i(
                origin.x(), origin.y(), origin.z(),
                origin.x() + sizeX - 1, origin.y() + sizeY - 1, origin.z() + sizeZ - 1);
    }

    /**
     * Creates a 1x1x1 bounding box covering a single block position.
     *
     * @param pos target block position
     * @return single-block bounding box
     */
    public static Aabb3i singleBlock(BlockPos3i pos) {
        Objects.requireNonNull(pos, "pos");
        return new Aabb3i(pos.x(), pos.y(), pos.z(), pos.x(), pos.y(), pos.z());
    }

    /**
     * Returns the inclusive block width along X.
     *
     * @return size along X
     */
    public int sizeX() {
        return maxX - minX + 1;
    }

    /**
     * Returns the inclusive block height along Y.
     *
     * @return size along Y
     */
    public int sizeY() {
        return maxY - minY + 1;
    }

    /**
     * Returns the inclusive block depth along Z.
     *
     * @return size along Z
     */
    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /**
     * Returns the horizontal footprint area ({@code sizeX * sizeZ}).
     *
     * @return footprint block count
     */
    public long footprintArea() {
        return (long) sizeX() * sizeZ();
    }

    /**
     * Returns the total 3D block volume ({@code sizeX * sizeY * sizeZ}).
     *
     * @return volume in blocks
     */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /**
     * Returns the minimum corner block position.
     *
     * @return {@code (minX, minY, minZ)}
     */
    public BlockPos3i minCorner() {
        return new BlockPos3i(minX, minY, minZ);
    }

    /**
     * Returns the maximum corner block position.
     *
     * @return {@code (maxX, maxY, maxZ)}
     */
    public BlockPos3i maxCorner() {
        return new BlockPos3i(maxX, maxY, maxZ);
    }

    /**
     * Returns the integer midpoint of this box.
     *
     * @return center block position
     */
    public BlockPos3i center() {
        return new BlockPos3i(
                minX + (maxX - minX) / 2,
                minY + (maxY - minY) / 2,
                minZ + (maxZ - minZ) / 2);
    }

    /**
     * Checks whether the given 3D coordinate lies inside this box (inclusive).
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return {@code true} if contained
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Checks whether the given block position lies inside this box (inclusive).
     *
     * @param pos block position
     * @return {@code true} if contained
     */
    public boolean contains(BlockPos3i pos) {
        Objects.requireNonNull(pos, "pos");
        return contains(pos.x(), pos.y(), pos.z());
    }

    /**
     * Checks whether the given horizontal XZ coordinate lies inside this box's footprint.
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @return {@code true} if contained horizontally
     */
    public boolean containsHorizontal(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Checks whether this box intersects another box in 3D (inclusive of shared faces/edges).
     *
     * @param other other bounding box
     * @return {@code true} if intersecting
     */
    public boolean intersects(Aabb3i other) {
        Objects.requireNonNull(other, "other");
        return this.minX <= other.maxX && this.maxX >= other.minX
                && this.minY <= other.maxY && this.maxY >= other.minY
                && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    /**
     * Checks whether this box overlaps another box horizontally on the XZ plane (ignoring Y).
     *
     * @param other other bounding box
     * @return {@code true} if horizontal footprints intersect
     */
    public boolean intersectsHorizontal(Aabb3i other) {
        Objects.requireNonNull(other, "other");
        return this.minX <= other.maxX && this.maxX >= other.minX
                && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    /**
     * Checks whether this box intersects another box after applying symmetric padding to this box.
     *
     * @param other other bounding box
     * @param horizontalPadding extra clearance on X and Z (must be {@code >= 0})
     * @param verticalPadding extra clearance on Y (must be {@code >= 0})
     * @return {@code true} if padded box intersects {@code other}
     */
    public boolean intersectsWithPadding(Aabb3i other, int horizontalPadding, int verticalPadding) {
        Objects.requireNonNull(other, "other");
        if (horizontalPadding < 0 || verticalPadding < 0) {
            throw new IllegalArgumentException("Padding must be >= 0");
        }
        return (this.minX - horizontalPadding) <= other.maxX
                && (this.maxX + horizontalPadding) >= other.minX
                && (this.minY - verticalPadding) <= other.maxY
                && (this.maxY + verticalPadding) >= other.minY
                && (this.minZ - horizontalPadding) <= other.maxZ
                && (this.maxZ + horizontalPadding) >= other.minZ;
    }

    /**
     * Returns a new box expanded symmetrically along each axis by the specified amounts.
     *
     * @param dx expansion applied to both {@code minX} and {@code maxX}
     * @param dy expansion applied to both {@code minY} and {@code maxY}
     * @param dz expansion applied to both {@code minZ} and {@code maxZ}
     * @return expanded bounding box
     */
    public Aabb3i expand(int dx, int dy, int dz) {
        return new Aabb3i(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /**
     * Returns a new box padded uniformly on all 3 axes.
     *
     * @param padding padding distance on X, Y, and Z (must be {@code >= 0})
     * @return padded bounding box
     */
    public Aabb3i pad(int padding) {
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be >= 0");
        }
        return expand(padding, padding, padding);
    }

    /**
     * Returns a new box padded horizontally on X and Z while preserving Y bounds.
     *
     * @param horizontalPadding padding distance on X and Z (must be {@code >= 0})
     * @return horizontally padded bounding box
     */
    public Aabb3i padHorizontal(int horizontalPadding) {
        if (horizontalPadding < 0) {
            throw new IllegalArgumentException("horizontalPadding must be >= 0");
        }
        return expand(horizontalPadding, 0, horizontalPadding);
    }

    /**
     * Returns a translated copy of this box.
     *
     * @param dx delta X
     * @param dy delta Y
     * @param dz delta Z
     * @return translated bounding box
     */
    public Aabb3i translate(int dx, int dy, int dz) {
        return new Aabb3i(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /**
     * Returns the smallest bounding box enclosing both this box and {@code other}.
     *
     * @param other other bounding box
     * @return union bounding box
     */
    public Aabb3i union(Aabb3i other) {
        Objects.requireNonNull(other, "other");
        return new Aabb3i(
                Math.min(this.minX, other.minX),
                Math.min(this.minY, other.minY),
                Math.min(this.minZ, other.minZ),
                Math.max(this.maxX, other.maxX),
                Math.max(this.maxY, other.maxY),
                Math.max(this.maxZ, other.maxZ));
    }
}
