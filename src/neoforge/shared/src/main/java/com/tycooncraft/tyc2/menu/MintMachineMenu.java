package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.MintMachineBlockEntity;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.platform.TyC2Native;
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
 * Container menu for the Industrial Minting Machine ({@code tyc2:mint_machine}),
 * aligned pixel-for-pixel with {@code gui/mint_machine.png} (176x176).
 */
public final class MintMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client-side constructor from extra menu buffer. */
    public MintMachineMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, resolveContainer(playerInventory, buf.readBlockPos()), new SimpleContainerData(MintMachineBlockEntity.DATA_COUNT));
    }

    /** Server-side constructor backed by live {@link MintMachineBlockEntity}. */
    public MintMachineMenu(
            int syncId,
            Inventory playerInventory,
            Container container,
            ContainerData data
    ) {
        super(TyC2Registries.MINT_MACHINE_MENU.get(), syncId);
        this.container = container;
        this.data = data;
        this.pos = container instanceof BlockEntity be ? be.getBlockPos() : BlockPos.ZERO;
        addDataSlots(data);

        // Slot 0 & 1: Metal / Alloy inputs
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_INPUT_0, 38, 26));
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_INPUT_1, 58, 26));

        // Slot 2: Minted Coin output (extract only)
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_OUTPUT, 112, 26) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Slot 3: Fuel / Battery discharge slot
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_FUEL, 38, 56) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(MintMachineBlockEntity.SLOT_FUEL, stack);
            }
        });

        // Slot 4: Communication Terminal charging slot
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_TERMINAL, 112, 56) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CommTerminalItem;
            }
        });

        // Slots 5..8: 4 IC2 Upgrade slots
        int[] upgradeYs = {18, 36, 54, 72};
        for (int i = 0; i < MintMachineBlockEntity.UPGRADE_COUNT; i++) {
            final int slotIdx = MintMachineBlockEntity.SLOT_UPGRADE_START + i;
            addSlot(new Slot(container, slotIdx, 152, upgradeYs[i]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TriModePowerMath.isSupportedMintUpgrade(TyC2Native.itemId(stack));
                }
            });
        }

        // Player Inventory (8, 94) + Hotbar (8, 152)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 94 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 152));
        }
    }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player != null && inv.player.level().getBlockEntity(pos) instanceof MintMachineBlockEntity be) {
            return be;
        }
        return new SimpleContainer(MintMachineBlockEntity.INVENTORY_SIZE);
    }

    public BlockPos blockPos() {
        return pos;
    }

    public int storedEu() {
        return (data.get(0) & 0xFFFF) | ((data.get(8) & 0xFFFF) << 16);
    }

    public int maxEu() {
        int raw = (data.get(1) & 0xFFFF) | ((data.get(9) & 0xFFFF) << 16);
        return Math.max(1, raw);
    }

    public int progressTicks() {
        return data.get(2);
    }

    public int totalTicks() {
        return Math.max(1, data.get(3));
    }

    public boolean isBatteryDischarging() {
        return data.get(4) > 0;
    }

    public int fuelRemaining() {
        return data.get(4);
    }

    public int fuelTotal() {
        return Math.max(1, data.get(5));
    }

    public int powerTier() {
        return Math.max(1, data.get(6));
    }

    public int maxInputEuPerTick() {
        return Math.max(32, data.get(7));
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
        int machineSlots = MintMachineBlockEntity.INVENTORY_SIZE;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof CommTerminalItem) {
                if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_TERMINAL, MintMachineBlockEntity.SLOT_TERMINAL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TriModePowerMath.isSupportedMintUpgrade(TyC2Native.itemId(stack))) {
                if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_UPGRADE_START, machineSlots, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (container.canPlaceItem(MintMachineBlockEntity.SLOT_FUEL, stack)) {
                if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_FUEL, MintMachineBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_INPUT_0, MintMachineBlockEntity.SLOT_INPUT_1 + 1, false)) {
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
