package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.TyC2Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * NeoForge 26.x client setup registering all 5 TyC2 menu screens.
 */
public final class TyC2ClientSetup {

    private TyC2ClientSetup() {
    }

    /** Installs client-only mod event bus listeners on 26.x. */
    public static void install(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(TyC2Registries.PRIMITIVE_MINT_MACHINE_MENU.get(), PrimitiveMintMachineScreen::new);
            event.register(TyC2Registries.MINT_MACHINE_MENU.get(), MintMachineScreen::new);
            event.register(TyC2Registries.ATM_MACHINE_MENU.get(), AtmMachineScreen::new);
            event.register(TyC2Registries.COOPERATION_HUB_MENU.get(), CooperationHubScreen::new);
            event.register(TyC2Registries.COMM_TERMINAL_MENU.get(), CommTerminalScreen::new);
            event.register(TyC2Registries.MAYOR_NEGOTIATION_MENU.get(), MayorNegotiationScreen::new);
        });
    }
}
