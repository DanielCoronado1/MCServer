package com.dolfin.zombies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.*;
/** Evaluated night survives stop/restart, manual stop and time rewinds: never reroll a night. */
public final class BloodMoonState extends SavedData {
    public static final Codec<BloodMoonState> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.BOOL.fieldOf("active").forGetter(s->s.active),
        Codec.LONG.fieldOf("lastNight").forGetter(s->s.lastNight),
        Codec.LONG.fieldOf("activeNight").forGetter(s->s.activeNight)
    ).apply(i,BloodMoonState::new));
    public static final SavedDataType<BloodMoonState> TYPE=new SavedDataType<>(DolfinZombies.id("bloodmoon"),BloodMoonState::new,CODEC,null);
    public boolean active; public long lastNight=-1,activeNight=-1;
    public BloodMoonState(){} public BloodMoonState(boolean active,long last,long started){this.active=active;lastNight=last;activeNight=started;}
    public static long night(long time){return Math.floorDiv(time,24000)+1;}
    public static boolean isNight(long time){long t=Math.floorMod(time,24000);return t>=13000&&t<23000;}
    public static boolean scheduled(long night,int every,double chance,double roll){return every>0?night%every==0:roll<chance;}
}
