package com.dolfin.zombies;

import com.dolfin.zombies.SpawnRules.Reason;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpawnRulesTest {
    /** Mutable fake position; biome selectors match when they are in {@code biomes}. */
    static final class Fake implements SpawnRules.Probe {
        boolean world=true,night=true,snowy=false,cap=true;int y=64,block=0,sky=4,water=0;Set<String> biomes=new HashSet<>(Set.of("#minecraft:is_overworld","minecraft:plains"));
        public boolean worldAllowed(){return world;} public boolean night(){return night;} public int y(){return y;}
        public int blockLight(){return block;} public int skyLight(){return sky;}
        public boolean biome(List<String> s){for(var id:s)if(biomes.contains(id))return true;return false;}
        public boolean snowy(){return snowy;} public int waterDepth(int needed){return Math.min(water,needed);} public boolean capAllows(){return cap;}
    }
    static SpawnRules.Rule rule(ZombieKind k){return SpawnRules.Rule.from(k,k.defaults(),true);}
    static Reason eval(ZombieKind k,Fake f){return SpawnRules.evaluate(k,rule(k),f,true);}

    @Test void eligibleAndGenericRejectionsInOrder(){
        var f=new Fake();assertNull(eval(ZombieKind.MINERO,f));
        assertEquals(Reason.DISABLED,SpawnRules.evaluate(ZombieKind.MINERO,SpawnRules.Rule.from(ZombieKind.MINERO,ZombieKind.MINERO.defaults(),false),f,true));
        f.world=false;assertEquals(Reason.WORLD,eval(ZombieKind.MINERO,f));f.world=true;
        f.night=false;assertEquals(Reason.DAY,eval(ZombieKind.MINERO,f));f.night=true;
        f.y=321;assertEquals(Reason.HEIGHT,eval(ZombieKind.MINERO,f));f.y=64;
        f.block=1;assertEquals(Reason.LIGHT,eval(ZombieKind.MINERO,f));f.block=0;
        f.biomes.remove("#minecraft:is_overworld");assertEquals(Reason.BIOME,eval(ZombieKind.MINERO,f));
        f.biomes.add("#minecraft:is_overworld");f.biomes.add("minecraft:mushroom_fields");assertEquals(Reason.BIOME,eval(ZombieKind.MINERO,f),"excluded biome wins");
        f.biomes.remove("minecraft:mushroom_fields");f.cap=false;assertEquals(Reason.PLAYER_CAP,eval(ZombieKind.MINERO,f));
        assertNull(SpawnRules.evaluate(ZombieKind.MINERO,rule(ZombieKind.MINERO),f,false),"pool stage ignores the cap");
    }

    @Test void mimoIsNeverNatural(){assertEquals(Reason.NEVER_NATURAL,eval(ZombieKind.MIMO,new Fake()));}

    @Test void enanoOnlyBelowFortyInTotalDarknessAnyTime(){
        var f=new Fake();f.y=39;f.sky=0;f.night=false;assertNull(eval(ZombieKind.ENANO,f),"underground darkness ignores the clock");
        f.y=40;assertEquals(Reason.HEIGHT,eval(ZombieKind.ENANO,f),"default alturaY is [-64,39]");
        // Even when an admin widens alturaY, the hard Y<40 rule holds.
        var wide=ZombieKind.ENANO.defaults();wide.add("alturaY",JsonParser.parseString("[-64,320]"));
        assertEquals(Reason.DWARF,SpawnRules.evaluate(ZombieKind.ENANO,SpawnRules.Rule.from(ZombieKind.ENANO,wide,true),f,true));
        f.y=10;f.sky=1;assertEquals(Reason.DWARF,eval(ZombieKind.ENANO,f));
        var bright=ZombieKind.ENANO.defaults();bright.addProperty("luzMax",7);f.sky=0;f.block=3;
        assertEquals(Reason.DWARF,SpawnRules.evaluate(ZombieKind.ENANO,SpawnRules.Rule.from(ZombieKind.ENANO,bright,true),f,true));
    }

    @Test void congeladoOnlyWhereItSnows(){
        var f=new Fake();f.biomes.add("minecraft:snowy_plains");assertEquals(Reason.SNOW,eval(ZombieKind.CONGELADO,f));
        f.snowy=true;assertNull(eval(ZombieKind.CONGELADO,f));
        f.biomes.remove("minecraft:snowy_plains");assertEquals(Reason.BIOME,eval(ZombieKind.CONGELADO,f));
        // Admin-added warm biome still needs snow.
        var custom=ZombieKind.CONGELADO.defaults();custom.add("biomas",JsonParser.parseString("[\"minecraft:plains\"]"));f.snowy=false;
        assertEquals(Reason.SNOW,SpawnRules.evaluate(ZombieKind.CONGELADO,SpawnRules.Rule.from(ZombieKind.CONGELADO,custom,true),f,true));
    }

    @Test void abismoNeedsFiveBlocksOfWater(){
        var f=new Fake();f.biomes.add("#minecraft:is_ocean");f.water=4;assertEquals(Reason.WATER_DEPTH,eval(ZombieKind.ABISMO,f));
        f.water=5;assertNull(eval(ZombieKind.ABISMO,f));
        var deeper=ZombieKind.ABISMO.defaults();deeper.getAsJsonObject("habilidades").getAsJsonObject("agarre").addProperty("profundidadMinima",8);
        assertEquals(8,SpawnRules.Rule.from(ZombieKind.ABISMO,deeper,true).waterDepth());
        deeper.getAsJsonObject("habilidades").getAsJsonObject("agarre").addProperty("profundidadMinima",2);
        assertEquals(5,SpawnRules.Rule.from(ZombieKind.ABISMO,deeper,true).waterDepth(),"never shallower than 5");
    }

    @Test void waterColumnCountsUpThenDownAndStopsEarly(){
        assertEquals(0,SpawnRules.columnDepth(d->d!=0,5));
        assertEquals(5,SpawnRules.columnDepth(d->d>=-2&&d<=2,5));
        assertEquals(3,SpawnRules.columnDepth(d->d>=0&&d<=2,5));
        assertEquals(5,SpawnRules.columnDepth(d->d<=0,5),"surface position, deep water below");
        int[] probes={0};SpawnRules.columnDepth(d->{probes[0]++;return true;},5);assertEquals(5,probes[0]);
    }

    @Test void perPlayerCap(){
        assertTrue(SpawnRules.capAllows(12,new int[]{}));assertTrue(SpawnRules.capAllows(12,new int[]{0,11}));
        assertFalse(SpawnRules.capAllows(12,new int[]{3,12}),"any nearby player at the cap blocks the spawn");
        assertFalse(SpawnRules.capAllows(0,new int[]{}),"cap 0 disables special natural spawns");
    }

    @Test void weightsAndSubstitution(){
        assertEquals(12,SpawnRules.effectiveWeight(8,1.5,true));assertEquals(8,SpawnRules.effectiveWeight(8,1.5,false));
        assertEquals(0,SpawnRules.effectiveWeight(0,1000,true));assertEquals(100000000,SpawnRules.effectiveWeight(1000000,1000,true));
        assertArrayEquals(new int[]{8,4},SpawnRules.substitute(0,new int[]{8,4}));
        assertArrayEquals(new int[]{8+64,4+32},SpawnRules.substitute(96,new int[]{8,4}));
        assertArrayEquals(new int[]{},SpawnRules.substitute(95,new int[]{}),"no eligible special: vanilla zombies are still removed");
    }

    @Test void ruleParsesDefaults(){
        var r=rule(ZombieKind.HAMBRIENTO);assertEquals(8,r.weight());assertEquals(1,r.min());assertEquals(3,r.max());
        assertEquals(3,r.bloodWeight());assertEquals(-64,r.low());assertEquals(320,r.high());assertEquals(0,r.light());
        assertEquals(List.of("minecraft:mushroom_fields"),r.exclude());
    }
}
