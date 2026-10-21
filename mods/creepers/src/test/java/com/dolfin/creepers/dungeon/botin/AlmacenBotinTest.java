package com.dolfin.creepers.dungeon.botin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class AlmacenBotinTest {

    private static SimpleContainer contenedorConContenido(int capacidad) {
        return new SimpleContainer(capacidad) {
            private boolean vacio = false;

            @Override
            public boolean isEmpty() {
                return vacio;
            }

            @Override
            public void clearContent() {
                vacio = true;
            }
        };
    }

    @Test
    void aislamientoPorJugador() {
        AlmacenBotin almacen = new AlmacenBotin();
        UUID partidaId = UUID.randomUUID();
        BlockPos cofre = new BlockPos(10, 60, 10);
        UUID jugador1 = UUID.randomUUID();
        UUID jugador2 = UUID.randomUUID();

        SimpleContainer cont1 = contenedorConContenido(27);
        almacen.guardar(partidaId, cofre, jugador1, cont1);

        SimpleContainer cont2 = contenedorConContenido(27);
        almacen.guardar(partidaId, cofre, jugador2, cont2);

        // Los contenedores son instancias distintas y no se interfieren
        SimpleContainer obtenido1 = almacen.obtener(partidaId, cofre, jugador1).orElseThrow();
        SimpleContainer obtenido2 = almacen.obtener(partidaId, cofre, jugador2).orElseThrow();
        assertNotSame(obtenido1, obtenido2);

        // Vaciar uno no afecta al otro
        obtenido1.clearContent();
        assertTrue(obtenido1.isEmpty());
        assertFalse(obtenido2.isEmpty());
    }

    @Test
    void estadoVaciado() {
        AlmacenBotin almacen = new AlmacenBotin();
        UUID partidaId = UUID.randomUUID();
        BlockPos cofre = new BlockPos(10, 60, 10);
        UUID jugador = UUID.randomUUID();

        // Antes de guardar o abrir, no está vaciado
        assertFalse(almacen.yaVaciado(partidaId, cofre, jugador));

        SimpleContainer cont = contenedorConContenido(27);
        almacen.guardar(partidaId, cofre, jugador, cont);
        assertFalse(almacen.yaVaciado(partidaId, cofre, jugador));

        // Al vaciar los objetos
        cont.clearContent();
        assertTrue(almacen.yaVaciado(partidaId, cofre, jugador));
    }

    @Test
    void limpiezaAlTerminarPartida() {
        AlmacenBotin almacen = new AlmacenBotin();
        UUID partida1 = UUID.randomUUID();
        UUID partida2 = UUID.randomUUID();
        BlockPos cofre = new BlockPos(10, 60, 10);
        UUID jugador = UUID.randomUUID();

        almacen.guardar(partida1, cofre, jugador, new SimpleContainer(27));
        almacen.guardar(partida2, cofre, jugador, new SimpleContainer(27));
        assertEquals(2, almacen.partidasActivas());

        almacen.limpiarPartida(partida1);
        assertEquals(1, almacen.partidasActivas());
        assertTrue(almacen.obtener(partida1, cofre, jugador).isEmpty());
        assertTrue(almacen.obtener(partida2, cofre, jugador).isPresent());
    }
}
