package com.tycooncraft.tyc2.qa;

import com.industrialage.ia2.forge.equipment.ForgeGear;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldPowerPort;
import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.TyC2ForgeRegistries;
import com.tycooncraft.tyc2.forge.block.AtmMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.MintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.block.PrimitiveMintMachineBlockEntity;
import com.tycooncraft.tyc2.forge.item.CommTerminalItem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Isolated IC2 runtime assertions for Forge 1.19.2 Enhanced Mode QA.
 *
 * <p>NEVER referenced or classloaded in Standalone Mode (gated behind {@code ModList.get().isLoaded("mhera")}).
 */
final class TyC2QaIc2Checks {

    private TyC2QaIc2Checks() {
    }

    static void checkPowerPortSubclasses(MintMachineBlockEntity mint, AtmMachineBlockEntity atm, TyC2QaAssert qa) {
        qa.check("ic2.mint_implements_world_power_port",
                mint instanceof WorldPowerPort && mint instanceof EuBuffer,
                "class=" + mint.getClass().getName());
        qa.check("ic2.atm_implements_world_power_port",
                atm instanceof WorldPowerPort && atm instanceof EuBuffer,
                "class=" + atm.getClass().getName());
    }

    static void checkPortableGearMixinDirect(TyC2QaAssert qa) {
        ItemStack term = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(term, 0);

        qa.check("ic2.mixin_forge_gear.electrical_item", ForgeGear.electricalItem(term),
                "electricalItem=" + ForgeGear.electricalItem(term));
        qa.check("ic2.mixin_forge_gear.accepts_charge_lv", ForgeGear.acceptsCharge(term, 1),
                "acceptsCharge(LV)=" + ForgeGear.acceptsCharge(term, 1));
        qa.check("ic2.mixin_forge_gear.capacity_20000",
                Math.abs(ForgeGear.capacity(term) - TriModePowerMath.TERMINAL_MAX_CAPACITY_EU) < 1e-6,
                "capacity=" + ForgeGear.capacity(term));
        double accepted = ForgeGear.charge(term, 100.0, 1);
        qa.check("ic2.mixin_forge_gear.charge_returns_eu",
                accepted > 0.0 && CommTerminalItem.getStoredEu(term) == (int) Math.round(accepted),
                "accepted=" + accepted + ", stored=" + CommTerminalItem.getStoredEu(term));
        qa.check("ic2.mixin_forge_gear.stored_matches",
                Math.abs(ForgeGear.stored(term) - CommTerminalItem.getStoredEu(term)) < 1e-6,
                "ForgeGear.stored=" + ForgeGear.stored(term));
    }

    static void checkMetalFormerCoinRecipesDisabled(TyC2QaAssert qa) throws Exception {
        // 1. Verify Ic2ProcessingRecipesMixin filters out metal_former_cutting -> ic2:coin
        Class<?> procClass = Class.forName("com.industrialage.ia2.content.ProcessingRecipes");
        Field builtinField = procClass.getDeclaredField("BUILTIN");
        builtinField.setAccessible(true);
        Object procInstance = builtinField.get(null);
        qa.check("ic2.processing_recipes_builtin", procInstance != null,
                "BUILTIN=" + (procInstance == null ? "null" : procInstance.getClass().getName()));

        Method findMethod = procClass.getDeclaredMethod("find", String.class, String.class, Predicate.class);
        findMethod.setAccessible(true);
        Predicate<String> noTag = s -> false;
        Object coinRecipe = findMethod.invoke(procInstance, "metal_former_cutting", "mhera:iron_casing", noTag);
        qa.check("ic2.mixin_processing_recipes.coin_cutting_disabled", coinRecipe == null,
                "find(metal_former_cutting, mhera:iron_casing)=" + coinRecipe);

        // Ensure another builtin ProcessingRecipes entry still resolves cleanly (e.g., macerator on cobblestone/coal)
        Object otherRecipe = findMethod.invoke(procInstance, "macerator", "minecraft:cobblestone", noTag);
        if (otherRecipe == null) {
            otherRecipe = findMethod.invoke(procInstance, "macerator", "minecraft:coal", noTag);
        }
        qa.check("ic2.mixin_processing_recipes.other_recipes_preserved", otherRecipe != null,
                "macerator sample=" + otherRecipe);

        // 2. Verify Ic2WorkshopRecipesMixin filters out all metal_former_cutting recipes outputting ic2:coin
        Class<?> wsClass = Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes");
        Method forType = wsClass.getDeclaredMethod("forType", String.class);
        forType.setAccessible(true);
        Object rawList = forType.invoke(null, "metal_former_cutting");
        qa.check("ic2.workshop_recipes_list_present", rawList instanceof List<?> list && !list.isEmpty(),
                "metal_former_cutting size=" + (rawList instanceof List<?> l ? l.size() : "null"));

        List<?> recipes = (List<?>) rawList;
        int coinOutputs = 0;
        for (Object r : recipes) {
            if (recipeOutputsCoin(r)) {
                coinOutputs++;
            }
        }
        qa.checkEquals("ic2.mixin_workshop_recipes.zero_coin_outputs", 0, coinOutputs);
        qa.metric("ic2.workshop_cutting_recipes_remaining", recipes.size());
    }

    static void setupGeneratorCableAndMachines(
            ServerLevel level,
            BlockPos genPos,
            BlockPos cableMintPos,
            BlockPos mintPos,
            BlockPos cableAtmPos,
            BlockPos atmPos,
            TyC2QaAssert qa
    ) {
        Block cableBlock = requireBlock("mhera:insulated_copper_cable");
        Block genBlock = requireBlock("mhera:generator");

        // Place cables with ALL 6 arm properties explicitly FALSE first so ONLY real CableBlock.connects
        // (patched by Ic2CableBlockMixin) flips arms facing TyC2 machines to TRUE!
        BlockState bareCable = withAllCableArmsFalse(cableBlock.defaultBlockState());
        level.setBlock(cableMintPos, bareCable, Block.UPDATE_ALL);
        level.setBlock(cableAtmPos, bareCable, Block.UPDATE_ALL);
        level.setBlock(genPos, genBlock.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(mintPos, TyC2ForgeRegistries.MINT_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(atmPos, TyC2ForgeRegistries.ATM_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);

        BlockState cableMintState = level.getBlockState(cableMintPos);
        BlockState cableAtmState = level.getBlockState(cableAtmPos);
        Direction towardMint = directionFromTo(cableMintPos, mintPos);
        Direction towardAtm = directionFromTo(cableAtmPos, atmPos);
        Boolean mintArm = getCableDirectionProp(cableMintState, towardMint);
        Boolean mintAirArm = getCableDirectionProp(cableMintState, Direction.UP);
        Boolean atmArm = getCableDirectionProp(cableAtmState, towardAtm);
        Boolean atmAirArm = getCableDirectionProp(cableAtmState, Direction.UP);

        qa.check("ic2.mixin_cable_connects_mint",
                Boolean.TRUE.equals(mintArm) && Boolean.FALSE.equals(mintAirArm),
                "cable@mint arm(" + towardMint + ")=" + mintArm + ", up=" + mintAirArm);
        qa.check("ic2.mixin_cable_connects_atm",
                Boolean.TRUE.equals(atmArm) && Boolean.FALSE.equals(atmAirArm),
                "cable@atm arm(" + towardAtm + ")=" + atmArm + ", up=" + atmAirArm);

        // Fuel the IC2 Generator with coal (NO coal in Mint or ATM!)
        boolean fueled = insertIntoBlock(level, genPos, new ItemStack(Items.COAL, 16));
        qa.check("ic2.generator_fueled", fueled, "inserted 16 coal into mhera:generator at " + genPos);
    }

    /**
     * Verifies that IC2 copper cables do NOT visually connect to {@code tyc2:primitive_mint_machine},
     * and that its BlockEntity does NOT implement IC2 power interfaces.
     */
    static void checkCableDoesNotConnectToPrimitiveMint(
            ServerLevel level,
            BlockPos cablePos,
            BlockPos primPos,
            TyC2QaAssert qa
    ) {
        Block cableBlock = requireBlock("mhera:insulated_copper_cable");
        BlockState bareCable = withAllCableArmsFalse(cableBlock.defaultBlockState());
        level.setBlock(primPos, TyC2ForgeRegistries.PRIMITIVE_MINT_MACHINE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cablePos, bareCable, Block.UPDATE_ALL);
        // Trigger neighbor shape refresh explicitly so CableBlock.connects runs against primitive_mint_machine
        BlockState updated = level.getBlockState(cablePos);
        Direction towardPrim = directionFromTo(cablePos, primPos);
        Boolean primArm = getCableDirectionProp(updated, towardPrim);
        qa.check("ic2.cable_does_not_connect_primitive_mint",
                Boolean.FALSE.equals(primArm),
                "cable@primitive arm(" + towardPrim + ")=" + primArm);

        BlockEntity primBe = level.getBlockEntity(primPos);
        qa.check("ic2.primitive_mint_not_ic2_power_port",
                primBe instanceof PrimitiveMintMachineBlockEntity
                        && !(primBe instanceof WorldPowerPort)
                        && !(primBe instanceof EuBuffer),
                "class=" + (primBe == null ? "null" : primBe.getClass().getName()));
    }

    /** Creates a charged {@code ic2:re_battery} for verifying real IC2 battery discharge in Industrial Minting Machine. */
    static ItemStack createChargedIc2Battery(double targetEu) {
        Item reBat = requireItem("mhera:re_battery");
        ItemStack stack = new ItemStack(reBat, 1);
        double remaining = targetEu;
        for (int i = 0; i < 400 && remaining > 0.0; i++) {
            double added = ForgeGear.charge(stack, remaining, 1);
            if (added <= 0.0) {
                break;
            }
            remaining -= added;
        }
        return stack;
    }

    static void setupBatBoxTerminalCharging(
            ServerLevel level,
            BlockPos genPos,
            BlockPos cablePos,
            BlockPos batBoxPos,
            net.minecraft.world.entity.player.Player player,
            TyC2QaAssert qa
    ) {
        Block genBlock = requireBlock("mhera:generator");
        Block cableBlock = requireBlock("mhera:insulated_copper_cable");
        Block batBoxBlock = requireBlock("mhera:batbox");
        level.setBlock(genPos, genBlock.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(batBoxPos, batBoxBlock.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cablePos, cableBlock.defaultBlockState(), Block.UPDATE_ALL);

        boolean fueled = insertIntoBlock(level, genPos, new ItemStack(Items.COAL, 16));
        qa.check("ic2.batbox_generator_fueled", fueled, "fueled generator at " + genPos);

        // Place empty tyc2:comm_terminal into BatBox's charging slot (Container, IItemHandler, or MenuProvider GUI slot)
        ItemStack emptyTerm = new ItemStack(TyC2ForgeRegistries.COMM_TERMINAL.get());
        CommTerminalItem.setStoredEu(emptyTerm, 0);
        BlockEntity batBe = level.getBlockEntity(batBoxPos);
        qa.check("ic2.batbox_be_present", batBe != null, "batBe=" + batBe);
        boolean placed = false;
        String method = "none";
        if (batBe instanceof Container c) {
            for (int s = 0; s < c.getContainerSize(); s++) {
                if (c.canPlaceItem(s, emptyTerm)) {
                    c.setItem(s, emptyTerm);
                    c.setChanged();
                    placed = true;
                    method = "Container.slot=" + s;
                    break;
                }
            }
        }
        if (!placed && batBe != null) {
            for (Direction d : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                IItemHandler h = batBe.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElse(null);
                if (h != null) {
                    for (int s = 0; s < h.getSlots(); s++) {
                        if (h.insertItem(s, emptyTerm.copy(), false).isEmpty()) {
                            placed = true;
                            method = "IItemHandler(" + d + ").slot=" + s;
                            break;
                        }
                    }
                }
                if (placed) {
                    break;
                }
            }
        }
        if (!placed && batBe instanceof net.minecraft.world.MenuProvider mp) {
            var menu = mp.createMenu(0, player.getInventory(), player);
            if (menu != null) {
                for (int i = 0; i < menu.slots.size(); i++) {
                    var slot = menu.slots.get(i);
                    if (slot.container != player.getInventory() && slot.mayPlace(emptyTerm)) {
                        slot.set(emptyTerm);
                        slot.setChanged();
                        placed = true;
                        method = "MenuSlot=" + i;
                        break;
                    }
                }
            }
        }
        qa.check("ic2.batbox_terminal_inserted", placed, "method=" + method);
    }

    static int readBatBoxTerminalEu(ServerLevel level, BlockPos batBoxPos, net.minecraft.world.entity.player.Player player) {
        BlockEntity batBe = level.getBlockEntity(batBoxPos);
        if (batBe instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (st.getItem() instanceof CommTerminalItem) {
                    return CommTerminalItem.getStoredEu(st);
                }
            }
        }
        if (batBe != null) {
            for (Direction d : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH}) {
                IItemHandler h = batBe.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElse(null);
                if (h != null) {
                    for (int i = 0; i < h.getSlots(); i++) {
                        ItemStack st = h.getStackInSlot(i);
                        if (st.getItem() instanceof CommTerminalItem) {
                            return CommTerminalItem.getStoredEu(st);
                        }
                    }
                }
            }
            if (batBe instanceof net.minecraft.world.MenuProvider mp) {
                var menu = mp.createMenu(0, player.getInventory(), player);
                if (menu != null) {
                    for (var slot : menu.slots) {
                        if (slot.container != player.getInventory() && slot.getItem().getItem() instanceof CommTerminalItem) {
                            return CommTerminalItem.getStoredEu(slot.getItem());
                        }
                    }
                }
            }
        }
        return -1;
    }

    private static Object resolveProcessingRecipesInstance(Class<?> procClass) throws Exception {
        for (Method m : procClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                    && procClass.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                Object res = m.invoke(null);
                if (res != null) {
                    return res;
                }
            }
        }
        for (Field f : procClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && procClass.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object val = f.get(null);
                if (val != null) {
                    return val;
                }
            }
        }
        try {
            var ctor = procClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean recipeOutputsCoin(Object recipe) {
        if (recipe == null) {
            return false;
        }
        try {
            Method outputsM = recipe.getClass().getDeclaredMethod("outputs");
            outputsM.setAccessible(true);
            Object outputsObj = outputsM.invoke(recipe);
            if (outputsObj instanceof List<?> outputs) {
                for (Object st : outputs) {
                    if (st == null) {
                        continue;
                    }
                    Method idM = st.getClass().getDeclaredMethod("id");
                    idM.setAccessible(true);
                    if (CurrencyDenomination.IC2_COIN_ID.equals(idM.invoke(st))) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return recipe.toString().contains(CurrencyDenomination.IC2_COIN_ID);
        }
        return false;
    }

    private static Block requireBlock(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        if (b == null || b == Blocks.AIR) {
            throw new AssertionError("Missing required IC2 block: " + id);
        }
        return b;
    }

    private static Item requireItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item i = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (i == null || i == Items.AIR) {
            throw new AssertionError("Missing required IC2 item: " + id);
        }
        return i;
    }

    static BlockState withAllCableArmsFalse(BlockState cableState) {
        BlockState state = cableState;
        for (Direction dir : Direction.values()) {
            String name = dir.getName();
            for (Property<?> prop : state.getProperties()) {
                if (prop instanceof BooleanProperty bp && bp.getName().equals(name)) {
                    state = state.setValue(bp, Boolean.FALSE);
                }
            }
        }
        return state;
    }

    static Boolean getCableDirectionProp(BlockState cableState, Direction dir) {
        String name = dir.getName();
        for (Property<?> prop : cableState.getProperties()) {
            if (prop instanceof BooleanProperty bp && bp.getName().equals(name)) {
                return cableState.getValue(bp);
            }
        }
        return null;
    }

    private static Direction directionFromTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        for (Direction d : Direction.values()) {
            if (d.getStepX() == dx && d.getStepY() == dy && d.getStepZ() == dz) {
                return d;
            }
        }
        throw new IllegalArgumentException("Non-adjacent positions: " + from + " -> " + to);
    }

    private static boolean insertIntoBlock(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        IItemHandler h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (h != null && ItemHandlerHelper.insertItemStacked(h, stack.copy(), false).isEmpty()) {
            return true;
        }
        for (Direction d : Direction.values()) {
            IItemHandler sided = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElse(null);
            if (sided != null && ItemHandlerHelper.insertItemStacked(sided, stack.copy(), false).isEmpty()) {
                return true;
            }
        }
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stack)) {
                    c.setItem(i, stack.copy());
                    c.setChanged();
                    return true;
                }
            }
        }
        return false;
    }
}
