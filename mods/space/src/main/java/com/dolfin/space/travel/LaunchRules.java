package com.dolfin.space.travel;

import com.dolfin.space.SpaceConfig;

/** Server and UI share mandatory checks. Oxygen is a warning, never a launch veto. */
public final class LaunchRules {
  public record Checklist(
      boolean surface,
      boolean fuel,
      boolean pilot,
      boolean active,
      boolean tankWarning,
      int required) {
    public boolean allowed() {
      return surface && fuel && pilot && active;
    }
  }

  public static Checklist check(
      boolean earth,
      boolean surface,
      int fuel,
      boolean pilot,
      boolean active,
      boolean tank,
      SpaceConfig.ShipsSection cfg) {
    int required =
        earth
            ? Math.max(
                cfg.porcentajeCombustibleMinimoDespegueTierra(),
                2 * cfg.porcentajeCombustiblePorViaje())
            : cfg.porcentajeCombustiblePorViaje();
    return new Checklist(surface, fuel >= required, pilot, active, !tank, required);
  }

  public static int deduct(int fuel, int cost) {
    if (cost < 0 || fuel < cost) throw new IllegalArgumentException("insufficient fuel");
    return fuel - cost;
  }

  public static double shake(double serverIntensity, double clientScale) {
    if (!Double.isFinite(serverIntensity) || !Double.isFinite(clientScale)) return 0;
    return Math.clamp(serverIntensity, 0, 1) * Math.clamp(clientScale, 0, 1);
  }

  private LaunchRules() {}
}
