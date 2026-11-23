package com.tycooncraft.tyc2.village;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for 1,000-slot dual-zone {@link CooperationHubStorage}, {@link VillageTradeOrder},
 * {@link ExpansionDesire}, and {@link VillageRecord} trade execution.
 */
class CooperationHubStorageAndOrderTest {

    @Test
    void dualZoneIsolationAndZeroLoopBackGuarantee() {
        assertEquals(1000, CooperationHubStorage.TOTAL_SLOTS);
        assertEquals(0, CooperationHubStorage.INBOUND_START);
        assertEquals(500, CooperationHubStorage.INBOUND_SLOTS);
        assertEquals(500, CooperationHubStorage.OUTBOUND_START);
        assertEquals(500, CooperationHubStorage.OUTBOUND_SLOTS);

        CooperationHubStorage storage = new CooperationHubStorage();

        // External insert goes strictly into Inbound (0..499)
        assertEquals(0, storage.insertExternal("minecraft:iron_ingot", 150, ""));
        assertEquals(150, storage.countInInbound("minecraft:iron_ingot"));
        assertEquals(0, storage.countInOutbound("minecraft:iron_ingot"));
        assertEquals(3, storage.getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
        assertEquals(0, storage.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));

        // External extract NEVER pulls from Inbound slots 0..499 (zero loop-back!)
        for (int slot = 0; slot < 500; slot++) {
            assertFalse(storage.canExtractExternalAtSlot(slot));
            assertTrue(storage.extractExternal(slot, 64).isEmpty());
        }
        assertEquals(150, storage.countInInbound("minecraft:iron_ingot"));

        // External slot insert rejects ALL Outbound slots 500..999
        for (int slot = 500; slot < 1000; slot++) {
            assertFalse(storage.canInsertExternalAtSlot(slot));
            assertEquals(10, storage.insertExternalAtSlot(slot, "minecraft:coal", 10, "", 64, false));
        }
        assertEquals(0, storage.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));

        // Filling all 500 Inbound slots (32,000 items) overflows cleanly without spilling into Outbound
        int maxInboundCapacity = 500 * 64;
        int remainingToFill = maxInboundCapacity - 150;
        assertEquals(0, storage.insertExternal("minecraft:iron_ingot", remainingToFill, ""));
        assertEquals(500, storage.getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
        assertEquals(25, storage.insertExternal("minecraft:iron_ingot", 25, ""));
        assertEquals(0, storage.getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));

        // Internal depositToOutbound writes strictly into 500..999 and external extraction pulls from 500..999
        assertTrue(storage.canFitInOutbound("minecraft:enchanted_book", 1, "{mending:1}", 1));
        assertEquals(0, storage.depositToOutbound("minecraft:enchanted_book", 1, "{mending:1}", 1, false));
        assertEquals(1, storage.countInOutbound("minecraft:enchanted_book"));
        assertEquals(0, storage.countInInbound("minecraft:enchanted_book"));

        // Simulate extract does not mutate
        Optional<SlotStack> sim = storage.extractExternal(500, 1, true);
        assertTrue(sim.isPresent());
        assertEquals("minecraft:enchanted_book", sim.get().itemId());
        assertEquals(1, storage.countInOutbound("minecraft:enchanted_book"));

        // Real extract removes from slot 500
        Optional<SlotStack> pulled = storage.extractExternal(500, 1);
        assertTrue(pulled.isPresent());
        assertEquals("{mending:1}", pulled.get().nbtJson());
        assertEquals(0, storage.countInOutbound("minecraft:enchanted_book"));
    }

    @Test
    void atomicInboundConsumptionAndSnapshotPersistence() {
        CooperationHubStorage storage = new CooperationHubStorage();
        storage.insertExternal("minecraft:copper_ingot", 20, "");
        storage.depositToOutbound("minecraft:copper_ingot", 64, "");

        // Atomic failure when requesting more than Inbound contains (Outbound ignored!)
        assertFalse(storage.consumeFromInbound("minecraft:copper_ingot", 21));
        assertEquals(20, storage.countInInbound("minecraft:copper_ingot"));
        assertEquals(64, storage.countInOutbound("minecraft:copper_ingot"));

        // Atomic success consumes exact quantity
        assertTrue(storage.consumeFromInbound("minecraft:copper_ingot", 15));
        assertEquals(5, storage.countInInbound("minecraft:copper_ingot"));

        // Snapshot export/import roundtrip
        storage.setSlotRaw(499, new SlotStack("minecraft:diamond", 12, "{custom:1}"));
        storage.setSlotRaw(999, new SlotStack("minecraft:emerald", 33, ""));
        CooperationHubStorage.StorageSnapshot snapshot = storage.exportSnapshot();

        CooperationHubStorage restored = new CooperationHubStorage();
        restored.importSnapshot(snapshot);
        assertEquals(5, restored.countInInbound("minecraft:copper_ingot"));
        assertEquals(12, restored.countInInbound("minecraft:diamond"));
        assertEquals(64, restored.countInOutbound("minecraft:copper_ingot"));
        assertEquals(33, restored.countInOutbound("minecraft:emerald"));
        assertEquals("{custom:1}", restored.getSlot(499).orElseThrow().nbtJson());
    }

    @Test
    void paginationAndSearchAcrossAll1000Slots() {
        CooperationHubStorage storage = new CooperationHubStorage();
        storage.setSlotRaw(0, SlotStack.of("minecraft:iron_ingot", 32));
        storage.setSlotRaw(255, SlotStack.of("minecraft:iron_block", 4));
        storage.setSlotRaw(499, SlotStack.of("minecraft:wheat", 64));
        storage.setSlotRaw(500, new SlotStack("minecraft:enchanted_book", 1, "{StoredEnchantments:[mending]}"));
        storage.setSlotRaw(950, SlotStack.of("minecraft:iron_ingot", 16));

        // Unfiltered pagination: 10 pages x 50 slots per zone
        CooperationHubStorage.PageResult inPage0 =
                storage.getPage(CooperationHubStorage.Zone.INBOUND, 0, 50, "");
        assertEquals(10, inPage0.totalPages());
        assertEquals(500, inPage0.totalMatchingSlots());
        assertEquals(50, inPage0.slotIndices().size());
        assertEquals(0, inPage0.slotIndices().get(0));
        assertEquals(49, inPage0.slotIndices().get(49));

        CooperationHubStorage.PageResult inPage9 =
                storage.getPage(CooperationHubStorage.Zone.INBOUND, 9, 50, null);
        assertEquals(450, inPage9.slotIndices().get(0));
        assertEquals(499, inPage9.slotIndices().get(49));
        assertEquals(1, inPage9.nonEmptyEntries().size());

        CooperationHubStorage.PageResult outPage9 =
                storage.getPage(CooperationHubStorage.Zone.OUTBOUND, 9, 50, "");
        assertEquals(10, outPage9.totalPages());
        assertEquals(950, outPage9.slotIndices().get(0));
        assertEquals(999, outPage9.slotIndices().get(49));

        // Search filter in Inbound matches slots 0 and 255 only (ignores Outbound slot 950!)
        CooperationHubStorage.PageResult ironSearch =
                storage.getPage(CooperationHubStorage.Zone.INBOUND, 0, 50, "iron");
        assertEquals(2, ironSearch.totalMatchingSlots());
        assertEquals(1, ironSearch.totalPages());
        assertEquals(java.util.List.of(0, 255), ironSearch.slotIndices());

        // Search by NBT payload in Outbound finds Mending book at slot 500
        CooperationHubStorage.PageResult mendingSearch =
                storage.getPage(CooperationHubStorage.Zone.OUTBOUND, 0, 50, "mending");
        assertEquals(1, mendingSearch.totalMatchingSlots());
        assertEquals(java.util.List.of(500), mendingSearch.slotIndices());
    }

    @Test
    void tradeOrderAndExpansionDesireFulfillmentAgainstHubStorage() {
        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Copperton",
                new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS);
        village.establishPartnership(UUID.randomUUID(), null, 500L);

        VillageTradeOrder buyOrder = new VillageTradeOrder(
                "buy-1", OrderSide.VILLAGE_BUYS, "minecraft:iron_ingot", "Iron Ingot", "",
                20, 0, 15L, 0L, 12_000L, true, false);
        VillageTradeOrder sellOrder = new VillageTradeOrder(
                "sell-1", OrderSide.VILLAGE_SELLS, "minecraft:wheat", "Wheat", "",
                32, 0, 3L, 0L, 12_000L, false, true);
        village.addOrder(buyOrder);
        village.addOrder(sellOrder);

        // Pipe iron ingots into Inbound and fulfill buy order
        village.getStorage().insertExternal("minecraft:iron_ingot", 12, "");
        VillageRecord.TradeExecutionResult buyRes = village.executeBuyOrderFromInbound("buy-1", 20);
        assertTrue(buyRes.success());
        assertEquals(12, buyRes.executedQty());
        assertEquals(180L, buyRes.totalCurrency());
        assertEquals(320L, village.getVillageBalance());
        assertEquals(180L, village.getCumulativeTradeVolume());
        assertEquals(0, village.getStorage().countInInbound("minecraft:iron_ingot"));
        assertEquals(8, buyRes.updatedOrder().remainingQty());

        // Buy wheat from village sell order -> deposited into Outbound
        VillageRecord.TradeExecutionResult sellRes =
                village.executeSellOrderToOutbound("sell-1", 32, "", 64);
        assertTrue(sellRes.success());
        assertEquals(32, sellRes.executedQty());
        assertEquals(96L, sellRes.totalCurrency());
        assertEquals(416L, village.getVillageBalance());
        assertEquals(32, village.getStorage().countInOutbound("minecraft:wheat"));
        assertTrue(sellRes.updatedOrder().isCompleted());

        // ExpansionDesire pulls materials cleanly from Inbound
        ExpansionDesire desire = new ExpansionDesire(
                "exp-1", ProjectType.ROAD_AND_LAMPS,
                Map.of("minecraft:cobblestone", 24, "minecraft:lantern", 2),
                Map.of(), 80L, false, true);
        village.getStorage().insertExternal("minecraft:cobblestone", 24, "");
        village.getStorage().insertExternal("minecraft:lantern", 1, "");

        ExpansionDesire partial = desire.pullAvailableFromInbound(village.getStorage());
        assertFalse(partial.allMaterialsDelivered());
        assertEquals(0, partial.remainingFor("minecraft:cobblestone"));
        assertEquals(1, partial.remainingFor("minecraft:lantern"));

        village.getStorage().insertExternal("minecraft:lantern", 1, "");
        ExpansionDesire full = partial.pullAvailableFromInbound(village.getStorage());
        assertTrue(full.allMaterialsDelivered());
        assertEquals(1.0, full.completionRatio(), 1e-9);
    }

    @Test
    void tradeFailureCodesDistinguishMissingItemsFromLowFundsAndSupportTagMatching() {
        VillageRecord village = new VillageRecord(
                UUID.randomUUID(), "minecraft:overworld", "Tradehaven",
                new BlockPos3i(0, 64, 0), BiomeStyle.PLAINS);
        village.establishPartnership(UUID.randomUUID(), null, 500L);

        VillageTradeOrder buyOrder = new VillageTradeOrder(
                "buy-iron", OrderSide.VILLAGE_BUYS, "minecraft:iron_ingot", "Iron Ingot", "",
                10, 0, 20L, 0L, 12_000L, true, false);
        VillageTradeOrder logTagBuyOrder = new VillageTradeOrder(
                "buy-logs", OrderSide.VILLAGE_BUYS, "#minecraft:logs", "Any Logs", "",
                10, 0, 5L, 0L, 12_000L, true, false);
        VillageTradeOrder sellOrder = new VillageTradeOrder(
                "sell-coal", OrderSide.VILLAGE_SELLS, "minecraft:coal", "Coal", "",
                16, 0, 4L, 0L, 12_000L, false, false);
        village.addOrder(buyOrder);
        village.addOrder(logTagBuyOrder);
        village.addOrder(sellOrder);

        // 1. Plenty of village funds (500 IC) but 0 matching items in Inbound -> INSUFFICIENT_INBOUND_ITEMS
        VillageRecord.TradeExecutionResult noItems = village.executeBuyOrderFromInbound("buy-iron", 5);
        assertFalse(noItems.success());
        assertEquals(VillageRecord.TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS, noItems.failureCode());
        assertEquals(500L, village.getVillageBalance());

        // 2. Inbound has 10 items, but villageBalance (15 IC) < unitPrice (20 IC) -> INSUFFICIENT_VILLAGE_FUNDS
        village.getStorage().insertExternal("minecraft:iron_ingot", 10, "");
        village.setVillageBalance(15L);
        VillageRecord.TradeExecutionResult lowFunds = village.executeBuyOrderFromInbound("buy-iron", 5);
        assertFalse(lowFunds.success());
        assertEquals(VillageRecord.TradeFailureCode.INSUFFICIENT_VILLAGE_FUNDS, lowFunds.failureCode());
        assertEquals(10, village.getStorage().countInInbound("minecraft:iron_ingot"));

        // 3. Partial delivery succeeds when villageBalance >= unitPrice even if < remainingQty * unitPrice
        village.setVillageBalance(45L); // affords 2 * 20 = 40 IC (order remaining value is 200 IC)
        VillageRecord.TradeExecutionResult partialBuy = village.executeBuyOrderFromInbound("buy-iron", 10);
        assertTrue(partialBuy.success());
        assertEquals(VillageRecord.TradeFailureCode.NONE, partialBuy.failureCode());
        assertEquals(2, partialBuy.executedQty());
        assertEquals(40L, partialBuy.totalCurrency());
        assertEquals(5L, village.getVillageBalance());
        assertEquals(8, village.getStorage().countInInbound("minecraft:iron_ingot"));
        assertEquals(8, partialBuy.updatedOrder().remainingQty());

        // Remaining 5 IC can no longer afford another unit -> INSUFFICIENT_VILLAGE_FUNDS
        VillageRecord.TradeExecutionResult afterPartial = village.executeBuyOrderFromInbound("buy-iron", 1);
        assertFalse(afterPartial.success());
        assertEquals(VillageRecord.TradeFailureCode.INSUFFICIENT_VILLAGE_FUNDS, afterPartial.failureCode());

        // 4. Tag-matching buy delivery across mixed log species
        village.setVillageBalance(500L);
        java.util.function.Predicate<SlotStack> isLog = stack -> stack.itemId().endsWith("_log");
        VillageRecord.TradeExecutionResult tagMissing =
                village.executeBuyOrderFromInboundMatching("buy-logs", 10, isLog);
        assertFalse(tagMissing.success());
        assertEquals(VillageRecord.TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS, tagMissing.failureCode());

        village.getStorage().insertExternal("minecraft:oak_log", 3, "");
        village.getStorage().insertExternal("minecraft:spruce_log", 4, "");
        VillageRecord.TradeExecutionResult tagDelivered =
                village.executeBuyOrderFromInboundMatching("buy-logs", 10, isLog);
        assertTrue(tagDelivered.success());
        assertEquals(VillageRecord.TradeFailureCode.NONE, tagDelivered.failureCode());
        assertEquals(7, tagDelivered.executedQty());
        assertEquals(35L, tagDelivered.totalCurrency());
        assertEquals(0, village.getStorage().countInInbound(isLog));
        assertEquals(3, tagDelivered.updatedOrder().remainingQty());

        // 5. Validation & Outbound-full failure codes + 5-arg backwards-compatible constructor
        assertEquals(VillageRecord.TradeFailureCode.INVALID_QTY,
                village.executeBuyOrderFromInbound("buy-iron", 0).failureCode());
        assertEquals(VillageRecord.TradeFailureCode.ORDER_NOT_FOUND,
                village.executeBuyOrderFromInbound("missing-id", 1).failureCode());
        assertEquals(VillageRecord.TradeFailureCode.WRONG_SIDE,
                village.executeBuyOrderFromInbound("sell-coal", 1).failureCode());
        assertEquals(VillageRecord.TradeFailureCode.WRONG_SIDE,
                village.executeSellOrderToOutbound("buy-iron", 1, "", 64).failureCode());

        // Fill all 500 Outbound slots with non-matching items -> OUTBOUND_FULL
        for (int s = CooperationHubStorage.OUTBOUND_START; s < CooperationHubStorage.OUTBOUND_END_EXCLUSIVE; s++) {
            village.getStorage().setSlotRaw(s, SlotStack.of("minecraft:stone", 64));
        }
        VillageRecord.TradeExecutionResult outFull =
                village.executeSellOrderToOutbound("sell-coal", 4, "", 64);
        assertFalse(outFull.success());
        assertEquals(VillageRecord.TradeFailureCode.OUTBOUND_FULL, outFull.failureCode());

        // Clear slot 500, finish sell order, then verify ALREADY_COMPLETED
        village.getStorage().setSlotRaw(500, null);
        assertEquals(VillageRecord.TradeFailureCode.NONE,
                village.executeSellOrderToOutbound("sell-coal", 16, "", 64).failureCode());
        assertEquals(VillageRecord.TradeFailureCode.ALREADY_COMPLETED,
                village.executeSellOrderToOutbound("sell-coal", 1, "", 64).failureCode());

        // Backwards-compatible 5-arg constructor infers failureCode cleanly
        VillageRecord.TradeExecutionResult legacyCtor =
                new VillageRecord.TradeExecutionResult(false, 0, 0L, null, "Insufficient items in Inbound zone");
        assertEquals(VillageRecord.TradeFailureCode.INSUFFICIENT_INBOUND_ITEMS, legacyCtor.failureCode());
    }
}
