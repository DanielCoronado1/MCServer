package com.dolfin.space.travel;

import com.dolfin.space.oxygen.*;

import net.minecraft.world.entity.player.Player;

/** Exact, equipped-slot status shared by pilot/passenger cockpit telemetry. */
public final class OxygenStatusBridge {
    public record Status(boolean equipped, int percent, int seconds, int capacity) {
        public Status {
            percent = Math.clamp(percent, 0, 100);
            seconds = Math.max(0, seconds);
            capacity = Math.max(0, capacity);
        }
    }

    public static Status measure(boolean equipped, int seconds, int capacity) {
        return new Status(
                equipped,
                capacity <= 0 ? 0 : (int) (100L * Math.clamp(seconds, 0, capacity) / capacity),
                seconds,
                capacity);
    }

    public static Status status(Player player) {
        if (!(player instanceof TankAccess access)) return measure(false, 0, 0);
        var tank = access.dolfinspace$tank().getItem(0);
        if (!TankItem.isTank(tank)) return measure(false, 0, 0);
        return measure(true, TankItem.remaining(tank), tank.getMaxDamage());
    }

    private OxygenStatusBridge() {}
}
