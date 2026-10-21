package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;

/**
 * Registros de las plantillas: el {@link MarcadorMob} (bloque, objeto y entidad de bloque) y el
 * ticket de chunks de {@link ZonaCargada}.
 */
public final class ContenidoPlantillas {
    private static final String MARCADOR = "marcador_mob";

    /** Irrompible fuera de creativo, sin colisión ni botín, y no lo mueven los pistones. */
    public static final Block MARCADOR_MOB =
            Registry.register(
                    BuiltInRegistries.BLOCK,
                    clave(Registries.BLOCK),
                    new MarcadorMob(
                            BlockBehaviour.Properties.of()
                                    .setId(clave(Registries.BLOCK))
                                    .strength(-1.0f, 3_600_000.0f)
                                    .noCollision()
                                    .noOcclusion()
                                    .noLootTable()
                                    .noTerrainParticles()
                                    .pushReaction(PushReaction.IMMOVEABLE)));

    public static final Item MARCADOR_MOB_OBJETO =
            Registry.register(
                    BuiltInRegistries.ITEM,
                    clave(Registries.ITEM),
                    new BlockItem(
                            MARCADOR_MOB,
                            new Item.Properties()
                                    .setId(clave(Registries.ITEM))
                                    .useBlockDescriptionPrefix()));

    public static final BlockEntityType<MarcadorMobEntidad> MARCADOR_MOB_ENTIDAD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    DolfinCreepers.id(MARCADOR),
                    new BlockEntityType<>(MarcadorMobEntidad::new, Set.of(MARCADOR_MOB)));

    /**
     * Ticket que carga una zona mientras se guarda o reinicia, sin simularla. Propio, distinto del
     * de la partida ({@code CargaChunks}), para no pisarse.
     */
    public static final TicketType TICKET_ZONA =
            Registry.register(
                    BuiltInRegistries.TICKET_TYPE,
                    DolfinCreepers.id("dungeon_plantilla"),
                    new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

    private ContenidoPlantillas() {}

    /** Fuerza el registro durante la inicialización del mod. */
    public static void registrar() {
        DolfinCreepers.LOGGER.debug(
                "event=dungeon_template_content_registered marker={} ticket={}",
                MARCADOR_MOB,
                TICKET_ZONA);
    }

    private static <T> ResourceKey<T> clave(ResourceKey<? extends Registry<T>> registro) {
        return ResourceKey.create(registro, DolfinCreepers.id(MARCADOR));
    }
}
