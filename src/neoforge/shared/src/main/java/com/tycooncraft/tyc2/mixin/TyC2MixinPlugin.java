package com.tycooncraft.tyc2.mixin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Conditional Mixin configuration plugin ensuring TyC2 boots cleanly with ZERO hard dependency
 * on IndustrialCraft 2 ({@code ic2}). All {@code Ic2*Mixin} classes are only applied when
 * {@code ic2} is actually present on the runtime classpath / loading mod list.
 */
public final class TyC2MixinPlugin implements IMixinConfigPlugin {

    private Boolean ic2Present;

    @Override
    public void onLoad(String mixinPackage) {
        this.ic2Present = detectIc2();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.contains(".Ic2")) {
            if (ic2Present == null) {
                ic2Present = detectIc2();
            }
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

    private static boolean detectIc2() {
        // 1. Probe NeoForge FMLLoader reflectively (works identically across 1.21.1 and 26.x)
        try {
            Class<?> fml = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Object loadingList = null;
            try {
                Method staticGetter = fml.getMethod("getLoadingModList");
                loadingList = staticGetter.invoke(null);
            } catch (NoSuchMethodException ignored) {
                try {
                    Object current = fml.getMethod("getCurrent").invoke(null);
                    if (current != null) {
                        loadingList = current.getClass().getMethod("getLoadingModList").invoke(current);
                    }
                } catch (Throwable ignoredInner) {
                    // Fall through to classpath probe
                }
            }
            if (loadingList != null) {
                Method getModFile = loadingList.getClass().getMethod("getModFileById", String.class);
                if (getModFile.invoke(loadingList, "ic2") != null) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to resource probe
        }

        // 2. Classpath resource probe (does NOT trigger classloading of IC2 classes)
        try {
            ClassLoader own = TyC2MixinPlugin.class.getClassLoader();
            if (own != null && own.getResource("com/industrialage/ia2/gameplay/CableBlock.class") != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Ignore
        }
        try {
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            if (ctx != null && ctx.getResource("com/industrialage/ia2/gameplay/CableBlock.class") != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Ignore
        }
        return false;
    }
}
