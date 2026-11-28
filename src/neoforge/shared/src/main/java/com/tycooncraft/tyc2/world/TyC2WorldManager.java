package com.tycooncraft.tyc2.world;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CommonTagAliases;
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
import com.tycooncraft.tyc2.menu.AtmMachineMenu;
import com.tycooncraft.tyc2.menu.CommTerminalMenu;
import com.tycooncraft.tyc2.menu.CooperationHubMenu;
import com.tycooncraft.tyc2.menu.MayorNegotiationMenu;
import com.tycooncraft.tyc2.network.TyC2ActionPayload;
import com.tycooncraft.tyc2.network.TyC2SyncPayload;
import com.tycooncraft.tyc2.platform.TyC2ChunkTickets;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.Aabb3i;
import com.tycooncraft.tyc2.village.AidConstructionProject;
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
import com.tycooncraft.tyc2.village.StructureBlueprint;
import com.tycooncraft.tyc2.village.SupplyCycleClock;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import com.tycooncraft.tyc2.village.WorkplaceArchitectureCatalog;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.village.WorkplaceSpec;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
    private static final Set<String> BASIC_NEGOTIATION_FOODS = Set.of(
            "minecraft:bread", "minecraft:baked_potato", "minecraft:cooked_beef",
            "minecraft:cooked_porkchop", "minecraft:cooked_mutton", "minecraft:cooked_chicken",
            "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:cooked_rabbit",
            "minecraft:carrot", "minecraft:apple", "minecraft:potato", "minecraft:beetroot",
            "minecraft:sweet_berries", "minecraft:pumpkin_pie", "minecraft:golden_carrot"
    );

    private static final Set<String> REJECTED_NEGOTIATION_FOODS = Set.of(
            "minecraft:rotten_flesh",
            "minecraft:spider_eye",
            "minecraft:pufferfish",
            "minecraft:poisonous_potato",
            "minecraft:chorus_fruit"
    );

    private record ActiveNegotiation(UUID playerUuid, String dimensionId) {
    }

    /** Maps negotiating Mayor entity UUID -> active negotiating player & dimension. */
    private static final Map<UUID, ActiveNegotiation> ACTIVE_NEGOTIATIONS = new ConcurrentHashMap<>();

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
                if (data.industrialTiers().recordItemMilestone(
                        player.getUUID(), placedId, Ic2SoftBridge.isIc2Loaded(), TyC2Native::itemIdHasTag)) {
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
                                Component.translatable("status.mnera.hub.protected").withStyle(ChatFormatting.RED)
                        );
                    }
                    return true;
                }
                findVillageByHubPos(serverLevel, pos).ifPresent(v -> {
                    v.setHubDeployed(false);
                    getData(serverLevel).setDirty();
                });
            }
            getData(serverLevel).removePlayerPlaced(TyC2Native.dimensionId(serverLevel), pos);
            return false;
        });

        gameBus.addListener(PlayerInteractEvent.EntityInteract.class, TyC2WorldManager::onEntityInteract);
        gameBus.addListener(LivingDeathEvent.class, TyC2WorldManager::onLivingDeath);
        gameBus.addListener(PlayerTickEvent.Post.class, TyC2WorldManager::onPlayerTick);
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                endAllNegotiationsForPlayer(sp);
            }
        });

        gameBus.addListener(ExplosionEvent.Detonate.class, event -> {
            Level lvl = event.getLevel();
            event.getAffectedBlocks().removeIf(
                    bp -> lvl.getBlockState(bp).is(TyC2Registries.COOPERATION_HUB_BLOCK.get())
            );
        });

        gameBus.addListener(ServerStartedEvent.class, event -> {
            MinecraftServer server = event.getServer();
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                return;
            }
            TycoonSavedData data = getData(overworld);
            for (VillageRecord v : data.allVillages()) {
                if (v.isPartnered() && v.isChunkLoaderInstalled()) {
                    ServerLevel targetLevel = resolveDimensionLevel(server, v.getDimensionId(), overworld);
                    ChunkPos cp = new ChunkPos(v.getHubPos().x() >> 4, v.getHubPos().z() >> 4);
                    TyC2ChunkTickets.holdArea3x3(targetLevel, cp);
                }
            }
        });
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
            VillageRecord rec = existing.get();
            if (rec.isPartnered() && !rec.isHubDeployed()) {
                rec.setHubDeployed(true);
                getData(level).setDirty();
            }
            return rec;
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
            TyC2Config cfg = TyC2DataLoader.config();
            String activeCo = data.bank().getPersonal(ownerUuid)
                    .map(BankAccountManager.PersonalAccount::activeCompanyId)
                    .orElse(null);
            v.establishPartnershipWithWorkingCapital(
                    ownerUuid,
                    activeCo,
                    cfg.negotiationCostIc(),
                    cfg.initialVillageWorkingCapital()
            );
            v.setHubDeployed(true);
            long dayTime = overworldDayTime(level);
            v.anchorStipendIfUnset(dayTime);
            refreshVillageOrders(level, data, v, dayTime);
        }
        data.putVillage(v);
        if (ownerUuid != null) {
            syncMacroAccountTotals(data);
        }
        return v;
    }

    private static long overworldDayTime(ServerLevel level) {
        return TyC2Native.dayTime(level);
    }

    private static void syncMacroAccountTotals(TycoonSavedData data) {
        long villageTotal = 0L;
        int partneredCount = 0;
        for (VillageRecord v : data.allVillages()) {
            // Include unpartnered villages' remaining treasuries so terminating a partnership cannot cloak money supply
            if (v.getVillageBalance() > 0L) {
                villageTotal += v.getVillageBalance();
            }
            if (v.isPartnered()) {
                partneredCount++;
            }
        }
        data.economy().syncAccountTotals(
                data.bank().getTotalCombinedBalance(),
                villageTotal,
                Math.max(1, partneredCount)
        );
    }

    /**
     * Restores a partnered village's Cooperation Hub block and block entity at its canonical
     * coordinates (used by OP recovery command {@code /tyc2 restore_hub}).
     */
    public static BlockPos restoreHubBlock(ServerLevel fallbackLevel, VillageRecord v) {
        ServerLevel targetLevel = resolveDimensionLevel(fallbackLevel.getServer(), v.getDimensionId(), fallbackLevel);
        BlockPos hubPos = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
        targetLevel.setBlock(hubPos, TyC2Registries.COOPERATION_HUB_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (targetLevel.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity be) {
            be.setVillageId(v.getVillageId());
        }
        v.setHubDeployed(true);
        v.anchorStipendIfUnset(overworldDayTime(targetLevel));
        getData(targetLevel).setDirty();
        return hubPos;
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

        // Every tick: keep negotiating Mayors halted and facing the negotiating player
        tickActiveNegotiations(level, dimId);

        // Every 20 ticks: keep non-negotiating Mayors anchored to their village Bell / Hub plaza
        if (gameTime % 20L == 0L) {
            tickMayorPlazaAnchors(level, data, dimId);
        }

        // Periodic Bell/Hub village scan & Mayor election near online players (every 80 ticks)
        if (gameTime % 80L == 0L && !level.players().isEmpty()) {
            scanLoadedVillagesAndMayors(level, data, gameTime);
        }
    }

    /** Starts an active negotiation lock so {@code mayor} halts and faces {@code player}. */
    public static void startMayorNegotiation(ServerPlayer player, ServerLevel level, Entity mayor) {
        if (player == null || level == null || mayor == null) {
            return;
        }
        ACTIVE_NEGOTIATIONS.put(mayor.getUUID(), new ActiveNegotiation(player.getUUID(), TyC2Native.dimensionId(level)));
        TyC2Native.holdMayorForNegotiation(mayor, player);
    }

    /** Releases a specific Mayor from active negotiation tracking. */
    public static void endMayorNegotiation(ServerPlayer player, UUID mayorUuid) {
        if (mayorUuid == null) {
            return;
        }
        ActiveNegotiation removed = ACTIVE_NEGOTIATIONS.remove(mayorUuid);
        if (player != null && player.level() instanceof ServerLevel level) {
            Entity e = level.getEntity(mayorUuid);
            if (e != null) {
                TyC2Native.setVillagerTradingPlayer(e, null);
            } else if (removed != null && level.getServer() != null) {
                ServerLevel target = resolveDimensionLevel(level.getServer(), removed.dimensionId(), level);
                Entity other = target.getEntity(mayorUuid);
                if (other != null) {
                    TyC2Native.setVillagerTradingPlayer(other, null);
                }
            }
        }
    }

    /** Releases all Mayors currently negotiating with {@code player} (logout / menu close / sign). */
    public static void endAllNegotiationsForPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID pid = player.getUUID();
        List<UUID> toRelease = new ArrayList<>();
        for (Map.Entry<UUID, ActiveNegotiation> entry : ACTIVE_NEGOTIATIONS.entrySet()) {
            if (pid.equals(entry.getValue().playerUuid())) {
                toRelease.add(entry.getKey());
            }
        }
        for (UUID mayorUuid : toRelease) {
            endMayorNegotiation(player, mayorUuid);
        }
    }

    private static void tickActiveNegotiations(ServerLevel level, String dimId) {
        if (ACTIVE_NEGOTIATIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        for (Map.Entry<UUID, ActiveNegotiation> entry : new ArrayList<>(ACTIVE_NEGOTIATIONS.entrySet())) {
            ActiveNegotiation neg = entry.getValue();
            if (!dimId.equals(neg.dimensionId())) {
                continue;
            }
            UUID mayorUuid = entry.getKey();
            Entity mayor = level.getEntity(mayorUuid);
            ServerPlayer player = server != null ? server.getPlayerList().getPlayer(neg.playerUuid()) : null;
            boolean valid = mayor != null
                    && mayor.isAlive()
                    && player != null
                    && player.isAlive()
                    && player.level() == level
                    && player.containerMenu instanceof MayorNegotiationMenu
                    && mayor.distanceToSqr(player) <= 16.0 * 16.0;
            if (!valid) {
                ACTIVE_NEGOTIATIONS.remove(mayorUuid);
                if (mayor != null) {
                    TyC2Native.setVillagerTradingPlayer(mayor, null);
                }
            } else {
                TyC2Native.holdMayorForNegotiation(mayor, player);
            }
        }
    }

    private static void tickMayorPlazaAnchors(ServerLevel level, TycoonSavedData data, String dimId) {
        for (VillageRecord v : data.allVillages()) {
            if (!dimId.equals(v.getDimensionId())) {
                continue;
            }
            UUID mayorUuid = v.getMayorState().getMayorEntityUuid();
            if (mayorUuid == null || ACTIVE_NEGOTIATIONS.containsKey(mayorUuid)) {
                continue;
            }
            BlockPos hubPos = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
            if (!level.hasChunkAt(hubPos)) {
                continue;
            }
            Entity mayor = level.getEntity(mayorUuid);
            if (mayor != null && mayor.isAlive()) {
                TyC2Native.anchorMayorToPlaza(level, mayor, hubPos);
            }
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
            syncMacroAccountTotals(data);
        }

        // 2. Daily municipal upkeep deflationary sink (runs on ALL villages with remaining treasury to prevent cloaking)
        long currentDay = overworld.getGameTime() / 24_000L;
        if (data.lastUpkeepDay() < 0L) {
            data.setLastUpkeepDay(currentDay);
        } else if (currentDay > data.lastUpkeepDay()) {
            data.setLastUpkeepDay(currentDay);
            long totalUpkeep = 0L;
            for (VillageRecord v : data.allVillages()) {
                if (v.getVillageBalance() > 0L) {
                    long upkeep = v.applyMunicipalUpkeep(cfg.municipalUpkeepRate());
                    if (upkeep > 0L) {
                        data.economy().recordVillageMoneySink(upkeep);
                        totalUpkeep += upkeep;
                    }
                }
            }
            if (totalUpkeep > 0L) {
                syncMacroAccountTotals(data);
                data.setDirty();
            }
        }

        // 3. Per-village virtual timers (run even when Hub chunks are unloaded!)
        long dayTime = TyC2Native.dayTime(overworld);
        boolean runAutoFulfill = (gameTime % cfg.autoFulfillIntervalTicks()) == 0L;
        boolean runAidTick = (gameTime % 20L) == 0L;

        for (VillageRecord v : data.allVillages()) {
            if (!v.isPartnered()) {
                continue;
            }

            // Re-anchor backward /time set jumps or legacy saves so weekly refreshes never stall
            if (v.normalizeDemandRefreshAnchor(dayTime)) {
                data.setDirty();
            }

            // 3a. Auto-fulfill Buy Orders & Expansion Desire from Inbound 0..499
            if (runAutoFulfill) {
                processAutoFulfill(overworld, data, v, gameTime);
            }

            // 3b. Fixed weekly calendar dayTime demand order refresh (never early-reset on all-orders-completed)
            if (v.canGenerateOrders()) {
                int refreshTicks = Math.max(200, cfg.demandRefreshTicks());
                boolean neverInitialized = !v.hasInitialOrdersGenerated();
                if (SupplyCycleClock.isRefreshDue(
                        dayTime, v.getLastDemandRefreshTick(), refreshTicks, neverInitialized)) {
                    // Apply weekly municipal stipend strictly at most once per full weekly period
                    if (!neverInitialized) {
                        long stipend = v.applyWeeklyMunicipalStipendIfDue(
                                cfg.weeklyMunicipalStipendRatio(), dayTime, refreshTicks
                        );
                        if (stipend > 0L) {
                            syncMacroAccountTotals(data);
                            data.setDirty();
                        }
                    } else {
                        v.anchorStipendIfUnset(dayTime);
                    }
                    data.economy().advanceDemandCycleIfDue(dayTime, refreshTicks);
                    refreshVillageOrders(overworld, data, v, dayTime);
                }
            }

            // 3c. Periodic Expansion Desire generation (paused when expansionPaused == true)
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

            // 3d. Aid construction background gathering & site planning (NEVER paused by expansionPaused!)
            if (runAidTick && !v.getAidProjects().isEmpty()) {
                tickVillageAidProjects(overworld, data, cfg, v, gameTime);
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
            VillageRecord.TradeExecutionResult res = v.executeBuyOrderFromInboundMatching(
                    order.orderId(), order.remainingQty(), buildOrderSlotMatcher(order)
            );
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
                long basePrice = TyC2DataLoader.goodsCatalog()
                        .findOrderGood(order.displayHint(), order.itemId(), true)
                        .map(g -> g.basePrice())
                        .orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
                data.setDirty();
            }
        }

        // Auto-fulfill Expansion Desire & auto-launch ready desires once funds/site are available
        ExpansionDesire desire = v.getActiveExpansion();
        if (desire != null && !desire.completed() && !v.isExpansionPaused()) {
            ExpansionDesire current = desire;
            if (current.autoFulfill()) {
                ExpansionDesire updated = current.pullAvailableFromInbound(v.getStorage(), TyC2Native::itemIdHasTag);
                if (!updated.equals(current)) {
                    v.setActiveExpansion(updated);
                    data.setDirty();
                    current = updated;
                }
            }
            if (current.allMaterialsDelivered() && gameTime >= v.getNextAutoExpansionRetryTick()) {
                boolean launched = tryLaunchExpansionFromDesire(overworld, data, v, gameTime, null);
                if (!launched && v.getVillageBalance() >= current.constructionBudgetCost()) {
                    v.setNextAutoExpansionRetryTick(gameTime + 400L);
                }
            }
        }
    }

    private static void tickVillageAidProjects(
            ServerLevel overworld,
            TycoonSavedData data,
            TyC2Config cfg,
            VillageRecord v,
            long gameTime
    ) {
        // 1. Advance the head COLLECTING aid project (works even while village chunks are unloaded)
        double gatherSpeed = v.getEmploymentStats().gatherSpeedMultiplier(cfg);
        double delta = 20.0 * gatherSpeed;
        for (AidConstructionProject proj : v.getAidProjects()) {
            if (proj.status() == AidConstructionProject.Status.COLLECTING) {
                AidConstructionProject updated = proj.advanceGathering(delta);
                if (!updated.equals(proj)) {
                    v.updateAidProject(updated);
                    data.setDirty();
                }
                break;
            }
        }

        // 2. Plan & launch any WAITING_SITE or (every 400 ticks) NO_SITE project once Hub chunk is loaded
        boolean retryNoSite = (gameTime % 400L) == 0L;
        ServerLevel targetLevel = null;
        boolean hubLoaded = false;
        boolean checkedLoaded = false;

        for (AidConstructionProject proj : new ArrayList<>(v.getAidProjects())) {
            boolean shouldAttempt = proj.status() == AidConstructionProject.Status.WAITING_SITE
                    || (proj.status() == AidConstructionProject.Status.NO_SITE && retryNoSite);
            if (!shouldAttempt) {
                continue;
            }
            if (!checkedLoaded) {
                targetLevel = resolveDimensionLevel(overworld.getServer(), v.getDimensionId(), overworld);
                BlockPos hubPos = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
                hubLoaded = targetLevel.hasChunkAt(hubPos);
                checkedLoaded = true;
            }
            if (!hubLoaded) {
                break;
            }

            StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(
                    proj.toWorkplaceSpec(), proj.size(), v.getBiomeStyle()
            );
            String jobId = "aid_job_" + proj.projectId();
            LevelTerrainProbe probe = new LevelTerrainProbe(targetLevel, data);
            ExpansionPlanner.PlanResult plan = PLANNER.planBlueprintIgnoringZone(
                    v, bp, probe, jobId, true, proj.reservedZone()
            );
            if (plan.success()) {
                if (proj.reservedZone() != null && !proj.reservedZone().equals(plan.reservedZone())) {
                    v.removeOccupiedZone(proj.reservedZone());
                }
                PLANNER.commitPlanWithPriority(v, plan, gameTime, true);
                AidConstructionProject building = proj
                        .withReservedZone(plan.reservedZone())
                        .withBuilding(jobId);
                v.updateAidProject(building);
                data.setDirty();
                notifyVillageOwnerOrNearby(
                        targetLevel,
                        v,
                        Component.translatable(
                                "status.mnera.aid.building",
                                v.getVillageName(),
                                workplaceDisplayComponent(building)
                        ).withStyle(ChatFormatting.AQUA)
                );
            } else if (proj.status() == AidConstructionProject.Status.WAITING_SITE) {
                v.updateAidProject(proj.withStatus(AidConstructionProject.Status.NO_SITE));
                data.setDirty();
                notifyVillageOwnerOrNearby(
                        targetLevel,
                        v,
                        Component.translatable(
                                "status.mnera.aid.no_site_waiting",
                                v.getVillageName(),
                                workplaceDisplayComponent(proj)
                        ).withStyle(ChatFormatting.YELLOW)
                );
            }
        }
    }

    private static void tickVillageConstruction(ServerLevel level, TycoonSavedData data, VillageRecord v) {
        Optional<ConstructionJob> jobOpt = v.getActiveConstructionJob();
        if (jobOpt.isEmpty()) {
            return;
        }
        ConstructionJob job = jobOpt.get();
        Aabb3i box = job.getBoundingBox();
        int minCx = box.minX() >> 4;
        int maxCx = box.maxX() >> 4;
        int minCz = box.minZ() >> 4;
        int maxCz = box.maxZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return;
                }
            }
        }
        ConstructionJob.StepResult step = job.tick();
        if (step.placedBlocks().isEmpty() && !step.completedNow()) {
            return;
        }
        int placementFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
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
            level.setBlock(bp, targetState, placementFlags);
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
            for (WorldBlockPlacement wp : job.getPlacements()) {
                if (!WorldBlockPlacement.isFragileOrAttached(wp.blockStateSpec())) {
                    continue;
                }
                BlockPos bp = new BlockPos(wp.pos().x(), wp.pos().y(), wp.pos().z());
                if (level.hasChunkAt(bp)
                        && level.getBlockState(bp).isAir()
                        && !data.isPlayerPlaced(v.getDimensionId(), bp.getX(), bp.getY(), bp.getZ())) {
                    BlockState targetState = BlockStateSpecParser.parse(wp.blockStateSpec());
                    if (!targetState.isAir()) {
                        level.setBlock(bp, targetState, placementFlags);
                    }
                }
            }
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
        BlockPos hub = new BlockPos(v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z());
        int popCap = TyC2DataLoader.levelTable().getSpecClamped(v.getLevel()).maxPopulationCap();
        int spawnedVillagers = 0;

        // Collect unemployed non-nitwit non-mayor adult villagers for any WORKSTATION markers
        boolean hasWorkstations = markers.stream().anyMatch(m -> m.kind() == SpawnMarker.MarkerKind.WORKSTATION);
        List<Entity> unemployedCandidates = List.of();
        Set<UUID> assignedVillagers = new HashSet<>();
        if (hasWorkstations) {
            UUID mayorUuid = v.getMayorState().getMayorEntityUuid();
            List<Entity> allNearby = TyC2Native.findVillagersInRadius(level, hub, v.effectiveVillagerScanRadius());
            List<Entity> eligible = new ArrayList<>();
            for (Entity e : allNearby) {
                if (mayorUuid != null && mayorUuid.equals(e.getUUID())) {
                    continue;
                }
                if (!TyC2Native.isAdultVillager(e) || TyC2Native.isNitwitVillager(e)) {
                    continue;
                }
                if (VillageEmploymentStats.isUnemployedProfession(TyC2Native.villagerProfessionId(e))) {
                    eligible.add(e);
                }
            }
            unemployedCandidates = eligible;
        }

        for (SpawnMarker.WorldSpawnMarker marker : markers) {
            BlockPos pos = new BlockPos(marker.pos().x(), marker.pos().y(), marker.pos().z());
            if (marker.kind() == SpawnMarker.MarkerKind.VILLAGER) {
                if (v.getPopulation() + spawnedVillagers < popCap) {
                    TyC2Native.spawnVillager(level, pos);
                    spawnedVillagers++;
                }
            } else if (marker.kind() == SpawnMarker.MarkerKind.ANIMAL) {
                TyC2Native.spawnAnimal(level, pos, marker.hint());
            } else if (marker.kind() == SpawnMarker.MarkerKind.WORKSTATION) {
                Entity best = null;
                double bestDistSq = Double.MAX_VALUE;
                for (Entity candidate : unemployedCandidates) {
                    if (assignedVillagers.contains(candidate.getUUID())) {
                        continue;
                    }
                    double d2 = candidate.blockPosition().distSqr(pos);
                    if (d2 < bestDistSq) {
                        bestDistSq = d2;
                        best = candidate;
                    }
                }
                if (best != null
                        && TyC2Native.assignVillagerToWorkstation(level, best, pos, marker.hint())) {
                    assignedVillagers.add(best.getUUID());
                }
            }
        }

        if (spawnedVillagers > 0) {
            v.setPopulation(v.getPopulation() + spawnedVillagers);
            int desiredGolems = v.getPopulation() / 10;
            TyC2Native.spawnIronGolemIfNeeded(level, hub, 36.0, desiredGolems);
        }

        // Aid construction completion: remove completed aid record, refresh employment stats immediately, notify
        Optional<AidConstructionProject> completedAid = v.findAidProjectByJobId(job.getJobId());
        if (completedAid.isPresent() || job.getProjectType().isAidWorkplace()) {
            Component wpTitle;
            if (completedAid.isPresent()) {
                AidConstructionProject aidProj = completedAid.get();
                v.incrementCompletedAidCount(aidProj.professionId());
                v.removeAidProject(aidProj.projectId());
                wpTitle = workplaceDisplayComponent(aidProj);
            } else {
                wpTitle = Component.translatable(
                        "project.mnera." + job.getProjectType().name().toLowerCase(Locale.ROOT)
                );
            }
            if (level.hasChunkAt(hub)) {
                evaluateVillageMayorAndPop(level, data, v, hub, level.getGameTime());
            }
            notifyVillageOwnerOrNearby(
                    level,
                    v,
                    Component.translatable("status.mnera.aid.completed", v.getVillageName(), wpTitle)
                            .withStyle(ChatFormatting.GREEN)
            );
            data.setDirty();
            return;
        }

        // Regular expansion completion: notify owner if online
        if (v.getOwnerPlayerUuid() != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(v.getOwnerPlayerUuid());
            if (owner != null) {
                Component projectTitle = Component.translatable(
                        "project.mnera." + job.getProjectType().name().toLowerCase(Locale.ROOT)
                );
                owner.sendSystemMessage(Component.translatable(
                        "status.mnera.expansion.completed", v.getVillageName(), projectTitle
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
        List<Entity> villagers = TyC2Native.findVillagersInRadius(level, hubPos, v.effectiveVillagerScanRadius());
        int count = villagers.size();
        v.setPopulation(count);

        List<MayorElectionRules.MayorCandidate> candidates = new ArrayList<>(villagers.size());
        List<VillageEmploymentStats.VillagerSnapshot> snapshots = new ArrayList<>(villagers.size());
        Map<UUID, Entity> entityById = new LinkedHashMap<>();
        for (Entity e : villagers) {
            entityById.put(e.getUUID(), e);
            boolean adult = TyC2Native.isAdultVillager(e);
            boolean nitwit = TyC2Native.isNitwitVillager(e);
            String profId = TyC2Native.villagerProfessionId(e);
            candidates.add(new MayorElectionRules.MayorCandidate(
                    e.getUUID(),
                    v.getVillageName(),
                    adult,
                    nitwit,
                    e.isAlive(),
                    TyC2Native.villagerXp(e),
                    e.tickCount
            ));
            snapshots.add(new VillageEmploymentStats.VillagerSnapshot(
                    e.getUUID(),
                    adult,
                    nitwit,
                    profId
            ));
        }

        boolean wasPaused = v.getMayorState().isPausedDueToDepopulation();
        MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.evaluate(
                v.getMayorState(), count, candidates, gameTime
        );
        VillageEmploymentStats prevStats = v.getEmploymentStats();
        VillageEmploymentStats nextStats = VillageEmploymentStats.computeFromSnapshots(
                snapshots, outcome.mayorUuid(), gameTime
        );
        v.setEmploymentStats(nextStats);
        if (prevStats.workforceCount() != nextStats.workforceCount()
                || prevStats.employedCount() != nextStats.employedCount()
                || prevStats.unemployedCount() != nextStats.unemployedCount()
                || prevStats.mayorCountedEmployed() != nextStats.mayorCountedEmployed()
                || !prevStats.activeProfessionCounts().equals(nextStats.activeProfessionCounts())) {
            data.setDirty();
        }
        // Strip legacy/disqualified Mayor tags (e.g. Nitwit Mayors from older saves) from any non-mayor villager
        for (Entity e : villagers) {
            if (outcome.mayorUuid() == null || !e.getUUID().equals(outcome.mayorUuid())) {
                if (TyC2Nbt.getBoolean(e.getPersistentData(), TyC2Constants.TAG_MAYOR, false)) {
                    stripMayorVisuals(e);
                    data.setDirty();
                }
            }
        }
        if (outcome.mayorUuid() != null) {
            Entity mayor = entityById.get(outcome.mayorUuid());
            if (mayor != null) {
                applyMayorVisuals(mayor, v);
            }
        }
        if (outcome.action() == MayorElectionRules.Action.ELECTED_NEW_MAYOR) {
            notifyVillageOwnerOrNearby(level, v, Component.translatable("mayor.mnera.elected", v.getVillageName())
                    .withStyle(ChatFormatting.GOLD));
            data.setDirty();
        } else if (outcome.action() == MayorElectionRules.Action.RESUMED_AND_ELECTED) {
            notifyVillageOwnerOrNearby(level, v, Component.translatable("mayor.mnera.resumed", v.getVillageName())
                    .withStyle(ChatFormatting.GREEN));
            data.setDirty();
        } else if (outcome.action() == MayorElectionRules.Action.PAUSED_DEPOPULATED && !wasPaused) {
            notifyVillageOwnerOrNearby(level, v, Component.translatable("mayor.mnera.paused_depopulated", v.getVillageName())
                    .withStyle(ChatFormatting.RED));
            data.setDirty();
        }
    }

    private static void applyMayorVisuals(Entity villager, VillageRecord v) {
        villager.getPersistentData().putBoolean(TyC2Constants.TAG_MAYOR, true);
        villager.getPersistentData().putString(TyC2Constants.TAG_MAYOR_VILLAGE_ID, v.getVillageId().toString());
        villager.setCustomName(
                Component.translatable("mayor.mnera.name_format", v.getVillageName()).withStyle(ChatFormatting.GOLD)
        );
        villager.setCustomNameVisible(true);
        villager.setGlowingTag(true);
    }

    private static void stripMayorVisuals(Entity villager) {
        villager.getPersistentData().remove(TyC2Constants.TAG_MAYOR);
        villager.getPersistentData().remove(TyC2Constants.TAG_MAYOR_VILLAGE_ID);
        villager.setCustomName(null);
        villager.setCustomNameVisible(false);
        villager.setGlowingTag(false);
        TyC2Native.setVillagerTradingPlayer(villager, null);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Entity target = event.getTarget();
        if (!TyC2Native.isExactVanillaVillager(target)) {
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
            village = findNearestVillage(
                    serverLevel,
                    target.blockPosition(),
                    Math.max(TyC2Constants.VILLAGE_SCAN_RADIUS + 8, VillageRecord.MAX_CONFIGURABLE_RADIUS + 12)
            ).orElse(null);
        }
        if (village == null) {
            return;
        }

        boolean isMayor = taggedMayor || target.getUUID().equals(village.getMayorState().getMayorEntityUuid());
        if (!isMayor && Math.abs(target.blockPosition().getY() - village.getHubPos().y()) > 24) {
            return;
        }

        if (village.isPartnered()) {
            // Cancel vanilla emerald trading inside ALL partnered villages!
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (isMayor) {
                player.sendSystemMessage(Component.translatable(
                        "mayor.mnera.partnered_status",
                        village.getVillageName(),
                        village.getLevel(),
                        village.getVillageBalance(),
                        village.getActiveOrders().size()
                ).withStyle(ChatFormatting.GOLD));
            } else {
                TyC2Native.sendActionBar(
                        player,
                        Component.translatable("mayor.mnera.villager_redirect").withStyle(ChatFormatting.YELLOW)
                );
            }
            return;
        }

        if (isMayor) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            UUID villageId = village.getVillageId();
            UUID mayorUuid = target.getUUID();
            BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
            startMayorNegotiation(player, serverLevel, target);
            player.openMenu(
                    new SimpleMenuProvider(
                            (syncId, inv, p) -> new MayorNegotiationMenu(syncId, inv, villageId, hubPos, mayorUuid),
                            Component.translatable("container.mnera.mayor_negotiation")
                    ),
                    buf -> {
                        buf.writeUUID(villageId);
                        buf.writeBlockPos(hubPos);
                        buf.writeBoolean(true);
                        buf.writeUUID(mayorUuid);
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
        List<VillageEmploymentStats.VillagerSnapshot> snapshots = new ArrayList<>();
        Map<UUID, Entity> byId = new LinkedHashMap<>();
        for (Entity e : TyC2Native.findVillagersInRadius(level, hubPos, village.effectiveVillagerScanRadius())) {
            if (e.getUUID().equals(entity.getUUID())) {
                continue;
            }
            others.add(e);
            byId.put(e.getUUID(), e);
            boolean adult = TyC2Native.isAdultVillager(e);
            boolean nitwit = TyC2Native.isNitwitVillager(e);
            candidates.add(new MayorElectionRules.MayorCandidate(
                    e.getUUID(),
                    village.getVillageName(),
                    adult,
                    nitwit,
                    e.isAlive(),
                    TyC2Native.villagerXp(e),
                    e.tickCount
            ));
            snapshots.add(new VillageEmploymentStats.VillagerSnapshot(
                    e.getUUID(),
                    adult,
                    nitwit,
                    TyC2Native.villagerProfessionId(e)
            ));
        }

        notifyVillageOwnerOrNearby(
                level,
                village,
                Component.translatable("mayor.mnera.died", village.getVillageName()).withStyle(ChatFormatting.RED)
        );
        MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.onMayorDeathAndReelect(
                village.getMayorState(),
                entity.getUUID(),
                others.size(),
                candidates,
                level.getGameTime()
        );
        village.setPopulation(others.size());
        village.setEmploymentStats(VillageEmploymentStats.computeFromSnapshots(
                snapshots, outcome.mayorUuid(), level.getGameTime()
        ));
        if (outcome.mayorUuid() != null) {
            Entity newMayor = byId.get(outcome.mayorUuid());
            if (newMayor != null) {
                applyMayorVisuals(newMayor, village);
                notifyVillageOwnerOrNearby(
                        level,
                        village,
                        Component.translatable("mayor.mnera.elected", village.getVillageName())
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
                if (data.industrialTiers().recordItemMilestone(
                        player.getUUID(), TyC2Native.itemId(s), ic2, TyC2Native::itemIdHasTag)) {
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

    /** Regenerates fresh Sell & Buy orders for {@code v} using DemandGenerator + Season + CPI + Tier + Employment. */
    public static void refreshVillageOrders(
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord v,
            long dayTime
    ) {
        Season season = SeasonSoftBridge.currentSeason(level);
        IndustrialTier tier = resolveVillageIndustrialTier(data, v);
        TyC2Config cfg = TyC2DataLoader.config();
        Predicate<String> modLoaded = modId -> ModList.get() != null && ModList.get().isLoaded(modId);
        DemandGenerator.GeneratedDemand demand = TyC2DataLoader.demandGenerator().generate(
                v.getLevel(),
                v.getBiomeStyle().id(),
                v.getVillageBalance(),
                tier,
                season,
                data.economy(),
                TyC2Native::itemExists,
                modLoaded,
                TyC2Native::findFirstItemIdForTag,
                v.getEmploymentStats(),
                TyC2DataLoader.workplaceCatalog(),
                RNG
        );

        long anchorTick = Math.max(0L, dayTime);
        int refreshTicks = Math.max(200, cfg.demandRefreshTicks());
        long expires = anchorTick + refreshTicks;
        List<VillageTradeOrder> fresh = new ArrayList<>();
        for (DemandGenerator.GeneratedOrder go : demand.allOrders()) {
            OrderSide side = go.side() == DemandGenerator.OrderSide.VILLAGE_SELLS
                    ? OrderSide.VILLAGE_SELLS : OrderSide.VILLAGE_BUYS;
            fresh.add(new VillageTradeOrder(
                    go.orderId() + "_" + anchorTick,
                    side,
                    go.itemId(),
                    go.goodId(),
                    go.optionalEnchantmentSpec() == null ? "" : go.optionalEnchantmentSpec(),
                    go.quantity(),
                    0,
                    go.unitPrice(),
                    anchorTick,
                    expires,
                    false,
                    go.seasonalHighlight(),
                    go.biomeSpecialty(),
                    go.optionalTag() == null ? "" : go.optionalTag()
            ));
        }
        v.setActiveOrders(fresh);
        v.setLastDemandRefreshTick(anchorTick);
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
                        Component.translatable(
                                "status.mnera.expansion.low_funds",
                                desire.constructionBudgetCost(),
                                v.getVillageBalance()
                        ).withStyle(ChatFormatting.RED)
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
                        Component.translatable("status.mnera.expansion.no_site").withStyle(ChatFormatting.RED)
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
                "project.mnera." + desire.projectType().name().toLowerCase(Locale.ROOT)
        );
        notifyVillageOwnerOrNearby(
                targetLevel,
                v,
                Component.translatable("status.mnera.expansion.started", v.getVillageName(), projectTitle)
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
        if (!(player.containerMenu instanceof AtmMachineMenu atmMenu) || !pos.equals(atmMenu.blockPos())) {
            return;
        }
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
        MutableComponent statusComp = null;

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
                        statusComp = Component.translatable(
                                "status.mnera.atm.deposited", value, activeAccountLabelComponent(bank, player.getUUID())
                        );
                    }
                }
            }
            case "DEPOSIT_ALL" -> {
                long total = 0L;
                ItemStack slotStack = atm.getItem(AtmMachineBlockEntity.SLOT_DEPOSIT);
                Optional<CurrencyDenomination> slotDenom = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(slotStack));
                boolean hasSlotCoins = slotDenom.isPresent() && !slotStack.isEmpty();
                if (hasSlotCoins) {
                    total += slotDenom.get().value() * slotStack.getCount();
                }
                List<Integer> coinSlots = new ArrayList<>();
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack invStack = player.getInventory().getItem(i);
                    Optional<CurrencyDenomination> d = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(invStack));
                    if (d.isPresent() && !invStack.isEmpty()) {
                        total += d.get().value() * invStack.getCount();
                        coinSlots.add(i);
                    }
                }
                if (total > 0L) {
                    BankAccountManager.BankResult res = bank.deposit(player.getUUID(), total, now, "ATM Deposit All");
                    if (res.success()) {
                        if (hasSlotCoins) {
                            atm.setItem(AtmMachineBlockEntity.SLOT_DEPOSIT, ItemStack.EMPTY);
                        }
                        for (int idx : coinSlots) {
                            player.getInventory().setItem(idx, ItemStack.EMPTY);
                        }
                        data.economy().recordCashDepositedToBank(total);
                        data.setDirty();
                        statusComp = Component.translatable(
                                "status.mnera.atm.deposited", total, activeAccountLabelComponent(bank, player.getUUID())
                        );
                    } else if (res.errorKey() != null) {
                        statusComp = Component.translatable(res.errorKey());
                    }
                }
            }
            case "WITHDRAW_AMOUNT" -> {
                long amount = JsonLite.getLong(args, "amount", 0L);
                if (amount <= 0L) {
                    statusComp = Component.translatable("status.mnera.atm.invalid_amount");
                } else if (!CurrencyDenomination.isWithinSingleWithdrawalLimit(amount)) {
                    statusComp = Component.translatable(
                            "status.mnera.atm.withdraw_limit", CurrencyDenomination.MAX_SINGLE_WITHDRAWAL_IC
                    );
                } else {
                    BankAccountManager.BankResult res = bank.withdraw(player.getUUID(), amount, now, "ATM Withdraw");
                    if (res.success()) {
                        dispenseCoins(player, atm, CurrencyDenomination.decompose(amount));
                        data.economy().recordCashWithdrawnFromBank(amount);
                        data.setDirty();
                        statusComp = Component.translatable("status.mnera.atm.withdrawn", amount);
                    } else if (res.errorKey() != null) {
                        statusComp = Component.translatable(res.errorKey());
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
                        statusComp = Component.translatable("status.mnera.atm.withdrawn", total);
                    } else if (res.errorKey() != null) {
                        statusComp = Component.translatable(res.errorKey());
                    }
                }
            }
            case "CREATE_COMPANY" -> {
                String name = JsonLite.getString(args, "name", "").trim();
                if (name.length() > BankAccountManager.MAX_COMPANY_NAME_LENGTH) {
                    name = name.substring(0, BankAccountManager.MAX_COMPANY_NAME_LENGTH).trim();
                }
                if (!name.isEmpty()) {
                    if (!bank.canJoinAnotherCompany(player.getUUID())) {
                        statusComp = Component.translatable("status.mnera.atm.company_limit");
                    } else {
                        try {
                            BankAccountManager.CompanyAccount co = bank.createCompany(
                                    player.getUUID(), player.getName().getString(), name
                            );
                            bank.setActiveCompany(player.getUUID(), co.companyId());
                            data.setDirty();
                            statusComp = Component.translatable("status.mnera.atm.company_created", co.name(), co.inviteCode());
                        } catch (IllegalStateException ex) {
                            statusComp = Component.translatable("status.mnera.atm.company_limit");
                        }
                    }
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
                    statusComp = Component.translatable("status.mnera.atm.company_joined", coName);
                } else if (res.errorKey() != null) {
                    statusComp = Component.translatable(res.errorKey());
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
                        statusComp = Component.translatable("status.mnera.atm.company_left", coName);
                    } else if (res.errorKey() != null) {
                        statusComp = Component.translatable(res.errorKey());
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
                    statusComp = Component.translatable(res.errorKey());
                }
            }
            case "TRANSFER" -> {
                String recipient = JsonLite.getString(args, "recipient", "").trim();
                long amount = JsonLite.getLong(args, "amount", 0L);
                BankAccountManager.BankResult res = executeNamedTransfer(bank, player.getUUID(), recipient, amount, now);
                if (res.success()) {
                    data.setDirty();
                    statusComp = Component.translatable("status.mnera.atm.transferred", amount, recipient);
                } else if (res.errorKey() != null) {
                    statusComp = Component.translatable(res.errorKey());
                }
            }
            default -> {
            }
        }

        if (statusComp != null) {
            TyC2Native.sendActionBar(player, statusComp.withStyle(ChatFormatting.AQUA));
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
            return new BankAccountManager.BankResult(false, "status.mnera.atm.invalid_recipient", null, 0L);
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
        return new BankAccountManager.BankResult(false, "status.mnera.atm.invalid_recipient", null, 0L);
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
        if (!(player.containerMenu instanceof CooperationHubMenu hubMenu) || !pos.equals(hubMenu.blockPos())) {
            return;
        }
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

        if (!"REQUEST_SYNC".equals(action) && !canAccessVillage(player, data, village)) {
            TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.not_owner").withStyle(ChatFormatting.RED));
            sendHubSync(player, level, pos, hub, args);
            return;
        }

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
                TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.not_owner").withStyle(ChatFormatting.RED));
                return;
            }
            if (!TyC2Native.dimensionId(level).equals(village.getDimensionId())) {
                TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.wrong_dimension").withStyle(ChatFormatting.RED));
                return;
            }
            if (!level.getBlockState(pos).is(TyC2Registries.COOPERATION_HUB_BLOCK.get())
                    || !level.removeBlock(pos, false)) {
                return;
            }
            village.setHubDeployed(false);
            data.setDirty();
            ItemStack packed = new ItemStack(TyC2Registries.COOPERATION_HUB_ITEM.get());
            CooperationHubBlockItem.setBoundVillage(packed, village.getVillageId(), village.getVillageName());
            if (!player.getInventory().add(packed)) {
                player.drop(packed, false);
            }
            player.closeContainer();
            TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.relocated").withStyle(ChatFormatting.GREEN));
            return;
        }
        if ("TERMINATE_PARTNERSHIP".equals(action)) {
            if (!canManageVillage(player, data, village)) {
                TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.not_owner").withStyle(ChatFormatting.RED));
                return;
            }
            String vName = village.getVillageName();
            BankAccountManager bank = data.bank();
            BankAccountManager.AccountRef fallbackRef = bank.resolveSettlementAccount(player.getUUID());
            long now = level.getGameTime();
            for (AidConstructionProject proj : new ArrayList<>(village.getAidProjects())) {
                if (proj.reservedZone() != null) {
                    village.removeOccupiedZone(proj.reservedZone());
                }
                if (proj.canCancel() && proj.paidIc() > 0L) {
                    BankAccountManager.AccountRef refundRef = resolveAidRefundAccount(bank, proj, fallbackRef);
                    bank.creditAccount(
                            refundRef,
                            proj.paidIc(),
                            BankAccountManager.TransactionType.SYSTEM_ADJUST,
                            now,
                            vName,
                            "Aid Refund (Termination)"
                    );
                    data.economy().refundBankMoneySink(proj.paidIc());
                } else if (!proj.canCancel()) {
                    // Already BUILDING: count toward per-profession aid cap so termination cannot reset build caps
                    village.incrementCompletedAidCount(proj.professionId());
                }
            }
            village.clearAidProjects();
            village.clearConstructionQueue();
            village.setChunkLoaderInstalled(false);
            long overworldGameTime = level.getServer().overworld().getGameTime();
            village.terminatePartnership(overworldGameTime);
            syncMacroAccountTotals(data);
            data.setDirty();
            if (level.getBlockState(pos).is(TyC2Registries.COOPERATION_HUB_BLOCK.get())) {
                BlockState bell = Blocks.BELL.defaultBlockState();
                if (bell.canSurvive(level, pos)) {
                    level.setBlock(pos, bell, Block.UPDATE_ALL);
                } else {
                    level.removeBlock(pos, false);
                }
            }
            player.closeContainer();
            TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.partnership_terminated", vName).withStyle(ChatFormatting.YELLOW));
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
        if (!(player.containerMenu instanceof CommTerminalMenu termMenu)) {
            return;
        }
        ItemStack term = termMenu.isOffhand() ? player.getOffhandItem() : player.getMainHandItem();
        if (!(term.getItem() instanceof CommTerminalItem)) {
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
        if (!"REQUEST_SYNC".equals(action) && !canAccessVillage(player, data, villageOpt.get())) {
            TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.not_owner").withStyle(ChatFormatting.RED));
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
                String orderId = JsonLite.getString(args, "orderId", "");
                int qty = Math.max(1, JsonLite.getInt(args, "qty", 1));
                Optional<VillageTradeOrder> orderOpt = village.findOrder(orderId);
                if (orderOpt.isEmpty() || orderOpt.get().side() != OrderSide.VILLAGE_SELLS || orderOpt.get().isCompleted()) {
                    return;
                }
                VillageTradeOrder order = orderOpt.get();
                int actualQty = Math.min(qty, order.remainingQty());
                long totalCost = order.totalPriceFor(actualQty);
                String nbtMeta = order.enchantSpec().isBlank() ? "" : ("enchant:" + order.enchantSpec());
                int maxStack = order.enchantSpec().isBlank()
                        ? Math.max(1, TyC2Native.stack(order.itemId(), 1).getMaxStackSize())
                        : 1;

                // Validate Outbound capacity & player funds BEFORE deducting Comm Terminal 50 EU
                if (!village.getStorage().canFitInOutbound(order.itemId(), actualQty, nbtMeta, maxStack)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.outbound_full").withStyle(ChatFormatting.RED));
                    return;
                }
                if (useCash && hub != null) {
                    if (!canAffordCash(player, hub, totalCost)) {
                        TyC2Native.sendActionBar(player, Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED));
                        return;
                    }
                } else if (bank.getBalance(activeRef) < totalCost) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED));
                    return;
                }
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_TRADE)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                if (useCash && hub != null && !consumeCashPayment(player, hub, totalCost)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED));
                    return;
                }

                VillageRecord.TradeExecutionResult res = village.executeSellOrderToOutbound(
                        orderId, actualQty, nbtMeta, maxStack
                );
                if (!res.success()) {
                    if (useCash && hub != null) {
                        giveChangeCoins(player, totalCost);
                    }
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.outbound_full").withStyle(ChatFormatting.RED));
                    return;
                }
                if (useCash && hub != null) {
                    data.economy().recordCashPaidToVillage(res.totalCurrency());
                } else {
                    bank.debitAccount(activeRef, res.totalCurrency(), BankAccountManager.TransactionType.TRADE_BUY, now, village.getVillageName(), order.itemId());
                }
                long basePrice = TyC2DataLoader.goodsCatalog()
                        .findOrderGood(order.displayHint(), order.itemId(), false)
                        .map(g -> g.basePrice())
                        .orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, false);
                data.setDirty();
                TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.order_fulfilled", res.totalCurrency()).withStyle(ChatFormatting.GREEN));
            }
            case "SELL_ORDER" -> {
                // Player delivers to Village Buy Order -> consumes from Inbound 0..499 (or player inv at Hub), pays player!
                String orderId = JsonLite.getString(args, "orderId", "");
                int qty = Math.max(1, JsonLite.getInt(args, "qty", 64));
                Optional<VillageTradeOrder> orderOpt = village.findOrder(orderId);
                if (orderOpt.isEmpty() || orderOpt.get().side() != OrderSide.VILLAGE_BUYS || orderOpt.get().isCompleted()) {
                    return;
                }
                VillageTradeOrder order = orderOpt.get();

                // 1. Check village affordability FIRST before pulling player items or deducting Terminal EU
                long unitPrice = Math.max(1L, order.unitPrice());
                int affordableQty = (int) Math.min(Integer.MAX_VALUE, village.getVillageBalance() / unitPrice);
                if (affordableQty <= 0) {
                    TyC2Native.sendActionBar(
                            player,
                            Component.translatable(
                                    "status.mnera.hub.village_low_funds_detail",
                                    village.getVillageBalance(),
                                    order.unitPrice()
                            ).withStyle(ChatFormatting.RED)
                    );
                    return;
                }

                // 2. Cap delivery to what the village can afford & still needs, with tag-aware item matching
                int targetQty = Math.min(Math.min(qty, order.remainingQty()), affordableQty);
                Predicate<SlotStack> slotMatcher = buildOrderSlotMatcher(order);
                Predicate<String> idMatcher = buildOrderIdMatcher(order);

                if (!isTerminal) {
                    int currentIn = village.getStorage().countInInbound(slotMatcher);
                    if (currentIn < targetQty) {
                        transferMatchingFromPlayerToInbound(player, level, village, idMatcher, targetQty - currentIn);
                    }
                }

                // 3. Verify matching Inbound stock > 0 BEFORE deducting Terminal 50 EU
                if (village.getStorage().countInInbound(slotMatcher) <= 0) {
                    TyC2Native.sendActionBar(
                            player,
                            Component.translatable(
                                    isTerminal ? "status.mnera.terminal.insufficient_goods" : "status.mnera.hub.insufficient_goods"
                            ).withStyle(ChatFormatting.RED)
                    );
                    return;
                }

                // 4. Deduct Terminal EU only when settlement is ready to execute
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_TRADE)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }

                VillageRecord.TradeExecutionResult res = village.executeBuyOrderFromInboundMatching(
                        orderId, targetQty, slotMatcher
                );
                if (!res.success()) {
                    if (res.failureCode() == VillageRecord.TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS) {
                        TyC2Native.sendActionBar(
                                player,
                                Component.translatable(
                                        isTerminal ? "status.mnera.terminal.insufficient_goods" : "status.mnera.hub.insufficient_goods"
                                ).withStyle(ChatFormatting.RED)
                        );
                    } else if (res.failureCode() == VillageRecord.TradeFailureCode.INSUFFICIENT_VILLAGE_FUNDS) {
                        TyC2Native.sendActionBar(
                                player,
                                Component.translatable(
                                        "status.mnera.hub.village_low_funds_detail",
                                        village.getVillageBalance(),
                                        order.unitPrice()
                                ).withStyle(ChatFormatting.RED)
                        );
                    }
                    return;
                }
                if (useCash && hub != null) {
                    giveChangeCoins(player, res.totalCurrency());
                    data.economy().recordVillagePaidCash(res.totalCurrency());
                } else {
                    bank.creditAccount(activeRef, res.totalCurrency(), BankAccountManager.TransactionType.TRADE_SELL, now, village.getVillageName(), order.itemId());
                }
                long basePrice = TyC2DataLoader.goodsCatalog()
                        .findOrderGood(order.displayHint(), order.itemId(), true)
                        .map(g -> g.basePrice())
                        .orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
                data.setDirty();
                TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.order_fulfilled", res.totalCurrency()).withStyle(ChatFormatting.GREEN));
            }
            case "TOGGLE_ORDER_AUTO" -> {
                String orderId = JsonLite.getString(args, "orderId", "");
                village.findOrder(orderId).ifPresent(o -> {
                    village.updateOrder(o.withAutoFulfill(!o.autoFulfill()));
                    data.setDirty();
                });
            }
            case "DELIVER_EXPANSION" -> {
                ExpansionDesire desire = village.getActiveExpansion();
                if (desire == null) {
                    return;
                }
                if (desire.allMaterialsDelivered() && village.getVillageBalance() < desire.constructionBudgetCost()) {
                    TyC2Native.sendActionBar(
                            player,
                            Component.translatable(
                                    "status.mnera.expansion.low_funds",
                                    desire.constructionBudgetCost(),
                                    village.getVillageBalance()
                            ).withStyle(ChatFormatting.RED)
                    );
                    return;
                }
                if (isTerminal && !desire.allMaterialsDelivered()) {
                    boolean anyInboundMatch = false;
                    for (Map.Entry<String, Integer> rem : desire.remainingMaterials().entrySet()) {
                        if (rem.getValue() > 0 && ExpansionDesire.countAvailableInInbound(
                                village.getStorage(), rem.getKey(), TyC2Native::itemIdHasTag) > 0) {
                            anyInboundMatch = true;
                            break;
                        }
                    }
                    if (!anyInboundMatch) {
                        TyC2Native.sendActionBar(
                                player,
                                Component.translatable(
                                        "status.mnera.expansion.missing_materials",
                                        buildMissingMaterialsComponent(desire)
                                ).withStyle(ChatFormatting.RED)
                        );
                        return;
                    }
                }
                if (isTerminal && !CommTerminalItem.consumeActionEu(terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!isTerminal) {
                    for (Map.Entry<String, Integer> rem : desire.remainingMaterials().entrySet()) {
                        transferExpansionMaterialFromPlayerToInbound(player, level, village, rem.getKey(), rem.getValue());
                    }
                }
                ExpansionDesire updated = desire.pullAvailableFromInbound(village.getStorage(), TyC2Native::itemIdHasTag);
                boolean deliveredAny = !updated.equals(desire);
                if (deliveredAny) {
                    village.setActiveExpansion(updated);
                    data.setDirty();
                }
                if (updated.allMaterialsDelivered()) {
                    tryLaunchExpansionFromDesire(level, data, village, now, player);
                } else {
                    MutableComponent missingComp = buildMissingMaterialsComponent(updated);
                    if (deliveredAny) {
                        TyC2Native.sendActionBar(
                                player,
                                Component.translatable("status.mnera.expansion.delivered_partial", missingComp)
                                        .withStyle(ChatFormatting.YELLOW)
                        );
                    } else {
                        TyC2Native.sendActionBar(
                                player,
                                Component.translatable("status.mnera.expansion.missing_materials", missingComp)
                                        .withStyle(ChatFormatting.RED)
                        );
                    }
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
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.terminal.low_power").withStyle(ChatFormatting.RED));
                    return;
                }
                long sink = village.tryUpgrade(TyC2DataLoader.levelTable());
                if (sink > 0L) {
                    data.economy().recordVillageMoneySink(sink);
                    refreshVillageOrders(level, data, village, overworldDayTime(level));
                    data.setDirty();
                    Component lvlName = Component.translatable("level.mnera." + village.getLevel());
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.upgraded", village.getVillageName(), village.getLevel(), lvlName).withStyle(ChatFormatting.GOLD));
                } else {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.upgrade_failed").withStyle(ChatFormatting.RED));
                }
            }
            case "TOGGLE_PAUSE_EXPANSION" -> {
                village.setExpansionPaused(!village.isExpansionPaused());
                data.setDirty();
            }
            case "RENAME_VILLAGE" -> {
                if (!canManageVillage(player, data, village)) {
                    TyC2Native.sendActionBar(player, Component.translatable("status.mnera.hub.not_owner").withStyle(ChatFormatting.RED));
                    return;
                }
                String newName = JsonLite.getString(args, "name", "").trim();
                if (!newName.isBlank() && newName.length() <= 32) {
                    village.setVillageName(newName);
                    data.setDirty();
                }
            }
            case "START_AID" -> handleStartAid(
                    player, level, data, village, hub, args, useCash, isTerminal, terminalStack, now, bank, activeRef
            );
            case "CANCEL_AID" -> handleCancelAid(
                    player, data, village, args, isTerminal, now, bank, activeRef
            );
            default -> {
            }
        }
    }

    private static void handleStartAid(
            ServerPlayer player,
            ServerLevel level,
            TycoonSavedData data,
            VillageRecord village,
            CooperationHubBlockEntity hub,
            Map<String, Object> args,
            boolean useCash,
            boolean isTerminal,
            ItemStack terminalStack,
            long now,
            BankAccountManager bank,
            BankAccountManager.AccountRef activeRef
    ) {
        if (!village.isPartnered()) {
            return;
        }
        if (village.getMayorState().isPausedDueToDepopulation()) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("gui.mnera.hub.paused_depopulated").withStyle(ChatFormatting.RED)
            );
            return;
        }
        TyC2Config cfg = TyC2DataLoader.config();
        if (village.getAidProjects().size() >= cfg.maxQueuedAidProjects()) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.aid.queue_full", cfg.maxQueuedAidProjects())
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        String professionId = JsonLite.getString(args, "professionId", "").trim();
        Optional<WorkplaceSpec> specOpt = TyC2DataLoader.workplaceCatalog().getByProfessionId(professionId);
        if (specOpt.isEmpty()
                || !specOpt.get().enabled()
                || !TyC2Native.blockExists(specOpt.get().workstationBlockId())) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.aid.unknown_profession", professionId)
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }
        WorkplaceSpec spec = specOpt.get();
        WorkplaceSize size = WorkplaceSize.fromNameOrDefault(
                JsonLite.getString(args, "size", "small"), WorkplaceSize.SMALL
        );
        if (!spec.isUnlockedAtLevel(village.getLevel(), size)) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.aid.level_locked", spec.minVillageLevel(size))
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }
        if (!village.canStartAidForProfession(spec.professionId())) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable(
                            "status.mnera.aid.profession_limit",
                            WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION
                    ).withStyle(ChatFormatting.RED)
            );
            return;
        }

        int priorBuilds = village.getTotalAidCountForProfession(spec.professionId());
        long costIc = WorkplaceCatalog.quoteCostIc(spec, size, data.economy().computeCpi(), cfg, priorBuilds);
        boolean payCash = useCash && hub != null && !isTerminal;

        // Verify funds BEFORE site preview / Terminal 100 EU deduction
        if (payCash) {
            if (!canAffordCash(player, hub, costIc)) {
                TyC2Native.sendActionBar(
                        player,
                        Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED)
                );
                return;
            }
        } else if (bank.getBalance(activeRef) < costIc) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED)
            );
            return;
        }

        // If target village Hub chunk IS loaded, validate & pre-reserve site BEFORE charging IC or Terminal EU!
        ServerLevel targetLevel = resolveDimensionLevel(level.getServer(), village.getDimensionId(), level);
        BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
        Aabb3i reservedZone = null;
        if (targetLevel.hasChunkAt(hubPos)) {
            LevelTerrainProbe probe = new LevelTerrainProbe(targetLevel, data);
            StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(spec, size, village.getBiomeStyle());
            ExpansionPlanner.PlanResult preview = PLANNER.planBlueprint(village, bp, probe, "aid_preview", true);
            if (!preview.success()) {
                TyC2Native.sendActionBar(
                        player,
                        Component.translatable("status.mnera.aid.no_site").withStyle(ChatFormatting.RED)
                );
                return;
            }
            reservedZone = preview.reservedZone();
        }

        // Deduct Terminal 100 EU only after all preconditions & loaded-site check succeeded
        if (isTerminal && !CommTerminalItem.consumeActionEu(
                terminalStack, player, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.terminal.low_power").withStyle(ChatFormatting.RED)
            );
            return;
        }

        // Deduct IC and record matching bank vs cash deflationary money sink so M stays exact
        if (payCash) {
            if (!consumeCashPayment(player, hub, costIc)) {
                TyC2Native.sendActionBar(
                        player,
                        Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED)
                );
                return;
            }
            data.economy().recordMoneySink(costIc);
        } else {
            BankAccountManager.BankResult deb = bank.debitAccount(
                    activeRef,
                    costIc,
                    BankAccountManager.TransactionType.TRADE_BUY,
                    now,
                    village.getVillageName(),
                    "Aid " + spec.professionId()
            );
            if (!deb.success()) {
                TyC2Native.sendActionBar(
                        player,
                        Component.translatable("status.mnera.atm.insufficient_balance").withStyle(ChatFormatting.RED)
                );
                return;
            }
            data.economy().recordBankMoneySink(costIc);
        }

        if (reservedZone != null) {
            village.addOccupiedZone(reservedZone);
        }
        String projectId = "aid_" + now + "_" + (village.getAidProjects().size() + 1) + "_" + Math.abs(RNG.nextInt(10000));
        String payerCo = (!payCash && activeRef.scope() == BankAccountManager.AccountScope.COMPANY)
                ? activeRef.accountId()
                : null;
        AidConstructionProject project = AidConstructionProject.create(
                projectId,
                spec,
                size,
                village.getBiomeStyle(),
                costIc,
                size.gatherTicks(cfg),
                reservedZone,
                player.getUUID(),
                payerCo,
                payCash
        );
        village.addAidProject(project);
        syncMacroAccountTotals(data);
        data.setDirty();

        TyC2Native.sendActionBar(
                player,
                Component.translatable(
                        "status.mnera.aid.started",
                        village.getVillageName(),
                        workplaceDisplayComponent(spec, size),
                        costIc
                ).withStyle(ChatFormatting.GREEN)
        );
    }

    private static void handleCancelAid(
            ServerPlayer player,
            TycoonSavedData data,
            VillageRecord village,
            Map<String, Object> args,
            boolean isTerminal,
            long now,
            BankAccountManager bank,
            BankAccountManager.AccountRef activeRef
    ) {
        String projectId = JsonLite.getString(args, "projectId", "").trim();
        Optional<AidConstructionProject> projOpt = village.findAidProject(projectId);
        if (projOpt.isEmpty()) {
            return;
        }
        AidConstructionProject proj = projOpt.get();
        boolean isPayer = player.getUUID().equals(proj.payerPlayerUuid());
        if (!isPayer && !canManageVillage(player, data, village)) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.hub.not_owner").withStyle(ChatFormatting.RED)
            );
            return;
        }
        if (!proj.canCancel()) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.aid.cannot_cancel").withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (proj.reservedZone() != null) {
            village.removeOccupiedZone(proj.reservedZone());
        }
        village.removeAidProject(proj.projectId());

        long refundIc = proj.paidIc();
        if (refundIc > 0L) {
            boolean refundToCash = proj.paidWithCash() && !isTerminal && isPayer;
            if (refundToCash) {
                giveChangeCoins(player, refundIc);
            } else {
                BankAccountManager.AccountRef refundRef = resolveAidRefundAccount(bank, proj, activeRef);
                bank.creditAccount(
                        refundRef,
                        refundIc,
                        BankAccountManager.TransactionType.SYSTEM_ADJUST,
                        now,
                        village.getVillageName(),
                        "Aid Refund " + proj.professionId()
                );
            }
            data.economy().refundMoneySink(refundIc, refundToCash);
        }
        syncMacroAccountTotals(data);
        data.setDirty();

        TyC2Native.sendActionBar(
                player,
                Component.translatable(
                        "status.mnera.aid.cancelled",
                        workplaceDisplayComponent(proj),
                        refundIc
                ).withStyle(ChatFormatting.YELLOW)
        );
    }

    private static BankAccountManager.AccountRef resolveAidRefundAccount(
            BankAccountManager bank,
            AidConstructionProject proj,
            BankAccountManager.AccountRef fallbackRef
    ) {
        if (proj.payerCompanyId() != null && bank.getCompany(proj.payerCompanyId()).isPresent()) {
            return BankAccountManager.AccountRef.company(proj.payerCompanyId());
        }
        if (proj.payerPlayerUuid() != null) {
            bank.getOrCreatePersonal(proj.payerPlayerUuid(), null);
            return BankAccountManager.AccountRef.personal(proj.payerPlayerUuid());
        }
        return fallbackRef;
    }

    /**
     * OP command helper ({@code /tyc2 aid <profession> [small|large]}) that queues an aid workplace
     * directly on {@code village} without charging player currency (money supply remains unchanged).
     *
     * @return empty on success, or localized/plain failure message
     */
    public static Optional<Component> forceStartAid(
            ServerLevel level,
            VillageRecord village,
            String professionArg,
            WorkplaceSize size,
            UUID actorUuid
    ) {
        if (!village.isPartnered()) {
            return Optional.of(Component.literal("Village " + village.getVillageName() + " is not partnered."));
        }
        TyC2Config cfg = TyC2DataLoader.config();
        if (village.getAidProjects().size() >= cfg.maxQueuedAidProjects()) {
            return Optional.of(Component.translatable("status.mnera.aid.queue_full", cfg.maxQueuedAidProjects()));
        }
        WorkplaceCatalog catalog = TyC2DataLoader.workplaceCatalog();
        String norm = professionArg == null ? "" : professionArg.trim().toLowerCase(Locale.ROOT);
        Optional<WorkplaceSpec> specOpt = catalog.getByProfessionId(norm);
        if (specOpt.isEmpty() && !norm.contains(":")) {
            specOpt = catalog.getByProfessionId("minecraft:" + norm);
        }
        if (specOpt.isEmpty()
                || !specOpt.get().enabled()
                || !TyC2Native.blockExists(specOpt.get().workstationBlockId())) {
            return Optional.of(Component.translatable("status.mnera.aid.unknown_profession", professionArg));
        }
        WorkplaceSpec spec = specOpt.get();
        TycoonSavedData data = getData(level);
        ServerLevel targetLevel = resolveDimensionLevel(level.getServer(), village.getDimensionId(), level);
        BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
        Aabb3i reservedZone = null;
        if (targetLevel.hasChunkAt(hubPos)) {
            LevelTerrainProbe probe = new LevelTerrainProbe(targetLevel, data);
            StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(spec, size, village.getBiomeStyle());
            ExpansionPlanner.PlanResult preview = PLANNER.planBlueprint(village, bp, probe, "aid_cmd_preview", true);
            if (!preview.success()) {
                return Optional.of(Component.translatable("status.mnera.aid.no_site"));
            }
            reservedZone = preview.reservedZone();
        }
        if (reservedZone != null) {
            village.addOccupiedZone(reservedZone);
        }
        long now = level.getGameTime();
        String projectId = "aid_cmd_" + now + "_" + (village.getAidProjects().size() + 1) + "_" + Math.abs(RNG.nextInt(10000));
        AidConstructionProject project = AidConstructionProject.create(
                projectId,
                spec,
                size,
                village.getBiomeStyle(),
                0L,
                size.gatherTicks(cfg),
                reservedZone,
                actorUuid,
                null,
                false
        );
        village.addAidProject(project);
        syncMacroAccountTotals(data);
        data.setDirty();
        return Optional.empty();
    }

    /** Returns a localized display Component for a workplace spec and size. */
    public static Component workplaceDisplayComponent(WorkplaceSpec spec, WorkplaceSize size) {
        return workplaceDisplayComponent(spec.archetype(), spec.displayNameFallback(), size);
    }

    /** Returns a localized display Component for an active aid construction project. */
    public static Component workplaceDisplayComponent(AidConstructionProject proj) {
        return workplaceDisplayComponent(proj.archetype(), proj.displayNameFallback(), proj.size());
    }

    private static Component workplaceDisplayComponent(
            WorkplaceArchetype archetype,
            String displayNameFallback,
            WorkplaceSize size
    ) {
        MutableComponent profComp = (archetype != null && archetype != WorkplaceArchetype.GENERIC)
                ? Component.translatable(archetype.langKey())
                : (displayNameFallback != null && !displayNameFallback.isBlank()
                        ? Component.literal(displayNameFallback)
                        : Component.translatable(WorkplaceArchetype.GENERIC.langKey()));
        Component sizeComp = Component.translatable(
                size == WorkplaceSize.LARGE ? "gui.mnera.aid.size.large" : "gui.mnera.aid.size.small"
        );
        return profComp.append(" ").append(sizeComp);
    }

    /** Returns a localized status Component for {@code proj} (shared by HUD bridges & commands). */
    public static Component aidStatusComponent(VillageRecord v, AidConstructionProject proj) {
        return switch (proj.status()) {
            case COLLECTING -> Component.translatable(
                    "gui.mnera.aid.status.collecting",
                    (int) Math.round(proj.completionRatio() * 100.0)
            );
            case WAITING_SITE -> Component.translatable("gui.mnera.aid.status.waiting_site");
            case NO_SITE -> Component.translatable("gui.mnera.aid.status.no_site");
            case BUILDING -> {
                int pct = v.getConstructionQueue().stream()
                        .filter(j -> proj.plannedJobId().equals(j.getJobId()))
                        .findFirst()
                        .map(j -> (int) Math.round(j.getProgressRatio() * 100.0))
                        .orElse(0);
                yield Component.translatable("gui.mnera.aid.status.building", pct);
            }
        };
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
        if (!(player.containerMenu instanceof MayorNegotiationMenu negMenu)
                || !villageId.equals(negMenu.villageId())) {
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
        if (!TyC2Native.dimensionId(level).equals(village.getDimensionId())) {
            return;
        }
        UUID mayorUuid = negMenu.mayorEntityUuid() != null
                ? negMenu.mayorEntityUuid()
                : village.getMayorState().getMayorEntityUuid();
        Entity mayorEntity = mayorUuid != null ? level.getEntity(mayorUuid) : null;
        if (mayorEntity == null || !mayorEntity.isAlive() || mayorEntity.distanceToSqr(player) > 100.0) {
            return;
        }

        TyC2Config cfg = TyC2DataLoader.config();
        long dayTime = overworldDayTime(level);
        if (village.normalizeDemandRefreshAnchor(dayTime)) {
            data.setDirty();
        }
        int refreshTicks = Math.max(200, cfg.demandRefreshTicks());
        long overworldGameTime = level.getServer().overworld().getGameTime();
        long resignCooldown = village.remainingResignCooldownTicks(overworldGameTime, refreshTicks);
        if (resignCooldown > 0L) {
            Component cooldownMsg = Component.translatable(
                    "status.mnera.negotiation.resign_cooldown",
                    formatRemainingMcTime(resignCooldown)
            ).withStyle(ChatFormatting.RED);
            TyC2Native.sendActionBar(player, cooldownMsg);
            player.sendSystemMessage(cooldownMsg);
            sendMayorSync(player, level, village);
            return;
        }

        NegotiationCheck check = evaluateNegotiation(player, data, cfg);
        if (!check.allMet()) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.mnera.negotiation.missing_requirements").withStyle(ChatFormatting.RED)
            );
            sendMayorSync(player, level, village);
            return;
        }

        // 1. Deduct startup currency (prefer bank account, else physical cash in inventory with exact change)
        long costIc = cfg.negotiationCostIc();
        if (costIc > 0L) {
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            if (data.bank().getBalance(ref) >= costIc) {
                data.bank().debitAccount(
                        ref, costIc, BankAccountManager.TransactionType.NEGOTIATION,
                        level.getGameTime(), village.getVillageName(), "Village Partnership"
                );
            } else {
                long collected = consumeCoinsFromInventory(player, costIc);
                if (collected > costIc) {
                    giveChangeCoins(player, collected - costIc);
                }
                data.economy().recordCashPaidToVillage(costIc);
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
        village.establishPartnershipWithWorkingCapital(
                player.getUUID(),
                activeCo,
                costIc,
                cfg.initialVillageWorkingCapital()
        );
        village.setHubDeployed(true);
        village.anchorStipendIfUnset(dayTime);

        ServerLevel targetLevel = resolveDimensionLevel(level.getServer(), village.getDimensionId(), level);
        BlockPos hubPos = new BlockPos(village.getHubPos().x(), village.getHubPos().y(), village.getHubPos().z());
        targetLevel.setBlock(hubPos, TyC2Registries.COOPERATION_HUB_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (targetLevel.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity be) {
            be.setVillageId(village.getVillageId());
        }

        // Register initial Hub core zone & existing village houses so expansion stays cleanly outside
        village.addOccupiedZone(Aabb3i.singleBlock(village.getHubPos()).padHorizontal(4));
        registerExistingVillageZones(targetLevel, village, hubPos);
        if (SupplyCycleClock.isRefreshDue(
                dayTime, village.getLastDemandRefreshTick(), refreshTicks, !village.hasInitialOrdersGenerated())) {
            refreshVillageOrders(targetLevel, data, village, dayTime);
        }
        syncMacroAccountTotals(data);
        data.setDirty();

        endAllNegotiationsForPlayer(player);
        player.closeContainer();
        player.sendSystemMessage(
                Component.translatable("status.mnera.hub.partnership_signed", village.getVillageName())
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
                    // Inventory.add mutates 'out' in-place on partial insertion; return ONLY leftovers!
                    if (!out.isEmpty()) {
                        SlotStack remaining = TyC2Native.toSlotStack(out, registries);
                        if (remaining != null) {
                            storage.setSlotRaw(slot, remaining);
                        }
                    }
                    data.setDirty();
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
                    .anyMatch(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted() && buildOrderIdMatcher(o).test(id));
            if (!needed && village.getActiveExpansion() != null) {
                needed = village.getActiveExpansion().remainingMaterials().keySet().stream()
                        .anyMatch(reqId -> ExpansionDesire.matchesMaterial(id, reqId, TyC2Native::itemIdHasTag));
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

    private static void transferExpansionMaterialFromPlayerToInbound(
            ServerPlayer player,
            ServerLevel level,
            VillageRecord village,
            String requiredItemId,
            int maxCount
    ) {
        // Exact item ID first, then controlled common-tag substitutes for any remaining shortfall
        int movedExact = transferMatchingFromPlayerToInbound(
                player, level, village, id -> requiredItemId.equals(id), maxCount
        );
        int remaining = maxCount - movedExact;
        if (remaining > 0 && ExpansionDesire.optionalTagForMaterial(requiredItemId) != null) {
            transferMatchingFromPlayerToInbound(
                    player,
                    level,
                    village,
                    id -> !requiredItemId.equals(id)
                            && ExpansionDesire.matchesMaterial(id, requiredItemId, TyC2Native::itemIdHasTag),
                    remaining
            );
        }
    }

    private static int transferMatchingFromPlayerToInbound(
            ServerPlayer player,
            ServerLevel level,
            VillageRecord village,
            Predicate<String> itemIdMatcher,
            int maxCount
    ) {
        int remaining = maxCount;
        var registries = level.registryAccess();
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty() || !itemIdMatcher.test(TyC2Native.itemId(s))) {
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
        return maxCount - remaining;
    }

    private static String resolveOrderOptionalTag(VillageTradeOrder order) {
        if (order.optionalTag() != null && !order.optionalTag().isBlank()) {
            return order.optionalTag();
        }
        return TyC2DataLoader.goodsCatalog()
                .findOrderGood(order.displayHint(), order.itemId(), order.side() == OrderSide.VILLAGE_BUYS)
                .map(g -> g.optionalTag())
                .orElse(null);
    }

    private static Predicate<String> buildOrderIdMatcher(VillageTradeOrder order) {
        String exactId = order.itemId();
        String optTag = resolveOrderOptionalTag(order);
        if (optTag == null || optTag.isBlank()) {
            return id -> exactId.equals(id);
        }
        return id -> exactId.equals(id) || matchesTagSpec(id, optTag);
    }

    private static Predicate<SlotStack> buildOrderSlotMatcher(VillageTradeOrder order) {
        Predicate<String> idMatcher = buildOrderIdMatcher(order);
        return ss -> ss != null && ss.count() > 0 && idMatcher.test(ss.itemId());
    }

    private static boolean matchesTagSpec(String itemId, String tagSpec) {
        return CommonTagAliases.matchesTagSpec(itemId, tagSpec, TyC2Native::itemIdHasTag);
    }

    private static Container resolveHubCashBox(ServerPlayer player, CooperationHubBlockEntity hub) {
        if (player != null && player.containerMenu instanceof CooperationHubMenu hubMenu) {
            return hubMenu.cashContainer();
        }
        return hub.localContainer();
    }

    private static int resolveHubCashSlotIndex(ServerPlayer player) {
        return (player != null && player.containerMenu instanceof CooperationHubMenu)
                ? 0
                : CooperationHubBlockEntity.LOCAL_SLOT_CASH;
    }

    private static boolean canAffordCash(ServerPlayer player, CooperationHubBlockEntity hub, long requiredIc) {
        Container cashBox = resolveHubCashBox(player, hub);
        ItemStack cashSlot = cashBox.getItem(resolveHubCashSlotIndex(player));
        long slotVal = CurrencyDenomination.valueOfItemId(TyC2Native.itemId(cashSlot))
                .map(d -> d.value() * cashSlot.getCount()).orElse(0L);
        return slotVal + countCoinsInInventory(player) >= requiredIc;
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
                vm.put("biome", rec.getBiomeStyle().id());
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
                vm.put("biome", "plains");
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

        VillageEmploymentStats es = v.getEmploymentStats();
        TyC2Config cfg = TyC2DataLoader.config();
        int refreshTicks = Math.max(200, cfg.demandRefreshTicks());
        long refreshRemainingTicks = SupplyCycleClock.remainingRefreshTicks(
                overworldDayTime(level), v.getLastDemandRefreshTick(), refreshTicks
        );
        root.put("refreshRemainingTicks", refreshRemainingTicks);
        root.put("refreshTotalTicks", refreshTicks);

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
            om.put("biomeSpecialty", o.biomeSpecialty());
            String optTag = resolveOrderOptionalTag(o);
            om.put("optionalTag", optTag == null ? "" : CommonTagAliases.displayTagLabel(optTag, false));
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
                String nbt = st.get().nbtJson();
                if (nbt != null && !nbt.isBlank()) {
                    String hint = nbt.startsWith("enchant:") ? nbt.substring("enchant:".length()) : "NBT";
                    if (hint.length() > 48) {
                        hint = hint.substring(0, 48);
                    }
                    sm.put("metaHint", hint);
                }
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

        // Employment stats & low-unemployment bonus (order quantity boost only; weekly refresh interval stays fixed)
        int unempPct = (int) Math.round(es.unemploymentRate() * 100.0);
        boolean bonusActive = es.isLowUnemploymentBonusActive(cfg);
        int restockBonusPct = 0;
        int qtyBonusPct = bonusActive
                ? (int) Math.round((es.tradeQuantityMultiplier(cfg) - 1.0) * 100.0)
                : 0;
        root.put("workforceCount", es.workforceCount());
        root.put("employedCount", es.employedCount());
        root.put("unemployedCount", es.unemployedCount());
        root.put("unemploymentPct", unempPct);
        root.put("bonusActive", bonusActive);
        root.put("restockBonusPct", restockBonusPct);
        root.put("qtyBonusPct", qtyBonusPct);
        root.put("gatherSpeedMult", es.gatherSpeedMultiplier(cfg));
        root.put("lowUnempThresholdPct", (int) Math.round(cfg.lowUnemploymentThreshold() * 100.0));
        root.put("maxAidQueue", cfg.maxQueuedAidProjects());

        // Available workplaces (with live CPI pricing, repeat surcharge, and profession build cap)
        double cpi = econ.computeCpi();
        List<Map<String, Object>> workplaces = new ArrayList<>();
        for (WorkplaceSpec spec : TyC2DataLoader.workplaceCatalog().getAvailableWorkplaces(TyC2Native::blockExists)) {
            int priorBuilds = v.getTotalAidCountForProfession(spec.professionId());
            boolean limitReached = !v.canStartAidForProfession(spec.professionId());
            Map<String, Object> wm = new LinkedHashMap<>();
            wm.put("professionId", spec.professionId());
            wm.put("workstationBlockId", spec.workstationBlockId());
            wm.put("archetype", spec.archetype().name());
            wm.put("displayNameFallback", spec.displayNameFallback());
            wm.put("costSmallIc", WorkplaceCatalog.quoteCostIc(spec, WorkplaceSize.SMALL, cpi, cfg, priorBuilds));
            wm.put("costLargeIc", WorkplaceCatalog.quoteCostIc(spec, WorkplaceSize.LARGE, cpi, cfg, priorBuilds));
            wm.put("smallUnlocked", spec.isUnlockedAtLevel(v.getLevel(), WorkplaceSize.SMALL));
            wm.put("minLevelSmall", spec.minVillageLevelSmall());
            wm.put("largeUnlocked", spec.isUnlockedAtLevel(v.getLevel(), WorkplaceSize.LARGE));
            wm.put("minLevelLarge", spec.minVillageLevelLarge());
            wm.put("aidCount", priorBuilds);
            wm.put("aidMax", WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION);
            wm.put("aidLimitReached", limitReached);
            workplaces.add(wm);
        }
        root.put("workplaces", workplaces);

        // Active & queued aid projects
        List<Map<String, Object>> aidProjects = new ArrayList<>();
        for (AidConstructionProject proj : v.getAidProjects()) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("projectId", proj.projectId());
            am.put("professionId", proj.professionId());
            am.put("workstationBlockId", proj.workstationBlockId());
            am.put("archetype", proj.archetype().name());
            am.put("displayNameFallback", proj.displayNameFallback());
            am.put("size", proj.size().name());
            am.put("status", proj.status().name());
            int pct;
            if (proj.status() == AidConstructionProject.Status.BUILDING) {
                pct = v.getConstructionQueue().stream()
                        .filter(j -> proj.plannedJobId().equals(j.getJobId()))
                        .findFirst()
                        .map(j -> (int) Math.round(j.getProgressRatio() * 100.0))
                        .orElse(0);
            } else {
                pct = (int) Math.round(proj.completionRatio() * 100.0);
            }
            am.put("progressPct", pct);
            am.put("paidIc", proj.paidIc());
            am.put("canCancel", proj.canCancel());
            am.put("requiredMaterials", new LinkedHashMap<>(proj.requiredMaterials()));
            am.put("gatheredMaterials", new LinkedHashMap<>(proj.displayedGatheredMaterials()));
            aidProjects.add(am);
        }
        root.put("aidProjects", aidProjects);

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
        String id = TyC2Native.itemId(stack);
        if (REJECTED_NEGOTIATION_FOODS.contains(id)) {
            return false;
        }
        if (BASIC_NEGOTIATION_FOODS.contains(id)) {
            return true;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && food.nutrition() >= 3;
    }

    private static Component formatRemainingMcTime(long remainingTicks) {
        long clamped = Math.max(0L, remainingTicks);
        long days = clamped / SupplyCycleClock.TICKS_PER_DAY;
        long hours = (clamped % SupplyCycleClock.TICKS_PER_DAY) / 1_000L;
        if (days > 0L && hours > 0L) {
            return Component.translatable("gui.mnera.hub.refresh_days_hours", days, hours);
        }
        if (days > 0L) {
            return Component.translatable("gui.mnera.hub.refresh_days", days);
        }
        return Component.translatable("gui.mnera.hub.refresh_hours", Math.max(1L, hours));
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
        Container cashBox = resolveHubCashBox(player, hub);
        int cashIdx = resolveHubCashSlotIndex(player);
        ItemStack cashSlot = cashBox.getItem(cashIdx);
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
                cashBox.setItem(cashIdx, ItemStack.EMPTY);
            } else {
                cashBox.setChanged();
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

    /**
     * Checks whether {@code player} is permitted to operate {@code village}'s Cooperation Hub,
     * warehouse, orders, aid queue, or remote terminal bindings (owner, partner company member,
     * server OP, or any player while unpartnered).
     */
    public static boolean canAccessVillage(ServerPlayer player, TycoonSavedData data, VillageRecord village) {
        if (player == null || village == null) {
            return false;
        }
        if (!village.isPartnered() || TyC2Native.hasOpPermission(player)) {
            return true;
        }
        if (player.getUUID().equals(village.getOwnerPlayerUuid())) {
            return true;
        }
        if (village.getCompanyAccountId() != null) {
            return data.bank().getCompany(village.getCompanyAccountId())
                    .map(co -> co.isMember(player.getUUID()))
                    .orElse(false);
        }
        return false;
    }

    private static void registerExistingVillageZones(ServerLevel level, VillageRecord village, BlockPos center) {
        int r = 32;
        int minY = Math.max(TyC2Native.minBuildY(level), center.getY() - 8);
        int maxY = Math.min(TyC2Native.maxBuildY(level) - 1, center.getY() + 14);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int registered = 0;
        for (int dx = -r; dx <= r && registered < 24; dx += 2) {
            for (int dz = -r; dz <= r && registered < 24; dz += 2) {
                int wx = center.getX() + dx;
                int wz = center.getZ() + dz;
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY && registered < 24; y++) {
                    cursor.set(wx, y, wz);
                    Block b = level.getBlockState(cursor).getBlock();
                    if (b instanceof BedBlock || b instanceof DoorBlock) {
                        Aabb3i zone = new Aabb3i(wx - 4, y - 1, wz - 4, wx + 4, y + 5, wz + 4);
                        boolean overlapsExisting = village.getOccupiedZones().stream().anyMatch(z -> z.intersects(zone));
                        if (!overlapsExisting) {
                            village.addOccupiedZone(zone);
                            registered++;
                        }
                    }
                }
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
        return "";
    }

    private static MutableComponent buildMissingMaterialsComponent(ExpansionDesire desire) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> rem : desire.remainingMaterials().entrySet()) {
            if (rem.getValue() <= 0) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            first = false;
            ItemStack st = TyC2Native.stack(rem.getKey(), 1);
            Component itemComp = st.isEmpty()
                    ? Component.literal(rem.getKey())
                    : Component.translatable(st.getItem().getDescriptionId());
            out.append(itemComp).append(" ×" + rem.getValue());
        }
        return out;
    }

    private static Component activeAccountLabelComponent(BankAccountManager bank, UUID playerUuid) {
        String label = activeAccountLabel(bank, playerUuid);
        return label.isEmpty() ? Component.translatable("gui.mnera.account.personal") : Component.literal(label);
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

    public static ServerLevel resolveDimensionLevel(MinecraftServer server, String dimId, ServerLevel fallback) {
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
