package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.gui.AtmMachineMenu;
import com.tycooncraft.tyc2.forge.network.C2SAtmActionPacket;
import com.tycooncraft.tyc2.forge.network.S2CAtmSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 1.19.2 screen for {@code tyc2:atm_machine} matching {@code gui/atm_machine.png} (220x214).
 */
public class AtmMachineScreen extends AbstractContainerScreen<AtmMachineMenu> {

    private static final ResourceLocation TEXTURE = TyC2Ids.id("textures/gui/atm_machine.png");

    @Nullable
    private S2CAtmSyncPacket sync;
    private EditBox amountBox;
    private EditBox targetBox;
    private TyC2UiWidgets.CompactButton denomBtn;
    private TyC2UiWidgets.CompactButton modeBtn;
    private TyC2UiWidgets.CompactButton companyPanelBtn;
    private TyC2UiWidgets.CompactButton createCoBtn;
    private TyC2UiWidgets.CompactButton joinCoBtn;
    private TyC2UiWidgets.CompactButton leaveCoBtn;
    private TyC2UiWidgets.CompactButton logPrevBtn;
    private TyC2UiWidgets.CompactButton logNextBtn;

    private int selectedDenomIndex = 5; // Default X100
    private boolean showCompanyPanel = false;
    private int logPage = 0;

    public AtmMachineScreen(AtmMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 220;
        this.imageHeight = 214;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 29;
        this.inventoryLabelY = 122;
    }

    @Override
    protected void init() {
        super.init();

        // Controls panel (35, 56, 72x68)
        amountBox = new EditBox(font, leftPos + 37, topPos + 58, 68, 11, Component.translatable("gui.tyc2.atm.amount_placeholder"));
        amountBox.setMaxLength(12);
        amountBox.setValue("100");
        addRenderableWidget(amountBox);

        targetBox = new EditBox(font, leftPos + 37, topPos + 71, 68, 11, Component.translatable("gui.tyc2.atm.recipient_placeholder"));
        targetBox.setMaxLength(32);
        addRenderableWidget(targetBox);

        // Row 1: Deposit / Deposit All / Withdraw
        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 36, topPos + 84, 22, 12, Component.literal("Dep"),
                btn -> sendAction(C2SAtmActionPacket.Action.DEPOSIT_SLOT, 0L, "")
        ));
        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 59, topPos + 84, 22, 12, Component.literal("All"),
                btn -> sendAction(C2SAtmActionPacket.Action.DEPOSIT_ALL, 0L, "")
        ));
        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 82, topPos + 84, 24, 12, Component.literal("Wdr"),
                btn -> sendAction(C2SAtmActionPacket.Action.WITHDRAW_AMOUNT, parseAmount(100L), "")
        ));

        // Row 2: Denomination picker & exact withdraw
        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 36, topPos + 97, 11, 12, Component.literal("<"),
                btn -> {
                    selectedDenomIndex = Math.floorMod(selectedDenomIndex - 1, CurrencyDenomination.values().length);
                    updateButtonLabels();
                }
        ));
        denomBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 48, topPos + 97, 46, 12, Component.literal("100x"),
                btn -> {
                    long cnt = Math.max(1L, Math.min(64L, parseAmount(1L)));
                    TyC2ForgeNetwork.sendToServer(new C2SAtmActionPacket(
                            menu.getAtmPos(), C2SAtmActionPacket.Action.WITHDRAW_DENOM, cnt, selectedDenomIndex, "", ""
                    ));
                }
        ));
        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 95, topPos + 97, 11, 12, Component.literal(">"),
                btn -> {
                    selectedDenomIndex = Math.floorMod(selectedDenomIndex + 1, CurrencyDenomination.values().length);
                    updateButtonLabels();
                }
        ));

        // Row 3: Transfer / Personal-Company switch / Company Management
        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 36, topPos + 110, 28, 12, Component.literal("Send"),
                btn -> sendAction(C2SAtmActionPacket.Action.TRANSFER, parseAmount(0L), targetBox.getValue())
        ));
        modeBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 65, topPos + 110, 26, 12, Component.literal("Acct"),
                btn -> toggleSettlementMode()
        ));
        companyPanelBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 92, topPos + 110, 15, 12, Component.literal("Co"),
                btn -> {
                    showCompanyPanel = !showCompanyPanel;
                    updateButtonLabels();
                }
        ));

        // Right LCD (111, 56, 75x68) sub-controls: Company create/join/leave OR Log pagination
        createCoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 114, topPos + 70, 69, 13, Component.translatable("gui.tyc2.account.create_company"),
                btn -> sendAction(C2SAtmActionPacket.Action.CREATE_COMPANY, 0L, targetBox.getValue())
        ));
        joinCoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 114, topPos + 86, 69, 13, Component.translatable("gui.tyc2.account.join_company"),
                btn -> sendAction(C2SAtmActionPacket.Action.JOIN_COMPANY, 0L, targetBox.getValue())
        ));
        leaveCoBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 114, topPos + 102, 69, 13, Component.translatable("gui.tyc2.account.leave_company"),
                btn -> sendAction(C2SAtmActionPacket.Action.LEAVE_COMPANY, 0L, sync == null ? "" : sync.activeCompanyId)
        ));

        logPrevBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 157, topPos + 111, 12, 11, Component.literal("<"),
                btn -> logPage = Math.max(0, logPage - 1)
        ));
        logNextBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 171, topPos + 111, 12, 11, Component.literal(">"),
                btn -> logPage = Math.min(5, logPage + 1)
        ));

        updateButtonLabels();
        if (menu.getLatestSync() != null) {
            onSync(menu.getLatestSync());
        }
        TyC2ForgeNetwork.sendToServer(C2SAtmActionPacket.simple(menu.getAtmPos(), C2SAtmActionPacket.Action.REQUEST_SYNC));
    }

    public void onSync(S2CAtmSyncPacket pkt) {
        this.sync = pkt;
        this.menu.setLatestSync(pkt);
        updateButtonLabels();
    }

    private void updateButtonLabels() {
        CurrencyDenomination denom = CurrencyDenomination.values()[selectedDenomIndex];
        denomBtn.setMessage(Component.literal("W:" + denom.value() + "x"));
        companyPanelBtn.setHighlighted(showCompanyPanel);
        modeBtn.setHighlighted(sync != null && sync.companyMode);

        createCoBtn.visible = showCompanyPanel;
        joinCoBtn.visible = showCompanyPanel;
        leaveCoBtn.visible = showCompanyPanel && sync != null && !sync.activeCompanyId.isEmpty();
        logPrevBtn.visible = !showCompanyPanel;
        logNextBtn.visible = !showCompanyPanel;
    }

    private void toggleSettlementMode() {
        if (sync == null) {
            return;
        }
        if (sync.companyMode) {
            sendAction(C2SAtmActionPacket.Action.SWITCH_TO_PERSONAL, 0L, "");
        } else if (!sync.companies.isEmpty()) {
            sendAction(C2SAtmActionPacket.Action.SWITCH_TO_COMPANY, 0L, sync.companies.get(0).id());
        } else {
            showCompanyPanel = true;
            updateButtonLabels();
        }
    }

    private void sendAction(C2SAtmActionPacket.Action action, long amt, String text) {
        TyC2ForgeNetwork.sendToServer(new C2SAtmActionPacket(menu.getAtmPos(), action, amt, selectedDenomIndex, text, ""));
    }

    private long parseAmount(long fallback) {
        try {
            long val = Long.parseLong(amountBox.getValue().trim());
            return val > 0L ? val : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Mini EU bar (196,56,14x30) -> fill sprite (224,0,12x28)
        int eu = menu.getStoredEu();
        int maxEu = menu.getMaxEu();
        if (eu > 0) {
            int fillH = Math.min(28, Math.max(1, (eu * 28) / maxEu));
            blit(poseStack, leftPos + 197, topPos + 57 + (28 - fillH), 224, 28 - fillH, 12, fillH);
        }

        // Personal (224,32,16x16) or Guild (224,52,16x16) icon inside Top LCD
        boolean co = sync != null && sync.companyMode;
        blit(poseStack, leftPos + 12, topPos + 25, 224, co ? 52 : 32, 16, 16);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303840);
        font.draw(poseStack, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040);

        // Top LCD (8, 18, 204x32)
        if (sync != null) {
            String acctHeader = sync.companyMode
                    ? Component.translatable("gui.tyc2.account.company_named", sync.activeCompanyName).getString()
                    : Component.translatable("gui.tyc2.account.personal").getString();
            font.draw(poseStack, font.plainSubstrByWidth(acctHeader, 110), 32, 21, 0x55FFFF);
            String balStr = Component.translatable("gui.tyc2.atm.balance", sync.activeBalance()).getString();
            font.draw(poseStack, balStr, 32, 31, 0x66FF88);
            if (sync.companyMode && !sync.activeCompanyInvite.isEmpty()) {
                font.draw(poseStack, sync.activeCompanyInvite, 146, 21, 0xFFCC44);
            }
            if (!sync.feedback.isEmpty()) {
                int fw = font.width(sync.feedback);
                if (fw <= 176) {
                    font.draw(poseStack, sync.feedback, 32, 41, 0xFFDD77);
                } else {
                    float needed = 176.0F / fw;
                    float scale = Math.max(0.68F, needed);
                    String rendered = needed >= 0.68F
                            ? sync.feedback
                            : font.plainSubstrByWidth(sync.feedback, (int) Math.floor(176.0F / scale));
                    poseStack.pushPose();
                    poseStack.translate(32.0F, 41.0F, 0.0F);
                    poseStack.scale(scale, scale, 1.0F);
                    font.draw(poseStack, rendered, 0, 0, 0xFFDD77);
                    poseStack.popPose();
                }
            }

            // Right LCD (111, 56, 75x68)
            if (showCompanyPanel) {
                font.draw(poseStack, Component.translatable("gui.tyc2.account.company"), 114, 59, 0x55FFFF);
            } else {
                font.draw(poseStack, "Log (" + sync.logs.size() + ")", 114, 59, 0x55FFFF);
                int start = logPage * 5;
                for (int i = 0; i < 5 && start + i < sync.logs.size(); i++) {
                    String entry = sync.logs.get(start + i);
                    int col = entry.startsWith("+") ? 0x66FF88 : 0xFF8877;
                    font.draw(poseStack, font.plainSubstrByWidth(entry, 69), 114, 70 + i * 8, col);
                }
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (relX >= 196 && relX <= 210 && relY >= 56 && relY <= 86) {
            int eu = menu.getStoredEu();
            renderTooltip(
                    poseStack,
                    Component.translatable("gui.tyc2.energy.eu_fe", eu, menu.getMaxEu(), (long) eu * TriModePowerMath.EU_TO_FE_RATIO),
                    mouseX, mouseY
            );
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (amountBox.isFocused() && amountBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (targetBox.isFocused() && targetBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (amountBox.isFocused() || targetBox.isFocused()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
