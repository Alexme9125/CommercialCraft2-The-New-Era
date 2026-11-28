package com.tycooncraft.tyc2;

import com.tycooncraft.tyc2.client.TyC2ClientSetup;
import com.tycooncraft.tyc2.command.TyC2Commands;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.compat.TopSoftBridge;
import com.tycooncraft.tyc2.network.TyC2Network;
import com.tycooncraft.tyc2.platform.TyC2ChunkTickets;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary NeoForge entrypoint for TycoonCraft²: The New Era ({@code tyc2}),
 * supporting NeoForge 1.21.1, 26.1.2, and 26.2 with zero hard dependencies on optional mods.
 */
@Mod(TyC2Constants.MOD_ID)
public final class TyC2Mod {

    private static final Logger LOGGER = LoggerFactory.getLogger(TyC2Mod.class);

    public TyC2Mod(IEventBus modBus, Dist side) {
        TyC2Registries.install(modBus);
        TyC2ChunkTickets.install(modBus);
        TyC2Native.registerCapabilities(modBus);
        TyC2Network.install(modBus, side);
        TopSoftBridge.install(modBus);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        TyC2DataLoader.install(gameBus);
        TyC2WorldManager.install(gameBus);
        TyC2Commands.install(gameBus);

        if (side == Dist.CLIENT) {
            TyC2ClientSetup.install(modBus);
        }

        LOGGER.info(
                "Monetera initialized (optional compat: mhera={}, ecera={}, trera={})",
                Ic2SoftBridge.isIc2Loaded(),
                ModList.get() != null && ModList.get().isLoaded(TyC2Constants.MOD_ECC2),
                Lc2SoftBridge.isLc2Loaded()
        );
    }
}
