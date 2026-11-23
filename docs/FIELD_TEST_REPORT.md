# TycoonCraft²: The New Era (`tyc2`) — Full Build, Live Dedicated Server & Client–Server Field Test Report (`v1.0.0-pre.2`)

**Date**: 2026-11-23  
**Version Tested**: `1.0.0-pre.2` (final release-candidate artifacts including `tyc2:primitive_mint_machine` vs pure-electric `tyc2:mint_machine`, complete bilingual `zh_cn`/`en_us` localization & anti-advertising polish, Nitwit Mayor disqualification, Mayor negotiation hold & daytime plaza tether, and exact buy-order / expansion error diagnostics)  
**Optional Integration Pairings Tested**:
- **IndustrialCraft²: The New Era (`ic2`)**: `1.0.5` (`.ic2-deps/ic2-{neoforge,forge}-<ver>-1.0.5.jar`)
- **LogisticsCraft²: The New Era (`lc2`)**: `1.1.0` (`lc2-{neoforge,forge}-<ver>-1.1.0.jar`)

---

## 1. Executive Summary & Verified Release Artifacts

Every production JAR across all **4 Minecraft targets** (`Forge 1.19.2`, `NeoForge 1.21.1`, `NeoForge 26.1.2`, `NeoForge 26.2`) was rebuilt fresh after all playtest fixes and visual/i18n polish passes and verified through:
1. Core platform-neutral import gate (`:checkCommonImports`) and **33/33 JUnit 5 unit tests** (`:test:test`), including Nitwit disqualification, buy-order missing-goods vs low-funds discrimination, and item-tag matching.
2. Automated static, bytecode & localization production JAR audit (`src/qa/audit_jars.py` + Forge `auditPackagedTyc2Jar`) — **4/4 PASSED**:
   - **327/327** `zh_cn`/`en_us` key and `%s` format-placeholder parity; **0** banned mechanic-advertising strings (`1000 格`, `1000格`, `千格`, `0-499`, `500-999`, `防爆`, `防破坏`, `blast-proof`, `1000-slot`, `1,000-slot`, `CL:3x3`).
   - Zero QA/preview leaks, zero non-compat/mixin `com.industrialage` references, valid `mods.toml`/`neoforge.mods.toml`, all **35 textures**, **21 models**, **4 blockstates**, **14** MC 26.x `assets/tyc2/items/*.json`, unconditional `primitive_mint_machine.json`, conditional `mint_machine_ic2.json` & `mint_machine_standalone.json`, zero stale `mint_machine.json`, and SRG reobfuscation verified on Forge 1.19.2.
3. Live headless Dedicated Server end-to-end QA across **BOTH Standalone (ONLY `tyc2`, NO IC2/LC2) AND Enhanced (`tyc2` + `ic2 1.0.5` + `lc2 1.1.0`)** with full **Write + Cold-Restart Read** world persistence — **892/892 live server checks PASSED across all 4 targets, 0 errors**.
4. Live **Graphical Client (`1280×720`) ↔ Dedicated Server over real TCP** joint tests in **BOTH `zh_cn` and `en_us`** across Standalone and Enhanced modes on all 4 targets — **10/10 live sessions PASSED** (`35/35` sprites/textures verified, all **6 custom GUIs** + all **4 Hub tabs** + all **4 Comm Terminal tabs** + live 48-point CPI chart + live TCP Mayor negotiation hold/release verified, visually audited screenshots with 100% Chinese translation in `zh_cn`, zero advertising copy, zero text clipping/overlap, clean `client=0, server=0` exits, 0 log errors).

### Final Shipped Production JARs (`v1.0.0-pre.2`)

| Target | Java | Artifact Path | Size (Bytes) | Classes | Assets (`tex / mod / bs`) | SHA-256 | Static & Lang Audit |
|---|---|---|---:|---:|---|---|---|
| **Forge 1.19.2** | 17 | `forge-1.19.2/build/libs/tyc2-forge-1.19.2-1.0.0-pre.2.jar` | `638,965` | 192 (SRG) | `35 / 21 / 4` | `e99732cdd58b9d7603c3eafecc915d142ce459f1261ffb03879e81ac0e6005c5` | **PASS** (`327/327` i18n) |
| **NeoForge 1.21.1** | 21 | `src/neoforge/1.21.1/build/libs/tyc2-neoforge-1.21.1-1.0.0-pre.2.jar` | `596,326` | 172 | `35 / 21 / 4` | `2e2c3677a28e50acce3d769a9dccd497d75ac1ba5580d2bb232202945aa0ff2d` | **PASS** (`327/327` i18n) |
| **NeoForge 26.1.2** | 25 | `src/neoforge/26.1.2/build/libs/tyc2-neoforge-26.1.2-1.0.0-pre.2.jar` | `605,012` | 174 (`+14 items`) | `35 / 21 / 4` | `329b19c4c980b202cde69e6baeffc52c161ba430c7c396121c0922589a7083d6` | **PASS** (`327/327` i18n) |
| **NeoForge 26.2** | 25 | `src/neoforge/26.2/build/libs/tyc2-neoforge-26.2-1.0.0-pre.2.jar` | `605,012` | 174 (`+14 items`) | `35 / 21 / 4` | `335b4f0cc94512ae7cabb30cac0d36344fe8d11b1b1cc9672dde40c05bbf9e07` | **PASS** (`327/327` i18n) |

---

## 2. Full 4-Target Pass/Fail Verification Matrix

| Target | Java | Unit / Static Gate | Dedicated Server: **Standalone** (`write` + cold `read`) | Dedicated Server: **Enhanced IC2+LC2** (`write` + cold `read`) | Live Graphical Client (`1280×720`) ↔ Server (TCP): **`zh_cn` & `en_us`** | Log Errors |
|---|---|---|---|---|---|---|
| **Core (`src/common`)** | 17/21 | **33/33 JUnit 5 PASS** + `:checkCommonImports` PASS | — | — | — | **0** |
| **Forge 1.19.2** | 17 | **PASS** (`audit_jars.py` + `auditPackagedTyc2Jar`) | **150 + 18 = 168/168 PASS** (`pid=99307 / 99525`) | **201 + 19 = 220/220 PASS** (`pid=99570 / 99823`) | **3 Runs / 270/270 PASS** (Standalone `zh_cn` `:60560` `88/88`; Enhanced `zh_cn` `:60015` `91/91`; Enhanced `en_us` `:60293` `91/91`; 42 PNGs, live TCP Mayor hold/release, `rc=0/0`) | **0** |
| **NeoForge 1.21.1** | 21 | **PASS** (`audit_jars.py`) | **115 + 16 = 131/131 PASS** (`660t / 5t`, `pid=97558 / 97616`) | **30 + 7 = 37/37 PASS** (`242t / 10t`, re-verified `pid=2667`) | **3 Runs PASS** (Standalone `zh_cn` `:58082`; Enhanced `en_us` `:58823`; Enhanced `zh_cn` `:61551` post-LC2 i18n; `35/35` sprites, 6 GUIs + all 4 Hub/Terminal tabs, live TCP Mayor hold/release, 42 PNGs, `rc=0/0`) | **0** |
| **NeoForge 26.1.2** | 25 | **PASS** (`audit_jars.py`) | **115 + 16 = 131/131 PASS** (`660t / 5t`, `pid=97573 / 97624`) | **30 + 7 = 37/37 PASS** (`293t / 10t`, `pid=97474 / 97504`) | **2 Runs PASS** (Standalone `zh_cn` `:58737`; Enhanced `en_us` `:58968`; `35/35` sprites, 6 GUIs + all 4 Hub/Terminal tabs, live TCP Mayor hold/release, 28 PNGs, `rc=0/0`) | **0** |
| **NeoForge 26.2** | 25 | **PASS** (`audit_jars.py`) | **115 + 16 = 131/131 PASS** (`660t / 5t`, `pid=97500 / 97568`) | **30 + 7 = 37/37 PASS** (`373t / 10t`, `pid=97381 / 97463`) | **2 Runs PASS** (Standalone `zh_cn` `:58717`; Enhanced `en_us` `:58921`; `35/35` sprites, 6 GUIs + all 4 Hub/Terminal tabs, live TCP Mayor hold/release, 28 PNGs, `rc=0/0`) | **0** |

**Aggregate Live Automated Checks Verified**:
- **Forge 1.19.2**: `388` Dedicated Server + `270` Live Client–Server = **`658 / 658` live checks PASSED** (`0` failures, `0` log errors)
- **NeoForge 1.21.1 / 26.1.2 / 26.2**: `168 × 3 = 504 / 504` Dedicated Server checks PASSED + **`7 / 7` live graphical Client–Server sessions PASSED** (`0` failures, `0` log errors)
- **Core Engine**: **`33 / 33` JUnit 5 unit tests PASSED** (`0` failures)

---

## 3. Verified Functional Coverage

### 3.1 Playtest Regression Suite (All 5 User Playtest Fixes Verified Live on All 4 Targets)

1. **Complete `zh_cn` / `en_us` Localization & Zero Mechanic-Advertising Copy**:
   - `src/qa/audit_jars.py` enforces **327/327** key & `%s` format parity across `zh_cn.json` and `en_us.json` and rejects any occurrence of internal/advertising phrases (`1000 格`, `1000格`, `千格`, `0-499`, `500-999`, `防爆`, `防破坏`, `blast-proof`, `1000-slot`, `1,000-slot`, `CL:3x3`).
   - Live `zh_cn` and `en_us` `1280×720` screenshots visually verified **100% localized UI** across all 6 screens, all 4 Hub tabs, all 4 Comm Terminal tabs, localized item names (`铁锭`/`煤炭`/`黏液球`/`胡萝卜`/`鞍`), localized Mayor names (`[村长] 农民` / `[村长] MetroHaven`), clean ATM transaction logs (`-50 取出` / `-50 Withdraw`), localized nearby LC2 stations (`附近物流站：货运基站 (24,0,16)` / `Cargo Station`), and natural in-world copy (`收货区`/`加载:开`/`物价走势`).
2. **Strict Nitwit Mayor Disqualification**:
   - Spawning **2 adult Nitwit villagers** next to a Bell elects **NO Mayor** (`mayor == null`, neither villager tagged `tyc2_mayor`).
   - Pre-existing/legacy Nitwit tagged `tyc2_mayor` + custom name + glowing alongside an eligible adult villager is immediately **disqualified and stripped** (`tyc2_mayor`, `tyc2_village_id`, custom name, glowing removed), and the eligible non-nitwit adult is elected Mayor.
3. **Mayor Negotiation Hold & Daytime Plaza Tether**:
   - Opening `MayorNegotiationMenu` sets `villager.getTradingPlayer() == player`, halts path navigation and horizontal velocity (`navDone=true, vel=(0,0,0)`), and turns the Mayor to face the negotiating player (`yawDiff=0.0`); closing the menu releases `tradingPlayer == null`. Verified BOTH in headless dedicated server tests AND over live TCP multiplayer (`mayorNegotiatingWithClient == true -> false`).
   - Displacing a non-negotiating Mayor >18 blocks from the Hub/Bell during daytime binds `MemoryModuleType.MEETING_POINT` to the Hub/Bell and triggers active return navigation toward the plaza.
4. **Buy-Order Delivery & Exact Diagnostic Error Keys**:
   - **Comm Terminal `SELL_ORDER` with 0 matching Inbound items** (even when village funds are ample) returns `status.tyc2.terminal.insufficient_goods` (never misleading `village_low_funds`) and deducts **0 EU** (`5000 -> 5000`).
   - **Hub `SELL_ORDER` with `villageBalance < unitPrice`** returns `status.tyc2.hub.village_low_funds_detail` and leaves the player's inventory **100% untouched** (`inboundCount == 0`).
   - **Hub `SELL_ORDER` with sufficient village funds** (including `#forge:ingots/copper` / `#c:ingots/copper` tag matching) fulfills directly from player inventory/Inbound, settles exact IC into the player's bank account, and returns `status.tyc2.hub.order_fulfilled`.
   - **`DELIVER_EXPANSION` with insufficient village budget** returns `status.tyc2.expansion.low_funds`.

### 3.2 Standalone Mode (`tyc2` ONLY — Zero IC2, Zero LC2)

Verified identically on Forge `1.19.2`, NeoForge `1.21.1`, NeoForge `26.1.2`, and NeoForge `26.2`:
- **Clean Boot & Conditional Recipes**: Boots cleanly with ONLY `tyc2` in `mods/`; `TyC2MixinPlugin` cleanly skips all 4 IC2 Mixins when `ic2` is absent; zero Mixin warnings or `NoClassDefFoundError`. `RecipeManager` loads `tyc2:primitive_mint_machine` (unconditional 100% Vanilla) and `tyc2:mint_machine_standalone` while cleanly skipping `tyc2:mint_machine_ic2`.
- **Auto-Generated Configs & Datapacks**: `config/tyc2/general.json`, `custom_goods.json`, and `custom_mint_recipes.json` auto-generated on first boot; datapack loader loads **24 mint recipes** (6 `primitiveAllowed=true` + 18 industrial-only), **141 goods**, **10 village levels**, and seasonal overlays; `/tyc2 status`, `/tyc2 cpi`, `/tyc2 season` execute cleanly.
- **Primitive Minting Machine (`tyc2:primitive_mint_machine`)**:
  - Exposes **NO FE energy capability** (`feResult=-1`), burns standard furnace fuels (`minecraft:coal`), mints pure-Vanilla `1x–100x` (`tyc2:coin_1` in `90` ticks, `tyc2:coin_100` in `210` ticks — exact `1.5×` speed) with lit `ACTIVE` firebox state, and **blocks** `200x–1000x` industrial recipes (`blockedByIndustrialTier = true`).
- **Pure-Electric Industrial Minting Machine (`tyc2:mint_machine`)**:
  - Rejects solid coal/redstone in Slot 3 (`canPlaceItem == false`), runs via Forge/NeoForge FE (`128 FE/t = 32 EU/t`) and FE battery discharge, mints full `1x–1000x`, updates `MacroEconomyEngine`, and concurrently charges `tyc2:comm_terminal` in Slot 4 without starving mint progress.
- **Industrial ATM (`tyc2:atm_machine`)**:
  - Personal deposits, Guild/Company creation + invite-code join, cross-account transfers with exact balance reconciliation, optimal greedy-denomination coin withdrawals, and standalone Vanilla coal terminal charging fallback.
- **Village Bell Discovery, Partnership, Sneak+Right-Click Binding & Dual-Zone Cooperation Hub**:
  - Odd-coordinate Bell discovery (`(21, surfaceY, 21)`), 1-villager negative vs 2+ villager positive election + Mayor death re-election, blast-proof/mining-protected `tyc2:cooperation_hub`, vanilla emerald trading lock on normal villagers.
  - Authentic `ServerPlayerGameMode.useItemOn` Sneak+Right-Click with `tyc2:comm_terminal` binds (`bound=1`, zero 20 EU drain) and unbinds (`bound=0`) cleanly even with offhand item.
  - Dual-zone pipe isolation (Inbound insert-only, Outbound extract-only), CPI inflation/deflation dynamics, Lv 1 → Lv 2 upgrade, collision-safe gradual bottom-up `SMALL_COTTAGE` construction (adjacent Chest BlockEntity & Cobblestone Road 100% unharmed), and full cold-restart world persistence.

### 3.3 Enhanced Mode (`tyc2` + `ic2 1.0.5` + `lc2 1.1.0`)

Verified identically across all 4 targets loading the exact packaged `ic2-*-1.0.5.jar` and `lc2-*-1.1.0.jar`:
- **Conditional IC2 Recipe Activation**: Loads `tyc2:primitive_mint_machine` + `tyc2:mint_machine_ic2` and skips `tyc2:mint_machine_standalone`.
- **All 4 IC2 Soft-Compat Mixins Applied Cleanly**:
  1. `Ic2CableBlockMixin`: `ic2:insulated_copper_cable` does **NOT** connect to `tyc2:primitive_mint_machine`, and **DOES** visually connect and deliver real EU to `tyc2:mint_machine` and `tyc2:atm_machine`.
  2. `Ic2PortableGearMixin`: `tyc2:comm_terminal` charges live inside real `ic2:batbox`.
  3. `Ic2ProcessingRecipesMixin` & `Ic2WorkshopRecipesMixin`: disable IC2 `metal_former_cutting` `ic2:iron_casing -> ic2:coin` while preserving all non-coin Metal Former recipes (`cuttingCount = 4`).
- **Two-Tier Minting Under IC2 + Battery Discharge**:
  - `tyc2:primitive_mint_machine` mints canonical `ic2:coin x2` for Vanilla Copper while blocking IC2 Alloy/Tin/Iridium.
  - Pure-electric `tyc2:mint_machine` discharges **`ic2:single_use_battery`** (`1,200 EU` → `900.0 EU` left after `300 EU` `ic2:tin_ingot -> 2x ic2:coin`) and `ic2:re_battery`, and mints IC2 Endgame `ic2:iridium + ic2:alloy -> tyc2:coin_1000 x1`.
  - ATM accepts `ic2:coin` and `tyc2:coin_1` 1:1; all 6 live IC2 goods (`ic2:resin`, `hops`, `coffee_beans`, `tin_ingot`, `alloy`, `iridium_shard`) active in catalog.
- **LC2 Logistics & Chunk Loading Integration**: `lc2:chunk_loading_upgrade` in Hub activates 3×3 chunk loading (re-armed cleanly across cold JVM restart); nearby `lc2:cargo_station` / `lc2:smart_port` detected and localized client-side (`货运基站` / `Cargo Station`).

---

## 4. Real-World Bugs Caught & Verified Fixed During Live QA

Every defect below was uncovered by static audit, live dedicated-server tests, or `1280×720` `zh_cn`/`en_us` graphical Client–Server visual review, fixed by the owning engineer, rebuilt into all affected JARs, and re-verified to 100% GREEN:

1. **Preview PNG Leaked Into NeoForge Production JARs** *(Static Audit)*: Fixed exclude pattern `'preview/**'` → `'**/preview/**'` in `src/neoforge/platform.gradle`.
2. **Village Bell Scanner Skipped 75% of Odd-Coordinate Bells** *(Live Server QA)*: Replaced `dx += 2, dz += 2` stride with loaded-chunk `BellBlockEntity` scan + 1-block fallback; locked with odd-coord `(21, y, 21)` regression check.
3. **Minting Machine EU Starvation When `comm_terminal` Occupies Slot 4** *(Live Server QA)*: Reordered `tickMinting()` before `tickTerminalCharging()` and restricted terminal charging to surplus EU.
4. **IC2 1.0.5 Item ID Mismatches** *(Live Enhanced Server QA)*: Aligned `GoodsCatalog`, `IndustrialTier`, and datagen to IC2 1.0.5 IDs (`ic2:resin`, `ic2:machine`, `ic2:advanced_machine`, `ic2:centrifuge`, `ic2:uranium_fuel_rod`, `ic2:iridium`, `ic2:iridium_neutron_reflector`).
5. **Depopulation Chat Spam Every 4 Seconds** *(Live Client–Server Screenshots)*: Transition-gated `PAUSED_DEPOPULATED` broadcast with `!wasPaused`.
6. **BLOCKER — `CommTerminalItem` Sneak+Right-Click Could Not Bind to `CooperationHubBlock`** *(Live Client–Server QA)*: Fixed via `CommTerminalItem.onItemUseFirst(ItemStack, UseOnContext)` on all 4 targets; locked with authentic `ServerPlayerGameMode.useItemOn` bind/unbind tests.
7. **BLOCKER — `ic2:single_use_battery` Discharged `0.0 EU` in Mint/ATM** *(Live Enhanced Server QA)*: Fixed by passing full buffer headroom `maxCap - storedEu` to IC2's 4-arg `PortableGear`/`ForgeGear.discharge(...)` while keeping `maxInputEuPerTick` for FE discharge.
8. **Live `zh_cn` & `en_us` Visual / i18n Polish Across All 4 Targets** *(1280×720 Bilingual Visual Audit)*:
   - **NeoForge `CommTerminalScreen` header overlap**: Reduced `balLine` `maxWidth` from `156 → 114` so `村库资金：1800 IC | 余额:850` / `Funds: 1800 IC | Bal:850` cleanly clears `[解绑]`/`[Unbind]`.
   - **NeoForge & Forge `Lc2SoftBridge` station labels**: Changed server wire format to locale-independent `blockId|x,y,z` and localized client-side (`货运基站 (24,0,16)` / `Cargo Station`).
   - **Forge 1.19.2 ATM tx logs, Mayor nametag & Hub Expansion/Status LCD**: Suppressed internal `"ATM*"` counterparty (`-50 取出` / `-50 Withdraw`), lowered `CompactButton` `minScale` so `Account` renders unclipped, localized Mayor profession nametags (`[村长] 农民`), and formatted Hub Expansion materials & Status LCD cleanly across 2 lines without clipping.

---

## 5. Reproducible Verification Commands

```bash
# 1. Core import check & 33 JUnit 5 unit tests
./gradlew :checkCommonImports :test:test --rerun-tasks

# 2. Build all 4 production JARs
./gradlew :neoforge:1.21.1:jar :neoforge:26.1.2:jar :neoforge:26.2:jar
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 build reobfJar auditPackagedTyc2Jar

# 3. Static, bytecode & zh_cn/en_us lang audit of all 4 production JARs
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
python3 src/qa/run_client_server_live.py --mc-version 1.21.1 --profile enhanced --lang zh_cn
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=standalone -Ptyc2QaLang=zh_cn
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=enhanced -Ptyc2QaLang=zh_cn
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=enhanced -Ptyc2QaLang=en_us
```
