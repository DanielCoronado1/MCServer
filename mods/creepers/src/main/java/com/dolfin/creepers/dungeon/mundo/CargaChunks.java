package com.dolfin.creepers.dungeon.mundo;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaIniciada;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaTerminada;
import com.dolfin.creepers.dungeon.modelo.Zona;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Mantiene cargados y simulando los chunks de una dungeon solo mientras tiene partida. Los tickets
 * no persisten: tras una caída nada queda cargado de más. Se engancha a {@code PartidaIniciada} y
 * {@code PartidaTerminada}, así ningún otro módulo tiene que acordarse de llamarla.
 */
public final class CargaChunks {
    /**
     * Ticket propio: carga, simula y mantiene activa la dimensión aunque no haya jugadores (por
     * ejemplo, mientras todos están desconectados con plazo para volver). Sin {@code FLAG_PERSIST}.
     */
    public static final TicketType TICKET =
            Registry.register(
                    BuiltInRegistries.TICKET_TYPE,
                    DolfinCreepers.id("dungeon"),
                    new TicketType(
                            TicketType.NO_TIMEOUT,
                            TicketType.FLAG_LOADING
                                    | TicketType.FLAG_SIMULATION
                                    | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    /** Radio del ticket por chunk: 2 hace que el propio chunk procese entidades. */
    private static final int RADIO = 2;

    private final Supplier<MinecraftServer> server;

    public CargaChunks(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    /** Fuerza el registro del ticket durante la inicialización del mod. */
    public static void registrar() {
        DolfinCreepers.LOGGER.debug("event=dungeon_ticket_registered type={}", TICKET);
    }

    /** Suscribe la carga y descarga a las partidas del bus. */
    public void suscribir(BusEventos bus) {
        bus.suscribir(
                PartidaIniciada.class,
                e -> aplicar(e.partida().dungeon().zona(), this::poner, "loaded"));
        bus.suscribir(
                PartidaTerminada.class,
                e -> aplicar(e.partida().dungeon().zona(), this::quitar, "released"));
    }

    private void aplicar(Zona zona, BiConsumer<ServerLevel, ChunkPos> accion, String evento) {
        MinecraftServer s = server.get();
        if (s == null) {
            return;
        }
        DimensionDungeons.nivel(s)
                .ifPresent(
                        nivel -> {
                            int chunks = 0;
                            for (int x = zona.chunkMinX(); x <= zona.chunkMaxX(); x++) {
                                for (int z = zona.chunkMinZ(); z <= zona.chunkMaxZ(); z++) {
                                    accion.accept(nivel, new ChunkPos(x, z));
                                    chunks++;
                                }
                            }
                            DolfinCreepers.LOGGER.info(
                                    "event=dungeon_chunks_{} chunks={} zone={}",
                                    evento,
                                    chunks,
                                    zona);
                        });
    }

    private void poner(ServerLevel nivel, ChunkPos pos) {
        nivel.getChunkSource().addTicketWithRadius(TICKET, pos, RADIO);
    }

    private void quitar(ServerLevel nivel, ChunkPos pos) {
        nivel.getChunkSource().removeTicketWithRadius(TICKET, pos, RADIO);
    }
}
