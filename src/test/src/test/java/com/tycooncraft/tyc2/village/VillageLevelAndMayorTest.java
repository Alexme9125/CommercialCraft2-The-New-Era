package com.tycooncraft.tyc2.village;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VillageLevelTable}, {@link MayorState}, and {@link MayorElectionRules}.
 */
class VillageLevelAndMayorTest {

    @Test
    void levelTableMatchesDesignSpecExactly() {
        assertEquals(10, VillageLevelTable.DEFAULT.allLevels().size());

        VillageLevelTable.LevelSpec lv1 = VillageLevelTable.get(1);
        assertEquals(2, lv1.minPopulation());
        assertEquals(0L, lv1.minTradeVolume());
        assertEquals(0L, lv1.minVillageBalance());
        assertEquals(0L, lv1.upgradeCostSink());
        assertEquals(3, lv1.sellOrderCount());
        assertEquals(2, lv1.buyOrderCount());
        assertEquals(1.0, lv1.qtyScale(), 1e-9);
        assertEquals(8, lv1.maxPopulationCap());

        VillageLevelTable.LevelSpec lv5 = VillageLevelTable.get(5);
        assertEquals(12, lv5.minPopulation());
        assertEquals(7_000L, lv5.minTradeVolume());
        assertEquals(1_800L, lv5.minVillageBalance());
        assertEquals(1_100L, lv5.upgradeCostSink());
        assertEquals(6, lv5.sellOrderCount());
        assertEquals(5, lv5.buyOrderCount());
        assertEquals(2.5, lv5.qtyScale(), 1e-9);
        assertEquals(24, lv5.maxPopulationCap());

        VillageLevelTable.LevelSpec lv10 = VillageLevelTable.get(10);
        assertEquals(36, lv10.minPopulation());
        assertEquals(250_000L, lv10.minTradeVolume());
        assertEquals(55_000L, lv10.minVillageBalance());
        assertEquals(30_000L, lv10.upgradeCostSink());
        assertEquals(10, lv10.sellOrderCount());
        assertEquals(9, lv10.buyOrderCount());
        assertEquals(8.0, lv10.qtyScale(), 1e-9);
        assertEquals(40, lv10.maxPopulationCap());
        assertEquals(72, lv10.expansionRadiusMax());

        assertTrue(VillageLevelTable.nextLevel(1).isPresent());
        assertEquals(2, VillageLevelTable.nextLevel(1).get().level());
        assertTrue(VillageLevelTable.nextLevel(10).isEmpty());
    }

    @Test
    void levelUpgradeRequiresAllThreeThresholdsAndBurnsCapitalSink() {
        // Lv 1 -> Lv 2 requires pop >= 4, vol >= 400, balance >= 150 (sink = 100)
        assertFalse(VillageLevelTable.canUpgrade(1, 3, 400L, 150L));
        assertFalse(VillageLevelTable.canUpgrade(1, 4, 399L, 150L));
        assertFalse(VillageLevelTable.canUpgrade(1, 4, 400L, 149L));
        assertTrue(VillageLevelTable.canUpgrade(1, 4, 400L, 150L));

        // Max level cannot upgrade further
        assertFalse(VillageLevelTable.canUpgrade(10, 40, 1_000_000L, 1_000_000L));

        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Oakvale",
                new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS);
        village.setPopulation(4);
        village.setCumulativeTradeVolume(450L);
        village.setVillageBalance(200L);
        assertTrue(village.canUpgrade());

        long burned = village.tryUpgrade();
        assertEquals(100L, burned);
        assertEquals(2, village.getLevel());
        assertEquals(100L, village.getVillageBalance());
        assertEquals(30, village.getMaxExpansionRadius());

        // Second upgrade fails immediately because balance/pop/volume don't meet Lv 3 yet
        assertEquals(0L, village.tryUpgrade());
        assertEquals(2, village.getLevel());

        // Municipal upkeep burns 2% of excess above target reserve
        village.setVillageBalance(10_000L);
        long upkeep = village.applyMunicipalUpkeep();
        assertTrue(upkeep > 0L);
        assertEquals(10_000L - upkeep, village.getVillageBalance());
    }

    @Test
    void mayorNeverElectsAtZeroOrOneVillagers() {
        MayorState state = new MayorState();
        UUID v1 = UUID.randomUUID();
        MayorElectionRules.MayorCandidate solo =
                new MayorElectionRules.MayorCandidate(v1, "Solo Villager", true, false, true, 120, 5000);

        MayorElectionRules.ElectionOutcome out1 =
                MayorElectionRules.evaluate(state, 1, List.of(solo), 100L);
        assertEquals(MayorElectionRules.Action.INSUFFICIENT_POPULATION, out1.action());
        assertNull(out1.mayorUuid());
        assertFalse(state.hasLivingMayor());
        assertTrue(state.isReelectionPending());

        MayorElectionRules.ElectionOutcome out0 =
                MayorElectionRules.evaluate(state, 0, List.of(), 200L);
        assertEquals(MayorElectionRules.Action.PAUSED_DEPOPULATED, out0.action());
        assertFalse(out0.orderGenerationAllowed());
        assertTrue(state.isPausedDueToDepopulation());
        assertFalse(state.hasLivingMayor());
    }

    @Test
    void mayorElectsBestAdultNonNitwitAndHandlesDeathAndDepopulationRecovery() {
        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Ironbridge",
                new BlockPos3i(100, 64, 100), BiomeStyle.TAIGA);
        UUID owner = UUID.randomUUID();
        village.establishPartnership(owner, "guild-alpha", 100L);
        MayorState state = village.getMayorState();

        UUID babyId = UUID.randomUUID();
        UUID nitwitId = UUID.randomUUID();
        UUID rookieId = UUID.randomUUID();
        UUID veteranId = UUID.randomUUID();

        MayorElectionRules.MayorCandidate baby =
                new MayorElectionRules.MayorCandidate(babyId, "Kid", false, false, true, 999, 1000);
        MayorElectionRules.MayorCandidate nitwit =
                new MayorElectionRules.MayorCandidate(nitwitId, "Nitwit", true, true, true, 500, 9999);
        MayorElectionRules.MayorCandidate rookie =
                new MayorElectionRules.MayorCandidate(rookieId, "Rookie", true, false, true, 50, 2000);
        MayorElectionRules.MayorCandidate veteran =
                new MayorElectionRules.MayorCandidate(veteranId, "Veteran", true, false, true, 250, 4000);

        // Pop >= 2 elects Veteran (adult non-nitwit with highest XP)
        MayorElectionRules.ElectionOutcome elected = MayorElectionRules.evaluate(
                state, 4, List.of(baby, nitwit, rookie, veteran), 500L);
        assertEquals(MayorElectionRules.Action.ELECTED_NEW_MAYOR, elected.action());
        assertEquals(veteranId, elected.mayorUuid());
        assertEquals("Veteran", state.getMayorName());
        assertEquals(1, state.getElectionsHeldCount());
        assertTrue(village.canGenerateOrders());

        // Stable re-evaluation makes no change
        MayorElectionRules.ElectionOutcome stable = MayorElectionRules.evaluate(
                state, 4, List.of(baby, nitwit, rookie, veteran), 600L);
        assertEquals(MayorElectionRules.Action.NO_CHANGE, stable.action());
        assertEquals(veteranId, stable.mayorUuid());

        // Mayor dies with >= 2 remaining -> re-elects Rookie (prefers non-nitwit over higher-XP nitwit)
        MayorElectionRules.ElectionOutcome afterDeath = MayorElectionRules.onMayorDeathAndReelect(
                state, veteranId, 3, List.of(baby, nitwit, rookie), 700L);
        assertEquals(MayorElectionRules.Action.ELECTED_NEW_MAYOR, afterDeath.action());
        assertEquals(rookieId, afterDeath.mayorUuid());
        assertEquals(2, state.getElectionsHeldCount());

        // Non-mayor death does not disturb Rookie
        MayorElectionRules.ElectionOutcome nonMayorDeath = MayorElectionRules.onMayorDeathAndReelect(
                state, babyId, 2, List.of(nitwit, rookie), 750L);
        assertEquals(MayorElectionRules.Action.NO_CHANGE, nonMayorDeath.action());
        assertEquals(rookieId, state.getMayorEntityUuid());

        // Rookie dies leaving only 1 villager (Nitwit) -> clears Mayor, marks reelectionPending, does NOT elect!
        MayorElectionRules.ElectionOutcome soloLeft = MayorElectionRules.onMayorDeathAndReelect(
                state, rookieId, 1, List.of(nitwit), 800L);
        assertEquals(MayorElectionRules.Action.MAYOR_LOST_PENDING_REELECTION, soloLeft.action());
        assertNull(state.getMayorEntityUuid());
        assertTrue(state.isReelectionPending());

        // Entire village depopulates (0 villagers) -> pauses orders while PRESERVING partnership!
        MayorElectionRules.ElectionOutcome depop = MayorElectionRules.evaluate(
                state, 0, List.of(), 900L);
        assertEquals(MayorElectionRules.Action.PAUSED_DEPOPULATED, depop.action());
        assertTrue(state.isPausedDueToDepopulation());
        assertTrue(village.isPartnered());
        assertEquals(owner, village.getOwnerPlayerUuid());
        assertEquals("guild-alpha", village.getCompanyAccountId());
        assertFalse(village.canGenerateOrders());

        // 1 villager returns -> still paused, still NO Mayor elected
        UUID settler1Id = UUID.randomUUID();
        MayorElectionRules.MayorCandidate settler1 =
                new MayorElectionRules.MayorCandidate(settler1Id, "Settler One", true, false, true, 10, 500);
        MayorElectionRules.ElectionOutcome oneReturned = MayorElectionRules.evaluate(
                state, 1, List.of(settler1), 1000L);
        assertEquals(MayorElectionRules.Action.INSUFFICIENT_POPULATION, oneReturned.action());
        assertFalse(oneReturned.orderGenerationAllowed());
        assertNull(state.getMayorEntityUuid());
        assertFalse(village.canGenerateOrders());

        // 2nd villager returns -> resumes partnership order generation & elects new Mayor automatically!
        UUID settler2Id = UUID.randomUUID();
        MayorElectionRules.MayorCandidate settler2 =
                new MayorElectionRules.MayorCandidate(settler2Id, "Settler Two", true, false, true, 40, 800);
        MayorElectionRules.ElectionOutcome resumed = MayorElectionRules.evaluate(
                state, 2, List.of(settler1, settler2), 1100L);
        assertEquals(MayorElectionRules.Action.RESUMED_AND_ELECTED, resumed.action());
        assertTrue(resumed.orderGenerationAllowed());
        assertEquals(settler2Id, state.getMayorEntityUuid());
        assertFalse(state.isPausedDueToDepopulation());
        assertTrue(village.isPartnered());
        assertTrue(village.canGenerateOrders());
    }

    @Test
    void nitwitsAreStrictlyForbiddenFromBecomingMayor() {
        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Greenbelt",
                new BlockPos3i(50, 64, 50), BiomeStyle.PLAINS);
        village.establishPartnership(UUID.randomUUID(), null, 200L);
        MayorState state = village.getMayorState();

        UUID nitwit1Id = UUID.randomUUID();
        UUID nitwit2Id = UUID.randomUUID();
        UUID noviceId = UUID.randomUUID();

        MayorElectionRules.MayorCandidate nitwit1 =
                new MayorElectionRules.MayorCandidate(nitwit1Id, "Nitwit A", true, true, true, 999, 50_000);
        MayorElectionRules.MayorCandidate nitwit2 =
                new MayorElectionRules.MayorCandidate(nitwit2Id, "Nitwit B", true, true, true, 500, 40_000);
        MayorElectionRules.MayorCandidate novice =
                new MayorElectionRules.MayorCandidate(noviceId, "Novice", true, false, true, 0, 0);

        // 1. Two Nitwit adults -> NO Mayor elected, partnership remains intact
        assertTrue(MayorElectionRules.selectBestCandidate(List.of(nitwit1, nitwit2)).isEmpty());
        MayorElectionRules.ElectionOutcome allNitwits =
                MayorElectionRules.evaluate(state, 2, List.of(nitwit1, nitwit2), 100L);
        assertEquals(MayorElectionRules.Action.WAITING_FOR_ADULT_CANDIDATE, allNitwits.action());
        assertNull(allNitwits.mayorUuid());
        assertFalse(state.hasLivingMayor());
        assertTrue(state.isReelectionPending());
        assertEquals(2, state.getLastKnownVillagerCount());
        assertTrue(village.isPartnered());

        // 2. 1 high-XP Nitwit + 1 0-XP non-nitwit adult -> ALWAYS elects the non-nitwit
        MayorElectionRules.ElectionOutcome mixed =
                MayorElectionRules.evaluate(state, 2, List.of(nitwit1, novice), 200L);
        assertEquals(MayorElectionRules.Action.ELECTED_NEW_MAYOR, mixed.action());
        assertEquals(noviceId, mixed.mayorUuid());
        assertEquals(noviceId, state.getMayorEntityUuid());

        // 3a. Legacy save with a Nitwit stored as current Mayor -> automatically disqualified & replaced by non-nitwit
        state.setMayorEntityUuid(nitwit1Id);
        state.setMayorName("Legacy Nitwit Mayor");
        MayorElectionRules.ElectionOutcome replaced =
                MayorElectionRules.evaluate(state, 2, List.of(nitwit1, novice), 300L);
        assertEquals(MayorElectionRules.Action.ELECTED_NEW_MAYOR, replaced.action());
        assertEquals(noviceId, replaced.mayorUuid());
        assertEquals(noviceId, state.getMayorEntityUuid());

        // 3b. Legacy save with a Nitwit Mayor and NO non-nitwit adults -> cleared & waits cleanly
        state.setMayorEntityUuid(nitwit1Id);
        state.setMayorName("Legacy Nitwit Mayor");
        MayorElectionRules.ElectionOutcome cleared =
                MayorElectionRules.evaluate(state, 2, List.of(nitwit1, nitwit2), 400L);
        assertEquals(MayorElectionRules.Action.WAITING_FOR_ADULT_CANDIDATE, cleared.action());
        assertNull(cleared.mayorUuid());
        assertFalse(state.hasLivingMayor());
        assertTrue(state.isReelectionPending());
        assertTrue(village.isPartnered());
    }
}
