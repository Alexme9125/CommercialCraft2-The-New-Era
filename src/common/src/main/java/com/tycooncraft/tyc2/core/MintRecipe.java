package com.tycooncraft.tyc2.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Data-driven recipe for the Primitive Minting Machine ({@code tyc2:primitive_mint_machine})
 * and the electric Industrial Minting Machine ({@code tyc2:mint_machine}).
 *
 * <p>Supports 1 or 2 metal/alloy ingredients producing a specific
 * {@link CurrencyDenomination} and coin count, with EU energy cost, tick duration,
 * optional mod gate, and a {@code primitiveAllowed} flag controlling whether the
 * burnable-fuel Primitive Minting Machine may execute this recipe.
 */
public final class MintRecipe {

    private static final Set<String> PRIMITIVE_VANILLA_TAGS = Set.of(
            "ingots/copper",
            "ingots/iron",
            "ingots/gold",
            "gems/emerald"
    );

    private final String id;
    private final List<MintIngredient> ingredients;
    private final CurrencyDenomination outputDenomination;
    private final int outputCount;
    private final int energyEu;
    private final int durationTicks;
    private final String requiredModId;
    private final boolean primitiveAllowed;

    /**
     * Constructs an immutable minting recipe, inferring {@code primitiveAllowed}
     * from the default rule (true only for pure-Vanilla {@code 1x..100x} recipes
     * without mod gates or modded metal/alloy tags).
     *
     * @param id unique recipe ID (e.g. {@code "mnera:mint_iron_5"})
     * @param ingredients 1 or 2 input ingredients
     * @param outputDenomination minted coin denomination
     * @param outputCount minted coin quantity (>= 1)
     * @param energyEu base energy cost in EU (>= 1)
     * @param durationTicks base processing time in ticks (>= 1)
     * @param requiredModId optional mod ID required for this recipe to be active, or {@code null}
     */
    public MintRecipe(
            String id,
            List<MintIngredient> ingredients,
            CurrencyDenomination outputDenomination,
            int outputCount,
            int energyEu,
            int durationTicks,
            String requiredModId
    ) {
        this(
                id,
                ingredients,
                outputDenomination,
                outputCount,
                energyEu,
                durationTicks,
                requiredModId,
                inferDefaultPrimitiveAllowed(ingredients, outputDenomination, requiredModId)
        );
    }

    /**
     * Constructs an immutable minting recipe with an explicit {@code primitiveAllowed} flag.
     *
     * @param id unique recipe ID (e.g. {@code "mnera:mint_iron_5"})
     * @param ingredients 1 or 2 input ingredients
     * @param outputDenomination minted coin denomination
     * @param outputCount minted coin quantity (>= 1)
     * @param energyEu base energy cost in EU (>= 1)
     * @param durationTicks base processing time in ticks (>= 1)
     * @param requiredModId optional mod ID required for this recipe to be active, or {@code null}
     * @param primitiveAllowed whether {@code tyc2:primitive_mint_machine} can mint this recipe
     */
    public MintRecipe(
            String id,
            List<MintIngredient> ingredients,
            CurrencyDenomination outputDenomination,
            int outputCount,
            int energyEu,
            int durationTicks,
            String requiredModId,
            boolean primitiveAllowed
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("MintRecipe id must not be blank");
        }
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 2) {
            throw new IllegalArgumentException("MintRecipe must have 1 or 2 ingredients: " + id);
        }
        for (MintIngredient ing : ingredients) {
            if (ing == null) {
                throw new IllegalArgumentException("MintRecipe ingredient must not be null: " + id);
            }
        }
        this.id = id.trim();
        this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
        this.outputDenomination = Objects.requireNonNull(outputDenomination, "outputDenomination must not be null");
        if (outputCount <= 0) {
            throw new IllegalArgumentException("outputCount must be positive: " + outputCount);
        }
        if (energyEu <= 0) {
            throw new IllegalArgumentException("energyEu must be positive: " + energyEu);
        }
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive: " + durationTicks);
        }
        this.outputCount = outputCount;
        this.energyEu = energyEu;
        this.durationTicks = durationTicks;
        this.requiredModId = (requiredModId == null || requiredModId.isBlank()) ? null : requiredModId.trim();
        this.primitiveAllowed = primitiveAllowed;
    }

    /**
     * Evaluates the default {@code primitiveAllowed} eligibility rule:
     * {@code true} only for pure-Vanilla low-to-mid denominations ({@code 1x..100x})
     * with no {@code requiredModId} and no modded-only metal/alloy tags or items.
     *
     * @param ingredients input ingredients
     * @param outputDenomination minted denomination
     * @param requiredModId optional mod ID
     * @return {@code true} if allowed on {@code tyc2:primitive_mint_machine} by default
     */
    public static boolean inferDefaultPrimitiveAllowed(
            List<MintIngredient> ingredients,
            CurrencyDenomination outputDenomination,
            String requiredModId
    ) {
        if (outputDenomination == null || ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        if (requiredModId != null && !requiredModId.isBlank()) {
            return false;
        }
        if (outputDenomination.value() > CurrencyDenomination.X100.value()) {
            return false;
        }
        for (MintIngredient ing : ingredients) {
            if (ing == null) {
                return false;
            }
            if (ing.isTag()) {
                String raw = ing.spec().substring(1);
                int colon = raw.indexOf(':');
                String path = colon >= 0 ? raw.substring(colon + 1) : raw;
                if (!PRIMITIVE_VANILLA_TAGS.contains(path)) {
                    return false;
                }
            } else if (!ing.spec().startsWith("minecraft:")) {
                return false;
            }
        }
        return true;
    }

    /** @return unique recipe identifier */
    public String id() {
        return id;
    }

    /** @return 1 or 2 required ingredients */
    public List<MintIngredient> ingredients() {
        return ingredients;
    }

    /** @return {@code true} if this recipe uses two input ingredients */
    public boolean isDualIngredient() {
        return ingredients.size() == 2;
    }

    /** @return minted currency denomination */
    public CurrencyDenomination outputDenomination() {
        return outputDenomination;
    }

    /** @return minted coin stack count */
    public int outputCount() {
        return outputCount;
    }

    /** @return total face value injected into global money supply on completion */
    public long totalFaceValue() {
        return Math.multiplyExact(outputDenomination.value(), outputCount);
    }

    /** @return base energy cost in EU */
    public int energyEu() {
        return energyEu;
    }

    /** @return base processing duration in ticks */
    public int durationTicks() {
        return durationTicks;
    }

    /** @return optional required mod ID, or {@code null} if always available */
    public String requiredModId() {
        return requiredModId;
    }

    /** @return {@code true} if the burnable-fuel {@code tyc2:primitive_mint_machine} can mint this recipe */
    public boolean primitiveAllowed() {
        return primitiveAllowed;
    }

    /**
     * Resolves the canonical minted output item ID ({@code ic2:coin} for {@code X1} when IC2 is loaded).
     *
     * @param ic2Loaded whether {@code ic2} is loaded
     * @return output item ID
     */
    public String outputItemId(boolean ic2Loaded) {
        return outputDenomination.canonicalItemId(ic2Loaded);
    }

    /**
     * Serializes this recipe to a JSON-compatible map.
     *
     * @return ordered map
     */
    public Map<String, Object> toDataMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        List<Map<String, Object>> ingList = new ArrayList<>(ingredients.size());
        for (MintIngredient ing : ingredients) {
            ingList.add(ing.toDataMap());
        }
        map.put("ingredients", ingList);
        map.put("outputDenomination", outputDenomination.name());
        map.put("outputCount", outputCount);
        map.put("energyEu", energyEu);
        map.put("durationTicks", durationTicks);
        map.put("primitive_allowed", primitiveAllowed);
        if (requiredModId != null) {
            map.put("requiredModId", requiredModId);
        }
        return map;
    }

    /**
     * Parses a {@link MintRecipe} from a JSON map. Supports standard and alias field names
     * used across datapacks and external user config files.
     *
     * @param map source JSON map
     * @return parsed recipe
     */
    public static MintRecipe fromDataMap(Map<String, Object> map) {
        Objects.requireNonNull(map, "recipe map must not be null");
        String id = JsonLite.requireString(map, "id");

        List<Object> rawIngs = JsonLite.asArray(map.get("ingredients"));
        if (rawIngs == null || rawIngs.isEmpty() || rawIngs.size() > 2) {
            throw new IllegalArgumentException("MintRecipe '" + id + "' must contain 1 or 2 ingredients");
        }
        List<MintIngredient> ingredients = new ArrayList<>(rawIngs.size());
        for (Object item : rawIngs) {
            if (item instanceof String) {
                ingredients.add(MintIngredient.ofSpec((String) item, 1));
            } else {
                Map<String, Object> ingMap = JsonLite.asObject(item);
                if (ingMap == null) {
                    throw new IllegalArgumentException("Invalid ingredient entry in MintRecipe '" + id + "'");
                }
                ingredients.add(MintIngredient.fromDataMap(ingMap));
            }
        }

        Object denomObj = map.containsKey("outputDenomination")
                ? map.get("outputDenomination")
                : (map.containsKey("denomination") ? map.get("denomination") : map.get("output"));
        CurrencyDenomination denom;
        if (denomObj instanceof Number) {
            denom = CurrencyDenomination.fromValue(((Number) denomObj).longValue())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown denomination value: " + denomObj));
        } else if (denomObj instanceof String) {
            denom = CurrencyDenomination.parse((String) denomObj)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown denomination spec: " + denomObj));
        } else {
            throw new IllegalArgumentException("MintRecipe '" + id + "' missing valid outputDenomination");
        }

        int count = map.containsKey("outputCount")
                ? JsonLite.getInt(map, "outputCount", 1)
                : JsonLite.getInt(map, "count", 1);
        int energy = map.containsKey("energyEu")
                ? JsonLite.getInt(map, "energyEu", 400)
                : JsonLite.getInt(map, "energy", 400);
        int ticks = map.containsKey("durationTicks")
                ? JsonLite.getInt(map, "durationTicks", 80)
                : (map.containsKey("ticks") ? JsonLite.getInt(map, "ticks", 80) : JsonLite.getInt(map, "duration", 80));
        String modId = JsonLite.getString(map, "requiredModId", JsonLite.getString(map, "requiredMod", null));

        boolean defaultPrimitive = inferDefaultPrimitiveAllowed(ingredients, denom, modId);
        boolean primitiveAllowed;
        if (map.containsKey("primitive_allowed")) {
            primitiveAllowed = JsonLite.getBoolean(map, "primitive_allowed", defaultPrimitive);
        } else if (map.containsKey("primitiveAllowed")) {
            primitiveAllowed = JsonLite.getBoolean(map, "primitiveAllowed", defaultPrimitive);
        } else {
            primitiveAllowed = defaultPrimitive;
        }

        return new MintRecipe(id, ingredients, denom, count, energy, ticks, modId, primitiveAllowed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MintRecipe)) {
            return false;
        }
        MintRecipe that = (MintRecipe) o;
        return outputCount == that.outputCount
                && energyEu == that.energyEu
                && durationTicks == that.durationTicks
                && primitiveAllowed == that.primitiveAllowed
                && id.equals(that.id)
                && ingredients.equals(that.ingredients)
                && outputDenomination == that.outputDenomination
                && Objects.equals(requiredModId, that.requiredModId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ingredients, outputDenomination, outputCount, energyEu, durationTicks, requiredModId, primitiveAllowed);
    }

    @Override
    public String toString() {
        return "MintRecipe{" + id + ": " + ingredients + " -> " + outputCount + "x " + outputDenomination
                + (primitiveAllowed ? " [primitive]" : " [industrial]") + "}";
    }
}
