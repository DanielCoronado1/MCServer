package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.comando.EdicionDungeon.Resultado;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.plantilla.ServicioPlantillas.InformeGuardado;
import com.dolfin.creepers.dungeon.plantilla.ServicioPlantillas.Respuesta;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.util.Prediction;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Subárbol {@code /dungeon admin}, con {@link PermisosDungeon#ADMIN}. Cada comando que cambia algo
 * deja en el log quién lo ejecutó ({@link AuditoriaAdmin}). Las ediciones de geometría pasan por
 * {@link EdicionDungeon} y se guardan con {@code Dungeons.guardarDungeon}. {@code save} y {@code
 * reset} usan las plantillas de zona (#19, {@code Dungeons.plantillas()}): responden al empezar y
 * el guardado avisa otra vez al terminar, porque se reparte en varios ticks.
 */
final class ComandosAdmin {
    private static final String JUGADOR = "jugador";

    private ComandosAdmin() {}

    static LiteralArgumentBuilder<CommandSourceStack> nodo() {
        return Commands.literal("admin")
                .requires(PermisosDungeon::esAdmin)
                .then(Commands.literal("reload").executes(ComandosAdmin::reload))
                .then(Commands.literal("wand").executes(ComandosAdmin::wand))
                .then(conDungeon("setzone", ComandosAdmin::setzone))
                .then(conDungeon("setentry", c -> editar(c, "setentry", ComandosAdmin::setentry)))
                .then(conDungeon("setexit", c -> editar(c, "setexit", ComandosAdmin::setexit)))
                .then(nodoCheckpoint())
                .then(conDungeon("save", ComandosAdmin::save))
                .then(conDungeon("reset", ComandosAdmin::reset))
                .then(conDungeon("stop", ComandosAdmin::stop))
                .then(conDungeon("enable", c -> habilitar(c, true)))
                .then(conDungeon("disable", c -> habilitar(c, false)))
                .then(nodoPardon())
                .then(conDungeon("tp", ComandosAdmin::tp))
                .then(Commands.literal("status").executes(ComandosAdmin::status));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> conDungeon(
            String nombre, Command<CommandSourceStack> accion) {
        return Commands.literal(nombre)
                .then(ArgumentosDungeon.argumentoDungeonCualquiera().executes(accion));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nodoCheckpoint() {
        return Commands.literal("checkpoint")
                .then(
                        ArgumentosDungeon.argumentoDungeonCualquiera()
                                .then(
                                        Commands.literal("add")
                                                .executes(
                                                        c ->
                                                                editar(
                                                                        c,
                                                                        "checkpoint add",
                                                                        ComandosAdmin
                                                                                ::anadirCheckpoint)))
                                .then(
                                        Commands.literal("remove")
                                                .executes(
                                                        c ->
                                                                editar(
                                                                        c,
                                                                        "checkpoint remove",
                                                                        ComandosAdmin
                                                                                ::quitarCheckpoint))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nodoPardon() {
        return Commands.literal("pardon")
                .then(
                        Commands.argument(JUGADOR, StringArgumentType.word())
                                .suggests(
                                        (c, b) ->
                                                SharedSuggestionProvider.suggest(
                                                        BanesDungeon.nombres(bans(c.getSource())),
                                                        b))
                                .executes(ComandosAdmin::pardon));
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        DungeonsConfig.Informe informe = Dungeons.recargarConfig();
        AuditoriaAdmin.registrar(c.getSource(), "reload", informe.resumen());
        String problemas =
                informe.problemas().isEmpty() ? "" : "\n" + informe.problemas().toString();
        return exito(c, "Config recargada: " + informe.resumen() + problemas);
    }

    private static int wand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        var varita = VaritaZona.crear();
        admin.getInventory().placeItemBackInInventory(varita, Prediction.SERVER_ONLY);
        AuditoriaAdmin.registrar(c.getSource(), "wand", "");
        return exito(
                c,
                "Clic izquierdo marca la esquina 1 y clic derecho la 2, dentro de la dimensión de"
                        + " dungeons.");
    }

    private static int setzone(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, ArgumentosDungeon.DUNGEON);
        Optional<Zona> zona = VaritaZona.selecciones().zona(admin.getUUID());
        if (zona.isEmpty()) {
            return fallo(c, "Marca las dos esquinas con la varita (/dungeon admin wand).");
        }
        if (ocupada(id)) {
            return fallo(c, "No se puede cambiar la zona con una partida o reinicio en curso.");
        }
        Resultado resultado =
                EdicionDungeon.definirZona(
                        id,
                        Dungeons.registro().config().dungeon(id),
                        zona.get(),
                        ArgumentosDungeon.todas());
        return guardar(c, "setzone", id, resultado);
    }

    private static int editar(
            CommandContext<CommandSourceStack> c,
            String accion,
            BiFunction<DefinicionDungeon, Punto, Resultado> edicion)
            throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        if (!DimensionDungeons.es(admin.level())) {
            return fallo(c, "Colócate dentro de la dimensión de dungeons.");
        }
        return guardar(c, accion, d.id(), edicion.apply(d, posicionDe(admin)));
    }

    private static Resultado setentry(DefinicionDungeon d, Punto punto) {
        return EdicionDungeon.definirEntrada(d, punto);
    }

    private static Resultado setexit(DefinicionDungeon d, Punto punto) {
        return EdicionDungeon.definirSalida(d, punto);
    }

    private static Resultado anadirCheckpoint(DefinicionDungeon d, Punto punto) {
        return EdicionDungeon.anadirCheckpoint(d, punto);
    }

    private static Resultado quitarCheckpoint(DefinicionDungeon d, Punto punto) {
        return EdicionDungeon.quitarCheckpoint(d, punto);
    }

    private static int save(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        CommandSourceStack fuente = c.getSource();
        Respuesta respuesta =
                Dungeons.plantillas()
                        .guardarPlantilla(
                                fuente.getServer(),
                                d.id(),
                                informe -> avisarGuardado(fuente, informe));
        return responder(c, "save", d.id(), respuesta, "Guardando la plantilla de " + d.id() + "…");
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        Respuesta respuesta = Dungeons.plantillas().reiniciar(c.getSource().getServer(), d.id());
        return responder(c, "reset", d.id(), respuesta, "Reiniciando " + d.id() + "…");
    }

    private static int responder(
            CommandContext<CommandSourceStack> c,
            String accion,
            String id,
            Respuesta respuesta,
            String exito) {
        if (respuesta != Respuesta.INICIADO) {
            return fallo(c, accion + " " + id + ": " + motivo(respuesta));
        }
        AuditoriaAdmin.registrar(c.getSource(), accion, id);
        return exito(c, exito);
    }

    private static String motivo(Respuesta respuesta) {
        return switch (respuesta) {
            case INICIADO -> "en marcha.";
            case DUNGEON_DESCONOCIDA -> "no existe esa dungeon.";
            case OCUPADA -> "tiene una partida, un reinicio o un guardado en curso.";
            case SIN_DIMENSION -> "la dimensión de dungeons no está cargada.";
            case ERROR_ARCHIVOS -> "no se pudo preparar la carpeta de la plantilla; mira el log.";
        };
    }

    private static void avisarGuardado(CommandSourceStack fuente, InformeGuardado informe) {
        if (informe.error().isPresent()) {
            fuente.sendFailure(
                    Component.literal(
                            "Falló el guardado de "
                                    + informe.dungeon()
                                    + " (la plantilla anterior sigue intacta): "
                                    + informe.error().get()));
            return;
        }
        String texto =
                "Plantilla de %s guardada: %d trozos, %d marcadores, %d entidades (%d ms)."
                        .formatted(
                                informe.dungeon(),
                                informe.trozos(),
                                informe.marcadores(),
                                informe.entidades(),
                                informe.milisegundos());
        fuente.sendSuccess(() -> Component.literal(texto), true);
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        Optional<Partida> partida =
                Dungeons.partidas()
                        .terminar(c.getSource().getServer(), d.id(), ResultadoPartida.CANCELADA);
        if (partida.isEmpty()) {
            return fallo(c, "No hay partida en " + d.id() + ".");
        }
        AuditoriaAdmin.registrar(c.getSource(), "stop", d.id() + " match=" + partida.get().id());
        return exito(c, "Partida de " + d.id() + " terminada, sin ban ni pérdida.");
    }

    private static int habilitar(CommandContext<CommandSourceStack> c, boolean habilitada)
            throws CommandSyntaxException {
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        Resultado resultado = new Resultado.Listo(d.conHabilitada(habilitada), nota(d.id()));
        return guardar(c, habilitada ? "enable" : "disable", d.id(), resultado);
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        DefinicionDungeon d = ArgumentosDungeon.dungeon(c);
        if (Dungeons.registro().ocupacion(admin.getUUID()).isPresent()) {
            return fallo(c, "Estás en una partida o fila: sal antes con /dungeon leave.");
        }
        if (!Dungeons.entradaSalida().observar(c.getSource().getServer(), admin, d.entrada())) {
            return fallo(c, "No se pudo entrar a la dimensión de dungeons.");
        }
        AuditoriaAdmin.registrar(c.getSource(), "tp", d.id());
        return exito(c, "En espectador en " + d.id() + ". Sal con /dungeon leave.");
    }

    private static int pardon(CommandContext<CommandSourceStack> c) {
        String nombre = StringArgumentType.getString(c, JUGADOR);
        int quitados = BanesDungeon.perdonar(bans(c.getSource()), nombre);
        if (quitados == 0) {
            return fallo(c, nombre + " no tiene ningún ban de dungeons vigente.");
        }
        AuditoriaAdmin.registrar(c.getSource(), "pardon", nombre);
        return exito(c, "Ban de dungeons quitado a " + nombre + ".");
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        RegistroDungeons registro = Dungeons.registro();
        StringBuilder texto = new StringBuilder("Dungeons:");
        registro.estados()
                .forEach(
                        (id, estado) ->
                                texto.append("\n- ")
                                        .append(id)
                                        .append(": ")
                                        .append(estado)
                                        .append(" · filas: ")
                                        .append(Dungeons.fila().gruposEnFila(id)));
        for (Partida partida : registro.partidas()) {
            texto.append("\nPartida ")
                    .append(partida.dungeon().id())
                    .append(" (")
                    .append(partida.dificultad().id())
                    .append("):");
            for (Miembro m : partida.miembros()) {
                texto.append(" ").append(m.nombre()).append("=").append(m.estado());
            }
        }
        c.getSource().sendSuccess(() -> Component.literal(texto.toString()), false);
        return 1;
    }

    private static int guardar(
            CommandContext<CommandSourceStack> c, String accion, String id, Resultado resultado) {
        return switch (resultado) {
            case Resultado.Rechazado(String motivo) -> fallo(c, motivo);
            case Resultado.Listo(DefinicionDungeon dungeon, var avisos) -> {
                try {
                    DungeonsConfig.Informe informe = Dungeons.guardarDungeon(dungeon);
                    AuditoriaAdmin.registrar(c.getSource(), accion, id);
                    avisos.forEach(a -> aviso(c, a));
                    if (!informe.problemas().isEmpty()) {
                        aviso(
                                c,
                                "La config tiene "
                                        + informe.problemas().size()
                                        + " aviso(s): mira el log.");
                    }
                    yield exito(c, accion + " " + id + ": guardado.");
                } catch (IOException e) {
                    DolfinCreepers.LOGGER.error(
                            "event=dungeon_config_save_failed dungeon={}", id, e);
                    yield fallo(c, "No se pudo escribir la config: " + e.getMessage());
                }
            }
        };
    }

    private static List<String> nota(String id) {
        return ocupada(id)
                ? List.of("Tiene partida o reinicio en curso: el cambio se aplica al terminar.")
                : List.of();
    }

    private static boolean ocupada(String id) {
        return Dungeons.registro()
                .estado(id)
                .filter(e -> e == EstadoDungeon.EN_PARTIDA || e == EstadoDungeon.REINICIANDO)
                .isPresent();
    }

    private static Punto posicionDe(ServerPlayer jugador) {
        return new Punto(
                jugador.getX(),
                jugador.getY(),
                jugador.getZ(),
                jugador.getYRot(),
                jugador.getXRot());
    }

    private static UserBanList bans(CommandSourceStack origen) {
        return origen.getServer().getPlayerList().getBans();
    }

    private static int exito(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendSuccess(() -> Component.literal(texto), true);
        return 1;
    }

    private static void aviso(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource()
                .sendSuccess(
                        () -> Component.literal(texto).withStyle(ChatFormatting.YELLOW), false);
    }

    private static int fallo(CommandContext<CommandSourceStack> c, String texto) {
        c.getSource().sendFailure(Component.literal(texto));
        return 0;
    }
}
