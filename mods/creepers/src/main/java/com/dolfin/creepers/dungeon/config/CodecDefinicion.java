package com.dolfin.creepers.dungeon.config;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon.Modo;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lee y escribe la lista {@code dungeons} de la config. Un campo inválido toma su valor por
 * defecto; una dungeon sin id válido, con id repetido o cuya zona se solape con otra se descarta
 * entera.
 */
public final class CodecDefinicion {
    /** Límite de coordenadas horizontales aceptadas en zonas y puntos. */
    private static final int COORDENADA_MAX = 1_000_000;

    private static final String PATRON_ID = "[a-z0-9_]{1,32}";

    private CodecDefinicion() {}

    /** Dungeon de ejemplo de la config por defecto: deshabilitada hasta construirla. */
    public static DefinicionDungeon ejemplo() {
        Zona zona = Zona.ranura(0);
        return porDefecto("ejemplo", zona).conHabilitada(false);
    }

    /**
     * Definición mínima para una dungeon nueva en {@code zona}, por ejemplo desde {@code setzone}.
     */
    public static DefinicionDungeon porDefecto(String id, Zona zona) {
        return new DefinicionDungeon(
                id,
                id,
                true,
                zona,
                zona.centroInferior(),
                List.of(),
                Optional.empty(),
                Modo.GRUPO,
                1,
                4,
                EnumSet.allOf(Dificultad.class),
                "dolfin.dungeon." + id,
                List.of(),
                30,
                0);
    }

    /** Interpreta el array {@code dungeons}; ausente = ninguna dungeon. */
    public static List<DefinicionDungeon> leerTodas(
            JsonElement elemento, List<DungeonsConfig.Problema> problemas) {
        if (elemento == null || elemento.isJsonNull()) {
            return List.of();
        }
        if (!elemento.isJsonArray()) {
            problemas.add(new DungeonsConfig.Problema("dungeons", "se esperaba una lista"));
            return List.of();
        }
        List<DefinicionDungeon> aceptadas = new ArrayList<>();
        JsonArray lista = elemento.getAsJsonArray();
        for (int i = 0; i < lista.size(); i++) {
            leerUna(lista.get(i), i, problemas)
                    .filter(d -> compatible(d, aceptadas, problemas))
                    .ifPresent(aceptadas::add);
        }
        return List.copyOf(aceptadas);
    }

    /** Serializa una definición con las mismas claves que lee {@link #leerTodas}. */
    public static JsonObject aJson(DefinicionDungeon d) {
        JsonObject json = new JsonObject();
        json.addProperty("id", d.id());
        json.addProperty("nombre", d.nombre());
        json.addProperty("habilitada", d.habilitada());
        json.add("zona", zonaAJson(d.zona()));
        json.add("entrada", puntoAJson(d.entrada()));
        JsonArray checkpoints = new JsonArray();
        d.checkpoints().forEach(p -> checkpoints.add(puntoAJson(p)));
        json.add("checkpoints", checkpoints);
        d.salida().ifPresent(p -> json.add("salida", puntoAJson(p)));
        json.addProperty("modo", d.modo().name().toLowerCase(Locale.ROOT));
        json.addProperty("minJugadores", d.minJugadores());
        json.addProperty("maxJugadores", d.maxJugadores());
        JsonArray dificultades = new JsonArray();
        for (Dificultad dificultad : Dificultad.values()) {
            if (d.dificultades().contains(dificultad)) {
                dificultades.add(dificultad.id());
            }
        }
        json.add("dificultades", dificultades);
        json.addProperty("permiso", d.permiso());
        JsonArray requisitos = new JsonArray();
        d.requisitos().forEach(requisitos::add);
        json.add("requisitos", requisitos);
        json.addProperty("cooldownMinutos", d.cooldownMinutos());
        json.addProperty("limiteMinutos", d.limiteMinutos());
        return json;
    }

    private static Optional<DefinicionDungeon> leerUna(
            JsonElement elemento, int indice, List<DungeonsConfig.Problema> problemas) {
        String ruta = "dungeons[" + indice + "]";
        if (!elemento.isJsonObject()) {
            problemas.add(new DungeonsConfig.Problema(ruta, "se esperaba un objeto; se descarta"));
            return Optional.empty();
        }
        JsonObject json = elemento.getAsJsonObject();
        JsonElement id = json.get("id");
        if (id == null
                || !id.isJsonPrimitive()
                || !id.getAsJsonPrimitive().isString()
                || !id.getAsString().matches(PATRON_ID)) {
            problemas.add(
                    new DungeonsConfig.Problema(
                            ruta + ".id", "se esperaba un id " + PATRON_ID + "; se descarta"));
            return Optional.empty();
        }
        return Optional.of(leerCampos(id.getAsString(), indice, json, problemas));
    }

    private static DefinicionDungeon leerCampos(
            String id, int indice, JsonObject json, List<DungeonsConfig.Problema> problemas) {
        LectorSeccion r = new LectorSeccion("dungeons." + id, json, problemas);
        DefinicionDungeon d = porDefecto(id, Zona.ranura(indice));
        r.texto("id", id);
        Zona zona = r.objeto("zona").flatMap(z -> leerZona(r, z)).orElse(d.zona());
        Punto entrada =
                r.objeto("entrada")
                        .flatMap(p -> leerPunto(r, "entrada", p))
                        .filter(p -> dentro(r, "entrada", zona, p))
                        .orElse(zona.centroInferior());
        List<Punto> checkpoints = leerCheckpoints(r, zona);
        Optional<Punto> salida =
                r.objeto("salida")
                        .flatMap(p -> leerPunto(r, "salida", p))
                        .filter(p -> dentro(r, "salida", zona, p));
        Modo modo = leerModo(r, d.modo());
        int[] jugadores = leerJugadores(r, modo, d);
        DefinicionDungeon definicion =
                new DefinicionDungeon(
                        id,
                        r.texto("nombre", d.nombre()),
                        r.booleano("habilitada", d.habilitada()),
                        zona,
                        entrada,
                        checkpoints,
                        salida,
                        modo,
                        jugadores[0],
                        jugadores[1],
                        leerDificultades(r, d.dificultades()),
                        r.texto("permiso", d.permiso()),
                        r.textos("requisitos", d.requisitos(), PATRON_ID),
                        r.entero("cooldownMinutos", d.cooldownMinutos(), 0, 10_080),
                        r.entero("limiteMinutos", d.limiteMinutos(), 0, 600));
        r.terminar();
        return definicion;
    }

    private static boolean compatible(
            DefinicionDungeon nueva,
            List<DefinicionDungeon> aceptadas,
            List<DungeonsConfig.Problema> problemas) {
        for (DefinicionDungeon otra : aceptadas) {
            if (otra.id().equals(nueva.id())) {
                problemas.add(
                        new DungeonsConfig.Problema(
                                "dungeons." + nueva.id(), "id repetido; se descarta"));
                return false;
            }
            if (otra.zona().seSolapaCon(nueva.zona())) {
                problemas.add(
                        new DungeonsConfig.Problema(
                                "dungeons." + nueva.id() + ".zona",
                                "se solapa con la zona de " + otra.id() + "; se descarta"));
                return false;
            }
        }
        return true;
    }

    private static Optional<Zona> leerZona(LectorSeccion padre, JsonObject json) {
        Optional<int[]> min = vector(json.get("min"));
        Optional<int[]> max = vector(json.get("max"));
        if (min.isEmpty() || max.isEmpty()) {
            return padre.rechazar(
                    "zona",
                    "se esperaba {\"min\": [x, y, z], \"max\": [x, y, z]}",
                    Optional.empty());
        }
        int[] a = min.get();
        int[] b = max.get();
        Zona zona = Zona.entre(a[0], a[1], a[2], b[0], b[1], b[2]);
        if (zona.minY() < Zona.Y_MIN || zona.maxY() > Zona.Y_MAX) {
            return padre.rechazar(
                    "zona",
                    "la altura debe estar en [" + Zona.Y_MIN + ", " + Zona.Y_MAX + "]",
                    Optional.empty());
        }
        return Optional.of(zona);
    }

    private static Optional<int[]> vector(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonArray() || elemento.getAsJsonArray().size() != 3) {
            return Optional.empty();
        }
        int[] valores = new int[3];
        for (int i = 0; i < 3; i++) {
            JsonElement e = elemento.getAsJsonArray().get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
                return Optional.empty();
            }
            double v = e.getAsDouble();
            if (v != Math.rint(v) || Math.abs(v) > COORDENADA_MAX) {
                return Optional.empty();
            }
            valores[i] = (int) v;
        }
        return Optional.of(valores);
    }

    private static Optional<Punto> leerPunto(LectorSeccion padre, String campo, JsonObject json) {
        double[] v = new double[5];
        String[] claves = {"x", "y", "z", "yaw", "pitch"};
        for (int i = 0; i < claves.length; i++) {
            JsonElement e = json.get(claves[i]);
            boolean opcional = i >= 3;
            if (e == null && opcional) {
                continue;
            }
            if (e == null
                    || !e.isJsonPrimitive()
                    || !e.getAsJsonPrimitive().isNumber()
                    || !Double.isFinite(e.getAsDouble())
                    || Math.abs(e.getAsDouble()) > COORDENADA_MAX) {
                return padre.rechazar(
                        campo, "se esperaba {x, y, z[, yaw, pitch]} numéricos", Optional.empty());
            }
            v[i] = e.getAsDouble();
        }
        return Optional.of(new Punto(v[0], v[1], v[2], (float) v[3], (float) v[4]));
    }

    private static boolean dentro(LectorSeccion r, String campo, Zona zona, Punto punto) {
        return zona.contiene(punto)
                || r.rechazar(campo, "queda fuera de la zona de la dungeon", false);
    }

    private static List<Punto> leerCheckpoints(LectorSeccion r, Zona zona) {
        List<Punto> puntos = new ArrayList<>();
        JsonArray lista = r.lista("checkpoints").orElse(new JsonArray());
        for (int i = 0; i < lista.size(); i++) {
            String campo = "checkpoints[" + i + "]";
            JsonElement e = lista.get(i);
            Optional<Punto> punto =
                    e.isJsonObject()
                            ? leerPunto(r, campo, e.getAsJsonObject())
                            : r.rechazar(campo, "se esperaba un objeto", Optional.empty());
            punto.filter(p -> dentro(r, campo, zona, p)).ifPresent(puntos::add);
        }
        return puntos;
    }

    private static Modo leerModo(LectorSeccion r, Modo porDefecto) {
        String texto = r.texto("modo", porDefecto.name().toLowerCase(Locale.ROOT));
        return switch (texto) {
            case "solo" -> Modo.SOLO;
            case "grupo" -> Modo.GRUPO;
            default -> r.rechazar("modo", "se esperaba solo o grupo", porDefecto);
        };
    }

    /** Devuelve {min, max}; en modo solo ambos valen 1. */
    private static int[] leerJugadores(LectorSeccion r, Modo modo, DefinicionDungeon d) {
        int min = r.entero("minJugadores", d.minJugadores(), 1, 16);
        int max = r.entero("maxJugadores", d.maxJugadores(), 1, 16);
        if (modo == Modo.SOLO) {
            return new int[] {1, 1};
        }
        if (min > max) {
            return r.rechazar(
                    "minJugadores",
                    "minJugadores (" + min + ") supera a maxJugadores (" + max + ")",
                    new int[] {d.minJugadores(), d.maxJugadores()});
        }
        return new int[] {min, max};
    }

    private static Set<Dificultad> leerDificultades(LectorSeccion r, Set<Dificultad> porDefecto) {
        List<String> ids = r.textos("dificultades", List.of(), "facil|medio|hardcore");
        Set<Dificultad> resultado = new HashSet<>();
        ids.forEach(id -> Dificultad.deId(id).ifPresent(resultado::add));
        return resultado.isEmpty() ? porDefecto : resultado;
    }

    private static JsonObject zonaAJson(Zona zona) {
        JsonObject json = new JsonObject();
        JsonArray min = new JsonArray();
        min.add(zona.minX());
        min.add(zona.minY());
        min.add(zona.minZ());
        JsonArray max = new JsonArray();
        max.add(zona.maxX());
        max.add(zona.maxY());
        max.add(zona.maxZ());
        json.add("min", min);
        json.add("max", max);
        return json;
    }

    private static JsonObject puntoAJson(Punto punto) {
        JsonObject json = new JsonObject();
        json.addProperty("x", punto.x());
        json.addProperty("y", punto.y());
        json.addProperty("z", punto.z());
        json.addProperty("yaw", punto.yaw());
        json.addProperty("pitch", punto.pitch());
        return json;
    }
}
