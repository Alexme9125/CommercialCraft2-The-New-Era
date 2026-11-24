package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
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
 * ({@code gui/comm_terminal.png}, 248x210). 100% localized across zh_cn and en_us.
 */
public final class CommTerminalScreen extends TyC2ScreenBase<CommTerminalMenu> {

    private static final String GUI_TEX = "comm_terminal";
    private static final int RAIL_PER_PAGE = 8;
    private static final int ORDERS_PER_PAGE = 5;

    private int railPage = 0;
    private int subTab = 0;       // 0=Orders, 1=Stock, 2=CPI, 3=Growth
    private int growthMode = 0;   // 0=Expansion, 1=Aid Construction
    private int orderPage = 0;
    private boolean stockOutbound = false;
    private int aidProfIdx = 0;
    private boolean aidLarge = false;

    private Button remoteAutoBtn;
    private Button remotePauseBtn;
    private Button remoteAidSizeBtn;
    private Button remoteAidStartBtn;

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
        remoteAutoBtn = null;
        remotePauseBtn = null;
        remoteAidSizeBtn = null;
        remoteAidStartBtn = null;

        // Left rail scroll buttons (bottom of left rail 8, 22, 64x178)
        addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.prev"), b -> {
            railPage = Math.max(0, railPage - 1);
        }).bounds(leftPos + 10, topPos + 186, 26, 12).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.next"), b -> {
            List<Object> list = JsonLite.asArray(menu.snapshot().get("boundVillages"));
            int max = list == null ? 0 : Math.max(0, (list.size() - 1) / RAIL_PER_PAGE);
            railPage = Math.min(max, railPage + 1);
        }).bounds(leftPos + 44, topPos + 186, 26, 12).build());

        // Right panel sub-tabs (78, 42, 160x12) — 100% localized
        String[] tabKeys = {
                "gui.tyc2.terminal.tab.orders",
                "gui.tyc2.terminal.tab.stock",
                "gui.tyc2.terminal.tab.cpi",
                "gui.tyc2.terminal.tab.growth"
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.translatable(tabKeys[i]), b -> {
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
                    Component.translatable(stockOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"),
                    b -> {
                        stockOutbound = !stockOutbound;
                        b.setMessage(Component.translatable(
                                stockOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
                        ));
                    }
            ).bounds(leftPos + 80, topPos + 57, 64, 12).build());
        } else if (subTab == 3) {
            // Sub-toggle inside Growth: Expansion (0) vs Aid Construction (1)
            addRenderableWidget(Button.builder(
                    Component.translatable("gui.tyc2.hub.tab.expansion"),
                    b -> {
                        growthMode = 0;
                        rebuildControls();
                    }
            ).bounds(leftPos + 80, topPos + 56, 77, 12).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("gui.tyc2.hub.tab.aid"),
                    b -> {
                        growthMode = 1;
                        rebuildControls();
                        sendAction("REQUEST_SYNC", Map.of());
                    }
            ).bounds(leftPos + 159, topPos + 56, 77, 12).build());

            if (growthMode == 0) {
                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.terminal.upgrade_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                        b -> sendAction("UPGRADE_VILLAGE", Map.of())
                ).bounds(leftPos + 80, topPos + 91, 156, 14).build());

                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.terminal.deliver_cost_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                        b -> sendAction("DELIVER_EXPANSION", Map.of())
                ).bounds(leftPos + 80, topPos + 138, 68, 13).build());

                remoteAutoBtn = addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.auto_short.off"),
                        b -> sendAction("TOGGLE_EXPANSION_AUTO", Map.of())
                ).bounds(leftPos + 150, topPos + 138, 44, 13).build());

                remotePauseBtn = addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.pause_btn"),
                        b -> sendAction("TOGGLE_PAUSE_EXPANSION", Map.of())
                ).bounds(leftPos + 196, topPos + 138, 42, 13).build());
            } else {
                addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.prev"), b -> {
                    Map<String, Object> v = JsonLite.asObject(menu.snapshot().get("selectedVillage"));
                    int total = CooperationHubScreen.availableWorkplaces(v).size();
                    if (total > 0) {
                        aidProfIdx = (aidProfIdx - 1 + total) % total;
                    }
                }).bounds(leftPos + 80, topPos + 89, 13, 14).build());

                addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.next"), b -> {
                    Map<String, Object> v = JsonLite.asObject(menu.snapshot().get("selectedVillage"));
                    int total = CooperationHubScreen.availableWorkplaces(v).size();
                    if (total > 0) {
                        aidProfIdx = (aidProfIdx + 1) % total;
                    }
                }).bounds(leftPos + 223, topPos + 89, 13, 14).build());

                remoteAidSizeBtn = addRenderableWidget(Button.builder(
                        Component.translatable(aidLarge ? "gui.tyc2.aid.size.large" : "gui.tyc2.aid.size.small"),
                        b -> aidLarge = !aidLarge
                ).bounds(leftPos + 80, topPos + 105, 76, 14).build());

                remoteAidStartBtn = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.tyc2.aid.start_cost_btn",
                                TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                        ),
                        b -> {
                            Map<String, Object> v = JsonLite.asObject(menu.snapshot().get("selectedVillage"));
                            Map<String, Object> spec = selectedWorkplace(v);
                            if (spec != null) {
                                Map<String, Object> args = new LinkedHashMap<>();
                                args.put("professionId", JsonLite.getString(spec, "professionId", ""));
                                args.put("size", aidLarge ? "large" : "small");
                                sendAction("START_AID", args);
                            }
                        }
                ).bounds(leftPos + 158, topPos + 105, 78, 14).build());
            }
        }
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        Map<String, Object> snap = menu.snapshot();
        // Shift title right of the left green signal icon (8, 6, 12x12) so there is zero overlap
        drawFitString(canvas, title.getString(), 24, 8, 148, 0xFFCAE8FF);

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
        syncDynamicGrowthButtons(village);
        if (village == null) {
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.terminal.no_villages").getString(),
                    82, 86, 152, 0xFF8AA0B8
            );
            return;
        }

        // Right LCD header
        String vName = JsonLite.getString(village, "villageName", "");
        int level = JsonLite.getInt(village, "level", 1);
        long vFunds = JsonLite.getLong(village, "villageBalance", 0L);
        long bankBal = JsonLite.getLong(snap, "activeBalance", 0L);

        String titleLine = Component.translatable("gui.tyc2.terminal.level_prefix", level, vName).getString();
        drawFitString(canvas, titleLine, 80, 24, 114, 0xFFFFE57F);
        String balLine = Component.translatable("gui.tyc2.hub.village_funds", vFunds).getString()
                + " | " + Component.translatable("gui.tyc2.hub.status.bank", bankBal).getString();
        drawFitString(canvas, balLine, 80, 33, 114, 0xFF8CE8FF);

        int pmx = mouseX - leftPos;
        int pmy = mouseY - topPos;
        switch (subTab) {
            case 0 -> paintRemoteOrders(canvas, village, pmx, pmy);
            case 1 -> paintRemoteStock(canvas, village);
            case 2 -> paintRemoteEconomy(canvas, village);
            case 3 -> paintRemoteGrowth(canvas, village, pmx, pmy);
            default -> {
            }
        }
    }

    private void syncDynamicGrowthButtons(Map<String, Object> village) {
        if (remoteAutoBtn != null) {
            Map<String, Object> desire = village == null ? null : JsonLite.asObject(village.get("expansionDesire"));
            boolean auto = desire != null && JsonLite.getBoolean(desire, "autoFulfill", false);
            remoteAutoBtn.setMessage(Component.translatable(
                    auto ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        if (remotePauseBtn != null) {
            boolean paused = village != null && JsonLite.getBoolean(village, "expansionPaused", false);
            remotePauseBtn.setMessage(Component.translatable(
                    paused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
        }
        if (remoteAidSizeBtn != null || remoteAidStartBtn != null) {
            Map<String, Object> spec = selectedWorkplace(village);
            boolean largeUnlocked = spec != null && JsonLite.getBoolean(spec, "largeUnlocked", false);
            int minLvLarge = spec == null ? 3 : JsonLite.getInt(spec, "minLevelLarge", 3);
            boolean smallUnlocked = spec == null || JsonLite.getBoolean(spec, "smallUnlocked", true);
            if (remoteAidSizeBtn != null) {
                if (!aidLarge) {
                    remoteAidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.small"));
                } else if (largeUnlocked) {
                    remoteAidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.large"));
                } else {
                    remoteAidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.large_locked", minLvLarge));
                }
            }
            if (remoteAidStartBtn != null) {
                int queueSize = CooperationHubScreen.aidProjects(village).size();
                int maxQueue = village == null ? 3 : Math.max(1, JsonLite.getInt(village, "maxAidQueue", 3));
                boolean depop = village != null && JsonLite.getBoolean(village, "depopPaused", false);
                boolean unlocked = aidLarge ? largeUnlocked : smallUnlocked;
                remoteAidStartBtn.active = village != null && spec != null && !depop && queueSize < maxQueue && unlocked;
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
            String vname = JsonLite.getString(vm, "name", "");
            int lv = JsonLite.getInt(vm, "level", 1);
            boolean loaded = JsonLite.getBoolean(vm, "loaded", false);
            boolean sel = vid.equals(selectedId);
            boolean hover = pmx >= 10 && pmx < 70 && pmy >= ry && pmy < ry + 16;

            canvas.fill(10, ry, 70, ry + 16, sel ? 0xFF214866 : (hover ? 0xFF1A344B : 0xFF11202E));
            canvas.outline(10, ry, 60, 16, sel ? 0xFF56E4FF : 0xFF2B4358);
            // Status dot: green=loaded, cyan=remote
            canvas.fill(12, ry + 6, 15, ry + 9, loaded ? 0xFF42F587 : 0xFF39C4FF);
            drawFitString(canvas, vname, 17, ry + 4, 39, 0xFFEAF6FF);
            canvas.drawScaledString("Lv" + lv, 56, ry + 5, 13, 0.72F, 0xFFFFD866);
        }
    }

    private void paintRemoteOrders(ScreenCanvas canvas, Map<String, Object> village, int pmx, int pmy) {
        List<Object> raw = JsonLite.asArray(village.get("orders"));
        if (raw == null || raw.isEmpty()) {
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.no_orders").getString(),
                    82, 86, 152, 0xFF8AA0B8
            );
            return;
        }
        int totalPages = Math.max(1, (raw.size() + ORDERS_PER_PAGE - 1) / ORDERS_PER_PAGE);
        if (orderPage >= totalPages) {
            orderPage = totalPages - 1;
        }
        drawFitString(
                canvas,
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
            String name = TyC2Native.localizedItemName(itemId);
            String sideTag = Component.translatable(
                    sells ? "gui.tyc2.hub.side.sell_tag" : "gui.tyc2.hub.side.buy_tag"
            ).getString();
            drawFitString(canvas, sideTag + " " + name, 98, rowY + 3, 85, 0xFFEAF6FF);

            String sub = Component.translatable("gui.tyc2.hub.order_progress", doneQty, total).getString()
                    + " " + Component.translatable("gui.tyc2.hub.unit_price", price).getString();
            drawFitString(canvas, sub, 98, rowY + 13, 85, 0xFFFFD866);

            // Auto toggle box (184, rowY+5, 16x13)
            canvas.fill(184, rowY + 5, 200, rowY + 18, auto ? 0xFF247044 : 0xFF28323D);
            String tinyAuto = Component.translatable(
                    auto ? "gui.tyc2.hub.btn.auto_tiny.on" : "gui.tyc2.hub.btn.auto_tiny.off"
            ).getString();
            drawFitString(canvas, tinyAuto, 186, rowY + 8, 13, auto ? 0xFF7AFFA8 : 0xFF7A8C9E);

            // Remote Buy/Deliver button (202, rowY+4, 34x15)
            boolean hover = pmx >= 202 && pmx < 236 && pmy >= rowY + 4 && pmy < rowY + 19;
            canvas.fill(202, rowY + 4, 236, rowY + 19, done ? 0xFF252D35 : (hover ? 0xFF3B73A8 : 0xFF264E73));
            canvas.outline(202, rowY + 4, 34, 15, 0xFF8CB4D8);
            String actLbl = done
                    ? Component.translatable("gui.tyc2.confirm").getString()
                    : Component.translatable(sells ? "gui.tyc2.hub.buy" : "gui.tyc2.hub.deliver").getString();
            drawFitString(canvas, actLbl, 205, rowY + 8, 28, 0xFFFFFFFF);
        }
    }

    private void paintRemoteStock(ScreenCanvas canvas, Map<String, Object> village) {
        int inUsed = JsonLite.getInt(village, "inboundUsed", 0);
        int outUsed = JsonLite.getInt(village, "outboundUsed", 0);
        drawFitString(
                canvas,
                Component.translatable("gui.tyc2.terminal.remote_warehouse", inUsed, outUsed).getString(),
                148, 59, 88, 0xFF9BE8FF
        );

        Map<String, Object> summary = JsonLite.asObject(
                village.get(stockOutbound ? "outboundSummary" : "inboundSummary")
        );
        if (summary == null || summary.isEmpty()) {
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.zone.used", 0).getString(),
                    84, 86, 140, 0xFF8AA0B8
            );
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
            String lbl = TyC2Native.localizedItemName(e.getKey());
            int cnt = e.getValue() instanceof Number n ? n.intValue() : 0;
            drawFitString(canvas, lbl, x + 18, y + 1, 58, 0xFFEAF6FF);
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.slot_tooltip", cnt).getString(),
                    x + 18, y + 10, 58, 0xFFFFD866
            );
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
        String inflText = Component.translatable("gui.tyc2.economy." + infl.toLowerCase(Locale.ROOT)).getString();
        String cpiLine = Component.translatable("gui.tyc2.economy.cpi", String.format("%.2f", cpi)).getString()
                + " (" + inflText + ")";
        drawFitString(canvas, cpiLine, 80, 58, 156, cpiColor);
        String mqLine = Component.translatable("gui.tyc2.economy.money_supply", money).getString()
                + " | " + Component.translatable("gui.tyc2.economy.goods_circulation", goods).getString();
        drawFitString(canvas, mqLine, 80, 70, 156, 0xFFE2EFFA);
        drawFitString(
                canvas,
                Component.translatable("season.tyc2." + season.toLowerCase(Locale.ROOT)).getString()
                        + " | " + Component.translatable("tier.tyc2." + tier).getString(),
                80, 82, 156, 0xFF9BE8FF
        );
        drawFitString(
                canvas,
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

    private void paintRemoteGrowth(ScreenCanvas canvas, Map<String, Object> village, int pmx, int pmy) {
        canvas.outline(growthMode == 0 ? 80 : 159, 56, 77, 12, 0xFFFFD84D);

        if (growthMode == 1) {
            paintRemoteAid(canvas, village, pmx, pmy);
            return;
        }

        int level = JsonLite.getInt(village, "level", 1);
        int pop = JsonLite.getInt(village, "population", 0);
        long vol = JsonLite.getLong(village, "tradeVolume", 0L);
        long bal = JsonLite.getLong(village, "villageBalance", 0L);
        int reqPop = JsonLite.getInt(village, "nextMinPop", 0);
        long reqVol = JsonLite.getLong(village, "nextMinVol", 0L);
        long reqBal = JsonLite.getLong(village, "nextMinBal", 0L);
        int unempPct = JsonLite.getInt(village, "unemploymentPct", 0);
        int unempCount = JsonLite.getInt(village, "unemployedCount", 0);
        int workforce = JsonLite.getInt(village, "workforceCount", 0);
        boolean bonusActive = JsonLite.getBoolean(village, "bonusActive", false);

        String topLine = Component.translatable("gui.tyc2.hub.level", level, Component.translatable("level.tyc2." + level)).getString();
        if (workforce > 0) {
            topLine += " | " + Component.translatable("gui.tyc2.hub.unemployment", unempPct, unempCount, workforce).getString();
        }
        drawFitString(canvas, topLine, 80, 70, 156, 0.54F, bonusActive ? 0xFF72FFAA : 0xFFFFE57F);

        String reqStr = level >= 10
                ? Component.translatable("gui.tyc2.hub.max_level").getString()
                : Component.translatable(
                        "gui.tyc2.hub.upgrade_req",
                        pop + "/" + reqPop,
                        vol + "/" + reqVol,
                        bal + "/" + reqBal
                ).getString();
        drawFitString(canvas, reqStr, 80, 80, 156, 0.56F, 0xFFCAE8FF);

        Map<String, Object> build = JsonLite.asObject(village.get("activeBuild"));
        Map<String, Object> desire = JsonLite.asObject(village.get("expansionDesire"));
        if (build != null) {
            String pType = JsonLite.getString(build, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
            int pct = JsonLite.getInt(build, "progressPct", 0);
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.expansion.building",
                            Component.translatable("project.tyc2." + pType), pct).getString(),
                    80, 114, 156, 0xFF64E8FF
            );
        } else if (desire != null) {
            String pType = JsonLite.getString(desire, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
            long budget = JsonLite.getLong(desire, "budget", 0L);
            drawFitString(
                    canvas,
                    Component.translatable("project.tyc2." + pType).getString() + " (" + budget + " IC)",
                    80, 108, 156, 0.56F, 0xFFFFE066
            );
            Map<String, Object> req = JsonLite.asObject(desire.get("required"));
            Map<String, Object> del = JsonLite.asObject(desire.get("delivered"));
            if (req != null && !req.isEmpty()) {
                List<String> parts = new ArrayList<>(req.size());
                List<Boolean> met = new ArrayList<>(req.size());
                for (Map.Entry<String, Object> e : req.entrySet()) {
                    int need = JsonLite.getInt(req, e.getKey(), 0);
                    int have = del == null ? 0 : JsonLite.getInt(del, e.getKey(), 0);
                    parts.add(TyC2Native.localizedItemName(e.getKey()) + " " + have + "/" + need);
                    met.add(have >= need);
                }
                int split = (parts.size() + 1) / 2;
                StringBuilder line1 = new StringBuilder();
                boolean line1Met = true;
                for (int i = 0; i < split; i++) {
                    if (i > 0) {
                        line1.append(" · ");
                    }
                    line1.append(parts.get(i));
                    line1Met &= met.get(i);
                }
                drawFitString(
                        canvas,
                        line1.toString(),
                        80, 118, 156, 0.46F,
                        line1Met ? 0xFF66FF99 : 0xFFFFCC66
                );
                if (split < parts.size()) {
                    StringBuilder line2 = new StringBuilder();
                    boolean line2Met = true;
                    for (int i = split; i < parts.size(); i++) {
                        if (i > split) {
                            line2.append(" · ");
                        }
                        line2.append(parts.get(i));
                        line2Met &= met.get(i);
                    }
                    drawFitString(
                            canvas,
                            line2.toString(),
                            80, 127, 156, 0.46F,
                            line2Met ? 0xFF66FF99 : 0xFFFFCC66
                    );
                }
            }
        } else {
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.expansion.none").getString(),
                    80, 114, 156, 0xFF8AA0B8
            );
        }

        // Nearby Logistics coordinates
        List<Object> lc2 = JsonLite.asArray(village.get("lc2Stations"));
        if (lc2 == null || lc2.isEmpty()) {
            drawFitString(
                    canvas,
                    Component.translatable(
                            "gui.tyc2.terminal.lc2_nearby",
                            Component.translatable("gui.tyc2.terminal.no_lc2")
                    ).getString(),
                    80, 156, 156, 0xFF7B8E9F
            );
        } else {
            drawFitString(
                    canvas,
                    Component.translatable(
                            "gui.tyc2.terminal.lc2_nearby",
                            Lc2SoftBridge.formatStationLabel(lc2.get(0).toString())
                    ).getString(),
                    80, 156, 156, 0xFF64E8FF
            );
            int y = 168;
            for (int i = 1; i < Math.min(3, lc2.size()); i++) {
                drawFitString(
                        canvas,
                        Lc2SoftBridge.formatStationLabel(lc2.get(i).toString()),
                        80, y, 156, 0xFF64E8FF
                );
                y += 10;
            }
        }
    }

    private void paintRemoteAid(ScreenCanvas canvas, Map<String, Object> village, int pmx, int pmy) {
        int unempPct = JsonLite.getInt(village, "unemploymentPct", 0);
        int unempCount = JsonLite.getInt(village, "unemployedCount", 0);
        int workforce = JsonLite.getInt(village, "workforceCount", 0);
        boolean bonusActive = JsonLite.getBoolean(village, "bonusActive", false);
        int restockPct = JsonLite.getInt(village, "restockBonusPct", 0);
        int qtyPct = JsonLite.getInt(village, "qtyBonusPct", 0);
        int thresholdPct = JsonLite.getInt(village, "lowUnempThresholdPct", 25);

        drawFitString(
                canvas,
                Component.translatable("gui.tyc2.hub.unemployment", unempPct, unempCount, workforce).getString(),
                80, 70, 156, 0.56F, bonusActive ? 0xFF72FFAA : 0xFFE0ECF8
        );
        String bonusLine = bonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", restockPct, qtyPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", thresholdPct).getString();
        drawFitString(canvas, bonusLine, 80, 79, 156, 0.50F, bonusActive ? 0xFF56EC8B : 0xFF8AA0B8);

        // Profession picker display (94..222, 89..103)
        canvas.fill(94, 89, 222, 103, 0xFF132231);
        canvas.outline(94, 89, 128, 14, 0xFF3E6F9E);
        Map<String, Object> spec = selectedWorkplace(village);
        if (spec != null) {
            String wsBlockId = JsonLite.getString(spec, "workstationBlockId", "");
            ItemStack wsStack = TyC2Native.stack(wsBlockId, 1);
            if (!wsStack.isEmpty()) {
                canvas.drawItem(wsStack, 95, 88);
            }
            String profLabel = CooperationHubScreen.localizedProfessionName(
                    JsonLite.getString(spec, "archetype", "GENERIC"),
                    JsonLite.getString(spec, "professionId", ""),
                    JsonLite.getString(spec, "displayNameFallback", "")
            );
            drawFitString(canvas, profLabel, 113, 92, 106, 0.58F, 0xFFEAF6FF);

            long cost = aidLarge
                    ? JsonLite.getLong(spec, "costLargeIc", 0L)
                    : JsonLite.getLong(spec, "costSmallIc", 0L);
            boolean unlocked = aidLarge
                    ? JsonLite.getBoolean(spec, "largeUnlocked", false)
                    : JsonLite.getBoolean(spec, "smallUnlocked", true);
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.aid.cost", cost).getString(),
                    80, 122, 156, 0.56F, unlocked ? 0xFFFFD866 : 0xFFFF7766
            );
        }

        double gatherMult = JsonLite.getDouble(village, "gatherSpeedMult", 1.0);
        List<Map<String, Object>> queue = CooperationHubScreen.aidProjects(village);
        int maxQueue = Math.max(1, JsonLite.getInt(village, "maxAidQueue", 3));
        drawFitString(
                canvas,
                Component.translatable("gui.tyc2.aid.gather_rate", String.format("%.2f", gatherMult), unempCount).getString(),
                80, 131, 98, 0.50F, 0xFF9BE8FF
        );
        drawFitString(
                canvas,
                Component.translatable("gui.tyc2.aid.queue_header", queue.size(), maxQueue).getString(),
                180, 131, 56, 0.52F, 0xFFFFE57F
        );

        if (queue.isEmpty()) {
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    84, 158, 148, 0xFF8AA0B8
            );
            return;
        }

        int shown = Math.min(3, queue.size());
        for (int i = 0; i < shown; i++) {
            Map<String, Object> proj = queue.get(i);
            int ry = 141 + i * 18;
            String status = JsonLite.getString(proj, "status", "COLLECTING");
            int pct = Math.max(0, Math.min(100, JsonLite.getInt(proj, "progressPct", 0)));
            boolean canCancel = JsonLite.getBoolean(proj, "canCancel", false);
            String sizeStr = JsonLite.getString(proj, "size", "SMALL");
            String profName = CooperationHubScreen.localizedProfessionName(
                    JsonLite.getString(proj, "archetype", "GENERIC"),
                    JsonLite.getString(proj, "professionId", ""),
                    JsonLite.getString(proj, "displayNameFallback", "")
            );
            String sizeShort = "LARGE".equalsIgnoreCase(sizeStr) ? "L" : "S";

            int bg = "BUILDING".equals(status) ? 0xFF152936
                    : ("NO_SITE".equals(status) ? 0xFF2D1E18 : 0xFF17222D);
            int border = "BUILDING".equals(status) ? 0xFF4BE2FF
                    : ("NO_SITE".equals(status) ? 0xFFFF8855 : 0xFF3E6F9E);
            canvas.fill(80, ry, 236, ry + 17, bg);
            canvas.outline(80, ry, 156, 17, border);

            int barMaxW = canCancel ? 110 : 154;
            int barW = (int) Math.round(barMaxW * pct / 100.0);
            if (barW > 0) {
                int barColor = "BUILDING".equals(status) ? 0xFF39D98A : 0xFF4CA8FF;
                canvas.fill(81, ry + 15, 81 + barW, ry + 16, barColor);
            }

            int textMaxW = canCancel ? 108 : 150;
            drawFitString(canvas, profName + " [" + sizeShort + "]", 82, ry + 1, textMaxW, 0.54F, 0xFFEAF6FF);
            drawFitString(
                    canvas,
                    CooperationHubScreen.aidStatusText(status, pct),
                    82, ry + 8, textMaxW, 0.52F,
                    "NO_SITE".equals(status) ? 0xFFFFAA66 : ("BUILDING".equals(status) ? 0xFF66FF99 : 0xFFFFDF7F)
            );

            if (canCancel) {
                boolean hoverCancel = pmx >= 193 && pmx < 234 && pmy >= ry + 2 && pmy < ry + 14;
                canvas.fill(193, ry + 2, 234, ry + 14, hoverCancel ? 0xFF9E3B33 : 0xFF6E2924);
                canvas.outline(193, ry + 2, 41, 12, 0xFFD87A72);
                drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.aid.cancel_btn").getString(),
                        195, ry + 4, 37, 0.50F, 0xFFFFFFFF
                );
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
                        if (pmx >= 184 && pmx < 200 && pmy >= rowY + 5 && pmy < rowY + 18) {
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
        } else if (subTab == 3 && growthMode == 1) {
            Map<String, Object> village = JsonLite.asObject(snap.get("selectedVillage"));
            List<Map<String, Object>> queue = CooperationHubScreen.aidProjects(village);
            int shown = Math.min(3, queue.size());
            for (int i = 0; i < shown; i++) {
                int ry = 141 + i * 18;
                Map<String, Object> proj = queue.get(i);
                if (JsonLite.getBoolean(proj, "canCancel", false)
                        && pmx >= 193 && pmx < 234 && pmy >= ry + 2 && pmy < ry + 14) {
                    String projId = JsonLite.getString(proj, "projectId", "");
                    if (!projId.isEmpty()) {
                        sendAction("CANCEL_AID", Map.of("projectId", projId));
                        return true;
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
        Map<String, Object> snap = menu.snapshot();
        if (pmx >= 178 && pmx < 236 && pmy >= 6 && pmy < 18) {
            int eu = JsonLite.getInt(snap, "energyEu", 0);
            int maxEu = JsonLite.getInt(snap, "maxEu", TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
            canvas.drawTooltip(
                    List.of(Component.translatable("gui.tyc2.terminal.battery", eu, maxEu)),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (subTab == 3 && growthMode == 1) {
            Map<String, Object> village = JsonLite.asObject(snap.get("selectedVillage"));
            List<Map<String, Object>> queue = CooperationHubScreen.aidProjects(village);
            int shown = Math.min(3, queue.size());
            for (int i = 0; i < shown; i++) {
                int ry = 141 + i * 18;
                if (pmx >= 80 && pmx < 193 && pmy >= ry && pmy < ry + 17) {
                    List<Component> tip = CooperationHubScreen.buildAidMaterialsTooltip(queue.get(i));
                    if (!tip.isEmpty()) {
                        canvas.drawTooltip(tip, mouseX, mouseY);
                    }
                    return;
                }
            }
        }
    }

    private Map<String, Object> selectedWorkplace(Map<String, Object> village) {
        List<Map<String, Object>> list = CooperationHubScreen.availableWorkplaces(village);
        if (list.isEmpty()) {
            return null;
        }
        if (aidProfIdx < 0 || aidProfIdx >= list.size()) {
            aidProfIdx = 0;
        }
        return list.get(aidProfIdx);
    }

    private static void drawFitString(ScreenCanvas canvas, String text, int x, int y, int maxWidth, int color) {
        drawFitString(canvas, text, x, y, maxWidth, 0.62F, color);
    }

    private static void drawFitString(
            ScreenCanvas canvas, String text, int x, int y, int maxWidth, float minScale, int color
    ) {
        int w = canvas.textWidth(text);
        if (w <= maxWidth) {
            canvas.drawClippedString(text, x, y, maxWidth, color);
        } else {
            float sc = Math.max(minScale, maxWidth / (float) w);
            canvas.drawScaledString(text, x, y, maxWidth, sc, color);
        }
    }

    private void sendAction(String action, Map<String, Object> args) {
        String json = args.isEmpty() ? "{}" : JsonLite.toJson(new LinkedHashMap<>(args));
        TyC2Native.sendToServer(new TyC2ActionPayload("TERMINAL", BlockPos.ZERO, action, json));
    }
}
