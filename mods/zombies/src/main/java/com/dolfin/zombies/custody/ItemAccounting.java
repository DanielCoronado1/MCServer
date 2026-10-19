package com.dolfin.zombies.custody;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;
import java.util.function.*;

/**
 * Pure item bookkeeping shared by every custody path. Generic over the stack type so JUnit can run
 * the exact production logic with plain records while the mod plugs in {@code ItemStack}.
 */
public final class ItemAccounting {
    private ItemAccounting() {}

    /** Minimal view of a stack: identity key (item + components) and size. */
    public interface Stacks<T> {
        boolean isEmpty(T stack);

        int count(T stack);

        String key(T stack);
    }

    /** Items carried by a thief/zombie/mailbox entry, persisted as one unit with its custody id. */
    public record Packet<T>(UUID custody, UUID owner, String ownerName, List<T> items) {
        public Packet {
            items = List.copyOf(items);
        }

        public static <T> Codec<Packet<T>> codec(Codec<T> item) {
            return RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.STRING
                                                    .xmap(UUID::fromString, UUID::toString)
                                                    .fieldOf("custody")
                                                    .forGetter(Packet<T>::custody),
                                            Codec.STRING
                                                    .xmap(UUID::fromString, UUID::toString)
                                                    .fieldOf("owner")
                                                    .forGetter(Packet<T>::owner),
                                            Codec.STRING
                                                    .fieldOf("ownerName")
                                                    .forGetter(Packet<T>::ownerName),
                                            item.listOf()
                                                    .fieldOf("items")
                                                    .forGetter(Packet<T>::items))
                                    .apply(i, Packet::new));
        }
    }

    /**
     * Chest layout: first chest (27 slots), optional second half of a double chest, and leftovers.
     */
    public record ChestPlan<T>(List<T> first, List<T> second, List<T> overflow) {
        public boolean needsDouble() {
            return !second.isEmpty();
        }
    }

    public static final int CHEST_SLOTS = 27;

    public static <T> Map<String, Integer> tally(Stacks<T> ops, Collection<T> stacks) {
        Map<String, Integer> out = new TreeMap<>();
        for (T s : stacks)
            if (s != null && !ops.isEmpty(s)) out.merge(ops.key(s), ops.count(s), Integer::sum);
        return out;
    }

    public static <T> int count(Stacks<T> ops, Collection<T> stacks) {
        int n = 0;
        for (T s : stacks) if (s != null && !ops.isEmpty(s)) n += ops.count(s);
        return n;
    }

    public static <T> int stacks(Stacks<T> ops, Collection<T> stacks) {
        int n = 0;
        for (T s : stacks) if (s != null && !ops.isEmpty(s)) n++;
        return n;
    }

    /** `items=count:N,stacks:M` for the custody log line. */
    public static <T> String countSummary(Stacks<T> ops, Collection<T> stacks) {
        return "count:" + count(ops, stacks) + ",stacks:" + stacks(ops, stacks);
    }

    /** Human/admin readable detail, truncated for logs and the ledger. */
    public static <T> String detail(Stacks<T> ops, Collection<T> stacks, int max) {
        var sb = new StringBuilder();
        for (var e : tally(ops, stacks).entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('*').append(e.getValue());
        }
        return sb.length() > max ? sb.substring(0, Math.max(0, max - 3)) + "..." : sb.toString();
    }

    /**
     * Picks the slots that change hands. Returns slot indices; the caller copies those stacks into
     * custody and only then clears them, so a failure before the clear leaves the player untouched.
     */
    public static <T> List<Integer> capture(Stacks<T> ops, List<T> slots, Predicate<T> excluded) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            T s = slots.get(i);
            if (s != null && !ops.isEmpty(s) && !excluded.test(s)) out.add(i);
        }
        return out;
    }

    /**
     * Splits held stacks over a single/double chest; anything that does not fit is overflow (never
     * dropped silently).
     */
    public static <T> ChestPlan<T> chestPlan(
            Stacks<T> ops, List<T> items, boolean doubleAvailable) {
        List<T> real = items.stream().filter(s -> s != null && !ops.isEmpty(s)).toList();
        int firstEnd = Math.min(CHEST_SLOTS, real.size());
        int secondEnd = doubleAvailable ? Math.min(2 * CHEST_SLOTS, real.size()) : firstEnd;
        return new ChestPlan<>(
                List.copyOf(real.subList(0, firstEnd)),
                List.copyOf(real.subList(firstEnd, secondEnd)),
                List.copyOf(real.subList(secondEnd, real.size())));
    }

    /**
     * Steal slot choice. Prefers the selected hand slot; otherwise a random eligible slot from the
     * hotbar (or the whole main inventory when {@code hotbarOnly} is false). -1 when nothing is
     * eligible.
     */
    public static int stealSlot(
            List<Boolean> eligible,
            int selected,
            int hotbarSize,
            boolean hotbarOnly,
            IntUnaryOperator random) {
        if (selected >= 0 && selected < eligible.size() && eligible.get(selected)) return selected;
        int limit = hotbarOnly ? Math.min(hotbarSize, eligible.size()) : eligible.size();
        List<Integer> options = new ArrayList<>();
        for (int i = 0; i < limit; i++) if (eligible.get(i)) options.add(i);
        return options.isEmpty() ? -1 : options.get(random.applyAsInt(options.size()));
    }

    /**
     * Infection stage from remaining/total ticks: 0 nothing (<50 %), 1 mild hunger (50–80 %), 2
     * occasional nausea (80–100 %).
     */
    public static int infectionStage(int remaining, int total) {
        if (total <= 0 || remaining >= total) return 0;
        double elapsed = 1 - (double) Math.max(0, remaining) / total;
        return elapsed < .5 ? 0 : elapsed < .8 ? 1 : 2;
    }

    /**
     * Matches an item identifier or `#tag` against configured selector strings without requiring
     * Minecraft bootstrap.
     */
    public static boolean matchesSelector(
            String itemId, Predicate<String> tagMatcher, List<String> selectors) {
        for (String sel : selectors) {
            if (sel.startsWith("#")) {
                if (tagMatcher.test(sel.substring(1))) return true;
            } else if (sel.equals(itemId)) return true;
        }
        return false;
    }
}
