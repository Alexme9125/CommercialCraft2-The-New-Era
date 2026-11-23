package com.tycooncraft.tyc2.forge.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem with localized multi-line tooltips and relocated Cooperation Hub village-binding display.
 */
public class TyC2BlockItem extends BlockItem {

    public static final String TAG_BOUND_VILLAGE_ID = "tyc2_bound_village_id";
    public static final String TAG_BOUND_VILLAGE_NAME = "tyc2_bound_village_name";

    private final String[] tooltipKeys;

    public TyC2BlockItem(Block block, Properties properties, String... tooltipKeys) {
        super(block, properties);
        this.tooltipKeys = tooltipKeys;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_BOUND_VILLAGE_NAME)) {
            tooltip.add(Component.translatable(
                    "tooltip.tyc2.cooperation_hub.bound",
                    tag.getString(TAG_BOUND_VILLAGE_NAME)
            ).withStyle(ChatFormatting.AQUA));
        }
        for (String key : tooltipKeys) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
