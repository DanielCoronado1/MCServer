# Desarrollo

[← Volver a DolfinServer](../README.md)

Cada mod propio es un subproyecto Gradle en `mods/<nombre>/` integrado en el proyecto multiproyecto raíz, compartiendo el wrapper de Gradle y las fuentes habituales `src/main` y `src/client`. Mantén binarios y cachés en `build/` y `.gradle/`, excluidos de Git. Usa JDK 25 mediante `JAVA_HOME`; no guardes rutas de un equipo concreto en las propiedades de Gradle.

## Creepers

`mods/creepers/` añade creepers de fuego, hielo, eléctricos, saltarines, mini, multiplicadores, tóxicos, gigantes y el Rey Creeper. Compila con `./gradlew build` (o `./gradlew :mods:creepers:build`) desde la raíz. En Windows, `scripts/update_creepers.bat` compila e instala el mod; detén el servidor primero. Despliega mediante el instalador para sincronizar las copias del servidor y de AutoModpack. Actualiza `version` en `mods/creepers/gradle.properties` al publicar cambios.

Genera las texturas con `python3 tools/generate_creeper_textures.py`. Las texturas y su vista previa viven en el proyecto del mod. Instala `requirements-dev.txt` para usar las herramientas de recursos y comprobación visual.

## Mods de terceros

Gestiona los metadatos de Packwiz desde `pack/`:

```sh
packwiz modrinth add <proyecto>
packwiz update <proyecto>
packwiz remove <proyecto>
packwiz refresh
```

Configura `side` en cada `mods/*.pw.toml`: `server` instala solo en el servidor; `client`, solo en el pack alojado por AutoModpack; `both`, en ambos. AutoModpack se instala por separado en el servidor y mediante el `.mrpack` del jugador, y se excluye de su propio pack alojado. CI comprueba la coherencia del índice.

## Validación

Ejecuta las pruebas aisladas de herramientas:

```sh
python3 -m unittest discover -s tests -p 'test_*.py'
```

Formato y lint: `./gradlew formatJava` aplica google-java-format (AOSP) y `./gradlew check` lo verifica junto con `-Xlint:all`; para Python, `uvx ruff format .` y `uvx ruff check .` según `ruff.toml`.

CI también compila todos los mods propios y genera los artefactos de las versiones. Los scripts de juego están en `tests/manual/` y se apoyan en el arnés unificado `servidor.py`:

| Script | Qué comprueba |
| :--- | :--- |
| `arranque_fabric.py ["comando" ...]` | Arranca el **servidor real** de `runtime/server/`, ejecuta comandos y lo detiene. Úsalo solo cuando esté detenido. |
| `prueba_mod_servidor.py` | Variantes en `runtime/test-server/`, puerto 25570. |
| `prueba_saltarin.py` | Lanzamientos del creeper saltarín en esa instancia de prueba. |
| `prueba_zombies.py` | Smoke test de regresión, configuración, 16 tipos de zombis y recargas en `runtime/zombies-smoke/`. |
| `prueba_zombies_presupuesto.py` | Invoca las 16 variantes de zombis, verifica sus atributos y mide el presupuesto de MSPT. |
| `prueba_luna_sangre.py` | Regresión de luna de sangre, recargas dinámicas, biomas y persistencia en `runtime/zombies-smoke/`. |
| `prueba_space_26_28.py` | Prueba sin ventanas de mods/space: máquinas, combustible, naves y dimensiones. |
| `prueba_visual_mod.py` | Capturas de variantes con `runtime/test-server/` y `runtime/test-client/`. Requiere escritorio Windows y uso exclusivo del equipo. |
| `hoja.py [directorio]` | Una hoja de contacto con las capturas. |

Estos scripts requieren instancias Fabric de prueba ya preparadas; no forman parte de las pruebas unitarias automáticas. La automatización visual sigue siendo específica de Windows. Configura `JAVA_HOME` para el JDK de prueba. Los scripts pueden restablecer el mundo de prueba. Compilar o generar un archivo de versión no demuestra el comportamiento dentro del juego.

## Zombis especiales (`mods/zombies/` — Épica #1)

`mods/zombies/` (`dolfinzombies`) registra 16 tipos de entidad `Zombie` independientes, sus huevos de aparición, texturas 32x con capas emisivas y accesorios (#8), aparición natural y evento de luna de sangre (#3), modificadores de bloques con `BlockGuard`/OPAC/Ledger (#4), zombis de combate (#5), apoyo y sigilo (#6), custodia persistente de inventario (#7) y presupuesto de rendimiento (#9). Compila con `JAVA_HOME=<JDK25> ./gradlew :mods:zombies:build --no-daemon` (o `./gradlew build` para todos). Documentación detallada por issue en `docs/zombies/issue-03.md` a `docs/zombies/issue-09.md`.

### Configuración y comandos

El primer arranque crea `config/dolfinzombies/general.json` y `zombies/<id>.json`. Cada campo ausente recibe su valor predeterminado; los campos inválidos usan su valor de respaldo de forma independiente y conservan los campos válidos. Un JSON malformado usa los valores predeterminados de ese archivo sin sobrescribir el original.

`/zombies` y `/zb` ofrecen `invocar <id> [cantidad 1..64] [jugador]`, `recargar`, `activar|desactivar <id>`, `info <id>`, `lunadesangre iniciar|detener`, `dondeesta`, `inspeccionar <id>` y `diagnostico`. LuckPerms es opcional (`dolfinzombies.admin.recargar`, `.admin.invocar`, `.admin.config`, `.admin.luna`, `.info`, `.dondeesta`).

## Exploración espacial (`mods/space/` — Épica #24)

`mods/space/` (`dolfinspace`) implementa la dimensión `dolfinspace:kepler` con gravedad al 40 % y la Tierra en el cielo (#25), la cadena industrial de acero, compresor, fabricador de circuitos y refinería (#26), ensamblaje de 3 naves, plataforma y cargador (#27), panel de cabina y viaje interdimensional (#28), sistema de oxígeno y compresor (#29), 5 biomas y menas espaciales (#30), fauna hostil del planeta (#31), modelos 3D, sonidos y texturas 32x (#32) e integración con OPAC, PvP, Ledger, LuckPerms y plan de pruebas en `docs/espacio/PRUEBAS.md` (#33). Compila con `JAVA_HOME=<JDK25> ./gradlew :mods:space:build --no-daemon` (o `./gradlew build` para todos).

