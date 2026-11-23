package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * NeoForge 1.21.1 client setup registering all 5 TyC2 menu screens and
 * {@code tyc2:comm_terminal} item model predicates ({@code tyc2:empty} & {@code tyc2:charged}).
 */
public final class TyC2ClientSetup {

    private TyC2ClientSetup() {
    }

    /** Installs client-only mod event bus listeners on 1.21.1. */
    public static void install(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(TyC2Registries.PRIMITIVE_MINT_MACHINE_MENU.get(), PrimitiveMintMachineScreen::new);
            event.register(TyC2Registries.MINT_MACHINE_MENU.get(), MintMachineScreen::new);
            event.register(TyC2Registries.ATM_MACHINE_MENU.get(), AtmMachineScreen::new);
            event.register(TyC2Registries.COOPERATION_HUB_MENU.get(), CooperationHubScreen::new);
            event.register(TyC2Registries.COMM_TERMINAL_MENU.get(), CommTerminalScreen::new);
            event.register(TyC2Registries.MAYOR_NEGOTIATION_MENU.get(), MayorNegotiationScreen::new);
        });

        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            ItemProperties.register(
                    TyC2Registries.COMM_TERMINAL_ITEM.get(),
                    ResourceLocation.fromNamespaceAndPath(TyC2Constants.MOD_ID, "empty"),
                    (stack, level, entity, seed) -> CommTerminalItem.getEnergyEu(stack) <= 0 ? 1.0F : 0.0F
            );
            ItemProperties.register(
                    TyC2Registries.COMM_TERMINAL_ITEM.get(),
                    ResourceLocation.fromNamespaceAndPath(TyC2Constants.MOD_ID, "charged"),
                    (stack, level, entity, seed) -> CommTerminalItem.getEnergyEu(stack) > 0 ? 1.0F : 0.0F
            );
        }));
    }
}
