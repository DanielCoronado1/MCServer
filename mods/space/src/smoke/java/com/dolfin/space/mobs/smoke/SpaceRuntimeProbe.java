package com.dolfin.space.mobs.smoke;

import com.dolfin.space.*;
import com.dolfin.space.mobs.*;
import com.dolfin.space.oxygen.*;
import com.mojang.authlib.GameProfile;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.*;

import java.util.UUID;

/** Only runs with an explicit property in the isolated space-smoke development world. */
public final class SpaceRuntimeProbe implements ModInitializer {
    private int checks;

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && Boolean.getBoolean("dolfinspace.smoke"))
            ServerLifecycleEvents.SERVER_STARTED.register(
                    server -> {
                        if (!server.getWorldData().getLevelName().equals("space-smoke"))
                            throw new IllegalStateException(
                                    "Runtime probes require isolated space-smoke world");
                        try {
                            run(server);
                        } catch (Throwable failure) {
                            DolfinSpace.LOGGER.error(
                                    "event=space_runtime_probe_failed checks={}", checks, failure);
                        }
                    });
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        DolfinSpace.LOGGER.info("event=space_runtime_probe_pass check={}", label);
    }

    private ServerPlayer player(MinecraftServer server, ServerLevel level) {
        var profile = new GameProfile(UUID.randomUUID(), "SpaceProbe");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection =
                new ServerGamePacketListenerImpl(
                        server,
                        new Connection(PacketFlow.SERVERBOUND),
                        player,
                        CommonListenerCookie.createInitial(profile, false));
        player.setPos(4, 90, 4);
        return player;
    }

    private void run(MinecraftServer server) {
        ServerLevel level = server.getLevel(KeplerDimension.LEVEL);
        level.getChunk(0, 0); // actual worldgen, not merely biome lookup
        var player = player(server, level);
        var tank = ((TankAccess) player).dolfinspace$tank();
        ItemStack oxygen = new ItemStack(OxygenModule.SMALL);
        TankItem.normalize(oxygen);
        check(
                tank.canPlaceItem(0, oxygen) && !tank.canPlaceItem(0, new ItemStack(Items.STONE)),
                "tank_container_filter");
        int tankSlot =
                player.inventoryMenu.slots.stream()
                        .filter(s -> s.container == tank)
                        .findFirst()
                        .orElseThrow()
                        .index;
        check(
                player.inventoryMenu.getSlot(tankSlot).getNoItemIcon() != null,
                "tank_menu_slot_and_icon");
        player.getInventory().setItem(9, oxygen.copy());
        player.inventoryMenu.quickMoveStack(player, 9);
        check(
                TankItem.isTank(tank.getItem(0)) && player.getInventory().getItem(9).isEmpty(),
                "tank_shift_click_equip");
        player.inventoryMenu.quickMoveStack(player, tankSlot);
        check(tank.isEmpty(), "tank_shift_click_unequip");
        player.inventoryMenu.setCarried(new ItemStack(Items.STONE));
        player.inventoryMenu.clicked(tankSlot, 0, ContainerInput.PICKUP, player);
        check(
                tank.isEmpty() && player.inventoryMenu.getCarried().is(Items.STONE),
                "tank_click_rejects_stone");
        player.inventoryMenu.clicked(-999, 0, ContainerInput.QUICK_CRAFT, player);
        player.inventoryMenu.clicked(tankSlot, 1, ContainerInput.QUICK_CRAFT, player);
        player.inventoryMenu.clicked(-999, 2, ContainerInput.QUICK_CRAFT, player);
        check(tank.isEmpty(), "tank_drag_rejects_stone");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        var rejected =
                HopperBlockEntity.addItem(null, tank, new ItemStack(Items.STONE), Direction.UP);
        check(rejected.is(Items.STONE) && tank.isEmpty(), "tank_automation_rejects_stone");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(
                        tankSlot, new ItemStack(Items.STONE)));
        check(tank.isEmpty(), "tank_creative_packet_rejects_stone");
        player.connection.handleSetCreativeModeSlot(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(
                        tankSlot, oxygen.copy()));
        check(TankItem.isTank(tank.getItem(0)), "tank_creative_packet_accepts_tank");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        TankItem.remaining(oxygen, 123);
        tank.setItem(0, oxygen.copy());
        var out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, level.registryAccess());
        player.saveWithoutId(out);
        var restored = player(server, level);
        restored.load(
                TagValueInput.create(
                        ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        check(
                TankItem.remaining(((TankAccess) restored).dolfinspace$tank().getItem(0)) == 123,
                "tank_player_nbt_roundtrip");
        var pos = new BlockPos(0, 90, 0);
        level.setBlockAndUpdate(pos, OxygenModule.COMPRESSOR.defaultBlockState());
        var compressor = (CompressorEntity) level.getBlockEntity(pos);
        var empty = oxygen.copy();
        TankItem.remaining(empty, 0);
        compressor.setItem(0, new ItemStack(Items.OAK_LEAVES));
        compressor.setItem(1, new ItemStack(Items.COAL));
        compressor.setItem(2, empty);
        new CompressorMenu(7, player.getInventory(), compressor);
        for (int i = 0; i < DolfinSpace.config().current().maquinas().ticksCompresorOxigeno(); i++)
            compressor.tick();
        check(
                TankItem.remaining(compressor.getItem(2)) == 60
                        && compressor.getItem(0).isEmpty()
                        && compressor.getItem(1).isEmpty(),
                "compressor_real_refill_and_menu");
        var placed = new BlockPos(1, 90, 1);
        level.setBlockAndUpdate(placed, SpaceContent.REGOLITO_BLOCK.defaultBlockState());
        check(!TerrainHistory.natural(level, placed), "worm_rejects_post_generation_regolith");
        var parasite = new Parasite(MobsModule.PARASITE, level);
        check(parasite.startRiding(player, true, true), "parasite_can_ride_player_26_3");
        player.positionRider(parasite);
        check(
                Math.abs(parasite.getY() - (player.getEyeY() + .05)) < .01,
                "parasite_head_attachment_position");
        for (int i = 0; i < 120; i++) {
            player.setInvulnerableTime(0);
            parasite.tick();
        }
        check(
                !parasite.isPassenger() && player.hasEffect(MobEffects.LEVITATION),
                "parasite_third_attack_levitation_detach");
        check(player.getHealth() == 17, "parasite_three_one_damage_attacks");
        var sneaky = new Parasite(MobsModule.PARASITE, level);
        sneaky.startRiding(player, true, true);
        for (int i = 0; i < 3; i++) {
            player.setShiftKeyDown(true);
            sneaky.tick();
            player.setShiftKeyDown(false);
            sneaky.tick();
        }
        check(!sneaky.isPassenger(), "parasite_three_sneaks_detach");
        var hit = new Parasite(MobsModule.PARASITE, level);
        hit.startRiding(player, true, true);
        hit.hurtServer(level, player.damageSources().playerAttack(player), 1);
        check(!hit.isPassenger(), "parasite_player_hit_detach");
        for (var type :
                java.util.List.of(
                        MobsModule.STALKER,
                        MobsModule.WORM,
                        MobsModule.SENTINEL,
                        MobsModule.ZOMBIE,
                        MobsModule.SKELETON,
                        MobsModule.CREEPER,
                        MobsModule.PROJECTILE,
                        MobsModule.CRYSTAL_ARROW,
                        MobsModule.ACID_CLOUD))
            check(
                    type.create(level, EntitySpawnReason.COMMAND) != null,
                    "construct_"
                            + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                    .getKey(type)
                                    .getPath());
        player.remove(Entity.RemovalReason.DISCARDED);
        restored.remove(Entity.RemovalReason.DISCARDED);
        DolfinSpace.LOGGER.info("event=space_runtime_probe_complete checks={}", checks);
    }
}
