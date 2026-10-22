package com.dolfin.space.travel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FlightProfileTest {
    @Test
    void ascentEasesInAndEndsAtFullAltitude() {
        double first = FlightProfile.ascentOffset(40, FlightProfile.ASCENT_TICKS);
        double mid = FlightProfile.ascentOffset(40, FlightProfile.ASCENT_TICKS / 2);
        assertTrue(first < .01);
        assertTrue(mid < 20, "accelerating ascent is below the linear midpoint");
        assertEquals(40, FlightProfile.ascentOffset(40, 1), 1e-9);
    }

    @Test
    void descentStartsAtDropAndTouchesDownGently() {
        assertEquals(
                FlightProfile.DESCENT_DROP,
                FlightProfile.descentOffset(FlightProfile.DESCENT_TICKS),
                1e-9);
        assertEquals(0, FlightProfile.descentOffset(1), 1e-9);
        double lastStep = FlightProfile.descentOffset(2) - FlightProfile.descentOffset(1);
        double firstStep =
                FlightProfile.descentOffset(FlightProfile.DESCENT_TICKS)
                        - FlightProfile.descentOffset(FlightProfile.DESCENT_TICKS - 1);
        assertTrue(lastStep < firstStep / 10);
    }

    @Test
    void thrustIsColdWhenParkedAndIgnitesNearLiftoff() {
        assertEquals(0, FlightProfile.thrust(-1, 0));
        assertEquals(0, FlightProfile.thrust(0, 100));
        assertTrue(FlightProfile.thrust(0, 5) > 0);
        assertEquals(1, FlightProfile.thrust(1, 1), 1e-6);
        assertEquals(1, FlightProfile.thrust(2, 10), 1e-6);
        assertTrue(FlightProfile.thrust(3, 1) > FlightProfile.thrust(3, 80));
        assertEquals(0, FlightProfile.thrust(9, 0));
    }

    @Test
    void beepsRiseOverTheFinalSeconds() {
        assertEquals(1.2f, FlightProfile.beepPitch(100));
        assertTrue(FlightProfile.beepPitch(20) > FlightProfile.beepPitch(60));
        assertEquals(20, FlightProfile.verticalSpeed(10, 11), 1e-9);
        assertEquals(0, FlightProfile.verticalSpeed(Double.NaN, 1));
    }

    @Test
    void checklistFlagsRoundTrip() {
        var ok = new LaunchRules.Checklist(true, true, true, true, false, 50);
        assertTrue(LaunchRules.Checklist.ready(ok.flags()));
        var noFuel = new LaunchRules.Checklist(true, false, true, true, true, 50);
        assertFalse(LaunchRules.Checklist.ready(noFuel.flags()));
        assertNotEquals(0, noFuel.flags() & LaunchRules.Checklist.TANK_WARNING);
    }
}
