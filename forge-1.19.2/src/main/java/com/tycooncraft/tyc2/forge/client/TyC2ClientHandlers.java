package com.tycooncraft.tyc2.forge.client;

import com.tycooncraft.tyc2.forge.network.S2CAtmSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CMayorSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CTerminalSyncPacket;
import net.minecraft.client.Minecraft;

/**
 * Physical client-only S2C packet dispatchers isolated from dedicated server classloading.
 */
public final class TyC2ClientHandlers {

    private TyC2ClientHandlers() {
    }

    public static void handleAtmSync(S2CAtmSyncPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AtmMachineScreen screen) {
            screen.onSync(pkt);
        }
    }

    public static void handleHubSync(S2CHubSyncPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CooperationHubScreen screen) {
            screen.onSync(pkt);
        }
    }

    public static void handleTerminalSync(S2CTerminalSyncPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CommTerminalScreen screen) {
            screen.onSync(pkt);
        }
    }

    public static void handleMayorSync(S2CMayorSyncPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MayorNegotiationScreen screen) {
            screen.onSync(pkt);
        }
    }
}
