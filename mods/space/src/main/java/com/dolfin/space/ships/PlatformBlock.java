package com.dolfin.space.ships;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
/** The center is marked when a ship is placed; validity always uses the complete current structure. */
public final class PlatformBlock extends Block {
 public static final BooleanProperty CENTER=BooleanProperty.create("center");
 public PlatformBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(CENTER,false));}
 protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(CENTER);}
}
