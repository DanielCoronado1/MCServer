package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.Dificultad;

import java.util.List;
import java.util.Locale;

/** Textos y formatos del fin de partida (#17) y de los récords (#22), sin Minecraft. */
public final class TextosFin {
    private TextosFin() {}

    /** {@code m:ss}, o {@code h:mm:ss} desde la hora. */
    public static String duracion(long segundos) {
        long s = Math.max(0, segundos);
        return s >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Como {@link #duracion} con milésimas ({@code 12:34.567}), para desempatar en el top. */
    public static String duracionExacta(long millis) {
        return duracion(millis / 1000) + String.format(Locale.ROOT, ".%03d", millis % 1000);
    }

    /** "Ana", "Ana y Beto", "Ana, Beto y Cleo". */
    public static String lista(List<String> nombres) {
        if (nombres.size() <= 1) {
            return String.join("", nombres);
        }
        String ultimo = nombres.getLast();
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + ultimo;
    }

    /** Nombre en mayúscula inicial: "Hardcore". */
    public static String dificultad(Dificultad dificultad) {
        String id = dificultad.id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** Título de la bossbar mientras se juega: nombre, dificultad y tiempo sobre el límite. */
    public static String barraJugando(
            String dungeon, Dificultad dificultad, long transcurridoSegundos, int limiteMinutos) {
        return dungeon
                + " · "
                + dificultad(dificultad)
                + " · "
                + duracion(transcurridoSegundos)
                + " / "
                + duracion(limiteMinutos * 60L);
    }

    /** Título de la bossbar con un aviso de tiempo restante delante. */
    public static String barraConAviso(String barra, int minutosRestantes) {
        return "¡Quedan " + minutosRestantes + " min! · " + barra;
    }

    /** Título de la bossbar durante la ventana de botín o la espera tras la derrota. */
    public static String barraCierre(String dungeon, boolean victoria, long segundosParaSalir) {
        return dungeon
                + " · "
                + (victoria ? "Victoria · recoge el botín" : "Derrota")
                + " · salida en "
                + Math.max(0, segundosParaSalir)
                + " s";
    }

    public static String avisoTiempo(int minutosRestantes) {
        return minutosRestantes == 1
                ? "Queda 1 minuto para que se agote el tiempo"
                : "Quedan " + minutosRestantes + " minutos para que se agote el tiempo";
    }

    /** Anuncio global de una victoria. */
    public static String anuncioVictoria(
            List<String> miembros, String dungeon, Dificultad dificultad, long tiempoSegundos) {
        return "¡"
                + lista(miembros)
                + " completó "
                + dungeon
                + " en "
                + dificultad(dificultad)
                + " en "
                + duracion(tiempoSegundos)
                + "!";
    }

    public static String anuncioRecord(String dungeon, Dificultad dificultad, long tiempoMillis) {
        return "¡Nuevo récord de "
                + dungeon
                + " ("
                + dificultad(dificultad)
                + "): "
                + duracionExacta(tiempoMillis)
                + "!";
    }

    public static String avisoBotin(int segundos) {
        return "El jefe ha caído. Tenéis " + segundos + " s para recoger el botín";
    }

    public static String avisoDerrota(int segundos) {
        return "Derrota. Saldréis de la dungeon en " + segundos + " s";
    }

    public static String avisoTiempoAgotado(int segundos) {
        return "Se agotó el tiempo. Saldréis de la dungeon en " + segundos + " s";
    }
}
