package com.dolfin.space.ships;
import com.dolfin.space.*;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
public final class ShipsModule {
 public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
 static {
  for(String id:List.of("punta_conica","punta_titanio","placa_casco","placa_casco_titanio","aleta_nave","motor_nave","motor_avanzado","tanque_combustible_nave","asiento_piloto")) ITEMS.put(id,SpaceContent.registerItem(id,Item::new,new Item.Properties()));
  for(var tier:ShipTier.values()) ITEMS.put(tier.itemId(),SpaceContent.registerItem(tier.itemId(),p->new ShipItem(p,tier),new Item.Properties().stacksTo(1)));
 }
 public static final Block PLATFORM=SpaceContent.registerBlockWithItem("bloque_plataforma",PlatformBlock::new,BlockBehaviour.Properties.of().strength(4,6));
 public static final AssemblyBlock ASSEMBLY=SpaceContent.registerBlockWithItem("mesa_ensamblaje",AssemblyBlock::new,BlockBehaviour.Properties.of().strength(4,6));
 public static final FuelLoaderBlock LOADER=SpaceContent.registerBlockWithItem("cargador_combustible",FuelLoaderBlock::new,BlockBehaviour.Properties.of().strength(4,6));
 public static final BlockEntityType<AssemblyBlockEntity> ASSEMBLY_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,DolfinSpace.id("mesa_ensamblaje"),FabricBlockEntityTypeBuilder.create(AssemblyBlockEntity::new,ASSEMBLY).build());
 public static final BlockEntityType<FuelLoaderBlockEntity> LOADER_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,DolfinSpace.id("cargador_combustible"),FabricBlockEntityTypeBuilder.create(FuelLoaderBlockEntity::new,LOADER).build());
 public static final MenuType<AssemblyMenu> MENU=Registry.register(BuiltInRegistries.MENU,DolfinSpace.id("ensamblaje"),new ExtendedMenuType<>((id,inv,t)->new AssemblyMenu(id,inv),ByteBufCodecs.INT));
 public static final EntityType<SpaceshipEntity> SHIP=Registry.register(BuiltInRegistries.ENTITY_TYPE,DolfinSpace.id("nave"),EntityType.Builder.<SpaceshipEntity>of(SpaceshipEntity::new,MobCategory.MISC).sized(2.5f,3f).fireImmune().clientTrackingRange(12).updateInterval(2).build(ResourceKey.create(Registries.ENTITY_TYPE,DolfinSpace.id("nave"))));
 /** #28 installs the cockpit without creating an implementation dependency in #27. */
 public static java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> onPilotMounted=p->{};
 public static java.util.function.Consumer<SpaceshipEntity> flightTick=s->{};
 public static void init() {DolfinSpace.LOGGER.info("event=ships_registered tiers=3");}
 private ShipsModule() {}
}
