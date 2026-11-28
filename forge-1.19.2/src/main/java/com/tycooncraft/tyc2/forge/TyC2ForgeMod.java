package com.tycooncraft.tyc2.forge;

import com.tycooncraft.tyc2.forge.client.TyC2ForgeClient;
import com.tycooncraft.tyc2.forge.command.TyC2ForgeCommand;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.forge.compat.TopSoftBridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2ChunkTickets;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.19.2 entrypoint for TycoonCraft²: The New Era ({@code tyc2}).
 * Boots cleanly in pure Vanilla Minecraft while unlocking optional IC2, LC2,
 * Serene/Ecliptic Seasons, Farmer's Delight, Kaleidoscope Cookery, TOP, Jade, and JEI bridges.
 */
@Mod(TyC2Ids.MOD_ID)
public final class TyC2ForgeMod {

    public static final Logger LOGGER = LoggerFactory.getLogger(TyC2Ids.MOD_ID);

    public TyC2ForgeMod(FMLJavaModLoadingContext context) {
        TyC2RuntimeData.bootstrapExternalConfig();

        IEventBus modBus = context.getModEventBus();
        TyC2ForgeRegistries.register(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onInterModEnqueue);

        MinecraftForge.EVENT_BUS.register(TycoonForgeWorldManager.class);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            TyC2ForgeClient.install(modBus);
        }

        LOGGER.info(
                "Monetera (Forge 1.19.2) initialized [mhera={}, ecera={}, trera={}, seasons={}].",
                Ic2Bridge.isLoaded(),
                ModList.get().isLoaded("ecera"),
                Lc2SoftBridge.isLoaded(),
                SeasonSoftBridge.isAnySeasonModLoaded()
        );
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TyC2ForgeNetwork.register();
            TyC2ChunkTickets.registerValidationCallback();
            TyC2RuntimeData.refreshModdedProfessions();
        });
    }

    private void onInterModEnqueue(InterModEnqueueEvent event) {
        TopSoftBridge.enqueueImc();
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(TyC2RuntimeData.createReloadListener());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TyC2ForgeCommand.register(event.getDispatcher());
    }
}
