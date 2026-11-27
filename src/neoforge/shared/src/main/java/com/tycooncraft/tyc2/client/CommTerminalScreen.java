package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.CommTerminalMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.village.SupplyCycleClock;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Bespoke "Deep-Space Cyan/Teal Tactical PDA" remote management GUI for the handheld
 * Commerce Communication Terminal ({@code gui/comm_terminal.png}, 248x210).
 * 100% localized across {@code zh_cn} and {@code en_us} with zero vanilla grey buttons.
 */
public final class CommTerminalScreen extends TyC2ScreenBase<CommTerminalMenu> {

    private static final String GUI_TEX = TyC2Theme.PDA_TEX;
    private static final int RAIL_PER_PAGE = 8;
    private static final int ORDERS_PER_PAGE = 4;
    private static final int[] QTY_PRESETS = {1, 16, 64, 9999};

    private int railPage = 0;
    private int subTab = 0;       // 0=Orders, 1=Stock, 2=CPI, 3=Growth
    private int growthMode = 0;   // 0=Expansion, 1=Aid Construction
    private int orderFilter = 0;  // 0=All, 1=Sell, 2=Buy
    private int qtyPresetIdx = 1; // default 16
    private int orderPage = 0;
    private boolean stockOutbound = false;
    private int aidProfIdx = 0;
    private boolean aidLarge = false;
    private int lastSeenSyncRevision = -1;
    private int ticksSinceSync = 0;

    private StyledButton stockZoneBtn;
    private StyledButton remoteAutoBtn;
    private StyledButton remotePauseBtn;
    private StyledButton remoteAidSizeBtn;
    private StyledButton remoteAidStartBtn;

    private final List<TagTipRect> orderTagTips = new ArrayList<>();

    private record TagTipRect(int x, int y, int w, int h, String tooltipKey) {
        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

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
        lastSeenSyncRevision = menu.syncRevision();
        ticksSinceSync = 0;
        rebuildControls();
        sendAction("REQUEST_SYNC", Map.of());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int rev = menu.syncRevision();
        if (rev != lastSeenSyncRevision) {
            lastSeenSyncRevision = rev;
            ticksSinceSync = 0;
        } else {
            ticksSinceSync++;
            if (ticksSinceSync % 40 == 0) {
                sendAction("REQUEST_SYNC", Map.of());
            }
        }
    }

    private long liveRefreshRemaining(Map<String, Object> village) {
        int rev = menu.syncRevision();
        if (rev != lastSeenSyncRevision) {
            lastSeenSyncRevision = rev;
            ticksSinceSync = 0;
        }
        long base = JsonLite.getLong(village, "refreshRemainingTicks", SupplyCycleClock.TICKS_PER_WEEK);
        return Math.max(0L, base - ticksSinceSync);
    }

    private void rebuildControls() {
        clearWidgets();
        clearEditBoxes();
        stockZoneBtn = null;
        remoteAutoBtn = null;
        remotePauseBtn = null;
        remoteAidSizeBtn = null;
        remoteAidStartBtn = null;

        // Buttons 0, 1: Left rail scroll buttons (indices strictly preserved for live QA!)
        addStyledButton(
                leftPos + 10, topPos + 186, 28, 12,
                Component.translatable("gui.tyc2.prev"),
                TyC2Theme.ButtonStyle.PDA_ACTION,
                b -> railPage = Math.max(0, railPage - 1)
        );

        addStyledButton(
                leftPos + 42, topPos + 186, 28, 12,
                Component.translatable("gui.tyc2.next"),
                TyC2Theme.ButtonStyle.PDA_ACTION,
                b -> {
                    List<Object> list = JsonLite.asArray(menu.snapshot().get("boundVillages"));
                    int max = list == null ? 0 : Math.max(0, (list.size() - 1) / RAIL_PER_PAGE);
                    railPage = Math.min(max, railPage + 1);
                }
        );

        // Buttons 2, 3, 4, 5: Right panel sub-tabs (Orders, Stock, CPI, Growth) at y=43
        // Indices 2..5 stay visible & active at all times for live QA button index compatibility!
        String[] tabKeys = {
                "gui.tyc2.terminal.tab.orders",
                "gui.tyc2.terminal.tab.stock",
                "gui.tyc2.terminal.tab.cpi",
                "gui.tyc2.terminal.tab.growth"
        };
        int[] tabX = {78, 118, 158, 198};
        int[] tabW = {39, 39, 39, 40};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addStyledButton(
                    leftPos + tabX[i], topPos + 43, tabW[i], 12,
                    Component.translatable(tabKeys[i]),
                    TyC2Theme.ButtonStyle.PDA_TAB,
                    b -> {
                        subTab = idx;
                        rebuildControls();
                    }
            ).setSelected(subTab == idx);
        }

        // Button 6: Compact Unbind button at top-right of tactical header (zero overlap!)
        addStyledButton(
                leftPos + 205, topPos + 23, 32, 9,
                Component.translatable("gui.tyc2.terminal.unbind"),
                TyC2Theme.ButtonStyle.PDA_DANGER,
                b -> {
                    String sel = JsonLite.getString(menu.snapshot(), "selectedId", "");
                    if (!sel.isEmpty()) {
                        sendAction("UNBIND_VILLAGE", Map.of("villageId", sel));
                    }
                }
        );

        if (subTab == 0) {
            // Compact Orders filter + qty preset toolbar (y=57, h=11)
            addStyledButton(
                    leftPos + 78, topPos + 57, 36, 11,
                    orderFilterComponent(),
                    TyC2Theme.ButtonStyle.PDA_ACTION,
                    b -> {
                        orderFilter = (orderFilter + 1) % 3;
                        orderPage = 0;
                        b.setMessage(orderFilterComponent());
                    }
            );

            addStyledButton(
                    leftPos + 116, topPos + 57, 48, 11,
                    qtyPresetComponent(),
                    TyC2Theme.ButtonStyle.PDA_ACTION,
                    b -> {
                        qtyPresetIdx = (qtyPresetIdx + 1) % QTY_PRESETS.length;
                        b.setMessage(qtyPresetComponent());
                    }
            );

            // Order pagination footer (y=185, h=12)
            addStyledButton(
                    leftPos + 206, topPos + 185, 15, 12,
                    Component.translatable("gui.tyc2.prev"),
                    TyC2Theme.ButtonStyle.PDA_ACTION,
                    b -> orderPage = Math.max(0, orderPage - 1)
            );

            addStyledButton(
                    leftPos + 223, topPos + 185, 15, 12,
                    Component.translatable("gui.tyc2.next"),
                    TyC2Theme.ButtonStyle.PDA_ACTION,
                    b -> {
                        Map<String, Object> village = JsonLite.asObject(menu.snapshot().get("selectedVillage"));
                        int maxPage = Math.max(0, (filteredOrders(village).size() - 1) / ORDERS_PER_PAGE);
                        orderPage = Math.min(maxPage, orderPage + 1);
                    }
            );
        } else if (subTab == 1) {
            stockZoneBtn = addStyledButton(
                    leftPos + 79, topPos + 57, 64, 12,
                    Component.translatable(stockOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"),
                    stockOutbound ? TyC2Theme.ButtonStyle.PDA_BUY : TyC2Theme.ButtonStyle.PDA_PRIMARY,
                    b -> {
                        stockOutbound = !stockOutbound;
                        b.setMessage(Component.translatable(
                                stockOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
                        ));
                        stockZoneBtn.setStyle(stockOutbound ? TyC2Theme.ButtonStyle.PDA_BUY : TyC2Theme.ButtonStyle.PDA_PRIMARY);
                    }
            );
        } else if (subTab == 3) {
            // Sub-toggle inside Growth: Expansion (0) vs Aid Construction (1)
            addStyledButton(
                    leftPos + 78, topPos + 56, 79, 12,
                    Component.translatable("gui.tyc2.hub.tab.expansion"),
                    TyC2Theme.ButtonStyle.PDA_SUBTAB,
                    b -> {
                        growthMode = 0;
                        rebuildControls();
                    }
            ).setSelected(growthMode == 0);

            addStyledButton(
                    leftPos + 159, topPos + 56, 79, 12,
                    Component.translatable("gui.tyc2.hub.tab.aid"),
                    TyC2Theme.ButtonStyle.PDA_SUBTAB,
                    b -> {
                        growthMode = 1;
                        rebuildControls();
                        sendAction("REQUEST_SYNC", Map.of());
                    }
            ).setSelected(growthMode == 1);

            if (growthMode == 0) {
                addStyledButton(
                        leftPos + 78, topPos + 92, 160, 14,
                        Component.translatable(
                                "gui.tyc2.terminal.upgrade_short",
                                TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                        ),
                        TyC2Theme.ButtonStyle.PDA_PRIMARY,
                        b -> sendAction("UPGRADE_VILLAGE", Map.of())
                );

                addStyledButton(
                        leftPos + 78, topPos + 140, 68, 13,
                        Component.translatable(
                                "gui.tyc2.terminal.deliver_short",
                                TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                        ),
                        TyC2Theme.ButtonStyle.PDA_PRIMARY,
                        b -> sendAction("DELIVER_EXPANSION", Map.of())
                );

                remoteAutoBtn = addStyledButton(
                        leftPos + 148, topPos + 140, 44, 13,
                        Component.translatable("gui.tyc2.hub.auto_short.off"),
                        TyC2Theme.ButtonStyle.PDA_ACTION,
                        b -> sendAction("TOGGLE_EXPANSION_AUTO", Map.of())
                );

                remotePauseBtn = addStyledButton(
                        leftPos + 194, topPos + 140, 44, 13,
                        Component.translatable("gui.tyc2.hub.pause_btn"),
                        TyC2Theme.ButtonStyle.PDA_ACTION,
                        b -> sendAction("TOGGLE_PAUSE_EXPANSION", Map.of())
                );
            } else {
                addStyledButton(
                        leftPos + 78, topPos + 89, 13, 14,
                        Component.translatable("gui.tyc2.prev"),
                        TyC2Theme.ButtonStyle.PDA_ACTION,
                        b -> {
                            Map<String, Object> v = JsonLite.asObject(menu.snapshot().get("selectedVillage"));
                            int total = CooperationHubScreen.availableWorkplaces(v).size();
                            if (total > 0) {
                                aidProfIdx = (aidProfIdx - 1 + total) % total;
                            }
                        }
                );

                addStyledButton(
                        leftPos + 225, topPos + 89, 13, 14,
                        Component.translatable("gui.tyc2.next"),
                        TyC2Theme.ButtonStyle.PDA_ACTION,
                        b -> {
                            Map<String, Object> v = JsonLite.asObject(menu.snapshot().get("selectedVillage"));
                            int total = CooperationHubScreen.availableWorkplaces(v).size();
                            if (total > 0) {
                                aidProfIdx = (aidProfIdx + 1) % total;
                            }
                        }
                );

                remoteAidSizeBtn = addStyledButton(
                        leftPos + 78, topPos + 105, 79, 14,
                        Component.translatable(aidLarge ? "gui.tyc2.aid.size.large" : "gui.tyc2.aid.size.small"),
                        TyC2Theme.ButtonStyle.PDA_ACTION,
                        b -> aidLarge = !aidLarge
                );

                remoteAidStartBtn = addStyledButton(
                        leftPos + 159, topPos + 105, 79, 14,
                        Component.translatable(
                                "gui.tyc2.aid.start_cost_btn",
                                TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                        ),
                        TyC2Theme.ButtonStyle.PDA_PRIMARY,
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
                );
            }
        }
    }

    @Override
    protected void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY) {
        canvas.blit(GUI_TEX, 0, 0, 0, 0, imageWidth, imageHeight);

        Map<String, Object> snap = menu.snapshot();
        // Title cleanly right of baked emerald-cyan RF signal LED (8, 6, 14x12)
        TyC2Theme.drawFitString(canvas, title.getString(), 25, 8, 148, 0.65F, 0xFFD2F3FF);

        // Segmented Battery gauge: frame (178, 6, 58x12) -> fill (179, 7, 56x10)
        int eu = JsonLite.getInt(snap, "energyEu", 0);
        int maxEu = Math.max(1, JsonLite.getInt(snap, "maxEu", TriModePowerMath.TERMINAL_MAX_CAPACITY_EU));
        if (eu > 0) {
            int fillW = Math.max(1, Math.min(56, (int) Math.round(56.0 * eu / (double) maxEu)));
            int v = (eu * 5 < maxEu) ? TyC2Theme.PDA_BATTERY_RED_V : TyC2Theme.PDA_BATTERY_GREEN_V;
            canvas.blit(GUI_TEX, 179, 7, 0, v, fillW, 10);
        }

        int pmx = mouseX - leftPos;
        int pmy = mouseY - topPos;
        paintVillageRail(canvas, snap, pmx, pmy);

        Map<String, Object> village = JsonLite.asObject(snap.get("selectedVillage"));
        syncDynamicGrowthButtons(village);
        orderTagTips.clear();
        if (village == null) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.terminal.no_villages").getString(),
                    82, 76, 152, 48, 0.62F, TyC2Theme.MUTED_TEXT
            );
            return;
        }

        paintTacticalHeader(canvas, village);

        switch (subTab) {
            case 0 -> paintRemoteOrders(canvas, snap, village, pmx, pmy);
            case 1 -> paintRemoteStock(canvas, village);
            case 2 -> paintRemoteEconomy(canvas, village);
            case 3 -> paintRemoteGrowth(canvas, village, pmx, pmy);
            default -> {
            }
        }
    }

    private void paintTacticalHeader(ScreenCanvas canvas, Map<String, Object> village) {
        String vName = JsonLite.getString(village, "villageName", "");
        int level = JsonLite.getInt(village, "level", 1);
        long vFunds = JsonLite.getLong(village, "villageBalance", 0L);
        String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(village, "biome", "plains"));
        String season = JsonLite.getString(village, "season", "NONE");
        long refreshRem = liveRefreshRemaining(village);

        // Line 1: LvX VillageName (80..202) — clear of Unbind button at (205, 23, 32x9)
        String titleLine = Component.translatable("gui.tyc2.terminal.level_prefix", level, vName).getString();
        TyC2Theme.drawFitString(canvas, titleLine, 80, 23, 122, 0.62F, TyC2Theme.GOLD_TEXT);

        // Line 2: Funds + Biome 10x10 + optional Season 12x12 (hidden when NONE) + Calendar 12x12 + countdown
        int seasonU = TyC2Theme.pdaSeasonU(season);
        boolean hasSeason = seasonU >= 0;
        int biomeX = hasSeason ? 136 : 142;
        int calX = hasSeason ? 162 : 155;

        String fundsLine = Component.translatable(
                "gui.tyc2.hub.village_funds", TyC2Theme.formatCompactNumber(vFunds)
        ).getString();
        TyC2Theme.drawFitString(canvas, fundsLine, 80, 32, biomeX - 83, 0.52F, TyC2Theme.CYAN_TEXT);

        // Biome 10x10 emblem (pdaBiomeU, 236) — clean 1px margin above y=41 divider
        canvas.blit(GUI_TEX, biomeX, 30, TyC2Theme.pdaBiomeU(biomeKey), TyC2Theme.PDA_BIOME_V, 10, 10);

        // Season 12x12 emblem (pdaSeasonU, 216) — rendered only when active (no empty box when NONE)
        if (hasSeason) {
            canvas.blit(GUI_TEX, 148, 29, seasonU, TyC2Theme.PDA_SPRITE_V, 12, 12);
        }

        // Weekly Calendar 12x12 icon (160, 216) + compact countdown nudged cleanly above y=41 border
        canvas.blit(GUI_TEX, calX, 29, TyC2Theme.PDA_CALENDAR_U, TyC2Theme.PDA_SPRITE_V, 12, 12);
        String countdown = TyC2Theme.formatRestockCountdown(refreshRem);
        TyC2Theme.drawFitString(canvas, countdown, calX + 14, 32, Math.max(24, 237 - (calX + 14)), 0.54F, 0xFFFFDF7A);
    }

    private void syncDynamicGrowthButtons(Map<String, Object> village) {
        if (remoteAutoBtn != null) {
            Map<String, Object> desire = village == null ? null : JsonLite.asObject(village.get("expansionDesire"));
            boolean auto = desire != null && JsonLite.getBoolean(desire, "autoFulfill", false);
            remoteAutoBtn.setMessage(Component.translatable(
                    auto ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
            remoteAutoBtn.setSelected(auto);
        }
        if (remotePauseBtn != null) {
            boolean paused = village != null && JsonLite.getBoolean(village, "expansionPaused", false);
            remotePauseBtn.setMessage(Component.translatable(
                    paused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
            remotePauseBtn.setStyle(paused ? TyC2Theme.ButtonStyle.PDA_DANGER : TyC2Theme.ButtonStyle.PDA_ACTION);
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
                boolean limitReached = spec != null && JsonLite.getBoolean(spec, "aidLimitReached", false);
                remoteAidStartBtn.active = village != null && spec != null && !depop && queueSize < maxQueue && unlocked && !limitReached;
            }
        }
    }

    private void paintVillageRail(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        List<Object> bound = JsonLite.asArray(snap.get("boundVillages"));
        int count = bound == null ? 0 : bound.size();
        // Clean header inside rail top (y=24..32 above baked divider y=33)
        canvas.fill(12, 27, 15, 30, 0xFF36EED4);
        TyC2Theme.drawRightFitString(canvas, count + "/16", 68, 25, 48, 0.72F, 0xFF8BE8F5);

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
            int ry = 36 + i * 18;
            String vid = JsonLite.getString(vm, "id", "");
            String vname = JsonLite.getString(vm, "name", "");
            int lv = JsonLite.getInt(vm, "level", 1);
            String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(vm, "biome", "plains"));
            boolean loaded = JsonLite.getBoolean(vm, "loaded", false);
            boolean sel = vid.equals(selectedId);
            boolean hover = pmx >= 10 && pmx < 70 && pmy >= ry && pmy < ry + 16;

            canvas.fill(10, ry, 70, ry + 16, sel ? 0xFF153B56 : (hover ? 0xFF132C42 : 0xFF0D1B29));
            canvas.outline(10, ry, 60, 16, sel ? 0xFF46EEFF : (hover ? 0xFF2B6082 : 0xFF1F384F));
            if (sel) {
                canvas.fill(10, ry + 1, 12, ry + 15, 0xFF46EEFF);
            }

            // Online/remote LED dot (top) + biome color pip (bottom) stacked cleanly on left
            canvas.fill(13, ry + 3, 15, ry + 6, loaded ? 0xFF42F587 : 0xFF39C4FF);
            canvas.fill(13, ry + 10, 15, ry + 13, TyC2Theme.biomeAccentColor(biomeKey));

            // Village name strictly inside x=17..51 (34px) — guaranteed gap before right-aligned LvX pill!
            TyC2Theme.drawFitString(
                    canvas, vname, 17, ry + 4, 34, 0.52F,
                    sel ? 0xFFFFFFFF : 0xFFD2ECFA
            );

            // Right-aligned golden LvX badge pill (x=53..69, width 16px, height 10px)
            canvas.fill(53, ry + 3, 69, ry + 13, sel ? 0xFF30240D : 0xFF211A0C);
            canvas.outline(53, ry + 3, 16, 10, sel ? 0xFFE8B944 : 0xFF80601C);
            TyC2Theme.drawCenteredFitString(canvas, "Lv" + lv, 53, ry + 3, 16, 10, 0.56F, TyC2Theme.GOLD_TEXT);
        }
    }

    private void paintRemoteOrders(
            ScreenCanvas canvas,
            Map<String, Object> snap,
            Map<String, Object> village,
            int pmx,
            int pmy
    ) {
        // Right-aligned player active bank balance on toolbar line (166..238, y=59)
        long bankBal = JsonLite.getLong(snap, "activeBalance", 0L);
        String balText = Component.translatable(
                "gui.tyc2.hub.status.bank", TyC2Theme.formatCompactNumber(bankBal)
        ).getString();
        TyC2Theme.drawRightFitString(canvas, balText, 237, 59, 70, 0.56F, TyC2Theme.GOLD_TEXT);

        List<Map<String, Object>> orders = filteredOrders(village);
        int totalPages = Math.max(1, (orders.size() + ORDERS_PER_PAGE - 1) / ORDERS_PER_PAGE);
        if (orderPage >= totalPages) {
            orderPage = totalPages - 1;
        }

        // Footer bar: EU cost (80..158) + page indicator (160..202)
        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.terminal.cost_trade_short", TriModePowerMath.TERMINAL_COST_TRADE_EU).getString(),
                80, 187, 78, 0.58F, 0xFF72B4D8
        );
        TyC2Theme.drawCenteredFitString(
                canvas, (orderPage + 1) + "/" + totalPages, 160, 185, 44, 12, 0.65F, 0xFFAAD8F0
        );

        if (orders.isEmpty()) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.no_orders").getString(),
                    82, 92, 152, 48, 0.65F, TyC2Theme.MUTED_TEXT
            );
            return;
        }

        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
            Map<String, Object> o = orders.get(start + i);
            int rowY = 70 + i * 28; // 70, 98, 126, 154 (height 26px -> bottom 180)
            boolean sells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
            String itemId = JsonLite.getString(o, "itemId", "");
            int total = Math.max(1, JsonLite.getInt(o, "totalQty", 1));
            int doneQty = Math.max(0, JsonLite.getInt(o, "fulfilledQty", 0));
            long price = JsonLite.getLong(o, "unitPrice", 1L);
            boolean auto = JsonLite.getBoolean(o, "autoFulfill", false);
            boolean seasonal = JsonLite.getBoolean(o, "seasonalBonus", false);
            boolean biomeSpec = JsonLite.getBoolean(o, "biomeSpecialty", false);
            boolean done = doneQty >= total;

            int bg = done ? 0xFF101922 : (sells ? 0xFF0E2030 : 0xFF211A12);
            int topHilite = done ? 0xFF182430 : (sells ? 0xFF17334C : 0xFF33281B);
            int border = done ? 0xFF223445 : (sells ? 0xFF27547A : 0xFF785728);
            int stripe = done ? TyC2Theme.STRIPE_DONE : (sells ? TyC2Theme.STRIPE_SELL : TyC2Theme.STRIPE_BUY);

            canvas.fill(78, rowY, 238, rowY + 26, bg);
            canvas.fill(80, rowY + 1, 237, rowY + 2, topHilite);
            canvas.outline(78, rowY, 160, 26, border);
            canvas.fill(78, rowY + 1, 80, rowY + 25, stripe);

            ItemStack st = TyC2Native.stack(itemId, 1);
            canvas.drawItem(st, 82, rowY + 4);

            // Auto-Fulfill toggle ONLY rendered & clickable on Village Buy Orders!
            int contentRight = sells ? 199 : 181;
            if (!sells) {
                boolean hoverAuto = pmx >= 184 && pmx < 200 && pmy >= rowY + 4 && pmy < rowY + 19;
                int autoBg = auto ? (hoverAuto ? 0xFF227A48 : 0xFF185E37) : (hoverAuto ? 0xFF22364A : 0xFF152433);
                int autoBorder = auto ? 0xFF42F08C : 0xFF355470;
                canvas.fill(184, rowY + 4, 200, rowY + 19, autoBg);
                canvas.outline(184, rowY + 4, 16, 15, autoBorder);
                String tinyAuto = Component.translatable(
                        auto ? "gui.tyc2.hub.btn.auto_tiny.on" : "gui.tyc2.hub.btn.auto_tiny.off"
                ).getString();
                TyC2Theme.drawCenteredFitString(
                        canvas, tinyAuto, 184, rowY + 4, 16, 15, 0.50F,
                        auto ? 0xFFA2FFC4 : 0xFF88A4BD
                );
            }

            // Compact 8x8 Biome / Season tag gems right-aligned on Line 1 (with tooltips!)
            int tagRight = contentRight;
            if (seasonal) {
                int gx = tagRight - 10;
                if (gx > 120) {
                    canvas.fill(gx, rowY + 2, gx + 10, rowY + 12, 0xFF102A3A);
                    canvas.outline(gx, rowY + 2, 10, 10, 0xFF39B5DE);
                    canvas.blit(GUI_TEX, gx + 1, rowY + 3, TyC2Theme.PDA_TAG_SEASON_U, TyC2Theme.PDA_TAG_V, 8, 8);
                    orderTagTips.add(new TagTipRect(
                            gx, rowY + 2, 10, 10,
                            sells ? "gui.tyc2.order.tooltip.season_sell" : "gui.tyc2.order.tooltip.season_buy"
                    ));
                    tagRight = gx - 2;
                }
            }
            if (biomeSpec) {
                int gx = tagRight - 10;
                if (gx > 120) {
                    canvas.fill(gx, rowY + 2, gx + 10, rowY + 12, 0xFF112C1F);
                    canvas.outline(gx, rowY + 2, 10, 10, 0xFF3AC478);
                    canvas.blit(GUI_TEX, gx + 1, rowY + 3, TyC2Theme.PDA_TAG_BIOME_U, TyC2Theme.PDA_TAG_V, 8, 8);
                    orderTagTips.add(new TagTipRect(
                            gx, rowY + 2, 10, 10,
                            sells ? "gui.tyc2.order.tooltip.biome_sell" : "gui.tyc2.order.tooltip.biome_buy"
                    ));
                    tagRight = gx - 2;
                }
            }

            // Line 1: Localized item name
            String name = TyC2Native.localizedItemName(itemId);
            TyC2Theme.drawFitString(
                    canvas, name, 100, rowY + 3, Math.max(28, tagRight - 100), 0.56F,
                    done ? 0xFF708294 : TyC2Theme.ICE_WHITE
            );

            // Line 2: Progress on left + Unit Price right-aligned
            String progStr = Component.translatable("gui.tyc2.hub.order_progress", doneQty, total).getString();
            String priceStr = price + " IC";
            int priceW = Math.min(44, Math.max(28, (contentRight - 100) / 2));
            TyC2Theme.drawFitString(
                    canvas, progStr, 100, rowY + 14, Math.max(26, contentRight - 100 - priceW - 2), 0.54F,
                    done ? TyC2Theme.MINT_TEXT : 0xFFA4CEE8
            );
            TyC2Theme.drawRightFitString(
                    canvas, priceStr, contentRight, rowY + 14, priceW, 0.56F,
                    done ? 0xFF998F6A : TyC2Theme.GOLD_TEXT
            );

            // Bottom 2px progress bar (80..237, rowY+23..rowY+25)
            canvas.fill(80, rowY + 23, 237, rowY + 25, 0xFF08101A);
            double ratio = Math.min(1.0, Math.max(0.0, doneQty / (double) total));
            int barW = (int) Math.round(157.0 * ratio);
            if (barW > 0) {
                int barCol = done ? 0xFF3CE87A : (sells ? TyC2Theme.STRIPE_SELL : TyC2Theme.STRIPE_BUY);
                canvas.fill(80, rowY + 23, 80 + barW, rowY + 25, barCol);
            }

            // Remote Buy/Deliver CTA (202, rowY+4, 34x15)
            boolean hoverBtn = !done && pmx >= 202 && pmx < 236 && pmy >= rowY + 4 && pmy < rowY + 19;
            String actLbl = done
                    ? Component.translatable("gui.tyc2.confirm").getString()
                    : Component.translatable(sells ? "gui.tyc2.hub.buy" : "gui.tyc2.hub.deliver").getString();
            TyC2Theme.drawBevelButtonBox(
                    canvas, 202, rowY + 4, 34, 15,
                    sells ? TyC2Theme.ButtonStyle.PDA_PRIMARY : TyC2Theme.ButtonStyle.PDA_BUY,
                    !done, hoverBtn, actLbl
            );
        }
    }

    private void paintRemoteStock(ScreenCanvas canvas, Map<String, Object> village) {
        int inUsed = JsonLite.getInt(village, "inboundUsed", 0);
        int outUsed = JsonLite.getInt(village, "outboundUsed", 0);
        TyC2Theme.drawRightFitString(
                canvas,
                Component.translatable("gui.tyc2.terminal.stock_compact", inUsed, outUsed).getString(),
                237, 59, 90, 0.58F, 0xFF9BE8FF
        );

        Map<String, Object> summary = JsonLite.asObject(
                village.get(stockOutbound ? "outboundSummary" : "inboundSummary")
        );
        if (summary == null || summary.isEmpty()) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.zone.used", 0).getString(),
                    82, 92, 152, 48, 0.65F, TyC2Theme.MUTED_TEXT
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
            int x = 79 + col * 80;
            int y = 73 + row * 20;
            canvas.fill(x, y, x + 78, y + 18, 0xFF0E1D2C);
            canvas.outline(x, y, 78, 18, stockOutbound ? 0xFF5E4722 : 0xFF1F4260);

            ItemStack st = TyC2Native.stack(e.getKey(), 1);
            canvas.drawItem(st, x + 1, y + 1);
            String lbl = TyC2Native.localizedItemName(e.getKey());
            int cnt = e.getValue() instanceof Number n ? n.intValue() : 0;
            TyC2Theme.drawFitString(canvas, lbl, x + 19, y + 2, 57, 0.56F, TyC2Theme.ICE_WHITE);
            TyC2Theme.drawFitString(
                    canvas,
                    "×" + cnt,
                    x + 19, y + 10, 57, 0.58F, TyC2Theme.GOLD_TEXT
            );
            idx++;
        }
    }

    private void paintRemoteEconomy(ScreenCanvas canvas, Map<String, Object> village) {
        double cpi = JsonLite.getDouble(village, "cpi", 1.0);
        String infl = JsonLite.getString(village, "inflationState", "STABLE");
        long money = JsonLite.getLong(village, "moneySupply", 0L);
        long goods = JsonLite.getLong(village, "goodsCirculation", 0L);
        double liq = JsonLite.getDouble(village, "villageLiquidity", 1.0);
        String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(village, "biome", "plains"));
        String season = JsonLite.getString(village, "season", "NONE");
        int tier = JsonLite.getInt(village, "industrialTier", 0);
        long refreshRem = liveRefreshRemaining(village);

        // Framed macro-economy telemetry box (78..238, 57..109) — Money(M) & Goods(Q) on separate clean lines!
        canvas.fill(78, 57, 238, 109, 0xFF0A1624);
        canvas.outline(78, 57, 160, 52, 0xFF1E4463);

        int cpiColor = TyC2Theme.inflationColor(infl);
        String inflText = Component.translatable("gui.tyc2.economy." + infl.toLowerCase(Locale.ROOT)).getString();
        String cpiLine = Component.translatable("gui.tyc2.economy.cpi", String.format(Locale.ROOT, "%.2f", cpi)).getString()
                + " (" + inflText + ")";
        TyC2Theme.drawFitString(canvas, cpiLine, 82, 59, 152, 0.58F, cpiColor);
        canvas.fill(80, 68, 236, 69, 0xFF142D42);

        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.money_short", TyC2Theme.formatCompactNumber(money)).getString(),
                82, 70, 152, 0.60F, 0xFFE2EFFA
        );
        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.goods_short", TyC2Theme.formatCompactNumber(goods)).getString(),
                82, 80, 152, 0.60F, 0xFFE2EFFA
        );
        String envStrip = Component.translatable("biome.tyc2." + biomeKey).getString()
                + " · " + Component.translatable("season.tyc2." + season.toLowerCase(Locale.ROOT)).getString()
                + " · " + String.format(Locale.ROOT, "%.2fx", liq);
        TyC2Theme.drawFitString(canvas, envStrip, 82, 90, 152, 0.56F, 0xFF9BE8FF);
        TyC2Theme.drawFitString(canvas, Component.translatable("tier.tyc2." + tier).getString(), 82, 100, 152, 0.54F, 0xFFB8D4F0);

        // Chart title + restock countdown
        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.cpi_chart").getString(),
                80, 112, 80, 0.62F, 0xFFA8D4EC
        );
        TyC2Theme.drawRightFitString(
                canvas,
                Component.translatable("gui.tyc2.hub.refresh_label", TyC2Theme.formatRestockCountdown(refreshRem)).getString(),
                237, 112, 76, 0.56F, 0xFFFFDF7A
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
        CpiChartRenderer.drawChart(canvas, 78, 121, 160, 76, pts, true);
    }

    private void paintRemoteGrowth(ScreenCanvas canvas, Map<String, Object> village, int pmx, int pmy) {
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
        TyC2Theme.drawFitString(canvas, topLine, 80, 71, 156, 0.54F, bonusActive ? TyC2Theme.MINT_TEXT : TyC2Theme.GOLD_TEXT);

        String reqStr = level >= 10
                ? Component.translatable("gui.tyc2.hub.max_level").getString()
                : Component.translatable(
                        "gui.tyc2.hub.upgrade_req",
                        pop + "/" + reqPop,
                        TyC2Theme.formatCompactNumber(vol) + "/" + TyC2Theme.formatCompactNumber(reqVol),
                        TyC2Theme.formatCompactNumber(bal) + "/" + TyC2Theme.formatCompactNumber(reqBal)
                ).getString();
        TyC2Theme.drawFitString(canvas, reqStr, 80, 81, 156, 0.54F, 0xFFCAE8FF);

        // Expansion desire / active build framed card (78..238, 108..138)
        canvas.fill(78, 108, 238, 138, 0xFF0A1623);
        canvas.outline(78, 108, 160, 30, 0xFF1E3E5A);

        Map<String, Object> build = JsonLite.asObject(village.get("activeBuild"));
        Map<String, Object> desire = JsonLite.asObject(village.get("expansionDesire"));
        if (build != null) {
            String pType = JsonLite.getString(build, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
            int pct = Math.max(0, Math.min(100, JsonLite.getInt(build, "progressPct", 0)));
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.expansion.building",
                            Component.translatable("project.tyc2." + pType), pct).getString(),
                    81, 111, 154, 0.56F, 0xFF64E8FF
            );
            canvas.fill(81, 123, 235, 132, 0xFF07101A);
            canvas.outline(81, 123, 154, 9, 0xFF285578);
            int fillW = Math.max(0, (int) Math.round(152.0 * pct / 100.0));
            if (fillW > 0) {
                canvas.fill(82, 124, 82 + fillW, 131, 0xFF34DE92);
            }
        } else if (desire != null) {
            String pType = JsonLite.getString(desire, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
            long budget = JsonLite.getLong(desire, "budget", 0L);
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable("project.tyc2." + pType).getString() + " (" + budget + " IC)",
                    81, 110, 154, 0.56F, 0xFFFFE066
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
                TyC2Theme.drawFitString(
                        canvas, line1.toString(), 81, 120, 154, 0.46F,
                        line1Met ? TyC2Theme.MINT_TEXT : 0xFFFFCC66
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
                    TyC2Theme.drawFitString(
                            canvas, line2.toString(), 81, 129, 154, 0.46F,
                            line2Met ? TyC2Theme.MINT_TEXT : 0xFFFFCC66
                    );
                }
            }
        } else {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.expansion.none").getString(),
                    81, 110, 154, 24, 0.60F, TyC2Theme.MUTED_TEXT
            );
        }

        // Nearby Logistics box (78..238, 156..196)
        canvas.fill(78, 156, 238, 196, 0xFF0A1623);
        canvas.outline(78, 156, 160, 40, 0xFF1D3B55);

        List<Object> lc2 = JsonLite.asArray(village.get("lc2Stations"));
        if (lc2 == null || lc2.isEmpty()) {
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable(
                            "gui.tyc2.terminal.lc2_nearby",
                            Component.translatable("gui.tyc2.terminal.no_lc2")
                    ).getString(),
                    82, 160, 152, 0.56F, 0xFF7B95AD
            );
        } else {
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable(
                            "gui.tyc2.terminal.lc2_nearby",
                            Lc2SoftBridge.formatStationLabel(lc2.get(0).toString())
                    ).getString(),
                    82, 160, 152, 0.56F, 0xFF64E8FF
            );
            int y = 171;
            for (int i = 1; i < Math.min(3, lc2.size()); i++) {
                TyC2Theme.drawFitString(
                        canvas,
                        Lc2SoftBridge.formatStationLabel(lc2.get(i).toString()),
                        82, y, 152, 0.56F, 0xFF64E8FF
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
        int qtyPct = JsonLite.getInt(village, "qtyBonusPct", 0);
        int thresholdPct = JsonLite.getInt(village, "lowUnempThresholdPct", 25);

        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.hub.unemployment", unempPct, unempCount, workforce).getString(),
                80, 70, 156, 0.56F, bonusActive ? TyC2Theme.MINT_TEXT : 0xFFE0ECF8
        );
        String bonusLine = bonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", qtyPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", thresholdPct).getString();
        TyC2Theme.drawFitString(canvas, bonusLine, 80, 79, 156, 0.50F, bonusActive ? 0xFF56EC8B : TyC2Theme.MUTED_TEXT);

        // Profession picker display (92..224, 89..103)
        canvas.fill(92, 89, 224, 103, 0xFF0D1E2E);
        canvas.outline(92, 89, 132, 14, 0xFF285B80);
        Map<String, Object> spec = selectedWorkplace(village);
        if (spec != null) {
            String wsBlockId = JsonLite.getString(spec, "workstationBlockId", "");
            ItemStack wsStack = TyC2Native.stack(wsBlockId, 1);
            if (!wsStack.isEmpty()) {
                canvas.drawItem(wsStack, 94, 88);
            }
            String profLabel = CooperationHubScreen.localizedProfessionName(
                    JsonLite.getString(spec, "archetype", "GENERIC"),
                    JsonLite.getString(spec, "professionId", ""),
                    JsonLite.getString(spec, "displayNameFallback", "")
            );
            TyC2Theme.drawFitString(canvas, profLabel, 112, 92, 108, 0.58F, TyC2Theme.ICE_WHITE);

            long cost = aidLarge
                    ? JsonLite.getLong(spec, "costLargeIc", 0L)
                    : JsonLite.getLong(spec, "costSmallIc", 0L);
            boolean unlocked = aidLarge
                    ? JsonLite.getBoolean(spec, "largeUnlocked", false)
                    : JsonLite.getBoolean(spec, "smallUnlocked", true);
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.aid.cost", cost).getString(),
                    80, 122, 156, 0.56F, unlocked ? TyC2Theme.GOLD_TEXT : 0xFFFF7766
            );
        }

        double gatherMult = JsonLite.getDouble(village, "gatherSpeedMult", 1.0);
        List<Map<String, Object>> queue = CooperationHubScreen.aidProjects(village);
        int maxQueue = Math.max(1, JsonLite.getInt(village, "maxAidQueue", 3));
        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.aid.gather_rate", String.format(Locale.ROOT, "%.2f", gatherMult), unempCount).getString(),
                80, 131, 98, 0.50F, TyC2Theme.CYAN_TEXT
        );
        TyC2Theme.drawRightFitString(
                canvas,
                Component.translatable("gui.tyc2.aid.queue_header", queue.size(), maxQueue).getString(),
                236, 131, 56, 0.52F, TyC2Theme.GOLD_TEXT
        );

        if (queue.isEmpty()) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    82, 148, 152, 36, 0.62F, TyC2Theme.MUTED_TEXT
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

            int bg = "BUILDING".equals(status) ? 0xFF102838
                    : ("NO_SITE".equals(status) ? 0xFF2B1C16 : 0xFF0E1D2C);
            int border = "BUILDING".equals(status) ? 0xFF4BE2FF
                    : ("NO_SITE".equals(status) ? 0xFFFF8855 : 0xFF28557A);
            canvas.fill(78, ry, 238, ry + 17, bg);
            canvas.outline(78, ry, 160, 17, border);

            int barMaxW = canCancel ? 112 : 158;
            int barW = (int) Math.round(barMaxW * pct / 100.0);
            if (barW > 0) {
                int barColor = "BUILDING".equals(status) ? 0xFF39D98A : 0xFF4CA8FF;
                canvas.fill(79, ry + 15, 79 + barW, ry + 16, barColor);
            }

            int textMaxW = canCancel ? 108 : 152;
            TyC2Theme.drawFitString(canvas, profName + " [" + sizeShort + "]", 81, ry + 1, textMaxW, 0.54F, TyC2Theme.ICE_WHITE);
            TyC2Theme.drawFitString(
                    canvas,
                    CooperationHubScreen.aidStatusText(status, pct),
                    81, ry + 8, textMaxW, 0.52F,
                    "NO_SITE".equals(status) ? 0xFFFFAA66 : ("BUILDING".equals(status) ? TyC2Theme.MINT_TEXT : 0xFFFFDF7F)
            );

            if (canCancel) {
                boolean hoverCancel = pmx >= 193 && pmx < 236 && pmy >= ry + 2 && pmy < ry + 14;
                TyC2Theme.drawBevelButtonBox(
                        canvas, 193, ry + 2, 43, 12,
                        TyC2Theme.ButtonStyle.PDA_DANGER, true, hoverCancel,
                        Component.translatable("gui.tyc2.aid.cancel_btn").getString()
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
        if (bound != null && pmx >= 10 && pmx < 70 && pmy >= 36 && pmy < 180) {
            int row = (int) ((pmy - 36) / 18);
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
            List<Map<String, Object>> orders = filteredOrders(village);
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
                int rowY = 70 + i * 28;
                Map<String, Object> o = orders.get(start + i);
                String orderId = JsonLite.getString(o, "id", "");
                boolean sells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
                int total = Math.max(1, JsonLite.getInt(o, "totalQty", 1));
                int doneQty = Math.max(0, JsonLite.getInt(o, "fulfilledQty", 0));
                boolean done = doneQty >= total;

                // Auto-Fulfill toggle ONLY active on Village Buy Orders!
                if (!sells && pmx >= 184 && pmx < 200 && pmy >= rowY + 4 && pmy < rowY + 19) {
                    sendAction("TOGGLE_ORDER_AUTO", Map.of("orderId", orderId));
                    return true;
                }
                if (!done && pmx >= 202 && pmx < 236 && pmy >= rowY + 4 && pmy < rowY + 19) {
                    int chosenQty = shift ? 64 : QTY_PRESETS[qtyPresetIdx];
                    sendAction(sells ? "BUY_ORDER" : "SELL_ORDER", Map.of(
                            "orderId", orderId,
                            "qty", chosenQty
                    ));
                    return true;
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
                        && pmx >= 193 && pmx < 236 && pmy >= ry + 2 && pmy < ry + 14) {
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

        // 1. Battery gauge tooltip
        if (pmx >= 178 && pmx < 239 && pmy >= 6 && pmy < 18) {
            int eu = JsonLite.getInt(snap, "energyEu", 0);
            int maxEu = JsonLite.getInt(snap, "maxEu", TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
            canvas.drawTooltip(
                    List.of(Component.translatable("gui.tyc2.terminal.battery", eu, maxEu)),
                    mouseX, mouseY
            );
            return;
        }

        Map<String, Object> village = JsonLite.asObject(snap.get("selectedVillage"));
        if (village != null) {
            String seasonRaw = JsonLite.getString(village, "season", "NONE");
            boolean hasSeason = TyC2Theme.pdaSeasonU(seasonRaw) >= 0;
            int biomeX = hasSeason ? 136 : 142;
            int calX = hasSeason ? 162 : 155;

            // 2. Tactical header Biome tooltip
            if (pmx >= biomeX && pmx < biomeX + 10 && pmy >= 29 && pmy < 41) {
                String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(village, "biome", "plains"));
                canvas.drawTooltip(List.of(
                        Component.translatable("gui.tyc2.hub.biome_tooltip", Component.translatable("biome.tyc2." + biomeKey))
                                .withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.tyc2.economy.biome_specialty").withStyle(ChatFormatting.GREEN)
                ), mouseX, mouseY);
                return;
            }
            // 3. Tactical header Season tooltip (only when active)
            if (hasSeason && pmx >= 148 && pmx < 160 && pmy >= 29 && pmy < 41) {
                canvas.drawTooltip(List.of(Component.translatable(
                        "gui.tyc2.hub.season_tooltip",
                        Component.translatable("season.tyc2." + seasonRaw.toLowerCase(Locale.ROOT))
                ).withStyle(ChatFormatting.AQUA)), mouseX, mouseY);
                return;
            }
            // 4. Tactical header Weekly Calendar & Countdown tooltip
            if (pmx >= calX && pmx < 238 && pmy >= 29 && pmy < 41) {
                long refreshRem = liveRefreshRemaining(village);
                canvas.drawTooltip(List.of(Component.translatable(
                        "gui.tyc2.hub.refresh_tooltip", TyC2Theme.formatRestockCountdown(refreshRem)
                ).withStyle(ChatFormatting.GOLD)), mouseX, mouseY);
                return;
            }
        }

        // 5. Remote Orders tag gem, auto-fulfill & item tooltips
        if (subTab == 0 && village != null) {
            for (TagTipRect rect : orderTagTips) {
                if (rect.contains(pmx, pmy)) {
                    canvas.drawTooltip(List.of(Component.translatable(rect.tooltipKey())), mouseX, mouseY);
                    return;
                }
            }
            List<Map<String, Object>> orders = filteredOrders(village);
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
                int rowY = 70 + i * 28;
                Map<String, Object> o = orders.get(start + i);
                boolean sells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
                if (!sells && pmx >= 184 && pmx < 200 && pmy >= rowY + 4 && pmy < rowY + 19) {
                    boolean auto = JsonLite.getBoolean(o, "autoFulfill", false);
                    canvas.drawTooltip(List.of(Component.translatable(
                            auto ? "gui.tyc2.hub.auto_fulfill.on" : "gui.tyc2.hub.auto_fulfill.off"
                    )), mouseX, mouseY);
                    return;
                }
                if (pmx >= 82 && pmx < 98 && pmy >= rowY + 4 && pmy < rowY + 20) {
                    String itemId = JsonLite.getString(o, "itemId", "");
                    String ench = JsonLite.getString(o, "enchantSpec", "");
                    ItemStack st = TyC2Native.stack(itemId, 1);
                    List<Component> tip = new ArrayList<>(2);
                    tip.add(st.isEmpty() ? Component.literal(itemId) : st.getHoverName());
                    if (!ench.isBlank()) {
                        tip.add(Component.literal(ench).withStyle(ChatFormatting.AQUA));
                    }
                    canvas.drawTooltip(tip, mouseX, mouseY);
                    return;
                }
            }
        }

        // 6. Remote Aid queue & cap tooltips
        if (subTab == 3 && growthMode == 1 && village != null) {
            Map<String, Object> spec = selectedWorkplace(village);
            if (spec != null && JsonLite.getBoolean(spec, "aidLimitReached", false)
                    && pmx >= 159 && pmx < 238 && pmy >= 105 && pmy < 119) {
                int aidMax = JsonLite.getInt(spec, "aidMax", WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION);
                canvas.drawTooltip(
                        List.of(Component.translatable("status.tyc2.aid.profession_limit", aidMax)
                                .withStyle(ChatFormatting.RED)),
                        mouseX, mouseY
                );
                return;
            }
            List<Map<String, Object>> queue = CooperationHubScreen.aidProjects(village);
            int shown = Math.min(3, queue.size());
            for (int i = 0; i < shown; i++) {
                int ry = 141 + i * 18;
                if (pmx >= 78 && pmx < 193 && pmy >= ry && pmy < ry + 17) {
                    List<Component> tip = CooperationHubScreen.buildAidMaterialsTooltip(queue.get(i));
                    if (!tip.isEmpty()) {
                        canvas.drawTooltip(tip, mouseX, mouseY);
                    }
                    return;
                }
            }
        }
    }

    private List<Map<String, Object>> filteredOrders(Map<String, Object> village) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (village == null) {
            return out;
        }
        List<Object> raw = JsonLite.asArray(village.get("orders"));
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

    private void sendAction(String action, Map<String, Object> args) {
        String json = args.isEmpty() ? "{}" : JsonLite.toJson(new LinkedHashMap<>(args));
        TyC2Native.sendToServer(new TyC2ActionPayload("TERMINAL", BlockPos.ZERO, action, json));
    }
}
