package com.tycooncraft.tyc2.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * NeoForge 1.21.1 base block class for TyC2 machines and Cooperation Hub bridging version-specific
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

    /** Called on the server when the block is truly removed (not merely a blockstate change). */
    protected void onBlockRemovedHook(BlockState state, ServerLevel level, BlockPos pos) {
    }

    /** Computes the 0..15 redstone comparator signal for this block. */
    protected int computeAnalogOutputSignal(Level level, BlockPos pos) {
        return 0;
    }

    /** Returns whether boss/mob entities (Wither, Ender Dragon) may destroy this block. */
    protected boolean allowEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return true;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return allowEntityDestroy(state, level, pos, entity) && super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return computeAnalogOutputSignal(level, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (handleUseWithItem(stack, state, level, pos, player, hand, hit)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        onNeighborChangedHook(state, level, pos);
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (state.getBlock() != newState.getBlock() && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof TyC2BlockEntityBase be) {
                be.onBeforeRemove(serverLevel, pos);
            }
            onBlockRemovedHook(state, serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
