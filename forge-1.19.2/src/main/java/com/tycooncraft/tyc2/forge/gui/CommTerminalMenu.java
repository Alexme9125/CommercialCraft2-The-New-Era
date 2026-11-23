package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.network.S2CTerminalSyncPacket;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Remote multi-village management menu for {@code tyc2:comm_terminal} matching
 * {@code gui/comm_terminal.png} (248x210), operating across unloaded chunks.
 */
public class CommTerminalMenu extends AbstractContainerMenu {

    private final int terminalSlot;
    private final Player player;
    @Nullable
    private S2CTerminalSyncPacket latestSync;

    public CommTerminalMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readVarInt());
    }

    public CommTerminalMenu(int containerId, Inventory playerInv, int terminalSlot) {
        super(TyC2ForgeRegistries.COMM_TERMINAL_MENU.get(), containerId);
        this.terminalSlot = terminalSlot;
        this.player = playerInv.player;
    }

    public int getTerminalSlot() {
        return terminalSlot;
    }

    /** Resolves the live {@code tyc2:comm_terminal} {@link ItemStack} held by the player. */
    public ItemStack getTerminalStack() {
        if (terminalSlot == 40) {
            return player.getOffhandItem();
        }
        if (terminalSlot >= 0 && terminalSlot < player.getInventory().getContainerSize()) {
            return player.getInventory().getItem(terminalSlot);
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public S2CTerminalSyncPacket getLatestSync() {
        return latestSync;
    }

    public void setLatestSync(S2CTerminalSyncPacket sync) {
        this.latestSync = sync;
    }

    @Override
    public boolean stillValid(Player player) {
        return getTerminalStack().getItem() instanceof CommTerminalItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
