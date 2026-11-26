package com.tycooncraft.tyc2.platform;

import com.tycooncraft.tyc2.TyC2Constants;
import com.tycooncraft.tyc2.TyC2Registries;
import com.tycooncraft.tyc2.blockentity.TyC2FeSink;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.item.CommTerminalItem;
import com.tycooncraft.tyc2.village.SlotStack;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21.1 version-specific native API shims for TyC2.
 */
public final class TyC2Native {

    private static final Logger LOGGER = LoggerFactory.getLogger(TyC2Native.class);

    private TyC2Native() {
    }

    /** Returns the canonical qualified registry ID of the given stack's item. */
    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return itemId(stack.getItem());
    }

    /** Returns the canonical qualified registry ID of the given item. */
    public static String itemId(Item item) {
        if (item == null) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    /** Returns the canonical qualified registry ID of a block. */
    public static String blockId(Block block) {
        if (block == null) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? "" : key.toString();
    }

    /** Returns the canonical qualified registry ID of a block state's block. */
    public static String blockId(BlockState state) {
        return state == null ? "" : blockId(state.getBlock());
    }

    /** Parses a qualified registry identifier into 1.21.1's {@link ResourceLocation}. */
    public static Object parseId(String qualifiedId) {
        return qualifiedId == null ? null : ResourceLocation.tryParse(qualifiedId.trim());
    }

    /** Syncs CommTerminal charged visual state (handled by item property overrides on 1.21.1). */
    public static void syncTerminalChargeVisual(ItemStack stack, boolean charged) {
        // 1.21.1 uses registered ClampedItemPropertyFunction ("tyc2:empty") in TyC2ClientSetup.
    }

    /** Looks up a registered item by qualified ID. */
    public static Optional<Item> findItem(String qualifiedId) {
        if (qualifiedId == null || qualifiedId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation rl = ResourceLocation.tryParse(qualifiedId.trim());
        if (rl == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(rl);
    }

    /** Returns {@code true} if the qualified item ID is registered in Minecraft's live item registry. */
    public static boolean itemExists(String qualifiedId) {
        if (qualifiedId == null || qualifiedId.isBlank()) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(qualifiedId.trim());
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
    }

    /** Looks up a registered block by qualified ID. */
    public static Optional<Block> findBlock(String qualifiedId) {
        if (qualifiedId == null || qualifiedId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation rl = ResourceLocation.tryParse(qualifiedId.trim());
        if (rl == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(rl);
    }

    /** Returns {@code true} if the qualified block ID is registered in Minecraft's live block registry. */
    public static boolean blockExists(String qualifiedId) {
        if (qualifiedId == null || qualifiedId.isBlank()) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(qualifiedId.trim());
        return rl != null && BuiltInRegistries.BLOCK.containsKey(rl);
    }

    /** Creates an ItemStack from a qualified item ID and count, or empty if absent. */
    public static ItemStack stack(String qualifiedId, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        return findItem(qualifiedId).map(item -> new ItemStack(item, count)).orElse(ItemStack.EMPTY);
    }

    /** Checks whether {@code stack} belongs to item tag {@code tagId} (without leading {@code '#'}). */
    public static boolean itemHasTag(ItemStack stack, String tagId) {
        if (stack == null || stack.isEmpty() || tagId == null || tagId.isBlank()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            return false;
        }
        return stack.is(TagKey.create(Registries.ITEM, rl));
    }

    /** Checks whether a registered item ID belongs to item tag {@code tagId}. */
    public static boolean itemIdHasTag(String itemId, String tagId) {
        Optional<Item> itemOpt = findItem(itemId);
        if (itemOpt.isEmpty() || tagId == null || tagId.isBlank()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            return false;
        }
        return itemOpt.get().builtInRegistryHolder().is(TagKey.create(Registries.ITEM, rl));
    }

    /** Checks whether {@code state} belongs to block tag {@code tagId}. */
    public static boolean blockHasTag(BlockState state, String tagId) {
        if (state == null || tagId == null || tagId.isBlank()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            return false;
        }
        return state.is(TagKey.create(Registries.BLOCK, rl));
    }

    /** Returns the namespaced dimension ID string of a Level. */
    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    /** Returns the namespaced biome ID string at {@code pos}. */
    public static String biomeId(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos)
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("minecraft:plains");
    }

    /** Returns {@code true} if the level is client-side. */
    public static boolean isClientSide(Level level) {
        return level.isClientSide;
    }

    /** Sends an actionbar (overlay) message to a player. */
    public static void sendActionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** Returns the minimum build Y coordinate of the level. */
    public static int minBuildY(Level level) {
        return level.getMinBuildHeight();
    }

    /** Returns the maximum build Y coordinate of the level. */
    public static int maxBuildY(Level level) {
        return level.getMaxBuildHeight();
    }

    /** Returns {@code true} if the player has OP permission level >= 2. */
    public static boolean hasOpPermission(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    /** Returns {@code true} if the command source has OP permission level >= 2. */
    public static boolean hasOpPermission(CommandSourceStack source) {
        return source != null && source.hasPermission(2);
    }

    /** Registers a block on NeoForge 1.21.1. */
    public static <T extends Block> DeferredBlock<T> registerBlock(
            DeferredRegister.Blocks blocks,
            String path,
            Function<Block.Properties, T> factory,
            Supplier<Block.Properties> properties
    ) {
        return blocks.registerBlock(path, factory, properties.get());
    }

    /** Registers an item on NeoForge 1.21.1. */
    public static <T extends Item> DeferredItem<T> registerItem(
            DeferredRegister.Items items,
            String path,
            Function<Item.Properties, T> factory,
            Supplier<Item.Properties> properties
    ) {
        return items.registerItem(path, factory, properties.get());
    }

    /** Registers a block item with localized tooltip lines on NeoForge 1.21.1. */
    public static <T extends TyC2TooltipBlockItem> DeferredItem<T> registerCustomBlockItem(
            DeferredRegister.Items items,
            String path,
            Function<Item.Properties, T> factory,
            Supplier<Item.Properties> properties
    ) {
        return items.registerItem(path, factory, properties.get());
    }

    /** Creates a BlockEntityType on NeoForge 1.21.1. */
    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory,
            Block... blocks
    ) {
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }

    /** Creates a CustomPacketPayload.Type for a tyc2 packet path. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(TyC2Constants.MOD_ID, path)
        );
    }

    /** Registers one C2S and one S2C payload on NeoForge 1.21.1. */
    public static <C extends CustomPacketPayload, S extends CustomPacketPayload> void registerPayloads(
            IEventBus modBus,
            Dist side,
            String protocolVersion,
            CustomPacketPayload.Type<C> c2sType,
            StreamCodec<RegistryFriendlyByteBuf, C> c2sCodec,
            IPayloadHandler<C> c2sHandler,
            CustomPacketPayload.Type<S> s2cType,
            StreamCodec<RegistryFriendlyByteBuf, S> s2cCodec,
            IPayloadHandler<S> s2cHandler
    ) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var registrar = event.registrar(protocolVersion).executesOn(HandlerThread.MAIN);
            registrar.playToServer(c2sType, c2sCodec, c2sHandler);
            registrar.playToClient(s2cType, s2cCodec, s2cHandler);
        });
    }

    /** Sends a payload from client to server. */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Sends a payload from server to a single player. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Registers block Item/Energy capabilities and CommTerminal Item Energy capability on 1.21.1. */
    public static void registerCapabilities(IEventBus modBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> {
            // Primitive Minting Machine: Item capability ONLY (NO EnergyStorage capability!)
            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    TyC2Registries.PRIMITIVE_MINT_MACHINE_BE.get(),
                    (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side)
            );

            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    TyC2Registries.MINT_MACHINE_BE.get(),
                    (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side)
            );
            event.registerBlockEntity(
                    Capabilities.EnergyStorage.BLOCK,
                    TyC2Registries.MINT_MACHINE_BE.get(),
                    (be, side) -> wrapFeSink(be)
            );

            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    TyC2Registries.ATM_MACHINE_BE.get(),
                    (be, side) -> new SidedInvWrapper(be, side == null ? Direction.UP : side)
            );
            event.registerBlockEntity(
                    Capabilities.EnergyStorage.BLOCK,
                    TyC2Registries.ATM_MACHINE_BE.get(),
                    (be, side) -> wrapFeSink(be)
            );

            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    TyC2Registries.COOPERATION_HUB_BE.get(),
                    (be, side) -> new SidedInvWrapper(be, side == null ? Direction.UP : side)
            );

            event.registerItem(
                    Capabilities.EnergyStorage.ITEM,
                    (stack, ctx) -> wrapTerminalItemEnergy(stack),
                    TyC2Registries.COMM_TERMINAL_ITEM.get()
            );
        });
    }

    /** Returns Minecraft smelting burn time in ticks for {@code stack}, or {@code 0} if not burnable. */
    public static int furnaceBurnTicks(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return Math.max(0, stack.getBurnTime(RecipeType.SMELTING));
    }

    /** Returns the crafting remaining item for {@code stack} (e.g. empty bucket for lava bucket). */
    public static ItemStack craftingRemainder(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return stack.getCraftingRemainingItem();
    }

    /** Returns {@code true} if {@code stack} exposes a NeoForge FE EnergyStorage that can discharge. */
    public static boolean canDischargeFeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage != null && storage.canExtract() && storage.getEnergyStored() > 0;
    }

    /** Discharges up to {@code maxEu} (converted at 4 FE = 1 EU) from an FE item in {@code container[slot]}. */
    public static double dischargeFeItem(Container container, int slot, double maxEu) {
        if (container == null || maxEu <= 0.0) {
            return 0.0;
        }
        ItemStack stack = container.getItem(slot);
        if (stack.isEmpty()) {
            return 0.0;
        }
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canExtract() || storage.getEnergyStored() <= 0) {
            return 0.0;
        }
        int requestFe = (int) Math.min(Integer.MAX_VALUE, Math.floor(maxEu * TriModePowerMath.EU_TO_FE_RATIO));
        if (requestFe <= 0) {
            return 0.0;
        }
        int extractedFe = storage.extractEnergy(requestFe, false);
        if (extractedFe <= 0) {
            return 0.0;
        }
        return extractedFe / (double) TriModePowerMath.EU_TO_FE_RATIO;
    }

    private static IEnergyStorage wrapFeSink(TyC2FeSink sink) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                return sink.receiveFe(toReceive, simulate);
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return sink.getStoredFe();
            }

            @Override
            public int getMaxEnergyStored() {
                return sink.getMaxFe();
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    private static IEnergyStorage wrapTerminalItemEnergy(ItemStack stack) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                if (toReceive <= 0) {
                    return 0;
                }
                int maxRateFe = TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK * TriModePowerMath.EU_TO_FE_RATIO;
                int offeredFe = Math.min(toReceive, maxRateFe);
                int offeredEu = (int) TriModePowerMath.feToEuFloor(offeredFe);
                if (offeredEu <= 0) {
                    return 0;
                }
                int currentEu = CommTerminalItem.getEnergyEu(stack);
                int spaceEu = Math.max(0, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU - currentEu);
                int acceptedEu = Math.min(offeredEu, spaceEu);
                if (acceptedEu > 0 && !simulate) {
                    CommTerminalItem.setEnergyEu(stack, currentEu + acceptedEu);
                }
                return acceptedEu * TriModePowerMath.EU_TO_FE_RATIO;
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return CommTerminalItem.getEnergyEu(stack) * TriModePowerMath.EU_TO_FE_RATIO;
            }

            @Override
            public int getMaxEnergyStored() {
                return TriModePowerMath.TERMINAL_MAX_CAPACITY_EU * TriModePowerMath.EU_TO_FE_RATIO;
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    /** Subscribes a block-break listener on 1.21.1 ({@link BlockEvent.BreakEvent}). */
    public static void registerBreakBlockListener(
            IEventBus gameBus,
            TriBreakHandler onBreak
    ) {
        gameBus.addListener(BlockEvent.BreakEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                boolean cancel = onBreak.handleBreak(serverLevel, event.getPos(), event.getState(), event.getPlayer());
                if (cancel) {
                    event.setCanceled(true);
                }
            }
        });
    }

    /** Functional callback for block-break interception. */
    @FunctionalInterface
    public interface TriBreakHandler {
        /** Returns {@code true} to cancel breaking the block. */
        boolean handleBreak(ServerLevel level, BlockPos pos, BlockState state, Player player);
    }

    /**
     * Registers the TyC2 datapack JSON reload listener on 1.21.1 ({@link AddReloadListenerEvent}).
     * Scans both built-in {@code data/tyc2/{mint_recipes,goods,seasons,levels}/*.json} and
     * third-party {@code data/<ns>/tyc2/{mint_recipes,goods,seasons,levels}/*.json}.
     */
    public static void registerReloadListener(
            IEventBus gameBus,
            Runnable onReloadBegin,
            BiConsumer<String, String> onJsonEntry,
            Runnable onReloadComplete
    ) {
        gameBus.addListener(AddReloadListenerEvent.class, event -> event.addListener(
                new SimplePreparableReloadListener<Map<String, String>>() {
                    @Override
                    protected Map<String, String> prepare(ResourceManager manager, ProfilerFiller profiler) {
                        return scanDatapackJson(manager);
                    }

                    @Override
                    protected void apply(Map<String, String> entries, ResourceManager manager, ProfilerFiller profiler) {
                        onReloadBegin.run();
                        entries.forEach(onJsonEntry);
                        onReloadComplete.run();
                    }
                }
        ));
    }

    private static Map<String, String> scanDatapackJson(ResourceManager manager) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] roots = {
                "mint_recipes", "goods", "seasons", "levels", "workplaces",
                "tyc2/mint_recipes", "tyc2/goods", "tyc2/seasons", "tyc2/levels", "tyc2/workplaces"
        };
        for (String root : roots) {
            boolean builtInNamespaceOnly = !root.startsWith("tyc2/");
            Map<ResourceLocation, Resource> found = manager.listResources(
                    root, rl -> rl.getPath().endsWith(".json")
            );
            List<ResourceLocation> keys = new ArrayList<>(found.keySet());
            keys.sort(ResourceLocation::compareTo);
            for (ResourceLocation id : keys) {
                if (builtInNamespaceOnly && !TyC2Constants.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                try (BufferedReader reader = found.get(id).openAsReader()) {
                    String content = reader.lines().collect(Collectors.joining("\n"));
                    String category = root.startsWith("tyc2/") ? root.substring("tyc2/".length()) : root;
                    result.put(category + "|" + id, content);
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read TyC2 datapack resource {}: {}", id, ex.getMessage());
                }
            }
        }
        return result;
    }

    // --- Villager & Entity Shims (net.minecraft.world.entity.npc.Villager in 1.21.1) ---

    /** Returns {@code true} if {@code entity} is a vanilla Minecraft Villager (including subclasses). */
    public static boolean isVillager(Entity entity) {
        return entity instanceof Villager;
    }

    /** Returns {@code true} strictly for exact vanilla {@link Villager} instances (excludes modded NPC subclasses). */
    public static boolean isExactVanillaVillager(Entity entity) {
        return entity != null && entity.getClass() == Villager.class;
    }

    /** Returns {@code true} if {@code entity} is a living adult Villager. */
    public static boolean isAdultVillager(Entity entity) {
        return entity instanceof Villager v && v.isAlive() && !v.isBaby();
    }

    /** Returns {@code true} if {@code entity} is a Nitwit Villager. */
    public static boolean isNitwitVillager(Entity entity) {
        return entity instanceof Villager v && v.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    /**
     * Returns the canonical lowercase profession registry ID of a villager
     * (e.g. {@code "minecraft:farmer"}, {@code "minecraft:none"}, {@code "minecraft:nitwit"},
     * or modded {@code "ns:path"}). Returns {@code "minecraft:none"} for non-villager entities.
     */
    public static String villagerProfessionId(Entity entity) {
        if (!(entity instanceof Villager v)) {
            return "minecraft:none";
        }
        VillagerProfession prof = v.getVillagerData().getProfession();
        if (prof == null) {
            return "minecraft:none";
        }
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
        return key == null ? "minecraft:none" : key.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Scans {@link BuiltInRegistries#VILLAGER_PROFESSION} and {@link BuiltInRegistries#POINT_OF_INTEREST_TYPE}
     * to discover non-{@code none}/non-{@code nitwit} professions with a valid non-air default workstation block.
     */
    public static List<DiscoveredProfession> discoverModdedProfessions() {
        List<DiscoveredProfession> discovered = new ArrayList<>();
        try {
            List<Holder.Reference<PoiType>> poiHolders = BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders()
                    .sorted(Comparator.comparing(h -> h.key().location().toString()))
                    .toList();
            List<Map.Entry<ResourceKey<VillagerProfession>, VillagerProfession>> entries =
                    new ArrayList<>(BuiltInRegistries.VILLAGER_PROFESSION.entrySet());
            entries.sort(Comparator.comparing(e -> e.getKey().location().toString()));

            for (Map.Entry<ResourceKey<VillagerProfession>, VillagerProfession> entry : entries) {
                try {
                    ResourceLocation rl = entry.getKey().location();
                    String profId = rl.toString().toLowerCase(Locale.ROOT);
                    VillagerProfession prof = entry.getValue();
                    if (prof == null
                            || prof == VillagerProfession.NONE
                            || prof == VillagerProfession.NITWIT
                            || "minecraft:none".equals(profId)
                            || "minecraft:nitwit".equals(profId)) {
                        continue;
                    }
                    String workstationBlockId = findWorkstationBlockId(prof, poiHolders);
                    if (workstationBlockId == null || workstationBlockId.isBlank()) {
                        continue;
                    }
                    String rawName = prof.name();
                    String fallback = (rawName != null
                            && !rawName.isBlank()
                            && !rawName.contains(":")
                            && !rawName.contains(".")
                            && !rawName.equals(rl.getPath()))
                            ? rawName.trim()
                            : "";
                    discovered.add(new DiscoveredProfession(profId, workstationBlockId, fallback));
                } catch (Throwable ignored) {
                    // Isolate malformed third-party VillagerProfession registrations
                }
            }
        } catch (Throwable ex) {
            LOGGER.warn("Failed to scan villager professions for dynamic discovery: {}", ex.getMessage());
        }
        return discovered;
    }

    private static String findWorkstationBlockId(
            VillagerProfession prof,
            List<Holder.Reference<PoiType>> poiHolders
    ) {
        String fromHeld = matchWorkstationBlock(prof.heldJobSite(), poiHolders);
        if (fromHeld != null) {
            return fromHeld;
        }
        return matchWorkstationBlock(prof.acquirableJobSite(), poiHolders);
    }

    private static String matchWorkstationBlock(
            Predicate<Holder<PoiType>> predicate,
            List<Holder.Reference<PoiType>> poiHolders
    ) {
        if (predicate == null
                || predicate == PoiType.NONE
                || predicate == VillagerProfession.ALL_ACQUIRABLE_JOBS) {
            return null;
        }
        List<Holder.Reference<PoiType>> matches = new ArrayList<>();
        for (Holder.Reference<PoiType> poi : poiHolders) {
            try {
                if (predicate.test(poi)) {
                    matches.add(poi);
                }
            } catch (Throwable ignored) {
                // Skip misbehaving third-party POI predicates
            }
        }
        if (matches.isEmpty() || matches.size() > 8) {
            return null;
        }
        for (Holder.Reference<PoiType> poi : matches) {
            try {
                PoiType poiVal = poi.value();
                if (poiVal == null || poiVal.matchingStates() == null) {
                    continue;
                }
                String bestBlock = null;
                for (BlockState state : poiVal.matchingStates()) {
                    if (state == null || state.isAir() || state.getBlock().asItem() == Items.AIR) {
                        continue;
                    }
                    String bid = blockId(state).toLowerCase(Locale.ROOT);
                    if (bid.isBlank() || "minecraft:air".equals(bid)
                            || "minecraft:cave_air".equals(bid)
                            || "minecraft:void_air".equals(bid)) {
                        continue;
                    }
                    if (bestBlock == null || bid.compareTo(bestBlock) < 0) {
                        bestBlock = bid;
                    }
                }
                if (bestBlock != null) {
                    return bestBlock;
                }
            } catch (Throwable ignored) {
                // Skip broken third-party PoiType definitions
            }
        }
        return null;
    }

    /**
     * Immediately assigns an adult unemployed non-nitwit villager to the workstation at
     * {@code workstationPos} with {@code professionId}, claiming its POI ticket when available,
     * setting {@link MemoryModuleType#JOB_SITE}, updating {@code VillagerData}, and guiding navigation.
     */
    public static boolean assignVillagerToWorkstation(
            ServerLevel level,
            Entity villager,
            BlockPos workstationPos,
            String professionId
    ) {
        if (level == null
                || !(villager instanceof Villager v)
                || !v.isAlive()
                || v.isBaby()
                || v.getVillagerXp() > 0
                || isNitwitVillager(v)
                || workstationPos == null
                || professionId == null
                || professionId.isBlank()
                || !level.hasChunkAt(workstationPos)) {
            return false;
        }
        BlockState state = level.getBlockState(workstationPos);
        if (state.isAir()) {
            return false;
        }
        ResourceLocation profRl = ResourceLocation.tryParse(professionId.trim().toLowerCase(Locale.ROOT));
        if (profRl == null) {
            return false;
        }
        Optional<VillagerProfession> profOpt = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(profRl);
        if (profOpt.isEmpty()) {
            return false;
        }
        VillagerProfession prof = profOpt.get();
        if (prof == VillagerProfession.NONE || prof == VillagerProfession.NITWIT) {
            return false;
        }

        // Ensure POI is registered at workstationPos (handles same-tick block placement) and claim ticket if free
        try {
            PoiManager poiManager = level.getPoiManager();
            if (poiManager.getType(workstationPos).isEmpty()) {
                PoiTypes.forState(state).ifPresent(poi -> poiManager.add(workstationPos, poi));
            }
            Predicate<Holder<PoiType>> poiPred = h -> {
                try {
                    return (prof.heldJobSite() != null && prof.heldJobSite().test(h))
                            || (prof.acquirableJobSite() != null && prof.acquirableJobSite().test(h));
                } catch (Throwable ignored) {
                    return false;
                }
            };
            poiManager.take(poiPred, (h, p) -> p.equals(workstationPos), workstationPos, 1);
        } catch (Throwable ex) {
            LOGGER.debug("POI claim skipped for {} at {}: {}", professionId, workstationPos, ex.getMessage());
        }

        if (v.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) {
            v.releasePoi(MemoryModuleType.JOB_SITE);
        }
        v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        GlobalPos jobPos = GlobalPos.of(level.dimension(), workstationPos);
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobPos);
        v.setVillagerData(v.getVillagerData().setProfession(prof));
        v.refreshBrain(level);
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobPos);
        v.getNavigation().moveTo(workstationPos.getX() + 0.5, workstationPos.getY(), workstationPos.getZ() + 0.5, 0.55D);
        return true;
    }

    /** Returns the villager's trade XP, or 0 if not a villager. */
    public static int villagerXp(Entity entity) {
        return entity instanceof Villager v ? v.getVillagerXp() : 0;
    }

    /** Finds all living Villagers within {@code radius} blocks of {@code center}. */
    public static List<Entity> findVillagersInRadius(ServerLevel level, BlockPos center, double radius) {
        AABB box = new AABB(center).inflate(radius);
        return new ArrayList<>(level.getEntitiesOfClass(Villager.class, box, Entity::isAlive));
    }

    /** Returns the client-localized display name for a registered item ID, falling back to {@code itemId}. */
    public static String localizedItemName(String itemId) {
        ItemStack st = stack(itemId, 1);
        return st.isEmpty() ? (itemId == null ? "" : itemId) : st.getHoverName().getString();
    }

    /** Returns {@code true} if it is currently daytime in {@code level}. */
    public static boolean isDaytime(ServerLevel level) {
        return level != null && level.isDay();
    }

    /** Returns the Overworld calendar {@code dayTime} tick (advances when players sleep). */
    public static long dayTime(ServerLevel level) {
        if (level == null) {
            return 0L;
        }
        ServerLevel overworld = level.getServer() != null && level.getServer().overworld() != null
                ? level.getServer().overworld()
                : level;
        return Math.max(0L, overworld.getDayTime());
    }

    /** Sets or clears the trading player on a Villager entity so it halts and faces the player. */
    public static void setVillagerTradingPlayer(Entity entity, Player player) {
        if (entity instanceof Villager v) {
            v.setTradingPlayer(player);
        }
    }

    /** Holds a Mayor Villager still and facing {@code player} while negotiation UI is open. */
    public static void holdMayorForNegotiation(Entity entity, ServerPlayer player) {
        if (!(entity instanceof Villager v) || !v.isAlive() || player == null) {
            return;
        }
        v.setTradingPlayer(player);
        v.getNavigation().stop();
        v.setDeltaMovement(0.0, Math.min(0.0, v.getDeltaMovement().y), 0.0);
        v.getLookControl().setLookAt(player, 30.0F, 30.0F);
        double dx = player.getX() - v.getX();
        double dz = player.getZ() - v.getZ();
        if (dx * dx + dz * dz > 1.0E-4) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            v.setYRot(yaw);
            v.setYHeadRot(yaw);
            v.yBodyRot = yaw;
        }
    }

    /**
     * Anchors a Mayor Villager's Brain {@code MEETING_POINT} to the village Bell/Hub, smoothly
     * walks them back if they drift > 14 blocks away during daytime, and faces nearby players.
     */
    public static void anchorMayorToPlaza(ServerLevel level, Entity entity, BlockPos hubPos) {
        if (!(entity instanceof Villager v) || !v.isAlive() || level == null || hubPos == null) {
            return;
        }
        if (v.isSleeping() || v.hurtTime > 0 || v.getLastHurtByMob() != null) {
            return;
        }
        v.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), hubPos));
        double dx = v.getX() - (hubPos.getX() + 0.5);
        double dz = v.getZ() - (hubPos.getZ() + 0.5);
        double distSq = dx * dx + dz * dz;
        if (isDaytime(level) && distSq > 14.0 * 14.0) {
            v.getNavigation().moveTo(hubPos.getX() + 0.5, hubPos.getY(), hubPos.getZ() + 0.5, 0.55D);
        } else if (distSq <= 10.0 * 10.0) {
            Player near = level.getNearestPlayer(v, 5.0);
            if (near != null) {
                v.getNavigation().stop();
                v.getLookControl().setLookAt(near, 30.0F, 30.0F);
            }
        }
    }

    /** Spawns a new adult Villager at {@code pos}. */
    public static void spawnVillager(ServerLevel level, BlockPos pos) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager != null) {
            villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(villager);
        }
    }

    /** Spawns a livestock animal matching {@code entityIdHint} (defaulting to Cow) at {@code pos}. */
    public static void spawnAnimal(ServerLevel level, BlockPos pos, String entityIdHint) {
        EntityType<?> type = EntityType.COW;
        if (entityIdHint != null && !entityIdHint.isBlank()) {
            ResourceLocation rl = ResourceLocation.tryParse(entityIdHint);
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            }
        }
        Entity entity = type.create(level);
        if (entity != null) {
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(entity);
        }
    }

    /** Spawns an Iron Golem near {@code center} if fewer than {@code desiredCount} exist within {@code radius}. */
    public static void spawnIronGolemIfNeeded(ServerLevel level, BlockPos center, double radius, int desiredCount) {
        if (desiredCount <= 0) {
            return;
        }
        AABB box = new AABB(center).inflate(radius);
        int current = level.getEntitiesOfClass(IronGolem.class, box, Entity::isAlive).size();
        if (current >= desiredCount) {
            return;
        }
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem != null) {
            golem.setPlayerCreated(false);
            golem.moveTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 0.0F, 0.0F);
            level.addFreshEntity(golem);
        }
    }

    // --- Enchanted Books & SlotStack Serialization ---

    /**
     * Creates an {@link Items#ENCHANTED_BOOK} stack populated from {@code enchantSpec}
     * (e.g. {@code "minecraft:mending:1"} or {@code "minecraft:efficiency:5"}).
     */
    public static ItemStack createEnchantedBook(HolderLookup.Provider registries, String enchantSpec) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        if (registries == null || enchantSpec == null || enchantSpec.isBlank()) {
            return book;
        }
        String trimmed = enchantSpec.trim();
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon <= 0) {
            return book;
        }
        String enchId = trimmed.substring(0, lastColon);
        int lvl = 1;
        try {
            lvl = Math.max(1, Integer.parseInt(trimmed.substring(lastColon + 1)));
        } catch (NumberFormatException ignored) {
            enchId = trimmed;
        }
        ResourceLocation rl = ResourceLocation.tryParse(enchId);
        if (rl == null) {
            return book;
        }
        try {
            var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            Optional<Holder.Reference<Enchantment>> holder = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, rl));
            if (holder.isPresent()) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(holder.get(), lvl);
                book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            }
        } catch (Exception ex) {
            LOGGER.debug("Could not apply enchantment {} to book: {}", enchantSpec, ex.getMessage());
        }
        return book;
    }

    /** Converts a live Minecraft {@link ItemStack} into a pure-Java {@link SlotStack}. */
    public static SlotStack toSlotStack(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String id = itemId(stack);
        if (id.isEmpty()) {
            return null;
        }
        String nbtJson = "";
        if (registries != null && !stack.getComponentsPatch().isEmpty()) {
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            nbtJson = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1))
                    .result()
                    .map(Object::toString)
                    .orElse("");
        }
        return new SlotStack(id, stack.getCount(), nbtJson);
    }

    /** Reconstructs a live Minecraft {@link ItemStack} from a {@link SlotStack}. */
    public static ItemStack fromSlotStack(SlotStack slotStack, HolderLookup.Provider registries) {
        if (slotStack == null || slotStack.count() <= 0) {
            return ItemStack.EMPTY;
        }
        String meta = slotStack.nbtJson();
        if (meta != null && !meta.isEmpty()) {
            if (meta.startsWith("enchant:")) {
                ItemStack book = createEnchantedBook(registries, meta.substring("enchant:".length()));
                book.setCount(slotStack.count());
                return book;
            }
            if (registries != null && meta.startsWith("{")) {
                try {
                    CompoundTag parsed = TagParser.parseTag(meta);
                    var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
                    Optional<ItemStack> decoded = ItemStack.CODEC.parse(ops, parsed).result();
                    if (decoded.isPresent() && !decoded.get().isEmpty()) {
                        ItemStack restored = decoded.get();
                        restored.setCount(slotStack.count());
                        return restored;
                    }
                } catch (Exception ignored) {
                    // Fall back to plain item stack below
                }
            }
        }
        return stack(slotStack.itemId(), slotStack.count());
    }
}
