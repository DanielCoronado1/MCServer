package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.dungeon.modelo.Zona;

/**
 * Caja de bloques con esquinas inclusivas: un trozo de plantilla o una capa que se coloca en un
 * tick. Sin Minecraft, para poder probar el troceado.
 */
public record Caja(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Caja {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("la esquina mínima supera a la máxima");
        }
    }

    /** La misma caja que ocupa una zona de dungeon. */
    public static Caja de(Zona zona) {
        return new Caja(
                zona.minX(), zona.minY(), zona.minZ(), zona.maxX(), zona.maxY(), zona.maxZ());
    }

    public int ancho() {
        return maxX - minX + 1;
    }

    public int alto() {
        return maxY - minY + 1;
    }

    public int largo() {
        return maxZ - minZ + 1;
    }

    public long volumen() {
        return (long) ancho() * alto() * largo();
    }

    /** Desplaza la caja; sirve para pasar de coordenadas relativas a la zona a absolutas. */
    public Caja desplazada(int dx, int dy, int dz) {
        return new Caja(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public boolean contiene(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
