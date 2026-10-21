package com.dolfin.creepers.dungeon.reglas;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Reglas de la dimensión de dungeons (#15): aventura ({@link ReglasModo}), sin PvP ({@link
 * ReglasDanio}), objetos y vuelo bloqueados ({@link ReglasObjetos}). Las decisiones puras están en
 * {@link DecisionesReglas}; los mixins de {@code dungeon.mixin} consultan {@link #permiteEfecto} y
 * {@link #permitePlaneo}.
 */
public final class ReglasDungeon {
    private ReglasDungeon() {}

    /** Engancha los eventos de Fabric de las reglas; se llama una vez al arrancar el mod. */
    public static void registrar() {
        ReglasDanio.registrar();
        ReglasObjetos.registrar();
    }

    /**
     * Una vez por segundo: devuelve a aventura a quien esté en supervivencia dentro de una partida.
     */
    public static void cadaSegundo(MinecraftServer server) {
        ReglasModo.forzarAventura(server);
    }

    /** Falso si el efecto dañino lo causa otro jugador dentro de la dimensión. */
    public static boolean permiteEfecto(
            LivingEntity victima, MobEffectInstance efecto, Entity origen) {
        return ReglasDanio.permiteEfecto(victima, efecto, origen);
    }

    /** Falso si el jugador no puede despegar con élitros (dentro de la dimensión). */
    public static boolean permitePlaneo(Player jugador) {
        return ReglasObjetos.permitePlaneo(jugador);
    }
}
