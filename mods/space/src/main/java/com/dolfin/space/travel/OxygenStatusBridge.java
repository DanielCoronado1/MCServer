package com.dolfin.space.travel;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;

/** #29 can install a provider without either branch importing the other's implementation. */
public final class OxygenStatusBridge {
  public record Status(boolean equipped, int percent) {
    public Status {
      percent = Math.clamp(percent, -1, 100);
    }
  }

  private static volatile java.util.function.Function<Player, Status> provider =
      OxygenStatusBridge::inventoryStatus;

  public static void install(java.util.function.Function<Player, Status> statusProvider) {
    provider = java.util.Objects.requireNonNull(statusProvider);
  }

  public static Status status(Player p) {
    return p == null ? new Status(false, -1) : provider.apply(p);
  }

  private static Status inventoryStatus(Player p) {
    Status best = new Status(false, -1);
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      var stack = p.getInventory().getItem(i);
      String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      if (!id.startsWith("dolfinspace:tanque_oxigeno_")) continue;
      var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
      int percent = tag.getIntOr("oxygen_percent", -1);
      if (percent < 0 && stack.isDamageableItem())
        percent =
            100
                * (stack.getMaxDamage() - stack.getDamageValue())
                / Math.max(1, stack.getMaxDamage());
      // Unknown data must be displayed as unknown, never silently reported as a full tank.
      best = new Status(true, Math.max(best.percent(), percent));
    }
    return best;
  }

  private OxygenStatusBridge() {}
}
