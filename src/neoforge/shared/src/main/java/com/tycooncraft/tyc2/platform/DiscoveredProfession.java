package com.tycooncraft.tyc2.platform;

/**
 * Runtime metadata for a villager profession discovered from Minecraft's live
 * {@code VILLAGER_PROFESSION} and {@code POINT_OF_INTEREST_TYPE} registries.
 *
 * @param professionId canonical lowercase namespaced profession ID (e.g. {@code "ie:engineer"})
 * @param workstationBlockId canonical lowercase namespaced workstation block ID
 * @param displayNameFallback optional human-readable display name fallback (may be empty)
 */
public record DiscoveredProfession(
        String professionId,
        String workstationBlockId,
        String displayNameFallback
) {
}
