package com.tycooncraft.tyc2.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 26.x NBT & CustomData compatibility helpers matching 1.21.1's {@code TyC2Nbt}.
 */
public final class TyC2Nbt {

    private static final Logger LOGGER = LoggerFactory.getLogger(TyC2Nbt.class);

    private TyC2Nbt() {
    }

    /** Reads an integer from {@code tag}, returning {@code fallback} if absent. */
    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.getIntOr(key, fallback);
    }

    /** Reads a long from {@code tag}, returning {@code fallback} if absent. */
    public static long getLong(CompoundTag tag, String key, long fallback) {
        return tag.getLongOr(key, fallback);
    }

    /** Reads a double from {@code tag}, returning {@code fallback} if absent. */
    public static double getDouble(CompoundTag tag, String key, double fallback) {
        return tag.getDoubleOr(key, fallback);
    }

    /** Reads a float from {@code tag}, returning {@code fallback} if absent. */
    public static float getFloat(CompoundTag tag, String key, float fallback) {
        return tag.getFloatOr(key, fallback);
    }

    /** Reads a boolean from {@code tag}, returning {@code fallback} if absent. */
    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.getBooleanOr(key, fallback);
    }

    /** Reads a string from {@code tag}, returning {@code fallback} if absent. */
    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag.getStringOr(key, fallback);
    }

    /** Reads a long array from {@code tag}, returning an empty array if absent. */
    public static long[] getLongArray(CompoundTag tag, String key) {
        return tag.getLongArray(key).orElse(new long[0]);
    }

    /** Reads a child compound tag, returning an empty CompoundTag if absent. */
    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    /** Reads a list of CompoundTags from {@code tag}. */
    public static List<CompoundTag> getCompoundList(CompoundTag tag, String key) {
        ListTag list = tag.getListOrEmpty(key);
        List<CompoundTag> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompoundOrEmpty(i));
        }
        return result;
    }

    /** Returns a mutable copy of an ItemStack's {@code CustomData} tag (empty if absent). */
    public static CompoundTag copyStackTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** Updates an ItemStack's {@code CustomData} tag in place. */
    public static void updateStackTag(ItemStack stack, Consumer<CompoundTag> mutator) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, mutator);
    }

    /** Encodes a list of ItemStacks (including empty slots) into {@code parent} under {@code key}. */
    public static void saveItemList(
            CompoundTag parent,
            String key,
            List<ItemStack> stacks,
            HolderLookup.Provider registries
    ) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        ItemStack.OPTIONAL_CODEC.listOf()
                .encodeStart(ops, stacks)
                .resultOrPartial(err -> LOGGER.warn("Failed to encode TyC2 item list {}: {}", key, err))
                .ifPresent(encoded -> parent.put(key, encoded));
    }

    /** Decodes a list of ItemStacks from {@code parent} under {@code key}. */
    public static List<ItemStack> loadItemList(
            CompoundTag parent,
            String key,
            HolderLookup.Provider registries
    ) {
        if (!parent.contains(key)) {
            return new ArrayList<>();
        }
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        return ItemStack.OPTIONAL_CODEC.listOf()
                .parse(ops, parent.get(key))
                .resultOrPartial(err -> LOGGER.warn("Failed to decode TyC2 item list {}: {}", key, err))
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
    }
}
