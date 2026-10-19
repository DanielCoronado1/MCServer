package com.dolfin.space.machines;

import com.dolfin.space.SpaceConfig;

import java.util.List;

/** Registry-independent recipes and automation rules, also used by the live machines. */
public final class MachineRecipes {
    public enum Kind {
        COMPRESSOR,
        CIRCUITS,
        REFINERY
    }

    public enum Face {
        TOP,
        SIDE,
        BOTTOM
    }

    public record Ingredient(String id, int count) {}

    public record Recipe(Kind kind, List<Ingredient> inputs, String output, String remainder) {
        public boolean matches(List<String> ids, List<Integer> counts) {
            for (int i = 0; i < 3; i++) {
                if (i < inputs.size()) {
                    if (!inputs.get(i).id().equals(ids.get(i))
                            || counts.get(i) < inputs.get(i).count()) return false;
                } else if (counts.get(i) != 0) return false;
            }
            return true;
        }
    }

    private static Ingredient i(String id, int n) {
        return new Ingredient(id, n);
    }

    public static final List<Recipe> ALL =
            List.of(
                    new Recipe(
                            Kind.COMPRESSOR,
                            List.of(i("dolfinspace:lingote_acero", 2)),
                            "placa_acero",
                            ""),
                    new Recipe(
                            Kind.COMPRESSOR,
                            List.of(i("minecraft:copper_ingot", 2)),
                            "placa_cobre",
                            ""),
                    new Recipe(
                            Kind.COMPRESSOR,
                            List.of(i("minecraft:gold_ingot", 2)),
                            "placa_oro",
                            ""),
                    new Recipe(
                            Kind.COMPRESSOR,
                            List.of(i("#dolfinspace:lingotes_titanio", 2)),
                            "placa_titanio",
                            ""),
                    new Recipe(
                            Kind.CIRCUITS,
                            List.of(
                                    i("minecraft:redstone", 1),
                                    i("minecraft:quartz", 1),
                                    i("dolfinspace:placa_oro", 1)),
                            "circuito_basico",
                            ""),
                    new Recipe(
                            Kind.CIRCUITS,
                            List.of(
                                    i("dolfinspace:circuito_basico", 1),
                                    i("minecraft:diamond", 1),
                                    i("#dolfinspace:cristales_energia", 1)),
                            "circuito_avanzado",
                            ""),
                    new Recipe(
                            Kind.REFINERY,
                            List.of(
                                    i("minecraft:lava_bucket", 1),
                                    i("minecraft:blaze_powder", 1),
                                    i("dolfinspace:bidon_vacio", 1)),
                            "bidon_combustible",
                            "minecraft:bucket"));

    public static int duration(Kind kind, SpaceConfig.MachinesSection cfg) {
        return switch (kind) {
            case COMPRESSOR -> cfg.ticksCompresor();
            case CIRCUITS -> cfg.ticksFabricadorCircuitos();
            case REFINERY -> cfg.ticksRefineria();
        };
    }

    public static boolean fuel(String id) {
        return id.equals("minecraft:coal") || id.equals("minecraft:charcoal");
    }

    public static boolean insert(int slot, Face face) {
        return face == Face.TOP ? slot >= 0 && slot < 3 : face == Face.SIDE && slot == 3;
    }

    public static boolean extract(int slot, Face face) {
        return face == Face.BOTTOM && (slot == 4 || slot == 5);
    }

    /** One coal supplies 1600 processing ticks; blocked machines never consume new fuel. */
    public static boolean consumeFuel(boolean recipeReady, int burnTicks, boolean fuelAvailable) {
        return recipeReady && burnTicks == 0 && fuelAvailable;
    }

    private MachineRecipes() {}
}
