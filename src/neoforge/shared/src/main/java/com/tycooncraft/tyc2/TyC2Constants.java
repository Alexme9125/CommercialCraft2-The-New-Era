package com.tycooncraft.tyc2;

/**
 * Shared constants for TycoonCraft²: The New Era ({@code tyc2}) across all NeoForge targets.
 */
public final class TyC2Constants {

    /** Canonical Mod ID across all 4 targets. */
    public static final String MOD_ID = "tyc2";

    /** Optional mod ID for IndustrialCraft²: The New Era. */
    public static final String MOD_IC2 = "ic2";

    /** Optional mod ID for LogisticsCraft²: The New Era. */
    public static final String MOD_LC2 = "lc2";

    /** Optional mod ID for Serene Seasons. */
    public static final String MOD_SERENE_SEASONS = "sereneseasons";

    /** Optional mod ID for Ecliptic Seasons. */
    public static final String MOD_ECLIPTIC_SEASONS = "eclipticseasons";

    /** Optional mod ID for The One Probe. */
    public static final String MOD_TOP = "theoneprobe";

    /** Optional mod ID for Jade. */
    public static final String MOD_JADE = "jade";

    /** Optional mod ID for JEI. */
    public static final String MOD_JEI = "jei";

    /** LC2 Chunk Loading Upgrade canonical item ID. */
    public static final String ITEM_LC2_CHUNK_LOADER = "lc2:chunk_loading_upgrade";

    /** LC2 Smart Port canonical block ID. */
    public static final String BLOCK_LC2_SMART_PORT = "lc2:smart_port";

    /** LC2 Cargo Station canonical block ID. */
    public static final String BLOCK_LC2_CARGO_STATION = "lc2:cargo_station";

    /** PersistentData boolean tag marking a Villager as a TyC2 Village Mayor. */
    public static final String TAG_MAYOR = "tyc2_mayor";

    /** PersistentData string tag storing the Mayor's village UUID. */
    public static final String TAG_MAYOR_VILLAGE_ID = "tyc2_village_id";

    /** Village scanning radius (blocks) around Bell / Cooperation Hub. */
    public static final int VILLAGE_SCAN_RADIUS = 48;

    /** Minimum horizontal separation (blocks) between two tracked villages. */
    public static final int MIN_VILLAGE_SEPARATION = 96;

    /** Radius (chunks) around a Hub for 3x3 chunk loading (1 -> 3x3). */
    public static final int CHUNK_LOADER_RADIUS = 1;

    private TyC2Constants() {
    }
}
