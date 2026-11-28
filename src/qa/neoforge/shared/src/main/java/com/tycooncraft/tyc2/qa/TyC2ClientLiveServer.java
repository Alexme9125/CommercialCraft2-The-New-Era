package com.tycooncraft.tyc2.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import com.tycooncraft.tyc2.world.TycoonSavedData;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live dedicated-server showcase &amp; command host ({@code tyc2.qa.mode=client_live}) for
 * real graphical Client ↔ Dedicated Server joint testing on NeoForge 1.21.1, 26.1.2, and 26.2.
 */
public final class TyC2ClientLiveServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("mnera_live_server");

    private int surfaceY;
    private BlockPos primMintPos;
    private BlockPos mintPos;
    private BlockPos atmPos;
    private BlockPos hubPos;
    private BlockPos bellPos;
    private BlockPos mayorPos;
    private BlockPos ic2GenPos;
    private BlockPos ic2CablePos;
    private BlockPos lc2StationPos;

    private UUID partneredVillageId;
    private UUID unpartneredVillageId;
    private int tickCounter = 0;

    public void install() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::registerCommands);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, this::onPlayerLogin);
    }

    public void onServerStarted(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        TyC2QaChecks.setChunkRangeForced(level, -1, 8, -1, 3, true);
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 16, 16) - 1;
        this.surfaceY = groundY + 1;

        this.primMintPos = new BlockPos(10, surfaceY, 16);
        this.mintPos = new BlockPos(14, surfaceY, 16);
        this.atmPos = new BlockPos(17, surfaceY, 16);
        this.hubPos = new BlockPos(20, surfaceY, 16);
        this.ic2GenPos = new BlockPos(12, surfaceY, 16);
        this.ic2CablePos = new BlockPos(13, surfaceY, 16);
        this.lc2StationPos = new BlockPos(24, surfaceY, 16);

        // Unpartnered village Bell + Mayor >96 blocks away to honor 96-block village separation
        this.bellPos = new BlockPos(120, surfaceY, 16);
        this.mayorPos = new BlockPos(120, surfaceY, 19);

        // Build clean stone brick plaza floor for crisp screenshots
        for (int x = 8; x <= 26; x++) {
            for (int z = 12; z <= 22; z++) {
                level.setBlockAndUpdate(new BlockPos(x, groundY, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = 116; x <= 124; x++) {
            for (int z = 12; z <= 22; z++) {
                level.setBlockAndUpdate(new BlockPos(x, groundY, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }

        // 0. Primitive Minting Machine (facing SOUTH, lit firebox burning coal for 1x..100x Vanilla!)
        level.setBlockAndUpdate(primMintPos, TyC2Registries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState()
                .setValue(PrimitiveMintMachineBlock.FACING, Direction.SOUTH));
        PrimitiveMintMachineBlockEntity primMint = (PrimitiveMintMachineBlockEntity) level.getBlockEntity(primMintPos);
        if (primMint != null) {
            primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 32));
            primMint.setItem(PrimitiveMintMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 16));
        }

        // 1. Pure-Electric Industrial Minting Machine (facing SOUTH, NO solid fuel — FE in standalone, IC2 EU+battery in enhanced!)
        level.setBlockAndUpdate(mintPos, TyC2Registries.MINT_MACHINE_BLOCK.get().defaultBlockState()
                .setValue(MintMachineBlock.FACING, Direction.SOUTH));
        MintMachineBlockEntity mint = (MintMachineBlockEntity) level.getBlockEntity(mintPos);
        if (mint != null) {
            mint.setItem(MintMachineBlockEntity.SLOT_INPUT_0, new ItemStack(Items.COPPER_INGOT, 32));
            mint.setItem(MintMachineBlockEntity.SLOT_TERMINAL, new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get()));
            if (Ic2SoftBridge.isIc2Loaded()) {
                mint.setItem(MintMachineBlockEntity.SLOT_BATTERY, TyC2Native.stack("mhera:single_use_battery", 1));
            }
        }

        // In Enhanced mode: attach real IC2 Generator + Insulated Copper Cable to Industrial Minting Machine!
        if (Ic2SoftBridge.isIc2Loaded()) {
            var fake = TyC2QaPlatform.createFakePlayer(level, UUID.randomUUID(), "SceneBuilder");
            TyC2QaChecks.survivalPlace(level, fake, "mhera:generator", ic2GenPos);
            TyC2QaChecks.survivalPlace(level, fake, "mhera:insulated_copper_cable", ic2CablePos);
            if (level.getBlockEntity(ic2GenPos) instanceof Container gen) {
                gen.setItem(0, new ItemStack(Items.COAL, 16));
            }
        }

        // 2. Industrial ATM (facing SOUTH)
        level.setBlockAndUpdate(atmPos, TyC2Registries.ATM_MACHINE_BLOCK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        AtmMachineBlockEntity atm = (AtmMachineBlockEntity) level.getBlockEntity(atmPos);
        if (atm != null) {
            atm.setItem(AtmMachineBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 8));
        }

        // 3. Partnered Lv 2 Village Cooperation Hub ("MetroHaven") with orders, warehouse items & CPI history!
        level.setBlockAndUpdate(hubPos, TyC2Registries.COOPERATION_HUB_BLOCK.get().defaultBlockState());
        TycoonSavedData data = TyC2WorldManager.getData(level);
        VillageRecord metro = new VillageRecord(
                UUID.randomUUID(),
                TyC2Native.dimensionId(level),
                "MetroHaven",
                new BlockPos3i(hubPos.getX(), hubPos.getY(), hubPos.getZ()),
                BiomeStyle.PLAINS
        );
        this.partneredVillageId = metro.getVillageId();
        metro.establishPartnership(UUID.fromString("99999999-1111-2222-3333-444444444444"), null, 1800L);
        metro.setLevel(2);
        metro.setPopulation(8);
        metro.addTradeVolume(2400L);
        metro.getStorage().setSlotRaw(0, new SlotStack("minecraft:iron_ingot", 32, ""));
        metro.getStorage().setSlotRaw(1, new SlotStack("minecraft:coal", 64, ""));
        metro.getStorage().setSlotRaw(500, new SlotStack("minecraft:wheat", 48, ""));
        metro.getStorage().setSlotRaw(501, new SlotStack("minecraft:leather", 16, ""));
        // Pre-deliver 4 of 5 SMALL_COTTAGE materials (leaving minecraft:white_wool 0/3) so screenshots visually
        // verify ALL 5 materials (green met + amber 5th item) in BOTH Hub Expansion & Terminal Growth tabs!
        ExpansionDesire liveExp = ExpansionDesire.createDefault("live_exp", ProjectType.SMALL_COTTAGE, BiomeStyle.PLAINS, false);
        for (Map.Entry<String, Integer> entry : liveExp.requiredMaterials().entrySet()) {
            if (!"minecraft:white_wool".equals(entry.getKey())) {
                liveExp = liveExp.withDelivered(entry.getKey(), entry.getValue());
            }
        }
        metro.setActiveExpansion(liveExp);
        data.putVillage(metro);
        TyC2QaPlatform.spawnAdultVillager(level, hubPos.getX() - 1.5, surfaceY, hubPos.getZ() - 2.0);
        TyC2QaPlatform.spawnAdultVillager(level, hubPos.getX() + 1.5, surfaceY, hubPos.getZ() - 2.0);
        TyC2QaPlatform.spawnAdultVillager(level, hubPos.getX() + 0.5, surfaceY, hubPos.getZ() - 3.0);
        TyC2WorldManager.refreshVillageOrders(level, data, metro, TyC2Native.dayTime(level));

        if (level.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity hubBe) {
            hubBe.setVillageId(partneredVillageId);
            if (Lc2SoftBridge.isLc2Loaded()) {
                hubBe.localContainer().setItem(
                        CooperationHubBlockEntity.LOCAL_SLOT_UPGRADE_0,
                        TyC2Native.stack(TyC2Constants.ITEM_LC2_CHUNK_LOADER, 1)
                );
                level.setBlockAndUpdate(lc2StationPos, TyC2QaChecks.block("trera:cargo_station").defaultBlockState());
            }
        }

        // Populate rich 16-point CPI trend history so the Hub & Terminal CPI chart renders clearly!
        MacroEconomyEngine econ = data.economy();
        for (int i = 0; i < 16; i++) {
            if (i % 3 == 0) {
                econ.recordMinting(1200L);
            } else {
                econ.recordTrade("minecraft:iron_ingot", 24, 8L, true);
                econ.recordMoneySink(450L);
            }
            econ.recordSnapshot(level.getGameTime() + i * 200L);
        }

        // 4. Unpartnered Village ("Pinebrook") with Bell + 3 Villagers + elected Golden Mayor!
        level.setBlockAndUpdate(bellPos, Blocks.BELL.defaultBlockState());
        VillageRecord pinebrook = new VillageRecord(
                UUID.randomUUID(),
                TyC2Native.dimensionId(level),
                "Pinebrook",
                new BlockPos3i(bellPos.getX(), bellPos.getY(), bellPos.getZ()),
                BiomeStyle.PLAINS
        );
        this.unpartneredVillageId = pinebrook.getVillageId();
        data.putVillage(pinebrook);
        TyC2QaPlatform.spawnAdultVillager(level, mayorPos.getX() + 0.5, surfaceY, mayorPos.getZ() + 0.5);
        TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() - 2.0, surfaceY, bellPos.getZ() + 2.5);
        TyC2QaPlatform.spawnAdultVillager(level, bellPos.getX() + 2.0, surfaceY, bellPos.getZ() + 2.5);
        Method evalMayor = TyC2WorldManager.class.getDeclaredMethod(
                "evaluateVillageMayorAndPop",
                ServerLevel.class, TycoonSavedData.class, VillageRecord.class, BlockPos.class, long.class
        );
        evalMayor.setAccessible(true);
        evalMayor.invoke(null, level, data, pinebrook, bellPos, level.getGameTime());

        data.setDirty();
        lockDaytimeAndWeather(server);
        writeSiteJson(level);
        LOGGER.info("[TYC2-LIVE] Client-Server showcase ready (mint={}, atm={}, hub={}, mayor={})",
                mintPos, atmPos, hubPos, mayorPos);
    }

    public void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 200 == 1) {
            lockDaytimeAndWeather(server);
        }
        // In Standalone mode, supply live FE into the pure-electric Industrial Minting Machine so it runs actively
        if (!Ic2SoftBridge.isIc2Loaded() && mintPos != null) {
            TyC2QaPlatform.injectFeIntoBlock(server.overworld(), mintPos, 128);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level().getServer().overworld();
        TycoonSavedData data = TyC2WorldManager.getData(level);
        BankAccountManager bank = data.bank();
        bank.getOrCreatePersonal(player.getUUID(), player.getName().getString());
        if (bank.getOrCreatePersonal(player.getUUID(), player.getName().getString()).balance() < 1250L) {
            bank.deposit(player.getUUID(), 1250L, level.getGameTime(), "Initial Grant");
        }
        if (bank.getCompaniesForPlayer(player.getUUID()).isEmpty()) {
            BankAccountManager.CompanyAccount co = bank.createCompany(
                    player.getUUID(), player.getName().getString(), "SkylineCorp"
            );
            bank.transferToCompany(player.getUUID(), co.companyId(), 400L, level.getGameTime(), "Seed Capital");
        }
        // Make player owner of MetroHaven so owner controls are active in Hub GUI!
        data.getVillage(partneredVillageId).ifPresent(v ->
                v.establishPartnership(player.getUUID(), null, 0L));
        data.setDirty();

        // Equip player with charged Comm Terminal bound to MetroHaven + negotiation materials!
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        ItemStack terminal = new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get());
        CommTerminalItem.setEnergyEu(terminal, 18_500);
        CommTerminalItem.toggleBindVillage(terminal, partneredVillageId, "MetroHaven");
        CommTerminalItem.setSelectedVillage(terminal, partneredVillageId);
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        player.getInventory().add(new ItemStack(TyC2Registries.COIN_100.get(), 2));
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        player.getInventory().add(new ItemStack(Items.BREAD, 32));

        // Place player facing the showcase plaza (NORTH toward z=16)
        TyC2QaPlatform.teleportPlayer(player, level, 17.5, surfaceY, 21.5, 180.0F, 12.0F);
    }

    private void lockDaytimeAndWeather(MinecraftServer server) {
        var src = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, "time set 5500");
        server.getCommands().performPrefixedCommand(src, "weather clear 999999");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tyc2live")
                        .then(Commands.argument("action", StringArgumentType.word())
                                .executes(ctx -> {
                                    String action = StringArgumentType.getString(ctx, "action");
                                    MinecraftServer server = ctx.getSource().getServer();
                                    ServerLevel level = server.overworld();
                                    List<ServerPlayer> players = server.getPlayerList().getPlayers();
                                    ServerPlayer player = players.isEmpty() ? null : players.get(0);
                                    try {
                                        handleLiveCommand(level, player, action);
                                        return 1;
                                    } catch (Exception e) {
                                        LOGGER.error("[TYC2-LIVE] /mnelive {} failed", action, e);
                                        return 0;
                                    }
                                }))
        );
    }

    private void handleLiveCommand(ServerLevel level, ServerPlayer player, String action) throws IOException {
        if (player != null) {
            switch (action) {
                case "tp_plaza" -> TyC2QaPlatform.teleportPlayer(player, level, 16.0, surfaceY, 22.5, 180.0F, 11.0F);
                case "tp_prim_mint" -> TyC2QaPlatform.teleportPlayer(player, level, primMintPos.getX() + 0.5, surfaceY, primMintPos.getZ() + 2.5, 180.0F, 10.0F);
                case "prim_set_blocked" -> {
                    if (level.getBlockEntity(primMintPos) instanceof PrimitiveMintMachineBlockEntity pm) {
                        pm.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_0,
                                Ic2SoftBridge.isIc2Loaded() ? TyC2Native.stack("mhera:alloy", 1) : new ItemStack(Items.DIAMOND, 1));
                        pm.setItem(PrimitiveMintMachineBlockEntity.SLOT_INPUT_1, new ItemStack(Items.GOLD_INGOT, 2));
                        pm.setItem(PrimitiveMintMachineBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    }
                }
                case "tp_mint" -> TyC2QaPlatform.teleportPlayer(player, level, mintPos.getX() + 0.5, surfaceY, mintPos.getZ() + 2.5, 180.0F, 10.0F);
                case "tp_atm" -> TyC2QaPlatform.teleportPlayer(player, level, atmPos.getX() + 0.5, surfaceY, atmPos.getZ() + 2.5, 180.0F, 10.0F);
                case "tp_hub" -> TyC2QaPlatform.teleportPlayer(player, level, hubPos.getX() + 0.5, surfaceY, hubPos.getZ() + 2.5, 180.0F, 10.0F);
                case "tp_mayor" -> TyC2QaPlatform.teleportPlayer(player, level, mayorPos.getX() + 0.5, surfaceY, mayorPos.getZ() + 2.8, 180.0F, 5.0F);
                case "dump_state" -> {
                }
                default -> throw new IllegalArgumentException("Unknown /mnelive action: " + action);
            }
        }
        writeStateJson(level, player);
    }

    private void writeSiteJson(ServerLevel level) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("surfaceY", surfaceY);
        obj.addProperty("ic2Loaded", Ic2SoftBridge.isIc2Loaded());
        obj.addProperty("lc2Loaded", Lc2SoftBridge.isLc2Loaded());
        obj.add("primMint", posArray(primMintPos));
        obj.add("mint", posArray(mintPos));
        obj.add("atm", posArray(atmPos));
        obj.add("hub", posArray(hubPos));
        obj.add("bell", posArray(bellPos));
        obj.add("mayor", posArray(mayorPos));
        writeAtomic(Path.of("live-site.json"), obj);
    }

    private void writeStateJson(ServerLevel level, ServerPlayer player) throws IOException {
        TycoonSavedData data = TyC2WorldManager.getData(level);
        JsonObject obj = new JsonObject();
        obj.addProperty("villages", data.allVillages().size());
        obj.addProperty("cpi", data.economy().computeCpi());
        obj.addProperty("cpiHistoryPoints", data.economy().history().size());
        obj.addProperty("ic2Loaded", Ic2SoftBridge.isIc2Loaded());
        obj.addProperty("lc2Loaded", Lc2SoftBridge.isLc2Loaded());
        if (player != null) {
            obj.addProperty("playerName", player.getName().getString());
            obj.addProperty("playerBalance", data.bank().getActiveBalance(player.getUUID()));
        }
        if (level.getBlockEntity(mintPos) instanceof MintMachineBlockEntity mint) {
            obj.addProperty("mintStoredEu", mint.storedEu());
        }
        if (level.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity hub) {
            obj.addProperty("hubChunkLoaderActive", hub.isChunkLoaderActive());
        }
        boolean mayorHeld = false;
        if (unpartneredVillageId != null && player != null) {
            var optV = data.getVillage(unpartneredVillageId);
            if (optV.isPresent() && optV.get().getMayorState().getMayorEntityUuid() != null) {
                var mayorEnt = level.getEntity(optV.get().getMayorState().getMayorEntityUuid());
                mayorHeld = mayorEnt != null && TyC2QaPlatform.villagerTradingPlayer(mayorEnt) == player;
            }
        }
        obj.addProperty("mayorNegotiatingWithClient", mayorHeld);
        writeAtomic(Path.of("server-state.json"), obj);
    }

    private static JsonArray posArray(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    private static void writeAtomic(Path path, JsonObject obj) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(obj) + "\n");
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
