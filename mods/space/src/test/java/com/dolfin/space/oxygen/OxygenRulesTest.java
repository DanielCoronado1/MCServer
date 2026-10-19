package com.dolfin.space.oxygen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OxygenRulesTest {
    @Test void acceptsSuffocationConfigAlias(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var path=dir.resolve("space.json");
        java.nio.file.Files.writeString(path,"{\"oxigeno\":{\"danioAsfixiaPorSegundo\":4.0}}");
        var config=new com.dolfin.space.SpaceConfig(path, s -> {});
        assertEquals(4.0,config.current().oxigeno().danioSinOxigenoPorSegundo());
    }
    @Test void onlyThreeTanksAllowed() {
        for (var tier : new String[]{"chico","mediano","grande"}) assertTrue(OxygenRules.isTank("tanque_oxigeno_"+tier));
        assertFalse(OxygenRules.isTank("stone")); assertFalse(OxygenRules.isTank("tanque_vacio"));
    }
    @Test void survivalExemptions() {
        assertTrue(OxygenRules.consumes(true,false,false,false));
        assertFalse(OxygenRules.consumes(false,false,false,false));
        assertFalse(OxygenRules.consumes(true,true,false,false));
        assertFalse(OxygenRules.consumes(true,false,true,false));
        assertFalse(OxygenRules.consumes(true,false,false,true));
    }
    @Test void consumesExactlyOneSecondWithoutUnderflow() {
        int remaining=300; for(int i=0;i<60;i++) remaining=OxygenRules.consume(remaining);
        assertEquals(240,remaining); assertEquals(0,OxygenRules.consume(0));
    }
    @Test void thresholdIncludesTwentyPercent() {
        assertTrue(OxygenRules.low(60,300,.20)); assertFalse(OxygenRules.low(61,300,.20));
        assertTrue(OxygenRules.low(0,0,.20));
    }
    @Test void compressorAddsOneMinuteAndClampsToCapacity() {
        assertEquals(60,OxygenRules.refill(0,300,60)); assertEquals(300,OxygenRules.refill(280,300,60));
        assertEquals(1200,OxygenRules.refill(1199,1200,Integer.MAX_VALUE));
    }
}
