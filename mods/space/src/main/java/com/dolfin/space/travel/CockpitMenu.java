package com.dolfin.space.travel;

import com.dolfin.space.ships.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class CockpitMenu extends AbstractContainerMenu {
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
            ? new ContainerData() {
              public int get(int i) {
                var check = TravelModule.check(ship, (ServerPlayer) inv.player);
                var pilot = OxygenStatusBridge.status(inv.player);
                var passenger =
                    ship.getPassengers().stream()
                        .filter(e -> e != inv.player && e instanceof Player)
                        .map(e -> (Player) e)
                        .findFirst()
                        .orElse(null);
                var other = OxygenStatusBridge.status(passenger);
                var journal = FlightController.journal(ship);
                return switch (i) {
                  case 0 -> ship.fuel();
                  case 1 -> check.required();
                  case 2 -> check.surface() ? 1 : 0;
                  case 3 -> pilot.equipped() ? 1 : 0;
                  case 4 -> pilot.percent();
                  case 5 -> passenger == null ? 0 : other.equipped() ? 2 : 1;
                  case 6 -> other.percent();
                  case 7 -> journal == null ? -1 : journal.phase().ordinal();
                  case 8 -> journal == null ? 0 : journal.ticks();
                  case 9 -> check.allowed() ? 1 : 0;
                  case 10 -> ship.passengersAllowed ? 1 : 0;
                  case 12 -> pilot.seconds();
                  case 13 -> other.seconds();
                  case 14 -> pilot.capacity();
                  case 15 -> other.capacity();
                  case 16 -> ship.tier().ordinal();
                  case 11 ->
                      com.dolfin.space.KeplerDimension.isKepler(ship.level().dimension()) ? 1 : 0;
                  default -> 0;
                };
              }

              public void set(int i, int v) {}

              public int getCount() {
                return 17;
              }
            }
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
