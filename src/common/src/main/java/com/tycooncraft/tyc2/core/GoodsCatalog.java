package com.tycooncraft.tyc2.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Complete built-in and extensible catalog of tradeable goods spanning Village Levels 1–10
 * across Vanilla Minecraft, IndustrialCraft² ({@code ic2}), Farmer's Delight ({@code farmersdelight}),
 * and Kaleidoscope Cookery ({@code kaleidoscope_cookery}).
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
     * Creates a catalog pre-populated with all built-in Vanilla, IC2, Farmer's Delight,
     * and Kaleidoscope Cookery trade goods.
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
        registerFarmersDelightGoods();
        registerKaleidoscopeCookeryGoods();
    }

    private void registerVanillaRuralSells() {
        // Textiles & animal goods (Lv 1-4)
        add(TradeGood.builder("tyc2:white_wool", "minecraft:white_wool", GoodsCategory.TEXTILE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("tyc2:white_carpet", "minecraft:white_carpet", GoodsCategory.TEXTILE, 3)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(8, 20)
                .biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("tyc2:string", "minecraft:string", GoodsCategory.TEXTILE, 2)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(10, 28)
                .biomeAffinities("plains", "taiga").build());
        add(TradeGood.builder("tyc2:leather", "minecraft:leather", GoodsCategory.TEXTILE, 5)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(6, 18)
                .biomeAffinities("plains", "savanna", "taiga").build());
        add(TradeGood.builder("tyc2:feather", "minecraft:feather", GoodsCategory.TEXTILE, 2)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:rabbit_hide", "minecraft:rabbit_hide", GoodsCategory.TEXTILE, 4)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(6, 16)
                .biomeAffinities("desert", "snowy").build());

        // Monster drops (Lv 1-5)
        add(TradeGood.builder("tyc2:rotten_flesh", "minecraft:rotten_flesh", GoodsCategory.MONSTER_DROP, 1)
                .levelRange(1, 4).sellable(true).buyable(false).quantityRange(16, 40).build());
        add(TradeGood.builder("tyc2:bone", "minecraft:bone", GoodsCategory.MONSTER_DROP, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:spider_eye", "minecraft:spider_eye", GoodsCategory.MONSTER_DROP, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(6, 18).build());
        add(TradeGood.builder("tyc2:gunpowder", "minecraft:gunpowder", GoodsCategory.MONSTER_DROP, 7)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(6, 16).build());
        add(TradeGood.builder("tyc2:slime_ball", "minecraft:slime_ball", GoodsCategory.MONSTER_DROP, 9)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(4, 14)
                .biomeAffinities("swamp").build());

        // Forestry, winter goods, desert/biome staples (Lv 1-5)
        add(TradeGood.builder("tyc2:stick", "minecraft:stick", GoodsCategory.FORESTRY, 1)
                .levelRange(1, 3).sellable(true).buyable(false).quantityRange(16, 48)
                .biomeAffinities("plains", "taiga", "savanna", "jungle", "swamp").build());
        add(TradeGood.builder("tyc2:oak_log", "minecraft:oak_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:spruce_log", "minecraft:spruce_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("taiga", "snowy").build());
        add(TradeGood.builder("tyc2:acacia_log", "minecraft:acacia_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("savanna").build());
        add(TradeGood.builder("tyc2:jungle_log", "minecraft:jungle_log", GoodsCategory.FORESTRY, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("jungle").build());
        add(TradeGood.builder("tyc2:clay_ball", "minecraft:clay_ball", GoodsCategory.BUILDING, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("swamp").build());
        add(TradeGood.builder("tyc2:lily_pad", "minecraft:lily_pad", GoodsCategory.AGRICULTURE, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(6, 18)
                .biomeAffinities("swamp").build());
        add(TradeGood.builder("tyc2:honey_bottle", "minecraft:honey_bottle", GoodsCategory.AGRICULTURE, 6)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(4, 12)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:beef", "minecraft:beef", GoodsCategory.FOOD_BASIC, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 20)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:mutton", "minecraft:mutton", GoodsCategory.FOOD_BASIC, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 20)
                .biomeAffinities("snowy", "plains").build());
        add(TradeGood.builder("tyc2:sand", "minecraft:sand", GoodsCategory.BUILDING, 2)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(16, 48)
                .biomeAffinities("desert").build());
        add(TradeGood.builder("tyc2:sandstone", "minecraft:sandstone", GoodsCategory.BUILDING, 4)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("desert").build());
        add(TradeGood.builder("tyc2:cactus", "minecraft:cactus", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("desert").build());
        add(TradeGood.builder("tyc2:snow_block", "minecraft:snow_block", GoodsCategory.BUILDING, 2)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(16, 48)
                .biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:packed_ice", "minecraft:packed_ice", GoodsCategory.BUILDING, 8)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:saddle", "minecraft:saddle", GoodsCategory.TEXTILE, 28)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(1, 3)
                .biomeAffinities("plains", "savanna").build());
    }

    private void registerVanillaCrops() {
        // Seasonal Vanilla Crops (DESIGN.md Section 8.3)
        add(TradeGood.builder("tyc2:wheat", "minecraft:wheat", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:carrot", "minecraft:carrot", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:potato", "minecraft:potato", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("taiga", "plains").build());
        add(TradeGood.builder("tyc2:beetroot", "minecraft:beetroot", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.AUTUMN, Season.WINTER)
                .biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:pumpkin", "minecraft:pumpkin", GoodsCategory.AGRICULTURE, 5)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(6, 18)
                .harvestSeasons(Season.AUTUMN)
                .biomeAffinities("plains", "taiga").build());
        add(TradeGood.builder("tyc2:melon_slice", "minecraft:melon_slice", GoodsCategory.AGRICULTURE, 2)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 36)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("savanna", "jungle").build());
        add(TradeGood.builder("tyc2:sweet_berries", "minecraft:sweet_berries", GoodsCategory.AGRICULTURE, 3)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("taiga").build());
        add(TradeGood.builder("tyc2:apple", "minecraft:apple", GoodsCategory.AGRICULTURE, 5)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(6, 18)
                .harvestSeasons(Season.AUTUMN)
                .biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:sugar_cane", "minecraft:sugar_cane", GoodsCategory.AGRICULTURE, 3)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("jungle", "swamp").build());
        add(TradeGood.builder("tyc2:cocoa_beans", "minecraft:cocoa_beans", GoodsCategory.AGRICULTURE, 5)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("jungle").build());
        add(TradeGood.builder("tyc2:bamboo", "minecraft:bamboo", GoodsCategory.AGRICULTURE, 2)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(16, 48)
                .harvestSeasons(Season.SUMMER)
                .biomeAffinities("jungle").build());

        // Year-round cave/marine crops (All 4 seasons)
        add(TradeGood.builder("tyc2:brown_mushroom", "minecraft:brown_mushroom", GoodsCategory.AGRICULTURE, 4)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.allFourSeasons()).biomeAffinities("taiga", "swamp").build());
        add(TradeGood.builder("tyc2:red_mushroom", "minecraft:red_mushroom", GoodsCategory.AGRICULTURE, 4)
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.allFourSeasons()).biomeAffinities("taiga", "swamp").build());
        add(TradeGood.builder("tyc2:kelp", "minecraft:kelp", GoodsCategory.AGRICULTURE, 2)
                .levelRange(1, 5).sellable(true).buyable(false).quantityRange(16, 40)
                .harvestSeasons(Season.allFourSeasons()).build());
        add(TradeGood.builder("tyc2:glow_berries", "minecraft:glow_berries", GoodsCategory.AGRICULTURE, 5)
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.allFourSeasons()).build());

        // Lv 7-10 Metropolis staple crop demand
        add(TradeGood.builder("tyc2:metro_wheat_demand", "minecraft:wheat", GoodsCategory.AGRICULTURE, 4)
                .levelRange(7, 10).sellable(false).buyable(true).quantityRange(16, 40)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("tyc2:metro_carrot_demand", "minecraft:carrot", GoodsCategory.AGRICULTURE, 4)
                .levelRange(7, 10).sellable(false).buyable(true).quantityRange(16, 40)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("tyc2:metro_potato_demand", "minecraft:potato", GoodsCategory.AGRICULTURE, 4)
                .levelRange(7, 10).sellable(false).buyable(true).quantityRange(16, 40)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN).build());
    }

    private void registerVanillaMiningAndIndustrialBuys() {
        // Lv 1-4 Rural villages buy mining & basic supplies
        add(TradeGood.builder("tyc2:coal", "minecraft:coal", GoodsCategory.MINING, 4)
                .levelRange(1, 5).sellable(false).buyable(true).quantityRange(12, 32)
                .biomeAffinities("taiga").build());
        add(TradeGood.builder("tyc2:charcoal", "minecraft:charcoal", GoodsCategory.MINING, 4)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(12, 32).build());
        add(TradeGood.builder("tyc2:copper_ingot", "minecraft:copper_ingot", GoodsCategory.METAL, 5)
                .optionalTag("#ingots/copper").levelRange(1, 5).sellable(false).buyable(true)
                .quantityRange(8, 24).biomeAffinities("savanna").build());
        add(TradeGood.builder("tyc2:iron_ingot", "minecraft:iron_ingot", GoodsCategory.METAL, 9)
                .optionalTag("#ingots/iron").levelRange(1, 6).sellable(false).buyable(true)
                .quantityRange(6, 20).biomeAffinities("taiga").build());
        add(TradeGood.builder("tyc2:cobblestone", "minecraft:cobblestone", GoodsCategory.MINING, 1)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(32, 64).build());
        add(TradeGood.builder("tyc2:stone", "minecraft:stone", GoodsCategory.MINING, 2)
                .levelRange(1, 5).sellable(false).buyable(true).quantityRange(24, 64).build());
        add(TradeGood.builder("tyc2:flint", "minecraft:flint", GoodsCategory.MINING, 3)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(10, 24).build());
        add(TradeGood.builder("tyc2:torch", "minecraft:torch", GoodsCategory.BUILDING, 2)
                .levelRange(1, 4).sellable(false).buyable(true).quantityRange(16, 48).build());
    }

    private void registerVanillaMidTierSellsAndBuys() {
        // Lv 4-6 Developing town sells
        add(TradeGood.builder("tyc2:paper", "minecraft:paper", GoodsCategory.TEXTILE, 4)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32).build());
        add(TradeGood.builder("tyc2:book", "minecraft:book", GoodsCategory.TEXTILE, 12)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(4, 12).build());
        add(TradeGood.builder("tyc2:glass", "minecraft:glass", GoodsCategory.BUILDING, 4)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("desert").build());
        add(TradeGood.builder("tyc2:brick", "minecraft:brick", GoodsCategory.BUILDING, 5)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32).build());
        add(TradeGood.builder("tyc2:terracotta", "minecraft:terracotta", GoodsCategory.BUILDING, 5)
                .levelRange(4, 7).sellable(true).buyable(false).quantityRange(12, 32)
                .biomeAffinities("desert", "savanna").build());
        add(TradeGood.builder("tyc2:red_dye", "minecraft:red_dye", GoodsCategory.TEXTILE, 4)
                .levelRange(4, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .biomeAffinities("savanna").build());

        // Basic enchanted books (Lv 4-6, Tier 1)
        add(TradeGood.builder("tyc2:book_unbreaking_1", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 90)
                .levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:unbreaking:1").biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:book_efficiency_2", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 110)
                .levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:efficiency:2").biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:book_protection_2", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 110)
                .levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:protection:2").biomeAffinities("snowy").build());

        // Lv 4-6 Developing town buys (refined metals, redstone, components, cooked foods)
        add(TradeGood.builder("tyc2:gold_ingot", "minecraft:gold_ingot", GoodsCategory.METAL, 22)
                .optionalTag("#ingots/gold").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(4, 14).biomeAffinities("desert").build());
        add(TradeGood.builder("tyc2:redstone", "minecraft:redstone", GoodsCategory.INDUSTRIAL, 6)
                .levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(12, 32).build());
        add(TradeGood.builder("tyc2:lapis_lazuli", "minecraft:lapis_lazuli", GoodsCategory.MINING, 7)
                .levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(8, 24).biomeAffinities("desert").build());
        add(TradeGood.builder("tyc2:quartz", "minecraft:quartz", GoodsCategory.MINING, 10)
                .levelRange(5, 8).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:piston", "minecraft:piston", GoodsCategory.INDUSTRIAL, 24)
                .levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 8).build());
        add(TradeGood.builder("tyc2:repeater", "minecraft:repeater", GoodsCategory.INDUSTRIAL, 20)
                .levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(3, 10).build());
        add(TradeGood.builder("tyc2:bread", "minecraft:bread", GoodsCategory.FOOD_BASIC, 7)
                .levelRange(4, 6).sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:baked_potato", "minecraft:baked_potato", GoodsCategory.FOOD_BASIC, 7)
                .levelRange(4, 6).sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:cooked_porkchop", "minecraft:cooked_porkchop", GoodsCategory.FOOD_BASIC, 11)
                .levelRange(4, 7).sellable(false).buyable(true).quantityRange(6, 18).build());
    }

    private void registerVanillaHighTierAndEndgame() {
        // Lv 7-10 Metropolis buys: fine foods & high-tier industrial supplies
        add(TradeGood.builder("tyc2:golden_carrot", "minecraft:golden_carrot", GoodsCategory.FOOD_FINE, 26)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(6, 18).build());
        add(TradeGood.builder("tyc2:cooked_beef", "minecraft:cooked_beef", GoodsCategory.FOOD_FINE, 14)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_0)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:pumpkin_pie", "minecraft:pumpkin_pie", GoodsCategory.FOOD_FINE, 18)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(6, 16).build());
        add(TradeGood.builder("tyc2:cake", "minecraft:cake", GoodsCategory.FOOD_FINE, 32)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 6).build());
        add(TradeGood.builder("tyc2:rabbit_stew", "minecraft:rabbit_stew", GoodsCategory.FOOD_FINE, 28)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(3, 10).build());
        add(TradeGood.builder("tyc2:glowstone_dust", "minecraft:glowstone_dust", GoodsCategory.INDUSTRIAL, 14)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:blaze_powder", "minecraft:blaze_powder", GoodsCategory.INDUSTRIAL, 24)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(4, 14).build());
        add(TradeGood.builder("tyc2:obsidian", "minecraft:obsidian", GoodsCategory.INDUSTRIAL, 18)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(6, 18).build());
        add(TradeGood.builder("tyc2:diamond", "minecraft:diamond", GoodsCategory.METAL, 140)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(1, 4).build());

        // Lv 7-10 Metropolis sells: High-level enchanted books, rare alloys/treasures, endgame
        add(TradeGood.builder("tyc2:book_mending", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 480)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:mending:1").biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:book_efficiency_5", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 420)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:efficiency:5").biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:book_fortune_3", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 450)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:fortune:3").biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:book_unbreaking_3", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 360)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:unbreaking:3").biomeAffinities("snowy").build());
        add(TradeGood.builder("tyc2:book_sharpness_5", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 430)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:sharpness:5").build());
        add(TradeGood.builder("tyc2:book_protection_4", "minecraft:enchanted_book", GoodsCategory.ENCHANTED_BOOK, 410)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2)
                .enchantmentSpec("minecraft:protection:4").build());

        add(TradeGood.builder("tyc2:name_tag", "minecraft:name_tag", GoodsCategory.RARE_ALLOY, 85)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(1, 4).build());
        add(TradeGood.builder("tyc2:amethyst_shard", "minecraft:amethyst_shard", GoodsCategory.RARE_ALLOY, 24)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(6, 18).build());
        add(TradeGood.builder("tyc2:prismarine_crystals", "minecraft:prismarine_crystals", GoodsCategory.RARE_ALLOY, 30)
                .levelRange(7, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(true).buyable(false).quantityRange(6, 16).build());
        add(TradeGood.builder("tyc2:echo_shard", "minecraft:echo_shard", GoodsCategory.RARE_ALLOY, 320)
                .levelRange(8, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 3).build());
        add(TradeGood.builder("tyc2:heart_of_the_sea", "minecraft:heart_of_the_sea", GoodsCategory.RARE_ALLOY, 650)
                .levelRange(8, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(1, 2).build());
        add(TradeGood.builder("tyc2:totem_of_undying", "minecraft:totem_of_undying", GoodsCategory.ENDGAME, 850)
                .levelRange(9, 10).industrialTier(IndustrialTier.TIER_4)
                .sellable(true).buyable(false).quantityRange(1, 1).build());
        add(TradeGood.builder("tyc2:netherite_scrap", "minecraft:netherite_scrap", GoodsCategory.RARE_ALLOY, 520)
                .levelRange(8, 10).industrialTier(IndustrialTier.TIER_4)
                .sellable(true).buyable(false).quantityRange(1, 2).build());
        add(TradeGood.builder("tyc2:ancient_debris", "minecraft:ancient_debris", GoodsCategory.ENDGAME, 950)
                .levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(false).quantityRange(1, 2).build());
        add(TradeGood.builder("tyc2:netherite_upgrade_template", "minecraft:netherite_upgrade_smithing_template", GoodsCategory.ENDGAME, 1100)
                .levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(false).quantityRange(1, 1).build());
    }

    private void registerIc2Goods() {
        // IC2 crops & forestry (DESIGN.md Section 8.3; IC2 1.0.5 sticky resin ID is ic2:resin)
        add(TradeGood.builder("tyc2:ic2_sticky_resin", "ic2:resin", GoodsCategory.FORESTRY, 6)
                .requiredModId("ic2").levelRange(1, 5).industrialTier(IndustrialTier.TIER_0)
                .sellable(true).buyable(false).quantityRange(6, 20)
                .harvestSeasons(Season.SPRING, Season.SUMMER, Season.AUTUMN)
                .biomeAffinities("plains", "taiga").build());
        add(TradeGood.builder("tyc2:ic2_rubber", "ic2:rubber", GoodsCategory.INDUSTRIAL, 10)
                .requiredModId("ic2").levelRange(3, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(6, 18).build());
        add(TradeGood.builder("tyc2:ic2_hops", "ic2:hops", GoodsCategory.AGRICULTURE, 12)
                .requiredModId("ic2").levelRange(3, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(6, 16)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("tyc2:ic2_coffee_beans", "ic2:coffee_beans", GoodsCategory.AGRICULTURE, 18)
                .requiredModId("ic2").levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(true).buyable(false).quantityRange(4, 14)
                .harvestSeasons(Season.SUMMER).build());
        add(TradeGood.builder("tyc2:ic2_terra_wart", "ic2:terra_wart", GoodsCategory.AGRICULTURE, 45)
                .requiredModId("ic2").levelRange(6, 9).industrialTier(IndustrialTier.TIER_2)
                .sellable(true).buyable(false).quantityRange(3, 10)
                .harvestSeasons(Season.allFourSeasons()).build());

        // IC2 metals & industrial components
        add(TradeGood.builder("tyc2:ic2_tin_ingot", "ic2:tin_ingot", GoodsCategory.METAL, 6)
                .optionalTag("#ingots/tin").requiredModId("ic2")
                .levelRange(1, 5).industrialTier(IndustrialTier.TIER_0)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:ic2_lead_ingot", "ic2:lead_ingot", GoodsCategory.METAL, 7)
                .optionalTag("#ingots/lead").requiredModId("ic2")
                .levelRange(1, 5).industrialTier(IndustrialTier.TIER_0)
                .sellable(false).buyable(true).quantityRange(8, 24).build());
        add(TradeGood.builder("tyc2:ic2_bronze_ingot", "ic2:bronze_ingot", GoodsCategory.METAL, 14)
                .optionalTag("#ingots/bronze").requiredModId("ic2")
                .levelRange(4, 9).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(6, 18).build());
        add(TradeGood.builder("tyc2:ic2_silver_ingot", "ic2:silver_ingot", GoodsCategory.METAL, 20)
                .optionalTag("#ingots/silver").requiredModId("ic2")
                .levelRange(4, 8).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(4, 14).build());
        add(TradeGood.builder("tyc2:ic2_steel_ingot", "ic2:steel_ingot", GoodsCategory.RARE_ALLOY, 26)
                .optionalTag("#ingots/steel").requiredModId("ic2")
                .levelRange(4, 10).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(4, 14).build());
        add(TradeGood.builder("tyc2:ic2_circuit", "ic2:circuit", GoodsCategory.INDUSTRIAL, 34)
                .requiredModId("ic2").levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 8).build());
        add(TradeGood.builder("tyc2:ic2_advanced_circuit", "ic2:advanced_circuit", GoodsCategory.INDUSTRIAL, 85)
                .requiredModId("ic2").levelRange(6, 9).industrialTier(IndustrialTier.TIER_2)
                .sellable(false).buyable(true).quantityRange(1, 5).build());
        add(TradeGood.builder("tyc2:ic2_copper_cable", "ic2:copper_cable", GoodsCategory.INDUSTRIAL, 5)
                .requiredModId("ic2").levelRange(4, 6).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(12, 32).build());
        add(TradeGood.builder("tyc2:ic2_re_battery", "ic2:re_battery", GoodsCategory.INDUSTRIAL, 28)
                .requiredModId("ic2").levelRange(4, 7).industrialTier(IndustrialTier.TIER_1)
                .sellable(false).buyable(true).quantityRange(2, 6).build());
        add(TradeGood.builder("tyc2:ic2_energy_crystal", "ic2:energy_crystal", GoodsCategory.INDUSTRIAL, 180)
                .requiredModId("ic2").levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(false).buyable(true).quantityRange(1, 3).build());

        // IC2 rare alloys & Lv 10 Endgame Iridium Shard
        add(TradeGood.builder("tyc2:ic2_alloy", "ic2:alloy", GoodsCategory.RARE_ALLOY, 120)
                .requiredModId("ic2").levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(2, 6).build());
        add(TradeGood.builder("tyc2:ic2_carbon_plate", "ic2:carbon_plate", GoodsCategory.RARE_ALLOY, 95)
                .requiredModId("ic2").levelRange(7, 10).industrialTier(IndustrialTier.TIER_3)
                .sellable(true).buyable(false).quantityRange(2, 6).build());
        add(TradeGood.builder("tyc2:ic2_iridium_shard", "ic2:iridium_shard", GoodsCategory.ENDGAME, 900)
                .requiredModId("ic2").levelRange(10, 10).industrialTier(IndustrialTier.TIER_5)
                .sellable(true).buyable(false).quantityRange(1, 3).build());
    }

    private void registerFarmersDelightGoods() {
        // Farmer's Delight crops (DESIGN.md Section 8.3)
        add(TradeGood.builder("tyc2:fd_onion", "farmersdelight:onion", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/onion").requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.AUTUMN).biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:fd_cabbage", "farmersdelight:cabbage", GoodsCategory.AGRICULTURE, 5)
                .optionalTag("#crops/cabbage").requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SPRING, Season.AUTUMN, Season.WINTER).biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("tyc2:fd_cabbage_leaf", "farmersdelight:cabbage_leaf", GoodsCategory.AGRICULTURE, 3)
                .requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(12, 32)
                .harvestSeasons(Season.SPRING, Season.AUTUMN, Season.WINTER).biomeAffinities("plains", "snowy").build());
        add(TradeGood.builder("tyc2:fd_tomato", "farmersdelight:tomato", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/tomato").requiredModId("farmersdelight")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna", "plains").build());
        add(TradeGood.builder("tyc2:fd_rice", "farmersdelight:rice", GoodsCategory.AGRICULTURE, 4)
                .optionalTag("#crops/rice").requiredModId("farmersdelight")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("tyc2:fd_rice_panicle", "farmersdelight:rice_panicle", GoodsCategory.AGRICULTURE, 4)
                .requiredModId("farmersdelight")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());

        // Farmer's Delight hearty fine meals (Lv 7-10 Buy Orders!)
        registerFineMeal("tyc2:fd_beef_stew", "farmersdelight:beef_stew", "farmersdelight", 34);
        registerFineMeal("tyc2:fd_chicken_soup", "farmersdelight:chicken_soup", "farmersdelight", 32);
        registerFineMeal("tyc2:fd_vegetable_soup", "farmersdelight:vegetable_soup", "farmersdelight", 28);
        registerFineMeal("tyc2:fd_fried_rice", "farmersdelight:fried_rice", "farmersdelight", 30);
        registerFineMeal("tyc2:fd_pasta_with_meatballs", "farmersdelight:pasta_with_meatballs", "farmersdelight", 38);
        registerFineMeal("tyc2:fd_roast_chicken", "farmersdelight:roast_chicken", "farmersdelight", 44);
        registerFineMeal("tyc2:fd_honey_glazed_ham", "farmersdelight:honey_glazed_ham", "farmersdelight", 46);
        registerFineMeal("tyc2:fd_shepherds_pie", "farmersdelight:shepherds_pie", "farmersdelight", 42);
        registerFineMeal("tyc2:fd_stuffed_pumpkin", "farmersdelight:stuffed_pumpkin", "farmersdelight", 45);
    }

    private void registerKaleidoscopeCookeryGoods() {
        // Kaleidoscope Cookery crops (DESIGN.md Section 8.3)
        add(TradeGood.builder("tyc2:kc_lettuce", "kaleidoscope_cookery:lettuce", GoodsCategory.AGRICULTURE, 4)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SPRING, Season.AUTUMN).biomeAffinities("plains").build());
        add(TradeGood.builder("tyc2:kc_tomato", "kaleidoscope_cookery:tomato", GoodsCategory.AGRICULTURE, 4)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(1, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("tyc2:kc_chili", "kaleidoscope_cookery:chili", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("tyc2:kc_red_chili", "kaleidoscope_cookery:red_chili", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("tyc2:kc_green_chili", "kaleidoscope_cookery:green_chili", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).biomeAffinities("savanna").build());
        add(TradeGood.builder("tyc2:kc_rice", "kaleidoscope_cookery:rice", GoodsCategory.AGRICULTURE, 4)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(10, 28)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("tyc2:kc_wild_rice", "kaleidoscope_cookery:wild_rice", GoodsCategory.AGRICULTURE, 5)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(2, 6).sellable(true).buyable(false).quantityRange(8, 24)
                .harvestSeasons(Season.SUMMER, Season.AUTUMN).build());
        add(TradeGood.builder("tyc2:kc_tea_leaf", "kaleidoscope_cookery:tea_leaf", GoodsCategory.AGRICULTURE, 7)
                .requiredModId("kaleidoscope_cookery")
                .levelRange(3, 7).sellable(true).buyable(false).quantityRange(8, 20)
                .harvestSeasons(Season.SPRING, Season.SUMMER).build());

        // Kaleidoscope Cookery gourmet meals (Lv 7-10 Buy Orders!)
        registerFineMeal("tyc2:kc_cooked_rice", "kaleidoscope_cookery:cooked_rice", "kaleidoscope_cookery", 24);
        registerFineMeal("tyc2:kc_egg_fried_rice", "kaleidoscope_cookery:egg_fried_rice", "kaleidoscope_cookery", 32);
        registerFineMeal("tyc2:kc_braised_beef", "kaleidoscope_cookery:braised_beef", "kaleidoscope_cookery", 44);
        registerFineMeal("tyc2:kc_sweet_and_sour_ender_pearls", "kaleidoscope_cookery:sweet_and_sour_ender_pearls", "kaleidoscope_cookery", 58);
        registerFineMeal("tyc2:kc_dongpo_pork", "kaleidoscope_cookery:dongpo_pork", "kaleidoscope_cookery", 48);
        registerFineMeal("tyc2:kc_fondant_pie", "kaleidoscope_cookery:fondant_pie", "kaleidoscope_cookery", 42);
        registerFineMeal("tyc2:kc_spicy_chicken", "kaleidoscope_cookery:spicy_chicken", "kaleidoscope_cookery", 38);
        registerFineMeal("tyc2:kc_tomato_scrambled_eggs", "kaleidoscope_cookery:tomato_scrambled_eggs", "kaleidoscope_cookery", 30);
    }

    private void registerFineMeal(String id, String itemId, String modId, long basePrice) {
        add(TradeGood.builder(id, itemId, GoodsCategory.FOOD_FINE, basePrice)
                .requiredModId(modId)
                .levelRange(7, 10)
                .industrialTier(IndustrialTier.TIER_1)
                .sellable(false)
                .buyable(true)
                .quantityRange(3, 10)
                .harvestSeasons(Season.allFourSeasons())
                .build());
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
        Objects.requireNonNull(itemExists, "itemExists predicate must not be null");
        Map<String, TradeGood> merged = new LinkedHashMap<>(goods);
        for (TradeGood apiGood : com.tycooncraft.tyc2.api.TyC2RegistryApi.getRegisteredTradeGoods()) {
            merged.put(apiGood.id(), apiGood);
        }

        List<TradeGood> available = new ArrayList<>();
        for (TradeGood good : merged.values()) {
            if (good.requiredModId() != null && modLoaded != null && !modLoaded.test(good.requiredModId())) {
                continue;
            }
            if (itemExists.test(good.itemId())) {
                available.add(good);
            }
        }
        return Collections.unmodifiableList(available);
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
