package com.tycooncraft.tyc2.compat;

import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.gameplay.EnergyNetwork;
import com.industrialage.ia2.gameplay.PortableGear;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.power.WorldPowerPort;
import com.industrialage.ia2.world.MachineAutomation;
import com.tycooncraft.tyc2.blockentity.TyC2EuSink;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Isolated soft-dependency bridge to IndustrialCraft 2 ({@code ic2}).
 *
 * <p>ALL direct references to {@code com.industrialage.ia2.*} live exclusively inside the private
 * nested class {@link Loaded}, which the JVM never loads unless {@link #isIc2Loaded()} returns
 * {@code true}. This guarantees TyC2 runs cleanly without IC2 installed.
 */
public final class Ic2SoftBridge {

    private static Boolean cachedLoaded;

    private Ic2SoftBridge() {
    }

    /** Returns {@code true} if {@code ic2} is loaded at runtime. */
    public static boolean isIc2Loaded() {
        if (cachedLoaded == null) {
            try {
                cachedLoaded = ModList.get() != null && ModList.get().isLoaded("ic2");
            } catch (Throwable ignored) {
                cachedLoaded = false;
            }
        }
        return cachedLoaded;
    }

    /** Registers a {@link TyC2EuSink} on IC2's {@code EnergyNetwork} if IC2 is loaded. */
    public static void connectEuSink(ServerLevel level, TyC2EuSink sink) {
        if (isIc2Loaded() && level != null && sink != null) {
            Loaded.connect(level, sink);
        }
    }

    /** Unregisters a {@link TyC2EuSink} from IC2's {@code EnergyNetwork} if IC2 is loaded. */
    public static void disconnectEuSink(ServerLevel level, TyC2EuSink sink) {
        if (isIc2Loaded() && level != null && sink != null) {
            Loaded.disconnect(level, sink);
        }
    }

    /** Returns {@code true} if {@code stack} is an IC2 portable battery dischargeable at {@code tier}. */
    public static boolean canDischargeBattery(ItemStack stack, int tier) {
        if (!isIc2Loaded() || stack == null || stack.isEmpty()) {
            return false;
        }
        return Loaded.canDischarge(stack, tier);
    }

    /** Discharges up to {@code requestedEu} from an IC2 battery stack, returning accepted EU. */
    public static double dischargeBattery(ItemStack stack, double requestedEu, int tier) {
        if (!isIc2Loaded() || stack == null || stack.isEmpty() || requestedEu <= 0.0) {
            return 0.0;
        }
        return Loaded.discharge(stack, requestedEu, requestedEu, tier);
    }

    /**
     * Discharges an IC2 battery (rechargeable or {@code ic2:single_use_battery}) using the
     * machine's full free buffer room and total capacity so single-use 1,200 EU batteries
     * discharge cleanly whenever {@code freeSpaceEu >= 1200} while rechargeable batteries
     * transfer at their own IC2 tier rate.
     */
    public static double dischargeBattery(
            ItemStack stack,
            double freeSpaceEu,
            double machineCapacityEu,
            int tier
    ) {
        if (!isIc2Loaded() || stack == null || stack.isEmpty() || freeSpaceEu <= 0.0) {
            return 0.0;
        }
        return Loaded.discharge(stack, freeSpaceEu, Math.max(freeSpaceEu, machineCapacityEu), tier);
    }

    /** Resolves configured ejector faces from installed IC2 ejector upgrades. */
    public static List<Direction> resolveEjectorFaces(List<ItemStack> upgrades) {
        if (!isIc2Loaded() || upgrades == null || upgrades.isEmpty()) {
            return List.of();
        }
        return Loaded.resolveFaces(upgrades, "ic2:ejector_upgrade");
    }

    /** Resolves configured pulling faces from installed IC2 pulling upgrades. */
    public static List<Direction> resolvePullingFaces(List<ItemStack> upgrades) {
        if (!isIc2Loaded() || upgrades == null || upgrades.isEmpty()) {
            return List.of();
        }
        return Loaded.resolveFaces(upgrades, "ic2:pulling_upgrade");
    }

    /**
     * Handles IC2 Wrench / Electric Wrench right-click rotation and shift-right-click dismantling
     * on TyC2 machines (Minting Machine &amp; Industrial ATM). Safe even if IC2 is absent.
     */
    public static boolean handleWrenchInteraction(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player,
            ItemStack held,
            Direction hitFace
    ) {
        if (held == null || held.isEmpty()) {
            return false;
        }
        String id = TyC2Native.itemId(held);
        if (!"ic2:wrench".equals(id) && !"ic2:electric_wrench".equals(id)) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        if (player.isShiftKeyDown()) {
            level.destroyBlock(pos, true, player);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.1f);
            return true;
        }
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction target = hitFace.getAxis().isHorizontal()
                    ? hitFace
                    : state.getValue(HorizontalDirectionalBlock.FACING).getClockWise();
            if (target == state.getValue(HorizontalDirectionalBlock.FACING)) {
                target = target.getClockWise();
            }
            level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, target), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
            return true;
        }
        return false;
    }

    /**
     * Lazy inner holder loaded ONLY when {@code ic2} is confirmed present in {@link ModList}.
     */
    private static final class Loaded {

        private static final EnergyGraph.Ports ALL_SINK_PORTS = new EnergyGraph.Ports(
                EnumSet.allOf(CapabilityFacade.Face.class),
                Set.of(),
                false
        );

        private static final Map<TyC2EuSink, WorldPowerPort> PORTS =
                Collections.synchronizedMap(new WeakHashMap<>());

        private static void connect(ServerLevel level, TyC2EuSink sink) {
            WorldPowerPort port = PORTS.computeIfAbsent(sink, Loaded::wrapSink);
            EnergyNetwork.load(level, port);
        }

        private static void disconnect(ServerLevel level, TyC2EuSink sink) {
            WorldPowerPort port = PORTS.remove(sink);
            if (port != null) {
                EnergyNetwork.unload(level, port);
            }
        }

        private static WorldPowerPort wrapSink(TyC2EuSink sink) {
            return new WorldPowerPort() {
                @Override
                public BlockPos getBlockPos() {
                    return sink.getBlockPos();
                }

                @Override
                public boolean isRemoved() {
                    return sink.isRemoved();
                }

                @Override
                public boolean ranThisTick(ServerLevel level) {
                    return sink.ranThisTick(level);
                }

                @Override
                public EnergyGraph.Ports energyPorts() {
                    return ALL_SINK_PORTS;
                }

                @Override
                public boolean source() {
                    return false;
                }

                @Override
                public double receive(double offered) {
                    return sink.receiveEu(offered);
                }

                @Override
                public double packetLimit() {
                    return sink.packetLimitEu();
                }
            };
        }

        private static boolean canDischarge(ItemStack stack, int tier) {
            return PortableGear.canDischarge(stack, Math.max(1, tier));
        }

        private static double discharge(ItemStack stack, double freeSpaceEu, double capacityEu, int tier) {
            int clampedTier = Math.max(1, tier);
            if (!PortableGear.canDischarge(stack, clampedTier)) {
                return 0.0;
            }
            return PortableGear.discharge(stack, freeSpaceEu, capacityEu, clampedTier);
        }

        private static List<Direction> resolveFaces(List<ItemStack> upgrades, String targetUpgradeId) {
            Set<Direction> result = new LinkedHashSet<>();
            for (ItemStack s : upgrades) {
                if (s == null || s.isEmpty()) {
                    continue;
                }
                if (targetUpgradeId.equals(TyC2Native.itemId(s))) {
                    List<Direction> configured = MachineAutomation.faces(s);
                    if (configured != null && !configured.isEmpty()) {
                        result.addAll(configured);
                    } else {
                        Collections.addAll(result, Direction.values());
                    }
                }
            }
            return new ArrayList<>(result);
        }
    }
}
