package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import java.util.Collections;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for the handheld Commerce Communication Terminal ({@code tyc2:comm_terminal}),
 * providing remote multi-village management across unloaded chunks.
 */
public final class CommTerminalMenu extends AbstractContainerMenu {

    private final boolean offhand;
    private Map<String, Object> syncedSnapshot = Collections.emptyMap();
    private int syncRevision = 0;

    /** Client-side constructor from extra menu buffer. */
    public CommTerminalMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, buf.readBoolean());
    }

    /** Server/Client constructor. */
    public CommTerminalMenu(int syncId, Inventory playerInventory, boolean offhand) {
        super(TyC2Registries.COMM_TERMINAL_MENU.get(), syncId);
        this.offhand = offhand;
    }

    public boolean isOffhand() {
        return offhand;
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
        ItemStack held = offhand ? player.getOffhandItem() : player.getMainHandItem();
        return held.getItem() instanceof CommTerminalItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
