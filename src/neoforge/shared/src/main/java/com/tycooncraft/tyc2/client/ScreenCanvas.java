package com.tycooncraft.tyc2.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Version-neutral 2D drawing surface bridged to 1.21.1 {@code GuiGraphics} and 26.x
 * {@code GuiGraphicsExtractor} for all five TyC2 GUI screens (256x256 texture atlases).
 */
public interface ScreenCanvas {

    /** Draws a textured rectangle from {@code assets/tyc2/textures/gui/<guiName>.png} (256x256). */
    void blit(String guiName, int x, int y, int u, int v, int width, int height);

    /** Fills a panel-local rectangle {@code [x1, y1) .. [x2, y2)} with an ARGB color. */
    void fill(int x1, int y1, int x2, int y2, int argbColor);

    /** Draws a 1px panel-local rectangle border. */
    void outline(int x, int y, int width, int height, int argbColor);

    /** Draws a Bresenham pixel line between panel-local points {@code (x0, y0)} and {@code (x1, y1)}. */
    default void drawLine(int x0, int y0, int x1, int y1, int argbColor) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int cx = x0;
        int cy = y0;
        while (true) {
            fill(cx, cy, cx + 1, cy + 1, argbColor);
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

    /** Renders an ItemStack icon at panel-local {@code (x, y)}. */
    void drawItem(ItemStack stack, int x, int y);

    /** Renders an ItemStack count/durability decoration overlay at panel-local {@code (x, y)}. */
    void drawItemDecorations(ItemStack stack, int x, int y, String countOverride);

    /** Draws a Component string at panel-local {@code (x, y)} with no drop shadow. */
    void drawString(Component text, int x, int y, int argbColor);

    /** Draws a plain string clipped cleanly to {@code maxWidth} pixels. */
    void drawClippedString(String text, int x, int y, int maxWidth, int argbColor);

    /** Draws a plain string scaled by {@code scale} and clipped cleanly to {@code maxWidth} panel pixels. */
    void drawScaledString(String text, int x, int y, int maxWidth, float scale, int argbColor);

    /** Returns pixel width of a plain string in the active font. */
    int textWidth(String text);

    /** Renders a hover tooltip at screen coordinates {@code (mouseX, mouseY)}. */
    void drawTooltip(List<Component> lines, int mouseX, int mouseY);
}
