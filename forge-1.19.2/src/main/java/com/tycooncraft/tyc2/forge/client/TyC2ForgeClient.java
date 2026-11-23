package com.tycooncraft.tyc2.forge.client;

import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Physical client bootstrap registering all 6 TyC2 container screens and item model predicates
 * ({@code tyc2:empty} and {@code tyc2:charged}) for {@code tyc2:comm_terminal}.
 */
public final class TyC2ForgeClient {

    private TyC2ForgeClient() {
    }

    public static void install(IEventBus modBus) {
        modBus.addListener(TyC2ForgeClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_MENU.get(), PrimitiveMintMachineScreen::new);
            MenuScreens.register(TyC2ForgeRegistries.MINT_MACHINE_MENU.get(), MintMachineScreen::new);
            MenuScreens.register(TyC2ForgeRegistries.ATM_MACHINE_MENU.get(), AtmMachineScreen::new);
            MenuScreens.register(TyC2ForgeRegistries.COOPERATION_HUB_MENU.get(), CooperationHubScreen::new);
            MenuScreens.register(TyC2ForgeRegistries.COMM_TERMINAL_MENU.get(), CommTerminalScreen::new);
            MenuScreens.register(TyC2ForgeRegistries.MAYOR_NEGOTIATION_MENU.get(), MayorNegotiationScreen::new);

            // Register BOTH tyc2:empty (used by datagen model) and tyc2:charged for full compatibility
            ItemProperties.register(
                    TyC2ForgeRegistries.COMM_TERMINAL.get(),
                    TyC2Ids.id("empty"),
                    (stack, level, entity, seed) -> CommTerminalItem.getStoredEu(stack) <= 0 ? 1.0F : 0.0F
            );
            ItemProperties.register(
                    TyC2ForgeRegistries.COMM_TERMINAL.get(),
                    TyC2Ids.id("charged"),
                    (stack, level, entity, seed) -> CommTerminalItem.getStoredEu(stack) > 0 ? 1.0F : 0.0F
            );
        });
    }
}
