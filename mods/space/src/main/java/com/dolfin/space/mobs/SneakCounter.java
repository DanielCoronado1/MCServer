package com.dolfin.space.mobs;

import java.util.ArrayDeque;

/** Counts rising edges, never held sneak; 40 ticks = two seconds of game time. */
public final class SneakCounter {
    private final ArrayDeque<Integer> presses = new ArrayDeque<>();
    private boolean previous;

    public void reset(boolean sneaking) {
        presses.clear();
        previous = sneaking;
    }

    public boolean update(boolean sneaking, int tick) {
        while (!presses.isEmpty() && tick - presses.peekFirst() > 40) presses.removeFirst();
        if (sneaking && !previous) presses.addLast(tick);
        previous = sneaking;
        if (presses.size() >= 3) {
            presses.clear();
            return true;
        }
        return false;
    }
}
