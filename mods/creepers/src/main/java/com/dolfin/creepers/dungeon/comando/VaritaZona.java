package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.comando.SeleccionesZona.Esquina;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Herramienta para marcar la zona de una dungeon: con la varita en la mano, clic izquierdo sobre un
 * bloque marca la primera esquina y clic derecho la segunda; {@code /dungeon admin setzone} las
 * convierte en zona. Solo funciona con {@link PermisosDungeon#ADMIN} y dentro de la dimensión de
 * dungeons. Mientras se sostiene no rompe ni usa bloques, también en el cliente, para no mostrar
 * romper bloques que el servidor no rompe.
 */
final class VaritaZona {
    private static final String CLAVE = "dolfin_dungeon_varita";
    private static final SeleccionesZona SELECCIONES = new SeleccionesZona();

    private VaritaZona() {}

    /** Las esquinas marcadas por los administradores. */
    static SeleccionesZona selecciones() {
        return SELECCIONES;
    }

    /** Engancha los clics de la varita; se llama una vez al arrancar el mod. */
    static void registrarEventos() {
        AttackBlockCallback.EVENT.register(
                (jugador, nivel, mano, pos, cara) ->
                        clic(jugador, nivel, mano, pos, true)
                                ? InteractionResult.FAIL
                                : InteractionResult.PASS);
        UseBlockCallback.EVENT.register(
                (jugador, nivel, mano, impacto) ->
                        clic(jugador, nivel, mano, impacto.getBlockPos(), false)
                                ? InteractionResult.SUCCESS
                                : InteractionResult.PASS);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SELECCIONES.olvidar(handler.getPlayer().getUUID()));
    }

    /** Una varita nueva: un hacha dorada marcada en sus datos personalizados. */
    static ItemStack crear() {
        ItemStack varita = new ItemStack(Items.GOLDEN_AXE);
        varita.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Varita de zona de dungeons")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        CustomData.update(DataComponents.CUSTOM_DATA, varita, tag -> tag.putBoolean(CLAVE, true));
        return varita;
    }

    static boolean esVarita(ItemStack pila) {
        CustomData datos = pila.get(DataComponents.CUSTOM_DATA);
        return datos != null && datos.copyTag().contains(CLAVE);
    }

    /**
     * @return {@code true} si el clic era de la varita y no debe seguir (se marcó, o se ignora
     *     porque quien la sostiene no es administrador)
     */
    private static boolean clic(
            Player jugador, Level nivel, InteractionHand mano, BlockPos pos, boolean primera) {
        if (!esVarita(jugador.getItemInHand(mano))) {
            return false;
        }
        if (nivel.isClientSide()) {
            return true;
        }
        if (jugador instanceof ServerPlayer admin
                && PermisosDungeon.esAdmin(admin.createCommandSourceStack())) {
            marcar(admin, nivel, pos, primera);
            return true;
        }
        return false;
    }

    private static void marcar(ServerPlayer admin, Level nivel, BlockPos pos, boolean primera) {
        if (!DimensionDungeons.es(nivel)) {
            aviso(admin, "Marca las esquinas dentro de la dimensión de dungeons.");
            return;
        }
        Esquina esquina = new Esquina(pos.getX(), pos.getY(), pos.getZ());
        if (primera) {
            SELECCIONES.marcarPrimera(admin.getUUID(), esquina);
        } else {
            SELECCIONES.marcarSegunda(admin.getUUID(), esquina);
        }
        String lugar = esquina.x() + " " + esquina.y() + " " + esquina.z();
        aviso(admin, (primera ? "Esquina 1: " : "Esquina 2: ") + lugar);
        SELECCIONES
                .zona(admin.getUUID())
                .ifPresent(
                        z ->
                                aviso(
                                        admin,
                                        "Zona de "
                                                + (z.maxX() - z.minX() + 1)
                                                + "x"
                                                + (z.maxY() - z.minY() + 1)
                                                + "x"
                                                + (z.maxZ() - z.minZ() + 1)
                                                + " bloques: usa /dungeon admin setzone <id>"));
    }

    private static void aviso(ServerPlayer admin, String texto) {
        admin.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.AQUA));
    }
}
