package com.dolfin.creepers.dungeon.grupo;

import com.dolfin.creepers.dungeon.comando.PermisosDungeon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /party}: crear, invitar, aceptar, rechazar, expulsar, salir, listar y hablar en el grupo
 * de dungeons. Necesita {@link PermisosDungeon#USO}; la lógica está en {@link Grupos}.
 */
public final class ComandosGrupo {
    private static final String JUGADOR = "jugador";
    private static final String MENSAJE = "mensaje";

    private final Grupos grupos;
    private final Nombres nombres;

    public ComandosGrupo(Grupos grupos, Nombres nombres) {
        this.grupos = grupos;
        this.nombres = nombres;
    }

    /** Registra {@code /party}; se llama en cada construcción del despachador de comandos. */
    public void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("party")
                        .requires(PermisosDungeon::puedeUsar)
                        .then(Commands.literal("create").executes(this::crear))
                        .then(
                                Commands.literal("invite")
                                        .then(
                                                Commands.argument(JUGADOR, EntityArgument.player())
                                                        .executes(this::invitar)))
                        .then(Commands.literal("accept").executes(this::aceptar))
                        .then(Commands.literal("deny").executes(this::rechazar))
                        .then(
                                Commands.literal("kick")
                                        .then(argumentoMiembro().executes(this::expulsar)))
                        .then(Commands.literal("leave").executes(this::salir))
                        .then(Commands.literal("list").executes(this::listar))
                        .then(
                                Commands.literal("chat")
                                        .then(
                                                Commands.argument(
                                                                MENSAJE,
                                                                StringArgumentType.greedyString())
                                                        .executes(this::hablar))));
    }

    private int crear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID jugador = c.getSource().getPlayerOrException().getUUID();
        return resultado(c, grupos.crear(jugador));
    }

    private int invitar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID lider = c.getSource().getPlayerOrException().getUUID();
        ServerPlayer objetivo = EntityArgument.getPlayer(c, JUGADOR);
        return resultado(c, grupos.invitar(lider, objetivo.getUUID(), ahora()));
    }

    private int aceptar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID jugador = c.getSource().getPlayerOrException().getUUID();
        return resultado(c, grupos.aceptar(jugador, ahora()));
    }

    private int rechazar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID jugador = c.getSource().getPlayerOrException().getUUID();
        return resultado(c, grupos.rechazar(jugador, ahora()));
    }

    private int expulsar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID lider = c.getSource().getPlayerOrException().getUUID();
        String nombre = StringArgumentType.getString(c, JUGADOR);
        Optional<UUID> objetivo = nombres.idDe(nombre);
        if (objetivo.isEmpty()) {
            return fallo(c, "No conozco a ningún jugador llamado " + nombre + ".");
        }
        return resultado(c, grupos.expulsar(lider, objetivo.get()));
    }

    private int salir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID jugador = c.getSource().getPlayerOrException().getUUID();
        return resultado(c, grupos.salir(jugador));
    }

    private int listar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID jugador = c.getSource().getPlayerOrException().getUUID();
        Optional<Grupo> grupo = grupos.de(jugador);
        if (grupo.isEmpty()) {
            return fallo(c, TextosGrupo.error(Grupos.Error.SIN_GRUPO));
        }
        String miembros =
                grupo.get().miembros().stream()
                        .map(m -> etiqueta(grupo.get(), m))
                        .collect(Collectors.joining(", "));
        c.getSource()
                .sendSuccess(
                        () ->
                                Component.literal("Grupo (" + grupo.get().miembros().size() + "): ")
                                        .withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal(miembros)),
                        false);
        return grupo.get().miembros().size();
    }

    private int hablar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer jugador = c.getSource().getPlayerOrException();
        Optional<Grupo> grupo = grupos.de(jugador.getUUID());
        if (grupo.isEmpty()) {
            return fallo(c, TextosGrupo.error(Grupos.Error.SIN_GRUPO));
        }
        String texto = StringArgumentType.getString(c, MENSAJE);
        Component mensaje =
                Component.literal("[Grupo] " + jugador.getName().getString() + ": " + texto)
                        .withStyle(ChatFormatting.AQUA);
        Chat.enviar(c.getSource().getServer(), grupo.get().miembros(), mensaje);
        return 1;
    }

    private String etiqueta(Grupo grupo, UUID miembro) {
        String texto = nombres.de(miembro);
        if (grupo.lider().equals(miembro)) {
            texto += " (líder)";
        }
        return grupos.estaDesconectado(miembro) ? texto + " (desconectado)" : texto;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> argumentoMiembro() {
        return Commands.argument(JUGADOR, StringArgumentType.word())
                .suggests(
                        (c, b) -> {
                            UUID origen = c.getSource().getPlayerOrException().getUUID();
                            List<String> candidatos =
                                    grupos.miembrosDe(origen).stream()
                                            .filter(m -> !m.equals(origen))
                                            .map(nombres::de)
                                            .toList();
                            return SharedSuggestionProvider.suggest(candidatos, b);
                        });
    }

    private static int resultado(
            CommandContext<CommandSourceStack> c, Optional<Grupos.Error> error) {
        return error.map(e -> fallo(c, TextosGrupo.error(e))).orElse(1);
    }

    private static int fallo(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendFailure(Component.literal(texto));
        return 0;
    }

    private static long ahora() {
        return System.currentTimeMillis();
    }
}
