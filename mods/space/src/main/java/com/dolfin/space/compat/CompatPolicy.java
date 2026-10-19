package com.dolfin.space.compat;
/** Pure decisions used by native API adapters and security-boundary tests. */
public final class CompatPolicy {
 public enum Action { BREAK, PLACE, CONTAINER, SHIP_PLACE, SHIP_USE, PVP }
 public static boolean allowed(boolean installed,boolean respectClaims,Boolean nativeBlocked){return !installed||!respectClaims||Boolean.FALSE.equals(nativeBlocked);}
 public static boolean pvp(boolean globalEnabled,boolean teamAllows,boolean claimAllows){return globalEnabled&&teamAllows&&claimAllows;}
 public static boolean audit(boolean server,boolean kepler,boolean successful){return server&&kepler&&successful;}
 public static boolean footprint(com.dolfin.space.ships.ShipTier tier,java.util.function.BiPredicate<Integer,Integer> allows){int r=tier.radius();for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(!allows.test(x,z))return false;return true;}
 private CompatPolicy(){}
}
