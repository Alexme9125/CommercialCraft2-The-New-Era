package com.tycooncraft.tyc2.forge.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * Reference-counted 3x3 chunk-loading ticket manager for Cooperation Hubs equipped with
 * {@code lc2:chunk_loading_upgrade}, backed by Forge 1.19.2 {@link ForgeChunkManager#forceChunk}.
 */
public final class TyC2ChunkTickets {

    public static final int CHUNK_RADIUS = 1; // 3x3 area

    private static final Map<ServerLevel, Set<BlockPos>> ACTIVE_OWNERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<ChunkPos, Integer>> HELD_COUNTS = new WeakHashMap<>();

    private TyC2ChunkTickets() {
    }

    /** Registers Forge's forced-chunk validation callback during common setup. */
    public static void registerValidationCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(TyC2Ids.MOD_ID, (level, ticketHelper) -> {
            // Keep persisted block tickets valid; Hub BlockEntities re-assert or release on load.
        });
    }

    /** Force-loads the 3x3 chunk area centered on {@code ownerPos}. */
    public static void holdArea(ServerLevel level, BlockPos ownerPos) {
        BlockPos key = ownerPos.immutable();
        Set<BlockPos> owners = ACTIVE_OWNERS.computeIfAbsent(level, ignored -> new HashSet<>());
        if (!owners.add(key)) {
            return;
        }
        ChunkPos center = new ChunkPos(key);
        Map<ChunkPos, Integer> counts = HELD_COUNTS.computeIfAbsent(level, ignored -> new HashMap<>());
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                ForgeChunkManager.forceChunk(level, TyC2Ids.MOD_ID, key, cp.x, cp.z, true, true);
                counts.merge(cp, 1, Integer::sum);
            }
        }
    }

    /** Releases the 3x3 forced-chunk tickets owned by {@code ownerPos}. */
    public static void releaseArea(ServerLevel level, BlockPos ownerPos) {
        BlockPos key = ownerPos.immutable();
        Set<BlockPos> owners = ACTIVE_OWNERS.get(level);
        if (owners == null || !owners.remove(key)) {
            return;
        }
        ChunkPos center = new ChunkPos(key);
        Map<ChunkPos, Integer> counts = HELD_COUNTS.get(level);
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                ForgeChunkManager.forceChunk(level, TyC2Ids.MOD_ID, key, cp.x, cp.z, false, true);
                if (counts != null) {
                    Integer cur = counts.get(cp);
                    if (cur != null) {
                        if (cur > 1) {
                            counts.put(cp, cur - 1);
                        } else {
                            counts.remove(cp);
                        }
                    }
                }
            }
        }
        if (owners.isEmpty()) {
            ACTIVE_OWNERS.remove(level);
        }
    }

    /** Returns whether {@code ownerPos} currently holds active 3x3 chunk tickets. */
    public static boolean isOwnerActive(ServerLevel level, BlockPos ownerPos) {
        Set<BlockPos> owners = ACTIVE_OWNERS.get(level);
        return owners != null && owners.contains(ownerPos);
    }

    /** Clears tracking maps when a level unloads. */
    public static void clearLevel(ServerLevel level) {
        ACTIVE_OWNERS.remove(level);
        HELD_COUNTS.remove(level);
    }
}
