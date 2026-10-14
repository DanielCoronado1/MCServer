package com.dolfin.creepers;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Los tipos de creeper especiales. MINI solo nace de un MULTIPLICADOR. */
public enum Variante {
    HIELO("Creeper de Hielo", NamedTextColor.AQUA),
    ELECTRICO("Creeper Electrico", NamedTextColor.YELLOW),
    SALTARIN("Creeper Saltarin", NamedTextColor.WHITE),
    MULTIPLICADOR("Creeper Multiplicador", NamedTextColor.GREEN),
    GIGANTE("Creeper Gigante", NamedTextColor.DARK_GREEN),
    MINI("Mini Creeper", NamedTextColor.GREEN);

    private final String nombre;
    private final TextColor color;

    Variante(String nombre, TextColor color) {
        this.nombre = nombre;
        this.color = color;
    }

    public String nombre() {
        return nombre;
    }

    public TextColor color() {
        return color;
    }

    /** Nombre para config.yml y comandos: "hielo", "electrico"... */
    public String clave() {
        return name().toLowerCase();
    }

    public static Variante desde(String texto) {
        for (Variante variante : values()) {
            if (variante.clave().equalsIgnoreCase(texto)) {
                return variante;
            }
        }
        return null;
    }
}
