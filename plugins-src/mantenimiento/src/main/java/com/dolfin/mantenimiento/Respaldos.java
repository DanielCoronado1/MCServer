package com.dolfin.mantenimiento;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * Copia de seguridad del mundo en un .zip:
 *  1. guarda los mundos y desactiva el autoguardado (para que no cambien mientras se copian)
 *  2. comprime las carpetas en otro hilo (el juego sigue funcionando sin lag)
 *  3. vuelve a activar el autoguardado y borra las copias mas viejas
 */
final class Respaldos {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final MantenimientoPlugin plugin;
    private boolean enCurso = false;

    Respaldos(MantenimientoPlugin plugin) {
        this.plugin = plugin;
    }

    Path carpeta() {
        return plugin.getServer().getWorldContainer().toPath()
                .resolve(plugin.getConfig().getString("copias.carpeta", "../backups"))
                .normalize().toAbsolutePath();
    }

    /** Hace una copia. 'quien' recibe el resultado (null = solo en la consola). */
    void hacer(CommandSender quien) {
        if (enCurso) {
            avisar(quien, "Ya hay una copia en curso.", NamedTextColor.YELLOW);
            return;
        }
        enCurso = true;

        List<Path> carpetasMundos = new ArrayList<>();
        for (World mundo : plugin.getServer().getWorlds()) {
            mundo.save();
            mundo.setAutoSave(false);
            carpetasMundos.add(mundo.getWorldFolder().toPath().toAbsolutePath());
        }
        Path raiz = plugin.getServer().getWorldContainer().toPath().toAbsolutePath();
        Path destino = carpeta().resolve("mundo_" + LocalDateTime.now().format(FORMATO) + ".zip");
        avisar(quien, "Haciendo copia de seguridad...", NamedTextColor.GRAY);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String resultado;
            NamedTextColor color = NamedTextColor.GREEN;
            try {
                Files.createDirectories(destino.getParent());
                comprimir(raiz, carpetasMundos, destino);
                limpiarViejas();
                resultado = "Copia guardada: " + destino.getFileName() + " (" + Files.size(destino) / (1024 * 1024) + " MB)";
            } catch (IOException e) {
                resultado = "La copia de seguridad fallo: " + e.getMessage();
                color = NamedTextColor.RED;
                plugin.getLogger().severe(resultado);
            }
            String mensaje = resultado;
            NamedTextColor colorFinal = color;
            // De vuelta en el hilo principal: reactivar el autoguardado y avisar.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().getWorlds().forEach(mundo -> mundo.setAutoSave(true));
                enCurso = false;
                avisar(quien, mensaje, colorFinal);
            });
        });
    }

    private void comprimir(Path raiz, List<Path> carpetas, Path destino) throws IOException {
        try (OutputStream salida = Files.newOutputStream(destino); ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Path carpeta : carpetas) {
                try (Stream<Path> archivos = Files.walk(carpeta)) {
                    for (Path archivo : (Iterable<Path>) archivos.filter(Files::isRegularFile)::iterator) {
                        if (archivo.getFileName().toString().equals("session.lock")) {
                            continue; // lo tiene bloqueado el servidor y no hace falta
                        }
                        zip.putNextEntry(new ZipEntry(raiz.relativize(archivo).toString().replace('\\', '/')));
                        Files.copy(archivo, zip);
                        zip.closeEntry();
                    }
                }
            }
        }
    }

    /** Borra las copias mas antiguas dejando solo las ultimas N. */
    private void limpiarViejas() throws IOException {
        int conservar = plugin.getConfig().getInt("copias.conservar", 24);
        try (Stream<Path> copias = Files.list(carpeta())) {
            List<Path> ordenadas = copias
                    .filter(p -> p.getFileName().toString().startsWith("mundo_") && p.toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            for (Path vieja : ordenadas.stream().skip(conservar).toList()) {
                Files.deleteIfExists(vieja);
            }
        }
    }

    private void avisar(CommandSender quien, String texto, NamedTextColor color) {
        plugin.getLogger().info(texto);
        if (quien != null && quien != plugin.getServer().getConsoleSender()) {
            quien.sendMessage(Component.text(texto, color));
        }
    }
}
