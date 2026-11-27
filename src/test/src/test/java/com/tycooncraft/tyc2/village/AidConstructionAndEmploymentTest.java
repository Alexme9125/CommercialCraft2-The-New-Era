package com.tycooncraft.tyc2.village;

import com.tycooncraft.tyc2.api.TyC2RegistryApi;
import com.tycooncraft.tyc2.core.DemandGenerator;
import com.tycooncraft.tyc2.core.GoodsCatalog;
import com.tycooncraft.tyc2.core.GoodsCategory;
import com.tycooncraft.tyc2.core.IndustrialTier;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TradeGood;
import com.tycooncraft.tyc2.core.TyC2Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for TyC2's Aid Construction & Low-Unemployment Bonus systems:
 * <ul>
 *   <li>All 13 vanilla + modded generic workplace blueprints across SMALL (7x6x7, 1 workstation)
 *       and LARGE (9x7x9, 3 workstations) in all 7 biomes and 4 rotations with 0 beds</li>
 *   <li>3-layer {@link WorkplaceCatalog} extensibility (datapack/config JSON, {@link TyC2RegistryApi},
 *       dynamic modded professions) and CPI pricing</li>
 *   <li>{@link AidConstructionProject} virtual gathering acceleration, material progress, and
 *       cancel/refund money-supply conservation</li>
 *   <li>{@link ExpansionPlanner} pause-independence, pre-reserved zone lifecycle, priority queue,
 *       and auto-expansion exclusion</li>
 *   <li>{@link VillageEmploymentStats} accounting rules (Mayor employed, Nitwit/Baby excluded,
 *       0 workforce edge case, &lt;25% smooth bonus curve)</li>
 *   <li>{@link DemandGenerator} low-unemployment quantity scaling and gentle profession weighting</li>
 * </ul>
 */
class AidConstructionAndEmploymentTest {

    @AfterEach
    void resetRegistryApi() {
        TyC2RegistryApi.reset();
    }

    @Test
    void all13VanillaAndModdedWorkplacesBuildCleanlyAcross7Biomes2SizesAnd4Rotations() {
        WorkplaceCatalog catalog = WorkplaceCatalog.createDefault();
        assertEquals(13, catalog.allConfiguredWorkplaces().size());

        List<WorkplaceSpec> allSpecs = new ArrayList<>(catalog.allConfiguredWorkplaces());
        allSpecs.add(new WorkplaceSpec(
                "create:mechanical_engineer",
                "create:mechanical_press",
                WorkplaceArchetype.GENERIC,
                450L,
                2.2,
                1,
                3,
                List.of(GoodsCategory.INDUSTRIAL),
                true,
                true,
                "Mechanical Engineer"));

        for (WorkplaceSpec spec : allSpecs) {
            for (WorkplaceSize size : WorkplaceSize.values()) {
                for (BiomeStyle style : BiomeStyle.values()) {
                    StructureBlueprint base = WorkplaceArchitectureCatalog.getBlueprint(spec, size, style);
                    assertEquals(size.projectType(), base.projectType());
                    assertEquals(size.width(), base.width(), "Width mismatch for " + spec.professionId() + "/" + size);
                    assertEquals(size.height(), base.height(), "Height mismatch for " + spec.professionId() + "/" + size);
                    assertEquals(size.depth(), base.depth(), "Depth mismatch for " + spec.professionId() + "/" + size);

                    // Zero villager markers, exact workstation marker count
                    assertTrue(base.spawnMarkers().stream()
                            .noneMatch(m -> m.kind() == SpawnMarker.MarkerKind.VILLAGER));
                    List<SpawnMarker> wsMarkers = base.spawnMarkers().stream()
                            .filter(m -> m.kind() == SpawnMarker.MarkerKind.WORKSTATION)
                            .toList();
                    assertEquals(size.workstationCount(), wsMarkers.size(),
                            "Workstation marker count mismatch for " + spec.professionId() + "/" + size);
                    for (SpawnMarker m : wsMarkers) {
                        assertEquals(spec.professionId(), m.hint());
                    }

                    // Validate all 4 rotations
                    for (Facing2D facing : Facing2D.values()) {
                        StructureBlueprint rot = base.rotated(facing);
                        assertEquals(facing, rot.facing());
                        assertEquals(base.placements().size(), rot.placements().size());

                        Map<BlockPos3i, String> byPos = new HashMap<>();
                        int prevDy = Integer.MIN_VALUE;
                        int doorLowers = 0;
                        int doorUppers = 0;

                        for (BlockPlacement bp : rot.placements()) {
                            assertTrue(bp.dx() >= 0 && bp.dx() < rot.width());
                            assertTrue(bp.dy() >= 0 && bp.dy() < rot.height());
                            assertTrue(bp.dz() >= 0 && bp.dz() < rot.depth());
                            assertTrue(bp.dy() >= prevDy, "Must sort bottom-up");
                            prevDy = bp.dy();

                            BlockPos3i coord = new BlockPos3i(bp.dx(), bp.dy(), bp.dz());
                            assertNull(byPos.put(coord, bp.blockStateSpec()),
                                    "Duplicate coord " + coord + " in " + spec.professionId());

                            String blockSpec = bp.blockStateSpec();
                            // Strict 0 beds guarantee
                            assertFalse(blockSpec.contains("_bed"), "Workplace must NEVER place beds: " + blockSpec);
                            assertFalse(blockSpec.contains("part=foot"), "Workplace must NEVER place beds");
                            assertFalse(blockSpec.contains("part=head"), "Workplace must NEVER place beds");
                            // Cross-version 1.19.2 - 26.2 block ID safety
                            assertFalse(blockSpec.startsWith("minecraft:cut_sandstone[axis="));
                            assertFalse(blockSpec.startsWith("minecraft:chain"));
                            assertFalse(blockSpec.contains("cherry_"));
                            assertFalse(blockSpec.contains("bamboo_planks"));
                            assertFalse(blockSpec.contains("chiseled_bookshelf"));

                            if (blockSpec.contains("half=lower")) {
                                doorLowers++;
                            } else if (blockSpec.contains("half=upper")) {
                                doorUppers++;
                            }
                        }

                        assertTrue(doorLowers >= 1, "Workplace must have at least one door");
                        assertEquals(doorLowers, doorUppers, "Door halves must match");

                        // Verify every rotated WORKSTATION marker matches exact workstationBlockId, floor below, air above
                        List<SpawnMarker> rotMarkers = rot.spawnMarkers().stream()
                                .filter(m -> m.kind() == SpawnMarker.MarkerKind.WORKSTATION)
                                .toList();
                        assertEquals(size.workstationCount(), rotMarkers.size());
                        for (SpawnMarker m : rotMarkers) {
                            BlockPos3i pos = new BlockPos3i(m.dx(), m.dy(), m.dz());
                            assertEquals(spec.workstationBlockId(), byPos.get(pos),
                                    "Workstation block mismatch at " + pos + " for " + spec.professionId());
                            assertNotNull(byPos.get(pos.below()),
                                    "Workstation must sit on solid floor at " + pos);
                            assertNull(byPos.get(pos.above()),
                                    "Workstation must have open air above at " + pos);
                        }
                    }
                }
            }
        }
    }

    @Test
    void workplaceCatalogExtensibilityModdedDiscoveryAndCpiPricing(@TempDir Path tempDir) throws Exception {
        WorkplaceCatalog catalog = WorkplaceCatalog.createDefault();
        TyC2Config cfg = TyC2Config.defaults();

        // Exact Section 2.2 vanilla prices
        assertEquals(250L, catalog.getByProfessionId("minecraft:farmer").orElseThrow().baseCostSmallIc());
        assertEquals(260L, catalog.getByProfessionId("minecraft:fisherman").orElseThrow().baseCostSmallIc());
        assertEquals(600L, catalog.getByProfessionId("minecraft:librarian").orElseThrow().baseCostSmallIc());

        // CPI pricing with guardrail clamping (M1: aid CPI floor >= 0.75 + repeat surcharge + profession cap)
        WorkplaceSpec farmer = catalog.getByProfessionId("minecraft:farmer").orElseThrow();
        assertEquals(250L, WorkplaceCatalog.quoteCostIc(farmer, WorkplaceSize.SMALL, 1.0, cfg));
        assertEquals(550L, WorkplaceCatalog.quoteCostIc(farmer, WorkplaceSize.LARGE, 1.0, cfg));
        assertEquals(1_000L, WorkplaceCatalog.quoteCostIc(farmer, WorkplaceSize.SMALL, 99.0, cfg)); // clamped to 4.0x
        assertEquals(188L, WorkplaceCatalog.quoteCostIc(farmer, WorkplaceSize.SMALL, 0.01, cfg));   // clamped to 0.75x floor
        assertEquals(438L, WorkplaceCatalog.quoteCostIc(farmer, WorkplaceSize.SMALL, 1.0, cfg, 1)); // +75% repeat surcharge

        VillageRecord capVillage = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "CapTown",
                new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS);
        assertTrue(capVillage.canStartAidForProfession("minecraft:farmer"));
        capVillage.incrementCompletedAidCount("minecraft:farmer");
        assertEquals(1, capVillage.getTotalAidCountForProfession("minecraft:farmer"));
        assertTrue(capVillage.canStartAidForProfession("minecraft:farmer"));
        capVillage.incrementCompletedAidCount("minecraft:farmer");
        assertEquals(WorkplaceCatalog.MAX_AID_BUILDS_PER_PROFESSION, capVillage.getTotalAidCountForProfession("minecraft:farmer"));
        assertFalse(capVillage.canStartAidForProfession("minecraft:farmer"));

        // Required materials include exact workstation count (1 small, 3 large)
        Map<String, Integer> farmerSmallMats =
                WorkplaceCatalog.requiredMaterials(farmer, WorkplaceSize.SMALL, BiomeStyle.JUNGLE);
        Map<String, Integer> farmerLargeMats =
                WorkplaceCatalog.requiredMaterials(farmer, WorkplaceSize.LARGE, BiomeStyle.SWAMP);
        assertEquals(1, farmerSmallMats.get("minecraft:composter"));
        assertTrue(farmerSmallMats.containsKey("minecraft:jungle_log"));
        assertEquals(3, farmerLargeMats.get("minecraft:composter"));
        assertTrue(farmerLargeMats.containsKey("minecraft:dark_oak_log"));

        // External config template creation & JSON merge
        Path customPath = tempDir.resolve("custom_workplaces.json");
        assertEquals(0, catalog.loadExternalConfig(customPath, true));
        assertTrue(Files.exists(customPath));

        String json = """
                {
                  "workplaces": [
                    {
                      "professionId": "ie:engineer",
                      "workstationBlockId": "immersiveengineering:workbench",
                      "archetype": "TOOLSMITH",
                      "baseCostSmallIc": 500,
                      "largeCostMultiplier": 2.4,
                      "minVillageLevelSmall": 2,
                      "minVillageLevelLarge": 4,
                      "tradeCategories": ["INDUSTRIAL", "METAL"],
                      "enabled": true
                    },
                    {
                      "professionId": "minecraft:cleric",
                      "enabled": false
                    }
                  ]
                }
                """;
        assertEquals(2, catalog.mergeFromJson(json));
        assertEquals(500L, catalog.getByProfessionId("ie:engineer").orElseThrow().baseCostSmallIc());
        assertFalse(catalog.getByProfessionId("minecraft:cleric").orElseThrow().enabled());

        // Registry API registration & cleanup
        WorkplaceSpec apiSpec = new WorkplaceSpec(
                "morevillagers:florist",
                "morevillagers:gardening_table",
                WorkplaceArchetype.FARMER,
                300L, 2.0, 1, 3, List.of(GoodsCategory.AGRICULTURE), true, false, "Florist");
        TyC2RegistryApi.registerWorkplace(apiSpec);
        assertEquals(1, TyC2RegistryApi.getRegisteredWorkplaces().size());

        // Dynamic modded profession discovery respects toggle, blacklist, and never overwrites explicit entries
        cfg.setModdedProfessionBlacklist(List.of("modx:guard", "bannedmod:*"));
        assertFalse(catalog.registerModdedProfession("minecraft:farmer", "minecraft:dirt", "Fake", cfg));
        assertFalse(catalog.registerModdedProfession("ie:engineer", "minecraft:dirt", "Fake", cfg));
        assertFalse(catalog.registerModdedProfession("morevillagers:florist", "minecraft:dirt", "Fake", cfg));
        assertFalse(catalog.registerModdedProfession("minecraft:none", "minecraft:stone", "None", cfg));
        assertFalse(catalog.registerModdedProfession("minecraft:nitwit", "minecraft:stone", "Nitwit", cfg));
        assertFalse(catalog.registerModdedProfession("modx:guard", "modx:post", "Guard", cfg));
        assertFalse(catalog.registerModdedProfession("bannedmod:baker", "bannedmod:oven", "Baker", cfg));
        assertTrue(catalog.registerModdedProfession("create:engineer", "create:depot", "Engineer", cfg));

        WorkplaceSpec dyn = catalog.getByProfessionId("create:engineer").orElseThrow();
        assertTrue(dyn.moddedDynamic());
        assertEquals(WorkplaceArchetype.GENERIC, dyn.archetype());
        assertEquals(450L, dyn.baseCostSmallIc());
        assertEquals(990L, WorkplaceCatalog.quoteCostIc(dyn, WorkplaceSize.LARGE, 1.0, cfg));

        // Disabled toggle blocks new dynamic discovery
        cfg.setEnableModdedProfessions(false);
        assertFalse(catalog.registerModdedProfession("other:smith", "other:bench", "Smith", cfg));

        // getAvailableWorkplaces filters disabled (cleric) and missing blocks (immersiveengineering:workbench)
        List<WorkplaceSpec> avail = catalog.getAvailableWorkplaces(
                blockId -> !blockId.equals("immersiveengineering:workbench"));
        assertTrue(avail.stream().noneMatch(s -> s.professionId().equals("minecraft:cleric")));
        assertTrue(avail.stream().noneMatch(s -> s.professionId().equals("ie:engineer")));
        assertTrue(avail.stream().anyMatch(s -> s.professionId().equals("morevillagers:florist")));
        assertTrue(avail.stream().anyMatch(s -> s.professionId().equals("create:engineer")));

        assertTrue(TyC2RegistryApi.unregisterWorkplace("morevillagers:florist"));
        assertTrue(TyC2RegistryApi.getRegisteredWorkplaces().isEmpty());
    }

    @Test
    void aidProjectGatheringAccelerationAndCancelRefundMoneyConservation() {
        TyC2Config cfg = TyC2Config.defaults();
        WorkplaceSpec librarian = WorkplaceCatalog.createDefault()
                .getByProfessionId("minecraft:librarian").orElseThrow();
        UUID playerUuid = UUID.randomUUID();

        AidConstructionProject aid = AidConstructionProject.create(
                "aid-1", librarian, WorkplaceSize.SMALL, BiomeStyle.PLAINS,
                600L, cfg.aidGatherTicksSmall(), null, playerUuid, null, true);

        assertEquals(AidConstructionProject.Status.COLLECTING, aid.status());
        assertEquals(0.0, aid.completionRatio(), 1e-9);
        assertTrue(aid.canCancel());
        assertEquals(0, aid.displayedGatheredMaterials().get("minecraft:lectern"));

        // Labor speed multiplier: 0 -> 1.0x, 4 -> 1.5x, 8 -> 2.0x, 20 -> capped at 2.0x
        assertEquals(1.0, new VillageEmploymentStats(10, 10, 0, true, Map.of(), 0L).gatherSpeedMultiplier(cfg), 1e-9);
        assertEquals(1.5, new VillageEmploymentStats(10, 6, 4, true, Map.of(), 0L).gatherSpeedMultiplier(cfg), 1e-9);
        assertEquals(2.0, new VillageEmploymentStats(10, 2, 8, true, Map.of(), 0L).gatherSpeedMultiplier(cfg), 1e-9);
        assertEquals(2.0, new VillageEmploymentStats(25, 5, 20, true, Map.of(), 0L).gatherSpeedMultiplier(cfg), 1e-9);

        // Advance at 2.0x speed for 900 real ticks -> 1800 progress ticks = 50% of 3600
        AidConstructionProject half = aid.advanceGathering(900 * 2.0);
        assertEquals(AidConstructionProject.Status.COLLECTING, half.status());
        assertEquals(0.5, half.completionRatio(), 1e-9);
        assertEquals(900, half.remainingTicksEstimate(2.0));
        assertTrue(half.displayedGatheredMaterials().get("minecraft:oak_planks") > 0);

        // Advance remaining 900 real ticks at 2.0x -> 100%, auto-transitions to WAITING_SITE
        AidConstructionProject ready = half.advanceGathering(900 * 2.0);
        assertEquals(AidConstructionProject.Status.WAITING_SITE, ready.status());
        assertEquals(1.0, ready.completionRatio(), 1e-9);
        assertEquals(1, ready.displayedGatheredMaterials().get("minecraft:lectern"));
        assertTrue(ready.canCancel());

        // NO_SITE stays cancellable; BUILDING forbids cancel
        AidConstructionProject noSite = ready.withStatus(AidConstructionProject.Status.NO_SITE);
        assertTrue(noSite.canCancel());
        AidConstructionProject building = noSite.withBuilding("aid-job-1");
        assertEquals(AidConstructionProject.Status.BUILDING, building.status());
        assertFalse(building.canCancel());
        assertEquals("aid-job-1", building.plannedJobId());

        // MacroEconomyEngine money conservation on cash sink + cancel refund
        MacroEconomyEngine econ = new MacroEconomyEngine(cfg);
        econ.recordMinting(5_000L);
        long initialM = econ.totalMoneySupply();
        econ.recordMoneySink(600L);
        assertEquals(initialM - 600L, econ.totalMoneySupply());
        assertEquals(600L, econ.cumulativeSinks());

        econ.refundMoneySink(600L);
        assertEquals(initialM, econ.totalMoneySupply());
        assertEquals(0L, econ.cumulativeSinks());

        // Over-refund cannot drop cumulativeSinks below 0 or create free money
        econ.refundMoneySink(999L);
        assertEquals(initialM, econ.totalMoneySupply());
        assertEquals(0L, econ.cumulativeSinks());

        // Bank-funded aid sink + refund keeps bank total and cash total exact
        econ.syncAccountTotals(2_000L, 1_000L, 1);
        long mBeforeBankAid = econ.totalMoneySupply();
        econ.recordBankMoneySink(800L);
        assertEquals(1_200L, econ.playerAndGuildBankTotal());
        assertEquals(mBeforeBankAid - 800L, econ.totalMoneySupply());
        econ.refundMoneySink(800L, false);
        assertEquals(2_000L, econ.playerAndGuildBankTotal());
        assertEquals(mBeforeBankAid, econ.totalMoneySupply());
        assertEquals(0L, econ.cumulativeSinks());
    }

    @Test
    void plannerPreReservedZonePauseIndependencePriorityQueueAndAutoExclusion() {
        ExpansionPlanner planner = new ExpansionPlanner();
        FlatTerrain terrain = new FlatTerrain(64);
        BlockPos3i hub = new BlockPos3i(0, 64, 0);

        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Aidville", hub, BiomeStyle.JUNGLE);
        village.setLevel(5);
        village.setExpansionRadiusBounds(12, 48);
        assertEquals(60, village.effectiveVillagerScanRadius());
        village.setExpansionRadiusBounds(10, 20);
        assertEquals(48, village.effectiveVillagerScanRadius());
        village.setExpansionRadiusBounds(12, 48);

        // 1. selectNextProjectType NEVER picks AID_WORKPLACE_*
        for (int lv = 1; lv <= 10; lv++) {
            village.setLevel(lv);
            for (int occ = 0; occ < 15; occ++) {
                assertFalse(planner.selectNextProjectType(village, true).isAidWorkplace());
                assertFalse(planner.selectNextProjectType(village, false).isAidWorkplace());
            }
        }
        village.setLevel(5);

        // 2. Pausing regular expansion blocks regular plan() but ALLOWS aid planBlueprint(..., true)
        village.setExpansionPaused(true);
        assertFalse(planner.plan(village, ProjectType.SMALL_COTTAGE, terrain, "reg-paused").success());

        WorkplaceSpec mason = WorkplaceCatalog.createDefault()
                .getByProfessionId("minecraft:mason").orElseThrow();
        StructureBlueprint masonBp = WorkplaceArchitectureCatalog.getBlueprint(
                mason, WorkplaceSize.LARGE, village.getBiomeStyle());

        ExpansionPlanner.PlanResult aidPreview = planner.planBlueprint(village, masonBp, terrain, "aid-prev", true);
        assertTrue(aidPreview.success(), aidPreview.failureReason());
        Aabb3i preReserved = aidPreview.reservedZone();
        village.addOccupiedZone(preReserved);

        AidConstructionProject aidProj = AidConstructionProject.create(
                "aid-mason", mason, WorkplaceSize.LARGE, village.getBiomeStyle(),
                792L, 6000, preReserved, UUID.randomUUID(), null, false);
        assertTrue(village.addAidProject(aidProj, 3));

        // 3. Regular expansion resumed while aid is collecting NEVER collides with aid's pre-reserved zone
        village.setExpansionPaused(false);
        ExpansionPlanner.PlanResult reg1 = planner.plan(village, ProjectType.SMALL_COTTAGE, terrain, "reg-1");
        assertTrue(reg1.success(), reg1.failureReason());
        assertFalse(preReserved.padHorizontal(ExpansionPlanner.CLEARANCE_PADDING)
                .intersectsHorizontal(reg1.reservedZone()));
        assertTrue(planner.commitPlan(village, reg1, 1_000L));

        ExpansionPlanner.PlanResult reg2 = planner.plan(village, ProjectType.CROP_FARM, terrain, "reg-2");
        assertTrue(reg2.success(), reg2.failureReason());
        assertTrue(planner.commitPlan(village, reg2, 2_000L));

        // Simulate reg-1 actively placing blocks (placedCount > 0) while reg-2 waits at index 1
        village.getConstructionQueue().get(0).step();
        assertTrue(village.getConstructionQueue().get(0).getPlacedCount() > 0);

        // 4. Finalizing aid onto its OWN pre-reserved footprint succeeds cleanly & inserts at index 1 ahead of reg-2!
        ExpansionPlanner.PlanResult aidFinal = planner.planBlueprintIgnoringZone(
                village, masonBp, terrain, "aid-job-mason", true, preReserved);
        assertTrue(aidFinal.success(), aidFinal.failureReason());
        assertEquals(preReserved.minX(), aidFinal.reservedZone().minX());
        assertEquals(preReserved.minZ(), aidFinal.reservedZone().minZ());

        int zonesBefore = village.getOccupiedZones().size();
        assertTrue(planner.commitPlanWithPriority(village, aidFinal, 3_000L, true));
        // Must not duplicate identical pre-reserved zone
        assertEquals(zonesBefore, village.getOccupiedZones().size());
        // Must not overwrite lastExpansionTick
        assertEquals(2_000L, village.getLastExpansionTick());
        // Queue order: [reg-1 (active), aid-job-mason (priority!), reg-2 (queued regular)]
        assertEquals("reg-1", village.getConstructionQueue().get(0).getJobId());
        assertEquals("aid-job-mason", village.getConstructionQueue().get(1).getJobId());
        assertEquals("reg-2", village.getConstructionQueue().get(2).getJobId());

        // 5. If a pre-reserved site gets blocked by a player machine while collecting, planner safely relocates
        WorkplaceSpec cleric = WorkplaceCatalog.createDefault()
                .getByProfessionId("minecraft:cleric").orElseThrow();
        StructureBlueprint clericBp = WorkplaceArchitectureCatalog.getBlueprint(
                cleric, WorkplaceSize.SMALL, village.getBiomeStyle());
        ExpansionPlanner.PlanResult clericPreview = planner.planBlueprint(village, clericBp, terrain, "prev-c", true);
        assertTrue(clericPreview.success());
        Aabb3i clericReserved = clericPreview.reservedZone();
        village.addOccupiedZone(clericReserved);

        // Player places a protected machine right in the center of clericReserved
        terrain.markProtected(new BlockPos3i(clericReserved.center().x(), 65, clericReserved.center().z()));
        ExpansionPlanner.PlanResult clericRelocated = planner.planBlueprintIgnoringZone(
                village, clericBp, terrain, "aid-cleric", true, clericReserved);
        assertTrue(clericRelocated.success(), clericRelocated.failureReason());
        assertNotEquals(clericReserved.minCorner(), clericRelocated.reservedZone().minCorner());

        // Releasing cancelled pre-reserved zone removes it cleanly
        assertTrue(village.removeOccupiedZone(clericReserved));
        assertFalse(village.getOccupiedZones().contains(clericReserved));

        // 6. When head of constructionQueue has placedCount == 0, highPriority inserts at index 0
        VillageRecord fresh = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Fresh", hub, BiomeStyle.SWAMP);
        ExpansionPlanner.PlanResult unstartedReg = planner.plan(fresh, ProjectType.ROAD_AND_LAMPS, terrain, "reg-idle");
        assertTrue(planner.commitPlan(fresh, unstartedReg, 100L));
        ExpansionPlanner.PlanResult urgentAid = planner.planBlueprint(fresh, clericBp, terrain, "aid-urgent", true);
        assertTrue(planner.commitPlanWithPriority(fresh, urgentAid, 200L, true));
        assertEquals("aid-urgent", fresh.getConstructionQueue().get(0).getJobId());
        assertEquals("reg-idle", fresh.getConstructionQueue().get(1).getJobId());

        // 7. Aid project queue cap enforcement (default 3)
        assertTrue(fresh.addAidProject(aidProj, 3));
        assertTrue(fresh.addAidProject(aidProj, 3));
        assertTrue(fresh.addAidProject(aidProj, 3));
        assertFalse(fresh.addAidProject(aidProj, 3));
    }

    @Test
    void employmentStatsAccountingAndSmoothLowUnemploymentCurve() {
        TyC2Config cfg = TyC2Config.defaults();

        // 1. Zero workforce (only babies + nitwits) -> 100% unemployment, bonus inactive
        List<VillageEmploymentStats.VillagerSnapshot> onlyExcluded = List.of(
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), false, false, "minecraft:none"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, true, "minecraft:nitwit"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, false, "minecraft:nitwit"));
        VillageEmploymentStats emptyStats = VillageEmploymentStats.computeFromSnapshots(onlyExcluded, null, 100L);
        assertEquals(0, emptyStats.workforceCount());
        assertEquals(0, emptyStats.employedCount());
        assertEquals(0, emptyStats.unemployedCount());
        assertEquals(1.0, emptyStats.unemploymentRate(), 1e-9);
        assertFalse(emptyStats.isLowUnemploymentBonusActive(cfg));
        assertEquals(0.0, emptyStats.bonusIntensity(cfg), 1e-9);
        assertEquals(1.0, emptyStats.restockSpeedMultiplier(cfg), 1e-9);
        assertEquals(168_000, emptyStats.effectiveDemandRefreshTicks(cfg));
        assertEquals(1.0, emptyStats.tradeQuantityMultiplier(cfg), 1e-9);
        assertEquals(1.0, emptyStats.rareTradeQuantityMultiplier(cfg), 1e-9);

        // 2. Mayor counted as employed even with minecraft:none; babies & nitwits excluded
        UUID mayorId = UUID.randomUUID();
        List<VillageEmploymentStats.VillagerSnapshot> mixed = List.of(
                new VillageEmploymentStats.VillagerSnapshot(mayorId, true, false, "minecraft:none"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, false, "minecraft:farmer"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, false, "minecraft:farmer"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, false, "minecraft:librarian"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, false, "minecraft:none"),
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), false, false, "minecraft:none"), // baby
                new VillageEmploymentStats.VillagerSnapshot(UUID.randomUUID(), true, true, "minecraft:nitwit")); // nitwit

        VillageEmploymentStats stats = VillageEmploymentStats.computeFromSnapshots(mixed, mayorId, 500L);
        assertEquals(5, stats.workforceCount());
        assertEquals(4, stats.employedCount());
        assertEquals(1, stats.unemployedCount());
        assertTrue(stats.mayorCountedEmployed());
        assertEquals(2, stats.activeCountForProfession("minecraft:farmer"));
        assertEquals(1, stats.activeCountForProfession("minecraft:librarian"));
        assertEquals(0, stats.activeCountForProfession("minecraft:none"));

        // 1/5 = 20% < 25% -> active! intensity = (0.25 - 0.20) / 0.25 = 0.20
        // Task 2.3: restock cadence stays fixed at 1.0x / 168,000 ticks; qty boost remains active!
        assertEquals(0.20, stats.unemploymentRate(), 1e-9);
        assertTrue(stats.isLowUnemploymentBonusActive(cfg));
        assertEquals(0.20, stats.bonusIntensity(cfg), 1e-9);
        assertEquals(1.0, stats.restockSpeedMultiplier(cfg), 1e-9);
        assertEquals(1.30, stats.tradeQuantityMultiplier(cfg), 1e-9);
        assertEquals(1.12, stats.rareTradeQuantityMultiplier(cfg), 1e-9);
        assertEquals(168_000, stats.effectiveDemandRefreshTicks(cfg));

        // 3. Exact 25% boundary (1/4) -> NOT active
        VillageEmploymentStats atBoundary = new VillageEmploymentStats(4, 3, 1, true, Map.of(), 600L);
        assertEquals(0.25, atBoundary.unemploymentRate(), 1e-9);
        assertFalse(atBoundary.isLowUnemploymentBonusActive(cfg));
        assertEquals(1.0, atBoundary.restockSpeedMultiplier(cfg), 1e-9);
        assertEquals(1.0, atBoundary.tradeQuantityMultiplier(cfg), 1e-9);

        // 4. 0% unemployment -> maximum qty bonus (1.50x qty, 1.20x rare), fixed weekly 168,000 refresh ticks
        VillageEmploymentStats fullEmp = new VillageEmploymentStats(8, 8, 0, true, Map.of(), 700L);
        assertEquals(0.0, fullEmp.unemploymentRate(), 1e-9);
        assertTrue(fullEmp.isLowUnemploymentBonusActive(cfg));
        assertEquals(1.0, fullEmp.bonusIntensity(cfg), 1e-9);
        assertEquals(1.0, fullEmp.restockSpeedMultiplier(cfg), 1e-9);
        assertEquals(1.50, fullEmp.tradeQuantityMultiplier(cfg), 1e-9);
        assertEquals(1.20, fullEmp.rareTradeQuantityMultiplier(cfg), 1e-9);
        assertEquals(168_000, fullEmp.effectiveDemandRefreshTicks(cfg));
    }

    @Test
    void demandGeneratorAppliesEmploymentBonusQuantitiesProfessionWeightingAndJungleSwampAffinities() {
        GoodsCatalog goods = GoodsCatalog.createDefault();
        WorkplaceCatalog workplaces = WorkplaceCatalog.createDefault();
        MacroEconomyEngine econ = new MacroEconomyEngine();
        DemandGenerator gen = new DemandGenerator(goods, TyC2Config.defaults());

        // Verify jungle & swamp biome specialty affinities in GoodsCatalog and TradeGood.normalizeBiomeStyle
        TradeGood cocoa = goods.getById("tyc2:cocoa_beans").orElseThrow();
        TradeGood bamboo = goods.getById("tyc2:bamboo").orElseThrow();
        TradeGood melon = goods.getById("tyc2:melon_slice").orElseThrow();
        TradeGood slime = goods.getById("tyc2:slime_ball").orElseThrow();
        TradeGood brownMush = goods.getById("tyc2:brown_mushroom").orElseThrow();
        TradeGood redMush = goods.getById("tyc2:red_mushroom").orElseThrow();

        assertTrue(cocoa.hasBiomeAffinity("minecraft:jungle"));
        assertTrue(bamboo.hasBiomeAffinity("minecraft:bamboo_jungle"));
        assertTrue(melon.hasBiomeAffinity("biomesoplenty:rainforest"));
        assertTrue(slime.hasBiomeAffinity("minecraft:mangrove_swamp"));
        assertTrue(brownMush.hasBiomeAffinity("minecraft:swamp"));
        assertTrue(redMush.hasBiomeAffinity("biomesoplenty:bayou"));
        assertFalse(slime.hasBiomeAffinity("minecraft:snowy_plains"));

        // 1. 0% unemployment scales Sell & Buy order quantities up (~1.50x) vs inactive baseline with identical seed
        VillageEmploymentStats highUnemp = new VillageEmploymentStats(8, 4, 4, true, Map.of(), 0L);
        VillageEmploymentStats fullEmp = new VillageEmploymentStats(8, 8, 0, true, Map.of(), 0L);

        DemandGenerator.GeneratedDemand baseDemand = gen.generate(
                5, "minecraft:jungle", 500_000L, IndustrialTier.TIER_3, Season.SUMMER,
                econ, id -> id.startsWith("minecraft:"), highUnemp, workplaces, new Random(42L));
        DemandGenerator.GeneratedDemand boostedDemand = gen.generate(
                5, "minecraft:jungle", 500_000L, IndustrialTier.TIER_3, Season.SUMMER,
                econ, id -> id.startsWith("minecraft:"), fullEmp, workplaces, new Random(42L));

        assertEquals(baseDemand.sellOrders().size(), boostedDemand.sellOrders().size());
        assertEquals(baseDemand.buyOrders().size(), boostedDemand.buyOrders().size());

        int baseSellTotal = baseDemand.sellOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        int boostedSellTotal = boostedDemand.sellOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        int baseBuyTotal = baseDemand.buyOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();
        int boostedBuyTotal = boostedDemand.buyOrders().stream().mapToInt(DemandGenerator.GeneratedOrder::quantity).sum();

        assertTrue(boostedSellTotal > baseSellTotal * 1.35,
                "Full employment must substantially boost Sell order quantities: " + boostedSellTotal + " vs " + baseSellTotal);
        assertTrue(boostedBuyTotal > baseBuyTotal * 1.35,
                "Full employment must substantially boost Buy order quantities: " + boostedBuyTotal + " vs " + baseBuyTotal);

        // 2. Profession light weighting increases matching category frequency without breaking Lv/Tier locks
        VillageEmploymentStats manyLibrarians = new VillageEmploymentStats(
                6, 6, 0, true, Map.of("minecraft:librarian", 5), 0L);

        // Lv 1 village with 5 librarians MUST STILL NEVER sell Lv 4+ enchanted books!
        for (int seed = 0; seed < 25; seed++) {
            DemandGenerator.GeneratedDemand lv1 = gen.generate(
                    1, "minecraft:plains", 10_000L, IndustrialTier.TIER_5, Season.NONE,
                    econ, id -> true, manyLibrarians, workplaces, new Random(seed));
            for (DemandGenerator.GeneratedOrder o : lv1.allOrders()) {
                assertNotEquals(GoodsCategory.ENCHANTED_BOOK, o.category(),
                        "Profession weighting must NEVER bypass village level lock!");
            }
        }

        // Lv 10 village with Tier 4 player and 5 clerics MUST STILL NEVER bypass industrial tier cap into Tier 4/5!
        VillageEmploymentStats manyClerics = new VillageEmploymentStats(
                6, 6, 0, true, Map.of("minecraft:cleric", 5), 0L);
        for (int seed = 0; seed < 25; seed++) {
            DemandGenerator.GeneratedDemand capped = gen.generate(
                    10, "minecraft:swamp", 500_000L, IndustrialTier.TIER_4, Season.NONE,
                    econ, id -> true, manyClerics, workplaces, new Random(200 + seed));
            for (DemandGenerator.GeneratedOrder o : capped.allOrders()) {
                TradeGood g = goods.getById(o.goodId()).orElseThrow();
                assertTrue(g.industrialTierRequired().level() <= 3,
                        "Profession weighting must NEVER bypass industrial tier cap!");
            }
        }

        // At Lv 8 + Tier 4, having active librarians measurably boosts enchanted book selection frequency
        int booksWithoutLibrarians = 0;
        int booksWithLibrarians = 0;
        for (int seed = 0; seed < 80; seed++) {
            DemandGenerator.GeneratedDemand d0 = gen.generate(
                    8, "minecraft:plains", 500_000L, IndustrialTier.TIER_4, Season.NONE,
                    econ, id -> id.startsWith("minecraft:"), fullEmp, workplaces, new Random(1000 + seed));
            DemandGenerator.GeneratedDemand d1 = gen.generate(
                    8, "minecraft:plains", 500_000L, IndustrialTier.TIER_4, Season.NONE,
                    econ, id -> id.startsWith("minecraft:"), manyLibrarians, workplaces, new Random(1000 + seed));
            booksWithoutLibrarians += (int) d0.sellOrders().stream()
                    .filter(o -> o.category() == GoodsCategory.ENCHANTED_BOOK).count();
            booksWithLibrarians += (int) d1.sellOrders().stream()
                    .filter(o -> o.category() == GoodsCategory.ENCHANTED_BOOK).count();
        }
        assertTrue(booksWithLibrarians > booksWithoutLibrarians,
                "Active librarians should gently increase enchanted book weight: "
                        + booksWithLibrarians + " vs " + booksWithoutLibrarians);
    }

    private static final class FlatTerrain implements TerrainProbe {
        private final int surfaceY;
        private final Set<BlockPos3i> protectedBlocks = new HashSet<>();

        FlatTerrain(int surfaceY) {
            this.surfaceY = surfaceY;
        }

        void markProtected(BlockPos3i pos) {
            protectedBlocks.add(pos);
        }

        @Override
        public int getSurfaceY(int x, int z) {
            return surfaceY;
        }

        @Override
        public boolean isNaturalReplaceable(int x, int y, int z) {
            return y > surfaceY && !protectedBlocks.contains(new BlockPos3i(x, y, z));
        }

        @Override
        public boolean isSolidFoundation(int x, int y, int z) {
            return y <= surfaceY;
        }

        @Override
        public boolean isProtectedOrOccupied(int x, int y, int z) {
            return protectedBlocks.contains(new BlockPos3i(x, y, z));
        }

        @Override
        public boolean isWaterSurface(int x, int z) {
            return false;
        }
    }
}
