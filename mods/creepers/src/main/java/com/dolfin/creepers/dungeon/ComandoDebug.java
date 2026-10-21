package com.dolfin.creepers.dungeon;

import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /dungeondebug}: comando mínimo de operador para probar #11/#12 sin el árbol {@code
 * /dungeon} de #13, que lo sustituirá. Subcomandos: {@code iniciar <id> <dificultad> [jugadores]},
 * {@code salir [jugadores]}, {@code terminar <id> <resultado>}, {@code estado}, {@code recargar}.
 */
final class ComandoDebug {
    private ComandoDebug() {}

    static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dungeondebug")
                        .requires(
                                s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(nodoIniciar())
                        .then(nodoSalir())
                        .then(nodoTerminar())
                        .then(Commands.literal("estado").executes(ComandoDebug::estado))
                        .then(Commands.literal("recargar").executes(ComandoDebug::recargar)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nodoIniciar() {
        var jugadores =
                Commands.argument("jugadores", EntityArgument.players())
                        .executes(
                                c ->
                                        iniciar(
                                                c,
                                                List.copyOf(
                                                        EntityArgument.getPlayers(
                                                                c, "jugadores"))));
        var dificultad =
                argumentoDificultad()
                        .executes(c -> iniciar(c, List.of(c.getSource().getPlayerOrException())))
                        .then(jugadores);
        return Commands.literal("iniciar").then(argumentoDungeon().then(dificultad));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nodoSalir() {
        var jugadores =
                Commands.argument("jugadores", EntityArgument.players())
                        .executes(c -> salir(c, EntityArgument.getPlayers(c, "jugadores")));
        return Commands.literal("salir")
                .executes(c -> salir(c, List.of(c.getSource().getPlayerOrException())))
                .then(jugadores);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nodoTerminar() {
        var resultado =
                Commands.argument("resultado", StringArgumentType.word())
                        .suggests(
                                (c, b) ->
                                        SharedSuggestionProvider.suggest(
                                                Arrays.stream(ResultadoPartida.values())
                                                        .map(
                                                                r ->
                                                                        r.name()
                                                                                .toLowerCase(
                                                                                        Locale
                                                                                                .ROOT)),
                                                b))
                        .executes(ComandoDebug::terminar);
        return Commands.literal("terminar").then(argumentoDungeon().then(resultado));
    }

    private static int iniciar(CommandContext<CommandSourceStack> c, List<ServerPlayer> jugadores) {
        String id = StringArgumentType.getString(c, "dungeon");
        Optional<Dificultad> dificultad =
                Dificultad.deId(StringArgumentType.getString(c, "dificultad"));
        if (dificultad.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Dificultad desconocida"));
            return 0;
        }
        ResultadoInicio resultado =
                Dungeons.partidas()
                        .iniciar(c.getSource().getServer(), id, dificultad.get(), jugadores);
        if (resultado instanceof ResultadoInicio.Rechazada(var rechazos)) {
            c.getSource().sendFailure(Component.literal("No se pudo iniciar: " + rechazos));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Partida iniciada en " + id), true);
        return 1;
    }

    private static int salir(
            CommandContext<CommandSourceStack> c, Collection<ServerPlayer> jugadores) {
        int sacados = 0;
        for (ServerPlayer jugador : jugadores) {
            if (Dungeons.partidas().salir(jugador, MotivoSalida.ABANDONO)) {
                sacados++;
            }
        }
        int total = sacados;
        c.getSource().sendSuccess(() -> Component.literal(total + " jugador(es) fuera"), true);
        return total;
    }

    private static int terminar(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "dungeon");
        String texto = StringArgumentType.getString(c, "resultado").toUpperCase(Locale.ROOT);
        Optional<ResultadoPartida> resultado =
                Arrays.stream(ResultadoPartida.values())
                        .filter(r -> r.name().equals(texto))
                        .findFirst();
        if (resultado.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Resultado desconocido"));
            return 0;
        }
        boolean hecho =
                Dungeons.partidas()
                        .terminar(c.getSource().getServer(), id, resultado.get())
                        .isPresent();
        if (!hecho) {
            c.getSource().sendFailure(Component.literal("Sin partida en " + id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Partida terminada en " + id), true);
        return 1;
    }

    private static int estado(CommandContext<CommandSourceStack> c) {
        StringBuilder texto = new StringBuilder("Dungeons:");
        Dungeons.registro()
                .estados()
                .forEach(
                        (id, estado) ->
                                texto.append("\n- ").append(id).append(": ").append(estado));
        for (Partida partida : Dungeons.registro().partidas()) {
            texto.append("\nPartida ")
                    .append(partida.dungeon().id())
                    .append(" (")
                    .append(partida.dificultad().id())
                    .append("):");
            for (Miembro m : partida.miembros()) {
                texto.append(" ")
                        .append(m.nombre())
                        .append("=")
                        .append(m.estado())
                        .append("/vidas:")
                        .append(m.vidas());
            }
        }
        c.getSource().sendSuccess(() -> Component.literal(texto.toString()), false);
        return 1;
    }

    private static int recargar(CommandContext<CommandSourceStack> c) {
        var informe = Dungeons.recargarConfig();
        c.getSource()
                .sendSuccess(
                        () ->
                                Component.literal(
                                        "Config recargada: "
                                                + informe.resumen()
                                                + (informe.problemas().isEmpty()
                                                        ? ""
                                                        : "\n" + informe.problemas())),
                        true);
        return 1;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> argumentoDungeon() {
        return Commands.argument("dungeon", StringArgumentType.word())
                .suggests(
                        (c, b) ->
                                SharedSuggestionProvider.suggest(
                                        Dungeons.registro().estados().keySet(), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> argumentoDificultad() {
        return Commands.argument("dificultad", StringArgumentType.word())
                .suggests(
                        (c, b) ->
                                SharedSuggestionProvider.suggest(
                                        Arrays.stream(Dificultad.values()).map(Dificultad::id), b));
    }
}
