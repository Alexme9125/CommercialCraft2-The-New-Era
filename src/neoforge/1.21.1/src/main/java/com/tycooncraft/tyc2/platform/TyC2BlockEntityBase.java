package com.tycooncraft.tyc2.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge 1.21.1 BlockEntity serialization and client-sync base for all TyC2 machines and hubs.
 */
public abstract class TyC2BlockEntityBase extends BlockEntity {

    private static final String KEY_TYC2_DATA = "mnera";

    protected TyC2BlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Returns the persistent inventory backing list for this block entity. */
    protected abstract NonNullList<ItemStack> inventoryItems();

    /** Writes non-item persistent state into {@code tag}. */
    protected abstract void saveMachineTag(CompoundTag tag);

    /** Restores non-item persistent state from {@code tag}. */
    protected abstract void loadMachineTag(CompoundTag tag);

    /** Writes lightweight client-side sync state into {@code tag}. */
    protected void saveClientSyncTag(CompoundTag tag) {
        saveMachineTag(tag);
    }

    /** Restores lightweight client-side sync state from {@code tag}. */
    protected void loadClientSyncTag(CompoundTag tag) {
        loadMachineTag(tag);
    }

    /** Called immediately before the block is removed on the server. */
    protected abstract void onBeforeRemove(ServerLevel level, BlockPos pos);

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> items = inventoryItems();
        if (!items.isEmpty()) {
            ContainerHelper.saveAllItems(tag, items, registries);
        }
        CompoundTag custom = new CompoundTag();
        saveMachineTag(custom);
        tag.put(KEY_TYC2_DATA, custom);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = inventoryItems();
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        if (!items.isEmpty()) {
            ContainerHelper.loadAllItems(tag, items, registries);
        }
        if (tag.contains(KEY_TYC2_DATA)) {
            loadMachineTag(tag.getCompound(KEY_TYC2_DATA));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag sync = new CompoundTag();
        saveClientSyncTag(sync);
        tag.put(KEY_TYC2_DATA, sync);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        if (tag.contains(KEY_TYC2_DATA)) {
            loadClientSyncTag(tag.getCompound(KEY_TYC2_DATA));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket pkt,
            HolderLookup.Provider lookupProvider
    ) {
        CompoundTag tag = pkt.getTag();
        if (tag != null && tag.contains(KEY_TYC2_DATA)) {
            loadClientSyncTag(tag.getCompound(KEY_TYC2_DATA));
        }
    }
}
