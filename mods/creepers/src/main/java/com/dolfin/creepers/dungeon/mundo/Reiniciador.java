package com.dolfin.creepers.dungeon.mundo;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;

import net.minecraft.server.MinecraftServer;

/**
 * Devuelve la zona de una dungeon a su estado inicial. Se llama con la dungeon en {@code
 * REINICIANDO}: al terminar una partida, al arrancar tras una caída o por {@code reset} de admin.
 * Puede repartir el trabajo en varios ticks y debe llamar a {@code alTerminar} en el hilo del
 * servidor cuando acabe; entonces la dungeon queda {@code LIBRE}.
 *
 * <p>#19 sustituye la implementación por defecto con {@code Dungeons.usarReiniciador}.
 */
@FunctionalInterface
public interface Reiniciador {
    void reiniciar(MinecraftServer server, DefinicionDungeon dungeon, Runnable alTerminar);

    /** Sin plantillas todavía: no toca la zona y la libera enseguida. */
    Reiniciador SIN_PLANTILLA =
            (server, dungeon, alTerminar) -> {
                DolfinCreepers.LOGGER.warn(
                        "event=dungeon_reset_skipped dungeon={} reason=no_template_support",
                        dungeon.id());
                alTerminar.run();
            };
}
