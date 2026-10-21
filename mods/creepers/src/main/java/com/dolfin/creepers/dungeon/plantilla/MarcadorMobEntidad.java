package com.dolfin.creepers.dungeon.plantilla;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/**
 * Datos de un {@link MarcadorMob}: {@code entidad} (id), {@code cantidad} (1–64, por defecto 1) y
 * {@code nbt} (compuesto opcional con datos de la entidad, como en {@code /summon}). Se guardan con
 * el bloque en la plantilla y en el manifiesto.
 */
public final class MarcadorMobEntidad extends BlockEntity {
    private static final String ENTIDAD_POR_DEFECTO = "minecraft:zombie";

    private String entidad = ENTIDAD_POR_DEFECTO;
    private int cantidad = 1;
    private CompoundTag nbt = new CompoundTag();

    public MarcadorMobEntidad(BlockPos pos, BlockState estado) {
        super(ContenidoPlantillas.MARCADOR_MOB_ENTIDAD, pos, estado);
    }

    /**
     * Datos del marcador con su posición relativa a {@code origen} (la esquina mínima de la zona);
     * vacío si el id de entidad no es válido.
     */
    public Optional<DatosMarcador> datos(BlockPos origen) {
        if (!DatosMarcador.idValido(entidad)) {
            return Optional.empty();
        }
        BlockPos rel = worldPosition.subtract(origen);
        return Optional.of(
                new DatosMarcador(
                        rel.getX(),
                        rel.getY(),
                        rel.getZ(),
                        entidad,
                        cantidad,
                        nbt.isEmpty() ? "" : nbt.toString()));
    }

    /** Resumen para el admin que lo inspecciona. */
    public String descripcion() {
        return "Marcador: " + cantidad + " × " + entidad + (nbt.isEmpty() ? "" : " " + nbt);
    }

    @Override
    protected void loadAdditional(ValueInput entrada) {
        super.loadAdditional(entrada);
        entidad = entrada.getStringOr("entidad", ENTIDAD_POR_DEFECTO);
        cantidad = DatosMarcador.cantidadValida(entrada.getIntOr("cantidad", 1));
        nbt = entrada.read("nbt", CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }

    @Override
    protected void saveAdditional(ValueOutput salida) {
        super.saveAdditional(salida);
        salida.putString("entidad", entidad);
        salida.putInt("cantidad", cantidad);
        if (!nbt.isEmpty()) {
            salida.store("nbt", CompoundTag.CODEC, nbt);
        }
    }
}
