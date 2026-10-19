# Issue #7 — Zombies que se quedan con objetos del jugador: Ladrón, Contagiado y Mimo

## Resumen de la arquitectura

Toda transferencia de objetos de un jugador pasa por una máquina de estados explícita y persistida (`CustodyLedger` + `CustodyManager` en `SavedData` del Overworld bajo el id `dolfinzombies:custody`, más el paquete NBT `ItemAccounting.Packet<ItemStack>` en cada entidad).

### Máquina de estados de custodia (`CustodyLedger.Holder`)

- `PLAYER -> THIEF | INFECTED | MAILBOX`
- `THIEF -> GROUND | MAILBOX`
- `INFECTED -> CHEST | GROUND | MAILBOX`
- `MAILBOX -> RETURNED`

Cada transición verifica el estado origen esperado (`expected`). Si una entidad duplicada (por ejemplo, tras restaurar un chunk) intenta liberar la misma custodia por segunda vez, `CustodyLedger.move` devuelve `Outcome.REJECTED`, garantizando **cero pérdida y cero duplicación**.

### Fallback seguro ante muerte en vacío o lava (`MAILBOX`)

Si un `LadronZombie` o `ContagiadoZombie` con custodia activa muere en lava, fuego o vacío (`Y < minY + 1`), los objetos no se sueltan donde se destruirían: pasan a `Holder.MAILBOX` dentro de `CustodyManager` y se entregan automáticamente al dueño cuando está vivo o al reaparecer/reconectarse (`Holder.RETURNED`).

## Zombies implementados

### 1. Ladrón (`ladron_zombie` — `LadronZombie`)
- **Habilidad (`habilidades.robar`)**: `activo` (`true`), `probabilidadRobo` (`0.35`), `velocidadHuida` (`1.6`), `listaNegra` (`[]`), `soloHotbar` (`true`), `minutosParaSoltar` (`10`).
- Al golpear a un jugador en supervivencia/aventura, elige el objeto de la mano principal (o un slot elegible de la hotbar/inventario si la mano está vacía o en `listaNegra`).
- Copia primero el `ItemStack` al paquete de custodia (`DolfinStolenPacket`), registra `PLAYER -> THIEF` en `CustodyManager`, y solo entonces limpia el slot del jugador.
- Mientras lleva un objeto:
  - No hace despawn (`isPersistenceRequired() == true`, `removeWhenFarAway() == false`).
  - No arde al sol (`isSunSensitive() == false`).
  - No abre ni rompe puertas (`canBreakDoors() == false`).
  - Muestra el objeto robado en la mano principal (`dropChance = 0`) y huye del jugador con `AvoidEntityGoal` a velocidad `velocidadHuida`.
- Al morir o al cumplirse `minutosParaSoltar` (10 min = 12 000 ticks), libera el objeto como `ItemEntity` invulnerable y sin caducidad (`setUnlimitedLifetime()`, `setPermanentlyInvulnerable(true)`) y avisa al dueño en chat con las coordenadas (si `mensajesChat` es `true`).

### 2. Contagiado (`contagiado_zombie` — `ContagiadoZombie` + `InfectionEffect`)
- **Efecto `dolfinzombies:infeccion`**: efecto dañino con icono propio (`assets/dolfinzombies/textures/mob_effect/infeccion.png`).
  - Etapas según progreso (`ItemAccounting.infectionStage`):
    - `0–50 %`: sin síntomas adicionales.
    - `50–80 %`: Hambre I leve.
    - `80–100 %`: Náusea ocasional.
  - **Curas (`curas`)**: por defecto `minecraft:golden_apple`, `minecraft:enchanted_golden_apple` y `minecraft:milk_bucket` (interceptado en `ServerPlayerCustodyMixin` antes y después de `completeUsingItem`).
- **Conversión al morir infectado (`convertirAlMorir`)**:
  - Aunque el golpe mortal no provenga de un zombie, `ServerPlayerCustodyMixin` intercepta `ServerPlayer.die` antes de `dropAllDeathLoot`.
  - Con `keepInventory == false`: captura todos los slots salvo los que tienen maldición de desaparición (`EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP`), abre custodia `PLAYER -> INFECTED` y vacía esos slots para que vanilla destruya solo los malditos sin duplicar nada.
  - Con `keepInventory == true`: el zombie convertido aparece con nombre y copia visual de la armadura, pero no captura objetos (`items=count:0,stacks:0`).
  - El zombie convertido lleva el nombre del jugador, su perfil de skin sincronizado, no arde al sol ni hace despawn.
- **Cofre al morir el convertido (`cofre`)**:
  - `activo` (`true`), `minutosProteccion` (`15`), `radioBusqueda` (`5`).
  - Busca el bloque válido más cercano en radio 5 que no esté en lava, agua, aire sin soporte, vacío ni dentro de una claim ajena de Open Parties and Claims (`ExternalIntegrations.blockedByForeignClaim`).
  - Si hay más de 27 stacks, busca dos bloques adyacentes válidos y coloca un cofre doble con nombre `"Cofre de <jugador>"`.
  - Durante `minutosProteccion` (15 min), solo el dueño y su party de OPAC pueden abrirlo o romperlo (`UseBlockCallback`, `PlayerBlockBreakEvents.BEFORE`), y está protegido contra tolvas (`BaseContainerProtectionMixin`) y explosiones (`ServerExplosionProtectionMixin`).
  - Registra la colocación e inserción de objetos en Ledger (`ExternalIntegrations.logChestToLedger`) y notifica al dueño con coordenadas.
  - Si `cofre.activo` es `false`, `mobGriefing` es `false` o no hay lugar válido en `radioBusqueda`, suelta los objetos al suelo sin desaparición (o al buzón si murió en lava/vacío).

### 3. Mimo (`mimo_zombie` — `MimoZombie`)
- **Habilidad (`habilidades.aparicionPorMuerte`)**: `activo` (`true`), `probabilidadAparecer` (`0.30`), `minutosVentana` (`10`), `copiarEncantamientos` (`false`).
- Peso natural `0` (nunca aparece por generación natural).
- Cuando muere un jugador —salvo que haya muerto infectado o a manos de un `ContagiadoZombie`—, se agenda una ventana de aparición (`PendingMimo`) en `CustodyManager` con probabilidad `0.30` durante `10` minutos.
- Cuando hay un jugador a menos de 48 bloques del lugar de la muerte dentro de la ventana, aparece el `MimoZombie` con:
  - Vida máxima igual a `vidaMaxJugador * 1.5` (con bonus de luna de sangre si aplica).
  - Nombre visible y perfil de skin del jugador muerto (`PlayerSkinZombieRenderer` en el cliente).
  - Copia de las 4 piezas de armadura sin encantamientos (`copiarEncantamientos: false`) e imposible de obtener (`dropChance = 0`, `dropEquipment` y `dropCustomDeathLoot` anulados).
  - Animaciones de imitación de jugador cuando no está en combate (agacharse periódicamente y mover el brazo).

### 4. Comando `/zombies dondeesta`
- Disponible para todos los jugadores (`dolfinzombies.dondeesta`, público por defecto).
- Muestra la dimensión, coordenadas actualizadas y cantidad de objetos de cualquier `LadronZombie` o `ContagiadoZombie` activo cuyo dueño sea el jugador que ejecuta el comando o un miembro de su party de OPAC.

## Eventos de log (`SLF4J`)

- `event=item_custody id=<uuid> from=<Holder> to=<Holder> owner=<uuid> ownerName=<name> kind=<id> items=count:<N>,stacks:<M> detail=<...> entity=<uuid> pos=<dim@x,y,z> reason=<...>`
- `event=custody_loaded active=<N> mailbox=<M> chests=<C> pendingMimos=<P>`
- `event=ladron_custody_restored`, `event=contagiado_custody_restored`
- `event=infection_applied`, `event=infection_cured`, `event=contagiado_converted`
- `event=ledger_chest_placed`, `event=contagiado_chest_no_site`, `event=contagiado_chest_denied`
- `event=mimo_scheduled`, `event=mimo_spawned`, `event=mimo_window_expired`
- `event=command_dondeesta`

## Limitaciones y verificación pendiente en juego

- La suite JUnit (`CustodyFlowTest`, `InventoryZombiesConfigTest`) verifica la contabilidad exacta sin pérdida ni duplicación, serialización `Codec`, etapas de infección, curas, búsqueda de sitio de cofre, permisos de cofre con expiración y parámetros del Mimo.
- La integración con Open Parties and Claims y Ledger usa reflexión opcional en tiempo de ejecución; requiere verificación con ambos mods instalados en servidor real.
- El renderizado de skin del jugador (`PlayerSkinZombieRenderer`) compila contra el cliente 26.3 y debe verificarse visualmente en cliente.
