package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.DolfinCreepers;

import net.fabricmc.loader.api.FabricLoader;

import java.util.UUID;

/**
 * Títulos de las dungeons (#22): nodos de permiso de LuckPerms que otorga la primera victoria. Sin
 * LuckPerms instalado no hace nada y no falla.
 */
final class Titulos {
    private Titulos() {}

    /**
     * @return false si no se pudo pedir (sin LuckPerms, o todavía sin arrancar)
     */
    static boolean conceder(UUID jugador, String nodo) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            DolfinCreepers.LOGGER.debug(
                    "event=dungeon_title_skipped reason=no_luckperms node={}", nodo);
            return false;
        }
        try {
            TitulosLuckPerms.conceder(jugador, nodo)
                    .whenComplete(
                            (vacio, error) -> {
                                if (error != null) {
                                    DolfinCreepers.LOGGER.warn(
                                            "event=dungeon_title_failed player={} node={}",
                                            jugador,
                                            nodo,
                                            error);
                                }
                            });
            return true;
        } catch (IllegalStateException | NoClassDefFoundError e) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_title_failed player={} node={} reason=luckperms_unavailable",
                    jugador,
                    nodo,
                    e);
            return false;
        }
    }
}
