package com.dolfin.creepers.dungeon.reglas;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Modo aventura dentro de las partidas: no se rompen ni ponen bloques, pero botones, palancas,
 * placas, puertas y cofres funcionan. {@code EntradaSalida} ya entra en aventura; esto lo recupera
 * si algo (una reaparición, otro mod) deja a un miembro vivo en supervivencia.
 */
final class ReglasModo {
    private ReglasModo() {}

    /** Pasa a aventura a los miembros vivos de las partidas que estén en supervivencia. */
    static void forzarAventura(MinecraftServer server) {
        for (Partida partida : Dungeons.registro().partidas()) {
            for (Miembro miembro : partida.miembros()) {
                ServerPlayer jugador = server.getPlayerList().getPlayer(miembro.id());
                if (jugador != null
                        && DimensionDungeons.es(jugador.level())
                        && DecisionesReglas.debeForzarAventura(
                                miembro.estado(), jugador.gameMode() == GameType.SURVIVAL)) {
                    jugador.setGameMode(GameType.ADVENTURE);
                }
            }
        }
    }
}
