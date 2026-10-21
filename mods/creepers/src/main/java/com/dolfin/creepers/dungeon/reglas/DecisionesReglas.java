package com.dolfin.creepers.dungeon.reglas;

import com.dolfin.creepers.dungeon.modelo.EstadoMiembro;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Decisiones puras de las reglas de la dimensión de dungeons (#15), sin Minecraft: cada método
 * recibe ya resueltos los datos que necesita y los adaptadores ({@code ReglasDanio}, {@code
 * ReglasObjetos}, {@code ReglasModo}) los extraen del juego.
 */
final class DecisionesReglas {
    /** Id del objeto cuyo uso solo vale como munición de ballesta. */
    static final String COHETE = "minecraft:firework_rocket";

    private DecisionesReglas() {}

    /** Qué hacer con el uso de un objeto. */
    enum UsoObjeto {
        PERMITIDO,
        /** Está en {@code objetosProhibidos}. */
        PROHIBIDO,
        /** Cohete fuera de una ballesta. */
        COHETE_SIN_BALLESTA
    }

    /**
     * Las reglas solo se aplican dentro de la dimensión y a quien no está en creativo ni espectador
     * (los administradores construyen y revisan sin restricciones).
     */
    static boolean sujetoAReglas(boolean enDimension, boolean creativoOEspectador) {
        return enDimension && !creativoOEspectador;
    }

    /**
     * Ningún daño entre jugadores: se bloquea el que causa otro jugador (directo o a través de un
     * proyectil, nube, TNT o mascota). El daño propio, como una flecha que rebota, sigue valiendo.
     */
    static boolean bloqueaDanio(UUID victima, Optional<UUID> responsable) {
        return responsable.isPresent() && !responsable.get().equals(victima);
    }

    /** Los efectos dañinos de otro jugador (pociones, nubes, flechas) no se aplican. */
    static boolean bloqueaEfecto(boolean daninoso, UUID victima, Optional<UUID> responsable) {
        return daninoso && bloqueaDanio(victima, responsable);
    }

    /**
     * Uso de un objeto en el aire (o al comer o beber).
     *
     * @param otraManoBallesta la otra mano sostiene una ballesta: el cohete es su munición, y
     *     usarlo no hace nada por sí solo porque el vuelo está bloqueado
     */
    static UsoObjeto usoEnAire(
            String idObjeto, boolean otraManoBallesta, Collection<String> prohibidos) {
        if (prohibidos.contains(idObjeto)) {
            return UsoObjeto.PROHIBIDO;
        }
        if (COHETE.equals(idObjeto) && !otraManoBallesta) {
            return UsoObjeto.COHETE_SIN_BALLESTA;
        }
        return UsoObjeto.PERMITIDO;
    }

    /**
     * Uso de un objeto sobre un bloque. Solo se frena el cohete, que lanzado desde el suelo
     * explotaría: el resto de objetos prohibidos no hacen nada contra bloques en modo aventura, y
     * frenarlos también impediría pulsar botones con ellos en la mano.
     */
    static UsoObjeto usoEnBloque(String idObjeto) {
        return COHETE.equals(idObjeto) ? UsoObjeto.COHETE_SIN_BALLESTA : UsoObjeto.PERMITIDO;
    }

    /** Un miembro vivo en supervivencia pasa a aventura: dentro nadie rompe ni pone bloques. */
    static boolean debeForzarAventura(EstadoMiembro estado, boolean enSupervivencia) {
        return estado == EstadoMiembro.VIVO && enSupervivencia;
    }
}
