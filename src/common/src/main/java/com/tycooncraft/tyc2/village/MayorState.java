package com.tycooncraft.tyc2.village;

import java.util.Objects;
import java.util.UUID;

/**
 * Mutable per-village Mayor lifecycle state persisted inside {@link VillageRecord}.
 * Tracks the currently living Mayor entity, re-election status, and depopulation pause state.
 */
public final class MayorState {

    private UUID mayorEntityUuid;
    private String mayorName;
    private long electedTick;
    private boolean reelectionPending;
    private boolean pausedDueToDepopulation;
    private int lastKnownVillagerCount;
    private long lastElectionAttemptTick;
    private int electionsHeldCount;

    /**
     * Creates a fresh empty {@link MayorState} with no living Mayor elected.
     */
    public MayorState() {
        this.mayorEntityUuid = null;
        this.mayorName = "";
        this.electedTick = 0L;
        this.reelectionPending = false;
        this.pausedDueToDepopulation = false;
        this.lastKnownVillagerCount = 0;
        this.lastElectionAttemptTick = 0L;
        this.electionsHeldCount = 0;
    }

    /**
     * Creates a {@link MayorState} restored from persisted storage.
     *
     * @param mayorEntityUuid UUID of the living Mayor entity, or null if none
     * @param mayorName display name of the Mayor (null normalized to empty string)
     * @param electedTick game tick when the current Mayor was elected
     * @param reelectionPending whether a re-election is queued
     * @param pausedDueToDepopulation whether order generation is paused due to zero villagers
     * @param lastKnownVillagerCount last observed living villager count
     * @param lastElectionAttemptTick game tick of the most recent election evaluation
     * @param electionsHeldCount total number of Mayor elections completed in this village
     */
    public MayorState(
            UUID mayorEntityUuid,
            String mayorName,
            long electedTick,
            boolean reelectionPending,
            boolean pausedDueToDepopulation,
            int lastKnownVillagerCount,
            long lastElectionAttemptTick,
            int electionsHeldCount) {
        this.mayorEntityUuid = mayorEntityUuid;
        this.mayorName = mayorName == null ? "" : mayorName;
        this.electedTick = Math.max(0L, electedTick);
        this.reelectionPending = reelectionPending;
        this.pausedDueToDepopulation = pausedDueToDepopulation;
        this.lastKnownVillagerCount = Math.max(0, lastKnownVillagerCount);
        this.lastElectionAttemptTick = Math.max(0L, lastElectionAttemptTick);
        this.electionsHeldCount = Math.max(0, electionsHeldCount);
    }

    /**
     * Returns whether a Mayor entity UUID is currently assigned.
     *
     * @return {@code true} if a Mayor is currently elected
     */
    public boolean hasLivingMayor() {
        return mayorEntityUuid != null;
    }

    /**
     * Returns the UUID of the current Mayor entity, or {@code null} if none.
     *
     * @return Mayor entity UUID or null
     */
    public UUID getMayorEntityUuid() {
        return mayorEntityUuid;
    }

    /**
     * Sets the current Mayor entity UUID.
     *
     * @param mayorEntityUuid entity UUID or null
     */
    public void setMayorEntityUuid(UUID mayorEntityUuid) {
        this.mayorEntityUuid = mayorEntityUuid;
    }

    /**
     * Returns the display name of the current Mayor (empty string if none).
     *
     * @return Mayor display name
     */
    public String getMayorName() {
        return mayorName;
    }

    /**
     * Sets the display name of the current Mayor.
     *
     * @param mayorName display name (null normalized to empty string)
     */
    public void setMayorName(String mayorName) {
        this.mayorName = mayorName == null ? "" : mayorName;
    }

    /**
     * Returns the tick at which the current Mayor was elected.
     *
     * @return elected game tick
     */
    public long getElectedTick() {
        return electedTick;
    }

    /**
     * Sets the tick at which the current Mayor was elected.
     *
     * @param electedTick game tick
     */
    public void setElectedTick(long electedTick) {
        this.electedTick = Math.max(0L, electedTick);
    }

    /**
     * Returns whether a Mayor re-election is currently pending.
     *
     * @return {@code true} if re-election is pending
     */
    public boolean isReelectionPending() {
        return reelectionPending;
    }

    /**
     * Sets the re-election pending flag.
     *
     * @param reelectionPending pending flag
     */
    public void setReelectionPending(boolean reelectionPending) {
        this.reelectionPending = reelectionPending;
    }

    /**
     * Returns whether trade order generation is paused because the village became depopulated.
     *
     * @return {@code true} when paused due to depopulation
     */
    public boolean isPausedDueToDepopulation() {
        return pausedDueToDepopulation;
    }

    /**
     * Sets the depopulation pause flag.
     *
     * @param pausedDueToDepopulation pause flag
     */
    public void setPausedDueToDepopulation(boolean pausedDueToDepopulation) {
        this.pausedDueToDepopulation = pausedDueToDepopulation;
    }

    /**
     * Returns the last observed living villager count in this village.
     *
     * @return living villager count
     */
    public int getLastKnownVillagerCount() {
        return lastKnownVillagerCount;
    }

    /**
     * Updates the last observed living villager count.
     *
     * @param lastKnownVillagerCount living villager count (clamped to {@code >= 0})
     */
    public void setLastKnownVillagerCount(int lastKnownVillagerCount) {
        this.lastKnownVillagerCount = Math.max(0, lastKnownVillagerCount);
    }

    /**
     * Returns the game tick of the last election evaluation.
     *
     * @return tick timestamp
     */
    public long getLastElectionAttemptTick() {
        return lastElectionAttemptTick;
    }

    /**
     * Sets the game tick of the last election evaluation.
     *
     * @param lastElectionAttemptTick tick timestamp
     */
    public void setLastElectionAttemptTick(long lastElectionAttemptTick) {
        this.lastElectionAttemptTick = Math.max(0L, lastElectionAttemptTick);
    }

    /**
     * Returns how many elections have successfully elected a Mayor in this village.
     *
     * @return completed election count
     */
    public int getElectionsHeldCount() {
        return electionsHeldCount;
    }

    /**
     * Sets the total completed election count.
     *
     * @param electionsHeldCount count (clamped to {@code >= 0})
     */
    public void setElectionsHeldCount(int electionsHeldCount) {
        this.electionsHeldCount = Math.max(0, electionsHeldCount);
    }

    /**
     * Clears the active Mayor assignment without altering partnership or pause state.
     */
    public void clearMayor() {
        this.mayorEntityUuid = null;
        this.mayorName = "";
    }

    /**
     * Assigns a newly elected Mayor and increments the election counter.
     *
     * @param entityUuid elected villager UUID
     * @param displayName elected villager name
     * @param tick game tick of election
     */
    public void assignMayor(UUID entityUuid, String displayName, long tick) {
        this.mayorEntityUuid = Objects.requireNonNull(entityUuid, "entityUuid");
        this.mayorName = displayName == null ? "" : displayName;
        this.electedTick = Math.max(0L, tick);
        this.reelectionPending = false;
        this.pausedDueToDepopulation = false;
        this.electionsHeldCount++;
    }

    /**
     * Creates a deep copy of this {@link MayorState}.
     *
     * @return independent copy
     */
    public MayorState copy() {
        return new MayorState(
                mayorEntityUuid,
                mayorName,
                electedTick,
                reelectionPending,
                pausedDueToDepopulation,
                lastKnownVillagerCount,
                lastElectionAttemptTick,
                electionsHeldCount);
    }
}
