package com.dolfin.creepers.dungeon.muerte;

import net.fabricmc.loader.api.FabricLoader;

import java.util.UUID;

/**
 * Permiso {@value #NODO} (decisión P4): quien lo tiene pierde sus objetos al caer en hardcore pero
 * no recibe ban. Se consulta en LuckPerms si está instalado; sin LuckPerms, o si el usuario no está
 * cargado, nadie lo tiene (ni siquiera los operadores, para que el castigo sea predecible).
 */
public final class PermisoBypassBan {
    public static final String NODO = "dolfin.dungeon.bypass.ban";

    private PermisoBypassBan() {}

    public static boolean tiene(UUID jugador) {
        return FabricLoader.getInstance().isModLoaded("luckperms")
                && ConsultaLuckPerms.permitido(jugador, NODO);
    }
}
