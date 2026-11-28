# Monetera (`mnera`) — Full Build, Live Dedicated Server & Client–Server Field Test Report (`v1.0.0-pre.4`)

**Date**: 2026-11-24  
**Version Tested**: `1.0.0-pre.4` (adds **Village Aid Construction & Low-Unemployment `<25%` Bonus**: 13 vanilla villager professions + dynamic modded profession discovery, Small `7×6×7` 1-slot & Large `9×7×9` 3-slot workplaces across **7 biome styles** including newly added `JUNGLE` and `SWAMP`, pure-background villager material gathering accelerated by unemployed workforce up to `2.0×`, upstream-independent 3-slot aid queue unaffected by Pause Expansion + downstream collision-safe `ExpansionPlanner` / `ConstructionJob` reuse with loaded pre-reservation & priority queue & cancel/refund `M` conservation, post-build auto job assignment, smooth `<25%` restock speed & trade quantity bonuses `+25%..+50%`, light profession-trade weighting `<= 1.4×`, Hub/Terminal Aid UI, Jade/TOP HUD unemployment/aid lines, `/mnera aid`, and 3-layer `workplaces/*.json` / `custom_workplaces.json` / `TyC2RegistryApi.registerWorkplace` extensibility)  
**Optional Integration Pairings Tested**:
- **Machinera (`mhera`)**: `1.0.5` (`.mhera-deps/mhera-{neoforge,forge}-<ver>-2.0.0.jar`)
- **Transitera (`trera`)**: `1.1.0` (`trera-{neoforge,forge}-<ver>-2.0.0.jar`)

---

## 1. Executive Summary & Verified Release Artifacts

Every production JAR across all **4 Minecraft targets** (`Forge 1.19.2`, `NeoForge 1.21.1`, `NeoForge 26.1.2`, `NeoForge 26.2`) was rebuilt fresh for `v1.0.0-pre.4` and verified through:
1. Core platform-neutral import gate (`:checkCommonImports`) and **39/39 JUnit 5 unit tests** (`:test:test`), including 7-biome palettes & blueprints, 13 vanilla + generic modded workplace blueprints (`7×6×7` & `9×7×9`), `WorkplaceCatalog` JSON/API/CPI pricing, `AidConstructionProject` labor-accelerated gathering & cancel/refund money conservation, `ExpansionPlanner` pre-reserved zone & aid priority queueing, `VillageEmploymentStats` exact denominator/numerator & `<25%` smooth bonus curves, and `DemandGenerator` boosted quantities & profession light weighting.
2. Automated static, bytecode & localization production JAR audit (`src/qa/audit_jars.py` + Forge `auditPackagedMneraJar`) — **4/4 PASSED**:
   - **381/381** `zh_cn`/`en_us` key and `%s` format-placeholder parity; **0** banned mechanic-advertising strings.
   - Zero QA/preview leaks, zero non-compat/mixin `com.industrialage` references, valid `mods.toml`/`neoforge.mods.toml`, `data/mnera/workplaces/vanilla_workplaces.json` (13 vanilla professions), all **35 textures**, **21 models**, **4 blockstates**, **14** MC 26.x `assets/mnera/items/*.json`, unconditional `primitive_mint_machine.json`, conditional `mint_machine_mhera.json` & `mint_machine_standalone.json`, zero stale `mint_machine.json`, and SRG reobfuscation verified on Forge 1.19.2.
3. Live headless Dedicated Server end-to-end QA across **BOTH Standalone (ONLY `mnera`, NO MHERA/TRERA) AND Enhanced (`mnera` + `mhera 1.0.5` + `trera 1.1.0`)** with full **Write + Cold-Restart Read** world persistence — **1,121/1,121 live server checks PASSED across all 4 targets, 0 failures, 0 log errors** (covering Aid Construction funding/CPI pricing/pre-reservation, cancel/refund `M` conservation, background villager gathering & priority build while `expansionPaused = true`, automatic villager job assignment dropping unemployment `<25%`, live restock acceleration & boosted order volume, Jade/TOP HUD unemployment/aid output, and cold-restart NBT persistence of `employmentStats` & queued aid projects).

### Final Shipped Production JARs (`v1.0.0-pre.4`)

| Target | Java | Artifact Path | Size (Bytes) | Classes | Assets (`tex / mod / bs`) | SHA-256 | Static & Lang Audit |
|---|---|---|---:|---:|---|---|---|
| **Forge 1.19.2** | 17 | `forge-1.19.2/build/libs/mnera-forge-1.19.2-1.0.0-pre.4.jar` | `730,710` | 208 (SRG) | `35 / 21 / 4` | `64391128b1edaab9df5a7d1b313ab6072898b1b909944d9482fae2be76f89a72` | **PASS** (`381/381` i18n) |
| **NeoForge 1.21.1** | 21 | `src/neoforge/1.21.1/build/libs/mnera-neoforge-1.21.1-1.0.0-pre.4.jar` | `678,703` | 186 | `35 / 21 / 4` | `5bc4249f4ebc44f6302fde859484eecc17907167f32d6d33993cf3bda44fc88e` | **PASS** (`381/381` i18n) |
| **NeoForge 26.1.2** | 25 | `src/neoforge/26.1.2/build/libs/mnera-neoforge-26.1.2-1.0.0-pre.4.jar` | `687,790` | 188 (`+14 items`) | `35 / 21 / 4` | `25a236f4654a675f6f73eff4ade0a42bfd86cfa9f55683ac17de7b97ac738ce2` | **PASS** (`381/381` i18n) |
| **NeoForge 26.2** | 25 | `src/neoforge/26.2/build/libs/mnera-neoforge-26.2-1.0.0-pre.4.jar` | `687,790` | 188 (`+14 items`) | `35 / 21 / 4` | `e13f0f4c9262835bc0fc88566810baa58e25bfe960e2a5ed25a2cff0d446912d` | **PASS** (`381/381` i18n) |

---

## 2. Full 4-Target Pass/Fail Verification Matrix (`v1.0.0-pre.4`)

| Target | Java | Unit / Static Gate | Dedicated Server: **Standalone** (`write` + cold `read`) | Dedicated Server: **Enhanced MHERA+TRERA** (`write` + cold `read`) | Log Errors |
|---|---|---|---|---|---|
| **Core (`src/common`)** | 17/21 | **39/39 JUnit 5 PASS** + `:checkCommonImports` PASS | — | — | **0** |
| **Forge 1.19.2** | 17 | **PASS** (`audit_jars.py` + `auditPackagedMneraJar`) | **195 + 20 = 215/215 PASS** | **246 + 21 = 267/267 PASS** | **0** |
| **NeoForge 1.21.1** | 21 | **PASS** (`audit_jars.py`) | **157 + 19 = 176/176 PASS** | **30 + 7 = 37/37 PASS** | **0** |
| **NeoForge 26.1.2** | 25 | **PASS** (`audit_jars.py`) | **157 + 19 = 176/176 PASS** | **30 + 7 = 37/37 PASS** | **0** |
| **NeoForge 26.2** | 25 | **PASS** (`audit_jars.py`) | **157 + 19 = 176/176 PASS** | **30 + 7 = 37/37 PASS** | **0** |

**Aggregate Live Automated Checks Verified (`v1.0.0-pre.4`)**:
- **Forge 1.19.2**: **`482 / 482`** Dedicated Server checks PASSED (`0` failures, `0` log errors)
- **NeoForge 1.21.1 / 26.1.2 / 26.2**: `213 × 3 =` **`639 / 639`** Dedicated Server checks PASSED (`0` failures, `0` log errors)
- **Core Engine**: **`39 / 39`** JUnit 5 unit tests PASSED (`0` failures)
- **Total Live Dedicated Server Checks**: **`1,121 / 1,121` PASSED** across all 4 targets (plus 9/9 graphical Client–Server sessions established in `v1.0.0-pre.3` baseline)

---

## 3. Verified Functional Coverage

### 3.1 Playtest Regression Suites (All Playtest Fixes Verified Live on All 4 Targets)

1. **Expansion Materials Display, Partial-Delivery Feedback, Terminal 100 EU Guard & Auto-Launch (`1.0.0-pre.2`)**:
   - **All 5+ Expansion Materials Rendered**: `CooperationHubScreen` Expansion tab (`05b`/`05d`) dynamically fits all 5+ required materials and hover tooltips with zero cutoff (`16/16`, `48/48`, `32/32`, `8/8` green + 5th item `White Wool`/`白色羊毛` `0/3` amber). `CommTerminalScreen` Growth tab (`06d`) renders all 5 materials across 2 crisp fitted lines cleanly above action buttons in both `zh_cn` and `en_us`.
   - **Actionable Partial & Missing-Materials Feedback + Terminal EU Guard**: Delivering 4 of 5 `SMALL_COTTAGE` materials via Hub reports `status.mnera.expansion.delivered_partial` with client-localized missing items (`minecraft:white_wool ×3`); clicking again with 0 new items reports `status.mnera.expansion.missing_materials`; triggering Comm Terminal `DELIVER_EXPANSION` when Inbound has 0 matching remaining materials returns `status.mnera.expansion.missing_materials` with **0 EU deducted** (`5000 -> 5000`).
   - **All-Delivered Auto-Launch Without `autoFulfill`**: Delivering the 5th material while village funds are below budget returns `status.mnera.expansion.low_funds` (and Terminal `DELIVER_EXPANSION` deducts **0 EU**), records all 5 materials delivered (`autoFulfill == false`), and once village balance is restored `>= budget`, the periodic village tick **automatically launches `SMALL_COTTAGE` construction** (`39–40` ticks) without requiring another manual click!
   - **Hardened Terrain & Forested-Site Probes**: NeoForge `LevelTerrainProbe` clears replaceable MC 1.21.5+/26.x natural ground clutter (`moss_carpet`, `sweet_berry_bush`, `leaf_litter`, `pink_petals`, `wildflowers`, `dry_grass`, `powder_snow`) while strictly protecting player-placed blocks & BlockEntities. Forge 1.19.2 `ServerTerrainProbe.getSurfaceY` skips natural (non-player-placed) `BlockTags.LOGS`/`BlockTags.LEAVES` to report true ground Y while preserving player-placed logs.
2. **Complete `zh_cn` / `en_us` Localization & Zero Mechanic-Advertising Copy**:
   - `src/qa/audit_jars.py` enforces **329/329** key & `%s` format parity across `zh_cn.json` and `en_us.json` and rejects any occurrence of internal/advertising phrases (`1000 格`, `1000格`, `千格`, `0-499`, `500-999`, `防爆`, `防破坏`, `blast-proof`, `1000-slot`, `1,000-slot`, `CL:3x3`).
   - Live `zh_cn` and `en_us` `1280×720` screenshots visually verified **100% localized UI** across all 6 screens, all 4 Hub tabs, all 4 Comm Terminal tabs, localized item names, localized Mayor names (`[村长] 农民` / `[村长] MetroHaven`), clean ATM transaction logs (`-50 取出` / `-50 Withdraw`), localized nearby TRERA stations (`附近物流站：货运基站` / `Cargo Station`), and natural in-world copy (`收货区`/`加载:开`/`物价走势`).
3. **Strict Nitwit Mayor Disqualification**:
   - Spawning **2 adult Nitwit villagers** next to a Bell elects **NO Mayor** (`mayor == null`, neither villager tagged `mnera_mayor`).
   - Pre-existing/legacy Nitwit tagged `mnera_mayor` + custom name + glowing alongside an eligible adult villager is immediately **disqualified and stripped** (`mnera_mayor`, `mnera_village_id`, custom name, glowing removed), and the eligible non-nitwit adult is elected Mayor.
4. **Mayor Negotiation Hold & Daytime Plaza Tether**:
   - Opening `MayorNegotiationMenu` sets `villager.getTradingPlayer() == player`, halts path navigation and horizontal velocity (`navDone=true, vel=(0,0,0)`), and turns the Mayor to face the negotiating player (`yawDiff=0.0`); closing the menu releases `tradingPlayer == null`. Verified BOTH in headless dedicated server tests AND over live TCP multiplayer (`mayorNegotiatingWithClient == true -> false`).
   - Displacing a non-negotiating Mayor >18 blocks from the Hub/Bell during daytime binds `MemoryModuleType.MEETING_POINT` to the Hub/Bell and triggers active return navigation toward the plaza.
5. **Buy-Order Delivery & Exact Diagnostic Error Keys**:
   - **Comm Terminal `SELL_ORDER` with 0 matching Inbound items** returns `status.mnera.terminal.insufficient_goods` (never misleading `village_low_funds`) and deducts **0 EU** (`5000 -> 5000`).
   - **Hub `SELL_ORDER` with `villageBalance < unitPrice`** returns `status.mnera.hub.village_low_funds_detail` and leaves the player's inventory **100% untouched** (`inboundCount == 0`).
   - **Hub `SELL_ORDER` with sufficient village funds** (including `#forge:ingots/copper` / `#c:ingots/copper` tag matching) fulfills directly from player inventory/Inbound, settles exact UC into the player's bank account, and returns `status.mnera.hub.order_fulfilled`.

### 3.2 Standalone Mode (`mnera` ONLY — Zero MHERA, Zero TRERA)

Verified identically on Forge `1.19.2`, NeoForge `1.21.1`, NeoForge `26.1.2`, and NeoForge `26.2`:
- **Clean Boot & Conditional Recipes**: Boots cleanly with ONLY `mnera` in `mods/`; `TyC2MixinPlugin` cleanly skips all 4 MHERA Mixins when `mhera` is absent; zero Mixin warnings or `NoClassDefFoundError`. `RecipeManager` loads `mnera:primitive_mint_machine` (unconditional 100% Vanilla) and `mnera:mint_machine_standalone` while cleanly skipping `mnera:mint_machine_mhera`.
- **Auto-Generated Configs & Datapacks**: `config/mnera/general.json`, `custom_goods.json`, and `custom_mint_recipes.json` auto-generated on first boot; datapack loader loads **24 mint recipes** (6 `primitiveAllowed=true` + 18 industrial-only), **141 goods**, **10 village levels**, and seasonal overlays; `/mnera status`, `/mnera cpi`, `/mnera season` execute cleanly.
- **Primitive Minting Machine (`mnera:primitive_mint_machine`)**:
  - Exposes **NO FE energy capability** (`feResult=-1`), burns standard furnace fuels (`minecraft:coal`), mints pure-Vanilla `1x–100x` (`mnera:coin_1` in `90` ticks, `mnera:coin_100` in `210` ticks — exact `1.5×` speed) with lit `ACTIVE` firebox state, and **blocks** `200x–1000x` industrial recipes (`blockedByIndustrialTier = true`).
- **Pure-Electric Industrial Minting Machine (`mnera:mint_machine`)**:
  - Rejects solid coal/redstone in Slot 3 (`canPlaceItem == false`), runs via Forge/NeoForge FE (`128 FE/t = 32 EU/t`) and FE battery discharge, mints full `1x–1000x`, updates `MacroEconomyEngine`, and concurrently charges `mnera:comm_terminal` in Slot 4 without starving mint progress.
- **Industrial ATM (`mnera:atm_machine`)**:
  - Personal deposits, Guild/Company creation + invite-code join, cross-account transfers with exact balance reconciliation, optimal greedy-denomination coin withdrawals, and standalone Vanilla coal terminal charging fallback.
- **Village Bell Discovery, Partnership, Sneak+Right-Click Binding & Dual-Zone Cooperation Hub**:
  - Odd-coordinate Bell discovery (`(21, surfaceY, 21)`), 1-villager negative vs 2+ villager positive election + Mayor death re-election, blast-proof/mining-protected `mnera:cooperation_hub`, vanilla emerald trading lock on normal villagers.
  - Authentic `ServerPlayerGameMode.useItemOn` Sneak+Right-Click with `mnera:comm_terminal` binds (`bound=1`, zero 20 EU drain) and unbinds (`bound=0`) cleanly even with offhand item.
  - Dual-zone pipe isolation (Inbound insert-only, Outbound extract-only), CPI inflation/deflation dynamics, Lv 1 → Lv 2 upgrade, collision-safe gradual bottom-up `SMALL_COTTAGE` construction (adjacent Chest BlockEntity & Cobblestone Road 100% unharmed), and full cold-restart world persistence.

### 3.3 Enhanced Mode (`mnera` + `mhera 1.0.5` + `trera 1.1.0`)

Verified identically across all 4 targets loading the exact packaged `ic2-*-2.0.0.jar` and `lc2-*-2.0.0.jar`:
- **Conditional MHERA Recipe Activation**: Loads `mnera:primitive_mint_machine` + `mnera:mint_machine_mhera` and skips `mnera:mint_machine_standalone`.
- **All 4 MHERA Soft-Compat Mixins Applied Cleanly**:
  1. `Ic2CableBlockMixin`: `mhera:insulated_copper_cable` does **NOT** connect to `mnera:primitive_mint_machine`, and **DOES** visually connect and deliver real EU to `mnera:mint_machine` and `mnera:atm_machine`.
  2. `Ic2PortableGearMixin`: `mnera:comm_terminal` charges live inside real `mhera:batbox`.
  3. `Ic2ProcessingRecipesMixin` & `Ic2WorkshopRecipesMixin`: disable MHERA `metal_former_cutting` `mhera:iron_casing -> mhera:coin` while preserving all non-coin Metal Former recipes (`cuttingCount = 4`).
- **Two-Tier Minting Under MHERA + Battery Discharge**:
  - `mnera:primitive_mint_machine` mints canonical `mhera:coin x2` for Vanilla Copper while blocking MHERA Alloy/Tin/Iridium.
  - Pure-electric `mnera:mint_machine` discharges **`mhera:single_use_battery`** (`1,200 EU` → `900.0 EU` left after `300 EU` `mhera:tin_ingot -> 2x mhera:coin`) and `mhera:re_battery`, and mints MHERA Endgame `mhera:iridium + mhera:alloy -> mnera:coin_1000 x1`.
  - ATM accepts `mhera:coin` and `mnera:coin_1` 1:1; all 6 live MHERA goods (`mhera:resin`, `hops`, `coffee_beans`, `tin_ingot`, `alloy`, `iridium_shard`) active in catalog.
- **TRERA Logistics & Chunk Loading Integration**: `trera:chunk_loading_upgrade` in Hub activates 3×3 chunk loading (re-armed cleanly across cold JVM restart); nearby `trera:cargo_station` / `trera:smart_port` detected and localized client-side (`货运基站` / `Cargo Station`).

---

## 4. Real-World Bugs Caught & Verified Fixed During Live QA & Playtesting

Every defect below was uncovered by static audit, live dedicated-server tests, `1280×720` graphical Client–Server visual review, or real-world user playtesting, fixed by the owning engineer, rebuilt into all affected JARs, and re-verified to 100% GREEN:

1. **Preview PNG Leaked Into NeoForge Production JARs** *(Static Audit)*: Fixed exclude pattern `'preview/**'` → `'**/preview/**'` in `src/neoforge/platform.gradle`.
2. **Village Bell Scanner Skipped 75% of Odd-Coordinate Bells** *(Live Server QA)*: Replaced `dx += 2, dz += 2` stride with loaded-chunk `BellBlockEntity` scan + 1-block fallback; locked with odd-coord `(21, y, 21)` regression check.
3. **Minting Machine EU Starvation When `comm_terminal` Occupies Slot 4** *(Live Server QA)*: Reordered `tickMinting()` before `tickTerminalCharging()` and restricted terminal charging to surplus EU.
4. **MHERA 1.0.5 Item ID Mismatches** *(Live Enhanced Server QA)*: Aligned `GoodsCatalog`, `IndustrialTier`, and datagen to MHERA 1.0.5 IDs (`mhera:resin`, `mhera:machine`, `mhera:advanced_machine`, `mhera:centrifuge`, `mhera:uranium_fuel_rod`, `mhera:iridium`, `mhera:iridium_neutron_reflector`).
5. **Depopulation Chat Spam Every 4 Seconds** *(Live Client–Server Screenshots)*: Transition-gated `PAUSED_DEPOPULATED` broadcast with `!wasPaused`.
6. **BLOCKER — `CommTerminalItem` Sneak+Right-Click Could Not Bind to `CooperationHubBlock`** *(Live Client–Server QA)*: Fixed via `CommTerminalItem.onItemUseFirst(ItemStack, UseOnContext)` on all 4 targets; locked with authentic `ServerPlayerGameMode.useItemOn` bind/unbind tests.
7. **BLOCKER — `mhera:single_use_battery` Discharged `0.0 EU` in Mint/ATM** *(Live Enhanced Server QA)*: Fixed by passing full buffer headroom `maxCap - storedEu` to MHERA's 4-arg `PortableGear`/`ForgeGear.discharge(...)` while keeping `maxInputEuPerTick` for FE discharge.
8. **Live `zh_cn` & `en_us` Visual / i18n Polish Across All 4 Targets** *(1280×720 Bilingual Visual Audit)*:
   - **NeoForge `CommTerminalScreen` header overlap**: Reduced `balLine` `maxWidth` from `156 → 114` so `村库资金：1800 UC | 余额:850` / `Funds: 1800 UC | Bal:850` cleanly clears `[解绑]`/`[Unbind]`.
   - **NeoForge & Forge `Lc2SoftBridge` station labels**: Changed server wire format to locale-independent `blockId|x,y,z` and localized client-side (`货运基站` / `Cargo Station`).
   - **Forge 1.19.2 ATM tx logs, Mayor nametag & Hub Expansion/Status LCD**: Suppressed internal `"ATM*"` counterparty (`-50 取出` / `-50 Withdraw`), lowered `CompactButton` `minScale` so `Account` renders unclipped, localized Mayor profession nametags (`[村长] 农民`), and formatted Hub Expansion materials & Status LCD cleanly across 2 lines without clipping.
9. **5th Expansion Material Hidden, Missing Actionable Feedback, Terminal 100 EU Drain & `autoFulfill=false` Auto-Launch** *(Real-World `26.1.2-NeoForge` Playtest → Fixed & Verified Across All 4 Targets in `1.0.0-pre.2`)*:
   - Removed the `x > 195` cutoff in NeoForge `CooperationHubScreen` (icons + tooltips) and added 2-line localized material lists to `CommTerminalScreen` Growth tab on all 4 targets.
   - Added `status.mnera.expansion.delivered_partial` and `status.mnera.expansion.missing_materials` with client-translatable item lists, guarded Comm Terminal `100 EU` against no-op missing-material / low-funds clicks, enabled automatic construction launch once all materials are delivered even with `autoFulfill=false`, and hardened NeoForge `LevelTerrainProbe` & Forge `ServerTerrainProbe` for MC 26.x ground clutter and forested sites.

---

## 5. Reproducible Verification Commands

```bash
# 1. Core import check & 39 JUnit 5 unit tests
./gradlew :checkCommonImports :test:test --rerun-tasks

# 2. Build all 4 production JARs
./gradlew :neoforge:1.21.1:jar :neoforge:26.1.2:jar :neoforge:26.2:jar
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 build reobfJar auditPackagedMneraJar

# 3. Static, bytecode & zh_cn/en_us lang audit of all 4 production JARs (381/381 keys)
python3 src/qa/audit_jars.py

# 4. Live Dedicated Server QA (Standalone + Enhanced, write + cold-restart read)
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:1.21.1:tyc2QaAll
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:26.1.2:tyc2QaAll
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:26.2:tyc2QaAll
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2QaStandalone runTyc2QaEnhanced

# 5. Live Graphical Client (1280x720) <-> Dedicated Server (TCP) Joint Tests (zh_cn + en_us)
for VER in 1.21.1 26.1.2 26.2; do
  ./gradlew --init-script src/qa/neoforge-init.gradle ":neoforge:${VER}:prepareClientServerPlay"
  python3 src/qa/run_client_server_live.py --mc-version "$VER" --profile standalone --lang zh_cn
  python3 src/qa/run_client_server_live.py --mc-version "$VER" --profile enhanced --lang en_us
done
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=standalone -Ptyc2QaLang=zh_cn
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=enhanced -Ptyc2QaLang=zh_cn
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=enhanced -Ptyc2QaLang=en_us
```
