package com.tycooncraft.tyc2.network;

import com.tycooncraft.tyc2.platform.TyC2Native;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-to-client state sync payload carrying live snapshots for ATM, Cooperation Hub,
 * Comm Terminal, and Mayor Negotiation screens (including 48-point CPI trend history and
 * paginated 1,000-slot warehouse views).
 */
public record TyC2SyncPayload(
        String screenType,
        BlockPos blockPos,
        String payloadJson
) implements CustomPacketPayload {

    public static final Type<TyC2SyncPayload> TYPE = TyC2Native.payloadType("sync");

    public static final StreamCodec<RegistryFriendlyByteBuf, TyC2SyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(32), TyC2SyncPayload::screenType,
                    BlockPos.STREAM_CODEC, TyC2SyncPayload::blockPos,
                    ByteBufCodecs.stringUtf8(262144), TyC2SyncPayload::payloadJson,
                    TyC2SyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
