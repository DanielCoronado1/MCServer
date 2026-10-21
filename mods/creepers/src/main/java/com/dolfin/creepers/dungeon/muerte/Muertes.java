package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.entrada.EntradaSalida;
import com.dolfin.creepers.dungeon.modelo.CausaMuerte;
import com.dolfin.creepers.dungeon.modelo.EstadoMiembro;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.Consecuencia;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Muerte dentro de una partida (#16). Se engancha a {@code ServerLivingEntityEvents.ALLOW_DEATH} y
 * cancela la muerte vanilla de los miembros: nunca hay pantalla de muerte ni objetos en el suelo.
 * Lo que pasa después lo decide {@link ReglasMuerte#consecuencia}: reaparecer en el checkpoint (con
 * o sin tumba), pasar a espectador o el castigo de hardcore ({@link CastigosHardcore}).
 *
 * <p>Orden con el tótem (comprobado en MC 26.3): Fabric lanza {@code ALLOW_DEATH} dentro de {@code
 * LivingEntity.hurtServer} <em>antes</em> de {@code checkTotemDeathProtection}. Por eso, si el
 * tótem va a actuar ({@link #totemActuara}) se deja seguir a vanilla: el tótem salva al jugador y
 * no cuenta como muerte en ninguna dificultad.
 *
 * <p>Además, cada {@value #TICKS_ENTRE_COMPROBACIONES} ticks marca los checkpoints alcanzados y
 * devuelve a la zona a los espectadores que salgan de ella.
 */
public final class Muertes {
    private static final int TICKS_ENTRE_COMPROBACIONES = 5;

    private final RegistroDungeons registro;
    private final EntradaSalida entradaSalida;
    private final Tumbas tumbas;
    private final CastigosHardcore castigos;

    public Muertes(
            RegistroDungeons registro,
            EntradaSalida entradaSalida,
            Tumbas tumbas,
            CastigosHardcore castigos) {
        this.registro = registro;
        this.entradaSalida = entradaSalida;
        this.tumbas = tumbas;
        this.castigos = castigos;
    }

    /**
     * Oyente de {@code ALLOW_DEATH}: false cancela la muerte vanilla de un miembro de una partida y
     * aplica la de la dungeon. Fuera de una partida, o si el tótem va a salvarlo, no interviene.
     */
    public boolean permitirMuerte(LivingEntity entidad, DamageSource fuente, float danio) {
        if (!(entidad instanceof ServerPlayer jugador) || !DimensionDungeons.es(jugador.level())) {
            return true;
        }
        Optional<Partida> partida = registro.partidaDe(jugador.getUUID());
        Optional<EstadoMiembro> estado =
                partida.flatMap(p -> p.miembro(jugador.getUUID())).map(Miembro::estado);
        if (estado.isEmpty() || totemActuara(jugador, fuente)) {
            return true;
        }
        reanimar(jugador);
        if (estado.get() == EstadoMiembro.ESPECTADOR) {
            reubicar(jugador, partida.get(), GameType.SPECTATOR);
        } else {
            morir(jugador, partida.get(), fuente);
        }
        return false;
    }

    /** Checkpoints alcanzados y confinamiento de espectadores. */
    public void tick(MinecraftServer server) {
        if (server.getTickCount() % TICKS_ENTRE_COMPROBACIONES != 0) {
            return;
        }
        for (Partida partida : registro.partidas()) {
            for (Miembro miembro : partida.miembros()) {
                ServerPlayer jugador = server.getPlayerList().getPlayer(miembro.id());
                if (jugador == null || !DimensionDungeons.es(jugador.level())) {
                    continue;
                }
                if (miembro.estado() == EstadoMiembro.VIVO) {
                    comprobarCheckpoint(jugador, partida);
                } else if (miembro.estado() == EstadoMiembro.ESPECTADOR) {
                    confinar(jugador, partida);
                }
            }
        }
    }

    /**
     * El tótem (o cualquier objeto con {@code death_protection} en una mano) salvará al jugador:
     * misma condición que {@code LivingEntity.checkTotemDeathProtection}.
     */
    static boolean totemActuara(LivingEntity entidad, DamageSource fuente) {
        if (fuente.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        for (InteractionHand mano : InteractionHand.values()) {
            if (entidad.getItemInHand(mano).has(DataComponents.DEATH_PROTECTION)) {
                return true;
            }
        }
        return false;
    }

    /** Resta la vida y aplica la consecuencia; el aviso al grupo sale de {@code JugadorMurio}. */
    private void morir(ServerPlayer jugador, Partida partida, DamageSource fuente) {
        int vidas =
                registro.registrarMuerte(jugador.getUUID(), CausaMuerte.COMBATE)
                        .orElse(Miembro.VIDAS_ILIMITADAS);
        Consecuencia consecuencia = ReglasMuerte.consecuencia(partida.dificultad(), vidas);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_death player={} dungeon={} difficulty={} cause={} livesLeft={}"
                        + " outcome={} match={}",
                jugador.getName().getString(),
                partida.dungeon().id(),
                partida.dificultad().id(),
                fuente.getMsgId(),
                vidas,
                consecuencia,
                partida.id());
        if (consecuencia.dejaTumba()) {
            tumbas.crear(jugador, partida);
        }
        switch (consecuencia.destino()) {
            case REAPARECE -> reubicar(jugador, partida, GameType.ADVENTURE);
            case ESPECTADOR -> {
                registro.pasarAEspectador(jugador.getUUID());
                reubicar(jugador, partida, GameType.SPECTATOR);
            }
            case ELIMINADO -> castigos.alMorir(jugador, partida);
        }
    }

    private void comprobarCheckpoint(ServerPlayer jugador, Partida partida) {
        partida.dungeon()
                .checkpointEn(jugador.getX(), jugador.getY(), jugador.getZ())
                .ifPresent(indice -> registro.alcanzarCheckpoint(jugador.getUUID(), indice));
    }

    private void confinar(ServerPlayer jugador, Partida partida) {
        if (!partida.dungeon().zona().contiene(jugador.getX(), jugador.getY(), jugador.getZ())) {
            reubicar(jugador, partida, GameType.SPECTATOR);
        }
    }

    /** Lleva al miembro a su checkpoint; si no se puede, sale de la partida. */
    private void reubicar(ServerPlayer jugador, Partida partida, GameType modo) {
        if (!entradaSalida.llevarAReaparicion(jugador, partida, modo)) {
            entradaSalida.sacar(jugador, MotivoSalida.EXPULSION);
        }
    }

    /** Deja al jugador como recién aparecido: vida, hambre, sin fuego, efectos ni inercia. */
    private static void reanimar(ServerPlayer jugador) {
        jugador.setHealth(jugador.getMaxHealth());
        jugador.getFoodData().setFoodLevel(20);
        jugador.getFoodData().setSaturation(5.0F);
        jugador.clearFire();
        jugador.setTicksFrozen(0);
        jugador.setAirSupply(jugador.getMaxAirSupply());
        jugador.removeAllEffects();
        jugador.resetFallDistance();
        jugador.setDeltaMovement(Vec3.ZERO);
    }
}
