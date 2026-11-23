package com.tycooncraft.tyc2.village;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure-Java Mayor election, death, and depopulation lifecycle rules.
 *
 * <ul>
 *   <li>Never elects or spawns a Mayor when {@code villagerCount <= 1}.</li>
 *   <li>Elects the highest-XP, oldest adult non-nitwit candidate (falling back to any living adult)
 *       when {@code villagerCount >= 2} and no living Mayor exists.</li>
 *   <li>On Mayor death, clears the Mayor and marks re-election pending.</li>
 *   <li>On village depopulation ({@code villagerCount == 0}), pauses order generation while
 *       preserving partnership state, resuming and re-electing automatically once {@code >= 2}
 *       villagers return.</li>
 * </ul>
 */
public final class MayorElectionRules {

    /** Minimum living villager population required to elect a Mayor or resume after depopulation. */
    public static final int MIN_VILLAGERS_FOR_MAYOR = 2;

    /**
     * Snapshot of a villager candidate considered during an election scan.
     *
     * @param entityUuid villager entity UUID
     * @param displayName villager display name
     * @param adult {@code true} if the villager is not a baby
     * @param nitwit {@code true} if the villager has the nitwit profession
     * @param alive {@code true} if the villager is currently alive
     * @param villagerXp villager trade experience points
     * @param ageTicks villager age / existence ticks (older preferred on tie)
     */
    public record MayorCandidate(
            UUID entityUuid,
            String displayName,
            boolean adult,
            boolean nitwit,
            boolean alive,
            int villagerXp,
            int ageTicks) {

        /**
         * Validates candidate fields.
         */
        public MayorCandidate {
            Objects.requireNonNull(entityUuid, "entityUuid");
            displayName = displayName == null ? "" : displayName;
        }
    }

    /**
     * Outcome category of a Mayor lifecycle evaluation.
     */
    public enum Action {
        /** Existing living Mayor remains valid; no state change required. */
        NO_CHANGE,
        /** A new Mayor was elected for an active village. */
        ELECTED_NEW_MAYOR,
        /** The previous Mayor died or vanished and re-election is pending. */
        MAYOR_LOST_PENDING_REELECTION,
        /** Village reached 0 villagers; order generation paused while partnership is preserved. */
        PAUSED_DEPOPULATED,
        /** Depopulated village recovered to {@code >= 2} villagers and elected a new Mayor. */
        RESUMED_AND_ELECTED,
        /** Village reached {@code >= 2} villagers but no living adult candidate is available yet. */
        WAITING_FOR_ADULT_CANDIDATE,
        /** Population is {@code <= 1}; no Mayor may be elected or spawned. */
        INSUFFICIENT_POPULATION
    }

    /**
     * Result returned by {@link #evaluate(MayorState, int, Collection, long)} and death handlers.
     *
     * @param action lifecycle action taken
     * @param mayorUuid current living Mayor UUID after evaluation, or null
     * @param mayorName current living Mayor name after evaluation (empty string if none)
     * @param orderGenerationAllowed whether the village may currently generate new trade orders
     */
    public record ElectionOutcome(
            Action action,
            UUID mayorUuid,
            String mayorName,
            boolean orderGenerationAllowed) {
    }

    private static final Comparator<MayorCandidate> CANDIDATE_RANKING = Comparator
            .comparing(MayorCandidate::nitwit) // false (non-nitwit) sorts before true (nitwit)
            .thenComparing(Comparator.comparingInt(MayorCandidate::villagerXp).reversed())
            .thenComparing(Comparator.comparingInt(MayorCandidate::ageTicks).reversed())
            .thenComparing(c -> c.entityUuid().toString());

    private MayorElectionRules() {
    }

    /**
     * Checks whether trade order generation is currently permitted for the given {@link MayorState}.
     * Partnership state on {@link VillageRecord} is preserved across depopulation pauses.
     *
     * @param state village Mayor state
     * @return {@code true} if not paused due to depopulation
     */
    public static boolean isOrderGenerationAllowed(MayorState state) {
        Objects.requireNonNull(state, "state");
        return !state.isPausedDueToDepopulation();
    }

    /**
     * Selects the best eligible living adult candidate, strictly prioritizing non-nitwits,
     * then highest villager XP, then oldest age, then deterministic UUID ordering.
     *
     * @param candidates scanned village candidates
     * @return best eligible candidate, or empty if no living adult exists
     */
    public static Optional<MayorCandidate> selectBestCandidate(Collection<MayorCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(MayorCandidate::alive)
                .filter(MayorCandidate::adult)
                .min(CANDIDATE_RANKING);
    }

    /**
     * Evaluates the village's current population and candidate list, updating {@code state}
     * according to TycoonCraft² Mayor rules.
     *
     * @param state mutable Mayor state to update
     * @param villagerCount total living villagers currently in the village
     * @param candidates living villager candidates scanned in the village
     * @param currentTick current server game tick
     * @return structured election outcome
     */
    public static ElectionOutcome evaluate(
            MayorState state,
            int villagerCount,
            Collection<MayorCandidate> candidates,
            long currentTick) {
        Objects.requireNonNull(state, "state");
        int livingCount = Math.max(0, villagerCount);
        state.setLastKnownVillagerCount(livingCount);
        state.setLastElectionAttemptTick(currentTick);

        if (livingCount == 0) {
            state.clearMayor();
            state.setPausedDueToDepopulation(true);
            state.setReelectionPending(true);
            return new ElectionOutcome(Action.PAUSED_DEPOPULATED, null, "", false);
        }

        boolean currentMayorAlive = isCurrentMayorAlive(state.getMayorEntityUuid(), candidates);

        if (livingCount < MIN_VILLAGERS_FOR_MAYOR) {
            // Never elect or spawn a Mayor when villagerCount <= 1.
            if (!currentMayorAlive) {
                state.clearMayor();
                state.setReelectionPending(true);
            }
            // If paused due to prior depopulation, remain paused until >= 2 villagers return.
            boolean ordersAllowed = isOrderGenerationAllowed(state);
            return new ElectionOutcome(
                    Action.INSUFFICIENT_POPULATION,
                    state.getMayorEntityUuid(),
                    state.getMayorName(),
                    ordersAllowed);
        }

        // livingCount >= 2
        boolean wasPaused = state.isPausedDueToDepopulation();

        if (currentMayorAlive && !wasPaused) {
            state.setReelectionPending(false);
            return new ElectionOutcome(
                    Action.NO_CHANGE,
                    state.getMayorEntityUuid(),
                    state.getMayorName(),
                    true);
        }

        if (!currentMayorAlive) {
            state.clearMayor();
            state.setReelectionPending(true);
        }

        Optional<MayorCandidate> chosen = selectBestCandidate(candidates);
        if (chosen.isEmpty()) {
            state.setReelectionPending(true);
            return new ElectionOutcome(
                    Action.WAITING_FOR_ADULT_CANDIDATE,
                    null,
                    "",
                    !wasPaused);
        }

        MayorCandidate winner = chosen.get();
        state.assignMayor(winner.entityUuid(), winner.displayName(), currentTick);
        Action action = wasPaused ? Action.RESUMED_AND_ELECTED : Action.ELECTED_NEW_MAYOR;
        return new ElectionOutcome(action, winner.entityUuid(), winner.displayName(), true);
    }

    /**
     * Records the death of an entity, clearing the Mayor and marking re-election pending if the
     * deceased entity was the current Mayor.
     *
     * @param state mutable Mayor state
     * @param deadEntityUuid UUID of the villager that died
     * @param currentTick current game tick
     * @return {@code true} if the deceased entity was the village's Mayor
     */
    public static boolean onMayorDeath(MayorState state, UUID deadEntityUuid, long currentTick) {
        Objects.requireNonNull(state, "state");
        if (deadEntityUuid == null || !deadEntityUuid.equals(state.getMayorEntityUuid())) {
            return false;
        }
        state.clearMayor();
        state.setReelectionPending(true);
        state.setLastElectionAttemptTick(currentTick);
        return true;
    }

    /**
     * Handles Mayor death and immediately evaluates whether to re-elect, wait for more villagers,
     * or pause due to depopulation.
     *
     * @param state mutable Mayor state
     * @param deadEntityUuid UUID of the villager that died
     * @param remainingVillagerCount number of living villagers remaining after this death
     * @param remainingCandidates remaining living villager candidates (excluding the dead villager)
     * @param currentTick current game tick
     * @return election outcome after death processing
     */
    public static ElectionOutcome onMayorDeathAndReelect(
            MayorState state,
            UUID deadEntityUuid,
            int remainingVillagerCount,
            Collection<MayorCandidate> remainingCandidates,
            long currentTick) {
        Objects.requireNonNull(state, "state");
        boolean wasMayor = onMayorDeath(state, deadEntityUuid, currentTick);
        if (!wasMayor) {
            return new ElectionOutcome(
                    Action.NO_CHANGE,
                    state.getMayorEntityUuid(),
                    state.getMayorName(),
                    isOrderGenerationAllowed(state));
        }

        int remaining = Math.max(0, remainingVillagerCount);
        state.setLastKnownVillagerCount(remaining);
        if (remaining == 0) {
            state.setPausedDueToDepopulation(true);
            state.setReelectionPending(true);
            return new ElectionOutcome(Action.PAUSED_DEPOPULATED, null, "", false);
        }
        if (remaining < MIN_VILLAGERS_FOR_MAYOR) {
            return new ElectionOutcome(
                    Action.MAYOR_LOST_PENDING_REELECTION,
                    null,
                    "",
                    isOrderGenerationAllowed(state));
        }
        return evaluate(state, remaining, remainingCandidates, currentTick);
    }

    private static boolean isCurrentMayorAlive(UUID mayorUuid, Collection<MayorCandidate> candidates) {
        if (mayorUuid == null || candidates == null) {
            return false;
        }
        for (MayorCandidate candidate : candidates) {
            if (candidate != null && candidate.alive() && mayorUuid.equals(candidate.entityUuid())) {
                return true;
            }
        }
        return false;
    }
}
