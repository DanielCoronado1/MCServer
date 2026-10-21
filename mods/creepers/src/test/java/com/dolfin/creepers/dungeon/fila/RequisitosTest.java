package com.dolfin.creepers.dungeon.fila;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.fila.Requisitos.Candidato;
import com.dolfin.creepers.dungeon.fila.Requisitos.Contexto;
import com.dolfin.creepers.dungeon.fila.Requisitos.Incumplimiento;
import com.dolfin.creepers.dungeon.fila.Requisitos.Regla;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.Zona;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class RequisitosTest {
    static final long AHORA = 1_000_000;

    final Set<String> completadas = new HashSet<>();
    final Map<UUID, Long> cooldownHasta = new HashMap<>();

    final ConsultaProgreso progreso =
            new ConsultaProgreso() {
                @Override
                public boolean completo(UUID jugador, String dungeon) {
                    return completadas.contains(jugador + ":" + dungeon);
                }

                @Override
                public long cooldownRestanteMillis(UUID jugador, String dungeon, long ahora) {
                    return Math.max(0, cooldownHasta.getOrDefault(jugador, 0L) - ahora);
                }
            };

    static Candidato candidato(String nombre) {
        return new Candidato(
                UUID.nameUUIDFromBytes(nombre.getBytes()), nombre, true, Optional.empty());
    }

    static DefinicionDungeon dungeon(int min, int max, List<String> requisitos) {
        DefinicionDungeon base = CodecDefinicion.porDefecto("cripta", Zona.ranura(0));
        return new DefinicionDungeon(
                base.id(),
                "Cripta",
                true,
                base.zona(),
                base.entrada(),
                base.checkpoints(),
                base.salida(),
                base.modo(),
                min,
                max,
                Set.of(Dificultad.MEDIO, Dificultad.HARDCORE),
                base.permiso(),
                requisitos,
                10,
                0);
    }

    Contexto contexto(DefinicionDungeon d, Dificultad dificultad, EstadoDungeon estado) {
        return new Contexto(d, dificultad, true, estado, id -> "Torre", progreso, AHORA);
    }

    List<String> textos(List<Incumplimiento> fallos) {
        return fallos.stream().map(Incumplimiento::texto).toList();
    }

    @Test
    void sinIncumplimientosPuedeEntrar() {
        var d = dungeon(1, 4, List.of());
        assertTrue(
                Requisitos.validar(
                                contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE),
                                List.of(candidato("Pepe")))
                        .isEmpty());
    }

    @Test
    void deshabilitadaCierraPorMantenimientoYNadaMas() {
        var d = dungeon(1, 4, List.of());
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.FACIL, EstadoDungeon.DESHABILITADA),
                        List.of(candidato("Pepe")));
        assertEquals(List.of("Esta dungeon está cerrada por mantenimiento"), textos(fallos));
    }

    @Test
    void sistemaDesactivadoSeAvisa() {
        var d = dungeon(1, 4, List.of());
        var contexto =
                new Contexto(
                        d, Dificultad.MEDIO, false, EstadoDungeon.LIBRE, id -> id, progreso, AHORA);
        var fallos = Requisitos.validar(contexto, List.of(candidato("Pepe")));
        assertEquals(Regla.SISTEMA_DESACTIVADO, fallos.get(0).regla());
    }

    @Test
    void dificultadNoPermitida() {
        var d = dungeon(1, 4, List.of());
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.FACIL, EstadoDungeon.LIBRE),
                        List.of(candidato("Pepe")));
        assertEquals(List.of("Cripta no tiene modo fácil"), textos(fallos));
    }

    @Test
    void tamanioDelGrupoConRango() {
        var d = dungeon(1, 4, List.of());
        var cinco =
                List.of(
                        candidato("a"),
                        candidato("b"),
                        candidato("c"),
                        candidato("d"),
                        candidato("e"));
        var fallos = Requisitos.validar(contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE), cinco);
        assertEquals(List.of("Cripta admite de 1 a 4 jugadores"), textos(fallos));
    }

    @Test
    void tamanioExactoEnSingularYPlural() {
        var solo = dungeon(1, 1, List.of());
        var pareja = dungeon(2, 2, List.of());
        var dos = List.of(candidato("a"), candidato("b"));
        assertEquals(
                List.of("Cripta admite 1 jugador"),
                textos(
                        Requisitos.validar(
                                contexto(solo, Dificultad.MEDIO, EstadoDungeon.LIBRE), dos)));
        assertEquals(
                List.of("Cripta admite 2 jugadores"),
                textos(
                        Requisitos.validar(
                                contexto(pareja, Dificultad.MEDIO, EstadoDungeon.LIBRE),
                                List.of(candidato("a")))));
    }

    @Test
    void sinPermisoSeDiceQuien() {
        var d = dungeon(1, 4, List.of());
        var sinAcceso = new Candidato(UUID.randomUUID(), "Pepe", false, Optional.empty());
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE),
                        List.of(candidato("Ana"), sinAcceso));
        assertEquals(List.of("Pepe no tiene acceso a Cripta"), textos(fallos));
        assertEquals(Optional.of("Pepe"), fallos.get(0).jugador());
    }

    @Test
    void dungeonPreviaSinCompletar() {
        var d = dungeon(1, 4, List.of("torre"));
        Candidato pepe = candidato("Pepe");
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE), List.of(pepe));
        assertEquals(List.of("Pepe necesita completar Torre primero"), textos(fallos));
        completadas.add(pepe.id() + ":torre");
        assertTrue(
                Requisitos.validar(
                                contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE), List.of(pepe))
                        .isEmpty());
    }

    @Test
    void cooldownEnMinutosRedondeadoHaciaArriba() {
        var d = dungeon(1, 4, List.of());
        Candidato pepe = candidato("Pepe");
        cooldownHasta.put(pepe.id(), AHORA + 11 * 60_000 + 1);
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE), List.of(pepe));
        assertEquals(List.of("Pepe podrá volver a entrar en 12 min"), textos(fallos));
    }

    @Test
    void cooldownCortoEnSegundos() {
        assertEquals("40 s", Requisitos.tiempo(40_000));
        assertEquals("1 min", Requisitos.tiempo(60_000));
        assertEquals("1 s", Requisitos.tiempo(1));
    }

    @Test
    void cooldownVencidoNoBloquea() {
        var d = dungeon(1, 4, List.of());
        Candidato pepe = candidato("Pepe");
        cooldownHasta.put(pepe.id(), AHORA);
        assertTrue(
                Requisitos.validar(
                                contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE), List.of(pepe))
                        .isEmpty());
    }

    @Test
    void modoDeJuegoNoValido() {
        var d = dungeon(1, 4, List.of());
        var creativo = new Candidato(UUID.randomUUID(), "Pepe", true, Optional.of("creativo"));
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE), List.of(creativo));
        assertEquals(List.of("Pepe está en modo creativo"), textos(fallos));
    }

    @Test
    void variosMiembrosYVariasReglasSeAcumulan() {
        var d = dungeon(1, 4, List.of("torre"));
        var sinAcceso = new Candidato(UUID.randomUUID(), "Pepe", false, Optional.empty());
        Candidato ana = candidato("Ana");
        cooldownHasta.put(ana.id(), AHORA + 60_000);
        var fallos =
                Requisitos.validar(
                        contexto(d, Dificultad.MEDIO, EstadoDungeon.LIBRE),
                        List.of(sinAcceso, ana));
        assertEquals(
                List.of(
                        Regla.PERMISO,
                        Regla.COMPLETADA_PREVIA,
                        Regla.COMPLETADA_PREVIA,
                        Regla.COOLDOWN),
                fallos.stream().map(Incumplimiento::regla).toList());
    }
}
