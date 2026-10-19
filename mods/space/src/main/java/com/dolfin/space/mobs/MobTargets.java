package com.dolfin.space.mobs;

import com.dolfin.space.*;
import com.dolfin.space.oxygen.OxygenModule;
import java.util.Comparator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class MobTargets {
    private MobTargets() {}
    static boolean eligible(Player p) {
        boolean eligible=DolfinSpace.config().current().general().activo() && KeplerDimension.isKepler(p.level().dimension()) && FaunaRules.eligible(p.isCreative(),p.isSpectator(),OxygenModule.inShip(p),p.isAlive() && !p.isRemoved());
        if (!eligible) DolfinSpace.LOGGER.debug("event=fauna_target_rejected player={} active={} kepler={} creative={} spectator={} ship={} alive={}",p.getUUID(),DolfinSpace.config().current().general().activo(),KeplerDimension.isKepler(p.level().dimension()),p.isCreative(),p.isSpectator(),OxygenModule.inShip(p),p.isAlive() && !p.isRemoved());
        return eligible;
    }
    static Player nearest(Entity entity, double range) {
        return entity.level().players().stream().filter(MobTargets::eligible).filter(p -> entity.distanceToSqr(p)<range*range).min(Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
    }
}
