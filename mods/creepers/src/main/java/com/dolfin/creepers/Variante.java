package com.dolfin.creepers;

/**
 * Las variantes de creeper y sus estadisticas.
 *
 * <p>vida: puntos de vida (el creeper normal tiene 20 = 10 corazones) escala: tamano (1 = normal)
 * velocidad: multiplicador de la velocidad normal mecha: ticks que tarda en explotar (normal 30 =
 * 1,5 s) radio: fuerza de la explosion (normal 3) rompeBloques: si su explosion destruye el terreno
 * peso: probabilidad de aparecer de forma natural (el creeper normal tiene 100; 0 = nunca)
 */
public enum Variante {
    //                vida   escala vel   mecha radio rompe  peso grupo
    FUEGO(24, 1.0, 1.0, 30, 3, true, 12, 1, 1),
    HIELO(24, 1.0, 1.0, 30, 3, false, 18, 1, 2),
    SALTARIN(16, 0.75, 1.35, 20, 2, true, 10, 1, 2),
    MINI(6, 0.45, 1.3, 20, 1, false, 10, 2, 4),
    ELECTRICO(20, 1.0, 1.0, 30, 3, true, 6, 1, 1),
    MULTIPLICADOR(20, 1.0, 1.0, 30, 2, true, 8, 1, 1),
    GIGANTE(60, 2.0, 0.8, 50, 5, true, 4, 1, 1),
    TOXICO(20, 1.0, 1.0, 30, 2, false, 8, 1, 1),
    REY(300, 3.0, 0.9, 40, 4, false, 0, 1, 1);

    public final double vida;
    public final double escala;
    public final double velocidad;
    public final int mecha;
    public final float radio;
    public final boolean rompeBloques;
    public final int peso;
    public final int grupoMin;
    public final int grupoMax;

    Variante(
            double vida,
            double escala,
            double velocidad,
            int mecha,
            float radio,
            boolean rompeBloques,
            int peso,
            int grupoMin,
            int grupoMax) {
        this.vida = vida;
        this.escala = escala;
        this.velocidad = velocidad;
        this.mecha = mecha;
        this.radio = radio;
        this.rompeBloques = rompeBloques;
        this.peso = peso;
        this.grupoMin = grupoMin;
        this.grupoMax = grupoMax;
    }

    /** Id en el juego: dolfincreepers:fuego_creeper, etc. (y nombre del archivo de textura). */
    public String id() {
        return name().toLowerCase() + "_creeper";
    }
}
