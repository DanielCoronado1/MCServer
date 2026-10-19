# #31 — Fauna hostil de Kepler

## Implementación

Código común en `.mobs`, mixins en `.mobs.mixin` y renderizado en `.client.mobs`. Un hook común `MobsModule.init()` y uno cliente. Siete mobs, huevos, tres drops, proyectil, flecha y nube. Los cinco biomas usan el formato 26.3 `natural_mob_spawns` con `count` uniforme. Parásito: peso 100 en cavernas / 35 en superficie. Spawns solo en Kepler y fuera de pacífico.

- **Parásito**: 0.5 × 0.5, velocidad 0.36, vida 6 configurable. Salta a <4 bloques y se monta al contacto mediante `startRiding`, máximo uno por jugador. Minecraft 26.3 rechaza vehículos no serializables incluso con force=true; `ParasiteHeadMixin` permite únicamente parásito→jugador (`noSave`) y coloca el pasajero en ojos/casco. `ParasiteCycle`: 1 daño cada 40 ticks; tercer ataque aplica Levitación 100 ticks, detacha y huye 100 ticks. `SneakCounter` cuenta tres flancos dentro de 40 ticks, no mantener la tecla. Un golpe de cualquier jugador también lo quita. Exentos creativo/espectador/nave/muertos/desconectados. Eventos de muerte/desconexión limpian pasajeros. Cliente detecta pasajero sincronizado para el tinte verde. Daño y mensaje de muerte propios.
- **Acechador**: Spider vanilla, navegación trepadora, cuerpo a cuerpo, animaciones y sonidos heredados. Vida 16/daño 4 desde config, grupos 2–3, ventana nocturna 13000–23000 del reloj de dimensión. Drop quitina.
- **Gusano**: Silverfish con metas propias, sin infestación ni rotura de bloques. Fases superficie 60 ticks / enterrado 40–160 ticks, emerge cerca de jugador y muerde por 6. Enterrado invisible, sin física/gravedad, movimiento validado por columna/huella y partículas de bloque. No consulta heightmap global de superficie para atravesar techos de cuevas. Drop diente.
- **Centinela**: Ghast de un bloque, altura local suelo+8±1 y diez bloques libres requeridos al spawn. Proyectil no explosivo/incendiario cada 60 ticks, daño 5 configurable; recibe x2 de cualquier AbstractArrow. Drop núcleo.
- **Variantes**: zombi con IA vanilla, sin conversión a ahogado/refuerzos vanilla; esqueleto dispara flecha de cristal con Lentitud I 100 ticks; creeper conserva camino de explosión vanilla y añade nube ácida 200 ticks después de explotar. Space no reemplaza/cancela la explosión.

Estadísticas desde `DolfinSpace.config().current().mobs()`: vida/ataque del acechador al crear entidad; ataques especiales/duración de levitación consultados al atacar. Gravedad y caída siguen #25; ninguna protección adicional después de levitar.

## Terreno construido y claims

`TerrainHistory`: attachment persistente de posiciones por chunk. Mixin en `LevelChunk.setBlockState` marca escrituras de regolito/roca lunar posteriores a generación, incluso comandos/pistones; quitar bloque elimina marca. Gusanos solo aceptan esos dos bloques sin marca y nunca remueven terreno. Si una construcción encierra un gusano enterrado, se queda inmóvil en lugar de atravesarla. No es posible reconocer retroactivamente construcciones anteriores a esta implementación; si existieran necesitarían migración conservadora.

La nube no coloca lodo ni cambia bloques. `mobGriefing=false` impide crearla/aplicar veneno. Con `general.respetarClaims=true`, consulta por reflexión opcional la API pública de OPAC 0.32.8: `OpenPACServerAPI.get → getServerClaimsManager → IClaimsManagerAPI.get(Identifier,BlockPos)`. Rechaza cualquier claim en origen y por víctima, incluidos límites entre chunks. Política conservadora incluso si el claim permite otros daños. Si OPAC está instalado pero falla el bridge, deshabilita peligros ácidos y registra WARN una vez. La explosión queda bajo protecciones vanilla/OPAC y el mod de creepers: su mixin solo intercepta `VarianteCreeper`; Space no evita ese camino.

## Assets y recetas

EntityModel/EntityRenderer vanilla con animaciones y sonidos heredados, texturas procedurales originales, huevos y cuatro locales. Placeholders permitidos hasta #32. Proyectil muestra cristal de energía; flecha tiene textura cian; nube usa partículas vanilla y NoopRenderer.

Quitina+cristal → escudo vanilla; tres dientes+palos → pico de hierro vanilla; núcleo → ocho cristales. Todos los drops son útiles. Integración final: filtro puro de tanques verifica namespace/ID y se utiliza en TankItem; logs de pulsos/exenciones; hongo alimenta compresor; cavernas con suelo luminoso de cristal.

Regenerar con `tools/space/generate_assets.py` (regenera recursos, biomas y fauna de forma integrada).

## Observabilidad

INFO `fauna_registered`. DEBUG `parasite_leap/latched/attack/detached/latch_rejected/loaded/saved`, `fauna_target_rejected`, `worm_state/bite/burrow_rejected/saved`, `terrain_provenance_changed`, `sentinel_fire/projectile_hit/loaded/saved`, `acid_cloud_spawned/loaded/saved`, `acid_hazard_rejected`, `oxygen_pulse`. WARN `acid_claim_guard_failed`.

## Validación y límites

`./gradlew build --no-daemon`, JDK 25 especificado: **34 JUnit**, cero fallos. Pruebas de salto/contacto/límite uno, exenciones, tres ataques/levitación, sneak por flancos/expiración, terreno natural, noche, cooldown, flechas, wiring de cinco biomas, pesos/grupos, huevos/texturas/loot/renderers, recetas y paquetes de mixins.

Único smoke #31: servidor arrancó/cerró limpiamente, sin ERROR; cuatro regiones de Kepler forzadas a generar, siete summons exitosos y creeper encendido. Log ignorado `runtime/space/smoke.log`; #30 conservado en `runtime/space/issue-30-smoke.log`. Sin jugadores conectados: no demuestra combate, head-latch ni NBT de jugador. Consulta del compresor no encontró block entity porque el fixture no llegó a colocarlo.

Fixture adicional `src/smoke` (`dolfinspace_smoke`) compila y está excluido del JAR. Está diseñado para probar objetos reales: filtros/clic/arrastre/shift-clic/creativo/NBT/compresor/montaje/ataques. No se cargó en el único arranque por faltar dependencia `processSmokeResources`. Se corrigieron `check` y `runServer` para depender de `smokeClasses`; `runServer --dry-run` confirma grafo. **No se repitió servidor** por el límite de uno por ticket. Script falla si falta `space_runtime_probe_complete`; su salida 1 aquí indica fixture ausente, no crash. Sus aserciones quedan sin verificar.

Sin cliente ni Chunky. Pendiente: aspecto/HUD/tinte/sonidos, latch/combate/caída con jugadores, muerte/reinicio de tanques, automatización/compresor real, persistence de provenance tras restart, OPAC+creepers juntos, balance y Chunky. Retoques finales de paleta/plantas verificados por build/recursos sin nuevo arranque.
