package com.tycooncraft.tyc2.village;

import java.util.Objects;

/**
 * Pure-Java immutable item stack representation stored inside {@link CooperationHubStorage}.
 *
 * @param itemId namespaced item identifier (e.g. {@code "minecraft:iron_ingot"})
 * @param count positive stack count ({@code >= 1})
 * @param nbtJson optional serialized NBT/data-component payload (normalized to {@code ""} when null)
 */
public record SlotStack(String itemId, int count, String nbtJson) {

    /**
     * Validates item ID and count, normalizing null {@code nbtJson} to an empty string.
     */
    public SlotStack {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be null or blank");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0, got " + count);
        }
        nbtJson = nbtJson == null ? "" : nbtJson;
    }

    /**
     * Creates a plain un-tagged {@link SlotStack} with empty NBT metadata.
     *
     * @param itemId namespaced item identifier
     * @param count positive stack count
     * @return new slot stack
     */
    public static SlotStack of(String itemId, int count) {
        return new SlotStack(itemId, count, "");
    }

    /**
     * Checks whether this stack can merge with another stack (identical item ID and NBT payload).
     *
     * @param other candidate stack
     * @return {@code true} if stackable together
     */
    public boolean canStackWith(SlotStack other) {
        Objects.requireNonNull(other, "other");
        return this.itemId.equals(other.itemId) && this.nbtJson.equals(other.nbtJson);
    }

    /**
     * Checks whether this stack can merge with the given item ID and NBT payload.
     *
     * @param otherItemId candidate item ID
     * @param otherNbtJson candidate NBT payload (null treated as {@code ""})
     * @return {@code true} if stackable together
     */
    public boolean canStackWith(String otherItemId, String otherNbtJson) {
        if (otherItemId == null) {
            return false;
        }
        String normNbt = otherNbtJson == null ? "" : otherNbtJson;
        return this.itemId.equals(otherItemId) && this.nbtJson.equals(normNbt);
    }

    /**
     * Checks whether this stack's item ID matches {@code targetItemId}.
     *
     * @param targetItemId item ID to compare
     * @return {@code true} if item IDs match
     */
    public boolean matchesItem(String targetItemId) {
        return this.itemId.equals(targetItemId);
    }

    /**
     * Returns a copy of this stack with a new positive count.
     *
     * @param newCount replacement count ({@code >= 1})
     * @return updated stack
     */
    public SlotStack withCount(int newCount) {
        return new SlotStack(itemId, newCount, nbtJson);
    }
}
