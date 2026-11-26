package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-reflection soft integration for The One Probe ({@code theoneprobe}) via IMC
 * ({@code "getTheOneProbe"}). Renders localized TyC2 HUD lines for Cooperation Hub, Minting
 * Machine, Industrial ATM, and Village Mayors with zero compile-time dependency on TOP.
 */
public final class TopSoftBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopSoftBridge.class);

    private TopSoftBridge() {
    }

    /** Hooks IMC enqueue on the mod bus when {@code theoneprobe} is loaded. */
    public static void install(IEventBus modBus) {
        modBus.addListener(TopSoftBridge::onInterModEnqueue);
    }

    private static void onInterModEnqueue(InterModEnqueueEvent event) {
        if (ModList.get() == null || !ModList.get().isLoaded("theoneprobe")) {
            return;
        }
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", () -> (Function<Object, Void>) TopSoftBridge::registerProviders);
    }

    private static Void registerProviders(Object theOneProbe) {
        if (theOneProbe == null) {
            return null;
        }
        try {
            ClassLoader cl = theOneProbe.getClass().getClassLoader();
            Class<?> blockProviderIface = Class.forName("mcjty.theoneprobe.api.IProbeInfoProvider", false, cl);
            Object blockProvider = Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{blockProviderIface},
                    (proxy, method, args) -> {
                        if ("getID".equals(method.getName())) {
                            String rawId = TyC2Constants.MOD_ID + ":hud_provider";
                            return method.getReturnType() == String.class ? rawId : TyC2Native.parseId(rawId);
                        }
                        if ("addProbeInfo".equals(method.getName()) && args != null && args.length >= 6) {
                            // addProbeInfo(ProbeMode, IProbeInfo, Player, Level, BlockState, IProbeHitData)
                            Object probeInfo = args[1];
                            if (args[3] instanceof ServerLevel serverLevel && args[5] != null) {
                                Object posObj = args[5].getClass().getMethod("getPos").invoke(args[5]);
                                if (posObj instanceof BlockPos pos) {
                                    List<Component> lines = JadeSoftBridge.buildBlockHudLines(serverLevel, pos);
                                    appendLines(probeInfo, lines);
                                }
                            }
                        }
                        return null;
                    }
            );
            theOneProbe.getClass().getMethod("registerProvider", blockProviderIface).invoke(theOneProbe, blockProvider);

            Class<?> entityProviderIface = Class.forName("mcjty.theoneprobe.api.IProbeInfoEntityProvider", false, cl);
            Object entityProvider = Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{entityProviderIface},
                    (proxy, method, args) -> {
                        if ("getID".equals(method.getName())) {
                            String rawId = TyC2Constants.MOD_ID + ":mayor_provider";
                            return method.getReturnType() == String.class ? rawId : TyC2Native.parseId(rawId);
                        }
                        if ("addProbeEntityInfo".equals(method.getName()) && args != null && args.length >= 5) {
                            // addProbeEntityInfo(ProbeMode, IProbeInfo, Player, Level, Entity, IProbeHitEntityData)
                            Object probeInfo = args[1];
                            if (args[3] instanceof ServerLevel serverLevel && args[4] instanceof Entity entity) {
                                List<Component> lines = JadeSoftBridge.buildEntityHudLines(serverLevel, entity);
                                appendLines(probeInfo, lines);
                            }
                        }
                        return null;
                    }
            );
            theOneProbe.getClass().getMethod("registerEntityProvider", entityProviderIface).invoke(theOneProbe, entityProvider);
        } catch (Throwable ex) {
            LOGGER.debug("Skipping optional The One Probe integration: {}", ex.getMessage());
        }
        return null;
    }

    private static void appendLines(Object probeInfo, List<Component> lines) {
        if (probeInfo == null || lines == null || lines.isEmpty()) {
            return;
        }
        try {
            Method mcText = probeInfo.getClass().getMethod("mcText", Component.class);
            for (Component line : lines) {
                mcText.invoke(probeInfo, line);
            }
        } catch (Throwable ignored) {
            // Ignore incompatible TOP API version
        }
    }
}
