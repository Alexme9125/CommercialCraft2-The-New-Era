package com.tycooncraft.tyc2.forge.compat.ic2;

import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.forge.equipment.ForgeGear;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import com.industrialage.ia2.world.MachineAutomation;
import com.industrialage.ia2.wrench.WrenchTarget;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlock;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlock;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Isolated IndustrialCraft² ({@code ic2}) runtime adapter for Forge 1.19.2.
 *
 * <p>NEVER classloaded unless {@code ModList.get().isLoaded("ic2")} is {@code true}
 * (strictly gated behind {@link com.tycooncraft.tyc2.forge.compat.Ic2Bridge}).
 */
public final class Ic2LoadedBridge {

    private static final EnergyGraph.Ports ALL_FACE_SINK =
            new EnergyGraph.Ports(EnumSet.allOf(CapabilityFacade.Face.class), Set.of(), false);

    private Ic2LoadedBridge() {
    }

    public static Block createMintBlock() {
        return new Ic2MintMachineBlock();
    }

    public static Block createAtmBlock() {
        return new Ic2AtmMachineBlock();
    }

    public static MintMachineBlockEntity createMintBlockEntity(BlockPos pos, BlockState state) {
        return new Ic2MintMachineBlockEntity(pos, state);
    }

    public static AtmMachineBlockEntity createAtmBlockEntity(BlockPos pos, BlockState state) {
        return new Ic2AtmMachineBlockEntity(pos, state);
    }

    public static void connectPower(BlockEntity be) {
        WorldEnergy.connect(be);
    }

    public static void disconnectPower(BlockEntity be) {
        WorldEnergy.disconnect(be);
    }

    public static boolean canDischargeBattery(ItemStack stack, int tier) {
        return ForgeGear.canDischarge(stack, tier);
    }

    public static double dischargeBattery(ItemStack stack, double neededEu, int tier) {
        return ForgeGear.discharge(stack, neededEu, tier);
    }

    public static double dischargeBattery(ItemStack stack, double freeSpaceEu, double maxCapacityEu, int tier) {
        return ForgeGear.discharge(stack, freeSpaceEu, maxCapacityEu, tier);
    }

    private static volatile Method automationMatchesFilterMethod;
    private static volatile boolean automationMatchesFilterResolved;
    private static volatile Method automationSettingsMethod;
    private static volatile Method settingsMatchesMethod;

    public static List<Direction> resolveUpgradeFaces(ItemStack stack) {
        return MachineAutomation.faces(stack);
    }

    public static boolean matchesUpgradeFilter(
            List<ItemStack> upgrades,
            String basicUpgradeId,
            String advancedUpgradeId,
            ItemStack candidate
    ) {
        boolean hasAdvanced = false;
        for (ItemStack s : upgrades) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            String id = TyC2Ids.itemId(s);
            if (basicUpgradeId.equals(id)) {
                return true;
            }
            if (advancedUpgradeId.equals(id)) {
                hasAdvanced = true;
                if (matchesAdvancedUpgrade(s, candidate)) {
                    return true;
                }
            }
        }
        return !hasAdvanced;
    }

    private static boolean matchesAdvancedUpgrade(ItemStack upgrade, ItemStack candidate) {
        try {
            if (!automationMatchesFilterResolved) {
                try {
                    automationMatchesFilterMethod = MachineAutomation.class.getMethod(
                            "matchesFilter", ItemStack.class, ItemStack.class
                    );
                } catch (NoSuchMethodException ignored) {
                    automationMatchesFilterMethod = null;
                }
                automationMatchesFilterResolved = true;
            }
            if (automationMatchesFilterMethod != null) {
                Object res = automationMatchesFilterMethod.invoke(null, upgrade, candidate);
                return !(res instanceof Boolean b) || b;
            }
            if (automationSettingsMethod == null) {
                Method m = MachineAutomation.class.getDeclaredMethod("settings", ItemStack.class);
                m.setAccessible(true);
                automationSettingsMethod = m;
            }
            Object settings = automationSettingsMethod.invoke(null, upgrade);
            if (settings == null) {
                return true;
            }
            if (settingsMatchesMethod == null) {
                Method m = settings.getClass().getDeclaredMethod("matches", ItemStack.class);
                m.setAccessible(true);
                settingsMatchesMethod = m;
            }
            Object res = settingsMatchesMethod.invoke(settings, candidate);
            return !(res instanceof Boolean b) || b;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** IC2 Wrench-enabled Minting Machine block subclass (instantiated only when IC2 is loaded). */
    public static final class Ic2MintMachineBlock extends MintMachineBlock implements WrenchTarget {
        @Override
        public boolean wrenchUsesLoot() {
            return true;
        }
    }

    /** IC2 Wrench-enabled ATM block subclass (instantiated only when IC2 is loaded). */
    public static final class Ic2AtmMachineBlock extends AtmMachineBlock implements WrenchTarget {
        @Override
        public boolean wrenchUsesLoot() {
            return true;
        }
    }

    /** IC2 EU-enabled Minting Machine BlockEntity subclass (instantiated only when IC2 is loaded). */
    public static final class Ic2MintMachineBlockEntity extends MintMachineBlockEntity
            implements WorldPowerPort, EuBuffer {

        public Ic2MintMachineBlockEntity(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public boolean ranThisTick(ServerLevel level) {
            return didRunThisTick(level);
        }

        @Override
        public EnergyGraph.Ports energyPorts() {
            return ALL_FACE_SINK;
        }

        @Override
        public double packetLimit() {
            return getMaxInputEuPerTick();
        }

        @Override
        public double receive(double offered) {
            return receiveEu(offered);
        }

        @Override
        public EuBuffer.Reading bufferedEu() {
            return new EuBuffer.Reading(getStoredEu(), getMaxCapacityEu());
        }
    }

    /** IC2 EU-enabled ATM BlockEntity subclass (instantiated only when IC2 is loaded). */
    public static final class Ic2AtmMachineBlockEntity extends AtmMachineBlockEntity
            implements WorldPowerPort, EuBuffer {

        public Ic2AtmMachineBlockEntity(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public boolean ranThisTick(ServerLevel level) {
            return didRunThisTick(level);
        }

        @Override
        public EnergyGraph.Ports energyPorts() {
            return ALL_FACE_SINK;
        }

        @Override
        public double packetLimit() {
            return TriModePowerMath.ATM_BASE_MAX_INPUT_EU_PER_TICK;
        }

        @Override
        public double receive(double offered) {
            return receiveEu(offered);
        }

        @Override
        public EuBuffer.Reading bufferedEu() {
            return new EuBuffer.Reading(getStoredEu(), TriModePowerMath.ATM_BASE_CAPACITY_EU);
        }
    }
}
