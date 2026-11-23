package com.tycooncraft.tyc2.forge.util;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Adjacent-container I/O helper for IC2 pulling and ejector upgrades on TyC2 machines and Hubs,
 * with zero direct IC2 class dependencies.
 */
public final class TyC2ContainerIo {

    private TyC2ContainerIo() {
    }

    /**
     * Pushes {@code stack} into adjacent containers along {@code faces}, returning any leftover.
     */
    public static ItemStack pushToAdjacent(
            ServerLevel level,
            BlockPos originPos,
            List<Direction> faces,
            ItemStack stack,
            Predicate<BlockEntity> blockEntityFilter
    ) {
        if (stack.isEmpty() || faces == null || faces.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (Direction face : faces) {
            if (remaining.isEmpty()) {
                break;
            }
            BlockPos neighborPos = originPos.relative(face);
            if (!level.hasChunkAt(neighborPos)) {
                continue;
            }
            remaining = insertIntoBlock(level, neighborPos, face.getOpposite(), remaining, blockEntityFilter);
        }
        return remaining;
    }

    /**
     * Pulls up to {@code maxItems} matching {@code itemFilter} from adjacent containers along {@code faces},
     * delegating insertion to {@code receiver} (which returns any leftover stack).
     *
     * @return total items moved
     */
    public static int pullFromAdjacent(
            ServerLevel level,
            BlockPos originPos,
            List<Direction> faces,
            Set<Direction> excludedFaces,
            int maxItems,
            Predicate<ItemStack> itemFilter,
            Predicate<BlockEntity> blockEntityFilter,
            java.util.function.UnaryOperator<ItemStack> receiver
    ) {
        if (maxItems <= 0 || faces == null || faces.isEmpty()) {
            return 0;
        }
        int totalMoved = 0;
        for (Direction face : faces) {
            if (totalMoved >= maxItems) {
                break;
            }
            if (excludedFaces != null && excludedFaces.contains(face)) {
                continue;
            }
            BlockPos neighborPos = originPos.relative(face);
            if (!level.hasChunkAt(neighborPos)) {
                continue;
            }
            totalMoved += extractFromBlock(
                    level,
                    neighborPos,
                    face.getOpposite(),
                    maxItems - totalMoved,
                    itemFilter,
                    blockEntityFilter,
                    receiver
            );
        }
        return totalMoved;
    }

    private static ItemStack insertIntoBlock(
            ServerLevel level,
            BlockPos pos,
            Direction accessSide,
            ItemStack stack,
            Predicate<BlockEntity> blockEntityFilter
    ) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || (blockEntityFilter != null && !blockEntityFilter.test(be))) {
            return stack;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container chest = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (chest != null) {
                return insertIntoVanillaContainer(chest, accessSide, stack);
            }
        }
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, accessSide).orElse(null);
        if (handler != null) {
            return ItemHandlerHelper.insertItemStacked(handler, stack, false);
        }
        if (be instanceof Container container) {
            return insertIntoVanillaContainer(container, accessSide, stack);
        }
        return stack;
    }

    private static int extractFromBlock(
            ServerLevel level,
            BlockPos pos,
            Direction accessSide,
            int maxItems,
            Predicate<ItemStack> itemFilter,
            Predicate<BlockEntity> blockEntityFilter,
            java.util.function.UnaryOperator<ItemStack> receiver
    ) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || (blockEntityFilter != null && !blockEntityFilter.test(be))) {
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container chest = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (chest != null) {
                return extractFromVanillaContainer(chest, accessSide, maxItems, itemFilter, receiver);
            }
        }
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, accessSide).orElse(null);
        if (handler != null) {
            int moved = 0;
            for (int slot = 0; slot < handler.getSlots() && moved < maxItems; slot++) {
                int budget = maxItems - moved;
                ItemStack preview = handler.extractItem(slot, budget, true);
                if (preview.isEmpty() || (itemFilter != null && !itemFilter.test(preview))) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, preview.getCount(), false);
                if (extracted.isEmpty()) {
                    continue;
                }
                int before = extracted.getCount();
                ItemStack leftover = receiver.apply(extracted);
                moved += before - leftover.getCount();
                if (!leftover.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(handler, leftover, false);
                }
            }
            return moved;
        }
        if (be instanceof Container container) {
            return extractFromVanillaContainer(container, accessSide, maxItems, itemFilter, receiver);
        }
        return 0;
    }

    private static ItemStack insertIntoVanillaContainer(Container container, Direction side, ItemStack stack) {
        ItemStack remaining = stack.copy();
        int[] slots = container instanceof WorldlyContainer wc
                ? wc.getSlotsForFace(side)
                : allSlots(container.getContainerSize());

        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int slot : slots) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (!container.canPlaceItem(slot, remaining)) {
                    continue;
                }
                if (container instanceof WorldlyContainer wc && !wc.canPlaceItemThroughFace(slot, remaining, side)) {
                    continue;
                }
                ItemStack existing = container.getItem(slot);
                if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)) {
                    int limit = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
                    int moved = Math.min(limit - existing.getCount(), remaining.getCount());
                    if (moved > 0) {
                        existing.grow(moved);
                        remaining.shrink(moved);
                        container.setChanged();
                    }
                } else if (pass == 1 && existing.isEmpty()) {
                    int limit = Math.min(container.getMaxStackSize(), remaining.getMaxStackSize());
                    int moved = Math.min(limit, remaining.getCount());
                    if (moved > 0) {
                        container.setItem(slot, remaining.split(moved));
                        container.setChanged();
                    }
                }
            }
        }
        return remaining;
    }

    private static int extractFromVanillaContainer(
            Container source,
            Direction side,
            int maxItems,
            Predicate<ItemStack> itemFilter,
            java.util.function.UnaryOperator<ItemStack> receiver
    ) {
        int[] slots = source instanceof WorldlyContainer wc
                ? wc.getSlotsForFace(side)
                : allSlots(source.getContainerSize());
        int moved = 0;
        for (int slot : slots) {
            if (moved >= maxItems) {
                break;
            }
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty() || (itemFilter != null && !itemFilter.test(stack))) {
                continue;
            }
            if (source instanceof WorldlyContainer wc && !wc.canTakeItemThroughFace(slot, stack, side)) {
                continue;
            }
            int budget = Math.min(maxItems - moved, stack.getCount());
            ItemStack taken = source.removeItem(slot, budget);
            if (taken.isEmpty()) {
                continue;
            }
            int before = taken.getCount();
            ItemStack leftover = receiver.apply(taken);
            moved += before - leftover.getCount();
            if (!leftover.isEmpty()) {
                ItemStack cur = source.getItem(slot);
                if (cur.isEmpty()) {
                    source.setItem(slot, leftover);
                } else {
                    cur.grow(leftover.getCount());
                }
                source.setChanged();
            }
        }
        return moved;
    }

    private static int[] allSlots(int size) {
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = i;
        }
        return arr;
    }
}
