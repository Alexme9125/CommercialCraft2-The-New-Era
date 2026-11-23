package com.tycooncraft.tyc2.world;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.item.CooperationHubBlockItem;
import com.tycooncraft.tyc2.menu.MayorNegotiationMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.network.TyC2SyncPayload;
import com.tycooncraft.tyc2.platform.TyC2ChunkTickets;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.Aabb3i;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.ConstructionJob;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.ExpansionPlanner;
import com.tycooncraft.tyc2.village.MayorElectionRules;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.SpawnMarker;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central server-side orchestrator for TycoonCraft²: The New Era.
 * Manages Overworld {@link TycoonSavedData}, village & Mayor discovery/elections,
 * virtual economy/demand/upkeep/auto-fulfill timers, collision-safe gradual construction,
 * banking/trade/negotiation execution, and GUI synchronization payloads.
 */
public final class TyC2WorldManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TyC2WorldManager.class);
    private static final ExpansionPlanner PLANNER = new ExpansionPlanner();
    private static final Random RNG = new Random();

    private static final String[] VILLAGE_NAME_PREFIXES = {
            "Oak", "Silver", "Iron", "Pine", "Sun", "Frost", "River", "Stone", "Amber", "Green", "High", "Dawn"
    };
    private static final String[] VILLAGE_NAME_SUFFIXES = {
            "vale", "haven", "brook", "stead", "crest", "ford", "field", "watch", "port", "ridge", "shire", "mill"
    };

    private TyC2WorldManager() {
    }

    /** Subscribes all server gameplay event listeners on the NeoForge game event bus. */
    public static void install(IEventBus gameBus) {
        gameBus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                tickServerLevel(serverLevel);
            }
        });

        gameBus.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                TyC2ChunkTickets.clearLevel(serverLevel);
            }
        });

        gameBus.addListener(BlockEvent.EntityPlaceEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel serverLevel
                    && event.getEntity() instanceof ServerPlayer player) {
                TycoonSavedData data = getData(serverLevel);
                data.recordPlayerPlaced(TyC2Native.dimensionId(serverLevel), event.getPos());
                String placedId = TyC2Native.blockId(event.getPlacedBlock());
                if (data.industrialTiers().recordItemMilestone(player.getUUID(), placedId, Ic2SoftBridge.isIc2Loaded())) {
                    data.setDirty();
                }
            }
        });

        TyC2Native.registerBreakBlockListener(gameBus, (serverLevel, pos, state, player) -> {
            if (state.is(TyC2Registries.COOPERATION_HUB_BLOCK.get())) {
                if (!(player instanceof ServerPlayer sp) || (!sp.isCreative() || !TyC2Native.hasOpPermission(sp))) {
                    if (player != null) {
                        TyC2Native.sendActionBar(
                                player,
                                Component.translatable("status.tyc2.hub.protected").withStyle(ChatFormatting.RED)
                        );
                    }
                    return true;
                }
            }
            getData(serverLevel).removePlayerPlaced(TyC2Native.dimensionId(serverLevel), pos);
            return false;
        });

        gameBus.addListener(PlayerInteractEvent.EntityInteract.class, TyC2WorldManager::onEntityInteract);
        gameBus.addListener(LivingDeathEvent.class, TyC2WorldManager::onLivingDeath);
        gameBus.addListener(PlayerTickEvent.Post.class, TyC2WorldManager::onPlayerTick);
    }

    /** Retrieves the single world-wide {@link TycoonSavedData} stored on the Overworld. */
    public static TycoonSavedData getData(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return TycoonSavedData.getOrCreate(overworld != null ? overworld : level);
    }

    /** Marks world economy data dirty for disk persistence. */
    public static void markDirty(ServerLevel level) {
        getData(level).setDirty();
    }

    /** Records newly minted coins in {@link MacroEconomyEngine}. */
    public static void recordCoinsMinted(ServerLevel level, long faceValueTotal) {
        TycoonSavedData data = getData(level);
        data.economy().recordMinting(faceValueTotal);
        data.setDirty();
    }

    /** Looks up a village by UUID. */
    public static Optional<VillageRecord> getVillage(ServerLevel level, UUID villageId) {
        return getData(level).getVillage(villageId);
    }

    /** Looks up a village whose Hub position matches {@code pos} in {@code level}. */
    public static Optional<VillageRecord> findVillageByHubPos(ServerLevel level, BlockPos pos) {
        String dim = TyC2Native.dimensionId(level);
        for (VillageRecord v : getData(level).allVillages()) {
            if (dim.equals(v.getDimensionId())
                    && v.getHubPos().x() == pos.getX()
                    && v.getHubPos().y() == pos.getY()
                    && v.getHubPos().z() == pos.getZ()) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    /** Finds the nearest tracked village within {@code maxHorizontalDist} blocks of {@code pos}. */
    public static Optional<VillageRecord> findNearestVillage(ServerLevel level, BlockPos pos, int maxHorizontalDist) {
        String dim = TyC2Native.dimensionId(level);
        long maxDistSq = (long) maxHorizontalDist * maxHorizontalDist;
        VillageRecord best = null;
        long bestDistSq = Long.MAX_VALUE;
        for (VillageRecord v : getData(level).allVillages()) {
            if (!dim.equals(v.getDimensionId())) {
                continue;
            }
            long dx = v.getHubPos().x() - pos.getX();
            long dz = v.getHubPos().z() - pos.getZ();
            long d2 = dx * dx + dz * dz;
            if (d2 <= maxDistSq && d2 < bestDistSq) {
                bestDistSq = d2;
                best = v;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Creates or returns an existing VillageRecord for a newly placed Hub (e.g. Creative placement). */
    public static VillageRecord getOrCreateVillageForHub(ServerLevel level, BlockPos pos, UUID ownerUuid) {
        Optional<VillageRecord> existing = findVillageByHubPos(level, pos);
        if (existing.isPresent()) {
            return existing.get();
        }
        TycoonSavedData data = getData(level);
        BiomeStyle style = BiomeStyle.fromBiomeId(TyC2Native.biomeId(level, pos));
        String name = generateVillageName(pos);
        VillageRecord v = new VillageRecord(
                UUID.randomUUID(),
                TyC2Native.dimensionId(level),
                name,
                new BlockPos3i(pos.getX(), pos.getY(), pos.getZ()),
                style
        );
        if (ownerUuid != null) {
            String activeCo = data.bank().getPersonal(ownerUuid)
                    .map(BankAccountManager.PersonalAccount::activeCompanyId)
                    .orElse(null);
            v.establishPartnership(ownerUuid, activeCo, TyC2DataLoader.config().negotiationCostIc());
            refreshVillageOrders(level, data, v, level.getGameTime());
        }
        data.putVillage(v);
        return v;
    }

    // =========================================================================
    // Server Tick Loop (Virtual Economy + Loaded Village Scans + Construction)
    // =========================================================================

    private static void tickServerLevel(ServerLevel level) {
        long gameTime = level.getGameTime();
        boolean isOverworld = level.dimension() == Level.OVERWORLD;
        TycoonSavedData data = getData(level);
        TyC2Config cfg = TyC2DataLoader.config();

        if (isOverworld) {
            tickOverworldVirtualEconomy(level, data, cfg, gameTime);
        }

        // Gradual construction ticker for villages belonging to this dimension
        String dimId = TyC2Native.dimensionId(level);
        for (VillageRecord v : data.allVillages()) {
            if (!dimId.equals(v.getDimensionId())) {
                continue;
            }
            tickVillageConstruction(level, data, v);
        }

        // Periodic Bell/Hub village scan & Mayor election near online players (every 80 ticks)
        if (gameTime % 80L == 0L && !level.players().isEmpty()) {
            scanLoadedVillagesAndMayors(level, data, gameTime);
        }
    }

    private static void tickOverworldVirtualEconomy(
            ServerLevel overworld,
            TycoonSavedData data,
            TyC2Config cfg,
            long gameTime
    ) {
        // 1. Sync bank + village balances into MacroEconomyEngine periodically
        if (gameTime % 40L == 0L) {
            long villageTotal = 0L;
            int partneredCount = 0;
            for (VillageRecord v : data.allVillages()) {
                if (v.isPartnered()) {
                    villageTotal += v.getVillageBalance();
                    partneredCount++;
                }
            }
            data.economy().syncAccountTotals(
                    data.bank().getTotalCombinedBalance(),
                    villageTotal,
                    Math.max(1, partneredCount)
            );
        }

        // 2. Daily municipal upkeep deflationary sink
        long currentDay = overworld.getGameTime() / 24_000L;
        if (data.lastUpkeepDay() < 0L) {
            data.setLastUpkeepDay(currentDay);
        } else if (currentDay > data.lastUpkeepDay()) {
            data.setLastUpkeepDay(currentDay);
            long totalUpkeep = 0L;
            for (VillageRecord v : data.allVillages()) {
                if (v.isPartnered()) {
                    long upkeep = v.applyMunicipalUpkeep();
                    if (upkeep > 0L) {
                        data.economy().recordVillageMoneySink(upkeep);
                        totalUpkeep += upkeep;
                    }
                }
            }
            if (totalUpkeep > 0L) {
                data.setDirty();
            }
        }

        // 3. Per-village virtual timers (run even when Hub chunks are unloaded!)
        boolean runAutoFulfill = (gameTime % cfg.autoFulfillIntervalTicks()) == 0L;
        boolean recordedCycleSnapshot = false;

        for (VillageRecord v : data.allVillages()) {
            if (!v.isPartnered()) {
                continue;
            }

            // 3a. Auto-fulfill Buy Orders & Expansion Desire from Inbound 0..499
            if (runAutoFulfill) {
                processAutoFulfill(overworld, data, v, gameTime);
            }

            // 3b. Demand order refresh (periodic or when all active orders completed)
            if (v.canGenerateOrders()) {
                boolean allDone = !v.getActiveOrders().isEmpty()
                        && v.getActiveOrders().stream().allMatch(VillageTradeOrder::isCompleted);
                boolean refreshDue = v.getActiveOrders().isEmpty()
                        || (gameTime - v.getLastDemandRefreshTick()) >= cfg.demandRefreshTicks()
                        || allDone;
                if (refreshDue) {
                    if (!recordedCycleSnapshot) {
                        data.economy().advanceDemandCycle(gameTime);
                        recordedCycleSnapshot = true;
                    }
                    refreshVillageOrders(overworld, data, v, gameTime);
                }
            }

            // 3c. Periodic Expansion Desire generation
            if (!v.isExpansionPaused()
                    && v.canGenerateOrders()
                    && v.getActiveExpansion() == null
                    && v.getActiveConstructionJob().isEmpty()
                    && (gameTime - v.getLastExpansionTick()) >= cfg.expansionIntervalTicks()) {
                boolean hasWater = isWaterNearVillage(overworld, v);
                ProjectType nextType = PLANNER.selectNextProjectType(v, hasWater);
                ExpansionDesire desire = ExpansionDesire.createDefault(
                        "exp_" + gameTime + "_" + v.getOccupiedZones().size(),
                        nextType,
                        v.getBiomeStyle(),
                        false
                );
                v.setActiveExpansion(desire);
                v.setLastExpansionTick(gameTime);
                data.setDirty();
            }
        }

        // Ensure at least 1 snapshot exists for the CPI chart on brand new worlds
        if (data.economy().history().isEmpty()) {
            data.economy().recordSnapshot(gameTime);
            data.setDirty();
        }
    }

    private static void processAutoFulfill(
            ServerLevel overworld,
            TycoonSavedData data,
            VillageRecord v,
            long gameTime
    ) {
        BankAccountManager.AccountRef payoutAccount = resolveVillageOwnerAccount(data, v);

        // Auto-fulfill Village Buy Orders
        List<VillageTradeOrder> snapshot = new ArrayList<>(v.getActiveOrders());
        for (VillageTradeOrder order : snapshot) {
            if (order.side() != OrderSide.VILLAGE_BUYS || !order.autoFulfill() || order.isCompleted()) {
                continue;
            }
            VillageRecord.TradeExecutionResult res = v.executeBuyOrderFromInbound(order.orderId(), order.remainingQty());
            if (res.success() && res.executedQty() > 0) {
                if (payoutAccount != null) {
                    data.bank().creditAccount(
                            payoutAccount,
                            res.totalCurrency(),
                            BankAccountManager.TransactionType.TRADE_SELL,
                            gameTime,
                            v.getVillageName(),
                            "Auto-fulfill " + order.itemId()
                    );
                }
                long basePrice = TyC2DataLoader.goodsCatalog().findByItemId(order.itemId())
                        .map(g -> g.basePrice())
                        .orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
                data.setDirty();
            }
        }

        // Auto-fulfill Expansion Desire
        ExpansionDesire desire = v.getActiveExpansion();
        if (desire != null && !desire.completed() && desire.autoFulfill()) {
            ExpansionDesire updated = desire.pullAvailableFromInbound(v.getStorage());
            if (!updated.equals(desire)) {
                v.setActiveExpansion(updated);
                data.setDirty();
            }
            if (updated.allMaterialsDelivered()) {
                tryLaunchExpansionFromDesire(overworld, data, v, gameTime, null);
            }
        }
    }

    private static void tickVillageConstruction(ServerLevel level, TycoonSavedData data, VillageRecord v) {
        Optional<ConstructionJob> jobOpt = v.getActiveConstructionJob();
        if (jobOpt.isEmpty()) {
            return;
        }
        ConstructionJob job = jobOpt.get();
        BlockPos originBlock = new BlockPos(job.getOrigin().x(), job.getOrigin().y(), job.getOrigin().z());
        if (!level.hasChunkAt(originBlock)) {
            return;
        }
        ConstructionJob.StepResult step = job.tick();
        if (step.placedBlocks().isEmpty() && !step.completedNow()) {
            return;
        }
        for (WorldBlockPlacement wp : step.placedBlocks()) {
            BlockPos bp = new BlockPos(wp.pos().x(), wp.pos().y(), wp.pos().z());
            if (!level.hasChunkAt(bp)) {
                continue;
            }
            // Final anti-grief double check right before setting any block
            if (data.isPlayerPlaced(v.getDimensionId(), bp.getX(), bp.getY(), bp.getZ())
                    || level.getBlockEntity(bp) != null) {
                continue;
            }
            BlockState targetState = BlockStateSpecParser.parse(wp.blockStateSpec());
            level.setBlock(bp, targetState, Block.UPDATE_ALL);
            level.sendParticles(
                    ParticleTypes.CLOUD,
                    bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                    3, 0.25, 0.25, 0.25, 0.01
            );
            level.playSound(
                    null, bp, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.45F, 1.0F
            );
        }
        if (step.completedNow()) {
            handleConstructionCompleted(level, data, v, job, step.spawnMarkersToTrigger());
            v.pruneCompletedConstructionJobs();
        }
        data.setDirty();
    }

    private static void handleConstructionCompleted(
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord v,
            ConstructionJob job,
            List<SpawnMarker.WorldSpawnMarker> markers
    ) {
        int popCap = TyC2DataLoader.levelTable().getSpecClamped(v.getLevel()).maxPopulationCap();
        int spawnedVillagers = 0;
        for (SpawnMarker.WorldSpawnMarker marker : markers) {
            BlockPos pos = new BlockPos(marker.pos().x(), marker.pos().y(), marker.pos().z());
            if (marker.kind() == SpawnMarker.MarkerKind.VILLAGER) {
                if (v.getPopulation() + spawnedVillagers < popCap) {
                    TyC2Native.spawnVillager(level, pos);
                    spawnedVillagers++;
                }
            } else if (marker.kind() == SpawnMarker.MarkerKind.ANIMAL) {
                TyC2Native.spawnAnimal(level, pos, marker.hint());
            }
        }
        if (spawnedVillagers > 0) {
            v.setPopulation(v.getPopulation() + spawnedVillagers);
            int desiredGolems = v.getPopulation() / 10;
            BlockPos hub = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
            TyC2Native.spawnIronGolemIfNeeded(level, hub, 36.0, desiredGolems);
        }

        // Notify owner if online
        if (v.getOwnerPlayerUuid() != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(v.getOwnerPlayerUuid());
            if (owner != null) {
                Component projectTitle = Component.translatable(
                        "project.tyc2." + job.getProjectType().name().toLowerCase(Locale.ROOT)
                );
                owner.sendSystemMessage(Component.translatable(
                        "status.tyc2.expansion.completed", v.getVillageName(), projectTitle
                ).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static void scanLoadedVillagesAndMayors(ServerLevel level, TycoonSavedData data, long gameTime) {
        String dimId = TyC2Native.dimensionId(level);

        // 1. Update already-tracked villages whose hub chunk is loaded
        for (VillageRecord v : new ArrayList<>(data.allVillages())) {
            if (!dimId.equals(v.getDimensionId())) {
                continue;
            }
            BlockPos hubPos = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
            if (!level.hasChunkAt(hubPos)) {
                continue;
            }
            evaluateVillageMayorAndPop(level, data, v, hubPos, gameTime);
        }

        // 2. Discover new vanilla villages around Bells near online players
        for (ServerPlayer player : level.players()) {
            BlockPos pPos = player.blockPosition();
            List<Entity> nearbyVillagers = TyC2Native.findVillagersInRadius(level, pPos, 48.0);
            if (nearbyVillagers.size() < MayorElectionRules.MIN_VILLAGERS_FOR_MAYOR) {
                continue;
            }
            if (findNearestVillage(level, pPos, TyC2Constants.MIN_VILLAGE_SEPARATION).isPresent()) {
                continue;
            }
            BlockPos bellPos = findNearbyBell(level, pPos, 32);
            if (bellPos == null) {
                continue;
            }
            if (findNearestVillage(level, bellPos, TyC2Constants.MIN_VILLAGE_SEPARATION).isPresent()) {
                continue;
            }
            List<Entity> villageVillagers = TyC2Native.findVillagersInRadius(
                    level, bellPos, TyC2Constants.VILLAGE_SCAN_RADIUS
            );
            if (villageVillagers.size() < MayorElectionRules.MIN_VILLAGERS_FOR_MAYOR) {
                continue;
            }
            BiomeStyle style = BiomeStyle.fromBiomeId(TyC2Native.biomeId(level, bellPos));
            VillageRecord discovered = new VillageRecord(
                    UUID.randomUUID(),
                    dimId,
                    generateVillageName(bellPos),
                    new BlockPos3i(bellPos.getX(), bellPos.getY(), bellPos.getZ()),
                    style
            );
            data.putVillage(discovered);
            evaluateVillageMayorAndPop(level, data, discovered, bellPos, gameTime);
        }
    }

    private static BlockPos findNearbyBell(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int minY = Math.max(TyC2Native.minBuildY(level), center.getY() - 16);
        int maxY = Math.min(TyC2Native.maxBuildY(level) - 1, center.getY() + 20);
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;
        long maxDistSq = (long) radius * (long) radius;

        // 1. Fast O(BE) scan over chunk BlockEntities (vanilla Bells always hold a BellBlockEntity)
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (var entry : level.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    BlockPos bp = entry.getKey();
                    if (bp.getY() < minY || bp.getY() > maxY) {
                        continue;
                    }
                    if (!entry.getValue().getBlockState().is(Blocks.BELL)) {
                        continue;
                    }
                    double d = bp.distSqr(center);
                    if (d <= maxDistSq && d < bestDist) {
                        bestDist = d;
                        best = bp.immutable();
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }

        // 2. Fallback exact 1-block step scan (covers both odd and even coordinates)
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((long) dx * dx + (long) dz * dz > maxDistSq) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState st = level.getBlockState(cursor);
                    if (st.is(Blocks.BELL)) {
                        double d = cursor.distSqr(center);
                        if (d < bestDist) {
                            bestDist = d;
                            best = cursor.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void evaluateVillageMayorAndPop(
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord v,
            BlockPos hubPos,
            long gameTime
    ) {
        List<Entity> villagers = TyC2Native.findVillagersInRadius(level, hubPos, TyC2Constants.VILLAGE_SCAN_RADIUS);
        int count = villagers.size();
        v.setPopulation(count);

        List<MayorElectionRules.MayorCandidate> candidates = new ArrayList<>(villagers.size());
        Map<UUID, Entity> entityById = new LinkedHashMap<>();
        for (Entity e : villagers) {
            entityById.put(e.getUUID(), e);
            candidates.add(new MayorElectionRules.MayorCandidate(
                    e.getUUID(),
                    v.getVillageName(),
                    TyC2Native.isAdultVillager(e),
                    TyC2Native.isNitwitVillager(e),
                    e.isAlive(),
                    TyC2Native.villagerXp(e),
                    e.tickCount
            ));
        }

        boolean wasPaused = v.getMayorState().isPausedDueToDepopulation();
        MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.evaluate(
                v.getMayorState(), count, candidates, gameTime
        );
        if (outcome.mayorUuid() != null) {
            Entity mayor = entityById.get(outcome.mayorUuid());
            if (mayor != null) {
                applyMayorVisuals(mayor, v);
            }
        }
        if (outcome.action() == MayorElectionRules.Action.ELECTED_NEW_MAYOR) {
            notifyVillageOwnerOrNearby(level, v, Component.translatable("mayor.tyc2.elected", v.getVillageName())
                    .withStyle(ChatFormatting.GOLD));
            data.setDirty();
        } else if (outcome.action() == MayorElectionRules.Action.RESUMED_AND_ELECTED) {
            notifyVillageOwnerOrNearby(level, v, Component.translatable("mayor.tyc2.resumed", v.getVillageName())
                    .withStyle(ChatFormatting.GREEN));
            data.setDirty();
        } else if (outcome.action() == MayorElectionRules.Action.PAUSED_DEPOPULATED && !wasPaused) {
            notifyVillageOwnerOrNearby(level, v, Component.translatable("mayor.tyc2.paused_depopulated", v.getVillageName())
                    .withStyle(ChatFormatting.RED));
            data.setDirty();
        }
    }

    private static void applyMayorVisuals(Entity villager, VillageRecord v) {
        villager.getPersistentData().putBoolean(TyC2Constants.TAG_MAYOR, true);
        villager.getPersistentData().putString(TyC2Constants.TAG_MAYOR_VILLAGE_ID, v.getVillageId().toString());
        villager.setCustomName(
                Component.translatable("mayor.tyc2.name_format", v.getVillageName()).withStyle(ChatFormatting.GOLD)
        );
        villager.setCustomNameVisible(true);
        villager.setGlowingTag(true);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Entity target = event.getTarget();
        if (!TyC2Native.isVillager(target)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        TycoonSavedData data = getData(serverLevel);
        boolean taggedMayor = TyC2Nbt.getBoolean(target.getPersistentData(), TyC2Constants.TAG_MAYOR, false);
        VillageRecord village = null;
        if (taggedMayor) {
            String vidStr = TyC2Nbt.getString(target.getPersistentData(), TyC2Constants.TAG_MAYOR_VILLAGE_ID, "");
            if (!vidStr.isBlank()) {
                try {
                    village = data.getVillage(UUID.fromString(vidStr)).orElse(null);
                } catch (IllegalArgumentException ignored) {
                    // Fall through
                }
            }
        }
        if (village == null) {
            village = findNearestVillage(serverLevel, target.blockPosition(), TyC2Constants.VILLAGE_SCAN_RADIUS + 8)
                    .orElse(null);
        }
        if (village == null) {
            return;
        }

        boolean isMayor = taggedMayor || target.getUUID().equals(village.getMayorState().getMayorEntityUuid());

        if (village.isPartnered()) {
            // Cancel vanilla emerald trading inside ALL partnered villages!
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (isMayor) {
                player.sendSystemMessage(Component.translatable(
                        "mayor.tyc2.partnered_status",
                        village.getVillageName(),
                        village.getLevel(),
                        village.getVillageBalance(),
                        village.getActiveOrders().size()
                ).withStyle(ChatFormatting.GOLD));
            } else {
                TyC2Native.sendActionBar(
                        player,
                        Component.translatable("mayor.tyc2.villager_redirect").withStyle(ChatFormatting.YELLOW)
                );
            }
            return;
        }

        if (isMayor) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            UUID villageId = village.getVillageId();
            BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
            player.openMenu(
                    new SimpleMenuProvider(
                            (syncId, inv, p) -> new MayorNegotiationMenu(syncId, inv, villageId, hubPos),
                            Component.translatable("container.tyc2.mayor_negotiation")
                    ),
                    buf -> {
                        buf.writeUUID(villageId);
                        buf.writeBlockPos(hubPos);
                    }
            );
            sendMayorSync(player, serverLevel, village);
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!TyC2Native.isVillager(entity)) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        TycoonSavedData data = getData(level);
        VillageRecord village = null;
        String vidStr = TyC2Nbt.getString(entity.getPersistentData(), TyC2Constants.TAG_MAYOR_VILLAGE_ID, "");
        if (!vidStr.isBlank()) {
            try {
                village = data.getVillage(UUID.fromString(vidStr)).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // Fall through
            }
        }
        if (village == null) {
            for (VillageRecord v : data.allVillages()) {
                if (entity.getUUID().equals(v.getMayorState().getMayorEntityUuid())) {
                    village = v;
                    break;
                }
            }
        }
        if (village == null) {
            return;
        }

        BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
        List<Entity> others = new ArrayList<>();
        List<MayorElectionRules.MayorCandidate> candidates = new ArrayList<>();
        Map<UUID, Entity> byId = new LinkedHashMap<>();
        for (Entity e : TyC2Native.findVillagersInRadius(level, hubPos, TyC2Constants.VILLAGE_SCAN_RADIUS)) {
            if (e.getUUID().equals(entity.getUUID())) {
                continue;
            }
            others.add(e);
            byId.put(e.getUUID(), e);
            candidates.add(new MayorElectionRules.MayorCandidate(
                    e.getUUID(),
                    village.getVillageName(),
                    TyC2Native.isAdultVillager(e),
                    TyC2Native.isNitwitVillager(e),
                    e.isAlive(),
                    TyC2Native.villagerXp(e),
                    e.tickCount
            ));
        }

        notifyVillageOwnerOrNearby(
                level,
                village,
                Component.translatable("mayor.tyc2.died", village.getVillageName()).withStyle(ChatFormatting.RED)
        );
        MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.onMayorDeathAndReelect(
                village.getMayorState(),
                entity.getUUID(),
                others.size(),
                candidates,
                level.getGameTime()
        );
        village.setPopulation(others.size());
        if (outcome.mayorUuid() != null) {
            Entity newMayor = byId.get(outcome.mayorUuid());
            if (newMayor != null) {
                applyMayorVisuals(newMayor, village);
                notifyVillageOwnerOrNearby(
                        level,
                        village,
                        Component.translatable("mayor.tyc2.elected", village.getVillageName())
                                .withStyle(ChatFormatting.GOLD)
                );
            }
        }
        data.setDirty();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 100 != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        TycoonSavedData data = getData(level);
        boolean ic2 = Ic2SoftBridge.isIc2Loaded();
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) {
                if (data.industrialTiers().recordItemMilestone(player.getUUID(), TyC2Native.itemId(s), ic2)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    // =========================================================================
    // Demand Generation & Expansion Launch Helpers
    // =========================================================================

    /** Regenerates fresh Sell & Buy orders for {@code v} using DemandGenerator + Season + CPI + Tier. */
    public static void refreshVillageOrders(
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord v,
            long gameTime
    ) {
        Season season = SeasonSoftBridge.currentSeason(level);
        IndustrialTier tier = resolveVillageIndustrialTier(data, v);
        DemandGenerator.GeneratedDemand demand = TyC2DataLoader.demandGenerator().generate(
                v.getLevel(),
                v.getBiomeStyle().id(),
                v.getVillageBalance(),
                tier,
                season,
                data.economy(),
                TyC2Native::itemExists,
                RNG
        );

        long expires = gameTime + TyC2DataLoader.config().demandRefreshTicks();
        List<VillageTradeOrder> fresh = new ArrayList<>();
        for (DemandGenerator.GeneratedOrder go : demand.allOrders()) {
            OrderSide side = go.side() == DemandGenerator.OrderSide.VILLAGE_SELLS
                    ? OrderSide.VILLAGE_SELLS : OrderSide.VILLAGE_BUYS;
            fresh.add(new VillageTradeOrder(
                    go.orderId() + "_" + gameTime,
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
        v.setActiveOrders(fresh);
        v.setLastDemandRefreshTick(gameTime);
        data.setDirty();
    }

    /** Attempts to launch a collision-safe expansion build immediately from {@code v}'s active desire. */
    public static boolean tryLaunchExpansionFromDesire(
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord v,
            long gameTime,
            ServerPlayer feedbackPlayer
    ) {
        ExpansionDesire desire = v.getActiveExpansion();
        if (desire == null || !desire.allMaterialsDelivered()) {
            return false;
        }
        ServerLevel targetLevel = resolveDimensionLevel(level.getServer(), v.getDimensionId(), level);
        BlockPos hubPos = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
        if (!targetLevel.hasChunkAt(hubPos)) {
            return false;
        }
        if (v.getVillageBalance() < desire.constructionBudgetCost()) {
            if (feedbackPlayer != null) {
                TyC2Native.sendActionBar(
                        feedbackPlayer,
                        Component.translatable("status.tyc2.hub.village_low_funds").withStyle(ChatFormatting.RED)
                );
            }
            return false;
        }

        LevelTerrainProbe probe = new LevelTerrainProbe(targetLevel, data);
        ExpansionPlanner.PlanResult plan = PLANNER.plan(
                v, desire.projectType(), probe, "job_" + gameTime + "_" + v.getOccupiedZones().size()
        );
        if (!plan.success()) {
            if (feedbackPlayer != null) {
                TyC2Native.sendActionBar(
                        feedbackPlayer,
                        Component.translatable("status.tyc2.expansion.no_site").withStyle(ChatFormatting.RED)
                );
            }
            return false;
        }

        v.adjustVillageBalance(-desire.constructionBudgetCost());
        data.economy().recordVillageMoneySink(desire.constructionBudgetCost());
        PLANNER.commitPlan(v, plan, gameTime);
        v.setActiveExpansion(null);
        data.setDirty();

        Component projectTitle = Component.translatable(
                "project.tyc2." + desire.projectType().name().toLowerCase(Locale.ROOT)
        );
        notifyVillageOwnerOrNearby(
                targetLevel,
                v,
                Component.translatable("status.tyc2.expansion.started", v.getVillageName(), projectTitle)
                        .withStyle(ChatFormatting.AQUA)
        );
        return true;
    }

    /** Forces a collision-safe expansion build immediately (used by {@code /tyc2 expand}). */
    public static ExpansionPlanner.PlanResult forcePlanAndBuild(
            ServerLevel level,
            VillageRecord v,
            ProjectType explicitType
    ) {
        TycoonSavedData data = getData(level);
        ProjectType type = explicitType != null
                ? explicitType
                : PLANNER.selectNextProjectType(v, isWaterNearVillage(level, v));
        LevelTerrainProbe probe = new LevelTerrainProbe(level, data);
        ExpansionPlanner.PlanResult res = PLANNER.plan(
                v, type, probe, "cmd_job_" + level.getGameTime() + "_" + v.getOccupiedZones().size()
        );
        if (res.success()) {
            PLANNER.commitPlan(v, res, level.getGameTime());
            data.setDirty();
        }
        return res;
    }

    // =========================================================================
    // C2S Action Routing (ATM, HUB, TERMINAL, MAYOR)
    // =========================================================================

    public static void handleActionPayload(ServerPlayer player, ServerLevel level, TyC2ActionPayload payload) {
        Map<String, Object> args = Map.of();
        if (payload.payloadJson() != null && !payload.payloadJson().isBlank()) {
            try {
                args = JsonLite.parseObject(payload.payloadJson());
            } catch (Exception ignored) {
                args = Map.of();
            }
        }
        switch (payload.screenType()) {
            case "ATM" -> handleAtmAction(player, level, payload.blockPos(), payload.action(), args);
            case "HUB" -> handleHubAction(player, level, payload.blockPos(), payload.action(), args);
            case "TERMINAL" -> handleTerminalAction(player, level, payload.action(), args);
            case "MAYOR" -> handleMayorAction(player, level, payload.action(), args);
            default -> {
            }
        }
    }

    private static void handleAtmAction(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            String action,
            Map<String, Object> args
    ) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AtmMachineBlockEntity atm)) {
            return;
        }
        if (!atm.stillValid(player)) {
            return;
        }
        TycoonSavedData data = getData(level);
        BankAccountManager bank = data.bank();
        bank.getOrCreatePersonal(player.getUUID(), player.getName().getString());
        long now = level.getGameTime();
        String statusMsg = "";

        switch (action) {
            case "DEPOSIT_SLOT" -> {
                ItemStack stack = atm.getItem(AtmMachineBlockEntity.SLOT_DEPOSIT);
                Optional<CurrencyDenomination> denom = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(stack));
                if (denom.isPresent() && !stack.isEmpty()) {
                    long value = denom.get().value() * stack.getCount();
                    BankAccountManager.BankResult res = bank.deposit(player.getUUID(), value, now, "ATM Deposit");
                    if (res.success()) {
                        atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, ItemStack.EMPTY);
                        data.economy().recordCashDepositedToBank(value);
                        data.setDirty();
                        statusMsg = Component.translatable("status.tyc2.atm.deposited", value, activeAccountLabel(bank, player.getUUID())).getString();
                    }
                }
            }
            case "DEPOSIT_ALL" -> {
                long total = 0L;
                ItemStack slotStack = atm.getItem(AtmMachineBlockEntity.SLOT_DEPOSIT);
                Optional<CurrencyDenomination> slotDenom = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(slotStack));
                if (slotDenom.isPresent() && !slotStack.isEmpty()) {
                    total += slotDenom.get().value() * slotStack.getCount();
                    atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, ItemStack.EMPTY);
                }
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack invStack = player.getInventory().getItem(i);
                    Optional<CurrencyDenomination> d = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(invStack));
                    if (d.isPresent() && !invStack.isEmpty()) {
                        total += d.get().value() * invStack.getCount();
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                if (total > 0L) {
                    bank.deposit(player.getUUID(), total, now, "ATM Deposit All");
                    data.economy().recordCashDepositedToBank(total);
                    data.setDirty();
                    statusMsg = Component.translatable("status.tyc2.atm.deposited", total, activeAccountLabel(bank, player.getUUID())).getString();
                }
            }
            case "WITHDRAW_AMOUNT" -> {
                long amount = JsonLite.getLong(args, "amount", 0L);
                if (amount <= 0L) {
                    statusMsg = Component.translatable("status.tyc2.atm.invalid_amount").getString();
                } else {
                    BankAccountManager.BankResult res = bank.withdraw(player.getUUID(), amount, now, "ATM Withdraw");
                    if (res.success()) {
                        dispenseCoins(player, atm, CurrencyDenomination.decompose(amount));
                        data.economy().recordCashWithdrawnFromBank(amount);
                        data.setDirty();
                        statusMsg = Component.translatable("status.tyc2.atm.withdrawn", amount).getString();
                    } else {
                        statusMsg = Component.translatable(res.errorKey()).getString();
                    }
                }
            }
            case "WITHDRAW_DENOM" -> {
                String denomSpec = JsonLite.getString(args, "denom", "X1");
                int count = Math.max(1, Math.min(64, JsonLite.getInt(args, "count", 1)));
                Optional<CurrencyDenomination> denom = CurrencyDenomination.parse(denomSpec);
                if (denom.isPresent()) {
                    long total = denom.get().value() * count;
                    BankAccountManager.BankResult res = bank.withdrawDenominated(
                            player.getUUID(), denom.get(), count, now, "ATM Withdraw " + denom.get().value() + "x"
                    );
                    if (res.success()) {
                        dispenseCoins(player, atm, Map.of(denom.get(), count));
                        data.economy().recordCashWithdrawnFromBank(total);
                        data.setDirty();
                        statusMsg = Component.translatable("status.tyc2.atm.withdrawn", total).getString();
                    } else {
                        statusMsg = Component.translatable(res.errorKey()).getString();
                    }
                }
            }
            case "CREATE_COMPANY" -> {
                String name = JsonLite.getString(args, "name", "").trim();
                if (!name.isEmpty()) {
                    BankAccountManager.CompanyAccount co = bank.createCompany(
                            player.getUUID(), player.getName().getString(), name
                    );
                    bank.setActiveCompany(player.getUUID(), co.companyId());
                    data.setDirty();
                    statusMsg = Component.translatable("status.tyc2.atm.company_created", co.name(), co.inviteCode()).getString();
                }
            }
            case "JOIN_COMPANY" -> {
                String code = JsonLite.getString(args, "code", "").trim();
                BankAccountManager.BankResult res = bank.joinCompanyByInviteCode(
                        player.getUUID(), player.getName().getString(), code
                );
                if (res.success() && res.accountRef() != null) {
                    bank.setActiveCompany(player.getUUID(), res.accountRef().accountId());
                    data.setDirty();
                    String coName = bank.getCompany(res.accountRef().accountId())
                            .map(BankAccountManager.CompanyAccount::name).orElse(code);
                    statusMsg = Component.translatable("status.tyc2.atm.company_joined", coName).getString();
                } else if (res.errorKey() != null) {
                    statusMsg = Component.translatable(res.errorKey()).getString();
                }
            }
            case "LEAVE_COMPANY" -> {
                String coId = JsonLite.getString(args, "companyId", "");
                if (coId.isBlank()) {
                    coId = bank.getPersonal(player.getUUID())
                            .map(BankAccountManager.PersonalAccount::activeCompanyId).orElse("");
                }
                if (!coId.isBlank()) {
                    String coName = bank.getCompany(coId).map(BankAccountManager.CompanyAccount::name).orElse(coId);
                    BankAccountManager.BankResult res = bank.leaveCompany(player.getUUID(), coId, now);
                    if (res.success()) {
                        data.setDirty();
                        statusMsg = Component.translatable("status.tyc2.atm.company_left", coName).getString();
                    } else if (res.errorKey() != null) {
                        statusMsg = Component.translatable(res.errorKey()).getString();
                    }
                }
            }
            case "SWITCH_ACCOUNT" -> {
                String coId = JsonLite.getString(args, "companyId", "");
                BankAccountManager.BankResult res = bank.setActiveCompany(
                        player.getUUID(), coId.isBlank() ? null : coId
                );
                if (res.success()) {
                    data.setDirty();
                } else if (res.errorKey() != null) {
                    statusMsg = Component.translatable(res.errorKey()).getString();
                }
            }
            case "TRANSFER" -> {
                String recipient = JsonLite.getString(args, "recipient", "").trim();
                long amount = JsonLite.getLong(args, "amount", 0L);
                BankAccountManager.BankResult res = executeNamedTransfer(bank, player.getUUID(), recipient, amount, now);
                if (res.success()) {
                    data.setDirty();
                    statusMsg = Component.translatable("status.tyc2.atm.transferred", amount, recipient).getString();
                } else if (res.errorKey() != null) {
                    statusMsg = Component.translatable(res.errorKey()).getString();
                }
            }
            default -> {
            }
        }

        if (!statusMsg.isEmpty()) {
            TyC2Native.sendActionBar(player, Component.literal(statusMsg).withStyle(ChatFormatting.AQUA));
        }
        sendAtmSync(player, level, pos, atm);
    }

    private static BankAccountManager.BankResult executeNamedTransfer(
            BankAccountManager bank,
            UUID senderUuid,
            String recipient,
            long amount,
            long now
    ) {
        if (recipient == null || recipient.isBlank()) {
            return new BankAccountManager.BankResult(false, "status.tyc2.atm.invalid_recipient", null, 0L);
        }
        // Match company ID or company name first, then player display name
        if (bank.getCompany(recipient).isPresent()) {
            return bank.transferToCompany(senderUuid, recipient, amount, now, "ATM Transfer");
        }
        for (BankAccountManager.CompanyAccount co : bank.allCompanyAccounts().values()) {
            if (co.name().equalsIgnoreCase(recipient)) {
                return bank.transferToCompany(senderUuid, co.companyId(), amount, now, "ATM Transfer");
            }
        }
        Optional<BankAccountManager.PersonalAccount> targetPlayer = bank.findPersonalByName(recipient);
        if (targetPlayer.isPresent()) {
            return bank.transferToPlayer(senderUuid, targetPlayer.get().playerUuid(), amount, now, "ATM Transfer");
        }
        return new BankAccountManager.BankResult(false, "status.tyc2.atm.invalid_recipient", null, 0L);
    }

    private static void dispenseCoins(
            ServerPlayer player,
            AtmMachineBlockEntity atm,
            Map<CurrencyDenomination, Integer> coins
    ) {
        boolean ic2Loaded = Ic2SoftBridge.isIc2Loaded();
        for (Map.Entry<CurrencyDenomination, Integer> e : coins.entrySet()) {
            int remaining = e.getValue();
            String itemId = e.getKey().canonicalItemId(ic2Loaded);
            while (remaining > 0) {
                int batch = Math.min(64, remaining);
                ItemStack stack = TyC2Native.stack(itemId, batch);
                remaining -= batch;
                if (stack.isEmpty()) {
                    continue;
                }
                // Try ATM withdraw slot first, then player inventory, then drop at player feet
                ItemStack existing = atm.getItem(AtmMachineBlockEntity.SLOT_WITHDRAW);
                if (existing.isEmpty()) {
                    atm.setItem(AtmMachineBlockEntity.SLOT_WITHDRAW, stack);
                } else if (ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(stack.getCount());
                    atm.setChanged();
                } else if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    private static void handleHubAction(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            String action,
            Map<String, Object> args
    ) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof CooperationHubBlockEntity hub)) {
            return;
        }
        if (!hub.stillValid(player)) {
            return;
        }
        VillageRecord village = hub.resolveVillage(level);
        if (village == null) {
            return;
        }
        TycoonSavedData data = getData(level);
        boolean useCash = JsonLite.getBoolean(args, "cashMode", false);

        if ("WAREHOUSE_CLICK".equals(action)) {
            int slot = JsonLite.getInt(args, "slot", -1);
            boolean shift = JsonLite.getBoolean(args, "shift", false);
            if (slot >= 0 && slot < CooperationHubStorage.TOTAL_SLOTS) {
                handleWarehouseSlotClick(player, level, data, village, slot, shift);
            }
            sendHubSync(player, level, pos, hub, args);
            return;
        }
        if ("WAREHOUSE_DEPOSIT_INV".equals(action)) {
            depositPlayerMatchingToInbound(player, level, data, village);
            sendHubSync(player, level, pos, hub, args);
            return;
        }
        if ("RELOCATE_HUB".equals(action)) {
            if (!canManageVillage(player, data, village)) {
                TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.not_owner").withStyle(ChatFormatting.RED));
                return;
            }
            ItemStack packed = new ItemStack(TyC2Registries.COOPERATION_HUB_ITEM.get());
            CooperationHubBlockItem.setBoundVillage(packed, village.getVillageId(), village.getVillageName());
            level.removeBlock(pos, false);
            if (!player.getInventory().add(packed)) {
                player.drop(packed, false);
            }
            player.closeContainer();
            TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.relocated").withStyle(ChatFormatting.GREEN));
            return;
        }
        if ("TERMINATE_PARTNERSHIP".equals(action)) {
            if (!canManageVillage(player, data, village)) {
                TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.not_owner").withStyle(ChatFormatting.RED));
                return;
            }
            String vName = village.getVillageName();
            village.terminatePartnership();
            data.setDirty();
            level.setBlock(pos, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
            player.closeContainer();
            TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.partnership_terminated", vName).withStyle(ChatFormatting.YELLOW));
            return;
        }

        executeVillageAction(player, level, data, village, hub, action, args, useCash, false, ItemStack.EMPTY);
        sendHubSync(player, level, pos, hub, args);
    }

    private static void handleTerminalAction(
            ServerPlayer player,
            ServerLevel level,
            String action,
            Map<String, Object> args
    ) {
        ItemStack term = findHeldTerminal(player);
        if (term.isEmpty()) {
            return;
        }
        TycoonSavedData data = getData(level);

        if ("SELECT_VILLAGE".equals(action)) {
            String vid = JsonLite.getString(args, "villageId", "");
            if (!vid.isBlank()) {
                try {
                    CommTerminalItem.setSelectedVillage(term, UUID.fromString(vid));
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid UUID
                }
            }
            sendTerminalSync(player, level, term);
            return;
        }
        if ("UNBIND_VILLAGE".equals(action)) {
            String vid = JsonLite.getString(args, "villageId", "");
            if (!vid.isBlank()) {
                try {
                    CommTerminalItem.unbindVillage(term, UUID.fromString(vid));
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid UUID
                }
            }
            sendTerminalSync(player, level, term);
            return;
        }

        UUID selectedId = CommTerminalItem.getSelectedVillage(term);
        if (selectedId == null) {
            sendTerminalSync(player, level, term);
            return;
        }
        Optional<VillageRecord> villageOpt = data.getVillage(selectedId);
        if (villageOpt.isEmpty()) {
            sendTerminalSync(player, level, term);
            return;
        }

        executeVillageAction(player, level, data, villageOpt.get(), null, action, args, false, true, term);
        sendTerminalSync(player, level, term);
    }

    private static void executeVillageAction(
            ServerPlayer player,
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord village,
            CooperationHubBlockEntity hub,
            String action,
            Map<String, Object> args,
            boolean useCash,
            boolean isTerminal,
            ItemStack terminalStack
    ) {
        long now = level.getGameTime();
        BankAccountManager bank = data.bank();
        bank.getOrCreatePersonal(player.getUUID(), player.getName().getString());
        BankAccountManager.AccountRef activeRef = bank.resolveSettlementAccount(player.getUUID());

        switch (action) {
            case "BUY_ORDER" -> {
                // Player buys from Village Sell Order -> player pays IC, goods go to Outbound 500..999
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_TRADE)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                String orderId = JsonLite.getString(args, "orderId", "");
                int qty = Math.max(1, JsonLite.getInt(args, "qty", 1));
                Optional<VillageTradeOrder> orderOpt = village.findOrder(orderId);
                if (orderOpt.isEmpty() || orderOpt.get().side() != OrderSide.VILLAGE_SELLS || orderOpt.get().isCompleted()) {
                    return;
                }
                VillageTradeOrder order = orderOpt.get();
                int actualQty = Math.min(qty, order.remainingQty());
                long totalCost = order.totalPriceFor(actualQty);

                if (useCash && hub != null) {
                    if (!consumeCashPayment(player, hub, totalCost)) {
                        TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.atm.insufficient_balance").withStyle(ChatFormatting.RED));
                        return;
                    }
                } else {
                    if (bank.getBalance(activeRef) < totalCost) {
                        TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.atm.insufficient_balance").withStyle(ChatFormatting.RED));
                        return;
                    }
                }

                String nbtMeta = order.enchantSpec().isBlank() ? "" : ("enchant:" + order.enchantSpec());
                int maxStack = order.enchantSpec().isBlank() ? 64 : 1;
                VillageRecord.TradeExecutionResult res = village.executeSellOrderToOutbound(
                        orderId, actualQty, nbtMeta, maxStack
                );
                if (!res.success()) {
                    // Refund cash if we took physical cash
                    if (useCash && hub != null) {
                        giveChangeCoins(player, totalCost);
                    }
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.outbound_full").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!useCash || hub == null) {
                    bank.debitAccount(activeRef, res.totalCurrency(), BankAccountManager.TransactionType.TRADE_BUY, now, village.getVillageName(), order.itemId());
                }
                long basePrice = TyC2DataLoader.goodsCatalog().findByItemId(order.itemId()).map(g -> g.basePrice()).orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, false);
                data.setDirty();
                TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.order_fulfilled", res.totalCurrency()).withStyle(ChatFormatting.GREEN));
            }
            case "SELL_ORDER" -> {
                // Player delivers to Village Buy Order -> consumes from Inbound 0..499 (or player inv at Hub), pays player!
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_TRADE)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                String orderId = JsonLite.getString(args, "orderId", "");
                int qty = Math.max(1, JsonLite.getInt(args, "qty", 64));
                Optional<VillageTradeOrder> orderOpt = village.findOrder(orderId);
                if (orderOpt.isEmpty() || orderOpt.get().side() != OrderSide.VILLAGE_BUYS || orderOpt.get().isCompleted()) {
                    return;
                }
                VillageTradeOrder order = orderOpt.get();
                // At local Hub, automatically pull matching items from player inventory into Inbound if Inbound is short
                if (!isTerminal && village.getStorage().countInInbound(order.itemId()) < Math.min(qty, order.remainingQty())) {
                    int deficit = Math.min(qty, order.remainingQty()) - village.getStorage().countInInbound(order.itemId());
                    transferItemFromPlayerToInbound(player, level, village, order.itemId(), deficit);
                }
                VillageRecord.TradeExecutionResult res = village.executeBuyOrderFromInbound(orderId, qty);
                if (!res.success()) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.village_low_funds").withStyle(ChatFormatting.RED));
                    return;
                }
                if (useCash && hub != null) {
                    giveChangeCoins(player, res.totalCurrency());
                } else {
                    bank.creditAccount(activeRef, res.totalCurrency(), BankAccountManager.TransactionType.TRADE_SELL, now, village.getVillageName(), order.itemId());
                }
                long basePrice = TyC2DataLoader.goodsCatalog().findByItemId(order.itemId()).map(g -> g.basePrice()).orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
                data.setDirty();
                TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.order_fulfilled", res.totalCurrency()).withStyle(ChatFormatting.GREEN));
            }
            case "TOGGLE_ORDER_AUTO" -> {
                String orderId = JsonLite.getString(args, "orderId", "");
                village.findOrder(orderId).ifPresent(o -> {
                    village.updateOrder(o.withAutoFulfill(!o.autoFulfill()));
                    data.setDirty();
                });
            }
            case "DELIVER_EXPANSION" -> {
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                ExpansionDesire desire = village.getActiveExpansion();
                if (desire == null) {
                    return;
                }
                if (!isTerminal) {
                    for (Map.Entry<String, Integer> rem : desire.remainingMaterials().entrySet()) {
                        transferItemFromPlayerToInbound(player, level, village, rem.getKey(), rem.getValue());
                    }
                }
                ExpansionDesire updated = desire.pullAvailableFromInbound(village.getStorage());
                village.setActiveExpansion(updated);
                data.setDirty();
                if (updated.allMaterialsDelivered()) {
                    tryLaunchExpansionFromDesire(level, data, village, now, player);
                }
            }
            case "TOGGLE_EXPANSION_AUTO" -> {
                ExpansionDesire desire = village.getActiveExpansion();
                if (desire != null) {
                    village.setActiveExpansion(desire.withAutoFulfill(!desire.autoFulfill()));
                    data.setDirty();
                }
            }
            case "UPGRADE_VILLAGE" -> {
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                long sink = village.tryUpgrade(TyC2DataLoader.levelTable());
                if (sink > 0L) {
                    data.economy().recordVillageMoneySink(sink);
                    refreshVillageOrders(level, data, village, now);
                    data.setDirty();
                    Component lvlName = Component.translatable("level.tyc2." + village.getLevel());
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.upgraded", village.getVillageName(), village.getLevel(), lvlName).withStyle(ChatFormatting.GOLD));
                } else {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.upgrade_failed").withStyle(ChatFormatting.RED));
                }
            }
            case "TOGGLE_PAUSE_EXPANSION" -> {
                village.setExpansionPaused(!village.isExpansionPaused());
                data.setDirty();
            }
            case "RENAME_VILLAGE" -> {
                if (!canManageVillage(player, data, village)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.tyc2.hub.not_owner").withStyle(ChatFormatting.RED));
                    return;
                }
                String newName = JsonLite.getString(args, "name", "").trim();
                if (!newName.isBlank() && newName.length() <= 32) {
                    village.setVillageName(newName);
                    data.setDirty();
                }
            }
            default -> {
            }
        }
    }

    private static void handleMayorAction(
            ServerPlayer player,
            ServerLevel level,
            String action,
            Map<String, Object> args
    ) {
        String vidStr = JsonLite.getString(args, "villageId", "");
        if (vidStr.isBlank()) {
            return;
        }
        UUID villageId;
        try {
            villageId = UUID.fromString(vidStr);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        TycoonSavedData data = getData(level);
        Optional<VillageRecord> villageOpt = data.getVillage(villageId);
        if (villageOpt.isEmpty()) {
            return;
        }
        VillageRecord village = villageOpt.get();
        if ("REQUEST_SYNC".equals(action)) {
            sendMayorSync(player, level, village);
            return;
        }
        if (!"SIGN_PARTNERSHIP".equals(action) || village.isPartnered()) {
            return;
        }

        TyC2Config cfg = TyC2DataLoader.config();
        NegotiationCheck check = evaluateNegotiation(player, data, cfg);
        if (!check.allMet()) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.tyc2.negotiation.missing_requirements").withStyle(ChatFormatting.RED)
            );
            sendMayorSync(player, level, village);
            return;
        }

        // 1. Deduct startup currency (prefer bank account, else physical cash in inventory)
        long costIc = cfg.negotiationCostIc();
        if (costIc > 0L) {
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            if (data.bank().getBalance(ref) >= costIc) {
                data.bank().debitAccount(
                        ref, costIc, BankAccountManager.TransactionType.NEGOTIATION,
                        level.getGameTime(), village.getVillageName(), "Village Partnership"
                );
            } else {
                consumeCoinsFromInventory(player, costIc);
            }
        }

        // 2. Consume startup supplies (logs, iron ingots, basic food)
        consumeTagOrPredicateFromPlayer(player, cfg.negotiationRequiredLogs(), s -> TyC2Native.itemHasTag(s, "minecraft:logs"));
        consumeTagOrPredicateFromPlayer(player, cfg.negotiationRequiredIron(), TyC2WorldManager::isIronIngot);
        consumeTagOrPredicateFromPlayer(player, cfg.negotiationRequiredFood(), TyC2WorldManager::isBasicFood);

        // 3. Establish partnership & deploy blast-proof Cooperation Hub at Bell location!
        String activeCo = data.bank().getPersonal(player.getUUID())
                .map(BankAccountManager.PersonalAccount::activeCompanyId)
                .orElse(null);
        village.establishPartnership(player.getUUID(), activeCo, costIc);

        BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
        level.setBlock(hubPos, TyC2Registries.COOPERATION_HUB_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity be) {
            be.setVillageId(village.getVillageId());
        }

        // Register initial Hub core zone so expansion stays cleanly outside existing buildings
        village.addOccupiedZone(Aabb3i.singleBlock(village.getHubPos()).padHorizontal(4));
        refreshVillageOrders(level, data, village, level.getGameTime());
        data.setDirty();

        player.closeContainer();
        player.sendSystemMessage(
                Component.translatable("status.tyc2.hub.partnership_signed", village.getVillageName())
                        .withStyle(ChatFormatting.GREEN)
        );
    }

    // =========================================================================
    // Warehouse Direct Slot Click & Helpers
    // =========================================================================

    private static void handleWarehouseSlotClick(
            ServerPlayer player,
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord village,
            int slot,
            boolean shift
    ) {
        CooperationHubStorage storage = village.getStorage();
        ItemStack carried = player.containerMenu.getCarried();
        var registries = level.registryAccess();

        if (!carried.isEmpty() && CooperationHubStorage.Zone.INBOUND.containsSlot(slot)) {
            SlotStack ss = TyC2Native.toSlotStack(carried, registries);
            if (ss != null) {
                int remainder = storage.playerInsertIntoInbound(
                        slot, ss.itemId(), ss.count(), ss.nbtJson(), carried.getMaxStackSize()
                );
                if (remainder <= 0) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                } else {
                    carried.setCount(remainder);
                }
                data.setDirty();
            }
            return;
        }

        Optional<SlotStack> existing = storage.getSlot(slot);
        if (existing.isEmpty()) {
            return;
        }
        int takeCount = shift ? existing.get().count() : Math.min(existing.get().count(), 64);
        Optional<SlotStack> taken = storage.playerExtract(slot, takeCount);
        if (taken.isPresent()) {
            ItemStack out = TyC2Native.fromSlotStack(taken.get(), registries);
            if (shift || !carried.isEmpty()) {
                if (!player.getInventory().add(out)) {
                    // Put back if player inventory is completely full
                    storage.setSlotRaw(slot, taken.get());
                    return;
                }
            } else {
                player.containerMenu.setCarried(out);
            }
            data.setDirty();
        }
    }

    private static void depositPlayerMatchingToInbound(
            ServerPlayer player,
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord village
    ) {
        var registries = level.registryAccess();
        CooperationHubStorage storage = village.getStorage();
        boolean movedAny = false;
        // Deposit items that match active buy orders or active expansion materials
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            String id = TyC2Native.itemId(s);
            boolean needed = village.getActiveOrders().stream()
                    .anyMatch(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted() && o.itemId().equals(id));
            if (!needed && village.getActiveExpansion() != null) {
                needed = village.getActiveExpansion().remainingFor(id) > 0;
            }
            if (!needed) {
                continue;
            }
            SlotStack ss = TyC2Native.toSlotStack(s, registries);
            if (ss != null) {
                int rem = storage.insertExternal(ss.itemId(), ss.count(), ss.nbtJson(), s.getMaxStackSize(), false);
                if (rem < s.getCount()) {
                    if (rem <= 0) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    } else {
                        s.setCount(rem);
                    }
                    movedAny = true;
                }
            }
        }
        if (movedAny) {
            data.setDirty();
        }
    }

    private static void transferItemFromPlayerToInbound(
            ServerPlayer player,
            ServerLevel level,
            VillageRecord village,
            String targetItemId,
            int maxCount
    ) {
        int remaining = maxCount;
        var registries = level.registryAccess();
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty() || !targetItemId.equals(TyC2Native.itemId(s))) {
                continue;
            }
            int take = Math.min(remaining, s.getCount());
            SlotStack ss = TyC2Native.toSlotStack(s.copyWithCount(take), registries);
            if (ss != null) {
                int notInserted = village.getStorage().insertExternal(
                        ss.itemId(), ss.count(), ss.nbtJson(), s.getMaxStackSize(), false
                );
                int moved = take - notInserted;
                if (moved > 0) {
                    s.shrink(moved);
                    if (s.isEmpty()) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                    remaining -= moved;
                }
            }
        }
    }

    // =========================================================================
    // S2C Sync Payload Builders
    // =========================================================================

    public static void sendAtmSync(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            AtmMachineBlockEntity atm
    ) {
        TycoonSavedData data = getData(level);
        BankAccountManager bank = data.bank();
        BankAccountManager.PersonalAccount personal = bank.getOrCreatePersonal(
                player.getUUID(), player.getName().getString()
        );
        BankAccountManager.AccountRef activeRef = bank.resolveSettlementAccount(player.getUUID());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("personalBalance", personal.balance());
        root.put("activeScope", activeRef.scope().name());
        root.put("activeBalance", bank.getBalance(activeRef));
        root.put("activeLabel", activeAccountLabel(bank, player.getUUID()));
        root.put("activeCompanyId", personal.activeCompanyId() == null ? "" : personal.activeCompanyId());
        root.put("storedEu", (int) Math.floor(atm.storedEu()));
        root.put("maxEu", (int) atm.maxCapacityEu());

        List<Map<String, Object>> companies = new ArrayList<>();
        for (BankAccountManager.CompanyAccount co : bank.getCompaniesForPlayer(player.getUUID())) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", co.companyId());
            cm.put("name", co.name());
            cm.put("inviteCode", co.inviteCode());
            cm.put("balance", co.balance());
            cm.put("members", co.memberUuids().size());
            companies.add(cm);
        }
        root.put("companies", companies);

        List<Map<String, Object>> logs = new ArrayList<>();
        List<BankAccountManager.BankTransactionLog> rawLogs = bank.getTransactionLogs(activeRef);
        for (int i = rawLogs.size() - 1; i >= 0; i--) {
            logs.add(rawLogs.get(i).toDataMap());
        }
        root.put("logs", logs);

        TyC2Native.sendToPlayer(player, new TyC2SyncPayload("ATM", pos, JsonLite.toJson(root)));
    }

    public static void sendHubSync(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            CooperationHubBlockEntity hub
    ) {
        sendHubSync(player, level, pos, hub, Map.of());
    }

    public static void sendHubSync(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            CooperationHubBlockEntity hub,
            Map<String, Object> clientViewArgs
    ) {
        VillageRecord village = hub.resolveVillage(level);
        if (village == null) {
            return;
        }
        TycoonSavedData data = getData(level);
        String zoneStr = JsonLite.getString(clientViewArgs, "zone", "INBOUND");
        CooperationHubStorage.Zone zone = "OUTBOUND".equalsIgnoreCase(zoneStr)
                ? CooperationHubStorage.Zone.OUTBOUND : CooperationHubStorage.Zone.INBOUND;
        int page = Math.max(0, JsonLite.getInt(clientViewArgs, "page", 0));
        String search = JsonLite.getString(clientViewArgs, "search", "");

        Map<String, Object> root = buildVillageSnapshotMap(player, level, data, village, zone, page, search);
        root.put("chunkLoaderActive", hub.isChunkLoaderActive());
        root.put("canManage", canManageVillage(player, data, village));
        root.put("lc2Stations", Lc2SoftBridge.findNearbyLogisticsStations(level, pos, 64));

        TyC2Native.sendToPlayer(player, new TyC2SyncPayload("HUB", pos, JsonLite.toJson(root)));
    }

    public static void sendTerminalSync(ServerPlayer player, ServerLevel level, ItemStack terminalStack) {
        TycoonSavedData data = getData(level);
        Map<UUID, String> bound = CommTerminalItem.getBoundVillages(terminalStack);
        UUID selected = CommTerminalItem.getSelectedVillage(terminalStack);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("energyEu", CommTerminalItem.getEnergyEu(terminalStack));
        root.put("maxEu", TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        root.put("selectedId", selected == null ? "" : selected.toString());
        root.put("activeBalance", data.bank().getActiveBalance(player.getUUID()));
        root.put("activeAccount", activeAccountLabel(data.bank(), player.getUUID()));

        List<Map<String, Object>> villageSummaries = new ArrayList<>();
        for (Map.Entry<UUID, String> e : bound.entrySet()) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", e.getKey().toString());
            Optional<VillageRecord> recOpt = data.getVillage(e.getKey());
            if (recOpt.isPresent()) {
                VillageRecord rec = recOpt.get();
                vm.put("name", rec.getVillageName());
                vm.put("level", rec.getLevel());
                vm.put("partnered", rec.isPartnered());
                ServerLevel vLvl = resolveDimensionLevel(level.getServer(), rec.getDimensionId(), level);
                BlockPos hp = new BlockPos(rec.getHubPos().x(), rec.getHubPos().y(), rec.getHubPos().z());
                vm.put("loaded", vLvl.hasChunkAt(hp));
                if (!rec.getVillageName().equals(e.getValue())) {
                    CommTerminalItem.refreshBoundVillageName(terminalStack, e.getKey(), rec.getVillageName());
                }
            } else {
                vm.put("name", e.getValue().isBlank() ? e.getKey().toString().substring(0, 8) : e.getValue());
                vm.put("level", 1);
                vm.put("partnered", false);
                vm.put("loaded", false);
            }
            villageSummaries.add(vm);
        }
        root.put("boundVillages", villageSummaries);

        if (selected != null) {
            data.getVillage(selected).ifPresent(v -> {
                Map<String, Object> detail = buildVillageSnapshotMap(
                        player, level, data, v, CooperationHubStorage.Zone.INBOUND, 0, ""
                );
                ServerLevel vLvl = resolveDimensionLevel(level.getServer(), v.getDimensionId(), level);
                BlockPos hp = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
                detail.put("lc2Stations", vLvl.hasChunkAt(hp)
                        ? Lc2SoftBridge.findNearbyLogisticsStations(vLvl, hp, 64)
                        : List.of());
                detail.put("inboundSummary", new LinkedHashMap<>(v.getStorage().getItemSummary(CooperationHubStorage.Zone.INBOUND)));
                detail.put("outboundSummary", new LinkedHashMap<>(v.getStorage().getItemSummary(CooperationHubStorage.Zone.OUTBOUND)));
                root.put("selectedVillage", detail);
            });
        }

        TyC2Native.sendToPlayer(player, new TyC2SyncPayload("TERMINAL", BlockPos.ZERO, JsonLite.toJson(root)));
    }

    public static void sendMayorSync(ServerPlayer player, ServerLevel level, VillageRecord village) {
        TycoonSavedData data = getData(level);
        TyC2Config cfg = TyC2DataLoader.config();
        NegotiationCheck check = evaluateNegotiation(player, data, cfg);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("villageId", village.getVillageId().toString());
        root.put("villageName", village.getVillageName());
        root.put("biome", village.getBiomeStyle().id());
        root.put("population", village.getPopulation());
        root.put("reqIc", cfg.negotiationCostIc());
        root.put("haveIc", check.availableIc());
        root.put("icOk", check.icOk());
        root.put("reqLogs", cfg.negotiationRequiredLogs());
        root.put("haveLogs", check.logsCount());
        root.put("logsOk", check.logsOk());
        root.put("reqIron", cfg.negotiationRequiredIron());
        root.put("haveIron", check.ironCount());
        root.put("ironOk", check.ironOk());
        root.put("reqFood", cfg.negotiationRequiredFood());
        root.put("haveFood", check.foodCount());
        root.put("foodOk", check.foodOk());
        root.put("canSign", check.allMet());

        BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
        TyC2Native.sendToPlayer(player, new TyC2SyncPayload("MAYOR", hubPos, JsonLite.toJson(root)));
    }

    private static Map<String, Object> buildVillageSnapshotMap(
            ServerPlayer player,
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord v,
            CooperationHubStorage.Zone zone,
            int page,
            String search
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        MacroEconomyEngine econ = data.economy();
        VillageLevelTable levels = TyC2DataLoader.levelTable();
        VillageLevelTable.UpgradeStatus upStatus = v.evaluateUpgrade(levels);
        Season season = SeasonSoftBridge.currentSeason(level);
        IndustrialTier tier = resolveVillageIndustrialTier(data, v);

        root.put("villageId", v.getVillageId().toString());
        root.put("villageName", v.getVillageName());
        root.put("biome", v.getBiomeStyle().id());
        root.put("level", v.getLevel());
        root.put("population", v.getPopulation());
        root.put("tradeVolume", v.getCumulativeTradeVolume());
        root.put("villageBalance", v.getVillageBalance());
        root.put("partnered", v.isPartnered());
        root.put("depopPaused", v.getMayorState().isPausedDueToDepopulation());
        root.put("expansionPaused", v.isExpansionPaused());
        root.put("minRadius", v.getMinExpansionRadius());
        root.put("maxRadius", v.getMaxExpansionRadius());
        root.put("playerBankBalance", data.bank().getActiveBalance(player.getUUID()));
        root.put("playerAccountLabel", activeAccountLabel(data.bank(), player.getUUID()));

        // Macro Economy & 48-point CPI history
        root.put("cpi", econ.computeCpi());
        root.put("inflationState", econ.getInflationState().name());
        root.put("moneySupply", econ.totalMoneySupply());
        root.put("goodsCirculation", Math.round(econ.rollingGoodsCirculation()));
        root.put("season", season.name());
        root.put("industrialTier", tier.level());
        root.put("villageLiquidity", econ.computeVillageLiquidityFactor(v.getLevel(), v.getVillageBalance(), false));

        List<Double> cpiHistory = new ArrayList<>();
        for (MacroEconomyEngine.PriceSnapshot snap : econ.history()) {
            cpiHistory.add(snap.cpiIndex());
        }
        root.put("cpiHistory", cpiHistory);

        // Upgrade requirements
        root.put("canUpgrade", upStatus.eligible());
        if (upStatus.targetSpec() != null) {
            root.put("nextMinPop", upStatus.targetSpec().minPopulation());
            root.put("nextMinVol", upStatus.targetSpec().minTradeVolume());
            root.put("nextMinBal", upStatus.targetSpec().minVillageBalance());
            root.put("nextUpgradeCost", upStatus.targetSpec().upgradeCostSink());
        }

        // Active Orders
        List<Map<String, Object>> orders = new ArrayList<>();
        for (VillageTradeOrder o : v.getActiveOrders()) {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.orderId());
            om.put("side", o.side().name());
            om.put("itemId", o.itemId());
            om.put("enchantSpec", o.enchantSpec());
            om.put("totalQty", o.totalQty());
            om.put("fulfilledQty", o.fulfilledQty());
            om.put("unitPrice", o.unitPrice());
            om.put("autoFulfill", o.autoFulfill());
            om.put("seasonalBonus", o.seasonalBonus());
            orders.add(om);
        }
        root.put("orders", orders);

        // Warehouse pagination
        CooperationHubStorage storage = v.getStorage();
        root.put("inboundUsed", storage.getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
        root.put("outboundUsed", storage.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));
        CooperationHubStorage.PageResult pageRes = storage.getPage(zone, page, 36, search);
        root.put("whZone", zone.name());
        root.put("whPage", pageRes.pageIndex());
        root.put("whTotalPages", pageRes.totalPages());
        List<Map<String, Object>> whSlots = new ArrayList<>();
        for (int absSlot : pageRes.slotIndices()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("slot", absSlot);
            Optional<SlotStack> st = storage.getSlot(absSlot);
            if (st.isPresent()) {
                sm.put("itemId", st.get().itemId());
                sm.put("count", st.get().count());
            } else {
                sm.put("itemId", "");
                sm.put("count", 0);
            }
            whSlots.add(sm);
        }
        root.put("whSlots", whSlots);

        // Expansion Desire & Active Construction
        if (v.getActiveExpansion() != null) {
            ExpansionDesire ed = v.getActiveExpansion();
            Map<String, Object> edm = new LinkedHashMap<>();
            edm.put("projectType", ed.projectType().name());
            edm.put("budget", ed.constructionBudgetCost());
            edm.put("autoFulfill", ed.autoFulfill());
            edm.put("required", new LinkedHashMap<>(ed.requiredMaterials()));
            edm.put("delivered", new LinkedHashMap<>(ed.deliveredMaterials()));
            root.put("expansionDesire", edm);
        }
        v.getActiveConstructionJob().ifPresent(job -> {
            Map<String, Object> jm = new LinkedHashMap<>();
            jm.put("projectType", job.getProjectType().name());
            jm.put("progressPct", (int) Math.round(job.getProgressRatio() * 100.0));
            root.put("activeBuild", jm);
        });

        return root;
    }

    // =========================================================================
    // Negotiation, Payment & Ownership Utilities
    // =========================================================================

    private record NegotiationCheck(
            long availableIc, boolean icOk,
            int logsCount, boolean logsOk,
            int ironCount, boolean ironOk,
            int foodCount, boolean foodOk
    ) {
        boolean allMet() {
            return icOk && logsOk && ironOk && foodOk;
        }
    }

    private static NegotiationCheck evaluateNegotiation(ServerPlayer player, TycoonSavedData data, TyC2Config cfg) {
        long bankBal = data.bank().getActiveBalance(player.getUUID());
        long invCash = countCoinsInInventory(player);
        long availIc = Math.max(bankBal, invCash);
        int logs = countMatchingInInventory(player, s -> TyC2Native.itemHasTag(s, "minecraft:logs"));
        int iron = countMatchingInInventory(player, TyC2WorldManager::isIronIngot);
        int food = countMatchingInInventory(player, TyC2WorldManager::isBasicFood);
        return new NegotiationCheck(
                availIc, availIc >= cfg.negotiationCostIc(),
                logs, logs >= cfg.negotiationRequiredLogs(),
                iron, iron >= cfg.negotiationRequiredIron(),
                food, food >= cfg.negotiationRequiredFood()
        );
    }

    private static boolean isIronIngot(ItemStack stack) {
        String id = TyC2Native.itemId(stack);
        return "minecraft:iron_ingot".equals(id)
                || TyC2Native.itemHasTag(stack, "c:ingots/iron")
                || TyC2Native.itemHasTag(stack, "forge:ingots/iron");
    }

    private static boolean isBasicFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }
        String id = TyC2Native.itemId(stack);
        return "minecraft:bread".equals(id)
                || "minecraft:baked_potato".equals(id)
                || "minecraft:cooked_beef".equals(id)
                || "minecraft:cooked_porkchop".equals(id)
                || "minecraft:cooked_chicken".equals(id)
                || "minecraft:cooked_mutton".equals(id)
                || "minecraft:carrot".equals(id)
                || "minecraft:apple".equals(id);
    }

    private static long countCoinsInInventory(Player player) {
        long sum = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            Optional<CurrencyDenomination> d = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(s));
            if (d.isPresent()) {
                sum += d.get().value() * s.getCount();
            }
        }
        return sum;
    }

    private static boolean consumeCashPayment(ServerPlayer player, CooperationHubBlockEntity hub, long requiredIc) {
        ItemStack cashSlot = hub.localContainer().getItem(CooperationHubBlockEntity.LOCAL_SLOT_CASH);
        long slotVal = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(cashSlot))
                .map(d -> d.value() * cashSlot.getCount()).orElse(0L);
        long invVal = countCoinsInInventory(player);
        if (slotVal + invVal < requiredIc) {
            return false;
        }
        long collected = 0L;
        if (slotVal > 0L) {
            CurrencyDenomination d = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(cashSlot)).orElseThrow();
            int needed = (int) Math.min(cashSlot.getCount(), (requiredIc - collected + d.value() - 1) / d.value());
            cashSlot.shrink(needed);
            if (cashSlot.isEmpty()) {
                hub.localContainer().setItem(CooperationHubBlockEntity.LOCAL_SLOT_CASH, ItemStack.EMPTY);
            }
            collected += needed * d.value();
        }
        if (collected < requiredIc) {
            collected += consumeCoinsFromInventory(player, requiredIc - collected);
        }
        if (collected > requiredIc) {
            giveChangeCoins(player, collected - requiredIc);
        }
        return true;
    }

    private static long consumeCoinsFromInventory(Player player, long targetIc) {
        long collected = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize() && collected < targetIc; i++) {
            ItemStack s = player.getInventory().getItem(i);
            Optional<CurrencyDenomination> dOpt = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(s));
            if (dOpt.isEmpty() || s.isEmpty()) {
                continue;
            }
            CurrencyDenomination d = dOpt.get();
            int take = (int) Math.min(s.getCount(), (targetIc - collected + d.value() - 1) / d.value());
            s.shrink(take);
            if (s.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
            collected += take * d.value();
        }
        return collected;
    }

    private static void giveChangeCoins(ServerPlayer player, long amountIc) {
        if (amountIc <= 0L) {
            return;
        }
        boolean ic2Loaded = Ic2SoftBridge.isIc2Loaded();
        for (Map.Entry<CurrencyDenomination, Integer> e : CurrencyDenomination.decompose(amountIc).entrySet()) {
            int rem = e.getValue();
            String itemId = e.getKey().canonicalItemId(ic2Loaded);
            while (rem > 0) {
                int batch = Math.min(64, rem);
                ItemStack stack = TyC2Native.stack(itemId, batch);
                rem -= batch;
                if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    private static int countMatchingInInventory(Player player, java.util.function.Predicate<ItemStack> pred) {
        int sum = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && pred.test(s)) {
                sum += s.getCount();
            }
        }
        return sum;
    }

    private static void consumeTagOrPredicateFromPlayer(
            Player player,
            int required,
            java.util.function.Predicate<ItemStack> pred
    ) {
        int rem = required;
        for (int i = 0; i < player.getInventory().getContainerSize() && rem > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && pred.test(s)) {
                int take = Math.min(rem, s.getCount());
                s.shrink(take);
                if (s.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                rem -= take;
            }
        }
    }

    private static boolean canManageVillage(ServerPlayer player, TycoonSavedData data, VillageRecord village) {
        if (TyC2Native.hasOpPermission(player)) {
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

    private static BankAccountManager.AccountRef resolveVillageOwnerAccount(TycoonSavedData data, VillageRecord v) {
        if (v.getCompanyAccountId() != null && data.bank().getCompany(v.getCompanyAccountId()).isPresent()) {
            return BankAccountManager.AccountRef.company(v.getCompanyAccountId());
        }
        if (v.getOwnerPlayerUuid() != null) {
            data.bank().getOrCreatePersonal(v.getOwnerPlayerUuid(), null);
            return BankAccountManager.AccountRef.personal(v.getOwnerPlayerUuid());
        }
        return null;
    }

    private static IndustrialTier resolveVillageIndustrialTier(TycoonSavedData data, VillageRecord v) {
        if (v.getCompanyAccountId() != null) {
            Optional<BankAccountManager.CompanyAccount> co = data.bank().getCompany(v.getCompanyAccountId());
            if (co.isPresent()) {
                return data.industrialTiers().getMaxTier(co.get().memberUuids());
            }
        }
        return data.industrialTiers().getTier(v.getOwnerPlayerUuid());
    }

    private static String activeAccountLabel(BankAccountManager bank, UUID playerUuid) {
        BankAccountManager.AccountRef ref = bank.resolveSettlementAccount(playerUuid);
        if (ref.scope() == BankAccountManager.AccountScope.COMPANY) {
            return bank.getCompany(ref.accountId()).map(BankAccountManager.CompanyAccount::name).orElse(ref.accountId());
        }
        return Component.translatable("gui.tyc2.account.personal").getString();
    }

    private static ItemStack findHeldTerminal(Player player) {
        if (player.getMainHandItem().getItem() instanceof CommTerminalItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof CommTerminalItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static boolean isWaterNearVillage(ServerLevel overworld, VillageRecord v) {
        ServerLevel target = resolveDimensionLevel(overworld.getServer(), v.getDimensionId(), overworld);
        BlockPos hub = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
        if (!target.hasChunkAt(hub)) {
            return false;
        }
        LevelTerrainProbe probe = new LevelTerrainProbe(target, getData(target));
        for (int dx = -36; dx <= 36; dx += 6) {
            for (int dz = -36; dz <= 36; dz += 6) {
                if (probe.isWaterSurface(hub.getX() + dx, hub.getZ() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ServerLevel resolveDimensionLevel(MinecraftServer server, String dimId, ServerLevel fallback) {
        if (server == null || dimId == null) {
            return fallback;
        }
        for (ServerLevel lvl : server.getAllLevels()) {
            if (dimId.equals(TyC2Native.dimensionId(lvl))) {
                return lvl;
            }
        }
        return fallback;
    }

    private static void notifyVillageOwnerOrNearby(ServerLevel level, VillageRecord v, Component msg) {
        if (v.getOwnerPlayerUuid() != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(v.getOwnerPlayerUuid());
            if (owner != null) {
                owner.sendSystemMessage(msg);
                return;
            }
        }
        BlockPos hub = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(hub) <= 64.0 * 64.0) {
                p.sendSystemMessage(msg);
            }
        }
    }

    private static String generateVillageName(BlockPos pos) {
        int hash = Math.abs(pos.getX() * 73856093 ^ pos.getZ() * 19349663);
        String prefix = VILLAGE_NAME_PREFIXES[hash % VILLAGE_NAME_PREFIXES.length];
        String suffix = VILLAGE_NAME_SUFFIXES[(hash / VILLAGE_NAME_PREFIXES.length) % VILLAGE_NAME_SUFFIXES.length];
        return prefix + suffix;
    }
}
