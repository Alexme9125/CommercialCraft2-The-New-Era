package com.tycooncraft.tyc2.client;

import com.tycooncraft.tyc2.TyC2Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    /** Creates a custom-themed industrial {@link StyledButton} extending vanilla {@link Button}. */
    protected final StyledButton createStyledButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            TyC2Theme.ButtonStyle style,
            Button.OnPress onPress
    ) {
        return new StyledButton(x, y, width, height, message, style, onPress);
    }

    /** Creates and registers a custom-themed industrial {@link StyledButton} widget. */
    protected final StyledButton addStyledButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            TyC2Theme.ButtonStyle style,
            Button.OnPress onPress
    ) {
        return addRenderableWidget(createStyledButton(x, y, width, height, message, style, onPress));
    }

    /**
     * Custom-rendered industrial button extending vanilla {@link Button} so automated QA drivers
     * and accessibility narration continue to work identically while replacing vanilla grey chrome
     * with bespoke beveled dark-ledger / tactical-PDA styling and fit-scaled centered text.
     */
    public static final class StyledButton extends Button {

        private TyC2Theme.ButtonStyle style;
        private boolean selected;

        public StyledButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                TyC2Theme.ButtonStyle style,
                OnPress onPress
        ) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style == null ? TyC2Theme.ButtonStyle.HUB_ACTION : style;
        }

        /** Marks this button as actively selected (e.g. active tab/sub-tab highlight). */
        public StyledButton setSelected(boolean selected) {
            this.selected = selected;
            return this;
        }

        /** Returns whether this button is rendered in its selected visual state. */
        public boolean isSelected() {
            return selected;
        }

        /** Updates this button's visual theme preset dynamically. */
        public StyledButton setStyle(TyC2Theme.ButtonStyle style) {
            if (style != null) {
                this.style = style;
            }
            return this;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            TyC2Theme.ButtonVisual vis = TyC2Theme.resolveButtonVisual(
                    style, active, isHoveredOrFocused(), selected
            );

            // Crisp beveled industrial chrome (zero vanilla grey button texture)
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, vis.fill());
            if (h >= 4) {
                g.fill(x + 1, y + 1, x + w - 1, y + 2, vis.topBevel());
                g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, vis.bottomBevel());
            }
            if (vis.accentBar() != 0 && w >= 4 && h >= 4) {
                int barH = h >= 14 ? 2 : 1;
                g.fill(x + 1, y + 1, x + w - 1, y + 1 + barH, vis.accentBar());
            }
            g.renderOutline(x, y, w, h, vis.border());

            // Fit-scaled centered label (never clips in en_us or zh_cn)
            Font font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            if (label.isEmpty() || w <= 4) {
                return;
            }
            int availW = Math.max(4, w - 4);
            int rawW = font.width(label);
            float scale = rawW <= availW ? 1.0F : Math.max(0.50F, availW / (float) rawW);
            String rendered = rawW <= availW
                    ? label
                    : font.plainSubstrByWidth(label, Math.max(1, (int) Math.floor(availW / scale)));
            float drawW = font.width(rendered) * scale;
            float drawH = 8.0F * scale;
            float tx = x + (w - drawW) * 0.5F;
            float ty = y + (h - drawH) * 0.5F + (vis.accentBar() != 0 && h >= 14 ? 0.8F : 0.3F);

            g.pose().pushPose();
            g.pose().translate(tx, ty, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, rendered, 0, 0, vis.textColor(), true);
            g.pose().popPose();
        }
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
