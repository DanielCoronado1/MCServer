package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.Partida;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** La bossbar de cada partida (#17): una por dungeon, visible solo para sus miembros. */
final class BarrasPartida {
    private final Map<String, ServerBossEvent> barras = new HashMap<>();

    /** Muestra {@code titulo} con {@code progreso} (0 a 1) a los miembros conectados. */
    void mostrar(
            MinecraftServer server,
            Partida partida,
            String titulo,
            float progreso,
            BossEvent.BossBarColor color) {
        ServerBossEvent barra =
                barras.computeIfAbsent(
                        partida.dungeon().id(),
                        id ->
                                new ServerBossEvent(
                                        UUID.randomUUID(),
                                        Component.empty(),
                                        color,
                                        BossEvent.BossBarOverlay.PROGRESS));
        barra.setName(Component.literal(titulo));
        barra.setProgress(Math.clamp(progreso, 0f, 1f));
        barra.setColor(color);
        sincronizarJugadores(server, partida, barra);
    }

    void quitar(String dungeonId) {
        ServerBossEvent barra = barras.remove(dungeonId);
        if (barra != null) {
            barra.removeAllPlayers();
        }
    }

    /** Solo ven la barra los miembros que siguen en la partida y están conectados. */
    private static void sincronizarJugadores(
            MinecraftServer server, Partida partida, ServerBossEvent barra) {
        Set<UUID> deseados =
                partida.miembros().stream()
                        .filter(m -> m.estado().participa())
                        .map(Miembro::id)
                        .collect(Collectors.toSet());
        List<ServerPlayer> sobran =
                barra.getPlayers().stream()
                        .filter(
                                p ->
                                        !deseados.contains(p.getUUID())
                                                || server.getPlayerList().getPlayer(p.getUUID())
                                                        != p)
                        .toList();
        sobran.forEach(barra::removePlayer);
        for (UUID id : deseados) {
            ServerPlayer jugador = server.getPlayerList().getPlayer(id);
            if (jugador != null) {
                barra.addPlayer(jugador);
            }
        }
    }
}
