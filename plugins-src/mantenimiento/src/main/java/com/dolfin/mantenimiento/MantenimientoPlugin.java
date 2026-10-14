package com.dolfin.mantenimiento;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mantenimiento del servidor:
 *  - copia de seguridad del mundo cada X minutos (ver Respaldos)
 *  - reinicio diario a una hora fija, avisando antes a los jugadores
 */
public final class MantenimientoPlugin extends JavaPlugin {

    private Respaldos respaldos;
    private BukkitTask reinicioPendiente;
    private LocalDateTime horaReinicio;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        respaldos = new Respaldos(this);

        int cadaMinutos = getConfig().getInt("copias.cada-minutos", 60);
        if (cadaMinutos > 0) {
            long ticks = cadaMinutos * 60L * 20L;
            getServer().getScheduler().runTaskTimer(this, () -> respaldos.hacer(null), ticks, ticks);
            getLogger().info("Copias de seguridad cada " + cadaMinutos + " minutos en " + respaldos.carpeta());
        }

        String hora = getConfig().getString("reinicio.hora", "");
        if (!hora.isBlank()) {
            // La proxima vez que llegue esa hora: hoy, o manana si ya paso.
            LocalDateTime ahora = LocalDateTime.now();
            LocalDateTime proximo = ahora.toLocalDate().atTime(LocalTime.parse(hora));
            if (proximo.isBefore(ahora.plusMinutes(15))) {
                proximo = proximo.plusDays(1);
            }
            programarReinicio(Duration.between(ahora, proximo));
        }
    }

    // =====================================================================
    //  Reinicio con avisos
    // =====================================================================

    /** Programa un reinicio dentro de 'espera'. Comprueba cada segundo si toca avisar. */
    private void programarReinicio(Duration espera) {
        cancelarReinicio();
        horaReinicio = LocalDateTime.now().plus(espera);
        List<Integer> avisos = getConfig().getIntegerList("reinicio.avisos");
        getLogger().info("Reinicio programado para " + horaReinicio.toLocalTime().withNano(0));

        reinicioPendiente = getServer().getScheduler().runTaskTimer(this, () -> {
            long segundos = Duration.between(LocalDateTime.now(), horaReinicio).toSeconds();
            if (segundos <= 0) {
                anunciar("El servidor se reinicia ahora. Vuelve a entrar en un minuto.");
                getServer().savePlayers();
                getServer().getWorlds().forEach(mundo -> mundo.save());
                getServer().shutdown(); // start.bat lo vuelve a arrancar
                return;
            }
            if (segundos % 60 == 0 && avisos.contains((int) (segundos / 60))) {
                anunciar("El servidor se reinicia en " + segundos / 60 + " minuto(s).");
            } else if (segundos == 10) {
                anunciar("El servidor se reinicia en 10 segundos.");
            }
        }, 20L, 20L);
    }

    private void cancelarReinicio() {
        if (reinicioPendiente != null) {
            reinicioPendiente.cancel();
            reinicioPendiente = null;
        }
    }

    private void anunciar(String texto) {
        getServer().broadcast(Component.text("[Servidor] " + texto, NamedTextColor.GOLD));
    }

    // =====================================================================
    //  Comandos
    // =====================================================================

    @Override
    public boolean onCommand(CommandSender quien, Command comando, String alias, String[] args) {
        if (comando.getName().equals("respaldo")) {
            respaldos.hacer(quien);
            return true;
        }
        // /reinicio <minutos|cancelar>
        if (args.length != 1) {
            return false;
        }
        if (args[0].equalsIgnoreCase("cancelar")) {
            cancelarReinicio();
            anunciar("Reinicio cancelado.");
            return true;
        }
        try {
            int minutos = Integer.parseInt(args[0]);
            programarReinicio(Duration.ofMinutes(minutos));
            anunciar("El servidor se reinicia en " + minutos + " minuto(s).");
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender quien, Command comando, String alias, String[] args) {
        if (comando.getName().equals("reinicio") && args.length == 1) {
            return List.of("1", "5", "10", "cancelar");
        }
        return List.of();
    }
}
