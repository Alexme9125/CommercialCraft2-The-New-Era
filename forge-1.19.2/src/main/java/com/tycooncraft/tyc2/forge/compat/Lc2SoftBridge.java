package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * Zero-hard-dependency LogisticsCraft² ({@code lc2}) integration bridge for chunk-loading
 * upgrades and scanning nearby {@code lc2:smart_port} / {@code lc2:cargo_station} coordinates.
 */
public final class Lc2SoftBridge {

    public static final String MOD_ID = "lc2";
    public static final String CHUNK_LOADING_UPGRADE_ID = "lc2:chunk_loading_upgrade";
    public static final String SMART_PORT_ID = "lc2:smart_port";
    public static final String CARGO_STATION_ID = "lc2:cargo_station";

    private Lc2SoftBridge() {
    }

    /** Returns {@code true} when LogisticsCraft² is installed. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Returns {@code true} if {@code stack} is an {@code lc2:chunk_loading_upgrade}. */
    public static boolean isChunkLoadingUpgrade(ItemStack stack) {
        return !stack.isEmpty() && CHUNK_LOADING_UPGRADE_ID.equals(TyC2Ids.itemId(stack));
    }

    /**
     * Scans loaded chunks within {@code radius} blocks of {@code center} for nearby LC2
     * {@code lc2:smart_port} and {@code lc2:cargo_station} blocks (up to 8 entries).
     */
    public static List<String> scanNearbyStations(ServerLevel level, BlockPos center, int radius) {
        List<String> results = new ArrayList<>();
        if (!isLoaded() || level == null || center == null) {
            return results;
        }
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - 16);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 24);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius && results.size() < 8; dx++) {
            int wx = center.getX() + dx;
            for (int dz = -radius; dz <= radius && results.size() < 8; dz++) {
                int wz = center.getZ() + dz;
                if (!level.hasChunkAt(cursor.set(wx, center.getY(), wz))) {
                    continue;
                }
                for (int y = minY; y <= maxY && results.size() < 8; y++) {
                    cursor.set(wx, y, wz);
                    BlockState state = level.getBlockState(cursor);
                    String bid = TyC2Ids.blockId(state.getBlock());
                    if (SMART_PORT_ID.equals(bid)) {
                        results.add(SMART_PORT_ID + "|" + wx + "," + y + "," + wz);
                    } else if (CARGO_STATION_ID.equals(bid)) {
                        results.add(CARGO_STATION_ID + "|" + wx + "," + y + "," + wz);
                    }
                }
            }
        }
        return results;
    }

    /** Formats a station entry ({@code "<blockId>|<x>,<y>,<z>"}) using the client-localized block name. */
    public static String formatStationLabel(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int sep = raw.indexOf('|');
        if (sep <= 0 || sep + 1 >= raw.length()) {
            return raw;
        }
        String blockId = raw.substring(0, sep);
        String coords = raw.substring(sep + 1);
        return TyC2Ids.localizedItemName(blockId, null) + " (" + coords + ")";
    }
}
