package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.forge.gui.PrimitiveMintMachineMenu;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Forge 1.19.2 screen for {@code tyc2:primitive_mint_machine} matching
 * {@code gui/primitive_mint_machine.png} (176x166): burnable fuel flame gauge,
 * progress arrow, and right status LCD with Amber Warning Badge when industrial-only
 * inputs (200x-1000x or modded alloys) are inserted.
 */
public class PrimitiveMintMachineScreen extends AbstractContainerScreen<PrimitiveMintMachineMenu> {

    private static final ResourceLocation TEXTURE = TyC2Ids.id("textures/gui/primitive_mint_machine.png");

    public PrimitiveMintMachineScreen(PrimitiveMintMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Lit Flame (49, 41, 14x14) from right-margin sprite (176, 0, 14x14) bottom-up
        int burnRem = menu.getBurnRemaining();
        if (burnRem > 0) {
            int flameH = Math.min(14, Math.max(1, (burnRem * 14) / menu.getBurnTotal()));
            blit(poseStack, leftPos + 49, topPos + 41 + (14 - flameH), 176, 14 - flameH, 14, flameH);
        }

        // Lit Press Arrow (82, 23, 24x16) from right-margin sprite (176, 14, 24x16) left-to-right
        int prog = menu.getProgress();
        if (prog > 0) {
            int arrowW = Math.min(24, Math.max(1, (prog * 24) / menu.getMaxProgress()));
            blit(poseStack, leftPos + 82, topPos + 23, 176, 14, arrowW, 16);
        }

        // Amber Warning Badge (176, 32, 14x14) inside Status LCD (76, 48, 92x25) when blocked
        if (menu.isBlockedByIndustrialTier()) {
            blit(poseStack, leftPos + 78, topPos + 53, 176, 32, 14, 14);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x404040);
        font.draw(poseStack, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040);

        // Status / Upgrade-Required Banner LCD (76, 48, 92x25) -> inner (78..166, 50..71)
        if (menu.isBlockedByIndustrialTier()) {
            String warnTitle = Component.translatable("gui.tyc2.primitive_mint.requires_industrial").getString();
            drawFittedString(poseStack, warnTitle, 94, 51, 70, 0.50F, 0xFFCC44);
            List<FormattedCharSequence> detailLines = font.split(
                    Component.translatable("gui.tyc2.primitive_mint.requires_industrial_detail"), 130
            );
            if (detailLines.size() <= 2) {
                poseStack.pushPose();
                poseStack.translate(94.0F, 59.0F, 0.0F);
                poseStack.scale(0.54F, 0.54F, 1.0F);
                for (int i = 0; i < detailLines.size(); i++) {
                    font.draw(poseStack, detailLines.get(i), 0, i * 11, 0xFFAA66);
                }
                poseStack.popPose();
            } else {
                drawFittedString(poseStack, "200x-1000x / Alloys", 94, 62, 70, 0.50F, 0xFFAA66);
            }
        } else {
            String line1;
            int color1;
            if (menu.getProgress() > 0) {
                int pct = Math.min(100, (menu.getProgress() * 100) / menu.getMaxProgress());
                line1 = Component.translatable("gui.tyc2.primitive_mint.minting", pct).getString();
                color1 = 0x66FF88;
            } else if (menu.getBurnRemaining() <= 0 && menu.hasAnyInput()) {
                line1 = Component.translatable("gui.tyc2.primitive_mint.no_fuel").getString();
                color1 = 0xFF8866;
            } else {
                line1 = Component.translatable("gui.tyc2.primitive_mint.idle").getString();
                color1 = 0xDDE6EE;
            }
            String scope = Component.translatable("gui.tyc2.primitive_mint.scope_hint").getString();
            drawFittedString(poseStack, line1, 80, 52, 85, 0.65F, color1);
            drawFittedString(poseStack, scope, 80, 63, 85, 0.65F, 0xAACCDD);
        }
    }

    private void drawFittedString(
            PoseStack poseStack,
            String text,
            int x,
            int y,
            int maxWidth,
            float minScale,
            int color
    ) {
        int w = font.width(text);
        if (w <= maxWidth) {
            font.draw(poseStack, text, x, y, color);
            return;
        }
        float needed = maxWidth / (float) w;
        float scale = Math.max(minScale, needed);
        // Draw full string when needed >= minScale to avoid float-truncation cutoff of the final glyph
        String rendered = needed >= minScale
                ? text
                : font.plainSubstrByWidth(text, (int) Math.floor(maxWidth / scale));
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        font.draw(poseStack, rendered, 0, 0, color);
        poseStack.popPose();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (relX >= 49 && relX <= 63 && relY >= 41 && relY <= 55) {
            int sec = Math.max(0, menu.getBurnRemaining() / 20);
            renderTooltip(poseStack, Component.translatable("hud.tyc2.primitive_mint.fuel", sec), mouseX, mouseY);
        } else if (relX >= 82 && relX <= 106 && relY >= 23 && relY <= 39) {
            Component msg;
            if (menu.isBlockedByIndustrialTier()) {
                msg = Component.translatable("gui.tyc2.primitive_mint.requires_industrial");
            } else if (menu.getProgress() > 0) {
                int pct = Math.min(100, (menu.getProgress() * 100) / menu.getMaxProgress());
                msg = Component.translatable("gui.tyc2.primitive_mint.minting", pct);
            } else if (menu.getBurnRemaining() <= 0) {
                msg = Component.translatable("gui.tyc2.primitive_mint.no_fuel");
            } else {
                msg = Component.translatable("gui.tyc2.primitive_mint.idle");
            }
            renderTooltip(poseStack, msg, mouseX, mouseY);
        } else if (relX >= 76 && relX <= 168 && relY >= 48 && relY <= 73 && menu.isBlockedByIndustrialTier()) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.tyc2.primitive_mint.requires_industrial"));
            tip.add(Component.translatable("gui.tyc2.primitive_mint.requires_industrial_detail"));
            renderComponentTooltip(poseStack, tip, mouseX, mouseY);
        }
    }
}
