package com.dolfin.creepers.dungeon.modelo;

/**
 * Ciclo de vida de una dungeon: {@code LIBRE → EN_PARTIDA → REINICIANDO → LIBRE}. {@code
 * DESHABILITADA} sale de la config o de {@code /dungeon admin disable} y solo cambia cuando no hay
 * partida ni reinicio en curso.
 */
public enum EstadoDungeon {
    LIBRE,
    EN_PARTIDA,
    REINICIANDO,
    DESHABILITADA;

    /** Estados que deben sobrevivir a una caída: al arrancar se reinicia la zona (#19). */
    public boolean requiereReinicioTrasCaida() {
        return this == EN_PARTIDA || this == REINICIANDO;
    }
}
