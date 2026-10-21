package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaTerminada;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tumbas de dificultad media (#16): al morir, los objetos del jugador van a una lápida junto a
 * donde cayó. Cualquier miembro de su partida puede abrirla; al terminar la partida lo que quede
 * vuelve al dueño si el grupo ganó y se pierde si perdió ({@link ReglasMuerte#devolverTumba}). Tras
 * una caída del servidor nadie pierde nada: todas las tumbas vuelven a sus dueños.
 */
public final class Tumbas {
    /** Bloques que se suben como mucho desde el punto de muerte buscando un hueco. */
    private static final int SUBIDA_MAXIMA = 4;

    /** Radio horizontal de búsqueda de hueco alrededor del punto de muerte. */
    private static final int RADIO_BUSQUEDA = 2;

    private final RegistroDungeons registro;
    private final Supplier<MinecraftServer> server;

    public Tumbas(RegistroDungeons registro, Supplier<MinecraftServer> server) {
        this.registro = registro;
        this.server = server;
    }

    /** Retira las tumbas de cada partida que termina. */
    public void suscribir(BusEventos bus) {
        bus.suscribir(
                PartidaTerminada.class,
                e -> {
                    MinecraftServer s = server.get();
                    if (s != null) {
                        cerrar(s, e.partida(), e.resultado());
                    }
                });
    }

    /**
     * Pasa todos los objetos del jugador a una tumba nueva junto a donde está (debe hacerse antes
     * de moverlo a su checkpoint). Sin objetos no crea nada.
     */
    public Optional<Tumba> crear(ServerPlayer jugador, Partida partida) {
        List<ItemStack> objetos = Inventarios.retirarObjetos(jugador);
        Optional<ServerLevel> nivel = DimensionDungeons.nivel(jugador.level().getServer());
        if (objetos.isEmpty() || nivel.isEmpty()) {
            return Optional.empty();
        }
        DatosMuerte datos = DatosMuerte.de(nivel.get().getServer());
        BlockPos pos = lugarLibre(nivel.get(), datos, jugador, partida);
        nivel.get().setBlock(pos, BloqueTumba.bloque().defaultBlockState(), Block.UPDATE_ALL);
        Tumba tumba =
                Tumba.nueva(
                        UUID.randomUUID(),
                        jugador.getUUID(),
                        jugador.getName().getString(),
                        partida.id(),
                        pos,
                        objetos);
        datos.agregarTumba(tumba);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_grave_created player={} dungeon={} pos={} stacks={} match={}",
                tumba.nombreDueno(),
                partida.dungeon().id(),
                pos.toShortString(),
                objetos.size(),
                partida.id());
        return Optional.of(tumba);
    }

    /** Abre el cofre de la tumba en {@code pos} si el jugador juega la partida de la tumba. */
    public void abrir(ServerPlayer jugador, BlockPos pos) {
        if (!DimensionDungeons.es(jugador.level())) {
            return;
        }
        Optional<Tumba> tumba = DatosMuerte.de(jugador.level().getServer()).tumbaEn(pos);
        if (tumba.isEmpty()) {
            aviso(jugador, "Esta tumba está vacía");
            return;
        }
        if (!puedeAbrir(jugador.getUUID(), tumba.get())) {
            aviso(jugador, "Solo el grupo de " + tumba.get().nombreDueno() + " puede abrirla");
            return;
        }
        jugador.openMenu(
                new SimpleMenuProvider(
                        (id, inventario, p) ->
                                ChestMenu.sixRows(id, inventario, tumba.get().contenido()),
                        Component.literal("Tumba de " + tumba.get().nombreDueno())));
    }

    /** Entrega al conectarse lo que le quedó pendiente de una tumba devuelta. */
    public void alConectarse(ServerPlayer jugador) {
        List<ItemStack> objetos =
                DatosMuerte.de(jugador.level().getServer()).recogerEntrega(jugador.getUUID());
        if (!objetos.isEmpty()) {
            Inventarios.entregar(jugador, objetos);
            avisoBueno(jugador, "Recuperas los objetos de tu tumba");
        }
    }

    /**
     * Tras arrancar no hay partidas: toda tumba guardada es de una partida interrumpida por una
     * caída y vuelve a su dueño.
     */
    public void recuperarTrasArranque(MinecraftServer s) {
        for (Tumba tumba : DatosMuerte.de(s).tumbas()) {
            if (registro.partidas().stream().noneMatch(p -> p.id().equals(tumba.partida()))) {
                retirar(s, tumba, true);
            }
        }
    }

    private void cerrar(MinecraftServer s, Partida partida, ResultadoPartida resultado) {
        boolean devolver = ReglasMuerte.devolverTumba(resultado);
        for (Tumba tumba : DatosMuerte.de(s).tumbas()) {
            if (tumba.partida().equals(partida.id())) {
                retirar(s, tumba, devolver);
            }
        }
    }

    private void retirar(MinecraftServer s, Tumba tumba, boolean devolver) {
        List<ItemStack> objetos = tumba.retirar();
        DatosMuerte datos = DatosMuerte.de(s);
        datos.quitarTumba(tumba);
        quitarBloque(s, tumba.pos());
        DolfinCreepers.LOGGER.info(
                "event=dungeon_grave_closed player={} returned={} stacks={} match={}",
                tumba.nombreDueno(),
                devolver,
                objetos.size(),
                tumba.partida());
        ServerPlayer dueno = s.getPlayerList().getPlayer(tumba.dueno());
        if (!devolver) {
            if (dueno != null && !objetos.isEmpty()) {
                aviso(dueno, "Los objetos de tu tumba se perdieron con la partida");
            }
        } else if (dueno != null) {
            Inventarios.entregar(dueno, objetos);
            if (!objetos.isEmpty()) {
                avisoBueno(dueno, "Recuperas los objetos de tu tumba");
            }
        } else {
            datos.dejarEntrega(tumba.dueno(), objetos);
        }
    }

    private boolean puedeAbrir(UUID jugador, Tumba tumba) {
        return registro.partidaDe(jugador).map(p -> p.id().equals(tumba.partida())).orElse(false);
    }

    /**
     * Primer hueco reemplazable (aire, agua, lava…) dentro de la zona, empezando en el punto de
     * muerte y subiendo; luego alrededor. Si no hay ninguno, el punto de reaparición del jugador.
     */
    private static BlockPos lugarLibre(
            ServerLevel nivel, DatosMuerte datos, ServerPlayer jugador, Partida partida) {
        Zona zona = partida.dungeon().zona();
        BlockPos inicio = dentroDe(zona, jugador.blockPosition());
        return candidatos(inicio)
                .filter(p -> zona.contiene(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5))
                .filter(p -> datos.tumbaEn(p).isEmpty())
                .filter(p -> nivel.getBlockState(p).canBeReplaced())
                .findFirst()
                .orElseGet(() -> reaparicion(partida, jugador.getUUID()));
    }

    private static Stream<BlockPos> candidatos(BlockPos inicio) {
        Stream<BlockPos> encima = Stream.iterate(inicio, p -> p.above()).limit(SUBIDA_MAXIMA + 1L);
        Stream<BlockPos> alrededor =
                BlockPos.betweenClosedStream(
                                inicio.offset(-RADIO_BUSQUEDA, 0, -RADIO_BUSQUEDA),
                                inicio.offset(RADIO_BUSQUEDA, SUBIDA_MAXIMA, RADIO_BUSQUEDA))
                        .map(BlockPos::immutable);
        return Stream.concat(encima, alrededor);
    }

    /** Lleva el punto de muerte dentro de la zona (caída al vacío, empujón fuera). */
    private static BlockPos dentroDe(Zona zona, BlockPos pos) {
        return new BlockPos(
                Mth.clamp(pos.getX(), zona.minX(), zona.maxX()),
                Mth.clamp(pos.getY(), zona.minY() + 1, zona.maxY()),
                Mth.clamp(pos.getZ(), zona.minZ(), zona.maxZ()));
    }

    private static BlockPos reaparicion(Partida partida, UUID jugador) {
        Punto punto = partida.puntoDeReaparicion(jugador);
        return BlockPos.containing(punto.x(), punto.y(), punto.z());
    }

    private static void quitarBloque(MinecraftServer s, BlockPos pos) {
        DimensionDungeons.nivel(s)
                .filter(nivel -> BloqueTumba.es(nivel.getBlockState(pos)))
                .ifPresent(nivel -> nivel.removeBlock(pos, false));
    }

    private static void aviso(ServerPlayer jugador, String texto) {
        jugador.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.RED));
    }

    private static void avisoBueno(ServerPlayer jugador, String texto) {
        jugador.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.GREEN));
    }
}
