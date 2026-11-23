package com.tycooncraft.tyc2.item;

import com.tycooncraft.tyc2.platform.TyC2TooltipItem;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * NeoForge 26.x base item class for {@link CommTerminalItem} bridging {@code Item.use} return types.
 */
public abstract class CommTerminalItemBase extends TyC2TooltipItem {

    protected CommTerminalItemBase(Properties properties, List<String> tooltipKeys) {
        super(properties, tooltipKeys);
    }

    /** Handles right-clicking the Communication Terminal in air. Returns {@code true} if handled. */
    protected abstract boolean handleUseTerminal(Level level, Player player, InteractionHand hand, ItemStack stack);

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (handleUseTerminal(level, player, hand, stack)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
