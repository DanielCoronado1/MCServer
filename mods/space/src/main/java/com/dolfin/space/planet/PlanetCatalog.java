package com.dolfin.space.planet;

import java.util.List;

/** Shared registration manifest, deliberately independent of Minecraft bootstrap for tests. */
public final class PlanetCatalog {
    public static final List<String> BIOMES = List.of("kepler_plains","kepler_craters","kepler_crystal_forest","kepler_acid_marshes","kepler_abyssal_caverns");
    public static final List<String> BLOCKS = List.of("roca_lunar","bloque_cristal_energia","lodo_acido","hongo_bioluminiscente","mena_titanio","mena_hierro_meteorico","mena_cristal_energia");
    public static final List<String> ITEMS = List.of("titanio_crudo","lingote_titanio","hierro_meteorico_crudo","lingote_hierro_meteorico","cristal_energia");
    public static final int ACID_DURATION_TICKS = 60;
    public static final int ACID_AMPLIFIER = 1;
    public static boolean acidAffects(boolean living, boolean creative, boolean spectator, boolean server) {
        return server && living && !creative && !spectator;
    }
    private PlanetCatalog() {}
}
