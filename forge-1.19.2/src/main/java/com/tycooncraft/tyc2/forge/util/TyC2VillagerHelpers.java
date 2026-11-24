package com.tycooncraft.tyc2.forge.util;

import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forge 1.19.2 villager profession inspection, dynamic third-party modded profession discovery,
 * post-construction workstation job assignment, and display component helpers.
 */
public final class TyC2VillagerHelpers {

    /**
     * Dynamically discovered villager profession and its default POI workstation block.
     *
     * @param professionId canonical namespaced profession ID
     * @param workstationBlockId canonical namespaced default workstation block ID
     * @param displayNameFallback human-readable fallback name
     */
    public record DiscoveredProfession(
            String professionId,
            String workstationBlockId,
            String displayNameFallback
    ) {
    }

    private TyC2VillagerHelpers() {
    }

    /**
     * Reads a villager's canonical namespaced profession ID via {@link ForgeRegistries#VILLAGER_PROFESSIONS}.
     *
     * @param villager living villager entity
     * @return lowercase profession ID (defaults to {@code "minecraft:none"})
     */
    public static String getVillagerProfessionId(@Nullable Villager villager) {
        if (villager == null) {
            return "minecraft:none";
        }
        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (prof == null) {
            return "minecraft:none";
        }
        ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(prof);
        return key == null ? "minecraft:none" : key.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Converts a live Forge 1.19.2 {@link Villager} into a pure-Java {@link VillageEmploymentStats.VillagerSnapshot}.
     *
     * @param villager living villager entity
     * @return employment scan snapshot
     */
    public static VillageEmploymentStats.VillagerSnapshot toEmploymentSnapshot(Villager villager) {
        String profId = getVillagerProfessionId(villager);
        boolean nitwit = villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                || VillageEmploymentStats.isNitwitProfession(profId);
        return new VillageEmploymentStats.VillagerSnapshot(
                villager.getUUID(),
                !villager.isBaby(),
                nitwit,
                profId
        );
    }

    /**
     * Scans {@link ForgeRegistries#VILLAGER_PROFESSIONS} and {@link ForgeRegistries#POI_TYPES}
     * to discover all valid professions with a non-air default workstation block, skipping
     * {@code none} and {@code nitwit}.
     *
     * @return sorted list of discovered professions
     */
    public static List<DiscoveredProfession> discoverModdedProfessions() {
        List<Holder<PoiType>> poiHolders = new ArrayList<>();
        for (Map.Entry<ResourceKey<PoiType>, PoiType> e : ForgeRegistries.POI_TYPES.getEntries()) {
            ForgeRegistries.POI_TYPES.getHolder(e.getKey()).ifPresent(poiHolders::add);
        }

        List<DiscoveredProfession> discovered = new ArrayList<>();
        for (Map.Entry<ResourceKey<VillagerProfession>, VillagerProfession> entry
                : ForgeRegistries.VILLAGER_PROFESSIONS.getEntries()) {
            ResourceLocation profKey = entry.getKey().location();
            VillagerProfession prof = entry.getValue();
            if (prof == null || prof == VillagerProfession.NONE || prof == VillagerProfession.NITWIT) {
                continue;
            }
            String profId = profKey.toString().toLowerCase(Locale.ROOT);
            String path = profKey.getPath().toLowerCase(Locale.ROOT);
            if ("minecraft:none".equals(profId)
                    || "minecraft:nitwit".equals(profId)
                    || "none".equals(path)
                    || "nitwit".equals(path)) {
                continue;
            }
            String workstationBlockId = resolveWorkstationBlockId(prof, poiHolders);
            if (workstationBlockId == null || workstationBlockId.isBlank()) {
                continue;
            }
            discovered.add(new DiscoveredProfession(profId, workstationBlockId, deriveFallbackName(profKey)));
        }
        discovered.sort(Comparator.comparing(DiscoveredProfession::professionId));
        return discovered;
    }

    /**
     * Post-construction job assignment helper: verifies the workstation block at {@code workstationPos},
     * resolves {@code professionId} in {@link ForgeRegistries#VILLAGER_PROFESSIONS}, acquires a POI ticket
     * if available via {@link ServerLevel#getPoiManager()}, binds {@link MemoryModuleType#JOB_SITE},
     * updates {@code VillagerData} profession for the closest eligible unemployed adult non-nitwit
     * non-mayor villager, and guides navigation toward the workstation.
     *
     * @param level target server level
     * @param workstationPos world coordinates of the placed workstation block
     * @param professionId canonical namespaced profession ID
     * @param mayorUuid current village Mayor entity UUID (excluded from reassignment; nullable)
     * @param searchRadius search radius around {@code workstationPos}
     * @return {@code true} if an unemployed villager was assigned to the workstation
     */
    public static boolean assignVillagerToWorkstation(
            ServerLevel level,
            BlockPos workstationPos,
            String professionId,
            @Nullable UUID mayorUuid,
            int searchRadius
    ) {
        if (level == null || workstationPos == null || !level.hasChunkAt(workstationPos)) {
            return false;
        }
        BlockState wsState = level.getBlockState(workstationPos);
        if (wsState.isAir() || wsState.getBlock() == Blocks.AIR) {
            return false;
        }

        ResourceLocation profRl = TyC2Ids.parse(professionId);
        if (profRl == null || !ForgeRegistries.VILLAGER_PROFESSIONS.containsKey(profRl)) {
            return false;
        }
        VillagerProfession targetProf = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(profRl);
        if (targetProf == null || targetProf == VillagerProfession.NONE || targetProf == VillagerProfession.NITWIT) {
            return false;
        }

        int effectiveRadius = Math.max(16, searchRadius);
        AABB box = new AABB(workstationPos).inflate(effectiveRadius);
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, box, v -> {
            if (!v.isAlive() || v.isBaby()) {
                return false;
            }
            if (mayorUuid != null && mayorUuid.equals(v.getUUID())) {
                return false;
            }
            if (v.getPersistentData().getBoolean(TycoonForgeWorldManager.TAG_MAYOR)) {
                return false;
            }
            return v.getVillagerData().getProfession() == VillagerProfession.NONE;
        });
        if (candidates.isEmpty()) {
            return false;
        }

        double tx = workstationPos.getX() + 0.5;
        double ty = workstationPos.getY() + 0.5;
        double tz = workstationPos.getZ() + 0.5;
        candidates.sort(Comparator.comparingDouble(v -> v.distanceToSqr(tx, ty, tz)));
        Villager chosen = candidates.get(0);

        // Ensure POI exists and acquire ticket at workstationPos if available
        PoiManager poiManager = level.getPoiManager();
        Optional<Holder<PoiType>> poiHolder = findPoiHolderForState(wsState);
        if (poiHolder.isPresent() && poiManager.getType(workstationPos).isEmpty()) {
            poiManager.add(workstationPos, poiHolder.get());
        }
        Optional<BlockPos> claimed = poiManager.take(
                targetProf.heldJobSite(),
                (holder, pos) -> pos.equals(workstationPos),
                workstationPos,
                1
        );
        if (claimed.isEmpty()) {
            poiManager.take(
                    targetProf.acquirableJobSite(),
                    (holder, pos) -> pos.equals(workstationPos),
                    workstationPos,
                    1
            );
        }

        GlobalPos jobSite = GlobalPos.of(level.dimension(), workstationPos);
        chosen.releasePoi(MemoryModuleType.JOB_SITE);
        chosen.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        chosen.setVillagerData(chosen.getVillagerData().setProfession(targetProf));
        chosen.refreshBrain(level);
        chosen.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSite);
        chosen.getNavigation().moveTo(tx, workstationPos.getY() + 1.0, tz, 0.6D);
        return true;
    }

    /**
     * Builds a server-safe localized profession {@link Component} without emitting raw missing translation keys.
     */
    public static Component professionDisplayComponent(
            String professionId,
            @Nullable WorkplaceArchetype archetype,
            @Nullable String displayNameFallback
    ) {
        if (archetype != null && archetype != WorkplaceArchetype.GENERIC) {
            return Component.translatable(archetype.langKey());
        }
        if (displayNameFallback != null && !displayNameFallback.isBlank()) {
            return Component.literal(displayNameFallback.trim());
        }
        if (professionId != null && !professionId.isBlank()) {
            ResourceLocation rl = TyC2Ids.parse(professionId);
            if (rl != null) {
                return Component.literal(deriveFallbackName(rl));
            }
        }
        return Component.translatable("profession.tyc2.generic");
    }

    /**
     * Builds a localized workplace label combining profession and building size for notifications and HUD.
     */
    public static Component workplaceDisplayComponent(
            String professionId,
            @Nullable WorkplaceArchetype archetype,
            @Nullable String displayNameFallback,
            @Nullable WorkplaceSize size
    ) {
        Component prof = professionDisplayComponent(professionId, archetype, displayNameFallback);
        if (size == null) {
            return prof;
        }
        String projKey = size == WorkplaceSize.LARGE
                ? "project.tyc2.aid_workplace_large"
                : "project.tyc2.aid_workplace_small";
        return Component.empty()
                .append(prof)
                .append(Component.literal(" ("))
                .append(Component.translatable(projKey))
                .append(Component.literal(")"));
    }

    /**
     * Formats an {@link AidConstructionProject.Status} into its localized HUD/GUI status component.
     */
    public static Component aidStatusComponent(AidConstructionProject.Status status, int progressPct) {
        int clamped = Math.max(0, Math.min(100, progressPct));
        return switch (status) {
            case COLLECTING -> Component.translatable("gui.tyc2.aid.status.collecting", clamped);
            case WAITING_SITE -> Component.translatable("gui.tyc2.aid.status.waiting_site");
            case NO_SITE -> Component.translatable("gui.tyc2.aid.status.no_site");
            case BUILDING -> Component.translatable("gui.tyc2.aid.status.building", clamped);
        };
    }

    @Nullable
    private static String resolveWorkstationBlockId(VillagerProfession prof, List<Holder<PoiType>> poiHolders) {
        String fromHeld = findMatchingBlockId(prof.heldJobSite(), poiHolders);
        if (fromHeld != null) {
            return fromHeld;
        }
        return findMatchingBlockId(prof.acquirableJobSite(), poiHolders);
    }

    @Nullable
    private static String findMatchingBlockId(
            java.util.function.Predicate<Holder<PoiType>> predicate,
            List<Holder<PoiType>> poiHolders
    ) {
        if (predicate == null || predicate == PoiType.NONE || predicate == VillagerProfession.ALL_ACQUIRABLE_JOBS) {
            return null;
        }
        List<Holder<PoiType>> matched = new ArrayList<>();
        for (Holder<PoiType> holder : poiHolders) {
            try {
                if (predicate.test(holder)) {
                    matched.add(holder);
                }
            } catch (Exception ignored) {
                // Guard against exotic modded POI predicates
            }
        }
        // Skip overly broad wildcard predicates that match nearly every POI in the registry
        if (matched.isEmpty() || matched.size() > 8) {
            return null;
        }
        for (Holder<PoiType> holder : matched) {
            PoiType poi = holder.value();
            if (poi == null || poi.matchingStates() == null) {
                continue;
            }
            for (BlockState state : poi.matchingStates()) {
                if (state == null || state.isAir()) {
                    continue;
                }
                Block block = state.getBlock();
                if (block == Blocks.AIR) {
                    continue;
                }
                String id = TyC2Ids.blockId(block);
                if (!id.isEmpty() && !"minecraft:air".equals(id)) {
                    return id.toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private static Optional<Holder<PoiType>> findPoiHolderForState(BlockState state) {
        Optional<Holder<PoiType>> direct = PoiTypes.forState(state);
        if (direct.isPresent()) {
            return direct;
        }
        for (Map.Entry<ResourceKey<PoiType>, PoiType> entry : ForgeRegistries.POI_TYPES.getEntries()) {
            PoiType poi = entry.getValue();
            if (poi != null && poi.is(state)) {
                Optional<Holder<PoiType>> holder = ForgeRegistries.POI_TYPES.getHolder(entry.getKey());
                if (holder.isPresent()) {
                    return holder;
                }
            }
        }
        return Optional.empty();
    }

    private static String deriveFallbackName(ResourceLocation profKey) {
        String path = profKey.getPath();
        if (path == null || path.isEmpty()) {
            return profKey.toString();
        }
        String[] parts = path.split("[_-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
