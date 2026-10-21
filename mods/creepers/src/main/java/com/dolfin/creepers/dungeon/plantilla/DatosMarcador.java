package com.dolfin.creepers.dungeon.plantilla;

/**
 * Lo que dice un marcador de mobs de la plantilla: qué aparece, cuántos y dónde. Se lee del bloque
 * al guardar la plantilla y se usa al empezar cada partida.
 *
 * @param x posición relativa a la esquina mínima de la zona
 * @param y posición relativa
 * @param z posición relativa
 * @param entidad id del tipo de entidad, por ejemplo {@code minecraft:zombie}
 * @param cantidad cuántos aparecen antes del escalado de #18, entre 1 y {@link #CANTIDAD_MAXIMA}
 * @param nbt datos extra de la entidad en SNBT (equipo, nombre…); vacío si no hay
 */
public record DatosMarcador(int x, int y, int z, String entidad, int cantidad, String nbt) {
    public static final int CANTIDAD_MAXIMA = 64;

    private static final String PATRON_ID = "[a-z0-9_.-]+:[a-z0-9/._-]+";

    public DatosMarcador {
        if (!idValido(entidad)) {
            throw new IllegalArgumentException("id de entidad inválido: " + entidad);
        }
        if (cantidad < 1 || cantidad > CANTIDAD_MAXIMA) {
            throw new IllegalArgumentException("cantidad fuera de rango: " + cantidad);
        }
        nbt = nbt == null ? "" : nbt;
    }

    /** {@code espacio:ruta} en minúsculas. */
    public static boolean idValido(String id) {
        return id != null && id.matches(PATRON_ID);
    }

    /** Cantidad recortada al rango admitido, para leer valores escritos a mano. */
    public static int cantidadValida(int cantidad) {
        return Math.clamp(cantidad, 1, CANTIDAD_MAXIMA);
    }
}
