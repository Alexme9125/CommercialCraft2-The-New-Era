package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.forge.gui.CommTerminalMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * C2S remote management action sent from {@link com.tycooncraft.tyc2.forge.client.CommTerminalScreen}.
 */
public final class C2STerminalActionPacket {

    public enum Action {
        REQUEST_SYNC,
        SELECT_VILLAGE,
        UNBIND_VILLAGE,
        BUY_FROM_VILLAGE,
        SELL_TO_VILLAGE,
        TOGGLE_ORDER_AUTO,
        UPGRADE_VILLAGE,
        DELIVER_EXPANSION,
        TOGGLE_EXPANSION_AUTO,
        TOGGLE_EXPANSION_PAUSE,
        START_AID,
        CANCEL_AID
    }

    public final Action action;
    public final int intArg;
    public final String textArg;

    public C2STerminalActionPacket(Action action, int intArg, String textArg) {
        this.action = action;
        this.intArg = intArg;
        this.textArg = textArg == null ? "" : textArg;
    }

    public static C2STerminalActionPacket simple(Action action) {
        return new C2STerminalActionPacket(action, 0, "");
    }

    public static void encode(C2STerminalActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeVarInt(pkt.intArg);
        buf.writeUtf(pkt.textArg, 96);
    }

    public static C2STerminalActionPacket decode(FriendlyByteBuf buf) {
        return new C2STerminalActionPacket(
                buf.readEnum(Action.class),
                buf.readVarInt(),
                buf.readUtf(96)
        );
    }

    public static void handle(C2STerminalActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null || !(player.containerMenu instanceof CommTerminalMenu menu)) {
            ctx.setPacketHandled(true);
            return;
        }
        ItemStack term = menu.getTerminalStack();
        if (!(term.getItem() instanceof CommTerminalItem)) {
            ctx.setPacketHandled(true);
            return;
        }

        List<UUID> bound = CommTerminalItem.getBoundVillages(term);
        int activeIdx = Math.min(CommTerminalItem.getActiveIndex(term), Math.max(0, bound.size() - 1));
        UUID activeVillageId = bound.isEmpty() ? null : bound.get(activeIdx);
        Component feedback = Component.empty();

        if (activeVillageId != null
                && pkt.action != Action.REQUEST_SYNC
                && pkt.action != Action.SELECT_VILLAGE
                && pkt.action != Action.UNBIND_VILLAGE) {
            TycoonForgeSavedData authData = TycoonForgeSavedData.get(player.getServer());
            Optional<VillageRecord> authOpt = authData.getVillage(activeVillageId);
            if (authOpt.isPresent() && !TycoonForgeWorldManager.canAccessVillage(player, authData, authOpt.get())) {
                TyC2ForgeNetwork.sendToPlayer(
                        player,
                        S2CTerminalSyncPacket.buildFor(player, term, Component.translatable("status.tyc2.hub.not_owner"))
                );
                ctx.setPacketHandled(true);
                return;
            }
        }

        switch (pkt.action) {
            case REQUEST_SYNC -> feedback = Component.empty();
            case SELECT_VILLAGE -> {
                if (pkt.intArg >= 0 && pkt.intArg < bound.size()) {
                    CommTerminalItem.setActiveIndex(term, pkt.intArg);
                }
            }
            case UNBIND_VILLAGE -> {
                if (activeVillageId != null) {
                    CommTerminalItem.unbindVillage(term, activeVillageId);
                }
            }
            case BUY_FROM_VILLAGE -> {
                if (activeVillageId != null) {
                    var res = TycoonForgeWorldManager.executeSellOrder(
                            player, activeVillageId, pkt.textArg, Math.max(1, pkt.intArg), true, null, term
                    );
                    feedback = res.toComponent();
                }
            }
            case SELL_TO_VILLAGE -> {
                if (activeVillageId != null) {
                    var res = TycoonForgeWorldManager.executeBuyOrder(
                            player, activeVillageId, pkt.textArg, Math.max(1, pkt.intArg), true, false, term
                    );
                    feedback = res.toComponent();
                }
            }
            case TOGGLE_ORDER_AUTO -> {
                if (activeVillageId != null) {
                    TycoonForgeWorldManager.toggleOrderAutoFulfill(player.getServer(), activeVillageId, pkt.textArg);
                }
            }
            case UPGRADE_VILLAGE -> {
                if (activeVillageId != null) {
                    feedback = TycoonForgeWorldManager.upgradeVillage(player, activeVillageId, term).toComponent();
                }
            }
            case DELIVER_EXPANSION -> {
                if (activeVillageId != null) {
                    feedback = TycoonForgeWorldManager.deliverExpansionMaterials(player, activeVillageId, false, term)
                            .toComponent();
                }
            }
            case TOGGLE_EXPANSION_AUTO -> {
                if (activeVillageId != null) {
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
                    Optional<VillageRecord> vOpt = data.getVillage(activeVillageId);
                    if (vOpt.isPresent() && vOpt.get().getActiveExpansion() != null) {
                        ExpansionDesire exp = vOpt.get().getActiveExpansion();
                        vOpt.get().setActiveExpansion(exp.withAutoFulfill(!exp.autoFulfill()));
                        data.setDirty();
                    }
                }
            }
            case TOGGLE_EXPANSION_PAUSE -> {
                if (activeVillageId != null) {
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
                    data.getVillage(activeVillageId).ifPresent(v -> {
                        v.setExpansionPaused(!v.isExpansionPaused());
                        data.setDirty();
                    });
                }
            }
            case START_AID -> {
                if (activeVillageId != null) {
                    WorkplaceSize size = pkt.intArg == 1 ? WorkplaceSize.LARGE : WorkplaceSize.SMALL;
                    feedback = TycoonForgeWorldManager.startAidConstruction(
                            player, activeVillageId, pkt.textArg, size, true, null, term
                    ).toComponent();
                }
            }
            case CANCEL_AID -> {
                if (activeVillageId != null) {
                    feedback = TycoonForgeWorldManager.cancelAidConstruction(
                            player, activeVillageId, pkt.textArg, false
                    ).toComponent();
                }
            }
        }

        TyC2ForgeNetwork.sendToPlayer(player, S2CTerminalSyncPacket.buildFor(player, term, feedback));
        ctx.setPacketHandled(true);
    }
}
