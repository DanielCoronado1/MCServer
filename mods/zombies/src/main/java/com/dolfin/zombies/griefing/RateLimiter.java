package com.dolfin.zombies.griefing;

/**
 * Sliding-window cap (per entity, not persisted): at most {@code max} events in any {@code
 * windowTicks} window. Uses game time so it is deterministic and pauses with the server.
 */
public final class RateLimiter {
    private final long windowTicks;
    private long[] times = new long[0];
    private int head, size;

    public RateLimiter(long windowTicks) {
        this.windowTicks = windowTicks;
    }

    /**
     * True if one more event fits at {@code now} under a cap of {@code max}. Does not record it.
     */
    public boolean canAcquire(long now, int max) {
        if (max <= 0) return false;
        evict(now);
        return size < max;
    }

    /** Records an event if it fits; returns whether it was recorded. */
    public boolean tryAcquire(long now, int max) {
        if (!canAcquire(now, max)) return false;
        if (times.length < max) {
            long[] grown = new long[max];
            for (int i = 0; i < size; i++) grown[i] = times[(head + i) % times.length];
            times = grown;
            head = 0;
        }
        times[(head + size) % times.length] = now;
        size++;
        return true;
    }

    public int recent(long now) {
        evict(now);
        return size;
    }

    private void evict(long now) {
        while (size > 0 && now - times[head] >= windowTicks) {
            head = (head + 1) % times.length;
            size--;
        }
    }
}
