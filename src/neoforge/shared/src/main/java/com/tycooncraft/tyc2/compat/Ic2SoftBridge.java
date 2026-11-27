package com.tycooncraft.tyc2.compat;

import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.gameplay.EnergyNetwork;
import com.industrialage.ia2.gameplay.PortableGear;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldPowerPort;
import com.industrialage.ia2.world.MachineAutomation;
import com.tycooncraft.tyc2.blockentity.TyC2EuSink;
import com.tycooncraft.tyc2.core.CommonTagAliases;
import com.tycooncraft.tyc2.platform.TyC2Native;
import java.lang.reflect.Method;
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
import net.minecraft.world.entity.EquipmentSlot;
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

    /** Resolves configured ejector faces from installed IC2 ejector or advanced ejector upgrades. */
    public static List<Direction> resolveEjectorFaces(List<ItemStack> upgrades) {
        if (!isIc2Loaded() || upgrades == null || upgrades.isEmpty()) {
            return List.of();
        }
        return Loaded.resolveFaces(upgrades, "ic2:ejector_upgrade", "ic2:advanced_ejector_upgrade");
    }

    /** Resolves configured pulling faces from installed IC2 pulling or advanced pulling upgrades. */
    public static List<Direction> resolvePullingFaces(List<ItemStack> upgrades) {
        if (!isIc2Loaded() || upgrades == null || upgrades.isEmpty()) {
            return List.of();
        }
        return Loaded.resolveFaces(upgrades, "ic2:pulling_upgrade", "ic2:advanced_pulling_upgrade");
    }

    /** Returns {@code true} if {@code candidate} passes installed ejector/pulling upgrade filters. */
    public static boolean matchesUpgradeFilter(List<ItemStack> upgrades, boolean ejector, ItemStack candidate) {
        if (!isIc2Loaded() || upgrades == null || upgrades.isEmpty() || candidate == null || candidate.isEmpty()) {
            return true;
        }
        return Loaded.matchesFilter(
                upgrades,
                ejector ? "ic2:ejector_upgrade" : "ic2:pulling_upgrade",
                ejector ? "ic2:advanced_ejector_upgrade" : "ic2:advanced_pulling_upgrade",
                candidate
        );
    }

    /**
     * Handles IC2 Wrench / Electric Wrench and any standard common-tagged wrench
     * ({@code #c:tools/wrench}, {@code #c:wrenches}, {@code #forge:tools/wrench}) right-click
     * rotation and shift-right-click dismantling on TyC2 machines, properly deducting wrench
     * durability or electric wrench EU charge in Survival mode. Safe even if IC2 is absent.
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
        boolean isElectric = "ic2:electric_wrench".equals(id);
        boolean isWrench = isElectric
                || "ic2:wrench".equals(id)
                || CommonTagAliases.isWrenchTag(id, TyC2Native::itemIdHasTag);
        if (!isWrench) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        boolean creative = player != null && player.isCreative();
        EquipmentSlot slot = (player != null && player.getOffhandItem() == held)
                ? EquipmentSlot.OFFHAND
                : EquipmentSlot.MAINHAND;
        if (player != null && player.isShiftKeyDown()) {
            if (!consumeWrenchCost(held, isElectric, creative, player, slot)) {
                return false;
            }
            level.destroyBlock(pos, true, player);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.1f);
            return true;
        }
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            if (!consumeWrenchCost(held, isElectric, creative, player, slot)) {
                return false;
            }
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

    private static boolean consumeWrenchCost(
            ItemStack held,
            boolean isElectric,
            boolean creative,
            Player player,
            EquipmentSlot slot
    ) {
        if (creative) {
            return true;
        }
        if (isElectric) {
            return isIc2Loaded() && Loaded.spendElectricWrench(held, 100.0);
        }
        if (player != null && held.isDamageableItem()) {
            held.hurtAndBreak(1, player, slot);
        }
        return true;
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

        private static volatile Method automationMatchesFilterMethod;
        private static volatile boolean automationMatchesFilterResolved;
        private static volatile Method automationSettingsMethod;
        private static volatile Method settingsMatchesMethod;

        private static final class EuSinkAdapter implements WorldPowerPort, EuBuffer {
            private final TyC2EuSink sink;

            private EuSinkAdapter(TyC2EuSink sink) {
                this.sink = sink;
            }

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

            @Override
            public EuBuffer.Reading bufferedEu() {
                return new EuBuffer.Reading(sink.storedEu(), sink.maxCapacityEu());
            }
        }

        private static WorldPowerPort wrapSink(TyC2EuSink sink) {
            return new EuSinkAdapter(sink);
        }

        private static boolean spendElectricWrench(ItemStack stack, double eu) {
            return PortableGear.spend(stack, eu);
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

        private static List<Direction> resolveFaces(
                List<ItemStack> upgrades,
                String basicUpgradeId,
                String advancedUpgradeId
        ) {
            Set<Direction> result = new LinkedHashSet<>();
            for (ItemStack s : upgrades) {
                if (s == null || s.isEmpty()) {
                    continue;
                }
                String id = TyC2Native.itemId(s);
                if (basicUpgradeId.equals(id) || advancedUpgradeId.equals(id)) {
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

        private static boolean matchesFilter(
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
                String id = TyC2Native.itemId(s);
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
    }
}
