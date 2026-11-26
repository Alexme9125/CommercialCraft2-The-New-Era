package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared compact Forge 1.19.2 UI widgets, fit-scaled text helpers, and CPI line chart renderer
 * for TyC2 screens.
 */
public final class TyC2UiWidgets {

    private TyC2UiWidgets() {
    }

    /**
     * Draws {@code text} at {@code (x, y)} scaled cleanly to fit within {@code maxWidth} without
     * float-truncation cutoff of the final glyph.
     */
    public static void drawFittedString(
            PoseStack poseStack,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            float minScale,
            int color
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }
        int w = font.width(text);
        if (w <= maxWidth) {
            font.draw(poseStack, text, x, y, color);
            return;
        }
        float needed = maxWidth / (float) w;
        float scale = Math.max(minScale, needed);
        String rendered = needed >= minScale
                ? text
                : font.plainSubstrByWidth(text, (int) Math.floor(maxWidth / scale));
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        font.draw(poseStack, rendered, 0, 0, color);
        poseStack.popPose();
    }

    /**
     * Compact metallic/industrial button that renders crisply at heights as small as 11-14px and
     * automatically fit-scales localized {@code zh_cn}/{@code en_us} labels to prevent clipping.
     */
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
            String raw = this.getMessage().getString();
            int availW = Math.max(4, this.width - 3);
            int rawW = font.width(raw);
            int textColor = this.active ? (this.highlighted ? 0xFFFFFF : 0xECEFF4) : 0x8A929C;
            if (rawW <= availW) {
                int textX = this.x + (this.width - rawW) / 2;
                int textY = this.y + Math.max(1, (this.height - font.lineHeight) / 2);
                font.draw(poseStack, raw, textX, textY, textColor);
            } else {
                float minScale = 0.50F;
                float needed = availW / (float) rawW;
                float scale = Math.max(minScale, needed);
                String label = needed >= minScale
                        ? raw
                        : font.plainSubstrByWidth(raw, (int) Math.floor(availW / scale));
                float scaledW = font.width(label) * scale;
                float scaledH = font.lineHeight * scale;
                float tx = this.x + (this.width - scaledW) * 0.5F;
                float ty = this.y + Math.max(1.0F, (this.height - scaledH) * 0.5F);
                poseStack.pushPose();
                poseStack.translate(tx, ty, 0.0F);
                poseStack.scale(scale, scale, 1.0F);
                font.draw(poseStack, label, 0, 0, textColor);
                poseStack.popPose();
            }
        }
    }

    /**
     * Renders a localized price-trend line chart inside {@code (x, y, w, h)}.
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
        int plotY = y + 11;
        int plotW = Math.max(10, w - 6);
        int plotH = Math.max(10, h - 14);

        double minV = 0.75;
        double maxV = 1.35;
        if (history != null) {
            for (double v : history) {
                if (!Double.isFinite(v)) {
                    continue;
                }
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

        // Top scale bounds + localized centered chart title ("Price Trend" / "物价走势")
        String maxLabel = String.format("%.2f", maxV);
        String minLabel = String.format("%.2f", minV);
        int maxW = font.width(maxLabel);
        int minW = font.width(minLabel);
        font.draw(poseStack, maxLabel, plotX, y + 2, 0x7FA4B8);
        font.draw(poseStack, minLabel, plotX + plotW - minW, y + 2, 0x7FA4B8);

        int titleArea = plotW - maxW - minW - 6;
        if (titleArea >= 24) {
            String chartTitle = Component.translatable("gui.tyc2.economy.cpi_chart").getString();
            int tw = font.width(chartTitle);
            float scale = tw <= titleArea ? 1.0F : Math.max(0.55F, titleArea / (float) tw);
            String rendered = tw <= titleArea || titleArea / (float) tw >= 0.55F
                    ? chartTitle
                    : font.plainSubstrByWidth(chartTitle, (int) Math.floor(titleArea / scale));
            float renderedW = font.width(rendered) * scale;
            float tx = plotX + maxW + 3 + (titleArea - renderedW) * 0.5F;
            poseStack.pushPose();
            poseStack.translate(tx, y + 2.0F, 0.0F);
            poseStack.scale(scale, scale, 1.0F);
            font.draw(poseStack, rendered, 0, 0, 0xA8D4EC);
            poseStack.popPose();
        }

        if (history == null || history.length == 0) {
            return;
        }
        int n = history.length;
        int prevPx = -1;
        int prevPy = -1;
        for (int i = 0; i < n; i++) {
            double v = Double.isFinite(history[i]) ? history[i] : 1.0;
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

    /** Formats large IC values compactly (e.g., {@code 12500 -> "12.5k"}). */
    public static String compactIc(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (abs >= 10_000L) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    /** Resolves a client-localized display name for a vanilla or modded villager profession. */
    public static String localizedProfessionName(
            String professionId, String archetypeName, String displayNameFallback
    ) {
        WorkplaceArchetype arch = WorkplaceArchetype.fromNameOrDefault(archetypeName, WorkplaceArchetype.GENERIC);
        if (arch != WorkplaceArchetype.GENERIC) {
            return Component.translatable(arch.langKey()).getString();
        }
        ResourceLocation rl = TyC2Ids.parse(professionId);
        if (rl != null) {
            String mcKey = "entity.minecraft.villager."
                    + ("minecraft".equals(rl.getNamespace())
                    ? rl.getPath()
                    : rl.getNamespace() + "." + rl.getPath());
            if (I18n.exists(mcKey)) {
                return Component.translatable(mcKey).getString();
            }
            String modKey = "profession." + rl.getNamespace() + "." + rl.getPath();
            if (I18n.exists(modKey)) {
                return Component.translatable(modKey).getString();
            }
        }
        if (displayNameFallback != null && !displayNameFallback.isBlank()) {
            return displayNameFallback;
        }
        return Component.translatable("profession.tyc2.generic").getString();
    }

    /** Formats an aid project status string from {@link S2CHubSyncPacket.AidProjectDto}. */
    public static String formatAidStatus(S2CHubSyncPacket.AidProjectDto aid) {
        return switch (aid.statusName()) {
            case "COLLECTING" -> Component.translatable("gui.tyc2.aid.status.collecting", aid.progressPct()).getString();
            case "WAITING_SITE" -> Component.translatable("gui.tyc2.aid.status.waiting_site").getString();
            case "NO_SITE" -> Component.translatable("gui.tyc2.aid.status.no_site").getString();
            case "BUILDING" -> Component.translatable("gui.tyc2.aid.status.building", aid.progressPct()).getString();
            default -> Component.translatable("gui.tyc2.aid.status.collecting", aid.progressPct()).getString();
        };
    }

    /** Builds the hover tooltip lines displaying gathered/required materials for an aid construction project. */
    public static List<Component> buildAidMaterialsTooltip(S2CHubSyncPacket.AidProjectDto aid) {
        List<Component> tips = new ArrayList<>();
        String profName = localizedProfessionName(aid.professionId(), aid.archetypeName(), aid.displayNameFallback());
        Component sizeComp = Component.translatable(
                aid.large() ? "gui.tyc2.aid.size.large" : "gui.tyc2.aid.size.small"
        );
        tips.add(Component.literal(profName + " - ").append(sizeComp).withStyle(ChatFormatting.GOLD));
        tips.add(Component.translatable("gui.tyc2.aid.materials_tooltip").withStyle(ChatFormatting.AQUA));
        for (S2CHubSyncPacket.MatDto m : aid.materials()) {
            boolean done = m.delivered() >= m.required();
            String itemName = TyC2Ids.localizedItemName(m.itemId(), null);
            tips.add(Component.literal("  " + itemName + ": " + m.delivered() + "/" + m.required())
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        tips.add(Component.translatable("gui.tyc2.aid.cost", aid.paidIc()).withStyle(ChatFormatting.GRAY));
        return tips;
    }
}
