# Issue #25 — Esqueleto de `dolfinspace`, configuración y dimensión Kepler de baja gravedad

## Resumen

Este documento describe la base técnica implementada en `mods/space/` (`modId = dolfinspace`, paquete `com.dolfin.space`) para la épica de exploración espacial (#24) y los puntos de extensión preparados para los tickets #26–#33.

## Componentes implementados

1. **Proyecto Gradle independiente (`mods/space/`)**
   - Minecraft `26.3`, Fabric Loader `0.19.5`, Fabric API `0.163.0+26.3`, Java `25`, Loom `1.18.4`.
   - `splitEnvironmentSourceSets()` activo (`src/main` común/servidor, `src/client` cliente, `src/test` pruebas JUnit 5).
   - Compilable de forma aislada con `./gradlew build --no-daemon`.

2. **Configuración con validación por campo (`SpaceConfig`)**
   - Archivo: `config/dolfin-space.json`.
   - Si no existe al iniciar o recargar, se genera automáticamente con todos los valores por defecto (decisiones D1–D13 de la épica).
   - Un `record` inmutable por sección en `SpaceConfig.Snapshot`:
     - `general` (`GeneralSection`): `activo`, `exigirModEnCliente`, `mensajeSinMod`, `respetarClaims`, `mensajesChat`.
     - `dimension` (`DimensionSection`): `idDimension` (`dolfinspace:kepler`), `multiplicadorGravedad` (`0.40`), `multiplicadorDanioCaida` (`0.40`), `multiplicadorDistanciaSeguraCaida` (`2.50`), `sinLluvia`, `sinNubes`, `sinMobsVanilla`, `camasExplotan`, `anclaReaparicionActiva`, `bloquearPortales`, `bloquearTpDirecto`, `permitirExecuteAdmin`.
     - `maquinas` (`MachinesSection`): duraciones de compresor, fabricador de circuitos, refinería y compresor de oxígeno (#26, #29).
     - `naves` (`ShipsSection`): permisos de pilotaje, invulnerabilidad, retorno automático, porcentajes de combustible por viaje (50% ida + 50% vuelta = 100% desde Tierra), cuentas regresivas y capacidad de carga (#27, #28).
     - `oxigeno` (`OxygenSection`): duración de tanques chico/mediano/grande (`300`/`600`/`1200` s), daño por asfixia (`2.0`/s), umbral de alerta (`0.20`), consumo en nave/fuera del planeta (#29).
     - `mobs` (`MobsSection`): vida, daño y efectos para parásito, acechador, gusano y centinela (#31).
     - `cliente` (`ClientSection`): `intensidadVibracionCamara`, `mostrarTierraEnCielo`, `brilloEstrellasKepler`.
   - Si un campo tiene tipo inválido, está fuera de rango o falta, `SpaceConfig` emite `event=config_invalid` en el log y aplica el default **solo a ese campo**, preservando el resto del archivo. Claves desconocidas se ignoran sin error.
   - Comandos administrativos (`/dolfinspace estado`, `/dolfinspace recargar`, alias `/space`) protegidos por `SpacePermissions` (`dolfinspace.admin` vía LuckPerms si está cargado, o nivel OP 2 vanilla).

3. **Pestaña creativa y registro de contenido (`SpaceContent`)**
   - Pestaña creativa `dolfinspace:space` (*"Espacio"*) con ícono `dolfinspace:regolito`.
   - Bloque base del planeta: `dolfinspace:regolito` (`SpaceContent.REGOLITO` / `SpaceContent.REGOLITO_ITEM`), minable con pico (`data/minecraft/tags/block/mineable/pickaxe.json`), con tabla de botín propia (`data/dolfinspace/loot_table/blocks/regolito.json`) y traducciones en `en_us`, `es_es`, `es_mx` y `es_ar`.

4. **Dimensión `dolfinspace:kepler` y cielo con la Tierra**
   - Definición data-driven para Minecraft 26.3:
     - `data/dolfinspace/dimension/kepler.json`
     - `data/dolfinspace/dimension_type/kepler.json` (`bed_rule` y `straw_bed_rule` con `explodes: true`, `respawn_anchor_works: true`, `cloud_color: "#00000000"`, `sky_color: "#000000"`, `fog_color: "#000000"`, `star_brightness: 1.0`, `timelines: "#dolfinspace:in_kepler"`).
     - `data/dolfinspace/timeline/kepler_orbit.json` y `data/dolfinspace/tags/timeline/in_kepler.json`.
     - `data/dolfinspace/worldgen/noise_settings/kepler.json` (`default_block: "dolfinspace:regolito"`, `disable_mob_generation: true`).
     - `data/dolfinspace/worldgen/material_rule/kepler.json` y `data/dolfinspace/worldgen/biome/kepler_plains.json` (`has_precipitation: false`, `downfall: 0.0`, `natural_mob_spawns` vacío).
   - **Cielo en cliente (`KeplerSkyMixin` sobre `SkyRenderer`)**:
     - En Minecraft 26.3, `DimensionRenderingRegistry` fue eliminado de `fabric-rendering-v1`. El renderizado del cielo de Kepler se realiza mediante `KeplerSkyMixin` interceptando `SkyRenderer.renderSunMoonAndStars` cuando el cliente está en `dolfinspace:kepler`, dibujando el fondo estrellado permanente y el sprite `dolfinspace:earth` (`assets/dolfinspace/textures/environment/celestial/earth.png`, integrado automáticamente en el atlas `minecraft:celestials`).
   - **Supresión de mobs vanilla**: además de `disable_mob_generation: true` y listas vacías en el bioma, `NaturalSpawnerMixin` y `ServerLevelSpawnersMixin` bloquean cualquier intento de spawn natural de entidades que no pertenezcan al namespace `dolfinspace:`.

5. **Gravedad reducida al 40% (`KeplerDimension`)**
   - Aplica modificadores transitorios (`AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL`) con identificadores propios del mod:
     - `dolfinspace:kepler_gravity` sobre `Attributes.GRAVITY` (`-0.60` para multiplicador `0.40`).
     - `dolfinspace:kepler_fall_damage` sobre `Attributes.FALL_DAMAGE_MULTIPLIER` (`-0.60` para multiplicador `0.40`).
     - `dolfinspace:kepler_safe_fall` sobre `Attributes.SAFE_FALL_DISTANCE` (`+1.50` para multiplicador `2.50`).
   - Ciclo de vida garantizado: se sincroniza al entrar a Kepler, cargar entidad, unirse al servidor o reaparecer, y se **limpia explícitamente** al salir de Kepler (`AFTER_PLAYER_CHANGE_LEVEL` / `AFTER_ENTITY_CHANGE_LEVEL`), desconectarse (`ServerPlayConnectionEvents.DISCONNECT`) o morir (`ServerLivingEntityEvents.AFTER_DEATH`).

6. **Control de acceso a Kepler (`SpaceTravelAccess`)**
   - Bloquea portales (`EntityTravelMixin`, `ServerPlayerTravelMixin`) y `/tp` directo hacia `dolfinspace:kepler` (`TeleportCommandMixin`).
   - Permite acceso mediante `/execute in dolfinspace:kepler run ...` únicamente a administradores (`ExecuteCommandMixin` + `CommandSourceStackMixin` + `SpacePermissions.isAdmin`).
   - Provee `SpaceTravelAccess.runAuthorized(entity, reason, runnable)` y `supplyAuthorized(...)` para que el sistema de naves (#28) autorice el viaje espacial de forma limpia y thread-safe.

7. **Verificación de modpack en cliente (`SpaceModpackCheck`)**
   - Registra el payload de configuración `dolfinspace:handshake` (`SpaceHandshakePayload`).
   - Durante `ServerConfigurationConnectionEvents.BEFORE_CONFIGURE`, verifica si el cliente soporta el canal de `dolfinspace`. Si no tiene el mod instalado (o cuando falla la sincronización de registros en `ServerCommonPacketListenerMixin`), desconecta al jugador con el mensaje claro configurado en `general.mensajeSinMod`.

## Decisiones técnicas registradas

- **D1–D13 (Configuración)**: Todos los valores sugeridos en la épica #24 quedaron plasmados como defaults en `SpaceConfig`.
- **Modelos de entidades en Minecraft 26.3**: GeckoLib no tiene versión publicada para Minecraft 26.3; las naves (#27) y mobs (#31) deben implementarse con modelos de entidad vanilla (`EntityModel` / `EntityRenderer` estándar de Minecraft 26.3).
- **Renderizado de cielo en Minecraft 26.3**: Ante la eliminación de `DimensionRenderingRegistry` en Fabric API 26.3, se utiliza `SkyRenderer` + el atlas `celestials` (`dolfinspace:earth`) junto con los nuevos atributos data-driven de `dimension_type` (`minecraft:visual/*` y `timeline`).

## Eventos de log estructurados (`dolfinspace`)

- `event=startup`: inicio del mod con archivo de configuración, generación, dimensión, multiplicador de gravedad y proveedor de permisos.
- `event=config_invalid`: campo inválido o fuera de rango y fallback al valor por defecto.
- `event=config_reloaded`: recarga de configuración mediante `/dolfinspace recargar`.
- `event=content_registered`: bloques, ítems y pestaña creativa registrados.
- `event=kepler_gravity_applied` / `event=kepler_gravity_removed`: aplicación y limpieza de modificadores de baja gravedad.
- `event=kepler_weather_cleared`: limpieza de lluvia/tormenta si se intenta forzar clima en Kepler.
- `event=kepler_portal_blocked` / `event=kepler_tp_blocked` / `event=kepler_travel_blocked`: intentos bloqueados de entrar a Kepler sin nave.
- `event=kepler_execute_in`: uso autorizado de `/execute in dolfinspace:kepler` por un administrador.
- `event=client_mod_verified` / `event=client_missing_mod`: verificación de modpack al conectar.

## Puntos de extensión para tickets #26–#33

- **#26 (Máquinas y recursos)**:
  - Usar `SpaceContent.registerBlockWithItem(path, factory, settings)` y `SpaceContent.registerItem(path, factory, settings)` para registrar minerales, lingotes, circuitos, combustible y máquinas; se añaden automáticamente a la pestaña creativa `dolfinspace:space`.
  - Leer tiempos de procesado desde `DolfinSpace.config().current().maquinas()`.
- **#27 (Naves)**:
  - Leer configuración de naves desde `DolfinSpace.config().current().naves()`.
- **#28 (Viaje espacial)**:
  - Envolver la transición dimensional de la nave y sus pasajeros con `SpaceTravelAccess.runAuthorized(entity, "ship_travel", () -> entity.teleport(...))` hacia `ServerLevel` de `KeplerDimension.LEVEL`.
  - Leer `DolfinSpace.config().current().cliente().intensidadVibracionCamara()` en cliente para la vibración de despegue/aterrizaje.
- **#29 (Oxígeno)**:
  - Consultar `KeplerDimension.isKepler(level.dimension())` y `DolfinSpace.config().current().oxigeno()`.
- **#30 (Biomas y estructuras de Kepler)**:
  - Ampliar `data/dolfinspace/dimension/kepler.json`, `data/dolfinspace/worldgen/noise_settings/kepler.json` y `data/dolfinspace/worldgen/material_rule/kepler.json` usando `SpaceContent.REGOLITO` como bloque base.
- **#31 (Mobs hostiles de Kepler)**:
  - Registrar entidades bajo el namespace `dolfinspace:`; `KeplerDimension.isAllowedKeplerNaturalSpawnId` ya permite automáticamente el spawn natural de entidades `dolfinspace:*` mientras mantiene bloqueados los mobs `minecraft:*`.
  - Leer estadísticas desde `DolfinSpace.config().current().mobs()`.
