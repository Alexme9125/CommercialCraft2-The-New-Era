package com.tycooncraft.tyc2.network;

import com.tycooncraft.tyc2.platform.TyC2Native;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-server action payload for ATM, Cooperation Hub, Comm Terminal, and Mayor Negotiation GUIs.
 * Uses explicit structured fields and pure-Java JSON strings (zero reflection).
 */
public record TyC2ActionPayload(
        String screenType,
        BlockPos blockPos,
        String action,
        String payloadJson
) implements CustomPacketPayload {

    public static final Type<TyC2ActionPayload> TYPE = TyC2Native.payloadType("action");

    public static final StreamCodec<RegistryFriendlyByteBuf, TyC2ActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(32), TyC2ActionPayload::screenType,
                    BlockPos.STREAM_CODEC, TyC2ActionPayload::blockPos,
                    ByteBufCodecs.stringUtf8(64), TyC2ActionPayload::action,
                    ByteBufCodecs.stringUtf8(32768), TyC2ActionPayload::payloadJson,
                    TyC2ActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
