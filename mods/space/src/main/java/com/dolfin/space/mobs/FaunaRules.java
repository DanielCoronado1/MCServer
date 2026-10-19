package com.dolfin.space.mobs;

import java.util.List;
import java.util.Set;

public final class FaunaRules {
    public static final List<String> MOBS=List.of("parasito","acechador","gusano","centinela","zombi_espacial","esqueleto_espacial","creeper_espacial");
    private static final Set<String> NATURAL=Set.of("dolfinspace:regolito","dolfinspace:roca_lunar");
    public static final int SENTINEL_COOLDOWN=60;
    private FaunaRules() {}
    public static boolean eligible(boolean creative, boolean spectator, boolean ship, boolean alive) { return !creative && !spectator && !ship && alive; }
    public static boolean leap(double distanceSquared, boolean eligible, int attached, int fleeing) { return eligible && attached==0 && fleeing<=0 && distanceSquared<16; }
    public static boolean latch(double distanceSquared, boolean eligible, int attached) { return eligible && attached==0 && distanceSquared<=1.5625; }
    public static boolean natural(String block, boolean placed) { return !placed && NATURAL.contains(block); }
    public static boolean projectileReady(int ticksSinceShot) { return ticksSinceShot>=SENTINEL_COOLDOWN; }
    public static float incomingDamage(float damage, boolean arrow) { return arrow?damage*2:damage; }
    public static boolean night(long time) { long t=Math.floorMod(time,24000); return t>=13000 && t<23000; }
}
