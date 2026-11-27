package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.forge.client.TyC2UiWidgets.ButtonTheme;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.gui.CooperationHubMenu;
import com.tycooncraft.tyc2.forge.network.C2SHubActionPacket;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 1.19.2 bespoke Gilded Bronze &amp; Deep Ledger screen for {@code tyc2:cooperation_hub}
 * matching {@code gui/cooperation_hub.png} (248x222): Orders, 1,000-slot Warehouse, Macro Economy
 * + 48-point CPI Chart, and Growth / Aid / Settings.
 */
public class CooperationHubScreen extends AbstractContainerScreen<CooperationHubMenu> {

    private static final ResourceLocation TEXTURE = TyC2Ids.id("textures/gui/cooperation_hub.png");
    private static final int ORDERS_PER_PAGE = 4;
    private static final int MAX_AID_ROWS = 3;
    private static final int WH_COLS = 12;
    private static final int WH_ROWS = 3;
    private static final int WH_GRID_X = 16;
    private static final int WH_GRID_Y = 66;

    @Nullable
    private S2CHubSyncPacket sync;

    private int activeTab = 0; // 0=Orders, 1=Warehouse, 2=Economy, 3=Growth(Expansion/Aid/Settings)
    private int growthSubTab = 0; // 0=Expansion, 1=Aid, 2=Settings
    private boolean useBankMode = true;
    private boolean showBuyOrders = false; // false=Village Sells, true=Village Buys
    private int orderQtyPreset = 0; // 0=1x, 1=8x, 2=Max
    private int orderPage = 0;

    private boolean warehouseOutbound = false;
    private int warehousePage = 0;
    private boolean confirmTerminate = false;

    private int selectedWorkplaceIdx = 0;
    private boolean aidLargeSize = false;

    private final TyC2UiWidgets.CompactButton[] tabBtns = new TyC2UiWidgets.CompactButton[4];

    // Orders controls
    private TyC2UiWidgets.CompactButton payModeBtn;
    private TyC2UiWidgets.CompactButton sideFilterBtn;
    private TyC2UiWidgets.CompactButton qtyPresetBtn;
    private TyC2UiWidgets.CompactButton orderPrevBtn;
    private TyC2UiWidgets.CompactButton orderNextBtn;
    private final TyC2UiWidgets.CompactButton[] orderActionBtns = new TyC2UiWidgets.CompactButton[ORDERS_PER_PAGE];
    private final TyC2UiWidgets.CompactButton[] orderAutoBtns = new TyC2UiWidgets.CompactButton[ORDERS_PER_PAGE];

    // Warehouse controls
    private TyC2UiWidgets.CompactButton zoneToggleBtn;
    private EditBox searchBox;
    private TyC2UiWidgets.CompactButton whPrevBtn;
    private TyC2UiWidgets.CompactButton whNextBtn;
    private TyC2UiWidgets.CompactButton whDepositBtn;

    // Tab 3 sub-tab switcher (Expansion / Aid / Settings)
    private TyC2UiWidgets.CompactButton subExpBtn;
    private TyC2UiWidgets.CompactButton subAidBtn;
    private TyC2UiWidgets.CompactButton subSetBtn;

    // Tab 3.0: Expansion & Upgrade controls
    private TyC2UiWidgets.CompactButton upgradeBtn;
    private TyC2UiWidgets.CompactButton expDeliverBtn;
    private TyC2UiWidgets.CompactButton expAutoBtn;
    private TyC2UiWidgets.CompactButton expPauseBtn;

    // Tab 3.1: Aid Construction controls
    private TyC2UiWidgets.CompactButton aidProfPrevBtn;
    private TyC2UiWidgets.CompactButton aidProfNextBtn;
    private TyC2UiWidgets.CompactButton aidSizeBtn;
    private TyC2UiWidgets.CompactButton aidPayModeBtn;
    private TyC2UiWidgets.CompactButton aidStartBtn;
    private final TyC2UiWidgets.CompactButton[] aidCancelBtns = new TyC2UiWidgets.CompactButton[MAX_AID_ROWS];

    // Tab 3.2: Settings controls
    private EditBox renameBox;
    private TyC2UiWidgets.CompactButton renameSaveBtn;
    private TyC2UiWidgets.CompactButton relocateBtn;
    private TyC2UiWidgets.CompactButton terminateBtn;

    public CooperationHubScreen(CooperationHubMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 222;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();

        // Top 4 tabs seated cleanly inside tab rail (8,4,232,16), aligned with separators x=65,123,181
        String[] tabKeys = {
                "gui.tyc2.hub.tab.orders",
                "gui.tyc2.hub.tab.warehouse",
                "gui.tyc2.hub.tab.economy",
                "gui.tyc2.hub.tab.growth"
        };
        int[] tabXs = {9, 66, 124, 182};
        int[] tabWs = {56, 57, 57, 57};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tabBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + tabXs[i], topPos + 5, tabWs[i], 14,
                    Component.translatable(tabKeys[i]),
                    btn -> switchTab(idx),
                    ButtonTheme.HUB_TAB
            ));
        }

        // --- Tab 0: Orders controls (toolbar y=50..62) ---
        payModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 50, 48, 12, Component.translatable("gui.tyc2.hub.bank_short"),
                btn -> {
                    useBankMode = !useBankMode;
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        sideFilterBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 60, topPos + 50, 72, 12, Component.translatable("gui.tyc2.hub.sell_orders"),
                btn -> {
                    showBuyOrders = !showBuyOrders;
                    orderPage = 0;
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        qtyPresetBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 134, topPos + 50, 48, 12, Component.translatable("gui.tyc2.hub.qty_preset", 1),
                btn -> {
                    orderQtyPreset = (orderQtyPreset + 1) % 3;
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        orderPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 206, topPos + 50, 14, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    orderPage = Math.max(0, orderPage - 1);
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        orderNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 222, topPos + 50, 14, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    orderPage++;
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        for (int i = 0; i < ORDERS_PER_PAGE; i++) {
            final int row = i;
            int ry = topPos + 65 + i * 17;
            orderActionBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 181, ry, 54, 13, Component.translatable("gui.tyc2.hub.buy"),
                    btn -> executeOrderRow(row),
                    ButtonTheme.HUB_PRIMARY
            ));
            orderAutoBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 218, ry, 18, 13, Component.translatable("gui.tyc2.hub.btn.auto_tiny.off"),
                    btn -> toggleOrderAutoRow(row),
                    ButtonTheme.HUB_ACTION
            ));
        }

        // --- Tab 1: Warehouse controls (zero slot-count/index advertising) ---
        zoneToggleBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 50, 56, 12, Component.translatable("gui.tyc2.hub.zone.inbound"),
                btn -> {
                    warehouseOutbound = !warehouseOutbound;
                    warehousePage = 0;
                    sendWarehouseView();
                },
                ButtonTheme.HUB_ACTION
        ));
        searchBox = new EditBox(font, leftPos + 68, topPos + 50, 76, 12, Component.translatable("gui.tyc2.hub.search_placeholder"));
        searchBox.setMaxLength(32);
        searchBox.setResponder(s -> {
            warehousePage = 0;
            sendWarehouseView();
        });
        addRenderableWidget(searchBox);
        whPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 146, topPos + 50, 14, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    warehousePage = Math.max(0, warehousePage - 1);
                    sendWarehouseView();
                },
                ButtonTheme.HUB_ACTION
        ));
        whNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 188, topPos + 50, 14, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    if (sync != null && warehousePage + 1 < sync.totalPages) {
                        warehousePage++;
                        sendWarehouseView();
                    }
                },
                ButtonTheme.HUB_ACTION
        ));
        whDepositBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 204, topPos + 50, 32, 12, Component.translatable("gui.tyc2.hub.btn.put"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.WAREHOUSE_DEPOSIT_HELD
                )),
                ButtonTheme.HUB_PRIMARY
        ));

        // --- Tab 3: Sub-tabs (Expansion / Aid / Settings) at y=50..61 ---
        subExpBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 50, 74, 11, Component.translatable("gui.tyc2.hub.tab.expansion"),
                btn -> switchGrowthSubTab(0),
                ButtonTheme.HUB_SUBTAB
        ));
        subAidBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 86, topPos + 50, 75, 11, Component.translatable("gui.tyc2.hub.tab.aid"),
                btn -> switchGrowthSubTab(1),
                ButtonTheme.HUB_SUBTAB
        ));
        subSetBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 163, topPos + 50, 74, 11, Component.translatable("gui.tyc2.hub.tab.settings"),
                btn -> switchGrowthSubTab(2),
                ButtonTheme.HUB_SUBTAB
        ));

        // --- Tab 3.0: Expansion & Upgrade controls ---
        upgradeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 12, topPos + 83, 109, 12, Component.translatable("gui.tyc2.hub.upgrade_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.UPGRADE_VILLAGE
                )),
                ButtonTheme.HUB_PRIMARY
        ));
        expDeliverBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 12, topPos + 118, 39, 12, Component.translatable("gui.tyc2.hub.deliver"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.DELIVER_EXPANSION
                )),
                ButtonTheme.HUB_PRIMARY
        ));
        expAutoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 52, topPos + 118, 34, 12, Component.translatable("gui.tyc2.hub.auto_short.off"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_EXPANSION_AUTO
                )),
                ButtonTheme.HUB_ACTION
        ));
        expPauseBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 87, topPos + 118, 34, 12, Component.translatable("gui.tyc2.hub.pause_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_EXPANSION_PAUSE
                )),
                ButtonTheme.HUB_ACTION
        ));

        // --- Tab 3.1: Aid Construction controls ---
        aidProfPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 12, topPos + 65, 13, 14, Component.translatable("gui.tyc2.prev"),
                btn -> stepWorkplace(-1),
                ButtonTheme.HUB_ACTION
        ));
        aidProfNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 108, topPos + 65, 13, 14, Component.translatable("gui.tyc2.next"),
                btn -> stepWorkplace(1),
                ButtonTheme.HUB_ACTION
        ));
        aidSizeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 12, topPos + 82, 62, 12, Component.translatable("gui.tyc2.aid.size.small"),
                btn -> {
                    aidLargeSize = !aidLargeSize;
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        aidPayModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 76, topPos + 82, 45, 12, Component.translatable("gui.tyc2.hub.bank_short"),
                btn -> {
                    useBankMode = !useBankMode;
                    refreshWidgetVisibility();
                },
                ButtonTheme.HUB_ACTION
        ));
        aidStartBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 12, topPos + 116, 109, 14, Component.translatable("gui.tyc2.aid.start_btn"),
                btn -> sendStartAid(),
                ButtonTheme.HUB_PRIMARY
        ));
        for (int i = 0; i < MAX_AID_ROWS; i++) {
            final int row = i;
            aidCancelBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 203, topPos + 84 + i * 16, 33, 13, Component.translatable("gui.tyc2.aid.cancel_btn"),
                    btn -> sendCancelAid(row),
                    ButtonTheme.HUB_DANGER
            ));
        }

        // --- Tab 3.2: Settings controls ---
        renameBox = new EditBox(font, leftPos + 14, topPos + 77, 158, 13, Component.translatable("gui.tyc2.hub.rename_placeholder"));
        renameBox.setMaxLength(32);
        addRenderableWidget(renameBox);
        renameSaveBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 175, topPos + 77, 58, 13, Component.translatable("gui.tyc2.save"),
                btn -> TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                        menu.getHubPos(), C2SHubActionPacket.Action.RENAME_VILLAGE, 0, 0, false, renameBox.getValue()
                )),
                ButtonTheme.HUB_PRIMARY
        ));
        relocateBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 14, topPos + 97, 219, 14, Component.translatable("gui.tyc2.hub.relocate"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.RELOCATE_HUB
                )),
                ButtonTheme.HUB_ACTION
        ));
        terminateBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 14, topPos + 114, 219, 14, Component.translatable("gui.tyc2.hub.terminate"),
                btn -> {
                    if (!confirmTerminate) {
                        confirmTerminate = true;
                        refreshWidgetVisibility();
                    } else {
                        confirmTerminate = false;
                        TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                                menu.getHubPos(), C2SHubActionPacket.Action.TERMINATE_PARTNERSHIP
                        ));
                    }
                },
                ButtonTheme.HUB_DANGER
        ));

        refreshWidgetVisibility();
        if (menu.getLatestSync() != null) {
            onSync(menu.getLatestSync());
        }
        TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(menu.getHubPos(), C2SHubActionPacket.Action.REQUEST_SYNC));
    }

    private int ticksSinceSync = 0;
    private Component activeFeedbackComponent = Component.empty();
    private String activeFeedback = "";
    private int feedbackTtlTicks = 0;

    public void onSync(S2CHubSyncPacket pkt) {
        this.sync = pkt;
        this.ticksSinceSync = 0;
        if (!pkt.feedback.isEmpty()) {
            this.activeFeedbackComponent = pkt.feedbackComponent;
            this.activeFeedback = pkt.feedback;
            this.feedbackTtlTicks = 100;
        }
        this.menu.setLatestSync(pkt);
        this.warehouseOutbound = pkt.outboundZone;
        this.warehousePage = pkt.pageIndex;
        if (renameBox != null && !renameBox.isFocused() && renameBox.getValue().isEmpty()) {
            renameBox.setValue(pkt.villageName);
        }
        refreshWidgetVisibility();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (feedbackTtlTicks > 0) {
            feedbackTtlTicks--;
            if (feedbackTtlTicks == 0) {
                activeFeedbackComponent = Component.empty();
                activeFeedback = "";
            }
        }
        if (sync != null) {
            ticksSinceSync++;
            if (ticksSinceSync % 40 == 0) {
                TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.REQUEST_SYNC
                ));
            }
        }
    }

    private long liveRefreshRemaining() {
        return sync == null ? 0L : Math.max(0L, sync.refreshRemainingTicks - ticksSinceSync);
    }

    private void switchTab(int tab) {
        this.activeTab = tab;
        this.confirmTerminate = false;
        refreshWidgetVisibility();
        if (tab == 1) {
            sendWarehouseView();
        }
    }

    private void switchGrowthSubTab(int sub) {
        this.growthSubTab = sub;
        this.confirmTerminate = false;
        refreshWidgetVisibility();
    }

    private void stepWorkplace(int delta) {
        if (sync == null || sync.availableWorkplaces.isEmpty()) {
            return;
        }
        int n = sync.availableWorkplaces.size();
        selectedWorkplaceIdx = ((selectedWorkplaceIdx + delta) % n + n) % n;
        refreshWidgetVisibility();
    }

    @Nullable
    private S2CHubSyncPacket.WorkplaceDto currentWorkplace() {
        if (sync == null || sync.availableWorkplaces.isEmpty()) {
            return null;
        }
        int idx = Math.max(0, Math.min(selectedWorkplaceIdx, sync.availableWorkplaces.size() - 1));
        selectedWorkplaceIdx = idx;
        return sync.availableWorkplaces.get(idx);
    }

    private void sendStartAid() {
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        if (wp == null) {
            return;
        }
        TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                menu.getHubPos(),
                C2SHubActionPacket.Action.START_AID,
                aidLargeSize ? 1 : 0,
                0,
                useBankMode,
                wp.professionId()
        ));
    }

    private void sendCancelAid(int row) {
        if (sync == null || row < 0 || row >= sync.aidProjects.size()) {
            return;
        }
        S2CHubSyncPacket.AidProjectDto aid = sync.aidProjects.get(row);
        if (!aid.canCancel()) {
            return;
        }
        TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                menu.getHubPos(),
                C2SHubActionPacket.Action.CANCEL_AID,
                0,
                0,
                false,
                aid.projectId()
        ));
    }

    private void sendWarehouseView() {
        TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                menu.getHubPos(),
                C2SHubActionPacket.Action.SET_WAREHOUSE_VIEW,
                warehousePage,
                0,
                warehouseOutbound,
                searchBox == null ? "" : searchBox.getValue()
        ));
    }

    private List<S2CHubSyncPacket.OrderDto> filteredOrders() {
        List<S2CHubSyncPacket.OrderDto> out = new ArrayList<>();
        if (sync == null) {
            return out;
        }
        for (S2CHubSyncPacket.OrderDto o : sync.orders) {
            if (o.isVillageBuy() == showBuyOrders && o.remainingQty() > 0) {
                out.add(o);
            }
        }
        return out;
    }

    private void executeOrderRow(int row) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int idx = orderPage * ORDERS_PER_PAGE + row;
        if (idx < 0 || idx >= list.size()) {
            return;
        }
        S2CHubSyncPacket.OrderDto order = list.get(idx);
        int qty = switch (orderQtyPreset) {
            case 0 -> 1;
            case 1 -> Math.min(8, order.remainingQty());
            default -> order.remainingQty();
        };
        C2SHubActionPacket.Action act = order.isVillageBuy()
                ? C2SHubActionPacket.Action.SELL_TO_VILLAGE
                : C2SHubActionPacket.Action.BUY_FROM_VILLAGE;
        TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                menu.getHubPos(), act, qty, 0, useBankMode, order.orderId()
        ));
    }

    private void toggleOrderAutoRow(int row) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int idx = orderPage * ORDERS_PER_PAGE + row;
        if (idx < 0 || idx >= list.size()) {
            return;
        }
        TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_ORDER_AUTO, 0, 0, false, list.get(idx).orderId()
        ));
    }

    private void refreshWidgetVisibility() {
        for (int i = 0; i < 4; i++) {
            tabBtns[i].setHighlighted(i == activeTab);
        }

        // Tab 0: Orders
        boolean t0 = activeTab == 0;
        List<S2CHubSyncPacket.OrderDto> orders = filteredOrders();
        int maxOrderPage = Math.max(0, (orders.size() - 1) / ORDERS_PER_PAGE);
        orderPage = Math.min(orderPage, maxOrderPage);

        payModeBtn.visible = t0;
        payModeBtn.setMessage(Component.translatable(useBankMode ? "gui.tyc2.hub.bank_short" : "gui.tyc2.hub.cash_short"));
        payModeBtn.setHighlighted(useBankMode);
        payModeBtn.setAccentColor(useBankMode ? 0xFF21527D : 0xFF7D531B);

        sideFilterBtn.visible = t0;
        sideFilterBtn.setMessage(Component.translatable(showBuyOrders ? "gui.tyc2.hub.buy_orders" : "gui.tyc2.hub.sell_orders"));
        sideFilterBtn.setHighlighted(true);
        sideFilterBtn.setAccentColor(showBuyOrders ? 0xFF8C5318 : 0xFF1F4F80);

        qtyPresetBtn.visible = t0;
        qtyPresetBtn.setMessage(switch (orderQtyPreset) {
            case 0 -> Component.translatable("gui.tyc2.hub.qty_preset", 1);
            case 1 -> Component.translatable("gui.tyc2.hub.qty_preset", 8);
            default -> Component.translatable("gui.tyc2.hub.qty_preset", Component.translatable("gui.tyc2.hub.qty_all"));
        });

        orderPrevBtn.visible = t0;
        orderNextBtn.visible = t0;
        orderPrevBtn.active = orderPage > 0;
        orderNextBtn.active = orderPage < maxOrderPage;

        for (int i = 0; i < ORDERS_PER_PAGE; i++) {
            int idx = orderPage * ORDERS_PER_PAGE + i;
            boolean has = t0 && idx < orders.size();
            boolean isBuy = has && orders.get(idx).isVillageBuy();
            orderActionBtns[i].visible = has;
            orderAutoBtns[i].visible = isBuy;
            if (has) {
                S2CHubSyncPacket.OrderDto o = orders.get(idx);
                // Expand Buy CTA when Auto button is absent (Sell orders); keep live QA hit point (190, 72) covered!
                orderActionBtns[i].x = leftPos + (isBuy ? 180 : 181);
                orderActionBtns[i].setWidth(isBuy ? 36 : 54);
                orderActionBtns[i].setMessage(Component.translatable(
                        isBuy ? "gui.tyc2.hub.deliver" : "gui.tyc2.hub.buy"
                ));
                orderActionBtns[i].setAccentColor(isBuy ? 0xFF945817 : 0xFF1F6340);

                if (isBuy) {
                    orderAutoBtns[i].setHighlighted(o.autoFulfill());
                    if (o.autoFulfill()) {
                        orderAutoBtns[i].setAccentColor(0xFF1F6E42);
                    } else {
                        orderAutoBtns[i].clearAccentColor();
                    }
                    orderAutoBtns[i].setMessage(Component.translatable(
                            o.autoFulfill() ? "gui.tyc2.hub.btn.auto_tiny.on" : "gui.tyc2.hub.btn.auto_tiny.off"
                    ));
                }
            }
        }

        // Tab 1: Warehouse
        boolean t1 = activeTab == 1;
        zoneToggleBtn.visible = t1;
        zoneToggleBtn.setMessage(Component.translatable(
                warehouseOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
        ));
        zoneToggleBtn.setHighlighted(true);
        zoneToggleBtn.setAccentColor(warehouseOutbound ? 0xFF945518 : 0xFF1F4F80);
        searchBox.visible = t1;
        whPrevBtn.visible = t1;
        whNextBtn.visible = t1;
        whPrevBtn.active = warehousePage > 0;
        whNextBtn.active = sync != null && warehousePage + 1 < sync.totalPages;
        whDepositBtn.visible = t1 && !warehouseOutbound;

        // Tab 3 sub-tabs (0=Expansion, 1=Aid, 2=Settings)
        boolean t3 = activeTab == 3;
        boolean t3Exp = t3 && growthSubTab == 0;
        boolean t3Aid = t3 && growthSubTab == 1;
        boolean t3Set = t3 && growthSubTab == 2;
        boolean canManage = sync != null && sync.canManage;

        subExpBtn.visible = t3;
        subExpBtn.setHighlighted(t3Exp);
        subAidBtn.visible = t3;
        subAidBtn.setHighlighted(t3Aid);
        subSetBtn.visible = t3;
        subSetBtn.setHighlighted(t3Set);

        // Tab 3.0: Expansion
        upgradeBtn.visible = t3Exp;
        upgradeBtn.active = sync != null && sync.canUpgrade;
        expDeliverBtn.visible = t3Exp;
        expAutoBtn.visible = t3Exp && sync != null && sync.hasExpansion;
        if (sync != null && sync.hasExpansion) {
            expAutoBtn.setHighlighted(sync.expAutoFulfill);
            if (sync.expAutoFulfill) {
                expAutoBtn.setAccentColor(0xFF1F6B40);
            } else {
                expAutoBtn.clearAccentColor();
            }
            expAutoBtn.setMessage(Component.translatable(
                    sync.expAutoFulfill ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        expPauseBtn.visible = t3Exp;
        if (sync != null) {
            expPauseBtn.setHighlighted(sync.expansionPaused);
            if (sync.expansionPaused) {
                expPauseBtn.setAccentColor(0xFF8C5318);
            } else {
                expPauseBtn.clearAccentColor();
            }
            expPauseBtn.setMessage(Component.translatable(
                    sync.expansionPaused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
        }

        // Tab 3.1: Aid Construction
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        boolean hasWps = sync != null && !sync.availableWorkplaces.isEmpty();
        aidProfPrevBtn.visible = t3Aid;
        aidProfPrevBtn.active = hasWps && sync.availableWorkplaces.size() > 1;
        aidProfNextBtn.visible = t3Aid;
        aidProfNextBtn.active = hasWps && sync.availableWorkplaces.size() > 1;
        aidSizeBtn.visible = t3Aid;
        if (wp != null) {
            if (aidLargeSize) {
                aidSizeBtn.setMessage(wp.largeUnlocked()
                        ? Component.translatable("gui.tyc2.aid.size.large")
                        : Component.translatable("gui.tyc2.aid.size.large_locked", wp.minLevelLarge()));
            } else {
                aidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.small"));
            }
            aidSizeBtn.setHighlighted(aidLargeSize && wp.largeUnlocked());
        } else {
            aidSizeBtn.setMessage(Component.translatable("gui.tyc2.aid.size.small"));
            aidSizeBtn.setHighlighted(false);
        }
        aidPayModeBtn.visible = t3Aid;
        aidPayModeBtn.setMessage(Component.translatable(useBankMode ? "gui.tyc2.hub.bank_short" : "gui.tyc2.hub.cash_short"));
        aidPayModeBtn.setHighlighted(useBankMode);
        aidPayModeBtn.setAccentColor(useBankMode ? 0xFF21527D : 0xFF7D531B);

        boolean sizeUnlocked = wp != null && (aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked());
        boolean queueOpen = sync != null && sync.aidProjects.size() < sync.maxQueuedAid;
        aidStartBtn.visible = t3Aid;
        aidStartBtn.active = t3Aid && wp != null && sizeUnlocked && !wp.aidLimitReached() && queueOpen && !sync.depopulatedPaused;

        for (int i = 0; i < MAX_AID_ROWS; i++) {
            boolean showCancel = t3Aid && sync != null && i < sync.aidProjects.size()
                    && sync.aidProjects.get(i).canCancel();
            aidCancelBtns[i].visible = showCancel;
            aidCancelBtns[i].active = showCancel;
        }

        // Tab 3.2: Settings
        renameBox.visible = t3Set;
        renameSaveBtn.visible = t3Set;
        renameSaveBtn.active = canManage;
        relocateBtn.visible = t3Set;
        relocateBtn.active = canManage;
        terminateBtn.visible = t3Set;
        terminateBtn.active = canManage;
        terminateBtn.setHighlighted(confirmTerminate);
        terminateBtn.setMessage(Component.translatable(
                confirmTerminate ? "gui.tyc2.hub.terminate_confirm" : "gui.tyc2.hub.terminate"
        ));
    }

    private void bindTexture() {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        bindTexture();
        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Banner LCD left emblem: Mayor Crown (212,226,16x16)
        blit(poseStack, leftPos + 12, topPos + 25, TyC2UiWidgets.HUB_MAYOR_CROWN_U, TyC2UiWidgets.HUB_BADGE_V, 16, 16);

        if (sync == null) {
            return;
        }

        // Banner LCD: Biome 10x10 emblem (biomeU, 244)
        int biomeU = TyC2UiWidgets.hubBiomeU(sync.biomeStyleId);
        blit(poseStack, leftPos + 126, topPos + 23, biomeU, TyC2UiWidgets.HUB_BIOME_V, 10, 10);

        // Banner LCD: Weekly Calendar 12x12 icon (232, 226)
        blit(poseStack, leftPos + 121, topPos + 33, TyC2UiWidgets.HUB_CALENDAR_U, TyC2UiWidgets.HUB_BADGE_V, 12, 12);

        // Banner LCD: Right status icon cluster (Season 12x12, CPI arrow 10x10, Chunk-Loader 16x16)
        int seasonU = TyC2UiWidgets.hubSeasonU(sync.seasonKey);
        if (seasonU >= 0) {
            blit(poseStack, leftPos + 193, topPos + 27, seasonU, TyC2UiWidgets.HUB_SEASON_V, 12, 12);
        }
        int cpiU = sync.cpi > 1.08
                ? TyC2UiWidgets.HUB_CPI_UP_U
                : (sync.cpi < 0.92 ? TyC2UiWidgets.HUB_CPI_DOWN_U : TyC2UiWidgets.HUB_CPI_STABLE_U);
        blit(poseStack, leftPos + 208, topPos + 28, cpiU, TyC2UiWidgets.HUB_CPI_V, 10, 10);

        if (sync.chunkLoaderActive) {
            blit(poseStack, leftPos + 221, topPos + 25, TyC2UiWidgets.HUB_CHUNK_LOADER_U, TyC2UiWidgets.HUB_BADGE_V, 16, 16);
        } else {
            // Subtle dim socket showing chunk loader slot inactive
            fill(poseStack, leftPos + 222, topPos + 26, leftPos + 236, topPos + 40, 0xFF1A2433);
            fill(poseStack, leftPos + 223, topPos + 27, leftPos + 235, topPos + 39, 0xFF0E1621);
            bindTexture();
        }

        // Viewport tab-specific chrome (drawn beneath buttons & labels!)
        switch (activeTab) {
            case 0 -> renderOrdersChrome(poseStack);
            case 1 -> renderWarehouseChrome(poseStack);
            case 2 -> renderEconomyChrome(poseStack);
            case 3 -> renderGrowthChrome(poseStack);
        }
    }

    private void renderOrdersChrome(PoseStack poseStack) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
            S2CHubSyncPacket.OrderDto o = list.get(start + i);
            int cardX = leftPos + 10;
            int cardY = topPos + 64 + i * 17;
            int cardW = 227;
            int cardH = 16;

            int borderCol = o.isVillageBuy() ? 0xFF3E3524 : 0xFF233952;
            int bgCol = (i % 2 == 0) ? 0xFF162232 : 0xFF131E2D;
            int accentCol = o.isVillageBuy() ? 0xFFF0A43A : 0xFF3B96FF;

            fill(poseStack, cardX, cardY, cardX + cardW, cardY + cardH, borderCol);
            fill(poseStack, cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1, bgCol);
            // Left 2px accent strip
            fill(poseStack, cardX, cardY, cardX + 2, cardY + cardH, accentCol);

            // Inline 2px bottom progress bar along card bottom
            int barX = cardX + 2;
            int barW = cardW - 3;
            int barY = cardY + cardH - 2;
            fill(poseStack, barX, barY, barX + barW, barY + 2, 0xFF0B121C);
            if (o.totalQty() > 0 && o.fulfilledQty() > 0) {
                int fillW = Math.min(barW, Math.max(2, (barW * o.fulfilledQty()) / o.totalQty()));
                fill(poseStack, barX, barY, barX + fillW, barY + 2, accentCol);
            }

            // Order specialty/seasonal tag chip backdrops + 8x8 gem sprites
            boolean hasBiome = o.biomeSpecialty();
            boolean hasSeason = o.seasonalBonus();
            if (hasBiome && hasSeason) {
                drawOrderTagChipBg(poseStack, leftPos + 79, cardY + 2, 22, true, o.isVillageBuy());
                drawOrderTagChipBg(poseStack, leftPos + 102, cardY + 2, 22, false, o.isVillageBuy());
                bindTexture();
                blit(poseStack, leftPos + 80, cardY + 3, TyC2UiWidgets.HUB_TAG_BIOME_U, TyC2UiWidgets.HUB_TAG_V, 8, 8);
                blit(poseStack, leftPos + 103, cardY + 3, TyC2UiWidgets.HUB_TAG_SEASON_U, TyC2UiWidgets.HUB_TAG_V, 8, 8);
            } else if (hasBiome) {
                drawOrderTagChipBg(poseStack, leftPos + 94, cardY + 2, 30, true, o.isVillageBuy());
                bindTexture();
                blit(poseStack, leftPos + 95, cardY + 3, TyC2UiWidgets.HUB_TAG_BIOME_U, TyC2UiWidgets.HUB_TAG_V, 8, 8);
            } else if (hasSeason) {
                drawOrderTagChipBg(poseStack, leftPos + 94, cardY + 2, 30, false, o.isVillageBuy());
                bindTexture();
                blit(poseStack, leftPos + 95, cardY + 3, TyC2UiWidgets.HUB_TAG_SEASON_U, TyC2UiWidgets.HUB_TAG_V, 8, 8);
            }
        }
    }

    private void drawOrderTagChipBg(PoseStack poseStack, int x, int y, int w, boolean biomeTag, boolean isBuy) {
        int border = biomeTag
                ? (isBuy ? 0xFFB88028 : 0xFF2D8C54)
                : (isBuy ? 0xFFC47A24 : 0xFF2A8EA8);
        int fillCol = biomeTag
                ? (isBuy ? 0xFF2E2212 : 0xFF132A1E)
                : (isBuy ? 0xFF302014 : 0xFF122833);
        fill(poseStack, x, y, x + w, y + 10, border);
        fill(poseStack, x + 1, y + 1, x + w - 1, y + 9, fillCol);
    }

    private void renderWarehouseChrome(PoseStack poseStack) {
        bindTexture();
        int slotU = warehouseOutbound ? TyC2UiWidgets.HUB_SLOT_OUTBOUND_U : TyC2UiWidgets.HUB_SLOT_INBOUND_U;
        for (int r = 0; r < WH_ROWS; r++) {
            for (int c = 0; c < WH_COLS; c++) {
                blit(poseStack, leftPos + WH_GRID_X + c * 18, topPos + WH_GRID_Y + r * 18,
                        slotU, TyC2UiWidgets.HUB_SLOT_V, 18, 18);
            }
        }
    }

    private void renderEconomyChrome(PoseStack poseStack) {
        // Left dark ledger KPI card (10,50,94x82)
        int cx = leftPos + 10;
        int cy = topPos + 50;
        fill(poseStack, cx, cy, cx + 94, cy + 82, 0xFF3A3122);
        fill(poseStack, cx + 1, cy + 1, cx + 93, cy + 81, 0xFF141F2E);
        // Subtle horizontal dividers between KPI groups
        fill(poseStack, cx + 3, cy + 14, cx + 91, cy + 15, 0xFF25354A);
        fill(poseStack, cx + 3, cy + 36, cx + 91, cy + 37, 0xFF25354A);
        fill(poseStack, cx + 3, cy + 58, cx + 91, cy + 59, 0xFF25354A);
    }

    private void renderGrowthChrome(PoseStack poseStack) {
        if (growthSubTab == 0) {
            // Two ledger cards: Left Upgrade/Expansion (10,63,113x69), Right Telemetry (125,63,112x69)
            fill(poseStack, leftPos + 10, topPos + 63, leftPos + 123, topPos + 132, 0xFF3A3122);
            fill(poseStack, leftPos + 11, topPos + 64, leftPos + 122, topPos + 131, 0xFF141F2E);
            fill(poseStack, leftPos + 13, topPos + 97, leftPos + 120, topPos + 98, 0xFF26374C);

            fill(poseStack, leftPos + 125, topPos + 63, leftPos + 237, topPos + 132, 0xFF3A3122);
            fill(poseStack, leftPos + 126, topPos + 64, leftPos + 236, topPos + 131, 0xFF141F2E);
            fill(poseStack, leftPos + 128, topPos + 102, leftPos + 234, topPos + 103, 0xFF26374C);

            // Render biome 10x10 emblem inside right telemetry card
            bindTexture();
            int biomeU = TyC2UiWidgets.hubBiomeU(sync.biomeStyleId);
            blit(poseStack, leftPos + 129, topPos + 74, biomeU, TyC2UiWidgets.HUB_BIOME_V, 10, 10);
        } else if (growthSubTab == 1) {
            // Left Aid Picker card (10,63,113x69), Right Aid Queue card (125,63,112x69)
            fill(poseStack, leftPos + 10, topPos + 63, leftPos + 123, topPos + 132, 0xFF3A3122);
            fill(poseStack, leftPos + 11, topPos + 64, leftPos + 122, topPos + 131, 0xFF141F2E);

            fill(poseStack, leftPos + 125, topPos + 63, leftPos + 237, topPos + 132, 0xFF3A3122);
            fill(poseStack, leftPos + 126, topPos + 64, leftPos + 236, topPos + 131, 0xFF141F2E);
        } else {
            // Settings full-width card (10,63,227x69)
            fill(poseStack, leftPos + 10, topPos + 63, leftPos + 237, topPos + 132, 0xFF3A3122);
            fill(poseStack, leftPos + 11, topPos + 64, leftPos + 236, topPos + 131, 0xFF141F2E);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        if (sync == null) {
            return;
        }

        // --- Banner LCD (8,21,232x25 -> interior 11..238, 22..44) ---
        // Row 1 (y=23..32): Golden Lv.X pill + Village Name + Biome badge (beside 10x10 emblem at x=126)
        TyC2UiWidgets.drawPillBadge(
                poseStack, font, 31, 23, 24, 10,
                0xFF3C2B0D, 0xFFE0B038,
                "Lv." + sync.level, 0xFFE87A
        );
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, sync.villageName, 57, 25, 67, 0.90F, 0.56F, 0xFFF4D4, true
        );
        String biomeName = Component.translatable("biome.tyc2." + sync.biomeStyleId).getString();
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, biomeName, 138, 25, 51, 0.82F, 0.54F,
                TyC2UiWidgets.biomeTextColor(sync.biomeStyleId), false
        );

        // Row 2 (y=34..43): Population + Funds | Weekly Calendar Countdown | Unemployment indicator
        String popFunds = Component.translatable("gui.tyc2.hub.population", sync.population).getString()
                + " · " + TyC2UiWidgets.compactIc(sync.villageBalance) + " IC";
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, popFunds, 31, 35, 88, 0.80F, 0.52F, 0x9EE8D8, false
        );
        String countdown = TyC2UiWidgets.formatRefreshCountdown(liveRefreshRemaining());
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, countdown, 134, 35, 32, 0.82F, 0.48F, 0xFFE27A, false
        );
        String unempShort = sync.unemploymentPct + "%" + (sync.employmentBonusActive ? "\u2605" : "");
        TyC2UiWidgets.drawRightFittedString(
                poseStack, font, unempShort, 189, 35, 22, 0.78F, 0.52F,
                sync.employmentBonusActive ? 0x66FF99 : 0xFFCC66
        );

        // --- Status LCD (179,166,61x49 -> interior 180..238, 169..213) ---
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.bank", TyC2UiWidgets.compactIc(sync.activeBankBalance)).getString(),
                182, 170, 55, 0.82F, 0.56F, 0x6EF29E, false);
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.inbound", sync.inboundUsed).getString(),
                182, 179, 55, 0.82F, 0.56F, 0x7CC2FF, false);
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.outbound", sync.outboundUsed).getString(),
                182, 188, 55, 0.82F, 0.56F, 0xFFBD6B, false);
        GuiComponent.fill(poseStack, 181, 197, 238, 198, 0xFF28384D);

        if (!activeFeedback.isEmpty()) {
            List<FormattedCharSequence> fbLines = font.split(activeFeedbackComponent, 110);
            if (fbLines.size() <= 1) {
                TyC2UiWidgets.drawFittedStringScaled(poseStack, font, activeFeedback, 182, 201, 55, 0.76F, 0.44F, 0xFFE27A, false);
            } else {
                poseStack.pushPose();
                poseStack.translate(182.0F, 199.0F, 0.0F);
                poseStack.scale(0.50F, 0.50F, 1.0F);
                for (int i = 0; i < Math.min(2, fbLines.size()); i++) {
                    font.draw(poseStack, fbLines.get(i), 0, i * 11, 0xFFE27A);
                }
                poseStack.popPose();
            }
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable(sync.chunkLoaderActive ? "gui.tyc2.hub.status.cl_on" : "gui.tyc2.hub.status.cl_off").getString(),
                    182, 201, 55, 0.80F, 0.56F, sync.chunkLoaderActive ? 0x66FF99 : 0x889EB3, false);
        }

        // --- Viewport content (8,48,232x87) ---
        switch (activeTab) {
            case 0 -> renderOrdersLabels(poseStack);
            case 1 -> renderWarehouseLabels(poseStack);
            case 2 -> renderEconomyTab(poseStack);
            case 3 -> renderGrowthLabels(poseStack);
        }
    }

    private void renderOrdersLabels(PoseStack poseStack) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int maxPage = Math.max(1, (list.size() + ORDERS_PER_PAGE - 1) / ORDERS_PER_PAGE);
        String pageStr = (orderPage + 1) + "/" + maxPage;
        TyC2UiWidgets.drawCenteredFittedString(poseStack, font, pageStr, 183, 52, 22, 0.80F, 0.55F, 0xC8D8E8);

        if (list.isEmpty()) {
            TyC2UiWidgets.drawCenteredFittedString(
                    poseStack, font,
                    Component.translatable(sync.depopulatedPaused
                            ? "gui.tyc2.hub.paused_depopulated"
                            : "gui.tyc2.hub.no_orders").getString(),
                    14, 86, 220, 0.90F, 0.60F, 0xFFAA66
            );
            return;
        }
        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
            S2CHubSyncPacket.OrderDto o = list.get(start + i);
            int cardY = 64 + i * 17;
            String itemName = TyC2Ids.localizedItemName(o.itemId(), o.enchantSpec());

            boolean hasBiome = o.biomeSpecialty();
            boolean hasSeason = o.seasonalBonus();
            int nameMaxW = (hasBiome && hasSeason) ? 47 : ((hasBiome || hasSeason) ? 62 : 94);
            int nameCol = hasSeason ? 0xFFF08A : (hasBiome ? 0xD8FFE6 : 0xEEF4FA);
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, itemName, 30, cardY + 4, nameMaxW, 0.82F, 0.52F, nameCol, false);

            // Render crisp tag label next to 8x8 gem sprite
            if (hasBiome && hasSeason) {
                String bTag = Component.translatable(o.isVillageBuy()
                        ? "gui.tyc2.order.tag.biome_buy" : "gui.tyc2.order.tag.biome_sell").getString();
                String sTag = Component.translatable(o.isVillageBuy()
                        ? "gui.tyc2.order.tag.season_buy" : "gui.tyc2.order.tag.season_sell").getString();
                TyC2UiWidgets.drawFittedStringScaled(poseStack, font, bTag, 89, cardY + 4, 11, 0.62F, 0.44F,
                        o.isVillageBuy() ? 0xFFD270 : 0x82F2A8, false);
                TyC2UiWidgets.drawFittedStringScaled(poseStack, font, sTag, 112, cardY + 4, 11, 0.62F, 0.44F,
                        o.isVillageBuy() ? 0xFFCC70 : 0x80ECFF, false);
            } else if (hasBiome) {
                String bTag = Component.translatable(o.isVillageBuy()
                        ? "gui.tyc2.order.tag.biome_buy" : "gui.tyc2.order.tag.biome_sell").getString();
                TyC2UiWidgets.drawFittedStringScaled(poseStack, font, bTag, 104, cardY + 4, 19, 0.72F, 0.48F,
                        o.isVillageBuy() ? 0xFFD270 : 0x82F2A8, false);
            } else if (hasSeason) {
                String sTag = Component.translatable(o.isVillageBuy()
                        ? "gui.tyc2.order.tag.season_buy" : "gui.tyc2.order.tag.season_sell").getString();
                TyC2UiWidgets.drawFittedStringScaled(poseStack, font, sTag, 104, cardY + 4, 19, 0.72F, 0.48F,
                        o.isVillageBuy() ? 0xFFCC70 : 0x80ECFF, false);
            }

            // Progress & unit price
            String qtyStr = o.fulfilledQty() + "/" + o.totalQty();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, qtyStr, 126, cardY + 4, 25, 0.76F, 0.52F, 0xAAD8F2, false);
            String priceStr = TyC2UiWidgets.compactIc(o.unitPrice()) + " IC";
            TyC2UiWidgets.drawRightFittedString(poseStack, font, priceStr, 178, cardY + 4, 26, 0.76F, 0.52F, 0x6EF29E);
        }
    }

    private void renderWarehouseLabels(PoseStack poseStack) {
        String pg = (warehousePage + 1) + "/" + Math.max(1, sync.totalPages);
        TyC2UiWidgets.drawCenteredFittedString(poseStack, font, pg, 161, 52, 26, 0.85F, 0.55F, 0xDDEEFF);

        int used = warehouseOutbound ? sync.outboundUsed : sync.inboundUsed;
        String zoneSummary = Component.translatable(
                warehouseOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
        ).getString() + " · " + Component.translatable("gui.tyc2.hub.zone.used", used).getString();
        TyC2UiWidgets.drawCenteredFittedString(
                poseStack, font, zoneSummary, 16, 123, 216, 0.80F, 0.55F,
                warehouseOutbound ? 0xFFBD6B : 0x7CC2FF
        );
    }

    private void renderEconomyTab(PoseStack poseStack) {
        int col = sync.cpi > 1.08 ? 0xFF7A5A : (sync.cpi < 0.92 ? 0x5AE2FF : 0x66F298);
        String regimeKey = switch (sync.inflationState) {
            case "DEFLATION" -> "gui.tyc2.economy.deflation";
            case "STABLE" -> "gui.tyc2.economy.stable";
            default -> "gui.tyc2.economy.inflation";
        };
        String cpiHead = Component.translatable("gui.tyc2.hub.status.cpi", String.format(Locale.ROOT, "%.2f", sync.cpi)).getString()
                + " · " + Component.translatable(regimeKey).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, cpiHead, 13, 53, 88, 0.80F, 0.50F, col, false);

        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.economy.money_short", TyC2UiWidgets.compactIc(sync.moneySupply)).getString(),
                13, 67, 88, 0.78F, 0.50F, 0xFFE07A, false);
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.economy.goods_short", TyC2UiWidgets.compactIc(Math.round(sync.goodsCirculation))).getString(),
                13, 77, 88, 0.78F, 0.50F, 0x82EED2, false);

        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.economy.season", Component.translatable("season.tyc2." + sync.seasonKey)).getString(),
                13, 89, 88, 0.78F, 0.50F, 0xAAD8F2, false);
        int clampedTier = Math.max(0, Math.min(5, sync.playerTierLevel));
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("tier.tyc2." + clampedTier).getString(),
                13, 99, 88, 0.74F, 0.48F, 0xD2DAE4, false);

        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.unemployment", sync.unemploymentPct, sync.unemployedCount, sync.workforceCount).getString(),
                13, 111, 88, 0.72F, 0.48F, sync.employmentBonusActive ? 0x66F298 : 0xFFCC66, false);
        String bonusLine = sync.employmentBonusActive
                ? Component.translatable("gui.tyc2.hub.unemployment_bonus", sync.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, bonusLine,
                13, 121, 88, 0.66F, 0.44F, sync.employmentBonusActive ? 0x66FF99 : 0x8CA0B5, false);

        TyC2UiWidgets.drawCpiLineChart(poseStack, font, 107, 50, 130, 82, sync.cpiHistory, false);
    }

    private void renderGrowthLabels(PoseStack poseStack) {
        switch (growthSubTab) {
            case 0 -> renderExpansionSubTab(poseStack);
            case 1 -> renderAidSubTab(poseStack);
            case 2 -> renderSettingsSubTab(poseStack);
        }
    }

    private void renderExpansionSubTab(PoseStack poseStack) {
        // Left card: Upgrade (y=66..95) + Expansion (y=99..130)
        if (sync.level >= 10) {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.hub.max_level").getString(), 13, 68, 106, 0.82F, 0.56F, 0x66F298, false);
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.hub.upgrade_cost", sync.nextSinkCost).getString(),
                    13, 66, 106, 0.78F, 0.52F, 0xFFE070, false);
            String req = Component.translatable(
                    "gui.tyc2.hub.upgrade_req",
                    sync.population + "/" + sync.nextMinPop,
                    TyC2UiWidgets.compactIc(sync.cumulativeVolume) + "/" + TyC2UiWidgets.compactIc(sync.nextMinVol),
                    TyC2UiWidgets.compactIc(sync.villageBalance) + "/" + TyC2UiWidgets.compactIc(sync.nextMinBal)
            ).getString();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, req, 13, 74, 106, 0.68F, 0.44F, 0xAAD8F2, false);
        }

        if (sync.hasJob) {
            int pct = sync.jobTotal > 0 ? (sync.jobPlaced * 100) / sync.jobTotal : 0;
            String msg = Component.translatable(
                    "gui.tyc2.expansion.building",
                    Component.translatable("project.tyc2." + sync.jobProjectKey),
                    pct
            ).getString();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, msg, 13, 103, 106, 0.76F, 0.52F, 0x66F298, false);
        } else if (sync.hasExpansion) {
            String proj = Component.translatable(
                    "gui.tyc2.expansion.project",
                    Component.translatable("project.tyc2." + sync.expProjectKey)
            ).getString();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, proj, 13, 99, 106, 0.72F, 0.48F, 0xFFE07A, false);
            renderMaterialLines(poseStack, sync.expMaterials, 13, 106, 112, 106);
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.none").getString(), 13, 104, 106, 0.76F, 0.52F, 0x99AEBF, false);
        }

        // Right telemetry card (128..234, y=66..129)
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.trade_volume", TyC2UiWidgets.compactIc(sync.cumulativeVolume)).getString(),
                129, 66, 104, 0.76F, 0.52F, 0xAAD8F2, false);
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("biome.tyc2." + sync.biomeStyleId).getString(),
                142, 76, 91, 0.78F, 0.54F, TyC2UiWidgets.biomeTextColor(sync.biomeStyleId), false);
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.unemployment", sync.unemploymentPct, sync.unemployedCount, sync.workforceCount).getString(),
                129, 86, 104, 0.72F, 0.48F, sync.employmentBonusActive ? 0x66F298 : 0xFFCC66, false);
        String empDetail = sync.employmentBonusActive
                ? Component.translatable("gui.tyc2.hub.unemployment_bonus", sync.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, empDetail,
                129, 94, 104, 0.64F, 0.44F, sync.employmentBonusActive ? 0x66FF99 : 0x8CA0B5, false);

        if (!sync.nearbyLc2Stations.isEmpty()) {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", sync.nearbyLc2Stations.size()).getString(),
                    129, 105, 104, 0.74F, 0.52F, 0x5CEEFF, false);
            for (int i = 0; i < Math.min(2, sync.nearbyLc2Stations.size()); i++) {
                String label = Lc2SoftBridge.formatStationLabel(sync.nearbyLc2Stations.get(i));
                TyC2UiWidgets.drawFittedStringScaled(poseStack, font, label, 129, 114 + i * 8, 104, 0.68F, 0.46F, 0xAAD8F2, false);
            }
        } else if (sync.hasExpansion) {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.budget", sync.expBudget).getString(),
                    129, 106, 104, 0.74F, 0.50F, 0x82EED2, false);
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.hub.refresh_label",
                            TyC2UiWidgets.formatRefreshCountdown(liveRefreshRemaining())).getString(),
                    129, 117, 104, 0.74F, 0.50F, 0xFFE27A, false);
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.hub.refresh_label",
                            TyC2UiWidgets.formatRefreshCountdown(liveRefreshRemaining())).getString(),
                    129, 107, 104, 0.76F, 0.52F, 0xFFE27A, false);
        }
    }

    private void renderMaterialLines(
            PoseStack poseStack, List<S2CHubSyncPacket.MatDto> mats, int x, int y1, int y2, int maxW
    ) {
        int matCount = mats.size();
        int splitIdx = matCount <= 2 ? matCount : (matCount + 1) / 2;
        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        boolean line1AllMet = true;
        boolean line2AllMet = true;
        for (int i = 0; i < matCount; i++) {
            S2CHubSyncPacket.MatDto m = mats.get(i);
            boolean met = m.delivered() >= m.required();
            StringBuilder dst = i < splitIdx ? line1 : line2;
            if (i < splitIdx) {
                line1AllMet &= met;
            } else {
                line2AllMet &= met;
            }
            if (dst.length() > 0) {
                dst.append(' ');
            }
            dst.append(TyC2Ids.localizedItemName(m.itemId(), null))
                    .append(':')
                    .append(m.delivered())
                    .append('/')
                    .append(m.required());
        }
        if (line1.length() > 0) {
            TyC2UiWidgets.drawFittedStringScaled(
                    poseStack, font, line1.toString(), x, y1, maxW, 0.66F, 0.44F,
                    line1AllMet ? 0x66FF99 : 0xFFCC66, false
            );
        }
        if (line2.length() > 0) {
            TyC2UiWidgets.drawFittedStringScaled(
                    poseStack, font, line2.toString(), x, y2, maxW, 0.66F, 0.44F,
                    line2AllMet ? 0x66FF99 : 0xFFCC66, false
            );
        }
    }

    private void renderAidSubTab(PoseStack poseStack) {
        // Left card: Profession picker, live CPI quote & gather speed
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        if (wp != null) {
            String profName = TyC2UiWidgets.localizedProfessionName(
                    wp.professionId(), wp.archetypeName(), wp.displayNameFallback()
            );
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, profName, 45, 68, 61, 0.80F, 0.52F, 0xFFE888, false);
            long cost = aidLargeSize ? wp.costLargeIc() : wp.costSmallIc();
            boolean unlocked = aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.aid.cost", cost).getString(),
                    13, 97, 106, 0.78F, 0.52F, unlocked ? 0x66F298 : 0xFF8866, false);
        }
        String gatherLine = Component.translatable(
                "gui.tyc2.aid.gather_rate",
                String.format(Locale.ROOT, "%.2f", sync.gatherSpeedMult),
                sync.unemployedCount
        ).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, gatherLine, 13, 106, 106, 0.68F, 0.46F, 0x82EED2, false);

        // Right card: Queue header, low-unemployment bonus & up to 3 aid rows
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.aid.queue_header", sync.aidProjects.size(), sync.maxQueuedAid).getString(),
                128, 66, 106, 0.78F, 0.54F, 0x5CEEFF, false);
        String bonusText = sync.employmentBonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", sync.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, bonusText,
                128, 74, 106, 0.62F, 0.44F, sync.employmentBonusActive ? 0x66F298 : 0x8CA0B5, false);

        if (sync.aidProjects.isEmpty()) {
            TyC2UiWidgets.drawCenteredFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    128, 98, 106, 0.78F, 0.54F, 0x889EB3);
            return;
        }
        int rows = Math.min(MAX_AID_ROWS, sync.aidProjects.size());
        for (int i = 0; i < rows; i++) {
            S2CHubSyncPacket.AidProjectDto aid = sync.aidProjects.get(i);
            int ry = 83 + i * 16;
            int rowW = aid.canCancel() ? 73 : 107;
            GuiComponent.fill(poseStack, 128, ry, 128 + rowW, ry + 15, 0xFF0E1722);
            int fillW = Math.max(0, Math.min(rowW - 2, ((rowW - 2) * aid.progressPct()) / 100));
            int barCol = "BUILDING".equals(aid.statusName())
                    ? 0xFF44DD77
                    : ("NO_SITE".equals(aid.statusName()) ? 0xFFEE6644 : 0xFF44AADD);
            if (fillW > 0) {
                GuiComponent.fill(poseStack, 129, ry + 13, 129 + fillW, ry + 15, barCol);
            }
            String profName = TyC2UiWidgets.localizedProfessionName(
                    aid.professionId(), aid.archetypeName(), aid.displayNameFallback()
            );
            String titleLine = profName + (aid.large() ? " [L]" : " [S]");
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, titleLine, 130, ry + 1, rowW - 4, 0.70F, 0.46F, 0xFFE888, false);
            String statusLine = TyC2UiWidgets.formatAidStatus(aid);
            int stCol = "NO_SITE".equals(aid.statusName()) ? 0xFF9966 : 0xAAD8F2;
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, statusLine, 130, ry + 7, rowW - 4, 0.64F, 0.44F, stCol, false);
        }
    }

    private void renderSettingsSubTab(PoseStack poseStack) {
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.rename_placeholder").getString(),
                14, 66, 219, 0.82F, 0.56F, 0xAAD8F2, false);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        ItemStack hoveredCustom = ItemStack.EMPTY;
        List<Component> hoveredTips = null;
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;

        if (sync != null && activeTab == 0) {
            List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
                S2CHubSyncPacket.OrderDto o = list.get(start + i);
                ItemStack icon = TyC2Ids.createStack(o.itemId(), 1, "", o.enchantSpec());
                int ix = leftPos + 13;
                int iy = topPos + 64 + i * 17;
                if (!icon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(icon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hoveredCustom = icon;
                    }
                }
                // Check order gem tag tooltips & Auto button tooltip
                int cardY = 64 + i * 17;
                if (hoveredTips == null && relY >= cardY + 1 && relY <= cardY + 14) {
                    boolean hasBiome = o.biomeSpecialty();
                    boolean hasSeason = o.seasonalBonus();
                    if (hasBiome && hasSeason) {
                        if (relX >= 79 && relX <= 101) {
                            hoveredTips = List.of(Component.translatable(o.isVillageBuy()
                                    ? "gui.tyc2.order.tooltip.biome_buy"
                                    : "gui.tyc2.order.tooltip.biome_sell").withStyle(ChatFormatting.GREEN));
                        } else if (relX >= 102 && relX <= 124) {
                            hoveredTips = List.of(Component.translatable(o.isVillageBuy()
                                    ? "gui.tyc2.order.tooltip.season_buy"
                                    : "gui.tyc2.order.tooltip.season_sell").withStyle(ChatFormatting.AQUA));
                        }
                    } else if (hasBiome && relX >= 94 && relX <= 124) {
                        hoveredTips = List.of(Component.translatable(o.isVillageBuy()
                                ? "gui.tyc2.order.tooltip.biome_buy"
                                : "gui.tyc2.order.tooltip.biome_sell").withStyle(ChatFormatting.GREEN));
                    } else if (hasSeason && relX >= 94 && relX <= 124) {
                        hoveredTips = List.of(Component.translatable(o.isVillageBuy()
                                ? "gui.tyc2.order.tooltip.season_buy"
                                : "gui.tyc2.order.tooltip.season_sell").withStyle(ChatFormatting.AQUA));
                    } else if (o.isVillageBuy() && relX >= 218 && relX <= 236) {
                        hoveredTips = List.of(Component.translatable(o.autoFulfill()
                                ? "gui.tyc2.hub.auto_fulfill.on"
                                : "gui.tyc2.hub.auto_fulfill.off").withStyle(
                                o.autoFulfill() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    }
                }
            }
        } else if (sync != null && activeTab == 1) {
            for (int i = 0; i < sync.warehouseSlots.size() && i < WH_ROWS * WH_COLS; i++) {
                S2CHubSyncPacket.SlotDto s = sync.warehouseSlots.get(i);
                if (s.count() <= 0 || s.itemId().isEmpty()) {
                    continue;
                }
                int col = i % WH_COLS;
                int row = i / WH_COLS;
                int ix = leftPos + WH_GRID_X + col * 18 + 1;
                int iy = topPos + WH_GRID_Y + row * 18 + 1;
                ItemStack stack = TyC2Ids.createStack(s.itemId(), s.count(), s.nbtJson(), null);
                if (!stack.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(stack, ix, iy);
                    itemRenderer.renderGuiItemDecorations(font, stack, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hoveredCustom = stack;
                    }
                }
            }
        } else if (sync != null && activeTab == 3 && growthSubTab == 1) {
            S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
            if (wp != null) {
                ItemStack wsIcon = TyC2Ids.createBlockOrItemIcon(wp.workstationBlockId());
                int ix = leftPos + 27;
                int iy = topPos + 64;
                if (!wsIcon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(wsIcon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hoveredCustom = wsIcon;
                    }
                }
                if (hoveredCustom.isEmpty() && wp.aidLimitReached()
                        && relX >= 12 && relX < 121 && relY >= 116 && relY < 130) {
                    hoveredTips = List.of(Component.translatable(
                            "status.tyc2.aid.profession_limit",
                            WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION
                    ).withStyle(ChatFormatting.RED));
                }
            }
            if (hoveredTips == null) {
                int rows = Math.min(MAX_AID_ROWS, sync.aidProjects.size());
                for (int i = 0; i < rows; i++) {
                    S2CHubSyncPacket.AidProjectDto aid = sync.aidProjects.get(i);
                    int ry = 83 + i * 16;
                    int maxHoverX = aid.canCancel() ? 201 : 235;
                    if (relX >= 128 && relX <= maxHoverX && relY >= ry && relY <= ry + 15) {
                        hoveredTips = TyC2UiWidgets.buildAidMaterialsTooltip(aid);
                        break;
                    }
                }
            }
        }

        // Banner LCD hover tooltips (Biome, Weekly Calendar, Unemployment/Bonus, Season, CPI Arrow, Chunk-Loader)
        if (sync != null && hoveredCustom.isEmpty() && hoveredTips == null && relY >= 21 && relY <= 45) {
            if (relX >= 125 && relX <= 190 && relY <= 33) {
                hoveredTips = List.of(Component.translatable(
                        "gui.tyc2.hub.biome_tooltip",
                        Component.translatable("biome.tyc2." + sync.biomeStyleId)
                ).withStyle(ChatFormatting.GOLD));
            } else if (relX >= 120 && relX <= 167 && relY >= 33) {
                hoveredTips = List.of(Component.translatable(
                        "gui.tyc2.hub.refresh_tooltip",
                        TyC2UiWidgets.formatRefreshCountdown(liveRefreshRemaining())
                ).withStyle(ChatFormatting.YELLOW));
            } else if (relX >= 168 && relX <= 191 && relY >= 33) {
                List<Component> empTips = new ArrayList<>();
                empTips.add(Component.translatable(
                        "gui.tyc2.hub.unemployment", sync.unemploymentPct, sync.unemployedCount, sync.workforceCount
                ).withStyle(sync.employmentBonusActive ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                empTips.add(sync.employmentBonusActive
                        ? Component.translatable("gui.tyc2.aid.bonus_active", sync.qtyBonusPct)
                                .withStyle(ChatFormatting.AQUA)
                        : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct)
                                .withStyle(ChatFormatting.GRAY));
                hoveredTips = empTips;
            } else if (relX >= 192 && relX <= 206 && TyC2UiWidgets.hubSeasonU(sync.seasonKey) >= 0) {
                hoveredTips = List.of(Component.translatable(
                        "gui.tyc2.hub.season_tooltip",
                        Component.translatable("season.tyc2." + sync.seasonKey)
                ).withStyle(ChatFormatting.AQUA));
            } else if (relX >= 207 && relX <= 220) {
                String regimeKey = switch (sync.inflationState) {
                    case "DEFLATION" -> "gui.tyc2.economy.deflation";
                    case "STABLE" -> "gui.tyc2.economy.stable";
                    default -> "gui.tyc2.economy.inflation";
                };
                hoveredTips = List.of(
                        Component.translatable("gui.tyc2.economy.cpi", String.format(Locale.ROOT, "%.2f", sync.cpi))
                                .withStyle(ChatFormatting.GOLD),
                        Component.translatable(regimeKey).withStyle(ChatFormatting.GRAY)
                );
            } else if (relX >= 221 && relX <= 238) {
                hoveredTips = List.of(Component.translatable(
                        sync.chunkLoaderActive ? "gui.tyc2.hub.chunk_loader.active" : "gui.tyc2.hub.chunk_loader.inactive"
                ).withStyle(sync.chunkLoaderActive ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }

        if (!hoveredCustom.isEmpty()) {
            renderTooltip(poseStack, hoveredCustom, mouseX, mouseY);
        } else if (hoveredTips != null && !hoveredTips.isEmpty()) {
            renderComponentTooltip(poseStack, hoveredTips, mouseX, mouseY);
        } else if (sync != null && !activeFeedback.isEmpty()
                && relX >= 179 && relX <= 240 && relY >= 196 && relY <= 215) {
            renderTooltip(poseStack, font.split(activeFeedbackComponent, 220), mouseX, mouseY);
        } else {
            renderTooltip(poseStack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sync != null && activeTab == 1) {
            int relX = (int) mouseX - (leftPos + WH_GRID_X);
            int relY = (int) mouseY - (topPos + WH_GRID_Y);
            if (relX >= 0 && relY >= 0 && relX < WH_COLS * 18 && relY < WH_ROWS * 18) {
                int col = relX / 18;
                int row = relY / 18;
                int idx = row * WH_COLS + col;
                if (idx < sync.warehouseSlots.size()) {
                    int absSlot = sync.warehouseSlots.get(idx).absSlot();
                    TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                            menu.getHubPos(),
                            C2SHubActionPacket.Action.WAREHOUSE_SLOT_CLICK,
                            absSlot,
                            0,
                            button == 1,
                            ""
                    ));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (searchBox.visible && searchBox.isFocused()) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (renameBox.visible && renameBox.isFocused()) {
            renameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
