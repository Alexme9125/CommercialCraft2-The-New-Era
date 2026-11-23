package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.TyC2Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge 1.21.1 container screen bridge wrapping {@link GuiGraphics} behind {@link ScreenCanvas}
 * (256x256 texture atlases) and protecting focused {@link EditBox} fields from close keybinds.
 */
public abstract class TyC2ScreenBase<M extends AbstractContainerMenu>
        extends AbstractContainerScreen<M> {

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private final List<EditBox> editBoxes = new ArrayList<>();

    protected TyC2ScreenBase(
            M menu,
            Inventory playerInventory,
            Component title,
            int panelWidth,
            int panelHeight
    ) {
        super(menu, playerInventory, title);
        this.imageWidth = panelWidth;
        this.imageHeight = panelHeight;
    }

    protected final void clearEditBoxes() {
        editBoxes.clear();
    }

    protected final EditBox addTrackedEditBox(EditBox box) {
        editBoxes.add(box);
        return box;
    }

    protected abstract void paintPanel(ScreenCanvas canvas, int mouseX, int mouseY);

    protected abstract void paintOverlayTooltips(ScreenCanvas canvas, int mouseX, int mouseY);

    /** Hook for panel-local mouse clicks. Returns {@code true} if handled. */
    protected boolean handlePanelClick(double panelMouseX, double panelMouseY, int button, boolean shift) {
        return false;
    }

    /** Returns {@code true} if standard title & player inventory labels should be drawn automatically. */
    protected boolean drawDefaultLabels() {
        return true;
    }

    protected Component headerTitle() {
        return title;
    }

    private static ResourceLocation guiTexture(String name) {
        return TEXTURES.computeIfAbsent(
                name,
                n -> ResourceLocation.fromNamespaceAndPath(
                        TyC2Constants.MOD_ID,
                        "textures/gui/" + n + ".png"
                )
        );
    }

    private ScreenCanvas wrapCanvas(GuiGraphics g) {
        return new ScreenCanvas() {
            @Override
            public void blit(String guiName, int x, int y, int u, int v, int width, int height) {
                if (width <= 0 || height <= 0) {
                    return;
                }
                g.blit(guiTexture(guiName), leftPos + x, topPos + y, (float) u, (float) v, width, height, 256, 256);
            }

            @Override
            public void fill(int x1, int y1, int x2, int y2, int argbColor) {
                g.fill(leftPos + x1, topPos + y1, leftPos + x2, topPos + y2, argbColor);
            }

            @Override
            public void outline(int x, int y, int width, int height, int argbColor) {
                g.renderOutline(leftPos + x, topPos + y, width, height, argbColor);
            }

            @Override
            public void drawItem(ItemStack stack, int x, int y) {
                if (stack != null && !stack.isEmpty()) {
                    g.renderItem(stack, leftPos + x, topPos + y);
                }
            }

            @Override
            public void drawItemDecorations(ItemStack stack, int x, int y, String countOverride) {
                if (stack != null && !stack.isEmpty()) {
                    g.renderItemDecorations(font, stack, leftPos + x, topPos + y, countOverride);
                }
            }

            @Override
            public void drawString(Component text, int x, int y, int argbColor) {
                g.drawString(font, text, leftPos + x, topPos + y, argbColor, false);
            }

            @Override
            public void drawClippedString(String text, int x, int y, int maxWidth, int argbColor) {
                if (text == null || text.isEmpty() || maxWidth <= 0) {
                    return;
                }
                String rendered = font.plainSubstrByWidth(text, maxWidth);
                g.drawString(font, rendered, leftPos + x, topPos + y, argbColor, false);
            }

            @Override
            public void drawScaledString(String text, int x, int y, int maxWidth, float scale, int argbColor) {
                if (text == null || text.isEmpty() || maxWidth <= 0 || scale <= 0.0F) {
                    return;
                }
                int scaledMax = Math.max(1, (int) Math.floor(maxWidth / scale));
                String rendered = font.plainSubstrByWidth(text, scaledMax);
                g.pose().pushPose();
                g.pose().translate(leftPos + x, topPos + y, 0.0F);
                g.pose().scale(scale, scale, 1.0F);
                g.drawString(font, rendered, 0, 0, argbColor, false);
                g.pose().popPose();
            }

            @Override
            public int textWidth(String text) {
                return text == null ? 0 : font.width(text);
            }

            @Override
            public void drawTooltip(List<Component> lines, int mouseX, int mouseY) {
                if (lines != null && !lines.isEmpty()) {
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        paintPanel(wrapCanvas(g), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!drawDefaultLabels()) {
            return;
        }
        g.drawString(font, headerTitle(), titleLabelX, titleLabelY, 0xFF2C3440, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF2C3440, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        paintOverlayTooltips(wrapCanvas(g), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handlePanelClick(mouseX - leftPos, mouseY - topPos, button, hasShiftDown())) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : editBoxes) {
            if (box.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    box.setFocused(false);
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
                return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
