package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/**
 * C2S negotiation packet sent from {@link com.tycooncraft.tyc2.forge.client.MayorNegotiationScreen}.
 */
public final class C2SMayorNegotiatePacket {

    public final UUID villageId;
    public final boolean sign;

    public C2SMayorNegotiatePacket(UUID villageId, boolean sign) {
        this.villageId = villageId;
        this.sign = sign;
    }

    public static void encode(C2SMayorNegotiatePacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.villageId);
        buf.writeBoolean(pkt.sign);
    }

    public static C2SMayorNegotiatePacket decode(FriendlyByteBuf buf) {
        return new C2SMayorNegotiatePacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(C2SMayorNegotiatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null
                || !(player.containerMenu instanceof MayorNegotiationMenu menu)
                || !pkt.villageId.equals(menu.getVillageId())) {
            ctx.setPacketHandled(true);
            return;
        }
        Component fb = Component.empty();
        if (pkt.sign) {
            TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
            Optional<VillageRecord> vOpt = data.getVillage(pkt.villageId);
            if (vOpt.isEmpty()
                    || !player.getLevel().dimension().location().toString().equals(vOpt.get().getDimensionId())) {
                ctx.setPacketHandled(true);
                return;
            }
            UUID mayorUuid = menu.getMayorEntityUuid() != null
                    ? menu.getMayorEntityUuid()
                    : vOpt.get().getMayorState().getMayorEntityUuid();
            Entity mayor = mayorUuid != null ? player.getLevel().getEntity(mayorUuid) : null;
            if (mayor == null || !mayor.isAlive() || mayor.distanceToSqr(player) > 100.0) {
                ctx.setPacketHandled(true);
                return;
            }
            TycoonForgeWorldManager.ActionFeedback res = TycoonForgeWorldManager.signPartnership(player, pkt.villageId);
            fb = res.toComponent();
            if (res.success()) {
                player.sendSystemMessage(res.toComponent().copy().withStyle(ChatFormatting.GREEN));
            }
        }
        TyC2ForgeNetwork.sendToPlayer(player, S2CMayorSyncPacket.buildFor(player, pkt.villageId, fb));
        ctx.setPacketHandled(true);
    }
}
