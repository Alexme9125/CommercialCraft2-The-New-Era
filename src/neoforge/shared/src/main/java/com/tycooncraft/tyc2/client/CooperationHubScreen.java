package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.menu.CooperationHubMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Pixel-exact 4-tab GUI screen for the Village Cooperation Hub ({@code gui/cooperation_hub.png}, 248x222).
 * 100% localized across zh_cn and en_us with zero hardcoded UI labels or mechanic-advertising strings.
 */
public final class CooperationHubScreen extends TyC2ScreenBase<CooperationHubMenu> {

    private static final String GUI_TEX = "cooperation_hub";
    private static final int ORDERS_PER_PAGE = 3;
    private static final int[] QTY_PRESETS = {1, 16, 64, 9999};

    private int activeTab = 0;       // 0=Orders, 1=Warehouse, 2=Economy, 3=Growth/Settings
    private int orderFilter = 0;     // 0=All, 1=Sell, 2=Buy
    private int orderPage = 0;
    private int qtyPresetIdx = 1;    // default 16
    private boolean cashMode = false;

    private String whZone = "INBOUND";
    private int whPage = 0;
    private String whSearch = "";

    private int growthSubTab = 0;    // 0=Upgrade & Expansion, 1=Settings & Logistics
    private boolean terminateArmed = false;

    private EditBox searchInput;
    private EditBox renameInput;
    private Button expAutoBtn;
    private Button expPauseBtn;

    public CooperationHubScreen(CooperationHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 248, 222);
    }

    @Override
    protected boolean drawDefaultLabels() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        rebuildControls();
        requestSync();
    }

    private void rebuildControls() {
        clearWidgets();
        clearEditBoxes();
        searchInput = null;
        renameInput = null;
        expAutoBtn = null;
        expPauseBtn = null;
        terminateArmed = false;

        // Top 4 Tabs inside (8, 5, 232x14)
        String[] tabKeys = {
                "gui.tyc2.hub.tab.orders",
                "gui.tyc2.hub.tab.warehouse",
                "gui.tyc2.hub.tab.economy",
                "gui.tyc2.hub.tab.expansion"
        };
        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            addRenderableWidget(Button.builder(
                    Component.translatable(tabKeys[i]),
                    b -> {
                        activeTab = tabIdx;
                        rebuildControls();
                        requestSync();
                    }
            ).bounds(leftPos + 8 + i * 58, topPos + 5, 57, 14).build());
        }

        if (activeTab == 0) {
            // Orders toolbar inside viewport top (10, 50)
            addRenderableWidget(Button.builder(
                    orderFilterComponent(),
                    b -> {
                        orderFilter = (orderFilter + 1) % 3;
                        orderPage = 0;
                        b.setMessage(orderFilterComponent());
                    }
            ).bounds(leftPos + 10, topPos + 50, 38, 12).build());

            addRenderableWidget(Button.builder(
                    Component.translatable(cashMode ? "gui.tyc2.hub.cash_mode" : "gui.tyc2.hub.bank_mode"),
                    b -> {
                        cashMode = !cashMode;
                        b.setMessage(Component.translatable(cashMode ? "gui.tyc2.hub.cash_mode" : "gui.tyc2.hub.bank_mode"));
                    }
            ).bounds(leftPos + 50, topPos + 50, 74, 12).build());

            addRenderableWidget(Button.builder(
                    qtyPresetComponent(),
                    b -> {
                        qtyPresetIdx = (qtyPresetIdx + 1) % QTY_PRESETS.length;
                        b.setMessage(qtyPresetComponent());
                    }
            ).bounds(leftPos + 126, topPos + 50, 48, 12).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.prev"), b -> {
                orderPage = Math.max(0, orderPage - 1);
            }).bounds(leftPos + 206, topPos + 50, 14, 12).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.next"), b -> {
                int maxPage = Math.max(0, (filteredOrders().size() - 1) / ORDERS_PER_PAGE);
                orderPage = Math.min(maxPage, orderPage + 1);
            }).bounds(leftPos + 222, topPos + 50, 14, 12).build());

        } else if (activeTab == 1) {
            // Warehouse toolbar (10, 50) — concise localized Inbound/Outbound with no internal slot-count ads
            boolean isOut = "OUTBOUND".equals(whZone);
            addRenderableWidget(Button.builder(
                    Component.translatable(isOut ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"),
                    b -> {
                        whZone = "OUTBOUND".equals(whZone) ? "INBOUND" : "OUTBOUND";
                        whPage = 0;
                        b.setMessage(Component.translatable(
                                "OUTBOUND".equals(whZone) ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
                        ));
                        requestSync();
                    }
            ).bounds(leftPos + 10, topPos + 50, 54, 12).build());

            searchInput = addTrackedEditBox(new EditBox(
                    font, leftPos + 66, topPos + 50, 74, 12,
                    Component.translatable("gui.tyc2.hub.search_placeholder")
            ));
            searchInput.setMaxLength(32);
            searchInput.setValue(whSearch);
            searchInput.setResponder(txt -> {
                whSearch = txt == null ? "" : txt.trim();
                whPage = 0;
                requestSync();
            });
            addRenderableWidget(searchInput);

            addRenderableWidget(Button.builder(
                    Component.translatable("gui.tyc2.hub.btn.put"),
                    b -> sendAction("WAREHOUSE_DEPOSIT_INV", viewArgs())
            ).bounds(leftPos + 142, topPos + 50, 32, 12).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.prev"), b -> {
                if (whPage > 0) {
                    whPage--;
                    requestSync();
                }
            }).bounds(leftPos + 178, topPos + 50, 14, 12).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.tyc2.next"), b -> {
                int total = Math.max(1, JsonLite.getInt(menu.snapshot(), "whTotalPages", 1));
                if (whPage + 1 < total) {
                    whPage++;
                    requestSync();
                }
            }).bounds(leftPos + 222, topPos + 50, 14, 12).build());

        } else if (activeTab == 3) {
            // Sub-toggle: Growth vs Settings
            addRenderableWidget(Button.builder(
                    Component.translatable("gui.tyc2.hub.tab.expansion"),
                    b -> {
                        growthSubTab = 0;
                        rebuildControls();
                    }
            ).bounds(leftPos + 10, topPos + 50, 62, 12).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("gui.tyc2.hub.tab.settings"),
                    b -> {
                        growthSubTab = 1;
                        rebuildControls();
                    }
            ).bounds(leftPos + 74, topPos + 50, 62, 12).build());

            if (growthSubTab == 0) {
                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.upgrade_btn"),
                        b -> sendAction("UPGRADE_VILLAGE", viewArgs())
                ).bounds(leftPos + 152, topPos + 50, 84, 13).build());

                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.expansion.deliver"),
                        b -> sendAction("DELIVER_EXPANSION", viewArgs())
                ).bounds(leftPos + 10, topPos + 118, 86, 13).build());

                expAutoBtn = addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.auto_short.off"),
                        b -> sendAction("TOGGLE_EXPANSION_AUTO", viewArgs())
                ).bounds(leftPos + 98, topPos + 118, 62, 13).build());

                expPauseBtn = addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.pause_btn"),
                        b -> sendAction("TOGGLE_PAUSE_EXPANSION", viewArgs())
                ).bounds(leftPos + 162, topPos + 118, 74, 13).build());
            } else {
                renameInput = addTrackedEditBox(new EditBox(
                        font, leftPos + 10, topPos + 66, 112, 12,
                        Component.translatable("gui.tyc2.hub.rename_placeholder")
                ));
                renameInput.setMaxLength(32);
                renameInput.setValue(JsonLite.getString(menu.snapshot(), "villageName", ""));
                addRenderableWidget(renameInput);

                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.save"),
                        b -> {
                            String val = renameInput.getValue().trim();
                            if (!val.isEmpty()) {
                                Map<String, Object> args = viewArgs();
                                args.put("name", val);
                                sendAction("RENAME_VILLAGE", args);
                            }
                        }
                ).bounds(leftPos + 125, topPos + 66, 46, 12).build());

                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.relocate"),
                        b -> sendAction("RELOCATE_HUB", viewArgs())
                ).bounds(leftPos + 10, topPos + 116, 108, 14).build());

                addRenderableWidget(Button.builder(
                        Component.translatable("gui.tyc2.hub.terminate"),
                        b -> {
                            if (!terminateArmed) {
                                terminateArmed = true;
                                b.setMessage(Component.translatable("gui.tyc2.hub.terminate_confirm"));
                            } else {
                                terminateArmed = false;
                                sendAction("TERMINATE_PARTNERSHIP", viewArgs());
                            }
                        }
                ).bounds(leftPos + 122, topPos + 116, 114, 14).build());
            }
        }
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        // Highlight active top tab
        canvas.outline(8 + activeTab * 58, 5, 57, 14, 0xFFFFD84D);

        Map<String, Object> snap = menu.snapshot();
        syncDynamicGrowthButtons(snap);
        paintBanner(canvas, snap);

        int pmx = mouseX - leftPos;
        int pmy = mouseY - topPos;
        switch (activeTab) {
            case 0 -> paintOrdersTab(canvas, snap, pmx, pmy);
            case 1 -> paintWarehouseTab(canvas, snap, pmx, pmy);
            case 2 -> paintEconomyTab(canvas, snap);
            case 3 -> paintGrowthSettingsTab(canvas, snap);
            default -> {
            }
        }

        paintStatusLcd(canvas, snap);
    }

    private void syncDynamicGrowthButtons(Map<String, Object> snap) {
        if (expAutoBtn != null) {
            Map<String, Object> desire = JsonLite.asObject(snap.get("expansionDesire"));
            boolean auto = desire != null && JsonLite.getBoolean(desire, "autoFulfill", false);
            expAutoBtn.setMessage(Component.translatable(
                    auto ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        if (expPauseBtn != null) {
            boolean paused = JsonLite.getBoolean(snap, "expansionPaused", false);
            expPauseBtn.setMessage(Component.translatable(
                    paused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
        }
    }

    private void paintBanner(ScreenCanvas canvas, Map<String, Object> snap) {
        String name = JsonLite.getString(snap, "villageName", "");
        int level = JsonLite.getInt(snap, "level", 1);
        int pop = JsonLite.getInt(snap, "population", 0);
        long funds = JsonLite.getLong(snap, "villageBalance", 0L);
        boolean depop = JsonLite.getBoolean(snap, "depopPaused", false);
        boolean chunkLoaded = JsonLite.getBoolean(snap, "chunkLoaderActive", false);
        String season = JsonLite.getString(snap, "season", "NONE");
        String infl = JsonLite.getString(snap, "inflationState", "STABLE");

        // Mayor crown icon (212, 226, 16x16)
        canvas.blit(GUI_TEX, 11, 25, 212, 226, 16, 16);

        String lvlText = Component.translatable(
                "gui.tyc2.hub.level", level, Component.translatable("level.tyc2." + level)
        ).getString();
        String titleLine = name.isEmpty() ? lvlText : (name + "  " + lvlText);
        drawFitString(canvas, titleLine, 30, 23, 156, 0xFFFFE57F);

        if (depop) {
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.paused_depopulated").getString(),
                    30, 34, 158, 0xFFFF6666
            );
        } else {
            String stats = Component.translatable("gui.tyc2.hub.population", pop).getString()
                    + " | " + Component.translatable("gui.tyc2.hub.village_funds", funds).getString();
            drawFitString(canvas, stats, 30, 34, 158, 0xFF9BE8FF);
        }

        // Season sprite (62/76/90/104, 226, 12x12)
        int seasonU = seasonSpriteU(season);
        if (seasonU >= 0) {
            canvas.blit(GUI_TEX, 192, 27, seasonU, 226, 12, 12);
        }

        // CPI arrow sprite (120=up/infl, 132=down/defl, 144=flat/stable, 10x10)
        int cpiU = "INFLATION".equals(infl) ? 120 : ("DEFLATION".equals(infl) ? 132 : 144);
        canvas.blit(GUI_TEX, 207, 28, cpiU, 226, 10, 10);

        // Chunk loader badge (192, 226, 16x16) when active
        if (chunkLoaded) {
            canvas.blit(GUI_TEX, 220, 25, 192, 226, 16, 16);
        }
    }

    private void paintOrdersTab(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        List<Map<String, Object>> orders = filteredOrders();
        int totalPages = Math.max(1, (orders.size() + ORDERS_PER_PAGE - 1) / ORDERS_PER_PAGE);
        if (orderPage >= totalPages) {
            orderPage = totalPages - 1;
        }
        canvas.drawClippedString((orderPage + 1) + "/" + totalPages, 178, 52, 26, 0xFFB0C4DE);

        if (orders.isEmpty()) {
            canvas.drawClippedString(
                    Component.translatable("gui.tyc2.hub.no_orders").getString(),
                    16, 86, 214, 0xFF8AA0B8
            );
            return;
        }

        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
            Map<String, Object> o = orders.get(start + i);
            int rowY = 65 + i * 22;
            boolean villageSells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
            String itemId = JsonLite.getString(o, "itemId", "");
            int totalQty = JsonLite.getInt(o, "totalQty", 1);
            int fulfilled = JsonLite.getInt(o, "fulfilledQty", 0);
            long unitPrice = JsonLite.getLong(o, "unitPrice", 1L);
            boolean auto = JsonLite.getBoolean(o, "autoFulfill", false);
            boolean seasonal = JsonLite.getBoolean(o, "seasonalBonus", false);
            boolean done = fulfilled >= totalQty;

            int bg = done ? 0xFF1A242D : (villageSells ? 0xFF162533 : 0xFF282016);
            canvas.fill(10, rowY, 238, rowY + 20, bg);
            canvas.outline(10, rowY, 228, 20, villageSells ? 0xFF3E6F9E : 0xFF9E753E);

            ItemStack preview = TyC2Native.stack(itemId, 1);
            canvas.drawItem(preview, 12, rowY + 2);

            String itemName = TyC2Native.localizedItemName(itemId);
            String sideTag = Component.translatable(
                    villageSells ? "gui.tyc2.hub.side.sell_tag" : "gui.tyc2.hub.side.buy_tag"
            ).getString();
            String prefix = sideTag + " " + (seasonal ? "* " : "");
            drawFitString(canvas, prefix + itemName, 31, rowY + 2, 130, done ? 0xFF708090 : 0xFFEAF6FF);

            String sub = Component.translatable("gui.tyc2.hub.order_progress", fulfilled, totalQty).getString()
                    + " | " + Component.translatable("gui.tyc2.hub.unit_price", unitPrice).getString();
            drawFitString(canvas, sub, 31, rowY + 11, 130, 0xFFFFD866);

            // Auto-fulfill toggle icon (158=ON, 174=OFF, 14x14) at (166, rowY+3)
            canvas.blit(GUI_TEX, 166, rowY + 3, auto ? 158 : 174, 226, 14, 14);

            // Action button box (183, rowY+3, 52x14)
            boolean hoverBtn = pmx >= 183 && pmx < 235 && pmy >= rowY + 3 && pmy < rowY + 17;
            int btnFill = done ? 0xFF2A323A : (hoverBtn ? 0xFF3C6E9C : (villageSells ? 0xFF285075 : 0xFF6B4C22));
            canvas.fill(183, rowY + 3, 235, rowY + 17, btnFill);
            canvas.outline(183, rowY + 3, 52, 14, 0xFF90B4D0);
            String btnLbl = done
                    ? Component.translatable("gui.tyc2.confirm").getString()
                    : Component.translatable(villageSells ? "gui.tyc2.hub.buy" : "gui.tyc2.hub.deliver").getString();
            drawFitString(canvas, btnLbl, 187, rowY + 6, 45, done ? 0xFF8899AA : 0xFFFFFFFF);
        }
    }

    private void paintWarehouseTab(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        int page = JsonLite.getInt(snap, "whPage", whPage);
        int totalPages = Math.max(1, JsonLite.getInt(snap, "whTotalPages", 1));
        canvas.drawClippedString((page + 1) + "/" + totalPages, 195, 52, 26, 0xFFCAE6FF);

        boolean outbound = "OUTBOUND".equals(JsonLite.getString(snap, "whZone", whZone));
        int slotU = outbound ? 40 : 20; // 20=inbound blue, 40=outbound amber (18x18 at v=226)

        List<Object> slots = JsonLite.asArray(snap.get("whSlots"));
        for (int idx = 0; idx < 36; idx++) {
            int row = idx / 12;
            int col = idx % 12;
            int sx = 16 + col * 18;
            int sy = 67 + row * 20;
            canvas.blit(GUI_TEX, sx, sy, slotU, 226, 18, 18);

            if (slots != null && idx < slots.size()) {
                Map<String, Object> sm = JsonLite.asObject(slots.get(idx));
                if (sm != null) {
                    String itemId = JsonLite.getString(sm, "itemId", "");
                    int count = JsonLite.getInt(sm, "count", 0);
                    if (!itemId.isEmpty() && count > 0) {
                        ItemStack stack = TyC2Native.stack(itemId, Math.min(64, count));
                        canvas.drawItem(stack, sx + 1, sy + 1);
                        canvas.drawItemDecorations(stack, sx + 1, sy + 1, count > 1 ? String.valueOf(count) : null);
                    }
                }
            }
            if (pmx >= sx && pmx < sx + 18 && pmy >= sy && pmy < sy + 18) {
                canvas.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x55FFFFFF);
            }
        }
    }

    private void paintEconomyTab(ScreenCanvas canvas, Map<String, Object> snap) {
        double cpi = JsonLite.getDouble(snap, "cpi", 1.0);
        String infl = JsonLite.getString(snap, "inflationState", "STABLE");
        long money = JsonLite.getLong(snap, "moneySupply", 0L);
        long goods = JsonLite.getLong(snap, "goodsCirculation", 0L);
        double liq = JsonLite.getDouble(snap, "villageLiquidity", 1.0);
        String season = JsonLite.getString(snap, "season", "NONE");
        int tier = JsonLite.getInt(snap, "industrialTier", 0);

        int cpiColor = "INFLATION".equals(infl) ? 0xFFFF6B5E : ("DEFLATION".equals(infl) ? 0xFF4BE2FF : 0xFF56EC8B);
        String inflText = Component.translatable("gui.tyc2.economy." + infl.toLowerCase(Locale.ROOT)).getString();

        String cpiLine = Component.translatable("gui.tyc2.hub.status.cpi", String.format("%.2f", cpi)).getString()
                + " (" + inflText + ")";
        drawFitString(canvas, cpiLine, 12, 52, 108, cpiColor);
        drawFitString(canvas, Component.translatable("gui.tyc2.economy.money_supply", money).getString(), 12, 64, 108, 0xFFE2EFFA);
        drawFitString(canvas, Component.translatable("gui.tyc2.economy.goods_circulation", goods).getString(), 12, 76, 108, 0xFFE2EFFA);
        drawFitString(canvas, Component.translatable("gui.tyc2.economy.village_liquidity", String.format("%.2fx", liq)).getString(), 12, 88, 108, 0xFFFFDF7F);
        drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.season",
                        Component.translatable("season.tyc2." + season.toLowerCase(Locale.ROOT))).getString(),
                12, 100, 108, 0xFF9BE8FF
        );
        drawFitString(canvas, Component.translatable("tier.tyc2." + tier).getString(), 12, 112, 108, 0xFFB8D4F0);

        String chartTitle = Component.translatable("gui.tyc2.economy.cpi_chart").getString();
        drawFitString(canvas, chartTitle, 122, 50, 114, 0xFFAEC8E0);
        CpiChartRenderer.drawChart(canvas, 122, 60, 114, 70, extractCpiHistory(snap));
    }

    private void paintGrowthSettingsTab(ScreenCanvas canvas, Map<String, Object> snap) {
        if (growthSubTab == 0) {
            int level = JsonLite.getInt(snap, "level", 1);
            int pop = JsonLite.getInt(snap, "population", 0);
            long vol = JsonLite.getLong(snap, "tradeVolume", 0L);
            long bal = JsonLite.getLong(snap, "villageBalance", 0L);
            boolean canUp = JsonLite.getBoolean(snap, "canUpgrade", false);

            if (level >= 10) {
                drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.hub.max_level").getString(),
                        12, 66, 224, 0xFFFFD84D
                );
            } else {
                int reqPop = JsonLite.getInt(snap, "nextMinPop", 0);
                long reqVol = JsonLite.getLong(snap, "nextMinVol", 0L);
                long reqBal = JsonLite.getLong(snap, "nextMinBal", 0L);
                String reqLine = Component.translatable(
                        "gui.tyc2.hub.upgrade_req",
                        pop + "/" + reqPop,
                        vol + "/" + reqVol,
                        bal + "/" + reqBal
                ).getString();
                drawFitString(canvas, reqLine, 12, 66, 224, canUp ? 0xFF66FF99 : 0xFFE0ECF8);
            }

            Map<String, Object> build = JsonLite.asObject(snap.get("activeBuild"));
            Map<String, Object> desire = JsonLite.asObject(snap.get("expansionDesire"));
            if (build != null) {
                String pType = JsonLite.getString(build, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
                int pct = JsonLite.getInt(build, "progressPct", 0);
                Component pName = Component.translatable("project.tyc2." + pType);
                drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.expansion.building", pName, pct).getString(),
                        12, 80, 224, 0xFF64E8FF
                );
                canvas.fill(12, 94, 232, 104, 0xFF142230);
                int fillW = Math.max(2, (int) Math.round(218.0 * pct / 100.0));
                canvas.fill(13, 95, 13 + fillW, 103, 0xFF39D98A);
            } else if (desire != null) {
                String pType = JsonLite.getString(desire, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
                long budget = JsonLite.getLong(desire, "budget", 0L);
                boolean auto = JsonLite.getBoolean(desire, "autoFulfill", false);
                Component pName = Component.translatable("project.tyc2." + pType);
                String autoTxt = Component.translatable(
                        auto ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
                ).getString();
                String header = pName.getString() + " (" + budget + " IC) " + autoTxt;
                drawFitString(canvas, header, 12, 79, 224, 0xFFFFE066);
                Map<String, Object> req = JsonLite.asObject(desire.get("required"));
                Map<String, Object> del = JsonLite.asObject(desire.get("delivered"));
                for (MaterialCell cell : layoutExpansionMaterials(req)) {
                    int need = JsonLite.getInt(req, cell.itemId(), 0);
                    int have = del == null ? 0 : JsonLite.getInt(del, cell.itemId(), 0);
                    ItemStack st = TyC2Native.stack(cell.itemId(), 1);
                    canvas.drawItem(st, cell.x(), cell.y());
                    drawFitString(
                            canvas,
                            have + "/" + need,
                            cell.x() + 18,
                            cell.y() + 4,
                            cell.textW(),
                            0.50F,
                            have >= need ? 0xFF66FF99 : 0xFFFFCC66
                    );
                }
            } else {
                drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.expansion.none").getString(),
                        12, 84, 224, 0xFF8AA0B8
                );
            }

            boolean paused = JsonLite.getBoolean(snap, "expansionPaused", false);
            drawFitString(
                    canvas,
                    Component.translatable(paused ? "gui.tyc2.expansion.pause.on" : "gui.tyc2.expansion.pause.off").getString(),
                    12, 108, 224, paused ? 0xFFFF8866 : 0xFF7BE89A
            );
        } else {
            // Settings & Nearby Logistics
            int minR = JsonLite.getInt(snap, "minRadius", 16);
            int maxR = JsonLite.getInt(snap, "maxRadius", 64);
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.expansion.radius", minR, maxR).getString(),
                    12, 82, 224, 0xFFAEC8E0
            );

            List<Object> stations = JsonLite.asArray(snap.get("lc2Stations"));
            String lc2Summary = (stations == null || stations.isEmpty())
                    ? Component.translatable("gui.tyc2.terminal.no_lc2").getString()
                    : Lc2SoftBridge.formatStationLabel(stations.get(0).toString())
                            + (stations.size() > 1 ? " (+" + (stations.size() - 1) + ")" : "");
            drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", lc2Summary).getString(),
                    12, 94, 224, 0xFF64E8FF
            );

            boolean canManage = JsonLite.getBoolean(snap, "canManage", false);
            if (!canManage) {
                drawFitString(
                        canvas,
                        Component.translatable("status.tyc2.hub.not_owner").getString(),
                        12, 105, 224, 0xFFFF8866
                );
            }
        }
    }

    private void paintStatusLcd(ScreenCanvas canvas, Map<String, Object> snap) {
        // Bottom right Status LCD (179, 166, 61x49) — 100% localized, zero slot-cap advertising
        long bankBal = JsonLite.getLong(snap, "playerBankBalance", 0L);
        int inUsed = JsonLite.getInt(snap, "inboundUsed", 0);
        int outUsed = JsonLite.getInt(snap, "outboundUsed", 0);
        double cpi = JsonLite.getDouble(snap, "cpi", 1.0);
        boolean cl = JsonLite.getBoolean(snap, "chunkLoaderActive", false);

        drawFitString(canvas, Component.translatable("gui.tyc2.hub.status.bank", bankBal).getString(), 182, 169, 55, 0xFFFFE066);
        drawFitString(canvas, Component.translatable("gui.tyc2.hub.status.inbound", inUsed).getString(), 182, 178, 55, 0xFF66C2FF);
        drawFitString(canvas, Component.translatable("gui.tyc2.hub.status.outbound", outUsed).getString(), 182, 187, 55, 0xFFFFB866);
        drawFitString(canvas, Component.translatable("gui.tyc2.hub.status.cpi", String.format("%.2f", cpi)).getString(), 182, 196, 55, 0xFF66FF99);
        drawFitString(
                canvas,
                Component.translatable(cl ? "gui.tyc2.hub.status.cl_on" : "gui.tyc2.hub.status.cl_off").getString(),
                182, 205, 55, cl ? 0xFF7BE8FF : 0xFF7A8B9A
        );
    }

    @Override
    protected boolean handlePanelClick(double pmx, double pmy, int button, boolean shift) {
        if (button != 0) {
            return false;
        }
        if (activeTab == 0) {
            List<Map<String, Object>> orders = filteredOrders();
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
                int rowY = 65 + i * 22;
                Map<String, Object> o = orders.get(start + i);
                String orderId = JsonLite.getString(o, "id", "");
                if (pmx >= 166 && pmx < 180 && pmy >= rowY + 3 && pmy < rowY + 17) {
                    Map<String, Object> args = viewArgs();
                    args.put("orderId", orderId);
                    sendAction("TOGGLE_ORDER_AUTO", args);
                    return true;
                }
                if (pmx >= 183 && pmx < 235 && pmy >= rowY + 3 && pmy < rowY + 17) {
                    boolean villageSells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
                    int chosenQty = shift ? 64 : QTY_PRESETS[qtyPresetIdx];
                    Map<String, Object> args = viewArgs();
                    args.put("orderId", orderId);
                    args.put("qty", chosenQty);
                    args.put("cashMode", cashMode);
                    sendAction(villageSells ? "BUY_ORDER" : "SELL_ORDER", args);
                    return true;
                }
            }
        } else if (activeTab == 1) {
            List<Object> slots = JsonLite.asArray(menu.snapshot().get("whSlots"));
            for (int idx = 0; idx < 36; idx++) {
                int row = idx / 12;
                int col = idx % 12;
                int sx = 16 + col * 18;
                int sy = 67 + row * 20;
                if (pmx >= sx && pmx < sx + 18 && pmy >= sy && pmy < sy + 18) {
                    int absSlot = -1;
                    if (slots != null && idx < slots.size()) {
                        Map<String, Object> sm = JsonLite.asObject(slots.get(idx));
                        if (sm != null) {
                            absSlot = JsonLite.getInt(sm, "slot", -1);
                        }
                    }
                    if (absSlot >= 0) {
                        Map<String, Object> args = viewArgs();
                        args.put("slot", absSlot);
                        args.put("shift", shift);
                        sendAction("WAREHOUSE_CLICK", args);
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

        // Warehouse slot hover tooltips — localized item name + Count (no internal slot index)
        if (activeTab == 1) {
            List<Object> slots = JsonLite.asArray(snap.get("whSlots"));
            for (int idx = 0; idx < 36; idx++) {
                int row = idx / 12;
                int col = idx % 12;
                int sx = 16 + col * 18;
                int sy = 67 + row * 20;
                if (pmx >= sx && pmx < sx + 18 && pmy >= sy && pmy < sy + 18) {
                    if (slots != null && idx < slots.size()) {
                        Map<String, Object> sm = JsonLite.asObject(slots.get(idx));
                        if (sm != null) {
                            String itemId = JsonLite.getString(sm, "itemId", "");
                            int count = JsonLite.getInt(sm, "count", 0);
                            if (!itemId.isEmpty() && count > 0) {
                                ItemStack st = TyC2Native.stack(itemId, 1);
                                Component nameComp = st.isEmpty() ? Component.literal(itemId) : st.getHoverName();
                                canvas.drawTooltip(List.of(
                                        nameComp,
                                        Component.translatable("gui.tyc2.hub.slot_tooltip", count)
                                ), mouseX, mouseY);
                            }
                        }
                    }
                    return;
                }
            }
        }

        // Expansion material hover tooltips
        if (activeTab == 3 && growthSubTab == 0 && JsonLite.asObject(snap.get("activeBuild")) == null) {
            Map<String, Object> desire = JsonLite.asObject(snap.get("expansionDesire"));
            if (desire != null) {
                Map<String, Object> req = JsonLite.asObject(desire.get("required"));
                Map<String, Object> del = JsonLite.asObject(desire.get("delivered"));
                for (MaterialCell cell : layoutExpansionMaterials(req)) {
                    if (pmx >= cell.x() && pmx < cell.x() + cell.hitW()
                            && pmy >= cell.y() && pmy < cell.y() + 16) {
                        int need = JsonLite.getInt(req, cell.itemId(), 0);
                        int have = del == null ? 0 : JsonLite.getInt(del, cell.itemId(), 0);
                        ItemStack st = TyC2Native.stack(cell.itemId(), 1);
                        Component nameComp = st.isEmpty() ? Component.literal(cell.itemId()) : st.getHoverName();
                        canvas.drawTooltip(List.of(
                                nameComp,
                                Component.translatable("gui.tyc2.hub.order_progress", have, need)
                        ), mouseX, mouseY);
                        return;
                    }
                }
            }
        }

        // Banner chunk loader icon tooltip
        if (pmx >= 220 && pmx < 236 && pmy >= 25 && pmy < 41) {
            boolean cl = JsonLite.getBoolean(snap, "chunkLoaderActive", false);
            canvas.drawTooltip(List.of(Component.translatable(
                    cl ? "gui.tyc2.hub.chunk_loader.active" : "gui.tyc2.hub.chunk_loader.inactive"
            )), mouseX, mouseY);
        }
    }

    private List<Map<String, Object>> filteredOrders() {
        List<Object> raw = JsonLite.asArray(menu.snapshot().get("orders"));
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Object obj : raw) {
            Map<String, Object> om = JsonLite.asObject(obj);
            if (om == null) {
                continue;
            }
            String side = JsonLite.getString(om, "side", "");
            if (orderFilter == 1 && !"VILLAGE_SELLS".equals(side)) {
                continue;
            }
            if (orderFilter == 2 && !"VILLAGE_BUYS".equals(side)) {
                continue;
            }
            out.add(om);
        }
        return out;
    }

    private Component orderFilterComponent() {
        return switch (orderFilter) {
            case 1 -> Component.translatable("gui.tyc2.hub.filter.sell");
            case 2 -> Component.translatable("gui.tyc2.hub.filter.buy");
            default -> Component.translatable("gui.tyc2.hub.filter.all");
        };
    }

    private Component qtyPresetComponent() {
        int val = QTY_PRESETS[qtyPresetIdx];
        if (val >= 9999) {
            return Component.translatable("gui.tyc2.hub.qty_preset", Component.translatable("gui.tyc2.hub.qty_all"));
        }
        return Component.translatable("gui.tyc2.hub.qty_preset", val);
    }

    private static List<Double> extractCpiHistory(Map<String, Object> snap) {
        List<Object> arr = JsonLite.asArray(snap.get("cpiHistory"));
        List<Double> out = new ArrayList<>();
        if (arr != null) {
            for (Object o : arr) {
                if (o instanceof Number n) {
                    out.add(n.doubleValue());
                }
            }
        }
        return out;
    }

    private static int seasonSpriteU(String season) {
        return switch (season) {
            case "SPRING" -> 62;
            case "SUMMER" -> 76;
            case "AUTUMN" -> 90;
            case "WINTER" -> 104;
            default -> -1;
        };
    }

    private record MaterialCell(String itemId, int x, int y, int textW, int hitW) {
    }

    private static List<MaterialCell> layoutExpansionMaterials(Map<String, Object> req) {
        if (req == null || req.isEmpty()) {
            return List.of();
        }
        int count = req.size();
        List<MaterialCell> cells = new ArrayList<>(count);
        if (count <= 6) {
            int step;
            int textW;
            if (count <= 4) {
                step = 56;
                textW = 36;
            } else if (count == 5) {
                step = 44;
                textW = 25;
            } else {
                step = Math.max(36, 224 / count);
                textW = Math.max(18, step - 19);
            }
            int idx = 0;
            for (String itemId : req.keySet()) {
                int x = 12 + idx * step;
                cells.add(new MaterialCell(itemId, x, 92, textW, Math.min(step - 1, 18 + textW)));
                idx++;
            }
        } else {
            int perRow = (count + 1) / 2;
            int step = Math.max(36, 224 / Math.min(6, perRow));
            int textW = Math.max(18, step - 19);
            int idx = 0;
            for (String itemId : req.keySet()) {
                int col = idx % perRow;
                int y = (idx < perRow) ? 88 : 100;
                int x = 12 + col * step;
                cells.add(new MaterialCell(itemId, x, y, textW, Math.min(step - 1, 18 + textW)));
                idx++;
            }
        }
        return cells;
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

    private Map<String, Object> viewArgs() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("zone", whZone);
        args.put("page", whPage);
        args.put("search", whSearch);
        return args;
    }

    private void requestSync() {
        sendAction("REQUEST_SYNC", viewArgs());
    }

    private void sendAction(String action, Map<String, Object> args) {
        String json = args.isEmpty() ? "{}" : JsonLite.toJson(new LinkedHashMap<>(args));
        TyC2Native.sendToServer(new TyC2ActionPayload("HUB", menu.blockPos(), action, json));
    }
}
