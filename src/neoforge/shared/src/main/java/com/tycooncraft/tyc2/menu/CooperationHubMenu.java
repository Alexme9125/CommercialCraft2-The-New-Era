package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.TriModePowerMath;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for the Village Cooperation Hub ({@code tyc2:cooperation_hub}),
 * aligned pixel-for-pixel with {@code gui/cooperation_hub.png} (248x222).
 */
public final class CooperationHubMenu extends AbstractContainerMenu {

    private final Container localContainer;
    private final SimpleContainer cashContainer = new SimpleContainer(1);
    private final BlockPos pos;
    private Map<String, Object> syncedSnapshot = Collections.emptyMap();
    private int syncRevision = 0;

    /** Client-side constructor from extra menu buffer. */
    public CooperationHubMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, buf.readBlockPos());
    }

    private CooperationHubMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, resolveLocalContainer(playerInventory, pos), pos);
    }

    /** Server-side constructor backed by live {@link CooperationHubBlockEntity}. */
    public CooperationHubMenu(int syncId, Inventory playerInventory, CooperationHubBlockEntity hub) {
        this(syncId, playerInventory, hub.localContainer(), hub.getBlockPos());
    }

    private CooperationHubMenu(
            int syncId,
            Inventory playerInventory,
            Container localContainer,
            BlockPos pos
    ) {
        super(TyC2Registries.COOPERATION_HUB_MENU.get(), syncId);
        this.localContainer = localContainer;
        this.pos = pos;

        // Per-player Cash Slot (181, 144): isolated from concurrent Hub users and returned on menu close
        addSlot(new Slot(cashContainer, 0, 181, 144) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CurrencyDenomination.isLegalTender(TyC2Native.itemId(stack));
            }
        });

        // Local Slots 1 & 2: Hub Upgrade Slots (203, 144) and (223, 144), max 1 per slot
        addSlot(new Slot(localContainer, CooperationHubBlockEntity.LOCAL_SLOT_UPGRADE_0, 203, 144) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TriModePowerMath.isSupportedHubUpgrade(TyC2Native.itemId(stack));
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(localContainer, CooperationHubBlockEntity.LOCAL_SLOT_UPGRADE_1, 223, 144) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TriModePowerMath.isSupportedHubUpgrade(TyC2Native.itemId(stack));
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Player Inventory (8, 140) + Hotbar (8, 198)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    private static Container resolveLocalContainer(Inventory inv, BlockPos pos) {
        if (inv.player != null && inv.player.level().getBlockEntity(pos) instanceof CooperationHubBlockEntity be) {
            return be.localContainer();
        }
        return new SimpleContainer(CooperationHubBlockEntity.LOCAL_INVENTORY_SIZE);
    }

    public BlockPos blockPos() {
        return pos;
    }

    /** Returns the per-player isolated physical cash slot container for this open menu. */
    public Container cashContainer() {
        return cashContainer;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, cashContainer);
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
        return localContainer.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int localCount = CooperationHubBlockEntity.LOCAL_INVENTORY_SIZE;

        if (index < localCount) {
            if (!moveItemStackTo(stack, localCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (CurrencyDenomination.isLegalTender(TyC2Native.itemId(stack))) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (TriModePowerMath.isSupportedHubUpgrade(TyC2Native.itemId(stack))) {
            if (!moveItemStackTo(stack, 1, localCount, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
