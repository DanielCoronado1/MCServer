package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.muerte.BanHardcore;

import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.util.List;

/**
 * Bans de hardcore vigentes ({@link BanHardcore}, origen {@value BanHardcore#FUENTE}): {@code
 * /dungeon admin pardon} solo quita estos y nunca los de un moderador.
 */
final class BanesDungeon {
    private BanesDungeon() {}

    /** Bans de dungeons vigentes. */
    static List<UserBanListEntry> vigentes(UserBanList bans) {
        return bans.getEntries().stream()
                .filter(e -> BanHardcore.esDeDungeon(e) && !e.hasExpired())
                .toList();
    }

    /** Nombres de los jugadores con un ban de dungeons vigente. */
    static List<String> nombres(UserBanList bans) {
        return vigentes(bans).stream().map(e -> e.getUser().name()).toList();
    }

    /**
     * Quita los bans de dungeons del jugador, sin importar mayúsculas.
     *
     * @return cuántos quitó
     */
    static int perdonar(UserBanList bans, String nombre) {
        List<NameAndId> objetivos =
                vigentes(bans).stream()
                        .map(UserBanListEntry::getUser)
                        .filter(u -> u.name().equalsIgnoreCase(nombre))
                        .toList();
        objetivos.forEach(bans::remove);
        return objetivos.size();
    }
}
