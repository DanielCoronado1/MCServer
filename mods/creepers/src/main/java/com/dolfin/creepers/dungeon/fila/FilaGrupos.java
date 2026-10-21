package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.ServicioPartidas;
import com.dolfin.creepers.dungeon.entrada.EntradaSalida;
import com.dolfin.creepers.dungeon.fila.Filas.Efecto;
import com.dolfin.creepers.dungeon.fila.Filas.Entrada;
import com.dolfin.creepers.dungeon.fila.Requisitos.Candidato;
import com.dolfin.creepers.dungeon.fila.Requisitos.Incumplimiento;
import com.dolfin.creepers.dungeon.grupo.Chat;
import com.dolfin.creepers.dungeon.grupo.Grupos;
import com.dolfin.creepers.dungeon.grupo.Nombres;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Entrada a las dungeons con grupos, fila por dungeon y ready check (#14). Une {@link Grupos}, la
 * máquina de estados {@link Filas} y los {@link Requisitos} con el juego: ocupa a cada miembro en
 * el registro mientras espera, aplica los {@link Efecto}s de la fila (avisos, liberar ocupaciones)
 * y empieza la partida cuando el ready check termina bien, revalidando antes los requisitos.
 *
 * <p>Solo el líder pide entrar; sin grupo, un jugador es un grupo de uno. Quien entra directo en
 * una dungeon libre cuenta como listo en su propio ready check. Si el grupo cambia mientras espera,
 * sus ocupaciones se ajustan: quien se une entra en la fila y quien se va sale; si se va quien
 * pidió entrar, el grupo entero sale de la fila.
 */
public final class FilaGrupos implements ServicioFila {
    private final RegistroDungeons registro;
    private final ServicioPartidas partidas;
    private final ConsultaProgreso progreso;
    private final BiPredicate<ServerPlayer, DefinicionDungeon> permiso;
    private final Supplier<MinecraftServer> server;
    private final Nombres nombres;
    private final Grupos grupos;
    private final Filas filas;
    private final AvisosFila avisos;

    /**
     * @param permiso si el jugador tiene el permiso de la dungeon
     * @param nombres nombres conocidos, compartidos con {@code /party}
     */
    public FilaGrupos(
            RegistroDungeons registro,
            ServicioPartidas partidas,
            ConsultaProgreso progreso,
            BiPredicate<ServerPlayer, DefinicionDungeon> permiso,
            Nombres nombres,
            Supplier<MinecraftServer> server) {
        this.registro = registro;
        this.partidas = partidas;
        this.progreso = progreso;
        this.permiso = permiso;
        this.server = server;
        this.nombres = nombres;
        this.filas = new Filas(new EntornoFilas());
        this.grupos = new Grupos(new ContextoGrupos());
        this.avisos = new AvisosFila(nombres, this::nombreDeDungeon);
        grupos.suscribir(this::alCambiarElGrupo);
    }

    /** Grupos de jugadores; {@code /party} los modifica. */
    public Grupos grupos() {
        return grupos;
    }

    @Override
    public ResultadoUnirse unirse(
            MinecraftServer server,
            ServerPlayer jugador,
            DefinicionDungeon dungeon,
            Dificultad dificultad) {
        UUID id = jugador.getUUID();
        Optional<String> impedimento = impedimento(jugador);
        if (impedimento.isPresent()) {
            return new ResultadoUnirse.Bloqueado(impedimento.get());
        }
        List<UUID> ids = grupos.miembrosDe(id);
        List<ServerPlayer> miembros = conectados(server, ids);
        if (miembros.size() < ids.size()) {
            return new ResultadoUnirse.Bloqueado(desconectados(ids, miembros));
        }
        List<Incumplimiento> fallos =
                Requisitos.validar(contexto(dungeon, dificultad), candidatos(miembros, dungeon));
        if (!fallos.isEmpty()) {
            return rechazadoPorRequisitos(server, id, ids, fallos);
        }
        return encolar(server, jugador, dungeon, dificultad, ids);
    }

    @Override
    public boolean salir(UUID jugador) {
        MinecraftServer s = server.get();
        if (s == null || filas.ubicacion(jugador).isEmpty()) {
            return registro.salirDeFila(jugador);
        }
        aplicar(s, filas.salir(jugador, s.getTickCount()), jugador);
        return true;
    }

    @Override
    public int gruposEnFila(String dungeonId) {
        return filas.grupos(dungeonId);
    }

    @Override
    public OptionalInt posicion(UUID jugador) {
        return filas.posicion(jugador);
    }

    @Override
    public Optional<String> bloqueo(UUID jugador, DefinicionDungeon dungeon) {
        Requisitos.Contexto contexto = contexto(dungeon, Dificultad.FACIL);
        Candidato candidato = new Candidato(jugador, nombres.de(jugador), true, Optional.empty());
        return Requisitos.deJugador(contexto, candidato).stream()
                .map(Incumplimiento::texto)
                .findFirst();
    }

    @Override
    public Optional<String> confirmar(MinecraftServer server, ServerPlayer jugador) {
        UUID id = jugador.getUUID();
        if (!filas.tieneReadyCheck(id)) {
            return Optional.of("No tienes ningún ready check pendiente.");
        }
        aplicar(server, filas.responder(id, true, server.getTickCount()), id);
        return Optional.empty();
    }

    @Override
    public void tick(MinecraftServer server) {
        grupos.vencer(System.currentTimeMillis());
        aplicar(server, filas.tick(server.getTickCount()), null);
    }

    @Override
    public void alConectarse(ServerPlayer jugador) {
        nombres.recordar(jugador);
        grupos.conectar(jugador.getUUID());
    }

    @Override
    public void alDesconectarse(ServerPlayer jugador) {
        grupos.desconectar(jugador.getUUID(), System.currentTimeMillis());
    }

    @Override
    public void alArrancar() {
        filas.limpiar();
        grupos.limpiar();
        nombres.olvidarTodo();
    }

    /** Solo el líder pide entrar, y nadie que ya esté en una partida o fila. */
    private Optional<String> impedimento(ServerPlayer jugador) {
        UUID id = jugador.getUUID();
        if (!grupos.puedeDirigir(id)) {
            return Optional.of("Solo el líder del grupo puede entrar a una dungeon.");
        }
        return registro.ocupacion(id).isPresent()
                ? Optional.of("Ya estás en una partida o en una fila.")
                : Optional.empty();
    }

    /** Ocupa a todos los miembros y entra en la fila; puede que el ready check termine ya. */
    private ResultadoUnirse encolar(
            MinecraftServer server,
            ServerPlayer jugador,
            DefinicionDungeon dungeon,
            Dificultad dificultad,
            List<UUID> ids) {
        Optional<String> ocupado = ocupar(dungeon.id(), ids);
        if (ocupado.isPresent()) {
            return new ResultadoUnirse.Bloqueado(ocupado.get());
        }
        UUID id = jugador.getUUID();
        boolean directa = filas.grupos(dungeon.id()) == 0;
        Entrada entrada = new Entrada(id, dificultad);
        List<Efecto> efectos = filas.unirse(dungeon.id(), entrada, directa, server.getTickCount());
        Map<UUID, ResultadoUnirse> terminados = aplicar(server, efectos, id);
        ResultadoUnirse resultado = terminados.get(id);
        if (resultado != null) {
            return resultado;
        }
        OptionalInt puesto = filas.posicion(id);
        return puesto.isPresent()
                ? new ResultadoUnirse.EnFila(puesto.getAsInt(), filas.grupos(dungeon.id()))
                : new ResultadoUnirse.Bloqueado(
                        "Has salido de la fila de " + dungeon.nombre() + ".");
    }

    /** Marca a cada jugador en la fila de la dungeon; si alguno no puede, deshace todo. */
    private Optional<String> ocupar(String dungeon, List<UUID> ids) {
        for (UUID id : ids) {
            if (registro.ocupacion(id).isPresent() || !registro.entrarEnFila(id, dungeon)) {
                ids.forEach(registro::salirDeFila);
                return Optional.of(nombres.de(id) + " ya está en una partida o en una fila.");
            }
        }
        return Optional.empty();
    }

    private ResultadoUnirse rechazadoPorRequisitos(
            MinecraftServer server, UUID solicitante, List<UUID> ids, List<Incumplimiento> fallos) {
        String texto = textos(fallos);
        notificarFallos(server, ids, solicitante, fallos);
        return new ResultadoUnirse.Bloqueado(texto);
    }

    /** Cuenta a los demás miembros qué falla, para que sepan por qué no entran. */
    private void notificarFallos(
            MinecraftServer server, List<UUID> ids, UUID excepto, List<Incumplimiento> fallos) {
        List<UUID> otros = ids.stream().filter(m -> !m.equals(excepto)).toList();
        for (Incumplimiento fallo : fallos) {
            Chat.enviar(server, otros, Chat.texto(fallo.texto(), ChatFormatting.RED));
        }
    }

    /**
     * Aplica los efectos: avisos, liberar a quien sale de la fila y empezar las partidas de los
     * ready checks completos. Tras empezar una partida vuelve a procesar la fila, porque el
     * siguiente grupo puede tener que esperar o empezar su ready check.
     *
     * @param excepto jugador cuyo comando muestra el resultado (no se le repite en el chat)
     * @return resultado de cada entrada que completó su ready check, por solicitante
     */
    private Map<UUID, ResultadoUnirse> aplicar(
            MinecraftServer s, List<Efecto> inicial, UUID excepto) {
        Map<UUID, ResultadoUnirse> terminados = new HashMap<>();
        List<Efecto> efectos = inicial;
        while (!efectos.isEmpty()) {
            boolean completada = false;
            for (Efecto efecto : efectos) {
                if (efecto instanceof Efecto.Completa c) {
                    terminados.put(c.entrada().solicitante(), completar(s, c, excepto));
                    completada = true;
                } else {
                    efectoSimple(s, efecto, excepto);
                }
            }
            efectos = completada ? filas.tick(s.getTickCount()) : List.of();
        }
        return terminados;
    }

    private void efectoSimple(MinecraftServer s, Efecto efecto, UUID excepto) {
        if (efecto instanceof Efecto.Retirada r) {
            r.miembros().forEach(registro::salirDeFila);
        }
        avisos.avisar(s, efecto, registro.config().partida().readyCheckSegundos(), excepto);
    }

    /** Fin del ready check: revalida los requisitos y empieza la partida. */
    private ResultadoUnirse completar(MinecraftServer s, Efecto.Completa c, UUID excepto) {
        List<UUID> ids = List.copyOf(c.miembros());
        Optional<DefinicionDungeon> dungeon = registro.config().dungeon(c.dungeon());
        List<ServerPlayer> jugadores = conectados(s, ids);
        if (dungeon.isEmpty() || jugadores.size() < ids.size()) {
            ids.forEach(registro::salirDeFila);
            String texto =
                    dungeon.isEmpty() ? "La dungeon ya no existe." : desconectados(ids, jugadores);
            return fallar(s, ids, excepto, texto);
        }
        Dificultad dificultad = c.entrada().dificultad();
        List<Incumplimiento> fallos =
                Requisitos.validar(
                        contexto(dungeon.get(), dificultad), candidatos(jugadores, dungeon.get()));
        if (!fallos.isEmpty()) {
            ids.forEach(registro::salirDeFila);
            notificarFallos(s, ids, excepto, fallos);
            return new ResultadoUnirse.Bloqueado(textos(fallos));
        }
        return empezar(s, c, jugadores, dificultad, excepto);
    }

    private ResultadoUnirse empezar(
            MinecraftServer s,
            Efecto.Completa c,
            List<ServerPlayer> jugadores,
            Dificultad dificultad,
            UUID excepto) {
        ResultadoInicio resultado = partidas.iniciar(s, c.dungeon(), dificultad, jugadores);
        return switch (resultado) {
            case ResultadoInicio.Iniciada(var partida) -> new ResultadoUnirse.Entro(partida);
            case ResultadoInicio.Rechazada(var rechazos) -> {
                c.miembros().forEach(registro::salirDeFila);
                ResultadoUnirse.Rechazado rechazado = new ResultadoUnirse.Rechazado(rechazos);
                Chat.enviar(
                        s,
                        c.miembros().stream().filter(m -> !m.equals(excepto)).toList(),
                        Chat.texto(
                                "No se pudo entrar a " + nombreDeDungeon(c.dungeon()) + ".",
                                ChatFormatting.RED));
                yield rechazado;
            }
        };
    }

    private ResultadoUnirse fallar(MinecraftServer s, List<UUID> ids, UUID excepto, String texto) {
        Chat.enviar(
                s,
                ids.stream().filter(m -> !m.equals(excepto)).toList(),
                Chat.texto(texto, ChatFormatting.RED));
        return new ResultadoUnirse.Bloqueado(texto);
    }

    /** Mantiene las ocupaciones al día cuando cambia el grupo de quien espera. */
    private void alCambiarElGrupo(Grupos.Evento evento) {
        MinecraftServer s = server.get();
        if (s == null) {
            return;
        }
        switch (evento) {
            case Grupos.Evento.Unido u -> alUnirse(s, u);
            case Grupos.Evento.Salio salio -> alSalir(s, salio);
            default -> {}
        }
    }

    private void alUnirse(MinecraftServer s, Grupos.Evento.Unido u) {
        for (UUID otro : u.miembros()) {
            Optional<Filas.Ubicacion> ubicacion =
                    otro.equals(u.jugador()) ? Optional.empty() : filas.ubicacion(otro);
            if (ubicacion.isPresent()) {
                String dungeon = ubicacion.get().dungeon();
                if (registro.entrarEnFila(u.jugador(), dungeon)) {
                    int puesto = filas.posicion(u.jugador()).orElse(1);
                    Chat.enviar(
                            s,
                            List.of(u.jugador()),
                            Chat.texto(
                                    TextosFila.enFila(
                                            nombreDeDungeon(dungeon),
                                            puesto,
                                            filas.grupos(dungeon)),
                                    ChatFormatting.YELLOW));
                }
                return;
            }
        }
    }

    /**
     * Quien se va sale de la fila; si era quien pidió entrar, todo el grupo sale con él y los que
     * quedan lo saben.
     */
    private void alSalir(MinecraftServer s, Grupos.Evento.Salio salio) {
        UUID jugador = salio.jugador();
        Optional<Filas.Ubicacion> ubicacion = filas.ubicacion(jugador);
        if (ubicacion.isEmpty()) {
            return;
        }
        boolean eraSolicitante = ubicacion.get().entrada().solicitante().equals(jugador);
        if (eraSolicitante) {
            salio.miembros().forEach(registro::salirDeFila);
            Chat.enviar(
                    s,
                    salio.miembros(),
                    Chat.texto(
                            nombres.de(jugador)
                                    + " ha dejado el grupo: sales de la fila de "
                                    + nombreDeDungeon(ubicacion.get().dungeon())
                                    + ".",
                            ChatFormatting.RED));
            aplicar(s, filas.salir(jugador, s.getTickCount()), jugador);
        } else {
            registro.salirDeFila(jugador);
        }
    }

    private Requisitos.Contexto contexto(DefinicionDungeon dungeon, Dificultad dificultad) {
        EstadoDungeon estado = registro.estado(dungeon.id()).orElse(EstadoDungeon.DESHABILITADA);
        return new Requisitos.Contexto(
                dungeon,
                dificultad,
                registro.config().general().activo(),
                estado,
                this::nombreDeDungeon,
                progreso,
                System.currentTimeMillis());
    }

    private List<Candidato> candidatos(List<ServerPlayer> jugadores, DefinicionDungeon dungeon) {
        return jugadores.stream()
                .map(
                        j ->
                                new Candidato(
                                        j.getUUID(),
                                        j.getName().getString(),
                                        permiso.test(j, dungeon),
                                        modoNoValido(j)))
                .toList();
    }

    private static Optional<String> modoNoValido(ServerPlayer jugador) {
        if (EntradaSalida.modoPermiteEntrar(jugador)) {
            return Optional.empty();
        }
        return Optional.of(jugador.gameMode() == GameType.SPECTATOR ? "espectador" : "creativo");
    }

    private String nombreDeDungeon(String id) {
        return registro.config().dungeon(id).map(DefinicionDungeon::nombre).orElse(id);
    }

    private List<ServerPlayer> conectados(MinecraftServer s, List<UUID> ids) {
        return ids.stream()
                .map(id -> s.getPlayerList().getPlayer(id))
                .filter(Objects::nonNull)
                .toList();
    }

    private String desconectados(List<UUID> ids, List<ServerPlayer> conectados) {
        List<UUID> presentes = conectados.stream().map(ServerPlayer::getUUID).toList();
        String fuera =
                ids.stream()
                        .filter(id -> !presentes.contains(id))
                        .map(nombres::de)
                        .collect(Collectors.joining(", "));
        return fuera + " está desconectado: todo el grupo debe estar conectado.";
    }

    private static String textos(List<Incumplimiento> fallos) {
        return fallos.stream().map(Incumplimiento::texto).collect(Collectors.joining("; "));
    }

    private final class EntornoFilas implements Filas.Entorno {
        @Override
        public Optional<EstadoDungeon> estado(String dungeon) {
            return registro.estado(dungeon);
        }

        @Override
        public List<UUID> miembros(UUID jugador) {
            return grupos.miembrosDe(jugador);
        }

        @Override
        public boolean enLinea(UUID jugador) {
            MinecraftServer s = server.get();
            return s != null && s.getPlayerList().getPlayer(jugador) != null;
        }

        @Override
        public int readyCheckSegundos() {
            return registro.config().partida().readyCheckSegundos();
        }
    }

    private final class ContextoGrupos implements Grupos.Contexto {
        @Override
        public boolean enPartida(UUID jugador) {
            return registro.partidaDe(jugador).isPresent();
        }

        @Override
        public boolean ocupado(UUID jugador) {
            return registro.ocupacion(jugador).isPresent();
        }

        @Override
        public boolean enReadyCheck(UUID jugador) {
            return filas.tieneReadyCheck(jugador);
        }
    }
}
