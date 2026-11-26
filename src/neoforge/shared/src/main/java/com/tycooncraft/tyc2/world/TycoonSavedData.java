package com.tycooncraft.tyc2.world;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.platform.TyC2SavedDataBase;
import com.tycooncraft.tyc2.village.Aabb3i;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.ConstructionJob;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.Facing2D;
import com.tycooncraft.tyc2.village.MayorState;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.SpawnMarker;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import com.tycooncraft.tyc2.village.WorkplaceCatalog;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.village.WorkplaceSpec;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single Overworld-scoped {@link net.minecraft.world.level.saveddata.SavedData} persisting:
 * <ul>
 *   <li>{@link BankAccountManager} (Personal & Guild/Company accounts + 32-entry logs)</li>
 *   <li>{@link MacroEconomyEngine} (Money supply, goods circulation, CPI, 48-point history)</li>
 *   <li>{@link IndustrialTier.Tracker} (Player/Company industrial progression)</li>
 *   <li>All tracked {@link VillageRecord}s (including 1,000-slot warehouses & construction queues)</li>
 *   <li>Chunk-indexed {@code PlayerPlacedBlockTracker} preventing village expansion from overwriting player builds</li>
 * </ul>
 */
public final class TycoonSavedData extends TyC2SavedDataBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TycoonSavedData.class);

    private BankAccountManager bank = new BankAccountManager();
    private MacroEconomyEngine economy = new MacroEconomyEngine(TyC2DataLoader.config());
    private IndustrialTier.Tracker industrialTiers = new IndustrialTier.Tracker();
    private final Map<UUID, VillageRecord> villages = new LinkedHashMap<>();
    private final Map<String, Map<Long, Set<Long>>> playerPlacedByDimension = new HashMap<>();
    private long lastUpkeepDay = -1L;

    public TycoonSavedData() {
    }

    public BankAccountManager bank() {
        return bank;
    }

    public MacroEconomyEngine economy() {
        economy.setConfig(TyC2DataLoader.config());
        return economy;
    }

    public IndustrialTier.Tracker industrialTiers() {
        return industrialTiers;
    }

    public Map<UUID, VillageRecord> villagesMap() {
        return villages;
    }

    public Collection<VillageRecord> allVillages() {
        return Collections.unmodifiableCollection(villages.values());
    }

    public Optional<VillageRecord> getVillage(UUID villageId) {
        return villageId == null ? Optional.empty() : Optional.ofNullable(villages.get(villageId));
    }

    public void putVillage(VillageRecord village) {
        villages.put(village.getVillageId(), village);
        setDirty();
    }

    public long lastUpkeepDay() {
        return lastUpkeepDay;
    }

    public void setLastUpkeepDay(long day) {
        this.lastUpkeepDay = day;
        setDirty();
    }

    // --- PlayerPlacedBlockTracker (chunk-indexed) ---

    private static final int PLAYER_PLACED_VILLAGE_TRACK_RADIUS = 160;
    private static final int MAX_NBT_STRING_CHUNK = 24_000;

    private static long packChunkKey(int cx, int cz) {
        return (((long) cx) & 0xFFFFFFFFL) | ((((long) cz) & 0xFFFFFFFFL) << 32);
    }

    /**
     * Marks {@code pos} in {@code dimensionId} as player-placed if it lies within
     * {@value #PLAYER_PLACED_VILLAGE_TRACK_RADIUS} blocks of any tracked village in the same
     * dimension, bounding SavedData growth while protecting every village perimeter build.
     */
    public void recordPlayerPlaced(String dimensionId, BlockPos pos) {
        if (!isNearAnyTrackedVillage(dimensionId, pos.getX(), pos.getZ())) {
            return;
        }
        long chunkKey = packChunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        boolean added = playerPlacedByDimension
                .computeIfAbsent(dimensionId, ignored -> new HashMap<>())
                .computeIfAbsent(chunkKey, ignored -> new HashSet<>())
                .add(pos.asLong());
        if (added) {
            setDirty();
        }
    }

    private boolean isNearAnyTrackedVillage(String dimensionId, int x, int z) {
        long maxDistSq = (long) PLAYER_PLACED_VILLAGE_TRACK_RADIUS * PLAYER_PLACED_VILLAGE_TRACK_RADIUS;
        for (VillageRecord v : villages.values()) {
            if (!dimensionId.equals(v.getDimensionId())) {
                continue;
            }
            long dx = (long) x - v.getHubPos().x();
            long dz = (long) z - v.getHubPos().z();
            if (dx * dx + dz * dz <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    /** Clears {@code pos} from player-placed tracking when broken. */
    public void removePlayerPlaced(String dimensionId, BlockPos pos) {
        Map<Long, Set<Long>> byChunk = playerPlacedByDimension.get(dimensionId);
        if (byChunk == null) {
            return;
        }
        long chunkKey = packChunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<Long> positions = byChunk.get(chunkKey);
        if (positions != null && positions.remove(pos.asLong())) {
            if (positions.isEmpty()) {
                byChunk.remove(chunkKey);
            }
            setDirty();
        }
    }

    /** Returns {@code true} if a player placed a block at {@code (x, y, z)} in {@code dimensionId}. */
    public boolean isPlayerPlaced(String dimensionId, int x, int y, int z) {
        Map<Long, Set<Long>> byChunk = playerPlacedByDimension.get(dimensionId);
        if (byChunk == null) {
            return false;
        }
        Set<Long> positions = byChunk.get(packChunkKey(x >> 4, z >> 4));
        return positions != null && positions.contains(BlockPos.asLong(x, y, z));
    }

    // --- NBT Serialization ---

    @Override
    public CompoundTag saveToTag(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        putLargeString(root, "bank_json", bank.toJson());
        putLargeString(root, "economy_json", economy.toJson());
        putLargeString(root, "tiers_json", JsonLite.toJson(industrialTiers.toDataMap()));
        root.putLong("last_upkeep_day", lastUpkeepDay);

        ListTag villageList = new ListTag();
        for (VillageRecord v : villages.values()) {
            villageList.add(saveVillage(v));
        }
        root.put("villages", villageList);

        ListTag dimList = new ListTag();
        for (Map.Entry<String, Map<Long, Set<Long>>> dimEntry : playerPlacedByDimension.entrySet()) {
            CompoundTag dimTag = new CompoundTag();
            dimTag.putString("dim", dimEntry.getKey());
            ListTag chunkList = new ListTag();
            for (Map.Entry<Long, Set<Long>> cEntry : dimEntry.getValue().entrySet()) {
                if (cEntry.getValue().isEmpty()) {
                    continue;
                }
                CompoundTag cTag = new CompoundTag();
                cTag.putLong("chunk", cEntry.getKey());
                long[] packed = new long[cEntry.getValue().size()];
                int idx = 0;
                for (Long p : cEntry.getValue()) {
                    packed[idx++] = p;
                }
                cTag.putLongArray("pos", packed);
                chunkList.add(cTag);
            }
            dimTag.put("chunks", chunkList);
            dimList.add(dimTag);
        }
        root.put("player_placed", dimList);
        return root;
    }

    /** Restores a {@link TycoonSavedData} instance from NBT. */
    public static TycoonSavedData loadFromTag(CompoundTag root, HolderLookup.Provider registries) {
        TycoonSavedData data = new TycoonSavedData();
        String bankJson = getLargeString(root, "bank_json");
        if (!bankJson.isBlank()) {
            try {
                data.bank = BankAccountManager.fromJson(bankJson);
            } catch (Exception ex) {
                LOGGER.warn("Failed to load TyC2 BankAccountManager: {}", ex.getMessage());
            }
        }
        String econJson = getLargeString(root, "economy_json");
        if (!econJson.isBlank()) {
            try {
                data.economy = MacroEconomyEngine.fromJson(econJson, TyC2DataLoader.config());
            } catch (Exception ex) {
                LOGGER.warn("Failed to load TyC2 MacroEconomyEngine: {}", ex.getMessage());
            }
        }
        String tiersJson = getLargeString(root, "tiers_json");
        if (!tiersJson.isBlank()) {
            try {
                data.industrialTiers = IndustrialTier.Tracker.fromDataMap(JsonLite.parseObject(tiersJson));
            } catch (Exception ex) {
                LOGGER.warn("Failed to load TyC2 IndustrialTier tracker: {}", ex.getMessage());
            }
        }
        data.lastUpkeepDay = TyC2Nbt.getLong(root, "last_upkeep_day", -1L);

        for (CompoundTag vTag : TyC2Nbt.getCompoundList(root, "villages")) {
            try {
                VillageRecord v = loadVillage(vTag);
                data.villages.put(v.getVillageId(), v);
            } catch (Exception ex) {
                LOGGER.warn("Skipping malformed VillageRecord in SavedData: {}", ex.getMessage());
            }
        }

        for (CompoundTag dimTag : TyC2Nbt.getCompoundList(root, "player_placed")) {
            String dim = TyC2Nbt.getString(dimTag, "dim", "");
            if (dim.isBlank()) {
                continue;
            }
            Map<Long, Set<Long>> byChunk = data.playerPlacedByDimension.computeIfAbsent(dim, k -> new HashMap<>());
            for (CompoundTag cTag : TyC2Nbt.getCompoundList(dimTag, "chunks")) {
                long chunkKey = TyC2Nbt.getLong(cTag, "chunk", 0L);
                long[] packed = TyC2Nbt.getLongArray(cTag, "pos");
                if (packed.length > 0) {
                    Set<Long> set = byChunk.computeIfAbsent(chunkKey, k -> new HashSet<>(packed.length));
                    for (long p : packed) {
                        set.add(p);
                    }
                }
            }
        }
        return data;
    }

    private static CompoundTag saveVillage(VillageRecord v) {
        CompoundTag t = new CompoundTag();
        t.putString("id", v.getVillageId().toString());
        t.putString("dim", v.getDimensionId());
        t.putString("name", v.getVillageName());
        t.putInt("hx", v.getHubPos().x());
        t.putInt("hy", v.getHubPos().y());
        t.putInt("hz", v.getHubPos().z());
        t.putString("biome", v.getBiomeStyle().name());
        t.putBoolean("partnered", v.isPartnered());
        if (v.getOwnerPlayerUuid() != null) {
            t.putString("owner", v.getOwnerPlayerUuid().toString());
        }
        if (v.getCompanyAccountId() != null) {
            t.putString("company", v.getCompanyAccountId());
        }
        t.putInt("level", v.getLevel());
        t.putInt("pop", v.getPopulation());
        t.putLong("volume", v.getCumulativeTradeVolume());
        t.putLong("balance", v.getVillageBalance());
        t.putBoolean("exp_paused", v.isExpansionPaused());
        t.putInt("min_rad", v.getMinExpansionRadius());
        t.putInt("max_rad", v.getMaxExpansionRadius());
        t.putLong("last_demand_tick", v.getLastDemandRefreshTick());
        t.putLong("last_exp_tick", v.getLastExpansionTick());
        t.putBoolean("chunk_loader", v.isChunkLoaderInstalled());

        // MayorState
        MayorState ms = v.getMayorState();
        CompoundTag mTag = new CompoundTag();
        if (ms.getMayorEntityUuid() != null) {
            mTag.putString("uuid", ms.getMayorEntityUuid().toString());
        }
        mTag.putString("name", ms.getMayorName());
        mTag.putLong("elected_tick", ms.getElectedTick());
        mTag.putBoolean("reelect", ms.isReelectionPending());
        mTag.putBoolean("paused_depop", ms.isPausedDueToDepopulation());
        mTag.putInt("last_pop", ms.getLastKnownVillagerCount());
        mTag.putLong("last_attempt", ms.getLastElectionAttemptTick());
        mTag.putInt("elections", ms.getElectionsHeldCount());
        t.put("mayor", mTag);

        // Sparse 1,000-slot warehouse
        ListTag slotsTag = new ListTag();
        for (CooperationHubStorage.SlotEntry e : v.getStorage().exportNonEmptySlots()) {
            CompoundTag st = new CompoundTag();
            st.putInt("s", e.slot());
            st.putString("i", e.stack().itemId());
            st.putInt("c", e.stack().count());
            if (!e.stack().nbtJson().isEmpty()) {
                st.putString("n", e.stack().nbtJson());
            }
            slotsTag.add(st);
        }
        t.put("storage", slotsTag);

        // Active orders
        ListTag ordersTag = new ListTag();
        for (VillageTradeOrder o : v.getActiveOrders()) {
            CompoundTag ot = new CompoundTag();
            ot.putString("id", o.orderId());
            ot.putString("side", o.side().name());
            ot.putString("item", o.itemId());
            ot.putString("hint", o.displayHint());
            ot.putString("ench", o.enchantSpec());
            ot.putInt("total", o.totalQty());
            ot.putInt("done", o.fulfilledQty());
            ot.putLong("price", o.unitPrice());
            ot.putLong("created", o.createdTick());
            ot.putLong("expires", o.expiresTick());
            ot.putBoolean("auto", o.autoFulfill());
            ot.putBoolean("season", o.seasonalBonus());
            ordersTag.add(ot);
        }
        t.put("orders", ordersTag);

        // Active ExpansionDesire
        if (v.getActiveExpansion() != null) {
            ExpansionDesire ed = v.getActiveExpansion();
            CompoundTag edTag = new CompoundTag();
            edTag.putString("id", ed.desireId());
            edTag.putString("type", ed.projectType().name());
            edTag.putLong("budget", ed.constructionBudgetCost());
            edTag.putBoolean("done", ed.completed());
            edTag.putBoolean("auto", ed.autoFulfill());
            edTag.putString("req_json", JsonLite.toJson(new LinkedHashMap<>(ed.requiredMaterials())));
            edTag.putString("del_json", JsonLite.toJson(new LinkedHashMap<>(ed.deliveredMaterials())));
            t.put("expansion_desire", edTag);
        }

        // Occupied zones
        ListTag zonesTag = new ListTag();
        for (Aabb3i z : v.getOccupiedZones()) {
            CompoundTag zt = new CompoundTag();
            zt.putInt("x0", z.minX());
            zt.putInt("y0", z.minY());
            zt.putInt("z0", z.minZ());
            zt.putInt("x1", z.maxX());
            zt.putInt("y1", z.maxY());
            zt.putInt("z1", z.maxZ());
            zonesTag.add(zt);
        }
        t.put("zones", zonesTag);

        // Reserved LC2 pads
        ListTag padsTag = new ListTag();
        for (BlockPos3i p : v.getReservedLc2Pads()) {
            CompoundTag pt = new CompoundTag();
            pt.putInt("x", p.x());
            pt.putInt("y", p.y());
            pt.putInt("z", p.z());
            padsTag.add(pt);
        }
        t.put("lc2_pads", padsTag);

        // Construction queue
        ListTag jobsTag = new ListTag();
        for (ConstructionJob job : v.getConstructionQueue()) {
            if (job.isCompleted()) {
                continue;
            }
            CompoundTag jt = new CompoundTag();
            jt.putString("id", job.getJobId());
            jt.putString("type", job.getProjectType().name());
            jt.putString("biome", job.getBiomeStyle().name());
            jt.putString("facing", job.getFacing().name());
            jt.putInt("ox", job.getOrigin().x());
            jt.putInt("oy", job.getOrigin().y());
            jt.putInt("oz", job.getOrigin().z());
            Aabb3i bb = job.getBoundingBox();
            jt.putInt("bx0", bb.minX());
            jt.putInt("by0", bb.minY());
            jt.putInt("bz0", bb.minZ());
            jt.putInt("bx1", bb.maxX());
            jt.putInt("by1", bb.maxY());
            jt.putInt("bz1", bb.maxZ());
            jt.putInt("bps", job.getBlocksPerStep());
            jt.putInt("tps", job.getTicksPerStep());
            jt.putInt("next", job.getNextIndex());
            jt.putInt("acc", job.getTickAccumulator());

            ListTag pList = new ListTag();
            for (WorldBlockPlacement wp : job.getPlacements()) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("x", wp.pos().x());
                pt.putInt("y", wp.pos().y());
                pt.putInt("z", wp.pos().z());
                pt.putString("s", wp.blockStateSpec());
                pList.add(pt);
            }
            jt.put("blocks", pList);

            ListTag mList = new ListTag();
            for (SpawnMarker.WorldSpawnMarker sm : job.getSpawnMarkers()) {
                CompoundTag smt = new CompoundTag();
                smt.putString("k", sm.kind().name());
                smt.putInt("x", sm.pos().x());
                smt.putInt("y", sm.pos().y());
                smt.putInt("z", sm.pos().z());
                smt.putString("h", sm.hint());
                mList.add(smt);
            }
            jt.put("markers", mList);
            jobsTag.add(jt);
        }
        t.put("jobs", jobsTag);

        // Aid construction projects
        ListTag aidTag = new ListTag();
        for (AidConstructionProject ap : v.getAidProjects()) {
            CompoundTag at = new CompoundTag();
            at.putString("id", ap.projectId());
            at.putString("prof", ap.professionId());
            at.putString("block", ap.workstationBlockId());
            at.putString("arch", ap.archetype().name());
            if (!ap.displayNameFallback().isEmpty()) {
                at.putString("fallback", ap.displayNameFallback());
            }
            at.putString("size", ap.size().name());
            at.putString("status", ap.status().name());
            at.putLong("paid", ap.paidIc());
            at.putString("req_json", JsonLite.toJson(new LinkedHashMap<>(ap.requiredMaterials())));
            at.putDouble("gathered", ap.gatheredProgressTicks());
            at.putInt("req_ticks", ap.requiredTicks());
            if (ap.reservedZone() != null) {
                Aabb3i rz = ap.reservedZone();
                at.putBoolean("rz_has", true);
                at.putInt("rz_x0", rz.minX());
                at.putInt("rz_y0", rz.minY());
                at.putInt("rz_z0", rz.minZ());
                at.putInt("rz_x1", rz.maxX());
                at.putInt("rz_y1", rz.maxY());
                at.putInt("rz_z1", rz.maxZ());
            }
            if (!ap.plannedJobId().isEmpty()) {
                at.putString("job_id", ap.plannedJobId());
            }
            if (ap.payerPlayerUuid() != null) {
                at.putString("payer_uuid", ap.payerPlayerUuid().toString());
            }
            if (ap.payerCompanyId() != null) {
                at.putString("payer_co", ap.payerCompanyId());
            }
            at.putBoolean("paid_cash", ap.paidWithCash());
            aidTag.add(at);
        }
        t.put("aid_projects", aidTag);

        // VillageEmploymentStats
        VillageEmploymentStats es = v.getEmploymentStats();
        CompoundTag empTag = new CompoundTag();
        empTag.putInt("wf", es.workforceCount());
        empTag.putInt("emp", es.employedCount());
        empTag.putInt("unemp", es.unemployedCount());
        empTag.putBoolean("mayor_emp", es.mayorCountedEmployed());
        empTag.putString("profs_json", JsonLite.toJson(new LinkedHashMap<>(es.activeProfessionCounts())));
        empTag.putLong("scan_tick", es.lastScanTick());
        t.put("employment", empTag);
        return t;
    }

    private static VillageRecord loadVillage(CompoundTag t) {
        UUID id = UUID.fromString(TyC2Nbt.getString(t, "id", UUID.randomUUID().toString()));
        String dim = TyC2Nbt.getString(t, "dim", "minecraft:overworld");
        String name = TyC2Nbt.getString(t, "name", "Village");
        BlockPos3i hubPos = new BlockPos3i(
                TyC2Nbt.getInt(t, "hx", 0),
                TyC2Nbt.getInt(t, "hy", 64),
                TyC2Nbt.getInt(t, "hz", 0)
        );
        BiomeStyle biome = BiomeStyle.fromNameOrDefault(TyC2Nbt.getString(t, "biome", "PLAINS"), BiomeStyle.PLAINS);
        boolean partnered = TyC2Nbt.getBoolean(t, "partnered", false);
        String ownerStr = TyC2Nbt.getString(t, "owner", "");
        UUID ownerUuid = ownerStr.isBlank() ? null : UUID.fromString(ownerStr);
        String companyId = TyC2Nbt.getString(t, "company", "");
        int level = TyC2Nbt.getInt(t, "level", 1);
        int pop = TyC2Nbt.getInt(t, "pop", 0);
        long volume = TyC2Nbt.getLong(t, "volume", 0L);
        long balance = TyC2Nbt.getLong(t, "balance", 0L);

        CompoundTag mTag = TyC2Nbt.getCompound(t, "mayor");
        String mayorUuidStr = TyC2Nbt.getString(mTag, "uuid", "");
        UUID mayorUuid = mayorUuidStr.isBlank() ? null : UUID.fromString(mayorUuidStr);
        MayorState mayorState = new MayorState(
                mayorUuid,
                TyC2Nbt.getString(mTag, "name", ""),
                TyC2Nbt.getLong(mTag, "elected_tick", 0L),
                TyC2Nbt.getBoolean(mTag, "reelect", false),
                TyC2Nbt.getBoolean(mTag, "paused_depop", false),
                TyC2Nbt.getInt(mTag, "last_pop", 0),
                TyC2Nbt.getLong(mTag, "last_attempt", 0L),
                TyC2Nbt.getInt(mTag, "elections", 0)
        );

        CooperationHubStorage storage = new CooperationHubStorage();
        List<CooperationHubStorage.SlotEntry> entries = new ArrayList<>();
        for (CompoundTag st : TyC2Nbt.getCompoundList(t, "storage")) {
            int slot = TyC2Nbt.getInt(st, "s", -1);
            String itemId = TyC2Nbt.getString(st, "i", "");
            int count = TyC2Nbt.getInt(st, "c", 0);
            String nbt = TyC2Nbt.getString(st, "n", "");
            if (slot >= 0 && slot < CooperationHubStorage.TOTAL_SLOTS && !itemId.isBlank() && count > 0) {
                entries.add(new CooperationHubStorage.SlotEntry(slot, new SlotStack(itemId, count, nbt)));
            }
        }
        storage.importSlots(entries);

        List<VillageTradeOrder> orders = new ArrayList<>();
        for (CompoundTag ot : TyC2Nbt.getCompoundList(t, "orders")) {
            try {
                orders.add(new VillageTradeOrder(
                        TyC2Nbt.getString(ot, "id", "order"),
                        OrderSide.valueOf(TyC2Nbt.getString(ot, "side", "VILLAGE_SELLS")),
                        TyC2Nbt.getString(ot, "item", "minecraft:wheat"),
                        TyC2Nbt.getString(ot, "hint", ""),
                        TyC2Nbt.getString(ot, "ench", ""),
                        Math.max(1, TyC2Nbt.getInt(ot, "total", 1)),
                        Math.max(0, TyC2Nbt.getInt(ot, "done", 0)),
                        Math.max(1L, TyC2Nbt.getLong(ot, "price", 1L)),
                        TyC2Nbt.getLong(ot, "created", 0L),
                        TyC2Nbt.getLong(ot, "expires", 12000L),
                        TyC2Nbt.getBoolean(ot, "auto", false),
                        TyC2Nbt.getBoolean(ot, "season", false)
                ));
            } catch (Exception ignored) {
                // Skip invalid order
            }
        }

        ExpansionDesire expansion = null;
        if (t.contains("expansion_desire")) {
            CompoundTag edTag = TyC2Nbt.getCompound(t, "expansion_desire");
            String edId = TyC2Nbt.getString(edTag, "id", "");
            if (!edId.isBlank()) {
                ProjectType pt = ProjectType.valueOf(TyC2Nbt.getString(edTag, "type", "SMALL_COTTAGE"));
                Map<String, Integer> req = parseIntMap(TyC2Nbt.getString(edTag, "req_json", "{}"));
                Map<String, Integer> del = parseIntMap(TyC2Nbt.getString(edTag, "del_json", "{}"));
                if (!req.isEmpty()) {
                    expansion = new ExpansionDesire(
                            edId,
                            pt,
                            req,
                            del,
                            TyC2Nbt.getLong(edTag, "budget", pt.defaultBudgetCost()),
                            TyC2Nbt.getBoolean(edTag, "done", false),
                            TyC2Nbt.getBoolean(edTag, "auto", false)
                    );
                }
            }
        }

        List<Aabb3i> zones = new ArrayList<>();
        for (CompoundTag zt : TyC2Nbt.getCompoundList(t, "zones")) {
            zones.add(Aabb3i.of(
                    TyC2Nbt.getInt(zt, "x0", 0), TyC2Nbt.getInt(zt, "y0", 0), TyC2Nbt.getInt(zt, "z0", 0),
                    TyC2Nbt.getInt(zt, "x1", 0), TyC2Nbt.getInt(zt, "y1", 0), TyC2Nbt.getInt(zt, "z1", 0)
            ));
        }

        List<BlockPos3i> pads = new ArrayList<>();
        for (CompoundTag pt : TyC2Nbt.getCompoundList(t, "lc2_pads")) {
            pads.add(new BlockPos3i(
                    TyC2Nbt.getInt(pt, "x", 0), TyC2Nbt.getInt(pt, "y", 0), TyC2Nbt.getInt(pt, "z", 0)
            ));
        }

        List<ConstructionJob> jobs = new ArrayList<>();
        for (CompoundTag jt : TyC2Nbt.getCompoundList(t, "jobs")) {
            try {
                List<WorldBlockPlacement> blocks = new ArrayList<>();
                for (CompoundTag bt : TyC2Nbt.getCompoundList(jt, "blocks")) {
                    blocks.add(new WorldBlockPlacement(
                            new BlockPos3i(
                                    TyC2Nbt.getInt(bt, "x", 0),
                                    TyC2Nbt.getInt(bt, "y", 0),
                                    TyC2Nbt.getInt(bt, "z", 0)
                            ),
                            TyC2Nbt.getString(bt, "s", "minecraft:cobblestone")
                    ));
                }
                List<SpawnMarker.WorldSpawnMarker> markers = new ArrayList<>();
                for (CompoundTag smt : TyC2Nbt.getCompoundList(jt, "markers")) {
                    markers.add(new SpawnMarker.WorldSpawnMarker(
                            SpawnMarker.MarkerKind.valueOf(TyC2Nbt.getString(smt, "k", "VILLAGER")),
                            new BlockPos3i(
                                    TyC2Nbt.getInt(smt, "x", 0),
                                    TyC2Nbt.getInt(smt, "y", 0),
                                    TyC2Nbt.getInt(smt, "z", 0)
                            ),
                            TyC2Nbt.getString(smt, "h", "")
                    ));
                }
                jobs.add(new ConstructionJob(
                        TyC2Nbt.getString(jt, "id", "job"),
                        ProjectType.valueOf(TyC2Nbt.getString(jt, "type", "SMALL_COTTAGE")),
                        BiomeStyle.fromNameOrDefault(TyC2Nbt.getString(jt, "biome", "PLAINS"), BiomeStyle.PLAINS),
                        Facing2D.fromNameOrDefault(TyC2Nbt.getString(jt, "facing", "NORTH"), Facing2D.NORTH),
                        new BlockPos3i(
                                TyC2Nbt.getInt(jt, "ox", 0),
                                TyC2Nbt.getInt(jt, "oy", 64),
                                TyC2Nbt.getInt(jt, "oz", 0)
                        ),
                        Aabb3i.of(
                                TyC2Nbt.getInt(jt, "bx0", 0), TyC2Nbt.getInt(jt, "by0", 0), TyC2Nbt.getInt(jt, "bz0", 0),
                                TyC2Nbt.getInt(jt, "bx1", 0), TyC2Nbt.getInt(jt, "by1", 0), TyC2Nbt.getInt(jt, "bz1", 0)
                        ),
                        blocks,
                        markers,
                        Math.max(1, TyC2Nbt.getInt(jt, "bps", ConstructionJob.DEFAULT_BLOCKS_PER_STEP)),
                        Math.max(1, TyC2Nbt.getInt(jt, "tps", ConstructionJob.DEFAULT_TICKS_PER_STEP)),
                        Math.max(0, TyC2Nbt.getInt(jt, "next", 0)),
                        Math.max(0, TyC2Nbt.getInt(jt, "acc", 0)),
                        false
                ));
            } catch (Exception ignored) {
                // Skip invalid job
            }
        }

        List<AidConstructionProject> aidProjects = new ArrayList<>();
        for (CompoundTag at : TyC2Nbt.getCompoundList(t, "aid_projects")) {
            try {
                String apId = TyC2Nbt.getString(at, "id", "");
                String profId = TyC2Nbt.getString(at, "prof", "");
                String blockId = TyC2Nbt.getString(at, "block", "");
                if (apId.isBlank() || profId.isBlank() || blockId.isBlank()) {
                    continue;
                }
                WorkplaceArchetype arch = WorkplaceArchetype.fromNameOrDefault(
                        TyC2Nbt.getString(at, "arch", "GENERIC"), WorkplaceArchetype.GENERIC
                );
                String fallback = TyC2Nbt.getString(at, "fallback", "");
                WorkplaceSize size = WorkplaceSize.fromNameOrDefault(
                        TyC2Nbt.getString(at, "size", "SMALL"), WorkplaceSize.SMALL
                );
                AidConstructionProject.Status status;
                try {
                    status = AidConstructionProject.Status.valueOf(TyC2Nbt.getString(at, "status", "COLLECTING"));
                } catch (IllegalArgumentException ex) {
                    status = AidConstructionProject.Status.COLLECTING;
                }
                long paidIc = Math.max(0L, TyC2Nbt.getLong(at, "paid", 0L));
                Map<String, Integer> reqMats = parseIntMap(TyC2Nbt.getString(at, "req_json", "{}"));
                if (reqMats.isEmpty()) {
                    WorkplaceSpec tmpSpec = new WorkplaceSpec(
                            profId, blockId, arch, Math.max(1L, paidIc),
                            size.defaultCostMultiplier(), 1, 3, List.of(), true, false, fallback
                    );
                    reqMats = WorkplaceCatalog.requiredMaterials(tmpSpec, size, biome);
                }
                double gathered = TyC2Nbt.getDouble(at, "gathered", 0.0);
                int reqTicks = Math.max(1, TyC2Nbt.getInt(at, "req_ticks", size.defaultGatherTicks()));
                Aabb3i reservedZone = null;
                if (TyC2Nbt.getBoolean(at, "rz_has", false)) {
                    reservedZone = Aabb3i.of(
                            TyC2Nbt.getInt(at, "rz_x0", 0), TyC2Nbt.getInt(at, "rz_y0", 0), TyC2Nbt.getInt(at, "rz_z0", 0),
                            TyC2Nbt.getInt(at, "rz_x1", 0), TyC2Nbt.getInt(at, "rz_y1", 0), TyC2Nbt.getInt(at, "rz_z1", 0)
                    );
                }
                String jobId = TyC2Nbt.getString(at, "job_id", "");
                String payerUuidStr = TyC2Nbt.getString(at, "payer_uuid", "");
                UUID payerUuid = null;
                if (!payerUuidStr.isBlank()) {
                    try {
                        payerUuid = UUID.fromString(payerUuidStr);
                    } catch (IllegalArgumentException ignored) {
                        payerUuid = null;
                    }
                }
                String payerCo = TyC2Nbt.getString(at, "payer_co", "");
                boolean paidCash = TyC2Nbt.getBoolean(at, "paid_cash", false);
                aidProjects.add(new AidConstructionProject(
                        apId,
                        profId,
                        blockId,
                        arch,
                        fallback,
                        size,
                        status,
                        paidIc,
                        reqMats,
                        gathered,
                        reqTicks,
                        reservedZone,
                        jobId,
                        payerUuid,
                        payerCo.isBlank() ? null : payerCo,
                        paidCash
                ));
            } catch (Exception ignored) {
                // Skip invalid aid project entry cleanly
            }
        }

        VillageEmploymentStats employmentStats = VillageEmploymentStats.EMPTY;
        if (t.contains("employment")) {
            CompoundTag empTag = TyC2Nbt.getCompound(t, "employment");
            int wf = Math.max(0, TyC2Nbt.getInt(empTag, "wf", 0));
            int emp = Math.max(0, TyC2Nbt.getInt(empTag, "emp", 0));
            int unemp = Math.max(0, TyC2Nbt.getInt(empTag, "unemp", Math.max(0, wf - emp)));
            boolean mayorEmp = TyC2Nbt.getBoolean(empTag, "mayor_emp", false);
            Map<String, Integer> profCounts = parseIntMap(TyC2Nbt.getString(empTag, "profs_json", "{}"));
            long scanTick = Math.max(0L, TyC2Nbt.getLong(empTag, "scan_tick", 0L));
            employmentStats = new VillageEmploymentStats(wf, emp, unemp, mayorEmp, profCounts, scanTick);
        }

        VillageRecord record = new VillageRecord(
                id,
                dim,
                name,
                hubPos,
                biome,
                partnered,
                ownerUuid,
                companyId.isBlank() ? null : companyId,
                level,
                pop,
                volume,
                balance,
                mayorState,
                storage,
                orders,
                expansion,
                zones,
                pads,
                TyC2Nbt.getBoolean(t, "exp_paused", false),
                TyC2Nbt.getInt(t, "min_rad", VillageRecord.MIN_CONFIGURABLE_RADIUS),
                TyC2Nbt.getInt(t, "max_rad", VillageRecord.MAX_CONFIGURABLE_RADIUS),
                TyC2Nbt.getLong(t, "last_demand_tick", 0L),
                TyC2Nbt.getLong(t, "last_exp_tick", 0L),
                jobs,
                aidProjects,
                employmentStats
        );
        record.setChunkLoaderInstalled(TyC2Nbt.getBoolean(t, "chunk_loader", false));
        return record;
    }

    private static void putLargeString(CompoundTag root, String key, String value) {
        if (value == null) {
            value = "";
        }
        String chunksKey = key + "_chunks";
        if (value.length() <= MAX_NBT_STRING_CHUNK) {
            root.putString(key, value);
            root.remove(chunksKey);
            return;
        }
        ListTag chunks = new ListTag();
        for (int start = 0; start < value.length(); start += MAX_NBT_STRING_CHUNK) {
            int end = Math.min(value.length(), start + MAX_NBT_STRING_CHUNK);
            CompoundTag part = new CompoundTag();
            part.putString("v", value.substring(start, end));
            chunks.add(part);
        }
        root.put(chunksKey, chunks);
        root.remove(key);
    }

    private static String getLargeString(CompoundTag root, String key) {
        String chunksKey = key + "_chunks";
        List<CompoundTag> chunks = TyC2Nbt.getCompoundList(root, chunksKey);
        if (!chunks.isEmpty()) {
            StringBuilder sb = new StringBuilder(chunks.size() * MAX_NBT_STRING_CHUNK);
            for (CompoundTag part : chunks) {
                sb.append(TyC2Nbt.getString(part, "v", ""));
            }
            return sb.toString();
        }
        return TyC2Nbt.getString(root, key, "");
    }

    private static Map<String, Integer> parseIntMap(String json) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> raw = JsonLite.parseObject(json);
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Number n && n.intValue() > 0) {
                    result.put(e.getKey(), n.intValue());
                }
            }
        } catch (Exception ignored) {
            // Return empty map
        }
        return result;
    }
}
