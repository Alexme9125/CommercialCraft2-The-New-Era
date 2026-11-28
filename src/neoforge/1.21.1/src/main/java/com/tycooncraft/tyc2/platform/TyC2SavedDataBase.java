package com.tycooncraft.tyc2.platform;

import com.tycooncraft.tyc2.world.TycoonSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * NeoForge 1.21.1 SavedData persistence bridge for {@link TycoonSavedData}.
 */
public abstract class TyC2SavedDataBase extends SavedData {

    private static final String STORAGE_NAME = "mnera_economy";

    /** Retrieves or creates the world-wide {@link TycoonSavedData} stored on the Overworld. */
    public static TycoonSavedData getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        TycoonSavedData::new,
                        (tag, registries) -> TycoonSavedData.loadFromTag(tag, registries)
                ),
                STORAGE_NAME
        );
    }

    /** Serializes this saved data instance into a CompoundTag. */
    public abstract CompoundTag saveToTag(HolderLookup.Provider registries);

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(saveToTag(registries));
        return tag;
    }
}
