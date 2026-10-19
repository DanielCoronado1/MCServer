package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;

/** Vanilla spider climbing/navigation and melee animations. */
public final class Stalker extends Spider {
    public Stalker(EntityType<? extends Stalker> type, Level level) {
        super(type, level);
        var c = DolfinSpace.config().current().mobs();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(c.acechadorVida());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(c.acechadorDanio());
        setHealth(getMaxHealth());
    }
}
