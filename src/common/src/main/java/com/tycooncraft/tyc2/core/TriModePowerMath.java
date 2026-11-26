package com.tycooncraft.tyc2.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure-Java energy and fuel math for the Primitive Minting Machine (burnable fuel only),
 * the pure-electric Industrial Minting Machine (IC2 EU + FE + battery discharge only),
 * the Industrial ATM (Tri-Mode EU + FE + solid fuel fallback), and the Communication Terminal.
 */
public final class TriModePowerMath {

    /** Canonical conversion ratio: 1 EU = 4 FE. */
    public static final int EU_TO_FE_RATIO = 4;

    /** Primitive Minting Machine duration multiplier relative to Industrial Mint base ticks (1.5x slower). */
    public static final double PRIMITIVE_MINT_TIME_MULTIPLIER = 1.5;

    /**
     * The electric Industrial Minting Machine ({@code tyc2:mint_machine}) is pure electric
     * (IC2 EU, Forge/NeoForge FE, and electric battery discharge only) and does NOT burn solid coal/fuel.
     */
    public static final boolean MINT_ACCEPTS_SOLID_FUEL = false;

    /** Base Industrial Minting Machine energy buffer in EU (64,000 FE). */
    public static final int MINT_BASE_CAPACITY_EU = 16_000;

    /** Base Industrial Minting Machine LV input limit in EU/t (128 FE/t). */
    public static final int MINT_BASE_MAX_INPUT_EU_PER_TICK = 32;

    /** Vanilla solid fuel burn rate in EU/t for the Industrial ATM standalone fuel fallback. */
    public static final int FUEL_BURN_RATE_EU_PER_TICK = 16;

    /** Capacity bonus per IC2 Energy Storage Upgrade in EU. */
    public static final int ENERGY_STORAGE_UPGRADE_BONUS_EU = 10_000;

    /** Duration multiplier per IC2 Overclocker Upgrade (0.70x per upgrade). */
    public static final double OVERCLOCKER_TIME_MULTIPLIER = 0.70;

    /** Energy-per-tick multiplier per IC2 Overclocker Upgrade (1.60x per upgrade). */
    public static final double OVERCLOCKER_POWER_MULTIPLIER = 1.60;

    /** Maximum number of transformer tier steps supported (LV -> MV -> HV -> EV -> IV). */
    public static final int MAX_TRANSFORMER_UPGRADES = 4;

    /** Maximum effective overclocker upgrades applied in duration/energy scaling (prevents exponential int overflow). */
    public static final int MAX_OVERCLOCKER_UPGRADES = 16;

    /** Maximum effective energy storage upgrades applied in capacity calculation (prevents int overflow). */
    public static final int MAX_ENERGY_STORAGE_UPGRADES = 64;

    /** Base ATM energy buffer in EU (64,000 FE). */
    public static final int ATM_BASE_CAPACITY_EU = 16_000;

    /** Base ATM LV input limit in EU/t. */
    public static final int ATM_BASE_MAX_INPUT_EU_PER_TICK = 32;

    /** Communication Terminal maximum internal charge in EU (80,000 FE). */
    public static final int TERMINAL_MAX_CAPACITY_EU = 20_000;

    /** Communication Terminal maximum charge transfer rate in EU/t (LV/MV). */
    public static final int TERMINAL_CHARGE_RATE_EU_PER_TICK = 128;

    /** Energy cost to open the remote village terminal screen. */
    public static final int TERMINAL_COST_OPEN_EU = 20;

    /** Energy cost to execute a remote buy/sell trade. */
    public static final int TERMINAL_COST_TRADE_EU = 50;

    /** Energy cost to trigger a remote village upgrade or expansion. */
    public static final int TERMINAL_COST_UPGRADE_OR_EXPANSION_EU = 100;

    private static final Map<String, Integer> VANILLA_FUEL_TABLE;
    private static final Map<String, Integer> PRIMITIVE_BURN_TABLE;
    private static final Set<String> MINT_UPGRADE_IDS = Set.of(
            "ic2:overclocker_upgrade",
            "ic2:transformer_upgrade",
            "ic2:energy_storage_upgrade",
            "ic2:ejector_upgrade",
            "ic2:advanced_ejector_upgrade",
            "ic2:pulling_upgrade",
            "ic2:advanced_pulling_upgrade"
    );
    private static final Set<String> HUB_UPGRADE_IDS = Set.of(
            "lc2:chunk_loading_upgrade",
            "ic2:ejector_upgrade",
            "ic2:advanced_ejector_upgrade",
            "ic2:pulling_upgrade",
            "ic2:advanced_pulling_upgrade"
    );

    static {
        // ATM standalone solid-fuel EU fallback table
        Map<String, Integer> fuels = new LinkedHashMap<>();
        fuels.put("minecraft:coal", 1_600);
        fuels.put("minecraft:charcoal", 1_600);
        fuels.put("minecraft:redstone", 800);
        fuels.put("minecraft:coal_block", 14_400);
        fuels.put("minecraft:blaze_rod", 2_400);
        VANILLA_FUEL_TABLE = Collections.unmodifiableMap(fuels);

        // Primitive Minting Machine standard furnace burn-time table (in ticks)
        Map<String, Integer> burn = new LinkedHashMap<>();
        burn.put("minecraft:coal", 1_600);
        burn.put("minecraft:charcoal", 1_600);
        burn.put("minecraft:coal_block", 16_000);
        burn.put("minecraft:blaze_rod", 2_400);
        burn.put("minecraft:dried_kelp_block", 4_000);
        burn.put("minecraft:lava_bucket", 20_000);
        burn.put("minecraft:stick", 100);
        burn.put("minecraft:bamboo", 50);
        burn.put("minecraft:oak_planks", 300);
        burn.put("minecraft:spruce_planks", 300);
        burn.put("minecraft:birch_planks", 300);
        burn.put("minecraft:jungle_planks", 300);
        burn.put("minecraft:acacia_planks", 300);
        burn.put("minecraft:dark_oak_planks", 300);
        burn.put("minecraft:mangrove_planks", 300);
        burn.put("minecraft:cherry_planks", 300);
        burn.put("minecraft:bamboo_planks", 300);
        burn.put("minecraft:oak_log", 300);
        burn.put("minecraft:spruce_log", 300);
        burn.put("minecraft:birch_log", 300);
        burn.put("minecraft:jungle_log", 300);
        burn.put("minecraft:acacia_log", 300);
        burn.put("minecraft:dark_oak_log", 300);
        burn.put("minecraft:mangrove_log", 300);
        burn.put("minecraft:cherry_log", 300);
        PRIMITIVE_BURN_TABLE = Collections.unmodifiableMap(burn);
    }

    private TriModePowerMath() {
    }

    /**
     * Actions performed on a {@code tyc2:comm_terminal} with fixed EU costs.
     */
    public enum TerminalAction {
        OPEN_REMOTE_UI(TERMINAL_COST_OPEN_EU),
        REMOTE_TRADE(TERMINAL_COST_TRADE_EU),
        REMOTE_UPGRADE_OR_EXPANSION(TERMINAL_COST_UPGRADE_OR_EXPANSION_EU);

        private final int costEu;

        TerminalAction(int costEu) {
            this.costEu = costEu;
        }

        /**
         * Returns the EU cost of executing this action.
         *
         * @return cost in EU
         */
        public int costEu() {
            return costEu;
        }

        /**
         * Returns the equivalent FE cost of executing this action.
         *
         * @return cost in FE
         */
        public int costFe() {
            return costEu * EU_TO_FE_RATIO;
        }
    }

    /**
     * Snapshot of computed Minting Machine upgrade capabilities.
     *
     * @param overclockerCount number of installed overclocker upgrades
     * @param transformerCount number of installed transformer upgrades
     * @param energyStorageCount number of installed energy storage upgrades
     * @param maxCapacityEu resulting energy buffer capacity in EU
     * @param maxInputEuPerTick resulting max input rate in EU/t
     * @param powerTier voltage tier index (1=LV, 2=MV, 3=HV, 4=EV, 5=IV)
     */
    public record MintUpgradeStats(
            int overclockerCount,
            int transformerCount,
            int energyStorageCount,
            int maxCapacityEu,
            int maxInputEuPerTick,
            int powerTier
    ) {
        /**
         * Returns the maximum energy buffer capacity in Forge/NeoForge FE.
         *
         * @return max capacity in FE
         */
        public long maxCapacityFe() {
            return euToFe(maxCapacityEu);
        }

        /**
         * Returns the maximum input rate in Forge/NeoForge FE/t.
         *
         * @return max input rate in FE/t
         */
        public long maxInputFePerTick() {
            return euToFe(maxInputEuPerTick);
        }
    }

    /**
     * Converts EU to equivalent FE ({@code eu * 4}).
     *
     * @param eu energy in EU
     * @return equivalent energy in FE
     */
    public static long euToFe(long eu) {
        if (eu <= 0L) {
            return 0L;
        }
        return eu >= Long.MAX_VALUE / EU_TO_FE_RATIO ? Long.MAX_VALUE : eu * EU_TO_FE_RATIO;
    }

    /**
     * Converts FE to whole EU using floor division ({@code fe / 4}).
     *
     * @param fe energy in FE
     * @return whole EU amount (0 if {@code fe <= 0})
     */
    public static long feToEuFloor(long fe) {
        return fe <= 0L ? 0L : fe / EU_TO_FE_RATIO;
    }

    /**
     * Converts FE to EU using ceiling division so partial energy costs never round down to zero.
     *
     * @param fe energy in FE
     * @return ceiling EU amount (0 if {@code fe <= 0})
     */
    public static long feToEuCeil(long fe) {
        return fe <= 0L ? 0L : (fe + EU_TO_FE_RATIO - 1L) / EU_TO_FE_RATIO;
    }

    /**
     * Computes Minting Machine capacity and voltage tier stats from installed IC2 upgrades.
     *
     * @param overclockers count of overclocker upgrades (clamped >= 0)
     * @param transformers count of transformer upgrades (clamped >= 0)
     * @param energyStorages count of energy storage upgrades (clamped >= 0)
     * @return computed upgrade statistics
     */
    public static MintUpgradeStats computeMintUpgradeStats(int overclockers, int transformers, int energyStorages) {
        int oc = Math.min(MAX_OVERCLOCKER_UPGRADES, Math.max(0, overclockers));
        int tf = Math.max(0, transformers);
        int es = Math.min(MAX_ENERGY_STORAGE_UPGRADES, Math.max(0, energyStorages));
        int capacity = MINT_BASE_CAPACITY_EU + es * ENERGY_STORAGE_UPGRADE_BONUS_EU;
        int clampedTierSteps = Math.min(tf, MAX_TRANSFORMER_UPGRADES);
        int maxInput = MINT_BASE_MAX_INPUT_EU_PER_TICK * (1 << (2 * clampedTierSteps));
        int tier = 1 + clampedTierSteps;
        return new MintUpgradeStats(oc, tf, es, capacity, maxInput, tier);
    }

    /**
     * Computes the effective minting duration in ticks after applying overclocker upgrades.
     *
     * @param baseTicks base recipe duration in ticks (must be >= 1)
     * @param overclockers number of installed overclocker upgrades
     * @return effective tick duration (at least 1)
     */
    public static int effectiveMintDurationTicks(int baseTicks, int overclockers) {
        int safeBase = Math.max(1, baseTicks);
        int oc = Math.min(MAX_OVERCLOCKER_UPGRADES, Math.max(0, overclockers));
        if (oc == 0) {
            return safeBase;
        }
        double scaled = safeBase * Math.pow(OVERCLOCKER_TIME_MULTIPLIER, oc);
        return Math.max(1, (int) Math.round(scaled));
    }

    /**
     * Computes the effective energy draw per tick (EU/t) for a mint recipe with overclockers.
     *
     * @param baseEnergyEu base total recipe energy in EU
     * @param baseTicks base recipe duration in ticks
     * @param overclockers number of installed overclocker upgrades
     * @return EU consumed per active tick (at least 1)
     */
    public static int effectiveMintEuPerTick(int baseEnergyEu, int baseTicks, int overclockers) {
        int safeEnergy = Math.max(1, baseEnergyEu);
        int safeTicks = Math.max(1, baseTicks);
        int oc = Math.min(MAX_OVERCLOCKER_UPGRADES, Math.max(0, overclockers));
        double basePerTick = (double) safeEnergy / safeTicks;
        double scaled = basePerTick * Math.pow(OVERCLOCKER_POWER_MULTIPLIER, oc);
        if (!Double.isFinite(scaled) || scaled >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil(scaled));
    }

    /**
     * Computes the effective total EU cost of a mint recipe with overclockers.
     * Never drops below {@code baseEnergyEu}.
     *
     * @param baseEnergyEu base total recipe energy in EU
     * @param baseTicks base recipe duration in ticks
     * @param overclockers number of installed overclocker upgrades
     * @return effective total EU required
     */
    public static int effectiveMintTotalEu(int baseEnergyEu, int baseTicks, int overclockers) {
        int safeEnergy = Math.max(1, baseEnergyEu);
        int oc = Math.min(MAX_OVERCLOCKER_UPGRADES, Math.max(0, overclockers));
        if (oc == 0) {
            return safeEnergy;
        }
        int ticks = effectiveMintDurationTicks(baseTicks, oc);
        int perTick = effectiveMintEuPerTick(safeEnergy, baseTicks, oc);
        long product = (long) ticks * (long) perTick;
        int capped = product >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) product;
        return Math.max(safeEnergy, capped);
    }

    /**
     * Computes the minting duration in ticks for {@code tyc2:primitive_mint_machine}
     * ({@code ceil(recipe.durationTicks() * 1.5)}).
     *
     * @param recipe primitive-allowed mint recipe
     * @return primitive minting duration in ticks (>= 1)
     */
    public static int primitiveMintDurationTicks(MintRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        return primitiveMintDurationTicks(recipe.durationTicks());
    }

    /**
     * Computes the primitive minting duration in ticks for a given base recipe duration
     * ({@code ceil(baseTicks * 1.5)}).
     *
     * @param baseTicks base Industrial Mint duration in ticks
     * @return primitive minting duration in ticks (>= 1)
     */
    public static int primitiveMintDurationTicks(int baseTicks) {
        return Math.max(1, (int) Math.ceil(Math.max(1, baseTicks) * PRIMITIVE_MINT_TIME_MULTIPLIER));
    }

    /**
     * Returns standard furnace burn duration in ticks for {@code tyc2:primitive_mint_machine}
     * (Coal = 1600, Charcoal = 1600, Coal Block = 16000, Blaze Rod = 2400, Planks/Logs/Wood = 300,
     * Stick = 100), or {@code 0} if not a burnable primitive fuel.
     *
     * @param itemId item identifier
     * @return burn time in ticks, or {@code 0}
     */
    public static int primitiveBurnTicks(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        String norm = itemId.trim().toLowerCase(Locale.ROOT);
        Integer exact = PRIMITIVE_BURN_TABLE.get(norm);
        if (exact != null) {
            return exact;
        }
        // Generic overworld wooden planks/logs/wood/saplings support (including modded woods, excluding fireproof Nether woods)
        if (norm.contains("crimson") || norm.contains("warped")) {
            return 0;
        }
        if (norm.endsWith("_planks") || norm.endsWith("_log") || norm.endsWith("_wood")) {
            return 300;
        }
        if (norm.endsWith("_sapling")) {
            return 100;
        }
        return 0;
    }

    /**
     * Checks whether the given item ID is a burnable fuel for {@code tyc2:primitive_mint_machine}.
     *
     * @param itemId item identifier
     * @return {@code true} if burnable in the Primitive Minting Machine
     */
    public static boolean isPrimitiveFuelItem(String itemId) {
        return primitiveBurnTicks(itemId) > 0;
    }

    /**
     * Returns the core Primitive Minting Machine fuel burn-tick lookup table.
     *
     * @return read-only fuel burn-tick map
     */
    public static Map<String, Integer> primitiveFuelTable() {
        return PRIMITIVE_BURN_TABLE;
    }

    /**
     * Returns whether the electric Industrial Minting Machine ({@code tyc2:mint_machine})
     * accepts solid burnable fuel in slot 3. Always {@code false}: slot 3 is battery-discharge only.
     *
     * @return {@code false}
     */
    public static boolean mintAcceptsSolidFuel() {
        return MINT_ACCEPTS_SOLID_FUEL;
    }

    /**
     * Computes how many EU can be discharged in a single tick from an electric battery
     * (IC2 battery or FE energy item) into the pure-electric Industrial Minting Machine buffer.
     *
     * @param batteryAvailableEu energy currently stored in battery in EU
     * @param machineStoredEu current EU stored in the Industrial Minting Machine
     * @param machineCapacityEu maximum EU capacity of the Industrial Minting Machine
     * @param maxDischargeRateEuPerTick maximum discharge rate in EU/t
     * @return EU transferred from battery to machine this tick (>= 0)
     */
    public static long computeBatteryDischargeTransferEu(
            long batteryAvailableEu,
            long machineStoredEu,
            long machineCapacityEu,
            long maxDischargeRateEuPerTick
    ) {
        return computeChargeTransferEu(batteryAvailableEu, machineStoredEu, machineCapacityEu, maxDischargeRateEuPerTick);
    }

    /**
     * Returns the read-only standalone Vanilla fuel energy table (item ID -> EU yield)
     * used by the Industrial ATM ({@code tyc2:atm_machine}) fallback fuel slot.
     *
     * @return fuel yield table in EU
     */
    public static Map<String, Integer> vanillaFuelTable() {
        return VANILLA_FUEL_TABLE;
    }

    /**
     * Returns the EU yield of a standalone ATM fuel item, or {@code 0} if not a supported fuel.
     *
     * @param itemId item identifier
     * @return EU yield, or {@code 0}
     */
    public static int getFuelEnergyEu(String itemId) {
        if (itemId == null) {
            return 0;
        }
        return VANILLA_FUEL_TABLE.getOrDefault(itemId, 0);
    }

    /**
     * Checks whether the given item ID is a supported standalone ATM fuel item.
     *
     * @param itemId item identifier
     * @return {@code true} if burnable in the Industrial ATM auxiliary fuel slot
     */
    public static boolean isFuelItem(String itemId) {
        return getFuelEnergyEu(itemId) > 0;
    }

    /**
     * Checks whether an item ID is an accepted upgrade for {@code tyc2:mint_machine}.
     *
     * @param itemId item identifier
     * @return {@code true} if valid Minting Machine upgrade
     */
    public static boolean isSupportedMintUpgrade(String itemId) {
        return itemId != null && MINT_UPGRADE_IDS.contains(itemId);
    }

    /**
     * Checks whether an item ID is an accepted upgrade for {@code tyc2:cooperation_hub}.
     *
     * @param itemId item identifier
     * @return {@code true} if valid Cooperation Hub upgrade
     */
    public static boolean isSupportedHubUpgrade(String itemId) {
        return itemId != null && HUB_UPGRADE_IDS.contains(itemId);
    }

    /**
     * Computes how many EU can be transferred in a single tick from a machine/charger
     * to a rechargeable item (such as {@code tyc2:comm_terminal}).
     *
     * @param sourceAvailableEu available EU in source buffer
     * @param targetStoredEu current EU stored in target device
     * @param targetCapacityEu max EU capacity of target device
     * @param maxRateEuPerTick max charge rate in EU/t
     * @return EU amount to transfer this tick (>= 0)
     */
    public static long computeChargeTransferEu(
            long sourceAvailableEu,
            long targetStoredEu,
            long targetCapacityEu,
            long maxRateEuPerTick
    ) {
        if (sourceAvailableEu <= 0L || maxRateEuPerTick <= 0L || targetStoredEu >= targetCapacityEu) {
            return 0L;
        }
        long space = targetCapacityEu - targetStoredEu;
        return Math.min(sourceAvailableEu, Math.min(space, maxRateEuPerTick));
    }

    /**
     * Checks whether a Communication Terminal with {@code storedEu} can execute {@code action}.
     *
     * @param storedEu current stored EU
     * @param action terminal action
     * @return {@code true} if stored charge covers the action cost
     */
    public static boolean canAffordTerminalAction(long storedEu, TerminalAction action) {
        return action != null && storedEu >= action.costEu();
    }

    /**
     * Deducts a Communication Terminal action's EU cost if sufficient energy exists.
     *
     * @param storedEu current stored EU
     * @param action terminal action
     * @return remaining EU after deduction, or {@code -1} if insufficient energy
     */
    public static long consumeTerminalAction(long storedEu, TerminalAction action) {
        if (!canAffordTerminalAction(storedEu, action)) {
            return -1L;
        }
        return storedEu - action.costEu();
    }

    /**
     * Computes the fraction of fuel EU to generate in one tick while burning a solid fuel buffer.
     *
     * @param remainingFuelEu remaining EU in active fuel burn buffer
     * @param machineStoredEu current EU stored in machine
     * @param machineCapacityEu max EU capacity of machine
     * @return EU generated this tick (>= 0)
     */
    public static int computeFuelTickGenerationEu(int remainingFuelEu, int machineStoredEu, int machineCapacityEu) {
        if (remainingFuelEu <= 0 || machineStoredEu >= machineCapacityEu) {
            return 0;
        }
        int space = machineCapacityEu - machineStoredEu;
        return Math.min(remainingFuelEu, Math.min(space, FUEL_BURN_RATE_EU_PER_TICK));
    }
}
