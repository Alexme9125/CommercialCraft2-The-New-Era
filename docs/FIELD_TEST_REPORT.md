# TycoonCraft²: The New Era (`tyc2`) — Full Build, Live Dedicated Server & Client–Server Field Test Report (`v1.0.0-pre.1`)

**Date**: 2026-11-23  
**Version Tested**: `1.0.0-pre.1` (final post-split production artifacts including `tyc2:primitive_mint_machine` vs pure-electric `tyc2:mint_machine`)  
**Optional Integration Pairings Tested**:
- **IndustrialCraft²: The New Era (`ic2`)**: `1.0.5` (`.ic2-deps/ic2-{neoforge,forge}-<ver>-1.0.5.jar`)
- **LogisticsCraft²: The New Era (`lc2`)**: `1.1.0` (`lc2-{neoforge,forge}-<ver>-1.1.0.jar`)

---

## 1. Executive Summary & Verified Release Artifacts

Every production JAR across all **4 Minecraft targets** (`Forge 1.19.2`, `NeoForge 1.21.1`, `NeoForge 26.1.2`, `NeoForge 26.2`) was rebuilt fresh after all feature updates and live QA fixes and verified through:
1. Core platform-neutral import gate (`:checkCommonImports`) and **31/31 JUnit 5 unit tests** (`:test:test`).
2. Automated static & bytecode production JAR audit (`src/qa/audit_jars.py` + Forge `auditPackagedTyc2Jar`) — **4/4 PASSED** (zero QA/preview leaks, zero non-compat/mixin `com.industrialage` references, valid `mods.toml`/`neoforge.mods.toml`, all **35 textures**, **21 models**, **4 blockstates**, **14** MC 26.x `assets/tyc2/items/*.json`, unconditional `primitive_mint_machine.json`, conditional `mint_machine_ic2.json` & `mint_machine_standalone.json`, zero stale `mint_machine.json`, and SRG reobfuscation verified on Forge 1.19.2).
3. Live headless Dedicated Server end-to-end QA across **BOTH Standalone (ONLY `tyc2`, NO IC2/LC2) AND Enhanced (`tyc2` + `ic2 1.0.5` + `lc2 1.1.0`)** with full **Write + Cold-Restart Read** world persistence — **100% PASSED across all 4 targets, 0 errors**.
4. Live **Graphical Client (`1280×720`) ↔ Dedicated Server over real TCP** joint tests across **BOTH Standalone AND Enhanced** on all 4 targets — **8/8 live sessions PASSED** (`35/35` sprites/textures verified, all **6 custom GUIs** + live 48-point CPI chart verified, visually inspected screenshots with zero text clipping, clean `client=0, server=0` exits, 0 log errors).

### Final Shipped Production JARs (`v1.0.0-pre.1`)

| Target | Java | Artifact Path | Size (Bytes) | Classes | Assets (`tex / mod / bs`) | SHA-256 | Static Audit |
|---|---|---|---:|---:|---|---|---|
| **Forge 1.19.2** | 17 | `forge-1.19.2/build/libs/tyc2-forge-1.19.2-1.0.0-pre.1.jar` | `622,303` | 188 (SRG) | `35 / 21 / 4` | `8a6c17fa5a4113781b47cd9c7978abe9e7656c7bff7107f3ec05c09ce9d3b592` | **PASS** |
| **NeoForge 1.21.1** | 21 | `src/neoforge/1.21.1/build/libs/tyc2-neoforge-1.21.1-1.0.0-pre.1.jar` | `583,288` | 170 | `35 / 21 / 4` | `0dd3ec4be659d9b574ac1d83bfcad0f614c17027dad65317f043d6cab99e3114` | **PASS** |
| **NeoForge 26.1.2** | 25 | `src/neoforge/26.1.2/build/libs/tyc2-neoforge-26.1.2-1.0.0-pre.1.jar` | `591,932` | 172 (`+14 items`) | `35 / 21 / 4` | `dcf37fa210a4554deb3031a4b74d8a749a1c6ade277827a1b35702167adb7608` | **PASS** |
| **NeoForge 26.2** | 25 | `src/neoforge/26.2/build/libs/tyc2-neoforge-26.2-1.0.0-pre.1.jar` | `591,931` | 172 (`+14 items`) | `35 / 21 / 4` | `789c882f8803352c42fc635bb1c8a1b1ff9fe07e358e6e77783de749ed28f7e6` | **PASS** |

---

## 2. Full 4-Target Pass/Fail Verification Matrix

| Target | Java | Unit / Static Gate | Dedicated Server: **Standalone** (`write` + cold `read`) | Dedicated Server: **Enhanced IC2+LC2** (`write` + cold `read`) | Live Graphical Client (`1280×720`) ↔ Server (TCP): **Standalone** | Live Graphical Client (`1280×720`) ↔ Server (TCP): **Enhanced IC2+LC2** | Log Errors |
|---|---|---|---|---|---|---|---|
| **Core (`src/common`)** | 17/21 | **31/31 JUnit 5 PASS** + `:checkCommonImports` PASS | — | — | — | — | **0** |
| **Forge 1.19.2** | 17 | **PASS** (`audit_jars.py` + `auditPackagedTyc2Jar`) | **134 + 18 = 152/152 PASS** (`pid=93146 / 93452`) | **185 + 19 = 204/204 PASS** (`pid=94018 / 94332`) | **73/73 PASS** (`:53431`, `14 srv + 59 cli`, `35/35` tex, 6 GUIs, `rc=0/0`, 8 PNGs) | **76/76 PASS** (`:55111`, `17 srv + 59 cli`, `35/35` tex, 6 GUIs, `rc=0/0`, 8 PNGs) | **0** |
| **NeoForge 1.21.1** | 21 | **PASS** (`audit_jars.py`) | **101 + 16 = 117/117 PASS** (`660t / 5t`, `pid=93004 / 93042`) | **30 + 7 = 37/37 PASS** (`242t / 10t`, `pid=92775 / 92896`) | **PASS** (`:52243`, `35/35` sprites, 6 GUIs + CPI chart, `rc=0/0`, 9 PNGs) | **PASS** (`:51881`, `35/35` sprites, IC2+LC2 live, 6 GUIs + CPI chart, `rc=0/0`, 9 PNGs) | **0** |
| **NeoForge 26.1.2** | 25 | **PASS** (`audit_jars.py`) | **101 + 16 = 117/117 PASS** (`660t / 5t`, `pid=92971 / 93023`) | **30 + 7 = 37/37 PASS** (`349t / 10t`, `pid=92882 / 92917`) | **PASS** (`:51885`, `35/35` sprites, 6 GUIs + CPI chart, `rc=0/0`, 9 PNGs) | **PASS** (`:52168`, `35/35` sprites, IC2+LC2 live, 6 GUIs + CPI chart, `rc=0/0`, 9 PNGs) | **0** |
| **NeoForge 26.2** | 25 | **PASS** (`audit_jars.py`) | **101 + 16 = 117/117 PASS** (`660t / 5t`, `pid=92958 / 93006`) | **30 + 7 = 37/37 PASS** (`369t / 10t`, `pid=92849 / 92903`) | **PASS** (`:51857`, `35/35` sprites, 6 GUIs + CPI chart, `rc=0/0`, 9 PNGs) | **PASS** (`:52088`, `35/35` sprites, IC2+LC2 live, 6 GUIs + CPI chart, `rc=0/0`, 9 PNGs) | **0** |

**Aggregate Live Automated Checks Verified**:
- **Forge 1.19.2**: `505 / 505` live checks PASSED (`0` failures, `0` log errors)
- **NeoForge 1.21.1 / 26.1.2 / 26.2**: `154 × 3 = 462 / 462` dedicated-server checks PASSED + `6 / 6` live graphical Client–Server sessions PASSED (`0` failures, `0` log errors)
- **Core Engine**: `31 / 31` JUnit 5 unit tests PASSED (`0` failures)

---

## 3. Verified Functional Coverage

### 3.1 Standalone Mode (`tyc2` ONLY — Zero IC2, Zero LC2)

Verified identically on Forge `1.19.2`, NeoForge `1.21.1`, NeoForge `26.1.2`, and NeoForge `26.2`:
- **Clean Boot & Conditional Recipes**: Boots cleanly with ONLY `tyc2` in `mods/`; `TyC2MixinPlugin` cleanly skips all 4 IC2 Mixins when `ic2` is absent; zero Mixin warnings or `NoClassDefFoundError`. `RecipeManager` loads `tyc2:primitive_mint_machine` (unconditional 100% Vanilla) and `tyc2:mint_machine_standalone` (high-tier redstone/diamond upgrade) while cleanly skipping `tyc2:mint_machine_ic2`.
- **Auto-Generated Configs & Datapacks**: `config/tyc2/general.json`, `custom_goods.json`, and `custom_mint_recipes.json` auto-generated on first boot; datapack loader loads **24 mint recipes** (6 `primitiveAllowed=true` + 18 industrial-only), **141 goods**, **10 village levels**, and seasonal overlays; `/tyc2 status`, `/tyc2 cpi`, `/tyc2 season` execute cleanly.
- **Primitive Minting Machine (`tyc2:primitive_mint_machine`)**:
  - Exposes **NO FE energy capability** (`feResult=-1`) and burns standard furnace fuels (`minecraft:coal`, etc.).
  - Mints pure-Vanilla `1x–100x` (`tyc2:coin_1` in `90` ticks and `tyc2:coin_100` in `210` ticks — exact `1.5×` speed!) with lit `ACTIVE` firebox blockstate.
  - **Blocks** high-tier `200x–1000x` recipes (`Diamond + 2×Gold` and `Netherite + Diamond`), setting `blockedByIndustrialTier = true` with `progress = 0` and zero output.
- **Pure-Electric Industrial Minting Machine (`tyc2:mint_machine`)**:
  - **Rejects solid coal/redstone in Slot 3** (`canPlaceItem == false`; forcing coal produces `0.0 EU` and `0` progress).
  - Runs via Forge/NeoForge FE (`128 FE/t = 32 EU/t`) and FE battery discharge, minting full `1x–1000x` (including dual-input `tyc2:coin_1000`), injecting minted face value into `MacroEconomyEngine`, and concurrently charging `tyc2:comm_terminal` in Slot 4 without starving mint progress.
- **Industrial ATM (`tyc2:atm_machine`)**:
  - Personal deposits, Guild/Company creation + invite-code join, cross-account transfers with exact balance reconciliation, optimal greedy-denomination coin withdrawals, and **standalone Vanilla coal terminal charging fallback** preserved intact.
- **Village Bell Discovery, Mayor Election, Partnership & Live Sneak+Right-Click Binding**:
  - Odd-coordinate Bell discovery (`(21, surfaceY, 21)`) verified live.
  - 1-villager negative test (NO Mayor) vs 2+ villagers positive test (golden `[Mayor]`) + Mayor death immediate re-election.
  - Partnership replaces Bell with blast-proof, mining-protected `tyc2:cooperation_hub` (`hardness = -1.0`, `explosionResistance >= 3,600,000.0`), cancels survival breaking, and blocks normal villagers from vanilla emerald trading.
  - Authentic `ServerPlayerGameMode.useItemOn` Sneak+Right-Click with `tyc2:comm_terminal` (even with an item in offhand) binds (`bound=1`, `CONSUME/SUCCESS`, zero 20 EU drain) and unbinds (`bound=0`) cleanly without falling through to `Item.use()`.
- **1,000-Slot Dual-Zone Cooperation Hub, CPI Engine & Collision-Safe Gradual Expansion**:
  - External item capability exposes `1,000` slots with strict unidirectional isolation: Inbound `0..499` insert-only (zero loop-back on extract), Outbound `500..999` extract-only; Sell→Outbound extract and Buy Auto-Fulfill verified.
  - Macro-Economy CPI rises on heavy minting (`~1.66 -> 4.00`) and deflates on sinks + trade circulation (`4.00 -> ~0.90`); Village upgrades Lv 1 → Lv 2 (burning `100 IC` sink).
  - `SMALL_COTTAGE` Expansion Desire builds `199–201` blocks gradually bottom-up across multiple ticks while leaving adjacent player-placed **Chests (BlockEntities with Diamonds)** and **player-placed Cobblestone Roads** 100% unharmed.
- **Cold-Restart Persistence (`read` phase in fresh JVM)**: Exact bank balances, CPI history, Lv 2 partnered village, Mayor, Hub block/BE & slot sentinels, occupied zones, and player-placed tracker all survive full JVM restart.

### 3.2 Enhanced Mode (`tyc2` + `ic2 1.0.5` + `lc2 1.1.0`)

Verified identically across all 4 targets loading the exact packaged `ic2-*-1.0.5.jar` and `lc2-*-1.1.0.jar`:
- **Conditional IC2 Recipe Activation**: `RecipeManager` loads `tyc2:primitive_mint_machine` + `tyc2:mint_machine_ic2` (`ic2:machine`, `ic2:circuit`, `ic2:iron_plate`, `ic2:electric_motor`, `tyc2:primitive_mint_machine`) and skips `tyc2:mint_machine_standalone`.
- **All 4 IC2 Soft-Compat Mixins Applied Cleanly**:
  1. `Ic2CableBlockMixin`: `ic2:insulated_copper_cable` does **NOT** connect to `tyc2:primitive_mint_machine` (`east=false`), and **DOES** visually connect (`east=true`) and deliver real EU to `tyc2:mint_machine` and `tyc2:atm_machine`.
  2. `Ic2PortableGearMixin`: `tyc2:comm_terminal` charges live inside real `ic2:batbox` (`590–2,400 EU` observed).
  3. `Ic2ProcessingRecipesMixin` & `Ic2WorkshopRecipesMixin`: disable IC2 `metal_former_cutting` `ic2:iron_casing -> ic2:coin` while preserving all non-coin Metal Former recipes (`cuttingCount = 4`).
- **Two-Tier Minting Under IC2 + Battery Discharge**:
  - `tyc2:primitive_mint_machine` mints canonical `ic2:coin x2` for Vanilla Copper while **blocking IC2 Alloy/Tin/Iridium** (`blockedByIndustrialTier = true`).
  - Pure-electric `tyc2:mint_machine` discharges **`ic2:single_use_battery`** (`1,200 EU` cleanly delivered → `900.0 EU` left after `300 EU` `ic2:tin_ingot -> 2x ic2:coin`!) as well as `ic2:re_battery`, and mints IC2 Endgame **`ic2:iridium + ic2:alloy -> tyc2:coin_1000 x1`**.
  - ATM accepts both `ic2:coin` and `tyc2:coin_1` 1:1; all 6 live IC2 goods (`ic2:resin`, `hops`, `coffee_beans`, `tin_ingot`, `alloy`, `iridium_shard`) active in catalog.
- **LC2 Logistics & Chunk Loading Integration**: `lc2:chunk_loading_upgrade` in Hub activates 3×3 chunk loading (`CL:3x3`, 9/9 chunks verified entity-ticking at `(160, 0, 0)` on Forge 1.19.2) and re-arms cleanly across cold JVM restart; nearby `lc2:cargo_station` detected.

### 3.3 Live Graphical Client (`1280×720`) ↔ Dedicated Server (TCP) Joint Tests

Real graphical Minecraft clients (`1280×720`) connected over loopback TCP to dedicated servers in **both Standalone and Enhanced modes** across all 4 targets:
- **Sprite / Texture Audit**: **35/35** TyC2 block, item, and GUI textures baked and verified non-missing on every target.
- **All 6 Custom Screens Verified Over Real Multiplayer Sync**:
  1. `PrimitiveMintMachineScreen` — verified in BOTH states: active coal pressing (`Pressing: 48–95%`, `1x-100x Vanilla Only`, outputting `tyc2:coin_1` Standalone / `ic2:coin` Enhanced) AND blocked high-tier/alloy state (Amber Warning Badge + `Requires Industrial Mint` + `200x-1000x / Alloys`, zero clipping).
  2. `MintMachineScreen` — pure-electric EU/FE bar, new Slot 3 Battery-Discharge Lightning Icon, concurrent `comm_terminal` charging, 4 IC2 upgrade slots.
  3. `AtmMachineScreen` — synced personal & guild accounts, transaction log, unclipped controls, fuel charger.
  4. `CooperationHubScreen` — Orders tab, Warehouse tab, live 48-point CPI inflation/deflation trend chart (`Economy` tab), Expansion tab, `CL:OFF` (Standalone) vs `CL:3x3` active green badge (Enhanced).
  5. `CommTerminalScreen` — bound village list (`1/16`), battery gauge, remote village orders (including live IC2 goods), remote CPI chart.
  6. `MayorNegotiationScreen` — golden `[Mayor]` header, 2-line greeting, two-column Village Profile + 4 green requirement checks, and 3-line Benefits LCD — visually confirmed completely unclipped on all 4 targets.

---

## 4. Real-World Bugs Caught & Verified Fixed During Live QA

Every bug below was uncovered by static audit, live dedicated-server tests, or `1280×720` graphical Client–Server screenshots, fixed by the owning engineer, rebuilt into all affected JARs, and re-verified to 100% GREEN:

1. **Preview PNG Leaked Into NeoForge Production JARs** *(Static Audit)*: `src/neoforge/platform.gradle` used `'preview/**'` instead of `'**/preview/**'`. Fixed; verified 0 preview/QA leaks across all 4 JARs.
2. **Village Bell Scanner Skipped 75% of Odd-Coordinate Bells** *(Live Server QA)*: Both NeoForge and Forge `findNearbyBell` stepped `dx += 2, dz += 2`. Fixed with loaded-chunk `BellBlockEntity` scan + 1-block fallback; locked with odd-coord `(21, y, 21)` regression check.
3. **Minting Machine EU Starvation When `comm_terminal` Occupies Slot 4** *(Live Server QA)*: `tickTerminalCharging()` ran before `tickMinting()`, draining low-rate power to `0 EU`. Fixed by running `tickMinting()` first and restricting terminal charging to surplus EU above pending mint demand.
4. **IC2 1.0.5 Item ID Mismatches (`ic2:sticky_resin` & Industrial Tier IDs)** *(Live Enhanced Server QA)*: Corrected to IC2 1.0.5's real IDs (`ic2:resin`, `ic2:machine`, `ic2:advanced_machine`, `ic2:centrifuge`, `ic2:uranium_fuel_rod`, `ic2:iridium`, `ic2:iridium_neutron_reflector`) in `GoodsCatalog`, `IndustrialTier`, and datagen.
5. **Depopulation Chat Spam Every 4 Seconds** *(Live Client–Server Screenshots)*: Transition-gated `PAUSED_DEPOPULATED` broadcast with `!wasPaused` on NeoForge and Forge.
6. **BLOCKER — `CommTerminalItem` Sneak+Right-Click Could Not Bind to `CooperationHubBlock`** *(Live Forge 1.19.2 Client–Server QA → Cross-Checked & Confirmed on All 3 NeoForge Targets)*: `ServerPlayerGameMode.useItemOn` skips `BlockState.use`/`useItemOn` while sneaking with a held item unless `doesSneakBypassUse` is true for both hands. Fixed via `CommTerminalItem.onItemUseFirst(ItemStack, UseOnContext)` on all 4 targets and locked with authentic `ServerPlayerGameMode.useItemOn` bind/unbind regression tests.
7. **BLOCKER — `ic2:single_use_battery` Discharged `0.0 EU` in `MintMachineBlockEntity` & `AtmMachineBlockEntity`** *(Live Enhanced Server QA Across All 4 Targets)*: Pre-clamping `requested` to `maxInputEuPerTick` (`32.0 EU`) caused IC2 1.0.5's `PortableEquipment.disposableCharge(requested, capacity, tier)` to reject 1,200 EU disposable batteries (`32 < 1200`). Fixed across Forge 1.19.2 and NeoForge 1.21.1/26.1.2/26.2 by calling the 4-arg `PortableGear`/`ForgeGear.discharge(stack, freeSpaceEu, maxCapEu, tier)` with full buffer headroom `maxCap - storedEu` (while keeping `maxInputEuPerTick` strictly for FE discharge). Verified live (`1,200 EU` discharged cleanly → `900.0 EU` remaining after `300 EU` Tin mint!).
8. **Live Client GUI Text Clipping & Alignment Polish** *(1280×720 Screenshots Across All 6 Screens)*: Fixed text truncation/overlap across `AtmMachineScreen`, `CooperationHubScreen`, `CommTerminalScreen`, `MayorNegotiationScreen` (2-column Village Profile + scaled greeting & benefits LCD via `PoseStack` on 1.19.2/1.21.1 and `Matrix3x2fStack` on 26.x), and `PrimitiveMintMachineScreen` (concise unclipped `200x-1000x / Alloys` blocked warning line 2 inside the 71px LCD with full detail in hover tooltip).

---

## 5. Reproducible Verification Commands

```bash
# 1. Core import check & 31 JUnit 5 unit tests
./gradlew :checkCommonImports :test:test --rerun-tasks

# 2. Build all 4 production JARs
./gradlew :neoforge:1.21.1:jar :neoforge:26.1.2:jar :neoforge:26.2:jar
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 build reobfJar auditPackagedTyc2Jar

# 3. Static & bytecode audit of all 4 production JARs (35 textures, 4 blockstates, conditional recipes)
python3 src/qa/audit_jars.py

# 4. Live Dedicated Server QA (Standalone + Enhanced, write + cold-restart read)
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:1.21.1:tyc2QaAll
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:26.1.2:tyc2QaAll
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:26.2:tyc2QaAll
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2QaStandalone runTyc2QaEnhanced

# 5. Live Graphical Client (1280x720) <-> Dedicated Server (TCP) Joint Tests (6 GUIs + 35/35 sprites)
for VER in 1.21.1 26.1.2 26.2; do
  ./gradlew --init-script src/qa/neoforge-init.gradle ":neoforge:${VER}:prepareClientServerPlay"
  python3 src/qa/run_client_server_live.py --mc-version "$VER" --profile standalone
  python3 src/qa/run_client_server_live.py --mc-version "$VER" --profile enhanced
done
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=standalone
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home \
  ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2LiveClientServer -Ptyc2QaProfile=enhanced
```
