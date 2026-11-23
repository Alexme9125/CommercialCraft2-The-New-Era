package com.tycooncraft.tyc2.forge.network;

import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.client.TyC2ClientHandlers;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.ConstructionJob;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionDesire;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.VillageLevelTable;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.VillageTradeOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Full village/warehouse/economy/expansion snapshot synced to
 * {@link com.tycooncraft.tyc2.forge.client.CooperationHubScreen} and reused inside
 * {@link S2CTerminalSyncPacket}.
 */
public final class S2CHubSyncPacket {

    public static final int WAREHOUSE_PAGE_SIZE = 36; // 3 rows x 12 cols cleanly fits 232x86 viewport

    public record OrderDto(
            String orderId,
            boolean isVillageBuy,
            String itemId,
            String enchantSpec,
            int totalQty,
            int fulfilledQty,
            long unitPrice,
            boolean autoFulfill,
            boolean seasonalBonus
    ) {
        public int remainingQty() {
            return Math.max(0, totalQty - fulfilledQty);
        }
    }

    public record SlotDto(int absSlot, String itemId, int count, String nbtJson) {
    }

    public record MatDto(String itemId, int delivered, int required) {
    }

    public final UUID villageId;
    public final String villageName;
    public final String biomeStyleId;
    public final int level;
    public final int population;
    public final long cumulativeVolume;
    public final long villageBalance;
    public final String mayorName;
    public final boolean depopulatedPaused;
    public final boolean expansionPaused;
    public final boolean canManage;
    public final boolean chunkLoaderActive;
    public final int refreshSecondsRemaining;
    public final long activeBankBalance;

    public final List<OrderDto> orders;

    public final boolean outboundZone;
    public final int pageIndex;
    public final int totalPages;
    public final int inboundUsed;
    public final int outboundUsed;
    public final List<SlotDto> warehouseSlots;

    public final double cpi;
    public final String inflationState;
    public final long moneySupply;
    public final double goodsCirculation;
    public final String seasonKey;
    public final int playerTierLevel;
    public final double[] cpiHistory;

    public final boolean canUpgrade;
    public final int nextMinPop;
    public final long nextMinVol;
    public final long nextMinBal;
    public final long nextSinkCost;

    public final boolean hasExpansion;
    public final String expProjectKey;
    public final long expBudget;
    public final boolean expAutoFulfill;
    public final List<MatDto> expMaterials;

    public final boolean hasJob;
    public final String jobProjectKey;
    public final int jobPlaced;
    public final int jobTotal;
    public final List<String> nearbyLc2Stations;
    public final Component feedbackComponent;
    public final String feedback;

    public S2CHubSyncPacket(
            UUID villageId,
            String villageName,
            String biomeStyleId,
            int level,
            int population,
            long cumulativeVolume,
            long villageBalance,
            String mayorName,
            boolean depopulatedPaused,
            boolean expansionPaused,
            boolean canManage,
            boolean chunkLoaderActive,
            int refreshSecondsRemaining,
            long activeBankBalance,
            List<OrderDto> orders,
            boolean outboundZone,
            int pageIndex,
            int totalPages,
            int inboundUsed,
            int outboundUsed,
            List<SlotDto> warehouseSlots,
            double cpi,
            String inflationState,
            long moneySupply,
            double goodsCirculation,
            String seasonKey,
            int playerTierLevel,
            double[] cpiHistory,
            boolean canUpgrade,
            int nextMinPop,
            long nextMinVol,
            long nextMinBal,
            long nextSinkCost,
            boolean hasExpansion,
            String expProjectKey,
            long expBudget,
            boolean expAutoFulfill,
            List<MatDto> expMaterials,
            boolean hasJob,
            String jobProjectKey,
            int jobPlaced,
            int jobTotal,
            List<String> nearbyLc2Stations,
            Component feedbackComponent
    ) {
        this.villageId = villageId;
        this.villageName = villageName;
        this.biomeStyleId = biomeStyleId;
        this.level = level;
        this.population = population;
        this.cumulativeVolume = cumulativeVolume;
        this.villageBalance = villageBalance;
        this.mayorName = mayorName;
        this.depopulatedPaused = depopulatedPaused;
        this.expansionPaused = expansionPaused;
        this.canManage = canManage;
        this.chunkLoaderActive = chunkLoaderActive;
        this.refreshSecondsRemaining = refreshSecondsRemaining;
        this.activeBankBalance = activeBankBalance;
        this.orders = orders;
        this.outboundZone = outboundZone;
        this.pageIndex = pageIndex;
        this.totalPages = totalPages;
        this.inboundUsed = inboundUsed;
        this.outboundUsed = outboundUsed;
        this.warehouseSlots = warehouseSlots;
        this.cpi = cpi;
        this.inflationState = inflationState;
        this.moneySupply = moneySupply;
        this.goodsCirculation = goodsCirculation;
        this.seasonKey = seasonKey;
        this.playerTierLevel = playerTierLevel;
        this.cpiHistory = cpiHistory;
        this.canUpgrade = canUpgrade;
        this.nextMinPop = nextMinPop;
        this.nextMinVol = nextMinVol;
        this.nextMinBal = nextMinBal;
        this.nextSinkCost = nextSinkCost;
        this.hasExpansion = hasExpansion;
        this.expProjectKey = expProjectKey;
        this.expBudget = expBudget;
        this.expAutoFulfill = expAutoFulfill;
        this.expMaterials = expMaterials;
        this.hasJob = hasJob;
        this.jobProjectKey = jobProjectKey;
        this.jobPlaced = jobPlaced;
        this.jobTotal = jobTotal;
        this.nearbyLc2Stations = nearbyLc2Stations;
        this.feedbackComponent = feedbackComponent == null ? Component.empty() : feedbackComponent;
        this.feedback = this.feedbackComponent.getString();
    }

    /** Builds a full village snapshot for Hub or Comm Terminal screens with translatable feedback. */
    public static S2CHubSyncPacket buildFor(
            ServerPlayer player,
            VillageRecord village,
            @Nullable CooperationHubBlockEntity hubBe,
            CooperationHubStorage.Zone zone,
            int pageIndex,
            String searchQuery,
            Component feedback
    ) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(player.getServer());
        data.syncMacroAccountTotals();
        MacroEconomyEngine econ = data.economy();
        ServerLevel dimLevel = TycoonForgeWorldManager.resolveLevel(player.getServer(), village.getDimensionId());
        if (dimLevel == null) {
            dimLevel = player.getServer().overworld();
        }
        Season season = SeasonSoftBridge.currentSeason(dimLevel);
        long now = player.getServer().overworld().getGameTime();
        long refreshTicks = TyC2RuntimeData.config().demandRefreshTicks();
        long elapsed = Math.max(0L, now - village.getLastDemandRefreshTick());
        int refreshSecs = (int) Math.max(0L, (refreshTicks - elapsed) / 20L);

        List<OrderDto> orderDtos = new ArrayList<>();
        for (VillageTradeOrder o : village.getActiveOrders()) {
            orderDtos.add(new OrderDto(
                    o.orderId(),
                    o.side() == OrderSide.VILLAGE_BUYS,
                    o.itemId(),
                    o.enchantSpec(),
                    o.totalQty(),
                    o.fulfilledQty(),
                    o.unitPrice(),
                    o.autoFulfill(),
                    o.seasonalBonus()
            ));
        }

        CooperationHubStorage storage = village.getStorage();
        CooperationHubStorage.PageResult page = storage.getPage(zone, pageIndex, WAREHOUSE_PAGE_SIZE, searchQuery);
        List<SlotDto> slotDtos = new ArrayList<>(page.slotIndices().size());
        for (int absSlot : page.slotIndices()) {
            var opt = storage.getSlot(absSlot);
            if (opt.isPresent()) {
                slotDtos.add(new SlotDto(absSlot, opt.get().itemId(), opt.get().count(), opt.get().nbtJson()));
            } else {
                slotDtos.add(new SlotDto(absSlot, "", 0, ""));
            }
        }

        List<MacroEconomyEngine.PriceSnapshot> hist = econ.history();
        double[] histArr = new double[Math.max(1, hist.size())];
        if (hist.isEmpty()) {
            histArr[0] = econ.computeCpi();
        } else {
            for (int i = 0; i < hist.size(); i++) {
                histArr[i] = hist.get(i).cpiIndex();
            }
        }

        VillageLevelTable.UpgradeStatus upg = village.evaluateUpgrade(TyC2RuntimeData.levelTable());
        int nPop = upg.targetSpec() == null ? 0 : upg.targetSpec().minPopulation();
        long nVol = upg.targetSpec() == null ? 0L : upg.targetSpec().minTradeVolume();
        long nBal = upg.targetSpec() == null ? 0L : upg.targetSpec().minVillageBalance();
        long nSink = upg.targetSpec() == null ? 0L : upg.targetSpec().upgradeCostSink();

        ExpansionDesire exp = village.getActiveExpansion();
        List<MatDto> matDtos = new ArrayList<>();
        if (exp != null) {
            for (Map.Entry<String, Integer> e : exp.requiredMaterials().entrySet()) {
                matDtos.add(new MatDto(
                        e.getKey(),
                        exp.deliveredMaterials().getOrDefault(e.getKey(), 0),
                        e.getValue()
                ));
            }
        }

        Optional<ConstructionJob> jobOpt = village.getActiveConstructionJob();
        List<String> lc2Stations = Lc2SoftBridge.scanNearbyStations(dimLevel, TyC2Ids.toMc(village.getHubPos()), 48);

        data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
        long playerBankBal = data.bank().getActiveBalance(player.getUUID());
        int ownerTier = data.resolveVillageOwnerTier(village).level();

        return new S2CHubSyncPacket(
                village.getVillageId(),
                village.getVillageName(),
                village.getBiomeStyle().id(),
                village.getLevel(),
                village.getPopulation(),
                village.getCumulativeTradeVolume(),
                village.getVillageBalance(),
                village.getMayorState().getMayorName(),
                village.getMayorState().isPausedDueToDepopulation(),
                village.isExpansionPaused(),
                TycoonForgeWorldManager.canManageVillage(player, data, village),
                hubBe != null && hubBe.isChunkLoaderActive(),
                refreshSecs,
                playerBankBal,
                orderDtos,
                zone == CooperationHubStorage.Zone.OUTBOUND,
                page.pageIndex(),
                page.totalPages(),
                storage.getUsedSlotCount(CooperationHubStorage.Zone.INBOUND),
                storage.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND),
                slotDtos,
                econ.computeCpi(),
                econ.getInflationState().name(),
                econ.totalMoneySupply(),
                econ.rollingGoodsCirculation(),
                season.key(),
                ownerTier,
                histArr,
                upg.eligible(),
                nPop,
                nVol,
                nBal,
                nSink,
                exp != null,
                exp == null ? "" : exp.projectType().name().toLowerCase(),
                exp == null ? 0L : exp.constructionBudgetCost(),
                exp != null && exp.autoFulfill(),
                matDtos,
                jobOpt.isPresent(),
                jobOpt.map(j -> j.getProjectType().name().toLowerCase()).orElse(""),
                jobOpt.map(ConstructionJob::getPlacedCount).orElse(0),
                jobOpt.map(ConstructionJob::getTotalBlocks).orElse(0),
                lc2Stations,
                feedback
        );
    }

    public static void encode(S2CHubSyncPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.villageId);
        buf.writeUtf(p.villageName, 64);
        buf.writeUtf(p.biomeStyleId, 32);
        buf.writeVarInt(p.level);
        buf.writeVarInt(p.population);
        buf.writeVarLong(p.cumulativeVolume);
        buf.writeVarLong(p.villageBalance);
        buf.writeUtf(p.mayorName, 64);
        buf.writeBoolean(p.depopulatedPaused);
        buf.writeBoolean(p.expansionPaused);
        buf.writeBoolean(p.canManage);
        buf.writeBoolean(p.chunkLoaderActive);
        buf.writeVarInt(p.refreshSecondsRemaining);
        buf.writeVarLong(p.activeBankBalance);

        buf.writeVarInt(p.orders.size());
        for (OrderDto o : p.orders) {
            buf.writeUtf(o.orderId(), 64);
            buf.writeBoolean(o.isVillageBuy());
            buf.writeUtf(o.itemId(), 96);
            buf.writeUtf(o.enchantSpec(), 64);
            buf.writeVarInt(o.totalQty());
            buf.writeVarInt(o.fulfilledQty());
            buf.writeVarLong(o.unitPrice());
            buf.writeBoolean(o.autoFulfill());
            buf.writeBoolean(o.seasonalBonus());
        }

        buf.writeBoolean(p.outboundZone);
        buf.writeVarInt(p.pageIndex);
        buf.writeVarInt(p.totalPages);
        buf.writeVarInt(p.inboundUsed);
        buf.writeVarInt(p.outboundUsed);
        buf.writeVarInt(p.warehouseSlots.size());
        for (SlotDto s : p.warehouseSlots) {
            buf.writeVarInt(s.absSlot());
            buf.writeUtf(s.itemId(), 96);
            buf.writeVarInt(s.count());
            buf.writeUtf(s.nbtJson(), 256);
        }

        buf.writeDouble(p.cpi);
        buf.writeUtf(p.inflationState, 32);
        buf.writeVarLong(p.moneySupply);
        buf.writeDouble(p.goodsCirculation);
        buf.writeUtf(p.seasonKey, 16);
        buf.writeVarInt(p.playerTierLevel);
        buf.writeVarInt(p.cpiHistory.length);
        for (double v : p.cpiHistory) {
            buf.writeDouble(v);
        }

        buf.writeBoolean(p.canUpgrade);
        buf.writeVarInt(p.nextMinPop);
        buf.writeVarLong(p.nextMinVol);
        buf.writeVarLong(p.nextMinBal);
        buf.writeVarLong(p.nextSinkCost);

        buf.writeBoolean(p.hasExpansion);
        buf.writeUtf(p.expProjectKey, 48);
        buf.writeVarLong(p.expBudget);
        buf.writeBoolean(p.expAutoFulfill);
        buf.writeVarInt(p.expMaterials.size());
        for (MatDto m : p.expMaterials) {
            buf.writeUtf(m.itemId(), 96);
            buf.writeVarInt(m.delivered());
            buf.writeVarInt(m.required());
        }

        buf.writeBoolean(p.hasJob);
        buf.writeUtf(p.jobProjectKey, 48);
        buf.writeVarInt(p.jobPlaced);
        buf.writeVarInt(p.jobTotal);
        buf.writeVarInt(p.nearbyLc2Stations.size());
        for (String st : p.nearbyLc2Stations) {
            buf.writeUtf(st, 64);
        }
        buf.writeComponent(p.feedbackComponent);
    }

    public static S2CHubSyncPacket decode(FriendlyByteBuf buf) {
        UUID vid = buf.readUUID();
        String name = buf.readUtf(64);
        String biome = buf.readUtf(32);
        int lv = buf.readVarInt();
        int pop = buf.readVarInt();
        long vol = buf.readVarLong();
        long bal = buf.readVarLong();
        String mayor = buf.readUtf(64);
        boolean depop = buf.readBoolean();
        boolean expPaused = buf.readBoolean();
        boolean manage = buf.readBoolean();
        boolean chunk = buf.readBoolean();
        int refSecs = buf.readVarInt();
        long bankBal = buf.readVarLong();

        int oCount = Math.min(64, buf.readVarInt());
        List<OrderDto> orders = new ArrayList<>(oCount);
        for (int i = 0; i < oCount; i++) {
            orders.add(new OrderDto(
                    buf.readUtf(64),
                    buf.readBoolean(),
                    buf.readUtf(96),
                    buf.readUtf(64),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readBoolean(),
                    buf.readBoolean()
            ));
        }

        boolean outZone = buf.readBoolean();
        int pIdx = buf.readVarInt();
        int totPages = buf.readVarInt();
        int inUsed = buf.readVarInt();
        int outUsed = buf.readVarInt();
        int sCount = Math.min(64, buf.readVarInt());
        List<SlotDto> slots = new ArrayList<>(sCount);
        for (int i = 0; i < sCount; i++) {
            slots.add(new SlotDto(buf.readVarInt(), buf.readUtf(96), buf.readVarInt(), buf.readUtf(256)));
        }

        double cpi = buf.readDouble();
        String inf = buf.readUtf(32);
        long ms = buf.readVarLong();
        double gc = buf.readDouble();
        String season = buf.readUtf(16);
        int tier = buf.readVarInt();
        int hLen = Math.min(64, buf.readVarInt());
        double[] hist = new double[hLen];
        for (int i = 0; i < hLen; i++) {
            hist[i] = buf.readDouble();
        }

        boolean canUpg = buf.readBoolean();
        int nPop = buf.readVarInt();
        long nVol = buf.readVarLong();
        long nBal = buf.readVarLong();
        long nSink = buf.readVarLong();

        boolean hasExp = buf.readBoolean();
        String expProj = buf.readUtf(48);
        long expBud = buf.readVarLong();
        boolean expAuto = buf.readBoolean();
        int mCount = Math.min(32, buf.readVarInt());
        List<MatDto> mats = new ArrayList<>(mCount);
        for (int i = 0; i < mCount; i++) {
            mats.add(new MatDto(buf.readUtf(96), buf.readVarInt(), buf.readVarInt()));
        }

        boolean hasJob = buf.readBoolean();
        String jobProj = buf.readUtf(48);
        int jobPlaced = buf.readVarInt();
        int jobTotal = buf.readVarInt();
        int lc2Count = Math.min(16, buf.readVarInt());
        List<String> lc2 = new ArrayList<>(lc2Count);
        for (int i = 0; i < lc2Count; i++) {
            lc2.add(buf.readUtf(64));
        }
        Component fb = buf.readComponent();

        return new S2CHubSyncPacket(
                vid, name, biome, lv, pop, vol, bal, mayor, depop, expPaused, manage, chunk,
                refSecs, bankBal, orders, outZone, pIdx, totPages, inUsed, outUsed, slots,
                cpi, inf, ms, gc, season, tier, hist, canUpg, nPop, nVol, nBal, nSink,
                hasExp, expProj, expBud, expAuto, mats, hasJob, jobProj, jobPlaced, jobTotal, lc2, fb
        );
    }

    public static void handle(S2CHubSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TyC2ClientHandlers.handleHubSync(pkt));
        ctx.setPacketHandled(true);
    }
}
