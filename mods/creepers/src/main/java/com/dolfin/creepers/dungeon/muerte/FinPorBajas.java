package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorEspectador;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorMurio;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorSalio;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;

import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Señal de derrota por bajas (#16 → #17): tras cada muerte, paso a espectador o salida, revisa la
 * partida al final del tick y, si {@link ReglasMuerte#derrotaPorBajas} se cumple, llama al {@link
 * Terminador}. Se revisa al final del tick, y antes de la red de seguridad de partidas vacías de
 * {@code ServicioPartidas}, para no terminar la partida en mitad del daño que la provocó ni que
 * acabe como abandonada.
 */
public final class FinPorBajas {
    /** Termina una partida en derrota; por defecto {@code partidas().terminar(…, DERROTA)}. */
    @FunctionalInterface
    public interface Terminador {
        void derrota(MinecraftServer server, String dungeonId);
    }

    private final RegistroDungeons registro;
    private final Terminador terminador;
    private final Set<String> porRevisar = new LinkedHashSet<>();

    public FinPorBajas(RegistroDungeons registro, Terminador terminador) {
        this.registro = registro;
        this.terminador = terminador;
    }

    public void suscribir(BusEventos bus) {
        bus.suscribir(JugadorMurio.class, e -> revisar(e.partida()));
        bus.suscribir(JugadorEspectador.class, e -> revisar(e.partida()));
        bus.suscribir(JugadorSalio.class, e -> revisar(e.partida()));
    }

    /** Termina en derrota las partidas marcadas que se quedaron sin vivos. */
    public void tick(MinecraftServer server) {
        if (porRevisar.isEmpty()) {
            return;
        }
        List<String> dungeons = List.copyOf(porRevisar);
        porRevisar.clear();
        for (String dungeonId : dungeons) {
            registro.partida(dungeonId)
                    .filter(ReglasMuerte::derrotaPorBajas)
                    .ifPresent(
                            p -> {
                                DolfinCreepers.LOGGER.info(
                                        "event=dungeon_defeat_no_survivors dungeon={} match={}",
                                        dungeonId,
                                        p.id());
                                terminador.derrota(server, dungeonId);
                            });
        }
    }

    private void revisar(Partida partida) {
        if (partida.activa()) {
            porRevisar.add(partida.dungeon().id());
        }
    }
}
