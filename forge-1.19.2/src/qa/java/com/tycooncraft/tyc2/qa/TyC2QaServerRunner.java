package com.tycooncraft.tyc2.qa;

import com.mojang.authlib.GameProfile;
import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.GoodsCatalog;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.compat.JadeSoftBridge;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.ServerTerrainProbe;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.Aabb3i;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.ConstructionJob;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SpawnMarker;
import com.tycooncraft.tyc2.village.StructureBlueprint;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.village.WorkplaceArchitectureCatalog;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.village.WorkplaceSpec;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Comprehensive two-phase ({@code write} + cold-restart {@code read}) live dedicated-server
 * QA harness for TycoonCraft² on Forge 1.19.2 covering BOTH Standalone (pure Vanilla)
 * and Enhanced (IC2 + LC2) profiles.
 */
final class TyC2QaServerRunner {

    private static final UUID PLAYER_A_UUID = UUID.fromString("11111111-2222-3333-4444-555555555551");
    private static final UUID PLAYER_B_UUID = UUID.fromString("11111111-2222-3333-4444-555555555552");

    private enum Stage {
        INIT,
        // Write phase stages
        W1_BOOT_AND_REGISTRY,
        W2_MINT_AND_POWER_SETUP,
        W2_PRIMITIVE_AND_COAL_REJECT_WAIT,
        W2_MINT_DUAL_AND_TERMINAL_WAIT,
        W3_ATM_AND_BANKING,
        W3_ATM_COAL_FALLBACK_WAIT,
        W4_VILLAGE_ONE_VILLAGER_SETUP,
        W4_VILLAGE_ONE_VILLAGER_WAIT,
        W4_VILLAGE_MULTI_VILLAGER_WAIT,
        W4_MAYOR_TETHER_AND_NITWIT_STRIP_WAIT,
        W5_PARTNERSHIP_HUB_AND_ISOLATION,
        W5_WAIT_AUTOFULFILL,
        W6_UPGRADE_AND_EXPANSION_SETUP,
        W6_WAIT_AUTO_LAUNCH_CONSTRUCTION,
        W6_WAIT_CONSTRUCTION_PROGRESS,
        W6B_AID_SPAWN_UNEMPLOYED,
        W6B_AID_FUND_CANCEL_AND_PAUSE_SETUP,
        W6B_AID_WAIT_GATHERING,
        W6B_AID_WAIT_BUILD,
        W7_ENHANCED_IC2_LC2_SETUP,
        W7_ENHANCED_IC2_LC2_WAIT,
        W8_SAVE_AND_HALT,
        // Read phase stage
        R1_VERIFY_RELOADED,
        DONE
    }

    private final String profile;
    private final String phase;
    private final boolean enhanced;
    private final TyC2QaAssert qa;

    private Stage stage = Stage.INIT;
    private int stageTicks = 0;
    private boolean finished = false;

    private int surfaceY = 0;
    private BlockPos primMintPos;
    private BlockPos mintCoalPos;
    private BlockPos atmPos;
    private BlockPos hubPos;
    private BlockPos protectedChestPosA;
    private BlockPos protectedChestPosB;
    private BlockPos ic2GenPos;
    private BlockPos ic2CableMintPos;
    private BlockPos ic2MintPos;
    private BlockPos ic2CableAtmPos;
    private BlockPos ic2AtmPos;
    private BlockPos ic2BatGenPos;
    private BlockPos ic2BatBoxPos;
    private BlockPos ic2CablePrimPos;
    private BlockPos ic2PrimPos;
    private BlockPos ic2SingleUseBatMintPos;
    private BlockPos nitwitHubPos;
    private BlockPos lc2HubPos;

    private FakePlayer playerA;
    private FakePlayer playerB;
    private UUID villageId;
    private UUID nitwitVillageId;
    private UUID nitwit1Uuid;
    private UUID nitwit2Uuid;
    private UUID eligibleNonNitwitUuid;
    private UUID tetherMayorUuid;
    private double tetherInitialDistSq;
    private boolean checkedMayorTether = false;
    private UUID lc2VillageId;
    private String companyId;
    private UUID firstMayorUuid;
    private String autoFulfillOrderId;
    private long tradeVolBeforeAutoFulfill = 0L;
    private int initialJobPlaced = 0;
    private int sawMidProgressPlaced = 0;
    private double cpiBeforeMint;
    private long primMintStartTick = 0L;
    private boolean sawPrimMintActiveAnd15x = false;
    private boolean checkedElectricCoalReject = false;
    private boolean checkedPrimBlockedVanilla = false;

    // Aid construction & low-unemployment bonus state tracking
    private String aidProjectId;
    private String aidPersistProjectId;
    private long aidPersistPaidIc = -1L;
    private double aidFirstObservedGathered = -1.0;
    private double aidExpectedGatherSpeed = 1.0;
    private double aidUnemploymentBefore = 0.0;
    private boolean aidBuildingStarted = false;
    private List<BlockPos> aidWorkstationPositions = List.of();
    private List<UUID> aidUnemployedUuids = List.of();

    TyC2QaServerRunner(String profile, String phase) {
        this.profile = profile;
        this.phase = phase;
        this.enhanced = "enhanced".equalsIgnoreCase(profile);
        this.qa = new TyC2QaAssert(profile + "-" + phase);
    }

    private Path reportPath() {
        return Path.of("qa-" + profile + "-" + phase + ".properties");
    }

    private Path failurePath() {
        return Path.of("qa-" + profile + "-" + phase + "-failure.txt");
    }

    private Path expectedStatePath() {
        return Path.of("qa-" + profile + "-expected.properties");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        try {
            step(server, level);
        } catch (Throwable t) {
            finished = true;
            TyC2QaMod.LOGGER.error("[TYC2-QA] Unhandled failure in profile={} phase={} stage={}",
                    profile, phase, stage, t);
            qa.writeFailureReport(reportPath(), failurePath(), t);
            server.halt(false);
        }
    }

    private void advance(Stage next) {
        this.stage = next;
        this.stageTicks = 0;
    }

    private void step(MinecraftServer server, ServerLevel level) throws Exception {
        stageTicks++;
        switch (stage) {
            case INIT -> {
                if (stageTicks < 3) {
                    return;
                }
                if ("read".equals(phase) && Files.isRegularFile(expectedStatePath())) {
                    Properties exp = new Properties();
                    try (Reader r = Files.newBufferedReader(expectedStatePath())) {
                        exp.load(r);
                    }
                    surfaceY = Integer.parseInt(exp.getProperty("surfaceY", "0"));
                } else {
                    surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                }
                // Keep 5x5 chunks around origin (0,0) loaded for village & expansion construction
                for (int cx = -2; cx <= 2; cx++) {
                    for (int cz = -2; cz <= 2; cz++) {
                        level.setChunkForced(cx, cz, true);
                        level.getChunk(cx, cz);
                    }
                }
                primMintPos = new BlockPos(-8, surfaceY, -6);
                mintCoalPos = new BlockPos(-6, surfaceY, -6);
                atmPos = new BlockPos(-4, surfaceY, -6);
                hubPos = new BlockPos(0, surfaceY, 0);
                protectedChestPosA = new BlockPos(10, surfaceY, 0);
                protectedChestPosB = new BlockPos(0, surfaceY, 10);
                ic2GenPos = new BlockPos(-10, surfaceY, -14);
                ic2CableMintPos = new BlockPos(-9, surfaceY, -14);
                ic2MintPos = new BlockPos(-8, surfaceY, -14);
                ic2CableAtmPos = new BlockPos(-11, surfaceY, -14);
                ic2AtmPos = new BlockPos(-12, surfaceY, -14);
                ic2BatGenPos = new BlockPos(-10, surfaceY, -18);
                ic2BatBoxPos = new BlockPos(-9, surfaceY, -18);
                ic2CablePrimPos = new BlockPos(-13, surfaceY, -18);
                ic2PrimPos = new BlockPos(-14, surfaceY, -18);
                ic2SingleUseBatMintPos = new BlockPos(-14, surfaceY, -14);
                nitwitHubPos = new BlockPos(120, surfaceY, 120); // Isolated >169 blocks from origin & lc2HubPos
                lc2HubPos = new BlockPos(160, surfaceY, 0); // Far chunk (10, 0) with NO QA force ticket!

                if ("write".equals(phase)) {
                    advance(Stage.W1_BOOT_AND_REGISTRY);
                } else {
                    advance(Stage.R1_VERIFY_RELOADED);
                }
            }

            case W1_BOOT_AND_REGISTRY -> {
                runBootAndRegistryChecks(server, level);
                advance(Stage.W2_MINT_AND_POWER_SETUP);
            }

            case W2_MINT_AND_POWER_SETUP -> {
                runMintSetup(level);
                advance(Stage.W2_PRIMITIVE_AND_COAL_REJECT_WAIT);
            }

            case W2_PRIMITIVE_AND_COAL_REJECT_WAIT -> {
                PrimitiveMintMachineBlockEntity prim = (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintCoalPos);
                if (prim == null || mint == null) {
                    throw new AssertionError("Primitive or Electric Minting Machine BE missing");
                }

                // 1. After 15 ticks, prove electric tyc2:mint_machine NEVER burns coal in Slot 3!
                if (!checkedElectricCoalReject && stageTicks >= 15) {
                    checkedElectricCoalReject = true;
                    qa.check("electric_mint.does_not_burn_coal_eu_zero",
                            mint.getStoredEu() == 0.0 && mint.getProgressPercent() == 0
                                    && mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT).isEmpty()
                                    && mint.getItem(MintMachineBlockEntity.SLOT_BATTERY).getCount() == 4,
                            "eu=" + mint.getStoredEu() + ", prog=" + mint.getProgressPercent()
                                    + ", coalCount=" + mint.getItem(MintMachineBlockEntity.SLOT_BATTERY).getCount());

                    // Replace rejected coal with QA extractable Forge FE battery (40,000 FE = 10,000 EU)!
                    ItemStack qaBat = TyC2QaMod.createQaFeBattery(40_000);
                    qa.check("electric_mint.accepts_fe_battery_item",
                            MintMachineBlockEntity.isValidBatteryItem(qaBat, 1)
                                    && mint.canPlaceItem(MintMachineBlockEntity.SLOT_BATTERY, qaBat),
                            "qaBat=" + qaBat);
                    mint.setItem(MintMachineBlockEntity.SLOT_BATTERY, qaBat);
                }

                // 2. While Primitive Mint is actively running copper->1x, check 1.5x duration (90t) & ACTIVE blockstate!
                if (!sawPrimMintActiveAnd15x && prim.getProgressTicks() > 0) {
                    sawPrimMintActiveAnd15x = true;
                    qa.checkEquals("primitive_mint.copper_duration_1_5x_90_ticks",
                            TriModePowerMath.primitiveMintDurationTicks(60), prim.getMaxProgressTicks());
                    qa.check("primitive_mint.active_blockstate_true",
                            Boolean.TRUE.equals(level.getBlockState(primMintPos).getValue(PrimitiveMintMachineBlock.ACTIVE)),
                            "state=" + level.getBlockState(primMintPos));
                    qa.check("primitive_mint.not_blocked_on_vanilla_copper",
                            !prim.isBlockedByIndustrialTier(), "blocked=" + prim.isBlockedByIndustrialTier());
                }

                ItemStack primOut = prim.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT);
                if (!primOut.isEmpty()) {
                    long elapsed = level.getGameTime() - primMintStartTick;
                    String expected1x = enhanced ? CurrencyDenomination.IC2_COIN_ID : "tyc2:coin_1";
                    qa.check("primitive_mint.saw_active_and_1_5x", sawPrimMintActiveAnd15x, "elapsed=" + elapsed);
                    qa.check("primitive_mint.elapsed_at_least_90_ticks", elapsed >= 88L, "elapsed=" + elapsed);
                    qa.checkEquals("primitive_mint.copper_output_item_id", expected1x, TyC2Ids.itemId(primOut));
                    qa.checkEquals("primitive_mint.copper_output_count", 2, primOut.getCount());
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                    qa.check("mint.injected_money_supply",
                            data.economy().totalMintedNet() >= 2L,
                            "totalMintedNet=" + data.economy().totalMintedNet());
                    qa.check("mint.cpi_positive", data.economy().computeCpi() > 0.0,
                            "cpi=" + data.economy().computeCpi());

                    // 3. Verify electric Mint discharged FE battery cleanly!
                    ItemStack batAfter = mint.getItem(MintMachineBlockEntity.SLOT_BATTERY);
                    int feLeft = TyC2QaMod.getQaFeStored(batAfter);
                    qa.check("electric_mint.fe_battery_discharged_into_eu",
                            feLeft < 40_000 && (mint.getStoredEu() > 0.0 || !mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT).isEmpty()),
                            "feLeft=" + feLeft + ", storedEu=" + mint.getStoredEu()
                                    + ", out=" + mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT));

                    // 4. Put Vanilla 1000x (Netherite + Diamond) into Primitive Mint -> MUST BLOCK!
                    prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.NETHERITE_INGOT, 1));
                    prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.DIAMOND, 1));

                    // 5. Feed Forge FE into Electric Mint & run dual-input 1000x + concurrent comm_terminal charging!
                    mint.setItem(MintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    mint.setItem(MintMachineBlockEntity.SLOT_BATTERY, ItemStack.EMPTY);
                    IEnergyStorage feCap = mint.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                            .orElseThrow(() -> new AssertionError("Missing FE capability on MintMachine"));
                    int totalFeFed = 0;
                    for (int i = 0; i < 512 && mint.getStoredEu() < 12_000.0; i++) {
                        totalFeFed += feCap.receiveEnergy(4_000, false);
                    }
                    qa.check("electric_mint.charged_via_forge_fe_for_1000x",
                            totalFeFed > 0 && mint.getStoredEu() >= 10_000.0,
                            "totalFeFed=" + totalFeFed + ", storedEu=" + mint.getStoredEu());
                    mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.NETHERITE_INGOT, 1));
                    mint.setItem(MintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.DIAMOND, 1));
                    ItemStack emptyTerm = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
                    CommTerminalItem.setStoredEu(emptyTerm, 0);
                    mint.setItem(MintMachineBlockEntity.SLOT_TERMINAL, emptyTerm);
                    advance(Stage.W2_MINT_DUAL_AND_TERMINAL_WAIT);
                } else if (stageTicks > 200) {
                    throw new AssertionError("PrimitiveMintMachine did not mint copper->1x within 200 ticks (burn="
                            + prim.getBurnTicksRemaining() + ", prog=" + prim.getProgressTicks() + "/" + prim.getMaxProgressTicks() + ")");
                }
            }

            case W2_MINT_DUAL_AND_TERMINAL_WAIT -> {
                PrimitiveMintMachineBlockEntity prim = (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintCoalPos);
                // Simulate continuous LV Forge FE cable delivery (128 FE/t = 32 EU/t) into the pure-electric Mint!
                mint.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                        .ifPresent(fe -> fe.receiveEnergy(128, false));
                if (!checkedPrimBlockedVanilla && stageTicks >= 6 && prim != null) {
                    checkedPrimBlockedVanilla = true;
                    qa.check("primitive_mint.blocks_vanilla_1000x_netherite_diamond",
                            prim.isBlockedByIndustrialTier()
                                    && prim.getProgressTicks() == 0
                                    && prim.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT).isEmpty()
                                    && Boolean.FALSE.equals(level.getBlockState(primMintPos).getValue(PrimitiveMintMachineBlock.ACTIVE)),
                            "blocked=" + prim.isBlockedByIndustrialTier()
                                    + ", prog=" + prim.getProgressTicks()
                                    + ", active=" + level.getBlockState(primMintPos).getValue(PrimitiveMintMachineBlock.ACTIVE));
                    // Also check 200x Diamond+Gold requiresIndustrialMint
                    qa.check("primitive_mint.requires_industrial_for_200x_diamond_gold",
                            TyC2RuntimeData.mintRecipes().requiresIndustrialMint(
                                    "minecraft:diamond", 1, "minecraft:gold_ingot", 2,
                                    TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded),
                            "200x diamond+gold");
                }

                ItemStack out = mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT);
                if (!out.isEmpty()) {
                    qa.checkEquals("mint.dual_1000x_output_id", "tyc2:coin_1000", TyC2Ids.itemId(out));
                    qa.checkEquals("mint.dual_1000x_output_count", 1, out.getCount());
                    int termEu = CommTerminalItem.getStoredEu(mint.getItem(MintMachineBlockEntity.SLOT_TERMINAL));
                    qa.check("mint.terminal_charged", termEu > 0, "terminalEu=" + termEu);

                    // Verify CPI inflation on minting & deflation on money sinks!
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                    MacroEconomyEngine econ = data.economy();
                    double cpiBase = econ.computeCpi();
                    econ.recordMinting(5_000L);
                    double cpiInflated = econ.computeCpi();
                    econ.recordMoneySink(4_500L);
                    double cpiAfterSink = econ.computeCpi();
                    econ.advanceDemandCycle(level.getGameTime());
                    data.setDirty();
                    qa.check("macro.cpi_inflates_on_minting", cpiInflated > cpiBase,
                            String.format("base=%.4f -> inflated=%.4f", cpiBase, cpiInflated));
                    qa.check("macro.cpi_deflates_on_sink", cpiAfterSink < cpiInflated,
                            String.format("inflated=%.4f -> afterSink=%.4f", cpiInflated, cpiAfterSink));
                    qa.metric("macro.cpi_inflated", String.format("%.4f", cpiInflated));
                    qa.metric("macro.cpi_after_sink", String.format("%.4f", cpiAfterSink));
                    advance(Stage.W3_ATM_AND_BANKING);
                } else if (stageTicks > 480) {
                    throw new AssertionError("MintMachine did not mint dual 1000x recipe within 480 ticks (eu="
                            + mint.getStoredEu() + ", prog=" + mint.getProgressPercent() + ")");
                }
            }

            case W3_ATM_AND_BANKING -> {
                runAtmAndBankingChecks(server, level);
                // Seed coal + empty comm_terminal in ATM to prove standalone coal fuel charger fallback STILL works!
                if (level.getBlockEntity(atmPos) instanceof AtmMachineBlockEntity atm) {
                    atm.setItem(AtmMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2));
                    ItemStack atmTerm = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
                    CommTerminalItem.setStoredEu(atmTerm, 0);
                    atm.setItem(AtmMachineBlockEntity.SLOT_TERMINAL, atmTerm);
                }
                advance(Stage.W3_ATM_COAL_FALLBACK_WAIT);
            }

            case W3_ATM_COAL_FALLBACK_WAIT -> {
                if (stageTicks < 25) {
                    return;
                }
                AtmMachineBlockEntity atm = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
                int atmTermEu = atm == null ? 0 : CommTerminalItem.getStoredEu(atm.getItem(AtmMachineBlockEntity.SLOT_TERMINAL));
                qa.check("atm.standalone_coal_fuel_charger_still_works",
                        atm != null && (atm.getStoredEu() > 0.0 || atmTermEu > 0),
                        "atmEu=" + (atm == null ? -1 : atm.getStoredEu()) + ", atmTermEu=" + atmTermEu);
                advance(Stage.W4_VILLAGE_ONE_VILLAGER_SETUP);
            }

            case W4_VILLAGE_ONE_VILLAGER_SETUP -> {
                // Place Bell + ONLY 1 villager -> MUST NOT elect a Mayor!
                level.setBlock(hubPos, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
                Villager solo = spawnAdultVillager(level, hubPos.offset(2, 0, 0), VillagerProfession.FARMER, "SoloVillager");
                qa.check("village.solo_villager_spawned", solo.isAlive(), "uuid=" + solo.getUUID());

                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                villageId = UUID.randomUUID();
                VillageRecord rec = new VillageRecord(
                        villageId,
                        level.dimension().location().toString(),
                        "QA Oakfield",
                        TyC2Ids.toCore(hubPos),
                        BiomeStyle.PLAINS
                );
                data.putVillage(rec);

                // In parallel, set up isolated Nitwit-only village at (120, y, 120): 2 adult NITWITS MUST NOT elect Mayor!
                ChunkPos nc = new ChunkPos(nitwitHubPos);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setChunkForced(nc.x + dx, nc.z + dz, true);
                        level.getChunk(nc.x + dx, nc.z + dz);
                    }
                }
                level.setBlock(nitwitHubPos, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
                Villager nw1 = spawnAdultVillager(level, nitwitHubPos.offset(2, 0, 0), VillagerProfession.NITWIT, "NitwitOne");
                Villager nw2 = spawnAdultVillager(level, nitwitHubPos.offset(-2, 0, 0), VillagerProfession.NITWIT, "NitwitTwo");
                nitwit1Uuid = nw1.getUUID();
                nitwit2Uuid = nw2.getUUID();
                nitwitVillageId = UUID.randomUUID();
                VillageRecord nitwitRec = new VillageRecord(
                        nitwitVillageId,
                        level.dimension().location().toString(),
                        "QA Nitwit Hollow",
                        TyC2Ids.toCore(nitwitHubPos),
                        BiomeStyle.PLAINS
                );
                data.putVillage(nitwitRec);
                advance(Stage.W4_VILLAGE_ONE_VILLAGER_WAIT);
            }

            case W4_VILLAGE_ONE_VILLAGER_WAIT -> {
                // Wait >= 105 ticks so TycoonForgeWorldManager.onServerTick's 100-tick scan runs!
                if (stageTicks < 105) {
                    return;
                }
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord rec = data.getVillage(villageId).orElseThrow();
                qa.check("village.one_villager_no_mayor",
                        rec.getMayorState().getMayorEntityUuid() == null,
                        "pop=" + rec.getPopulation() + ", mayor=" + rec.getMayorState().getMayorEntityUuid());

                // Verify Nitwit-only village (2 adult Nitwits, 0 non-nitwits) elected NO Mayor!
                VillageRecord nwRec = data.getVillage(nitwitVillageId).orElseThrow();
                Villager nw1 = findVillagerByUuid(level, nitwitHubPos, nitwit1Uuid);
                Villager nw2 = findVillagerByUuid(level, nitwitHubPos, nitwit2Uuid);
                qa.check("nitwit.two_nitwits_no_mayor_elected",
                        nwRec.getMayorState().getMayorEntityUuid() == null
                                && nw1 != null && !nw1.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)
                                && nw2 != null && !nw2.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR),
                        "mayor=" + nwRec.getMayorState().getMayorEntityUuid()
                                + ", nw1Tag=" + (nw1 != null && nw1.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR))
                                + ", nw2Tag=" + (nw2 != null && nw2.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)));

                // Legacy save simulation: force-tag Nitwit #1 as Mayor, then add 1 eligible non-nitwit adult!
                nw1.getPersistentData().putBoolean(TycoonForgeWorldManager.TAG_MAYOR, true);
                nw1.getPersistentData().putString(TycoonForgeWorldManager.TAG_VILLAGE_ID, nitwitVillageId.toString());
                nw1.setCustomName(Component.literal("[Mayor] LegacyNitwit"));
                nw1.setCustomNameVisible(true);
                nw1.setGlowingTag(true);
                nwRec.getMayorState().assignMayor(nw1.getUUID(), "LegacyNitwit", level.getGameTime());
                Villager eligible = spawnAdultVillager(level, nitwitHubPos.offset(0, 0, 2), VillagerProfession.LIBRARIAN, "EligibleLibrarian");
                eligibleNonNitwitUuid = eligible.getUUID();

                // Now spawn 2 more adult villagers (total 3 >= 2) at main Bell -> Mayor MUST be elected on next 100-tick scan!
                spawnAdultVillager(level, hubPos.offset(-2, 0, 0), VillagerProfession.LIBRARIAN, "SecondVillager");
                spawnAdultVillager(level, hubPos.offset(0, 0, 2), VillagerProfession.CLERIC, "ThirdVillager");
                advance(Stage.W4_VILLAGE_MULTI_VILLAGER_WAIT);
            }

            case W4_VILLAGE_MULTI_VILLAGER_WAIT -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord rec = data.getVillage(villageId).orElseThrow();
                UUID elected = rec.getMayorState().getMayorEntityUuid();
                if (elected != null) {
                    firstMayorUuid = elected;
                    Villager mayor = findVillagerByUuid(level, hubPos, firstMayorUuid);
                    qa.check("village.two_plus_elects_mayor",
                            mayor != null
                                    && mayor.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)
                                    && mayor.hasCustomName()
                                    && mayor.isCustomNameVisible(),
                            "mayorUuid=" + firstMayorUuid + ", customName="
                                    + (mayor == null || mayor.getCustomName() == null ? "null" : mayor.getCustomName().getString()));

                    // Kill Mayor -> MUST immediately re-elect a DIFFERENT surviving villager via LivingDeathEvent!
                    mayor.hurt(DamageSource.OUT_OF_WORLD, 1000.0F);
                    UUID afterDeathMayor = rec.getMayorState().getMayorEntityUuid();
                    Villager newMayor = findVillagerByUuid(level, hubPos, afterDeathMayor);
                    qa.check("village.mayor_death_reelects",
                            afterDeathMayor != null
                                    && !afterDeathMayor.equals(firstMayorUuid)
                                    && newMayor != null
                                    && newMayor.isAlive()
                                    && newMayor.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR),
                            "old=" + firstMayorUuid + " -> new=" + afterDeathMayor);

                    // Verify Mayor Negotiation Hold (stationary + tradingPlayer + facing player, then clean release!)
                    newMayor.moveTo(hubPos.getX() + 0.5, surfaceY, hubPos.getZ() + 0.5, 0.0F, 0.0F);
                    playerA.setPos(hubPos.getX() + 3.5, surfaceY, hubPos.getZ() + 0.5);
                    newMayor.setDeltaMovement(0.4, 0.0, -0.3);
                    TycoonForgeWorldManager.startMayorNegotiation(playerA, newMayor);
                    MayorNegotiationMenu negMenu = new MayorNegotiationMenu(1, playerA.getInventory(), villageId, newMayor.getUUID());
                    float expectedYaw = (float) (Mth.atan2(playerA.getZ() - newMayor.getZ(), playerA.getX() - newMayor.getX()) * (180.0 / Math.PI)) - 90.0F;
                    float yawDiff = Math.abs(Mth.wrapDegrees(newMayor.getYHeadRot() - expectedYaw));
                    qa.check("mayor.negotiation_hold_active",
                            newMayor.getTradingPlayer() == playerA
                                    && newMayor.getNavigation().isDone()
                                    && Math.abs(newMayor.getDeltaMovement().x) < 1e-6
                                    && Math.abs(newMayor.getDeltaMovement().z) < 1e-6
                                    && yawDiff < 2.0F,
                            "trading=" + (newMayor.getTradingPlayer() == playerA)
                                    + ", navDone=" + newMayor.getNavigation().isDone()
                                    + ", vel=" + newMayor.getDeltaMovement()
                                    + ", yawDiff=" + yawDiff);
                    negMenu.removed(playerA);
                    qa.check("mayor.negotiation_hold_released_on_close",
                            newMayor.getTradingPlayer() == null,
                            "tradingAfterClose=" + newMayor.getTradingPlayer());

                    // Setup daytime Plaza Tether test: move Mayor >18 blocks away from Bell during daytime!
                    level.setDayTime(6000L);
                    tetherMayorUuid = newMayor.getUUID();
                    newMayor.moveTo(hubPos.getX() + 20.5, surfaceY, hubPos.getZ() + 0.5, 0.0F, 0.0F);
                    newMayor.getNavigation().stop();
                    tetherInitialDistSq = newMayor.distanceToSqr(hubPos.getX() + 0.5, surfaceY, hubPos.getZ() + 0.5);
                    advance(Stage.W4_MAYOR_TETHER_AND_NITWIT_STRIP_WAIT);
                } else if (stageTicks > 130) {
                    throw new AssertionError("Mayor was not elected with 3 villagers within 130 ticks");
                }
            }

            case W4_MAYOR_TETHER_AND_NITWIT_STRIP_WAIT -> {
                level.setDayTime(6000L);
                if (stageTicks < 35) {
                    return;
                }
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                // 1. Verify Plaza Tether ONCE after >=35 ticks so 20-tick tickMayorAnchors ran!
                if (!checkedMayorTether) {
                    checkedMayorTether = true;
                    Villager tetherMayor = findVillagerByUuid(level, hubPos, tetherMayorUuid);
                    qa.check("mayor.tether_mayor_alive", tetherMayor != null && tetherMayor.isAlive(), "uuid=" + tetherMayorUuid);
                    Optional<GlobalPos> mpOpt = tetherMayor.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
                    GlobalPos expectedMp = GlobalPos.of(level.dimension(), hubPos);
                    qa.check("mayor.tether_meeting_point_bound_to_hub",
                            mpOpt.isPresent() && expectedMp.equals(mpOpt.get()),
                            "meetingPoint=" + mpOpt);
                    double curDistSq = tetherMayor.distanceToSqr(hubPos.getX() + 0.5, surfaceY, hubPos.getZ() + 0.5);
                    var navPath = tetherMayor.getNavigation().getPath();
                    boolean movingOrTargetingPlaza = curDistSq < tetherInitialDistSq - 0.25
                            || (navPath != null && navPath.getTarget().distSqr(hubPos) < 16.0);
                    qa.check("mayor.tether_navigates_back_toward_plaza",
                            movingOrTargetingPlaza,
                            String.format("initDist=%.2f -> curDist=%.2f, navTarget=%s",
                                    Math.sqrt(tetherInitialDistSq), Math.sqrt(curDistSq),
                                    navPath == null ? "null" : navPath.getTarget()));
                    // Return Mayor back near Bell so subsequent stages run cleanly
                    tetherMayor.moveTo(hubPos.getX() + 1.5, surfaceY, hubPos.getZ() + 1.5, 0.0F, 0.0F);
                }

                // 2. Verify legacy Nitwit Mayor was disqualified & stripped and non-nitwit became Mayor!
                VillageRecord nwRec = data.getVillage(nitwitVillageId).orElseThrow();
                if (!eligibleNonNitwitUuid.equals(nwRec.getMayorState().getMayorEntityUuid())) {
                    if (stageTicks > 130) {
                        throw new AssertionError("Legacy Nitwit Mayor was not replaced by eligible adult within 130 ticks: "
                                + nwRec.getMayorState().getMayorEntityUuid());
                    }
                    return;
                }
                Villager nw1 = findVillagerByUuid(level, nitwitHubPos, nitwit1Uuid);
                Villager eligible = findVillagerByUuid(level, nitwitHubPos, eligibleNonNitwitUuid);
                qa.check("nitwit.legacy_nitwit_replaced_by_non_nitwit",
                        eligibleNonNitwitUuid.equals(nwRec.getMayorState().getMayorEntityUuid())
                                && eligible != null && eligible.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR),
                        "elected=" + nwRec.getMayorState().getMayorEntityUuid());
                qa.check("nitwit.legacy_nitwit_tags_and_name_stripped",
                        nw1 != null
                                && !nw1.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)
                                && !nw1.getPersistentData().contains(TycoonForgeWorldManager.TAG_VILLAGE_ID)
                                && !nw1.hasCustomName()
                                && !nw1.hasGlowingTag(),
                        "tagMayor=" + (nw1 != null && nw1.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR))
                                + ", hasName=" + (nw1 != null && nw1.hasCustomName())
                                + ", glowing=" + (nw1 != null && nw1.hasGlowingTag()));
                advance(Stage.W5_PARTNERSHIP_HUB_AND_ISOLATION);
            }

            case W5_PARTNERSHIP_HUB_AND_ISOLATION -> {
                runPartnershipHubAndIsolationChecks(server, level);
                advance(Stage.W5_WAIT_AUTOFULFILL);
            }

            case W5_WAIT_AUTOFULFILL -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord rec = data.getVillage(villageId).orElseThrow();
                Optional<VillageTradeOrder> ordOpt = rec.findOrder(autoFulfillOrderId);
                boolean partiallyDone = ordOpt.isPresent() && ordOpt.get().fulfilledQty() > 0;
                boolean fullyCompletedAndPruned = ordOpt.isEmpty() && rec.getCumulativeTradeVolume() > tradeVolBeforeAutoFulfill;
                if (partiallyDone || fullyCompletedAndPruned) {
                    qa.check("hub.auto_fulfill_executed", true,
                            partiallyDone
                                    ? "order=" + autoFulfillOrderId + ", fulfilled=" + ordOpt.get().fulfilledQty()
                                    + "/" + ordOpt.get().totalQty()
                                    : "order=" + autoFulfillOrderId + " 100% completed & pruned (vol "
                                    + tradeVolBeforeAutoFulfill + " -> " + rec.getCumulativeTradeVolume() + ")");
                    advance(Stage.W6_UPGRADE_AND_EXPANSION_SETUP);
                } else if (stageTicks > 100) {
                    throw new AssertionError("Hub auto-fulfill did not trigger within 100 ticks for order " + autoFulfillOrderId);
                }
            }

            case W6_UPGRADE_AND_EXPANSION_SETUP -> {
                runUpgradeAndExpansionSetup(server, level);
                advance(Stage.W6_WAIT_AUTO_LAUNCH_CONSTRUCTION);
            }

            case W6_WAIT_AUTO_LAUNCH_CONSTRUCTION -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord rec = data.getVillage(villageId).orElseThrow();
                Optional<ConstructionJob> activeOpt = rec.getActiveConstructionJob();
                if (activeOpt.isPresent()) {
                    ConstructionJob job = activeOpt.get();
                    qa.check("expansion.auto_launched_with_autofullfill_false",
                            job.getProjectType() == ProjectType.SMALL_COTTAGE,
                            "project=" + job.getProjectType() + ", ticks=" + stageTicks + ", jobs=" + rec.getConstructionQueue().size());
                    initialJobPlaced = job.getPlacedCount();
                    // Verify bottom-up Y sorting invariant of placements!
                    List<WorldBlockPlacement> placements = job.getPlacements();
                    qa.check("expansion.blueprint_non_empty", placements.size() >= 15, "blocks=" + placements.size());
                    boolean bottomUpSorted = true;
                    for (int i = 1; i < placements.size(); i++) {
                        if (placements.get(i).pos().y() < placements.get(i - 1).pos().y()) {
                            bottomUpSorted = false;
                            break;
                        }
                    }
                    qa.check("expansion.bottom_up_y_ordered", bottomUpSorted, "totalBlocks=" + placements.size());
                    qa.metric("expansion.job_blocks", placements.size());
                    advance(Stage.W6_WAIT_CONSTRUCTION_PROGRESS);
                } else if (stageTicks > 100) {
                    throw new AssertionError("All-delivered SMALL_COTTAGE (autoFulfill=false) did not auto-launch within 100 ticks after funds restored (bal="
                            + rec.getVillageBalance() + ", exp=" + rec.getActiveExpansion() + ")");
                }
            }

            case W6_WAIT_CONSTRUCTION_PROGRESS -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord rec = data.getVillage(villageId).orElseThrow();
                Optional<ConstructionJob> activeOpt = rec.getActiveConstructionJob();
                if (activeOpt.isPresent()) {
                    int placed = activeOpt.get().getPlacedCount();
                    if (placed > initialJobPlaced && placed < activeOpt.get().getTotalBlocks()) {
                        sawMidProgressPlaced = Math.max(sawMidProgressPlaced, placed);
                    }
                    if (stageTicks > 800) {
                        throw new AssertionError("Gradual construction did not finish within 800 ticks (placed="
                                + placed + "/" + activeOpt.get().getTotalBlocks() + ")");
                    }
                } else {
                    qa.check("expansion.gradual_progress_observed", sawMidProgressPlaced > 0,
                            "observed mid-build placedCount=" + sawMidProgressPlaced);
                    // Verify BOTH adjacent placed chests & their diamond marker items are 100% untouched!
                    verifyProtectedChestIntact(level, protectedChestPosA, "chestA");
                    verifyProtectedChestIntact(level, protectedChestPosB, "chestB");
                    qa.metric("expansion.occupied_zones", rec.getOccupiedZones().size());

                    advance(Stage.W6B_AID_SPAWN_UNEMPLOYED);
                }
            }

            case W6B_AID_SPAWN_UNEMPLOYED -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord village = data.getVillage(villageId).orElseThrow();
                UUID mayorUuid = village.getMayorState().getMayorEntityUuid();
                var scanBox = new net.minecraft.world.phys.AABB(hubPos).inflate(village.effectiveVillagerScanRadius());
                List<Villager> existing = level.getEntitiesOfClass(Villager.class, scanBox, Villager::isAlive);
                List<UUID> trackedUnemp = new ArrayList<>();
                for (Villager v : existing) {
                    if (v.isBaby() || v.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
                        continue;
                    }
                    if ((mayorUuid != null && mayorUuid.equals(v.getUUID()))
                            || v.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)) {
                        continue;
                    }
                    if (v.getVillagerData().getProfession() == VillagerProfession.NONE) {
                        trackedUnemp.add(v.getUUID());
                    }
                }
                int spawnIdx = 0;
                while (trackedUnemp.size() < 2) {
                    Villager u = spawnUnemployedVillager(level, hubPos.offset(-2 - spawnIdx, 0, -2));
                    trackedUnemp.add(u.getUUID());
                    spawnIdx++;
                }
                this.aidUnemployedUuids = List.copyOf(trackedUnemp);
                advance(Stage.W6B_AID_FUND_CANCEL_AND_PAUSE_SETUP);
            }

            case W6B_AID_FUND_CANCEL_AND_PAUSE_SETUP -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord village = data.getVillage(villageId).orElseThrow();
                VillageEmploymentStats statsBefore = village.getEmploymentStats();
                // Wait for the live 100-tick periodic villager scan to register our 2+ unemployed adult villagers!
                if (statsBefore.unemployedCount() < 2 || statsBefore.workforceCount() < 5) {
                    if (stageTicks > 130) {
                        throw new AssertionError("Live periodic villager scan did not pick up unemployed villagers within 130 ticks: "
                                + statsBefore);
                    }
                    return;
                }

                TyC2Config cfg = TyC2RuntimeData.config();
                this.aidUnemploymentBefore = statsBefore.unemploymentRate();
                this.aidExpectedGatherSpeed = statsBefore.gatherSpeedMultiplier(cfg);
                qa.check("aid.initial_unemployment_ge_25pct",
                        statsBefore.workforceCount() >= 5
                                && statsBefore.unemployedCount() >= 2
                                && statsBefore.unemploymentRate() >= cfg.lowUnemploymentThreshold()
                                && !statsBefore.isLowUnemploymentBonusActive(cfg)
                                && aidExpectedGatherSpeed > 1.0,
                        String.format("workforce=%d, unemployed=%d, rate=%.3f, gatherSpeed=%.3f",
                                statsBefore.workforceCount(), statsBefore.unemployedCount(),
                                statsBefore.unemploymentRate(), aidExpectedGatherSpeed));

                // Credit Player A's active settlement bank account & sync M before START_AID / CANCEL_AID
                BankAccountManager bank = data.bank();
                BankAccountManager.AccountRef activeRef = bank.resolveSettlementAccount(PLAYER_A_UUID);
                bank.creditAccount(activeRef, 4_000L, BankAccountManager.TransactionType.SYSTEM_ADJUST,
                        level.getGameTime(), "QA", "Aid funding capital");
                data.syncMacroAccountTotals();

                WorkplaceSpec libSpec = TyC2RuntimeData.workplaceCatalog()
                        .getByProfessionId("minecraft:librarian").orElseThrow();
                long expectedCost = WorkplaceCatalog.quoteCostIc(
                        libSpec, WorkplaceSize.SMALL, data.economy().computeCpi(), cfg
                );
                long balBeforeStart = bank.getBalance(activeRef);
                long supplyBeforeStart = data.economy().totalMoneySupply();
                long sinksBeforeStart = data.economy().cumulativeSinks();
                int zonesBeforeStart = village.getOccupiedZones().size();

                // (b) Fund Aid Construction (START_AID small minecraft:librarian) via bank
                TycoonForgeWorldManager.ActionFeedback startRes = TycoonForgeWorldManager.startAidConstruction(
                        playerA, villageId, "minecraft:librarian", WorkplaceSize.SMALL, true, null, null
                );
                qa.check("aid.start_succeeded",
                        startRes.success() && "status.tyc2.aid.started".equals(startRes.translationKey()),
                        "success=" + startRes.success() + ", key=" + startRes.translationKey());
                qa.check("aid.start_queued_and_cpi_priced",
                        village.getAidProjects().size() == 1
                                && village.getAidProjects().get(0).paidIc() == expectedCost
                                && village.getAidProjects().get(0).status() == AidConstructionProject.Status.COLLECTING,
                        "count=" + village.getAidProjects().size() + ", expectedCost=" + expectedCost
                                + ", paid=" + (village.getAidProjects().isEmpty() ? -1 : village.getAidProjects().get(0).paidIc()));
                AidConstructionProject firstAid = village.getAidProjects().get(0);
                Aabb3i firstZone = firstAid.reservedZone();
                qa.checkEquals("aid.start_bank_debited_exact", balBeforeStart - expectedCost, bank.getBalance(activeRef));
                qa.check("aid.start_pre_reserved_zone_in_occupied",
                        firstZone != null
                                && village.getOccupiedZones().contains(firstZone)
                                && village.getOccupiedZones().size() == zonesBeforeStart + 1,
                        "zone=" + firstZone + ", zones=" + zonesBeforeStart + "->" + village.getOccupiedZones().size());
                qa.check("aid.start_money_supply_m_sink_exact",
                        data.economy().totalMoneySupply() == supplyBeforeStart - expectedCost
                                && data.economy().cumulativeSinks() == sinksBeforeStart + expectedCost,
                        "M=" + supplyBeforeStart + "->" + data.economy().totalMoneySupply()
                                + ", sinks=" + sinksBeforeStart + "->" + data.economy().cumulativeSinks());

                // (c) Cancel Aid Construction (CANCEL_AID) while COLLECTING -> full refund, zone released, M conserved!
                TycoonForgeWorldManager.ActionFeedback cancelRes = TycoonForgeWorldManager.cancelAidConstruction(
                        playerA, villageId, firstAid.projectId(), true
                );
                qa.check("aid.cancel_succeeded",
                        cancelRes.success() && "status.tyc2.aid.cancelled".equals(cancelRes.translationKey()),
                        "success=" + cancelRes.success() + ", key=" + cancelRes.translationKey());
                qa.check("aid.cancel_removed_and_full_refund",
                        village.getAidProjects().isEmpty() && bank.getBalance(activeRef) == balBeforeStart,
                        "remaining=" + village.getAidProjects().size() + ", bal=" + bank.getBalance(activeRef));
                qa.check("aid.cancel_released_reserved_zone",
                        !village.getOccupiedZones().contains(firstZone)
                                && village.getOccupiedZones().size() == zonesBeforeStart,
                        "zones=" + village.getOccupiedZones().size() + " (expected=" + zonesBeforeStart + ")");
                qa.check("aid.cancel_money_supply_m_exact_conservation",
                        data.economy().totalMoneySupply() == supplyBeforeStart
                                && data.economy().cumulativeSinks() == sinksBeforeStart,
                        "M=" + data.economy().totalMoneySupply() + " (expected=" + supplyBeforeStart + ")"
                                + ", sinks=" + data.economy().cumulativeSinks() + " (expected=" + sinksBeforeStart + ")");

                // (d-setup) Pause regular expansion (expansionPaused = true), then fund Aid Construction!
                village.setExpansionPaused(true);
                data.setDirty();
                qa.check("aid.expansion_paused_enabled", village.isExpansionPaused(), "paused=true");

                TycoonForgeWorldManager.ActionFeedback start2 = TycoonForgeWorldManager.startAidConstruction(
                        playerA, villageId, "minecraft:librarian", WorkplaceSize.SMALL, true, null, null
                );
                qa.check("aid.start_while_paused_succeeds",
                        start2.success() && village.getAidProjects().size() == 1,
                        "key=" + start2.translationKey());
                AidConstructionProject activeAid = village.getAidProjects().get(0);
                this.aidProjectId = activeAid.projectId();
                this.aidFirstObservedGathered = -1.0;
                this.aidBuildingStarted = false;
                this.aidWorkstationPositions = List.of();

                // (f-1) Jade/TOP HUD bridge lines while Aid is COLLECTING
                List<Component> hubHudCollecting = JadeSoftBridge.buildBlockHudLines(level, hubPos);
                verifyHudLine("hud.hub_unemployment_collecting", hubHudCollecting, "hud.tyc2.hub.unemployment");
                verifyHudLine("hud.hub_aid_active_collecting", hubHudCollecting, "hud.tyc2.hub.aid_active");
                Villager mayorEnt = findVillagerByUuid(level, hubPos, village.getMayorState().getMayorEntityUuid());
                qa.check("hud.mayor_entity_found", mayorEnt != null, "mayor=" + mayorEnt);
                List<Component> mayorHud = JadeSoftBridge.buildEntityHudLines(level, mayorEnt);
                verifyHudLine("hud.mayor_unemployment", mayorHud, "hud.tyc2.mayor.unemployment");

                advance(Stage.W6B_AID_WAIT_GATHERING);
            }

            case W6B_AID_WAIT_GATHERING -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord village = data.getVillage(villageId).orElseThrow();
                AidConstructionProject proj = village.findAidProject(aidProjectId)
                        .orElseThrow(() -> new AssertionError("Aid project missing during gathering: " + aidProjectId));
                double gathered = proj.gatheredProgressTicks();
                if (aidFirstObservedGathered < 0.0) {
                    if (gathered > 0.0) {
                        aidFirstObservedGathered = gathered;
                    } else if (stageTicks > 40) {
                        throw new AssertionError("Aid background gathering did not tick within 40 ticks");
                    }
                    return;
                }
                if (gathered > aidFirstObservedGathered + 0.01) {
                    double stepDelta = gathered - aidFirstObservedGathered;
                    double expectedStep = 20.0 * aidExpectedGatherSpeed;
                    qa.check("aid.gathering_advances_accelerated_while_paused",
                            village.isExpansionPaused()
                                    && proj.status() == AidConstructionProject.Status.COLLECTING
                                    && stepDelta > 20.0
                                    && Math.abs(stepDelta - expectedStep) < 0.05,
                            String.format("paused=%s, stepDelta=%.2f, expected=%.2f (speed=%.3fx)",
                                    village.isExpansionPaused(), stepDelta, expectedStep, aidExpectedGatherSpeed));

                    // Fast-forward virtual gathering to 1 real cycle before completion so live server transitions to BUILDING
                    double advanceBy = Math.max(0.0, (proj.requiredTicks() - 10.0) - proj.gatheredProgressTicks());
                    village.updateAidProject(proj.advanceGathering(advanceBy));
                    data.setDirty();
                    advance(Stage.W6B_AID_WAIT_BUILD);
                } else if (stageTicks > 80) {
                    throw new AssertionError("Aid gathering did not advance a second 20-tick step within 80 ticks (gathered=" + gathered + ")");
                }
            }

            case W6B_AID_WAIT_BUILD -> {
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                VillageRecord village = data.getVillage(villageId).orElseThrow();
                TyC2Config cfg = TyC2RuntimeData.config();
                Optional<AidConstructionProject> aidOpt = village.findAidProject(aidProjectId);
                Optional<ConstructionJob> activeJobOpt = village.getActiveConstructionJob();

                if (!aidBuildingStarted) {
                    if (aidOpt.isPresent()
                            && aidOpt.get().status() == AidConstructionProject.Status.BUILDING
                            && activeJobOpt.isPresent()) {
                        aidBuildingStarted = true;
                        AidConstructionProject buildingProj = aidOpt.get();
                        ConstructionJob job = activeJobOpt.get();
                        qa.check("aid.transitioned_to_building_with_priority_while_paused",
                                village.isExpansionPaused()
                                        && job.getProjectType() == ProjectType.AID_WORKPLACE_SMALL
                                        && job.getJobId().equals(buildingProj.plannedJobId()),
                                "jobId=" + job.getJobId() + ", type=" + job.getProjectType()
                                        + ", blocks=" + job.getTotalBlocks());

                        List<BlockPos> wsPosList = new ArrayList<>();
                        for (SpawnMarker.WorldSpawnMarker m : job.getSpawnMarkers()) {
                            if (m.kind() == SpawnMarker.MarkerKind.WORKSTATION) {
                                wsPosList.add(TyC2Ids.toMc(m.pos()));
                            }
                        }
                        this.aidWorkstationPositions = List.copyOf(wsPosList);
                        qa.check("aid.job_has_workstation_marker", !aidWorkstationPositions.isEmpty(),
                                "markers=" + aidWorkstationPositions);

                        // Cancelling while BUILDING MUST be rejected!
                        TycoonForgeWorldManager.ActionFeedback cancelBuild = TycoonForgeWorldManager.cancelAidConstruction(
                                playerA, villageId, aidProjectId, true
                        );
                        qa.check("aid.cancel_rejected_while_building",
                                !cancelBuild.success()
                                        && "status.tyc2.aid.cannot_cancel".equals(cancelBuild.translationKey())
                                        && village.findAidProject(aidProjectId).isPresent(),
                                "key=" + cancelBuild.translationKey());
                    } else if (stageTicks > 80) {
                        throw new AssertionError("Aid project did not transition to BUILDING within 80 ticks (aid="
                                + aidOpt + ", activeJob=" + activeJobOpt + ")");
                    }
                    return;
                }

                if (activeJobOpt.isPresent()) {
                    if (stageTicks > 800) {
                        throw new AssertionError("Aid construction timed out at "
                                + activeJobOpt.get().getPlacedCount() + "/" + activeJobOpt.get().getTotalBlocks());
                    }
                    return;
                }

                // Completed! Verify workstation block, protected chests, auto-assignment, unemployment < 25%, & bonus!
                qa.check("aid.completed_removed_from_queue", village.findAidProject(aidProjectId).isEmpty(),
                        "remainingAid=" + village.getAidProjects().size());
                boolean allWorkstationsPlaced = !aidWorkstationPositions.isEmpty()
                        && aidWorkstationPositions.stream()
                        .allMatch(p -> "minecraft:lectern".equals(TyC2Ids.blockId(level.getBlockState(p).getBlock())));
                qa.check("aid.workstation_block_placed_exact", allWorkstationsPlaced,
                        "positions=" + aidWorkstationPositions + ", blocks=" + aidWorkstationPositions.stream()
                                .map(p -> TyC2Ids.blockId(level.getBlockState(p).getBlock())).toList());
                verifyProtectedChestIntact(level, protectedChestPosA, "aid.chestA");
                verifyProtectedChestIntact(level, protectedChestPosB, "aid.chestB");

                var scanBox = new net.minecraft.world.phys.AABB(hubPos).inflate(village.effectiveVillagerScanRadius());
                List<Villager> postVillagers = level.getEntitiesOfClass(Villager.class, scanBox, Villager::isAlive);
                long newlyAssignedLibrarians = postVillagers.stream()
                        .filter(v -> aidUnemployedUuids.contains(v.getUUID())
                                && v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN)
                        .count();
                VillageEmploymentStats statsAfter = village.getEmploymentStats();
                qa.check("aid.auto_assigned_unemployed_villager",
                        newlyAssignedLibrarians >= 1,
                        "newlyAssignedLibrarians=" + newlyAssignedLibrarians);
                qa.check("aid.unemployment_dropped_below_25pct",
                        statsAfter.unemploymentRate() < aidUnemploymentBefore
                                && statsAfter.unemploymentRate() < cfg.lowUnemploymentThreshold()
                                && statsAfter.isLowUnemploymentBonusActive(cfg),
                        String.format("before=%.3f -> after=%.3f (unemployed=%d/%d)",
                                aidUnemploymentBefore, statsAfter.unemploymentRate(),
                                statsAfter.unemployedCount(), statsAfter.workforceCount()));

                // (e) Low-unemployment (<25%) bonus: fixed weekly refresh interval + boosted trade quantity!
                int effectiveRefresh = statsAfter.effectiveDemandRefreshTicks(cfg);
                qa.check("employment_bonus.refresh_interval_fixed_weekly",
                        effectiveRefresh == Math.max(200, cfg.demandRefreshTicks()),
                        "effective=" + effectiveRefresh + " == base=" + cfg.demandRefreshTicks());

                DemandGenerator dg = TyC2RuntimeData.demandGenerator();
                int baselineQtySum = 0;
                int boostedQtySum = 0;
                for (int seed = 101; seed <= 108; seed++) {
                    DemandGenerator.GeneratedDemand baseDem = dg.generate(
                            village.getLevel(), village.getBiomeStyle().id(), village.getVillageBalance(),
                            IndustrialTier.TIER_0, Season.NONE, data.economy(),
                            TyC2Ids::itemExists, VillageEmploymentStats.EMPTY,
                            TyC2RuntimeData.workplaceCatalog(), new Random(seed)
                    );
                    DemandGenerator.GeneratedDemand boostDem = dg.generate(
                            village.getLevel(), village.getBiomeStyle().id(), village.getVillageBalance(),
                            IndustrialTier.TIER_0, Season.NONE, data.economy(),
                            TyC2Ids::itemExists, statsAfter,
                            TyC2RuntimeData.workplaceCatalog(), new Random(seed)
                    );
                    baselineQtySum += baseDem.allOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
                    boostedQtySum += boostDem.allOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
                }
                qa.check("employment_bonus.trade_quantity_boosted",
                        statsAfter.tradeQuantityMultiplier(cfg) >= cfg.lowUnempQtyMinMult()
                                && boostedQtySum > (int) Math.round(baselineQtySum * 1.15),
                        String.format("qtyMult=%.3f, baseQtySum=%d -> boostedQtySum=%d (ratio=%.2fx)",
                                statsAfter.tradeQuantityMultiplier(cfg), baselineQtySum, boostedQtySum,
                                baselineQtySum > 0 ? (double) boostedQtySum / baselineQtySum : 0.0));

                TycoonForgeWorldManager.refreshVillageOrders(level, data, village, level.getDayTime());
                qa.check("employment_bonus.live_refresh_orders_present",
                        !village.getActiveOrders().isEmpty(),
                        "orders=" + village.getActiveOrders().size());

                // (f-2) Jade/TOP HUD bridge lines AFTER unemployment < 25% now emit employment_bonus!
                List<Component> hubHudAfter = JadeSoftBridge.buildBlockHudLines(level, hubPos);
                verifyHudLine("hud.hub_unemployment_after_aid", hubHudAfter, "hud.tyc2.hub.unemployment");
                verifyHudLine("hud.hub_employment_bonus_active", hubHudAfter, "hud.tyc2.hub.employment_bonus");

                // Queue 1 small mason aid project right before shutdown to verify cold-restart NBT persistence!
                TycoonForgeWorldManager.ActionFeedback persistStart = TycoonForgeWorldManager.startAidConstruction(
                        playerA, villageId, "minecraft:mason", WorkplaceSize.SMALL, true, null, null
                );
                qa.check("aid.persist_sentinel_queued",
                        persistStart.success() && village.getAidProjects().size() == 1
                                && "minecraft:mason".equals(village.getAidProjects().get(0).professionId()),
                        "key=" + persistStart.translationKey() + ", count=" + village.getAidProjects().size());
                AidConstructionProject persistAid = village.getAidProjects().get(0);
                this.aidPersistProjectId = persistAid.projectId();
                this.aidPersistPaidIc = persistAid.paidIc();

                if (enhanced) {
                    advance(Stage.W7_ENHANCED_IC2_LC2_SETUP);
                } else {
                    advance(Stage.W8_SAVE_AND_HALT);
                }
            }

            case W7_ENHANCED_IC2_LC2_SETUP -> {
                runEnhancedIc2Lc2Setup(server, level);
                advance(Stage.W7_ENHANCED_IC2_LC2_WAIT);
            }

            case W7_ENHANCED_IC2_LC2_WAIT -> {
                if (stageTicks < 60) {
                    return;
                }
                runEnhancedIc2Lc2Verify(server, level);
                advance(Stage.W8_SAVE_AND_HALT);
            }

            case W8_SAVE_AND_HALT -> {
                writeExpectedSnapshot(server, level);
                server.saveEverything(false, true, true);
                qa.writePassedReport(reportPath(), Map.of("profile", profile, "phase", phase));
                finished = true;
                advance(Stage.DONE);
                TyC2QaMod.LOGGER.info("[TYC2-QA] Write phase PASSED ({} checks). Halting server cleanly for cold restart.",
                        qa.passedCount());
                server.halt(false);
            }

            case R1_VERIFY_RELOADED -> {
                runColdRestartReadVerification(server, level);
                qa.writePassedReport(reportPath(), Map.of("profile", profile, "phase", phase));
                finished = true;
                advance(Stage.DONE);
                TyC2QaMod.LOGGER.info("[TYC2-QA] Read phase PASSED ({} checks). Halting server cleanly.",
                        qa.passedCount());
                server.halt(false);
            }

            case DONE -> {
            }
        }
    }

    private void runBootAndRegistryChecks(MinecraftServer server, ServerLevel level) {
        qa.check("boot.tyc2_loaded", ModList.get().isLoaded("tyc2"), "mod tyc2 loaded");
        qa.checkEquals("boot.ic2_loaded_matches_profile", enhanced, ModList.get().isLoaded("ic2"));
        qa.checkEquals("boot.lc2_loaded_matches_profile", enhanced, ModList.get().isLoaded("lc2"));
        qa.checkEquals("boot.ic2_bridge_matches_profile", enhanced, Ic2Bridge.isLoaded());
        qa.checkEquals("boot.lc2_bridge_matches_profile", enhanced, Lc2SoftBridge.isLoaded());

        // External configs auto-created
        Path cfgDir = TyC2RuntimeData.configDir();
        qa.check("config.general_json_created", Files.isRegularFile(cfgDir.resolve("general.json")),
                cfgDir.resolve("general.json").toAbsolutePath().toString());
        qa.check("config.custom_goods_json_created", Files.isRegularFile(cfgDir.resolve("custom_goods.json")),
                cfgDir.resolve("custom_goods.json").toAbsolutePath().toString());
        qa.check("config.custom_mint_recipes_json_created",
                Files.isRegularFile(cfgDir.resolve("custom_mint_recipes.json")),
                cfgDir.resolve("custom_mint_recipes.json").toAbsolutePath().toString());
        qa.check("config.custom_workplaces_json_created",
                Files.isRegularFile(cfgDir.resolve("custom_workplaces.json")),
                cfgDir.resolve("custom_workplaces.json").toAbsolutePath().toString());

        // Datapacks loaded
        int recipeCount = TyC2RuntimeData.mintRecipes().allRecipes().size();
        int goodsCount = TyC2RuntimeData.goodsCatalog().allGoods().size();
        int levelsCount = TyC2RuntimeData.levelTable().allLevels().size();
        qa.check("datapack.mint_recipes_loaded", recipeCount >= 17, "recipes=" + recipeCount);
        qa.check("datapack.goods_catalog_loaded", goodsCount >= 60, "goods=" + goodsCount);
        qa.checkEquals("datapack.village_levels_10", 10, levelsCount);
        qa.metric("datapack.mintRecipes", recipeCount);
        qa.metric("datapack.goods", goodsCount);

        // (a) Workplace datapack loading (all 13 vanilla professions + JUNGLE & SWAMP styles valid)
        List<String> expectedVanillaProfs = List.of(
                "minecraft:farmer", "minecraft:fisherman", "minecraft:shepherd", "minecraft:fletcher",
                "minecraft:butcher", "minecraft:leatherworker", "minecraft:mason", "minecraft:cartographer",
                "minecraft:toolsmith", "minecraft:weaponsmith", "minecraft:armorer", "minecraft:cleric",
                "minecraft:librarian"
        );
        Set<String> availableProfs = TyC2RuntimeData.workplaceCatalog()
                .getAvailableWorkplaces(TyC2Ids::blockExists)
                .stream().map(WorkplaceSpec::professionId).collect(Collectors.toSet());
        List<String> missingProfs = expectedVanillaProfs.stream()
                .filter(p -> !availableProfs.contains(p)).toList();
        qa.check("datapack.workplaces_all_13_vanilla",
                missingProfs.isEmpty() && availableProfs.size() >= 13,
                "available=" + availableProfs.size() + ", missing=" + missingProfs);
        qa.check("biome.seven_styles_incl_jungle_swamp",
                BiomeStyle.values().length == 7
                        && BiomeStyle.fromBiomeId("minecraft:bamboo_jungle") == BiomeStyle.JUNGLE
                        && BiomeStyle.fromBiomeId("minecraft:mangrove_swamp") == BiomeStyle.SWAMP,
                "count=" + BiomeStyle.values().length);
        WorkplaceSpec libSpec = TyC2RuntimeData.workplaceCatalog()
                .getByProfessionId("minecraft:librarian").orElseThrow();
        boolean jungleSwampBlocksValid = true;
        for (BiomeStyle style : List.of(BiomeStyle.JUNGLE, BiomeStyle.SWAMP)) {
            StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(libSpec, WorkplaceSize.SMALL, style);
            boolean hasWsMarker = bp.spawnMarkers().stream()
                    .anyMatch(m -> m.kind() == SpawnMarker.MarkerKind.WORKSTATION
                            && "minecraft:librarian".equals(m.hint()));
            boolean allBlocksExist = bp.placements().stream()
                    .map(p -> {
                        String specStr = p.blockStateSpec();
                        int br = specStr.indexOf('[');
                        return br >= 0 ? specStr.substring(0, br) : specStr;
                    })
                    .filter(id -> !"minecraft:air".equals(id))
                    .allMatch(TyC2Ids::blockExists);
            if (!hasWsMarker || !allBlocksExist) {
                jungleSwampBlocksValid = false;
            }
        }
        qa.check("biome.jungle_swamp_workplace_blueprints_valid", jungleSwampBlocksValid,
                "JUNGLE & SWAMP blueprint blocks & WORKSTATION markers verified");

        // Registries: 4 blocks, 4 BEs, 14 items, 6 menus, 1 POI
        for (String b : List.of("primitive_mint_machine", "mint_machine", "atm_machine", "cooperation_hub")) {
            qa.check("registry.block." + b, ForgeRegistries.BLOCKS.containsKey(TyC2Ids.id(b)), b);
            qa.check("registry.be." + b, ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(TyC2Ids.id(b)), b);
        }
        for (CurrencyDenomination d : CurrencyDenomination.values()) {
            ResourceLocation rl = TyC2Ids.id(d.itemPath());
            qa.check("registry.coin." + d.itemPath(), ForgeRegistries.ITEMS.containsKey(rl), rl.toString());
            ItemStack st = new ItemStack(ForgeRegistries.ITEMS.getValue(rl), 1);
            qa.checkEquals("coin.value." + d.itemPath(), d.value(), TyC2Ids.coinStackValue(st));
        }
        qa.check("registry.item.comm_terminal", ForgeRegistries.ITEMS.containsKey(TyC2Ids.id("comm_terminal")), "comm_terminal");
        for (String m : List.of("primitive_mint_machine", "mint_machine", "atm_machine", "cooperation_hub", "comm_terminal", "mayor_negotiation")) {
            qa.check("registry.menu." + m, ForgeRegistries.MENU_TYPES.containsKey(TyC2Ids.id(m)), m);
        }
        qa.check("registry.poi.cooperation_hub", ForgeRegistries.POI_TYPES.containsKey(TyC2Ids.id("cooperation_hub")), "poi");

        // Conditional Crafting Recipes loaded in RecipeManager (primitive always; mint_machine_ic2 vs mint_machine_standalone!)
        var rm = server.getRecipeManager();
        Optional<? extends Recipe<?>> primRec = rm.byKey(TyC2Ids.id("primitive_mint_machine"));
        Optional<? extends Recipe<?>> ic2MintRec = rm.byKey(TyC2Ids.id("mint_machine_ic2"));
        Optional<? extends Recipe<?>> standaloneMintRec = rm.byKey(TyC2Ids.id("mint_machine_standalone"));
        Optional<? extends Recipe<?>> staleMintRec = rm.byKey(TyC2Ids.id("mint_machine"));
        qa.check("recipe.primitive_mint_loaded",
                primRec.isPresent() && TyC2Ids.itemId(primRec.get().getResultItem()).equals("tyc2:primitive_mint_machine"),
                "present=" + primRec.isPresent());
        qa.check("recipe.stale_unconditional_mint_absent", staleMintRec.isEmpty(), "stale=" + staleMintRec.isPresent());
        if (enhanced) {
            qa.check("recipe.enhanced.mint_machine_ic2_loaded", ic2MintRec.isPresent(), "present=" + ic2MintRec.isPresent());
            qa.check("recipe.enhanced.mint_machine_standalone_not_loaded", standaloneMintRec.isEmpty(), "present=" + standaloneMintRec.isPresent());
            if (ic2MintRec.isPresent()) {
                for (String reqId : List.of("tyc2:primitive_mint_machine", "ic2:machine", "ic2:circuit", "ic2:iron_plate", "ic2:electric_motor")) {
                    qa.check("recipe.enhanced.ic2_mint_requires." + reqId,
                            recipeHasIngredient(ic2MintRec.get(), reqId), reqId);
                }
            }
        } else {
            qa.check("recipe.standalone.mint_machine_standalone_loaded", standaloneMintRec.isPresent(), "present=" + standaloneMintRec.isPresent());
            qa.check("recipe.standalone.mint_machine_ic2_not_loaded", ic2MintRec.isEmpty(), "present=" + ic2MintRec.isPresent());
            if (standaloneMintRec.isPresent()) {
                for (String reqId : List.of("tyc2:primitive_mint_machine", "minecraft:diamond", "minecraft:redstone_block")) {
                    qa.check("recipe.standalone.mint_requires." + reqId,
                            recipeHasIngredient(standaloneMintRec.get(), reqId), reqId);
                }
            }
        }

        // Commands work (/tyc2 status, /tyc2 cpi, /tyc2 season, /tyc2 villages)
        CommandSourceStack src = server.createCommandSourceStack();
        int statusRc = server.getCommands().performPrefixedCommand(src, "tyc2 status");
        int cpiRc = server.getCommands().performPrefixedCommand(src, "tyc2 cpi");
        int seasonRc = server.getCommands().performPrefixedCommand(src, "tyc2 season");
        int villagesRc = server.getCommands().performPrefixedCommand(src, "tyc2 villages");
        qa.check("command.tyc2_status", statusRc > 0, "rc=" + statusRc);
        qa.check("command.tyc2_cpi", cpiRc > 0, "rc=" + cpiRc);
        qa.check("command.tyc2_season", seasonRc > 0, "rc=" + seasonRc);
        qa.check("command.tyc2_villages", villagesRc > 0, "rc=" + villagesRc);

        // Create two FakePlayers for banking & negotiation
        playerA = FakePlayerFactory.get(level, new GameProfile(PLAYER_A_UUID, "TyC2TesterA"));
        playerB = FakePlayerFactory.get(level, new GameProfile(PLAYER_B_UUID, "TyC2TesterB"));
        playerA.setPos(hubPos.getX() + 1.5, surfaceY, hubPos.getZ() + 1.5);
        playerB.setPos(hubPos.getX() - 1.5, surfaceY, hubPos.getZ() + 1.5);
    }

    private static boolean recipeHasIngredient(Recipe<?> recipe, String itemId) {
        Item item = TyC2Ids.getItem(itemId);
        if (item == null) {
            return false;
        }
        ItemStack probe = new ItemStack(item, 1);
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.test(probe)) {
                return true;
            }
        }
        return false;
    }

    private void runMintSetup(ServerLevel level) {
        // 1. Primitive Minting Machine: NO FE capability, accepts coal fuel, mints Vanilla 1x-100x at 1.5x speed!
        level.setBlock(primMintPos, TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        PrimitiveMintMachineBlockEntity prim = (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
        qa.check("primitive_mint.be_placed", prim != null, "pos=" + primMintPos);
        boolean primHasAnyFe = prim.getCapability(ForgeCapabilities.ENERGY, null).isPresent();
        for (Direction d : Direction.values()) {
            if (prim.getCapability(ForgeCapabilities.ENERGY, d).isPresent()) {
                primHasAnyFe = true;
            }
        }
        qa.check("primitive_mint.no_fe_energy_capability", !primHasAnyFe, "hasFe=" + primHasAnyFe);
        qa.check("primitive_mint.accepts_coal_fuel",
                PrimitiveMintMachineBlockEntity.isBurnableFuel(new ItemStack(Items.COAL))
                        && prim.canPlaceItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL)),
                "coal accepted");
        primMintStartTick = level.getGameTime();
        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 2));

        // 2. Pure-Electric Industrial Minting Machine: exposes FE, STRICTLY rejects coal/charcoal/redstone/blaze_rod!
        level.setBlock(mintCoalPos, TyC2ForgeRegistries.MINT_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintCoalPos);
        qa.check("mint.be_placed", mint != null, "pos=" + mintCoalPos);
        qa.check("electric_mint.core_disallows_solid_fuel", !TriModePowerMath.MINT_ACCEPTS_SOLID_FUEL,
                "MINT_ACCEPTS_SOLID_FUEL=" + TriModePowerMath.MINT_ACCEPTS_SOLID_FUEL);
        qa.check("electric_mint.rejects_coal_in_slot3",
                !MintMachineBlockEntity.isValidBatteryItem(new ItemStack(Items.COAL), 1)
                        && !mint.canPlaceItem(MintMachineBlockEntity.SLOT_BATTERY, new ItemStack(Items.COAL)),
                "coal rejected");
        qa.check("electric_mint.rejects_charcoal_redstone_blaze",
                !MintMachineBlockEntity.isValidBatteryItem(new ItemStack(Items.CHARCOAL), 1)
                        && !MintMachineBlockEntity.isValidBatteryItem(new ItemStack(Items.REDSTONE), 1)
                        && !MintMachineBlockEntity.isValidBatteryItem(new ItemStack(Items.BLAZE_ROD), 1),
                "solid fuels rejected");

        // Verify Forge FE capability (1 EU = 4 FE)
        IEnergyStorage fe = mint.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElse(null);
        qa.check("mint.fe_capability_exposed", fe != null && fe.canReceive(), "fe=" + fe);
        int acceptedFe = fe.receiveEnergy(128, false);
        qa.checkEquals("mint.fe_receive_128_eq_32eu", 128, acceptedFe);
        qa.check("mint.fe_converted_to_32eu", Math.abs(mint.getStoredEu() - 32.0) < 0.01,
                "storedEu=" + mint.getStoredEu());

        // Reset EU to 0 and force-place coal in Slot 3 for 15 ticks to prove ZERO solid fuel burning!
        mint.setStoredEu(0.0);
        cpiBeforeMint = TycoonForgeSavedData.get(level).economy().computeCpi();
        mint.setItem(MintMachineBlockEntity.SLOT_BATTERY, new ItemStack(Items.COAL, 4));
        mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 2));
    }

    private void runAtmAndBankingChecks(MinecraftServer server, ServerLevel level) {
        level.setBlock(atmPos, TyC2ForgeRegistries.ATM_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        AtmMachineBlockEntity atm = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
        qa.check("atm.be_placed", atm != null, "pos=" + atmPos);

        // Deposit 5x coin_100 = 500 IC into Player A personal account via ATM slot
        atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, new ItemStack(TyC2ForgeRegistries.COIN_100.get(), 5));
        atm.handleDepositSlot(playerA);
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        BankAccountManager bank = data.bank();
        qa.checkEquals("atm.personal_deposit_500", 500L, bank.getBalance(BankAccountManager.AccountRef.personal(PLAYER_A_UUID)));

        // Withdraw 186 IC -> optimal greedy coins (100+50+20+10+5+1) and balance becomes 314
        playerA.getInventory().clearContent();
        atm.handleWithdrawAmount(playerA, 186L);
        qa.checkEquals("atm.personal_withdraw_balance", 314L, bank.getBalance(BankAccountManager.AccountRef.personal(PLAYER_A_UUID)));
        long withdrawnCash = TyC2Ids.coinStackValue(atm.getItem(AtmMachineBlockEntity.SLOT_WITHDRAW))
                + TyC2Ids.countPlayerCash(playerA);
        qa.checkEquals("atm.withdraw_exact_cash_186", 186L, withdrawnCash);

        // Check canonical 1x coin dispensed by ATM withdrawal (ic2:coin in enhanced, tyc2:coin_1 in standalone)
        String expected1x = enhanced ? CurrencyDenomination.IC2_COIN_ID : "tyc2:coin_1";
        boolean foundCanonical1x = TyC2Ids.itemId(atm.getItem(AtmMachineBlockEntity.SLOT_WITHDRAW)).equals(expected1x);
        for (int i = 0; i < playerA.getInventory().getContainerSize(); i++) {
            if (TyC2Ids.itemId(playerA.getInventory().getItem(i)).equals(expected1x)) {
                foundCanonical1x = true;
            }
        }
        qa.check("atm.withdraw_canonical_1x_coin", foundCanonical1x, "expected1x=" + expected1x);

        // Create Guild/Company account, join via invite code, switch settlement, deposit & transfer!
        BankAccountManager.CompanyAccount company = bank.createCompany(PLAYER_A_UUID, "TyC2TesterA", "Nova Trade Co");
        companyId = company.companyId();
        qa.check("atm.company_created", companyId != null && !company.inviteCode().isEmpty(),
                "id=" + companyId + ", invite=" + company.inviteCode());

        bank.getOrCreatePersonal(PLAYER_B_UUID, "TyC2TesterB");
        BankAccountManager.BankResult joinRes = bank.joinCompanyByInviteCode(
                PLAYER_B_UUID, "TyC2TesterB", company.inviteCode()
        );
        qa.check("atm.company_join_by_invite", joinRes.success(), "error=" + joinRes.errorKey());

        // Transfer 100 IC from Player A personal to Player B personal, and 150 IC to Nova Trade Co
        long tick = level.getGameTime();
        BankAccountManager.BankResult tPlayer = bank.transferToPlayer(PLAYER_A_UUID, PLAYER_B_UUID, 100L, tick, "QA P2P");
        long bBal = bank.getBalance(BankAccountManager.AccountRef.personal(PLAYER_B_UUID));
        qa.check("atm.transfer_to_player", tPlayer.success() && bBal == 100L,
                "playerB=" + bBal);
        BankAccountManager.BankResult tCo = bank.transferToCompany(PLAYER_A_UUID, companyId, 150L, tick, "QA Seed Co");
        qa.check("atm.transfer_to_company",
                tCo.success() && bank.getCompany(companyId).orElseThrow().balance() == 150L,
                "companyBal=" + bank.getCompany(companyId).orElseThrow().balance());

        // Switch Player A active settlement account to Company & credit working capital for trades
        bank.setActiveCompany(PLAYER_A_UUID, companyId);
        bank.creditAccount(BankAccountManager.AccountRef.company(companyId), 5000L,
                BankAccountManager.TransactionType.SYSTEM_ADJUST, tick, "QA", "Working capital");
        data.syncMacroAccountTotals();
        data.setDirty();
    }

    private void runPartnershipHubAndIsolationChecks(MinecraftServer server, ServerLevel level) {
        // Give Player A full negotiation supplies: 100+ IC cash, 32 oak logs, 16 iron ingots, 32 bread
        playerA.getInventory().clearContent();
        playerA.getInventory().add(new ItemStack(TyC2ForgeRegistries.COIN_100.get(), 2));
        playerA.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
        playerA.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        playerA.getInventory().add(new ItemStack(Items.BREAD, 32));

        TycoonForgeWorldManager.ActionFeedback signRes = TycoonForgeWorldManager.signPartnership(playerA, villageId);
        qa.check("partnership.signed", signRes.success(), "key=" + signRes.translationKey());
        qa.check("partnership.bell_replaced_with_hub",
                level.getBlockState(hubPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get()),
                "block=" + level.getBlockState(hubPos));

        // Normal non-mayor villager emerald trading MUST be blocked!
        Villager nonMayor = findNonMayorVillager(level, hubPos);
        qa.check("village.non_mayor_exists", nonMayor != null, "nonMayor=" + nonMayor);
        PlayerInteractEvent.EntityInteract interactEvt = new PlayerInteractEvent.EntityInteract(
                playerA, InteractionHand.MAIN_HAND, nonMayor
        );
        MinecraftForge.EVENT_BUS.post(interactEvt);
        qa.check("village.normal_villager_emerald_trade_blocked", interactEvt.isCanceled(),
                "canceled=" + interactEvt.isCanceled());

        // Blast-proof & mining protection check!
        Block hubBlock = level.getBlockState(hubPos).getBlock();
        qa.check("hub.blast_resistance_3600000", hubBlock.getExplosionResistance() >= 3_600_000.0F,
                "resistance=" + hubBlock.getExplosionResistance());
        qa.check("hub.unbreakable_hardness",
                level.getBlockState(hubPos).getDestroySpeed(level, hubPos) < 0.0F,
                "destroySpeed=" + level.getBlockState(hubPos).getDestroySpeed(level, hubPos));

        // Detonate real TNT-strength explosion right next to Hub -> Hub MUST survive!
        for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(hubPos).inflate(16.0))) {
            v.setInvulnerable(true);
        }
        level.explode(null, hubPos.getX() + 0.5, hubPos.getY() + 1.5, hubPos.getZ() + 0.5, 4.0F, Explosion.BlockInteraction.BREAK);
        qa.check("hub.survives_explosion",
                level.getBlockState(hubPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get()),
                "blockAfterExplosion=" + level.getBlockState(hubPos));
        // Restore flat grass ground crater around Hub so subsequent expansion test has clean terrain
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(new BlockPos(hubPos.getX() + dx, surfaceY - 1, hubPos.getZ() + dz),
                        Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(hubPos.getX() + dx, surfaceY - 2, hubPos.getZ() + dz),
                        Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        // Non-OP survival break event MUST be canceled!
        BlockEvent.BreakEvent breakEvt = new BlockEvent.BreakEvent(level, hubPos, level.getBlockState(hubPos), playerB);
        MinecraftForge.EVENT_BUS.post(breakEvt);
        qa.check("hub.break_event_canceled_for_survival", breakEvt.isCanceled(), "canceled=" + breakEvt.isCanceled());

        // 1,000-slot Inbound/Outbound pipe isolation check!
        CooperationHubBlockEntity hubBe = (CooperationHubBlockEntity) level.getBlockEntity(hubPos);
        qa.check("hub.be_bound_to_village", hubBe != null && villageId.equals(hubBe.resolveVillageId()),
                "vid=" + (hubBe == null ? "null" : hubBe.resolveVillageId()));

        IItemHandler handler = hubBe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElse(null);
        qa.check("hub.handler_1000_slots", handler != null && handler.getSlots() == 1000,
                "slots=" + (handler == null ? -1 : handler.getSlots()));

        // Insert into Inbound slot 0 -> SUCCEEDS
        ItemStack coalRem = handler.insertItem(0, new ItemStack(Items.COAL, 32), false);
        qa.check("hub.inbound_insert_succeeds", coalRem.isEmpty(), "rem=" + coalRem);
        // Extract from Inbound slot 0 -> REJECTED (zero loop-back!)
        ItemStack loopExtract = handler.extractItem(0, 16, false);
        qa.check("hub.inbound_extract_blocked_zero_loopback", loopExtract.isEmpty(), "extracted=" + loopExtract);
        // Insert into Outbound slot 500 -> REJECTED!
        ItemStack badOutboundInsert = handler.insertItem(500, new ItemStack(Items.DIRT, 16), false);
        qa.checkEquals("hub.outbound_insert_blocked", 16, badOutboundInsert.getCount());

        // Execute a Village Sell Order -> populates Outbound 500..999 -> External extract from Outbound SUCCEEDS!
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        VillageRecord village = data.getVillage(villageId).orElseThrow();
        VillageTradeOrder sellOrder = village.getActiveOrders().stream()
                .filter(o -> o.side() == OrderSide.VILLAGE_SELLS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No initial Lv 1 Sell Orders generated!"));
        TycoonForgeWorldManager.ActionFeedback buyRes = TycoonForgeWorldManager.executeSellOrder(
                playerA, villageId, sellOrder.orderId(), Math.min(4, sellOrder.remainingQty()), true, null
        );
        qa.check("hub.sell_order_executed_to_outbound", buyRes.success(), "key=" + buyRes.translationKey());
        ItemStack extractedOutbound = ItemStack.EMPTY;
        for (int s = CooperationHubStorage.OUTBOUND_START; s < CooperationHubStorage.OUTBOUND_END_EXCLUSIVE; s++) {
            ItemStack ex = handler.extractItem(s, 1, false);
            if (!ex.isEmpty()) {
                extractedOutbound = ex;
                break;
            }
        }
        qa.check("hub.outbound_extract_succeeds", !extractedOutbound.isEmpty(),
                "extractedOutbound=" + extractedOutbound);

        // Setup Buy-Order delivery & accurate village-funds error message verification (#73)!
        village.adjustVillageBalance(5000L);
        String qaCopperOrderId = "qa_buy_copper_" + level.getGameTime();
        List<VillageTradeOrder> withQaOrder = new ArrayList<>(village.getActiveOrders());
        withQaOrder.add(new VillageTradeOrder(
                qaCopperOrderId, OrderSide.VILLAGE_BUYS, "minecraft:copper_ingot", "vanilla_copper_ingot",
                "", 10, 0, 25L, level.getDayTime(), level.getDayTime() + 168_000L, false, false, false
        ));
        village.setActiveOrders(withQaOrder);

        // (b) Comm Terminal SELL_ORDER with 0 matching Inbound items while village has plenty of funds
        // -> MUST return status.tyc2.terminal.insufficient_goods (NOT village_low_funds) AND NOT deduct 50 EU!
        ItemStack termGuard = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(termGuard, 5000);
        TycoonForgeWorldManager.ActionFeedback termNoGoods = TycoonForgeWorldManager.executeBuyOrder(
                playerA, villageId, qaCopperOrderId, 4, true, false, termGuard
        );
        qa.check("trade.terminal_missing_goods_exact_error",
                !termNoGoods.success() && "status.tyc2.terminal.insufficient_goods".equals(termNoGoods.translationKey()),
                "success=" + termNoGoods.success() + ", key=" + termNoGoods.translationKey());
        qa.checkEquals("trade.terminal_missing_goods_preserves_50eu", 5000, CommTerminalItem.getStoredEu(termGuard));

        // (c) Hub SELL_ORDER when villageBalance < order.unitPrice()
        // -> MUST return status.tyc2.hub.village_low_funds_detail AND NOT drain player inventory into Inbound!
        long savedVillageBal = village.getVillageBalance();
        village.adjustVillageBalance(-(savedVillageBal - 10L)); // Leave 10 IC < 25 IC unitPrice
        playerA.getInventory().clearContent();
        playerA.getInventory().add(new ItemStack(Items.COPPER_INGOT, 8));
        TycoonForgeWorldManager.ActionFeedback lowFundsRes = TycoonForgeWorldManager.executeBuyOrder(
                playerA, villageId, qaCopperOrderId, 4, true, true, null
        );
        int playerCopperAfterLowFunds = playerA.getInventory().countItem(Items.COPPER_INGOT);
        int inboundCopperAfterLowFunds = village.getStorage().countInInbound("minecraft:copper_ingot");
        qa.check("trade.hub_low_funds_exact_error",
                !lowFundsRes.success() && "status.tyc2.hub.village_low_funds_detail".equals(lowFundsRes.translationKey()),
                "success=" + lowFundsRes.success() + ", key=" + lowFundsRes.translationKey());
        qa.checkEquals("trade.hub_low_funds_keeps_player_items_intact", 8, playerCopperAfterLowFunds);
        qa.checkEquals("trade.hub_low_funds_does_not_drain_to_inbound", 0, inboundCopperAfterLowFunds);
        village.adjustVillageBalance(savedVillageBal - 10L);

        // (a) Buy-Order delivery with sufficient funds succeeds (pulls exact 4 from player inv + tag matcher verified)!
        qa.check("trade.tag_matcher_matches_copper_tag",
                TyC2Ids.matchesItemOrTag("minecraft:copper_ingot", "#ingots/copper"),
                "minecraft:copper_ingot vs #ingots/copper");
        TycoonForgeWorldManager.ActionFeedback okBuyRes = TycoonForgeWorldManager.executeBuyOrder(
                playerA, villageId, qaCopperOrderId, 4, true, true, null
        );
        qa.check("trade.hub_buy_order_delivery_succeeds",
                okBuyRes.success()
                        && "status.tyc2.hub.order_fulfilled".equals(okBuyRes.translationKey())
                        && playerA.getInventory().countItem(Items.COPPER_INGOT) == 4,
                "key=" + okBuyRes.translationKey() + ", playerRem=" + playerA.getInventory().countItem(Items.COPPER_INGOT));

        // Setup Auto-Fulfill on a Village Buy Order & pipe required items into Inbound 0..499!
        VillageTradeOrder buyOrder = village.getActiveOrders().stream()
                .filter(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.orderId().equals(qaCopperOrderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No initial Lv 1 Buy Orders generated!"));
        autoFulfillOrderId = buyOrder.orderId();
        tradeVolBeforeAutoFulfill = village.getCumulativeTradeVolume();
        TycoonForgeWorldManager.toggleOrderAutoFulfill(server, villageId, autoFulfillOrderId);
        Item buyItem = TyC2Ids.getItem(buyOrder.itemId());
        qa.check("hub.buy_order_item_valid", buyItem != null, "itemId=" + buyOrder.itemId());
        ItemStack rem = handler.insertItem(1, new ItemStack(buyItem, Math.min(8, buyOrder.remainingQty())), false);
        qa.check("hub.auto_fulfill_inbound_seeded", rem.isEmpty(), "item=" + buyOrder.itemId());

        // Bind Comm Terminal to village & test action EU deduction
        ItemStack term = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(term, 5000);
        int bindRes = CommTerminalItem.toggleBindVillage(term, villageId);
        qa.check("terminal.bind_village", bindRes == 1 && CommTerminalItem.getBoundVillages(term).contains(villageId),
                "bindRes=" + bindRes);
        boolean openOk = CommTerminalItem.consumeAction(term, TriModePowerMath.TerminalAction.OPEN_REMOTE_UI);
        qa.check("terminal.consume_open_20eu", openOk && CommTerminalItem.getStoredEu(term) == 4980,
                "stored=" + CommTerminalItem.getStoredEu(term));
        data.setDirty();
    }

    private void runUpgradeAndExpansionSetup(MinecraftServer server, ServerLevel level) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        VillageRecord village = data.getVillage(villageId).orElseThrow();

        // Spawn 2 more villagers so population >= 4 for Lv 1 -> Lv 2 upgrade
        spawnAdultVillager(level, hubPos.offset(3, 0, 1), VillagerProfession.MASON, "FourthVillager");
        spawnAdultVillager(level, hubPos.offset(-3, 0, -1), VillagerProfession.TOOLSMITH, "FifthVillager");
        village.setPopulation(5);
        // Ensure cumulative trade volume >= 400 and village balance >= 500
        village.addTradeVolume(500L);
        if (village.getVillageBalance() < 1500L) {
            village.adjustVillageBalance(1500L);
        }

        long balBefore = village.getVillageBalance();
        double cpiBefore = data.economy().computeCpi();
        TycoonForgeWorldManager.ActionFeedback upgRes = TycoonForgeWorldManager.upgradeVillage(playerA, villageId);
        qa.check("village.upgrade_lv1_to_lv2", upgRes.success() && village.getLevel() == 2,
                "level=" + village.getLevel() + ", key=" + upgRes.translationKey());
        qa.checkEquals("village.upgrade_burned_100ic_sink", balBefore - 100L, village.getVillageBalance());
        qa.check("village.upgrade_deflationary_sink", data.economy().computeCpi() <= cpiBefore,
                String.format("before=%.4f after=%.4f", cpiBefore, data.economy().computeCpi()));

        // Place TWO protected Chests (real BlockEntities containing marker Diamonds!) right on the r=10 expansion ring
        placeProtectedChest(level, data, protectedChestPosA);
        placeProtectedChest(level, data, protectedChestPosB);

        // 0. ServerTerrainProbe.getSurfaceY skips natural trees (BlockTags.LOGS / LEAVES) and returns true ground Y!
        BlockPos treeBase = new BlockPos(12, surfaceY, -12);
        String dimId = level.dimension().location().toString();
        for (int dy = 0; dy <= 3; dy++) {
            level.setBlock(treeBase.above(dy), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
            data.placedBlocks().markRemoved(dimId, treeBase.above(dy));
        }
        for (int dy = 4; dy <= 5; dy++) {
            level.setBlock(treeBase.above(dy), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
            data.placedBlocks().markRemoved(dimId, treeBase.above(dy));
        }
        ServerTerrainProbe terrainProbe = new ServerTerrainProbe(level, data.placedBlocks());
        int naturalTreeSurfaceY = terrainProbe.getSurfaceY(treeBase.getX(), treeBase.getZ());
        qa.checkEquals("terrain_probe.skips_natural_tree_to_ground_y", surfaceY - 1, naturalTreeSurfaceY);
        // Player-placed log at treeBase MUST NOT be skipped!
        data.placedBlocks().markPlaced(dimId, treeBase);
        int playerLogSurfaceY = terrainProbe.getSurfaceY(treeBase.getX(), treeBase.getZ());
        qa.checkEquals("terrain_probe.preserves_player_placed_log_y", surfaceY, playerLogSurfaceY);
        // Clean up temporary tree probe column
        data.placedBlocks().markRemoved(dimId, treeBase);
        for (int dy = 0; dy <= 5; dy++) {
            level.setBlock(treeBase.above(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        // 1. Create SMALL_COTTAGE ExpansionDesire (5 materials, autoFulfill=false) & test partial delivery + Terminal EU guard!
        ExpansionDesire desire = ExpansionDesire.createDefault(
                "qa_exp_cottage", ProjectType.SMALL_COTTAGE, village.getBiomeStyle(), false
        );
        village.setActiveExpansion(desire);
        qa.check("expansion.small_cottage_has_5_materials_incl_wool",
                desire.requiredMaterials().size() == 5
                        && desire.requiredMaterials().containsKey("minecraft:white_wool")
                        && !desire.autoFulfill(),
                "materials=" + desire.requiredMaterials());

        // Ensure Hub Inbound has 0 pre-existing white_wool so the 5th material is strictly missing
        int preWool = village.getStorage().countInInbound("minecraft:white_wool");
        if (preWool > 0) {
            village.getStorage().consumeFromInbound("minecraft:white_wool", preWool);
        }

        // Insert ONLY the first 4 materials (skipping minecraft:white_wool!)
        int requiredWool = desire.requiredMaterials().get("minecraft:white_wool");
        for (Map.Entry<String, Integer> req : desire.requiredMaterials().entrySet()) {
            Item matItem = TyC2Ids.getItem(req.getKey());
            qa.check("expansion.req_material_exists." + req.getKey(), matItem != null, req.getKey());
            if ("minecraft:white_wool".equals(req.getKey())) {
                continue;
            }
            int rem = req.getValue();
            while (rem > 0) {
                int chunk = Math.min(rem, matItem.getMaxStackSize());
                village.getStorage().insertExternal(req.getKey(), chunk, "", matItem.getMaxStackSize(), false);
                rem -= chunk;
            }
        }

        // 1a. Deliver first 4 materials -> status.tyc2.expansion.delivered_partial, construction NOT started!
        TycoonForgeWorldManager.ActionFeedback partialRes = TycoonForgeWorldManager.deliverExpansionMaterials(
                playerA, villageId, false
        );
        qa.check("expansion.partial_delivery_exact_key",
                partialRes.success()
                        && "status.tyc2.expansion.delivered_partial".equals(partialRes.translationKey())
                        && village.getActiveConstructionJob().isEmpty(),
                "success=" + partialRes.success() + ", key=" + partialRes.translationKey()
                        + ", remaining=" + village.getActiveExpansion().remainingMaterials());
        qa.checkEquals("expansion.partial_delivery_only_wool_remaining",
                requiredWool, village.getActiveExpansion().remainingMaterials().getOrDefault("minecraft:white_wool", -1));

        // 1b. Clicking DELIVER_EXPANSION again with 0 new items -> status.tyc2.expansion.missing_materials!
        TycoonForgeWorldManager.ActionFeedback missingAgainRes = TycoonForgeWorldManager.deliverExpansionMaterials(
                playerA, villageId, false
        );
        qa.check("expansion.repeat_zero_delivery_missing_materials",
                !missingAgainRes.success()
                        && "status.tyc2.expansion.missing_materials".equals(missingAgainRes.translationKey())
                        && village.getActiveConstructionJob().isEmpty(),
                "success=" + missingAgainRes.success() + ", key=" + missingAgainRes.translationKey());

        // 1c. Comm Terminal DELIVER_EXPANSION with 0 white_wool in Inbound -> missing_materials & ZERO EU deducted!
        ItemStack expTerm = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(expTerm, 5000);
        TycoonForgeWorldManager.ActionFeedback termMissingRes = TycoonForgeWorldManager.deliverExpansionMaterials(
                playerA, villageId, false, expTerm
        );
        qa.check("expansion.terminal_missing_materials_exact_key",
                !termMissingRes.success()
                        && "status.tyc2.expansion.missing_materials".equals(termMissingRes.translationKey())
                        && village.getActiveConstructionJob().isEmpty(),
                "success=" + termMissingRes.success() + ", key=" + termMissingRes.translationKey());
        qa.checkEquals("expansion.terminal_missing_materials_zero_eu_deducted",
                5000, CommTerminalItem.getStoredEu(expTerm));

        // 2. Deliver 5th material (white_wool) while villageBalance < budget -> status.tyc2.expansion.low_funds,
        //    then restore balance WITHOUT clicking DELIVER_EXPANSION again so periodic tick auto-launches construction!
        Item woolItem = TyC2Ids.getItem("minecraft:white_wool");
        village.getStorage().insertExternal("minecraft:white_wool", requiredWool, "", woolItem.getMaxStackSize(), false);
        long balBeforeExpTest = village.getVillageBalance();
        qa.check("expansion.budget_positive", desire.constructionBudgetCost() > 0L,
                "budget=" + desire.constructionBudgetCost());
        village.adjustVillageBalance(-balBeforeExpTest);
        TycoonForgeWorldManager.ActionFeedback expLowFunds = TycoonForgeWorldManager.deliverExpansionMaterials(
                playerA, villageId, false
        );
        qa.check("expansion.low_funds_exact_error",
                !expLowFunds.success()
                        && "status.tyc2.expansion.low_funds".equals(expLowFunds.translationKey())
                        && village.getActiveExpansion() != null
                        && village.getActiveExpansion().allMaterialsDelivered()
                        && !village.getActiveExpansion().autoFulfill()
                        && village.getActiveConstructionJob().isEmpty(),
                "success=" + expLowFunds.success() + ", key=" + expLowFunds.translationKey()
                        + ", allDelivered=" + (village.getActiveExpansion() != null && village.getActiveExpansion().allMaterialsDelivered()));
        // Restore balance and let W6_WAIT_AUTO_LAUNCH_CONSTRUCTION verify automatic launch on next periodic tick!
        village.adjustVillageBalance(balBeforeExpTest);
        data.setDirty();
    }

    private void runEnhancedIc2Lc2Setup(MinecraftServer server, ServerLevel level) throws Exception {
        // 1. Direct IC2 Mixin checks (ForgeGear, ProcessingRecipes, WorkshopRecipes)
        TyC2QaIc2Checks.checkPortableGearMixinDirect(qa);
        TyC2QaIc2Checks.checkMetalFormerCoinRecipesDisabled(qa);

        // 2. IC2 Generator -> insulated_copper_cable -> Minting Machine & ATM (NO coal in Mint/ATM!)
        TyC2QaIc2Checks.setupGeneratorCableAndMachines(
                level, ic2GenPos, ic2CableMintPos, ic2MintPos, ic2CableAtmPos, ic2AtmPos, qa
        );
        // Also verify IC2 copper cable does NOT visually connect to tyc2:primitive_mint_machine,
        // and that Primitive Mint blocks IC2 Tin/Alloy/Iridium inputs while Electric Mint accepts them!
        TyC2QaIc2Checks.checkCableDoesNotConnectToPrimitiveMint(level, ic2CablePrimPos, ic2PrimPos, qa);
        PrimitiveMintMachineBlockEntity ic2Prim = (PrimitiveMintMachineBlockEntity) level.getBlockEntity(ic2PrimPos);
        Item tinIngot = TyC2Ids.getItem("ic2:tin_ingot");
        qa.check("ic2.tin_ingot_registered", tinIngot != null, "ic2:tin_ingot");
        ic2Prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
        ic2Prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(tinIngot, 2));

        MintMachineBlockEntity ic2Mint = (MintMachineBlockEntity) level.getBlockEntity(ic2MintPos);
        AtmMachineBlockEntity ic2Atm = (AtmMachineBlockEntity) level.getBlockEntity(ic2AtmPos);
        TyC2QaIc2Checks.checkPowerPortSubclasses(ic2Mint, ic2Atm, qa);

        // Load IC2 Tin Ingot into EU-powered Electric Mint -> verifies IC2 EU powers minting & produces ic2:coin!
        ic2Mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(tinIngot, 2));

        // Verify Electric Mint also accepts IC2 re_battery & discharges ic2:single_use_battery (1200 EU) cleanly!
        ItemStack reBat = TyC2QaIc2Checks.createChargedIc2Battery(1000.0);
        qa.check("ic2.electric_mint_accepts_ic2_re_battery",
                MintMachineBlockEntity.isValidBatteryItem(reBat, 1)
                        && ic2Mint.canPlaceItem(MintMachineBlockEntity.SLOT_BATTERY, reBat),
                "reBat=" + reBat);
        Item singleUseBatItem = TyC2Ids.getItem("ic2:single_use_battery");
        qa.check("ic2.single_use_battery_registered", singleUseBatItem != null, "ic2:single_use_battery");
        level.setBlock(ic2SingleUseBatMintPos, TyC2ForgeRegistries.MINT_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        MintMachineBlockEntity singleBatMint = (MintMachineBlockEntity) level.getBlockEntity(ic2SingleUseBatMintPos);
        qa.check("ic2.single_use_bat_mint_placed", singleBatMint != null, "pos=" + ic2SingleUseBatMintPos);
        singleBatMint.setStoredEu(0.0);
        singleBatMint.setItem(MintMachineBlockEntity.SLOT_BATTERY, new ItemStack(singleUseBatItem, 1));
        // Verify Primitive Mint blocks IC2 alloys/iridium via requiresIndustrialMint
        qa.check("ic2.primitive_blocks_alloy_and_iridium",
                TyC2RuntimeData.mintRecipes().requiresIndustrialMint(
                        "ic2:alloy", 1, "minecraft:gold_ingot", 1, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded)
                        && TyC2RuntimeData.mintRecipes().requiresIndustrialMint(
                        "ic2:iridium_ore", 1, "ic2:alloy", 1, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded),
                "ic2 alloy + iridium blocked on primitive");

        // 3. IC2 BatBox charging tyc2:comm_terminal via Generator -> Cable -> BatBox
        TyC2QaIc2Checks.setupBatBoxTerminalCharging(
                level, ic2BatGenPos, ic2BatGenPos.east(), ic2BatGenPos.east(2), playerA, qa
        );
        // Also verify ATM accepts BOTH ic2:coin AND tyc2:coin_1 at 1:1 in Enhanced Mode!
        AtmMachineBlockEntity atmMain = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
        TycoonForgeSavedData enhData = TycoonForgeSavedData.get(server);
        long balBefore1x = enhData.bank().getActiveBalance(PLAYER_A_UUID);
        atmMain.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, new ItemStack(TyC2Ids.getItem("ic2:coin"), 5));
        atmMain.handleDepositSlot(playerA);
        atmMain.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, new ItemStack(TyC2ForgeRegistries.COIN_1.get(), 3));
        atmMain.handleDepositSlot(playerA);
        qa.checkEquals("ic2.atm_accepts_ic2_coin_and_tyc2_coin1_1to1",
                balBefore1x + 8L, enhData.bank().getActiveBalance(PLAYER_A_UUID));

        // 4. IC2 Alloys, Iridium & Crops loaded in GoodsCatalog & MintRecipeRegistry!
        GoodsCatalog cat = TyC2RuntimeData.goodsCatalog();
        for (String ic2Item : List.of("ic2:resin", "ic2:hops", "ic2:coffee_beans", "ic2:terra_wart",
                "ic2:alloy", "ic2:iridium_shard")) {
            qa.check("ic2.item_registered." + ic2Item, TyC2Ids.itemExists(ic2Item), ic2Item);
            qa.check("ic2.good_in_catalog." + ic2Item, cat.findByItemId(ic2Item).isPresent(), ic2Item);
        }
        var iridiumMatch = TyC2RuntimeData.mintRecipes().findMatch(
                "ic2:iridium", 1, "ic2:alloy", 1, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
        );
        var alloyMatch = TyC2RuntimeData.mintRecipes().findMatch(
                "ic2:alloy", 1, "minecraft:gold_ingot", 1, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
        );
        qa.check("ic2.mint_recipe_alloy_100x", alloyMatch.isPresent(), "alloyMatch=" + alloyMatch);
        qa.check("ic2.mint_recipe_iridium_1000x",
                iridiumMatch.isPresent() || TyC2RuntimeData.mintRecipes().findMatch(
                        "ic2:iridium_ore", 1, "ic2:alloy", 1, TyC2Ids.ITEM_TAG_MATCHER, ModList.get()::isLoaded
                ).isPresent(),
                "iridiumMatch=" + iridiumMatch);

        // 5. LC2 Chunk Loading Upgrade inside Cooperation Hub (placed at far chunk (10, 0) with NO QA force ticket!)
        Item lc2Upgrade = TyC2Ids.getItem("lc2:chunk_loading_upgrade");
        qa.check("lc2.chunk_loading_upgrade_registered", lc2Upgrade != null, "lc2:chunk_loading_upgrade");
        level.getChunk(new ChunkPos(lc2HubPos).x, new ChunkPos(lc2HubPos).z);
        level.setBlock(lc2HubPos, TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        CooperationHubBlockEntity lc2Hub = (CooperationHubBlockEntity) level.getBlockEntity(lc2HubPos);
        qa.check("lc2.hub_placed", lc2Hub != null, "pos=" + lc2HubPos);
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        lc2VillageId = UUID.randomUUID();
        VillageRecord lc2Rec = new VillageRecord(
                lc2VillageId,
                level.dimension().location().toString(),
                "QA Logistics Port",
                TyC2Ids.toCore(lc2HubPos),
                BiomeStyle.PLAINS
        );
        lc2Rec.establishPartnership(PLAYER_A_UUID, companyId, 100L);
        data.putVillage(lc2Rec);
        lc2Hub.setVillageId(lc2VillageId);
        lc2Hub.upgradeContainer.setItem(0, new ItemStack(lc2Upgrade, 1));
    }

    private void runEnhancedIc2Lc2Verify(MinecraftServer server, ServerLevel level) {
        MintMachineBlockEntity ic2Mint = (MintMachineBlockEntity) level.getBlockEntity(ic2MintPos);
        AtmMachineBlockEntity ic2Atm = (AtmMachineBlockEntity) level.getBlockEntity(ic2AtmPos);
        PrimitiveMintMachineBlockEntity ic2Prim = (PrimitiveMintMachineBlockEntity) level.getBlockEntity(ic2PrimPos);
        qa.check("ic2.primitive_mint_blocks_tin_ingot_live",
                ic2Prim != null && ic2Prim.isBlockedByIndustrialTier()
                        && ic2Prim.getProgressTicks() == 0
                        && ic2Prim.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT).isEmpty(),
                "blocked=" + (ic2Prim != null && ic2Prim.isBlockedByIndustrialTier())
                        + ", out=" + (ic2Prim == null ? "null" : ic2Prim.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT)));
        qa.check("ic2.eu_delivered_to_mint_via_cable",
                ic2Mint != null && (ic2Mint.getStoredEu() > 0.0 || ic2Mint.getProgressPercent() > 0
                        || !ic2Mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT).isEmpty()),
                "mintEu=" + (ic2Mint == null ? -1 : ic2Mint.getStoredEu())
                        + ", prog=" + (ic2Mint == null ? -1 : ic2Mint.getProgressPercent())
                        + ", out=" + (ic2Mint == null ? "null" : ic2Mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT)));
        MintMachineBlockEntity singleBatMint = (MintMachineBlockEntity) level.getBlockEntity(ic2SingleUseBatMintPos);
        qa.check("ic2.single_use_battery_discharged_1200eu",
                singleBatMint != null && singleBatMint.getStoredEu() >= 1199.0
                        && singleBatMint.getItem(MintMachineBlockEntity.SLOT_BATTERY).isEmpty(),
                "storedEu=" + (singleBatMint == null ? -1 : singleBatMint.getStoredEu())
                        + ", slot3=" + (singleBatMint == null ? "null" : singleBatMint.getItem(MintMachineBlockEntity.SLOT_BATTERY)));
        qa.check("ic2.eu_delivered_to_atm_via_cable", ic2Atm != null && ic2Atm.getStoredEu() > 0.0,
                "atmEu=" + (ic2Atm == null ? -1 : ic2Atm.getStoredEu()));
        qa.metric("ic2.mint_eu_via_cable", ic2Mint.getStoredEu());
        qa.metric("ic2.atm_eu_via_cable", ic2Atm.getStoredEu());

        // Check IC2 BatBox charged tyc2:comm_terminal!
        int batTermEu = TyC2QaIc2Checks.readBatBoxTerminalEu(level, ic2BatGenPos.east(2), playerA);
        qa.check("ic2.batbox_charged_comm_terminal", batTermEu > 0, "batBoxTerminalEu=" + batTermEu);
        qa.metric("ic2.batbox_terminal_eu", batTermEu);

        // Verify LC2 chunk_loading_upgrade activated 3x3 ForgeChunkManager forced chunks!
        CooperationHubBlockEntity lc2Hub = (CooperationHubBlockEntity) level.getBlockEntity(lc2HubPos);
        qa.check("lc2.hub_chunk_loader_active",
                lc2Hub != null && lc2Hub.isChunkLoaderActive()
                        && com.tycooncraft.tyc2.forge.util.TyC2ChunkTickets.isOwnerActive(level, lc2HubPos),
                "active=" + (lc2Hub != null && lc2Hub.isChunkLoaderActive()));
        ChunkPos centerChunk = new ChunkPos(lc2HubPos);
        int tickingIn3x3 = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = centerChunk.x + dx;
                int cz = centerChunk.z + dz;
                BlockPos sample = new BlockPos((cx << 4) + 8, surfaceY, (cz << 4) + 8);
                if (level.getChunkSource().getChunkNow(cx, cz) != null && level.isPositionEntityTicking(sample)) {
                    tickingIn3x3++;
                }
            }
        }
        qa.checkEquals("lc2.hub_3x3_chunks_entity_ticking", 9, tickingIn3x3);
    }

    private void writeExpectedSnapshot(MinecraftServer server, ServerLevel level) throws Exception {
        // Freeze live Minting Machines before snapshotting totalMintedNet so no in-flight tin/coal cycle completes during shutdown/startup ticks.
        if (enhanced && level.getBlockEntity(ic2MintPos) instanceof MintMachineBlockEntity ic2Mint) {
            ic2Mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, ItemStack.EMPTY);
            ic2Mint.setItem(MintMachineBlockEntity.SLOT_INPUT_1, ItemStack.EMPTY);
        }
        if (level.getBlockEntity(mintCoalPos) instanceof MintMachineBlockEntity coalMint) {
            coalMint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, ItemStack.EMPTY);
            coalMint.setItem(MintMachineBlockEntity.SLOT_INPUT_1, ItemStack.EMPTY);
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        VillageRecord v = data.getVillage(villageId).orElseThrow();
        MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintCoalPos);
        Properties p = new Properties();
        p.setProperty("surfaceY", Integer.toString(surfaceY));
        p.setProperty("villageId", villageId.toString());
        p.setProperty("companyId", companyId);
        p.setProperty("playerABalance", Long.toString(data.bank().getBalance(BankAccountManager.AccountRef.personal(PLAYER_A_UUID))));
        p.setProperty("playerBBalance", Long.toString(data.bank().getBalance(BankAccountManager.AccountRef.personal(PLAYER_B_UUID))));
        p.setProperty("companyBalance", Long.toString(data.bank().getCompany(companyId).orElseThrow().balance()));
        p.setProperty("villageLevel", Integer.toString(v.getLevel()));
        p.setProperty("villageBalance", Long.toString(v.getVillageBalance()));
        p.setProperty("villageTradeVolume", Long.toString(v.getCumulativeTradeVolume()));
        p.setProperty("villageInboundUsed", Integer.toString(v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.INBOUND)));
        p.setProperty("villageOccupiedZones", Integer.toString(v.getOccupiedZones().size()));
        p.setProperty("totalMintedNet", Long.toString(data.economy().totalMintedNet()));
        p.setProperty("mintTerminalEu", Integer.toString(CommTerminalItem.getStoredEu(mint.getItem(MintMachineBlockEntity.SLOT_TERMINAL))));
        p.setProperty("mintOutputId", TyC2Ids.itemId(mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT)));
        p.setProperty("writePid", Long.toString(ProcessHandle.current().pid()));
        if (aidPersistProjectId != null && !aidPersistProjectId.isBlank()) {
            p.setProperty("aidPersistProjectId", aidPersistProjectId);
            p.setProperty("aidPersistPaidIc", Long.toString(aidPersistPaidIc));
        }
        if (enhanced && lc2VillageId != null) {
            p.setProperty("lc2VillageId", lc2VillageId.toString());
        }
        try (Writer w = Files.newBufferedWriter(expectedStatePath())) {
            p.store(w, "TyC2 Forge 1.19.2 expected persistence snapshot");
        }
    }

    private void runColdRestartReadVerification(MinecraftServer server, ServerLevel level) throws Exception {
        Properties exp = new Properties();
        try (Reader r = Files.newBufferedReader(expectedStatePath())) {
            exp.load(r);
        }
        long writePid = Long.parseLong(exp.getProperty("writePid", "-1"));
        long readPid = ProcessHandle.current().pid();
        qa.check("reload.distinct_jvm_pid", readPid != writePid, "writePid=" + writePid + ", readPid=" + readPid);

        UUID expVid = UUID.fromString(exp.getProperty("villageId"));
        String expCoId = exp.getProperty("companyId");
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);

        qa.checkEquals("reload.playerA_balance", Long.parseLong(exp.getProperty("playerABalance")),
                data.bank().getBalance(BankAccountManager.AccountRef.personal(PLAYER_A_UUID)));
        qa.checkEquals("reload.playerB_balance", Long.parseLong(exp.getProperty("playerBBalance")),
                data.bank().getBalance(BankAccountManager.AccountRef.personal(PLAYER_B_UUID)));
        qa.checkEquals("reload.company_balance", Long.parseLong(exp.getProperty("companyBalance")),
                data.bank().getCompany(expCoId).orElseThrow().balance());
        qa.checkEquals("reload.total_minted_net", Long.parseLong(exp.getProperty("totalMintedNet")),
                data.economy().totalMintedNet());

        VillageRecord v = data.getVillage(expVid).orElseThrow(() -> new AssertionError("Village missing after reload!"));
        qa.check("reload.village_partnered", v.isPartnered(), "partnered=" + v.isPartnered());
        qa.checkEquals("reload.village_level", Integer.parseInt(exp.getProperty("villageLevel")), v.getLevel());
        qa.checkEquals("reload.village_balance", Long.parseLong(exp.getProperty("villageBalance")), v.getVillageBalance());
        qa.checkEquals("reload.village_trade_vol", Long.parseLong(exp.getProperty("villageTradeVolume")), v.getCumulativeTradeVolume());
        qa.checkEquals("reload.hub_inbound_used", Integer.parseInt(exp.getProperty("villageInboundUsed")),
                v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
        qa.checkEquals("reload.occupied_zones", Integer.parseInt(exp.getProperty("villageOccupiedZones")),
                v.getOccupiedZones().size());
        qa.check("reload.employment_stats_preserved",
                v.getEmploymentStats().workforceCount() >= 5
                        && v.getEmploymentStats().unemploymentRate() < 0.25,
                "workforce=" + v.getEmploymentStats().workforceCount()
                        + ", rate=" + v.getEmploymentStats().unemploymentRate());
        String expAidId = exp.getProperty("aidPersistProjectId", "");
        long expAidPaid = Long.parseLong(exp.getProperty("aidPersistPaidIc", "-1"));
        qa.check("reload.aid_project_preserved",
                !expAidId.isBlank()
                        && v.findAidProject(expAidId)
                        .map(ap -> "minecraft:mason".equals(ap.professionId())
                                && ap.paidIc() == expAidPaid
                                && ap.reservedZone() != null)
                        .orElse(false),
                "expectedAidId=" + expAidId + ", count=" + v.getAidProjects().size());

        // Block entities & protected chests survived reload
        qa.check("reload.hub_block_present",
                level.getBlockState(hubPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get()), "hub");
        qa.check("reload.primitive_mint_present",
                level.getBlockState(primMintPos).is(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_BLOCK.get())
                        && level.getBlockEntity(primMintPos) instanceof PrimitiveMintMachineBlockEntity pBe
                        && pBe.isBlockedByIndustrialTier(),
                "primState=" + level.getBlockState(primMintPos));
        MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintCoalPos);
        qa.check("reload.mint_be_present", mint != null, "mint=" + mint);
        qa.checkEquals("reload.mint_output_persisted", exp.getProperty("mintOutputId"),
                TyC2Ids.itemId(mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT)));
        int expTermEu = Integer.parseInt(exp.getProperty("mintTerminalEu"));
        int actualTermEu = CommTerminalItem.getStoredEu(mint.getItem(MintMachineBlockEntity.SLOT_TERMINAL));
        qa.check("reload.mint_terminal_eu_persisted", actualTermEu >= expTermEu && actualTermEu > 0,
                "expected>=" + expTermEu + ", actual=" + actualTermEu);
        verifyProtectedChestIntact(level, protectedChestPosA, "reload.chestA");
        verifyProtectedChestIntact(level, protectedChestPosB, "reload.chestB");

        if (enhanced) {
            CooperationHubBlockEntity lc2Hub = (CooperationHubBlockEntity) level.getBlockEntity(lc2HubPos);
            qa.check("reload.lc2_hub_upgrade_persisted",
                    lc2Hub != null && Lc2SoftBridge.isChunkLoadingUpgrade(lc2Hub.upgradeContainer.getItem(0)),
                    "upgrade=" + (lc2Hub == null ? "null" : lc2Hub.upgradeContainer.getItem(0)));
        }
    }

    private void placeProtectedChest(ServerLevel level, TycoonForgeSavedData data, BlockPos pos) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        data.placedBlocks().markPlaced(level.dimension().location().toString(), pos);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            chest.setChanged();
        }
        data.setDirty();
    }

    private void verifyProtectedChestIntact(ServerLevel level, BlockPos pos, String tag) {
        BlockEntity be = level.getBlockEntity(pos);
        boolean ok = level.getBlockState(pos).is(Blocks.CHEST)
                && be instanceof ChestBlockEntity chest
                && chest.getItem(0).is(Items.DIAMOND)
                && chest.getItem(0).getCount() == 7;
        qa.check("protection." + tag + "_untouched", ok,
                "pos=" + pos + ", state=" + level.getBlockState(pos) + ", be=" + be);
    }

    private void verifyHudLine(String checkName, List<Component> lines, String expectedKey) {
        Component matched = null;
        for (Component c : lines) {
            if (c != null && c.getContents() instanceof TranslatableContents tc && expectedKey.equals(tc.getKey())) {
                matched = c;
                break;
            }
        }
        String rendered = matched == null ? "<missing>" : matched.getString();
        boolean hasLang = Language.getInstance().has(expectedKey);
        boolean noRawSubkeys = matched != null && !rendered.isBlank() && !rendered.contains(".tyc2.");
        qa.check(checkName, matched != null && hasLang && noRawSubkeys,
                "key=" + expectedKey + ", hasLang=" + hasLang + ", rendered=" + rendered);
    }

    private static Villager spawnUnemployedVillager(ServerLevel level, BlockPos pos) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            throw new AssertionError("Failed to create Unemployed Villager");
        }
        v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        v.setAge(0);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
        v.setVillagerXp(0);
        v.setNoAi(true);
        v.setPersistenceRequired();
        level.addFreshEntity(v);
        return v;
    }

    private static Villager spawnAdultVillager(
            ServerLevel level, BlockPos pos, VillagerProfession profession, String name
    ) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            throw new AssertionError("Failed to create Villager");
        }
        v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        v.setAge(0);
        v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(2));
        v.setVillagerXp(25);
        v.setPersistenceRequired();
        level.addFreshEntity(v);
        return v;
    }

    private static Villager findVillagerByUuid(ServerLevel level, BlockPos center, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        var box = new net.minecraft.world.phys.AABB(center).inflate(48.0);
        for (Villager v : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
            if (uuid.equals(v.getUUID())) {
                return v;
            }
        }
        return null;
    }

    private static Villager findNonMayorVillager(ServerLevel level, BlockPos center) {
        var box = new net.minecraft.world.phys.AABB(center).inflate(48.0);
        for (Villager v : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
            if (!v.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)) {
                return v;
            }
        }
        return null;
    }
}
