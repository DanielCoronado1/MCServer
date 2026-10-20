package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ZombieKind;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Immutable, range-clamped views of each combat kind's ability JSON (parsed once per entity, not
 * per tick).
 */
public final class CombatParams {
    private CombatParams() {}

    public record Escudero(
            double anguloBloqueo,
            double hachaSegundos,
            boolean bloqueaProyectiles,
            double probabilidadSoltarEscudo) {
        public static final String ABILITY = "bloquear";

        public static Escudero from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Escudero(
                    Abilities.num(a, "anguloBloqueo", 90, 0, 360),
                    Abilities.num(a, "hachaDesactivaEscudo", 5, 0, 600),
                    Abilities.bool(a, "bloqueaProyectiles", true),
                    Abilities.num(a, "probabilidadSoltarEscudo", 0.08, 0, 1));
        }
    }

    public record Hambriento(
            int hambrePorGolpe,
            float saturacionPorGolpe,
            int efectoHambreTicks,
            boolean distraidoPorComida,
            double radioComida) {
        public static final String ABILITY = "hambre";

        public static Hambriento from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Hambriento(
                    Abilities.integer(a, "hambrePorGolpe", 3, 0, 20),
                    (float) Abilities.num(a, "saturacionPorGolpe", 2, 0, 20),
                    (int) Math.round(Abilities.num(a, "efectoHambre", 5, 0, 600) * 20),
                    Abilities.bool(a, "distraidoPorComida", true),
                    Abilities.num(a, "radioComida", 10, 1, 32));
        }
    }

    public record Congelado(
            int lentitudTicks,
            int nivelLentitud,
            boolean congelacionPolvo,
            int congelacionTicks,
            String bloqueAlMorir,
            int radioHielo) {
        public static final String ABILITY = "congelar";

        public static Congelado from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Congelado(
                    (int) Math.round(Abilities.num(a, "lentitudSegundos", 4, 0, 600) * 20),
                    Abilities.integer(a, "nivelLentitud", 1, 1, 10),
                    Abilities.bool(a, "congelacionPolvo", true),
                    Abilities.integer(a, "congelacionTicks", 200, 0, 1000),
                    Abilities.str(a, "bloqueAlMorir", "minecraft:ice"),
                    Abilities.integer(a, "radioHielo", 1, 0, 3));
        }
    }

    public record Bilioso(
            double radioNube,
            int duracionNube,
            double resistenciaEmpuje,
            int nauseaTicks,
            int venenoTicks,
            int venenoNivel) {
        public static final String ABILITY = "nube";

        public static Bilioso from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Bilioso(
                    Abilities.num(a, "radioNube", 3, 0.5, 16),
                    Abilities.integer(a, "duracionNube", 100, 1, 6000),
                    Abilities.num(a, "resistenciaEmpuje", 0.8, 0, 1),
                    (int) Math.round(Abilities.num(a, "nauseaSegundos", 6, 0, 600) * 20),
                    (int) Math.round(Abilities.num(a, "venenoSegundos", 2, 0, 600) * 20),
                    Abilities.integer(a, "venenoNivel", 1, 1, 10));
        }
    }

    public record Abismo(
            double fuerzaArrastre,
            int duracionAgarre,
            int enfriamiento,
            int profundidadMinima,
            double danioParaSoltar,
            double alcanceAgarre,
            int nivelFatiga) {
        public static final String ABILITY = "agarre";

        public static Abismo from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Abismo(
                    Abilities.num(a, "fuerzaArrastre", 0.12, 0, 1),
                    Abilities.integer(a, "duracionAgarre", 60, 1, 1200),
                    Abilities.integer(a, "enfriamiento", 200, 0, 72000),
                    Abilities.integer(a, "profundidadMinima", 5, 1, 64),
                    Abilities.num(a, "danioParaSoltar", 4, 0, 1000),
                    Abilities.num(a, "alcanceAgarre", 1.8, 0.5, 6),
                    Abilities.integer(a, "nivelFatiga", 1, 0, 5));
        }
    }

    public record Jinete(Map<String, Double> monturas, double vidaMontura) {
        public static final String ABILITY = "montura";

        public static Jinete from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            JsonObject m =
                    a.has("monturas") && a.get("monturas").isJsonObject()
                            ? a.getAsJsonObject("monturas")
                            : null;
            return new Jinete(WeightedPick.parse(m), Abilities.num(a, "vidaMontura", 20, 1, 1024));
        }
    }

    /**
     * Final bounding box in blocks ({@code ancho} x {@code alto}) plus the configured {@code
     * escala}. Entity dimensions are scaled by the SCALE attribute afterwards, so the unscaled base
     * is width/scale.
     */
    public record Enano(double ancho, double alto, double escala) {
        public static final String ABILITY = "cuerpoPequeno";
        public static final Enano DEFAULT = from(ZombieKind.ENANO.defaults());

        public static Enano from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            double escala = Abilities.num(settings, "escala", 0.5, 0.05, 16);
            return new Enano(
                    Abilities.num(a, "anchoHitbox", 0.5, 0.1, 4),
                    Abilities.num(a, "altoHitbox", 0.9, 0.1, 4),
                    escala);
        }
    }

    public record Minero(
            float durezaMax,
            int tiempoPorBloque,
            double distanciaMax,
            boolean soltar,
            int maxPorMinuto,
            List<String> listaNegra) {
        public static final String ABILITY = "romperBloques";

        public static Minero from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Minero(
                    (float) Abilities.num(a, "durezaMax", 3, 0, 1000),
                    Abilities.integer(a, "tiempoPorBloque", 40, 1, 72000),
                    Abilities.num(a, "distanciaMaxRompiendo", 16, 1, 64),
                    Abilities.bool(a, "soltarBloques", false),
                    Abilities.integer(a, "maxBloquesPorMinuto", 20, 1, 1000),
                    Abilities.stringList(a, "listaNegra"));
        }
    }

    public record Bombero(double probabilidadPorPaso, float quemarJugador, int segundosEnAgua) {
        public static final String ABILITY = "quemarBloques";

        public static Bombero from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Bombero(
                    Abilities.num(a, "probabilidadPorPaso", 0.08, 0, 1),
                    (float) Abilities.num(a, "quemarJugador", 4, 0, 1000),
                    Abilities.integer(a, "segundosEnAgua", 3, 0, 600));
        }
    }

    public record Bruja(
            float curacion,
            double radioCuracion,
            int enfriamientoPocion,
            double distanciaPreferida,
            String efectoLanzado,
            int duracionEfectoSegundos) {
        public static final String ABILITY = "curacion";

        public static Bruja from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Bruja(
                    (float) Abilities.num(a, "curacion", 6, 0, 1000),
                    Abilities.num(a, "radioCuracion", 8, 1, 32),
                    Abilities.integer(a, "enfriamientoPocion", 80, 20, 72000),
                    Abilities.num(a, "distanciaPreferida", 8, 1, 24),
                    Abilities.str(a, "efectoLanzado", "minecraft:slowness"),
                    Abilities.integer(a, "duracionEfecto", 6, 1, 600));
        }
    }

    public record Griton(
            double radioLlamada,
            int enfriamiento,
            int maxZombiesLlamados,
            boolean incluyeVanilla,
            int refuerzosLunaDeSangre) {
        public static final String ABILITY = "llamada";

        public static Griton from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Griton(
                    Abilities.num(a, "radioLlamada", 30, 1, 64),
                    Abilities.integer(a, "enfriamiento", 400, 20, 72000),
                    Abilities.integer(a, "maxZombiesLlamados", 15, 1, 64),
                    Abilities.bool(a, "incluyeVanilla", true),
                    Abilities.integer(a, "refuerzosLunaDeSangre", 2, 0, 15));
        }
    }

    public record Granjero(int radioBusqueda, int maxBloquesPorMinuto, List<String> animales) {
        public static final String CROPS = "destruirCultivos";
        public static final String ANIMALS = "atacarAnimales";

        public static Granjero from(JsonObject settings) {
            JsonObject c = Abilities.of(settings, CROPS);
            JsonObject a = Abilities.of(settings, ANIMALS);
            return new Granjero(
                    Abilities.integer(c, "radioBusqueda", 24, 1, 32),
                    Abilities.integer(c, "maxBloquesPorMinuto", 30, 1, 1000),
                    Abilities.stringList(a, "animales"));
        }
    }

    public record Ladron(
            double probabilidadRobo,
            double velocidadHuida,
            List<String> listaNegra,
            boolean soloHotbar,
            int minutosParaSoltar) {
        public static final String ABILITY = "robar";

        public static Ladron from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Ladron(
                    Abilities.num(a, "probabilidadRobo", 0.35, 0, 1),
                    Abilities.num(a, "velocidadHuida", 1.6, 0.1, 10),
                    Abilities.stringList(a, "listaNegra"),
                    Abilities.bool(a, "soloHotbar", true),
                    Abilities.integer(a, "minutosParaSoltar", 10, 1, 1000));
        }
    }

    /**
     * Contagiado's three cooperating abilities. Infection chance and duration come from {@code
     * convertirAlMorir}; cures prefer the newer {@code infeccion} block; chest settings prefer the
     * top-level {@code cofre} over the legacy one nested in {@code convertirAlMorir}, and either
     * block can switch the chest off.
     */
    public record Contagiado(
            boolean conversionActivo,
            boolean infectionActivo,
            double probabilidadInfeccion,
            int duracionInfeccion,
            List<String> curas,
            boolean cofreActivo,
            int minutosProteccion,
            int radioBusqueda) {
        public static final String CONVERSION = "convertirAlMorir";
        public static final String INFECTION = "infeccion";
        public static final String CHEST = "cofre";
        static final List<String> DEFAULT_CURES =
                List.of(
                        "minecraft:golden_apple",
                        "minecraft:enchanted_golden_apple",
                        "minecraft:milk_bucket");

        public static Contagiado from(JsonObject settings) {
            JsonObject conv = Abilities.of(settings, CONVERSION);
            JsonObject legacyChest = child(conv, CHEST);
            JsonObject chest =
                    present(settings, CHEST) ? Abilities.of(settings, CHEST) : legacyChest;
            JsonObject cures =
                    present(settings, INFECTION) ? Abilities.of(settings, INFECTION) : conv;
            return new Contagiado(
                    present(settings, CONVERSION)
                            && Abilities.bool(conv, "activo", true)
                            && Abilities.bool(child(conv, CONVERSION), "activo", true),
                    Abilities.bool(conv, "activo", false)
                            && Abilities.bool(Abilities.of(settings, INFECTION), "activo", true),
                    Abilities.num(conv, "probabilidadInfeccion", 0.25, 0, 1),
                    Abilities.integer(conv, "duracionInfeccion", 6000, 1, 1200000),
                    Abilities.stringList(cures, "curas", DEFAULT_CURES),
                    Abilities.bool(Abilities.of(settings, CHEST), "activo", true)
                            && Abilities.bool(legacyChest, "activo", true),
                    Abilities.integer(chest, "minutosProteccion", 15, 0, 10000),
                    Abilities.integer(chest, "radioBusqueda", 5, 0, 32));
        }

        private static boolean present(JsonObject settings, String ability) {
            return settings != null
                    && settings.get("habilidades") instanceof JsonObject h
                    && h.get(ability) instanceof JsonObject;
        }

        private static JsonObject child(JsonObject o, String key) {
            return o.get(key) instanceof JsonObject c ? c : new JsonObject();
        }
    }

    public record Mimo(
            double probabilidadAparecer, int minutosVentana, boolean copiarEncantamientos) {
        public static final String ABILITY = "aparicionPorMuerte";

        public static Mimo from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Mimo(
                    Abilities.num(a, "probabilidadAparecer", 0.30, 0, 1),
                    Abilities.integer(a, "minutosVentana", 10, 1, 1000),
                    Abilities.bool(a, "copiarEncantamientos", false));
        }
    }

    public record Explorador(
            double visibleAlRecibirDanioSegundos, boolean pasosSilenciosos, boolean revelarConLuz) {
        public static final String ABILITY = "invisibilidad";

        public static Explorador from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Explorador(
                    Abilities.num(a, "visibleAlRecibirDanio", 3, 0, 600),
                    Abilities.bool(a, "pasosSilenciosos", true),
                    Abilities.bool(a, "revelarConLuz", true));
        }

        public long visibleAlRecibirDanioTicks() {
            return (long) (visibleAlRecibirDanioSegundos * 20);
        }
    }
}
