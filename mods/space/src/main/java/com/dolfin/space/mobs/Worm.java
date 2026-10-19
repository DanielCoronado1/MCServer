package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class Worm extends Silverfish {
    private boolean burrowed;
    private int phaseTicks;
    public Worm(EntityType<? extends Worm> type, Level level) {
        super(type,level);var c=DolfinSpace.config().current().mobs();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(c.gusanoVida());getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(c.gusanoDanio());setHealth(getMaxHealth());
    }
    @Override protected void registerGoals() { goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(4,new RandomStrollGoal(this,.8)); }
    private void state(boolean underground, String reason) {
        burrowed=underground;phaseTicks=0;noPhysics=underground;setNoGravity(underground);setInvisible(underground);getNavigation().stop();
        DolfinSpace.LOGGER.debug("event=worm_state entity={} burrowed={} reason={}",getUUID(),burrowed,reason);
    }
    private boolean safeColumn(ServerLevel level, BlockPos ground) {
        return TerrainHistory.natural(level,ground) && level.isEmptyBlock(ground.above()) && level.isEmptyBlock(ground.above(2));
    }
    private boolean safeFootprint(ServerLevel level, double x, int y, double z) {
        double radius=getBbWidth()*.5;
        for (BlockPos floor:BlockPos.betweenClosed(BlockPos.containing(x-radius,y,z-radius),BlockPos.containing(x+radius,y,z+radius)))
            if (!safeColumn(level,floor)) return false;
        return true;
    }
    @Override public void travel(Vec3 movement) {
        if (burrowed) { setDeltaMovement(Vec3.ZERO); return; }
        super.travel(movement);
    }
    @Override public void tick() {
        super.tick();if (!(level() instanceof ServerLevel server)) return;
        Player target=MobTargets.nearest(this,24);phaseTicks++;
        if (!burrowed) {
            if (target!=null) getNavigation().moveTo(target,1.1);
            BlockPos ground=blockPosition().below();
            if (phaseTicks>=60 && target!=null && safeFootprint(server,getX(),ground.getY(),getZ())) {
                setPos(getX(),ground.getY()+.15,getZ());state(true,"natural_ground");
            }
            return;
        }
        BlockPos current=blockPosition();
        if (!TerrainHistory.natural(server,current)) {
            // A placed block can trap a worm, but never grants permission to tunnel through it.
            setDeltaMovement(Vec3.ZERO);
            if (tickCount%20==0) DolfinSpace.LOGGER.debug("event=worm_burrow_rejected entity={} reason=placed_or_foreign_terrain",getUUID());
            return;
        }
        if (tickCount%4==0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,server.getBlockState(current)),getX(),current.getY()+1.05,getZ(),3,.2,.05,.2,.01);
        if (target!=null) {
            Vec3 step=target.position().subtract(position()).multiply(1,0,1).normalize().scale(.16);
            BlockPos next=BlockPos.containing(getX()+step.x,getY(),getZ()+step.z);
            // One block of slope at most; inspecting every crossed column prevents tunneling through constructions.
            BlockPos ground=next;
            if (!safeColumn(server,ground)) ground=safeColumn(server,next.above())?next.above():next.below();
            if (safeFootprint(server,getX()+step.x,ground.getY(),getZ()+step.z)) setPos(getX()+step.x,ground.getY()+.15,getZ()+step.z);
            else if (tickCount%20==0) DolfinSpace.LOGGER.debug("event=worm_burrow_rejected entity={} pos={} reason=unnatural_or_obstructed",getUUID(),ground);
        }
        if (phaseTicks>=40 && (target==null || distanceToSqr(target)<6.25 || phaseTicks>=160) && safeFootprint(server,getX(),getBlockY(),getZ())) {
            setPos(getX(),blockPosition().getY()+1.01,getZ());state(false,"emerge");
            if (target!=null && distanceToSqr(target)<6.25 && hasLineOfSight(target)) {
                target.hurtServer(server,damageSources().mobAttack(this),(float)DolfinSpace.config().current().mobs().gusanoDanio());
                DolfinSpace.LOGGER.debug("event=worm_bite entity={} victim={}",getUUID(),target.getUUID());
            }
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out);out.putBoolean("SpaceBurrowed",burrowed);out.putInt("SpacePhase",phaseTicks);DolfinSpace.LOGGER.debug("event=worm_saved entity={} burrowed={}",getUUID(),burrowed); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in);state(in.getBooleanOr("SpaceBurrowed",false),"loaded");phaseTicks=Math.clamp(in.getIntOr("SpacePhase",0),0,160); }
}
