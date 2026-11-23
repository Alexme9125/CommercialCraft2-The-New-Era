package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.api.TyC2RegistryApi;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Zero-hard-dependency reflection bridge for Serene Seasons ({@code sereneseasons}) and
 * Ecliptic Seasons ({@code eclipticseasons}), plus custom providers registered via
 * {@link TyC2RegistryApi}. Gracefully degrades to {@link Season#NONE} when neither mod is present.
 */
public final class SeasonSoftBridge {

    private static Boolean sereneLoaded;
    private static Boolean eclipticLoaded;
    private static boolean sereneProbeFailed;
    private static boolean eclipticProbeFailed;

    private SeasonSoftBridge() {
    }

    /** Resolves the active {@link Season} for {@code level}, returning {@link Season#NONE} if unknown. */
    public static Season currentSeason(Level level) {
        if (level == null) {
            return Season.NONE;
        }

        // 1. Custom third-party provider via TyC2RegistryApi
        Season apiSeason = TyC2RegistryApi.resolveSeason(TyC2Native.dimensionId(level));
        if (apiSeason != null && apiSeason != Season.NONE) {
            return apiSeason;
        }

        // 2. Serene Seasons reflective probe
        if (isSereneLoaded() && !sereneProbeFailed) {
            Season s = probeSereneSeasons(level);
            if (s != Season.NONE) {
                return s;
            }
        }

        // 3. Ecliptic Seasons reflective probe
        if (isEclipticLoaded() && !eclipticProbeFailed) {
            Season s = probeEclipticSeasons(level);
            if (s != Season.NONE) {
                return s;
            }
        }

        return Season.NONE;
    }

    private static boolean isSereneLoaded() {
        if (sereneLoaded == null) {
            try {
                sereneLoaded = ModList.get() != null && ModList.get().isLoaded("sereneseasons");
            } catch (Throwable ignored) {
                sereneLoaded = false;
            }
        }
        return sereneLoaded;
    }

    private static boolean isEclipticLoaded() {
        if (eclipticLoaded == null) {
            try {
                eclipticLoaded = ModList.get() != null && ModList.get().isLoaded("eclipticseasons");
            } catch (Throwable ignored) {
                eclipticLoaded = false;
            }
        }
        return eclipticLoaded;
    }

    private static Season probeSereneSeasons(Level level) {
        try {
            Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
            Method getState = helper.getMethod("getSeasonState", Level.class);
            Object state = getState.invoke(null, level);
            if (state == null) {
                return Season.NONE;
            }
            Object seasonEnum = state.getClass().getMethod("getSeason").invoke(state);
            return seasonEnum == null ? Season.NONE : Season.parse(seasonEnum.toString());
        } catch (Throwable ignored) {
            sereneProbeFailed = true;
            return Season.NONE;
        }
    }

    private static Season probeEclipticSeasons(Level level) {
        try {
            Class<?> api = Class.forName("com.teamtea.eclipticseasons.api.EclipticSeasonsApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return Season.NONE;
            }
            Object term = instance.getClass().getMethod("getSolarTerm", Level.class).invoke(instance, level);
            if (term == null) {
                return Season.NONE;
            }
            Object seasonEnum = term.getClass().getMethod("getSeason").invoke(term);
            return seasonEnum == null ? Season.NONE : Season.parse(seasonEnum.toString());
        } catch (Throwable ignored) {
            eclipticProbeFailed = true;
            return Season.NONE;
        }
    }
}
