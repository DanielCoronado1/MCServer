package com.dolfin.zombies.griefing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
    @Test void capsEventsPerWindowAndRecovers(){
        var r=new RateLimiter(1200);
        for(int i=0;i<20;i++)assertTrue(r.tryAcquire(i*10,20));
        assertFalse(r.canAcquire(500,20));assertFalse(r.tryAcquire(1199,20));
        assertEquals(20,r.recent(1199));
        assertTrue(r.tryAcquire(1200,20)); // first event (t=0) left the window
        assertEquals(20,r.recent(1200));
    }
    @Test void canAcquireDoesNotRecord(){
        var r=new RateLimiter(100);
        for(int i=0;i<5;i++)assertTrue(r.canAcquire(0,1));
        assertTrue(r.tryAcquire(0,1));assertFalse(r.canAcquire(50,1));assertTrue(r.canAcquire(100,1));
    }
    @Test void zeroCapRejectsAndCapChangesApplyLive(){
        var r=new RateLimiter(1200);
        assertFalse(r.tryAcquire(0,0));
        assertTrue(r.tryAcquire(0,2));assertTrue(r.tryAcquire(1,2));assertFalse(r.tryAcquire(2,2));
        assertTrue(r.tryAcquire(3,5));assertTrue(r.tryAcquire(4,5));assertTrue(r.tryAcquire(5,5));assertFalse(r.tryAcquire(6,5));
        assertFalse(r.canAcquire(7,3));
    }
}
