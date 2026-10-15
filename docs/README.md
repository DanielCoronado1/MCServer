# DolfinServer

Servidor **Fabric 26.3** con mods de optimización y administración, más nuestro propio mod de creepers. Los jugadores entran con el modpack (`.mrpack`) que incluye Fabric y los mods necesarios.

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Plataforma | Fabric + mods propios | Permite texturas y entidades nuevas de verdad (el jugador instala el modpack) |
| Versión | 26.3 (Java 25) | La más reciente en el canal estable |
| Memoria | 10 GB (`server-fabric/iniciar.bat`) | El límite es el procesador, no la RAM; dar más no mejora |
| Jugadores | máx. 25, lista blanca activa | Capacidad realista del i7-1185G7 |

## Carpetas

- `server-fabric/` – el servidor. En el repositorio solo se guardan `iniciar.bat`, `eula.txt`, `ops.json`, `whitelist.json`, `server.properties.ejemplo` y `config/`.
- `mods-src/` – código de nuestros mods (Gradle). Cada mod es una subcarpeta.
- `scripts/` – instalación del servidor, creación del modpack y pruebas.

## Primer arranque

1. `python scripts/instalar_fabric.py` descarga el lanzador de Fabric y los mods del servidor desde Modrinth (verificando SHA-512).
2. Copiar `server-fabric/server.properties.ejemplo` a `server-fabric/server.properties`.
3. Abrir `server-fabric/iniciar.bat`. Cuando el servidor se apaga vuelve a arrancar solo a los 10 segundos; para apagarlo del todo, pulsar Ctrl+C durante la cuenta atrás.
4. En la consola: `whitelist add <tu_nombre>` y `op <tu_nombre>`.

## Mods del servidor

Los instala `scripts/instalar_fabric.py` (siempre la última versión para 26.3). Para actualizarlos, borrar `server-fabric/mods/` (salvo nuestros mods) y volver a ejecutarlo.

- **Optimización:** Lithium, C2ME, FerriteCore, ScalableLux, VMP.
- **Administración:** LuckPerms, Chunky, spark, Ledger (registro de bloques y deshacer destrozos), Open Parties and Claims, Essential Commands.

## Mod propio: creepers (Dolfin Creepers)

Código en `mods-src/creepers/`. Añade variantes de creeper: fuego, hielo, eléctrico, saltarín, mini, multiplicador, tóxico, gigante y rey.

- Las texturas se diseñan con `python scripts/texturas_creepers.py` (también genera `mods-src/creepers/vista_previa_texturas.png`).
- Tras compilarlo, `scripts/actualizar-mod-creepers.bat` copia la última versión al servidor y a tu `.minecraft\mods` (con Minecraft y el servidor cerrados).

## Modpack para jugadores

`python scripts/crear_modpack.py [version]` crea `modpack/DolfinServer-<version>.mrpack`. Se importa con un clic en Prism Launcher o Modrinth App e incluye Fabric, Fabric API, mods opcionales de rendimiento (Sodium, Lithium, FerriteCore) y nuestros mods.

## Pruebas

- `python scripts/pruebas/arranque_fabric.py ["comando" ...]` arranca el servidor real, ejecuta comandos de comprobación y lo apaga (salida en `server-fabric/prueba_arranque.log`).
- `python scripts/pruebas/prueba_mod_servidor.py` prueba cada variante sin ventanas en `test-fabric/` (puerto 25570, mundo nuevo cada vez).
- `python scripts/pruebas/prueba_saltarin.py` mide cuánto lanza hacia arriba el Creeper Saltarín.
- `python scripts/pruebas/prueba_visual_mod.py` lanza `test-fabric/` y un Minecraft de pruebas (`test-client/`, jugador `Tester`) y guarda capturas de todas las variantes en `test-client/capturas/`. Necesita que nadie esté usando el equipo.
- `python scripts/pruebas/hoja.py [carpeta]` junta las capturas de una prueba en una sola imagen.
