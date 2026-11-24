package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.gui.CommTerminalMenu;
import com.tycooncraft.tyc2.forge.network.C2STerminalActionPacket;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CTerminalSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Forge 1.19.2 remote management screen for {@code tyc2:comm_terminal} matching
 * {@code gui/comm_terminal.png} (248x210): up to 16 bound villages on the left rail,
 * battery gauge at top-right, and remote Orders / Warehouse Stock / CPI Chart / Growth
 * operations working even across unloaded chunks!
 */
public class CommTerminalScreen extends AbstractContainerScreen<CommTerminalMenu> {

    private static final ResourceLocation TEXTURE = TyC2Ids.id("textures/gui/comm_terminal.png");
    private static final int RAIL_VISIBLE = 8;
    private static final int ORDERS_PER_PAGE = 5;
    private static final int MAX_AID_ROWS = 3;

    @Nullable
    private S2CTerminalSyncPacket sync;

    private int subTab = 0; // 0=Orders, 1=Stock, 2=CPI, 3=Growth
    private boolean growthAidMode = false; // false=Expansion, true=Aid
    private int railOffset = 0;
    private boolean showBuyOrders = false;
    private int orderPage = 0;
    private int qtyPreset = 0; // 0=1, 1=8, 2=Max
    private int selectedWorkplaceIdx = 0;
    private boolean aidLargeSize = false;

    private final TyC2UiWidgets.CompactButton[] railBtns = new TyC2UiWidgets.CompactButton[RAIL_VISIBLE];
    private TyC2UiWidgets.CompactButton railPrevBtn;
    private TyC2UiWidgets.CompactButton railNextBtn;
    private TyC2UiWidgets.CompactButton unbindBtn;

    private final TyC2UiWidgets.CompactButton[] subTabBtns = new TyC2UiWidgets.CompactButton[4];

    // Orders sub-tab
    private TyC2UiWidgets.CompactButton sideBtn;
    private TyC2UiWidgets.CompactButton qtyBtn;
    private TyC2UiWidgets.CompactButton orderPrevBtn;
    private TyC2UiWidgets.CompactButton orderNextBtn;
    private final TyC2UiWidgets.CompactButton[] orderActBtns = new TyC2UiWidgets.CompactButton[ORDERS_PER_PAGE];
    private final TyC2UiWidgets.CompactButton[] orderAutoBtns = new TyC2UiWidgets.CompactButton[ORDERS_PER_PAGE];

    // Growth sub-tab mode switcher + Expansion & Aid controls
    private TyC2UiWidgets.CompactButton growthExpModeBtn;
    private TyC2UiWidgets.CompactButton growthAidModeBtn;
    private TyC2UiWidgets.CompactButton upgradeBtn;
    private TyC2UiWidgets.CompactButton expDeliverBtn;
    private TyC2UiWidgets.CompactButton expAutoBtn;
    private TyC2UiWidgets.CompactButton expPauseBtn;
    private TyC2UiWidgets.CompactButton aidProfPrevBtn;
    private TyC2UiWidgets.CompactButton aidProfNextBtn;
    private TyC2UiWidgets.CompactButton aidSizeBtn;
    private TyC2UiWidgets.CompactButton aidStartBtn;
    private final TyC2UiWidgets.CompactButton[] aidCancelBtns = new TyC2UiWidgets.CompactButton[MAX_AID_ROWS];

    public CommTerminalScreen(CommTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 210;
        this.titleLabelX = 24;
        this.titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();

        // Left village rail (8, 22, 64x178)
        for (int i = 0; i < RAIL_VISIBLE; i++) {
            final int row = i;
            railBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 10, topPos + 34 + i * 17, 60, 15, Component.empty(),
                    btn -> {
                        int idx = railOffset + row;
                        if (sync != null && idx < sync.boundVillages.size()) {
                            orderPage = 0;
                            TyC2ForgeNetwork.sendToServer(new C2STerminalActionPacket(
                                    C2STerminalActionPacket.Action.SELECT_VILLAGE, idx, ""
                            ));
                        }
                    }
            ));
        }
        railPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 172, 16, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    railOffset = Math.max(0, railOffset - RAIL_VISIBLE);
                    refreshWidgets();
                }
        ));
        railNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 54, topPos + 172, 16, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    if (sync != null && railOffset + RAIL_VISIBLE < sync.boundVillages.size()) {
                        railOffset += RAIL_VISIBLE;
                        refreshWidgets();
                    }
                }
        ));
        unbindBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 186, 60, 12, Component.translatable("gui.tyc2.terminal.unbind"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.UNBIND_VILLAGE
                ))
        ).setAccentColor(0xFF883333));

        // Main LCD (76, 22, 164x178) -> 4 sub-tabs of 40px at y=24 (100% localized)
        String[] subKeys = {
                "gui.tyc2.terminal.tab.orders",
                "gui.tyc2.terminal.tab.stock",
                "gui.tyc2.terminal.tab.cpi",
                "gui.tyc2.terminal.tab.growth"
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            subTabBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 78 + i * 40, topPos + 24, 40, 13, Component.translatable(subKeys[i]),
                    btn -> {
                        subTab = idx;
                        refreshWidgets();
                    }
            ));
        }

        // Orders sub-tab controls
        sideBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 78, topPos + 54, 72, 12, Component.translatable("gui.tyc2.hub.sell_orders"),
                btn -> {
                    showBuyOrders = !showBuyOrders;
                    orderPage = 0;
                    refreshWidgets();
                }
        ));
        qtyBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 152, topPos + 54, 52, 12, Component.translatable("gui.tyc2.hub.qty_preset", 1),
                btn -> {
                    qtyPreset = (qtyPreset + 1) % 3;
                    refreshWidgets();
                }
        ));
        orderPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 206, topPos + 54, 15, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    orderPage = Math.max(0, orderPage - 1);
                    refreshWidgets();
                }
        ));
        orderNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 223, topPos + 54, 15, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    orderPage++;
                    refreshWidgets();
                }
        ));

        for (int i = 0; i < ORDERS_PER_PAGE; i++) {
            final int row = i;
            int ry = topPos + 70 + i * 21;
            orderActBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 186, ry, 34, 15, Component.translatable("gui.tyc2.hub.buy"),
                    btn -> executeRemoteOrder(row)
            ));
            orderAutoBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 221, ry, 17, 15, Component.translatable("gui.tyc2.hub.btn.auto_tiny.off"),
                    btn -> toggleRemoteOrderAuto(row)
            ));
        }

        // Growth sub-tab controls (Expansion / Aid mode toggle)
        growthExpModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 78, topPos + 52, 79, 11, Component.translatable("gui.tyc2.hub.tab.expansion"),
                btn -> {
                    growthAidMode = false;
                    refreshWidgets();
                }
        ));
        growthAidModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 159, topPos + 52, 79, 11, Component.translatable("gui.tyc2.hub.tab.aid"),
                btn -> {
                    growthAidMode = true;
                    refreshWidgets();
                }
        ));
        upgradeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 85, 156, 13,
                Component.translatable("gui.tyc2.terminal.upgrade_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.UPGRADE_VILLAGE
                ))
        ));
        expDeliverBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 129, 74, 13,
                Component.translatable("gui.tyc2.terminal.deliver_cost_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.DELIVER_EXPANSION
                ))
        ));
        expAutoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 156, topPos + 129, 40, 13, Component.translatable("gui.tyc2.hub.auto_short.off"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.TOGGLE_EXPANSION_AUTO
                ))
        ));
        expPauseBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 198, topPos + 129, 38, 13, Component.translatable("gui.tyc2.hub.pause_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.TOGGLE_EXPANSION_PAUSE
                ))
        ));

        // Remote Aid Construction controls inside Growth tab
        aidProfPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 86, 14, 14, Component.translatable("gui.tyc2.prev"),
                btn -> stepWorkplace(-1)
        ));
        aidProfNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 224, topPos + 86, 14, 14, Component.translatable("gui.tyc2.next"),
                btn -> stepWorkplace(1)
        ));
        aidSizeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 103, 76, 13, Component.translatable("gui.tyc2.aid.size.small"),
                btn -> {
                    aidLargeSize = !aidLargeSize;
                    refreshWidgets();
                }
        ));
        aidStartBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 158, topPos + 103, 80, 13,
                Component.translatable("gui.tyc2.aid.start_cost_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> sendRemoteStartAid()
        ).setAccentColor(0xFF2E7D32));
        for (int i = 0; i < MAX_AID_ROWS; i++) {
            final int row = i;
            aidCancelBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 202, topPos + 137 + i * 15, 36, 13, Component.translatable("gui.tyc2.aid.cancel_btn"),
                    btn -> sendRemoteCancelAid(row)
            ).setAccentColor(0xFF992222));
        }

        refreshWidgets();
        if (menu.getLatestSync() != null) {
            onSync(menu.getLatestSync());
        }
        TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(C2STerminalActionPacket.Action.REQUEST_SYNC));
    }

    public void onSync(S2CTerminalSyncPacket pkt) {
        this.sync = pkt;
        this.menu.setLatestSync(pkt);
        refreshWidgets();
    }

    private List<S2CHubSyncPacket.OrderDto> filteredOrders() {
        List<S2CHubSyncPacket.OrderDto> list = new ArrayList<>();
        if (sync == null || sync.selectedVillage == null) {
            return list;
        }
        for (S2CHubSyncPacket.OrderDto o : sync.selectedVillage.orders) {
            if (o.isVillageBuy() == showBuyOrders && o.remainingQty() > 0) {
                list.add(o);
            }
        }
        return list;
    }

    private void executeRemoteOrder(int row) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int idx = orderPage * ORDERS_PER_PAGE + row;
        if (idx < 0 || idx >= list.size()) {
            return;
        }
        S2CHubSyncPacket.OrderDto o = list.get(idx);
        int qty = switch (qtyPreset) {
            case 0 -> 1;
            case 1 -> Math.min(8, o.remainingQty());
            default -> o.remainingQty();
        };
        C2STerminalActionPacket.Action act = o.isVillageBuy()
                ? C2STerminalActionPacket.Action.SELL_TO_VILLAGE
                : C2STerminalActionPacket.Action.BUY_FROM_VILLAGE;
        TyC2ForgeNetwork.sendToServer(new C2STerminalActionPacket(act, qty, o.orderId()));
    }

    private void toggleRemoteOrderAuto(int row) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int idx = orderPage * ORDERS_PER_PAGE + row;
        if (idx < 0 || idx >= list.size()) {
            return;
        }
        TyC2ForgeNetwork.sendToServer(new C2STerminalActionPacket(
                C2STerminalActionPacket.Action.TOGGLE_ORDER_AUTO, 0, list.get(idx).orderId()
        ));
    }

    private void stepWorkplace(int delta) {
        if (sync == null || sync.selectedVillage == null || sync.selectedVillage.availableWorkplaces.isEmpty()) {
            return;
        }
        int n = sync.selectedVillage.availableWorkplaces.size();
        selectedWorkplaceIdx = ((selectedWorkplaceIdx + delta) % n + n) % n;
        refreshWidgets();
    }

    @Nullable
    private S2CHubSyncPacket.WorkplaceDto currentWorkplace() {
        if (sync == null || sync.selectedVillage == null || sync.selectedVillage.availableWorkplaces.isEmpty()) {
            return null;
        }
        int idx = Math.max(0, Math.min(selectedWorkplaceIdx, sync.selectedVillage.availableWorkplaces.size() - 1));
        selectedWorkplaceIdx = idx;
        return sync.selectedVillage.availableWorkplaces.get(idx);
    }

    private void sendRemoteStartAid() {
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        if (wp == null) {
            return;
        }
        TyC2ForgeNetwork.sendToServer(new C2STerminalActionPacket(
                C2STerminalActionPacket.Action.START_AID,
                aidLargeSize ? 1 : 0,
                wp.professionId()
        ));
    }

    private void sendRemoteCancelAid(int row) {
        if (sync == null || sync.selectedVillage == null
                || row < 0 || row >= sync.selectedVillage.aidProjects.size()) {
            return;
        }
        S2CHubSyncPacket.AidProjectDto aid = sync.selectedVillage.aidProjects.get(row);
        if (!aid.canCancel()) {
            return;
        }
        TyC2ForgeNetwork.sendToServer(new C2STerminalActionPacket(
                C2STerminalActionPacket.Action.CANCEL_AID,
                0,
                aid.projectId()
        ));
    }

    private void refreshWidgets() {
        int count = sync == null ? 0 : sync.boundVillages.size();
        for (int i = 0; i < RAIL_VISIBLE; i++) {
            int idx = railOffset + i;
            boolean vis = sync != null && idx < count;
            railBtns[i].visible = vis;
            if (vis) {
                S2CTerminalSyncPacket.BoundVillageDto v = sync.boundVillages.get(idx);
                railBtns[i].setMessage(Component.translatable("gui.tyc2.terminal.level_prefix", v.level(), v.name()));
                railBtns[i].setHighlighted(idx == sync.activeIndex);
            }
        }
        railPrevBtn.visible = count > RAIL_VISIBLE;
        railNextBtn.visible = count > RAIL_VISIBLE;
        unbindBtn.visible = count > 0;

        boolean hasSel = sync != null && sync.selectedVillage != null;
        for (int i = 0; i < 4; i++) {
            subTabBtns[i].visible = hasSel;
            subTabBtns[i].setHighlighted(i == subTab);
        }

        boolean t0 = hasSel && subTab == 0;
        List<S2CHubSyncPacket.OrderDto> orders = filteredOrders();
        int maxPage = Math.max(0, (orders.size() - 1) / ORDERS_PER_PAGE);
        orderPage = Math.min(orderPage, maxPage);

        sideBtn.visible = t0;
        sideBtn.setMessage(Component.translatable(showBuyOrders ? "gui.tyc2.hub.buy_orders" : "gui.tyc2.hub.sell_orders"));
        qtyBtn.visible = t0;
        qtyBtn.setMessage(switch (qtyPreset) {
            case 0 -> Component.translatable("gui.tyc2.hub.qty_preset", 1);
            case 1 -> Component.translatable("gui.tyc2.hub.qty_preset", 8);
            default -> Component.translatable("gui.tyc2.hub.qty_preset", Component.translatable("gui.tyc2.hub.qty_all"));
        });
        orderPrevBtn.visible = t0;
        orderNextBtn.visible = t0;
        orderPrevBtn.active = orderPage > 0;
        orderNextBtn.active = orderPage < maxPage;

        boolean hasPowerTrade = sync != null && sync.batteryEu >= TriModePowerMath.TERMINAL_COST_TRADE_EU;
        for (int i = 0; i < ORDERS_PER_PAGE; i++) {
            int idx = orderPage * ORDERS_PER_PAGE + i;
            boolean has = t0 && idx < orders.size();
            orderActBtns[i].visible = has;
            orderActBtns[i].active = has && hasPowerTrade;
            orderAutoBtns[i].visible = has && orders.get(idx).isVillageBuy();
            if (has) {
                S2CHubSyncPacket.OrderDto o = orders.get(idx);
                orderActBtns[i].setMessage(Component.translatable(o.isVillageBuy() ? "gui.tyc2.hub.deliver" : "gui.tyc2.hub.buy"));
                orderAutoBtns[i].setHighlighted(o.autoFulfill());
                orderAutoBtns[i].setMessage(Component.translatable(
                        o.autoFulfill() ? "gui.tyc2.hub.btn.auto_tiny.on" : "gui.tyc2.hub.btn.auto_tiny.off"
                ));
            }
        }

        boolean t3 = hasSel && subTab == 3;
        boolean t3Exp = t3 && !growthAidMode;
        boolean t3Aid = t3 && growthAidMode;
        boolean hasPowerUpg = sync != null && sync.batteryEu >= TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU;

        growthExpModeBtn.visible = t3;
        growthExpModeBtn.setHighlighted(t3Exp);
        growthAidModeBtn.visible = t3;
        growthAidModeBtn.setHighlighted(t3Aid);

        upgradeBtn.visible = t3Exp;
        upgradeBtn.active = hasSel && sync.selectedVillage.canUpgrade && hasPowerUpg;
        expDeliverBtn.visible = t3Exp;
        expDeliverBtn.active = hasSel && hasPowerUpg;
        expAutoBtn.visible = t3Exp && hasSel && sync.selectedVillage.hasExpansion;
        if (hasSel && sync.selectedVillage.hasExpansion) {
            expAutoBtn.setHighlighted(sync.selectedVillage.expAutoFulfill);
            expAutoBtn.setMessage(Component.translatable(
                    sync.selectedVillage.expAutoFulfill ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        expPauseBtn.visible = t3Exp;
        if (hasSel) {
            expPauseBtn.setHighlighted(sync.selectedVillage.expansionPaused);
            expPauseBtn.setMessage(Component.translatable(
                    sync.selectedVillage.expansionPaused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
        }

        // Aid sub-mode widgets inside Growth tab
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        boolean hasWps = hasSel && !sync.selectedVillage.availableWorkplaces.isEmpty();
        aidProfPrevBtn.visible = t3Aid;
        aidProfPrevBtn.active = hasWps && sync.selectedVillage.availableWorkplaces.size() > 1;
        aidProfNextBtn.visible = t3Aid;
        aidProfNextBtn.active = hasWps && sync.selectedVillage.availableWorkplaces.size() > 1;
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
        boolean sizeUnlocked = wp != null && (aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked());
        boolean queueOpen = hasSel && sync.selectedVillage.aidProjects.size() < sync.selectedVillage.maxQueuedAid;
        aidStartBtn.visible = t3Aid;
        aidStartBtn.active = t3Aid && wp != null && sizeUnlocked && queueOpen
                && hasPowerUpg && !sync.selectedVillage.depopulatedPaused;
        aidStartBtn.setHighlighted(aidStartBtn.active);

        for (int i = 0; i < MAX_AID_ROWS; i++) {
            boolean showCancel = t3Aid && hasSel && i < sync.selectedVillage.aidProjects.size()
                    && sync.selectedVillage.aidProjects.get(i).canCancel();
            aidCancelBtns[i].visible = showCancel;
            aidCancelBtns[i].active = showCancel;
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Top battery frame (178, 6, 58x12): green (0,216,56x10) or red (0,228,56x10)
        int eu = sync == null ? 0 : sync.batteryEu;
        int maxEu = sync == null ? TriModePowerMath.TERMINAL_MAX_CAPACITY_EU : Math.max(1, sync.maxBatteryEu);
        if (eu > 0) {
            int fillW = Math.min(56, Math.max(1, (eu * 56) / maxEu));
            int spriteV = eu < 2000 ? 228 : 216;
            blit(poseStack, leftPos + 179, topPos + 7, 0, spriteV, fillW, 10);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0xDDEEFF);
        if (sync == null) {
            return;
        }
        // Bound count in rail header (100% localized)
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.terminal.bound_count", sync.boundVillages.size()).getString(),
                10, 24, 60, 0.56F, 0x55FFFF);

        if (sync.selectedVillage == null) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.no_villages").getString(),
                    80, 56, 156, 0.65F, 0xFFCC66);
            return;
        }

        S2CHubSyncPacket v = sync.selectedVillage;
        String vHead = Component.translatable("gui.tyc2.terminal.level_prefix", v.level, v.villageName).getString()
                + "  " + Component.translatable("gui.tyc2.hub.village_funds", TyC2UiWidgets.compactIc(v.villageBalance)).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, vHead, 80, 41, 158, 0.68F, 0xFFDD55);

        switch (subTab) {
            case 0 -> renderRemoteOrders(poseStack);
            case 1 -> renderRemoteStock(poseStack, v);
            case 2 -> renderRemoteCpi(poseStack, v);
            case 3 -> renderRemoteGrowth(poseStack, v);
        }

        // Bottom feedback & Bank balance line inside LCD (100% localized; wraps cleanly for multi-item status)
        boolean hasFeedback = !sync.feedback.isEmpty();
        Component footerComp = hasFeedback
                ? sync.feedbackComponent
                : Component.translatable(
                        "gui.tyc2.terminal.footer",
                        TyC2UiWidgets.compactIc(v.activeBankBalance),
                        TriModePowerMath.TERMINAL_COST_TRADE_EU,
                        TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                );
        String footer = footerComp.getString();
        if (hasFeedback && font.width(footer) > 272) {
            List<FormattedCharSequence> fbLines = font.split(footerComp, 336);
            poseStack.pushPose();
            poseStack.translate(80.0F, 183.0F, 0.0F);
            poseStack.scale(0.46F, 0.46F, 1.0F);
            for (int i = 0; i < Math.min(2, fbLines.size()); i++) {
                font.draw(poseStack, fbLines.get(i), 0, i * 11, 0xFFDD77);
            }
            poseStack.popPose();
        } else {
            TyC2UiWidgets.drawFittedString(
                    poseStack, font, footer, 80, 188, 158, 0.58F, hasFeedback ? 0xFFDD77 : 0x88EECC
            );
        }
    }

    private void renderRemoteOrders(PoseStack poseStack) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        if (list.isEmpty()) {
            font.draw(poseStack, Component.translatable("gui.tyc2.hub.no_orders"), 80, 80, 0xFFAA66);
            return;
        }
        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
            S2CHubSyncPacket.OrderDto o = list.get(start + i);
            int y = 71 + i * 21;
            String name = TyC2Ids.localizedItemName(o.itemId(), o.enchantSpec());
            TyC2UiWidgets.drawFittedString(poseStack, font, name, 96, y, 88, 0.68F, o.seasonalBonus() ? 0xFFEE66 : 0xECEFF4);
            String detail = Component.translatable("gui.tyc2.hub.order_progress", o.fulfilledQty(), o.totalQty()).getString()
                    + " · " + Component.translatable("gui.tyc2.hub.unit_price", o.unitPrice()).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, detail, 96, y + 9, 88, 0.68F, 0x66FF88);
        }
    }

    private void renderRemoteStock(PoseStack poseStack, S2CHubSyncPacket v) {
        String inHead = Component.translatable("gui.tyc2.hub.zone.inbound").getString()
                + " (" + Component.translatable("gui.tyc2.hub.zone.used", v.inboundUsed).getString() + ")";
        TyC2UiWidgets.drawFittedString(poseStack, font, inHead, 80, 56, 156, 0.72F, 0x77BBFF);
        for (int i = 0; i < Math.min(5, sync.inboundSummary.size()); i++) {
            var e = sync.inboundSummary.get(i);
            String label = shortItemName(e.itemId()) + " x" + e.count();
            TyC2UiWidgets.drawFittedString(poseStack, font, label, 84, 67 + i * 10, 154, 0.70F, 0xDDEEFF);
        }
        String outHead = Component.translatable("gui.tyc2.hub.zone.outbound").getString()
                + " (" + Component.translatable("gui.tyc2.hub.zone.used", v.outboundUsed).getString() + ")";
        TyC2UiWidgets.drawFittedString(poseStack, font, outHead, 80, 122, 156, 0.72F, 0xFFBB66);
        for (int i = 0; i < Math.min(5, sync.outboundSummary.size()); i++) {
            var e = sync.outboundSummary.get(i);
            String label = shortItemName(e.itemId()) + " x" + e.count();
            TyC2UiWidgets.drawFittedString(poseStack, font, label, 84, 133 + i * 10, 154, 0.70F, 0xFFEECC);
        }
    }

    private void renderRemoteCpi(PoseStack poseStack, S2CHubSyncPacket v) {
        int col = v.cpi > 1.08 ? 0xFF7755 : (v.cpi < 0.92 ? 0x55DDFF : 0x66FF88);
        String regimeKey = switch (v.inflationState) {
            case "DEFLATION" -> "gui.tyc2.economy.deflation";
            case "STABLE" -> "gui.tyc2.economy.stable";
            default -> "gui.tyc2.economy.inflation";
        };
        String cpiLine = Component.translatable("gui.tyc2.economy.cpi", String.format(Locale.ROOT, "%.2f", v.cpi)).getString()
                + " (" + Component.translatable(regimeKey).getString() + ")";
        TyC2UiWidgets.drawFittedString(poseStack, font, cpiLine, 80, 55, 156, 0.65F, col);
        String macroLine = Component.translatable("gui.tyc2.economy.money_supply", TyC2UiWidgets.compactIc(v.moneySupply)).getString()
                + " · " + Component.translatable("season.tyc2." + v.seasonKey).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, macroLine, 80, 66, 156, 0.60F, 0xAADDEE);
        TyC2UiWidgets.drawCpiLineChart(poseStack, font, 80, 78, 156, 102, v.cpiHistory);
    }

    private void renderRemoteGrowth(PoseStack poseStack, S2CHubSyncPacket v) {
        if (growthAidMode) {
            renderRemoteAid(poseStack, v);
            return;
        }
        if (v.level >= 10) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.max_level").getString(), 80, 67, 156, 0.72F, 0x66FF88);
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.upgrade_cost", v.nextSinkCost).getString(),
                    80, 66, 156, 0.68F, 0xFFDD55);
            String req = Component.translatable(
                    "gui.tyc2.hub.upgrade_req",
                    v.population + "/" + v.nextMinPop,
                    TyC2UiWidgets.compactIc(v.cumulativeVolume) + "/" + TyC2UiWidgets.compactIc(v.nextMinVol),
                    TyC2UiWidgets.compactIc(v.villageBalance) + "/" + TyC2UiWidgets.compactIc(v.nextMinBal)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, req, 80, 75, 156, 0.56F, 0xAADDEE);
        }

        if (v.hasJob) {
            int pct = v.jobTotal > 0 ? (v.jobPlaced * 100) / v.jobTotal : 0;
            String msg = Component.translatable(
                    "gui.tyc2.expansion.building",
                    Component.translatable("project.tyc2." + v.jobProjectKey),
                    pct
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, msg, 80, 103, 156, 0.65F, 0x66FF88);
        } else if (v.hasExpansion) {
            String proj = Component.translatable(
                    "gui.tyc2.expansion.project",
                    Component.translatable("project.tyc2." + v.expProjectKey)
            ).getString() + " (" + v.expBudget + " IC)";
            TyC2UiWidgets.drawFittedString(poseStack, font, proj, 80, 101, 156, 0.55F, 0xFFDD77);

            int matCount = v.expMaterials.size();
            int splitIdx = matCount <= 2 ? matCount : (matCount + 1) / 2;
            StringBuilder line1 = new StringBuilder();
            StringBuilder line2 = new StringBuilder();
            boolean line1AllMet = true;
            boolean line2AllMet = true;
            for (int i = 0; i < matCount; i++) {
                S2CHubSyncPacket.MatDto m = v.expMaterials.get(i);
                boolean met = m.delivered() >= m.required();
                StringBuilder dst = i < splitIdx ? line1 : line2;
                if (i < splitIdx) {
                    line1AllMet &= met;
                } else {
                    line2AllMet &= met;
                }
                if (dst.length() > 0) {
                    dst.append(" \u00B7 ");
                }
                dst.append(shortItemName(m.itemId()))
                        .append(' ')
                        .append(m.delivered())
                        .append('/')
                        .append(m.required());
            }
            if (line1.length() > 0) {
                TyC2UiWidgets.drawFittedString(
                        poseStack, font, line1.toString(), 80, 110, 156, 0.45F,
                        line1AllMet ? 0x66FF99 : 0xFFCC66
                );
            }
            if (line2.length() > 0) {
                TyC2UiWidgets.drawFittedString(
                        poseStack, font, line2.toString(), 80, 118, 156, 0.45F,
                        line2AllMet ? 0x66FF99 : 0xFFCC66
                );
            }
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.none").getString(), 80, 107, 156, 0.68F, 0x99AABB);
        }

        // Unemployment & bonus summary line
        String unempLine = Component.translatable(
                "gui.tyc2.hub.unemployment", v.unemploymentPct, v.unemployedCount, v.workforceCount
        ).getString();
        if (v.employmentBonusActive) {
            unempLine += "  " + Component.translatable(
                    "gui.tyc2.hub.unemployment_bonus", v.restockBonusPct, v.qtyBonusPct
            ).getString();
        }
        TyC2UiWidgets.drawFittedString(poseStack, font, unempLine, 80, 146, 156, 0.50F,
                v.employmentBonusActive ? 0x66FF88 : 0xFFCC66);

        if (!v.nearbyLc2Stations.isEmpty()) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", v.nearbyLc2Stations.size()).getString(),
                    80, 157, 156, 0.62F, 0x55FFFF);
            for (int i = 0; i < Math.min(2, v.nearbyLc2Stations.size()); i++) {
                String label = Lc2SoftBridge.formatStationLabel(v.nearbyLc2Stations.get(i));
                TyC2UiWidgets.drawFittedString(poseStack, font, label, 82, 166 + i * 8, 154, 0.52F, 0xAADDEE);
            }
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.no_lc2").getString(),
                    80, 159, 156, 0.65F, 0x8899AA);
        }
    }

    private void renderRemoteAid(PoseStack poseStack, S2CHubSyncPacket v) {
        // Unemployment & bonus readout (y=66..83)
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.unemployment", v.unemploymentPct, v.unemployedCount, v.workforceCount).getString(),
                80, 66, 156, 0.62F, v.employmentBonusActive ? 0x66FF88 : 0xFFCC66);
        String bonusLine = v.employmentBonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", v.restockBonusPct, v.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", v.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, bonusLine,
                80, 75, 156, 0.50F, v.employmentBonusActive ? 0x66FF99 : 0x99AABB);

        // Selected workplace name (icon rendered in render())
        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        if (wp != null) {
            String profName = TyC2UiWidgets.localizedProfessionName(
                    wp.professionId(), wp.archetypeName(), wp.displayNameFallback()
            );
            TyC2UiWidgets.drawFittedString(poseStack, font, profName, 114, 89, 108, 0.68F, 0xFFE888);
            long cost = aidLargeSize ? wp.costLargeIc() : wp.costSmallIc();
            boolean unlocked = aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked();
            String costGather = Component.translatable("gui.tyc2.aid.cost", cost).getString()
                    + " · " + Component.translatable(
                            "gui.tyc2.aid.gather_rate",
                            String.format(Locale.ROOT, "%.2f", v.gatherSpeedMult),
                            v.unemployedCount
                    ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, costGather,
                    80, 118, 156, 0.48F, unlocked ? 0x88EECC : 0xFF8866);
        }

        // Queue header & up to 3 aid rows (y=128..181)
        TyC2UiWidgets.drawFittedString(poseStack, font,
                Component.translatable("gui.tyc2.aid.queue_header", v.aidProjects.size(), v.maxQueuedAid).getString(),
                80, 128, 156, 0.64F, 0x55FFFF);
        if (v.aidProjects.isEmpty()) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    80, 144, 156, 0.66F, 0x8899AA);
            return;
        }
        int rows = Math.min(MAX_AID_ROWS, v.aidProjects.size());
        for (int i = 0; i < rows; i++) {
            S2CHubSyncPacket.AidProjectDto aid = v.aidProjects.get(i);
            int ry = 137 + i * 15;
            int rowW = aid.canCancel() ? 120 : 158;
            GuiComponent.fill(poseStack, 80, ry, 80 + rowW, ry + 14, 0x88101822);
            int fillW = Math.max(0, Math.min(rowW - 2, ((rowW - 2) * aid.progressPct()) / 100));
            int barCol = "BUILDING".equals(aid.statusName())
                    ? 0xFF44DD77
                    : ("NO_SITE".equals(aid.statusName()) ? 0xFFEE6644 : 0xFF44AADD);
            if (fillW > 0) {
                GuiComponent.fill(poseStack, 81, ry + 12, 81 + fillW, ry + 14, barCol);
            }
            String profName = TyC2UiWidgets.localizedProfessionName(
                    aid.professionId(), aid.archetypeName(), aid.displayNameFallback()
            );
            String line = profName + (aid.large() ? " [L] · " : " [S] · ") + TyC2UiWidgets.formatAidStatus(aid);
            int col = "NO_SITE".equals(aid.statusName()) ? 0xFF9966 : 0xFFE888;
            TyC2UiWidgets.drawFittedString(poseStack, font, line, 82, ry + 2, rowW - 4, 0.50F, col);
        }
    }

    private static String shortItemName(String itemId) {
        return TyC2Ids.localizedItemName(itemId, null);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        ItemStack hovered = ItemStack.EMPTY;
        List<Component> hoveredAidTooltip = null;
        if (sync != null && sync.selectedVillage != null && subTab == 0) {
            List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
                S2CHubSyncPacket.OrderDto o = list.get(start + i);
                ItemStack icon = TyC2Ids.createStack(o.itemId(), 1, "", o.enchantSpec());
                int ix = leftPos + 78;
                int iy = topPos + 70 + i * 21;
                if (!icon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(icon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hovered = icon;
                    }
                }
            }
        } else if (sync != null && sync.selectedVillage != null && subTab == 3 && growthAidMode) {
            S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
            if (wp != null) {
                ItemStack wsIcon = TyC2Ids.createBlockOrItemIcon(wp.workstationBlockId());
                int ix = leftPos + 96;
                int iy = topPos + 85;
                if (!wsIcon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(wsIcon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hovered = wsIcon;
                    }
                }
            }
            int relXHover = mouseX - leftPos;
            int relYHover = mouseY - topPos;
            int rows = Math.min(MAX_AID_ROWS, sync.selectedVillage.aidProjects.size());
            for (int i = 0; i < rows; i++) {
                S2CHubSyncPacket.AidProjectDto aid = sync.selectedVillage.aidProjects.get(i);
                int ry = 137 + i * 15;
                int maxHoverX = aid.canCancel() ? 200 : 238;
                if (relXHover >= 80 && relXHover <= maxHoverX && relYHover >= ry && relYHover <= ry + 14) {
                    hoveredAidTooltip = TyC2UiWidgets.buildAidMaterialsTooltip(aid);
                    break;
                }
            }
        }

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (!hovered.isEmpty()) {
            renderTooltip(poseStack, hovered, mouseX, mouseY);
        } else if (hoveredAidTooltip != null && !hoveredAidTooltip.isEmpty()) {
            renderComponentTooltip(poseStack, hoveredAidTooltip, mouseX, mouseY);
        } else if (sync != null && relX >= 178 && relX <= 236 && relY >= 6 && relY <= 18) {
            renderTooltip(
                    poseStack,
                    Component.translatable("gui.tyc2.terminal.battery", sync.batteryEu, sync.maxBatteryEu),
                    mouseX, mouseY
            );
        } else if (sync != null && !sync.feedback.isEmpty()
                && relX >= 78 && relX <= 238 && relY >= 182 && relY <= 198) {
            renderTooltip(poseStack, font.split(sync.feedbackComponent, 220), mouseX, mouseY);
        } else {
            renderTooltip(poseStack, mouseX, mouseY);
        }
    }
}
