package com.dolfin.space.machines;

import com.dolfin.space.*;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.*;

public final class MachinesModule {
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    static {
        for (String id :
                List.of(
                        "mezcla_acero",
                        "lingote_acero",
                        "placa_acero",
                        "placa_cobre",
                        "placa_oro",
                        "placa_titanio",
                        "circuito_basico",
                        "circuito_avanzado",
                        "bidon_vacio",
                        "bidon_combustible"))
            ITEMS.put(
                    id,
                    SpaceContent.registerItem(
                            id,
                            Item::new,
                            new Item.Properties().stacksTo(id.startsWith("bidon") ? 16 : 64)));
    }

    public static final Block STEEL =
            SpaceContent.registerBlockWithItem(
                    "bloque_acero", Block::new, BlockBehaviour.Properties.of().strength(5, 6));
    public static final MachineBlock COMPRESSOR =
            machine("compresor", MachineRecipes.Kind.COMPRESSOR);
    public static final MachineBlock CIRCUITS =
            machine("fabricador_circuitos", MachineRecipes.Kind.CIRCUITS);
    public static final MachineBlock REFINERY = machine("refineria", MachineRecipes.Kind.REFINERY);
    public static final BlockEntityType<AbstractSpaceMachineBlockEntity> TYPE =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    DolfinSpace.id("maquina"),
                    FabricBlockEntityTypeBuilder.create(
                                    AbstractSpaceMachineBlockEntity::new,
                                    COMPRESSOR,
                                    CIRCUITS,
                                    REFINERY)
                            .build());
    public static final MenuType<MachineMenu> MENU =
            Registry.register(
                    BuiltInRegistries.MENU,
                    DolfinSpace.id("maquina"),
                    new ExtendedMenuType<>(
                            (id, inv, kind) -> new MachineMenu(id, inv, kind), ByteBufCodecs.INT));

    private static MachineBlock machine(String id, MachineRecipes.Kind kind) {
        return SpaceContent.registerBlockWithItem(
                id, p -> new MachineBlock(p, kind), BlockBehaviour.Properties.of().strength(4, 6));
    }

    public static Item item(String id) {
        return ITEMS.get(id);
    }

    public static void init() {
        DolfinSpace.LOGGER.info("event=machines_registered recipes={}", MachineRecipes.ALL.size());
    }

    private MachinesModule() {}
}
