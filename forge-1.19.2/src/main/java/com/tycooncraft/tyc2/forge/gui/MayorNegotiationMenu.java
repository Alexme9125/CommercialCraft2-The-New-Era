package com.tycooncraft.tyc2.forge.gui;

import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.network.S2CMayorSyncPacket;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Village Partnership Negotiation menu opened by right-clicking an unpartnered Village Mayor,
 * matching {@code gui/mayor_negotiation.png} (220x192). Carries the Mayor entity UUID so the
 * server holds the Mayor stationary and facing the negotiating player until closed.
 */
public class MayorNegotiationMenu extends AbstractContainerMenu {

    private final UUID villageId;
    @Nullable
    private final UUID mayorEntityUuid;
    @Nullable
    private S2CMayorSyncPacket latestSync;

    public MayorNegotiationMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(
                containerId,
                playerInv,
                extraData.readUUID(),
                extraData.readableBytes() > 0 && extraData.readBoolean() ? extraData.readUUID() : null
        );
    }

    public MayorNegotiationMenu(int containerId, Inventory playerInv, UUID villageId) {
        this(containerId, playerInv, villageId, null);
    }

    public MayorNegotiationMenu(
            int containerId,
            Inventory playerInv,
            UUID villageId,
            @Nullable UUID mayorEntityUuid
    ) {
        super(TyC2ForgeRegistries.MAYOR_NEGOTIATION_MENU.get(), containerId);
        this.villageId = villageId;
        this.mayorEntityUuid = mayorEntityUuid;
    }

    public UUID getVillageId() {
        return villageId;
    }

    @Nullable
    public UUID getMayorEntityUuid() {
        return mayorEntityUuid;
    }

    @Nullable
    public S2CMayorSyncPacket getLatestSync() {
        return latestSync;
    }

    public void setLatestSync(S2CMayorSyncPacket sync) {
        this.latestSync = sync;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            TycoonForgeWorldManager.endMayorNegotiation(serverPlayer, mayorEntityUuid);
        }
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
