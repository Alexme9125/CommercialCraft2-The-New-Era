package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.network.S2CMayorSyncPacket;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Village Partnership Negotiation menu opened by right-clicking an unpartnered Village Mayor,
 * matching {@code gui/mayor_negotiation.png} (220x192).
 */
public class MayorNegotiationMenu extends AbstractContainerMenu {

    private final UUID villageId;
    @Nullable
    private S2CMayorSyncPacket latestSync;

    public MayorNegotiationMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readUUID());
    }

    public MayorNegotiationMenu(int containerId, Inventory playerInv, UUID villageId) {
        super(TyC2ForgeRegistries.MAYOR_NEGOTIATION_MENU.get(), containerId);
        this.villageId = villageId;
    }

    public UUID getVillageId() {
        return villageId;
    }

    @Nullable
    public S2CMayorSyncPacket getLatestSync() {
        return latestSync;
    }

    public void setLatestSync(S2CMayorSyncPacket sync) {
        this.latestSync = sync;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
