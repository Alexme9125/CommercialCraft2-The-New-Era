package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for {@code tyc2:cooperation_hub} matching {@code gui/cooperation_hub.png} (248x222):
 * physical coin cash slot {@code (181,144)}, 2 upgrade slots {@code (203,144)} & {@code (223,144)},
 * player inventory {@code (8,140)} + hotbar {@code (8,198)}, plus virtual 1,000-slot warehouse view state.
 */
public class CooperationHubMenu extends AbstractContainerMenu {

    public static final int CASH_SLOT_INDEX = 0;
    public static final int UPGRADE_0_INDEX = 1;
    public static final int UPGRADE_1_INDEX = 2;
    public static final int HUB_SLOTS = 3;

    private final BlockPos hubPos;
    @Nullable
    private final CooperationHubBlockEntity blockEntity;
    private final SimpleContainer cashContainer = new SimpleContainer(1);
    private final Container upgradeContainer;

    // Server-tracked warehouse view filter per open menu instance
    private CooperationHubStorage.Zone warehouseZone = CooperationHubStorage.Zone.INBOUND;
    private int warehousePage = 0;
    private String warehouseSearch = "";

    @Nullable
    private S2CHubSyncPacket latestSync;

    public CooperationHubMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    private CooperationHubMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, resolveBe(playerInv, pos), pos);
    }

    public CooperationHubMenu(int containerId, Inventory playerInv, CooperationHubBlockEntity be) {
        this(containerId, playerInv, be, be.getBlockPos());
    }

    private CooperationHubMenu(
            int containerId,
            Inventory playerInv,
            @Nullable CooperationHubBlockEntity be,
            BlockPos pos
    ) {
        super(TyC2ForgeRegistries.COOPERATION_HUB_MENU.get(), containerId);
        this.hubPos = pos;
        this.blockEntity = be;
        this.upgradeContainer = be != null ? be.upgradeContainer : new SimpleContainer(CooperationHubBlockEntity.UPGRADE_SLOTS);

        // Physical cash payment slot (181,144)
        addSlot(new Slot(cashContainer, 0, 181, 144) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CurrencyDenomination.isLegalTender(TyC2Ids.itemId(stack));
            }
        });

        // 2 Upgrade slots (203,144) & (223,144)
        addSlot(new Slot(upgradeContainer, 0, 203, 144) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TriModePowerMath.isSupportedHubUpgrade(TyC2Ids.itemId(stack));
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(upgradeContainer, 1, 223, 144) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TriModePowerMath.isSupportedHubUpgrade(TyC2Ids.itemId(stack));
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Player inventory (8,140) + hotbar (8,198)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    @Nullable
    private static CooperationHubBlockEntity resolveBe(Inventory inv, BlockPos pos) {
        if (inv.player.level != null && inv.player.level.getBlockEntity(pos) instanceof CooperationHubBlockEntity be) {
            return be;
        }
        return null;
    }

    public BlockPos getHubPos() {
        return hubPos;
    }

    @Nullable
    public CooperationHubBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public Container getCashContainer() {
        return cashContainer;
    }

    public CooperationHubStorage.Zone getWarehouseZone() {
        return warehouseZone;
    }

    public int getWarehousePage() {
        return warehousePage;
    }

    public String getWarehouseSearch() {
        return warehouseSearch;
    }

    public void setWarehouseView(CooperationHubStorage.Zone zone, int page, String search) {
        this.warehouseZone = zone == null ? CooperationHubStorage.Zone.INBOUND : zone;
        this.warehousePage = Math.max(0, page);
        this.warehouseSearch = search == null ? "" : search.trim();
    }

    @Nullable
    public S2CHubSyncPacket getLatestSync() {
        return latestSync;
    }

    public void setLatestSync(S2CHubSyncPacket sync) {
        this.latestSync = sync;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Return any coins left in the temporary cash slot back to player
        if (!player.level.isClientSide) {
            clearContainer(player, cashContainer);
        }
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
        if (index < HUB_SLOTS) {
            if (!moveItemStackTo(stack, HUB_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (CurrencyDenomination.isLegalTender(TyC2Ids.itemId(stack))) {
                if (!moveItemStackTo(stack, CASH_SLOT_INDEX, CASH_SLOT_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TriModePowerMath.isSupportedHubUpgrade(TyC2Ids.itemId(stack))) {
                if (!moveItemStackTo(stack, UPGRADE_0_INDEX, UPGRADE_1_INDEX + 1, false)) {
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
