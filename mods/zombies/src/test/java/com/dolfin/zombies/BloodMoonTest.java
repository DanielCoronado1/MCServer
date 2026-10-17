package com.dolfin.zombies;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BloodMoonTest {
 @Test void exactSeventhNights(){for(int i=1;i<=70;i++)assertEquals(i%7==0,BloodMoonState.scheduled(i,7,1,.5));}
 @Test void randomBoundaryAndNightWindow(){assertTrue(BloodMoonState.scheduled(1,0,.1,.099));assertFalse(BloodMoonState.scheduled(1,0,.1,.1));assertFalse(BloodMoonState.isNight(12999));assertTrue(BloodMoonState.isNight(13000));assertTrue(BloodMoonState.isNight(22999));assertFalse(BloodMoonState.isNight(23000));assertEquals(7,BloodMoonState.night(6*24000+13000));}
 @Test void fractionalWeightsAndZeroStayZero(){assertEquals(12,SpawnRules.effectiveWeight(8,1.5,true));assertEquals(8,SpawnRules.effectiveWeight(8,1.5,false));assertEquals(0,SpawnRules.effectiveWeight(0,1000,true));assertEquals(100000000,SpawnRules.effectiveWeight(1000000,1000,true));}
}
