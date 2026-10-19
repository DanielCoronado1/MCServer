package com.dolfin.space.client.travel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StarTransitionScreen extends Screen {
  private int ticks;

  public StarTransitionScreen() {
    super(Component.literal("Tránsito estelar"));
  }

  public boolean isPauseScreen() {
    return false;
  }

  public void tick() {
    if (++ticks >= 22) minecraft.gui.setScreen(null);
  }

  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
    g.fill(0, 0, width, height, 0xff020612);
    for (int i = 0; i < 150; i++) {
      int sx = Math.floorMod(i * 7919, width),
          sy = Math.floorMod(i * 3571 + ticks * (2 + i % 4), height);
      g.fill(sx, sy, sx + 1, sy + 2 + i % 5, 0xffdcecff);
    }
    g.centeredText(font, title, width / 2, height / 2, 0xffeeeeff);
  }
}
