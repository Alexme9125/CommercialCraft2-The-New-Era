package com.tycooncraft.tyc2.network;

import com.tycooncraft.tyc2.menu.AtmMachineMenu;
import com.tycooncraft.tyc2.menu.CommTerminalMenu;
import com.tycooncraft.tyc2.menu.CooperationHubMenu;
import com.tycooncraft.tyc2.menu.MayorNegotiationMenu;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Registers and routes TyC2 C2S action packets and S2C GUI synchronization packets.
 */
public final class TyC2Network {

    private static final String PROTOCOL_VERSION = "1";

    private TyC2Network() {
    }

    /** Registers payloads on the mod event bus across NeoForge 1.21.1 and 26.x. */
    public static void install(IEventBus modBus, Dist side) {
        TyC2Native.registerPayloads(
                modBus,
                side,
                PROTOCOL_VERSION,
                TyC2ActionPayload.TYPE,
                TyC2ActionPayload.STREAM_CODEC,
                TyC2Network::handleServerAction,
                TyC2SyncPayload.TYPE,
                TyC2SyncPayload.STREAM_CODEC,
                TyC2Network::handleClientSync
        );
    }

    private static void handleServerAction(TyC2ActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        TyC2WorldManager.handleActionPayload(player, level, payload);
    }

    private static void handleClientSync(TyC2SyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        switch (payload.screenType()) {
            case "ATM" -> {
                if (player.containerMenu instanceof AtmMachineMenu menu) {
                    menu.applyClientSync(payload.payloadJson());
                }
            }
            case "HUB" -> {
                if (player.containerMenu instanceof CooperationHubMenu menu) {
                    menu.applyClientSync(payload.payloadJson());
                }
            }
            case "TERMINAL" -> {
                if (player.containerMenu instanceof CommTerminalMenu menu) {
                    menu.applyClientSync(payload.payloadJson());
                }
            }
            case "MAYOR" -> {
                if (player.containerMenu instanceof MayorNegotiationMenu menu) {
                    menu.applyClientSync(payload.payloadJson());
                }
            }
            default -> {
            }
        }
    }
}
