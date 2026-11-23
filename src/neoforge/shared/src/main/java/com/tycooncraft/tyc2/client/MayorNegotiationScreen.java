package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.menu.MayorNegotiationMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-exact GUI screen for Village Mayor Partnership Negotiation ({@code gui/mayor_negotiation.png}, 220x192).
 */
public final class MayorNegotiationScreen extends TyC2ScreenBase<MayorNegotiationMenu> {

    private static final String GUI_TEX = "mayor_negotiation";
    private Button signBtn;

    public MayorNegotiationScreen(MayorNegotiationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 220, 192);
    }

    @Override
    protected boolean drawDefaultLabels() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        signBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.tyc2.mayor.sign"),
                b -> TyC2Native.sendToServer(new TyC2ActionPayload(
                        "MAYOR",
                        menu.villagePos(),
                        "SIGN_PARTNERSHIP",
                        JsonLite.toJson(Map.of("villageId", menu.villageId().toString()))
                ))
        ).bounds(leftPos + 42, topPos + 122, 108, 18).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.tyc2.cancel"),
                b -> onClose()
        ).bounds(leftPos + 154, topPos + 122, 54, 18).build());

        TyC2Native.sendToServer(new TyC2ActionPayload(
                "MAYOR",
                menu.villagePos(),
                "REQUEST_SYNC",
                JsonLite.toJson(Map.of("villageId", menu.villageId().toString()))
        ));
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        Map<String, Object> snap = menu.snapshot();
        String name = JsonLite.getString(snap, "villageName", "");
        String biome = JsonLite.getString(snap, "biome", "plains");
        int pop = JsonLite.getInt(snap, "population", 0);
        boolean canSign = JsonLite.getBoolean(snap, "canSign", false);
        if (signBtn != null) {
            signBtn.active = canSign;
        }

        // Top Mayor Banner (8, 14, 204x34); crown is baked at (14, 23)
        canvas.drawClippedString(
                Component.translatable("mayor.tyc2.name_format", name).getString(),
                40, 17, 168, 0xFFFFE066
        );
        drawWrappedTwoLines(
                canvas,
                Component.translatable("gui.tyc2.mayor.greeting").getString(),
                40, 28, 168, 0.80F, 0xFFD0DCEB
        );

        // Left side of Contract Box (12..89, divider baked at x=92..93): Village Profile
        canvas.drawClippedString(
                Component.translatable("gui.tyc2.mayor.village_info").getString(),
                12, 56, 78, 0xFF3A2D18
        );
        canvas.drawClippedString(name, 12, 68, 78, 0xFF202020);
        canvas.drawClippedString(
                Component.translatable("biome.tyc2." + biome).getString(),
                12, 80, 78, 0xFF444444
        );
        canvas.drawClippedString(
                Component.translatable("gui.tyc2.hub.population", pop).getString(),
                12, 92, 78, 0xFF2B5B34
        );

        // Right side of Contract Box (95..208, right of x=92..93 divider): 4 Requirements
        long reqIc = JsonLite.getLong(snap, "reqIc", 100L);
        long haveIc = JsonLite.getLong(snap, "haveIc", 0L);
        boolean icOk = JsonLite.getBoolean(snap, "icOk", false);

        int reqLogs = JsonLite.getInt(snap, "reqLogs", 32);
        int haveLogs = JsonLite.getInt(snap, "haveLogs", 0);
        boolean logsOk = JsonLite.getBoolean(snap, "logsOk", false);

        int reqIron = JsonLite.getInt(snap, "reqIron", 16);
        int haveIron = JsonLite.getInt(snap, "haveIron", 0);
        boolean ironOk = JsonLite.getBoolean(snap, "ironOk", false);

        int reqFood = JsonLite.getInt(snap, "reqFood", 32);
        int haveFood = JsonLite.getInt(snap, "haveFood", 0);
        boolean foodOk = JsonLite.getBoolean(snap, "foodOk", false);

        drawReqRow(canvas, 56, icOk, Component.translatable("gui.tyc2.mayor.req_currency", haveIc + "/" + reqIc).getString());
        drawReqRow(canvas, 70, logsOk, Component.translatable("gui.tyc2.mayor.req_logs", haveLogs + "/" + reqLogs).getString());
        drawReqRow(canvas, 84, ironOk, Component.translatable("gui.tyc2.mayor.req_iron", haveIron + "/" + reqIron).getString());
        drawReqRow(canvas, 98, foodOk, Component.translatable("gui.tyc2.mayor.req_food", haveFood + "/" + reqFood).getString());

        // Action Bar Wax Seal at (14, 121, 20x20) from UV (64, 196, 20x20)
        canvas.blit(GUI_TEX, 14, 121, 64, 196, 20, 20);

        // Benefits LCD (8, 148, 204x36): scale cleanly so all 3 localized lines fit with zero clipping
        String b1 = Component.translatable("gui.tyc2.mayor.benefits.line1").getString();
        String b2 = Component.translatable("gui.tyc2.mayor.benefits.line2").getString();
        String b3 = Component.translatable("gui.tyc2.mayor.benefits.line3").getString();
        int maxBenW = Math.max(canvas.textWidth(b1), Math.max(canvas.textWidth(b2), canvas.textWidth(b3)));
        float benScale = maxBenW <= 194 ? 1.0F : Math.max(0.55F, Math.min(0.82F, 194.0F / (float) maxBenW));
        canvas.drawScaledString(b1, 12, 153, 196, benScale, 0xFF8BE8B0);
        canvas.drawScaledString(b2, 12, 163, 196, benScale, 0xFF8BE8B0);
        canvas.drawScaledString(b3, 12, 173, 196, benScale, 0xFF8BE8B0);
    }

    private void drawReqRow(ScreenCanvas canvas, int y, boolean ok, String label) {
        // Green Check (8, 196, 14x14) or Red Cross (26, 196, 14x14), cleanly right of x=92..93 divider
        canvas.blit(GUI_TEX, 95, y - 2, ok ? 8 : 26, 196, 14, 14);
        int w = canvas.textWidth(label);
        if (w <= 98) {
            canvas.drawClippedString(label, 110, y + 1, 98, ok ? 0xFF1F6E35 : 0xFFA62626);
        } else {
            float sc = Math.max(0.65F, 98.0F / (float) w);
            canvas.drawScaledString(label, 110, y + 1, 98, sc, ok ? 0xFF1F6E35 : 0xFFA62626);
        }
    }

    private void drawWrappedTwoLines(
            ScreenCanvas canvas,
            String text,
            int x,
            int y,
            int maxWidth,
            float scale,
            int color
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int lineMaxUnscaled = Math.max(1, (int) Math.floor(maxWidth / scale));
        if (canvas.textWidth(text) <= lineMaxUnscaled) {
            canvas.drawScaledString(text, x, y + 3, maxWidth, scale, color);
            return;
        }
        String first = font.plainSubstrByWidth(text, lineMaxUnscaled);
        int cut = first.length();
        int lastSpace = first.lastIndexOf(' ');
        if (lastSpace > first.length() / 2) {
            cut = lastSpace;
        }
        String line1 = text.substring(0, cut).trim();
        String line2 = text.substring(cut).trim();
        canvas.drawScaledString(line1, x, y, maxWidth, scale, color);
        if (!line2.isEmpty()) {
            canvas.drawScaledString(line2, x, y + 9, maxWidth, scale, color);
        }
    }

    @Override
    protected void paintOverlayTooltips(ScreenCanvas canvas, int mouseX, int mouseY) {
    }
}
