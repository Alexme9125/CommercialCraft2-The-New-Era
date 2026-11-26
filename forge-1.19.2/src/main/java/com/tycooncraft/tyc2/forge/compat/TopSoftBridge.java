package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-reflection optional The One Probe ({@code theoneprobe}) IMC bridge displaying live
 * Cooperation Hub, Minting Machine, ATM, and Village Mayor HUD overlays with zero compile
 * dependency on TOP.
 */
public final class TopSoftBridge {

    public static final String MOD_ID = "theoneprobe";
    private static final Logger LOGGER = LoggerFactory.getLogger("tyc2");

    private TopSoftBridge() {
    }

    /** Enqueues The One Probe IMC provider registration when {@code theoneprobe} is loaded. */
    public static void enqueueImc() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        InterModComms.sendTo(MOD_ID, "getTheOneProbe", () -> (Function<Object, Void>) TopSoftBridge::registerProviders);
    }

    private static Void registerProviders(Object probeObj) {
        if (probeObj == null) {
            return null;
        }
        try {
            ClassLoader cl = probeObj.getClass().getClassLoader();
            Class<?> blockProviderIfc = Class.forName("mcjty.theoneprobe.api.IProbeInfoProvider", false, cl);
            Class<?> entityProviderIfc = Class.forName("mcjty.theoneprobe.api.IProbeInfoEntityProvider", false, cl);

            Object blockProxy = Proxy.newProxyInstance(cl, new Class<?>[]{blockProviderIfc}, (proxy, method, args) -> {
                String name = method.getName();
                if ("getID".equals(name)) {
                    var rl = TyC2Ids.id("top_blocks");
                    return method.getReturnType() == String.class ? rl.toString() : rl;
                }
                if ("addProbeInfo".equals(name) && args != null && args.length >= 6) {
                    Object probeInfo = args[1];
                    Level level = (Level) args[3];
                    Object hitData = args[5];
                    BlockPos pos = (BlockPos) hitData.getClass().getMethod("getPos").invoke(hitData);
                    emitLines(probeInfo, JadeSoftBridge.buildBlockHudLines(level, pos));
                }
                return null;
            });

            Object entityProxy = Proxy.newProxyInstance(cl, new Class<?>[]{entityProviderIfc}, (proxy, method, args) -> {
                String name = method.getName();
                if ("getID".equals(name)) {
                    var rl = TyC2Ids.id("top_entities");
                    return method.getReturnType() == String.class ? rl.toString() : rl;
                }
                if ("addProbeEntityInfo".equals(name) && args != null && args.length >= 5) {
                    Object probeInfo = args[1];
                    Level level = (Level) args[3];
                    Entity entity = (Entity) args[4];
                    emitLines(probeInfo, JadeSoftBridge.buildEntityHudLines(level, entity));
                }
                return null;
            });

            Method regBlock = probeObj.getClass().getMethod("registerProvider", blockProviderIfc);
            regBlock.invoke(probeObj, blockProxy);
            Method regEntity = probeObj.getClass().getMethod("registerEntityProvider", entityProviderIfc);
            regEntity.invoke(probeObj, entityProxy);
        } catch (Throwable t) {
            LOGGER.debug("Skipping optional TOP registration: {}", t.getMessage());
        }
        return null;
    }

    private static void emitLines(Object probeInfo, List<Component> lines) {
        if (probeInfo == null || lines.isEmpty()) {
            return;
        }
        try {
            Method mcText = probeInfo.getClass().getMethod("mcText", Component.class);
            for (Component line : lines) {
                mcText.invoke(probeInfo, line);
            }
        } catch (Throwable ignored) {
            // Graceful fallback
        }
    }
}
