package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.network.C2SMayorNegotiatePacket;
import com.tycooncraft.tyc2.forge.network.S2CMayorSyncPacket;
import com.tycooncraft.tyc2.forge.network.TyC2ForgeNetwork;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Forge 1.19.2 Village Partnership Negotiation screen matching {@code gui/mayor_negotiation.png} (220x192).
 */
public class MayorNegotiationScreen extends AbstractContainerScreen<MayorNegotiationMenu> {

    private static final ResourceLocation TEXTURE = TyC2Ids.id("textures/gui/mayor_negotiation.png");

    @Nullable
    private S2CMayorSyncPacket sync;
    private TyC2UiWidgets.CompactButton signBtn;

    public MayorNegotiationScreen(MayorNegotiationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 220;
        this.imageHeight = 192;
        this.titleLabelX = 8;
        this.titleLabelY = 4;
    }

    @Override
    protected void init() {
        super.init();

        // Action bar (8, 118, 204x26)
        signBtn = addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 36, topPos + 123, 114, 16,
                Component.translatable("gui.mnera.mayor.sign"),
                btn -> TyC2ForgeNetwork.sendToServer(new C2SMayorNegotiatePacket(menu.getVillageId(), true))
        ).setHighlighted(true).setAccentColor(0xFF2E8B57));

        addRenderableWidget(new TyC2UiWidgets.CompactButton(
                leftPos + 156, topPos + 123, 50, 16,
                Component.translatable("gui.mnera.cancel"),
                btn -> onClose()
        ));

        if (menu.getLatestSync() != null) {
            onSync(menu.getLatestSync());
        }
        TyC2ForgeNetwork.sendToServer(new C2SMayorNegotiatePacket(menu.getVillageId(), false));
    }

    public void onSync(S2CMayorSyncPacket pkt) {
        this.sync = pkt;
        this.menu.setLatestSync(pkt);
        if (signBtn != null) {
            signBtn.active = !pkt.partnered && pkt.allMet();
        }
        if (pkt.partnered) {
            onClose();
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Wax seal sprite (64,196,20x20) inside Action Bar
        blit(poseStack, leftPos + 12, topPos + 121, 64, 196, 20, 20);

        if (sync != null) {
            // Right column of parchment contract box (divider at x=92..93): check/cross at x=95
            drawCheckOrCross(poseStack, leftPos + 95, topPos + 54, sync.currencyMet());
            drawCheckOrCross(poseStack, leftPos + 95, topPos + 68, sync.logsMet());
            drawCheckOrCross(poseStack, leftPos + 95, topPos + 82, sync.ironMet());
            drawCheckOrCross(poseStack, leftPos + 95, topPos + 96, sync.foodMet());
        }
    }

    private void drawCheckOrCross(PoseStack poseStack, int x, int y, boolean ok) {
        // Check (8,196,14x14), Cross (26,196,14x14)
        blit(poseStack, x, y, ok ? 8 : 26, 196, 14, 14);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303840);

        if (sync != null) {
            // Mayor banner (8,14,204x34); crown + gold vertical divider at x=35 -> text starts at x=39 (169px wide)
            String mayorTitle = Component.translatable("mayor.mnera.name_format", sync.villageName).getString();
            font.draw(poseStack, font.plainSubstrByWidth(mayorTitle, 169), 39, 17, 0xFFE066);

            poseStack.pushPose();
            poseStack.translate(39.0F, 28.0F, 0.0F);
            poseStack.scale(0.80F, 0.80F, 1.0F);
            List<FormattedCharSequence> greetLines = font.split(Component.translatable("gui.mnera.mayor.greeting"), 210);
            for (int i = 0; i < Math.min(2, greetLines.size()); i++) {
                font.draw(poseStack, greetLines.get(i), 0, i * 10, 0xD0DCEB);
            }
            poseStack.popPose();

            // Left half of Contract Box (12..89, 77px wide): Village Profile
            font.draw(poseStack, font.plainSubstrByWidth(
                    Component.translatable("gui.mnera.mayor.village_info").getString(), 77
            ), 12, 56, 0x3A2D18);
            font.draw(poseStack, font.plainSubstrByWidth(sync.villageName, 77), 12, 69, 0x202020);
            font.draw(poseStack, font.plainSubstrByWidth(
                    Component.translatable("biome.mnera." + sync.biomeStyleId).getString(), 77
            ), 12, 82, 0x444444);
            font.draw(poseStack, font.plainSubstrByWidth(
                    Component.translatable("gui.mnera.hub.population", sync.population).getString(), 77
            ), 12, 95, 0x1F6E35);

            // Right half of Contract Box (95..208): 4 Requirements with high-contrast ink on parchment (100% localized)
            long totalIc = sync.cashIc + sync.bankIc;
            drawReqLine(poseStack, 57, sync.currencyMet(),
                    Component.translatable("gui.mnera.mayor.req_currency", TyC2UiWidgets.compactIc(totalIc) + "/" + sync.reqIc).getString());
            drawReqLine(poseStack, 71, sync.logsMet(),
                    Component.translatable("gui.mnera.mayor.req_logs", sync.haveLogs + "/" + sync.reqLogs).getString());
            drawReqLine(poseStack, 85, sync.ironMet(),
                    Component.translatable("gui.mnera.mayor.req_iron", sync.haveIron + "/" + sync.reqIron).getString());
            drawReqLine(poseStack, 99, sync.foodMet(),
                    Component.translatable("gui.mnera.mayor.req_food", sync.haveFood + "/" + sync.reqFood).getString());

            // Benefits LCD (8,148,204x36) — 0.65F fit-scale so all 3 localized lines fit with zero clipping
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.mnera.mayor.benefits.line1").getString(), 12, 152, 196, 0.58F, 0x8BE8B0);
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.mnera.mayor.benefits.line2").getString(), 12, 162, 196, 0.58F, 0x8BE8B0);
            TyC2UiWidgets.drawFittedString(poseStack, font,
                    Component.translatable("gui.mnera.mayor.benefits.line3").getString(), 12, 172, 196, 0.58F, 0x8BE8B0);
        }
    }

    private void drawReqLine(PoseStack poseStack, int y, boolean ok, String localizedLabel) {
        TyC2UiWidgets.drawFittedString(poseStack, font, localizedLabel, 110, y, 98, 0.58F, ok ? 0x1F6E35 : 0xA62626);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);
    }
}
