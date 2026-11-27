package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.menu.CooperationHubMenu;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Bespoke "Gilded Bronze &amp; Deep Ledger" 4-tab GUI screen for the Village Cooperation Hub
 * ({@code gui/cooperation_hub.png}, 248x222). Uses 100% custom beveled controls, biome/season/weekly
 * calendar indicators with hover tooltips, and full {@code zh_cn}/{@code en_us} localization.
 */
public final class CooperationHubScreen extends TyC2ScreenBase<CooperationHubMenu> {

    private static final String GUI_TEX = TyC2Theme.HUB_TEX;
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

    private int growthSubTab = 0;    // 0=Expansion, 1=Aid Construction, 2=Settings & Logistics
    private boolean terminateArmed = false;
    private int aidProfIdx = 0;
    private boolean aidLarge = false;
    private int lastSeenSyncRevision = -1;
    private int ticksSinceSync = 0;

    private EditBox searchInput;
    private EditBox renameInput;
    private StyledButton payModeBtn;
    private StyledButton whZoneBtn;
    private StyledButton expAutoBtn;
    private StyledButton expPauseBtn;
    private StyledButton aidCashBtn;
    private StyledButton aidSizeBtn;
    private StyledButton aidStartBtn;

    // Dynamic hitboxes computed during paint for crisp hover tooltips
    private int bannerBiomeX = 138;
    private int bannerBiomeW = 51;
    private final List<TagTipRect> orderTagTips = new ArrayList<>();

    private record TagTipRect(int x, int y, int w, int h, String tooltipKey) {
        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

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
        lastSeenSyncRevision = menu.syncRevision();
        ticksSinceSync = 0;
        rebuildControls();
        requestSync();
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
                requestSync();
            }
        }
    }

    private long liveRefreshRemaining(Map<String, Object> snap) {
        int rev = menu.syncRevision();
        if (rev != lastSeenSyncRevision) {
            lastSeenSyncRevision = rev;
            ticksSinceSync = 0;
        }
        long base = JsonLite.getLong(snap, "refreshRemainingTicks", SupplyCycleClock.TICKS_PER_WEEK);
        return Math.max(0L, base - ticksSinceSync);
    }

    private void rebuildControls() {
        clearWidgets();
        clearEditBoxes();
        searchInput = null;
        renameInput = null;
        payModeBtn = null;
        whZoneBtn = null;
        expAutoBtn = null;
        expPauseBtn = null;
        aidCashBtn = null;
        aidSizeBtn = null;
        aidStartBtn = null;
        terminateArmed = false;

        // Top 4 Tabs seated precisely inside (8, 4, 232x16) matching baked separators at x=65, 123, 181.
        // Indices 0..3 stay visible & active at all times for live QA button index compatibility.
        String[] tabKeys = {
                "gui.tyc2.hub.tab.orders",
                "gui.tyc2.hub.tab.warehouse",
                "gui.tyc2.hub.tab.economy",
                "gui.tyc2.hub.tab.growth"
        };
        int[] tabX = {9, 66, 124, 182};
        int[] tabW = {56, 57, 57, 57};
        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            addStyledButton(
                    leftPos + tabX[i], topPos + 5, tabW[i], 14,
                    Component.translatable(tabKeys[i]),
                    TyC2Theme.ButtonStyle.HUB_TAB,
                    b -> {
                        activeTab = tabIdx;
                        rebuildControls();
                        requestSync();
                    }
            ).setSelected(activeTab == tabIdx);
        }

        if (activeTab == 0) {
            // Orders compact toolbar inside viewport top (10..236, y=50)
            addStyledButton(
                    leftPos + 10, topPos + 50, 38, 12,
                    orderFilterComponent(),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> {
                        orderFilter = (orderFilter + 1) % 3;
                        orderPage = 0;
                        b.setMessage(orderFilterComponent());
                    }
            );

            payModeBtn = addStyledButton(
                    leftPos + 50, topPos + 50, 48, 12,
                    payModeShortComponent(),
                    cashMode ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_PRIMARY,
                    b -> {
                        cashMode = !cashMode;
                        b.setMessage(payModeShortComponent());
                        payModeBtn.setStyle(cashMode ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_PRIMARY);
                    }
            );

            addStyledButton(
                    leftPos + 100, topPos + 50, 50, 12,
                    qtyPresetComponent(),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> {
                        qtyPresetIdx = (qtyPresetIdx + 1) % QTY_PRESETS.length;
                        b.setMessage(qtyPresetComponent());
                    }
            );

            addStyledButton(
                    leftPos + 206, topPos + 50, 14, 12,
                    Component.translatable("gui.tyc2.prev"),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> orderPage = Math.max(0, orderPage - 1)
            );

            addStyledButton(
                    leftPos + 222, topPos + 50, 14, 12,
                    Component.translatable("gui.tyc2.next"),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> {
                        int maxPage = Math.max(0, (filteredOrders().size() - 1) / ORDERS_PER_PAGE);
                        orderPage = Math.min(maxPage, orderPage + 1);
                    }
            );

        } else if (activeTab == 1) {
            // Warehouse toolbar (10..236, y=50)
            boolean isOut = "OUTBOUND".equals(whZone);
            whZoneBtn = addStyledButton(
                    leftPos + 10, topPos + 50, 54, 12,
                    Component.translatable(isOut ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"),
                    isOut ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_PRIMARY,
                    b -> {
                        whZone = "OUTBOUND".equals(whZone) ? "INBOUND" : "OUTBOUND";
                        whPage = 0;
                        boolean out = "OUTBOUND".equals(whZone);
                        b.setMessage(Component.translatable(
                                out ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
                        ));
                        whZoneBtn.setStyle(out ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_PRIMARY);
                        requestSync();
                    }
            );

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

            addStyledButton(
                    leftPos + 142, topPos + 50, 32, 12,
                    Component.translatable("gui.tyc2.hub.btn.put"),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> sendAction("WAREHOUSE_DEPOSIT_INV", viewArgs())
            );

            addStyledButton(
                    leftPos + 178, topPos + 50, 14, 12,
                    Component.translatable("gui.tyc2.prev"),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> {
                        if (whPage > 0) {
                            whPage--;
                            requestSync();
                        }
                    }
            );

            addStyledButton(
                    leftPos + 222, topPos + 50, 14, 12,
                    Component.translatable("gui.tyc2.next"),
                    TyC2Theme.ButtonStyle.HUB_ACTION,
                    b -> {
                        int total = Math.max(1, JsonLite.getInt(menu.snapshot(), "whTotalPages", 1));
                        if (whPage + 1 < total) {
                            whPage++;
                            requestSync();
                        }
                    }
            );

        } else if (activeTab == 3) {
            // Sub-toggle: 0=Expansion, 1=Aid, 2=Settings
            addStyledButton(
                    leftPos + 10, topPos + 49, 44, 12,
                    Component.translatable("gui.tyc2.hub.tab.expansion"),
                    TyC2Theme.ButtonStyle.HUB_SUBTAB,
                    b -> {
                        growthSubTab = 0;
                        rebuildControls();
                    }
            ).setSelected(growthSubTab == 0);

            addStyledButton(
                    leftPos + 56, topPos + 49, 44, 12,
                    Component.translatable("gui.tyc2.hub.tab.aid"),
                    TyC2Theme.ButtonStyle.HUB_SUBTAB,
                    b -> {
                        growthSubTab = 1;
                        rebuildControls();
                        requestSync();
                    }
            ).setSelected(growthSubTab == 1);

            addStyledButton(
                    leftPos + 102, topPos + 49, 44, 12,
                    Component.translatable("gui.tyc2.hub.tab.settings"),
                    TyC2Theme.ButtonStyle.HUB_SUBTAB,
                    b -> {
                        growthSubTab = 2;
                        rebuildControls();
                    }
            ).setSelected(growthSubTab == 2);

            if (growthSubTab == 0) {
                addStyledButton(
                        leftPos + 152, topPos + 49, 84, 12,
                        Component.translatable("gui.tyc2.hub.upgrade_btn"),
                        TyC2Theme.ButtonStyle.HUB_PRIMARY,
                        b -> sendAction("UPGRADE_VILLAGE", viewArgs())
                );

                addStyledButton(
                        leftPos + 10, topPos + 118, 86, 13,
                        Component.translatable("gui.tyc2.expansion.deliver"),
                        TyC2Theme.ButtonStyle.HUB_PRIMARY,
                        b -> sendAction("DELIVER_EXPANSION", viewArgs())
                );

                expAutoBtn = addStyledButton(
                        leftPos + 98, topPos + 118, 62, 13,
                        Component.translatable("gui.tyc2.hub.auto_short.off"),
                        TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> sendAction("TOGGLE_EXPANSION_AUTO", viewArgs())
                );

                expPauseBtn = addStyledButton(
                        leftPos + 162, topPos + 118, 74, 13,
                        Component.translatable("gui.tyc2.hub.pause_btn"),
                        TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> sendAction("TOGGLE_PAUSE_EXPANSION", viewArgs())
                );
            } else if (growthSubTab == 1) {
                aidCashBtn = addStyledButton(
                        leftPos + 152, topPos + 49, 84, 12,
                        Component.translatable(cashMode ? "gui.tyc2.hub.cash_mode" : "gui.tyc2.hub.bank_mode"),
                        cashMode ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> {
                            cashMode = !cashMode;
                            b.setMessage(Component.translatable(
                                    cashMode ? "gui.tyc2.hub.cash_mode" : "gui.tyc2.hub.bank_mode"
                            ));
                            aidCashBtn.setStyle(cashMode ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_ACTION);
                        }
                );

                addStyledButton(
                        leftPos + 10, topPos + 63, 13, 14,
                        Component.translatable("gui.tyc2.prev"),
                        TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> {
                            int total = availableWorkplaces(menu.snapshot()).size();
                            if (total > 0) {
                                aidProfIdx = (aidProfIdx - 1 + total) % total;
                            }
                        }
                );

                addStyledButton(
                        leftPos + 107, topPos + 63, 13, 14,
                        Component.translatable("gui.tyc2.next"),
                        TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> {
                            int total = availableWorkplaces(menu.snapshot()).size();
                            if (total > 0) {
                                aidProfIdx = (aidProfIdx + 1) % total;
                            }
                        }
                );

                aidSizeBtn = addStyledButton(
                        leftPos + 122, topPos + 63, 62, 14,
                        Component.translatable(aidLarge ? "gui.tyc2.aid.size.large" : "gui.tyc2.aid.size.small"),
                        TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> aidLarge = !aidLarge
                );

                aidStartBtn = addStyledButton(
                        leftPos + 186, topPos + 63, 50, 14,
                        Component.translatable("gui.tyc2.aid.start_btn"),
                        TyC2Theme.ButtonStyle.HUB_PRIMARY,
                        b -> {
                            Map<String, Object> spec = selectedWorkplace(menu.snapshot());
                            if (spec != null) {
                                Map<String, Object> args = viewArgs();
                                args.put("professionId", JsonLite.getString(spec, "professionId", ""));
                                args.put("size", aidLarge ? "large" : "small");
                                args.put("cashMode", cashMode);
                                sendAction("START_AID", args);
                            }
                        }
                );
            } else {
                renameInput = addTrackedEditBox(new EditBox(
                        font, leftPos + 10, topPos + 66, 112, 12,
                        Component.translatable("gui.tyc2.hub.rename_placeholder")
                ));
                renameInput.setMaxLength(32);
                renameInput.setValue(JsonLite.getString(menu.snapshot(), "villageName", ""));
                addRenderableWidget(renameInput);

                addStyledButton(
                        leftPos + 125, topPos + 66, 46, 12,
                        Component.translatable("gui.tyc2.save"),
                        TyC2Theme.ButtonStyle.HUB_PRIMARY,
                        b -> {
                            String val = renameInput.getValue().trim();
                            if (!val.isEmpty()) {
                                Map<String, Object> args = viewArgs();
                                args.put("name", val);
                                sendAction("RENAME_VILLAGE", args);
                            }
                        }
                );

                addStyledButton(
                        leftPos + 10, topPos + 116, 108, 14,
                        Component.translatable("gui.tyc2.hub.relocate"),
                        TyC2Theme.ButtonStyle.HUB_ACTION,
                        b -> sendAction("RELOCATE_HUB", viewArgs())
                );

                addStyledButton(
                        leftPos + 122, topPos + 116, 114, 14,
                        Component.translatable("gui.tyc2.hub.terminate"),
                        TyC2Theme.ButtonStyle.HUB_DANGER,
                        b -> {
                            if (!terminateArmed) {
                                terminateArmed = true;
                                b.setMessage(Component.translatable("gui.tyc2.hub.terminate_confirm"));
                            } else {
                                terminateArmed = false;
                                sendAction("TERMINATE_PARTNERSHIP", viewArgs());
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
        syncDynamicGrowthButtons(snap);
        paintBanner(canvas, snap);

        int pmx = mouseX - leftPos;
        int pmy = mouseY - topPos;
        orderTagTips.clear();
        switch (activeTab) {
            case 0 -> paintOrdersTab(canvas, snap, pmx, pmy);
            case 1 -> paintWarehouseTab(canvas, snap, pmx, pmy);
            case 2 -> paintEconomyTab(canvas, snap);
            case 3 -> paintGrowthSettingsTab(canvas, snap, pmx, pmy);
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
            expAutoBtn.setSelected(auto);
        }
        if (expPauseBtn != null) {
            boolean paused = JsonLite.getBoolean(snap, "expansionPaused", false);
            expPauseBtn.setMessage(Component.translatable(
                    paused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
            expPauseBtn.setStyle(paused ? TyC2Theme.ButtonStyle.HUB_DANGER : TyC2Theme.ButtonStyle.HUB_ACTION);
        }
        if (aidCashBtn != null) {
            aidCashBtn.setMessage(Component.translatable(
                    cashMode ? "gui.tyc2.hub.cash_mode" : "gui.tyc2.hub.bank_mode"
            ));
            aidCashBtn.setStyle(cashMode ? TyC2Theme.ButtonStyle.HUB_BUY : TyC2Theme.ButtonStyle.HUB_ACTION);
        }
        if (aidSizeBtn != null || aidStartBtn != null) {
            Map<String, Object> spec = selectedWorkplace(snap);
            boolean largeUnlocked = spec != null && JsonLite.getBoolean(spec, "largeUnlocked", false);
            int minLvLarge = spec == null ? 3 : JsonLite.getInt(spec, "minLevelLarge", 3);
            boolean smallUnlocked = spec == null || JsonLite.getBoolean(spec, "smallUnlocked", true);
            if (aidSizeBtn != null) {
                if (!aidLarge) {
                    aidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.small"));
                } else if (largeUnlocked) {
                    aidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.large"));
                } else {
                    aidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.large_locked", minLvLarge));
                }
            }
            if (aidStartBtn != null) {
                int queueSize = aidProjects(snap).size();
                int maxQueue = Math.max(1, JsonLite.getInt(snap, "maxAidQueue", 3));
                boolean depop = JsonLite.getBoolean(snap, "depopPaused", false);
                boolean unlocked = aidLarge ? largeUnlocked : smallUnlocked;
                boolean limitReached = spec != null && JsonLite.getBoolean(spec, "aidLimitReached", false);
                aidStartBtn.active = spec != null && !depop && queueSize < maxQueue && unlocked && !limitReached;
            }
        }
    }

    private void paintBanner(ScreenCanvas canvas, Map<String, Object> snap) {
        String name = JsonLite.getString(snap, "villageName", "");
        String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(snap, "biome", "plains"));
        int level = JsonLite.getInt(snap, "level", 1);
        int pop = JsonLite.getInt(snap, "population", 0);
        long funds = JsonLite.getLong(snap, "villageBalance", 0L);
        boolean depop = JsonLite.getBoolean(snap, "depopPaused", false);
        boolean chunkLoaded = JsonLite.getBoolean(snap, "chunkLoaderActive", false);
        String season = JsonLite.getString(snap, "season", "NONE");
        String infl = JsonLite.getString(snap, "inflationState", "STABLE");
        boolean bonusActive = JsonLite.getBoolean(snap, "bonusActive", false);
        long refreshRem = liveRefreshRemaining(snap);

        // Mayor crown emblem (212, 226, 16x16) at (12, 25)
        canvas.blit(GUI_TEX, 12, 25, TyC2Theme.HUB_MAYOR_CROWN_U, TyC2Theme.HUB_SPRITE_V, 16, 16);

        // Line 1 right: Biome 10x10 emblem + compact localized biome pill (y=23..33, right edge x=189)
        String biomeName = Component.translatable("biome.tyc2." + biomeKey).getString();
        int biomeScaledW = Math.round(canvas.textWidth(biomeName) * 0.68F);
        bannerBiomeW = Math.max(30, Math.min(54, 13 + biomeScaledW + 3));
        bannerBiomeX = 189 - bannerBiomeW;
        canvas.fill(bannerBiomeX, 23, bannerBiomeX + bannerBiomeW, 33, TyC2Theme.biomePillBg(biomeKey));
        canvas.outline(bannerBiomeX, 23, bannerBiomeW, 10, TyC2Theme.biomeAccentColor(biomeKey) & 0x00FFFFFF | 0xAA000000);
        canvas.blit(GUI_TEX, bannerBiomeX + 1, 23, TyC2Theme.hubBiomeU(biomeKey), TyC2Theme.HUB_BIOME_V, 10, 10);
        TyC2Theme.drawFitString(
                canvas,
                biomeName,
                bannerBiomeX + 12,
                25,
                Math.max(12, bannerBiomeW - 14),
                0.56F,
                0xFFEAF7FF
        );

        // Line 1 left: Village Name + golden Lv.X title (zero collision with biome pill)
        String lvlText = Component.translatable(
                "gui.tyc2.hub.level", level, Component.translatable("level.tyc2." + level)
        ).getString();
        String titleLine = name.isEmpty() ? lvlText : (name + "  " + lvlText);
        TyC2Theme.drawFitString(canvas, titleLine, 30, 24, Math.max(40, bannerBiomeX - 33), 0.58F, TyC2Theme.GOLD_TEXT);

        // Line 2: Population + Funds (+ bonus star) on left; Weekly Calendar + countdown pill on right
        if (depop) {
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.paused_depopulated").getString(),
                    30, 35, 158, 0.56F, TyC2Theme.DANGER_TEXT
            );
        } else {
            String stats = Component.translatable("gui.tyc2.hub.population", pop).getString()
                    + " | " + Component.translatable("gui.tyc2.hub.village_funds", TyC2Theme.formatCompactNumber(funds)).getString()
                    + (bonusActive ? " ★" : "");
            TyC2Theme.drawFitString(
                    canvas, stats, 30, 35, 101, 0.54F,
                    bonusActive ? TyC2Theme.MINT_TEXT : TyC2Theme.CYAN_TEXT
            );

            // Weekly calendar countdown pill (133..189, y=34..44)
            canvas.fill(133, 34, 189, 44, 0xFF0C1826);
            canvas.outline(133, 34, 56, 10, 0xFF28435C);
            canvas.blit(GUI_TEX, 134, 33, TyC2Theme.HUB_CALENDAR_U, TyC2Theme.HUB_SPRITE_V, 12, 12);
            String countdown = TyC2Theme.formatRestockCountdown(refreshRem);
            TyC2Theme.drawFitString(canvas, countdown, 148, 36, 39, 0.56F, 0xFFFFDF7A);
        }

        // Right Badge Strip (191..237): Season 12x12 (only when active) + CPI arrow 10x10 + Chunk-Loader 16x16
        int seasonU = TyC2Theme.hubSeasonU(season);
        if (seasonU >= 0) {
            canvas.blit(GUI_TEX, 192, 27, seasonU, TyC2Theme.HUB_SPRITE_V, 12, 12);
        }

        int cpiU = TyC2Theme.hubCpiArrowU(infl);
        canvas.blit(GUI_TEX, 207, 28, cpiU, TyC2Theme.HUB_SPRITE_V, 10, 10);

        if (chunkLoaded) {
            canvas.blit(GUI_TEX, 220, 25, TyC2Theme.HUB_CHUNK_LOADER_U, TyC2Theme.HUB_SPRITE_V, 16, 16);
        } else {
            canvas.fill(221, 26, 235, 40, 0xFF0E1824);
            canvas.outline(220, 25, 16, 16, 0xFF263B52);
            canvas.fill(226, 31, 230, 35, 0xFF2C4257);
        }
    }

    private void paintOrdersTab(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        List<Map<String, Object>> orders = filteredOrders();
        int totalPages = Math.max(1, (orders.size() + ORDERS_PER_PAGE - 1) / ORDERS_PER_PAGE);
        if (orderPage >= totalPages) {
            orderPage = totalPages - 1;
        }
        String pageLabel = (orderPage + 1) + "/" + totalPages;
        TyC2Theme.drawCenteredFitString(canvas, pageLabel, 152, 50, 52, 12, 0.70F, 0xFFB8D0E8);

        if (orders.isEmpty()) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.hub.no_orders").getString(),
                    14, 76, 220, 40, 0.70F, TyC2Theme.MUTED_TEXT
            );
            return;
        }

        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
            Map<String, Object> o = orders.get(start + i);
            int rowY = 65 + i * 23; // 65, 88, 111 (height 21px -> bottom 132)
            boolean villageSells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
            String itemId = JsonLite.getString(o, "itemId", "");
            int totalQty = Math.max(1, JsonLite.getInt(o, "totalQty", 1));
            int fulfilled = Math.max(0, JsonLite.getInt(o, "fulfilledQty", 0));
            long unitPrice = JsonLite.getLong(o, "unitPrice", 1L);
            boolean auto = JsonLite.getBoolean(o, "autoFulfill", false);
            boolean seasonal = JsonLite.getBoolean(o, "seasonalBonus", false);
            boolean biomeSpec = JsonLite.getBoolean(o, "biomeSpecialty", false);
            boolean done = fulfilled >= totalQty;

            // Elevated dark ledger card chrome + 2px left semantic accent stripe
            int bg = done ? 0xFF161E26 : (villageSells ? 0xFF152333 : 0xFF241D14);
            int topHilite = done ? 0xFF1E2934 : (villageSells ? 0xFF20354D : 0xFF382C1D);
            int border = done ? 0xFF2C3A47 : (villageSells ? 0xFF34597D : 0xFF7D5D2D);
            int stripe = done ? TyC2Theme.STRIPE_DONE : (villageSells ? TyC2Theme.STRIPE_SELL : TyC2Theme.STRIPE_BUY);

            canvas.fill(10, rowY, 237, rowY + 21, bg);
            canvas.fill(12, rowY + 1, 236, rowY + 2, topHilite);
            canvas.outline(10, rowY, 227, 21, border);
            canvas.fill(10, rowY + 1, 12, rowY + 20, stripe);

            // Item icon (14, rowY + 2)
            ItemStack preview = TyC2Native.stack(itemId, 1);
            canvas.drawItem(preview, 14, rowY + 2);

            // Auto-Fulfill 14x14 sprite rendered & clickable ONLY on Buy orders!
            int contentRight = villageSells ? 181 : 165;
            if (!villageSells) {
                canvas.blit(
                        GUI_TEX, 168, rowY + 3,
                        auto ? TyC2Theme.HUB_AUTO_ON_U : TyC2Theme.HUB_AUTO_OFF_U,
                        TyC2Theme.HUB_SPRITE_V, 14, 14
                );
            }

            // Line 1: localized item name on left + inline Biome Specialty & Seasonal tag pills beside name
            String biomeLbl = biomeSpec
                    ? Component.translatable(
                            villageSells ? "gui.tyc2.order.tag.biome_sell" : "gui.tyc2.order.tag.biome_buy"
                    ).getString()
                    : "";
            String seasonLbl = seasonal
                    ? Component.translatable(
                            villageSells ? "gui.tyc2.order.tag.season_sell" : "gui.tyc2.order.tag.season_buy"
                    ).getString()
                    : "";
            int biomePillW = biomeSpec ? TyC2Theme.measureOrderTagPillWidth(canvas, biomeLbl, 0.56F) : 0;
            int seasonPillW = seasonal ? TyC2Theme.measureOrderTagPillWidth(canvas, seasonLbl, 0.56F) : 0;
            int totalTagW = (biomeSpec ? biomePillW + 4 : 0)
                    + (seasonal ? seasonPillW + (biomeSpec ? 2 : 4) : 0);

            String itemName = TyC2Native.localizedItemName(itemId);
            int nameMaxW = Math.max(34, (contentRight - 33) - totalTagW);
            TyC2Theme.drawFitString(
                    canvas, itemName, 33, rowY + 2, nameMaxW, 0.56F,
                    done ? 0xFF7B8C9E : TyC2Theme.ICE_WHITE
            );
            int renderedNameW = Math.min(nameMaxW, canvas.textWidth(itemName));
            int tagCursorX = 33 + renderedNameW + 4;
            if (biomeSpec && tagCursorX + biomePillW <= contentRight) {
                TyC2Theme.drawOrderTagPill(
                        canvas, GUI_TEX, tagCursorX, rowY + 1, biomePillW,
                        TyC2Theme.HUB_TAG_BIOME_U, TyC2Theme.HUB_TAG_V,
                        biomeLbl, 0.56F, 0xFF112C1F, 0xFF3AC478, 0xFFA6FFCA
                );
                orderTagTips.add(new TagTipRect(
                        tagCursorX, rowY + 1, biomePillW, 9,
                        villageSells ? "gui.tyc2.order.tooltip.biome_sell" : "gui.tyc2.order.tooltip.biome_buy"
                ));
                tagCursorX += biomePillW + 2;
            }
            if (seasonal && tagCursorX + seasonPillW <= contentRight) {
                TyC2Theme.drawOrderTagPill(
                        canvas, GUI_TEX, tagCursorX, rowY + 1, seasonPillW,
                        TyC2Theme.HUB_TAG_SEASON_U, TyC2Theme.HUB_TAG_V,
                        seasonLbl, 0.56F, 0xFF102A3A, 0xFF39B5DE, 0xFFAEEFFF
                );
                orderTagTips.add(new TagTipRect(
                        tagCursorX, rowY + 1, seasonPillW, 9,
                        villageSells ? "gui.tyc2.order.tooltip.season_sell" : "gui.tyc2.order.tooltip.season_buy"
                ));
            }

            // Line 2: Progress on left + Unit price right-aligned
            String progStr = Component.translatable("gui.tyc2.hub.order_progress", fulfilled, totalQty).getString();
            String priceStr = Component.translatable("gui.tyc2.hub.unit_price", unitPrice).getString();
            int priceAlloc = Math.min(62, Math.max(42, (contentRight - 33) / 2));
            int progMaxW = Math.max(32, (contentRight - 33) - priceAlloc - 3);
            TyC2Theme.drawFitString(
                    canvas, progStr, 33, rowY + 11, progMaxW, 0.56F,
                    done ? TyC2Theme.MINT_TEXT : 0xFFACCBE6
            );
            TyC2Theme.drawRightFitString(
                    canvas, priceStr, contentRight, rowY + 11, priceAlloc, 0.56F,
                    done ? 0xFF998F6A : TyC2Theme.GOLD_TEXT
            );

            // Crisp 2px bottom progress bar across card width (12..236, rowY+18..rowY+20)
            canvas.fill(12, rowY + 18, 236, rowY + 20, 0xFF0A111A);
            double ratio = Math.min(1.0, Math.max(0.0, fulfilled / (double) totalQty));
            int barW = (int) Math.round(224.0 * ratio);
            if (barW > 0) {
                int barCol = done ? 0xFF3CE87A : (villageSells ? TyC2Theme.STRIPE_SELL : TyC2Theme.STRIPE_BUY);
                canvas.fill(12, rowY + 18, 12 + barW, rowY + 20, barCol);
            }

            // Styled Buy/Deliver CTA button (184, rowY+3, 51x14)
            boolean hoverBtn = !done && pmx >= 184 && pmx < 235 && pmy >= rowY + 3 && pmy < rowY + 17;
            String btnLbl = done
                    ? Component.translatable("gui.tyc2.confirm").getString()
                    : Component.translatable(villageSells ? "gui.tyc2.hub.buy" : "gui.tyc2.hub.deliver").getString();
            TyC2Theme.drawBevelButtonBox(
                    canvas, 184, rowY + 3, 51, 14,
                    villageSells ? TyC2Theme.ButtonStyle.HUB_PRIMARY : TyC2Theme.ButtonStyle.HUB_BUY,
                    !done, hoverBtn, btnLbl
            );
        }
    }

    private void paintWarehouseTab(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        int page = JsonLite.getInt(snap, "whPage", whPage);
        int totalPages = Math.max(1, JsonLite.getInt(snap, "whTotalPages", 1));
        TyC2Theme.drawCenteredFitString(canvas, (page + 1) + "/" + totalPages, 193, 50, 28, 12, 0.65F, 0xFFCAE6FF);

        boolean outbound = "OUTBOUND".equals(JsonLite.getString(snap, "whZone", whZone));
        int slotU = outbound ? TyC2Theme.HUB_SLOT_OUTBOUND_U : TyC2Theme.HUB_SLOT_INBOUND_U;

        List<Object> slots = JsonLite.asArray(snap.get("whSlots"));
        for (int idx = 0; idx < 36; idx++) {
            int row = idx / 12;
            int col = idx % 12;
            int sx = 16 + col * 18;
            int sy = 67 + row * 20;
            canvas.blit(GUI_TEX, sx, sy, slotU, TyC2Theme.HUB_SPRITE_V, 18, 18);

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
                canvas.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x44FFFFFF);
                canvas.outline(sx, sy, 18, 18, outbound ? 0xFFFFCA66 : 0xFF6BC7FF);
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
        String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(snap, "biome", "plains"));
        int tier = JsonLite.getInt(snap, "industrialTier", 0);
        long refreshRem = liveRefreshRemaining(snap);

        // Framed left ledger stats card (10..119, 50..133)
        canvas.fill(10, 50, 119, 133, 0xFF0D1622);
        canvas.outline(10, 50, 109, 83, 0xFF284059);

        int cpiColor = TyC2Theme.inflationColor(infl);
        String inflText = Component.translatable("gui.tyc2.economy." + infl.toLowerCase(Locale.ROOT)).getString();
        String cpiLine = Component.translatable("gui.tyc2.hub.status.cpi", String.format(Locale.ROOT, "%.2f", cpi)).getString()
                + " (" + inflText + ")";
        TyC2Theme.drawFitString(canvas, cpiLine, 13, 52, 103, 0.56F, cpiColor);
        canvas.fill(12, 61, 117, 62, 0xFF1A2C40);

        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.money_short", TyC2Theme.formatCompactNumber(money)).getString(),
                13, 63, 103, 0.56F, 0xFFE2EFFA
        );
        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.goods_short", TyC2Theme.formatCompactNumber(goods)).getString(),
                13, 73, 103, 0.56F, 0xFFE2EFFA
        );
        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.economy.village_liquidity", String.format(Locale.ROOT, "%.2fx", liq)).getString(),
                13, 83, 103, 0.56F, 0xFFFFDF7F
        );
        String envLine = Component.translatable("biome.tyc2." + biomeKey).getString()
                + " · " + Component.translatable("season.tyc2." + season.toLowerCase(Locale.ROOT)).getString();
        TyC2Theme.drawFitString(canvas, envLine, 13, 93, 103, 0.56F, 0xFF9BE8FF);
        TyC2Theme.drawFitString(canvas, Component.translatable("tier.tyc2." + tier).getString(), 13, 103, 103, 0.54F, 0xFFB8D4F0);

        int unempPct = JsonLite.getInt(snap, "unemploymentPct", 0);
        int unempCount = JsonLite.getInt(snap, "unemployedCount", 0);
        int workforce = JsonLite.getInt(snap, "workforceCount", 0);
        boolean bonusActive = JsonLite.getBoolean(snap, "bonusActive", false);
        int qtyPct = JsonLite.getInt(snap, "qtyBonusPct", 0);
        int thresholdPct = JsonLite.getInt(snap, "lowUnempThresholdPct", 25);

        TyC2Theme.drawFitString(
                canvas,
                Component.translatable("gui.tyc2.hub.unemployment", unempPct, unempCount, workforce).getString(),
                13, 113, 103, 0.52F, bonusActive ? TyC2Theme.MINT_TEXT : 0xFFE0ECF8
        );
        String bonusLine = bonusActive
                ? Component.translatable("gui.tyc2.hub.unemployment_bonus", qtyPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", thresholdPct).getString();
        TyC2Theme.drawFitString(canvas, bonusLine, 13, 123, 103, 0.48F, bonusActive ? 0xFF56EC8B : TyC2Theme.MUTED_TEXT);

        // Right Chart Header + upgraded CPI Chart (122..237, 60..133)
        String chartTitle = Component.translatable("gui.tyc2.economy.cpi_chart").getString();
        TyC2Theme.drawFitString(canvas, chartTitle, 123, 51, 56, 0.62F, 0xFFB8D2EB);
        String restockStr = Component.translatable(
                "gui.tyc2.hub.refresh_label", TyC2Theme.formatRestockCountdown(refreshRem)
        ).getString();
        TyC2Theme.drawRightFitString(canvas, restockStr, 237, 51, 56, 0.54F, 0xFFFFDF7A);
        CpiChartRenderer.drawChart(canvas, 122, 60, 115, 73, extractCpiHistory(snap), false);
    }

    private void paintGrowthSettingsTab(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        if (growthSubTab == 0) {
            int level = JsonLite.getInt(snap, "level", 1);
            int pop = JsonLite.getInt(snap, "population", 0);
            long vol = JsonLite.getLong(snap, "tradeVolume", 0L);
            long bal = JsonLite.getLong(snap, "villageBalance", 0L);
            boolean canUp = JsonLite.getBoolean(snap, "canUpgrade", false);

            // Framed growth summary card (10..237, 64..115)
            canvas.fill(10, 64, 237, 115, 0xFF0E1824);
            canvas.outline(10, 64, 227, 51, 0xFF2B445E);

            if (level >= 10) {
                TyC2Theme.drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.hub.max_level").getString(),
                        14, 67, 219, 0.60F, TyC2Theme.GOLD_TEXT
                );
            } else {
                int reqPop = JsonLite.getInt(snap, "nextMinPop", 0);
                long reqVol = JsonLite.getLong(snap, "nextMinVol", 0L);
                long reqBal = JsonLite.getLong(snap, "nextMinBal", 0L);
                String reqLine = Component.translatable(
                        "gui.tyc2.hub.upgrade_req",
                        pop + "/" + reqPop,
                        TyC2Theme.formatCompactNumber(vol) + "/" + TyC2Theme.formatCompactNumber(reqVol),
                        TyC2Theme.formatCompactNumber(bal) + "/" + TyC2Theme.formatCompactNumber(reqBal)
                ).getString();
                TyC2Theme.drawFitString(canvas, reqLine, 14, 67, 219, 0.56F, canUp ? TyC2Theme.MINT_TEXT : 0xFFE0ECF8);
            }
            canvas.fill(12, 76, 235, 77, 0xFF1A2D42);

            Map<String, Object> build = JsonLite.asObject(snap.get("activeBuild"));
            Map<String, Object> desire = JsonLite.asObject(snap.get("expansionDesire"));
            if (build != null) {
                String pType = JsonLite.getString(build, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
                int pct = Math.max(0, Math.min(100, JsonLite.getInt(build, "progressPct", 0)));
                Component pName = Component.translatable("project.tyc2." + pType);
                TyC2Theme.drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.expansion.building", pName, pct).getString(),
                        14, 79, 219, 0.60F, 0xFF64E8FF
                );
                canvas.fill(14, 92, 233, 102, 0xFF0A121C);
                canvas.outline(14, 92, 219, 10, 0xFF315678);
                int fillW = Math.max(0, (int) Math.round(217.0 * pct / 100.0));
                if (fillW > 0) {
                    canvas.fill(15, 93, 15 + fillW, 101, 0xFF36D988);
                    canvas.fill(15, 93, 15 + fillW, 95, 0xFF72FFB8);
                }
            } else if (desire != null) {
                String pType = JsonLite.getString(desire, "projectType", "SMALL_COTTAGE").toLowerCase(Locale.ROOT);
                long budget = JsonLite.getLong(desire, "budget", 0L);
                boolean auto = JsonLite.getBoolean(desire, "autoFulfill", false);
                Component pName = Component.translatable("project.tyc2." + pType);
                String autoTxt = Component.translatable(
                        auto ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
                ).getString();
                String header = pName.getString() + " (" + budget + " IC) " + autoTxt;
                TyC2Theme.drawFitString(canvas, header, 14, 78, 219, 0.58F, 0xFFFFE066);
                Map<String, Object> req = JsonLite.asObject(desire.get("required"));
                Map<String, Object> del = JsonLite.asObject(desire.get("delivered"));
                for (MaterialCell cell : layoutExpansionMaterials(req)) {
                    int need = JsonLite.getInt(req, cell.itemId(), 0);
                    int have = del == null ? 0 : JsonLite.getInt(del, cell.itemId(), 0);
                    ItemStack st = TyC2Native.stack(cell.itemId(), 1);
                    canvas.drawItem(st, cell.x(), cell.y());
                    TyC2Theme.drawFitString(
                            canvas,
                            have + "/" + need,
                            cell.x() + 18,
                            cell.y() + 4,
                            cell.textW(),
                            0.50F,
                            have >= need ? TyC2Theme.MINT_TEXT : 0xFFFFCC66
                    );
                }
            } else {
                TyC2Theme.drawFitString(
                        canvas,
                        Component.translatable("gui.tyc2.expansion.none").getString(),
                        14, 86, 219, 0.60F, TyC2Theme.MUTED_TEXT
                );
            }

            boolean paused = JsonLite.getBoolean(snap, "expansionPaused", false);
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable(paused ? "gui.tyc2.expansion.pause.on" : "gui.tyc2.expansion.pause.off").getString(),
                    14, 106, 219, 0.56F, paused ? 0xFFFF8866 : 0xFF7BE89A
            );
        } else if (growthSubTab == 1) {
            paintAidSubTab(canvas, snap, pmx, pmy);
        } else {
            // Settings & Nearby Logistics framed card (10..237, 81..113)
            canvas.fill(10, 81, 237, 113, 0xFF0E1824);
            canvas.outline(10, 81, 227, 32, 0xFF2B445E);

            int minR = JsonLite.getInt(snap, "minRadius", 16);
            int maxR = JsonLite.getInt(snap, "maxRadius", 64);
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.expansion.radius", minR, maxR).getString(),
                    14, 84, 219, 0.60F, 0xFFAEC8E0
            );

            List<Object> stations = JsonLite.asArray(snap.get("lc2Stations"));
            String lc2Summary = (stations == null || stations.isEmpty())
                    ? Component.translatable("gui.tyc2.terminal.no_lc2").getString()
                    : Lc2SoftBridge.formatStationLabel(stations.get(0).toString())
                            + (stations.size() > 1 ? " (+" + (stations.size() - 1) + ")" : "");
            TyC2Theme.drawFitString(
                    canvas,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", lc2Summary).getString(),
                    14, 94, 219, 0.58F, 0xFF64E8FF
            );

            boolean canManage = JsonLite.getBoolean(snap, "canManage", false);
            if (!canManage) {
                TyC2Theme.drawFitString(
                        canvas,
                        Component.translatable("status.tyc2.hub.not_owner").getString(),
                        14, 104, 219, 0.56F, 0xFFFF8866
                );
            }
        }
    }

    private void paintAidSubTab(ScreenCanvas canvas, Map<String, Object> snap, int pmx, int pmy) {
        Map<String, Object> spec = selectedWorkplace(snap);
        // Profession display box (24..106, 63..77)
        canvas.fill(24, 63, 106, 77, 0xFF101E2E);
        canvas.outline(24, 63, 82, 14, 0xFF3E6F9E);
        if (spec != null) {
            String wsBlockId = JsonLite.getString(spec, "workstationBlockId", "");
            String profId = JsonLite.getString(spec, "professionId", "");
            String arch = JsonLite.getString(spec, "archetype", "GENERIC");
            String fallback = JsonLite.getString(spec, "displayNameFallback", "");
            ItemStack wsStack = TyC2Native.stack(wsBlockId, 1);
            if (!wsStack.isEmpty()) {
                canvas.drawItem(wsStack, 25, 62);
            }
            String profLabel = localizedProfessionName(arch, profId, fallback);
            TyC2Theme.drawFitString(canvas, profLabel, 43, 66, 61, 0.56F, TyC2Theme.ICE_WHITE);

            long cost = aidLarge
                    ? JsonLite.getLong(spec, "costLargeIc", 0L)
                    : JsonLite.getLong(spec, "costSmallIc", 0L);
            boolean unlocked = aidLarge
                    ? JsonLite.getBoolean(spec, "largeUnlocked", false)
                    : JsonLite.getBoolean(spec, "smallUnlocked", true);
            String costLine = Component.translatable("gui.tyc2.aid.cost", cost).getString();
            TyC2Theme.drawFitString(canvas, costLine, 10, 79, 102, 0.56F, unlocked ? TyC2Theme.GOLD_TEXT : 0xFFFF7766);
        }

        double gatherMult = JsonLite.getDouble(snap, "gatherSpeedMult", 1.0);
        int unemployed = JsonLite.getInt(snap, "unemployedCount", 0);
        String rateLine = Component.translatable(
                "gui.tyc2.aid.gather_rate",
                String.format(Locale.ROOT, "%.2f", gatherMult),
                unemployed
        ).getString();
        TyC2Theme.drawFitString(canvas, rateLine, 114, 79, 122, 0.56F, TyC2Theme.CYAN_TEXT);

        boolean bonusActive = JsonLite.getBoolean(snap, "bonusActive", false);
        int qtyPct = JsonLite.getInt(snap, "qtyBonusPct", 0);
        int thresholdPct = JsonLite.getInt(snap, "lowUnempThresholdPct", 25);
        String bonusLine = bonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", qtyPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", thresholdPct).getString();
        TyC2Theme.drawFitString(canvas, bonusLine, 10, 88, 152, 0.52F, bonusActive ? TyC2Theme.MINT_TEXT : 0xFF9BB4CC);

        List<Map<String, Object>> queue = aidProjects(snap);
        int maxQueue = Math.max(1, JsonLite.getInt(snap, "maxAidQueue", 3));
        String qHeader = Component.translatable("gui.tyc2.aid.queue_header", queue.size(), maxQueue).getString();
        TyC2Theme.drawRightFitString(canvas, qHeader, 236, 88, 72, 0.56F, TyC2Theme.GOLD_TEXT);

        if (queue.isEmpty()) {
            TyC2Theme.drawCenteredFitString(
                    canvas,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    14, 100, 218, 28, 0.65F, TyC2Theme.MUTED_TEXT
            );
            return;
        }

        int shown = Math.min(3, queue.size());
        for (int i = 0; i < shown; i++) {
            Map<String, Object> proj = queue.get(i);
            int ry = 97 + i * 12;
            String status = JsonLite.getString(proj, "status", "COLLECTING");
            int pct = Math.max(0, Math.min(100, JsonLite.getInt(proj, "progressPct", 0)));
            boolean canCancel = JsonLite.getBoolean(proj, "canCancel", false);
            String sizeStr = JsonLite.getString(proj, "size", "SMALL");
            String profName = localizedProfessionName(
                    JsonLite.getString(proj, "archetype", "GENERIC"),
                    JsonLite.getString(proj, "professionId", ""),
                    JsonLite.getString(proj, "displayNameFallback", "")
            );
            String sizeShort = "LARGE".equalsIgnoreCase(sizeStr) ? "L" : "S";

            int bg = "BUILDING".equals(status) ? 0xFF132936
                    : ("NO_SITE".equals(status) ? 0xFF2B1C16 : 0xFF14212E);
            int border = "BUILDING".equals(status) ? 0xFF4BE2FF
                    : ("NO_SITE".equals(status) ? 0xFFFF8855 : 0xFF395E84);
            canvas.fill(10, ry, 236, ry + 11, bg);
            canvas.outline(10, ry, 226, 11, border);

            int barMaxW = canCancel ? 180 : 224;
            int barW = (int) Math.round(barMaxW * pct / 100.0);
            if (barW > 0) {
                int barColor = "BUILDING".equals(status) ? 0xFF39D98A : 0xFF4CA8FF;
                canvas.fill(11, ry + 9, 11 + barW, ry + 10, barColor);
            }

            TyC2Theme.drawFitString(canvas, profName + " [" + sizeShort + "]", 13, ry + 1, 92, 0.54F, TyC2Theme.ICE_WHITE);
            TyC2Theme.drawFitString(
                    canvas,
                    aidStatusText(status, pct),
                    107, ry + 1, canCancel ? 83 : 126, 0.54F,
                    "NO_SITE".equals(status) ? 0xFFFFAA66 : ("BUILDING".equals(status) ? TyC2Theme.MINT_TEXT : 0xFFFFDF7F)
            );

            if (canCancel) {
                boolean hoverCancel = pmx >= 192 && pmx < 235 && pmy >= ry + 1 && pmy < ry + 10;
                TyC2Theme.drawBevelButtonBox(
                        canvas, 192, ry + 1, 43, 9,
                        TyC2Theme.ButtonStyle.HUB_DANGER, true, hoverCancel,
                        Component.translatable("gui.tyc2.aid.cancel_btn").getString()
                );
            }
        }
    }

    private void paintStatusLcd(ScreenCanvas canvas, Map<String, Object> snap) {
        // Bottom-right Status LCD (179, 166, 61x49) — gilded top hairline baked at y=167..168
        long bankBal = JsonLite.getLong(snap, "playerBankBalance", 0L);
        int inUsed = JsonLite.getInt(snap, "inboundUsed", 0);
        int outUsed = JsonLite.getInt(snap, "outboundUsed", 0);
        double cpi = JsonLite.getDouble(snap, "cpi", 1.0);
        String infl = JsonLite.getString(snap, "inflationState", "STABLE");
        boolean cl = JsonLite.getBoolean(snap, "chunkLoaderActive", false);

        String[] rows = {
                Component.translatable("gui.tyc2.hub.status.bank", TyC2Theme.formatCompactNumber(bankBal)).getString(),
                Component.translatable("gui.tyc2.hub.status.inbound", inUsed).getString(),
                Component.translatable("gui.tyc2.hub.status.outbound", outUsed).getString(),
                Component.translatable("gui.tyc2.hub.status.cpi", String.format(Locale.ROOT, "%.2f", cpi)).getString(),
                Component.translatable(cl ? "gui.tyc2.hub.status.cl_on" : "gui.tyc2.hub.status.cl_off").getString()
        };
        int[] colors = {
                0xFFFFE270,
                0xFF66C2FF,
                0xFFFFB866,
                TyC2Theme.inflationColor(infl),
                cl ? 0xFF5EFFDB : 0xFF728699
        };
        for (int i = 0; i < rows.length; i++) {
            int ry = 170 + i * 9;
            if (i > 0) {
                canvas.fill(181, ry - 1, 238, ry, 0xFF172536);
            }
            TyC2Theme.drawFitString(canvas, rows[i], 182, ry, 55, 0.56F, colors[i]);
        }
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
                int rowY = 65 + i * 23;
                Map<String, Object> o = orders.get(start + i);
                String orderId = JsonLite.getString(o, "id", "");
                boolean villageSells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
                int totalQty = Math.max(1, JsonLite.getInt(o, "totalQty", 1));
                int fulfilled = Math.max(0, JsonLite.getInt(o, "fulfilledQty", 0));
                boolean done = fulfilled >= totalQty;

                // Auto-Fulfill toggle ONLY active on Village Buy Orders!
                if (!villageSells && pmx >= 168 && pmx < 182 && pmy >= rowY + 3 && pmy < rowY + 17) {
                    Map<String, Object> args = viewArgs();
                    args.put("orderId", orderId);
                    sendAction("TOGGLE_ORDER_AUTO", args);
                    return true;
                }
                if (!done && pmx >= 184 && pmx < 235 && pmy >= rowY + 3 && pmy < rowY + 17) {
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
        } else if (activeTab == 3 && growthSubTab == 1) {
            List<Map<String, Object>> queue = aidProjects(menu.snapshot());
            int shown = Math.min(3, queue.size());
            for (int i = 0; i < shown; i++) {
                int ry = 97 + i * 12;
                Map<String, Object> proj = queue.get(i);
                if (JsonLite.getBoolean(proj, "canCancel", false)
                        && pmx >= 192 && pmx < 235 && pmy >= ry + 1 && pmy < ry + 10) {
                    String projId = JsonLite.getString(proj, "projectId", "");
                    if (!projId.isEmpty()) {
                        Map<String, Object> args = viewArgs();
                        args.put("projectId", projId);
                        sendAction("CANCEL_AID", args);
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

        // 1. Order tag pills, item icons & Auto-Fulfill badges on Orders tab
        if (activeTab == 0) {
            for (TagTipRect rect : orderTagTips) {
                if (rect.contains(pmx, pmy)) {
                    canvas.drawTooltip(List.of(Component.translatable(rect.tooltipKey())), mouseX, mouseY);
                    return;
                }
            }
            List<Map<String, Object>> orders = filteredOrders();
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < orders.size(); i++) {
                int rowY = 65 + i * 23;
                Map<String, Object> o = orders.get(start + i);
                boolean villageSells = "VILLAGE_SELLS".equals(JsonLite.getString(o, "side", ""));
                if (!villageSells && pmx >= 168 && pmx < 182 && pmy >= rowY + 3 && pmy < rowY + 17) {
                    boolean auto = JsonLite.getBoolean(o, "autoFulfill", false);
                    canvas.drawTooltip(List.of(Component.translatable(
                            auto ? "gui.tyc2.hub.auto_fulfill.on" : "gui.tyc2.hub.auto_fulfill.off"
                    )), mouseX, mouseY);
                    return;
                }
                if (pmx >= 14 && pmx < 30 && pmy >= rowY + 2 && pmy < rowY + 18) {
                    String itemId = JsonLite.getString(o, "itemId", "");
                    String ench = JsonLite.getString(o, "enchantSpec", "");
                    String optTag = JsonLite.getString(o, "optionalTag", "");
                    ItemStack st = TyC2Native.stack(itemId, 1);
                    List<Component> tip = new ArrayList<>(3);
                    tip.add(st.isEmpty() ? Component.literal(itemId) : st.getHoverName());
                    if (!ench.isBlank()) {
                        tip.add(Component.literal(ench).withStyle(ChatFormatting.AQUA));
                    }
                    if (!villageSells && !optTag.isBlank()) {
                        tip.add(Component.translatable("gui.tyc2.order.tooltip.accepts_tag", optTag)
                                .withStyle(ChatFormatting.GOLD));
                    }
                    canvas.drawTooltip(tip, mouseX, mouseY);
                    return;
                }
            }
        }

        // 2. Warehouse slot hover tooltips
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
                            String metaHint = JsonLite.getString(sm, "metaHint", "");
                            if (!itemId.isEmpty() && count > 0) {
                                ItemStack st = TyC2Native.stack(itemId, 1);
                                Component nameComp = st.isEmpty() ? Component.literal(itemId) : st.getHoverName();
                                List<Component> tip = new ArrayList<>(3);
                                tip.add(nameComp);
                                if (!metaHint.isBlank()) {
                                    tip.add(Component.literal(metaHint).withStyle(ChatFormatting.AQUA));
                                }
                                tip.add(Component.translatable("gui.tyc2.hub.slot_tooltip", count));
                                canvas.drawTooltip(tip, mouseX, mouseY);
                            }
                        }
                    }
                    return;
                }
            }
        }

        // 3. Expansion material hover tooltips
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

        // 4. Aid construction queue & cap hover tooltips
        if (activeTab == 3 && growthSubTab == 1) {
            Map<String, Object> spec = selectedWorkplace(snap);
            if (spec != null && JsonLite.getBoolean(spec, "aidLimitReached", false)
                    && pmx >= 186 && pmx < 236 && pmy >= 63 && pmy < 77) {
                int aidMax = JsonLite.getInt(spec, "aidMax", WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION);
                canvas.drawTooltip(
                        List.of(Component.translatable("status.tyc2.aid.profession_limit", aidMax)
                                .withStyle(ChatFormatting.RED)),
                        mouseX, mouseY
                );
                return;
            }
            List<Map<String, Object>> queue = aidProjects(snap);
            int shown = Math.min(3, queue.size());
            for (int i = 0; i < shown; i++) {
                int ry = 97 + i * 12;
                if (pmx >= 10 && pmx < 192 && pmy >= ry && pmy < ry + 11) {
                    List<Component> tip = buildAidMaterialsTooltip(queue.get(i));
                    if (!tip.isEmpty()) {
                        canvas.drawTooltip(tip, mouseX, mouseY);
                    }
                    return;
                }
            }
        }

        // 5. Banner Biome Pill tooltip
        if (pmx >= bannerBiomeX && pmx < bannerBiomeX + bannerBiomeW && pmy >= 22 && pmy < 34) {
            String biomeKey = TyC2Theme.normalizeBiomeKey(JsonLite.getString(snap, "biome", "plains"));
            canvas.drawTooltip(List.of(
                    Component.translatable("gui.tyc2.hub.biome_tooltip", Component.translatable("biome.tyc2." + biomeKey))
                            .withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.tyc2.economy.biome_specialty").withStyle(ChatFormatting.GREEN)
            ), mouseX, mouseY);
            return;
        }

        // 6. Banner Weekly Calendar countdown tooltip
        if (!JsonLite.getBoolean(snap, "depopPaused", false)
                && pmx >= 133 && pmx < 190 && pmy >= 33 && pmy < 45) {
            long refreshRem = liveRefreshRemaining(snap);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "gui.tyc2.hub.refresh_tooltip", TyC2Theme.formatRestockCountdown(refreshRem)
            ).withStyle(ChatFormatting.GOLD));
            if (JsonLite.getBoolean(snap, "bonusActive", false)) {
                lines.add(Component.translatable(
                        "gui.tyc2.aid.bonus_active",
                        JsonLite.getInt(snap, "qtyBonusPct", 0)
                ).withStyle(ChatFormatting.GREEN));
            }
            canvas.drawTooltip(lines, mouseX, mouseY);
            return;
        }

        // 7. Banner Population / Unemployment & Bonus tooltip
        if (!JsonLite.getBoolean(snap, "depopPaused", false)
                && pmx >= 30 && pmx < 132 && pmy >= 33 && pmy < 45) {
            int unempPct = JsonLite.getInt(snap, "unemploymentPct", 0);
            int unempCount = JsonLite.getInt(snap, "unemployedCount", 0);
            int workforce = JsonLite.getInt(snap, "workforceCount", 0);
            boolean bonusActive = JsonLite.getBoolean(snap, "bonusActive", false);
            int qtyPct = JsonLite.getInt(snap, "qtyBonusPct", 0);
            int thresholdPct = JsonLite.getInt(snap, "lowUnempThresholdPct", 25);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.tyc2.hub.unemployment", unempPct, unempCount, workforce));
            if (bonusActive) {
                lines.add(Component.translatable("gui.tyc2.aid.bonus_active", qtyPct)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                lines.add(Component.translatable("gui.tyc2.aid.bonus_inactive", thresholdPct)
                        .withStyle(ChatFormatting.GRAY));
            }
            canvas.drawTooltip(lines, mouseX, mouseY);
            return;
        }

        // 8. Banner Season badge tooltip (only when a season mod is active)
        String bannerSeason = JsonLite.getString(snap, "season", "NONE");
        if (TyC2Theme.hubSeasonU(bannerSeason) >= 0 && pmx >= 192 && pmx < 204 && pmy >= 27 && pmy < 39) {
            canvas.drawTooltip(List.of(Component.translatable(
                    "gui.tyc2.hub.season_tooltip",
                    Component.translatable("season.tyc2." + bannerSeason.toLowerCase(Locale.ROOT))
            ).withStyle(ChatFormatting.AQUA)), mouseX, mouseY);
            return;
        }

        // 9. Banner CPI trend arrow tooltip
        if (pmx >= 207 && pmx < 217 && pmy >= 28 && pmy < 38) {
            double cpi = JsonLite.getDouble(snap, "cpi", 1.0);
            String infl = JsonLite.getString(snap, "inflationState", "STABLE").toLowerCase(Locale.ROOT);
            canvas.drawTooltip(List.of(
                    Component.translatable("gui.tyc2.economy.cpi", String.format(Locale.ROOT, "%.2f", cpi))
                            .withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.tyc2.economy." + infl)
            ), mouseX, mouseY);
            return;
        }

        // 10. Banner Chunk-Loader badge tooltip
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

    private Component payModeShortComponent() {
        return Component.translatable(cashMode ? "gui.tyc2.hub.cash_short" : "gui.tyc2.hub.bank_short");
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
                step = 55;
                textW = 35;
            } else if (count == 5) {
                step = 44;
                textW = 24;
            } else {
                step = Math.max(36, 220 / count);
                textW = Math.max(18, step - 19);
            }
            int idx = 0;
            for (String itemId : req.keySet()) {
                int x = 14 + idx * step;
                cells.add(new MaterialCell(itemId, x, 89, textW, Math.min(step - 1, 18 + textW)));
                idx++;
            }
        } else {
            int perRow = (count + 1) / 2;
            int step = Math.max(36, 220 / Math.min(6, perRow));
            int textW = Math.max(18, step - 19);
            int idx = 0;
            for (String itemId : req.keySet()) {
                int col = idx % perRow;
                int y = (idx < perRow) ? 87 : 98;
                int x = 14 + col * step;
                cells.add(new MaterialCell(itemId, x, y, textW, Math.min(step - 1, 18 + textW)));
                idx++;
            }
        }
        return cells;
    }

    static List<Map<String, Object>> availableWorkplaces(Map<String, Object> snap) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (snap == null) {
            return out;
        }
        List<Object> raw = JsonLite.asArray(snap.get("workplaces"));
        if (raw != null) {
            for (Object obj : raw) {
                Map<String, Object> m = JsonLite.asObject(obj);
                if (m != null) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    static List<Map<String, Object>> aidProjects(Map<String, Object> snap) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (snap == null) {
            return out;
        }
        List<Object> raw = JsonLite.asArray(snap.get("aidProjects"));
        if (raw != null) {
            for (Object obj : raw) {
                Map<String, Object> m = JsonLite.asObject(obj);
                if (m != null) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private Map<String, Object> selectedWorkplace(Map<String, Object> snap) {
        List<Map<String, Object>> list = availableWorkplaces(snap);
        if (list.isEmpty()) {
            return null;
        }
        if (aidProfIdx < 0 || aidProfIdx >= list.size()) {
            aidProfIdx = 0;
        }
        return list.get(aidProfIdx);
    }

    static String localizedProfessionName(String archetypeName, String professionId, String fallback) {
        if (archetypeName != null && !archetypeName.isBlank() && !"GENERIC".equalsIgnoreCase(archetypeName)) {
            return Component.translatable("profession.tyc2." + archetypeName.toLowerCase(Locale.ROOT)).getString();
        }
        if (professionId != null && !professionId.isBlank()) {
            int colon = professionId.indexOf(':');
            if (colon > 0) {
                String ns = professionId.substring(0, colon);
                String path = professionId.substring(colon + 1);
                Language lang = Language.getInstance();
                String mcKey = "entity.minecraft.villager." + ns + "." + path;
                if (lang.has(mcKey)) {
                    return Component.translatable(mcKey).getString();
                }
                String altKey = "entity.minecraft.villager." + path;
                if (lang.has(altKey)) {
                    return Component.translatable(altKey).getString();
                }
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return Component.translatable("profession.tyc2.generic").getString();
    }

    static String aidStatusText(String status, int pct) {
        if ("BUILDING".equals(status)) {
            return Component.translatable("gui.tyc2.aid.status.building", pct).getString();
        }
        if ("WAITING_SITE".equals(status)) {
            return Component.translatable("gui.tyc2.aid.status.waiting_site").getString();
        }
        if ("NO_SITE".equals(status)) {
            return Component.translatable("gui.tyc2.aid.status.no_site").getString();
        }
        return Component.translatable("gui.tyc2.aid.status.collecting", pct).getString();
    }

    static List<Component> buildAidMaterialsTooltip(Map<String, Object> proj) {
        if (proj == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.tyc2.aid.materials_tooltip").withStyle(ChatFormatting.GOLD));
        Map<String, Object> req = JsonLite.asObject(proj.get("requiredMaterials"));
        Map<String, Object> gathered = JsonLite.asObject(proj.get("gatheredMaterials"));
        if (req != null) {
            for (Map.Entry<String, Object> e : req.entrySet()) {
                int need = JsonLite.getInt(req, e.getKey(), 0);
                int have = gathered == null ? 0 : JsonLite.getInt(gathered, e.getKey(), 0);
                ItemStack st = TyC2Native.stack(e.getKey(), 1);
                Component itemComp = st.isEmpty() ? Component.literal(e.getKey()) : st.getHoverName();
                lines.add(Component.literal("• ")
                        .append(itemComp)
                        .append(": " + have + " / " + need)
                        .withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        return lines;
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
