package com.tycooncraft.tyc2.qa;

import com.mojang.blaze3d.platform.NativeImage;
import com.tycooncraft.tyc2.forge.client.AtmMachineScreen;
import com.tycooncraft.tyc2.forge.client.CommTerminalScreen;
import com.tycooncraft.tyc2.forge.client.CooperationHubScreen;
import com.tycooncraft.tyc2.forge.client.MayorNegotiationScreen;
import com.tycooncraft.tyc2.forge.client.MintMachineScreen;
import com.tycooncraft.tyc2.forge.client.PrimitiveMintMachineScreen;
import com.tycooncraft.tyc2.forge.gui.AtmMachineMenu;
import com.tycooncraft.tyc2.forge.gui.CommTerminalMenu;
import com.tycooncraft.tyc2.forge.gui.CooperationHubMenu;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.gui.MintMachineMenu;
import com.tycooncraft.tyc2.forge.gui.PrimitiveMintMachineMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.network.C2SAtmActionPacket;
import com.tycooncraft.tyc2.forge.network.C2SHubActionPacket;
import com.tycooncraft.tyc2.forge.network.S2CAtmSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CMayorSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CTerminalSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Physical Forge 1.19.2 graphical client driver for the real Client + Server joint test.
 * Auto-connects over TCP {@code 127.0.0.1:<port>}, audits every TyC2 texture sprite &amp; GUI PNG,
 * interacts with all 5 TyC2 screens over the live network, and captures crisp 1280x720 screenshots.
 */
final class TyC2LiveClientDriver {

    private static final List<String> REQUIRED_BLOCK_SPRITES = List.of(
            "mnera:block/primitive_mint_machine_front",
            "mnera:block/primitive_mint_machine_front_active",
            "mnera:block/primitive_mint_machine_side",
            "mnera:block/primitive_mint_machine_top",
            "mnera:block/primitive_mint_machine_bottom",
            "mnera:block/mint_machine_front",
            "mnera:block/mint_machine_front_active",
            "mnera:block/mint_machine_side",
            "mnera:block/mint_machine_top",
            "mnera:block/mint_machine_bottom",
            "mnera:block/atm_machine_front",
            "mnera:block/atm_machine_side",
            "mnera:block/atm_machine_top",
            "mnera:block/atm_machine_bottom",
            "mnera:block/cooperation_hub_front",
            "mnera:block/cooperation_hub_side",
            "mnera:block/cooperation_hub_top",
            "mnera:block/cooperation_hub_bottom"
    );

    private static final List<String> REQUIRED_ITEM_SPRITES = List.of(
            "mnera:item/coin_1",
            "mnera:item/coin_5",
            "mnera:item/coin_10",
            "mnera:item/coin_20",
            "mnera:item/coin_50",
            "mnera:item/coin_100",
            "mnera:item/coin_200",
            "mnera:item/coin_500",
            "mnera:item/coin_1000",
            "mnera:item/comm_terminal",
            "mnera:item/comm_terminal_empty"
    );

    private static final List<String> REQUIRED_GUI_RESOURCES = List.of(
            "mnera:textures/gui/primitive_mint_machine.png",
            "mnera:textures/gui/mint_machine.png",
            "mnera:textures/gui/atm_machine.png",
            "mnera:textures/gui/cooperation_hub.png",
            "mnera:textures/gui/comm_terminal.png",
            "mnera:textures/gui/mayor_negotiation.png"
    );

    private static volatile TyC2LiveClientDriver instance;

    private final int port;
    private final Path screenshotsDir;
    private final TyC2QaAssert qa;
    private final Queue<TyC2LiveNetwork.S2CLiveDirective> pendingDirectives = new ArrayDeque<>();
    private final Map<String, String> capturedScreenshots = new LinkedHashMap<>();

    private boolean connectStarted = false;
    private boolean shuttingDown = false;
    private int shutdownDelay = 0;

    private TyC2LiveNetwork.S2CLiveDirective activeDirective = null;
    private int subStep = 0;
    private int dirTicks = 0;
    private long atmBalBeforeWithdraw = 0L;

    private volatile String pendingShotName = null;
    private volatile boolean shotDone = false;
    private volatile String shotError = null;

    private TyC2LiveClientDriver(String profile, int port, Path screenshotsDir) {
        this.port = port;
        this.screenshotsDir = screenshotsDir;
        this.qa = new TyC2QaAssert("live-client-" + profile);
    }

    static void install(String profile) {
        int port = Integer.getInteger("tyc2.live.port", 25565);
        String dirStr = System.getProperty("tyc2.live.screenshotsDir", "live-screenshots");
        TyC2LiveClientDriver driver = new TyC2LiveClientDriver(profile, port, Path.of(dirStr));
        instance = driver;
        MinecraftForge.EVENT_BUS.register(driver);
        TyC2QaMod.LOGGER.info("[TYC2-LIVE-CLIENT] Installed graphical client driver (target=127.0.0.1:{}, shots={})",
                port, dirStr);
    }

    static void onDirective(TyC2LiveNetwork.S2CLiveDirective pkt) {
        TyC2LiveClientDriver driver = instance;
        if (driver != null) {
            TyC2QaMod.LOGGER.info("[TYC2-LIVE-CLIENT] Directive step={} action={} pos={} ent={} arg={}",
                    pkt.stepId(), pkt.action(), pkt.targetPos(), pkt.entityId(), pkt.argText());
            driver.pendingDirectives.add(pkt);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        if (shuttingDown) {
            shutdownDelay++;
            if (shutdownDelay >= 10) {
                mc.stop();
            }
            return;
        }

        if (!connectStarted && mc.screen instanceof TitleScreen titleScreen) {
            connectStarted = true;
            TyC2QaMod.LOGGER.info("[TYC2-LIVE-CLIENT] TitleScreen ready -> connecting to 127.0.0.1:{}", port);
            ServerAddress addr = new ServerAddress("127.0.0.1", port);
            ServerData data = new ServerData("TyC2 Live Server", "127.0.0.1:" + port, false);
            ConnectScreen.startConnecting(titleScreen, mc, addr, data);
            return;
        }

        if (connectStarted && mc.screen instanceof DisconnectedScreen disconnected) {
            failAndStop(mc, new AssertionError("Disconnected unexpectedly: " + disconnected.getTitle().getString()));
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (activeDirective == null && !pendingDirectives.isEmpty()) {
            activeDirective = pendingDirectives.poll();
            subStep = 0;
            dirTicks = 0;
        }

        if (activeDirective == null) {
            return;
        }

        dirTicks++;
        try {
            tickDirective(mc, activeDirective);
        } catch (Throwable t) {
            TyC2QaMod.LOGGER.error("[TYC2-LIVE-CLIENT] Directive {} ({}) failed",
                    activeDirective.stepId(), activeDirective.action(), t);
            TyC2LiveNetwork.sendToServer(new TyC2LiveNetwork.C2SLiveAck(
                    activeDirective.stepId(), false, t.getClass().getSimpleName() + ": " + t.getMessage()));
            failAndStop(mc, t);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        String name = this.pendingShotName;
        if (name == null) {
            return;
        }
        this.pendingShotName = null;
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.createDirectories(screenshotsDir);
            Path outFile = screenshotsDir.resolve(name);
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(outFile);
            }
            capturedScreenshots.put(name, outFile.toAbsolutePath().toString());
            TyC2QaMod.LOGGER.info("[TYC2-LIVE-CLIENT] Captured screenshot: {}", outFile.toAbsolutePath());
            this.shotError = null;
        } catch (Throwable t) {
            TyC2QaMod.LOGGER.error("[TYC2-LIVE-CLIENT] Failed capturing screenshot {}", name, t);
            this.shotError = t.toString();
        } finally {
            this.shotDone = true;
        }
    }

    private void tickDirective(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) throws Exception {
        switch (dir.action()) {
            case "AUDIT_ASSETS" -> runAssetAudit(mc, dir);
            case "CAPTURE_SCREENSHOT" -> tickCaptureWorldShot(mc, dir);
            case "TEST_MAYOR_GUI" -> tickTestMayorGui(mc, dir);
            case "TEST_PRIMITIVE_MINT_ACTIVE_GUI" -> tickTestPrimitiveMintGui(mc, dir, false);
            case "TEST_PRIMITIVE_MINT_BLOCKED_GUI" -> tickTestPrimitiveMintGui(mc, dir, true);
            case "TEST_MINT_GUI" -> tickTestMintGui(mc, dir);
            case "TEST_ATM_GUI" -> tickTestAtmGui(mc, dir);
            case "TEST_HUB_GUI" -> tickTestHubGui(mc, dir);
            case "TEST_TERMINAL_GUI" -> tickTestTerminalGui(mc, dir);
            case "FINISH_AND_DISCONNECT" -> runFinish(mc, dir);
            default -> throw new AssertionError("Unknown live directive: " + dir.action());
        }
    }

    private void runAssetAudit(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        String expectedLang = (dir.argText() == null || dir.argText().isBlank()) ? "en_us" : dir.argText();
        String actualLang = mc.getLanguageManager().getSelected().getCode();
        qa.check("client.lang_loaded_matches_expected", expectedLang.equalsIgnoreCase(actualLang),
                "expected=" + expectedLang + ", actual=" + actualLang);
        String hubTitle = Component.translatable("block.mnera.cooperation_hub").getString();
        boolean localizedOk = !hubTitle.contains("block.mnera.")
                && (!"zh_cn".equalsIgnoreCase(expectedLang) || hubTitle.contains("合作枢纽"));
        qa.check("client.lang_translation_active", localizedOk,
                "lang=" + actualLang + ", block.mnera.cooperation_hub=" + hubTitle);

        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        ResourceLocation missing = MissingTextureAtlasSprite.getLocation();

        int sprites = 0;
        for (String s : REQUIRED_BLOCK_SPRITES) {
            ResourceLocation rl = new ResourceLocation(s);
            TextureAtlasSprite sp = atlas.getSprite(rl);
            qa.check("client.sprite." + rl.getPath(), sp != null && !missing.equals(sp.getName()),
                    "sprite=" + (sp == null ? "null" : sp.getName()));
            sprites++;
        }
        for (String s : REQUIRED_ITEM_SPRITES) {
            ResourceLocation rl = new ResourceLocation(s);
            TextureAtlasSprite sp = atlas.getSprite(rl);
            qa.check("client.sprite." + rl.getPath(), sp != null && !missing.equals(sp.getName()),
                    "sprite=" + (sp == null ? "null" : sp.getName()));
            sprites++;
        }
        int guiRes = 0;
        for (String r : REQUIRED_GUI_RESOURCES) {
            ResourceLocation rl = new ResourceLocation(r);
            boolean present = mc.getResourceManager().getResource(rl).isPresent();
            qa.check("client.gui_png." + rl.getPath(), present, r);
            guiRes++;
        }
        qa.metric("client.verifiedSprites", sprites);
        qa.metric("client.verifiedGuiPngs", guiRes);
        ackAndClear(dir, true, "verified lang=" + actualLang + ", " + sprites + " atlas sprites + " + guiRes + " GUI PNGs");
    }

    private void tickCaptureWorldShot(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        if (subStep == 0) {
            if (mc.screen != null) {
                dirTicks = 0;
                return;
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false; // Show hotbar with TyC2 coins & comm_terminal!
            mc.getToasts().clear();
            if (dirTicks < 25) {
                return;
            }
            requestShot(dir.argText());
            subStep = 1;
            dirTicks = 0;
            return;
        }
        if (awaitShot(dir.argText())) {
            qa.check("client.screenshot." + dir.argText(), true, "saved " + capturedScreenshots.get(dir.argText()));
            ackAndClear(dir, true, "captured " + dir.argText());
        }
    }

    private void tickTestMayorGui(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        switch (subStep) {
            case 0 -> {
                Entity ent = mc.level.getEntity(dir.entityId());
                if (ent == null) {
                    if (dirTicks > 80) {
                        throw new AssertionError("Mayor entity id=" + dir.entityId() + " not synced to client");
                    }
                    return;
                }
                qa.check("client.mayor_has_custom_name", ent.hasCustomName() && ent.isCustomNameVisible(),
                        "name=" + (ent.getCustomName() == null ? "null" : ent.getCustomName().getString()));
                mc.gameMode.interact(mc.player, ent, InteractionHand.MAIN_HAND);
                subStep = 1;
                dirTicks = 0;
            }
            case 1 -> {
                if (!(mc.screen instanceof MayorNegotiationScreen)
                        || !(mc.player.containerMenu instanceof MayorNegotiationMenu menu)
                        || menu.getLatestSync() == null) {
                    if (dirTicks > 100) {
                        throw new AssertionError("Timed out waiting for MayorNegotiationScreen sync (screen=" + mc.screen + ")");
                    }
                    return;
                }
                if (dirTicks < 10) {
                    return;
                }
                S2CMayorSyncPacket sync = menu.getLatestSync();
                qa.check("client.mayor_gui_opened_and_met", sync.allMet() && !sync.partnered,
                        "village=" + sync.villageName + ", pop=" + sync.population + ", allMet=" + sync.allMet());
                mc.getToasts().clear();
                requestShot(dir.argText());
                subStep = 2;
                dirTicks = 0;
            }
            case 2 -> {
                if (!awaitShot(dir.argText())) {
                    return;
                }
                qa.check("client.screenshot." + dir.argText(), true, "saved " + capturedScreenshots.get(dir.argText()));
                // Click the Sign Partnership button at (leftPos + 36 + 50, topPos + 123 + 8)
                MayorNegotiationScreen screen = (MayorNegotiationScreen) mc.screen;
                boolean clicked = screen.mouseClicked(screen.getGuiLeft() + 86.0, screen.getGuiTop() + 131.0, 0);
                qa.check("client.mayor_gui_sign_clicked", clicked, "clicked sign button");
                subStep = 3;
                dirTicks = 0;
            }
            case 3 -> {
                // MayorNegotiationScreen auto-closes when partnered==true arrives from server!
                if (mc.screen == null) {
                    qa.check("client.mayor_gui_closed_after_signing", true, "screen closed cleanly");
                    ackAndClear(dir, true, "MayorNegotiationScreen verified & signed over TCP");
                } else if (dirTicks > 100) {
                    throw new AssertionError("MayorNegotiationScreen did not close after clicking Sign");
                }
            }
        }
    }

    private void tickTestPrimitiveMintGui(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir, boolean expectBlocked) {
        switch (subStep) {
            case 0 -> {
                BlockPos pos = dir.targetPos();
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                subStep = 1;
                dirTicks = 0;
            }
            case 1 -> {
                if (!(mc.screen instanceof PrimitiveMintMachineScreen)
                        || !(mc.player.containerMenu instanceof PrimitiveMintMachineMenu menu)) {
                    if (dirTicks > 100) {
                        throw new AssertionError("Timed out waiting for PrimitiveMintMachineScreen (screen=" + mc.screen + ")");
                    }
                    return;
                }
                if (dirTicks < 18) {
                    return;
                }
                if (expectBlocked) {
                    if (!menu.isBlockedByIndustrialTier()) {
                        if (dirTicks > 80) {
                            throw new AssertionError("PrimitiveMintMachineScreen did not sync blockedByIndustrialTier=true");
                        }
                        return;
                    }
                    qa.check("client.primitive_mint_gui_blocked_warning_synced",
                            menu.isBlockedByIndustrialTier() && menu.slots.size() == 40,
                            "blocked=" + menu.isBlockedByIndustrialTier() + ", slots=" + menu.slots.size());
                } else {
                    if (menu.getBurnRemaining() <= 0 || menu.getProgress() <= 0) {
                        if (dirTicks > 80) {
                            throw new AssertionError("PrimitiveMintMachineScreen did not sync active burn/progress: burn="
                                    + menu.getBurnRemaining() + ", prog=" + menu.getProgress());
                        }
                        return;
                    }
                    qa.check("client.primitive_mint_gui_active_coal_minting",
                            menu.getBurnRemaining() > 0 && menu.getProgress() > 0
                                    && !menu.isBlockedByIndustrialTier() && menu.slots.size() == 40,
                            "burn=" + menu.getBurnRemaining() + "/" + menu.getBurnTotal()
                                    + ", prog=" + menu.getProgress() + "/" + menu.getMaxProgress()
                                    + ", slots=" + menu.slots.size());
                }
                mc.getToasts().clear();
                requestShot(dir.argText());
                subStep = 2;
                dirTicks = 0;
            }
            case 2 -> {
                if (!awaitShot(dir.argText())) {
                    return;
                }
                qa.check("client.screenshot." + dir.argText(), true, "saved " + capturedScreenshots.get(dir.argText()));
                mc.player.closeContainer();
                ackAndClear(dir, true, expectBlocked
                        ? "PrimitiveMintMachineScreen industrial-tier blocked warning verified over TCP"
                        : "PrimitiveMintMachineScreen active coal minting verified over TCP");
            }
        }
    }

    private void tickTestMintGui(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        switch (subStep) {
            case 0 -> {
                BlockPos pos = dir.targetPos();
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                subStep = 1;
                dirTicks = 0;
            }
            case 1 -> {
                if (!(mc.screen instanceof MintMachineScreen)
                        || !(mc.player.containerMenu instanceof MintMachineMenu menu)) {
                    if (dirTicks > 100) {
                        throw new AssertionError("Timed out waiting for MintMachineScreen (screen=" + mc.screen + ")");
                    }
                    return;
                }
                if (dirTicks < 18) {
                    return;
                }
                qa.check("client.mint_gui_opened_electric_with_battery",
                        menu.getStoredEu() > 0 && menu.isBatteryDischarging() && menu.slots.size() == 45,
                        "storedEu=" + menu.getStoredEu() + "/" + menu.getMaxEu()
                                + ", batteryDischarging=" + menu.isBatteryDischarging()
                                + ", progress=" + menu.getProgress() + "/" + menu.getMaxProgress()
                                + ", slots=" + menu.slots.size());
                mc.getToasts().clear();
                requestShot(dir.argText());
                subStep = 2;
                dirTicks = 0;
            }
            case 2 -> {
                if (!awaitShot(dir.argText())) {
                    return;
                }
                qa.check("client.screenshot." + dir.argText(), true, "saved " + capturedScreenshots.get(dir.argText()));
                mc.player.closeContainer();
                ackAndClear(dir, true, "Pure-electric MintMachineScreen & battery discharge verified over TCP");
            }
        }
    }

    private void tickTestAtmGui(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        BlockPos pos = dir.targetPos();
        switch (subStep) {
            case 0 -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                subStep = 1;
                dirTicks = 0;
            }
            case 1 -> {
                if (!(mc.screen instanceof AtmMachineScreen)
                        || !(mc.player.containerMenu instanceof AtmMachineMenu menu)
                        || menu.getLatestSync() == null) {
                    if (dirTicks > 100) {
                        throw new AssertionError("Timed out waiting for AtmMachineScreen sync");
                    }
                    return;
                }
                // Click Deposit slot button over TCP!
                TyC2ForgeNetwork.sendToServer(C2SAtmActionPacket.simple(pos, C2SAtmActionPacket.Action.DEPOSIT_SLOT));
                subStep = 2;
                dirTicks = 0;
            }
            case 2 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.personalBalance >= 500L) {
                    qa.check("client.atm_deposit_roundtrip", true, "personalBalance=" + sync.personalBalance);
                    // Withdraw 100 IC over TCP!
                    atmBalBeforeWithdraw = sync.personalBalance;
                    TyC2ForgeNetwork.sendToServer(new C2SAtmActionPacket(
                            pos, C2SAtmActionPacket.Action.WITHDRAW_AMOUNT, 100L, 0, "", ""
                    ));
                    subStep = 3;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM deposit sync timed out: " + (sync == null ? "null" : sync.personalBalance));
                }
            }
            case 3 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.personalBalance == atmBalBeforeWithdraw - 100L) {
                    qa.check("client.atm_withdraw_roundtrip", true, "balance=" + sync.personalBalance);
                    // Create company over TCP!
                    TyC2ForgeNetwork.sendToServer(new C2SAtmActionPacket(
                            pos, C2SAtmActionPacket.Action.CREATE_COMPANY, 0L, 0, "Forge Guild", ""
                    ));
                    subStep = 4;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM withdraw sync timed out");
                }
            }
            case 4 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.companyMode && "Forge Guild".equals(sync.activeCompanyName)) {
                    qa.check("client.atm_company_created_roundtrip", true,
                            "company=" + sync.activeCompanyName + ", invite=" + sync.activeCompanyInvite);
                    // Switch back to Personal briefly so we can transfer 200 IC to Forge Guild and populate localized tx logs!
                    TyC2ForgeNetwork.sendToServer(C2SAtmActionPacket.simple(pos, C2SAtmActionPacket.Action.SWITCH_TO_PERSONAL));
                    subStep = 5;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM create company sync timed out");
                }
            }
            case 5 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && !sync.companyMode) {
                    TyC2ForgeNetwork.sendToServer(new C2SAtmActionPacket(
                            pos, C2SAtmActionPacket.Action.TRANSFER, 200L, 0, "Forge Guild", ""
                    ));
                    subStep = 6;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM switch to personal timed out");
                }
            }
            case 6 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.personalBalance == atmBalBeforeWithdraw - 300L) {
                    qa.check("client.atm_transfer_to_company_roundtrip", true, "personal=" + sync.personalBalance);
                    TyC2ForgeNetwork.sendToServer(C2SAtmActionPacket.simple(pos, C2SAtmActionPacket.Action.SWITCH_TO_COMPANY));
                    subStep = 7;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM transfer to company sync timed out: "
                            + (sync == null ? "null" : sync.personalBalance));
                }
            }
            case 7 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.companyMode && sync.activeCompanyBalance >= 200L) {
                    // Withdraw 50 IC from Forge Guild so BOTH +200 Transfer In AND -50 Withdraw appear in localized tx logs!
                    TyC2ForgeNetwork.sendToServer(new C2SAtmActionPacket(
                            pos, C2SAtmActionPacket.Action.WITHDRAW_AMOUNT, 50L, 0, "", ""
                    ));
                    subStep = 8;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM switch back to company sync timed out");
                }
            }
            case 8 -> {
                S2CAtmSyncPacket sync = ((AtmMachineMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.companyMode && sync.activeCompanyBalance == 150L && sync.logs.size() >= 2) {
                    qa.check("client.atm_company_tx_logs_populated", true,
                            "bal=" + sync.activeCompanyBalance + ", logs=" + sync.logs + ", feedback=" + sync.feedback);
                    if (dirTicks < 8) {
                        return;
                    }
                    mc.getToasts().clear();
                    requestShot(dir.argText());
                    subStep = 9;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("ATM company withdraw/log sync timed out: "
                            + (sync == null ? "null" : ("bal=" + sync.activeCompanyBalance + ", logs=" + sync.logs.size())));
                }
            }
            case 9 -> {
                if (!awaitShot(dir.argText())) {
                    return;
                }
                qa.check("client.screenshot." + dir.argText(), true, "saved " + capturedScreenshots.get(dir.argText()));
                mc.player.closeContainer();
                ackAndClear(dir, true, "AtmMachineScreen deposit/withdraw/company/transfer/tx-logs verified over TCP");
            }
        }
    }

    private void tickTestHubGui(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        BlockPos pos = dir.targetPos();
        String prefix = dir.argText();
        switch (subStep) {
            case 0 -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                subStep = 1;
                dirTicks = 0;
            }
            case 1 -> {
                if (!(mc.screen instanceof CooperationHubScreen screen)
                        || !(mc.player.containerMenu instanceof CooperationHubMenu menu)
                        || menu.getLatestSync() == null) {
                    if (dirTicks > 100) {
                        throw new AssertionError("Timed out waiting for CooperationHubScreen sync");
                    }
                    return;
                }
                S2CHubSyncPacket sync = menu.getLatestSync();
                qa.check("client.hub_gui_opened_with_orders_and_cpi",
                        !sync.orders.isEmpty() && sync.cpiHistory.length >= 2,
                        "orders=" + sync.orders.size() + ", cpiPts=" + sync.cpiHistory.length);
                // Execute first Sell Order via Tab 0 Buy button so localized trade feedback also renders!
                boolean clickedBuy = screen.mouseClicked(screen.getGuiLeft() + 190.0, screen.getGuiTop() + 72.0, 0);
                qa.check("client.hub_tab0_order_clicked", clickedBuy, "clicked row 0 action button");
                subStep = 2;
                dirTicks = 0;
            }
            case 2 -> {
                S2CHubSyncPacket sync = ((CooperationHubMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && sync.outboundUsed > 0) {
                    if (dirTicks < 8) {
                        return;
                    }
                    qa.check("client.hub_order_populated_outbound", true,
                            "outboundUsed=" + sync.outboundUsed + ", feedback=" + sync.feedback);
                    mc.getToasts().clear();
                    requestShot(prefix + "a_hub_orders.png");
                    subStep = 3;
                    dirTicks = 0;
                } else if (dirTicks > 80) {
                    throw new AssertionError("Hub Buy order did not populate outboundUsed");
                }
            }
            case 3 -> {
                String shot = prefix + "a_hub_orders.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Click Tab 1 (Warehouse) at (leftPos + 8 + 58 + 20, topPos + 12)
                CooperationHubScreen screen = (CooperationHubScreen) mc.screen;
                boolean clickedTab1 = screen.mouseClicked(screen.getGuiLeft() + 86.0, screen.getGuiTop() + 12.0, 0);
                qa.check("client.hub_tab1_warehouse_clicked", clickedTab1, "switched to Warehouse tab");
                subStep = 4;
                dirTicks = 0;
            }
            case 4 -> {
                if (dirTicks < 12) {
                    return;
                }
                mc.getToasts().clear();
                requestShot(prefix + "b_hub_warehouse.png");
                subStep = 5;
                dirTicks = 0;
            }
            case 5 -> {
                String shot = prefix + "b_hub_warehouse.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Click Tab 2 (Macro Economy + 48-point CPI Line Chart!) at (leftPos + 8 + 2*58 + 20, topPos + 12)
                CooperationHubScreen screen = (CooperationHubScreen) mc.screen;
                boolean clickedTab2 = screen.mouseClicked(screen.getGuiLeft() + 144.0, screen.getGuiTop() + 12.0, 0);
                qa.check("client.hub_tab2_cpi_chart_clicked", clickedTab2, "switched to Macro Economy tab");
                subStep = 6;
                dirTicks = 0;
            }
            case 6 -> {
                if (dirTicks < 10) {
                    return;
                }
                mc.getToasts().clear();
                requestShot(prefix + "c_hub_economy.png");
                subStep = 7;
                dirTicks = 0;
            }
            case 7 -> {
                String shot = prefix + "c_hub_economy.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Click Tab 3 (Growth/Expansion/Settings) at (leftPos + 8 + 3*58 + 20, topPos + 12)
                CooperationHubScreen screen = (CooperationHubScreen) mc.screen;
                boolean clickedTab3 = screen.mouseClicked(screen.getGuiLeft() + 202.0, screen.getGuiTop() + 12.0, 0);
                qa.check("client.hub_tab3_expansion_clicked", clickedTab3, "switched to Growth/Expansion tab");
                subStep = 8;
                dirTicks = 0;
            }
            case 8 -> {
                if (dirTicks < 10) {
                    return;
                }
                mc.getToasts().clear();
                requestShot(prefix + "d_hub_expansion.png");
                subStep = 9;
                dirTicks = 0;
            }
            case 9 -> {
                String shot = prefix + "d_hub_expansion.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Rename village over TCP!
                TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                        pos, C2SHubActionPacket.Action.RENAME_VILLAGE, 0, 0, false, "Forge Plaza"
                ));
                subStep = 10;
                dirTicks = 0;
            }
            case 10 -> {
                S2CHubSyncPacket sync = ((CooperationHubMenu) mc.player.containerMenu).getLatestSync();
                if (sync != null && "Forge Plaza".equals(sync.villageName)) {
                    qa.check("client.hub_tab3_rename_roundtrip", true, "villageName=" + sync.villageName);
                    mc.player.closeContainer();
                    ackAndClear(dir, true, "CooperationHubScreen all 4 tabs & CPI chart captured & verified over TCP");
                } else if (dirTicks > 80) {
                    throw new AssertionError("Hub rename sync timed out");
                }
            }
        }
    }

    private void tickTestTerminalGui(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) {
        BlockPos hubPos = dir.targetPos();
        String prefix = dir.argText();
        switch (subStep) {
            case 0 -> {
                mc.player.getInventory().selected = 0;
                if (!(mc.player.getMainHandItem().getItem() instanceof CommTerminalItem)) {
                    if (dirTicks > 60) {
                        throw new AssertionError("Hotbar slot 0 does not hold mnera:comm_terminal: " + mc.player.getMainHandItem());
                    }
                    return;
                }
                // Authentic sneak key hold so LocalPlayer naturally transmits PRESS_SHIFT_KEY before useItemOn!
                mc.options.keyShift.setDown(true);
                mc.player.setShiftKeyDown(true);
                if (dirTicks < 3) {
                    return;
                }
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(hubPos), Direction.SOUTH, hubPos, false));
                mc.options.keyShift.setDown(false);
                mc.player.setShiftKeyDown(false);
                subStep = 1;
                dirTicks = 0;
            }
            case 1 -> {
                mc.options.keyShift.setDown(false);
                if (dirTicks < 12) {
                    return;
                }
                // Right-click in air to open remote CommTerminalScreen!
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                subStep = 2;
                dirTicks = 0;
            }
            case 2 -> {
                if (!(mc.screen instanceof CommTerminalScreen)
                        || !(mc.player.containerMenu instanceof CommTerminalMenu menu)
                        || menu.getLatestSync() == null) {
                    if (dirTicks > 100) {
                        throw new AssertionError("Timed out waiting for CommTerminalScreen sync (screen=" + mc.screen + ")");
                    }
                    return;
                }
                if (dirTicks < 10) {
                    return;
                }
                S2CTerminalSyncPacket sync = menu.getLatestSync();
                qa.check("client.terminal_gui_bound_and_synced",
                        !sync.boundVillages.isEmpty() && sync.selectedVillage != null && sync.batteryEu > 0,
                        "bound=" + sync.boundVillages.size() + ", batteryEu=" + sync.batteryEu
                                + ", village=" + (sync.selectedVillage == null ? "null" : sync.selectedVillage.villageName));
                // Sub-tab 0 (Orders) is active by default — capture it!
                mc.getToasts().clear();
                requestShot(prefix + "a_terminal_orders.png");
                subStep = 3;
                dirTicks = 0;
            }
            case 3 -> {
                String shot = prefix + "a_terminal_orders.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Click Sub-tab 1 (Stock: leftPos + 78 + 40 + 20, topPos + 49)
                CommTerminalScreen screen = (CommTerminalScreen) mc.screen;
                screen.mouseClicked(screen.getGuiLeft() + 138.0, screen.getGuiTop() + 49.0, 0);
                subStep = 4;
                dirTicks = 0;
            }
            case 4 -> {
                if (dirTicks < 10) {
                    return;
                }
                mc.getToasts().clear();
                requestShot(prefix + "b_terminal_stock.png");
                subStep = 5;
                dirTicks = 0;
            }
            case 5 -> {
                String shot = prefix + "b_terminal_stock.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Click Sub-tab 2 (CPI: leftPos + 78 + 2*40 + 20, topPos + 49)
                CommTerminalScreen screen = (CommTerminalScreen) mc.screen;
                screen.mouseClicked(screen.getGuiLeft() + 178.0, screen.getGuiTop() + 49.0, 0);
                subStep = 6;
                dirTicks = 0;
            }
            case 6 -> {
                if (dirTicks < 10) {
                    return;
                }
                mc.getToasts().clear();
                requestShot(prefix + "c_terminal_cpi.png");
                subStep = 7;
                dirTicks = 0;
            }
            case 7 -> {
                String shot = prefix + "c_terminal_cpi.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                // Click Sub-tab 3 (Growth: leftPos + 78 + 3*40 + 20, topPos + 49)
                CommTerminalScreen screen = (CommTerminalScreen) mc.screen;
                screen.mouseClicked(screen.getGuiLeft() + 218.0, screen.getGuiTop() + 49.0, 0);
                subStep = 8;
                dirTicks = 0;
            }
            case 8 -> {
                if (dirTicks < 10) {
                    return;
                }
                mc.getToasts().clear();
                requestShot(prefix + "d_terminal_growth.png");
                subStep = 9;
                dirTicks = 0;
            }
            case 9 -> {
                String shot = prefix + "d_terminal_growth.png";
                if (!awaitShot(shot)) {
                    return;
                }
                qa.check("client.screenshot." + shot, true, "saved " + capturedScreenshots.get(shot));
                mc.player.closeContainer();
                ackAndClear(dir, true, "CommTerminalScreen bind & all 4 sub-tabs captured & verified over TCP");
            }
        }
    }

    private void runFinish(Minecraft mc, TyC2LiveNetwork.S2CLiveDirective dir) throws Exception {
        Map<String, String> extras = new LinkedHashMap<>();
        int idx = 1;
        for (Map.Entry<String, String> e : capturedScreenshots.entrySet()) {
            extras.put("screenshot." + idx + ".name", e.getKey());
            extras.put("screenshot." + idx + ".path", e.getValue());
            idx++;
        }
        qa.writePassedReport(Path.of("live-client-report.properties"), extras);
        ackAndClear(dir, true, "client finished (" + qa.passedCount() + " checks, "
                + capturedScreenshots.size() + " screenshots)");
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.clearLevel();
        shuttingDown = true;
        shutdownDelay = 0;
    }

    private void requestShot(String name) {
        this.shotDone = false;
        this.shotError = null;
        this.pendingShotName = name;
    }

    private boolean awaitShot(String name) {
        if (!this.shotDone) {
            if (dirTicks > 150) {
                throw new AssertionError("Timed out capturing screenshot: " + name);
            }
            return false;
        }
        if (this.shotError != null) {
            throw new AssertionError("Screenshot error for " + name + ": " + this.shotError);
        }
        return true;
    }

    private void ackAndClear(TyC2LiveNetwork.S2CLiveDirective dir, boolean ok, String summary) {
        TyC2LiveNetwork.sendToServer(new TyC2LiveNetwork.C2SLiveAck(dir.stepId(), ok, summary));
        activeDirective = null;
        subStep = 0;
        dirTicks = 0;
    }

    private void failAndStop(Minecraft mc, Throwable t) {
        qa.writeFailureReport(
                Path.of("live-client-report.properties"),
                Path.of("live-client-failure.txt"),
                t
        );
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.clearLevel();
        shuttingDown = true;
        shutdownDelay = 0;
    }
}
