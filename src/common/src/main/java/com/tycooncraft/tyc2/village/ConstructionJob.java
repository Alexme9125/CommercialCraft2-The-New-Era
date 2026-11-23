package com.tycooncraft.tyc2.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stateful gradual construction job that places a planned structure's blocks bottom-up
 * at a throttled cadence (default {@value #DEFAULT_BLOCKS_PER_STEP} blocks every
 * {@value #DEFAULT_TICKS_PER_STEP} ticks) to prevent tick spikes, emitting post-construction
 * villager, livestock, and LC2 pad spawn markers upon completion.
 */
public final class ConstructionJob {

    /** Default number of blocks placed per construction step. */
    public static final int DEFAULT_BLOCKS_PER_STEP = 2;

    /** Default server tick interval between construction steps. */
    public static final int DEFAULT_TICKS_PER_STEP = 3;

    /**
     * Result produced by a single call to {@link #step()} or {@link #tick()}.
     *
     * @param placedBlocks ordered list of blocks to set in the world during this step (may be empty)
     * @param completedNow {@code true} only on the exact step that finishes the job
     * @param spawnMarkersToTrigger post-construction markers to spawn when {@code completedNow} is {@code true}
     * @param placedTotal cumulative blocks placed so far
     * @param totalBlocks total blocks in this construction job
     */
    public record StepResult(
            List<WorldBlockPlacement> placedBlocks,
            boolean completedNow,
            List<SpawnMarker.WorldSpawnMarker> spawnMarkersToTrigger,
            int placedTotal,
            int totalBlocks) {
        /**
         * Defensively wraps lists as unmodifiable.
         */
        public StepResult {
            placedBlocks = List.copyOf(Objects.requireNonNull(placedBlocks, "placedBlocks"));
            spawnMarkersToTrigger = List.copyOf(
                    Objects.requireNonNull(spawnMarkersToTrigger, "spawnMarkersToTrigger"));
        }
    }

    private final String jobId;
    private final ProjectType projectType;
    private final BiomeStyle biomeStyle;
    private final Facing2D facing;
    private final BlockPos3i origin;
    private final Aabb3i boundingBox;
    private final List<WorldBlockPlacement> placements;
    private final List<SpawnMarker.WorldSpawnMarker> spawnMarkers;
    private final int blocksPerStep;
    private final int ticksPerStep;
    private int nextIndex;
    private int tickAccumulator;
    private boolean completed;

    /**
     * Creates a new unstarted {@link ConstructionJob} using default pacing
     * ({@value #DEFAULT_BLOCKS_PER_STEP} blocks per {@value #DEFAULT_TICKS_PER_STEP} ticks).
     *
     * @param jobId unique job identifier
     * @param projectType structure type being built
     * @param biomeStyle architectural style
     * @param facing orientation of the placed blueprint
     * @param origin world-space blueprint origin
     * @param boundingBox reserved 3D bounding box of the structure
     * @param placements bottom-up ordered world block placements
     * @param spawnMarkers post-construction spawn and LC2 markers
     */
    public ConstructionJob(
            String jobId,
            ProjectType projectType,
            BiomeStyle biomeStyle,
            Facing2D facing,
            BlockPos3i origin,
            Aabb3i boundingBox,
            List<WorldBlockPlacement> placements,
            List<SpawnMarker.WorldSpawnMarker> spawnMarkers) {
        this(
                jobId,
                projectType,
                biomeStyle,
                facing,
                origin,
                boundingBox,
                placements,
                spawnMarkers,
                DEFAULT_BLOCKS_PER_STEP,
                DEFAULT_TICKS_PER_STEP,
                0,
                0,
                false);
    }

    /**
     * Creates or restores a {@link ConstructionJob} with explicit pacing and persisted progress state.
     *
     * @param jobId unique job identifier
     * @param projectType structure type being built
     * @param biomeStyle architectural style
     * @param facing orientation of the placed blueprint
     * @param origin world-space blueprint origin
     * @param boundingBox reserved 3D bounding box
     * @param placements world block placements (sorted bottom-up if not already)
     * @param spawnMarkers post-construction spawn markers
     * @param blocksPerStep blocks placed per step ({@code >= 1})
     * @param ticksPerStep ticks between steps ({@code >= 1})
     * @param nextIndex index of the next block to place ({@code 0..placements.size()})
     * @param tickAccumulator ticks accumulated toward next step ({@code >= 0})
     * @param completed whether this job has already finished
     */
    public ConstructionJob(
            String jobId,
            ProjectType projectType,
            BiomeStyle biomeStyle,
            Facing2D facing,
            BlockPos3i origin,
            Aabb3i boundingBox,
            List<WorldBlockPlacement> placements,
            List<SpawnMarker.WorldSpawnMarker> spawnMarkers,
            int blocksPerStep,
            int ticksPerStep,
            int nextIndex,
            int tickAccumulator,
            boolean completed) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be null or blank");
        }
        this.jobId = jobId;
        this.projectType = Objects.requireNonNull(projectType, "projectType");
        this.biomeStyle = Objects.requireNonNull(biomeStyle, "biomeStyle");
        this.facing = Objects.requireNonNull(facing, "facing");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.boundingBox = Objects.requireNonNull(boundingBox, "boundingBox");
        List<WorldBlockPlacement> sorted = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        sorted.sort(WorldBlockPlacement.BOTTOM_UP_ORDER);
        this.placements = Collections.unmodifiableList(sorted);
        this.spawnMarkers = List.copyOf(Objects.requireNonNull(spawnMarkers, "spawnMarkers"));
        if (blocksPerStep < 1) {
            throw new IllegalArgumentException("blocksPerStep must be >= 1, got " + blocksPerStep);
        }
        if (ticksPerStep < 1) {
            throw new IllegalArgumentException("ticksPerStep must be >= 1, got " + ticksPerStep);
        }
        this.blocksPerStep = blocksPerStep;
        this.ticksPerStep = ticksPerStep;
        this.nextIndex = Math.max(0, Math.min(nextIndex, this.placements.size()));
        this.tickAccumulator = Math.max(0, tickAccumulator);
        this.completed = completed || this.nextIndex >= this.placements.size();
    }

    /**
     * Advances the internal tick timer by 1 server tick, executing {@link #step()} whenever
     * {@link #getTicksPerStep()} ticks have elapsed.
     *
     * @return step result (empty if throttled or already completed)
     */
    public StepResult tick() {
        if (completed) {
            return new StepResult(List.of(), false, List.of(), nextIndex, placements.size());
        }
        tickAccumulator++;
        if (tickAccumulator < ticksPerStep) {
            return new StepResult(List.of(), false, List.of(), nextIndex, placements.size());
        }
        tickAccumulator = 0;
        return step();
    }

    /**
     * Immediately executes one construction step, returning up to {@link #getBlocksPerStep()}
     * bottom-up block placements and triggering spawn markers when the final block is placed.
     *
     * @return step result
     */
    public StepResult step() {
        if (completed) {
            return new StepResult(List.of(), false, List.of(), nextIndex, placements.size());
        }
        int total = placements.size();
        if (nextIndex >= total) {
            completed = true;
            return new StepResult(List.of(), true, spawnMarkers, total, total);
        }
        int end = Math.min(total, nextIndex + blocksPerStep);
        List<WorldBlockPlacement> batch = new ArrayList<>(placements.subList(nextIndex, end));
        nextIndex = end;
        boolean finishedNow = nextIndex >= total;
        if (finishedNow) {
            completed = true;
        }
        return new StepResult(
                batch,
                finishedNow,
                finishedNow ? spawnMarkers : List.of(),
                nextIndex,
                total);
    }

    /**
     * Returns the unique job identifier.
     *
     * @return job ID
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * Returns the project type being constructed.
     *
     * @return project type
     */
    public ProjectType getProjectType() {
        return projectType;
    }

    /**
     * Returns the biome architectural style of this job.
     *
     * @return biome style
     */
    public BiomeStyle getBiomeStyle() {
        return biomeStyle;
    }

    /**
     * Returns the placed structure facing.
     *
     * @return horizontal facing
     */
    public Facing2D getFacing() {
        return facing;
    }

    /**
     * Returns the world-space blueprint origin.
     *
     * @return origin coordinate
     */
    public BlockPos3i getOrigin() {
        return origin;
    }

    /**
     * Returns the reserved 3D bounding box of the structure.
     *
     * @return structure bounding box
     */
    public Aabb3i getBoundingBox() {
        return boundingBox;
    }

    /**
     * Returns the complete bottom-up ordered block placement schedule.
     *
     * @return unmodifiable placement list
     */
    public List<WorldBlockPlacement> getPlacements() {
        return placements;
    }

    /**
     * Returns all post-construction spawn and LC2 reservation markers.
     *
     * @return unmodifiable list of world spawn markers
     */
    public List<SpawnMarker.WorldSpawnMarker> getSpawnMarkers() {
        return spawnMarkers;
    }

    /**
     * Returns how many blocks are placed per construction step.
     *
     * @return blocks per step
     */
    public int getBlocksPerStep() {
        return blocksPerStep;
    }

    /**
     * Returns the tick interval between construction steps.
     *
     * @return ticks per step
     */
    public int getTicksPerStep() {
        return ticksPerStep;
    }

    /**
     * Returns the index of the next block to be placed (also equal to placed block count).
     *
     * @return next block index
     */
    public int getNextIndex() {
        return nextIndex;
    }

    /**
     * Returns the current tick accumulator toward the next step.
     *
     * @return tick accumulator
     */
    public int getTickAccumulator() {
        return tickAccumulator;
    }

    /**
     * Returns whether all blocks in this job have been placed.
     *
     * @return {@code true} when completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns the number of blocks placed so far.
     *
     * @return placed count
     */
    public int getPlacedCount() {
        return nextIndex;
    }

    /**
     * Returns the total number of blocks scheduled in this job.
     *
     * @return total block count
     */
    public int getTotalBlocks() {
        return placements.size();
    }

    /**
     * Returns the number of blocks remaining to be placed.
     *
     * @return remaining block count
     */
    public int getRemainingCount() {
        return Math.max(0, placements.size() - nextIndex);
    }

    /**
     * Returns construction progress as a fraction in {@code [0.0, 1.0]}.
     *
     * @return progress ratio
     */
    public double getProgressRatio() {
        if (placements.isEmpty()) {
            return 1.0;
        }
        return (double) nextIndex / (double) placements.size();
    }
}
