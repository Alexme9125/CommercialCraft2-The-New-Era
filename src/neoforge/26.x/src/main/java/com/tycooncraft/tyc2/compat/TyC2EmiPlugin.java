package com.tycooncraft.tyc2.compat;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.MintIngredient;
import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.TradeGood;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * NeoForge 26.x (26.1.2 &amp; 26.2) EMI plugin exposing TyC2's Primitive Minting, Industrial Minting,
 * Village Trade Market catalog (including optional IC2/EcC2/cooking-mod goods), and
 * machine/terminal/currency {@link EmiInfoRecipe} pages with zero hard runtime dependency on EMI.
 */
@EmiEntrypoint
public final class TyC2EmiPlugin implements EmiPlugin {

    private static final int TEXT_COLOR = 0xFF3F3F3F;

    @Override
    public void register(EmiRegistry registry) {
        Predicate<String> modLoaded = modId -> ModList.get() != null && ModList.get().isLoaded(modId);

        EmiRecipeCategory primitiveCategory = new EmiRecipeCategory(
                id("primitive_minting"),
                EmiStack.of(TyC2Registries.PRIMITIVE_MINT_MACHINE_ITEM.get())
        );
        EmiRecipeCategory industrialCategory = new EmiRecipeCategory(
                id("minting"),
                EmiStack.of(TyC2Registries.MINT_MACHINE_ITEM.get())
        );
        EmiRecipeCategory tradeCategory = new EmiRecipeCategory(
                id("village_trade"),
                EmiStack.of(TyC2Registries.COOPERATION_HUB_ITEM.get())
        );

        registry.addCategory(primitiveCategory);
        registry.addWorkstation(primitiveCategory, EmiStack.of(TyC2Registries.PRIMITIVE_MINT_MACHINE_ITEM.get()));

        registry.addCategory(industrialCategory);
        registry.addWorkstation(industrialCategory, EmiStack.of(TyC2Registries.MINT_MACHINE_ITEM.get()));

        registry.addCategory(tradeCategory);
        registry.addWorkstation(tradeCategory, EmiStack.of(TyC2Registries.COOPERATION_HUB_ITEM.get()));
        registry.addWorkstation(tradeCategory, EmiStack.of(TyC2Registries.COMM_TERMINAL_ITEM.get()));

        for (MintRecipe recipe : JeiSoftBridge.activePrimitiveMintRecipes()) {
            addMintRecipe(registry, primitiveCategory, recipe, true);
        }
        for (MintRecipe recipe : JeiSoftBridge.activeMintRecipes()) {
            addMintRecipe(registry, industrialCategory, recipe, false);
        }

        List<TradeGood> liveGoods = TyC2DataLoader.goodsCatalog().getAvailableGoods(TyC2Native::itemExists, modLoaded);
        List<EmiIngredient> ecc2Stacks = new ArrayList<>();
        for (TradeGood good : liveGoods) {
            ItemStack itemStack = TyC2Native.stack(good.itemId(), 1);
            if (itemStack.isEmpty()) {
                continue;
            }
            EmiStack goodEmi = EmiStack.of(itemStack);
            if (TyC2Constants.MOD_ECC2.equals(good.requiredModId())) {
                ecc2Stacks.add(goodEmi);
            }
            EmiStack coinEmi = representativeCoinStack(good.basePrice());
            if (coinEmi.isEmpty()) {
                continue;
            }
            if (good.isSellableByVillage()) {
                registry.addRecipe(new TradeEmiRecipe(
                        tradeCategory,
                        id("/trade/sell/" + sanitize(good.id())),
                        good,
                        coinEmi,
                        goodEmi,
                        false
                ));
            }
            if (good.isBuyableByVillage()) {
                registry.addRecipe(new TradeEmiRecipe(
                        tradeCategory,
                        id("/trade/buy/" + sanitize(good.id())),
                        good,
                        goodEmi,
                        coinEmi,
                        true
                ));
            }
        }

        registerInfoPages(registry, ecc2Stacks);
    }

    private static void addMintRecipe(
            EmiRegistry registry,
            EmiRecipeCategory category,
            MintRecipe recipe,
            boolean primitive
    ) {
        List<EmiIngredient> resolvedInputs = new ArrayList<>(recipe.ingredients().size());
        for (MintIngredient ing : recipe.ingredients()) {
            EmiIngredient resolved = resolveMintIngredient(ing);
            if (resolved.isEmpty()) {
                return;
            }
            resolvedInputs.add(resolved);
        }
        EmiStack output = resolveMintOutput(recipe);
        if (output.isEmpty()) {
            return;
        }
        String prefix = primitive ? "/primitive_minting/" : "/minting/";
        registry.addRecipe(new MintEmiRecipe(
                category,
                id(prefix + sanitize(recipe.id())),
                recipe,
                resolvedInputs,
                output,
                primitive
        ));
    }

    private static void registerInfoPages(EmiRegistry registry, List<EmiIngredient> ecc2Stacks) {
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2Registries.PRIMITIVE_MINT_MACHINE_ITEM.get())),
                List.of(Component.translatable("emi.info.tyc2.primitive_mint_machine")),
                id("/info/primitive_mint_machine")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2Registries.MINT_MACHINE_ITEM.get())),
                List.of(Component.translatable("emi.info.tyc2.mint_machine")),
                id("/info/mint_machine")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2Registries.ATM_MACHINE_ITEM.get())),
                List.of(Component.translatable("emi.info.tyc2.atm_machine")),
                id("/info/atm_machine")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2Registries.COOPERATION_HUB_ITEM.get())),
                List.of(Component.translatable("emi.info.tyc2.cooperation_hub")),
                id("/info/cooperation_hub")
        ));
        ItemStack chargedTerminal = new ItemStack(TyC2Registries.COMM_TERMINAL_ITEM.get());
        CommTerminalItem.setEnergyEu(chargedTerminal, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        registry.addRecipe(new EmiInfoRecipe(
                List.of(
                        EmiStack.of(TyC2Registries.COMM_TERMINAL_ITEM.get()),
                        EmiStack.of(chargedTerminal)
                ),
                List.of(Component.translatable("emi.info.tyc2.comm_terminal")),
                id("/info/comm_terminal")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(
                        EmiStack.of(TyC2Registries.COIN_1.get()),
                        EmiStack.of(TyC2Registries.COIN_5.get()),
                        EmiStack.of(TyC2Registries.COIN_10.get()),
                        EmiStack.of(TyC2Registries.COIN_20.get()),
                        EmiStack.of(TyC2Registries.COIN_50.get()),
                        EmiStack.of(TyC2Registries.COIN_100.get()),
                        EmiStack.of(TyC2Registries.COIN_200.get()),
                        EmiStack.of(TyC2Registries.COIN_500.get()),
                        EmiStack.of(TyC2Registries.COIN_1000.get())
                ),
                List.of(Component.translatable("emi.info.tyc2.coins")),
                id("/info/coins")
        ));
        if (!ecc2Stacks.isEmpty()) {
            registry.addRecipe(new EmiInfoRecipe(
                    ecc2Stacks,
                    List.of(Component.translatable("emi.info.tyc2.ecc2_trade")),
                    id("/info/ecc2_trade")
            ));
        }
    }

    private static EmiIngredient resolveMintIngredient(MintIngredient ing) {
        if (!ing.isTag()) {
            ItemStack st = TyC2Native.stack(ing.spec(), ing.count());
            return st.isEmpty() ? EmiStack.EMPTY : EmiStack.of(st);
        }
        List<EmiIngredient> tagMatches = new ArrayList<>(2);
        for (String tagId : ing.expandedTagIds()) {
            Identifier tagLoc = Identifier.tryParse(tagId);
            if (tagLoc != null) {
                EmiIngredient candidate = EmiIngredient.of(TagKey.create(Registries.ITEM, tagLoc), ing.count());
                if (!candidate.isEmpty()) {
                    tagMatches.add(candidate);
                }
            }
        }
        if (tagMatches.size() == 1) {
            return tagMatches.get(0);
        }
        if (tagMatches.size() > 1) {
            return EmiIngredient.of(tagMatches, ing.count());
        }
        ItemStack fallback = TyC2Native.stack(ing.representativeItemId(), ing.count());
        return fallback.isEmpty() ? EmiStack.EMPTY : EmiStack.of(fallback);
    }

    private static EmiStack resolveMintOutput(MintRecipe recipe) {
        boolean ic2Loaded = Ic2SoftBridge.isIc2Loaded();
        ItemStack out = TyC2Native.stack(recipe.outputItemId(ic2Loaded), recipe.outputCount());
        if (out.isEmpty()) {
            out = TyC2Native.stack(recipe.outputDenomination().tyc2ItemId(), recipe.outputCount());
        }
        return out.isEmpty() ? EmiStack.EMPTY : EmiStack.of(out);
    }

    private static EmiStack representativeCoinStack(long basePrice) {
        CurrencyDenomination best = CurrencyDenomination.X1;
        for (CurrencyDenomination denom : CurrencyDenomination.values()) {
            if (denom.value() <= basePrice && denom.value() >= best.value()) {
                best = denom;
            }
        }
        ItemStack stack = TyC2Native.stack(best.tyc2ItemId(), 1);
        return stack.isEmpty() ? EmiStack.EMPTY : EmiStack.of(stack);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TyC2Constants.MOD_ID, path);
    }

    private static String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static final class MintEmiRecipe extends BasicEmiRecipe {

        private final MintRecipe recipe;
        private final boolean primitive;

        MintEmiRecipe(
                EmiRecipeCategory category,
                Identifier id,
                MintRecipe recipe,
                List<EmiIngredient> resolvedInputs,
                EmiStack output,
                boolean primitive
        ) {
            super(category, id, 154, 44);
            this.recipe = recipe;
            this.primitive = primitive;
            this.inputs.addAll(resolvedInputs);
            this.outputs.add(output);
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            int x = 4;
            for (EmiIngredient input : inputs) {
                widgets.addSlot(input, x, 4);
                x += 20;
            }
            int effectiveTicks = primitive
                    ? TriModePowerMath.primitiveMintDurationTicks(recipe.durationTicks())
                    : recipe.durationTicks();
            widgets.addFillingArrow(48, 5, Math.max(1000, effectiveTicks * 50));
            widgets.addSlot(outputs.get(0), 78, 4).recipeContext(this);

            Component powerLine = primitive
                    ? Component.translatable("jei.tyc2.primitive_minting.fuel_only")
                    : Component.translatable("jei.tyc2.minting.eu", recipe.energyEu());
            widgets.addText(powerLine, 4, 25, TEXT_COLOR, false);
            widgets.addText(
                    Component.translatable("jei.tyc2.minting.ticks", effectiveTicks, formatSeconds(effectiveTicks)),
                    4,
                    35,
                    TEXT_COLOR,
                    false
            );
        }
    }

    private static final class TradeEmiRecipe extends BasicEmiRecipe {

        private final TradeGood good;
        private final boolean villageBuys;

        TradeEmiRecipe(
                EmiRecipeCategory category,
                Identifier id,
                TradeGood good,
                EmiIngredient input,
                EmiStack output,
                boolean villageBuys
        ) {
            super(category, id, 154, 44);
            this.good = good;
            this.villageBuys = villageBuys;
            this.inputs.add(input);
            this.outputs.add(output);
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 4, 4);
            widgets.addFillingArrow(28, 5, 2500);
            widgets.addSlot(outputs.get(0), 58, 4).recipeContext(this);
            widgets.addText(
                    Component.translatable(villageBuys ? "emi.tyc2.trade.village_buys" : "emi.tyc2.trade.village_sells"),
                    82,
                    9,
                    TEXT_COLOR,
                    false
            );
            widgets.addText(
                    Component.translatable("emi.tyc2.trade.price_level", good.basePrice(), good.minLevel(), good.maxLevel()),
                    4,
                    25,
                    TEXT_COLOR,
                    false
            );
            Component catName = Component.translatable("category.tyc2." + good.category().name().toLowerCase(Locale.ROOT));
            widgets.addText(
                    Component.translatable("emi.tyc2.trade.tier_category", catName, good.industrialTierRequired().level()),
                    4,
                    35,
                    TEXT_COLOR,
                    false
            );
        }
    }
}
