package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 1.19.2 {@link SimpleChannel} networking registry for TycoonCraft².
 */
public final class TyC2ForgeNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            TyC2Ids.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered = false;

    private TyC2ForgeNetwork() {
    }

    /** Registers all C2S and S2C packets once during common setup. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;

        CHANNEL.messageBuilder(C2SAtmActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAtmActionPacket::encode)
                .decoder(C2SAtmActionPacket::decode)
                .consumerMainThread(C2SAtmActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CAtmSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CAtmSyncPacket::encode)
                .decoder(S2CAtmSyncPacket::decode)
                .consumerMainThread(S2CAtmSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SHubActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SHubActionPacket::encode)
                .decoder(C2SHubActionPacket::decode)
                .consumerMainThread(C2SHubActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CHubSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CHubSyncPacket::encode)
                .decoder(S2CHubSyncPacket::decode)
                .consumerMainThread(S2CHubSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2STerminalActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STerminalActionPacket::encode)
                .decoder(C2STerminalActionPacket::decode)
                .consumerMainThread(C2STerminalActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CTerminalSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CTerminalSyncPacket::encode)
                .decoder(S2CTerminalSyncPacket::decode)
                .consumerMainThread(S2CTerminalSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SMayorNegotiatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SMayorNegotiatePacket::encode)
                .decoder(C2SMayorNegotiatePacket::decode)
                .consumerMainThread(C2SMayorNegotiatePacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CMayorSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMayorSyncPacket::encode)
                .decoder(S2CMayorSyncPacket::decode)
                .consumerMainThread(S2CMayorSyncPacket::handle)
                .add();
    }

    /** Sends a C2S packet from client to server. */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** Sends an S2C packet to a specific player. */
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
