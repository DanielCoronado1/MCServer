package com.dolfin.zombies.custody;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/**
 * Pure, persisted state machine answering "who holds this player's items right now". Every batch of
 * items taken from a player gets one record; it moves only along {@link Holder#canMoveTo}. A
 * transition whose expected source does not match the stored state is rejected: that is the
 * anti-duplication guard (a second copy of the same thief/zombie cannot release the same items
 * twice). No Minecraft types so it is unit-testable; persisted inside {@link CustodyState}.
 */
public final class CustodyLedger {
    /**
     * Terminal holders end the custody: items are back in the world or in the owner's inventory.
     */
    public enum Holder {
        PLAYER,
        THIEF,
        INFECTED,
        CHEST,
        GROUND,
        MAILBOX,
        RETURNED,
        BLOCKED;

        public boolean terminal() {
            return this == CHEST || this == GROUND || this == RETURNED || this == BLOCKED;
        }

        public boolean canMoveTo(Holder to) {
            return switch (this) {
                case PLAYER -> to == THIEF || to == INFECTED || to == MAILBOX;
                case THIEF -> to == GROUND || to == MAILBOX;
                case INFECTED -> to == CHEST || to == GROUND || to == MAILBOX;
                case MAILBOX -> to == RETURNED;
                default -> false;
            };
        }
    }

    public record Position(String dimension, int x, int y, int z) {
        public static final Codec<Position> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.STRING
                                                                .fieldOf("dimension")
                                                                .forGetter(Position::dimension),
                                                        Codec.INT
                                                                .fieldOf("x")
                                                                .forGetter(Position::x),
                                                Codec.INT.fieldOf("y").forGetter(Position::y),
                                                        Codec.INT
                                                                .fieldOf("z")
                                                                .forGetter(Position::z))
                                        .apply(i, Position::new));

        @Override
        public String toString() {
            return dimension + "@" + x + "," + y + "," + z;
        }
    }

    /** Immutable snapshot of one custody; replaced on every change. */
    public record Record(
            UUID id,
            UUID owner,
            String ownerName,
            Holder holder,
            Optional<UUID> entity,
            String kind,
            Position position,
            int items,
            int stacks,
            String summary,
            long created,
            long updated) {
        public static final Codec<Record> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.STRING
                                                        .xmap(UUID::fromString, UUID::toString)
                                                        .fieldOf("id")
                                                        .forGetter(Record::id),
                                                Codec.STRING
                                                        .xmap(UUID::fromString, UUID::toString)
                                                        .fieldOf("owner")
                                                        .forGetter(Record::owner),
                                                Codec.STRING
                                                        .fieldOf("ownerName")
                                                        .forGetter(Record::ownerName),
                                                Codec.STRING
                                                        .xmap(Holder::valueOf, Holder::name)
                                                        .fieldOf("holder")
                                                        .forGetter(Record::holder),
                                                Codec.STRING
                                                        .xmap(UUID::fromString, UUID::toString)
                                                        .optionalFieldOf("entity")
                                                        .forGetter(Record::entity),
                                                Codec.STRING
                                                        .fieldOf("kind")
                                                        .forGetter(Record::kind),
                                                Position.CODEC
                                                        .fieldOf("position")
                                                        .forGetter(Record::position),
                                                Codec.INT.fieldOf("items").forGetter(Record::items),
                                                Codec.INT
                                                        .fieldOf("stacks")
                                                        .forGetter(Record::stacks),
                                                Codec.STRING
                                                        .fieldOf("summary")
                                                        .forGetter(Record::summary),
                                                Codec.LONG
                                                        .fieldOf("created")
                                                        .forGetter(Record::created),
                                                Codec.LONG
                                                        .fieldOf("updated")
                                                        .forGetter(Record::updated))
                                        .apply(i, Record::new));

        Record with(Holder h, Optional<UUID> e, Position p, long time) {
            return new Record(
                    id, owner, ownerName, h, e, kind, p, items, stacks, summary, created, time);
        }
    }

    public enum Outcome {
        OK,
        MISSING,
        REJECTED
    }

    public record Result(Outcome outcome, Holder previous, String reason) {
        public boolean ok() {
            return outcome == Outcome.OK;
        }
    }

    /**
     * Called for every accepted change so the runtime can log `event=item_custody` and mark
     * SavedData dirty.
     */
    public interface Listener {
        void moved(Holder from, Record record, String reason);
    }

    public static final int TERMINAL_HISTORY = 512;
    public static final Codec<CustodyLedger> CODEC =
            Record.CODEC.listOf().xmap(CustodyLedger::new, CustodyLedger::records);

    private final LinkedHashMap<UUID, Record> records = new LinkedHashMap<>();
    private Listener listener = (f, r, why) -> {};

    public CustodyLedger() {}

    public CustodyLedger(List<Record> saved) {
        for (var r : saved) records.put(r.id(), r);
    }

    public void listener(Listener l) {
        listener = l == null ? (f, r, why) -> {} : l;
    }

    public List<Record> records() {
        return List.copyOf(records.values());
    }

    public Optional<Record> get(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public List<Record> active() {
        return records.values().stream().filter(r -> !r.holder().terminal()).toList();
    }

    /**
     * Starts custody: items just left the player. Rejects a reused id (never overwrite history).
     */
    public Result open(Record r, String reason) {
        if (records.containsKey(r.id()))
            return new Result(Outcome.REJECTED, records.get(r.id()).holder(), "duplicate_id");
        if (!Holder.PLAYER.canMoveTo(r.holder()))
            return new Result(Outcome.REJECTED, Holder.PLAYER, "illegal_open_" + r.holder());
        records.put(r.id(), r);
        listener.moved(Holder.PLAYER, r, reason);
        prune();
        return new Result(Outcome.OK, Holder.PLAYER, reason);
    }

    /** Moves custody only if it is currently held by {@code expected}. */
    public Result move(
            UUID id,
            Holder expected,
            Holder to,
            Optional<UUID> entity,
            Position pos,
            long time,
            String reason) {
        var r = records.get(id);
        if (r == null) return new Result(Outcome.MISSING, null, "unknown_custody");
        if (r.holder() != expected)
            return new Result(
                    Outcome.REJECTED, r.holder(), "expected_" + expected + "_found_" + r.holder());
        if (!expected.canMoveTo(to))
            return new Result(Outcome.REJECTED, r.holder(), "illegal_" + expected + "_to_" + to);
        var next = r.with(to, entity, pos, time);
        records.put(id, next);
        listener.moved(expected, next, reason);
        prune();
        return new Result(Outcome.OK, expected, reason);
    }

    /** Last known location for `/zombies dondeesta`; not a state change, so no custody log line. */
    public boolean track(UUID id, Position pos, long time) {
        var r = records.get(id);
        if (r == null || r.holder().terminal()) return false;
        records.put(id, r.with(r.holder(), r.entity(), pos, time));
        return true;
    }

    public List<Record> visibleTo(java.util.function.Predicate<UUID> ownerVisible) {
        return active().stream().filter(r -> ownerVisible.test(r.owner())).toList();
    }

    private void prune() {
        long terminal = records.values().stream().filter(r -> r.holder().terminal()).count();
        if (terminal <= TERMINAL_HISTORY) return;
        var it = records.values().iterator();
        while (terminal > TERMINAL_HISTORY && it.hasNext()) {
            if (it.next().holder().terminal()) {
                it.remove();
                terminal--;
            }
        }
    }
}
