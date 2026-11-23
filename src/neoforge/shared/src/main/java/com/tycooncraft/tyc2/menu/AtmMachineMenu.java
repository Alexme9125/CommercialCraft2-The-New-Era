package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.Collections;
import java.util.Map;
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
 * Container menu for the Industrial ATM ({@code tyc2:atm_machine}),
 * aligned pixel-for-pixel with {@code gui/atm_machine.png} (220x214).
 */
public final class AtmMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    private Map<String, Object> syncedSnapshot = Collections.emptyMap();
    private int syncRevision = 0;

    /** Client-side constructor from extra menu buffer. */
    public AtmMachineMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, resolveContainer(playerInventory, buf.readBlockPos()), new SimpleContainerData(AtmMachineBlockEntity.DATA_COUNT));
    }

    /** Server-side constructor backed by live {@link AtmMachineBlockEntity}. */
    public AtmMachineMenu(
            int syncId,
            Inventory playerInventory,
            Container container,
            ContainerData data
    ) {
        super(TyC2Registries.ATM_MACHINE_MENU.get(), syncId);
        this.container = container;
        this.data = data;
        this.pos = container instanceof BlockEntity be ? be.getBlockPos() : BlockPos.ZERO;
        addDataSlots(data);

        // Slot 0: Coin Deposit (14, 62)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_DEPOSIT, 14, 62) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CurrencyDenomination.isLegalTender(TyC2Native.itemId(stack));
            }
        });

        // Slot 1: Coin Withdrawal output (14, 88)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_WITHDRAW, 14, 88) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Slot 2: Terminal charging (195, 90)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_TERMINAL, 195, 90) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CommTerminalItem;
            }
        });

        // Slot 3: Fuel / battery (195, 110)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_FUEL, 195, 110) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(AtmMachineBlockEntity.SLOT_FUEL, stack);
            }
        });

        // Player Inventory (29, 132) + Hotbar (29, 190)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 29 + col * 18, 132 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 29 + col * 18, 190));
        }
    }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player != null && inv.player.level().getBlockEntity(pos) instanceof AtmMachineBlockEntity be) {
            return be;
        }
        return new SimpleContainer(AtmMachineBlockEntity.INVENTORY_SIZE);
    }

    public BlockPos blockPos() {
        return pos;
    }

    public int storedEu() {
        return data.get(0);
    }

    public int maxEu() {
        return Math.max(1, data.get(1));
    }

    public Map<String, Object> snapshot() {
        return syncedSnapshot;
    }

    public int syncRevision() {
        return syncRevision;
    }

    public void applyClientSync(String json) {
        try {
            this.syncedSnapshot = JsonLite.parseObject(json);
            this.syncRevision++;
        } catch (Exception ignored) {
            // Keep prior snapshot
        }
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
        int machineSlots = AtmMachineBlockEntity.INVENTORY_SIZE;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (CurrencyDenomination.isLegalTender(TyC2Native.itemId(stack))) {
                if (!moveItemStackTo(stack, AtmMachineBlockEntity.SLOT_DEPOSIT, AtmMachineBlockEntity.SLOT_DEPOSIT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof CommTerminalItem) {
                if (!moveItemStackTo(stack, AtmMachineBlockEntity.SLOT_TERMINAL, AtmMachineBlockEntity.SLOT_TERMINAL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (container.canPlaceItem(AtmMachineBlockEntity.SLOT_FUEL, stack)) {
                if (!moveItemStackTo(stack, AtmMachineBlockEntity.SLOT_FUEL, AtmMachineBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
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
