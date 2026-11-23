package com.tycooncraft.tyc2.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical 1-to-10 village level progression table matching TycoonCraft² Design Section 6.1.
 * Supports both the default built-in curve and custom overrides loaded from datapacks/config.
 */
public final class VillageLevelTable {

    /** Minimum valid village level. */
    public static final int MIN_LEVEL = 1;

    /** Maximum valid village level. */
    public static final int MAX_LEVEL = 10;

    /** Hard cap on natural villager population growth across levels. */
    public static final int ABSOLUTE_MAX_POPULATION_CAP = 40;

    /**
     * Immutable specification for a single village level.
     *
     * @param level level index ({@code 1..10})
     * @param minPopulation minimum living villager population required
     * @param minTradeVolume minimum cumulative trade volume (IC) required
     * @param minVillageBalance minimum village account balance (IC) required
     * @param upgradeCostSink municipal capital expenditure burned from villageBalance upon reaching this level
     * @param sellOrderCount active village sell order slots at this level
     * @param buyOrderCount active village buy order slots at this level
     * @param qtyScale base quantity multiplier for generated orders
     * @param maxPopulationCap maximum natural villager population cap enabled at this level
     * @param expansionRadiusMax maximum concentric expansion radius (blocks) at this level
     */
    public record LevelSpec(
            int level,
            int minPopulation,
            long minTradeVolume,
            long minVillageBalance,
            long upgradeCostSink,
            int sellOrderCount,
            int buyOrderCount,
            double qtyScale,
            int maxPopulationCap,
            int expansionRadiusMax) {

        /**
         * Validates level specification invariants.
         */
        public LevelSpec {
            if (level < MIN_LEVEL || level > MAX_LEVEL) {
                throw new IllegalArgumentException("level must be in 1..10, got " + level);
            }
            if (minPopulation < 0 || minTradeVolume < 0 || minVillageBalance < 0 || upgradeCostSink < 0) {
                throw new IllegalArgumentException("Thresholds and sink cost must be >= 0");
            }
            if (sellOrderCount < 0 || buyOrderCount < 0) {
                throw new IllegalArgumentException("Order counts must be >= 0");
            }
            if (qtyScale <= 0.0) {
                throw new IllegalArgumentException("qtyScale must be > 0");
            }
            if (maxPopulationCap < minPopulation) {
                throw new IllegalArgumentException("maxPopulationCap must be >= minPopulation");
            }
            if (expansionRadiusMax < 10) {
                throw new IllegalArgumentException("expansionRadiusMax must be >= 10");
            }
        }
    }

    /**
     * Diagnostic breakdown of upgrade eligibility from {@code currentLevel} to {@code targetLevel}.
     *
     * @param eligible {@code true} when every requirement is satisfied and not yet at max level
     * @param currentLevel current village level
     * @param targetSpec next level specification (null if already at {@link #MAX_LEVEL})
     * @param populationMet whether population meets {@code targetSpec.minPopulation()}
     * @param tradeVolumeMet whether cumulative trade volume meets {@code targetSpec.minTradeVolume()}
     * @param balanceMet whether village balance meets {@code targetSpec.minVillageBalance()} and sink cost
     */
    public record UpgradeStatus(
            boolean eligible,
            int currentLevel,
            LevelSpec targetSpec,
            boolean populationMet,
            boolean tradeVolumeMet,
            boolean balanceMet) {
    }

    /** Built-in default progression table from Design Section 6.1. */
    public static final VillageLevelTable DEFAULT = new VillageLevelTable(List.of(
            new LevelSpec(1, 2, 0L, 0L, 0L, 3, 2, 1.0, 8, 24),
            new LevelSpec(2, 4, 400L, 150L, 100L, 4, 3, 1.3, 12, 30),
            new LevelSpec(3, 6, 1_200L, 400L, 250L, 4, 4, 1.6, 16, 36),
            new LevelSpec(4, 9, 3_000L, 900L, 550L, 5, 4, 2.0, 20, 42),
            new LevelSpec(5, 12, 7_000L, 1_800L, 1_100L, 6, 5, 2.5, 24, 48),
            new LevelSpec(6, 16, 15_000L, 3_500L, 2_000L, 6, 6, 3.2, 28, 54),
            new LevelSpec(7, 20, 32_000L, 7_000L, 4_000L, 7, 6, 4.0, 32, 60),
            new LevelSpec(8, 25, 65_000L, 14_000L, 7_500L, 8, 7, 5.0, 36, 64),
            new LevelSpec(9, 30, 130_000L, 28_000L, 15_000L, 9, 8, 6.5, 40, 68),
            new LevelSpec(10, 36, 250_000L, 55_000L, 30_000L, 10, 9, 8.0, 40, 72)));

    private final List<LevelSpec> levels;

    private VillageLevelTable(List<LevelSpec> specs) {
        Objects.requireNonNull(specs, "specs");
        if (specs.size() != MAX_LEVEL) {
            throw new IllegalArgumentException("Expected exactly " + MAX_LEVEL + " level specs, got " + specs.size());
        }
        LevelSpec[] ordered = new LevelSpec[MAX_LEVEL];
        for (LevelSpec spec : specs) {
            Objects.requireNonNull(spec, "spec");
            int idx = spec.level() - MIN_LEVEL;
            if (ordered[idx] != null) {
                throw new IllegalArgumentException("Duplicate level spec for level " + spec.level());
            }
            ordered[idx] = spec;
        }
        List<LevelSpec> copy = new ArrayList<>(MAX_LEVEL);
        Collections.addAll(copy, ordered);
        this.levels = Collections.unmodifiableList(copy);
    }

    /**
     * Creates a custom {@link VillageLevelTable} from 10 ordered or unordered level specifications.
     *
     * @param specs exactly 10 specifications covering levels 1..10
     * @return immutable level table instance
     */
    public static VillageLevelTable of(List<LevelSpec> specs) {
        return new VillageLevelTable(specs);
    }

    /**
     * Returns the {@link LevelSpec} for an exact level ({@code 1..10}) from the default table.
     *
     * @param level village level in {@code 1..10}
     * @return level specification
     */
    public static LevelSpec get(int level) {
        return DEFAULT.getSpec(level);
    }

    /**
     * Returns the {@link LevelSpec} clamped into {@code [1, 10]} from the default table.
     *
     * @param level arbitrary level value
     * @return clamped level specification
     */
    public static LevelSpec getClamped(int level) {
        return DEFAULT.getSpecClamped(level);
    }

    /**
     * Returns the next level specification after {@code currentLevel} from the default table,
     * or empty if {@code currentLevel >= MAX_LEVEL}.
     *
     * @param currentLevel current village level
     * @return optional next level spec
     */
    public static Optional<LevelSpec> nextLevel(int currentLevel) {
        return DEFAULT.nextSpec(currentLevel);
    }

    /**
     * Checks whether a village at {@code level} satisfies all upgrade thresholds for {@code level + 1}
     * using the default progression table.
     *
     * @param level current village level
     * @param population current living villager count
     * @param tradeVolume cumulative trade volume in IC
     * @param balance current village treasury balance in IC
     * @return {@code true} if eligible to upgrade
     */
    public static boolean canUpgrade(int level, int population, long tradeVolume, long balance) {
        return DEFAULT.isUpgradeEligible(level, population, tradeVolume, balance);
    }

    /**
     * Computes the target municipal treasury reserve ({@code 200 * level^1.8}) from Design Section 7.2.
     *
     * @param level village level ({@code 1..10})
     * @return target municipal reserve in IC
     */
    public static double targetReserve(int level) {
        int clamped = clampLevel(level);
        return 200.0 * Math.pow(clamped, 1.8);
    }

    /**
     * Computes the natural population cap formula {@code min(40, level * 4 + 4)}.
     *
     * @param level village level ({@code 1..10})
     * @return population cap
     */
    public static int defaultPopulationCap(int level) {
        int clamped = clampLevel(level);
        return Math.min(ABSOLUTE_MAX_POPULATION_CAP, clamped * 4 + 4);
    }

    /**
     * Returns all 10 level specifications in ascending level order.
     *
     * @return unmodifiable list of level specs
     */
    public List<LevelSpec> allLevels() {
        return levels;
    }

    /**
     * Returns the {@link LevelSpec} for an exact level ({@code 1..10}).
     *
     * @param level village level
     * @return level specification
     */
    public LevelSpec getSpec(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Village level out of range (1..10): " + level);
        }
        return levels.get(level - MIN_LEVEL);
    }

    /**
     * Returns the {@link LevelSpec} clamped to {@code [1, 10]}.
     *
     * @param level arbitrary level value
     * @return clamped level specification
     */
    public LevelSpec getSpecClamped(int level) {
        return levels.get(clampLevel(level) - MIN_LEVEL);
    }

    /**
     * Returns the next level specification after {@code currentLevel}, or empty at max level.
     *
     * @param currentLevel current village level
     * @return optional next level spec
     */
    public Optional<LevelSpec> nextSpec(int currentLevel) {
        if (currentLevel < MIN_LEVEL || currentLevel >= MAX_LEVEL) {
            return Optional.empty();
        }
        return Optional.of(levels.get(currentLevel));
    }

    /**
     * Checks whether a village meets population, cumulative trade volume, and balance requirements
     * for {@code currentLevel + 1}.
     *
     * @param currentLevel current village level
     * @param population living villager count
     * @param tradeVolume cumulative trade volume in IC
     * @param balance current village balance in IC
     * @return {@code true} if upgrade can proceed
     */
    public boolean isUpgradeEligible(int currentLevel, int population, long tradeVolume, long balance) {
        return evaluateUpgrade(currentLevel, population, tradeVolume, balance).eligible();
    }

    /**
     * Evaluates upgrade eligibility with a per-requirement breakdown suitable for UI display.
     *
     * @param currentLevel current village level
     * @param population living villager count
     * @param tradeVolume cumulative trade volume in IC
     * @param balance current village balance in IC
     * @return detailed upgrade status
     */
    public UpgradeStatus evaluateUpgrade(int currentLevel, int population, long tradeVolume, long balance) {
        Optional<LevelSpec> nextOpt = nextSpec(currentLevel);
        if (nextOpt.isEmpty()) {
            return new UpgradeStatus(false, currentLevel, null, false, false, false);
        }
        LevelSpec next = nextOpt.get();
        boolean popOk = population >= next.minPopulation();
        boolean volOk = tradeVolume >= next.minTradeVolume();
        boolean balOk = balance >= next.minVillageBalance() && balance >= next.upgradeCostSink();
        return new UpgradeStatus(popOk && volOk && balOk, currentLevel, next, popOk, volOk, balOk);
    }

    /**
     * Returns the capital cost sink required to upgrade from {@code currentLevel} to {@code currentLevel + 1},
     * or {@code 0} when already at max level.
     *
     * @param currentLevel current village level
     * @return capital sink cost in IC
     */
    public long upgradeCostForNext(int currentLevel) {
        return nextSpec(currentLevel).map(LevelSpec::upgradeCostSink).orElse(0L);
    }

    private static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }
}
