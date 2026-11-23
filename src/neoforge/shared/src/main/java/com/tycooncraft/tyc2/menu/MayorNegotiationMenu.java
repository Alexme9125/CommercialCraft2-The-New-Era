package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for Village Mayor Partnership Negotiation ({@code tyc2:mayor_negotiation}).
 * Tracks the negotiating Mayor entity UUID so the Mayor halts and faces the player while open
 * and resumes normal plaza behavior as soon as the menu closes.
 */
public final class MayorNegotiationMenu extends AbstractContainerMenu {

    private final UUID villageId;
    private final BlockPos villagePos;
    private final UUID mayorEntityUuid;
    private Map<String, Object> syncedSnapshot = Collections.emptyMap();
    private int syncRevision = 0;

    /** Client-side constructor from extra menu buffer (backwards-compatible optional Mayor UUID). */
    public MayorNegotiationMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(
                syncId,
                playerInventory,
                buf.readUUID(),
                buf.readBlockPos(),
                buf.isReadable() && buf.readBoolean() ? buf.readUUID() : null
        );
    }

    /** Backwards-compatible server/client constructor without explicit Mayor entity UUID. */
    public MayorNegotiationMenu(
            int syncId,
            Inventory playerInventory,
            UUID villageId,
            BlockPos villagePos
    ) {
        this(syncId, playerInventory, villageId, villagePos, null);
    }

    /** Canonical server/client constructor carrying the negotiating Mayor's entity UUID. */
    public MayorNegotiationMenu(
            int syncId,
            Inventory playerInventory,
            UUID villageId,
            BlockPos villagePos,
            UUID mayorEntityUuid
    ) {
        super(TyC2Registries.MAYOR_NEGOTIATION_MENU.get(), syncId);
        this.villageId = villageId;
        this.villagePos = villagePos;
        this.mayorEntityUuid = mayorEntityUuid;
    }

    public UUID villageId() {
        return villageId;
    }

    public BlockPos villagePos() {
        return villagePos;
    }

    public UUID mayorEntityUuid() {
        return mayorEntityUuid;
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
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            if (mayorEntityUuid != null) {
                TyC2WorldManager.endMayorNegotiation(serverPlayer, mayorEntityUuid);
            } else {
                TyC2WorldManager.endAllNegotiationsForPlayer(serverPlayer);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                villagePos.getX() + 0.5,
                villagePos.getY() + 0.5,
                villagePos.getZ() + 0.5
        ) <= 64.0 * 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
