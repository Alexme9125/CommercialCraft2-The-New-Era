package com.tycooncraft.tyc2.village;

import java.util.Locale;
import java.util.Objects;

/**
 * The seven canonical Minecraft village architectural styles supported by TycoonCraft².
 */
public enum BiomeStyle {
    /** Temperate oak-and-stone village architecture. */
    PLAINS("plains"),
    /** Arid sandstone-and-terracotta village architecture. */
    DESERT("desert"),
    /** Warm acacia-and-red-terracotta village architecture. */
    SAVANNA("savanna"),
    /** Boreal spruce-and-mossy-cobblestone village architecture. */
    TAIGA("taiga"),
    /** Frost spruce, snow-block, and packed-ice village architecture. */
    SNOWY("snowy"),
    /** Lush jungle-timber and mossy-stone village architecture. */
    JUNGLE("jungle"),
    /** Marshland dark-oak, spruce, and mud-brick village architecture. */
    SWAMP("swamp");

    private final String id;

    BiomeStyle(String id) {
        this.id = id;
    }

    /**
     * Returns the lowercase canonical identifier for this biome style.
     *
     * @return biome style ID (e.g. {@code "plains"})
     */
    public String id() {
        return id;
    }

    /**
     * Returns the canonical {@link BiomePalette} for this biome style.
     *
     * @return architectural block palette
     */
    public BiomePalette palette() {
        return BiomePalette.forStyle(this);
    }

    /**
     * Maps a Minecraft biome registry ID (e.g. {@code "minecraft:desert"} or modded biome IDs)
     * to the closest {@link BiomeStyle}.
     *
     * @param biomeId namespaced or plain biome identifier (may be null)
     * @return matching {@link BiomeStyle}, defaulting to {@link #PLAINS}
     */
    public static BiomeStyle fromBiomeId(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return PLAINS;
        }
        String normalized = biomeId.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;

        if (path.contains("snow")
                || path.contains("ice")
                || path.contains("frozen")
                || path.contains("glacier")
                || path.contains("tundra")
                || path.equals("grove")
                || path.equals("jagged_peaks")) {
            return SNOWY;
        }
        if (path.contains("desert")
                || path.contains("badlands")
                || path.contains("mesa")
                || path.contains("dune")) {
            return DESERT;
        }
        if (path.contains("savanna")) {
            return SAVANNA;
        }
        if (path.contains("jungle")
                || path.contains("bamboo")
                || path.contains("rainforest")) {
            return JUNGLE;
        }
        if (path.contains("swamp")
                || path.contains("mangrove")
                || path.contains("marsh")
                || path.contains("bog")
                || path.contains("bayou")) {
            return SWAMP;
        }
        if (path.contains("taiga")
                || path.contains("spruce")
                || path.contains("pine")
                || path.contains("boreal")
                || path.contains("redwood")
                || path.contains("windswept")) {
            return TAIGA;
        }
        return PLAINS;
    }

    /**
     * Parses a {@link BiomeStyle} by enum name or lowercase ID, returning {@code fallback} if unrecognized.
     *
     * @param name style name or ID (may be null)
     * @param fallback default style when unrecognized
     * @return parsed style or fallback
     */
    public static BiomeStyle fromNameOrDefault(String name, BiomeStyle fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (BiomeStyle style : values()) {
            if (style.id.equals(normalized) || style.name().equalsIgnoreCase(normalized)) {
                return style;
            }
        }
        return fallback;
    }
}
