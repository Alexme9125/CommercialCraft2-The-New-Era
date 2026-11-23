package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.forge.block.AtmMachineBlock;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlock;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.compat.ic2.Ic2LoadedBridge;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * Zero-hard-dependency facade isolating all IndustrialCraft² ({@code ic2}) interactions.
 *
 * <p>This class intentionally imports ZERO {@code com.industrialage.*} classes; it only
 * loads {@link Ic2LoadedBridge} when {@code ModList.get().isLoaded("ic2")} is {@code true}.
 */
public final class Ic2Bridge {

    public static final String MOD_ID = "ic2";

    private Ic2Bridge() {
    }

    /** Returns {@code true} if IndustrialCraft² ({@code ic2}) is loaded at runtime. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Instantiates the Minting Machine block (with IC2 {@code WrenchTarget} when IC2 is loaded). */
    public static Block createMintBlock() {
        if (isLoaded()) {
            return Ic2LoadedBridge.createMintBlock();
        }
        return new MintMachineBlock();
    }

    /** Instantiates the Industrial ATM block (with IC2 {@code WrenchTarget} when IC2 is loaded). */
    public static Block createAtmBlock() {
        if (isLoaded()) {
            return Ic2LoadedBridge.createAtmBlock();
        }
        return new AtmMachineBlock();
    }

    /** Instantiates the Minting Machine BE (with IC2 {@code WorldPowerPort}/{@code EuBuffer} when loaded). */
    public static MintMachineBlockEntity createMintBlockEntity(BlockPos pos, BlockState state) {
        if (isLoaded()) {
            return Ic2LoadedBridge.createMintBlockEntity(pos, state);
        }
        return new MintMachineBlockEntity(pos, state);
    }

    /** Instantiates the ATM BE (with IC2 {@code WorldPowerPort}/{@code EuBuffer} when loaded). */
    public static AtmMachineBlockEntity createAtmBlockEntity(BlockPos pos, BlockState state) {
        if (isLoaded()) {
            return Ic2LoadedBridge.createAtmBlockEntity(pos, state);
        }
        return new AtmMachineBlockEntity(pos, state);
    }

    /** Connects an IC2-capable BlockEntity to the IC2 EU energy grid when IC2 is loaded. */
    public static void connectPower(BlockEntity be) {
        if (isLoaded() && be != null) {
            Ic2LoadedBridge.connectPower(be);
        }
    }

    /** Disconnects an IC2-capable BlockEntity from the IC2 EU energy grid when IC2 is loaded. */
    public static void disconnectPower(BlockEntity be) {
        if (isLoaded() && be != null) {
            Ic2LoadedBridge.disconnectPower(be);
        }
    }

    /** Checks whether {@code stack} is an IC2 dischargeable battery at or below {@code tier}. */
    public static boolean canDischargeBattery(ItemStack stack, int tier) {
        if (!isLoaded() || stack.isEmpty()) {
            return false;
        }
        return Ic2LoadedBridge.canDischargeBattery(stack, tier);
    }

    /** Discharges up to {@code neededEu} from an IC2 battery stack, returning actual EU drawn. */
    public static double dischargeBattery(ItemStack stack, double neededEu, int tier) {
        if (!isLoaded() || stack.isEmpty() || neededEu <= 0.0) {
            return 0.0;
        }
        return Ic2LoadedBridge.dischargeBattery(stack, neededEu, tier);
    }

    /**
     * Discharges an IC2 battery stack given the machine's full remaining buffer space and
     * capacity (required by IC2 disposable batteries like {@code ic2:single_use_battery} which
     * only discharge when {@code freeSpaceEu >= 1200}; rechargeable batteries clamp to their own
     * transfer rate internally).
     */
    public static double dischargeBattery(ItemStack stack, double freeSpaceEu, double maxCapacityEu, int tier) {
        if (!isLoaded() || stack.isEmpty() || freeSpaceEu <= 0.0) {
            return 0.0;
        }
        return Ic2LoadedBridge.dischargeBattery(stack, freeSpaceEu, maxCapacityEu, tier);
    }

    /** Resolves configured faces on an IC2 ejector/pulling upgrade stack (or all faces if IC2 absent). */
    public static List<Direction> resolveUpgradeFaces(ItemStack stack) {
        if (isLoaded() && !stack.isEmpty()) {
            return Ic2LoadedBridge.resolveUpgradeFaces(stack);
        }
        return Arrays.asList(Direction.values());
    }
}
