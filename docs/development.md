# Desarrollo

[← Volver a DolfinServer](../README.md)

Cada mod propio es un proyecto Gradle independiente en `mods/<nombre>/`, con su wrapper y las fuentes habituales `src/main` y `src/client`. Mantén binarios y cachés en `build/` y `.gradle/`, excluidos de Git. Usa JDK 25 mediante `JAVA_HOME`; no guardes rutas de un equipo concreto en las propiedades de Gradle.

## Creepers

`mods/creepers/` añade creepers de fuego, hielo, eléctricos, saltarines, mini, multiplicadores, tóxicos, gigantes y el Rey Creeper. Compila con `./gradlew build` desde ese directorio. En Windows, `scripts/update_creepers.bat` compila e instala el mod; detén el servidor primero. Despliega mediante el instalador para sincronizar las copias del servidor y de AutoModpack. Actualiza `version` en `gradle.properties` al publicar cambios.

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

CI también compila todos los mods propios y genera los artefactos de las versiones. Los scripts de juego están en `tests/manual/`:

| Script | Qué comprueba |
| :--- | :--- |
| `arranque_fabric.py ["comando" ...]` | Arranca el **servidor real** de `runtime/server/`, ejecuta comandos y lo detiene. Úsalo solo cuando esté detenido. |
| `prueba_mod_servidor.py` | Variantes en `runtime/test-server/`, puerto 25570. |
| `prueba_saltarin.py` | Lanzamientos del creeper saltarín en esa instancia de prueba. |
| `prueba_visual_mod.py` | Capturas de variantes con `runtime/test-server/` y `runtime/test-client/`. Requiere escritorio Windows y uso exclusivo del equipo. |
| `hoja.py [directorio]` | Una hoja de contacto con las capturas. |

Estos scripts requieren instancias Fabric de prueba ya preparadas; no forman parte de las pruebas unitarias automáticas. La automatización visual sigue siendo específica de Windows. Configura `JAVA_HOME` para el JDK de prueba. Los scripts pueden restablecer el mundo de prueba. Compilar o generar un archivo de versión no demuestra el comportamiento dentro del juego.

## Base de zombis (#1 / #2)

`mods/zombies/` sigue la estructura de `mods/creepers/`; la ruta `mods-src/` de los issues corresponde a la estructura anterior. Compila con `JAVA_HOME=<JDK25> mods/zombies/gradlew -p mods/zombies build`. CI compila todos los proyectos `mods/*`; el empaquetador incluye ambos JAR propios y el instalador los sincroniza con el servidor y los clientes AutoModpack sin una lista adicional.

La base registra dieciséis tipos de entidad `Zombie` independientes, sus huevos de aparición y tablas de botín de datapack en `dolfinzombies:entities/<id>`. Cada tabla delega actualmente en el botín del zombi de Minecraft. Los nombres de entidades y huevos incluyen `en_us`, `es_mx`, `es_es` y `es_ar`. El renderizado usa el modelo y la textura del zombi de Minecraft hasta el trabajo de #8. Las mecánicas individuales (#4–#7), la aparición del Mimo según la salud del jugador (#7) y la luna de sangre/aparición natural (#3) amplían esta base. El trabajo de #3 está en desarrollo en esta rama; no se considera validado por las pruebas de la base. La épica sigue abierta.

### Configuración y comandos

El primer arranque crea `config/dolfinzombies/general.json` y `zombies/<id>.json`. Cada campo ausente recibe su valor predeterminado; los campos inválidos usan su valor de respaldo de forma independiente y conservan los campos válidos. Un JSON malformado usa los valores predeterminados de ese archivo sin sobrescribir el original. La configuración solo se analiza durante el arranque, recarga o activación/desactivación, en el hilo del servidor. La base no introduce modificaciones asíncronas del mundo ni mecánicas que cambien bloques.

`/zombies` y `/zb` ofrecen `invocar <id> [cantidad 1..64] [jugador]`, `recargar`, `activar|desactivar <id>` e `info <id>`. La invocación usa la posición del origen del comando, salvo que se indique un jugador. El autocompletado enumera los identificadores. La recarga informa de los 17 archivos y todos los errores de archivos/campos, y explica que las entidades existentes conservan sus estadísticas. Los cambios de activación guardan `activo` de forma atómica.

La desactivación global o por tipo rechaza invocaciones por comando y descarta entidades desactivadas que se carguen mediante huevos, invocaciones de Minecraft o carga del mundo. No destruye retroactivamente entidades ya cargadas: controla las cargas y apariciones posteriores. Las habilidades tienen interruptores booleanos independientes para futuras mecánicas; las dos habilidades destructivas del Granjero están desactivadas de forma predeterminada. En la base #1/#2, `lunadesangre iniciar|detener` y `dondeesta` informan de sus issues pendientes; el trabajo posterior de #3 amplía la luna de sangre.

LuckPerms es opcional. Si está instalado, se comprueban mediante su API los nodos `dolfinzombies.admin.recargar`, `.admin.invocar`, `.admin.config`, `.admin.luna`, `.info` y `.dondeesta`. Una denegación explícita prevalece; los nodos de administración sin configurar recurren al nivel de operador 2 de Minecraft, mientras que información/localización se permiten a todos de forma predeterminada. La consola usa los permisos de Minecraft. Sin LuckPerms no hay dependencia de su API durante la ejecución.

Cada entidad nueva captura sus ajustes y los guarda en NBT junto con la generación de recarga. Minecraft carga los atributos y la salud guardados, por lo que las recargas y reinicios no curan ni actualizan silenciosamente a entidades existentes. Los interruptores de habilidades consultan la configuración actual.

### Diagnósticos y pruebas de la base

- El arranque registra cantidades de elementos registrados, ruta absoluta de configuración, generación de recarga, proveedor de permisos y funciones pendientes.
- Cada campo inválido registra `event=config_invalid`, archivo, campo, motivo y generación. `config/dolfinzombies/diagnostics/last-reload.json` guarda la configuración efectiva, motivos de respaldo, fecha y duración sin modificar los archivos de entrada.
- Las recargas, activaciones e invocaciones administrativas incluyen actor, tipo, cantidad/resultados, dimensión, posición y generación. Los eventos por entidad de aparición y carga desactivada usan DEBUG para evitar saturar producción; habilita DEBUG de `dolfinzombies` al investigar. El NBT contiene `DolfinZombieSettings` y `DolfinConfigGeneration`, además de los atributos y salud de Minecraft.
- Los informes JUnit están en `mods/zombies/build/reports/tests/test/index.html` y `mods/zombies/build/test-results/test/`. Cubren valores iniciales, respaldo por campo, JSON malformado, aislamiento entre recargas, lecturas inmutables, interruptores globales/por tipo/por habilidad, persistencia, validación de arreglos e identificadores y resolución de permisos.

`tests/manual/prueba_zombies.py --java <JDK25>/bin/java` controla un servidor Fabric aislado con ambos mods propios en una sesión. Comprueba cada entidad y tabla de botín, conservación de salud al recargar, configuración inválida o malformada, interruptores y diagnósticos de comandos.

Prepara `runtime/zombies-smoke/` con el lanzador Fabric 26.3/0.19.5 llamado `fabric-server-launch.jar`, Fabric API y ambos JAR compilados en `mods/`. Acepta antes la EULA de Minecraft en esa instancia. Usa un puerto dedicado en `server.properties`, `online-mode=false`, `view-distance=2`, `simulation-distance=2` y `pause-when-empty-seconds=0`. Mantén este directorio separado de producción.

Se generan `console-smoke.log`, `smoke-result.json`, `logs/latest.log` y el JSON de diagnóstico de recarga. El script guarda el mundo y detiene el proceso también ante un fallo; los artefactos permiten investigar una aserción fallida sin repetir sesiones manuales de juego.

Estas comprobaciones sin interfaz validan el registro del servidor y el comportamiento de comandos/configuración. No validan el renderizado del cliente, la integración de permisos con usuarios reales conectados, la transferencia de AutoModpack ni las futuras mecánicas de zombis.
