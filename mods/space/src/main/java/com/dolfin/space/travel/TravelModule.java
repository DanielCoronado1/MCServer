package com.dolfin.space.travel;

import com.dolfin.space.*;
import com.dolfin.space.ships.*;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.menu.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.*;

public final class TravelModule {
    public static final MenuType<CockpitMenu> MENU =
            Registry.register(
                    BuiltInRegistries.MENU,
                    DolfinSpace.id("cabina"),
                    new ExtendedMenuType<>(CockpitMenu::new, ByteBufCodecs.INT));

    public static LaunchRules.Checklist check(SpaceshipEntity ship, ServerPlayer pilot) {
        boolean earth = FlightController.launchWorld(ship.level()),
                kepler = KeplerDimension.isKepler(ship.level().dimension());
        var floor = ship.blockPosition().below();
        boolean surface =
                earth
                        ? PlatformRules.valid(ship.level(), floor, ship.tier())
                        : kepler
                                && ship.level()
                                        .getBlockState(floor)
                                        .isFaceSturdy(ship.level(), floor, Direction.UP)
                                && ship.level().getFluidState(floor).isEmpty();
        if (surface)
            surface =
                    SafeLanding.surface(
                            (net.minecraft.server.level.ServerLevel) ship.level(),
                            ship.blockPosition());
        // A clear ascent column prevents launching through buildings or overhangs.
        if (surface) {
            int top =
                    Math.min(
                            ship.level().getMaxY() - 1,
                            floor.getY()
                                    + DolfinSpace.config().current().naves().alturaAscensoDespegue()
                                    + 4);
            for (int y = floor.getY() + 1; y <= top && surface; y++)
                for (int x = -ship.tier().radius(); x <= ship.tier().radius() && surface; x++)
                    for (int z = -ship.tier().radius(); z <= ship.tier().radius(); z++)
                        if (!ship.level()
                                .getBlockState(new BlockPos(floor.getX() + x, y, floor.getZ() + z))
                                .isAir()) {
                            surface = false;
                            break;
                        }
        }
        return LaunchRules.check(
                earth,
                surface,
                ship.fuel(),
                ship.getFirstPassenger() == pilot && ShipAccess.allowed(ship.owner, pilot),
                DolfinSpace.config().current().general().activo(),
                OxygenStatusBridge.status(pilot).equipped(),
                DolfinSpace.config().current().naves());
    }

    public static void open(ServerPlayer p) {
        if (p.getVehicle() instanceof SpaceshipEntity ship
                && ship.getFirstPassenger() == p
                && ShipAccess.allowed(ship.owner, p))
            p.openMenu(
                    new ExtendedMenuProvider<Integer>() {
                        public Integer getScreenOpeningData(ServerPlayer who) {
                            return ship.getId();
                        }

                        public Component getDisplayName() {
                            return Component.translatable("space.ui.cockpit");
                        }

                        public AbstractContainerMenu createMenu(
                                int id,
                                net.minecraft.world.entity.player.Inventory inv,
                                net.minecraft.world.entity.player.Player who) {
                            return new CockpitMenu(id, inv, ship, true);
                        }
                    });
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay()
                .register(CameraShakePayload.TYPE, CameraShakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(FlightPhasePayload.TYPE, FlightPhasePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(CockpitActionPayload.TYPE, CockpitActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                CockpitActionPayload.TYPE,
                (payload, ctx) ->
                        ctx.server()
                                .execute(
                                        () -> {
                                            if (payload.action() == 0) open(ctx.player());
                                            else if (payload.action() == 2)
                                                FlightController.dismount(ctx.player());
                                        }));
        ShipsModule.onPilotMounted = TravelModule::open;
        ShipsModule.flightTick = FlightController::safeTick;
        ServerTickEvents.END_SERVER_TICK.register(FlightController::recover);
        ServerEntityEvents.ENTITY_LOAD.register(
                (entity, level) -> {
                    if (entity instanceof SpaceshipEntity ship) {
                        FlightLedger.get(level.getServer()).track(ship);
                        FlightController.loaded(ship);
                    }
                });
        ServerEntityEvents.ENTITY_UNLOAD.register(
                (entity, level) -> {
                    if (entity instanceof SpaceshipEntity ship
                            && (entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
                                    || entity.getRemovalReason() == Entity.RemovalReason.KILLED))
                        FlightLedger.get(level.getServer()).remove(ship);
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> FlightController.disconnected(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (old, p, alive) -> {
                    if (!alive
                            && FlightJournal.autoReturn(
                                    KeplerDimension.isKepler(old.level().dimension()),
                                    !KeplerDimension.isKepler(p.level().dimension()),
                                    DolfinSpace.config()
                                            .current()
                                            .naves()
                                            .retornoAutomaticoAlReaparecerFuera()))
                        FlightController.ownerRespawned(p);
                });
        DolfinSpace.LOGGER.info("event=travel_registered");
    }

    private TravelModule() {}
}
