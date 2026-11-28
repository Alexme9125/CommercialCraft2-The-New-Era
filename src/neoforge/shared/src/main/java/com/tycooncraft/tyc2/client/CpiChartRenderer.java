package com.tycooncraft.tyc2.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Renders the live 48-point CPI &amp; Inflation Trend Line Chart onto any {@link ScreenCanvas}
 * viewport inside {@link CooperationHubScreen} and {@link CommTerminalScreen}, complete with
 * subtle instrument gridlines, dashed 1.00 reference baseline, translucent area shading,
 * color-coded trend segments, and a highlighted latest-value marker.
 */
public final class CpiChartRenderer {

    private CpiChartRenderer() {
    }

    /**
     * Draws a framed CPI trend chart inside panel-local box {@code (x, y, w, h)} using the Hub theme.
     */
    public static void drawChart(ScreenCanvas canvas, int x, int y, int w, int h, List<Double> points) {
        drawChart(canvas, x, y, w, h, points, false);
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
     * @param pdaTheme {@code true} for tactical cyan-slate PDA styling, {@code false} for deep-ledger Hub styling
     */
    public static void drawChart(
            ScreenCanvas canvas,
            int x,
            int y,
            int w,
            int h,
            List<Double> points,
            boolean pdaTheme
    ) {
        int bgColor = pdaTheme ? 0xFF07111B : 0xFF0B131D;
        int topTint = pdaTheme ? 0xFF0E2133 : 0xFF132030;
        int borderColor = pdaTheme ? 0xFF214E6E : 0xFF39526D;
        int gridColor = pdaTheme ? 0xFF0F2334 : 0xFF152434;
        int baseLineColor = pdaTheme ? 0xFF2E7699 : 0xFF476985;
        int axisLabelColor = pdaTheme ? 0xFF6BA6C4 : 0xFF7591AB;

        canvas.fill(x, y, x + w, y + h, bgColor);
        if (h > 4) {
            canvas.fill(x + 1, y + 1, x + w - 1, y + 2, topTint);
        }
        canvas.outline(x, y, w, h, borderColor);

        int plotX = x + 3;
        int plotY = y + 3;
        int plotW = Math.max(8, w - 6);
        int plotH = Math.max(8, h - 6);
        int plotBottom = plotY + plotH;

        double minVal = 0.70;
        double maxVal = 1.40;
        if (points != null) {
            for (Double d : points) {
                if (d != null && Double.isFinite(d)) {
                    minVal = Math.min(minVal, Math.max(0.20, d - 0.10));
                    maxVal = Math.max(maxVal, Math.min(4.20, d + 0.10));
                }
            }
        }
        double range = Math.max(0.20, maxVal - minVal);

        // Subtle horizontal instrument gridlines at 25%, 50%, 75%
        for (int g = 1; g <= 3; g++) {
            int gy = plotY + (plotH * g) / 4;
            canvas.fill(plotX, gy, plotX + plotW, gy + 1, gridColor);
        }

        // Dashed horizontal 1.00 baseline reference line
        int baseY = plotY + plotH - (int) Math.round(((1.0 - minVal) / range) * (plotH - 1));
        if (baseY >= plotY && baseY < plotBottom) {
            for (int px = plotX; px < plotX + plotW; px += 4) {
                canvas.fill(px, baseY, Math.min(px + 2, plotX + plotW), baseY + 1, baseLineColor);
            }
            if (baseY > plotY + 7 && baseY < plotBottom - 8) {
                canvas.drawScaledString("1.00", plotX + plotW - 15, baseY - 6, 16, 0.60F, baseLineColor);
            }
        }

        if (points == null || points.isEmpty()) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.mnera.hub.status.cpi", "1.00").getString(),
                    plotX, plotY, plotW, plotH, 0.65F, axisLabelColor
            );
            return;
        }

        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] cols = new int[n];
        int validCount = 0;

        for (int i = 0; i < n; i++) {
            Double rawVal = points.get(i);
            if (rawVal == null || !Double.isFinite(rawVal)) {
                continue;
            }
            double val = rawVal;
            int px = n == 1
                    ? (plotX + plotW / 2)
                    : plotX + (int) Math.round((i / (double) (n - 1)) * (plotW - 1));
            int py = plotY + plotH - 1 - (int) Math.round(((val - minVal) / range) * (plotH - 1));
            py = Math.max(plotY, Math.min(plotBottom - 1, py));

            int lineCol = val > 1.08 ? 0xFFFF6354 : (val < 0.92 ? 0xFF3CE0FF : 0xFF44EC84);
            xs[validCount] = px;
            ys[validCount] = py;
            cols[validCount] = lineCol;
            validCount++;
        }

        if (validCount == 0) {
            return;
        }

        // Translucent vertical area fill under trend curve for rich readability
        if (validCount >= 2) {
            for (int i = 1; i < validCount; i++) {
                int x0 = xs[i - 1];
                int y0 = ys[i - 1];
                int x1 = xs[i];
                int y1 = ys[i];
                int areaColor = (cols[i] & 0x00FFFFFF) | 0x26000000;
                int span = Math.max(1, x1 - x0);
                for (int cx = x0; cx <= x1; cx++) {
                    double t = (cx - x0) / (double) span;
                    int cy = (int) Math.round(y0 + (y1 - y0) * t);
                    if (cy < plotBottom) {
                        canvas.fill(cx, cy, cx + 1, plotBottom, areaColor);
                    }
                }
            }
        }

        // Crisp trend line + data points
        for (int i = 0; i < validCount; i++) {
            int px = xs[i];
            int py = ys[i];
            int color = cols[i];
            if (i > 0) {
                canvas.drawLine(xs[i - 1], ys[i - 1], px, py, color);
            }
            canvas.fill(px, py, Math.min(plotX + plotW, px + 2), Math.min(plotBottom, py + 2), color);
        }

        // Highlight latest (rightmost) data sample with bright core dot
        int lastX = xs[validCount - 1];
        int lastY = ys[validCount - 1];
        int haloColor = (cols[validCount - 1] & 0x00FFFFFF) | 0x55000000;
        canvas.fill(
                Math.max(plotX, lastX - 1),
                Math.max(plotY, lastY - 1),
                Math.min(plotX + plotW, lastX + 3),
                Math.min(plotBottom, lastY + 3),
                haloColor
        );
        canvas.fill(lastX, lastY, Math.min(plotX + plotW, lastX + 2), Math.min(plotBottom, lastY + 2), 0xFFFFFFFF);

        // Compact scaled axis max / min indicators (never obscure data points)
        canvas.drawScaledString(String.format(Locale.ROOT, "%.2f", maxVal), plotX + 1, plotY, 24, 0.65F, axisLabelColor);
        canvas.drawScaledString(String.format(Locale.ROOT, "%.2f", minVal), plotX + 1, plotBottom - 6, 24, 0.65F, axisLabelColor);
    }
}
