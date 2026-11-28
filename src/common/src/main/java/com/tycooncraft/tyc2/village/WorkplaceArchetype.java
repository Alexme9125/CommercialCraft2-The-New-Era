package com.tycooncraft.tyc2.village;

import java.util.Locale;
import java.util.Objects;

/**
 * Architectural blueprint archetype for aided villager workplaces, covering all 13 vanilla
 * Minecraft villager professions plus {@link #GENERIC} for dynamically discovered modded professions.
 */
public enum WorkplaceArchetype {
    FARMER,
    FISHERMAN,
    SHEPHERD,
    FLETCHER,
    BUTCHER,
    LEATHERWORKER,
    MASON,
    CARTOGRAPHER,
    TOOLSMITH,
    WEAPONSMITH,
    ARMORER,
    CLERIC,
    LIBRARIAN,
    GENERIC;

    /**
     * Returns the canonical localization key for this profession archetype
     * (e.g. {@code "profession.mnera.farmer"}).
     *
     * @return lang translation key
     */
    public String langKey() {
        return "profession.mnera." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses an archetype by name (case-insensitive), falling back to {@code fallback} if unrecognized.
     *
     * @param name archetype name (may be null)
     * @param fallback fallback archetype
     * @return parsed archetype or fallback
     */
    public static WorkplaceArchetype fromNameOrDefault(String name, WorkplaceArchetype fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String norm = name.trim().toUpperCase(Locale.ROOT);
        for (WorkplaceArchetype archetype : values()) {
            if (archetype.name().equals(norm)) {
                return archetype;
            }
        }
        return fallback;
    }
}
