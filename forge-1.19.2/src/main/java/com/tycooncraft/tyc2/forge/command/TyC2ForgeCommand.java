package com.tycooncraft.tyc2.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.compat.Lc2SoftBridge;
import com.tycooncraft.tyc2.forge.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.village.WorkplaceSpec;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Administrative & diagnostic {@code /tyc2} command suite for players, modpack authors, and QA.
 */
public final class TyC2ForgeCommand {

    private TyC2ForgeCommand() {
    }

    /** Registers the {@code /tyc2} command tree. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mnera")
                .then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource())))
                .then(Commands.literal("season").executes(ctx -> showSeason(ctx.getSource())))
                .then(Commands.literal("cpi").executes(ctx -> showCpi(ctx.getSource())))
                .then(Commands.literal("villages").executes(ctx -> listVillages(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> reloadExternal(ctx.getSource())))
                .then(Commands.literal("mint")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000L))
                                .executes(ctx -> mintCoins(ctx.getSource(), LongArgumentType.getLong(ctx, "amount")))))
                .then(Commands.literal("bank")
                        .then(Commands.literal("balance").executes(ctx -> showBalance(ctx.getSource())))
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("amount", LongArgumentType.longArg(1, 10_000_000L))
                                        .executes(ctx -> addBank(ctx.getSource(), LongArgumentType.getLong(ctx, "amount"))))))
                .then(Commands.literal("levelup")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> forceLevelUp(ctx.getSource())))
                .then(Commands.literal("refresh")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> forceRefresh(ctx.getSource())))
                .then(Commands.literal("restore_hub")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> restoreHub(ctx.getSource())))
                .then(Commands.literal("expand")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> forceExpand(ctx.getSource(), null))
                        .then(Commands.argument("project", StringArgumentType.word())
                                .executes(ctx -> forceExpand(ctx.getSource(), StringArgumentType.getString(ctx, "project")))))
                .then(Commands.literal("aid")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("profession", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        TyC2RuntimeData.workplaceCatalog()
                                                .getAvailableWorkplaces(TyC2Ids::blockExists)
                                                .stream()
                                                .map(WorkplaceSpec::professionId),
                                        builder
                                ))
                                .executes(ctx -> forceAid(
                                        ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "profession"),
                                        "small"
                                ))
                                .then(Commands.argument("size", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                new String[]{"small", "large"}, builder
                                        ))
                                        .executes(ctx -> forceAid(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "profession"),
                                                StringArgumentType.getString(ctx, "size")
                                        )))))
        );
    }

    private static int showStatus(CommandSourceStack src) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
        data.syncMacroAccountTotals();
        MacroEconomyEngine econ = data.economy();
        TyC2Config cfg = TyC2RuntimeData.config();
        Season season = SeasonSoftBridge.currentSeason(src.getLevel());
        int partnered = 0;
        int totalAidQueued = 0;
        int bonusVillages = 0;
        for (VillageRecord v : data.allVillages()) {
            if (v.isPartnered()) {
                partnered++;
                totalAidQueued += v.getAidProjects().size();
                if (v.getEmploymentStats().isLowUnemploymentBonusActive(cfg)) {
                    bonusVillages++;
                }
            }
        }
        int workplaces = TyC2RuntimeData.workplaceCatalog().getAvailableWorkplaces(TyC2Ids::blockExists).size();
        src.sendSuccess(Component.literal(String.format(
                Locale.ROOT,
                "[MNERA] MHERA=%s TRERA=%s Season=%s | CPI=%.2f (%s) M=%d Q=%.0f | Villages=%d (%d partnered, %d bonus, %d aid) | Recipes=%d Goods=%d Workplaces=%d",
                Ic2Bridge.isLoaded(),
                Lc2SoftBridge.isLoaded(),
                season.name(),
                econ.computeCpi(),
                econ.getInflationState().name(),
                econ.totalMoneySupply(),
                econ.rollingGoodsCirculation(),
                data.allVillages().size(),
                partnered,
                bonusVillages,
                totalAidQueued,
                TyC2RuntimeData.mintRecipes().allRecipes().size(),
                TyC2RuntimeData.goodsCatalog().allGoods().size(),
                workplaces
        )), false);

        if (src.getEntity() instanceof ServerPlayer player) {
            Optional<VillageRecord> nearest = data.findNearestVillage(
                    player.getLevel().dimension().location().toString(), player.blockPosition(), 128
            );
            if (nearest.isPresent()) {
                VillageRecord v = nearest.get();
                VillageEmploymentStats emp = v.getEmploymentStats();
                int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
                boolean bonus = emp.isLowUnemploymentBonusActive(cfg);
                src.sendSuccess(Component.literal(String.format(
                        Locale.ROOT,
                        "[MNERA] Nearest Village %s (Lv.%d): Unemp=%d%% (%d/%d) Bonus=%s (Qty %.2fx, Gather %.2fx) AidQueue=%d/%d",
                        v.getVillageName(),
                        v.getLevel(),
                        unempPct,
                        emp.unemployedCount(),
                        emp.workforceCount(),
                        bonus ? "ACTIVE" : "OFF",
                        emp.tradeQuantityMultiplier(cfg),
                        emp.gatherSpeedMultiplier(cfg),
                        v.getAidProjects().size(),
                        cfg.maxQueuedAidProjects()
                )), false);
            }
        }
        return 1;
    }

    private static int showSeason(CommandSourceStack src) {
        Season s = SeasonSoftBridge.currentSeason(src.getLevel());
        src.sendSuccess(Component.translatable("gui.mnera.economy.season",
                Component.translatable("season.mnera." + s.key())), false);
        return 1;
    }

    private static int showCpi(CommandSourceStack src) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
        data.syncMacroAccountTotals();
        MacroEconomyEngine econ = data.economy();
        src.sendSuccess(Component.literal(String.format(
                Locale.ROOT,
                "[MNERA] CPI=%.3f (%s) | MoneySupply=%d UC (MintedNet=%d, Banks=%d, Villages=%d) | Circulation Q=%.1f | History=%d pts",
                econ.computeCpi(),
                econ.getInflationState().name(),
                econ.totalMoneySupply(),
                econ.totalMintedNet(),
                econ.playerAndGuildBankTotal(),
                econ.villageBankTotal(),
                econ.rollingGoodsCirculation(),
                econ.history().size()
        )), false);
        return 1;
    }

    private static int listVillages(CommandSourceStack src) {
        TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
        if (data.allVillages().isEmpty()) {
            src.sendSuccess(Component.literal("[MNERA] No tracked villages yet."), false);
            return 1;
        }
        TyC2Config cfg = TyC2RuntimeData.config();
        for (VillageRecord v : data.allVillages()) {
            VillageEmploymentStats emp = v.getEmploymentStats();
            int unempPct = emp.workforceCount() <= 0 ? 0 : (int) Math.round(emp.unemploymentRate() * 100.0);
            src.sendSuccess(Component.literal(String.format(
                    Locale.ROOT,
                    "- %s [%s] Lv.%d Pop=%d Unemp=%d%%(%d/%d)%s Funds=%d UC Partnered=%s Hub=(%d,%d,%d) Orders=%d Jobs=%d Aid=%d",
                    v.getVillageName(),
                    v.getBiomeStyle().id(),
                    v.getLevel(),
                    v.getPopulation(),
                    unempPct,
                    emp.unemployedCount(),
                    emp.workforceCount(),
                    emp.isLowUnemploymentBonusActive(cfg) ? "[BONUS]" : "",
                    v.getVillageBalance(),
                    v.isPartnered(),
                    v.getHubPos().x(), v.getHubPos().y(), v.getHubPos().z(),
                    v.getActiveOrders().size(),
                    v.getConstructionQueue().size(),
                    v.getAidProjects().size()
            )), false);
        }
        return data.allVillages().size();
    }

    private static int reloadExternal(CommandSourceStack src) {
        TyC2RuntimeData.reloadExternalConfigOnly();
        src.sendSuccess(Component.literal("[MNERA] Reloaded config/mnera/*.json overrides."), true);
        return 1;
    }

    private static int mintCoins(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            TyC2Ids.giveCoinsToPlayer(player, amount, Ic2Bridge.isLoaded());
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            data.economy().recordMinting(amount);
            data.setDirty();
            src.sendSuccess(Component.literal("[MNERA] Minted " + amount + " UC (CPI now "
                    + String.format(Locale.ROOT, "%.2f", data.economy().computeCpi()) + ")"), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player required for /mnera mint"));
            return 0;
        }
    }

    private static int showBalance(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            long bal = data.bank().getActiveBalance(player.getUUID());
            src.sendSuccess(Component.translatable("gui.mnera.atm.balance", bal), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player required"));
            return 0;
        }
    }

    private static int addBank(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            data.bank().getOrCreatePersonal(player.getUUID(), player.getGameProfile().getName());
            BankAccountManager.AccountRef ref = data.bank().resolveSettlementAccount(player.getUUID());
            data.bank().creditAccount(ref, amount, BankAccountManager.TransactionType.SYSTEM_ADJUST,
                    src.getServer().overworld().getGameTime(), "Admin", "/mnera bank add");
            data.syncMacroAccountTotals();
            data.setDirty();
            src.sendSuccess(Component.literal("[MNERA] Credited " + amount + " UC. New balance: "
                    + data.bank().getBalance(ref) + " UC"), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player required"));
            return 0;
        }
    }

    private static int forceLevelUp(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.getLevel();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            Optional<VillageRecord> vOpt = data.findNearestVillage(
                    level.dimension().location().toString(), player.blockPosition(), 128
            );
            if (vOpt.isEmpty()) {
                src.sendFailure(Component.literal("[MNERA] No tracked village within 128 blocks."));
                return 0;
            }
            VillageRecord v = vOpt.get();
            int next = Math.min(10, v.getLevel() + 1);
            v.setLevel(next);
            TycoonForgeWorldManager.refreshVillageOrders(level, data, v, src.getServer().overworld().getDayTime());
            data.setDirty();
            src.sendSuccess(Component.literal("[MNERA] Upgraded " + v.getVillageName() + " to Lv." + next), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player required"));
            return 0;
        }
    }

    private static int forceRefresh(CommandSourceStack src) {
        try {
            ServerLevel level = src.getLevel();
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(src.getPosition());
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            Optional<VillageRecord> vOpt = data.findNearestVillage(
                    level.dimension().location().toString(), pos, 96
            );
            if (vOpt.isEmpty()) {
                src.sendFailure(Component.literal("[MNERA] No Monetera village within 96 blocks."));
                return 0;
            }
            VillageRecord v = vOpt.get();
            TycoonForgeWorldManager.refreshVillageOrders(level, data, v, src.getServer().overworld().getDayTime());
            src.sendSuccess(Component.literal(
                    "[MNERA] Refreshed trade orders for " + v.getVillageName()
                            + " (" + v.getActiveOrders().size() + " active orders)."
            ), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[MNERA] Refresh failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int restoreHub(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.getLevel();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            Optional<VillageRecord> vOpt = data.findNearestVillage(
                    level.dimension().location().toString(), player.blockPosition(), 128
            );
            if (vOpt.isEmpty()) {
                src.sendFailure(Component.literal("[MNERA] No tracked village within 128 blocks."));
                return 0;
            }
            VillageRecord v = vOpt.get();
            if (!v.isPartnered()) {
                src.sendFailure(Component.literal("[MNERA] " + v.getVillageName() + " is not currently partnered."));
                return 0;
            }
            net.minecraft.core.BlockPos hubPos = TycoonForgeWorldManager.restoreHubBlock(level, v);
            src.sendSuccess(Component.translatable(
                    "status.mnera.hub.restored",
                    v.getVillageName(),
                    hubPos.getX() + "," + hubPos.getY() + "," + hubPos.getZ()
            ), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player required"));
            return 0;
        }
    }

    private static int forceExpand(CommandSourceStack src, String projectArg) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.getLevel();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            Optional<VillageRecord> vOpt = data.findNearestVillage(
                    level.dimension().location().toString(), player.blockPosition(), 128
            );
            if (vOpt.isEmpty()) {
                src.sendFailure(Component.literal("[MNERA] No tracked village within 128 blocks."));
                return 0;
            }
            ProjectType type = null;
            if (projectArg != null && !projectArg.isBlank()) {
                type = ProjectType.valueOf(projectArg.trim().toUpperCase(Locale.ROOT));
            }
            var res = TycoonForgeWorldManager.forcePlanExpansion(level, data, vOpt.get(), type);
            if (res.success()) {
                src.sendSuccess(res.toComponent(), true);
                return 1;
            }
            src.sendFailure(res.toComponent());
            return 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[MNERA] Expand failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int forceAid(CommandSourceStack src, ResourceLocation professionId, String sizeArg) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.getLevel();
            TycoonForgeSavedData data = TycoonForgeSavedData.get(src.getServer());
            Optional<VillageRecord> vOpt = data.findNearestVillage(
                    level.dimension().location().toString(), player.blockPosition(), 128
            );
            if (vOpt.isEmpty()) {
                src.sendFailure(Component.literal("[MNERA] No tracked village within 128 blocks."));
                return 0;
            }
            WorkplaceSize size = WorkplaceSize.fromNameOrDefault(sizeArg, WorkplaceSize.SMALL);
            var res = TycoonForgeWorldManager.forcePlanAid(
                    level, data, vOpt.get(), professionId.toString(), size
            );
            if (res.success()) {
                src.sendSuccess(res.toComponent(), true);
                return 1;
            }
            src.sendFailure(res.toComponent());
            return 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[MNERA] Aid command failed: " + e.getMessage()));
            return 0;
        }
    }
}
