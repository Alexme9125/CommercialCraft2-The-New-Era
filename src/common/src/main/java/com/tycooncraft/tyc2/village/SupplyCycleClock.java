package com.tycooncraft.tyc2.village;

/**
 * Pure-Java weekly supply-cycle calendar helper based on Minecraft's {@code dayTime} clock
 * (where sleeping advances {@code dayTime} by skipping nights).
 *
 * <p>Shared identically across NeoForge (1.21.1 / 26.x) and Forge (1.19.2) server managers
 * and client HUD/GUI countdown formatters.
 */
public final class SupplyCycleClock {

    /** Minecraft ticks per in-game hour (24 hours = 24,000 ticks). */
    public static final long TICKS_PER_HOUR = 1_000L;

    /** Minecraft ticks per full in-game day-night cycle. */
    public static final long TICKS_PER_DAY = 24_000L;

    /** Number of Minecraft days in one village supply week. */
    public static final int DAYS_PER_WEEK = 7;

    /** Minecraft ticks per full 7-day village supply week ({@code 168,000}). */
    public static final long TICKS_PER_WEEK = TICKS_PER_DAY * DAYS_PER_WEEK;

    private SupplyCycleClock() {
    }

    /**
     * Normalizes a village's stored {@code lastRefreshTick} anchor against {@code currentDayTime}.
     *
     * <p>Handles backward time jumps (e.g., {@code /time set 0}) or legacy saves that previously
     * recorded {@code gameTime > dayTime} by re-anchoring to {@code max(0, currentDayTime)} so
     * weekly refreshes never stall.
     *
     * @param currentDayTime current overworld {@code dayTime} tick
     * @param lastRefreshTick persisted last refresh tick anchor
     * @return safe non-negative anchor guaranteed {@code <= max(0, currentDayTime)}
     */
    public static long normalizeRefreshAnchor(long currentDayTime, long lastRefreshTick) {
        long safeNow = Math.max(0L, currentDayTime);
        if (lastRefreshTick < 0L || lastRefreshTick > safeNow) {
            return safeNow;
        }
        return lastRefreshTick;
    }

    /**
     * Computes remaining {@code dayTime} ticks until the next scheduled supply refresh,
     * strictly clamped to {@code [0, max(0, effectiveRefreshTicks)]}.
     *
     * @param currentDayTime current overworld {@code dayTime} tick
     * @param lastRefreshTick persisted last refresh tick anchor
     * @param effectiveRefreshTicks effective refresh interval in ticks (after low-unemployment bonus)
     * @return remaining ticks in {@code [0, max(0, effectiveRefreshTicks)]}
     */
    public static long remainingRefreshTicks(
            long currentDayTime,
            long lastRefreshTick,
            int effectiveRefreshTicks) {
        if (effectiveRefreshTicks <= 0) {
            return 0L;
        }
        long safeNow = Math.max(0L, currentDayTime);
        long anchor = normalizeRefreshAnchor(safeNow, lastRefreshTick);
        long elapsed = Math.max(0L, safeNow - anchor);
        if (elapsed >= effectiveRefreshTicks) {
            return 0L;
        }
        return effectiveRefreshTicks - elapsed;
    }

    /**
     * Evaluates whether a village is due for a supply/demand order refresh.
     *
     * <p>Returns {@code true} when:
     * <ul>
     *   <li>{@code ordersEmpty} is {@code true} (initial order generation), OR</li>
     *   <li>{@code allOrdersCompleted} is {@code true} (early restock reward when player finishes all orders), OR</li>
     *   <li>elapsed {@code dayTime} since the normalized anchor is {@code >= max(1, effectiveRefreshTicks)}.</li>
     * </ul>
     *
     * @param currentDayTime current overworld {@code dayTime} tick
     * @param lastRefreshTick persisted last refresh tick anchor
     * @param effectiveRefreshTicks effective refresh interval in ticks (after low-unemployment bonus)
     * @param ordersEmpty {@code true} if the village currently has no active orders
     * @param allOrdersCompleted {@code true} if all active orders are completed
     * @return {@code true} if a demand refresh should execute now
     */
    public static boolean isRefreshDue(
            long currentDayTime,
            long lastRefreshTick,
            int effectiveRefreshTicks,
            boolean ordersEmpty,
            boolean allOrdersCompleted) {
        if (ordersEmpty || allOrdersCompleted) {
            return true;
        }
        long period = Math.max(1L, effectiveRefreshTicks);
        long safeNow = Math.max(0L, currentDayTime);
        long anchor = normalizeRefreshAnchor(safeNow, lastRefreshTick);
        return (safeNow - anchor) >= period;
    }

    /**
     * Returns whole remaining Minecraft days ({@code floor(max(0, remainingTicks) / 24_000)}) for HUD display.
     *
     * @param remainingTicks remaining refresh ticks
     * @return whole remaining days ({@code >= 0})
     */
    public static long remainingWholeDays(long remainingTicks) {
        return Math.max(0L, remainingTicks) / TICKS_PER_DAY;
    }

    /**
     * Returns remaining whole Minecraft hours within the partial day
     * ({@code (max(0, remainingTicks) % 24_000) / 1_000}, in {@code 0..23}) for HUD display.
     *
     * @param remainingTicks remaining refresh ticks
     * @return remaining hours within the day ({@code 0..23})
     */
    public static long remainingWholeHoursInDay(long remainingTicks) {
        return (Math.max(0L, remainingTicks) % TICKS_PER_DAY) / TICKS_PER_HOUR;
    }

    /**
     * Returns total whole remaining Minecraft hours ({@code floor(max(0, remainingTicks) / 1_000)}).
     *
     * @param remainingTicks remaining refresh ticks
     * @return total whole remaining hours ({@code >= 0})
     */
    public static long remainingTotalHours(long remainingTicks) {
        return Math.max(0L, remainingTicks) / TICKS_PER_HOUR;
    }
}
