package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.MintMachineMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-exact GUI screen for the Industrial Minting Machine ({@code gui/mint_machine.png}, 176x176).
 */
public final class MintMachineScreen extends TyC2ScreenBase<MintMachineMenu> {

    private static final String GUI_TEX = "mint_machine";

    public MintMachineScreen(MintMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 176);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 82;
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        // Energy gauge fill: (13, 21, 12x46) from UV (176, 32, 12x46) bottom-up
        int storedEu = menu.storedEu();
        int maxEu = menu.maxEu();
        if (storedEu > 0 && maxEu > 0) {
            int filledH = Math.max(1, Math.min(46, (int) Math.round(46.0 * storedEu / (double) maxEu)));
            int offset = 46 - filledH;
            canvas.blit(GUI_TEX, 13, 21 + offset, 176, 32 + offset, 12, filledH);
            // Lit EU bolt at (14, 70) from UV (190, 32, 10x14)
            canvas.blit(GUI_TEX, 14, 70, 190, 32, 10, 14);
        }

        // Pure-electric battery discharge lightning icon: (59, 57, 14x14) from UV (176, 0, 14x14)
        boolean discharging = menu.isBatteryDischarging();
        if (discharging) {
            canvas.blit(GUI_TEX, 59, 57, 176, 0, 14, 14);
        }

        // Minting press arrow: (80, 27, 24x16) from UV (176, 14, 24x16) left-to-right
        int prog = menu.progressTicks();
        int total = menu.totalTicks();
        if (prog > 0 && total > 0) {
            int arrowW = Math.max(1, Math.min(24, (int) Math.round(24.0 * prog / (double) total)));
            canvas.blit(GUI_TEX, 80, 27, 176, 14, arrowW, 16);
        }

        // Small status text under arrow
        String statusStr;
        int statusColor;
        if (prog > 0) {
            int pct = Math.min(100, (prog * 100) / Math.max(1, total));
            statusStr = Component.translatable("gui.mnera.mint.minting", pct).getString();
            statusColor = 0xFF2B6CB0;
        } else if (storedEu <= 0 && !discharging) {
            statusStr = Component.translatable("gui.mnera.mint.no_power").getString();
            statusColor = 0xFFC53030;
        } else {
            statusStr = Component.translatable("gui.mnera.mint.idle").getString();
            statusColor = 0xFF4A5568;
        }
        int statusW = canvas.textWidth(statusStr);
        if (statusW <= 66) {
            canvas.drawClippedString(statusStr, 76, 46, 66, statusColor);
        } else {
            float sc = Math.max(0.62F, 66.0F / (float) statusW);
            canvas.drawScaledString(statusStr, 76, 46, 66, sc, statusColor);
        }
    }

    @Override
    protected void paintOverlayTooltips(ScreenCanvas canvas, int mouseX, int mouseY) {
        int px = mouseX - leftPos;
        int py = mouseY - topPos;

        // Energy gauge tooltip (12, 20, 14x48)
        if (px >= 12 && px < 26 && py >= 20 && py < 68) {
            int eu = menu.storedEu();
            int maxEu = menu.maxEu();
            long fe = TriModePowerMath.euToFe(eu);
            canvas.drawTooltip(
                    List.of(
                            Component.translatable("gui.mnera.energy.eu_fe", eu, maxEu, fe),
                            Component.translatable("gui.mnera.energy.tier", menu.powerTier(), menu.maxInputEuPerTick())
                    ),
                    mouseX,
                    mouseY
            );
        } else if (px >= 59 && px < 73 && py >= 57 && py < 71) {
            canvas.drawTooltip(
                    List.of(Component.translatable("gui.mnera.slot.battery_discharge")),
                    mouseX,
                    mouseY
            );
        }
    }
}
