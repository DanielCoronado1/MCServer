package com.dolfin.zombies;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Entity constructor per kind. Groups follow the issue that owns each kind's mechanics so
 * parallel work only touches its own section; replace {@code ConfiguredZombie} with a subclass.
 */
final class ZombieFactories {
    private ZombieFactories(){}
    static ConfiguredZombie create(EntityType<ConfiguredZombie> type,Level level,ZombieKind kind){
        return switch(kind){

            // #4 block-modifying zombies
            case MINERO, BOMBERO, GRANJERO -> new ConfiguredZombie(type,level,kind);

            // #5 combat zombies
            case ESCUDERO, HAMBRIENTO, CONGELADO, BILIOSO, ABISMO, JINETE, ENANO -> new ConfiguredZombie(type,level,kind);

            // #6 support and stealth zombies
            case GRITON -> new com.dolfin.zombies.kinds.GritonZombie(type,level);
            case BRUJA -> new com.dolfin.zombies.kinds.BrujaZombie(type,level);
            case EXPLORADOR -> new com.dolfin.zombies.kinds.ExploradorZombie(type,level);

            // #7 inventory zombies
            case LADRON, CONTAGIADO, MIMO -> new ConfiguredZombie(type,level,kind);

        };
    }
}
