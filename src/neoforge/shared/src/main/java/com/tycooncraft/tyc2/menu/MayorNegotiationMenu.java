package com.tycooncraft.tyc2.menu;

import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.core.JsonLite;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for Village Mayor Partnership Negotiation ({@code tyc2:mayor_negotiation}).
 */
public final class MayorNegotiationMenu extends AbstractContainerMenu {

    private final UUID villageId;
    private final BlockPos villagePos;
    private Map<String, Object> syncedSnapshot = Collections.emptyMap();
    private int syncRevision = 0;

    /** Client-side constructor from extra menu buffer. */
    public MayorNegotiationMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, buf.readUUID(), buf.readBlockPos());
    }

    /** Server/Client constructor. */
    public MayorNegotiationMenu(
            int syncId,
            Inventory playerInventory,
            UUID villageId,
            BlockPos villagePos
    ) {
        super(TyC2Registries.MAYOR_NEGOTIATION_MENU.get(), syncId);
        this.villageId = villageId;
        this.villagePos = villagePos;
    }

    public UUID villageId() {
        return villageId;
    }

    public BlockPos villagePos() {
        return villagePos;
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
