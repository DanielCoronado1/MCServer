package com.dolfin.zombies.griefing;

import com.dolfin.zombies.griefing.GuardDecision.ClaimStatus;
import com.dolfin.zombies.griefing.GuardDecision.Reason;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardDecisionTest {
    static final List<String> OVERWORLD=List.of("minecraft:overworld");

    /** Fake world: counts claim lookups so we can assert the expensive check is skipped. */
    static final class Fake implements GuardDecision.Env {
        final String dim; final boolean griefing; final ClaimStatus claim; int lookups;
        Fake(String dim,boolean griefing,ClaimStatus claim){this.dim=dim;this.griefing=griefing;this.claim=claim;}
        public String dimension(){return dim;}
        public boolean mobGriefing(){return griefing;}
        public ClaimStatus claim(){lookups++;return claim;}
    }

    @Test void allowedWhenEverythingPasses(){
        assertNull(GuardDecision.decide(new Fake("minecraft:overworld",true,ClaimStatus.UNCLAIMED),OVERWORLD,true,true));
    }
    @Test void worldNotAllowedWinsAndSkipsClaimLookup(){
        var f=new Fake("minecraft:the_nether",true,ClaimStatus.CLAIMED);
        assertEquals(Reason.WORLD,GuardDecision.decide(f,OVERWORLD,true,true));assertEquals(0,f.lookups);
        assertEquals(Reason.WORLD,GuardDecision.decide(f,List.of(),true,true));
    }
    @Test void mobGriefingFalseBlocksBlocksButNotAnimalAttacks(){
        var f=new Fake("minecraft:overworld",false,ClaimStatus.UNCLAIMED);
        assertEquals(Reason.GAMERULE,GuardDecision.decide(f,OVERWORLD,true,true));assertEquals(0,f.lookups);
        assertNull(GuardDecision.decide(f,OVERWORLD,true,false));
    }
    @Test void claimBlocksBothBlocksAndAnimals(){
        var f=new Fake("minecraft:overworld",true,ClaimStatus.CLAIMED);
        assertEquals(Reason.CLAIM,GuardDecision.decide(f,OVERWORLD,true,true));
        assertEquals(Reason.CLAIM,GuardDecision.decide(f,OVERWORLD,true,false));
    }
    @Test void respetarClaimsFalseIgnoresClaimsWithoutLookup(){
        var f=new Fake("minecraft:overworld",true,ClaimStatus.CLAIMED);
        assertNull(GuardDecision.decide(f,OVERWORLD,false,true));assertEquals(0,f.lookups);
    }
    @Test void claimLookupErrorFailsClosed(){
        assertEquals(Reason.CLAIM_ERROR,GuardDecision.decide(new Fake("minecraft:overworld",true,ClaimStatus.ERROR),OVERWORLD,true,true));
    }
    @Test void fullDecisionTable(){
        for(boolean world:new boolean[]{true,false})for(boolean griefing:new boolean[]{true,false})for(boolean respect:new boolean[]{true,false})
            for(var claim:ClaimStatus.values())for(boolean block:new boolean[]{true,false}){
                Reason expected=!world?Reason.WORLD:block&&!griefing?Reason.GAMERULE:!respect||claim==ClaimStatus.UNCLAIMED?null:claim==ClaimStatus.CLAIMED?Reason.CLAIM:Reason.CLAIM_ERROR;
                var f=new Fake(world?"minecraft:overworld":"minecraft:the_end",griefing,claim);
                assertEquals(expected,GuardDecision.decide(f,OVERWORLD,respect,block),()->"world="+world+" griefing="+griefing+" respect="+respect+" claim="+claim+" block="+block);
            }
    }
}
