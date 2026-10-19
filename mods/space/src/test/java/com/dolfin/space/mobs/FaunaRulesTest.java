package com.dolfin.space.mobs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaunaRulesTest {
    @Test void leapIsStrictlyWithinFourBlocksAndRequiresEligibleUnlatchedTarget() {
        assertTrue(FaunaRules.leap(15.99,true,0,0));assertFalse(FaunaRules.leap(16,true,0,0));
        assertFalse(FaunaRules.leap(4,false,0,0));assertFalse(FaunaRules.leap(4,true,1,0));assertFalse(FaunaRules.leap(4,true,0,10));
    }
    @Test void latchNeedsContactAndNeverAllowsSecondParasite() {
        assertTrue(FaunaRules.latch(1,true,0));assertFalse(FaunaRules.latch(2,true,0));assertFalse(FaunaRules.latch(1,true,1));assertFalse(FaunaRules.latch(1,false,0));
    }
    @Test void creativeSpectatorShipAndDeadPlayersAreExempt() {
        assertTrue(FaunaRules.eligible(false,false,false,true));
        assertFalse(FaunaRules.eligible(true,false,false,true));assertFalse(FaunaRules.eligible(false,true,false,true));
        assertFalse(FaunaRules.eligible(false,false,true,true));assertFalse(FaunaRules.eligible(false,false,false,false));
    }
    @Test void parasiteAttacksEveryTwoSecondsAndLaunchesExactlyOnThird() {
        var cycle=new ParasiteCycle();
        for(int attack=1;attack<=3;attack++) {
            for(int tick=1;tick<40;tick++) assertEquals(ParasiteCycle.Attack.NONE,cycle.tick());
            assertEquals(attack==3?ParasiteCycle.Attack.LAUNCH:ParasiteCycle.Attack.BITE,cycle.tick());
        }
        cycle.reset();for(int tick=0;tick<39;tick++)assertEquals(ParasiteCycle.Attack.NONE,cycle.tick());assertEquals(ParasiteCycle.Attack.BITE,cycle.tick());
    }
    @Test void threeSneakEdgesWithinTwoSecondsDetachButHoldingDoesNot() {
        var counter=new SneakCounter();
        assertFalse(counter.update(true,0));for(int t=1;t<40;t++)assertFalse(counter.update(true,t));
        counter.reset(false);
        assertFalse(counter.update(true,0));assertFalse(counter.update(false,1));assertFalse(counter.update(true,20));assertFalse(counter.update(false,21));assertTrue(counter.update(true,40));
    }
    @Test void staleSneaksExpireAndAlreadyHeldSneakDoesNotCountOnLatch() {
        var counter=new SneakCounter();counter.reset(true);assertFalse(counter.update(true,0));assertFalse(counter.update(false,1));
        assertFalse(counter.update(true,2));assertFalse(counter.update(false,3));assertFalse(counter.update(true,43));assertFalse(counter.update(false,44));assertFalse(counter.update(true,45));
        counter.reset(false);assertFalse(counter.update(true,100));assertFalse(counter.update(false,101));assertFalse(counter.update(true,142));assertFalse(counter.update(false,143));assertFalse(counter.update(true,144));
    }
    @Test void wormsOnlyAcceptUnplacedNaturalKeplerTerrain() {
        for(String id:new String[]{"dolfinspace:regolito","dolfinspace:roca_lunar"}) {
            assertTrue(FaunaRules.natural(id,false));assertFalse(FaunaRules.natural(id,true));
        }
        for(String id:new String[]{"minecraft:stone","minecraft:oak_planks","dolfinspace:mena_titanio","dolfinspace:lodo_acido"}) assertFalse(FaunaRules.natural(id,false));
    }
    @Test void sentinelCooldownAndArrowVulnerability() {
        assertFalse(FaunaRules.projectileReady(59));assertTrue(FaunaRules.projectileReady(60));assertTrue(FaunaRules.projectileReady(61));
        assertEquals(10F,FaunaRules.incomingDamage(5,true));assertEquals(5F,FaunaRules.incomingDamage(5,false));
    }
    @Test void stalkerNightWindowExcludesDayAndWrapsClock() {
        assertFalse(FaunaRules.night(12999));assertTrue(FaunaRules.night(13000));assertFalse(FaunaRules.night(23000));assertTrue(FaunaRules.night(37000));
    }
}
