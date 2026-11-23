package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.network.S2CAtmSyncPacket;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
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
 * Container menu for {@code tyc2:atm_machine} matching {@code gui/atm_machine.png} (220x214).
 */
public class AtmMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final BlockPos atmPos;
    @Nullable
    private final AtmMachineBlockEntity blockEntity;
    @Nullable
    private S2CAtmSyncPacket latestSync;

    public AtmMachineMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    private AtmMachineMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, resolveBe(playerInv, pos), pos);
    }

    public AtmMachineMenu(int containerId, Inventory playerInv, AtmMachineBlockEntity be) {
        this(containerId, playerInv, be, be.getBlockPos());
    }

    private AtmMachineMenu(
            int containerId,
            Inventory playerInv,
            @Nullable AtmMachineBlockEntity be,
            BlockPos pos
    ) {
        super(TyC2ForgeRegistries.ATM_MACHINE_MENU.get(), containerId);
        this.blockEntity = be;
        this.atmPos = pos;
        this.container = be != null ? be : new SimpleContainer(AtmMachineBlockEntity.TOTAL_SLOTS);
        this.data = be != null ? be.dataAccess : new SimpleContainerData(3);

        // Deposit slot (14,62)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_DEPOSIT, 14, 62) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CurrencyDenomination.isLegalTender(TyC2Ids.itemId(stack));
            }
        });

        // Withdraw slot (14,88) — extract only
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_WITHDRAW, 14, 88) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Terminal charge slot (195,90)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_TERMINAL, 195, 90) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CommTerminalItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Auxiliary fuel / battery slot (195,110)
        addSlot(new Slot(container, AtmMachineBlockEntity.SLOT_FUEL, 195, 110) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TriModePowerMath.isFuelItem(TyC2Ids.itemId(stack))
                        || Ic2Bridge.canDischargeBattery(stack, 1);
            }
        });

        // Player inventory (29,132) + hotbar (29,190)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 29 + col * 18, 132 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 29 + col * 18, 190));
        }

        addDataSlots(this.data);
    }

    @Nullable
    private static AtmMachineBlockEntity resolveBe(Inventory inv, BlockPos pos) {
        if (inv.player.level != null && inv.player.level.getBlockEntity(pos) instanceof AtmMachineBlockEntity be) {
            return be;
        }
        return null;
    }

    public BlockPos getAtmPos() {
        return atmPos;
    }

    @Nullable
    public AtmMachineBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getStoredEu() {
        return data.get(0);
    }

    public int getMaxEu() {
        return Math.max(1, data.get(1));
    }

    public int getFuelRemaining() {
        return data.get(2);
    }

    @Nullable
    public S2CAtmSyncPacket getLatestSync() {
        return latestSync;
    }

    public void setLatestSync(S2CAtmSyncPacket sync) {
        this.latestSync = sync;
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
        int atmSlots = AtmMachineBlockEntity.TOTAL_SLOTS;
        if (index < atmSlots) {
            if (!moveItemStackTo(stack, atmSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof CommTerminalItem) {
                if (!moveItemStackTo(stack, AtmMachineBlockEntity.SLOT_TERMINAL, AtmMachineBlockEntity.SLOT_TERMINAL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (CurrencyDenomination.isLegalTender(TyC2Ids.itemId(stack))) {
                if (!moveItemStackTo(stack, AtmMachineBlockEntity.SLOT_DEPOSIT, AtmMachineBlockEntity.SLOT_DEPOSIT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TriModePowerMath.isFuelItem(TyC2Ids.itemId(stack))
                    || Ic2Bridge.canDischargeBattery(stack, 1)) {
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
