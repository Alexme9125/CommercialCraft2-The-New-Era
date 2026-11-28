package com.tycooncraft.tyc2.qa;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlock;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlock;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Dedicated-server orchestrator for the real Forge 1.19.2 Graphical Client + Server joint test.
 * Builds a village plaza, waits for the real graphical client to connect over TCP, verifies
 * real-player Bell discovery &amp; Mayor election, and drives interactive GUI/packet tests for
 * all 5 TyC2 screens (Mayor Negotiation, Minting Machine, ATM, Cooperation Hub, Comm Terminal).
 */
final class TyC2LiveServerRunner {

    private static volatile TyC2LiveServerRunner instance;

    private enum Stage {
        SETUP_PLAZA,
        WAIT_CLIENT_JOIN,
        WAIT_MAYOR_ELECTED,
        STEP_AUDIT_ASSETS,
        STEP_SHOT_PLAZA,
        STEP_MAYOR_NEGOTIATION_GUI,
        STEP_PRIMITIVE_MINT_ACTIVE_GUI,
        STEP_PRIMITIVE_MINT_BLOCKED_GUI,
        STEP_MINT_GUI,
        STEP_ATM_GUI,
        STEP_HUB_GUI,
        STEP_TERMINAL_GUI,
        STEP_FINISH,
        WAIT_HALT,
        DONE
    }

    private final String profile;
    private final String lang;
    private final boolean enhanced;
    private final TyC2QaAssert qa;

    private Stage stage = Stage.SETUP_PLAZA;
    private int stageTicks = 0;
    private int nextStepId = 1;
    private int waitingStepId = -1;
    private volatile TyC2LiveNetwork.C2SLiveAck pendingAck = null;
    private boolean finished = false;

    private int surfaceY;
    private BlockPos bellHubPos;
    private BlockPos primMintPos;
    private BlockPos mintPos;
    private BlockPos atmPos;
    private BlockPos ic2GenPos;
    private BlockPos ic2CablePos;
    private ServerPlayer tester;
    private UUID villageId;
    private UUID mayorUuid;
    private int mayorEntityId = -1;
    private int mayorHoldTicks = 0;
    private boolean mayorHoldVerified = false;

    TyC2LiveServerRunner(String profile) {
        this.profile = profile;
        this.lang = System.getProperty("tyc2.live.lang", "en_us");
        this.enhanced = "enhanced".equalsIgnoreCase(profile);
        this.qa = new TyC2QaAssert("live-server-" + profile + "-" + this.lang);
        instance = this;
    }

    private String shotName(String suffix) {
        return "mnera_1192_" + profile + "_" + lang + "_" + suffix;
    }

    static void onClientAck(TyC2LiveNetwork.C2SLiveAck ack) {
        TyC2LiveServerRunner runner = instance;
        if (runner != null) {
            TyC2QaMod.LOGGER.info("[TYC2-LIVE-SERVER] Received ACK step={} ok={} summary={}",
                    ack.stepId(), ack.success(), ack.summary());
            runner.pendingAck = ack;
        }
    }

    private Path reportPath() {
        return Path.of("live-server-report.properties");
    }

    private Path failurePath() {
        return Path.of("live-server-failure.txt");
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
            TyC2QaMod.LOGGER.error("[TYC2-LIVE-SERVER] Live joint test failed in stage={}", stage, t);
            qa.writeFailureReport(reportPath(), failurePath(), t);
            server.halt(false);
        }
    }

    private void advance(Stage next) {
        this.stage = next;
        this.stageTicks = 0;
        this.waitingStepId = -1;
        this.pendingAck = null;
    }

    private void sendDirective(String action, BlockPos pos, int entityId, String arg) {
        waitingStepId = nextStepId++;
        pendingAck = null;
        TyC2LiveNetwork.sendToClient(tester, new TyC2LiveNetwork.S2CLiveDirective(
                waitingStepId, action, pos, entityId, arg
        ));
    }

    private TyC2LiveNetwork.C2SLiveAck pollAck(int timeoutTicks, String stepName) {
        TyC2LiveNetwork.C2SLiveAck ack = pendingAck;
        if (ack != null && ack.stepId() == waitingStepId) {
            pendingAck = null;
            if (!ack.success()) {
                throw new AssertionError("Client failed step " + stepName + ": " + ack.summary());
            }
            return ack;
        }
        if (stageTicks > timeoutTicks) {
            throw new AssertionError("Timed out waiting for client ACK on " + stepName + " (step=" + waitingStepId + ")");
        }
        return null;
    }

    private void step(MinecraftServer server, ServerLevel level) throws Exception {
        stageTicks++;
        switch (stage) {
            case SETUP_PLAZA -> {
                if (stageTicks < 4) {
                    return;
                }
                setupPlaza(server, level);
                advance(Stage.WAIT_CLIENT_JOIN);
            }

            case WAIT_CLIENT_JOIN -> {
                if (stageTicks > 2400) {
                    throw new AssertionError("Timed out waiting for real graphical client to join server");
                }
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    return;
                }
                tester = players.get(0);
                tester.setGameMode(GameType.SURVIVAL);
                tester.setInvulnerable(true);
                // Position player cleanly facing North toward the village plaza
                tester.teleportTo(level, 0.5, surfaceY + 1.0, 5.5, 180.0F, 12.0F);
                stockPlayerHotbarAndSupplies(server, tester);
                qa.check("live.client_joined", true, "player=" + tester.getGameProfile().getName());
                advance(Stage.WAIT_MAYOR_ELECTED);
            }

            case WAIT_MAYOR_ELECTED -> {
                // Keep time bright noon and clear weather
                level.setDayTime(6000L);
                TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                Optional<VillageRecord> vOpt = data.findNearestVillage(
                        level.dimension().location().toString(), bellHubPos, 32
                );
                if (vOpt.isPresent() && vOpt.get().getMayorState().getMayorEntityUuid() != null) {
                    VillageRecord v = vOpt.get();
                    villageId = v.getVillageId();
                    UUID mUuid = v.getMayorState().getMayorEntityUuid();
                    Villager mayor = findVillager(level, bellHubPos, mUuid);
                    if (mayor != null && mayor.hasCustomName()) {
                        this.mayorUuid = mUuid;
                        mayorEntityId = mayor.getId();
                        // Pose Mayor right beside Bell facing South toward camera for a crisp plaza shot!
                        mayor.moveTo(bellHubPos.getX() + 0.5, surfaceY, bellHubPos.getZ() + 1.4, 0.0F, 0.0F);
                        mayor.setNoAi(true);
                        qa.check("live.real_player_discovered_village_and_elected_mayor", true,
                                "village=" + v.getVillageName() + ", mayor=" + mayor.getCustomName().getString());
                        advance(Stage.STEP_AUDIT_ASSETS);
                        return;
                    }
                }
                if (stageTicks > 260) {
                    throw new AssertionError("Real-player proximity did not auto-discover Bell & elect Mayor within 260 ticks");
                }
            }

            case STEP_AUDIT_ASSETS -> {
                if (waitingStepId < 0) {
                    sendDirective("AUDIT_ASSETS", BlockPos.ZERO, 0, lang);
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(400, "AUDIT_ASSETS");
                if (ack != null) {
                    qa.check("live.client_assets_and_sprites_ok", true, ack.summary());
                    advance(Stage.STEP_SHOT_PLAZA);
                }
            }

            case STEP_SHOT_PLAZA -> {
                if (waitingStepId < 0) {
                    // Ensure BOTH Primitive Minting Machine (roaring firebox!) and Electric Minting Machine are active!
                    if (level.getBlockEntity(primMintPos) instanceof PrimitiveMintMachineBlockEntity prim) {
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 16));
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 16));
                    }
                    if (level.getBlockEntity(mintPos) instanceof MintMachineBlockEntity mint) {
                        mint.setStoredEu(8000.0);
                        mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.IRON_INGOT, 8));
                    }
                    tester.teleportTo(level, 0.0, surfaceY + 1.4, 5.4, 180.0F, 14.0F);
                    sendDirective("CAPTURE_SCREENSHOT", bellHubPos, mayorEntityId,
                            shotName("01_plaza_mayor.png"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(500, "CAPTURE_SCREENSHOT_PLAZA");
                if (ack != null) {
                    qa.check("live.plaza_screenshot_captured", true, ack.summary());
                    advance(Stage.STEP_MAYOR_NEGOTIATION_GUI);
                }
            }

            case STEP_MAYOR_NEGOTIATION_GUI -> {
                if (waitingStepId < 0) {
                    // Re-enable full Mayor AI so tickActiveNegotiations is genuinely tested against a live ServerPlayer!
                    Villager mayor = findVillager(level, bellHubPos, mayorUuid);
                    if (mayor != null) {
                        mayor.setNoAi(false);
                        mayor.moveTo(bellHubPos.getX() + 0.5, surfaceY, bellHubPos.getZ() + 1.3, 0.0F, 0.0F);
                    }
                    mayorHoldTicks = 0;
                    mayorHoldVerified = false;
                    tester.teleportTo(level, bellHubPos.getX() + 0.5, surfaceY, bellHubPos.getZ() + 2.8, 180.0F, 8.0F);
                    sendDirective("TEST_MAYOR_GUI", bellHubPos, mayorEntityId,
                            shotName("02_mayor_negotiation.png"));
                    return;
                }
                if (!mayorHoldVerified && tester.containerMenu instanceof MayorNegotiationMenu) {
                    mayorHoldTicks++;
                    if (mayorHoldTicks >= 4) {
                        Villager mayor = findVillager(level, bellHubPos, mayorUuid);
                        double horizSpeed = mayor == null ? 999.0
                                : Math.hypot(mayor.getDeltaMovement().x, mayor.getDeltaMovement().z);
                        boolean held = mayor != null
                                && mayor.getTradingPlayer() == tester
                                && mayor.getNavigation().isDone()
                                && horizSpeed < 0.05;
                        qa.check("live.mayor_negotiation_hold_verified", held,
                                "tradingPlayer=" + (mayor == null || mayor.getTradingPlayer() == null
                                        ? "null" : mayor.getTradingPlayer().getGameProfile().getName())
                                        + ", navDone=" + (mayor != null && mayor.getNavigation().isDone())
                                        + ", horizSpeed=" + String.format("%.4f", horizSpeed));
                        mayorHoldVerified = true;
                    }
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(600, "TEST_MAYOR_GUI");
                if (ack != null) {
                    qa.check("live.mayor_negotiation_hold_observed", mayorHoldVerified,
                            "holdTicks=" + mayorHoldTicks);
                    Villager mayorAfter = findVillager(level, bellHubPos, mayorUuid);
                    qa.check("live.mayor_negotiation_released_after_sign",
                            mayorAfter != null && mayorAfter.getTradingPlayer() == null,
                            "tradingPlayer=" + (mayorAfter == null ? "null-entity" : mayorAfter.getTradingPlayer()));
                    qa.check("live.mayor_gui_signed_partnership",
                            level.getBlockState(bellHubPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get()),
                            ack.summary() + " | block=" + level.getBlockState(bellHubPos));
                    // Install LC2 chunk_loading_upgrade in Hub in Enhanced mode so badge renders in Hub GUI!
                    if (enhanced && level.getBlockEntity(bellHubPos) instanceof CooperationHubBlockEntity hubBe) {
                        Item upg = TyC2Ids.getItem("trera:chunk_loading_upgrade");
                        if (upg != null) {
                            hubBe.upgradeContainer.setItem(0, new ItemStack(upg, 1));
                        }
                    }
                    // Seed Inbound stock & active ExpansionDesire so ALL 4 Hub tabs + ALL 4 Terminal tabs have live data!
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                    data.getVillage(villageId).ifPresent(v -> {
                        v.getStorage().insertExternal("minecraft:iron_ingot", 32, "", 64, false);
                        v.getStorage().insertExternal("minecraft:coal", 48, "", 64, false);
                        v.adjustVillageBalance(2000L);
                        v.setActiveExpansion(ExpansionDesire.createDefault(
                                "live_exp_cottage", ProjectType.SMALL_COTTAGE, v.getBiomeStyle(), false
                        ));
                    });
                    data.setDirty();
                    advance(Stage.STEP_PRIMITIVE_MINT_ACTIVE_GUI);
                }
            }

            case STEP_PRIMITIVE_MINT_ACTIVE_GUI -> {
                if (waitingStepId < 0) {
                    if (level.getBlockEntity(primMintPos) instanceof PrimitiveMintMachineBlockEntity prim) {
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.GOLD_INGOT, 4));
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.IRON_INGOT, 4));
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 12));
                    }
                    tester.teleportTo(level, primMintPos.getX() + 0.5, surfaceY, primMintPos.getZ() + 2.2, 180.0F, 12.0F);
                    sendDirective("TEST_PRIMITIVE_MINT_ACTIVE_GUI", primMintPos, 0,
                            shotName("03a_primitive_mint_active.png"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(500, "TEST_PRIMITIVE_MINT_ACTIVE_GUI");
                if (ack != null) {
                    qa.check("live.primitive_mint_active_gui_verified", true, ack.summary());
                    advance(Stage.STEP_PRIMITIVE_MINT_BLOCKED_GUI);
                }
            }

            case STEP_PRIMITIVE_MINT_BLOCKED_GUI -> {
                if (waitingStepId < 0) {
                    if (level.getBlockEntity(primMintPos) instanceof PrimitiveMintMachineBlockEntity prim) {
                        prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                        if (enhanced && TyC2Ids.itemExists("mhera:alloy") && TyC2Ids.itemExists("mhera:iridium_ore")) {
                            prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(TyC2Ids.getItem("mhera:iridium_ore"), 1));
                            prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(TyC2Ids.getItem("mhera:alloy"), 1));
                        } else {
                            prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.NETHERITE_INGOT, 1));
                            prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.DIAMOND, 1));
                        }
                    }
                    tester.teleportTo(level, primMintPos.getX() + 0.5, surfaceY, primMintPos.getZ() + 2.2, 180.0F, 12.0F);
                    sendDirective("TEST_PRIMITIVE_MINT_BLOCKED_GUI", primMintPos, 0,
                            shotName("03b_primitive_mint_blocked.png"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(500, "TEST_PRIMITIVE_MINT_BLOCKED_GUI");
                if (ack != null) {
                    qa.check("live.primitive_mint_blocked_gui_verified", true, ack.summary());
                    advance(Stage.STEP_MINT_GUI);
                }
            }

            case STEP_MINT_GUI -> {
                if (waitingStepId < 0) {
                    if (level.getBlockEntity(mintPos) instanceof MintMachineBlockEntity mint) {
                        mint.setStoredEu(8000.0);
                        mint.setItem(MintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                        mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.NETHERITE_INGOT, 2));
                        mint.setItem(MintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.DIAMOND, 2));
                        if (enhanced) {
                            mint.setItem(MintMachineBlockEntity.SLOT_BATTERY,
                                    TyC2QaIc2Checks.createChargedIc2Battery(10_000.0));
                        } else {
                            mint.setItem(MintMachineBlockEntity.SLOT_BATTERY,
                                    TyC2QaMod.createQaFeBattery(40_000));
                        }
                    }
                    tester.teleportTo(level, mintPos.getX() + 0.5, surfaceY, mintPos.getZ() + 2.2, 180.0F, 12.0F);
                    sendDirective("TEST_MINT_GUI", mintPos, 0,
                            shotName("03_mint_machine_gui.png"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(500, "TEST_MINT_GUI");
                if (ack != null) {
                    qa.check("live.mint_gui_verified", true, ack.summary());
                    advance(Stage.STEP_ATM_GUI);
                }
            }

            case STEP_ATM_GUI -> {
                if (waitingStepId < 0) {
                    if (level.getBlockEntity(atmPos) instanceof AtmMachineBlockEntity atm) {
                        atm.receiveEu(6400.0);
                        atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, new ItemStack(TyC2ForgeRegistries.COIN_100.get(), 5));
                    }
                    tester.teleportTo(level, atmPos.getX() + 0.5, surfaceY, atmPos.getZ() + 2.2, 180.0F, 12.0F);
                    sendDirective("TEST_ATM_GUI", atmPos, 0,
                            shotName("04_atm_machine_gui.png"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(600, "TEST_ATM_GUI");
                if (ack != null) {
                    qa.check("live.atm_gui_verified", true, ack.summary());
                    // Ensure player's active bank account has plenty of balance for Hub trade test
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                    BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(tester.getUUID());
                    data.bank().creditAccount(ref, 2000L, BankAccountManager.TransactionType.SYSTEM_ADJUST,
                            level.getGameTime(), "QA", "Live trade capital");
                    data.syncMacroAccountTotals();
                    data.setDirty();
                    advance(Stage.STEP_HUB_GUI);
                }
            }

            case STEP_HUB_GUI -> {
                if (waitingStepId < 0) {
                    tester.teleportTo(level, bellHubPos.getX() + 0.5, surfaceY, bellHubPos.getZ() + 2.2, 180.0F, 12.0F);
                    sendDirective("TEST_HUB_GUI", bellHubPos, 0, shotName("05"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(1000, "TEST_HUB_GUI");
                if (ack != null) {
                    TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
                    String renamed = data.getVillage(villageId).map(VillageRecord::getVillageName).orElse("");
                    qa.checkEquals("live.hub_gui_rename_persisted", "Forge Plaza", renamed);
                    qa.check("live.hub_gui_verified", true, ack.summary());
                    advance(Stage.STEP_TERMINAL_GUI);
                }
            }

            case STEP_TERMINAL_GUI -> {
                if (waitingStepId < 0) {
                    // Place charged comm_terminal in hotbar slot 0 and ensure selected!
                    ItemStack term = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
                    CommTerminalItem.setStoredEu(term, 18_500);
                    tester.getInventory().setItem(0, term);
                    tester.getInventory().selected = 0;
                    tester.inventoryMenu.broadcastChanges();
                    tester.teleportTo(level, bellHubPos.getX() + 0.5, surfaceY, bellHubPos.getZ() + 2.0, 180.0F, 15.0F);
                    sendDirective("TEST_TERMINAL_GUI", bellHubPos, 0, shotName("06"));
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(1000, "TEST_TERMINAL_GUI");
                if (ack != null) {
                    qa.check("live.terminal_gui_verified", true, ack.summary());
                    advance(Stage.STEP_FINISH);
                }
            }

            case STEP_FINISH -> {
                if (waitingStepId < 0) {
                    sendDirective("FINISH_AND_DISCONNECT", BlockPos.ZERO, 0, "done");
                    return;
                }
                TyC2LiveNetwork.C2SLiveAck ack = pollAck(300, "FINISH_AND_DISCONNECT");
                if (ack != null) {
                    qa.check("live.client_clean_finish", true, ack.summary());
                    qa.writePassedReport(reportPath(), Map.of("profile", profile, "lang", lang));
                    advance(Stage.WAIT_HALT);
                }
            }

            case WAIT_HALT -> {
                if (stageTicks >= 15) {
                    finished = true;
                    advance(Stage.DONE);
                    server.halt(false);
                }
            }

            case DONE -> {
            }
        }
    }

    private void setupPlaza(MinecraftServer server, ServerLevel level) throws Exception {
        surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        level.setDefaultSpawnPos(new BlockPos(0, surfaceY, 6), 180.0F);
        level.setDayTime(6000L);
        level.setWeatherParameters(100000, 0, false, false);

        // Decorative stone-brick plaza floor around (0, surfaceY - 1, 0)
        for (int x = -6; x <= 6; x++) {
            for (int z = -5; z <= 6; z++) {
                boolean border = Math.abs(x) == 6 || z == -5 || z == 6;
                level.setBlock(
                        new BlockPos(x, surfaceY - 1, z),
                        border ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
        // Decorative oak-post lanterns flanking the plaza corners
        for (int lx : new int[]{-6, 6}) {
            level.setBlock(new BlockPos(lx, surfaceY, -4), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(lx, surfaceY + 1, -4), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(lx, surfaceY + 2, -4), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        bellHubPos = new BlockPos(0, surfaceY, 0);
        primMintPos = new BlockPos(-1, surfaceY, -2);
        mintPos = new BlockPos(-3, surfaceY, -2);
        atmPos = new BlockPos(2, surfaceY, -2);
        ic2GenPos = new BlockPos(-5, surfaceY, -2);
        ic2CablePos = new BlockPos(-4, surfaceY, -2);

        level.setBlock(bellHubPos, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(
                primMintPos,
                TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState()
                        .setValue(PrimitiveMintMachineBlock.FACING, Direction.SOUTH),
                Block.UPDATE_ALL
        );
        if (level.getBlockEntity(primMintPos) instanceof PrimitiveMintMachineBlockEntity prim) {
            prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 16));
            prim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 16));
        }
        level.setBlock(
                mintPos,
                TyC2ForgeRegistries.MINT_MACHINE_BLOCK.get().defaultBlockState()
                        .setValue(MintMachineBlock.FACING, Direction.SOUTH),
                Block.UPDATE_ALL
        );
        level.setBlock(
                atmPos,
                TyC2ForgeRegistries.ATM_MACHINE_BLOCK.get().defaultBlockState()
                        .setValue(AtmMachineBlock.FACING, Direction.SOUTH),
                Block.UPDATE_ALL
        );

        if (enhanced) {
            qa.check("live.enhanced_mods_loaded",
                    ModList.get().isLoaded("mhera") && ModList.get().isLoaded("trera"),
                    "ic2=" + ModList.get().isLoaded("mhera") + ", lc2=" + ModList.get().isLoaded("trera"));
            TyC2QaIc2Checks.setupGeneratorCableAndMachines(
                    level, ic2GenPos, ic2CablePos, mintPos, new BlockPos(-5, surfaceY, -3),
                    new BlockPos(-5, surfaceY, -4), qa
            );
            // Re-orient Minting Machine SOUTH so the animated front panel faces the player!
            level.setBlock(
                    mintPos,
                    TyC2ForgeRegistries.MINT_MACHINE_BLOCK.get().defaultBlockState()
                            .setValue(MintMachineBlock.FACING, Direction.SOUTH),
                    Block.UPDATE_ALL
            );
            // Place an LC2 Cargo Station nearby so Hub & Comm Terminal display nearby LC2 stations!
            Block lc2Cargo = TyC2Ids.parseBlockState("trera:cargo_station").getBlock();
            if (lc2Cargo != Blocks.AIR) {
                level.setBlock(new BlockPos(4, surfaceY, -2), lc2Cargo.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else {
            qa.check("live.standalone_no_ic2_lc2",
                    !ModList.get().isLoaded("mhera") && !ModList.get().isLoaded("trera"),
                    "standalone pure vanilla");
            if (level.getBlockEntity(mintPos) instanceof MintMachineBlockEntity mint) {
                mint.setStoredEu(8000.0);
                mint.setItem(MintMachineBlockEntity.SLOT_BATTERY, TyC2QaMod.createQaFeBattery(40_000));
            }
        }

        // Spawn 3 adult villagers around the Bell for automatic real-player discovery & Mayor election!
        spawnVillager(level, new BlockPos(-1, surfaceY, 1), VillagerProfession.FARMER);
        spawnVillager(level, new BlockPos(1, surfaceY, 1), VillagerProfession.LIBRARIAN);
        spawnVillager(level, new BlockPos(2, surfaceY, 2), VillagerProfession.TOOLSMITH);

        // Pre-populate a realistic 16-point CPI trend history so the Hub & Terminal CPI Line Chart looks vivid!
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        MacroEconomyEngine econ = data.economy();
        long t = 100L;
        for (int i = 0; i < 16; i++) {
            if (i % 3 == 0) {
                econ.recordMinting(1800L + i * 250L);
            } else if (i % 3 == 1) {
                econ.recordTrade("minecraft:iron_ingot", 16, 12L, true);
            } else {
                econ.recordVillageMoneySink(900L);
            }
            econ.advanceDemandCycle(t + i * 1200L);
        }
        data.setDirty();

        // Signal Python orchestrator that dedicated server is ready on its bound port!
        Properties ready = new Properties();
        ready.setProperty("port", Integer.toString(server.getPort()));
        ready.setProperty("profile", profile);
        try (Writer w = Files.newBufferedWriter(Path.of("live-server-ready.properties"))) {
            ready.store(w, "TyC2 Live Server Ready");
        }
        TyC2QaMod.LOGGER.info("[TYC2-LIVE-SERVER] Plaza ready on port {} (profile={})", server.getPort(), profile);
    }

    private void stockPlayerHotbarAndSupplies(MinecraftServer server, ServerPlayer player) {
        player.getInventory().clearContent();
        // Hotbar: Comm Terminal + representative denominations (1x, 5x, 10x, 20x, 50x, 100x, 500x, 1000x)
        ItemStack term = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(term, 18_500);
        player.getInventory().setItem(0, term);
        player.getInventory().setItem(1, new ItemStack(TyC2ForgeRegistries.COIN_1.get(), 16));
        player.getInventory().setItem(2, new ItemStack(TyC2ForgeRegistries.COIN_5.get(), 8));
        player.getInventory().setItem(3, new ItemStack(TyC2ForgeRegistries.COIN_10.get(), 8));
        player.getInventory().setItem(4, new ItemStack(TyC2ForgeRegistries.COIN_20.get(), 4));
        player.getInventory().setItem(5, new ItemStack(TyC2ForgeRegistries.COIN_50.get(), 4));
        player.getInventory().setItem(6, new ItemStack(TyC2ForgeRegistries.COIN_100.get(), 4));
        player.getInventory().setItem(7, new ItemStack(TyC2ForgeRegistries.COIN_500.get(), 2));
        player.getInventory().setItem(8, new ItemStack(TyC2ForgeRegistries.COIN_1000.get(), 1));

        // Main inventory: coin_200 + Mayor negotiation package (32 oak logs, 16 iron ingots, 32 bread)
        player.getInventory().add(new ItemStack(TyC2ForgeRegistries.COIN_200.get(), 2));
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        player.getInventory().add(new ItemStack(Items.BREAD, 32));
        player.inventoryMenu.broadcastChanges();
    }

    private static void spawnVillager(ServerLevel level, BlockPos pos, VillagerProfession prof) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v != null) {
            v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            v.setAge(0);
            v.setVillagerData(v.getVillagerData().setProfession(prof).setLevel(2));
            v.setVillagerXp(20);
            v.setPersistenceRequired();
            level.addFreshEntity(v);
        }
    }

    private static Villager findVillager(ServerLevel level, BlockPos center, UUID uuid) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(32.0), Villager::isAlive)) {
            if (uuid.equals(v.getUUID())) {
                return v;
            }
        }
        return null;
    }
}
