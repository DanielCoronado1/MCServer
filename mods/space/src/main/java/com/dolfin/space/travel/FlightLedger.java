package com.dolfin.space.travel;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.ships.SpaceshipEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;

import java.util.*;

/** World-level recovery index; parked ships need no forced chunks. */
public final class FlightLedger extends SavedData {
    public record Entry(
            String ship,
            String owner,
            String dimension,
            long position,
            boolean active,
            boolean autoReturn,
            List<String> pending,
            List<String> crew) {
        public Entry(
                String ship,
                String owner,
                String dimension,
                long position,
                boolean active,
                boolean autoReturn,
                List<String> pending) {
            this(ship, owner, dimension, position, active, autoReturn, pending, List.of());
        }

        public static final Codec<Entry> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.STRING.fieldOf("ship").forGetter(Entry::ship),
                                                Codec.STRING
                                                        .fieldOf("owner")
                                                        .forGetter(Entry::owner),
                                                Codec.STRING
                                                        .fieldOf("dimension")
                                                        .forGetter(Entry::dimension),
                                                Codec.LONG
                                                        .fieldOf("position")
                                                        .forGetter(Entry::position),
                                                Codec.BOOL
                                                        .fieldOf("active")
                                                        .forGetter(Entry::active),
                                                Codec.BOOL
                                                        .fieldOf("autoReturn")
                                                        .forGetter(Entry::autoReturn),
                                                Codec.STRING
                                                        .listOf()
                                                        .fieldOf("pending")
                                                        .forGetter(Entry::pending),
                                                Codec.STRING
                                                        .listOf()
                                                        .optionalFieldOf("crew", List.of())
                                                        .forGetter(Entry::crew))
                                        .apply(i, Entry::new));

        public Entry pending(String player) {
            var list = new ArrayList<>(pending);
            if (!list.contains(player)) list.add(player);
            return new Entry(
                    ship, owner, dimension, position, active, autoReturn, List.copyOf(list), crew);
        }

        public Entry arrived(String player) {
            return new Entry(
                    ship,
                    owner,
                    dimension,
                    position,
                    active,
                    autoReturn,
                    pending.stream().filter(p -> !p.equals(player)).toList(),
                    crew);
        }

        public Entry returning() {
            return new Entry(ship, owner, dimension, position, active, true, pending, crew);
        }
    }

    public static final Codec<FlightLedger> CODEC =
            Entry.CODEC.listOf().xmap(FlightLedger::new, l -> List.copyOf(l.entries.values()));
    public static final SavedDataType<FlightLedger> TYPE =
            new SavedDataType<>(DolfinSpace.id("flight_ledger"), FlightLedger::new, CODEC, null);
    public final Map<String, Entry> entries = new LinkedHashMap<>();

    public FlightLedger() {}

    private FlightLedger(List<Entry> list) {
        list.forEach(e -> entries.put(e.ship(), e));
        DolfinSpace.LOGGER.debug("event=flight_ledger_load entries={}", list.size());
    }

    public static FlightLedger get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void put(Entry e) {
        entries.put(e.ship(), e);
        setDirty();
        DolfinSpace.LOGGER.debug(
                "event=flight_ledger_save ship={} active={} pending={}",
                e.ship(),
                e.active(),
                e.pending().size());
    }

    public void track(SpaceshipEntity ship) {
        String key = ship.getUUID().toString();
        var old = entries.get(key);
        var e =
                new Entry(
                        key,
                        ship.owner == null ? "" : ship.owner.toString(),
                        ship.level().dimension().identifier().toString(),
                        ship.blockPosition().asLong(),
                        ship.inFlight,
                        old != null && old.autoReturn(),
                        old == null ? List.of() : old.pending(),
                        FlightController.journal(ship) != null
                                ? FlightController.journal(ship).riders()
                                : old == null ? List.of() : old.crew());
        if (!e.equals(old)) put(e);
    }

    public void remove(SpaceshipEntity ship) {
        if (entries.remove(ship.getUUID().toString()) != null) setDirty();
    }
}
