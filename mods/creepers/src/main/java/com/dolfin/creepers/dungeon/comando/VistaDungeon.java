package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon.Modo;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Rechazo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/** Textos de los comandos de jugador, sin Minecraft para poder probarlos. */
final class VistaDungeon {
    private VistaDungeon() {}

    /** Cómo se ve una dungeon en {@code /dungeon list}; {@code clase} decide el color. */
    record EstadoVisible(Clase clase, String texto) {
        enum Clase {
            LIBRE,
            EN_PARTIDA,
            EN_FILA,
            REINICIANDO,
            BLOQUEADA,
            CERRADA
        }
    }

    /**
     * Estado visible para un jugador: Libre / En partida (mm:ss) / En fila (n grupos) / Bloqueada
     * para ti. Una dungeon en partida con fila muestra ambos datos; una libre con fila (a punto de
     * llamar al siguiente grupo) solo la fila.
     *
     * @param segundosPartida tiempo de la partida en curso, si la hay
     * @param gruposEnFila grupos esperando (#14)
     * @param bloqueada el jugador no tiene permiso, requisitos o cooldown
     */
    static EstadoVisible estado(
            EstadoDungeon estado,
            OptionalLong segundosPartida,
            int gruposEnFila,
            boolean bloqueada) {
        if (estado == EstadoDungeon.DESHABILITADA) {
            return new EstadoVisible(EstadoVisible.Clase.CERRADA, "Cerrada");
        }
        if (bloqueada) {
            return new EstadoVisible(EstadoVisible.Clase.BLOQUEADA, "Bloqueada para ti");
        }
        return switch (estado) {
            case LIBRE ->
                    gruposEnFila > 0
                            ? new EstadoVisible(EstadoVisible.Clase.EN_FILA, enFila(gruposEnFila))
                            : new EstadoVisible(EstadoVisible.Clase.LIBRE, "Libre");
            case EN_PARTIDA -> {
                String partida = "En partida (" + tiempo(segundosPartida.orElse(0)) + ")";
                yield new EstadoVisible(
                        EstadoVisible.Clase.EN_PARTIDA,
                        gruposEnFila > 0 ? partida + " · " + enFila(gruposEnFila) : partida);
            }
            case REINICIANDO -> new EstadoVisible(EstadoVisible.Clase.REINICIANDO, "Reiniciando");
            case DESHABILITADA -> throw new IllegalStateException("tratada arriba");
        };
    }

    /** Minutos y segundos como {@code mm:ss}; los minutos pueden pasar de 59. */
    static String tiempo(long segundos) {
        long s = Math.max(0, segundos);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Líneas de {@code /dungeon info} con el mejor tiempo del jugador en cada dificultad. */
    static List<String> info(DefinicionDungeon d, Map<Dificultad, Long> mejoresTiempos) {
        return info(d, mejoresTiempos(mejoresTiempos));
    }

    /** "facil 05:00 · hardcore 12:34", o "sin registro". */
    static String mejoresTiempos(Map<Dificultad, Long> mejoresTiempos) {
        return mejoresTiempos.isEmpty()
                ? "sin registro"
                : mejoresTiempos.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey().id() + " " + tiempo(e.getValue()))
                        .collect(Collectors.joining(" · "));
    }

    /** Líneas de {@code /dungeon info}. */
    static List<String> info(DefinicionDungeon d, OptionalLong mejorTiempo) {
        return info(d, mejorTiempo.isPresent() ? tiempo(mejorTiempo.getAsLong()) : "sin registro");
    }

    private static List<String> info(DefinicionDungeon d, String mejorTiempo) {
        List<String> lineas = new ArrayList<>();
        lineas.add(d.nombre() + " (" + d.id() + ")");
        lineas.add("Jugadores: " + jugadores(d));
        lineas.add(
                "Dificultades: "
                        + d.dificultades().stream()
                                .sorted(Comparator.naturalOrder())
                                .map(Dificultad::id)
                                .collect(Collectors.joining(", ")));
        lineas.add(
                "Requisitos: "
                        + (d.requisitos().isEmpty()
                                ? "ninguno"
                                : String.join(", ", d.requisitos())));
        lineas.add(
                "Cooldown: "
                        + (d.cooldownMinutos() == 0 ? "ninguno" : d.cooldownMinutos() + " min"));
        lineas.add("Tu mejor tiempo: " + mejorTiempo);
        return lineas;
    }

    /** Por qué no se pudo empezar, en lenguaje de jugador. */
    static String rechazo(Rechazo rechazo, Optional<DefinicionDungeon> dungeon) {
        String quien = rechazo.jugador().map(n -> n + ": ").orElse("");
        return quien
                + switch (rechazo.tipo()) {
                    case SISTEMA_DESACTIVADO -> "las dungeons están desactivadas";
                    case DUNGEON_DESCONOCIDA -> "esa dungeon no existe";
                    case DUNGEON_DESHABILITADA -> "la dungeon está cerrada";
                    case DUNGEON_OCUPADA -> "la dungeon está ocupada";
                    case DIFICULTAD_NO_PERMITIDA -> "esa dificultad no está disponible aquí";
                    case TAMANIO_GRUPO ->
                            dungeon.map(d -> "esta dungeon es de " + jugadores(d))
                                    .orElse("el tamaño del grupo no vale");
                    case JUGADOR_REPETIDO -> "jugador repetido";
                    case MODO_DE_JUEGO -> "hay que estar en supervivencia o aventura";
                    case JUGADOR_OCUPADO -> "ya está en una partida o en una fila";
                };
    }

    /** Aviso de {@code join … hardcore} y del abandono en hardcore. */
    static String avisoHardcore(int baneoMinutos) {
        return "Hardcore: si mueres pierdes todo y serás baneado " + baneoMinutos + " min.";
    }

    private static String jugadores(DefinicionDungeon d) {
        String rango =
                d.minJugadores() == d.maxJugadores()
                        ? String.valueOf(d.minJugadores())
                        : d.minJugadores() + "-" + d.maxJugadores();
        return (d.modo() == Modo.SOLO ? "solo" : "grupo") + " (" + rango + ")";
    }

    private static String enFila(int grupos) {
        return "En fila (" + grupos + (grupos == 1 ? " grupo)" : " grupos)");
    }
}
