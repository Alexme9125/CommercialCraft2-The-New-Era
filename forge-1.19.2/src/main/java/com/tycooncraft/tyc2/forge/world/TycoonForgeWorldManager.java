package com.tycooncraft.tyc2.forge.world;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.item.TyC2BlockItem;
import com.tycooncraft.tyc2.forge.util.TyC2ChunkTickets;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.village.Aabb3i;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.ConstructionJob;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.ExpansionPlanner;
import com.tycooncraft.tyc2.village.MayorElectionRules;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SpawnMarker;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Authoritative Forge 1.19.2 server lifecycle coordinator for TycoonCraft²:
 * village Bell/Hub detection, Mayor election/death/re-election, emerald-trade blocking,
 * virtual demand cycles, municipal upkeep, auto-fulfillment, collision-safe gradual village
 * expansion, industrial tier tracking, and all trade/upgrade/relocation actions.
 */
public final class TycoonForgeWorldManager {

    public static final String TAG_MAYOR = "tyc2_mayor";
    public static final String TAG_VILLAGE_ID = "tyc2_village_id";
    public static final int VILLAGE_SCAN_RADIUS = 48;
    public static final int MIN_VILLAGE_SEPARATION = 96;

    private static final Set<String> BASIC_NEGOTIATION_FOODS = Set.of(
            "minecraft:bread", "minecraft:baked_potato", "minecraft:cooked_beef",
            "minecraft:cooked_porkchop", "minecraft:cooked_mutton", "minecraft:cooked_chicken",
            "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:cooked_rabbit",
            "minecraft:carrot", "minecraft:apple", "minecraft:potato", "minecraft:beetroot",
            "minecraft:sweet_berries", "minecraft:pumpkin_pie", "minecraft:golden_carrot"
    );

    private static final ExpansionPlanner PLANNER = new ExpansionPlanner();
    private static final Random RNG = new Random();

    private TycoonForgeWorldManager() {
    }

    /** Result DTO returned by trade, upgrade, negotiation, and hub management actions. */
    public record ActionFeedback(boolean success, String translationKey, Object... args) {
        public Component toComponent() {
            return Component.translatable(translationKey, args);
        }
    }

    /** Live player possession snapshot for the Mayor Negotiation GUI. */
    public record NegotiationStatus(
            long cashIc,
            long bankIc,
            long requiredIc,
            int haveLogs,
            int requiredLogs,
            int haveIron,
            int requiredIron,
            int haveFood,
            int requiredFood
    ) {
        public boolean currencyMet() {
            return cashIc + bankIc >= requiredIc;
        }

        public boolean logsMet() {
            return haveLogs >= requiredLogs;
        }

        public boolean ironMet() {
            return haveIron >= requiredIron;
        }

        public boolean foodMet() {
            return haveFood >= requiredFood;
        }

        public boolean allMet() {
            return currencyMet() && logsMet() && ironMet() && foodMet();
        }
    }

    // --- Forge Event Bus Handlers ---

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long gameTime = overworld.getGameTime();
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        TyC2Config cfg = TyC2RuntimeData.config();

        // 1. Scan for village Bells / Hubs and update Mayor elections every 100 ticks
        if (gameTime % 100L == 0L) {
            scanAndUpdateVillages(server, data, gameTime);
        }

        // 2. Virtual village economy, demand refresh, upkeep, and auto-fulfillment
        if (gameTime % 20L == 0L) {
            tickVirtualVillages(server, overworld, data, cfg, gameTime);
        }

        // 3. Gradual collision-safe construction jobs (ticks every tick; ConstructionJob throttles blocks/step)
        tickConstructionJobs(server, data);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
            String dim = level.dimension().location().toString();
            data.placedBlocks().markPlaced(dim, event.getPos());
            String blockId = TyC2Ids.blockId(event.getPlacedBlock().getBlock());
            if (data.tiers().recordItemMilestone(player.getUUID(), blockId, Ic2Bridge.isLoaded())) {
                data.setDirty();
            } else {
                data.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (state.is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())) {
            Player player = event.getPlayer();
            if (player == null || !(player.isCreative() && player.hasPermissions(2))) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("status.tyc2.hub.protected").withStyle(ChatFormatting.RED),
                            true
                    );
                }
                return;
            }
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
        data.placedBlocks().markRemoved(level.dimension().location().toString(), event.getPos());
        data.setDirty();
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos ->
                level.getBlockState(pos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())
        );
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        recordMilestone(event.getEntity(), event.getCrafting());
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        recordMilestone(event.getEntity(), event.getSmelting());
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        recordMilestone(event.getEntity(), event.getItem().getItem());
    }

    private static void recordMilestone(Player player, ItemStack stack) {
        if (player == null || stack.isEmpty() || !(player.level instanceof ServerLevel level)) {
            return;
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
        String id = TyC2Ids.itemId(stack);
        if (data.tiers().recordItemMilestone(player.getUUID(), id, Ic2Bridge.isLoaded())) {
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
        CompoundTag pdata = villager.getPersistentData();
        String dim = level.dimension().location().toString();

        // Case 1: Interacting with a Village Mayor
        if (pdata.getBoolean(TAG_MAYOR) && pdata.contains(TAG_VILLAGE_ID)) {
            UUID villageId = parseUuidSafe(pdata.getString(TAG_VILLAGE_ID));
            Optional<VillageRecord> recOpt = data.getVillage(villageId);
            if (recOpt.isPresent()) {
                VillageRecord village = recOpt.get();
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (!village.isPartnered()) {
                    openMayorNegotiationScreen(player, village);
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "mayor.tyc2.partnered_status",
                            village.getVillageName(),
                            village.getLevel(),
                            village.getVillageBalance(),
                            village.getActiveOrders().size()
                    ).withStyle(ChatFormatting.GOLD));
                }
                return;
            }
        }

        // Case 2: Any villager belonging to a partnered village has vanilla emerald trading disabled!
        Optional<VillageRecord> nearest = data.findNearestVillage(dim, villager.blockPosition(), VILLAGE_SCAN_RADIUS);
        if (nearest.isPresent() && nearest.get().isPartnered()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            player.displayClientMessage(
                    Component.translatable("mayor.tyc2.villager_redirect").withStyle(ChatFormatting.GOLD),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level instanceof ServerLevel level)) {
            return;
        }
        CompoundTag pdata = villager.getPersistentData();
        if (!pdata.getBoolean(TAG_MAYOR) || !pdata.contains(TAG_VILLAGE_ID)) {
            return;
        }
        UUID villageId = parseUuidSafe(pdata.getString(TAG_VILLAGE_ID));
        TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
        Optional<VillageRecord> recOpt = data.getVillage(villageId);
        if (recOpt.isEmpty()) {
            return;
        }
        VillageRecord village = recOpt.get();
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        List<Villager> others = findLivingVillagers(level, hubPos, villager.getUUID());
        List<MayorElectionRules.MayorCandidate> candidates = toCandidates(others);
        long tick = level.getServer().overworld().getGameTime();

        boolean wasPaused = village.getMayorState().isPausedDueToDepopulation();
        MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.onMayorDeathAndReelect(
                village.getMayorState(),
                villager.getUUID(),
                others.size(),
                candidates,
                tick
        );
        village.setPopulation(others.size());
        data.setDirty();

        broadcastToVillageStakeholders(
                level, hubPos, village,
                Component.translatable("mayor.tyc2.died", village.getVillageName()).withStyle(ChatFormatting.RED)
        );
        if (outcome.action() == MayorElectionRules.Action.ELECTED_NEW_MAYOR
                || outcome.action() == MayorElectionRules.Action.RESUMED_AND_ELECTED) {
            applyMayorEntityTag(level, others, outcome.mayorUuid(), village);
            broadcastToVillageStakeholders(
                    level, hubPos, village,
                    Component.translatable("mayor.tyc2.elected", village.getVillageName()).withStyle(ChatFormatting.GOLD)
            );
        } else if (outcome.action() == MayorElectionRules.Action.PAUSED_DEPOPULATED && !wasPaused) {
            broadcastToVillageStakeholders(
                    level, hubPos, village,
                    Component.translatable("mayor.tyc2.paused_depopulated", village.getVillageName())
                            .withStyle(ChatFormatting.YELLOW)
            );
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TyC2ChunkTickets.clearLevel(level);
        }
    }

    // --- Village Scanning, Mayor Election, Virtual Ticks & Gradual Construction ---

    private static void scanAndUpdateVillages(MinecraftServer server, TycoonForgeSavedData data, long gameTime) {
        // A. Discover Bells near players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.getLevel();
            String dim = level.dimension().location().toString();
            BlockPos center = player.blockPosition();
            AABB box = new AABB(center).inflate(VILLAGE_SCAN_RADIUS);
            List<Villager> nearbyVillagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive);
            if (nearbyVillagers.size() < MayorElectionRules.MIN_VILLAGERS_FOR_MAYOR) {
                continue;
            }
            // Find a Bell in the area if no tracked village is already within 96 blocks
            if (data.findNearestVillage(dim, center, MIN_VILLAGE_SEPARATION).isEmpty()) {
                BlockPos bellPos = findNearbyBell(level, center, 32);
                if (bellPos != null && data.findNearestVillage(dim, bellPos, MIN_VILLAGE_SEPARATION).isEmpty()) {
                    List<Villager> bellVillagers = findLivingVillagers(level, bellPos, null);
                    if (bellVillagers.size() >= MayorElectionRules.MIN_VILLAGERS_FOR_MAYOR) {
                        BiomeStyle style = detectBiomeStyle(level, bellPos);
                        VillageRecord created = new VillageRecord(
                                UUID.randomUUID(),
                                dim,
                                data.allocateDefaultVillageName(style),
                                TyC2Ids.toCore(bellPos),
                                style
                        );
                        created.setPopulation(bellVillagers.size());
                        data.putVillage(created);
                    }
                }
            }
        }

        // B. Evaluate Mayor lifecycle & population on all tracked villages whose Hub chunk is loaded
        for (VillageRecord village : new ArrayList<>(data.allVillages())) {
            ServerLevel level = resolveLevel(server, village.getDimensionId());
            if (level == null) {
                continue;
            }
            BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
            if (!level.hasChunkAt(hubPos)) {
                continue;
            }
            List<Villager> villagers = findLivingVillagers(level, hubPos, null);
            List<MayorElectionRules.MayorCandidate> candidates = toCandidates(villagers);
            village.setPopulation(villagers.size());

            boolean wasPaused = village.getMayorState().isPausedDueToDepopulation();
            MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.evaluate(
                    village.getMayorState(),
                    villagers.size(),
                    candidates,
                    gameTime
            );
            switch (outcome.action()) {
                case ELECTED_NEW_MAYOR -> {
                    applyMayorEntityTag(level, villagers, outcome.mayorUuid(), village);
                    data.setDirty();
                    if (village.isPartnered()) {
                        broadcastToVillageStakeholders(
                                level, hubPos, village,
                                Component.translatable("mayor.tyc2.elected", village.getVillageName())
                                        .withStyle(ChatFormatting.GOLD)
                        );
                    }
                }
                case RESUMED_AND_ELECTED -> {
                    applyMayorEntityTag(level, villagers, outcome.mayorUuid(), village);
                    data.setDirty();
                    broadcastToVillageStakeholders(
                            level, hubPos, village,
                            Component.translatable("mayor.tyc2.resumed", village.getVillageName())
                                    .withStyle(ChatFormatting.GREEN)
                    );
                }
                case PAUSED_DEPOPULATED -> {
                    if (!wasPaused) {
                        data.setDirty();
                        if (village.isPartnered()) {
                            broadcastToVillageStakeholders(
                                    level, hubPos, village,
                                    Component.translatable("mayor.tyc2.paused_depopulated", village.getVillageName())
                                            .withStyle(ChatFormatting.YELLOW)
                            );
                        }
                    }
                }
                case NO_CHANGE -> {
                    // Ensure existing Mayor keeps custom golden title & persistence
                    if (outcome.mayorUuid() != null) {
                        applyMayorEntityTag(level, villagers, outcome.mayorUuid(), village);
                    }
                }
                default -> {
                    // INSUFFICIENT_POPULATION or WAITING_FOR_ADULT_CANDIDATE
                }
            }
        }
    }

    private static void tickVirtualVillages(
            MinecraftServer server,
            ServerLevel overworld,
            TycoonForgeSavedData data,
            TyC2Config cfg,
            long gameTime
    ) {
        data.syncMacroAccountTotals();
        boolean anyDemandCycleAdvanced = false;
        long currentDay = overworld.getDayTime() / 24_000L;
        boolean runDailyUpkeep = currentDay > data.lastUpkeepDay() && data.lastUpkeepDay() >= 0L;
        if (data.lastUpkeepDay() < 0L) {
            data.setLastUpkeepDay(currentDay);
        }

        for (VillageRecord village : data.allVillages()) {
            if (!village.isPartnered()) {
                continue;
            }
            ServerLevel dimLevel = resolveLevel(server, village.getDimensionId());
            if (dimLevel == null) {
                dimLevel = overworld;
            }

            // 1. Municipal Upkeep sink once per Minecraft day
            if (runDailyUpkeep) {
                long upkeep = village.applyMunicipalUpkeep();
                if (upkeep > 0L) {
                    data.economy().recordVillageMoneySink(upkeep);
                    data.setDirty();
                }
            }

            // 2. Auto-Fulfill Buy Orders & Expansion Desire from Inbound 0..499
            if (gameTime % Math.max(20, cfg.autoFulfillIntervalTicks()) == 0L) {
                runAutoFulfill(data, village, gameTime);
            }

            // 3. Demand Refresh (virtual — runs even if village chunk is unloaded!)
            village.pruneFinishedOrExpiredOrders(gameTime);
            boolean dueForRefresh = village.getActiveOrders().isEmpty()
                    || (gameTime - village.getLastDemandRefreshTick()) >= cfg.demandRefreshTicks();
            if (dueForRefresh && village.canGenerateOrders()) {
                if (!anyDemandCycleAdvanced) {
                    data.economy().advanceDemandCycle(gameTime);
                    anyDemandCycleAdvanced = true;
                }
                refreshVillageOrders(dimLevel, data, village, gameTime);
            }

            // 4. Periodic Expansion Desire generation
            if (!village.isExpansionPaused()
                    && village.getActiveExpansion() == null
                    && village.getActiveConstructionJob().isEmpty()
                    && (gameTime - village.getLastExpansionTick()) >= cfg.expansionIntervalTicks()) {
                boolean hasWater = hasNearbyWaterIfLoaded(dimLevel, TyC2Ids.toMc(village.getHubPos()));
                ProjectType nextType = PLANNER.selectNextProjectType(village, hasWater);
                ExpansionDesire desire = ExpansionDesire.createDefault(
                        "exp_" + gameTime + "_" + village.getOccupiedZones().size(),
                        nextType,
                        village.getBiomeStyle(),
                        false
                );
                village.setActiveExpansion(desire);
                village.setLastExpansionTick(gameTime);
                data.setDirty();
            }
        }

        if (runDailyUpkeep) {
            data.setLastUpkeepDay(currentDay);
        }
        data.syncMacroAccountTotals();
    }

    private static void runAutoFulfill(TycoonForgeSavedData data, VillageRecord village, long gameTime) {
        BankAccountManager.AccountRef payoutAccount = resolveVillageSettlementAccount(data, village);
        boolean changed = false;

        // Auto-fulfill enabled Village Buy Orders from Inbound 0..499
        for (VillageTradeOrder order : new ArrayList<>(village.getActiveOrders())) {
            if (order.side() != OrderSide.VILLAGE_BUYS || !order.autoFulfill() || order.isCompleted()) {
                continue;
            }
            int available = village.getStorage().countInInbound(order.itemId());
            if (available <= 0) {
                continue;
            }
            VillageRecord.TradeExecutionResult res = village.executeBuyOrderFromInbound(order.orderId(), available);
            if (res.success() && res.executedQty() > 0) {
                if (payoutAccount != null) {
                    data.bank().creditAccount(
                            payoutAccount,
                            res.totalCurrency(),
                            BankAccountManager.TransactionType.TRADE_SELL,
                            gameTime,
                            village.getVillageName(),
                            "Auto-Fulfill " + res.executedQty() + "x " + order.itemId()
                    );
                }
                long basePrice = TyC2RuntimeData.goodsCatalog().findByItemId(order.itemId())
                        .map(g -> g.basePrice()).orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
                changed = true;
            }
        }

        // Auto-fulfill enabled Expansion Desire from Inbound 0..499
        ExpansionDesire exp = village.getActiveExpansion();
        if (exp != null && exp.autoFulfill() && !exp.completed()) {
            ExpansionDesire updated = exp.pullAvailableFromInbound(village.getStorage());
            if (updated != exp) {
                village.setActiveExpansion(updated);
                changed = true;
            }
            if (updated.allMaterialsDelivered() && village.getVillageBalance() >= updated.constructionBudgetCost()) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                ServerLevel dimLevel = server == null ? null : resolveLevel(server, village.getDimensionId());
                if (dimLevel != null && dimLevel.hasChunkAt(TyC2Ids.toMc(village.getHubPos()))) {
                    tryLaunchExpansionConstruction(dimLevel, data, village, gameTime);
                    changed = true;
                }
            }
        }

        if (changed) {
            data.syncMacroAccountTotals();
            data.setDirty();
        }
    }

    private static void tickConstructionJobs(MinecraftServer server, TycoonForgeSavedData data) {
        for (VillageRecord village : data.allVillages()) {
            Optional<ConstructionJob> jobOpt = village.getActiveConstructionJob();
            if (jobOpt.isEmpty()) {
                continue;
            }
            ServerLevel level = resolveLevel(server, village.getDimensionId());
            if (level == null || !level.hasChunkAt(TyC2Ids.toMc(village.getHubPos()))) {
                continue;
            }
            ConstructionJob job = jobOpt.get();
            ConstructionJob.StepResult step = job.tick();
            if (step.placedBlocks().isEmpty() && !step.completedNow()) {
                continue;
            }
            for (WorldBlockPlacement wp : step.placedBlocks()) {
                BlockPos targetPos = TyC2Ids.toMc(wp.pos());
                if (!level.hasChunkAt(targetPos)) {
                    continue;
                }
                // Extra runtime safety guard: never clobber a BlockEntity or player-placed block
                if (level.getBlockEntity(targetPos) != null
                        || data.placedBlocks().isPlayerPlaced(village.getDimensionId(), targetPos)) {
                    continue;
                }
                BlockState state = TyC2Ids.parseBlockState(wp.blockStateSpec());
                level.setBlock(targetPos, state, Block.UPDATE_ALL);
                SoundType sound = state.getSoundType(level, targetPos, null);
                level.playSound(
                        null,
                        targetPos,
                        sound.getPlaceSound(),
                        SoundSource.BLOCKS,
                        0.55F,
                        0.9F + RNG.nextFloat() * 0.2F
                );
                level.sendParticles(
                        ParticleTypes.CLOUD,
                        targetPos.getX() + 0.5,
                        targetPos.getY() + 0.6,
                        targetPos.getZ() + 0.5,
                        3,
                        0.2, 0.2, 0.2, 0.01
                );
            }

            if (step.completedNow()) {
                handleConstructionCompleted(level, data, village, job, step.spawnMarkersToTrigger());
                village.pruneCompletedConstructionJobs();
            }
            data.setDirty();
        }
    }

    private static void handleConstructionCompleted(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            ConstructionJob job,
            List<SpawnMarker.WorldSpawnMarker> markers
    ) {
        int popCap = TyC2RuntimeData.levelTable().getSpecClamped(village.getLevel()).maxPopulationCap();
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        int currentPop = findLivingVillagers(level, hubPos, null).size();

        for (SpawnMarker.WorldSpawnMarker marker : markers) {
            BlockPos spawnPos = TyC2Ids.toMc(marker.pos());
            if (!level.hasChunkAt(spawnPos)) {
                continue;
            }
            if (marker.kind() == SpawnMarker.MarkerKind.VILLAGER && currentPop < popCap) {
                Villager v = EntityType.VILLAGER.create(level);
                if (v != null) {
                    v.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, RNG.nextFloat() * 360F, 0F);
                    v.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
                    level.addFreshEntity(v);
                    currentPop++;
                }
            } else if (marker.kind() == SpawnMarker.MarkerKind.ANIMAL) {
                EntityType<?> entityType = resolveAnimalType(marker.hint());
                Entity e = entityType.create(level);
                if (e != null) {
                    e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, RNG.nextFloat() * 360F, 0F);
                    level.addFreshEntity(e);
                }
            }
        }

        // Spawn an Iron Golem per 10 villagers if fewer golems than floor(pop / 10)
        int desiredGolems = currentPop / 10;
        if (desiredGolems > 0) {
            List<IronGolem> existing = level.getEntitiesOfClass(
                    IronGolem.class, new AABB(hubPos).inflate(VILLAGE_SCAN_RADIUS), IronGolem::isAlive
            );
            if (existing.size() < desiredGolems) {
                IronGolem golem = EntityType.IRON_GOLEM.create(level);
                if (golem != null) {
                    BlockPos origin = TyC2Ids.toMc(job.getOrigin());
                    golem.moveTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0F, 0F);
                    golem.setPlayerCreated(false);
                    level.addFreshEntity(golem);
                }
            }
        }

        village.setPopulation(currentPop);
        broadcastToVillageStakeholders(
                level, hubPos, village,
                Component.translatable(
                        "status.tyc2.expansion.completed",
                        village.getVillageName(),
                        Component.translatable("project.tyc2." + job.getProjectType().name().toLowerCase())
                ).withStyle(ChatFormatting.GREEN)
        );
    }

    /** Generates a fresh set of Sell & Buy orders for {@code village} using live CPI, season, and tier rules. */
    public static void refreshVillageOrders(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            long gameTime
    ) {
        Season season = SeasonSoftBridge.currentSeason(level);
        IndustrialTier ownerTier = data.resolveVillageOwnerTier(village);
        DemandGenerator.GeneratedDemand demand = TyC2RuntimeData.demandGenerator().generate(
                village.getLevel(),
                village.getBiomeStyle().id(),
                village.getVillageBalance(),
                ownerTier,
                season,
                data.economy(),
                TyC2Ids::itemExists,
                RNG
        );
        long expires = gameTime + TyC2RuntimeData.config().demandRefreshTicks();
        List<VillageTradeOrder> orders = new ArrayList<>();
        for (DemandGenerator.GeneratedOrder go : demand.allOrders()) {
            OrderSide side = go.side() == DemandGenerator.OrderSide.VILLAGE_SELLS
                    ? OrderSide.VILLAGE_SELLS
                    : OrderSide.VILLAGE_BUYS;
            orders.add(new VillageTradeOrder(
                    go.orderId() + "_" + (gameTime % 100_000L),
                    side,
                    go.itemId(),
                    go.goodId(),
                    go.optionalEnchantmentSpec() == null ? "" : go.optionalEnchantmentSpec(),
                    go.quantity(),
                    0,
                    go.unitPrice(),
                    gameTime,
                    expires,
                    false,
                    go.seasonalHighlight()
            ));
        }
        village.setActiveOrders(orders);
        village.setLastDemandRefreshTick(gameTime);
        data.setDirty();
    }

    // --- Public Player / GUI Actions ---

    /** Opens the Mayor Negotiation GUI for an unpartnered village. */
    public static void openMayorNegotiationScreen(ServerPlayer player, VillageRecord village) {
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inv, p) -> new MayorNegotiationMenu(id, inv, village.getVillageId()),
                        Component.translatable("container.tyc2.mayor_negotiation")
                ),
                buf -> buf.writeUUID(village.getVillageId())
        );
    }

    /** Computes current player resources vs required Mayor negotiation package. */
    public static NegotiationStatus evaluateNegotiation(ServerPlayer player, TycoonForgeSavedData data) {
        TyC2Config cfg = TyC2RuntimeData.config();
        data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long cashIc = TyC2Ids.countPlayerCash(player);
        long bankIc = data.bank().getActiveBalance(player.getUUID());
        int logs = countMatchingItems(player.getInventory(), s -> TyC2Ids.matchesItemOrTag(s, "#minecraft:logs"));
        int iron = countMatchingItems(player.getInventory(), s -> TyC2Ids.matchesItemOrTag(s, "#ingots/iron"));
        int food = countMatchingItems(player.getInventory(), TycoonForgeWorldManager::isBasicNegotiationFood);
        return new NegotiationStatus(
                cashIc, bankIc, cfg.negotiationCostIc(),
                logs, cfg.negotiationRequiredLogs(),
                iron, cfg.negotiationRequiredIron(),
                food, cfg.negotiationRequiredFood()
        );
    }

    /** Signs a formal village partnership, consumes supplies, replaces Bell with Cooperation Hub, and generates Lv 1 orders. */
    public static ActionFeedback signPartnership(ServerPlayer player, UUID villageId) {
        ServerLevel level = player.getLevel();
        TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
        Optional<VillageRecord> recOpt = data.getVillage(villageId);
        if (recOpt.isEmpty()) {
            return new ActionFeedback(false, "status.tyc2.negotiation.missing_requirements");
        }
        VillageRecord village = recOpt.get();
        if (village.isPartnered()) {
            return new ActionFeedback(true, "status.tyc2.hub.partnership_signed", village.getVillageName());
        }
        NegotiationStatus status = evaluateNegotiation(player, data);
        if (!status.allMet()) {
            return new ActionFeedback(false, "status.tyc2.negotiation.missing_requirements");
        }

        // 1. Deduct currency (bank first if preferred or cash first -> pay remainder from bank)
        long requiredIc = status.requiredIc();
        long tick = level.getServer().overworld().getGameTime();
        long payFromCash = Math.min(requiredIc, status.cashIc());
        long payFromBank = requiredIc - payFromCash;
        if (payFromCash > 0L) {
            if (!TyC2Ids.consumePhysicalCash(player, null, payFromCash, Ic2Bridge.isLoaded())) {
                return new ActionFeedback(false, "status.tyc2.negotiation.missing_requirements");
            }
        }
        if (payFromBank > 0L) {
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            BankAccountManager.BankResult res = data.bank().debitAccount(
                    ref, payFromBank, BankAccountManager.TransactionType.NEGOTIATION,
                    tick, village.getVillageName(), "Village Partnership Capital"
            );
            if (!res.success()) {
                if (payFromCash > 0L) {
                    TyC2Ids.giveCoinsToPlayer(player, payFromCash, Ic2Bridge.isLoaded());
                }
                return new ActionFeedback(false, "status.tyc2.negotiation.missing_requirements");
            }
        }

        // 2. Consume basic startup supplies
        consumeMatchingItems(player.getInventory(), s -> TyC2Ids.matchesItemOrTag(s, "#minecraft:logs"), status.requiredLogs());
        consumeMatchingItems(player.getInventory(), s -> TyC2Ids.matchesItemOrTag(s, "#ingots/iron"), status.requiredIron());
        consumeMatchingItems(player.getInventory(), TycoonForgeWorldManager::isBasicNegotiationFood, status.requiredFood());

        // 3. Establish partnership and register existing village structures as protected zones
        BankAccountManager.AccountRef activeRef = data.bank().resolveSettlementAccount(player.getUUID());
        String companyId = activeRef.scope() == BankAccountManager.AccountScope.COMPANY ? activeRef.accountId() : null;
        village.establishPartnership(player.getUUID(), companyId, requiredIc);

        ServerLevel villageLevel = resolveLevel(level.getServer(), village.getDimensionId());
        if (villageLevel == null) {
            villageLevel = level;
        }
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        registerExistingVillageZones(villageLevel, village, hubPos);

        // 4. Replace village Bell with blast-proof Cooperation Hub bound to villageId
        if (villageLevel.hasChunkAt(hubPos)) {
            villageLevel.setBlock(hubPos, TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (villageLevel.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity hubBe) {
                hubBe.setVillageId(village.getVillageId());
            }
            villageLevel.playSound(null, hubPos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // 5. Generate initial Lv 1 orders immediately
        refreshVillageOrders(villageLevel, data, village, tick);
        data.syncMacroAccountTotals();
        data.setDirty();
        return new ActionFeedback(true, "status.tyc2.hub.partnership_signed", village.getVillageName());
    }

    /** Executes a player purchasing from a {@link OrderSide#VILLAGE_SELLS} order (cash or bank settlement). */
    public static ActionFeedback executeSellOrder(
            ServerPlayer player,
            UUID villageId,
            String orderId,
            int requestedQty,
            boolean useBank,
            @Nullable Container cashSlot
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }
        VillageRecord village = vOpt.get();
        Optional<VillageTradeOrder> oOpt = village.findOrder(orderId);
        if (oOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }
        VillageTradeOrder order = oOpt.get();
        int qty = Math.min(Math.max(1, requestedQty), order.remainingQty());
        if (qty <= 0) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }

        ItemStack sample = TyC2Ids.createStack(order.itemId(), 1, "", order.enchantSpec());
        int maxStack = sample.isEmpty() ? 64 : sample.getMaxStackSize();
        String nbtJson = TyC2Ids.nbtJson(sample);
        if (!village.getStorage().canFitInOutbound(order.itemId(), qty, nbtJson, maxStack)) {
            return new ActionFeedback(false, "status.tyc2.hub.outbound_full");
        }

        long totalCost = order.totalPriceFor(qty);
        long tick = player.getServer().overworld().getGameTime();
        if (useBank) {
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            BankAccountManager.BankResult debit = data.bank().debitAccount(
                    ref, totalCost, BankAccountManager.TransactionType.TRADE_BUY,
                    tick, village.getVillageName(), "Bought " + qty + "x " + order.itemId()
            );
            if (!debit.success()) {
                return new ActionFeedback(false, debit.errorKey());
            }
        } else {
            if (!TyC2Ids.consumePhysicalCash(player, cashSlot, totalCost, Ic2Bridge.isLoaded())) {
                return new ActionFeedback(false, "status.tyc2.atm.insufficient_balance");
            }
        }

        VillageRecord.TradeExecutionResult res = village.executeSellOrderToOutbound(orderId, qty, nbtJson, maxStack);
        if (!res.success()) {
            // Refund on unexpected failure
            if (useBank) {
                BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
                data.bank().creditAccount(ref, totalCost, BankAccountManager.TransactionType.SYSTEM_ADJUST, tick, "Refund", "");
            } else {
                TyC2Ids.giveCoinsToPlayer(player, totalCost, Ic2Bridge.isLoaded());
            }
            return new ActionFeedback(false, "status.tyc2.hub.outbound_full");
        }

        long basePrice = TyC2RuntimeData.goodsCatalog().findByItemId(order.itemId())
                .map(g -> g.basePrice()).orElse(order.unitPrice());
        data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, false);
        data.syncMacroAccountTotals();
        data.setDirty();
        return new ActionFeedback(true, "status.tyc2.hub.order_fulfilled", res.totalCurrency());
    }

    /** Executes a player fulfilling a {@link OrderSide#VILLAGE_BUYS} order from Hub Inbound slots (or player inv). */
    public static ActionFeedback executeBuyOrder(
            ServerPlayer player,
            UUID villageId,
            String orderId,
            int requestedQty,
            boolean useBank,
            boolean allowPlayerInventoryFallback
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }
        VillageRecord village = vOpt.get();
        Optional<VillageTradeOrder> oOpt = village.findOrder(orderId);
        if (oOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }
        VillageTradeOrder order = oOpt.get();
        int desired = Math.min(Math.max(1, requestedQty), order.remainingQty());
        if (desired <= 0) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }

        // If Inbound doesn't have enough yet and player is at the physical Hub, pull from player inventory into Inbound
        int inInbound = village.getStorage().countInInbound(order.itemId());
        if (allowPlayerInventoryFallback && inInbound < desired) {
            int need = desired - inInbound;
            int pulled = pullItemsFromPlayerIntoInbound(player.getInventory(), village, order.itemId(), need);
            inInbound += pulled;
        }

        if (inInbound <= 0) {
            return new ActionFeedback(false, "status.tyc2.negotiation.missing_requirements");
        }
        if (village.getVillageBalance() < order.unitPrice()) {
            return new ActionFeedback(false, "status.tyc2.hub.village_low_funds");
        }

        VillageRecord.TradeExecutionResult res = village.executeBuyOrderFromInbound(orderId, Math.min(desired, inInbound));
        if (!res.success()) {
            return new ActionFeedback(false, "status.tyc2.hub.village_low_funds");
        }

        long tick = player.getServer().overworld().getGameTime();
        if (useBank) {
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            data.bank().creditAccount(
                    ref, res.totalCurrency(), BankAccountManager.TransactionType.TRADE_SELL,
                    tick, village.getVillageName(), "Sold " + res.executedQty() + "x " + order.itemId()
            );
        } else {
            TyC2Ids.giveCoinsToPlayer(player, res.totalCurrency(), Ic2Bridge.isLoaded());
        }

        long basePrice = TyC2RuntimeData.goodsCatalog().findByItemId(order.itemId())
                .map(g -> g.basePrice()).orElse(order.unitPrice());
        data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
        data.syncMacroAccountTotals();
        data.setDirty();
        return new ActionFeedback(true, "status.tyc2.hub.order_fulfilled", res.totalCurrency());
    }

    /** Toggles per-order auto-fulfill flag. */
    public static boolean toggleOrderAutoFulfill(MinecraftServer server, UUID villageId, String orderId) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(server);
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return false;
        }
        VillageRecord village = vOpt.get();
        Optional<VillageTradeOrder> oOpt = village.findOrder(orderId);
        if (oOpt.isEmpty()) {
            return false;
        }
        VillageTradeOrder updated = oOpt.get().withAutoFulfill(!oOpt.get().autoFulfill());
        village.updateOrder(updated);
        data.setDirty();
        return true;
    }

    /** Attempts to upgrade a partnered village to {@code level + 1}, burning the municipal capital sink. */
    public static ActionFeedback upgradeVillage(ServerPlayer player, UUID villageId) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "status.tyc2.hub.upgrade_failed");
        }
        VillageRecord village = vOpt.get();
        long burned = village.tryUpgrade(TyC2RuntimeData.levelTable());
        if (burned <= 0L) {
            return new ActionFeedback(false, "status.tyc2.hub.upgrade_failed");
        }
        data.economy().recordVillageMoneySink(burned);
        data.syncMacroAccountTotals();
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel != null) {
            refreshVillageOrders(dimLevel, data, village, player.getServer().overworld().getGameTime());
        }
        data.setDirty();
        return new ActionFeedback(
                true,
                "status.tyc2.hub.upgraded",
                village.getVillageName(),
                village.getLevel(),
                Component.translatable("level.tyc2." + village.getLevel())
        );
    }

    /** Pulls available expansion materials from Hub Inbound (and player inventory if at Hub) and starts construction when ready. */
    public static ActionFeedback deliverExpansionMaterials(
            ServerPlayer player,
            UUID villageId,
            boolean pullFromPlayerInventory
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.expansion.none");
        }
        VillageRecord village = vOpt.get();
        ExpansionDesire exp = village.getActiveExpansion();
        if (exp == null) {
            // Propose a fresh desire immediately so player can trigger expansion on demand
            ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
            boolean hasWater = dimLevel != null && hasNearbyWaterIfLoaded(dimLevel, TyC2Ids.toMc(village.getHubPos()));
            ProjectType next = PLANNER.selectNextProjectType(village, hasWater);
            exp = ExpansionDesire.createDefault(
                    "exp_" + player.getServer().overworld().getGameTime(),
                    next,
                    village.getBiomeStyle(),
                    false
            );
            village.setActiveExpansion(exp);
        }

        if (pullFromPlayerInventory) {
            for (var entry : exp.remainingMaterials().entrySet()) {
                pullItemsFromPlayerIntoInbound(player.getInventory(), village, entry.getKey(), entry.getValue());
            }
        }
        ExpansionDesire updated = exp.pullAvailableFromInbound(village.getStorage());
        village.setActiveExpansion(updated);
        data.setDirty();

        if (!updated.allMaterialsDelivered()) {
            return new ActionFeedback(true, "gui.tyc2.expansion.deliver");
        }
        if (village.getVillageBalance() < updated.constructionBudgetCost()) {
            return new ActionFeedback(false, "status.tyc2.hub.village_low_funds");
        }
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel == null) {
            return new ActionFeedback(false, "status.tyc2.expansion.no_site");
        }
        return tryLaunchExpansionConstruction(dimLevel, data, village, player.getServer().overworld().getGameTime());
    }

    /** Plans and launches gradual construction for the village's current {@link ExpansionDesire}. */
    public static ActionFeedback tryLaunchExpansionConstruction(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            long gameTime
    ) {
        ExpansionDesire exp = village.getActiveExpansion();
        if (exp == null) {
            return new ActionFeedback(false, "gui.tyc2.expansion.none");
        }
        ServerTerrainProbe probe = new ServerTerrainProbe(level, data.placedBlocks());
        String jobId = "job_" + gameTime + "_" + village.getOccupiedZones().size();
        ExpansionPlanner.PlanResult plan = PLANNER.plan(village, exp.projectType(), probe, jobId);
        if (!plan.success()) {
            return new ActionFeedback(false, "status.tyc2.expansion.no_site");
        }
        long cost = exp.constructionBudgetCost();
        if (cost > 0L) {
            village.adjustVillageBalance(-cost);
            data.economy().recordVillageMoneySink(cost);
        }
        PLANNER.commitPlan(village, plan, gameTime);
        village.setActiveExpansion(null);
        data.syncMacroAccountTotals();
        data.setDirty();

        Component projName = Component.translatable("project.tyc2." + exp.projectType().name().toLowerCase());
        broadcastToVillageStakeholders(
                level, TyC2Ids.toMc(village.getHubPos()), village,
                Component.translatable("status.tyc2.expansion.started", village.getVillageName(), projName)
                        .withStyle(ChatFormatting.AQUA)
        );
        return new ActionFeedback(true, "status.tyc2.expansion.started", village.getVillageName(), projName);
    }

    /** Admin/command helper that plans & enqueues an expansion immediately without requiring materials. */
    public static ActionFeedback forcePlanExpansion(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            @Nullable ProjectType forcedType
    ) {
        long gameTime = level.getServer().overworld().getGameTime();
        ProjectType type = forcedType != null
                ? forcedType
                : PLANNER.selectNextProjectType(village, hasNearbyWaterIfLoaded(level, TyC2Ids.toMc(village.getHubPos())));
        boolean wasPaused = village.isExpansionPaused();
        village.setExpansionPaused(false);
        ServerTerrainProbe probe = new ServerTerrainProbe(level, data.placedBlocks());
        ExpansionPlanner.PlanResult plan = PLANNER.plan(village, type, probe, "cmd_job_" + gameTime);
        village.setExpansionPaused(wasPaused);
        if (!plan.success()) {
            return new ActionFeedback(false, "status.tyc2.expansion.no_site");
        }
        PLANNER.commitPlan(village, plan, gameTime);
        data.setDirty();
        return new ActionFeedback(
                true,
                "status.tyc2.expansion.started",
                village.getVillageName(),
                Component.translatable("project.tyc2." + type.name().toLowerCase())
        );
    }

    /** Checks whether {@code player} is authorized to manage ownership/relocation/termination for {@code village}. */
    public static boolean canManageVillage(ServerPlayer player, TycoonForgeSavedData data, VillageRecord village) {
        if (player.hasPermissions(2)) {
            return true;
        }
        if (player.getUUID().equals(village.getOwnerPlayerUuid())) {
            return true;
        }
        if (village.getCompanyAccountId() != null) {
            return data.bank().getCompany(village.getCompanyAccountId())
                    .map(co -> co.ownerUuid().equals(player.getUUID()))
                    .orElse(false);
        }
        return false;
    }

    /** Packs the Cooperation Hub safely into a bound item without losing any of its 1,000 warehouse slots. */
    public static ActionFeedback relocateHub(ServerPlayer player, UUID villageId) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        VillageRecord village = vOpt.get();
        if (!canManageVillage(player, data, village)) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel != null) {
            BlockPos oldPos = TyC2Ids.toMc(village.getHubPos());
            if (dimLevel.hasChunkAt(oldPos)
                    && dimLevel.getBlockState(oldPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())) {
                if (dimLevel.getBlockEntity(oldPos) instanceof CooperationHubBlockEntity hubBe) {
                    hubBe.dropUpgradesTo(player);
                }
                dimLevel.removeBlock(oldPos, false);
            }
        }
        ItemStack boundHub = new ItemStack(TyC2ForgeRegistries.COOPERATION_HUB_ITEM.get());
        CompoundTag tag = boundHub.getOrCreateTag();
        tag.putUUID(TyC2BlockItem.TAG_BOUND_VILLAGE_ID, village.getVillageId());
        tag.putString(TyC2BlockItem.TAG_BOUND_VILLAGE_NAME, village.getVillageName());
        if (!player.getInventory().add(boundHub)) {
            player.drop(boundHub, false);
        }
        data.setDirty();
        return new ActionFeedback(true, "status.tyc2.hub.relocated");
    }

    /** Terminates partnership, restores the village Bell, and clears orders/expansion while preserving data safely. */
    public static ActionFeedback terminatePartnership(ServerPlayer player, UUID villageId) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        VillageRecord village = vOpt.get();
        if (!canManageVillage(player, data, village)) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        String name = village.getVillageName();
        village.terminatePartnership();
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel != null) {
            BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
            if (dimLevel.hasChunkAt(hubPos)
                    && dimLevel.getBlockState(hubPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())) {
                if (dimLevel.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity hubBe) {
                    hubBe.dropUpgradesTo(player);
                }
                dimLevel.setBlock(hubPos, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        data.syncMacroAccountTotals();
        data.setDirty();
        return new ActionFeedback(true, "status.tyc2.hub.partnership_terminated", name);
    }

    // --- Internal Helpers ---

    @Nullable
    public static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceLocation rl = TyC2Ids.parse(dimensionId);
        if (rl == null) {
            return server.overworld();
        }
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, rl);
        return server.getLevel(key);
    }

    private static List<Villager> findLivingVillagers(ServerLevel level, BlockPos center, @Nullable UUID exclude) {
        AABB box = new AABB(center).inflate(VILLAGE_SCAN_RADIUS);
        return level.getEntitiesOfClass(
                Villager.class,
                box,
                v -> v.isAlive() && (exclude == null || !v.getUUID().equals(exclude))
        );
    }

    private static List<MayorElectionRules.MayorCandidate> toCandidates(List<Villager> villagers) {
        List<MayorElectionRules.MayorCandidate> list = new ArrayList<>(villagers.size());
        for (Villager v : villagers) {
            String baseName = v.hasCustomName()
                    ? v.getCustomName().getString().replaceFirst("^\\[[^]]+]\\s*", "")
                    : v.getDisplayName().getString();
            list.add(new MayorElectionRules.MayorCandidate(
                    v.getUUID(),
                    baseName,
                    !v.isBaby(),
                    v.getVillagerData().getProfession() == VillagerProfession.NITWIT,
                    v.isAlive(),
                    v.getVillagerXp(),
                    v.tickCount
            ));
        }
        return list;
    }

    private static void applyMayorEntityTag(
            ServerLevel level,
            List<Villager> villagers,
            @Nullable UUID mayorUuid,
            VillageRecord village
    ) {
        if (mayorUuid == null) {
            return;
        }
        for (Villager v : villagers) {
            CompoundTag pdata = v.getPersistentData();
            if (v.getUUID().equals(mayorUuid)) {
                pdata.putBoolean(TAG_MAYOR, true);
                pdata.putString(TAG_VILLAGE_ID, village.getVillageId().toString());
                String display = village.getMayorState().getMayorName().isBlank()
                        ? village.getVillageName()
                        : village.getMayorState().getMayorName();
                v.setCustomName(Component.translatable("mayor.tyc2.name_format", display)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                v.setCustomNameVisible(true);
                v.setPersistenceRequired();
            } else if (pdata.getBoolean(TAG_MAYOR)
                    && village.getVillageId().toString().equals(pdata.getString(TAG_VILLAGE_ID))) {
                pdata.remove(TAG_MAYOR);
            }
        }
    }

    private static void broadcastToVillageStakeholders(
            ServerLevel level,
            BlockPos hubPos,
            VillageRecord village,
            Component message
    ) {
        UUID owner = village.getOwnerPlayerUuid();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            boolean isOwner = owner != null && owner.equals(player.getUUID());
            boolean isNear = player.level == level && player.blockPosition().closerThan(hubPos, 64.0);
            if (isOwner || isNear) {
                player.sendSystemMessage(message);
            }
        }
    }

    @Nullable
    private static BlockPos findNearbyBell(ServerLevel level, BlockPos center, int radius) {
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - 12);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 16);
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int radiusSq = radius * radius;

        // Fast exact O(BE) scan over all loaded chunks in range (covers all even & odd coordinates)
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof BellBlockEntity)) {
                        continue;
                    }
                    BlockPos bp = be.getBlockPos();
                    if (bp.getY() < minY || bp.getY() > maxY) {
                        continue;
                    }
                    int dx = bp.getX() - center.getX();
                    int dz = bp.getZ() - center.getZ();
                    if (dx * dx + dz * dz > radiusSq) {
                        continue;
                    }
                    double dSq = bp.distSqr(center);
                    if (dSq < bestDistSq) {
                        bestDistSq = dSq;
                        best = bp.immutable();
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }

        // Exact 1-block-step fallback in case a Bell block exists without a loaded BE entry
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int wx = center.getX() + dx;
                int wz = center.getZ() + dz;
                if (!level.hasChunkAt(cursor.set(wx, center.getY(), wz))) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    if (level.getBlockState(cursor.set(wx, y, wz)).is(Blocks.BELL)) {
                        double dSq = cursor.distSqr(center);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            best = cursor.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private static BiomeStyle detectBiomeStyle(ServerLevel level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        return holder.unwrapKey()
                .map(k -> BiomeStyle.fromBiomeId(k.location().toString()))
                .orElse(BiomeStyle.PLAINS);
    }

    private static void registerExistingVillageZones(ServerLevel level, VillageRecord village, BlockPos hubPos) {
        // Protect Cooperation Hub core (5x5)
        village.addOccupiedZone(new Aabb3i(
                hubPos.getX() - 2, hubPos.getY() - 1, hubPos.getZ() - 2,
                hubPos.getX() + 2, hubPos.getY() + 3, hubPos.getZ() + 2
        ));
        if (!level.hasChunkAt(hubPos)) {
            return;
        }
        // Scan radius 32 for existing village beds, doors, and workstations and register protected boxes
        int radius = 32;
        int minY = Math.max(level.getMinBuildHeight(), hubPos.getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, hubPos.getY() + 14);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int wx = hubPos.getX() + dx;
                int wz = hubPos.getZ() + dz;
                if (!level.hasChunkAt(cursor.set(wx, hubPos.getY(), wz))) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockState st = level.getBlockState(cursor.set(wx, y, wz));
                    if (st.is(BlockTags.BEDS) || st.is(BlockTags.DOORS)) {
                        Aabb3i box = new Aabb3i(wx - 3, y - 1, wz - 3, wx + 3, y + 4, wz + 3);
                        boolean duplicate = false;
                        for (Aabb3i existing : village.getOccupiedZones()) {
                            if (existing.contains(wx, y, wz)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            village.addOccupiedZone(box);
                        }
                    }
                }
            }
        }
    }

    private static boolean hasNearbyWaterIfLoaded(ServerLevel level, BlockPos hubPos) {
        if (!level.hasChunkAt(hubPos)) {
            return false;
        }
        ServerTerrainProbe probe = new ServerTerrainProbe(level, TycoonForgeSavedData.get(level).placedBlocks());
        for (int r = 12; r <= 48; r += 8) {
            for (int a = 0; a < 12; a++) {
                double ang = (Math.PI * 2.0 * a) / 12.0;
                int wx = hubPos.getX() + (int) Math.round(Math.cos(ang) * r);
                int wz = hubPos.getZ() + (int) Math.round(Math.sin(ang) * r);
                if (probe.isWaterSurface(wx, wz)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static BankAccountManager.AccountRef resolveVillageSettlementAccount(
            TycoonForgeSavedData data,
            VillageRecord village
    ) {
        if (village.getCompanyAccountId() != null && data.bank().getCompany(village.getCompanyAccountId()).isPresent()) {
            return BankAccountManager.AccountRef.company(village.getCompanyAccountId());
        }
        if (village.getOwnerPlayerUuid() != null) {
            data.bank().getOrCreatePersonal(village.getOwnerPlayerUuid(), null);
            return BankAccountManager.AccountRef.personal(village.getOwnerPlayerUuid());
        }
        return null;
    }

    private static int pullItemsFromPlayerIntoInbound(
            Inventory inv,
            VillageRecord village,
            String targetItemId,
            int maxNeeded
    ) {
        int moved = 0;
        for (int i = 0; i < inv.getContainerSize() && moved < maxNeeded; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !TyC2Ids.itemId(stack).equals(targetItemId)) {
                continue;
            }
            int take = Math.min(stack.getCount(), maxNeeded - moved);
            int remainder = village.getStorage().insertExternal(
                    targetItemId, take, TyC2Ids.nbtJson(stack), stack.getMaxStackSize(), false
            );
            int inserted = take - remainder;
            if (inserted > 0) {
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                moved += inserted;
            }
        }
        return moved;
    }

    private static boolean isBasicNegotiationFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (BASIC_NEGOTIATION_FOODS.contains(TyC2Ids.itemId(stack))) {
            return true;
        }
        Item item = stack.getItem();
        return item.isEdible() && item.getFoodProperties() != null && item.getFoodProperties().getNutrition() >= 3;
    }

    private static int countMatchingItems(Inventory inv, java.util.function.Predicate<ItemStack> predicate) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeMatchingItems(
            Inventory inv,
            java.util.function.Predicate<ItemStack> predicate,
            int count
    ) {
        int rem = count;
        for (int i = 0; i < inv.getContainerSize() && rem > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                int take = Math.min(rem, stack.getCount());
                stack.shrink(take);
                if (stack.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                rem -= take;
            }
        }
    }

    private static EntityType<?> resolveAnimalType(String hint) {
        ResourceLocation rl = TyC2Ids.parse(hint);
        if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
            EntityType<?> found = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (found != null) {
                return found;
            }
        }
        return EntityType.COW;
    }

    @Nullable
    private static UUID parseUuidSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
