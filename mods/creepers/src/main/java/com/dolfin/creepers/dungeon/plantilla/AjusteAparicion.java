package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.dungeon.modelo.Partida;

import net.minecraft.world.entity.Entity;

/**
 * Punto de enganche del escalado (#18) en la aparición de mobs de los marcadores, al empezar cada
 * partida. Se instala con {@code Dungeons.usarAjusteAparicion(...)}. Ambos métodos corren en el
 * hilo del servidor; la partida da la dificultad, {@code jugadoresIniciales()} y {@code
 * parametros()}.
 */
public interface AjusteAparicion {
    /** Sin escalado: la cantidad del marcador y la entidad tal cual. */
    AjusteAparicion NINGUNO = new AjusteAparicion() {};

    /** Cuántas entidades aparecen de un marcador; 0 o menos no pone ninguna. */
    default int cantidad(Partida partida, DatosMarcador marcador) {
        return marcador.cantidad();
    }

    /**
     * Ajusta cada entidad ya creada y colocada, antes de añadirla al mundo (vida, daño, equipo…).
     * Los mobs ya pasaron por {@code finalizeSpawn} si el marcador no trae NBT propio.
     */
    default void ajustar(Partida partida, DatosMarcador marcador, Entity entidad) {}
}
