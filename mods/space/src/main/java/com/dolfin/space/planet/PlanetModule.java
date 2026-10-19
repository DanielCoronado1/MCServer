package com.dolfin.space.planet;

import com.dolfin.space.*;
import java.util.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class PlanetModule {
    public static final Map<String,Block> BLOCKS = new LinkedHashMap<>();
    public static final Map<String,Item> ITEMS = new LinkedHashMap<>();
    private PlanetModule() {}
    public static void init() {
        for (String id : PlanetCatalog.BLOCKS) {
            var settings=BlockBehaviour.Properties.of().strength(3.0F,6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops();
            Block block;
            if (id.equals("hongo_bioluminiscente")) block=SpaceContent.registerBlockWithItem(id,GlowingFungusBlock::new,BlockBehaviour.Properties.of().noCollision().instabreak().lightLevel(s -> 10).sound(SoundType.FUNGUS));
            else if (id.equals("lodo_acido")) block=SpaceContent.registerBlockWithItem(id,AcidMudBlock::new,settings.strength(0.8F).sound(SoundType.MUD));
            else block=SpaceContent.registerBlockWithItem(id,Block::new,id.equals("bloque_cristal_energia")?settings.lightLevel(s -> 13):settings);
            BLOCKS.put(id,block);
        }
        for (String id : PlanetCatalog.ITEMS) ITEMS.put(id,SpaceContent.registerItem(id,Item::new,new Item.Properties()));
        DolfinSpace.LOGGER.info("event=planet_registered biomes={} blocks={} items={}",PlanetCatalog.BIOMES,BLOCKS.keySet(),ITEMS.keySet());
    }
}
