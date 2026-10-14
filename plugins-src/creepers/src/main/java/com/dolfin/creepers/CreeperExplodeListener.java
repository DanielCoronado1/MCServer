package com.dolfin.creepers;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Escucha eventos del juego. Cada metodo con @EventHandler se ejecuta
 * automaticamente cuando ocurre el evento que recibe como parametro.
 */
public final class CreeperExplodeListener implements Listener {

    /** Cuantos fuegos artificiales salen por cada explosion. */
    private static final int FUEGOS_POR_EXPLOSION = 3;

    /** "Etiqueta" invisible para reconocer nuestros fuegos artificiales. */
    private final NamespacedKey etiquetaFuego;
    private final Variantes variantes;

    public CreeperExplodeListener(CreepersPlugin plugin, Variantes variantes) {
        this.etiquetaFuego = new NamespacedKey(plugin, "fuego_creeper");
        this.variantes = variantes;
    }

    /** Se ejecuta cada vez que algo explota en el mundo. */
    @EventHandler
    public void alExplotar(EntityExplodeEvent evento) {
        // Solo nos interesan las explosiones de creepers normales (las variantes tienen sus propios efectos).
        if (!(evento.getEntity() instanceof Creeper) || variantes.de(evento.getEntity()) != null) {
            return;
        }

        Location lugar = evento.getLocation();
        for (int i = 0; i < FUEGOS_POR_EXPLOSION; i++) {
            lanzarFuego(lugar.clone().add(0, 1 + i, 0));
        }
    }

    /** Crea un fuego artificial de colores al azar y lo hace estallar al instante. */
    private void lanzarFuego(Location lugar) {
        lugar.getWorld().spawn(lugar, Firework.class, fuego -> {
            FireworkMeta datos = fuego.getFireworkMeta();
            datos.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(colorAlAzar(), colorAlAzar())
                    .withFade(colorAlAzar())
                    .flicker(true)
                    .trail(true)
                    .build());
            fuego.setFireworkMeta(datos);
            fuego.getPersistentDataContainer().set(etiquetaFuego, PersistentDataType.BOOLEAN, true);
            fuego.detonate();
        });
    }

    /**
     * Los fuegos artificiales hacen dano al estallar. Aqui lo anulamos
     * para que solo sean decoracion (la explosion del creeper ya hace dano).
     */
    @EventHandler
    public void alDanar(EntityDamageByEntityEvent evento) {
        if (evento.getDamager() instanceof Firework fuego
                && fuego.getPersistentDataContainer().has(etiquetaFuego)) {
            evento.setCancelled(true);
        }
    }

    private static Color colorAlAzar() {
        return Color.fromRGB(ThreadLocalRandom.current().nextInt(0x1000000));
    }
}
