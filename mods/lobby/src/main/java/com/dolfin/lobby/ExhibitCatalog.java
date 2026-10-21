package com.dolfin.lobby;

import com.dolfin.creepers.Variante;
import com.dolfin.zombies.ZombieKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.Mob;

import java.util.*;

/** Registry-backed full catalog: deliberate summons do not depend on biome or natural spawning. */
public final class ExhibitCatalog {
    public record Entry(String id, String name, String description) {}

    public static final List<Entry> ALL;

    static {
        var entries = new ArrayList<Entry>();
        String[] creepers = {
            "Fuego: prende llamas; aparece en desiertos, sabanas y Nether.",
            "Hielo: congela y ralentiza; biomas nevados.",
            "Saltarín: salta para acercarse; superficie del mundo.",
            "Mini: pequeño y veloz; superficie del mundo.",
            "Eléctrico: descarga eléctrica; superficie del mundo.",
            "Multiplicador: genera minis; superficie del mundo.",
            "Gigante: gran vida y explosión; superficie del mundo.",
            "Tóxico: nube venenosa; pantanos y cuevas frondosas.",
            "Rey: jefe por fases y refuerzos; no aparece naturalmente."
        };
        for (var v : Variante.values())
            entries.add(
                    new Entry(
                            "dolfincreepers:" + v.id(),
                            "Creeper " + v.name().toLowerCase(Locale.ROOT),
                            creepers[v.ordinal()]));
        String[] zombies = {
            "Excava obstáculos del circuito de prueba.",
            "Roba objetos desechables de la barra; se recuperan al reiniciar.",
            "Fuego al caminar y golpear; prueba la zona de madera.",
            "Agarra y arrastra bajo el agua; usa la piscina de seis bloques.",
            "Escudo frontal; prueba hacha y espada.",
            "Reduce hambre y persigue comida.",
            "Llama aliados próximos; hay un compañero en el circuito.",
            "Cura aliados y lanza pociones; tiene un compañero herido.",
            "Cultivos y animales: habilidades desactivadas por defecto; esta estación respeta la"
                    + " configuración.",
            "Ralentiza y deja hielo al morir.",
            "Montura y movilidad.",
            "Nube nociva al morir.",
            "Escala pequeña y velocidad.",
            "Infección curable con manzana dorada; la muerte se cancela para aislar la"
                    + " demostración.",
            "Apariencia de jugador; el circuito usa tu apariencia, sin custodiar objetos de"
                    + " supervivencia.",
            "Invisibilidad y luz; usa las zonas iluminadas."
        };
        for (var k : ZombieKind.values())
            entries.add(new Entry("dolfinzombies:" + k.id(), k.displayName, zombies[k.ordinal()]));
        ALL = List.copyOf(entries);
    }

    public static void spawn(ServerLevel world, BlockPos pos, Entry entry, ServerPlayer player) {
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entry.id()));
        var e = type.create(world, EntitySpawnReason.COMMAND);
        if (e == null) throw new IllegalStateException(entry.id());
        e.addTag("dolfin_demo_mob");
        e.addTag("dolfin_demo_" + player.getUUID());
        e.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (e instanceof Mob mob) {
            mob.finalizeSpawn(
                    world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
            mob.setPersistenceRequired();
            mob.setTarget(player);
        }
        if (e instanceof com.dolfin.zombies.ConfiguredZombie z && z.kind() == ZombieKind.MIMO)
            z.copyPlayerAppearance(player);
        world.addFreshEntity(e);
        if (entry.id().contains("abismo")) e.setPos(pos.getX() + 7.5, 123, pos.getZ() + .5);
        if (entry.id().contains("granjero")) {
            var animal = EntityTypes.COW.create(world, EntitySpawnReason.COMMAND);
            if (animal != null) {
                animal.addTag("dolfin_demo_mob");
                animal.setPos(pos.getX() - 4.5, 129, pos.getZ() + 2.5);
                world.addFreshEntity(animal);
            }
        }
        if (entry.id().contains("griton") || entry.id().contains("bruja")) {
            var ally = EntityTypes.ZOMBIE.create(world, EntitySpawnReason.COMMAND);
            if (ally != null) {
                ally.addTag("dolfin_demo_mob");
                ally.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
                ally.setHealth(5);
                ally.setTarget(player);
                world.addFreshEntity(ally);
            }
        }
    }
}
