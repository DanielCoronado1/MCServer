package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.fin.ProgresoJugadores;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lo que los requisitos de entrada necesitan saber del progreso guardado de cada jugador. Lo
 * escribe el fin de partida (#17): las dungeons completadas y el cooldown, que empieza al terminar
 * la partida sea cual sea el resultado.
 */
public interface ConsultaProgreso {
    /** Ha ganado la dungeon alguna vez, en cualquier dificultad. */
    boolean completo(UUID jugador, String dungeon);

    /** Milisegundos que le faltan para volver a entrar a la dungeon; 0 si puede. */
    long cooldownRestanteMillis(UUID jugador, String dungeon, long ahoraMillis);

    /**
     * Consulta sobre el almacén de progreso de {@code HistorialDungeons}, que se vuelve a crear al
     * cargar cada mundo; por eso se pide en cada llamada.
     */
    static ConsultaProgreso de(Supplier<ProgresoJugadores> almacen) {
        return new ConsultaProgreso() {
            @Override
            public boolean completo(UUID jugador, String dungeon) {
                return almacen.get().completo(jugador, dungeon);
            }

            @Override
            public long cooldownRestanteMillis(UUID jugador, String dungeon, long ahoraMillis) {
                return almacen.get().cooldownRestanteMillis(jugador, dungeon, ahoraMillis);
            }
        };
    }
}
