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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    @Nullable
    private S2CTerminalSyncPacket sync;

    private int subTab = 0; // 0=Orders, 1=Stock, 2=CPI, 3=Growth
    private int railOffset = 0;
    private boolean showBuyOrders = false;
    private int orderPage = 0;
    private int qtyPreset = 0; // 0=1, 1=8, 2=Max

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

    // Growth sub-tab
    private TyC2UiWidgets.CompactButton upgradeBtn;
    private TyC2UiWidgets.CompactButton expDeliverBtn;
    private TyC2UiWidgets.CompactButton expAutoBtn;
    private TyC2UiWidgets.CompactButton expPauseBtn;

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

        // Growth sub-tab controls
        upgradeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 80, 156, 14,
                Component.translatable("gui.tyc2.terminal.upgrade_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.UPGRADE_VILLAGE
                ))
        ));
        expDeliverBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 132, 74, 14,
                Component.translatable("gui.tyc2.terminal.deliver_cost_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.DELIVER_EXPANSION
                ))
        ));
        expAutoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 156, topPos + 132, 40, 14, Component.translatable("gui.tyc2.hub.auto_short.off"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.TOGGLE_EXPANSION_AUTO
                ))
        ));
        expPauseBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 198, topPos + 132, 38, 14, Component.translatable("gui.tyc2.hub.pause_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.TOGGLE_EXPANSION_PAUSE
                ))
        ));

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
        boolean hasPowerUpg = sync != null && sync.batteryEu >= TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU;
        upgradeBtn.visible = t3;
        upgradeBtn.active = hasSel && sync.selectedVillage.canUpgrade && hasPowerUpg;
        expDeliverBtn.visible = t3;
        expDeliverBtn.active = hasSel && hasPowerUpg;
        expAutoBtn.visible = t3 && hasSel && sync.selectedVillage.hasExpansion;
        if (hasSel && sync.selectedVillage.hasExpansion) {
            expAutoBtn.setHighlighted(sync.selectedVillage.expAutoFulfill);
            expAutoBtn.setMessage(Component.translatable(
                    sync.selectedVillage.expAutoFulfill ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        expPauseBtn.visible = t3;
        if (hasSel) {
            expPauseBtn.setHighlighted(sync.selectedVillage.expansionPaused);
            expPauseBtn.setMessage(Component.translatable(
                    sync.selectedVillage.expansionPaused ? "gui.tyc2.hub.resume_btn" : "gui.tyc2.hub.pause_btn"
            ));
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

        // Bottom feedback & Bank balance line inside LCD (100% localized)
        String footer = !sync.feedback.isEmpty()
                ? sync.feedback
                : Component.translatable(
                        "gui.tyc2.terminal.footer",
                        TyC2UiWidgets.compactIc(v.activeBankBalance),
                        TriModePowerMath.TERMINAL_COST_TRADE_EU,
                        TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                ).getString();
        TyC2UiWidgets.drawFittedString(poseStack, font, footer, 80, 188, 158, 0.62F, 0x88EECC);
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
        if (v.level >= 10) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.max_level").getString(), 80, 56, 156, 0.75F, 0x66FF88);
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.hub.upgrade_cost", v.nextSinkCost).getString(),
                    80, 56, 156, 0.72F, 0xFFDD55);
            String req = Component.translatable(
                    "gui.tyc2.hub.upgrade_req",
                    v.population + "/" + v.nextMinPop,
                    TyC2UiWidgets.compactIc(v.cumulativeVolume) + "/" + TyC2UiWidgets.compactIc(v.nextMinVol),
                    TyC2UiWidgets.compactIc(v.villageBalance) + "/" + TyC2UiWidgets.compactIc(v.nextMinBal)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, req, 80, 67, 156, 0.60F, 0xAADDEE);
        }

        if (v.hasJob) {
            int pct = v.jobTotal > 0 ? (v.jobPlaced * 100) / v.jobTotal : 0;
            String msg = Component.translatable(
                    "gui.tyc2.expansion.building",
                    Component.translatable("project.tyc2." + v.jobProjectKey),
                    pct
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, msg, 80, 102, 156, 0.68F, 0x66FF88);
        } else if (v.hasExpansion) {
            String proj = Component.translatable(
                    "gui.tyc2.expansion.project",
                    Component.translatable("project.tyc2." + v.expProjectKey)
            ).getString();
            TyC2UiWidgets.drawFittedString(poseStack, font, proj, 80, 102, 156, 0.68F, 0xFFDD77);
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.budget", v.expBudget).getString(),
                    80, 114, 156, 0.68F, 0xCCCCCC);
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.none").getString(), 80, 106, 156, 0.70F, 0x99AABB);
        }

        if (!v.nearbyLc2Stations.isEmpty()) {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", v.nearbyLc2Stations.size()).getString(),
                    80, 152, 156, 0.68F, 0x55FFFF);
            for (int i = 0; i < Math.min(2, v.nearbyLc2Stations.size()); i++) {
                String label = Lc2SoftBridge.formatStationLabel(v.nearbyLc2Stations.get(i));
                TyC2UiWidgets.drawFittedString(poseStack, font, label, 82, 162 + i * 10, 154, 0.58F, 0xAADDEE);
            }
        } else {
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.no_lc2").getString(),
                    80, 156, 156, 0.68F, 0x8899AA);
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
        }

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (!hovered.isEmpty()) {
            renderTooltip(poseStack, hovered, mouseX, mouseY);
        } else if (sync != null && relX >= 178 && relX <= 236 && relY >= 6 && relY <= 18) {
            renderTooltip(
                    poseStack,
                    Component.translatable("gui.tyc2.terminal.battery", sync.batteryEu, sync.maxBatteryEu),
                    mouseX, mouseY
            );
        } else {
            renderTooltip(poseStack, mouseX, mouseY);
        }
    }
}
