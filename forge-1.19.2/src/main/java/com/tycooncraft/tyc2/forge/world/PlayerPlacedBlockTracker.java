package com.tycooncraft.tyc2.forge.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/**
 * Tracks player-placed block positions per dimension so village expansion never overwrites
 * player builds, machines, pipes, or decorative structures.
 */
public final class PlayerPlacedBlockTracker {

    private final Map<String, Set<Long>> placedByDimension = new HashMap<>();

    /** Records a player-placed block position. */
    public void markPlaced(String dimensionId, BlockPos pos) {
        if (dimensionId == null || pos == null) {
            return;
        }
        placedByDimension.computeIfAbsent(dimensionId, ignored -> new HashSet<>()).add(pos.asLong());
    }

    /** Clears a tracked position when broken or removed. */
    public void markRemoved(String dimensionId, BlockPos pos) {
        if (dimensionId == null || pos == null) {
            return;
        }
        Set<Long> set = placedByDimension.get(dimensionId);
        if (set != null) {
            set.remove(pos.asLong());
            if (set.isEmpty()) {
                placedByDimension.remove(dimensionId);
            }
        }
    }

    /** Returns {@code true} if the block at {@code (x, y, z)} in {@code dimensionId} was placed by a player. */
    public boolean isPlayerPlaced(String dimensionId, int x, int y, int z) {
        Set<Long> set = placedByDimension.get(dimensionId);
        return set != null && set.contains(BlockPos.asLong(x, y, z));
    }

    /** Returns {@code true} if {@code pos} in {@code dimensionId} was placed by a player. */
    public boolean isPlayerPlaced(String dimensionId, BlockPos pos) {
        return isPlayerPlaced(dimensionId, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Returns an unmodifiable view of tracked positions in {@code dimensionId}. */
    public Set<Long> getTracked(String dimensionId) {
        Set<Long> set = placedByDimension.get(dimensionId);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    /** Serializes all tracked coordinates into NBT. */
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<String, Set<Long>> entry : placedByDimension.entrySet()) {
            Set<Long> longs = entry.getValue();
            if (longs.isEmpty()) {
                continue;
            }
            long[] array = new long[longs.size()];
            int i = 0;
            for (Long v : longs) {
                array[i++] = v;
            }
            root.put(entry.getKey(), new LongArrayTag(array));
        }
        return root;
    }

    /** Restores a {@link PlayerPlacedBlockTracker} from NBT. */
    public static PlayerPlacedBlockTracker load(CompoundTag root) {
        PlayerPlacedBlockTracker tracker = new PlayerPlacedBlockTracker();
        if (root == null) {
            return tracker;
        }
        for (String dim : root.getAllKeys()) {
            if (root.contains(dim, Tag.TAG_LONG_ARRAY)) {
                long[] arr = root.getLongArray(dim);
                Set<Long> set = new HashSet<>(Math.max(16, arr.length));
                for (long v : arr) {
                    set.add(v);
                }
                if (!set.isEmpty()) {
                    tracker.placedByDimension.put(dim, set);
                }
            }
        }
        return tracker;
    }
}
