package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/** Argumentos Brigadier compartidos por los comandos de jugador y de admin. */
final class ArgumentosDungeon {
    static final String DUNGEON = "dungeon";
    static final String DIFICULTAD = "dificultad";

    private static final DynamicCommandExceptionType DUNGEON_DESCONOCIDA =
            new DynamicCommandExceptionType(id -> Component.literal("No existe la dungeon " + id));

    private ArgumentosDungeon() {}

    /** Argumento con el id de una dungeon que sugiere las que cumplan {@code visible}. */
    static RequiredArgumentBuilder<CommandSourceStack, String> argumentoDungeon(
            BiPredicate<CommandSourceStack, DefinicionDungeon> visible) {
        return Commands.argument(DUNGEON, StringArgumentType.word())
                .suggests(
                        (c, b) ->
                                SharedSuggestionProvider.suggest(
                                        todas().stream()
                                                .filter(d -> visible.test(c.getSource(), d))
                                                .map(DefinicionDungeon::id),
                                        b));
    }

    /** Argumento con el id de cualquier dungeon definida. */
    static RequiredArgumentBuilder<CommandSourceStack, String> argumentoDungeonCualquiera() {
        return argumentoDungeon((origen, d) -> true);
    }

    /** Argumento opcional de dificultad que sugiere las de la dungeon ya escrita. */
    static RequiredArgumentBuilder<CommandSourceStack, String> argumentoDificultad() {
        return Commands.argument(DIFICULTAD, StringArgumentType.word())
                .suggests(
                        (c, b) ->
                                SharedSuggestionProvider.suggest(
                                        dificultadesSugeridas(c).stream().map(Dificultad::id), b));
    }

    /** La dungeon escrita en el comando; falla con un mensaje si no existe. */
    static DefinicionDungeon dungeon(CommandContext<CommandSourceStack> c)
            throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, DUNGEON);
        return Dungeons.registro()
                .config()
                .dungeon(id)
                .orElseThrow(() -> DUNGEON_DESCONOCIDA.create(id));
    }

    static List<DefinicionDungeon> todas() {
        return Dungeons.registro().config().dungeons();
    }

    private static List<Dificultad> dificultadesSugeridas(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, DUNGEON);
        return Dungeons.registro()
                .config()
                .dungeon(id)
                .map(
                        d ->
                                Arrays.stream(Dificultad.values())
                                        .filter(d.dificultades()::contains)
                                        .toList())
                .orElse(List.of());
    }
}
