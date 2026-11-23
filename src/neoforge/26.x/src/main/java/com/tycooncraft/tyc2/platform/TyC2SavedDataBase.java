package com.tycooncraft.tyc2.platform;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.world.TycoonSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * NeoForge 26.x SavedData persistence bridge using {@link SavedDataType} and {@link CompoundTag#CODEC}.
 */
public abstract class TyC2SavedDataBase extends SavedData {

    private static final SavedDataType<TycoonSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TyC2Constants.MOD_ID, "economy"),
            level -> new TycoonSavedData(),
            level -> {
                HolderLookup.Provider registries = level.registryAccess();
                return CompoundTag.CODEC.xmap(
                        tag -> TycoonSavedData.loadFromTag(tag, registries),
                        data -> data.saveToTag(registries)
                );
            }
    );

    /** Retrieves or creates the world-wide {@link TycoonSavedData} stored on the Overworld. */
    public static TycoonSavedData getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Serializes this saved data instance into a CompoundTag. */
    public abstract CompoundTag saveToTag(HolderLookup.Provider registries);
}
