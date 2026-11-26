package com.tycooncraft.tyc2.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The 9 Industrial Credit (IC) currency denominations supported by TycoonCraft².
 *
 * <p>When IndustrialCraft² ({@code ic2}) is installed, {@code ic2:coin} is treated
 * as canonical 1x legal tender while both {@code ic2:coin} and {@code tyc2:coin_1}
 * remain mutually accepted at 1x value everywhere.
 */
public enum CurrencyDenomination {
    X1(1L, "coin_1", "ic2:coin"),
    X5(5L, "coin_5", null),
    X10(10L, "coin_10", null),
    X20(20L, "coin_20", null),
    X50(50L, "coin_50", null),
    X100(100L, "coin_100", null),
    X200(200L, "coin_200", null),
    X500(500L, "coin_500", null),
    X1000(1000L, "coin_1000", null);

    /** Mod namespace for TyC2 coin items. */
    public static final String MOD_ID = "tyc2";

    /** IC2 coin item identifier unified with {@link #X1}. */
    public static final String IC2_COIN_ID = "ic2:coin";

    /**
     * Maximum IC allowed in a single physical cash withdrawal (64 full stacks of 1000x coins = 4,096,000 IC)
     * to prevent world item entity flooding or integer overflow in {@link #decompose(long)}.
     */
    public static final long MAX_SINGLE_WITHDRAWAL_IC = 4_096_000L;

    private static final List<CurrencyDenomination> DESCENDING = Collections.unmodifiableList(
            Arrays.asList(X1000, X500, X200, X100, X50, X20, X10, X5, X1)
    );

    private static final Map<String, CurrencyDenomination> BY_ITEM_ID;
    private static final Map<Long, CurrencyDenomination> BY_VALUE;

    static {
        Map<String, CurrencyDenomination> idMap = new HashMap<>();
        Map<Long, CurrencyDenomination> valMap = new HashMap<>();
        for (CurrencyDenomination denom : values()) {
            idMap.put(denom.tyc2ItemId(), denom);
            idMap.put(denom.itemPath, denom);
            if (denom.ic2CompatId != null) {
                idMap.put(denom.ic2CompatId, denom);
            }
            valMap.put(denom.value, denom);
        }
        BY_ITEM_ID = Collections.unmodifiableMap(idMap);
        BY_VALUE = Collections.unmodifiableMap(valMap);
    }

    private final long value;
    private final String itemPath;
    private final String ic2CompatId;

    CurrencyDenomination(long value, String itemPath, String ic2CompatId) {
        this.value = value;
        this.itemPath = itemPath;
        this.ic2CompatId = ic2CompatId;
    }

    /**
     * Returns the face value of a single coin in Industrial Credits (IC).
     *
     * @return coin value
     */
    public long value() {
        return value;
    }

    /**
     * Returns the TyC2 registry path (e.g. {@code "coin_100"}).
     *
     * @return item path without namespace
     */
    public String itemPath() {
        return itemPath;
    }

    /**
     * Returns the TyC2-namespaced item ID (e.g. {@code "tyc2:coin_100"}).
     *
     * @return TyC2 item identifier
     */
    public String tyc2ItemId() {
        return MOD_ID + ":" + itemPath;
    }

    /**
     * Returns the optional IC2 compatibility item ID ({@code "ic2:coin"} for {@link #X1}).
     *
     * @return IC2 item ID or {@code null} if none
     */
    public String ic2CompatId() {
        return ic2CompatId;
    }

    /**
     * Returns the canonical output item ID to mint or dispense for this denomination.
     *
     * @param ic2Loaded whether the {@code ic2} mod is currently loaded
     * @return {@code "ic2:coin"} for {@link #X1} when IC2 is loaded, otherwise {@link #tyc2ItemId()}
     */
    public String canonicalItemId(boolean ic2Loaded) {
        if (ic2Loaded && ic2CompatId != null) {
            return ic2CompatId;
        }
        return tyc2ItemId();
    }

    /**
     * Resolves a denomination from a full item ID (such as {@code "tyc2:coin_1"},
     * {@code "ic2:coin"}, or {@code "tyc2:coin_500"}) or bare path ({@code "coin_50"}).
     *
     * @param itemId item identifier
     * @return matching denomination, or empty if not a recognized coin
     */
    public static Optional<CurrencyDenomination> valueOfItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ITEM_ID.get(itemId.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * Resolves a denomination from its exact face value (1, 5, 10, ..., 1000).
     *
     * @param faceValue exact denomination value
     * @return matching denomination, or empty if not a standard denomination
     */
    public static Optional<CurrencyDenomination> fromValue(long faceValue) {
        return Optional.ofNullable(BY_VALUE.get(faceValue));
    }

    /**
     * Resolves a denomination from its enum name (e.g. {@code "X100"}), numeric text ({@code "100"}),
     * or item ID ({@code "tyc2:coin_100"} / {@code "ic2:coin"}).
     *
     * @param spec denomination specification string
     * @return matching denomination, or empty if invalid
     */
    public static Optional<CurrencyDenomination> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return Optional.empty();
        }
        String trimmed = spec.trim();
        Optional<CurrencyDenomination> byId = valueOfItemId(trimmed);
        if (byId.isPresent()) {
            return byId;
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        for (CurrencyDenomination denom : values()) {
            if (denom.name().equals(upper)) {
                return Optional.of(denom);
            }
        }
        try {
            long val = Long.parseLong(upper.startsWith("X") ? upper.substring(1) : upper);
            return fromValue(val);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Checks whether the given item ID is any legal tender coin (including {@code ic2:coin}).
     *
     * @param itemId item identifier
     * @return {@code true} if the item is legal tender
     */
    public static boolean isLegalTender(String itemId) {
        return valueOfItemId(itemId).isPresent();
    }

    /**
     * Returns all denominations ordered from highest ({@link #X1000}) to lowest ({@link #X1}).
     *
     * @return unmodifiable list in descending value order
     */
    public static List<CurrencyDenomination> descendingOrder() {
        return DESCENDING;
    }

    /**
     * Returns whether {@code amount} is within the safe single physical cash withdrawal bound
     * ({@code 1 .. MAX_SINGLE_WITHDRAWAL_IC}).
     *
     * @param amount requested IC withdrawal
     * @return {@code true} if safe to dispense as physical coins
     */
    public static boolean isWithinSingleWithdrawalLimit(long amount) {
        return amount > 0L && amount <= MAX_SINGLE_WITHDRAWAL_IC;
    }

    /**
     * Decomposes a non-negative credit amount into the optimal minimum-coin map
     * using greedy high-to-low change-making.
     *
     * @param amount non-negative amount in Industrial Credits
     * @return ordered map from highest to lowest non-zero denomination count
     * @throws IllegalArgumentException if {@code amount < 0}
     * @throws ArithmeticException if the required {@link #X1000} coin count exceeds {@link Integer#MAX_VALUE}
     */
    public static Map<CurrencyDenomination, Integer> decompose(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Currency amount must be non-negative: " + amount);
        }
        Map<CurrencyDenomination, Integer> result = new LinkedHashMap<>();
        long remaining = amount;
        for (CurrencyDenomination denom : DESCENDING) {
            if (remaining >= denom.value) {
                long countLong = remaining / denom.value;
                if (countLong > Integer.MAX_VALUE) {
                    throw new ArithmeticException("Coin count overflow for denomination " + denom + ": " + countLong);
                }
                result.put(denom, (int) countLong);
                remaining %= denom.value;
            }
            if (remaining == 0L) {
                break;
            }
        }
        return result;
    }

    /**
     * Computes the total Industrial Credit value represented by a denomination-to-count map.
     *
     * @param coins map of denomination counts (may be {@code null} or empty)
     * @return total IC value
     * @throws IllegalArgumentException if any count is negative
     */
    public static long totalValue(Map<CurrencyDenomination, Integer> coins) {
        if (coins == null || coins.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (Map.Entry<CurrencyDenomination, Integer> entry : coins.entrySet()) {
            CurrencyDenomination denom = entry.getKey();
            Integer count = entry.getValue();
            if (denom == null || count == null || count == 0) {
                continue;
            }
            if (count < 0) {
                throw new IllegalArgumentException("Negative coin count for " + denom + ": " + count);
            }
            sum = Math.addExact(sum, Math.multiplyExact(denom.value, count.longValue()));
        }
        return sum;
    }
}
