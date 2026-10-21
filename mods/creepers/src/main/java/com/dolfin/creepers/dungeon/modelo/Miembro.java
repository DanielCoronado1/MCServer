package com.dolfin.creepers.dungeon.modelo;

import java.util.UUID;

/**
 * Estado de un jugador dentro de su {@link Partida}. Solo {@link RegistroDungeons} lo modifica,
 * para que cada cambio publique su evento; el resto del código lo lee.
 */
public final class Miembro {
    /** Valor de {@link #vidas()} cuando la dificultad no limita las vidas. */
    public static final int VIDAS_ILIMITADAS = -1;

    /** Valor de {@link #checkpoint()} antes de alcanzar el primero: reaparece en la entrada. */
    public static final int SIN_CHECKPOINT = -1;

    private final UUID id;
    private final String nombre;
    private EstadoMiembro estado = EstadoMiembro.VIVO;
    private EstadoMiembro estadoAlDesconectar = EstadoMiembro.VIVO;
    private int vidas;
    private int checkpoint = SIN_CHECKPOINT;
    private long plazoReconexionTick;

    Miembro(UUID id, String nombre, int vidas) {
        this.id = id;
        this.nombre = nombre;
        this.vidas = vidas;
    }

    public UUID id() {
        return id;
    }

    /** Nombre del jugador al empezar la partida, para mensajes mientras está desconectado. */
    public String nombre() {
        return nombre;
    }

    public EstadoMiembro estado() {
        return estado;
    }

    /** Vidas restantes, o {@link #VIDAS_ILIMITADAS}. */
    public int vidas() {
        return vidas;
    }

    /** Índice del último checkpoint alcanzado, o {@link #SIN_CHECKPOINT}. */
    public int checkpoint() {
        return checkpoint;
    }

    /** Tick del servidor en que vence el plazo de reconexión; solo vale si está desconectado. */
    public long plazoReconexionTick() {
        return plazoReconexionTick;
    }

    /** Estado que recupera al reconectar; solo vale si está desconectado. */
    public EstadoMiembro estadoAlDesconectar() {
        return estadoAlDesconectar;
    }

    void desconectar(long plazoTick) {
        estadoAlDesconectar = estado;
        estado = EstadoMiembro.DESCONECTADO;
        plazoReconexionTick = plazoTick;
    }

    void reconectar() {
        estado = estadoAlDesconectar;
    }

    void estado(EstadoMiembro nuevo) {
        estado = nuevo;
    }

    int perderVida() {
        if (vidas > 0) {
            vidas--;
        }
        return vidas;
    }

    void checkpoint(int indice) {
        checkpoint = indice;
    }
}
