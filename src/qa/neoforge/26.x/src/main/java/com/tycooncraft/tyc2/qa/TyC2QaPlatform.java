package com.tycooncraft.tyc2.qa;

import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge 26.x (26.1.2 &amp; 26.2) version-specific QA helpers for TyC2 live dedicated-server tests.
 */
public final class TyC2QaPlatform {

    private TyC2QaPlatform() {
    }

    public static Set<String> allRecipeIds(MinecraftServer server) {
        Set<String> ids = new TreeSet<>();
        server.getRecipeManager().getRecipes().forEach(holder -> ids.add(holder.id().identifier().toString()));
        return ids;
    }

    public static FakePlayer createFakePlayer(ServerLevel level, UUID uuid, String name) {
        FakePlayer fp = FakePlayerFactory.get(level, new GameProfile(uuid, name));
        fp.setGameMode(GameType.SURVIVAL);
        return fp;
    }

    public static void teleportPlayer(
            net.minecraft.server.level.ServerPlayer player,
            ServerLevel level,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        player.stopRiding();
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
    }

    public static Entity spawnAdultVillager(ServerLevel level, double x, double y, double z) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace("villager"))
                .orElseThrow(() -> new AssertionError("Missing minecraft:villager"));
        Entity created = type.create(level, EntitySpawnReason.COMMAND);
        if (!(created instanceof Villager v)) {
            throw new AssertionError("Failed to create Villager on 26.x: " + created);
        }
        v.setAge(0);
        BuiltInRegistries.VILLAGER_PROFESSION.get(VillagerProfession.FARMER)
                .ifPresent(prof -> v.setVillagerData(v.getVillagerData().withProfession(prof)));
        v.snapTo(x, y, z, 0.0F, 0.0F);
        v.setNoAi(true);
        if (!level.addFreshEntity(v)) {
            throw new AssertionError("Failed to add Villager to level");
        }
        return v;
    }

    public static void killEntity(ServerLevel level, Entity entity) {
        entity.kill(level);
    }

    public static boolean fireVillagerInteract(ServerLevel level, FakePlayer player, Entity villager) {
        PlayerInteractEvent.EntityInteract event;
        try {
            var ctor4 = PlayerInteractEvent.EntityInteract.class.getConstructor(
                    net.minecraft.world.entity.player.Player.class,
                    InteractionHand.class,
                    Entity.class,
                    net.minecraft.world.phys.Vec3.class
            );
            event = ctor4.newInstance(player, InteractionHand.MAIN_HAND, villager, net.minecraft.world.phys.Vec3.ZERO);
        } catch (NoSuchMethodException e) {
            try {
                var ctor3 = PlayerInteractEvent.EntityInteract.class.getConstructor(
                        net.minecraft.world.entity.player.Player.class,
                        InteractionHand.class,
                        Entity.class
                );
                event = ctor3.newInstance(player, InteractionHand.MAIN_HAND, villager);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Could not instantiate EntityInteract", ex);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not instantiate EntityInteract", e);
        }
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    public static boolean fireSurvivalBreakBlock(ServerLevel level, FakePlayer player, BlockPos pos) {
        player.setGameMode(GameType.SURVIVAL);
        BlockState state = level.getBlockState(pos);
        BreakBlockEvent event = new BreakBlockEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    public static int executeCommand(MinecraftServer server, String commandNoSlash) throws Exception {
        return server.getCommands().getDispatcher().execute(commandNoSlash, server.createCommandSourceStack());
    }

    public static int injectFeIntoBlock(ServerLevel level, BlockPos pos, int feAmount) {
        EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.UP);
        if (energy == null) {
            return -1;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int accepted = energy.insert(feAmount, tx);
            tx.commit();
            return accepted;
        }
    }

    public static void verifyHubCapabilityIsolation(
            ServerLevel level,
            BlockPos hubPos,
            TyC2QaChecks checks
    ) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, hubPos, Direction.NORTH);
        checks.check("hub.cap.present", handler != null, "Item ResourceHandler present on Hub");
        checks.check("hub.cap.slot_count_1000", handler.size() == 1000, "slots=" + handler.size());

        // 1. Insert 8 cobblestone into Inbound slot 0
        int insertedIn;
        try (Transaction tx = Transaction.openRoot()) {
            insertedIn = handler.insert(0, ItemResource.of(Items.COBBLESTONE), 8, tx);
            tx.commit();
        }
        checks.check("hub.cap.inbound_insert_ok", insertedIn == 8, "inserted=" + insertedIn);

        // 2. Attempt external extract from Inbound slot 0 -> MUST BE 0 (zero loop-back!)
        int extractedIn;
        try (Transaction tx = Transaction.openRoot()) {
            extractedIn = handler.extract(0, ItemResource.of(Items.COBBLESTONE), 8, tx);
            tx.commit();
        }
        checks.check("hub.cap.inbound_extract_blocked", extractedIn == 0,
                "extractedFromInbound=" + extractedIn);

        // 3. Attempt external insert into Outbound slot 500 -> MUST BE 0!
        int insertedOut;
        try (Transaction tx = Transaction.openRoot()) {
            insertedOut = handler.insert(500, ItemResource.of(Items.WHEAT), 4, tx);
            tx.commit();
        }
        checks.check("hub.cap.outbound_insert_blocked", insertedOut == 0,
                "insertedIntoOutbound=" + insertedOut);
    }

    public static ItemStack extractFromHubOutboundSlot(ServerLevel level, BlockPos hubPos, int slot, int maxCount) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, hubPos, Direction.NORTH);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ItemResource res = handler.getResource(slot);
        if (res.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(slot, res, maxCount, tx);
            tx.commit();
            return extracted > 0 ? res.toStack(extracted) : ItemStack.EMPTY;
        }
    }

    public static ItemStack insertIntoHubInbound(ServerLevel level, BlockPos hubPos, ItemStack stack) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, hubPos, Direction.NORTH);
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        ItemResource res = ItemResource.of(stack);
        int rem = stack.getCount();
        try (Transaction tx = Transaction.openRoot()) {
            for (int s = 0; s < 500 && rem > 0; s++) {
                int accepted = handler.insert(s, res, rem, tx);
                rem -= accepted;
            }
            tx.commit();
        }
        return rem > 0 ? stack.copyWithCount(rem) : ItemStack.EMPTY;
    }
}
