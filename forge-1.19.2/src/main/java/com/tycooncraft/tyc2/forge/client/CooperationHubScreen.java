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
    private static final int WH_COLS = 12;
    private static final int WH_ROWS = 3;
    private static final int WH_GRID_X = 16;
    private static final int WH_GRID_Y = 66;

    @Nullable
    private S2CHubSyncPacket sync;

    private int activeTab = 0; // 0=Orders, 1=Warehouse, 2=Economy, 3=Growth/Settings
    private boolean useBankMode = true;
    private boolean showBuyOrders = false; // false=Village Sells, true=Village Buys
    private int orderQtyPreset = 0; // 0=1x, 1=8x, 2=Max
    private int orderPage = 0;

    private boolean warehouseOutbound = false;
    private int warehousePage = 0;
    private boolean confirmTerminate = false;

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

    // Growth & Settings controls
    private TyC2UiWidgets.CompactButton upgradeBtn;
    private TyC2UiWidgets.CompactButton expDeliverBtn;
    private TyC2UiWidgets.CompactButton expAutoBtn;
    private TyC2UiWidgets.CompactButton expPauseBtn;
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

        // --- Tab 3: Growth & Settings controls ---
        upgradeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 72, 112, 13, Component.translatable("gui.tyc2.hub.upgrade_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.UPGRADE_VILLAGE
                ))
        ));
        expDeliverBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 118, 42, 13, Component.translatable("gui.tyc2.hub.deliver"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.DELIVER_EXPANSION
                ))
        ));
        expAutoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 53, topPos + 118, 34, 13, Component.translatable("gui.tyc2.hub.auto_short.off"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_EXPANSION_AUTO
                ))
        ));
        expPauseBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 88, topPos + 118, 34, 13, Component.translatable("gui.tyc2.hub.pause_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.TOGGLE_EXPANSION_PAUSE
                ))
        ));

        renameBox = new EditBox(font, leftPos + 130, topPos + 51, 74, 12, Component.translatable("gui.tyc2.hub.rename_placeholder"));
        renameBox.setMaxLength(32);
        addRenderableWidget(renameBox);
        renameSaveBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 206, topPos + 51, 30, 12, Component.translatable("gui.tyc2.save"),
                btn -> TyC2ForgeNetwork.sendToServer(new C2SHubActionPacket(
                        menu.getHubPos(), C2SHubActionPacket.Action.RENAME_VILLAGE, 0, 0, false, renameBox.getValue()
                ))
        ));
        relocateBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 130, topPos + 102, 106, 13, Component.translatable("gui.tyc2.hub.relocate"),
                btn -> TyC2ForgeNetwork.sendToServer(C2SHubActionPacket.simple(
                        menu.getHubPos(), C2SHubActionPacket.Action.RELOCATE_HUB
                ))
        ));
        terminateBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 130, topPos + 118, 106, 13, Component.translatable("gui.tyc2.hub.terminate"),
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

        // Tab 3
        boolean t3 = activeTab == 3;
        boolean canManage = sync != null && sync.canManage;
        upgradeBtn.visible = t3;
        upgradeBtn.active = sync != null && sync.canUpgrade;
        expDeliverBtn.visible = t3;
        expAutoBtn.visible = t3 && sync != null && sync.hasExpansion;
        if (sync != null && sync.hasExpansion) {
            expAutoBtn.setHighlighted(sync.expAutoFulfill);
            expAutoBtn.setMessage(Component.translatable(
                    sync.expAutoFulfill ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        expPauseBtn.visible = t3;
        if (sync != null) {
            expPauseBtn.setHighlighted(sync.expansionPaused);
            expPauseBtn.setMessage(Component.translatable(
                    sync.expansionPaused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
        }
        renameBox.visible = t3;
        renameSaveBtn.visible = t3;
        renameSaveBtn.active = canManage;
        relocateBtn.visible = t3;
        relocateBtn.active = canManage;
        terminateBtn.visible = t3;
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
                + "  " + Component.translatable("gui.tyc2.hub.population", sync.population).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, head, 30, 24, 160, 0.70F, 0xFFDD55);
        String sub = Component.translatable("gui.tyc2.hub.village_funds", TyC2UiWidgets.compactIc(sync.villageBalance)).getString()
                + " | " + Component.translatable("gui.tyc2.hub.status.cpi", String.format(Locale.ROOT, "%.2f", sync.cpi)).getString()
                + " | " + sync.refreshSecondsRemaining + "s";
        TyC2UiWidgets.drawFittedString(poseStack, font, sub, 30, 34, 160, 0.65F, 0x88EECC);

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
                12, 52, 90, 0.72F, col);
        String regimeKey = switch (sync.inflationState) {
            case "DEFLATION" -> "gui.tyc2.economy.deflation";
            case "STABLE" -> "gui.tyc2.economy.stable";
            default -> "gui.tyc2.economy.inflation";
        };
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable(regimeKey).getString(), 12, 63, 90, 0.72F, col);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.economy.money_supply", TyC2UiWidgets.compactIc(sync.moneySupply)).getString(),
                12, 76, 90, 0.60F, 0xFFDD77);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.economy.goods_circulation", TyC2UiWidgets.compactIc(Math.round(sync.goodsCirculation))).getString(),
                12, 87, 90, 0.60F, 0x88EECC);
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.economy.season", Component.translatable("season.tyc2." + sync.seasonKey)).getString(),
                12, 100, 90, 0.65F, 0xAADDEE);
        int clampedTier = Math.max(0, Math.min(5, sync.playerTierLevel));
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("tier.tyc2." + clampedTier).getString(),
                12, 112, 90, 0.60F, 0xCCCCCC);

        TyC2UiWidgets.drawCpiLineChart(poseStack, font, 106, 51, 130, 78, sync.cpiHistory);
    }

    private void renderGrowthLabels(PoseStack poseStack) {
        if (sync.level >= 10) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.max_level").getString(), 10, 52, 114, 0.75F, 0x66FF88);
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.upgrade_cost", sync.nextSinkCost).getString(),
                    10, 51, 114, 0.68F, 0xFFDD55);
            String req = Component.translatable(
                    "gui.tyc2.hub.upgrade_req",
                    sync.population + "/" + sync.nextMinPop,
                    TyC2UiWidgets.compactIc(sync.cumulativeVolume) + "/" + TyC2UiWidgets.compactIc(sync.nextMinVol),
                    TyC2UiWidgets.compactIc(sync.villageBalance) + "/" + TyC2UiWidgets.compactIc(sync.nextMinBal)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, req, 10, 61, 114, 0.44F, 0xAADDEE);
        }

        if (sync.hasJob) {
            int pct = sync.jobTotal > 0 ? (sync.jobPlaced * 100) / sync.jobTotal : 0;
            String msg = Component.translatable(
                    "gui.tyc2.expansion.building",
                    Component.translatable("project.tyc2." + sync.jobProjectKey),
                    pct
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, msg, 10, 89, 114, 0.65F, 0x66FF88);
        } else if (sync.hasExpansion) {
            String proj = Component.translatable(
                    "gui.tyc2.expansion.project",
                    Component.translatable("project.tyc2." + sync.expProjectKey)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, proj, 10, 87, 114, 0.62F, 0xFFDD77);
            int matCount = sync.expMaterials.size();
            int splitIdx = matCount <= 2 ? matCount : (matCount + 1) / 2;
            StringBuilder line1 = new StringBuilder();
            StringBuilder line2 = new StringBuilder();
            for (int i = 0; i < matCount; i++) {
                S2CHubSyncPacket.MatDto m = sync.expMaterials.get(i);
                StringBuilder dst = i < splitIdx ? line1 : line2;
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
                TyC2UiWidgets.drawFittedString(poseStack, font, line1.toString(), 10, 96, 114, 0.45F, 0xCCCCCC);
            }
            if (line2.length() > 0) {
                TyC2UiWidgets.drawFittedString(poseStack, font, line2.toString(), 10, 103, 114, 0.45F, 0xCCCCCC);
            }
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.budget", sync.expBudget).getString(),
                    10, 110, 114, 0.60F, 0x88EECC);
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.none").getString(), 10, 92, 114, 0.68F, 0x99AABB);
        }

        // Right column: Nearby stations (localized block names) or village summary
        if (!sync.nearbyLc2Stations.isEmpty()) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", sync.nearbyLc2Stations.size()).getString(),
                    130, 66, 106, 0.65F, 0x55FFFF);
            for (int i = 0; i < Math.min(3, sync.nearbyLc2Stations.size()); i++) {
                String label = Lc2SoftBridge.formatStationLabel(sync.nearbyLc2Stations.get(i));
                TyC2UiWidgets.drawFittedString(poseStack, font, label, 130, 75 + i * 9, 106, 0.55F, 0xAADDEE);
            }
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.trade_volume", TyC2UiWidgets.compactIc(sync.cumulativeVolume)).getString(),
                    130, 72, 106, 0.65F, 0xAADDEE);
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("biome.tyc2." + sync.biomeStyleId).getString(),
                    130, 84, 106, 0.72F, 0xCCCCCC);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        // Render ItemStacks inside Tab 0 (Orders) and Tab 1 (Warehouse grid)
        ItemStack hoveredCustom = ItemStack.EMPTY;
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
        }

        if (!hoveredCustom.isEmpty()) {
            renderTooltip(poseStack, hoveredCustom, mouseX, mouseY);
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
