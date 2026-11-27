package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.client.TyC2UiWidgets.ButtonTheme;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.gui.CommTerminalMenu;
import com.tycooncraft.tyc2.forge.network.C2STerminalActionPacket;
import com.tycooncraft.tyc2.forge.network.S2CHubSyncPacket;
import com.tycooncraft.tyc2.forge.network.S2CTerminalSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Forge 1.19.2 Deep-Space Cyan Tactical PDA screen for {@code tyc2:comm_terminal} matching
 * {@code gui/comm_terminal.png} (248x210): up to 16 bound villages on the left biome-accented
 * rail, segmented LED battery gauge at top-right, and remote Orders / Warehouse Stock / CPI Chart
 * / Growth &amp; Aid operations working across unloaded chunks.
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

    private final VillageRailButton[] railBtns = new VillageRailButton[RAIL_VISIBLE];
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

    /** Bespoke PDA village rail card button with biome accent pip, status LED dot, fitted name, and golden LvX pill. */
    private static final class VillageRailButton extends TyC2UiWidgets.CompactButton {
        @Nullable
        private S2CTerminalSyncPacket.BoundVillageDto village;

        VillageRailButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, ButtonTheme.PDA_ACTION);
        }

        void setVillage(@Nullable S2CTerminalSyncPacket.BoundVillageDto dto, boolean selected) {
            this.village = dto;
            this.setHighlighted(selected);
            this.setMessage(dto == null ? Component.empty()
                    : Component.translatable("gui.tyc2.terminal.level_prefix", dto.level(), dto.name()));
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible || village == null) {
                return;
            }
            boolean hover = this.isHoveredOrFocused();
            int border = this.highlighted ? 0xFF3CEBFF : (hover ? 0xFF2FA8C8 : 0xFF1A3449);
            int bg = this.highlighted ? 0xFF133952 : (hover ? 0xFF12283C : 0xFF0C1A27);
            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, border);
            fill(poseStack, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, bg);

            // Left 2px biome accent strip
            int biomeCol = TyC2UiWidgets.biomeAccentColor(village.biomeStyleId());
            fill(poseStack, this.x + 1, this.y + 1, this.x + 3, this.y + this.height - 1, biomeCol);

            // 3x3 status dot (emerald active, amber depopulated, muted grey unpartnered)
            int dotCol = !village.partnered()
                    ? 0xFF6B7F94
                    : (village.depopulated() ? 0xFFFFAA33 : 0xFF3BF296);
            fill(poseStack, this.x + 5, this.y + 6, this.x + 8, this.y + 9, dotCol);

            // Village name cleanly fitted between status dot and golden LvX pill
            Font font = Minecraft.getInstance().font;
            int nameColor = this.highlighted ? 0xFFFFFF : (hover ? 0xE6F8FF : 0xC6DFEE);
            TyC2UiWidgets.drawFittedStringScaled(
                    poseStack, font, village.name(), this.x + 10, this.y + 4, 30, 0.78F, 0.48F, nameColor, false
            );

            // Golden LvX pill on right (never collides with village name!)
            TyC2UiWidgets.drawPillBadge(
                    poseStack, font, this.x + this.width - 18, this.y + 2, 16, 11,
                    this.highlighted ? 0xFF47330F : 0xFF30230C,
                    this.highlighted ? 0xFFF2C244 : 0xFFB88824,
                    "L" + village.level(),
                    0xFFE87A
            );
        }
    }

    public CommTerminalScreen(CommTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 210;
        this.titleLabelX = 25;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();

        // Left village rail (8, 22, 64x178) — 8 sleek cards at y=35..162 + paging + danger Unbind
        for (int i = 0; i < RAIL_VISIBLE; i++) {
            final int row = i;
            railBtns[i] = addRenderableWidget(new VillageRailButton(
                    leftPos + 10, topPos + 35 + i * 16, 60, 15,
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
                leftPos + 10, topPos + 170, 16, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    railOffset = Math.max(0, railOffset - RAIL_VISIBLE);
                    refreshWidgets();
                },
                ButtonTheme.PDA_ACTION
        ));
        railNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 54, topPos + 170, 16, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    if (sync != null && railOffset + RAIL_VISIBLE < sync.boundVillages.size()) {
                        railOffset += RAIL_VISIBLE;
                        refreshWidgets();
                    }
                },
                ButtonTheme.PDA_ACTION
        ));
        unbindBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 10, topPos + 184, 60, 13, Component.translatable("gui.tyc2.terminal.unbind"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.UNBIND_VILLAGE
                )),
                ButtonTheme.PDA_DANGER
        ));

        // Right Tactical Viewport (76,22,164x178): header in y=24..40, 4 PDA_TAB sub-tabs at y=43..55
        String[] subKeys = {
                "gui.tyc2.terminal.tab.orders",
                "gui.tyc2.terminal.tab.stock",
                "gui.tyc2.terminal.tab.cpi",
                "gui.tyc2.terminal.tab.growth"
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            subTabBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 78 + i * 40, topPos + 43, 39, 13, Component.translatable(subKeys[i]),
                    btn -> {
                        subTab = idx;
                        refreshWidgets();
                    },
                    ButtonTheme.PDA_TAB
            ));
        }

        // Orders sub-tab controls (toolbar y=58..70)
        sideBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 78, topPos + 58, 68, 12, Component.translatable("gui.tyc2.hub.sell_orders"),
                btn -> {
                    showBuyOrders = !showBuyOrders;
                    orderPage = 0;
                    refreshWidgets();
                },
                ButtonTheme.PDA_ACTION
        ));
        qtyBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 148, topPos + 58, 56, 12, Component.translatable("gui.tyc2.hub.qty_preset", 1),
                btn -> {
                    qtyPreset = (qtyPreset + 1) % 3;
                    refreshWidgets();
                },
                ButtonTheme.PDA_ACTION
        ));
        orderPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 206, topPos + 58, 15, 12, Component.translatable("gui.tyc2.prev"),
                btn -> {
                    orderPage = Math.max(0, orderPage - 1);
                    refreshWidgets();
                },
                ButtonTheme.PDA_ACTION
        ));
        orderNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 223, topPos + 58, 15, 12, Component.translatable("gui.tyc2.next"),
                btn -> {
                    orderPage++;
                    refreshWidgets();
                },
                ButtonTheme.PDA_ACTION
        ));

        for (int i = 0; i < ORDERS_PER_PAGE; i++) {
            final int row = i;
            int ry = topPos + 75 + i * 22;
            orderActBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 198, ry, 38, 14, Component.translatable("gui.tyc2.hub.buy"),
                    btn -> executeRemoteOrder(row),
                    ButtonTheme.PDA_PRIMARY
            ));
            orderAutoBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 219, ry, 17, 14, Component.translatable("gui.tyc2.hub.btn.auto_tiny.off"),
                    btn -> toggleRemoteOrderAuto(row),
                    ButtonTheme.PDA_ACTION
            ));
        }

        // Growth sub-tab controls (Expansion / Aid mode toggle at y=58..69)
        growthExpModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 78, topPos + 58, 79, 11, Component.translatable("gui.tyc2.hub.tab.expansion"),
                btn -> {
                    growthAidMode = false;
                    refreshWidgets();
                },
                ButtonTheme.PDA_SUBTAB
        ));
        growthAidModeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 159, topPos + 58, 79, 11, Component.translatable("gui.tyc2.hub.tab.aid"),
                btn -> {
                    growthAidMode = true;
                    refreshWidgets();
                },
                ButtonTheme.PDA_SUBTAB
        ));
        upgradeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 93, 156, 12,
                Component.translatable("gui.tyc2.terminal.upgrade_short", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.UPGRADE_VILLAGE
                )),
                ButtonTheme.PDA_PRIMARY
        ));
        expDeliverBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 135, 70, 12,
                Component.translatable("gui.tyc2.terminal.deliver_short", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.DELIVER_EXPANSION
                )),
                ButtonTheme.PDA_PRIMARY
        ));
        expAutoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 152, topPos + 135, 41, 12, Component.translatable("gui.tyc2.hub.auto_short.off"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.TOGGLE_EXPANSION_AUTO
                )),
                ButtonTheme.PDA_ACTION
        ));
        expPauseBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 195, topPos + 135, 41, 12, Component.translatable("gui.tyc2.hub.pause_btn"),
                btn -> TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.TOGGLE_EXPANSION_PAUSE
                )),
                ButtonTheme.PDA_ACTION
        ));

        // Remote Aid Construction controls inside Growth tab
        aidProfPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 90, 14, 14, Component.translatable("gui.tyc2.prev"),
                btn -> stepWorkplace(-1),
                ButtonTheme.PDA_ACTION
        ));
        aidProfNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 222, topPos + 90, 14, 14, Component.translatable("gui.tyc2.next"),
                btn -> stepWorkplace(1),
                ButtonTheme.PDA_ACTION
        ));
        aidSizeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 80, topPos + 106, 76, 12, Component.translatable("gui.tyc2.aid.size.small"),
                btn -> {
                    aidLargeSize = !aidLargeSize;
                    refreshWidgets();
                },
                ButtonTheme.PDA_ACTION
        ));
        aidStartBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 158, topPos + 106, 78, 12,
                Component.translatable("gui.tyc2.aid.start_cost_btn", TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU),
                btn -> sendRemoteStartAid(),
                ButtonTheme.PDA_PRIMARY
        ));
        for (int i = 0; i < MAX_AID_ROWS; i++) {
            final int row = i;
            aidCancelBtns[i] = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                    leftPos + 202, topPos + 138 + i * 14, 34, 13, Component.translatable("gui.tyc2.aid.cancel_btn"),
                    btn -> sendRemoteCancelAid(row),
                    ButtonTheme.PDA_DANGER
            ));
        }

        refreshWidgets();
        if (menu.getLatestSync() != null) {
            onSync(menu.getLatestSync());
        }
        TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(C2STerminalActionPacket.Action.REQUEST_SYNC));
    }

    private int ticksSinceSync = 0;
    private Component activeFeedbackComponent = Component.empty();
    private String activeFeedback = "";
    private int feedbackTtlTicks = 0;

    public void onSync(S2CTerminalSyncPacket pkt) {
        this.sync = pkt;
        this.ticksSinceSync = 0;
        if (!pkt.feedback.isEmpty()) {
            this.activeFeedbackComponent = pkt.feedbackComponent;
            this.activeFeedback = pkt.feedback;
            this.feedbackTtlTicks = 100;
        }
        this.menu.setLatestSync(pkt);
        refreshWidgets();
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
        if (sync != null && sync.selectedVillage != null) {
            ticksSinceSync++;
            if (ticksSinceSync % 40 == 0) {
                TyC2ForgeNetwork.sendToServer(C2STerminalActionPacket.simple(
                        C2STerminalActionPacket.Action.REQUEST_SYNC
                ));
            }
        }
    }

    private long liveRefreshRemaining(S2CHubSyncPacket v) {
        return Math.max(0L, v.refreshRemainingTicks - ticksSinceSync);
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
        if (railOffset >= count && railOffset > 0) {
            railOffset = Math.max(0, ((count - 1) / RAIL_VISIBLE) * RAIL_VISIBLE);
        }
        for (int i = 0; i < RAIL_VISIBLE; i++) {
            int idx = railOffset + i;
            boolean vis = sync != null && idx < count;
            railBtns[i].visible = vis;
            if (vis) {
                railBtns[i].setVillage(sync.boundVillages.get(idx), idx == sync.activeIndex);
            } else {
                railBtns[i].setVillage(null, false);
            }
        }
        railPrevBtn.visible = count > RAIL_VISIBLE;
        railNextBtn.visible = count > RAIL_VISIBLE;
        railPrevBtn.active = railOffset > 0;
        railNextBtn.active = railOffset + RAIL_VISIBLE < count;
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
        sideBtn.setHighlighted(true);
        sideBtn.setAccentColor(showBuyOrders ? 0xFF8C5318 : 0xFF124D6B);

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
            boolean isBuy = has && orders.get(idx).isVillageBuy();
            orderActBtns[i].visible = has;
            orderActBtns[i].active = has && hasPowerTrade;
            orderAutoBtns[i].visible = isBuy;
            if (has) {
                S2CHubSyncPacket.OrderDto o = orders.get(idx);
                orderActBtns[i].x = leftPos + (isBuy ? 184 : 198);
                orderActBtns[i].setWidth(isBuy ? 33 : 38);
                orderActBtns[i].setMessage(Component.translatable(isBuy ? "gui.tyc2.hub.deliver" : "gui.tyc2.hub.buy"));
                orderActBtns[i].setAccentColor(isBuy ? 0xFF945817 : 0xFF0F564A);
                if (isBuy) {
                    orderAutoBtns[i].setHighlighted(o.autoFulfill());
                    if (o.autoFulfill()) {
                        orderAutoBtns[i].setAccentColor(0xFF146B52);
                    } else {
                        orderAutoBtns[i].clearAccentColor();
                    }
                    orderAutoBtns[i].setMessage(Component.translatable(
                            o.autoFulfill() ? "gui.tyc2.hub.btn.auto_tiny.on" : "gui.tyc2.hub.btn.auto_tiny.off"
                    ));
                }
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
            if (sync.selectedVillage.expAutoFulfill) {
                expAutoBtn.setAccentColor(0xFF146B52);
            } else {
                expAutoBtn.clearAccentColor();
            }
            expAutoBtn.setMessage(Component.translatable(
                    sync.selectedVillage.expAutoFulfill ? "gui.tyc2.hub.auto_short.on" : "gui.tyc2.hub.auto_short.off"
            ));
        }
        expPauseBtn.visible = t3Exp;
        if (hasSel) {
            expPauseBtn.setHighlighted(sync.selectedVillage.expansionPaused);
            if (sync.selectedVillage.expansionPaused) {
                expPauseBtn.setAccentColor(0xFF8C5318);
            } else {
                expPauseBtn.clearAccentColor();
            }
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
        aidStartBtn.active = t3Aid && wp != null && sizeUnlocked && !wp.aidLimitReached() && queueOpen
                && hasPowerUpg && !sync.selectedVillage.depopulatedPaused;

        for (int i = 0; i < MAX_AID_ROWS; i++) {
            boolean showCancel = t3Aid && hasSel && i < sync.selectedVillage.aidProjects.size()
                    && sync.selectedVillage.aidProjects.get(i).canCancel();
            aidCancelBtns[i].visible = showCancel;
            aidCancelBtns[i].active = showCancel;
        }
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

        // Top segmented LED battery gauge (178,6,58x12): green-cyan (0,216,56x10) or red (0,228,56x10)
        int eu = sync == null ? 0 : sync.batteryEu;
        int maxEu = sync == null ? TriModePowerMath.TERMINAL_MAX_CAPACITY_EU : Math.max(1, sync.maxBatteryEu);
        if (eu > 0) {
            int fillW = Math.min(56, Math.max(1, (eu * 56) / maxEu));
            int spriteV = eu < 2000 ? TyC2UiWidgets.PDA_BATTERY_RED_V : TyC2UiWidgets.PDA_BATTERY_GREEN_V;
            blit(poseStack, leftPos + 179, topPos + 7, 0, spriteV, fillW, 10);
        }

        if (sync == null || sync.selectedVillage == null) {
            return;
        }
        S2CHubSyncPacket v = sync.selectedVillage;

        // Right Tactical Header icons inside framed y=24..40 zone:
        // Biome 10x10 (pdaBiomeU, 236), Weekly Calendar 12x12 (160, 216), Season 12x12 (pdaSeasonU, 216)
        int biomeU = TyC2UiWidgets.pdaBiomeU(v.biomeStyleId);
        blit(poseStack, leftPos + 79, topPos + 27, biomeU, TyC2UiWidgets.PDA_BIOME_V, 10, 10);
        blit(poseStack, leftPos + 176, topPos + 26, TyC2UiWidgets.PDA_CALENDAR_U, TyC2UiWidgets.PDA_ICON_V, 12, 12);
        int seasonU = TyC2UiWidgets.pdaSeasonU(v.seasonKey);
        if (seasonU >= 0) {
            blit(poseStack, leftPos + 224, topPos + 26, seasonU, TyC2UiWidgets.PDA_SEASON_V, 12, 12);
        }

        // Recessed tactical footer bar (78..238, y=183..197)
        fill(poseStack, leftPos + 78, topPos + 183, leftPos + 238, topPos + 184, 0xFF1D435C);
        fill(poseStack, leftPos + 78, topPos + 184, leftPos + 238, topPos + 197, 0xFF08121C);

        // Tab-specific card chrome
        switch (subTab) {
            case 0 -> renderRemoteOrdersChrome(poseStack);
            case 1 -> renderRemoteStockChrome(poseStack);
            case 2 -> renderRemoteCpiChrome(poseStack);
            case 3 -> renderRemoteGrowthChrome(poseStack);
        }
    }

    private void renderRemoteOrdersChrome(PoseStack poseStack) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
            S2CHubSyncPacket.OrderDto o = list.get(start + i);
            int cardX = leftPos + 78;
            int cardY = topPos + 72 + i * 22;
            int cardW = 160;
            int cardH = 21;

            int borderCol = o.isVillageBuy() ? 0xFF47361E : 0xFF1B3B54;
            int bgCol = (i % 2 == 0) ? 0xFF0E1D2C : 0xFF0B1825;
            int accentCol = o.isVillageBuy() ? 0xFFF0A43A : 0xFF32D4FF;

            fill(poseStack, cardX, cardY, cardX + cardW, cardY + cardH, borderCol);
            fill(poseStack, cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1, bgCol);
            fill(poseStack, cardX, cardY, cardX + 2, cardY + cardH, accentCol);

            // Inline 2px bottom progress bar
            int barX = cardX + 2;
            int barW = cardW - 3;
            int barY = cardY + cardH - 2;
            fill(poseStack, barX, barY, barX + barW, barY + 2, 0xFF071019);
            if (o.totalQty() > 0 && o.fulfilledQty() > 0) {
                int fillW = Math.min(barW, Math.max(2, (barW * o.fulfilledQty()) / o.totalQty()));
                fill(poseStack, barX, barY, barX + fillW, barY + 2, accentCol);
            }

            // 8x8 Biome Specialty & Seasonal gem sprites on Row 1 right side
            boolean hasBiome = o.biomeSpecialty();
            boolean hasSeason = o.seasonalBonus();
            int rightEdge = leftPos + (o.isVillageBuy() ? 182 : 196);
            if (hasBiome || hasSeason) {
                bindTexture();
                int gx = rightEdge;
                if (hasSeason) {
                    gx -= 9;
                    blit(poseStack, gx, cardY + 2, TyC2UiWidgets.PDA_TAG_SEASON_U, TyC2UiWidgets.PDA_TAG_V, 8, 8);
                }
                if (hasBiome) {
                    gx -= 9;
                    blit(poseStack, gx, cardY + 2, TyC2UiWidgets.PDA_TAG_BIOME_U, TyC2UiWidgets.PDA_TAG_V, 8, 8);
                }
            }
        }
    }

    private void renderRemoteStockChrome(PoseStack poseStack) {
        // Compact summary header strip (78,58,160x12)
        fill(poseStack, leftPos + 78, topPos + 58, leftPos + 238, topPos + 70, 0xFF1B3B54);
        fill(poseStack, leftPos + 79, topPos + 59, leftPos + 237, topPos + 69, 0xFF0E2030);
        // Inbound box (78,72,160x53)
        fill(poseStack, leftPos + 78, topPos + 72, leftPos + 238, topPos + 125, 0xFF1D415E);
        fill(poseStack, leftPos + 79, topPos + 73, leftPos + 237, topPos + 124, 0xFF0C1A28);
        fill(poseStack, leftPos + 79, topPos + 83, leftPos + 237, topPos + 84, 0xFF17354C);
        // Outbound box (78,127,160x54)
        fill(poseStack, leftPos + 78, topPos + 127, leftPos + 238, topPos + 181, 0xFF473720);
        fill(poseStack, leftPos + 79, topPos + 128, leftPos + 237, topPos + 180, 0xFF0E1B26);
        fill(poseStack, leftPos + 79, topPos + 138, leftPos + 237, topPos + 139, 0xFF342A1B);
    }

    private void renderRemoteCpiChrome(PoseStack poseStack) {
        // KPI header card (78,58,160x24)
        fill(poseStack, leftPos + 78, topPos + 58, leftPos + 238, topPos + 82, 0xFF1D445E);
        fill(poseStack, leftPos + 79, topPos + 59, leftPos + 237, topPos + 81, 0xFF0D1D2D);
        fill(poseStack, leftPos + 81, topPos + 70, leftPos + 235, topPos + 71, 0xFF18364C);
    }

    private void renderRemoteGrowthChrome(PoseStack poseStack) {
        if (!growthAidMode) {
            // Upgrade card (78,72,160x35), Expansion card (78,108,160x41), Telemetry card (78,150,160x31)
            fill(poseStack, leftPos + 78, topPos + 72, leftPos + 238, topPos + 107, 0xFF1B3C55);
            fill(poseStack, leftPos + 79, topPos + 73, leftPos + 237, topPos + 106, 0xFF0C1B29);

            fill(poseStack, leftPos + 78, topPos + 108, leftPos + 238, topPos + 149, 0xFF1B3C55);
            fill(poseStack, leftPos + 79, topPos + 109, leftPos + 237, topPos + 148, 0xFF0C1B29);

            fill(poseStack, leftPos + 78, topPos + 150, leftPos + 238, topPos + 181, 0xFF19354A);
            fill(poseStack, leftPos + 79, topPos + 151, leftPos + 237, topPos + 180, 0xFF0A1622);
        } else {
            // Aid header & picker card (78,72,160x55) + Aid queue card (78,128,160x53)
            fill(poseStack, leftPos + 78, topPos + 72, leftPos + 238, topPos + 127, 0xFF1B3C55);
            fill(poseStack, leftPos + 79, topPos + 73, leftPos + 237, topPos + 126, 0xFF0C1B29);

            fill(poseStack, leftPos + 78, topPos + 128, leftPos + 238, topPos + 181, 0xFF1B3C55);
            fill(poseStack, leftPos + 79, topPos + 129, leftPos + 237, topPos + 180, 0xFF0C1B29);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, title.getString(), titleLabelX, titleLabelY, 148, 0.90F, 0.62F, 0xD8F4FF, true
        );
        if (sync == null) {
            return;
        }

        // Left rail clean header: "n/16" centered in y=24..32 (full label on hover!)
        String countStr = sync.boundVillages.size() + "/16";
        TyC2UiWidgets.drawCenteredFittedString(poseStack, font, countStr, 10, 25, 60, 0.85F, 0.60F, 0x5CEEFF);
        if (sync.boundVillages.size() > RAIL_VISIBLE) {
            int curPg = (railOffset / RAIL_VISIBLE) + 1;
            int totPg = (sync.boundVillages.size() + RAIL_VISIBLE - 1) / RAIL_VISIBLE;
            TyC2UiWidgets.drawCenteredFittedString(poseStack, font, curPg + "/" + totPg, 27, 173, 26, 0.78F, 0.55F, 0x9CD6EE);
        }

        if (sync.selectedVillage == null) {
            List<FormattedCharSequence> emptyLines = font.split(Component.translatable("gui.tyc2.terminal.no_villages"), 148);
            for (int i = 0; i < Math.min(3, emptyLines.size()); i++) {
                font.draw(poseStack, emptyLines.get(i), 84, 72 + i * 11, 0xFFCC66);
            }
            return;
        }

        S2CHubSyncPacket v = sync.selectedVillage;

        // Right Tactical Header (inside framed y=24..40 zone)
        TyC2UiWidgets.drawPillBadge(
                poseStack, font, 91, 24, 21, 9,
                0xFF3C2B0D, 0xFFE0B038,
                "Lv" + v.level, 0xFFE87A
        );
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, v.villageName, 114, 25, 60, 0.84F, 0.52F, 0xF2FAFF, true
        );
        String fundsLine = Component.translatable("gui.tyc2.hub.village_funds", TyC2UiWidgets.compactIc(v.villageBalance)).getString();
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, fundsLine, 91, 33, 83, 0.74F, 0.48F, 0x6EF29E, false
        );

        String countdown = TyC2UiWidgets.formatRefreshCountdown(liveRefreshRemaining(v));
        TyC2UiWidgets.drawFittedStringScaled(
                poseStack, font, countdown, 189, 29, 34, 0.80F, 0.50F, 0xFFE27A, false
        );

        switch (subTab) {
            case 0 -> renderRemoteOrders(poseStack);
            case 1 -> renderRemoteStock(poseStack, v);
            case 2 -> renderRemoteCpi(poseStack, v);
            case 3 -> renderRemoteGrowth(poseStack, v);
        }

        // Bottom tactical footer (inside y=184..197 strip)
        boolean hasFeedback = !activeFeedback.isEmpty();
        Component footerComp = hasFeedback
                ? activeFeedbackComponent
                : Component.translatable(
                        "gui.tyc2.terminal.footer",
                        TyC2UiWidgets.compactIc(v.activeBankBalance),
                        TriModePowerMath.TERMINAL_COST_TRADE_EU,
                        TriModePowerMath.TERMINAL_COST_UPGRADE_OR_EXPANSION_EU
                );
        String footer = footerComp.getString();
        if (hasFeedback && font.width(footer) > 260) {
            List<FormattedCharSequence> fbLines = font.split(footerComp, 330);
            poseStack.pushPose();
            poseStack.translate(81.0F, 185.0F, 0.0F);
            poseStack.scale(0.46F, 0.46F, 1.0F);
            for (int i = 0; i < Math.min(2, fbLines.size()); i++) {
                font.draw(poseStack, fbLines.get(i), 0, i * 11, 0xFFE27A);
            }
            poseStack.popPose();
        } else {
            TyC2UiWidgets.drawFittedStringScaled(
                    poseStack, font, footer, 81, 187, 154, 0.76F, 0.48F,
                    hasFeedback ? 0xFFE27A : 0x7CEAD0, false
            );
        }
    }

    private void renderRemoteOrders(PoseStack poseStack) {
        List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
        if (list.isEmpty()) {
            TyC2UiWidgets.drawCenteredFittedString(
                    poseStack, font,
                    Component.translatable("gui.tyc2.hub.no_orders").getString(),
                    80, 96, 156, 0.86F, 0.56F, 0xFFAA66
            );
            return;
        }
        int start = orderPage * ORDERS_PER_PAGE;
        for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
            S2CHubSyncPacket.OrderDto o = list.get(start + i);
            int cardY = 72 + i * 22;
            int rightEdge = o.isVillageBuy() ? 182 : 196;
            int gemReserve = (o.biomeSpecialty() ? 9 : 0) + (o.seasonalBonus() ? 9 : 0);
            int nameMaxW = Math.max(36, rightEdge - 99 - gemReserve - (gemReserve > 0 ? 2 : 0));

            String name = TyC2Ids.localizedItemName(o.itemId(), o.enchantSpec());
            int nameCol = o.seasonalBonus() ? 0xFFF08A : (o.biomeSpecialty() ? 0xD8FFE6 : 0xEEF6FF);
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, name, 99, cardY + 2, nameMaxW, 0.80F, 0.52F, nameCol, false);

            String detail = o.fulfilledQty() + "/" + o.totalQty() + " · " + TyC2UiWidgets.compactIc(o.unitPrice()) + " IC";
            TyC2UiWidgets.drawFittedStringScaled(
                    poseStack, font, detail, 99, cardY + 11, rightEdge - 99, 0.76F, 0.50F, 0x7CF2C4, false
            );
        }
    }

    private void renderRemoteStock(PoseStack poseStack, S2CHubSyncPacket v) {
        String compact = Component.translatable("gui.tyc2.terminal.stock_compact", v.inboundUsed, v.outboundUsed).getString();
        TyC2UiWidgets.drawCenteredFittedString(poseStack, font, compact, 80, 61, 156, 0.80F, 0.54F, 0x7CF4FF);

        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.zone.inbound").getString(),
                82, 74, 100, 0.80F, 0.56F, 0x7CC2FF, false);
        TyC2UiWidgets.drawRightFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.zone.used", v.inboundUsed).getString(),
                234, 74, 58, 0.74F, 0.50F, 0x90B8D4);
        for (int i = 0; i < Math.min(4, sync.inboundSummary.size()); i++) {
            var e = sync.inboundSummary.get(i);
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, shortItemName(e.itemId()), 82, 86 + i * 9, 114, 0.76F, 0.52F, 0xDDEEFF, false);
            TyC2UiWidgets.drawRightFittedString(poseStack, font, "x" + e.count(), 234, 86 + i * 9, 36, 0.76F, 0.52F, 0x7CC2FF);
        }

        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.zone.outbound").getString(),
                82, 129, 100, 0.80F, 0.56F, 0xFFBD6B, false);
        TyC2UiWidgets.drawRightFittedString(poseStack, font,
                Component.translatable("gui.tyc2.hub.zone.used", v.outboundUsed).getString(),
                234, 129, 58, 0.74F, 0.50F, 0xC8B090);
        for (int i = 0; i < Math.min(4, sync.outboundSummary.size()); i++) {
            var e = sync.outboundSummary.get(i);
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, shortItemName(e.itemId()), 82, 141 + i * 9, 114, 0.76F, 0.52F, 0xFFEECC, false);
            TyC2UiWidgets.drawRightFittedString(poseStack, font, "x" + e.count(), 234, 141 + i * 9, 36, 0.76F, 0.52F, 0xFFBD6B);
        }
    }

    private void renderRemoteCpi(PoseStack poseStack, S2CHubSyncPacket v) {
        int col = v.cpi > 1.08 ? 0xFF7A5A : (v.cpi < 0.92 ? 0x5AE2FF : 0x66F298);
        String regimeKey = switch (v.inflationState) {
            case "DEFLATION" -> "gui.tyc2.economy.deflation";
            case "STABLE" -> "gui.tyc2.economy.stable";
            default -> "gui.tyc2.economy.inflation";
        };
        String cpiLine = Component.translatable("gui.tyc2.economy.cpi", String.format(Locale.ROOT, "%.2f", v.cpi)).getString()
                + " (" + Component.translatable(regimeKey).getString() + ")";
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, cpiLine, 82, 61, 152, 0.80F, 0.52F, col, false);

        String moneyShort = Component.translatable("gui.tyc2.economy.money_short", TyC2UiWidgets.compactIc(v.moneySupply)).getString();
        String goodsShort = Component.translatable("gui.tyc2.economy.goods_short", TyC2UiWidgets.compactIc(Math.round(v.goodsCirculation))).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, moneyShort, 82, 72, 82, 0.74F, 0.48F, 0xFFE07A, false);
        TyC2UiWidgets.drawRightFittedString(poseStack, font, goodsShort, 234, 72, 72, 0.74F, 0.48F, 0x82EED2);

        TyC2UiWidgets.drawCpiLineChart(poseStack, font, 78, 85, 160, 96, v.cpiHistory, true);
    }

    private void renderRemoteGrowth(PoseStack poseStack, S2CHubSyncPacket v) {
        if (growthAidMode) {
            renderRemoteAid(poseStack, v);
            return;
        }
        // Upgrade card (78,72,160x35)
        if (v.level >= 10) {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.hub.max_level").getString(), 82, 77, 152, 0.80F, 0.56F, 0x66F298, false);
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.hub.upgrade_cost", v.nextSinkCost).getString(),
                    82, 74, 152, 0.76F, 0.52F, 0xFFE070, false);
            String req = Component.translatable(
                    "gui.tyc2.hub.upgrade_req",
                    v.population + "/" + v.nextMinPop,
                    TyC2UiWidgets.compactIc(v.cumulativeVolume) + "/" + TyC2UiWidgets.compactIc(v.nextMinVol),
                    TyC2UiWidgets.compactIc(v.villageBalance) + "/" + TyC2UiWidgets.compactIc(v.nextMinBal)
            ).getString();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, req, 82, 83, 152, 0.68F, 0.46F, 0xAAD8F2, false);
        }

        // Expansion card (78,108,160x41)
        if (v.hasJob) {
            int pct = v.jobTotal > 0 ? (v.jobPlaced * 100) / v.jobTotal : 0;
            String msg = Component.translatable(
                    "gui.tyc2.expansion.building",
                    Component.translatable("project.tyc2." + v.jobProjectKey),
                    pct
            ).getString();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, msg, 82, 116, 152, 0.78F, 0.52F, 0x66F298, false);
        } else if (v.hasExpansion) {
            String proj = Component.translatable(
                    "gui.tyc2.expansion.project",
                    Component.translatable("project.tyc2." + v.expProjectKey)
            ).getString() + " (" + v.expBudget + " IC)";
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, proj, 82, 110, 152, 0.72F, 0.48F, 0xFFE07A, false);

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
                TyC2UiWidgets.drawFittedStringScaled(
                        poseStack, font, line1.toString(), 82, 118, 152, 0.66F, 0.44F,
                        line1AllMet ? 0x66FF99 : 0xFFCC66, false
                );
            }
            if (line2.length() > 0) {
                TyC2UiWidgets.drawFittedStringScaled(
                        poseStack, font, line2.toString(), 82, 126, 152, 0.66F, 0.44F,
                        line2AllMet ? 0x66FF99 : 0xFFCC66, false
                );
            }
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.expansion.none").getString(), 82, 118, 152, 0.76F, 0.54F, 0x8CA2B8, false);
        }

        // Bottom telemetry card (78,150,160x31)
        String unempLine = Component.translatable(
                "gui.tyc2.hub.unemployment", v.unemploymentPct, v.unemployedCount, v.workforceCount
        ).getString();
        if (v.employmentBonusActive) {
            unempLine += "  " + Component.translatable(
                    "gui.tyc2.hub.unemployment_bonus", v.qtyBonusPct
            ).getString();
        }
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, unempLine, 82, 153, 152, 0.70F, 0.46F,
                v.employmentBonusActive ? 0x66F298 : 0xFFCC66, false);

        if (!v.nearbyLc2Stations.isEmpty()) {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.lc2_nearby", v.nearbyLc2Stations.size()).getString(),
                    82, 162, 152, 0.70F, 0.50F, 0x5CEEFF, false);
            String label = Lc2SoftBridge.formatStationLabel(v.nearbyLc2Stations.get(0));
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, label, 82, 171, 152, 0.68F, 0.46F, 0xAAD8F2, false);
        } else {
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                    Component.translatable("gui.tyc2.terminal.no_lc2").getString(),
                    82, 165, 152, 0.72F, 0.50F, 0x7A93A8, false);
        }
    }

    private void renderRemoteAid(PoseStack poseStack, S2CHubSyncPacket v) {
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.hub.unemployment", v.unemploymentPct, v.unemployedCount, v.workforceCount).getString(),
                82, 74, 152, 0.74F, 0.50F, v.employmentBonusActive ? 0x66F298 : 0xFFCC66, false);
        String bonusLine = v.employmentBonusActive
                ? Component.translatable("gui.tyc2.aid.bonus_active", v.qtyBonusPct).getString()
                : Component.translatable("gui.tyc2.aid.bonus_inactive", v.lowUnempThresholdPct).getString();
        TyC2UiWidgets.drawFittedStringScaled(poseStack, font, bonusLine,
                82, 82, 152, 0.64F, 0.44F, v.employmentBonusActive ? 0x66FF99 : 0x8CA2B8, false);

        S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
        if (wp != null) {
            String profName = TyC2UiWidgets.localizedProfessionName(
                    wp.professionId(), wp.archetypeName(), wp.displayNameFallback()
            );
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, profName, 114, 93, 106, 0.80F, 0.52F, 0xFFE888, false);
            long cost = aidLargeSize ? wp.costLargeIc() : wp.costSmallIc();
            boolean unlocked = aidLargeSize ? wp.largeUnlocked() : wp.smallUnlocked();
            String costGather = Component.translatable("gui.tyc2.aid.cost", cost).getString()
                    + " · " + Component.translatable(
                            "gui.tyc2.aid.gather_rate",
                            String.format(Locale.ROOT, "%.2f", v.gatherSpeedMult),
                            v.unemployedCount
                    ).getString();
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, costGather,
                    82, 119, 152, 0.64F, 0.44F, unlocked ? 0x82EED2 : 0xFF8866, false);
        }

        TyC2UiWidgets.drawFittedStringScaled(poseStack, font,
                Component.translatable("gui.tyc2.aid.queue_header", v.aidProjects.size(), v.maxQueuedAid).getString(),
                82, 130, 152, 0.74F, 0.52F, 0x5CEEFF, false);
        if (v.aidProjects.isEmpty()) {
            TyC2UiWidgets.drawCenteredFittedString(poseStack, font,
                    Component.translatable("gui.tyc2.aid.queue_empty").getString(),
                    82, 152, 152, 0.78F, 0.54F, 0x8096AA);
            return;
        }
        int rows = Math.min(MAX_AID_ROWS, v.aidProjects.size());
        for (int i = 0; i < rows; i++) {
            S2CHubSyncPacket.AidProjectDto aid = v.aidProjects.get(i);
            int ry = 138 + i * 14;
            int rowW = aid.canCancel() ? 119 : 154;
            GuiComponent.fill(poseStack, 81, ry, 81 + rowW, ry + 13, 0xFF0A1520);
            int fillW = Math.max(0, Math.min(rowW - 2, ((rowW - 2) * aid.progressPct()) / 100));
            int barCol = "BUILDING".equals(aid.statusName())
                    ? 0xFF44DD77
                    : ("NO_SITE".equals(aid.statusName()) ? 0xFFEE6644 : 0xFF44C8EE);
            if (fillW > 0) {
                GuiComponent.fill(poseStack, 82, ry + 11, 82 + fillW, ry + 13, barCol);
            }
            String profName = TyC2UiWidgets.localizedProfessionName(
                    aid.professionId(), aid.archetypeName(), aid.displayNameFallback()
            );
            String line = profName + (aid.large() ? " [L] · " : " [S] · ") + TyC2UiWidgets.formatAidStatus(aid);
            int col = "NO_SITE".equals(aid.statusName()) ? 0xFF9966 : 0xFFE888;
            TyC2UiWidgets.drawFittedStringScaled(poseStack, font, line, 83, ry + 2, rowW - 4, 0.68F, 0.46F, col, false);
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
        List<Component> hoveredTips = null;
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;

        if (sync != null && sync.selectedVillage != null && subTab == 0) {
            List<S2CHubSyncPacket.OrderDto> list = filteredOrders();
            int start = orderPage * ORDERS_PER_PAGE;
            for (int i = 0; i < ORDERS_PER_PAGE && start + i < list.size(); i++) {
                S2CHubSyncPacket.OrderDto o = list.get(start + i);
                ItemStack icon = TyC2Ids.createStack(o.itemId(), 1, "", o.enchantSpec());
                int cardY = 72 + i * 22;
                int ix = leftPos + 81;
                int iy = topPos + cardY + 2;
                if (!icon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(icon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        if (o.isVillageBuy() && o.optionalTag() != null && !o.optionalTag().isEmpty()) {
                            List<Component> lines = new ArrayList<>(getTooltipFromItem(icon));
                            lines.add(Component.translatable("gui.tyc2.order.tooltip.accepts_tag", o.optionalTag())
                                    .withStyle(ChatFormatting.GOLD));
                            hoveredTips = lines;
                        } else {
                            hovered = icon;
                        }
                    }
                }
                if (hoveredTips == null && relY >= cardY && relY <= cardY + 14) {
                    int rightEdge = o.isVillageBuy() ? 182 : 196;
                    boolean hasBiome = o.biomeSpecialty();
                    boolean hasSeason = o.seasonalBonus();
                    int gx = rightEdge;
                    if (hasSeason) {
                        gx -= 9;
                        if (relX >= gx && relX <= gx + 8) {
                            hoveredTips = List.of(Component.translatable(o.isVillageBuy()
                                    ? "gui.tyc2.order.tooltip.season_buy"
                                    : "gui.tyc2.order.tooltip.season_sell").withStyle(ChatFormatting.AQUA));
                        }
                    }
                    if (hoveredTips == null && hasBiome) {
                        gx -= 9;
                        if (relX >= gx && relX <= gx + 8) {
                            hoveredTips = List.of(Component.translatable(o.isVillageBuy()
                                    ? "gui.tyc2.order.tooltip.biome_buy"
                                    : "gui.tyc2.order.tooltip.biome_sell").withStyle(ChatFormatting.GREEN));
                        }
                    }
                    if (hoveredTips == null && o.isVillageBuy() && relX >= 219 && relX <= 236) {
                        hoveredTips = List.of(Component.translatable(o.autoFulfill()
                                ? "gui.tyc2.hub.auto_fulfill.on"
                                : "gui.tyc2.hub.auto_fulfill.off").withStyle(
                                o.autoFulfill() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    }
                }
            }
        } else if (sync != null && sync.selectedVillage != null && subTab == 3 && growthAidMode) {
            S2CHubSyncPacket.WorkplaceDto wp = currentWorkplace();
            if (wp != null) {
                ItemStack wsIcon = TyC2Ids.createBlockOrItemIcon(wp.workstationBlockId());
                int ix = leftPos + 96;
                int iy = topPos + 89;
                if (!wsIcon.isEmpty()) {
                    itemRenderer.renderAndDecorateItem(wsIcon, ix, iy);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                        hovered = wsIcon;
                    }
                }
                if (hovered.isEmpty() && wp.aidLimitReached()
                        && relX >= 158 && relX < 236 && relY >= 106 && relY < 118) {
                    hoveredTips = List.of(Component.translatable(
                            "status.tyc2.aid.profession_limit",
                            WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION
                    ).withStyle(ChatFormatting.RED));
                }
            }
            if (hoveredTips == null) {
                int rows = Math.min(MAX_AID_ROWS, sync.selectedVillage.aidProjects.size());
                for (int i = 0; i < rows; i++) {
                    S2CHubSyncPacket.AidProjectDto aid = sync.selectedVillage.aidProjects.get(i);
                    int ry = 138 + i * 14;
                    int maxHoverX = aid.canCancel() ? 200 : 235;
                    if (relX >= 81 && relX <= maxHoverX && relY >= ry && relY <= ry + 13) {
                        hoveredTips = TyC2UiWidgets.buildAidMaterialsTooltip(aid);
                        break;
                    }
                }
            }
        }

        // Hover tooltips for Left Rail header/cards & Right Tactical Header icons
        if (sync != null && hovered.isEmpty() && hoveredTips == null) {
            if (relX >= 9 && relX <= 70 && relY >= 23 && relY <= 33) {
                hoveredTips = List.of(Component.translatable("gui.tyc2.terminal.bound_count", sync.boundVillages.size())
                        .withStyle(ChatFormatting.AQUA));
            } else if (relX >= 10 && relX <= 70 && relY >= 35 && relY < 35 + RAIL_VISIBLE * 16) {
                int row = (relY - 35) / 16;
                int idx = railOffset + row;
                if (idx >= 0 && idx < sync.boundVillages.size()) {
                    S2CTerminalSyncPacket.BoundVillageDto bv = sync.boundVillages.get(idx);
                    hoveredTips = List.of(
                            Component.translatable("gui.tyc2.hub.level", bv.level(), bv.name()).withStyle(ChatFormatting.GOLD),
                            Component.translatable("gui.tyc2.hub.biome_tooltip",
                                    Component.translatable("biome.tyc2." + bv.biomeStyleId())).withStyle(ChatFormatting.AQUA),
                            Component.translatable("gui.tyc2.hub.village_funds", TyC2UiWidgets.compactIc(bv.balance()))
                                    .withStyle(ChatFormatting.GREEN)
                    );
                }
            } else if (sync.selectedVillage != null && relY >= 24 && relY <= 40) {
                S2CHubSyncPacket v = sync.selectedVillage;
                if (relX >= 78 && relX <= 90) {
                    hoveredTips = List.of(Component.translatable("gui.tyc2.hub.biome_tooltip",
                            Component.translatable("biome.tyc2." + v.biomeStyleId)).withStyle(ChatFormatting.GOLD));
                } else if (relX >= 175 && relX <= 223) {
                    hoveredTips = List.of(Component.translatable("gui.tyc2.hub.refresh_tooltip",
                            TyC2UiWidgets.formatRefreshCountdown(liveRefreshRemaining(v))).withStyle(ChatFormatting.YELLOW));
                } else if (relX >= 224 && relX <= 237 && TyC2UiWidgets.pdaSeasonU(v.seasonKey) >= 0) {
                    hoveredTips = List.of(Component.translatable("gui.tyc2.hub.season_tooltip",
                            Component.translatable("season.tyc2." + v.seasonKey)).withStyle(ChatFormatting.AQUA));
                }
            }
        }

        if (!hovered.isEmpty()) {
            renderTooltip(poseStack, hovered, mouseX, mouseY);
        } else if (hoveredTips != null && !hoveredTips.isEmpty()) {
            renderComponentTooltip(poseStack, hoveredTips, mouseX, mouseY);
        } else if (sync != null && relX >= 178 && relX <= 236 && relY >= 6 && relY <= 18) {
            renderTooltip(
                    poseStack,
                    Component.translatable("gui.tyc2.terminal.battery", sync.batteryEu, sync.maxBatteryEu),
                    mouseX, mouseY
            );
        } else if (sync != null && !activeFeedback.isEmpty()
                && relX >= 78 && relX <= 238 && relY >= 183 && relY <= 197) {
            renderTooltip(poseStack, font.split(activeFeedbackComponent, 220), mouseX, mouseY);
        } else {
            renderTooltip(poseStack, mouseX, mouseY);
        }
    }
}
