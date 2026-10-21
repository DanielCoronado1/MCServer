package com.dolfin.creepers.dungeon.modelo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Definición inmutable de una dungeon tal como está en {@code config/dolfin-dungeons.json}. Zonas,
 * entradas y checkpoints se marcan desde el juego con los comandos de admin (#13).
 *
 * @param id identificador en minúsculas ({@code [a-z0-9_]+}), único
 * @param nombre nombre visible para los jugadores
 * @param habilitada {@code false} deja la dungeon en {@link EstadoDungeon#DESHABILITADA}
 * @param zona caja que ocupa en la dimensión; no se solapa con otras
 * @param entrada donde aparece el grupo al empezar (y al reconectar sin checkpoint)
 * @param checkpoints puntos de reaparición en orden de avance (#16)
 * @param salida punto opcional dentro de la zona que marca la salida física (portal de victoria);
 *     sacar a un jugador siempre lo devuelve a su posición guardada
 * @param modo solo o grupo
 * @param minJugadores mínimo de jugadores para empezar
 * @param maxJugadores máximo de jugadores (1 en modo solo)
 * @param dificultades dificultades admitidas
 * @param permiso nodo de permiso para entrar (#14), por defecto {@code dolfin.dungeon.<id>}
 * @param requisitos ids de dungeons que hay que haber completado antes (#14)
 * @param cooldownMinutos espera por jugador tras terminar una partida (#14)
 * @param limiteMinutos límite propio de la partida; 0 usa {@code partida.limiteMinutos}
 */
public record DefinicionDungeon(
        String id,
        String nombre,
        boolean habilitada,
        Zona zona,
        Punto entrada,
        List<Punto> checkpoints,
        Optional<Punto> salida,
        Modo modo,
        int minJugadores,
        int maxJugadores,
        Set<Dificultad> dificultades,
        String permiso,
        List<String> requisitos,
        int cooldownMinutos,
        int limiteMinutos) {

    /** Si la dungeon se juega en solitario o en grupo. */
    public enum Modo {
        SOLO,
        GRUPO
    }

    public DefinicionDungeon {
        checkpoints = List.copyOf(checkpoints);
        dificultades = Set.copyOf(dificultades);
        requisitos = List.copyOf(requisitos);
    }

    /** Patrón que debe cumplir un id de dungeon. */
    public static boolean idValido(String id) {
        return id != null && id.matches("[a-z0-9_]{1,32}");
    }

    /** Copia con otro valor de {@code habilitada}, para {@code enable}/{@code disable} (#13). */
    public DefinicionDungeon conHabilitada(boolean valor) {
        return new DefinicionDungeon(
                id,
                nombre,
                valor,
                zona,
                entrada,
                checkpoints,
                salida,
                modo,
                minJugadores,
                maxJugadores,
                dificultades,
                permiso,
                requisitos,
                cooldownMinutos,
                limiteMinutos);
    }

    /**
     * Punto donde reaparece o reconecta quien alcanzó el checkpoint {@code indice} (−1 = entrada).
     */
    public Punto puntoDeCheckpoint(int indice) {
        return indice >= 0 && indice < checkpoints.size() ? checkpoints.get(indice) : entrada;
    }
}
