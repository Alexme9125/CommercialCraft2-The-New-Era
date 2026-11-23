package com.tycooncraft.tyc2.village;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for geometry primitives ({@link BlockPos3i}, {@link Aabb3i}, {@link Facing2D})
 * and biome architectural styling ({@link BiomeStyle}, {@link BiomePalette}).
 */
class GeometryAndBiomeTest {

    @Test
    void blockPos3iOffsetsAndDistances() {
        BlockPos3i origin = new BlockPos3i(10, 64, -5);
        assertEquals(new BlockPos3i(13, 66, -7), origin.offset(3, 2, -2));
        assertEquals(new BlockPos3i(10, 64, -9), origin.offset(Facing2D.NORTH, 4));
        assertEquals(new BlockPos3i(14, 64, -5), origin.offset(Facing2D.EAST, 4));
        assertEquals(new BlockPos3i(10, 65, -5), origin.above());
        assertEquals(new BlockPos3i(10, 61, -5), origin.below(3));

        BlockPos3i target = new BlockPos3i(13, 68, -1);
        assertEquals(25L, origin.horizontalDistanceSq(target));
        assertEquals(5.0, origin.horizontalDistance(target), 1e-9);
        assertEquals(41L, origin.distanceSq(target));
        assertEquals(11, origin.manhattanDistance(target));
    }

    @Test
    void aabb3iContainmentIntersectionPaddingAndExpansion() {
        Aabb3i box = Aabb3i.fromOriginAndSize(new BlockPos3i(10, 64, 20), 7, 6, 7);
        assertEquals(new Aabb3i(10, 64, 20, 16, 69, 26), box);
        assertEquals(7, box.sizeX());
        assertEquals(6, box.sizeY());
        assertEquals(7, box.sizeZ());
        assertEquals(49L, box.footprintArea());
        assertEquals(294L, box.volume());

        assertTrue(box.contains(10, 64, 20));
        assertTrue(box.contains(16, 69, 26));
        assertFalse(box.contains(17, 64, 20));
        assertTrue(box.containsHorizontal(13, 23));

        // 1-block gap (x=17 empty, next box starts at x=18): satisfies 1-block buffer, violates 2-block buffer
        Aabb3i oneBlockGap = new Aabb3i(18, 64, 20, 24, 69, 26);
        assertFalse(box.intersects(oneBlockGap));
        assertFalse(box.intersectsWithPadding(oneBlockGap, 1, 0));
        assertTrue(box.intersectsWithPadding(oneBlockGap, 2, 0));
        assertTrue(box.padHorizontal(2).intersectsHorizontal(oneBlockGap));

        // 2-block gap (x=17,18 empty, next box starts at x=19): satisfies 2-block buffer
        Aabb3i twoBlockGap = new Aabb3i(19, 64, 20, 25, 69, 26);
        assertFalse(box.intersectsWithPadding(twoBlockGap, 2, 0));
        assertFalse(box.padHorizontal(2).intersectsHorizontal(twoBlockGap));

        // Vertical separation: 3D doesn't intersect, horizontal does
        Aabb3i above = new Aabb3i(12, 80, 22, 14, 85, 24);
        assertFalse(box.intersects(above));
        assertTrue(box.intersectsHorizontal(above));

        Aabb3i union = box.union(twoBlockGap);
        assertEquals(new Aabb3i(10, 64, 20, 25, 69, 26), union);

        assertThrows(IllegalArgumentException.class, () -> new Aabb3i(5, 0, 0, 4, 1, 1));
    }

    @Test
    void facing2DRotationAndBlockStateRotation() {
        // 4 clockwise turns return to NORTH
        Facing2D f = Facing2D.NORTH;
        for (int i = 0; i < 4; i++) {
            f = f.clockwise();
        }
        assertEquals(Facing2D.NORTH, f);
        assertEquals(Facing2D.WEST, Facing2D.NORTH.counterClockwise());
        assertEquals(Facing2D.SOUTH, Facing2D.NORTH.opposite());

        // Asymmetric box (width=11, depth=9): verify in-box rotation preserves valid bounds and 90-deg cycle
        int w = 11;
        int d = 9;
        BlockPos3i p0 = new BlockPos3i(2, 3, 0);
        BlockPos3i pEast = Facing2D.EAST.rotateInBox(p0.x(), p0.y(), p0.z(), w, d);
        assertEquals(new BlockPos3i(8, 3, 2), pEast); // dz=0 front maps to east edge x=d-1=8
        BlockPos3i pSouth = Facing2D.SOUTH.rotateInBox(p0.x(), p0.y(), p0.z(), w, d);
        assertEquals(new BlockPos3i(8, 3, 8), pSouth); // dz=0 front maps to south edge z=d-1=8
        BlockPos3i pWest = Facing2D.WEST.rotateInBox(p0.x(), p0.y(), p0.z(), w, d);
        assertEquals(new BlockPos3i(0, 3, 8), pWest); // dz=0 front maps to west edge x=0

        // Directional block-state property rotation
        assertEquals(
                "minecraft:oak_stairs[facing=east,half=bottom]",
                Facing2D.EAST.rotateBlockStateSpec("minecraft:oak_stairs[facing=north,half=bottom]"));
        assertEquals(
                "minecraft:oak_stairs[facing=south,half=bottom]",
                Facing2D.SOUTH.rotateBlockStateSpec("minecraft:oak_stairs[facing=north,half=bottom]"));
        assertEquals(
                "minecraft:oak_log[axis=z]",
                Facing2D.EAST.rotateBlockStateSpec("minecraft:oak_log[axis=x]"));
        assertEquals(
                "minecraft:oak_log[axis=y]",
                Facing2D.EAST.rotateBlockStateSpec("minecraft:oak_log[axis=y]"));
        assertEquals(
                "minecraft:oak_fence[east=true,south=false]",
                Facing2D.EAST.rotateBlockStateSpec("minecraft:oak_fence[north=true,east=false]"));

        // Delta-to-facing helper
        assertEquals(Facing2D.EAST, Facing2D.fromDelta(10, 3));
        assertEquals(Facing2D.WEST, Facing2D.fromDelta(-10, -3));
        assertEquals(Facing2D.SOUTH, Facing2D.fromDelta(2, 9));
        assertEquals(Facing2D.NORTH, Facing2D.fromDelta(-2, -9));
    }

    @Test
    void biomeStyleMappingAndPalettes() {
        assertEquals(BiomeStyle.PLAINS, BiomeStyle.fromBiomeId("minecraft:plains"));
        assertEquals(BiomeStyle.PLAINS, BiomeStyle.fromBiomeId("minecraft:meadow"));
        assertEquals(BiomeStyle.DESERT, BiomeStyle.fromBiomeId("minecraft:desert"));
        assertEquals(BiomeStyle.DESERT, BiomeStyle.fromBiomeId("minecraft:badlands"));
        assertEquals(BiomeStyle.SAVANNA, BiomeStyle.fromBiomeId("minecraft:savanna_plateau"));
        assertEquals(BiomeStyle.TAIGA, BiomeStyle.fromBiomeId("minecraft:taiga"));
        assertEquals(BiomeStyle.TAIGA, BiomeStyle.fromBiomeId("minecraft:old_growth_spruce_taiga"));
        assertEquals(BiomeStyle.SNOWY, BiomeStyle.fromBiomeId("minecraft:snowy_plains"));
        assertEquals(BiomeStyle.SNOWY, BiomeStyle.fromBiomeId("minecraft:snowy_taiga"));
        assertEquals(BiomeStyle.SNOWY, BiomeStyle.fromBiomeId("minecraft:ice_spikes"));
        assertEquals(BiomeStyle.PLAINS, BiomeStyle.fromBiomeId(null));

        for (BiomeStyle style : BiomeStyle.values()) {
            BiomePalette p = style.palette();
            assertNotNull(p);
            assertTrue(p.log().contains(":"));
            assertTrue(p.planks().contains(":"));
            assertTrue(p.bed().endsWith("_bed"));
            assertTrue(p.door().endsWith("_door"));
            assertTrue(p.helipadBlock().contains(":"));
        }

        // Desert log is cut_sandstone (no axis property); Plains log is oak_log (has axis property)
        assertEquals("minecraft:cut_sandstone",
                BiomePalette.withAxisIfSupported(BiomeStyle.DESERT.palette().log(), "y"));
        assertEquals("minecraft:oak_log[axis=y]",
                BiomePalette.withAxisIfSupported(BiomeStyle.PLAINS.palette().log(), "y"));
    }
}
