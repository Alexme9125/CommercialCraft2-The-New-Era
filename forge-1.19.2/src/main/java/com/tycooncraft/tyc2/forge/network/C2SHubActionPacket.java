package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.gui.CooperationHubMenu;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * C2S action packet sent from {@link com.tycooncraft.tyc2.forge.client.CooperationHubScreen}.
 */
public final class C2SHubActionPacket {

    public enum Action {
        REQUEST_SYNC,
        BUY_FROM_VILLAGE,
        SELL_TO_VILLAGE,
        TOGGLE_ORDER_AUTO,
        SET_WAREHOUSE_VIEW,
        WAREHOUSE_SLOT_CLICK,
        WAREHOUSE_DEPOSIT_HELD,
        UPGRADE_VILLAGE,
        DELIVER_EXPANSION,
        TOGGLE_EXPANSION_AUTO,
        TOGGLE_EXPANSION_PAUSE,
        RENAME_VILLAGE,
        RELOCATE_HUB,
        TERMINATE_PARTNERSHIP
    }

    public final BlockPos hubPos;
    public final Action action;
    public final int intArgA;
    public final int intArgB;
    public final boolean flagArg;
    public final String textArg;

    public C2SHubActionPacket(
            BlockPos hubPos, Action action, int intArgA, int intArgB, boolean flagArg, String textArg
    ) {
        this.hubPos = hubPos;
        this.action = action;
        this.intArgA = intArgA;
        this.intArgB = intArgB;
        this.flagArg = flagArg;
        this.textArg = textArg == null ? "" : textArg;
    }

    public static C2SHubActionPacket simple(BlockPos hubPos, Action action) {
        return new C2SHubActionPacket(hubPos, action, 0, 0, false, "");
    }

    public static void encode(C2SHubActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.hubPos);
        buf.writeEnum(pkt.action);
        buf.writeVarInt(pkt.intArgA);
        buf.writeVarInt(pkt.intArgB);
        buf.writeBoolean(pkt.flagArg);
        buf.writeUtf(pkt.textArg, 96);
    }

    public static C2SHubActionPacket decode(FriendlyByteBuf buf) {
        return new C2SHubActionPacket(
                buf.readBlockPos(),
                buf.readEnum(Action.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(96)
        );
    }

    public static void handle(C2SHubActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        if (!(player.level.getBlockEntity(pkt.hubPos) instanceof CooperationHubBlockEntity hubBe)
                || !hubBe.stillValid(player)) {
            ctx.setPacketHandled(true);
            return;
        }
        UUID vid = hubBe.resolveVillageId();
        if (vid == null) {
            ctx.setPacketHandled(true);
            return;
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(vid);
        if (vOpt.isEmpty()) {
            ctx.setPacketHandled(true);
            return;
        }
        VillageRecord village = vOpt.get();
        CooperationHubMenu menu = player.containerMenu instanceof CooperationHubMenu m ? m : null;
        Component feedback = Component.empty();
        boolean closeScreen = false;

        switch (pkt.action) {
            case REQUEST_SYNC -> feedback = Component.empty();
            case BUY_FROM_VILLAGE -> {
                var res = TycoonForgeWorldManager.executeSellOrder(
                        player, vid, pkt.textArg, Math.max(1, pkt.intArgA),
                        pkt.flagArg, menu != null ? menu.getCashContainer() : null
                );
                feedback = res.toComponent();
            }
            case SELL_TO_VILLAGE -> {
                var res = TycoonForgeWorldManager.executeBuyOrder(
                        player, vid, pkt.textArg, Math.max(1, pkt.intArgA), pkt.flagArg, true
                );
                feedback = res.toComponent();
            }
            case TOGGLE_ORDER_AUTO -> {
                TycoonForgeWorldManager.toggleOrderAutoFulfill(player.getServer(), vid, pkt.textArg);
            }
            case SET_WAREHOUSE_VIEW -> {
                if (menu != null) {
                    menu.setWarehouseView(
                            pkt.flagArg ? CooperationHubStorage.Zone.OUTBOUND : CooperationHubStorage.Zone.INBOUND,
                            pkt.intArgA,
                            pkt.textArg
                    );
                }
            }
            case WAREHOUSE_SLOT_CLICK -> {
                int absSlot = pkt.intArgA;
                boolean rightClick = pkt.flagArg;
                if (absSlot >= 0 && absSlot < CooperationHubStorage.TOTAL_SLOTS) {
                    CooperationHubStorage storage = village.getStorage();
                    ItemStack carried = menu != null ? menu.getCarried() : ItemStack.EMPTY;
                    if (!carried.isEmpty() && CooperationHubStorage.Zone.INBOUND.containsSlot(absSlot)) {
                        int toPut = rightClick ? 1 : carried.getCount();
                        int rem = storage.playerInsertIntoInbound(
                                absSlot,
                                TyC2Ids.itemId(carried),
                                toPut,
                                TyC2Ids.nbtJson(carried),
                                carried.getMaxStackSize()
                        );
                        int inserted = toPut - rem;
                        if (inserted > 0) {
                            carried.shrink(inserted);
                            if (menu != null) {
                                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                            }
                            data.setDirty();
                        }
                    } else {
                        int maxTake = rightClick ? 1 : 64;
                        Optional<SlotStack> taken = storage.playerExtract(absSlot, maxTake);
                        if (taken.isPresent()) {
                            ItemStack stack = TyC2Ids.fromSlotStack(taken.get());
                            if (!stack.isEmpty()) {
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                                data.setDirty();
                            }
                        }
                    }
                }
            }
            case WAREHOUSE_DEPOSIT_HELD -> {
                if (menu != null && !menu.getCarried().isEmpty()) {
                    ItemStack carried = menu.getCarried();
                    int rem = village.getStorage().insertExternal(
                            TyC2Ids.itemId(carried),
                            carried.getCount(),
                            TyC2Ids.nbtJson(carried),
                            carried.getMaxStackSize(),
                            false
                    );
                    if (rem < carried.getCount()) {
                        carried.setCount(rem);
                        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                        data.setDirty();
                    }
                }
            }
            case UPGRADE_VILLAGE -> {
                feedback = TycoonForgeWorldManager.upgradeVillage(player, vid).toComponent();
            }
            case DELIVER_EXPANSION -> {
                feedback = TycoonForgeWorldManager.deliverExpansionMaterials(player, vid, true).toComponent();
            }
            case TOGGLE_EXPANSION_AUTO -> {
                ExpansionDesire exp = village.getActiveExpansion();
                if (exp != null) {
                    village.setActiveExpansion(exp.withAutoFulfill(!exp.autoFulfill()));
                    data.setDirty();
                }
            }
            case TOGGLE_EXPANSION_PAUSE -> {
                village.setExpansionPaused(!village.isExpansionPaused());
                data.setDirty();
            }
            case RENAME_VILLAGE -> {
                if (!TycoonForgeWorldManager.canManageVillage(player, data, village)) {
                    feedback = Component.translatable("status.tyc2.hub.not_owner");
                } else {
                    String cleaned = pkt.textArg.trim();
                    if (!cleaned.isEmpty()) {
                        village.setVillageName(cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned);
                        data.setDirty();
                    }
                }
            }
            case RELOCATE_HUB -> {
                var res = TycoonForgeWorldManager.relocateHub(player, vid);
                feedback = res.toComponent();
                player.sendSystemMessage(res.toComponent());
                if (res.success()) {
                    closeScreen = true;
                }
            }
            case TERMINATE_PARTNERSHIP -> {
                var res = TycoonForgeWorldManager.terminatePartnership(player, vid);
                feedback = res.toComponent();
                player.sendSystemMessage(res.toComponent());
                if (res.success()) {
                    closeScreen = true;
                }
            }
        }

        if (closeScreen) {
            player.closeContainer();
        } else {
            CooperationHubStorage.Zone zone = menu != null ? menu.getWarehouseZone() : CooperationHubStorage.Zone.INBOUND;
            int page = menu != null ? menu.getWarehousePage() : 0;
            String search = menu != null ? menu.getWarehouseSearch() : "";
            TyC2ForgeNetwork.sendToPlayer(
                    player,
                    S2CHubSyncPacket.buildFor(player, village, hubBe, zone, page, search, feedback)
            );
        }
        ctx.setPacketHandled(true);
    }
}
