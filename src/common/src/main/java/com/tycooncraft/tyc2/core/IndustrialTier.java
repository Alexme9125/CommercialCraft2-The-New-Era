package com.tycooncraft.tyc2.core;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player and company industrial progression tier ({@code TIER_0} through {@code TIER_5}).
 *
 * <p>Provides separate milestone item/block progression tables for IC2-enabled worlds
 * and pure Vanilla worlds, plus the tech-gating rule keeping village trade offerings
 * naturally one tier behind the player's current industrial peak (with endgame unlock
 * at {@code TIER_5} + Village Level 10).
 */
public enum IndustrialTier {
    TIER_0(0, "pre_industrial"),
    TIER_1(1, "lv_basic"),
    TIER_2(2, "mv_industrial"),
    TIER_3(3, "hv_advanced"),
    TIER_4(4, "ev_high_tech"),
    TIER_5(5, "endgame_iridium");

    private static final Map<IndustrialTier, Set<String>> IC2_MILESTONES;
    private static final Map<IndustrialTier, Set<String>> VANILLA_MILESTONES;
    private static final Map<String, IndustrialTier> IC2_LOOKUP;
    private static final Map<String, IndustrialTier> VANILLA_LOOKUP;
    private static final Map<String, IndustrialTier> IC2_TAG_MILESTONES;
    private static final Map<String, IndustrialTier> VANILLA_TAG_MILESTONES;

    static {
        Map<IndustrialTier, Set<String>> ic2 = new EnumMap<>(IndustrialTier.class);
        ic2.put(TIER_0, Set.of(
                "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:iron_ingot"
        ));
        ic2.put(TIER_1, Set.of(
                "ic2:generator", "ic2:batbox", "ic2:macerator", "ic2:extractor",
                "ic2:compressor", "ic2:electric_furnace", "ic2:copper_plate",
                "ic2:tin_plate", "ic2:bronze_plate", "ic2:circuit",
                "ic2:re_battery", "ic2:copper_cable", "ic2:machine"
        ));
        ic2.put(TIER_2, Set.of(
                "ic2:cesu", "ic2:metal_former", "ic2:ore_washing_plant",
                "ic2:centrifuge", "ic2:blast_furnace", "ic2:steel_ingot",
                "ic2:refined_iron_ingot", "ic2:advanced_machine", "ic2:advanced_circuit"
        ));
        ic2.put(TIER_3, Set.of(
                "ic2:mfe", "ic2:induction_furnace", "ic2:nuclear_reactor",
                "ic2:alloy", "ic2:carbon_plate", "ic2:energy_crystal"
        ));
        ic2.put(TIER_4, Set.of(
                "ic2:mfsu", "ic2:mass_fabricator", "ic2:scanner", "ic2:advanced_scanner",
                "ic2:uu_scanner", "ic2:replicator", "ic2:lapotron_crystal",
                "ic2:uranium", "ic2:uranium_fuel_rod", "ic2:dual_uranium_fuel_rod",
                "ic2:quad_uranium_fuel_rod", "ic2:mox_fuel_rod",
                "ic2:dual_mox_fuel_rod", "ic2:quad_mox_fuel_rod"
        ));
        ic2.put(TIER_5, Set.of(
                "ic2:uu_matter", "ic2:iridium_shard", "ic2:iridium_ore", "ic2:iridium",
                "ic2:iridium_neutron_reflector", "ic2:iridium_drill", "ic2:quantum_helmet",
                "ic2:quantum_chestplate", "ic2:quantum_leggings", "ic2:quantum_boots"
        ));
        IC2_MILESTONES = Collections.unmodifiableMap(ic2);

        Map<IndustrialTier, Set<String>> vanilla = new EnumMap<>(IndustrialTier.class);
        vanilla.put(TIER_0, Set.of(
                "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:furnace"
        ));
        vanilla.put(TIER_1, Set.of(
                "minecraft:iron_ingot", "minecraft:iron_pickaxe", "minecraft:iron_sword",
                "minecraft:iron_chestplate", "minecraft:blast_furnace", "minecraft:smoker"
        ));
        vanilla.put(TIER_2, Set.of(
                "minecraft:diamond", "minecraft:diamond_pickaxe", "minecraft:diamond_sword",
                "minecraft:diamond_chestplate", "minecraft:enchanting_table",
                "minecraft:brewing_stand", "minecraft:anvil"
        ));
        vanilla.put(TIER_3, Set.of(
                "minecraft:blaze_rod", "minecraft:quartz", "minecraft:netherite_scrap",
                "minecraft:ancient_debris", "minecraft:crying_obsidian", "minecraft:ghast_tear"
        ));
        vanilla.put(TIER_4, Set.of(
                "minecraft:netherite_ingot", "minecraft:netherite_pickaxe",
                "minecraft:netherite_sword", "minecraft:netherite_chestplate",
                "minecraft:beacon", "minecraft:end_crystal",
                "minecraft:shulker_shell", "minecraft:chorus_fruit"
        ));
        vanilla.put(TIER_5, Set.of(
                "minecraft:dragon_egg", "minecraft:elytra", "minecraft:heavy_core",
                "minecraft:mace", "minecraft:nether_star"
        ));
        VANILLA_MILESTONES = Collections.unmodifiableMap(vanilla);

        Map<String, IndustrialTier> ic2Map = new HashMap<>();
        for (Map.Entry<IndustrialTier, Set<String>> entry : ic2.entrySet()) {
            for (String id : entry.getValue()) {
                ic2Map.merge(id, entry.getKey(), IndustrialTier::max);
            }
        }
        IC2_LOOKUP = Collections.unmodifiableMap(ic2Map);

        Map<String, IndustrialTier> vanMap = new HashMap<>();
        for (Map.Entry<IndustrialTier, Set<String>> entry : vanilla.entrySet()) {
            for (String id : entry.getValue()) {
                vanMap.merge(id, entry.getKey(), IndustrialTier::max);
            }
        }
        VANILLA_LOOKUP = Collections.unmodifiableMap(vanMap);

        Map<String, IndustrialTier> ic2Tags = new LinkedHashMap<>();
        ic2Tags.put("#plates/copper", TIER_1);
        ic2Tags.put("#plates/tin", TIER_1);
        ic2Tags.put("#plates/bronze", TIER_1);
        ic2Tags.put("#circuits/basic", TIER_1);
        ic2Tags.put("#cables/copper", TIER_1);
        ic2Tags.put("#batteries", TIER_1);
        ic2Tags.put("#ingots/steel", TIER_2);
        ic2Tags.put("#circuits/advanced", TIER_2);
        ic2Tags.put("#plates/advanced_alloy", TIER_3);
        ic2Tags.put("#ingots/uranium", TIER_4);
        ic2Tags.put("#ingots/iridium", TIER_5);
        IC2_TAG_MILESTONES = Collections.unmodifiableMap(ic2Tags);

        Map<String, IndustrialTier> vanTags = new LinkedHashMap<>();
        vanTags.put("#ingots/iron", TIER_1);
        vanTags.put("#gems/diamond", TIER_2);
        vanTags.put("#rods/blaze", TIER_3);
        vanTags.put("#gems/quartz", TIER_3);
        vanTags.put("#ingots/netherite", TIER_4);
        VANILLA_TAG_MILESTONES = Collections.unmodifiableMap(vanTags);
    }

    private final int level;
    private final String key;

    IndustrialTier(int level, String key) {
        this.level = level;
        this.key = key;
    }

    /**
     * Returns the numeric tier index ({@code 0..5}).
     *
     * @return tier index
     */
    public int level() {
        return level;
    }

    /**
     * Returns the lowercase translation/serialization key.
     *
     * @return tier key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the milestone item/block IDs for this tier in IC2 mode or Vanilla-only mode.
     *
     * @param ic2Loaded whether IC2 is installed
     * @return unmodifiable set of milestone item/block IDs
     */
    public Set<String> milestoneItemIds(boolean ic2Loaded) {
        return ic2Loaded ? IC2_MILESTONES.get(this) : VANILLA_MILESTONES.get(this);
    }

    /**
     * Returns the tier corresponding to {@code level}, clamped to {@code [0, 5]}.
     *
     * @param level numeric tier level
     * @return matching {@link IndustrialTier}
     */
    public static IndustrialTier fromLevel(int level) {
        if (level <= 0) {
            return TIER_0;
        }
        if (level >= 5) {
            return TIER_5;
        }
        return values()[level];
    }

    /**
     * Parses a tier from enum name (e.g. {@code "TIER_3"}), numeric string ({@code "3"}), or key.
     *
     * @param raw tier spec
     * @return matching tier, defaulting to {@link #TIER_0}
     */
    public static IndustrialTier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TIER_0;
        }
        String trimmed = raw.trim();
        for (IndustrialTier t : values()) {
            if (t.name().equalsIgnoreCase(trimmed) || t.key.equalsIgnoreCase(trimmed)) {
                return t;
            }
        }
        try {
            return fromLevel(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            return TIER_0;
        }
    }

    /**
     * Returns the higher of two industrial tiers.
     *
     * @param a first tier (may be {@code null})
     * @param b second tier (may be {@code null})
     * @return higher tier (never {@code null})
     */
    public static IndustrialTier max(IndustrialTier a, IndustrialTier b) {
        if (a == null) {
            return b == null ? TIER_0 : b;
        }
        if (b == null) {
            return a;
        }
        return a.level >= b.level ? a : b;
    }

    /**
     * Detects the industrial tier unlocked by owning/crafting/placing a single item or block ID.
     *
     * @param itemId item or block registry ID
     * @param ic2Loaded whether IC2 is installed
     * @return detected tier ({@link #TIER_0} if not a milestone)
     */
    public static IndustrialTier detectFromItemId(String itemId, boolean ic2Loaded) {
        return detectFromItemId(itemId, ic2Loaded, null);
    }

    /**
     * Detects the industrial tier unlocked by owning/crafting/placing a single item or block ID,
     * checking both exact milestone IDs and common item tags when {@code tagMatcher} is provided.
     *
     * @param itemId item or block registry ID
     * @param ic2Loaded whether IC2 is installed
     * @param tagMatcher optional platform tag matcher (may be {@code null})
     * @return detected tier ({@link #TIER_0} if not a milestone)
     */
    public static IndustrialTier detectFromItemId(
            String itemId,
            boolean ic2Loaded,
            MintIngredient.TagMatcher tagMatcher) {
        if (itemId == null || itemId.isBlank()) {
            return TIER_0;
        }
        String normalized = itemId.trim().toLowerCase(Locale.ROOT);
        IndustrialTier builtIn = (ic2Loaded ? IC2_LOOKUP : VANILLA_LOOKUP).getOrDefault(normalized, TIER_0);
        if (tagMatcher != null) {
            Map<String, IndustrialTier> tagMap = ic2Loaded ? IC2_TAG_MILESTONES : VANILLA_TAG_MILESTONES;
            for (Map.Entry<String, IndustrialTier> e : tagMap.entrySet()) {
                if (e.getValue().level > builtIn.level
                        && CommonTagAliases.matchesTagSpec(normalized, e.getKey(), tagMatcher)) {
                    builtIn = e.getValue();
                    if (builtIn == TIER_5) {
                        break;
                    }
                }
            }
        }
        IndustrialTier custom = com.tycooncraft.tyc2.api.TyC2RegistryApi.resolveIndustrialTier(normalized, ic2Loaded);
        return max(builtIn, custom);
    }

    /**
     * Evaluates the highest industrial tier represented across a collection of item/block IDs.
     *
     * @param itemIds item or block IDs owned/crafted by a player
     * @param ic2Loaded whether IC2 is installed
     * @return highest detected tier
     */
    public static IndustrialTier evaluateHighestTier(Collection<String> itemIds, boolean ic2Loaded) {
        return evaluateHighestTier(itemIds, ic2Loaded, null);
    }

    /**
     * Evaluates the highest industrial tier represented across a collection of item/block IDs,
     * checking both exact IDs and common item tags.
     *
     * @param itemIds item or block IDs owned/crafted by a player
     * @param ic2Loaded whether IC2 is installed
     * @param tagMatcher optional platform tag matcher (may be {@code null})
     * @return highest detected tier
     */
    public static IndustrialTier evaluateHighestTier(
            Collection<String> itemIds,
            boolean ic2Loaded,
            MintIngredient.TagMatcher tagMatcher) {
        IndustrialTier best = TIER_0;
        if (itemIds == null) {
            return best;
        }
        for (String id : itemIds) {
            best = max(best, detectFromItemId(id, ic2Loaded, tagMatcher));
            if (best == TIER_5) {
                break;
            }
        }
        return best;
    }

    /**
     * Maps a village level (1..10) to its natural target industrial tier (0..5).
     *
     * @param villageLevel village level {@code 1..10}
     * @return natural target tier {@code 0..5}
     */
    public static int villageTargetTier(int villageLevel) {
        if (villageLevel <= 2) {
            return 0;
        }
        if (villageLevel <= 4) {
            return 1;
        }
        if (villageLevel <= 6) {
            return 2;
        }
        if (villageLevel <= 8) {
            return 3;
        }
        if (villageLevel == 9) {
            return 4;
        }
        return 5;
    }

    /**
     * Computes the effective maximum item industrial tier unlocked for a village's trade orders.
     *
     * <p>When {@code enforceCap} is enabled:
     * {@code min(villageTargetTier, max(0, playerTier - 1))}, with the endgame exception that
     * at {@code playerTier == TIER_5} and {@code villageLevel >= 10}, Tier 5 endgame goods unlock.
     *
     * @param villageLevel village level (1..10)
     * @param playerTier player or company highest industrial tier
     * @param enforceCap whether {@code enforceIndustrialTierCap} config is enabled
     * @return effective unlocked tier level (0..5)
     */
    public static int effectiveUnlockedTier(int villageLevel, IndustrialTier playerTier, boolean enforceCap) {
        int target = villageTargetTier(villageLevel);
        if (!enforceCap) {
            return target;
        }
        IndustrialTier safeTier = playerTier == null ? TIER_0 : playerTier;
        if (safeTier.level >= 5 && villageLevel >= 10) {
            return 5;
        }
        return Math.min(target, Math.max(0, safeTier.level - 1));
    }

    /**
     * Pure-Java tracker mapping player UUIDs to their highest achieved {@link IndustrialTier},
     * with company-wide max aggregation and JSON/Map persistence.
     */
    public static final class Tracker {
        private final Map<UUID, IndustrialTier> playerTiers = new LinkedHashMap<>();

        /**
         * Returns the tracked tier for a player (default {@link #TIER_0}).
         *
         * @param playerUuid player UUID
         * @return current tier
         */
        public IndustrialTier getTier(UUID playerUuid) {
            return playerUuid == null ? TIER_0 : playerTiers.getOrDefault(playerUuid, TIER_0);
        }

        /**
         * Monotonically promotes a player's tracked tier if {@code candidate} is higher.
         *
         * @param playerUuid player UUID
         * @param candidate candidate tier
         * @return {@code true} if the player's tier increased
         */
        public boolean recordTier(UUID playerUuid, IndustrialTier candidate) {
            if (playerUuid == null || candidate == null) {
                return false;
            }
            IndustrialTier current = getTier(playerUuid);
            if (candidate.level > current.level) {
                playerTiers.put(playerUuid, candidate);
                return true;
            }
            return false;
        }

        /**
         * Inspects an item/block ID acquired or placed by a player and promotes their tier if applicable.
         *
         * @param playerUuid player UUID
         * @param itemId item or block registry ID
         * @param ic2Loaded whether IC2 is installed
         * @return {@code true} if the player's tier increased
         */
        public boolean recordItemMilestone(UUID playerUuid, String itemId, boolean ic2Loaded) {
            return recordItemMilestone(playerUuid, itemId, ic2Loaded, null);
        }

        /**
         * Inspects an item/block ID acquired or placed by a player (including common item tags) and
         * promotes their tier if applicable.
         *
         * @param playerUuid player UUID
         * @param itemId item or block registry ID
         * @param ic2Loaded whether IC2 is installed
         * @param tagMatcher optional platform tag matcher (may be {@code null})
         * @return {@code true} if the player's tier increased
         */
        public boolean recordItemMilestone(
                UUID playerUuid,
                String itemId,
                boolean ic2Loaded,
                MintIngredient.TagMatcher tagMatcher) {
            return recordTier(playerUuid, detectFromItemId(itemId, ic2Loaded, tagMatcher));
        }

        /**
         * Computes the effective tier for a group of players (e.g., all members of a Company).
         *
         * @param memberUuids member UUIDs
         * @return highest tier among all members
         */
        public IndustrialTier getMaxTier(Collection<UUID> memberUuids) {
            IndustrialTier best = TIER_0;
            if (memberUuids == null) {
                return best;
            }
            for (UUID member : memberUuids) {
                best = max(best, getTier(member));
            }
            return best;
        }

        /**
         * Exports tracked tiers to a pure-Java map suitable for JSON/NBT storage.
         *
         * @return map of UUID string to integer tier level
         */
        public Map<String, Object> toDataMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<UUID, IndustrialTier> entry : playerTiers.entrySet()) {
                map.put(entry.getKey().toString(), entry.getValue().level());
            }
            return map;
        }

        /**
         * Restores a {@link Tracker} from a data map.
         *
         * @param map persisted map
         * @return restored tracker
         */
        public static Tracker fromDataMap(Map<String, Object> map) {
            Tracker tracker = new Tracker();
            if (map == null) {
                return tracker;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    if (entry.getValue() instanceof Number) {
                        tracker.playerTiers.put(uuid, fromLevel(((Number) entry.getValue()).intValue()));
                    } else if (entry.getValue() instanceof String) {
                        tracker.playerTiers.put(uuid, parse((String) entry.getValue()));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed UUID keys gracefully
                }
            }
            return tracker;
        }
    }
}
