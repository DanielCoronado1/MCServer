package com.dolfin.zombies;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ZombieTickBudgetTest {
 @Test void allSixteenMapDefaultsAndCustomStatsThroughProductionMapper(){
  double[] health={24,16,22,26,28,16,18,22,20,24,20,50,8,20,30,18};
  double[] damage={3,1,4,4,4,2,2,2,3,3,3,5,2,3,4,4};
  assertEquals(16,ZombieKind.values().length);
  for(var kind:ZombieKind.values()){
   var config=kind.defaults();var stats=ZombieStats.from(config);
   assertEquals(health[kind.ordinal()],stats.health(),kind.name());assertEquals(damage[kind.ordinal()],stats.damage(),kind.name());
   assertEquals(.23*config.get("velocidad").getAsDouble(),stats.movementSpeed(),1e-12);assertEquals(2,stats.armor());assertEquals(kind==ZombieKind.ENANO?.5:1,stats.scale());
   config.addProperty("vida",91);config.addProperty("danio",17);config.addProperty("velocidad",1.7);config.addProperty("armadura",11);config.addProperty("escala",.8);
   assertEquals(new ZombieStats(91,17,.23*1.7,11,.8),ZombieStats.from(config));
  }
 }
 @Test void periodicScansAreBoundedAndStaggeredForTwentyFiveOfEveryKind(){
  for(var kind:ZombieKind.values()){
   int period=ZombieTickPolicy.scanPeriod(kind);int total=0,peak=0;
   for(int tick=0;tick<200;tick++){int scans=0;for(int entity=0;entity<25;entity++)if(ZombieTickPolicy.scanDue(kind,tick,entity))scans++;total+=scans;peak=Math.max(peak,scans);}
   assertEquals(period==0?0:25*200/period,total,kind.name());assertTrue(peak<=(period==0?0:(25+period-1)/period),kind.name());
  }
 }
 @Test void twoMillisecondBoundaryRequiresFullWindowAndTwentyFiveNearbyEntities(){
  var boundary=new TickBudgetWindow(100);for(int i=0;i<99;i++)boundary.add(2_000_000,5_000_000,25,25);assertFalse(boundary.withinBudget());boundary.add(2_000_000,5_000_000,25,25);assertTrue(boundary.withinBudget());assertEquals(2,boundary.meanMs());assertEquals(2,boundary.percentileMs(.95));
  var over=new TickBudgetWindow(1);over.add(2_000_001,3_000_000,25,25);assertFalse(over.withinBudget());
  var noPlayer=new TickBudgetWindow(1);noPlayer.add(1,1,25,0);assertFalse(noPlayer.withinBudget());
  var missing=new TickBudgetWindow(1);missing.add(1,1,24,24);assertFalse(missing.withinBudget());
 }
 @Test void budgetKeepsMeanAndTailSeparate(){var w=new TickBudgetWindow(100);for(int i=0;i<99;i++)w.add(1_000_000,3_000_000,25,25);w.add(50_000_000,60_000_000,25,25);assertTrue(w.withinBudget());assertEquals(1.49,w.meanMs());assertEquals(50,w.percentileMs(1));assertEquals(1,w.percentileMs(.95));assertThrows(IllegalStateException.class,()->w.add(1,1,25,25));}
}
