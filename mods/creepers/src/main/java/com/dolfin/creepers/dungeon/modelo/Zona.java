package com.dolfin.creepers.dungeon.modelo;

/**
 * Caja de bloques (esquinas inclusivas) que ocupa una dungeon en la dimensión. Las dungeons nunca
 * se solapan; por defecto la dungeon n ocupa la {@link #ranura(int) ranura} centrada en x = n ×
 * {@value #SEPARACION}.
 */
public record Zona(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /** Distancia en bloques entre los centros de dos ranuras consecutivas. */
    public static final int SEPARACION = 2000;

    /** Mitad del lado horizontal de una ranura por defecto. */
    public static final int MEDIO_LADO_RANURA = 128;

    /** Altura mínima y máxima construible del tipo de dimensión {@code dolfincreepers:dungeons}. */
    public static final int Y_MIN = 0;

    public static final int Y_MAX = 255;

    public Zona {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("la esquina mínima supera a la máxima");
        }
    }

    /** Construye la zona a partir de dos esquinas en cualquier orden. */
    public static Zona entre(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Zona(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2));
    }

    /** Zona por defecto de la dungeon número {@code n}: 256×256 centrada en x = n × 2000. */
    public static Zona ranura(int n) {
        int centro = n * SEPARACION;
        return new Zona(
                centro - MEDIO_LADO_RANURA,
                Y_MIN,
                -MEDIO_LADO_RANURA,
                centro + MEDIO_LADO_RANURA - 1,
                Y_MAX,
                MEDIO_LADO_RANURA - 1);
    }

    public boolean contiene(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public boolean contiene(Punto punto) {
        return contiene(punto.x(), punto.y(), punto.z());
    }

    public boolean seSolapaCon(Zona otra) {
        return minX <= otra.maxX
                && otra.minX <= maxX
                && minY <= otra.maxY
                && otra.minY <= maxY
                && minZ <= otra.maxZ
                && otra.minZ <= maxZ;
    }

    /** Punto centrado en el suelo de la zona, usado como entrada mientras no se marque otra. */
    public Punto centroInferior() {
        return new Punto((minX + maxX + 1) / 2.0, minY + 1, (minZ + maxZ + 1) / 2.0, 0, 0);
    }

    public int chunkMinX() {
        return minX >> 4;
    }

    public int chunkMinZ() {
        return minZ >> 4;
    }

    public int chunkMaxX() {
        return maxX >> 4;
    }

    public int chunkMaxZ() {
        return maxZ >> 4;
    }
}
