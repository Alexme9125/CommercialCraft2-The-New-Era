package com.tycooncraft.tyc2.village;

import com.tycooncraft.tyc2.core.TyC2Config;

import java.util.Locale;
import java.util.Objects;

/**
 * Building scale tiers available for player-funded village aid construction workplaces.
 */
public enum WorkplaceSize {
    /** Compact 7x6x7 single-workstation workplace unlocked at Village Level 1. */
    SMALL(1, 1, 1.0, 3_600, 7, 6, 7, "small"),
    /** Spacious 9x7x9 three-workstation workshop unlocked at Village Level 3. */
    LARGE(3, 3, 2.2, 6_000, 9, 7, 9, "large");

    private final int workstationCount;
    private final int defaultMinVillageLevel;
    private final double defaultCostMultiplier;
    private final int defaultGatherTicks;
    private final int width;
    private final int height;
    private final int depth;
    private final String id;

    WorkplaceSize(
            int workstationCount,
            int defaultMinVillageLevel,
            double defaultCostMultiplier,
            int defaultGatherTicks,
            int width,
            int height,
            int depth,
            String id) {
        this.workstationCount = workstationCount;
        this.defaultMinVillageLevel = defaultMinVillageLevel;
        this.defaultCostMultiplier = defaultCostMultiplier;
        this.defaultGatherTicks = defaultGatherTicks;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.id = id;
    }

    /**
     * Returns the number of profession workstation blocks placed inside this workplace.
     *
     * @return workstation count ({@code 1} for SMALL, {@code 3} for LARGE)
     */
    public int workstationCount() {
        return workstationCount;
    }

    /**
     * Returns the default minimum village level required to commission this size.
     *
     * @return minimum village level ({@code 1} for SMALL, {@code 3} for LARGE)
     */
    public int defaultMinVillageLevel() {
        return defaultMinVillageLevel;
    }

    /**
     * Returns the default cost multiplier relative to {@code baseCostSmallIc}.
     *
     * @return cost multiplier ({@code 1.0} for SMALL, {@code 2.2} for LARGE)
     */
    public double defaultCostMultiplier() {
        return defaultCostMultiplier;
    }

    /**
     * Returns the default base material-gathering duration in server ticks.
     *
     * @return default gather ticks ({@code 3600} for SMALL, {@code 6000} for LARGE)
     */
    public int defaultGatherTicks() {
        return defaultGatherTicks;
    }

    /**
     * Returns the configured base material-gathering duration in ticks from {@code config}.
     *
     * @param config active configuration (null falls back to defaults)
     * @return base gather ticks
     */
    public int gatherTicks(TyC2Config config) {
        TyC2Config effective = config == null ? TyC2Config.defaults() : config;
        return this == LARGE ? effective.aidGatherTicksLarge() : effective.aidGatherTicksSmall();
    }

    /**
     * Returns the blueprint X width in blocks.
     *
     * @return width ({@code 7} or {@code 9})
     */
    public int width() {
        return width;
    }

    /**
     * Returns the blueprint Y height in blocks.
     *
     * @return height ({@code 6} or {@code 7})
     */
    public int height() {
        return height;
    }

    /**
     * Returns the blueprint Z depth in blocks.
     *
     * @return depth ({@code 7} or {@code 9})
     */
    public int depth() {
        return depth;
    }

    /**
     * Returns the lowercase canonical identifier ({@code "small"} or {@code "large"}).
     *
     * @return size ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the corresponding {@link ProjectType} used by {@link ConstructionJob}.
     *
     * @return {@link ProjectType#AID_WORKPLACE_SMALL} or {@link ProjectType#AID_WORKPLACE_LARGE}
     */
    public ProjectType projectType() {
        return this == LARGE ? ProjectType.AID_WORKPLACE_LARGE : ProjectType.AID_WORKPLACE_SMALL;
    }

    /**
     * Parses a {@link WorkplaceSize} by enum name or lowercase ID, returning {@code fallback} if unrecognized.
     *
     * @param name size string (may be null)
     * @param fallback fallback value
     * @return parsed size or fallback
     */
    public static WorkplaceSize fromNameOrDefault(String name, WorkplaceSize fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String norm = name.trim().toLowerCase(Locale.ROOT);
        for (WorkplaceSize size : values()) {
            if (size.id.equals(norm) || size.name().equalsIgnoreCase(norm)) {
                return size;
            }
        }
        return fallback;
    }
}
