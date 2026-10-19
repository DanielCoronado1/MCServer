# Plan de Pruebas — DolfinSpace (`dolfinspace:kepler`)

Lista de verificación previa a publicación para el sistema espacial y la dimensión Kepler, cubriendo compatibilidad con mods de administración del servidor (OPAC, PvP, Ledger, LuckPerms), progresión completa de crafteo y vuelo, desconexión/recuperación, supervivencia/combate y rendimiento bajo carga.

---

## 1. Compatibilidad con OPAC, PvP, Ledger y LuckPerms

### 1.1 Protección de claims OPAC (`openpartiesandclaims`)
- [ ] **Bloques en Kepler**: Con `general.respetarClaims = true`, reclamar un chunk en `dolfinspace:kepler` con la Cuenta A (`/openpac-claims claim`).Con la Cuenta B (fuera de la party y sin permisos), intentar romper y colocar bloques (`dolfinspace:regolito`, `dolfinspace:roca_lunar`, menas, bloques vanilla): ambas acciones deben cancelarse (`SpaceClaims.allowed` consulta `IChunkProtectionAPI.onBlockInteraction` / `onEntityPlaceBlock`).
- [ ] **Contenedores y máquinas**: Colocar `compresor`, `refineria`, `fabricador_circuitos`, `compresor_oxigeno`, `mesa_ensamblaje` y `cargador_combustible` dentro de la claim de la Cuenta A. Verificar que la Cuenta B no puede abrir sus menús ni cargar bidones en `FuelLoaderBlock`, y que un contenedor abierto se cierra si pierde permisos (`ContainerAccessMixin` en `canOpen` y `stillValid`).
- [ ] **Colocación y uso de naves**: Intentar colocar `nave_basica`, `nave_carga` o `nave_rapida` con la Cuenta B sobre una plataforma dentro o solapando el borde de la claim de la Cuenta A (`SpaceClaims.shipPlacement` verifica toda la huella `(2r+1)×(2r+1)`, incluido radio 2 para `CARGA` 5×5). Debe rechazarse sin consumir el ítem. Verificar que la Cuenta B tampoco puede montar, abrir la bodega ni recoger una nave estacionada en claim ajena (`SpaceClaims.entityUse`).

### 1.2 PvP en Kepler y reglas por claim
- [ ] **PvP fuera de claims**: Con `pvp = true` en gamerules, verificar que dos jugadores pueden dañarse cuerpo a cuerpo y con proyectiles en terreno no reclamado de Kepler.
- [ ] **PvP protegido en claims**: En un chunk reclamado con protección PvP de OPAC activa, verificar que ataques directos y proyectiles entre jugadores se bloquean (`SpaceCompat` en `ServerLivingEntityEvents.ALLOW_DAMAGE` combina `GameRules.PVP`, `attacker.canHarmPlayer(target)` y `SpaceClaims.pvp`). Al habilitar PvP en la configuración de la claim, el daño vuelve a permitirse.

### 1.3 Auditoría en Ledger (`ledger`)
- [ ] **Rotura y colocación de bloques**: Romper y colocar bloques en `dolfinspace:kepler` y consultar `/ledger inspect` o `/ledger search world:dolfinspace:kepler`: verificar entradas nativas y las acciones de auditoría `space-break` y `space-place` (`SpaceLedgerIntegration`). Intentos cancelados por OPAC no deben registrarse.
- [ ] **Apertura de contenedores**: Abrir máquinas espaciales, el compresor de oxígeno, el cargador de combustible, la mesa de ensamblaje y la bodega de `nave_carga` en Kepler; verificar que Ledger registra la acción `space-open` con actor, coordenadas, dimensión `dolfinspace:kepler` y bloque/objeto asociado.

### 1.4 Permisos con LuckPerms (`dolfinspace.admin`)
- [ ] **Bloqueo sin permiso**: Con una cuenta sin OP (nivel < 2) y sin el nodo `dolfinspace.admin` en LuckPerms, verificar que `/space reload` (`/space recargar`) y `/space nave recuperar <jugador>` no están disponibles y se rechazan.
- [ ] **Concesión en LuckPerms**: Otorgar `lp user <jugador> permission set dolfinspace.admin true` y verificar que el jugador puede ejecutar `/space reload`, `/space recargar` y `/space nave recuperar <jugador>`, encolando el retorno administrativo de las naves del propietario con combustible de regreso `0` (`event=ship_admin_recovery_queued`).

---

## 2. Progresión completa: del primer lingote de acero al viaje ida y vuelta

- [ ] **Acero y máquinas base**:
  1. Craftear `mezcla_acero` (hierro + carbón) y fundirla en horno/alto horno para obtener el primer `lingote_acero` y `bloque_acero`.
  2. Construir y colocar `compresor`, `fabricador_circuitos`, `refineria`, `compresor_oxigeno`, `mesa_ensamblaje` y `cargador_combustible`.
  3. Prensar placas (`placa_acero`, `placa_cobre`, `placa_oro`, `placa_titanio`) en el `compresor` usando carbón como combustible.
  4. Fabricar `circuito_basico` (redstone + cuarzo + placa de oro) y `circuito_avanzado` (circuito básico + diamante + cristal de energía) en el `fabricador_circuitos`.
  5. Refinar `bidon_combustible` en la `refineria` (cubeta de lava + polvo de blaze + `bidon_vacio`, verificando la devolución de la cubeta vacía).
- [ ] **Ensamblaje y vuelo ida y vuelta con las 3 naves**:
  - **Nave Básica (`nave_basica`, 1 asiento, cuenta 10 s, plataforma 3×3)**:
    - [ ] Ensamblar componentes (`punta_conica`, `placa_casco`, `aleta_nave`, `motor_nave`, `tanque_combustible_nave`, `asiento_piloto`) en la `mesa_ensamblaje`.
    - [ ] Construir plataforma de lanzamiento 3×3 de `bloque_plataforma`, colocar la nave, conectar `cargador_combustible` adyacente y cargar al 100% (2 bidones).
    - [ ] Montar, abrir cabina (`R`), verificar checklist (plataforma, cielo despejado, combustible 100%, tanque de oxígeno), lanzar hacia Kepler, recolectar titanio/cristales, y regresar al Overworld (50% en ida + 50% en vuelta).
  - **Nave de Carga (`nave_carga`, 1 asiento + 27 slots de bodega, cuenta 10 s, plataforma 5×5)**:
    - [ ] Ensamblar en `mesa_ensamblaje`, colocar sobre plataforma 5×5, cargar combustible al 100%, guardar ítems en los 27 slots de carga (clic con cofre), volar a Kepler y de vuelta al Overworld verificando que el inventario de carga se conserva íntegro.
  - **Nave Rápida (`nave_rapida`, 2 asientos piloto + pasajero, cuenta 5 s, plataforma 3×3)**:
    - [ ] Ensamblar con `punta_titanio`, `placa_casco_titanio`, `motor_avanzado` y `circuito_avanzado`.
    - [ ] Activar permiso de pasajeros (clic con palanca), embarcar piloto y pasajero con tanques equipados, verificar cuenta regresiva de 5 s y completar viaje de ida y vuelta con ambos ocupantes montados en orden.

---

## 3. Desconexión y reinicio del servidor

- [ ] **En la plataforma (durante cuenta regresiva)**:
  - Iniciar la cuenta regresiva de despegue y desconectar al piloto o reiniciar el servidor antes de `ticks == 0`.
  - Verificar que el despegue se cancela sin descontar combustible (`porcentajeCombustiblePorViaje` intacto) y la nave permanece estacionada en la plataforma.
- [ ] **En pleno vuelo (ascenso, transición o descenso)**:
  - Desconectar al piloto (y pasajero en `nave_rapida`) o reiniciar el servidor durante el ascenso (200 ticks), transición estelar (20 ticks) o descenso (80 ticks).
  - Verificar que `FlightLedger` (`data/dolfinspace/flight_ledger.dat`) persiste el vuelo, la nave aterriza de forma segura en destino y, al reconectar, los jugadores completan la llegada pendiente y aparecen montados en su orden original (`event=ship_reconnect_arrival`).
- [ ] **En el planeta Kepler**:
  - Desconectar jugadores en la superficie de Kepler y reiniciar el servidor con naves estacionadas y cargadas en distintos chunks.
  - Al reconectar, verificar que posición, dimensión `dolfinspace:kepler`, oxígeno restante del tanque equipado, dueño/combustible/carga de la nave y marcas de terreno (`TerrainHistory`) se conservan sin pérdidas.

---

## 4. Supervivencia: oxígeno, parásito, muertes y recarga de tanques

- [ ] **Tanque vacío, cambio y recarga**:
  - Equipar `tanque_oxigeno_chico` (300 s), `tanque_oxigeno_mediano` (600 s) y `tanque_oxigeno_grande` (1200 s) en la ranura de oxígeno.
  - Verificar aviso en HUD y sonido al bajar del 20% de capacidad.
  - Cambiar en caliente un tanque vacío por uno lleno desde el inventario en Kepler y verificar que la telemetría de cabina y HUD actualiza segundos y porcentaje inmediatamente sin reiniciar el pulso.
  - Recargar tanques en el `compresor_oxigeno` usando hojas/plantas/hongos bioluminiscentes (`#dolfinspace:plantas_compresor`), verificando que suma 60 s por ciclo hasta el tope del tanque.
- [ ] **Muerte por asfixia**:
  - Vaciar el tanque o desequiparlo en Kepler en modo supervivencia; verificar daño de asfixia periódica tras agotarse el oxígeno y el mensaje de muerte `death.attack.dolfinspace.oxygen` (o `.player` en combate).
- [ ] **Muerte por el parásito (`dolfinspace:parasito`)**:
  - Dejar que un parásito salte (< 4 bloques) y se adhiera a la cabeza del jugador: verificar tinte verdoso en pantalla, daño cada 40 ticks, levitación (100 ticks) al tercer ataque y desprendimiento al pulsar Shift 3 veces en < 2 s o recibir un golpe de otro jugador.
  - Dejar que el daño del parásito mate al jugador y verificar mensaje `death.attack.dolfinspace.parasito` y limpieza del pasajero al morir.
- [ ] **Muerte en PvP y recuperación de nave**:
  - Morir en combate PvP en Kepler y reaparecer en el Overworld: con `retornoAutomaticoAlReaparecerFuera = true`, verificar que la nave del dueño regresa automáticamente a su plataforma de origen (o fallback junto al spawn) con combustible `0` y su carga intacta (`event=ship_auto_return`), o bien mediante `/space nave recuperar <jugador>`.

---

## 5. Rendimiento con 15 jugadores y perfilado Spark

- [ ] **Stack de optimización activo**:
  - Verificar en el arranque del servidor que están cargados `c2me`, `lithium`, `vmp` (Very Many Players), `scalablelux` y `spark` junto con `dolfinspace`, `openpartiesandclaims`, `ledger` y `luckperms`.
- [ ] **Prueba de carga en Kepler (15 jugadores simultáneos)**:
  - Conectar 15 jugadores (o bots de prueba con clientes/carpet) distribuidos en los 5 biomas de `dolfinspace:kepler` (`kepler_plains`, `kepler_craters`, `kepler_crystal_forest`, `kepler_acid_marshes`, `kepler_abyssal_caverns`), generando chunks, combatiendo fauna hostil, operando máquinas y realizando vuelos.
  - Ejecutar `/spark profiler start --timeout 180` y `/spark tps` durante al menos 3 minutos de actividad sostenida.
  - **Criterio de aceptación**: TPS promedio **≥ 18.0** (MSPT ≤ 55 ms) sin picos de bloqueo en generación de chunks, ticks de `SpaceshipEntity`, `FlightLedger`, pulsos de oxígeno ni IA de fauna espacial.
