package com.tycooncraft.tyc2.forge.mixin;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Optional Mixin gate ensuring {@code Ic2*Mixin} classes are NEVER applied or loaded
 * when IndustrialCraft² ({@code ic2}) is absent, guaranteeing pure-Vanilla standalone boot.
 */
public final class TyC2ForgeMixinPlugin implements IMixinConfigPlugin {

    private boolean ic2Present = false;

    @Override
    public void onLoad(String mixinPackage) {
        this.ic2Present = detectIc2Present();
    }

    private boolean detectIc2Present() {
        try {
            if (FMLLoader.getLoadingModList() != null
                    && FMLLoader.getLoadingModList().getModFileById("mhera") != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to resource check
        }
        try {
            ClassLoader cl = TyC2ForgeMixinPlugin.class.getClassLoader();
            return cl != null && cl.getResource("com/industrialage/ia2/IndustrialAge2.class") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && (mixinClassName.contains(".Ic2") || targetClassName.startsWith("com.industrialage."))) {
            return ic2Present;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
