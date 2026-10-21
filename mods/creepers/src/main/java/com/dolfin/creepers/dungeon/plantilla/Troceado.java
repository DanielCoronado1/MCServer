package com.dolfin.creepers.dungeon.plantilla;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide una zona en trozos de plantilla y cada trozo en capas que caben en el presupuesto de un
 * tick. Es determinista: guardar y reiniciar calculan los mismos trozos a partir del tamaño de la
 * zona, así el manifiesto no tiene que listarlos.
 */
public final class Troceado {
    /**
     * Lado máximo de un trozo: el límite de un bloque de estructuras vanilla, para poder cargar un
     * trozo suelto con uno y revisarlo.
     */
    public static final int LADO_MAXIMO = 48;

    private Troceado() {}

    /**
     * Un trozo de la zona.
     *
     * @param i índice del trozo en x
     * @param j índice en y
     * @param k índice en z; los tres forman el nombre del archivo
     * @param caja posición relativa a la esquina mínima de la zona
     */
    public record Trozo(int i, int j, int k, Caja caja) {
        /** Nombre del archivo del trozo dentro de {@code trozos/}. */
        public String archivo() {
            return i + "_" + j + "_" + k + ".nbt";
        }
    }

    /**
     * Trozos de como mucho {@code lado}³ que cubren exactamente una zona de {@code ancho × alto ×
     * largo}, en coordenadas relativas a su esquina mínima. Orden: y ascendente (los cimientos
     * primero), luego x y z.
     */
    public static List<Trozo> trozos(int ancho, int alto, int largo, int lado) {
        if (ancho < 1 || alto < 1 || largo < 1 || lado < 1) {
            throw new IllegalArgumentException("tamaño o lado no positivo");
        }
        List<Trozo> trozos = new ArrayList<>();
        for (int j = 0; j * lado < alto; j++) {
            for (int i = 0; i * lado < ancho; i++) {
                for (int k = 0; k * lado < largo; k++) {
                    trozos.add(
                            new Trozo(
                                    i,
                                    j,
                                    k,
                                    new Caja(
                                            i * lado,
                                            j * lado,
                                            k * lado,
                                            Math.min(ancho, (i + 1) * lado) - 1,
                                            Math.min(alto, (j + 1) * lado) - 1,
                                            Math.min(largo, (k + 1) * lado) - 1)));
                }
            }
        }
        return trozos;
    }

    /**
     * Capas horizontales de {@code caja}, de abajo arriba, de como mucho {@code presupuesto}
     * bloques cada una. Una capa nunca baja de un bloque de alto: si la planta de la caja supera el
     * presupuesto, cada capa la ocupa entera (con {@link #LADO_MAXIMO} y un presupuesto ≥ 2304 no
     * pasa).
     */
    public static List<Caja> capas(Caja caja, int presupuesto) {
        if (presupuesto < 1) {
            throw new IllegalArgumentException("presupuesto no positivo");
        }
        int planta = caja.ancho() * caja.largo();
        int altoCapa = Math.max(1, presupuesto / planta);
        List<Caja> capas = new ArrayList<>();
        for (int y = caja.minY(); y <= caja.maxY(); y += altoCapa) {
            capas.add(
                    new Caja(
                            caja.minX(),
                            y,
                            caja.minZ(),
                            caja.maxX(),
                            Math.min(caja.maxY(), y + altoCapa - 1),
                            caja.maxZ()));
        }
        return capas;
    }
}
