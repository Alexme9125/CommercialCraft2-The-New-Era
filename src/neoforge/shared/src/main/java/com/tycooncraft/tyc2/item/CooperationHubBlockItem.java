package com.tycooncraft.tyc2.item;

import com.tycooncraft.tyc2.platform.TyC2Nbt;
import com.tycooncraft.tyc2.platform.TyC2TooltipBlockItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem for {@code tyc2:cooperation_hub}, carrying an optional bound village UUID & name
 * when packed for authorized relocation via the Hub Settings tab.
 */
public final class CooperationHubBlockItem extends TyC2TooltipBlockItem {

    private static final String KEY_VILLAGE_ID = "bound_village_id";
    private static final String KEY_VILLAGE_NAME = "bound_village_name";

    public CooperationHubBlockItem(Block block, Properties properties) {
        super(
                block,
                properties.stacksTo(1),
                List.of(
                        "tooltip.mnera.cooperation_hub.line1",
                        "tooltip.mnera.cooperation_hub.line2"
                )
        );
    }

    /** Writes bound village metadata onto a packed Hub item stack for relocation. */
    public static void setBoundVillage(ItemStack stack, UUID villageId, String villageName) {
        TyC2Nbt.updateStackTag(stack, tag -> {
            if (villageId != null) {
                tag.putString(KEY_VILLAGE_ID, villageId.toString());
            } else {
                tag.remove(KEY_VILLAGE_ID);
            }
            if (villageName != null && !villageName.isBlank()) {
                tag.putString(KEY_VILLAGE_NAME, villageName);
            } else {
                tag.remove(KEY_VILLAGE_NAME);
            }
        });
    }

    /** Reads the bound village UUID from a relocated Hub item stack, if any. */
    public static Optional<UUID> getBoundVillageId(ItemStack stack) {
        CompoundTag tag = TyC2Nbt.copyStackTag(stack);
        String raw = TyC2Nbt.getString(tag, KEY_VILLAGE_ID, "");
        if (raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** Reads the bound village display name cached on the Hub item stack. */
    public static String getBoundVillageName(ItemStack stack) {
        return TyC2Nbt.getString(TyC2Nbt.copyStackTag(stack), KEY_VILLAGE_NAME, "");
    }

    @Override
    protected void addDynamicTooltips(ItemStack stack, Consumer<Component> adder) {
        Optional<UUID> bound = getBoundVillageId(stack);
        if (bound.isPresent()) {
            String name = getBoundVillageName(stack);
            String label = name.isBlank() ? bound.get().toString().substring(0, 8) : name;
            adder.accept(Component.translatable("tooltip.mnera.cooperation_hub.bound", label)
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
