# #30 — Terreno, biomas y materiales de Kepler

## Contenido

`PlanetModule.init()` registra siete bloques y cinco materiales de `PlanetCatalog`, sin modificar `SpaceContent.REGOLITO_BLOCK`. Biomas data-driven: llanuras de regolito con titanio, cráteres con roca lunar/hierro meteórico, bosque con columnas de cristal de 2–5 bloques y hongos, pantano con superficie de lodo ácido, cavernas profundas con cristales y hongos en capas. Atmósferas (cielo/niebla/partículas) distintas en los cinco.

Multi-noise usa temperatura y vegetación de ruido vanilla; profundidad mediante gradiente vertical propio. Cuatro bandas térmicas de superficie y una banda profunda permiten localizar todos los biomas. Densidad combina relieve de la base con ruido 3D de cuevas; `material_rule` conserva bedrock y aplica paletas por biome/on_floor. No hay agua/lluvia. Los nombres de cráter/pantano describen paleta y recursos: no se implementan estructuras de meteoritos ni lagos líquidos.

`roca_lunar`, `bloque_cristal_energia` (luz 13), `lodo_acido` (Veneno II, 60 ticks al contacto, servidor, exento creativo/espectador), `hongo_bioluminiscente` (vegetación sin colisión, luz 10). Menas `mena_titanio` → `titanio_crudo` → `lingote_titanio`; `mena_hierro_meteorico` → `hierro_meteorico_crudo` → `lingote_hierro_meteorico`; `mena_cristal_energia` → `cristal_energia`. Fundición/alto horno de los metales y bloque de cristal fabricable. Pico de hierro para titanio/cristal, piedra para hierro meteórico; lodo con pala. Loot de menas soporta Silk Touch y Fortune ore_drops, usando el formato 26.3 `modifier`, no el antiguo `functions`.

Tags de integración: `#dolfinspace:cristales_energia` contiene `dolfinspace:cristal_energia`; `#dolfinspace:lingotes_titanio` contiene `dolfinspace:lingote_titanio`. `#dolfinspace:terreno_kepler` define reemplazables de menas. Registro en `.planet` y un único hook común. No se cambia configuración compartida.

## Validación

Build Java 25 / `--no-daemon` con 21 tests JUnit verdes. `PlanetResourcesTest`: cinco biomas/wiring/ambientes, features referenciadas existentes, ruido/profundidad/superficies, loot Fortune/Silk Touch 26.3, catálogo/assets/tags/herramientas y lógica de ácido.

Un único smoke headless arrancó y cerró limpiamente, sin ERROR. `/execute in dolfinspace:kepler ... locate biome` encontró:

- plains [0,60,0]
- craters [-896,60,-896]
- crystal_forest [-1376,60,-1248]
- acid_marshes [-1760,60,-1536]
- abyssal_caverns [0,-32,0]

Esto también confirma carga común de los mixins de oxígeno tras separar paquetes. La colocación de compresor se rechazó por chunk sin cargar, no cuenta como prueba de funcionamiento. No se lanzó cliente ni Chunky. Queda sin verificar visualmente relieve/paletas/densidad de recursos, contacto con ácido, Fortuna en juego, pregeneración Chunky e interoperabilidad del pack. No se confunde resolución de biomas con generación de chunks.

Eventos: `event=planet_registered` (INFO, manifiesto completo), `event=acid_poison` (DEBUG). Scripts `tools/space/planet_resources.py` y `resources.py` regeneran recursos; logs ignorados en `runtime/space/smoke.log`. También se migró el loot de regolito/compresor al formato de condición 26.3.

### Integración #31

Siete especies en cinco biomas. Chunks de cuatro regiones de superficie forzados a cargar/generar en smoke #31 sin errores; no equivale a Chunky/inspección visual. Paleta final de cavernas con suelo cristalino luminoso (retoque posterior al smoke, validado por recursos/build). Hongo también alimenta compresor. Tras regenerar biomas ejecutar `tools/space/mobs_resources.py` para conservar fauna.
