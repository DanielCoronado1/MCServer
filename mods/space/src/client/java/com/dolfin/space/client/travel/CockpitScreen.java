package com.dolfin.space.client.travel;

import com.dolfin.space.travel.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CockpitScreen extends AbstractContainerScreen<CockpitMenu> {
  private Button launch, consent, shake;
  private int ticks, previousChecklist;
  private boolean hover;

  public CockpitScreen(CockpitMenu m, Inventory inv, Component title) {
    super(m, inv, title, 300, 235);
  }

  protected void init() {
    super.init();
    launch =
        addRenderableWidget(
            Button.builder(
                    Component.literal("LANZAR"),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 12, topPos + 153, 132, 20)
                .build());
    consent =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Pasajeros"),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .bounds(leftPos + 150, topPos + 153, 138, 20)
                .build());
    shake =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Vibración"),
                    b -> {
                      TravelClientModule.scale(
                          (Math.round(TravelClientModule.scale() * 4) + 1) % 5 / 4d);
                    })
                .bounds(leftPos + 12, topPos + 180, 276, 20)
                .build());
  }

  protected void containerTick() {
    super.containerTick();
    ticks++;
    launch.active = menu.data.get(9) == 1 && menu.data.get(7) < 0;
    consent.active = menu.data.get(7) < 0;
    consent.setMessage(Component.literal("Pasajeros: " + (menu.data.get(10) == 1 ? "sí" : "no")));
    shake.setMessage(
        Component.literal("Vibración: " + Math.round(TravelClientModule.scale() * 100) + "%"));
    int checks = (menu.data.get(2) == 1 ? 1 : 0) | (menu.data.get(0) >= menu.data.get(1) ? 2 : 0);
    if ((checks & ~previousChecklist) != 0) TravelClientModule.beep(false);
    previousChecklist = checks;
    if (ticks % 60 == 0 && menu.data.get(0) < menu.data.get(1)) TravelClientModule.beep(true);
  }

  public boolean keyPressed(KeyEvent key) {
    if (key.key() == InputConstants.KEY_LSHIFT || key.key() == InputConstants.KEY_RSHIFT) {
      ClientPlayNetworking.send(new CockpitActionPayload(2));
      onClose();
      return true;
    }
    if (TravelClientModule.COCKPIT.matches(key)) {
      onClose();
      return true;
    }
    return super.keyPressed(key);
  }

  private String oxygen(int equipped, int percent) {
    return equipped == 0
        ? "sin tanque"
        : percent < 0 ? "tanque (nivel no disponible)" : percent + "%";
  }

  protected void extractLabels(GuiGraphicsExtractor g, int x, int y) {
    g.text(font, title, 12, 10, 0xffeeeeff);
  }

  public void extractContents(GuiGraphicsExtractor g, int x, int y, float dt) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff172330);
    int white = 0xffeeeeff, good = 0xff77eebb, bad = 0xffff6666;
    boolean fuel = menu.data.get(0) >= menu.data.get(1), earth = menu.data.get(11) == 0;
    g.text(font, "Destino: " + (earth ? "Kepler" : "Overworld"), leftPos + 12, topPos + 30, white);
    g.text(
        font,
        "Combustible: " + menu.data.get(0) + "% / mínimo " + menu.data.get(1) + "%",
        leftPos + 12,
        topPos + 47,
        fuel ? good : bad);
    g.text(
        font,
        "Oxígeno piloto: " + oxygen(menu.data.get(3), menu.data.get(4)),
        leftPos + 12,
        topPos + 64,
        white);
    g.text(
        font,
        "Pasajero: "
            + (menu.data.get(5) == 0
                ? "sin pasajero"
                : oxygen(menu.data.get(5) == 2 ? 1 : 0, menu.data.get(6))),
        leftPos + 12,
        topPos + 81,
        white);
    g.text(
        font,
        (menu.data.get(2) == 1 ? "✓ " : "× ")
            + (earth ? "Plataforma válida / ascenso libre" : "Suelo y ascenso libres"),
        leftPos + 12,
        topPos + 99,
        menu.data.get(2) == 1 ? good : bad);
    g.text(
        font,
        (fuel ? "✓ " : "× ") + "Combustible de viaje",
        leftPos + 12,
        topPos + 114,
        fuel ? good : bad);
    g.text(
        font,
        menu.data.get(3) == 1 ? "✓ Tanque equipado" : "! Aviso: equipa un tanque de oxígeno",
        leftPos + 12,
        topPos + 129,
        menu.data.get(3) == 1 ? good : 0xffffcc66);
    if (menu.data.get(7) == 0)
      g.text(
          font,
          "Despegue: " + (menu.data.get(8) + 19) / 20 + " s",
          leftPos + 12,
          topPos + 140,
          white);
    g.text(
        font,
        "R: cerrar / abrir · Shift: bajar y cancelar",
        leftPos + 12,
        topPos + 214,
        0xffaabbcc);
    boolean over = launch.isHovered() || consent.isHovered() || shake.isHovered();
    if (over && !hover) TravelClientModule.beep(false);
    hover = over;
    super.extractContents(g, x, y, dt);
  }
}
