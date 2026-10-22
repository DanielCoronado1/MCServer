package com.dolfin.space.travel;

import com.dolfin.space.ships.*;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class CockpitMenu extends AbstractContainerMenu {
    /**
     * Server-side cockpit readings. Vanilla polls every slot each tick, so the expensive launch
     * check (it scans the ascent column) is computed once per ship tick and reused for all 17.
     */
    private static final class Telemetry implements ContainerData {
        private final SpaceshipEntity ship;
        private final ServerPlayer pilot;
        private final int[] values = new int[17];
        private int sampledTick = -1;

        Telemetry(SpaceshipEntity ship, ServerPlayer pilot) {
            this.ship = ship;
            this.pilot = pilot;
        }

        public int get(int i) {
            if (ship.tickCount != sampledTick) sample();
            return i >= 0 && i < values.length ? values[i] : 0;
        }

        private void sample() {
            sampledTick = ship.tickCount;
            var check = TravelModule.check(ship, pilot);
            var own = OxygenStatusBridge.status(pilot);
            var passenger =
                    ship.getPassengers().stream()
                            .filter(e -> e != pilot && e instanceof Player)
                            .map(e -> (Player) e)
                            .findFirst()
                            .orElse(null);
            var other = OxygenStatusBridge.status(passenger);
            var journal = FlightController.journal(ship);
            values[0] = ship.fuel();
            values[1] = check.required();
            values[2] = check.surface() ? 1 : 0;
            values[3] = own.equipped() ? 1 : 0;
            values[4] = own.percent();
            values[5] = passenger == null ? 0 : other.equipped() ? 2 : 1;
            values[6] = other.percent();
            values[7] = journal == null ? -1 : journal.phase().ordinal();
            values[8] = journal == null ? 0 : journal.ticks();
            values[9] = check.allowed() ? 1 : 0;
            values[10] = ship.passengersAllowed ? 1 : 0;
            values[11] =
                    com.dolfin.space.KeplerDimension.isKepler(ship.level().dimension()) ? 1 : 0;
            values[12] = own.seconds();
            values[13] = other.seconds();
            values[14] = own.capacity();
            values[15] = other.capacity();
            values[16] = ship.tier().ordinal();
        }

        public void set(int i, int v) {}

        public int getCount() {
            return values.length;
        }
    }

    public final SpaceshipEntity ship;
    public final ContainerData data;

    public CockpitMenu(int id, Inventory inv, int entity) {
        this(
                id,
                inv,
                inv.player.level().getEntity(entity) instanceof SpaceshipEntity s ? s : null,
                false);
    }

    public CockpitMenu(int id, Inventory inv, SpaceshipEntity ship, boolean server) {
        super(TravelModule.MENU, id);
        this.ship = ship;
        data =
                server
                        ? new Telemetry(ship, (ServerPlayer) inv.player)
                        : new SimpleContainerData(17);
        addDataSlots(data);
    }

    public boolean stillValid(Player p) {
        return ship != null
                && !ship.isRemoved()
                && ship.getFirstPassenger() == p
                && ShipAccess.allowed(ship.owner, p);
    }

    public ItemStack quickMoveStack(Player p, int i) {
        return ItemStack.EMPTY;
    }

    public boolean clickMenuButton(Player p, int b) {
        if (!(p instanceof ServerPlayer sp) || !stillValid(p)) return false;
        if (b == 0) return FlightController.launch(ship, sp);
        if (b == 1 && !ship.inFlight) {
            ship.passengersAllowed = !ship.passengersAllowed;
            com.dolfin.space.DolfinSpace.LOGGER.info(
                    "event=ship_passenger_consent ship={} pilot={} allowed={}",
                    ship.getUUID(),
                    p.getUUID(),
                    ship.passengersAllowed);
            return true;
        }
        if (b == 2) {
            FlightController.dismount(sp);
            return true;
        }
        return false;
    }
}
