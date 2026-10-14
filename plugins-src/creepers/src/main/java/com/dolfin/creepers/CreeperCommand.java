package com.dolfin.creepers;

import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /creeper invocar <variante> [cantidad] [jugador]: para probar las variantes. */
public final class CreeperCommand implements TabExecutor {

    private final Variantes variantes;

    public CreeperCommand(Variantes variantes) {
        this.variantes = variantes;
    }

    @Override
    public boolean onCommand(CommandSender quien, Command comando, String alias, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("invocar")) {
            return false;
        }
        // Delante de quien lo escribe, o del jugador indicado (util desde la consola).
        Player jugador = args.length > 3 ? Bukkit.getPlayerExact(args[3])
                : quien instanceof Player yo ? yo : null;
        if (jugador == null) {
            quien.sendMessage(Component.text("Indica un jugador conectado: /creeper invocar <variante> <cantidad> <jugador>",
                    NamedTextColor.RED));
            return true;
        }
        Variante variante = Variante.desde(args[1]);
        if (variante == null) {
            quien.sendMessage(Component.text("Variante desconocida: " + args[1], NamedTextColor.RED));
            return true;
        }
        int cantidad = 1;
        if (args.length > 2) {
            try {
                cantidad = Math.clamp(Integer.parseInt(args[2]), 1, 10);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        // Aparecen 4 bloques delante del jugador.
        var lugar = jugador.getLocation().add(jugador.getLocation().getDirection().setY(0).normalize().multiply(4));
        for (int i = 0; i < cantidad; i++) {
            variantes.invocar(lugar, variante);
        }
        quien.sendMessage(Component.text("Invocado: " + cantidad + " x " + variante.nombre(), variante.color()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender quien, Command comando, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("invocar");
        }
        if (args.length == 2) {
            return Arrays.stream(Variante.values()).map(Variante::clave)
                    .filter(clave -> clave.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 4) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
