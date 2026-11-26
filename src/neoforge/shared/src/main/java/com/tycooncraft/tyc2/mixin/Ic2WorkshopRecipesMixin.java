package com.tycooncraft.tyc2.mixin;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters IC2's {@code WorkshopRecipes} Metal Former cutting recipe whose OUTPUT produces
 * {@code ic2:coin} whenever {@code disableIc2MetalFormerCoinRecipe} is enabled, keeping
 * JEI/machine views consistent with TyC2's Minting Machine currency rules without accidentally
 * matching recipes that merely take {@code ic2:coin} as an input.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopRecipes", remap = false)
public abstract class Ic2WorkshopRecipesMixin {

    private static volatile Method recipeOutputsMethod;
    private static volatile Method stackIdMethod;

    @Inject(method = "forType", at = @At("RETURN"), cancellable = true)
    private static void tyc2$filterWorkshopCoinRecipes(
            String type,
            CallbackInfoReturnable<List<?>> cir
    ) {
        if (!TyC2DataLoader.config().disableIc2MetalFormerCoinRecipe()
                || !"metal_former_cutting".equals(type)) {
            return;
        }
        List<?> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        boolean removedAny = false;
        List<Object> filtered = new ArrayList<>(original.size());
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
                return false;
            }
        } catch (Throwable ignored) {
            return recipe.toString().contains("outputs=[Stack[id=" + CurrencyDenomination.IC2_COIN_ID);
        }
        return false;
    }
}
