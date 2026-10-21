package com.dolfin.lobby;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Repeatable trim inside named station lanes; all interactive anchors remain fixed. */
public final class StationDecor {
    private StationDecor() {}

    private static void box(ServerLevel w, int x, int y, int z, int xx, int yy, int zz, Block b) {
        StationLayout.fill(w, x, y, z, xx, yy, zz, b);
    }

    private static Block color(DyeColor c) {
        return Blocks.CONCRETE.pick(c);
    }

    public static void deck(ServerLevel w, boolean showcase) {
        // Panoramic windows, graphite plinths and lit structural ribs; open sky for flight.
        for (int z : new int[] {-32, 32}) {
            box(w, -48, 128, z, 48, 129, z, Blocks.POLISHED_DEEPSLATE);
            box(w, -48, 130, z, 48, 134, z, Blocks.GLASS);
            box(w, -48, 135, z, 48, 135, z, Blocks.QUARTZ_BRICKS);
        }
        for (int x : new int[] {-48, 48}) {
            box(w, x, 128, -32, x, 129, 32, Blocks.POLISHED_DEEPSLATE);
            box(w, x, 130, -32, x, 134, 32, Blocks.GLASS);
            box(w, x, 135, -32, x, 135, 32, Blocks.QUARTZ_BRICKS);
        }
        for (int x = -48; x <= 48; x += 16)
            for (int z : new int[] {-32, 32}) {
                box(w, x, 129, z, x, 135, z, Blocks.QUARTZ_PILLAR);
                box(w, x, 134, z, x, 134, z, Blocks.SEA_LANTERN);
            }
        box(w, -2, 128, 8, 2, 128, 28, Blocks.SMOOTH_QUARTZ);
        for (int x : new int[] {-3, 3}) {
            box(w, x, 128, 8, x, 128, 28, color(DyeColor.CYAN));
            for (int z : new int[] {8, 16, 24}) box(w, x, 128, z, x, 128, z, Blocks.SEA_LANTERN);
        }
        box(w, -44, 128, 5, 39, 128, 7, Blocks.QUARTZ_BRICKS);
        box(w, -44, 136, 6, 39, 136, 6, Blocks.QUARTZ_BRICKS);
        box(w, 0, 136, 8, 0, 136, 26, Blocks.POLISHED_DEEPSLATE);
        for (int x = -40; x <= 36; x += 8) box(w, x, 136, 6, x, 136, 6, Blocks.SEA_LANTERN);
        if (showcase) {
            // Retire only the initial builder's solid-color placeholder pads. Preserve other
            // blocks (including player edits) and leave unrelated deck areas untouched.
            for (int center : new int[] {-30, 0, 30})
                for (int x = center - 10; x <= center + 10; x++)
                    for (int z = -24; z <= -4; z++) {
                        var pos = new net.minecraft.core.BlockPos(x, 128, z);
                        var state = w.getBlockState(pos);
                        if (state.is(color(DyeColor.LIME))
                                || state.is(color(DyeColor.GREEN))
                                || state.is(color(DyeColor.BLUE)))
                            w.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                    }
            wing(w, -46, -24, color(DyeColor.LIME));
            wing(w, -18, 5, color(DyeColor.ORANGE));
            wing(w, 18, 42, color(DyeColor.CYAN));
            box(w, -2, 128, 29, 2, 128, 31, color(DyeColor.CYAN));
            for (int i = 0; i < ExhibitCatalog.ALL.size(); i++) {
                boolean c = i < 9;
                int j = c ? i : i - 9;
                int x = (c ? -44 : -16) + j % 4 * 6, z = -25 + j / 4 * 7;
                box(
                        w,
                        x - 1,
                        128,
                        z - 1,
                        x + 1,
                        128,
                        z + 1,
                        color(c ? DyeColor.LIME : DyeColor.ORANGE));
                box(w, x, 128, z + 2, x, 128, z + 2, Blocks.SEA_LANTERN);
            }
            landing(w, 30, 128, -14);
        } else
            for (int x : new int[] {-20, 20}) {
                Block accent = color(x < 0 ? DyeColor.LIME : DyeColor.CYAN);
                box(w, x - 3, 128, -5, x + 3, 128, 4, accent);
                box(w, x - 2, 128, -3, x + 2, 128, 4, Blocks.SMOOTH_QUARTZ);
                for (int dx : new int[] {-3, 3})
                    box(w, x + dx, 129, -3, x + dx, 134, -3, Blocks.QUARTZ_PILLAR);
                box(w, x - 3, 134, -3, x + 3, 134, -3, accent);
                box(w, x - 2, 134, -3, x + 2, 134, -3, Blocks.SEA_LANTERN);
                box(w, x, 128, 5, x, 128, 7, accent);
            }
    }

    private static void wing(ServerLevel w, int a, int b, Block accent) {
        box(w, a, 128, -29, b, 128, -29, accent);
        box(w, a, 128, 3, b, 128, 3, accent);
        for (int x : new int[] {a, b}) {
            box(w, x, 128, -29, x, 128, 3, accent);
            box(w, x, 129, -29, x, 134, -29, Blocks.QUARTZ_PILLAR);
            box(w, x, 133, -29, x, 133, -29, Blocks.SEA_LANTERN);
        }
    }

    public static void landing(ServerLevel w, int x, int y, int z) {
        // Floor only: never obstruct the ship's ascent/descent column.
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                int edge = Math.max(Math.abs(dx), Math.abs(dz));
                if (edge == 6 || edge == 5)
                    box(
                            w,
                            x + dx,
                            y,
                            z + dz,
                            x + dx,
                            y,
                            z + dz,
                            edge == 6 ? color(DyeColor.YELLOW) : Blocks.POLISHED_DEEPSLATE);
            }
        for (int dx : new int[] {-6, 6})
            for (int dz : new int[] {-6, 6})
                box(w, x + dx, y, z + dz, x + dx, y, z + dz, Blocks.SEA_LANTERN);
    }

    public static void outpost(ServerLevel w) {
        landing(w, 100000, 200, 100000);
        for (int x : new int[] {99984, 100016})
            for (int z : new int[] {99984, 100016}) {
                box(w, x, 201, z, x, 205, z, Blocks.QUARTZ_PILLAR);
                box(w, x, 204, z, x, 204, z, Blocks.SEA_LANTERN);
            }
        for (int z = 100007; z <= 100013; z++)
            for (int x : new int[] {99998, 100002})
                box(w, x, 200, z, x, 200, z, color(DyeColor.CYAN));
    }

    public static void arena(ServerLevel w, int cx) {
        // Solid bedrock remains behind the decorative inner observation shell.
        for (int d : new int[] {-11, 11}) {
            box(w, cx + d, 129, -11, cx + d, 139, 11, Blocks.QUARTZ_BRICKS);
            box(w, cx - 11, 129, d, cx + 11, 139, d, Blocks.QUARTZ_BRICKS);
            box(w, cx + d, 132, -10, cx + d, 134, 10, Blocks.STAINED_GLASS.pick(DyeColor.CYAN));
            box(w, cx - 10, 132, d, cx + 10, 134, d, Blocks.STAINED_GLASS.pick(DyeColor.CYAN));
        }
        for (int x : new int[] {-10, 10})
            for (int z : new int[] {-10, 10}) {
                box(w, cx + x, 129, z, cx + x, 139, z, Blocks.POLISHED_DEEPSLATE);
                box(w, cx + x, 136, z, cx + x, 138, z, Blocks.SEA_LANTERN);
            }
        for (int x = -8; x <= 8; x += 4) box(w, cx + x, 128, 9, cx + x, 128, 9, Blocks.SEA_LANTERN);
        box(w, cx - 3, 128, 7, cx + 3, 128, 7, color(DyeColor.CYAN));
    }
}
