package com.tycooncraft.tyc2.mixin;

import com.industrialage.ia2.content.ProcessingMachine;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents IC2's Metal Former (Cutting mode) from minting {@code ic2:coin} directly from iron
 * casings when {@code disableIc2MetalFormerCoinRecipe} is enabled in TyC2's config, preserving
 * macro-economic money-supply accounting through the Industrial Minting Machine.
 */
@Mixin(targets = "com.industrialage.ia2.content.ProcessingRecipes", remap = false)
public abstract class Ic2ProcessingRecipesMixin {

    @Inject(method = "find", at = @At("RETURN"), cancellable = true)
    private void tyc2$blockMetalFormerCoinRecipe(
            String machine,
            String itemId,
            Predicate<String> hasTag,
            CallbackInfoReturnable<ProcessingMachine.Recipe> cir
    ) {
        if (!TyC2DataLoader.config().disableIc2MetalFormerCoinRecipe()) {
            return;
        }
        ProcessingMachine.Recipe recipe = cir.getReturnValue();
        if (recipe != null && "ic2:coin".equals(recipe.output())) {
            cir.setReturnValue(null);
        }
    }
}
