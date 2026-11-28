package com.tycooncraft.tyc2.blockentity;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.menu.AtmMachineMenu;
import com.tycooncraft.tyc2.platform.TyC2BlockEntityBase;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for the Industrial ATM ({@code tyc2:atm_machine}).
 *
 * <p>Slots:
 * <ul>
 *   <li>{@code 0}: Coin Deposit slot</li>
 *   <li>{@code 1}: Coin Withdrawal output slot</li>
 *   <li>{@code 2}: Communication Terminal charging slot</li>
 *   <li>{@code 3}: Fuel / IC2 battery discharge slot</li>
 * </ul>
 */
public final class AtmMachineBlockEntity extends TyC2BlockEntityBase
        implements TyC2EuSink, TyC2FeSink, WorldlyContainer, MenuProvider {

    public static final int SLOT_DEPOSIT = 0;
    public static final int SLOT_WITHDRAW = 1;
    public static final int SLOT_TERMINAL = 2;
    public static final int SLOT_FUEL = 3;
    public static final int INVENTORY_SIZE = 4;

    public static final int DATA_COUNT = 4;

    private static final int[] SLOTS_NONE = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private double storedEu = 0.0;
    private int fuelRemainingEu = 0;
    private int fuelTotalEu = 0;
    private int lastServerTick = Integer.MIN_VALUE;
    private boolean removalProcessed = false;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.floor(storedEu);
                case 1 -> TriModePowerMath.ATM_BASE_CAPACITY_EU;
                case 2 -> fuelRemainingEu;
                case 3 -> fuelTotalEu;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> storedEu = Math.max(0, value);
                case 2 -> fuelRemainingEu = Math.max(0, value);
                case 3 -> fuelTotalEu = Math.max(0, value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public AtmMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2Registries.ATM_MACHINE_BE.get(), pos, state);
    }

    public ContainerData containerData() {
        return containerData;
    }

    @Override
    protected NonNullList<ItemStack> inventoryItems() {
        return items;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            Ic2SoftBridge.connectEuSink(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            Ic2SoftBridge.disconnectEuSink(serverLevel, this);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel serverLevel) {
            Ic2SoftBridge.disconnectEuSink(serverLevel, this);
        }
        super.onChunkUnloaded();
    }

    @Override
    protected void onBeforeRemove(ServerLevel level, BlockPos pos) {
        if (removalProcessed) {
            return;
        }
        removalProcessed = true;
        Ic2SoftBridge.disconnectEuSink(level, this);
        Containers.dropContents(level, pos, items);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
    }

    /** Server-side tick: burns auxiliary fuel / discharges battery and charges Comm Terminal. */
    public void serverTick(ServerLevel level) {
        lastServerTick = level.getServer().getTickCount();
        boolean changed = false;
        int maxCap = TriModePowerMath.ATM_BASE_CAPACITY_EU;

        if (fuelRemainingEu > 0 && storedEu < maxCap) {
            int gen = TriModePowerMath.computeFuelTickGenerationEu(
                    fuelRemainingEu, (int) Math.floor(storedEu), maxCap
            );
            if (gen > 0) {
                storedEu = Math.min(maxCap, storedEu + gen);
                fuelRemainingEu -= gen;
                changed = true;
            }
        }

        ItemStack fuelStack = items.get(SLOT_FUEL);
        if (!fuelStack.isEmpty() && storedEu < maxCap) {
            double freeSpace = maxCap - storedEu;
            double fromBatt = Ic2SoftBridge.dischargeBattery(fuelStack, freeSpace, maxCap, 1);
            if (fromBatt > 0.0) {
                storedEu = Math.min(maxCap, storedEu + fromBatt);
                if (fuelStack.isEmpty()) {
                    items.set(SLOT_FUEL, ItemStack.EMPTY);
                }
                changed = true;
            } else {
                double feRequestEu = Math.min(freeSpace, TriModePowerMath.ATM_BASE_MAX_INPUT_EU_PER_TICK);
                double fromFe = TyC2Native.dischargeFeItem(this, SLOT_FUEL, feRequestEu);
                if (fromFe > 0.0) {
                    storedEu = Math.min(maxCap, storedEu + fromFe);
                    changed = true;
                } else if (fuelRemainingEu <= 0) {
                    int yield = TriModePowerMath.getFuelEnergyEu(TyC2Native.itemId(fuelStack), TyC2Native::itemIdHasTag);
                    if (yield > 0) {
                        fuelStack.shrink(1);
                        if (fuelStack.isEmpty()) {
                            items.set(SLOT_FUEL, ItemStack.EMPTY);
                        }
                        fuelRemainingEu = yield;
                        fuelTotalEu = yield;
                        changed = true;
                    }
                }
            }
        }

        ItemStack term = items.get(SLOT_TERMINAL);
        if (!term.isEmpty() && term.getItem() instanceof CommTerminalItem && storedEu >= 1.0) {
            long transfer = TriModePowerMath.computeChargeTransferEu(
                    (long) Math.floor(storedEu),
                    CommTerminalItem.getEnergyEu(term),
                    TriModePowerMath.TERMINAL_MAX_CAPACITY_EU,
                    TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK
            );
            if (transfer > 0L) {
                int accepted = CommTerminalItem.chargeEu(term, (int) transfer);
                if (accepted > 0) {
                    storedEu = Math.max(0.0, storedEu - accepted);
                    changed = true;
                }
            }
        }

        if (changed) {
            setChanged();
        }
    }

    /** Sends an up-to-date ATM banking snapshot to {@code player}. */
    public void sendSyncTo(ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel) {
            TyC2WorldManager.sendAtmSync(player, serverLevel, worldPosition, this);
        }
    }

    // --- TyC2EuSink ---

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return lastServerTick == level.getServer().getTickCount();
    }

    @Override
    public double receiveEu(double offeredEu) {
        if (offeredEu <= 0.0 || isRemoved()) {
            return 0.0;
        }
        double room = Math.max(0.0, TriModePowerMath.ATM_BASE_CAPACITY_EU - storedEu);
        double accepted = Math.min(offeredEu, room);
        if (accepted > 0.0) {
            storedEu += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public double packetLimitEu() {
        return TriModePowerMath.ATM_BASE_MAX_INPUT_EU_PER_TICK;
    }

    @Override
    public double storedEu() {
        return storedEu;
    }

    @Override
    public double maxCapacityEu() {
        return TriModePowerMath.ATM_BASE_CAPACITY_EU;
    }

    // --- TyC2FeSink ---

    @Override
    public int getStoredFe() {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(storedEu * TriModePowerMath.EU_TO_FE_RATIO));
    }

    @Override
    public int getMaxFe() {
        return TriModePowerMath.ATM_BASE_CAPACITY_EU * TriModePowerMath.EU_TO_FE_RATIO;
    }

    @Override
    public int getMaxReceiveFePerTick() {
        return TriModePowerMath.ATM_BASE_MAX_INPUT_EU_PER_TICK * TriModePowerMath.EU_TO_FE_RATIO;
    }

    @Override
    public int receiveFe(int maxReceiveFe, boolean simulate) {
        if (maxReceiveFe <= 0 || isRemoved()) {
            return 0;
        }
        int capped = Math.min(maxReceiveFe, getMaxReceiveFePerTick());
        double offeredEu = capped / (double) TriModePowerMath.EU_TO_FE_RATIO;
        double roomEu = Math.max(0.0, TriModePowerMath.ATM_BASE_CAPACITY_EU - storedEu);
        double acceptedEu = Math.min(offeredEu, roomEu);
        int acceptedFe = (int) Math.floor(acceptedEu * TriModePowerMath.EU_TO_FE_RATIO);
        if (acceptedFe > 0 && !simulate) {
            storedEu = Math.min(
                    TriModePowerMath.ATM_BASE_CAPACITY_EU,
                    storedEu + (acceptedFe / (double) TriModePowerMath.EU_TO_FE_RATIO)
            );
            setChanged();
        }
        return acceptedFe;
    }

    @Override
    public void setStoredFeForRollback(int fe) {
        storedEu = Math.max(
                0.0,
                Math.min(TriModePowerMath.ATM_BASE_CAPACITY_EU, fe / (double) TriModePowerMath.EU_TO_FE_RATIO)
        );
    }

    // --- WorldlyContainer ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_NONE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
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
        items.clear();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_WITHDRAW) {
            return false;
        }
        if (slot == SLOT_DEPOSIT) {
            return CurrencyDenomination.isLegalTender(TyC2Native.itemId(stack));
        }
        if (slot == SLOT_TERMINAL) {
            return stack.getItem() instanceof CommTerminalItem;
        }
        if (slot == SLOT_FUEL) {
            return TriModePowerMath.isFuelItem(TyC2Native.itemId(stack), TyC2Native::itemIdHasTag)
                    || Ic2SoftBridge.canDischargeBattery(stack, 1)
                    || TyC2Native.canDischargeFeItem(stack);
        }
        return false;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mnera.atm_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new AtmMachineMenu(syncId, playerInventory, this, containerData);
    }

    // --- Persistence ---

    @Override
    protected void saveMachineTag(CompoundTag tag) {
        tag.putDouble("stored_eu", storedEu);
        tag.putInt("fuel_rem", fuelRemainingEu);
        tag.putInt("fuel_total", fuelTotalEu);
    }

    @Override
    protected void loadMachineTag(CompoundTag tag) {
        storedEu = Math.max(0.0, TyC2Nbt.getDouble(tag, "stored_eu", 0.0));
        fuelRemainingEu = Math.max(0, TyC2Nbt.getInt(tag, "fuel_rem", 0));
        fuelTotalEu = Math.max(0, TyC2Nbt.getInt(tag, "fuel_total", 0));
    }
}
