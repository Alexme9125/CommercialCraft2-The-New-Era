package com.tycooncraft.tyc2.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyAndPowerTest {

    @Test
    void currencyDenominationsAndIc2Unification() {
        assertEquals(9, CurrencyDenomination.values().length);
        assertEquals("mhera:coin", CurrencyDenomination.X1.canonicalItemId(true));
        assertEquals("mnera:coin_1", CurrencyDenomination.X1.canonicalItemId(false));
        assertEquals("mhera:coin", CurrencyDenomination.X1.ic2CompatId());

        for (CurrencyDenomination denom : CurrencyDenomination.values()) {
            if (denom != CurrencyDenomination.X1) {
                assertNull(denom.ic2CompatId());
                assertEquals(denom.tyc2ItemId(), denom.canonicalItemId(true));
                assertEquals(denom.tyc2ItemId(), denom.canonicalItemId(false));
            }
            assertEquals(Optional.of(denom), CurrencyDenomination.valueOfItemId(denom.tyc2ItemId()));
            assertEquals(Optional.of(denom), CurrencyDenomination.fromValue(denom.value()));
        }

        // Mutual acceptance of ic2:coin and tyc2:coin_1 as 1x legal tender
        assertEquals(Optional.of(CurrencyDenomination.X1), CurrencyDenomination.valueOfItemId("mhera:coin"));
        assertEquals(Optional.of(CurrencyDenomination.X1), CurrencyDenomination.valueOfItemId("mnera:coin_1"));
        assertTrue(CurrencyDenomination.isLegalTender("mhera:coin"));
        assertTrue(CurrencyDenomination.isLegalTender("mnera:coin_1000"));
        assertFalse(CurrencyDenomination.isLegalTender("minecraft:emerald"));
    }

    @Test
    void greedyChangeDecompositionIsOptimalAndExact() {
        assertTrue(CurrencyDenomination.decompose(0L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> CurrencyDenomination.decompose(-5L));

        Map<CurrencyDenomination, Integer> d1888 = CurrencyDenomination.decompose(1888L);
        assertEquals(1888L, CurrencyDenomination.totalValue(d1888));
        assertEquals(1, d1888.get(CurrencyDenomination.X1000));
        assertEquals(1, d1888.get(CurrencyDenomination.X500));
        assertEquals(1, d1888.get(CurrencyDenomination.X200));
        assertEquals(1, d1888.get(CurrencyDenomination.X100));
        assertEquals(1, d1888.get(CurrencyDenomination.X50));
        assertEquals(1, d1888.get(CurrencyDenomination.X20));
        assertEquals(1, d1888.get(CurrencyDenomination.X10));
        assertEquals(1, d1888.get(CurrencyDenomination.X5));
        assertEquals(3, d1888.get(CurrencyDenomination.X1));

        long[] samples = {1, 4, 5, 9, 10, 19, 49, 99, 150, 499, 777, 2543, 123_456L};
        for (long sample : samples) {
            Map<CurrencyDenomination, Integer> coins = CurrencyDenomination.decompose(sample);
            assertEquals(sample, CurrencyDenomination.totalValue(coins));
            assertFalse(coins.containsValue(0));
        }

        Map<CurrencyDenomination, Integer> neg = new EnumMap<>(CurrencyDenomination.class);
        neg.put(CurrencyDenomination.X10, -1);
        assertThrows(IllegalArgumentException.class, () -> CurrencyDenomination.totalValue(neg));
    }

    @Test
    void triModePowerMathConversionsUpgradesFuelAndTerminal() {
        // 1 EU = 4 FE
        assertEquals(64_000L, TriModePowerMath.euToFe(TriModePowerMath.MINT_BASE_CAPACITY_EU));
        assertEquals(80_000L, TriModePowerMath.euToFe(TriModePowerMath.TERMINAL_MAX_CAPACITY_EU));
        assertEquals(25L, TriModePowerMath.feToEuFloor(103L));
        assertEquals(26L, TriModePowerMath.feToEuCeil(103L));

        // Base stats
        TriModePowerMath.MintUpgradeStats base = TriModePowerMath.computeMintUpgradeStats(0, 0, 0);
        assertEquals(16_000, base.maxCapacityEu());
        assertEquals(32, base.maxInputEuPerTick());
        assertEquals(1, base.powerTier());

        // Upgraded stats (2 overclockers, 2 transformers -> HV 512 EU/t, 3 energy storages -> +30k EU)
        TriModePowerMath.MintUpgradeStats upgraded = TriModePowerMath.computeMintUpgradeStats(2, 2, 3);
        assertEquals(46_000, upgraded.maxCapacityEu());
        assertEquals(512, upgraded.maxInputEuPerTick());
        assertEquals(3, upgraded.powerTier());

        // Overclocker speed up & energy scaling
        int baseTicks = TriModePowerMath.effectiveMintDurationTicks(80, 0);
        int oc2Ticks = TriModePowerMath.effectiveMintDurationTicks(80, 2);
        assertEquals(80, baseTicks);
        assertTrue(oc2Ticks < baseTicks && oc2Ticks >= 1);
        assertEquals(400, TriModePowerMath.effectiveMintTotalEu(400, 80, 0));
        assertTrue(TriModePowerMath.effectiveMintTotalEu(400, 80, 2) > 400);

        // Vanilla fuel fallback table
        assertEquals(1_600, TriModePowerMath.getFuelEnergyEu("minecraft:coal"));
        assertEquals(1_600, TriModePowerMath.getFuelEnergyEu("minecraft:charcoal"));
        assertEquals(800, TriModePowerMath.getFuelEnergyEu("minecraft:redstone"));
        assertEquals(14_400, TriModePowerMath.getFuelEnergyEu("minecraft:coal_block"));
        assertEquals(2_400, TriModePowerMath.getFuelEnergyEu("minecraft:blaze_rod"));
        assertFalse(TriModePowerMath.isFuelItem("minecraft:cobblestone"));
        assertEquals(16, TriModePowerMath.computeFuelTickGenerationEu(1_600, 0, 16_000));

        // Terminal power costs & charging
        assertEquals(128L, TriModePowerMath.computeChargeTransferEu(
                5_000L, 1_000L, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU, TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK));
        assertEquals(19_980L, TriModePowerMath.consumeTerminalAction(20_000L, TriModePowerMath.TerminalAction.OPEN_REMOTE_UI));
        assertEquals(19_950L, TriModePowerMath.consumeTerminalAction(20_000L, TriModePowerMath.TerminalAction.REMOTE_TRADE));
        assertEquals(19_900L, TriModePowerMath.consumeTerminalAction(20_000L, TriModePowerMath.TerminalAction.REMOTE_UPGRADE_OR_EXPANSION));
        assertFalse(TriModePowerMath.canAffordTerminalAction(10L, TriModePowerMath.TerminalAction.OPEN_REMOTE_UI));
        assertEquals(-1L, TriModePowerMath.consumeTerminalAction(49L, TriModePowerMath.TerminalAction.REMOTE_TRADE));
    }

    @Test
    void primitiveMintDurationFuelAndElectricOnlyIndustrialMintMath() {
        assertEquals(1.5, TriModePowerMath.PRIMITIVE_MINT_TIME_MULTIPLIER, 1e-9);
        assertFalse(TriModePowerMath.MINT_ACCEPTS_SOLID_FUEL);
        assertFalse(TriModePowerMath.mintAcceptsSolidFuel());

        MintRecipeRegistry registry = MintRecipeRegistry.createDefault();
        MintRecipe copper = registry.getById("mnera:mint_copper_1").orElseThrow();
        MintRecipe iron = registry.getById("mnera:mint_iron_5").orElseThrow();
        MintRecipe emerald100 = registry.getById("mnera:mint_gold_emerald_100").orElseThrow();
        assertEquals(90, TriModePowerMath.primitiveMintDurationTicks(copper));     // 60 * 1.5
        assertEquals(120, TriModePowerMath.primitiveMintDurationTicks(iron));      // 80 * 1.5
        assertEquals(210, TriModePowerMath.primitiveMintDurationTicks(emerald100)); // 140 * 1.5
        assertEquals(17, TriModePowerMath.primitiveMintDurationTicks(11));         // ceil(11 * 1.5) = 17

        // Standard furnace burn ticks for Primitive Minting Machine
        assertEquals(1_600, TriModePowerMath.primitiveBurnTicks("minecraft:coal"));
        assertEquals(1_600, TriModePowerMath.primitiveBurnTicks("minecraft:charcoal"));
        assertEquals(16_000, TriModePowerMath.primitiveBurnTicks("minecraft:coal_block"));
        assertEquals(2_400, TriModePowerMath.primitiveBurnTicks("minecraft:blaze_rod"));
        assertEquals(300, TriModePowerMath.primitiveBurnTicks("minecraft:oak_planks"));
        assertEquals(300, TriModePowerMath.primitiveBurnTicks("minecraft:spruce_log"));
        assertEquals(300, TriModePowerMath.primitiveBurnTicks("minecraft:birch_wood"));
        assertEquals(300, TriModePowerMath.primitiveBurnTicks("mhera:rubber_wood"));
        assertEquals(100, TriModePowerMath.primitiveBurnTicks("minecraft:stick"));
        assertEquals(0, TriModePowerMath.primitiveBurnTicks("minecraft:crimson_planks"));
        assertEquals(0, TriModePowerMath.primitiveBurnTicks("minecraft:redstone"));
        assertTrue(TriModePowerMath.isPrimitiveFuelItem("minecraft:coal"));
        assertFalse(TriModePowerMath.isPrimitiveFuelItem("minecraft:stone"));

        // ATM solid fuel table still retained
        assertEquals(800, TriModePowerMath.getFuelEnergyEu("minecraft:redstone"));
        assertEquals(14_400, TriModePowerMath.getFuelEnergyEu("minecraft:coal_block"));

        // Pure-electric Industrial Mint battery discharge helper
        assertEquals(32L, TriModePowerMath.computeBatteryDischargeTransferEu(10_000L, 0L, 16_000L, 32L));
        assertEquals(10L, TriModePowerMath.computeBatteryDischargeTransferEu(10L, 0L, 16_000L, 32L));
        assertEquals(0L, TriModePowerMath.computeBatteryDischargeTransferEu(10_000L, 16_000L, 16_000L, 32L));
    }
}
