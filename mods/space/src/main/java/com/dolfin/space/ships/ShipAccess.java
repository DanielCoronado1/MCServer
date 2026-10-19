package com.dolfin.space.ships;

import com.dolfin.space.DolfinSpace;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * OPAC is optional. Unknown or incompatible APIs fail closed, never grant strangers pilot rights.
 */
public final class ShipAccess {
    private static boolean warned;

    public static boolean allowed(UUID owner, Player player) {
        if (owner == null) return false;
        if (owner.equals(player.getUUID())) return true;
        if (!(player instanceof ServerPlayer sp)
                || !FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) return false;
        try {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object serverApi =
                    api.getMethod("get", net.minecraft.server.MinecraftServer.class)
                            .invoke(null, sp.level().getServer());
            Object manager = serverApi.getClass().getMethod("getPartyManager").invoke(serverApi);
            Object a =
                    manager.getClass()
                            .getMethod("getPartyByMember", UUID.class)
                            .invoke(manager, owner);
            Object b =
                    manager.getClass()
                            .getMethod("getPartyByMember", UUID.class)
                            .invoke(manager, sp.getUUID());
            if (a == null || b == null) return false;
            Object aid = a.getClass().getMethod("getId").invoke(a),
                    bid = b.getClass().getMethod("getId").invoke(b);
            if (aid.equals(bid)) return true;
            return Boolean.TRUE.equals(a.getClass().getMethod("isAlly", UUID.class).invoke(a, bid));
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!warned) {
                warned = true;
                DolfinSpace.LOGGER.warn("event=ship_opac_unavailable reason={}", e.toString());
            }
            return false;
        }
    }

    private ShipAccess() {}
}
