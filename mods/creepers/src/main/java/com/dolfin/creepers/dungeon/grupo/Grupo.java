package com.dolfin.creepers.dungeon.grupo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Vista de un grupo de jugadores (la {@code /party} propia de las dungeons). Los miembros van en
 * orden de entrada y el líder es uno de ellos. Solo {@link Grupos} lo modifica.
 */
public final class Grupo {
    private final UUID id = UUID.randomUUID();
    private final List<UUID> miembros = new ArrayList<>();
    private UUID lider;

    Grupo(UUID lider) {
        this.lider = lider;
        miembros.add(lider);
    }

    public UUID id() {
        return id;
    }

    public UUID lider() {
        return lider;
    }

    /** Miembros en orden de entrada, líder incluido. */
    public List<UUID> miembros() {
        return Collections.unmodifiableList(miembros);
    }

    public boolean contiene(UUID jugador) {
        return miembros.contains(jugador);
    }

    void anadir(UUID jugador) {
        miembros.add(jugador);
    }

    void quitar(UUID jugador) {
        miembros.remove(jugador);
    }

    void lider(UUID nuevo) {
        lider = nuevo;
    }
}
