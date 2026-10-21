package com.dolfin.creepers.dungeon.comando;

import static com.dolfin.creepers.dungeon.comando.ArgumentosDungeon.DIFICULTAD;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible;
import com.dolfin.creepers.dungeon.estadisticas.Estadisticas.EntradaTop;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Ocupacion;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Comandos de jugador de {@code /dungeon}: {@code list}, {@code info}, {@code join}, {@code leave},
 * {@code queue} y {@code top}. Necesitan {@link PermisosDungeon#USO}; entrar además pide el permiso
 * de la dungeon. La entrada pasa por {@code Dungeons.fila()} y los tiempos por {@code
 * Dungeons.estadisticas()}, que #14 y #22 sustituyen.
 */
final class ComandosJugador {
    private static final int TOP_LIMITE = 10;

    private static final DynamicCommandExceptionType DIFICULTAD_DESCONOCIDA =
            new DynamicCommandExceptionType(
                    id -> Component.literal("Dificultad desconocida: " + id));

    private final Confirmaciones confirmaciones;
    private final FlujoUnirse flujo;

    ComandosJugador(Confirmaciones confirmaciones, FlujoUnirse flujo) {
        this.confirmaciones = confirmaciones;
        this.flujo = flujo;
    }

    List<LiteralArgumentBuilder<CommandSourceStack>> nodos() {
        return List.of(
                nodoList(),
                nodoInfo(),
                nodoJoin(),
                nodoReady(),
                nodoLeave(),
                nodoQueue(),
                nodoTop());
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoList() {
        return Commands.literal("list").requires(PermisosDungeon::puedeUsar).executes(this::list);
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoInfo() {
        return Commands.literal("info")
                .requires(PermisosDungeon::puedeUsar)
                .then(
                        ArgumentosDungeon.argumentoDungeon(PermisosDungeon::puedeEntrar)
                                .executes(this::info));
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoJoin() {
        var nombre = ArgumentosDungeon.argumentoDungeon(PermisosDungeon::puedeEntrar);
        return Commands.literal("join")
                .requires(PermisosDungeon::puedeUsar)
                .then(
                        nombre.executes(c -> join(c, Dificultad.FACIL))
                                .then(
                                        ArgumentosDungeon.argumentoDificultad()
                                                .executes(c -> join(c, dificultadEscrita(c)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoReady() {
        return Commands.literal("ready").requires(PermisosDungeon::puedeUsar).executes(this::ready);
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoLeave() {
        return Commands.literal("leave").requires(PermisosDungeon::puedeUsar).executes(this::leave);
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoQueue() {
        return Commands.literal("queue").requires(PermisosDungeon::puedeUsar).executes(this::queue);
    }

    private LiteralArgumentBuilder<CommandSourceStack> nodoTop() {
        return Commands.literal("top")
                .requires(PermisosDungeon::puedeUsar)
                .then(
                        ArgumentosDungeon.argumentoDungeon(PermisosDungeon::puedeEntrar)
                                .executes(c -> top(c, Optional.empty()))
                                .then(
                                        ArgumentosDungeon.argumentoDificultad()
                                                .executes(
                                                        c ->
                                                                top(
                                                                        c,
                                                                        Optional.of(
                                                                                dificultadEscrita(
                                                                                        c))))));
    }

    private int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack origen = c.getSource();
        List<DefinicionDungeon> dungeons = ArgumentosDungeon.todas();
        if (dungeons.isEmpty()) {
            origen.sendSuccess(() -> Component.literal("No hay dungeons configuradas."), false);
            return 0;
        }
        origen.sendSuccess(
                () -> Component.literal("Dungeons:").withStyle(ChatFormatting.GOLD), false);
        for (DefinicionDungeon d : dungeons) {
            EstadoVisible visible = ConsultaDungeon.estadoVisible(origen, d);
            origen.sendSuccess(
                    () ->
                            Component.literal(" - " + d.nombre() + " (" + d.id() + "): ")
                                    .append(
                                            Component.literal(visible.texto())
                                                    .withStyle(color(visible.clase()))),
                    false);
        }
        return dungeons.size();
    }

    private int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        Map<Dificultad, Long> mejores =
                c.getSource().getPlayer() == null
                        ? Map.of()
                        : Dungeons.estadisticas()
                                .mejoresTiemposSegundos(
                                        c.getSource().getPlayer().getUUID(), d.id());
        for (String linea : VistaDungeon.info(d, mejores)) {
            c.getSource().sendSuccess(() -> Component.literal(linea), false);
        }
        return 1;
    }

    private int join(CommandContext<CommandSourceStack> c, Dificultad dificultad)
            throws CommandSyntaxException {
        ServerPlayer jugador = c.getSource().getPlayerOrException();
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        return switch (flujo.unirse(c.getSource(), jugador, d, dificultad)) {
            case FlujoUnirse.Resultado.Exito(String texto) -> exito(c, texto);
            case FlujoUnirse.Resultado.Fallo(String texto) -> fallo(c, texto);
            case FlujoUnirse.Resultado.PideConfirmacion p ->
                    avisarConfirmacion(
                            c, p.baneoMinutos(), "/dungeon join " + d.id() + " hardcore", "entrar");
        };
    }

    private int ready(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer jugador = c.getSource().getPlayerOrException();
        Optional<String> error = Dungeons.fila().confirmar(c.getSource().getServer(), jugador);
        return error.map(texto -> fallo(c, texto)).orElseGet(() -> exito(c, "Estás listo."));
    }

    private int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer jugador = c.getSource().getPlayerOrException();
        Optional<Ocupacion> ocupacion = Dungeons.registro().ocupacion(jugador.getUUID());
        if (ocupacion.isPresent() && ocupacion.get().tipo() == Ocupacion.Tipo.PARTIDA) {
            return abandonarPartida(c, jugador);
        }
        if (ocupacion.isPresent()) {
            Dungeons.fila().salir(jugador.getUUID());
            return exito(c, "Has salido de la fila de " + ocupacion.get().dungeon() + ".");
        }
        if (DimensionDungeons.es(jugador.level())) {
            Dungeons.entradaSalida().restaurar(jugador);
            return exito(c, "Has salido de la dimensión de dungeons.");
        }
        return fallo(c, "No estás en ninguna partida ni fila.");
    }

    private int abandonarPartida(CommandContext<CommandSourceStack> c, ServerPlayer jugador) {
        Optional<Partida> partida = Dungeons.registro().partidaDe(jugador.getUUID());
        if (partida.isPresent()
                && partida.get().dificultad() == Dificultad.HARDCORE
                && !confirmaHardcore(jugador, "leave")) {
            return avisarConfirmacion(
                    c,
                    Dungeons.registro().config().partida().hardcoreBanMinutos(),
                    "/dungeon leave",
                    "salir (cuenta como muerte)");
        }
        Dungeons.partidas().salir(jugador, MotivoSalida.ABANDONO);
        return exito(c, "Has salido de la partida.");
    }

    private int queue(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID jugador = c.getSource().getPlayerOrException().getUUID();
        Optional<Ocupacion> ocupacion = Dungeons.registro().ocupacion(jugador);
        OptionalInt posicion = Dungeons.fila().posicion(jugador);
        if (ocupacion.isPresent() && posicion.isPresent()) {
            int grupos = Dungeons.fila().gruposEnFila(ocupacion.get().dungeon());
            return exito(
                    c,
                    "Fila de "
                            + ocupacion.get().dungeon()
                            + ": puesto "
                            + posicion.getAsInt()
                            + " de "
                            + grupos
                            + ".");
        }
        if (ocupacion.isPresent()) {
            return exito(c, "Estás en una partida de " + ocupacion.get().dungeon() + ".");
        }
        return fallo(c, "No estás en ninguna fila.");
    }

    private int top(CommandContext<CommandSourceStack> c, Optional<Dificultad> dificultad)
            throws CommandSyntaxException {
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        List<EntradaTop> top = Dungeons.estadisticas().top(d.id(), dificultad, TOP_LIMITE);
        String filtro = dificultad.map(x -> " (" + x.id() + ")").orElse("");
        if (top.isEmpty()) {
            return exito(c, "Aún no hay tiempos registrados en " + d.nombre() + filtro + ".");
        }
        c.getSource()
                .sendSuccess(
                        () ->
                                Component.literal("Mejores tiempos de " + d.nombre() + filtro + ":")
                                        .withStyle(ChatFormatting.GOLD),
                        false);
        for (int i = 0; i < top.size(); i++) {
            EntradaTop entrada = top.get(i);
            String linea =
                    (i + 1)
                            + ". "
                            + entrada.jugador()
                            + " · "
                            + VistaDungeon.tiempo(entrada.segundos())
                            + " · "
                            + entrada.dificultad().id();
            c.getSource().sendSuccess(() -> Component.literal(linea), false);
        }
        return top.size();
    }

    private boolean confirmaHardcore(ServerPlayer jugador, String accion) {
        return confirmaciones.confirmar(jugador.getUUID(), accion, System.currentTimeMillis());
    }

    private static int avisarConfirmacion(
            CommandContext<CommandSourceStack> c, int baneoMinutos, String comando, String accion) {
        String texto =
                VistaDungeon.avisoHardcore(baneoMinutos)
                        + " Para "
                        + accion
                        + ", repite "
                        + comando
                        + " en "
                        + Confirmaciones.VENTANA_MS / 1000
                        + " s.";
        c.getSource()
                .sendSuccess(
                        () -> Component.literal(texto).withStyle(ChatFormatting.YELLOW), false);
        return 0;
    }

    private static Dificultad dificultadEscrita(CommandContext<CommandSourceStack> c)
            throws CommandSyntaxException {
        String texto = StringArgumentType.getString(c, DIFICULTAD);
        return Dificultad.deId(texto).orElseThrow(() -> DIFICULTAD_DESCONOCIDA.create(texto));
    }

    private static ChatFormatting color(EstadoVisible.Clase clase) {
        return switch (clase) {
            case LIBRE -> ChatFormatting.GREEN;
            case EN_PARTIDA, EN_FILA -> ChatFormatting.YELLOW;
            case REINICIANDO -> ChatFormatting.GRAY;
            case BLOQUEADA, CERRADA -> ChatFormatting.RED;
        };
    }

    private static int exito(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendSuccess(() -> Component.literal(texto), false);
        return 1;
    }

    private static int fallo(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendFailure(Component.literal(texto));
        return 0;
    }
}
