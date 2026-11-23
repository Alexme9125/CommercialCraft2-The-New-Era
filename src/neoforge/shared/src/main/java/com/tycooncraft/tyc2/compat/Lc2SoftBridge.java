package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

/**
 * Zero-hard-dependency soft bridge to LogisticsCraft² ({@code lc2}).
 * Detects nearby LC2 Smart Ports and Cargo Stations around a Village Cooperation Hub using
 * chunk BlockEntity maps so players can locate aerial/maritime/rail cargo routes directly in
 * Hub Settings and the handheld Communication Terminal.
 */
public final class Lc2SoftBridge {

    private static Boolean cachedLoaded;

    private Lc2SoftBridge() {
    }

    /** Returns {@code true} if {@code lc2} is installed at runtime. */
    public static boolean isLc2Loaded() {
        if (cachedLoaded == null) {
            try {
                cachedLoaded = ModList.get() != null && ModList.get().isLoaded("lc2");
            } catch (Throwable ignored) {
                cachedLoaded = false;
            }
        }
        return cachedLoaded;
    }

    /**
     * Scans already-loaded chunks within {@code radius} blocks of {@code center} for LC2 Smart Ports
     * and Cargo Stations, returning up to 8 human-readable coordinate labels sorted by distance.
     */
    public static List<String> findNearbyLogisticsStations(ServerLevel level, BlockPos center, int radius) {
        if (!isLc2Loaded() || level == null || center == null || radius <= 0) {
            return List.of();
        }
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;
        long maxDistSq = (long) radius * (long) radius;

        record StationMatch(String label, double distSq) {
        }
        List<StationMatch> matches = new ArrayList<>();

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos bp = entry.getKey();
                    double dSq = bp.distSqr(center);
                    if (dSq > maxDistSq) {
                        continue;
                    }
                    String blockId = TyC2Native.blockId(entry.getValue().getBlockState());
                    String label = classifyLc2Block(blockId);
                    if (label != null) {
                        matches.add(new StationMatch(
                                label + " (" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ() + ")",
                                dSq
                        ));
                    }
                }
            }
        }

        matches.sort(Comparator.comparingDouble(StationMatch::distSq));
        List<String> out = new ArrayList<>(Math.min(8, matches.size()));
        for (int i = 0; i < Math.min(8, matches.size()); i++) {
            out.add(matches.get(i).label());
        }
        return out;
    }

    private static String classifyLc2Block(String blockId) {
        if (blockId == null || !blockId.startsWith("lc2:")) {
            return null;
        }
        String path = blockId.substring("lc2:".length());
        if (path.contains("smart_port")) {
            return "Smart Port";
        }
        if (path.contains("cargo_station")) {
            return "Cargo Station";
        }
        if (path.contains("drone") || path.contains("port") || path.contains("station")) {
            return "LC2 " + path;
        }
        return null;
    }
}
