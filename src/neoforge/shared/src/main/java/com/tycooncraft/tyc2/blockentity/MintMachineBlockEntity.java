package com.tycooncraft.tyc2.blockentity;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.block.MintMachineBlock;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.MintRecipeRegistry;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.menu.MintMachineMenu;
import com.tycooncraft.tyc2.platform.TyC2BlockEntityBase;
import com.tycooncraft.tyc2.platform.TyC2ItemAccess;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * BlockEntity for the Industrial Minting Machine ({@code tyc2:mint_machine}).
 *
 * <p>Slots:
 * <ul>
 *   <li>{@code 0, 1}: Metal/Alloy input slots (order-independent)</li>
 *   <li>{@code 2}: Minted coin output slot</li>
 *   <li>{@code 3}: Vanilla solid fuel or IC2 battery discharge slot</li>
 *   <li>{@code 4}: Communication Terminal charging slot</li>
 *   <li>{@code 5..8}: 4 optional IC2 machine upgrade slots</li>
 * </ul>
 */
public final class MintMachineBlockEntity extends TyC2BlockEntityBase
        implements TyC2EuSink, TyC2FeSink, WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_OUTPUT = 2;
    /** Slot 3: Electric battery discharge slot (IC2 batteries or FE items; no solid fuel). */
    public static final int SLOT_FUEL = 3;
    public static final int SLOT_BATTERY = SLOT_FUEL;
    public static final int SLOT_TERMINAL = 4;
    public static final int SLOT_UPGRADE_START = 5;
    public static final int UPGRADE_COUNT = 4;
    public static final int INVENTORY_SIZE = 9;

    public static final int DATA_COUNT = 10;

    private static final int[] SLOTS_TOP = {SLOT_INPUT_0, SLOT_INPUT_1};
    private static final int[] SLOTS_SIDES = {SLOT_INPUT_0, SLOT_INPUT_1, SLOT_FUEL};
    private static final int[] SLOTS_BOTTOM = {SLOT_OUTPUT, SLOT_FUEL};

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private double storedEu = 0.0;
    private int progressTicks = 0;
    private int totalTicks = 80;
    private boolean batteryDischarging = false;
    private String activeRecipeId = "";
    private int lastServerTick = Integer.MIN_VALUE;
    private int ioTickCounter = 0;
    private int pendingMintEuPerTick = 0;
    private boolean removalProcessed = false;
    private TriModePowerMath.MintUpgradeStats upgradeStats =
            TriModePowerMath.computeMintUpgradeStats(0, 0, 0);

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            int eu = (int) Math.floor(storedEu);
            int cap = upgradeStats.maxCapacityEu();
            return switch (index) {
                case 0 -> eu & 0xFFFF;
                case 1 -> cap & 0xFFFF;
                case 2 -> progressTicks;
                case 3 -> totalTicks;
                case 4 -> batteryDischarging ? 1 : 0;
                case 5 -> batteryDischarging ? 1 : 0;
                case 6 -> upgradeStats.powerTier();
                case 7 -> upgradeStats.maxInputEuPerTick();
                case 8 -> (eu >>> 16) & 0xFFFF;
                case 9 -> (cap >>> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> {
                    int hi = ((int) Math.floor(storedEu)) & 0xFFFF0000;
                    storedEu = Math.max(0, hi | (value & 0xFFFF));
                }
                case 2 -> progressTicks = Math.max(0, value);
                case 3 -> totalTicks = Math.max(1, value);
                case 4 -> batteryDischarging = value != 0;
                case 8 -> {
                    int lo = ((int) Math.floor(storedEu)) & 0xFFFF;
                    storedEu = Math.max(0, ((value & 0xFFFF) << 16) | lo);
                }
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MintMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2Registries.MINT_MACHINE_BE.get(), pos, state);
    }

    /** Returns current minting progress in ticks. */
    public int progress() {
        return progressTicks;
    }

    /** Returns total ticks required for the active minting cycle. */
    public int maxProgress() {
        return totalTicks;
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
        refreshUpgrades();
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

    /** Server-side per-tick logic driving pure-electric power, minting, terminal charging, and upgrades. */
    public void serverTick(ServerLevel level) {
        lastServerTick = level.getServer().getTickCount();
        refreshUpgrades();

        boolean dirty = false;

        // 1. Slot 3 electric battery discharge (IC2 battery or FE energy item; NO solid fuel)
        dirty |= tickBatteryDischarge();

        // 2. Process minting recipe FIRST so active minting is never starved by slot 4 charging
        boolean working = tickMinting(level);
        if (working) {
            dirty = true;
        }

        // 3. Charge Communication Terminal in slot 4 using surplus EU above next-tick mint demand
        dirty |= tickTerminalCharging(pendingMintEuPerTick);

        // 4. Optional IC2 ejector / pulling upgrade automation
        tickUpgradeAutomation(level);

        // 5. Update ACTIVE blockstate for glowing animated front texture
        BlockState state = getBlockState();
        if (!isRemoved() && state.hasProperty(MintMachineBlock.ACTIVE)
                && state.getValue(MintMachineBlock.ACTIVE) != working) {
            level.setBlock(worldPosition, state.setValue(MintMachineBlock.ACTIVE, working), Block.UPDATE_CLIENTS);
        }

        if (dirty) {
            setChanged();
        }
    }

    private boolean tickBatteryDischarge() {
        batteryDischarging = false;
        int maxCap = upgradeStats.maxCapacityEu();
        ItemStack battStack = items.get(SLOT_FUEL);
        if (battStack.isEmpty() || storedEu >= maxCap) {
            return false;
        }
        double freeSpace = maxCap - storedEu;
        if (freeSpace <= 0.0) {
            return false;
        }

        // 1. IC2 rechargeable / single-use battery discharge (passes full freeSpace & maxCap
        //    so 1,200 EU single-use batteries discharge cleanly while rechargeable batteries
        //    stay capped by their own IC2 tier transfer rate)
        double fromIc2 = Ic2SoftBridge.dischargeBattery(battStack, freeSpace, maxCap, upgradeStats.powerTier());
        if (fromIc2 > 0.0) {
            storedEu = Math.min(maxCap, storedEu + fromIc2);
            if (battStack.isEmpty()) {
                items.set(SLOT_FUEL, ItemStack.EMPTY);
            }
            batteryDischarging = true;
            return true;
        }

        // 2. NeoForge FE energy item discharge (capped to machine max input EU/t)
        double feRequestEu = Math.min(freeSpace, upgradeStats.maxInputEuPerTick());
        double fromFe = TyC2Native.dischargeFeItem(this, SLOT_FUEL, feRequestEu);
        if (fromFe > 0.0) {
            storedEu = Math.min(maxCap, storedEu + fromFe);
            batteryDischarging = true;
            return true;
        }
        return false;
    }

    private boolean isAcceptedBattery(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return Ic2SoftBridge.canDischargeBattery(stack, upgradeStats.powerTier())
                || TyC2Native.canDischargeFeItem(stack);
    }

    private boolean tickTerminalCharging(int reserveEuForMinting) {
        ItemStack term = items.get(SLOT_TERMINAL);
        double surplusEu = storedEu - Math.max(0, reserveEuForMinting);
        if (term.isEmpty() || !(term.getItem() instanceof CommTerminalItem) || surplusEu < 1.0) {
            return false;
        }
        long transfer = TriModePowerMath.computeChargeTransferEu(
                (long) Math.floor(surplusEu),
                CommTerminalItem.getEnergyEu(term),
                TriModePowerMath.TERMINAL_MAX_CAPACITY_EU,
                TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK
        );
        if (transfer > 0L) {
            int accepted = CommTerminalItem.chargeEu(term, (int) transfer);
            if (accepted > 0) {
                storedEu = Math.max(0.0, storedEu - accepted);
                return true;
            }
        }
        return false;
    }

    private boolean tickMinting(ServerLevel level) {
        pendingMintEuPerTick = 0;
        ItemStack in0 = items.get(SLOT_INPUT_0);
        ItemStack in1 = items.get(SLOT_INPUT_1);
        Optional<MintRecipeRegistry.MatchResult> matchOpt = TyC2DataLoader.mintRecipes().findMatch(
                TyC2Native.itemId(in0),
                in0.getCount(),
                TyC2Native.itemId(in1),
                in1.getCount(),
                TyC2Native::itemIdHasTag,
                ModList.get()::isLoaded
        );

        if (matchOpt.isEmpty()) {
            if (progressTicks != 0 || !activeRecipeId.isEmpty()) {
                progressTicks = 0;
                activeRecipeId = "";
                setChanged();
            }
            return false;
        }

        MintRecipeRegistry.MatchResult match = matchOpt.get();
        MintRecipe recipe = match.recipe();
        boolean ic2Loaded = Ic2SoftBridge.isIc2Loaded();
        String outputId = recipe.outputItemId(ic2Loaded);
        ItemStack expectedOut = TyC2Native.stack(outputId, recipe.outputCount());
        if (expectedOut.isEmpty() || !canAcceptOutput(expectedOut)) {
            return false;
        }

        if (!recipe.id().equals(activeRecipeId)) {
            activeRecipeId = recipe.id();
            progressTicks = 0;
        }

        int oc = upgradeStats.overclockerCount();
        int effTicks = TriModePowerMath.effectiveMintDurationTicks(recipe.durationTicks(), oc);
        int euPerTick = TriModePowerMath.effectiveMintEuPerTick(recipe.energyEu(), recipe.durationTicks(), oc);
        totalTicks = effTicks;
        pendingMintEuPerTick = euPerTick;

        if (storedEu + 1e-6 < euPerTick) {
            return false;
        }

        storedEu = Math.max(0.0, storedEu - euPerTick);
        progressTicks++;
        if (progressTicks >= effTicks) {
            progressTicks = 0;
            // Consume exact ingredient quantities
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
            // Place minted coins in output slot
            ItemStack existingOut = items.get(SLOT_OUTPUT);
            if (existingOut.isEmpty()) {
                items.set(SLOT_OUTPUT, expectedOut);
            } else {
                existingOut.grow(expectedOut.getCount());
            }
            // Inject newly minted currency face value into global MacroEconomyEngine!
            TyC2WorldManager.recordCoinsMinted(level, recipe.totalFaceValue());
            setChanged();
        }
        return true;
    }

    private boolean canAcceptOutput(ItemStack produced) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, produced)
                && out.getCount() + produced.getCount() <= Math.min(getMaxStackSize(), out.getMaxStackSize());
    }

    private void tickUpgradeAutomation(ServerLevel level) {
        if (!Ic2SoftBridge.isIc2Loaded()) {
            return;
        }
        ioTickCounter++;
        int interval = Math.max(2, 10 - upgradeStats.overclockerCount() * 2);
        if (ioTickCounter < interval) {
            return;
        }
        ioTickCounter = 0;

        List<ItemStack> upgrades = new ArrayList<>(UPGRADE_COUNT);
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            ItemStack u = items.get(SLOT_UPGRADE_START + i);
            if (!u.isEmpty()) {
                upgrades.add(u);
            }
        }
        if (upgrades.isEmpty()) {
            return;
        }

        // Ejector upgrade: push minted coins from SLOT_OUTPUT
        List<Direction> ejectFaces = Ic2SoftBridge.resolveEjectorFaces(upgrades);
        ItemStack out = items.get(SLOT_OUTPUT);
        if (!ejectFaces.isEmpty() && !out.isEmpty() && Ic2SoftBridge.matchesUpgradeFilter(upgrades, true, out)) {
            for (Direction dir : ejectFaces) {
                if (out.isEmpty()) {
                    break;
                }
                int pushed = TyC2ItemAccess.push(level, worldPosition.relative(dir), dir.getOpposite(), out);
                if (pushed > 0) {
                    out.shrink(pushed);
                    if (out.isEmpty()) {
                        items.set(SLOT_OUTPUT, ItemStack.EMPTY);
                    }
                    setChanged();
                }
            }
        }

        // Pulling upgrade: pull valid minting ingredients into SLOT_INPUT_0 / SLOT_INPUT_1
        List<Direction> pullFaces = Ic2SoftBridge.resolvePullingFaces(upgrades);
        if (!pullFaces.isEmpty()) {
            for (Direction dir : pullFaces) {
                ItemStack pulled = TyC2ItemAccess.pull(
                        level,
                        worldPosition.relative(dir),
                        dir.getOpposite(),
                        16,
                        stack -> Ic2SoftBridge.matchesUpgradeFilter(upgrades, false, stack)
                                && TyC2DataLoader.mintRecipes().isValidInputItem(
                                        TyC2Native.itemId(stack),
                                        TyC2Native::itemIdHasTag,
                                        ModList.get()::isLoaded
                                ) && canFitInInputSlots(stack)
                );
                if (!pulled.isEmpty()) {
                    ItemStack leftover = insertIntoInputSlots(pulled);
                    if (!leftover.isEmpty()) {
                        TyC2ItemAccess.push(level, worldPosition.relative(dir), dir.getOpposite(), leftover);
                    }
                    setChanged();
                    break;
                }
            }
        }
    }

    private boolean canFitInInputSlots(ItemStack sample) {
        for (int slot : SLOTS_TOP) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, sample)
                    && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack insertIntoInputSlots(ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int slot : SLOTS_TOP) {
            if (rem.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = items.get(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, rem)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, rem.getCount());
                    existing.grow(move);
                    rem.shrink(move);
                }
            }
        }
        for (int slot : SLOTS_TOP) {
            if (rem.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (items.get(slot).isEmpty()) {
                int move = Math.min(rem.getMaxStackSize(), rem.getCount());
                items.set(slot, rem.split(move));
            }
        }
        return rem;
    }

    /** Recomputes overclocker, transformer, and energy storage stats from slots 5..8. */
    public void refreshUpgrades() {
        int oc = 0;
        int tf = 0;
        int es = 0;
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            ItemStack stack = items.get(SLOT_UPGRADE_START + i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = TyC2Native.itemId(stack);
            if ("ic2:overclocker_upgrade".equals(id)) {
                oc += stack.getCount();
            } else if ("ic2:transformer_upgrade".equals(id)) {
                tf += stack.getCount();
            } else if ("ic2:energy_storage_upgrade".equals(id)) {
                es += stack.getCount();
            }
        }
        upgradeStats = TriModePowerMath.computeMintUpgradeStats(oc, tf, es);
        if (storedEu > upgradeStats.maxCapacityEu()) {
            storedEu = upgradeStats.maxCapacityEu();
        }
    }

    public int computeComparatorSignal() {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return 0;
        }
        return Math.min(15, Mth.floor((float) out.getCount() / (float) out.getMaxStackSize() * 14.0F) + 1);
    }

    public int getProgressPercent() {
        return totalTicks <= 0 ? 0 : Mth.clamp((progressTicks * 100) / totalTicks, 0, 100);
    }

    public void sendSyncTo(ServerPlayer player) {
        // Synced automatically via ContainerData in MintMachineMenu
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
        double room = Math.max(0.0, upgradeStats.maxCapacityEu() - storedEu);
        double accepted = Math.min(offeredEu, room);
        if (accepted > 0.0) {
            storedEu += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public double packetLimitEu() {
        return upgradeStats.maxInputEuPerTick();
    }

    @Override
    public double storedEu() {
        return storedEu;
    }

    @Override
    public double maxCapacityEu() {
        return upgradeStats.maxCapacityEu();
    }

    // --- TyC2FeSink ---

    @Override
    public int getStoredFe() {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(storedEu * TriModePowerMath.EU_TO_FE_RATIO));
    }

    @Override
    public int getMaxFe() {
        return (int) Math.min(Integer.MAX_VALUE, upgradeStats.maxCapacityFe());
    }

    @Override
    public int getMaxReceiveFePerTick() {
        return (int) Math.min(Integer.MAX_VALUE, upgradeStats.maxInputFePerTick());
    }

    @Override
    public int receiveFe(int maxReceiveFe, boolean simulate) {
        if (maxReceiveFe <= 0 || isRemoved()) {
            return 0;
        }
        int cappedFe = Math.min(maxReceiveFe, getMaxReceiveFePerTick());
        double offeredEu = cappedFe / (double) TriModePowerMath.EU_TO_FE_RATIO;
        double roomEu = Math.max(0.0, upgradeStats.maxCapacityEu() - storedEu);
        double acceptedEu = Math.min(offeredEu, roomEu);
        int acceptedFe = (int) Math.floor(acceptedEu * TriModePowerMath.EU_TO_FE_RATIO);
        if (acceptedFe > 0 && !simulate) {
            storedEu = Math.min(upgradeStats.maxCapacityEu(), storedEu + (acceptedFe / (double) TriModePowerMath.EU_TO_FE_RATIO));
            setChanged();
        }
        return acceptedFe;
    }

    @Override
    public void setStoredFeForRollback(int fe) {
        storedEu = Math.max(0.0, Math.min(upgradeStats.maxCapacityEu(), fe / (double) TriModePowerMath.EU_TO_FE_RATIO));
    }

    // --- WorldlyContainer ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return SLOTS_BOTTOM;
        }
        if (side == Direction.UP) {
            return SLOTS_TOP;
        }
        return SLOTS_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
        if (slot == SLOT_INPUT_0 || slot == SLOT_INPUT_1) {
            return TyC2DataLoader.mintRecipes().isValidInputItem(
                    TyC2Native.itemId(stack), TyC2Native::itemIdHasTag, ModList.get()::isLoaded
            );
        }
        if (slot == SLOT_FUEL) {
            return isAcceptedBattery(stack);
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == SLOT_OUTPUT || (slot == SLOT_FUEL && !isAcceptedBattery(stack));
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
            if (slot >= SLOT_UPGRADE_START) {
                refreshUpgrades();
            }
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
        if (slot >= SLOT_UPGRADE_START) {
            refreshUpgrades();
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
        refreshUpgrades();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT) {
            return false;
        }
        if (slot == SLOT_TERMINAL) {
            return stack.getItem() instanceof CommTerminalItem;
        }
        if (slot >= SLOT_UPGRADE_START) {
            return TriModePowerMath.isSupportedMintUpgrade(TyC2Native.itemId(stack));
        }
        if (slot == SLOT_FUEL) {
            return isAcceptedBattery(stack);
        }
        return true;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.tyc2.mint_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MintMachineMenu(syncId, playerInventory, this, containerData);
    }

    // --- Persistence ---

    @Override
    protected void saveMachineTag(CompoundTag tag) {
        tag.putDouble("stored_eu", storedEu);
        tag.putInt("progress", progressTicks);
        tag.putInt("total_ticks", totalTicks);
        tag.putString("recipe_id", activeRecipeId);
    }

    @Override
    protected void loadMachineTag(CompoundTag tag) {
        storedEu = Math.max(0.0, TyC2Nbt.getDouble(tag, "stored_eu", 0.0));
        progressTicks = Math.max(0, TyC2Nbt.getInt(tag, "progress", 0));
        totalTicks = Math.max(1, TyC2Nbt.getInt(tag, "total_ticks", 80));
        activeRecipeId = TyC2Nbt.getString(tag, "recipe_id", "");
        refreshUpgrades();
    }
}
