package com.tycooncraft.tyc2.forge.mixin;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters out IC2 Workshop {@code metal_former_cutting -> ic2:coin} recipes when
 * {@code config.disableIc2MetalFormerCoinRecipe} is enabled. Only applied when {@code ic2} is loaded.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopRecipes", remap = false)
public class Ic2WorkshopRecipesMixin {

    private static volatile Method recipeOutputsMethod;
    private static volatile Method stackIdMethod;

    @Inject(method = "forType", at = @At("RETURN"), cancellable = true, remap = false)
    private static void tyc2$filterWorkshopCoinCutting(
            String machine,
            CallbackInfoReturnable<List<?>> cir
    ) {
        if (!TyC2RuntimeData.config().disableIc2MetalFormerCoinRecipe()
                || !"metal_former_cutting".equals(machine)) {
            return;
        }
        List<?> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        List<Object> filtered = new ArrayList<>(original.size());
        boolean removedAny = false;
        for (Object recipe : original) {
            if (producesIc2Coin(recipe)) {
                removedAny = true;
            } else {
                filtered.add(recipe);
            }
        }
        if (removedAny) {
            cir.setReturnValue(List.copyOf(filtered));
        }
    }

    private static boolean producesIc2Coin(Object recipe) {
        if (recipe == null) {
            return false;
        }
        try {
            if (recipeOutputsMethod == null) {
                Method m = recipe.getClass().getDeclaredMethod("outputs");
                m.setAccessible(true);
                recipeOutputsMethod = m;
            }
            Object outputsObj = recipeOutputsMethod.invoke(recipe);
            if (outputsObj instanceof List<?> outputs) {
                for (Object st : outputs) {
                    if (st == null) {
                        continue;
                    }
                    if (stackIdMethod == null) {
                        Method idM = st.getClass().getDeclaredMethod("id");
                        idM.setAccessible(true);
                        stackIdMethod = idM;
                    }
                    Object id = stackIdMethod.invoke(st);
                    if (CurrencyDenomination.IC2_COIN_ID.equals(id)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return recipe.toString().contains(CurrencyDenomination.IC2_COIN_ID);
        }
        return false;
    }
}
