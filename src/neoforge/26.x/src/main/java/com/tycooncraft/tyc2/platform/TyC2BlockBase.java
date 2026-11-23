package com.tycooncraft.tyc2.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * NeoForge 26.x base block class for TyC2 machines and Cooperation Hub bridging version-specific
 * interaction, neighbor-update, and block-removal hooks.
 */
public abstract class TyC2BlockBase extends Block implements EntityBlock {

    protected TyC2BlockBase(Properties properties) {
        super(properties);
    }

    /**
     * Handles right-clicking this block while holding an item.
     * Return {@code true} if handled (consumes interaction); {@code false} to fall through to {@code useWithoutItem}.
     */
    protected boolean handleUseWithItem(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        return false;
    }

    /** Called when a neighboring block updates. */
    protected void onNeighborChangedHook(BlockState state, Level level, BlockPos pos) {
    }

    /** Called on the server when the block is truly removed. */
    protected void onBlockRemovedHook(BlockState state, ServerLevel level, BlockPos pos) {
    }

    /** Computes the 0..15 redstone comparator signal for this block. */
    protected int computeAnalogOutputSignal(Level level, BlockPos pos) {
        return 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) {
        return computeAnalogOutputSignal(level, pos);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (handleUseWithItem(stack, state, level, pos, player, hand, hit)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            Orientation orientation,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        onNeighborChangedHook(state, level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            boolean movedByPiston
    ) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        onBlockRemovedHook(state, level, pos);
    }
}
