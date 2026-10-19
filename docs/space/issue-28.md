# #28 — Cabina y viaje dimensional

## Implementación

`TravelModule.init()` integra cabina, red, vuelo y recuperación sin importar clases de #29–#31. `ShipsModule` entrega los hooks de montaje/tick. La cámara usa un archivo de mixins propio (`dolfinspace.travel.mixins.json`) para reducir conflictos al fusionar ramas.

Cabina `CockpitMenu` / `CockpitScreen`: se abre al montar como piloto; R cierra/reabre. Muestra destino, combustible/mínimo, oxígeno de ambos ocupantes, plataforma/suelo y columna de ascenso, advertencia de tanque, cuenta regresiva y consentimiento de pasajeros. Shift envía una petición validada al servidor para bajar/cancelar antes del despegue. Cambiar cualquier ocupante durante la cuenta también cancela. Sin condiciones obligatorias, LANZAR permanece desactivado; el servidor vuelve a validar al pulsarlo y durante la cuenta.

`OxygenStatusBridge.install(Function<Player, Status>)` permite a #29 suministrar el porcentaje real. El fallback detecta `dolfinspace:tanque_oxigeno_*` en inventario, lee `oxygen_percent` de CUSTOM_DATA o durabilidad si existe. Si encuentra tanque sin nivel interpretable, muestra **nivel no disponible**, nunca lo presenta lleno. El tanque es aviso, no condición obligatoria.

Sonidos vanilla provisionales en categoría `neutral`: beacon ambiente en bucle mientras se está montado, pling para hover/checklist/cuenta, bajo para alarma de combustible. Vibración cliente desde `cliente.intensidadVibracionCamara` (0.0–1.0); botón en cabina recorre 0/25/50/75/100% y guarda la opción local. Mixin `Camera.update` recibe `{inicio, intensidad}`; escala/clampa y descarta paquetes viejos después de 700 ms. 0 desactiva el desplazamiento.

## Secuencia y configuración

- Cuenta: `naves.segundosCuentaRegresivaBasica/Carga/Rapida` (10/10/5 s).
- Despegue: descuenta `porcentajeCombustiblePorViaje` (50). Desde Tierra exige `max(porcentajeCombustibleMinimoDespegueTierra, 2 × coste)` (100 por defecto), para reservar vuelta. Un coste configurado >50 impide despegar desde Tierra, en lugar de permitir un viaje sin reserva.
- Ascenso: 200 ticks, `alturaAscensoDespegue` (100 bloques), limitado por techo del mundo. Humo, llama y vibración progresiva durante últimos 3 s y ascenso.
- Transición: 20 ticks de partículas y pantalla de estrellas. `SpaceTravelAccess.supplyAuthorized/runAuthorized` envuelven **cada** teletransporte. En mappings Mojang 26.3 la API es `TeleportTransition` / `Entity.teleport`, equivalente al TeleportTarget citado por el ticket. Se desmontan y trasladan nave y ocupantes online, y se vuelven a montar en orden.
- Descenso: 80 ticks desde 20 bloques de altura (0.25 bloque/tick), partículas de nubes. Revalida suelo al aterrizar.

`SafeLanding` busca en radio 32 una superficie de 5×5 con cuatro bloques libres; rechaza fluidos, lava, agua, ácido/acido, magma, fuego, cactus y fogatas. Carga todo el footprint **antes** de consultar altura y usa `WORLD_SURFACE`: en 26.3 los heightmaps MOTION_BLOCKING dependen de etiquetas vanilla que pueden omitir bloques de otros mods. Si no hay área plana, construye 5×5 de piedra lisa cuatro bloques por encima del terreno más alto, sin despejar estructuras. Si no hay espacio, conserva el vuelo y registra el fallo en vez de derribar el servidor.

La vuelta usa la plataforma `home` persistida si sigue íntegra y despejada; de lo contrario busca cerca del respawn del piloto/dueño online o del spawn mundial si está offline. La plataforma se revalida incluso si fue destruida durante el vuelo.

## Persistencia y recuperación

La entidad guarda `FlightJournal` junto con combustible e inventario. `FlightLedger` es SavedData del Overworld (`data/dolfinspace/flight_ledger.dat`), indexa posición/dimensión, tripulación ordenada y llegadas pendientes. Solo vuelos/recuperaciones mantienen tickets temporales de chunks; naves estacionadas no los necesitan.

- Desconexión antes de despegar: cancela sin descontar combustible.
- Desconexión en ascenso/transición/descenso: registra llegada pendiente; el vuelo sigue sin jugadores. Al reconectar, completa la llegada y remonta conservando orden de piloto/pasajero.
- Reinicio: restaura diario; una cuenta previa al despegue se cancela, vuelos posteriores continúan.
- Dueño muerto en Kepler y reaparecido fuera: marca retorno automático, carga la nave aunque su chunk esté descargado y vuelve al origen/fallback con combustible 0. Conserva carga y lleva ocupantes presentes de forma autorizada. Config: `retornoAutomaticoAlReaparecerFuera`.

Eventos INFO: `travel_registered`, `ship_launch_rejected`, `ship_flight_transition`, `ship_fuel_consumed`, `ship_landed`, `ship_disconnect_pending`, `ship_reconnect_arrival`, `ship_auto_return`, `ship_home_fallback`, `ship_passenger_consent`. DEBUG: `flight_ledger_load/save`; WARN/ERROR: `ship_landing_platform`, `ship_transition_wait/failed`, `ship_flight_wait`, `ship_recovery_wait`, `client_shake_config_failed`.

## Evidencia y límites

`./gradlew build --no-daemon` con el JDK 25 indicado pasa: **29 tests** (10 base, 5 máquinas, 4 naves, 10 viaje). Viaje cubre checklist, reserva/coste, fluidos y obstáculos, escala de cámara, secuencia completa, codecs y política de recuperación, preparación de chunks y regresión con una muestra del terreno Kepler real guardado en `src/test/resources/com/dolfin/space/travel/kepler-surface.json`.

Se realizó **un solo servidor headless**, sin cliente. `tests/manual/prueba_space_26_28.py` deja mundo/logs en `mods/space/run/` ignorado y puede inspeccionar NBT sin arrancar (`--inspect --world <ruta>`). Se comprobó carga de registros/datapacks, funcionamiento de las tres máquinas, refinería devolviendo cubeta, cargador devolviendo dos bidones, nave con combustible 100, dueño/origen y tres diamantes de carga guardados, descenso en Overworld al origen y guardado limpio (no demuestra una vuelta dimensional, porque la ida anterior falló). `space_nbt.py` confirma siete invariantes en los archivos `.mca` guardados.

**El smoke no pasó completo**: encontró un fallo de aterrizaje hacia Kepler (heightmap de movimiento veía bedrock a -63 mientras WORLD_SURFACE veía regolito a 69). Se corrigió seleccionando WORLD_SURFACE y cargando chunks previamente, y se añadió regresión con esos datos reales. No se volvió a arrancar el servidor, respetando el límite. El primer script también consultaba máquinas antes del tiempo por usar una duración inválida de 4 ticks (el mínimo es 20); el NBT final confirmó sus productos y el script quedó corregido.

Pendiente en juego: GUI/modelo, mixin de cámara y sonidos; vuelo con piloto/pasajero hacia Kepler tras la corrección; reconexión real y reinicio; muerte con nave descargada; integración de porcentaje real con #29 y permisos OPAC. Los tests de recuperación verifican lógica/serialización, no reemplazan estos casos multijugador. Máquinas mantienen guía de recetas internas en su barra, sin libro vanilla para esos procesos (#26). Assets son provisionales para #32. CI (`.github/workflows/release.yml`) y release builder (`scripts/build_release.py:mods_propios`) recorren automáticamente `mods/*`, incluida space.
