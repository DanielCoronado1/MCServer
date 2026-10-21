package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible;
import com.dolfin.creepers.dungeon.modelo.Dificultad;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Colocación de los iconos del menú de cofre de {@code /dungeon} (#23), sin Minecraft para poder
 * probarla. La pantalla de dungeons pone un icono por dungeon en orden; la de dificultades pone las
 * tres en la fila central y un botón para volver abajo.
 */
final class DisposicionMenu {
    static final int COLUMNAS = 9;
    static final int MIN_FILAS = 3;
    static final int MAX_FILAS = 6;

    /** Qué se dibuja en un hueco; el menú lo traduce a un objeto. */
    enum Icono {
        LIBRE,
        OCUPADA,
        EN_FILA,
        REINICIANDO,
        BLOQUEADA,
        CERRADA,
        FACIL,
        MEDIO,
        HARDCORE,
        VOLVER,
        RELLENO
    }

    private static final int FILA_DIFICULTADES = 1;

    private DisposicionMenu() {}

    /** Filas del cofre para {@code dungeons} iconos, entre 3 y 6. */
    static int filas(int dungeons) {
        int necesarias = (dungeons + COLUMNAS - 1) / COLUMNAS;
        return Math.min(MAX_FILAS, Math.max(MIN_FILAS, necesarias));
    }

    /** Dungeons que caben en el menú. */
    static int capacidad() {
        return MAX_FILAS * COLUMNAS;
    }

    /** Índice de la dungeon cuyo icono está en el hueco, si hay alguna de {@code total}. */
    static OptionalInt dungeonEn(int hueco, int total) {
        return hueco >= 0 && hueco < Math.min(total, capacidad())
                ? OptionalInt.of(hueco)
                : OptionalInt.empty();
    }

    /** Hueco de la dificultad en la pantalla de dificultades. */
    static int huecoDificultad(Dificultad dificultad) {
        int columna =
                switch (dificultad) {
                    case FACIL -> 2;
                    case MEDIO -> 4;
                    case HARDCORE -> 6;
                };
        return FILA_DIFICULTADES * COLUMNAS + columna;
    }

    /** Dificultad cuyo icono está en el hueco. */
    static Optional<Dificultad> dificultadEn(int hueco) {
        for (Dificultad dificultad : Dificultad.values()) {
            if (huecoDificultad(dificultad) == hueco) {
                return Optional.of(dificultad);
            }
        }
        return Optional.empty();
    }

    /** Hueco del botón de volver: centro de la última fila. */
    static int huecoVolver(int filas) {
        return (filas - 1) * COLUMNAS + COLUMNAS / 2;
    }

    /** Hueco de la cabecera con la dungeon elegida: centro de la primera fila. */
    static int huecoCabecera() {
        return COLUMNAS / 2;
    }

    /** Icono del estado visible de una dungeon. */
    static Icono icono(EstadoVisible.Clase clase) {
        return switch (clase) {
            case LIBRE -> Icono.LIBRE;
            case EN_PARTIDA -> Icono.OCUPADA;
            case EN_FILA -> Icono.EN_FILA;
            case REINICIANDO -> Icono.REINICIANDO;
            case BLOQUEADA -> Icono.BLOQUEADA;
            case CERRADA -> Icono.CERRADA;
        };
    }

    /** Icono de una dificultad. */
    static Icono icono(Dificultad dificultad) {
        return switch (dificultad) {
            case FACIL -> Icono.FACIL;
            case MEDIO -> Icono.MEDIO;
            case HARDCORE -> Icono.HARDCORE;
        };
    }
}
