package com.dolfin.zombies.kinds;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningMathTest {
    static void assertOffsets(List<int[]> actual,int[]... expected){
        assertEquals(expected.length,actual.size());
        for(int i=0;i<expected.length;i++)assertArrayEquals(expected[i],actual.get(i),"candidate "+i);
    }
    @Test void sameLevelDigsFeetThenHeadAlongDominantAxis(){
        assertOffsets(MiningMath.candidates(5,0,2),new int[]{1,0,0},new int[]{1,1,0});
        assertOffsets(MiningMath.candidates(-1,0,-4),new int[]{0,0,-1},new int[]{0,1,-1});
        assertOffsets(MiningMath.candidates(-3,0,3),new int[]{-1,0,0},new int[]{-1,1,0}); // ties go to x
    }
    @Test void targetAboveKeepsStepAndClearsHeadroom(){
        assertOffsets(MiningMath.candidates(4,2,0),new int[]{1,1,0},new int[]{1,2,0},new int[]{0,2,0});
        assertOffsets(MiningMath.candidates(1,3,0),new int[]{0,2,0},new int[]{1,1,0},new int[]{1,2,0});
        assertOffsets(MiningMath.candidates(0,5,0),new int[]{0,2,0});
    }
    @Test void targetBelowOpensFrontThenDigsDown(){
        assertOffsets(MiningMath.candidates(3,-2,0),new int[]{1,0,0},new int[]{1,1,0},new int[]{1,-1,0});
        assertOffsets(MiningMath.candidates(0,-3,0),new int[]{0,-1,0});
    }
    @Test void sameBlockOrJustAboveHeadNeedsNothing(){
        assertTrue(MiningMath.candidates(0,0,0).isEmpty());
        assertTrue(MiningMath.candidates(0,1,0).isEmpty());
    }
    @Test void neverMoreThanThreeCandidatesAndAllAdjacent(){
        for(int dx=-6;dx<=6;dx++)for(int dy=-6;dy<=6;dy++)for(int dz=-6;dz<=6;dz++){
            var c=MiningMath.candidates(dx,dy,dz);assertTrue(c.size()<=3);
            for(int[] o:c){assertTrue(Math.abs(o[0])<=1&&Math.abs(o[2])<=1&&o[1]>=-1&&o[1]<=2);assertTrue(o[0]==0||o[2]==0);}
        }
    }
    @Test void breakTimeScalesWithHardness(){
        assertEquals(20,MiningMath.breakTicks(.5f,40));  // dirt
        assertEquals(60,MiningMath.breakTicks(1.5f,40)); // stone
        assertEquals(120,MiningMath.breakTicks(3f,40));  // ores, the default durezaMax
        assertEquals(20,MiningMath.breakTicks(0f,40));   // instant blocks still take the minimum fraction
        assertEquals(5,MiningMath.breakTicks(.5f,1));
    }
    @Test void crackStagesCoverZeroToNine(){
        assertEquals(0,MiningMath.crackStage(0,40));assertEquals(5,MiningMath.crackStage(20,40));
        assertEquals(9,MiningMath.crackStage(39,40));assertEquals(9,MiningMath.crackStage(80,40));assertEquals(0,MiningMath.crackStage(1,0));
    }
    @Test void checkBlockEligibilityCoversHardnessObsidianAndBlacklist(){
        assertNull(MiningMath.checkBlock(.5f,3f,false,false,false));  // dirt
        assertNull(MiningMath.checkBlock(1.5f,3f,false,false,false)); // stone
        assertNull(MiningMath.checkBlock(3f,3f,false,false,false));   // exact durezaMax
        assertEquals(com.dolfin.zombies.griefing.GuardDecision.Reason.HARDNESS,MiningMath.checkBlock(3.5f,3f,false,false,false));
        assertEquals(com.dolfin.zombies.griefing.GuardDecision.Reason.UNBREAKABLE,MiningMath.checkBlock(-1f,3f,false,false,false)); // bedrock
        assertEquals(com.dolfin.zombies.griefing.GuardDecision.Reason.UNBREAKABLE,MiningMath.checkBlock(50f,100f,true,false,false)); // obsidian even if durezaMax raised
        assertEquals(com.dolfin.zombies.griefing.GuardDecision.Reason.BLACKLIST,MiningMath.checkBlock(2.5f,3f,false,true,false)); // chest/bed
        assertEquals(com.dolfin.zombies.griefing.GuardDecision.Reason.BLACKLIST,MiningMath.checkBlock(22.5f,3f,false,true,false)); // ender chest reports blacklist
        assertEquals(com.dolfin.zombies.griefing.GuardDecision.Reason.BLACKLIST,MiningMath.checkBlock(2f,3f,false,false,true)); // modded container BE
    }
}
