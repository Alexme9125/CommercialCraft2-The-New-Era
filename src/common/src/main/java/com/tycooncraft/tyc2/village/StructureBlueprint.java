package com.tycooncraft.tyc2.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable 3D procedural structure blueprint with bottom-up ordered block placements,
 * rotation support across all 4 {@link Facing2D} orientations, road attachment point,
 * and post-construction {@link SpawnMarker}s.
 */
public final class StructureBlueprint {

    private final ProjectType projectType;
    private final BiomeStyle biomeStyle;
    private final Facing2D facing;
    private final int width;
    private final int height;
    private final int depth;
    private final List<BlockPlacement> placements;
    private final List<SpawnMarker> spawnMarkers;
    private final int roadAttachDx;
    private final int roadAttachDz;

    private StructureBlueprint(
            ProjectType projectType,
            BiomeStyle biomeStyle,
            Facing2D facing,
            int width,
            int height,
            int depth,
            List<BlockPlacement> placements,
            List<SpawnMarker> spawnMarkers,
            int roadAttachDx,
            int roadAttachDz) {
        this.projectType = Objects.requireNonNull(projectType, "projectType");
        this.biomeStyle = Objects.requireNonNull(biomeStyle, "biomeStyle");
        this.facing = Objects.requireNonNull(facing, "facing");
        if (width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException(
                    "Blueprint dimensions must be >= 1, got (" + width + "," + height + "," + depth + ")");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        List<BlockPlacement> sorted = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        sorted.sort(BlockPlacement.BOTTOM_UP_ORDER);
        this.placements = Collections.unmodifiableList(sorted);
        this.spawnMarkers = List.copyOf(Objects.requireNonNull(spawnMarkers, "spawnMarkers"));
        this.roadAttachDx = roadAttachDx;
        this.roadAttachDz = roadAttachDz;
    }

    /**
     * Returns the project type represented by this blueprint.
     *
     * @return project type
     */
    public ProjectType projectType() {
        return projectType;
    }

    /**
     * Returns the biome architectural style used by this blueprint.
     *
     * @return biome style
     */
    public BiomeStyle biomeStyle() {
        return biomeStyle;
    }

    /**
     * Returns the horizontal orientation of this blueprint instance.
     *
     * @return facing direction
     */
    public Facing2D facing() {
        return facing;
    }

    /**
     * Returns the blueprint X width in blocks.
     *
     * @return width along X
     */
    public int width() {
        return width;
    }

    /**
     * Returns the blueprint Y height in blocks.
     *
     * @return height along Y
     */
    public int height() {
        return height;
    }

    /**
     * Returns the blueprint Z depth in blocks.
     *
     * @return depth along Z
     */
    public int depth() {
        return depth;
    }

    /**
     * Returns all block placements sorted bottom-up ({@code dy} ascending, then {@code dz}, then {@code dx}).
     *
     * @return unmodifiable list of block placements
     */
    public List<BlockPlacement> placements() {
        return placements;
    }

    /**
     * Returns all spawn and reservation markers attached to this blueprint.
     *
     * @return unmodifiable list of spawn markers
     */
    public List<SpawnMarker> spawnMarkers() {
        return spawnMarkers;
    }

    /**
     * Returns the local X coordinate of the front road attachment point at {@code dy = 0}.
     *
     * @return local X road attach offset
     */
    public int roadAttachDx() {
        return roadAttachDx;
    }

    /**
     * Returns the local Z coordinate of the front road attachment point at {@code dy = 0}.
     *
     * @return local Z road attach offset
     */
    public int roadAttachDz() {
        return roadAttachDz;
    }

    /**
     * Returns a rotated copy of this blueprint facing {@code targetFacing}.
     * When {@code this.facing == Facing2D.NORTH}, rotates directly from canonical space;
     * otherwise rotates by the relative clockwise delta.
     *
     * @param targetFacing desired orientation
     * @return rotated blueprint with bottom-up sorted placements and rotated block-state properties
     */
    public StructureBlueprint rotated(Facing2D targetFacing) {
        Objects.requireNonNull(targetFacing, "targetFacing");
        if (targetFacing == this.facing) {
            return this;
        }
        int deltaSteps = (targetFacing.clockwiseSteps() - this.facing.clockwiseSteps() + 4) & 3;
        Facing2D relativeRot = switch (deltaSteps) {
            case 0 -> Facing2D.NORTH;
            case 1 -> Facing2D.EAST;
            case 2 -> Facing2D.SOUTH;
            default -> Facing2D.WEST;
        };

        int newWidth = relativeRot.rotatedWidth(width, depth);
        int newDepth = relativeRot.rotatedDepth(width, depth);

        List<BlockPlacement> rotatedPlacements = new ArrayList<>(placements.size());
        for (BlockPlacement bp : placements) {
            BlockPos3i rp = relativeRot.rotateInBox(bp.dx(), bp.dy(), bp.dz(), width, depth);
            String rotSpec = relativeRot.rotateBlockStateSpec(bp.blockStateSpec());
            rotatedPlacements.add(new BlockPlacement(rp.x(), rp.y(), rp.z(), rotSpec));
        }

        List<SpawnMarker> rotatedMarkers = new ArrayList<>(spawnMarkers.size());
        for (SpawnMarker marker : spawnMarkers) {
            BlockPos3i rm = relativeRot.rotateInBox(marker.dx(), marker.dy(), marker.dz(), width, depth);
            rotatedMarkers.add(new SpawnMarker(marker.kind(), rm.x(), rm.y(), rm.z(), marker.hint()));
        }

        BlockPos3i rotAttach = relativeRot.rotateInBox(roadAttachDx, 0, roadAttachDz, width, depth);
        return new StructureBlueprint(
                projectType,
                biomeStyle,
                targetFacing,
                newWidth,
                height,
                newDepth,
                rotatedPlacements,
                rotatedMarkers,
                rotAttach.x(),
                rotAttach.z());
    }

    /**
     * Creates a mutable builder for assembling a canonical {@link Facing2D#NORTH}-facing blueprint.
     *
     * @param projectType structure project type
     * @param biomeStyle architectural biome style
     * @param width X dimension ({@code >= 1})
     * @param height Y dimension ({@code >= 1})
     * @param depth Z dimension ({@code >= 1})
     * @return blueprint builder
     */
    public static Builder builder(
            ProjectType projectType, BiomeStyle biomeStyle, int width, int height, int depth) {
        return new Builder(projectType, biomeStyle, width, height, depth);
    }

    /**
     * Fluent builder that deduplicates block coordinates and sorts placements bottom-up on {@link #build()}.
     */
    public static final class Builder {
        private final ProjectType projectType;
        private final BiomeStyle biomeStyle;
        private final int width;
        private final int height;
        private final int depth;
        private final Map<BlockPos3i, String> blocks = new LinkedHashMap<>();
        private final List<SpawnMarker> markers = new ArrayList<>();
        private int roadAttachDx;
        private int roadAttachDz;

        private Builder(ProjectType projectType, BiomeStyle biomeStyle, int width, int height, int depth) {
            this.projectType = Objects.requireNonNull(projectType, "projectType");
            this.biomeStyle = Objects.requireNonNull(biomeStyle, "biomeStyle");
            if (width < 1 || height < 1 || depth < 1) {
                throw new IllegalArgumentException("Dimensions must be >= 1");
            }
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.roadAttachDx = width / 2;
            this.roadAttachDz = 0;
        }

        /**
         * Places or replaces a single block at {@code (dx, dy, dz)}.
         *
         * @param dx local X in {@code [0, width-1]}
         * @param dy local Y in {@code [0, height-1]}
         * @param dz local Z in {@code [0, depth-1]}
         * @param blockStateSpec block-state spec
         * @return this builder
         */
        public Builder set(int dx, int dy, int dz, String blockStateSpec) {
            checkBounds(dx, dy, dz);
            if (blockStateSpec == null || blockStateSpec.isBlank()) {
                throw new IllegalArgumentException("blockStateSpec must not be blank");
            }
            blocks.put(new BlockPos3i(dx, dy, dz), blockStateSpec);
            return this;
        }

        /**
         * Removes any previously set block at {@code (dx, dy, dz)} (leaving air).
         *
         * @param dx local X
         * @param dy local Y
         * @param dz local Z
         * @return this builder
         */
        public Builder clear(int dx, int dy, int dz) {
            checkBounds(dx, dy, dz);
            blocks.remove(new BlockPos3i(dx, dy, dz));
            return this;
        }

        /**
         * Fills an inclusive 3D rectangular region with {@code blockStateSpec}.
         *
         * @param x1 min X
         * @param y1 min Y
         * @param z1 min Z
         * @param x2 max X
         * @param y2 max Y
         * @param z2 max Z
         * @param blockStateSpec block-state spec
         * @return this builder
         */
        public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, String blockStateSpec) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    for (int x = x1; x <= x2; x++) {
                        set(x, y, z, blockStateSpec);
                    }
                }
            }
            return this;
        }

        /**
         * Places a vertical pillar from {@code y1} to {@code y2} (inclusive), adding {@code [axis=y]}
         * only when the block supports the axis property.
         *
         * @param dx local X
         * @param y1 start Y
         * @param y2 end Y
         * @param dz local Z
         * @param blockId block ID
         * @return this builder
         */
        public Builder pillar(int dx, int y1, int y2, int dz, String blockId) {
            String spec = BiomePalette.withAxisIfSupported(blockId, "y");
            for (int y = y1; y <= y2; y++) {
                set(dx, y, dz, spec);
            }
            return this;
        }

        /**
         * Places a complete two-block tall Minecraft door ({@code half=lower} and {@code half=upper}).
         *
         * @param dx local X
         * @param dy lower block Y
         * @param dz local Z
         * @param doorBlockId door block ID
         * @param facing door facing direction
         * @return this builder
         */
        public Builder setDoor(int dx, int dy, int dz, String doorBlockId, Facing2D facing) {
            Objects.requireNonNull(facing, "facing");
            String f = facing.serializedName();
            set(dx, dy, dz, doorBlockId + "[facing=" + f + ",half=lower,hinge=left,open=false]");
            set(dx, dy + 1, dz, doorBlockId + "[facing=" + f + ",half=upper,hinge=left,open=false]");
            return this;
        }

        /**
         * Places a complete two-block Minecraft bed ({@code part=foot} at {@code (dx, dy, dz)} and
         * {@code part=head} one block along {@code headDirection}).
         *
         * @param dx foot X
         * @param dy bed Y
         * @param dz foot Z
         * @param bedBlockId bed block ID
         * @param headDirection direction from foot to head
         * @return this builder
         */
        public Builder setBed(int dx, int dy, int dz, String bedBlockId, Facing2D headDirection) {
            Objects.requireNonNull(headDirection, "headDirection");
            String f = headDirection.serializedName();
            set(dx, dy, dz, bedBlockId + "[facing=" + f + ",part=foot]");
            set(dx + headDirection.stepX(), dy, dz + headDirection.stepZ(),
                    bedBlockId + "[facing=" + f + ",part=head]");
            return this;
        }

        /**
         * Sets the front road attachment coordinate at {@code dy = 0}.
         *
         * @param dx local X
         * @param dz local Z
         * @return this builder
         */
        public Builder roadAttach(int dx, int dz) {
            checkBounds(dx, 0, dz);
            this.roadAttachDx = dx;
            this.roadAttachDz = dz;
            return this;
        }

        /**
         * Adds a post-construction {@link SpawnMarker}.
         *
         * @param kind marker category
         * @param dx local X
         * @param dy local Y
         * @param dz local Z
         * @param hint entity or block hint
         * @return this builder
         */
        public Builder addMarker(SpawnMarker.MarkerKind kind, int dx, int dy, int dz, String hint) {
            checkBounds(dx, dy, dz);
            markers.add(new SpawnMarker(kind, dx, dy, dz, hint));
            return this;
        }

        /**
         * Builds the immutable, bottom-up sorted {@link StructureBlueprint}.
         *
         * @return constructed blueprint
         */
        public StructureBlueprint build() {
            List<BlockPlacement> list = new ArrayList<>(blocks.size());
            for (Map.Entry<BlockPos3i, String> e : blocks.entrySet()) {
                BlockPos3i p = e.getKey();
                list.add(new BlockPlacement(p.x(), p.y(), p.z(), e.getValue()));
            }
            return new StructureBlueprint(
                    projectType,
                    biomeStyle,
                    Facing2D.NORTH,
                    width,
                    height,
                    depth,
                    list,
                    markers,
                    roadAttachDx,
                    roadAttachDz);
        }

        private void checkBounds(int dx, int dy, int dz) {
            if (dx < 0 || dx >= width || dy < 0 || dy >= height || dz < 0 || dz >= depth) {
                throw new IndexOutOfBoundsException(
                        "Local coord (" + dx + "," + dy + "," + dz + ") outside ("
                                + width + "," + height + "," + depth + ")");
            }
        }
    }
}
