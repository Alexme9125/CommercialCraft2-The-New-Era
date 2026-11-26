package com.tycooncraft.tyc2.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Collision-safe concentric-ring village expansion planner.
 *
 * <p>Searches concentric rings outward from {@link VillageRecord#getHubPos()} within
 * {@code [minExpansionRadius, maxExpansionRadius]} while enforcing:
 * <ol>
 *   <li>Zero overlap (with a 2-block horizontal clearance buffer) against every registered
 *       {@code occupiedZone}, queued {@link ConstructionJob}, Cooperation Hub core, and
 *       reserved LC2 logistics pad airway/waterway corridor.</li>
 *   <li>Terrain flatness check ({@code maxY - minY <= 4}).</li>
 *   <li>Strict protection check ({@code !probe.isProtectedOrOccupied(...)}) across the entire
 *       padded footprint and interior, replacing only natural clutter.</li>
 *   <li>Water-edge requirement for {@link ProjectType#LC2_WATERFRONT_PIER} and open-sky
 *       clearance for {@link ProjectType#LC2_HELIPAD_PLAZA} and piers.</li>
 *   <li>Automatic bottom-up foundation support pillars on slopes plus a collision-safe road spur
 *       connecting the structure's front entrance toward the Cooperation Hub.</li>
 * </ol>
 */
public final class ExpansionPlanner {

    /** Minimum horizontal clearance buffer (blocks) kept around every structure footprint. */
    public static final int CLEARANCE_PADDING = 2;

    /** Maximum permitted terrain elevation delta ({@code maxY - minY}) across a footprint. */
    public static final int MAX_ELEVATION_DELTA = 4;

    /** Minimum horizontal keep-out radius around the Cooperation Hub block itself. */
    public static final int HUB_CORE_CLEARANCE = 4;

    /** Horizontal clearance radius preserved around each reserved LC2 logistics pad. */
    public static final int LC2_PAD_CLEARANCE = 6;

    /** Maximum downward depth (blocks) for slope foundation support pillars. */
    public static final int MAX_FOUNDATION_PILLAR_DEPTH = 6;

    /** Maximum downward depth (blocks) for underwater waterfront pier pilings. */
    public static final int MAX_PIER_PILING_DEPTH = 8;

    /** Maximum length (blocks) of the connecting road spur generated toward the Hub. */
    public static final int MAX_ROAD_SPUR_LENGTH = 5;

    /** Radial step size (blocks) between concentric search rings. */
    public static final int RING_STEP = 3;

    /**
     * Outcome returned by {@link #plan(VillageRecord, ProjectType, TerrainProbe, String)}.
     *
     * @param success whether a collision-safe site was found
     * @param job planned bottom-up {@link ConstructionJob} (null on failure)
     * @param reservedZone 3D bounding box to register in {@link VillageRecord#addOccupiedZone(Aabb3i)} (null on failure)
     * @param lc2PadPos reserved LC2 pad coordinate if project is an LC2 pad (nullable)
     * @param facing chosen structure facing (null on failure)
     * @param failureReason human-readable reason when {@code success == false}
     */
    public record PlanResult(
            boolean success,
            ConstructionJob job,
            Aabb3i reservedZone,
            BlockPos3i lc2PadPos,
            Facing2D facing,
            String failureReason) {

        /**
         * Returns the planned {@link ConstructionJob} wrapped in an {@link Optional}.
         *
         * @return optional construction job
         */
        public Optional<ConstructionJob> jobOptional() {
            return Optional.ofNullable(job);
        }

        /**
         * Returns the reserved LC2 pad position wrapped in an {@link Optional}.
         *
         * @return optional LC2 pad anchor
         */
        public Optional<BlockPos3i> lc2PadOptional() {
            return Optional.ofNullable(lc2PadPos);
        }

        static PlanResult fail(String reason) {
            return new PlanResult(false, null, null, null, null, reason);
        }

        static PlanResult ok(
                ConstructionJob job, Aabb3i reservedZone, BlockPos3i lc2PadPos, Facing2D facing) {
            return new PlanResult(true, job, reservedZone, lc2PadPos, facing, "");
        }
    }

    /**
     * Creates a stateless {@link ExpansionPlanner}.
     */
    public ExpansionPlanner() {
    }

    /**
     * Plans a collision-safe placement for {@code projectType} around {@code village} without
     * mutating {@code village}. Call {@link #commitPlan(VillageRecord, PlanResult, long)} to
     * register the reserved zone and enqueue the {@link ConstructionJob}.
     *
     * @param village target village state
     * @param projectType structure category to place
     * @param probe pure-Java terrain callback
     * @param jobId unique identifier assigned to the generated {@link ConstructionJob}
     * @return planning result
     */
    public PlanResult plan(
            VillageRecord village, ProjectType projectType, TerrainProbe probe, String jobId) {
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(projectType, "projectType");
        StructureBlueprint canonical = VillageArchitectureCatalog.getBlueprint(
                village.getBiomeStyle(), projectType);
        return planBlueprintIgnoringZone(village, canonical, probe, jobId, false, null);
    }

    /**
     * Plans a collision-safe placement for an explicit canonical {@link StructureBlueprint}
     * (used by both regular village expansion and player-funded aided workplaces) without mutating
     * {@code village}.
     *
     * @param village target village state
     * @param canonical canonical {@link Facing2D#NORTH}-facing blueprint
     * @param probe pure-Java terrain callback
     * @param jobId unique identifier assigned to the generated {@link ConstructionJob}
     * @param ignorePause {@code true} to allow player-funded aid construction even while auto-expansion is paused
     * @return planning result
     */
    public PlanResult planBlueprint(
            VillageRecord village,
            StructureBlueprint canonical,
            TerrainProbe probe,
            String jobId,
            boolean ignorePause) {
        return planBlueprintIgnoringZone(village, canonical, probe, jobId, ignorePause, null);
    }

    /**
     * Plans a collision-safe placement for an explicit canonical {@link StructureBlueprint},
     * optionally ignoring one pre-reserved {@link Aabb3i} belonging to the same aid project so it
     * can finalize cleanly onto its own reserved footprint (or fall back to any other safe ring site).
     *
     * @param village target village state
     * @param canonical canonical {@link Facing2D#NORTH}-facing blueprint
     * @param probe pure-Java terrain callback
     * @param jobId unique identifier assigned to the generated {@link ConstructionJob}
     * @param ignorePause {@code true} to bypass {@link VillageRecord#isExpansionPaused()}
     * @param ignoredOwnReservedZone optional pre-locked zone owned by this aid project (nullable)
     * @return planning result
     */
    public PlanResult planBlueprintIgnoringZone(
            VillageRecord village,
            StructureBlueprint canonical,
            TerrainProbe probe,
            String jobId,
            boolean ignorePause,
            Aabb3i ignoredOwnReservedZone) {
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(probe, "probe");
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be null or blank");
        }

        if (!ignorePause && village.isExpansionPaused()) {
            return PlanResult.fail("Village expansion is currently paused");
        }

        int minRadius = village.getMinExpansionRadius();
        int maxRadius = village.getMaxExpansionRadius();
        if (minRadius > maxRadius) {
            return PlanResult.fail("Invalid expansion radius bounds");
        }

        ProjectType projectType = canonical.projectType();
        BlockPos3i hub = village.getHubPos();

        // Collect all existing blocked zones (registered occupiedZones excluding own pre-reserved zone + active queue)
        List<Aabb3i> blockedZones = new ArrayList<>(
                village.getOccupiedZones().size() + village.getConstructionQueue().size());
        boolean skippedOwnZone = false;
        for (Aabb3i zone : village.getOccupiedZones()) {
            if (!skippedOwnZone && ignoredOwnReservedZone != null && ignoredOwnReservedZone.equals(zone)) {
                skippedOwnZone = true;
                continue;
            }
            blockedZones.add(zone);
        }
        Set<BlockPos3i> queuedBlockCoords = new HashSet<>();
        for (ConstructionJob queued : village.getConstructionQueue()) {
            blockedZones.add(queued.getBoundingBox());
            for (WorldBlockPlacement wp : queued.getPlacements()) {
                queuedBlockCoords.add(wp.pos());
            }
        }

        // First attempt: reuse the exact pre-reserved footprint if still clean and collision-safe
        if (ignoredOwnReservedZone != null) {
            int cx = ignoredOwnReservedZone.minX() + (ignoredOwnReservedZone.sizeX() / 2);
            int cz = ignoredOwnReservedZone.minZ() + (ignoredOwnReservedZone.sizeZ() / 2);
            Facing2D[] preferredFacings = candidateFacings(projectType, hub, cx, cz);
            for (Facing2D facing : preferredFacings) {
                StructureBlueprint bp = canonical.rotated(facing);
                if (bp.width() == ignoredOwnReservedZone.sizeX()
                        && bp.depth() == ignoredOwnReservedZone.sizeZ()) {
                    Optional<PlanResult> reservedCandidate = evaluateCandidate(
                            village, bp,
                            ignoredOwnReservedZone.minX(), ignoredOwnReservedZone.minZ(),
                            probe, blockedZones, queuedBlockCoords, jobId);
                    if (reservedCandidate.isPresent()) {
                        return reservedCandidate.get();
                    }
                }
            }
        }

        // Rotate starting angle based on existing structure count so village expands evenly in 360 degrees
        double baseAngleOffset = (blockedZones.size() * 2.399963229728653); // golden angle (rad)

        for (int r = minRadius; r <= maxRadius; r = nextRadius(r, maxRadius)) {
            int steps = Math.max(12, (int) Math.round((2.0 * Math.PI * r) / 4.0));
            for (int i = 0; i < steps; i++) {
                double angle = baseAngleOffset + (2.0 * Math.PI * i) / steps;
                int cx = hub.x() + (int) Math.round(Math.cos(angle) * r);
                int cz = hub.z() + (int) Math.round(Math.sin(angle) * r);

                Facing2D[] facingsToTry = candidateFacings(projectType, hub, cx, cz);
                for (Facing2D facing : facingsToTry) {
                    StructureBlueprint bp = canonical.rotated(facing);
                    int originX = cx - (bp.width() / 2);
                    int originZ = cz - (bp.depth() / 2);

                    Optional<PlanResult> candidate = evaluateCandidate(
                            village, bp, originX, originZ, probe, blockedZones, queuedBlockCoords, jobId);
                    if (candidate.isPresent()) {
                        return candidate.get();
                    }
                }
            }
            if (r == maxRadius) {
                break;
            }
        }

        return PlanResult.fail(
                "No collision-safe terrain site found for " + projectType
                        + " within radius " + minRadius + ".." + maxRadius);
    }

    /**
     * Commits a successful {@link PlanResult} into {@code village}: registers its occupied zone,
     * registers any reserved LC2 pad coordinate, enqueues the {@link ConstructionJob}, and records
     * {@code currentTick} as {@link VillageRecord#getLastExpansionTick()}.
     *
     * @param village target village record to mutate
     * @param result successful plan result
     * @param currentTick current server game tick
     * @return {@code true} if committed, {@code false} if {@code result} was unsuccessful
     */
    public boolean commitPlan(VillageRecord village, PlanResult result, long currentTick) {
        return commitPlanWithPriority(village, result, currentTick, false);
    }

    /**
     * Commits a successful {@link PlanResult} into {@code village} with optional high-priority queue
     * insertion for player-funded aid construction.
     *
     * <p>When {@code highPriority} is {@code true}, the job is placed ahead of queued regular expansion
     * jobs (at index 1 if index 0 already has placed blocks, or index 0 otherwise) and does not overwrite
     * {@link VillageRecord#getLastExpansionTick()} so regular auto-expansion cadence stays independent.
     *
     * @param village target village record to mutate
     * @param result successful plan result
     * @param currentTick current server game tick
     * @param highPriority {@code true} for aided workplace priority insertion
     * @return {@code true} if committed, {@code false} if {@code result} was unsuccessful
     */
    public boolean commitPlanWithPriority(
            VillageRecord village, PlanResult result, long currentTick, boolean highPriority) {
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(result, "result");
        if (!result.success() || result.job() == null || result.reservedZone() == null) {
            return false;
        }
        if (!village.getOccupiedZones().contains(result.reservedZone())) {
            village.addOccupiedZone(result.reservedZone());
        }
        if (result.lc2PadPos() != null) {
            village.addReservedLc2Pad(result.lc2PadPos());
        }
        village.enqueueConstructionWithPriority(result.job(), highPriority);
        if (!highPriority) {
            village.setLastExpansionTick(currentTick);
        }
        return true;
    }

    /**
     * Recommends the next {@link ProjectType} for a village based on its level, current population
     * vs cap, and previously built projects.
     *
     * @param village target village record
     * @param hasNearbyWater whether a waterfront site is known to be near the village
     * @return recommended project type
     */
    public ProjectType selectNextProjectType(VillageRecord village, boolean hasNearbyWater) {
        Objects.requireNonNull(village, "village");
        int level = village.getLevel();
        int popCap = VillageLevelTable.getClamped(level).maxPopulationCap();
        int occupiedCount = village.getOccupiedZones().size();

        // Early priority: grow housing whenever population has room to grow
        if (village.getPopulation() < popCap) {
            int cycle = occupiedCount % 4;
            if (cycle == 0 || cycle == 2) {
                if (level >= ProjectType.MULTI_STORY_TOWNHOUSE.minVillageLevel()) {
                    return ProjectType.MULTI_STORY_TOWNHOUSE;
                }
                if (level >= ProjectType.MEDIUM_RESIDENCE.minVillageLevel()) {
                    return ProjectType.MEDIUM_RESIDENCE;
                }
                return ProjectType.SMALL_COTTAGE;
            }
        }

        // LC2 logistics pads once unlocked and none reserved yet
        if (village.getReservedLc2Pads().isEmpty()) {
            if (hasNearbyWater && level >= ProjectType.LC2_WATERFRONT_PIER.minVillageLevel()) {
                return ProjectType.LC2_WATERFRONT_PIER;
            }
            if (level >= ProjectType.LC2_HELIPAD_PLAZA.minVillageLevel()) {
                return ProjectType.LC2_HELIPAD_PLAZA;
            }
        }

        // Balanced civic & agrarian mix
        return switch (occupiedCount % 5) {
            case 0 -> ProjectType.ROAD_AND_LAMPS;
            case 1 -> ProjectType.CROP_FARM;
            case 2 -> level >= ProjectType.ANIMAL_RANCH.minVillageLevel()
                    ? ProjectType.ANIMAL_RANCH
                    : ProjectType.CROP_FARM;
            case 3 -> level >= ProjectType.LC2_HELIPAD_PLAZA.minVillageLevel()
                    ? ProjectType.LC2_HELIPAD_PLAZA
                    : ProjectType.SMALL_COTTAGE;
            default -> level >= ProjectType.MEDIUM_RESIDENCE.minVillageLevel()
                    ? ProjectType.MEDIUM_RESIDENCE
                    : ProjectType.SMALL_COTTAGE;
        };
    }

    private Optional<PlanResult> evaluateCandidate(
            VillageRecord village,
            StructureBlueprint bp,
            int originX,
            int originZ,
            TerrainProbe probe,
            List<Aabb3i> blockedZones,
            Set<BlockPos3i> queuedBlockCoords,
            String jobId) {
        int w = bp.width();
        int h = bp.height();
        int d = bp.depth();
        int maxX = originX + w - 1;
        int maxZ = originZ + d - 1;

        // 1. Fast horizontal overlap check against Hub core, reserved LC2 pads, and all occupied zones (+2 clearance!)
        Aabb3i prelimHorizBox = new Aabb3i(originX, 0, originZ, maxX, 0, maxZ);
        Aabb3i paddedHorizBox = prelimHorizBox.padHorizontal(CLEARANCE_PADDING);

        Aabb3i hubCoreBox = Aabb3i.singleBlock(village.getHubPos()).padHorizontal(HUB_CORE_CLEARANCE);
        if (paddedHorizBox.intersectsHorizontal(hubCoreBox)) {
            return Optional.empty();
        }
        for (Aabb3i zone : blockedZones) {
            if (paddedHorizBox.intersectsHorizontal(zone)) {
                return Optional.empty();
            }
        }
        for (BlockPos3i pad : village.getReservedLc2Pads()) {
            Aabb3i padClearance = Aabb3i.singleBlock(pad).padHorizontal(LC2_PAD_CLEARANCE);
            if (paddedHorizBox.intersectsHorizontal(padClearance)) {
                return Optional.empty();
            }
        }

        // 2. Terrain elevation, foundation, and special waterfront/sky rules
        boolean isPier = bp.projectType().requiresWaterEdge();
        int[][] surfaceYs = new int[w][d];
        boolean[][] isWater = new boolean[w][d];

        int originY;
        int minGroundY;

        if (isPier) {
            PierFit pierFit = evaluatePierTerrain(bp, originX, originZ, probe, surfaceYs, isWater);
            if (pierFit == null) {
                return Optional.empty();
            }
            originY = pierFit.originY;
            minGroundY = pierFit.minGroundY;
        } else {
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int dx = 0; dx < w; dx++) {
                int wx = originX + dx;
                for (int dz = 0; dz < d; dz++) {
                    int wz = originZ + dz;
                    if (probe.isWaterSurface(wx, wz)) {
                        return Optional.empty();
                    }
                    int sy = probe.getSurfaceY(wx, wz);
                    if (!probe.isSolidFoundation(wx, sy, wz)) {
                        return Optional.empty();
                    }
                    surfaceYs[dx][dz] = sy;
                    if (sy < minY) {
                        minY = sy;
                    }
                    if (sy > maxY) {
                        maxY = sy;
                    }
                }
            }
            if (maxY - minY > MAX_ELEVATION_DELTA) {
                return Optional.empty();
            }
            originY = maxY + 1;
            minGroundY = minY;
        }

        // 3. Strict protection & natural-only clearance across padded footprint + interior
        int scanMaxY = originY + h;
        for (int wx = originX - CLEARANCE_PADDING; wx <= maxX + CLEARANCE_PADDING; wx++) {
            for (int wz = originZ - CLEARANCE_PADDING; wz <= maxZ + CLEARANCE_PADDING; wz++) {
                boolean insideFootprint = wx >= originX && wx <= maxX && wz >= originZ && wz <= maxZ;
                boolean waterCol = insideFootprint
                        ? isWater[wx - originX][wz - originZ]
                        : probe.isWaterSurface(wx, wz);
                int colSurfaceY = insideFootprint
                        ? surfaceYs[wx - originX][wz - originZ]
                        : probe.getSurfaceY(wx, wz);

                // For water columns (allowed in pier water zone or outer margin), check strictly above water surface;
                // for land columns, check from ground surface upward so buried pipes/cables/floors are never touched!
                int scanStartY = waterCol ? Math.max(colSurfaceY + 1, originY) : Math.min(colSurfaceY, minGroundY);
                for (int y = scanStartY; y <= scanMaxY; y++) {
                    if (probe.isProtectedOrOccupied(wx, y, wz)) {
                        return Optional.empty();
                    }
                    if (insideFootprint && y >= originY && y < originY + h) {
                        if (!probe.isNaturalReplaceable(wx, y, wz)) {
                            return Optional.empty();
                        }
                    }
                    if (insideFootprint && !waterCol && y > colSurfaceY && y < originY) {
                        // Slope foundation fill zone must only displace natural clutter
                        if (!probe.isNaturalReplaceable(wx, y, wz)) {
                            return Optional.empty();
                        }
                    }
                }
            }
        }

        // 3b. For waterfront piers, verify underwater piling columns never overwrite protected blocks/BEs/pipes!
        if (isPier) {
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < d; dz++) {
                    if (!isWater[dx][dz]) {
                        continue;
                    }
                    boolean cornerOrEdgePost = (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1 || dz == d / 2)
                            || (dz == 0 || dz == d - 1) && (dx == 0 || dx == w - 1 || dx == w / 2);
                    if (!cornerOrEdgePost) {
                        continue;
                    }
                    int wx = originX + dx;
                    int wz = originZ + dz;
                    for (int depth = 1; depth <= MAX_PIER_PILING_DEPTH; depth++) {
                        int y = originY - depth;
                        if (probe.isProtectedUnderwater(wx, y, wz)) {
                            return Optional.empty();
                        }
                        if (probe.isSolidFoundation(wx, y, wz)) {
                            break;
                        }
                    }
                }
            }
        }

        // 4. Open-sky clearance for LC2 logistics pads (helipad & pier deck)
        if (bp.projectType().requiresOpenSky()) {
            int padMinX = originX + 1;
            int padMaxX = maxX - 1;
            int padMinZ = originZ + 1;
            int padMaxZ = maxZ - 1;
            for (int wx = padMinX; wx <= padMaxX; wx++) {
                for (int wz = padMinZ; wz <= padMaxZ; wz++) {
                    if (!probe.isSkyClear(wx, originY + 1, wz)) {
                        return Optional.empty();
                    }
                }
            }
        }

        // 5. Assemble complete bottom-up placements: foundation pillars + blueprint + connecting road spur
        BlockPos3i origin = new BlockPos3i(originX, originY, originZ);
        BiomePalette palette = village.getBiomeStyle().palette();
        Map<BlockPos3i, String> placementMap = new LinkedHashMap<>();

        // 5a. Foundation support pillars / waterfront pilings
        int lowestPlacedY = originY;
        if (isPier) {
            lowestPlacedY = addPierPilings(bp, origin, surfaceYs, isWater, probe, palette, placementMap, lowestPlacedY);
        } else {
            lowestPlacedY = addSlopeFoundations(bp, origin, surfaceYs, probe, palette, placementMap, lowestPlacedY);
        }

        // 5b. Blueprint blocks (override any pillar/spur overlap)
        for (BlockPlacement rel : bp.placements()) {
            BlockPos3i wp = origin.offset(rel.dx(), rel.dy(), rel.dz());
            if (queuedBlockCoords.contains(wp)) {
                return Optional.empty();
            }
            placementMap.put(wp, rel.blockStateSpec());
            if (wp.y() < lowestPlacedY) {
                lowestPlacedY = wp.y();
            }
        }

        // 5c. Short connecting road spur stepping from front entrance toward Hub
        addConnectingRoadSpur(
                village, bp, origin, probe, blockedZones, queuedBlockCoords, palette, placementMap);

        // Build final bottom-up sorted placement list
        List<WorldBlockPlacement> worldPlacements = new ArrayList<>(placementMap.size());
        for (Map.Entry<BlockPos3i, String> e : placementMap.entrySet()) {
            worldPlacements.add(new WorldBlockPlacement(e.getKey(), e.getValue()));
        }
        worldPlacements.sort(WorldBlockPlacement.BOTTOM_UP_ORDER);

        // Translate spawn markers and locate any LC2 pad reservation anchor
        List<SpawnMarker.WorldSpawnMarker> worldMarkers = new ArrayList<>(bp.spawnMarkers().size());
        BlockPos3i lc2PadPos = null;
        for (SpawnMarker marker : bp.spawnMarkers()) {
            SpawnMarker.WorldSpawnMarker wm = marker.toWorld(origin);
            worldMarkers.add(wm);
            if (wm.kind() == SpawnMarker.MarkerKind.LC2_PORT_PAD
                    || wm.kind() == SpawnMarker.MarkerKind.LC2_HELIPAD_CENTER) {
                lc2PadPos = wm.pos();
            }
        }

        Aabb3i reservedZone = new Aabb3i(
                originX,
                Math.min(lowestPlacedY, originY),
                originZ,
                maxX,
                originY + h - 1,
                maxZ);

        ConstructionJob job = new ConstructionJob(
                jobId,
                bp.projectType(),
                bp.biomeStyle(),
                bp.facing(),
                origin,
                reservedZone,
                worldPlacements,
                worldMarkers);

        return Optional.of(PlanResult.ok(job, reservedZone, lc2PadPos, bp.facing()));
    }

    private PierFit evaluatePierTerrain(
            StructureBlueprint bp,
            int originX,
            int originZ,
            TerrainProbe probe,
            int[][] surfaceYs,
            boolean[][] isWater) {
        int w = bp.width();
        int d = bp.depth();
        int shoreMinY = Integer.MAX_VALUE;
        int shoreMaxY = Integer.MIN_VALUE;
        int waterMaxY = Integer.MIN_VALUE;
        int dryCount = 0;
        int waterCount = 0;

        for (int dx = 0; dx < w; dx++) {
            int wx = originX + dx;
            for (int dz = 0; dz < d; dz++) {
                int wz = originZ + dz;
                boolean water = probe.isWaterSurface(wx, wz);
                int sy = probe.getSurfaceY(wx, wz);
                isWater[dx][dz] = water;
                surfaceYs[dx][dz] = sy;
                if (water) {
                    waterCount++;
                    if (sy > waterMaxY) {
                        waterMaxY = sy;
                    }
                } else {
                    if (!probe.isSolidFoundation(wx, sy, wz)) {
                        return null;
                    }
                    dryCount++;
                    if (sy < shoreMinY) {
                        shoreMinY = sy;
                    }
                    if (sy > shoreMaxY) {
                        shoreMaxY = sy;
                    }
                }
            }
        }

        // Must bridge shore and water
        if (dryCount == 0 || waterCount == 0) {
            return null;
        }
        // Shore abutment edge (containing roadAttach) must be dry solid land; opposite dock edge must be water!
        if (!checkPierShoreAndWaterEdges(bp, isWater, originX, originZ, probe)) {
            return null;
        }
        if (shoreMaxY - shoreMinY > MAX_ELEVATION_DELTA) {
            return null;
        }
        if (shoreMaxY - waterMaxY > 3 || waterMaxY - shoreMinY > 2) {
            return null;
        }
        int originY = Math.max(shoreMaxY, waterMaxY) + 1;
        return new PierFit(originY, Math.min(shoreMinY, waterMaxY));
    }

    private boolean checkPierShoreAndWaterEdges(
            StructureBlueprint bp, boolean[][] isWater, int originX, int originZ, TerrainProbe probe) {
        int w = bp.width();
        int d = bp.depth();
        int ax = bp.roadAttachDx();
        int az = bp.roadAttachDz();

        if (az == 0) {
            // Shore at dz=0, water edge at dz=d-1 extending +Z
            for (int x = 0; x < w; x++) {
                if (isWater[x][0] || !isWater[x][d - 1]) {
                    return false;
                }
            }
            return probe.isWaterSurface(originX + w / 2, originZ + d);
        }
        if (az == d - 1) {
            // Shore at dz=d-1, water edge at dz=0 extending -Z
            for (int x = 0; x < w; x++) {
                if (isWater[x][d - 1] || !isWater[x][0]) {
                    return false;
                }
            }
            return probe.isWaterSurface(originX + w / 2, originZ - 1);
        }
        if (ax == 0) {
            // Shore at dx=0, water edge at dx=w-1 extending +X
            for (int z = 0; z < d; z++) {
                if (isWater[0][z] || !isWater[w - 1][z]) {
                    return false;
                }
            }
            return probe.isWaterSurface(originX + w, originZ + d / 2);
        }
        if (ax == w - 1) {
            // Shore at dx=w-1, water edge at dx=0 extending -X
            for (int z = 0; z < d; z++) {
                if (isWater[w - 1][z] || !isWater[0][z]) {
                    return false;
                }
            }
            return probe.isWaterSurface(originX - 1, originZ + d / 2);
        }
        return false;
    }

    private int addSlopeFoundations(
            StructureBlueprint bp,
            BlockPos3i origin,
            int[][] surfaceYs,
            TerrainProbe probe,
            BiomePalette palette,
            Map<BlockPos3i, String> placementMap,
            int lowestPlacedY) {
        boolean[][] hasBaseBlock = new boolean[bp.width()][bp.depth()];
        for (BlockPlacement rel : bp.placements()) {
            if (rel.dy() == 0) {
                hasBaseBlock[rel.dx()][rel.dz()] = true;
            }
        }
        int lowest = lowestPlacedY;
        for (int dx = 0; dx < bp.width(); dx++) {
            for (int dz = 0; dz < bp.depth(); dz++) {
                if (!hasBaseBlock[dx][dz]) {
                    continue;
                }
                int sy = surfaceYs[dx][dz];
                int wx = origin.x() + dx;
                int wz = origin.z() + dz;
                int floorY = Math.max(sy + 1, origin.y() - MAX_FOUNDATION_PILLAR_DEPTH);
                for (int y = floorY; y < origin.y(); y++) {
                    if (!probe.isProtectedOrOccupied(wx, y, wz) && probe.isNaturalReplaceable(wx, y, wz)) {
                        placementMap.put(new BlockPos3i(wx, y, wz), palette.baseStone());
                        if (y < lowest) {
                            lowest = y;
                        }
                    }
                }
            }
        }
        return lowest;
    }

    private int addPierPilings(
            StructureBlueprint bp,
            BlockPos3i origin,
            int[][] surfaceYs,
            boolean[][] isWater,
            TerrainProbe probe,
            BiomePalette palette,
            Map<BlockPos3i, String> placementMap,
            int lowestPlacedY) {
        int w = bp.width();
        int d = bp.depth();
        int lowest = lowestPlacedY;
        String pilingSpec = BiomePalette.withAxisIfSupported(palette.waterPierLog(), "y");

        // Place support pillars on shore abutment slopes and corner/edge pilings down through water
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                int wx = origin.x() + dx;
                int wz = origin.z() + dz;
                boolean cornerOrEdgePost = (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1 || dz == d / 2)
                        || (dz == 0 || dz == d - 1) && (dx == 0 || dx == w - 1 || dx == w / 2);

                if (!isWater[dx][dz]) {
                    int sy = surfaceYs[dx][dz];
                    int floorY = Math.max(sy + 1, origin.y() - MAX_FOUNDATION_PILLAR_DEPTH);
                    for (int y = floorY; y < origin.y(); y++) {
                        if (!probe.isProtectedOrOccupied(wx, y, wz) && probe.isNaturalReplaceable(wx, y, wz)) {
                            placementMap.put(new BlockPos3i(wx, y, wz), palette.baseStone());
                            if (y < lowest) {
                                lowest = y;
                            }
                        }
                    }
                } else if (cornerOrEdgePost) {
                    for (int depth = 1; depth <= MAX_PIER_PILING_DEPTH; depth++) {
                        int y = origin.y() - depth;
                        if (probe.isProtectedUnderwater(wx, y, wz) || probe.isSolidFoundation(wx, y, wz)) {
                            break;
                        }
                        placementMap.put(new BlockPos3i(wx, y, wz), pilingSpec);
                        if (y < lowest) {
                            lowest = y;
                        }
                    }
                }
            }
        }
        return lowest;
    }

    private void addConnectingRoadSpur(
            VillageRecord village,
            StructureBlueprint bp,
            BlockPos3i origin,
            TerrainProbe probe,
            List<Aabb3i> blockedZones,
            Set<BlockPos3i> queuedBlockCoords,
            BiomePalette palette,
            Map<BlockPos3i, String> placementMap) {
        BlockPos3i hub = village.getHubPos();
        int sx = origin.x() + bp.roadAttachDx();
        int sz = origin.z() + bp.roadAttachDz();
        int prevY = origin.y() - 1;

        // First step steps directly outward from the front entrance along bp.facing()
        Facing2D outward = bp.facing();
        int curX = sx + outward.stepX();
        int curZ = sz + outward.stepZ();

        for (int step = 0; step < MAX_ROAD_SPUR_LENGTH; step++) {
            if (Math.abs(curX - hub.x()) <= 2 && Math.abs(curZ - hub.z()) <= 2) {
                break;
            }
            if (probe.isWaterSurface(curX, curZ)) {
                break;
            }
            int sy = probe.getSurfaceY(curX, curZ);
            if (Math.abs(sy - prevY) > 1) {
                break;
            }
            if (!probe.isSolidFoundation(curX, sy, curZ)) {
                break;
            }
            if (probe.isProtectedOrOccupied(curX, sy, curZ)
                    || probe.isProtectedOrOccupied(curX, sy + 1, curZ)
                    || !probe.isNaturalReplaceable(curX, sy + 1, curZ)) {
                break;
            }
            BlockPos3i spurPos = new BlockPos3i(curX, sy, curZ);
            if (queuedBlockCoords.contains(spurPos) || isInsideAnyZone(curX, curZ, blockedZones)) {
                break;
            }

            placementMap.putIfAbsent(spurPos, palette.roadPrimary());
            prevY = sy;

            // Step next block greedily toward the Hub
            int toHubX = hub.x() - curX;
            int toHubZ = hub.z() - curZ;
            if (toHubX == 0 && toHubZ == 0) {
                break;
            }
            if (Math.abs(toHubX) >= Math.abs(toHubZ)) {
                curX += Integer.signum(toHubX);
            } else {
                curZ += Integer.signum(toHubZ);
            }
        }
    }

    private static boolean isInsideAnyZone(int x, int z, List<Aabb3i> zones) {
        for (Aabb3i zone : zones) {
            if (zone.containsHorizontal(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static Facing2D[] candidateFacings(ProjectType projectType, BlockPos3i hub, int cx, int cz) {
        Facing2D towardHub = Facing2D.fromDelta(hub.x() - cx, hub.z() - cz);
        if (projectType.requiresWaterEdge()) {
            return new Facing2D[]{
                    towardHub,
                    towardHub.clockwise(),
                    towardHub.counterClockwise(),
                    towardHub.opposite()
            };
        }
        return new Facing2D[]{towardHub};
    }

    private static int nextRadius(int current, int maxRadius) {
        if (current >= maxRadius) {
            return maxRadius + 1;
        }
        return Math.min(maxRadius, current + RING_STEP);
    }

    private record PierFit(int originY, int minGroundY) {
    }
}
