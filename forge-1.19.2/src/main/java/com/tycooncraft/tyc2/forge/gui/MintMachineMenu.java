package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
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
 * Container menu for {@code tyc2:mint_machine} matching {@code gui/mint_machine.png} (176x176).
 */
public class MintMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final BlockPos machinePos;
    @Nullable
    private final MintMachineBlockEntity blockEntity;

    public MintMachineMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    private MintMachineMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, resolveBe(playerInv, pos), pos);
    }

    public MintMachineMenu(int containerId, Inventory playerInv, MintMachineBlockEntity be) {
        this(containerId, playerInv, be, be.getBlockPos());
    }

    private MintMachineMenu(
            int containerId,
            Inventory playerInv,
            @Nullable MintMachineBlockEntity be,
            BlockPos pos
    ) {
        super(TyC2ForgeRegistries.MINT_MACHINE_MENU.get(), containerId);
        this.blockEntity = be;
        this.machinePos = pos;
        this.container = be != null ? be : new SimpleContainer(MintMachineBlockEntity.TOTAL_SLOTS);
        this.data = be != null ? be.dataAccess : new SimpleContainerData(10);

        // Input slots (38,26) & (58,26)
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_INPUT_0, 38, 26));
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_INPUT_1, 58, 26));

        // Coin output slot (112,26) — extract only
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_OUTPUT, 112, 26) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Electric battery discharge slot (38,56) — IC2 battery or FE energy item only (NO solid fuel)
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_BATTERY, 38, 56) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MintMachineBlockEntity.isValidBatteryItem(stack, Math.max(1, getPowerTier()));
            }
        });

        // Comm Terminal charging slot (112,56)
        addSlot(new Slot(container, MintMachineBlockEntity.SLOT_TERMINAL, 112, 56) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CommTerminalItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // 4 IC2 Upgrade slots (152, 18 / 36 / 54 / 72)
        for (int i = 0; i < MintMachineBlockEntity.UPGRADE_COUNT; i++) {
            addSlot(new Slot(container, MintMachineBlockEntity.SLOT_UPGRADE_START + i, 152, 18 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TriModePowerMath.isSupportedMintUpgrade(TyC2Ids.itemId(stack));
                }
            });
        }

        // Player inventory (8,94) + hotbar (8,152)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 94 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 152));
        }

        addDataSlots(this.data);
    }

    @Nullable
    private static MintMachineBlockEntity resolveBe(Inventory inv, BlockPos pos) {
        if (inv.player.level != null && inv.player.level.getBlockEntity(pos) instanceof MintMachineBlockEntity be) {
            return be;
        }
        return null;
    }

    public BlockPos getMachinePos() {
        return machinePos;
    }

    public int getStoredEu() {
        return (data.get(0) & 0xFFFF) | ((data.get(8) & 0xFFFF) << 16);
    }

    public int getMaxEu() {
        int val = (data.get(1) & 0xFFFF) | ((data.get(9) & 0xFFFF) << 16);
        return Math.max(1, val);
    }

    public int getProgress() {
        return data.get(2);
    }

    public int getMaxProgress() {
        return Math.max(1, data.get(3));
    }

    public boolean isBatteryDischarging() {
        return data.get(4) > 0;
    }

    public int getPowerTier() {
        return Math.max(1, data.get(6));
    }

    public int getMaxInputEuPerTick() {
        return Math.max(32, data.get(7));
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
        int machineSlots = MintMachineBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof CommTerminalItem) {
                if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_TERMINAL, MintMachineBlockEntity.SLOT_TERMINAL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TriModePowerMath.isSupportedMintUpgrade(TyC2Ids.itemId(stack))) {
                if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_UPGRADE_START, machineSlots, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (MintMachineBlockEntity.isValidBatteryItem(stack, getPowerTier())) {
                if (!moveItemStackTo(stack, MintMachineBlockEntity.SLOT_BATTERY, MintMachineBlockEntity.SLOT_BATTERY + 1, false)) {
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
