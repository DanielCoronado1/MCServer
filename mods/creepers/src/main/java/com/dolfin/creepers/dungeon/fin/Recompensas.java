package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.config.DungeonsConfig.Fin;
import com.dolfin.creepers.dungeon.fin.ReglasFin.Premios;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Entrega las recompensas de la primera victoria (#22): el título de LuckPerms y la cabeza del
 * jefe. La cabeza de quien estaba desconectado queda pendiente en {@link ProgresoJugadores} y se
 * entrega al conectarse.
 */
final class Recompensas {
    private final RegistroDungeons registro;
    private final HistorialDungeons historial;

    Recompensas(RegistroDungeons registro, HistorialDungeons historial) {
        this.registro = registro;
        this.historial = historial;
    }

    /** Entrega los {@code premios} de {@code jugador} por ganar {@code partida}. */
    void otorgar(MinecraftServer server, Partida partida, UUID jugador, Premios premios) {
        ServerPlayer conectado = server.getPlayerList().getPlayer(jugador);
        if (premios.titulo()) {
            partida.config()
                    .fin()
                    .nodoTitulo(partida.dungeon().id(), partida.dificultad())
                    .filter(nodo -> Titulos.conceder(jugador, nodo))
                    .ifPresent(nodo -> avisarTitulo(conectado, nodo));
        }
        if (premios.cabeza()) {
            if (conectado != null) {
                entregarCabeza(conectado, partida.dungeon().id());
            } else {
                historial.progreso().pendienteCabeza(jugador, partida.dungeon().id());
                historial.guardar();
            }
        }
    }

    /** Entrega las cabezas que esperaban a que el jugador se conectase. */
    void alConectarse(ServerPlayer jugador) {
        Set<String> pendientes = historial.progreso().recogerCabezas(jugador.getUUID());
        pendientes.forEach(dungeon -> entregarCabeza(jugador, dungeon));
        if (!pendientes.isEmpty()) {
            historial.guardar();
        }
    }

    private void entregarCabeza(ServerPlayer jugador, String dungeonId) {
        Fin fin = registro.config().fin();
        String nombre = registro.config().dungeon(dungeonId).map(d -> d.nombre()).orElse(dungeonId);
        Optional<ItemStack> cabeza = crearCabeza(fin, nombre);
        if (cabeza.isEmpty()) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_head_skipped dungeon={} item={}", dungeonId, fin.cabezaJefe());
            return;
        }
        jugador.getInventory().placeItemBackInInventory(cabeza.get(), Prediction.SERVER_ONLY);
        jugador.sendSystemMessage(
                Component.literal("Recibes la cabeza del jefe de " + nombre)
                        .withStyle(ChatFormatting.GOLD));
    }

    /** El objeto configurado con el nombre del jefe; vacío si el id no existe. */
    static Optional<ItemStack> crearCabeza(Fin fin, String dungeon) {
        Identifier id = Identifier.tryParse(fin.cabezaJefe());
        Optional<Item> item =
                id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        return item.map(
                i -> {
                    ItemStack pila = new ItemStack(i);
                    pila.set(
                            DataComponents.CUSTOM_NAME,
                            Component.literal(fin.cabezaJefeNombre().replace("%dungeon%", dungeon))
                                    .withStyle(ChatFormatting.GOLD));
                    return pila;
                });
    }

    private static void avisarTitulo(ServerPlayer jugador, String nodo) {
        if (jugador != null) {
            jugador.sendSystemMessage(
                    Component.literal("Has desbloqueado un título (" + nodo + ")")
                            .withStyle(ChatFormatting.GOLD));
        }
    }
}
