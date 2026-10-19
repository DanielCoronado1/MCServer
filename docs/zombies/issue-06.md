# #6 — Apoyo y sigilo

Implementados `GritonZombie`, `BrujaZombie` y `ExploradorZombie`; sólo se reemplaza su sección de `ZombieFactories`.

- Gritón: busca jugador visible cada 20 ticks, llama zombies vivos dentro de una esfera (30 incluido; 35 excluido), límite 15, vainilla opcional. Enfriamiento 400 ticks y huida 60 ticks con goal prioritario. No hay refuerzos vanilla al recibir daño; sólo intenta hasta 2 refuerzos durante luna de sangre, comprobando suelo y colisión.
- Bruja: escaneo cada 20 ticks, cura 6 dentro de 8 bloques sin incluirse, partículas verdes. Aplica directamente lentitud durante 6 segundos (alternativa permitida al proyectil), enfriamiento compartido 80 ticks. Goal prioritario mantiene distancia preferida 8.
- Explorador: invisibilidad nocturna; daño revela 3 segundos, luz efectiva de bloque/cielo >=12 revela, no consulta visión nocturna del jugador. No arde; rechaza objetivos diurnos y busca sombra en 12 posiciones como máximo cada 20 ticks. El render de ojos corresponde a #8. Luz se evalúa cada 20 ticks (hasta 19 ticks de latencia); #8 ajusta la expiración de daño a exactamente 60 ticks.

Parámetros: todos los del ticket se leen de `settings().habilidades` y los interruptores `llamada`, `curacion`, `invisibilidad` usan `abilityEnabled`. Bruja agrega `efectoLanzado: "minecraft:slowness"` y `duracionEfecto: 6` (segundos). Radios limitados a 64/32 y presupuestos a 64 llamados/15 refuerzos para acotar trabajo. Los parámetros conservan el snapshot por entidad; interruptores son en vivo. Cooldowns y ventana de daño persisten en NBT.

Observabilidad DEBUG: `event=support_rejected reason=...`, `griton_call`, `griton_flee_end`, `bruja_potion`, `explorador_reveal`, `explorador_day_state`, y `griton|bruja|explorador_saved|loaded`. Config inválida usa la validación y `event=config_invalid` existentes.

Validación: `./gradlew build --no-daemon` con JDK25 pasa los 33 tests, incluyendo 4 pruebas puras de radio esférico, exclusión de autocuración, throttling y revelado. No se lanzó cliente ni servidor. Pendientes de aceptación en juego: navegación real, sombras, sonido/partículas y presupuesto MSPT con 25 entidades. No se afirma validación visual ni rendimiento medido.
