package com.tycooncraft.tyc2.village;

import java.util.Objects;

/**
 * Immutable snapshot of a single active trade order hosted by a partnered village.
 *
 * @param orderId unique order identifier within the village
 * @param side whether the village is selling or buying
 * @param itemId namespaced item identifier (or tag reference when used with tag matchers)
 * @param displayHint optional localized or human-readable display hint (normalized to {@code ""})
 * @param enchantSpec optional enchantment specification string (e.g. {@code "minecraft:mending:1"})
 * @param totalQty total order quantity ({@code >= 1})
 * @param fulfilledQty quantity already fulfilled ({@code 0..totalQty})
 * @param unitPrice price per single item in IC ({@code >= 1})
 * @param createdTick game tick when this order was created
 * @param expiresTick game tick when this order expires
 * @param autoFulfill whether Hub auto-fulfillment pulls matching Inbound stock automatically
 * @param seasonalBonus whether this order carries a seasonal harvest discount or seasonal demand bonus
 * @param biomeSpecialty whether this order matches the village's local biome specialty supply or shortage demand
 */
public record VillageTradeOrder(
        String orderId,
        OrderSide side,
        String itemId,
        String displayHint,
        String enchantSpec,
        int totalQty,
        int fulfilledQty,
        long unitPrice,
        long createdTick,
        long expiresTick,
        boolean autoFulfill,
        boolean seasonalBonus,
        boolean biomeSpecialty) {

    /**
     * Backwards-compatible 12-argument constructor defaulting {@link #biomeSpecialty()} to {@code false}.
     *
     * @param orderId unique order identifier within the village
     * @param side whether the village is selling or buying
     * @param itemId namespaced item identifier
     * @param displayHint optional display hint
     * @param enchantSpec optional enchantment specification string
     * @param totalQty total order quantity ({@code >= 1})
     * @param fulfilledQty quantity already fulfilled ({@code 0..totalQty})
     * @param unitPrice price per single item in IC ({@code >= 1})
     * @param createdTick dayTime tick when this order was created
     * @param expiresTick dayTime tick when this order expires
     * @param autoFulfill whether Hub auto-fulfillment pulls matching Inbound stock automatically
     * @param seasonalBonus whether this order carries a seasonal bonus
     */
    public VillageTradeOrder(
            String orderId,
            OrderSide side,
            String itemId,
            String displayHint,
            String enchantSpec,
            int totalQty,
            int fulfilledQty,
            long unitPrice,
            long createdTick,
            long expiresTick,
            boolean autoFulfill,
            boolean seasonalBonus) {
        this(orderId, side, itemId, displayHint, enchantSpec,
                totalQty, fulfilledQty, unitPrice,
                createdTick, expiresTick, autoFulfill, seasonalBonus, false);
    }

    /**
     * Validates order invariants and normalizes optional strings.
     */
    public VillageTradeOrder {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be null or blank");
        }
        Objects.requireNonNull(side, "side");
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be null or blank");
        }
        displayHint = displayHint == null ? "" : displayHint;
        enchantSpec = enchantSpec == null ? "" : enchantSpec;
        if (totalQty < 1) {
            throw new IllegalArgumentException("totalQty must be >= 1, got " + totalQty);
        }
        if (fulfilledQty < 0 || fulfilledQty > totalQty) {
            throw new IllegalArgumentException(
                    "fulfilledQty must be in 0.." + totalQty + ", got " + fulfilledQty);
        }
        if (unitPrice < 1L) {
            throw new IllegalArgumentException("unitPrice must be >= 1, got " + unitPrice);
        }
    }

    /**
     * Returns the remaining unfulfilled quantity ({@code >= 0}).
     *
     * @return remaining items
     */
    public int remainingQty() {
        return totalQty - fulfilledQty;
    }

    /**
     * Returns whether this order has been completely fulfilled.
     *
     * @return {@code true} when {@code fulfilledQty >= totalQty}
     */
    public boolean isCompleted() {
        return fulfilledQty >= totalQty;
    }

    /**
     * Returns whether this order has expired as of {@code currentTick}.
     *
     * @param currentTick current server game tick
     * @return {@code true} if {@code currentTick >= expiresTick}
     */
    public boolean isExpired(long currentTick) {
        return currentTick >= expiresTick;
    }

    /**
     * Computes the total currency value for {@code qty} items at {@link #unitPrice()}.
     *
     * @param qty item quantity ({@code >= 0})
     * @return total price in IC
     */
    public long totalPriceFor(int qty) {
        if (qty < 0) {
            throw new IllegalArgumentException("qty must be >= 0, got " + qty);
        }
        return Math.multiplyExact(unitPrice, (long) qty);
    }

    /**
     * Computes the total value of the remaining unfulfilled portion of this order.
     *
     * @return remaining value in IC
     */
    public long remainingValue() {
        return totalPriceFor(remainingQty());
    }

    /**
     * Returns an updated copy with {@code delta} additional items marked fulfilled.
     *
     * @param delta positive fulfilled quantity ({@code 0..remainingQty()})
     * @return updated order
     */
    public VillageTradeOrder withAddedFulfillment(int delta) {
        if (delta < 0 || fulfilledQty + delta > totalQty) {
            throw new IllegalArgumentException(
                    "Invalid fulfillment delta " + delta + " for remaining " + remainingQty());
        }
        return new VillageTradeOrder(
                orderId, side, itemId, displayHint, enchantSpec,
                totalQty, fulfilledQty + delta, unitPrice,
                createdTick, expiresTick, autoFulfill, seasonalBonus, biomeSpecialty);
    }

    /**
     * Returns an updated copy with {@link #autoFulfill()} set to {@code enabled}.
     *
     * @param enabled auto-fulfill flag
     * @return updated order
     */
    public VillageTradeOrder withAutoFulfill(boolean enabled) {
        if (this.autoFulfill == enabled) {
            return this;
        }
        return new VillageTradeOrder(
                orderId, side, itemId, displayHint, enchantSpec,
                totalQty, fulfilledQty, unitPrice,
                createdTick, expiresTick, enabled, seasonalBonus, biomeSpecialty);
    }
}
