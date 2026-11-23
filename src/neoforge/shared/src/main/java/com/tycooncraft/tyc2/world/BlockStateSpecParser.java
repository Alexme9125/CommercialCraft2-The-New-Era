package com.tycooncraft.tyc2.world;

import com.tycooncraft.tyc2.platform.TyC2Native;
import java.util.Optional;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Parses pure-Java {@code WorldBlockPlacement.blockStateSpec()} strings such as
 * {@code "minecraft:oak_stairs[facing=north,half=bottom]"} into live Minecraft {@link BlockState}s.
 */
public final class BlockStateSpecParser {

    private BlockStateSpecParser() {
    }

    /** Parses a block-state specification string into a valid {@link BlockState} (never null). */
    public static BlockState parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return Blocks.AIR.defaultBlockState();
        }
        String trimmed = spec.trim();
        int bracket = trimmed.indexOf('[');
        String blockId = bracket >= 0 ? trimmed.substring(0, bracket).trim() : trimmed;
        Optional<Block> blockOpt = TyC2Native.findBlock(blockId);
        if (blockOpt.isEmpty()) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        Block block = blockOpt.get();
        BlockState state = block.defaultBlockState();
        if (bracket < 0 || !trimmed.endsWith("]")) {
            return state;
        }
        String propsBody = trimmed.substring(bracket + 1, trimmed.length() - 1).trim();
        if (propsBody.isEmpty()) {
            return state;
        }
        StateDefinition<Block, BlockState> def = block.getStateDefinition();
        for (String pair : propsBody.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq >= pair.length() - 1) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            Property<?> prop = def.getProperty(key);
            if (prop != null) {
                state = applyProperty(state, prop, val);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(
            BlockState state,
            Property<T> property,
            String rawValue
    ) {
        return property.getValue(rawValue)
                .map(parsed -> state.setValue(property, parsed))
                .orElse(state);
    }
}
