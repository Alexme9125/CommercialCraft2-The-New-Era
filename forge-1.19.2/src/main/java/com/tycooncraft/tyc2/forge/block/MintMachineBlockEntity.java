package com.tycooncraft.tyc2.forge.block;

import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.MintRecipeRegistry;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.gui.MintMachineMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2ContainerIo;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/**
 * Pure-Electric Industrial Minting Machine {@link BlockEntity} supporting IC2 EU (via optional
 * subclass), Forge FE ({@link ForgeCapabilities#ENERGY}), and electric battery discharge in
 * {@link #SLOT_BATTERY} (NO solid coal/fuel burning), full {@code 1x..1000x} Vanilla/IC2/modded
 * alloy minting recipes, terminal charging, 4 IC2 upgrades, and global Money Supply injection
 * into {@link com.tycooncraft.tyc2.core.MacroEconomyEngine}.
 */
public class MintMachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_FUEL = 3;
    /** Pure-electric alias for {@link #SLOT_FUEL} (electric battery / FE discharge slot). */
    public static final int SLOT_BATTERY = SLOT_FUEL;
    public static final int SLOT_TERMINAL = 4;
    public static final int SLOT_UPGRADE_START = 5;
    public static final int UPGRADE_COUNT = 4;
    public static final int TOTAL_SLOTS = 9;

    private static final int[] AUTOMATED_SLOTS = {SLOT_INPUT_0, SLOT_INPUT_1, SLOT_OUTPUT, SLOT_BATTERY};

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private double storedEu = 0.0;
    private boolean batteryDischarging = false;
    private int progressTicks = 0;
    private int maxProgressTicks = 80;
    private String activeRecipeId = "";
    private long lastTickTime = Long.MIN_VALUE;
    private boolean powerAttached = false;
    private boolean lastTickMinted = false;

    private TriModePowerMath.MintUpgradeStats upgradeStats =
            TriModePowerMath.computeMintUpgradeStats(0, 0, 0);
    private boolean hasEjector = false;
    private boolean hasPulling = false;
    private boolean upgradesDirty = true;

    private LazyOptional<? extends IItemHandler>[] sidedItemHandlers;
    private LazyOptional<IEnergyStorage> feHandler;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(storedEu);
                case 1 -> getMaxCapacityEu();
                case 2 -> progressTicks;
                case 3 -> maxProgressTicks;
                case 4 -> batteryDischarging ? 1 : 0;
                case 5 -> 1;
                case 6 -> getUpgradeStats().powerTier();
                case 7 -> getUpgradeStats().maxInputEuPerTick();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> storedEu = value;
                case 2 -> progressTicks = value;
                case 3 -> maxProgressTicks = Math.max(1, value);
                case 4 -> batteryDischarging = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public MintMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2ForgeRegistries.MINT_MACHINE_BE.get(), pos, state);
        this.sidedItemHandlers = SidedInvWrapper.create(this, Direction.values());
        this.feHandler = LazyOptional.of(this::createFeAdapter);
    }

    // --- Lifecycle ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel) {
            Ic2Bridge.connectPower(this);
            this.powerAttached = true;
            this.upgradesDirty = true;
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
        for (LazyOptional<? extends IItemHandler> h : sidedItemHandlers) {
            h.invalidate();
        }
        feHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.sidedItemHandlers = SidedInvWrapper.create(this, Direction.values());
        this.feHandler = LazyOptional.of(this::createFeAdapter);
    }

    // --- Server Tick ---

    public void serverTick(ServerLevel level) {
        long gameTime = level.getGameTime();
        this.lastTickTime = gameTime;
        if (!this.powerAttached) {
            Ic2Bridge.connectPower(this);
            this.powerAttached = true;
        }
        refreshUpgradesIfNeeded();

        int maxCap = upgradeStats.maxCapacityEu();
        if (storedEu > maxCap) {
            storedEu = maxCap;
            setChanged();
        }

        // 1. Pure-electric battery discharge in SLOT_BATTERY (IC2 battery or FE energy item; NO solid coal burning)
        tickBatteryDischarge(maxCap);

        // 2. IC2 Pulling / Ejector automation
        int ioPeriod = upgradeStats.overclockerCount() > 0 ? 2 : 5;
        if (gameTime % ioPeriod == 0L) {
            if (hasEjector) {
                ejectOutputSlot(level);
            }
            if (hasPulling) {
                pullInputSlots(level);
            }
        }

        // 3. Minting recipe execution (priority over terminal charging so LV/battery power is never starved)
        int mintReserveEu = tickMinting(level);
        updateActiveState(level, mintReserveEu > 0 && lastTickMinted);

        // 4. Charge tyc2:comm_terminal in SLOT_TERMINAL only with surplus EU above active mint demand
        tickTerminalCharging(mintReserveEu);
    }

    /**
     * Returns {@code true} if {@code stack} is an accepted electric battery for {@link #SLOT_BATTERY}
     * (either an IC2 battery when {@code ic2} is loaded or a Forge FE extractable energy item).
     * Solid coal/charcoal/redstone/blaze_rod fuels are strictly rejected ({@link TriModePowerMath#MINT_ACCEPTS_SOLID_FUEL}).
     */
    public static boolean isValidBatteryItem(ItemStack stack, int powerTier) {
        if (stack == null || stack.isEmpty() || stack.getItem() instanceof CommTerminalItem) {
            return false;
        }
        if (Ic2Bridge.canDischargeBattery(stack, Math.max(1, powerTier))) {
            return true;
        }
        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(energy -> energy.canExtract() && energy.getMaxEnergyStored() > 0)
                .orElse(false);
    }

    private void tickBatteryDischarge(int maxCap) {
        batteryDischarging = false;
        ItemStack batteryStack = items.get(SLOT_BATTERY);
        if (batteryStack.isEmpty() || storedEu >= maxCap) {
            return;
        }

        // 1. IC2 battery discharge (when IC2 is loaded): pass full freeSpace & maxCap so disposable
        //    batteries (ic2:single_use_battery, 1200 EU) discharge cleanly while rechargeable batteries
        //    stay rate-limited internally by IC2 ForgeGear.
        double space = maxCap - storedEu;
        if (Ic2Bridge.canDischargeBattery(batteryStack, upgradeStats.powerTier())) {
            double drawn = Ic2Bridge.dischargeBattery(
                    batteryStack,
                    space,
                    maxCap,
                    upgradeStats.powerTier()
            );
            if (drawn > 0.0) {
                storedEu = Math.min(maxCap, storedEu + drawn);
                batteryDischarging = true;
                if (batteryStack.isEmpty()) {
                    items.set(SLOT_BATTERY, ItemStack.EMPTY);
                }
                setChanged();
                return;
            }
        }

        // 2. Forge FE extractable battery / energy item discharge (1 EU = 4 FE)
        if (!(batteryStack.getItem() instanceof CommTerminalItem)) {
            IEnergyStorage itemEnergy = batteryStack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (itemEnergy != null && itemEnergy.canExtract() && itemEnergy.getEnergyStored() > 0) {
                long availEu = TriModePowerMath.feToEuFloor(itemEnergy.getEnergyStored());
                long wantEu = TriModePowerMath.computeBatteryDischargeTransferEu(
                        availEu,
                        (long) Math.floor(storedEu),
                        maxCap,
                        upgradeStats.maxInputEuPerTick()
                );
                if (wantEu > 0L) {
                    int requestFe = (int) Math.min(Integer.MAX_VALUE, TriModePowerMath.euToFe(wantEu));
                    int extractedFe = itemEnergy.extractEnergy(requestFe, false);
                    long gainedEu = TriModePowerMath.feToEuFloor(extractedFe);
                    if (gainedEu > 0L) {
                        storedEu = Math.min(maxCap, storedEu + gainedEu);
                        batteryDischarging = true;
                        setChanged();
                    }
                }
            }
        }
    }

    private void tickTerminalCharging(int mintReserveEu) {
        ItemStack term = items.get(SLOT_TERMINAL);
        double surplusEu = storedEu - Math.max(0, mintReserveEu);
        if (term.isEmpty() || !(term.getItem() instanceof CommTerminalItem) || surplusEu < 1.0) {
            return;
        }
        double charged = CommTerminalItem.chargeEu(term, surplusEu, upgradeStats.powerTier());
        if (charged > 0.0) {
            storedEu = Math.max(0.0, storedEu - charged);
            setChanged();
        }
    }

    /**
     * Advances minting for this tick and returns the EU/t reserve required by the queued/active recipe
     * (0 when idle or blocked on output), setting {@link #lastTickMinted} when progress actually advanced.
     */
    private int tickMinting(ServerLevel level) {
        lastTickMinted = false;
        ItemStack in0 = items.get(SLOT_INPUT_0);
        ItemStack in1 = items.get(SLOT_INPUT_1);
        Optional<MintRecipeRegistry.MatchResult> matchOpt = TyC2RuntimeData.mintRecipes().findMatch(
                TyC2Ids.itemId(in0),
                in0.getCount(),
                TyC2Ids.itemId(in1),
                in1.getCount(),
                TyC2Ids.ITEM_TAG_MATCHER,
                ModList.get()::isLoaded
        );
        if (matchOpt.isEmpty()) {
            if (progressTicks != 0 || !activeRecipeId.isEmpty()) {
                progressTicks = 0;
                activeRecipeId = "";
                setChanged();
            }
            return 0;
        }

        MintRecipeRegistry.MatchResult match = matchOpt.get();
        MintRecipe recipe = match.recipe();
        ItemStack outputSample = resolveOutputStack(recipe);
        if (outputSample.isEmpty() || !canAcceptOutput(outputSample)) {
            return 0;
        }

        int duration = TriModePowerMath.effectiveMintDurationTicks(recipe.durationTicks(), upgradeStats.overclockerCount());
        int euPerTick = TriModePowerMath.effectiveMintEuPerTick(
                recipe.energyEu(), recipe.durationTicks(), upgradeStats.overclockerCount()
        );
        if (!recipe.id().equals(activeRecipeId)) {
            activeRecipeId = recipe.id();
            progressTicks = 0;
        }
        maxProgressTicks = duration;

        if (storedEu < euPerTick) {
            return euPerTick;
        }

        storedEu -= euPerTick;
        progressTicks++;
        lastTickMinted = true;
        if (progressTicks >= duration) {
            progressTicks = 0;
            if (match.consumeSlot0() > 0) {
                in0.shrink(match.consumeSlot0());
                if (in0.isEmpty()) {
                    items.set(SLOT_INPUT_0, ItemStack.EMPTY);
                }
            }
            if (match.consumeSlot1() > 0) {
                in1.shrink(match.consumeSlot1());
                if (in1.isEmpty()) {
                    items.set(SLOT_INPUT_1, ItemStack.EMPTY);
                }
            }
            ItemStack existingOut = items.get(SLOT_OUTPUT);
            if (existingOut.isEmpty()) {
                items.set(SLOT_OUTPUT, outputSample);
            } else {
                existingOut.grow(outputSample.getCount());
            }
            // Inject newly minted currency face value into Global Money Supply!
            TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
            data.economy().recordMinting(recipe.totalFaceValue());
            data.setDirty();
        }
        setChanged();
        return euPerTick;
    }

    private ItemStack resolveOutputStack(MintRecipe recipe) {
        String id = recipe.outputItemId(Ic2Bridge.isLoaded());
        Item item = TyC2Ids.getItem(id);
        if (item == null) {
            item = TyC2Ids.getItem(recipe.outputDenomination().tyc2ItemId());
        }
        return item == null ? ItemStack.EMPTY : new ItemStack(item, recipe.outputCount());
    }

    private boolean canAcceptOutput(ItemStack produced) {
        ItemStack existing = items.get(SLOT_OUTPUT);
        if (existing.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(existing, produced)
                && existing.getCount() + produced.getCount() <= existing.getMaxStackSize();
    }

    private void ejectOutputSlot(ServerLevel level) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return;
        }
        List<Direction> faces = collectUpgradeFaces(true);
        if (faces.isEmpty()) {
            return;
        }
        ItemStack leftover = TyC2ContainerIo.pushToAdjacent(
                level, worldPosition, faces, out.copy(), be -> be != this
        );
        if (leftover.getCount() != out.getCount()) {
            items.set(SLOT_OUTPUT, leftover);
            setChanged();
        }
    }

    private void pullInputSlots(ServerLevel level) {
        List<Direction> pullFaces = collectUpgradeFaces(false);
        if (pullFaces.isEmpty()) {
            return;
        }
        Set<Direction> excluded = hasEjector
                ? copyToDirSet(collectUpgradeFaces(true))
                : Set.of();
        int moved = TyC2ContainerIo.pullFromAdjacent(
                level,
                worldPosition,
                pullFaces,
                excluded,
                16,
                s -> TyC2RuntimeData.mintRecipes().isValidInputItem(
                        TyC2Ids.itemId(s), TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
                ),
                be -> be != this,
                this::insertIntoMintInputs
        );
        if (moved > 0) {
            setChanged();
        }
    }

    private ItemStack insertIntoMintInputs(ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int slot : new int[]{SLOT_INPUT_0, SLOT_INPUT_1}) {
            if (rem.isEmpty()) {
                break;
            }
            ItemStack cur = items.get(slot);
            if (cur.isEmpty()) {
                items.set(slot, rem.split(Math.min(rem.getCount(), rem.getMaxStackSize())));
            } else if (ItemStack.isSameItemSameTags(cur, rem)) {
                int add = Math.min(cur.getMaxStackSize() - cur.getCount(), rem.getCount());
                if (add > 0) {
                    cur.grow(add);
                    rem.shrink(add);
                }
            }
        }
        return rem;
    }

    private List<Direction> collectUpgradeFaces(boolean ejector) {
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            ItemStack st = items.get(SLOT_UPGRADE_START + i);
            String id = TyC2Ids.itemId(st);
            boolean match = ejector
                    ? ("ic2:ejector_upgrade".equals(id) || "ic2:advanced_ejector_upgrade".equals(id))
                    : ("ic2:pulling_upgrade".equals(id) || "ic2:advanced_pulling_upgrade".equals(id));
            if (match) {
                faces.addAll(Ic2Bridge.resolveUpgradeFaces(st));
            }
        }
        return new ArrayList<>(faces);
    }

    private static Set<Direction> copyToDirSet(List<Direction> dirs) {
        return dirs.isEmpty() ? Set.of() : EnumSet.copyOf(dirs);
    }

    private void updateActiveState(ServerLevel level, boolean active) {
        BlockState state = getBlockState();
        if (state.hasProperty(MintMachineBlock.ACTIVE) && state.getValue(MintMachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(MintMachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    // --- Power API (used by FE adapter & optional IC2 subclass) ---

    public boolean didRunThisTick(ServerLevel level) {
        return !isRemoved() && this.lastTickTime == level.getGameTime();
    }

    public double getStoredEu() {
        return storedEu;
    }

    public void setStoredEu(double eu) {
        refreshUpgradesIfNeeded();
        this.storedEu = Math.max(0.0, Math.min(getMaxCapacityEu(), eu));
        setChanged();
    }

    public int getMaxCapacityEu() {
        refreshUpgradesIfNeeded();
        return upgradeStats.maxCapacityEu();
    }

    public int getMaxInputEuPerTick() {
        refreshUpgradesIfNeeded();
        return upgradeStats.maxInputEuPerTick();
    }

    public double receiveEu(double offered) {
        if (!Double.isFinite(offered) || offered <= 0.0) {
            return 0.0;
        }
        refreshUpgradesIfNeeded();
        double space = Math.max(0.0, getMaxCapacityEu() - storedEu);
        double accepted = Math.min(offered, space);
        if (accepted > 0.0) {
            storedEu += accepted;
            setChanged();
        }
        return accepted;
    }

    public int getProgressPercent() {
        return maxProgressTicks <= 0 ? 0 : Math.min(100, (progressTicks * 100) / maxProgressTicks);
    }

    public TriModePowerMath.MintUpgradeStats getUpgradeStats() {
        refreshUpgradesIfNeeded();
        return upgradeStats;
    }

    private void refreshUpgradesIfNeeded() {
        if (!upgradesDirty) {
            return;
        }
        int oc = 0;
        int tf = 0;
        int es = 0;
        boolean ej = false;
        boolean pu = false;
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            ItemStack st = items.get(SLOT_UPGRADE_START + i);
            if (st.isEmpty()) {
                continue;
            }
            String id = TyC2Ids.itemId(st);
            switch (id) {
                case "ic2:overclocker_upgrade" -> oc += st.getCount();
                case "ic2:transformer_upgrade" -> tf += st.getCount();
                case "ic2:energy_storage_upgrade" -> es += st.getCount();
                case "ic2:ejector_upgrade", "ic2:advanced_ejector_upgrade" -> ej = true;
                case "ic2:pulling_upgrade", "ic2:advanced_pulling_upgrade" -> pu = true;
                default -> {
                }
            }
        }
        this.upgradeStats = TriModePowerMath.computeMintUpgradeStats(oc, tf, es);
        this.hasEjector = ej;
        this.hasPulling = pu;
        this.upgradesDirty = false;
    }

    private IEnergyStorage createFeAdapter() {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                if (maxReceive <= 0) {
                    return 0;
                }
                refreshUpgradesIfNeeded();
                int capEu = getMaxCapacityEu();
                int spaceEu = Math.max(0, capEu - (int) Math.floor(storedEu));
                int rateEu = upgradeStats.maxInputEuPerTick();
                int acceptEu = (int) Math.min(Math.min(spaceEu, rateEu), TriModePowerMath.feToEuFloor(maxReceive));
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
                return (int) Math.min(Integer.MAX_VALUE, Math.round(storedEu) * TriModePowerMath.EU_TO_FE_RATIO);
            }

            @Override
            public int getMaxEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, (long) getMaxCapacityEu() * TriModePowerMath.EU_TO_FE_RATIO);
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

    // --- WorldlyContainer & Capabilities ---

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!remove) {
            if (cap == ForgeCapabilities.ENERGY) {
                return feHandler.cast();
            }
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                if (side == null) {
                    return sidedItemHandlers[Direction.UP.ordinal()].cast();
                }
                return sidedItemHandlers[side.ordinal()].cast();
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return AUTOMATED_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == SLOT_INPUT_0 || slot == SLOT_INPUT_1) {
            return TyC2RuntimeData.mintRecipes().isValidInputItem(
                    TyC2Ids.itemId(stack), TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
            );
        }
        if (slot == SLOT_BATTERY) {
            return isValidBatteryItem(stack, getUpgradeStats().powerTier());
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == SLOT_OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT) {
            return false;
        }
        if (slot == SLOT_TERMINAL) {
            return stack.getItem() instanceof CommTerminalItem;
        }
        if (slot >= SLOT_UPGRADE_START && slot < TOTAL_SLOTS) {
            return TriModePowerMath.isSupportedMintUpgrade(TyC2Ids.itemId(stack));
        }
        if (slot == SLOT_BATTERY) {
            return isValidBatteryItem(stack, getUpgradeStats().powerTier());
        }
        return true;
    }

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
            if (slot >= SLOT_UPGRADE_START) {
                upgradesDirty = true;
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty() && slot >= SLOT_UPGRADE_START) {
            upgradesDirty = true;
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (slot >= SLOT_UPGRADE_START) {
            upgradesDirty = true;
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        upgradesDirty = true;
    }

    // --- MenuProvider & NBT ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.tyc2.mint_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new MintMachineMenu(containerId, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putDouble("StoredEu", storedEu);
        tag.putInt("Progress", progressTicks);
        tag.putInt("MaxProgress", maxProgressTicks);
        tag.putString("RecipeId", activeRecipeId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        storedEu = Math.max(0.0, tag.getDouble("StoredEu"));
        batteryDischarging = false;
        progressTicks = Math.max(0, tag.getInt("Progress"));
        maxProgressTicks = Math.max(1, tag.getInt("MaxProgress"));
        activeRecipeId = tag.getString("RecipeId");
        upgradesDirty = true;
    }
}
