package com.tycooncraft.tyc2.forge.compat;

import com.tycooncraft.tyc2.core.CommonTagAliases;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.MintIngredient;
import com.tycooncraft.tyc2.core.MintRecipe;
import com.tycooncraft.tyc2.core.TradeGood;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
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
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Optional soft integration with EMI ({@code emi}) on Forge 1.19.2 exposing TyC2's
 * Primitive Minting, Industrial Minting, Village Trade Market catalog (including optional
 * IC2/EcC2/cooking-mod goods), and machine/terminal/currency {@link EmiInfoRecipe} pages.
 * Loaded exclusively by EMI's {@link EmiEntrypoint} scanner when EMI is installed.
 */
@EmiEntrypoint
public final class TyC2EmiPlugin implements EmiPlugin {

    private static final int TEXT_COLOR = 0xFF3F3F3F;

    @Override
    public void register(EmiRegistry registry) {
        Predicate<String> modLoaded = modId -> ModList.get() != null && ModList.get().isLoaded(modId);

        EmiRecipeCategory primitiveCategory = new EmiRecipeCategory(
                TyC2Ids.id("primitive_minting"),
                EmiStack.of(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_ITEM.get())
        );
        EmiRecipeCategory industrialCategory = new EmiRecipeCategory(
                TyC2Ids.id("minting"),
                EmiStack.of(TyC2ForgeRegistries.MINT_MACHINE_ITEM.get())
        );
        EmiRecipeCategory tradeCategory = new EmiRecipeCategory(
                TyC2Ids.id("village_trade"),
                EmiStack.of(TyC2ForgeRegistries.COOPERATION_HUB_ITEM.get())
        );

        registry.addCategory(primitiveCategory);
        registry.addWorkstation(primitiveCategory, EmiStack.of(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_ITEM.get()));

        registry.addCategory(industrialCategory);
        registry.addWorkstation(industrialCategory, EmiStack.of(TyC2ForgeRegistries.MINT_MACHINE_ITEM.get()));

        registry.addCategory(tradeCategory);
        registry.addWorkstation(tradeCategory, EmiStack.of(TyC2ForgeRegistries.COOPERATION_HUB_ITEM.get()));
        registry.addWorkstation(tradeCategory, EmiStack.of(TyC2ForgeRegistries.COMM_TERMINAL.get()));

        for (MintRecipe recipe : JeiSoftBridge.getPrimitiveMintingRecipes()) {
            addMintRecipe(registry, primitiveCategory, recipe, true);
        }
        for (MintRecipe recipe : JeiSoftBridge.getActiveMintingRecipes()) {
            addMintRecipe(registry, industrialCategory, recipe, false);
        }

        List<TradeGood> liveGoods = TyC2RuntimeData.goodsCatalog().getAvailableGoods(
                TyC2Ids::itemExists, modLoaded, TyC2Ids::findFirstItemIdForTag);
        List<EmiIngredient> ecc2Stacks = new ArrayList<>();
        for (TradeGood good : liveGoods) {
            ItemStack itemStack = TyC2Ids.createStack(good.itemId(), 1, "", good.optionalEnchantmentSpec());
            if (itemStack.isEmpty()) {
                continue;
            }
            EmiStack goodEmi = EmiStack.of(itemStack);
            if ("ecera".equals(good.requiredModId())) {
                ecc2Stacks.add(goodEmi);
            }
            EmiStack coinEmi = representativeCoinStack(good.basePrice());
            if (coinEmi.isEmpty()) {
                continue;
            }
            if (good.isSellableByVillage()) {
                registry.addRecipe(new TradeEmiRecipe(
                        tradeCategory,
                        TyC2Ids.id("/trade/sell/" + sanitize(good.id())),
                        good,
                        coinEmi,
                        goodEmi,
                        false
                ));
            }
            if (good.isBuyableByVillage()) {
                EmiIngredient buyInput = resolveBuyGoodIngredient(good, goodEmi);
                registry.addRecipe(new TradeEmiRecipe(
                        tradeCategory,
                        TyC2Ids.id("/trade/buy/" + sanitize(good.id())),
                        good,
                        buyInput,
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
                TyC2Ids.id(prefix + sanitize(recipe.id())),
                recipe,
                resolvedInputs,
                output,
                primitive
        ));
    }

    private static void registerInfoPages(EmiRegistry registry, List<EmiIngredient> ecc2Stacks) {
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_ITEM.get())),
                List.of(Component.translatable("emi.info.mnera.primitive_mint_machine")),
                TyC2Ids.id("/info/primitive_mint_machine")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2ForgeRegistries.MINT_MACHINE_ITEM.get())),
                List.of(Component.translatable("emi.info.mnera.mint_machine")),
                TyC2Ids.id("/info/mint_machine")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2ForgeRegistries.ATM_MACHINE_ITEM.get())),
                List.of(Component.translatable("emi.info.mnera.atm_machine")),
                TyC2Ids.id("/info/atm_machine")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(TyC2ForgeRegistries.COOPERATION_HUB_ITEM.get())),
                List.of(Component.translatable("emi.info.mnera.cooperation_hub")),
                TyC2Ids.id("/info/cooperation_hub")
        ));
        ItemStack chargedTerminal = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(chargedTerminal, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        registry.addRecipe(new EmiInfoRecipe(
                List.of(
                        EmiStack.of(TyC2ForgeRegistries.COMM_TERMINAL.get()),
                        EmiStack.of(chargedTerminal)
                ),
                List.of(Component.translatable("emi.info.mnera.comm_terminal")),
                TyC2Ids.id("/info/comm_terminal")
        ));
        registry.addRecipe(new EmiInfoRecipe(
                List.of(
                        EmiStack.of(TyC2ForgeRegistries.COIN_1.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_5.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_10.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_20.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_50.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_100.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_200.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_500.get()),
                        EmiStack.of(TyC2ForgeRegistries.COIN_1000.get())
                ),
                List.of(Component.translatable("emi.info.mnera.coins")),
                TyC2Ids.id("/info/coins")
        ));
        if (!ecc2Stacks.isEmpty()) {
            registry.addRecipe(new EmiInfoRecipe(
                    ecc2Stacks,
                    List.of(Component.translatable("emi.info.mnera.ecera_trade")),
                    TyC2Ids.id("/info/ecera_trade")
            ));
        }
    }

    private static EmiIngredient resolveBuyGoodIngredient(TradeGood good, EmiStack fallback) {
        String optTag = good.optionalTag();
        if (optTag == null || optTag.isBlank()) {
            return fallback;
        }
        List<EmiIngredient> tagMatches = new ArrayList<>(4);
        for (String tagId : CommonTagAliases.expandTagCandidates(optTag)) {
            ResourceLocation rl = TyC2Ids.parse(tagId);
            if (rl != null) {
                EmiIngredient candidate = EmiIngredient.of(TagKey.create(Registry.ITEM_REGISTRY, rl), 1);
                if (!candidate.isEmpty()) {
                    tagMatches.add(candidate);
                }
            }
        }
        if (tagMatches.isEmpty()) {
            return fallback;
        }
        if (tagMatches.size() == 1) {
            return tagMatches.get(0);
        }
        return EmiIngredient.of(tagMatches, 1);
    }

    private static EmiIngredient resolveMintIngredient(MintIngredient ing) {
        if (!ing.isTag()) {
            ItemStack st = TyC2Ids.createStack(ing.spec(), ing.count(), "", null);
            return st.isEmpty() ? EmiStack.EMPTY : EmiStack.of(st);
        }
        List<EmiIngredient> tagMatches = new ArrayList<>(2);
        for (String tagId : ing.expandedTagIds()) {
            ResourceLocation rl = TyC2Ids.parse(tagId);
            if (rl != null) {
                EmiIngredient candidate = EmiIngredient.of(TagKey.create(Registry.ITEM_REGISTRY, rl), ing.count());
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
        ItemStack fallback = TyC2Ids.createStack(ing.representativeItemId(), ing.count(), "", null);
        return fallback.isEmpty() ? EmiStack.EMPTY : EmiStack.of(fallback);
    }

    private static EmiStack resolveMintOutput(MintRecipe recipe) {
        boolean ic2Loaded = Ic2Bridge.isLoaded();
        ItemStack out = TyC2Ids.createStack(recipe.outputItemId(ic2Loaded), recipe.outputCount(), "", null);
        if (out.isEmpty()) {
            out = TyC2Ids.createStack(recipe.outputDenomination().tyc2ItemId(), recipe.outputCount(), "", null);
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
        ItemStack stack = TyC2Ids.createStack(best.tyc2ItemId(), 1, "", null);
        return stack.isEmpty() ? EmiStack.EMPTY : EmiStack.of(stack);
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
                ResourceLocation id,
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
                    ? Component.translatable("jei.mnera.primitive_minting.fuel_only")
                    : Component.translatable("jei.mnera.minting.eu", recipe.energyEu());
            widgets.addText(powerLine, 4, 25, TEXT_COLOR, false);
            widgets.addText(
                    Component.translatable("jei.mnera.minting.ticks", effectiveTicks, formatSeconds(effectiveTicks)),
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
                ResourceLocation id,
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
                    Component.translatable(villageBuys ? "emi.mnera.trade.village_buys" : "emi.mnera.trade.village_sells"),
                    82,
                    9,
                    TEXT_COLOR,
                    false
            );
            widgets.addText(
                    Component.translatable("emi.mnera.trade.price_level", good.basePrice(), good.minLevel(), good.maxLevel()),
                    4,
                    25,
                    TEXT_COLOR,
                    false
            );
            Component catName = Component.translatable("category.mnera." + good.category().name().toLowerCase(Locale.ROOT));
            widgets.addText(
                    Component.translatable("emi.mnera.trade.tier_category", catName, good.industrialTierRequired().level()),
                    4,
                    35,
                    TEXT_COLOR,
                    false
            );
        }
    }
}
