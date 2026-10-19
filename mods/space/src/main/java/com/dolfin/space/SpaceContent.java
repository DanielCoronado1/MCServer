package com.dolfin.space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Shared block, item, and creative tab registration helpers for {@code dolfinspace}.
 * Every item registered through this class is automatically included in the mod's creative tab.
 */
public final class SpaceContent {
    public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, DolfinSpace.id("space"));

    private static final List<Item> REGISTERED_ITEMS = new ArrayList<>();
    private static final List<Block> REGISTERED_BLOCKS = new ArrayList<>();

    public static final Block REGOLITO_BLOCK = registerBlockWithItem(
            "regolito",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.TUFF)
                    .requiresCorrectToolForDrops()
    );
    public static final Item REGOLITO_ITEM = REGOLITO_BLOCK.asItem();

    public static final CreativeModeTab CREATIVE_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            CREATIVE_TAB_KEY,
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dolfinspace.space"))
                    .icon(() -> new ItemStack(REGOLITO_ITEM))
                    .displayItems((params, output) -> {
                        for (Item item : REGISTERED_ITEMS) {
                            output.accept(item);
                        }
                    })
                    .build()
    );

    private SpaceContent() {}

    /** Ensures static registration of the creative tab and foundation content. */
    public static void init() {
        DolfinSpace.LOGGER.info(
                "event=content_registered blocks={} items={} creativeTab={}",
                REGISTERED_BLOCKS.size(),
                REGISTERED_ITEMS.size(),
                CREATIVE_TAB_KEY.identifier()
        );
    }

    /** Registers a block and a matching {@link BlockItem} added to the space creative tab. */
    public static <B extends Block> B registerBlockWithItem(
            String path,
            Function<BlockBehaviour.Properties, B> factory,
            BlockBehaviour.Properties properties
    ) {
        B block = registerBlock(path, factory, properties);
        registerBlockItem(path, block, new Item.Properties());
        return block;
    }

    /** Registers a standalone block without creating an item. */
    public static <B extends Block> B registerBlock(
            String path,
            Function<BlockBehaviour.Properties, B> factory,
            BlockBehaviour.Properties properties
    ) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, DolfinSpace.id(path));
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        REGISTERED_BLOCKS.add(block);
        return block;
    }

    /** Registers a {@link BlockItem} for an already registered block and adds it to the creative tab. */
    public static BlockItem registerBlockItem(String path, Block block, Item.Properties properties) {
        return registerItem(path, props -> new BlockItem(block, props.useBlockDescriptionPrefix()), properties);
    }

    /** Registers an item and appends it to the mod's creative tab. */
    public static <I extends Item> I registerItem(
            String path,
            Function<Item.Properties, I> factory,
            Item.Properties properties
    ) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DolfinSpace.id(path));
        I item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
        REGISTERED_ITEMS.add(item);
        return item;
    }

    /** Returns an unmodifiable view of all items registered in the space creative tab. */
    public static List<Item> registeredItems() {
        return Collections.unmodifiableList(REGISTERED_ITEMS);
    }

    /** Returns an unmodifiable view of all blocks registered by the mod. */
    public static List<Block> registeredBlocks() {
        return Collections.unmodifiableList(REGISTERED_BLOCKS);
    }
}
