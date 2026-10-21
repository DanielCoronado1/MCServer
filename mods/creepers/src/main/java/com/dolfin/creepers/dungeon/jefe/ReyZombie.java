package com.dolfin.creepers.dungeon.jefe;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.fin.JefeDungeon;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Jefe de la primera dungeon: el Rey Zombie (#20).
 *
 * <p>Fase 1 (100%–50% de vida): combate cuerpo a cuerpo e invoca 3 zombis cada 15 segundos.
 *
 * <p>Fase 2 (&lt;50% de vida): furia con mayor velocidad, golpe en área que empuja e invoca
 * esbirros con armadura de hierro.
 *
 * <p>Está atado a su sala: no puede salir de la dimensión ni alejarse de su origen. Al morir suelta
 * el botín escalado por dificultad y activa la victoria.
 */
public class ReyZombie extends Zombie {
    private static final Identifier MODIFIER_FURIA =
            Identifier.fromNamespaceAndPath("dolfincreepers", "furia_rey_zombie");
    private static final int INTERVALO_INVOCACION_TICKS = 300; // 15 segundos
    private static final int INTERVALO_GOLPE_AREA_TICKS = 160; // 8 segundos
    private static final double DISTANCIA_MAXIMA_SALA = 26.0;

    private final ServerBossEvent barra =
            new ServerBossEvent(
                    getUUID(),
                    Component.literal("Rey Zombie")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.NOTCHED_10);

    private int fase = 1;
    private BlockPos posicionSpawn;

    public ReyZombie(EntityType<? extends Zombie> tipo, Level mundo) {
        super(tipo, mundo);
        setPersistenceRequired();
        xpReward = 150;
        JefeDungeon.marcar(this);
    }

    public static AttributeSupplier.Builder crearAtributos() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel mundo) || !isAlive()) {
            return;
        }

        // Seguridad: nunca existe fuera de la dimensión de dungeons
        if (!DimensionDungeons.es(mundo)) {
            discard();
            return;
        }

        // Ancla de spawn para evitar que sea arrastrado fuera de la sala del jefe
        if (posicionSpawn == null) {
            posicionSpawn = blockPosition();
        } else if (position().distanceToSqr(Vec3.atCenterOf(posicionSpawn))
                > DISTANCIA_MAXIMA_SALA * DISTANCIA_MAXIMA_SALA) {
            teleportTo(
                    posicionSpawn.getX() + 0.5, posicionSpawn.getY(), posicionSpawn.getZ() + 0.5);
            setDeltaMovement(Vec3.ZERO);
            mundo.sendParticles(
                    ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        }

        barra.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));

        // Transición de fases
        float porcentajeVida = getHealth() / getMaxHealth();
        int nuevaFase = porcentajeVida < 0.5f ? 2 : 1;
        if (nuevaFase != fase) {
            cambiarFase(mundo, nuevaFase);
        }

        // Invocación periódica si está en combate
        if (getTarget() != null && tickCount % INTERVALO_INVOCACION_TICKS == 0) {
            invocarEsbirros(mundo);
        }

        // Golpe en área en fase 2
        if (fase == 2 && getTarget() != null && tickCount % INTERVALO_GOLPE_AREA_TICKS == 0) {
            ejecutarGolpeEnArea(mundo);
        }

        // Partículas en fase 2 (enfurecido)
        if (fase == 2 && tickCount % 8 == 0) {
            mundo.sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    getX(),
                    getY() + getBbHeight() + 0.2,
                    getZ(),
                    1,
                    0.4,
                    0.2,
                    0.4,
                    0.0);
        }
    }

    private void cambiarFase(ServerLevel mundo, int nuevaFase) {
        this.fase = nuevaFase;
        barra.setColor(BossEvent.BossBarColor.PURPLE);
        for (ServerPlayer jugador : mundo.players()) {
            if (jugador.distanceToSqr(this) <= 48 * 48) {
                jugador.sendSystemMessage(
                        Component.literal("¡El Rey Zombie entra en cólera!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
        }
        mundo.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE,
                1.8f,
                0.8f);

        // Aumenta su velocidad en fase 2
        var attrVel = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attrVel != null) {
            attrVel.addOrReplacePermanentModifier(
                    new AttributeModifier(
                            MODIFIER_FURIA, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private void invocarEsbirros(ServerLevel mundo) {
        int cantidad = 3;
        for (int i = 0; i < cantidad; i++) {
            Zombie esbirro = EntityTypes.ZOMBIE.create(mundo, EntitySpawnReason.MOB_SUMMONED);
            if (esbirro == null) {
                continue;
            }
            double angulo = random.nextDouble() * 2 * Math.PI;
            double radio = 2.0 + random.nextDouble() * 2.0;
            esbirro.snapTo(
                    getX() + Math.cos(angulo) * radio,
                    getY(),
                    getZ() + Math.sin(angulo) * radio,
                    random.nextFloat() * 360f,
                    0f);
            esbirro.setTarget(getTarget());
            esbirro.setPersistenceRequired();

            // En fase 2 los invocados llevan armadura de hierro
            if (fase == 2) {
                esbirro.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                esbirro.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                esbirro.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                esbirro.setDropChance(EquipmentSlot.HEAD, 0.0f);
                esbirro.setDropChance(EquipmentSlot.CHEST, 0.0f);
                esbirro.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }

            mundo.addFreshEntity(esbirro);
            mundo.sendParticles(
                    ParticleTypes.POOF,
                    esbirro.getX(),
                    esbirro.getY() + 0.5,
                    esbirro.getZ(),
                    8,
                    0.2,
                    0.2,
                    0.2,
                    0.02);
        }
        mundo.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE,
                1.5f,
                0.8f);
    }

    /** Golpe circular que daña y empuja a los jugadores alrededor. */
    private void ejecutarGolpeEnArea(ServerLevel mundo) {
        double radioGolpe = 5.0;
        mundo.sendParticles(
                ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.5, getZ(), 4, 1.2, 0.2, 1.2, 0.0);
        mundo.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE,
                1.5f,
                0.6f);

        for (Player p :
                mundo.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radioGolpe))) {
            if (p.isAlive() && !p.isSpectator()) {
                p.hurtServer(mundo, mundo.damageSources().mobAttack(this), 6.0f);
                Vec3 empuje = p.position().subtract(position()).normalize().scale(1.2);
                p.setDeltaMovement(empuje.x, 0.35, empuje.z);
                p.needsSync = true;
                if (p instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }
            }
        }
    }

    @Override
    protected void dropFromLootTable(
            ServerLevel nivel, DamageSource fuente, boolean recientementeGolpeado) {
        Optional<Partida> partida =
                Dungeons.registro().partidas().stream()
                        .filter(p -> p.dungeon().zona().contiene(getX(), getY(), getZ()))
                        .findFirst();

        String dungeonId = partida.map(p -> p.dungeon().id()).orElse("cripta");
        String dificultadId = partida.map(p -> p.dificultad().id()).orElse("facil");

        Identifier idTabla =
                Identifier.fromNamespaceAndPath(
                        "dolfincreepers", "dungeon/" + dungeonId + "/jefe_" + dificultadId);
        ResourceKey<net.minecraft.world.level.storage.loot.LootTable> clave =
                ResourceKey.create(Registries.LOOT_TABLE, idTabla);

        dropFromLootTable(nivel, fuente, recientementeGolpeado, clave);
    }

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
