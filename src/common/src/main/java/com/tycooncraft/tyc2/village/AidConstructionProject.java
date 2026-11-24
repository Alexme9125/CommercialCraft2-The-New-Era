package com.tycooncraft.tyc2.village;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable state record for a single player-funded villager workplace aid construction project.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link Status#COLLECTING}: villagers gather biome/profession materials virtually in the background
 *       (works even while village chunks are unloaded, accelerated by unemployed adult villagers).</li>
 *   <li>{@link Status#WAITING_SITE}: material gathering finished; waiting for village chunk load and
 *       collision-safe site planning via {@link ExpansionPlanner#planBlueprintIgnoringZone}.</li>
 *   <li>{@link Status#NO_SITE}: site planning attempted while loaded but no safe terrain was available;
 *       retried periodically or cancellable for a full refund.</li>
 *   <li>{@link Status#BUILDING}: planned and pushed into {@link VillageRecord#getConstructionQueue()};
 *       cancellation is no longer permitted once block placement begins.</li>
 * </ol>
 *
 * @param projectId unique aid project identifier
 * @param professionId target villager profession ID (e.g. {@code "minecraft:librarian"})
 * @param workstationBlockId exact workstation block ID placed inside the structure
 * @param archetype architectural blueprint archetype
 * @param displayNameFallback fallback display name (used for dynamically discovered modded professions)
 * @param size workplace size ({@link WorkplaceSize#SMALL} or {@link WorkplaceSize#LARGE})
 * @param status current lifecycle status
 * @param paidIc actual CPI-adjusted IC paid by the player/company (refunded in full on cancel)
 * @param requiredMaterials immutable map of {@code itemId -> requiredQty} for background gathering display
 * @param gatheredProgressTicks fractional gathering ticks accumulated in {@code [0, requiredTicks]}
 * @param requiredTicks total gathering ticks required before site planning ({@code >= 1})
 * @param reservedZone optional pre-locked {@link Aabb3i} footprint reserved at order time (nullable)
 * @param plannedJobId linked {@link ConstructionJob#getJobId()} once in {@link Status#BUILDING} (empty prior)
 * @param payerPlayerUuid UUID of the player who commissioned this aid project (nullable)
 * @param payerCompanyId company/guild bank account ID if funded via company account (nullable)
 * @param paidWithCash {@code true} if funded by physical cash coins, {@code false} if funded via bank account
 */
public record AidConstructionProject(
        String projectId,
        String professionId,
        String workstationBlockId,
        WorkplaceArchetype archetype,
        String displayNameFallback,
        WorkplaceSize size,
        Status status,
        long paidIc,
        Map<String, Integer> requiredMaterials,
        double gatheredProgressTicks,
        int requiredTicks,
        Aabb3i reservedZone,
        String plannedJobId,
        UUID payerPlayerUuid,
        String payerCompanyId,
        boolean paidWithCash) {

    /**
     * Lifecycle states of an {@link AidConstructionProject}.
     */
    public enum Status {
        /** Villagers are gathering required materials in the background. */
        COLLECTING,
        /** Gathering is complete; waiting for chunk load and site finalization. */
        WAITING_SITE,
        /** Gathering is complete, but no collision-safe building site was available on last check. */
        NO_SITE,
        /** Planned and queued into {@link VillageRecord#getConstructionQueue()} for gradual placement. */
        BUILDING
    }

    /**
     * Validates invariants and normalizes fields and material map.
     */
    public AidConstructionProject {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be null or blank");
        }
        if (professionId == null || professionId.isBlank()) {
            throw new IllegalArgumentException("professionId must not be null or blank");
        }
        if (workstationBlockId == null || workstationBlockId.isBlank()) {
            throw new IllegalArgumentException("workstationBlockId must not be null or blank");
        }
        projectId = projectId.trim();
        professionId = professionId.trim().toLowerCase(Locale.ROOT);
        workstationBlockId = workstationBlockId.trim().toLowerCase(Locale.ROOT);
        archetype = archetype == null ? WorkplaceArchetype.GENERIC : archetype;
        displayNameFallback = displayNameFallback == null ? "" : displayNameFallback.trim();
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(status, "status");
        if (paidIc < 0L) {
            throw new IllegalArgumentException("paidIc must be >= 0, got " + paidIc);
        }
        Objects.requireNonNull(requiredMaterials, "requiredMaterials");
        Map<String, Integer> reqCopy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : requiredMaterials.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalArgumentException("Material itemId must not be null or blank");
            }
            int qty = e.getValue() == null ? 0 : e.getValue();
            if (qty <= 0) {
                throw new IllegalArgumentException("Required material qty must be > 0 for " + e.getKey());
            }
            reqCopy.put(e.getKey(), qty);
        }
        requiredMaterials = Collections.unmodifiableMap(reqCopy);
        requiredTicks = Math.max(1, requiredTicks);
        if (!Double.isFinite(gatheredProgressTicks) || gatheredProgressTicks < 0.0) {
            gatheredProgressTicks = 0.0;
        } else if (gatheredProgressTicks > requiredTicks) {
            gatheredProgressTicks = requiredTicks;
        }
        plannedJobId = plannedJobId == null ? "" : plannedJobId.trim();
        payerCompanyId = (payerCompanyId == null || payerCompanyId.isBlank()) ? null : payerCompanyId.trim();
    }

    /**
     * Creates a new {@link AidConstructionProject} starting in {@link Status#COLLECTING} with
     * biome- and profession-styled required materials.
     *
     * @param projectId unique project ID
     * @param spec target workplace spec
     * @param size building size
     * @param biomeStyle village biome style
     * @param paidIc actual paid IC amount
     * @param requiredTicks base gathering duration in ticks
     * @param reservedZone optional pre-locked site bounding box (nullable if ordered remotely while unloaded)
     * @param payerPlayerUuid commissioning player UUID (nullable)
     * @param payerCompanyId commissioning company account ID (nullable)
     * @param paidWithCash {@code true} if paid via physical coins
     * @return new aid construction project
     */
    public static AidConstructionProject create(
            String projectId,
            WorkplaceSpec spec,
            WorkplaceSize size,
            BiomeStyle biomeStyle,
            long paidIc,
            int requiredTicks,
            Aabb3i reservedZone,
            UUID payerPlayerUuid,
            String payerCompanyId,
            boolean paidWithCash) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(biomeStyle, "biomeStyle");
        return new AidConstructionProject(
                projectId,
                spec.professionId(),
                spec.workstationBlockId(),
                spec.archetype(),
                spec.displayNameFallback(),
                size,
                Status.COLLECTING,
                paidIc,
                WorkplaceCatalog.requiredMaterials(spec, size, biomeStyle),
                0.0,
                requiredTicks,
                reservedZone,
                "",
                payerPlayerUuid,
                payerCompanyId,
                paidWithCash);
    }

    /**
     * Returns the fractional material-gathering completion ratio in {@code [0.0, 1.0]}.
     *
     * @return completion ratio
     */
    public double completionRatio() {
        if (status == Status.BUILDING) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, gatheredProgressTicks / (double) requiredTicks));
    }

    /**
     * Returns whether virtual background material gathering has finished.
     *
     * @return {@code true} if gathering is complete
     */
    public boolean isGatheringComplete() {
        return status != Status.COLLECTING || gatheredProgressTicks >= requiredTicks;
    }

    /**
     * Computes proportionally revealed gathered counts per required material based on {@link #completionRatio()}
     * for GUI and HUD display.
     *
     * @return unmodifiable insertion-ordered map of {@code itemId -> displayedGatheredCount}
     */
    public Map<String, Integer> displayedGatheredMaterials() {
        double ratio = completionRatio();
        Map<String, Integer> gathered = new LinkedHashMap<>(requiredMaterials.size());
        for (Map.Entry<String, Integer> e : requiredMaterials.entrySet()) {
            int req = e.getValue();
            int shown = (ratio >= 1.0 - 1e-9) ? req : Math.min(req, (int) Math.floor(req * ratio));
            gathered.put(e.getKey(), Math.max(0, shown));
        }
        return Collections.unmodifiableMap(gathered);
    }

    /**
     * Returns whether this aid project may still be cancelled for a full IC refund
     * (allowed in {@link Status#COLLECTING}, {@link Status#WAITING_SITE}, and {@link Status#NO_SITE};
     * forbidden once in {@link Status#BUILDING}).
     *
     * @return {@code true} if cancellable
     */
    public boolean canCancel() {
        return status != Status.BUILDING;
    }

    /**
     * Advances background material gathering by {@code deltaTicks} (already multiplied by the village's
     * labor speed multiplier). Transitions automatically from {@link Status#COLLECTING} to
     * {@link Status#WAITING_SITE} when {@code gatheredProgressTicks >= requiredTicks}.
     *
     * @param deltaTicks effective gathering progress ticks to add (ignored if {@code <= 0} or not collecting)
     * @return updated project snapshot
     */
    public AidConstructionProject advanceGathering(double deltaTicks) {
        if (status != Status.COLLECTING || !Double.isFinite(deltaTicks) || deltaTicks <= 0.0) {
            return this;
        }
        double next = Math.min(requiredTicks, gatheredProgressTicks + deltaTicks);
        Status nextStatus = next >= requiredTicks ? Status.WAITING_SITE : Status.COLLECTING;
        return new AidConstructionProject(
                projectId,
                professionId,
                workstationBlockId,
                archetype,
                displayNameFallback,
                size,
                nextStatus,
                paidIc,
                requiredMaterials,
                next,
                requiredTicks,
                reservedZone,
                plannedJobId,
                payerPlayerUuid,
                payerCompanyId,
                paidWithCash);
    }

    /**
     * Returns an updated copy with the specified lifecycle {@code nextStatus}.
     *
     * @param nextStatus new lifecycle status
     * @return updated project snapshot
     */
    public AidConstructionProject withStatus(Status nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (this.status == nextStatus) {
            return this;
        }
        double nextTicks = (nextStatus == Status.WAITING_SITE || nextStatus == Status.NO_SITE
                || nextStatus == Status.BUILDING)
                ? requiredTicks
                : gatheredProgressTicks;
        return new AidConstructionProject(
                projectId,
                professionId,
                workstationBlockId,
                archetype,
                displayNameFallback,
                size,
                nextStatus,
                paidIc,
                requiredMaterials,
                nextTicks,
                requiredTicks,
                reservedZone,
                plannedJobId,
                payerPlayerUuid,
                payerCompanyId,
                paidWithCash);
    }

    /**
     * Returns an updated copy with {@link #reservedZone()} updated.
     *
     * @param nextReservedZone new reserved bounding box (nullable)
     * @return updated project snapshot
     */
    public AidConstructionProject withReservedZone(Aabb3i nextReservedZone) {
        if (Objects.equals(this.reservedZone, nextReservedZone)) {
            return this;
        }
        return new AidConstructionProject(
                projectId,
                professionId,
                workstationBlockId,
                archetype,
                displayNameFallback,
                size,
                status,
                paidIc,
                requiredMaterials,
                gatheredProgressTicks,
                requiredTicks,
                nextReservedZone,
                plannedJobId,
                payerPlayerUuid,
                payerCompanyId,
                paidWithCash);
    }

    /**
     * Transitions this aid project into {@link Status#BUILDING} linked to {@code jobId}.
     *
     * @param jobId queued {@link ConstructionJob#getJobId()}
     * @return updated project snapshot in {@link Status#BUILDING}
     */
    public AidConstructionProject withBuilding(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be null or blank");
        }
        return new AidConstructionProject(
                projectId,
                professionId,
                workstationBlockId,
                archetype,
                displayNameFallback,
                size,
                Status.BUILDING,
                paidIc,
                requiredMaterials,
                requiredTicks,
                requiredTicks,
                reservedZone,
                jobId.trim(),
                payerPlayerUuid,
                payerCompanyId,
                paidWithCash);
    }

    /**
     * Reconstructs a minimal {@link WorkplaceSpec} suitable for generating this project's
     * {@link StructureBlueprint} via {@link WorkplaceArchitectureCatalog#getBlueprint}.
     *
     * @return workplace spec matching this project's persisted profession, workstation block, and archetype
     */
    public WorkplaceSpec toWorkplaceSpec() {
        return new WorkplaceSpec(
                professionId,
                workstationBlockId,
                archetype,
                Math.max(1L, paidIc),
                size.defaultCostMultiplier(),
                1,
                3,
                List.of(),
                true,
                archetype == WorkplaceArchetype.GENERIC,
                displayNameFallback);
    }

    /**
     * Estimates remaining real server ticks until material gathering finishes at {@code gatherSpeedMultiplier}.
     *
     * @param gatherSpeedMultiplier current village labor speed multiplier (typically {@code 1.0..2.0})
     * @return estimated remaining ticks ({@code 0} if already complete)
     */
    public int remainingTicksEstimate(double gatherSpeedMultiplier) {
        if (isGatheringComplete()) {
            return 0;
        }
        double speed = (!Double.isFinite(gatherSpeedMultiplier) || gatherSpeedMultiplier <= 0.0)
                ? 1.0
                : gatherSpeedMultiplier;
        return Math.max(0, (int) Math.ceil((requiredTicks - gatheredProgressTicks) / speed));
    }
}
