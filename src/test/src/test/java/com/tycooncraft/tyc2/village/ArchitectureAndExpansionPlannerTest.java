package com.tycooncraft.tyc2.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link VillageArchitectureCatalog}, {@link StructureBlueprint},
 * {@link ExpansionPlanner}, and {@link ConstructionJob}.
 */
class ArchitectureAndExpansionPlannerTest {

    @Test
    void all7BiomesAndAllProjectTypesRotateCleanlyAndSortBottomUp() {
        assertEquals(7, BiomeStyle.values().length);
        assertEquals(10, ProjectType.values().length);
        for (BiomeStyle style : BiomeStyle.values()) {
            for (ProjectType type : ProjectType.values()) {
                StructureBlueprint base = VillageArchitectureCatalog.getBlueprint(style, type);
                assertNotNull(base);
                assertFalse(base.placements().isEmpty(),
                        "Blueprint must not be empty: " + style + " / " + type);

                // Verify spawn marker counts match project semantics
                long villagers = base.spawnMarkers().stream()
                        .filter(m -> m.kind() == SpawnMarker.MarkerKind.VILLAGER).count();
                long animals = base.spawnMarkers().stream()
                        .filter(m -> m.kind() == SpawnMarker.MarkerKind.ANIMAL).count();
                long lc2Pads = base.spawnMarkers().stream()
                        .filter(m -> m.kind() == SpawnMarker.MarkerKind.LC2_PORT_PAD
                                || m.kind() == SpawnMarker.MarkerKind.LC2_HELIPAD_CENTER).count();

                assertEquals(type.villagerSpawnCount(), villagers,
                        "Villager marker count mismatch for " + style + "/" + type);
                assertEquals(type.animalSpawnCount(), animals,
                        "Animal marker count mismatch for " + style + "/" + type);
                assertEquals(type.isLc2LogisticsPad() ? 1 : 0, lc2Pads,
                        "LC2 pad marker count mismatch for " + style + "/" + type);

                // Validate all 4 rotations
                for (Facing2D facing : Facing2D.values()) {
                    StructureBlueprint rot = base.rotated(facing);
                    assertEquals(facing, rot.facing());
                    assertEquals(base.placements().size(), rot.placements().size());

                    int prevDy = Integer.MIN_VALUE;
                    Set<BlockPos3i> seen = new HashSet<>();
                    int doorLowers = 0;
                    int doorUppers = 0;
                    int bedFoots = 0;
                    int bedHeads = 0;

                    for (BlockPlacement bp : rot.placements()) {
                        assertTrue(bp.dx() >= 0 && bp.dx() < rot.width());
                        assertTrue(bp.dy() >= 0 && bp.dy() < rot.height());
                        assertTrue(bp.dz() >= 0 && bp.dz() < rot.depth());
                        assertTrue(bp.dy() >= prevDy,
                                "Placements must be sorted bottom-up for " + style + "/" + type + "/" + facing);
                        prevDy = bp.dy();
                        assertTrue(seen.add(new BlockPos3i(bp.dx(), bp.dy(), bp.dz())),
                                "Duplicate coordinate in blueprint " + style + "/" + type);

                        String spec = bp.blockStateSpec();
                        // Never attach axis property to non-axis blocks like cut_sandstone
                        assertFalse(spec.startsWith("minecraft:cut_sandstone[axis="),
                                "Invalid axis property on sandstone: " + spec);

                        if (spec.contains("half=lower")) {
                            doorLowers++;
                        } else if (spec.contains("half=upper")) {
                            doorUppers++;
                        }
                        if (spec.contains("part=foot")) {
                            bedFoots++;
                        } else if (spec.contains("part=head")) {
                            bedHeads++;
                        }
                    }

                    assertEquals(doorLowers, doorUppers, "Doors must have paired lower/upper halves");
                    assertEquals(bedFoots, bedHeads, "Beds must have paired foot/head halves");
                    if (type.isResidential()) {
                        assertEquals(type.villagerSpawnCount(), bedFoots,
                                "Residential structure must have 1 bed per villager spawn: " + type);
                    }
                }
            }
        }
    }

    @Test
    void multiStepConcentricExpansionNeverOverlapsBuildingsBlockEntitiesOrPlayerBlocks() {
        FakeTerrain terrain = new FakeTerrain(64);
        BlockPos3i hub = new BlockPos3i(0, 64, 0);

        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Metropolis",
                hub, BiomeStyle.PLAINS);
        village.setLevel(10);
        village.setExpansionRadiusBounds(12, 68);

        // Pre-existing village building occupied zone
        Aabb3i preexistingHouse = new Aabb3i(12, 64, -4, 19, 70, 3);
        village.addOccupiedZone(preexistingHouse);

        // Scatter protected player-placed blocks & BlockEntities (chests, machines, pipes) around inner rings
        Set<BlockPos3i> protectedBlocks = new HashSet<>();
        int[][] hazards = {
                {-14, 65, 0}, {-14, 64, 0}, // chest on ground + pipe buried flush in ground
                {0, 65, 16}, {1, 65, 16},   // IC2 generator + cable
                {0, 65, -16}, {18, 65, 18},
                {-20, 65, -18}, {22, 65, -14}
        };
        for (int[] h : hazards) {
            BlockPos3i p = new BlockPos3i(h[0], h[1], h[2]);
            protectedBlocks.add(p);
            terrain.markProtected(p);
        }

        ExpansionPlanner planner = new ExpansionPlanner();
        ProjectType[] sequence = {
                ProjectType.SMALL_COTTAGE,
                ProjectType.CROP_FARM,
                ProjectType.ROAD_AND_LAMPS,
                ProjectType.ANIMAL_RANCH,
                ProjectType.MEDIUM_RESIDENCE,
                ProjectType.LC2_HELIPAD_PLAZA,
                ProjectType.MULTI_STORY_TOWNHOUSE,
                ProjectType.SMALL_COTTAGE,
                ProjectType.MEDIUM_RESIDENCE,
                ProjectType.CROP_FARM
        };

        List<ConstructionJob> plannedJobs = new ArrayList<>();
        Set<BlockPos3i> allPlacedCoords = new HashSet<>();

        for (int i = 0; i < sequence.length; i++) {
            ExpansionPlanner.PlanResult res = planner.plan(village, sequence[i], terrain, "job-" + i);
            assertTrue(res.success(), "Failed to plan step " + i + " (" + sequence[i] + "): " + res.failureReason());
            assertTrue(planner.commitPlan(village, res, (i + 1) * 1000L));
            plannedJobs.add(res.job());

            // Verify every single placed block (including foundations & road spur) avoids protected blocks & duplicates
            for (WorldBlockPlacement wp : res.job().getPlacements()) {
                assertFalse(protectedBlocks.contains(wp.pos()),
                        "Job " + i + " overwrote protected block at " + wp.pos());
                assertFalse(terrain.isProtectedOrOccupied(wp.pos().x(), wp.pos().y(), wp.pos().z()),
                        "Job " + i + " touched protected/occupied coordinate " + wp.pos());
                assertTrue(allPlacedCoords.add(wp.pos()),
                        "Job " + i + " collided with previously scheduled block at " + wp.pos());
            }
        }

        // Verify pairwise 2-block horizontal clearance between ALL occupied zones
        List<Aabb3i> zones = village.getOccupiedZones();
        assertEquals(1 + sequence.length, zones.size());
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                assertFalse(
                        zones.get(i).padHorizontal(ExpansionPlanner.CLEARANCE_PADDING)
                                .intersectsHorizontal(zones.get(j)),
                        "Occupied zones " + i + " and " + j + " violated 2-block clearance: "
                                + zones.get(i) + " vs " + zones.get(j));
            }
        }

        // Verify LC2 helipad pad was reserved
        assertEquals(1, village.getReservedLc2Pads().size());
        assertEquals(sequence.length, plannedJobs.size());

        // Verify no planned structure encroached within the 2-block horizontal buffer of any protected hazard
        for (ConstructionJob job : plannedJobs) {
            Aabb3i padded = job.getBoundingBox().padHorizontal(ExpansionPlanner.CLEARANCE_PADDING);
            for (BlockPos3i hazard : protectedBlocks) {
                assertFalse(padded.containsHorizontal(hazard.x(), hazard.z()),
                        "Job " + job.getJobId() + " placed within 2-block buffer of protected block " + hazard);
            }
        }
    }

    @Test
    void plannerRespectsPauseSlopesWaterfrontPierAndOpenSkyHelipad() {
        ExpansionPlanner planner = new ExpansionPlanner();
        BlockPos3i hub = new BlockPos3i(0, 64, 0);

        // 1. Paused expansion fails immediately
        VillageRecord paused = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Quietville", hub, BiomeStyle.SAVANNA);
        paused.setExpansionPaused(true);
        assertFalse(planner.plan(paused, ProjectType.SMALL_COTTAGE, new FakeTerrain(64), "paused-1").success());

        // 2. Gentle slope (delta <= 4) succeeds and generates bottom-up stone foundation pillars
        FakeTerrain gentleSlope = new FakeTerrain(64);
        gentleSlope.setHeightFunction((x, z) -> 64 + Math.max(0, Math.min(3, (x - 10) / 3)));
        VillageRecord slopeVillage = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Hillcrest", hub, BiomeStyle.DESERT);
        slopeVillage.setExpansionRadiusBounds(12, 24);
        ExpansionPlanner.PlanResult slopeRes =
                planner.plan(slopeVillage, ProjectType.SMALL_COTTAGE, gentleSlope, "slope-1");
        assertTrue(slopeRes.success(), slopeRes.failureReason());
        // Verify bottom-up ordering starts below or at originY
        List<WorldBlockPlacement> placements = slopeRes.job().getPlacements();
        for (int i = 1; i < placements.size(); i++) {
            assertTrue(placements.get(i).pos().y() >= placements.get(i - 1).pos().y());
        }

        // 3. Steep cliff everywhere (delta > 4) fails safely
        FakeTerrain steepCliff = new FakeTerrain(64);
        steepCliff.setHeightFunction((x, z) -> 64 + ((x + z) & 1) * 6);
        assertFalse(planner.plan(slopeVillage, ProjectType.SMALL_COTTAGE, steepCliff, "cliff-1").success());

        // 4. Waterfront pier fails on dry land, succeeds along a shoreline with water pilings
        FakeTerrain dryLand = new FakeTerrain(64);
        VillageRecord coastal = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Harborview", hub, BiomeStyle.SNOWY);
        coastal.setLevel(5);
        coastal.setExpansionRadiusBounds(10, 32);
        assertFalse(planner.plan(coastal, ProjectType.LC2_WATERFRONT_PIER, dryLand, "pier-dry").success());

        FakeTerrain shoreline = new FakeTerrain(64);
        // Everything at z >= 18 is a deep river/lake (surface Y=63, seabed at Y=59)
        shoreline.setWaterPredicate((x, z) -> z >= 18);
        shoreline.setWaterSurfaceY(63);
        shoreline.setSeabedY(59);
        ExpansionPlanner.PlanResult pierRes =
                planner.plan(coastal, ProjectType.LC2_WATERFRONT_PIER, shoreline, "pier-wet");
        assertTrue(pierRes.success(), pierRes.failureReason());
        assertTrue(pierRes.lc2PadOptional().isPresent());
        // Verify pilings descend below deck level into water
        int minPlacedY = pierRes.job().getPlacements().stream()
                .mapToInt(p -> p.pos().y()).min().orElse(Integer.MAX_VALUE);
        assertTrue(minPlacedY < pierRes.job().getOrigin().y(),
                "Pier must generate underwater pilings below originY");

        // 5. Helipad plaza fails when sky is blocked everywhere, succeeds under open sky
        FakeTerrain caveWorld = new FakeTerrain(64);
        caveWorld.setSkyClear(false);
        assertFalse(planner.plan(coastal, ProjectType.LC2_HELIPAD_PLAZA, caveWorld, "heli-cave").success());

        ExpansionPlanner.PlanResult heliRes =
                planner.plan(coastal, ProjectType.LC2_HELIPAD_PLAZA, dryLand, "heli-open");
        assertTrue(heliRes.success(), heliRes.failureReason());
        assertTrue(heliRes.lc2PadOptional().isPresent());
    }

    @Test
    void constructionJobBuildsGraduallyBottomUpAndFiresSpawnMarkersOnCompletion() {
        FakeTerrain terrain = new FakeTerrain(64);
        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Buildtown",
                new BlockPos3i(0, 64, 0), BiomeStyle.TAIGA);
        village.setLevel(6);
        village.setExpansionRadiusBounds(12, 36);

        ExpansionPlanner planner = new ExpansionPlanner();
        ExpansionPlanner.PlanResult res =
                planner.plan(village, ProjectType.MULTI_STORY_TOWNHOUSE, terrain, "townhouse-1");
        assertTrue(res.success(), res.failureReason());

        ConstructionJob job = res.job();
        assertEquals(ConstructionJob.DEFAULT_BLOCKS_PER_STEP, job.getBlocksPerStep());
        assertEquals(ConstructionJob.DEFAULT_TICKS_PER_STEP, job.getTicksPerStep());
        assertFalse(job.isCompleted());
        assertTrue(job.getTotalBlocks() > 200);

        int prevY = Integer.MIN_VALUE;
        int totalPlaced = 0;
        int completionTriggerCount = 0;
        List<SpawnMarker.WorldSpawnMarker> triggeredMarkers = new ArrayList<>();

        int safetyTicks = job.getTotalBlocks() * job.getTicksPerStep() + 100;
        for (int t = 1; t <= safetyTicks && !job.isCompleted(); t++) {
            ConstructionJob.StepResult step = job.tick();
            if (t % job.getTicksPerStep() != 0) {
                assertTrue(step.placedBlocks().isEmpty(), "Should throttle between step intervals");
                assertFalse(step.completedNow());
                assertTrue(step.spawnMarkersToTrigger().isEmpty());
                continue;
            }
            assertTrue(step.placedBlocks().size() >= 1
                    && step.placedBlocks().size() <= job.getBlocksPerStep());
            for (WorldBlockPlacement wp : step.placedBlocks()) {
                assertTrue(wp.pos().y() >= prevY,
                        "ConstructionJob must place strictly bottom-up: " + wp.pos().y() + " < " + prevY);
                prevY = wp.pos().y();
                totalPlaced++;
            }
            if (step.completedNow()) {
                completionTriggerCount++;
                triggeredMarkers.addAll(step.spawnMarkersToTrigger());
            } else {
                assertTrue(step.spawnMarkersToTrigger().isEmpty(),
                        "Spawn markers must not trigger prior to completion");
            }
        }

        assertTrue(job.isCompleted());
        assertEquals(job.getTotalBlocks(), totalPlaced);
        assertEquals(1.0, job.getProgressRatio(), 1e-9);
        assertEquals(1, completionTriggerCount);
        assertEquals(3, triggeredMarkers.size());
        assertTrue(triggeredMarkers.stream().allMatch(m -> m.kind() == SpawnMarker.MarkerKind.VILLAGER));

        // Subsequent ticks/steps are safe no-ops
        ConstructionJob.StepResult afterDone = job.step();
        assertTrue(afterDone.placedBlocks().isEmpty());
        assertFalse(afterDone.completedNow());
        assertTrue(afterDone.spawnMarkersToTrigger().isEmpty());
    }

    /**
     * Configurable pure-Java {@link TerrainProbe} test double supporting flat/sloped terrain,
     * protected blocks/BlockEntities, shorelines, and sky-clearance toggles.
     */
    private static final class FakeTerrain implements TerrainProbe {
        @FunctionalInterface
        interface HeightFn {
            int surfaceY(int x, int z);
        }

        @FunctionalInterface
        interface WaterFn {
            boolean isWater(int x, int z);
        }

        private HeightFn heightFn;
        private WaterFn waterFn = (x, z) -> false;
        private int waterSurfaceY = 63;
        private int seabedY = 59;
        private boolean skyClear = true;
        private final Map<BlockPos3i, Boolean> protectedCoords = new HashMap<>();

        FakeTerrain(int flatY) {
            this.heightFn = (x, z) -> flatY;
        }

        void setHeightFunction(HeightFn fn) {
            this.heightFn = fn;
        }

        void setWaterPredicate(WaterFn fn) {
            this.waterFn = fn;
        }

        void setWaterSurfaceY(int y) {
            this.waterSurfaceY = y;
        }

        void setSeabedY(int y) {
            this.seabedY = y;
        }

        void setSkyClear(boolean skyClear) {
            this.skyClear = skyClear;
        }

        void markProtected(BlockPos3i pos) {
            protectedCoords.put(pos, Boolean.TRUE);
        }

        @Override
        public int getSurfaceY(int x, int z) {
            return waterFn.isWater(x, z) ? waterSurfaceY : heightFn.surfaceY(x, z);
        }

        @Override
        public boolean isNaturalReplaceable(int x, int y, int z) {
            if (protectedCoords.containsKey(new BlockPos3i(x, y, z))) {
                return false;
            }
            if (waterFn.isWater(x, z)) {
                return y > waterSurfaceY;
            }
            return y > heightFn.surfaceY(x, z);
        }

        @Override
        public boolean isSolidFoundation(int x, int y, int z) {
            if (waterFn.isWater(x, z)) {
                return y <= seabedY;
            }
            return y <= heightFn.surfaceY(x, z);
        }

        @Override
        public boolean isProtectedOrOccupied(int x, int y, int z) {
            if (protectedCoords.containsKey(new BlockPos3i(x, y, z))) {
                return true;
            }
            // Simulate realistic probe: fluid itself reports as protected/occupied!
            return waterFn.isWater(x, z) && y <= waterSurfaceY && y > seabedY;
        }

        @Override
        public boolean isWaterSurface(int x, int z) {
            return waterFn.isWater(x, z);
        }

        @Override
        public boolean isSkyClear(int x, int fromY, int z) {
            return skyClear && TerrainProbe.super.isSkyClear(x, fromY, z);
        }
    }
}
