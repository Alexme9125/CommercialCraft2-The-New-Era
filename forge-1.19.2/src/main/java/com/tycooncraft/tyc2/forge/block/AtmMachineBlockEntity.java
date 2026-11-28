package com.tycooncraft.tyc2.forge.block;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.gui.AtmMachineMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Industrial ATM {@link BlockEntity} providing Personal & Guild/Company banking, 1:1 coin
 * deposit/withdrawal/exchange, player/company transfers, and a Tri-Mode powered Comm Terminal
 * charging dock (IC2 EU via optional subclass, Forge FE, or Vanilla fuel fallback).
 */
public class AtmMachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_DEPOSIT = 0;
    public static final int SLOT_WITHDRAW = 1;
    public static final int SLOT_TERMINAL = 2;
    public static final int SLOT_FUEL = 3;
    public static final int TOTAL_SLOTS = 4;
    private static final int[] SLOTS_NONE = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private double storedEu = 0.0;
    private int fuelRemainingEu = 0;
    private long lastTickTime = Long.MIN_VALUE;
    private boolean powerAttached = false;

    private LazyOptional<IEnergyStorage> feHandler;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(storedEu);
                case 1 -> TriModePowerMath.ATM_BASE_CAPACITY_EU;
                case 2 -> fuelRemainingEu;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> storedEu = value;
                case 2 -> fuelRemainingEu = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public AtmMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2ForgeRegistries.ATM_MACHINE_BE.get(), pos, state);
        this.feHandler = LazyOptional.of(this::createFeAdapter);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel) {
            Ic2Bridge.connectPower(this);
            this.powerAttached = true;
        }
    }

    @Override
    public void setRemoved() {
        detachPower();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        detachPower();
        super.onChunkUnloaded();
    }

    private void detachPower() {
        if (this.powerAttached) {
            Ic2Bridge.disconnectPower(this);
            this.powerAttached = false;
        }
        this.lastTickTime = Long.MIN_VALUE;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        feHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        feHandler = LazyOptional.of(this::createFeAdapter);
    }

    public void serverTick(ServerLevel level) {
        this.lastTickTime = level.getGameTime();
        if (!this.powerAttached) {
            Ic2Bridge.connectPower(this);
            this.powerAttached = true;
        }

        int maxCap = TriModePowerMath.ATM_BASE_CAPACITY_EU;
        if (fuelRemainingEu > 0 && storedEu < maxCap) {
            int gen = TriModePowerMath.computeFuelTickGenerationEu(fuelRemainingEu, (int) Math.floor(storedEu), maxCap);
            if (gen > 0) {
                fuelRemainingEu -= gen;
                storedEu = Math.min(maxCap, storedEu + gen);
                setChanged();
            }
        }

        ItemStack fuelStack = items.get(SLOT_FUEL);
        if (!fuelStack.isEmpty() && storedEu < maxCap) {
            if (Ic2Bridge.canDischargeBattery(fuelStack, 1)) {
                double drawn = Ic2Bridge.dischargeBattery(fuelStack, maxCap - storedEu, maxCap, 1);
                if (drawn > 0.0) {
                    storedEu = Math.min(maxCap, storedEu + drawn);
                    if (fuelStack.isEmpty()) {
                        items.set(SLOT_FUEL, ItemStack.EMPTY);
                    }
                    setChanged();
                }
            } else {
                boolean dischargedFe = false;
                if (!(fuelStack.getItem() instanceof CommTerminalItem)) {
                    IEnergyStorage itemEnergy = fuelStack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
                    if (itemEnergy != null && itemEnergy.canExtract() && itemEnergy.getEnergyStored() > 0) {
                        long availEu = TriModePowerMath.feToEuFloor(itemEnergy.getEnergyStored());
                        long wantEu = TriModePowerMath.computeBatteryDischargeTransferEu(
                                availEu,
                                (long) Math.floor(storedEu),
                                maxCap,
                                TriModePowerMath.ATM_BASE_MAX_INPUT_EU_PER_TICK
                        );
                        if (wantEu > 0L) {
                            int reqFe = (int) Math.min(Integer.MAX_VALUE, TriModePowerMath.euToFe(wantEu));
                            int extractedFe = itemEnergy.extractEnergy(reqFe, false);
                            long gainedEu = TriModePowerMath.feToEuFloor(extractedFe);
                            if (gainedEu > 0L) {
                                storedEu = Math.min(maxCap, storedEu + gainedEu);
                                dischargedFe = true;
                                setChanged();
                            }
                        }
                    }
                }
                if (!dischargedFe && fuelRemainingEu <= 0) {
                    int yield = TriModePowerMath.getFuelEnergyEu(TyC2Ids.itemId(fuelStack), TyC2Ids.ITEM_TAG_MATCHER);
                    if (yield > 0) {
                        fuelStack.shrink(1);
                        if (fuelStack.isEmpty()) {
                            items.set(SLOT_FUEL, ItemStack.EMPTY);
                        }
                        fuelRemainingEu = yield;
                        setChanged();
                    }
                }
            }
        }

        // Charge tyc2:comm_terminal in SLOT_TERMINAL
        ItemStack term = items.get(SLOT_TERMINAL);
        if (!term.isEmpty() && term.getItem() instanceof CommTerminalItem && storedEu >= 1.0) {
            double charged = CommTerminalItem.chargeEu(term, storedEu, 2);
            if (charged > 0.0) {
                storedEu = Math.max(0.0, storedEu - charged);
                setChanged();
            }
        }
    }

    // --- Banking Operations ---

    /** Deposits coins currently placed in {@link #SLOT_DEPOSIT} into the player's active account. */
    public Component handleDepositSlot(ServerPlayer player) {
        ItemStack stack = items.get(SLOT_DEPOSIT);
        long value = TyC2Ids.coinStackValue(stack);
        if (value <= 0L) {
            return Component.translatable("status.mnera.atm.invalid_amount");
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long tick = player.getServer().overworld().getGameTime();
        BankAccountManager.BankResult res = data.bank().deposit(player.getUUID(), value, tick, "ATM Deposit");
        if (!res.success()) {
            return Component.translatable(res.errorKey());
        }
        items.set(SLOT_DEPOSIT, ItemStack.EMPTY);
        data.economy().recordCashDepositedToBank(value);
        data.syncMacroAccountTotals();
        data.setDirty();
        setChanged();
        return Component.translatable("status.mnera.atm.deposited", value, describeActiveAccount(data, player));
    }

    /** Deposits all legal-tender coins from {@link #SLOT_DEPOSIT} AND {@code player}'s inventory. */
    public Component handleDepositAll(ServerPlayer player) {
        long total = 0L;
        ItemStack dep = items.get(SLOT_DEPOSIT);
        long depVal = TyC2Ids.coinStackValue(dep);
        if (depVal > 0L) {
            total += depVal;
        }
        Inventory inv = player.getInventory();
        List<Integer> coinSlots = new java.util.ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            long v = TyC2Ids.coinStackValue(st);
            if (v > 0L) {
                total += v;
                coinSlots.add(i);
            }
        }
        if (total <= 0L) {
            return Component.translatable("status.mnera.atm.invalid_amount");
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long tick = player.getServer().overworld().getGameTime();
        BankAccountManager.BankResult res = data.bank().deposit(player.getUUID(), total, tick, "ATM Deposit All");
        if (!res.success()) {
            return Component.translatable(res.errorKey());
        }
        if (depVal > 0L) {
            items.set(SLOT_DEPOSIT, ItemStack.EMPTY);
        }
        for (int idx : coinSlots) {
            inv.setItem(idx, ItemStack.EMPTY);
        }
        data.economy().recordCashDepositedToBank(total);
        data.syncMacroAccountTotals();
        data.setDirty();
        setChanged();
        return Component.translatable("status.mnera.atm.deposited", total, describeActiveAccount(data, player));
    }

    /** Withdraws an arbitrary IC amount in optimal high-to-low denominations into withdraw slot / player inventory. */
    public Component handleWithdrawAmount(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return Component.translatable("status.mnera.atm.invalid_amount");
        }
        if (!CurrencyDenomination.isWithinSingleWithdrawalLimit(amount)) {
            return Component.translatable(
                    "status.mnera.atm.withdraw_limit", CurrencyDenomination.MAX_SINGLE_WITHDRAWAL_IC
            );
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long tick = player.getServer().overworld().getGameTime();
        BankAccountManager.BankResult res = data.bank().withdraw(player.getUUID(), amount, tick, "ATM Withdraw");
        if (!res.success()) {
            return Component.translatable(res.errorKey());
        }
        dispenseCoins(player, TyC2Ids.createCoinStacks(amount, Ic2Bridge.isLoaded()));
        data.economy().recordCashWithdrawnFromBank(amount);
        data.syncMacroAccountTotals();
        data.setDirty();
        setChanged();
        return Component.translatable("status.mnera.atm.withdrawn", amount);
    }

    /** Withdraws an exact quantity of a chosen {@link CurrencyDenomination}. */
    public Component handleWithdrawDenom(ServerPlayer player, CurrencyDenomination denom, int count) {
        if (denom == null || count <= 0) {
            return Component.translatable("status.mnera.atm.invalid_amount");
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long tick = player.getServer().overworld().getGameTime();
        BankAccountManager.BankResult res = data.bank().withdrawDenominated(
                player.getUUID(), denom, count, tick, "Withdraw " + count + "x " + denom.value() + "x"
        );
        if (!res.success()) {
            return Component.translatable(res.errorKey());
        }
        String cid = denom.canonicalItemId(Ic2Bridge.isLoaded());
        Item item = TyC2Ids.getItem(cid);
        if (item == null) {
            item = TyC2Ids.getItem(denom.tyc2ItemId());
        }
        if (item != null) {
            int rem = count;
            List<ItemStack> stacks = new java.util.ArrayList<>();
            while (rem > 0) {
                int take = Math.min(rem, item.getMaxStackSize());
                stacks.add(new ItemStack(item, take));
                rem -= take;
            }
            dispenseCoins(player, stacks);
        }
        long total = denom.value() * count;
        data.economy().recordCashWithdrawnFromBank(total);
        data.syncMacroAccountTotals();
        data.setDirty();
        setChanged();
        return Component.translatable("status.mnera.atm.withdrawn", total);
    }

    private void dispenseCoins(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack coin : stacks) {
            ItemStack curWithdraw = items.get(SLOT_WITHDRAW);
            if (curWithdraw.isEmpty()) {
                items.set(SLOT_WITHDRAW, coin);
                continue;
            }
            if (ItemStack.isSameItemSameTags(curWithdraw, coin)
                    && curWithdraw.getCount() + coin.getCount() <= curWithdraw.getMaxStackSize()) {
                curWithdraw.grow(coin.getCount());
                continue;
            }
            if (!player.getInventory().add(coin)) {
                player.drop(coin, false);
            }
        }
    }

    public static String describeActiveAccount(TycoonForgeSavedData data, ServerPlayer player) {
        BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
        if (ref.scope() == BankAccountManager.AccountScope.COMPANY) {
            return data.bank().getCompany(ref.accountId())
                    .map(BankAccountManager.CompanyAccount::name)
                    .orElse(ref.accountId());
        }
        return player.getGameProfile().getName();
    }

    // --- Power API ---

    public boolean didRunThisTick(ServerLevel level) {
        return !isRemoved() && this.lastTickTime == level.getGameTime();
    }

    public double getStoredEu() {
        return storedEu;
    }

    public double receiveEu(double offered) {
        if (!Double.isFinite(offered) || offered <= 0.0) {
            return 0.0;
        }
        double space = Math.max(0.0, TriModePowerMath.ATM_BASE_CAPACITY_EU - storedEu);
        double accepted = Math.min(offered, space);
        if (accepted > 0.0) {
            storedEu += accepted;
            setChanged();
        }
        return accepted;
    }

    private IEnergyStorage createFeAdapter() {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                if (maxReceive <= 0) {
                    return 0;
                }
                int capEu = TriModePowerMath.ATM_BASE_CAPACITY_EU;
                int spaceEu = Math.max(0, capEu - (int) Math.floor(storedEu));
                int acceptEu = (int) Math.min(
                        Math.min(spaceEu, TriModePowerMath.ATM_BASE_MAX_INPUT_EU_PER_TICK),
                        TriModePowerMath.feToEuFloor(maxReceive)
                );
                if (acceptEu <= 0) {
                    return 0;
                }
                if (!simulate) {
                    storedEu = Math.min(capEu, storedEu + acceptEu);
                    setChanged();
                }
                return acceptEu * TriModePowerMath.EU_TO_FE_RATIO;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.round(storedEu) * TriModePowerMath.EU_TO_FE_RATIO;
            }

            @Override
            public int getMaxEnergyStored() {
                return TriModePowerMath.ATM_BASE_CAPACITY_EU * TriModePowerMath.EU_TO_FE_RATIO;
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!remove && cap == ForgeCapabilities.ENERGY) {
            return feHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    // --- WorldlyContainer (block hoppers/pipes from stealing deposit/withdraw/terminal slots) ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_NONE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
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
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_DEPOSIT -> CurrencyDenomination.isLegalTender(TyC2Ids.itemId(stack));
            case SLOT_WITHDRAW -> false;
            case SLOT_TERMINAL -> stack.getItem() instanceof CommTerminalItem;
            case SLOT_FUEL -> TriModePowerMath.isFuelItem(TyC2Ids.itemId(stack), TyC2Ids.ITEM_TAG_MATCHER)
                    || Ic2Bridge.canDischargeBattery(stack, 1);
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mnera.atm_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new AtmMachineMenu(containerId, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putDouble("StoredEu", storedEu);
        tag.putInt("FuelRem", fuelRemainingEu);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        storedEu = Math.max(0.0, tag.getDouble("StoredEu"));
        fuelRemainingEu = Math.max(0, tag.getInt("FuelRem"));
    }
}
