package com.tycooncraft.tyc2.forge.world;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TradeGood;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.item.TyC2BlockItem;
import com.tycooncraft.tyc2.forge.util.TyC2ChunkTickets;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.util.TyC2VillagerHelpers;
import com.tycooncraft.tyc2.village.Aabb3i;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.ConstructionJob;
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
import com.tycooncraft.tyc2.village.WorkplaceArchitectureCatalog;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.village.WorkplaceSpec;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
import net.minecraft.world.phys.Vec3;
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

    private record ActiveNegotiation(UUID mayorUuid, UUID playerUuid, String dimensionId) {
    }

    /** Active Mayor negotiations keyed by Mayor entity UUID so the Mayor stays stationary and faces the player. */
    private static final Map<UUID, ActiveNegotiation> ACTIVE_NEGOTIATIONS = new ConcurrentHashMap<>();

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

        // 0. Keep actively negotiating Mayors stationary and facing the player every server tick
        tickActiveNegotiations(server);

        // 1. Scan for village Bells / Hubs and update Mayor elections every 100 ticks
        if (gameTime % 100L == 0L) {
            scanAndUpdateVillages(server, data, gameTime);
        }

        // 2. Virtual village economy, demand refresh, upkeep, auto-fulfillment, and Mayor plaza anchoring
        if (gameTime % 20L == 0L) {
            tickVirtualVillages(server, overworld, data, cfg, gameTime);
            tickMayorAnchors(server, data);
        }

        // 3. Gradual collision-safe construction jobs (ticks every tick; ConstructionJob throttles blocks/step)
        tickConstructionJobs(server, data);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endMayorNegotiation(player, null);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
            String dim = level.dimension().location().toString();
            boolean placed = data.findNearestVillage(dim, event.getPos(), 160).isPresent()
                    && data.placedBlocks().markPlaced(dim, event.getPos());
            String blockId = TyC2Ids.blockId(event.getPlacedBlock().getBlock());
            boolean milestone = data.tiers().recordItemMilestone(player.getUUID(), blockId, Ic2Bridge.isLoaded());
            if (placed || milestone) {
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
        if (data.placedBlocks().markRemoved(level.dimension().location().toString(), event.getPos())) {
            data.setDirty();
        }
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
        if (event.getTarget().getClass() != Villager.class) {
            return;
        }
        Villager villager = (Villager) event.getTarget();
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
                    startMayorNegotiation(player, villager);
                    openMayorNegotiationScreen(player, village, villager.getUUID());
                } else {
                    villager.getNavigation().stop();
                    villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
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
        Optional<VillageRecord> nearest = data.findNearestVillage(
                dim, villager.blockPosition(), VillageRecord.MAX_CONFIGURABLE_RADIUS + 12
        );
        if (nearest.isPresent() && nearest.get().isPartnered()) {
            VillageRecord nv = nearest.get();
            int scanRad = nv.effectiveVillagerScanRadius();
            BlockPos hubMc = TyC2Ids.toMc(nv.getHubPos());
            if (Math.abs(villager.blockPosition().getY() - hubMc.getY()) <= 24
                    && villager.blockPosition().distSqr(hubMc) <= (double) scanRad * scanRad) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                player.displayClientMessage(
                        Component.translatable("mayor.tyc2.villager_redirect").withStyle(ChatFormatting.GOLD),
                        true
                );
            }
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
        int scanRadius = village.effectiveVillagerScanRadius();
        List<Villager> others = findLivingVillagers(level, hubPos, scanRadius, villager.getUUID());
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
        updateEmploymentStats(village, others, tick);
        data.setDirty();

        ACTIVE_NEGOTIATIONS.remove(villager.getUUID());
        syncMayorEntityTags(level, others, outcome.mayorUuid(), village);

        broadcastToVillageStakeholders(
                level, hubPos, village,
                Component.translatable("mayor.tyc2.died", village.getVillageName()).withStyle(ChatFormatting.RED)
        );
        if (outcome.action() == MayorElectionRules.Action.ELECTED_NEW_MAYOR
                || outcome.action() == MayorElectionRules.Action.RESUMED_AND_ELECTED) {
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
            String dim = level.dimension().location().toString();
            ACTIVE_NEGOTIATIONS.values().removeIf(neg -> dim.equals(neg.dimensionId()));
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
                        updateEmploymentStats(created, bellVillagers, gameTime);
                        data.putVillage(created);
                    }
                }
            }
        }

        // B. Evaluate Mayor lifecycle, population & employment stats on all tracked villages whose Hub chunk is loaded
        for (VillageRecord village : new ArrayList<>(data.allVillages())) {
            ServerLevel level = resolveLevel(server, village.getDimensionId());
            if (level == null) {
                continue;
            }
            BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
            if (!level.hasChunkAt(hubPos)) {
                continue;
            }
            int scanRadius = village.effectiveVillagerScanRadius();
            List<Villager> villagers = findLivingVillagers(level, hubPos, scanRadius, null);
            List<MayorElectionRules.MayorCandidate> candidates = toCandidates(villagers);
            village.setPopulation(villagers.size());

            boolean wasPaused = village.getMayorState().isPausedDueToDepopulation();
            MayorElectionRules.ElectionOutcome outcome = MayorElectionRules.evaluate(
                    village.getMayorState(),
                    villagers.size(),
                    candidates,
                    gameTime
            );
            // Always synchronize Mayor tags and immediately strip any disqualified Nitwit / stale Mayors
            syncMayorEntityTags(level, villagers, outcome.mayorUuid(), village);

            // Recompute cached workforce & employment stats (with newly updated Mayor UUID)
            if (updateEmploymentStats(village, villagers, gameTime)) {
                data.setDirty();
            }

            switch (outcome.action()) {
                case ELECTED_NEW_MAYOR -> {
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
                    // Mayor tags already verified by syncMayorEntityTags above
                }
                default -> {
                    // INSUFFICIENT_POPULATION or WAITING_FOR_ADULT_CANDIDATE (disqualified Mayors already stripped)
                    data.setDirty();
                }
            }
        }
    }

    /** Begins an active Mayor negotiation: stops the Mayor immediately and faces {@code player}. */
    public static void startMayorNegotiation(ServerPlayer player, Villager mayor) {
        if (player == null || mayor == null || !mayor.isAlive()) {
            return;
        }
        String dim = mayor.level.dimension().location().toString();
        ACTIVE_NEGOTIATIONS.put(mayor.getUUID(), new ActiveNegotiation(mayor.getUUID(), player.getUUID(), dim));
        freezeMayorFacingPlayer(mayor, player);
    }

    /** Releases a negotiating Mayor when the menu closes, partnership is signed, or player logs out. */
    public static void endMayorNegotiation(ServerPlayer player, @Nullable UUID mayorUuid) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (mayorUuid != null) {
            ActiveNegotiation removed = ACTIVE_NEGOTIATIONS.remove(mayorUuid);
            if (removed != null) {
                releaseMayorTradingState(server, removed);
            }
            return;
        }
        List<ActiveNegotiation> toRelease = new ArrayList<>();
        ACTIVE_NEGOTIATIONS.values().removeIf(neg -> {
            if (neg.playerUuid().equals(player.getUUID())) {
                toRelease.add(neg);
                return true;
            }
            return false;
        });
        for (ActiveNegotiation neg : toRelease) {
            releaseMayorTradingState(server, neg);
        }
    }

    private static void releaseMayorTradingState(MinecraftServer server, ActiveNegotiation neg) {
        ServerLevel level = resolveLevel(server, neg.dimensionId());
        if (level == null) {
            return;
        }
        Entity e = level.getEntity(neg.mayorUuid());
        if (e instanceof Villager mayor) {
            mayor.setTradingPlayer(null);
        }
    }

    private static void tickActiveNegotiations(MinecraftServer server) {
        if (ACTIVE_NEGOTIATIONS.isEmpty()) {
            return;
        }
        for (ActiveNegotiation neg : new ArrayList<>(ACTIVE_NEGOTIATIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(neg.playerUuid());
            ServerLevel level = resolveLevel(server, neg.dimensionId());
            Entity entity = level != null ? level.getEntity(neg.mayorUuid()) : null;
            if (player == null || !player.isAlive()
                    || !(player.containerMenu instanceof MayorNegotiationMenu)
                    || !(entity instanceof Villager mayor) || !mayor.isAlive()
                    || mayor.level != player.level
                    || mayor.distanceToSqr(player) > 256.0) {
                ACTIVE_NEGOTIATIONS.remove(neg.mayorUuid());
                if (entity instanceof Villager v && (player == null || v.getTradingPlayer() == player)) {
                    v.setTradingPlayer(null);
                }
                continue;
            }
            freezeMayorFacingPlayer(mayor, player);
        }
    }

    private static void freezeMayorFacingPlayer(Villager mayor, ServerPlayer player) {
        mayor.setTradingPlayer(player);
        mayor.getNavigation().stop();
        Vec3 vel = mayor.getDeltaMovement();
        mayor.setDeltaMovement(0.0, Math.min(0.0, vel.y), 0.0);
        mayor.getLookControl().setLookAt(player, 30.0F, 30.0F);
        double dx = player.getX() - mayor.getX();
        double dz = player.getZ() - mayor.getZ();
        if (dx * dx + dz * dz > 1.0E-4) {
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            mayor.setYHeadRot(targetYaw);
            mayor.setYBodyRot(targetYaw);
            mayor.setYRot(targetYaw);
        }
    }

    /** Anchors each loaded village's Mayor near the Bell / Cooperation Hub plaza during daytime. */
    private static void tickMayorAnchors(MinecraftServer server, TycoonForgeSavedData data) {
        for (VillageRecord village : data.allVillages()) {
            UUID mayorUuid = village.getMayorState().getMayorEntityUuid();
            if (mayorUuid == null || ACTIVE_NEGOTIATIONS.containsKey(mayorUuid)) {
                continue;
            }
            ServerLevel level = resolveLevel(server, village.getDimensionId());
            if (level == null) {
                continue;
            }
            BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
            if (!level.hasChunkAt(hubPos)) {
                continue;
            }
            Entity entity = level.getEntity(mayorUuid);
            if (!(entity instanceof Villager mayor) || !mayor.isAlive()) {
                continue;
            }
            if (mayor.isSleeping() || mayor.getLastHurtByMob() != null || mayor.hurtTime > 0) {
                continue;
            }
            // Bind Brain MEETING_POINT memory to the village Bell / Cooperation Hub
            mayor.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), hubPos));

            if (!level.isDay()) {
                continue;
            }
            double hx = hubPos.getX() + 0.5;
            double hz = hubPos.getZ() + 0.5;
            double dx = mayor.getX() - hx;
            double dz = mayor.getZ() - hz;
            double horizDistSq = dx * dx + dz * dz;
            if (horizDistSq > 14.0 * 14.0) {
                mayor.getNavigation().moveTo(hx, hubPos.getY() + 1.0, hz, 0.55D);
            } else if (horizDistSq <= 10.0 * 10.0) {
                Player nearPlayer = level.getNearestPlayer(mayor, 5.0);
                if (nearPlayer != null && !nearPlayer.isSpectator()) {
                    mayor.getNavigation().stop();
                    mayor.getLookControl().setLookAt(nearPlayer, 30.0F, 30.0F);
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
        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / SupplyCycleClock.TICKS_PER_DAY;
        boolean runDailyUpkeep = currentDay > data.lastUpkeepDay() && data.lastUpkeepDay() >= 0L;
        if (data.lastUpkeepDay() < 0L) {
            data.setLastUpkeepDay(currentDay);
        }

        for (VillageRecord village : data.allVillages()) {
            if (!village.isPartnered()) {
                continue;
            }
            ServerLevel exactLevel = resolveLevel(server, village.getDimensionId());
            ServerLevel dimLevel = exactLevel != null ? exactLevel : overworld;

            // 1. Municipal Upkeep sink once per Minecraft day
            if (runDailyUpkeep) {
                long upkeep = village.applyMunicipalUpkeep(cfg.municipalUpkeepRate());
                if (upkeep > 0L) {
                    data.economy().recordVillageMoneySink(upkeep);
                    data.setDirty();
                }
            }

            // 2. Auto-Fulfill Buy Orders & Expansion Desire from Inbound 0..499
            if (gameTime % Math.max(20, cfg.autoFulfillIntervalTicks()) == 0L) {
                runAutoFulfill(data, village, gameTime);
            }

            // 3. Weekly Calendar Demand Refresh (dayTime-based; runs even while chunk is unloaded!)
            if (village.normalizeDemandRefreshAnchor(dayTime)) {
                data.setDirty();
            }
            boolean hadOrdersBeforePrune = !village.getActiveOrders().isEmpty();
            boolean allDone = hadOrdersBeforePrune
                    && village.getActiveOrders().stream().allMatch(VillageTradeOrder::isCompleted);
            int pruned = village.pruneFinishedOrExpiredOrders(dayTime);
            if (pruned > 0) {
                data.setDirty();
            }
            int effectiveRefreshTicks = village.getEmploymentStats().effectiveDemandRefreshTicks(cfg);
            boolean dueForRefresh = SupplyCycleClock.isRefreshDue(
                    dayTime,
                    village.getLastDemandRefreshTick(),
                    effectiveRefreshTicks,
                    village.getActiveOrders().isEmpty(),
                    allDone
            );
            if (dueForRefresh && village.canGenerateOrders()) {
                if (hadOrdersBeforePrune) {
                    long stipend = village.applyWeeklyMunicipalStipend(cfg.weeklyMunicipalStipendRatio());
                    if (stipend > 0L) {
                        data.syncMacroAccountTotals();
                        data.setDirty();
                    }
                }
                data.economy().advanceDemandCycleIfDue(dayTime, effectiveRefreshTicks);
                refreshVillageOrders(dimLevel, data, village, dayTime);
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

            // 5. Aid Construction virtual material gathering & loaded-chunk site launch (never paused by expansionPaused!)
            tickAidProjects(exactLevel, data, village, cfg, gameTime);
        }

        if (runDailyUpkeep) {
            data.setLastUpkeepDay(currentDay);
        }
        data.syncMacroAccountTotals();
    }

    private static void runAutoFulfill(TycoonForgeSavedData data, VillageRecord village, long gameTime) {
        BankAccountManager.AccountRef payoutAccount = resolveVillageSettlementAccount(data, village);
        boolean changed = false;

        // Auto-fulfill enabled Village Buy Orders from Inbound 0..499 (tag-aware)
        for (VillageTradeOrder order : new ArrayList<>(village.getActiveOrders())) {
            if (order.side() != OrderSide.VILLAGE_BUYS || !order.autoFulfill() || order.isCompleted()) {
                continue;
            }
            Predicate<SlotStack> slotMatcher = buildOrderSlotMatcher(order);
            int available = village.getStorage().countInInbound(slotMatcher);
            if (available <= 0) {
                continue;
            }
            VillageRecord.TradeExecutionResult res = village.executeBuyOrderFromInboundMatching(
                    order.orderId(), available, slotMatcher
            );
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
                long basePrice = TyC2RuntimeData.goodsCatalog()
                        .findOrderGood(order.displayHint(), order.itemId(), true)
                        .map(TradeGood::basePrice).orElse(order.unitPrice());
                data.economy().recordTrade(order.itemId(), res.executedQty(), basePrice, true);
                changed = true;
            }
        }

        // Auto-fulfill enabled Expansion Desire from Inbound 0..499 & auto-launch once all materials + funds/site are ready
        ExpansionDesire exp = village.getActiveExpansion();
        if (exp != null && !exp.completed() && !village.isExpansionPaused()) {
            ExpansionDesire current = exp;
            if (current.autoFulfill()) {
                ExpansionDesire updated = current.pullAvailableFromInbound(village.getStorage());
                if (updated != current) {
                    village.setActiveExpansion(updated);
                    current = updated;
                    changed = true;
                }
            }
            if (current.allMaterialsDelivered()
                    && gameTime >= village.getNextAutoExpansionRetryTick()
                    && village.getActiveConstructionJob().isEmpty()
                    && village.getVillageBalance() >= current.constructionBudgetCost()) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                ServerLevel dimLevel = server == null ? null : resolveLevel(server, village.getDimensionId());
                if (dimLevel != null && dimLevel.hasChunkAt(TyC2Ids.toMc(village.getHubPos()))) {
                    ActionFeedback launched = tryLaunchExpansionConstruction(dimLevel, data, village, gameTime);
                    if (launched.success()) {
                        changed = true;
                    } else {
                        village.setNextAutoExpansionRetryTick(gameTime + 400L);
                    }
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
            Aabb3i box = job.getBoundingBox();
            int minCx = box.minX() >> 4;
            int maxCx = box.maxX() >> 4;
            int minCz = box.minZ() >> 4;
            int maxCz = box.maxZ() >> 4;
            boolean allChunksLoaded = true;
            for (int cx = minCx; cx <= maxCx && allChunksLoaded; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        allChunksLoaded = false;
                        break;
                    }
                }
            }
            if (!allChunksLoaded) {
                continue;
            }
            ConstructionJob.StepResult step = job.tick();
            if (step.placedBlocks().isEmpty() && !step.completedNow()) {
                continue;
            }
            int placementFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
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
                level.setBlock(targetPos, state, placementFlags);
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
                for (WorldBlockPlacement wp : job.getPlacements()) {
                    if (!WorldBlockPlacement.isFragileOrAttached(wp.blockStateSpec())) {
                        continue;
                    }
                    BlockPos targetPos = TyC2Ids.toMc(wp.pos());
                    if (level.hasChunkAt(targetPos)
                            && level.getBlockState(targetPos).isAir()
                            && !data.placedBlocks().isPlayerPlaced(village.getDimensionId(), targetPos)) {
                        BlockState state = TyC2Ids.parseBlockState(wp.blockStateSpec());
                        if (!state.isAir()) {
                            level.setBlock(targetPos, state, placementFlags);
                        }
                    }
                }
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
        int scanRadius = village.effectiveVillagerScanRadius();
        int currentPop = findLivingVillagers(level, hubPos, scanRadius, null).size();

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
            } else if (marker.kind() == SpawnMarker.MarkerKind.WORKSTATION) {
                TyC2VillagerHelpers.assignVillagerToWorkstation(
                        level,
                        spawnPos,
                        marker.hint(),
                        village.getMayorState().getMayorEntityUuid(),
                        scanRadius
                );
            }
        }

        // Spawn an Iron Golem per 10 villagers if fewer golems than floor(pop / 10)
        int desiredGolems = currentPop / 10;
        if (desiredGolems > 0) {
            List<IronGolem> existing = level.getEntitiesOfClass(
                    IronGolem.class, new AABB(hubPos).inflate(scanRadius), IronGolem::isAlive
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

        List<Villager> updatedVillagers = findLivingVillagers(level, hubPos, scanRadius, null);
        village.setPopulation(updatedVillagers.size());
        updateEmploymentStats(village, updatedVillagers, level.getServer().overworld().getGameTime());

        Optional<AidConstructionProject> aidOpt = village.findAidProjectByJobId(job.getJobId());
        if (aidOpt.isPresent()) {
            AidConstructionProject completedAid = aidOpt.get();
            village.removeAidProject(completedAid.projectId());
            Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                    completedAid.professionId(),
                    completedAid.archetype(),
                    completedAid.displayNameFallback(),
                    completedAid.size()
            );
            broadcastToVillageStakeholders(
                    level, hubPos, village,
                    Component.translatable("status.tyc2.aid.completed", village.getVillageName(), wpName)
                            .withStyle(ChatFormatting.GREEN)
            );
        } else {
            broadcastToVillageStakeholders(
                    level, hubPos, village,
                    Component.translatable(
                            "status.tyc2.expansion.completed",
                            village.getVillageName(),
                            Component.translatable("project.tyc2." + job.getProjectType().name().toLowerCase(Locale.ROOT))
                    ).withStyle(ChatFormatting.GREEN)
            );
        }
    }

    /** Generates a fresh weekly set of Sell & Buy orders for {@code village} anchored to {@code dayTime}. */
    public static void refreshVillageOrders(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            long dayTime
    ) {
        long safeDayTime = Math.max(0L, dayTime);
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
                village.getEmploymentStats(),
                TyC2RuntimeData.workplaceCatalog(),
                RNG
        );
        int effectiveRefreshTicks = village.getEmploymentStats().effectiveDemandRefreshTicks(TyC2RuntimeData.config());
        long expires = safeDayTime + effectiveRefreshTicks;
        List<VillageTradeOrder> orders = new ArrayList<>();
        for (DemandGenerator.GeneratedOrder go : demand.allOrders()) {
            OrderSide side = go.side() == DemandGenerator.OrderSide.VILLAGE_SELLS
                    ? OrderSide.VILLAGE_SELLS
                    : OrderSide.VILLAGE_BUYS;
            orders.add(new VillageTradeOrder(
                    go.orderId() + "_" + (safeDayTime % 100_000L),
                    side,
                    go.itemId(),
                    go.goodId(),
                    go.optionalEnchantmentSpec() == null ? "" : go.optionalEnchantmentSpec(),
                    go.quantity(),
                    0,
                    go.unitPrice(),
                    safeDayTime,
                    expires,
                    false,
                    go.seasonalHighlight(),
                    go.biomeSpecialty()
            ));
        }
        village.setActiveOrders(orders);
        village.setLastDemandRefreshTick(safeDayTime);
        data.setDirty();
    }

    // --- Public Player / GUI Actions ---

    /** Opens the Mayor Negotiation GUI for an unpartnered village. */
    public static void openMayorNegotiationScreen(ServerPlayer player, VillageRecord village) {
        openMayorNegotiationScreen(player, village, village.getMayorState().getMayorEntityUuid());
    }

    /** Opens the Mayor Negotiation GUI for an unpartnered village carrying {@code mayorEntityUuid}. */
    public static void openMayorNegotiationScreen(
            ServerPlayer player,
            VillageRecord village,
            @Nullable UUID mayorEntityUuid
    ) {
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inv, p) -> new MayorNegotiationMenu(id, inv, village.getVillageId(), mayorEntityUuid),
                        Component.translatable("container.tyc2.mayor_negotiation")
                ),
                buf -> {
                    buf.writeUUID(village.getVillageId());
                    buf.writeBoolean(mayorEntityUuid != null);
                    if (mayorEntityUuid != null) {
                        buf.writeUUID(mayorEntityUuid);
                    }
                }
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
        if (payFromCash > 0L) {
            data.economy().recordCashPaidToVillage(payFromCash);
        }

        // 2. Consume basic startup supplies
        consumeMatchingItems(player.getInventory(), s -> TyC2Ids.matchesItemOrTag(s, "#minecraft:logs"), status.requiredLogs());
        consumeMatchingItems(player.getInventory(), s -> TyC2Ids.matchesItemOrTag(s, "#ingots/iron"), status.requiredIron());
        consumeMatchingItems(player.getInventory(), TycoonForgeWorldManager::isBasicNegotiationFood, status.requiredFood());

        // 3. Establish partnership with working capital floor and register existing village structures as protected zones
        BankAccountManager.AccountRef activeRef = data.bank().resolveSettlementAccount(player.getUUID());
        String companyId = activeRef.scope() == BankAccountManager.AccountScope.COMPANY ? activeRef.accountId() : null;
        TyC2Config cfg = TyC2RuntimeData.config();
        village.establishPartnershipWithWorkingCapital(
                player.getUUID(), companyId, requiredIc, cfg.initialVillageWorkingCapital()
        );

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

        // 5. Generate initial Lv 1 orders immediately (anchored to dayTime) & release negotiating Mayor
        long dayTime = level.getServer().overworld().getDayTime();
        refreshVillageOrders(villageLevel, data, village, dayTime);
        endMayorNegotiation(player, village.getMayorState().getMayorEntityUuid());
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
        return executeSellOrder(player, villageId, orderId, requestedQty, useBank, cashSlot, null);
    }

    /**
     * Executes a player purchasing from a {@link OrderSide#VILLAGE_SELLS} order, validating
     * Outbound capacity and player balance BEFORE deducting {@code terminalStack} 50 EU.
     */
    public static ActionFeedback executeSellOrder(
            ServerPlayer player,
            UUID villageId,
            String orderId,
            int requestedQty,
            boolean useBank,
            @Nullable Container cashSlot,
            @Nullable ItemStack terminalStack
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
        if (order.side() != OrderSide.VILLAGE_SELLS || order.isCompleted()) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }
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
        // Pre-validate player balance BEFORE deducting Terminal 50 EU
        if (useBank) {
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            if (data.bank().getActiveBalance(player.getUUID()) < totalCost) {
                return new ActionFeedback(false, "status.tyc2.atm.insufficient_balance");
            }
        } else {
            long cash = TyC2Ids.countPlayerCash(player) + (cashSlot == null ? 0L : TyC2Ids.coinStackValue(cashSlot.getItem(0)));
            if (cash < totalCost) {
                return new ActionFeedback(false, "status.tyc2.atm.insufficient_balance");
            }
        }

        if (terminalStack != null && !CommTerminalItem.consumeAction(terminalStack, TriModePowerMath.TerminalAction.REMOTE_TRADE)) {
            return new ActionFeedback(false, "status.tyc2.terminal.low_power");
        }

        long tick = player.getServer().overworld().getGameTime();
        if (useBank) {
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            BankAccountManager.BankResult debit = data.bank().debitAccount(
                    ref, totalCost, BankAccountManager.TransactionType.TRADE_BUY,
                    tick, village.getVillageName(), ""
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
                data.bank().creditAccount(ref, totalCost, BankAccountManager.TransactionType.SYSTEM_ADJUST, tick, village.getVillageName(), "");
            } else {
                TyC2Ids.giveCoinsToPlayer(player, totalCost, Ic2Bridge.isLoaded());
            }
            return new ActionFeedback(false, res.failureCode() == VillageRecord.TradeFailureCode.OUTBOUND_FULL
                    ? "status.tyc2.hub.outbound_full"
                    : "gui.tyc2.hub.no_orders");
        }

        if (!useBank) {
            data.economy().recordCashPaidToVillage(res.totalCurrency());
        }
        long basePrice = TyC2RuntimeData.goodsCatalog()
                .findOrderGood(order.displayHint(), order.itemId(), false)
                .map(TradeGood::basePrice).orElse(order.unitPrice());
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
        return executeBuyOrder(player, villageId, orderId, requestedQty, useBank, allowPlayerInventoryFallback, null);
    }

    /**
     * Executes a player fulfilling a {@link OrderSide#VILLAGE_BUYS} order using tag-aware matching,
     * checking village treasury affordability and Inbound stock BEFORE pulling from player inventory
     * or deducting {@code terminalStack} 50 EU.
     */
    public static ActionFeedback executeBuyOrder(
            ServerPlayer player,
            UUID villageId,
            String orderId,
            int requestedQty,
            boolean useBank,
            boolean allowPlayerInventoryFallback,
            @Nullable ItemStack terminalStack
    ) {
        boolean isTerminal = terminalStack != null || !allowPlayerInventoryFallback;
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
        if (order.side() != OrderSide.VILLAGE_BUYS || order.isCompleted()) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }
        int desired = Math.min(Math.max(1, requestedQty), order.remainingQty());
        if (desired <= 0) {
            return new ActionFeedback(false, "gui.tyc2.hub.no_orders");
        }

        // 1. Check village funds affordability FIRST (before pulling player items or deducting Terminal EU)
        int affordableQty = (int) Math.min(Integer.MAX_VALUE, village.getVillageBalance() / Math.max(1L, order.unitPrice()));
        if (affordableQty <= 0) {
            return new ActionFeedback(
                    false,
                    "status.tyc2.hub.village_low_funds_detail",
                    village.getVillageBalance(),
                    order.unitPrice()
            );
        }

        // 2. Build tag-aware item matcher (exact itemId OR GoodsCatalog optionalTag via findOrderGood)
        Predicate<SlotStack> slotMatcher = buildOrderSlotMatcher(order);
        Predicate<ItemStack> stackMatcher = buildOrderStackMatcher(order);

        // 3. At local Hub, pull at most (min(desired, affordableQty) - currentMatchingInbound) from player inv
        int cappedTarget = Math.min(desired, affordableQty);
        int currentMatchingInbound = village.getStorage().countInInbound(slotMatcher);
        if (allowPlayerInventoryFallback && currentMatchingInbound < cappedTarget) {
            int need = cappedTarget - currentMatchingInbound;
            pullMatchingItemsFromPlayerIntoInbound(player.getInventory(), village, stackMatcher, need);
            currentMatchingInbound = village.getStorage().countInInbound(slotMatcher);
        }

        // 4. Verify matching Inbound stock > 0 BEFORE deducting Terminal 50 EU (never falsely report low funds!)
        if (currentMatchingInbound <= 0) {
            return new ActionFeedback(
                    false,
                    isTerminal ? "status.tyc2.terminal.insufficient_goods" : "status.tyc2.hub.insufficient_goods"
            );
        }

        // 5. Deduct Terminal 50 EU only when ready to settle
        if (terminalStack != null && !CommTerminalItem.consumeAction(terminalStack, TriModePowerMath.TerminalAction.REMOTE_TRADE)) {
            return new ActionFeedback(false, "status.tyc2.terminal.low_power");
        }

        VillageRecord.TradeExecutionResult res = village.executeBuyOrderFromInboundMatching(
                orderId, cappedTarget, slotMatcher
        );
        if (!res.success()) {
            return switch (res.failureCode()) {
                case INSUFFICIENT_INBOUND_ITEMS -> new ActionFeedback(
                        false,
                        isTerminal ? "status.tyc2.terminal.insufficient_goods" : "status.tyc2.hub.insufficient_goods"
                );
                case INSUFFICIENT_VILLAGE_FUNDS -> new ActionFeedback(
                        false,
                        "status.tyc2.hub.village_low_funds_detail",
                        village.getVillageBalance(),
                        order.unitPrice()
                );
                case OUTBOUND_FULL -> new ActionFeedback(false, "status.tyc2.hub.outbound_full");
                default -> new ActionFeedback(false, "gui.tyc2.hub.no_orders");
            };
        }

        long tick = player.getServer().overworld().getGameTime();
        if (useBank) {
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            data.bank().creditAccount(
                    ref, res.totalCurrency(), BankAccountManager.TransactionType.TRADE_SELL,
                    tick, village.getVillageName(), ""
            );
        } else {
            TyC2Ids.giveCoinsToPlayer(player, res.totalCurrency(), Ic2Bridge.isLoaded());
            data.economy().recordVillagePaidCash(res.totalCurrency());
        }

        long basePrice = TyC2RuntimeData.goodsCatalog()
                .findOrderGood(order.displayHint(), order.itemId(), true)
                .map(TradeGood::basePrice).orElse(order.unitPrice());
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
        return upgradeVillage(player, villageId, null);
    }

    /** Attempts to upgrade a partnered village, validating eligibility BEFORE deducting {@code terminalStack} 100 EU. */
    public static ActionFeedback upgradeVillage(
            ServerPlayer player,
            UUID villageId,
            @Nullable ItemStack terminalStack
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "status.tyc2.hub.upgrade_failed");
        }
        VillageRecord village = vOpt.get();
        if (!village.evaluateUpgrade(TyC2RuntimeData.levelTable()).eligible()) {
            return new ActionFeedback(false, "status.tyc2.hub.upgrade_failed");
        }
        if (terminalStack != null
                && !CommTerminalItem.consumeAction(terminalStack, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
            return new ActionFeedback(false, "status.tyc2.terminal.low_power");
        }
        long burned = village.tryUpgrade(TyC2RuntimeData.levelTable());
        if (burned <= 0L) {
            return new ActionFeedback(false, "status.tyc2.hub.upgrade_failed");
        }
        data.economy().recordVillageMoneySink(burned);
        data.syncMacroAccountTotals();
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel != null) {
            refreshVillageOrders(dimLevel, data, village, player.getServer().overworld().getDayTime());
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
        return deliverExpansionMaterials(player, villageId, pullFromPlayerInventory, null);
    }

    /** Pulls available expansion materials and starts construction, returning accurate expansion fund errors. */
    public static ActionFeedback deliverExpansionMaterials(
            ServerPlayer player,
            UUID villageId,
            boolean pullFromPlayerInventory,
            @Nullable ItemStack terminalStack
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
            data.setDirty();
        }

        if (exp.allMaterialsDelivered() && village.getVillageBalance() < exp.constructionBudgetCost()) {
            return new ActionFeedback(
                    false,
                    "status.tyc2.expansion.low_funds",
                    exp.constructionBudgetCost(),
                    village.getVillageBalance()
            );
        }

        // Guard Comm Terminal 100 EU when no remaining expansion materials exist in Hub Inbound
        if (terminalStack != null && !exp.allMaterialsDelivered()) {
            boolean hasAnyInbound = false;
            for (String reqItemId : exp.remainingMaterials().keySet()) {
                if (village.getStorage().countInInbound(reqItemId) > 0) {
                    hasAnyInbound = true;
                    break;
                }
            }
            if (!hasAnyInbound) {
                return new ActionFeedback(
                        false,
                        "status.tyc2.expansion.missing_materials",
                        buildMissingMaterialsComponent(exp)
                );
            }
        }

        if (terminalStack != null
                && !CommTerminalItem.consumeAction(terminalStack, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
            return new ActionFeedback(false, "status.tyc2.terminal.low_power");
        }

        int deliveredBefore = totalDelivered(exp);
        if (pullFromPlayerInventory) {
            for (var entry : exp.remainingMaterials().entrySet()) {
                pullItemsFromPlayerIntoInbound(player.getInventory(), village, entry.getKey(), entry.getValue());
            }
        }
        ExpansionDesire updated = exp.pullAvailableFromInbound(village.getStorage());
        village.setActiveExpansion(updated);
        data.setDirty();

        if (!updated.allMaterialsDelivered()) {
            boolean deliveredAny = totalDelivered(updated) > deliveredBefore;
            return new ActionFeedback(
                    deliveredAny,
                    deliveredAny
                            ? "status.tyc2.expansion.delivered_partial"
                            : "status.tyc2.expansion.missing_materials",
                    buildMissingMaterialsComponent(updated)
            );
        }
        if (village.getVillageBalance() < updated.constructionBudgetCost()) {
            return new ActionFeedback(
                    false,
                    "status.tyc2.expansion.low_funds",
                    updated.constructionBudgetCost(),
                    village.getVillageBalance()
            );
        }
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel == null) {
            return new ActionFeedback(false, "status.tyc2.expansion.no_site");
        }
        return tryLaunchExpansionConstruction(dimLevel, data, village, player.getServer().overworld().getGameTime());
    }

    private static int totalDelivered(ExpansionDesire desire) {
        int sum = 0;
        for (int qty : desire.deliveredMaterials().values()) {
            sum += Math.max(0, qty);
        }
        return sum;
    }

    /** Builds a client-translatable comma-separated list of missing expansion materials ({@code <Item> ×N}). */
    private static MutableComponent buildMissingMaterialsComponent(ExpansionDesire desire) {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : desire.remainingMaterials().entrySet()) {
            if (!first) {
                list.append(Component.literal(", "));
            }
            first = false;
            Item item = TyC2Ids.getItem(entry.getKey());
            Component itemName = item != null
                    ? Component.translatable(item.getDescriptionId())
                    : Component.literal(entry.getKey());
            list.append(itemName).append(Component.literal(" \u00D7" + entry.getValue()));
        }
        return list;
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

        Component projName = Component.translatable("project.tyc2." + exp.projectType().name().toLowerCase(Locale.ROOT));
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
                Component.translatable("project.tyc2." + type.name().toLowerCase(Locale.ROOT))
        );
    }

    /**
     * Player-funded Aid Construction commissioning action shared by Cooperation Hub (bank or cash)
     * and Commerce Comm Terminal (bank + 100 EU). Validates village eligibility, queue capacity,
     * workplace spec, level unlock, live CPI funds, and loaded-chunk site availability BEFORE
     * deducting Terminal EU or charging currency.
     */
    public static ActionFeedback startAidConstruction(
            ServerPlayer player,
            UUID villageId,
            String professionId,
            WorkplaceSize size,
            boolean useBank,
            @Nullable Container cashSlot,
            @Nullable ItemStack terminalStack
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        TyC2Config cfg = TyC2RuntimeData.config();
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty() || !vOpt.get().isPartnered()) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        VillageRecord village = vOpt.get();
        if (village.getMayorState().isPausedDueToDepopulation()) {
            return new ActionFeedback(false, "gui.tyc2.hub.paused_depopulated");
        }
        if (village.getAidProjects().size() >= cfg.maxQueuedAidProjects()) {
            return new ActionFeedback(false, "status.tyc2.aid.queue_full", cfg.maxQueuedAidProjects());
        }

        String rawProf = professionId == null ? "" : professionId.trim();
        Optional<WorkplaceSpec> specOpt = TyC2RuntimeData.workplaceCatalog().getByProfessionId(rawProf);
        if (specOpt.isEmpty() || !specOpt.get().enabled() || !TyC2Ids.blockExists(specOpt.get().workstationBlockId())) {
            return new ActionFeedback(false, "status.tyc2.aid.unknown_profession", rawProf);
        }
        WorkplaceSpec spec = specOpt.get();
        WorkplaceSize effectiveSize = size == null ? WorkplaceSize.SMALL : size;
        if (!spec.isUnlockedAtLevel(village.getLevel(), effectiveSize)) {
            return new ActionFeedback(false, "status.tyc2.aid.level_locked", spec.minVillageLevel(effectiveSize));
        }

        data.syncMacroAccountTotals();
        long costIc = WorkplaceCatalog.quoteCostIc(spec, effectiveSize, data.economy().computeCpi(), cfg);
        boolean payBank = terminalStack != null || useBank;

        // Validate player funds BEFORE site preview or Terminal 100 EU deduction
        if (payBank) {
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            if (data.bank().getActiveBalance(player.getUUID()) < costIc) {
                return new ActionFeedback(false, "status.tyc2.atm.insufficient_balance");
            }
        } else {
            long cash = TyC2Ids.countPlayerCash(player)
                    + (cashSlot == null ? 0L : TyC2Ids.coinStackValue(cashSlot.getItem(0)));
            if (cash < costIc) {
                return new ActionFeedback(false, "status.tyc2.atm.insufficient_balance");
            }
        }

        // If Hub chunk IS loaded, pre-check site availability and pre-reserve footprint (no charge if no site!)
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        Aabb3i preReservedZone = null;
        if (dimLevel != null && dimLevel.hasChunkAt(hubPos)) {
            StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(spec, effectiveSize, village.getBiomeStyle());
            ServerTerrainProbe probe = new ServerTerrainProbe(dimLevel, data.placedBlocks());
            ExpansionPlanner.PlanResult preview = PLANNER.planBlueprint(village, bp, probe, "aid_preview", true);
            if (!preview.success()) {
                return new ActionFeedback(false, "status.tyc2.aid.no_site");
            }
            preReservedZone = preview.reservedZone();
        }

        // Deduct Terminal 100 EU ONLY after all eligibility, funds, and site checks pass
        if (terminalStack != null
                && !CommTerminalItem.consumeAction(terminalStack, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION)) {
            return new ActionFeedback(false, "status.tyc2.terminal.low_power");
        }

        long tick = player.getServer().overworld().getGameTime();
        String payerCompanyId = null;
        boolean paidWithCash = !payBank;
        if (payBank) {
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            if (ref.scope() == BankAccountManager.AccountScope.COMPANY) {
                payerCompanyId = ref.accountId();
            }
            BankAccountManager.BankResult debit = data.bank().debitAccount(
                    ref, costIc, BankAccountManager.TransactionType.TRADE_BUY,
                    tick, village.getVillageName(), "Aid Construction: " + spec.professionId()
            );
            if (!debit.success()) {
                return new ActionFeedback(false, debit.errorKey());
            }
            data.economy().recordBankMoneySink(costIc);
        } else {
            if (!TyC2Ids.consumePhysicalCash(player, cashSlot, costIc, Ic2Bridge.isLoaded())) {
                return new ActionFeedback(false, "status.tyc2.atm.insufficient_balance");
            }
            data.economy().recordMoneySink(costIc);
        }

        if (preReservedZone != null) {
            village.addOccupiedZone(preReservedZone);
        }
        String projectId = "aid_" + tick + "_" + RNG.nextInt(100_000);
        int requiredTicks = effectiveSize.gatherTicks(cfg);
        AidConstructionProject project = AidConstructionProject.create(
                projectId,
                spec,
                effectiveSize,
                village.getBiomeStyle(),
                costIc,
                requiredTicks,
                preReservedZone,
                player.getUUID(),
                payerCompanyId,
                paidWithCash
        );
        village.addAidProject(project);
        data.syncMacroAccountTotals();
        data.setDirty();

        Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                spec.professionId(), spec.archetype(), spec.displayNameFallback(), effectiveSize
        );
        if (dimLevel != null) {
            broadcastToVillageStakeholders(
                    dimLevel, hubPos, village,
                    Component.translatable("status.tyc2.aid.started", village.getVillageName(), wpName, costIc)
                            .withStyle(ChatFormatting.AQUA)
            );
        }
        return new ActionFeedback(true, "status.tyc2.aid.started", village.getVillageName(), wpName, costIc);
    }

    /**
     * Cancels a queued aid construction project prior to block placement, releasing its pre-reserved
     * footprint, refunding {@code paidIc} in full, and reversing the matching macro-economy sink so
     * global money supply {@code M} stays strictly conserved.
     */
    public static ActionFeedback cancelAidConstruction(
            ServerPlayer player,
            UUID villageId,
            String projectId,
            boolean allowCashRefund
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(villageId);
        if (vOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.aid.queue_empty");
        }
        VillageRecord village = vOpt.get();
        Optional<AidConstructionProject> projOpt = village.findAidProject(projectId);
        if (projOpt.isEmpty()) {
            return new ActionFeedback(false, "gui.tyc2.aid.queue_empty");
        }
        AidConstructionProject proj = projOpt.get();
        boolean isPayer = player.getUUID().equals(proj.payerPlayerUuid());
        if (!isPayer && !canManageVillage(player, data, village)) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        if (!proj.canCancel()) {
            return new ActionFeedback(false, "status.tyc2.aid.cannot_cancel");
        }

        if (proj.reservedZone() != null) {
            village.removeOccupiedZone(proj.reservedZone());
        }
        village.removeAidProject(proj.projectId());

        long refundIc = proj.paidIc();
        boolean refundAsCash = proj.paidWithCash() && allowCashRefund && isPayer;
        long tick = player.getServer().overworld().getGameTime();
        if (refundIc > 0L) {
            if (refundAsCash) {
                TyC2Ids.giveCoinsToPlayer(player, refundIc, Ic2Bridge.isLoaded());
            } else {
                BankAccountManager.AccountRef ref;
                if (proj.payerCompanyId() != null && data.bank().getCompany(proj.payerCompanyId()).isPresent()) {
                    ref = BankAccountManager.AccountRef.company(proj.payerCompanyId());
                } else if (proj.payerPlayerUuid() != null) {
                    data.bank().getOrCreatePersonal(proj.payerPlayerUuid(), null);
                    ref = BankAccountManager.AccountRef.personal(proj.payerPlayerUuid());
                } else {
                    data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
                    ref = data.bank().resolveSettlementAccount(player.getUUID());
                }
                data.bank().creditAccount(
                        ref, refundIc, BankAccountManager.TransactionType.SYSTEM_ADJUST,
                        tick, village.getVillageName(), "Aid Construction Cancelled Refund"
                );
            }
            data.economy().refundMoneySink(refundIc, refundAsCash);
        }

        data.syncMacroAccountTotals();
        data.setDirty();
        Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                proj.professionId(), proj.archetype(), proj.displayNameFallback(), proj.size()
        );
        return new ActionFeedback(true, "status.tyc2.aid.cancelled", wpName, refundIc);
    }

    /** Admin/OP helper for {@code /tyc2 aid <profession> [small|large]} launching an aid workplace immediately. */
    public static ActionFeedback forcePlanAid(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            String professionId,
            WorkplaceSize size
    ) {
        Optional<WorkplaceSpec> specOpt = TyC2RuntimeData.workplaceCatalog().getByProfessionId(professionId);
        if (specOpt.isEmpty() || !specOpt.get().enabled() || !TyC2Ids.blockExists(specOpt.get().workstationBlockId())) {
            return new ActionFeedback(false, "status.tyc2.aid.unknown_profession", professionId);
        }
        WorkplaceSpec spec = specOpt.get();
        WorkplaceSize effectiveSize = size == null ? WorkplaceSize.SMALL : size;
        long gameTime = level.getServer().overworld().getGameTime();
        StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(spec, effectiveSize, village.getBiomeStyle());
        ServerTerrainProbe probe = new ServerTerrainProbe(level, data.placedBlocks());
        String projectId = "cmd_aid_" + gameTime + "_" + RNG.nextInt(10_000);
        String jobId = "aid_job_" + projectId;
        ExpansionPlanner.PlanResult plan = PLANNER.planBlueprint(village, bp, probe, jobId, true);
        if (!plan.success()) {
            return new ActionFeedback(false, "status.tyc2.aid.no_site");
        }
        PLANNER.commitPlanWithPriority(village, plan, gameTime, true);
        AidConstructionProject proj = AidConstructionProject.create(
                projectId,
                spec,
                effectiveSize,
                village.getBiomeStyle(),
                0L,
                effectiveSize.gatherTicks(TyC2RuntimeData.config()),
                plan.reservedZone(),
                null,
                null,
                false
        ).withBuilding(jobId);
        village.addAidProject(proj);
        data.setDirty();

        Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                spec.professionId(), spec.archetype(), spec.displayNameFallback(), effectiveSize
        );
        broadcastToVillageStakeholders(
                level, TyC2Ids.toMc(village.getHubPos()), village,
                Component.translatable("status.tyc2.aid.building", village.getVillageName(), wpName)
                        .withStyle(ChatFormatting.AQUA)
        );
        return new ActionFeedback(true, "status.tyc2.aid.building", village.getVillageName(), wpName);
    }

    /** Computes display progress percentage (0..100) for an aid project, using live block placement when BUILDING. */
    public static int resolveAidProgressPct(VillageRecord village, AidConstructionProject proj) {
        if (proj.status() == AidConstructionProject.Status.BUILDING) {
            for (ConstructionJob job : village.getConstructionQueue()) {
                if (proj.plannedJobId().equals(job.getJobId())) {
                    int total = job.getTotalBlocks();
                    return total > 0 ? Math.min(100, (job.getPlacedCount() * 100) / total) : 0;
                }
            }
            return 100;
        }
        return (int) Math.min(100L, Math.max(0L, Math.round(proj.completionRatio() * 100.0)));
    }

    private static void tickAidProjects(
            ServerLevel dimLevel,
            TycoonForgeSavedData data,
            VillageRecord village,
            TyC2Config cfg,
            long gameTime
    ) {
        if (village.getAidProjects().isEmpty()) {
            return;
        }
        // 1. Advance head COLLECTING aid project virtually (runs even while unloaded and even when expansionPaused!)
        double speedMult = village.getEmploymentStats().gatherSpeedMultiplier(cfg);
        for (AidConstructionProject proj : new ArrayList<>(village.getAidProjects())) {
            if (proj.status() == AidConstructionProject.Status.COLLECTING) {
                AidConstructionProject updated = proj.advanceGathering(20.0 * speedMult);
                if (updated != proj) {
                    village.updateAidProject(updated);
                    data.setDirty();
                }
                break;
            }
        }

        // 2. When Hub chunk IS loaded, launch WAITING_SITE projects immediately and retry NO_SITE every 400 ticks
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        if (dimLevel == null || !dimLevel.hasChunkAt(hubPos)) {
            return;
        }
        boolean retryNoSite = (gameTime % 400L == 0L);
        for (AidConstructionProject proj : new ArrayList<>(village.getAidProjects())) {
            if (proj.status() != AidConstructionProject.Status.WAITING_SITE
                    && !(proj.status() == AidConstructionProject.Status.NO_SITE && retryNoSite)) {
                continue;
            }
            launchAidConstruction(dimLevel, data, village, proj, gameTime);
        }
    }

    private static void launchAidConstruction(
            ServerLevel level,
            TycoonForgeSavedData data,
            VillageRecord village,
            AidConstructionProject proj,
            long gameTime
    ) {
        WorkplaceSpec spec = TyC2RuntimeData.workplaceCatalog()
                .getByProfessionId(proj.professionId())
                .orElseGet(proj::toWorkplaceSpec);
        StructureBlueprint bp = WorkplaceArchitectureCatalog.getBlueprint(spec, proj.size(), village.getBiomeStyle());
        ServerTerrainProbe probe = new ServerTerrainProbe(level, data.placedBlocks());
        String jobId = "aid_job_" + proj.projectId() + "_" + gameTime;

        ExpansionPlanner.PlanResult plan = PLANNER.planBlueprintIgnoringZone(
                village, bp, probe, jobId, true, proj.reservedZone()
        );
        Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                proj.professionId(), proj.archetype(), proj.displayNameFallback(), proj.size()
        );
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        if (plan.success()) {
            if (proj.reservedZone() != null && !proj.reservedZone().equals(plan.reservedZone())) {
                village.removeOccupiedZone(proj.reservedZone());
            }
            PLANNER.commitPlanWithPriority(village, plan, gameTime, true);
            village.updateAidProject(proj.withReservedZone(plan.reservedZone()).withBuilding(jobId));
            data.setDirty();
            broadcastToVillageStakeholders(
                    level, hubPos, village,
                    Component.translatable("status.tyc2.aid.building", village.getVillageName(), wpName)
                            .withStyle(ChatFormatting.AQUA)
            );
        } else if (proj.status() == AidConstructionProject.Status.WAITING_SITE) {
            village.updateAidProject(proj.withStatus(AidConstructionProject.Status.NO_SITE));
            data.setDirty();
            broadcastToVillageStakeholders(
                    level, hubPos, village,
                    Component.translatable("status.tyc2.aid.no_site_waiting", village.getVillageName(), wpName)
                            .withStyle(ChatFormatting.YELLOW)
            );
        }
    }

    /** Recomputes and stores {@link VillageEmploymentStats} from living villagers; returns {@code true} if counts changed. */
    private static boolean updateEmploymentStats(VillageRecord village, List<Villager> villagers, long gameTime) {
        List<VillageEmploymentStats.VillagerSnapshot> snaps = new ArrayList<>(villagers.size());
        for (Villager v : villagers) {
            snaps.add(TyC2VillagerHelpers.toEmploymentSnapshot(v));
        }
        VillageEmploymentStats prev = village.getEmploymentStats();
        VillageEmploymentStats next = VillageEmploymentStats.computeFromSnapshots(
                snaps,
                village.getMayorState().getMayorEntityUuid(),
                gameTime
        );
        village.setEmploymentStats(next);
        return prev.workforceCount() != next.workforceCount()
                || prev.employedCount() != next.employedCount()
                || prev.unemployedCount() != next.unemployedCount()
                || prev.mayorCountedEmployed() != next.mayorCountedEmployed()
                || !prev.activeProfessionCounts().equals(next.activeProfessionCounts());
    }

    /**
     * Checks whether {@code player} is authorized to operate {@code village}'s Cooperation Hub,
     * warehouse, orders, aid queue, or remote terminal bindings (owner, partner company member,
     * server OP, or any player while unpartnered).
     */
    public static boolean canAccessVillage(ServerPlayer player, TycoonForgeSavedData data, VillageRecord village) {
        if (player == null || village == null) {
            return false;
        }
        if (!village.isPartnered() || player.hasPermissions(2)) {
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

    /** Restores a partnered village's Cooperation Hub block and block entity at its canonical coordinates. */
    public static BlockPos restoreHubBlock(ServerLevel fallbackLevel, VillageRecord village) {
        ServerLevel targetLevel = resolveLevel(fallbackLevel.getServer(), village.getDimensionId());
        if (targetLevel == null) {
            targetLevel = fallbackLevel;
        }
        BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
        targetLevel.setBlock(hubPos, TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (targetLevel.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity hubBe) {
            hubBe.setVillageId(village.getVillageId());
        }
        TycoonForgeSavedData.get(targetLevel).setDirty();
        return hubPos;
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
        if (dimLevel == null) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        BlockPos oldPos = TyC2Ids.toMc(village.getHubPos());
        if (!dimLevel.isLoaded(oldPos)
                || !dimLevel.getBlockState(oldPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
        }
        if (dimLevel.getBlockEntity(oldPos) instanceof CooperationHubBlockEntity hubBe) {
            hubBe.dropUpgradesTo(player);
        }
        if (!dimLevel.removeBlock(oldPos, false)) {
            return new ActionFeedback(false, "status.tyc2.hub.not_owner");
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

    /** Terminates partnership, refunds pending aid projects, restores the village Bell, and clears queues safely. */
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
        long tick = player.getServer().overworld().getGameTime();
        for (AidConstructionProject proj : new ArrayList<>(village.getAidProjects())) {
            if (proj.reservedZone() != null) {
                village.removeOccupiedZone(proj.reservedZone());
            }
            if (proj.canCancel() && proj.paidIc() > 0L) {
                BankAccountManager.AccountRef ref;
                if (proj.payerCompanyId() != null && data.bank().getCompany(proj.payerCompanyId()).isPresent()) {
                    ref = BankAccountManager.AccountRef.company(proj.payerCompanyId());
                } else if (proj.payerPlayerUuid() != null) {
                    data.bank().getOrCreatePersonal(proj.payerPlayerUuid(), null);
                    ref = BankAccountManager.AccountRef.personal(proj.payerPlayerUuid());
                } else {
                    data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
                    ref = data.bank().resolveSettlementAccount(player.getUUID());
                }
                data.bank().creditAccount(
                        ref, proj.paidIc(), BankAccountManager.TransactionType.SYSTEM_ADJUST,
                        tick, name, "Aid Refund (Termination)"
                );
                data.economy().refundBankMoneySink(proj.paidIc());
            }
        }
        village.clearAidProjects();
        village.clearConstructionQueue();
        village.setChunkLoaderInstalled(false);
        village.terminatePartnership();
        ServerLevel dimLevel = resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel != null) {
            BlockPos hubPos = TyC2Ids.toMc(village.getHubPos());
            if (dimLevel.hasChunkAt(hubPos)
                    && dimLevel.getBlockState(hubPos).is(TyC2ForgeRegistries.COOPERATION_HUB_BLOCK.get())) {
                if (dimLevel.getBlockEntity(hubPos) instanceof CooperationHubBlockEntity hubBe) {
                    hubBe.dropUpgradesTo(player);
                }
                BlockState bell = Blocks.BELL.defaultBlockState();
                if (bell.canSurvive(dimLevel, hubPos)) {
                    dimLevel.setBlock(hubPos, bell, Block.UPDATE_ALL);
                } else {
                    dimLevel.removeBlock(hubPos, false);
                }
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
        return findLivingVillagers(level, center, VILLAGE_SCAN_RADIUS, exclude);
    }

    private static List<Villager> findLivingVillagers(
            ServerLevel level, BlockPos center, int radius, @Nullable UUID exclude
    ) {
        AABB box = new AABB(center).inflate(Math.max(VILLAGE_SCAN_RADIUS, radius));
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

    /**
     * Synchronizes Mayor persistent tags &amp; localized golden display name onto {@code mayorUuid},
     * and immediately strips {@code tyc2_mayor}, {@code tyc2_village_id}, custom name, and glowing
     * from any villager in the scan that is no longer the elected Mayor (including disqualified Nitwits).
     */
    private static void syncMayorEntityTags(
            ServerLevel level,
            List<Villager> villagers,
            @Nullable UUID mayorUuid,
            VillageRecord village
    ) {
        String villageIdStr = village.getVillageId().toString();
        for (Villager v : villagers) {
            CompoundTag pdata = v.getPersistentData();
            if (mayorUuid != null && v.getUUID().equals(mayorUuid)) {
                pdata.putBoolean(TAG_MAYOR, true);
                pdata.putString(TAG_VILLAGE_ID, villageIdStr);
                Component displayComp = resolveMayorDisplayComponent(v, village);
                v.setCustomName(Component.translatable("mayor.tyc2.name_format", displayComp)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                v.setCustomNameVisible(true);
                v.setPersistenceRequired();
            } else if (pdata.getBoolean(TAG_MAYOR)
                    && (villageIdStr.equals(pdata.getString(TAG_VILLAGE_ID))
                    || v.getVillagerData().getProfession() == VillagerProfession.NITWIT)) {
                pdata.remove(TAG_MAYOR);
                pdata.remove(TAG_VILLAGE_ID);
                v.setCustomName(null);
                v.setCustomNameVisible(false);
                v.setGlowingTag(false);
                if (v.getTradingPlayer() != null) {
                    v.setTradingPlayer(null);
                }
                ACTIVE_NEGOTIATIONS.remove(v.getUUID());
            }
        }
    }

    /**
     * Resolves a client-localizable {@link Component} for the Mayor's name argument inside
     * {@code mayor.tyc2.name_format} (translatable villager profession when no player-given custom
     * name exists, or village name for jobless villagers, so {@code zh_cn} clients never see
     * server-flattened English profession names like {@code "Librarian"}).
     */
    private static Component resolveMayorDisplayComponent(Villager v, VillageRecord village) {
        VillagerProfession prof = v.getVillagerData().getProfession();
        ResourceLocation profKey = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(prof);
        Component profComp = (prof != VillagerProfession.NONE && profKey != null)
                ? Component.translatable(
                        "entity.minecraft.villager."
                                + ("minecraft".equals(profKey.getNamespace())
                                ? profKey.getPath()
                                : profKey.getNamespace() + "." + profKey.getPath())
                )
                : null;
        String stored = village.getMayorState().getMayorName();
        String trimmed = stored == null ? "" : stored.trim();
        boolean isGenericOrUnset = trimmed.isEmpty()
                || "Villager".equalsIgnoreCase(trimmed)
                || (profComp != null && trimmed.equalsIgnoreCase(profComp.getString()))
                || trimmed.equalsIgnoreCase(village.getVillageName());
        if (!isGenericOrUnset) {
            return Component.literal(trimmed);
        }
        return profComp != null ? profComp : Component.literal(village.getVillageName());
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

    private static Predicate<SlotStack> buildOrderSlotMatcher(VillageTradeOrder order) {
        String optionalTag = resolveOrderOptionalTag(order);
        String orderItemId = order.itemId();
        return slot -> slot != null && slot.count() > 0 && matchesOrderItem(slot.itemId(), orderItemId, optionalTag);
    }

    private static Predicate<ItemStack> buildOrderStackMatcher(VillageTradeOrder order) {
        String optionalTag = resolveOrderOptionalTag(order);
        String orderItemId = order.itemId();
        return stack -> !stack.isEmpty() && matchesOrderItem(TyC2Ids.itemId(stack), orderItemId, optionalTag);
    }

    private static Predicate<ItemStack> buildOrderStackMatcher(String orderItemId) {
        String optionalTag = resolveOrderOptionalTag(orderItemId);
        return stack -> !stack.isEmpty() && matchesOrderItem(TyC2Ids.itemId(stack), orderItemId, optionalTag);
    }

    @Nullable
    private static String resolveOrderOptionalTag(VillageTradeOrder order) {
        Optional<TradeGood> goodOpt = TyC2RuntimeData.goodsCatalog().findOrderGood(
                order.displayHint(), order.itemId(), order.side() == OrderSide.VILLAGE_BUYS
        );
        return normalizeOptionalTag(goodOpt);
    }

    @Nullable
    private static String resolveOrderOptionalTag(String orderItemId) {
        Optional<TradeGood> goodOpt = TyC2RuntimeData.goodsCatalog().findOrderGood(null, orderItemId, true);
        return normalizeOptionalTag(goodOpt);
    }

    @Nullable
    private static String normalizeOptionalTag(Optional<TradeGood> goodOpt) {
        if (goodOpt.isEmpty()) {
            return null;
        }
        String raw = goodOpt.get().optionalTag();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.startsWith("#") ? raw : "#" + raw;
    }

    private static boolean matchesOrderItem(
            String candidateItemId,
            String orderItemId,
            @Nullable String optionalTag
    ) {
        if (candidateItemId == null || candidateItemId.isBlank()) {
            return false;
        }
        if (candidateItemId.equalsIgnoreCase(orderItemId)) {
            return true;
        }
        return optionalTag != null && TyC2Ids.matchesItemOrTag(candidateItemId, optionalTag);
    }

    private static int pullItemsFromPlayerIntoInbound(
            Inventory inv,
            VillageRecord village,
            String targetItemId,
            int maxNeeded
    ) {
        return pullMatchingItemsFromPlayerIntoInbound(inv, village, buildOrderStackMatcher(targetItemId), maxNeeded);
    }

    private static int pullMatchingItemsFromPlayerIntoInbound(
            Inventory inv,
            VillageRecord village,
            Predicate<ItemStack> matcher,
            int maxNeeded
    ) {
        int moved = 0;
        for (int i = 0; i < inv.getContainerSize() && moved < maxNeeded; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            String actualId = TyC2Ids.itemId(stack);
            if (actualId.isEmpty()) {
                continue;
            }
            int take = Math.min(stack.getCount(), maxNeeded - moved);
            int remainder = village.getStorage().insertExternal(
                    actualId, take, TyC2Ids.nbtJson(stack), stack.getMaxStackSize(), false
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
