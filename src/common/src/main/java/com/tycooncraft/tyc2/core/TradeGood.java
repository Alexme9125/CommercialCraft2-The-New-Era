package com.tycooncraft.tyc2.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable catalog definition of a tradeable item in the village economy.
 *
 * <p>Supports exact item IDs, optional cross-mod tags, mod gates, village level bands,
 * player industrial tier gating, harvest seasons, biome affinities, sell/buy direction flags,
 * base order quantity bounds, and optional enchantment specifications (for enchanted books).
 */
public final class TradeGood {

    private final String id;
    private final String itemId;
    private final String optionalTag;
    private final String requiredModId;
    private final GoodsCategory category;
    private final long basePrice;
    private final int minLevel;
    private final int maxLevel;
    private final IndustrialTier industrialTierRequired;
    private final Set<Season> harvestSeasons;
    private final Set<String> biomeAffinities;
    private final boolean biomeExclusive;
    private final Set<String> demandBiomes;
    private final Set<Season> demandSeasons;
    private final boolean sellableByVillage;
    private final boolean buyableByVillage;
    private final int baseMinQty;
    private final int baseMaxQty;
    private final String optionalEnchantmentSpec;

    private TradeGood(Builder b) {
        if (b.id == null || b.id.isBlank()) {
            throw new IllegalArgumentException("TradeGood id must not be blank");
        }
        if (b.itemId == null || b.itemId.isBlank()) {
            throw new IllegalArgumentException("TradeGood itemId must not be blank: " + b.id);
        }
        if (b.basePrice <= 0L) {
            throw new IllegalArgumentException("TradeGood basePrice must be positive: " + b.id);
        }
        if (b.minLevel < 1 || b.maxLevel > 10 || b.minLevel > b.maxLevel) {
            throw new IllegalArgumentException("TradeGood level bounds invalid for " + b.id + ": " + b.minLevel + ".." + b.maxLevel);
        }
        if (b.baseMinQty < 1 || b.baseMaxQty < b.baseMinQty) {
            throw new IllegalArgumentException("TradeGood quantity bounds invalid for " + b.id + ": " + b.baseMinQty + ".." + b.baseMaxQty);
        }
        this.id = b.id.trim();
        this.itemId = b.itemId.trim().toLowerCase(Locale.ROOT);
        this.optionalTag = (b.optionalTag == null || b.optionalTag.isBlank()) ? null : b.optionalTag.trim().toLowerCase(Locale.ROOT);
        this.requiredModId = (b.requiredModId == null || b.requiredModId.isBlank()) ? null : b.requiredModId.trim().toLowerCase(Locale.ROOT);
        this.category = Objects.requireNonNull(b.category, "category must not be null");
        this.basePrice = b.basePrice;
        this.minLevel = b.minLevel;
        this.maxLevel = b.maxLevel;
        this.industrialTierRequired = b.industrialTierRequired == null ? IndustrialTier.TIER_0 : b.industrialTierRequired;

        EnumSet<Season> cleanedSeasons = EnumSet.noneOf(Season.class);
        if (b.harvestSeasons != null) {
            for (Season s : b.harvestSeasons) {
                if (s != null && s.isActive()) {
                    cleanedSeasons.add(s);
                }
            }
        }
        this.harvestSeasons = Collections.unmodifiableSet(cleanedSeasons);

        Set<String> biomes = new LinkedHashSet<>();
        if (b.biomeAffinities != null) {
            for (String biome : b.biomeAffinities) {
                if (biome != null && !biome.isBlank()) {
                    biomes.add(biome.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.biomeAffinities = Collections.unmodifiableSet(biomes);
        this.biomeExclusive = b.biomeExclusive;

        Set<String> dBiomes = new LinkedHashSet<>();
        if (b.demandBiomes != null) {
            for (String biome : b.demandBiomes) {
                if (biome != null && !biome.isBlank()) {
                    dBiomes.add(biome.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.demandBiomes = Collections.unmodifiableSet(dBiomes);

        EnumSet<Season> dSeasons = EnumSet.noneOf(Season.class);
        if (b.demandSeasons != null) {
            for (Season s : b.demandSeasons) {
                if (s != null && s.isActive()) {
                    dSeasons.add(s);
                }
            }
        }
        this.demandSeasons = Collections.unmodifiableSet(dSeasons);

        this.sellableByVillage = b.sellableByVillage;
        this.buyableByVillage = b.buyableByVillage;
        this.baseMinQty = b.baseMinQty;
        this.baseMaxQty = b.baseMaxQty;
        this.optionalEnchantmentSpec = (b.optionalEnchantmentSpec == null || b.optionalEnchantmentSpec.isBlank())
                ? null
                : b.optionalEnchantmentSpec.trim();
    }

    /**
     * Creates a new builder for a {@link TradeGood}.
     *
     * @param id unique catalog entry ID
     * @param itemId item registry ID
     * @param category economic category
     * @param basePrice base unit price in IC
     * @return builder instance
     */
    public static Builder builder(String id, String itemId, GoodsCategory category, long basePrice) {
        return new Builder(id, itemId, category, basePrice);
    }

    /** @return unique catalog ID */
    public String id() {
        return id;
    }

    /** @return item registry ID */
    public String itemId() {
        return itemId;
    }

    /** @return optional tag specification (e.g. {@code "#ingots/iron"}), or {@code null} */
    public String optionalTag() {
        return optionalTag;
    }

    /** @return optional required mod ID (e.g. {@code "ic2"}), or {@code null} */
    public String requiredModId() {
        return requiredModId;
    }

    /** @return goods category */
    public GoodsCategory category() {
        return category;
    }

    /** @return base reference unit price in IC */
    public long basePrice() {
        return basePrice;
    }

    /** @return minimum village level (1..10) */
    public int minLevel() {
        return minLevel;
    }

    /** @return maximum village level (1..10) */
    public int maxLevel() {
        return maxLevel;
    }

    /** @return required unlocked industrial tier */
    public IndustrialTier industrialTierRequired() {
        return industrialTierRequired;
    }

    /** @return harvest seasons (empty or all 4 = year-round; 1..3 seasons = seasonal crop) */
    public Set<Season> harvestSeasons() {
        return harvestSeasons;
    }

    /** @return biome affinity keys (e.g. {@code "plains"}, {@code "desert"}, {@code "taiga"}) */
    public Set<String> biomeAffinities() {
        return biomeAffinities;
    }

    /** @return {@code true} if this good is geographically restricted to matching biome(s) */
    public boolean isBiomeExclusive() {
        return biomeExclusive;
    }

    /** @return biome keys with heightened local buy demand for this good */
    public Set<String> demandBiomes() {
        return demandBiomes;
    }

    /** @return calendar seasons during which non-crop demand for this good is heightened */
    public Set<Season> demandSeasons() {
        return demandSeasons;
    }

    /** @return {@code true} if villages can generate Sell Orders (village sells to player) */
    public boolean isSellableByVillage() {
        return sellableByVillage;
    }

    /** @return {@code true} if villages can generate Buy Orders (village buys from player) */
    public boolean isBuyableByVillage() {
        return buyableByVillage;
    }

    /** @return base minimum quantity per order at level 1 */
    public int baseMinQty() {
        return baseMinQty;
    }

    /** @return base maximum quantity per order at level 1 */
    public int baseMaxQty() {
        return baseMaxQty;
    }

    /** @return optional enchantment spec (e.g. {@code "minecraft:mending:1"}), or {@code null} */
    public String optionalEnchantmentSpec() {
        return optionalEnchantmentSpec;
    }

    /**
     * Returns {@code true} if this good has restricted harvest seasons (1 to 3 seasons out of 4).
     *
     * @return {@code true} for seasonal crops
     */
    public boolean isSeasonalCrop() {
        return !harvestSeasons.isEmpty() && harvestSeasons.size() < 4;
    }

    /**
     * Checks whether this good is currently in harvest season (or year-round / season inactive).
     *
     * @param season current season
     * @return {@code true} if in season
     */
    public boolean isInSeason(Season season) {
        if (season == null || !season.isActive() || harvestSeasons.isEmpty()) {
            return true;
        }
        return harvestSeasons.contains(season);
    }

    /**
     * Checks whether this good is strictly out of season under an active season calendar.
     *
     * @param season current season
     * @return {@code true} if season is active and this good does not grow in {@code season}
     */
    public boolean isOffSeason(Season season) {
        return season != null && season.isActive() && !harvestSeasons.isEmpty() && !harvestSeasons.contains(season);
    }

    /**
     * Checks whether this good is available at the specified village level.
     *
     * @param villageLevel village level (1..10)
     * @return {@code true} if within {@code [minLevel, maxLevel]}
     */
    public boolean matchesLevel(int villageLevel) {
        return villageLevel >= minLevel && villageLevel <= maxLevel;
    }

    /**
     * Checks whether this good has specialty affinity for the given biome identifier
     * (e.g., {@code "minecraft:plains"}, {@code "plains"}, {@code "minecraft:snowy_taiga"}).
     *
     * @param biomeId biome ID or style key
     * @return {@code true} if matched
     */
    public boolean hasBiomeAffinity(String biomeId) {
        return matchesBiomeSet(biomeAffinities, biomeId);
    }

    /**
     * Checks whether the given biome has heightened local buy demand for this good.
     *
     * @param biomeId biome ID or style key
     * @return {@code true} if matched
     */
    public boolean hasDemandBiome(String biomeId) {
        return matchesBiomeSet(demandBiomes, biomeId);
    }

    /**
     * Checks whether this good has heightened non-crop demand during {@code season}.
     * Always returns {@code false} when {@code season} is {@code null} or {@link Season#NONE}.
     *
     * @param season current season
     * @return {@code true} if active season matches {@link #demandSeasons()}
     */
    public boolean hasDemandSeason(Season season) {
        return season != null && season.isActive() && demandSeasons.contains(season);
    }

    private static boolean matchesBiomeSet(Set<String> targetBiomes, String biomeId) {
        if (biomeId == null || biomeId.isBlank() || targetBiomes.isEmpty()) {
            return false;
        }
        String lower = biomeId.trim().toLowerCase(Locale.ROOT);
        if (targetBiomes.contains(lower)) {
            return true;
        }
        String normalizedStyle = normalizeBiomeStyle(lower);
        return normalizedStyle != null && targetBiomes.contains(normalizedStyle);
    }

    /**
     * Normalizes a Minecraft biome ID into one of the 7 village architectural biome keys
     * ({@code plains, desert, savanna, taiga, snowy, jungle, swamp}) when recognizable.
     *
     * @param biomeId biome ID string
     * @return normalized biome key or bare path
     */
    public static String normalizeBiomeStyle(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return null;
        }
        String lower = biomeId.trim().toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen")
                || path.contains("glacier") || path.contains("tundra")
                || path.equals("grove") || path.equals("jagged_peaks")) {
            return "snowy";
        }
        if (path.contains("desert") || path.contains("badlands") || path.contains("mesa") || path.contains("dune")) {
            return "desert";
        }
        if (path.contains("savanna")) {
            return "savanna";
        }
        if (path.contains("jungle") || path.contains("bamboo") || path.contains("rainforest")) {
            return "jungle";
        }
        if (path.contains("swamp") || path.contains("mangrove") || path.contains("marsh")
                || path.contains("bog") || path.contains("bayou")) {
            return "swamp";
        }
        if (path.contains("taiga") || path.contains("spruce") || path.contains("pine")
                || path.contains("boreal") || path.contains("redwood") || path.contains("windswept")) {
            return "taiga";
        }
        if (path.contains("plains") || path.contains("meadow") || path.contains("forest")) {
            return "plains";
        }
        return path;
    }

    /**
     * Returns a copy of this good with replaced harvest seasons (used by seasonal datapack overlays).
     *
     * @param newSeasons replacement harvest seasons
     * @return updated {@link TradeGood}
     */
    public TradeGood withHarvestSeasons(Collection<Season> newSeasons) {
        return new Builder(id, itemId, category, basePrice)
                .optionalTag(optionalTag)
                .requiredModId(requiredModId)
                .levelRange(minLevel, maxLevel)
                .industrialTier(industrialTierRequired)
                .harvestSeasons(newSeasons)
                .biomeAffinities(biomeAffinities)
                .biomeExclusive(biomeExclusive)
                .demandBiomes(demandBiomes)
                .demandSeasons(demandSeasons)
                .sellable(sellableByVillage)
                .buyable(buyableByVillage)
                .quantityRange(baseMinQty, baseMaxQty)
                .enchantmentSpec(optionalEnchantmentSpec)
                .build();
    }

    /**
     * Serializes this good into a JSON-compatible map.
     *
     * @return ordered map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("itemId", itemId);
        if (optionalTag != null) {
            map.put("optionalTag", optionalTag);
        }
        if (requiredModId != null) {
            map.put("requiredModId", requiredModId);
        }
        map.put("category", category.name());
        map.put("basePrice", basePrice);
        map.put("minLevel", minLevel);
        map.put("maxLevel", maxLevel);
        map.put("industrialTierRequired", industrialTierRequired.name());
        if (!harvestSeasons.isEmpty()) {
            List<String> seasons = new ArrayList<>(harvestSeasons.size());
            for (Season s : harvestSeasons) {
                seasons.add(s.name());
            }
            map.put("harvestSeasons", seasons);
        }
        if (!biomeAffinities.isEmpty()) {
            map.put("biomeAffinities", new ArrayList<>(biomeAffinities));
        }
        if (biomeExclusive) {
            map.put("biomeExclusive", true);
        }
        if (!demandBiomes.isEmpty()) {
            map.put("demandBiomes", new ArrayList<>(demandBiomes));
        }
        if (!demandSeasons.isEmpty()) {
            List<String> dSeasons = new ArrayList<>(demandSeasons.size());
            for (Season s : demandSeasons) {
                dSeasons.add(s.name());
            }
            map.put("demandSeasons", dSeasons);
        }
        map.put("isSellableByVillage", sellableByVillage);
        map.put("isBuyableByVillage", buyableByVillage);
        map.put("baseMinQty", baseMinQty);
        map.put("baseMaxQty", baseMaxQty);
        if (optionalEnchantmentSpec != null) {
            map.put("optionalEnchantmentSpec", optionalEnchantmentSpec);
        }
        return map;
    }

    /**
     * Deserializes a {@link TradeGood} from a JSON map, accepting both canonical field names
     * and concise datapack aliases.
     *
     * @param map JSON map
     * @return parsed {@link TradeGood}
     */
    public static TradeGood fromDataMap(Map<String, Object> map) {
        Objects.requireNonNull(map, "TradeGood map must not be null");
        String itemId = JsonLite.getString(map, "itemId", JsonLite.getString(map, "item", null));
        String id = JsonLite.getString(map, "id", itemId);
        if (id == null || id.isBlank() || itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("TradeGood requires 'id' and 'itemId'");
        }

        String catStr = JsonLite.getString(map, "category", GoodsCategory.AGRICULTURE.name());
        GoodsCategory category = GoodsCategory.valueOf(catStr.trim().toUpperCase(Locale.ROOT));
        long basePrice = Math.max(1L, JsonLite.getLong(map, "basePrice", 10L));
        int minLevel = Math.max(1, Math.min(10, JsonLite.getInt(map, "minLevel", 1)));
        int maxLevel = Math.max(minLevel, Math.min(10, JsonLite.getInt(map, "maxLevel", 10)));

        Object tierObj = map.containsKey("industrialTierRequired")
                ? map.get("industrialTierRequired")
                : map.get("industrialTier");
        IndustrialTier tier = IndustrialTier.TIER_0;
        if (tierObj instanceof Number) {
            tier = IndustrialTier.fromLevel(((Number) tierObj).intValue());
        } else if (tierObj instanceof String) {
            tier = IndustrialTier.parse((String) tierObj);
        }

        List<Season> seasons = new ArrayList<>();
        for (String s : JsonLite.getStringList(map, map.containsKey("harvestSeasons") ? "harvestSeasons" : "seasons")) {
            if ("ALL".equalsIgnoreCase(s)) {
                seasons.addAll(Season.allFourSeasons());
            } else {
                Season parsed = Season.parse(s);
                if (parsed.isActive()) {
                    seasons.add(parsed);
                }
            }
        }

        List<String> biomes = JsonLite.getStringList(map, map.containsKey("biomeAffinities") ? "biomeAffinities" : "biomes");
        boolean biomeExclusive = JsonLite.getBoolean(map, "biomeExclusive", false);
        List<String> demandBiomes = JsonLite.getStringList(map, "demandBiomes");
        List<Season> demandSeasons = new ArrayList<>();
        for (String s : JsonLite.getStringList(map, "demandSeasons")) {
            if ("ALL".equalsIgnoreCase(s)) {
                demandSeasons.addAll(Season.allFourSeasons());
            } else {
                Season parsed = Season.parse(s);
                if (parsed.isActive()) {
                    demandSeasons.add(parsed);
                }
            }
        }
        boolean sellable = map.containsKey("isSellableByVillage")
                ? JsonLite.getBoolean(map, "isSellableByVillage", true)
                : JsonLite.getBoolean(map, "sellable", true);
        boolean buyable = map.containsKey("isBuyableByVillage")
                ? JsonLite.getBoolean(map, "isBuyableByVillage", false)
                : JsonLite.getBoolean(map, "buyable", false);
        int minQty = Math.max(1, JsonLite.getInt(map, "baseMinQty", JsonLite.getInt(map, "minQty", 4)));
        int maxQty = Math.max(minQty, JsonLite.getInt(map, "baseMaxQty", JsonLite.getInt(map, "maxQty", Math.max(minQty, 16))));

        String tag = JsonLite.getString(map, "optionalTag", JsonLite.getString(map, "tag", null));
        String modId = JsonLite.getString(map, "requiredModId", JsonLite.getString(map, "requiredMod", null));
        String ench = JsonLite.getString(map, "optionalEnchantmentSpec", JsonLite.getString(map, "enchantment", null));

        return builder(id, itemId, category, basePrice)
                .optionalTag(tag)
                .requiredModId(modId)
                .levelRange(minLevel, maxLevel)
                .industrialTier(tier)
                .harvestSeasons(seasons)
                .biomeAffinities(biomes)
                .biomeExclusive(biomeExclusive)
                .demandBiomes(demandBiomes)
                .demandSeasons(demandSeasons)
                .sellable(sellable)
                .buyable(buyable)
                .quantityRange(minQty, maxQty)
                .enchantmentSpec(ench)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TradeGood)) {
            return false;
        }
        TradeGood that = (TradeGood) o;
        return basePrice == that.basePrice
                && minLevel == that.minLevel
                && maxLevel == that.maxLevel
                && biomeExclusive == that.biomeExclusive
                && sellableByVillage == that.sellableByVillage
                && buyableByVillage == that.buyableByVillage
                && baseMinQty == that.baseMinQty
                && baseMaxQty == that.baseMaxQty
                && id.equals(that.id)
                && itemId.equals(that.itemId)
                && Objects.equals(optionalTag, that.optionalTag)
                && Objects.equals(requiredModId, that.requiredModId)
                && category == that.category
                && industrialTierRequired == that.industrialTierRequired
                && harvestSeasons.equals(that.harvestSeasons)
                && biomeAffinities.equals(that.biomeAffinities)
                && demandBiomes.equals(that.demandBiomes)
                && demandSeasons.equals(that.demandSeasons)
                && Objects.equals(optionalEnchantmentSpec, that.optionalEnchantmentSpec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, itemId, optionalTag, requiredModId, category, basePrice,
                minLevel, maxLevel, industrialTierRequired, harvestSeasons, biomeAffinities,
                biomeExclusive, demandBiomes, demandSeasons,
                sellableByVillage, buyableByVillage, baseMinQty, baseMaxQty, optionalEnchantmentSpec);
    }

    @Override
    public String toString() {
        return "TradeGood{" + id + " (" + itemId + "), base=" + basePrice + " IC, Lv " + minLevel + ".." + maxLevel + "}";
    }

    /**
     * Fluent builder for {@link TradeGood}.
     */
    public static final class Builder {
        private final String id;
        private final String itemId;
        private final GoodsCategory category;
        private final long basePrice;
        private String optionalTag;
        private String requiredModId;
        private int minLevel = 1;
        private int maxLevel = 10;
        private IndustrialTier industrialTierRequired = IndustrialTier.TIER_0;
        private Set<Season> harvestSeasons = EnumSet.noneOf(Season.class);
        private Set<String> biomeAffinities = new LinkedHashSet<>();
        private boolean biomeExclusive = false;
        private Set<String> demandBiomes = new LinkedHashSet<>();
        private Set<Season> demandSeasons = EnumSet.noneOf(Season.class);
        private boolean sellableByVillage = true;
        private boolean buyableByVillage = false;
        private int baseMinQty = 4;
        private int baseMaxQty = 16;
        private String optionalEnchantmentSpec;

        Builder(String id, String itemId, GoodsCategory category, long basePrice) {
            this.id = id;
            this.itemId = itemId;
            this.category = category;
            this.basePrice = basePrice;
        }

        /** @param tag optional tag spec */
        public Builder optionalTag(String tag) {
            this.optionalTag = tag;
            return this;
        }

        /** @param modId optional required mod ID */
        public Builder requiredModId(String modId) {
            this.requiredModId = modId;
            return this;
        }

        /** @param min minimum village level (1..10) @param max maximum village level (1..10) */
        public Builder levelRange(int min, int max) {
            this.minLevel = min;
            this.maxLevel = max;
            return this;
        }

        /** @param tier required industrial tier */
        public Builder industrialTier(IndustrialTier tier) {
            this.industrialTierRequired = tier;
            return this;
        }

        /** @param seasons harvest seasons */
        public Builder harvestSeasons(Season... seasons) {
            this.harvestSeasons = EnumSet.noneOf(Season.class);
            if (seasons != null) {
                for (Season s : seasons) {
                    if (s != null && s.isActive()) {
                        this.harvestSeasons.add(s);
                    }
                }
            }
            return this;
        }

        /** @param seasons harvest seasons collection */
        public Builder harvestSeasons(Collection<Season> seasons) {
            this.harvestSeasons = EnumSet.noneOf(Season.class);
            if (seasons != null) {
                for (Season s : seasons) {
                    if (s != null && s.isActive()) {
                        this.harvestSeasons.add(s);
                    }
                }
            }
            return this;
        }

        /** @param biomes biome affinity keys */
        public Builder biomeAffinities(String... biomes) {
            this.biomeAffinities = new LinkedHashSet<>();
            if (biomes != null) {
                Collections.addAll(this.biomeAffinities, biomes);
            }
            return this;
        }

        /** @param biomes biome affinity keys */
        public Builder biomeAffinities(Collection<String> biomes) {
            this.biomeAffinities = new LinkedHashSet<>();
            if (biomes != null) {
                this.biomeAffinities.addAll(biomes);
            }
            return this;
        }

        /** @param exclusive whether this good is geographically restricted to matching biome(s) */
        public Builder biomeExclusive(boolean exclusive) {
            this.biomeExclusive = exclusive;
            return this;
        }

        /** @param biomes biome keys with heightened local buy demand */
        public Builder demandBiomes(String... biomes) {
            this.demandBiomes = new LinkedHashSet<>();
            if (biomes != null) {
                Collections.addAll(this.demandBiomes, biomes);
            }
            return this;
        }

        /** @param biomes biome keys with heightened local buy demand */
        public Builder demandBiomes(Collection<String> biomes) {
            this.demandBiomes = new LinkedHashSet<>();
            if (biomes != null) {
                this.demandBiomes.addAll(biomes);
            }
            return this;
        }

        /** @param seasons active calendar seasons with heightened non-crop demand */
        public Builder demandSeasons(Season... seasons) {
            this.demandSeasons = EnumSet.noneOf(Season.class);
            if (seasons != null) {
                for (Season s : seasons) {
                    if (s != null && s.isActive()) {
                        this.demandSeasons.add(s);
                    }
                }
            }
            return this;
        }

        /** @param seasons active calendar seasons collection with heightened non-crop demand */
        public Builder demandSeasons(Collection<Season> seasons) {
            this.demandSeasons = EnumSet.noneOf(Season.class);
            if (seasons != null) {
                for (Season s : seasons) {
                    if (s != null && s.isActive()) {
                        this.demandSeasons.add(s);
                    }
                }
            }
            return this;
        }

        /** @param sellable whether villages can sell this item */
        public Builder sellable(boolean sellable) {
            this.sellableByVillage = sellable;
            return this;
        }

        /** @param buyable whether villages can buy this item */
        public Builder buyable(boolean buyable) {
            this.buyableByVillage = buyable;
            return this;
        }

        /** @param min base min quantity @param max base max quantity */
        public Builder quantityRange(int min, int max) {
            this.baseMinQty = min;
            this.baseMaxQty = max;
            return this;
        }

        /** @param spec enchantment spec (e.g. {@code "minecraft:mending:1"}) */
        public Builder enchantmentSpec(String spec) {
            this.optionalEnchantmentSpec = spec;
            return this;
        }

        /**
         * Builds the immutable {@link TradeGood}.
         *
         * @return constructed trade good
         */
        public TradeGood build() {
            return new TradeGood(this);
        }
    }
}
