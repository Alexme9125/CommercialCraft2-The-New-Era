package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.menu.PrimitiveMintMachineMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-exact GUI screen for the Primitive Minting Machine
 * ({@code gui/primitive_mint_machine.png}, 176x166).
 */
public final class PrimitiveMintMachineScreen extends TyC2ScreenBase<PrimitiveMintMachineMenu> {

    private static final String GUI_TEX = "primitive_mint_machine";

    public PrimitiveMintMachineScreen(
            PrimitiveMintMachineMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        // Furnace Flame Gauge: (49, 41, 14x14) from UV (176, 0, 14x14) bottom-up
        int burnRem = menu.burnRemaining();
        int burnTot = menu.burnTotal();
        if (burnRem > 0 && burnTot > 0) {
            int flameH = Math.max(1, Math.min(14, (int) Math.round(14.0 * burnRem / (double) burnTot)));
            int off = 14 - flameH;
            canvas.blit(GUI_TEX, 49, 41 + off, 176, off, 14, flameH);
        }

        // Press Progress Arrow: (82, 23, 24x16) from UV (176, 14, 24x16) left-to-right
        int prog = menu.progressTicks();
        int total = menu.totalTicks();
        if (prog > 0 && total > 0) {
            int arrowW = Math.max(1, Math.min(24, (int) Math.round(24.0 * prog / (double) total)));
            canvas.blit(GUI_TEX, 82, 23, 176, 14, arrowW, 16);
        }

        // Status / Upgrade-Required Banner LCD: (76, 48, 92x25)
        if (menu.isBlockedByIndustrialTier()) {
            // Amber Warning Badge (176, 32, 14x14) at (78, 53)
            canvas.blit(GUI_TEX, 78, 53, 176, 32, 14, 14);
            String warnTitle = Component.translatable("gui.tyc2.primitive_mint.requires_industrial").getString();
            String fullDetail = Component.translatable("gui.tyc2.primitive_mint.requires_industrial_detail").getString();
            // Keep line 2 crisp and unclipped inside 71px LCD width (full detail shown in hover tooltip)
            String warnDetail = canvas.textWidth(fullDetail) <= 115 ? fullDetail : "200x-1000x / Alloys";
            float titleScale = fitScale(canvas, warnTitle, 70, 0.72F);
            float detailScale = fitScale(canvas, warnDetail, 70, 0.68F);
            canvas.drawScaledString(warnTitle, 94, 52, 71, titleScale, 0xFFFFC14D);
            canvas.drawScaledString(warnDetail, 94, 62, 71, detailScale, 0xFFFFE5B4);
        } else {
            String statusStr;
            int statusColor;
            if (prog > 0) {
                int pct = Math.min(100, (prog * 100) / Math.max(1, total));
                statusStr = Component.translatable("gui.tyc2.primitive_mint.minting", pct).getString();
                statusColor = 0xFFFFB84D;
            } else if (burnRem <= 0 && menu.hasInputItems()) {
                statusStr = Component.translatable("gui.tyc2.primitive_mint.no_fuel").getString();
                statusColor = 0xFFFF6B5E;
            } else {
                statusStr = Component.translatable("gui.tyc2.primitive_mint.idle").getString();
                statusColor = 0xFFD9C8A9;
            }
            String scopeHint = Component.translatable("gui.tyc2.primitive_mint.scope_hint").getString();
            canvas.drawScaledString(statusStr, 80, 52, 84, fitScale(canvas, statusStr, 84, 0.80F), statusColor);
            canvas.drawScaledString(scopeHint, 80, 63, 84, fitScale(canvas, scopeHint, 84, 0.72F), 0xFFA6B8C8);
        }
    }

    @Override
    protected void paintOverlayTooltips(ScreenCanvas canvas, int mouseX, int mouseY) {
        int px = mouseX - leftPos;
        int py = mouseY - topPos;

        // Flame tooltip (49, 41, 14x14)
        if (px >= 49 && px < 63 && py >= 41 && py < 55) {
            int burnSec = (menu.burnRemaining() + 19) / 20;
            canvas.drawTooltip(
                    List.of(
                            Component.translatable("gui.tyc2.slot.burn_fuel"),
                            Component.translatable("hud.tyc2.primitive_mint.fuel", burnSec)
                    ),
                    mouseX,
                    mouseY
            );
        } else if (px >= 76 && px < 168 && py >= 48 && py < 73 && menu.isBlockedByIndustrialTier()) {
            canvas.drawTooltip(
                    List.of(
                            Component.translatable("gui.tyc2.primitive_mint.requires_industrial"),
                            Component.translatable("gui.tyc2.primitive_mint.requires_industrial_detail")
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    private static float fitScale(ScreenCanvas canvas, String text, int maxWidth, float preferredScale) {
        int w = canvas.textWidth(text);
        if (w <= 0) {
            return preferredScale;
        }
        float needed = maxWidth / (float) w;
        return Math.max(0.50F, Math.min(preferredScale, needed));
    }
}
