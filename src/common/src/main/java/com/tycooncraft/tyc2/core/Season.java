package com.tycooncraft.tyc2.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Agricultural season state shared between soft season bridges
 * (Serene Seasons / Ecliptic Seasons) and the economy/demand engine.
 *
 * <p>When no season mod is installed, {@link #NONE} is used and all crops operate
 * with year-round neutral availability and pricing.
 */
public enum Season {
    NONE("none"),
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    private static final Set<Season> ALL_FOUR = Collections.unmodifiableSet(
            EnumSet.of(SPRING, SUMMER, AUTUMN, WINTER)
    );

    private final String key;

    Season(String key) {
        this.key = key;
    }

    /**
     * Returns the lowercase translation/serialization key.
     *
     * @return season key
     */
    public String key() {
        return key;
    }

    /**
     * Returns {@code true} when an actual calendar season is active (not {@link #NONE}).
     *
     * @return {@code true} if active season
     */
    public boolean isActive() {
        return this != NONE;
    }

    /**
     * Returns an unmodifiable set containing all four calendar seasons.
     *
     * @return set of {@code SPRING, SUMMER, AUTUMN, WINTER}
     */
    public static Set<Season> allFourSeasons() {
        return ALL_FOUR;
    }

    /**
     * Parses a season name (case-insensitive), handling sub-season strings such as
     * {@code "EARLY_SPRING"}, {@code "MID_AUTUMN"}, {@code "FALL"}, or {@code "LATE_WINTER"}.
     *
     * @param raw season string (may be {@code null})
     * @return matched season, or {@link #NONE} if unrecognized
     */
    public static Season parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("NONE") || upper.equals("ALL")) {
            return NONE;
        }
        if (upper.contains("SPRING")) {
            return SPRING;
        }
        if (upper.contains("SUMMER")) {
            return SUMMER;
        }
        if (upper.contains("AUTUMN") || upper.contains("FALL")) {
            return AUTUMN;
        }
        if (upper.contains("WINTER")) {
            return WINTER;
        }
        return NONE;
    }
}
