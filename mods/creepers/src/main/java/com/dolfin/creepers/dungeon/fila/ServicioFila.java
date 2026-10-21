package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Rechazo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Cómo se entra a una dungeon desde {@code /dungeon join}: grupos, fila, ready check y requisitos
 * (#14). {@link FilaGrupos} es la implementación en uso; {@link EntradaDirecta} mete al jugador
 * solo en la dungeon si está libre y sirve de alternativa ({@code Dungeons.usarFila}). Los comandos
 * ya comprueban el permiso y la confirmación de hardcore; esta capa decide si el jugador entra,
 * espera o no puede.
 */
public interface ServicioFila {
    /** Lo que pasó al pedir entrar. */
    sealed interface ResultadoUnirse {
        /** La partida empezó y el jugador ya va hacia la entrada. */
        record Entro(Partida partida) implements ResultadoUnirse {}

        /** El jugador espera: {@code posicion} (1 = el siguiente) entre {@code grupos} en fila. */
        record EnFila(int posicion, int grupos) implements ResultadoUnirse {}

        /** No se pudo empezar ni esperar. */
        record Rechazado(List<Rechazo> rechazos) implements ResultadoUnirse {}

        /** Requisitos o cooldown sin cumplir; el texto se muestra tal cual al jugador. */
        record Bloqueado(String motivo) implements ResultadoUnirse {}
    }

    /** Un jugador pide entrar a una dungeon o ponerse en su fila. */
    ResultadoUnirse unirse(
            MinecraftServer server,
            ServerPlayer jugador,
            DefinicionDungeon dungeon,
            Dificultad dificultad);

    /** Saca al jugador de la fila en la que esté; {@code false} si no estaba en ninguna. */
    boolean salir(UUID jugador);

    /** Grupos esperando para entrar a la dungeon. */
    int gruposEnFila(String dungeonId);

    /** Puesto del jugador en su fila (1 = el siguiente); vacío si no está en una. */
    OptionalInt posicion(UUID jugador);

    /** Por qué el jugador no puede entrar ahora (requisitos, cooldown); vacío si puede. */
    Optional<String> bloqueo(UUID jugador, DefinicionDungeon dungeon);

    /**
     * El jugador confirma su ready check.
     *
     * @return el motivo del fallo si no tiene ninguno pendiente
     */
    default Optional<String> confirmar(MinecraftServer server, ServerPlayer jugador) {
        return Optional.of("No tienes ningún ready check pendiente.");
    }

    /** Llamado cada segundo desde el tick del servidor: plazos, desconexiones y ready checks. */
    default void tick(MinecraftServer server) {}

    /** Un jugador se conectó. */
    default void alConectarse(ServerPlayer jugador) {}

    /** Un jugador se desconectó. */
    default void alDesconectarse(ServerPlayer jugador) {}

    /** El servidor arranca: se olvidan filas y grupos de una ejecución anterior. */
    default void alArrancar() {}
}
