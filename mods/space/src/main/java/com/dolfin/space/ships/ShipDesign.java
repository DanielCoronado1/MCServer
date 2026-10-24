package com.dolfin.space.ships;

import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical ship geometry shared by collision, rendering, item models and the texture generator.
 *
 * <p>Coordinates are sixteenths of a block with Y pointing up from the ship's feet and the cockpit
 * canopy on the -Z side. Every part is a box-UV cuboid whose texture region starts at {@code (u,
 * v)}; mirrored parts deliberately share one region. {@code tools/space/generate_assets.py} parses
 * this file to paint the textures and item models, so keep the {@code p(...)} call format intact.
 */
public record ShipDesign(
        float width, float height, int theme, List<Nozzle> nozzles, List<Part> parts) {
    /** A box-UV cuboid: texture origin {@code (u, v)}, position and size in sixteenths. */
    public record Part(
            String name, int u, int v, float x, float y, float z, float w, float h, float d) {
        /** Width of the box-UV region: two side strips plus front and back. */
        public int uvWidth() {
            return (int) (2 * (d + w));
        }

        /** Height of the box-UV region: top/bottom strip plus side strip. */
        public int uvHeight() {
            return (int) (d + h);
        }
    }

    /** Engine exhaust centre on the ship floor, in sixteenths, and its flame diameter. */
    public record Nozzle(float x, float z, float size) {
        /** World-space offset in blocks for a ship with the given yaw, matching the renderer. */
        public Vec3 offset(float yawDegrees) {
            double a = Math.toRadians(180 - yawDegrees), cos = Math.cos(a), sin = Math.sin(a);
            return new Vec3((x * cos + z * sin) / 16, 0, (-x * sin + z * cos) / 16);
        }
    }

    private static Part p(
            String name, int u, int v, float x, float y, float z, float w, float h, float d) {
        return new Part(name, u, v, x, y, z, w, h, d);
    }

    private static final Map<ShipTier, ShipDesign> DESIGNS = designs();

    /** Cached design for a tier; safe to call every frame. */
    public static ShipDesign of(ShipTier tier) {
        return DESIGNS.get(tier);
    }

    private static Map<ShipTier, ShipDesign> designs() {
        var map = new EnumMap<ShipTier, ShipDesign>(ShipTier.class);
        for (var tier : ShipTier.values()) map.put(tier, create(tier));
        return Map.copyOf(map);
    }

    private static ShipDesign create(ShipTier tier) {
        return switch (tier) {
            case BASICA ->
                    new ShipDesign(
                            3.125f,
                            4.6875f,
                            0xffc4703c,
                            List.of(new Nozzle(0, 0, 11)),
                            List.of(
                                    p("bell", 64, 170, -8, 0, -8, 16, 4, 16),
                                    p("bell_neck", 50, 192, -5, 4, -5, 10, 4, 10),
                                    p("firebox", 0, 144, -11, 8, -11, 22, 4, 22),
                                    p("hull", 0, 0, -12, 12, -12, 24, 30, 24),
                                    p("band_low", 0, 54, -14, 16, -14, 28, 3, 28),
                                    p("band_high", 0, 85, -14, 36, -14, 28, 3, 28),
                                    p("canopy", 90, 192, -6, 24, -15, 12, 11, 3),
                                    p("gauge", 88, 212, 6, 19, -14, 5, 5, 2),
                                    p("gear_left", 0, 192, -15, 24, -5, 3, 10, 10),
                                    p("gear_right", 0, 192, 12, 24, -5, 3, 10, 10),
                                    p("pipe_left", 112, 85, -16, 12, 6, 2, 26, 3),
                                    p("pipe_right", 112, 85, 14, 12, 6, 2, 26, 3),
                                    p("dome_base", 48, 116, -10, 42, -10, 20, 7, 20),
                                    p("dome_mid", 0, 170, -8, 49, -8, 16, 6, 16),
                                    p("dome_top", 0, 212, -5, 55, -5, 10, 4, 10),
                                    p("stack", 40, 212, -2, 59, -2, 4, 10, 4),
                                    p("stack_cap", 56, 212, -4, 69, -4, 8, 2, 8),
                                    p("whistle", 102, 212, -1, 71, -1, 2, 4, 2),
                                    p("fin_left", 26, 192, -22, 0, -1, 10, 18, 2),
                                    p("fin_right", 26, 192, 12, 0, -1, 10, 18, 2),
                                    p("fin_back", 0, 116, -1, 0, 12, 2, 18, 10),
                                    p("fin_front", 24, 116, -1, 0, -22, 2, 18, 10),
                                    p("foot_left", 18, 226, -24, 0, -2, 5, 1, 4),
                                    p("foot_right", 18, 226, 19, 0, -2, 5, 1, 4),
                                    p("foot_back", 110, 212, -2, 0, 19, 4, 1, 5),
                                    p("foot_front", 0, 226, -2, 0, -24, 4, 1, 5)));
            case CARGA ->
                    new ShipDesign(
                            5.625f,
                            3.75f,
                            0xffd6a848,
                            List.of(new Nozzle(-27.5f, 0, 11), new Nozzle(27.5f, 0, 11)),
                            List.of(
                                    p("hull", 0, 0, -20, 12, -18, 40, 28, 36),
                                    p("band_low", 0, 64, -21, 15, -19, 42, 3, 38),
                                    p("band_high", 0, 105, -21, 35, -19, 42, 3, 38),
                                    p("canopy", 112, 204, -12, 25, -20, 24, 9, 2),
                                    p("bridge", 0, 146, -15, 40, -12, 30, 9, 24),
                                    p("roof", 108, 146, -16, 49, -14, 32, 1, 28),
                                    p("stack_left", 94, 204, -11, 50, -2, 5, 8, 4),
                                    p("stack_right", 94, 204, 6, 50, -2, 5, 8, 4),
                                    p("stack_cap_left", 164, 204, -12, 58, -4, 7, 2, 8),
                                    p("stack_cap_right", 164, 204, 5, 58, -4, 7, 2, 8),
                                    p("pod_left", 152, 0, -35, 10, -12, 15, 25, 24),
                                    p("pod_right", 152, 0, 20, 10, -12, 15, 25, 24),
                                    p("pod_cap_left", 32, 179, -32, 35, -10, 10, 4, 20),
                                    p("pod_cap_right", 32, 179, 22, 35, -10, 10, 4, 20),
                                    p("vent_left", 72, 204, -30, 39, 2, 5, 7, 6),
                                    p("vent_right", 72, 204, 25, 39, 2, 5, 7, 6),
                                    p("bell_left", 158, 179, -35, 0, -8, 15, 4, 16),
                                    p("bell_right", 158, 179, 20, 0, -8, 15, 4, 16),
                                    p("neck_left", 0, 204, -32, 4, -5, 10, 6, 10),
                                    p("neck_right", 0, 204, 22, 4, -5, 10, 6, 10),
                                    p("gear_left", 0, 179, -39, 16, -6, 4, 13, 12),
                                    p("gear_right", 0, 179, 35, 16, -6, 4, 13, 12),
                                    p("cargo", 92, 179, -15, 15, 18, 30, 20, 3),
                                    p("gauge", 246, 204, -2, 18, -19, 4, 4, 1),
                                    p("leg_front_left", 40, 204, -19, 1, -18, 4, 11, 4),
                                    p("leg_front_right", 40, 204, 15, 1, -18, 4, 11, 4),
                                    p("leg_back_left", 56, 204, -19, 1, 14, 4, 11, 4),
                                    p("leg_back_right", 56, 204, 15, 1, 14, 4, 11, 4),
                                    p("foot_front_left", 194, 204, -20, 0, -19, 6, 1, 7),
                                    p("foot_front_right", 194, 204, 14, 0, -19, 6, 1, 7),
                                    p("foot_back_left", 220, 204, -20, 0, 12, 6, 1, 7),
                                    p("foot_back_right", 220, 204, 14, 0, 12, 6, 1, 7)));
            case RAPIDA ->
                    new ShipDesign(
                            3.125f,
                            5.625f,
                            0xff58aa96,
                            List.of(new Nozzle(0, 0, 9)),
                            List.of(
                                    p("bell", 0, 199, -6, 0, -6, 12, 4, 12),
                                    p("bell_neck", 68, 199, -4, 4, -4, 8, 4, 8),
                                    p("firebox", 0, 89, -8, 8, -8, 16, 7, 16),
                                    p("hull", 0, 0, -9, 15, -9, 18, 43, 18),
                                    p("band_low", 0, 135, -10, 20, -10, 20, 2, 20),
                                    p("band_mid", 0, 157, -10, 38, -10, 20, 2, 20),
                                    p("band_high", 0, 112, -10, 55, -10, 20, 3, 20),
                                    p("pipe_left", 36, 61, -11, 28, 2, 2, 24, 3),
                                    p("pipe_right", 36, 61, 9, 28, 2, 2, 24, 3),
                                    p("dome_base", 80, 157, -6, 58, -6, 12, 10, 12),
                                    p("dome_mid", 14, 179, -4, 68, -4, 8, 10, 8),
                                    p("dome_top", 112, 199, -2, 78, -2, 4, 7, 4),
                                    p("spike", 28, 215, -1, 85, -1, 2, 5, 2),
                                    p("wing_left", 46, 61, -18, 5, -1, 10, 23, 2),
                                    p("wing_right", 46, 61, 8, 5, -1, 10, 23, 2),
                                    p("wing_left_tip", 66, 179, -24, 0, -1, 6, 15, 2),
                                    p("wing_right_tip", 66, 179, 18, 0, -1, 6, 15, 2),
                                    p("wing_left_top", 100, 199, -12, 28, -1, 4, 10, 2),
                                    p("wing_right_top", 100, 199, 8, 28, -1, 4, 10, 2),
                                    p("booster_left", 0, 179, -11, 10, -2, 3, 16, 4),
                                    p("booster_right", 0, 179, 8, 10, -2, 3, 16, 4),
                                    p("booster_cap_left", 0, 215, -12, 26, -4, 6, 3, 8),
                                    p("booster_cap_right", 0, 215, 6, 26, -4, 6, 3, 8),
                                    p("fin_back", 72, 0, -1, 5, 8, 2, 23, 10),
                                    p("fin_back_tip", 46, 179, -1, 0, 12, 2, 10, 8),
                                    p("hull_cockpit", 0, 61, -6, 14, -14, 12, 22, 6),
                                    p("canopy", 48, 199, -4, 23, -16, 8, 11, 2),
                                    p("gauge", 36, 215, -2, 16, -15, 4, 4, 1)));
        };
    }

    /** Texture width: the smallest power of two that holds every part's UV region. */
    public int textureWidth() {
        return powerOfTwo(parts.stream().mapToInt(p -> p.u() + p.uvWidth()).max().orElse(1));
    }

    /** Texture height: the smallest power of two that holds every part's UV region. */
    public int textureHeight() {
        return powerOfTwo(parts.stream().mapToInt(p -> p.v() + p.uvHeight()).max().orElse(1));
    }

    private static int powerOfTwo(int n) {
        return Integer.highestOneBit(Math.max(1, n - 1)) << 1;
    }

    public static boolean boarding(boolean sight, boolean clearSeat, double distanceSquared) {
        return sight && clearSeat && distanceSquared <= 36;
    }
}
