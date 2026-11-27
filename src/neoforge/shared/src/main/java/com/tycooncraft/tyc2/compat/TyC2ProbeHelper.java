package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.blockentity.MintMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.CooperationHubStorage;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.WorkplaceArchetype;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import com.tycooncraft.tyc2.world.TycoonSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared server-to-client NBT telemetry helper for NeoForge WTHIT/HUD plugins across
 * 1.21.1, 26.1.2, and 26.2 with zero compile-time dependency on optional HUD mods.
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
            VillageRecord v = hub.resolveVillage(level);
            if (v == null) {
                return;
            }
            TycoonSavedData data = TyC2WorldManager.getData(level);
            long sells = v.getActiveOrders().stream()
                    .filter(o -> o.side() == OrderSide.VILLAGE_SELLS && !o.isCompleted())
                    .count();
            long buys = v.getActiveOrders().stream()
                    .filter(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted())
                    .count();
            MacroEconomyEngine econ = data.economy();
            double cpi = econ.computeCpi();
            String stateKey = switch (econ.getInflationState()) {
                case DEFLATION -> "gui.tyc2.economy.deflation";
                case STABLE -> "gui.tyc2.economy.stable";
                default -> "gui.tyc2.economy.inflation";
            };
            tag.putString(KEY_KIND, KIND_HUB);
            tag.putString("tyc2_village", v.getVillageName());
            tag.putInt("tyc2_level", v.getLevel());
            tag.putInt("tyc2_pop", v.getPopulation());
            tag.putLong("tyc2_funds", v.getVillageBalance());
            tag.putInt("tyc2_sells", (int) sells);
            tag.putInt("tyc2_buys", (int) buys);
            tag.putDouble("tyc2_cpi", cpi);
            tag.putString("tyc2_cpi_state", stateKey);
            tag.putInt("tyc2_in_used", v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.INBOUND));
            tag.putInt("tyc2_out_used", v.getStorage().getUsedSlotCount(CooperationHubStorage.Zone.OUTBOUND));
            tag.putBoolean("tyc2_chunk_loader", hub.isChunkLoaderActive());

            VillageEmploymentStats es = v.getEmploymentStats();
            TyC2Config cfg = TyC2DataLoader.config();
            int unempPct = es.workforceCount() <= 0 ? 0 : (int) Math.round(es.unemploymentRate() * 100.0);
            tag.putInt("tyc2_unemp_pct", unempPct);
            tag.putInt("tyc2_unemp_cnt", es.unemployedCount());
            tag.putInt("tyc2_workforce", es.workforceCount());
            if (es.isLowUnemploymentBonusActive(cfg)) {
                tag.putBoolean("tyc2_emp_bonus", true);
                tag.putInt("tyc2_qty_pct", (int) Math.round((es.tradeQuantityMultiplier(cfg) - 1.0) * 100.0));
            }
            if (!v.getAidProjects().isEmpty()) {
                AidConstructionProject head = v.getAidProjects().get(0);
                for (AidConstructionProject candidate : v.getAidProjects()) {
                    if (candidate.status() == AidConstructionProject.Status.BUILDING) {
                        head = candidate;
                        break;
                    }
                }
                final AidConstructionProject activeAid = head;
                tag.putBoolean("tyc2_aid_active", true);
                String archKey = (activeAid.archetype() != null && activeAid.archetype() != WorkplaceArchetype.GENERIC)
                        ? activeAid.archetype().langKey()
                        : "";
                tag.putString("tyc2_aid_arch_key", archKey);
                tag.putString("tyc2_aid_fallback", activeAid.displayNameFallback() == null ? "" : activeAid.displayNameFallback());
                tag.putBoolean("tyc2_aid_large", activeAid.size() == WorkplaceSize.LARGE);
                tag.putString("tyc2_aid_status", activeAid.status().name());
                int aidPct = switch (activeAid.status()) {
                    case COLLECTING -> (int) Math.round(activeAid.completionRatio() * 100.0);
                    case BUILDING -> v.getConstructionQueue().stream()
                            .filter(j -> activeAid.plannedJobId().equals(j.getJobId()))
                            .findFirst()
                            .map(j -> (int) Math.round(j.getProgressRatio() * 100.0))
                            .orElse(0);
                    default -> 0;
                };
                tag.putInt("tyc2_aid_pct", Math.max(0, Math.min(100, aidPct)));
            }
        } else if (be instanceof PrimitiveMintMachineBlockEntity prim) {
            int burnSec = (prim.burnTimeRemaining() + 19) / 20;
            int pct = prim.maxProgress() > 0
                    ? Math.min(100, (int) Math.round(100.0 * prim.progress() / prim.maxProgress()))
                    : 0;
            tag.putString(KEY_KIND, KIND_PRIMITIVE_MINT);
            tag.putInt("tyc2_burn_sec", Math.max(0, burnSec));
            tag.putInt("tyc2_progress", pct);
            tag.putBoolean("tyc2_blocked_ind", prim.isBlockedByIndustrialTier());
        } else if (be instanceof MintMachineBlockEntity mint) {
            int eu = (int) Math.floor(mint.storedEu());
            int maxEu = (int) mint.maxCapacityEu();
            int pct = mint.maxProgress() > 0
                    ? Math.min(100, (int) Math.round(100.0 * mint.progress() / mint.maxProgress()))
                    : 0;
            tag.putString(KEY_KIND, KIND_MINT);
            tag.putInt("tyc2_eu", eu);
            tag.putInt("tyc2_max_eu", maxEu);
            tag.putInt("tyc2_progress", pct);
        } else if (be instanceof AtmMachineBlockEntity atm) {
            tag.putString(KEY_KIND, KIND_ATM);
            tag.putInt("tyc2_eu", (int) Math.floor(atm.storedEu()));
            tag.putInt("tyc2_max_eu", (int) atm.maxCapacityEu());
        }
    }

    /** Reconstructs localized client-side HUD lines from {@link #writeBlockData} NBT. */
    public static List<Component> readBlockLines(CompoundTag tag) {
        if (tag == null) {
            return List.of();
        }
        String kind = TyC2Nbt.getString(tag, KEY_KIND, "");
        if (kind.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        switch (kind) {
            case KIND_HUB -> {
                lines.add(Component.translatable("hud.tyc2.hub.village", TyC2Nbt.getString(tag, "tyc2_village", "")));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.level_pop",
                        TyC2Nbt.getInt(tag, "tyc2_level", 1),
                        TyC2Nbt.getInt(tag, "tyc2_pop", 0)
                ));
                lines.add(Component.translatable("hud.tyc2.hub.funds", TyC2Nbt.getLong(tag, "tyc2_funds", 0L)));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.orders",
                        TyC2Nbt.getInt(tag, "tyc2_sells", 0),
                        TyC2Nbt.getInt(tag, "tyc2_buys", 0)
                ));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.warehouse",
                        TyC2Nbt.getInt(tag, "tyc2_in_used", 0),
                        TyC2Nbt.getInt(tag, "tyc2_out_used", 0)
                ));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.cpi",
                        String.format(Locale.ROOT, "%.2f", TyC2Nbt.getDouble(tag, "tyc2_cpi", 1.0)),
                        Component.translatable(TyC2Nbt.getString(tag, "tyc2_cpi_state", "gui.tyc2.economy.stable"))
                ));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.unemployment",
                        TyC2Nbt.getInt(tag, "tyc2_unemp_pct", 0),
                        TyC2Nbt.getInt(tag, "tyc2_unemp_cnt", 0),
                        TyC2Nbt.getInt(tag, "tyc2_workforce", 0)
                ));
                if (TyC2Nbt.getBoolean(tag, "tyc2_emp_bonus", false)) {
                    lines.add(Component.translatable(
                            "hud.tyc2.hub.employment_bonus",
                            TyC2Nbt.getInt(tag, "tyc2_qty_pct", 0)
                    ));
                }
                if (TyC2Nbt.getBoolean(tag, "tyc2_chunk_loader", false)) {
                    lines.add(Component.translatable("gui.tyc2.hub.chunk_loader.active"));
                }
                if (TyC2Nbt.getBoolean(tag, "tyc2_aid_active", false)) {
                    lines.add(Component.translatable(
                            "hud.tyc2.hub.aid_active",
                            buildAidWorkplaceComponent(tag),
                            buildAidStatusComponent(tag)
                    ));
                }
            }
            case KIND_PRIMITIVE_MINT -> {
                lines.add(Component.translatable("hud.tyc2.primitive_mint.scope"));
                lines.add(Component.translatable("hud.tyc2.primitive_mint.fuel", TyC2Nbt.getInt(tag, "tyc2_burn_sec", 0)));
                lines.add(Component.translatable("hud.tyc2.primitive_mint.progress", TyC2Nbt.getInt(tag, "tyc2_progress", 0)));
                if (TyC2Nbt.getBoolean(tag, "tyc2_blocked_ind", false)) {
                    lines.add(Component.translatable("gui.tyc2.primitive_mint.requires_industrial"));
                }
            }
            case KIND_MINT -> {
                int eu = TyC2Nbt.getInt(tag, "tyc2_eu", 0);
                int maxEu = TyC2Nbt.getInt(tag, "tyc2_max_eu", 0);
                lines.add(Component.translatable("hud.tyc2.mint.power", eu, maxEu, TriModePowerMath.euToFe(eu)));
                lines.add(Component.translatable("hud.tyc2.mint.progress", TyC2Nbt.getInt(tag, "tyc2_progress", 0)));
            }
            case KIND_ATM -> {
                lines.add(Component.translatable("hud.tyc2.atm.ready"));
                lines.add(Component.translatable(
                        "hud.tyc2.atm.power",
                        TyC2Nbt.getInt(tag, "tyc2_eu", 0),
                        TyC2Nbt.getInt(tag, "tyc2_max_eu", 0)
                ));
            }
            default -> {
            }
        }
        return lines;
    }

    /** Serializes Village Mayor telemetry into {@code tag} when {@code entity} is a TyC2 Mayor. */
    public static void writeEntityData(CompoundTag tag, ServerLevel level, Entity entity) {
        if (tag == null || level == null || entity == null) {
            return;
        }
        if (!TyC2Nbt.getBoolean(entity.getPersistentData(), TyC2Constants.TAG_MAYOR, false)) {
            return;
        }
        tag.putString(KEY_KIND, KIND_MAYOR);
        String vidStr = TyC2Nbt.getString(entity.getPersistentData(), TyC2Constants.TAG_MAYOR_VILLAGE_ID, "");
        if (vidStr.isBlank()) {
            return;
        }
        try {
            UUID vid = UUID.fromString(vidStr);
            VillageRecord v = TyC2WorldManager.getData(level).getVillage(vid).orElse(null);
            if (v != null) {
                tag.putBoolean("tyc2_has_village", true);
                tag.putBoolean("tyc2_partnered", v.isPartnered());
                tag.putInt("tyc2_level", v.getLevel());
                tag.putLong("tyc2_funds", v.getVillageBalance());
                VillageEmploymentStats es = v.getEmploymentStats();
                int unempPct = es.workforceCount() <= 0 ? 0 : (int) Math.round(es.unemploymentRate() * 100.0);
                tag.putInt("tyc2_unemp_pct", unempPct);
                tag.putInt("tyc2_unemp_cnt", es.unemployedCount());
                tag.putInt("tyc2_workforce", es.workforceCount());
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to unpartnered mayor marker
        }
    }

    /** Reconstructs localized client-side HUD lines for a Village Mayor entity. */
    public static List<Component> readEntityLines(CompoundTag tag) {
        if (tag == null || !KIND_MAYOR.equals(TyC2Nbt.getString(tag, KEY_KIND, ""))) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(3);
        if (!TyC2Nbt.getBoolean(tag, "tyc2_has_village", false)) {
            lines.add(Component.translatable("hud.tyc2.mayor.unpartnered"));
            return lines;
        }
        if (TyC2Nbt.getBoolean(tag, "tyc2_partnered", false)) {
            lines.add(Component.translatable("hud.tyc2.mayor.partnered", TyC2Nbt.getInt(tag, "tyc2_level", 1)));
            lines.add(Component.translatable("hud.tyc2.hub.funds", TyC2Nbt.getLong(tag, "tyc2_funds", 0L)));
        } else {
            lines.add(Component.translatable("hud.tyc2.mayor.unpartnered"));
        }
        lines.add(Component.translatable(
                "hud.tyc2.mayor.unemployment",
                TyC2Nbt.getInt(tag, "tyc2_unemp_pct", 0),
                TyC2Nbt.getInt(tag, "tyc2_unemp_cnt", 0),
                TyC2Nbt.getInt(tag, "tyc2_workforce", 0)
        ));
        return lines;
    }

    private static Component buildAidWorkplaceComponent(CompoundTag tag) {
        String archKey = TyC2Nbt.getString(tag, "tyc2_aid_arch_key", "");
        String fallback = TyC2Nbt.getString(tag, "tyc2_aid_fallback", "");
        MutableComponent profComp;
        if (!archKey.isBlank()) {
            profComp = Component.translatable(archKey);
        } else if (!fallback.isBlank()) {
            profComp = Component.literal(fallback);
        } else {
            profComp = Component.translatable(WorkplaceArchetype.GENERIC.langKey());
        }
        Component sizeComp = Component.translatable(
                TyC2Nbt.getBoolean(tag, "tyc2_aid_large", false)
                        ? "gui.tyc2.aid.size.large"
                        : "gui.tyc2.aid.size.small"
        );
        return profComp.append(" ").append(sizeComp);
    }

    private static Component buildAidStatusComponent(CompoundTag tag) {
        String status = TyC2Nbt.getString(tag, "tyc2_aid_status", "COLLECTING");
        int pct = TyC2Nbt.getInt(tag, "tyc2_aid_pct", 0);
        return switch (status) {
            case "BUILDING" -> Component.translatable("gui.tyc2.aid.status.building", pct);
            case "WAITING_SITE" -> Component.translatable("gui.tyc2.aid.status.waiting_site");
            case "NO_SITE" -> Component.translatable("gui.tyc2.aid.status.no_site");
            default -> Component.translatable("gui.tyc2.aid.status.collecting", pct);
        };
    }
}
