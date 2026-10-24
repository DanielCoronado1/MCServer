package com.dolfin.space.client.travel;

import com.dolfin.space.travel.FlightProfile;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Star transit shown while the server moves the ship between dimensions. It stays open until the
 * descent phase arrives; the timeout only covers a lost phase packet.
 */
public final class StarTransitionScreen extends Screen {
    private static final int TIMEOUT_TICKS =
            FlightProfile.TRANSITION_TICKS + FlightProfile.TRANSIT_WAIT_TICKS + 20;

    private int ticks;

    public StarTransitionScreen() {
        super(Component.translatable("space.ui.transit"));
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void tick() {
        if (++ticks >= TIMEOUT_TICKS) minecraft.gui.setScreen(null);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        g.fill(0, 0, width, height, 0xff020612);
        // Stars speed up over the first two seconds of the jump, then keep cruising.
        float t = ticks + dt;
        float speed = 1 + 3 * Math.min(1, t / 40f);
        for (int i = 0; i < 150; i++) {
            int sx = Math.floorMod(i * 7919, width),
                    sy = Math.floorMod((int) (i * 3571 + t * speed * (2 + i % 4)), height);
            g.fill(sx, sy, sx + 1, sy + 2 + (int) (speed * (1 + i % 5)), 0xffdcecff);
        }
        g.centeredText(font, title, width / 2, height / 2, 0xffeeeeff);
    }
}
