package com.dolfin.space.ships;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
public final class PlatformRules {
 public static boolean valid(ShipTier tier,BiPredicate<Integer,Integer> platform) {
  int r=tier.radius();
  for(int x=-r;x<=r;x++) for(int z=-r;z<=r;z++) if(!platform.test(x,z)) return false;
  // A larger contiguous platform must be addressed at its own center.
  if(platform.test(-r-1,0)!=platform.test(r+1,0) || platform.test(0,-r-1)!=platform.test(0,r+1)) return false;
  return true;
 }
 public static boolean valid(Level level,BlockPos center,ShipTier tier) {return valid(tier,(x,z)->level.getBlockState(center.offset(x,0,z)).is(ShipsModule.PLATFORM));}
 private PlatformRules() {}
}
