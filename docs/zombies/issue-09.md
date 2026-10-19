# #9 — Integración final, presupuesto y prueba automatizada

## Integración de #5–#8

- Mimo y Contagiado convertido usan `ConfiguredZombie.setPlayerAppearance(ResolvableProfile)` y `PlayerLikeZombieRenderer`. El setter **no cambia equipo**: conserva las reglas de encantamientos, drop y custodia de #7. Contagiado copia el perfil resuelto antes de insertar la entidad; Mimo conserva las propiedades de skin en la ventana pendiente, incluso tras reiniciar. Datos antiguos sin ese campo migran a perfil por UUID. El cliente usa `PlayerSkinRenderCache` para resolver también perfiles pendientes; mantiene modelos normal/slim, armadura, tinte/venas del Contagiado y cara del Mimo. Se elimina `PlayerSkinZombieRenderer`, que no estaba registrado.
- `AbismoZombie.tryGrab` reproduce `ABISMO_GRAB` sólo al aceptar un agarre. `LadronZombie.trySteal` reproduce `LADRON_STEAL` después de abrir custodia y retirar el objeto. `BiliosoZombie.die` reproduce `BILIOSO_BURST` en la rama idempotente que crea su nube; se elimina el hook genérico de muerte para evitar duplicar el sonido custom. Gritón conserva `GRITON_SCREAM` en su llamada.
- Startup: `mechanics=all_16_kinds_active`.

## Auditoría de los 16 tipos

Todo acceso al mundo sigue en el hilo de servidor. Parámetros son snapshots por entidad; los interruptores se consultan en vivo. No se parsean archivos de config durante ticks.

| Tipo | Trabajo de servidor / límite |
|---|---|
| Minero | Scan cada 20 ticks, hasta 3 bloques candidatos; progreso de una rotura O(1), guard/rate-limit al terminar. |
| Ladrón | Robo en golpe aceptado; custodia cada 20 ticks; deadline O(1). JSON sólo al robar/restaurar/soltar, no por tick. |
| Bombero | Contador de agua O(1); sólo al cambiar bloque intenta ignición (5 posiciones, hasta 6 vecinos cada una). |
| Ahogado | Intento de agarre cada 5 ticks; columna de agua limitada a 32, cooldown/reintento; arrastre activo O(1). |
| Escudero | Cono del escudo y desactivación sólo en daño; sin búsqueda periódica propia. |
| Hambriento | Búsqueda de comida cada 20 ticks, fase por ID, radio máximo 32; hambre en golpe. |
| Gritón | Scan cada 20 ticks con fase por ID; radio máximo 64, hasta 64 dirigidos y 15 intentos de refuerzo (defaults 30/15/2). |
| Bruja | Scan/curación cada 20 ticks con fase; radio máximo 32, cooldown 80 por defecto. Navegación/retiro cada 20 ticks, sin repetir pathfinding en el mismo lanzamiento. |
| Granjero | Cada 20 ticks, fase por ID; 24 columnas × 5 alturas como máximo, radio máximo 32; target goal vanilla separado. |
| Congelado | Lentitud en golpe; hielo sólo al morir, radio máximo 3 y guards de #4. |
| Jinete | Identidad del vehículo O(1) por tick; elección/spawn de montura sólo al aparecer. |
| Bilioso | Nube sólo al morir; coordinador limitado a 64 nubes, partículas cada 4 ticks, efectos cada 20, radio máximo 16. |
| Enano | Tamaño/atributos en spawn/load; cálculo de dimensiones O(1), sin búsqueda periódica. |
| Contagiado | Infección en golpe, conversión en muerte; tracking de custodia cada 20 ticks. |
| Mimo | Animaciones O(1), intervalos 30/45 ticks, slots de drop constantes; ventanas pendientes en coordinador cada 20 ticks. |
| Explorador | Luz y hasta 12 candidatos de sombra cada 20 ticks con fase; reloj de revelado por daño O(1) cada tick. |

`ZombieTickPolicy` centraliza cadencias de scans y navegación. Los soportes escalonan el primer scan y conservan `nextScan` para garantizar separación de al menos 20 ticks. Se evita que 25 apariciones simultáneas concentren todos esos scans en el mismo frame. `ZombieStats` es el mapper puro que **usa** `ConfiguredZombie.applyStats`: vida, daño, velocidad ×0.23, armadura y escala. Los atributos adicionales de #5 (resistencia al empuje/tamaño) siguen en sus clases. Vida del Mimo configurado desde un jugador sigue siendo 1.5× la vida máxima del jugador; no se sustituye por la vida JSON.

Los límites de operaciones no demuestran un tiempo de CPU: densidad de entidades, navegación vanilla, claims, mods y hardware afectan el resultado. No se afirma un MSPT medido sin ejecutar la carga.

## Instrumentación opt-in

Iniciar el servidor con `-Ddolfinzombies.tickProbe=true`. Por defecto el probe queda desactivado, con guard constante en los dispatches. `ServerLevelTickProbeMixin` envuelve `tickNonPassenger` y `tickPassenger`; mide el tick completo del subtipo, incluyendo trabajo posterior a `super.tick()`. No reemplaza ni cancela la lógica vanilla.

Cada ventana **no solapada de 100 ticks** guarda atómicamente `config/dolfinzombies/diagnostics/tick-budget.json`: media, P95, tiempo global entre eventos START/END, media por tipo, mínimo de entidades medidas y mínimo con AI activa cerca (64 bloques) de un jugador vivo de supervivencia/aventura. NoAI, creativos, espectadores y ausencia de jugador no satisfacen la carga. Una ventana incompleta nunca pasa; con 25 entidades y 25 cerca durante toda la ventana, la media inclusiva debe ser ≤2 ms. Pasajeros especiales anidados pueden contarse inclusivamente dos veces; el resultado es conservador.

Logs INFO: `event=zombie_tick_probe_enabled`, `event=zombie_tick_budget`; fallos de escritura: `event=zombie_tick_probe_write_failed`. Perfiles: `event=zombie_appearance_set`, `mimo_profile_invalid` y eventos save/load existentes. El probe incluye coste vanilla del tick de cada zombie; **no es una resta de MSPT con/sin mod ni una medición de spark**, y no incluye el coste global de spawn/custodia/nubes en su subtotal por entidad.

## Harness de servidor

Ruta pedida: `scripts/pruebas/prueba_zombies.py`. Sigue consola + lector en hilo + barreras por comando + resultado JSON + stop/finally de `tests/manual/prueba_mod_servidor.py` y `tests/manual/prueba_luna_sangre.py`. Los nombres históricos `scripts/pruebas/prueba_mod_servidor.py` y `test-fabric/` no existen; se usa un fixture explícito bajo `runtime/`, nunca el servidor real.

Precondiciones: provisionar `runtime/zombies-budget/` con Fabric 26.3, Java 25, Fabric API y spark; aceptar su EULA personalmente; añadir `.zombies-budget-instance`; configurar `pause-when-empty-seconds=0`, puerto aislado (p.ej. 25570), misma seed/mundo, distancias y resto de mods para ambas pasadas. Sólo un jugador real de supervivencia conectado durante performance. No se abre cliente ni se crean jugadores falsos. El script no instala mods, no acepta EULA y no borra mundos.

```sh
export JAVA_HOME=/Users/emi0x7d1/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home
# Primera pasada: omitir exclusivamente dolfinzombies del fixture, conservar spark y demás mods.
python3 scripts/pruebas/prueba_zombies.py --mode baseline --player NOMBRE
# Parar; instalar el JAR recién construido en el mismo fixture (sin cambiar server.properties).
python3 scripts/pruebas/prueba_zombies.py --mode verify --player NOMBRE \
  --baseline-report runtime/zombies-budget/zombies-09-baseline-result.json
# Sólo stats: no necesita jugador conectado ni baseline/spark.
python3 scripts/pruebas/prueba_zombies.py --stats-only
```

El fixture modifica bloques en x/z 0..31, y 79..88; fija noche, tiempo despejado, dificultad normal y spawning natural desactivado. Teleporta al jugador indicado y le da resistencia. No restaura mundo/gamerules/posición/efectos; es una instancia de pruebas dedicada. Guarda/restaura `general.json` y desactiva temporalmente la luna de sangre para comparar stats sin bonus. No borra configuraciones de otros tipos.

En verify congela ticks, invoca uno de cada tipo mediante `/zombies invocar`, identifica/taggea sus fixtures, aplica NoAI sólo durante inspección y compara `Health` más los cinco atributos base mediante `/data get entity` contra `diagnostics/last-reload.json`. Aleja al jugador durante stats para que Mimo no tome el override 1.5×. Limpia únicamente entidades taggeadas por el harness y sus monturas; luego crea 25 zombies **con AI**, repartidos entre los 16 tipos cerca del jugador.

Tras 15 s de calentamiento mide ≥30 s (default 60), toma `/tick query` cada 5 s, conserva `/spark tps`, y requiere al menos 3 ventanas nuevas válidas del probe. Baseline exige ausencia del mod; verify exige startup integrado. Rechaza diferencias de propiedades (hash canónico sin comentarios/timestamps) y del resto de IDs de mods. Compara medianas de MSPT vanilla con delta ≤2 ms, además del subtotal del probe. Conserva logs y resultados aun al fallar y para el servidor en finally. No procesa stats de un comando fallido como si fueran cero.

Para cerrar la aceptación exacta del ticket, comparar también los valores/medias de spark y sus perfiles sin/con mod, con la misma carga/mundo y plugins Lithium/C2ME/VMP. Adjuntar los informes y anotar el baseline en #9. El output raw de spark se guarda, pero el harness **no lo presenta como un delta de spark parseado**. La resolución de `/tick query` es 0.1 ms; resultados limítrofes requieren más muestras/perfilado.

## Evidencia de esta sesión

- `./gradlew build --no-daemon` desde `mods/zombies`, JDK25: **92 tests, 0 fallos, 0 errores, 0 omitidos**, compilación principal y cliente correctas.
- `ZombieTickBudgetTest`: mapper real para defaults/custom de los 16 tipos; límites y reparto de scans de 25 entidades durante 200 ticks; frontera 2 ms, ventana incompleta, ausencia de jugador/entidad y separación media/cola. Son tests de lógica y contabilidad, no un benchmark del mundo Minecraft.
- `CustodyFlowTest`: round-trip del perfil en la ventana pendiente y compatibilidad con datos antiguos.
- `python3 -m unittest discover -s tests -p test_zombies_budget_harness.py -v`: 3 tests verdes (parsers SNBT/MSPT, 16 tipos/5 atributos, rechazo de evidencia incompleta o exceso de presupuesto).
- No existe `runtime/` provisionado en este worktree. No se ejecutó servidor ni cliente. Quedan sin medir baseline/spark, delta con jugador real, aplicación runtime de los mixins y aceptación visual/auditiva. **El criterio ≤2 ms extra de #9 todavía no está certificado.**
