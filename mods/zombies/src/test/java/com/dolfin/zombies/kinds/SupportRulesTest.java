package com.dolfin.zombies.kinds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SupportRulesTest {
 @Test void callUsesSphere(){assertTrue(SupportRules.within(30*30,30));assertFalse(SupportRules.within(35*35,30));assertFalse(SupportRules.within(30*30*2,30));}
 @Test void neverHealsSelfOrHealthy(){assertFalse(SupportRules.healable(true,true,1,20,0,8));assertFalse(SupportRules.healable(false,true,20,20,1,8));assertFalse(SupportRules.healable(false,true,1,20,81,8));assertTrue(SupportRules.healable(false,true,1,20,64,8));}
 @Test void scansAtMostEveryTwentyTicks(){long next=0;int scans=0;for(int tick=0;tick<100;tick++){if(SupportRules.scanDue(tick,next)){scans++;next=tick+20;}}assertEquals(5,scans);}
 @Test void damageLightAndDisableReveal(){assertFalse(SupportRules.revealed(true,false,11,true,100,100));assertTrue(SupportRules.revealed(true,false,12,true,100,100));assertFalse(SupportRules.revealed(true,false,15,false,100,100));assertTrue(SupportRules.revealed(true,false,0,false,159,160));assertFalse(SupportRules.revealed(true,false,0,false,160,160));assertTrue(SupportRules.revealed(false,false,0,false,160,160));assertTrue(SupportRules.revealed(true,true,0,false,160,160));}
}
