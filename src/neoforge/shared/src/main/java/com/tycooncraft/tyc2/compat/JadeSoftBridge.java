package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.blockentity.MintMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.AidConstructionProject;
import com.tycooncraft.tyc2.village.OrderSide;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import com.tycooncraft.tyc2.world.TycoonSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Shared HUD tooltip builder for Jade ({@code jade}) and The One Probe ({@code theoneprobe})
 * with zero hard compile-time dependency on either mod.
 */
public final class JadeSoftBridge {

    private JadeSoftBridge() {
    }

    /** Returns {@code true} if Jade is installed at runtime. */
    public static boolean isJadeLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("jade");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Builds localized HUD lines for any TyC2 machine or Cooperation Hub at {@code pos}. */
    public static List<Component> buildBlockHudLines(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return List.of();
        }
        BlockEntity be = level.getBlockEntity(pos);
        List<Component> lines = new ArrayList<>();
        if (be instanceof CooperationHubBlockEntity hub) {
            VillageRecord v = hub.resolveVillage(level);
            if (v != null) {
                TycoonSavedData data = TyC2WorldManager.getData(level);
                long sells = v.getActiveOrders().stream()
                        .filter(o -> o.side() == OrderSide.VILLAGE_SELLS && !o.isCompleted()).count();
                long buys = v.getActiveOrders().stream()
                        .filter(o -> o.side() == OrderSide.VILLAGE_BUYS && !o.isCompleted()).count();
                double cpi = data.economy().computeCpi();
                String stateKey = "gui.tyc2.economy." + data.economy().getInflationState().name().toLowerCase(Locale.ROOT);
                lines.add(Component.translatable("hud.tyc2.hub.village", v.getVillageName()));
                lines.add(Component.translatable("hud.tyc2.hub.level_pop", v.getLevel(), v.getPopulation()));
                lines.add(Component.translatable("hud.tyc2.hub.funds", v.getVillageBalance()));
                lines.add(Component.translatable("hud.tyc2.hub.orders", sells, buys));
                lines.add(Component.translatable(
                        "hud.tyc2.hub.cpi",
                        String.format("%.2f", cpi),
                        Component.translatable(stateKey)
                ));
                VillageEmploymentStats es = v.getEmploymentStats();
                TyC2Config cfg = TyC2DataLoader.config();
                int unempPct = (int) Math.round(es.unemploymentRate() * 100.0);
                lines.add(Component.translatable(
                        "hud.tyc2.hub.unemployment",
                        unempPct,
                        es.unemployedCount(),
                        es.workforceCount()
                ));
                if (es.isLowUnemploymentBonusActive(cfg)) {
                    int restockPct = (int) Math.round((es.restockSpeedMultiplier(cfg) - 1.0) * 100.0);
                    int qtyPct = (int) Math.round((es.tradeQuantityMultiplier(cfg) - 1.0) * 100.0);
                    lines.add(Component.translatable("hud.tyc2.hub.employment_bonus", restockPct, qtyPct));
                }
                if (!v.getAidProjects().isEmpty()) {
                    AidConstructionProject head = v.getAidProjects().get(0);
                    lines.add(Component.translatable(
                            "hud.tyc2.hub.aid_active",
                            TyC2WorldManager.workplaceDisplayComponent(head),
                            TyC2WorldManager.aidStatusComponent(v, head)
                    ));
                }
            }
        } else if (be instanceof PrimitiveMintMachineBlockEntity prim) {
            int burnSec = (prim.burnTimeRemaining() + 19) / 20;
            int pct = prim.maxProgress() > 0
                    ? Math.min(100, (int) Math.round(100.0 * prim.progress() / prim.maxProgress()))
                    : 0;
            lines.add(Component.translatable("hud.tyc2.primitive_mint.scope"));
            lines.add(Component.translatable("hud.tyc2.primitive_mint.fuel", burnSec));
            lines.add(Component.translatable("hud.tyc2.primitive_mint.progress", pct));
            if (prim.isBlockedByIndustrialTier()) {
                lines.add(Component.translatable("gui.tyc2.primitive_mint.requires_industrial"));
            }
        } else if (be instanceof MintMachineBlockEntity mint) {
            int eu = (int) Math.floor(mint.storedEu());
            int maxEu = (int) mint.maxCapacityEu();
            int pct = mint.maxProgress() > 0
                    ? Math.min(100, (int) Math.round(100.0 * mint.progress() / mint.maxProgress()))
                    : 0;
            lines.add(Component.translatable("hud.tyc2.mint.power", eu, maxEu, TriModePowerMath.euToFe(eu)));
            lines.add(Component.translatable("hud.tyc2.mint.progress", pct));
        } else if (be instanceof AtmMachineBlockEntity atm) {
            int eu = (int) Math.floor(atm.storedEu());
            int maxEu = (int) atm.maxCapacityEu();
            lines.add(Component.translatable("hud.tyc2.atm.ready"));
            lines.add(Component.translatable("hud.tyc2.atm.power", eu, maxEu));
        }
        return lines;
    }

    /** Builds localized HUD lines for Village Mayor entities. */
    public static List<Component> buildEntityHudLines(ServerLevel level, Entity entity) {
        if (level == null || entity == null) {
            return List.of();
        }
        if (!TyC2Nbt.getBoolean(entity.getPersistentData(), TyC2Constants.TAG_MAYOR, false)) {
            return List.of();
        }
        String vidStr = TyC2Nbt.getString(entity.getPersistentData(), TyC2Constants.TAG_MAYOR_VILLAGE_ID, "");
        if (vidStr.isBlank()) {
            return List.of(Component.translatable("hud.tyc2.mayor.unpartnered"));
        }
        try {
            UUID vid = UUID.fromString(vidStr);
            VillageRecord v = TyC2WorldManager.getData(level).getVillage(vid).orElse(null);
            if (v != null) {
                List<Component> lines = new ArrayList<>(2);
                if (v.isPartnered()) {
                    lines.add(Component.translatable("hud.tyc2.mayor.partnered", v.getLevel()));
                } else {
                    lines.add(Component.translatable("hud.tyc2.mayor.unpartnered"));
                }
                VillageEmploymentStats es = v.getEmploymentStats();
                int unempPct = (int) Math.round(es.unemploymentRate() * 100.0);
                lines.add(Component.translatable(
                        "hud.tyc2.mayor.unemployment",
                        unempPct,
                        es.unemployedCount(),
                        es.workforceCount()
                ));
                return lines;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through
        }
        return List.of(Component.translatable("hud.tyc2.mayor.unpartnered"));
    }
}
