package com.dolfin.cielo;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** El comando /apocalipsis <iniciar|detener>. */
public final class ApocalipsisCommand implements TabExecutor {

    private final CieloPlugin plugin;

    public ApocalipsisCommand(CieloPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender quien, Command comando, String alias, String[] args) {
        String accion = args.length > 0 ? args[0].toLowerCase() : "iniciar";

        switch (accion) {
            case "iniciar" -> {
                if (plugin.hayEvento()) {
                    quien.sendMessage(Component.text("Ya hay un apocalipsis en marcha.", NamedTextColor.RED));
                    return true;
                }
                // /apocalipsis iniciar <jugador>: sale donde esta ese jugador y hacia donde mira.
                // Sin nombre: donde esta quien lo lanza, o en el spawn (mirando al norte) si es la consola.
                Player objetivo = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : quien instanceof Player jugador ? jugador : null;
                if (args.length > 1 && objetivo == null) {
                    quien.sendMessage(Component.text("No hay ningun jugador conectado llamado " + args[1], NamedTextColor.RED));
                    return true;
                }
                Location centro = objetivo != null
                        ? objetivo.getLocation()
                        : Bukkit.getWorlds().getFirst().getSpawnLocation();
                plugin.iniciarEvento(centro);
                quien.sendMessage(Component.text("El cielo empieza a oscurecerse...", NamedTextColor.DARK_RED));
            }
            case "detener" -> {
                if (!plugin.hayEvento()) {
                    quien.sendMessage(Component.text("No hay ningun evento en marcha.", NamedTextColor.GRAY));
                    return true;
                }
                plugin.detenerEvento();
                quien.sendMessage(Component.text("Evento detenido.", NamedTextColor.GRAY));
            }
            default -> {
                return false; // muestra el "usage" de plugin.yml
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender quien, Command comando, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("iniciar", "detener");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("iniciar")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
