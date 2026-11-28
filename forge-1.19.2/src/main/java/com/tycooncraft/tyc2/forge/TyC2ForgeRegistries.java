package com.tycooncraft.tyc2.forge;

import com.google.common.collect.ImmutableSet;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlock;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.compat.Ic2Bridge;
import com.tycooncraft.tyc2.forge.gui.AtmMachineMenu;
import com.tycooncraft.tyc2.forge.gui.CommTerminalMenu;
import com.tycooncraft.tyc2.forge.gui.CooperationHubMenu;
import com.tycooncraft.tyc2.forge.gui.MayorNegotiationMenu;
import com.tycooncraft.tyc2.forge.gui.MintMachineMenu;
import com.tycooncraft.tyc2.forge.gui.PrimitiveMintMachineMenu;
import com.tycooncraft.tyc2.forge.item.CoinItem;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import com.tycooncraft.tyc2.forge.item.TyC2BlockItem;
import com.tycooncraft.tyc2.forge.util.TyC2Ids;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Canonical Forge 1.19.2 registries for all TycoonCraft² blocks, block entities, items,
 * menus, POI type, and creative mode tab.
 */
public final class TyC2ForgeRegistries {

    private TyC2ForgeRegistries() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TyC2Ids.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TyC2Ids.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TyC2Ids.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TyC2Ids.MOD_ID);
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, TyC2Ids.MOD_ID);

    // --- Creative Tab ---

    public static final CreativeModeTab CREATIVE_TAB = new CreativeModeTab(TyC2Ids.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(COIN_100.get());
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            items.add(new ItemStack(PRIMITIVE_MINT_MACHINE_ITEM.get()));
            items.add(new ItemStack(MINT_MACHINE_ITEM.get()));
            items.add(new ItemStack(ATM_MACHINE_ITEM.get()));
            items.add(new ItemStack(COOPERATION_HUB_ITEM.get()));
            items.add(new ItemStack(COMM_TERMINAL.get()));
            ItemStack chargedTerminal = new ItemStack(COMM_TERMINAL.get());
            CommTerminalItem.setStoredEu(chargedTerminal, 20_000);
            items.add(chargedTerminal);
            items.add(new ItemStack(COIN_1.get()));
            items.add(new ItemStack(COIN_5.get()));
            items.add(new ItemStack(COIN_10.get()));
            items.add(new ItemStack(COIN_20.get()));
            items.add(new ItemStack(COIN_50.get()));
            items.add(new ItemStack(COIN_100.get()));
            items.add(new ItemStack(COIN_200.get()));
            items.add(new ItemStack(COIN_500.get()));
            items.add(new ItemStack(COIN_1000.get()));
        }
    };

    // --- Blocks (4) ---

    public static final RegistryObject<Block> PRIMITIVE_MINT_MACHINE_BLOCK =
            BLOCKS.register("primitive_mint_machine", PrimitiveMintMachineBlock::new);

    public static final RegistryObject<Block> MINT_MACHINE_BLOCK =
            BLOCKS.register("mint_machine", Ic2Bridge::createMintBlock);

    public static final RegistryObject<Block> ATM_MACHINE_BLOCK =
            BLOCKS.register("atm_machine", Ic2Bridge::createAtmBlock);

    public static final RegistryObject<Block> COOPERATION_HUB_BLOCK =
            BLOCKS.register("cooperation_hub", CooperationHubBlock::new);

    // --- Block Entities (4) ---

    public static final RegistryObject<BlockEntityType<PrimitiveMintMachineBlockEntity>> PRIMITIVE_MINT_MACHINE_BE =
            BLOCK_ENTITIES.register("primitive_mint_machine", () ->
                    BlockEntityType.Builder.of(
                            PrimitiveMintMachineBlockEntity::new,
                            PRIMITIVE_MINT_MACHINE_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<MintMachineBlockEntity>> MINT_MACHINE_BE =
            BLOCK_ENTITIES.register("mint_machine", () ->
                    BlockEntityType.Builder.of(Ic2Bridge::createMintBlockEntity, MINT_MACHINE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<AtmMachineBlockEntity>> ATM_MACHINE_BE =
            BLOCK_ENTITIES.register("atm_machine", () ->
                    BlockEntityType.Builder.of(Ic2Bridge::createAtmBlockEntity, ATM_MACHINE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<CooperationHubBlockEntity>> COOPERATION_HUB_BE =
            BLOCK_ENTITIES.register("cooperation_hub", () ->
                    BlockEntityType.Builder.of(CooperationHubBlockEntity::new, COOPERATION_HUB_BLOCK.get()).build(null));

    // --- POI Type (1) ---

    public static final RegistryObject<PoiType> COOPERATION_HUB_POI =
            POI_TYPES.register("cooperation_hub", () -> new PoiType(
                    ImmutableSet.copyOf(COOPERATION_HUB_BLOCK.get().getStateDefinition().getPossibleStates()),
                    1,
                    1
            ));

    // --- Items (14 = 4 BlockItems + 9 Coins + 1 Comm Terminal) ---

    public static final RegistryObject<Item> PRIMITIVE_MINT_MACHINE_ITEM =
            ITEMS.register("primitive_mint_machine", () -> new TyC2BlockItem(
                    PRIMITIVE_MINT_MACHINE_BLOCK.get(),
                    tabProps(),
                    "tooltip.mnera.primitive_mint_machine.line1",
                    "tooltip.mnera.primitive_mint_machine.line2",
                    "tooltip.mnera.primitive_mint_machine.line3"
            ));

    public static final RegistryObject<Item> MINT_MACHINE_ITEM =
            ITEMS.register("mint_machine", () -> new TyC2BlockItem(
                    MINT_MACHINE_BLOCK.get(),
                    tabProps(),
                    "tooltip.mnera.mint_machine.line1",
                    "tooltip.mnera.mint_machine.line2",
                    "tooltip.mnera.mint_machine.line3"
            ));

    public static final RegistryObject<Item> ATM_MACHINE_ITEM =
            ITEMS.register("atm_machine", () -> new TyC2BlockItem(
                    ATM_MACHINE_BLOCK.get(),
                    tabProps(),
                    "tooltip.mnera.atm_machine.line1",
                    "tooltip.mnera.atm_machine.line2"
            ));

    public static final RegistryObject<Item> COOPERATION_HUB_ITEM =
            ITEMS.register("cooperation_hub", () -> new TyC2BlockItem(
                    COOPERATION_HUB_BLOCK.get(),
                    tabProps().stacksTo(1),
                    "tooltip.mnera.cooperation_hub.line1",
                    "tooltip.mnera.cooperation_hub.line2"
            ));

    public static final RegistryObject<Item> COIN_1 = registerCoin(CurrencyDenomination.X1);
    public static final RegistryObject<Item> COIN_5 = registerCoin(CurrencyDenomination.X5);
    public static final RegistryObject<Item> COIN_10 = registerCoin(CurrencyDenomination.X10);
    public static final RegistryObject<Item> COIN_20 = registerCoin(CurrencyDenomination.X20);
    public static final RegistryObject<Item> COIN_50 = registerCoin(CurrencyDenomination.X50);
    public static final RegistryObject<Item> COIN_100 = registerCoin(CurrencyDenomination.X100);
    public static final RegistryObject<Item> COIN_200 = registerCoin(CurrencyDenomination.X200);
    public static final RegistryObject<Item> COIN_500 = registerCoin(CurrencyDenomination.X500);
    public static final RegistryObject<Item> COIN_1000 = registerCoin(CurrencyDenomination.X1000);

    public static final RegistryObject<Item> COMM_TERMINAL =
            ITEMS.register("comm_terminal", () -> new CommTerminalItem(tabProps().stacksTo(1)));

    // --- Menus (6) ---

    public static final RegistryObject<MenuType<PrimitiveMintMachineMenu>> PRIMITIVE_MINT_MACHINE_MENU =
            MENUS.register("primitive_mint_machine", () -> IForgeMenuType.create(PrimitiveMintMachineMenu::new));

    public static final RegistryObject<MenuType<MintMachineMenu>> MINT_MACHINE_MENU =
            MENUS.register("mint_machine", () -> IForgeMenuType.create(MintMachineMenu::new));

    public static final RegistryObject<MenuType<AtmMachineMenu>> ATM_MACHINE_MENU =
            MENUS.register("atm_machine", () -> IForgeMenuType.create(AtmMachineMenu::new));

    public static final RegistryObject<MenuType<CooperationHubMenu>> COOPERATION_HUB_MENU =
            MENUS.register("cooperation_hub", () -> IForgeMenuType.create(CooperationHubMenu::new));

    public static final RegistryObject<MenuType<CommTerminalMenu>> COMM_TERMINAL_MENU =
            MENUS.register("comm_terminal", () -> IForgeMenuType.create(CommTerminalMenu::new));

    public static final RegistryObject<MenuType<MayorNegotiationMenu>> MAYOR_NEGOTIATION_MENU =
            MENUS.register("mayor_negotiation", () -> IForgeMenuType.create(MayorNegotiationMenu::new));

    private static RegistryObject<Item> registerCoin(CurrencyDenomination denom) {
        return ITEMS.register(denom.itemPath(), () -> new CoinItem(denom, tabProps().stacksTo(64)));
    }

    private static Item.Properties tabProps() {
        return new Item.Properties().tab(CREATIVE_TAB);
    }

    /** Registers all DeferredRegisters on the Forge mod event bus. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        POI_TYPES.register(modBus);
    }
}
