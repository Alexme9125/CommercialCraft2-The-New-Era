package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.util.TyC2VillagerHelpers;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared server-to-client NBT telemetry helper for Forge 1.19.2 WTHIT/HUD plugins with zero
 * compile-time dependency on optional HUD mods.
 */
public final class TyC2ProbeHelper {

    private static final String KEY_KIND = "mnera_kind";
    private static final String KIND_HUB = "hub";
    private static final String KIND_PRIMITIVE_MINT = "primitive_mint";
    private static final String KIND_MINT = "mint";
    private static final String KIND_ATM = "atm";
    private static final String KIND_MAYOR = "mayor";

    private TyC2ProbeHelper() {
    }

    /** Serializes live server-side telemetry for any TyC2 machine or Cooperation Hub into {@code tag}. */
    public static void writeBlockData(CompoundTag tag, ServerLevel level, BlockEntity be) {
        if (tag == null || level == null || be == null) {
            return;
        }
        if (be instanceof CooperationHubBlockEntity hub) {
            UUID vid = hub.resolveVillageId();
            if (vid == null) {
                return;
            }
            TycoonForgeSavedData data = TycoonForgeSavedData.get(level);
            Optional<VillageRecord> vOpt = data.getVillage(vid);
            if (vOpt.isEmpty()) {
                return;
            }
            VillageRecord v = vOpt.get();
            long sellCount = v.getActiveOrders().stream()
                    .filter(o -> o.side() == OrderSide.VILLAGE_SELLS && !o.isCompleted())
                    .count();
            long buyCount = v.getActiveOrders().stream()
                    .filter(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted())
                    .count();
            MacroEconomyEngine econ = data.economy();
            String regimeKey = switch (econ.getInflationState()) {
                case DEFLATION -> "gui.mnera.economy.deflation";
                case STABLE -> "gui.mnera.economy.stable";
                default -> "gui.mnera.economy.inflation";
            };
            tag.putString(KEY_KIND, KIND_HUB);
            tag.putString("mnera_village", v.getVillageName());
            tag.putInt("mnera_level", v.getLevel());
            tag.putInt("mnera_pop", v.getPopulation());
            tag.putLong("mnera_funds", v.getVillageBalance());
            tag.putInt("mnera_sells", (int) sellCount);
            tag.putInt("mnera_buys", (int) buyCount);
            tag.putDouble("mnera_cpi", econ.computeCpi());
            tag.putString("mnera_cpi_state", regimeKey);
            tag.putInt("mnera_in_used", v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
            tag.putInt("mnera_out_used", v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));
            tag.putBoolean("mnera_chunk_loader", v.isChunkLoaderInstalled());

            TyC2Config cfg = TyC2RuntimeData.config();
            VillageEmploymentStats emp = v.getEmploymentStats();
            int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
            tag.putInt("mnera_unemp_pct", unempPct);
            tag.putInt("mnera_unemp_cnt", emp.unemployedCount());
            tag.putInt("mnera_workforce", emp.workforceCount());
            if (emp.isLowUnemploymentBonusActive(cfg)) {
                tag.putBoolean("mnera_emp_bonus", true);
                tag.putInt("mnera_qty_pct", (int) Math.round((emp.tradeQuantityMultiplier(cfg) - 1.0) * 100.0));
            }
            if (!v.getAidProjects().isEmpty()) {
                AidConstructionProject headAid = v.getAidProjects().get(0);
                for (AidConstructionProject candidate : v.getAidProjects()) {
                    if (candidate.status() == AidConstructionProject.Status.BUILDING) {
                        headAid = candidate;
                        break;
                    }
                }
                int progPct = TycoonForgeWorldManager.resolveAidProgressPct(v, headAid);
                tag.putBoolean("mnera_aid_active", true);
                tag.putString("mnera_aid_prof", headAid.professionId() == null ? "" : headAid.professionId());
                tag.putString("mnera_aid_arch", headAid.archetype() == null ? "" : headAid.archetype().name());
                tag.putString("mnera_aid_fallback", headAid.displayNameFallback() == null ? "" : headAid.displayNameFallback());
                tag.putBoolean("mnera_aid_large", headAid.size() == WorkplaceSize.LARGE);
                tag.putString("mnera_aid_status", headAid.status().name());
                tag.putInt("mnera_aid_pct", progPct);
            }
        } else if (be instanceof PrimitiveMintMachineBlockEntity prim) {
            tag.putString(KEY_KIND, KIND_PRIMITIVE_MINT);
            tag.putInt("mnera_burn_sec", Math.max(0, prim.getBurnTicksRemaining() / 20));
            tag.putInt("mnera_progress", prim.getProgressPercent());
            tag.putBoolean("mnera_blocked_ind", prim.isBlockedByIndustrialTier());
        } else if (be instanceof MintMachineBlockEntity mint) {
            tag.putString(KEY_KIND, KIND_MINT);
            tag.putInt("mnera_eu", (int) Math.round(mint.getStoredEu()));
            tag.putInt("mnera_max_eu", mint.getMaxCapacityEu());
            tag.putInt("mnera_progress", mint.getProgressPercent());
        } else if (be instanceof AtmMachineBlockEntity atm) {
            tag.putString(KEY_KIND, KIND_ATM);
            tag.putInt("mnera_eu", (int) Math.round(atm.getStoredEu()));
            tag.putInt("mnera_max_eu", TriModePowerMath.ATM_BASE_CAPACITY_EU);
        }
    }

    /** Reconstructs localized client-side HUD lines from {@link #writeBlockData} NBT. */
    public static List<Component> readBlockLines(CompoundTag tag) {
        if (tag == null) {
            return List.of();
        }
        String kind = tag.getString(KEY_KIND);
        if (kind.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        switch (kind) {
            case KIND_HUB -> {
                lines.add(Component.translatable("hud.mnera.hub.village", tag.getString("mnera_village")));
                lines.add(Component.translatable("hud.mnera.hub.level_pop", tag.getInt("mnera_level"), tag.getInt("mnera_pop")));
                lines.add(Component.translatable("hud.mnera.hub.funds", tag.getLong("mnera_funds")));
                lines.add(Component.translatable("hud.mnera.hub.orders", tag.getInt("mnera_sells"), tag.getInt("mnera_buys")));
                lines.add(Component.translatable("hud.mnera.hub.warehouse", tag.getInt("mnera_in_used"), tag.getInt("mnera_out_used")));
                String stateKey = tag.getString("mnera_cpi_state");
                if (stateKey.isEmpty()) {
                    stateKey = "gui.mnera.economy.stable";
                }
                lines.add(Component.translatable(
                        "hud.mnera.hub.cpi",
                        String.format(Locale.ROOT, "%.2f", tag.getDouble("mnera_cpi")),
                        Component.translatable(stateKey)
                ));
                lines.add(Component.translatable(
                        "hud.mnera.hub.unemployment",
                        tag.getInt("mnera_unemp_pct"),
                        tag.getInt("mnera_unemp_cnt"),
                        tag.getInt("mnera_workforce")
                ));
                if (tag.getBoolean("mnera_emp_bonus")) {
                    lines.add(Component.translatable(
                            "hud.mnera.hub.employment_bonus",
                            tag.getInt("mnera_qty_pct")
                    ));
                }
                if (tag.getBoolean("mnera_chunk_loader")) {
                    lines.add(Component.translatable("gui.mnera.hub.chunk_loader.active"));
                }
                if (tag.getBoolean("mnera_aid_active")) {
                    WorkplaceArchetype arch = parseArchetype(tag.getString("mnera_aid_arch"));
                    WorkplaceSize size = tag.getBoolean("mnera_aid_large") ? WorkplaceSize.LARGE : WorkplaceSize.SMALL;
                    Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                            tag.getString("mnera_aid_prof"),
                            arch,
                            tag.getString("mnera_aid_fallback"),
                            size
                    );
                    AidConstructionProject.Status st = parseAidStatus(tag.getString("mnera_aid_status"));
                    Component statusComp = TyC2VillagerHelpers.aidStatusComponent(st, tag.getInt("mnera_aid_pct"));
                    lines.add(Component.translatable("hud.mnera.hub.aid_active", wpName, statusComp));
                }
            }
            case KIND_PRIMITIVE_MINT -> {
                lines.add(Component.translatable("hud.mnera.primitive_mint.scope"));
                lines.add(Component.translatable("hud.mnera.primitive_mint.fuel", tag.getInt("mnera_burn_sec")));
                lines.add(Component.translatable("hud.mnera.primitive_mint.progress", tag.getInt("mnera_progress")));
                if (tag.getBoolean("mnera_blocked_ind")) {
                    lines.add(Component.translatable("gui.mnera.primitive_mint.requires_industrial"));
                }
            }
            case KIND_MINT -> {
                int eu = tag.getInt("mnera_eu");
                int maxEu = tag.getInt("mnera_max_eu");
                lines.add(Component.translatable("hud.mnera.mint.power", eu, maxEu, (long) eu * TriModePowerMath.EU_TO_FE_RATIO));
                lines.add(Component.translatable("hud.mnera.mint.progress", tag.getInt("mnera_progress")));
            }
            case KIND_ATM -> {
                lines.add(Component.translatable("hud.mnera.atm.ready"));
                lines.add(Component.translatable("hud.mnera.atm.power", tag.getInt("mnera_eu"), tag.getInt("mnera_max_eu")));
            }
            default -> {
            }
        }
        return lines;
    }

    /** Serializes Village Mayor telemetry into {@code tag} when {@code entity} is a TyC2 Mayor. */
    public static void writeEntityData(CompoundTag tag, ServerLevel level, Entity entity) {
        if (tag == null || level == null || !(entity instanceof Villager villager)) {
            return;
        }
        CompoundTag pdata = villager.getPersistentData();
        if (!pdata.getBoolean(TycoonForgeWorldManager.TAG_MAYOR)) {
            return;
        }
        tag.putString(KEY_KIND, KIND_MAYOR);
        if (!pdata.contains(TycoonForgeWorldManager.TAG_VILLAGE_ID)) {
            return;
        }
        try {
            UUID vid = UUID.fromString(pdata.getString(TycoonForgeWorldManager.TAG_VILLAGE_ID));
            TycoonForgeSavedData.get(level).getVillage(vid).ifPresent(v -> {
                tag.putBoolean("mnera_has_village", true);
                tag.putBoolean("mnera_partnered", v.isPartnered());
                tag.putInt("mnera_level", v.getLevel());
                tag.putLong("mnera_funds", v.getVillageBalance());
                VillageEmploymentStats emp = v.getEmploymentStats();
                int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
                tag.putInt("mnera_unemp_pct", unempPct);
                tag.putInt("mnera_unemp_cnt", emp.unemployedCount());
                tag.putInt("mnera_workforce", emp.workforceCount());
            });
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed UUID
        }
    }

    /** Reconstructs localized client-side HUD lines for a Village Mayor entity. */
    public static List<Component> readEntityLines(CompoundTag tag) {
        if (tag == null || !KIND_MAYOR.equals(tag.getString(KEY_KIND))) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(3);
        if (!tag.getBoolean("mnera_has_village")) {
            lines.add(Component.translatable("hud.mnera.mayor.unpartnered"));
            return lines;
        }
        if (tag.getBoolean("mnera_partnered")) {
            lines.add(Component.translatable("hud.mnera.mayor.partnered", tag.getInt("mnera_level")));
            lines.add(Component.translatable("hud.mnera.hub.funds", tag.getLong("mnera_funds")));
        } else {
            lines.add(Component.translatable("hud.mnera.mayor.unpartnered"));
        }
        lines.add(Component.translatable(
                "hud.mnera.mayor.unemployment",
                tag.getInt("mnera_unemp_pct"),
                tag.getInt("mnera_unemp_cnt"),
                tag.getInt("mnera_workforce")
        ));
        return lines;
    }

    private static WorkplaceArchetype parseArchetype(String raw) {
        if (raw == null || raw.isBlank()) {
            return WorkplaceArchetype.GENERIC;
        }
        try {
            return WorkplaceArchetype.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return WorkplaceArchetype.GENERIC;
        }
    }

    private static AidConstructionProject.Status parseAidStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return AidConstructionProject.Status.COLLECTING;
        }
        try {
            return AidConstructionProject.Status.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return AidConstructionProject.Status.COLLECTING;
        }
    }
}
