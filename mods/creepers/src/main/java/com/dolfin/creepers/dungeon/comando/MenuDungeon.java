package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.comando.DisposicionMenu.Icono;
import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Menú de cofre de {@code /dungeon} (#23), solo con inventarios vanilla: el cliente no necesita
 * ningún mod. La primera pantalla muestra un icono por dungeon con su estado; al pulsar una se
 * cambia, en el mismo cofre, a sus dificultades, y elegir una sigue el flujo de {@code /dungeon
 * join} ({@link FlujoUnirse}), con la confirmación de hardcore incluida.
 *
 * <p>Los iconos son copias de pantalla: ningún clic llega a la lógica del cofre, así que no se
 * puede sacar ni meter nada, ni con mayúsculas, número, arrastre, soltar o doble clic.
 */
final class MenuDungeon extends ChestMenu {
    private enum Pantalla {
        DUNGEONS,
        DIFICULTADES
    }

    private final ServerPlayer jugador;
    private final FlujoUnirse flujo;
    private final Container contenedor;
    private final int filas;
    private Pantalla pantalla = Pantalla.DUNGEONS;
    private DefinicionDungeon elegida;
    private Optional<Dificultad> confirmando = Optional.empty();

    private MenuDungeon(
            MenuType<?> tipo,
            int id,
            Inventory inventario,
            Container contenedor,
            int filas,
            ServerPlayer jugador,
            FlujoUnirse flujo) {
        super(tipo, id, inventario, contenedor, filas);
        this.jugador = jugador;
        this.flujo = flujo;
        this.contenedor = contenedor;
        this.filas = filas;
        mostrarDungeons();
    }

    /** Abre el menú al jugador. */
    static void abrir(ServerPlayer jugador, FlujoUnirse flujo) {
        int filas = DisposicionMenu.filas(Dungeons.registro().config().dungeons().size());
        jugador.openMenu(
                new SimpleMenuProvider(
                        (id, inventario, p) ->
                                new MenuDungeon(
                                        tipo(filas),
                                        id,
                                        inventario,
                                        new SimpleContainer(filas * DisposicionMenu.COLUMNAS),
                                        filas,
                                        jugador,
                                        flujo),
                        Component.literal("Dungeons")));
    }

    @Override
    public void clicked(int hueco, int boton, ContainerInput entrada, Player quien) {
        boolean enElCofre = hueco >= 0 && hueco < filas * DisposicionMenu.COLUMNAS;
        if (enElCofre && entrada == ContainerInput.PICKUP) {
            pulsar(hueco);
        }
        // Nunca se delega: lo que el cliente haya movido se deshace volviendo a enviar el estado.
        if (quien.containerMenu == this) {
            sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player quien, int hueco) {
        return ItemStack.EMPTY;
    }

    private void pulsar(int hueco) {
        if (pantalla == Pantalla.DUNGEONS) {
            List<DefinicionDungeon> dungeons = ArgumentosDungeon.todas();
            DisposicionMenu.dungeonEn(hueco, dungeons.size())
                    .ifPresent(i -> elegirDungeon(dungeons.get(i)));
        } else if (hueco == DisposicionMenu.huecoVolver(filas)) {
            mostrarDungeons();
        } else {
            DisposicionMenu.dificultadEn(hueco)
                    .filter(elegida.dificultades()::contains)
                    .ifPresent(this::elegirDificultad);
        }
    }

    private void elegirDungeon(DefinicionDungeon dungeon) {
        CommandSourceStack origen = jugador.createCommandSourceStack();
        EstadoVisible visible = ConsultaDungeon.estadoVisible(origen, dungeon);
        switch (visible.clase()) {
            case CERRADA -> avisar("Esta dungeon está cerrada por mantenimiento.");
            case BLOQUEADA -> avisar(motivoBloqueo(origen, dungeon));
            default -> {
                elegida = dungeon;
                confirmando = Optional.empty();
                mostrarDificultades();
            }
        }
    }

    private void elegirDificultad(Dificultad dificultad) {
        FlujoUnirse.Resultado resultado =
                flujo.unirse(jugador.createCommandSourceStack(), jugador, elegida, dificultad);
        switch (resultado) {
            case FlujoUnirse.Resultado.Exito(String texto) -> terminar(texto, ChatFormatting.GREEN);
            case FlujoUnirse.Resultado.Fallo(String texto) -> terminar(texto, ChatFormatting.RED);
            case FlujoUnirse.Resultado.PideConfirmacion p -> {
                confirmando = Optional.of(dificultad);
                avisar(
                        VistaDungeon.avisoHardcore(p.baneoMinutos())
                                + " Pulsa Hardcore otra vez en "
                                + Confirmaciones.VENTANA_MS / 1000
                                + " s para confirmar.");
                mostrarDificultades();
            }
        }
    }

    private void terminar(String texto, ChatFormatting color) {
        jugador.sendSystemMessage(Component.literal(texto).withStyle(color));
        jugador.closeContainer();
    }

    private void avisar(String texto) {
        jugador.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.YELLOW));
    }

    private String motivoBloqueo(CommandSourceStack origen, DefinicionDungeon dungeon) {
        if (!PermisosDungeon.puedeEntrar(origen, dungeon)) {
            return "No tienes permiso para entrar a " + dungeon.nombre() + ".";
        }
        return Dungeons.fila()
                .bloqueo(jugador.getUUID(), dungeon)
                .orElse(dungeon.nombre() + " está bloqueada para ti.");
    }

    private void mostrarDungeons() {
        pantalla = Pantalla.DUNGEONS;
        contenedor.clearContent();
        CommandSourceStack origen = jugador.createCommandSourceStack();
        List<DefinicionDungeon> dungeons = ArgumentosDungeon.todas();
        for (int i = 0; i < Math.min(dungeons.size(), DisposicionMenu.capacidad()); i++) {
            contenedor.setItem(i, iconoDeDungeon(origen, dungeons.get(i)));
        }
    }

    private void mostrarDificultades() {
        pantalla = Pantalla.DIFICULTADES;
        contenedor.clearContent();
        for (int i = 0; i < contenedor.getContainerSize(); i++) {
            contenedor.setItem(i, pieza(Icono.RELLENO, Component.literal(" "), List.of()));
        }
        CommandSourceStack origen = jugador.createCommandSourceStack();
        contenedor.setItem(DisposicionMenu.huecoCabecera(), iconoDeDungeon(origen, elegida));
        for (Dificultad dificultad : Dificultad.values()) {
            contenedor.setItem(
                    DisposicionMenu.huecoDificultad(dificultad), iconoDeDificultad(dificultad));
        }
        contenedor.setItem(
                DisposicionMenu.huecoVolver(filas),
                pieza(
                        Icono.VOLVER,
                        nombre("Volver", ChatFormatting.WHITE),
                        List.of(linea("Elegir otra dungeon", ChatFormatting.GRAY))));
    }

    private ItemStack iconoDeDungeon(CommandSourceStack origen, DefinicionDungeon dungeon) {
        EstadoVisible visible = ConsultaDungeon.estadoVisible(origen, dungeon);
        OptionalLong mejor =
                Dungeons.estadisticas().mejorTiempoSegundos(jugador.getUUID(), dungeon.id());
        List<Component> lore = new ArrayList<>();
        lore.add(linea(visible.texto(), colorDe(visible.clase())));
        VistaDungeon.info(dungeon, mejor).stream()
                .skip(1)
                .forEach(l -> lore.add(linea(l, ChatFormatting.GRAY)));
        return pieza(
                DisposicionMenu.icono(visible.clase()),
                nombre(dungeon.nombre(), ChatFormatting.GOLD),
                lore);
    }

    private ItemStack iconoDeDificultad(Dificultad dificultad) {
        boolean disponible = elegida.dificultades().contains(dificultad);
        boolean pideConfirmar = confirmando.filter(dificultad::equals).isPresent();
        List<Component> lore = new ArrayList<>();
        if (!disponible) {
            lore.add(linea("No disponible en esta dungeon", ChatFormatting.RED));
        } else if (dificultad == Dificultad.HARDCORE) {
            int minutos = Dungeons.registro().config().partida().hardcoreBanMinutos();
            lore.add(linea(VistaDungeon.avisoHardcore(minutos), ChatFormatting.RED));
            lore.add(
                    linea(
                            pideConfirmar ? "Pulsa otra vez para confirmar" : "Pulsa para entrar",
                            pideConfirmar ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        } else {
            lore.add(linea("Pulsa para entrar", ChatFormatting.GRAY));
        }
        String titulo = pideConfirmar ? "Confirmar " + dificultad.id() : dificultad.id();
        return pieza(
                disponible ? DisposicionMenu.icono(dificultad) : Icono.BLOQUEADA,
                nombre(titulo, ChatFormatting.AQUA),
                lore);
    }

    private static ChatFormatting colorDe(EstadoVisible.Clase clase) {
        return switch (clase) {
            case LIBRE -> ChatFormatting.GREEN;
            case EN_PARTIDA, EN_FILA -> ChatFormatting.YELLOW;
            case REINICIANDO -> ChatFormatting.GRAY;
            case BLOQUEADA, CERRADA -> ChatFormatting.RED;
        };
    }

    private static ItemStack pieza(Icono icono, Component nombre, List<Component> lore) {
        ItemStack pieza = new ItemStack(objeto(icono));
        pieza.set(DataComponents.CUSTOM_NAME, nombre);
        pieza.set(DataComponents.LORE, new ItemLore(lore));
        return pieza;
    }

    private static Component nombre(String texto, ChatFormatting color) {
        return Component.literal(texto)
                .withStyle(s -> s.withColor(color).withItalic(false).withBold(true));
    }

    private static Component linea(String texto, ChatFormatting color) {
        return Component.literal(texto).withStyle(s -> s.withColor(color).withItalic(false));
    }

    private static Item objeto(Icono icono) {
        return switch (icono) {
            case LIBRE -> Items.CONCRETE.pick(DyeColor.LIME);
            case OCUPADA -> Items.CONCRETE.pick(DyeColor.RED);
            case EN_FILA -> Items.CONCRETE.pick(DyeColor.YELLOW);
            case REINICIANDO -> Items.CONCRETE.pick(DyeColor.GRAY);
            case BLOQUEADA, CERRADA -> Items.BARRIER;
            case FACIL -> Items.WOODEN_SWORD;
            case MEDIO -> Items.IRON_SWORD;
            case HARDCORE -> Items.WITHER_SKELETON_SKULL;
            case VOLVER -> Items.ARROW;
            case RELLENO -> Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY);
        };
    }

    private static MenuType<ChestMenu> tipo(int filas) {
        return switch (filas) {
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
