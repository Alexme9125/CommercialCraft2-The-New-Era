package com.tycooncraft.tyc2.platform;

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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge 26.x adjacent container item push/pull access supporting Transfer API
 * ({@code Capabilities.Item.BLOCK}) and vanilla containers.
 */
public final class TyC2ItemAccess {

    private TyC2ItemAccess() {
    }

    /**
     * Inserts up to {@code stack.getCount()} items into the container at {@code targetPos}
     * via {@code targetFace}. Returns the count actually inserted (does not mutate {@code stack}).
     */
    public static int push(ServerLevel level, BlockPos targetPos, Direction targetFace, ItemStack stack) {
        if (stack.isEmpty() || !level.hasChunkAt(targetPos)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(targetPos);
        var handler = level.getCapability(Capabilities.Item.BLOCK, targetPos, targetFace);
        if (handler != null) {
            try (var tx = Transaction.openRoot()) {
                int accepted = ResourceHandlerUtil.insertStacking(
                        handler,
                        ItemResource.of(stack),
                        stack.getCount(),
                        tx
                );
                tx.commit();
                return accepted;
            }
        }
        Container container = resolveContainer(level, targetPos, be);
        if (container == null) {
            return 0;
        }
        return pushIntoContainer(container, targetFace, stack);
    }

    /**
     * Extracts up to {@code maxCount} items matching {@code filter} from {@code sourcePos}
     * via {@code sourceFace}.
     */
    public static ItemStack pull(
            ServerLevel level,
            BlockPos sourcePos,
            Direction sourceFace,
            int maxCount,
            Predicate<ItemStack> filter
    ) {
        if (maxCount <= 0 || !level.hasChunkAt(sourcePos)) {
            return ItemStack.EMPTY;
        }
        BlockEntity be = level.getBlockEntity(sourcePos);
        var handler = level.getCapability(Capabilities.Item.BLOCK, sourcePos, sourceFace);
        if (handler != null) {
            try (var tx = Transaction.openRoot()) {
                var extracted = ResourceHandlerUtil.extractFirst(
                        handler,
                        resource -> filter.test(resource.toStack(1)),
                        maxCount,
                        tx
                );
                if (extracted == null) {
                    return ItemStack.EMPTY;
                }
                tx.commit();
                return extracted.resource().toStack(extracted.amount());
            }
        }
        Container container = resolveContainer(level, sourcePos, be);
        if (container == null) {
            return ItemStack.EMPTY;
        }
        return pullFromContainer(container, sourceFace, maxCount, filter);
    }

    private static Container resolveContainer(ServerLevel level, BlockPos pos, BlockEntity be) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return be instanceof Container container ? container : null;
    }

    private static int pushIntoContainer(Container container, Direction face, ItemStack stack) {
        int remaining = stack.getCount();
        int[] slots = accessibleSlots(container, face);

        for (int slot : slots) {
            if (remaining <= 0) {
                break;
            }
            if (!canInsert(container, slot, stack, face)) {
                continue;
            }
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int max = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
                int space = max - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, remaining);
                    existing.grow(move);
                    remaining -= move;
                    container.setChanged();
                }
            }
        }

        for (int slot : slots) {
            if (remaining <= 0) {
                break;
            }
            if (!canInsert(container, slot, stack, face)) {
                continue;
            }
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                int max = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                int move = Math.min(max, remaining);
                container.setItem(slot, stack.copyWithCount(move));
                remaining -= move;
                container.setChanged();
            }
        }
        return stack.getCount() - remaining;
    }

    private static ItemStack pullFromContainer(
            Container container,
            Direction face,
            int maxCount,
            Predicate<ItemStack> filter
    ) {
        int[] slots = accessibleSlots(container, face);
        for (int slot : slots) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !filter.test(existing)) {
                continue;
            }
            if (container instanceof WorldlyContainer worldly && !worldly.canTakeItemThroughFace(slot, existing, face)) {
                continue;
            }
            int take = Math.min(maxCount, Math.min(existing.getCount(), existing.getMaxStackSize()));
            ItemStack extracted = container.removeItem(slot, take);
            if (!extracted.isEmpty()) {
                container.setChanged();
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int[] accessibleSlots(Container container, Direction face) {
        if (container instanceof WorldlyContainer worldly) {
            return worldly.getSlotsForFace(face);
        }
        int[] slots = new int[container.getContainerSize()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private static boolean canInsert(Container container, int slot, ItemStack stack, Direction face) {
        if (!container.canPlaceItem(slot, stack)) {
            return false;
        }
        if (container instanceof WorldlyContainer worldly) {
            return worldly.canPlaceItemThroughFace(slot, stack, face);
        }
        return true;
    }
}
