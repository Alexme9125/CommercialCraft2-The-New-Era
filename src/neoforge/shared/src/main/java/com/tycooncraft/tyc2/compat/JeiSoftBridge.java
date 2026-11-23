package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import java.util.List;
import net.neoforged.fml.ModList;

/**
 * Zero-hard-dependency helper exposing active Industrial Minting recipes for JEI ({@code jei})
 * or recipe-viewer addons without importing JEI classes in core mod bytecode.
 */
public final class JeiSoftBridge {

    private JeiSoftBridge() {
    }

    /** Returns {@code true} if JEI is loaded at runtime. */
    public static boolean isJeiLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("jei");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns all currently active Industrial Minting Machine recipes filtered by installed mods. */
    public static List<MintRecipe> activeMintRecipes() {
        return TyC2DataLoader.mintRecipes().activeRecipes(modId ->
                ModList.get() != null && ModList.get().isLoaded(modId)
        );
    }

    /** Returns all currently active Primitive Minting Machine recipes ({@code primitiveAllowed == true}). */
    public static List<MintRecipe> activePrimitiveMintRecipes() {
        return TyC2DataLoader.mintRecipes().getPrimitiveRecipes(modId ->
                ModList.get() != null && ModList.get().isLoaded(modId)
        );
    }
}
