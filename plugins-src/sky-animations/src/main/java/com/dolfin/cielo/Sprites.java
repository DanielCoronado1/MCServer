package com.dolfin.cielo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * "Sprites": imagenes planas del resource pack colocadas en el mundo.
 *
 * Hay dos tipos: letras de una fuente propia (TextDisplay, ver letra()) y
 * objetos con modelo propio. Estos ultimos son ItemDisplay que muestran un objeto con item_model
 * "dolfin:<nombre>". En el pack, ese modelo es un cuadrado de 1x1 bloque
 * con la textura (resourcepack/assets/dolfin/...). Escalandolo conseguimos
 * imagenes del tamano que queramos.
 */
final class Sprites {

    private Sprites() {
    }

    static ItemDisplay crear(Location lugar, String modelo, Display.Billboard billboard, Transformation inicial) {
        ItemStack objeto = new ItemStack(Material.PAPER);
        objeto.editMeta(datos -> datos.setItemModel(new NamespacedKey("dolfin", modelo)));

        Location sinGiro = lugar.clone();
        sinGiro.setYaw(0);
        sinGiro.setPitch(0);
        return lugar.getWorld().spawn(sinGiro, ItemDisplay.class, sprite -> {
            sprite.setItemStack(objeto);
            sprite.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            sprite.setBillboard(billboard);
            sprite.setBrightness(new Display.Brightness(15, 15)); // brilla aunque sea de noche
            sprite.setViewRange(10f);                            // visible desde muy lejos
            sprite.setPersistent(false);                         // no se guarda en el mundo
            sprite.setTransformation(inicial);
        });
    }

    /** 1 pixel de texto = 0.025 bloques. Cada imagen de la fuente mide 128 px de alto. */
    static final float BLOQUES_POR_LETRA = 128 * 0.025f;

    /**
     * Sprite hecho con una "letra" de la fuente dolfin:cielo (ver resourcepack/assets/dolfin/font).
     * El texto no recibe sombreado, asi que se ve igual de brillante desde cualquier angulo.
     */
    static TextDisplay letra(Location lugar, char caracter, Transformation inicial) {
        Location sinGiro = lugar.clone();
        sinGiro.setYaw(0);
        sinGiro.setPitch(0);
        return lugar.getWorld().spawn(sinGiro, TextDisplay.class, sprite -> {
            sprite.text(Component.text(String.valueOf(caracter)).font(Key.key("dolfin", "cielo")));
            sprite.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            sprite.setShadowed(false);
            sprite.setLineWidth(10000);
            sprite.setBillboard(Display.Billboard.FIXED);
            sprite.setBrightness(new Display.Brightness(15, 15));
            sprite.setViewRange(10f);
            sprite.setPersistent(false);
            sprite.setTransformation(inicial);
        });
    }

    /** Posicion, giro y tamano (ancho, alto, fondo) de un sprite. */
    static Transformation forma(Vector3f posicion, Quaternionf giro, Vector3f tamano) {
        return new Transformation(posicion, giro, tamano, new Quaternionf());
    }

    /** Sprite plano, de frente, de 'ancho' x 'alto' bloques con centro en (x, y, z). */
    static Transformation plano(float x, float y, float z, float ancho, float alto) {
        return forma(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(ancho, alto, 1));
    }

    /** Cambia la forma de un display de manera fluida durante 'ticks'. */
    static void animar(Display display, Transformation destino, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(destino);
    }
}
