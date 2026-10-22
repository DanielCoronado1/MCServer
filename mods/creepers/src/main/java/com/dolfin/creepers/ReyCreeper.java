package com.dolfin.creepers;

import com.dolfin.creepers.mixin.CreeperAccessor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Jefe: el Rey Creeper. No muere al "explotar": lanza una onda expansiva y vuelve a cargar.
 *
 * <p>Fase 1 (mas del 66 % de vida): invoca 2 minis cada 10 s Fase 2 (66 % - 33 %): invoca 2
 * variantes al azar cada 8 s Fase 3 (menos del 33 %): enfurecido: mas rapido e invoca 3 minis cada
 * 6 s
 */
public class ReyCreeper extends VarianteCreeper {
    private static final Variante[] ESCOLTA = {
        Variante.FUEGO, Variante.HIELO, Variante.SALTARIN, Variante.TOXICO
    };

    private final ServerBossEvent barra =
            new ServerBossEvent(
                    getUUID(),
                    Component.literal("Rey Creeper")
                            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.NOTCHED_10);
    private int fase = 1;

    public ReyCreeper(EntityType<? extends Creeper> tipo, Level mundo) {
        super(tipo, mundo, Variante.REY);
        setPersistenceRequired(); // no desaparece aunque nadie este cerca
        xpReward = 100;
    }

    /** La onda expansiva: dana alrededor, no rompe bloques y el rey sigue vivo. */
    @Override
    public void explotar() {
        if (isRemoved() || !isAlive() || !(level() instanceof ServerLevel mundo)) {
            return;
        }
        if (mundo.players().isEmpty()) {
            ((CreeperAccessor) this).dolfin$setHinchado(0);
            setSwellDir(-1);
            return;
        }
        Vec3 centro = position();
        mundo.explode(
                this,
                centro.x,
                centro.y,
                centro.z,
                variante().radio,
                Level.ExplosionInteraction.NONE);
        mundo.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER, centro.x, centro.y + 1, centro.z, 1, 0, 0, 0, 0);
        // Vuelve a empezar la cuenta atras en lugar de morir.
        ((CreeperAccessor) this).dolfin$setHinchado(0);
        setSwellDir(-1);
    }

    /** Sus propias explosiones no le hacen dano. */
    @Override
    public boolean hurtServer(ServerLevel mundo, DamageSource origen, float cantidad) {
        if (origen.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return super.hurtServer(mundo, origen, cantidad);
    }

    @Override
    public void tick() {
        if (isRemoved() || !isAlive()) {
            return;
        }
        if (level() instanceof ServerLevel mundo && mundo.players().isEmpty()) {
            setTarget(null);
            setSwellDir(-1);
            ((CreeperAccessor) this).dolfin$setHinchado(0);
            return;
        }
        super.tick();
        if (!(level() instanceof ServerLevel mundo) || !isAlive()) {
            return;
        }
        barra.setProgress(getHealth() / getMaxHealth());

        float vida = getHealth() / getMaxHealth();
        int nuevaFase = vida > 0.66f ? 1 : vida > 0.33f ? 2 : 3;
        if (nuevaFase != fase) {
            cambiarFase(mundo, nuevaFase);
        }

        int cada =
                switch (fase) {
                    case 1 -> 200;
                    case 2 -> 160;
                    default -> 120;
                };
        if (getTarget() != null && tickCount % cada == 0) {
            invocar(mundo);
        }
        if (fase == 3 && tickCount % 5 == 0) {
            mundo.sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    getX(),
                    getY() + getBbHeight(),
                    getZ(),
                    1,
                    1,
                    0.3,
                    1,
                    0);
        }
    }

    private void cambiarFase(ServerLevel mundo, int nuevaFase) {
        fase = nuevaFase;
        String mensaje =
                fase == 2 ? "¡El Rey Creeper llama a su escolta!" : "¡El Rey Creeper esta furioso!";
        for (Player jugador : jugadoresCerca(mundo, position(), 48)) {
            jugador.sendSystemMessage(
                    Component.literal(mensaje).withStyle(ChatFormatting.DARK_GREEN));
        }
        mundo.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE,
                2f,
                1.5f);
        if (fase == 3) {
            var velocidad = getAttribute(Attributes.MOVEMENT_SPEED);
            if (velocidad != null) {
                velocidad.addOrReplacePermanentModifier(
                        new AttributeModifier(
                                DolfinCreepers.id("furia"),
                                0.4,
                                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            barra.setColor(BossEvent.BossBarColor.RED);
        }
    }

    private void invocar(ServerLevel mundo) {
        int cantidad = fase == 3 ? 3 : 2;
        for (int i = 0; i < cantidad; i++) {
            Variante variante = fase == 2 ? ESCOLTA[random.nextInt(ESCOLTA.length)] : Variante.MINI;
            VarianteCreeper esbirro =
                    DolfinCreepers.tipo(variante).create(mundo, EntitySpawnReason.MOB_SUMMONED);
            if (esbirro == null) {
                continue;
            }
            double angulo = random.nextDouble() * 2 * Math.PI;
            esbirro.snapTo(getX() + Math.cos(angulo) * 3, getY(), getZ() + Math.sin(angulo) * 3);
            esbirro.setTarget(getTarget());
            mundo.addFreshEntity(esbirro);
            mundo.sendParticles(
                    ParticleTypes.POOF,
                    esbirro.getX(),
                    esbirro.getY() + 0.5,
                    esbirro.getZ(),
                    10,
                    0.3,
                    0.3,
                    0.3,
                    0.02);
        }
        mundo.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE,
                2f,
                0.7f);
    }

    // ---- Barra de vida: la ven los jugadores que tienen al rey a la vista ----

    @Override
    public void startSeenByPlayer(ServerPlayer jugador) {
        super.startSeenByPlayer(jugador);
        barra.addPlayer(jugador);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer jugador) {
        super.stopSeenByPlayer(jugador);
        barra.removePlayer(jugador);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput datos) {
        super.readAdditionalSaveData(datos);
        barra.setName(getDisplayName());
    }

    @Override
    public void remove(RemovalReason razon) {
        barra.removeAllPlayers();
        super.remove(razon);
    }
}
