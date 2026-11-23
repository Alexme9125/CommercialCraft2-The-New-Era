package com.tycooncraft.tyc2.platform;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * NeoForge 26.x Item base supporting static localized tooltip keys and dynamic subclass lines.
 */
public class TyC2TooltipItem extends Item {

    private final List<String> tooltipKeys;

    public TyC2TooltipItem(Properties properties, List<String> tooltipKeys) {
        super(properties);
        this.tooltipKeys = tooltipKeys == null ? List.of() : List.copyOf(tooltipKeys);
    }

    /** Hook for subclasses to append dynamic tooltip lines in a version-neutral way. */
    protected void addDynamicTooltips(ItemStack stack, Consumer<Component> adder) {
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltipAdder,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        addDynamicTooltips(stack, tooltipAdder);
        for (int i = 0; i < tooltipKeys.size(); i++) {
            ChatFormatting color = i == 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
            tooltipAdder.accept(Component.translatable(tooltipKeys.get(i)).withStyle(color));
        }
    }
}
