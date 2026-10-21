package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Requisitos para entrar a una dungeon (#14), sin Minecraft. Se validan al pedir entrar y otra vez
 * al salir de la fila. Si falla uno, nadie entra y se dice quién y por qué: cada {@link
 * Incumplimiento} trae su texto listo para mostrar.
 */
public final class Requisitos {
    private Requisitos() {}

    /**
     * Un jugador a validar.
     *
     * @param permiso tiene el permiso de la dungeon
     * @param modoNoValido nombre del modo de juego ({@code creativo}, {@code espectador}) si no es
     *     supervivencia ni aventura
     */
    public record Candidato(
            UUID id, String nombre, boolean permiso, Optional<String> modoNoValido) {}

    /** Qué regla incumple. */
    public enum Regla {
        SISTEMA_DESACTIVADO,
        DESHABILITADA,
        DIFICULTAD,
        TAMANIO_GRUPO,
        PERMISO,
        COMPLETADA_PREVIA,
        COOLDOWN,
        MODO_DE_JUEGO
    }

    /** Un requisito sin cumplir; {@code jugador} es el culpable si la regla es de jugador. */
    public record Incumplimiento(Regla regla, Optional<String> jugador, String texto) {}

    /**
     * Contexto de la validación.
     *
     * @param sistemaActivo {@code general.activo}
     * @param estado estado de la dungeon ahora
     * @param nombreDeDungeon nombre visible de una dungeon por su id (para los requisitos previos)
     * @param ahoraMillis hora real, para los cooldowns
     */
    public record Contexto(
            DefinicionDungeon dungeon,
            Dificultad dificultad,
            boolean sistemaActivo,
            EstadoDungeon estado,
            Function<String, String> nombreDeDungeon,
            ConsultaProgreso progreso,
            long ahoraMillis) {}

    /**
     * Todos los incumplimientos del grupo, vacío si puede entrar. Los de la dungeon (cerrada,
     * dificultad, tamaño) van solos porque hacen irrelevante lo demás; después, los de cada miembro
     * en orden.
     */
    public static List<Incumplimiento> validar(Contexto contexto, List<Candidato> grupo) {
        List<Incumplimiento> deDungeon = deDungeon(contexto, grupo.size());
        if (!deDungeon.isEmpty()) {
            return deDungeon;
        }
        List<Incumplimiento> fallos = new ArrayList<>();
        for (Candidato candidato : grupo) {
            fallos.addAll(deJugador(contexto, candidato));
        }
        return fallos;
    }

    /** Solo los requisitos personales, sin tamaño de grupo ni dificultad. */
    public static List<Incumplimiento> deJugador(Contexto contexto, Candidato candidato) {
        List<Incumplimiento> fallos = new ArrayList<>();
        DefinicionDungeon d = contexto.dungeon();
        if (!candidato.permiso()) {
            fallos.add(
                    deJugador(
                            Regla.PERMISO,
                            candidato,
                            candidato.nombre() + " no tiene acceso a " + d.nombre()));
        }
        for (String previa : d.requisitos()) {
            if (!contexto.progreso().completo(candidato.id(), previa)) {
                fallos.add(
                        deJugador(
                                Regla.COMPLETADA_PREVIA,
                                candidato,
                                candidato.nombre()
                                        + " necesita completar "
                                        + contexto.nombreDeDungeon().apply(previa)
                                        + " primero"));
            }
        }
        long restante =
                contexto.progreso()
                        .cooldownRestanteMillis(candidato.id(), d.id(), contexto.ahoraMillis());
        if (restante > 0) {
            fallos.add(
                    deJugador(
                            Regla.COOLDOWN,
                            candidato,
                            candidato.nombre() + " podrá volver a entrar en " + tiempo(restante)));
        }
        if (candidato.modoNoValido().isPresent()) {
            fallos.add(
                    deJugador(
                            Regla.MODO_DE_JUEGO,
                            candidato,
                            candidato.nombre()
                                    + " está en modo "
                                    + candidato.modoNoValido().get()));
        }
        return fallos;
    }

    /** Duración legible: minutos redondeados hacia arriba, o segundos si falta menos de uno. */
    static String tiempo(long milisegundos) {
        long segundos = (milisegundos + 999) / 1000;
        return segundos < 60 ? segundos + " s" : (segundos + 59) / 60 + " min";
    }

    /** Nombre de una dificultad para los mensajes ({@code fácil}, {@code medio}…). */
    static String nombre(Dificultad dificultad) {
        return switch (dificultad) {
            case FACIL -> "fácil";
            case MEDIO -> "medio";
            case HARDCORE -> "hardcore";
        };
    }

    private static List<Incumplimiento> deDungeon(Contexto contexto, int jugadores) {
        DefinicionDungeon d = contexto.dungeon();
        if (!contexto.sistemaActivo()) {
            return List.of(deDungeon(Regla.SISTEMA_DESACTIVADO, "Las dungeons están desactivadas"));
        }
        if (contexto.estado() == EstadoDungeon.DESHABILITADA) {
            return List.of(
                    deDungeon(Regla.DESHABILITADA, "Esta dungeon está cerrada por mantenimiento"));
        }
        if (!d.dificultades().contains(contexto.dificultad())) {
            return List.of(
                    deDungeon(
                            Regla.DIFICULTAD,
                            d.nombre() + " no tiene modo " + nombre(contexto.dificultad())));
        }
        if (jugadores < d.minJugadores() || jugadores > d.maxJugadores()) {
            return List.of(deDungeon(Regla.TAMANIO_GRUPO, tamanio(d)));
        }
        return List.of();
    }

    private static String tamanio(DefinicionDungeon d) {
        if (d.minJugadores() == d.maxJugadores()) {
            return d.nombre()
                    + " admite "
                    + d.minJugadores()
                    + (d.minJugadores() == 1 ? " jugador" : " jugadores");
        }
        return d.nombre()
                + " admite de "
                + d.minJugadores()
                + " a "
                + d.maxJugadores()
                + " jugadores";
    }

    private static Incumplimiento deDungeon(Regla regla, String texto) {
        return new Incumplimiento(regla, Optional.empty(), texto);
    }

    private static Incumplimiento deJugador(Regla regla, Candidato candidato, String texto) {
        return new Incumplimiento(regla, Optional.of(candidato.nombre()), texto);
    }
}
