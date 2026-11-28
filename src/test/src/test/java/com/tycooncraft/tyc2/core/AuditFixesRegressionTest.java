package com.tycooncraft.tyc2.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tycooncraft.tyc2.village.BiomeStyle;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.ExpansionPlanner;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.SlotStack;
import com.tycooncraft.tyc2.village.TerrainProbe;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.WorldBlockPlacement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regression test suite verifying audit fixes across banking, currency caps, power math,
 * warehouse revision tracking, macro-economy arbitrage/decay/conservation, bottom-up fragile
 * block ordering, and underwater piling anti-grief protection.
 */
final class AuditFixesRegressionTest {

    @Test
    void inviteCodesAreUnambiguousAndUniqueAndCompanyLimitIsEnforced() {
        BankAccountManager bank = new BankAccountManager();
        UUID owner = UUID.randomUUID();
        bank.getOrCreatePersonal(owner, "Founder");

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < BankAccountManager.MAX_COMPANIES_PER_PLAYER; i++) {
            assertTrue(bank.canJoinAnotherCompany(owner));
            String longName = "Guild_" + i + "_" + "X".repeat(50);
            BankAccountManager.CompanyAccount co = bank.createCompany(owner, "Founder", longName);
            assertTrue(co.name().length() <= BankAccountManager.MAX_COMPANY_NAME_LENGTH);
            assertEquals(10, co.inviteCode().length());
            assertTrue(co.inviteCode().matches("TYC-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"),
                    "Invite code must use TYC- prefix + unambiguous alphabet: " + co.inviteCode());
            assertTrue(codes.add(co.inviteCode()), "Duplicate invite code generated");
        }

        assertFalse(bank.canJoinAnotherCompany(owner));
        assertThrows(IllegalStateException.class, () -> bank.createCompany(owner, "Founder", "OverflowGuild"));
    }

    @Test
    void bankTransferRejectsRecipientOverflowWithoutDebitingSender() {
        BankAccountManager bank = new BankAccountManager();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        bank.getOrCreatePersonal(sender, "Alice");
        bank.getOrCreatePersonal(receiver, "Bob");
        bank.creditAccount(
                BankAccountManager.AccountRef.personal(sender),
                1_000L,
                BankAccountManager.TransactionType.DEPOSIT,
                1L,
                "Test",
                "seed"
        );
        bank.creditAccount(
                BankAccountManager.AccountRef.personal(receiver),
                Long.MAX_VALUE - 5L,
                BankAccountManager.TransactionType.DEPOSIT,
                1L,
                "Test",
                "near-max"
        );

        BankAccountManager.BankResult res = bank.transferToPlayer(sender, receiver, 100L, 2L, "overflow");
        assertFalse(res.success());
        assertEquals(1_000L, bank.getBalance(BankAccountManager.AccountRef.personal(sender)));
        assertEquals(Long.MAX_VALUE - 5L, bank.getBalance(BankAccountManager.AccountRef.personal(receiver)));
    }

    @Test
    void currencySingleWithdrawalLimitPreventsEntityFlood() {
        assertTrue(CurrencyDenomination.isWithinSingleWithdrawalLimit(1L));
        assertTrue(CurrencyDenomination.isWithinSingleWithdrawalLimit(CurrencyDenomination.MAX_SINGLE_WITHDRAWAL_IC));
        assertFalse(CurrencyDenomination.isWithinSingleWithdrawalLimit(CurrencyDenomination.MAX_SINGLE_WITHDRAWAL_IC + 1L));
        assertFalse(CurrencyDenomination.isWithinSingleWithdrawalLimit(0L));
        assertFalse(CurrencyDenomination.isWithinSingleWithdrawalLimit(-500L));
    }

    @Test
    void triModePowerMathClampsUpgradesAndNeverOverflows() {
        TriModePowerMath.MintUpgradeStats stats = TriModePowerMath.computeMintUpgradeStats(64, 32, 256);
        assertEquals(TriModePowerMath.MAX_OVERCLOCKER_UPGRADES, stats.overclockerCount());
        assertEquals(TriModePowerMath.MAX_ENERGY_STORAGE_UPGRADES, stats.energyStorageCount());
        assertTrue(stats.maxCapacityEu() > 0);
        assertEquals(Long.MAX_VALUE, TriModePowerMath.euToFe(Long.MAX_VALUE));

        int totalEu = TriModePowerMath.effectiveMintTotalEu(1000, 80, 64);
        assertTrue(totalEu > 0, "effectiveMintTotalEu must not overflow or wrap negative");
    }

    @Test
    void warehouseRevisionIncrementsOnEveryMutation() {
        CooperationHubStorage storage = new CooperationHubStorage();
        long r0 = storage.getRevision();
        storage.insertExternal("minecraft:iron_ingot", 16, "", 64, false);
        long r1 = storage.getRevision();
        assertTrue(r1 > r0);

        storage.setSlotRaw(500, new SlotStack("minecraft:gold_ingot", 8, ""));
        long r2 = storage.getRevision();
        assertTrue(r2 > r1);

        storage.extractExternal(500, 4, false);
        long r3 = storage.getRevision();
        assertTrue(r3 > r2);

        storage.clearAll();
        assertTrue(storage.getRevision() > r3);
    }

    @Test
    void macroEconomyGlobalCycleThrottleCashConservationAndArbitrageFloors() {
        TyC2Config cfg = TyC2Config.defaults();
        MacroEconomyEngine econ = new MacroEconomyEngine(cfg);

        // Cash <-> Village conservation
        econ.recordMinting(10_000L);
        long mBefore = econ.totalMoneySupply();
        econ.recordCashPaidToVillage(2_500L);
        assertEquals(mBefore, econ.totalMoneySupply());
        econ.recordVillagePaidCash(1_200L);
        assertEquals(mBefore, econ.totalMoneySupply());

        // Saturation guard on huge minting
        MacroEconomyEngine sat = new MacroEconomyEngine(cfg);
        sat.recordMinting(Long.MAX_VALUE - 10L);
        sat.recordMinting(10_000L);
        assertEquals(Long.MAX_VALUE, sat.totalMoneySupply());

        // Single global Q decay per demand cycle even when multiple villages refresh
        econ.recordTrade("minecraft:wheat", 640, 10L, true);
        double qInitial = econ.rollingGoodsCirculation();
        assertTrue(econ.advanceDemandCycleIfDue(1_000L, 6_000L).isPresent());
        double qAfterFirst = econ.rollingGoodsCirculation();
        assertTrue(qAfterFirst < qInitial);
        assertTrue(econ.advanceDemandCycleIfDue(1_200L, 6_000L).isEmpty());
        assertEquals(qAfterFirst, econ.rollingGoodsCirculation(), 1e-9);
        assertTrue(econ.advanceDemandCycleIfDue(7_000L, 6_000L).isPresent());

        // Minting arbitrage floor: bronze & steel are buy-only; netherite_scrap & ic2:alloy have sell floors
        GoodsCatalog catalog = GoodsCatalog.createDefault();
        TradeGood bronze = catalog.findByItemId("mhera:bronze_ingot").orElseThrow();
        TradeGood steel = catalog.findByItemId("mhera:steel_ingot").orElseThrow();
        assertFalse(bronze.isSellableByVillage(), "IC2 bronze ingot must not be sold by villages (mint arbitrage)");
        assertTrue(bronze.isBuyableByVillage());
        assertFalse(steel.isSellableByVillage(), "IC2 steel ingot must not be sold by villages (mint arbitrage)");
        assertTrue(steel.isBuyableByVillage());

        // Extreme deflation (huge Q, tiny M) must still respect exact dynamic gross minting floors (C3)
        // without IEEE-754 rounding errors (+1 IC) or distorting secondary co-ingredients!
        MacroEconomyEngine deflated = new MacroEconomyEngine(cfg);
        deflated.recordTrade("minecraft:wheat", 100_000, 100L, true);
        assertEquals(110L, deflated.computeMintingArbitrageFloor("mhera:alloy"),
                "mhera:alloy must stay at 110 UC (not polluted to 1,100 UC by iridium+alloy)");
        assertEquals(550L, deflated.computeMintingArbitrageFloor("minecraft:netherite_scrap"));
        assertEquals(550L, deflated.computeMintingArbitrageFloor("minecraft:ancient_debris"));
        assertEquals(1_100L, deflated.computeMintingArbitrageFloor("mhera:iridium"));
        assertEquals(1_100L, deflated.computeMintingArbitrageFloor("ecera:iridium_ingot"));
        assertEquals(1_100L, deflated.computeMintingArbitrageFloor("minecraft:netherite_ingot"));
        assertEquals(123L, deflated.computeMintingArbitrageFloor("mhera:iridium_shard"));
        assertEquals(220L, deflated.computeMintingArbitrageFloor("minecraft:diamond"),
                "diamond must stay at 220 UC (not polluted to 1,100 UC by netherite+diamond)");
        assertEquals(110L, deflated.computeMintingArbitrageFloor("minecraft:emerald"));
        assertEquals(55L, deflated.computeMintingArbitrageFloor("minecraft:gold_ingot"),
                "gold_ingot must be exact integer ceil(50*11/10)=55 UC (not 56 or 275)");
        assertEquals(55L, deflated.computeMintingArbitrageFloor("mhera:refined_iron_ingot"),
                "refined_iron_ingot must be exact integer ceil(50*11/10)=55 UC (not 56)");
        assertEquals(6L, deflated.computeMintingArbitrageFloor("minecraft:iron_ingot"),
                "iron_ingot must be 6 UC (summed 2x iron pair & not polluted by gold+iron)");

        TradeGood scrap = catalog.findByItemId("minecraft:netherite_scrap").orElseThrow();
        TradeGood debris = catalog.findByItemId("minecraft:ancient_debris").orElseThrow();
        TradeGood alloy = catalog.findByItemId("mhera:alloy").orElseThrow();
        TradeGood iridiumShard = catalog.findByItemId("mhera:iridium_shard").orElseThrow();
        TradeGood refinedIron = TradeGood.builder("test:refined_iron", "mhera:refined_iron_ingot", GoodsCategory.METAL, 15L)
                .levelRange(1, 10).sellable(true).buyable(false).build();
        assertEquals(550L, deflated.quotePrice(scrap, 10, 0L, "plains", Season.NONE, false).finalUnitPrice());
        assertEquals(550L, deflated.quotePrice(debris, 10, 0L, "plains", Season.NONE, false).finalUnitPrice());
        assertEquals(110L, deflated.quotePrice(alloy, 10, 0L, "plains", Season.NONE, false).finalUnitPrice());
        assertEquals(55L, deflated.quotePrice(refinedIron, 10, 0L, "plains", Season.NONE, false).finalUnitPrice());
        assertTrue(deflated.quotePrice(iridiumShard, 10, 0L, "plains", Season.NONE, false).finalUnitPrice() >= 123L,
                "mhera:iridium_shard 1/9 precursor floor must be >= 123 UC");

        // Dynamic datapack 1-ing and 2-ing mint recipe floors without hardcoded item IDs
        MintRecipeRegistry customMintReg = MintRecipeRegistry.createDefault();
        customMintReg.register(new MintRecipe(
                "custom:mythril_500",
                List.of(MintIngredient.ofItem("custom:mythril_ingot", 2)),
                CurrencyDenomination.X500,
                1,
                1000,
                100,
                null
        ));
        customMintReg.register(new MintRecipe(
                "custom:orichalcum_iron_500",
                List.of(MintIngredient.ofItem("custom:orichalcum_ingot", 1), MintIngredient.ofTag("#ingots/iron", 1)),
                CurrencyDenomination.X500,
                1,
                2000,
                120,
                null
        ));
        deflated.setMintRecipeRegistry(customMintReg, null);
        TradeGood mythril = TradeGood.builder("custom:mythril", "custom:mythril_ingot", GoodsCategory.METAL, 50L)
                .levelRange(1, 10).sellable(true).buyable(false).build();
        TradeGood orichalcum = TradeGood.builder("custom:orichalcum", "custom:orichalcum_ingot", GoodsCategory.METAL, 50L)
                .levelRange(1, 10).sellable(true).buyable(false).build();
        assertEquals(275L, deflated.quotePrice(mythril, 10, 0L, "plains", Season.NONE, false).finalUnitPrice(),
                "Dynamic 1-ing gross mint floor: ceil((500 * 11) / (2 * 10)) = 275 UC");
        assertEquals(550L, deflated.quotePrice(orichalcum, 10, 0L, "plains", Season.NONE, false).finalUnitPrice(),
                "Dynamic 2-ing primary rare ingredient gets gross mint floor 550 UC");
        assertEquals(6L, deflated.computeMintingArbitrageFloor("minecraft:iron_ingot"),
                "Cheap iron co-ingredient in custom dual recipe must not be inflated to 550 UC");
    }

    @Test
    void warehouseMaxInboundInsertAndHubDeployedLifecycle() {
        // M2: maxInboundInsert accurately computes free space before pulling
        CooperationHubStorage storage = new CooperationHubStorage();
        assertEquals(32, storage.maxInboundInsert("minecraft:iron_ingot", 32, "", 64));
        for (int s = CooperationHubStorage.INBOUND_START; s < CooperationHubStorage.INBOUND_END_EXCLUSIVE; s++) {
            storage.setSlotRaw(s, new SlotStack("minecraft:stone", 64, ""));
        }
        assertEquals(0, storage.maxInboundInsert("minecraft:iron_ingot", 32, "", 64));
        storage.setSlotRaw(0, new SlotStack("minecraft:iron_ingot", 60, ""));
        assertEquals(4, storage.maxInboundInsert("minecraft:iron_ingot", 32, "", 64));

        // M4: hubDeployed lifecycle
        VillageRecord v = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Hubville", new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS
        );
        assertFalse(v.isHubDeployed());
        v.setHubDeployed(true);
        assertTrue(v.isHubDeployed());
        v.terminatePartnership(24_000L);
        assertFalse(v.isHubDeployed());
    }

    @Test
    void villageRecordConfiguredUpkeepAndLifecycleFields() {
        VillageRecord v = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Testville", new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS
        );
        v.establishPartnership(UUID.randomUUID(), null, 1_000L);
        long upkeep = v.applyMunicipalUpkeep(0.05);
        assertEquals(32L, upkeep);
        assertEquals(968L, v.getVillageBalance());

        assertFalse(v.isChunkLoaderInstalled());
        v.setChunkLoaderInstalled(true);
        assertTrue(v.isChunkLoaderInstalled());

        v.setNextAutoExpansionRetryTick(12_345L);
        assertEquals(12_345L, v.getNextAutoExpansionRetryTick());
    }

    @Test
    void bottomUpOrderPlacesStructuralBlocksBeforeFragileOnSameLayer() {
        List<WorldBlockPlacement> list = new ArrayList<>();
        list.add(new WorldBlockPlacement(new BlockPos3i(0, 65, 0), "minecraft:wall_torch[facing=north]"));
        list.add(new WorldBlockPlacement(new BlockPos3i(1, 65, 0), "minecraft:water"));
        list.add(new WorldBlockPlacement(new BlockPos3i(2, 65, 0), "minecraft:oak_planks"));
        list.add(new WorldBlockPlacement(new BlockPos3i(3, 64, 0), "minecraft:oak_door[half=lower]"));
        list.add(new WorldBlockPlacement(new BlockPos3i(4, 64, 0), "minecraft:cobblestone"));

        list.sort(WorldBlockPlacement.BOTTOM_UP_ORDER);

        // Y=64 first, structural cobblestone before oak_door
        assertEquals("minecraft:cobblestone", list.get(0).blockStateSpec());
        assertEquals("minecraft:oak_door[half=lower]", list.get(1).blockStateSpec());
        // Y=65 next, structural oak_planks before wall_torch & water
        assertEquals("minecraft:oak_planks", list.get(2).blockStateSpec());
        assertTrue(WorldBlockPlacement.isFragileOrAttached(list.get(3).blockStateSpec()));
        assertTrue(WorldBlockPlacement.isFragileOrAttached(list.get(4).blockStateSpec()));
    }

    @Test
    void waterfrontPierRefusesSitesWithProtectedUnderwaterBlocks() {
        VillageRecord v = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Portside", new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS
        );
        v.establishPartnership(UUID.randomUUID(), null, 5_000L);

        // Shoreline at z >= 18 is water (surface Y=62, riverbed Y=58); land is Y=64
        TerrainProbe protectedUnderwaterProbe = new TerrainProbe() {
            @Override
            public int getSurfaceY(int x, int z) {
                return z >= 18 ? 62 : 64;
            }

            @Override
            public boolean isNaturalReplaceable(int x, int y, int z) {
                return y > getSurfaceY(x, z);
            }

            @Override
            public boolean isSolidFoundation(int x, int y, int z) {
                return z >= 18 ? y <= 58 : y <= 64;
            }

            @Override
            public boolean isProtectedOrOccupied(int x, int y, int z) {
                return z >= 18 && y <= 62;
            }

            @Override
            public boolean isProtectedUnderwater(int x, int y, int z) {
                // Simulate player underwater cable/conduit everywhere in the water column
                return z >= 18 && y < 62 && y > 58;
            }

            @Override
            public boolean isWaterSurface(int x, int z) {
                return z >= 18;
            }

            @Override
            public boolean isSkyClear(int x, int fromY, int z) {
                return true;
            }
        };

        ExpansionPlanner planner = new ExpansionPlanner();
        ExpansionPlanner.PlanResult res = planner.plan(v, ProjectType.LC2_WATERFRONT_PIER, protectedUnderwaterProbe, "pier_protected");
        assertFalse(res.success(), "Pier planner must reject waterfront sites with protected underwater blocks");
    }
}
