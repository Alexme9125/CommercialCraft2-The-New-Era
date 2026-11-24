package com.tycooncraft.tyc2.forge.util;

import com.tycooncraft.tyc2.core.CurrencyDenomination;
import com.tycooncraft.tyc2.core.MintIngredient;
import com.tycooncraft.tyc2.village.BlockPos3i;
import com.tycooncraft.tyc2.village.SlotStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;

/**
 * Shared Forge 1.19.2 registry, tag, coordinate, NBT, currency, and block-state conversion helpers.
 */
public final class TyC2Ids {

    public static final String MOD_ID = "tyc2";

    private TyC2Ids() {
    }

    /** Creates a {@code tyc2:<path>} {@link ResourceLocation}. */
    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** Safely parses an arbitrary {@link ResourceLocation}, returning {@code null} on malformed input. */
    @Nullable
    public static ResourceLocation parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** Returns the canonical registry ID of {@code stack}, or {@code ""} if empty. */
    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return itemId(stack.getItem());
    }

    /** Returns the canonical registry ID of {@code item}, or {@code ""} if unregistered. */
    public static String itemId(@Nullable Item item) {
        if (item == null || item == Items.AIR) {
            return "";
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.toString();
    }

    /** Returns the canonical registry ID of {@code block}, or {@code ""} if unregistered. */
    public static String blockId(@Nullable Block block) {
        if (block == null) {
            return "";
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "" : key.toString();
    }

    /** Checks whether an item ID is registered in Forge's live item registry. */
    public static boolean itemExists(@Nullable String itemId) {
        ResourceLocation rl = parse(itemId);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item != null && item != Items.AIR;
    }

    /** Checks whether a block ID is registered in Forge's live block registry and is not air. */
    public static boolean blockExists(@Nullable String blockId) {
        ResourceLocation rl = parse(blockId);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) {
            return false;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        return block != null && block != Blocks.AIR;
    }

    /** Creates an icon {@link ItemStack} for a block or item ID, falling back to {@link Block#asItem()}. */
    public static ItemStack createBlockOrItemIcon(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = createStack(id, 1, "", null);
        if (!stack.isEmpty()) {
            return stack;
        }
        ResourceLocation rl = parse(id);
        if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != Blocks.AIR) {
                Item asItem = block.asItem();
                if (asItem != null && asItem != Items.AIR) {
                    return new ItemStack(asItem);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Resolves an {@link Item} by ID, returning {@code null} if missing or air. */
    @Nullable
    public static Item getItem(@Nullable String itemId) {
        ResourceLocation rl = parse(itemId);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == Items.AIR ? null : item;
    }

    /** Creates an {@link ItemStack} from an item ID, count, and optional enchantment spec or SNBT string. */
    public static ItemStack createStack(String itemId, int count, @Nullable String nbtJson, @Nullable String enchantSpec) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        if ("minecraft:enchanted_book".equalsIgnoreCase(itemId) && enchantSpec != null && !enchantSpec.isBlank()) {
            ItemStack book = createEnchantedBook(enchantSpec);
            if (!book.isEmpty()) {
                book.setCount(Math.min(count, book.getMaxStackSize()));
                return book;
            }
        }
        Item item = getItem(itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.min(count, item.getMaxStackSize()));
        if (nbtJson != null && !nbtJson.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtJson);
                if (!tag.isEmpty()) {
                    stack.setTag(tag);
                }
            } catch (Exception ignored) {
                // Ignore malformed optional SNBT
            }
        }
        return stack;
    }

    /** Builds an {@link EnchantedBookItem} stack from specs like {@code "minecraft:mending:1"}. */
    public static ItemStack createEnchantedBook(String enchantSpec) {
        if (enchantSpec == null || enchantSpec.isBlank()) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
        String trimmed = enchantSpec.trim();
        int lastColon = trimmed.lastIndexOf(':');
        String enchIdStr = trimmed;
        int level = 1;
        if (lastColon > 0) {
            String maybeNum = trimmed.substring(lastColon + 1);
            try {
                level = Math.max(1, Integer.parseInt(maybeNum));
                enchIdStr = trimmed.substring(0, lastColon);
            } catch (NumberFormatException ignored) {
                // Treat entire string as enchantment id at level 1
            }
        }
        ResourceLocation rl = parse(enchIdStr);
        if (rl != null && ForgeRegistries.ENCHANTMENTS.containsKey(rl)) {
            Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(rl);
            if (ench != null) {
                return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, level));
            }
        }
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    /** Returns the canonical SNBT string for an {@link ItemStack}'s tag, or {@code ""} if untagged. */
    public static String nbtJson(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return "";
        }
        CompoundTag tag = stack.getTag();
        return (tag == null || tag.isEmpty()) ? "" : tag.toString();
    }

    /** Converts a live Minecraft {@link ItemStack} to a pure-Java {@link SlotStack}. */
    @Nullable
    public static SlotStack toSlotStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String id = itemId(stack);
        if (id.isEmpty()) {
            return null;
        }
        return new SlotStack(id, stack.getCount(), nbtJson(stack));
    }

    /** Converts a pure-Java {@link SlotStack} back to a Minecraft {@link ItemStack}. */
    public static ItemStack fromSlotStack(@Nullable SlotStack slotStack) {
        if (slotStack == null || slotStack.count() <= 0) {
            return ItemStack.EMPTY;
        }
        return createStack(slotStack.itemId(), slotStack.count(), slotStack.nbtJson(), null);
    }

    /** Shared {@link MintIngredient.TagMatcher} querying Forge's live item tag manager for both {@code forge:} and {@code c:}. */
    public static final MintIngredient.TagMatcher ITEM_TAG_MATCHER = (itemId, tagId) -> {
        Item item = getItem(itemId);
        if (item == null) {
            return false;
        }
        return hasItemTag(item, tagId);
    };

    /** Checks whether {@code item} belongs to {@code tagId} (without leading {@code '#'}). */
    public static boolean hasItemTag(Item item, String tagId) {
        ResourceLocation rl = parse(tagId);
        if (rl == null) {
            return false;
        }
        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager == null) {
            return false;
        }
        TagKey<Item> key = tagManager.createTagKey(rl);
        return tagManager.getTag(key).contains(item);
    }

    /** Checks whether {@code stack} matches either an exact item ID or a {@code #tag} (supporting {@code forge:} & {@code c:}). */
    public static boolean matchesItemOrTag(ItemStack stack, String spec) {
        if (stack == null || stack.isEmpty() || spec == null || spec.isBlank()) {
            return false;
        }
        return matchesItemOrTag(itemId(stack), spec);
    }

    /** Checks whether {@code candidateItemId} matches either an exact item ID or a {@code #tag} ({@code forge:}/{@code c:}/{@code minecraft:}). */
    public static boolean matchesItemOrTag(@Nullable String candidateItemId, @Nullable String spec) {
        if (candidateItemId == null || candidateItemId.isBlank() || spec == null || spec.isBlank()) {
            return false;
        }
        String normItem = candidateItemId.trim().toLowerCase(Locale.ROOT);
        String trimmed = spec.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("#")) {
            Item item = getItem(normItem);
            if (item == null) {
                return false;
            }
            String rawTag = trimmed.substring(1);
            if (rawTag.contains(":")) {
                if (hasItemTag(item, rawTag)) {
                    return true;
                }
                int colon = rawTag.indexOf(':');
                String ns = rawTag.substring(0, colon);
                String path = rawTag.substring(colon + 1);
                if ("c".equals(ns)) {
                    return hasItemTag(item, "forge:" + path);
                }
                if ("forge".equals(ns)) {
                    return hasItemTag(item, "c:" + path);
                }
                return false;
            }
            return hasItemTag(item, "forge:" + rawTag)
                    || hasItemTag(item, "c:" + rawTag)
                    || hasItemTag(item, "minecraft:" + rawTag);
        }
        return normItem.equals(trimmed);
    }

    /** Resolves the localized display name of an item ID from Forge's live item registry. */
    public static String localizedItemName(@Nullable String itemId, @Nullable String enchantSpec) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        ItemStack stack = createStack(itemId, 1, "", enchantSpec);
        return stack.isEmpty() ? itemId : stack.getHoverName().getString();
    }

    /** Converts {@link BlockPos} to pure-Java {@link BlockPos3i}. */
    public static BlockPos3i toCore(BlockPos pos) {
        return new BlockPos3i(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Converts pure-Java {@link BlockPos3i} to {@link BlockPos}. */
    public static BlockPos toMc(BlockPos3i pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    /** Returns the face value in IC of all legal-tender coins in {@code stack}, or {@code 0} if not a coin. */
    public static long coinStackValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        Optional<CurrencyDenomination> denom = CurrencyDenomination.valueOfItemId(itemId(stack));
        return denom.map(d -> d.value() * stack.getCount()).orElse(0L);
    }

    /** Counts total physical IC value in a player's inventory. */
    public static long countPlayerCash(Player player) {
        long total = 0L;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            total += coinStackValue(inv.getItem(i));
        }
        return total;
    }

    /**
     * Attempts to collect at least {@code requiredIc} from {@code cashContainer} (if provided) and
     * {@code player}'s inventory, automatically returning exact change if a higher denomination coin
     * had to be broken.
     *
     * @return {@code true} if payment succeeded
     */
    public static boolean consumePhysicalCash(
            Player player,
            @Nullable Container cashContainer,
            long requiredIc,
            boolean ic2Loaded
    ) {
        if (requiredIc <= 0L) {
            return true;
        }
        long available = 0L;
        if (cashContainer != null) {
            for (int i = 0; i < cashContainer.getContainerSize(); i++) {
                available += coinStackValue(cashContainer.getItem(i));
            }
        }
        available += countPlayerCash(player);
        if (available < requiredIc) {
            return false;
        }

        long collected = 0L;
        // Take from dedicated cash slot first
        if (cashContainer != null) {
            for (int i = 0; i < cashContainer.getContainerSize() && collected < requiredIc; i++) {
                ItemStack stack = cashContainer.getItem(i);
                Optional<CurrencyDenomination> denomOpt = CurrencyDenomination.valueOfItemId(itemId(stack));
                if (denomOpt.isEmpty()) {
                    continue;
                }
                long unit = denomOpt.get().value();
                long need = requiredIc - collected;
                int take = (int) Math.min(stack.getCount(), (need + unit - 1L) / unit);
                if (take > 0) {
                    stack.shrink(take);
                    if (stack.isEmpty()) {
                        cashContainer.setItem(i, ItemStack.EMPTY);
                    }
                    cashContainer.setChanged();
                    collected += unit * take;
                }
            }
        }

        // Take remaining from player inventory (lowest-to-highest pass, then break larger if needed)
        Inventory inv = player.getInventory();
        for (int pass = 0; pass < 2 && collected < requiredIc; pass++) {
            for (int i = 0; i < inv.getContainerSize() && collected < requiredIc; i++) {
                ItemStack stack = inv.getItem(i);
                Optional<CurrencyDenomination> denomOpt = CurrencyDenomination.valueOfItemId(itemId(stack));
                if (denomOpt.isEmpty()) {
                    continue;
                }
                long unit = denomOpt.get().value();
                long need = requiredIc - collected;
                if (pass == 0 && unit > need) {
                    continue;
                }
                int take = (int) Math.min(stack.getCount(), pass == 0 ? (need / unit) : ((need + unit - 1L) / unit));
                if (take > 0) {
                    stack.shrink(take);
                    if (stack.isEmpty()) {
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                    collected += unit * take;
                }
            }
        }

        long change = collected - requiredIc;
        if (change > 0L) {
            giveCoinsToPlayer(player, change, ic2Loaded);
        }
        return true;
    }

    /** Creates canonical coin {@link ItemStack}s representing {@code amountIc} using optimal greedy decomposition. */
    public static List<ItemStack> createCoinStacks(long amountIc, boolean ic2Loaded) {
        List<ItemStack> result = new ArrayList<>();
        if (amountIc <= 0L) {
            return result;
        }
        Map<CurrencyDenomination, Integer> decomposed = CurrencyDenomination.decompose(amountIc);
        for (Map.Entry<CurrencyDenomination, Integer> entry : decomposed.entrySet()) {
            String cid = entry.getKey().canonicalItemId(ic2Loaded);
            Item item = getItem(cid);
            if (item == null) {
                item = getItem(entry.getKey().tyc2ItemId());
            }
            if (item == null) {
                continue;
            }
            int remaining = entry.getValue();
            int maxStack = item.getMaxStackSize();
            while (remaining > 0) {
                int split = Math.min(remaining, maxStack);
                result.add(new ItemStack(item, split));
                remaining -= split;
            }
        }
        return result;
    }

    /** Gives optimal-denomination coin stacks to {@code player}, dropping excess at feet. */
    public static void giveCoinsToPlayer(Player player, long amountIc, boolean ic2Loaded) {
        for (ItemStack coin : createCoinStacks(amountIc, ic2Loaded)) {
            if (!player.getInventory().add(coin)) {
                player.drop(coin, false);
            }
        }
    }

    /**
     * Parses a blueprint block-state spec such as {@code "minecraft:oak_stairs[facing=north,half=bottom]"}
     * into a live {@link BlockState}. Falls back to {@link Blocks#AIR} if the block ID is unknown.
     */
    public static BlockState parseBlockState(String spec) {
        if (spec == null || spec.isBlank()) {
            return Blocks.AIR.defaultBlockState();
        }
        String trimmed = spec.trim();
        int bracket = trimmed.indexOf('[');
        String blockIdStr = bracket >= 0 ? trimmed.substring(0, bracket).trim() : trimmed;
        ResourceLocation rl = parse(blockIdStr);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (bracket >= 0 && trimmed.endsWith("]")) {
            String propsBody = trimmed.substring(bracket + 1, trimmed.length() - 1);
            StateDefinition<Block, BlockState> def = block.getStateDefinition();
            for (String pair : propsBody.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0 || eq >= pair.length() - 1) {
                    continue;
                }
                String propName = pair.substring(0, eq).trim();
                String propVal = pair.substring(eq + 1).trim();
                Property<?> prop = def.getProperty(propName);
                if (prop != null) {
                    state = applyProperty(state, prop, propVal);
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String raw) {
        Optional<T> parsed = prop.getValue(raw);
        return parsed.map(v -> state.setValue(prop, v)).orElse(state);
    }
}
