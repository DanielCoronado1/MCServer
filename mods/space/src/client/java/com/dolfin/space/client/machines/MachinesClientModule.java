package com.dolfin.space.client.machines;

import com.dolfin.space.machines.MachinesModule;

import net.minecraft.client.gui.screens.MenuScreens;

public final class MachinesClientModule {
    public static void init() {
        MenuScreens.register(MachinesModule.MENU, MachineScreen::new);
    }
}
