package com.dolfin.creepers.dungeon.comando;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Árbol {@code /dungeon}: comandos de jugador ({@link ComandosJugador}) con el permiso {@value
 * PermisosDungeon#USO} y el subárbol {@code admin} ({@code ComandosAdmin}) con {@value
 * PermisosDungeon#ADMIN}.
 */
public final class ComandoDungeon {
    private ComandoDungeon() {}

    /** Engancha los eventos de Fabric de los comandos (la varita de zona); una vez al arrancar. */
    public static void registrarEventos() {
        VaritaZona.registrarEventos();
    }

    /** Registra {@code /dungeon}; se llama en cada construcción del despachador de comandos. */
    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> raiz =
                Commands.literal("dungeon")
                        .requires(s -> PermisosDungeon.puedeUsar(s) || PermisosDungeon.esAdmin(s));
        Confirmaciones confirmaciones = new Confirmaciones();
        new ComandosJugador(confirmaciones, new FlujoUnirse(confirmaciones))
                .nodos()
                .forEach(raiz::then);
        raiz.then(ComandosAdmin.nodo());
        dispatcher.register(raiz);
    }
}
