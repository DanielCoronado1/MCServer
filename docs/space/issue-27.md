# #27 — Construcción de naves

Piezas fabricables, mesa con siete casillas de plantilla y salida, tres botones de selección y validación exclusivamente en servidor. Cantidades exactas de #27; cada slot rechaza otras piezas. Casillas incompletas rojas y tooltip con cantidad/pieza constituyen la guía integrada. Para seleccionar RÁPIDA se requieren dos circuitos avanzados y titanio (etiqueta o pieza de titanio) en inventario. Cambiar plantilla requiere vaciar la mesa.

Plataforma de 3×3 para básica/rápida, 5×5 para carga. Se rechazan centros desplazados por simetría de bordes. El bloque central se marca al colocar una nave. La colocación comprueba despeje y naves existentes; Kepler permite suelo sólido. Romper la plataforma no elimina la nave; #28 revalida antes del despegue.

Cargador adyacente al borde: entrada manual o tolva (excepto abajo), salida de bidones vacíos abajo o clic vacío. Un bidón transfiere 25 puntos porcentuales; sin espacio de salida o con tanque >75% no se consume. Combustible interno 0–100.

Nave invulnerable con modelo vanilla provisional. Dueño UUID y aliados OPAC pueden pilotar; API 0.32.8 verificada mediante javap del JAR fijado en pack. Ausencia/error de API deniega aliados y registra `ship_opac_unavailable`. Piloto con palanca + clic permite/bloquea pasajeros; #28 añade botón en cabina. Dueño agachado recoge únicamente nave vacía, conservando componentes CUSTOM_DATA y CONTAINER. Carga: clic con cofre abre 27 slots, solo dueño/aliados; menú revalida permiso y distancia.

`ShipState.CODEC` comparte formato entre entidad y objeto: dueño, tier, combustible, origen y consentimiento. Carga persistida con ContainerHelper / ItemContainerContents. Hooks `onPilotMounted` y `flightTick` permiten a #28 integrar el viaje.

Logs: INFO `ships_registered`, `ship_assembled`, `ship_placed`, `ship_board`, `ship_pickup`, `ship_passenger_consent`; DEBUG `assembly_rejected`, `ship_access_rejected`, `ship_placement_rejected`, `ship_damage_rejected`, `ship_refueled`, `ship_load/save`, `loader_load/save`, `assembly_load/save`, `ship_cargo_changed`.

Build Java 25: 19 tests pasan (4 de reglas de naves: plataforma/bloques rotos, plantilla/casillas, transferencia, codec). Sin lanzamientos de cliente. Pendiente validar visualmente modelo/GUI, OPAC real, tolvas, pasajeros y reinicio. Modelos y sonidos finales son #32. Capacidad de carga fijada en 27 según ticket; no se aplica `ranurasInventarioCarga` para evitar truncar objetos tras recargar.
