package com.tycooncraft.tyc2.village;

import com.tycooncraft.tyc2.core.CommonTagAliases;
import com.tycooncraft.tyc2.core.MintIngredient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable representation of a village's active expansion material desire order.
 *
 * @param desireId unique identifier for this expansion request
 * @param projectType structure type to construct once materials and budget are satisfied
 * @param requiredMaterials immutable map of {@code itemId -> totalRequiredQty}
 * @param deliveredMaterials immutable map of {@code itemId -> deliveredQty}
 * @param constructionBudgetCost municipal capital cost (IC) spent from {@code villageBalance}
 * @param completed whether this desire has been completed and queued for construction
 * @param autoFulfill whether Cooperation Hub Inbound items auto-deliver into this desire
 */
public record ExpansionDesire(
        String desireId,
        ProjectType projectType,
        Map<String, Integer> requiredMaterials,
        Map<String, Integer> deliveredMaterials,
        long constructionBudgetCost,
        boolean completed,
        boolean autoFulfill) {

    /**
     * Validates invariants and wraps material maps in unmodifiable insertion-ordered copies.
     */
    public ExpansionDesire {
        if (desireId == null || desireId.isBlank()) {
            throw new IllegalArgumentException("desireId must not be null or blank");
        }
        Objects.requireNonNull(projectType, "projectType");
        Objects.requireNonNull(requiredMaterials, "requiredMaterials");
        Objects.requireNonNull(deliveredMaterials, "deliveredMaterials");
        if (constructionBudgetCost < 0L) {
            throw new IllegalArgumentException(
                    "constructionBudgetCost must be >= 0, got " + constructionBudgetCost);
        }

        Map<String, Integer> reqCopy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : requiredMaterials.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalArgumentException("Material itemId must not be null or blank");
            }
            int qty = e.getValue() == null ? 0 : e.getValue();
            if (qty <= 0) {
                throw new IllegalArgumentException("Required material qty must be > 0 for " + e.getKey());
            }
            reqCopy.put(e.getKey(), qty);
        }

        Map<String, Integer> delCopy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : deliveredMaterials.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            int qty = e.getValue() == null ? 0 : Math.max(0, e.getValue());
            int cap = reqCopy.getOrDefault(e.getKey(), qty);
            delCopy.put(e.getKey(), Math.min(qty, cap));
        }

        requiredMaterials = Collections.unmodifiableMap(reqCopy);
        deliveredMaterials = Collections.unmodifiableMap(delCopy);
    }

    /**
     * Creates a new unfulfilled {@link ExpansionDesire} populated with default biome-styled
     * materials and budget cost for {@code projectType}.
     *
     * @param desireId unique desire ID
     * @param projectType structure type
     * @param biomeStyle village biome style
     * @param autoFulfill initial auto-fulfill setting
     * @return fresh expansion desire
     */
    public static ExpansionDesire createDefault(
            String desireId, ProjectType projectType, BiomeStyle biomeStyle, boolean autoFulfill) {
        Objects.requireNonNull(projectType, "projectType");
        Objects.requireNonNull(biomeStyle, "biomeStyle");
        return new ExpansionDesire(
                desireId,
                projectType,
                projectType.defaultRequiredMaterials(biomeStyle),
                Map.of(),
                projectType.defaultBudgetCost(),
                false,
                autoFulfill);
    }

    /**
     * Returns the remaining undelivered quantity for {@code itemId}.
     *
     * @param itemId namespaced item ID
     * @return remaining required count ({@code >= 0})
     */
    public int remainingFor(String itemId) {
        int req = requiredMaterials.getOrDefault(itemId, 0);
        int del = deliveredMaterials.getOrDefault(itemId, 0);
        return Math.max(0, req - del);
    }

    /**
     * Returns a map of all materials that still have remaining undelivered quantities.
     *
     * @return unmodifiable map of {@code itemId -> remainingQty}
     */
    public Map<String, Integer> remainingMaterials() {
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : requiredMaterials.entrySet()) {
            int rem = remainingFor(e.getKey());
            if (rem > 0) {
                remaining.put(e.getKey(), rem);
            }
        }
        return Collections.unmodifiableMap(remaining);
    }

    /**
     * Returns whether every required material has been fully delivered.
     *
     * @return {@code true} if no material shortfall remains
     */
    public boolean allMaterialsDelivered() {
        for (Map.Entry<String, Integer> e : requiredMaterials.entrySet()) {
            if (deliveredMaterials.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes the fractional material delivery progress in {@code [0.0, 1.0]}.
     *
     * @return completion ratio
     */
    public double completionRatio() {
        int totalReq = 0;
        int totalDel = 0;
        for (Map.Entry<String, Integer> e : requiredMaterials.entrySet()) {
            totalReq += e.getValue();
            totalDel += Math.min(e.getValue(), deliveredMaterials.getOrDefault(e.getKey(), 0));
        }
        if (totalReq == 0) {
            return 1.0;
        }
        return (double) totalDel / (double) totalReq;
    }

    /**
     * Returns an updated copy after delivering {@code amount} of {@code itemId} (clamped to remaining need).
     *
     * @param itemId namespaced item ID
     * @param amount delivered quantity ({@code >= 0})
     * @return updated expansion desire
     */
    public ExpansionDesire withDelivered(String itemId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0, got " + amount);
        }
        int needed = remainingFor(itemId);
        if (amount == 0 || needed == 0) {
            return this;
        }
        Map<String, Integer> nextDelivered = new LinkedHashMap<>(deliveredMaterials);
        int current = nextDelivered.getOrDefault(itemId, 0);
        nextDelivered.put(itemId, current + Math.min(needed, amount));
        return new ExpansionDesire(
                desireId, projectType, requiredMaterials, nextDelivered,
                constructionBudgetCost, completed, autoFulfill);
    }

    /**
     * Resolves the controlled common item tag accepted as a fallback substitute for a required
     * expansion building material, or {@code null} when only exact item IDs are accepted.
     *
     * @param requiredItemId canonical required material item ID
     * @return controlled tag specification (with leading {@code '#'}), or {@code null}
     */
    public static String optionalTagForMaterial(String requiredItemId) {
        if (requiredItemId == null || requiredItemId.isBlank()) {
            return null;
        }
        String norm = requiredItemId.trim().toLowerCase(Locale.ROOT);
        if ("minecraft:iron_ingot".equals(norm)) {
            return "#ingots/iron";
        }
        if ("minecraft:cobblestone".equals(norm)) {
            return "#cobblestones";
        }
        if ("minecraft:stone".equals(norm)) {
            return "#stones";
        }
        if ("minecraft:stone_bricks".equals(norm)) {
            return "#minecraft:stone_bricks";
        }
        if ("minecraft:dirt".equals(norm)) {
            return "#minecraft:dirt";
        }
        if (norm.endsWith("_log")) {
            return "#minecraft:logs";
        }
        if (norm.endsWith("_planks")) {
            return "#minecraft:planks";
        }
        if (norm.endsWith("glass_pane")) {
            return "#glass_panes";
        }
        if (norm.endsWith("_wool")) {
            return "#minecraft:wool";
        }
        if (norm.endsWith("_fence") && !norm.contains("nether_brick")) {
            return "#minecraft:wooden_fences";
        }
        return null;
    }

    /**
     * Checks whether {@code candidateItemId} satisfies {@code requiredItemId} either by exact ID
     * or via the controlled common tag returned by {@link #optionalTagForMaterial(String)}.
     *
     * @param candidateItemId candidate item ID in player inventory or warehouse
     * @param requiredItemId required expansion material ID
     * @param tagMatcher optional platform tag matcher (may be {@code null} for exact-only matching)
     * @return {@code true} if the candidate item can be delivered for this material
     */
    public static boolean matchesMaterial(
            String candidateItemId,
            String requiredItemId,
            MintIngredient.TagMatcher tagMatcher) {
        if (candidateItemId == null || candidateItemId.isBlank()
                || requiredItemId == null || requiredItemId.isBlank()) {
            return false;
        }
        if (requiredItemId.equalsIgnoreCase(candidateItemId.trim())) {
            return true;
        }
        if (tagMatcher == null) {
            return false;
        }
        String optTag = optionalTagForMaterial(requiredItemId);
        return optTag != null && CommonTagAliases.matchesTagSpec(candidateItemId, optTag, tagMatcher);
    }

    /**
     * Counts total available items in {@code storage} Inbound ({@code 0..499}) that satisfy
     * {@code requiredItemId} (exact matches plus controlled common-tag substitutes when
     * {@code tagMatcher} is provided).
     *
     * @param storage Cooperation Hub warehouse
     * @param requiredItemId required expansion material ID
     * @param tagMatcher optional platform tag matcher (may be {@code null})
     * @return total eligible item count in Inbound
     */
    public static int countAvailableInInbound(
            CooperationHubStorage storage,
            String requiredItemId,
            MintIngredient.TagMatcher tagMatcher) {
        Objects.requireNonNull(storage, "storage");
        if (requiredItemId == null || requiredItemId.isBlank()) {
            return 0;
        }
        if (tagMatcher == null || optionalTagForMaterial(requiredItemId) == null) {
            return storage.countInInbound(requiredItemId);
        }
        return storage.countInInbound(slot ->
                slot != null && slot.count() > 0 && matchesMaterial(slot.itemId(), requiredItemId, tagMatcher));
    }

    /**
     * Pulls any available required materials from Inbound slots ({@code 0..499}) of {@code storage}
     * using exact item-ID matching and returns the updated {@link ExpansionDesire}.
     *
     * @param storage Cooperation Hub warehouse
     * @return updated expansion desire after consuming available Inbound stock
     */
    public ExpansionDesire pullAvailableFromInbound(CooperationHubStorage storage) {
        return pullAvailableFromInbound(storage, null);
    }

    /**
     * Pulls available required materials from Inbound slots ({@code 0..499}) of {@code storage},
     * prioritizing exact item-ID matches first and falling back to controlled common-tag substitutes
     * (such as {@code #minecraft:logs}, {@code #minecraft:planks}, {@code #ingots/iron},
     * {@code #glass_panes}, {@code #minecraft:wool}) when {@code tagMatcher} is provided.
     *
     * @param storage Cooperation Hub warehouse
     * @param tagMatcher optional platform tag matcher (may be {@code null} for exact-only mode)
     * @return updated expansion desire after consuming available Inbound stock
     */
    public ExpansionDesire pullAvailableFromInbound(
            CooperationHubStorage storage,
            MintIngredient.TagMatcher tagMatcher) {
        Objects.requireNonNull(storage, "storage");
        if (completed || allMaterialsDelivered()) {
            return this;
        }
        ExpansionDesire current = this;
        for (Map.Entry<String, Integer> rem : remainingMaterials().entrySet()) {
            String itemId = rem.getKey();
            int needed = rem.getValue();
            if (needed <= 0) {
                continue;
            }
            // Pass 1: exact item ID first
            int exactAvailable = storage.countInInbound(itemId);
            int takeExact = Math.min(needed, exactAvailable);
            if (takeExact > 0 && storage.consumeFromInbound(itemId, takeExact)) {
                current = current.withDelivered(itemId, takeExact);
                needed -= takeExact;
            }
            // Pass 2: controlled common-tag substitutes if still short
            if (needed > 0 && tagMatcher != null) {
                String optTag = optionalTagForMaterial(itemId);
                if (optTag != null) {
                    Predicate<SlotStack> tagPred = slot ->
                            slot != null && slot.count() > 0
                                    && !itemId.equalsIgnoreCase(slot.itemId())
                                    && CommonTagAliases.matchesTagSpec(slot.itemId(), optTag, tagMatcher);
                    int tagAvailable = storage.countInInbound(tagPred);
                    int takeTag = Math.min(needed, tagAvailable);
                    if (takeTag > 0 && storage.consumeFromInboundMatching(tagPred, takeTag)) {
                        current = current.withDelivered(itemId, takeTag);
                    }
                }
            }
        }
        return current;
    }

    /**
     * Returns an updated copy with {@link #completed()} set to {@code value}.
     *
     * @param value completed flag
     * @return updated expansion desire
     */
    public ExpansionDesire withCompleted(boolean value) {
        if (this.completed == value) {
            return this;
        }
        return new ExpansionDesire(
                desireId, projectType, requiredMaterials, deliveredMaterials,
                constructionBudgetCost, value, autoFulfill);
    }

    /**
     * Returns an updated copy with {@link #autoFulfill()} set to {@code enabled}.
     *
     * @param enabled auto-fulfill flag
     * @return updated expansion desire
     */
    public ExpansionDesire withAutoFulfill(boolean enabled) {
        if (this.autoFulfill == enabled) {
            return this;
        }
        return new ExpansionDesire(
                desireId, projectType, requiredMaterials, deliveredMaterials,
                constructionBudgetCost, completed, enabled);
    }
}
