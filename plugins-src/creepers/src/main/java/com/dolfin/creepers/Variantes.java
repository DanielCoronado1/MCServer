package com.dolfin.creepers;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Convierte un creeper normal en una variante y permite saber de que variante es.
 * La variante se guarda en el propio creeper (PersistentDataContainer), asi que
 * se conserva aunque el servidor se reinicie.
 */
public final class Variantes {

    private final NamespacedKey clave;

    public Variantes(CreepersPlugin plugin) {
        this.clave = new NamespacedKey(plugin, "variante");
    }

    /** La variante de un creeper, o null si es uno normal. */
    public Variante de(Entity entidad) {
        if (!(entidad instanceof Creeper)) {
            return null;
        }
        String texto = entidad.getPersistentDataContainer().get(clave, PersistentDataType.STRING);
        return texto == null ? null : Variante.desde(texto);
    }

    public Creeper invocar(Location lugar, Variante variante) {
        return lugar.getWorld().spawn(lugar, Creeper.class, creeper -> aplicar(creeper, variante));
    }

    /** Ajusta tamano, vida, velocidad, mecha y explosion segun la variante. */
    public void aplicar(Creeper creeper, Variante variante) {
        creeper.getPersistentDataContainer().set(clave, PersistentDataType.STRING, variante.clave());
        creeper.customName(Component.text(variante.nombre(), variante.color()));
        creeper.setCustomNameVisible(false); // el nombre se ve solo al mirarlo de cerca

        switch (variante) {
            case HIELO -> creeper.setExplosionRadius(3);
            case ELECTRICO -> {
                creeper.setPowered(true);        // el aura azul del creeper cargado
                creeper.setExplosionRadius(2);   // cargado se duplica: queda en 4 (normal es 3)
            }
            case SALTARIN -> {
                atributo(creeper, Attribute.SCALE, 0.7);
                multiplicar(creeper, Attribute.MOVEMENT_SPEED, 1.35);
                creeper.setMaxFuseTicks(20);     // mecha de 1 segundo (normal: 1,5 s)
                creeper.setExplosionRadius(2);
            }
            case MULTIPLICADOR -> creeper.setExplosionRadius(2);
            case GIGANTE -> {
                atributo(creeper, Attribute.SCALE, 2.0);
                atributo(creeper, Attribute.MAX_HEALTH, 60);
                creeper.setHealth(60);
                multiplicar(creeper, Attribute.MOVEMENT_SPEED, 0.8);
                creeper.setMaxFuseTicks(50);
                creeper.setExplosionRadius(5);
            }
            case MINI -> {
                atributo(creeper, Attribute.SCALE, 0.45);
                atributo(creeper, Attribute.MAX_HEALTH, 6);
                creeper.setHealth(6);
                multiplicar(creeper, Attribute.MOVEMENT_SPEED, 1.25);
                creeper.setMaxFuseTicks(20);
                creeper.setExplosionRadius(1);
            }
        }
    }

    private static void atributo(Creeper creeper, Attribute tipo, double valor) {
        AttributeInstance atributo = creeper.getAttribute(tipo);
        if (atributo != null) {
            atributo.setBaseValue(valor);
        }
    }

    private static void multiplicar(Creeper creeper, Attribute tipo, double factor) {
        AttributeInstance atributo = creeper.getAttribute(tipo);
        if (atributo != null) {
            atributo.setBaseValue(atributo.getBaseValue() * factor);
        }
    }
}
