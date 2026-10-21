package com.dolfin.creepers.dungeon.plantilla;

import com.mojang.datafixers.DataFixer;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Archivos de las plantillas en {@code config/dolfin-dungeons/plantillas/<id>/}:
 *
 * <ul>
 *   <li>{@code plantilla.json}: {@link ManifiestoPlantilla}.
 *   <li>{@code trozos/<i>_<j>_<k>.nbt}: un {@link StructureTemplate} vanilla por trozo (bloques sin
 *       aire y contenido de bloques), sin entidades.
 *   <li>{@code entidades.nbt}: las entidades marcadas, con el formato de un chunk de entidades
 *       ({@code DataVersion} + {@code Entities}) y posiciones relativas a la zona.
 * </ul>
 *
 * Todo se actualiza con los data fixers de Minecraft al leer. Guardar escribe en {@code <id>.nuevo}
 * y solo al final sustituye la plantilla anterior: un guardado a medias no la estropea. Las
 * operaciones de E/S lanzan {@link UncheckedIOException}.
 */
public final class AlmacenPlantillas {
    private static final String MANIFIESTO = "plantilla.json";
    private static final String TROZOS = "trozos";
    private static final String ENTIDADES = "entidades.nbt";
    private static final String SUFIJO_NUEVA = ".nuevo";
    private static final String SUFIJO_VIEJA = ".viejo";

    private final Path base;

    /**
     * @param base carpeta {@code plantillas}
     */
    public AlmacenPlantillas(Path base) {
        this.base = base;
    }

    /** Hay plantilla guardada para la dungeon. */
    public boolean existe(String dungeon) {
        return Files.isRegularFile(carpeta(dungeon).resolve(MANIFIESTO));
    }

    /**
     * Manifiesto de la plantilla; vacío si no hay. Lanza {@link IllegalArgumentException} si está
     * mal formado.
     */
    public Optional<ManifiestoPlantilla> manifiesto(String dungeon) {
        Path archivo = carpeta(dungeon).resolve(MANIFIESTO);
        if (!Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    ManifiestoPlantilla.deJson(Files.readString(archivo, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un trozo leído: su plantilla y qué posiciones ocupa. */
    public record TrozoLeido(StructureTemplate plantilla, MapaOcupacion ocupacion) {}

    /** Lee un trozo, actualizado a la versión actual del juego. */
    public TrozoLeido leerTrozo(
            String dungeon, Troceado.Trozo trozo, HolderGetter<Block> bloques, DataFixer fixer) {
        CompoundTag nbt = leerNbt(carpeta(dungeon).resolve(TROZOS).resolve(trozo.archivo()));
        CompoundTag actualizado =
                DataFixTypes.STRUCTURE.updateToCurrentVersion(
                        fixer, nbt, NbtUtils.getDataVersion(nbt, 500));
        StructureTemplate plantilla = new StructureTemplate();
        plantilla.load(bloques, actualizado);
        return new TrozoLeido(plantilla, ocupacion(plantilla, actualizado));
    }

    /** Entidades marcadas, actualizadas; vacío si la plantilla no tiene. */
    public List<CompoundTag> leerEntidades(String dungeon, DataFixer fixer) {
        Path archivo = carpeta(dungeon).resolve(ENTIDADES);
        if (!Files.isRegularFile(archivo)) {
            return List.of();
        }
        CompoundTag nbt = leerNbt(archivo);
        CompoundTag actualizado =
                DataFixTypes.ENTITY_CHUNK.updateToCurrentVersion(
                        fixer, nbt, NbtUtils.getDataVersion(nbt, 500));
        List<CompoundTag> entidades = new ArrayList<>();
        for (Tag t : actualizado.getListOrEmpty("Entities")) {
            if (t instanceof CompoundTag c) {
                entidades.add(c);
            }
        }
        return entidades;
    }

    /**
     * Quedó un guardado a medias (el servidor se cayó mientras se guardaba): la zona puede tener
     * obra nueva que la plantilla anterior pisaría.
     */
    public boolean guardadoInterrumpido(String dungeon) {
        return Files.exists(base.resolve(dungeon + SUFIJO_NUEVA));
    }

    /**
     * Borra los restos de un guardado interrumpido y, si se cortó justo al sustituir, recupera la
     * plantilla anterior.
     */
    public void descartarInterrumpido(String dungeon) {
        borrar(base.resolve(dungeon + SUFIJO_NUEVA));
        Path vieja = base.resolve(dungeon + SUFIJO_VIEJA);
        try {
            if (Files.exists(vieja) && !Files.exists(carpeta(dungeon))) {
                Files.move(vieja, carpeta(dungeon));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        borrar(vieja);
    }

    /** Empieza un guardado en una carpeta temporal limpia. */
    public Escritura empezarEscritura(String dungeon) {
        Path temporal = base.resolve(dungeon + SUFIJO_NUEVA);
        borrar(temporal);
        crearCarpetas(temporal.resolve(TROZOS));
        return new Escritura(dungeon, temporal);
    }

    /** Un guardado en curso; nada es visible hasta {@link #confirmar()}. */
    public final class Escritura {
        private final String dungeon;
        private final Path temporal;

        private Escritura(String dungeon, Path temporal) {
            this.dungeon = dungeon;
            this.temporal = temporal;
        }

        public void trozo(Troceado.Trozo trozo, StructureTemplate plantilla) {
            escribirNbt(
                    temporal.resolve(TROZOS).resolve(trozo.archivo()),
                    NbtUtils.addCurrentDataVersion(plantilla.save(new CompoundTag())));
        }

        public void entidades(List<CompoundTag> entidades) {
            CompoundTag raiz = new CompoundTag();
            ListTag lista = new ListTag();
            lista.addAll(entidades);
            raiz.put("Entities", lista);
            escribirNbt(temporal.resolve(ENTIDADES), NbtUtils.addCurrentDataVersion(raiz));
        }

        /** Escribe el manifiesto y sustituye la plantilla anterior por esta. */
        public void confirmar(ManifiestoPlantilla manifiesto) {
            try {
                Files.writeString(
                        temporal.resolve(MANIFIESTO), manifiesto.aJson(), StandardCharsets.UTF_8);
                Path destino = carpeta(dungeon);
                Path vieja = base.resolve(dungeon + SUFIJO_VIEJA);
                borrar(vieja);
                if (Files.exists(destino)) {
                    Files.move(destino, vieja);
                }
                Files.move(temporal, destino);
                borrar(vieja);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Descarta lo escrito. */
        public void descartar() {
            borrar(temporal);
        }
    }

    /** Posiciones de la lista {@code blocks} del NBT de un {@link StructureTemplate}. */
    private static MapaOcupacion ocupacion(StructureTemplate plantilla, CompoundTag nbt) {
        MapaOcupacion mapa =
                new MapaOcupacion(
                        plantilla.getSize().getX(),
                        plantilla.getSize().getY(),
                        plantilla.getSize().getZ());
        for (Tag t : nbt.getListOrEmpty("blocks")) {
            if (t instanceof CompoundTag bloque) {
                ListTag pos = bloque.getListOrEmpty("pos");
                mapa.marcar(pos.getIntOr(0, -1), pos.getIntOr(1, -1), pos.getIntOr(2, -1));
            }
        }
        return mapa;
    }

    private Path carpeta(String dungeon) {
        return base.resolve(dungeon);
    }

    private static CompoundTag leerNbt(Path archivo) {
        try {
            return NbtIo.readCompressed(archivo, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escribirNbt(Path archivo, CompoundTag nbt) {
        try {
            NbtIo.writeCompressed(nbt, archivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void crearCarpetas(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void borrar(Path carpeta) {
        if (!Files.exists(carpeta)) {
            return;
        }
        try (Stream<Path> rutas = Files.walk(carpeta)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(ruta);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
