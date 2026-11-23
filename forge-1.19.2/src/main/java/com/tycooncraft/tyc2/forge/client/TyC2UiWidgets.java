package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Shared compact Forge 1.19.2 UI widgets and 48-point CPI line chart renderer for TyC2 screens.
 */
public final class TyC2UiWidgets {

    private TyC2UiWidgets() {
    }

    /** Compact metallic/industrial button that renders crisply at heights as small as 11-14px. */
    public static final class CompactButton extends Button {
        private boolean highlighted = false;
        private int accentColor = 0xFF3A7BD5;

        public CompactButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress);
        }

        public CompactButton setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
            return this;
        }

        public CompactButton setAccentColor(int argb) {
            this.accentColor = argb;
            return this;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
            int bg;
            int border;
            if (!this.active) {
                bg = 0xFF3A3E44;
                border = 0xFF22252A;
            } else if (this.highlighted) {
                bg = this.isHoveredOrFocused() ? 0xFF2B6CB0 : accentColor;
                border = 0xFF88CCFF;
            } else if (this.isHoveredOrFocused()) {
                bg = 0xFF5A6574;
                border = 0xFF9EB4CC;
            } else {
                bg = 0xFF424A54;
                border = 0xFF252A30;
            }
            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, border);
            fill(poseStack, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, bg);

            Font font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(this.getMessage().getString(), Math.max(4, this.width - 2));
            int textColor = this.active ? (this.highlighted ? 0xFFFFFF : 0xECEFF4) : 0x8A929C;
            int textX = this.x + (this.width - font.width(label)) / 2;
            int textY = this.y + Math.max(1, (this.height - font.lineHeight) / 2);
            font.draw(poseStack, label, textX, textY, textColor);
        }
    }

    /**
     * Renders an interactive 48-point CPI & Inflation trend line chart inside {@code (x, y, w, h)}.
     */
    public static void drawCpiLineChart(
            PoseStack poseStack,
            Font font,
            int x,
            int y,
            int w,
            int h,
            double[] history
    ) {
        GuiComponent.fill(poseStack, x, y, x + w, y + h, 0xFF0E161E);
        GuiComponent.fill(poseStack, x + 1, y + 1, x + w - 1, y + h - 1, 0xFF13202B);

        int plotX = x + 3;
        int plotY = y + 10;
        int plotW = Math.max(10, w - 6);
        int plotH = Math.max(10, h - 14);

        double minV = 0.75;
        double maxV = 1.35;
        if (history != null) {
            for (double v : history) {
                if (v < minV) {
                    minV = Math.max(0.20, v - 0.08);
                }
                if (v > maxV) {
                    maxV = Math.min(4.20, v + 0.12);
                }
            }
        }
        double range = Math.max(0.1, maxV - minV);

        // Gridlines & CPI = 1.00 baseline
        int base1Y = plotY + plotH - (int) Math.round(((1.0 - minV) / range) * plotH);
        base1Y = Mth.clamp(base1Y, plotY, plotY + plotH);
        for (int gx = plotX; gx < plotX + plotW; gx += 4) {
            GuiComponent.fill(poseStack, gx, base1Y, Math.min(plotX + plotW, gx + 2), base1Y + 1, 0x8088AABB);
        }

        // Top mini scale labels
        font.draw(poseStack, String.format("%.2f", maxV), plotX, y + 2, 0x7FA4B8);
        String minLabel = String.format("%.2f", minV);
        font.draw(poseStack, minLabel, plotX + plotW - font.width(minLabel), y + 2, 0x7FA4B8);

        if (history == null || history.length == 0) {
            return;
        }
        int n = history.length;
        int prevPx = -1;
        int prevPy = -1;
        for (int i = 0; i < n; i++) {
            double v = history[i];
            int px = n == 1
                    ? plotX + plotW / 2
                    : plotX + (int) Math.round(((double) i / (n - 1)) * (plotW - 1));
            int py = plotY + plotH - (int) Math.round(((v - minV) / range) * plotH);
            py = Mth.clamp(py, plotY, plotY + plotH);

            int lineColor = v > 1.08 ? 0xFFFF7050 : (v < 0.92 ? 0xFF55DDFF : 0xFF55EE88);
            if (prevPx >= 0) {
                drawLine(poseStack, prevPx, prevPy, px, py, lineColor);
            }
            GuiComponent.fill(poseStack, px - 1, py - 1, px + 1, py + 1, lineColor);
            prevPx = px;
            prevPy = py;
        }
    }

    /** Draws a 1px Bresenham line using {@link GuiComponent#fill}. */
    private static void drawLine(PoseStack poseStack, int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int cx = x0;
        int cy = y0;
        while (true) {
            GuiComponent.fill(poseStack, cx, cy, cx + 1, cy + 1, argb);
            if (cx == x1 && cy == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    /** Formats large IC values compactly for tight LCD boxes. */
    public static String compactIc(long value) {
        if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }
}
