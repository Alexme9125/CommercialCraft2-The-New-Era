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

    private static final String KEY_KIND = "tyc2_kind";
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
                case DEFLATION -> "gui.tyc2.economy.deflation";
                case STABLE -> "gui.tyc2.economy.stable";
                default -> "gui.tyc2.economy.inflation";
            };
            tag.putString(KEY_KIND, KIND_HUB);
            tag.putString("tyc2_village", v.getVillageName());
            tag.putInt("tyc2_level", v.getLevel());
            tag.putInt("tyc2_pop", v.getPopulation());
            tag.putLong("tyc2_funds", v.getVillageBalance());
            tag.putInt("tyc2_sells", (int) sellCount);
            tag.putInt("tyc2_buys", (int) buyCount);
            tag.putDouble("tyc2_cpi", econ.computeCpi());
            tag.putString("tyc2_cpi_state", regimeKey);
            tag.putInt("tyc2_in_used", v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
            tag.putInt("tyc2_out_used", v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));
            tag.putBoolean("tyc2_chunk_loader", v.isChunkLoaderInstalled());

            TyC2Config cfg = TyC2RuntimeData.config();
            VillageEmploymentStats emp = v.getEmploymentStats();
            int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
            tag.putInt("tyc2_unemp_pct", unempPct);
            tag.putInt("tyc2_unemp_cnt", emp.unemployedCount());
            tag.putInt("tyc2_workforce", emp.workforceCount());
            if (emp.isLowUnemploymentBonusActive(cfg)) {
                tag.putBoolean("tyc2_emp_bonus", true);
                tag.putInt("tyc2_qty_pct", (int) Math.round((emp.tradeQuantityMultiplier(cfg) - 1.0) * 100.0));
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
                tag.putBoolean("tyc2_aid_active", true);
                tag.putString("tyc2_aid_prof", headAid.professionId() == null ? "" : headAid.professionId());
                tag.putString("tyc2_aid_arch", headAid.archetype() == null ? "" : headAid.archetype().name());
                tag.putString("tyc2_aid_fallback", headAid.displayNameFallback() == null ? "" : headAid.displayNameFallback());
                tag.putBoolean("tyc2_aid_large", headAid.size() == WorkplaceSize.LARGE);
                tag.putString("tyc2_aid_status", headAid.status().name());
                tag.putInt("tyc2_aid_pct", progPct);
            }
        } else if (be instanceof PrimitiveMintMachineBlockEntity prim) {
            tag.putString(KEY_KIND, KIND_PRIMITIVE_MINT);
            tag.putInt("tyc2_burn_sec", Math.max(0, prim.getBurnTicksRemaining() / 20));
            tag.putInt("tyc2_progress", prim.getProgressPercent());
            tag.putBoolean("tyc2_blocked_ind", prim.isBlockedByIndustrialTier());
        } else if (be instanceof MintMachineBlockEntity mint) {
            tag.putString(KEY_KIND, KIND_MINT);
            tag.putInt("tyc2_eu", (int) Math.round(mint.getStoredEu()));
            tag.putInt("tyc2_max_eu", mint.getMaxCapacityEu());
            tag.putInt("tyc2_progress", mint.getProgressPercent());
        } else if (be instanceof AtmMachineBlockEntity atm) {
            tag.putString(KEY_KIND, KIND_ATM);
            tag.putInt("tyc2_eu", (int) Math.round(atm.getStoredEu()));
            tag.putInt("tyc2_max_eu", TriModePowerMath.ATM_BASE_CAPACITY_EU);
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
                lines.add(Component.translatable("hud.tyc2.hub.village", tag.getString("tyc2_village")));
                lines.add(Component.translatable("hud.tyc2.hub.level_pop", tag.getInt("tyc2_level"), tag.getInt("tyc2_pop")));
                lines.add(Component.translatable("hud.tyc2.hub.funds", tag.getLong("tyc2_funds")));
                lines.add(Component.translatable("hud.tyc2.hub.orders", tag.getInt("tyc2_sells"), tag.getInt("tyc2_buys")));
                lines.add(Component.translatable("hud.tyc2.hub.warehouse", tag.getInt("tyc2_in_used"), tag.getInt("tyc2_out_used")));
                String stateKey = tag.getString("tyc2_cpi_state");
                if (stateKey.isEmpty()) {
                    stateKey = "gui.tyc2.economy.stable";
                }
                lines.add(Component.translatable(
                        "hud.tyc2.hub.cpi",
                        String.format(Locale.ROOT, "%.2f", tag.getDouble("tyc2_cpi")),
                        Component.translatable(stateKey)
                ));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.unemployment",
                        tag.getInt("tyc2_unemp_pct"),
                        tag.getInt("tyc2_unemp_cnt"),
                        tag.getInt("tyc2_workforce")
                ));
                if (tag.getBoolean("tyc2_emp_bonus")) {
                    lines.add(Component.translatable(
                            "hud.tyc2.hub.employment_bonus",
                            tag.getInt("tyc2_qty_pct")
                    ));
                }
                if (tag.getBoolean("tyc2_chunk_loader")) {
                    lines.add(Component.translatable("gui.tyc2.hub.chunk_loader.active"));
                }
                if (tag.getBoolean("tyc2_aid_active")) {
                    WorkplaceArchetype arch = parseArchetype(tag.getString("tyc2_aid_arch"));
                    WorkplaceSize size = tag.getBoolean("tyc2_aid_large") ? WorkplaceSize.LARGE : WorkplaceSize.SMALL;
                    Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                            tag.getString("tyc2_aid_prof"),
                            arch,
                            tag.getString("tyc2_aid_fallback"),
                            size
                    );
                    AidConstructionProject.Status st = parseAidStatus(tag.getString("tyc2_aid_status"));
                    Component statusComp = TyC2VillagerHelpers.aidStatusComponent(st, tag.getInt("tyc2_aid_pct"));
                    lines.add(Component.translatable("hud.tyc2.hub.aid_active", wpName, statusComp));
                }
            }
            case KIND_PRIMITIVE_MINT -> {
                lines.add(Component.translatable("hud.tyc2.primitive_mint.scope"));
                lines.add(Component.translatable("hud.tyc2.primitive_mint.fuel", tag.getInt("tyc2_burn_sec")));
                lines.add(Component.translatable("hud.tyc2.primitive_mint.progress", tag.getInt("tyc2_progress")));
                if (tag.getBoolean("tyc2_blocked_ind")) {
                    lines.add(Component.translatable("gui.tyc2.primitive_mint.requires_industrial"));
                }
            }
            case KIND_MINT -> {
                int eu = tag.getInt("tyc2_eu");
                int maxEu = tag.getInt("tyc2_max_eu");
                lines.add(Component.translatable("hud.tyc2.mint.power", eu, maxEu, (long) eu * TriModePowerMath.EU_TO_FE_RATIO));
                lines.add(Component.translatable("hud.tyc2.mint.progress", tag.getInt("tyc2_progress")));
            }
            case KIND_ATM -> {
                lines.add(Component.translatable("hud.tyc2.atm.ready"));
                lines.add(Component.translatable("hud.tyc2.atm.power", tag.getInt("tyc2_eu"), tag.getInt("tyc2_max_eu")));
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
                tag.putBoolean("tyc2_has_village", true);
                tag.putBoolean("tyc2_partnered", v.isPartnered());
                tag.putInt("tyc2_level", v.getLevel());
                tag.putLong("tyc2_funds", v.getVillageBalance());
                VillageEmploymentStats emp = v.getEmploymentStats();
                int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
                tag.putInt("tyc2_unemp_pct", unempPct);
                tag.putInt("tyc2_unemp_cnt", emp.unemployedCount());
                tag.putInt("tyc2_workforce", emp.workforceCount());
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
        if (!tag.getBoolean("tyc2_has_village")) {
            lines.add(Component.translatable("hud.tyc2.mayor.unpartnered"));
            return lines;
        }
        if (tag.getBoolean("tyc2_partnered")) {
            lines.add(Component.translatable("hud.tyc2.mayor.partnered", tag.getInt("tyc2_level")));
            lines.add(Component.translatable("hud.tyc2.hub.funds", tag.getLong("tyc2_funds")));
        } else {
            lines.add(Component.translatable("hud.tyc2.mayor.unpartnered"));
        }
        lines.add(Component.translatable(
                "hud.tyc2.mayor.unemployment",
                tag.getInt("tyc2_unemp_pct"),
                tag.getInt("tyc2_unemp_cnt"),
                tag.getInt("tyc2_workforce")
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
