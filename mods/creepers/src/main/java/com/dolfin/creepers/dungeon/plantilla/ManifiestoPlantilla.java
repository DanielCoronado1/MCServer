package com.dolfin.creepers.dungeon.plantilla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code plantilla.json}: describe una plantilla guardada. Los trozos no se listan: salen de {@link
 * Troceado#trozos} con el tamaño y el lado guardados.
 *
 * @param version formato del manifiesto ({@value #VERSION})
 * @param dungeon id de la dungeon
 * @param zona zona al guardar, en coordenadas absolutas. Todo se coloca relativo a la esquina
 *     mínima de la zona actual, que debe medir lo mismo
 * @param lado lado de los trozos
 * @param entidades entidades marcadas guardadas en {@code entidades.nbt}
 * @param guardadaMillis hora real del guardado (epoch ms)
 * @param marcadores marcadores de mobs, en orden de guardado
 */
public record ManifiestoPlantilla(
        int version,
        String dungeon,
        Caja zona,
        int lado,
        int entidades,
        long guardadaMillis,
        List<DatosMarcador> marcadores) {
    public static final int VERSION = 1;

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public ManifiestoPlantilla {
        if (lado < 1) {
            throw new IllegalArgumentException("lado no positivo");
        }
        marcadores = List.copyOf(marcadores);
    }

    /** Trozos de la plantilla, relativos a la esquina mínima de la zona. */
    public List<Troceado.Trozo> trozos() {
        return Troceado.trozos(zona.ancho(), zona.alto(), zona.largo(), lado);
    }

    /** La plantilla encaja en una zona de este tamaño. */
    public boolean encajaEn(Caja otra) {
        return otra.ancho() == zona.ancho()
                && otra.alto() == zona.alto()
                && otra.largo() == zona.largo();
    }

    public String aJson() {
        JsonObject raiz = new JsonObject();
        raiz.addProperty("version", version);
        raiz.addProperty("dungeon", dungeon);
        raiz.add("zona", GSON.toJsonTree(zona));
        raiz.addProperty("lado", lado);
        raiz.addProperty("entidades", entidades);
        raiz.addProperty("guardadaMillis", guardadaMillis);
        JsonArray lista = new JsonArray();
        marcadores.forEach(m -> lista.add(marcadorAJson(m)));
        raiz.add("marcadores", lista);
        return GSON.toJson(raiz);
    }

    /**
     * Lee un manifiesto. Lanza {@link IllegalArgumentException} si falta algo, es de otra versión o
     * tiene valores inválidos: una plantilla a medias no se coloca.
     */
    public static ManifiestoPlantilla deJson(String json) {
        try {
            JsonObject raiz = JsonParser.parseString(json).getAsJsonObject();
            int version = raiz.get("version").getAsInt();
            if (version != VERSION) {
                throw new IllegalArgumentException("versión de plantilla desconocida: " + version);
            }
            List<DatosMarcador> marcadores = new ArrayList<>();
            for (JsonElement m : raiz.getAsJsonArray("marcadores")) {
                marcadores.add(marcadorDeJson(m.getAsJsonObject()));
            }
            return new ManifiestoPlantilla(
                    version,
                    raiz.get("dungeon").getAsString(),
                    zonaDeJson(raiz.getAsJsonObject("zona")),
                    raiz.get("lado").getAsInt(),
                    raiz.get("entidades").getAsInt(),
                    raiz.get("guardadaMillis").getAsLong(),
                    marcadores);
        } catch (JsonParseException | IllegalStateException | NullPointerException e) {
            throw new IllegalArgumentException("plantilla.json mal formado: " + e.getMessage(), e);
        }
    }

    private static Caja zonaDeJson(JsonObject o) {
        return new Caja(
                o.get("minX").getAsInt(),
                o.get("minY").getAsInt(),
                o.get("minZ").getAsInt(),
                o.get("maxX").getAsInt(),
                o.get("maxY").getAsInt(),
                o.get("maxZ").getAsInt());
    }

    private static JsonObject marcadorAJson(DatosMarcador m) {
        JsonObject o = new JsonObject();
        o.add("pos", GSON.toJsonTree(new int[] {m.x(), m.y(), m.z()}));
        o.addProperty("entidad", m.entidad());
        o.addProperty("cantidad", m.cantidad());
        if (!m.nbt().isEmpty()) {
            o.addProperty("nbt", m.nbt());
        }
        return o;
    }

    private static DatosMarcador marcadorDeJson(JsonObject o) {
        int[] pos = GSON.fromJson(o.get("pos"), int[].class);
        if (pos.length != 3) {
            throw new IllegalArgumentException("pos de marcador sin tres coordenadas");
        }
        return new DatosMarcador(
                pos[0],
                pos[1],
                pos[2],
                o.get("entidad").getAsString(),
                o.get("cantidad").getAsInt(),
                o.has("nbt") ? o.get("nbt").getAsString() : "");
    }
}
