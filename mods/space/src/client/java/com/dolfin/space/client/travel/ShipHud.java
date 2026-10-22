package com.dolfin.space.client.travel;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.ships.*;
import com.dolfin.space.travel.*;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pilot and passenger flight HUD: a compact top-left panel (state, destination, altitude, vertical
 * speed, fuel gauge, warnings) plus a large centred countdown. Reads only synced ship data.
 */
public final class ShipHud {
    private static final int X = 6, Y = 6, WIDTH = 158, PAD = 5, LINE = 11;
    private static final int TEXT = 0xffd8e2ee, MUTED = 0xff8a9bb0, GOOD = 0xff7cf09a;
    private static final int BAD = 0xffff6b6b, AMBER = 0xffffcc66, SKY = 0xff8fe3ff;

    /** One panel text row with an optional right-aligned note. */
    private record Row(Component text, int color, Component note) {}

    public static void init() {
        HudElementRegistry.addLast(DolfinSpace.id("ship_hud"), (g, delta) -> extract(g));
    }

    private static void extract(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || !(mc.player.getVehicle() instanceof SpaceshipEntity ship)
                || mc.gui.screen() != null) return;
        boolean blink = mc.player.tickCount % 20 < 12;
        var rows = rows(ship, blink);
        int theme = ShipDesign.of(ship.tier()).theme();
        int fuelY = Y + PAD + rows.size() * LINE + 2;
        int height = fuelY - Y + 16;
        g.fill(X, Y, X + WIDTH, Y + height, 0xb00b1220);
        g.fill(X, Y, X + WIDTH, Y + 2, theme);
        for (int i = 0; i < rows.size(); i++) row(g, mc.font, rows.get(i), Y + PAD + 2 + i * LINE);
        fuelGauge(g, mc.font, ship, fuelY, theme, blink);
        countdown(g, mc.font, ship, blink);
    }

    /** Builds the panel rows: title, state, destination, then flight data or hint, warnings. */
    private static List<Row> rows(SpaceshipEntity ship, boolean blink) {
        int phase = ship.flightPhase();
        boolean kepler = KeplerDimension.isKepler(ship.level().dimension());
        var rows = new ArrayList<Row>();
        rows.add(
                new Row(
                        Component.translatable("item.dolfinspace." + ship.tier().itemId())
                                .withStyle(ChatFormatting.BOLD),
                        0xffffffff,
                        null));
        rows.add(state(ship, phase));
        rows.add(
                new Row(
                        tr(
                                "hud_destination",
                                tr(
                                        FlightStatus.boundForKepler(phase, kepler)
                                                ? "kepler"
                                                : "overworld")),
                        TEXT,
                        null));
        if (phase > FlightJournal.Phase.COUNTDOWN.ordinal()) {
            double speed = FlightProfile.verticalSpeed(ship.yo, ship.getY());
            rows.add(new Row(tr("hud_altitude", Math.round(ship.getY())), TEXT, null));
            rows.add(
                    new Row(
                            tr("hud_speed", String.format(Locale.ROOT, "%+.1f", speed)),
                            TEXT,
                            null));
        } else rows.add(new Row(tr("hud_hint"), MUTED, null));
        for (var warning :
                FlightStatus.warnings(phase, ship.fuel(), ship.requiredFuel(), ship.launchChecks()))
            rows.add(
                    new Row(
                            Component.literal("⚠ ").append(tr(warning.key)),
                            blink ? BAD : AMBER,
                            null));
        return rows;
    }

    private static Row state(SpaceshipEntity ship, int phase) {
        String key = FlightStatus.stateKey(phase, ship.phaseTicks(), ship.launchChecks());
        int color =
                switch (key) {
                    case "hud_ready" -> GOOD;
                    case "hud_not_ready" -> BAD;
                    case "hud_checking" -> MUTED;
                    case "hud_countdown", "hud_ignition" -> AMBER;
                    default -> SKY;
                };
        var text =
                key.equals("hud_countdown")
                        ? tr(key, FlightStatus.countdownSeconds(ship.phaseTicks()))
                        : tr(key);
        return new Row(text, color, null);
    }

    private static void row(GuiGraphicsExtractor g, Font font, Row row, int y) {
        g.text(font, row.text(), X + PAD, y, row.color(), true);
        if (row.note() != null)
            g.text(font, row.note(), X + WIDTH - PAD - font.width(row.note()), y, MUTED, true);
    }

    /** Fuel label, bar and a white tick at the launch minimum; flashes red when short. */
    private static void fuelGauge(
            GuiGraphicsExtractor g,
            Font font,
            SpaceshipEntity ship,
            int y,
            int theme,
            boolean blink) {
        int fuel = ship.fuel(), required = ship.requiredFuel();
        boolean parked = ship.flightPhase() < 0;
        boolean low = parked && fuel < required;
        row(
                g,
                font,
                new Row(
                        tr("hud_fuel", fuel),
                        low ? BAD : TEXT,
                        parked ? tr("hud_fuel_required", required) : null),
                y);
        int left = X + PAD, right = X + WIDTH - PAD, top = y + 10;
        int fill = left + (right - left) * Math.clamp(fuel, 0, 100) / 100;
        g.fill(left, top, right, top + 3, 0xff26303c);
        g.fill(left, top, fill, top + 3, low ? (blink ? BAD : 0xff8a2f2f) : theme);
        if (parked) {
            int mark = left + (right - left) * Math.clamp(required, 0, 100) / 100;
            g.fill(mark, top - 1, mark + 1, top + 4, 0xffffffff);
        }
    }

    /** Large centred countdown digits, then a flashing ignition banner. */
    private static void countdown(
            GuiGraphicsExtractor g, Font font, SpaceshipEntity ship, boolean blink) {
        if (ship.flightPhase() != FlightJournal.Phase.COUNTDOWN.ordinal()) return;
        int ticks = ship.phaseTicks();
        boolean ignition = ticks <= FlightProfile.IGNITION_TICKS;
        if (ignition && !blink) return;
        Component text =
                ignition
                        ? tr("hud_ignition").withStyle(ChatFormatting.BOLD)
                        : Component.literal(String.valueOf(FlightStatus.countdownSeconds(ticks)));
        float scale = ignition ? 2.5f : 4f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(g.guiWidth() / 2f, g.guiHeight() / 2f - 48);
        pose.scale(scale, scale);
        g.text(font, text, -font.width(text) / 2, 0, AMBER, true);
        pose.popMatrix();
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("space.ui." + key, args);
    }

    private ShipHud() {}
}
