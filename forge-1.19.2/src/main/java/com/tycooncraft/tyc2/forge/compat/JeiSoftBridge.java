package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Zero-hard-dependency JEI ({@code jei}) soft integration helper exposing both
 * {@code tyc2:primitive_minting} (burnable-fuel Vanilla 1x-100x recipes) and
 * {@code tyc2:minting} (full pure-electric Industrial Minting Machine recipes)
 * when JEI is present at runtime.
 */
public final class JeiSoftBridge {

    public static final String MOD_ID = "jei";
    public static final ResourceLocation CATEGORY_PRIMITIVE_MINTING = TyC2Ids.id("primitive_minting");
    public static final ResourceLocation CATEGORY_INDUSTRIAL_MINTING = TyC2Ids.id("minting");

    private JeiSoftBridge() {
    }

    /** Returns {@code true} when JEI is loaded at runtime. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Returns all active {@link MintRecipe} entries eligible for {@code tyc2:primitive_mint_machine}. */
    public static List<MintRecipe> getPrimitiveMintingRecipes() {
        return TyC2RuntimeData.mintRecipes().getPrimitiveRecipes(ModList.get()::isLoaded);
    }

    /** Returns all active {@link MintRecipe} entries for {@code tyc2:mint_machine} filtered by loaded mods. */
    public static List<MintRecipe> getActiveMintingRecipes() {
        return TyC2RuntimeData.mintRecipes().activeRecipes(ModList.get()::isLoaded);
    }
}
