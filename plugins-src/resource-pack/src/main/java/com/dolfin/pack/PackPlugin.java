package com.dolfin.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sirve plugins/DolfinPack/pack.zip por HTTP (un mini servidor web dentro
 * del plugin) y se lo envia a cada jugador al entrar.
 */
public final class PackPlugin extends JavaPlugin implements Listener {

    /** Identificador fijo: si el pack cambia, el nuevo reemplaza al viejo. */
    private static final UUID ID_PACK = UUID.nameUUIDFromBytes("dolfin-pack".getBytes(StandardCharsets.UTF_8));

    private HttpServer web;
    private volatile byte[] zip;
    private volatile String sha1;
    private String urlBase;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cargarPack();

        int puerto = getConfig().getInt("puerto", 8081);
        try {
            web = HttpServer.create(new InetSocketAddress(puerto), 0);
            web.createContext("/pack.zip", peticion -> {
                byte[] datos = zip;
                if (datos == null) {
                    peticion.sendResponseHeaders(404, -1);
                    peticion.close();
                    return;
                }
                peticion.getResponseHeaders().add("Content-Type", "application/zip");
                peticion.sendResponseHeaders(200, datos.length);
                try (OutputStream salida = peticion.getResponseBody()) {
                    salida.write(datos);
                }
            });
            web.start();
        } catch (IOException e) {
            getLogger().severe("No se pudo abrir el puerto " + puerto + " para el pack: " + e.getMessage());
        }

        urlBase = "http://" + hostPublico() + ":" + puerto + "/pack.zip";
        getLogger().info("Resource pack servido en " + urlBase);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (web != null) {
            web.stop(0);
        }
    }

    /** Lee pack.zip y calcula su huella SHA-1 (el juego la usa para saber si ya lo tiene). */
    private void cargarPack() {
        Path archivo = getDataFolder().toPath().resolve("pack.zip");
        try {
            byte[] datos = Files.readAllBytes(archivo);
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(datos));
            zip = datos;
            getLogger().info("pack.zip cargado (" + datos.length / 1024 + " KB, sha1 " + sha1 + ")");
        } catch (IOException e) {
            zip = null;
            getLogger().warning("No hay pack.zip en " + archivo + " (ejecuta scripts/deploy-plugins.bat)");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "auto" = la IP de este equipo en la red local. */
    private String hostPublico() {
        String host = getConfig().getString("host", "auto");
        if (!"auto".equalsIgnoreCase(host)) {
            return host;
        }
        // Truco: "conectar" un socket UDP no envia nada, pero revela la IP local de salida.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            return "localhost";
        }
    }

    private void enviar(Player jugador) {
        if (zip == null) {
            return;
        }
        // "?v=" cambia con cada version del pack, asi nunca se usa una copia vieja.
        URI uri = URI.create(urlBase + "?v=" + sha1);
        ResourcePackRequest pedido = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(ID_PACK, uri, sha1))
                .required(getConfig().getBoolean("obligatorio", true))
                .prompt(MiniMessage.miniMessage().deserialize(getConfig().getString("mensaje", "")))
                .replace(true)
                .build();
        jugador.sendResourcePacks(pedido);
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent evento) {
        enviar(evento.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender quien, Command comando, String alias, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("recargar")) {
            return false;
        }
        cargarPack();
        getServer().getOnlinePlayers().forEach(this::enviar);
        quien.sendMessage(Component.text("Pack recargado y reenviado a todos.", NamedTextColor.GREEN));
        return true;
    }
}
