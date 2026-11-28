package com.tycooncraft.tyc2.forge.item;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Industrial Credit legal-tender coin item representing one of the 9 denominations
 * ({@code 1x..1000x}).
 */
public class CoinItem extends Item {

    private final CurrencyDenomination denomination;

    public CoinItem(CurrencyDenomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    /** Returns the currency denomination represented by this coin item. */
    public CurrencyDenomination denomination() {
        return denomination;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mnera.coin.value", denomination.value())
                .withStyle(ChatFormatting.GOLD));
        if (denomination == CurrencyDenomination.X1 && Ic2Bridge.isLoaded()) {
            tooltip.add(Component.translatable("tooltip.mnera.coin.ic2_compat")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
