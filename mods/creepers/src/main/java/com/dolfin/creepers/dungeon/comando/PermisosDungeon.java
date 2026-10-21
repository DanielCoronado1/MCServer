package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;

import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;

import java.util.Optional;

/**
 * Permisos de los comandos: {@value #USO} para usar {@code /dungeon}, {@value #ADMIN} para el
 * subárbol {@code admin} y el permiso propio de cada dungeon ({@code dolfin.dungeon.<id>} por
 * defecto) para entrar.
 *
 * <p>Un nodo explícito manda (se pregunta a la API de permisos de Fabric y, si nadie responde y
 * LuckPerms está cargado, a LuckPerms). Sin valor explícito: {@code use} y los permisos de dungeon
 * valen para todos y {@code admin} para operadores (nivel de gamemaster o consola). Para cerrar una
 * dungeon a un grupo hay que negarla explícitamente.
 */
public final class PermisosDungeon {
    public static final String USO = "dolfin.dungeon.use";
    public static final String ADMIN = "dolfin.dungeon.admin";

    private PermisosDungeon() {}

    /** Puede usar los comandos de jugador. */
    public static boolean puedeUsar(CommandSourceStack origen) {
        return permitido(origen, USO, true);
    }

    /** Puede usar los comandos de administración. */
    public static boolean esAdmin(CommandSourceStack origen) {
        return permitido(origen, ADMIN, false);
    }

    /** Puede entrar a la dungeon: permiso base y el propio de la dungeon. */
    public static boolean puedeEntrar(CommandSourceStack origen, DefinicionDungeon dungeon) {
        return puedeUsar(origen) && permitido(origen, dungeon.permiso(), true);
    }

    /**
     * Resuelve un nodo para el origen.
     *
     * @param porDefecto valor cuando nadie define el nodo; si es {@code false}, los operadores
     *     (gamemaster) y la consola lo tienen igualmente
     */
    public static boolean permitido(CommandSourceStack origen, String nodo, boolean porDefecto) {
        boolean respaldo =
                porDefecto || origen.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return resolver(explicito(origen, nodo), respaldo);
    }

    /** El valor explícito si existe; si no, el de respaldo. */
    static boolean resolver(Boolean explicito, boolean respaldo) {
        return explicito == null ? respaldo : explicito;
    }

    /**
     * Un nodo {@code a.b.c} como clave de la API de Fabric: espacio de nombres {@code a.b} y ruta
     * {@code c}, que los proveedores de permisos vuelven a unir con un punto. Vacío si el nodo no
     * cabe en una clave válida.
     */
    static Optional<String[]> partirNodo(String nodo) {
        int punto = nodo.lastIndexOf('.');
        if (punto <= 0 || punto == nodo.length() - 1) {
            return Optional.empty();
        }
        String espacio = nodo.substring(0, punto);
        String ruta = nodo.substring(punto + 1);
        boolean valido = Identifier.isValidNamespace(espacio) && Identifier.isValidPath(ruta);
        return valido ? Optional.of(new String[] {espacio, ruta}) : Optional.empty();
    }

    private static Boolean explicito(CommandSourceStack origen, String nodo) {
        Boolean deFabric = deFabric(origen, nodo);
        if (deFabric != null) {
            return deFabric;
        }
        return FabricLoader.getInstance().isModLoaded("luckperms")
                ? PermisosLuckPerms.explicito(origen, nodo)
                : null;
    }

    private static Boolean deFabric(CommandSourceStack origen, String nodo) {
        Optional<String[]> partes = partirNodo(nodo);
        if (partes.isEmpty()) {
            return null;
        }
        try {
            Identifier clave = Identifier.fromNamespaceAndPath(partes.get()[0], partes.get()[1]);
            TriState valor = origen.checkPermission(clave);
            return valor == TriState.DEFAULT ? null : valor.get();
        } catch (RuntimeException e) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_permission_lookup_failed node={} error={}", nodo, e.toString());
            return null;
        }
    }
}
