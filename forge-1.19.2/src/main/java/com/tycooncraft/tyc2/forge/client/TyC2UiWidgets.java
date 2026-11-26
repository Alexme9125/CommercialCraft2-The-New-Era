package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.SupplyCycleClock;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Shared bespoke Forge 1.19.2 dual-theme UI widgets, sprite atlas UV tables, weekly supply
 * countdown formatting, fit-scaled text helpers, and CPI line chart renderer for TyC2 screens.
 */
public final class TyC2UiWidgets {

    // --- Cooperation Hub (cooperation_hub.png, 256x256) Sprite Atlas UVs ---
    public static final int HUB_SLOT_NEUTRAL_U = 0;
    public static final int HUB_SLOT_INBOUND_U = 20;
    public static final int HUB_SLOT_OUTBOUND_U = 40;
    public static final int HUB_SLOT_V = 226;

    public static final int HUB_SEASON_V = 226;
    public static final int HUB_CPI_UP_U = 120;
    public static final int HUB_CPI_DOWN_U = 132;
    public static final int HUB_CPI_STABLE_U = 144;
    public static final int HUB_CPI_V = 226;

    public static final int HUB_AUTO_ON_U = 158;
    public static final int HUB_AUTO_OFF_U = 174;
    public static final int HUB_AUTO_V = 226;

    public static final int HUB_CHUNK_LOADER_U = 192;
    public static final int HUB_MAYOR_CROWN_U = 212;
    public static final int HUB_CALENDAR_U = 232;
    public static final int HUB_BADGE_V = 226;

    public static final int HUB_BIOME_V = 244;
    public static final int HUB_TAG_BIOME_U = 86;
    public static final int HUB_TAG_SEASON_U = 96;
    public static final int HUB_TAG_V = 246;

    // --- Comm Terminal (comm_terminal.png, 256x256) Sprite Atlas UVs ---
    public static final int PDA_BATTERY_GREEN_V = 216;
    public static final int PDA_BATTERY_RED_V = 228;
    public static final int PDA_VILLAGE_PIN_U = 64;
    public static final int PDA_MAYOR_CROWN_U = 82;
    public static final int PDA_SEASON_V = 216;
    public static final int PDA_CALENDAR_U = 160;
    public static final int PDA_ICON_V = 216;

    public static final int PDA_BIOME_V = 236;
    public static final int PDA_TAG_BIOME_U = 150;
    public static final int PDA_TAG_SEASON_U = 160;
    public static final int PDA_TAG_V = 238;

    private TyC2UiWidgets() {
    }

    /** Dual-theme button styling presets matching Cooperation Hub (Gilded Bronze) & Comm Terminal (Tactical PDA). */
    public enum ButtonTheme {
        DEFAULT,
        HUB_TAB,
        HUB_SUBTAB,
        HUB_ACTION,
        HUB_PRIMARY,
        HUB_DANGER,
        PDA_TAB,
        PDA_SUBTAB,
        PDA_ACTION,
        PDA_PRIMARY,
        PDA_DANGER
    }

    /** Returns the U coordinate of the 10x10 biome emblem in {@code cooperation_hub.png} (at {@code v=244}). */
    public static int hubBiomeU(String biomeId) {
        return biomeIndex(biomeId) * 12;
    }

    /** Returns the U coordinate of the 10x10 biome emblem in {@code comm_terminal.png} (at {@code v=236}). */
    public static int pdaBiomeU(String biomeId) {
        return 64 + biomeIndex(biomeId) * 12;
    }

    private static int biomeIndex(String biomeId) {
        if (biomeId == null) {
            return 0;
        }
        return switch (biomeId.toLowerCase(Locale.ROOT)) {
            case "desert" -> 1;
            case "savanna" -> 2;
            case "taiga" -> 3;
            case "snowy" -> 4;
            case "jungle" -> 5;
            case "swamp" -> 6;
            default -> 0;
        };
    }

    /** Returns the U coordinate of the 12x12 season sprite in {@code cooperation_hub.png} (at {@code v=226}), or {@code -1}. */
    public static int hubSeasonU(String seasonKey) {
        if (seasonKey == null) {
            return -1;
        }
        return switch (seasonKey.toLowerCase(Locale.ROOT)) {
            case "spring" -> 62;
            case "summer" -> 76;
            case "autumn" -> 90;
            case "winter" -> 104;
            default -> -1;
        };
    }

    /** Returns the U coordinate of the 12x12 season sprite in {@code comm_terminal.png} (at {@code v=216}), or {@code -1}. */
    public static int pdaSeasonU(String seasonKey) {
        if (seasonKey == null) {
            return -1;
        }
        return switch (seasonKey.toLowerCase(Locale.ROOT)) {
            case "spring" -> 102;
            case "summer" -> 116;
            case "autumn" -> 130;
            case "winter" -> 144;
            default -> -1;
        };
    }

    /** Returns a vibrant ARGB accent color representing the village's biome style. */
    public static int biomeAccentColor(String biomeId) {
        if (biomeId == null) {
            return 0xFF6CD96C;
        }
        return switch (biomeId.toLowerCase(Locale.ROOT)) {
            case "desert" -> 0xFFF2C85B;
            case "savanna" -> 0xFFE88E45;
            case "taiga" -> 0xFF4CA87A;
            case "snowy" -> 0xFF8BE2FF;
            case "jungle" -> 0xFF39D957;
            case "swamp" -> 0xFF9C72D9;
            default -> 0xFF6CD96C;
        };
    }

    /** Returns an RGB text color representing the village's biome style. */
    public static int biomeTextColor(String biomeId) {
        return biomeAccentColor(biomeId) & 0x00FFFFFF;
    }

    /**
     * Formats remaining weekly supply refresh ticks (in Minecraft {@code dayTime} ticks)
     * into a crisp localized calendar countdown such as {@code "5d12h"} or {@code "5天12时"}.
     */
    public static String formatRefreshCountdown(long remainingTicks) {
        long clamped = Math.max(0L, remainingTicks);
        long days = SupplyCycleClock.remainingWholeDays(clamped);
        long hoursInDay = SupplyCycleClock.remainingWholeHoursInDay(clamped);
        long totalHours = SupplyCycleClock.remainingTotalHours(clamped);
        if (days > 0L) {
            if (hoursInDay > 0L) {
                return Component.translatable("gui.tyc2.hub.refresh_days_hours", days, hoursInDay).getString();
            }
            return Component.translatable("gui.tyc2.hub.refresh_days", days).getString();
        }
        if (totalHours > 0L) {
            return Component.translatable("gui.tyc2.hub.refresh_hours", totalHours).getString();
        }
        return Component.translatable("gui.tyc2.hub.refresh_soon").getString();
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
        drawFittedStringScaled(poseStack, font, text, x, y, maxWidth, 1.0F, minScale, color, false);
    }

    /**
     * Draws {@code text} at {@code (x, y)} with an upper scale cap {@code maxScale}, scaling down to
     * {@code minScale} if needed to fit within {@code maxWidth}.
     */
    public static void drawFittedStringScaled(
            PoseStack poseStack,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            float maxScale,
            float minScale,
            int color,
            boolean shadow
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }
        int w = font.width(text);
        if (w <= 0) {
            return;
        }
        float needed = maxWidth / (float) w;
        float scale = Math.min(maxScale, Math.max(minScale, needed));
        String rendered = (w * scale <= maxWidth + 0.25F)
                ? text
                : font.plainSubstrByWidth(text, Math.max(1, (int) Math.floor(maxWidth / scale)));
        if (Math.abs(scale - 1.0F) < 0.005F) {
            if (shadow) {
                font.drawShadow(poseStack, rendered, x, y, color);
            } else {
                font.draw(poseStack, rendered, x, y, color);
            }
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        if (shadow) {
            font.drawShadow(poseStack, rendered, 0, 0, color);
        } else {
            font.draw(poseStack, rendered, 0, 0, color);
        }
        poseStack.popPose();
    }

    /** Draws right-aligned fit-scaled text ending at {@code rightX}. */
    public static void drawRightFittedString(
            PoseStack poseStack,
            Font font,
            String text,
            int rightX,
            int y,
            int maxWidth,
            float maxScale,
            float minScale,
            int color
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }
        int w = font.width(text);
        if (w <= 0) {
            return;
        }
        float needed = maxWidth / (float) w;
        float scale = Math.min(maxScale, Math.max(minScale, needed));
        String rendered = (w * scale <= maxWidth + 0.25F)
                ? text
                : font.plainSubstrByWidth(text, Math.max(1, (int) Math.floor(maxWidth / scale)));
        float drawnW = font.width(rendered) * scale;
        float tx = rightX - drawnW;
        poseStack.pushPose();
        poseStack.translate(tx, y, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        font.draw(poseStack, rendered, 0, 0, color);
        poseStack.popPose();
    }

    /** Draws horizontally centered fit-scaled text inside {@code [x, x + width]}. */
    public static void drawCenteredFittedString(
            PoseStack poseStack,
            Font font,
            String text,
            int x,
            int y,
            int width,
            float maxScale,
            float minScale,
            int color
    ) {
        if (text == null || text.isEmpty() || width <= 0) {
            return;
        }
        int w = font.width(text);
        if (w <= 0) {
            return;
        }
        float needed = width / (float) w;
        float scale = Math.min(maxScale, Math.max(minScale, needed));
        String rendered = (w * scale <= width + 0.25F)
                ? text
                : font.plainSubstrByWidth(text, Math.max(1, (int) Math.floor(width / scale)));
        float drawnW = font.width(rendered) * scale;
        float tx = x + (width - drawnW) * 0.5F;
        poseStack.pushPose();
        poseStack.translate(tx, y, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        font.draw(poseStack, rendered, 0, 0, color);
        poseStack.popPose();
    }

    /** Draws a compact rounded-feel bordered pill badge with centered fit-scaled text. */
    public static void drawPillBadge(
            PoseStack poseStack,
            Font font,
            int x,
            int y,
            int w,
            int h,
            int bgArgb,
            int borderArgb,
            String text,
            int textRgb
    ) {
        GuiComponent.fill(poseStack, x, y, x + w, y + h, borderArgb);
        GuiComponent.fill(poseStack, x + 1, y + 1, x + w - 1, y + h - 1, bgArgb);
        if (text != null && !text.isEmpty()) {
            int availW = Math.max(2, w - 3);
            int rawW = font.width(text);
            float maxScale = h <= 9 ? 0.72F : 0.80F;
            float needed = rawW > 0 ? availW / (float) rawW : 1.0F;
            float scale = Math.min(maxScale, Math.max(0.46F, needed));
            String label = (rawW * scale <= availW + 0.25F)
                    ? text
                    : font.plainSubstrByWidth(text, Math.max(1, (int) Math.floor(availW / scale)));
            float drawnW = font.width(label) * scale;
            float drawnH = font.lineHeight * scale;
            float tx = x + (w - drawnW) * 0.5F;
            float ty = y + (h - drawnH) * 0.5F + 0.5F;
            poseStack.pushPose();
            poseStack.translate(tx, ty, 0.0F);
            poseStack.scale(scale, scale, 1.0F);
            font.draw(poseStack, label, 0, 0, textRgb);
            poseStack.popPose();
        }
    }

    /**
     * Bespoke dual-theme metallic/tactical button that renders crisply at heights as small as 11-16px,
     * with beveled edges, active tab top accent bars, hover glow, and zero-clipping fit-scaled text.
     */
    public static class CompactButton extends Button {
        protected boolean highlighted = false;
        protected int accentColor = 0xFF3A7BD5;
        protected boolean customAccent = false;
        protected ButtonTheme theme = ButtonTheme.DEFAULT;

        public CompactButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress);
        }

        public CompactButton(int x, int y, int width, int height, Component message, OnPress onPress, ButtonTheme theme) {
            super(x, y, width, height, message, onPress);
            this.theme = theme == null ? ButtonTheme.DEFAULT : theme;
        }

        public CompactButton setTheme(ButtonTheme theme) {
            this.theme = theme == null ? ButtonTheme.DEFAULT : theme;
            return this;
        }

        public CompactButton setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
            return this;
        }

        public CompactButton setAccentColor(int argb) {
            this.accentColor = argb;
            this.customAccent = true;
            return this;
        }

        public CompactButton clearAccentColor() {
            this.customAccent = false;
            return this;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
            boolean hover = this.isHoveredOrFocused();
            Palette p = resolvePalette(hover);

            // Outer border + inner body
            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, p.border);
            fill(poseStack, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, p.bg);

            // Crisp top highlight bevel & bottom shadow bevel
            if (this.height >= 8 && this.width >= 6) {
                if (p.topBar != 0) {
                    int barH = (theme == ButtonTheme.HUB_TAB || theme == ButtonTheme.PDA_TAB) && this.height >= 13 ? 2 : 1;
                    fill(poseStack, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + 1 + barH, p.topBar);
                    if (barH == 2 && p.topBarHi != 0) {
                        fill(poseStack, this.x + 2, this.y + 1, this.x + this.width - 2, this.y + 2, p.topBarHi);
                    }
                } else if (p.topBevel != 0) {
                    fill(poseStack, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + 2, p.topBevel);
                }
                if (p.bottomShadow != 0) {
                    fill(poseStack, this.x + 1, this.y + this.height - 2, this.x + this.width - 1, this.y + this.height - 1, p.bottomShadow);
                }
                if (hover && this.active && p.hoverGlow != 0) {
                    fill(poseStack, this.x + 1, this.y + 1, this.x + 2, this.y + this.height - 1, p.hoverGlow);
                    fill(poseStack, this.x + this.width - 2, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, p.hoverGlow);
                }
            }

            renderCenteredLabel(poseStack, p.textRgb);
        }

        protected void renderCenteredLabel(PoseStack poseStack, int textColor) {
            Font font = Minecraft.getInstance().font;
            String raw = this.getMessage().getString();
            if (raw.isEmpty()) {
                return;
            }
            int availW = Math.max(4, this.width - 4);
            int rawW = font.width(raw);
            float maxScale = this.height <= 11 ? 0.85F : 1.0F;
            float minScale = 0.50F;
            float needed = rawW > 0 ? availW / (float) rawW : 1.0F;
            float scale = Math.min(maxScale, Math.max(minScale, needed));
            String label = (rawW * scale <= availW + 0.25F)
                    ? raw
                    : font.plainSubstrByWidth(raw, Math.max(1, (int) Math.floor(availW / scale)));
            float scaledW = font.width(label) * scale;
            float scaledH = font.lineHeight * scale;
            float tx = this.x + (this.width - scaledW) * 0.5F;
            float ty = this.y + (this.height - scaledH) * 0.5F + 0.5F;
            if (Math.abs(scale - 1.0F) < 0.005F) {
                int ix = Math.round(tx);
                int iy = Math.max(this.y + 1, Math.round(ty));
                if (this.active) {
                    font.drawShadow(poseStack, label, ix, iy, textColor);
                } else {
                    font.draw(poseStack, label, ix, iy, textColor);
                }
            } else {
                poseStack.pushPose();
                poseStack.translate(tx, ty, 0.0F);
                poseStack.scale(scale, scale, 1.0F);
                if (this.active) {
                    font.drawShadow(poseStack, label, 0, 0, textColor);
                } else {
                    font.draw(poseStack, label, 0, 0, textColor);
                }
                poseStack.popPose();
            }
        }

        private record Palette(int bg, int border, int topBevel, int bottomShadow, int topBar, int topBarHi, int hoverGlow, int textRgb) {
        }

        private Palette resolvePalette(boolean hover) {
            if (!this.active) {
                boolean pda = theme.name().startsWith("PDA_");
                return pda
                        ? new Palette(0xFF0E1924, 0xFF192A3A, 0xFF152534, 0xFF070E15, 0, 0, 0, 0x5B7387)
                        : new Palette(0xFF282E38, 0xFF1B2028, 0xFF343B47, 0xFF191D24, 0, 0, 0, 0x7A8390);
            }
            return switch (theme) {
                case HUB_TAB -> this.highlighted
                        ? new Palette(0xFF213756, 0xFFD9A630, 0, 0xFF121F33, 0xFFD99E26, 0xFFFFEE96, 0x44FFE07A, 0xFFF5D0)
                        : (hover
                        ? new Palette(0xFF253850, 0xFFB88A30, 0xFF365070, 0xFF142030, 0, 0, 0x33F0C246, 0xFFF2D6)
                        : new Palette(0xFF182433, 0xFF3E3524, 0xFF24344A, 0xFF0F1722, 0, 0, 0, 0xC6BFA8));
                case HUB_SUBTAB -> this.highlighted
                        ? new Palette(0xFF233D5C, 0xFFD9A836, 0, 0xFF132236, 0xFFFFE57A, 0, 0x33FFE57A, 0xFFF4CC)
                        : (hover
                        ? new Palette(0xFF213349, 0xFF8C774E, 0xFF324A68, 0xFF121C29, 0, 0, 0x28FFD866, 0xECEFF4)
                        : new Palette(0xFF16212F, 0xFF2C3B4E, 0xFF203044, 0xFF0D141E, 0, 0, 0, 0xA8B8CA));
                case HUB_ACTION -> {
                    if (this.highlighted) {
                        int base = customAccent ? accentColor : 0xFF245282;
                        int bg = hover ? brighten(base, 18) : base;
                        yield new Palette(bg, 0xFF7EC2FF, brighten(bg, 26), darken(bg, 28), 0, 0, 0x4099D6FF, 0xFFFFFF);
                    }
                    int base = customAccent ? accentColor : 0xFF223042;
                    int bg = hover ? brighten(base, 16) : base;
                    yield new Palette(bg, hover ? 0xFFD4A843 : 0xFF45576E, brighten(bg, 22), darken(bg, 26), 0, 0, 0x33F2C44D, hover ? 0xFFFFFF : 0xE2EAF2);
                }
                case HUB_PRIMARY -> {
                    int base = customAccent ? accentColor : 0xFF1E5C3B;
                    int bg = hover ? brighten(base, 18) : base;
                    int border = customAccent ? brighten(base, 54) : (hover ? 0xFF70F0A4 : 0xFF3DB870);
                    yield new Palette(bg, border, brighten(bg, 28), darken(bg, 30), 0, 0, 0x4080FFAA, 0xF4FFF8);
                }
                case HUB_DANGER -> {
                    int base = this.highlighted ? 0xFF9C252A : 0xFF6B2024;
                    int bg = hover ? brighten(base, 20) : base;
                    yield new Palette(bg, hover || this.highlighted ? 0xFFFF7A80 : 0xFFB04046, brighten(bg, 26), darken(bg, 30), 0, 0, 0x44FF7A80, 0xFFF0F0);
                }
                case PDA_TAB -> this.highlighted
                        ? new Palette(0xFF12364E, 0xFF34E2FF, 0, 0xFF091D2B, 0xFF20B5D8, 0xFF8FF7FF, 0x445CEEFF, 0xE8FDFF)
                        : (hover
                        ? new Palette(0xFF132C40, 0xFF2EAAC8, 0xFF1F4663, 0xFF091622, 0, 0, 0x334CEEFF, 0xD6F7FF)
                        : new Palette(0xFF0C1A27, 0xFF1B384F, 0xFF13273A, 0xFF071018, 0, 0, 0, 0x8CB2C9));
                case PDA_SUBTAB -> this.highlighted
                        ? new Palette(0xFF123850, 0xFF32DEFF, 0, 0xFF091D2B, 0xFF7AF4FF, 0, 0x3360F0FF, 0xFFFFFF)
                        : (hover
                        ? new Palette(0xFF132B3E, 0xFF2D9DB8, 0xFF1E4460, 0xFF08141F, 0, 0, 0x2855EEFF, 0xDCF8FF)
                        : new Palette(0xFF0B1824, 0xFF1A3448, 0xFF122536, 0xFF060E16, 0, 0, 0, 0x88ADC4));
                case PDA_ACTION -> {
                    if (this.highlighted) {
                        int base = customAccent ? accentColor : 0xFF134B69;
                        int bg = hover ? brighten(base, 18) : base;
                        yield new Palette(bg, 0xFF4BEBFF, brighten(bg, 26), darken(bg, 26), 0, 0, 0x4060F0FF, 0xFFFFFF);
                    }
                    int base = customAccent ? accentColor : 0xFF112435;
                    int bg = hover ? brighten(base, 16) : base;
                    yield new Palette(bg, hover ? 0xFF38DDFC : 0xFF234763, brighten(bg, 20), darken(bg, 24), 0, 0, 0x334CEEFF, hover ? 0xFFFFFF : 0xCBE6F7);
                }
                case PDA_PRIMARY -> {
                    int base = customAccent ? accentColor : 0xFF0F5246;
                    int bg = hover ? brighten(base, 18) : base;
                    int border = customAccent ? brighten(base, 54) : (hover ? 0xFF60FFE0 : 0xFF29C4A6);
                    yield new Palette(bg, border, brighten(bg, 26), darken(bg, 28), 0, 0, 0x4066FFE0, 0xEEFFF9);
                }
                case PDA_DANGER -> {
                    int base = this.highlighted ? 0xFF8C222B : 0xFF5C1B22;
                    int bg = hover ? brighten(base, 20) : base;
                    yield new Palette(bg, hover || this.highlighted ? 0xFFFF707C : 0xFFB03A46, brighten(bg, 24), darken(bg, 28), 0, 0, 0x44FF707C, 0xFFEEF0);
                }
                case DEFAULT -> this.highlighted
                        ? new Palette(hover ? brighten(accentColor, 16) : accentColor, 0xFF88CCFF, brighten(accentColor, 26), darken(accentColor, 26), 0, 0, 0x3388CCFF, 0xFFFFFF)
                        : (hover
                        ? new Palette(0xFF566272, 0xFF9EB4CC, 0xFF6E7D90, 0xFF38404A, 0, 0, 0x229EB4CC, 0xFFFFFF)
                        : new Palette(0xFF404854, 0xFF252A30, 0xFF535D6C, 0xFF2A3038, 0, 0, 0, 0xECEFF4));
            };
        }

        private static int brighten(int argb, int delta) {
            int a = (argb >>> 24) & 0xFF;
            int r = Math.min(255, ((argb >>> 16) & 0xFF) + delta);
            int g = Math.min(255, ((argb >>> 8) & 0xFF) + delta);
            int b = Math.min(255, (argb & 0xFF) + delta);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int darken(int argb, int delta) {
            int a = (argb >>> 24) & 0xFF;
            int r = Math.max(0, ((argb >>> 16) & 0xFF) - delta);
            int g = Math.max(0, ((argb >>> 8) & 0xFF) - delta);
            int b = Math.max(0, (argb & 0xFF) - delta);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /** Renders a localized price-trend line chart inside {@code (x, y, w, h)} using the Hub theme. */
    public static void drawCpiLineChart(
            PoseStack poseStack,
            Font font,
            int x,
            int y,
            int w,
            int h,
            double[] history
    ) {
        drawCpiLineChart(poseStack, font, x, y, w, h, history, false);
    }

    /**
     * Renders an upgraded localized price-trend line chart inside {@code (x, y, w, h)} with subtle
     * horizontal gridlines, a crisp dashed {@code 1.00} baseline, translucent area shading, vibrant
     * trend line, and glowing highlight dot on the current CPI value.
     */
    public static void drawCpiLineChart(
            PoseStack poseStack,
            Font font,
            int x,
            int y,
            int w,
            int h,
            double[] history,
            boolean pdaTheme
    ) {
        int outerBorder = pdaTheme ? 0xFF1D445E : 0xFF3E3524;
        int innerBg = pdaTheme ? 0xFF091420 : 0xFF0E1722;
        int headerBg = pdaTheme ? 0xFF0E1F30 : 0xFF162334;
        int gridCol = pdaTheme ? 0x264888AA : 0x267A90AA;
        int baseDashCol = pdaTheme ? 0x9970C8E8 : 0x99A0B8D0;
        int boundTextCol = pdaTheme ? 0x7EC2DA : 0x94ACC2;
        int titleCol = pdaTheme ? 0x7CF4FF : 0xFFE699;

        GuiComponent.fill(poseStack, x, y, x + w, y + h, outerBorder);
        GuiComponent.fill(poseStack, x + 1, y + 1, x + w - 1, y + h - 1, innerBg);
        GuiComponent.fill(poseStack, x + 1, y + 1, x + w - 1, y + 11, headerBg);
        GuiComponent.fill(poseStack, x + 1, y + 11, x + w - 1, y + 12, pdaTheme ? 0xFF17364C : 0xFF28384D);

        int plotX = x + 4;
        int plotY = y + 14;
        int plotW = Math.max(10, w - 8);
        int plotH = Math.max(10, h - 18);

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

        // Subtle horizontal reference gridlines (25%, 50%, 75%)
        for (int g = 1; g <= 3; g++) {
            int gy = plotY + (plotH * g) / 4;
            GuiComponent.fill(poseStack, plotX, gy, plotX + plotW, gy + 1, gridCol);
        }

        // Crisp dashed CPI = 1.00 baseline
        int base1Y = plotY + plotH - (int) Math.round(((1.0 - minV) / range) * plotH);
        base1Y = Mth.clamp(base1Y, plotY, plotY + plotH);
        for (int gx = plotX; gx < plotX + plotW; gx += 5) {
            GuiComponent.fill(poseStack, gx, base1Y, Math.min(plotX + plotW, gx + 3), base1Y + 1, baseDashCol);
        }

        // Top header bounds + localized centered chart title
        String maxLabel = String.format(Locale.ROOT, "%.2f", maxV);
        String minLabel = String.format(Locale.ROOT, "%.2f", minV);
        float boundScale = 0.78F;
        int maxW = Math.round(font.width(maxLabel) * boundScale);
        int minW = Math.round(font.width(minLabel) * boundScale);
        drawFittedStringScaled(poseStack, font, maxLabel, x + 3, y + 3, maxW + 2, boundScale, 0.55F, boundTextCol, false);
        drawRightFittedString(poseStack, font, minLabel, x + w - 3, y + 3, minW + 2, boundScale, 0.55F, boundTextCol);

        int titleArea = w - maxW - minW - 12;
        if (titleArea >= 20) {
            String chartTitle = Component.translatable("gui.tyc2.economy.cpi_chart").getString();
            drawCenteredFittedString(poseStack, font, chartTitle, x + maxW + 6, y + 3, titleArea, 0.80F, 0.52F, titleCol);
        }

        if (history == null || history.length == 0) {
            return;
        }
        int n = history.length;
        int prevPx = -1;
        int prevPy = -1;
        int lastPx = plotX;
        int lastPy = base1Y;
        int lastColor = 0xFF5CF298;

        for (int i = 0; i < n; i++) {
            double v = Double.isFinite(history[i]) ? history[i] : 1.0;
            int px = n == 1
                    ? plotX + plotW / 2
                    : plotX + (int) Math.round(((double) i / (n - 1)) * (plotW - 1));
            int py = plotY + plotH - (int) Math.round(((v - minV) / range) * plotH);
            py = Mth.clamp(py, plotY, plotY + plotH);

            int lineColor = v > 1.08 ? 0xFFFF7658 : (v < 0.92 ? 0xFF58E4FF : 0xFF5CF298);
            int fillColor = (lineColor & 0x00FFFFFF) | 0x20000000;
            if (prevPx >= 0) {
                // Soft vertical wash below the segment for high visual readability
                int span = Math.max(1, px - prevPx);
                for (int sx = prevPx; sx <= px; sx++) {
                    float t = (sx - prevPx) / (float) span;
                    int sy = Math.round(prevPy + (py - prevPy) * t);
                    if (sy < plotY + plotH) {
                        GuiComponent.fill(poseStack, sx, sy + 1, sx + 1, plotY + plotH, fillColor);
                    }
                }
                drawLine(poseStack, prevPx, prevPy, px, py, lineColor);
            }
            GuiComponent.fill(poseStack, px, py, px + 1, py + 1, lineColor);
            prevPx = px;
            prevPy = py;
            lastPx = px;
            lastPy = py;
            lastColor = lineColor;
        }

        // Glowing current CPI dot on the latest data point
        int haloColor = (lastColor & 0x00FFFFFF) | 0x77000000;
        GuiComponent.fill(poseStack, lastPx - 2, lastPy - 2, lastPx + 3, lastPy + 3, haloColor);
        GuiComponent.fill(poseStack, lastPx - 1, lastPy - 1, lastPx + 2, lastPy + 2, lastColor);
        GuiComponent.fill(poseStack, lastPx, lastPy, lastPx + 1, lastPy + 1, 0xFFFFFFFF);
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
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
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
