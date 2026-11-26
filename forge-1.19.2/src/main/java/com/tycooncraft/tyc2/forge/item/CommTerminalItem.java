package com.tycooncraft.tyc2.forge.item;

import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.forge.block.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.forge.data.TyC2RuntimeData;
import com.tycooncraft.tyc2.forge.gui.CommTerminalMenu;
import com.tycooncraft.tyc2.forge.world.TycoonForgeSavedData;
import com.tycooncraft.tyc2.forge.world.TycoonForgeWorldManager;
import com.tycooncraft.tyc2.village.VillageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.network.NetworkHooks;

/**
 * Rechargeable Commerce Communication Terminal ({@code tyc2:comm_terminal}):
 * stores up to 20,000 EU (80,000 FE), binds up to 16 Cooperation Hub villages,
 * exposes {@link ForgeCapabilities#ENERGY} for FE chargers, charges in ATM/Mint/IC2 chargers,
 * and opens remote trade/banking/macro-economy management even across unloaded chunks.
 */
public class CommTerminalItem extends Item {

    public static final String TAG_ENERGY_EU = "tyc2_energy_eu";
    public static final String TAG_BOUND_VILLAGES = "tyc2_bound_villages";
    public static final String TAG_ACTIVE_INDEX = "tyc2_active_village_idx";

    public CommTerminalItem(Properties properties) {
        super(properties);
    }

    /** Returns stored energy in EU ({@code 0..20000}). */
    public static int getStoredEu(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(TAG_ENERGY_EU), 0, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
    }

    /** Sets stored energy in EU clamped to {@code [0, 20000]}. */
    public static void setStoredEu(ItemStack stack, int eu) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = Mth.clamp(eu, 0, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        stack.getOrCreateTag().putInt(TAG_ENERGY_EU, clamped);
    }

    /**
     * Charges the terminal by up to {@code availableEu} respecting the 128 EU/t rate limit.
     *
     * @return actual EU accepted
     */
    public static double chargeEu(ItemStack stack, double availableEu, int tier) {
        if (stack == null || stack.isEmpty() || stack.getCount() != 1 || availableEu <= 0.0 || tier < 1) {
            return 0.0;
        }
        int stored = getStoredEu(stack);
        int space = TriModePowerMath.TERMINAL_MAX_CAPACITY_EU - stored;
        if (space <= 0) {
            return 0.0;
        }
        int maxTransfer = Math.min(space, TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK);
        int accepted = (int) Math.min(maxTransfer, Math.floor(availableEu));
        if (accepted <= 0 && availableEu >= 1.0) {
            accepted = 1;
        }
        if (accepted > 0) {
            setStoredEu(stack, stored + accepted);
        }
        return accepted;
    }

    /** Attempts to deduct the EU cost of {@code action}, returning {@code true} on success. */
    public static boolean consumeAction(ItemStack stack, TriModePowerMath.TerminalAction action) {
        int stored = getStoredEu(stack);
        long next = TriModePowerMath.consumeTerminalAction(stored, action);
        if (next < 0L) {
            return false;
        }
        setStoredEu(stack, (int) next);
        return true;
    }

    /** Returns the ordered list of bound village UUIDs stored on {@code stack}. */
    public static List<UUID> getBoundVillages(ItemStack stack) {
        List<UUID> list = new ArrayList<>();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return list;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_VILLAGES, Tag.TAG_LIST)) {
            return list;
        }
        ListTag nbtList = tag.getList(TAG_BOUND_VILLAGES, Tag.TAG_STRING);
        for (int i = 0; i < nbtList.size(); i++) {
            try {
                list.add(UUID.fromString(nbtList.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entry
            }
        }
        return list;
    }

    /** Saves the bound village UUID list onto {@code stack}. */
    public static void setBoundVillages(ItemStack stack, List<UUID> villages) {
        ListTag nbtList = new ListTag();
        int max = TyC2RuntimeData.config().terminalMaxBoundVillages();
        for (int i = 0; i < villages.size() && i < max; i++) {
            nbtList.add(StringTag.valueOf(villages.get(i).toString()));
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_BOUND_VILLAGES, nbtList);
        int active = tag.getInt(TAG_ACTIVE_INDEX);
        if (active >= nbtList.size()) {
            tag.putInt(TAG_ACTIVE_INDEX, Math.max(0, nbtList.size() - 1));
        }
    }

    /**
     * Toggles binding for {@code villageId}: binds if absent (up to max 16), unbinds if already bound.
     *
     * @return {@code 1} if bound, {@code -1} if unbound, {@code 0} if at max capacity
     */
    public static int toggleBindVillage(ItemStack stack, UUID villageId) {
        List<UUID> current = getBoundVillages(stack);
        if (current.contains(villageId)) {
            current.remove(villageId);
            setBoundVillages(stack, current);
            return -1;
        }
        int max = TyC2RuntimeData.config().terminalMaxBoundVillages();
        if (current.size() >= max) {
            return 0;
        }
        current.add(villageId);
        setBoundVillages(stack, current);
        stack.getOrCreateTag().putInt(TAG_ACTIVE_INDEX, current.size() - 1);
        return 1;
    }

    /** Removes {@code villageId} if bound to {@code stack}. */
    public static boolean unbindVillage(ItemStack stack, UUID villageId) {
        List<UUID> current = getBoundVillages(stack);
        if (current.remove(villageId)) {
            setBoundVillages(stack, current);
            return true;
        }
        return false;
    }

    public static int getActiveIndex(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(TAG_ACTIVE_INDEX));
    }

    public static void setActiveIndex(ItemStack stack, int idx) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putInt(TAG_ACTIVE_INDEX, Math.max(0, idx));
    }

    /**
     * Handles Sneak+Right-Click on a {@link CooperationHubBlockEntity} BEFORE Forge's
     * {@code ServerPlayerGameMode.useItemOn} sneak-bypass check skips {@code BlockState.use}.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && player.isShiftKeyDown()
                && level.getBlockEntity(context.getClickedPos()) instanceof CooperationHubBlockEntity hub) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                handleHubBind(serverPlayer, stack, hub);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Toggles binding {@code stack} to {@code hub}'s village and notifies {@code serverPlayer}. */
    public static void handleHubBind(ServerPlayer serverPlayer, ItemStack stack, CooperationHubBlockEntity hub) {
        UUID vid = hub.resolveVillageId();
        if (vid == null) {
            return;
        }
        TycoonForgeSavedData data = TycoonForgeSavedData.get(serverPlayer.getServer());
        Optional<VillageRecord> vOpt = data.getVillage(vid);
        if (vOpt.isPresent() && !TycoonForgeWorldManager.canAccessVillage(serverPlayer, data, vOpt.get())) {
            serverPlayer.displayClientMessage(
                    Component.translatable("status.tyc2.hub.not_owner").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }
        String vName = vOpt.map(VillageRecord::getVillageName).orElse(vid.toString());
        int res = toggleBindVillage(stack, vid);
        if (res > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable(
                            "status.tyc2.terminal.bound",
                            vName,
                            getBoundVillages(stack).size()
                    ).withStyle(ChatFormatting.GREEN),
                    true
            );
        } else if (res < 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("status.tyc2.terminal.unbound", vName)
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
        } else {
            serverPlayer.displayClientMessage(
                    Component.translatable("status.tyc2.terminal.max_bound")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
        serverPlayer.inventoryMenu.broadcastChanges();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!consumeAction(stack, TriModePowerMath.TerminalAction.OPEN_REMOTE_UI)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("status.tyc2.terminal.low_power").withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }
            int handSlot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : 40;
            NetworkHooks.openScreen(
                    serverPlayer,
                    new SimpleMenuProvider(
                            (id, inv, p) -> new CommTerminalMenu(id, inv, handSlot),
                            Component.translatable("container.tyc2.comm_terminal")
                    ),
                    buf -> buf.writeVarInt(handSlot)
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getStoredEu(stack) / (float) TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = getStoredEu(stack) / (float) TriModePowerMath.TERMINAL_MAX_CAPACITY_EU;
        if (ratio <= 0.05F) {
            return 0xFF4444;
        }
        return Mth.hsvToRgb(Math.max(0.0F, ratio) * 0.45F, 0.9F, 0.95F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int stored = getStoredEu(stack);
        tooltip.add(Component.translatable(
                "tooltip.tyc2.comm_terminal.energy",
                stored,
                TriModePowerMath.TERMINAL_MAX_CAPACITY_EU
        ).withStyle(stored > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        tooltip.add(Component.translatable(
                "tooltip.tyc2.comm_terminal.bound",
                getBoundVillages(stack).size()
        ).withStyle(ChatFormatting.GOLD));
        if (stored <= 0) {
            tooltip.add(Component.translatable("tooltip.tyc2.comm_terminal.low_power")
                    .withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.tyc2.comm_terminal.line1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tyc2.comm_terminal.line2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    @Nullable
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new TerminalEnergyProvider(stack);
    }

    private static final class TerminalEnergyProvider implements ICapabilityProvider, IEnergyStorage {
        private final ItemStack stack;
        private final LazyOptional<IEnergyStorage> holder = LazyOptional.of(() -> this);

        TerminalEnergyProvider(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return ForgeCapabilities.ENERGY.orEmpty(cap, holder);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || stack.getCount() != 1) {
                return 0;
            }
            int storedEu = getStoredEu(stack);
            int spaceEu = TriModePowerMath.TERMINAL_MAX_CAPACITY_EU - storedEu;
            if (spaceEu <= 0) {
                return 0;
            }
            int maxEu = Math.min(spaceEu, TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK);
            int acceptEu = (int) Math.min(maxEu, TriModePowerMath.feToEuFloor(maxReceive));
            if (acceptEu <= 0) {
                return 0;
            }
            if (!simulate) {
                setStoredEu(stack, storedEu + acceptEu);
            }
            return acceptEu * TriModePowerMath.EU_TO_FE_RATIO;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return getStoredEu(stack) * TriModePowerMath.EU_TO_FE_RATIO;
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
    }
}
