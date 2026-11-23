package com.tycooncraft.tyc2.item;

import com.tycooncraft.tyc2.blockentity.CooperationHubBlockEntity;
import com.tycooncraft.tyc2.core.TriModePowerMath;
import com.tycooncraft.tyc2.menu.CommTerminalMenu;
import com.tycooncraft.tyc2.platform.TyC2Native;
import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.village.VillageRecord;
import com.tycooncraft.tyc2.world.TyC2DataLoader;
import com.tycooncraft.tyc2.world.TyC2WorldManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Commerce Communication Terminal ({@code tyc2:comm_terminal}):
 * rechargeable electric handheld (20,000 EU / 80,000 FE) that binds up to 16 villages
 * and provides remote banking, trade, CPI analytics, and upgrade/expansion control across
 * unloaded chunks.
 */
public final class CommTerminalItem extends CommTerminalItemBase {

    private static final String KEY_ENERGY_EU = "energy_eu";
    private static final String KEY_BOUND_LIST = "bound_villages";
    private static final String KEY_UUID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_SELECTED = "selected_village";

    public CommTerminalItem(Properties properties) {
        super(
                properties.stacksTo(1),
                List.of(
                        "tooltip.tyc2.comm_terminal.line1",
                        "tooltip.tyc2.comm_terminal.line2"
                )
        );
    }

    /** Returns stored energy in canonical EU ({@code 0..20000}). */
    public static int getEnergyEu(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CompoundTag tag = TyC2Nbt.copyStackTag(stack);
        return Mth.clamp(TyC2Nbt.getInt(tag, KEY_ENERGY_EU, 0), 0, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
    }

    /** Writes stored energy in canonical EU (clamped to {@code 0..20000}). */
    public static void setEnergyEu(ItemStack stack, int energyEu) {
        int clamped = Mth.clamp(energyEu, 0, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
        TyC2Nbt.updateStackTag(stack, tag -> tag.putInt(KEY_ENERGY_EU, clamped));
    }

    /** Charges this terminal by up to {@code offeredEu} EU, returning the accepted EU amount. */
    public static int chargeEu(ItemStack stack, int offeredEu) {
        if (stack == null || stack.isEmpty() || offeredEu <= 0) {
            return 0;
        }
        int current = getEnergyEu(stack);
        int space = Math.max(0, TriModePowerMath.TERMINAL_MAX_CAPACITY_EU - current);
        int accepted = Math.min(space, Math.min(offeredEu, TriModePowerMath.TERMINAL_CHARGE_RATE_EU_PER_TICK));
        if (accepted > 0) {
            setEnergyEu(stack, current + accepted);
        }
        return accepted;
    }

    /** Consumes {@code action}'s EU cost if sufficient energy is present (free in Creative mode). */
    public static boolean consumeActionEu(ItemStack stack, Player player, TriModePowerMath.TerminalAction action) {
        if (player != null && player.isCreative()) {
            return true;
        }
        int current = getEnergyEu(stack);
        long remaining = TriModePowerMath.consumeTerminalAction(current, action);
        if (remaining < 0L) {
            return false;
        }
        setEnergyEu(stack, (int) remaining);
        return true;
    }

    /** Returns an ordered map of {@code villageId -> cachedVillageName} bound to this terminal. */
    public static Map<UUID, String> getBoundVillages(ItemStack stack) {
        Map<UUID, String> map = new LinkedHashMap<>();
        CompoundTag tag = TyC2Nbt.copyStackTag(stack);
        for (CompoundTag entry : TyC2Nbt.getCompoundList(tag, KEY_BOUND_LIST)) {
            String rawId = TyC2Nbt.getString(entry, KEY_UUID, "");
            String name = TyC2Nbt.getString(entry, KEY_NAME, "");
            try {
                if (!rawId.isBlank()) {
                    map.put(UUID.fromString(rawId), name);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip invalid entry
            }
        }
        return map;
    }

    /** Returns ordered list of bound village UUIDs. */
    public static List<UUID> getBoundVillageIds(ItemStack stack) {
        return new ArrayList<>(getBoundVillages(stack).keySet());
    }

    /** Saves the bound village map back onto the item stack. */
    private static void saveBoundVillages(ItemStack stack, Map<UUID, String> villages) {
        TyC2Nbt.updateStackTag(stack, tag -> {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, String> e : villages.entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putString(KEY_UUID, e.getKey().toString());
                item.putString(KEY_NAME, e.getValue() == null ? "" : e.getValue());
                list.add(item);
            }
            tag.put(KEY_BOUND_LIST, list);
        });
    }

    /**
     * Binds or unbinds {@code villageId} on this terminal.
     *
     * @return {@code 1} if bound, {@code -1} if unbound, {@code 0} if max limit reached
     */
    public static int toggleBindVillage(ItemStack stack, UUID villageId, String villageName) {
        if (stack == null || stack.isEmpty() || villageId == null) {
            return 0;
        }
        Map<UUID, String> bound = getBoundVillages(stack);
        if (bound.containsKey(villageId)) {
            bound.remove(villageId);
            saveBoundVillages(stack, bound);
            return -1;
        }
        int max = TyC2DataLoader.config().terminalMaxBoundVillages();
        if (bound.size() >= max) {
            return 0;
        }
        bound.put(villageId, villageName == null ? "" : villageName);
        saveBoundVillages(stack, bound);
        setSelectedVillage(stack, villageId);
        return 1;
    }

    /** Explicitly removes {@code villageId} from this terminal's bound list. */
    public static boolean unbindVillage(ItemStack stack, UUID villageId) {
        Map<UUID, String> bound = getBoundVillages(stack);
        if (bound.remove(villageId) != null) {
            saveBoundVillages(stack, bound);
            return true;
        }
        return false;
    }

    /** Updates cached village display names on this terminal. */
    public static void refreshBoundVillageName(ItemStack stack, UUID villageId, String villageName) {
        Map<UUID, String> bound = getBoundVillages(stack);
        if (bound.containsKey(villageId)) {
            bound.put(villageId, villageName == null ? "" : villageName);
            saveBoundVillages(stack, bound);
        }
    }

    /** Returns the currently selected village UUID, or the first bound village if unset. */
    public static UUID getSelectedVillage(ItemStack stack) {
        CompoundTag tag = TyC2Nbt.copyStackTag(stack);
        String raw = TyC2Nbt.getString(tag, KEY_SELECTED, "");
        Map<UUID, String> bound = getBoundVillages(stack);
        if (!raw.isBlank()) {
            try {
                UUID id = UUID.fromString(raw);
                if (bound.containsKey(id)) {
                    return id;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through
            }
        }
        return bound.isEmpty() ? null : bound.keySet().iterator().next();
    }

    /** Stores the active selected village UUID on the terminal stack. */
    public static void setSelectedVillage(ItemStack stack, UUID villageId) {
        TyC2Nbt.updateStackTag(stack, tag -> {
            if (villageId == null) {
                tag.remove(KEY_SELECTED);
            } else {
                tag.putString(KEY_SELECTED, villageId.toString());
            }
        });
    }

    /**
     * Intercepts Sneak+Right-Click on a {@link CooperationHubBlockEntity} before block/item
     * fallback dispatch so binding works even with items in both hands and never falls through
     * to opening an unbound terminal or double-toggling the hub.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && player.isShiftKeyDown()
                && level.getBlockEntity(context.getClickedPos()) instanceof CooperationHubBlockEntity hub) {
            if (TyC2Native.isClientSide(level)) {
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel) {
                bindTerminalToHub(serverLevel, player, stack, hub);
            }
            return InteractionResult.CONSUME;
        }
        return super.onItemUseFirst(stack, context);
    }

    /** Toggles binding between {@code stack} and {@code hub}'s village, sending localized feedback. */
    public static void bindTerminalToHub(
            ServerLevel serverLevel,
            Player player,
            ItemStack stack,
            CooperationHubBlockEntity hub
    ) {
        VillageRecord village = hub.resolveVillage(serverLevel);
        if (village == null) {
            return;
        }
        int result = toggleBindVillage(stack, village.getVillageId(), village.getVillageName());
        if (result > 0) {
            int count = getBoundVillages(stack).size();
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.tyc2.terminal.bound", village.getVillageName(), count)
                            .withStyle(ChatFormatting.GREEN)
            );
        } else if (result < 0) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.tyc2.terminal.unbound", village.getVillageName())
                            .withStyle(ChatFormatting.YELLOW)
            );
        } else {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.tyc2.terminal.max_bound")
                            .withStyle(ChatFormatting.RED)
            );
        }
    }

    @Override
    protected boolean handleUseTerminal(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (TyC2Native.isClientSide(level)) {
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!consumeActionEu(stack, player, TriModePowerMath.TerminalAction.OPEN_REMOTE_UI)) {
            TyC2Native.sendActionBar(
                    player,
                    Component.translatable("status.tyc2.terminal.low_power").withStyle(ChatFormatting.RED)
            );
            return true;
        }
        boolean offhand = hand == InteractionHand.OFF_HAND;
        serverPlayer.openMenu(
                new SimpleMenuProvider(
                        (syncId, inv, p) -> new CommTerminalMenu(syncId, inv, offhand),
                        Component.translatable("container.tyc2.comm_terminal")
                ),
                buf -> buf.writeBoolean(offhand)
        );
        TyC2WorldManager.sendTerminalSync(serverPlayer, serverLevel, stack);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int eu = getEnergyEu(stack);
        return Math.round(13.0F * eu / (float) TriModePowerMath.TERMINAL_MAX_CAPACITY_EU);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = getEnergyEu(stack) / (float) TriModePowerMath.TERMINAL_MAX_CAPACITY_EU;
        if (ratio <= 0.15F) {
            return 0xE03B3B;
        }
        if (ratio <= 0.50F) {
            return 0xE6B422;
        }
        return 0x3BD16F;
    }

    @Override
    protected void addDynamicTooltips(ItemStack stack, Consumer<Component> adder) {
        int eu = getEnergyEu(stack);
        int maxEu = TriModePowerMath.TERMINAL_MAX_CAPACITY_EU;
        adder.accept(Component.translatable("tooltip.tyc2.comm_terminal.energy", eu, maxEu)
                .withStyle(eu > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        int boundCount = getBoundVillages(stack).size();
        adder.accept(Component.translatable("tooltip.tyc2.comm_terminal.bound", boundCount)
                .withStyle(ChatFormatting.YELLOW));
        if (eu < TriModePowerMath.TERMINAL_COST_OPEN_EU) {
            adder.accept(Component.translatable("tooltip.tyc2.comm_terminal.low_power")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
