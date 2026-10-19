package com.dolfin.space.compat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.*;

import java.util.function.Predicate;

/** The live Brigadier subtree, injectable without bootstrapping Minecraft in unit tests. */
public final class RecoveryCommandTree {
    public static <S, T> LiteralArgumentBuilder<S> build(
            Predicate<S> admin, ArgumentType<T> player, Command<S> execute) {
        return LiteralArgumentBuilder.<S>literal("nave")
                .requires(admin)
                .then(
                        LiteralArgumentBuilder.<S>literal("recuperar")
                                .requires(admin)
                                .then(
                                        RequiredArgumentBuilder.<S, T>argument("jugador", player)
                                                .executes(execute)));
    }

    private RecoveryCommandTree() {}
}
