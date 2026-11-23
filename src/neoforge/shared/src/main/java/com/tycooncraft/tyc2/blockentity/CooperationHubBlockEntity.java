package com.tycooncraft.tyc2.blockentity;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.CooperationHubMenu;
import com.tycooncraft.tyc2.platform.TyC2BlockEntityBase;
import com.tycooncraft.tyc2.platform.TyC2ChunkTickets;
import com.tycooncraft.tyc2.platform.TyC2ItemAccess;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for the Village Cooperation Hub ({@code tyc2:cooperation_hub}).
 *
 * <p>Its {@link WorldlyContainer} exposes the 1,000-slot dual-zone {@link CooperationHubStorage}
 * stored inside {@code TycoonSavedData} for this Hub's bound village:
 * <ul>
 *   <li>Slots {@code 0..499} (Inbound): external INSERT ONLY (hoppers, pipes, LC2 Smart Port / Cargo Station, IC2 puller)</li>
 *   <li>Slots {@code 500..999} (Outbound): external EXTRACT ONLY (zero loop-back!)</li>
 * </ul>
 * Local non-piped slots (3): {@code 0} physical cash slot, {@code 1..2} upgrade slots
 * ({@code lc2:chunk_loading_upgrade} for 3x3 forced chunk loading + IC2 pulling/ejector upgrades).
 */
public final class CooperationHubBlockEntity extends TyC2BlockEntityBase
        implements WorldlyContainer, MenuProvider {

    public static final int LOCAL_SLOT_CASH = 0;
    public static final int LOCAL_SLOT_UPGRADE_0 = 1;
    public static final int LOCAL_SLOT_UPGRADE_1 = 2;
    public static final int LOCAL_INVENTORY_SIZE = 3;

    private static final int[] ALL_WAREHOUSE_SLOTS;

    static {
        ALL_WAREHOUSE_SLOTS = new int[CooperationHubStorage.TOTAL_SLOTS];
        for (int i = 0; i < CooperationHubStorage.TOTAL_SLOTS; i++) {
            ALL_WAREHOUSE_SLOTS[i] = i;
        }
    }

    private final NonNullList<ItemStack> localItems = NonNullList.withSize(LOCAL_INVENTORY_SIZE, ItemStack.EMPTY);
    private UUID villageId = null;
    private boolean chunkLoaderActive = false;
    private ChunkPos heldChunkCenter = null;
    private int ioTickCounter = 0;
    private boolean removalProcessed = false;

    /** Dedicated local Container for GUI cash + 2 upgrade slots (isolated from external pipe I/O). */
    private final Container localContainer = new Container() {
        @Override
        public int getContainerSize() {
            return LOCAL_INVENTORY_SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : localItems) {
                if (!s.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return localItems.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack taken = ContainerHelper.removeItem(localItems, slot, amount);
            if (!taken.isEmpty()) {
                setChanged();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(localItems, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            localItems.set(slot, stack);
            if (stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            setChanged();
        }

        @Override
        public void setChanged() {
            CooperationHubBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return CooperationHubBlockEntity.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            localItems.clear();
            setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == LOCAL_SLOT_CASH) {
                return CurrencyDenomination.isLegalTender(TyC2Native.itemId(stack));
            }
            return TriModePowerMath.isSupportedHubUpgrade(TyC2Native.itemId(stack));
        }
    };

    public CooperationHubBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2Registries.COOPERATION_HUB_BE.get(), pos, state);
    }

    public UUID getVillageId() {
        return villageId;
    }

    public void setVillageId(UUID villageId) {
        this.villageId = villageId;
        setChanged();
    }

    public Container localContainer() {
        return localContainer;
    }

    public boolean isChunkLoaderActive() {
        return chunkLoaderActive;
    }

    /** Resolves or links the {@link VillageRecord} associated with this Hub. */
    public VillageRecord resolveVillage(ServerLevel level) {
        if (villageId != null) {
            Optional<VillageRecord> existing = TyC2WorldManager.getVillage(level, villageId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Optional<VillageRecord> byPos = TyC2WorldManager.findVillageByHubPos(level, worldPosition);
        if (byPos.isPresent()) {
            this.villageId = byPos.get().getVillageId();
            setChanged();
            return byPos.get();
        }
        return null;
    }

    private CooperationHubStorage resolveStorage() {
        if (level instanceof ServerLevel serverLevel) {
            VillageRecord village = resolveVillage(serverLevel);
            if (village != null) {
                return village.getStorage();
            }
        }
        return null;
    }

    @Override
    protected NonNullList<ItemStack> inventoryItems() {
        return localItems;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            releaseChunkTickets(serverLevel);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel serverLevel) {
            releaseChunkTickets(serverLevel);
        }
        super.onChunkUnloaded();
    }

    @Override
    protected void onBeforeRemove(ServerLevel level, BlockPos pos) {
        if (removalProcessed) {
            return;
        }
        removalProcessed = true;
        releaseChunkTickets(level);
        // Drop only local cash & upgrade items; the 1,000-slot warehouse remains safely in TycoonSavedData!
        Containers.dropContents(level, pos, localItems);
        for (int i = 0; i < localItems.size(); i++) {
            localItems.set(i, ItemStack.EMPTY);
        }
    }

    /** Server-side tick: maintains 3x3 chunk tickets and optional IC2 ejector/pulling automation. */
    public void serverTick(ServerLevel level) {
        // 1. LC2 Chunk Loading Upgrade -> 3x3 forced chunk loading
        boolean hasChunkUpgrade = hasChunkLoadingUpgrade();
        if (hasChunkUpgrade && !chunkLoaderActive) {
            ChunkPos center = new ChunkPos(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            TyC2ChunkTickets.holdArea3x3(level, center);
            chunkLoaderActive = true;
            heldChunkCenter = center;
        } else if (!hasChunkUpgrade && chunkLoaderActive) {
            releaseChunkTickets(level);
        }

        // 2. Optional IC2 ejector (Outbound -> neighbor) and pulling (neighbor -> Inbound)
        ioTickCounter++;
        if (ioTickCounter >= 10) {
            ioTickCounter = 0;
            tickIc2UpgradeAutomation(level);
        }
    }

    public boolean hasChunkLoadingUpgrade() {
        for (int i = LOCAL_SLOT_UPGRADE_0; i <= LOCAL_SLOT_UPGRADE_1; i++) {
            ItemStack s = localItems.get(i);
            if (!s.isEmpty() && TyC2Constants.ITEM_LC2_CHUNK_LOADER.equals(TyC2Native.itemId(s))) {
                return true;
            }
        }
        return false;
    }

    private void releaseChunkTickets(ServerLevel level) {
        if (chunkLoaderActive && heldChunkCenter != null) {
            TyC2ChunkTickets.releaseArea3x3(level, heldChunkCenter);
            chunkLoaderActive = false;
            heldChunkCenter = null;
        }
    }

    private void tickIc2UpgradeAutomation(ServerLevel level) {
        if (!Ic2SoftBridge.isIc2Loaded()) {
            return;
        }
        VillageRecord village = resolveVillage(level);
        if (village == null) {
            return;
        }
        List<ItemStack> upgrades = new ArrayList<>(2);
        for (int i = LOCAL_SLOT_UPGRADE_0; i <= LOCAL_SLOT_UPGRADE_1; i++) {
            if (!localItems.get(i).isEmpty()) {
                upgrades.add(localItems.get(i));
            }
        }
        if (upgrades.isEmpty()) {
            return;
        }

        CooperationHubStorage storage = village.getStorage();
        var registries = level.registryAccess();

        // Ejector: extract strictly from Outbound (500..999) and push to configured faces
        List<Direction> ejectFaces = Ic2SoftBridge.resolveEjectorFaces(upgrades);
        if (!ejectFaces.isEmpty()) {
            int budget = 32;
            for (int slot = CooperationHubStorage.OUTBOUND_START;
                 slot < CooperationHubStorage.OUTBOUND_END_EXCLUSIVE && budget > 0;
                 slot++) {
                Optional<SlotStack> opt = storage.getSlot(slot);
                if (opt.isEmpty()) {
                    continue;
                }
                ItemStack candidate = TyC2Native.fromSlotStack(opt.get(), registries);
                if (candidate.isEmpty()) {
                    continue;
                }
                int offerCount = Math.min(candidate.getCount(), budget);
                ItemStack offer = candidate.copyWithCount(offerCount);
                for (Direction dir : ejectFaces) {
                    if (offer.isEmpty()) {
                        break;
                    }
                    BlockPos neighbor = worldPosition.relative(dir);
                    if (level.getBlockEntity(neighbor) instanceof CooperationHubBlockEntity) {
                        continue;
                    }
                    int pushed = TyC2ItemAccess.push(level, neighbor, dir.getOpposite(), offer);
                    if (pushed > 0) {
                        storage.extractExternal(slot, pushed, false);
                        offer.shrink(pushed);
                        budget -= pushed;
                        TyC2WorldManager.markDirty(level);
                    }
                }
            }
        }

        // Pulling: pull from configured faces strictly into Inbound (0..499)
        List<Direction> pullFaces = Ic2SoftBridge.resolvePullingFaces(upgrades);
        if (!pullFaces.isEmpty()) {
            int budget = 32;
            for (Direction dir : pullFaces) {
                if (budget <= 0) {
                    break;
                }
                BlockPos neighbor = worldPosition.relative(dir);
                if (level.getBlockEntity(neighbor) instanceof CooperationHubBlockEntity) {
                    continue;
                }
                ItemStack pulled = TyC2ItemAccess.pull(
                        level,
                        neighbor,
                        dir.getOpposite(),
                        budget,
                        stack -> {
                            SlotStack ss = TyC2Native.toSlotStack(stack, registries);
                            return ss != null && storage.canFitInInbound(
                                    ss.itemId(), ss.count(), ss.nbtJson(), stack.getMaxStackSize()
                            );
                        }
                );
                if (!pulled.isEmpty()) {
                    SlotStack ss = TyC2Native.toSlotStack(pulled, registries);
                    if (ss != null) {
                        int rem = storage.insertExternal(
                                ss.itemId(), ss.count(), ss.nbtJson(), pulled.getMaxStackSize(), false
                        );
                        budget -= (pulled.getCount() - rem);
                        if (rem > 0) {
                            TyC2ItemAccess.push(level, neighbor, dir.getOpposite(), pulled.copyWithCount(rem));
                        }
                        TyC2WorldManager.markDirty(level);
                    }
                }
            }
        }
    }

    public int computeComparatorSignal(ServerLevel level) {
        VillageRecord village = resolveVillage(level);
        if (village == null) {
            return 0;
        }
        int used = village.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND);
        return used <= 0 ? 0 : Math.min(15, 1 + (used * 14) / CooperationHubStorage.OUTBOUND_SLOTS);
    }

    public void sendSyncTo(ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel) {
            TyC2WorldManager.sendHubSync(player, serverLevel, worldPosition, this);
        }
    }

    // --- WorldlyContainer (1,000-slot SavedData dual-zone warehouse) ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_WAREHOUSE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return CooperationHubStorage.Zone.INBOUND.containsSlot(slot);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return CooperationHubStorage.Zone.OUTBOUND.containsSlot(slot);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return CooperationHubStorage.Zone.INBOUND.containsSlot(slot);
    }

    @Override
    public int getContainerSize() {
        return CooperationHubStorage.TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        CooperationHubStorage storage = resolveStorage();
        if (storage == null) {
            return true;
        }
        return storage.getUsedSlotCount(CooperationHubStorage.Zone.INBOUND) == 0
                && storage.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND) == 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        CooperationHubStorage storage = resolveStorage();
        if (storage == null || slot < 0 || slot >= CooperationHubStorage.TOTAL_SLOTS || level == null) {
            return ItemStack.EMPTY;
        }
        return storage.getSlot(slot)
                .map(ss -> TyC2Native.fromSlotStack(ss, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        CooperationHubStorage storage = resolveStorage();
        if (storage == null || amount <= 0 || slot < 0 || slot >= CooperationHubStorage.TOTAL_SLOTS || level == null) {
            return ItemStack.EMPTY;
        }
        Optional<SlotStack> taken = storage.playerExtract(slot, amount);
        if (taken.isPresent()) {
            if (level instanceof ServerLevel serverLevel) {
                TyC2WorldManager.markDirty(serverLevel);
            }
            return TyC2Native.fromSlotStack(taken.get(), level.registryAccess());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        CooperationHubStorage storage = resolveStorage();
        if (storage == null || slot < 0 || slot >= CooperationHubStorage.TOTAL_SLOTS || level == null) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            storage.setSlotRaw(slot, null);
        } else {
            SlotStack ss = TyC2Native.toSlotStack(stack, level.registryAccess());
            storage.setSlotRaw(slot, ss);
        }
        if (level instanceof ServerLevel serverLevel) {
            TyC2WorldManager.markDirty(serverLevel);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5
                ) <= 64.0;
    }

    @Override
    public void clearContent() {
        CooperationHubStorage storage = resolveStorage();
        if (storage != null) {
            storage.clearAll();
            if (level instanceof ServerLevel serverLevel) {
                TyC2WorldManager.markDirty(serverLevel);
            }
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.tyc2.cooperation_hub");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new CooperationHubMenu(syncId, playerInventory, this);
    }

    // --- Persistence ---

    @Override
    protected void saveMachineTag(CompoundTag tag) {
        if (villageId != null) {
            tag.putString("village_id", villageId.toString());
        }
    }

    @Override
    protected void loadMachineTag(CompoundTag tag) {
        String raw = TyC2Nbt.getString(tag, "village_id", "");
        if (!raw.isBlank()) {
            try {
                villageId = UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                villageId = null;
            }
        }
    }
}
