package com.dolfin.space.travel;

import com.dolfin.space.DolfinSpace;
import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;

import java.util.*;

/** Exact ship IDs only; never select all ships belonging to a demo participant. */
public final class DemoCancellations extends SavedData {
    private final Set<String> ships = new HashSet<>();
    public static final Codec<DemoCancellations> CODEC =
            Codec.STRING.listOf().xmap(DemoCancellations::new, s -> List.copyOf(s.ships));
    public static final SavedDataType<DemoCancellations> TYPE =
            new SavedDataType<>(
                    DolfinSpace.id("demo_cancellations"),
                    () -> new DemoCancellations(List.of()),
                    CODEC,
                    null);

    private DemoCancellations(List<String> ids) {
        ships.addAll(ids);
    }

    public static DemoCancellations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void cancel(UUID id) {
        ships.add(id.toString());
        setDirty();
    }

    public boolean contains(UUID id) {
        return ships.contains(id.toString());
    }
}
