package com.tycooncraft.tyc2.world;

import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.village.TerrainProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Live {@link ServerLevel} implementation of {@link TerrainProbe} enforcing all
 * TycoonCraft² anti-grief and collision-safety guarantees:
 * <ul>
 *   <li>NEVER overwrites any player-placed block ({@link TycoonSavedData#isPlayerPlaced})</li>
 *   <li>NEVER overwrites any {@link net.minecraft.world.level.block.entity.BlockEntity}
 *       (chests, machines, IC2/LC2 stations, pipes, cables, furnaces)</li>
 *   <li>NEVER overwrites existing village architecture blocks (planks, cobblestone, stone bricks,
 *       doors, beds, fences, paths, glass, terracotta, bells, hubs, workstations)</li>
 *   <li>Only clears whitelisted natural surface clutter (air, grass, ferns, flowers, snow layers,
 *       dead bush, leaves, natural logs)</li>
 * </ul>
 */
public final class LevelTerrainProbe implements TerrainProbe {

    private final ServerLevel level;
    private final TycoonSavedData savedData;
    private final String dimensionId;
    private final int minY;
    private final int maxY;

    public LevelTerrainProbe(ServerLevel level, TycoonSavedData savedData) {
        this.level = level;
        this.savedData = savedData;
        this.dimensionId = TyC2Native.dimensionId(level);
        this.minY = TyC2Native.minBuildY(level);
        this.maxY = TyC2Native.maxBuildY(level);
    }

    @Override
    public int getSurfaceY(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return minY;
        }
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(topY + 2, maxY - 1), z);
        while (pos.getY() > minY) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !isSurfaceClutter(state, pos)) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return minY;
    }

    @Override
    public boolean isNaturalReplaceable(int x, int y, int z) {
        if (y < minY || y >= maxY || !level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        if (isProtectedOrOccupied(x, y, z)) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return isSurfaceClutter(state, pos);
    }

    @Override
    public boolean isSolidFoundation(int x, int y, int z) {
        if (y < minY || y >= maxY || !level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (isProtectedOrOccupied(x, y, z) || isSurfaceClutter(state, pos)) {
            return false;
        }
        return state.isSolid() && state.blocksMotion();
    }

    @Override
    public boolean isProtectedOrOccupied(int x, int y, int z) {
        if (y < minY || y >= maxY || !level.hasChunk(x >> 4, z >> 4)) {
            return true;
        }
        if (savedData.isPlayerPlaced(dimensionId, x, y, z)) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) != null) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return true;
        }
        String id = TyC2Native.blockId(state);
        if (id.startsWith("tyc2:") || id.startsWith("ic2:") || id.startsWith("lc2:")) {
            return true;
        }
        return isExistingStructureBlock(state, id);
    }

    @Override
    public boolean isWaterSurface(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        int sy = getSurfaceY(x, z);
        BlockPos pos = new BlockPos(x, sy, z);
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    public boolean isSkyClear(int x, int fromY, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        return level.canSeeSky(new BlockPos(x, fromY, z));
    }

    private boolean isSurfaceClutter(BlockState state, BlockPos pos) {
        if (!state.getFluidState().isEmpty()
                || isProtectedOrOccupied(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        if (state.canBeReplaced()) {
            return true;
        }
        if (state.is(Blocks.SNOW)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return true;
        }
        String id = TyC2Native.blockId(state);
        if (id.equals("minecraft:short_grass") || id.equals("minecraft:grass")
                || id.contains("leaf_litter") || id.contains("pink_petals")
                || id.contains("wildflowers") || id.contains("dry_grass")
                || id.contains("moss_carpet") || id.contains("powder_snow")
                || id.contains("sweet_berry_bush")
                || id.contains("bush") || id.contains("flower") || id.contains("tulip")
                || id.contains("daisy") || id.contains("orchid") || id.contains("allium")
                || id.contains("bluet") || id.contains("cornflower") || id.contains("lily")) {
            return true;
        }
        if (TyC2Native.blockHasTag(state, "minecraft:leaves")
                || TyC2Native.blockHasTag(state, "minecraft:flowers")
                || TyC2Native.blockHasTag(state, "minecraft:saplings")) {
            return true;
        }
        // Natural (non-player-placed) tree logs may be cleared cleanly
        if (TyC2Native.blockHasTag(state, "minecraft:logs")) {
            return true;
        }
        return false;
    }

    private static boolean isExistingStructureBlock(BlockState state, String id) {
        if (TyC2Native.blockHasTag(state, "minecraft:planks")
                || TyC2Native.blockHasTag(state, "minecraft:doors")
                || TyC2Native.blockHasTag(state, "minecraft:beds")
                || TyC2Native.blockHasTag(state, "minecraft:fences")
                || TyC2Native.blockHasTag(state, "minecraft:fence_gates")
                || TyC2Native.blockHasTag(state, "minecraft:stairs")
                || TyC2Native.blockHasTag(state, "minecraft:slabs")
                || TyC2Native.blockHasTag(state, "minecraft:walls")
                || TyC2Native.blockHasTag(state, "minecraft:wool")
                || TyC2Native.blockHasTag(state, "minecraft:terracotta")
                || TyC2Native.blockHasTag(state, "minecraft:rails")) {
            return true;
        }
        return id.contains("cobblestone")
                || id.contains("stone_brick")
                || id.contains("smooth_sandstone")
                || id.contains("cut_sandstone")
                || id.contains("glass")
                || id.contains("dirt_path")
                || id.contains("bell")
                || id.contains("lantern")
                || id.contains("torch")
                || id.contains("farmland")
                || id.contains("hay_block")
                || id.contains("composter")
                || id.contains("crafting_table")
                || id.contains("lectern")
                || id.contains("smithing_table")
                || id.contains("cartography_table")
                || id.contains("fletching_table")
                || id.contains("loom")
                || id.contains("stonecutter")
                || id.contains("grindstone")
                || id.contains("barrel")
                || id.contains("blast_furnace")
                || id.contains("smoker")
                || id.contains("brewing_stand")
                || id.contains("cauldron")
                || id.contains("water_cauldron")
                || id.contains("bookshelf")
                || id.contains("iron_bars")
                || id.contains("bricks");
    }
}
