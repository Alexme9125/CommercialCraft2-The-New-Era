# TycoonCraft²: The New Era (`tyc2`)

[简体中文](README.md) · **English**

TycoonCraft²: The New Era (`tyc2`) is a Minecraft mod built around currency minting, personal and company banking, village trade hubs, dynamic macro prices, and automated village expansion. It runs standalone in plain Vanilla Minecraft with no required dependencies.

If you also install [IndustrialCraft²: The New Era (`ic2`)](https://github.com/spectrum527/IndustrialCraft2-The-New-Era) or [LogisticsCraft²: The New Era (`lc2`)](https://github.com/spectrum527/LogisticsCraft2-The-New-Era), optional integration activates automatically: the Industrial Minting Machine connects directly to EU cables and accepts IC2 upgrades, `1x` coins unify onto `ic2:coin`, IC2 alloys, iridium shards, and crops enter village trade, and the Cooperation Hub supports LC2 chunk loading and automated cargo routes.

## Downloads and installation

Pick the JAR that matches your Minecraft version and mod loader, then drop it into your instance's `mods/` folder. Multiplayer requires the mod on both client and server.

[v1.0.0-pre.1 Release Notes](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/tag/v1.0.0-pre.1) · [SHA-256 Checksums](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/SHA256SUMS.txt) · [Live Server & Client-Server Field Test Report](docs/FIELD_TEST_REPORT.md) · [Design Specification](docs/DESIGN.md)

| Minecraft | Loader | Java | Download |
| --- | --- | ---: | --- |
| 1.19.2 | Forge 43.5.0 | 17 | [Forge 1.19.2 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-forge-1.19.2-1.0.0-pre.1.jar) |
| 1.21.1 | NeoForge 21.1.265 | 21 | [NeoForge 1.21.1 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-neoforge-1.21.1-1.0.0-pre.1.jar) |
| 26.1.2 | NeoForge 26.1.2.128 | 25 | [NeoForge 26.1.2 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-neoforge-26.1.2-1.0.0-pre.1.jar) |
| 26.2 | NeoForge 26.2.0.95 | 25 | [NeoForge 26.2 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-neoforge-26.2-1.0.0-pre.1.jar) |

TyC2 detects optional companion mods at startup and ignores any you do not install.

- Industry and logistics: [IndustrialCraft²: The New Era (`ic2` v1.0.5+)](https://github.com/spectrum527/IndustrialCraft2-The-New-Era), [LogisticsCraft²: The New Era (`lc2` v1.1.0+)](https://github.com/spectrum527/LogisticsCraft2-The-New-Era)
- Seasons and farming: Serene Seasons, Ecliptic Seasons, Farmer's Delight, Kaleidoscope Cookery
- HUD and recipe viewers: The One Probe, Jade, JEI

## Gameplay tutorial

Follow these five stages in a new world to go from your first coins to an automated multi-village trade network.

### Mint your starting capital

The currency comes in nine denominations: `1x`, `5x`, `10x`, `20x`, `50x`, `100x`, `200x`, `500x`, and `1000x`. Start by crafting a Primitive Minting Machine (`tyc2:primitive_mint_machine`) from plain Vanilla materials (iron ingots, a copper ingot, pistons, a furnace, and stone bricks), with no industrial parts required.

Place furnace fuel such as coal, charcoal, or planks in the bottom fuel slot and put copper, iron, gold, or emeralds in the top two input slots. The Primitive Minting Machine runs on fuel alone, takes no electricity or upgrades, works at `1.5x` the Industrial Minting Machine's base time, and mints Vanilla `1x` to `100x` coins only. If you put diamonds, netherite, or modded metals inside, the screen shows an amber warning telling you to upgrade.

Once you have power generation, upgrade the Primitive Minting Machine into an Industrial Minting Machine (`tyc2:mint_machine`):

- With IC2 installed, the crafting recipe switches to IC2 parts (`ic2:machine`, `ic2:circuit`, `ic2:electric_motor`, and `ic2:iron_plate` around your Primitive Minting Machine).
- Without IC2, the crafting recipe switches to iron blocks, redstone blocks, a diamond, gold ingots, and pistons around your Primitive Minting Machine, ready for FE tech mods.

The Industrial Minting Machine is strictly electric and does not burn coal. It accepts IC2 EU cables (`16,000 EU` base buffer, LV `32 EU/t`), standard Forge/NeoForge FE (`1 EU = 4 FE`), and rechargeable or single-use IC2/FE batteries in Slot 3. It runs at full `1.0x` speed, mints every denomination from `1x` to `1000x` along with IC2 and tagged `#c:ingots/*` or `#forge:ingots/*` alloys, accepts four IC2 upgrades (Overclocker, Transformer, Energy Storage, Ejector, Pulling), and charges a Comm Terminal in Slot 4.

When IC2 is installed, both minting machines and the ATM output `ic2:coin` as the standard `1x` coin, while still accepting `tyc2:coin_1` at 1:1. IC2's Metal Former cutting recipe from iron casing to `ic2:coin` is disabled by default (configurable in `config/tyc2/general.json`) so all newly minted money is counted in the world's money supply.

### Set up banking and a comm terminal

Craft an Industrial ATM (`tyc2:atm_machine`) from iron ingots, glass panes, gold ingots, a chest, and redstone. Use it to deposit stacks or all coins from your inventory, withdraw specific denominations, or withdraw any custom amount with automatic change and zero fees.

Every player gets a personal bank account automatically. On multiplayer servers, you can also create a named company account, generate an invite code for teammates, switch your active settlement account between personal and company, send 1:1 transfers to players or companies, and inspect the last `32` transactions.

Next, craft a handheld Commerce Comm Terminal (`tyc2:comm_terminal`, capacity `20,000 EU` or `80,000 FE`). Recharge it in the ATM's right-side charging slot. Because the ATM accepts EU, FE, and coal or redstone fuel as a fallback, standalone Vanilla players can always recharge their terminal. With IC2 installed, the terminal also charges inside BatBoxes, CESUs, MFEs, MFSUs, and charge pads.

### Negotiate with a village mayor

Visit any village that has a Bell (`minecraft:bell`) and at least `2` villagers within a `48`-block radius. The village automatically elects an adult villager who carries a golden `[Mayor]` tag. Villages with zero or one villager never elect a Mayor. If a Mayor dies and at least `2` villagers remain, the village elects a replacement.

Bring the default startup package: `100 IC` (cash or bank balance), `32` logs, `16` iron ingots, and `32` basic food items. Right-click the Mayor and sign the partnership:

1. The village Bell turns into a blast-proof Cooperation Hub (`tyc2:cooperation_hub`, resistance `3,600,000`, protected against normal mining so explosions or misclicks cannot spill `1,000` slots onto the ground). Only the owner, a company admin, or an OP can pack up the Hub for relocation or end the partnership from its Settings tab.
2. Normal villagers in that village stop offering emerald trades, moving all village commerce to the Cooperation Hub.
3. The first Lv 1 buy and sell orders appear right away. If a raid wipes out every villager later, the partnership record stays safe and orders pause until at least `2` villagers return.

### Automate trade and run remote orders

Open the Cooperation Hub to browse village sell orders (goods you buy from the village) and buy orders (goods you deliver for payment). In person, you can settle orders with physical coins or your bank account.

The Hub's `1,000`-slot paginated warehouse (with search) is divided into two separate `500`-slot halves so pipes and logistics blocks never pull back what they just delivered:

- Inbound zone (slots `0` to `499`): external pipes, hoppers, IC2 Pulling Upgrades, and LC2 Smart Ports or Cargo Stations can insert here only. Delivered goods satisfy village buy orders and expansion material requests.
- Outbound zone (slots `500` to `999`): goods you buy from the village land here, and external pipes, hoppers, IC2 Ejector Upgrades, and LC2 stations can extract from here only.

Turn on Auto-Fulfill for any buy or expansion order and the Hub checks the Inbound zone every `40` ticks, consuming delivered items and paying your active bank account automatically.

To manage villages from anywhere, sneak and right-click a Hub with a charged Comm Terminal to bind it (up to `16` villages per terminal). Right-click the terminal in the air to switch villages, check stock counts, run bank-settled trades (`50 EU` per trade), toggle Auto-Fulfill, read the CPI chart, or trigger upgrades and expansions (`100 EU`). Remote banking and order timers run virtually even when the village chunk is unloaded. If you also want pipes or LC2 boats and drones to keep moving cargo while you are away, place `lc2:chunk_loading_upgrade` in one of the Hub's two upgrade slots to keep a `3x3` chunk area loaded.

### Village levels, inflation, seasons, and expansion

Villages grow from Lv 1 to Lv 10 once they meet three targets in the Hub's Growth tab: population, cumulative trade volume, and village treasury balance (upgrading spends part of the village treasury). Low-level villages (Lv 1 to 3) mostly sell crops, wool, leather, and mob drops while buying coal, ores, and basic ingots. High-level villages (Lv 7 to 10) buy cooked meals and gourmet dishes while selling high-tier enchanted books and alloys, with Lv 10 unlocking Iridium Shards (or Ancient Debris when IC2 is absent). By default, offered goods stay below the player's or company's current industrial tier so villages do not skip your progression.

Prices move with a global Consumer Price Index (CPI) driven by total money supply `M` against rolling goods circulation `Q`, adjusted by each village's cash reserve, per-item supply and demand, season, and biome specialty (clamped between `0.25x` and `4.00x`). Heavy minting without trade pushes CPI up into inflation. Steady trading combined with village upgrade costs, expansion budgets, and daily municipal upkeep drains excess money and brings prices down. Both the Hub and the Comm Terminal display a `48`-point CPI line chart.

When Serene Seasons or Ecliptic Seasons is installed, farming villages sell in-season harvests at `0.75x` price and double volume. Once a crop goes out of season, especially in winter, villages stop selling it and post `1.45x` to `1.75x` buy orders instead, so you can stockpile harvests and sell them in the off-season. Farmer's Delight, Kaleidoscope Cookery, and IC2 crops and dishes join the catalog automatically when those mods are present.

Partnered villages also post periodic expansion material orders. Once supplied, the village builds outward `2` blocks every `3` ticks from the ground up using templates tailored to its biome (Plains, Desert, Savanna, Taiga, or Snowy): roads and streetlights, crop farms, animal ranches, cottages, larger homes, multi-story townhouses, and open waterfront piers or helipad plazas sized for LC2 ports and drone stations. The builder checks live terrain and a player-placed block tracker so it never overwrites player builds, chests, machines, pipes, or existing village structures. Finished houses attract new villagers automatically. You can pause expansion or adjust the allowed radius (`10` to `72` blocks) anytime in the Hub settings.

## Commands and customization

Built-in commands (the last four require OP):

- `/tyc2 status`: show global CPI, money supply, active season, and nearest village info
- `/tyc2 cpi`: show the current price index and inflation state
- `/tyc2 season`: show the detected season
- `/tyc2 reload`: reload external config files and data overrides
- `/tyc2 mint <amount>`: debug helper to adjust tracked money supply
- `/tyc2 expand [projectType]`: trigger an expansion project on the nearest partnered village
- `/tyc2 levelup`: test upgrading the nearest partnered village

Modpack authors and developers can extend the mod through three layers:

1. Datapack JSON (supports `/reload`): add or override `data/<namespace>/tyc2/goods/*.json`, `mint_recipes/*.json` (supports `#ingots/*` tags), `seasons/*.json`, and `levels/*.json`.
2. External config folder (created on first launch): edit `config/tyc2/general.json` (toggle IC2 Metal Former coin blocking, negotiation costs, CPI bounds, refresh and expansion timers), or add entries to `custom_goods.json` and `custom_mint_recipes.json` and run `/tyc2 reload`.
3. Public Java API: use `com.tycooncraft.tyc2.api.TyC2RegistryApi` to register custom goods, minting recipes, season providers, and industrial tier detectors from another mod.

## Building from source

This repository builds all four targets: Forge `1.19.2` and NeoForge `1.21.1`, `26.1.2`, and `26.2`.

### Prerequisites

| Tool | Version | Used for |
| --- | --- | --- |
| JDK 17 | OpenJDK / Liberica 17 | Forge `1.19.2` build and reobfuscation |
| JDK 21 | OpenJDK 21 | Pure-Java common module, JUnit 5 tests, NeoForge `1.21.1` |
| JDK 25 | OpenJDK 25 | NeoForge `26.1.2` and NeoForge `26.2` |
| Python 3 | 3.10+ | Cross-version resource generation and JAR audit (install Pillow only if regenerating textures) |

### Place compile-only IC2 JARs in `.ic2-deps/`

IC2 is optional at runtime, but compiling the isolated soft-integration bridges requires the four IC2 `v1.0.5` JARs as `compileOnly` dependencies. Download them from the [IC2: The New Era v1.0.5 release](https://github.com/spectrum527/IndustrialCraft2-The-New-Era/releases/tag/v1.0.5) and place them in `.ic2-deps/` at the repository root with these exact filenames:

```text
.ic2-deps/
├── ic2-forge-1.19.2-1.0.5.jar
├── ic2-neoforge-1.21.1-1.0.5.jar
├── ic2-neoforge-26.1.2-1.0.5.jar
└── ic2-neoforge-26.2-1.0.5.jar
```

### Build all four targets

Run these commands from the repository root (on Windows, replace `./gradlew` with `gradlew.bat` and `./forge-1.19.2/gradlew` with `forge-1.19.2\gradlew.bat`):

```sh
# 1. Pure-Java import check, 31 JUnit 5 tests, and NeoForge 1.21.1 / 26.1.2 / 26.2 JARs
./gradlew :checkCommonImports :test:test :neoforge:1.21.1:jar :neoforge:26.1.2:jar :neoforge:26.2:jar

# 2. Forge 1.19.2 reobfuscated JAR with JDK 17 (point JAVA_HOME to your JDK 17 install)
JAVA_HOME=/path/to/jdk-17 ./forge-1.19.2/gradlew -p forge-1.19.2 build reobfJar
```

Built JARs appear at:

- Forge 1.19.2: `forge-1.19.2/build/libs/tyc2-forge-1.19.2-1.0.0-pre.1.jar`
- NeoForge 1.21.1: `src/neoforge/1.21.1/build/libs/tyc2-neoforge-1.21.1-1.0.0-pre.1.jar`
- NeoForge 26.1.2: `src/neoforge/26.1.2/build/libs/tyc2-neoforge-26.1.2-1.0.0-pre.1.jar`
- NeoForge 26.2: `src/neoforge/26.2/build/libs/tyc2-neoforge-26.2-1.0.0-pre.1.jar`

### Run static audits and live QA (optional)

Verify assets, conditional recipes, and zero hard IC2 dependencies across all four built JARs:

```sh
python3 src/qa/audit_jars.py
```

To run the full standalone and IC2+LC2 live dedicated-server QA suites, also place the matching LC2 `v1.1.0` JAR into `.ic2-deps/` (or pass `-Plc2Jar=/path/to/lc2.jar`):

```sh
# NeoForge (swap 1.21.1 for 26.1.2 or 26.2 as needed)
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:1.21.1:tyc2QaAll

# Forge 1.19.2
JAVA_HOME=/path/to/jdk-17 ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2QaAll
```

## License

- Source code is licensed under [GPL-3.0-only](LICENSE).
- Original pixel art textures and GUI atlases are licensed under CC BY-SA 4.0.
