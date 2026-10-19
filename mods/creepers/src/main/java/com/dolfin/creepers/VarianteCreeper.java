package com.dolfin.creepers;

import com.dolfin.creepers.mixin.CreeperAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Un creeper con comportamiento especial segun su variante. La explosion la decide explotar() (la
 * llama CreeperExplosionMixin).
 */
public class VarianteCreeper extends Creeper {
    private final Variante variante;

    public VarianteCreeper(EntityType<? extends Creeper> tipo, Level mundo, Variante variante) {
        super(tipo, mundo);
        this.variante = variante;
        ((CreeperAccessor) this).dolfin$setMecha(variante.mecha);
        if (variante == Variante.ELECTRICO) {
            // El aura azul del creeper cargado (como cuando le cae un rayo).
            getEntityData().set(CreeperAccessor.dolfin$datoCargado(), true);
        }
    }

    public Variante variante() {
        return variante;
    }

    // =====================================================================
    //  Explosiones
    // =====================================================================

    /** Sustituye a la explosion normal del creeper. */
    public void explotar() {
        if (!(level() instanceof ServerLevel mundo)) {
            return;
        }
        Vec3 centro = position();
        Level.ExplosionInteraction tipo =
                variante.rompeBloques
                        ? Level.ExplosionInteraction.MOB // respeta la regla mobGriefing
                        : Level.ExplosionInteraction.NONE; // dana pero no rompe bloques

        dead = true;
        mundo.explode(
                this,
                centro.x,
                centro.y,
                centro.z,
                variante.radio,
                variante == Variante.FUEGO,
                tipo);
        efectoEspecial(mundo, centro);
        discard();
    }

    /** Lo que hace cada variante ademas de explotar. */
    protected void efectoEspecial(ServerLevel mundo, Vec3 centro) {
        switch (variante) {
            case FUEGO -> {
                for (Player jugador : jugadoresCerca(mundo, centro, 5)) {
                    jugador.igniteForSeconds(5);
                }
                mundo.sendParticles(
                        ParticleTypes.FLAME, centro.x, centro.y + 1, centro.z, 120, 2, 1, 2, 0.08);
            }
            case HIELO -> congelar(mundo, centro);
            case SALTARIN -> lanzarPorLosAires(mundo, centro);
            case ELECTRICO -> rayosEnCadena(mundo, centro);
            case MULTIPLICADOR -> soltarMinis(mundo, centro, 3);
            case TOXICO -> nubeToxica(mundo, centro);
            default -> {}
        }
    }

    private void congelar(ServerLevel mundo, Vec3 centro) {
        mundo.sendParticles(
                ParticleTypes.SNOWFLAKE,
                centro.x,
                centro.y + 1,
                centro.z,
                300,
                2.5,
                1.5,
                2.5,
                0.05);
        mundo.playSound(
                null,
                centro.x,
                centro.y,
                centro.z,
                SoundEvents.GLASS_BREAK,
                SoundSource.HOSTILE,
                2f,
                0.6f);
        for (Player jugador : jugadoresCerca(mundo, centro, 6)) {
            jugador.setTicksFrozen(Math.max(jugador.getTicksFrozen(), 300));
            jugador.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
            jugador.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 1));
        }
        // El agua quieta cercana se convierte en hielo escarchado (se derrite solo).
        BlockPos origen = BlockPos.containing(centro);
        for (BlockPos pos :
                BlockPos.betweenClosed(origen.offset(-5, -2, -5), origen.offset(5, 2, 5))) {
            if (pos.distSqr(origen) <= 25
                    && mundo.getFluidState(pos).is(FluidTags.WATER)
                    && mundo.getFluidState(pos).isSource()
                    && mundo.getBlockState(pos.above()).isAir()) {
                mundo.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
            }
        }
    }

    /**
     * Velocidad hacia arriba para subir unos 30 bloques (con la gravedad y el rozamiento del aire).
     */
    private static final double IMPULSO_30_BLOQUES = 2.54;

    /** Etiqueta para no recibir dano de la caida de este lanzamiento (ver CaidaMixin). */
    public static final String ETIQUETA_LANZADO = "dolfincreepers.lanzado";

    /** Lanza a todos los que esten cerca unos 30 bloques hacia arriba. */
    private void lanzarPorLosAires(ServerLevel mundo, Vec3 centro) {
        List<LivingEntity> cercanos =
                mundo.getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(4),
                        objetivo ->
                                !(objetivo instanceof Creeper)
                                        && objetivo.isAlive()
                                        && !objetivo.isSpectator());
        for (LivingEntity objetivo : cercanos) {
            Vec3 empuje = objetivo.getDeltaMovement();
            objetivo.setDeltaMovement(empuje.x * 0.3, IMPULSO_30_BLOQUES, empuje.z * 0.3);
            objetivo.needsSync = true;
            if (objetivo instanceof ServerPlayer jugador) {
                // El movimiento del jugador lo calcula su propio juego: hay que mandarle la
                // velocidad.
                jugador.connection.send(new ClientboundSetEntityMotionPacket(jugador));
            }
            objetivo.addTag(ETIQUETA_LANZADO);
            mundo.sendParticles(
                    ParticleTypes.CLOUD,
                    objetivo.getX(),
                    objetivo.getY(),
                    objetivo.getZ(),
                    30,
                    0.4,
                    0.2,
                    0.4,
                    0.1);
        }
        mundo.playSound(
                null,
                centro.x,
                centro.y,
                centro.z,
                SoundEvents.SLIME_JUMP,
                SoundSource.HOSTILE,
                3f,
                0.5f);
    }

    private void rayosEnCadena(ServerLevel mundo, Vec3 centro) {
        List<LivingEntity> objetivos =
                mundo.getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(10),
                        objetivo -> !(objetivo instanceof Creeper) && objetivo.isAlive());
        objetivos.stream()
                .sorted(Comparator.comparingDouble(objetivo -> objetivo.distanceToSqr(centro)))
                .limit(3)
                .forEach(
                        objetivo -> {
                            LightningBolt rayo =
                                    EntityTypes.LIGHTNING_BOLT.create(
                                            mundo, EntitySpawnReason.TRIGGERED);
                            if (rayo != null) {
                                rayo.snapTo(objetivo.getX(), objetivo.getY(), objetivo.getZ());
                                rayo.setVisualOnly(true); // solo se ve; el dano lo ponemos nosotros
                                mundo.addFreshEntity(rayo);
                            }
                            objetivo.hurtServer(mundo, mundo.damageSources().lightningBolt(), 5);
                        });
        mundo.sendParticles(
                ParticleTypes.ELECTRIC_SPARK, centro.x, centro.y + 1, centro.z, 150, 2, 2, 2, 0.3);
    }

    /** Suelta minis que salen disparados en distintas direcciones. */
    protected void soltarMinis(ServerLevel mundo, Vec3 centro, int cantidad) {
        mundo.playSound(
                null,
                centro.x,
                centro.y,
                centro.z,
                SoundEvents.SLIME_SQUISH,
                SoundSource.HOSTILE,
                2f,
                0.6f);
        for (int i = 0; i < cantidad; i++) {
            VarianteCreeper mini =
                    DolfinCreepers.tipo(Variante.MINI)
                            .create(mundo, EntitySpawnReason.MOB_SUMMONED);
            if (mini == null) {
                continue;
            }
            double angulo = 2 * Math.PI * i / cantidad;
            mini.snapTo(centro.x, centro.y + 0.5, centro.z);
            mini.setDeltaMovement(Math.cos(angulo) * 0.5, 0.45, Math.sin(angulo) * 0.5);
            mundo.addFreshEntity(mini);
        }
    }

    private void nubeToxica(ServerLevel mundo, Vec3 centro) {
        AreaEffectCloud nube = new AreaEffectCloud(mundo, centro.x, centro.y, centro.z);
        nube.setRadius(3.5f);
        nube.setRadiusPerTick(-0.005f);
        nube.setDuration(200);
        nube.setWaitTime(0);
        nube.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        nube.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
        nube.setCustomParticle(new DustParticleOptions(0x7CFF4F, 1.2f));
        mundo.addFreshEntity(nube);
        mundo.playSound(
                null,
                centro.x,
                centro.y,
                centro.z,
                SoundEvents.BREWING_STAND_BREW,
                SoundSource.HOSTILE,
                2f,
                0.5f);
    }

    protected List<Player> jugadoresCerca(ServerLevel mundo, Vec3 centro, double radio) {
        return mundo.getEntitiesOfClass(
                Player.class,
                getBoundingBox().inflate(radio),
                jugador ->
                        jugador.distanceToSqr(centro) <= radio * radio && !jugador.isSpectator());
    }

    // =====================================================================
    //  Cada tick: auras y saltos
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel mundo && isAlive()) {
            if (tickCount % 10 == 0) {
                aura(mundo);
            }
            if (variante == Variante.SALTARIN && tickCount % 10 == 0) {
                saltar(mundo);
            }
        }
    }

    private void aura(ServerLevel mundo) {
        ParticleOptions particula =
                switch (variante) {
                    case FUEGO -> ParticleTypes.FLAME;
                    case HIELO -> ParticleTypes.SNOWFLAKE;
                    case ELECTRICO -> ParticleTypes.ELECTRIC_SPARK;
                    case MULTIPLICADOR, MINI -> ParticleTypes.ITEM_SLIME;
                    case TOXICO -> new DustParticleOptions(0x7CFF4F, 1f);
                    default -> null;
                };
        if (particula != null) {
            mundo.sendParticles(
                    particula,
                    getX(),
                    getY() + getBbHeight() / 2,
                    getZ(),
                    3,
                    getBbWidth() / 2,
                    getBbHeight() / 3,
                    getBbWidth() / 2,
                    0.01);
        }
    }

    /** Si tiene a un jugador a 3-10 bloques y esta en el suelo, salta hacia el. */
    private void saltar(ServerLevel mundo) {
        LivingEntity objetivo = getTarget();
        if (objetivo == null || !onGround() || random.nextInt(3) != 0) {
            return;
        }
        Vec3 hacia = objetivo.position().subtract(position());
        double distancia = hacia.length();
        if (distancia < 3 || distancia > 10) {
            return;
        }
        Vec3 salto = hacia.normalize().scale(0.8);
        setDeltaMovement(salto.x, 0.55, salto.z);
        mundo.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.3, 0.1, 0.3, 0.02);
        mundo.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.SLIME_JUMP,
                SoundSource.HOSTILE,
                1f,
                1.4f);
    }

    // Al cargar el mundo, el juego lee "powered" del guardado: el electrico siempre vuelve cargado.
    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput datos) {
        super.readAdditionalSaveData(datos);
        if (variante == Variante.ELECTRICO) {
            getEntityData().set(CreeperAccessor.dolfin$datoCargado(), true);
        }
        ((CreeperAccessor) this).dolfin$setMecha(variante.mecha);
    }

    // El de hielo no se congela en la nieve polvo.
    @Override
    public boolean canFreeze() {
        return variante != Variante.HIELO && super.canFreeze();
    }
}
