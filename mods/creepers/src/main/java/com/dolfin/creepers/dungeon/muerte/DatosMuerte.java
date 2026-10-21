package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que la muerte en dungeons (#16) debe conservar en disco, en el overworld:
 *
 * <ul>
 *   <li>las tumbas abiertas y su contenido;
 *   <li>objetos por entregar a jugadores que estaban desconectados al terminar su partida;
 *   <li>jugadores caídos en hardcore estando desconectados, a los que se vacía el inventario al
 *       volver.
 * </ul>
 */
public final class DatosMuerte extends SavedData {
    public static final Codec<DatosMuerte> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Tumba.CODEC
                                                    .listOf()
                                                    .optionalFieldOf("tumbas", List.of())
                                                    .forGetter(d -> List.copyOf(d.tumbas.values())),
                                            Codec.unboundedMap(
                                                            UUIDUtil.STRING_CODEC,
                                                            ItemStack.CODEC.listOf())
                                                    .optionalFieldOf("entregasPendientes", Map.of())
                                                    .forGetter(d -> d.entregasPendientes),
                                            UUIDUtil.STRING_CODEC
                                                    .listOf()
                                                    .optionalFieldOf(
                                                            "vaciadosPendientes", List.of())
                                                    .forGetter(
                                                            d -> List.copyOf(d.vaciadosPendientes)))
                                    .apply(i, DatosMuerte::new));

    public static final SavedDataType<DatosMuerte> TIPO =
            new SavedDataType<>(
                    DolfinCreepers.id("dungeons_muerte"), DatosMuerte::new, CODEC, null);

    private final Map<BlockPos, Tumba> tumbas = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> entregasPendientes = new HashMap<>();
    private final Set<UUID> vaciadosPendientes;

    public DatosMuerte() {
        this(List.of(), Map.of(), List.of());
    }

    private DatosMuerte(
            List<Tumba> tumbas, Map<UUID, List<ItemStack>> entregas, List<UUID> vaciados) {
        tumbas.forEach(this::vigilar);
        entregas.forEach((jugador, objetos) -> entregasPendientes.put(jugador, copia(objetos)));
        this.vaciadosPendientes = new LinkedHashSet<>(vaciados);
    }

    /** Carga (o crea) los datos del mundo. */
    public static DatosMuerte de(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TIPO);
    }

    public Optional<Tumba> tumbaEn(BlockPos pos) {
        return Optional.ofNullable(tumbas.get(pos));
    }

    public Collection<Tumba> tumbas() {
        return List.copyOf(tumbas.values());
    }

    public void agregarTumba(Tumba tumba) {
        vigilar(tumba);
        setDirty();
    }

    public void quitarTumba(Tumba tumba) {
        if (tumbas.remove(tumba.pos(), tumba)) {
            setDirty();
        }
    }

    /** Suma objetos a la entrega pendiente de un jugador desconectado. */
    public void dejarEntrega(UUID jugador, List<ItemStack> objetos) {
        if (!objetos.isEmpty()) {
            entregasPendientes.computeIfAbsent(jugador, j -> new ArrayList<>()).addAll(objetos);
            setDirty();
        }
    }

    /** Quita y devuelve la entrega pendiente de un jugador. */
    public List<ItemStack> recogerEntrega(UUID jugador) {
        List<ItemStack> objetos = entregasPendientes.remove(jugador);
        if (objetos == null) {
            return List.of();
        }
        setDirty();
        return objetos;
    }

    /** Marca a un jugador desconectado para vaciarle el inventario al volver. */
    public void marcarVaciado(UUID jugador) {
        if (vaciadosPendientes.add(jugador)) {
            setDirty();
        }
    }

    /** Quita la marca de vaciado; true si la tenía. */
    public boolean quitarVaciado(UUID jugador) {
        boolean tenia = vaciadosPendientes.remove(jugador);
        if (tenia) {
            setDirty();
        }
        return tenia;
    }

    /** Cualquier cambio dentro del cofre de una tumba se guardará. */
    private void vigilar(Tumba tumba) {
        tumbas.put(tumba.pos(), tumba);
        tumba.vigilarCambios(this::setDirty);
    }

    private static List<ItemStack> copia(List<ItemStack> objetos) {
        return new ArrayList<>(objetos);
    }
}
