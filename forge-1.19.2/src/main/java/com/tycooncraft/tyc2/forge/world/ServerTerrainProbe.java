package com.tycooncraft.tyc2.forge.world;

import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.TerrainProbe;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Material;

/**
 * Forge 1.19.2 {@link ServerLevel}-backed implementation of {@link TerrainProbe} enforcing
 * strict anti-grief protection against overwriting any {@code BlockEntity}, player-placed
 * block, existing village structure block, or fluid, while whitelisting only natural surface
 * clutter for expansion clearance.
 */
public final class ServerTerrainProbe implements TerrainProbe {

    private static final Set<String> PROTECTED_VILLAGE_BLOCK_SUBSTRINGS = Set.of(
            "planks", "cobblestone", "stone_brick", "sandstone", "terracotta",
            "door", "bed", "glass", "fence", "bell", "cooperation_hub",
            "dirt_path", "lantern", "torch", "bricks", "stairs", "slab",
            "lectern", "composter", "smithing_table", "blast_furnace", "smoker",
            "barrel", "loom", "cartography_table", "fletching_table", "stonecutter",
            "grindstone", "brewing_stand", "cauldron", "bookshelf", "chest", "hay_block",
            "smart_port", "cargo_station", "mint_machine", "atm_machine", "cable"
    );

    private final ServerLevel level;
    private final String dimensionId;
    private final PlayerPlacedBlockTracker placedBlocks;

    public ServerTerrainProbe(ServerLevel level, PlayerPlacedBlockTracker placedBlocks) {
        this.level = level;
        this.dimensionId = level.dimension().location().toString();
        this.placedBlocks = placedBlocks;
    }

    @Override
    public int getSurfaceY(int x, int z) {
        BlockPos probePos = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(probePos)) {
            return level.getSeaLevel();
        }
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, topY, z);
        while (cursor.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(cursor);
            boolean naturalTree = (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))
                    && !placedBlocks.isPlayerPlaced(dimensionId, cursor.getX(), cursor.getY(), cursor.getZ());
            if (!state.isAir() && !isNaturalClutterOnly(state) && !naturalTree) {
                return cursor.getY();
            }
            cursor.move(0, -1, 0);
        }
        return level.getMinBuildHeight();
    }

    @Override
    public boolean isNaturalReplaceable(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos) || isProtectedOrOccupied(x, y, z)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (isNaturalClutterOnly(state)) {
            return true;
        }
        // Natural logs/leaves (already checked NOT player-placed via isProtectedOrOccupied)
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    @Override
    public boolean isSolidFoundation(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(BlockTags.LEAVES) || isNaturalClutterOnly(state)) {
            return false;
        }
        Material mat = state.getMaterial();
        return mat.isSolid() && mat.blocksMotion();
    }

    @Override
    public boolean isProtectedOrOccupied(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return true;
        }
        if (placedBlocks.isPlayerPlaced(dimensionId, x, y, z)) {
            return true;
        }
        if (level.getBlockEntity(pos) != null) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        Block block = state.getBlock();
        String id = TyC2Ids.blockId(block);
        // Any non-vanilla mod block is always treated as protected!
        if (!id.startsWith("minecraft:")) {
            return true;
        }
        for (String sub : PROTECTED_VILLAGE_BLOCK_SUBSTRINGS) {
            if (id.contains(sub)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isWaterSurface(int x, int z) {
        BlockPos pos = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.is(Blocks.WATER) || state.getMaterial() == Material.WATER;
    }

    @Override
    public boolean isSkyClear(int x, int fromY, int z) {
        BlockPos pos = new BlockPos(x, fromY, z);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return level.canSeeSky(pos);
    }

    private static boolean isNaturalClutterOnly(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN
                || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH || b == Blocks.SNOW
                || b == Blocks.MOSS_CARPET || b == Blocks.POWDER_SNOW
                || b == Blocks.VINE || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS
                || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.BROWN_MUSHROOM
                || b == Blocks.RED_MUSHROOM || b == Blocks.CACTUS || b == Blocks.SUGAR_CANE) {
            return true;
        }
        return state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE_PLANTS);
    }
}
