package com.tycooncraft.tyc2.qa;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * QA-only Forge {@link SimpleChannel} coordinating the real Forge 1.19.2 graphical client
 * and dedicated server during the live client-server joint test.
 */
final class TyC2LiveNetwork {

    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;

    private TyC2LiveNetwork() {
    }

    static synchronized void register() {
        if (channel != null) {
            return;
        }
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(TyC2QaMod.MOD_ID, "live_ctrl"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );
        int id = 0;
        channel.messageBuilder(S2CLiveDirective.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLiveDirective::encode)
                .decoder(S2CLiveDirective::decode)
                .consumerMainThread(S2CLiveDirective::handle)
                .add();
        channel.messageBuilder(C2SLiveAck.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SLiveAck::encode)
                .decoder(C2SLiveAck::decode)
                .consumerMainThread(C2SLiveAck::handle)
                .add();
    }

    static void sendToClient(ServerPlayer player, S2CLiveDirective packet) {
        if (channel != null && player != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    static void sendToServer(C2SLiveAck packet) {
        if (channel != null) {
            channel.sendToServer(packet);
        }
    }

    record S2CLiveDirective(
            int stepId,
            String action,
            BlockPos targetPos,
            int entityId,
            String argText
    ) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(stepId);
            buf.writeUtf(action == null ? "" : action, 64);
            buf.writeBlockPos(targetPos == null ? BlockPos.ZERO : targetPos);
            buf.writeVarInt(entityId);
            buf.writeUtf(argText == null ? "" : argText, 256);
        }

        static S2CLiveDirective decode(FriendlyByteBuf buf) {
            return new S2CLiveDirective(
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readUtf(256)
            );
        }

        static void handle(S2CLiveDirective pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> TyC2LiveClientDriver.onDirective(pkt)
            ));
            ctx.setPacketHandled(true);
        }
    }

    record C2SLiveAck(
            int stepId,
            boolean success,
            String summary
    ) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(stepId);
            buf.writeBoolean(success);
            buf.writeUtf(summary == null ? "" : summary, 512);
        }

        static C2SLiveAck decode(FriendlyByteBuf buf) {
            return new C2SLiveAck(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(512)
            );
        }

        static void handle(C2SLiveAck pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> TyC2LiveServerRunner.onClientAck(pkt));
            ctx.setPacketHandled(true);
        }
    }
}
