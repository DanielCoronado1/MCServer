package com.dolfin.space.ships;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
/** Shared codec used by entities and portable items. */
public record ShipState(String owner,int tier,int fuel,long home,boolean hasHome,boolean passengersAllowed) {
 public static final Codec<ShipState> CODEC=RecordCodecBuilder.create(i->i.group(
  Codec.STRING.fieldOf("owner").forGetter(ShipState::owner),Codec.INT.fieldOf("tier").forGetter(ShipState::tier),
  Codec.INT.fieldOf("fuel").forGetter(ShipState::fuel),Codec.LONG.fieldOf("home").forGetter(ShipState::home),
  Codec.BOOL.fieldOf("hasHome").forGetter(ShipState::hasHome),Codec.BOOL.fieldOf("passengersAllowed").forGetter(ShipState::passengersAllowed)
 ).apply(i,ShipState::new));
 public ShipState {fuel=Math.clamp(fuel,0,100);tier=Math.clamp(tier,0,2);}
 public static int transfer(int fuel,int availableCanisters,int outputRoom) {return fuel>75 || availableCanisters<1 || outputRoom<1?0:25;}
}
