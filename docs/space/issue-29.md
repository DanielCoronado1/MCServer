# #29 — Oxígeno

Trinkets/Accessories no son objetivos de integración para Minecraft 26.3 en este mod. Se implementa Plan B: mixin sobre `InventoryMenu` (nombre Mojang de `PlayerScreenHandler`), con ranura adicional junto a la armadura e icono de tanque vacío. No se añade dependencia externa. Datos persistentes propios del jugador (`dolfinspace_tank`, codec de ItemStack), sincronizados por la ranura vanilla; HUD mediante payload S2C cada segundo.

## Implementación

- Tanques chico/mediano/grande: 300/600/1200 s. Durabilidad significa oxígeno restante; al agotarse no se rompe. Recetas producen tanques vacíos; creativo proporciona tanques llenos. El compresor adapta capacidad al config conservando segundos restantes.
- Filtro en Slot y Container, rutas de shift-clic explícitas, máximo uno. No es un contenedor de mundo accesible por tolvas. Creative usa un puente validado de paquetes y posición propia en la pestaña de inventario para evitar solaparse con la hotbar.
- Persistencia al guardar/cargar jugador; muerte suelta el tanque salvo keepInventory; COPY_FROM copia lo que permanezca después de la muerte. No se cambian índices vanilla.
- Un segundo por cada 20 ticks del servidor, solamente en Kepler, fuera de nave y en supervivencia/aventura. Tras agotar el último segundo, la asfixia empieza en el siguiente pulso. Exentos creativo/espectador. Pausas/lag siguen el tiempo de juego, no reloj de pared.
- `oxygen` es daño propio que ignora armadura y tiene mensaje de muerte español. HUD de diez burbujas con alarma y parpadeo al llegar al umbral.
- Compresor: GUI vanilla de cinco ranuras, validada en ambos lados: planta, carbón/carbón vegetal, tres tanques. Un vegetal y un combustible por ciclo; llena el primer tanque no completo. Entrada superior de plantas, lateral de combustible/tanques, extracción inferior solamente de tanques llenos. Inventario y progreso persistentes, caída de contenido al romper.

## Configuración y contratos

`oxigeno.segundosTanqueChico/Mediano/Grande`, `porcentajeAlertaOxigeno`, `danioSinOxigenoPorSegundo` de la base; también se acepta `danioAsfixiaPorSegundo` (precedencia sobre el nombre anterior). `maquinas.ticksCompresorOxigeno=100`, `segundosRecargaPorCicloOxigeno=60`. Las banderas históricas de consumo fuera de Kepler/en nave no habilitan consumo: se respeta la exención obligatoria D11/D12.

Naves se reconocen por namespace `dolfinspace` y prefijo `nave`, `spaceship` o `ship_` del vehículo directo; este contrato debe coincidir con #27 al integrar. Recetas usan tags locales con placas/circuitos opcionales de #26 y fallback de hierro/redstone para que sean utilizables sin la rama hermana. Medio usa cobre y grande diamante como decisión de balance inicial. Hojas y saplings vanilla y `#dolfinspace:plantas_compresor` alimentan el compresor.

## Observabilidad y verificación

`event=oxygen_registered`, `tank_loaded`, `tank_saved`, `tank_slot_rejected`, `tank_death_drop`, `tank_respawn_copy`, `oxygen_suffocation`, `compressor_loaded/saved/state/refill`. Transiciones frecuentes en DEBUG.

Build Java 25 + 16 tests JUnit verdes. Pruebas puras cubren filtros de IDs, consumo, exenciones, umbral, saturación de recarga y alias config. Único smoke headless de #29 detectó un paquete de mixins demasiado amplio: se corrigió separando `.oxygen.mixin` de las clases normales y `.client.oxygen.mixin`. No se repitió para respetar el máximo de un smoke por ticket; #30 verificará el arranque integrado. No se inició cliente. Quedan para aceptación manual: clic/arrastre/shift-clic real con los mods del pack, creativo, muerte/reconexión/reinicio, automatización, HUD/sonido y apariencia. Los assets son placeholders deterministas generados por `tools/space/resources.py`, permitidos hasta #32.

CI (`.github/workflows/release.yml`) y `scripts/build_release.py` descubren todos los proyectos `mods/*`.
