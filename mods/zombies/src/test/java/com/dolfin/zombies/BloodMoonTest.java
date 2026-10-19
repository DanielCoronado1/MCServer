package com.dolfin.zombies;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.zombies.BloodMoonSchedule.Step;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.DoubleSupplier;

class BloodMoonTest {
    static final long DAY = 24000;
    static final DoubleSupplier NO_ROLL =
            () -> {
                throw new AssertionError("cadaNNoches must not roll");
            };

    /** Runs the clock in 100-tick steps and returns the nights on which a blood moon started. */
    static List<Long> simulate(
            BloodMoonSchedule s,
            long from,
            long to,
            int every,
            double chance,
            DoubleSupplier roll) {
        var starts = new ArrayList<Long>();
        for (long t = from; t < to; t += 100) {
            var o = s.advance(t, true, every, chance, roll);
            if (o.step() == Step.START) starts.add(o.night());
        }
        return starts;
    }

    @Test
    void nightArithmetic() {
        assertFalse(BloodMoonSchedule.isNight(12999));
        assertTrue(BloodMoonSchedule.isNight(13000));
        assertTrue(BloodMoonSchedule.isNight(22999));
        assertFalse(BloodMoonSchedule.isNight(23000));
        assertEquals(1, BloodMoonSchedule.night(0));
        assertEquals(7, BloodMoonSchedule.night(6 * DAY + 13000));
        assertTrue(BloodMoonSchedule.isNight(-DAY + 14000));
        assertEquals(0, BloodMoonSchedule.night(-1));
    }

    @Test
    void everySevenNightsIsExactAndNeverRolls() {
        var starts = simulate(new BloodMoonSchedule(), 0, 70 * DAY, 7, 1, NO_ROLL);
        assertEquals(List.of(7L, 14L, 21L, 28L, 35L, 42L, 49L, 56L, 63L, 70L), starts);
        for (int n = 1; n <= 70; n++)
            assertEquals(n % 7 == 0, BloodMoonSchedule.selected(n, 7, 0, Double.NaN));
    }

    @Test
    void blockedNightsLastFromDuskToDawnOnly() {
        var s = new BloodMoonSchedule();
        long n7 = 6 * DAY;
        s.advance(n7 + 100, true, 7, 0, NO_ROLL);
        assertFalse(s.active);
        assertEquals(7, s.scheduledNight);
        assertTrue(
                s.sleepBlocked(n7 + 12600),
                "pre-dusk nap (beds work from ~12542 or in thunderstorms) must not skip it");
        assertEquals(Step.START, s.advance(n7 + 13000, true, 7, 0, NO_ROLL).step());
        assertTrue(s.sleepBlocked(n7 + 20000));
        var end = s.advance(n7 + 23000, true, 7, 0, NO_ROLL);
        assertEquals(Step.END_DAWN, end.step());
        assertEquals("dawn", end.trigger());
        assertEquals(7, end.night());
        assertFalse(s.sleepBlocked(n7 + 23100));
        assertFalse(s.sleepBlocked(7 * DAY + 13000));
    }

    @Test
    void probabilityUsesInjectedRollOncePerNight() {
        var rolls = new ArrayDeque<>(List.of(.5, .05, .09999, .1, .99));
        int[] calls = {0};
        DoubleSupplier roll =
                () -> {
                    calls[0]++;
                    return rolls.removeFirst();
                };
        var starts = simulate(new BloodMoonSchedule(), 0, 5 * DAY, 0, .10, roll);
        assertEquals(List.of(2L, 3L), starts);
        assertEquals(5, calls[0]);
        assertTrue(BloodMoonSchedule.selected(1, 0, .1, .0999));
        assertFalse(BloodMoonSchedule.selected(1, 0, .1, .1));
        assertFalse(BloodMoonSchedule.selected(1, 0, 0, 0));
        assertTrue(BloodMoonSchedule.selected(1, 0, 1, .999999));
    }

    @Test
    void restartMidMoonKeepsItActiveWithoutReroll() {
        var s = new BloodMoonSchedule();
        s.advance(6 * DAY + 15000, true, 7, 0, NO_ROLL);
        assertTrue(s.active);
        // Same fields as BloodMoonState's codec.
        var restored =
                new BloodMoonSchedule(s.active, s.lastNight, s.activeNight, s.scheduledNight);
        assertEquals(Step.NONE, restored.advance(6 * DAY + 15100, true, 7, 0, NO_ROLL).step());
        assertTrue(restored.active);
        assertEquals(Step.END_DAWN, restored.advance(6 * DAY + 23000, true, 7, 0, NO_ROLL).step());
        // Restart during the day of a selected night keeps it selected.
        var pending = new BloodMoonSchedule();
        pending.advance(13 * DAY + 1000, true, 7, 0, NO_ROLL);
        var again =
                new BloodMoonSchedule(
                        pending.active,
                        pending.lastNight,
                        pending.activeNight,
                        pending.scheduledNight);
        assertEquals(Step.START, again.advance(13 * DAY + 13000, true, 7, 0, NO_ROLL).step());
    }

    @Test
    void timeRewindNeverRerollsAndJumpIsReported() {
        var s = new BloodMoonSchedule();
        int[] calls = {0};
        DoubleSupplier yes =
                () -> {
                    calls[0]++;
                    return 0;
                };
        s.advance(13000, true, 0, 1, yes);
        assertTrue(s.active);
        s.advance(23000, true, 0, 1, yes);
        s.advance(13000, true, 0, 1, yes);
        assertFalse(s.active);
        assertEquals(1, calls[0]);
        var j = new BloodMoonSchedule();
        j.advance(6 * DAY + 100, true, 7, 0, NO_ROLL);
        var o = j.advance(9 * DAY + 100, true, 7, 0, NO_ROLL);
        assertEquals(7, o.missedNight());
        assertEquals(10, o.evaluatedNight());
        assertFalse(j.active);
    }

    @Test
    void disablingEndsAndConsumesNights() {
        var s = new BloodMoonSchedule();
        s.advance(6 * DAY + 14000, true, 7, 0, NO_ROLL);
        var o = s.advance(6 * DAY + 14100, false, 7, 0, NO_ROLL);
        assertEquals(Step.END_DISABLED, o.step());
        assertEquals("disabled", o.trigger());
        assertEquals(
                Step.NONE,
                s.advance(6 * DAY + 14200, true, 7, 0, NO_ROLL).step(),
                "re-enabling the same night does not restart it");
        var off = new BloodMoonSchedule();
        for (long t = 0; t < 14 * DAY; t += 500)
            assertNotEquals(Step.START, off.advance(t, false, 7, 0, NO_ROLL).step());
        assertFalse(off.active);
        assertEquals(14, off.lastNight);
        assertEquals(-1, off.scheduledNight);
    }

    @Test
    void commandsStartStopAndCancel() {
        var s = new BloodMoonSchedule();
        assertTrue(s.forceStart(2 * DAY + 13000));
        assertFalse(s.forceStart(2 * DAY + 13000));
        assertEquals(3, s.activeNight);
        assertEquals(3, s.lastNight);
        assertTrue(s.forceStop());
        assertFalse(s.active);
        assertFalse(s.forceStop());
        assertEquals(
                Step.NONE,
                s.advance(2 * DAY + 14000, true, 0, 1, () -> 0).step(),
                "a stopped night is not rerolled");
        var p = new BloodMoonSchedule();
        p.advance(6 * DAY + 100, true, 7, 0, NO_ROLL);
        assertTrue(p.pendingTonight(6 * DAY + 5000));
        assertTrue(p.forceStop());
        assertEquals(Step.NONE, p.advance(6 * DAY + 13000, true, 7, 0, NO_ROLL).step());
    }

    @Test
    void optionsAndSpawnArithmetic() {
        var o = BloodMoonOptions.from(ZombieConfig.generalDefaults());
        assertTrue(o.enabled());
        assertEquals(.10, o.chance());
        assertEquals(0, o.every());
        assertEquals(2.5, o.multiplier());
        assertFalse(o.onlySpecials());
        assertTrue(o.warn() && o.redSky() && o.sleepBlocked() && o.chatMessages());
        assertEquals(1.5, o.xpMultiplier());
        var general = ZombieConfig.generalDefaults();
        general.addProperty("activo", false);
        assertFalse(BloodMoonOptions.from(general).enabled());
        assertEquals(175, BloodMoonOptions.scaledCap(70, 2.5));
        assertEquals(70, BloodMoonOptions.scaledCap(70, .5));
        assertEquals(1, BloodMoonOptions.extraPasses(2.5, .6));
        assertEquals(2, BloodMoonOptions.extraPasses(2.5, .4));
        assertEquals(0, BloodMoonOptions.extraPasses(1, 0));
        assertEquals(0, BloodMoonOptions.extraPasses(.5, 0));
        assertEquals(8, BloodMoonOptions.experience(5, 1.5, true));
        assertEquals(5, BloodMoonOptions.experience(5, 1.5, false));
        assertEquals(8, BloodMoonOptions.burnSeconds(1));
        assertEquals(32, BloodMoonOptions.burnSeconds(30));
        assertEquals(120, BloodMoonOptions.burnSeconds(1000));
    }
}
