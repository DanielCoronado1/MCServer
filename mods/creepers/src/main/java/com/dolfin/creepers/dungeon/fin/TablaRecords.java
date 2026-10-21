package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Todas las victorias registradas (#22) y las consultas de ranking, sin Minecraft. Guarda una
 * {@link Marca} por victoria, no solo las mejores: de ahí salen el top, el mejor tiempo de cada
 * jugador y si una victoria es la primera de alguien en una dificultad. Se serializa a JSON con
 * {@link #aJson} y {@link #deJson}.
 */
public final class TablaRecords {
    private static final Comparator<Marca> POR_TIEMPO =
            Comparator.comparingLong(Marca::tiempoMillis).thenComparingLong(Marca::fechaMillis);

    private final List<Marca> marcas = new ArrayList<>();

    public void anadir(Marca marca) {
        marcas.add(marca);
    }

    /** Mejor tiempo registrado de la dungeon en la dificultad. */
    public Optional<Marca> mejor(String dungeon, Dificultad dificultad) {
        return marcas.stream()
                .filter(m -> m.dungeon().equals(dungeon) && m.dificultad() == dificultad)
                .min(POR_TIEMPO);
    }

    /** Un tiempo es récord si no hay marca previa o si la mejora estrictamente. */
    public boolean esRecord(String dungeon, Dificultad dificultad, long tiempoMillis) {
        return mejor(dungeon, dificultad)
                .map(previa -> tiempoMillis < previa.tiempoMillis())
                .orElse(true);
    }

    /** El jugador aún no había ganado esta dungeon en esta dificultad. */
    public boolean esPrimeraVictoria(UUID jugador, String dungeon, Dificultad dificultad) {
        return marcas.stream()
                .noneMatch(
                        m ->
                                m.dungeon().equals(dungeon)
                                        && m.dificultad() == dificultad
                                        && m.incluye(jugador));
    }

    /**
     * Las {@code limite} mejores marcas de la dungeon, de menor a mayor tiempo. Sin dificultad
     * mezcla todas las dificultades.
     */
    public List<Marca> top(String dungeon, Optional<Dificultad> dificultad, int limite) {
        return marcas.stream()
                .filter(m -> m.dungeon().equals(dungeon))
                .filter(m -> dificultad.isEmpty() || m.dificultad() == dificultad.get())
                .sorted(POR_TIEMPO)
                .limit(limite)
                .toList();
    }

    /** Mejor tiempo del jugador en cada dificultad que ha ganado en la dungeon. */
    public Map<Dificultad, Long> mejoresTiempos(UUID jugador, String dungeon) {
        Map<Dificultad, Long> mejores = new EnumMap<>(Dificultad.class);
        for (Marca marca : marcas) {
            if (marca.dungeon().equals(dungeon) && marca.incluye(jugador)) {
                mejores.merge(marca.dificultad(), marca.tiempoMillis(), Math::min);
            }
        }
        return mejores;
    }

    public int tamano() {
        return marcas.size();
    }

    /** Documento guardado en disco: {@code {"marcas": [...]}}. */
    public JsonObject aJson() {
        JsonArray lista = new JsonArray();
        for (Marca marca : marcas) {
            JsonObject o = new JsonObject();
            o.addProperty("dungeon", marca.dungeon());
            o.addProperty("dificultad", marca.dificultad().id());
            o.addProperty("tiempoMillis", marca.tiempoMillis());
            o.addProperty("fechaMillis", marca.fechaMillis());
            JsonArray miembros = new JsonArray();
            for (Marca.Integrante m : marca.miembros()) {
                JsonObject j = new JsonObject();
                j.addProperty("id", m.id().toString());
                j.addProperty("nombre", m.nombre());
                miembros.add(j);
            }
            o.add("miembros", miembros);
            lista.add(o);
        }
        JsonObject raiz = new JsonObject();
        raiz.add("marcas", lista);
        return raiz;
    }

    /** Lee un documento; cada marca inválida se descarta y se informa en {@code avisos}. */
    public static TablaRecords deJson(JsonObject raiz, Consumer<String> avisos) {
        TablaRecords tabla = new TablaRecords();
        JsonElement lista = raiz.get("marcas");
        if (lista == null || !lista.isJsonArray()) {
            return tabla;
        }
        int indice = 0;
        for (JsonElement elemento : lista.getAsJsonArray()) {
            try {
                tabla.anadir(leerMarca(elemento.getAsJsonObject()));
            } catch (RuntimeException e) {
                avisos.accept("marca " + indice + " descartada: " + e);
            }
            indice++;
        }
        return tabla;
    }

    private static Marca leerMarca(JsonObject o) {
        Dificultad dificultad =
                Dificultad.deId(o.get("dificultad").getAsString())
                        .orElseThrow(() -> new IllegalArgumentException("dificultad desconocida"));
        List<Marca.Integrante> miembros = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("miembros")) {
            JsonObject m = e.getAsJsonObject();
            miembros.add(
                    new Marca.Integrante(
                            UUID.fromString(m.get("id").getAsString()),
                            m.get("nombre").getAsString()));
        }
        long tiempo = o.get("tiempoMillis").getAsLong();
        if (tiempo < 0 || miembros.isEmpty()) {
            throw new IllegalArgumentException("tiempo negativo o sin miembros");
        }
        return new Marca(
                o.get("dungeon").getAsString(),
                dificultad,
                miembros,
                tiempo,
                o.get("fechaMillis").getAsLong());
    }
}
