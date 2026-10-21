package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.fila.ServicioFila.ResultadoUnirse;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Lo que ocurre al pedir entrar a una dungeon con una dificultad, común a {@code /dungeon join} y
 * al menú de cofre (#23): permiso, dificultad admitida, confirmación de hardcore y entrega a {@code
 * Dungeons.fila()}.
 */
final class FlujoUnirse {
    /** Lo que se le cuenta al jugador. */
    sealed interface Resultado {
        /** Entró o quedó en la fila. */
        record Exito(String texto) implements Resultado {}

        /** No pudo entrar. */
        record Fallo(String texto) implements Resultado {}

        /** Hardcore sin confirmar: repetir la misma petición dentro de la ventana la confirma. */
        record PideConfirmacion(int baneoMinutos) implements Resultado {}
    }

    private final Confirmaciones confirmaciones;

    FlujoUnirse(Confirmaciones confirmaciones) {
        this.confirmaciones = confirmaciones;
    }

    /** Pide entrar a {@code dungeon}; en hardcore la primera vez solo pide confirmar. */
    Resultado unirse(
            CommandSourceStack origen,
            ServerPlayer jugador,
            DefinicionDungeon dungeon,
            Dificultad dificultad) {
        if (!PermisosDungeon.puedeEntrar(origen, dungeon)) {
            return new Resultado.Fallo("No tienes permiso para entrar a " + dungeon.nombre() + ".");
        }
        if (!dungeon.dificultades().contains(dificultad)) {
            return new Resultado.Fallo(
                    dungeon.nombre() + " no tiene la dificultad " + dificultad.id() + ".");
        }
        if (dificultad == Dificultad.HARDCORE && !confirmaHardcore(jugador, dungeon)) {
            return new Resultado.PideConfirmacion(
                    Dungeons.registro().config().partida().hardcoreBanMinutos());
        }
        ResultadoUnirse resultado =
                Dungeons.fila().unirse(origen.getServer(), jugador, dungeon, dificultad);
        return describir(dungeon, dificultad, resultado);
    }

    /** Olvida la confirmación pendiente de un jugador (al desconectarse). */
    void olvidar(java.util.UUID jugador) {
        confirmaciones.olvidar(jugador);
    }

    private boolean confirmaHardcore(ServerPlayer jugador, DefinicionDungeon dungeon) {
        return confirmaciones.confirmar(
                jugador.getUUID(), "join:" + dungeon.id(), System.currentTimeMillis());
    }

    private static Resultado describir(
            DefinicionDungeon d, Dificultad dificultad, ResultadoUnirse resultado) {
        return switch (resultado) {
            case ResultadoUnirse.Entro entro ->
                    new Resultado.Exito("Entras a " + d.nombre() + " (" + dificultad.id() + ").");
            case ResultadoUnirse.EnFila(int posicion, int grupos) ->
                    new Resultado.Exito(
                            "Estás en la fila de "
                                    + d.nombre()
                                    + ": puesto "
                                    + posicion
                                    + " de "
                                    + grupos
                                    + ".");
            case ResultadoUnirse.Bloqueado(String motivo) -> new Resultado.Fallo(motivo);
            case ResultadoUnirse.Rechazado(var rechazos) ->
                    new Resultado.Fallo(
                            "No se puede entrar: "
                                    + String.join(
                                            "; ",
                                            rechazos.stream()
                                                    .map(
                                                            r ->
                                                                    VistaDungeon.rechazo(
                                                                            r, Optional.of(d)))
                                                    .toList()));
        };
    }
}
