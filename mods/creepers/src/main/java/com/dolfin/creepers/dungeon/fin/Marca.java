package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.Dificultad;

import java.util.List;
import java.util.UUID;

/**
 * Una victoria guardada en los récords (#22).
 *
 * @param dungeon id de la dungeon
 * @param miembros quienes ganaron, con el nombre que tenían entonces
 * @param tiempoMillis duración de la partida hasta caer el jefe
 * @param fechaMillis hora real (epoch ms) de la victoria
 */
public record Marca(
        String dungeon,
        Dificultad dificultad,
        List<Integrante> miembros,
        long tiempoMillis,
        long fechaMillis) {
    /** Un ganador de la marca. */
    public record Integrante(UUID id, String nombre) {}

    public Marca {
        miembros = List.copyOf(miembros);
    }

    public boolean incluye(UUID jugador) {
        return miembros.stream().anyMatch(m -> m.id().equals(jugador));
    }
}
