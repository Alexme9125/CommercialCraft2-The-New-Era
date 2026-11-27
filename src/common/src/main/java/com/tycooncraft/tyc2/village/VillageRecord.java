package com.tycooncraft.tyc2.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Complete per-village persistent state record managed inside {@code TycoonSavedData}.
 * Holds identity, partnership ownership, level/population/finances, Mayor lifecycle,
 * 1,000-slot {@link CooperationHubStorage}, active trade orders, active expansion desire,
 * occupied bounding boxes, reserved LC2 pads, expansion settings, and queued {@link ConstructionJob}s.
 */
public final class VillageRecord {

    /** Minimum configurable inner expansion radius (blocks from Cooperation Hub). */
    public static final int MIN_CONFIGURABLE_RADIUS = 10;

    /** Maximum configurable outer expansion radius (blocks from Cooperation Hub). */
    public static final int MAX_CONFIGURABLE_RADIUS = 72;

    /** Municipal excess-reserve upkeep burn rate per day (2% from Design Section 7.2). */
    public static final double DAILY_EXCESS_UPKEEP_RATE = 0.02;

    /** Default weekly municipal stipend threshold ratio relative to target reserve (45%). */
    public static final double DEFAULT_WEEKLY_STIPEND_RATIO = 0.45;

    /**
     * Structured outcome code for village trade order execution.
     */
    public enum TradeFailureCode {
        /** Transaction succeeded with no error. */
        NONE,
        /** Requested quantity was less than 1. */
        INVALID_QTY,
        /** Order ID was not found among active village orders. */
        ORDER_NOT_FOUND,
        /** Order side did not match the requested buy or sell operation. */
        WRONG_SIDE,
        /** Order was already completely fulfilled. */
        ALREADY_COMPLETED,
        /** No matching items were present in the Cooperation Hub Inbound zone ({@code 0..499}). */
        INSUFFICIENT_INBOUND_ITEMS,
        /** Village treasury balance was below a single item's {@code unitPrice}. */
        INSUFFICIENT_VILLAGE_FUNDS,
        /** Cooperation Hub Outbound zone ({@code 500..999}) lacked space for purchased items. */
        OUTBOUND_FULL
    }

    /**
     * Outcome of executing a trade order against this village's {@link CooperationHubStorage}
     * and {@link #getVillageBalance()}.
     *
     * @param success whether the transaction executed
     * @param executedQty quantity actually traded
     * @param totalCurrency total IC value exchanged
     * @param updatedOrder updated order snapshot (null if order not found)
     * @param failureReason diagnostic message when {@code success == false}
     * @param failureCode structured outcome code ({@link TradeFailureCode#NONE} when {@code success == true})
     */
    public record TradeExecutionResult(
            boolean success,
            int executedQty,
            long totalCurrency,
            VillageTradeOrder updatedOrder,
            String failureReason,
            TradeFailureCode failureCode) {

        /**
         * Normalizes optional reason and failure code.
         */
        public TradeExecutionResult {
            failureReason = failureReason == null ? "" : failureReason;
            failureCode = failureCode == null ? inferFailureCode(success, failureReason) : failureCode;
        }

        /**
         * Backwards-compatible 5-argument constructor deriving {@link TradeFailureCode} from status and reason.
         *
         * @param success whether the transaction executed
         * @param executedQty quantity actually traded
         * @param totalCurrency total IC value exchanged
         * @param updatedOrder updated order snapshot (null if order not found)
         * @param failureReason diagnostic message when {@code success == false}
         */
        public TradeExecutionResult(
                boolean success,
                int executedQty,
                long totalCurrency,
                VillageTradeOrder updatedOrder,
                String failureReason) {
            this(success, executedQty, totalCurrency, updatedOrder, failureReason,
                    inferFailureCode(success, failureReason));
        }

        private static TradeFailureCode inferFailureCode(boolean success, String reason) {
            if (success) {
                return TradeFailureCode.NONE;
            }
            if (reason == null || reason.isBlank()) {
                return TradeFailureCode.INVALID_QTY;
            }
            String lower = reason.toLowerCase(Locale.ROOT);
            if (lower.contains("not found")) {
                return TradeFailureCode.ORDER_NOT_FOUND;
            }
            if (lower.contains("not a village")) {
                return TradeFailureCode.WRONG_SIDE;
            }
            if (lower.contains("completed")) {
                return TradeFailureCode.ALREADY_COMPLETED;
            }
            if (lower.contains("inbound")) {
                return TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS;
            }
            if (lower.contains("balance") || lower.contains("fund")) {
                return TradeFailureCode.INSUFFICIENT_VILLAGE_FUNDS;
            }
            if (lower.contains("outbound")) {
                return TradeFailureCode.OUTBOUND_FULL;
            }
            return TradeFailureCode.INVALID_QTY;
        }
    }

    private final UUID villageId;
    private String dimensionId;
    private String villageName;
    private BlockPos3i hubPos;
    private BiomeStyle biomeStyle;
    private boolean partnered;
    private UUID ownerPlayerUuid;
    private String companyAccountId;
    private int level;
    private int population;
    private long cumulativeTradeVolume;
    private long villageBalance;
    private final MayorState mayorState;
    private final CooperationHubStorage storage;
    private final List<VillageTradeOrder> activeOrders;
    private ExpansionDesire activeExpansion;
    private final List<Aabb3i> occupiedZones;
    private final List<BlockPos3i> reservedLc2Pads;
    private boolean expansionPaused;
    private int minExpansionRadius;
    private int maxExpansionRadius;
    private long lastDemandRefreshTick;
    private boolean initialOrdersGenerated;
    private long lastStipendTick;
    private boolean initialWorkingCapitalGranted;
    private long lastTerminationTick;
    private boolean hubDeployed;
    private long lastExpansionTick;
    private final List<ConstructionJob> constructionQueue;
    private final List<AidConstructionProject> aidProjects;
    private final Map<String, Integer> completedAidCounts;
    private VillageEmploymentStats employmentStats;
    private boolean chunkLoaderInstalled;
    private long nextAutoExpansionRetryTick;

    /**
     * Creates a new unpartnered Level-1 {@link VillageRecord} centered at {@code hubPos}.
     *
     * @param villageId unique village UUID
     * @param dimensionId dimension registry ID (e.g. {@code "minecraft:overworld"})
     * @param villageName human-readable village name
     * @param hubPos village Bell or Cooperation Hub block position
     * @param biomeStyle architectural biome style
     */
    public VillageRecord(
            UUID villageId,
            String dimensionId,
            String villageName,
            BlockPos3i hubPos,
            BiomeStyle biomeStyle) {
        this.villageId = Objects.requireNonNull(villageId, "villageId");
        this.dimensionId = requireNonBlank(dimensionId, "dimensionId");
        this.villageName = requireNonBlank(villageName, "villageName");
        this.hubPos = Objects.requireNonNull(hubPos, "hubPos");
        this.biomeStyle = Objects.requireNonNull(biomeStyle, "biomeStyle");
        this.partnered = false;
        this.ownerPlayerUuid = null;
        this.companyAccountId = null;
        this.level = VillageLevelTable.MIN_LEVEL;
        this.population = 0;
        this.cumulativeTradeVolume = 0L;
        this.villageBalance = 0L;
        this.mayorState = new MayorState();
        this.storage = new CooperationHubStorage();
        this.activeOrders = new ArrayList<>();
        this.activeExpansion = null;
        this.occupiedZones = new ArrayList<>();
        this.reservedLc2Pads = new ArrayList<>();
        this.expansionPaused = false;
        this.minExpansionRadius = MIN_CONFIGURABLE_RADIUS;
        this.maxExpansionRadius = VillageLevelTable.get(this.level).expansionRadiusMax();
        this.lastDemandRefreshTick = -1L;
        this.initialOrdersGenerated = false;
        this.lastStipendTick = -1L;
        this.initialWorkingCapitalGranted = false;
        this.lastTerminationTick = -1L;
        this.hubDeployed = false;
        this.lastExpansionTick = 0L;
        this.constructionQueue = new ArrayList<>();
        this.aidProjects = new ArrayList<>();
        this.completedAidCounts = new LinkedHashMap<>();
        this.employmentStats = VillageEmploymentStats.EMPTY;
    }

    /**
     * Restores a {@link VillageRecord} with full persisted state.
     *
     * @param villageId unique village UUID
     * @param dimensionId dimension registry ID
     * @param villageName village display name
     * @param hubPos Hub block position
     * @param biomeStyle architectural biome style
     * @param partnered whether partnership is active
     * @param ownerPlayerUuid owning player UUID (nullable)
     * @param companyAccountId linked company/guild account ID (nullable)
     * @param level village level ({@code 1..10})
     * @param population last tracked living villager count
     * @param cumulativeTradeVolume total IC traded historically
     * @param villageBalance current municipal treasury balance in IC
     * @param mayorState Mayor lifecycle state
     * @param storage 1,000-slot Cooperation Hub storage
     * @param activeOrders current active trade orders
     * @param activeExpansion current expansion desire (nullable)
     * @param occupiedZones protected bounding boxes of existing and built structures
     * @param reservedLc2Pads reserved LC2 logistics pad coordinates
     * @param expansionPaused whether automatic expansion is paused
     * @param minExpansionRadius inner expansion search radius
     * @param maxExpansionRadius outer expansion search radius
     * @param lastDemandRefreshTick tick of last demand refresh
     * @param lastExpansionTick tick of last expansion launch
     * @param constructionQueue queued gradual construction jobs
     */
    public VillageRecord(
            UUID villageId,
            String dimensionId,
            String villageName,
            BlockPos3i hubPos,
            BiomeStyle biomeStyle,
            boolean partnered,
            UUID ownerPlayerUuid,
            String companyAccountId,
            int level,
            int population,
            long cumulativeTradeVolume,
            long villageBalance,
            MayorState mayorState,
            CooperationHubStorage storage,
            List<VillageTradeOrder> activeOrders,
            ExpansionDesire activeExpansion,
            List<Aabb3i> occupiedZones,
            List<BlockPos3i> reservedLc2Pads,
            boolean expansionPaused,
            int minExpansionRadius,
            int maxExpansionRadius,
            long lastDemandRefreshTick,
            long lastExpansionTick,
            List<ConstructionJob> constructionQueue) {
        this(
                villageId,
                dimensionId,
                villageName,
                hubPos,
                biomeStyle,
                partnered,
                ownerPlayerUuid,
                companyAccountId,
                level,
                population,
                cumulativeTradeVolume,
                villageBalance,
                mayorState,
                storage,
                activeOrders,
                activeExpansion,
                occupiedZones,
                reservedLc2Pads,
                expansionPaused,
                minExpansionRadius,
                maxExpansionRadius,
                lastDemandRefreshTick,
                lastExpansionTick,
                constructionQueue,
                List.of(),
                VillageEmploymentStats.EMPTY);
    }

    /**
     * Restores a {@link VillageRecord} with full persisted state including aid construction projects
     * and cached {@link VillageEmploymentStats}.
     *
     * @param villageId unique village UUID
     * @param dimensionId dimension registry ID
     * @param villageName village display name
     * @param hubPos Hub block position
     * @param biomeStyle architectural biome style
     * @param partnered whether partnership is active
     * @param ownerPlayerUuid owning player UUID (nullable)
     * @param companyAccountId linked company/guild account ID (nullable)
     * @param level village level ({@code 1..10})
     * @param population last tracked living villager count
     * @param cumulativeTradeVolume total IC traded historically
     * @param villageBalance current municipal treasury balance in IC
     * @param mayorState Mayor lifecycle state
     * @param storage 1,000-slot Cooperation Hub storage
     * @param activeOrders current active trade orders
     * @param activeExpansion current expansion desire (nullable)
     * @param occupiedZones protected bounding boxes of existing and built structures
     * @param reservedLc2Pads reserved LC2 logistics pad coordinates
     * @param expansionPaused whether automatic expansion is paused
     * @param minExpansionRadius inner expansion search radius
     * @param maxExpansionRadius outer expansion search radius
     * @param lastDemandRefreshTick tick of last demand refresh
     * @param lastExpansionTick tick of last expansion launch
     * @param constructionQueue queued gradual construction jobs
     * @param aidProjects active and queued aid construction projects (nullable)
     * @param employmentStats cached village employment statistics (nullable)
     */
    public VillageRecord(
            UUID villageId,
            String dimensionId,
            String villageName,
            BlockPos3i hubPos,
            BiomeStyle biomeStyle,
            boolean partnered,
            UUID ownerPlayerUuid,
            String companyAccountId,
            int level,
            int population,
            long cumulativeTradeVolume,
            long villageBalance,
            MayorState mayorState,
            CooperationHubStorage storage,
            List<VillageTradeOrder> activeOrders,
            ExpansionDesire activeExpansion,
            List<Aabb3i> occupiedZones,
            List<BlockPos3i> reservedLc2Pads,
            boolean expansionPaused,
            int minExpansionRadius,
            int maxExpansionRadius,
            long lastDemandRefreshTick,
            long lastExpansionTick,
            List<ConstructionJob> constructionQueue,
            List<AidConstructionProject> aidProjects,
            VillageEmploymentStats employmentStats) {
        this.villageId = Objects.requireNonNull(villageId, "villageId");
        this.dimensionId = requireNonBlank(dimensionId, "dimensionId");
        this.villageName = requireNonBlank(villageName, "villageName");
        this.hubPos = Objects.requireNonNull(hubPos, "hubPos");
        this.biomeStyle = Objects.requireNonNull(biomeStyle, "biomeStyle");
        this.partnered = partnered;
        this.ownerPlayerUuid = ownerPlayerUuid;
        this.companyAccountId = (companyAccountId == null || companyAccountId.isBlank()) ? null : companyAccountId;
        this.level = Math.max(VillageLevelTable.MIN_LEVEL, Math.min(VillageLevelTable.MAX_LEVEL, level));
        this.population = Math.max(0, population);
        this.cumulativeTradeVolume = Math.max(0L, cumulativeTradeVolume);
        this.villageBalance = Math.max(0L, villageBalance);
        this.mayorState = Objects.requireNonNull(mayorState, "mayorState");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.activeOrders = new ArrayList<>(Objects.requireNonNull(activeOrders, "activeOrders"));
        this.activeExpansion = activeExpansion;
        this.occupiedZones = new ArrayList<>(Objects.requireNonNull(occupiedZones, "occupiedZones"));
        this.reservedLc2Pads = new ArrayList<>(Objects.requireNonNull(reservedLc2Pads, "reservedLc2Pads"));
        this.expansionPaused = expansionPaused;
        this.minExpansionRadius = clampRadius(minExpansionRadius);
        this.maxExpansionRadius = Math.max(this.minExpansionRadius, clampRadius(maxExpansionRadius));
        this.lastDemandRefreshTick = lastDemandRefreshTick < 0L ? -1L : lastDemandRefreshTick;
        this.initialOrdersGenerated = !this.activeOrders.isEmpty() || this.lastDemandRefreshTick > 0L;
        this.lastStipendTick = (partnered && this.lastDemandRefreshTick >= 0L) ? this.lastDemandRefreshTick : -1L;
        this.initialWorkingCapitalGranted = partnered || this.cumulativeTradeVolume > 0L || this.villageBalance > 0L;
        this.lastTerminationTick = -1L;
        this.hubDeployed = partnered;
        this.lastExpansionTick = Math.max(0L, lastExpansionTick);
        this.constructionQueue = new ArrayList<>(Objects.requireNonNull(constructionQueue, "constructionQueue"));
        this.aidProjects = aidProjects == null ? new ArrayList<>() : new ArrayList<>(aidProjects);
        this.completedAidCounts = new LinkedHashMap<>();
        this.employmentStats = employmentStats == null ? VillageEmploymentStats.EMPTY : employmentStats;
    }

    /**
     * Establishes a formal cooperation partnership with a player (and optional guild/company account),
     * crediting the initial seed capital into {@link #getVillageBalance()}.
     *
     * @param ownerPlayerUuid player UUID who negotiated the partnership
     * @param companyAccountId optional guild/company account ID (nullable)
     * @param initialCapital seed IC credited to village treasury ({@code >= 0})
     */
    public void establishPartnership(UUID ownerPlayerUuid, String companyAccountId, long initialCapital) {
        this.partnered = true;
        this.ownerPlayerUuid = Objects.requireNonNull(ownerPlayerUuid, "ownerPlayerUuid");
        this.companyAccountId = (companyAccountId == null || companyAccountId.isBlank()) ? null : companyAccountId;
        this.lastTerminationTick = -1L;
        if (initialCapital > 0L) {
            this.villageBalance += initialCapital;
        }
    }

    /**
     * Establishes a formal cooperation partnership, crediting {@code initialCapital} and ensuring
     * the municipal treasury starts with at least {@code workingCapitalFloor} IC so early Lv 1–3
     * villages do not immediately stall on weekly buy orders.
     *
     * @param ownerPlayerUuid player UUID who negotiated the partnership
     * @param companyAccountId optional guild/company account ID (nullable)
     * @param initialCapital seed IC transferred from player/company ({@code >= 0})
     * @param workingCapitalFloor minimum starting treasury balance in IC ({@code >= 0})
     * @return extra municipal working capital top-up added beyond {@code initialCapital} ({@code >= 0})
     */
    public long establishPartnershipWithWorkingCapital(
            UUID ownerPlayerUuid,
            String companyAccountId,
            long initialCapital,
            long workingCapitalFloor) {
        establishPartnership(ownerPlayerUuid, companyAccountId, initialCapital);
        return ensureInitialWorkingCapital(workingCapitalFloor);
    }

    /**
     * Tops up {@link #getVillageBalance()} to at least {@code workingCapitalFloor} if currently below it.
     *
     * <p>Strictly one-time per village ({@link #isInitialWorkingCapitalGranted()}): terminating and
     * re-signing partnership can NEVER grant free working capital a second time.
     *
     * @param workingCapitalFloor minimum municipal working capital floor in IC ({@code >= 0})
     * @return IC amount added to reach the floor ({@code 0} if already granted or already at/above floor)
     */
    public long ensureInitialWorkingCapital(long workingCapitalFloor) {
        if (this.initialWorkingCapitalGranted) {
            return 0L;
        }
        this.initialWorkingCapitalGranted = true;
        if (workingCapitalFloor <= 0L || this.villageBalance >= workingCapitalFloor) {
            return 0L;
        }
        long added = workingCapitalFloor - this.villageBalance;
        this.villageBalance = workingCapitalFloor;
        return added;
    }

    /**
     * Terminates cooperation partnership while retaining storage contents, active weekly order
     * state/timers (preventing terminate+re-sign shop rerolls), and treasury intact.
     */
    public void terminatePartnership() {
        this.partnered = false;
        this.ownerPlayerUuid = null;
        this.companyAccountId = null;
        this.activeExpansion = null;
        this.hubDeployed = false;
    }

    /**
     * Terminates cooperation partnership and records monotonic {@code currentGameTime} as
     * {@link #getLastTerminationTick()} so re-signing is gated by a full 1-week cooldown that
     * cannot be reset by {@code /time set 0} or skipped via bed sleeping.
     *
     * @param currentGameTime current monotonic overworld {@code gameTime} tick
     */
    public void terminatePartnership(long currentGameTime) {
        terminatePartnership();
        this.lastTerminationTick = Math.max(0L, currentGameTime);
    }

    /**
     * Checks whether this village may currently generate new trade orders (must be partnered and
     * not paused due to depopulation).
     *
     * @return {@code true} if order generation is enabled
     */
    public boolean canGenerateOrders() {
        return partnered && MayorElectionRules.isOrderGenerationAllowed(mayorState);
    }

    /**
     * Checks whether this village satisfies all requirements to upgrade to {@code level + 1}
     * using {@link VillageLevelTable#DEFAULT}.
     *
     * @return {@code true} if eligible to upgrade
     */
    public boolean canUpgrade() {
        return VillageLevelTable.canUpgrade(level, population, cumulativeTradeVolume, villageBalance);
    }

    /**
     * Evaluates upgrade eligibility with a per-requirement breakdown using {@code table}.
     *
     * @param table level progression table
     * @return detailed upgrade status
     */
    public VillageLevelTable.UpgradeStatus evaluateUpgrade(VillageLevelTable table) {
        Objects.requireNonNull(table, "table");
        return table.evaluateUpgrade(level, population, cumulativeTradeVolume, villageBalance);
    }

    /**
     * Attempts to upgrade this village by one level using {@link VillageLevelTable#DEFAULT},
     * burning the level's capital sink from {@code villageBalance} and expanding the default
     * expansion radius if needed.
     *
     * @return burned capital sink in IC ({@code > 0} on success, {@code 0} if not eligible)
     */
    public long tryUpgrade() {
        return tryUpgrade(VillageLevelTable.DEFAULT);
    }

    /**
     * Attempts to upgrade this village by one level using {@code table}.
     *
     * @param table level progression table
     * @return burned capital sink in IC ({@code > 0} on success, {@code 0} if ineligible)
     */
    public long tryUpgrade(VillageLevelTable table) {
        Objects.requireNonNull(table, "table");
        VillageLevelTable.UpgradeStatus status = evaluateUpgrade(table);
        if (!status.eligible() || status.targetSpec() == null) {
            return 0L;
        }
        int previousDefaultMax = table.getSpec(level).expansionRadiusMax();
        VillageLevelTable.LevelSpec next = status.targetSpec();
        long sink = next.upgradeCostSink();
        this.villageBalance -= sink;
        this.level = next.level();
        if (this.maxExpansionRadius == previousDefaultMax || this.maxExpansionRadius < next.expansionRadiusMax()) {
            this.maxExpansionRadius = Math.max(this.minExpansionRadius, clampRadius(next.expansionRadiusMax()));
        }
        return sink;
    }

    /**
     * Applies periodic municipal upkeep using the default 2% rate on excess balance above
     * {@link VillageLevelTable#targetReserve(int)}, deducting and returning the deflationary sink burned.
     *
     * @return burned upkeep amount in IC ({@code >= 0})
     */
    public long applyMunicipalUpkeep() {
        return applyMunicipalUpkeep(DAILY_EXCESS_UPKEEP_RATE);
    }

    /**
     * Applies periodic municipal upkeep using a configured {@code upkeepRate} (clamped to {@code [0.0, 0.25]})
     * on excess balance above {@link VillageLevelTable#targetReserve(int)}.
     *
     * @param upkeepRate configured upkeep fraction (0.0 disables upkeep)
     * @return burned upkeep amount in IC ({@code >= 0})
     */
    public long applyMunicipalUpkeep(double upkeepRate) {
        if (!Double.isFinite(upkeepRate) || upkeepRate <= 0.0) {
            return 0L;
        }
        double clampedRate = Math.min(0.25, upkeepRate);
        double target = VillageLevelTable.targetReserve(level);
        if (villageBalance <= target) {
            return 0L;
        }
        long excess = (long) Math.floor(villageBalance - target);
        long upkeep = Math.min(villageBalance, (long) Math.floor(excess * clampedRate));
        if (upkeep > 0L) {
            villageBalance -= upkeep;
        }
        return upkeep;
    }

    /**
     * Applies a gentle weekly municipal stipend when this village's balance falls below
     * {@code targetReserve * stipendRatio}, preventing permanent 0-IC buy-order deadlock.
     *
     * <p>When {@code villageBalance < targetReserve * stipendRatio}, adds
     * {@code min(deficit, max(150, round(targetReserve * 0.30)))} to {@link #getVillageBalance()}
     * and returns the added amount so server managers can synchronize macro-economy totals cleanly.
     *
     * @param stipendRatio threshold fraction of {@link VillageLevelTable#targetReserve(int)} in {@code [0.0, 1.0]}
     *                     ({@code <= 0.0} disables stipend)
     * @return IC amount credited to this village ({@code >= 0})
     */
    public long applyWeeklyMunicipalStipend(double stipendRatio) {
        if (!Double.isFinite(stipendRatio) || stipendRatio <= 0.0) {
            return 0L;
        }
        double clampedRatio = Math.min(1.0, stipendRatio);
        double target = VillageLevelTable.targetReserve(level);
        long threshold = Math.round(target * clampedRatio);
        if (villageBalance >= threshold) {
            return 0L;
        }
        long deficit = threshold - villageBalance;
        long cap = Math.max(150L, Math.round(target * 0.30));
        long grant = Math.min(deficit, cap);
        if (grant > 0L) {
            villageBalance += grant;
        }
        return grant;
    }

    /**
     * Returns whether at least one full {@code refreshPeriodTicks} (1 MC week) has elapsed since
     * {@link #getLastStipendTick()} so a weekly municipal stipend may be evaluated.
     *
     * @param currentDayTime current overworld {@code dayTime} tick
     * @param refreshPeriodTicks configured weekly period in ticks
     * @return {@code true} if a weekly stipend cycle has elapsed
     */
    public boolean isWeeklyStipendDue(long currentDayTime, int refreshPeriodTicks) {
        if (this.lastStipendTick < 0L) {
            return false;
        }
        long period = Math.max(1L, refreshPeriodTicks);
        long safeNow = Math.max(0L, currentDayTime);
        long anchor = SupplyCycleClock.normalizeRefreshAnchor(safeNow, this.lastStipendTick);
        return (safeNow - anchor) >= period;
    }

    /**
     * Strictly weekly-gated wrapper around {@link #applyWeeklyMunicipalStipend(double)}.
     *
     * <p>Only evaluates and grants a stipend when at least {@code refreshPeriodTicks} of {@code dayTime}
     * have elapsed since {@link #getLastStipendTick()}, then advances {@link #getLastStipendTick()} to
     * {@code currentDayTime} so no village can ever receive more than one stipend per MC week.
     *
     * @param stipendRatio threshold fraction of target reserve in {@code [0.0, 1.0]}
     * @param currentDayTime current overworld {@code dayTime} tick
     * @param refreshPeriodTicks configured weekly period in ticks
     * @return IC amount credited to this village ({@code 0} if not yet due or above threshold)
     */
    public long applyWeeklyMunicipalStipendIfDue(double stipendRatio, long currentDayTime, int refreshPeriodTicks) {
        if (!isWeeklyStipendDue(currentDayTime, refreshPeriodTicks)) {
            return 0L;
        }
        this.lastStipendTick = Math.max(0L, currentDayTime);
        return applyWeeklyMunicipalStipend(stipendRatio);
    }

    /**
     * Fulfills up to {@code requestedQty} of a {@link OrderSide#VILLAGE_BUYS} order by consuming
     * matching items from Hub Inbound slots ({@code 0..499}) and paying out from {@code villageBalance}.
     *
     * @param orderId target order ID
     * @param requestedQty desired quantity to deliver ({@code >= 1})
     * @return trade execution outcome containing actual quantity, IC payout, and structured failure code
     */
    public TradeExecutionResult executeBuyOrderFromInbound(String orderId, int requestedQty) {
        return executeBuyOrderInternal(orderId, requestedQty, null);
    }

    /**
     * Fulfills up to {@code requestedQty} of a {@link OrderSide#VILLAGE_BUYS} order by consuming
     * items accepted by {@code itemMatcher} (e.g., tag-matched item variants) from Hub Inbound slots
     * ({@code 0..499}) and paying out from {@code villageBalance}.
     *
     * @param orderId target order ID
     * @param requestedQty desired quantity to deliver ({@code >= 1})
     * @param itemMatcher predicate selecting acceptable Inbound stacks
     * @return trade execution outcome containing actual quantity, IC payout, and structured failure code
     */
    public TradeExecutionResult executeBuyOrderFromInboundMatching(
            String orderId, int requestedQty, Predicate<SlotStack> itemMatcher) {
        Objects.requireNonNull(itemMatcher, "itemMatcher");
        return executeBuyOrderInternal(orderId, requestedQty, itemMatcher);
    }

    private TradeExecutionResult executeBuyOrderInternal(
            String orderId, int requestedQty, Predicate<SlotStack> overrideMatcher) {
        if (requestedQty < 1) {
            return new TradeExecutionResult(
                    false, 0, 0L, null, "Quantity must be >= 1", TradeFailureCode.INVALID_QTY);
        }
        int idx = findOrderIndex(orderId);
        if (idx < 0) {
            return new TradeExecutionResult(
                    false, 0, 0L, null, "Order not found", TradeFailureCode.ORDER_NOT_FOUND);
        }
        VillageTradeOrder order = activeOrders.get(idx);
        if (order.side() != OrderSide.VILLAGE_BUYS) {
            return new TradeExecutionResult(
                    false, 0, 0L, order, "Order is not a village buy order", TradeFailureCode.WRONG_SIDE);
        }
        if (order.isCompleted()) {
            return new TradeExecutionResult(
                    false, 0, 0L, order, "Order already completed", TradeFailureCode.ALREADY_COMPLETED);
        }
        Predicate<SlotStack> matcher = overrideMatcher != null
                ? overrideMatcher
                : stack -> stack.matchesItem(order.itemId());
        int availableInbound = storage.countInInbound(matcher);
        if (availableInbound <= 0) {
            return new TradeExecutionResult(
                    false, 0, 0L, order,
                    "Insufficient items in Inbound zone",
                    TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS);
        }
        int affordableQty = (int) Math.min(Integer.MAX_VALUE, villageBalance / order.unitPrice());
        if (affordableQty <= 0) {
            return new TradeExecutionResult(
                    false, 0, 0L, order,
                    "Village treasury balance insufficient",
                    TradeFailureCode.INSUFFICIENT_VILLAGE_FUNDS);
        }
        int qty = Math.min(requestedQty, Math.min(order.remainingQty(), Math.min(availableInbound, affordableQty)));
        if (!storage.consumeFromInboundMatching(matcher, qty)) {
            return new TradeExecutionResult(
                    false, 0, 0L, order,
                    "Failed to consume Inbound items",
                    TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS);
        }
        long totalPayout = order.totalPriceFor(qty);
        this.villageBalance -= totalPayout;
        this.cumulativeTradeVolume += totalPayout;
        VillageTradeOrder updated = order.withAddedFulfillment(qty);
        activeOrders.set(idx, updated);
        return new TradeExecutionResult(true, qty, totalPayout, updated, "", TradeFailureCode.NONE);
    }

    /**
     * Executes a purchase of {@code requestedQty} items from a {@link OrderSide#VILLAGE_SELLS} order,
     * depositing purchased goods into Hub Outbound slots ({@code 500..999}) and crediting {@code villageBalance}.
     * Caller is responsible for charging the player/company account by {@code result.totalCurrency()}.
     *
     * @param orderId target order ID
     * @param requestedQty quantity to buy ({@code >= 1})
     * @param nbtJson optional NBT payload (e.g. enchanted book NBT)
     * @param maxStackSize item stack limit ({@code >= 1})
     * @return trade execution outcome containing actual quantity, total IC cost, and structured failure code
     */
    public TradeExecutionResult executeSellOrderToOutbound(
            String orderId, int requestedQty, String nbtJson, int maxStackSize) {
        if (requestedQty < 1) {
            return new TradeExecutionResult(
                    false, 0, 0L, null, "Quantity must be >= 1", TradeFailureCode.INVALID_QTY);
        }
        int idx = findOrderIndex(orderId);
        if (idx < 0) {
            return new TradeExecutionResult(
                    false, 0, 0L, null, "Order not found", TradeFailureCode.ORDER_NOT_FOUND);
        }
        VillageTradeOrder order = activeOrders.get(idx);
        if (order.side() != OrderSide.VILLAGE_SELLS) {
            return new TradeExecutionResult(
                    false, 0, 0L, order, "Order is not a village sell order", TradeFailureCode.WRONG_SIDE);
        }
        if (order.isCompleted()) {
            return new TradeExecutionResult(
                    false, 0, 0L, order, "Order already completed", TradeFailureCode.ALREADY_COMPLETED);
        }
        int qty = Math.min(requestedQty, order.remainingQty());
        if (!storage.canFitInOutbound(order.itemId(), qty, nbtJson, maxStackSize)) {
            return new TradeExecutionResult(
                    false, 0, 0L, order, "Outbound zone is full", TradeFailureCode.OUTBOUND_FULL);
        }
        int remainder = storage.depositToOutbound(order.itemId(), qty, nbtJson, maxStackSize, false);
        if (remainder != 0) {
            return new TradeExecutionResult(
                    false, 0, 0L, order, "Failed to deposit into Outbound zone", TradeFailureCode.OUTBOUND_FULL);
        }
        long totalCost = order.totalPriceFor(qty);
        this.villageBalance += totalCost;
        this.cumulativeTradeVolume += totalCost;
        VillageTradeOrder updated = order.withAddedFulfillment(qty);
        activeOrders.set(idx, updated);
        return new TradeExecutionResult(true, qty, totalCost, updated, "", TradeFailureCode.NONE);
    }

    /**
     * Returns the unique village UUID.
     *
     * @return village UUID
     */
    public UUID getVillageId() {
        return villageId;
    }

    /**
     * Returns the dimension ID containing this village.
     *
     * @return dimension ID
     */
    public String getDimensionId() {
        return dimensionId;
    }

    /**
     * Updates the dimension ID containing this village.
     *
     * @param dimensionId dimension ID
     */
    public void setDimensionId(String dimensionId) {
        this.dimensionId = requireNonBlank(dimensionId, "dimensionId");
    }

    /**
     * Returns the display name of this village.
     *
     * @return village name
     */
    public String getVillageName() {
        return villageName;
    }

    /**
     * Updates the display name of this village.
     *
     * @param villageName new village name
     */
    public void setVillageName(String villageName) {
        this.villageName = requireNonBlank(villageName, "villageName");
    }

    /**
     * Returns the Cooperation Hub (or initial Bell) block position.
     *
     * @return Hub position
     */
    public BlockPos3i getHubPos() {
        return hubPos;
    }

    /**
     * Updates the Cooperation Hub position (e.g. upon authorized Hub relocation).
     *
     * @param hubPos new Hub block position
     */
    public void setHubPos(BlockPos3i hubPos) {
        this.hubPos = Objects.requireNonNull(hubPos, "hubPos");
    }

    /**
     * Returns the village's architectural {@link BiomeStyle}.
     *
     * @return biome style
     */
    public BiomeStyle getBiomeStyle() {
        return biomeStyle;
    }

    /**
     * Updates the village's architectural {@link BiomeStyle}.
     *
     * @param biomeStyle biome style
     */
    public void setBiomeStyle(BiomeStyle biomeStyle) {
        this.biomeStyle = Objects.requireNonNull(biomeStyle, "biomeStyle");
    }

    /**
     * Returns whether this village is currently partnered with a player or guild.
     *
     * @return {@code true} if partnered
     */
    public boolean isPartnered() {
        return partnered;
    }

    /**
     * Sets the partnership status directly.
     *
     * @param partnered partnership status
     */
    public void setPartnered(boolean partnered) {
        this.partnered = partnered;
    }

    /**
     * Returns the owning player's UUID, or {@code null} if unpartnered.
     *
     * @return owner UUID or null
     */
    public UUID getOwnerPlayerUuid() {
        return ownerPlayerUuid;
    }

    /**
     * Sets the owning player's UUID.
     *
     * @param ownerPlayerUuid owner UUID or null
     */
    public void setOwnerPlayerUuid(UUID ownerPlayerUuid) {
        this.ownerPlayerUuid = ownerPlayerUuid;
    }

    /**
     * Returns the linked company/guild account ID, or {@code null} for personal settlement.
     *
     * @return company account ID or null
     */
    public String getCompanyAccountId() {
        return companyAccountId;
    }

    /**
     * Sets the linked company/guild account ID.
     *
     * @param companyAccountId company account ID or null/blank to clear
     */
    public void setCompanyAccountId(String companyAccountId) {
        this.companyAccountId = (companyAccountId == null || companyAccountId.isBlank()) ? null : companyAccountId;
    }

    /**
     * Returns the current village level ({@code 1..10}).
     *
     * @return village level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the village level (clamped to {@code 1..10}).
     *
     * @param level village level
     */
    public void setLevel(int level) {
        this.level = Math.max(VillageLevelTable.MIN_LEVEL, Math.min(VillageLevelTable.MAX_LEVEL, level));
    }

    /**
     * Returns the last tracked living villager population.
     *
     * @return villager population
     */
    public int getPopulation() {
        return population;
    }

    /**
     * Updates the living villager population (clamped to {@code >= 0}).
     *
     * @param population villager population
     */
    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    /**
     * Returns cumulative IC trade volume conducted through this village.
     *
     * @return cumulative trade volume
     */
    public long getCumulativeTradeVolume() {
        return cumulativeTradeVolume;
    }

    /**
     * Sets cumulative IC trade volume (clamped to {@code >= 0}).
     *
     * @param cumulativeTradeVolume trade volume
     */
    public void setCumulativeTradeVolume(long cumulativeTradeVolume) {
        this.cumulativeTradeVolume = Math.max(0L, cumulativeTradeVolume);
    }

    /**
     * Adds {@code delta} IC to cumulative trade volume.
     *
     * @param delta non-negative trade value
     */
    public void addTradeVolume(long delta) {
        if (delta > 0L) {
            this.cumulativeTradeVolume += delta;
        }
    }

    /**
     * Returns the village's municipal treasury balance in IC.
     *
     * @return village balance
     */
    public long getVillageBalance() {
        return villageBalance;
    }

    /**
     * Sets the village's municipal treasury balance (clamped to {@code >= 0}).
     *
     * @param villageBalance balance in IC
     */
    public void setVillageBalance(long villageBalance) {
        this.villageBalance = Math.max(0L, villageBalance);
    }

    /**
     * Adjusts {@link #getVillageBalance()} by {@code delta} (clamped at {@code >= 0}).
     *
     * @param delta signed IC adjustment
     */
    public void adjustVillageBalance(long delta) {
        this.villageBalance = Math.max(0L, this.villageBalance + delta);
    }

    /**
     * Returns the mutable {@link MayorState} for this village.
     *
     * @return Mayor state
     */
    public MayorState getMayorState() {
        return mayorState;
    }

    /**
     * Returns the 1,000-slot dual-zone {@link CooperationHubStorage} for this village.
     *
     * @return Hub storage
     */
    public CooperationHubStorage getStorage() {
        return storage;
    }

    /**
     * Returns an unmodifiable view of active trade orders.
     *
     * @return active trade orders
     */
    public List<VillageTradeOrder> getActiveOrders() {
        return Collections.unmodifiableList(activeOrders);
    }

    /**
     * Replaces all active trade orders with {@code orders}.
     *
     * @param orders new list of orders
     */
    public void setActiveOrders(List<VillageTradeOrder> orders) {
        Objects.requireNonNull(orders, "orders");
        activeOrders.clear();
        activeOrders.addAll(orders);
        if (!orders.isEmpty()) {
            this.initialOrdersGenerated = true;
        }
    }

    /**
     * Adds a single trade order to {@link #getActiveOrders()}.
     *
     * @param order order to add
     */
    public void addOrder(VillageTradeOrder order) {
        activeOrders.add(Objects.requireNonNull(order, "order"));
    }

    /**
     * Looks up an active trade order by {@code orderId}.
     *
     * @param orderId order ID
     * @return optional matching order
     */
    public Optional<VillageTradeOrder> findOrder(String orderId) {
        int idx = findOrderIndex(orderId);
        return idx >= 0 ? Optional.of(activeOrders.get(idx)) : Optional.empty();
    }

    /**
     * Replaces an existing order with the same {@code orderId}.
     *
     * @param updated updated order snapshot
     * @return {@code true} if an existing order was replaced
     */
    public boolean updateOrder(VillageTradeOrder updated) {
        Objects.requireNonNull(updated, "updated");
        int idx = findOrderIndex(updated.orderId());
        if (idx < 0) {
            return false;
        }
        activeOrders.set(idx, updated);
        return true;
    }

    /**
     * Removes any order that is completed or expired as of {@code currentTick}.
     *
     * @param currentTick current server game tick
     * @return number of pruned orders
     */
    public int pruneFinishedOrExpiredOrders(long currentTick) {
        int before = activeOrders.size();
        activeOrders.removeIf(o -> o.isCompleted() || o.isExpired(currentTick));
        return before - activeOrders.size();
    }

    /**
     * Returns the currently active {@link ExpansionDesire}, or {@code null} if none.
     *
     * @return active expansion desire or null
     */
    public ExpansionDesire getActiveExpansion() {
        return activeExpansion;
    }

    /**
     * Sets or clears the active {@link ExpansionDesire}.
     *
     * @param activeExpansion new expansion desire or null
     */
    public void setActiveExpansion(ExpansionDesire activeExpansion) {
        this.activeExpansion = activeExpansion;
    }

    /**
     * Returns an unmodifiable view of all protected occupied 3D bounding boxes in this village.
     *
     * @return occupied structure zones
     */
    public List<Aabb3i> getOccupiedZones() {
        return Collections.unmodifiableList(occupiedZones);
    }

    /**
     * Registers a protected occupied 3D bounding box so future expansions never overlap it.
     *
     * @param zone bounding box to register
     */
    public void addOccupiedZone(Aabb3i zone) {
        occupiedZones.add(Objects.requireNonNull(zone, "zone"));
    }

    /**
     * Removes a single matching occupied zone (used when cancelling an aid project that pre-reserved
     * its site bounding box prior to block placement).
     *
     * @param zone bounding box to remove
     * @return {@code true} if removed
     */
    public boolean removeOccupiedZone(Aabb3i zone) {
        return zone != null && occupiedZones.remove(zone);
    }

    /**
     * Returns an unmodifiable view of all reserved LC2 logistics pad coordinates.
     *
     * @return reserved LC2 pad coordinates
     */
    public List<BlockPos3i> getReservedLc2Pads() {
        return Collections.unmodifiableList(reservedLc2Pads);
    }

    /**
     * Registers a reserved LC2 logistics pad anchor so future buildings preserve its waterway/airway clearance.
     *
     * @param padPos pad anchor coordinate
     */
    public void addReservedLc2Pad(BlockPos3i padPos) {
        reservedLc2Pads.add(Objects.requireNonNull(padPos, "padPos"));
    }

    /**
     * Returns whether automatic village expansion is currently paused by the player.
     *
     * @return {@code true} if expansion is paused
     */
    public boolean isExpansionPaused() {
        return expansionPaused;
    }

    /**
     * Sets whether village expansion is paused.
     *
     * @param expansionPaused pause flag
     */
    public void setExpansionPaused(boolean expansionPaused) {
        this.expansionPaused = expansionPaused;
    }

    /**
     * Returns the configured inner expansion radius (blocks from {@link #getHubPos()}).
     *
     * @return minimum expansion radius
     */
    public int getMinExpansionRadius() {
        return minExpansionRadius;
    }

    /**
     * Returns the configured outer expansion radius (blocks from {@link #getHubPos()}).
     *
     * @return maximum expansion radius
     */
    public int getMaxExpansionRadius() {
        return maxExpansionRadius;
    }

    /**
     * Configures the concentric expansion radius bounds clamped to {@code [10, 72]}.
     *
     * @param minRadius inner radius
     * @param maxRadius outer radius
     */
    public void setExpansionRadiusBounds(int minRadius, int maxRadius) {
        int clampedMin = clampRadius(minRadius);
        int clampedMax = clampRadius(maxRadius);
        if (clampedMin > clampedMax) {
            int tmp = clampedMin;
            clampedMin = clampedMax;
            clampedMax = tmp;
        }
        this.minExpansionRadius = clampedMin;
        this.maxExpansionRadius = clampedMax;
    }

    /**
     * Returns the {@code dayTime} tick anchor of the last demand order refresh.
     *
     * @return last demand refresh tick
     */
    public long getLastDemandRefreshTick() {
        return lastDemandRefreshTick;
    }

    /**
     * Sets the {@code dayTime} tick anchor of the last demand order refresh.
     *
     * @param lastDemandRefreshTick tick timestamp ({@code < 0} marks uninitialized)
     */
    public void setLastDemandRefreshTick(long lastDemandRefreshTick) {
        if (lastDemandRefreshTick < 0L) {
            this.lastDemandRefreshTick = -1L;
        } else {
            this.lastDemandRefreshTick = lastDemandRefreshTick;
            this.initialOrdersGenerated = true;
        }
    }

    /**
     * Returns whether this village has ever had initial weekly orders generated.
     *
     * @return {@code true} if initial orders were generated at least once
     */
    public boolean hasInitialOrdersGenerated() {
        return initialOrdersGenerated;
    }

    /**
     * Sets whether initial weekly orders have been generated for this village.
     *
     * @param initialOrdersGenerated initialization flag
     */
    public void setInitialOrdersGenerated(boolean initialOrdersGenerated) {
        this.initialOrdersGenerated = initialOrdersGenerated;
    }

    /**
     * Returns the {@code dayTime} tick anchor of the last weekly stipend evaluation ({@code -1} if unset).
     *
     * @return last stipend tick
     */
    public long getLastStipendTick() {
        return lastStipendTick;
    }

    /**
     * Sets the {@code dayTime} tick anchor of the last weekly stipend evaluation ({@code < 0} resets to unset).
     *
     * @param lastStipendTick stipend anchor tick
     */
    public void setLastStipendTick(long lastStipendTick) {
        this.lastStipendTick = lastStipendTick < 0L ? -1L : lastStipendTick;
    }

    /**
     * Ensures {@link #getLastStipendTick()} is initialized to {@code currentDayTime} if currently unset.
     *
     * @param currentDayTime current overworld {@code dayTime} tick
     */
    public void anchorStipendIfUnset(long currentDayTime) {
        if (this.lastStipendTick < 0L) {
            this.lastStipendTick = Math.max(0L, currentDayTime);
        }
    }

    /**
     * Returns whether this village has already received its one-time initial working capital top-up.
     *
     * @return {@code true} if one-time working capital has already been granted
     */
    public boolean isInitialWorkingCapitalGranted() {
        return initialWorkingCapitalGranted;
    }

    /**
     * Sets the one-time initial working capital granted flag (for SavedData deserialization).
     *
     * @param initialWorkingCapitalGranted flag state
     */
    public void setInitialWorkingCapitalGranted(boolean initialWorkingCapitalGranted) {
        this.initialWorkingCapitalGranted = initialWorkingCapitalGranted;
    }

    /**
     * Returns the monotonic {@code gameTime} tick when partnership was last terminated
     * ({@code -1} if never terminated or cleared upon re-signing).
     *
     * @return last termination tick
     */
    public long getLastTerminationTick() {
        return lastTerminationTick;
    }

    /**
     * Sets the monotonic {@code gameTime} tick when partnership was last terminated
     * ({@code < 0} clears cooldown).
     *
     * @param lastTerminationTick termination tick
     */
    public void setLastTerminationTick(long lastTerminationTick) {
        this.lastTerminationTick = lastTerminationTick < 0L ? -1L : lastTerminationTick;
    }

    /**
     * Computes remaining monotonic {@code gameTime} ticks before this village may re-sign a
     * partnership after termination, clamped to {@code [0, cooldownTicks]}.
     *
     * <p>Because {@code gameTime} is strictly monotonic on normal servers, neither {@code /time set 0}
     * nor sleeping can reset or bypass the cooldown. If a legacy save stored a {@code dayTime} value
     * larger than {@code currentGameTime}, this safely returns {@code 0} rather than locking out
     * re-signing.
     *
     * @param currentGameTime current monotonic overworld {@code gameTime} tick
     * @param cooldownTicks required re-sign cooldown in ticks (default {@code demandRefreshTicks})
     * @return remaining cooldown ticks ({@code 0} when re-signing is permitted)
     */
    public long remainingResignCooldownTicks(long currentGameTime, int cooldownTicks) {
        if (this.lastTerminationTick < 0L || cooldownTicks <= 0) {
            return 0L;
        }
        long safeNow = Math.max(0L, currentGameTime);
        if (safeNow < this.lastTerminationTick) {
            return 0L;
        }
        long elapsed = safeNow - this.lastTerminationTick;
        return elapsed >= cooldownTicks ? 0L : (cooldownTicks - elapsed);
    }

    /**
     * Returns whether an authoritative live Cooperation Hub block is currently deployed in-world for
     * this village (used to prevent duplicate Hub item aliasing across unloaded chunks or dimensions).
     *
     * @return {@code true} if a Hub block is currently deployed
     */
    public boolean isHubDeployed() {
        return hubDeployed;
    }

    /**
     * Updates the authoritative Hub deployment flag.
     *
     * @param hubDeployed {@code true} when Hub block is placed/restored; {@code false} when packed or terminated
     */
    public void setHubDeployed(boolean hubDeployed) {
        this.hubDeployed = hubDeployed;
    }

    /**
     * Re-anchors {@link #getLastDemandRefreshTick()} and {@link #getLastStipendTick()} via
     * {@link SupplyCycleClock#normalizeRefreshAnchor(long, long)} when {@code currentDayTime}
     * jumped backward (e.g., {@code /time set 0}) or a legacy save recorded a {@code gameTime}
     * greater than {@code dayTime}. Never overwrites unset ({@code < 0}) anchors or monotonic
     * {@link #getLastTerminationTick()}.
     *
     * @param currentDayTime current overworld {@code dayTime} tick
     * @return {@code true} if any stored anchor changed and should be marked dirty
     */
    public boolean normalizeDemandRefreshAnchor(long currentDayTime) {
        boolean changed = false;
        if (this.lastDemandRefreshTick >= 0L) {
            long normalized = SupplyCycleClock.normalizeRefreshAnchor(currentDayTime, this.lastDemandRefreshTick);
            if (normalized != this.lastDemandRefreshTick) {
                this.lastDemandRefreshTick = normalized;
                changed = true;
            }
        }
        if (this.lastStipendTick >= 0L) {
            long normalized = SupplyCycleClock.normalizeRefreshAnchor(currentDayTime, this.lastStipendTick);
            if (normalized != this.lastStipendTick) {
                this.lastStipendTick = normalized;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Returns the game tick when the last expansion project was launched.
     *
     * @return last expansion tick
     */
    public long getLastExpansionTick() {
        return lastExpansionTick;
    }

    /**
     * Sets the game tick when the last expansion project was launched.
     *
     * @param lastExpansionTick tick timestamp
     */
    public void setLastExpansionTick(long lastExpansionTick) {
        this.lastExpansionTick = Math.max(0L, lastExpansionTick);
    }

    /**
     * Returns an unmodifiable view of queued and active {@link ConstructionJob}s.
     *
     * @return construction job queue
     */
    public List<ConstructionJob> getConstructionQueue() {
        return Collections.unmodifiableList(constructionQueue);
    }

    /**
     * Enqueues a planned {@link ConstructionJob} for gradual server-tick execution.
     *
     * @param job construction job to enqueue
     */
    public void enqueueConstruction(ConstructionJob job) {
        constructionQueue.add(Objects.requireNonNull(job, "job"));
    }

    /**
     * Enqueues a planned {@link ConstructionJob}, optionally inserting high-priority aid construction
     * jobs ahead of queued regular expansion jobs without interrupting an already-started head job.
     *
     * @param job construction job to enqueue
     * @param highPriority {@code true} to insert at index 1 if index 0 already has placed blocks, else index 0
     */
    public void enqueueConstructionWithPriority(ConstructionJob job, boolean highPriority) {
        Objects.requireNonNull(job, "job");
        if (!highPriority || constructionQueue.isEmpty()) {
            constructionQueue.add(job);
            return;
        }
        ConstructionJob head = constructionQueue.get(0);
        if (!head.isCompleted() && head.getPlacedCount() > 0) {
            constructionQueue.add(1, job);
        } else {
            constructionQueue.add(0, job);
        }
    }

    /**
     * Returns the dynamic villager scan radius covering the full village expansion perimeter:
     * {@code max(48, maxExpansionRadius + 12)}.
     *
     * @return villager scan radius in blocks
     */
    public int effectiveVillagerScanRadius() {
        return Math.max(48, maxExpansionRadius + 12);
    }

    /**
     * Returns an unmodifiable view of active and queued {@link AidConstructionProject}s.
     *
     * @return aid project list
     */
    public List<AidConstructionProject> getAidProjects() {
        return Collections.unmodifiableList(aidProjects);
    }

    /**
     * Replaces the aid project list with {@code projects}.
     *
     * @param projects replacement list (null clears list)
     */
    public void setAidProjects(List<AidConstructionProject> projects) {
        aidProjects.clear();
        if (projects != null) {
            aidProjects.addAll(projects);
        }
    }

    /**
     * Appends an {@link AidConstructionProject} unconditionally.
     *
     * @param project aid project to add
     */
    public void addAidProject(AidConstructionProject project) {
        aidProjects.add(Objects.requireNonNull(project, "project"));
    }

    /**
     * Appends an {@link AidConstructionProject} if the queue has fewer than {@code maxQueued} entries.
     *
     * @param project aid project to add
     * @param maxQueued maximum queue capacity ({@code >= 1})
     * @return {@code true} if added, {@code false} if queue is full
     */
    public boolean addAidProject(AidConstructionProject project, int maxQueued) {
        Objects.requireNonNull(project, "project");
        if (aidProjects.size() >= Math.max(1, maxQueued)) {
            return false;
        }
        aidProjects.add(project);
        return true;
    }

    /**
     * Looks up an aid project by its {@link AidConstructionProject#projectId()}.
     *
     * @param projectId project identifier
     * @return optional matching project
     */
    public Optional<AidConstructionProject> findAidProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        for (AidConstructionProject project : aidProjects) {
            if (projectId.equals(project.projectId())) {
                return Optional.of(project);
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up an aid project linked to a {@link ConstructionJob#getJobId()}.
     *
     * @param jobId construction job ID
     * @return optional matching project
     */
    public Optional<AidConstructionProject> findAidProjectByJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        for (AidConstructionProject project : aidProjects) {
            if (jobId.equals(project.plannedJobId())) {
                return Optional.of(project);
            }
        }
        return Optional.empty();
    }

    /**
     * Replaces an existing aid project with matching {@link AidConstructionProject#projectId()}.
     *
     * @param updated updated project snapshot
     * @return {@code true} if found and replaced
     */
    public boolean updateAidProject(AidConstructionProject updated) {
        Objects.requireNonNull(updated, "updated");
        for (int i = 0; i < aidProjects.size(); i++) {
            if (updated.projectId().equals(aidProjects.get(i).projectId())) {
                aidProjects.set(i, updated);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes an aid project by {@code projectId} and returns the removed snapshot if present.
     *
     * @param projectId project identifier
     * @return optional removed aid project
     */
    public Optional<AidConstructionProject> removeAidProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        for (int i = 0; i < aidProjects.size(); i++) {
            if (projectId.equals(aidProjects.get(i).projectId())) {
                return Optional.of(aidProjects.remove(i));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns an unmodifiable map of {@code professionId -> completed/committed aid build count}
     * persisted for anti-demolish-and-rebuild workstation printing protection.
     *
     * @return completed aid counts by profession
     */
    public Map<String, Integer> getCompletedAidCounts() {
        return Collections.unmodifiableMap(completedAidCounts);
    }

    /**
     * Replaces persisted completed aid counts (for SavedData deserialization).
     *
     * @param counts map of {@code professionId -> count}
     */
    public void setCompletedAidCounts(Map<String, Integer> counts) {
        completedAidCounts.clear();
        if (counts != null) {
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && e.getValue() > 0) {
                    completedAidCounts.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue());
                }
            }
        }
    }

    /**
     * Returns the historical count of completed/committed aided workplaces built for {@code professionId}.
     *
     * @param professionId namespaced profession ID
     * @return completed count ({@code >= 0})
     */
    public int getCompletedAidCount(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return 0;
        }
        return completedAidCounts.getOrDefault(professionId.trim().toLowerCase(Locale.ROOT), 0);
    }

    /**
     * Increments the persisted historical completed aid count for {@code professionId}.
     *
     * @param professionId namespaced profession ID
     */
    public void incrementCompletedAidCount(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return;
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        completedAidCounts.merge(norm, 1, Integer::sum);
    }

    /**
     * Returns the combined count of completed historical aid builds PLUS currently queued/building
     * aid projects for {@code professionId} in this village.
     *
     * @param professionId namespaced profession ID
     * @return total committed + active aid builds for this profession
     */
    public int getTotalAidCountForProfession(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return 0;
        }
        String norm = professionId.trim().toLowerCase(Locale.ROOT);
        int total = completedAidCounts.getOrDefault(norm, 0);
        for (AidConstructionProject p : aidProjects) {
            if (p.professionId() != null && norm.equals(p.professionId().trim().toLowerCase(Locale.ROOT))) {
                total++;
            }
        }
        return total;
    }

    /**
     * Returns whether another aided workplace for {@code professionId} may be started without
     * exceeding {@link WorkplaceCatalog#MAX_AID_BUILDS_PER_PROFESSION}.
     *
     * @param professionId namespaced profession ID
     * @return {@code true} if below the per-village profession cap
     */
    public boolean canStartAidForProfession(String professionId) {
        return getTotalAidCountForProfession(professionId) < WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION;
    }

    /**
     * Returns the last scanned (or persisted) {@link VillageEmploymentStats} snapshot.
     *
     * @return employment statistics (never null)
     */
    public VillageEmploymentStats getEmploymentStats() {
        return employmentStats;
    }

    /**
     * Updates the cached {@link VillageEmploymentStats} snapshot.
     *
     * @param stats new snapshot (null resets to {@link VillageEmploymentStats#EMPTY})
     */
    public void setEmploymentStats(VillageEmploymentStats stats) {
        this.employmentStats = stats == null ? VillageEmploymentStats.EMPTY : stats;
    }

    /**
     * Returns the head non-completed {@link ConstructionJob} in the queue, if any.
     *
     * @return active construction job
     */
    public Optional<ConstructionJob> getActiveConstructionJob() {
        for (ConstructionJob job : constructionQueue) {
            if (!job.isCompleted()) {
                return Optional.of(job);
            }
        }
        return Optional.empty();
    }

    /**
     * Removes completed {@link ConstructionJob}s from the queue.
     *
     * @return number of completed jobs removed
     */
    public int pruneCompletedConstructionJobs() {
        int before = constructionQueue.size();
        constructionQueue.removeIf(ConstructionJob::isCompleted);
        return before - constructionQueue.size();
    }

    /**
     * Clears and returns all pending/active {@link ConstructionJob}s (used on partnership termination).
     *
     * @return snapshot of cleared construction jobs
     */
    public List<ConstructionJob> clearConstructionQueue() {
        List<ConstructionJob> cleared = new ArrayList<>(constructionQueue);
        constructionQueue.clear();
        return cleared;
    }

    /**
     * Clears and returns all queued/active {@link AidConstructionProject}s (used on partnership termination).
     *
     * @return snapshot of cleared aid projects
     */
    public List<AidConstructionProject> clearAidProjects() {
        List<AidConstructionProject> cleared = new ArrayList<>(aidProjects);
        aidProjects.clear();
        return cleared;
    }

    /**
     * Returns whether an LC2 Chunk Loading Upgrade is installed in this village's Cooperation Hub
     * so chunk tickets can be cleanly restored after server restarts.
     *
     * @return {@code true} if a chunk loading upgrade is installed
     */
    public boolean isChunkLoaderInstalled() {
        return chunkLoaderInstalled;
    }

    /**
     * Updates whether an LC2 Chunk Loading Upgrade is installed in this village's Cooperation Hub.
     *
     * @param chunkLoaderInstalled installed flag
     */
    public void setChunkLoaderInstalled(boolean chunkLoaderInstalled) {
        this.chunkLoaderInstalled = chunkLoaderInstalled;
    }

    /**
     * Returns the earliest server game tick at which a failed auto-expansion site scan may retry.
     *
     * @return retry backoff tick
     */
    public long getNextAutoExpansionRetryTick() {
        return nextAutoExpansionRetryTick;
    }

    /**
     * Sets the earliest server game tick at which auto-expansion may retry after a site scan failure.
     *
     * @param nextAutoExpansionRetryTick retry backoff tick
     */
    public void setNextAutoExpansionRetryTick(long nextAutoExpansionRetryTick) {
        this.nextAutoExpansionRetryTick = Math.max(0L, nextAutoExpansionRetryTick);
    }

    private int findOrderIndex(String orderId) {
        if (orderId == null) {
            return -1;
        }
        for (int i = 0; i < activeOrders.size(); i++) {
            if (orderId.equals(activeOrders.get(i).orderId())) {
                return i;
            }
        }
        return -1;
    }

    private static int clampRadius(int radius) {
        return Math.max(MIN_CONFIGURABLE_RADIUS, Math.min(MAX_CONFIGURABLE_RADIUS, radius));
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return value;
    }
}
