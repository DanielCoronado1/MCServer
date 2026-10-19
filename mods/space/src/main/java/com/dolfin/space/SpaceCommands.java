package com.dolfin.space;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the {@code /dolfinspace}, {@code /space}, and {@code /espacio} administration
 * and diagnostics commands.
 */
public final class SpaceCommands {
    private SpaceCommands() {}

    /** Registers {@code /dolfinspace}, {@code /space}, and {@code /espacio} command trees. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot("dolfinspace"));
        dispatcher.register(buildRoot("space"));
        dispatcher.register(buildRoot("espacio"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String literal) {
        return Commands.literal(literal)
                .then(Commands.literal("reload")
                        .requires(SpacePermissions::isAdmin)
                        .executes(ctx -> executeReload(ctx.getSource())))
                .then(Commands.literal("recargar")
                        .requires(SpacePermissions::isAdmin)
                        .executes(ctx -> executeReload(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> executeInfo(ctx.getSource())))
                .then(Commands.literal("estado")
                        .executes(ctx -> executeInfo(ctx.getSource())));
    }

    private static int executeReload(CommandSourceStack source) {
        SpaceConfig.Report report = DolfinSpace.config().reload();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            KeplerDimension.syncGravity(player, player.level(), "config_reload");
        }
        DolfinSpace.LOGGER.info(
                "event=config_reload actor={} {}",
                source.getTextName(),
                report.summary()
        );
        source.sendSuccess(
                () -> Component.translatable(
                        "dolfinspace.command.reloaded",
                        report.generation(),
                        report.problems().size()
                ),
                true
        );
        return 1;
    }

    private static int executeInfo(CommandSourceStack source) {
        SpaceConfig.Snapshot snapshot = DolfinSpace.config().current();
        source.sendSuccess(
                () -> Component.translatable(
                        "dolfinspace.command.info",
                        snapshot.dimension().idDimension(),
                        (int) Math.round(snapshot.dimension().multiplicadorGravedad() * 100.0),
                        snapshot.generation()
                ),
                false
        );
        return 1;
    }
}
