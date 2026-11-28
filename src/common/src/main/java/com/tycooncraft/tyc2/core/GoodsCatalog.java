package com.tycooncraft.tyc2.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Complete built-in and extensible catalog of tradeable goods spanning Village Levels 1–10
 * across Vanilla Minecraft, IndustrialCraft² ({@code ic2}), EcoCraft² ({@code ecc2}),
 * Farmer's Delight ({@code farmersdelight}), and Kaleidoscope Cookery ({@code kaleidoscope_cookery}).
 *
 * <p>All runtime queries filter through {@code Predicate<String> itemExists} so items from
 * uninstalled mods never appear in village trade orders.
 */
public final class GoodsCatalog {

    private final Map<String, TradeGood> goods = new LinkedHashMap<>();

    /**
     * Creates an empty catalog. Use {@link #createDefault()} for the complete built-in catalog.
     */
    public GoodsCatalog() {
    }

    /**
     * Creates a catalog pre-populated with all built-in Vanilla, IC2, EcoCraft²,
     * Farmer's Delight, and Kaleidoscope Cookery trade goods.
     *
     * @return populated default catalog
     */
    public static GoodsCatalog createDefault() {
        GoodsCatalog catalog = new GoodsCatalog();
        catalog.registerBuiltInDefaults();
        return catalog;
    }

    /**
     * Registers all built-in goods from DESIGN.md Sections 6.3 and 8.3.
     */
    public void registerBuiltInDefaults() {
        registerVanillaRuralSells();
        registerVanillaCrops();
        registerVanillaMiningAndIndustrialBuys();
        registerVanillaMidTierSellsAndBuys();
        registerVanillaHighTierAndEndgame();
        registerIc2Goods();
        registerEcc2Goods();
        registerFarmersDelightGoods();
        registerKaleidoscopeCookeryGoods();
    }

    private void registerVanillaRuralSells() {
        // Textiles & animal goods (Lv 1-4)
        add(TradeGood.builder("mnera:white_wool", "minecraft:white_wool", GoodsCategory.TEXTILE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("mnera:white_carpet", "minecraft:white_carpet", GoodsCategory.TEXTILE, 3)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(8, 20)
                .biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("mnera:string", "minecraft:string", GoodsCategory.TEXTILE, 2)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(10, 28)
                .biomeAffinities("plains", "taiga").build());
        add(TradeGood.builder("mnera:leather", "minecraft:leather", GoodsCategory.TEXTILE, 5)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(6, 18)
                .biomeAffinities("plains", "savanna", "taiga").build());
        add(TradeGood.builder("mnera:feather", "minecraft:feather", GoodsCategory.TEXTILE, 2)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:rabbit_hide", "minecraft:rabbit_hide", GoodsCategory.TEXTILE, 4)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(6, 16)
                .biomeAffinities("desert", "snowy").build());

        // Monster drops (Lv 1-5)
        add(TradeGood.builder("mnera:rotten_flesh", "minecraft:rotten_flesh", GoodsCategory.MONSTER_DROP, 1)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(16, 40).build());
        add(TradeGood.builder("mnera:bone", "minecraft:bone", GoodsCategory.MONSTER_DROP, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24).build());
        add(TradeGood.builder("mnera:spider_eye", "minecraft:spider_eye", GoodsCategory.MONSTER_DROP, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(6, 18).build());
        add(TradeGood.builder("mnera:gunpowder", "minecraft:gunpowder", GoodsCategory.MONSTER_DROP, 7)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(6, 16).build());
        add(TradeGood.builder("mnera:slime_ball", "minecraft:slime_ball", GoodsCategory.MONSTER_DROP, 9)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(4, 14)
                .biomeAffinities("swamp").biomeExclusive(true).build());

        // Forestry, winter goods, desert/biome staples (Lv 1-5)
        add(TradeGood.builder("mnera:stick", "minecraft:stick", GoodsCategory.FORESTRY, 1)
                .levelRange(1, 3).sellable(true).buyable(false).quantityRange(16, 48)
                .biomeAffinities("plains", "taiga", "savanna", "jungle", "swamp").build());
        add(TradeGood.builder("mnera:oak_log", "minecraft:oak_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("plains", "swamp").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:spruce_log", "minecraft:spruce_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("taiga", "snowy").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:acacia_log", "minecraft:acacia_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("savanna").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:jungle_log", "minecraft:jungle_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("jungle").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:clay_ball", "minecraft:clay_ball", GoodsCategory.BUILDING, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("swamp").build());
        add(TradeGood.builder("mnera:lily_pad", "minecraft:lily_pad", GoodsCategory.AGRICULTURE, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(6, 18)
                .biomeAffinities("swamp").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:honey_bottle", "minecraft:honey_bottle", GoodsCategory.AGRICULTURE, 6)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(4, 12)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:beef", "minecraft:beef", GoodsCategory.FOOD_BASIC, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 20)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:mutton", "minecraft:mutton", GoodsCategory.FOOD_BASIC, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 20)
                .biomeAffinities("snowy", "plains").build());
        add(TradeGood.builder("mnera:sand", "minecraft:sand", GoodsCategory.BUILDING, 2)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(16, 48)
                .biomeAffinities("desert").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:sandstone", "minecraft:sandstone", GoodsCategory.BUILDING, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("desert").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:cactus", "minecraft:cactus", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("desert").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:snow_block", "minecraft:snow_block", GoodsCategory.BUILDING, 2)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(16, 48)
                .biomeAffinities("snowy").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:packed_ice", "minecraft:packed_ice", GoodsCategory.BUILDING, 8)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("snowy").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:saddle", "minecraft:saddle", GoodsCategory.TEXTILE, 28)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(1, 3)
                .biomeAffinities("plains", "savanna").build());
    }

    private void registerVanillaCrops() {
        // Seasonal Vanilla Crops (DESIGN.md Section 8.3)
        add(TradeGood.builder("mnera:wheat", "minecraft:wheat", GoodsCategory.AGRICULTURE, 3)
                .optionalTag("#crops/wheat").levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:carrot", "minecraft:carrot", GoodsCategory.AGRICULTURE, 3)
                .optionalTag("#crops/carrot").levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:potato", "minecraft:potato", GoodsCategory.AGRICULTURE, 3)
                .optionalTag("#crops/potato").levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("taiga", "plains").build());
        add(TradeGood.builder("mnera:beetroot", "minecraft:beetroot", GoodsCategory.AGRICULTURE, 3)
                .optionalTag("#crops/beetroot").levelRange(1, 5).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.AUTUMN, Season.WINTER)
                .biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:pumpkin", "minecraft:pumpkin", GoodsCategory.AGRICULTURE, 5)
                .optionalTag("#crops/pumpkin").levelRange(1, 6).sellable(true).buyable(false).quantityRange(6, 18)
                .harvestSeasons(Season.AUTUMN)
                .biomeAffinities("plains", "taiga").build());
        add(TradeGood.builder("mnera:melon_slice", "minecraft:melon_slice", GoodsCategory.AGRICULTURE, 2)
                .optionalTag("#crops/melon").levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 36)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("savanna", "jungle").build());
        add(TradeGood.builder("mnera:sweet_berries", "minecraft:sweet_berries", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("taiga", "snowy").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:apple", "minecraft:apple", GoodsCategory.AGRICULTURE, 5)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(6, 18)
                .harvestSeasons(Season.AUTUMN)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:sugar_cane", "minecraft:sugar_cane", GoodsCategory.AGRICULTURE, 3)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("jungle", "swamp").build());
        add(TradeGood.builder("mnera:cocoa_beans", "minecraft:cocoa_beans", GoodsCategory.AGRICULTURE, 5)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("jungle").biomeExclusive(true).build());
        add(TradeGood.builder("mnera:bamboo", "minecraft:bamboo", GoodsCategory.AGRICULTURE, 2)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(16, 48)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("jungle").biomeExclusive(true).build());

        // Year-round cave/marine crops (All 4 seasons)
        add(TradeGood.builder("mnera:brown_mushroom", "minecraft:brown_mushroom", GoodsCategory.AGRICULTURE, 4)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.allFourSeasons()).biomeAffinities("taiga", "swamp").build());
        add(TradeGood.builder("mnera:red_mushroom", "minecraft:red_mushroom", GoodsCategory.AGRICULTURE, 4)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.allFourSeasons()).biomeAffinities("taiga", "swamp").build());
        add(TradeGood.builder("mnera:kelp", "minecraft:kelp", GoodsCategory.AGRICULTURE, 2)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(16, 40)
                .harvestSeasons(Season.allFourSeasons()).build());
        add(TradeGood.builder("mnera:glow_berries", "minecraft:glow_berries", GoodsCategory.AGRICULTURE, 5)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.allFourSeasons()).build());

        // Lv 7-10 Metropolis staple crop demand
        add(TradeGood.builder("mnera:metro_wheat_demand", "minecraft:wheat", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/wheat").levelRange(7, 10).sellable(false).buyable(true).quantityRange(16, 40)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .demandBiomes("desert", "snowy").build());
        add(TradeGood.builder("mnera:metro_carrot_demand", "minecraft:carrot", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/carrot").levelRange(7, 10).sellable(false).buyable(true).quantityRange(16, 40)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .demandBiomes("desert", "snowy").build());
        add(TradeGood.builder("mnera:metro_potato_demand", "minecraft:potato", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/potato").levelRange(7, 10).sellable(false).buyable(true).quantityRange(16, 40)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .demandBiomes("desert", "snowy").build());
    }

    private void registerVanillaMiningAndIndustrialBuys() {
        // Lv 1-6 Rural & developing villages buy mining, metals, fuel, timber (desert), and basic supplies
        add(TradeGood.builder("mnera:coal", "minecraft:coal", GoodsCategory.MINING, 4)
                .levelRange(1, 6).sellable(false).buyable(true).quantityRange(12, 32)
                .demandBiomes("snowy", "plains", "jungle", "swamp")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:charcoal", "minecraft:charcoal", GoodsCategory.MINING, 4)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(12, 32)
                .demandBiomes("desert", "snowy")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:copper_ingot", "minecraft:copper_ingot", GoodsCategory.METAL, 5)
                .optionalTag("#ingots/copper").levelRange(1, 6).sellable(false).buyable(true)
                .quantityRange(8, 24)
                .demandBiomes("plains", "taiga", "savanna").build());
        add(TradeGood.builder("mnera:iron_ingot", "minecraft:iron_ingot", GoodsCategory.METAL, 9)
                .optionalTag("#ingots/iron").levelRange(1, 6).sellable(false).buyable(true)
                .quantityRange(6, 20)
                .demandBiomes("plains", "savanna", "jungle", "swamp", "taiga").build());
        add(TradeGood.builder("mnera:cobblestone", "minecraft:cobblestone", GoodsCategory.MINING, 1)
                .optionalTag("#cobblestones").levelRange(1, 4).sellable(false).buyable(true).quantityRange(32, 64)
                .demandBiomes("desert", "swamp").build());
        add(TradeGood.builder("mnera:stone", "minecraft:stone", GoodsCategory.MINING, 2)
                .optionalTag("#stones").levelRange(1, 6).sellable(false).buyable(true).quantityRange(24, 64)
                .demandBiomes("desert", "plains", "savanna", "jungle", "swamp").build());
        add(TradeGood.builder("mnera:flint", "minecraft:flint", GoodsCategory.MINING, 3)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(10, 24)
                .demandBiomes("taiga", "snowy").build());
        add(TradeGood.builder("mnera:torch", "minecraft:torch", GoodsCategory.BUILDING, 2)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(16, 48)
                .demandBiomes("snowy", "savanna", "jungle", "swamp")
                .demandSeasons(Season.WINTER).build());
        // Desert villages lack local trees and gladly buy any timber logs
        add(TradeGood.builder("mnera:oak_log_demand", "minecraft:oak_log", GoodsCategory.FORESTRY, 4)
                .optionalTag("#minecraft:logs").levelRange(1, 5).sellable(false).buyable(true)
                .quantityRange(12, 32).biomeExclusive(true)
                .demandBiomes("desert").demandSeasons(Season.WINTER).build());
    }

    private void registerVanillaMidTierSellsAndBuys() {
        // Lv 4-6 Developing town sells
        add(TradeGood.builder("mnera:paper", "minecraft:paper", GoodsCategory.TEXTILE, 4)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32).build());
        add(TradeGood.builder("mnera:book", "minecraft:book", GoodsCategory.TEXTILE, 12)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(4, 12).build());
        add(TradeGood.builder("mnera:glass", "minecraft:glass", GoodsCategory.BUILDING, 4)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("desert").build());
        add(TradeGood.builder("mnera:brick", "minecraft:brick", GoodsCategory.BUILDING, 5)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32).build());
        add(TradeGood.builder("mnera:terracotta", "minecraft:terracotta", GoodsCategory.BUILDING, 5)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("desert", "savanna").build());
        add(TradeGood.builder("mnera:red_dye", "minecraft:red_dye", GoodsCategory.TEXTILE, 4)
                .levelRange(4, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("savanna").build());

        // Basic enchanted books (Lv 4-6, Tier 1)
        add(TradeGood.builder("mnera:book_unbreaking_1", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 90)
                .levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:unbreaking:1").biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:book_efficiency_2", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 110)
                .levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:efficiency:2").biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:book_protection_2", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 110)
                .levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:protection:2").biomeAffinities("snowy").build());

        // Lv 4-6 Developing town buys (refined metals, redstone, components, cooked foods)
        add(TradeGood.builder("mnera:gold_ingot", "minecraft:gold_ingot", GoodsCategory.METAL, 22)
                .optionalTag("#ingots/gold").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(4, 14)
                .demandBiomes("desert", "savanna").build());
        add(TradeGood.builder("mnera:redstone", "minecraft:redstone", GoodsCategory.INDUSTRIAL, 6)
                .optionalTag("#dusts/redstone").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(12, 32)
                .demandBiomes("taiga", "plains").build());
        add(TradeGood.builder("mnera:lapis_lazuli", "minecraft:lapis_lazuli", GoodsCategory.MINING, 7)
                .optionalTag("#gems/lapis").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(8, 24)
                .demandBiomes("desert", "snowy").build());
        add(TradeGood.builder("mnera:quartz", "minecraft:quartz", GoodsCategory.MINING, 10)
                .optionalTag("#gems/quartz").levelRange(5, 8).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(8, 24)
                .demandBiomes("plains", "desert").build());
        add(TradeGood.builder("mnera:piston", "minecraft:piston", GoodsCategory.INDUSTRIAL, 24)
                .levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 8)
                .demandBiomes("taiga", "jungle").build());
        add(TradeGood.builder("mnera:repeater", "minecraft:repeater", GoodsCategory.INDUSTRIAL, 20)
                .levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(3, 10)
                .demandBiomes("taiga", "plains").build());
        add(TradeGood.builder("mnera:bread", "minecraft:bread", GoodsCategory.FOOD_BASIC, 7)
                .optionalTag("#foods/bread").levelRange(2, 6).sellable(false).buyable(true).quantityRange(8, 24)
                .demandBiomes("desert", "snowy", "taiga", "savanna", "swamp")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:baked_potato", "minecraft:baked_potato", GoodsCategory.FOOD_BASIC, 7)
                .levelRange(3, 6).sellable(false).buyable(true).quantityRange(8, 24)
                .demandBiomes("desert", "snowy", "taiga")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:cooked_porkchop", "minecraft:cooked_porkchop", GoodsCategory.FOOD_BASIC, 11)
                .optionalTag("#foods/cooked_pork").levelRange(4, 7).sellable(false).buyable(true).quantityRange(6, 18)
                .demandBiomes("desert", "snowy", "taiga", "savanna", "swamp")
                .demandSeasons(Season.WINTER).build());
    }

    private void registerVanillaHighTierAndEndgame() {
        // Lv 7-10 Metropolis buys: fine foods & high-tier industrial supplies
        add(TradeGood.builder("mnera:golden_carrot", "minecraft:golden_carrot", GoodsCategory.FOOD_FINE, 26)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(6, 18)
                .demandBiomes("desert", "snowy").build());
        add(TradeGood.builder("mnera:cooked_beef", "minecraft:cooked_beef", GoodsCategory.FOOD_FINE, 14)
                .optionalTag("#foods/cooked_beef").levelRange(7, 10).industrialTier(IndustrialTier.TIER_0)
                .sellable(false).buyable(true).quantityRange(8, 24)
                .demandBiomes("desert", "snowy", "taiga", "savanna", "swamp")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:pumpkin_pie", "minecraft:pumpkin_pie", GoodsCategory.FOOD_FINE, 18)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(6, 16)
                .demandBiomes("plains", "taiga", "snowy")
                .demandSeasons(Season.AUTUMN, Season.WINTER).build());
        add(TradeGood.builder("mnera:cake", "minecraft:cake", GoodsCategory.FOOD_FINE, 32)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 6)
                .demandBiomes("plains", "desert").build());
        add(TradeGood.builder("mnera:rabbit_stew", "minecraft:rabbit_stew", GoodsCategory.FOOD_FINE, 28)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(3, 10)
                .demandBiomes("snowy", "taiga", "swamp")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:glowstone_dust", "minecraft:glowstone_dust", GoodsCategory.INDUSTRIAL, 14)
                .optionalTag("#dusts/glowstone").levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(8, 24)
                .demandBiomes("snowy", "swamp", "jungle")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:blaze_powder", "minecraft:blaze_powder", GoodsCategory.INDUSTRIAL, 24)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(4, 14)
                .demandBiomes("snowy", "taiga")
                .demandSeasons(Season.WINTER).build());
        add(TradeGood.builder("mnera:obsidian", "minecraft:obsidian", GoodsCategory.INDUSTRIAL, 18)
                .optionalTag("#obsidians").levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(6, 18)
                .demandBiomes("desert", "savanna").build());
        add(TradeGood.builder("mnera:diamond", "minecraft:diamond", GoodsCategory.METAL, 140)
                .optionalTag("#gems/diamond").levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(1, 4)
                .demandBiomes("plains", "desert", "snowy").build());

        // Lv 7-10 Metropolis sells: High-level enchanted books, rare alloys/treasures, endgame
        add(TradeGood.builder("mnera:book_mending", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 480)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:mending:1").biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:book_efficiency_5", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 420)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:efficiency:5").biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:book_fortune_3", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 450)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:fortune:3").biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:book_unbreaking_3", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 360)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:unbreaking:3").biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:book_sharpness_5", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 430)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:sharpness:5").build());
        add(TradeGood.builder("mnera:book_protection_4", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 410)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:protection:4").build());

        add(TradeGood.builder("mnera:name_tag", "minecraft:name_tag", GoodsCategory.RARE_ALLOY, 85)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 4).build());
        add(TradeGood.builder("mnera:amethyst_shard", "minecraft:amethyst_shard", GoodsCategory.RARE_ALLOY, 24)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(6, 18).build());
        add(TradeGood.builder("mnera:prismarine_crystals", "minecraft:prismarine_crystals", GoodsCategory.RARE_ALLOY, 30)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(true).buyable(false).quantityRange(6, 16).build());
        add(TradeGood.builder("mnera:echo_shard", "minecraft:echo_shard", GoodsCategory.RARE_ALLOY, 320)
                .levelRange(8, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 3).build());
        add(TradeGood.builder("mnera:heart_of_the_sea", "minecraft:heart_of_the_sea", GoodsCategory.RARE_ALLOY, 650)
                .levelRange(8, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2).build());
        add(TradeGood.builder("mnera:totem_of_undying", "minecraft:totem_of_undying", GoodsCategory.ENDGAME, 850)
                .levelRange(9, 10).industrialTier(IndustrialTier.TIER_4)
                .sellable(true).buyable(false).quantityRange(1, 1).build());
        add(TradeGood.builder("mnera:netherite_scrap", "minecraft:netherite_scrap", GoodsCategory.RARE_ALLOY, 520)
                .levelRange(8, 10).industrialTier(IndustrialTier.TIER_4)
                .sellable(true).buyable(false).quantityRange(1, 2).build());
        add(TradeGood.builder("mnera:ancient_debris", "minecraft:ancient_debris", GoodsCategory.ENDGAME, 950)
                .levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(false).quantityRange(1, 2).build());
        add(TradeGood.builder("mnera:netherite_upgrade_template", "minecraft:netherite_upgrade_smithing_template", GoodsCategory.ENDGAME, 1100)
                .levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(false).quantityRange(1, 1).build());
    }

    private void registerIc2Goods() {
        // IC2 crops & forestry (DESIGN.md Section 8.3; IC2 1.0.5 sticky resin ID is ic2:resin)
        add(TradeGood.builder("mnera:mhera_sticky_resin", "mhera:resin", GoodsCategory.FORESTRY, 6)
                .requiredModId("mhera").levelRange(1, 5).industrialTier(IndustrialTier.TIER_0)
                .sellable(true).buyable(false).quantityRange(6, 20)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("plains", "taiga").build());
        add(TradeGood.builder("mnera:mhera_rubber", "mhera:rubber", GoodsCategory.INDUSTRIAL, 10)
                .requiredModId("mhera").levelRange(3, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(6, 18).build());
        add(TradeGood.builder("mnera:mhera_hops", "mhera:hops", GoodsCategory.AGRICULTURE, 12)
                .requiredModId("mhera").levelRange(3, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(6, 16)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("mnera:mhera_coffee_beans", "mhera:coffee_beans", GoodsCategory.AGRICULTURE, 18)
                .requiredModId("mhera").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(4, 14)
                .harvestSeasons(Season.SUMMER).build());
        add(TradeGood.builder("mnera:mhera_terra_wart", "mhera:terra_wart", GoodsCategory.AGRICULTURE, 45)
                .requiredModId("mhera").levelRange(6, 9).industrialTier(IndustrialTier.TIER_2)
                .sellable(true).buyable(false).quantityRange(3, 10)
                .harvestSeasons(Season.allFourSeasons()).build());

        // IC2 metals & industrial components
        add(TradeGood.builder("mnera:mhera_tin_ingot", "mhera:tin_ingot", GoodsCategory.METAL, 6)
                .optionalTag("#ingots/tin").requiredModId("mhera")
                .levelRange(1, 5).industrialTier(IndustrialTier.TIER_0)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("mnera:mhera_lead_ingot", "mhera:lead_ingot", GoodsCategory.METAL, 7)
                .optionalTag("#ingots/lead").requiredModId("mhera")
                .levelRange(1, 5).industrialTier(IndustrialTier.TIER_0)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("mnera:mhera_bronze_ingot", "mhera:bronze_ingot", GoodsCategory.METAL, 14)
                .optionalTag("#ingots/bronze").requiredModId("mhera")
                .levelRange(4, 9).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(6, 18).build());
        add(TradeGood.builder("mnera:mhera_silver_ingot", "mhera:silver_ingot", GoodsCategory.METAL, 20)
                .optionalTag("#ingots/silver").requiredModId("mhera")
                .levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(4, 14).build());
        add(TradeGood.builder("mnera:mhera_steel_ingot", "mhera:steel_ingot", GoodsCategory.RARE_ALLOY, 26)
                .optionalTag("#ingots/steel").requiredModId("mhera")
                .levelRange(4, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(4, 14).build());
        add(TradeGood.builder("mnera:mhera_circuit", "mhera:circuit", GoodsCategory.INDUSTRIAL, 34)
                .optionalTag("#circuits/basic").requiredModId("mhera").levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 8).build());
        add(TradeGood.builder("mnera:mhera_advanced_circuit", "mhera:advanced_circuit", GoodsCategory.INDUSTRIAL, 85)
                .optionalTag("#circuits/advanced").requiredModId("mhera").levelRange(6, 9).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(1, 5).build());
        add(TradeGood.builder("mnera:mhera_copper_cable", "mhera:copper_cable", GoodsCategory.INDUSTRIAL, 5)
                .optionalTag("#cables/copper").requiredModId("mhera").levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(12, 32).build());
        add(TradeGood.builder("mnera:mhera_re_battery", "mhera:re_battery", GoodsCategory.INDUSTRIAL, 28)
                .requiredModId("mhera").levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 6).build());
        add(TradeGood.builder("mnera:mhera_energy_crystal", "mhera:energy_crystal", GoodsCategory.INDUSTRIAL, 180)
                .requiredModId("mhera").levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(false).buyable(true).quantityRange(1, 3).build());

        // IC2 rare alloys & Lv 10 Endgame Iridium Shard
        add(TradeGood.builder("mnera:mhera_alloy", "mhera:alloy", GoodsCategory.RARE_ALLOY, 120)
                .requiredModId("mhera").levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(2, 6).build());
        add(TradeGood.builder("mnera:mhera_carbon_plate", "mhera:carbon_plate", GoodsCategory.RARE_ALLOY, 95)
                .requiredModId("mhera").levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(2, 6).build());
        add(TradeGood.builder("mnera:mhera_iridium_shard", "mhera:iridium_shard", GoodsCategory.ENDGAME, 900)
                .requiredModId("mhera").levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(false).quantityRange(1, 3).build());
    }

    private void registerEcc2Goods() {
        // EcoCraft 2 (ecc2) clean-energy, gas & photonic goods — auto-filtered when ecc2 is absent
        add(TradeGood.builder("mnera:ecera_culture_medium_cell", "ecera:culture_medium_cell", GoodsCategory.INDUSTRIAL, 18)
                .requiredModId("ecera").levelRange(3, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(4, 12).build());
        add(TradeGood.builder("mnera:ecera_water_gas_cell", "ecera:water_gas_cell", GoodsCategory.INDUSTRIAL, 24)
                .requiredModId("ecera").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(4, 14).build());
        add(TradeGood.builder("mnera:ecera_methane_ice_cluster", "ecera:methane_ice_cluster", GoodsCategory.MINING, 28)
                .requiredModId("ecera").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(4, 12)
                .biomeAffinities("snowy").build());
        add(TradeGood.builder("mnera:ecera_natural_gas_cell", "ecera:natural_gas_cell", GoodsCategory.INDUSTRIAL, 64)
                .requiredModId("ecera").levelRange(5, 9).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(3, 10).build());
        add(TradeGood.builder("mnera:ecera_sunnarium", "ecera:sunnarium", GoodsCategory.RARE_ALLOY, 320)
                .requiredModId("ecera").levelRange(8, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(false).buyable(true).quantityRange(1, 3).build());
        add(TradeGood.builder("mnera:ecera_sunnarium_alloy", "ecera:sunnarium_alloy", GoodsCategory.RARE_ALLOY, 560)
                .requiredModId("ecera").levelRange(9, 10).industrialTier(IndustrialTier.TIER_4)
                .sellable(false).buyable(true).quantityRange(1, 2).build());
        add(TradeGood.builder("mnera:ecera_iridium_ingot", "ecera:iridium_ingot", GoodsCategory.ENDGAME, 1150)
                .optionalTag("#ingots/iridium").requiredModId("ecera")
                .levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(true).quantityRange(1, 2).build());
    }

    private void registerFarmersDelightGoods() {
        // Farmer's Delight crops (DESIGN.md Section 8.3)
        add(TradeGood.builder("mnera:fd_onion", "farmersdelight:onion", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/onion").requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.AUTUMN).biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:fd_cabbage", "farmersdelight:cabbage", GoodsCategory.AGRICULTURE, 5)
                .optionalTag("#crops/cabbage").requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SPRING, Season.AUTUMN, Season.WINTER).biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("mnera:fd_cabbage_leaf", "farmersdelight:cabbage_leaf", GoodsCategory.AGRICULTURE, 3)
                .requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.AUTUMN, Season.WINTER).biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("mnera:fd_tomato", "farmersdelight:tomato", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/tomato").requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna", "plains").build());
        add(TradeGood.builder("mnera:fd_rice", "farmersdelight:rice", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/rice").requiredModId("farmersdelight")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("mnera:fd_rice_panicle", "farmersdelight:rice_panicle", GoodsCategory.AGRICULTURE, 4)
                .requiredModId("farmersdelight")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());

        // Farmer's Delight hearty fine meals (Lv 7-10 Buy Orders!)
        registerFineMeal("mnera:fd_beef_stew", "farmersdelight:beef_stew", "farmersdelight", 34, true);
        registerFineMeal("mnera:fd_chicken_soup", "farmersdelight:chicken_soup", "farmersdelight", 32, true);
        registerFineMeal("mnera:fd_vegetable_soup", "farmersdelight:vegetable_soup", "farmersdelight", 28, true);
        registerFineMeal("mnera:fd_fried_rice", "farmersdelight:fried_rice", "farmersdelight", 30, false);
        registerFineMeal("mnera:fd_pasta_with_meatballs", "farmersdelight:pasta_with_meatballs", "farmersdelight", 38, false);
        registerFineMeal("mnera:fd_roast_chicken", "farmersdelight:roast_chicken", "farmersdelight", 44, true);
        registerFineMeal("mnera:fd_honey_glazed_ham", "farmersdelight:honey_glazed_ham", "farmersdelight", 46, true);
        registerFineMeal("mnera:fd_shepherds_pie", "farmersdelight:shepherds_pie", "farmersdelight", 42, true);
        registerFineMeal("mnera:fd_stuffed_pumpkin", "farmersdelight:stuffed_pumpkin", "farmersdelight", 45, true);
    }

    private void registerKaleidoscopeCookeryGoods() {
        // Kaleidoscope Cookery crops (DESIGN.md Section 8.3)
        add(TradeGood.builder("mnera:kc_lettuce", "kaleidoscope_cookery:lettuce", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/lettuce").requiredModId("kaleidoscope_cookery")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.AUTUMN).biomeAffinities("plains").build());
        add(TradeGood.builder("mnera:kc_tomato", "kaleidoscope_cookery:tomato", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/tomato").requiredModId("kaleidoscope_cookery")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("mnera:kc_chili", "kaleidoscope_cookery:chili", GoodsCategory.AGRICULTURE, 5)
                .optionalTag("#crops/chili").requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("mnera:kc_red_chili", "kaleidoscope_cookery:red_chili", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("mnera:kc_green_chili", "kaleidoscope_cookery:green_chili", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("mnera:kc_rice", "kaleidoscope_cookery:rice", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/rice").requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("mnera:kc_wild_rice", "kaleidoscope_cookery:wild_rice", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("mnera:kc_tea_leaf", "kaleidoscope_cookery:tea_leaf", GoodsCategory.AGRICULTURE, 7)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(3, 7).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.SPRING, Season.SUMMER).build());

        // Kaleidoscope Cookery gourmet meals (Lv 7-10 Buy Orders!)
        registerFineMeal("mnera:kc_cooked_rice", "kaleidoscope_cookery:cooked_rice", "kaleidoscope_cookery", 24, false);
        registerFineMeal("mnera:kc_egg_fried_rice", "kaleidoscope_cookery:egg_fried_rice", "kaleidoscope_cookery", 32, false);
        registerFineMeal("mnera:kc_braised_beef", "kaleidoscope_cookery:braised_beef", "kaleidoscope_cookery", 44, true);
        registerFineMeal("mnera:kc_sweet_and_sour_ender_pearls", "kaleidoscope_cookery:sweet_and_sour_ender_pearls", "kaleidoscope_cookery", 58, false);
        registerFineMeal("mnera:kc_dongpo_pork", "kaleidoscope_cookery:dongpo_pork", "kaleidoscope_cookery", 48, true);
        registerFineMeal("mnera:kc_fondant_pie", "kaleidoscope_cookery:fondant_pie", "kaleidoscope_cookery", 42, false);
        registerFineMeal("mnera:kc_spicy_chicken", "kaleidoscope_cookery:spicy_chicken", "kaleidoscope_cookery", 38, true);
        registerFineMeal("mnera:kc_tomato_scrambled_eggs", "kaleidoscope_cookery:tomato_scrambled_eggs", "kaleidoscope_cookery", 30, false);
    }

    private void registerFineMeal(String id, String itemId, String modId, long basePrice, boolean heartyWinter) {
        TradeGood.Builder builder = TradeGood.builder(id, itemId, GoodsCategory.FOOD_FINE, basePrice)
                .requiredModId(modId)
                .levelRange(7, 10)
                .industrialTier(IndustrialTier.TIER_1)
                .sellable(false)
                .buyable(true)
                .quantityRange(3, 10)
                .harvestSeasons(Season.allFourSeasons());
        if (heartyWinter) {
            builder.demandBiomes("snowy", "taiga").demandSeasons(Season.WINTER);
        }
        add(builder.build());
    }

    /**
     * Registers or replaces a {@link TradeGood} by its unique {@link TradeGood#id()}.
     *
     * @param good trade good to register
     */
    public void register(TradeGood good) {
        add(good);
    }

    private void add(TradeGood good) {
        Objects.requireNonNull(good, "good must not be null");
        goods.put(good.id(), good);
    }

    /**
     * Removes a good by ID if present.
     *
     * @param goodId catalog ID
     * @return {@code true} if removed
     */
    public boolean unregister(String goodId) {
        return goodId != null && goods.remove(goodId) != null;
    }

    /**
     * Looks up a good by its catalog ID.
     *
     * @param goodId catalog ID
     * @return optional trade good
     */
    public Optional<TradeGood> getById(String goodId) {
        return Optional.ofNullable(goods.get(goodId));
    }

    /**
     * Finds the first good matching the given item registry ID.
     *
     * @param itemId item registry ID
     * @return optional trade good
     */
    public Optional<TradeGood> findByItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        String norm = itemId.trim().toLowerCase(Locale.ROOT);
        for (TradeGood good : goods.values()) {
            if (good.itemId().equals(norm)) {
                return Optional.of(good);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the best matching {@link TradeGood} for an active order, checking catalog ID
     * ({@code goodId}, e.g. {@code VillageTradeOrder#displayHint()}) first before falling back
     * to {@code itemId} (preferring entries whose buy/sell capability matches {@code preferBuyable}).
     *
     * @param goodId optional catalog ID
     * @param itemId item registry ID
     * @param preferBuyable {@code true} for village buy orders, {@code false} for village sell orders
     * @return optional matching trade good
     */
    public Optional<TradeGood> findOrderGood(String goodId, String itemId, boolean preferBuyable) {
        if (goodId != null && !goodId.isBlank()) {
            TradeGood direct = goods.get(goodId.trim());
            if (direct != null) {
                return Optional.of(direct);
            }
            for (TradeGood apiGood : com.tycooncraft.tyc2.api.TyC2RegistryApi.getRegisteredTradeGoods()) {
                if (apiGood.id().equals(goodId.trim())) {
                    return Optional.of(apiGood);
                }
            }
        }
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        String norm = itemId.trim().toLowerCase(Locale.ROOT);
        TradeGood fallback = null;
        for (TradeGood good : goods.values()) {
            if (!good.itemId().equals(norm)) {
                continue;
            }
            if (preferBuyable ? good.isBuyableByVillage() : good.isSellableByVillage()) {
                return Optional.of(good);
            }
            if (fallback == null) {
                fallback = good;
            }
        }
        for (TradeGood apiGood : com.tycooncraft.tyc2.api.TyC2RegistryApi.getRegisteredTradeGoods()) {
            if (!apiGood.itemId().equals(norm)) {
                continue;
            }
            if (preferBuyable ? apiGood.isBuyableByVillage() : apiGood.isSellableByVillage()) {
                return Optional.of(apiGood);
            }
            if (fallback == null) {
                fallback = apiGood;
            }
        }
        return Optional.ofNullable(fallback);
    }

    /**
     * Returns all goods registered in this catalog (unfiltered).
     *
     * @return unmodifiable list of all goods
     */
    public List<TradeGood> allGoods() {
        return Collections.unmodifiableList(new ArrayList<>(goods.values()));
    }

    /**
     * Returns all goods whose item exists in the live Minecraft registry.
     * Automatically includes any custom goods registered via {@link com.tycooncraft.tyc2.api.TyC2RegistryApi}.
     *
     * @param itemExists predicate checking {@code BuiltInRegistries.ITEM.containsKey(...)} (must not be {@code null})
     * @return unmodifiable list of live goods
     */
    public List<TradeGood> getAvailableGoods(Predicate<String> itemExists) {
        return getAvailableGoods(itemExists, null);
    }

    /**
     * Returns all goods whose item exists in the live Minecraft registry AND whose
     * optional {@code requiredModId} is loaded (when {@code modLoaded} is provided).
     *
     * @param itemExists predicate returning {@code true} if an item ID exists in the registry
     * @param modLoaded optional predicate returning {@code true} if a mod ID is loaded
     * @return unmodifiable list of active goods
     */
    public List<TradeGood> getAvailableGoods(Predicate<String> itemExists, Predicate<String> modLoaded) {
        return getAvailableGoods(itemExists, modLoaded, null);
    }

    /**
     * Returns all goods available in the live runtime registry.
     *
     * <p>Pass 1 includes every good whose concrete {@link TradeGood#itemId()} is registered and
     * whose {@link TradeGood#requiredModId()} (if set) is loaded. When {@code firstTagItemResolver}
     * is provided, Pass 2 additionally activates goods carrying a non-blank {@link TradeGood#optionalTag()}
     * whose primary provider mod is absent, substituting the first registered item belonging to that
     * tag as the display/trade item ID (while deduplicating overlapping tag+direction roles).
     *
     * @param itemExists predicate returning {@code true} if an item ID exists in the registry
     * @param modLoaded optional predicate returning {@code true} if a mod ID is loaded
     * @param firstTagItemResolver optional resolver mapping a tag spec (e.g. {@code "#ingots/tin"})
     *                             to the first registered item ID in that tag
     * @return unmodifiable list of active goods
     */
    public List<TradeGood> getAvailableGoods(
            Predicate<String> itemExists,
            Predicate<String> modLoaded,
            Function<String, Optional<String>> firstTagItemResolver) {
        Objects.requireNonNull(itemExists, "itemExists predicate must not be null");
        Map<String, TradeGood> merged = new LinkedHashMap<>(goods);
        for (TradeGood apiGood : com.tycooncraft.tyc2.api.TyC2RegistryApi.getRegisteredTradeGoods()) {
            merged.put(apiGood.id(), apiGood);
        }

        List<TradeGood> available = new ArrayList<>();
        Set<String> coveredGoodIds = new HashSet<>();
        Set<String> coveredTagRoles = new HashSet<>();

        // Pass 1: exact item exists and requiredModId (if any) is loaded
        for (TradeGood good : merged.values()) {
            if (good.requiredModId() != null && modLoaded != null && !modLoaded.test(good.requiredModId())) {
                continue;
            }
            if (itemExists.test(good.itemId())) {
                available.add(good);
                coveredGoodIds.add(good.id());
                recordTagRoles(coveredTagRoles, good);
            }
        }

        // Pass 2: tag-backed fallback activation when primary provider mod is absent
        if (firstTagItemResolver != null) {
            for (TradeGood good : merged.values()) {
                if (coveredGoodIds.contains(good.id())) {
                    continue;
                }
                String optTag = good.optionalTag();
                if (optTag == null || optTag.isBlank()) {
                    continue;
                }
                String tagKey = canonicalTagRoleKey(optTag);
                boolean needsBuy = good.isBuyableByVillage() && !coveredTagRoles.contains("B:" + tagKey);
                boolean needsSell = good.isSellableByVillage() && !coveredTagRoles.contains("S:" + tagKey);
                if (!needsBuy && !needsSell) {
                    continue;
                }
                Optional<String> resolvedOpt = firstTagItemResolver.apply(optTag);
                if (resolvedOpt == null || resolvedOpt.isEmpty()) {
                    continue;
                }
                String resolvedId = resolvedOpt.get().trim().toLowerCase(Locale.ROOT);
                if (resolvedId.isEmpty() || !itemExists.test(resolvedId)) {
                    continue;
                }
                TradeGood adapted = resolvedId.equals(good.itemId()) ? good : good.withItemId(resolvedId);
                available.add(adapted);
                coveredGoodIds.add(good.id());
                recordTagRoles(coveredTagRoles, adapted);
            }
        }
        return Collections.unmodifiableList(available);
    }

    private static void recordTagRoles(Set<String> coveredTagRoles, TradeGood good) {
        String optTag = good.optionalTag();
        if (optTag == null || optTag.isBlank()) {
            return;
        }
        String key = canonicalTagRoleKey(optTag);
        if (good.isBuyableByVillage()) {
            coveredTagRoles.add("B:" + key);
        }
        if (good.isSellableByVillage()) {
            coveredTagRoles.add("S:" + key);
        }
    }

    private static String canonicalTagRoleKey(String tagSpec) {
        return CommonTagAliases.displayTagLabel(tagSpec, false);
    }

    /**
     * Merges trade goods from datapack or external config JSON.
     * Supports single good object, {@code { "replace": bool, "goods": [...] }}, or top-level array {@code [...]}.
     * Entries with {@code "disabled": true} or {@code "remove": true} unregister that ID.
     *
     * @param json JSON text
     * @return number of entries processed
     */
    public int mergeFromJson(String json) {
        Object parsed = JsonLite.parse(json);
        if (parsed instanceof List<?>) {
            return applyGoodsList(JsonLite.asArray(parsed));
        }
        Map<String, Object> obj = JsonLite.asObject(parsed);
        if (obj == null) {
            throw new IllegalArgumentException("Goods JSON must be an object or array");
        }
        if (JsonLite.getBoolean(obj, "replace", false)) {
            goods.clear();
        }
        if (obj.containsKey("goods")) {
            List<Object> list = JsonLite.asArray(obj.get("goods"));
            return list == null ? 0 : applyGoodsList(list);
        }
        return applySingleEntry(obj) ? 1 : 0;
    }

    /**
     * Merges a seasonal harvest overlay JSON (such as {@code data/<ns>/tyc2/seasons/*.json})
     * mapping item IDs to harvest season lists:
     * {@code { "seasons": { "minecraft:wheat": ["SPRING", "SUMMER"] } }} or flat
     * {@code { "minecraft:wheat": ["SPRING", "SUMMER"] }}.
     *
     * @param json season overlay JSON text
     * @return number of catalog goods updated
     */
    public int mergeSeasonsOverlayJson(String json) {
        Map<String, Object> root = JsonLite.parseObject(json);
        Map<String, Object> entries = root.containsKey("seasons")
                ? JsonLite.asObject(root.get("seasons"))
                : root;
        if (entries == null) {
            return 0;
        }
        int updated = 0;
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_")) {
                continue;
            }
            List<Object> rawSeasons = JsonLite.asArray(entry.getValue());
            if (rawSeasons == null) {
                continue;
            }
            Set<Season> seasonSet = EnumSet.noneOf(Season.class);
            for (Object sObj : rawSeasons) {
                if (sObj instanceof String) {
                    String s = (String) sObj;
                    if ("ALL".equalsIgnoreCase(s)) {
                        seasonSet.addAll(Season.allFourSeasons());
                    } else {
                        Season parsed = Season.parse(s);
                        if (parsed.isActive()) {
                            seasonSet.add(parsed);
                        }
                    }
                }
            }
            String normItem = key.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, TradeGood> goodEntry : new ArrayList<>(goods.entrySet())) {
                TradeGood existing = goodEntry.getValue();
                if (existing.itemId().equals(normItem) || existing.id().equals(key)) {
                    goods.put(goodEntry.getKey(), existing.withHarvestSeasons(seasonSet));
                    updated++;
                }
            }
        }
        return updated;
    }

    /**
     * Loads and merges {@code config/tyc2/custom_goods.json}, optionally creating a clean starter file.
     *
     * @param path file path
     * @param createTemplateIfMissing whether to write a starter template if absent
     * @return number of goods applied
     * @throws IOException if I/O fails
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
     * Returns a starter JSON template for {@code config/tyc2/custom_goods.json}.
     *
     * @return pretty-printed JSON template
     */
    public static String defaultCustomConfigTemplate() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_comment", "Add or override village trade goods below. Set 'disabled': true on an existing id to remove it.");
        root.put("replace", false);
        root.put("goods", Collections.emptyList());
        return JsonLite.toPrettyJson(root);
    }

    /**
     * Serializes the entire catalog to pretty-printed JSON.
     *
     * @return JSON text
     */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>(goods.size());
        for (TradeGood good : goods.values()) {
            list.add(good.toDataMap());
        }
        root.put("goods", list);
        return JsonLite.toPrettyJson(root);
    }

    private int applyGoodsList(List<Object> list) {
        int count = 0;
        for (Object item : list) {
            Map<String, Object> map = JsonLite.asObject(item);
            if (map != null && applySingleEntry(map)) {
                count++;
            }
        }
        return count;
    }

    private boolean applySingleEntry(Map<String, Object> map) {
        boolean disabled = JsonLite.getBoolean(map, "disabled", false)
                || JsonLite.getBoolean(map, "remove", false);
        if (disabled) {
            String id = JsonLite.getString(map, "id", null);
            return id != null && unregister(id);
        }
        register(TradeGood.fromDataMap(map));
        return true;
    }
}
