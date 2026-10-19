# Issue #5 — Combat Zombies (`ESCUDERO`, `HAMBRIENTO`, `CONGELADO`, `BILIOSO`, `ABISMO`, `JINETE`, `ENANO`)

## Overview

Implements the seven combat-focused zombie variants in package `com.dolfin.zombies.kinds` and wires them in the `#5 combat zombies` section of `ZombieFactories`:

1. **`EscuderoZombie` (`escudero_zombie`, ability `bloquear`)**
   - Spawns with a shield in `OFFHAND` (`dropChance = 0`; custom drop rolled on player kill via `probabilidadSoltarEscudo`).
   - Intercepts incoming `hurtServer` hits when `ShieldMath.decide(...)` returns `BLOCK`: ability enabled, damage does not carry `BYPASSES_SHIELD` / `BYPASSES_INVULNERABILITY`, horizontal source position is within `anguloBloqueo` degrees of the zombie's facing direction (`getYRot()`), projectile blocking is enabled if the direct entity is a `Projectile`, and the shield is not on axe cooldown.
   - Plays vanilla shield-block entity event (`29`) and spawns `CRIT` particles in front of the zombie.
   - Non-projectile melee hits from a `LivingEntity` holding an item in `#minecraft:axes` disable the shield for `hachaDesactivaEscudo` seconds and broadcast entity event `30` (shield break sound).

2. **`HambrientoZombie` (`hambriento_zombie`, ability `hambre`)**
   - On melee hit against a `Player`, drains `hambrePorGolpe` food levels, `saturacionPorGolpe` saturation, and applies `MobEffects.HUNGER` for `efectoHambre` seconds.
   - `FoodDistractionGoal` (priority 1, throttled scan every 15–24 ticks within `radioComida` horizontal radius) diverts the zombie toward the nearest `ItemEntity` with `DataComponents.FOOD` dropped by a `Player` (`getOwner() instanceof Player`). Upon reaching within 1.5 blocks, consumes 1 item from the stack, heals `2.0` HP, and plays `GENERIC_EAT`. Gives up after 100 ticks if unreachable.

3. **`CongeladoZombie` (`congelado_zombie`, ability `congelar`)**
   - Immune to powder-snow freezing (`canFreeze() == false`) and does not burn in sunlight (`quemaConSol = false` by default).
   - On melee hit against a `LivingEntity`, applies `MobEffects.SLOWNESS` (`lentitudSegundos`, amplifier `nivelLentitud - 1`) and, when `congelacionPolvo` is enabled and `target.canFreeze()` is true (respecting vanilla leather armor freeze immunity), sets `ticksFrozen` to at least `congelacionTicks`.
   - On death, if `mobGriefing` is enabled, places `bloqueAlMorir` within `radioHielo` on replaceable non-fluid blocks above sturdy top surfaces.

4. **`BiliosoZombie` (`bilioso_zombie`, ability `nube`)**
   - Applies `resistenciaEmpuje` to `Attributes.KNOCKBACK_RESISTANCE` via `applyStats()`.
   - On death, registers a virtual poison/nausea cloud in `BiliousClouds` (bounded to `MAX_CLOUDS = 64`, cleared on server stop) for `duracionNube` ticks with radius `radioNube`.
   - Every 4 ticks emits `ENTITY_EFFECT` particles (`0xFF7BA428`); every 20 ticks applies `MobEffects.NAUSEA` (`nauseaSegundos`) and `MobEffects.POISON` (`venenoSegundos`, amplifier `venenoNivel - 1`) to living non-spectator entities inside the cylinder (`CloudMath.contains`), explicitly skipping all `Zombie` instances.

5. **`AbismoZombie` (`abismo_zombie`, ability `agarre`)**
   - Sets water pathfinding malus to `0.0` and suppresses upward liquid swimming (`jumpInLiquid`) while holding a victim.
   - Initiates a grab on melee hit or throttled proximity scan (every 5 ticks) against a valid survival/adventure `ServerPlayer` within `alcanceAgarre` when both are in water, water column depth (`WaterColumn.depth`) is at least `profundidadMinima`, and `TickCooldown` is ready.
   - Each held tick pulls the victim downward (`-fuerzaArrastre`) and horizontally toward the zombie (`0.5 * fuerzaArrastre` when horizontal distance > `0.6`), clamped to `[-0.6, 0.6]` per axis via `DragMath`, and syncs velocity to the client via `ClientboundSetEntityMotionPacket`. Applies `MobEffects.MINING_FATIGUE` (`nivelFatiga`) while held.
   - Releases reliably through `GrabRules` and starts `enfriamiento` ticks cooldown on: duration expiry (`duration`), hit by grabbed player (`hit_by_target`), external damage `>= danioParaSoltar` (`damage`), zombie death/removal (`zombie_dead` / `zombie_removed_*`), player disconnect/dimension change/removal (`target_gone`), player death (`target_dead`), creative/spectator switch (`target_invalid`), ability disabled live (`ability_off`), zombie or player leaving water (`zombie_out_of_water` / `target_left_water`), or distance exceeding `3.5` blocks (`too_far`).

6. **`JineteZombie` (`jinete_zombie`, ability `montura`)**
   - During `finalizeSpawn`, removes any vanilla baby-jockey vehicle if present, selects a mount ID from `monturas` via `WeightedPick.pick`, creates the `Mob` with `EntitySpawnReason.JOCKEY`, sets its `MAX_HEALTH` and health to `vidaMontura`, starts riding it, and adds the mount to the `ServerLevel`.
   - Vanilla mob passenger control drives the mount (`Spider` climbs walls with the rider; `ZombieHorse` chases). When the mount dies or is removed, `JineteZombie` logs `event=jinete_dismounted` and continues fighting on foot.

7. **`EnanoZombie` (`enano_zombie`, ability `cuerpoPequeno`)**
   - Default `escala = 0.5` and hitbox `anchoHitbox = 0.5`, `altoHitbox = 0.9`.
   - Overrides `getDefaultDimensions(Pose)` with `EntityDimensions.scalable(ancho / escala, alto / escala)` so that after Minecraft multiplies by the `SCALE` attribute (`0.5`), the resulting bounding box is `0.5 × 0.9` blocks (passes under 1-block-high gaps). Calls `refreshDimensions()` in `applyStats()` and `readAdditionalSaveData()`.

## Configuration Keys (`habilidades`)

| Kind | Ability key | Parameters & defaults |
|---|---|---|
| `escudero_zombie` | `bloquear` | `activo` (`true`), `anguloBloqueo` (`90`), `hachaDesactivaEscudo` (`5` s), `bloqueaProyectiles` (`true`), `probabilidadSoltarEscudo` (`0.08`) |
| `hambriento_zombie` | `hambre` | `activo` (`true`), `hambrePorGolpe` (`3`), `saturacionPorGolpe` (`2`), `efectoHambre` (`5` s), `distraidoPorComida` (`true`), `radioComida` (`10`) |
| `congelado_zombie` | `congelar` | `activo` (`true`), `lentitudSegundos` (`4`), `nivelLentitud` (`1`), `congelacionPolvo` (`true`), `congelacionTicks` (`200`), `bloqueAlMorir` (`"minecraft:ice"`), `radioHielo` (`1`) |
| `bilioso_zombie` | `nube` | `activo` (`true`), `radioNube` (`3`), `duracionNube` (`100` ticks), `resistenciaEmpuje` (`0.8`), `nauseaSegundos` (`6`), `venenoSegundos` (`2`), `venenoNivel` (`1`) |
| `abismo_zombie` | `agarre` | `activo` (`true`), `fuerzaArrastre` (`0.12`), `duracionAgarre` (`60` ticks), `enfriamiento` (`200` ticks), `profundidadMinima` (`5`), `danioParaSoltar` (`4`), `alcanceAgarre` (`1.8`), `nivelFatiga` (`1`) |
| `jinete_zombie` | `montura` | `activo` (`true`), `vidaMontura` (`20`), `monturas` (`{"zombie_horse": 60, "spider": 40}`) |
| `enano_zombie` | `cuerpoPequeno` | `activo` (`true`), `anchoHitbox` (`0.5`), `altoHitbox` (`0.9`) (plus top-level `escala`: `0.5`) |

## Structured Log Events

Grep for `event=` in `logs/latest.log` (enable `DEBUG` on logger `dolfinzombies` for per-hit/per-entity events):

- **Escudero**: `event=escudero_block` (`decision=BLOCK|ABILITY_OFF|BYPASS|NO_SOURCE_POSITION|PROJECTILE_IGNORED|SHIELD_DISABLED|OUTSIDE_CONE`), `event=escudero_shield_disabled`, `event=escudero_shield_drop`
- **Hambriento**: `event=hambriento_hit`, `event=hambriento_food_target`, `event=hambriento_food_eaten`, `event=hambriento_food_giveup`
- **Congelado**: `event=congelado_hit`, `event=congelado_ice`, `event=congelado_ice_skipped`, `event=congelado_ice_invalid` (WARN)
- **Bilioso**: `event=bilioso_death`, `event=bilioso_cloud_spawn`, `event=bilioso_cloud_apply`, `event=bilioso_cloud_end`, `event=bilioso_cloud_cap` (WARN)
- **Abismo**: `event=abismo_grab_start`, `event=abismo_grab_rejected` (`reason=target_invalid|target_creative_or_spectator|zombie_not_in_water|target_not_in_water|too_shallow`), `event=abismo_grab_tick`, `event=abismo_grab_release` (`reason=duration|hit_by_target|damage|zombie_dead|zombie_removed_*|target_gone|target_dead|target_invalid|ability_off|zombie_out_of_water|target_left_water|too_far`)
- **Jinete**: `event=jinete_mount`, `event=jinete_mount_skipped`, `event=jinete_vanilla_mount_removed`, `event=jinete_mount_invalid` (WARN), `event=jinete_dismounted`

## Known Limits & Not Verified In-Game

- **Shared block-protection guard (#4)**: `CongeladoZombie.placeIce` currently checks `GameRules.MOB_GRIEFING` directly and carries a `TODO(#4)` comment to route through the shared OpenPAC/Ledger block-guard helper once issue #4 merges.
- **In-game client/visual verification**: Verified via unit tests (`CombatKindsTest`, `ZombieConfigTest`, `BloodMoonTest`) and full Gradle build (`./gradlew build --no-daemon`). Client-side particle appearance, sound playback, spider wall-climbing pathing under player chase, and Enano 1-block gap navigation were not visually tested in a live client session.
