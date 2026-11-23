package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.PrimitiveMintMachineBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Container menu for the Primitive Minting Machine ({@code tyc2:primitive_mint_machine}),
 * aligned pixel-for-pixel with {@code gui/primitive_mint_machine.png} (176x166).
 */
public final class PrimitiveMintMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client-side constructor from extra menu buffer. */
    public PrimitiveMintMachineMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(
                syncId,
                playerInventory,
                resolveContainer(playerInventory, buf.readBlockPos()),
                new SimpleContainerData(PrimitiveMintMachineBlockEntity.DATA_COUNT)
        );
    }

    /** Server-side constructor backed by live {@link PrimitiveMintMachineBlockEntity}. */
    public PrimitiveMintMachineMenu(
            int syncId,
            Inventory playerInventory,
            Container container,
            ContainerData data
    ) {
        super(TyC2Registries.PRIMITIVE_MINT_MACHINE_MENU.get(), syncId);
        this.container = container;
        this.data = data;
        this.pos = container instanceof BlockEntity be ? be.getBlockPos() : BlockPos.ZERO;
        addDataSlots(data);

        // Slot 0 & 1: Metal inputs
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, 38, 22));
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, 58, 22));

        // Slot 2: Minted Coin output (extract only)
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, 114, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Slot 3: Burnable Fuel slot
        addSlot(new Slot(container, PrimitiveMintMachineBlockEntity.SLOT_FUEL, 48, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, stack);
            }
        });

        // Player Inventory (8, 84) + Hotbar (8, 142)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player != null && inv.player.level().getBlockEntity(pos) instanceof PrimitiveMintMachineBlockEntity be) {
            return be;
        }
        return new SimpleContainer(PrimitiveMintMachineBlockEntity.INVENTORY_SIZE);
    }

    public BlockPos blockPos() {
        return pos;
    }

    public int burnRemaining() {
        return data.get(0);
    }

    public int burnTotal() {
        return Math.max(1, data.get(1));
    }

    public int progressTicks() {
        return data.get(2);
    }

    public int totalTicks() {
        return Math.max(1, data.get(3));
    }

    public boolean isBlockedByIndustrialTier() {
        return data.get(4) != 0;
    }

    public boolean hasInputItems() {
        return !container.getItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0).isEmpty()
                || !container.getItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1).isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = PrimitiveMintMachineBlockEntity.INVENTORY_SIZE;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (container.canPlaceItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, stack)) {
                if (!moveItemStackTo(stack, PrimitiveMintMachineBlockEntity.SLOT_FUEL, PrimitiveMintMachineBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, PrimitiveMintMachineBlockEntity.SLOT_INPUT_1 + 1, false)) {
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
