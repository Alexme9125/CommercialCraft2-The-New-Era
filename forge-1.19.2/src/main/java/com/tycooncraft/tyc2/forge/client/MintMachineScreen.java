package com.tycooncraft.tyc2.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.gui.MintMachineMenu;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Forge 1.19.2 screen for {@code tyc2:mint_machine} matching {@code gui/mint_machine.png} (176x176).
 */
public class MintMachineScreen extends AbstractContainerScreen<MintMachineMenu> {

    private static final ResourceLocation TEXTURE = TyC2Ids.id("textures/gui/mint_machine.png");

    public MintMachineScreen(MintMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 176;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 83;
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // EU gauge frame (12,20,14x48) -> fill sprite (176,32,12x46)
        int stored = menu.getStoredEu();
        int maxEu = menu.getMaxEu();
        if (stored > 0) {
            int fillH = Math.min(46, Math.max(1, (stored * 46) / maxEu));
            blit(poseStack, leftPos + 13, topPos + 21 + (46 - fillH), 176, 32 + (46 - fillH), 12, fillH);
            // Lit bolt sprite (190,32,10x14)
            blit(poseStack, leftPos + 14, topPos + 70, 190, 32, 10, 14);
        }

        // Battery-discharge lightning indicator (59,57,14x14) -> lit sprite (176,0,14x14)
        if (menu.isBatteryDischarging()) {
            blit(poseStack, leftPos + 59, topPos + 57, 176, 0, 14, 14);
        }

        // Progress arrow (80,27,24x16) -> sprite (176,14,24x16)
        int prog = menu.getProgress();
        if (prog > 0) {
            int arrowW = Math.min(24, Math.max(1, (prog * 24) / menu.getMaxProgress()));
            blit(poseStack, leftPos + 80, topPos + 27, 176, 14, arrowW, 16);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (relX >= 12 && relX <= 26 && relY >= 20 && relY <= 68) {
            List<Component> tip = new ArrayList<>();
            int eu = menu.getStoredEu();
            int max = menu.getMaxEu();
            tip.add(Component.translatable("gui.mnera.energy.eu_fe", eu, max, (long) eu * TriModePowerMath.EU_TO_FE_RATIO));
            tip.add(Component.translatable("gui.mnera.energy.tier", menu.getPowerTier(), menu.getMaxInputEuPerTick()));
            renderComponentTooltip(poseStack, tip, mouseX, mouseY);
        } else if (relX >= 59 && relX <= 73 && relY >= 57 && relY <= 71) {
            renderTooltip(poseStack, Component.translatable("gui.mnera.slot.battery_discharge"), mouseX, mouseY);
        } else if (relX >= 80 && relX <= 104 && relY >= 27 && relY <= 43) {
            Component msg;
            if (menu.getProgress() > 0) {
                int pct = Math.min(100, (menu.getProgress() * 100) / menu.getMaxProgress());
                msg = Component.translatable("gui.mnera.mint.minting", pct);
            } else if (menu.getStoredEu() <= 0 && !menu.isBatteryDischarging()) {
                msg = Component.translatable("gui.mnera.mint.no_power");
            } else {
                msg = Component.translatable("gui.mnera.mint.idle");
            }
            renderTooltip(poseStack, msg, mouseX, mouseY);
        }
    }
}
