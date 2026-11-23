package com.tycooncraft.tyc2.mixin;

import com.tycooncraft.tyc2.world.TyC2DataLoader;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters IC2's {@code WorkshopRecipes} Metal Former cutting recipe that produces {@code ic2:coin}
 * whenever {@code disableIc2MetalFormerCoinRecipe} is enabled, keeping JEI/machine views consistent
 * with TyC2's Minting Machine currency rules.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopRecipes", remap = false)
public abstract class Ic2WorkshopRecipesMixin {

    @Inject(method = "forType", at = @At("RETURN"), cancellable = true)
    private static void tyc2$filterWorkshopCoinRecipes(
            String type,
            CallbackInfoReturnable<List<?>> cir
    ) {
        if (!TyC2DataLoader.config().disableIc2MetalFormerCoinRecipe()) {
            return;
        }
        List<?> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        boolean removedAny = false;
        List<Object> filtered = new ArrayList<>(original.size());
        for (Object recipe : original) {
            if (recipe != null && recipe.toString().contains("id=ic2:coin")) {
                removedAny = true;
            } else {
                filtered.add(recipe);
            }
        }
        if (removedAny) {
            cir.setReturnValue(filtered);
        }
    }
}
