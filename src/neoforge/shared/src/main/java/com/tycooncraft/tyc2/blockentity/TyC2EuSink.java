package com.tycooncraft.tyc2.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Internal pure-TyC2 EU power sink interface adapted to IC2's {@code WorldPowerPort}
 * exclusively inside {@code Ic2SoftBridge} when {@code ic2} is loaded.
 *
 * <p>Guarantees zero IC2 class references on BlockEntity class signatures!
 */
public interface TyC2EuSink {

    /** Returns the world block position of this sink. */
    BlockPos getBlockPos();

    /** Returns {@code true} if the backing block entity has been removed. */
    boolean isRemoved();

    /** Returns {@code true} if this block entity ticked on the current server tick. */
    boolean ranThisTick(ServerLevel level);

    /** Accepts up to {@code offeredEu} EU from an IC2 network packet, returning accepted EU. */
    double receiveEu(double offeredEu);

    /** Returns the maximum EU packet limit accepted per tick (e.g. 32 LV, 128 MV, ...). */
    double packetLimitEu();

    /** Returns current stored EU. */
    double storedEu();

    /** Returns maximum EU capacity. */
    double maxCapacityEu();
}
