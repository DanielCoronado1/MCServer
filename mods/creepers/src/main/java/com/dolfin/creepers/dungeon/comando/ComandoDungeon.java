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

    /** Registra {@code /dungeon}; se llama en cada construcción del despachador de comandos. */
    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> raiz =
                Commands.literal("dungeon")
                        .requires(s -> PermisosDungeon.puedeUsar(s) || PermisosDungeon.esAdmin(s));
        new ComandosJugador(new Confirmaciones()).nodos().forEach(raiz::then);
        dispatcher.register(raiz);
    }
}
