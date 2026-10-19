# #26 — Cadena industrial

Se registran acero, bloque reversible, cuatro placas, dos circuitos y bidones reutilizables. El alto horno tiene una entrada: primero se craftea `mezcla_acero` con hierro + carbón y después se funde. Máquinas y bidones se fabrican con recetas desbloqueadas en el libro.

`AbstractSpaceMachineBlockEntity` comparte combustible, inventario, GUI, persistencia y `WorldlyContainer`. Entradas 0–2 arriba, carbón/carbón vegetal en 3 al costado, producto 4 y cubeta 5 abajo. Refinería consume lava, blaze y bidón vacío y devuelve combustible + cubeta. No procesa si cualquiera de las salidas está bloqueada. Un carbón da 1600 ticks efectivos; no se quema estando bloqueada. Bidones son objetos simples: no líquidos ni consumibles.

Tiempos vivos: `maquinas.ticksCompresor` (200), `ticksFabricadorCircuitos` (200), `ticksRefineria` (400). La recarga afecta incluso ciclos en curso. Las etiquetas de titanio/cristal incluyen hierro/amatista como fallback y entradas opcionales de #30; `replace:false` conserva extensiones de datapacks.

Eventos DEBUG: `machine_recipe`, `machine_fuel`, `machine_complete`, `machine_save`, `machine_load`. INFO: `machines_registered`.

Validación: build Java 25 y 15 tests JUnit pasan, incluidos 5 de máquinas (recetas, caras de tolva, consumo condicionado, cubeta, recarga). Sin cliente ni servidor lanzados. Modelos vanilla provisionales generados con `tools/space/generate_assets.py`.

Desviación: recetas internas de procesamiento se consultan al pasar sobre la barra de progreso (guía integrada); no se registran como tipos del libro vanilla. La apariencia, interacción de tolvas y persistencia real de chunks requieren comprobación en juego.
