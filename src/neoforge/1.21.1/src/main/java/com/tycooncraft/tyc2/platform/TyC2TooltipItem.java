package com.tycooncraft.tyc2.platform;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * NeoForge 1.21.1 Item base supporting static localized tooltip keys and dynamic subclass tooltip lines.
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
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        addDynamicTooltips(stack, tooltipComponents::add);
        for (int i = 0; i < tooltipKeys.size(); i++) {
            ChatFormatting color = i == 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
            tooltipComponents.add(Component.translatable(tooltipKeys.get(i)).withStyle(color));
        }
    }
}
