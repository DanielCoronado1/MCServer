package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.ServicioPartidas;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Entrada sin grupos ni fila: un jugador es un grupo de uno y solo entra si la dungeon está libre
 * (el registro rechaza el resto: ocupada, tamaño de grupo, dificultad…). Sin requisitos ni
 * cooldown. #14 la sustituye con {@code Dungeons.usarFila}.
 */
public final class EntradaDirecta implements ServicioFila {
    private final ServicioPartidas partidas;
    private final RegistroDungeons registro;

    public EntradaDirecta(ServicioPartidas partidas, RegistroDungeons registro) {
        this.partidas = partidas;
        this.registro = registro;
    }

    @Override
    public ResultadoUnirse unirse(
            MinecraftServer server,
            ServerPlayer jugador,
            DefinicionDungeon dungeon,
            Dificultad dificultad) {
        ResultadoInicio resultado =
                partidas.iniciar(server, dungeon.id(), dificultad, List.of(jugador));
        return switch (resultado) {
            case ResultadoInicio.Iniciada(var partida) -> new ResultadoUnirse.Entro(partida);
            case ResultadoInicio.Rechazada(var rechazos) -> new ResultadoUnirse.Rechazado(rechazos);
        };
    }

    @Override
    public boolean salir(UUID jugador) {
        return registro.salirDeFila(jugador);
    }

    @Override
    public int gruposEnFila(String dungeonId) {
        return 0;
    }

    @Override
    public OptionalInt posicion(UUID jugador) {
        return OptionalInt.empty();
    }

    @Override
    public Optional<String> bloqueo(UUID jugador, DefinicionDungeon dungeon) {
        return Optional.empty();
    }
}
