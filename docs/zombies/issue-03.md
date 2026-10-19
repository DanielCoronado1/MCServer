# Issue #3 — Zombies: aparición natural y evento de luna de sangre

## Qué se implementó

### 1. Aparición natural y recarga en caliente (`SpawnRules`, `NaturalSpawnerMixin`, `SpawnPlacementsInvoker`)
- **Registro inicial:** al arrancar, `SpawnRules.init()` registra predicados `SpawnPlacements` (`IN_WATER` para `abismo_zombie`, `ON_GROUND` con `MOTION_BLOCKING_NO_LEAVES` para los otros 15) e invoca `BiomeModifications.addSpawn` (categoría `MONSTER`) con el `peso`, `grupo` (`[min, max]`), `biomas` y `biomasExcluidos` de la generación inicial (excluyendo `mimo_zombie`).
- **Filtro y pool dinámico sin reiniciar:** `NaturalSpawnerMixin` intercepta `NaturalSpawner.mobsAt` para la categoría `MONSTER` y delega en `SpawnRules.pool(level, pos, original)`. Este método:
  1. Descarta las entradas estáticas de `ConfiguredZombie` registradas al inicio.
  2. Evalúa en vivo la generación actual de `ZombieConfig` (`activo`, `peso`, `lunaDeSangre.pesoExtra`, `grupo`, `biomas`, `biomasExcluidos`, `alturaY`, `luzMax` y reglas especiales por tipo).
  3. Durante la luna de sangre con `soloEspeciales: true`, retira los zombies vanilla (`zombie`, `zombie_villager`, `husk`, `drowned`) y reparte su peso eliminado proporcionalmente entre los especiales elegibles en esa posición (`SpawnRules.substitute`).
- **Límite documentado de la recarga en caliente:** dado que Fabric congela las modificaciones de `BiomeModifications` al construir los registros del mundo, las herramientas o mods externos que inspeccionen directamente `Biome.getMobSettings()` seguirán viendo los pesos y biomas del arranque; sin embargo, el `NaturalSpawner` real del servidor consulta `NaturalSpawner.mobsAt` en cada intento de spawn y aplica inmediatamente cualquier cambio tras `/zombies recargar`.
- **Reglas por variante y rechazos ordenados (`SpawnRules.Reason`):**
  - `disabled`: variante o mod desactivado (`activo: false`).
  - `mimo`: `mimo_zombie` nunca aparece de forma natural (reservado a muertes de jugador en #7).
  - `world`: dimensión fuera de `mundosPermitidos`.
  - `day`: hora diurna en el reloj del Overworld (excepto `enano_zombie`, que habita en oscuridad subterránea total sin importar el reloj solar).
  - `height`: `Y` fuera del rango `alturaY` configurado.
  - `light`: luz de bloque mayor que `luzMax`.
  - `dwarf`: `enano_zombie` exige `Y < 40`, `blockLight == 0` y `skyLight == 0` incluso si se amplía `alturaY` o `luzMax` en el JSON.
  - `biome`: el bioma no coincide con `biomas` o coincide con `biomasExcluidos` (soporta IDs y tags `#namespace:tag`).
  - `snow`: `congelado_zombie` exige además `Biome#coldEnoughToSnow` en la posición y nivel del mar actuales.
  - `water_depth`: `abismo_zombie` exige una columna contigua de al menos `max(5, habilidades.agarre.profundidadMinima)` bloques de agua atravesando la posición candidata (`SpawnRules.columnDepth`).
  - `player_cap`: cualquier jugador no espectador en un radio de 64 bloques que ya tenga `>= maxZombiesEspecialesPorJugador` zombies especiales vivos en su radio de 64 bloques bloquea el spawn (`SpawnRules.belowCap`). La cuenta por jugador se cachea una vez por tick del servidor y se invalida al spawnear un nuevo `ConfiguredZombie`, respetándose tanto en noches normales como en pasadas extra de luna de sangre.

### 2. Calendario, persistencia y efectos de la luna de sangre (`BloodMoon`, `BloodMoonSchedule`, `BloodMoonState`, `BloodMoonOptions`)
- **Calendario puro (`BloodMoonSchedule`):**
  - Noche absoluta `N = floorDiv(time, 24000) + 1`; ventana nocturna `t = floorMod(time, 24000) ∈ [13000, 23000)`.
  - Cada noche se evalúa una sola vez en cuanto el reloj entra en su día (`night > lastNight`):
    - Si `cadaNNoches > 0`, es determinista (`night % cadaNNoches == 0`) y nunca consume el generador aleatorio.
    - Si `cadaNNoches == 0`, realiza una única tirada en `[0, 1)` contra `probabilidadPorNoche`.
  - Si la noche resulta elegida, queda marcada en `scheduledNight` desde el día (bloqueando siestas previas al anochecer o durante tormentas si `dormirBloqueado: true`) y se activa al llegar a `13000` ticks.
  - Retroceder el reloj (`/time set`) nunca vuelve a tirar dados para noches ya evaluadas (`night <= lastNight`).
- **Persistencia (`BloodMoonState`):**
  - Guardado en `SavedData` del Overworld bajo `dolfinzombies:bloodmoon` con los campos `active`, `lastNight`, `activeNight` y `scheduledNight`.
  - Al reiniciar a mitad de una luna de sangre (o durante el día previo a una noche ya programada), el estado se restaura íntegro sin reevaluar ni perder el evento.
- **Efectos activos:**
  - `multiplicadorSpawns`: `MonsterCapMixin` y `SpawnStateMixin` escalan el límite local y global de `MobCategory.MONSTER` (`ceil(70 * multiplicador)`), y `NaturalSpawnerMixin` ejecuta pasadas adicionales de `MONSTER` por chunk (`BloodMoonOptions.extraPasses`).
  - `soloEspeciales`: sustituye los zombies vanilla elegibles por zombies especiales conservando la masa de peso total.
  - `aviso`: envía título rojo (`dolfinzombies.bloodmoon.title`), subtítulo (`dolfinzombies.bloodmoon.subtitle`), paquete de sonido `WITHER_SPAWN` y mensaje de inicio (en chat si `mensajesChat: true`, o en action bar si `false`).
  - `cieloRojo`: sincroniza `BloodMoonPayload` a cada cliente (`BloodMoon.syncSky`), incluyendo jugadores que entran tarde, cambian de dimensión o recargan config; en el cliente, `BloodMoonSkyMixin` y `BloodMoonFogMixin` tiñen cielo y niebla de rojo oscuro.
  - `dormirBloqueado`: `EntitySleepEvents.ALLOW_SLEEPING` impide acostarse durante la noche activa o cuando esta noche ya está programada como luna de sangre, y despierta a quienes estuvieran durmiendo al iniciar.
  - `recompensaXpExtra`: `ConfiguredZombie.getBaseExperienceReward` multiplica la XP base por `recompensaXpExtra` mientras la luna de sangre está activa en esa dimensión.
  - `lunaDeSangre.pesoExtra` y `lunaDeSangre.bonusStats` por variante: `pesoExtra` multiplica el peso natural en `SpawnRules.effectiveWeight`; `bonusStats` multiplica `vida` y `danio` en el snapshot persistido del zombie al nacer en `ConfiguredZombie.finalizeSpawn`.
  - **Cierre al amanecer (`23000` ticks):** envía mensaje de cierre (`dolfinzombies.bloodmoon.end`) y prende fuego (`BloodMoonOptions.burnSeconds`) a los `ConfiguredZombie` vivos sobrantes que quemen con sol (`quemaConSol: true` y sin `PersistenceRequired`), respetando a los que no queman con sol (`bombero_zombie`, `congelado_zombie`, `explorador_zombie` o cualquier variante con `quemaConSol: false`).

### 3. Comandos (`/zombies` / `/zb`)
- `/zombies lunadesangre iniciar`: si es de día, avanza el reloj del Overworld al anochecer (`13000` ticks del día actual) y activa la luna de sangre.
- `/zombies lunadesangre detener`: detiene la luna de sangre en curso o cancela la programada para esta noche.
- `/zombies inspeccionar <id>`: evalúa en la posición del ejecutor todas las reglas de `SpawnRules` para `<id>` e informa razón de rechazo/elegibilidad, peso efectivo, grupo, bioma, luz, `SpawnPlacements` y cap por jugador.
- `/zombies diagnostico`: vuelca y muestra el estado del calendario de luna de sangre y los contadores acumulados de spawns/rechazos en `config/dolfinzombies/diagnostics/runtime.json`.

## Claves de configuración (`general.json → lunaDeSangre` y por zombie)
- `general.json`:
  - `maxZombiesEspecialesPorJugador` (int, def. `12`)
  - `mundosPermitidos` (array de IDs de dimensión, def. `["minecraft:overworld"]`)
  - `mensajesChat` (boolean, def. `true`)
  - `lunaDeSangre.activo` (boolean, def. `true`)
  - `lunaDeSangre.probabilidadPorNoche` (double `0..1`, def. `0.10`)
  - `lunaDeSangre.cadaNNoches` (int `>= 0`, def. `0`)
  - `lunaDeSangre.multiplicadorSpawns` (double `0..16`, def. `2.5`)
  - `lunaDeSangre.soloEspeciales` (boolean, def. `false`)
  - `lunaDeSangre.aviso` (boolean, def. `true`)
  - `lunaDeSangre.cieloRojo` (boolean, def. `true`)
  - `lunaDeSangre.dormirBloqueado` (boolean, def. `true`)
  - `lunaDeSangre.recompensaXpExtra` (double, def. `1.5`)
- `zombies/<id>.json`:
  - `peso`, `grupo`, `biomas`, `biomasExcluidos`, `alturaY`, `luzMax`, `quemaConSol`
  - `lunaDeSangre.pesoExtra` (double, def. `3.0`)
  - `lunaDeSangre.bonusStats` (double, def. `1.25`)

## Eventos de log estructurados
- `event=spawn_rules_registered` (INFO): registro inicial de `SpawnPlacements` y `BiomeModifications`.
- `event=spawn_rules_reloaded` / `event=spawn_counters` (INFO): recarga de generación con pesos efectivos y resumen de contadores previos.
- `event=spawn_rejected kind=<id> reason=<reason> stage=placement ...` (DEBUG): rechazo individual en `SpawnPlacements`.
- `event=blood_moon_state_loaded` / `event=blood_moon_state_saved` (INFO): carga y guardado de `BloodMoonState` en `SavedData`.
- `event=blood_moon_evaluate` (INFO): evaluación única de cada noche con `night`, `every`, `chance`, `roll`, `selected` y `trigger`.
- `event=blood_moon_start` / `event=blood_moon_end` (INFO): inicio y fin de luna de sangre con `trigger=command|random|every_n|dawn|disabled`, `night`, `burning` y `spared`.
- `event=blood_moon_command` / `event=blood_moon_force_night` (INFO): uso de `/zombies lunadesangre iniciar|detener`.
- `event=blood_moon_sleep_blocked` / `event=blood_moon_sky_sync` / `event=blood_moon_late_join` / `event=blood_moon_bonus` (DEBUG): detalle por jugador o entidad.

## Qué NO se verificó en cliente gráfico
- El tinte visual rojizo en cielo (`BloodMoonSkyMixin`) y niebla (`BloodMoonFogMixin`), la presentación en pantalla del título/sonido y la interacción de cama con un cliente conectado requieren verificación visual manual en el cliente.
