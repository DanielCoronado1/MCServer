package com.dolfin.lobby;

import com.dolfin.space.KeplerDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Server-thread protection; mutation scope is entered only by repeatable construction tooling. */
public final class StationProtection {
    private static int editing;

    public static void edit(Runnable action) {
        editing++;
        try {
            action.run();
        } finally {
            editing--;
        }
    }

    public static boolean station(Level level) {
        var s = DolfinLobby.sessions();
        return s != null
                && (level.dimension().equals(LobbyConfig.key(s.config.lobby()))
                        || level.dimension().equals(LobbyConfig.key(s.config.showcase())));
    }

    public static boolean outpost(BlockPos p) {
        return Math.abs(p.getX() - 100000) <= 20
                && Math.abs(p.getZ() - 100000) <= 20
                && p.getY() >= 180
                && p.getY() <= 260;
    }

    public static boolean protectedAt(Level level, BlockPos p) {
        return station(level) || (KeplerDimension.isKepler(level.dimension()) && outpost(p));
    }

    public static boolean isolated(Player p) {
        var s = DolfinLobby.sessions();
        return s != null && s.isolated(p.getUUID());
    }

    public static boolean mutableArena(Level level, BlockPos p) {
        var s = DolfinLobby.sessions();
        if (s == null || !level.dimension().equals(LobbyConfig.key(s.config.showcase())))
            return false;
        for (var id : s.store.ids())
            if (s.combatIdForProtection(id)) {
                var c = StationLayout.arenaCenter(s.slot(id));
                if (Math.abs(p.getX() - c.getX()) <= 9
                        && Math.abs(p.getZ()) <= 9
                        && p.getY() >= 128
                        && p.getY() <= 135) return true;
            }
        return false;
    }

    public static boolean rejectMutation(Level level, BlockPos p) {
        return editing == 0 && protectedAt(level, p) && !mutableArena(level, p);
    }

    public static boolean demoEntity(Entity e) {
        return e.entityTags().contains("dolfin_demo_mob")
                || e.entityTags().contains("dolfin_demo_ship");
    }
}
