package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.village.SupplyCycleClock;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Shared visual design tokens, UV coordinate tables, button style palettes, and HUD/GUI
 * formatting utilities for the bespoke dual-theme Cooperation Hub ("Gilded Bronze & Deep Ledger")
 * and Commerce Comm Terminal ("Deep-Space Cyan/Teal Tactical PDA") interfaces.
 */
public final class TyC2Theme {

    // --- Hub Texture Constants (cooperation_hub.png, 256x256) ---
    public static final String HUB_TEX = "cooperation_hub";
    public static final int HUB_SPRITE_V = 226;
    public static final int HUB_BIOME_V = 244;
    public static final int HUB_TAG_V = 246;
    public static final int HUB_SLOT_NEUTRAL_U = 0;
    public static final int HUB_SLOT_INBOUND_U = 20;
    public static final int HUB_SLOT_OUTBOUND_U = 40;
    public static final int HUB_AUTO_ON_U = 158;
    public static final int HUB_AUTO_OFF_U = 174;
    public static final int HUB_CHUNK_LOADER_U = 192;
    public static final int HUB_MAYOR_CROWN_U = 212;
    public static final int HUB_CALENDAR_U = 232;
    public static final int HUB_TAG_BIOME_U = 86;
    public static final int HUB_TAG_SEASON_U = 96;

    // --- Comm Terminal Texture Constants (comm_terminal.png, 256x256) ---
    public static final String PDA_TEX = "comm_terminal";
    public static final int PDA_SPRITE_V = 216;
    public static final int PDA_BIOME_V = 236;
    public static final int PDA_TAG_V = 238;
    public static final int PDA_BATTERY_GREEN_V = 216;
    public static final int PDA_BATTERY_RED_V = 228;
    public static final int PDA_VILLAGE_PIN_U = 64;
    public static final int PDA_MAYOR_CROWN_U = 82;
    public static final int PDA_CALENDAR_U = 160;
    public static final int PDA_TAG_BIOME_U = 150;
    public static final int PDA_TAG_SEASON_U = 160;

    // --- Semantic Order & Accent Colors ---
    public static final int STRIPE_SELL = 0xFF3B9EFF;
    public static final int STRIPE_BUY = 0xFFF0A830;
    public static final int STRIPE_DONE = 0xFF4B5E72;

    public static final int GOLD_TEXT = 0xFFFFE57F;
    public static final int GOLD_BRIGHT = 0xFFFFF2B0;
    public static final int CYAN_TEXT = 0xFF8EEAFF;
    public static final int MINT_TEXT = 0xFF66FF99;
    public static final int MUTED_TEXT = 0xFF8AA0B8;
    public static final int ICE_WHITE = 0xFFEAF6FF;
    public static final int DANGER_TEXT = 0xFFFF6E68;

    private TyC2Theme() {
    }

    /** Preset visual themes for custom-rendered {@code StyledButton} controls across Hub & PDA screens. */
    public enum ButtonStyle {
        HUB_TAB,
        HUB_SUBTAB,
        HUB_ACTION,
        HUB_PRIMARY,
        HUB_BUY,
        HUB_DANGER,
        PDA_TAB,
        PDA_SUBTAB,
        PDA_ACTION,
        PDA_PRIMARY,
        PDA_BUY,
        PDA_DANGER
    }

    /** Resolved ARGB chrome colors for a single button render state. */
    public record ButtonVisual(
            int fill,
            int topBevel,
            int bottomBevel,
            int border,
            int accentBar,
            int textColor
    ) {
    }

    /** Resolves exact beveled industrial chrome colors for a button style and interactive state. */
    public static ButtonVisual resolveButtonVisual(
            ButtonStyle style,
            boolean active,
            boolean hovered,
            boolean selected
    ) {
        if (!active) {
            return new ButtonVisual(
                    0xFF1A222C,
                    0xFF212B36,
                    0xFF131920,
                    0xFF2E3845,
                    0,
                    0xFF637282
            );
        }
        return switch (style) {
            case HUB_TAB -> selected
                    ? new ButtonVisual(0xFF243852, 0xFF345275, 0xFF142133, 0xFFE8B944, 0xFFF7CE4D, 0xFFFFF4C4)
                    : (hovered
                            ? new ButtonVisual(0xFF233247, 0xFF334966, 0xFF141E2B, 0xFFC79B3B, 0x80F2C446, 0xFFF5FAFF)
                            : new ButtonVisual(0xFF192434, 0xFF26364D, 0xFF101824, 0xFF475B73, 0, 0xFFC4D7EB));
            case HUB_SUBTAB -> selected
                    ? new ButtonVisual(0xFF213A54, 0xFF31567A, 0xFF122336, 0xFFEAB944, 0xFFF5C846, 0xFFFFEA99)
                    : (hovered
                            ? new ButtonVisual(0xFF1D3044, 0xFF2C4866, 0xFF111D2B, 0xFF6991B8, 0, 0xFFEAF6FF)
                            : new ButtonVisual(0xFF14202E, 0xFF1F3045, 0xFF0D1621, 0xFF354C66, 0, 0xFF9EB9D4));
            case HUB_ACTION -> selected
                    ? new ButtonVisual(0xFF2A4563, 0xFF3F658F, 0xFF192B40, 0xFFE6B640, 0, 0xFFFFF0B8)
                    : (hovered
                            ? new ButtonVisual(0xFF2B425C, 0xFF406287, 0xFF1B2B3D, 0xFFD9AA3D, 0, 0xFFFFFFFF)
                            : new ButtonVisual(0xFF1E2D3F, 0xFF2E435C, 0xFF131D29, 0xFF4C6580, 0, 0xFFE6F1FC));
            case HUB_PRIMARY -> hovered
                    ? new ButtonVisual(0xFF2D6596, 0xFF4B8EC7, 0xFF1C4266, 0xFFF5C846, 0, 0xFFFFFFFF)
                    : new ButtonVisual(0xFF214B73, 0xFF3670A3, 0xFF14304D, 0xFF5D98CE, 0, 0xFFF4FAFF);
            case HUB_BUY -> hovered
                    ? new ButtonVisual(0xFF7D541E, 0xFFB07A2E, 0xFF4F3310, 0xFFFFDF78, 0, 0xFFFFFFFF)
                    : new ButtonVisual(0xFF5D3E16, 0xFF875B21, 0xFF3C270D, 0xFFD49A3A, 0, 0xFFFFF5DC);
            case HUB_DANGER -> hovered
                    ? new ButtonVisual(0xFF782B27, 0xFFA8423D, 0xFF4D1A17, 0xFFFF8078, 0, 0xFFFFFFFF)
                    : new ButtonVisual(0xFF541F1C, 0xFF7D312C, 0xFF361210, 0xFFB84E48, 0, 0xFFFFE6E4);
            case PDA_TAB -> selected
                    ? new ButtonVisual(0xFF123650, 0xFF1D547A, 0xFF0A2030, 0xFF42EBFF, 0xFF42EBFF, 0xFFE8FDFF)
                    : (hovered
                            ? new ButtonVisual(0xFF142D42, 0xFF204869, 0xFF0B1B29, 0xFF32B2D4, 0x8036E4FF, 0xFFE4FAFF)
                            : new ButtonVisual(0xFF0D1B29, 0xFF172D42, 0xFF08121C, 0xFF21425E, 0, 0xFF8EC5DE));
            case PDA_SUBTAB -> selected
                    ? new ButtonVisual(0xFF143852, 0xFF205880, 0xFF0B2233, 0xFF46EEFF, 0xFF46EEFF, 0xFFE8FDFF)
                    : (hovered
                            ? new ButtonVisual(0xFF122C42, 0xFF1D4466, 0xFF0A1B29, 0xFF2FA7C9, 0, 0xFFDFF8FF)
                            : new ButtonVisual(0xFF0C1926, 0xFF152B40, 0xFF08111A, 0xFF1E3C54, 0, 0xFF8AB8D1));
            case PDA_ACTION -> selected
                    ? new ButtonVisual(0xFF17405E, 0xFF26628C, 0xFF0E283B, 0xFF4AEBFF, 0, 0xFFEBFDFF)
                    : (hovered
                            ? new ButtonVisual(0xFF18364F, 0xFF265378, 0xFF0E2233, 0xFF40D8F5, 0, 0xFFFFFFFF)
                            : new ButtonVisual(0xFF102334, 0xFF1B3A54, 0xFF091622, 0xFF285170, 0, 0xFFD4F1FF));
            case PDA_PRIMARY -> hovered
                    ? new ButtonVisual(0xFF1B5B80, 0xFF2F88BA, 0xFF103952, 0xFF78F4FF, 0, 0xFFFFFFFF)
                    : new ButtonVisual(0xFF13415E, 0xFF216994, 0xFF0B293D, 0xFF35C2E3, 0, 0xFFEDFDFF);
            case PDA_BUY -> hovered
                    ? new ButtonVisual(0xFF784F19, 0xFFAD7428, 0xFF4A2F0D, 0xFFFFDA6B, 0, 0xFFFFFFFF)
                    : new ButtonVisual(0xFF573912, 0xFF82561E, 0xFF362209, 0xFFC98F32, 0, 0xFFFFF3D6);
            case PDA_DANGER -> hovered
                    ? new ButtonVisual(0xFF6B2228, 0xFF99353E, 0xFF421418, 0xFFFF7580, 0, 0xFFFFFFFF)
                    : new ButtonVisual(0xFF47181C, 0xFF6B262C, 0xFF2B0E11, 0xFFA6414A, 0, 0xFFFFE0E3);
        };
    }

    // --- UV Coordinate Lookups ---

    /** Returns the U coordinate on {@code cooperation_hub.png} (v=226, 12x12) for {@code season}, or -1 if inactive. */
    public static int hubSeasonU(String season) {
        if (season == null) {
            return -1;
        }
        return switch (season.toUpperCase(Locale.ROOT)) {
            case "SPRING" -> 62;
            case "SUMMER" -> 76;
            case "AUTUMN" -> 90;
            case "WINTER" -> 104;
            default -> -1;
        };
    }

    /** Returns the U coordinate on {@code cooperation_hub.png} (v=226, 10x10) for CPI inflation state. */
    public static int hubCpiArrowU(String inflationState) {
        if ("INFLATION".equalsIgnoreCase(inflationState) || "HYPERINFLATION".equalsIgnoreCase(inflationState)) {
            return 120;
        }
        if ("DEFLATION".equalsIgnoreCase(inflationState)) {
            return 132;
        }
        return 144;
    }

    /** Returns the U coordinate on {@code cooperation_hub.png} (v=244, 10x10) for {@code biomeId}. */
    public static int hubBiomeU(String biomeId) {
        return biomeIndex(biomeId) * 12;
    }

    /** Returns the U coordinate on {@code comm_terminal.png} (v=216, 12x12) for {@code season}, or -1 if inactive. */
    public static int pdaSeasonU(String season) {
        if (season == null) {
            return -1;
        }
        return switch (season.toUpperCase(Locale.ROOT)) {
            case "SPRING" -> 102;
            case "SUMMER" -> 116;
            case "AUTUMN" -> 130;
            case "WINTER" -> 144;
            default -> -1;
        };
    }

    /** Returns the U coordinate on {@code comm_terminal.png} (v=236, 10x10) for {@code biomeId}. */
    public static int pdaBiomeU(String biomeId) {
        return 64 + biomeIndex(biomeId) * 12;
    }

    /** Normalizes a biome style string to one of the 7 supported TyC2 biome keys. */
    public static String normalizeBiomeKey(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return "plains";
        }
        String norm = biomeId.trim().toLowerCase(Locale.ROOT);
        int colon = norm.indexOf(':');
        if (colon >= 0) {
            norm = norm.substring(colon + 1);
        }
        return switch (norm) {
            case "desert", "savanna", "taiga", "snowy", "jungle", "swamp" -> norm;
            default -> "plains";
        };
    }

    private static int biomeIndex(String biomeId) {
        return switch (normalizeBiomeKey(biomeId)) {
            case "desert" -> 1;
            case "savanna" -> 2;
            case "taiga" -> 3;
            case "snowy" -> 4;
            case "jungle" -> 5;
            case "swamp" -> 6;
            default -> 0;
        };
    }

    /** Returns a distinct vivid ARGB accent color for the village biome style. */
    public static int biomeAccentColor(String biomeId) {
        return switch (normalizeBiomeKey(biomeId)) {
            case "desert" -> 0xFFF2C94C;
            case "savanna" -> 0xFFE69138;
            case "taiga" -> 0xFF35B885;
            case "snowy" -> 0xFF5CE6FF;
            case "jungle" -> 0xFF58EB46;
            case "swamp" -> 0xFFBA6BFF;
            default -> 0xFF4AE378;
        };
    }

    /** Returns a muted dark ARGB pill backdrop matching the village biome style. */
    public static int biomePillBg(String biomeId) {
        return switch (normalizeBiomeKey(biomeId)) {
            case "desert" -> 0xFF28200D;
            case "savanna" -> 0xFF29180D;
            case "taiga" -> 0xFF0E241E;
            case "snowy" -> 0xFF0D2233;
            case "jungle" -> 0xFF0E2613;
            case "swamp" -> 0xFF1F162B;
            default -> 0xFF10251A;
        };
    }

    /** Returns the semantic ARGB color for a CPI inflation state string. */
    public static int inflationColor(String inflationState) {
        if ("INFLATION".equalsIgnoreCase(inflationState) || "HYPERINFLATION".equalsIgnoreCase(inflationState)) {
            return 0xFFFF6B5E;
        }
        if ("DEFLATION".equalsIgnoreCase(inflationState)) {
            return 0xFF4BE2FF;
        }
        return 0xFF56EC8B;
    }

    // --- Formatting Utilities ---

    /**
     * Formats remaining weekly supply refresh ticks using {@link SupplyCycleClock} into a compact
     * localized calendar countdown (e.g. {@code 5d12h} / {@code 5天12时}, {@code 7d} / {@code 7天},
     * {@code 12h} / {@code 12时}, or {@code Soon} / {@code 即将刷新}).
     */
    public static String formatRestockCountdown(long remainingTicks) {
        long clamped = Math.max(0L, remainingTicks);
        long days = SupplyCycleClock.remainingWholeDays(clamped);
        long hoursInDay = SupplyCycleClock.remainingWholeHoursInDay(clamped);
        long totalHours = SupplyCycleClock.remainingTotalHours(clamped);
        if (days > 0L && hoursInDay > 0L) {
            return Component.translatable("gui.tyc2.hub.refresh_days_hours", days, hoursInDay).getString();
        }
        if (days > 0L) {
            return Component.translatable("gui.tyc2.hub.refresh_days", days).getString();
        }
        if (totalHours > 0L) {
            return Component.translatable("gui.tyc2.hub.refresh_hours", totalHours).getString();
        }
        return Component.translatable("gui.tyc2.hub.refresh_soon").getString();
    }

    /** Formats IC / circulation numbers compactly for tight status cells while keeping values <= 9,999 exact. */
    public static String formatCompactNumber(long value) {
        long abs = Math.abs(value);
        if (abs < 10_000L) {
            return Long.toString(value);
        }
        if (abs < 1_000_000L) {
            double k = value / 1_000.0;
            if (Math.abs(k - Math.rint(k)) < 0.05) {
                return String.format(Locale.ROOT, "%.0fk", k);
            }
            return String.format(Locale.ROOT, "%.1fk", k);
        }
        return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
    }

    // --- Shared ScreenCanvas Typography & Chrome Helpers ---

    /** Draws left-aligned text fit-scaled smoothly into {@code maxWidth}. */
    public static void drawFitString(ScreenCanvas canvas, String text, int x, int y, int maxWidth, int color) {
        drawFitString(canvas, text, x, y, maxWidth, 0.58F, color);
    }

    /** Draws left-aligned text fit-scaled smoothly into {@code maxWidth} down to {@code minScale}. */
    public static void drawFitString(
            ScreenCanvas canvas,
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
        int w = canvas.textWidth(text);
        if (w <= maxWidth) {
            canvas.drawClippedString(text, x, y, maxWidth, color);
        } else {
            float sc = Math.max(minScale, maxWidth / (float) w);
            int yOffset = sc < 0.85F ? Math.max(0, Math.round((8.0F * (1.0F - sc)) * 0.5F)) : 0;
            canvas.drawScaledString(text, x, y + yOffset, maxWidth, sc, color);
        }
    }

    /** Draws right-aligned text ending at {@code rightX}, fit-scaled into {@code maxWidth}. */
    public static void drawRightFitString(
            ScreenCanvas canvas,
            String text,
            int rightX,
            int y,
            int maxWidth,
            float minScale,
            int color
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }
        int w = canvas.textWidth(text);
        if (w <= maxWidth) {
            canvas.drawClippedString(text, rightX - w, y, maxWidth, color);
        } else {
            float sc = Math.max(minScale, maxWidth / (float) w);
            int drawW = Math.min(maxWidth, Math.round(w * sc));
            int yOffset = sc < 0.85F ? Math.max(0, Math.round((8.0F * (1.0F - sc)) * 0.5F)) : 0;
            canvas.drawScaledString(text, rightX - drawW, y + yOffset, maxWidth, sc, color);
        }
    }

    /** Draws centered fit-scaled text inside box {@code (x, y, width, height)}. */
    public static void drawCenteredFitString(
            ScreenCanvas canvas,
            String text,
            int x,
            int y,
            int width,
            int height,
            float minScale,
            int color
    ) {
        if (text == null || text.isEmpty() || width <= 2 || height <= 2) {
            return;
        }
        int availW = Math.max(2, width - 4);
        int rawW = canvas.textWidth(text);
        float scale = rawW <= availW ? 1.0F : Math.max(minScale, availW / (float) rawW);
        int drawW = Math.min(availW, Math.round(rawW * scale));
        int drawH = Math.max(5, Math.round(8.0F * scale));
        int tx = x + Math.max(1, (width - drawW) / 2);
        int ty = y + Math.max(1, (height - drawH) / 2);
        if (scale >= 0.99F) {
            canvas.drawClippedString(text, tx, ty, availW, color);
        } else {
            canvas.drawScaledString(text, tx, ty, availW, scale, color);
        }
    }

    /**
     * Draws a card-embedded custom beveled CTA button matching {@code StyledButton} visuals
     * (used for Buy/Deliver/Cancel buttons inside scrollable order & aid cards).
     */
    public static void drawBevelButtonBox(
            ScreenCanvas canvas,
            int x,
            int y,
            int w,
            int h,
            ButtonStyle style,
            boolean active,
            boolean hovered,
            String label
    ) {
        ButtonVisual vis = resolveButtonVisual(style, active, hovered, false);
        canvas.fill(x + 1, y + 1, x + w - 1, y + h - 1, vis.fill());
        canvas.fill(x + 1, y + 1, x + w - 1, y + 2, vis.topBevel());
        canvas.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, vis.bottomBevel());
        canvas.outline(x, y, w, h, vis.border());
        drawCenteredFitString(canvas, label, x, y, w, h, 0.50F, vis.textColor());
    }

    /** Measures pixel width of a compact 8x8-icon + scaled-label tag pill. */
    public static int measureOrderTagPillWidth(ScreenCanvas canvas, String label, float textScale) {
        if (label == null || label.isEmpty()) {
            return 10;
        }
        int scaledText = Math.max(8, Math.round(canvas.textWidth(label) * textScale));
        return Math.min(44, 11 + scaledText + 3);
    }

    /**
     * Draws a crisp compact order tag pill with an 8x8 pixel-art icon and scaled text label.
     * Height is strictly 9px ({@code y .. y + 9}) to guarantee clear breathing room above Line 2.
     */
    public static void drawOrderTagPill(
            ScreenCanvas canvas,
            String guiTex,
            int x,
            int y,
            int w,
            int iconU,
            int iconV,
            String label,
            float textScale,
            int bgColor,
            int borderColor,
            int textColor
    ) {
        canvas.fill(x, y, x + w, y + 9, bgColor);
        canvas.blit(guiTex, x + 1, y + 1, iconU, iconV, 8, 8);
        canvas.outline(x, y, w, 9, borderColor);
        if (label != null && !label.isEmpty() && w > 12) {
            int maxTextW = Math.max(4, w - 12);
            int rawW = canvas.textWidth(label);
            float sc = Math.min(textScale, rawW > 0 ? maxTextW / (float) rawW : textScale);
            sc = Math.max(0.48F, sc);
            int ty = y + Math.max(1, (9 - Math.round(8.0F * sc)) / 2);
            canvas.drawScaledString(label, x + 10, ty, maxTextW, sc, textColor);
        }
    }
}
