package com.dolfin.creepers.dungeon.cripta;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon.Modo;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.dolfin.creepers.dungeon.plantilla.AlmacenPlantillas;
import com.dolfin.creepers.dungeon.plantilla.Caja;
import com.dolfin.creepers.dungeon.plantilla.DatosMarcador;
import com.dolfin.creepers.dungeon.plantilla.ManifiestoPlantilla;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Generador y definición de la primera dungeon oficial: la Cripta (#20).
 *
 * <p>Consta de 3 pisos con 2 checkpoints (fogata + cofre) y la arena del Rey Zombie al final:
 *
 * <ul>
 *   <li>Piso 1: entrada, sala de emboscada y acertijo de palancas.
 *   <li>Piso 2: trampa de flechas y puerta de dos placas con camino alternativo para jugadores en
 *       solitario.
 *   <li>Piso 3: sala del jefe Rey Zombie y portal de victoria.
 * </ul>
 */
public final class CriptaPlantilla {
    public static final String ID = "cripta";
    public static final int LADO = 48;
    public static final int ALTO = 32;

    public static final Zona ZONA = new Zona(0, 50, 0, 47, 81, 47);

    private CriptaPlantilla() {}

    /** Definición de la dungeon para la configuración. */
    public static DefinicionDungeon definicion() {
        return new DefinicionDungeon(
                ID,
                "Cripta Olvidada",
                true,
                ZONA,
                new Punto(4.5, 52.0, 4.5, 0.0f, 0.0f),
                List.of(
                        new Punto(40.5, 61.0, 40.5, 0.0f, 0.0f),
                        new Punto(6.5, 71.0, 40.5, 0.0f, 0.0f)),
                Optional.of(new Punto(24.5, 72.0, 42.5, 0.0f, 0.0f)),
                Modo.GRUPO,
                1,
                4,
                EnumSet.allOf(Dificultad.class),
                "dolfin.dungeon.cripta",
                List.of(),
                30,
                45);
    }

    /** Marcadores de enemigos y del jefe para cada piso. */
    public static List<DatosMarcador> marcadores() {
        return List.of(
                // Piso 1: patrullas, emboscada y acertijo
                new DatosMarcador(12, 2, 8, "minecraft:zombie", 1, ""),
                new DatosMarcador(24, 2, 16, "minecraft:zombie", 3, "{extraHardcore:2}"),
                new DatosMarcador(36, 2, 28, "minecraft:skeleton", 2, ""),

                // Piso 2: trampa, dos placas y camino alternativo
                new DatosMarcador(20, 11, 20, "minecraft:skeleton", 2, ""),
                new DatosMarcador(15, 11, 35, "minecraft:zombie", 2, "{extraHardcore:1}"),
                new DatosMarcador(10, 11, 42, "minecraft:zombie", 1, ""),

                // Piso 3: Jefe Rey Zombie
                new DatosMarcador(24, 21, 28, "dolfincreepers:rey_zombie", 1, ""));
    }

    /** Manifiesto que describe los metadatos y marcadores de la cripta. */
    public static ManifiestoPlantilla manifiesto() {
        return new ManifiestoPlantilla(
                ManifiestoPlantilla.VERSION,
                ID,
                Caja.de(ZONA),
                LADO,
                0,
                System.currentTimeMillis(),
                marcadores());
    }

    /**
     * Asegura que los archivos de la plantilla existen en disco dentro del almacén. Si no existen,
     * los construye y guarda de forma atómica.
     */
    public static void asegurarEnDisco(AlmacenPlantillas almacen, Path carpetaPlantillas) {
        if (almacen.existe(ID)) {
            return;
        }

        Path carpetaCripta = carpetaPlantillas.resolve(ID);
        Path carpetaTrozos = carpetaCripta.resolve("trozos");
        try {
            Files.createDirectories(carpetaTrozos);
            CompoundTag nbtTrozo = construirNbtTrozo();
            NbtIo.writeCompressed(nbtTrozo, carpetaTrozos.resolve("0_0_0.nbt"));
            Files.writeString(
                    carpetaCripta.resolve("plantilla.json"),
                    manifiesto().aJson(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Construye la estructura física en formato StructureTemplate NBT vanilla. */
    public static CompoundTag construirNbtTrozo() {
        CompoundTag raiz = new CompoundTag();

        // Tamaño del trozo: 48x32x48
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(LADO));
        size.add(IntTag.valueOf(ALTO));
        size.add(IntTag.valueOf(LADO));
        raiz.put("size", size);
        raiz.put("entities", new ListTag());

        // Paleta de bloques
        List<String> nombresPaleta =
                List.of(
                        "minecraft:stone_bricks", // 0
                        "minecraft:cracked_stone_bricks", // 1
                        "minecraft:mossy_stone_bricks", // 2
                        "minecraft:iron_door", // 3
                        "minecraft:campfire", // 4
                        "minecraft:chest", // 5
                        "minecraft:lever", // 6
                        "minecraft:stone_pressure_plate", // 7
                        "minecraft:tripwire_hook", // 8
                        "minecraft:string", // 9
                        "minecraft:dispenser", // 10
                        "minecraft:chiseled_stone_bricks", // 11
                        "minecraft:polished_deepslate", // 12
                        "minecraft:torch" // 13
                        );

        ListTag palette = new ListTag();
        for (String nombre : nombresPaleta) {
            CompoundTag entrada = new CompoundTag();
            entrada.putString("Name", nombre);
            palette.add(entrada);
        }
        raiz.put("palette", palette);

        // Bloques colocados
        ListTag blocks = new ListTag();

        // --- Suelo general de la base (y = 1)
        for (int x = 0; x < LADO; x++) {
            for (int z = 0; z < LADO; z++) {
                int paleta = (x + z) % 7 == 0 ? 1 : ((x * z) % 5 == 0 ? 2 : 0);
                blocks.add(bloque(x, 1, z, paleta));
            }
        }

        // --- Piso 1: Sala de emboscada (puertas de hierro en x=24, z=10 y z=22)
        construirRecinto(blocks, 18, 1, 10, 30, 8, 22, 0);
        blocks.add(bloque(24, 2, 10, 3)); // Puerta hierro entrada
        blocks.add(bloque(24, 2, 11, 7)); // Placa interior
        blocks.add(bloque(24, 2, 22, 3)); // Puerta hierro salida

        // --- Piso 1: Acertijo de palancas (x=30..42, z=24..34)
        construirRecinto(blocks, 30, 1, 24, 42, 8, 34, 0);
        blocks.add(bloque(34, 3, 25, 6)); // Palanca 1
        blocks.add(bloque(36, 3, 25, 6)); // Palanca 2
        blocks.add(bloque(38, 3, 25, 6)); // Palanca 3
        blocks.add(bloque(36, 2, 34, 3)); // Puerta hierro

        // --- Checkpoint 1 (entre piso 1 y 2): fogata + cofre en x=40, z=40, y=10
        blocks.add(bloque(40, 10, 40, 4)); // Fogata
        blocks.add(bloque(41, 10, 40, 5)); // Cofre

        // --- Suelo Piso 2 (y = 10)
        for (int x = 2; x < LADO - 2; x++) {
            for (int z = 2; z < LADO - 2; z++) {
                blocks.add(bloque(x, 10, z, (x + z) % 3 == 0 ? 2 : 0));
            }
        }

        // --- Piso 2: Trampa de flechas (ganchos en x=18 y x=22 a y=11)
        blocks.add(bloque(18, 12, 18, 8)); // Gancho
        blocks.add(bloque(19, 11, 18, 9)); // Hilo
        blocks.add(bloque(20, 11, 18, 9)); // Hilo
        blocks.add(bloque(21, 11, 18, 9)); // Hilo
        blocks.add(bloque(22, 12, 18, 8)); // Gancho
        blocks.add(bloque(18, 12, 19, 10)); // Dispensador

        // --- Piso 2: Puerta de dos placas con camino alternativo para un jugador
        blocks.add(bloque(18, 11, 30, 7)); // Placa 1
        blocks.add(bloque(20, 11, 30, 7)); // Placa 2
        blocks.add(bloque(19, 11, 31, 3)); // Puerta 2 placas
        // Camino alternativo individual (bypass lateral por x=12)
        construirPasillo(blocks, 12, 11, 28, 12, 13, 34, 0);

        // --- Checkpoint 2 (entre piso 2 y 3): fogata + cofre en x=6, z=40, y=20
        blocks.add(bloque(6, 20, 40, 4)); // Fogata
        blocks.add(bloque(7, 20, 40, 5)); // Cofre

        // --- Suelo Piso 3: Gran arena del jefe (y = 20)
        for (int x = 8; x <= 40; x++) {
            for (int z = 12; z <= 44; z++) {
                blocks.add(bloque(x, 20, z, 12)); // Pulished deepslate arena
            }
        }

        // Columnas ornamentales en la arena
        construirColumna(blocks, 14, 21, 18, 6, 11);
        construirColumna(blocks, 34, 21, 18, 6, 11);
        construirColumna(blocks, 14, 21, 36, 6, 11);
        construirColumna(blocks, 34, 21, 36, 6, 11);

        // Portal de salida tras el jefe
        blocks.add(bloque(24, 21, 42, 12));
        blocks.add(bloque(23, 22, 42, 12));
        blocks.add(bloque(25, 22, 42, 12));
        blocks.add(bloque(24, 23, 42, 12));

        raiz.put("blocks", blocks);
        return NbtUtils.addCurrentDataVersion(raiz);
    }

    private static CompoundTag bloque(int x, int y, int z, int estado) {
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        tag.put("pos", pos);
        tag.putInt("state", estado);
        return tag;
    }

    private static void construirRecinto(
            ListTag blocks, int x1, int y1, int z1, int x2, int y2, int z2, int estado) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2 || y == y1 || y == y2) {
                        blocks.add(bloque(x, y, z, estado));
                    }
                }
            }
        }
    }

    private static void construirPasillo(
            ListTag blocks, int x1, int y1, int z1, int x2, int y2, int z2, int estado) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    blocks.add(bloque(x, y, z, estado));
                }
            }
        }
    }

    private static void construirColumna(
            ListTag blocks, int x, int yBase, int z, int altura, int estado) {
        for (int h = 0; h < altura; h++) {
            blocks.add(bloque(x, yBase + h, z, estado));
        }
    }
}
