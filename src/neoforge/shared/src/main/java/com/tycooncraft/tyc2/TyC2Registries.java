package com.tycooncraft.tyc2;

import com.tycooncraft.tyc2.block.AtmMachineBlock;
import com.tycooncraft.tyc2.block.CooperationHubBlock;
import com.tycooncraft.tyc2.block.MintMachineBlock;
import com.tycooncraft.tyc2.block.PrimitiveMintMachineBlock;
import com.tycooncraft.tyc2.blockentity.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.blockentity.MintMachineBlockEntity;
import com.tycooncraft.tyc2.blockentity.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.item.CoinItem;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.item.CooperationHubBlockItem;
import com.tycooncraft.tyc2.menu.AtmMachineMenu;
import com.tycooncraft.tyc2.menu.CommTerminalMenu;
import com.tycooncraft.tyc2.menu.CooperationHubMenu;
import com.tycooncraft.tyc2.menu.MayorNegotiationMenu;
import com.tycooncraft.tyc2.menu.MintMachineMenu;
import com.tycooncraft.tyc2.menu.PrimitiveMintMachineMenu;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2TooltipBlockItem;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Canonical NeoForge registries for all TyC2 blocks, items, block entities, menus,
 * and the TycoonCraft² creative mode tab.
 */
public final class TyC2Registries {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(TyC2Constants.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TyC2Constants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TyC2Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TyC2Constants.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TyC2Constants.MOD_ID);

    // --- Blocks (4) ---

    public static final DeferredBlock<PrimitiveMintMachineBlock> PRIMITIVE_MINT_MACHINE_BLOCK = TyC2Native.registerBlock(
            BLOCKS,
            "primitive_mint_machine",
            PrimitiveMintMachineBlock::new,
            () -> Block.Properties.of()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(PrimitiveMintMachineBlock.ACTIVE) ? 13 : 0)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<MintMachineBlock> MINT_MACHINE_BLOCK = TyC2Native.registerBlock(
            BLOCKS,
            "mint_machine",
            MintMachineBlock::new,
            () -> Block.Properties.of()
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<AtmMachineBlock> ATM_MACHINE_BLOCK = TyC2Native.registerBlock(
            BLOCKS,
            "atm_machine",
            AtmMachineBlock::new,
            () -> Block.Properties.of()
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<CooperationHubBlock> COOPERATION_HUB_BLOCK = TyC2Native.registerBlock(
            BLOCKS,
            "cooperation_hub",
            CooperationHubBlock::new,
            () -> Block.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.METAL)
                    .noLootTable()
    );

    // --- Block Items (4) ---

    public static final DeferredItem<TyC2TooltipBlockItem> PRIMITIVE_MINT_MACHINE_ITEM = TyC2Native.registerCustomBlockItem(
            ITEMS,
            "primitive_mint_machine",
            props -> new TyC2TooltipBlockItem(
                    PRIMITIVE_MINT_MACHINE_BLOCK.get(),
                    props,
                    List.of(
                            "tooltip.mnera.primitive_mint_machine.line1",
                            "tooltip.mnera.primitive_mint_machine.line2",
                            "tooltip.mnera.primitive_mint_machine.line3"
                    )
            ),
            Item.Properties::new
    );

    public static final DeferredItem<TyC2TooltipBlockItem> MINT_MACHINE_ITEM = TyC2Native.registerCustomBlockItem(
            ITEMS,
            "mint_machine",
            props -> new TyC2TooltipBlockItem(
                    MINT_MACHINE_BLOCK.get(),
                    props,
                    List.of(
                            "tooltip.mnera.mint_machine.line1",
                            "tooltip.mnera.mint_machine.line2",
                            "tooltip.mnera.mint_machine.line3"
                    )
            ),
            Item.Properties::new
    );

    public static final DeferredItem<TyC2TooltipBlockItem> ATM_MACHINE_ITEM = TyC2Native.registerCustomBlockItem(
            ITEMS,
            "atm_machine",
            props -> new TyC2TooltipBlockItem(
                    ATM_MACHINE_BLOCK.get(),
                    props,
                    List.of(
                            "tooltip.mnera.atm_machine.line1",
                            "tooltip.mnera.atm_machine.line2"
                    )
            ),
            Item.Properties::new
    );

    public static final DeferredItem<CooperationHubBlockItem> COOPERATION_HUB_ITEM = TyC2Native.registerCustomBlockItem(
            ITEMS,
            "cooperation_hub",
            props -> new CooperationHubBlockItem(COOPERATION_HUB_BLOCK.get(), props),
            Item.Properties::new
    );

    // --- Coins (9) ---

    public static final DeferredItem<CoinItem> COIN_1 = registerCoin(CurrencyDenomination.X1);
    public static final DeferredItem<CoinItem> COIN_5 = registerCoin(CurrencyDenomination.X5);
    public static final DeferredItem<CoinItem> COIN_10 = registerCoin(CurrencyDenomination.X10);
    public static final DeferredItem<CoinItem> COIN_20 = registerCoin(CurrencyDenomination.X20);
    public static final DeferredItem<CoinItem> COIN_50 = registerCoin(CurrencyDenomination.X50);
    public static final DeferredItem<CoinItem> COIN_100 = registerCoin(CurrencyDenomination.X100);
    public static final DeferredItem<CoinItem> COIN_200 = registerCoin(CurrencyDenomination.X200);
    public static final DeferredItem<CoinItem> COIN_500 = registerCoin(CurrencyDenomination.X500);
    public static final DeferredItem<CoinItem> COIN_1000 = registerCoin(CurrencyDenomination.X1000);

    // --- Electric Handheld (1) ---

    public static final DeferredItem<CommTerminalItem> COMM_TERMINAL_ITEM = TyC2Native.registerItem(
            ITEMS,
            "comm_terminal",
            CommTerminalItem::new,
            Item.Properties::new
    );

    // --- Block Entities (4) ---

    public static final Supplier<BlockEntityType<PrimitiveMintMachineBlockEntity>> PRIMITIVE_MINT_MACHINE_BE =
            BLOCK_ENTITIES.register(
                    "primitive_mint_machine",
                    () -> TyC2Native.createBlockEntityType(
                            PrimitiveMintMachineBlockEntity::new,
                            PRIMITIVE_MINT_MACHINE_BLOCK.get()
                    )
            );

    public static final Supplier<BlockEntityType<MintMachineBlockEntity>> MINT_MACHINE_BE =
            BLOCK_ENTITIES.register(
                    "mint_machine",
                    () -> TyC2Native.createBlockEntityType(MintMachineBlockEntity::new, MINT_MACHINE_BLOCK.get())
            );

    public static final Supplier<BlockEntityType<AtmMachineBlockEntity>> ATM_MACHINE_BE =
            BLOCK_ENTITIES.register(
                    "atm_machine",
                    () -> TyC2Native.createBlockEntityType(AtmMachineBlockEntity::new, ATM_MACHINE_BLOCK.get())
            );

    public static final Supplier<BlockEntityType<CooperationHubBlockEntity>> COOPERATION_HUB_BE =
            BLOCK_ENTITIES.register(
                    "cooperation_hub",
                    () -> TyC2Native.createBlockEntityType(CooperationHubBlockEntity::new, COOPERATION_HUB_BLOCK.get())
            );

    // --- Menus (6) ---

    public static final Supplier<MenuType<PrimitiveMintMachineMenu>> PRIMITIVE_MINT_MACHINE_MENU =
            MENUS.register("primitive_mint_machine", () -> IMenuTypeExtension.create(PrimitiveMintMachineMenu::new));

    public static final Supplier<MenuType<MintMachineMenu>> MINT_MACHINE_MENU =
            MENUS.register("mint_machine", () -> IMenuTypeExtension.create(MintMachineMenu::new));

    public static final Supplier<MenuType<AtmMachineMenu>> ATM_MACHINE_MENU =
            MENUS.register("atm_machine", () -> IMenuTypeExtension.create(AtmMachineMenu::new));

    public static final Supplier<MenuType<CooperationHubMenu>> COOPERATION_HUB_MENU =
            MENUS.register("cooperation_hub", () -> IMenuTypeExtension.create(CooperationHubMenu::new));

    public static final Supplier<MenuType<CommTerminalMenu>> COMM_TERMINAL_MENU =
            MENUS.register("comm_terminal", () -> IMenuTypeExtension.create(CommTerminalMenu::new));

    public static final Supplier<MenuType<MayorNegotiationMenu>> MAYOR_NEGOTIATION_MENU =
            MENUS.register("mayor_negotiation", () -> IMenuTypeExtension.create(MayorNegotiationMenu::new));

    // --- Creative Tab (1) ---

    public static final Supplier<CreativeModeTab> MAIN_TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mnera.main"))
                    .icon(() -> new ItemStack(COIN_100.get()))
                    .displayItems((params, output) -> {
                        output.accept(PRIMITIVE_MINT_MACHINE_ITEM.get());
                        output.accept(MINT_MACHINE_ITEM.get());
                        output.accept(ATM_MACHINE_ITEM.get());
                        output.accept(COOPERATION_HUB_ITEM.get());
                        ItemStack chargedTerminal = new ItemStack(COMM_TERMINAL_ITEM.get());
                        CommTerminalItem.setEnergyEu(chargedTerminal, 20_000);
                        output.accept(COMM_TERMINAL_ITEM.get());
                        output.accept(chargedTerminal);
                        output.accept(COIN_1.get());
                        output.accept(COIN_5.get());
                        output.accept(COIN_10.get());
                        output.accept(COIN_20.get());
                        output.accept(COIN_50.get());
                        output.accept(COIN_100.get());
                        output.accept(COIN_200.get());
                        output.accept(COIN_500.get());
                        output.accept(COIN_1000.get());
                    })
                    .build()
    );

    private TyC2Registries() {
    }

    private static DeferredItem<CoinItem> registerCoin(CurrencyDenomination denomination) {
        return TyC2Native.registerItem(
                ITEMS,
                denomination.itemPath(),
                props -> new CoinItem(props, denomination),
                Item.Properties::new
        );
    }

    /** Attaches all DeferredRegisters to the mod event bus. */
    public static void install(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }
}
