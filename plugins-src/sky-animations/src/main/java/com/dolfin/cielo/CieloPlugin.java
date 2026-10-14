package com.dolfin.cielo;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin DolfinCielo.
 * Guarda el evento que esta en marcha (solo puede haber uno a la vez)
 * y protege el mundo del dragon del evento.
 */
public final class CieloPlugin extends JavaPlugin implements Listener {

    private EventoApocalipsis eventoActual;
    private NamespacedKey marcaDragon;

    @Override
    public void onEnable() {
        // Crea plugins/DolfinCielo/config.yml la primera vez.
        saveDefaultConfig();
        marcaDragon = new NamespacedKey(this, "dragon_evento");
        getCommand("apocalipsis").setExecutor(new ApocalipsisCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DolfinCielo activo: usa /apocalipsis iniciar");
    }

    @Override
    public void onDisable() {
        // Si se apaga el servidor en pleno evento, limpiamos todo.
        detenerEvento();
    }

    public boolean hayEvento() {
        return eventoActual != null;
    }

    /** Arranca el evento en 'centro', con la grieta hacia donde mira 'centro'. */
    public void iniciarEvento(Location centro) {
        eventoActual = new EventoApocalipsis(this, centro);
        eventoActual.runTaskTimer(this, 1L, 1L); // se ejecuta cada tick (20 veces por segundo)
    }

    public void detenerEvento() {
        if (eventoActual != null) {
            eventoActual.detener();
        }
    }

    /** Lo llama el propio evento cuando termina. */
    void eventoTerminado() {
        eventoActual = null;
    }

    NamespacedKey marcaDragon() {
        return marcaDragon;
    }

    // ---- Proteccion: el dragon del evento es solo decorado ----

    private boolean esDragonDelEvento(Entity entidad) {
        if (entidad instanceof ComplexEntityPart parte) {
            entidad = parte.getParent();
        }
        return entidad.getPersistentDataContainer().has(marcaDragon);
    }

    /** Que no rompa bloques si roza una montana. */
    @EventHandler
    public void alRomperBloques(EntityExplodeEvent evento) {
        if (esDragonDelEvento(evento.getEntity())) {
            evento.setCancelled(true);
        }
    }

    /** Que no golpee a nadie. */
    @EventHandler
    public void alGolpear(EntityDamageByEntityEvent evento) {
        if (esDragonDelEvento(evento.getDamager())) {
            evento.setCancelled(true);
        }
    }
}
