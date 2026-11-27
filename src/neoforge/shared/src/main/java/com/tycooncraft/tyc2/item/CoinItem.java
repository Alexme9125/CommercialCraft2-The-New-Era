package com.tycooncraft.tyc2.item;

import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.platform.TyC2TooltipItem;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Physical Industrial Credit coin item for one of the 9 {@link CurrencyDenomination}s.
 */
public final class CoinItem extends TyC2TooltipItem {

    private final CurrencyDenomination denomination;

    public CoinItem(Properties properties, CurrencyDenomination denomination) {
        super(properties, List.of());
        this.denomination = denomination;
    }

    /** Returns the currency denomination represented by this coin item. */
    public CurrencyDenomination denomination() {
        return denomination;
    }

    @Override
    protected void addDynamicTooltips(ItemStack stack, Consumer<Component> adder) {
        adder.accept(Component.translatable(
                "tooltip.tyc2.coin.value",
                denomination.value()
        ).withStyle(ChatFormatting.GOLD));
        if (denomination == CurrencyDenomination.X1 && Ic2SoftBridge.isIc2Loaded()) {
            adder.accept(Component.translatable("tooltip.tyc2.coin.ic2_compat")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
