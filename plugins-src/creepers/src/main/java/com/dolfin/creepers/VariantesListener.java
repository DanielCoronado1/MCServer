package com.dolfin.creepers;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Hace aparecer las variantes y les da su efecto especial al explotar. */
public final class VariantesListener implements Listener {

    private final CreepersPlugin plugin;
    private final Variantes variantes;

    public VariantesListener(CreepersPlugin plugin, Variantes variantes) {
        this.plugin = plugin;
        this.variantes = variantes;
    }

    // =====================================================================
    //  Aparicion: algunos creepers naturales nacen como variante
    // =====================================================================

    @EventHandler(ignoreCancelled = true)
    public void alAparecer(CreatureSpawnEvent evento) {
        if (!(evento.getEntity() instanceof Creeper creeper)
                || evento.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        // Tiramos un "dado" de 0 a 100 y recorremos las probabilidades de config.yml.
        ConfigurationSection probabilidades = plugin.getConfig().getConfigurationSection("probabilidades");
        if (probabilidades == null) {
            return;
        }
        double dado = ThreadLocalRandom.current().nextDouble(100);
        double acumulado = 0;
        for (String clave : probabilidades.getKeys(false)) {
            acumulado += probabilidades.getDouble(clave);
            Variante variante = Variante.desde(clave);
            if (variante != null && dado < acumulado) {
                variantes.aplicar(creeper, variante);
                return;
            }
        }
    }

    // =====================================================================
    //  Explosiones especiales
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alExplotar(EntityExplodeEvent evento) {
        Variante variante = variantes.de(evento.getEntity());
        if (variante == null) {
            return;
        }
        Location lugar = evento.getLocation();
        switch (variante) {
            case HIELO -> {
                evento.blockList().clear();      // no rompe el terreno: congela
                congelar(lugar);
            }
            case ELECTRICO -> rayosEnCadena((Creeper) evento.getEntity(), lugar);
            case MULTIPLICADOR -> soltarMinis(lugar);
            case MINI -> evento.blockList().clear();
            default -> {
            }
        }
    }

    /** Ralentiza y congela a los jugadores cercanos y convierte el agua en hielo que se derrite solo. */
    private void congelar(Location lugar) {
        World mundo = lugar.getWorld();
        mundo.spawnParticle(Particle.SNOWFLAKE, lugar, 300, 2.5, 1.5, 2.5, 0.05);
        mundo.playSound(lugar, Sound.BLOCK_GLASS_BREAK, 2f, 0.6f);
        mundo.playSound(lugar, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.8f);

        for (Player jugador : lugar.getNearbyPlayers(6)) {
            jugador.setFreezeTicks(Math.max(jugador.getFreezeTicks(), 200));
            jugador.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            jugador.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));
        }

        int radio = 5;
        for (int x = -radio; x <= radio; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radio; z <= radio; z++) {
                    if (x * x + z * z > radio * radio) {
                        continue;
                    }
                    Block bloque = lugar.getBlock().getRelative(x, y, z);
                    boolean aguaQuieta = bloque.getType() == Material.WATER
                            && bloque.getBlockData() instanceof Levelled agua && agua.getLevel() == 0;
                    if (aguaQuieta && bloque.getRelative(0, 1, 0).getType().isAir()) {
                        bloque.setType(Material.FROSTED_ICE); // el hielo escarchado se derrite solo
                    }
                }
            }
        }
    }

    /** Lanza rayos a las 3 criaturas mas cercanas (jugadores y mobs, no otros creepers). */
    private void rayosEnCadena(Creeper creeper, Location lugar) {
        lugar.getWorld().getNearbyLivingEntities(lugar, 10).stream()
                .filter(objetivo -> !(objetivo instanceof Creeper) && objetivo.isValid())
                .sorted(Comparator.comparingDouble(objetivo -> objetivo.getLocation().distanceSquared(lugar)))
                .limit(3)
                .forEach(objetivo -> {
                    objetivo.getWorld().strikeLightningEffect(objetivo.getLocation());
                    objetivo.damage(4, creeper);
                });
        lugar.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, lugar, 150, 2, 2, 2, 0.3);
    }

    /** Suelta 3 mini creepers que salen disparados en distintas direcciones. */
    private void soltarMinis(Location lugar) {
        lugar.getWorld().playSound(lugar, Sound.ENTITY_SLIME_SQUISH, 2f, 0.6f);
        for (int i = 0; i < 3; i++) {
            Creeper mini = variantes.invocar(lugar.clone().add(0, 0.5, 0), Variante.MINI);
            double angulo = 2 * Math.PI * i / 3;
            mini.setVelocity(new Vector(Math.cos(angulo) * 0.5, 0.45, Math.sin(angulo) * 0.5));
        }
    }

    // =====================================================================
    //  Comportamiento continuo: auras de particulas y saltos
    // =====================================================================

    /** Se llama cada 10 ticks (medio segundo) desde CreepersPlugin. */
    public void tick() {
        for (World mundo : plugin.getServer().getWorlds()) {
            for (Creeper creeper : mundo.getEntitiesByClass(Creeper.class)) {
                Variante variante = variantes.de(creeper);
                if (variante != null) {
                    aura(creeper, variante);
                    if (variante == Variante.SALTARIN) {
                        saltar(creeper);
                    }
                }
            }
        }
    }

    private void aura(Creeper creeper, Variante variante) {
        Location lugar = creeper.getLocation().add(0, creeper.getHeight() / 2, 0);
        World mundo = creeper.getWorld();
        switch (variante) {
            case HIELO -> mundo.spawnParticle(Particle.SNOWFLAKE, lugar, 4, 0.3, 0.5, 0.3, 0.01);
            case ELECTRICO -> mundo.spawnParticle(Particle.ELECTRIC_SPARK, lugar, 3, 0.3, 0.5, 0.3, 0.05);
            case MULTIPLICADOR, MINI -> mundo.spawnParticle(Particle.HAPPY_VILLAGER, lugar, 2, 0.3, 0.4, 0.3);
            default -> {
            }
        }
    }

    /** Si tiene a un jugador cerca y esta en el suelo, salta hacia el. */
    private void saltar(Creeper creeper) {
        LivingEntity objetivo = creeper.getTarget();
        if (objetivo == null || !creeper.isOnGround() || ThreadLocalRandom.current().nextInt(3) != 0) {
            return;
        }
        Vector hacia = objetivo.getLocation().toVector().subtract(creeper.getLocation().toVector());
        double distancia = hacia.length();
        if (distancia < 3 || distancia > 10) {
            return;
        }
        creeper.setVelocity(hacia.normalize().multiply(0.8).setY(0.55));
        creeper.getWorld().spawnParticle(Particle.CLOUD, creeper.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
        creeper.getWorld().playSound(creeper.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 1.4f);
    }
}
