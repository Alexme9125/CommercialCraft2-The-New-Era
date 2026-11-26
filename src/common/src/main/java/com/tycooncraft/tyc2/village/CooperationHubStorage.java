package com.tycooncraft.tyc2.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pure-Java 1,000-slot dual-zone virtual warehouse backing the Village Cooperation Hub.
 *
 * <ul>
 *   <li><strong>Inbound / Delivery Zone (slots {@code 0..499})</strong>: external pipes, hoppers,
 *       IC2 pulling/ejector upgrades, and LC2 ports may ONLY insert here; internal trade logic
 *       consumes items here to satisfy Village Buy Orders and Expansion Material Orders.</li>
 *   <li><strong>Outbound / Purchase Zone (slots {@code 500..999})</strong>: internal trade logic
 *       deposits items purchased from Village Sell Orders here; external automation may ONLY
 *       extract from here, guaranteeing zero loop-back!</li>
 * </ul>
 */
public final class CooperationHubStorage {

    /** Total virtual inventory slots across both zones. */
    public static final int TOTAL_SLOTS = 1000;

    /** Inclusive first slot index of the Inbound (Delivery) zone. */
    public static final int INBOUND_START = 0;

    /** Number of slots in the Inbound (Delivery) zone. */
    public static final int INBOUND_SLOTS = 500;

    /** Exclusive upper slot index of the Inbound zone. */
    public static final int INBOUND_END_EXCLUSIVE = INBOUND_START + INBOUND_SLOTS;

    /** Inclusive first slot index of the Outbound (Purchase) zone. */
    public static final int OUTBOUND_START = 500;

    /** Number of slots in the Outbound (Purchase) zone. */
    public static final int OUTBOUND_SLOTS = 500;

    /** Exclusive upper slot index of the Outbound zone. */
    public static final int OUTBOUND_END_EXCLUSIVE = OUTBOUND_START + OUTBOUND_SLOTS;

    /** Default maximum stack size per slot. */
    public static final int DEFAULT_MAX_STACK_SIZE = 64;

    /** Default GUI page size (50 slots/page yields 10 pages per zone). */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * Dual-zone partition identifier for the 1,000-slot Cooperation Hub warehouse.
     */
    public enum Zone {
        /** Delivery zone ({@code 0..499}): external insert only, consumed by buy/expansion orders. */
        INBOUND(INBOUND_START, INBOUND_SLOTS),
        /** Purchase zone ({@code 500..999}): deposited by sell orders, external extract only. */
        OUTBOUND(OUTBOUND_START, OUTBOUND_SLOTS);

        private final int startSlot;
        private final int slotCount;

        Zone(int startSlot, int slotCount) {
            this.startSlot = startSlot;
            this.slotCount = slotCount;
        }

        /**
         * Returns the inclusive start slot index of this zone.
         *
         * @return start slot index
         */
        public int startSlot() {
            return startSlot;
        }

        /**
         * Returns the number of slots owned by this zone.
         *
         * @return slot count (500)
         */
        public int slotCount() {
            return slotCount;
        }

        /**
         * Returns the exclusive end slot index of this zone.
         *
         * @return exclusive end slot index
         */
        public int endSlotExclusive() {
            return startSlot + slotCount;
        }

        /**
         * Checks whether {@code slot} belongs to this zone.
         *
         * @param slot absolute slot index
         * @return {@code true} if inside this zone's bounds
         */
        public boolean containsSlot(int slot) {
            return slot >= startSlot && slot < endSlotExclusive();
        }

        /**
         * Resolves the {@link Zone} containing {@code slot}.
         *
         * @param slot absolute slot index in {@code 0..999}
         * @return owning zone
         */
        public static Zone forSlot(int slot) {
            if (INBOUND.containsSlot(slot)) {
                return INBOUND;
            }
            if (OUTBOUND.containsSlot(slot)) {
                return OUTBOUND;
            }
            throw new IndexOutOfBoundsException("Slot out of range 0..999: " + slot);
        }
    }

    /**
     * Sparse serialized entry representing a single occupied slot.
     *
     * @param slot absolute slot index in {@code 0..999}
     * @param stack non-null item stack stored at {@code slot}
     */
    public record SlotEntry(int slot, SlotStack stack) {
        /**
         * Validates slot index and non-null stack.
         */
        public SlotEntry {
            if (slot < 0 || slot >= TOTAL_SLOTS) {
                throw new IndexOutOfBoundsException("Slot out of range 0..999: " + slot);
            }
            Objects.requireNonNull(stack, "stack");
        }
    }

    /**
     * Immutable persistence DTO containing all non-empty slots in sorted slot-index order.
     *
     * @param entries unmodifiable sparse list of occupied slots
     */
    public record StorageSnapshot(List<SlotEntry> entries) {
        /**
         * Makes a defensive unmodifiable copy of entries.
         */
        public StorageSnapshot {
            Objects.requireNonNull(entries, "entries");
            entries = List.copyOf(entries);
        }
    }

    /**
     * Paginated view of a zone returned by {@link #getPage(Zone, int, int, String)}.
     *
     * @param zone queried zone
     * @param pageIndex clamped 0-based page index
     * @param pageSize requested page capacity
     * @param totalMatchingSlots total slots matching the query in this zone
     * @param totalPages total pages available (at least 1)
     * @param slotIndices absolute slot indices displayed on this page
     * @param nonEmptyEntries occupied slot entries present on this page
     */
    public record PageResult(
            Zone zone,
            int pageIndex,
            int pageSize,
            int totalMatchingSlots,
            int totalPages,
            List<Integer> slotIndices,
            List<SlotEntry> nonEmptyEntries) {
        /**
         * Defensively wraps lists as unmodifiable.
         */
        public PageResult {
            Objects.requireNonNull(zone, "zone");
            slotIndices = List.copyOf(Objects.requireNonNull(slotIndices, "slotIndices"));
            nonEmptyEntries = List.copyOf(Objects.requireNonNull(nonEmptyEntries, "nonEmptyEntries"));
        }
    }

    private final SlotStack[] slots = new SlotStack[TOTAL_SLOTS];
    private long revision = 0L;

    /**
     * Creates an empty 1,000-slot {@link CooperationHubStorage}.
     */
    public CooperationHubStorage() {
    }

    /**
     * Returns a monotonically increasing revision counter incremented on every actual storage
     * mutation. Platform container adapters use this to detect and synchronize live {@code ItemStack}
     * references returned to hoppers and automation mods.
     *
     * @return current storage revision
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Returns whether external automation is allowed to insert into {@code slot}.
     *
     * @param slot absolute slot index
     * @return {@code true} only for Inbound slots {@code 0..499}
     */
    public boolean canInsertExternalAtSlot(int slot) {
        return Zone.INBOUND.containsSlot(slot);
    }

    /**
     * Returns whether external automation is allowed to extract from {@code slot}.
     *
     * @param slot absolute slot index
     * @return {@code true} only for Outbound slots {@code 500..999}
     */
    public boolean canExtractExternalAtSlot(int slot) {
        return Zone.OUTBOUND.containsSlot(slot);
    }

    /**
     * Inserts items from external pipes, hoppers, or LC2 logistics strictly into Inbound slots
     * ({@code 0..499}), using default stack size 64.
     *
     * @param itemId namespaced item ID
     * @param count number of items to insert
     * @param nbtJson optional NBT payload (null normalized to empty string)
     * @return remainder count that did not fit ({@code 0} means all items were inserted)
     */
    public int insertExternal(String itemId, int count, String nbtJson) {
        return insertExternal(itemId, count, nbtJson, DEFAULT_MAX_STACK_SIZE, false);
    }

    /**
     * Inserts (or simulates inserting) items from external logistics strictly into Inbound slots
     * ({@code 0..499}). Never writes into Outbound slots ({@code 500..999}).
     *
     * @param itemId namespaced item ID
     * @param count number of items to insert
     * @param nbtJson optional NBT payload
     * @param maxStackSize maximum stack size per slot ({@code >= 1})
     * @param simulate if {@code true}, computes remainder without mutating storage
     * @return remainder count that did not fit
     */
    public int insertExternal(String itemId, int count, String nbtJson, int maxStackSize, boolean simulate) {
        return insertIntoZone(Zone.INBOUND, itemId, count, nbtJson, maxStackSize, simulate);
    }

    /**
     * Attempts an external slot-targeted insertion (for {@code IItemHandler.insertItem} adapters).
     * Rejects any slot outside Inbound {@code 0..499} by returning {@code count} unchanged.
     *
     * @param slot target slot index
     * @param itemId namespaced item ID
     * @param count number of items to insert
     * @param nbtJson optional NBT payload
     * @param maxStackSize maximum stack size per slot
     * @param simulate if {@code true}, performs no mutation
     * @return remainder count not inserted
     */
    public int insertExternalAtSlot(
            int slot, String itemId, int count, String nbtJson, int maxStackSize, boolean simulate) {
        if (count <= 0 || !Zone.INBOUND.containsSlot(slot)) {
            return Math.max(0, count);
        }
        return insertAtSlotInternal(slot, itemId, count, nbtJson, maxStackSize, simulate);
    }

    /**
     * Extracts up to {@code maxCount} items via external automation strictly from Outbound slots
     * ({@code 500..999}). Any attempt on Inbound slots ({@code 0..499}) returns {@link Optional#empty()}.
     *
     * @param slot target slot index
     * @param maxCount maximum items to extract
     * @return extracted stack, or empty if disallowed, empty, or {@code maxCount <= 0}
     */
    public Optional<SlotStack> extractExternal(int slot, int maxCount) {
        return extractExternal(slot, maxCount, false);
    }

    /**
     * Extracts (or simulates extracting) up to {@code maxCount} items strictly from Outbound slots
     * ({@code 500..999}). Never reads or removes from Inbound slots ({@code 0..499}).
     *
     * @param slot target slot index
     * @param maxCount maximum items to extract
     * @param simulate if {@code true}, returns what would be extracted without mutating storage
     * @return extracted stack, or empty if disallowed or empty
     */
    public Optional<SlotStack> extractExternal(int slot, int maxCount, boolean simulate) {
        if (maxCount <= 0 || !Zone.OUTBOUND.containsSlot(slot)) {
            return Optional.empty();
        }
        return extractAtSlotInternal(slot, maxCount, simulate);
    }

    /**
     * Counts total items matching {@code itemId} across Inbound slots ({@code 0..499}).
     *
     * @param itemId namespaced item ID
     * @return total item count in Inbound
     */
    public int countInInbound(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        return countInZone(Zone.INBOUND, stack -> stack.matchesItem(itemId));
    }

    /**
     * Counts total items matching {@code matcher} across Inbound slots ({@code 0..499}).
     * Useful for tag-based orders such as {@code #ingots/iron} or {@code #minecraft:logs}.
     *
     * @param matcher predicate evaluated on each occupied Inbound stack
     * @return total matching item count in Inbound
     */
    public int countInInbound(Predicate<SlotStack> matcher) {
        Objects.requireNonNull(matcher, "matcher");
        return countInZone(Zone.INBOUND, matcher);
    }

    /**
     * Counts total items matching {@code itemId} across Outbound slots ({@code 500..999}).
     *
     * @param itemId namespaced item ID
     * @return total item count in Outbound
     */
    public int countInOutbound(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        return countInZone(Zone.OUTBOUND, stack -> stack.matchesItem(itemId));
    }

    /**
     * Atomically consumes exactly {@code count} items matching {@code itemId} from Inbound
     * ({@code 0..499}) to satisfy Village Buy Orders or Expansion Material Orders.
     * If fewer than {@code count} items are present, storage is left completely untouched.
     *
     * @param itemId namespaced item ID
     * @param count quantity to consume ({@code >= 0})
     * @return {@code true} if fully consumed, {@code false} if insufficient stock
     */
    public boolean consumeFromInbound(String itemId, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, got " + count);
        }
        if (count == 0) {
            return true;
        }
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return consumeFromInboundMatching(stack -> stack.matchesItem(itemId), count);
    }

    /**
     * Atomically consumes exactly {@code count} items matching {@code matcher} from Inbound
     * ({@code 0..499}). Leaves storage untouched when fewer than {@code count} items match.
     *
     * @param matcher predicate selecting eligible Inbound stacks
     * @param count quantity to consume ({@code >= 0})
     * @return {@code true} if fully consumed, {@code false} if insufficient stock
     */
    public boolean consumeFromInboundMatching(Predicate<SlotStack> matcher, int count) {
        Objects.requireNonNull(matcher, "matcher");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, got " + count);
        }
        if (count == 0) {
            return true;
        }
        if (countInZone(Zone.INBOUND, matcher) < count) {
            return false;
        }
        int remaining = count;
        for (int i = INBOUND_START; i < INBOUND_END_EXCLUSIVE && remaining > 0; i++) {
            SlotStack stack = slots[i];
            if (stack == null || !matcher.test(stack)) {
                continue;
            }
            if (stack.count() <= remaining) {
                remaining -= stack.count();
                slots[i] = null;
            } else {
                slots[i] = stack.withCount(stack.count() - remaining);
                remaining = 0;
            }
        }
        revision++;
        return true;
    }

    /**
     * Deposits items purchased from a Village Sell Order strictly into Outbound slots
     * ({@code 500..999}), using default stack size 64.
     *
     * @param itemId namespaced item ID
     * @param count quantity to deposit
     * @param nbtJson optional NBT payload (e.g. enchanted book metadata)
     * @return remainder count that did not fit ({@code 0} means all items deposited)
     */
    public int depositToOutbound(String itemId, int count, String nbtJson) {
        return depositToOutbound(itemId, count, nbtJson, DEFAULT_MAX_STACK_SIZE, false);
    }

    /**
     * Deposits (or simulates depositing) purchased items strictly into Outbound slots
     * ({@code 500..999}). Never touches Inbound slots ({@code 0..499}).
     *
     * @param itemId namespaced item ID
     * @param count quantity to deposit
     * @param nbtJson optional NBT payload
     * @param maxStackSize maximum stack size per slot
     * @param simulate if {@code true}, performs no mutation
     * @return remainder count that did not fit
     */
    public int depositToOutbound(String itemId, int count, String nbtJson, int maxStackSize, boolean simulate) {
        return insertIntoZone(Zone.OUTBOUND, itemId, count, nbtJson, maxStackSize, simulate);
    }

    /**
     * Checks whether {@code count} plain items of {@code itemId} can fit into Outbound
     * ({@code 500..999}) without modifying storage.
     *
     * @param itemId namespaced item ID
     * @param count quantity to check
     * @return {@code true} if the entire quantity fits in Outbound
     */
    public boolean canFitInOutbound(String itemId, int count) {
        return canFitInOutbound(itemId, count, "", DEFAULT_MAX_STACK_SIZE);
    }

    /**
     * Checks whether {@code count} items with optional NBT can fit into Outbound ({@code 500..999}).
     *
     * @param itemId namespaced item ID
     * @param count quantity to check
     * @param nbtJson optional NBT payload
     * @param maxStackSize maximum stack size per slot
     * @return {@code true} if the entire quantity fits in Outbound
     */
    public boolean canFitInOutbound(String itemId, int count, String nbtJson, int maxStackSize) {
        if (count <= 0) {
            return true;
        }
        return insertIntoZone(Zone.OUTBOUND, itemId, count, nbtJson, maxStackSize, true) == 0;
    }

    /**
     * Checks whether {@code count} items can fit into Inbound ({@code 0..499}).
     *
     * @param itemId namespaced item ID
     * @param count quantity to check
     * @param nbtJson optional NBT payload
     * @param maxStackSize maximum stack size per slot
     * @return {@code true} if the entire quantity fits in Inbound
     */
    public boolean canFitInInbound(String itemId, int count, String nbtJson, int maxStackSize) {
        if (count <= 0) {
            return true;
        }
        return insertIntoZone(Zone.INBOUND, itemId, count, nbtJson, maxStackSize, true) == 0;
    }

    /**
     * Returns the current stack at {@code slot}, or empty if vacant.
     *
     * @param slot absolute slot index in {@code 0..999}
     * @return optional slot stack
     */
    public Optional<SlotStack> getSlot(int slot) {
        validateSlotIndex(slot);
        return Optional.ofNullable(slots[slot]);
    }

    /**
     * Sets a slot directly (intended for GUI sync, administrative tools, and deserialization).
     * Passing {@code null} clears the slot.
     *
     * @param slot absolute slot index in {@code 0..999}
     * @param stack new stack or null to clear
     */
    public void setSlotRaw(int slot, SlotStack stack) {
        validateSlotIndex(slot);
        slots[slot] = stack;
        revision++;
    }

    /**
     * Direct GUI helper allowing a player to manually extract items from any slot ({@code 0..999}),
     * e.g., to collect Outbound purchases or retrieve misplaced Inbound deliveries.
     *
     * @param slot absolute slot index in {@code 0..999}
     * @param maxCount maximum items to remove
     * @return extracted stack, or empty
     */
    public Optional<SlotStack> playerExtract(int slot, int maxCount) {
        validateSlotIndex(slot);
        if (maxCount <= 0) {
            return Optional.empty();
        }
        return extractAtSlotInternal(slot, maxCount, false);
    }

    /**
     * Direct GUI helper allowing a player to manually deposit into a specific Inbound slot ({@code 0..499}).
     * Rejects Outbound slots to preserve the dual-zone contract.
     *
     * @param slot target Inbound slot index
     * @param itemId namespaced item ID
     * @param count quantity to insert
     * @param nbtJson optional NBT payload
     * @param maxStackSize maximum stack size per slot
     * @return remainder not inserted
     */
    public int playerInsertIntoInbound(
            int slot, String itemId, int count, String nbtJson, int maxStackSize) {
        if (count <= 0 || !Zone.INBOUND.containsSlot(slot)) {
            return Math.max(0, count);
        }
        return insertAtSlotInternal(slot, itemId, count, nbtJson, maxStackSize, false);
    }

    /**
     * Clears all 1,000 slots.
     */
    public void clearAll() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slots[i] = null;
        }
        revision++;
    }

    /**
     * Returns a paginated (and optionally search-filtered) slice of a zone for the Hub/Terminal GUI.
     * With an empty search query and {@code pageSize = 50}, each 500-slot zone yields exactly 10 pages.
     *
     * @param zone target zone ({@link Zone#INBOUND} or {@link Zone#OUTBOUND})
     * @param pageIndex requested 0-based page index (clamped into valid range)
     * @param pageSize slots per page ({@code >= 1})
     * @param searchQuery optional case-insensitive substring filter on item ID or NBT (null/blank for all slots)
     * @return page result containing matching absolute slot indices and total page count
     */
    public PageResult getPage(Zone zone, int pageIndex, int pageSize, String searchQuery) {
        Objects.requireNonNull(zone, "zone");
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1, got " + pageSize);
        }

        String normalizedQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        boolean filtering = !normalizedQuery.isEmpty();

        List<Integer> matchingSlots;
        if (!filtering) {
            int total = zone.slotCount();
            int totalPages = (total + pageSize - 1) / pageSize;
            int clampedPage = Math.max(0, Math.min(pageIndex, totalPages - 1));
            int fromOffset = clampedPage * pageSize;
            int toOffset = Math.min(total, fromOffset + pageSize);
            List<Integer> indices = new ArrayList<>(toOffset - fromOffset);
            List<SlotEntry> nonEmpty = new ArrayList<>();
            for (int off = fromOffset; off < toOffset; off++) {
                int absSlot = zone.startSlot() + off;
                indices.add(absSlot);
                SlotStack st = slots[absSlot];
                if (st != null) {
                    nonEmpty.add(new SlotEntry(absSlot, st));
                }
            }
            return new PageResult(zone, clampedPage, pageSize, total, totalPages, indices, nonEmpty);
        }

        matchingSlots = new ArrayList<>();
        String underscoreQuery = normalizedQuery.replace(' ', '_');
        for (int s = zone.startSlot(); s < zone.endSlotExclusive(); s++) {
            SlotStack st = slots[s];
            if (st != null && matchesSearch(st, normalizedQuery, underscoreQuery)) {
                matchingSlots.add(s);
            }
        }

        int totalMatching = matchingSlots.size();
        int totalPages = Math.max(1, (totalMatching + pageSize - 1) / pageSize);
        int clampedPage = Math.max(0, Math.min(pageIndex, totalPages - 1));
        int from = clampedPage * pageSize;
        int to = Math.min(totalMatching, from + pageSize);

        List<Integer> pageIndices = new ArrayList<>(Math.max(0, to - from));
        List<SlotEntry> nonEmpty = new ArrayList<>(Math.max(0, to - from));
        for (int idx = from; idx < to; idx++) {
            int absSlot = matchingSlots.get(idx);
            pageIndices.add(absSlot);
            nonEmpty.add(new SlotEntry(absSlot, slots[absSlot]));
        }
        return new PageResult(zone, clampedPage, pageSize, totalMatching, totalPages, pageIndices, nonEmpty);
    }

    /**
     * Returns the number of occupied slots in the specified zone.
     *
     * @param zone target zone
     * @return occupied slot count ({@code 0..500})
     */
    public int getUsedSlotCount(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        int used = 0;
        for (int i = zone.startSlot(); i < zone.endSlotExclusive(); i++) {
            if (slots[i] != null) {
                used++;
            }
        }
        return used;
    }

    /**
     * Returns the sum of item counts across all slots in the specified zone.
     *
     * @param zone target zone
     * @return total item count
     */
    public int getTotalItemCount(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        int total = 0;
        for (int i = zone.startSlot(); i < zone.endSlotExclusive(); i++) {
            if (slots[i] != null) {
                total += slots[i].count();
            }
        }
        return total;
    }

    /**
     * Aggregates total quantities per item ID inside {@code zone} (ideal for remote Comm Terminal summaries).
     *
     * @param zone target zone
     * @return unmodifiable map of {@code itemId -> totalCount}
     */
    public Map<String, Integer> getItemSummary(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (int i = zone.startSlot(); i < zone.endSlotExclusive(); i++) {
            SlotStack st = slots[i];
            if (st != null) {
                summary.merge(st.itemId(), st.count(), Integer::sum);
            }
        }
        return Collections.unmodifiableMap(summary);
    }

    /**
     * Exports all occupied slots in ascending index order for compact SavedData NBT persistence.
     *
     * @return unmodifiable list of non-empty slot entries
     */
    public List<SlotEntry> exportNonEmptySlots() {
        List<SlotEntry> list = new ArrayList<>();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (slots[i] != null) {
                list.add(new SlotEntry(i, slots[i]));
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Exports an immutable {@link StorageSnapshot} DTO for SavedData persistence.
     *
     * @return storage snapshot
     */
    public StorageSnapshot exportSnapshot() {
        return new StorageSnapshot(exportNonEmptySlots());
    }

    /**
     * Replaces all warehouse contents from a sparse list of slot entries.
     *
     * @param entries slot entries to load
     */
    public void importSlots(Collection<SlotEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        clearAll();
        for (SlotEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            slots[entry.slot()] = entry.stack();
        }
        revision++;
    }

    /**
     * Replaces all warehouse contents from a {@link StorageSnapshot}.
     *
     * @param snapshot persisted snapshot DTO
     */
    public void importSnapshot(StorageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        importSlots(snapshot.entries());
    }

    /**
     * Creates an independent deep copy of this warehouse.
     *
     * @return copied storage
     */
    public CooperationHubStorage copy() {
        CooperationHubStorage clone = new CooperationHubStorage();
        System.arraycopy(this.slots, 0, clone.slots, 0, TOTAL_SLOTS);
        return clone;
    }

    private int insertIntoZone(
            Zone zone, String itemId, int count, String nbtJson, int maxStackSize, boolean simulate) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be null or blank");
        }
        if (count <= 0) {
            return 0;
        }
        if (maxStackSize < 1) {
            throw new IllegalArgumentException("maxStackSize must be >= 1, got " + maxStackSize);
        }
        String normNbt = nbtJson == null ? "" : nbtJson;
        int remaining = count;

        // Pass 1: merge into existing matching partial stacks.
        for (int i = zone.startSlot(); i < zone.endSlotExclusive() && remaining > 0; i++) {
            SlotStack existing = slots[i];
            if (existing != null && existing.canStackWith(itemId, normNbt) && existing.count() < maxStackSize) {
                int space = maxStackSize - existing.count();
                int toAdd = Math.min(space, remaining);
                if (!simulate) {
                    slots[i] = existing.withCount(existing.count() + toAdd);
                }
                remaining -= toAdd;
            }
        }

        // Pass 2: occupy empty slots within the same zone.
        for (int i = zone.startSlot(); i < zone.endSlotExclusive() && remaining > 0; i++) {
            if (slots[i] == null) {
                int toAdd = Math.min(maxStackSize, remaining);
                if (!simulate) {
                    slots[i] = new SlotStack(itemId, toAdd, normNbt);
                }
                remaining -= toAdd;
            }
        }
        if (!simulate && remaining < count) {
            revision++;
        }
        return remaining;
    }

    private int insertAtSlotInternal(
            int slot, String itemId, int count, String nbtJson, int maxStackSize, boolean simulate) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be null or blank");
        }
        if (maxStackSize < 1) {
            throw new IllegalArgumentException("maxStackSize must be >= 1, got " + maxStackSize);
        }
        String normNbt = nbtJson == null ? "" : nbtJson;
        SlotStack existing = slots[slot];
        if (existing == null) {
            int toAdd = Math.min(maxStackSize, count);
            if (!simulate) {
                slots[slot] = new SlotStack(itemId, toAdd, normNbt);
                revision++;
            }
            return count - toAdd;
        }
        if (!existing.canStackWith(itemId, normNbt) || existing.count() >= maxStackSize) {
            return count;
        }
        int space = maxStackSize - existing.count();
        int toAdd = Math.min(space, count);
        if (!simulate) {
            slots[slot] = existing.withCount(existing.count() + toAdd);
            revision++;
        }
        return count - toAdd;
    }

    private Optional<SlotStack> extractAtSlotInternal(int slot, int maxCount, boolean simulate) {
        SlotStack existing = slots[slot];
        if (existing == null) {
            return Optional.empty();
        }
        int taken = Math.min(existing.count(), maxCount);
        SlotStack result = existing.withCount(taken);
        if (!simulate) {
            if (taken >= existing.count()) {
                slots[slot] = null;
            } else {
                slots[slot] = existing.withCount(existing.count() - taken);
            }
            revision++;
        }
        return Optional.of(result);
    }

    private int countInZone(Zone zone, Predicate<SlotStack> matcher) {
        int total = 0;
        for (int i = zone.startSlot(); i < zone.endSlotExclusive(); i++) {
            SlotStack st = slots[i];
            if (st != null && matcher.test(st)) {
                total += st.count();
            }
        }
        return total;
    }

    private static boolean matchesSearch(SlotStack stack, String query, String underscoreQuery) {
        String idLower = stack.itemId().toLowerCase(Locale.ROOT);
        if (idLower.contains(query) || idLower.contains(underscoreQuery)) {
            return true;
        }
        String spacedId = idLower.replace('_', ' ');
        if (spacedId.contains(query)) {
            return true;
        }
        return !stack.nbtJson().isEmpty()
                && stack.nbtJson().toLowerCase(Locale.ROOT).contains(query);
    }

    private static void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOTS) {
            throw new IndexOutOfBoundsException("Slot out of range 0..999: " + slot);
        }
    }
}
