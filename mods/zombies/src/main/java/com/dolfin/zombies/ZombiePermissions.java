package com.dolfin.zombies;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

public final class ZombiePermissions {
    private ZombiePermissions() {}

    public static String provider() {
        return FabricLoader.getInstance().isModLoaded("luckperms")
                ? "LuckPerms"
                : "vanilla_op_level_2";
    }

    public static boolean allowed(CommandSourceStack source, String node, boolean publicDefault) {
        boolean fallback =
                publicDefault
                        || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (!FabricLoader.getInstance().isModLoaded("luckperms") || source.getPlayer() == null)
            return fallback;
        var user = LuckPermsProvider.get().getUserManager().getUser(source.getPlayer().getUUID());
        if (user == null) {
            DolfinZombies.LOGGER.warn(
                    "event=permission_user_missing player={} node={} fallback={}",
                    source.getPlayer().getUUID(),
                    node,
                    fallback);
            return fallback;
        }
        var value = user.getCachedData().getPermissionData().checkPermission(node);
        return resolve(value == Tristate.UNDEFINED ? null : value.asBoolean(), fallback);
    }

    public static boolean resolve(Boolean explicit, boolean fallback) {
        return explicit == null ? fallback : explicit;
    }
}
