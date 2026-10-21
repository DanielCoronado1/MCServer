package com.dolfin.creepers.dungeon.reglas;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.reglas.DecisionesReglas.UsoObjeto;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Objetos y vuelo bloqueados en la dimensión de dungeons: los de {@code objetosProhibidos} (por
 * defecto perla de ender y fruta coral) no se usan, el cohete solo vale como munición de ballesta y
 * los élitros no despegan (mixin {@code ElitrosDungeonMixin}, que llama a {@link #permitePlaneo}).
 * Cada bloqueo avisa en la barra de acción. Tótems, comida y pociones no se tocan. Los
 * administradores en creativo o espectador quedan fuera de las reglas.
 */
final class ReglasObjetos {
    private ReglasObjetos() {}

    static void registrar() {
        UseItemCallback.EVENT.register(ReglasObjetos::alUsarObjeto);
        UseBlockCallback.EVENT.register(
                (jugador, nivel, mano, impacto) -> alUsarSobreBloque(jugador, nivel, mano));
    }

    /**
     * Si el jugador puede despegar con élitros; avisa si no. Se llama desde el mixin al intentar
     * planear.
     */
    static boolean permitePlaneo(Player jugador) {
        if (!(jugador instanceof ServerPlayer p) || !sujeto(p)) {
            return true;
        }
        avisar(p, "No puedes planear en la dungeon.");
        return false;
    }

    private static InteractionResult alUsarObjeto(
            Player jugador, Level nivel, InteractionHand mano) {
        if (nivel.isClientSide() || !(jugador instanceof ServerPlayer p) || !sujeto(p)) {
            return InteractionResult.PASS;
        }
        InteractionHand otra =
                mano == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND
                        : InteractionHand.MAIN_HAND;
        UsoObjeto uso =
                DecisionesReglas.usoEnAire(
                        idDe(p.getItemInHand(mano)),
                        p.getItemInHand(otra).is(Items.CROSSBOW),
                        prohibidos(p));
        return resolver(p, uso, mano);
    }

    private static InteractionResult alUsarSobreBloque(
            Player jugador, Level nivel, InteractionHand mano) {
        if (nivel.isClientSide() || !(jugador instanceof ServerPlayer p) || !sujeto(p)) {
            return InteractionResult.PASS;
        }
        return resolver(p, DecisionesReglas.usoEnBloque(idDe(p.getItemInHand(mano))), mano);
    }

    private static InteractionResult resolver(ServerPlayer p, UsoObjeto uso, InteractionHand mano) {
        String mensaje =
                switch (uso) {
                    case PERMITIDO -> null;
                    case PROHIBIDO ->
                            "No puedes usar "
                                    + p.getItemInHand(mano).getHoverName().getString()
                                    + " en la dungeon.";
                    case COHETE_SIN_BALLESTA ->
                            "Los cohetes solo sirven como munición de ballesta.";
                };
        if (mensaje == null) {
            return InteractionResult.PASS;
        }
        avisar(p, mensaje);
        return InteractionResult.FAIL;
    }

    private static List<String> prohibidos(ServerPlayer jugador) {
        return Dungeons.registro()
                .partidaDe(jugador.getUUID())
                .map(partida -> partida.config().partida().objetosProhibidos())
                .orElseGet(() -> Dungeons.config().actual().partida().objetosProhibidos());
    }

    private static boolean sujeto(ServerPlayer jugador) {
        return DecisionesReglas.sujetoAReglas(
                DimensionDungeons.es(jugador.level()),
                jugador.isCreative() || jugador.isSpectator());
    }

    private static String idDe(ItemStack pila) {
        return BuiltInRegistries.ITEM.getKey(pila.getItem()).toString();
    }

    private static void avisar(ServerPlayer jugador, String texto) {
        jugador.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.RED), true);
    }
}
