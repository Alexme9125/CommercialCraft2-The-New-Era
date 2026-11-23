package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.CommTerminalMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Pixel-exact remote management GUI for the handheld Commerce Communication Terminal
 * ({@code gui/comm_terminal.png}, 248x210). Manages up to 16 bound villages remotely across
 * unloaded chunks with Bank-settled orders (50 EU), stock inspection, 48-point CPI chart,
 * and remote upgrades/expansion (100 EU).
 */
public final class CommTerminalScreen extends TyC2ScreenBase<CommTerminalMenu> {

    private static final String GUI_TEX = "comm_terminal";
    private static final int RAIL_PER_PAGE = 8;
    private static final int ORDERS_PER_PAGE = 5;

    private int railPage = 0;
    private int subTab = 0;       // 0=Orders, 1=Stock, 2=CPI, 3=Growth
    private int orderPage = 0;
    private boolean stockOutbound = false;

    public CommTerminalScreen(CommTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 248, 210);
    }

    @Override
    protected boolean drawDefaultLabels() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        rebuildControls();
        sendAction("REQUEST_SYNC", Map.of());
    }

    private void rebuildControls() {
        clearWidgets();
        clearEditBoxes();

        // Left rail scroll buttons (bottom of left rail 8, 22, 64x178)
        addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.prev"), b -> {
            railPage = Math.max(0, railPage - 1);
        }).bounds(leftPos + 10, topPos + 186, 26, 12).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.next"), b -> {
            List<Object> list = JsonLite.asArray(menu.snapshot().get("boundVillages"));
            int max = list == null ? 0 : Math.max(0, (list.size() - 1) / RAIL_PER_PAGE);
            railPage = Math.min(max, railPage + 1);
        }).bounds(leftPos + 44, topPos + 186, 26, 12).build());

        // Right panel sub-tabs (78, 42, 160x12)
        String[] labels = {"Orders", "Stock", "CPI", "Growth"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> {
                subTab = idx;
                rebuildControls();
            }).bounds(leftPos + 78 + i * 40, topPos + 42, 39, 12).build());
        }

        // Unbind button at top-right of tactical LCD
        addRenderableWidget(Button.builder(
                Component.translatable("gui.tyc2.terminal.unbind"),
                b -> {
                    String sel = JsonLite.getString(menu.snapshot(), "selectedId", "");
                    if (!sel.isEmpty()) {
                        sendAction("UNBIND_VILLAGE", Map.of("villageId", sel));
                    }
                }
        ).bounds(leftPos + 196, topPos + 24, 42, 12).build());

        if (subTab == 0) {
            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.prev"), b -> {
                orderPage = Math.max(0, orderPage - 1);
            }).bounds(leftPos + 206, topPos + 186, 15, 12).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.next"), b -> {
                orderPage++;
            }).bounds(leftPos + 223, topPos + 186, 15, 12).build());
        } else if (subTab == 1) {
            addRenderableWidget(Button.builder(
                    Component.literal(stockOutbound ? "Outbound" : "Inbound"),
                    b -> {
                        stockOutbound = !stockOutbound;
                        b.setMessage(Component.literal(stockOutbound ? "Outbound" : "Inbound"));
                    }
            ).bounds(leftPos + 80, topPos + 57, 70, 12).build());
        } else if (subTab == 3) {
            addRenderableWidget(Button.builder(
                    Component.literal("Upgrade (100 EU)"),
                    b -> sendAction("UPGRADE_VILLAGE", Map.of())
            ).bounds(leftPos + 80, topPos + 86, 156, 14).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Deliver (100 EU)"),
                    b -> sendAction("DELIVER_EXPANSION", Map.of())
            ).bounds(leftPos + 80, topPos + 132, 86, 13).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Auto"),
                    b -> sendAction("TOGGLE_EXPANSION_AUTO", Map.of())
            ).bounds(leftPos + 168, topPos + 132, 32, 13).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Pause"),
                    b -> sendAction("TOGGLE_PAUSE_EXPANSION", Map.of())
            ).bounds(leftPos + 202, topPos + 132, 34, 13).build());
        }
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        Map<String, Object> snap = menu.snapshot();
        // Shift title right of the left green signal icon (8, 6, 12x12) so there is zero overlap
        canvas.drawClippedString(title.getString(), 24, 8, 148, 0xFFCAE8FF);

        // Battery gauge: frame (178, 6, 58x12) -> fill (179, 7, 56x10) from UV (0,216) green or (0,228) red
        int eu = JsonLite.getInt(snap, "energyEu", 0);
        int maxEu = Math.max(1, JsonLite.getInt(snap, "maxEu", TriModePowerMath.TERMINAL_MAX_CAPACITY_EU));
        if (eu > 0) {
            int fillW = Math.max(1, Math.min(56, (int) Math.round(56.0 * eu / (double) maxEu)));
            int v = (eu * 5 < maxEu) ? 228 : 216;
            canvas.blit(GUI_TEX, 179, 7, 0, v, fillW, 10);
        }

        paintVillageRail(canvas, snap, mouseX - leftPos, mouseY - topPos);

        // Highlight active sub-tab
        canvas.outline(78 + subTab * 40, 42, 39, 12, 0xFF4BE2FF);

        Map<String, Object> village = JsonLite.asObject(snap.get("selectedVillage"));
        if (village == null) {
            canvas.drawClippedString(
                    Component.translatable("gui.tyc2.terminal.no_villages").getString(),
                    82, 86, 152, 0xFF8AA0B8
            );
            return;
        }

        // Right LCD header
        String vName = JsonLite.getString(village, "villageName", "Village");
        int level = JsonLite.getInt(village, "level", 1);
        long vFunds = JsonLite.getLong(village, "villageBalance", 0L);
        long bankBal = JsonLite.getLong(snap, "activeBalance", 0L);

        canvas.drawClippedString(vName + " Lv." + level, 80, 24, 114, 0xFFFFE57F);
        canvas.drawClippedString("VF:" + vFunds + "  Bk:" + bankBal, 80, 33, 156, 0xFF8CE8FF);

        int pmx = mouseX - leftPos;
        int pmy = mouseY - topPos;
        switch (subTab) {
            case 0 -> paintRemoteOrders(canvas, village, pmx, pmy);
            case 1 -> paintRemoteStock(canvas, village);
            case 2 -> paintRemoteEconomy(canvas, village);
            case 3 -> paintRemoteGrowth(canvas, village);
            default -> {
            }
        }
    }

    private void paintVillageRail(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        List<Object> bound = JsonLite.asArray(snap.get("boundVillages"));
        int count = bound == null ? 0 : bound.size();
        canvas.drawClippedString(count + "/16", 12, 24, 56, 0xFF7FA8C8);

        if (bound == null || bound.isEmpty()) {
            return;
        }
        String selectedId = JsonLite.getString(snap, "selectedId", "");
        int start = railPage * RAIL_PER_PAGE;
        for (int i = 0; i < RAIL_PER_PAGE && start + i < bound.size(); i++) {
            Map<String, Object> vm = JsonLite.asObject(bound.get(start + i));
            if (vm == null) {
                continue;
            }
            int ry = 35 + i * 18;
            String vid = JsonLite.getString(vm, "id", "");
            String vname = JsonLite.getString(vm, "name", "?");
            int lv = JsonLite.getInt(vm, "level", 1);
            boolean loaded = JsonLite.getBoolean(vm, "loaded", false);
            boolean sel = vid.equals(selectedId);
            boolean hover = pmx >= 10 && pmx < 70 && pmy >= ry && pmy < ry + 16;

            canvas.fill(10, ry, 70, ry + 16, sel ? 0xFF214866 : (hover ? 0xFF1A344B : 0xFF11202E));
            canvas.outline(10, ry, 60, 16, sel ? 0xFF56E4FF : 0xFF2B4358);
            // Status dot: green=loaded, cyan=virtual (still fully interactive!)
            canvas.fill(12, ry + 6, 15, ry + 9, loaded ? 0xFF42F587 : 0xFF39C4FF);
            canvas.drawClippedString(vname, 17, ry + 2, 42, 0xFFEAF6FF);
            canvas.drawClippedString("L" + lv, 58, ry + 7, 11, 0xFFFFD866);
        }
    }

    private void paintRemoteOrders(ScreenCanvas canvas, Map<String, Object> village, int pmx, int pmy) {
        List<Object> raw = JsonLite.asArray(village.get("orders"));
        if (raw == null || raw.isEmpty()) {
            canvas.drawClippedString(
                    Component.translatable("gui.tyc2.hub.no_orders").getString(),
                    82, 86, 152, 0xFF8AA0B8
            );
            return;
        }
        int totalPages = Math.max(1, (raw.size() + ORDERS_PER_PAGE - 1) / ORDERS_PER_PAGE);
        if (orderPage >= totalPages) {
            orderPage = totalPages - 1;
        }
        canvas.drawClippedString(
                Component.translatable("gui.tyc2.terminal.cost_trade", TriModePowerMath.TERMINAL_COST_TRADE_EU).getString(),
                80, 188, 122, 0xFF7FA8C8
        );

        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < raw.size(); i++) {
            Map<String, Object> o = JsonLite.asObject(raw.get(start + i));
            if (o == null) {
                continue;
            }
            int rowY = 58 + i * 25;
            boolean sells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
            String itemId = JsonLite.getString(o, "itemId", "");
            int total = JsonLite.getInt(o, "totalQty", 1);
            int doneQty = JsonLite.getInt(o, "fulfilledQty", 0);
            long price = JsonLite.getLong(o, "unitPrice", 1L);
            boolean auto = JsonLite.getBoolean(o, "autoFulfill", false);
            boolean done = doneQty >= total;

            canvas.fill(78, rowY, 238, rowY + 23, sells ? 0xFF142433 : 0xFF261E14);
            canvas.outline(78, rowY, 160, 23, sells ? 0xFF396993 : 0xFF936D39);

            ItemStack st = TyC2Native.stack(itemId, 1);
            canvas.drawItem(st, 80, rowY + 3);
            String name = st.isEmpty() ? itemId : st.getHoverName().getString();
            canvas.drawClippedString((sells ? "[S] " : "[B] ") + name, 98, rowY + 3, 86, 0xFFEAF6FF);
            canvas.drawClippedString(doneQty + "/" + total + " " + price + "IC", 98, rowY + 13, 86, 0xFFFFD866);

            // Auto toggle box (186, rowY+5, 14x13)
            canvas.fill(186, rowY + 5, 200, rowY + 18, auto ? 0xFF247044 : 0xFF28323D);
            canvas.drawClippedString("A", 190, rowY + 8, 8, auto ? 0xFF7AFFA8 : 0xFF7A8C9E);

            // Remote Buy/Deliver button (202, rowY+4, 34x15)
            boolean hover = pmx >= 202 && pmx < 236 && pmy >= rowY + 4 && pmy < rowY + 19;
            canvas.fill(202, rowY + 4, 236, rowY + 19, done ? 0xFF252D35 : (hover ? 0xFF3B73A8 : 0xFF264E73));
            canvas.outline(202, rowY + 4, 34, 15, 0xFF8CB4D8);
            canvas.drawClippedString(done ? "OK" : (sells ? "Buy" : "Del"), 207, rowY + 8, 26, 0xFFFFFFFF);
        }
    }

    private void paintRemoteStock(ScreenCanvas canvas, Map<String, Object> village) {
        int inUsed = JsonLite.getInt(village, "inboundUsed", 0);
        int outUsed = JsonLite.getInt(village, "outboundUsed", 0);
        canvas.drawClippedString(
                Component.translatable("gui.tyc2.terminal.remote_warehouse", inUsed, outUsed).getString(),
                154, 59, 82, 0xFF9BE8FF
        );

        Map<String, Object> summary = JsonLite.asObject(
                village.get(stockOutbound ? "outboundSummary" : "inboundSummary")
        );
        if (summary == null || summary.isEmpty()) {
            canvas.drawClippedString("Empty", 84, 86, 140, 0xFF8AA0B8);
            return;
        }
        int idx = 0;
        for (Map.Entry<String, Object> e : summary.entrySet()) {
            if (idx >= 12) {
                break;
            }
            int col = idx % 2;
            int row = idx / 2;
            int x = 80 + col * 80;
            int y = 74 + row * 20;
            ItemStack st = TyC2Native.stack(e.getKey(), 1);
            canvas.drawItem(st, x, y);
            String lbl = (st.isEmpty() ? e.getKey() : st.getHoverName().getString());
            int cnt = e.getValue() instanceof Number n ? n.intValue() : 0;
            canvas.drawClippedString(lbl, x + 18, y + 1, 58, 0xFFEAF6FF);
            canvas.drawClippedString("x" + cnt, x + 18, y + 10, 58, 0xFFFFD866);
            idx++;
        }
    }

    private void paintRemoteEconomy(ScreenCanvas canvas, Map<String, Object> village) {
        double cpi = JsonLite.getDouble(village, "cpi", 1.0);
        String infl = JsonLite.getString(village, "inflationState", "STABLE");
        long money = JsonLite.getLong(village, "moneySupply", 0L);
        long goods = JsonLite.getLong(village, "goodsCirculation", 0L);
        String season = JsonLite.getString(village, "season", "NONE");
        int tier = JsonLite.getInt(village, "industrialTier", 0);

        int cpiColor = "INFLATION".equals(infl) ? 0xFFFF6B5E : ("DEFLATION".equals(infl) ? 0xFF4BE2FF : 0xFF56EC8B);
        canvas.drawClippedString(String.format("CPI: %.2f (%s)", cpi, infl), 80, 58, 156, cpiColor);
        canvas.drawClippedString("M: " + money + " IC | Q: " + goods, 80, 70, 156, 0xFFE2EFFA);
        canvas.drawClippedString(
                Component.translatable("season.tyc2." + season.toLowerCase(Locale.ROOT)).getString()
                        + " | " + Component.translatable("tier.tyc2." + tier).getString(),
                80, 82, 156, 0xFF9BE8FF
        );
        canvas.drawClippedString(
                Component.translatable("gui.tyc2.economy.cpi_chart").getString(),
                80, 96, 156, 0xFFAEC8E0
        );

        List<Double> pts = new ArrayList<>();
        List<Object> arr = JsonLite.asArray(village.get("cpiHistory"));
        if (arr != null) {
            for (Object o : arr) {
                if (o instanceof Number n) {
                    pts.add(n.doubleValue());
                }
            }
        }
        CpiChartRenderer.drawChart(canvas, 80, 108, 156, 86, pts);
    }

    private void paintRemoteGrowth(ScreenCanvas canvas, Map<String, Object> village) {
        int level = JsonLite.getInt(village, "level", 1);
        int pop = JsonLite.getInt(village, "population", 0);
        long vol = JsonLite.getLong(village, "tradeVolume", 0L);
        long bal = JsonLite.getLong(village, "villageBalance", 0L);
        int reqPop = JsonLite.getInt(village, "nextMinPop", 0);
        long reqVol = JsonLite.getLong(village, "nextMinVol", 0L);
        long reqBal = JsonLite.getLong(village, "nextMinBal", 0L);

        canvas.drawClippedString("Lv." + level + " " + Component.translatable("level.tyc2." + level).getString(), 80, 58, 156, 0xFFFFE57F);
        canvas.drawClippedString(
                level >= 10
                        ? Component.translatable("gui.tyc2.hub.max_level").getString()
                        : String.format("Pop %d/%d | Vol %d/%d | IC %d/%d", pop, reqPop, vol, reqVol, bal, reqBal),
                80, 70, 156, 0xFFCAE8FF
        );

        Map<String, Object> build = JsonLite.asObject(village.get("activeBuild"));
        Map<String, Object> desire = JsonLite.asObject(village.get("expansionDesire"));
        if (build != null) {
            String pType = JsonLite.getString(build, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
            int pct = JsonLite.getInt(build, "progressPct", 0);
            canvas.drawClippedString(
                    Component.translatable("gui.tyc2.expansion.building",
                            Component.translatable("project.tyc2." + pType), pct).getString(),
                    80, 108, 156, 0xFF64E8FF
            );
        } else if (desire != null) {
            String pType = JsonLite.getString(desire, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
            long budget = JsonLite.getLong(desire, "budget", 0L);
            canvas.drawClippedString(
                    Component.translatable("project.tyc2." + pType).getString() + " (" + budget + " IC)",
                    80, 108, 156, 0xFFFFE066
            );
            boolean auto = JsonLite.getBoolean(desire, "autoFulfill", false);
            canvas.drawClippedString(auto ? "Auto-Fulfill: ON" : "Auto-Fulfill: OFF", 80, 119, 156, 0xFF9AF5B0);
        } else {
            canvas.drawClippedString(
                    Component.translatable("gui.tyc2.expansion.none").getString(),
                    80, 110, 156, 0xFF8AA0B8
            );
        }

        // Nearby LC2 Logistics coordinates
        List<Object> lc2 = JsonLite.asArray(village.get("lc2Stations"));
        canvas.drawClippedString("LC2 Logistics:", 80, 152, 156, 0xFFAEC8E0);
        if (lc2 == null || lc2.isEmpty()) {
            canvas.drawClippedString("None detected (or chunk unloaded)", 80, 164, 156, 0xFF7B8E9F);
        } else {
            int y = 164;
            for (int i = 0; i < Math.min(3, lc2.size()); i++) {
                canvas.drawClippedString(lc2.get(i).toString(), 80, y, 156, 0xFF64E8FF);
                y += 10;
            }
        }
    }

    @Override
    protected boolean handlePanelClick(double pmx, double pmy, int button, boolean shift) {
        if (button != 0) {
            return false;
        }
        Map<String, Object> snap = menu.snapshot();

        // Click left village rail to select village
        List<Object> bound = JsonLite.asArray(snap.get("boundVillages"));
        if (bound != null && pmx >= 10 && pmx < 70 && pmy >= 35 && pmy < 180) {
            int row = (int) ((pmy - 35) / 18);
            int idx = railPage * RAIL_PER_PAGE + row;
            if (row >= 0 && row < RAIL_PER_PAGE && idx < bound.size()) {
                Map<String, Object> vm = JsonLite.asObject(bound.get(idx));
                if (vm != null) {
                    String vid = JsonLite.getString(vm, "id", "");
                    if (!vid.isEmpty()) {
                        orderPage = 0;
                        sendAction("SELECT_VILLAGE", Map.of("villageId", vid));
                        return true;
                    }
                }
            }
        }

        // Click remote order rows
        if (subTab == 0) {
            Map<String, Object> village = JsonLite.asObject(snap.get("selectedVillage"));
            if (village != null) {
                List<Object> raw = JsonLite.asArray(village.get("orders"));
                if (raw != null) {
                    int start = orderPage * ORDERS_PER_PAGE;
                    for (int i = 0; i < ORDERS_PER_PAGE && start + i < raw.size(); i++) {
                        int rowY = 58 + i * 25;
                        Map<String, Object> o = JsonLite.asObject(raw.get(start + i));
                        if (o == null) {
                            continue;
                        }
                        String orderId = JsonLite.getString(o, "id", "");
                        if (pmx >= 186 && pmx < 200 && pmy >= rowY + 5 && pmy < rowY + 18) {
                            sendAction("TOGGLE_ORDER_AUTO", Map.of("orderId", orderId));
                            return true;
                        }
                        if (pmx >= 202 && pmx < 236 && pmy >= rowY + 4 && pmy < rowY + 19) {
                            boolean sells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
                            sendAction(sells ? "BUY_ORDER" : "SELL_ORDER", Map.of(
                                    "orderId", orderId,
                                    "qty", sells ? (shift ? 16 : 1) : (shift ? 64 : 16)
                            ));
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void paintOverlayTooltips(ScreenCanvas canvas, int mouseX, int mouseY) {
        int pmx = mouseX - leftPos;
        int pmy = mouseY - topPos;
        if (pmx >= 178 && pmx < 236 && pmy >= 6 && pmy < 18) {
            Map<String, Object> snap = menu.snapshot();
            int eu = JsonLite.getInt(snap, "energyEu", 0);
            int maxEu = JsonLite.getInt(snap, "maxEu", TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
            canvas.drawTooltip(
                    List.of(Component.translatable("gui.tyc2.terminal.battery", eu, maxEu)),
                    mouseX,
                    mouseY
            );
        }
    }

    private void sendAction(String action, Map<String, Object> args) {
        String json = args.isEmpty() ? "{}" : JsonLite.toJson(new LinkedHashMap<>(args));
        TyC2Native.sendToServer(new TyC2ActionPayload("TERMINAL", BlockPos.ZERO, action, json));
    }
}
