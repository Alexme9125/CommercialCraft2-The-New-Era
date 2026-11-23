package com.tycooncraft.tyc2.api;

import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TradeGood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public stable Java extension API for TycoonCraft²: The New Era ({@code tyc2}).
 *
 * <p>Allows third-party mods and integration bridges to programmatically register:
 * <ul>
 *   <li>Custom {@link TradeGood} catalog entries (including modded crops and fine foods)</li>
 *   <li>Custom {@link MintRecipe} metal/alloy minting recipes</li>
 *   <li>Custom {@link SeasonProvider} callbacks (e.g. Serene Seasons, Ecliptic Seasons, or custom calendar mods)</li>
 *   <li>Custom {@link IndustrialTierProvider} callbacks for modded technology progression detection</li>
 * </ul>
 *
 * <p>All methods are thread-safe and depend solely on pure Java 17 types.
 */
public final class TyC2RegistryApi {

    /**
     * Callback resolving the current agricultural {@link Season} for a Minecraft dimension ID
     * (such as {@code "minecraft:overworld"}).
     */
    @FunctionalInterface
    public interface SeasonProvider {
        /**
         * Returns the active season for {@code dimensionId}, or {@link Season#NONE} if inactive/unhandled.
         *
         * @param dimensionId dimension registry key
         * @return current {@link Season} (never {@code null}; {@code null} is treated as {@link Season#NONE})
         */
        Season currentSeason(String dimensionId);
    }

    /**
     * Callback detecting what {@link IndustrialTier} is unlocked by owning, crafting,
     * or placing a specific item or block registry ID.
     */
    @FunctionalInterface
    public interface IndustrialTierProvider {
        /**
         * Returns the unlocked {@link IndustrialTier} for {@code itemId}, or {@link IndustrialTier#TIER_0}
         * if this provider does not recognize the item as a progression milestone.
         *
         * @param itemId lowercase item or block registry ID
         * @param ic2Loaded whether IndustrialCraft² ({@code ic2}) is loaded
         * @return detected tier (never {@code null}; {@code null} is treated as {@link IndustrialTier#TIER_0})
         */
        IndustrialTier detectTier(String itemId, boolean ic2Loaded);
    }

    private static final Map<String, TradeGood> CUSTOM_GOODS = new ConcurrentHashMap<>();
    private static final Map<String, MintRecipe> CUSTOM_MINT_RECIPES = new ConcurrentHashMap<>();
    private static final Map<String, SeasonProvider> SEASON_PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, IndustrialTierProvider> TIER_PROVIDERS = new ConcurrentHashMap<>();

    private TyC2RegistryApi() {
    }

    /**
     * Registers or replaces a custom {@link TradeGood} that will automatically be merged
     * into {@link com.tycooncraft.tyc2.core.GoodsCatalog#getAvailableGoods}.
     *
     * @param good trade good definition
     */
    public static void registerTradeGood(TradeGood good) {
        Objects.requireNonNull(good, "good must not be null");
        CUSTOM_GOODS.put(good.id(), good);
    }

    /**
     * Unregisters a previously registered custom {@link TradeGood} by ID.
     *
     * @param goodId trade good ID
     * @return {@code true} if removed
     */
    public static boolean unregisterTradeGood(String goodId) {
        return goodId != null && CUSTOM_GOODS.remove(goodId) != null;
    }

    /**
     * Returns a snapshot of all custom trade goods registered via this API.
     *
     * @return unmodifiable list of custom trade goods
     */
    public static List<TradeGood> getRegisteredTradeGoods() {
        return Collections.unmodifiableList(new ArrayList<>(CUSTOM_GOODS.values()));
    }

    /**
     * Registers or replaces a custom {@link MintRecipe} that will automatically be visible
     * to {@link com.tycooncraft.tyc2.core.MintRecipeRegistry}.
     *
     * @param recipe minting recipe definition
     */
    public static void registerMintRecipe(MintRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        CUSTOM_MINT_RECIPES.put(recipe.id(), recipe);
    }

    /**
     * Unregisters a previously registered custom {@link MintRecipe} by ID.
     *
     * @param recipeId minting recipe ID
     * @return {@code true} if removed
     */
    public static boolean unregisterMintRecipe(String recipeId) {
        return recipeId != null && CUSTOM_MINT_RECIPES.remove(recipeId) != null;
    }

    /**
     * Returns a snapshot of all custom mint recipes registered via this API.
     *
     * @return unmodifiable list of custom mint recipes
     */
    public static List<MintRecipe> getRegisteredMintRecipes() {
        return Collections.unmodifiableList(new ArrayList<>(CUSTOM_MINT_RECIPES.values()));
    }

    /**
     * Registers a named {@link SeasonProvider} callback.
     *
     * @param providerId unique provider identifier (e.g. {@code "sereneseasons"})
     * @param provider season provider callback
     */
    public static void registerSeasonProvider(String providerId, SeasonProvider provider) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        Objects.requireNonNull(provider, "provider must not be null");
        SEASON_PROVIDERS.put(providerId.trim(), provider);
    }

    /**
     * Unregisters a {@link SeasonProvider} by identifier.
     *
     * @param providerId provider identifier
     * @return {@code true} if removed
     */
    public static boolean unregisterSeasonProvider(String providerId) {
        return providerId != null && SEASON_PROVIDERS.remove(providerId.trim()) != null;
    }

    /**
     * Resolves the current {@link Season} for {@code dimensionId} by consulting all registered
     * {@link SeasonProvider}s and returning the first active (non-{@link Season#NONE}) season.
     *
     * @param dimensionId dimension registry key (e.g. {@code "minecraft:overworld"})
     * @return active season, or {@link Season#NONE} if no provider reports one
     */
    public static Season resolveSeason(String dimensionId) {
        for (SeasonProvider provider : SEASON_PROVIDERS.values()) {
            Season season = provider.currentSeason(dimensionId);
            if (season != null && season.isActive()) {
                return season;
            }
        }
        return Season.NONE;
    }

    /**
     * Registers a named {@link IndustrialTierProvider} callback for custom tech-mod milestones.
     *
     * @param providerId unique provider identifier (e.g. {@code "mekanism"})
     * @param provider tier provider callback
     */
    public static void registerIndustrialTierProvider(String providerId, IndustrialTierProvider provider) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        Objects.requireNonNull(provider, "provider must not be null");
        TIER_PROVIDERS.put(providerId.trim(), provider);
    }

    /**
     * Unregisters an {@link IndustrialTierProvider} by identifier.
     *
     * @param providerId provider identifier
     * @return {@code true} if removed
     */
    public static boolean unregisterIndustrialTierProvider(String providerId) {
        return providerId != null && TIER_PROVIDERS.remove(providerId.trim()) != null;
    }

    /**
     * Resolves the highest {@link IndustrialTier} reported across all registered
     * {@link IndustrialTierProvider}s for the given item/block ID.
     *
     * @param itemId item or block registry ID
     * @param ic2Loaded whether IC2 is installed
     * @return highest tier reported, or {@link IndustrialTier#TIER_0}
     */
    public static IndustrialTier resolveIndustrialTier(String itemId, boolean ic2Loaded) {
        IndustrialTier best = IndustrialTier.TIER_0;
        for (IndustrialTierProvider provider : TIER_PROVIDERS.values()) {
            IndustrialTier detected = provider.detectTier(itemId, ic2Loaded);
            if (detected != null && detected.level() > best.level()) {
                best = detected;
            }
        }
        return best;
    }

    /**
     * Clears all API-registered entries. Intended for test isolation and server reload lifecycles.
     */
    public static void reset() {
        CUSTOM_GOODS.clear();
        CUSTOM_MINT_RECIPES.clear();
        SEASON_PROVIDERS.clear();
        TIER_PROVIDERS.clear();
    }
}
