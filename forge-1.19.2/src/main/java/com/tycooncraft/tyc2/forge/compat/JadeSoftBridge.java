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
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/**
 * Shared HUD tooltip line builder and soft Jade ({@code jade}) integration bridge with zero
 * compile-time dependency on Jade.
 */
public final class JadeSoftBridge {

    public static final String MOD_ID = "jade";

    private JadeSoftBridge() {
    }

    /** Returns {@code true} if Jade is loaded at runtime. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Builds localized HUD lines for any TyC2 block entity at {@code pos}. */
    public static List<Component> buildBlockHudLines(Level level, BlockPos pos) {
        List<Component> lines = new ArrayList<>();
        if (level == null || pos == null) {
            return lines;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PrimitiveMintMachineBlockEntity prim) {
            lines.add(Component.translatable("hud.mnera.primitive_mint.scope"));
            lines.add(Component.translatable("hud.mnera.primitive_mint.fuel", Math.max(0, prim.getBurnTicksRemaining() / 20)));
            lines.add(Component.translatable("hud.mnera.primitive_mint.progress", prim.getProgressPercent()));
            if (prim.isBlockedByIndustrialTier()) {
                lines.add(Component.translatable("gui.mnera.primitive_mint.requires_industrial"));
            }
        } else if (be instanceof MintMachineBlockEntity mint) {
            int eu = (int) Math.round(mint.getStoredEu());
            int max = mint.getMaxCapacityEu();
            lines.add(Component.translatable("hud.mnera.mint.power", eu, max, (long) eu * TriModePowerMath.EU_TO_FE_RATIO));
            lines.add(Component.translatable("hud.mnera.mint.progress", mint.getProgressPercent()));
        } else if (be instanceof AtmMachineBlockEntity atm) {
            int eu = (int) Math.round(atm.getStoredEu());
            lines.add(Component.translatable("hud.mnera.atm.ready"));
            lines.add(Component.translatable("hud.mnera.atm.power", eu, TriModePowerMath.ATM_BASE_CAPACITY_EU));
        } else if (be instanceof CooperationHubBlockEntity hub && level instanceof ServerLevel sl) {
            UUID vid = hub.resolveVillageId();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(sl);
            Optional<VillageRecord> vOpt = data.getVillage(vid);
            if (vOpt.isPresent()) {
                VillageRecord v = vOpt.get();
                long sellCount = v.getActiveOrders().stream()
                        .filter(o -> o.side() == OrderSide.VILLAGE_SELLS && !o.isCompleted()).count();
                long buyCount = v.getActiveOrders().stream()
                        .filter(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted()).count();
                MacroEconomyEngine econ = data.economy();
                String regimeKey = switch (econ.getInflationState()) {
                    case DEFLATION -> "gui.mnera.economy.deflation";
                    case STABLE -> "gui.mnera.economy.stable";
                    default -> "gui.mnera.economy.inflation";
                };
                lines.add(Component.translatable("hud.mnera.hub.village", v.getVillageName()));
                lines.add(Component.translatable("hud.mnera.hub.level_pop", v.getLevel(), v.getPopulation()));
                lines.add(Component.translatable("hud.mnera.hub.funds", v.getVillageBalance()));
                lines.add(Component.translatable("hud.mnera.hub.orders", sellCount, buyCount));
                lines.add(Component.translatable(
                        "hud.mnera.hub.cpi",
                        String.format(Locale.ROOT, "%.2f", econ.computeCpi()),
                        Component.translatable(regimeKey)
                ));

                // Employment & low-unemployment bonus HUD lines
                TyC2Config cfg = TyC2RuntimeData.config();
                VillageEmploymentStats emp = v.getEmploymentStats();
                int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
                lines.add(Component.translatable(
                        "hud.mnera.hub.unemployment", unempPct, emp.unemployedCount(), emp.workforceCount()
                ));
                if (emp.isLowUnemploymentBonusActive(cfg)) {
                    int qtyPct = (int) Math.round((emp.tradeQuantityMultiplier(cfg) - 1.0) * 100.0);
                    lines.add(Component.translatable("hud.mnera.hub.employment_bonus", qtyPct));
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
                    Component wpName = TyC2VillagerHelpers.workplaceDisplayComponent(
                            headAid.professionId(), headAid.archetype(), headAid.displayNameFallback(), headAid.size()
                    );
                    Component statusComp = TyC2VillagerHelpers.aidStatusComponent(headAid.status(), progPct);
                    lines.add(Component.translatable("hud.mnera.hub.aid_active", wpName, statusComp));
                }
            }
        }
        return lines;
    }

    /** Builds localized HUD lines for a TyC2 Village Mayor entity. */
    public static List<Component> buildEntityHudLines(Level level, Entity entity) {
        List<Component> lines = new ArrayList<>();
        if (!(entity instanceof Villager villager) || !(level instanceof ServerLevel sl)) {
            return lines;
        }
        CompoundTag pdata = villager.getPersistentData();
        if (!pdata.getBoolean(TycoonForgeWorldManager.TAG_MAYOR)
                || !pdata.contains(TycoonForgeWorldManager.TAG_VILLAGE_ID)) {
            return lines;
        }
        try {
            UUID vid = UUID.fromString(pdata.getString(TycoonForgeWorldManager.TAG_VILLAGE_ID));
            TycoonForgeSavedData.get(sl).getVillage(vid).ifPresent(v -> {
                if (v.isPartnered()) {
                    lines.add(Component.translatable("hud.mnera.mayor.partnered", v.getLevel()));
                    lines.add(Component.translatable("hud.mnera.hub.funds", v.getVillageBalance()));
                } else {
                    lines.add(Component.translatable("hud.mnera.mayor.unpartnered"));
                }
                VillageEmploymentStats emp = v.getEmploymentStats();
                int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
                lines.add(Component.translatable(
                        "hud.mnera.mayor.unemployment", unempPct, emp.unemployedCount(), emp.workforceCount()
                ));
            });
        } catch (IllegalArgumentException ignored) {
            // Ignore invalid UUID
        }
        return lines;
    }
}
