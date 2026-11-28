package com.tycooncraft.tyc2.forge.block;

import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.MintRecipeRegistry;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.gui.PrimitiveMintMachineMenu;
import com.tycooncraft.tyc2.forge.item.CoinItem;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import java.util.Optional;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/**
 * Forge 1.19.2 BlockEntity for {@code tyc2:primitive_mint_machine}:
 * <ul>
 *   <li>4 slots: {@code 0..1} metal inputs, {@code 2} coin output, {@code 3} burnable fuel.</li>
 *   <li>Burnable fuel only (NO IC2 EU, NO FE capability, NO upgrade/terminal slots).</li>
 *   <li>Mints pure-Vanilla {@code 1x..100x} recipes ({@code primitiveAllowed == true}) at
 *       {@code 1.5x} duration, injecting minted face value into {@code MacroEconomyEngine}.</li>
 *   <li>Blocks {@code 200x..1000x} &amp; modded/IC2 metals/alloys and syncs
 *       {@code blockedByIndustrialTier} so the GUI prompts upgrading to Industrial Mint.</li>
 * </ul>
 */
public class PrimitiveMintMachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_FUEL = 3;
    public static final int TOTAL_SLOTS = 4;

    private static final int[] SLOTS_TOP = {SLOT_INPUT_0, SLOT_INPUT_1};
    private static final int[] SLOTS_BOTTOM = {SLOT_OUTPUT, SLOT_FUEL};
    private static final int[] SLOTS_SIDES = {SLOT_INPUT_0, SLOT_INPUT_1, SLOT_FUEL};

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int burnTicksRemaining = 0;
    private int burnTicksTotal = 0;
    private int progressTicks = 0;
    private int maxProgressTicks = TriModePowerMath.primitiveMintDurationTicks(80);
    private String activeRecipeId = "";
    private boolean blockedByIndustrialTier = false;

    private LazyOptional<? extends IItemHandler>[] sidedItemHandlers;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTicksRemaining;
                case 1 -> Math.max(1, burnTicksTotal);
                case 2 -> progressTicks;
                case 3 -> Math.max(1, maxProgressTicks);
                case 4 -> blockedByIndustrialTier ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTicksRemaining = Math.max(0, value);
                case 1 -> burnTicksTotal = Math.max(1, value);
                case 2 -> progressTicks = Math.max(0, value);
                case 3 -> maxProgressTicks = Math.max(1, value);
                case 4 -> blockedByIndustrialTier = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public PrimitiveMintMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_BE.get(), pos, state);
        this.sidedItemHandlers = SidedInvWrapper.create(this, Direction.values());
    }

    /** Returns burn duration in ticks for {@code stack}, or {@code 0} if not a valid burnable fuel. */
    public static int getBurnTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || stack.getItem() instanceof CoinItem
                || stack.getItem() instanceof CommTerminalItem) {
            return 0;
        }
        int tableTicks = TriModePowerMath.primitiveBurnTicks(TyC2Ids.itemId(stack));
        if (tableTicks > 0) {
            return tableTicks;
        }
        int forgeTicks = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        return Math.max(0, forgeTicks);
    }

    /** Returns {@code true} if {@code stack} can burn in {@code tyc2:primitive_mint_machine}. */
    public static boolean isBurnableFuel(ItemStack stack) {
        return getBurnTicks(stack) > 0;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<? extends IItemHandler> h : sidedItemHandlers) {
            h.invalidate();
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.sidedItemHandlers = SidedInvWrapper.create(this, Direction.values());
    }

    // --- Server Tick ---

    public void serverTick(ServerLevel level) {
        ItemStack in0 = items.get(SLOT_INPUT_0);
        ItemStack in1 = items.get(SLOT_INPUT_1);
        String in0Id = TyC2Ids.itemId(in0);
        String in1Id = TyC2Ids.itemId(in1);

        MintRecipeRegistry registry = TyC2RuntimeData.mintRecipes();
        Optional<MintRecipeRegistry.MatchResult> matchOpt = registry.findMatch(
                in0Id,
                in0.getCount(),
                in1Id,
                in1.getCount(),
                TyC2Ids.ITEM_TAG_MATCHER,
                ModList.get()::isLoaded,
                true
        );

        boolean blocked = false;
        if (matchOpt.isEmpty()) {
            blocked = registry.requiresIndustrialMint(
                    in0Id, in0.getCount(), in1Id, in1.getCount(), TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
            ) || isIndustrialOnlyIngredient(registry, in0, in0Id)
                    || isIndustrialOnlyIngredient(registry, in1, in1Id);
        }
        if (this.blockedByIndustrialTier != blocked) {
            this.blockedByIndustrialTier = blocked;
            setChanged();
        }

        if (matchOpt.isEmpty()) {
            if (progressTicks != 0 || !activeRecipeId.isEmpty()) {
                progressTicks = 0;
                activeRecipeId = "";
                setChanged();
            }
            updateActiveState(level, false);
            return;
        }

        MintRecipeRegistry.MatchResult match = matchOpt.get();
        MintRecipe recipe = match.recipe();
        ItemStack outputSample = resolveOutputStack(recipe);
        if (outputSample.isEmpty() || !canAcceptOutput(outputSample)) {
            updateActiveState(level, false);
            return;
        }

        int duration = TriModePowerMath.primitiveMintDurationTicks(recipe);
        if (!recipe.id().equals(activeRecipeId)) {
            activeRecipeId = recipe.id();
            progressTicks = 0;
            setChanged();
        }
        maxProgressTicks = duration;

        // Only consume burnable fuel when actively minting a valid primitive recipe with room for output
        if (burnTicksRemaining <= 0) {
            tryConsumeFuelItem();
        }

        if (burnTicksRemaining <= 0) {
            if (progressTicks > 0) {
                progressTicks = Math.max(0, progressTicks - 2);
                setChanged();
            }
            updateActiveState(level, false);
            return;
        }

        burnTicksRemaining--;
        progressTicks++;
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
            // Inject newly minted currency face value into Global Money Supply
            TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
            data.economy().recordMinting(recipe.totalFaceValue());
            data.setDirty();
        }
        setChanged();
        updateActiveState(level, true);
    }

    private static boolean isIndustrialOnlyIngredient(MintRecipeRegistry registry, ItemStack stack, String itemId) {
        if (stack.isEmpty() || itemId.isEmpty()) {
            return false;
        }
        return !registry.isValidPrimitiveInputItem(itemId, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded)
                && registry.isValidInputItem(itemId, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded);
    }

    private void tryConsumeFuelItem() {
        ItemStack fuelStack = items.get(SLOT_FUEL);
        int burn = getBurnTicks(fuelStack);
        if (burn <= 0) {
            return;
        }
        ItemStack remainder = fuelStack.hasCraftingRemainingItem()
                ? fuelStack.getCraftingRemainingItem()
                : (fuelStack.is(Items.LAVA_BUCKET) ? new ItemStack(Items.BUCKET) : ItemStack.EMPTY);
        if (fuelStack.getCount() == 1 && !remainder.isEmpty()) {
            items.set(SLOT_FUEL, remainder);
        } else {
            fuelStack.shrink(1);
            if (fuelStack.isEmpty()) {
                items.set(SLOT_FUEL, remainder);
            }
        }
        burnTicksRemaining = burn;
        burnTicksTotal = burn;
        setChanged();
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

    private void updateActiveState(ServerLevel level, boolean active) {
        BlockState state = getBlockState();
        if (state.hasProperty(PrimitiveMintMachineBlock.ACTIVE)
                && state.getValue(PrimitiveMintMachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(PrimitiveMintMachineBlock.ACTIVE, active), 3);
        }
    }

    // --- Public Accessors ---

    public int getBurnTicksRemaining() {
        return burnTicksRemaining;
    }

    public int getBurnTicksTotal() {
        return Math.max(1, burnTicksTotal);
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getMaxProgressTicks() {
        return Math.max(1, maxProgressTicks);
    }

    public int getProgressPercent() {
        return maxProgressTicks <= 0 ? 0 : Math.min(100, (progressTicks * 100) / maxProgressTicks);
    }

    public boolean isBlockedByIndustrialTier() {
        return blockedByIndustrialTier;
    }

    public String getActiveRecipeId() {
        return activeRecipeId;
    }

    // --- WorldlyContainer & Capabilities (ITEM_HANDLER only — NO ENERGY!) ---

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!remove && cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return sidedItemHandlers[Direction.UP.ordinal()].cast();
            }
            return sidedItemHandlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.UP) {
            return SLOTS_TOP;
        }
        if (side == Direction.DOWN) {
            return SLOTS_BOTTOM;
        }
        return SLOTS_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == SLOT_INPUT_0 || slot == SLOT_INPUT_1) {
            return TyC2RuntimeData.mintRecipes().isValidPrimitiveInputItem(
                    TyC2Ids.itemId(stack), TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
            );
        }
        if (slot == SLOT_FUEL) {
            return isBurnableFuel(stack);
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == SLOT_OUTPUT || (slot == SLOT_FUEL && stack.is(Items.BUCKET));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT) {
            return false;
        }
        if (slot == SLOT_FUEL) {
            return isBurnableFuel(stack);
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
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- MenuProvider & NBT ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mnera.primitive_mint_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new PrimitiveMintMachineMenu(containerId, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("BurnRem", burnTicksRemaining);
        tag.putInt("BurnTot", burnTicksTotal);
        tag.putInt("Progress", progressTicks);
        tag.putInt("MaxProgress", maxProgressTicks);
        tag.putString("RecipeId", activeRecipeId);
        tag.putBoolean("BlockedIndustrial", blockedByIndustrialTier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        burnTicksRemaining = Math.max(0, tag.getInt("BurnRem"));
        burnTicksTotal = Math.max(0, tag.getInt("BurnTot"));
        progressTicks = Math.max(0, tag.getInt("Progress"));
        maxProgressTicks = Math.max(1, tag.getInt("MaxProgress"));
        activeRecipeId = tag.getString("RecipeId");
        blockedByIndustrialTier = tag.getBoolean("BlockedIndustrial");
    }
}
