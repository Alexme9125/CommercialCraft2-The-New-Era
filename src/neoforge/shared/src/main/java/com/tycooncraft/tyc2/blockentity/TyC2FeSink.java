package com.tycooncraft.tyc2.blockentity;

/**
 * Version-neutral Forge/NeoForge FE energy sink interface implemented by
 * {@code MintMachineBlockEntity} and {@code AtmMachineBlockEntity} and adapted to
 * {@code Capabilities.EnergyStorage.BLOCK} (1.21.1) or {@code Capabilities.Energy.BLOCK} (26.x).
 */
public interface TyC2FeSink {

    /** Returns current stored energy in FE (1 EU = 4 FE). */
    int getStoredFe();

    /** Returns maximum energy capacity in FE. */
    int getMaxFe();

    /** Returns maximum FE accepted per tick. */
    int getMaxReceiveFePerTick();

    /**
     * Inserts up to {@code maxReceiveFe} FE into this machine.
     *
     * @param maxReceiveFe maximum FE offered
     * @param simulate if {@code true}, computes accepted FE without mutating state
     * @return accepted FE (>= 0)
     */
    int receiveFe(int maxReceiveFe, boolean simulate);

    /** Restores stored FE directly (used for 26.x transactional rollback). */
    void setStoredFeForRollback(int fe);
}
