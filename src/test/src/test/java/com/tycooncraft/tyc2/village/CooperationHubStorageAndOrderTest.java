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
}
