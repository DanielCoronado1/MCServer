package com.dolfin.space.client.ships;
import com.dolfin.space.ships.ShipsModule;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
public final class ShipsClientModule {public static void init(){MenuScreens.register(ShipsModule.MENU,AssemblyScreen::new);EntityRendererRegistry.register(ShipsModule.SHIP,SpaceshipRenderer::new);}}
