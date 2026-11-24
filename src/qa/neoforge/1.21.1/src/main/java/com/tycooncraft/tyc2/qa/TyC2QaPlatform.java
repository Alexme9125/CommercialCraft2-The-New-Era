package com.tycooncraft.tyc2.qa;

import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * NeoForge 1.21.1 version-specific QA helpers for TyC2 live dedicated-server tests.
 */
public final class TyC2QaPlatform {

    private TyC2QaPlatform() {
    }

    public static Set<String> allRecipeIds(MinecraftServer server) {
        Set<String> ids = new TreeSet<>();
        server.getRecipeManager().getRecipes().forEach(holder -> ids.add(holder.id().toString()));
        return ids;
    }

    public static final class CapturingFakePlayer extends FakePlayer {
        private String lastActionBarKey = "";
        private String lastActionBarText = "";

        public CapturingFakePlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component chatComponent, boolean actionBar) {
            if (actionBar && chatComponent != null) {
                this.lastActionBarText = chatComponent.getString();
                this.lastActionBarKey = chatComponent.getContents() instanceof TranslatableContents tc
                        ? tc.getKey()
                        : chatComponent.getString();
            }
        }
    }

    public static FakePlayer createFakePlayer(ServerLevel level, UUID uuid, String name) {
        FakePlayer fp = new CapturingFakePlayer(level, new GameProfile(uuid, name));
        fp.setGameMode(GameType.SURVIVAL);
        return fp;
    }

    public static String lastActionBarKey(FakePlayer player) {
        return player instanceof CapturingFakePlayer c ? c.lastActionBarKey : "";
    }

    public static String lastActionBarText(FakePlayer player) {
        return player instanceof CapturingFakePlayer c ? c.lastActionBarText : "";
    }

    public static void clearActionBarKey(FakePlayer player) {
        if (player instanceof CapturingFakePlayer c) {
            c.lastActionBarKey = "";
            c.lastActionBarText = "";
        }
    }

    public static void teleportPlayer(
            net.minecraft.server.level.ServerPlayer player,
            ServerLevel level,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        player.stopRiding();
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    public static Entity spawnAdultVillager(ServerLevel level, double x, double y, double z) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            throw new AssertionError("Failed to create Villager on 1.21.1");
        }
        v.setAge(0);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
        v.moveTo(x, y, z, 0.0F, 0.0F);
        v.setNoAi(true);
        if (!level.addFreshEntity(v)) {
            throw new AssertionError("Failed to add Villager to level");
        }
        return v;
    }

    public static Entity spawnAdultNitwitVillager(ServerLevel level, double x, double y, double z, int xp) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            throw new AssertionError("Failed to create Nitwit Villager on 1.21.1");
        }
        v.setAge(0);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
        v.setVillagerXp(xp);
        v.moveTo(x, y, z, 0.0F, 0.0F);
        v.setNoAi(true);
        if (!level.addFreshEntity(v)) {
            throw new AssertionError("Failed to add Nitwit Villager to level");
        }
        return v;
    }

    public static Entity spawnUnemployedAdultVillager(ServerLevel level, double x, double y, double z) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            throw new AssertionError("Failed to create Unemployed Villager on 1.21.1");
        }
        v.setAge(0);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
        v.setVillagerXp(0);
        v.moveTo(x, y, z, 0.0F, 0.0F);
        v.setNoAi(true);
        if (!level.addFreshEntity(v)) {
            throw new AssertionError("Failed to add Unemployed Villager to level");
        }
        return v;
    }

    public static String translationKeyOf(Component component) {
        if (component == null) {
            return "";
        }
        return component.getContents() instanceof TranslatableContents tc ? tc.getKey() : component.getString();
    }

    public static Entity villagerTradingPlayer(Entity entity) {
        return entity instanceof Villager v ? v.getTradingPlayer() : null;
    }

    public static boolean villagerNavigationDone(Entity entity) {
        return entity instanceof Villager v && v.getNavigation().isDone();
    }

    public static boolean villagerHasNavPath(Entity entity) {
        return entity instanceof Villager v && !v.getNavigation().isDone();
    }

    public static BlockPos villagerMeetingPoint(Entity entity) {
        if (entity instanceof Villager v) {
            return v.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(GlobalPos::pos).orElse(null);
        }
        return null;
    }

    public static void setVillagerAiEnabled(Entity entity, boolean enabled) {
        if (entity instanceof Villager v) {
            v.setNoAi(!enabled);
        }
    }

    public static void moveEntityTo(Entity entity, double x, double y, double z, float yaw) {
        entity.moveTo(x, y, z, yaw, 0.0F);
    }

    public static void killEntity(ServerLevel level, Entity entity) {
        entity.kill();
    }

    public static boolean fireVillagerInteract(ServerLevel level, FakePlayer player, Entity villager) {
        PlayerInteractEvent.EntityInteract event = new PlayerInteractEvent.EntityInteract(
                player, InteractionHand.MAIN_HAND, villager
        );
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    public static boolean fireSurvivalBreakBlock(ServerLevel level, FakePlayer player, BlockPos pos) {
        player.setGameMode(GameType.SURVIVAL);
        BlockState state = level.getBlockState(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    public static int executeCommand(MinecraftServer server, String commandNoSlash) throws Exception {
        return server.getCommands().getDispatcher().execute(commandNoSlash, server.createCommandSourceStack());
    }

    public static int injectFeIntoBlock(ServerLevel level, BlockPos pos, int feAmount) {
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
        if (energy == null) {
            return -1;
        }
        return energy.receiveEnergy(feAmount, false);
    }

    /**
     * Verifies 1,000-slot Cooperation Hub external capability isolation:
     * - totalSlots == 1000
     * - Inbound (0..499): external INSERT succeeds, external EXTRACT blocked (zero loop-back!)
     * - Outbound (500..999): external INSERT blocked, external EXTRACT succeeds when populated
     */
    public static void verifyHubCapabilityIsolation(
            ServerLevel level,
            BlockPos hubPos,
            TyC2QaChecks checks
    ) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, hubPos, Direction.NORTH);
        checks.check("hub.cap.present", handler != null, "IItemHandler present on Hub");
        checks.check("hub.cap.slot_count_1000", handler.getSlots() == 1000, "slots=" + handler.getSlots());

        // 1. Insert 8 cobblestone into Inbound slot 0
        ItemStack remIn = handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 8), false);
        checks.check("hub.cap.inbound_insert_ok", remIn.isEmpty(), "rem=" + remIn);

        // 2. Attempt external extract from Inbound slot 0 -> MUST BE EMPTY (zero loop-back!)
        ItemStack pulledInbound = handler.extractItem(0, 8, false);
        checks.check("hub.cap.inbound_extract_blocked", pulledInbound.isEmpty(),
                "extractedFromInbound=" + pulledInbound);

        // 3. Attempt external insert into Outbound slot 500 -> MUST BE REJECTED!
        ItemStack remOut = handler.insertItem(500, new ItemStack(Items.WHEAT, 4), false);
        checks.check("hub.cap.outbound_insert_blocked", remOut.getCount() == 4,
                "rejectedOutboundInsert=" + remOut);
    }

    public static ItemStack extractFromHubOutboundSlot(ServerLevel level, BlockPos hubPos, int slot, int maxCount) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, hubPos, Direction.NORTH);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        return handler.extractItem(slot, maxCount, false);
    }

    public static ItemStack insertIntoHubInbound(ServerLevel level, BlockPos hubPos, ItemStack stack) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, hubPos, Direction.NORTH);
        if (handler == null) {
            return stack;
        }
        ItemStack rem = stack.copy();
        for (int s = 0; s < 500 && !rem.isEmpty(); s++) {
            rem = handler.insertItem(s, rem, false);
        }
        return rem;
    }
}
