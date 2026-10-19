package com.dolfin.space;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for {@code dolfinspace}: initializes configuration, content registries,
 * creative tab, Kepler dimension mechanics, travel guards, and client modpack verification.
 */
public final class DolfinSpace implements ModInitializer {
    public static final String MOD_ID = "dolfinspace";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SpaceConfig config;

    public static SpaceConfig config() {
        return config;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        config = new SpaceConfig(
                FabricLoader.getInstance().getConfigDir().resolve(SpaceConfig.FILE_NAME),
                LOGGER::warn
        );
        SpaceContent.init();
        com.dolfin.space.oxygen.OxygenModule.init();
        com.dolfin.space.planet.PlanetModule.init();
        SpaceModpackCheck.init();
        KeplerDimension.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                SpaceCommands.register(dispatcher));

        SpaceConfig.Snapshot current = config.current();
        LOGGER.info(
                "event=startup modId={} configFile={} generation={} dimension={} gravityMultiplier={} permissionProvider={}",
                MOD_ID,
                config.file().toAbsolutePath(),
                current.generation(),
                current.dimension().idDimension(),
                current.dimension().multiplicadorGravedad(),
                SpacePermissions.provider()
        );
    }
}
