package com.dolfin.zombies.griefing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CongeladoIceTest {
    @Test void discSizesAndCentreFirst(){
        assertEquals(1,CongeladoIce.disc(0).size());
        assertEquals(5,CongeladoIce.disc(1).size());
        assertEquals(13,CongeladoIce.disc(2).size());
        assertArrayEquals(new int[]{0,0},CongeladoIce.disc(2).getFirst());
        for(int[] o:CongeladoIce.disc(3))assertTrue(o[0]*o[0]+o[1]*o[1]<=9);
    }
}
