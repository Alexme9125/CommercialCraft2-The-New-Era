package com.tycooncraft.tyc2.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured assertion recorder and world helpers for TyC2 NeoForge live-server QA.
 */
public final class TyC2QaChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger("tyc2_qa");

    private final Map<String, String> passedChecks = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public void putMeta(String key, String value) {
        metadata.put(key, value);
    }

    public void check(String key, boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError("TyC2 QA check failed [" + key + "]: " + detail);
        }
        passedChecks.put(key, detail == null || detail.isEmpty() ? "passed" : detail);
        LOGGER.info("[TYC2-QA PASS] {}: {}", key, detail);
    }

    public void check(String key, boolean condition) {
        check(key, condition, "passed");
    }

    public int passedCount() {
        return passedChecks.size();
    }

    public void writeReports(String profile, String phase, int ticks, Throwable failure) throws IOException {
        long pid = ProcessHandle.current().pid();
        Properties props = new Properties();
        props.setProperty("profile", profile);
        props.setProperty("phase", phase);
        props.setProperty("pid", Long.toString(pid));
        props.setProperty("ticks", Integer.toString(ticks));
        props.setProperty("status", failure == null ? "passed" : "failed");
        props.setProperty("passedCount", Integer.toString(passedChecks.size()));
        metadata.forEach(props::setProperty);
        passedChecks.forEach((k, v) -> props.setProperty("check." + k, v));
        if (failure != null) {
            props.setProperty("problem", failure.toString());
            StringWriter sw = new StringWriter();
            failure.printStackTrace(new PrintWriter(sw));
            props.setProperty("trace", sw.toString());
        }
        String suffix = profile + "-" + phase;
        try (var writer = Files.newBufferedWriter(Path.of("qa-report-" + suffix + ".properties"))) {
            props.store(writer, "TyC2 NeoForge QA report (" + suffix + ")");
        }

        JsonObject json = new JsonObject();
        json.addProperty("profile", profile);
        json.addProperty("phase", phase);
        json.addProperty("pid", pid);
        json.addProperty("ticks", ticks);
        json.addProperty("status", failure == null ? "passed" : "failed");
        json.addProperty("passedCount", passedChecks.size());
        JsonObject metaObj = new JsonObject();
        metadata.forEach(metaObj::addProperty);
        json.add("metadata", metaObj);
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, String> e : passedChecks.entrySet()) {
            JsonObject row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("status", "passed");
            row.addProperty("detail", e.getValue());
            arr.add(row);
        }
        json.add("checks", arr);
        if (failure != null) {
            json.addProperty("problem", failure.toString());
        }
        Files.writeString(
                Path.of("qa-report-" + suffix + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n"
        );
    }

    public static Block block(String qualifiedId) {
        return BuiltInRegistries.BLOCK.stream()
                .filter(b -> BuiltInRegistries.BLOCK.getKey(b).toString().equals(qualifiedId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing registered block: " + qualifiedId));
    }

    public static Item item(String qualifiedId) {
        return BuiltInRegistries.ITEM.stream()
                .filter(i -> BuiltInRegistries.ITEM.getKey(i).toString().equals(qualifiedId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing registered item: " + qualifiedId));
    }

    public static boolean hasBlock(String id) {
        return BuiltInRegistries.BLOCK.stream()
                .anyMatch(b -> BuiltInRegistries.BLOCK.getKey(b).toString().equals(id));
    }

    public static boolean hasItem(String id) {
        return BuiltInRegistries.ITEM.stream()
                .anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).toString().equals(id));
    }

    public static boolean hasBlockEntity(String id) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.stream()
                .anyMatch(t -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(t).toString().equals(id));
    }

    public static boolean hasMenu(String id) {
        return BuiltInRegistries.MENU.stream()
                .anyMatch(t -> BuiltInRegistries.MENU.getKey(t).toString().equals(id));
    }

    public static void setChunkRangeForced(ServerLevel level, int minCx, int maxCx, int minCz, int maxCz, boolean forced) {
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.setChunkForced(cx, cz, forced);
                if (forced) {
                    level.getChunk(cx, cz);
                }
            }
        }
    }

    public static void survivalPlace(ServerLevel level, FakePlayer player, String qualifiedBlockId, BlockPos pos) {
        Block expected = block(qualifiedBlockId);
        BlockPos floor = pos.below();
        if (level.getBlockState(floor).isAir()) {
            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        }
        if (!level.getBlockState(pos).isAir()) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        ItemStack stack = new ItemStack(expected);
        player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        player.setYRot(90.0F);
        player.setYHeadRot(90.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, floor, false);
        var result = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction() || !level.getBlockState(pos).is(expected)) {
            throw new AssertionError("Survival placement failed for " + qualifiedBlockId + " at " + pos
                    + " (result=" + result + ", actual=" + level.getBlockState(pos).getBlock() + ")");
        }
    }
}
