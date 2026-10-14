package com.dolfin.creepers;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin (la que indica "main" en plugin.yml).
 * El servidor llama a onEnable() al arrancar y a onDisable() al apagarse.
 */
public final class CreepersPlugin extends JavaPlugin {

    private Variantes variantes;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // crea plugins/DolfinCreepers/config.yml la primera vez
        variantes = new Variantes(this);

        // Registramos los "oyentes": las clases que reaccionan a lo que pasa en el juego.
        getServer().getPluginManager().registerEvents(new CreeperExplodeListener(this, variantes), this);
        VariantesListener listenerVariantes = new VariantesListener(this, variantes);
        getServer().getPluginManager().registerEvents(listenerVariantes, this);

        // Auras de particulas y saltos: cada 10 ticks (medio segundo).
        getServer().getScheduler().runTaskTimer(this, listenerVariantes::tick, 20L, 10L);

        getCommand("creeper").setExecutor(new CreeperCommand(variantes));
        getLogger().info("DolfinCreepers activo: fuegos artificiales y variantes de creeper");
    }

    @Override
    public void onDisable() {
        getLogger().info("DolfinCreepers desactivado");
    }
}
