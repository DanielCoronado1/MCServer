package com.dolfin.creepers.dungeon.plantilla;

import java.util.BitSet;

/**
 * Qué posiciones de un trozo tienen bloque en la plantilla (el resto es aire). El reinicio solo
 * vacía lo que la plantilla no va a sustituir: así un bloque que ya está bien no se quita y se pone
 * otra vez.
 */
public final class MapaOcupacion {
    private final int ancho;
    private final int alto;
    private final int largo;
    private final BitSet ocupados;

    /** Mapa vacío para un trozo de {@code ancho × alto × largo}. */
    public MapaOcupacion(int ancho, int alto, int largo) {
        if (ancho < 1 || alto < 1 || largo < 1) {
            throw new IllegalArgumentException("tamaño no positivo");
        }
        this.ancho = ancho;
        this.alto = alto;
        this.largo = largo;
        this.ocupados = new BitSet(ancho * alto * largo);
    }

    /** Marca una posición relativa al trozo; las de fuera se ignoran. */
    public void marcar(int x, int y, int z) {
        if (dentro(x, y, z)) {
            ocupados.set(indice(x, y, z));
        }
    }

    /** La plantilla pone un bloque en esta posición relativa al trozo. */
    public boolean ocupado(int x, int y, int z) {
        return dentro(x, y, z) && ocupados.get(indice(x, y, z));
    }

    public int cuenta() {
        return ocupados.cardinality();
    }

    private boolean dentro(int x, int y, int z) {
        return x >= 0 && x < ancho && y >= 0 && y < alto && z >= 0 && z < largo;
    }

    private int indice(int x, int y, int z) {
        return (y * largo + z) * ancho + x;
    }
}
