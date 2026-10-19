package com.dolfin.zombies.kinds;

/** Absolute-game-time cooldown; pure so the bookkeeping is unit-testable. */
public final class TickCooldown {
    private long readyAt;

    public boolean ready(long now) { return now >= readyAt; }

    public void start(long now, int durationTicks) { readyAt = now + Math.max(0, durationTicks); }

    public long remaining(long now) { return Math.max(0, readyAt - now); }

    public void reset() { readyAt = 0; }
}
