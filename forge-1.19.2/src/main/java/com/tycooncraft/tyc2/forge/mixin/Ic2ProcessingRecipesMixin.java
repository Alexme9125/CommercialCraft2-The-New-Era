package com.tycooncraft.tyc2.forge.mixin;

import com.industrialage.ia2.content.ProcessingMachine;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters out IC2's {@code metal_former_cutting -> ic2:coin} recipe when
 * {@code config.disableIc2MetalFormerCoinRecipe} is enabled so players use the
 * Industrial Minting Machine. Only applied when {@code ic2} is loaded.
 */
@Mixin(targets = "com.industrialage.ia2.content.ProcessingRecipes", remap = false)
public class Ic2ProcessingRecipesMixin {

    @Inject(method = "find", at = @At("RETURN"), cancellable = true, remap = false)
    private void tyc2$filterCoinCutting(
            String machine,
            String item,
            Predicate<String> tagTest,
            CallbackInfoReturnable<ProcessingMachine.Recipe> cir
    ) {
        if (!TyC2RuntimeData.config().disableIc2MetalFormerCoinRecipe()) {
            return;
        }
        ProcessingMachine.Recipe recipe = cir.getReturnValue();
        if (recipe != null
                && "metal_former_cutting".equals(machine)
                && CurrencyDenomination.IC2_COIN_ID.equals(recipe.output())) {
            cir.setReturnValue(null);
        }
    }
}
