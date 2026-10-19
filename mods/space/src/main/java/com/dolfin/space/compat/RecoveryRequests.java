package com.dolfin.space.compat;

import com.dolfin.space.travel.FlightLedger;

import java.util.*;

/** Admin-only durable queue; the existing flight recovery worker handles unloaded ships. */
public final class RecoveryRequests {
    public static int queue(boolean admin, UUID owner, Map<String, FlightLedger.Entry> entries) {
        if (!admin) return 0;
        int count = 0;
        for (var entry : List.copyOf(entries.values()))
            if (entry.owner().equals(owner.toString())) {
                entries.put(entry.ship(), entry.returning());
                count++;
            }
        return count;
    }

    private RecoveryRequests() {}
}
