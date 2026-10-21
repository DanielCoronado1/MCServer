package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

import java.util.List;
import java.util.UUID;

/** Decisiones puras (sin Minecraft) del fin de partida y sus recompensas (#17, #22). */
public final class ReglasFin {
    /**
     * Recompensas de la primera victoria de un jugador.
     *
     * @param titulo primera victoria en esa dungeon y dificultad: nodo de LuckPerms
     * @param cabeza primera victoria en hardcore en esa dungeon: cabeza del jefe
     */
    public record Premios(boolean titulo, boolean cabeza) {}

    private ReglasFin() {}

    /** Premios de {@code jugador} por ganar; consultar antes de añadir la marca a la tabla. */
    public static Premios premios(
            TablaRecords records, UUID jugador, String dungeon, Dificultad dificultad) {
        boolean primera = records.esPrimeraVictoria(jugador, dungeon, dificultad);
        return new Premios(primera, primera && dificultad == Dificultad.HARDCORE);
    }

    /** Quienes ganan: los miembros que aún no han salido (conectados o con plazo para volver). */
    public static List<Marca.Integrante> ganadores(Partida partida) {
        return partida.miembros().stream()
                .filter(m -> m.estado().participa())
                .map(m -> new Marca.Integrante(m.id(), m.nombre()))
                .toList();
    }

    /** Marca de la victoria de {@code partida} con los ganadores actuales. */
    public static Marca marca(Partida partida, long tiempoMillis, long fechaMillis) {
        return new Marca(
                partida.dungeon().id(),
                partida.dificultad(),
                ganadores(partida),
                tiempoMillis,
                fechaMillis);
    }

    /** Miembros que empiezan cooldown al terminar: todos los que jugaron, hayan salido o no. */
    public static List<UUID> sujetosACooldown(Partida partida, ResultadoPartida resultado) {
        if (resultado == ResultadoPartida.CANCELADA || partida.dungeon().cooldownMinutos() <= 0) {
            return List.of();
        }
        return partida.miembros().stream().map(Miembro::id).toList();
    }

    /** Instante en que acaba el cooldown de una partida terminada en {@code ahoraMillis}. */
    public static long finDeCooldown(Partida partida, long ahoraMillis) {
        return ahoraMillis + partida.dungeon().cooldownMinutos() * 60_000L;
    }
}
