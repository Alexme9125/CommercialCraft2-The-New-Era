package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for {@code tyc2:primitive_mint_machine} matching
 * {@code gui/primitive_mint_machine.png} (176x166).
 */
public class PrimitiveMintMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final BlockPos machinePos;
    @Nullable
    private final PrimitiveMintMachineBlockEntity blockEntity;

    public PrimitiveMintMachineMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    private PrimitiveMintMachineMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, resolveBe(playerInv, pos), pos);
    }

    public PrimitiveMintMachineMenu(int containerId, Inventory playerInv, PrimitiveMintMachineBlockEntity be) {
        this(containerId, playerInv, be, be.getBlockPos());
    }

    private PrimitiveMintMachineMenu(
            int containerId,
            Inventory playerInv,
            @Nullable PrimitiveMintMachineBlockEntity be,
            BlockPos pos
    ) {
        super(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_MENU.get(), containerId);
        this.blockEntity = be;
        this.machinePos = pos;
        this.container = be != null ? be : new SimpleContainer(PrimitiveMintMachineBlockEntity.TOTAL_SLOTS);
        this.data = be != null ? be.dataAccess : new SimpleContainerData(5);

        // Input slots: Slot 0 (38, 22), Slot 1 (58, 22)
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, 38, 22));
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, 58, 22));

        // Output coin slot: Slot 2 (114, 22) — extract only
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, 114, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Burnable fuel slot: Slot 3 (48, 57)
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_FUEL, 48, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PrimitiveMintMachineBlockEntity.isBurnableFuel(stack);
            }
        });

        // Player inventory (8, 84) + hotbar (8, 142)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(this.data);
    }

    @Nullable
    private static PrimitiveMintMachineBlockEntity resolveBe(Inventory inv, BlockPos pos) {
        if (inv.player.level != null
                && inv.player.level.getBlockEntity(pos) instanceof PrimitiveMintMachineBlockEntity be) {
            return be;
        }
        return null;
    }

    public BlockPos getMachinePos() {
        return machinePos;
    }

    public int getBurnRemaining() {
        return data.get(0);
    }

    public int getBurnTotal() {
        return Math.max(1, data.get(1));
    }

    public int getProgress() {
        return data.get(2);
    }

    public int getMaxProgress() {
        return Math.max(1, data.get(3));
    }

    public boolean isBlockedByIndustrialTier() {
        return data.get(4) != 0;
    }

    public boolean hasAnyInput() {
        return !container.getItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0).isEmpty()
                || !container.getItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1).isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || blockEntity.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = PrimitiveMintMachineBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (PrimitiveMintMachineBlockEntity.isBurnableFuel(stack)) {
                if (!moveItemStackTo(stack, PrimitiveMintMachineBlockEntity.SLOT_FUEL,
                        PrimitiveMintMachineBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PrimitiveMintMachineBlockEntity.SLOT_INPUT_0,
                    PrimitiveMintMachineBlockEntity.SLOT_INPUT_1 + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
