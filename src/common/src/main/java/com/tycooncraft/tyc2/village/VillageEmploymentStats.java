package com.tycooncraft.tyc2.village;

import com.tycooncraft.tyc2.core.TyC2Config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable per-village workforce and employment snapshot driving both:
 * <ul>
 *   <li>Low-unemployment trade bonuses ({@code unemploymentRate < 25%}): smoother faster demand restock
 *       ({@code 1.25x..1.50x}) and larger per-order trade quantities ({@code 1.25x..1.50x}, gentle
 *       {@code 1.10x..1.20x} on rare enchanted books and endgame goods).</li>
 *   <li>Aid construction background gathering speed ({@code 1.0x..2.0x} based on idle/unemployed adult count).</li>
 * </ul>
 *
 * <p>Accounting rules:
 * <ul>
 *   <li>Workforce denominator = living adult non-nitwit villagers (babies and nitwits excluded).</li>
 *   <li>The village Mayor counts as employed (public office) even if holding vanilla {@code minecraft:none}.</li>
 *   <li>Zero workforce defaults to {@code unemploymentRate() == 1.0} and bonus inactive.</li>
 * </ul>
 *
 * @param workforceCount total eligible living adult non-nitwit villagers
 * @param employedCount employed workforce count (holding a profession or serving as Mayor)
 * @param unemployedCount unemployed workforce count ({@code workforceCount - employedCount})
 * @param mayorCountedEmployed whether the living Mayor was included as employed in this scan
 * @param activeProfessionCounts map of {@code professionId -> active villager count}
 * @param lastScanTick server game tick when this snapshot was last scanned
 */
public record VillageEmploymentStats(
        int workforceCount,
        int employedCount,
        int unemployedCount,
        boolean mayorCountedEmployed,
        Map<String, Integer> activeProfessionCounts,
        long lastScanTick) {

    /** Default empty snapshot for newly created villages or zero-workforce settlements. */
    public static final VillageEmploymentStats EMPTY =
            new VillageEmploymentStats(0, 0, 0, false, Map.of(), 0L);

    /**
     * Pure-Java villager scan input used by platform adapters to compute {@link VillageEmploymentStats}
     * with identical accounting across Forge 1.19.2 and NeoForge 1.21.1 / 26.x.
     *
     * @param entityUuid villager entity UUID (nullable)
     * @param adult {@code true} if adult villager ({@code !isBaby()})
     * @param nitwit {@code true} if nitwit profession
     * @param professionId namespaced profession ID (e.g. {@code "minecraft:none"}, {@code "minecraft:farmer"})
     */
    public record VillagerSnapshot(
            UUID entityUuid,
            boolean adult,
            boolean nitwit,
            String professionId) {
        /**
         * Normalizes profession ID.
         */
        public VillagerSnapshot {
            professionId = professionId == null ? "minecraft:none" : professionId.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Enforces consistent counts and wraps {@code activeProfessionCounts} in an unmodifiable copy.
     */
    public VillageEmploymentStats {
        workforceCount = Math.max(0, workforceCount);
        employedCount = Math.max(0, Math.min(workforceCount, employedCount));
        unemployedCount = workforceCount - employedCount;
        Map<String, Integer> profCopy = new LinkedHashMap<>();
        if (activeProfessionCounts != null) {
            for (Map.Entry<String, Integer> e : activeProfessionCounts.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                int count = e.getValue() == null ? 0 : e.getValue();
                if (count > 0) {
                    profCopy.merge(e.getKey().trim().toLowerCase(Locale.ROOT), count, Integer::sum);
                }
            }
        }
        activeProfessionCounts = Collections.unmodifiableMap(profCopy);
        lastScanTick = Math.max(0L, lastScanTick);
    }

    /**
     * Computes a {@link VillageEmploymentStats} snapshot from living villager records around a village.
     *
     * @param villagers scanned living villagers within dynamic village scan radius
     * @param mayorEntityUuid UUID of the current living Mayor (nullable)
     * @param scanTick current server tick
     * @return computed employment statistics
     */
    public static VillageEmploymentStats computeFromSnapshots(
            Collection<VillagerSnapshot> villagers,
            UUID mayorEntityUuid,
            long scanTick) {
        if (villagers == null || villagers.isEmpty()) {
            return new VillageEmploymentStats(0, 0, 0, false, Map.of(), scanTick);
        }
        int workforce = 0;
        int employed = 0;
        boolean mayorCounted = false;
        Map<String, Integer> profCounts = new LinkedHashMap<>();

        for (VillagerSnapshot v : villagers) {
            if (v == null || !v.adult() || v.nitwit()) {
                continue;
            }
            String prof = v.professionId();
            if (isNitwitProfession(prof)) {
                continue;
            }
            workforce++;
            boolean isMayor = mayorEntityUuid != null && mayorEntityUuid.equals(v.entityUuid());
            boolean hasJob = !isUnemployedProfession(prof);
            if (isMayor) {
                employed++;
                mayorCounted = true;
                if (hasJob) {
                    profCounts.merge(prof, 1, Integer::sum);
                }
            } else if (hasJob) {
                employed++;
                profCounts.merge(prof, 1, Integer::sum);
            }
        }
        return new VillageEmploymentStats(
                workforce,
                employed,
                workforce - employed,
                mayorCounted,
                profCounts,
                scanTick);
    }

    /**
     * Checks whether a profession ID represents an unemployed villager ({@code minecraft:none}, {@code none}, or blank).
     *
     * @param professionId profession ID string
     * @return {@code true} if unemployed
     */
    public static boolean isUnemployedProfession(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return true;
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        return norm.equals("none") || norm.equals("minecraft:none") || isNitwitProfession(norm);
    }

    /**
     * Checks whether a profession ID represents a nitwit villager.
     *
     * @param professionId profession ID string
     * @return {@code true} if nitwit
     */
    public static boolean isNitwitProfession(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return false;
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        return norm.equals("nitwit") || norm.equals("minecraft:nitwit");
    }

    /**
     * Returns the village unemployment rate in {@code [0.0, 1.0]}.
     * Returns {@code 1.0} when {@code workforceCount <= 0}.
     *
     * @return unemployment rate
     */
    public double unemploymentRate() {
        return workforceCount <= 0 ? 1.0 : (double) unemployedCount / (double) workforceCount;
    }

    /**
     * Returns whether the low-unemployment trade bonus is currently active
     * ({@code workforceCount > 0 && unemploymentRate() < cfg.lowUnemploymentThreshold()}).
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return {@code true} if bonus is active
     */
    public boolean isLowUnemploymentBonusActive(TyC2Config cfg) {
        TyC2Config c = cfg == null ? TyC2Config.defaults() : cfg;
        return workforceCount > 0 && unemploymentRate() < c.lowUnemploymentThreshold();
    }

    /**
     * Returns the normalized smooth low-unemployment bonus intensity in {@code [0.0, 1.0]}:
     * {@code 0.0} when inactive (or {@code rate >= threshold}), ramping linearly to {@code 1.0} at 0% unemployment.
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return bonus intensity in {@code [0.0, 1.0]}
     */
    public double bonusIntensity(TyC2Config cfg) {
        TyC2Config c = cfg == null ? TyC2Config.defaults() : cfg;
        double threshold = c.lowUnemploymentThreshold();
        if (workforceCount <= 0 || threshold <= 0.0) {
            return 0.0;
        }
        double rate = unemploymentRate();
        if (rate >= threshold) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (threshold - rate) / threshold));
    }

    /**
     * Returns the demand restock speed multiplier ({@code 1.0} when inactive, smoothly scaling
     * across {@code [lowUnempRestockMinMult, lowUnempRestockMaxMult]} = {@code [1.25, 1.50]} when active).
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return restock speed multiplier (>= 1.0)
     */
    public double restockSpeedMultiplier(TyC2Config cfg) {
        TyC2Config c = cfg == null ? TyC2Config.defaults() : cfg;
        if (!isLowUnemploymentBonusActive(c)) {
            return 1.0;
        }
        double intensity = bonusIntensity(c);
        return c.lowUnempRestockMinMult()
                + (c.lowUnempRestockMaxMult() - c.lowUnempRestockMinMult()) * intensity;
    }

    /**
     * Computes the effective demand refresh period in ticks after applying {@link #restockSpeedMultiplier(TyC2Config)},
     * floored at 200 ticks.
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return effective demand refresh interval in ticks (>= 200)
     */
    public int effectiveDemandRefreshTicks(TyC2Config cfg) {
        TyC2Config c = cfg == null ? TyC2Config.defaults() : cfg;
        return Math.max(200, (int) Math.floor(c.demandRefreshTicks() / restockSpeedMultiplier(c)));
    }

    /**
     * Returns the trade order quantity multiplier for standard goods ({@code 1.0} when inactive,
     * smoothly scaling across {@code [lowUnempQtyMinMult, lowUnempQtyMaxMult]} = {@code [1.25, 1.50]} when active).
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return standard trade quantity multiplier (>= 1.0)
     */
    public double tradeQuantityMultiplier(TyC2Config cfg) {
        TyC2Config c = cfg == null ? TyC2Config.defaults() : cfg;
        if (!isLowUnemploymentBonusActive(c)) {
            return 1.0;
        }
        double intensity = bonusIntensity(c);
        return c.lowUnempQtyMinMult()
                + (c.lowUnempQtyMaxMult() - c.lowUnempQtyMinMult()) * intensity;
    }

    /**
     * Returns the moderated trade quantity multiplier for rare categories ({@code ENCHANTED_BOOK} / {@code ENDGAME}):
     * {@code 1.0 + (tradeQuantityMultiplier(cfg) - 1.0) * 0.4} (default {@code 1.10..1.20} when active).
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return rare trade quantity multiplier (>= 1.0)
     */
    public double rareTradeQuantityMultiplier(TyC2Config cfg) {
        return 1.0 + (tradeQuantityMultiplier(cfg) - 1.0) * 0.4;
    }

    /**
     * Computes the background aid-material gathering speed multiplier accelerated by unemployed adult villagers:
     * {@code 1.0 + min(aidMaxLaborSpeedBonus, unemployedCount * aidLaborSpeedPerUnemployed)}
     * (default {@code 1.0..2.0}, reaching full {@code 2.0x} at 8+ unemployed adults).
     *
     * @param cfg active configuration (null falls back to defaults)
     * @return gathering speed multiplier (>= 1.0)
     */
    public double gatherSpeedMultiplier(TyC2Config cfg) {
        TyC2Config c = cfg == null ? TyC2Config.defaults() : cfg;
        double bonus = Math.min(c.aidMaxLaborSpeedBonus(), unemployedCount * c.aidLaborSpeedPerUnemployed());
        return 1.0 + Math.max(0.0, bonus);
    }

    /**
     * Returns the count of active employed villagers holding {@code professionId}.
     *
     * @param professionId namespaced profession ID
     * @return active count (>= 0)
     */
    public int activeCountForProfession(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return 0;
        }
        return activeProfessionCounts.getOrDefault(professionId.trim().toLowerCase(Locale.ROOT), 0);
    }
}
