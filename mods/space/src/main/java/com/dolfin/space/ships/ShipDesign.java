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
                            2.5f,
                            3.75f,
                            0xff49caca,
                            List.of(new Nozzle(0, 0, 9)),
                            List.of(
                                    p("bell", 80, 18, -6, 0, -6, 12, 3, 12),
                                    p("bell_neck", 64, 68, -4, 3, -4, 8, 3, 8),
                                    p("skirt", 0, 46, -9, 6, -9, 18, 4, 18),
                                    p("hull", 0, 0, -10, 10, -10, 20, 26, 20),
                                    p("canopy", 96, 71, -6, 25, -12, 12, 9, 2),
                                    p("nose_base", 0, 68, -8, 36, -8, 16, 6, 16),
                                    p("nose_mid", 80, 0, -6, 42, -6, 12, 6, 12),
                                    p("nose_top", 72, 55, -4, 48, -4, 8, 5, 8),
                                    p("tip", 64, 79, -2, 53, -2, 4, 4, 4),
                                    p("antenna", 116, 82, -1, 57, -1, 2, 3, 2),
                                    p("fin_left", 104, 55, -18, 0, -1, 8, 14, 2),
                                    p("fin_right", 104, 55, 10, 0, -1, 8, 14, 2),
                                    p("fin_left_top", 104, 82, -14, 14, -1, 4, 6, 2),
                                    p("fin_right_top", 104, 82, 10, 14, -1, 4, 6, 2),
                                    p("fin_back", 80, 33, -1, 0, 10, 2, 14, 8),
                                    p("fin_back_top", 80, 79, -1, 14, 10, 2, 6, 4),
                                    p("fin_front", 100, 33, -1, 0, -18, 2, 14, 8),
                                    p("fin_front_top", 92, 82, -1, 14, -14, 2, 6, 4),
                                    p("foot_left", 64, 87, -19, 0, -2, 4, 1, 4),
                                    p("foot_right", 64, 87, 15, 0, -2, 4, 1, 4),
                                    p("foot_back", 0, 90, -2, 0, 15, 4, 1, 4),
                                    p("foot_front", 16, 90, -2, 0, -19, 4, 1, 4)));
            case CARGA ->
                    new ShipDesign(
                            4.5f,
                            3f,
                            0xffefa545,
                            List.of(new Nozzle(-22, 0, 9), new Nozzle(22, 0, 9)),
                            List.of(
                                    p("hull", 0, 0, -16, 10, -14, 32, 22, 28),
                                    p("canopy", 208, 34, -10, 22, -16, 20, 8, 2),
                                    p("bridge", 120, 40, -12, 32, -10, 24, 7, 20),
                                    p("dish", 54, 50, -4, 39, -2, 8, 2, 8),
                                    p("antenna", 110, 50, -1, 41, -1, 2, 6, 2),
                                    p("pod_left", 120, 0, -28, 8, -10, 12, 20, 20),
                                    p("pod_right", 120, 0, 16, 8, -10, 12, 20, 20),
                                    p("pod_cap_left", 208, 0, -26, 28, -8, 8, 3, 16),
                                    p("pod_cap_right", 208, 0, 18, 28, -8, 8, 3, 16),
                                    p("bell_left", 208, 19, -28, 0, -6, 12, 3, 12),
                                    p("bell_right", 208, 19, 16, 0, -6, 12, 3, 12),
                                    p("neck_left", 208, 44, -26, 3, -4, 8, 5, 8),
                                    p("neck_right", 208, 44, 18, 3, -4, 8, 5, 8),
                                    p("cargo", 0, 50, -12, 12, 14, 24, 16, 3),
                                    p("fin_left", 240, 44, -31, 10, -1, 3, 14, 2),
                                    p("fin_right", 240, 44, 28, 10, -1, 3, 14, 2),
                                    p("leg_front_left", 86, 50, -15, 1, -14, 3, 9, 3),
                                    p("leg_front_right", 86, 50, 12, 1, -14, 3, 9, 3),
                                    p("leg_back_left", 98, 50, -15, 1, 11, 3, 9, 3),
                                    p("leg_back_right", 98, 50, 12, 1, 11, 3, 9, 3),
                                    p("foot_front_left", 208, 57, -16, 0, -15, 5, 1, 5),
                                    p("foot_front_right", 208, 57, 11, 0, -15, 5, 1, 5),
                                    p("foot_back_left", 54, 60, -16, 0, 10, 5, 1, 5),
                                    p("foot_back_right", 54, 60, 11, 0, 10, 5, 1, 5)));
            case RAPIDA ->
                    new ShipDesign(
                            2.5f,
                            4.5f,
                            0xffba8bff,
                            List.of(new Nozzle(0, 0, 7)),
                            List.of(
                                    p("bell", 88, 52, -5, 0, -5, 10, 3, 10),
                                    p("bell_neck", 64, 56, -3, 3, -3, 6, 3, 6),
                                    p("skirt", 68, 20, -6, 6, -6, 12, 6, 12),
                                    p("hull", 0, 0, -6, 12, -6, 12, 34, 12),
                                    p("canopy", 16, 46, -4, 34, -8, 8, 10, 2),
                                    p("canopy_top", 0, 60, -3, 44, -7, 6, 3, 2),
                                    p("nose_base", 48, 38, -5, 46, -5, 10, 8, 10),
                                    p("nose_mid", 88, 38, -3, 54, -3, 6, 8, 6),
                                    p("nose_top", 36, 56, -2, 62, -2, 4, 6, 4),
                                    p("tip", 16, 58, -1, 68, -1, 2, 4, 2),
                                    p("wing_left", 68, 0, -14, 4, -1, 8, 18, 2),
                                    p("wing_right", 68, 0, 6, 4, -1, 8, 18, 2),
                                    p("wing_left_tip", 112, 38, -19, 0, -1, 5, 12, 2),
                                    p("wing_right_tip", 112, 38, 14, 0, -1, 5, 12, 2),
                                    p("wing_left_top", 52, 56, -10, 22, -1, 4, 8, 2),
                                    p("wing_right_top", 52, 56, 6, 22, -1, 4, 8, 2),
                                    p("booster_left", 88, 0, -9, 8, -2, 3, 16, 4),
                                    p("booster_right", 88, 0, 6, 8, -2, 3, 16, 4),
                                    p("stabilizer", 48, 0, -1, 4, 6, 2, 18, 8),
                                    p("stabilizer_tip", 0, 46, -1, 0, 10, 2, 8, 6)));
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
