package com.tycooncraft.tyc2.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ingredient specification for a {@link MintRecipe}, supporting either an exact item ID
 * (such as {@code "minecraft:diamond"}) or a tag specification (such as {@code "#ingots/iron"},
 * which automatically matches both {@code c:ingots/iron} and {@code forge:ingots/iron}).
 */
public final class MintIngredient {

    /**
     * Platform callback checking whether a given item ID belongs to a given tag ID
     * (without leading {@code '#'}, e.g. {@code "c:ingots/iron"} or {@code "forge:ingots/iron"}).
     */
    @FunctionalInterface
    public interface TagMatcher {
        /**
         * Returns {@code true} if {@code itemId} has {@code tagId}.
         *
         * @param itemId item identifier
         * @param tagId tag identifier without leading {@code '#'}
         * @return {@code true} if matched
         */
        boolean hasTag(String itemId, String tagId);
    }

    /** Fallback item IDs matched directly for common metal/ingot tags when no platform tag registry is queried. */
    private static final Map<String, Set<String>> BUILTIN_TAG_FALLBACKS;

    static {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("ingots/copper", Set.of("minecraft:copper_ingot", "ic2:copper_ingot"));
        m.put("ingots/tin", Set.of("ic2:tin_ingot"));
        m.put("ingots/iron", Set.of("minecraft:iron_ingot"));
        m.put("ingots/bronze", Set.of("ic2:bronze_ingot"));
        m.put("ingots/silver", Set.of("ic2:silver_ingot"));
        m.put("ingots/gold", Set.of("minecraft:gold_ingot"));
        m.put("ingots/steel", Set.of("ic2:steel_ingot", "ic2:refined_iron_ingot"));
        m.put("ingots/lead", Set.of("ic2:lead_ingot"));
        m.put("ingots/netherite", Set.of("minecraft:netherite_ingot"));
        m.put("gems/diamond", Set.of("minecraft:diamond"));
        m.put("gems/emerald", Set.of("minecraft:emerald"));
        BUILTIN_TAG_FALLBACKS = Collections.unmodifiableMap(m);
    }

    private final String spec;
    private final boolean isTag;
    private final int count;
    private final List<String> expandedTagIds;
    private final String normalizedTagPath;

    private MintIngredient(String spec, boolean isTag, int count) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Mint ingredient spec must not be blank");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Mint ingredient count must be positive: " + count);
        }
        String trimmed = spec.trim().toLowerCase(Locale.ROOT);
        this.spec = isTag && !trimmed.startsWith("#") ? "#" + trimmed : trimmed;
        this.isTag = isTag || trimmed.startsWith("#");
        this.count = count;

        if (this.isTag) {
            String rawTag = this.spec.substring(1);
            List<String> tags = new ArrayList<>(2);
            if (rawTag.contains(":")) {
                tags.add(rawTag);
                int colon = rawTag.indexOf(':');
                String ns = rawTag.substring(0, colon);
                String path = rawTag.substring(colon + 1);
                this.normalizedTagPath = path;
                if ("c".equals(ns)) {
                    tags.add("forge:" + path);
                } else if ("forge".equals(ns)) {
                    tags.add("c:" + path);
                }
            } else {
                this.normalizedTagPath = rawTag;
                tags.add("c:" + rawTag);
                tags.add("forge:" + rawTag);
            }
            this.expandedTagIds = Collections.unmodifiableList(tags);
        } else {
            this.normalizedTagPath = null;
            this.expandedTagIds = Collections.emptyList();
        }
    }

    /**
     * Creates an exact-item ingredient.
     *
     * @param itemId item identifier (e.g. {@code "minecraft:diamond"})
     * @param count required item count (>= 1)
     * @return exact item ingredient
     */
    public static MintIngredient ofItem(String itemId, int count) {
        return new MintIngredient(itemId, false, count);
    }

    /**
     * Creates a tag-based ingredient (e.g. {@code "#ingots/iron"} or {@code "ingots/iron"}).
     * Unnamespaced tags expand to both {@code c:<path>} and {@code forge:<path>}.
     *
     * @param tagSpec tag specification with or without leading {@code '#'}
     * @param count required item count (>= 1)
     * @return tag ingredient
     */
    public static MintIngredient ofTag(String tagSpec, int count) {
        return new MintIngredient(tagSpec, true, count);
    }

    /**
     * Parses a spec string: leading {@code '#'} denotes a tag, otherwise an exact item ID.
     *
     * @param spec item or {@code #tag} specification
     * @param count required item count (>= 1)
     * @return parsed ingredient
     */
    public static MintIngredient ofSpec(String spec, int count) {
        if (spec != null && spec.trim().startsWith("#")) {
            return ofTag(spec, count);
        }
        return ofItem(spec, count);
    }

    /** @return raw spec string (with leading {@code '#'} for tags) */
    public String spec() {
        return spec;
    }

    /** @return {@code true} if this ingredient matches via item tags */
    public boolean isTag() {
        return isTag;
    }

    /** @return required stack count */
    public int count() {
        return count;
    }

    /**
     * Returns expanded tag IDs without leading {@code '#'} (e.g. {@code ["c:ingots/iron", "forge:ingots/iron"]}).
     * Empty for exact-item ingredients.
     *
     * @return unmodifiable list of tag IDs
     */
    public List<String> expandedTagIds() {
        return expandedTagIds;
    }

    /**
     * Returns a display-friendly fallback item ID for JEI or tooltips when no platform tag query is available.
     *
     * @return concrete item ID or the spec string
     */
    public String representativeItemId() {
        if (!isTag) {
            return spec;
        }
        Set<String> fallback = BUILTIN_TAG_FALLBACKS.get(normalizedTagPath);
        if (fallback != null && !fallback.isEmpty()) {
            return fallback.iterator().next();
        }
        return spec;
    }

    /**
     * Checks whether the given item ID and stack size satisfy this ingredient.
     *
     * @param itemId item ID in slot (may be {@code null} or blank if empty)
     * @param availableCount item count in slot
     * @param tagMatcher optional platform tag matcher (may be {@code null})
     * @return {@code true} if item matches and {@code availableCount >= count}
     */
    public boolean matches(String itemId, int availableCount, TagMatcher tagMatcher) {
        if (availableCount < count) {
            return false;
        }
        return matchesItem(itemId, tagMatcher);
    }

    /**
     * Checks whether the given item ID matches this ingredient regardless of stack count.
     *
     * @param itemId item ID to test
     * @param tagMatcher optional platform tag matcher (may be {@code null})
     * @return {@code true} if matched
     */
    public boolean matchesItem(String itemId, TagMatcher tagMatcher) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String normalized = itemId.trim().toLowerCase(Locale.ROOT);
        if (!isTag) {
            return spec.equals(normalized);
        }
        if (tagMatcher != null) {
            for (String tagId : expandedTagIds) {
                if (tagMatcher.hasTag(normalized, tagId)) {
                    return true;
                }
            }
        }
        Set<String> fallback = BUILTIN_TAG_FALLBACKS.get(normalizedTagPath);
        return fallback != null && fallback.contains(normalized);
    }

    /**
     * Serializes this ingredient into a JSON-compatible map.
     *
     * @return ordered key-value map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (isTag) {
            map.put("tag", spec);
        } else {
            map.put("item", spec);
        }
        map.put("count", count);
        return map;
    }

    /**
     * Deserializes a {@link MintIngredient} from a JSON map ({@code item} or {@code tag} plus optional {@code count}).
     *
     * @param map source map
     * @return parsed ingredient
     */
    public static MintIngredient fromDataMap(Map<String, Object> map) {
        Objects.requireNonNull(map, "ingredient map must not be null");
        int count = Math.max(1, JsonLite.getInt(map, "count", 1));
        String tag = JsonLite.getString(map, "tag", null);
        if (tag != null && !tag.isBlank()) {
            return ofTag(tag, count);
        }
        String item = JsonLite.getString(map, "item", null);
        if (item != null && !item.isBlank()) {
            return ofSpec(item, count);
        }
        String specVal = JsonLite.getString(map, "spec", null);
        if (specVal != null && !specVal.isBlank()) {
            return ofSpec(specVal, count);
        }
        throw new IllegalArgumentException("MintIngredient JSON must define 'item' or 'tag'");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MintIngredient)) {
            return false;
        }
        MintIngredient that = (MintIngredient) o;
        return isTag == that.isTag && count == that.count && spec.equals(that.spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spec, isTag, count);
    }

    @Override
    public String toString() {
        return count + "x " + spec;
    }
}
