package com.tycooncraft.tyc2.village;

/**
 * Pure-Java world terrain query callback implemented by platform adapters (NeoForge/Forge)
 * using {@code ServerLevel}, enabling deterministic collision-safe expansion planning in common code.
 */
public interface TerrainProbe {

    /**
     * Returns the Y coordinate of the highest load-bearing surface block at {@code (x, z)}
     * (or the water surface Y when {@link #isWaterSurface(int, int)} is {@code true}).
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return surface block Y coordinate
     */
    int getSurfaceY(int x, int z);

    /**
     * Checks whether the block at {@code (x, y, z)} is whitelisted natural surface clutter
     * (air, grass, tall grass, ferns, flowers, snow layers, dead bushes, leaves, or natural logs)
     * that expansion construction is permitted to clear.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return {@code true} if safely replaceable
     */
    boolean isNaturalReplaceable(int x, int y, int z);

    /**
     * Checks whether the block at {@code (x, y, z)} is solid natural ground suitable to bear
     * a structure foundation or support pillar (not air, fluid, leaves, or fragile clutter).
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return {@code true} if solid load-bearing terrain
     */
    boolean isSolidFoundation(int x, int y, int z);

    /**
     * Checks whether the block at {@code (x, y, z)} must NEVER be overwritten or built over:
     * any BlockEntity (chests, furnaces, machines, pipes, IC2/LC2 blocks), any player-placed
     * block, any existing village building/road block, or fluids.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return {@code true} if protected or occupied
     */
    boolean isProtectedOrOccupied(int x, int y, int z);

    /**
     * Checks whether the top surface at {@code (x, z)} is water (river, lake, or ocean).
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return {@code true} if surface at {@code (x, z)} is water
     */
    boolean isWaterSurface(int x, int z);

    /**
     * Checks whether the sky above {@code (x, fromY, z)} is clear and unobstructed for
     * open-sky LC2 logistics pads (helipads and piers). Platform adapters may override this
     * with {@code ServerLevel.canSeeSky} for O(1) heightmap checks.
     *
     * @param x world X coordinate
     * @param fromY starting Y coordinate above deck level
     * @param z world Z coordinate
     * @return {@code true} if sky corridor is unobstructed
     */
    default boolean isSkyClear(int x, int fromY, int z) {
        for (int dy = 0; dy < 16; dy++) {
            int y = fromY + dy;
            if (isProtectedOrOccupied(x, y, z) || !isNaturalReplaceable(x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
