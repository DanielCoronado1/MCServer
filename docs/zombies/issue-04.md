# Issue #4 — Zombies que modifican bloques (Minero, Bombero, Granjero y hielo del Congelado)

## Qué se implementó

### 1. Guardia común y registro en Ledger (`com.dolfin.zombies.griefing`)

- **`BlockGuard` / `GuardDecision`**:
  - `BlockGuard.puedeModificar(level, pos, zombie)` (y sobrecarga con `action`) evalúa en orden:
    1. `mundosPermitidos` (`general.json`).
    2. Gamerule `mobGriefing` (`GameRules.MOB_GRIEFING`) para modificaciones de bloques (`checkAttack` / `puedeAtacar` omite `mobGriefing` para ataques a animales pero mantiene `mundosPermitidos` y `respetarClaims`).
    3. `respetarClaims` (`general.json`) mediante integración opcional con **Open Parties and Claims** (`OpacClaims`).
  - `BlockGuard.tryBreak`, `tryPlace` y `tryChange` vuelven a verificar `puedeModificar`, aplican la mutación en el hilo del servidor, emiten callbacks de **Ledger** (`LedgerLog`) con `zombie.kind().id()` como actor (`@minero_zombie`, `@bombero_zombie`, `@granjero_zombie`, `@congelado_zombie`) y registran `event=grief_performed` en DEBUG.
- **Integraciones suaves (`OpacClaims`, `LedgerLog`)**:
  - `openpartiesandclaims` y `ledger` están en `suggests` dentro de `fabric.mod.json` y se invocan por reflexión.
  - Sin OPAC instalado, las comprobaciones de claims tratan las posiciones como no reclamadas sin fallar. Si OPAC está cargado pero falla por reflexión, la guardia **falla cerrado** (`Reason.CLAIM_ERROR`) para nunca romper construcciones protegidas.
  - En Ledger (`1.3.25` para MC 26.3), se invocan `BlockBreakCallback.EVENT`, `BlockPlaceCallback.EVENT` y `BlockChangeCallback.EVENT` para permitir `/ledger inspect` y `/ledger rollback source:@<zombie_id>`.
- **`RateLimiter`**:
  - Ventana deslizante por entidad sobre `level.getGameTime()` (1200 ticks = 1 minuto) para hacer cumplir `maxBloquesPorMinuto`.

### 2. Zombies (`com.dolfin.zombies.kinds` y `CongeladoIce`)

- **`MineroZombie` (`minero_zombie`) + `MiningMath`**:
  - Escanea cada 20 ticks (`(tickCount + getId()) % 20 == 0`) cuando tiene como objetivo un jugador vivo en supervivencia/aventura dentro de `distanciaMaxRompiendo` y no tiene camino (`!path.canReach()`) o lleva 2 escaneos atascado.
  - Inspecciona como máximo 3 posiciones adyacentes hacia el jugador (`MiningMath.candidates`).
  - Nunca rompe bloques con dureza `< 0`, bloques en la lista inmutable `NEVER` (obsidiana, obsidiana llorosa, nexo de reaparición, pizarra profunda reforzada, bedrock, marcos/portales de End y Nether, spawners, barreras), bloques con `dureza > durezaMax`, bloques/tags en `listaNegra`, ni bloques con `BlockEntity` que implemente `Container`.
  - Avanza grietas (`level.destroyBlockProgress`, etapas `0..9`), sonido de golpe y animación de brazo con pico de hierro cada 5 ticks; al completar revalida el límite por minuto y `BlockGuard.tryBreak`.
- **`BomberoZombie` (`bombero_zombie`)**:
  - Siempre en llamas visualmente fuera del agua (`isOnFire`), inmune al fuego (`fireImmune` + filtro `DamageTypeTags.IS_FIRE`) y al sol (`quemaConSol: false`).
  - Al cambiar de bloque con `quemarBloques.activo`, con probabilidad `probabilidadPorPaso` prende fuego en aire adyacente a cualquier bloque inflamable (`ignitedByLava()`), respetando `fire_spread_radius_around_player != 0` (reemplazo vanilla 26.3 de `doFireTick`) y `BlockGuard.tryPlace`.
  - Al golpear a un jugador lo prende por `quemarJugador` segundos.
  - Muere tras `segundosEnAgua` segundos en agua o bajo tormenta eléctrica (`isThundering() && isRainingAt(above)`).
  - Su tabla de botín (`data/dolfinzombies/loot_table/entities/bombero_zombie.json`) añade `0..2` de `minecraft:charcoal` (con bono por saqueo).
- **`GranjeroZombie` (`granjero_zombie`)**:
  - Equipa azada de hierro (`Items.IRON_HOE`, sin drop fuera de la loot table) y escanea cada 20 ticks muestreando hasta 24 columnas aleatorias dentro de `radioBusqueda` para acercarse a tierras de cultivo (`FarmlandBlock`).
  - Con `destruirCultivos.activo` (`false` por defecto), rompe el cultivo superior (`tryBreak` con drop) y convierte `farmland` en `minecraft:dirt` (`tryChange`), respetando `maxBloquesPorMinuto` y `BlockGuard`.
  - Con `atacarAnimales.activo` (`false` por defecto), selecciona animales listados en `animales` mediante `NearestAttackableTargetGoal` pasando por `BlockGuard.puedeAtacar` (respeta `mundosPermitidos` y claims de OPAC; suelta el objetivo en vivo si se desactiva la habilidad).
- **`CongeladoIce` (`congelado_zombie`, bloque al morir)**:
  - Registrado en `ServerLivingEntityEvents.AFTER_DEATH` para no tocar la clase de combate de #5.
  - Con `congelar.activo`, coloca `bloqueAlMorir` (por defecto `minecraft:ice`) en un disco horizontal de radio `radioHielo` (acotado a `0..3`) sobre fuentes de agua o aire con bloque inferior no vacío, pasando por `BlockGuard.tryPlace`.

## Claves de configuración

En `config/dolfinzombies/general.json`:
- `respetarClaims` (`true`): activa la consulta a OPAC.
- `mundosPermitidos` (`["minecraft:overworld"]`): dimensiones donde los zombies pueden modificar bloques o atacar animales.

En `config/dolfinzombies/zombies/<id>.json` (`habilidades`):
- `minero_zombie.json` -> `habilidades.romperBloques`:
  - `activo` (`true`), `durezaMax` (`3.0`), `tiempoPorBloque` (`40`), `distanciaMaxRompiendo` (`16`), `soltarBloques` (`false`), `maxBloquesPorMinuto` (`20`), `listaNegra` (cofres, camas, barriles, shulkers, hornos, mesas, portales).
- `bombero_zombie.json` -> `habilidades.quemarBloques`:
  - `activo` (`true`), `probabilidadPorPaso` (`0.08`), `quemarJugador` (`4`), `segundosEnAgua` (`3`).
- `granjero_zombie.json`:
  - `habilidades.destruirCultivos`: `activo` (`false`), `radioBusqueda` (`24`), `maxBloquesPorMinuto` (`30`).
  - `habilidades.atacarAnimales`: `activo` (`false`), `animales` (`["minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"]`).
- `congelado_zombie.json` -> `habilidades.congelar`:
  - `activo` (`true`), `bloqueAlMorir` (`"minecraft:ice"`), `radioHielo` (`1`).

## Eventos de log para diagnóstico

- Integraciones al iniciar (INFO):
  - `event=integration name=opac state=present|absent|error`
  - `event=integration name=ledger state=present|absent|error`
  - `event=grief_guard_reloaded generation=<n> worlds=[...] respetarClaims=<bool> providers=...`
- Guardia de bloques / animales (DEBUG):
  - `event=grief_rejected id=<kind> uuid=<uuid> action=<mine|ignite|trample|trample_crop|death_ice|attack_animal|modify> dimension=<dim> pos=<x, y, z> block=<id> reason=<world|gamerule|claim|claim_error|fire_tick|hardness|unbreakable|blacklist|rate_limit|disabled>`
  - `event=grief_performed id=<kind> uuid=<uuid> action=<...> dimension=<dim> pos=<x, y, z> from=<block> to=<block> ledger=<true|false>`
- Mecánicas por zombie (DEBUG):
  - Minero: `event=mine_start`, `event=mine_cancel`
  - Bombero: `event=bombero_extinguished`, `event=bombero_ignite_player`
  - Granjero: `event=granjero_farm_found`, `event=granjero_drop_target`
  - Congelado: `event=death_ice`

## Límites conocidos y qué falta verificar en juego

- En Minecraft 26.3 la gamerule booleana `doFireTick` fue reemplazada por `fire_spread_radius_around_player` (`GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER`); `BlockGuard.fireTickEnabled` considera desactivada la propagación cuando su valor es `0`.
- La linterna emisiva y el casco del Minero pertenecen al modelo/textura del cliente en el issue #8 (sin mod de luces dinámicas en `pack/mods/`).
- **Verificación pendiente en juego**:
  - Comprobación con OPAC y Ledger cargados simultáneamente en una instancia con jugadores reclamando chunks (`ClaimStatus.CLAIMED`) y ejecutando `/ledger rollback source:@minero_zombie`.
  - Prueba visual de grietas de rotura (`destroyBlockProgress`) del Minero y propagación de fuego del Bombero en cliente.
