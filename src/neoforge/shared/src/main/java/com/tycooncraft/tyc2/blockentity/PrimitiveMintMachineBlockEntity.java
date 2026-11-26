package com.tycooncraft.tyc2.blockentity;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.MintRecipeRegistry;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.PrimitiveMintMachineMenu;
import com.tycooncraft.tyc2.platform.TyC2BlockEntityBase;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * BlockEntity for the fuel-fired Primitive Minting Machine ({@code tyc2:primitive_mint_machine}).
 *
 * <p>Deliberately has ZERO EU/FE energy storage, ZERO IC2 upgrade slots, and ZERO terminal
 * charging slot. Accepts burnable furnace fuel in slot 3 and presses strictly pure-Vanilla
 * {@code 1x..100x} recipes ({@code primitiveAllowed == true}) at {@code 1.5x} base duration.
 *
 * <p>Inventory layout (4 slots):
 * <ul>
 *   <li>{@code 0, 1}: Metal inputs</li>
 *   <li>{@code 2}: Minted coin output (extract-only)</li>
 *   <li>{@code 3}: Burnable fuel (coal, charcoal, planks, logs, blaze rod, lava bucket, etc.)</li>
 * </ul>
 */
public final class PrimitiveMintMachineBlockEntity extends TyC2BlockEntityBase
        implements WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_FUEL = 3;
    public static final int INVENTORY_SIZE = 4;
    public static final int DATA_COUNT = 5;

    private static final int[] SLOTS_TOP = {SLOT_INPUT_0, SLOT_INPUT_1};
    private static final int[] SLOTS_SIDES = {SLOT_INPUT_0, SLOT_INPUT_1, SLOT_FUEL};
    private static final int[] SLOTS_BOTTOM = {SLOT_OUTPUT, SLOT_FUEL};

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int burnTimeRemaining = 0;
    private int burnTimeTotal = 0;
    private int progressTicks = 0;
    private int totalTicks = 120;
    private boolean blockedByIndustrialTier = false;
    private String activeRecipeId = "";
    private boolean removalProcessed = false;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTimeRemaining;
                case 1 -> burnTimeTotal;
                case 2 -> progressTicks;
                case 3 -> totalTicks;
                case 4 -> blockedByIndustrialTier ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTimeRemaining = Math.max(0, value);
                case 1 -> burnTimeTotal = Math.max(0, value);
                case 2 -> progressTicks = Math.max(0, value);
                case 3 -> totalTicks = Math.max(1, value);
                case 4 -> blockedByIndustrialTier = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public PrimitiveMintMachineBlockEntity(BlockPos pos, BlockState state) {
        super(TyC2Registries.PRIMITIVE_MINT_MACHINE_BE.get(), pos, state);
    }

    /** Returns remaining burn ticks in the firebox. */
    public int burnTimeRemaining() {
        return burnTimeRemaining;
    }

    /** Returns initial burn ticks of the current fuel item. */
    public int burnTimeTotal() {
        return burnTimeTotal;
    }

    /** Returns current minting progress in ticks. */
    public int progress() {
        return progressTicks;
    }

    /** Returns total ticks required for the active primitive minting cycle (1.5x base). */
    public int maxProgress() {
        return totalTicks;
    }

    /** Returns {@code true} if current inputs match an Industrial-Mint-only recipe (200x-1000x or alloy). */
    public boolean isBlockedByIndustrialTier() {
        return blockedByIndustrialTier;
    }

    @Override
    protected NonNullList<ItemStack> inventoryItems() {
        return items;
    }

    @Override
    public void onBeforeRemove(ServerLevel level, BlockPos pos) {
        if (removalProcessed) {
            return;
        }
        removalProcessed = true;
        Containers.dropContents(level, pos, items);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
    }

    /** Server-side per-tick logic driving fuel-fired primitive minting (1x-100x Vanilla only). */
    public void serverTick(ServerLevel level) {
        boolean dirty = false;
        boolean working = false;

        ItemStack in0 = items.get(SLOT_INPUT_0);
        ItemStack in1 = items.get(SLOT_INPUT_1);
        String in0Id = TyC2Native.itemId(in0);
        String in1Id = TyC2Native.itemId(in1);

        Optional<MintRecipeRegistry.MatchResult> matchOpt = TyC2DataLoader.mintRecipes().findMatch(
                in0Id,
                in0.getCount(),
                in1Id,
                in1.getCount(),
                TyC2Native::itemIdHasTag,
                ModList.get()::isLoaded,
                true
        );

        if (matchOpt.isEmpty()) {
            boolean blocked = TyC2DataLoader.mintRecipes().requiresIndustrialMint(
                    in0Id,
                    in0.getCount(),
                    in1Id,
                    in1.getCount(),
                    TyC2Native::itemIdHasTag,
                    ModList.get()::isLoaded
            ) || isIndustrialOnlyIngredient(in0Id) || isIndustrialOnlyIngredient(in1Id);
            if (blocked != blockedByIndustrialTier) {
                blockedByIndustrialTier = blocked;
                dirty = true;
            }
            if (progressTicks != 0 || !activeRecipeId.isEmpty()) {
                progressTicks = 0;
                activeRecipeId = "";
                dirty = true;
            }
        } else {
            if (blockedByIndustrialTier) {
                blockedByIndustrialTier = false;
                dirty = true;
            }
            MintRecipeRegistry.MatchResult match = matchOpt.get();
            MintRecipe recipe = match.recipe();
            boolean ic2Loaded = Ic2SoftBridge.isIc2Loaded();
            String outputId = recipe.outputItemId(ic2Loaded);
            ItemStack expectedOut = TyC2Native.stack(outputId, recipe.outputCount());

            if (!expectedOut.isEmpty() && canAcceptOutput(expectedOut)) {
                if (!recipe.id().equals(activeRecipeId)) {
                    activeRecipeId = recipe.id();
                    progressTicks = 0;
                    dirty = true;
                }
                int primitiveTicks = TriModePowerMath.primitiveMintDurationTicks(recipe);
                if (totalTicks != primitiveTicks) {
                    totalTicks = primitiveTicks;
                }

                // Only ignite new burnable fuel when a valid primitive recipe can output
                if (burnTimeRemaining <= 0) {
                    ItemStack fuel = items.get(SLOT_FUEL);
                    int burnTicks = resolveBurnTicks(fuel);
                    if (burnTicks > 0) {
                        consumeFuelItem(fuel);
                        burnTimeRemaining = burnTicks;
                        burnTimeTotal = burnTicks;
                        dirty = true;
                    }
                }

                // Only consume fuel ticks while actively minting
                if (burnTimeRemaining > 0) {
                    burnTimeRemaining--;
                    progressTicks++;
                    working = true;
                    dirty = true;

                    if (progressTicks >= primitiveTicks) {
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
                            items.set(SLOT_OUTPUT, expectedOut);
                        } else {
                            existingOut.grow(expectedOut.getCount());
                        }
                        TyC2WorldManager.recordCoinsMinted(level, recipe.totalFaceValue());
                    }
                }
            }
        }

        BlockState state = getBlockState();
        if (!isRemoved() && state.hasProperty(PrimitiveMintMachineBlock.ACTIVE)
                && state.getValue(PrimitiveMintMachineBlock.ACTIVE) != working) {
            level.setBlock(worldPosition, state.setValue(PrimitiveMintMachineBlock.ACTIVE, working), Block.UPDATE_CLIENTS);
        }

        if (dirty) {
            setChanged();
        }
    }

    private boolean isIndustrialOnlyIngredient(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return TyC2DataLoader.mintRecipes().isValidInputItem(itemId, TyC2Native::itemIdHasTag, ModList.get()::isLoaded)
                && !TyC2DataLoader.mintRecipes().isValidPrimitiveInputItem(itemId, TyC2Native::itemIdHasTag, ModList.get()::isLoaded);
    }

    private int resolveBurnTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int canonical = TriModePowerMath.primitiveBurnTicks(TyC2Native.itemId(stack));
        if (canonical > 0) {
            return canonical;
        }
        return TyC2Native.furnaceBurnTicks(level, stack);
    }

    private void consumeFuelItem(ItemStack fuel) {
        if (fuel.is(Items.LAVA_BUCKET)) {
            if (fuel.getCount() == 1) {
                items.set(SLOT_FUEL, new ItemStack(Items.BUCKET));
            } else {
                fuel.shrink(1);
            }
            return;
        }
        ItemStack remainder = TyC2Native.craftingRemainder(fuel);
        fuel.shrink(1);
        if (fuel.isEmpty()) {
            items.set(SLOT_FUEL, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        }
    }

    private boolean canAcceptOutput(ItemStack produced) {
        ItemStack existing = items.get(SLOT_OUTPUT);
        if (existing.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(existing, produced)
                && existing.getCount() + produced.getCount() <= existing.getMaxStackSize();
    }

    /** Returns a redstone comparator signal {@code 0..15} reflecting output fill and burn activity. */
    public int computeComparatorSignal() {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (!out.isEmpty()) {
            return Math.min(15, 1 + (out.getCount() * 14) / Math.max(1, out.getMaxStackSize()));
        }
        return burnTimeRemaining > 0 ? 1 : 0;
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
            return TyC2DataLoader.mintRecipes().isValidPrimitiveInputItem(
                    TyC2Native.itemId(stack), TyC2Native::itemIdHasTag, ModList.get()::isLoaded
            );
        }
        if (slot == SLOT_FUEL) {
            return resolveBurnTicks(stack) > 0;
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == SLOT_OUTPUT || (slot == SLOT_FUEL && stack.is(Items.BUCKET));
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
        if (slot == SLOT_OUTPUT) {
            return false;
        }
        if (slot == SLOT_FUEL) {
            return resolveBurnTicks(stack) > 0;
        }
        return true;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.tyc2.primitive_mint_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new PrimitiveMintMachineMenu(syncId, playerInventory, this, containerData);
    }

    // --- Persistence ---

    @Override
    protected void saveMachineTag(CompoundTag tag) {
        tag.putInt("burn_rem", burnTimeRemaining);
        tag.putInt("burn_total", burnTimeTotal);
        tag.putInt("progress", progressTicks);
        tag.putInt("total_ticks", totalTicks);
        tag.putBoolean("blocked_industrial", blockedByIndustrialTier);
        tag.putString("recipe_id", activeRecipeId);
    }

    @Override
    protected void loadMachineTag(CompoundTag tag) {
        burnTimeRemaining = Math.max(0, TyC2Nbt.getInt(tag, "burn_rem", 0));
        burnTimeTotal = Math.max(0, TyC2Nbt.getInt(tag, "burn_total", 0));
        progressTicks = Math.max(0, TyC2Nbt.getInt(tag, "progress", 0));
        totalTicks = Math.max(1, TyC2Nbt.getInt(tag, "total_ticks", 120));
        blockedByIndustrialTier = TyC2Nbt.getBoolean(tag, "blocked_industrial", false);
        activeRecipeId = TyC2Nbt.getString(tag, "recipe_id", "");
    }
}
