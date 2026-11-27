#!/usr/bin/env python3
"""Version-aware resource, model, recipe, loot table, tag, and datapack preset generator
for TycoonCraft²: The New Era (tyc2).

Supports four targets with exact Minecraft/loader JSON schemas:
  - forge-1.19.2
  - neoforge-1.21.1
  - neoforge-26.1.2
  - neoforge-26.2

Usage:
  python3 src/datagen/generate_resources.py --target <target> --output <dir>
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import tempfile

TARGETS = ("forge-1.19.2", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2")
MOD_ID = "tyc2"
MARKER_NAME = ".tyc2-generated-output"

BLOCK_ITEMS = ("primitive_mint_machine", "mint_machine", "atm_machine", "cooperation_hub")
MINEABLE_BLOCKS = ("primitive_mint_machine", "mint_machine", "atm_machine")
COIN_ITEMS = (
    "coin_1",
    "coin_5",
    "coin_10",
    "coin_20",
    "coin_50",
    "coin_100",
    "coin_200",
    "coin_500",
    "coin_1000",
)
SPRITE_ITEMS = COIN_ITEMS + ("comm_terminal",)
ALL_ITEMS = BLOCK_ITEMS + SPRITE_ITEMS

# Unified Forge 1.19.2 <-> NeoForge 1.21+/26.x common tag rename table (aligned with CommonTagAliases.java)
# Format: canonical_key -> (forge_1_19_2_path, neoforge_1_21_plus_path)
COMMON_TAG_RENAMES: dict[str, tuple[str, str]] = {
    "stone": ("stone", "stones"),
    "stones": ("stone", "stones"),
    "cobblestone": ("cobblestone", "cobblestones"),
    "cobblestones": ("cobblestone", "cobblestones"),
    "obsidian": ("obsidian", "obsidians"),
    "obsidians": ("obsidian", "obsidians"),
    "glass": ("glass", "glass_blocks"),
    "glass_blocks": ("glass", "glass_blocks"),
    "sand": ("sand", "sands"),
    "sands": ("sand", "sands"),
    "gravel": ("gravel", "gravels"),
    "gravels": ("gravel", "gravels"),
    "slimeballs": ("slimeballs", "slime_balls"),
    "slime_balls": ("slimeballs", "slime_balls"),
    "leather": ("leather", "leathers"),
    "leathers": ("leather", "leathers"),
    "string": ("string", "strings"),
    "strings": ("string", "strings"),
    "gunpowder": ("gunpowder", "gunpowders"),
    "gunpowders": ("gunpowder", "gunpowders"),
    "netherrack": ("netherrack", "netherracks"),
    "netherracks": ("netherrack", "netherracks"),
    "shears": ("shears", "tools/shear"),
    "tools/shear": ("shears", "tools/shear"),
    "tools/wrench": ("tools/wrench", "tools/wrench"),
    "wrenches": ("tools/wrench", "tools/wrench"),
    "bread": ("bread", "foods/bread"),
    "foods/bread": ("bread", "foods/bread"),
}


def resolve_common_tag(path: str, forge: bool) -> str:
    """Resolves a bare common tag path to '#forge:<path>' (1.19.2) or '#c:<path>' (1.21+)."""
    clean = path.lstrip("#")
    if clean in COMMON_TAG_RENAMES:
        forge_path, neo_path = COMMON_TAG_RENAMES[clean]
        mapped = forge_path if forge else neo_path
    else:
        mapped = clean
    return f"#{'forge' if forge else 'c'}:{mapped}"


# Unconditional 100%-Vanilla crafting recipes (work with ZERO optional mods installed!)
# Format: recipe_name -> (result_item_name, pattern, key_map, count)
# Prefix '#common:<path>' resolves to '#forge:<path>' on Forge 1.19.2 and '#c:<path>' on NeoForge 1.21+.
SHAPED_RECIPES: dict[str, tuple[str, list[str], dict[str, str], int]] = {
    "primitive_mint_machine": (
        "primitive_mint_machine",
        ["ICI", "PFP", "SSS"],
        {
            "I": "#common:ingots/iron",
            "C": "#common:ingots/copper",
            "P": "minecraft:piston",
            "F": "minecraft:furnace",
            "S": "#minecraft:stone_bricks",
        },
        1,
    ),
    "atm_machine": (
        "atm_machine",
        ["IGI", "ECE", "IRI"],
        {
            "I": "#common:ingots/iron",
            "G": "#common:glass_panes",
            "E": "#common:ingots/gold",
            "C": "#common:chests/wooden",
            "R": "#common:dusts/redstone",
        },
        1,
    ),
    "comm_terminal": (
        "comm_terminal",
        ["  C", "IGI", "RAR"],
        {
            "C": "#common:ingots/copper",
            "I": "#common:ingots/iron",
            "G": "#common:glass_panes",
            "R": "#common:dusts/redstone",
            "A": "#common:ingots/gold",
        },
        1,
    ),
}

# Conditional Industrial Minting Machine recipes (IC2 industrial vs standalone redstone upgrade)
# Format: recipe_name -> (result_item_name, condition_mode, pattern, key_map, count)
CONDITIONAL_SHAPED_RECIPES: dict[str, tuple[str, str, list[str], dict[str, str], int]] = {
    "mint_machine_ic2": (
        "mint_machine",
        "ic2_loaded",
        ["PEP", "CMC", "PBP"],
        {
            "P": "#common:plates/iron",
            "E": "ic2:electric_motor",
            "C": "#common:circuits/basic",
            "M": "tyc2:primitive_mint_machine",
            "B": "ic2:machine",
        },
        1,
    ),
    "mint_machine_standalone": (
        "mint_machine",
        "ic2_not_loaded",
        ["IDI", "RMR", "GBG"],
        {
            "I": "#common:storage_blocks/iron",
            "D": "#common:gems/diamond",
            "R": "#common:storage_blocks/redstone",
            "M": "tyc2:primitive_mint_machine",
            "G": "#common:ingots/gold",
            "B": "minecraft:piston",
        },
        1,
    ),
}


# =============================================================================
# Built-in Datapack JSON Presets (aligned with MintRecipeRegistry, GoodsCatalog,
# VillageLevelTable, and WorkplaceCatalog in src/common)
# =============================================================================

def _mint_recipe(
    rid: str,
    ingredients: list[tuple[str, int]],
    denom: str,
    out_count: int,
    energy_eu: int,
    ticks: int,
    required_mod: str | None = None,
    primitive_allowed: bool = False,
) -> dict:
    ing_list = []
    for spec, count in ingredients:
        if spec.startswith("#"):
            ing_list.append({"tag": spec, "count": count})
        else:
            ing_list.append({"item": spec, "count": count})
    entry = {
        "id": rid,
        "ingredients": ing_list,
        "outputDenomination": denom,
        "outputCount": out_count,
        "energyEu": energy_eu,
        "durationTicks": ticks,
        "primitive_allowed": primitive_allowed,
    }
    if required_mod is not None:
        entry["requiredModId"] = required_mod
    return entry


MINT_RECIPES_VANILLA = {
    "_comment": "Built-in standalone Vanilla & common-tag minting recipes for TycoonCraft² (primitive_allowed=true only for pure-Vanilla 1x-100x).",
    "replace": False,
    "recipes": [
        _mint_recipe("tyc2:mint_copper_1", [("#ingots/copper", 1)], "X1", 2, 300, 60, primitive_allowed=True),
        _mint_recipe("tyc2:mint_iron_5", [("#ingots/iron", 1)], "X5", 1, 400, 80, primitive_allowed=True),
        _mint_recipe("tyc2:mint_iron_pair_10", [("#ingots/iron", 1), ("#ingots/iron", 1)], "X10", 1, 600, 100, primitive_allowed=True),
        _mint_recipe("tyc2:mint_gold_20", [("#ingots/gold", 1)], "X20", 1, 800, 100, primitive_allowed=True),
        _mint_recipe("tyc2:mint_gold_iron_50", [("#ingots/gold", 1), ("#ingots/iron", 1)], "X50", 1, 1200, 120, primitive_allowed=True),
        _mint_recipe("tyc2:mint_gold_emerald_100", [("#ingots/gold", 2), ("#gems/emerald", 1)], "X100", 1, 1800, 140, primitive_allowed=True),
        _mint_recipe("tyc2:mint_diamond_gold_200", [("#gems/diamond", 1), ("#ingots/gold", 2)], "X200", 1, 2500, 160, primitive_allowed=False),
        _mint_recipe("tyc2:mint_netherite_scrap_gold_500", [("minecraft:netherite_scrap", 1), ("#ingots/gold", 2)], "X500", 1, 4000, 200, primitive_allowed=False),
        _mint_recipe("tyc2:mint_netherite_ingot_500", [("#ingots/netherite", 1)], "X500", 2, 4000, 200, primitive_allowed=False),
        _mint_recipe("tyc2:mint_netherite_diamond_1000", [("#ingots/netherite", 1), ("#gems/diamond", 1)], "X1000", 1, 6000, 240, primitive_allowed=False),
    ],
}

MINT_RECIPES_IC2 = {
    "_comment": "Built-in IC2 & modded common-tag minting recipes (Industrial Minting Machine only: primitive_allowed=false).",
    "replace": False,
    "recipes": [
        _mint_recipe("tyc2:mint_tin_1", [("#ingots/tin", 1)], "X1", 2, 300, 60, primitive_allowed=False),
        _mint_recipe("tyc2:mint_copper_tin_5", [("#ingots/copper", 1), ("#ingots/tin", 1)], "X5", 2, 500, 80, primitive_allowed=False),
        _mint_recipe("tyc2:mint_bronze_10", [("#ingots/bronze", 1)], "X10", 1, 600, 100, primitive_allowed=False),
        _mint_recipe("tyc2:mint_silver_20", [("#ingots/silver", 1)], "X20", 1, 800, 100, primitive_allowed=False),
        _mint_recipe("tyc2:mint_steel_20", [("#ingots/steel", 1)], "X20", 1, 800, 100, primitive_allowed=False),
        _mint_recipe("tyc2:mint_refined_iron_20", [("ic2:refined_iron_ingot", 1)], "X20", 1, 800, 100, "ic2", primitive_allowed=False),
        _mint_recipe("tyc2:mint_gold_steel_50", [("#ingots/gold", 1), ("#ingots/steel", 1)], "X50", 1, 1200, 120, primitive_allowed=False),
        _mint_recipe("tyc2:mint_gold_silver_50", [("#ingots/gold", 1), ("#ingots/silver", 1)], "X50", 1, 1200, 120, primitive_allowed=False),
        _mint_recipe("tyc2:mint_gold_refined_iron_50", [("#ingots/gold", 1), ("ic2:refined_iron_ingot", 1)], "X50", 1, 1200, 120, "ic2", primitive_allowed=False),
        _mint_recipe("tyc2:mint_alloy_gold_100", [("ic2:alloy", 1), ("#ingots/gold", 1)], "X100", 1, 1800, 140, "ic2", primitive_allowed=False),
        _mint_recipe("tyc2:mint_mixed_metal_gold_100", [("ic2:mixed_metal_ingot", 1), ("#ingots/gold", 1)], "X100", 1, 1800, 140, "ic2", primitive_allowed=False),
        _mint_recipe("tyc2:mint_alloy_diamond_200", [("ic2:alloy", 2), ("#gems/diamond", 1)], "X200", 1, 2500, 160, "ic2", primitive_allowed=False),
        _mint_recipe("tyc2:mint_iridium_alloy_1000", [("#ingots/iridium", 1), ("ic2:alloy", 1)], "X1000", 1, 6000, 240, "ic2", primitive_allowed=False),
        _mint_recipe("tyc2:mint_iridium_ore_alloy_1000", [("ic2:iridium_ore", 1), ("ic2:alloy", 1)], "X1000", 1, 6000, 240, "ic2", primitive_allowed=False),
    ],
}


def _good(
    gid: str,
    item_id: str,
    category: str,
    base_price: int,
    min_level: int,
    max_level: int,
    sellable: bool,
    buyable: bool,
    min_qty: int,
    max_qty: int,
    tier: str = "TIER_0",
    seasons: list[str] | None = None,
    biomes: list[str] | None = None,
    biome_exclusive: bool = False,
    demand_biomes: list[str] | None = None,
    demand_seasons: list[str] | None = None,
    optional_tag: str | None = None,
    required_mod: str | None = None,
    enchantment: str | None = None,
) -> dict:
    entry: dict = {
        "id": gid,
        "itemId": item_id,
    }
    if optional_tag:
        entry["optionalTag"] = optional_tag
    if required_mod:
        entry["requiredModId"] = required_mod
    entry.update(
        {
            "category": category,
            "basePrice": base_price,
            "minLevel": min_level,
            "maxLevel": max_level,
            "industrialTierRequired": tier,
        }
    )
    if seasons:
        entry["harvestSeasons"] = seasons
    if biomes:
        entry["biomeAffinities"] = biomes
    if biome_exclusive:
        entry["biomeExclusive"] = True
    if demand_biomes:
        entry["demandBiomes"] = demand_biomes
    if demand_seasons:
        entry["demandSeasons"] = demand_seasons
    entry.update(
        {
            "isSellableByVillage": sellable,
            "isBuyableByVillage": buyable,
            "baseMinQty": min_qty,
            "baseMaxQty": max_qty,
        }
    )
    if enchantment:
        entry["optionalEnchantmentSpec"] = enchantment
    return entry


ALL_SEASONS = ["SPRING", "SUMMER", "AUTUMN", "WINTER"]

GOODS_VANILLA = {
    "_comment": "Built-in Vanilla Minecraft village trade goods (Lv 1-10) for TycoonCraft².",
    "replace": False,
    "goods": [
        # Lv 1-5 Rural textiles, animal products, monster drops & biome staples
        _good("tyc2:white_wool", "minecraft:white_wool", "TEXTILE", 3, 1, 5, True, False, 8, 24, biomes=["plains", "snowy"]),
        _good("tyc2:white_carpet", "minecraft:white_carpet", "TEXTILE", 3, 1, 4, True, False, 8, 20, biomes=["plains", "snowy"]),
        _good("tyc2:string", "minecraft:string", "TEXTILE", 2, 1, 4, True, False, 10, 28, biomes=["plains", "taiga"]),
        _good("tyc2:leather", "minecraft:leather", "TEXTILE", 5, 1, 5, True, False, 6, 18, biomes=["plains", "savanna", "taiga"]),
        _good("tyc2:feather", "minecraft:feather", "TEXTILE", 2, 1, 4, True, False, 12, 32, biomes=["plains"]),
        _good("tyc2:rabbit_hide", "minecraft:rabbit_hide", "TEXTILE", 4, 1, 4, True, False, 6, 16, biomes=["desert", "snowy"]),
        _good("tyc2:rotten_flesh", "minecraft:rotten_flesh", "MONSTER_DROP", 1, 1, 4, True, False, 16, 40),
        _good("tyc2:bone", "minecraft:bone", "MONSTER_DROP", 3, 1, 5, True, False, 8, 24),
        _good("tyc2:spider_eye", "minecraft:spider_eye", "MONSTER_DROP", 4, 1, 5, True, False, 6, 18),
        _good("tyc2:gunpowder", "minecraft:gunpowder", "MONSTER_DROP", 7, 1, 6, True, False, 6, 16),
        _good("tyc2:slime_ball", "minecraft:slime_ball", "MONSTER_DROP", 9, 2, 6, True, False, 4, 14, biomes=["swamp"], biome_exclusive=True),
        _good("tyc2:stick", "minecraft:stick", "FORESTRY", 1, 1, 3, True, False, 16, 48, biomes=["plains", "taiga", "savanna", "jungle", "swamp"]),
        _good("tyc2:oak_log", "minecraft:oak_log", "FORESTRY", 3, 1, 5, True, False, 8, 24, biomes=["plains", "swamp"], biome_exclusive=True),
        _good("tyc2:spruce_log", "minecraft:spruce_log", "FORESTRY", 3, 1, 5, True, False, 8, 24, biomes=["taiga", "snowy"], biome_exclusive=True),
        _good("tyc2:acacia_log", "minecraft:acacia_log", "FORESTRY", 3, 1, 5, True, False, 8, 24, biomes=["savanna"], biome_exclusive=True),
        _good("tyc2:jungle_log", "minecraft:jungle_log", "FORESTRY", 3, 1, 5, True, False, 8, 24, biomes=["jungle"], biome_exclusive=True),
        _good("tyc2:clay_ball", "minecraft:clay_ball", "BUILDING", 3, 1, 5, True, False, 12, 32, biomes=["swamp"]),
        _good("tyc2:lily_pad", "minecraft:lily_pad", "AGRICULTURE", 4, 1, 5, True, False, 6, 18, biomes=["swamp"], biome_exclusive=True),
        _good("tyc2:honey_bottle", "minecraft:honey_bottle", "AGRICULTURE", 6, 1, 5, True, False, 4, 12, biomes=["plains"]),
        _good("tyc2:beef", "minecraft:beef", "FOOD_BASIC", 4, 1, 5, True, False, 8, 20, biomes=["plains"]),
        _good("tyc2:mutton", "minecraft:mutton", "FOOD_BASIC", 4, 1, 5, True, False, 8, 20, biomes=["snowy", "plains"]),
        _good("tyc2:sand", "minecraft:sand", "BUILDING", 2, 1, 5, True, False, 16, 48, biomes=["desert"], biome_exclusive=True),
        _good("tyc2:sandstone", "minecraft:sandstone", "BUILDING", 4, 1, 5, True, False, 12, 32, biomes=["desert"], biome_exclusive=True),
        _good("tyc2:cactus", "minecraft:cactus", "AGRICULTURE", 3, 1, 5, True, False, 8, 24, biomes=["desert"], biome_exclusive=True),
        _good("tyc2:snow_block", "minecraft:snow_block", "BUILDING", 2, 1, 5, True, False, 16, 48, biomes=["snowy"], biome_exclusive=True),
        _good("tyc2:packed_ice", "minecraft:packed_ice", "BUILDING", 8, 2, 6, True, False, 8, 24, biomes=["snowy"], biome_exclusive=True),
        _good("tyc2:saddle", "minecraft:saddle", "TEXTILE", 28, 2, 6, True, False, 1, 3, biomes=["plains", "savanna"]),
        # Seasonal Vanilla Crops
        _good("tyc2:wheat", "minecraft:wheat", "AGRICULTURE", 3, 1, 6, True, False, 12, 32, seasons=["SPRING", "SUMMER", "AUTUMN"], biomes=["plains"], optional_tag="#crops/wheat"),
        _good("tyc2:carrot", "minecraft:carrot", "AGRICULTURE", 3, 1, 6, True, False, 12, 32, seasons=["SPRING", "SUMMER", "AUTUMN"], biomes=["plains"], optional_tag="#crops/carrot"),
        _good("tyc2:potato", "minecraft:potato", "AGRICULTURE", 3, 1, 6, True, False, 12, 32, seasons=["SPRING", "SUMMER", "AUTUMN"], biomes=["taiga", "plains"], optional_tag="#crops/potato"),
        _good("tyc2:beetroot", "minecraft:beetroot", "AGRICULTURE", 3, 1, 5, True, False, 10, 28, seasons=["SPRING", "AUTUMN", "WINTER"], biomes=["snowy"], optional_tag="#crops/beetroot"),
        _good("tyc2:pumpkin", "minecraft:pumpkin", "AGRICULTURE", 5, 1, 6, True, False, 6, 18, seasons=["AUTUMN"], biomes=["plains", "taiga"], optional_tag="#crops/pumpkin"),
        _good("tyc2:melon_slice", "minecraft:melon_slice", "AGRICULTURE", 2, 1, 6, True, False, 12, 36, seasons=["SUMMER"], biomes=["savanna", "jungle"], optional_tag="#crops/melon"),
        _good("tyc2:sweet_berries", "minecraft:sweet_berries", "AGRICULTURE", 3, 1, 5, True, False, 10, 28, seasons=["SPRING", "SUMMER", "AUTUMN"], biomes=["taiga", "snowy"], biome_exclusive=True),
        _good("tyc2:apple", "minecraft:apple", "AGRICULTURE", 5, 1, 6, True, False, 6, 18, seasons=["AUTUMN"], biomes=["plains"]),
        _good("tyc2:sugar_cane", "minecraft:sugar_cane", "AGRICULTURE", 3, 2, 6, True, False, 12, 32, seasons=["SUMMER"], biomes=["jungle", "swamp"]),
        _good("tyc2:cocoa_beans", "minecraft:cocoa_beans", "AGRICULTURE", 5, 2, 6, True, False, 8, 24, seasons=["SUMMER"], biomes=["jungle"], biome_exclusive=True),
        _good("tyc2:bamboo", "minecraft:bamboo", "AGRICULTURE", 2, 2, 6, True, False, 16, 48, seasons=["SUMMER"], biomes=["jungle"], biome_exclusive=True),
        _good("tyc2:brown_mushroom", "minecraft:brown_mushroom", "AGRICULTURE", 4, 1, 6, True, False, 8, 20, seasons=ALL_SEASONS, biomes=["taiga", "swamp"]),
        _good("tyc2:red_mushroom", "minecraft:red_mushroom", "AGRICULTURE", 4, 1, 6, True, False, 8, 20, seasons=ALL_SEASONS, biomes=["taiga", "swamp"]),
        _good("tyc2:kelp", "minecraft:kelp", "AGRICULTURE", 2, 1, 5, True, False, 16, 40, seasons=ALL_SEASONS),
        _good("tyc2:glow_berries", "minecraft:glow_berries", "AGRICULTURE", 5, 2, 6, True, False, 8, 20, seasons=ALL_SEASONS),
        # Lv 7-10 Metropolis staple crop demand
        _good("tyc2:metro_wheat_demand", "minecraft:wheat", "AGRICULTURE", 4, 7, 10, False, True, 16, 40, seasons=["SPRING", "SUMMER", "AUTUMN"], demand_biomes=["desert", "snowy"], optional_tag="#crops/wheat"),
        _good("tyc2:metro_carrot_demand", "minecraft:carrot", "AGRICULTURE", 4, 7, 10, False, True, 16, 40, seasons=["SPRING", "SUMMER", "AUTUMN"], demand_biomes=["desert", "snowy"], optional_tag="#crops/carrot"),
        _good("tyc2:metro_potato_demand", "minecraft:potato", "AGRICULTURE", 4, 7, 10, False, True, 16, 40, seasons=["SPRING", "SUMMER", "AUTUMN"], demand_biomes=["desert", "snowy"], optional_tag="#crops/potato"),
        # Lv 1-6 Rural & developing mining, metal, fuel, timber (desert), and basic supplies buys
        _good("tyc2:coal", "minecraft:coal", "MINING", 4, 1, 6, False, True, 12, 32, demand_biomes=["snowy", "plains", "jungle", "swamp"], demand_seasons=["WINTER"]),
        _good("tyc2:charcoal", "minecraft:charcoal", "MINING", 4, 1, 4, False, True, 12, 32, demand_biomes=["desert", "snowy"], demand_seasons=["WINTER"]),
        _good("tyc2:copper_ingot", "minecraft:copper_ingot", "METAL", 5, 1, 6, False, True, 8, 24, demand_biomes=["plains", "taiga", "savanna"], optional_tag="#ingots/copper"),
        _good("tyc2:iron_ingot", "minecraft:iron_ingot", "METAL", 9, 1, 6, False, True, 6, 20, demand_biomes=["plains", "savanna", "jungle", "swamp", "taiga"], optional_tag="#ingots/iron"),
        _good("tyc2:cobblestone", "minecraft:cobblestone", "MINING", 1, 1, 4, False, True, 32, 64, demand_biomes=["desert", "swamp"], optional_tag="#cobblestones"),
        _good("tyc2:stone", "minecraft:stone", "MINING", 2, 1, 6, False, True, 24, 64, demand_biomes=["desert", "plains", "savanna", "jungle", "swamp"], optional_tag="#stones"),
        _good("tyc2:flint", "minecraft:flint", "MINING", 3, 1, 4, False, True, 10, 24, demand_biomes=["taiga", "snowy"]),
        _good("tyc2:torch", "minecraft:torch", "BUILDING", 2, 1, 4, False, True, 16, 48, demand_biomes=["snowy", "savanna", "jungle", "swamp"], demand_seasons=["WINTER"]),
        _good("tyc2:oak_log_demand", "minecraft:oak_log", "FORESTRY", 4, 1, 5, False, True, 12, 32, biome_exclusive=True, demand_biomes=["desert"], demand_seasons=["WINTER"], optional_tag="#minecraft:logs"),
        # Lv 4-7 Developing town sells & buys
        _good("tyc2:paper", "minecraft:paper", "TEXTILE", 4, 4, 7, True, False, 12, 32),
        _good("tyc2:book", "minecraft:book", "TEXTILE", 12, 4, 7, True, False, 4, 12),
        _good("tyc2:glass", "minecraft:glass", "BUILDING", 4, 4, 7, True, False, 12, 32, biomes=["desert"]),
        _good("tyc2:brick", "minecraft:brick", "BUILDING", 5, 4, 7, True, False, 12, 32),
        _good("tyc2:terracotta", "minecraft:terracotta", "BUILDING", 5, 4, 7, True, False, 12, 32, biomes=["desert", "savanna"]),
        _good("tyc2:red_dye", "minecraft:red_dye", "TEXTILE", 4, 4, 6, True, False, 8, 24, biomes=["savanna"]),
        _good("tyc2:book_unbreaking_1", "minecraft:enchanted_book", "ENCHANTED_BOOK", 90, 4, 6, True, False, 1, 2, tier="TIER_1", biomes=["snowy"], enchantment="minecraft:unbreaking:1"),
        _good("tyc2:book_efficiency_2", "minecraft:enchanted_book", "ENCHANTED_BOOK", 110, 4, 6, True, False, 1, 2, tier="TIER_1", biomes=["snowy"], enchantment="minecraft:efficiency:2"),
        _good("tyc2:book_protection_2", "minecraft:enchanted_book", "ENCHANTED_BOOK", 110, 4, 6, True, False, 1, 2, tier="TIER_1", biomes=["snowy"], enchantment="minecraft:protection:2"),
        _good("tyc2:gold_ingot", "minecraft:gold_ingot", "METAL", 22, 4, 8, False, True, 4, 14, tier="TIER_1", demand_biomes=["desert", "savanna"], optional_tag="#ingots/gold"),
        _good("tyc2:redstone", "minecraft:redstone", "INDUSTRIAL", 6, 4, 8, False, True, 12, 32, tier="TIER_1", demand_biomes=["taiga", "plains"], optional_tag="#dusts/redstone"),
        _good("tyc2:lapis_lazuli", "minecraft:lapis_lazuli", "MINING", 7, 4, 8, False, True, 8, 24, tier="TIER_1", demand_biomes=["desert", "snowy"], optional_tag="#gems/lapis"),
        _good("tyc2:quartz", "minecraft:quartz", "MINING", 10, 5, 8, False, True, 8, 24, tier="TIER_2", demand_biomes=["plains", "desert"], optional_tag="#gems/quartz"),
        _good("tyc2:piston", "minecraft:piston", "INDUSTRIAL", 24, 4, 7, False, True, 2, 8, tier="TIER_1", demand_biomes=["taiga", "jungle"]),
        _good("tyc2:repeater", "minecraft:repeater", "INDUSTRIAL", 20, 4, 7, False, True, 3, 10, tier="TIER_1", demand_biomes=["taiga", "plains"]),
        _good("tyc2:bread", "minecraft:bread", "FOOD_BASIC", 7, 2, 6, False, True, 8, 24, demand_biomes=["desert", "snowy", "taiga", "savanna", "swamp"], demand_seasons=["WINTER"], optional_tag="#foods/bread"),
        _good("tyc2:baked_potato", "minecraft:baked_potato", "FOOD_BASIC", 7, 3, 6, False, True, 8, 24, demand_biomes=["desert", "snowy", "taiga"], demand_seasons=["WINTER"]),
        _good("tyc2:cooked_porkchop", "minecraft:cooked_porkchop", "FOOD_BASIC", 11, 4, 7, False, True, 6, 18, demand_biomes=["desert", "snowy", "taiga", "savanna", "swamp"], demand_seasons=["WINTER"], optional_tag="#foods/cooked_pork"),
        # Lv 7-10 Metropolis fine cuisine, industrial buys, rare books & endgame sells
        _good("tyc2:golden_carrot", "minecraft:golden_carrot", "FOOD_FINE", 26, 7, 10, False, True, 6, 18, tier="TIER_1", demand_biomes=["desert", "snowy"]),
        _good("tyc2:cooked_beef", "minecraft:cooked_beef", "FOOD_FINE", 14, 7, 10, False, True, 8, 24, tier="TIER_0", demand_biomes=["desert", "snowy", "taiga", "savanna", "swamp"], demand_seasons=["WINTER"], optional_tag="#foods/cooked_beef"),
        _good("tyc2:pumpkin_pie", "minecraft:pumpkin_pie", "FOOD_FINE", 18, 7, 10, False, True, 6, 16, tier="TIER_1", demand_biomes=["plains", "taiga", "snowy"], demand_seasons=["AUTUMN", "WINTER"]),
        _good("tyc2:cake", "minecraft:cake", "FOOD_FINE", 32, 7, 10, False, True, 2, 6, tier="TIER_1", demand_biomes=["plains", "desert"]),
        _good("tyc2:rabbit_stew", "minecraft:rabbit_stew", "FOOD_FINE", 28, 7, 10, False, True, 3, 10, tier="TIER_1", demand_biomes=["snowy", "taiga", "swamp"], demand_seasons=["WINTER"]),
        _good("tyc2:glowstone_dust", "minecraft:glowstone_dust", "INDUSTRIAL", 14, 7, 10, False, True, 8, 24, tier="TIER_2", demand_biomes=["snowy", "swamp", "jungle"], demand_seasons=["WINTER"], optional_tag="#dusts/glowstone"),
        _good("tyc2:blaze_powder", "minecraft:blaze_powder", "INDUSTRIAL", 24, 7, 10, False, True, 4, 14, tier="TIER_2", demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"]),
        _good("tyc2:obsidian", "minecraft:obsidian", "INDUSTRIAL", 18, 7, 10, False, True, 6, 18, tier="TIER_2", demand_biomes=["desert", "savanna"], optional_tag="#obsidians"),
        _good("tyc2:diamond", "minecraft:diamond", "METAL", 140, 7, 10, False, True, 1, 4, tier="TIER_2", demand_biomes=["plains", "desert", "snowy"], optional_tag="#gems/diamond"),
        _good("tyc2:book_mending", "minecraft:enchanted_book", "ENCHANTED_BOOK", 480, 7, 10, True, False, 1, 2, tier="TIER_3", biomes=["snowy"], enchantment="minecraft:mending:1"),
        _good("tyc2:book_efficiency_5", "minecraft:enchanted_book", "ENCHANTED_BOOK", 420, 7, 10, True, False, 1, 2, tier="TIER_3", biomes=["snowy"], enchantment="minecraft:efficiency:5"),
        _good("tyc2:book_fortune_3", "minecraft:enchanted_book", "ENCHANTED_BOOK", 450, 7, 10, True, False, 1, 2, tier="TIER_3", biomes=["snowy"], enchantment="minecraft:fortune:3"),
        _good("tyc2:book_unbreaking_3", "minecraft:enchanted_book", "ENCHANTED_BOOK", 360, 7, 10, True, False, 1, 2, tier="TIER_2", biomes=["snowy"], enchantment="minecraft:unbreaking:3"),
        _good("tyc2:book_sharpness_5", "minecraft:enchanted_book", "ENCHANTED_BOOK", 430, 7, 10, True, False, 1, 2, tier="TIER_3", enchantment="minecraft:sharpness:5"),
        _good("tyc2:book_protection_4", "minecraft:enchanted_book", "ENCHANTED_BOOK", 410, 7, 10, True, False, 1, 2, tier="TIER_3", enchantment="minecraft:protection:4"),
        _good("tyc2:name_tag", "minecraft:name_tag", "RARE_ALLOY", 85, 7, 10, True, False, 1, 4, tier="TIER_1"),
        _good("tyc2:amethyst_shard", "minecraft:amethyst_shard", "RARE_ALLOY", 24, 7, 10, True, False, 6, 18, tier="TIER_1"),
        _good("tyc2:prismarine_crystals", "minecraft:prismarine_crystals", "RARE_ALLOY", 30, 7, 10, True, False, 6, 16, tier="TIER_2"),
        _good("tyc2:echo_shard", "minecraft:echo_shard", "RARE_ALLOY", 320, 8, 10, True, False, 1, 3, tier="TIER_3"),
        _good("tyc2:heart_of_the_sea", "minecraft:heart_of_the_sea", "RARE_ALLOY", 650, 8, 10, True, False, 1, 2, tier="TIER_3"),
        _good("tyc2:totem_of_undying", "minecraft:totem_of_undying", "ENDGAME", 850, 9, 10, True, False, 1, 1, tier="TIER_4"),
        _good("tyc2:netherite_scrap", "minecraft:netherite_scrap", "RARE_ALLOY", 520, 8, 10, True, False, 1, 2, tier="TIER_4"),
        _good("tyc2:ancient_debris", "minecraft:ancient_debris", "ENDGAME", 950, 10, 10, True, False, 1, 2, tier="TIER_5"),
        _good("tyc2:netherite_upgrade_template", "minecraft:netherite_upgrade_smithing_template", "ENDGAME", 1100, 10, 10, True, False, 1, 1, tier="TIER_5"),
    ],
}

GOODS_IC2 = {
    "_comment": "Built-in IndustrialCraft² (ic2) trade goods — auto-filtered when ic2 is absent.",
    "replace": False,
    "goods": [
        _good("tyc2:ic2_sticky_resin", "ic2:resin", "FORESTRY", 6, 1, 5, True, False, 6, 20, seasons=["SPRING", "SUMMER", "AUTUMN"], biomes=["plains", "taiga"], required_mod="ic2"),
        _good("tyc2:ic2_rubber", "ic2:rubber", "INDUSTRIAL", 10, 3, 6, True, False, 6, 18, tier="TIER_1", required_mod="ic2"),
        _good("tyc2:ic2_hops", "ic2:hops", "AGRICULTURE", 12, 3, 7, True, False, 6, 16, tier="TIER_1", seasons=["SUMMER", "AUTUMN"], required_mod="ic2"),
        _good("tyc2:ic2_coffee_beans", "ic2:coffee_beans", "AGRICULTURE", 18, 4, 8, True, False, 4, 14, tier="TIER_1", seasons=["SUMMER"], required_mod="ic2"),
        _good("tyc2:ic2_terra_wart", "ic2:terra_wart", "AGRICULTURE", 45, 6, 9, True, False, 3, 10, tier="TIER_2", seasons=ALL_SEASONS, required_mod="ic2"),
        _good("tyc2:ic2_tin_ingot", "ic2:tin_ingot", "METAL", 6, 1, 5, False, True, 8, 24, optional_tag="#ingots/tin", required_mod="ic2"),
        _good("tyc2:ic2_lead_ingot", "ic2:lead_ingot", "METAL", 7, 1, 5, False, True, 8, 24, optional_tag="#ingots/lead", required_mod="ic2"),
        _good("tyc2:ic2_bronze_ingot", "ic2:bronze_ingot", "METAL", 14, 4, 9, False, True, 6, 18, tier="TIER_1", optional_tag="#ingots/bronze", required_mod="ic2"),
        _good("tyc2:ic2_silver_ingot", "ic2:silver_ingot", "METAL", 20, 4, 8, False, True, 4, 14, tier="TIER_1", optional_tag="#ingots/silver", required_mod="ic2"),
        _good("tyc2:ic2_steel_ingot", "ic2:steel_ingot", "RARE_ALLOY", 26, 4, 10, False, True, 4, 14, tier="TIER_2", optional_tag="#ingots/steel", required_mod="ic2"),
        _good("tyc2:ic2_circuit", "ic2:circuit", "INDUSTRIAL", 34, 4, 7, False, True, 2, 8, tier="TIER_1", optional_tag="#circuits/basic", required_mod="ic2"),
        _good("tyc2:ic2_advanced_circuit", "ic2:advanced_circuit", "INDUSTRIAL", 85, 6, 9, False, True, 1, 5, tier="TIER_2", optional_tag="#circuits/advanced", required_mod="ic2"),
        _good("tyc2:ic2_copper_cable", "ic2:copper_cable", "INDUSTRIAL", 5, 4, 6, False, True, 12, 32, tier="TIER_1", optional_tag="#cables/copper", required_mod="ic2"),
        _good("tyc2:ic2_re_battery", "ic2:re_battery", "INDUSTRIAL", 28, 4, 7, False, True, 2, 6, tier="TIER_1", required_mod="ic2"),
        _good("tyc2:ic2_energy_crystal", "ic2:energy_crystal", "INDUSTRIAL", 180, 7, 10, False, True, 1, 3, tier="TIER_3", required_mod="ic2"),
        _good("tyc2:ic2_alloy", "ic2:alloy", "RARE_ALLOY", 120, 7, 10, True, False, 2, 6, tier="TIER_3", required_mod="ic2"),
        _good("tyc2:ic2_carbon_plate", "ic2:carbon_plate", "RARE_ALLOY", 95, 7, 10, True, False, 2, 6, tier="TIER_3", required_mod="ic2"),
        _good("tyc2:ic2_iridium_shard", "ic2:iridium_shard", "ENDGAME", 900, 10, 10, True, False, 1, 3, tier="TIER_5", required_mod="ic2"),
    ],
}

GOODS_ECC2 = {
    "_comment": "Built-in EcoCraft 2 (ecc2) clean-energy, gas & photonic trade goods — auto-filtered when ecc2 is absent.",
    "replace": False,
    "goods": [
        _good("tyc2:ecc2_culture_medium_cell", "ecc2:culture_medium_cell", "INDUSTRIAL", 18, 3, 7, False, True, 4, 12, tier="TIER_1", required_mod="ecc2"),
        _good("tyc2:ecc2_water_gas_cell", "ecc2:water_gas_cell", "INDUSTRIAL", 24, 4, 8, False, True, 4, 14, tier="TIER_1", required_mod="ecc2"),
        _good("tyc2:ecc2_methane_ice_cluster", "ecc2:methane_ice_cluster", "MINING", 28, 4, 8, True, False, 4, 12, tier="TIER_1", biomes=["snowy"], required_mod="ecc2"),
        _good("tyc2:ecc2_natural_gas_cell", "ecc2:natural_gas_cell", "INDUSTRIAL", 64, 5, 9, False, True, 3, 10, tier="TIER_2", required_mod="ecc2"),
        _good("tyc2:ecc2_sunnarium", "ecc2:sunnarium", "RARE_ALLOY", 320, 8, 10, False, True, 1, 3, tier="TIER_3", required_mod="ecc2"),
        _good("tyc2:ecc2_sunnarium_alloy", "ecc2:sunnarium_alloy", "RARE_ALLOY", 560, 9, 10, False, True, 1, 2, tier="TIER_4", required_mod="ecc2"),
        _good("tyc2:ecc2_iridium_ingot", "ecc2:iridium_ingot", "ENDGAME", 1150, 10, 10, True, True, 1, 2, tier="TIER_5", optional_tag="#ingots/iridium", required_mod="ecc2"),
    ],
}

GOODS_FARMERS_DELIGHT = {
    "_comment": "Built-in Farmer's Delight crops & Lv 7-10 fine meals — auto-filtered when farmersdelight is absent.",
    "replace": False,
    "goods": [
        _good("tyc2:fd_onion", "farmersdelight:onion", "AGRICULTURE", 4, 1, 6, True, False, 10, 28, seasons=["SPRING", "AUTUMN"], biomes=["plains"], optional_tag="#crops/onion", required_mod="farmersdelight"),
        _good("tyc2:fd_cabbage", "farmersdelight:cabbage", "AGRICULTURE", 5, 1, 6, True, False, 8, 24, seasons=["SPRING", "AUTUMN", "WINTER"], biomes=["plains", "snowy"], optional_tag="#crops/cabbage", required_mod="farmersdelight"),
        _good("tyc2:fd_cabbage_leaf", "farmersdelight:cabbage_leaf", "AGRICULTURE", 3, 1, 6, True, False, 12, 32, seasons=["SPRING", "AUTUMN", "WINTER"], biomes=["plains", "snowy"], required_mod="farmersdelight"),
        _good("tyc2:fd_tomato", "farmersdelight:tomato", "AGRICULTURE", 4, 1, 6, True, False, 10, 28, seasons=["SUMMER", "AUTUMN"], biomes=["savanna", "plains"], optional_tag="#crops/tomato", required_mod="farmersdelight"),
        _good("tyc2:fd_rice", "farmersdelight:rice", "AGRICULTURE", 4, 2, 6, True, False, 10, 28, seasons=["SUMMER", "AUTUMN"], optional_tag="#crops/rice", required_mod="farmersdelight"),
        _good("tyc2:fd_rice_panicle", "farmersdelight:rice_panicle", "AGRICULTURE", 4, 2, 6, True, False, 10, 28, seasons=["SUMMER", "AUTUMN"], required_mod="farmersdelight"),
        _good("tyc2:fd_beef_stew", "farmersdelight:beef_stew", "FOOD_FINE", 34, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
        _good("tyc2:fd_chicken_soup", "farmersdelight:chicken_soup", "FOOD_FINE", 32, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
        _good("tyc2:fd_vegetable_soup", "farmersdelight:vegetable_soup", "FOOD_FINE", 28, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
        _good("tyc2:fd_fried_rice", "farmersdelight:fried_rice", "FOOD_FINE", 30, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="farmersdelight"),
        _good("tyc2:fd_pasta_with_meatballs", "farmersdelight:pasta_with_meatballs", "FOOD_FINE", 38, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="farmersdelight"),
        _good("tyc2:fd_roast_chicken", "farmersdelight:roast_chicken", "FOOD_FINE", 44, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
        _good("tyc2:fd_honey_glazed_ham", "farmersdelight:honey_glazed_ham", "FOOD_FINE", 46, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
        _good("tyc2:fd_shepherds_pie", "farmersdelight:shepherds_pie", "FOOD_FINE", 42, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
        _good("tyc2:fd_stuffed_pumpkin", "farmersdelight:stuffed_pumpkin", "FOOD_FINE", 45, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="farmersdelight"),
    ],
}

GOODS_KALEIDOSCOPE_COOKERY = {
    "_comment": "Built-in Kaleidoscope Cookery crops & Lv 7-10 gourmet meals — auto-filtered when kaleidoscope_cookery is absent.",
    "replace": False,
    "goods": [
        _good("tyc2:kc_lettuce", "kaleidoscope_cookery:lettuce", "AGRICULTURE", 4, 1, 6, True, False, 10, 28, seasons=["SPRING", "AUTUMN"], biomes=["plains"], optional_tag="#crops/lettuce", required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_tomato", "kaleidoscope_cookery:tomato", "AGRICULTURE", 4, 1, 6, True, False, 10, 28, seasons=["SUMMER", "AUTUMN"], biomes=["savanna"], optional_tag="#crops/tomato", required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_chili", "kaleidoscope_cookery:chili", "AGRICULTURE", 5, 2, 6, True, False, 8, 24, seasons=["SUMMER", "AUTUMN"], biomes=["savanna"], optional_tag="#crops/chili", required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_red_chili", "kaleidoscope_cookery:red_chili", "AGRICULTURE", 5, 2, 6, True, False, 8, 24, seasons=["SUMMER", "AUTUMN"], biomes=["savanna"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_green_chili", "kaleidoscope_cookery:green_chili", "AGRICULTURE", 5, 2, 6, True, False, 8, 24, seasons=["SUMMER", "AUTUMN"], biomes=["savanna"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_rice", "kaleidoscope_cookery:rice", "AGRICULTURE", 4, 2, 6, True, False, 10, 28, seasons=["SUMMER", "AUTUMN"], optional_tag="#crops/rice", required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_wild_rice", "kaleidoscope_cookery:wild_rice", "AGRICULTURE", 5, 2, 6, True, False, 8, 24, seasons=["SUMMER", "AUTUMN"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_tea_leaf", "kaleidoscope_cookery:tea_leaf", "AGRICULTURE", 7, 3, 7, True, False, 8, 20, seasons=["SPRING", "SUMMER"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_cooked_rice", "kaleidoscope_cookery:cooked_rice", "FOOD_FINE", 24, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_egg_fried_rice", "kaleidoscope_cookery:egg_fried_rice", "FOOD_FINE", 32, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_braised_beef", "kaleidoscope_cookery:braised_beef", "FOOD_FINE", 44, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_sweet_and_sour_ender_pearls", "kaleidoscope_cookery:sweet_and_sour_ender_pearls", "FOOD_FINE", 58, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_dongpo_pork", "kaleidoscope_cookery:dongpo_pork", "FOOD_FINE", 48, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_fondant_pie", "kaleidoscope_cookery:fondant_pie", "FOOD_FINE", 42, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_spicy_chicken", "kaleidoscope_cookery:spicy_chicken", "FOOD_FINE", 38, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, demand_biomes=["snowy", "taiga"], demand_seasons=["WINTER"], required_mod="kaleidoscope_cookery"),
        _good("tyc2:kc_tomato_scrambled_eggs", "kaleidoscope_cookery:tomato_scrambled_eggs", "FOOD_FINE", 30, 7, 10, False, True, 3, 10, tier="TIER_1", seasons=ALL_SEASONS, required_mod="kaleidoscope_cookery"),
    ],
}

HARVEST_SEASONS = {
    "_comment": "Canonical crop harvest seasons for Serene Seasons / Ecliptic Seasons arbitrage (DESIGN.md Section 8.3).",
    "seasons": {
        # Vanilla
        "minecraft:wheat": ["SPRING", "SUMMER", "AUTUMN"],
        "minecraft:carrot": ["SPRING", "SUMMER", "AUTUMN"],
        "minecraft:potato": ["SPRING", "SUMMER", "AUTUMN"],
        "minecraft:beetroot": ["SPRING", "AUTUMN", "WINTER"],
        "minecraft:pumpkin": ["AUTUMN"],
        "minecraft:melon_slice": ["SUMMER"],
        "minecraft:sweet_berries": ["SPRING", "SUMMER", "AUTUMN"],
        "minecraft:apple": ["AUTUMN"],
        "minecraft:sugar_cane": ["SUMMER"],
        "minecraft:cocoa_beans": ["SUMMER"],
        "minecraft:bamboo": ["SUMMER"],
        "minecraft:brown_mushroom": ALL_SEASONS,
        "minecraft:red_mushroom": ALL_SEASONS,
        "minecraft:kelp": ALL_SEASONS,
        "minecraft:glow_berries": ALL_SEASONS,
        # IndustrialCraft²
        "ic2:resin": ["SPRING", "SUMMER", "AUTUMN"],
        "ic2:hops": ["SUMMER", "AUTUMN"],
        "ic2:coffee_beans": ["SUMMER"],
        "ic2:terra_wart": ALL_SEASONS,
        # Farmer's Delight
        "farmersdelight:onion": ["SPRING", "AUTUMN"],
        "farmersdelight:cabbage": ["SPRING", "AUTUMN", "WINTER"],
        "farmersdelight:cabbage_leaf": ["SPRING", "AUTUMN", "WINTER"],
        "farmersdelight:tomato": ["SUMMER", "AUTUMN"],
        "farmersdelight:rice": ["SUMMER", "AUTUMN"],
        "farmersdelight:rice_panicle": ["SUMMER", "AUTUMN"],
        # Kaleidoscope Cookery
        "kaleidoscope_cookery:lettuce": ["SPRING", "AUTUMN"],
        "kaleidoscope_cookery:tomato": ["SUMMER", "AUTUMN"],
        "kaleidoscope_cookery:chili": ["SUMMER", "AUTUMN"],
        "kaleidoscope_cookery:red_chili": ["SUMMER", "AUTUMN"],
        "kaleidoscope_cookery:green_chili": ["SUMMER", "AUTUMN"],
        "kaleidoscope_cookery:rice": ["SUMMER", "AUTUMN"],
        "kaleidoscope_cookery:wild_rice": ["SUMMER", "AUTUMN"],
        "kaleidoscope_cookery:tea_leaf": ["SPRING", "SUMMER"],
    },
}

VILLAGE_LEVELS = {
    "_comment": "Canonical Lv 1-10 Village Progression Table (DESIGN.md Section 6.1 & VillageLevelTable.DEFAULT).",
    "levels": [
        {"level": 1, "minPopulation": 2, "minTradeVolume": 0, "minVillageBalance": 0, "upgradeCostSink": 0, "sellOrderCount": 3, "buyOrderCount": 2, "qtyScale": 1.0, "maxPopulationCap": 8, "expansionRadiusMax": 24},
        {"level": 2, "minPopulation": 4, "minTradeVolume": 400, "minVillageBalance": 150, "upgradeCostSink": 100, "sellOrderCount": 4, "buyOrderCount": 3, "qtyScale": 1.3, "maxPopulationCap": 12, "expansionRadiusMax": 30},
        {"level": 3, "minPopulation": 6, "minTradeVolume": 1200, "minVillageBalance": 400, "upgradeCostSink": 250, "sellOrderCount": 4, "buyOrderCount": 4, "qtyScale": 1.6, "maxPopulationCap": 16, "expansionRadiusMax": 36},
        {"level": 4, "minPopulation": 9, "minTradeVolume": 3000, "minVillageBalance": 900, "upgradeCostSink": 550, "sellOrderCount": 5, "buyOrderCount": 4, "qtyScale": 2.0, "maxPopulationCap": 20, "expansionRadiusMax": 42},
        {"level": 5, "minPopulation": 12, "minTradeVolume": 7000, "minVillageBalance": 1800, "upgradeCostSink": 1100, "sellOrderCount": 6, "buyOrderCount": 5, "qtyScale": 2.5, "maxPopulationCap": 24, "expansionRadiusMax": 48},
        {"level": 6, "minPopulation": 16, "minTradeVolume": 15000, "minVillageBalance": 3500, "upgradeCostSink": 2000, "sellOrderCount": 6, "buyOrderCount": 6, "qtyScale": 3.2, "maxPopulationCap": 28, "expansionRadiusMax": 54},
        {"level": 7, "minPopulation": 20, "minTradeVolume": 32000, "minVillageBalance": 7000, "upgradeCostSink": 4000, "sellOrderCount": 7, "buyOrderCount": 6, "qtyScale": 4.0, "maxPopulationCap": 32, "expansionRadiusMax": 60},
        {"level": 8, "minPopulation": 25, "minTradeVolume": 65000, "minVillageBalance": 14000, "upgradeCostSink": 7500, "sellOrderCount": 8, "buyOrderCount": 7, "qtyScale": 5.0, "maxPopulationCap": 36, "expansionRadiusMax": 64},
        {"level": 9, "minPopulation": 30, "minTradeVolume": 130000, "minVillageBalance": 28000, "upgradeCostSink": 15000, "sellOrderCount": 9, "buyOrderCount": 8, "qtyScale": 6.5, "maxPopulationCap": 40, "expansionRadiusMax": 68},
        {"level": 10, "minPopulation": 36, "minTradeVolume": 250000, "minVillageBalance": 55000, "upgradeCostSink": 30000, "sellOrderCount": 10, "buyOrderCount": 9, "qtyScale": 8.0, "maxPopulationCap": 40, "expansionRadiusMax": 72},
    ],
}


def _workplace(
    profession_id: str,
    workstation_block_id: str,
    archetype: str,
    base_cost_small_ic: int,
    trade_categories: list[str],
    large_cost_multiplier: float = 2.2,
    min_level_small: int = 1,
    min_level_large: int = 3,
    enabled: bool = True,
) -> dict:
    return {
        "professionId": profession_id,
        "workstationBlockId": workstation_block_id,
        "archetype": archetype,
        "baseCostSmallIc": base_cost_small_ic,
        "largeCostMultiplier": large_cost_multiplier,
        "minVillageLevelSmall": min_level_small,
        "minVillageLevelLarge": min_level_large,
        "tradeCategories": trade_categories,
        "enabled": enabled,
    }


WORKPLACES_VANILLA = {
    "_comment": "Built-in Vanilla Minecraft villager workplace specs (13 professions) for TycoonCraft² Aid Construction.",
    "replace": False,
    "workplaces": [
        _workplace("minecraft:farmer", "minecraft:composter", "FARMER", 250, ["AGRICULTURE", "FOOD_BASIC"]),
        _workplace("minecraft:fisherman", "minecraft:barrel", "FISHERMAN", 260, ["FOOD_BASIC", "AGRICULTURE"]),
        _workplace("minecraft:shepherd", "minecraft:loom", "SHEPHERD", 280, ["TEXTILE"]),
        _workplace("minecraft:fletcher", "minecraft:fletching_table", "FLETCHER", 280, ["FORESTRY", "MONSTER_DROP"]),
        _workplace("minecraft:butcher", "minecraft:smoker", "BUTCHER", 320, ["FOOD_BASIC", "FOOD_FINE"]),
        _workplace("minecraft:leatherworker", "minecraft:cauldron", "LEATHERWORKER", 320, ["TEXTILE"]),
        _workplace("minecraft:mason", "minecraft:stonecutter", "MASON", 360, ["BUILDING", "MINING"]),
        _workplace("minecraft:cartographer", "minecraft:cartography_table", "CARTOGRAPHER", 400, ["BUILDING", "FORESTRY"]),
        _workplace("minecraft:toolsmith", "minecraft:smithing_table", "TOOLSMITH", 450, ["METAL", "MINING", "INDUSTRIAL"]),
        _workplace("minecraft:weaponsmith", "minecraft:grindstone", "WEAPONSMITH", 480, ["METAL", "RARE_ALLOY", "MONSTER_DROP"]),
        _workplace("minecraft:armorer", "minecraft:blast_furnace", "ARMORER", 520, ["METAL", "RARE_ALLOY", "INDUSTRIAL"]),
        _workplace("minecraft:cleric", "minecraft:brewing_stand", "CLERIC", 550, ["MONSTER_DROP", "ENDGAME"]),
        _workplace("minecraft:librarian", "minecraft:lectern", "LIBRARIAN", 600, ["ENCHANTED_BOOK"]),
    ],
}


# =============================================================================
# Atomic File Locking & Publishing
# =============================================================================

@contextmanager
def lock_file(path: Path):
    with path.open("a+b") as handle:
        if os.name == "nt":
            import msvcrt
            if not path.stat().st_size:
                handle.write(b"0")
                handle.flush()
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_LOCK, 1)
            try:
                yield
            finally:
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.flock(handle, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(handle, fcntl.LOCK_UN)


def publish(staged: Path, destination: Path):
    """Atomically commit generated resource files and prune stale files from prior runs."""
    names = {
        path.relative_to(staged).as_posix()
        for path in staged.rglob("*")
        if path.is_file() and path.suffix in (".json", ".mcmeta")
    }
    marker = destination / MARKER_NAME
    with lock_file(destination.parent / ("." + destination.name + ".lock")):
        if destination.exists() and not destination.is_dir():
            raise SystemExit("Resource output must be a directory")
        destination.mkdir(parents=True, exist_ok=True)
        if marker.is_file():
            previous = set(marker.read_text(encoding="utf-8").splitlines()[1:])
        elif any(destination.iterdir()):
            if not (destination.name == "tyc2-resources" and destination.parent.name == "generated"):
                raise SystemExit(f"Choose an empty directory for the generated pack: {destination}")
            previous = set()
        else:
            previous = set()
        for name in sorted(names):
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            os.replace(staged / name, target)
        for name in previous - names:
            rel = Path(name)
            if not rel.is_absolute() and ".." not in rel.parts and rel.suffix in (".json", ".mcmeta"):
                (destination / rel).unlink(missing_ok=True)
        ready = staged / MARKER_NAME
        ready.write_text("TyC2 generated resources\n" + "\n".join(sorted(names)) + "\n", encoding="utf-8")
        os.replace(ready, marker)


# =============================================================================
# Target-Aware Generation
# =============================================================================

def generate(target: str, destination: Path) -> int:
    forge = target == "forge-1.19.2"
    newest = target in ("neoforge-26.1.2", "neoforge-26.2")

    destination = destination.resolve()
    if destination == Path("/") or len(destination.parts) < 3:
        raise SystemExit("Choose a dedicated resource output directory")
    destination.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix=".tyc2-pack-", dir=destination.parent) as tmp_dir:
        out = Path(tmp_dir)

        def write_json(rel_path: str, value: dict):
            file_path = out / rel_path
            file_path.parent.mkdir(parents=True, exist_ok=True)
            file_path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        def asset(path: str, value: dict):
            write_json(f"assets/{MOD_ID}/{path}.json", value)

        def data(path: str, value: dict):
            write_json(f"data/{MOD_ID}/{path}.json", value)

        def mod(name: str) -> str:
            return f"{MOD_ID}:{name}"

        def item_definition(name: str):
            if newest:
                asset(
                    f"items/{name}",
                    {"model": {"type": "minecraft:model", "model": mod(f"item/{name}")}},
                )

        # ---------------------------------------------------------------------
        # 1. Blockstates & Block Models
        # ---------------------------------------------------------------------
        rotations = {"north": 0, "east": 90, "south": 180, "west": 270}

        # 1a. Primitive Minting Machine & Electric Industrial Minting Machine:
        #     4 horizontal facings x active=false/true
        for machine_name in ("primitive_mint_machine", "mint_machine"):
            machine_variants = {}
            for active in (False, True):
                model_name = f"{machine_name}_active" if active else machine_name
                front_tex = f"block/{machine_name}_front_active" if active else f"block/{machine_name}_front"
                asset(
                    f"models/block/{model_name}",
                    {
                        "parent": "minecraft:block/cube",
                        "textures": {
                            "north": mod(front_tex),
                            "south": mod(f"block/{machine_name}_side"),
                            "east": mod(f"block/{machine_name}_side"),
                            "west": mod(f"block/{machine_name}_side"),
                            "up": mod(f"block/{machine_name}_top"),
                            "down": mod(f"block/{machine_name}_bottom"),
                            "particle": mod(f"block/{machine_name}_side"),
                        },
                    },
                )
                for facing, deg in rotations.items():
                    entry: dict = {"model": mod(f"block/{model_name}")}
                    if deg:
                        entry["y"] = deg
                    machine_variants[f"active={str(active).lower()},facing={facing}"] = entry
            asset(f"blockstates/{machine_name}", {"variants": machine_variants})

        # 1b. Industrial ATM: 4 horizontal facings
        asset(
            "models/block/atm_machine",
            {
                "parent": "minecraft:block/cube",
                "textures": {
                    "north": mod("block/atm_machine_front"),
                    "south": mod("block/atm_machine_side"),
                    "east": mod("block/atm_machine_side"),
                    "west": mod("block/atm_machine_side"),
                    "up": mod("block/atm_machine_top"),
                    "down": mod("block/atm_machine_bottom"),
                    "particle": mod("block/atm_machine_side"),
                },
            },
        )
        atm_variants = {}
        for facing, deg in rotations.items():
            entry = {"model": mod("block/atm_machine")}
            if deg:
                entry["y"] = deg
            atm_variants[f"facing={facing}"] = entry
        asset("blockstates/atm_machine", {"variants": atm_variants})

        # 1c. Village Cooperation Hub: unconditional multipart (works cleanly with or without extra properties)
        asset(
            "models/block/cooperation_hub",
            {
                "parent": "minecraft:block/cube",
                "textures": {
                    "north": mod("block/cooperation_hub_front"),
                    "south": mod("block/cooperation_hub_front"),
                    "east": mod("block/cooperation_hub_side"),
                    "west": mod("block/cooperation_hub_side"),
                    "up": mod("block/cooperation_hub_top"),
                    "down": mod("block/cooperation_hub_bottom"),
                    "particle": mod("block/cooperation_hub_front"),
                },
            },
        )
        asset(
            "blockstates/cooperation_hub",
            {
                "multipart": [
                    {"apply": {"model": mod("block/cooperation_hub")}},
                ]
            },
        )

        # ---------------------------------------------------------------------
        # 2. Item Models & 26.x Client Item Definitions
        # ---------------------------------------------------------------------
        for block_name in BLOCK_ITEMS:
            asset(f"models/item/{block_name}", {"parent": mod(f"block/{block_name}")})
            item_definition(block_name)

        for coin_name in COIN_ITEMS:
            asset(
                f"models/item/{coin_name}",
                {
                    "parent": "minecraft:item/generated",
                    "textures": {"layer0": mod(f"item/{coin_name}")},
                },
            )
            item_definition(coin_name)

        # Communication Terminal + Empty variant model
        comm_model: dict = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": mod("item/comm_terminal")},
        }
        if not newest:
            # 1.19.2 & 1.21.1 support legacy item model property overrides
            comm_model["overrides"] = [
                {"predicate": {mod("empty"): 1.0}, "model": mod("item/comm_terminal_empty")},
            ]
        asset("models/item/comm_terminal", comm_model)
        asset(
            "models/item/comm_terminal_empty",
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": mod("item/comm_terminal_empty")},
            },
        )
        if newest:
            asset(
                "items/comm_terminal",
                {
                    "model": {
                        "type": "minecraft:condition",
                        "property": "minecraft:custom_model_data",
                        "index": 0,
                        "on_true": {"type": "minecraft:model", "model": mod("item/comm_terminal")},
                        "on_false": {"type": "minecraft:model", "model": mod("item/comm_terminal_empty")},
                    }
                },
            )
        else:
            item_definition("comm_terminal")

        # ---------------------------------------------------------------------
        # 3. Standalone & Conditional Crafting Recipes (Version-Specific Schema)
        # ---------------------------------------------------------------------
        recipe_dir = "recipes/" if forge else "recipe/"

        def format_ingredient(item_id: str):
            resolved = (
                resolve_common_tag(item_id[len("#common:"):], forge)
                if item_id.startswith("#common:")
                else item_id
            )
            if newest:
                # MC 1.21.2+ / 26.x: bare string item/tag identifier
                return resolved
            if resolved.startswith("#"):
                return {"tag": resolved[1:]}
            return {"item": resolved}

        def format_result(item_name: str, count: int) -> dict:
            return {("item" if forge else "id"): mod(item_name), "count": count}

        def build_conditions(condition_mode: str) -> tuple[str, list[dict]]:
            cond_key = "conditions" if forge else "neoforge:conditions"
            ns = "forge" if forge else "neoforge"
            ic2_loaded = {"type": f"{ns}:mod_loaded", "modid": "ic2"}
            if condition_mode == "ic2_loaded":
                return cond_key, [ic2_loaded]
            if condition_mode == "ic2_not_loaded":
                return cond_key, [{"type": f"{ns}:not", "value": ic2_loaded}]
            raise ValueError(f"Unknown condition_mode: {condition_mode}")

        for recipe_name, (result_name, pattern, keys, count) in SHAPED_RECIPES.items():
            data(
                recipe_dir + recipe_name,
                {
                    "type": "minecraft:crafting_shaped",
                    "pattern": pattern,
                    "key": {k: format_ingredient(v) for k, v in keys.items()},
                    "result": format_result(result_name, count),
                },
            )

        for recipe_name, (result_name, cond_mode, pattern, keys, count) in CONDITIONAL_SHAPED_RECIPES.items():
            cond_key, cond_list = build_conditions(cond_mode)
            data(
                recipe_dir + recipe_name,
                {
                    cond_key: cond_list,
                    "type": "minecraft:crafting_shaped",
                    "pattern": pattern,
                    "key": {k: format_ingredient(v) for k, v in keys.items()},
                    "result": format_result(result_name, count),
                },
            )

        # ---------------------------------------------------------------------
        # 4. Block Loot Tables & Tags (Version-Specific Directories)
        # ---------------------------------------------------------------------
        loot_dir = "loot_tables/blocks/" if forge else "loot_table/blocks/"
        block_tag_dir = "tags/blocks/" if forge else "tags/block/"
        item_tag_dir = "tags/items/" if forge else "tags/item/"

        for block_name in MINEABLE_BLOCKS:
            data(
                loot_dir + block_name,
                {
                    "type": "minecraft:block",
                    "pools": [
                        {
                            "rolls": 1,
                            "conditions": [{"condition": "minecraft:survives_explosion"}],
                            "entries": [{"type": "minecraft:item", "name": mod(block_name)}],
                        }
                    ],
                },
            )

        # Cooperation Hub is blast-proof / anti-grief protected: drops nothing on invalid break
        data(
            loot_dir + "cooperation_hub",
            {
                "type": "minecraft:block",
                "pools": [],
            },
        )

        write_json(
            f"data/minecraft/{block_tag_dir}mineable/pickaxe.json",
            {"replace": False, "values": [mod(b) for b in MINEABLE_BLOCKS]},
        )

        # Protect Cooperation Hub against Wither and Ender Dragon block destruction across all MC versions
        for boss_tag in ("wither_immune", "dragon_immune"):
            write_json(
                f"data/minecraft/{block_tag_dir}{boss_tag}.json",
                {"replace": False, "values": [mod("cooperation_hub")]},
            )

        # Optional village POI tag entry (safe whether custom POI or vanilla meeting POI is used)
        write_json(
            "data/minecraft/tags/point_of_interest_type/village.json",
            {
                "replace": False,
                "values": [{"id": mod("cooperation_hub"), "required": False}],
            },
        )

        # Convenience item tag grouping all 9 TyC2 coins + optional IC2 coin
        coin_tag_values = [mod(c) for c in COIN_ITEMS] + [{"id": "ic2:coin", "required": False}]
        write_json(
            f"data/{MOD_ID}/{item_tag_dir}coins.json",
            {"replace": False, "values": coin_tag_values},
        )

        # Common cross-mod tags: forge:* on Forge 1.19.2, c:* on NeoForge 1.21.1 / 26.x
        common_ns = "forge" if forge else "c"
        write_json(
            f"data/{common_ns}/{block_tag_dir}relocation_not_supported.json",
            {"replace": False, "values": [mod(b) for b in BLOCK_ITEMS]},
        )
        write_json(
            f"data/{common_ns}/{item_tag_dir}coins.json",
            {"replace": False, "values": coin_tag_values},
        )

        # ---------------------------------------------------------------------
        # 5. Built-in Datapack JSON Presets (data/tyc2/...)
        # ---------------------------------------------------------------------
        data("mint_recipes/default_vanilla", MINT_RECIPES_VANILLA)
        data("mint_recipes/default_ic2", MINT_RECIPES_IC2)

        data("goods/vanilla", GOODS_VANILLA)
        data("goods/ic2", GOODS_IC2)
        data("goods/ecc2", GOODS_ECC2)
        data("goods/farmers_delight", GOODS_FARMERS_DELIGHT)
        data("goods/kaleidoscope_cookery", GOODS_KALEIDOSCOPE_COOKERY)

        data("seasons/harvest_seasons", HARVEST_SEASONS)
        data("levels/village_levels", VILLAGE_LEVELS)
        data("workplaces/vanilla_workplaces", WORKPLACES_VANILLA)

        # ---------------------------------------------------------------------
        # 6. Forge 1.19.2 pack.mcmeta
        # ---------------------------------------------------------------------
        if forge:
            write_json(
                "pack.mcmeta",
                {
                    "pack": {
                        "pack_format": 9,
                        "forge:resource_pack_format": 9,
                        "forge:data_pack_format": 10,
                        "description": "TycoonCraft²: The New Era resources for Minecraft 1.19.2",
                    }
                },
            )

        count = sum(1 for p in out.rglob("*") if p.is_file() and p.suffix in (".json", ".mcmeta"))
        publish(out, destination)
        print(f"{target}: generated {count} files in {destination}")
        return count


def main():
    parser = argparse.ArgumentParser(description="Generate TyC2 version-specific resources and datapack presets.")
    parser.add_argument("--target", required=True, choices=TARGETS)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    generate(args.target, args.output)


if __name__ == "__main__":
    main()
