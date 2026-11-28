package com.tycooncraft.tyc2.qa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side QA driver ({@code tyc2.qa.clientDriver=true}) for real graphical
 * Client ↔ Dedicated Server live joint testing on NeoForge 1.21.1, 26.1.2, and 26.2.
 * Audits all 29 TyC2 sprites/textures, connects over TCP, verifies packet sync &amp;
 * all 5 TyC2 Screens (Mint, ATM, Hub, Comm Terminal, Mayor Negotiation), and captures PNGs.
 */
public final class TyC2ClientDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger("mnera_client_qa");

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

    private final Path input = Path.of("qa-input.txt");
    private final Path output = Path.of("qa-output.json");

    private String lastOverlay = "";
    private int overlayCount = 0;
    private String lastChat = "";
    private int chatCount = 0;
    private String lastInteractResult = "";

    public static void install() {
        if (!Boolean.getBoolean("tyc2.qa.clientDriver")) {
            return;
        }
        TyC2ClientDriver driver = new TyC2ClientDriver();
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, event -> {
            String msg = event.getMessage().getString();
            if (event.isOverlay()) {
                driver.lastOverlay = msg;
                driver.overlayCount++;
            } else {
                driver.lastChat = msg;
                driver.chatCount++;
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> driver.tick());
        LOGGER.info("[TYC2-CLIENT-QA] TyC2ClientDriver installed");
    }

    private void tick() {
        if (!Files.isRegularFile(input)) {
            return;
        }
        JsonObject result = new JsonObject();
        try {
            JsonObject req = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            Files.delete(input);
            result.addProperty("seq", req.get("seq").getAsInt());
            String action = req.get("action").getAsString();
            Minecraft mc = Minecraft.getInstance();
            act(mc, action, req, result);
            recordState(mc, result);
        } catch (Throwable t) {
            LOGGER.error("[TYC2-CLIENT-QA] Action error", t);
            result.addProperty("error", t.toString());
        }
        try {
            Path tmp = output.resolveSibling("qa-output.tmp");
            Files.writeString(tmp, result.toString());
            Files.move(tmp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void act(Minecraft mc, String action, JsonObject req, JsonObject result) {
        switch (action) {
            case "state" -> {
            }
            case "connect" -> {
                int port = Integer.getInteger("tyc2.qa.clientPort", 25565);
                String address = "127.0.0.1:" + port;
                ConnectScreen.startConnecting(
                        new TitleScreen(),
                        mc,
                        ServerAddress.parseString(address),
                        new ServerData("TyC2 Live QA Server", address, ServerData.Type.OTHER),
                        false,
                        null
                );
            }
            case "check_sprites" -> checkSprites(mc, result);
            case "clear_hud" -> TyC2QaClientPlatform.clearHud(mc);
            case "hide_gui" -> TyC2QaClientPlatform.setHideGui(mc, req.get("value").getAsBoolean());
            case "camera" -> {
                String mode = req.get("mode").getAsString();
                mc.options.setCameraType("third_back".equals(mode)
                        ? CameraType.THIRD_PERSON_BACK
                        : CameraType.FIRST_PERSON);
            }
            case "look" -> {
                if (mc.player != null) {
                    float yaw = req.get("yaw").getAsFloat();
                    float pitch = req.get("pitch").getAsFloat();
                    mc.player.setYRot(yaw);
                    mc.player.yRotO = yaw;
                    mc.player.setYHeadRot(yaw);
                    mc.player.setXRot(pitch);
                    mc.player.xRotO = pitch;
                }
            }
            case "command" -> {
                if (mc.getConnection() == null) {
                    throw new IllegalStateException("Not connected to server");
                }
                mc.getConnection().sendCommand(req.get("cmd").getAsString());
            }
            case "right_click_block" -> {
                BlockPos pos = new BlockPos(req.get("x").getAsInt(), req.get("y").getAsInt(), req.get("z").getAsInt());
                Direction face = Direction.byName(req.has("face") ? req.get("face").getAsString() : "up");
                if (face == null) {
                    face = Direction.UP;
                }
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
                var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                lastInteractResult = res.toString();
            }
            case "right_click_air" -> {
                var res = TyC2QaClientPlatform.useHeldItem(mc);
                lastInteractResult = res.toString();
            }
            case "interact_mayor" -> {
                Entity mayor = findNearestVillager(mc);
                if (mayor == null) {
                    throw new IllegalStateException("No Mayor Villager found within 16 blocks");
                }
                var res = TyC2QaClientPlatform.interactEntity(mc, mayor);
                lastInteractResult = res.toString();
            }
            case "click_button_index" -> {
                Screen screen = TyC2QaClientPlatform.currentScreen(mc);
                if (screen == null) {
                    throw new IllegalStateException("No open screen");
                }
                int idx = req.get("index").getAsInt();
                List<Button> buttons = collectButtons(screen);
                if (idx < 0 || idx >= buttons.size()) {
                    throw new IllegalStateException("Button index " + idx + " out of range (size=" + buttons.size() + ")");
                }
                TyC2QaClientPlatform.pressButton(buttons.get(idx));
            }
            case "click_button_contains" -> {
                Screen screen = TyC2QaClientPlatform.currentScreen(mc);
                if (screen == null) {
                    throw new IllegalStateException("No open screen");
                }
                String needle = req.get("text").getAsString().toLowerCase();
                List<Button> buttons = collectButtons(screen);
                Button target = buttons.stream()
                        .filter(b -> b.getMessage().getString().toLowerCase().contains(needle))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No button containing '" + needle + "' among "
                                        + buttons.stream().map(b -> b.getMessage().getString()).toList()));
                TyC2QaClientPlatform.pressButton(target);
            }
            case "close_screen" -> {
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                TyC2QaClientPlatform.setScreen(mc, null);
            }
            case "screenshot" -> TyC2QaClientPlatform.grabScreenshot(mc, req.get("name").getAsString());
            case "quit" -> mc.stop();
            default -> throw new IllegalArgumentException("Unknown client action: " + action);
        }
    }

    private static List<Button> collectButtons(Screen screen) {
        List<Button> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button b && b.visible && b.active) {
                buttons.add(b);
            }
        }
        return buttons;
    }

    private static Entity findNearestVillager(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        List<Entity> candidates = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if ("minecraft:villager".equals(TyC2QaClientPlatform.entityId(e))
                    && e.distanceToSqr(mc.player) <= 16.0 * 16.0) {
                candidates.add(e);
            }
        }
        // Prefer named Mayor villager first, then nearest
        return candidates.stream()
                .min(Comparator.comparing((Entity e) -> !e.hasCustomName())
                        .thenComparingDouble(e -> e.distanceToSqr(mc.player)))
                .orElse(null);
    }

    private void checkSprites(Minecraft mc, JsonObject out) {
        JsonArray missing = new JsonArray();
        for (String s : REQUIRED_BLOCK_SPRITES) {
            if (!TyC2QaClientPlatform.hasBlockSprite(mc, s)) {
                missing.add("block:" + s);
            }
        }
        for (String s : REQUIRED_ITEM_SPRITES) {
            if (!TyC2QaClientPlatform.hasItemSprite(mc, s)) {
                missing.add("item:" + s);
            }
        }
        for (String r : REQUIRED_GUI_RESOURCES) {
            if (!TyC2QaClientPlatform.hasResource(mc, r)) {
                missing.add("resource:" + r);
            }
        }
        out.addProperty("spritesChecked",
                REQUIRED_BLOCK_SPRITES.size() + REQUIRED_ITEM_SPRITES.size() + REQUIRED_GUI_RESOURCES.size());
        out.addProperty("spritesOk", missing.isEmpty());
        out.add("missingSprites", missing);
    }

    private void recordState(Minecraft mc, JsonObject out) {
        Screen screen = TyC2QaClientPlatform.currentScreen(mc);
        out.addProperty("world", mc.level != null);
        out.addProperty("overlay", TyC2QaClientPlatform.hasOverlay(mc));
        out.addProperty("screen", screen == null ? "" : screen.getClass().getSimpleName());
        out.addProperty("screenTitle", screen == null ? "" : screen.getTitle().getString());
        if (screen != null) {
            JsonArray btns = new JsonArray();
            for (Button b : collectButtons(screen)) {
                btns.add(b.getMessage().getString());
            }
            out.add("screenButtons", btns);
        }
        out.addProperty("lastOverlay", lastOverlay);
        out.addProperty("overlayCount", overlayCount);
        out.addProperty("lastChat", lastChat);
        out.addProperty("chatCount", chatCount);
        out.addProperty("lastInteractResult", lastInteractResult);
        if (mc.player != null) {
            out.addProperty("playerX", mc.player.getX());
            out.addProperty("playerY", mc.player.getY());
            out.addProperty("playerZ", mc.player.getZ());
        }
    }
}
