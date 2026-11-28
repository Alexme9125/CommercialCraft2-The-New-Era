# Monetera (`mnera`) — Design & Engineering Specification

**经贸纪元（Monetera）** — Industrial economy, multi-denomination currency minting, personal & guild banking, village trade hubs, macro-economic inflation/deflation simulation, seasonal agriculture arbitrage, and gradual collision-safe village expansion.

- **Optional Enhancement Addon** for **Machinera (`mhera`)** and **Transitera (`trera`)** — **neither is a required dependency**! Must boot, run, and offer full progression standalone in pure Vanilla Minecraft, while seamlessly unlocking enhanced EU power, MHERA coins/alloys/crops/upgrades/wrench, and TRERA automated logistics integration when installed.
- **Supported Targets (4)**:
  - **Forge 1.19.2** (Forge 43.5.0, Java 17) — `forge-1.19.2/`
  - **NeoForge 1.21.1** (NeoForge 21.1.265, Java 21) — `:neoforge:1.21.1`
  - **NeoForge 26.1.2** (NeoForge 26.1.2.128, Java 25) — `:neoforge:26.1.2`
  - **NeoForge 26.2** (NeoForge 26.2.0.95, Java 25) — `:neoforge:26.2`
- **Mod ID**: `mnera` · **Root Package**: `com.tycooncraft.tyc2` · **Version**: `1.0.0`
- **License**: **GPL-3.0-only** (code) / **CC BY-SA 4.0** (original pixel art assets).
- **Common Pure-Java Rule**: `src/common/` MUST compile with Java 17 (`options.release = 17`), MUST NOT import any `net.minecraft.*`, `net.neoforged.*`, `net.minecraftforge.*`, or `net.fabricmc.*` classes (enforced by `:checkCommonImports`), and MUST have zero external runtime dependencies.

---

## 1. Canonical Registry IDs (Identical Across All 4 Versions)

### 1.1 Blocks (4)
| Registry ID | English | 中文 | Notes |
|---|---|---|---|
| `mnera:primitive_mint_machine` | Primitive Minting Machine | 原始铸币机 | Horizontal facing + `active` boolean property. 100% Vanilla recipe (zero industrial ingredients). Burnable fuel only (coal/charcoal/coal_block/blaze_rod/planks, NO EU/FE power, NO upgrade/terminal slots, 1.5× slower). Mints **Vanilla low-to-mid denominations (`1x–100x`) only**. |
| `mnera:mint_machine` | Industrial Minting Machine | 工业铸币机 | Horizontal facing + `active` boolean property. **Pure Electric** (MHERA EU + FE + battery discharge slot; NO coal burning). 4 MHERA upgrade slots + terminal charging slot. Mints **all denominations (`1x–1000x`) & all Vanilla/MHERA/modded alloys** at full speed (1.0×, overclockable). Conditional recipe: MHERA industrial materials when `mhera` is loaded; high-tier Vanilla redstone/diamond upgrade recipe when `mhera` is absent. |
| `mnera:atm_machine` | Industrial ATM | 工业ATM机 | Horizontal facing. Bank deposit/withdraw, denomination exchange, personal/guild account management, player transfers, terminal charging slot (EU/FE/coal fallback). Pure Vanilla recipe. |
| `mnera:cooperation_hub` | Village Cooperation Hub | 村庄合作枢纽 | Replaces village Bell upon Mayor negotiation. Blast-proof (`resistance = 3600000`, `strength = -1` / protected break), 1000-slot SavedData-backed 2-zone warehouse (500 Inbound + 500 Outbound), village `MEETING` POI compatible. |

### 1.2 Block Entities (4)
- `mnera:primitive_mint_machine`
- `mnera:mint_machine`
- `mnera:atm_machine`
- `mnera:cooperation_hub`

### 1.3 Items (14 = 4 BlockItems + 9 Coins + 1 Electric Tool)
| Registry ID | English | 中文 | Value / Stack | Notes |
|---|---|---|---|---|
| `mnera:primitive_mint_machine` | Primitive Minting Machine | 原始铸币机 | 64 | BlockItem (pure Vanilla recipe) |
| `mnera:mint_machine` | Industrial Minting Machine | 工业铸币机 | 64 | BlockItem (MHERA industrial recipe when MHERA loaded; high-tier redstone recipe standalone) |
| `mnera:atm_machine` | Industrial ATM | 工业ATM机 | 64 | BlockItem |
| `mnera:cooperation_hub` | Village Cooperation Hub | 村庄合作枢纽 | 1 | BlockItem (creative/relocation item; carries bound village UUID on authorized relocate) |
| `mnera:coin_1` | Universal Currency (1x) | 通用货币 (1x) | 1 / 64 | Standalone 1x coin. When `mhera` is installed, `mhera:coin` is treated as canonical 1x legal tender; BOTH `mhera:coin` and `mnera:coin_1` are mutually accepted at 1x everywhere, and Mint/ATM output `mhera:coin` when `mhera` is loaded! |
| `mnera:coin_5` | Universal Currency (5x) | 通用货币 (5x) | 5 / 64 | Copper-bronze octagonal coin |
| `mnera:coin_10` | Universal Currency (10x) | 通用货币 (10x) | 10 / 64 | Tempered steel-blue octagonal coin |
| `mnera:coin_20` | Universal Currency (20x) | 通用货币 (20x) | 20 / 64 | Silver-emerald rimmed coin |
| `mnera:coin_50` | Universal Currency (50x) | 通用货币 (50x) | 50 / 64 | Gilded silver crest coin |
| `mnera:coin_100` | Universal Currency (100x) | 通用货币 (100x) | 100 / 64 | Pure gold sovereign coin |
| `mnera:coin_200` | Universal Currency (200x) | 通用货币 (200x) | 200 / 64 | Sapphire-gold refined alloy coin |
| `mnera:coin_500` | Universal Currency (500x) | 通用货币 (500x) | 500 / 64 | Netherite-amethyst dark gold coin |
| `mnera:coin_1000` | Universal Currency (1000x) | 通用货币 (1000x) | 1000 / 64 | Iridium-platinum radiant sovereign coin |
| `mnera:comm_terminal` | Commerce Comm Terminal | 商业通讯终端 | 1 | Rechargeable electric device (max 20,000 EU / 80,000 FE). Shift-right-click Cooperation Hub to bind (supports binding up to 16 villages!); right-click anywhere to open remote trade/economy/banking UI. |

### 1.4 Menus (6)
- `mnera:primitive_mint_machine`
- `mnera:mint_machine`
- `mnera:atm_machine`
- `mnera:cooperation_hub`
- `mnera:comm_terminal`
- `mnera:mayor_negotiation`

### 1.5 Creative Tab (1)
- `mnera:main` — `Monetera` / `经贸纪元` (icon: `mnera:coin_100`)

### 1.6 POI & Villager Integration
- Village Mayor is a standard Minecraft `Villager` tagged in PersistentData (`mnera_mayor = true`, `mnera_village_id = <uuid>`), given a distinct golden title (`[村长] ...` / `[Mayor] ...`), glowing golden badge/crown visual indicator (or custom display/equipment), and custom interaction handler:
  - Before cooperation: right-clicking opens `mnera:mayor_negotiation` screen showing initial coin + basic materials requirement and village stats.
  - After cooperation: Mayor remains active in the village as village representative (right-clicking shows village level, funds, active orders, and points to the Cooperation Hub; if killed, a new Mayor is automatically elected from remaining villagers as long as `villagerCount >= 2`).
  - Normal villagers belonging to a partnered village CANNOT open vanilla emerald trading; right-clicking displays an actionbar message directing the player to the Cooperation Hub.
  - When a village has `<= 1` villagers, no Mayor spawns/elects. If all villagers die in a partnered village, cooperation state is preserved in `TycoonSavedData`, order generation pauses, and once `>= 2` villagers are present again, a new Mayor is elected and operations resume automatically.

---

## 2. Currency, Minting Machine & Tri-Mode Power

### 2.1 Denomination System (`CurrencyDenomination`)
9 denominations: `1, 5, 10, 20, 50, 100, 200, 500, 1000`.
- `mhera:coin` = `1x` (canonical 1x output when `mhera` is installed).
- `mnera:coin_1` = `1x` (canonical 1x output when `mhera` is NOT installed; always accepted 1:1 alongside `mhera:coin`).
- When `mhera` IS installed and config `disableIc2MetalFormerCoinRecipe = true` (default), optional Mixins (`Ic2ProcessingRecipesMixin` & `Ic2WorkshopRecipesMixin`, loaded ONLY via `TyC2MixinPlugin` when `mhera` is detected!) filter out `metal_former_cutting` recipes whose output is `mhera:coin`, preventing bypassing the Minting Machine.

### 2.2 Primitive Minting Machine (`mnera:primitive_mint_machine`) vs Industrial Minting Machine (`mnera:mint_machine`)

#### A. Primitive Minting Machine (`mnera:primitive_mint_machine`)
- **Crafting Recipe (100% Vanilla, ZERO industrial materials)**:
  - Shaped (`["ICI", "PFP", "SSS"]`): `I = minecraft:iron_ingot`, `C = minecraft:copper_ingot`, `P = minecraft:piston`, `F = minecraft:furnace`, `S = minecraft:stone_bricks` (works unconditionally with or without MHERA!).
- **Power Source**: Pure burnable fuel only (standard furnace fuel: Coal = 1600 ticks, Charcoal = 1600 ticks, Coal Block = 16000 ticks, Blaze Rod = 2400 ticks, Planks/Logs/Sticks). **Does NOT connect to MHERA EU or FE grids**, has **NO upgrade slots** and **NO terminal charging slot**.
- **Inventory Slots (4)**:
  - `0, 1`: 2 Metal Input Slots
  - `2`: Output Coin Slot
  - `3`: Burnable Fuel Slot
- **Allowed Recipes (`primitiveAllowed = true`)**:
  - **Strictly Vanilla low-to-mid denominations (`1x–100x`) only** (Copper `1x`, Iron `5x`, 2×Iron `10x`, Gold `20x`, Gold+Iron `50x`, 2×Gold+Emerald `100x`).
  - **Blocks** high denominations (`200x`, `500x`, `1000x` such as Diamond/Netherite/Iridium) AND all modded/MHERA metals & alloys (Tin, Bronze, Silver, Steel, Mixed Metal, Advanced Alloy, Iridium) — GUI displays a clear warning directing the player to upgrade to the electric Industrial Minting Machine!
  - Minting duration is `1.5×` the Industrial Minting Machine base time.

#### B. Industrial Minting Machine (`mnera:mint_machine`)
- **Conditional Crafting Recipes**:
  - **When `mhera` IS installed** (`mod_loaded("ic2")`): Requires MHERA industrial components — `["PEP", "CMC", "PBP"]` where `P = mhera:iron_plate`, `E = mhera:electric_motor`, `C = mhera:circuit`, `M = mnera:primitive_mint_machine`, `B = mhera:machine`!
  - **When `mhera` is NOT installed** (`not(mod_loaded("ic2"))`): High-tier Vanilla/Redstone precision upgrade recipe — `["IDI", "RMR", "GBG"]` where `I = minecraft:iron_block`, `D = minecraft:diamond`, `R = minecraft:redstone_block`, `M = mnera:primitive_mint_machine`, `G = minecraft:gold_ingot`, `B = minecraft:piston` (ideal for FE tech modpacks like Create/Mekanism/Thermal).
- **Inventory Slots (9)**:
  - `0, 1`: 2 Metal/Alloy Input Slots (allows single-metal or dual-metal/alloy minting!)
  - `2`: Output Coin Slot
  - `3`: Electric Battery Discharge Slot (accepts MHERA rechargeable/single-use batteries & FE energy items — **NO solid coal/fuel burning!**)
  - `4`: Terminal Charging Slot (charges `mnera:comm_terminal` from the machine's EU/FE energy buffer)
  - `5..8`: 4 Upgrade Slots (when MHERA installed, accepts `mhera:overclocker_upgrade`, `mhera:transformer_upgrade`, `mhera:energy_storage_upgrade`, `mhera:ejector_upgrade`, `mhera:advanced_ejector_upgrade`, `mhera:pulling_upgrade`, `mhera:advanced_pulling_upgrade`)
- **Pure Electric Power System** (1 EU = 4 FE internal equivalence; internal buffer stored in canonical EU units):
  - Base capacity: **16,000 EU** (= 64,000 FE), base tier LV (**32 EU/t** = 128 FE/t).
  - **MHERA EU**: Registers external `WorldPowerPort` adapter via `EnergyNetwork.load(level, adapter)` + `Ic2CableBlockMixin` visual arm connection.
  - **Forge/NeoForge FE**: Exposes standard `IEnergyStorage` / `Capabilities.Energy` so Create, Mekanism, Thermal, AE2, etc. power it seamlessly!
  - **Battery Discharge (Slot 3)**: Discharges MHERA batteries (when MHERA loaded) or FE energy-storing items (always).
- **Allowed Recipes**: **ALL** denominations (`1x–1000x`), ALL Vanilla + MHERA + modded common-tag metals and alloys! Full `1.0×` base speed (overclockable with `mhera:overclocker_upgrade`).
- **Minting Recipes (Data-Driven + Common Tag Support `c:ingots/*` & `forge:ingots/*`)**:
  Base minting energy: `400 EU` (base 80 ticks @ 5 EU/t; scales with denomination & overclockers).
  Every minted coin injects its face value into the global **Money Supply (`totalMintedCurrency`)** in `MacroEconomyEngine`!

  | Output Denomination | Output Count | Inputs (Tag or Item ID) | Energy (EU) | Time (ticks) | Availability |
  |---|---:|---|---:|---:|---|
  | `1x` (`mhera:coin` / `mnera:coin_1`) | 2 | 1× Copper Ingot (`#ingots/copper`) | 300 | 60 | Always |
  | `1x` | 2 | 1× Tin Ingot (`#ingots/tin`) | 300 | 60 | MHERA / Modded |
  | `5x` (`mnera:coin_5`) | 1 | 1× Iron Ingot (`#ingots/iron`) | 400 | 80 | Always |
  | `5x` | 2 | 1× Copper + 1× Tin (`#ingots/copper` + `#ingots/tin`) | 500 | 80 | MHERA / Modded |
  | `10x` (`mnera:coin_10`) | 1 | 1× Bronze Ingot (`#ingots/bronze`) OR 2× Iron Ingot | 600 | 100 | Always / MHERA |
  | `20x` (`mnera:coin_20`) | 1 | 1× Silver Ingot (`#ingots/silver`) OR 1× Gold Ingot (`#ingots/gold`) | 800 | 100 | Always / MHERA |
  | `20x` | 1 | 1× Steel / Refined Iron Ingot (`#ingots/steel` / `mhera:refined_iron_ingot`) | 800 | 100 | MHERA / Modded |
  | `50x` (`mnera:coin_50`) | 1 | 1× Gold Ingot + 1× Iron Ingot | 1,200 | 120 | Always |
  | `50x` | 1 | 1× Gold Ingot + 1× Steel/Silver Ingot | 1,200 | 120 | MHERA / Modded |
  | `100x` (`mnera:coin_100`) | 1 | 2× Gold Ingot + 1× Emerald/Diamond (`minecraft:emerald`) | 1,800 | 140 | Always |
  | `100x` | 1 | 1× Mixed Metal Ingot / Advanced Alloy (`mhera:alloy` / `mhera:mixed_metal_ingot`) + 1× Gold Ingot | 1,800 | 140 | MHERA |
  | `200x` (`mnera:coin_200`) | 1 | 1× Diamond + 2× Gold Ingot | 2,500 | 160 | Always |
  | `200x` | 1 | 2× Advanced Alloy (`mhera:alloy`) + 1× Diamond | 2,500 | 160 | MHERA |
  | `500x` (`mnera:coin_500`) | 1 | 1× Netherite Scrap + 2× Gold Ingot | 4,000 | 200 | Always |
  | `500x` | 1 | 1× Netherite Ingot | 4,000 | 200 | Always (yields 2× `500x` = 1000!) |
  | `1000x` (`mnera:coin_1000`) | 1 | 1× Netherite Ingot + 1× Diamond | 6,000 | 240 | Always |
  | `1000x` | 1 | 1× Iridium (`mhera:iridium` / `mhera:iridium_ore`) + 1× Advanced Alloy (`mhera:alloy`) | 6,000 | 240 | MHERA |

  *(Note: all recipes are defined in `data/mnera/mint_recipes/*.json` and overridable via datapack, `config/mnera/mint_recipes.json`, or `TyC2RegistryApi`!)*

---

## 3. Industrial ATM & Banking System (`mnera:atm_machine`)

- **Account Model (`BankAccountManager`, persisted in Overworld `TycoonSavedData`)**:
  - **Personal Account**: Automatically created per player UUID on first interaction.
  - **Guild / Company Shared Account (商会共享账户)**: Players can create a named company/guild account (`companyId`), invite/add members by username/UUID (or join via invite code), leave, and toggle their **Active Settlement Account** (`PERSONAL` vs `COMPANY`) right inside the ATM or Comm Terminal!
  - **Transfers**: Instant 1:1 transfer from active account to another player's personal account or a company account (with transaction log history of last 32 entries).
  - **Cash Deposit & Withdrawal**:
    - Deposit slot accepts any of the 9 denominations (including `mhera:coin`), instantly crediting the active account.
    - Withdraw lets the player either choose a **specific denomination + quantity** OR enter an **arbitrary amount** (auto-dispensing optimal high-to-low denominations; outputs `mhera:coin` for 1x when MHERA is installed, else `mnera:coin_1`).
    - Denomination Exchange: Deposit high coin → withdraw small coins (or vice versa) with 0% fee!
  - **Terminal Charging Slot**: Built-in charging slot in the ATM (draws EU/FE if powered, OR accepts coal/redstone fuel slot inside ATM so standalone players can always charge their `mnera:comm_terminal`!).
  - **MHERA Integration**: When MHERA is loaded, `mnera:comm_terminal` can ALSO charge directly inside MHERA BatBox/CESU/MFE/MFSU/Charging Pads via optional `Ic2PortableGearMixin`!

---

## 4. Communication Terminal (`mnera:comm_terminal`)

- **Energy**: Max `20,000 EU` (= `80,000 FE`), LV/MV charge rate `128 EU/t`.
- **Usage Costs**:
  - Opening remote village terminal: `20 EU`
  - Executing a remote trade transaction (Buy or Sell): `50 EU`
  - Executing a remote village upgrade / expansion trigger: `100 EU`
  - Zero charge → cannot execute remote actions (tooltip & UI warn "电量不足，请前往 ATM / 铸币机 / MHERA 充电设备充电").
- **Binding**:
  - Sneak + right-click a `mnera:cooperation_hub` to bind that village (stores up to **16 bound village UUIDs** in item NBT/DataComponent; sneak-right-click again to unbind or select active).
  - Right-click in air to open the multi-village remote management screen (`mnera:comm_terminal`), allowing switching between bound villages, viewing remote 1000-slot Hub inventory summary (Inbound / Outbound stock), executing bank-settled trades, toggling per-order Auto-Fulfill, viewing Macro Economy CPI & price chart, and triggering upgrades/expansion even when the village chunk is unloaded!

---

## 5. Village Mayor, Negotiation & Cooperation Hub (`mnera:cooperation_hub`)

### 5.1 Village Detection & Mayor Election
- A village is detected around a vanilla `minecraft:bell` (or existing `mnera:cooperation_hub`) by scanning villagers within radius `48` blocks. Minimum separation between two tracked villages: `96` blocks.
- **Election Rule**:
  - If `villagerCount >= 2` and no living Mayor UUID exists for that village: elect the oldest/highest-XP adult non-nitwit villager (or any adult villager) as Mayor!
  - If `villagerCount <= 1`: DO NOT spawn/elect a Mayor.
  - On Mayor death (`LivingDeathEvent`): notify nearby players / bound owner, clear Mayor UUID, and schedule immediate re-election if `>= 2` villagers remain!

### 5.2 Initial Negotiation Conditions
- Default negotiation package (configurable in `config/mnera/general.json`):
  - **Currency**: `100` UC (cash in inventory/negotiation slot OR bank balance if player has ATM account)
  - **Basic Supplies**: `32×` Any Logs (`#minecraft:logs`), `16×` Iron Ingots (`#ingots/iron`), `32×` Basic Food (Bread / Baked Potato / Cooked Meat / Carrot)
- Upon successful negotiation:
  1. Consumes the currency & supplies; credits initial `100 UC` into the village's own `villageBalance`.
  2. Replaces the village Bell block with `mnera:cooperation_hub` (keeping it registered as village center / `MEETING` POI).
  3. Marks the village as **Partnered (`partnered = true`)** bound to the player (or their active Guild/Company account).
  4. All villagers within the village radius have vanilla emerald trading disabled (`PlayerInteractEvent.EntityInteract` cancels vanilla trade GUI and shows localized cooperation notice).
  5. Mayor stays as village representative (still re-elects if killed).
  6. Generates initial Lv 1 Sell Orders & Buy Orders immediately!

### 5.3 Cooperation Hub — 1000-Slot Dual-Zone Warehouse & Protection
- **Total Storage**: **1,000 slots** persisted in `TycoonSavedData` keyed by `villageId` (so even when unloaded, remote transactions via Comm Terminal read/write the exact same 1000-slot virtual inventory seamlessly!).
  - **Slots `0..499` (500 slots) — Inbound / Delivery Zone (交付收货区)**:
    - External pipes, hoppers, MHERA pulling/ejector upgrades, and TRERA Smart Ports/Cargo Stations **INSERT ONLY** into `0..499`.
    - Items delivered here satisfy **Village Buy Orders** (player selling to village) and **Village Expansion Material Orders**.
  - **Slots `500..999` (500 slots) — Outbound / Purchase Zone (采购发货区)**:
    - Items purchased from **Village Sell Orders** (village selling to player) are deposited here.
    - External pipes, hoppers, MHERA ejector upgrades, and TRERA Smart Ports/Cargo Stations **EXTRACT ONLY** from `500..999`.
  - **Zero Loop-Back Guarantee**: External extraction NEVER pulls back items piped into `0..499`; external insertion NEVER clogs `500..999`!
  - **GUI Pagination & Search**: 50 slots/page (`5 rows × 10 cols` or `5×9` + quick nav) × 10 pages per zone, with zone tabs (`交付区 0-499` / `采购区 500-999`), text search filter, and summary counters!
- **Cash Trade vs Bank Trade & Auto-Fulfill**:
  - At the Cooperation Hub GUI: player can pay/receive via **Physical Coins (Cash Slot)** OR **Linked Bank Account**.
  - Via Communication Terminal: settles via **Linked Bank Account** (Personal or Guild).
  - **Per-Order Auto-Fulfill (`autoFulfill`)**: When enabled on a Village Buy Order or Expansion Order, every 40 ticks (2 seconds) the Hub checks Inbound slots `0..499`; as soon as required items arrive (e.g. via pipes, TRERA drones, or TRERA boats), it automatically delivers them, pays the player/company bank account, updates village volume & macro economy, and marks the order fulfilled!
- **Chunk Loading & TRERA Synergy**:
  - Hub has **2 Upgrade Slots** accepting `trera:chunk_loading_upgrade` (if TRERA installed, keeps 3×3 chunks force-loaded so automated pipes/TRERA stations work unattended!) as well as `mhera:ejector_upgrade`, `mhera:advanced_ejector_upgrade`, `mhera:pulling_upgrade`, `mhera:advanced_pulling_upgrade`.
  - Even without chunk loading, all village timers (order refresh, economy ticks, terminal remote buys/sells) run **virtually** in Overworld `TycoonSavedData`!
- **Blast-Proof & Anti-Grief Protection**:
  - Block hardness `-1` (unbreakable by normal mining) & blast resistance `3,600,000` (immune to Creepers/TNT/Wither).
  - Only the village Owner (or Guild admin / OP permission level 2) can click **"打包迁移枢纽" (Relocate Hub)** or **"解除合作协议" (Terminate Partnership)** in the Hub Settings tab. Relocating returns a bound `mnera:cooperation_hub` item while keeping all 1000 slots & village accounts safely stored in `TycoonSavedData` (ZERO item explosion!).

---

## 6. Village Levels (1–10), Player Industrial Tier & Dynamic Demand

### 6.1 Village Level Progression (1 → 10)
Upgrade requires meeting ALL THREE thresholds: **Population**, **Cumulative Trade Volume (UC)**, and **Village Account Balance (UC)** (upgrading consumes a portion of `villageBalance` as a realistic municipal capital expenditure / deflationary money sink!):

| Level | Min Pop | Min Trade Volume (UC) | Min Village Balance (UC) | Upgrade Cost (Sink) | Active Sell Orders | Active Buy Orders | Order Qty Scale |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2 | 0 | 0 | 0 | 3 | 2 | 1.0× |
| 2 | 4 | 400 | 150 | 100 | 4 | 3 | 1.3× |
| 3 | 6 | 1,200 | 400 | 250 | 4 | 4 | 1.6× |
| 4 | 9 | 3,000 | 900 | 550 | 5 | 4 | 2.0× |
| 5 | 12 | 7,000 | 1,800 | 1,100 | 6 | 5 | 2.5× |
| 6 | 16 | 15,000 | 3,500 | 2,000 | 6 | 6 | 3.2× |
| 7 | 20 | 32,000 | 7,000 | 4,000 | 7 | 6 | 4.0× |
| 8 | 25 | 65,000 | 14,000 | 7,500 | 8 | 7 | 5.0× |
| 9 | 30 | 130,000 | 28,000 | 15,000 | 9 | 8 | 6.5× |
| 10 | 36 | 250,000 | 55,000 | 30,000 | 10 | 9 | 8.0× |

- **Refresh Cadence**: New demand cycle refreshes on a fixed weekly in-game calendar cadence of `168,000` ticks (7 Minecraft days on `dayTime`, configurable; legacy v1 configs carrying `12,000` auto-migrate to `168,000` under `configVersion = 2`). Completed orders do NOT trigger early restock or early municipal stipend payouts.

### 6.2 Player / Company Industrial Tier Detection (0 → 5)
Tracks the highest unlocked tier for each player (and max across members for a Company account), updating automatically via inventory/advancement/craft/block-place hooks:
- **When MHERA IS installed**:
  - `Tier 0` (Pre-Industrial): Vanilla wood/stone/iron
  - `Tier 1` (Basic Industrial / LV): Crafted/owned MHERA Generator, BatBox, Macerator, Extractor, Compressor, Electric Furnace, or Copper/Tin/Bronze plates
  - `Tier 2` (Medium Voltage / MV): CESU, Metal Former, Ore Washing Plant, Thermal Centrifuge, Blast Furnace, Steel/Refined Iron, Advanced Machine Casing
  - `Tier 3` (High Voltage / HV): MFE, Induction Furnace, Nuclear Reactor, Advanced Alloy, Carbon Plate, Energy Crystal
  - `Tier 4` (Extreme Voltage / EV): MFSU, Mass Fabricator, Scanner, Replicator, Lapotron Crystal, Uranium/MOX Fuel
  - `Tier 5` (Iridium / Endgame): UU-Matter, Iridium Shard, Iridium Ore, Iridium Reinforced Plate, Quantum Armor
- **When MHERA is NOT installed (Vanilla Progression)**:
  - `Tier 0`: Wood / Stone
  - `Tier 1`: Iron tools/armor / Smelting
  - `Tier 2`: Diamond tools / Enchanting Table / Brewing Stand
  - `Tier 3`: Nether access (Blaze Rod / Quartz / Netherite Scrap)
  - `Tier 4`: Full Netherite / Beacon / End access
  - `Tier 5`: Dragon Egg / Elytra / Heavy Core / Endgame
- **Tech Gating Rule** (`enforceIndustrialTierCap = true`, configurable):
  - Effective item tier unlocked for village orders = `min(villageTargetTier, max(0, playerIndustrialTier - 1))` for standard industrial goods (with exception that at `playerIndustrialTier >= 5` and `villageLevel == 10`, Endgame items like `mhera:iridium_shard` / Netherite Scrap unlock!). So village offerings stay naturally one step behind the player's current industrial peak, preventing trivial sequence-breaking!

### 6.3 Economic Goods Progression Curve (Lv 1 → Lv 10)
- **Lv 1–3 (Rural Frontier / Agrarian Village)**:
  - **Sells**: Textiles (Wool, Carpet, String), Monster Drops (Rotten Flesh, Bone, Spider Eye, Gunpowder, Slimeball), Leather, Sticks, Raw/Basic Agricultural Crops (in-season crops when season mod active!), Honey, Feathers, MHERA Sticky Resin & Hops (when MHERA loaded).
  - **Buys**: Raw/Processed Mining Products (Coal, Copper Ingot, Iron Ingot, Tin/Lead Ingots, Cobblestone/Stone, Charcoal, Flint, Torches).
- **Lv 4–6 (Developing Industrial Town)**:
  - **Sells**: Refined Textiles/Dyes, Paper, Books, Glass, Brick, Terracotta, Mid-tier Produce & Prepared Ingredients, Basic Enchanted Books (Lv I–II), Rubber / Coffee Beans (MHERA), Cooking Ingredients (FD / KC).
  - **Buys**: Refined Metals (Steel, Bronze, Gold, Redstone, Lapis, Quartz), Industrial Components (Pistons, Repeaters, MHERA Circuits/Cables/Plates/Batteries), Mid-tier Cooked Food.
- **Lv 7–10 (Prosperous Metropolis / High-Tech Hub)**:
  - **Buys**: **High-Quality Food & Fine Cuisine** (Golden Carrots, Steak, Pumpkin Pie, Cakes, **Farmer's Delight hearty meals**, **Kaleidoscope Cookery gourmet dishes**, MHERA Coffee/Beer), High-tier industrial supplies (Glowstone, Blaze Powder, Obsidian, Energy Crystals, Diamonds).
  - **Sells**: **Rare & Endgame Goods** — High-Level Enchanted Books (Mending, Efficiency V, Fortune III, Unbreaking III, Sharpness V, Protection IV), **Alloys** (`mhera:alloy`, `mhera:bronze_ingot`, `mhera:steel_ingot`, `mhera:carbon_plate`, or Vanilla Netherite Scrap / Amethyst / Prismarine / Echo Shards), Name Tags, Totem of Undying, Heart of the Sea, and at **Lv 10**: **Iridium Shards (`mhera:iridium_shard`)** (or Ancient Debris / Netherite Upgrade Template when MHERA absent!).
- **Biome Specialty Weighting (1.5× appearance & 0.9× sell price discount)**:
  - **Plains**: Wheat, Beef, Leather, Wool, Horses/Saddles, Onions/Cabbages
  - **Desert**: Sand, Sandstone, Terracotta, Glass, Cactus, Rabbit Hide, Gold/Lapis
  - **Savanna**: Acacia Wood, Copper, Melon, Leather, Red Dye, Chili/Tomatoes
  - **Taiga**: Spruce Wood, Sweet Berries, Fox/Wolf Pelt (Leather), Ferns, Iron/Coal, Potatoes/Mushrooms
  - **Snowy**: Snow/Packed Ice, Wool, Rabbit/Mutton, Beetroot, Spruce, Enchanting Books
  - **Jungle**: Cocoa Beans, Bamboo, Melon Slice, Jungle Logs, Sugar Cane
  - **Swamp**: Slimeball, Brown/Red Mushrooms, Clay Ball, Lily Pad

---

## 7. Macro-Economy Model (Inflation & Deflation Simulation)

Implemented cleanly in `com.tycooncraft.tyc2.core.MacroEconomyEngine`:

### 7.1 Tracked Macro Variables (Persisted in `TycoonSavedData`)
- `M` = **Total Active Money Supply**: `sum(all player/guild bank balances) + sum(all village balances) + netCashInCirculation` (incremented whenever Minting Machine mints coins; decremented when money sinks burn coins or coins are destroyed/spent on upgrades/expansion/upkeep).
- `Q` = **Rolling Goods Circulation Volume (EMA)**: Exponential moving average of base-value goods traded across all villages over recent cycles (with baseline floor `Q_base = 2000.0` so early game is stable).
- `M_base` = Reference baseline money supply (`2000.0`, scales gently with number of partnered villages: `2000.0 * max(1, partneredVillageCount)`).

### 7.2 Price Formula
For an item `i` at village `v` in season `s`:
$$P_{\text{final}}(i, v, s) = \text{round}\!\Big(P_{\text{base}}(i) \cdot I_{\text{CPI}} \cdot F_{\text{village}}(v) \cdot F_{\text{supply}}(i) \cdot F_{\text{season}}(i, s) \cdot F_{\text{biome}}(i, v)\Big)$$

1. **Global CPI (Inflation / Deflation Index)**:
   $$I_{\text{CPI}} = \text{clamp}\!\left(\left(\frac{M + 0.5\,M_{\text{base}}}{Q + 0.5\,M_{\text{base}}}\right)^{0.55},\; I_{\min},\; I_{\max}\right)$$
   - Over-minting coins (`M >> Q`) drives `I_CPI > 1.0` → **Inflation (通货膨胀)**! All prices rise.
   - Heavy trading + municipal upgrade/expansion/upkeep money sinks (`M < Q`) drives `I_CPI < 1.0` → **Deflation (通货紧缩)**! Purchasing power rises.
2. **Village Liquidity Factor** `F_village(v)`:
   - Target reserve `R_target(level) = 200 * level^1.8`.
   - `ratio = villageBalance / R_target`.
   - For **Village Buy Orders** (village paying player): wealthier villages pay up to `1.25×`, cash-strapped villages pay as low as `0.75×` (and cannot buy more than their `villageBalance` allows!).
   - For **Village Sell Orders** (player paying village): cash-strapped villages discount down to `0.80×` to raise cash; cash-rich villages hold firm up to `1.20×`.
3. **Per-Item Supply-Demand Elasticity** `F_supply(i)`:
   - Tracks `netFlow(i)` (positive when players heavily sell `i` into villages; negative when players heavily buy `i` from villages), decaying 10% back toward 0 every demand cycle (mean reversion).
   - `F_supply(i) = clamp(exp(-0.08 * netFlowNormalized(i)), 0.55, 1.85)`.
4. **Safety Guardrails** (Configurable):
   - Final combined price multiplier clamped to **`[0.25, 4.00]`** (min unit price = `1 UC`).
5. **Deflationary Money Sinks**:
   - Village Level-Up Capital Cost (burned from `villageBalance` into infrastructure).
   - Village Expansion Construction Cost (village pays builders/materials from `villageBalance`).
   - Periodic Municipal Upkeep (`2%` of excess village reserve per day above target reserve).
   - Player-Funded Village Aid Construction (cash `recordMoneySink` or bank `recordBankMoneySink` infrastructure burn; cancelling prior to block placement calls `refundMoneySink(amount, refundedToCash)` so global money supply `M` remains strictly conserved).
6. **Price History Ring Buffer**:
   - Records last **48 snapshots** of `(timestamp, cpiIndex, moneySupply, goodsCirculation)` for rendering an interactive **CPI & Inflation Trend Line Chart** in the Cooperation Hub & Comm Terminal GUI!

---

## 8. Seasonal Agriculture & Mod Integration Matrix

### 8.1 Soft Season Bridge (`SeasonAdapter`)
- Checks at runtime (reflection, zero compile dependency):
  1. **Serene Seasons (`sereneseasons`)**: `sereneseasons.api.season.SeasonHelper.getSeasonState(Level)` → `getSeason()` (`SPRING`, `SUMMER`, `AUTUMN`, `WINTER`) & `getSubSeason()`.
  2. **Ecliptic Seasons (`eclipticseasons`)**: `com.teamtea.eclipticseasons.api.EclipticSeasonsApi.getInstance().getSolarTerm(Level)` → `getSeason()` (`SPRING`, `SUMMER`, `AUTUMN`, `WINTER`).
  3. **Neither installed**: Returns `Season.NONE` (year-round neutral weights, no off-season restrictions).

### 8.2 Realistic Arbitrage Behavior (When Season Active)
- **In-Season (丰收季)**:
  - Agrarian villages (Lv 1–6) **Sell** at `0.75×` price and `2.0×` weight/quantity!
  - High-level villages (Lv 7–10) Buy at `0.90×` price (normal fresh demand).
- **Off-Season (淡季 / 反季, especially Winter)**:
  - Agrarian villages **DO NOT Sell** off-season crops (replaced by stored/winter goods: timber, wool, leather, meat, monster drops).
  - Instead, villages generate **Off-Season Buy Orders** for scarce off-season crops/preserved food at **`1.45× ~ 1.75×` premium price**, enabling authentic cross-season storage & arbitrage!

### 8.3 Verified Harvest Season Catalog (Data-Driven JSON + Runtime Registry Filter)
*(Any item whose mod is not installed is automatically skipped at load time!)*

| Mod | Item ID(s) | Harvest Seasons | Category / Level |
|---|---|---|---|
| **Vanilla** | `minecraft:wheat`, `minecraft:carrot`, `minecraft:potato` | Spring, Summer, Autumn | Staple Crop (Lv 1–6 sell / Lv 7–10 buy) |
| **Vanilla** | `minecraft:beetroot` | Spring, Autumn, Winter | Cold-Hardy Root (Lv 1–5) |
| **Vanilla** | `minecraft:pumpkin` | Autumn | Autumn Harvest (Lv 1–6) |
| **Vanilla** | `minecraft:melon_slice` | Summer | Summer Fruit (Lv 1–6) |
| **Vanilla** | `minecraft:sweet_berries` | Spring, Summer, Autumn | Berry (Lv 1–5) |
| **Vanilla** | `minecraft:apple` | Autumn | Orchard Fruit (Lv 1–6) |
| **Vanilla** | `minecraft:sugar_cane`, `minecraft:cocoa_beans`, `minecraft:bamboo` | Summer | Warm-Season Crop (Lv 2–6) |
| **Vanilla** | `minecraft:brown_mushroom`, `minecraft:red_mushroom`, `minecraft:kelp`, `minecraft:glow_berries` | All Seasons (Winter bonus) | Year-Round / Cave / Marine |
| **MHERA (`mhera`)** | `mhera:sticky_resin` | Spring, Summer, Autumn | Industrial Forestry (Lv 1–5) |
| **MHERA (`mhera`)** | `mhera:hops` | Summer, Autumn | MHERA Specialty Crop (Lv 3–7) |
| **MHERA (`mhera`)** | `mhera:coffee_beans` | Summer | MHERA Specialty Crop (Lv 4–8) |
| **MHERA (`mhera`)** | `mhera:terra_wart` | All Seasons | MHERA Rare Crop (Lv 6–9) |
| **Farmer's Delight (`farmersdelight`)** | `farmersdelight:onion` | Spring, Autumn | FD Crop (Lv 1–6) |
| **Farmer's Delight (`farmersdelight`)** | `farmersdelight:cabbage`, `farmersdelight:cabbage_leaf` | Spring, Autumn, Winter | FD Cold-Hardy Crop (Lv 1–6) |
| **Farmer's Delight (`farmersdelight`)** | `farmersdelight:tomato` | Summer, Autumn | FD Warm Crop (Lv 1–6) |
| **Farmer's Delight (`farmersdelight`)** | `farmersdelight:rice`, `farmersdelight:rice_panicle` | Summer, Autumn | FD Paddy Crop (Lv 2–6) |
| **Farmer's Delight (`farmersdelight`)** | `farmersdelight:beef_stew`, `farmersdelight:chicken_soup`, `farmersdelight:vegetable_soup`, `farmersdelight:fried_rice`, `farmersdelight:pasta_with_meatballs`, `farmersdelight:roast_chicken`, `farmersdelight:honey_glazed_ham`, `farmersdelight:shepherds_pie`, `farmersdelight:stuffed_pumpkin` | All Seasons (seasonal bonus matching ingredients) | FD Fine Meals (Lv 7–10 High-Tier Buy Orders!) |
| **Kaleidoscope Cookery (`kaleidoscope_cookery`)** | `kaleidoscope_cookery:lettuce` | Spring, Autumn | KC Crop (Lv 1–6) |
| **Kaleidoscope Cookery (`kaleidoscope_cookery`)** | `kaleidoscope_cookery:tomato` | Summer, Autumn | KC Crop (Lv 1–6) |
| **Kaleidoscope Cookery (`kaleidoscope_cookery`)** | `kaleidoscope_cookery:chili`, `kaleidoscope_cookery:red_chili`, `kaleidoscope_cookery:green_chili` | Summer, Autumn | KC Crop (Lv 2–6) |
| **Kaleidoscope Cookery (`kaleidoscope_cookery`)** | `kaleidoscope_cookery:rice`, `kaleidoscope_cookery:wild_rice` | Summer, Autumn | KC Paddy Crop (Lv 2–6) |
| **Kaleidoscope Cookery (`kaleidoscope_cookery`)** | `kaleidoscope_cookery:tea_leaf` | Spring, Summer | KC Specialty Crop (Lv 3–7) |
| **Kaleidoscope Cookery (`kaleidoscope_cookery`)** | `kaleidoscope_cookery:cooked_rice`, `kaleidoscope_cookery:egg_fried_rice`, `kaleidoscope_cookery:braised_beef`, `kaleidoscope_cookery:sweet_and_sour_ender_pearls`, `kaleidoscope_cookery:dongpo_pork`, `kaleidoscope_cookery:fondant_pie`, `kaleidoscope_cookery:spicy_chicken`, `kaleidoscope_cookery:tomato_scrambled_eggs` | All Seasons | KC Fine Meals (Lv 7–10 High-Tier Buy Orders!) |

*(Note: For Kaleidoscope Cookery / Farmer's Delight items whose exact ID varies across minor versions, the catalog loader checks `BuiltInRegistries.ITEM.containsKey(...)` gracefully and ALSO scans `#c:crops/*`, `#forge:crops/*`, `#c:foods/*` tags when present, plus users can add/edit any entry via datapack JSON or `config/mnera/`!)*

---

## 9. Village Expansion & Collision-Safe Gradual Builder

### 9.1 Expansion Desire Cycle
- Periodically (every `18,000` ticks / 15 min, or triggered faster when village grows & has surplus funds), a partnered village generates an **Expansion Desire Order** requesting construction materials scaled to project type:
  - **Road Paving & Streetlights**: Cobblestone / Stone Bricks / Sandstone / Gravel + Planks / Torches / Lanterns
  - **Crop Farm Plot**: Dirt / Logs / Planks / Fences / Seeds
  - **Livestock Ranch / Pen**: Logs / Planks / Fences / Hay Bales / Gates
  - **Small Cottage** (Lv 1+): Logs + Planks + Cobblestone/Sandstone + Glass + Wool (beds)
  - **Medium Residence** (Lv 3+): Stone Bricks + Logs + Planks + Glass + Bricks/Terracotta + Lanterns
  - **Multi-Story Townhouse / 2–3F Tower / Market Hall** (Lv 5+): Stone Bricks + Polished Stone/Deepslate + Logs + Planks + Glass Panes + Iron Bars + Lanterns
  - **TRERA Logistics Plaza / Waterfront Pier & Helipad** (Lv 3+ / riverside or Lv 4+): Stone Bricks + Planks + Smooth Stone Slabs + Iron Blocks/Fences + Lanterns — flat, cleanly accessible pad adjacent to water (for `trera:smart_port`) or open sky (for `trera:cargo_station`), marked in Village Record so subsequent buildings never block its water/airway clearance!

### 9.2 Strict Collision & Anti-Grief Safety Rules (`ExpansionPlanner`)
1. **Bounding-Box Registry**: Every built structure and road segment registers its 3D AABB in `VillageRecord.occupiedZones`. Initial village buildings (detected around beds/doors/workstations/roads on partnership) are ALSO registered as protected occupied zones!
2. **Player-Placed Block Protection**: Server tracks player-placed block positions (`PlayerPlacedBlockTracker` in `TycoonSavedData`) + runtime scan rejects ANY candidate footprint containing:
   - Any `BlockEntity` (chests, furnaces, machines, pipes, TRERA ports/stations, MHERA generators/cables, modded tech blocks!)
   - Any player-placed block
   - Any existing village structure block (planks, cobblestone, stone bricks, doors, beds, glass, fences, bells, hubs, path blocks, workstations, terracotta, sandstone bricks)
   - Deep water/lava (except specifically for waterfront pier pilings)
   - Extreme elevation deltas (`> 4` blocks across footprint)
3. **Natural-Only Clearance**: Only replaces whitelisted natural surface clutter (`air`, `grass`/`short_grass`, `tall_grass`, `fern`, `flowers`, `snow_layer`, `dead_bush`, `leaves`, `natural logs`) and lays neat foundation pillars down to solid ground on gentle slopes!
4. **Pause & No-Build Radius**: Hub GUI includes a **"暂停扩张" (Pause Expansion)** toggle and configurable **Min/Max expansion radius** (`10..72` blocks from Hub, growing outward in concentric rings!).
5. **Gradual Construction Ticker**: Places `2` blocks per `3` ticks layer-by-layer (bottom foundation → floor → walls → windows/doors/beds → roof → lights/decor) with block-place sounds and dust particles! Zero tick spikes!
6. **Natural Population Growth**: Completing a Residential Cottage / Medium Residence / Multi-Story Townhouse spawns `1 / 2 / 3` villagers (up to `min(40, level * 4 + 4)`) near the new house's beds (and an Iron Golem every 10 villagers if none nearby), enabling natural progression up to Lv 10!

### 9.3 7 Biome Architectural Templates (`BiomeStyle`)
All 7 biome styles (`PLAINS`, `DESERT`, `SAVANNA`, `TAIGA`, `SNOWY`, `JUNGLE`, `SWAMP`) share cross-version-safe block palettes (1.19.2–26.2) across both regular expansion and aid workplace blueprints:
- `PLAINS`: Oak logs/planks, cobblestone, stone bricks, white/yellow beds, stone-brick + path roads, oak lanterns
- `DESERT`: Cut/smooth sandstone, terracotta trim, birch/acacia doors, orange/cyan beds, sandstone slab roads, sandstone post lanterns
- `SAVANNA`: Acacia logs/planks, stripped acacia, red terracotta, cobblestone base, red/orange beds, dirt-path + acacia border roads
- `TAIGA`: Spruce logs/planks, mossy/cobblestone, campfires/lanterns, green/brown beds, coarse-dirt/cobble roads, spruce post lanterns
- `SNOWY`: Spruce logs/planks, packed ice/snow blocks/blue terracotta accents, stone bricks, blue/white beds, snow-brick/cobble roads, tall snow-capped lanterns
- `JUNGLE` (`jungle` / `bamboo` / `rainforest`): Jungle logs/planks/doors, mossy cobblestone, chiseled stone bricks, lime stained glass panes & lime beds, dirt-path + mossy cobble roads
- `SWAMP` (`swamp` / `mangrove` / `marsh` / `bog` / `bayou`): Dark oak logs/doors, spruce/oak planks, mossy cobblestone, mud bricks, purple stained glass panes & purple beds, coarse-dirt + mud-brick roads

### 9.4 Village Aid Construction & Low-Unemployment Bonus (`< 25%`)
- **Workplace Catalog (`WorkplaceCatalog` + `WorkplaceArchitectureCatalog`)**:
  - Covers all **13 vanilla villager professions** (`farmer`, `fisherman`, `shepherd`, `fletcher`, `butcher`, `leatherworker`, `mason`, `cartographer`, `toolsmith`, `weaponsmith`, `armorer`, `cleric`, `librarian`) with dedicated architectural prototypes × 2 sizes × 7 biome palettes.
  - **Dynamic Modded Professions**: At startup and reload, the platform adapter scans `VillagerProfession` + `PoiType` registries (skipping `none`/`nitwit` and configurable blacklist entries) and automatically registers modded professions using the `GENERIC` workshop prototype and their resolved workstation block (`enableModdedProfessions = true`, default `450 UC` small / `2.2×` large).
  - **Two Sizes (`WorkplaceSize`)**:
    - `SMALL` (`7×6×7`, 1 workstation, Lv 1+, base `250–600 UC`, `aidGatherTicksSmall = 3,600` base ticks).
    - `LARGE` (`9×7×9`, 3 workstations, Lv 3+, `2.2×` small base cost, `aidGatherTicksLarge = 6,000` base ticks).
  - All workplace blueprints contain **zero beds** (adding job sites without population inflation) and emit `SpawnMarker(MarkerKind.WORKSTATION, dx, dy, dz, professionId)`. `LevelTerrainProbe` explicitly protects workstation blocks (`blast_furnace`, `smoker`, `brewing_stand`, `cauldron`, `water_cauldron`, `bookshelf`, etc.) from future builds.
- **Entry Points & Pricing**:
  - Available on partnered villages via **Cooperation Hub** (Growth → Aid sub-tab; cash or bank), **Commerce Comm Terminal** (Growth → Aid view; bank + `100 EU`), or OP command `/mnera aid <profession> [small|large]`.
  - Live CPI quote: `max(1, round(baseCost * clamp(CPI, max(0.75, minPriceMultiplier), maxPriceMultiplier) * (1 + 0.75 * priorProfessionBuilds)))`, capped at `2` aided builds per profession per village (`MAX_AID_BUILDS_PER_PROFESSION = 2`) to prevent repeat demolish-and-rebuild workstation printing. Paid funds are burned as an infrastructure money sink (not added to `villageBalance`).
- **Decoupled Upstream / Shared Downstream Pipeline (`AidConstructionProject`)**:
  - Independent `aidQueue` (`maxQueuedAidProjects = 3`) never competes with `ExpansionDesire` and is **unaffected by `expansionPaused = true`**.
  - On loaded-chunk orders, `ExpansionPlanner.planBlueprint(..., /*ignorePause=*/true)` pre-validates terrain and pre-locks `reservedZone` in `occupiedZones` before charging UC or Terminal `100 EU` (rejecting cleanly with zero charge if no safe site exists).
  - **Virtual Background Gathering (`COLLECTING`)**: Runs every 20 ticks even while chunks are unloaded; accelerated by idle/unemployed adults via `v_gather = 1.0 + min(1.0, 0.125 * unemployedCount)` (`1.0×..2.0×`).
  - **Priority Launch & Cancellation**: Once gathered (`WAITING_SITE`), plans terrain via `planBlueprintIgnoringZone` and inserts ahead of queued regular expansion via `commitPlanWithPriority(..., true)` (`NO_SITE` retries every 400 ticks). Projects in `COLLECTING` / `WAITING_SITE` / `NO_SITE` can be cancelled for a **100% refund** (releasing `reservedZone` and reversing the money sink to conserve `M`); cancellation is rejected once `BUILDING` starts.
  - **Completion**: Removes the aid project, automatically assigns nearby unemployed adult non-nitwit non-mayor villagers to each `WORKSTATION` marker (`JOB_SITE` POI + `VillagerData`), and refreshes `VillageEmploymentStats` immediately.
- **Employment Stats & Low-Unemployment Bonus (`VillageEmploymentStats`)**:
  - Workforce denominator = living adult non-nitwit villagers within dynamic scan radius `max(48, maxExpansionRadius + 12)` (babies and nitwits excluded; living Mayor counts as employed public office; cached when unloaded).
  - When `workforceCount > 0` and `unemploymentRate < lowUnemploymentThreshold` (default `0.25`), smooth intensity `t = clamp((0.25 - r) / 0.25, 0, 1)` activates:
    - **Fixed Weekly Cadence**: Demand refresh interval stays pinned to the configured weekly calendar cycle (`demandRefreshTicks = 168,000` ticks / 7 Minecraft days); low unemployment does NOT shorten restock cadence.
    - **Trade Order Quantity**: `1.25×..1.50×` on standard goods and moderated `1.10×..1.20×` (`1 + 0.4 * (mult - 1)`) on rare `ENCHANTED_BOOK` / `ENDGAME` goods.
    - **Active Profession Weighting**: Active village professions gently boost matching `GoodsCategory` weights up to `1.4×` in `DemandGenerator` while strictly preserving village level and `IndustrialTier` caps.

---

## 10. Optional Mod Integrations & 3-Layer Extensibility

1. **MHERA (`mhera`)**: Soft `Ic2Bridge` + `TyC2MixinPlugin` (zero classloading when absent): EU `WorldPowerPort` adapter, cable connection mixin, `mhera:coin` canonical 1x unification, `metal_former_cutting` coin recipe filter, `PortableGear` charging mixin for Comm Terminal, MHERA wrench support on Mint/ATM, MHERA upgrades in Mint/Hub, MHERA metals/alloys/iridium & MHERA crops (`sticky_resin`, `hops`, `coffee_beans`, `terra_wart`).
2. **TRERA (`trera`)**: Soft registry/container integration: Hub 500/500 Inbound/Outbound aligns loop-free with TRERA Smart Port / Cargo Station 2-zone transfer; Hub accepts `trera:chunk_loading_upgrade` for unattended 3×3 chunk loading; Expansion builds dedicated TRERA-friendly waterfront piers & open-sky helipad plazas; Comm Terminal displays nearby linked TRERA station coordinates if detected!
3. **Serene Seasons (`sereneseasons`) & Ecliptic Seasons (`eclipticseasons`)**: Pure reflection soft adapters under `SeasonBridge`.
4. **Farmer's Delight (`farmersdelight`) & Kaleidoscope Cookery (`kaleidoscope_cookery`)**: Data-driven JSON goods & seasonal tables; items dynamically activated only when present in Minecraft's item registry.
5. **The One Probe (`theoneprobe`) & Jade (`jade`)**: Shared `JadeSoftBridge` providers showing Hub village name/level/funds/active orders/CPI, **unemployment rate, low-unemployment bonus readout, active aid construction status**, Mint power/progress, ATM status, and Mayor status + unemployment rate.
6. **JEI (`jei`)**: Soft plugin registering `mnera:minting` recipe category showing all active Minting Machine metal/alloy recipes.
7. **Common Tags**: Mint recipes & trade goods match `#c:ingots/*` (NeoForge 1.21.1+) and `#forge:ingots/*` (Forge 1.19.2) automatically supporting Create, Mekanism, Thermal, etc.
8. **3-Layer User/Modder Extensibility**:
   - **Layer 1 — Datapack JSON**: `data/<ns>/mnera/goods/*.json`, `data/<ns>/mnera/mint_recipes/*.json`, `data/<ns>/mnera/seasons/*.json`, `data/<ns>/mnera/levels/*.json`, and `data/<ns>/mnera/workplaces/*.json` (hot-reloaded on `/reload`!).
   - **Layer 2 — External Config Folder**: `config/mnera/general.json`, `config/mnera/custom_goods.json`, `config/mnera/custom_mint_recipes.json`, and `config/mnera/custom_workplaces.json` (auto-generated with clean examples on first launch!).
   - **Layer 3 — Public Java API**: `com.tycooncraft.tyc2.api.TyC2RegistryApi` allowing other mods to programmatically register trade goods, seasonal crops, minting recipes, **aided workplaces (`registerWorkplace` / `unregisterWorkplace` / `getRegisteredWorkplaces`)**, custom season providers, and industrial tier detectors!
9. **Commands**: `/mnera status` (includes village unemployment, bonus & aid queue), `/mnera cpi`, `/mnera season`, plus OP commands `/mnera reload`, `/mnera mint <amount>`, `/mnera expand [projectType]`, `/mnera aid <profession> [small|large]`, `/mnera levelup`, `/mnera refresh`, `/mnera bank give <amount>` (NeoForge) / `/mnera villages` (Forge).

---

## 11. Package Ownership & Swarm Architecture

| Package / Directory | Owner | Responsibility |
|---|---|---|
| `src/common/.../core/` & `.../api/` | `core-econ` / `core-aid` | Pure-Java 17 currency, mint registry, banking/guild manager, macro economy CPI/inflation/deflation + sink refund conservation, goods catalog, seasonal arbitrage, industrial tier model, JSON lite parser, public `TyC2RegistryApi` + JUnit tests |
| `src/common/.../village/` | `core-village` / `core-aid` | Pure-Java 17 `VillageRecord`, Mayor election state, 1000-slot dual-zone `CooperationHubStorage`, `DemandGenerator`, `ExpansionPlanner` & collision-safe builder, 7-biome architectural templates, 13+modded workplace blueprints, `AidConstructionProject` & `VillageEmploymentStats` + JUnit tests |
| `src/datagen/` & `assets/mnera/` | `art-datagen` / `assets-data` | Original 16×16 pixel art textures (9 coins, Mint, ATM, Hub, Terminal, Mayor badge, GUI panels), bilingual `zh_cn.json` / `en_us.json`, resource/model/recipe/datapack generator for all 4 targets |
| `src/neoforge/` | `neoforge-impl` | NeoForge 1.21.1 / 26.1.2 / 26.2 implementation, blocks, items, BEs, Menus, Screens, Mayor/Village/Aid manager, networking, SavedData, gradual builder, optional MHERA/TRERA/Seasons/TOP/Jade/JEI bridges, MixinPlugin |
| `forge-1.19.2/` | `forge-1192` | Forge 1.19.2 (Java 17) complete port sharing `src/common/` & `assets/` + `src/datagen/`, `reobfJar` |
| `src/qa/`, `forge-1.19.2/src/qa/`, `docs/DESIGN.md`, `README.md`, `README.en.md` | `qa-verifier` | 4-target compilation, `:checkCommonImports`, `:test:test`, static JAR audit, live dedicated-server integration tests (Standalone & MHERA+TRERA across all 4 targets), final bilingual docs |
