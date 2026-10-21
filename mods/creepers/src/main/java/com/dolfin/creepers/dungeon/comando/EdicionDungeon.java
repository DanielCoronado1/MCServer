package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.Zona;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cambios de {@code setzone}, {@code setentry}, {@code setexit} y {@code checkpoint} sobre una
 * {@link DefinicionDungeon}, validados antes de escribir en la config: lo que la recarga
 * descartaría (zona fuera de altura o solapada, puntos fuera de la zona) se rechaza aquí con un
 * mensaje para el administrador.
 */
final class EdicionDungeon {
    private EdicionDungeon() {}

    /** Resultado de una edición. */
    sealed interface Resultado {
        /** La definición a guardar y avisos para el administrador. */
        record Listo(DefinicionDungeon dungeon, List<String> avisos) implements Resultado {}

        /** No se puede aplicar; el texto explica por qué. */
        record Rechazado(String motivo) implements Resultado {}
    }

    /**
     * Fija la zona de {@code id}, creando la dungeon (deshabilitada, hasta que se defina su
     * entrada) si no existe. En una dungeon existente la entrada, los checkpoints y la salida que
     * queden fuera de la nueva zona se descartan y se avisa.
     *
     * @param todas todas las dungeons definidas, para comprobar que las zonas no se solapan
     */
    static Resultado definirZona(
            String id,
            Optional<DefinicionDungeon> actual,
            Zona zona,
            Collection<DefinicionDungeon> todas) {
        if (!DefinicionDungeon.idValido(id)) {
            return new Resultado.Rechazado("El id debe cumplir [a-z0-9_]{1,32}.");
        }
        if (zona.minY() < Zona.Y_MIN || zona.maxY() > Zona.Y_MAX) {
            return new Resultado.Rechazado(
                    "La altura de la zona debe estar entre "
                            + Zona.Y_MIN
                            + " y "
                            + Zona.Y_MAX
                            + ".");
        }
        Optional<DefinicionDungeon> solapada =
                todas.stream()
                        .filter(d -> !d.id().equals(id))
                        .filter(d -> d.zona().seSolapaCon(zona))
                        .findFirst();
        if (solapada.isPresent()) {
            return new Resultado.Rechazado(
                    "La zona se solapa con la de " + solapada.get().id() + ".");
        }
        return actual.map(d -> conZonaNueva(d, zona))
                .orElseGet(
                        () ->
                                new Resultado.Listo(
                                        CodecDefinicion.porDefecto(id, zona).conHabilitada(false),
                                        List.of(
                                                "Dungeon nueva, deshabilitada: marca la entrada con"
                                                        + " setentry y ábrela con enable.")));
    }

    /** Fija el punto de entrada, que debe quedar dentro de la zona. */
    static Resultado definirEntrada(DefinicionDungeon d, Punto punto) {
        return dentroDeLaZona(d, punto, "La entrada")
                .orElseGet(
                        () -> listo(d.conGeometria(d.zona(), punto, d.checkpoints(), d.salida())));
    }

    /** Fija la salida física de la dungeon, que debe quedar dentro de la zona. */
    static Resultado definirSalida(DefinicionDungeon d, Punto punto) {
        return dentroDeLaZona(d, punto, "La salida")
                .orElseGet(
                        () ->
                                listo(
                                        d.conGeometria(
                                                d.zona(),
                                                d.entrada(),
                                                d.checkpoints(),
                                                Optional.of(punto))));
    }

    /** Añade un checkpoint al final de la lista, dentro de la zona. */
    static Resultado anadirCheckpoint(DefinicionDungeon d, Punto punto) {
        return dentroDeLaZona(d, punto, "El checkpoint")
                .orElseGet(
                        () -> {
                            List<Punto> nuevos = new ArrayList<>(d.checkpoints());
                            nuevos.add(punto);
                            return listo(d.conGeometria(d.zona(), d.entrada(), nuevos, d.salida()));
                        });
    }

    /** Quita el checkpoint más cercano a {@code cerca}. */
    static Resultado quitarCheckpoint(DefinicionDungeon d, Punto cerca) {
        Optional<Punto> masCercano =
                d.checkpoints().stream().min(Comparator.comparingDouble(p -> distancia2(p, cerca)));
        if (masCercano.isEmpty()) {
            return new Resultado.Rechazado("La dungeon no tiene checkpoints.");
        }
        List<Punto> nuevos = new ArrayList<>(d.checkpoints());
        nuevos.remove(masCercano.get());
        return listo(d.conGeometria(d.zona(), d.entrada(), nuevos, d.salida()));
    }

    private static Resultado conZonaNueva(DefinicionDungeon d, Zona zona) {
        List<String> avisos = new ArrayList<>();
        Punto entrada = d.entrada();
        if (!zona.contiene(entrada)) {
            entrada = zona.centroInferior();
            avisos.add(
                    "La entrada quedaba fuera de la nueva zona: vuelve al centro; repite"
                            + " setentry.");
        }
        List<Punto> checkpoints = d.checkpoints().stream().filter(zona::contiene).toList();
        if (checkpoints.size() != d.checkpoints().size()) {
            avisos.add(
                    (d.checkpoints().size() - checkpoints.size())
                            + " checkpoint(s) quedaban fuera de la nueva zona y se quitan.");
        }
        Optional<Punto> salida = d.salida().filter(zona::contiene);
        if (salida.isEmpty() && d.salida().isPresent()) {
            avisos.add("La salida quedaba fuera de la nueva zona y se quita.");
        }
        return new Resultado.Listo(d.conGeometria(zona, entrada, checkpoints, salida), avisos);
    }

    private static Optional<Resultado> dentroDeLaZona(
            DefinicionDungeon d, Punto punto, String que) {
        return d.zona().contiene(punto)
                ? Optional.empty()
                : Optional.of(new Resultado.Rechazado(que + " debe estar dentro de la zona."));
    }

    private static Resultado listo(DefinicionDungeon d) {
        return new Resultado.Listo(d, List.of());
    }

    private static double distancia2(Punto a, Punto b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
