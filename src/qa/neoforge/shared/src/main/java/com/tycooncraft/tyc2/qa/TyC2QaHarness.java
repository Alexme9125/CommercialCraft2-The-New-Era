package com.tycooncraft.tyc2.qa;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.block.MintMachineBlock;
import com.tycooncraft.tyc2.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.blockentity.MintMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.TradeGood;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.ConstructionJob;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import com.tycooncraft.tyc2.world.TycoonSavedData;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live dedicated-server end-to-end QA harness mod ({@code tyc2_qa}) for NeoForge 1.21.1, 26.1.2, and 26.2.
 * Supports BOTH {@code standalone} (NO IC2, NO LC2) and {@code enhanced} (IC2 1.0.5 + LC2 1.1.0)
 * profiles with full {@code write} + cold-restart {@code read} persistence verification.
 */
@Mod("tyc2_qa")
public final class TyC2QaHarness {

    private static final Logger LOGGER = LoggerFactory.getLogger("tyc2_qa");

    private static final UUID PLAYER_A_UUID = UUID.fromString("11111111-2222-3333-4444-555555555551");
    private static final UUID PLAYER_B_UUID = UUID.fromString("11111111-2222-3333-4444-555555555552");

    private final String mode = System.getProperty("tyc2.qa.mode", "flat");
    private final String profile = System.getProperty("tyc2.qa.profile", "standalone");
    private final String phase = System.getProperty("tyc2.qa.phase", "write");
    private final TyC2QaChecks checks = new TyC2QaChecks();
    private final TyC2ClientLiveServer clientLiveServer = new TyC2ClientLiveServer();

    private int ticks = 0;
    private int stage = 0;
    private int stageStartTick = 0;
    private boolean finished = false;

    private int surfaceY;
    private FakePlayer playerA;
    private FakePlayer playerB;

    // Standalone state tracking
    private BlockPos primMintPos;
    private BlockPos mintPos;
    private BlockPos atmPos;
    private BlockPos bellPos;
    private BlockPos protectedChestPos;
    private List<BlockPos> protectedRoadPositions = List.of();
    private UUID villageId;
    private String companyId;
    private boolean primMintSawActive = false;
    private boolean mintSawActive = false;
    private boolean mintCoalChecked = false;
    private int expansionInitialPlaced = -1;
    private int expansionMaxIntermediatePlaced = -1;
    private int expansionTotalBlocks = 0;
    private int expansionObservedSteps = 0;
    private int autoFulfillInitialBank = 0;
    private String autoFulfillOrderId;

    // Enhanced state tracking
    private BlockPos ic2GenMintPos;
    private BlockPos ic2CableMintPos;
    private BlockPos ic2MintPos;
    private BlockPos ic2GenBatPos;
    private BlockPos ic2CableBatPos;
    private BlockPos ic2BatBoxPos;
    private BlockPos enhPrimGenPos;
    private BlockPos enhPrimCablePos;
    private BlockPos enhPrimPos;
    private BlockPos enhPrimBlockedPos;
    private BlockPos enhBatteryMintPos;
    private BlockPos enhIridiumMintPos;
    private int batBoxChargeSlot = 0;
    private BlockPos enhancedHubPos;
    private BlockPos enhancedLc2StationPos;

    public TyC2QaHarness(IEventBus modBus, Dist side) {
        if (side == Dist.CLIENT) {
            ClientEntry.install();
        }
        if ("client_only".equals(mode)) {
            return;
        }
        if ("client_live".equals(mode)) {
            clientLiveServer.install();
        }
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onServerTick);
    }

    private static final class ClientEntry {
        private static void install() {
            TyC2ClientDriver.install();
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        checks.putMeta("mcVersion", System.getProperty("tyc2.qa.mcVersion", "unknown"));
        checks.putMeta("profile", profile);
        try {
            if ("client_live".equals(mode)) {
                clientLiveServer.onServerStarted(server);
                return;
            }
            TyC2QaChecks.setChunkRangeForced(level, -2, 5, -2, 5, true);
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 20, 20) - 1;
            this.surfaceY = groundY + 1;
            this.primMintPos = new BlockPos(5, surfaceY, 8);
            this.mintPos = new BlockPos(8, surfaceY, 8);
            this.atmPos = new BlockPos(12, surfaceY, 8);
            this.bellPos = new BlockPos(21, surfaceY, 21); // Deliberately odd coords relative to (20, y, 20)!
            this.protectedChestPos = new BlockPos(bellPos.getX() + 12, surfaceY, bellPos.getZ());
            this.protectedRoadPositions = List.of(
                    new BlockPos(bellPos.getX() + 10, surfaceY - 1, bellPos.getZ() - 2),
                    new BlockPos(bellPos.getX() + 10, surfaceY - 1, bellPos.getZ() - 1),
                    new BlockPos(bellPos.getX() + 10, surfaceY - 1, bellPos.getZ()),
                    new BlockPos(bellPos.getX() + 10, surfaceY - 1, bellPos.getZ() + 1),
                    new BlockPos(bellPos.getX() + 10, surfaceY - 1, bellPos.getZ() + 2)
            );
            this.ic2GenMintPos = new BlockPos(8, surfaceY, 8);
            this.ic2CableMintPos = new BlockPos(9, surfaceY, 8);
            this.ic2MintPos = new BlockPos(10, surfaceY, 8);
            this.ic2GenBatPos = new BlockPos(8, surfaceY, 14);
            this.ic2CableBatPos = new BlockPos(9, surfaceY, 14);
            this.ic2BatBoxPos = new BlockPos(10, surfaceY, 14);
            this.enhPrimGenPos = new BlockPos(8, surfaceY, 18);
            this.enhPrimCablePos = new BlockPos(9, surfaceY, 18);
            this.enhPrimPos = new BlockPos(10, surfaceY, 18);
            this.enhPrimBlockedPos = new BlockPos(13, surfaceY, 18);
            this.enhBatteryMintPos = new BlockPos(14, surfaceY, 8);
            this.enhIridiumMintPos = new BlockPos(17, surfaceY, 8);
            this.enhancedHubPos = new BlockPos(24, surfaceY, 24);
            this.enhancedLc2StationPos = new BlockPos(30, surfaceY, 24);

            verifyLoadedArtifacts();
            server.tickRateManager().requestGameToSprint(8_000);
        } catch (Throwable t) {
            complete(server, t);
        }
    }

    private void verifyLoadedArtifacts() throws IOException {
        Path tyc2Jar = ModList.get().getModContainerById("tyc2").orElseThrow()
                .getModInfo().getOwningFile().getFile().getFilePath();
        String expectedTyc2 = System.getProperty("tyc2.qa.expectedTyc2Jar", tyc2Jar.getFileName().toString());
        checks.putMeta("tyc2Artifact", tyc2Jar.getFileName().toString());
        checks.check("boot.jar.tyc2_from_mods",
                Files.isSameFile(tyc2Jar, Path.of("mods", expectedTyc2)), tyc2Jar.getFileName().toString());

        if ("standalone".equals(profile)) {
            checks.check("boot.standalone.no_ic2", !ModList.get().isLoaded("ic2") && !Ic2SoftBridge.isIc2Loaded());
            checks.check("boot.standalone.no_lc2", !ModList.get().isLoaded("lc2") && !Lc2SoftBridge.isLc2Loaded());
        } else {
            checks.check("boot.enhanced.ic2_loaded", ModList.get().isLoaded("ic2") && Ic2SoftBridge.isIc2Loaded());
            checks.check("boot.enhanced.lc2_loaded", ModList.get().isLoaded("lc2") && Lc2SoftBridge.isLc2Loaded());
            Path ic2Jar = ModList.get().getModContainerById("ic2").orElseThrow()
                    .getModInfo().getOwningFile().getFile().getFilePath();
            Path lc2Jar = ModList.get().getModContainerById("lc2").orElseThrow()
                    .getModInfo().getOwningFile().getFile().getFilePath();
            checks.putMeta("ic2Artifact", ic2Jar.getFileName().toString());
            checks.putMeta("lc2Artifact", lc2Jar.getFileName().toString());
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (finished) {
            return;
        }
        MinecraftServer server = event.getServer();
        if ("client_live".equals(mode)) {
            clientLiveServer.onServerTick(server);
            return;
        }
        ServerLevel level = server.overworld();
        ticks++;
        try {
            if (ticks > 5_000) {
                throw new AssertionError("TyC2 QA tick budget exceeded at profile=" + profile + " phase=" + phase + " stage=" + stage);
            }
            if ("read".equals(phase)) {
                if ("standalone".equals(profile)) {
                    runStandaloneRead(server, level);
                } else {
                    runEnhancedRead(server, level);
                }
            } else {
                if ("standalone".equals(profile)) {
                    runStandaloneWrite(server, level);
                } else {
                    runEnhancedWrite(server, level);
                }
            }
        } catch (Throwable t) {
            complete(server, t);
        }
    }

    // =========================================================================
    // STANDALONE WRITE SUITE (Pure Vanilla + TyC2 ONLY)
    // =========================================================================

    private void runStandaloneWrite(MinecraftServer server, ServerLevel level) throws Exception {
        switch (stage) {
            case 0 -> {
                // Stage 0: Configs, Datapacks, Registries, Crafting Recipes, Commands
                checks.check("config.general_created", Files.isRegularFile(Path.of("config/tyc2/general.json")));
                checks.check("config.custom_goods_created", Files.isRegularFile(Path.of("config/tyc2/custom_goods.json")));
                checks.check("config.custom_mint_created", Files.isRegularFile(Path.of("config/tyc2/custom_mint_recipes.json")));

                checks.check("datapack.mint_recipes_loaded",
                        TyC2DataLoader.mintRecipes().allRecipes().size() >= 18,
                        "count=" + TyC2DataLoader.mintRecipes().allRecipes().size());
                checks.check("datapack.goods_loaded",
                        TyC2DataLoader.goodsCatalog().allGoods().size() >= 100,
                        "count=" + TyC2DataLoader.goodsCatalog().allGoods().size());
                checks.check("datapack.levels_loaded",
                        TyC2DataLoader.levelTable().allLevels().size() == 10,
                        "levels=" + TyC2DataLoader.levelTable().allLevels().size());

                for (String b : List.of(
                        "tyc2:primitive_mint_machine", "tyc2:mint_machine", "tyc2:atm_machine", "tyc2:cooperation_hub")) {
                    checks.check("reg.block." + b, TyC2QaChecks.hasBlock(b));
                    checks.check("reg.be." + b, TyC2QaChecks.hasBlockEntity(b));
                }
                for (String it : List.of(
                        "tyc2:primitive_mint_machine", "tyc2:mint_machine", "tyc2:atm_machine",
                        "tyc2:cooperation_hub", "tyc2:comm_terminal",
                        "tyc2:coin_1", "tyc2:coin_5", "tyc2:coin_10", "tyc2:coin_20", "tyc2:coin_50",
                        "tyc2:coin_100", "tyc2:coin_200", "tyc2:coin_500", "tyc2:coin_1000")) {
                    checks.check("reg.item." + it, TyC2QaChecks.hasItem(it));
                }
                for (String m : List.of(
                        "tyc2:primitive_mint_machine", "tyc2:mint_machine", "tyc2:atm_machine",
                        "tyc2:cooperation_hub", "tyc2:comm_terminal", "tyc2:mayor_negotiation")) {
                    checks.check("reg.menu." + m, TyC2QaChecks.hasMenu(m));
                }

                Set<String> recipes = TyC2QaPlatform.allRecipeIds(server);
                for (String r : List.of(
                        "tyc2:primitive_mint_machine", "tyc2:mint_machine_standalone",
                        "tyc2:atm_machine", "tyc2:comm_terminal")) {
                    checks.check("recipe.present." + r, recipes.contains(r), "in RecipeManager");
                }
                checks.check("recipe.absent.tyc2:mint_machine_ic2",
                        !recipes.contains("tyc2:mint_machine_ic2"),
                        "IC2 conditional recipe skipped standalone");

                checks.check("cmd.tyc2_status", TyC2QaPlatform.executeCommand(server, "tyc2 status") >= 1);
                checks.check("cmd.tyc2_cpi", TyC2QaPlatform.executeCommand(server, "tyc2 cpi") >= 1);
                checks.check("cmd.tyc2_season", TyC2QaPlatform.executeCommand(server, "tyc2 season") >= 1);

                playerA = TyC2QaPlatform.createFakePlayer(level, PLAYER_A_UUID, "TycoonAlice");
                playerB = TyC2QaPlatform.createFakePlayer(level, PLAYER_B_UUID, "TycoonBob");

                // 1. Place Primitive Minting Machine (fuel-fired, NO FE capability, 1x..100x Vanilla at 1.5x)
                level.setBlockAndUpdate(primMintPos, TyC2Registries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState());
                PrimitiveMintMachineBlockEntity primMint =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
                checks.check("primitive_mint.be_created", primMint != null);
                int primFe = TyC2QaPlatform.injectFeIntoBlock(level, primMintPos, 400);
                checks.check("primitive_mint.no_fe_capability", primFe < 0, "feResult=" + primFe);
                primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
                primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 1));

                // 2. Place Pure-Electric Industrial Minting Machine (rejects coal/redstone in slot 3!)
                level.setBlockAndUpdate(mintPos, TyC2Registries.MINT_MACHINE_BLOCK.get().defaultBlockState());
                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintPos);
                checks.check("mint.be_created", mint != null);
                checks.check("mint.rejects_solid_fuel_slot3",
                        !mint.canPlaceItem(MintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL))
                                && !mint.canPlaceItem(MintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.REDSTONE)),
                        "coal & redstone rejected in battery slot");
                // Briefly force coal into slot 3 for 5 ticks to prove coal burns 0 EU on pure-electric Mint
                mint.setItem(MintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
                mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 1));
                mint.setItem(MintMachineBlockEntity.SLOT_TERMINAL, new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get()));

                stage = 1;
                stageStartTick = ticks;
            }
            case 1 -> {
                // Stage 1: Verify pure-electric Mint burns 0 EU from coal, then power it via FE alongside Primitive Mint!
                PrimitiveMintMachineBlockEntity primMint =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintPos);
                BlockState primState = level.getBlockState(primMintPos);
                if (primState.hasProperty(PrimitiveMintMachineBlock.ACTIVE)
                        && primState.getValue(PrimitiveMintMachineBlock.ACTIVE)) {
                    primMintSawActive = true;
                }
                BlockState state = level.getBlockState(mintPos);
                if (state.hasProperty(MintMachineBlock.ACTIVE) && state.getValue(MintMachineBlock.ACTIVE)) {
                    mintSawActive = true;
                }

                if (!mintCoalChecked) {
                    if (ticks - stageStartTick >= 5) {
                        checks.check("mint.coal_produces_zero_eu",
                                mint.storedEu() == 0.0 && mint.progress() == 0,
                                "eu=" + mint.storedEu() + ", prog=" + mint.progress());
                        mint.setItem(MintMachineBlockEntity.SLOT_FUEL, ItemStack.EMPTY);
                        int feAccepted = TyC2QaPlatform.injectFeIntoBlock(level, mintPos, 400);
                        checks.check("mint.fe_capability_accepts", feAccepted > 0, "acceptedFe=" + feAccepted);
                        mintCoalChecked = true;
                    }
                    return;
                }

                // Feed 128 FE/t (= 32 EU/t) into pure-electric Industrial Minting Machine
                TyC2QaPlatform.injectFeIntoBlock(level, mintPos, 128);

                ItemStack primOut = primMint.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT);
                ItemStack out = mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT);
                if (!primOut.isEmpty() && !out.isEmpty()) {
                    checks.check("primitive_mint.active_blockstate_seen", primMintSawActive);
                    checks.check("primitive_mint.duration_1_5x", primMint.maxProgress() == 90,
                            "maxProgress=" + primMint.maxProgress());
                    checks.check("primitive_mint.coal_burned", primMint.burnTimeRemaining() > 0,
                            "burnRem=" + primMint.burnTimeRemaining());
                    checks.check("primitive_mint.coin1_output",
                            "tyc2:coin_1".equals(TyC2Native.itemId(primOut)) && primOut.getCount() == 2,
                            "out=" + TyC2Native.itemId(primOut) + " x" + primOut.getCount());

                    checks.check("mint.active_blockstate_seen", mintSawActive);
                    checks.check("mint.standalone_coin1_output",
                            "tyc2:coin_1".equals(TyC2Native.itemId(out)) && out.getCount() == 2,
                            "out=" + TyC2Native.itemId(out) + " x" + out.getCount());
                    checks.check("mint.money_supply_injected",
                            TyC2WorldManager.getData(level).economy().totalMintedNet() >= 4L,
                            "mintedNet=" + TyC2WorldManager.getData(level).economy().totalMintedNet());
                    ItemStack term = mint.getItem(MintMachineBlockEntity.SLOT_TERMINAL);
                    checks.check("mint.terminal_charged_pure_electric",
                            CommTerminalItem.getEnergyEu(term) > 0,
                            "terminalEu=" + CommTerminalItem.getEnergyEu(term));

                    // Stage 2: Primitive Mint runs Vanilla 100x (2x Gold + Emerald, 210 ticks) while
                    // Industrial Mint runs 1000x (Netherite + Diamond, 240 ticks) via FE!
                    primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.GOLD_INGOT, 2));
                    primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.EMERALD, 1));

                    mint.setItem(MintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.NETHERITE_INGOT, 1));
                    mint.setItem(MintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.DIAMOND, 1));
                    stage = 2;
                    stageStartTick = ticks;
                } else if (ticks - stageStartTick > 250) {
                    throw new AssertionError("Stage 1 minting timed out; primOut=" + primOut
                            + " primProg=" + primMint.progress() + " burn=" + primMint.burnTimeRemaining()
                            + "; mintOut=" + out + " eu=" + mint.storedEu() + " prog=" + mint.progress());
                }
            }
            case 2 -> {
                // Stage 2: Feed FE to Industrial Mint until both Primitive 100x & Industrial 1000x finish!
                TyC2QaPlatform.injectFeIntoBlock(level, mintPos, 128);
                PrimitiveMintMachineBlockEntity primMint =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintPos);
                ItemStack primOut = primMint.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT);
                ItemStack out = mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT);
                if (!primOut.isEmpty() && !out.isEmpty()) {
                    checks.check("primitive_mint.coin100_vanilla_allowed",
                            "tyc2:coin_100".equals(TyC2Native.itemId(primOut)) && primOut.getCount() == 1
                                    && primMint.maxProgress() == 210,
                            "out=" + TyC2Native.itemId(primOut) + " x" + primOut.getCount()
                                    + ", maxProgress=" + primMint.maxProgress());
                    checks.check("mint.coin_1000_dual_input",
                            "tyc2:coin_1000".equals(TyC2Native.itemId(out)) && out.getCount() == 1,
                            "out=" + TyC2Native.itemId(out) + " x" + out.getCount());
                    checks.check("mint.coin_1000_injected_supply",
                            TyC2WorldManager.getData(level).economy().totalMintedNet() >= 1104L,
                            "mintedNet=" + TyC2WorldManager.getData(level).economy().totalMintedNet());

                    // Load high-tier 200x (Diamond + 2x Gold) into Primitive Mint -> MUST BLOCK!
                    primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.DIAMOND, 1));
                    primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.GOLD_INGOT, 2));

                    // Setup ATM + test personal/company accounts, deposit, withdraw, transfer, and terminal charging
                    level.setBlockAndUpdate(atmPos, TyC2Registries.ATM_MACHINE_BLOCK.get().defaultBlockState());
                    AtmMachineBlockEntity atm = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
                    checks.check("atm.be_created", atm != null);
                    playerA.setPos(atmPos.getX() + 1.0, atmPos.getY(), atmPos.getZ());
                    playerB.setPos(atmPos.getX() + 1.0, atmPos.getY(), atmPos.getZ() + 1.0);

                    TycoonSavedData data = TyC2WorldManager.getData(level);
                    BankAccountManager bank = data.bank();

                    // Deposit 1x coin_1000 into Alice's personal account via live ATM action handler
                    atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, new ItemStack(TyC2Registries.COIN_1000.get(), 1));
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("ATM", atmPos, "DEPOSIT_SLOT", "{}"));
                    checks.check("atm.personal_deposit",
                            bank.getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance() == 1000L,
                            "aliceBal=" + bank.getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance());

                    // Create Guild/Company account via ATM action
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("ATM", atmPos, "CREATE_COMPANY", "{\"name\":\"IronGuild\"}"));
                    List<BankAccountManager.CompanyAccount> cos = bank.getCompaniesForPlayer(PLAYER_A_UUID);
                    checks.check("atm.company_created", cos.size() == 1 && "IronGuild".equals(cos.get(0).name()),
                            "companies=" + cos.size());
                    BankAccountManager.CompanyAccount guild = cos.get(0);
                    this.companyId = guild.companyId();

                    // Bob joins IronGuild via invite code
                    TyC2WorldManager.handleActionPayload(playerB, level,
                            new TyC2ActionPayload("ATM", atmPos, "JOIN_COMPANY",
                                    "{\"code\":\"" + guild.inviteCode() + "\"}"));
                    checks.check("atm.company_member_joined",
                            bank.getCompany(companyId).orElseThrow().memberUuids().contains(PLAYER_B_UUID));

                    // Switch Alice back to personal, transfer 300 IC to IronGuild, transfer 150 IC to Bob
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("ATM", atmPos, "SWITCH_ACCOUNT", "{\"companyId\":\"\"}"));
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("ATM", atmPos, "TRANSFER",
                                    "{\"recipient\":\"IronGuild\",\"amount\":300}"));
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("ATM", atmPos, "TRANSFER",
                                    "{\"recipient\":\"TycoonBob\",\"amount\":150}"));

                    checks.check("atm.transfer_balances_exact",
                            bank.getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance() == 550L
                                    && bank.getCompany(companyId).orElseThrow().balance() == 300L
                                    && bank.getOrCreatePersonal(PLAYER_B_UUID, "TycoonBob").balance() == 150L,
                            "alice=550, guild=300, bob=150");

                    // Greedy withdraw 75 IC from Alice personal (50 + 20 + 5)
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("ATM", atmPos, "WITHDRAW_AMOUNT", "{\"amount\":75}"));
                    checks.check("atm.withdraw_balance_updated",
                            bank.getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance() == 475L);

                    // ATM vanilla fuel terminal charging (MUST STILL WORK standalone!)
                    atm.setItem(AtmMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2));
                    atm.setItem(AtmMachineBlockEntity.SLOT_TERMINAL, new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get()));

                    stage = 3;
                    stageStartTick = ticks;
                } else if (ticks - stageStartTick > 500) {
                    throw new AssertionError("Stage 2 timed out; primOut=" + primOut + " primProg=" + primMint.progress()
                            + "; mintOut=" + out + " eu=" + mint.storedEu() + " prog=" + mint.progress());
                }
            }
            case 3 -> {
                // Stage 3: Wait 15 ticks for ATM terminal charge + Primitive Mint high-tier block, then Bell/Mayor/Hub!
                if (ticks - stageStartTick < 15) {
                    return;
                }
                PrimitiveMintMachineBlockEntity primMint =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
                checks.check("primitive_mint.blocks_high_tier_200x",
                        primMint.isBlockedByIndustrialTier()
                                && primMint.progress() == 0
                                && primMint.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT).isEmpty(),
                        "blocked=" + primMint.isBlockedByIndustrialTier() + ", prog=" + primMint.progress());
                AtmMachineBlockEntity atm = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
                checks.check("atm.terminal_charged_vanilla_fuel",
                        CommTerminalItem.getEnergyEu(atm.getItem(AtmMachineBlockEntity.SLOT_TERMINAL)) > 0,
                        "eu=" + CommTerminalItem.getEnergyEu(atm.getItem(AtmMachineBlockEntity.SLOT_TERMINAL)));

                // Place Bell at ODD coordinate (21, surfaceY, 21) relative to player at (20, surfaceY, 20)
                level.setBlockAndUpdate(bellPos, Blocks.BELL.defaultBlockState());

                // Verify findNearbyBell discovers bells at odd coordinate offsets!
                Method findBell = TyC2WorldManager.class.getDeclaredMethod(
                        "findNearbyBell", ServerLevel.class, BlockPos.class, int.class
                );
                findBell.setAccessible(true);
                BlockPos discoveredBell = (BlockPos) findBell.invoke(null, level, new BlockPos(20, surfaceY, 20), 32);
                checks.check("village.bell_discovery_odd_coords",
                        bellPos.equals(discoveredBell),
                        "expected=" + bellPos + ", found=" + discoveredBell);

                // Create village record around Bell and test 1-villager rule vs 2+ villager rule
                TycoonSavedData data = TyC2WorldManager.getData(level);
                VillageRecord village = new VillageRecord(
                        UUID.randomUUID(),
                        TyC2Native.dimensionId(level),
                        "QaPlainsville",
                        new BlockPos3i(bellPos.getX(), bellPos.getY(), bellPos.getZ()),
                        BiomeStyle.PLAINS
                );
                this.villageId = village.getVillageId();
                data.putVillage(village);

                Method evalMayor = TyC2WorldManager.class.getDeclaredMethod(
                        "evaluateVillageMayorAndPop",
                        ServerLevel.class, TycoonSavedData.class, VillageRecord.class, BlockPos.class, long.class
                );
                evalMayor.setAccessible(true);

                // A. 1 Villager -> MUST NOT elect Mayor!
                Entity v1 = TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() + 2.5, surfaceY, bellPos.getZ() + 0.5);
                evalMayor.invoke(null, level, data, village, bellPos, level.getGameTime());
                checks.check("mayor.no_election_with_1_villager",
                        village.getMayorState().getMayorEntityUuid() == null
                                && !TyC2Nbt.getBoolean(v1.getPersistentData(), TyC2Constants.TAG_MAYOR, false),
                        "pop=" + village.getPopulation() + ", mayor=" + village.getMayorState().getMayorEntityUuid());

                // B. 3 Villagers -> MUST elect Mayor!
                Entity v2 = TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() + 3.5, surfaceY, bellPos.getZ() + 0.5);
                Entity v3 = TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() + 4.5, surfaceY, bellPos.getZ() + 0.5);
                evalMayor.invoke(null, level, data, village, bellPos, level.getGameTime());
                UUID firstMayorUuid = village.getMayorState().getMayorEntityUuid();
                checks.check("mayor.elected_with_2plus_villagers",
                        firstMayorUuid != null && village.getPopulation() == 3,
                        "mayor=" + firstMayorUuid + ", pop=" + village.getPopulation());
                Entity firstMayor = List.of(v1, v2, v3).stream()
                        .filter(e -> e.getUUID().equals(firstMayorUuid)).findFirst().orElseThrow();
                checks.check("mayor.persistent_tag_and_custom_name",
                        TyC2Nbt.getBoolean(firstMayor.getPersistentData(), TyC2Constants.TAG_MAYOR, false)
                                && firstMayor.hasCustomName());

                // C. Mayor death -> MUST immediately elect a different living villager!
                TyC2QaPlatform.killEntity(level, firstMayor);
                UUID secondMayorUuid = village.getMayorState().getMayorEntityUuid();
                checks.check("mayor.death_triggers_reelection",
                        secondMayorUuid != null && !secondMayorUuid.equals(firstMayorUuid),
                        "old=" + firstMayorUuid + ", new=" + secondMayorUuid);

                // Spawn 2 more villagers so village reaches 4 villagers (for Lv 2 upgrade test later)
                Entity nonMayor = List.of(v1, v2, v3).stream()
                        .filter(e -> e.isAlive() && !e.getUUID().equals(secondMayorUuid)).findFirst().orElseThrow();
                TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() - 2.5, surfaceY, bellPos.getZ() + 0.5);
                TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() - 3.5, surfaceY, bellPos.getZ() + 0.5);
                evalMayor.invoke(null, level, data, village, bellPos, level.getGameTime());

                // D. Partnership negotiation -> replaces Bell with blast-proof cooperation_hub!
                playerA.setPos(bellPos.getX() + 1.0, surfaceY, bellPos.getZ());
                playerA.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
                playerA.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
                playerA.getInventory().add(new ItemStack(Items.BREAD, 32));
                // Alice has 475 IC in personal bank account (covers 100 IC startup cost)
                TyC2WorldManager.handleActionPayload(playerA, level,
                        new TyC2ActionPayload("MAYOR", bellPos, "SIGN_PARTNERSHIP",
                                "{\"villageId\":\"" + villageId + "\"}"));

                BlockState hubState = level.getBlockState(bellPos);
                checks.check("partnership.bell_replaced_with_hub",
                        hubState.is(TyC2Registries.COOPERATION_HUB_BLOCK.get()),
                        "block=" + TyC2Native.blockId(hubState));
                checks.check("partnership.hub_blast_proof_and_unbreakable",
                        hubState.getDestroySpeed(level, bellPos) < 0.0F
                                && hubState.getBlock().getExplosionResistance() >= 3_600_000.0F,
                        "hardness=" + hubState.getDestroySpeed(level, bellPos)
                                + ", blastRes=" + hubState.getBlock().getExplosionResistance());
                checks.check("partnership.village_partnered_and_orders_generated",
                        village.isPartnered() && village.getVillageBalance() == 100L && !village.getActiveOrders().isEmpty(),
                        "partnered=" + village.isPartnered() + ", bal=" + village.getVillageBalance()
                                + ", orders=" + village.getActiveOrders().size());

                // E. Normal villagers blocked from vanilla emerald trading inside partnered village!
                boolean tradeBlocked = TyC2QaPlatform.fireVillagerInteract(level, playerA, nonMayor);
                checks.check("partnership.normal_villager_emerald_trade_blocked", tradeBlocked);

                // F. Survival player breaking Cooperation Hub is canceled!
                boolean breakBlocked = TyC2QaPlatform.fireSurvivalBreakBlock(level, playerA, bellPos);
                checks.check("partnership.hub_survival_break_blocked", breakBlocked);

                // G. Real ServerPlayerGameMode.useItemOn Sneak+Right-Click binds & unbinds CommTerminal (even with offhand item!)
                ItemStack qaTerm = new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get());
                CommTerminalItem.setEnergyEu(qaTerm, 5_000);
                playerA.setItemInHand(InteractionHand.MAIN_HAND, qaTerm);
                playerA.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 1));
                playerA.setShiftKeyDown(true);
                BlockHitResult hubHit = new BlockHitResult(
                        Vec3.atCenterOf(bellPos), Direction.NORTH, bellPos, false
                );
                InteractionResult bindRes = playerA.gameMode.useItemOn(
                        playerA, level, qaTerm, InteractionHand.MAIN_HAND, hubHit
                );
                boolean boundOk = bindRes != InteractionResult.PASS
                        && bindRes != InteractionResult.FAIL
                        && CommTerminalItem.getBoundVillages(qaTerm).containsKey(villageId)
                        && CommTerminalItem.getEnergyEu(qaTerm) == 5_000;
                checks.check("partnership.terminal_sneak_bind_via_useItemOn", boundOk,
                        "res=" + bindRes + ", bound=" + CommTerminalItem.getBoundVillages(qaTerm).size()
                                + ", eu=" + CommTerminalItem.getEnergyEu(qaTerm));
                InteractionResult unbindRes = playerA.gameMode.useItemOn(
                        playerA, level, qaTerm, InteractionHand.MAIN_HAND, hubHit
                );
                boolean unboundOk = unbindRes != InteractionResult.PASS
                        && unbindRes != InteractionResult.FAIL
                        && !CommTerminalItem.getBoundVillages(qaTerm).containsKey(villageId);
                checks.check("partnership.terminal_sneak_unbind_via_useItemOn", unboundOk,
                        "res=" + unbindRes + ", bound=" + CommTerminalItem.getBoundVillages(qaTerm).size());
                playerA.setShiftKeyDown(false);
                playerA.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                playerA.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

                // Stage 4: Test 1000-slot Inbound/Outbound pipe isolation, Sell->Outbound extract, Buy Auto-Fulfill
                TyC2QaPlatform.verifyHubCapabilityIsolation(level, bellPos, checks);

                // Execute a Village Sell Order -> item lands in Outbound 500..999 -> external extract succeeds!
                VillageTradeOrder sellOrder = village.getActiveOrders().stream()
                        .filter(o -> o.side() == OrderSide.VILLAGE_SELLS && !o.isCompleted())
                        .findFirst().orElseThrow(() -> new AssertionError("No Lv 1 VILLAGE_SELLS order generated"));
                TyC2WorldManager.handleActionPayload(playerA, level,
                        new TyC2ActionPayload("HUB", bellPos, "BUY_ORDER",
                                "{\"orderId\":\"" + sellOrder.orderId() + "\",\"qty\":1,\"cashMode\":false}"));
                int outSlotFound = -1;
                for (int s = CooperationHubStorage.OUTBOUND_START; s < CooperationHubStorage.OUTBOUND_END_EXCLUSIVE; s++) {
                    if (village.getStorage().getSlot(s).isPresent()) {
                        outSlotFound = s;
                        break;
                    }
                }
                checks.check("hub.sell_order_lands_in_outbound", outSlotFound >= 500, "slot=" + outSlotFound);
                ItemStack extractedOut = TyC2QaPlatform.extractFromHubOutboundSlot(level, bellPos, outSlotFound, 1);
                checks.check("hub.cap.outbound_extract_ok", !extractedOut.isEmpty(), "extracted=" + extractedOut);

                // Test Auto-Fulfill on a Village Buy Order via piped Inbound items
                VillageTradeOrder buyOrder = village.getActiveOrders().stream()
                        .filter(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted())
                        .findFirst().orElseThrow(() -> new AssertionError("No Lv 1 VILLAGE_BUYS order generated"));
                this.autoFulfillOrderId = buyOrder.orderId();
                village.adjustVillageBalance(500L); // Ensure village has ample liquidity
                TyC2WorldManager.handleActionPayload(playerA, level,
                        new TyC2ActionPayload("HUB", bellPos, "TOGGLE_ORDER_AUTO",
                                "{\"orderId\":\"" + autoFulfillOrderId + "\"}"));
                this.autoFulfillInitialBank = (int) data.bank().getActiveBalance(PLAYER_A_UUID);
                ItemStack deliverStack = TyC2Native.stack(buyOrder.itemId(), Math.min(4, buyOrder.remainingQty()));
                ItemStack pipeRem = TyC2QaPlatform.insertIntoHubInbound(level, bellPos, deliverStack);
                checks.check("hub.auto_fulfill_piped_inbound", pipeRem.isEmpty(), "item=" + buyOrder.itemId());

                stage = 4;
                stageStartTick = ticks;
            }
            case 4 -> {
                // Stage 4: Wait for Auto-Fulfill tick (40 ticks), then test CPI Inflation/Deflation + Lv 1->2 Upgrade + Expansion setup
                TycoonSavedData data = TyC2WorldManager.getData(level);
                VillageRecord village = data.getVillage(villageId).orElseThrow();
                VillageTradeOrder order = village.findOrder(autoFulfillOrderId).orElse(null);
                if (order != null && order.fulfilledQty() > 0) {
                    long bankAfter = data.bank().getActiveBalance(PLAYER_A_UUID);
                    checks.check("hub.auto_fulfill_executed_and_paid",
                            bankAfter > autoFulfillInitialBank,
                            "fulfilled=" + order.fulfilledQty() + ", bank=" + autoFulfillInitialBank + "->" + bankAfter);

                    // Macro-Economy CPI inflation on minting & deflation on sinks
                    MacroEconomyEngine econ = data.economy();
                    double cpiBase = econ.computeCpi();
                    TyC2WorldManager.recordCoinsMinted(level, 25_000L);
                    double cpiInflated = econ.computeCpi();
                    checks.check("macro.cpi_rises_on_minting",
                            cpiInflated > cpiBase,
                            String.format("base=%.4f -> inflated=%.4f", cpiBase, cpiInflated));

                    // Village Upgrade Lv 1 -> 2 (requires pop>=4, vol>=400, bal>=150; burns 100 IC sink)
                    village.setPopulation(Math.max(4, village.getPopulation()));
                    village.addTradeVolume(500L);
                    village.adjustVillageBalance(500L);
                    long balBeforeUpgrade = village.getVillageBalance();
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("HUB", bellPos, "UPGRADE_VILLAGE", "{}"));
                    checks.check("village.upgrade_lv1_to_lv2",
                            village.getLevel() == 2 && village.getVillageBalance() == balBeforeUpgrade - 100L,
                            "level=" + village.getLevel() + ", bal=" + balBeforeUpgrade + "->" + village.getVillageBalance());

                    // Verify deflationary response to heavy money sinks & goods circulation
                    econ.recordMoneySink(24_000L);
                    econ.recordTrade("minecraft:iron_ingot", 400, 10L, true);
                    econ.advanceDemandCycle(level.getGameTime());
                    double cpiAfterDeflation = econ.computeCpi();
                    checks.check("macro.cpi_responds_to_deflation_sinks",
                            cpiAfterDeflation < cpiInflated,
                            String.format("inflated=%.4f -> afterSinks=%.4f", cpiInflated, cpiAfterDeflation));

                    // Stage 5 Setup: Place protected Chest (BlockEntity with Diamond!) and player-placed Road strip
                    level.setBlockAndUpdate(protectedChestPos, Blocks.CHEST.defaultBlockState());
                    ChestBlockEntity chestBe = (ChestBlockEntity) level.getBlockEntity(protectedChestPos);
                    checks.check("expansion.protected_chest_placed", chestBe != null);
                    chestBe.setItem(0, new ItemStack(Items.DIAMOND, 3));
                    data.recordPlayerPlaced(TyC2Native.dimensionId(level), protectedChestPos);
                    for (BlockPos rp : protectedRoadPositions) {
                        level.setBlockAndUpdate(rp, Blocks.COBBLESTONE.defaultBlockState());
                        data.recordPlayerPlaced(TyC2Native.dimensionId(level), rp);
                    }

                    // Create ExpansionDesire (SMALL_COTTAGE), pipe materials into Inbound, deliver & launch gradual build!
                    ExpansionDesire desire = ExpansionDesire.createDefault(
                            "qa_exp_1", ProjectType.SMALL_COTTAGE, village.getBiomeStyle(), false
                    );
                    village.setActiveExpansion(desire);
                    village.adjustVillageBalance(1_000L);
                    for (Map.Entry<String, Integer> req : desire.requiredMaterials().entrySet()) {
                        village.getStorage().insertExternal(req.getKey(), req.getValue(), "", 64, false);
                    }
                    TyC2WorldManager.handleActionPayload(playerA, level,
                            new TyC2ActionPayload("HUB", bellPos, "DELIVER_EXPANSION", "{}"));

                    Optional<ConstructionJob> activeJob = village.getActiveConstructionJob();
                    checks.check("expansion.construction_job_launched",
                            activeJob.isPresent() && activeJob.get().getTotalBlocks() > 10,
                            "totalBlocks=" + activeJob.map(ConstructionJob::getTotalBlocks).orElse(0));
                    expansionTotalBlocks = activeJob.get().getTotalBlocks();
                    expansionInitialPlaced = activeJob.get().getPlacedCount();
                    stage = 5;
                    stageStartTick = ticks;
                } else if (ticks - stageStartTick > 120) {
                    throw new AssertionError("Auto-fulfill did not trigger within 120 ticks for order " + autoFulfillOrderId);
                }
            }
            case 5 -> {
                // Stage 5: Observe gradual bottom-up construction until completion & verify zero griefing
                TycoonSavedData data = TyC2WorldManager.getData(level);
                VillageRecord village = data.getVillage(villageId).orElseThrow();
                Optional<ConstructionJob> jobOpt = village.getActiveConstructionJob();
                if (jobOpt.isPresent()) {
                    int placed = jobOpt.get().getPlacedCount();
                    if (placed > expansionMaxIntermediatePlaced && placed < expansionTotalBlocks) {
                        expansionMaxIntermediatePlaced = placed;
                        expansionObservedSteps++;
                    }
                    if (ticks - stageStartTick > 2_000) {
                        throw new AssertionError("Gradual construction timed out at " + placed + "/" + expansionTotalBlocks);
                    }
                } else {
                    checks.check("expansion.gradual_multi_tick_build",
                            expansionObservedSteps >= 5,
                            "observedSteps=" + expansionObservedSteps + ", totalBlocks=" + expansionTotalBlocks);
                    checks.check("expansion.occupied_zone_registered",
                            village.getOccupiedZones().size() >= 2,
                            "zones=" + village.getOccupiedZones().size());

                    // Verify protected Chest (BlockEntity + 3 diamonds) is 100% intact!
                    BlockEntity beAfter = level.getBlockEntity(protectedChestPos);
                    boolean chestIntact = level.getBlockState(protectedChestPos).is(Blocks.CHEST)
                            && beAfter instanceof ChestBlockEntity c
                            && c.getItem(0).is(Items.DIAMOND)
                            && c.getItem(0).getCount() == 3;
                    checks.check("expansion.protected_chest_unharmed", chestIntact);

                    // Verify every player-placed road block is 100% intact!
                    boolean roadIntact = protectedRoadPositions.stream()
                            .allMatch(p -> level.getBlockState(p).is(Blocks.COBBLESTONE));
                    checks.check("expansion.protected_road_unharmed", roadIntact);

                    // Place persistence sentinels in Hub Inbound slot 10 and Outbound slot 510
                    village.getStorage().setSlotRaw(10, new SlotStack("minecraft:emerald", 13, ""));
                    village.getStorage().setSlotRaw(510, new SlotStack("minecraft:gold_ingot", 7, ""));
                    long aliceFinalBal = data.bank().getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance();
                    checks.putMeta("aliceFinalBalance", Long.toString(aliceFinalBal));
                    data.setDirty();

                    checks.check("standalone.write.world_saved", server.saveEverything(false, true, true));
                    complete(server, null);
                }
            }
            default -> complete(server, null);
        }
    }

    // =========================================================================
    // STANDALONE COLD-RESTART READ SUITE
    // =========================================================================

    private void runStandaloneRead(MinecraftServer server, ServerLevel level) throws IOException {
        if (ticks < 5) {
            return;
        }
        TycoonSavedData data = TyC2WorldManager.getData(level);
        checks.check("read.config_present", Files.isRegularFile(Path.of("config/tyc2/general.json")));
        long expectedAliceBal = -1L;
        Path writeReport = Path.of("qa-report-standalone-write.properties");
        if (Files.isRegularFile(writeReport)) {
            var p = new java.util.Properties();
            try (var r = Files.newBufferedReader(writeReport)) {
                p.load(r);
            }
            expectedAliceBal = Long.parseLong(p.getProperty("aliceFinalBalance", "-1"));
        }
        long aliceBal = data.bank().getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance();
        checks.check("read.bank_alice_preserved",
                expectedAliceBal > 0L ? aliceBal == expectedAliceBal : aliceBal >= 250L,
                "aliceBal=" + aliceBal + " (expected=" + expectedAliceBal + ")");
        checks.check("read.bank_bob_preserved",
                data.bank().getOrCreatePersonal(PLAYER_B_UUID, "TycoonBob").balance() == 150L,
                "bobBal=" + data.bank().getOrCreatePersonal(PLAYER_B_UUID, "TycoonBob").balance());
        checks.check("read.bank_company_preserved",
                data.bank().getCompaniesForPlayer(PLAYER_A_UUID).stream()
                        .anyMatch(co -> "IronGuild".equals(co.name()) && co.balance() == 300L));
        checks.check("read.economy_history_preserved",
                !data.economy().history().isEmpty() && data.economy().totalMintedNet() > 0L,
                "history=" + data.economy().history().size() + ", minted=" + data.economy().totalMintedNet());

        VillageRecord village = data.allVillages().stream()
                .filter(v -> "QaPlainsville".equals(v.getVillageName()))
                .findFirst().orElseThrow(() -> new AssertionError("Saved village QaPlainsville missing after restart"));
        checks.check("read.village_lv2_partnered_preserved",
                village.isPartnered() && village.getLevel() == 2,
                "lv=" + village.getLevel() + ", partnered=" + village.isPartnered());
        checks.check("read.village_mayor_preserved",
                village.getMayorState().getMayorEntityUuid() != null);
        checks.check("read.hub_block_and_be_preserved",
                level.getBlockState(bellPos).is(TyC2Registries.COOPERATION_HUB_BLOCK.get())
                        && level.getBlockEntity(bellPos) instanceof CooperationHubBlockEntity);
        checks.check("read.hub_inbound_sentinel_preserved",
                village.getStorage().getSlot(10)
                        .map(s -> "minecraft:emerald".equals(s.itemId()) && s.count() == 13).orElse(false));
        checks.check("read.hub_outbound_sentinel_preserved",
                village.getStorage().getSlot(510)
                        .map(s -> "minecraft:gold_ingot".equals(s.itemId()) && s.count() == 7).orElse(false));
        checks.check("read.occupied_zones_preserved", village.getOccupiedZones().size() >= 2);
        checks.check("read.player_placed_tracker_preserved",
                data.isPlayerPlaced(TyC2Native.dimensionId(level),
                        protectedChestPos.getX(), protectedChestPos.getY(), protectedChestPos.getZ()));

        checks.check("standalone.read.world_saved", server.saveEverything(false, true, true));
        complete(server, null);
    }

    // =========================================================================
    // ENHANCED WRITE SUITE (TyC2 + IC2 1.0.5 + LC2 1.1.0)
    // =========================================================================

    private void runEnhancedWrite(MinecraftServer server, ServerLevel level) throws Exception {
        switch (stage) {
            case 0 -> {
                playerA = TyC2QaPlatform.createFakePlayer(level, PLAYER_A_UUID, "TycoonAlice");

                // 0. Verify conditional crafting recipes in Enhanced (IC2 loaded) mode!
                Set<String> recipes = TyC2QaPlatform.allRecipeIds(server);
                checks.check("recipe.enhanced.present.tyc2:primitive_mint_machine",
                        recipes.contains("tyc2:primitive_mint_machine"), "unconditional Vanilla recipe loaded");
                checks.check("recipe.enhanced.present.tyc2:mint_machine_ic2",
                        recipes.contains("tyc2:mint_machine_ic2"), "IC2 conditional recipe loaded");
                checks.check("recipe.enhanced.absent.tyc2:mint_machine_standalone",
                        !recipes.contains("tyc2:mint_machine_standalone"), "Standalone conditional recipe skipped");

                // 1. Verify IC2 ProcessingRecipesMixin & WorkshopRecipesMixin disabled metal_former_cutting -> ic2:coin!
                Class<?> procClass = Class.forName("com.industrialage.ia2.content.ProcessingRecipes");
                Field builtinField = procClass.getDeclaredField("BUILTIN");
                builtinField.setAccessible(true);
                Object builtin = builtinField.get(null);
                Method findMethod = procClass.getDeclaredMethod("find", String.class, String.class, Predicate.class);
                findMethod.setAccessible(true);
                Predicate<String> noTags = t -> false;
                Object blockedCoinRecipe = findMethod.invoke(builtin, "metal_former_cutting", "ic2:iron_casing", noTags);
                Object allowedOtherRecipe = findMethod.invoke(builtin, "metal_former_cutting", "ic2:copper_plate", noTags);
                checks.check("mixin.ic2_processing_coin_recipe_disabled",
                        blockedCoinRecipe == null,
                        "iron_casing->coin=" + blockedCoinRecipe + ", copper_plate=" + allowedOtherRecipe);

                Class<?> wsClass = Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes");
                Method forType = wsClass.getDeclaredMethod("forType", String.class);
                forType.setAccessible(true);
                List<?> cuttingRecipes = (List<?>) forType.invoke(null, "metal_former_cutting");
                boolean anyIc2CoinWorkshop = cuttingRecipes != null && cuttingRecipes.stream()
                        .anyMatch(r -> r != null && r.toString().contains("ic2:coin"));
                checks.check("mixin.ic2_workshop_coin_recipe_disabled",
                        !anyIc2CoinWorkshop,
                        "cuttingCount=" + (cuttingRecipes == null ? -1 : cuttingRecipes.size()));

                // 2. Verify IC2 items (alloys, iridium, crops) appear in live GoodsCatalog!
                Set<String> availableIds = TyC2DataLoader.goodsCatalog()
                        .getAvailableGoods(TyC2Native::itemExists)
                        .stream().map(TradeGood::itemId).collect(Collectors.toSet());
                for (String ic2Good : List.of(
                        "ic2:resin", "ic2:hops", "ic2:coffee_beans",
                        "ic2:tin_ingot", "ic2:alloy", "ic2:iridium_shard")) {
                    checks.check("catalog.ic2_good_active." + ic2Good, availableIds.contains(ic2Good));
                }

                // 3. Test Ic2CableBlockMixin + Real IC2 EU delivery: Generator -> Cable -> MintMachine
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:generator", ic2GenMintPos);
                level.setBlockAndUpdate(ic2MintPos, TyC2Registries.MINT_MACHINE_BLOCK.get().defaultBlockState());
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:insulated_copper_cable", ic2CableMintPos);
                BlockState cableState = level.getBlockState(ic2CableMintPos);
                checks.check("mixin.ic2_cable_connects_to_mint",
                        cableState.hasProperty(BlockStateProperties.EAST)
                                && cableState.getValue(BlockStateProperties.EAST),
                        "cableState=" + cableState);

                // Prime Generator with coal & insert copper_ingot (NO battery in Mint!) -> must run strictly on IC2 EU!
                Container genMint = (Container) level.getBlockEntity(ic2GenMintPos);
                checks.check("ic2.generator_for_mint_present", genMint != null);
                genMint.setItem(0, new ItemStack(Items.COAL, 8));
                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(ic2MintPos);
                mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 1));

                // 3b. Verify IC2 cable does NOT visually connect to Primitive Minting Machine!
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:generator", enhPrimGenPos);
                level.setBlockAndUpdate(enhPrimPos, TyC2Registries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState());
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:insulated_copper_cable", enhPrimCablePos);
                BlockState primCableState = level.getBlockState(enhPrimCablePos);
                checks.check("ic2.cable_does_not_connect_to_primitive_mint",
                        primCableState.hasProperty(BlockStateProperties.EAST)
                                && !primCableState.getValue(BlockStateProperties.EAST),
                        "primCableState=" + primCableState);
                PrimitiveMintMachineBlockEntity enhPrim =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(enhPrimPos);
                enhPrim.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
                enhPrim.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 1));

                // 3c. Verify Primitive Mint BLOCKS IC2 Alloy (ic2:alloy + gold_ingot) while Industrial Mint mints IC2 Tin & Iridium 1000x!
                level.setBlockAndUpdate(enhPrimBlockedPos, TyC2Registries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState());
                PrimitiveMintMachineBlockEntity enhPrimBlocked =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(enhPrimBlockedPos);
                enhPrimBlocked.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
                enhPrimBlocked.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, TyC2Native.stack("ic2:alloy", 1));
                enhPrimBlocked.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.GOLD_INGOT, 1));

                // 3d. Verify pure-electric Mint discharges IC2 single_use_battery in Slot 3 & mints IC2 tin_ingot -> ic2:coin!
                level.setBlockAndUpdate(enhBatteryMintPos, TyC2Registries.MINT_MACHINE_BLOCK.get().defaultBlockState());
                MintMachineBlockEntity battMint = (MintMachineBlockEntity) level.getBlockEntity(enhBatteryMintPos);
                battMint.setItem(MintMachineBlockEntity.SLOT_BATTERY, TyC2Native.stack("ic2:single_use_battery", 1));
                battMint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, TyC2Native.stack("ic2:tin_ingot", 1));

                // 3e. Verify pure-electric Mint mints IC2 Endgame 1000x (ic2:iridium + ic2:alloy -> tyc2:coin_1000)!
                level.setBlockAndUpdate(enhIridiumMintPos, TyC2Registries.MINT_MACHINE_BLOCK.get().defaultBlockState());
                MintMachineBlockEntity iridMint = (MintMachineBlockEntity) level.getBlockEntity(enhIridiumMintPos);
                iridMint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, TyC2Native.stack("ic2:iridium", 1));
                iridMint.setItem(MintMachineBlockEntity.SLOT_INPUT_1, TyC2Native.stack("ic2:alloy", 1));

                // 4. Test Ic2PortableGearMixin: Generator -> Cable -> BatBox charging empty tyc2:comm_terminal!
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:generator", ic2GenBatPos);
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:batbox", ic2BatBoxPos);
                TyC2QaChecks.survivalPlace(level, playerA, "ic2:insulated_copper_cable", ic2CableBatPos);
                Container genBat = (Container) level.getBlockEntity(ic2GenBatPos);
                Container batBox = (Container) level.getBlockEntity(ic2BatBoxPos);
                checks.check("ic2.batbox_present", genBat != null && batBox != null);
                genBat.setItem(0, new ItemStack(Items.COAL, 8));

                ItemStack emptyTerminal = new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get());
                int foundSlot = -1;
                for (int s = 0; s < batBox.getContainerSize(); s++) {
                    if (batBox.canPlaceItem(s, emptyTerminal)) {
                        foundSlot = s;
                        break;
                    }
                }
                checks.check("mixin.ic2_batbox_accepts_comm_terminal", foundSlot >= 0, "slot=" + foundSlot);
                this.batBoxChargeSlot = foundSlot;
                batBox.setItem(batBoxChargeSlot, emptyTerminal);

                // 5. Test LC2 chunk_loading_upgrade in Cooperation Hub + nearby LC2 station detection!
                level.setBlockAndUpdate(enhancedHubPos, TyC2Registries.COOPERATION_HUB_BLOCK.get().defaultBlockState());
                VillageRecord hubVillage = TyC2WorldManager.getOrCreateVillageForHub(level, enhancedHubPos, PLAYER_A_UUID);
                CooperationHubBlockEntity hub = (CooperationHubBlockEntity) level.getBlockEntity(enhancedHubPos);
                hub.setVillageId(hubVillage.getVillageId());
                hub.localContainer().setItem(
                        CooperationHubBlockEntity.LOCAL_SLOT_UPGRADE_0,
                        TyC2Native.stack(TyC2Constants.ITEM_LC2_CHUNK_LOADER, 1)
                );
                level.setBlockAndUpdate(enhancedLc2StationPos, TyC2QaChecks.block("lc2:cargo_station").defaultBlockState());

                // 6. Test ATM accepts BOTH ic2:coin AND tyc2:coin_1 at 1:1!
                level.setBlockAndUpdate(atmPos, TyC2Registries.ATM_MACHINE_BLOCK.get().defaultBlockState());
                AtmMachineBlockEntity atm = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
                playerA.setPos(atmPos.getX() + 1.0, surfaceY, atmPos.getZ());
                atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, TyC2Native.stack("ic2:coin", 5));
                TyC2WorldManager.handleActionPayload(playerA, level,
                        new TyC2ActionPayload("ATM", atmPos, "DEPOSIT_SLOT", "{}"));
                atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, new ItemStack(TyC2Registries.COIN_1.get(), 3));
                TyC2WorldManager.handleActionPayload(playerA, level,
                        new TyC2ActionPayload("ATM", atmPos, "DEPOSIT_SLOT", "{}"));
                long bal = TyC2WorldManager.getData(level).bank().getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance();
                checks.check("ic2.atm_accepts_ic2_coin_and_tyc2_coin1_1to1", bal == 8L, "bal=" + bal);

                stage = 1;
                stageStartTick = ticks;
            }
            case 1 -> {
                // Feed FE into Iridium 1000x Minting Machine so it completes alongside the cable-powered Mint
                TyC2QaPlatform.injectFeIntoBlock(level, enhIridiumMintPos, 128);

                MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(ic2MintPos);
                PrimitiveMintMachineBlockEntity enhPrim =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(enhPrimPos);
                PrimitiveMintMachineBlockEntity enhPrimBlocked =
                        (PrimitiveMintMachineBlockEntity) level.getBlockEntity(enhPrimBlockedPos);
                MintMachineBlockEntity battMint = (MintMachineBlockEntity) level.getBlockEntity(enhBatteryMintPos);
                MintMachineBlockEntity iridMint = (MintMachineBlockEntity) level.getBlockEntity(enhIridiumMintPos);
                Container batBox = (Container) level.getBlockEntity(ic2BatBoxPos);
                CooperationHubBlockEntity hub = (CooperationHubBlockEntity) level.getBlockEntity(enhancedHubPos);

                ItemStack mintedOut = mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT);
                ItemStack primOut = enhPrim.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT);
                ItemStack battOut = battMint.getItem(MintMachineBlockEntity.SLOT_OUTPUT);
                ItemStack iridOut = iridMint.getItem(MintMachineBlockEntity.SLOT_OUTPUT);
                int termEu = CommTerminalItem.getEnergyEu(batBox.getItem(batBoxChargeSlot));
                boolean chunkActive = hub.isChunkLoaderActive();

                if (!mintedOut.isEmpty() && !primOut.isEmpty() && !battOut.isEmpty() && !iridOut.isEmpty()
                        && termEu > 0 && chunkActive) {
                    checks.check("ic2.mint_received_eu_over_cable", true, "storedEu=" + mint.storedEu());
                    checks.check("ic2.mint_outputs_canonical_ic2_coin",
                            "ic2:coin".equals(TyC2Native.itemId(mintedOut)) && mintedOut.getCount() == 2,
                            "out=" + TyC2Native.itemId(mintedOut) + " x" + mintedOut.getCount());
                    checks.check("ic2.primitive_mint_outputs_canonical_ic2_coin",
                            "ic2:coin".equals(TyC2Native.itemId(primOut)) && primOut.getCount() == 2,
                            "out=" + TyC2Native.itemId(primOut) + " x" + primOut.getCount());
                    checks.check("ic2.primitive_mint_blocks_ic2_alloy",
                            enhPrimBlocked.isBlockedByIndustrialTier()
                                    && enhPrimBlocked.progress() == 0
                                    && enhPrimBlocked.getItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT).isEmpty(),
                            "blocked=" + enhPrimBlocked.isBlockedByIndustrialTier()
                                    + ", prog=" + enhPrimBlocked.progress());
                    checks.check("ic2.mint_battery_discharge_and_tin_recipe",
                            "ic2:coin".equals(TyC2Native.itemId(battOut)) && battOut.getCount() == 2,
                            "out=" + TyC2Native.itemId(battOut) + " x" + battOut.getCount()
                                    + ", storedEu=" + battMint.storedEu());
                    checks.check("ic2.mint_iridium_alloy_1000x",
                            "tyc2:coin_1000".equals(TyC2Native.itemId(iridOut)) && iridOut.getCount() == 1,
                            "out=" + TyC2Native.itemId(iridOut) + " x" + iridOut.getCount());
                    checks.check("mixin.ic2_portable_gear_charges_comm_terminal",
                            termEu > 0, "terminalEu=" + termEu);
                    checks.check("lc2.hub_chunk_loading_upgrade_active", chunkActive);
                    List<String> nearbyLc2 = Lc2SoftBridge.findNearbyLogisticsStations(level, enhancedHubPos, 32);
                    checks.check("lc2.nearby_station_detected", !nearbyLc2.isEmpty(), "stations=" + nearbyLc2);

                    checks.check("enhanced.write.world_saved", server.saveEverything(false, true, true));
                    complete(server, null);
                } else if (ticks - stageStartTick > 450) {
                    throw new AssertionError("Enhanced stage 1 timed out: mintEu=" + mint.storedEu()
                            + ", mintedOut=" + mintedOut
                            + ", primOut=" + primOut
                            + ", battOut=" + battOut + " (battEu=" + battMint.storedEu() + ")"
                            + ", iridOut=" + iridOut + " (iridEu=" + iridMint.storedEu() + ")"
                            + ", termEu=" + termEu
                            + ", hubChunkActive=" + chunkActive);
                }
            }
            default -> complete(server, null);
        }
    }

    // =========================================================================
    // ENHANCED COLD-RESTART READ SUITE
    // =========================================================================

    private void runEnhancedRead(MinecraftServer server, ServerLevel level) {
        if (ticks < 10) {
            return;
        }
        CooperationHubBlockEntity hub = (CooperationHubBlockEntity) level.getBlockEntity(enhancedHubPos);
        MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(ic2MintPos);
        checks.check("enhanced.read.hub_chunk_loader_rearmed",
                hub != null && hub.hasChunkLoadingUpgrade() && hub.isChunkLoaderActive());
        checks.check("enhanced.read.mint_output_preserved",
                mint != null && "ic2:coin".equals(TyC2Native.itemId(mint.getItem(MintMachineBlockEntity.SLOT_OUTPUT))));
        checks.check("enhanced.read.bank_preserved",
                TyC2WorldManager.getData(level).bank().getOrCreatePersonal(PLAYER_A_UUID, "TycoonAlice").balance() == 8L);
        checks.check("enhanced.read.world_saved", server.saveEverything(false, true, true));
        complete(server, null);
    }

    private void complete(MinecraftServer server, Throwable failure) {
        if (finished) {
            return;
        }
        finished = true;
        if (failure != null) {
            LOGGER.error("[TYC2-QA] FAILED ({}/{} at tick {}): {}", profile, phase, ticks, failure.toString(), failure);
        } else {
            LOGGER.info("[TYC2-QA] PASSED ({}/{}) {} checks in {} ticks", profile, phase, checks.passedCount(), ticks);
        }
        try {
            checks.writeReports(profile, phase, ticks, failure);
        } catch (IOException e) {
            LOGGER.error("[TYC2-QA] Failed writing QA report", e);
        } finally {
            server.halt(false);
        }
    }
}
