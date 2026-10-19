package com.dolfin.space.ships;

import com.dolfin.space.*;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.function.Consumer;

public final class ShipItem extends Item {
    private final ShipTier tier;

    public ShipItem(Properties p, ShipTier tier) {
        super(p);
        this.tier = tier;
    }

    private ShipState stored(ItemStack s) {
        return ShipState.CODEC
                .parse(
                        NbtOps.INSTANCE,
                        s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())
                .result()
                .orElse(new ShipState("", tier.ordinal(), 0, 0, false, false));
    }

    public InteractionResult useOn(UseOnContext c) {
        var level = c.getLevel();
        var player = c.getPlayer();
        if (player == null || c.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
        var pos = c.getClickedPos();
        boolean earth = level.dimension().equals(Level.OVERWORLD);
        boolean kepler = KeplerDimension.isKepler(level.dimension());
        boolean ground =
                level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
                        && level.getFluidState(pos).isEmpty();
        var design = ShipDesign.of(tier);
        double half = design.width() / 2;
        var box =
                new AABB(
                        pos.getX() + .5 - half,
                        pos.getY() + 1,
                        pos.getZ() + .5 - half,
                        pos.getX() + .5 + half,
                        pos.getY() + 1 + design.height(),
                        pos.getZ() + .5 + half);
        if ((!earth && !kepler)
                || !(earth ? PlatformRules.valid(level, pos, tier) : ground)
                || !level.noCollision(box)
                || !level.getEntitiesOfClass(SpaceshipEntity.class, box).isEmpty()) {
            DolfinSpace.LOGGER.debug(
                    "event=ship_placement_rejected player={} pos={} reason=platform_or_clearance",
                    player.getUUID(),
                    pos);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (!com.dolfin.space.compat.SpaceClaims.shipPlacement(
                    player, (net.minecraft.server.level.ServerLevel) level, pos, tier))
                return InteractionResult.FAIL;
            var ship = new SpaceshipEntity(ShipsModule.SHIP, level);
            var saved = stored(c.getItemInHand());
            ship.apply(saved);
            ship.tier(tier);
            if (ship.owner == null) ship.owner = player.getUUID();
            if (!ShipAccess.allowed(ship.owner, player)) return InteractionResult.FAIL;
            if (earth) {
                if (ship.home == null) ship.home = pos.immutable();
                level.setBlock(
                        pos, level.getBlockState(pos).setValue(PlatformBlock.CENTER, true), 3);
            }
            c.getItemInHand()
                    .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                    .copyInto(ship.cargo.getItems());
            ship.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            level.addFreshEntity(ship);
            c.getItemInHand().shrink(1);
            DolfinSpace.LOGGER.info(
                    "event=ship_placed ship={} owner={} tier={} fuel={}",
                    ship.getUUID(),
                    ship.owner,
                    tier,
                    ship.fuel());
        }
        return InteractionResult.SUCCESS;
    }

    public void appendHoverText(
            ItemStack s,
            TooltipContext c,
            TooltipDisplay d,
            Consumer<Component> out,
            TooltipFlag f) {
        out.accept(Component.translatable("space.ui.fuel_tip", stored(s).fuel()));
        out.accept(Component.translatable("space.ui.ship_help"));
    }
}
