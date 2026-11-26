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
     * and Cargo Stations, returning up to 8 locale-independent {@code blockId|x,y,z} descriptors
     * sorted by distance (formatted on the client via {@link #formatStationLabel(String)}).
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

        record StationMatch(String encoded, double distSq) {
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
                    if (isLc2LogisticsBlock(blockId)) {
                        matches.add(new StationMatch(
                                blockId + "|" + bp.getX() + "," + bp.getY() + "," + bp.getZ(),
                                dSq
                        ));
                    }
                }
            }
        }

        matches.sort(Comparator.comparingDouble(StationMatch::distSq));
        List<String> out = new ArrayList<>(Math.min(8, matches.size()));
        for (int i = 0; i < Math.min(8, matches.size()); i++) {
            out.add(matches.get(i).encoded());
        }
        return out;
    }

    /**
     * Formats a {@code blockId|x,y,z} station descriptor produced by
     * {@link #findNearbyLogisticsStations(ServerLevel, BlockPos, int)} using the client's
     * localized item/block name, falling back cleanly if {@code |} is absent.
     */
    public static String formatStationLabel(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        int sep = encoded.indexOf('|');
        if (sep <= 0 || sep >= encoded.length() - 1) {
            return encoded;
        }
        String blockId = encoded.substring(0, sep);
        String coords = encoded.substring(sep + 1);
        return TyC2Native.localizedItemName(blockId) + " (" + coords + ")";
    }

    private static boolean isLc2LogisticsBlock(String blockId) {
        return "lc2:smart_port".equals(blockId) || "lc2:cargo_station".equals(blockId);
    }
}
