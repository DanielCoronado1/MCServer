package com.dolfin.space.oxygen;

import com.dolfin.space.*;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.gamerules.GameRules;

public final class OxygenModule {
    public static final Item SMALL =
            SpaceContent.registerItem(
                    "tanque_oxigeno_chico", p -> new TankItem(p, 0), new Item.Properties());
    public static final Item MEDIUM =
            SpaceContent.registerItem(
                    "tanque_oxigeno_mediano", p -> new TankItem(p, 1), new Item.Properties());
    public static final Item LARGE =
            SpaceContent.registerItem(
                    "tanque_oxigeno_grande", p -> new TankItem(p, 2), new Item.Properties());
    public static final CompressorBlock COMPRESSOR =
            SpaceContent.registerBlockWithItem(
                    "compresor_oxigeno",
                    CompressorBlock::new,
                    BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops());
    public static final BlockEntityType<CompressorEntity> COMPRESSOR_ENTITY =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    DolfinSpace.id("compresor_oxigeno"),
                    FabricBlockEntityTypeBuilder.create(CompressorEntity::new, COMPRESSOR).build());
    public static final MenuType<CompressorMenu> MENU =
            Registry.register(
                    BuiltInRegistries.MENU,
                    DolfinSpace.id("compresor_oxigeno"),
                    new ExtendedMenuType<>(
                            (id, inv, data) -> new CompressorMenu(id, inv), ByteBufCodecs.VAR_INT));
    public static final ResourceKey<DamageType> OXYGEN =
            ResourceKey.create(Registries.DAMAGE_TYPE, DolfinSpace.id("oxygen"));

    private OxygenModule() {}

    public static boolean inShip(Entity entity) {
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        return id.getNamespace().equals("dolfinspace")
                && (id.getPath().startsWith("nave")
                        || id.getPath().startsWith("spaceship")
                        || id.getPath().startsWith("ship_"));
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(OxygenPayload.TYPE, OxygenPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (server.getTickCount() % 20 != 0
                            || !DolfinSpace.config().current().general().activo()) return;
                    for (var player : server.getPlayerList().getPlayers()) {
                        if (!player.isAlive()) continue;
                        var container = ((TankAccess) player).dolfinspace$tank();
                        var tank = container.getItem(0);
                        TankItem.normalize(tank);
                        boolean consume =
                                OxygenRules.consumes(
                                        KeplerDimension.isKepler(player.level().dimension()),
                                        inShip(player),
                                        player.isCreative(),
                                        player.isSpectator());
                        int seconds = TankItem.remaining(tank);
                        DolfinSpace.LOGGER.debug(
                                "event=oxygen_pulse player={} consumes={} kepler={} ship={}"
                                        + " creative={} spectator={} remaining={}",
                                player.getUUID(),
                                consume,
                                KeplerDimension.isKepler(player.level().dimension()),
                                inShip(player),
                                player.isCreative(),
                                player.isSpectator(),
                                seconds);
                        if (consume) {
                            if (seconds > 0) {
                                TankItem.remaining(tank, OxygenRules.consume(seconds));
                                container.setChanged();
                            } else {
                                player.hurtServer(
                                        player.level(),
                                        new DamageSource(
                                                player.registryAccess()
                                                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                                                        .getOrThrow(OXYGEN)),
                                        (float)
                                                DolfinSpace.config()
                                                        .current()
                                                        .oxigeno()
                                                        .danioSinOxigenoPorSegundo());
                                DolfinSpace.LOGGER.debug(
                                        "event=oxygen_suffocation player={}", player.getUUID());
                            }
                        }
                        int capacity = TankItem.isTank(tank) ? tank.getMaxDamage() : 0;
                        int remaining = TankItem.remaining(tank);
                        ServerPlayNetworking.send(
                                player,
                                new OxygenPayload(
                                        remaining,
                                        capacity,
                                        OxygenRules.low(
                                                remaining,
                                                capacity,
                                                DolfinSpace.config()
                                                        .current()
                                                        .oxigeno()
                                                        .porcentajeAlertaOxigeno())));
                    }
                });
        ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, source) -> {
                    if (!(entity instanceof net.minecraft.server.level.ServerPlayer p)
                            || p.level().getGameRules().get(GameRules.KEEP_INVENTORY)) return;
                    var tank = ((TankAccess) p).dolfinspace$tank().removeItemNoUpdate(0);
                    if (!tank.isEmpty()) p.spawnAtLocation(p.level(), tank);
                    DolfinSpace.LOGGER.debug(
                            "event=tank_death_drop player={} tank={}", p.getUUID(), tank);
                });
        ServerPlayerEvents.COPY_FROM.register(
                (old, p, alive) -> {
                    ((TankAccess) p)
                            .dolfinspace$tank()
                            .setItem(0, ((TankAccess) old).dolfinspace$tank().getItem(0).copy());
                    DolfinSpace.LOGGER.debug(
                            "event=tank_respawn_copy player={} alive={}", p.getUUID(), alive);
                });
        DolfinSpace.LOGGER.info(
                "event=oxygen_registered tanks=3 compressor=true persistence=player_nbt");
    }
}
