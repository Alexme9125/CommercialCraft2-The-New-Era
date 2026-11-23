package com.tycooncraft.tyc2.client;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Renders the live 48-point CPI & Inflation Trend Line Chart onto any {@link ScreenCanvas}
 * viewport inside {@link CooperationHubScreen} and {@link CommTerminalScreen}.
 */
public final class CpiChartRenderer {

    private CpiChartRenderer() {
    }

    /**
     * Draws a framed CPI trend chart inside panel-local box {@code (x, y, w, h)}.
     *
     * @param canvas drawing surface
     * @param x panel-local left X
     * @param y panel-local top Y
     * @param w chart box width
     * @param h chart box height
     * @param points chronological CPI snapshots (up to 48 points)
     */
    public static void drawChart(ScreenCanvas canvas, int x, int y, int w, int h, List<Double> points) {
        // Dark instrument chart background + inner border
        canvas.fill(x, y, x + w, y + h, 0xFF0C141E);
        canvas.outline(x, y, w, h, 0xFF33495E);

        int plotX = x + 3;
        int plotY = y + 3;
        int plotW = Math.max(8, w - 6);
        int plotH = Math.max(8, h - 6);

        double minVal = 0.65;
        double maxVal = 1.45;
        if (points != null) {
            for (Double d : points) {
                if (d != null) {
                    minVal = Math.min(minVal, Math.max(0.20, d - 0.10));
                    maxVal = Math.max(maxVal, Math.min(4.20, d + 0.10));
                }
            }
        }
        double range = Math.max(0.2, maxVal - minVal);

        // Horizontal 1.00 baseline reference line (dashed subtle cyan-gray)
        int baseY = plotY + plotH - (int) Math.round(((1.0 - minVal) / range) * plotH);
        if (baseY >= plotY && baseY <= plotY + plotH) {
            for (int px = plotX; px < plotX + plotW; px += 4) {
                canvas.fill(px, baseY, Math.min(px + 2, plotX + plotW), baseY + 1, 0xFF3D5970);
            }
        }

        if (points == null || points.isEmpty()) {
            canvas.drawClippedString("CPI 1.00", plotX + 2, plotY + 2, plotW - 4, 0xFF7090AA);
            return;
        }

        int n = points.size();
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < n; i++) {
            double val = points.get(i);
            int px = n == 1 ? (plotX + plotW / 2) : plotX + (int) Math.round((i / (double) (n - 1)) * (plotW - 1));
            int py = plotY + plotH - (int) Math.round(((val - minVal) / range) * (plotH - 1));
            py = Math.max(plotY, Math.min(plotY + plotH - 1, py));

            int color = val > 1.08 ? 0xFFFF5C4D : (val < 0.92 ? 0xFF3BD9FF : 0xFF42E87A);
            if (prevX >= 0) {
                canvas.drawLine(prevX, prevY, px, py, color);
            }
            canvas.fill(px, py, px + 2, py + 2, color);
            prevX = px;
            prevY = py;
        }

        // Compact axis max/min indicators
        canvas.drawString(Component.literal(String.format("%.2f", maxVal)), plotX + 1, plotY, 0xFF6B8499);
        canvas.drawString(Component.literal(String.format("%.2f", minVal)), plotX + 1, plotY + plotH - 7, 0xFF6B8499);
    }
}
