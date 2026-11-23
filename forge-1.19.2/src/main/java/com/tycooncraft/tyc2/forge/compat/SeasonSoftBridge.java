package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.api.TyC2RegistryApi;
import com.tycooncraft.tyc2.core.Season;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * Pure-reflection optional season bridge supporting Serene Seasons ({@code sereneseasons}),
 * Ecliptic Seasons ({@code eclipticseasons}), and custom {@link TyC2RegistryApi} providers,
 * with zero compile-time mod dependencies.
 */
public final class SeasonSoftBridge {

    private static volatile boolean initialized = false;
    private static volatile boolean sereneAvailable = false;
    private static volatile Method sereneGetState = null;
    private static volatile Method sereneGetSeason = null;

    private static volatile boolean eclipticAvailable = false;
    private static volatile Method eclipticGetInstance = null;
    private static volatile Method eclipticGetSolarTerm = null;
    private static volatile Method eclipticTermGetSeason = null;

    private SeasonSoftBridge() {
    }

    private static void initIfNeeded() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (ModList.get().isLoaded("sereneseasons")) {
            try {
                Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
                sereneGetState = helper.getMethod("getSeasonState", Level.class);
                sereneAvailable = true;
            } catch (Throwable ignored) {
                sereneAvailable = false;
            }
        }
        if (ModList.get().isLoaded("eclipticseasons")) {
            try {
                Class<?> api = Class.forName("com.teamtea.eclipticseasons.api.EclipticSeasonsApi");
                eclipticGetInstance = api.getMethod("getInstance");
                eclipticGetSolarTerm = api.getMethod("getSolarTerm", Level.class);
                eclipticAvailable = true;
            } catch (Throwable ignored) {
                eclipticAvailable = false;
            }
        }
    }

    /** Resolves the current agricultural {@link Season} for {@code level}, or {@link Season#NONE} if none active. */
    public static Season currentSeason(Level level) {
        if (level == null) {
            return Season.NONE;
        }
        initIfNeeded();

        // 1. Third-party API providers take priority if registered
        Season apiSeason = TyC2RegistryApi.resolveSeason(level.dimension().location().toString());
        if (apiSeason != null && apiSeason.isActive()) {
            return apiSeason;
        }

        // 2. Serene Seasons reflection
        if (sereneAvailable && sereneGetState != null) {
            try {
                Object state = sereneGetState.invoke(null, level);
                if (state != null) {
                    if (sereneGetSeason == null) {
                        sereneGetSeason = state.getClass().getMethod("getSeason");
                    }
                    Object seasonObj = sereneGetSeason.invoke(state);
                    if (seasonObj != null) {
                        Season parsed = Season.parse(seasonObj.toString());
                        if (parsed.isActive()) {
                            return parsed;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Fall through safely
            }
        }

        // 3. Ecliptic Seasons reflection
        if (eclipticAvailable && eclipticGetInstance != null && eclipticGetSolarTerm != null) {
            try {
                Object api = eclipticGetInstance.invoke(null);
                if (api != null) {
                    Object term = eclipticGetSolarTerm.invoke(api, level);
                    if (term != null) {
                        if (eclipticTermGetSeason == null) {
                            eclipticTermGetSeason = term.getClass().getMethod("getSeason");
                        }
                        Object seasonObj = eclipticTermGetSeason.invoke(term);
                        if (seasonObj != null) {
                            Season parsed = Season.parse(seasonObj.toString());
                            if (parsed.isActive()) {
                                return parsed;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Fall through safely
            }
        }

        return Season.NONE;
    }

    /** Returns {@code true} if any supported season mod is loaded. */
    public static boolean isAnySeasonModLoaded() {
        return ModList.get().isLoaded("sereneseasons") || ModList.get().isLoaded("eclipticseasons");
    }
}
