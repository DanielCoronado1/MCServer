package com.dolfin.zombies.kinds;

import com.dolfin.zombies.*;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

abstract class SupportZombie extends ConfiguredZombie {
    protected long nextScan;

    SupportZombie(EntityType<? extends Zombie> type, Level level, ZombieKind kind) {
        super(type, level, kind);
    }

    protected void reject(String reason) {
        DolfinZombies.LOGGER.debug(
                "event=support_rejected kind={} uuid={} reason={}", kind(), getUUID(), reason);
    }

    protected void retreat(LivingEntity target, double speed) {
        var pos = DefaultRandomPos.getPosAway(this, 12, 5, target.position());
        if (pos != null) getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
        else reject("no_retreat_path");
    }
}
