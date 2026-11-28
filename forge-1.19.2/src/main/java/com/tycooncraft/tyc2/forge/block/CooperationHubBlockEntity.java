package com.tycooncraft.tyc2.forge.block;

import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.gui.CooperationHubMenu;
import com.tycooncraft.tyc2.forge.util.TyC2ChunkTickets;
import com.tycooncraft.tyc2.forge.util.TyC2ContainerIo;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/**
 * Village Cooperation Hub {@link BlockEntity} exposing a SavedData-backed 1,000-slot dual-zone
 * warehouse ({@code 0..499} Inbound INSERT-ONLY, {@code 500..999} Outbound EXTRACT-ONLY with
 * zero loop-back!), plus 2 Upgrade slots supporting {@code lc2:chunk_loading_upgrade} (3x3
 * {@code ForgeChunkManager.forceChunk}) and IC2 pulling/ejector upgrades.
 */
public class CooperationHubBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int UPGRADE_SLOTS = 2;
    private static final int[] ALL_1000_SLOTS;

    static {
        ALL_1000_SLOTS = new int[CooperationHubStorage.TOTAL_SLOTS];
        for (int i = 0; i < ALL_1000_SLOTS.length; i++) {
            ALL_1000_SLOTS[i] = i;
        }
    }

    @Nullable
    private UUID villageId = null;
    private final NonNullList<ItemStack> upgrades = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
    private boolean chunkTicketsHeld = false;
    private final Map<Integer, ItemStack> liveSlotCache = new HashMap<>();
    private long cachedStorageRevision = -1L;

    private LazyOptional<IItemHandler> itemHandler;

    /** Dedicated 2-slot Container for the Hub's upgrade slots used by {@link CooperationHubMenu}. */
    public final Container upgradeContainer = new Container() {
        @Override
        public int getContainerSize() {
            return UPGRADE_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return upgrades.get(0).isEmpty() && upgrades.get(1).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return upgrades.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack r = ContainerHelper.removeItem(upgrades, slot, amount);
            if (!r.isEmpty()) {
                setChanged();
            }
            return r;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(upgrades, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            upgrades.set(slot, stack);
            setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
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
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return TriModePowerMath.isSupportedHubUpgrade(TyC2Ids.itemId(stack));
        }

        @Override
        public void clearContent() {
            upgrades.clear();
        }
    };

    public CooperationHubBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2ForgeRegistries.COOPERATION_HUB_BE.get(), pos, state);
        this.itemHandler = LazyOptional.of(HubWarehouseHandler::new);
    }

    @Nullable
    public UUID getVillageId() {
        return villageId;
    }

    public void setVillageId(@Nullable UUID villageId) {
        this.villageId = villageId;
        setChanged();
    }

    /** Resolves {@code villageId}, lazily matching the village at this Hub position if unset. */
    @Nullable
    public UUID resolveVillageId() {
        if (villageId != null) {
            return villageId;
        }
        if (level instanceof ServerLevel serverLevel) {
            TycoonForgeSavedData data = TycoonForgeSavedData.get(serverLevel);
            Optional<VillageRecord> found = data.findVillageAtHub(
                    serverLevel.dimension().location().toString(), worldPosition
            );
            if (found.isPresent()) {
                this.villageId = found.get().getVillageId();
                setChanged();
            }
        }
        return villageId;
    }

    /** Returns the backing SavedData {@link CooperationHubStorage}, or {@code null} on client / unbound. */
    @Nullable
    public CooperationHubStorage resolveStorage() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID vid = resolveVillageId();
        if (vid == null) {
            return null;
        }
        return TycoonForgeSavedData.get(serverLevel).getVillage(vid)
                .map(VillageRecord::getStorage)
                .orElse(null);
    }

    public boolean isChunkLoaderActive() {
        return chunkTicketsHeld;
    }

    public void dropUpgradesTo(ServerPlayer player) {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack st = upgrades.get(i);
            if (!st.isEmpty()) {
                upgrades.set(i, ItemStack.EMPTY);
                if (!player.getInventory().add(st)) {
                    player.drop(st, false);
                }
            }
        }
        setChanged();
    }

    // --- Lifecycle & Tick ---

    @Override
    public void setRemoved() {
        flushLiveSlotCache();
        if (level instanceof ServerLevel sl
                && sl.isLoaded(worldPosition)
                && !sl.getBlockState(worldPosition).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())) {
            releaseChunkTickets();
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        flushLiveSlotCache();
        // Keep persistent ForgeChunkManager tickets active across chunk/server unloads when upgrade is installed
        super.onChunkUnloaded();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandler = LazyOptional.of(HubWarehouseHandler::new);
    }

    private void releaseChunkTickets() {
        if (chunkTicketsHeld && level instanceof ServerLevel serverLevel) {
            TyC2ChunkTickets.releaseArea(serverLevel, worldPosition);
            chunkTicketsHeld = false;
        }
    }

    public void serverTick(ServerLevel level) {
        flushLiveSlotCache();
        resolveVillageId();

        // 1. LC2 Chunk Loading Upgrade -> 3x3 forced chunks via ForgeChunkManager
        boolean hasChunkUpgrade = false;
        boolean hasEjector = false;
        boolean hasPulling = false;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack st = upgrades.get(i);
            if (st.isEmpty()) {
                continue;
            }
            if (Lc2SoftBridge.isChunkLoadingUpgrade(st)) {
                hasChunkUpgrade = true;
            }
            String id = TyC2Ids.itemId(st);
            if ("mhera:ejector_upgrade".equals(id) || "mhera:advanced_ejector_upgrade".equals(id)) {
                hasEjector = true;
            } else if ("mhera:pulling_upgrade".equals(id) || "mhera:advanced_pulling_upgrade".equals(id)) {
                hasPulling = true;
            }
        }

        if (hasChunkUpgrade) {
            if (!chunkTicketsHeld) {
                TyC2ChunkTickets.holdArea(level, worldPosition);
                chunkTicketsHeld = true;
            }
        } else {
            releaseChunkTickets();
        }

        // 2. IC2 Ejector (Outbound 500..999 ONLY) & Pulling (Inbound 0..499 ONLY) automation
        if (level.getGameTime() % 5L == 0L && (hasEjector || hasPulling)) {
            CooperationHubStorage storage = resolveStorage();
            if (storage == null) {
                return;
            }
            boolean mutated = false;
            if (hasEjector) {
                mutated |= ejectOutbound(level, storage);
            }
            if (hasPulling) {
                mutated |= pullIntoInbound(level, storage, hasEjector);
            }
            if (mutated) {
                TycoonForgeSavedData.get(level).setDirty();
            }
        }
    }

    private boolean ejectOutbound(ServerLevel level, CooperationHubStorage storage) {
        List<Direction> pushFaces = collectUpgradeFaces(true);
        if (pushFaces.isEmpty()) {
            return false;
        }
        int budget = 32;
        boolean movedAny = false;
        for (int slot = CooperationHubStorage.OUTBOUND_START;
             slot < CooperationHubStorage.OUTBOUND_END_EXCLUSIVE && budget > 0;
             slot++) {
            Optional<SlotStack> opt = storage.getSlot(slot);
            if (opt.isEmpty()) {
                continue;
            }
            ItemStack sample = TyC2Ids.fromSlotStack(opt.get());
            if (sample.isEmpty() || !Ic2Bridge.matchesUpgradeFilter(
                    upgrades, "mhera:ejector_upgrade", "mhera:advanced_ejector_upgrade", sample)) {
                continue;
            }
            int moveCount = Math.min(budget, sample.getCount());
            sample.setCount(moveCount);
            ItemStack leftover = TyC2ContainerIo.pushToAdjacent(
                    level, worldPosition, pushFaces, sample, be -> be != this && !(be instanceof CooperationHubBlockEntity)
            );
            int pushed = moveCount - leftover.getCount();
            if (pushed > 0) {
                storage.extractExternal(slot, pushed, false);
                budget -= pushed;
                movedAny = true;
            }
        }
        return movedAny;
    }

    private boolean pullIntoInbound(ServerLevel level, CooperationHubStorage storage, boolean hasEjector) {
        List<Direction> pullFaces = collectUpgradeFaces(false);
        if (pullFaces.isEmpty()) {
            return false;
        }
        Set<Direction> excluded = hasEjector
                ? copyToDirSet(collectUpgradeFaces(true))
                : Set.of();
        int moved = TyC2ContainerIo.pullFromAdjacent(
                level,
                worldPosition,
                pullFaces,
                excluded,
                32,
                s -> Ic2Bridge.matchesUpgradeFilter(
                        upgrades, "mhera:pulling_upgrade", "mhera:advanced_pulling_upgrade", s
                ),
                be -> be != this && !(be instanceof CooperationHubBlockEntity),
                stack -> storage.maxInboundInsert(
                        TyC2Ids.itemId(stack), Math.min(32, stack.getCount()), TyC2Ids.nbtJson(stack), stack.getMaxStackSize()
                ),
                stack -> {
                    int rem = storage.insertExternal(
                            TyC2Ids.itemId(stack), stack.getCount(), TyC2Ids.nbtJson(stack), stack.getMaxStackSize(), false
                    );
                    if (rem <= 0) {
                        return ItemStack.EMPTY;
                    }
                    ItemStack out = stack.copy();
                    out.setCount(rem);
                    return out;
                }
        );
        return moved > 0;
    }

    private List<Direction> collectUpgradeFaces(boolean ejector) {
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack st = upgrades.get(i);
            String id = TyC2Ids.itemId(st);
            boolean match = ejector
                    ? ("mhera:ejector_upgrade".equals(id) || "mhera:advanced_ejector_upgrade".equals(id))
                    : ("mhera:pulling_upgrade".equals(id) || "mhera:advanced_pulling_upgrade".equals(id));
            if (match) {
                faces.addAll(Ic2Bridge.resolveUpgradeFaces(st));
            }
        }
        return new ArrayList<>(faces);
    }

    private static Set<Direction> copyToDirSet(List<Direction> dirs) {
        return dirs.isEmpty() ? Set.of() : EnumSet.copyOf(dirs);
    }

    // --- Strict Dual-Zone IItemHandler (0..499 INSERT ONLY, 500..999 EXTRACT ONLY) ---

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!remove && cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    private final class HubWarehouseHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return CooperationHubStorage.TOTAL_SLOTS;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            flushLiveSlotCache();
            CooperationHubStorage storage = resolveStorage();
            if (storage == null || slot < 0 || slot >= CooperationHubStorage.TOTAL_SLOTS) {
                return ItemStack.EMPTY;
            }
            return storage.getSlot(slot).map(TyC2Ids::fromSlotStack).orElse(ItemStack.EMPTY);
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            flushLiveSlotCache();
            if (stack.isEmpty() || !CooperationHubStorage.Zone.INBOUND.containsSlot(slot)) {
                return stack;
            }
            CooperationHubStorage storage = resolveStorage();
            if (storage == null) {
                return stack;
            }
            String id = TyC2Ids.itemId(stack);
            if (id.isEmpty()) {
                return stack;
            }
            int remainder = storage.insertExternalAtSlot(
                    slot, id, stack.getCount(), TyC2Ids.nbtJson(stack), stack.getMaxStackSize(), simulate
            );
            if (!simulate && remainder < stack.getCount() && level instanceof ServerLevel sl) {
                TycoonForgeSavedData.get(sl).setDirty();
            }
            if (remainder <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack left = stack.copy();
            left.setCount(remainder);
            return left;
        }

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            flushLiveSlotCache();
            if (amount <= 0 || !CooperationHubStorage.Zone.OUTBOUND.containsSlot(slot)) {
                return ItemStack.EMPTY;
            }
            CooperationHubStorage storage = resolveStorage();
            if (storage == null) {
                return ItemStack.EMPTY;
            }
            Optional<SlotStack> extracted = storage.extractExternal(slot, amount, simulate);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate && level instanceof ServerLevel sl) {
                TycoonForgeSavedData.get(sl).setDirty();
            }
            return TyC2Ids.fromSlotStack(extracted.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return CooperationHubStorage.Zone.INBOUND.containsSlot(slot);
        }
    }

    // --- WorldlyContainer (strict dual-zone parity for vanilla hoppers) ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_1000_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return CooperationHubStorage.Zone.INBOUND.containsSlot(slot);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return CooperationHubStorage.Zone.OUTBOUND.containsSlot(slot);
    }

    @Override
    public int getContainerSize() {
        return CooperationHubStorage.TOTAL_SLOTS;
    }

    private void flushLiveSlotCache() {
        if (liveSlotCache.isEmpty()) {
            return;
        }
        CooperationHubStorage storage = resolveStorage();
        if (storage == null) {
            liveSlotCache.clear();
            cachedStorageRevision = -1L;
            return;
        }
        if (cachedStorageRevision != storage.getRevision()) {
            // Storage was modified directly (GUI / trade / IItemHandler); discard stale cached copies
            liveSlotCache.clear();
            cachedStorageRevision = storage.getRevision();
            return;
        }
        boolean mutated = false;
        for (Map.Entry<Integer, ItemStack> entry : liveSlotCache.entrySet()) {
            int slot = entry.getKey();
            ItemStack live = entry.getValue();
            SlotStack desired = (live == null || live.isEmpty()) ? null : TyC2Ids.toSlotStack(live);
            SlotStack current = storage.getSlot(slot).orElse(null);
            if (!java.util.Objects.equals(current, desired)) {
                storage.setSlotRaw(slot, desired);
                mutated = true;
            }
        }
        liveSlotCache.clear();
        cachedStorageRevision = storage.getRevision();
        if (mutated && level instanceof ServerLevel sl) {
            TycoonForgeSavedData.get(sl).setDirty();
        }
    }

    @Override
    public void setChanged() {
        flushLiveSlotCache();
        super.setChanged();
    }

    @Override
    public boolean isEmpty() {
        flushLiveSlotCache();
        CooperationHubStorage s = resolveStorage();
        return s == null || (s.getUsedSlotCount(CooperationHubStorage.Zone.INBOUND) == 0
                && s.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND) == 0);
    }

    @Override
    public ItemStack getItem(int slot) {
        CooperationHubStorage s = resolveStorage();
        if (s == null || slot < 0 || slot >= CooperationHubStorage.TOTAL_SLOTS) {
            return ItemStack.EMPTY;
        }
        if (cachedStorageRevision != s.getRevision()) {
            liveSlotCache.clear();
            cachedStorageRevision = s.getRevision();
        }
        ItemStack cached = liveSlotCache.get(slot);
        if (cached != null) {
            return cached;
        }
        ItemStack fresh = s.getSlot(slot).map(TyC2Ids::fromSlotStack).orElse(ItemStack.EMPTY);
        if (!fresh.isEmpty()) {
            liveSlotCache.put(slot, fresh);
        }
        return fresh;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        flushLiveSlotCache();
        if (!CooperationHubStorage.Zone.OUTBOUND.containsSlot(slot)) {
            return ItemStack.EMPTY;
        }
        CooperationHubStorage s = resolveStorage();
        if (s == null) {
            return ItemStack.EMPTY;
        }
        Optional<SlotStack> ext = s.extractExternal(slot, amount, false);
        cachedStorageRevision = s.getRevision();
        if (ext.isPresent() && level instanceof ServerLevel sl) {
            TycoonForgeSavedData.get(sl).setDirty();
        }
        return ext.map(TyC2Ids::fromSlotStack).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        liveSlotCache.remove(slot);
        flushLiveSlotCache();
        CooperationHubStorage s = resolveStorage();
        if (s == null || slot < 0 || slot >= CooperationHubStorage.TOTAL_SLOTS) {
            return;
        }
        if (stack.isEmpty()) {
            s.setSlotRaw(slot, null);
        } else {
            s.setSlotRaw(slot, TyC2Ids.toSlotStack(stack));
        }
        cachedStorageRevision = s.getRevision();
        if (level instanceof ServerLevel sl) {
            TycoonForgeSavedData.get(sl).setDirty();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        // Intentional no-op: Hub warehouse persists safely in Overworld SavedData!
    }

    // --- MenuProvider & NBT ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mnera.cooperation_hub");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new CooperationHubMenu(containerId, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (villageId != null) {
            tag.putUUID("VillageId", villageId);
        }
        ContainerHelper.saveAllItems(tag, upgrades);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        villageId = tag.hasUUID("VillageId") ? tag.getUUID("VillageId") : null;
        upgrades.clear();
        ContainerHelper.loadAllItems(tag, upgrades);
    }
}
