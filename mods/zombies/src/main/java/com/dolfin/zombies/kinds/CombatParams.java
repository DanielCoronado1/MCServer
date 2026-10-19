package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ZombieKind;
import com.google.gson.JsonObject;
import java.util.Map;

/** Immutable, range-clamped views of each combat kind's ability JSON (parsed once per entity, not per tick). */
public final class CombatParams {
    private CombatParams() {}

    public record Escudero(double anguloBloqueo, double hachaSegundos, boolean bloqueaProyectiles, double probabilidadSoltarEscudo) {
        public static final String ABILITY = "bloquear";
        public static Escudero from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Escudero(Abilities.num(a, "anguloBloqueo", 90, 0, 360), Abilities.num(a, "hachaDesactivaEscudo", 5, 0, 600),
                Abilities.bool(a, "bloqueaProyectiles", true), Abilities.num(a, "probabilidadSoltarEscudo", 0.08, 0, 1));
        }
    }

    public record Hambriento(int hambrePorGolpe, float saturacionPorGolpe, int efectoHambreTicks, boolean distraidoPorComida, double radioComida) {
        public static final String ABILITY = "hambre";
        public static Hambriento from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Hambriento(Abilities.integer(a, "hambrePorGolpe", 3, 0, 20), (float) Abilities.num(a, "saturacionPorGolpe", 2, 0, 20),
                (int) Math.round(Abilities.num(a, "efectoHambre", 5, 0, 600) * 20), Abilities.bool(a, "distraidoPorComida", true),
                Abilities.num(a, "radioComida", 10, 1, 32));
        }
    }

    public record Congelado(int lentitudTicks, int nivelLentitud, boolean congelacionPolvo, int congelacionTicks, String bloqueAlMorir, int radioHielo) {
        public static final String ABILITY = "congelar";
        public static Congelado from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Congelado((int) Math.round(Abilities.num(a, "lentitudSegundos", 4, 0, 600) * 20), Abilities.integer(a, "nivelLentitud", 1, 1, 10),
                Abilities.bool(a, "congelacionPolvo", true), Abilities.integer(a, "congelacionTicks", 200, 0, 1000),
                Abilities.str(a, "bloqueAlMorir", "minecraft:ice"), Abilities.integer(a, "radioHielo", 1, 0, 3));
        }
    }

    public record Bilioso(double radioNube, int duracionNube, double resistenciaEmpuje, int nauseaTicks, int venenoTicks, int venenoNivel) {
        public static final String ABILITY = "nube";
        public static Bilioso from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Bilioso(Abilities.num(a, "radioNube", 3, 0.5, 16), Abilities.integer(a, "duracionNube", 100, 1, 6000),
                Abilities.num(a, "resistenciaEmpuje", 0.8, 0, 1), (int) Math.round(Abilities.num(a, "nauseaSegundos", 6, 0, 600) * 20),
                (int) Math.round(Abilities.num(a, "venenoSegundos", 2, 0, 600) * 20), Abilities.integer(a, "venenoNivel", 1, 1, 10));
        }
    }

    public record Abismo(double fuerzaArrastre, int duracionAgarre, int enfriamiento, int profundidadMinima, double danioParaSoltar,
                         double alcanceAgarre, int nivelFatiga) {
        public static final String ABILITY = "agarre";
        public static Abismo from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            return new Abismo(Abilities.num(a, "fuerzaArrastre", 0.12, 0, 1), Abilities.integer(a, "duracionAgarre", 60, 1, 1200),
                Abilities.integer(a, "enfriamiento", 200, 0, 72000), Abilities.integer(a, "profundidadMinima", 5, 1, 64),
                Abilities.num(a, "danioParaSoltar", 4, 0, 1000), Abilities.num(a, "alcanceAgarre", 1.8, 0.5, 6), Abilities.integer(a, "nivelFatiga", 1, 0, 5));
        }
    }

    public record Jinete(Map<String, Double> monturas, double vidaMontura) {
        public static final String ABILITY = "montura";
        public static Jinete from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            JsonObject m = a.has("monturas") && a.get("monturas").isJsonObject() ? a.getAsJsonObject("monturas") : null;
            return new Jinete(WeightedPick.parse(m), Abilities.num(a, "vidaMontura", 20, 1, 1024));
        }
    }

    /**
     * Final bounding box in blocks ({@code ancho} x {@code alto}) plus the configured {@code escala}.
     * Entity dimensions are scaled by the SCALE attribute afterwards, so the unscaled base is width/scale.
     */
    public record Enano(double ancho, double alto, double escala) {
        public static final String ABILITY = "cuerpoPequeno";
        public static final Enano DEFAULT = from(ZombieKind.ENANO.defaults());
        public static Enano from(JsonObject settings) {
            JsonObject a = Abilities.of(settings, ABILITY);
            double escala = Abilities.num(settings, "escala", 0.5, 0.05, 16);
            return new Enano(Abilities.num(a, "anchoHitbox", 0.5, 0.1, 4), Abilities.num(a, "altoHitbox", 0.9, 0.1, 4), escala);
        }
    }
}
