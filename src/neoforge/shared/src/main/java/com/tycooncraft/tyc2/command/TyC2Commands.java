package com.tycooncraft.tyc2.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tycooncraft.tyc2.compat.Ic2SoftBridge;
import com.tycooncraft.tyc2.compat.SeasonSoftBridge;
import com.tycooncraft.tyc2.core.BankAccountManager;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.MacroEconomyEngine;
import com.tycooncraft.tyc2.core.Season;
import com.tycooncraft.tyc2.core.TyC2Config;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.village.ExpansionPlanner;
import com.tycooncraft.tyc2.village.ProjectType;
import com.tycooncraft.tyc2.village.VillageEmploymentStats;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.village.WorkplaceSize;
import com.tycooncraft.tyc2.village.WorkplaceSpec;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import com.tycooncraft.tyc2.world.TycoonSavedData;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Administrative &amp; diagnostic {@code /tyc2} command suite for inspecting CPI, season, villages,
 * reloading configs, funding accounts, refreshing orders, and testing collision-safe expansion.
 */
public final class TyC2Commands {

    private TyC2Commands() {
    }

    /** Subscribes command registration on the NeoForge game bus. */
    public static void install(IEventBus gameBus) {
        gameBus.addListener(RegisterCommandsEvent.class, event -> register(event.getDispatcher()));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mnera")
                .then(Commands.literal("status").executes(ctx -> cmdStatus(ctx.getSource())))
                .then(Commands.literal("season").executes(ctx -> cmdSeason(ctx.getSource())))
                .then(Commands.literal("cpi").executes(ctx -> cmdCpi(ctx.getSource())))
                .then(Commands.literal("bank")
                        .requires(TyC2Native::hasOpPermission)
                        .then(Commands.literal("give")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L, 1_000_000_000L))
                                        .executes(ctx -> cmdBankGive(
                                                ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "amount")
                                        )))))
                .then(Commands.literal("mint")
                        .requires(TyC2Native::hasOpPermission)
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L, 1_000_000L))
                                .executes(ctx -> cmdMint(
                                        ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "amount")
                                ))))
                .then(Commands.literal("expand")
                        .requires(TyC2Native::hasOpPermission)
                        .executes(ctx -> cmdExpand(ctx.getSource(), null))
                        .then(Commands.argument("projectType", StringArgumentType.word())
                                .executes(ctx -> cmdExpand(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "projectType")
                                ))))
                .then(Commands.literal("aid")
                        .requires(TyC2Native::hasOpPermission)
                        .then(Commands.argument("professionAndSize", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    for (WorkplaceSpec spec : TyC2DataLoader.workplaceCatalog()
                                            .getAvailableWorkplaces(TyC2Native::blockExists)) {
                                        builder.suggest(spec.professionId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> cmdAid(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "professionAndSize")
                                ))))
                .then(Commands.literal("levelup")
                        .requires(TyC2Native::hasOpPermission)
                        .executes(ctx -> cmdLevelUp(ctx.getSource())))
                .then(Commands.literal("refresh")
                        .requires(TyC2Native::hasOpPermission)
                        .executes(ctx -> cmdRefresh(ctx.getSource())))
                .then(Commands.literal("restore_hub")
                        .requires(TyC2Native::hasOpPermission)
                        .executes(ctx -> cmdRestoreHub(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(TyC2Native::hasOpPermission)
                        .executes(ctx -> cmdReload(ctx.getSource())))
        );
    }

    private static int cmdStatus(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        TycoonSavedData data = TyC2WorldManager.getData(level);
        MacroEconomyEngine econ = data.economy();
        Season season = SeasonSoftBridge.currentSeason(level);
        long partnered = data.allVillages().stream().filter(VillageRecord::isPartnered).count();

        src.sendSuccess(() -> Component.literal(String.format(
                "[MNERA] CPI: %.2f (%s) | M: %d UC | Q: %.0f | Season: %s | Villages: %d (%d partnered)",
                econ.computeCpi(),
                econ.getInflationState().name(),
                econ.totalMoneySupply(),
                econ.rollingGoodsCirculation(),
                season.name(),
                data.allVillages().size(),
                partnered
        )).withStyle(ChatFormatting.GOLD), false);

        BlockPos pos = BlockPos.containing(src.getPosition());
        TyC2WorldManager.findNearestVillage(level, pos, 96).ifPresent(v -> {
            VillageEmploymentStats es = v.getEmploymentStats();
            TyC2Config cfg = TyC2DataLoader.config();
            int unempPct = (int) Math.round(es.unemploymentRate() * 100.0);
            String bonusTag = es.isLowUnemploymentBonusActive(cfg)
                    ? String.format(" [Bonus +%d%% qty]",
                            Math.round((es.tradeQuantityMultiplier(cfg) - 1.0) * 100.0))
                    : "";
            src.sendSuccess(() -> Component.literal(String.format(
                    "  Nearest: %s (Lv.%d, Pop=%d, Unemp=%d%% [%d/%d]%s, Aid=%d/%d, Funds=%d UC, Orders=%d, Partnered=%s)",
                    v.getVillageName(),
                    v.getLevel(),
                    v.getPopulation(),
                    unempPct,
                    es.unemployedCount(),
                    es.workforceCount(),
                    bonusTag,
                    v.getAidProjects().size(),
                    cfg.maxQueuedAidProjects(),
                    v.getVillageBalance(),
                    v.getActiveOrders().size(),
                    v.isPartnered()
            )).withStyle(ChatFormatting.AQUA), false);
        });
        return 1;
    }

    private static int cmdSeason(CommandSourceStack src) {
        Season season = SeasonSoftBridge.currentSeason(src.getLevel());
        src.sendSuccess(() -> Component.literal("[MNERA] Active Season: " + season.name())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int cmdCpi(CommandSourceStack src) {
        TycoonSavedData data = TyC2WorldManager.getData(src.getLevel());
        MacroEconomyEngine econ = data.economy();
        src.sendSuccess(() -> Component.literal(String.format(
                "[MNERA] CPI=%.3f | State=%s | MoneySupply=%d UC | Circulation=%.1f | Samples=%d",
                econ.computeCpi(),
                econ.getInflationState().name(),
                econ.totalMoneySupply(),
                econ.rollingGoodsCirculation(),
                econ.history().size()
        )).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int cmdBankGive(CommandSourceStack src, long amount) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("Player context required."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        TycoonSavedData data = TyC2WorldManager.getData(level);
        BankAccountManager bank = data.bank();
        bank.getOrCreatePersonal(player.getUUID(), player.getName().getString());
        BankAccountManager.AccountRef ref = bank.resolveSettlementAccount(player.getUUID());
        bank.creditAccount(ref, amount, BankAccountManager.TransactionType.DEPOSIT, level.getGameTime(), "OP Grant", "/mnera bank give");
        data.setDirty();
        src.sendSuccess(() -> Component.literal("[MNERA] Credited " + amount + " UC to active bank account.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cmdMint(CommandSourceStack src, long amount) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("Player context required."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        TycoonSavedData data = TyC2WorldManager.getData(level);
        boolean ic2Loaded = Ic2SoftBridge.isIc2Loaded();
        for (Map.Entry<CurrencyDenomination, Integer> e : CurrencyDenomination.decompose(amount).entrySet()) {
            int rem = e.getValue();
            String itemId = e.getKey().canonicalItemId(ic2Loaded);
            while (rem > 0) {
                int batch = Math.min(64, rem);
                ItemStack stack = TyC2Native.stack(itemId, batch);
                rem -= batch;
                if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        data.economy().recordMinting(amount);
        data.setDirty();
        src.sendSuccess(() -> Component.literal("[MNERA] Minted " + amount + " UC into inventory.")
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int cmdExpand(CommandSourceStack src, String projectTypeArg) {
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        Optional<VillageRecord> nearest = TyC2WorldManager.findNearestVillage(level, pos, 96);
        if (nearest.isEmpty()) {
            src.sendFailure(Component.literal("No Monetera village within 96 blocks."));
            return 0;
        }
        ProjectType explicit = null;
        if (projectTypeArg != null && !projectTypeArg.isBlank()) {
            try {
                explicit = ProjectType.valueOf(projectTypeArg.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                src.sendFailure(Component.literal("Unknown project type: " + projectTypeArg));
                return 0;
            }
        }
        ExpansionPlanner.PlanResult res = TyC2WorldManager.forcePlanAndBuild(level, nearest.get(), explicit);
        if (!res.success()) {
            src.sendFailure(Component.literal("Expansion planning failed: " + res.failureReason()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "[MNERA] Launched collision-safe expansion for " + nearest.get().getVillageName()
                        + " (" + res.job().getPlacements().size() + " blocks)."
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cmdLevelUp(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        Optional<VillageRecord> nearest = TyC2WorldManager.findNearestVillage(level, pos, 96);
        if (nearest.isEmpty()) {
            src.sendFailure(Component.literal("No Monetera village within 96 blocks."));
            return 0;
        }
        VillageRecord v = nearest.get();
        if (v.getLevel() >= 10) {
            src.sendFailure(Component.literal(v.getVillageName() + " is already Lv.10."));
            return 0;
        }
        TycoonSavedData data = TyC2WorldManager.getData(level);
        v.setLevel(v.getLevel() + 1);
        TyC2WorldManager.refreshVillageOrders(level, data, v, TyC2Native.dayTime(level));
        data.setDirty();
        src.sendSuccess(() -> Component.literal(
                "[MNERA] Elevated " + v.getVillageName() + " to Lv." + v.getLevel()
        ).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int cmdRefresh(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        Optional<VillageRecord> nearest = TyC2WorldManager.findNearestVillage(level, pos, 96);
        if (nearest.isEmpty()) {
            src.sendFailure(Component.literal("No Monetera village within 96 blocks."));
            return 0;
        }
        TycoonSavedData data = TyC2WorldManager.getData(level);
        TyC2WorldManager.refreshVillageOrders(level, data, nearest.get(), TyC2Native.dayTime(level));
        src.sendSuccess(() -> Component.literal(
                "[MNERA] Refreshed trade orders for " + nearest.get().getVillageName()
                        + " (" + nearest.get().getActiveOrders().size() + " active orders)."
        ).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int cmdRestoreHub(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        Optional<VillageRecord> nearest = TyC2WorldManager.findNearestVillage(level, pos, 128);
        if (nearest.isEmpty()) {
            src.sendFailure(Component.literal("No Monetera village within 128 blocks."));
            return 0;
        }
        VillageRecord v = nearest.get();
        if (!v.isPartnered()) {
            src.sendFailure(Component.literal(v.getVillageName() + " is not currently partnered."));
            return 0;
        }
        BlockPos hubPos = TyC2WorldManager.restoreHubBlock(level, v);
        src.sendSuccess(() -> Component.translatable(
                "status.mnera.hub.restored",
                v.getVillageName(),
                hubPos.getX() + "," + hubPos.getY() + "," + hubPos.getZ()
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cmdAid(CommandSourceStack src, String rawArgs) {
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        Optional<VillageRecord> nearest = TyC2WorldManager.findNearestVillage(level, pos, 96);
        if (nearest.isEmpty()) {
            src.sendFailure(Component.literal("No Monetera village within 96 blocks."));
            return 0;
        }
        String trimmed = rawArgs == null ? "" : rawArgs.trim();
        if (trimmed.isEmpty()) {
            src.sendFailure(Component.literal("Usage: /mnera aid <profession> [small|large]"));
            return 0;
        }
        String[] parts = trimmed.split("\\s+");
        String profArg = parts[0].replace("\"", "").replace("'", "");
        WorkplaceSize size = parts.length >= 2
                ? WorkplaceSize.fromNameOrDefault(parts[1], WorkplaceSize.SMALL)
                : WorkplaceSize.SMALL;
        ServerPlayer player = src.getPlayer();
        UUID actorUuid = player == null ? null : player.getUUID();
        Optional<Component> err = TyC2WorldManager.forceStartAid(level, nearest.get(), profArg, size, actorUuid);
        if (err.isPresent()) {
            src.sendFailure(err.get());
            return 0;
        }
        VillageRecord v = nearest.get();
        src.sendSuccess(() -> Component.literal(
                "[MNERA] Queued " + size.id() + " aid construction (" + profArg + ") for "
                        + v.getVillageName() + " (queue=" + v.getAidProjects().size() + ")."
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cmdReload(CommandSourceStack src) {
        TyC2DataLoader.reloadExternalConfigsOnly();
        src.sendSuccess(() -> Component.literal(
                "[MNERA] Reloaded config/mnera/*.json ("
                        + TyC2DataLoader.mintRecipes().allRecipes().size() + " mint recipes, "
                        + TyC2DataLoader.goodsCatalog().allGoods().size() + " catalog goods, "
                        + TyC2DataLoader.workplaceCatalog().getAvailableWorkplaces(TyC2Native::blockExists).size()
                        + " workplaces)."
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
