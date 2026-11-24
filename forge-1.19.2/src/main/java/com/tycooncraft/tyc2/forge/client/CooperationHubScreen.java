package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.gui.CooperationHubMenu;
import com.tycooncraft.tyc2.forge.network.C2SHubActionPacket;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
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
 * Forge 1.19.2 multi-tab screen for {@code tyc2:cooperation_hub} matching
 * {@code gui/cooperation_hub.png} (248x222): Orders, 1,000-slot Warehouse, Macro Economy +
 * 48-point CPI Chart, and Growth & Settings.
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

    private int activeTab = 0; // 0=Orders, 1=Warehouse, 2=Economy, 3=Expansion/Aid/Settings
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

        // Tab strip (8,5,232x14) -> 4 tabs of 58px
        String[] tabKeys = {
                "gui.tyc2.hub.tab.orders",
                "gui.tyc2.hub.tab.warehouse",
                "gui.tyc2.hub.tab.economy",
                "gui.tyc2.hub.tab.expansion"
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tabBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 8 + i * 58, topPos + 5, 58, 14,
                    Component.translatable(tabKeys[i]),
                    btn -> switchTab(idx)
            ));
        }

        // --- Tab 0: Orders controls ---
        payModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 50, 56, 12, Component.translatable("gui.tyc2.hub.bank_mode"),
                btn -> {
                    useBankMode = !useBankMode;
                    refreshWidgetVisibility();
                }
        ));
        sideFilterBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 68, topPos + 50, 76, 12, Component.translatable("gui.tyc2.hub.sell_orders"),
                btn -> {
                    showBuyOrders = !showBuyOrders;
                    orderPage = 0;
                    refreshWidgetVisibility();
                }
        ));
        qtyPresetBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 146, topPos + 50, 56, 12, Component.translatable("gui.tyc2.hub.qty_preset", 1),
                btn -> {
                    orderQtyPreset = (orderQtyPreset + 1) % 3;
                    refreshWidgetVisibility();
                }
        ));
        orderPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 206, topPos + 50, 14, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    orderPage = Math.max(0, orderPage - 1);
                    refreshWidgetVisibility();
                }
        ));
        orderNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 222, topPos + 50, 14, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    orderPage++;
                    refreshWidgetVisibility();
                }
        ));
        for (int i = 0; i < ORDERS_PER_PAGE; i++) {
            final int row = i;
            int ry = topPos + 65 + i * 17;
            orderActionBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 178, ry, 40, 14, Component.translatable("gui.tyc2.hub.buy"),
                    btn -> executeOrderRow(row)
            ));
            orderAutoBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 220, ry, 18, 14, Component.translatable("gui.tyc2.hub.btn.auto_tiny.off"),
                    btn -> toggleOrderAutoRow(row)
            ));
        }

        // --- Tab 1: Warehouse controls (zero slot-count/index advertising) ---
        zoneToggleBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 50, 62, 12, Component.translatable("gui.tyc2.hub.zone.inbound"),
                btn -> {
                    warehouseOutbound = !warehouseOutbound;
                    warehousePage = 0;
                    sendWarehouseView();
                }
        ));
        searchBox = new EditBox(font, leftPos + 74, topPos + 50, 74, 12, Component.translatable("gui.tyc2.hub.search_placeholder"));
        searchBox.setMaxLength(32);
        searchBox.setResponder(s -> {
            warehousePage = 0;
            sendWarehouseView();
        });
        addRenderableWidget(searchBox);
        whPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 150, topPos + 50, 14, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    warehousePage = Math.max(0, warehousePage - 1);
                    sendWarehouseView();
                }
        ));
        whNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 188, topPos + 50, 14, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    if (sync != null && warehousePage + 1 < sync.totalPages) {
                        warehousePage++;
                        sendWarehouseView();
                    }
                }
        ));
        whDepositBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 204, topPos + 50, 34, 12, Component.translatable("gui.tyc2.hub.btn.put"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.WAREHOUSE_DEPOSIT_HELD
                ))
        ));

        // --- Tab 3: Sub-tabs (Expansion / Aid / Settings) at y=49..60 ---
        subExpBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 49, 75, 11, Component.translatable("gui.tyc2.hub.tab.expansion"),
                btn -> switchGrowthSubTab(0)
        ));
        subAidBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 86, topPos + 49, 75, 11, Component.translatable("gui.tyc2.hub.tab.aid"),
                btn -> switchGrowthSubTab(1)
        ));
        subSetBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 162, topPos + 49, 76, 11, Component.translatable("gui.tyc2.hub.tab.settings"),
                btn -> switchGrowthSubTab(2)
        ));

        // --- Tab 3.0: Expansion & Upgrade controls ---
        upgradeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 82, 114, 12, Component.translatable("gui.tyc2.hub.upgrade_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.UPGRADE_VILLAGE
                ))
        ));
        expDeliverBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 120, 42, 12, Component.translatable("gui.tyc2.hub.deliver"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.DELIVER_EXPANSION
                ))
        ));
        expAutoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 53, topPos + 120, 34, 12, Component.translatable("gui.tyc2.hub.auto_short.off"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_EXPANSION_AUTO
                ))
        ));
        expPauseBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 88, topPos + 120, 36, 12, Component.translatable("gui.tyc2.hub.pause_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_EXPANSION_PAUSE
                ))
        ));

        // --- Tab 3.1: Aid Construction controls ---
        aidProfPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 64, 13, 14, Component.translatable("gui.tyc2.prev"),
                btn -> stepWorkplace(-1)
        ));
        aidProfNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 109, topPos + 64, 13, 14, Component.translatable("gui.tyc2.next"),
                btn -> stepWorkplace(1)
        ));
        aidSizeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 81, 60, 12, Component.translatable("gui.tyc2.aid.size.small"),
                btn -> {
                    aidLargeSize = !aidLargeSize;
                    refreshWidgetVisibility();
                }
        ));
        aidPayModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 72, topPos + 81, 50, 12, Component.translatable("gui.tyc2.hub.bank_mode"),
                btn -> {
                    useBankMode = !useBankMode;
                    refreshWidgetVisibility();
                }
        ));
        aidStartBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 116, 112, 15, Component.translatable("gui.tyc2.aid.start_btn"),
                btn -> sendStartAid()
        ).setAccentColor(0xFF2E7D32));
        for (int i = 0; i < MAX_AID_ROWS; i++) {
            final int row = i;
            aidCancelBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 204, topPos + 84 + i * 16, 34, 13, Component.translatable("gui.tyc2.aid.cancel_btn"),
                    btn -> sendCancelAid(row)
            ).setAccentColor(0xFF992222));
        }

        // --- Tab 3.2: Settings controls ---
        renameBox = new EditBox(font, leftPos + 10, topPos + 76, 160, 13, Component.translatable("gui.tyc2.hub.rename_placeholder"));
        renameBox.setMaxLength(32);
        addRenderableWidget(renameBox);
        renameSaveBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 174, topPos + 76, 62, 13, Component.translatable("gui.tyc2.save"),
                btn -> TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                        menu.getHubPos(), C2SHubActionPacket.Action.RENAME_VILLAGE, 0, 0, false, renameBox.getValue()
                ))
        ));
        relocateBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 97, 226, 14, Component.translatable("gui.tyc2.hub.relocate"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.RELOCATE_HUB
                ))
        ));
        terminateBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 115, 226, 14, Component.translatable("gui.tyc2.hub.terminate"),
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
                }
        ).setAccentColor(0xFF992222));

        refreshWidgetVisibility();
        if (menu.getLatestSync() != null) {
            onSync(menu.getLatestSync());
        }
        TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(menu.getHubPos(), C2SHubActionPacket.Action.REQUEST_SYNC));
    }

    public void onSync(S2CHubSyncPacket pkt) {
        this.sync = pkt;
        this.menu.setLatestSync(pkt);
        this.warehouseOutbound = pkt.outboundZone;
        this.warehousePage = pkt.pageIndex;
        if (renameBox != null && !renameBox.isFocused() && renameBox.getValue().isEmpty()) {
            renameBox.setValue(pkt.villageName);
        }
        refreshWidgetVisibility();
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

        // Tab 0
        boolean t0 = activeTab == 0;
        List<S2CHubSyncPacket.OrderDto> orders = filteredOrders();
        int maxOrderPage = Math.max(0, (orders.size() - 1) / ORDERS_PER_PAGE);
        orderPage = Math.min(orderPage, maxOrderPage);

        payModeBtn.visible = t0;
        payModeBtn.setMessage(Component.translatable(useBankMode ? "gui.tyc2.hub.bank_mode" : "gui.tyc2.hub.cash_mode"));
        payModeBtn.setHighlighted(useBankMode);

        sideFilterBtn.visible = t0;
        sideFilterBtn.setMessage(Component.translatable(showBuyOrders ? "gui.tyc2.hub.buy_orders" : "gui.tyc2.hub.sell_orders"));

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
            orderActionBtns[i].visible = has;
            orderAutoBtns[i].visible = has && orders.get(idx).isVillageBuy();
            if (has) {
                S2CHubSyncPacket.OrderDto o = orders.get(idx);
                orderActionBtns[i].setMessage(Component.translatable(
                        o.isVillageBuy() ? "gui.tyc2.hub.deliver" : "gui.tyc2.hub.buy"
                ));
                orderAutoBtns[i].setHighlighted(o.autoFulfill());
                orderAutoBtns[i].setMessage(Component.translatable(
                        o.autoFulfill() ? "gui.tyc2.hub.btn.auto_tiny.on" : "gui.tyc2.hub.btn.auto_tiny.off"
                ));
            }
        }

        // Tab 1
        boolean t1 = activeTab == 1;
        zoneToggleBtn.visible = t1;
        zoneToggleBtn.setMessage(Component.translatable(
                warehouseOutbound ? "gui.tyc2.hub.zone.outbound" : "gui.tyc2.hub.zone.inbound"
        ));
        zoneToggleBtn.setHighlighted(warehouseOutbound);
        zoneToggleBtn.setAccentColor(warehouseOutbound ? 0xFFB86B1A : 0xFF2B6CB0);
        searchBox.visible = t1;
        whPrevBtn.visible = t1;
        whNextBtn.visible = t1;
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
            expAutoBtn.setMessage(Component.translatable(
                    sync.expAutoFulfill ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        expPauseBtn.visible = t3Exp;
        if (sync != null) {
            expPauseBtn.setHighlighted(sync.expansionPaused);
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
        aidPayModeBtn.setMessage(Component.translatable(useBankMode ? "gui.tyc2.hub.bank_mode" : "gui.tyc2.hub.cash_mode"));
        aidPayModeBtn.setHighlighted(useBankMode);

        boolean sizeUnlocked = wp != null && (aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked());
        boolean queueOpen = sync != null && sync.aidProjects.size() < sync.maxQueuedAid;
        aidStartBtn.visible = t3Aid;
        aidStartBtn.active = t3Aid && wp != null && sizeUnlocked && queueOpen && !sync.depopulatedPaused;
        aidStartBtn.setHighlighted(aidStartBtn.active);

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

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Banner LCD sprites
        // Mayor crown (212,226,16x16)
        blit(poseStack, leftPos + 10, topPos + 25, 212, 226, 16, 16);

        if (sync != null) {
            // Season sprite (62/76/90/104, 226, 12x12)
            int seasonU = switch (sync.seasonKey) {
                case "spring" -> 62;
                case "summer" -> 76;
                case "autumn" -> 90;
                case "winter" -> 104;
                default -> -1;
            };
            if (seasonU >= 0) {
                blit(poseStack, leftPos + 194, topPos + 26, seasonU, 226, 12, 12);
            }
            // CPI arrow sprite (120=up/red, 132=down/blue, 144=flat/green, 226, 10x10)
            int cpiU = sync.cpi > 1.08 ? 120 : (sync.cpi < 0.92 ? 132 : 144);
            blit(poseStack, leftPos + 209, topPos + 27, cpiU, 226, 10, 10);

            // Chunk loader badge (192,226,16x16)
            if (sync.chunkLoaderActive) {
                blit(poseStack, leftPos + 221, topPos + 25, 192, 226, 16, 16);
            }

            // Tab 1: Warehouse 3x12 slot grid using exact inbound/outbound slot frames (20/40, 226, 18x18)
            if (activeTab == 1) {
                int slotU = warehouseOutbound ? 40 : 20;
                for (int r = 0; r < WH_ROWS; r++) {
                    for (int c = 0; c < WH_COLS; c++) {
                        blit(poseStack, leftPos + WH_GRID_X + c * 18, topPos + WH_GRID_Y + r * 18, slotU, 226, 18, 18);
                    }
                }
            }
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        if (sync == null) {
            return;
        }

        // Banner LCD (8,21,232x24) — 100% localized
        String head = Component.translatable("gui.tyc2.hub.level", sync.level, sync.villageName).getString()
                + "  " + Component.translatable("gui.tyc2.hub.population", sync.population).getString()
                + "  " + Component.translatable(
                        "gui.tyc2.hub.unemployment", sync.unemploymentPct, sync.unemployedCount, sync.workforceCount
                ).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, head, 30, 24, 160, 0.56F, 0xFFDD55);
        String sub = Component.translatable("gui.tyc2.hub.village_funds", TyC2UiWidgets.compactIc(sync.villageBalance)).getString()
                + " | " + Component.translatable("gui.tyc2.hub.status.cpi", String.format(Locale.ROOT, "%.2f", sync.cpi)).getString()
                + " | " + sync.refreshSecondsRemaining + "s";
        if (sync.employmentBonusActive) {
            sub += " | " + Component.translatable(
                    "gui.tyc2.hub.unemployment_bonus", sync.restockBonusPct, sync.qtyBonusPct
            ).getString();
        }
        TyC2UiWidgets.drawFittedString(poseStack, font, sub, 30, 34, 160, 0.50F,
                sync.employmentBonusActive ? 0x66FF99 : 0x88EECC);

        // Status LCD (179,166,61x49) — 100% localized, zero slot-cap advertising
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.bank", TyC2UiWidgets.compactIc(sync.activeBankBalance)).getString(),
                182, 169, 56, 0.68F, 0x66FF88);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.inbound", sync.inboundUsed).getString(),
                182, 179, 56, 0.68F, 0x77BBFF);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.outbound", sync.outboundUsed).getString(),
                182, 189, 56, 0.68F, 0xFFBB66);
        if (!sync.feedback.isEmpty()) {
            List<FormattedCharSequence> fbLines = font.split(sync.feedbackComponent, 112);
            if (fbLines.size() <= 1) {
                TyC2UiWidgets.drawFittedString(poseStack, font, sync.feedback, 182, 200, 56, 0.44F, 0xFFDD77);
            } else {
                poseStack.pushPose();
                poseStack.translate(182.0F, 198.0F, 0.0F);
                poseStack.scale(0.50F, 0.50F, 1.0F);
                for (int i = 0; i < Math.min(2, fbLines.size()); i++) {
                    font.draw(poseStack, fbLines.get(i), 0, i * 11, 0xFFDD77);
                }
                poseStack.popPose();
            }
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable(sync.chunkLoaderActive ? "gui.tyc2.hub.status.cl_on" : "gui.tyc2.hub.status.cl_off").getString(),
                    182, 201, 56, 0.65F, sync.chunkLoaderActive ? 0x66FF88 : 0x8899AA);
        }

        // Viewport content (8,48,232x86)
        switch (activeTab) {
            case 0 -> renderOrdersLabels(poseStack);
            case 1 -> renderWarehouseLabels(poseStack);
            case 2 -> renderEconomyTab(poseStack);
            case 3 -> renderGrowthLabels(poseStack);
        }
    }

    private void renderOrdersLabels(PoseStack poseStack) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        if (list.isEmpty()) {
            font.draw(poseStack, Component.translatable(
                    sync.depopulatedPaused ? "gui.tyc2.hub.paused_depopulated" : "gui.tyc2.hub.no_orders"
            ), 14, 72, 0xFFAA66);
            return;
        }
        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
            S2CHubSyncPacket.OrderDto o = list.get(start + i);
            int y = 66 + i * 17;
            String itemName = TyC2Ids.localizedItemName(o.itemId(), o.enchantSpec());
            TyC2UiWidgets.drawFittedString(poseStack, font, itemName, 30, y + 3, 72, 0.68F, o.seasonalBonus() ? 0xFFEE66 : 0xECEFF4);
            String qtyStr = Component.translatable("gui.tyc2.hub.order_progress", o.fulfilledQty(), o.totalQty()).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, qtyStr, 104, y + 3, 34, 0.72F, 0xAADDEE);
            String priceStr = Component.translatable("gui.tyc2.hub.unit_price", o.unitPrice()).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, priceStr, 140, y + 3, 36, 0.68F, 0x66FF88);
        }
    }

    private void renderWarehouseLabels(PoseStack poseStack) {
        String pg = (warehousePage + 1) + "/" + Math.max(1, sync.totalPages);
        font.draw(poseStack, pg, 166, 52, 0xDDEEFF);
    }

    private void renderEconomyTab(PoseStack poseStack) {
        int col = sync.cpi > 1.08 ? 0xFF7755 : (sync.cpi < 0.92 ? 0x55DDFF : 0x66FF88);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.status.cpi", String.format(Locale.ROOT, "%.2f", sync.cpi)).getString(),
                12, 50, 90, 0.72F, col);
        String regimeKey = switch (sync.inflationState) {
            case "DEFLATION" -> "gui.tyc2.economy.deflation";
            case "STABLE" -> "gui.tyc2.economy.stable";
            default -> "gui.tyc2.economy.inflation";
        };
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable(regimeKey).getString(), 12, 60, 90, 0.72F, col);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.economy.money_supply", TyC2UiWidgets.compactIc(sync.moneySupply)).getString(),
                12, 71, 90, 0.58F, 0xFFDD77);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.economy.goods_circulation", TyC2UiWidgets.compactIc(Math.round(sync.goodsCirculation))).getString(),
                12, 81, 90, 0.58F, 0x88EECC);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.economy.season", Component.translatable("season.tyc2." + sync.seasonKey)).getString(),
                12, 92, 90, 0.62F, 0xAADDEE);
        int clampedTier = Math.max(0, Math.min(5, sync.playerTierLevel));
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("tier.tyc2." + clampedTier).getString(),
                12, 102, 90, 0.58F, 0xCCCCCC);

        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.unemployment", sync.unemploymentPct, sync.unemployedCount, sync.workforceCount).getString(),
                12, 113, 90, 0.54F, sync.employmentBonusActive ? 0x66FF88 : 0xFFCC66);
        String bonusLine = sync.employmentBonusActive
                ? Component.translatable("gui.tyc2.hub.unemployment_bonus", sync.restockBonusPct, sync.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, bonusLine,
                12, 123, 90, 0.44F, sync.employmentBonusActive ? 0x66FF99 : 0x8899AA);

        TyC2UiWidgets.drawCpiLineChart(poseStack, font, 106, 51, 130, 78, sync.cpiHistory);
    }

    private void renderGrowthLabels(PoseStack poseStack) {
        switch (growthSubTab) {
            case 0 -> renderExpansionSubTab(poseStack);
            case 1 -> renderAidSubTab(poseStack);
            case 2 -> renderSettingsSubTab(poseStack);
        }
    }

    private void renderExpansionSubTab(PoseStack poseStack) {
        if (sync.level >= 10) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.max_level").getString(), 10, 64, 114, 0.72F, 0x66FF88);
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.upgrade_cost", sync.nextSinkCost).getString(),
                    10, 63, 114, 0.65F, 0xFFDD55);
            String req = Component.translatable(
                    "gui.tyc2.hub.upgrade_req",
                    sync.population + "/" + sync.nextMinPop,
                    TyC2UiWidgets.compactIc(sync.cumulativeVolume) + "/" + TyC2UiWidgets.compactIc(sync.nextMinVol),
                    TyC2UiWidgets.compactIc(sync.villageBalance) + "/" + TyC2UiWidgets.compactIc(sync.nextMinBal)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, req, 10, 72, 114, 0.44F, 0xAADDEE);
        }

        if (sync.hasJob) {
            int pct = sync.jobTotal > 0 ? (sync.jobPlaced * 100) / sync.jobTotal : 0;
            String msg = Component.translatable(
                    "gui.tyc2.expansion.building",
                    Component.translatable("project.tyc2." + sync.jobProjectKey),
                    pct
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, msg, 10, 99, 114, 0.62F, 0x66FF88);
        } else if (sync.hasExpansion) {
            String proj = Component.translatable(
                    "gui.tyc2.expansion.project",
                    Component.translatable("project.tyc2." + sync.expProjectKey)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, proj, 10, 96, 114, 0.58F, 0xFFDD77);
            int matCount = sync.expMaterials.size();
            int splitIdx = matCount <= 2 ? matCount : (matCount + 1) / 2;
            StringBuilder line1 = new StringBuilder();
            StringBuilder line2 = new StringBuilder();
            boolean line1AllMet = true;
            boolean line2AllMet = true;
            for (int i = 0; i < matCount; i++) {
                S2CHubSyncPacket.MatDto m = sync.expMaterials.get(i);
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
                TyC2UiWidgets.drawFittedString(
                        poseStack, font, line1.toString(), 10, 104, 114, 0.44F,
                        line1AllMet ? 0x66FF99 : 0xFFCC66
                );
            }
            if (line2.length() > 0) {
                TyC2UiWidgets.drawFittedString(
                        poseStack, font, line2.toString(), 10, 111, 114, 0.44F,
                        line2AllMet ? 0x66FF99 : 0xFFCC66
                );
            }
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.none").getString(), 10, 102, 114, 0.65F, 0x99AABB);
        }

        // Right column: Village stats, employment summary, and LC2 / budget info
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.trade_volume", TyC2UiWidgets.compactIc(sync.cumulativeVolume)).getString(),
                130, 63, 108, 0.60F, 0xAADDEE);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("biome.tyc2." + sync.biomeStyleId).getString(),
                130, 73, 108, 0.68F, 0xCCCCCC);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.unemployment", sync.unemploymentPct, sync.unemployedCount, sync.workforceCount).getString(),
                130, 83, 108, 0.56F, sync.employmentBonusActive ? 0x66FF88 : 0xFFCC66);
        String empDetail = sync.employmentBonusActive
                ? Component.translatable("gui.tyc2.hub.unemployment_bonus", sync.restockBonusPct, sync.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, empDetail,
                130, 93, 108, 0.44F, sync.employmentBonusActive ? 0x66FF99 : 0x8899AA);

        if (!sync.nearbyLc2Stations.isEmpty()) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", sync.nearbyLc2Stations.size()).getString(),
                    130, 105, 108, 0.60F, 0x55FFFF);
            for (int i = 0; i < Math.min(2, sync.nearbyLc2Stations.size()); i++) {
                String label = Lc2SoftBridge.formatStationLabel(sync.nearbyLc2Stations.get(i));
                TyC2UiWidgets.drawFittedString(poseStack, font, label, 130, 114 + i * 9, 108, 0.52F, 0xAADDEE);
            }
        } else if (sync.hasExpansion) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.budget", sync.expBudget).getString(),
                    130, 107, 108, 0.58F, 0x88EECC);
        }
    }

    private void renderAidSubTab(PoseStack poseStack) {
        // Left column (10..122): Profession picker, live CPI quote & gather speed
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        if (wp != null) {
            String profName = TyC2UiWidgets.localizedProfessionName(
                    wp.professionId(), wp.archetypeName(), wp.displayNameFallback()
            );
            TyC2UiWidgets.drawFittedString(poseStack, font, profName, 43, 67, 64, 0.60F, 0xFFE888);
            long cost = aidLargeSize ? wp.costLargeIc() : wp.costSmallIc();
            boolean unlocked = aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked();
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.aid.cost", cost).getString(),
                    10, 96, 112, 0.62F, unlocked ? 0x66FF88 : 0xFF8866);
        }
        String gatherLine = Component.translatable(
                "gui.tyc2.aid.gather_rate",
                String.format(Locale.ROOT, "%.2f", sync.gatherSpeedMult),
                sync.unemployedCount
        ).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, gatherLine, 10, 105, 112, 0.48F, 0x88EECC);

        // Right column (126..238): Queue header, low-unemployment bonus & up to 3 aid rows
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.aid.queue_header", sync.aidProjects.size(), sync.maxQueuedAid).getString(),
                126, 63, 112, 0.65F, 0x55FFFF);
        String bonusText = sync.employmentBonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", sync.restockBonusPct, sync.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", sync.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, bonusText,
                126, 72, 112, 0.44F, sync.employmentBonusActive ? 0x66FF88 : 0x99AABB);

        if (sync.aidProjects.isEmpty()) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    126, 94, 112, 0.64F, 0x8899AA);
            return;
        }
        int rows = Math.min(MAX_AID_ROWS, sync.aidProjects.size());
        for (int i = 0; i < rows; i++) {
            S2CHubSyncPacket.AidProjectDto aid = sync.aidProjects.get(i);
            int ry = 83 + i * 16;
            int rowW = aid.canCancel() ? 76 : 112;
            GuiComponent.fill(poseStack, 126, ry, 126 + rowW, ry + 15, 0x88101822);
            int fillW = Math.max(0, Math.min(rowW - 2, ((rowW - 2) * aid.progressPct()) / 100));
            int barCol = "BUILDING".equals(aid.statusName())
                    ? 0xFF44DD77
                    : ("NO_SITE".equals(aid.statusName()) ? 0xFFEE6644 : 0xFF44AADD);
            if (fillW > 0) {
                GuiComponent.fill(poseStack, 127, ry + 13, 127 + fillW, ry + 15, barCol);
            }
            String profName = TyC2UiWidgets.localizedProfessionName(
                    aid.professionId(), aid.archetypeName(), aid.displayNameFallback()
            );
            String titleLine = profName + (aid.large() ? " [L]" : " [S]");
            TyC2UiWidgets.drawFittedString(poseStack, font, titleLine, 128, ry + 1, rowW - 4, 0.48F, 0xFFE888);
            String statusLine = TyC2UiWidgets.formatAidStatus(aid);
            int stCol = "NO_SITE".equals(aid.statusName()) ? 0xFF9966 : 0xAADDEE;
            TyC2UiWidgets.drawFittedString(poseStack, font, statusLine, 128, ry + 7, rowW - 4, 0.45F, stCol);
        }
    }

    private void renderSettingsSubTab(PoseStack poseStack) {
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.rename_placeholder").getString(),
                10, 65, 226, 0.70F, 0xAADDEE);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        // Render ItemStacks inside Tab 0 (Orders), Tab 1 (Warehouse), and Tab 3.1 (Aid workstation icon)
        ItemStack hoveredCustom = ItemStack.EMPTY;
        List<Component> hoveredAidTooltip = null;
        if (sync != null && activeTab == 0) {
            List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
                S2CHubSyncPacket.OrderDto o = list.get(start + i);
                ItemStack icon = TyC2Ids.createStack(o.itemId(), 1, "", o.enchantSpec());
                int ix = leftPos + 11;
                int iy = topPos + 64 + i * 17;
                if (!icon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(icon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hoveredCustom = icon;
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
                int ix = leftPos + 25;
                int iy = topPos + 63;
                if (!wsIcon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(wsIcon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hoveredCustom = wsIcon;
                    }
                }
            }
            int relXHover = mouseX - leftPos;
            int relYHover = mouseY - topPos;
            int rows = Math.min(MAX_AID_ROWS, sync.aidProjects.size());
            for (int i = 0; i < rows; i++) {
                S2CHubSyncPacket.AidProjectDto aid = sync.aidProjects.get(i);
                int ry = 83 + i * 16;
                int maxHoverX = aid.canCancel() ? 202 : 238;
                if (relXHover >= 126 && relXHover <= maxHoverX && relYHover >= ry && relYHover <= ry + 15) {
                    hoveredAidTooltip = TyC2UiWidgets.buildAidMaterialsTooltip(aid);
                    break;
                }
            }
        }

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (!hoveredCustom.isEmpty()) {
            renderTooltip(poseStack, hoveredCustom, mouseX, mouseY);
        } else if (hoveredAidTooltip != null && !hoveredAidTooltip.isEmpty()) {
            renderComponentTooltip(poseStack, hoveredAidTooltip, mouseX, mouseY);
        } else if (sync != null && !sync.feedback.isEmpty()
                && relX >= 179 && relX <= 240 && relY >= 196 && relY <= 215) {
            renderTooltip(poseStack, font.split(sync.feedbackComponent, 220), mouseX, mouseY);
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
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        if (renameBox.visible && renameBox.isFocused()) {
            if (renameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
