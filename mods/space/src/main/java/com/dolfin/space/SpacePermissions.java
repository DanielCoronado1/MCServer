package com.dolfin.space;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * Resolves {@code dolfinspace} permission nodes through LuckPerms when installed, falling back to
 * vanilla operator level 2 ({@link Permissions#COMMANDS_GAMEMASTER}) for admin actions.
 */
public final class SpacePermissions {
    public static final String NODE_ADMIN = "dolfinspace.admin";

    private SpacePermissions() {}

    /** Returns the active permission provider identifier for startup diagnostics. */
    public static String provider() {
        return FabricLoader.getInstance().isModLoaded("luckperms")
                ? "LuckPerms"
                : "vanilla_op_level_2";
    }

    /** Checks whether a command source has administrator permission ({@code dolfinspace.admin}). */
    public static boolean isAdmin(CommandSourceStack source) {
        return allowed(source, NODE_ADMIN, false);
    }

    /**
     * Checks a permission node against LuckPerms if present, or falls back to vanilla permissions.
     */
    public static boolean allowed(CommandSourceStack source, String node, boolean publicDefault) {
        boolean fallback =
                publicDefault
                        || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (!FabricLoader.getInstance().isModLoaded("luckperms") || source.getPlayer() == null) {
            return fallback;
        }
        net.luckperms.api.model.user.User user;
        try {
            user = LuckPermsProvider.get().getUserManager().getUser(source.getPlayer().getUUID());
        } catch (IllegalStateException | LinkageError failure) {
            DolfinSpace.LOGGER.warn(
                    "event=integration name=luckperms state=unavailable action=deny reason={}",
                    failure.toString());
            return false;
        }
        if (user == null) {
            DolfinSpace.LOGGER.warn(
                    "event=permission_user_missing player={} node={} fallback={}",
                    source.getPlayer().getUUID(),
                    node,
                    fallback);
            return fallback;
        }
        var value = user.getCachedData().getPermissionData().checkPermission(node);
        return resolve(value == Tristate.UNDEFINED ? null : value.asBoolean(), fallback);
    }

    /** Pure tri-state permission resolution helper. */
    public static boolean resolve(Boolean explicit, boolean fallback) {
        return explicit == null ? fallback : explicit;
    }
}
