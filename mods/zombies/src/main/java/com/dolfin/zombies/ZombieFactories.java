package com.dolfin.zombies;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Entity constructor per kind. Groups follow the issue that owns each kind's mechanics so parallel
 * work only touches its own section; replace {@code ConfiguredZombie} with a subclass.
 */
final class ZombieFactories {
    private ZombieFactories() {}

    static ConfiguredZombie create(
            EntityType<ConfiguredZombie> type, Level level, ZombieKind kind) {
        return switch (kind) {

            // #4 block-modifying zombies
            case MINERO -> new com.dolfin.zombies.kinds.MineroZombie(type, level);
            case BOMBERO -> new com.dolfin.zombies.kinds.BomberoZombie(type, level);
            case GRANJERO -> new com.dolfin.zombies.kinds.GranjeroZombie(type, level);

            // #5 combat zombies
            case ESCUDERO -> new com.dolfin.zombies.kinds.EscuderoZombie(type, level);
            case HAMBRIENTO -> new com.dolfin.zombies.kinds.HambrientoZombie(type, level);
            case CONGELADO -> new com.dolfin.zombies.kinds.CongeladoZombie(type, level);
            case BILIOSO -> new com.dolfin.zombies.kinds.BiliosoZombie(type, level);
            case ABISMO -> new com.dolfin.zombies.kinds.AbismoZombie(type, level);
            case JINETE -> new com.dolfin.zombies.kinds.JineteZombie(type, level);
            case ENANO -> new com.dolfin.zombies.kinds.EnanoZombie(type, level);

            // #6 support and stealth zombies
            case GRITON -> new com.dolfin.zombies.kinds.GritonZombie(type, level);
            case BRUJA -> new com.dolfin.zombies.kinds.BrujaZombie(type, level);
            case EXPLORADOR -> new com.dolfin.zombies.kinds.ExploradorZombie(type, level);

            // #7 inventory zombies
            case LADRON -> new com.dolfin.zombies.kinds.LadronZombie(type, level);
            case CONTAGIADO -> new com.dolfin.zombies.kinds.ContagiadoZombie(type, level);
            case MIMO -> new com.dolfin.zombies.kinds.MimoZombie(type, level);
        };
    }
}
