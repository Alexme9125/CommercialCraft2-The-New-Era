package com.tycooncraft.tyc2.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Registry of all {@link MintRecipe} definitions, pre-populated with all built-in
 * Vanilla, common-tag, and IC2 minting recipes from the specification, and extensible
 * via datapack JSON, {@code config/tyc2/custom_mint_recipes.json}, and Java API calls.
 */
public final class MintRecipeRegistry {

    /**
     * Matched recipe result indicating how many items to consume from each of the
     * Minting Machine's two input slots.
     *
     * @param recipe matched minting recipe
     * @param consumeSlot0 count to consume from input slot 0
     * @param consumeSlot1 count to consume from input slot 1
     */
    public record MatchResult(MintRecipe recipe, int consumeSlot0, int consumeSlot1) {
    }

    private final Map<String, MintRecipe> recipes = new LinkedHashMap<>();

    /**
     * Creates an empty registry. Use {@link #createDefault()} to include built-in recipes.
     */
    public MintRecipeRegistry() {
    }

    /**
     * Creates a registry populated with all built-in Vanilla, common-tag, and IC2 recipes.
     *
     * @return registry with default recipes
     */
    public static MintRecipeRegistry createDefault() {
        MintRecipeRegistry registry = new MintRecipeRegistry();
        registry.registerBuiltInDefaults();
        return registry;
    }

    /**
     * Populates all built-in metal/alloy minting recipes defined in DESIGN.md Section 2.2.
     *
     * <p>Only the 6 pure-Vanilla low-to-mid recipes ({@code 1x..100x}: Copper, Iron, 2x Iron,
     * Gold, Gold+Iron, 2x Gold+Emerald) are marked {@code primitiveAllowed = true}.
     * All {@code 200x..1000x} recipes and all IC2/modded metal/alloy recipes require the
     * electric Industrial Minting Machine ({@code primitiveAllowed = false}).
     */
    public void registerBuiltInDefaults() {
        // 1x denomination
        register(single("tyc2:mint_copper_1", "#ingots/copper", 1, CurrencyDenomination.X1, 2, 300, 60, null, true));
        register(single("tyc2:mint_tin_1", "#ingots/tin", 1, CurrencyDenomination.X1, 2, 300, 60, null, false));

        // 5x denomination
        register(single("tyc2:mint_iron_5", "#ingots/iron", 1, CurrencyDenomination.X5, 1, 400, 80, null, true));
        register(dual("tyc2:mint_copper_tin_5", "#ingots/copper", 1, "#ingots/tin", 1, CurrencyDenomination.X5, 2, 500, 80, null, false));

        // 10x denomination
        register(single("tyc2:mint_bronze_10", "#ingots/bronze", 1, CurrencyDenomination.X10, 1, 600, 100, null, false));
        register(dual("tyc2:mint_iron_pair_10", "#ingots/iron", 1, "#ingots/iron", 1, CurrencyDenomination.X10, 1, 600, 100, null, true));

        // 20x denomination
        register(single("tyc2:mint_silver_20", "#ingots/silver", 1, CurrencyDenomination.X20, 1, 800, 100, null, false));
        register(single("tyc2:mint_gold_20", "#ingots/gold", 1, CurrencyDenomination.X20, 1, 800, 100, null, true));
        register(single("tyc2:mint_steel_20", "#ingots/steel", 1, CurrencyDenomination.X20, 1, 800, 100, null, false));
        register(single("tyc2:mint_refined_iron_20", "ic2:refined_iron_ingot", 1, CurrencyDenomination.X20, 1, 800, 100, "ic2", false));

        // 50x denomination
        register(dual("tyc2:mint_gold_iron_50", "#ingots/gold", 1, "#ingots/iron", 1, CurrencyDenomination.X50, 1, 1200, 120, null, true));
        register(dual("tyc2:mint_gold_steel_50", "#ingots/gold", 1, "#ingots/steel", 1, CurrencyDenomination.X50, 1, 1200, 120, null, false));
        register(dual("tyc2:mint_gold_silver_50", "#ingots/gold", 1, "#ingots/silver", 1, CurrencyDenomination.X50, 1, 1200, 120, null, false));
        register(dual("tyc2:mint_gold_refined_iron_50", "#ingots/gold", 1, "ic2:refined_iron_ingot", 1, CurrencyDenomination.X50, 1, 1200, 120, "ic2", false));

        // 100x denomination
        register(dual("tyc2:mint_gold_emerald_100", "#ingots/gold", 2, "minecraft:emerald", 1, CurrencyDenomination.X100, 1, 1800, 140, null, true));
        register(dual("tyc2:mint_alloy_gold_100", "ic2:alloy", 1, "#ingots/gold", 1, CurrencyDenomination.X100, 1, 1800, 140, "ic2", false));
        register(dual("tyc2:mint_mixed_metal_gold_100", "ic2:mixed_metal_ingot", 1, "#ingots/gold", 1, CurrencyDenomination.X100, 1, 1800, 140, "ic2", false));

        // 200x denomination (Industrial Mint only)
        register(dual("tyc2:mint_diamond_gold_200", "minecraft:diamond", 1, "#ingots/gold", 2, CurrencyDenomination.X200, 1, 2500, 160, null, false));
        register(dual("tyc2:mint_alloy_diamond_200", "ic2:alloy", 2, "minecraft:diamond", 1, CurrencyDenomination.X200, 1, 2500, 160, "ic2", false));

        // 500x denomination (Industrial Mint only)
        register(dual("tyc2:mint_netherite_scrap_gold_500", "minecraft:netherite_scrap", 1, "#ingots/gold", 2, CurrencyDenomination.X500, 1, 4000, 200, null, false));
        register(single("tyc2:mint_netherite_ingot_500", "#ingots/netherite", 1, CurrencyDenomination.X500, 2, 4000, 200, null, false));

        // 1000x denomination (Industrial Mint only)
        register(dual("tyc2:mint_netherite_diamond_1000", "#ingots/netherite", 1, "minecraft:diamond", 1, CurrencyDenomination.X1000, 1, 6000, 240, null, false));
        register(dual("tyc2:mint_iridium_alloy_1000", "ic2:iridium", 1, "ic2:alloy", 1, CurrencyDenomination.X1000, 1, 6000, 240, "ic2", false));
        register(dual("tyc2:mint_iridium_ore_alloy_1000", "ic2:iridium_ore", 1, "ic2:alloy", 1, CurrencyDenomination.X1000, 1, 6000, 240, "ic2", false));
    }

    private static MintRecipe single(
            String id,
            String spec,
            int count,
            CurrencyDenomination denom,
            int outCount,
            int energyEu,
            int ticks,
            String requiredModId,
            boolean primitiveAllowed
    ) {
        return new MintRecipe(
                id,
                Collections.singletonList(MintIngredient.ofSpec(spec, count)),
                denom,
                outCount,
                energyEu,
                ticks,
                requiredModId,
                primitiveAllowed
        );
    }

    private static MintRecipe dual(
            String id,
            String specA,
            int countA,
            String specB,
            int countB,
            CurrencyDenomination denom,
            int outCount,
            int energyEu,
            int ticks,
            String requiredModId,
            boolean primitiveAllowed
    ) {
        List<MintIngredient> ings = new ArrayList<>(2);
        ings.add(MintIngredient.ofSpec(specA, countA));
        ings.add(MintIngredient.ofSpec(specB, countB));
        return new MintRecipe(id, ings, denom, outCount, energyEu, ticks, requiredModId, primitiveAllowed);
    }

    /**
     * Registers or replaces a recipe by its {@link MintRecipe#id()}.
     *
     * @param recipe recipe to register
     */
    public void register(MintRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        recipes.put(recipe.id(), recipe);
    }

    /**
     * Removes a recipe by ID if present.
     *
     * @param recipeId recipe ID
     * @return {@code true} if a recipe was removed
     */
    public boolean unregister(String recipeId) {
        return recipeId != null && recipes.remove(recipeId) != null;
    }

    /**
     * Returns a recipe by exact ID (checking both local recipes and {@link com.tycooncraft.tyc2.api.TyC2RegistryApi}).
     *
     * @param recipeId recipe identifier
     * @return optional recipe
     */
    public Optional<MintRecipe> getById(String recipeId) {
        if (recipeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mergedRecipeMap().get(recipeId));
    }

    /**
     * Returns all registered recipes (including API-registered and mod-gated ones).
     *
     * @return unmodifiable list of recipes
     */
    public List<MintRecipe> allRecipes() {
        return Collections.unmodifiableList(new ArrayList<>(mergedRecipeMap().values()));
    }

    /**
     * Returns all recipes active given a mod-loaded predicate (including API-registered recipes).
     *
     * @param modLoaded predicate returning {@code true} if a mod ID is loaded (may be {@code null} to allow all)
     * @return filtered list of active recipes
     */
    public List<MintRecipe> activeRecipes(Predicate<String> modLoaded) {
        List<MintRecipe> active = new ArrayList<>();
        for (MintRecipe recipe : mergedRecipeMap().values()) {
            if (isRecipeModActive(recipe, modLoaded)) {
                active.add(recipe);
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * Returns all recipes permitted on the burnable-fuel Primitive Minting Machine
     * ({@code primitiveAllowed == true}).
     *
     * @return unmodifiable list of primitive-allowed recipes
     */
    public List<MintRecipe> getPrimitiveRecipes() {
        return getPrimitiveRecipes(null);
    }

    /**
     * Returns all active recipes permitted on the burnable-fuel Primitive Minting Machine
     * ({@code primitiveAllowed == true}) given a mod-loaded predicate.
     *
     * @param modLoaded optional mod-loaded predicate (may be {@code null} to allow all)
     * @return unmodifiable list of active primitive-allowed recipes
     */
    public List<MintRecipe> getPrimitiveRecipes(Predicate<String> modLoaded) {
        List<MintRecipe> primitive = new ArrayList<>();
        for (MintRecipe recipe : mergedRecipeMap().values()) {
            if (recipe.primitiveAllowed() && isRecipeModActive(recipe, modLoaded)) {
                primitive.add(recipe);
            }
        }
        return Collections.unmodifiableList(primitive);
    }

    /**
     * Finds the best matching active recipe for the electric Industrial Minting Machine's
     * two input slots (all recipes allowed).
     *
     * @param slot0Item item ID in slot 0 (or {@code null}/blank if empty)
     * @param slot0Count stack count in slot 0
     * @param slot1Item item ID in slot 1 (or {@code null}/blank if empty)
     * @param slot1Count stack count in slot 1
     * @param tagMatcher optional tag matcher callback (may be {@code null})
     * @param modLoaded optional mod-loaded predicate (may be {@code null})
     * @return matching recipe and exact slot consumption counts, or empty
     */
    public Optional<MatchResult> findMatch(
            String slot0Item,
            int slot0Count,
            String slot1Item,
            int slot1Count,
            MintIngredient.TagMatcher tagMatcher,
            Predicate<String> modLoaded
    ) {
        return findMatch(slot0Item, slot0Count, slot1Item, slot1Count, tagMatcher, modLoaded, false);
    }

    /**
     * Finds the best matching active recipe for a Minting Machine's two input slots.
     *
     * <p>Dual-ingredient recipes are always checked before single-ingredient recipes so
     * alloy recipes (e.g., Copper + Tin or Gold + Iron) take priority over single-metal
     * recipes. Single-ingredient recipes only match when the opposite input slot is empty,
     * preventing accidental partial consumption of dual-slot alloy setups.
     *
     * <p>When {@code primitiveOnly == true}, only recipes with {@link MintRecipe#primitiveAllowed()}
     * are eligible; industrial-only recipes ({@code 200x..1000x} and modded metals/alloys) are skipped.
     *
     * @param slot0Item item ID in slot 0 (or {@code null}/blank if empty)
     * @param slot0Count stack count in slot 0
     * @param slot1Item item ID in slot 1 (or {@code null}/blank if empty)
     * @param slot1Count stack count in slot 1
     * @param tagMatcher optional tag matcher callback (may be {@code null})
     * @param modLoaded optional mod-loaded predicate (may be {@code null})
     * @param primitiveOnly {@code true} for {@code tyc2:primitive_mint_machine}; {@code false} for {@code tyc2:mint_machine}
     * @return matching recipe and exact slot consumption counts, or empty
     */
    public Optional<MatchResult> findMatch(
            String slot0Item,
            int slot0Count,
            String slot1Item,
            int slot1Count,
            MintIngredient.TagMatcher tagMatcher,
            Predicate<String> modLoaded,
            boolean primitiveOnly
    ) {
        boolean has0 = slot0Item != null && !slot0Item.isBlank() && slot0Count > 0;
        boolean has1 = slot1Item != null && !slot1Item.isBlank() && slot1Count > 0;
        if (!has0 && !has1) {
            return Optional.empty();
        }
        Map<String, MintRecipe> effective = mergedRecipeMap();

        // Pass 1: Dual-ingredient recipes first (order-independent across slot 0 and slot 1)
        if (has0 && has1) {
            for (MintRecipe recipe : effective.values()) {
                if (!recipe.isDualIngredient()
                        || !isRecipeModActive(recipe, modLoaded)
                        || (primitiveOnly && !recipe.primitiveAllowed())) {
                    continue;
                }
                MintIngredient first = recipe.ingredients().get(0);
                MintIngredient second = recipe.ingredients().get(1);
                if (first.matches(slot0Item, slot0Count, tagMatcher)
                        && second.matches(slot1Item, slot1Count, tagMatcher)) {
                    return Optional.of(new MatchResult(recipe, first.count(), second.count()));
                }
                if (first.matches(slot1Item, slot1Count, tagMatcher)
                        && second.matches(slot0Item, slot0Count, tagMatcher)) {
                    return Optional.of(new MatchResult(recipe, second.count(), first.count()));
                }
            }
        }

        // Pass 2: Single-ingredient recipes (only when the other slot is empty)
        if (has0 ^ has1) {
            for (MintRecipe recipe : effective.values()) {
                if (recipe.isDualIngredient()
                        || !isRecipeModActive(recipe, modLoaded)
                        || (primitiveOnly && !recipe.primitiveAllowed())) {
                    continue;
                }
                MintIngredient ing = recipe.ingredients().get(0);
                if (has0 && ing.matches(slot0Item, slot0Count, tagMatcher)) {
                    return Optional.of(new MatchResult(recipe, ing.count(), 0));
                }
                if (has1 && ing.matches(slot1Item, slot1Count, tagMatcher)) {
                    return Optional.of(new MatchResult(recipe, 0, ing.count()));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks whether the current input slots match a valid active {@link MintRecipe} that
     * is NOT allowed on the Primitive Minting Machine ({@code primitiveAllowed == false}).
     *
     * <p>Used by {@code tyc2:primitive_mint_machine} GUIs to show a clear upgrade prompt
     * directing the player to the electric Industrial Minting Machine.
     *
     * @param slot0Item item ID in slot 0 (or {@code null}/blank if empty)
     * @param slot0Count stack count in slot 0
     * @param slot1Item item ID in slot 1 (or {@code null}/blank if empty)
     * @param slot1Count stack count in slot 1
     * @param tagMatcher optional tag matcher callback (may be {@code null})
     * @param modLoaded optional mod-loaded predicate (may be {@code null})
     * @return {@code true} if the inputs form an industrial-only mint recipe
     */
    public boolean requiresIndustrialMint(
            String slot0Item,
            int slot0Count,
            String slot1Item,
            int slot1Count,
            MintIngredient.TagMatcher tagMatcher,
            Predicate<String> modLoaded
    ) {
        Optional<MatchResult> fullMatch = findMatch(slot0Item, slot0Count, slot1Item, slot1Count, tagMatcher, modLoaded, false);
        return fullMatch.isPresent() && !fullMatch.get().recipe().primitiveAllowed();
    }

    /**
     * Checks whether the given item ID is usable as an ingredient in any active minting recipe.
     *
     * @param itemId candidate item ID
     * @param tagMatcher optional tag matcher
     * @param modLoaded optional mod-loaded predicate
     * @return {@code true} if accepted in Minting Machine input slots
     */
    public boolean isValidInputItem(
            String itemId,
            MintIngredient.TagMatcher tagMatcher,
            Predicate<String> modLoaded
    ) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        for (MintRecipe recipe : mergedRecipeMap().values()) {
            if (!isRecipeModActive(recipe, modLoaded)) {
                continue;
            }
            for (MintIngredient ing : recipe.ingredients()) {
                if (ing.matchesItem(itemId, tagMatcher)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given item ID is usable as an ingredient in any active
     * Primitive Minting Machine recipe ({@code primitiveAllowed == true}).
     *
     * @param itemId candidate item ID
     * @param tagMatcher optional tag matcher
     * @param modLoaded optional mod-loaded predicate
     * @return {@code true} if used by at least one active primitive recipe
     */
    public boolean isValidPrimitiveInputItem(
            String itemId,
            MintIngredient.TagMatcher tagMatcher,
            Predicate<String> modLoaded
    ) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        for (MintRecipe recipe : mergedRecipeMap().values()) {
            if (!recipe.primitiveAllowed() || !isRecipeModActive(recipe, modLoaded)) {
                continue;
            }
            for (MintIngredient ing : recipe.ingredients()) {
                if (ing.matchesItem(itemId, tagMatcher)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Convenience overload of {@link #isValidPrimitiveInputItem(String, MintIngredient.TagMatcher, Predicate)}
     * without a mod-loaded filter.
     *
     * @param itemId candidate item ID
     * @param tagMatcher optional tag matcher
     * @return {@code true} if used by at least one primitive recipe
     */
    public boolean isValidPrimitiveInputItem(String itemId, MintIngredient.TagMatcher tagMatcher) {
        return isValidPrimitiveInputItem(itemId, tagMatcher, null);
    }

    private Map<String, MintRecipe> mergedRecipeMap() {
        List<MintRecipe> apiRecipes = com.tycooncraft.tyc2.api.TyC2RegistryApi.getRegisteredMintRecipes();
        if (apiRecipes.isEmpty()) {
            return recipes;
        }
        Map<String, MintRecipe> merged = new LinkedHashMap<>(recipes);
        for (MintRecipe apiRecipe : apiRecipes) {
            merged.put(apiRecipe.id(), apiRecipe);
        }
        return merged;
    }

    /**
     * Merges minting recipes from JSON text (supports single recipe object,
     * {@code { "replace": bool, "recipes": [...] }}, or top-level array {@code [...]}).
     *
     * <p>Entries with {@code "disabled": true} or {@code "remove": true} unregister the matching ID.
     *
     * @param json JSON content from a datapack or external config file
     * @return number of recipe entries applied (added/updated/removed)
     */
    public int mergeFromJson(String json) {
        Object parsed = JsonLite.parse(json);
        if (parsed instanceof List<?>) {
            return applyRecipeList(JsonLite.asArray(parsed));
        }
        Map<String, Object> obj = JsonLite.asObject(parsed);
        if (obj == null) {
            throw new IllegalArgumentException("Mint recipe JSON must be an object or array");
        }
        if (JsonLite.getBoolean(obj, "replace", false)) {
            recipes.clear();
        }
        if (obj.containsKey("recipes")) {
            List<Object> list = JsonLite.asArray(obj.get("recipes"));
            return list == null ? 0 : applyRecipeList(list);
        }
        return applySingleEntry(obj) ? 1 : 0;
    }

    /**
     * Loads and merges custom recipes from an external file such as
     * {@code config/tyc2/custom_mint_recipes.json}. Creates a clean example template
     * if {@code createTemplateIfMissing} is {@code true} and the file does not exist.
     *
     * @param path file path
     * @param createTemplateIfMissing whether to generate a starter template file if absent
     * @return number of recipes merged
     * @throws IOException if file I/O fails
     */
    public int loadExternalConfig(Path path, boolean createTemplateIfMissing) throws IOException {
        if (!Files.exists(path)) {
            if (createTemplateIfMissing) {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, defaultCustomConfigTemplate() + "\n", StandardCharsets.UTF_8);
            }
            return 0;
        }
        return mergeFromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /**
     * Returns a clean starter JSON template for {@code config/tyc2/custom_mint_recipes.json}.
     *
     * @return pretty-printed JSON template
     */
    public static String defaultCustomConfigTemplate() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_comment", "Add custom Minting Machine recipes below. Set 'disabled': true on an existing id to remove it.");
        root.put("replace", false);
        root.put("recipes", Collections.emptyList());
        return JsonLite.toPrettyJson(root);
    }

    /**
     * Serializes all currently registered recipes to a pretty-printed JSON document.
     *
     * @return JSON text
     */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>(recipes.size());
        for (MintRecipe recipe : recipes.values()) {
            list.add(recipe.toDataMap());
        }
        root.put("recipes", list);
        return JsonLite.toPrettyJson(root);
    }

    private int applyRecipeList(List<Object> list) {
        int applied = 0;
        for (Object elem : list) {
            Map<String, Object> map = JsonLite.asObject(elem);
            if (map != null && applySingleEntry(map)) {
                applied++;
            }
        }
        return applied;
    }

    private boolean applySingleEntry(Map<String, Object> map) {
        boolean disabled = JsonLite.getBoolean(map, "disabled", false)
                || JsonLite.getBoolean(map, "remove", false);
        if (disabled) {
            String id = JsonLite.getString(map, "id", null);
            return id != null && unregister(id);
        }
        register(MintRecipe.fromDataMap(map));
        return true;
    }

    private static boolean isRecipeModActive(MintRecipe recipe, Predicate<String> modLoaded) {
        String req = recipe.requiredModId();
        return req == null || modLoaded == null || modLoaded.test(req);
    }
}
