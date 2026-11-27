package com.tycooncraft.tyc2.forge.world;

import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.JsonLite;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Overworld-anchored {@link SavedData} holding all persistent TycoonCraft² state:
 * banking accounts, macro-economic CPI & history, player industrial tier tracker,
 * every village record (including 1,000-slot Cooperation Hub warehouses, Mayor states,
 * active orders, expansion desires, and gradual construction queues), and player-placed blocks.
 */
public final class TycoonForgeSavedData extends SavedData {

    public static final String DATA_NAME = "tyc2_world_economy";
    private static final int LARGE_STRING_CHUNK = 24_000;
    private static final Logger LOGGER = LoggerFactory.getLogger("tyc2");

    private BankAccountManager bankAccounts;
    private MacroEconomyEngine macroEconomy;
    private IndustrialTier.Tracker tierTracker;
    private final Map<UUID, VillageRecord> villages = new LinkedHashMap<>();
    private PlayerPlacedBlockTracker playerPlacedBlocks;
    private long lastUpkeepDay = -1L;
    private int nextVillageNumber = 1;

    public TycoonForgeSavedData() {
        this.bankAccounts = new BankAccountManager();
        this.macroEconomy = new MacroEconomyEngine(TyC2RuntimeData.config());
        this.tierTracker = new IndustrialTier.Tracker();
        this.playerPlacedBlocks = new PlayerPlacedBlockTracker();
    }

    /** Retrieves or creates the singleton {@link TycoonForgeSavedData} from the server's Overworld. */
    public static TycoonForgeSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TycoonForgeSavedData::load,
                TycoonForgeSavedData::new,
                DATA_NAME
        );
    }

    /** Retrieves or creates the singleton {@link TycoonForgeSavedData} from any {@link ServerLevel}. */
    public static TycoonForgeSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public BankAccountManager bank() {
        return bankAccounts;
    }

    public MacroEconomyEngine economy() {
        macroEconomy.setConfig(TyC2RuntimeData.config());
        return macroEconomy;
    }

    public IndustrialTier.Tracker tiers() {
        return tierTracker;
    }

    public PlayerPlacedBlockTracker placedBlocks() {
        return playerPlacedBlocks;
    }

    public long lastUpkeepDay() {
        return lastUpkeepDay;
    }

    public void setLastUpkeepDay(long day) {
        this.lastUpkeepDay = day;
        setDirty();
    }

    public String allocateDefaultVillageName(BiomeStyle style) {
        int num = nextVillageNumber++;
        setDirty();
        String prefix = switch (style) {
            case DESERT -> "Oasis";
            case SAVANNA -> "Acacia";
            case TAIGA -> "Pine";
            case SNOWY -> "Frost";
            case JUNGLE -> "Canopy";
            case SWAMP -> "Willow";
            case PLAINS -> "Oakfield";
        };
        return prefix + " #" + num;
    }

    public Map<UUID, VillageRecord> villagesMap() {
        return Collections.unmodifiableMap(villages);
    }

    public Collection<VillageRecord> allVillages() {
        return Collections.unmodifiableCollection(villages.values());
    }

    public Optional<VillageRecord> getVillage(@Nullable UUID villageId) {
        return villageId == null ? Optional.empty() : Optional.ofNullable(villages.get(villageId));
    }

    public void putVillage(VillageRecord record) {
        villages.put(record.getVillageId(), record);
        setDirty();
    }

    public void removeVillage(UUID villageId) {
        if (villages.remove(villageId) != null) {
            setDirty();
        }
    }

    /** Finds a village whose Hub/Bell is within {@code maxDistance} blocks in {@code dimensionId}. */
    public Optional<VillageRecord> findNearestVillage(String dimensionId, BlockPos pos, int maxDistance) {
        VillageRecord best = null;
        long bestDistSq = (long) maxDistance * maxDistance;
        for (VillageRecord v : villages.values()) {
            if (!v.getDimensionId().equals(dimensionId)) {
                continue;
            }
            long dx = v.getHubPos().x() - pos.getX();
            long dz = v.getHubPos().z() - pos.getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = v;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Finds a village whose exact Hub position matches {@code pos} in {@code dimensionId}. */
    public Optional<VillageRecord> findVillageAtHub(String dimensionId, BlockPos pos) {
        for (VillageRecord v : villages.values()) {
            if (v.getDimensionId().equals(dimensionId)
                    && v.getHubPos().x() == pos.getX()
                    && v.getHubPos().y() == pos.getY()
                    && v.getHubPos().z() == pos.getZ()) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    /** Synchronizes aggregate player/guild/village balances into {@link MacroEconomyEngine}. */
    public void syncMacroAccountTotals() {
        long villageSum = 0L;
        int partneredCount = 0;
        for (VillageRecord v : villages.values()) {
            // Include unpartnered villages' remaining treasuries so terminating partnership cannot cloak money supply
            if (v.getVillageBalance() > 0L) {
                villageSum += v.getVillageBalance();
            }
            if (v.isPartnered()) {
                partneredCount++;
            }
        }
        economy().syncAccountTotals(bankAccounts.getTotalCombinedBalance(), villageSum, Math.max(1, partneredCount));
    }

    /** Returns the highest industrial tier unlocked by the village's owner or company members. */
    public IndustrialTier resolveVillageOwnerTier(VillageRecord village) {
        if (village.getCompanyAccountId() != null) {
            Optional<BankAccountManager.CompanyAccount> co = bankAccounts.getCompany(village.getCompanyAccountId());
            if (co.isPresent()) {
                return tierTracker.getMaxTier(co.get().memberUuids());
            }
        }
        return tierTracker.getTier(village.getOwnerPlayerUuid());
    }

    // --- NBT Serialization ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        putLargeString(tag, "BankJson", bankAccounts.toJson());
        putLargeString(tag, "MacroJson", macroEconomy.toJson());
        putLargeString(tag, "TiersJson", JsonLite.toJson(tierTracker.toDataMap()));
        tag.put("PlayerPlaced", playerPlacedBlocks.save());
        tag.putLong("LastUpkeepDay", lastUpkeepDay);
        tag.putInt("NextVillageNum", nextVillageNumber);

        ListTag villageList = new ListTag();
        for (VillageRecord v : villages.values()) {
            villageList.add(saveVillage(v));
        }
        tag.put("Villages", villageList);
        return tag;
    }

    public static TycoonForgeSavedData load(CompoundTag tag) {
        TycoonForgeSavedData data = new TycoonForgeSavedData();
        try {
            String bankJson = getLargeString(tag, "BankJson");
            if (!bankJson.isBlank()) {
                data.bankAccounts = BankAccountManager.fromJson(bankJson);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore TyC2 BankAccountManager from SavedData: {}", e.getMessage());
        }
        try {
            String macroJson = getLargeString(tag, "MacroJson");
            if (!macroJson.isBlank()) {
                data.macroEconomy = MacroEconomyEngine.fromJson(macroJson, TyC2RuntimeData.config());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore TyC2 MacroEconomyEngine from SavedData: {}", e.getMessage());
        }
        try {
            String tiersJson = getLargeString(tag, "TiersJson");
            if (!tiersJson.isBlank()) {
                data.tierTracker = IndustrialTier.Tracker.fromDataMap(JsonLite.parseObject(tiersJson));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore TyC2 IndustrialTier.Tracker from SavedData: {}", e.getMessage());
        }
        if (tag.contains("PlayerPlaced", Tag.TAG_COMPOUND)) {
            data.playerPlacedBlocks = PlayerPlacedBlockTracker.load(tag.getCompound("PlayerPlaced"));
        }
        data.lastUpkeepDay = tag.contains("LastUpkeepDay") ? tag.getLong("LastUpkeepDay") : -1L;
        data.nextVillageNumber = Math.max(1, tag.getInt("NextVillageNum"));

        ListTag villageList = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < villageList.size(); i++) {
            try {
                VillageRecord v = loadVillage(villageList.getCompound(i));
                data.villages.put(v.getVillageId(), v);
            } catch (Exception e) {
                LOGGER.warn("Skipping corrupted TyC2 village entry #{}: {}", i, e.getMessage());
            }
        }
        data.syncMacroAccountTotals();
        return data;
    }

    private static CompoundTag saveVillage(VillageRecord v) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", v.getVillageId());
        t.putString("Dim", v.getDimensionId());
        t.putString("Name", v.getVillageName());
        t.putIntArray("HubPos", new int[]{v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z()});
        t.putString("Biome", v.getBiomeStyle().name());
        t.putBoolean("Partnered", v.isPartnered());
        if (v.getOwnerPlayerUuid() != null) {
            t.putUUID("Owner", v.getOwnerPlayerUuid());
        }
        if (v.getCompanyAccountId() != null) {
            t.putString("CompanyId", v.getCompanyAccountId());
        }
        t.putInt("Level", v.getLevel());
        t.putInt("Pop", v.getPopulation());
        t.putLong("TradeVol", v.getCumulativeTradeVolume());
        t.putLong("Balance", v.getVillageBalance());
        t.putBoolean("ExpPaused", v.isExpansionPaused());
        t.putInt("MinRad", v.getMinExpansionRadius());
        t.putInt("MaxRad", v.getMaxExpansionRadius());
        t.putLong("LastDemandTick", v.getLastDemandRefreshTick());
        t.putBoolean("OrdersInitialized", v.hasInitialOrdersGenerated());
        t.putLong("LastStipendTick", v.getLastStipendTick());
        t.putBoolean("InitWcGranted", v.isInitialWorkingCapitalGranted());
        t.putLong("LastTermTick", v.getLastTerminationTick());
        t.putBoolean("HubDeployed", v.isHubDeployed());
        CompoundTag aidDoneTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : v.getCompletedAidCounts().entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && e.getValue() > 0) {
                aidDoneTag.putInt(e.getKey(), e.getValue());
            }
        }
        t.put("AidCompleted", aidDoneTag);
        t.putLong("LastExpTick", v.getLastExpansionTick());

        // MayorState
        MayorState ms = v.getMayorState();
        CompoundTag mTag = new CompoundTag();
        if (ms.getMayorEntityUuid() != null) {
            mTag.putUUID("Uuid", ms.getMayorEntityUuid());
        }
        mTag.putString("Name", ms.getMayorName());
        mTag.putLong("ElectedTick", ms.getElectedTick());
        mTag.putBoolean("Reelect", ms.isReelectionPending());
        mTag.putBoolean("Paused", ms.isPausedDueToDepopulation());
        mTag.putInt("LastPop", ms.getLastKnownVillagerCount());
        mTag.putLong("LastAttempt", ms.getLastElectionAttemptTick());
        mTag.putInt("Elections", ms.getElectionsHeldCount());
        t.put("Mayor", mTag);

        // 1,000-slot sparse warehouse
        ListTag storageList = new ListTag();
        for (CooperationHubStorage.SlotEntry entry : v.getStorage().exportNonEmptySlots()) {
            CompoundTag sTag = new CompoundTag();
            sTag.putInt("S", entry.slot());
            sTag.putString("I", entry.stack().itemId());
            sTag.putInt("C", entry.stack().count());
            if (!entry.stack().nbtJson().isEmpty()) {
                sTag.putString("N", entry.stack().nbtJson());
            }
            storageList.add(sTag);
        }
        t.put("Storage", storageList);

        // Active orders
        ListTag orderList = new ListTag();
        for (VillageTradeOrder o : v.getActiveOrders()) {
            CompoundTag oTag = new CompoundTag();
            oTag.putString("Id", o.orderId());
            oTag.putString("Side", o.side().name());
            oTag.putString("Item", o.itemId());
            oTag.putString("Hint", o.displayHint());
            oTag.putString("Ench", o.enchantSpec());
            oTag.putInt("Total", o.totalQty());
            oTag.putInt("Done", o.fulfilledQty());
            oTag.putLong("Price", o.unitPrice());
            oTag.putLong("Created", o.createdTick());
            oTag.putLong("Expires", o.expiresTick());
            oTag.putBoolean("Auto", o.autoFulfill());
            oTag.putBoolean("Seasonal", o.seasonalBonus());
            oTag.putBoolean("BiomeSpecialty", o.biomeSpecialty());
            orderList.add(oTag);
        }
        t.put("Orders", orderList);

        // Active expansion desire
        ExpansionDesire exp = v.getActiveExpansion();
        if (exp != null) {
            CompoundTag eTag = new CompoundTag();
            eTag.putString("Id", exp.desireId());
            eTag.putString("Type", exp.projectType().name());
            eTag.putLong("Budget", exp.constructionBudgetCost());
            eTag.putBoolean("Completed", exp.completed());
            eTag.putBoolean("Auto", exp.autoFulfill());
            CompoundTag reqTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : exp.requiredMaterials().entrySet()) {
                reqTag.putInt(e.getKey(), e.getValue());
            }
            eTag.put("Req", reqTag);
            CompoundTag delTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : exp.deliveredMaterials().entrySet()) {
                delTag.putInt(e.getKey(), e.getValue());
            }
            eTag.put("Del", delTag);
            t.put("Expansion", eTag);
        }

        // Occupied zones
        ListTag zonesList = new ListTag();
        for (Aabb3i z : v.getOccupiedZones()) {
            CompoundTag zTag = new CompoundTag();
            zTag.putIntArray("B", new int[]{z.minX(), z.minY(), z.minZ(), z.maxX(), z.maxY(), z.maxZ()});
            zonesList.add(zTag);
        }
        t.put("Zones", zonesList);

        // Reserved LC2 pads
        ListTag padsList = new ListTag();
        for (BlockPos3i p : v.getReservedLc2Pads()) {
            CompoundTag pTag = new CompoundTag();
            pTag.putIntArray("P", new int[]{p.x(), p.y(), p.z()});
            padsList.add(pTag);
        }
        t.put("Lc2Pads", padsList);

        // Construction jobs
        ListTag jobsList = new ListTag();
        for (ConstructionJob job : v.getConstructionQueue()) {
            CompoundTag jTag = new CompoundTag();
            jTag.putString("Id", job.getJobId());
            jTag.putString("Type", job.getProjectType().name());
            jTag.putString("Biome", job.getBiomeStyle().name());
            jTag.putString("Facing", job.getFacing().name());
            jTag.putIntArray("Origin", new int[]{job.getOrigin().x(), job.getOrigin().y(), job.getOrigin().z()});
            Aabb3i bb = job.getBoundingBox();
            jTag.putIntArray("Box", new int[]{bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ()});
            jTag.putInt("Bps", job.getBlocksPerStep());
            jTag.putInt("Tps", job.getTicksPerStep());
            jTag.putInt("Next", job.getNextIndex());
            jTag.putInt("Acc", job.getTickAccumulator());
            jTag.putBoolean("Done", job.isCompleted());

            ListTag pList = new ListTag();
            for (WorldBlockPlacement wp : job.getPlacements()) {
                CompoundTag wpTag = new CompoundTag();
                wpTag.putIntArray("P", new int[]{wp.pos().x(), wp.pos().y(), wp.pos().z()});
                wpTag.putString("S", wp.blockStateSpec());
                pList.add(wpTag);
            }
            jTag.put("Blocks", pList);

            ListTag smList = new ListTag();
            for (SpawnMarker.WorldSpawnMarker sm : job.getSpawnMarkers()) {
                CompoundTag smTag = new CompoundTag();
                smTag.putString("K", sm.kind().name());
                smTag.putIntArray("P", new int[]{sm.pos().x(), sm.pos().y(), sm.pos().z()});
                smTag.putString("H", sm.hint());
                smList.add(smTag);
            }
            jTag.put("Markers", smList);
            jobsList.add(jTag);
        }
        t.put("Jobs", jobsList);

        // Aid construction projects
        ListTag aidList = new ListTag();
        for (AidConstructionProject aid : v.getAidProjects()) {
            CompoundTag aTag = new CompoundTag();
            aTag.putString("Id", aid.projectId());
            aTag.putString("Prof", aid.professionId());
            aTag.putString("Ws", aid.workstationBlockId());
            aTag.putString("Arch", aid.archetype().name());
            aTag.putString("Fallback", aid.displayNameFallback());
            aTag.putString("Size", aid.size().name());
            aTag.putString("Status", aid.status().name());
            aTag.putLong("Paid", aid.paidIc());
            aTag.putDouble("Gathered", aid.gatheredProgressTicks());
            aTag.putInt("ReqTicks", aid.requiredTicks());
            CompoundTag reqMats = new CompoundTag();
            for (Map.Entry<String, Integer> e : aid.requiredMaterials().entrySet()) {
                reqMats.putInt(e.getKey(), e.getValue());
            }
            aTag.put("ReqMats", reqMats);
            if (aid.reservedZone() != null) {
                Aabb3i rz = aid.reservedZone();
                aTag.putIntArray("ResZone", new int[]{rz.minX(), rz.minY(), rz.minZ(), rz.maxX(), rz.maxY(), rz.maxZ()});
            }
            if (!aid.plannedJobId().isEmpty()) {
                aTag.putString("JobId", aid.plannedJobId());
            }
            if (aid.payerPlayerUuid() != null) {
                aTag.putUUID("PayerUuid", aid.payerPlayerUuid());
            }
            if (aid.payerCompanyId() != null) {
                aTag.putString("PayerCo", aid.payerCompanyId());
            }
            aTag.putBoolean("Cash", aid.paidWithCash());
            aidList.add(aTag);
        }
        t.put("AidProjects", aidList);

        // Cached village employment stats
        VillageEmploymentStats es = v.getEmploymentStats();
        CompoundTag esTag = new CompoundTag();
        esTag.putInt("Workforce", es.workforceCount());
        esTag.putInt("Employed", es.employedCount());
        esTag.putInt("Unemployed", es.unemployedCount());
        esTag.putBoolean("MayorEmp", es.mayorCountedEmployed());
        esTag.putLong("LastScan", es.lastScanTick());
        CompoundTag profCounts = new CompoundTag();
        for (Map.Entry<String, Integer> e : es.activeProfessionCounts().entrySet()) {
            profCounts.putInt(e.getKey(), e.getValue());
        }
        esTag.put("Profs", profCounts);
        t.put("EmpStats", esTag);
        return t;
    }

    private static VillageRecord loadVillage(CompoundTag t) {
        UUID id = t.getUUID("Id");
        String dim = t.getString("Dim");
        if (dim.isBlank()) {
            dim = Level.OVERWORLD.location().toString();
        }
        String name = t.getString("Name");
        if (name.isBlank()) {
            name = "Village";
        }
        int[] hp = t.getIntArray("HubPos");
        BlockPos3i hubPos = hp.length >= 3 ? new BlockPos3i(hp[0], hp[1], hp[2]) : new BlockPos3i(0, 64, 0);
        BiomeStyle biome = BiomeStyle.fromNameOrDefault(t.getString("Biome"), BiomeStyle.PLAINS);
        boolean partnered = t.getBoolean("Partnered");
        UUID owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
        String companyId = t.contains("CompanyId", Tag.TAG_STRING) ? t.getString("CompanyId") : null;
        int level = t.getInt("Level");
        int pop = t.getInt("Pop");
        long vol = t.getLong("TradeVol");
        long balance = t.getLong("Balance");
        boolean expPaused = t.getBoolean("ExpPaused");
        int minRad = t.contains("MinRad") ? t.getInt("MinRad") : VillageRecord.MIN_CONFIGURABLE_RADIUS;
        int maxRad = t.contains("MaxRad") ? t.getInt("MaxRad") : 24;
        long lastDemandTick = t.contains("LastDemandTick") ? t.getLong("LastDemandTick") : -1L;
        long lastExpTick = t.getLong("LastExpTick");

        CompoundTag mTag = t.getCompound("Mayor");
        MayorState mayor = new MayorState(
                mTag.hasUUID("Uuid") ? mTag.getUUID("Uuid") : null,
                mTag.getString("Name"),
                mTag.getLong("ElectedTick"),
                mTag.getBoolean("Reelect"),
                mTag.getBoolean("Paused"),
                mTag.getInt("LastPop"),
                mTag.getLong("LastAttempt"),
                mTag.getInt("Elections")
        );

        CooperationHubStorage storage = new CooperationHubStorage();
        ListTag storageList = t.getList("Storage", Tag.TAG_COMPOUND);
        List<CooperationHubStorage.SlotEntry> entries = new ArrayList<>(storageList.size());
        for (int i = 0; i < storageList.size(); i++) {
            CompoundTag sTag = storageList.getCompound(i);
            int slot = sTag.getInt("S");
            String itemId = sTag.getString("I");
            int count = sTag.getInt("C");
            String nbt = sTag.getString("N");
            if (slot >= 0 && slot < CooperationHubStorage.TOTAL_SLOTS && !itemId.isBlank() && count > 0) {
                entries.add(new CooperationHubStorage.SlotEntry(slot, new SlotStack(itemId, count, nbt)));
            }
        }
        storage.importSlots(entries);

        List<VillageTradeOrder> orders = new ArrayList<>();
        ListTag orderList = t.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderList.size(); i++) {
            CompoundTag oTag = orderList.getCompound(i);
            OrderSide side = "VILLAGE_BUYS".equals(oTag.getString("Side"))
                    ? OrderSide.VILLAGE_BUYS
                    : OrderSide.VILLAGE_SELLS;
            orders.add(new VillageTradeOrder(
                    oTag.getString("Id"),
                    side,
                    oTag.getString("Item"),
                    oTag.getString("Hint"),
                    oTag.getString("Ench"),
                    Math.max(1, oTag.getInt("Total")),
                    Math.max(0, oTag.getInt("Done")),
                    Math.max(1L, oTag.getLong("Price")),
                    oTag.getLong("Created"),
                    oTag.getLong("Expires"),
                    oTag.getBoolean("Auto"),
                    oTag.getBoolean("Seasonal"),
                    oTag.getBoolean("BiomeSpecialty")
            ));
        }

        ExpansionDesire expansion = null;
        if (t.contains("Expansion", Tag.TAG_COMPOUND)) {
            CompoundTag eTag = t.getCompound("Expansion");
            ProjectType pType = ProjectType.valueOf(eTag.getString("Type"));
            Map<String, Integer> req = new LinkedHashMap<>();
            CompoundTag reqTag = eTag.getCompound("Req");
            for (String k : reqTag.getAllKeys()) {
                int v = reqTag.getInt(k);
                if (v > 0) {
                    req.put(k, v);
                }
            }
            Map<String, Integer> del = new LinkedHashMap<>();
            CompoundTag delTag = eTag.getCompound("Del");
            for (String k : delTag.getAllKeys()) {
                del.put(k, Math.max(0, delTag.getInt(k)));
            }
            if (!req.isEmpty()) {
                expansion = new ExpansionDesire(
                        eTag.getString("Id"),
                        pType,
                        req,
                        del,
                        Math.max(0L, eTag.getLong("Budget")),
                        eTag.getBoolean("Completed"),
                        eTag.getBoolean("Auto")
                );
            }
        }

        List<Aabb3i> zones = new ArrayList<>();
        ListTag zonesList = t.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zonesList.size(); i++) {
            int[] b = zonesList.getCompound(i).getIntArray("B");
            if (b.length >= 6) {
                zones.add(Aabb3i.of(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
        }

        List<BlockPos3i> pads = new ArrayList<>();
        ListTag padsList = t.getList("Lc2Pads", Tag.TAG_COMPOUND);
        for (int i = 0; i < padsList.size(); i++) {
            int[] p = padsList.getCompound(i).getIntArray("P");
            if (p.length >= 3) {
                pads.add(new BlockPos3i(p[0], p[1], p[2]));
            }
        }

        List<ConstructionJob> jobs = new ArrayList<>();
        ListTag jobsList = t.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobsList.size(); i++) {
            CompoundTag jTag = jobsList.getCompound(i);
            ProjectType pType = ProjectType.valueOf(jTag.getString("Type"));
            BiomeStyle bStyle = BiomeStyle.fromNameOrDefault(jTag.getString("Biome"), biome);
            Facing2D facing = Facing2D.fromNameOrDefault(jTag.getString("Facing"), Facing2D.NORTH);
            int[] orig = jTag.getIntArray("Origin");
            BlockPos3i originPos = orig.length >= 3 ? new BlockPos3i(orig[0], orig[1], orig[2]) : hubPos;
            int[] box = jTag.getIntArray("Box");
            Aabb3i aabb = box.length >= 6
                    ? Aabb3i.of(box[0], box[1], box[2], box[3], box[4], box[5])
                    : Aabb3i.singleBlock(originPos);

            List<WorldBlockPlacement> blocks = new ArrayList<>();
            ListTag bList = jTag.getList("Blocks", Tag.TAG_COMPOUND);
            for (int k = 0; k < bList.size(); k++) {
                CompoundTag wpTag = bList.getCompound(k);
                int[] p = wpTag.getIntArray("P");
                String s = wpTag.getString("S");
                if (p.length >= 3 && !s.isBlank()) {
                    blocks.add(new WorldBlockPlacement(new BlockPos3i(p[0], p[1], p[2]), s));
                }
            }

            List<SpawnMarker.WorldSpawnMarker> markers = new ArrayList<>();
            ListTag smList = jTag.getList("Markers", Tag.TAG_COMPOUND);
            for (int k = 0; k < smList.size(); k++) {
                CompoundTag smTag = smList.getCompound(k);
                int[] p = smTag.getIntArray("P");
                if (p.length >= 3) {
                    markers.add(new SpawnMarker.WorldSpawnMarker(
                            SpawnMarker.MarkerKind.valueOf(smTag.getString("K")),
                            new BlockPos3i(p[0], p[1], p[2]),
                            smTag.getString("H")
                    ));
                }
            }

            jobs.add(new ConstructionJob(
                    jTag.getString("Id"),
                    pType,
                    bStyle,
                    facing,
                    originPos,
                    aabb,
                    blocks,
                    markers,
                    Math.max(1, jTag.getInt("Bps")),
                    Math.max(1, jTag.getInt("Tps")),
                    Math.max(0, jTag.getInt("Next")),
                    Math.max(0, jTag.getInt("Acc")),
                    jTag.getBoolean("Done")
            ));
        }

        // Backwards-compatible AidProjects restore
        List<AidConstructionProject> aidProjects = new ArrayList<>();
        if (t.contains("AidProjects", Tag.TAG_LIST)) {
            ListTag aidList = t.getList("AidProjects", Tag.TAG_COMPOUND);
            for (int i = 0; i < aidList.size(); i++) {
                try {
                    CompoundTag aTag = aidList.getCompound(i);
                    String projId = aTag.getString("Id");
                    String profId = aTag.getString("Prof");
                    String wsId = aTag.getString("Ws");
                    if (projId.isBlank() || profId.isBlank() || wsId.isBlank()) {
                        continue;
                    }
                    WorkplaceArchetype arch = WorkplaceArchetype.fromNameOrDefault(
                            aTag.getString("Arch"), WorkplaceArchetype.GENERIC);
                    String fallback = aTag.getString("Fallback");
                    WorkplaceSize size = WorkplaceSize.fromNameOrDefault(
                            aTag.getString("Size"), WorkplaceSize.SMALL);
                    AidConstructionProject.Status status = AidConstructionProject.Status.COLLECTING;
                    try {
                        String rawStatus = aTag.getString("Status");
                        if (!rawStatus.isBlank()) {
                            status = AidConstructionProject.Status.valueOf(rawStatus);
                        }
                    } catch (IllegalArgumentException ignored) {
                        status = AidConstructionProject.Status.COLLECTING;
                    }
                    long paid = Math.max(0L, aTag.getLong("Paid"));
                    double gathered = Math.max(0.0, aTag.getDouble("Gathered"));
                    int reqTicks = Math.max(1, aTag.contains("ReqTicks")
                            ? aTag.getInt("ReqTicks") : size.defaultGatherTicks());

                    Map<String, Integer> reqMats = new LinkedHashMap<>();
                    if (aTag.contains("ReqMats", Tag.TAG_COMPOUND)) {
                        CompoundTag rmTag = aTag.getCompound("ReqMats");
                        for (String k : rmTag.getAllKeys()) {
                            int qty = rmTag.getInt(k);
                            if (qty > 0) {
                                reqMats.put(k, qty);
                            }
                        }
                    }
                    if (reqMats.isEmpty()) {
                        WorkplaceSpec fallbackSpec = new WorkplaceSpec(
                                profId, wsId, arch, Math.max(1L, paid), size.defaultCostMultiplier(),
                                1, 3, List.of(), true, arch == WorkplaceArchetype.GENERIC, fallback);
                        reqMats = WorkplaceCatalog.requiredMaterials(fallbackSpec, size, biome);
                    }

                    Aabb3i reservedZone = null;
                    if (aTag.contains("ResZone", Tag.TAG_INT_ARRAY)) {
                        int[] rz = aTag.getIntArray("ResZone");
                        if (rz.length >= 6) {
                            reservedZone = Aabb3i.of(rz[0], rz[1], rz[2], rz[3], rz[4], rz[5]);
                        }
                    }
                    String plannedJobId = aTag.getString("JobId");
                    UUID payerUuid = aTag.hasUUID("PayerUuid") ? aTag.getUUID("PayerUuid") : null;
                    String payerCo = aTag.contains("PayerCo", Tag.TAG_STRING) ? aTag.getString("PayerCo") : null;
                    boolean paidWithCash = aTag.getBoolean("Cash");

                    aidProjects.add(new AidConstructionProject(
                            projId, profId, wsId, arch, fallback, size, status, paid,
                            reqMats, gathered, reqTicks, reservedZone, plannedJobId,
                            payerUuid, payerCo, paidWithCash
                    ));
                } catch (Exception e) {
                    LOGGER.warn("Skipping corrupted TyC2 aid project entry #{}: {}", i, e.getMessage());
                }
            }
        }

        // Backwards-compatible EmploymentStats restore
        VillageEmploymentStats empStats = VillageEmploymentStats.EMPTY;
        if (t.contains("EmpStats", Tag.TAG_COMPOUND)) {
            CompoundTag esTag = t.getCompound("EmpStats");
            Map<String, Integer> profCounts = new LinkedHashMap<>();
            if (esTag.contains("Profs", Tag.TAG_COMPOUND)) {
                CompoundTag pTag = esTag.getCompound("Profs");
                for (String k : pTag.getAllKeys()) {
                    int cnt = pTag.getInt(k);
                    if (cnt > 0) {
                        profCounts.put(k, cnt);
                    }
                }
            }
            empStats = new VillageEmploymentStats(
                    esTag.getInt("Workforce"),
                    esTag.getInt("Employed"),
                    esTag.getInt("Unemployed"),
                    esTag.getBoolean("MayorEmp"),
                    profCounts,
                    esTag.getLong("LastScan")
            );
        }

        VillageRecord rec = new VillageRecord(
                id, dim, name, hubPos, biome, partnered, owner, companyId,
                level, pop, vol, balance, mayor, storage, orders, expansion,
                zones, pads, expPaused, minRad, maxRad, lastDemandTick, lastExpTick, jobs,
                aidProjects, empStats
        );
        if (t.contains("OrdersInitialized")) {
            rec.setInitialOrdersGenerated(t.getBoolean("OrdersInitialized"));
        }
        if (t.contains("LastStipendTick")) {
            rec.setLastStipendTick(t.getLong("LastStipendTick"));
        }
        if (t.contains("InitWcGranted")) {
            rec.setInitialWorkingCapitalGranted(t.getBoolean("InitWcGranted"));
        }
        if (t.contains("LastTermTick")) {
            rec.setLastTerminationTick(t.getLong("LastTermTick"));
        }
        if (t.contains("HubDeployed")) {
            rec.setHubDeployed(t.getBoolean("HubDeployed"));
        }
        if (t.contains("AidCompleted", Tag.TAG_COMPOUND)) {
            CompoundTag aidDoneTag = t.getCompound("AidCompleted");
            Map<String, Integer> completedAid = new LinkedHashMap<>();
            for (String k : aidDoneTag.getAllKeys()) {
                int cnt = aidDoneTag.getInt(k);
                if (cnt > 0) {
                    completedAid.put(k, cnt);
                }
            }
            rec.setCompletedAidCounts(completedAid);
        }
        return rec;
    }

    /** Safely stores arbitrarily long JSON strings in NBT without hitting the 65KB writeUTF limit. */
    private static void putLargeString(CompoundTag tag, String key, String value) {
        ListTag chunks = new ListTag();
        if (value != null && !value.isEmpty()) {
            for (int i = 0; i < value.length(); i += LARGE_STRING_CHUNK) {
                int end = Math.min(value.length(), i + LARGE_STRING_CHUNK);
                chunks.add(StringTag.valueOf(value.substring(i, end)));
            }
        }
        tag.put(key, chunks);
    }

    private static String getLargeString(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_STRING)) {
            return tag.getString(key);
        }
        if (tag.contains(key, Tag.TAG_LIST)) {
            ListTag chunks = tag.getList(key, Tag.TAG_STRING);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                sb.append(chunks.getString(i));
            }
            return sb.toString();
        }
        return "";
    }
}
