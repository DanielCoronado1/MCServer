package com.dolfin.creepers.dungeon.muerte;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Operaciones sobre el inventario de un jugador al morir o al recuperar su tumba. */
final class Inventarios {
    private Inventarios() {}

    /**
     * Quita al jugador todos sus objetos sin soltarlos al suelo: inventario, armadura, mano
     * secundaria, el objeto del cursor y la rejilla de fabricación.
     */
    static List<ItemStack> retirarObjetos(ServerPlayer jugador) {
        List<ItemStack> objetos = new ArrayList<>();
        Inventory inventario = jugador.getInventory();
        for (int i = 0; i < inventario.getContainerSize(); i++) {
            agregarSiHay(objetos, inventario.removeItemNoUpdate(i));
        }
        agregarSiHay(objetos, jugador.containerMenu.getCarried());
        jugador.containerMenu.setCarried(ItemStack.EMPTY);
        var rejilla = jugador.inventoryMenu.getCraftSlots();
        for (int i = 0; i < rejilla.getContainerSize(); i++) {
            agregarSiHay(objetos, rejilla.removeItemNoUpdate(i));
        }
        jugador.inventoryMenu.broadcastChanges();
        return objetos;
    }

    /** Hardcore: el jugador pierde todos sus objetos y su experiencia, sin soltar nada. */
    static void vaciar(ServerPlayer jugador) {
        retirarObjetos(jugador);
        jugador.setExperienceLevels(0);
        jugador.setExperiencePoints(0);
        jugador.totalExperience = 0;
    }

    /** Mete los objetos en el inventario; lo que no cabe cae a sus pies. */
    static void entregar(ServerPlayer jugador, List<ItemStack> objetos) {
        for (ItemStack objeto : objetos) {
            jugador.getInventory().placeItemBackInInventory(objeto, Prediction.SERVER_ONLY);
        }
    }

    private static void agregarSiHay(List<ItemStack> objetos, ItemStack objeto) {
        if (!objeto.isEmpty()) {
            objetos.add(objeto);
        }
    }
}
