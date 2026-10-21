package com.dolfin.creepers.dungeon.botin;

import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaTerminada;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Controla el botín individual por cofre en las partidas de dungeons (#18). Intercepta la apertura
 * de cofres para ofrecer a cada jugador su propio contenedor aislado, evitando que las tolvas
 * extraigan objetos o que un jugador deje sin botín a los demás.
 */
public final class BotinDungeon {
    private final RegistroDungeons registro;
    private final AlmacenBotin almacen;
    private final Supplier<MinecraftServer> server;

    public BotinDungeon(
            RegistroDungeons registro, AlmacenBotin almacen, Supplier<MinecraftServer> server) {
        this.registro = registro;
        this.almacen = almacen;
        this.server = server;
    }

    public AlmacenBotin almacen() {
        return almacen;
    }

    /** Suscribe la limpieza al terminar partidas y la interacción con cofres. */
    public void registrar(BusEventos bus) {
        bus.suscribir(PartidaTerminada.class, e -> almacen.limpiarPartida(e.partida().id()));
        UseBlockCallback.EVENT.register(this::alUsarBloque);
    }

    /** Intercepta la apertura de cofres en la dimensión de dungeons. */
    private InteractionResult alUsarBloque(
            Player jugador, Level nivel, InteractionHand mano, BlockHitResult impacto) {
        if (mano != InteractionHand.MAIN_HAND || !(jugador instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!DimensionDungeons.es(nivel) || !(nivel instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = impacto.getBlockPos();
        BlockState estado = serverLevel.getBlockState(pos);
        if (!esContenedorBotin(estado.getBlock())) {
            return InteractionResult.PASS;
        }

        Optional<Partida> partidaOpt = registro.partidaDe(serverPlayer.getUUID());
        if (partidaOpt.isEmpty()) {
            return InteractionResult.PASS;
        }
        Partida partida = partidaOpt.get();
        if (!partida.dungeon().zona().contiene(pos.getX(), pos.getY(), pos.getZ())) {
            return InteractionResult.PASS;
        }

        BlockPos posNormalizada = AlmacenBotin.normalizarPosicion(estado, pos);
        abrirCofreIndividual(serverLevel, serverPlayer, partida, estado, posNormalizada);
        return InteractionResult.SUCCESS;
    }

    /** Abre o genera el contenedor individual del jugador para este cofre. */
    private void abrirCofreIndividual(
            ServerLevel nivel,
            ServerPlayer jugador,
            Partida partida,
            BlockState estado,
            BlockPos pos) {
        int capacidad = AlmacenBotin.capacidad(estado);
        boolean yaVacio = almacen.yaVaciado(partida.id(), pos, jugador.getUUID());

        SimpleContainer contenedor =
                almacen.obtener(partida.id(), pos, jugador.getUUID())
                        .orElseGet(
                                () -> {
                                    SimpleContainer nuevo = new SimpleContainer(capacidad);
                                    generarBotin(nivel, jugador, partida, pos, nuevo);
                                    almacen.guardar(partida.id(), pos, jugador.getUUID(), nuevo);
                                    return nuevo;
                                });

        // Indicación visual y sonora si el cofre ya había sido vaciado por el jugador
        if (yaVacio || contenedor.isEmpty()) {
            nivel.sendParticles(
                    ParticleTypes.SMOKE,
                    pos.getX() + 0.5,
                    pos.getY() + 0.6,
                    pos.getZ() + 0.5,
                    5,
                    0.2,
                    0.2,
                    0.2,
                    0.02);
            nivel.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 1.4f);
        } else {
            nivel.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.7f, 1.0f);
        }

        // Abre el menú tipo cofre para el jugador
        Component titulo = Component.translatable(estado.getBlock().getDescriptionId());
        jugador.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) ->
                                capacidad > 27
                                        ? ChestMenu.sixRows(id, inv, contenedor)
                                        : ChestMenu.threeRows(id, inv, contenedor),
                        titulo));
    }

    /**
     * Llena el contenedor con la tabla de botín de la dungeon y dificultad o con recompensas base.
     */
    private void generarBotin(
            ServerLevel nivel,
            ServerPlayer jugador,
            Partida partida,
            BlockPos pos,
            SimpleContainer contenedor) {
        String dungeonId = partida.dungeon().id();
        Dificultad dificultad = partida.dificultad();
        Identifier idTabla =
                Identifier.fromNamespaceAndPath(
                        "dolfincreepers", "dungeon/" + dungeonId + "/" + dificultad.id());
        ResourceKey<LootTable> clave = ResourceKey.create(Registries.LOOT_TABLE, idTabla);

        MinecraftServer s = server.get();
        if (s != null) {
            LootTable tabla = s.reloadableRegistries().getLootTable(clave);
            if (tabla != LootTable.EMPTY) {
                LootParams params =
                        new LootParams.Builder(nivel)
                                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                                .withOptionalParameter(LootContextParams.THIS_ENTITY, jugador)
                                .withLuck(jugador.getLuck())
                                .create(LootContextParamSets.CHEST);
                tabla.fill(contenedor, params, nivel.getRandom().nextLong());
                return;
            }
        }

        // Si no hay tabla personalizada, usa botín de reserva según dificultad
        aplicarBotinPorDefecto(contenedor, dificultad);
    }

    /** Botín básico de respaldo cuando no hay tabla de datos específica para la dungeon. */
    private static void aplicarBotinPorDefecto(SimpleContainer contenedor, Dificultad dificultad) {
        contenedor.addItem(new ItemStack(Items.BREAD, 4));
        contenedor.addItem(new ItemStack(Items.TORCH, 8));
        switch (dificultad) {
            case FACIL -> {
                contenedor.addItem(new ItemStack(Items.IRON_INGOT, 2));
                contenedor.addItem(new ItemStack(Items.ARROW, 8));
            }
            case MEDIO -> {
                contenedor.addItem(new ItemStack(Items.IRON_INGOT, 4));
                contenedor.addItem(new ItemStack(Items.GOLD_INGOT, 2));
                contenedor.addItem(new ItemStack(Items.ARROW, 16));
                contenedor.addItem(new ItemStack(Items.GOLDEN_APPLE, 1));
            }
            case HARDCORE -> {
                contenedor.addItem(new ItemStack(Items.DIAMOND, 2));
                contenedor.addItem(new ItemStack(Items.GOLD_INGOT, 6));
                contenedor.addItem(new ItemStack(Items.GOLDEN_APPLE, 2));
                contenedor.addItem(new ItemStack(Items.ARROW, 32));
            }
        }
    }

    private static boolean esContenedorBotin(Block bloque) {
        return bloque instanceof ChestBlock || bloque instanceof BarrelBlock;
    }
}
