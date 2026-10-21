package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.plantilla.EjecutorPorTicks.Paso;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Pasos para guardar la zona de una dungeon como plantilla: espera a que cargue, captura cada trozo
 * (bloques y contenido de bloques) en un tick propio, lee los marcadores de mobs y las entidades
 * marcadas, y por último escribe el manifiesto y sustituye la plantilla anterior. Un trozo de 48³
 * se lee y escribe en un solo tick: guardar es una acción de admin, no de partida.
 */
final class GuardadoPlantilla {
    private static final int COSTE_FIJO = 2048;

    /**
     * El aire no se guarda: la mayor parte de una zona es aire y guardarlo multiplica el tamaño de
     * la plantilla. El reinicio vacía cada capa antes de colocarla.
     */
    private static final List<Block> AIRE = List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR);

    private final ServerLevel nivel;
    private final String dungeon;
    private final Caja zona;
    private final AlmacenPlantillas.Escritura escritura;
    private final List<DatosMarcador> marcadores = new ArrayList<>();
    private int trozos;
    private int entidades;

    GuardadoPlantilla(
            ServerLevel nivel, String dungeon, Caja zona, AlmacenPlantillas.Escritura escritura) {
        this.nivel = nivel;
        this.dungeon = dungeon;
        this.zona = zona;
        this.escritura = escritura;
    }

    /** Pasos del guardado; {@code carga} ya debe haber pedido los chunks. */
    List<Paso> pasos(ZonaCargada carga) {
        List<Paso> pasos = new ArrayList<>();
        pasos.add(Paso.esperar(() -> carga.lista(dungeon)));
        List<Troceado.Trozo> lista =
                Troceado.trozos(zona.ancho(), zona.alto(), zona.largo(), Troceado.LADO_MAXIMO);
        for (Troceado.Trozo trozo : lista) {
            pasos.add(Paso.de((int) trozo.caja().volumen(), () -> capturar(trozo)));
        }
        pasos.add(Paso.de(COSTE_FIJO, this::leerMarcadores));
        pasos.add(Paso.de(COSTE_FIJO, this::capturarEntidades));
        pasos.add(Paso.de(COSTE_FIJO, this::confirmar));
        return pasos;
    }

    int trozos() {
        return trozos;
    }

    int marcadores() {
        return marcadores.size();
    }

    int entidades() {
        return entidades;
    }

    private void capturar(Troceado.Trozo trozo) {
        Caja caja = trozo.caja();
        StructureTemplate plantilla = new StructureTemplate();
        plantilla.fillFromWorld(
                nivel,
                new BlockPos(
                        zona.minX() + caja.minX(),
                        zona.minY() + caja.minY(),
                        zona.minZ() + caja.minZ()),
                new Vec3i(caja.ancho(), caja.alto(), caja.largo()),
                false,
                AIRE);
        plantilla.setAuthor(DolfinCreepers.MOD_ID);
        escritura.trozo(trozo, plantilla);
        trozos++;
    }

    /** Recorre las entidades de bloque de los chunks de la zona: es mucho menos que cada bloque. */
    private void leerMarcadores() {
        BlockPos origen = new BlockPos(zona.minX(), zona.minY(), zona.minZ());
        for (int x = zona.minX() >> 4; x <= zona.maxX() >> 4; x++) {
            for (int z = zona.minZ() >> 4; z <= zona.maxZ() >> 4; z++) {
                for (BlockEntity be : nivel.getChunk(x, z).getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (be instanceof MarcadorMobEntidad marcador
                            && zona.contiene(pos.getX(), pos.getY(), pos.getZ())) {
                        marcador.datos(origen).ifPresentOrElse(marcadores::add, () -> avisar(pos));
                    }
                }
            }
        }
    }

    private void capturarEntidades() {
        List<CompoundTag> capturadas = EntidadesZona.capturarMarcadas(nivel, zona);
        escritura.entidades(capturadas);
        entidades = capturadas.size();
    }

    private void confirmar() {
        escritura.confirmar(
                new ManifiestoPlantilla(
                        ManifiestoPlantilla.VERSION,
                        dungeon,
                        zona,
                        Troceado.LADO_MAXIMO,
                        entidades,
                        System.currentTimeMillis(),
                        marcadores));
    }

    private void avisar(BlockPos pos) {
        DolfinCreepers.LOGGER.warn(
                "event=dungeon_marker_invalid dungeon={} pos={} reason=bad_entity_id",
                dungeon,
                pos.toShortString());
    }
}
