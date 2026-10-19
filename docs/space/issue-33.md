# #33 — Compatibilidad con OPAC, PvP, Ledger y LuckPerms, y plan de pruebas

## Implementación

Integración en `com.dolfin.space.compat` y mixins en `com.dolfin.space.compat.mixin` (`dolfinspace.compat.mixins.json`), inicializada desde `DolfinSpace.onInitialize()` mediante `SpaceCompat.init()`. Todos los adaptadores contra mods externos son opcionales y reflexivos (`openpartiesandclaims`, `ledger`, `luckperms` declarados en `suggests` de `fabric.mod.json`).

- **OPAC (`SpaceClaims` + `CompatPolicy`)**:
  - Consulta la API pública de Open Parties and Claims 0.32.8 (`OpenPACServerAPI.get(server).getChunkProtection()`): `onBlockInteraction`, `onEntityPlaceBlock` y `onEntityInteraction`. En OPAC `true` significa acción bloqueada; `CompatPolicy.allowed` jamás invierte la decisión y falla cerrado (`false`) si OPAC está cargado, `general.respetarClaims = true` y la invocación reflexiva falla (`denied == null`).
  - Rotura de bloques en Kepler protegida en `PlayerBlockBreakEvents.BEFORE`; colocación de bloques en Kepler protegida en `BlockPlacementMixin` (`BlockItem.place` HEAD).
  - Contenedores de máquinas, compresor de oxígeno, mesa de ensamblaje y cargador de combustible protegidos en `ContainerAccessMixin` (`BaseContainerBlockEntity.canOpen` y revalidación en `stillValid`) más `FuelLoaderBlock.useItemOn`.
  - Colocación de naves en `ShipItem.useOn` valida toda la huella de la plataforma `(2r+1)×(2r+1)` mediante `SpaceClaims.shipPlacement` (3×3 para `BASICA`/`RAPIDA`, 5×5 para `CARGA`), impidiendo colocar una nave que pise total o parcialmente una claim ajena. `SpaceshipEntity.interact` y `cargo.stillValid` verifican `SpaceClaims.entityUse`.
- **PvP (`SpaceCompat`)**:
  - `ServerLivingEntityEvents.ALLOW_DAMAGE` intercepta daño entre `ServerPlayer` en `dolfinspace:kepler` y evalúa `CompatPolicy.pvp(GameRules.PVP, attacker.canHarmPlayer(target), SpaceClaims.pvp(attacker, target, source.getDirectEntity()))`, respetando tanto la gamerule global y equipos como la regla de PvP de cada claim de OPAC (incluidos proyectiles mediante `directEntity`).
- **Ledger (`SpaceLedgerIntegration` + `LedgerActionProxy`)**:
  - En `SERVER_STARTED`, si `ledger` está cargado, registra por reflexión tres `ActionType` de auditoría (`space-break`, `space-place`, `space-open`) en `ActionRegistry.INSTANCE` mediante un proxy dinámico (`LedgerActionProxy`) sin rollback de mundo (`rollback` devuelve `false`), conservando intacto el registro nativo de Ledger.
  - `SpaceLedgerIntegration.route` filtra exclusivamente eventos exitosos ocurridos en servidor dentro de `dolfinspace:kepler`: rotura tras éxito (`PlayerBlockBreakEvents.AFTER`), colocación cuando `InteractionResult.consumesAction()` es verdadero (`BlockPlacementMixin` RETURN), y apertura de contenedores cuando `createMenu` devuelve un menú no nulo (`ContainerAccessMixin` RETURN) o al abrir la bodega de `nave_carga`.
- **LuckPerms y comandos (`SpacePermissions`, `SpaceCommands`, `RecoveryCommandTree`, `RecoveryRequests`)**:
  - `SpacePermissions.isAdmin` consulta `me.lucko.fabric.api.permissions.v0.Permissions.check(source, "dolfinspace.admin", 2)` cuando `fabric-permissions-api-v0` / LuckPerms está presente, con fallback a nivel OP 2.
  - Protege `/space reload` (`/space recargar`) y el nuevo subárbol `/space nave recuperar <jugador>` (`RecoveryCommandTree`), que sincroniza en `FlightLedger` las naves cargadas del jugador indicado y marca `autoReturn = true` de forma idempotente y durable (`RecoveryRequests.queue`), devolviéndolas con combustible `0`.

## Plan de pruebas

Documentado en `docs/espacio/PRUEBAS.md` con listas de verificación detalladas para:
1. Claims de OPAC (bloques, máquinas/contenedores y huella completa de naves), PvP por claim, auditoría de Ledger (`space-break`, `space-place`, `space-open`) y permiso `dolfinspace.admin` en LuckPerms.
2. Progresión completa desde el primer lingote de acero hasta el ensamblaje y vuelo de ida y vuelta con `nave_basica`, `nave_carga` y `nave_rapida`.
3. Desconexión y reinicio del servidor en plataforma (durante cuenta regresiva), en pleno vuelo y en la superficie de Kepler.
4. Supervivencia: agotamiento, cambio en caliente y recarga de tanques de oxígeno, muerte por asfixia, muerte por parásito y muerte en PvP con recuperación de nave.
5. Prueba de carga con 15 jugadores en Kepler verificando TPS ≥ 18 con `spark` y `c2me`, `lithium`, `vmp` y `ScalableLux` activos.

## Validación

- `./gradlew build --no-daemon` con JDK 25: **68 tests JUnit** pasan (`SpaceCompatTest` añade 8 tests unitarios de políticas OPAC fail-closed, huella 5×5 de `CARGA` en bordes de claims, matriz booleana de PvP, enrutado de Ledger solo en Kepler tras éxito, proxy reflexivo `ActionType`, cola durable/idempotente de recuperación administrativa, árbol Brigadier `/space nave recuperar <jugador>` y presencia de hooks en mixins/bloques/entidades; `SpaceAssetsAuditTest` valida mixins y paridad de traducciones en los 4 idiomas).
