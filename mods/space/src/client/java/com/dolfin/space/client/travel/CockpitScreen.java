package com.dolfin.space.client.travel;

import com.dolfin.space.ships.*;
import com.dolfin.space.travel.*;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CockpitScreen extends AbstractContainerScreen<CockpitMenu> {
    private Button launch, consent, shake;
    private int ticks, previousChecklist;
    private boolean hover;

    private static Component tr(String key, Object... args) {
        return Component.translatable("space.ui." + key, args);
    }

    public CockpitScreen(CockpitMenu m, Inventory inv, Component title) {
        super(m, inv, title, 326, 235);
    }

    protected void init() {
        super.init();
        launch =
                addRenderableWidget(
                        Button.builder(
                                        tr("launch"),
                                        b -> {
                                            minecraft.gameMode.handleInventoryButtonClick(
                                                    menu.containerId, 0);
                                            // Hand over to the flight HUD's big countdown.
                                            onClose();
                                        })
                                .bounds(leftPos + 12, topPos + 153, 140, 20)
                                .build());
        consent =
                addRenderableWidget(
                        Button.builder(
                                        tr("passengers"),
                                        b ->
                                                minecraft.gameMode.handleInventoryButtonClick(
                                                        menu.containerId, 1))
                                .bounds(leftPos + 160, topPos + 153, 154, 20)
                                .build());
        shake =
                addRenderableWidget(
                        Button.builder(
                                        tr("shake", 0),
                                        b ->
                                                TravelClientModule.scale(
                                                        (Math.round(TravelClientModule.scale() * 4)
                                                                        + 1)
                                                                % 5
                                                                / 4d))
                                .bounds(leftPos + 12, topPos + 180, 302, 20)
                                .build());
    }

    protected void containerTick() {
        super.containerTick();
        ticks++;
        launch.active = menu.data.get(9) == 1 && menu.data.get(7) < 0;
        consent.active = menu.data.get(7) < 0;
        consent.setMessage(tr(menu.data.get(10) == 1 ? "passengers_on" : "passengers_off"));
        shake.setMessage(tr("shake", Math.round(TravelClientModule.scale() * 100)));
        int checks =
                (menu.data.get(2) == 1 ? 1 : 0) | (menu.data.get(0) >= menu.data.get(1) ? 2 : 0);
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

    private Component oxygen(boolean equipped, int percent, int seconds) {
        return equipped ? tr("oxygen", seconds, percent) : tr("no_tank");
    }

    protected void extractLabels(GuiGraphicsExtractor g, int x, int y) {
        g.text(font, title, 12, 10, 0xffeeeeff);
    }

    private void line(GuiGraphicsExtractor g, Component text, int y, int color) {
        g.text(font, text, leftPos + 12, topPos + y, color);
    }

    /** Gauge under the fuel line with a white tick at the launch minimum, as in the HUD. */
    private void fuelBar(GuiGraphicsExtractor g, int fuel, int required, int color) {
        int left = leftPos + 12, right = left + 140, top = topPos + 55;
        g.fill(left, top, right, top + 3, 0xff26303c);
        g.fill(left, top, left + 140 * Math.clamp(fuel, 0, 100) / 100, top + 3, color);
        int mark = left + 140 * Math.clamp(required, 0, 100) / 100;
        g.fill(mark, top - 1, mark + 1, top + 4, 0xffffffff);
    }

    public void extractContents(GuiGraphicsExtractor g, int x, int y, float dt) {
        int theme = ShipDesign.of(ShipTier.safe(menu.data.get(16))).theme();
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xf00b1220);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 3, theme);
        g.fill(leftPos + 8, topPos + 24, leftPos + imageWidth - 8, topPos + 25, 0x40ffffff);
        int white = 0xffeeeeff, good = theme, bad = 0xffff6666;
        boolean fuel = menu.data.get(0) >= menu.data.get(1), earth = menu.data.get(11) == 0;
        line(g, tr("destination", tr(earth ? "kepler" : "overworld")), 30, white);
        line(g, tr("fuel", menu.data.get(0), menu.data.get(1)), 44, fuel ? good : bad);
        fuelBar(g, menu.data.get(0), menu.data.get(1), fuel ? good : bad);
        line(
                g,
                tr("pilot", oxygen(menu.data.get(3) == 1, menu.data.get(4), menu.data.get(12))),
                64,
                white);
        line(
                g,
                tr(
                        "passenger",
                        menu.data.get(5) == 0
                                ? tr("no_passenger")
                                : oxygen(
                                        menu.data.get(5) == 2,
                                        menu.data.get(6),
                                        menu.data.get(13))),
                81,
                white);
        line(
                g,
                tr(earth ? "platform" : "ground", menu.data.get(2) == 1 ? "✓" : "×"),
                99,
                menu.data.get(2) == 1 ? good : bad);
        line(g, tr("fuel_check", fuel ? "✓" : "×"), 114, fuel ? good : bad);
        line(
                g,
                tr(menu.data.get(3) == 1 ? "tank_ok" : "tank_warning"),
                129,
                menu.data.get(3) == 1 ? good : 0xffffcc66);
        if (menu.data.get(7) == 0)
            line(g, tr("countdown", (menu.data.get(8) + 19) / 20), 140, white);
        line(g, tr("controls"), 214, 0xffaabbcc);
        boolean over = launch.isHovered() || consent.isHovered() || shake.isHovered();
        if (over && !hover) TravelClientModule.beep(false);
        hover = over;
        super.extractContents(g, x, y, dt);
    }
}
