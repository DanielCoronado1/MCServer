package com.dolfin.creepers.dungeon.eventos;

import com.dolfin.creepers.dungeon.modelo.CausaMuerte;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

import java.util.UUID;

/**
 * Eventos internos del sistema de dungeons. Los publica {@code RegistroDungeons} (y {@code
 * EntradaSalida} para {@link JugadorRestaurado}) en el hilo del servidor, siempre después de
 * aplicar el cambio de estado, así que un oyente ve el modelo ya actualizado.
 */
public sealed interface EventoDungeon {

    /** Una dungeon cambió de estado; se persiste para recuperarse de una caída. */
    record DungeonCambioEstado(String dungeon, EstadoDungeon anterior, EstadoDungeon nuevo)
            implements EventoDungeon {}

    /** Empezó una partida; sus miembros aún no se han teletransportado. */
    record PartidaIniciada(Partida partida) implements EventoDungeon {}

    /** Terminó una partida; todos sus miembros ya están {@code FUERA}. */
    record PartidaTerminada(Partida partida, ResultadoPartida resultado) implements EventoDungeon {}

    /** Un miembro se desconectó y tiene {@code segundosParaVolver} para volver. */
    record JugadorDesconectado(Partida partida, UUID jugador, int segundosParaVolver)
            implements EventoDungeon {}

    /** Un miembro desconectado volvió a tiempo. */
    record JugadorReconectado(Partida partida, UUID jugador) implements EventoDungeon {}

    /**
     * Un miembro murió. {@code vidasRestantes} es −1 con vidas ilimitadas. Con causa {@link
     * CausaMuerte#DESCONEXION} el jugador está desconectado: #16 aplica el castigo de hardcore a su
     * UUID.
     */
    record JugadorMurio(Partida partida, UUID jugador, CausaMuerte causa, int vidasRestantes)
            implements EventoDungeon {}

    /** Un miembro pasó a espectador (sin vidas en medio, #16). */
    record JugadorEspectador(Partida partida, UUID jugador) implements EventoDungeon {}

    /** Un miembro alcanzó el checkpoint {@code indice} de la definición. */
    record CheckpointAlcanzado(Partida partida, UUID jugador, int indice)
            implements EventoDungeon {}

    /** Un miembro dejó la partida (estado {@code FUERA}). */
    record JugadorSalio(Partida partida, UUID jugador, MotivoSalida motivo)
            implements EventoDungeon {}

    /**
     * Se devolvió a un jugador a su posición y modo guardados fuera de una partida activa (al
     * conectarse tras una caída, o tras terminar su partida mientras estaba desconectado). {@code
     * conDatosGuardados} es false si no había datos y se le mandó al spawn del mundo.
     */
    record JugadorRestaurado(UUID jugador, boolean conDatosGuardados) implements EventoDungeon {}
}
