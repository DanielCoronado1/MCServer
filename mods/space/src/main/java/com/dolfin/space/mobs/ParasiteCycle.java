package com.dolfin.space.mobs;

/** The same state machine drives the entity and the unit tests. */
public final class ParasiteCycle {
    public enum Attack { NONE, BITE, LAUNCH }
    private int ticks, attacks;
    public void reset() { ticks=attacks=0; }
    public Attack tick() {
        if (++ticks<40) return Attack.NONE;
        ticks=0;
        return ++attacks>=3?Attack.LAUNCH:Attack.BITE;
    }
}
