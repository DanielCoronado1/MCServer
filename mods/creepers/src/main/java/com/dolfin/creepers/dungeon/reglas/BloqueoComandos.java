package com.dolfin.creepers.dungeon.reglas;

import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.permission.v1.PermissionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Bloquea dentro de la dimensión de dungeons los comandos de EssentialCommands que teletransportan
 * o dan ventajas ({@code /home}, {@code /tpa}, {@code /spawn}, {@code /back}, {@code /fly}…): solo
 * se sale con {@code /dungeon leave}.
 *
 * <p>EssentialCommands consulta cada comando con la API de permisos de Fabric; aquí se responde
 * {@code false} a sus nodos cuando quien pregunta está en la dimensión, y {@code null} (no opina)
 * en cualquier otro caso. No hace falta ni dependencia ni mixin sobre EssentialCommands. Los
 * operadores también quedan bloqueados dentro; para moverse tienen {@code /dungeon admin tp} y
 * {@code leave}.
 */
final class BloqueoComandos {
    /** Espacio de nombres de los nodos de EssentialCommands. */
    static final String ESPACIO = "essentialcommands";

    /**
     * Rutas de nodo bloqueadas; una ruta también cubre las que cuelgan de ella ({@code fly} cubre
     * {@code fly.self} y {@code fly.others}). Teletransportes: tpa, tpahere, tpaccept, tpdeny,
     * home, warp, back, spawn, randomteleport y top. Ventajas: fly, invuln, heal, feed, extinguish,
     * repair, workbench, stonecutter, grindstone, anvil y enderchest.
     */
    private static final List<String> RUTAS =
            List.of(
                    "tpa",
                    "tpahere",
                    "tpaccept",
                    "tpdeny",
                    "home.tp",
                    "home_tp_others",
                    "warp.tp",
                    "warp.tp_named",
                    "warp_tp_others",
                    "back",
                    "spawn.tp",
                    "randomteleport",
                    "top",
                    "fly",
                    "invuln",
                    "heal",
                    "feed",
                    "extinguish",
                    "repair",
                    "workbench",
                    "stonecutter",
                    "grindstone",
                    "anvil",
                    "enderchest");

    private BloqueoComandos() {}

    /** El nodo {@code espacio:ruta} se deniega dentro de la dimensión. */
    static boolean bloquea(String espacio, String ruta) {
        return ESPACIO.equals(espacio)
                && RUTAS.stream().anyMatch(r -> ruta.equals(r) || ruta.startsWith(r + "."));
    }

    /** Engancha la consulta de permisos; se llama una vez al arrancar el mod. */
    static void registrar() {
        PermissionEvents.ON_REQUEST.register(BloqueoComandos::responder);
    }

    private static <T> T responder(PermissionContext contexto, PermissionNode<T> nodo) {
        if (!bloquea(nodo.key().getNamespace(), nodo.key().getPath())
                || !DimensionDungeons.es(nivelDe(contexto))) {
            return null;
        }
        try {
            return nodo.cast(Boolean.FALSE);
        } catch (ClassCastException e) {
            return null;
        }
    }

    private static Level nivelDe(PermissionContext contexto) {
        Level nivel = contexto.get(PermissionContext.LEVEL);
        if (nivel != null) {
            return nivel;
        }
        Entity entidad = contexto.get(PermissionContext.ENTITY);
        return entidad == null ? null : entidad.level();
    }
}
