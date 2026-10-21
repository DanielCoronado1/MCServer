package com.dolfin.creepers.dungeon.mundo;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;

import net.minecraft.server.MinecraftServer;

/**
 * Devuelve la zona de una dungeon a su estado inicial. Se llama con la dungeon en {@code
 * REINICIANDO}: al terminar una partida, al arrancar tras una caída o por {@code reset} de admin.
 * Puede repartir el trabajo en varios ticks y debe llamar a {@code alTerminar} en el hilo del
 * servidor cuando acabe; entonces la dungeon queda {@code LIBRE}.
 *
 * <p>La implementación por defecto es {@code plantilla.ReiniciadorPlantillas}; se cambia con {@code
 * Dungeons.usarReiniciador}.
 */
@FunctionalInterface
public interface Reiniciador {
    void reiniciar(MinecraftServer server, DefinicionDungeon dungeon, Runnable alTerminar);
}
