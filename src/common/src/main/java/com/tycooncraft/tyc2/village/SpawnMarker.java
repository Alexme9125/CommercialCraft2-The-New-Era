package com.tycooncraft.tyc2.village;

import java.util.Objects;

/**
 * Post-construction semantic marker attached to a {@link StructureBlueprint} indicating where
 * villagers, livestock, or LC2 logistics pads belong.
 *
 * @param kind marker category
 * @param dx local X offset
 * @param dy local Y offset
 * @param dz local Z offset
 * @param hint entity ID or block hint (e.g. {@code "minecraft:cow"}, {@code "lc2:smart_port"})
 */
public record SpawnMarker(MarkerKind kind, int dx, int dy, int dz, String hint) {

    /**
     * Category of a blueprint spawn or reservation marker.
     */
    public enum MarkerKind {
        /** Natural villager spawn point for completed housing. */
        VILLAGER,
        /** Livestock spawn point inside a completed ranch pen. */
        ANIMAL,
        /** Open deck anchor reserved for an LC2 Smart Port on a waterfront pier. */
        LC2_PORT_PAD,
        /** Open-sky landing pad anchor reserved for an LC2 Cargo Station. */
        LC2_HELIPAD_CENTER
    }

    /**
     * World-space version of a {@link SpawnMarker} attached to a {@link ConstructionJob}.
     *
     * @param kind marker category
     * @param pos absolute world block coordinate
     * @param hint entity ID or block hint
     */
    public record WorldSpawnMarker(MarkerKind kind, BlockPos3i pos, String hint) {
        /**
         * Validates fields.
         */
        public WorldSpawnMarker {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(pos, "pos");
            hint = hint == null ? "" : hint;
        }
    }

    /**
     * Validates fields and normalizes hint.
     */
    public SpawnMarker {
        Objects.requireNonNull(kind, "kind");
        hint = hint == null ? "" : hint;
    }

    /**
     * Translates this relative marker into an absolute {@link WorldSpawnMarker}.
     *
     * @param origin world-space structure origin
     * @return translated world spawn marker
     */
    public WorldSpawnMarker toWorld(BlockPos3i origin) {
        Objects.requireNonNull(origin, "origin");
        return new WorldSpawnMarker(kind, origin.offset(dx, dy, dz), hint);
    }
}
