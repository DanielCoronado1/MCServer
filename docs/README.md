# DolfinServer

Servidor **Fabric 26.3** con mods de optimización y administración, más nuestro propio mod de creepers. Los jugadores solo instalan Fabric y AutoModpack (el `.mrpack` de cada release); AutoModpack les descarga el resto de mods desde el servidor y los mantiene al día.

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Plataforma | Fabric + mods propios | Permite texturas y entidades nuevas de verdad (el jugador instala el modpack) |
| Versión | 26.3 (Java 25) | La más reciente en el canal estable |
| Memoria | 10 GB (`server-fabric/iniciar.bat`) | El límite es el procesador, no la RAM; dar más no mejora |
| Jugadores | máx. 25, lista blanca activa | Capacidad realista del i7-1185G7 |
| Lista de mods | packwiz (`pack/`) | Versiones exactas y huellas guardadas en el repositorio; actualizar es un cambio que se puede revisar |
| Mods de los jugadores | AutoModpack | Los jugadores se sincronizan solos con el servidor; no hay que repartir el modpack en cada cambio |

## Carpetas

- `server-fabric/` – el servidor. En el repositorio solo se guardan `iniciar.bat`, `eula.txt`, `ops.json`, `whitelist.json`, `server.properties.ejemplo`, `config/` y `automodpack/server.conf`.
- `pack/` – lista de mods de terceros (packwiz): versión exacta, enlace y huella de cada uno, y si va al servidor, a los jugadores o a ambos.
- `mods-src/` – código de nuestros mods (Gradle). Cada mod es una subcarpeta.
- `scripts/` – instalación del servidor, creación de releases y pruebas.
- `.github/workflows/release.yml` – compila y publica las releases.

## Primer arranque

1. `python scripts/instalar_servidor.py` (Python 3.11+) descarga el lanzador de Fabric y los mods de `pack/` desde Modrinth, verificando la huella de cada uno, e instala nuestros mods (compílalos antes con `gradlew build`).
2. Copiar `server-fabric/server.properties.ejemplo` a `server-fabric/server.properties`.
3. Abrir `server-fabric/iniciar.bat`. Cuando el servidor se apaga vuelve a arrancar solo a los 10 segundos; para apagarlo del todo, pulsar Ctrl+C durante la cuenta atrás.
4. En la consola: `whitelist add <tu_nombre>` y `op <tu_nombre>`.
5. Anotar la huella del certificado que AutoModpack escribe en la consola (`Certificate fingerprint: ...`) y pasársela a los jugadores: la verán al conectarse por primera vez.

## Mods (packwiz)

La lista está en `pack/` y se maneja con [packwiz](https://packwiz.infra.link) (`go install github.com/packwiz/packwiz@latest`), desde la carpeta `pack/`:

- Añadir: `packwiz modrinth add <proyecto>` y después fijar `side` en `mods/<proyecto>.pw.toml`.
- Actualizar: `packwiz update <proyecto>` o `packwiz update --all`.
- Quitar: `packwiz remove <proyecto>`.
- Si editas un `.pw.toml` a mano, ejecuta `packwiz refresh` (la GitHub Action falla si `index.toml` no está al día).

El campo `side` decide adónde va cada mod al ejecutar `scripts/instalar_servidor.py`:

| `side` | Destino | Ejemplos |
|---|---|---|
| `server` | `server-fabric/mods/` | C2ME, ScalableLux, VMP, LuckPerms, Chunky, spark, Ledger, Open Parties and Claims, Essential Commands |
| `client` | `server-fabric/automodpack/host-modpack/main/mods/` (AutoModpack lo envía a los jugadores) | Sodium |
| `both` | las dos carpetas | Fabric API, Lithium, FerriteCore |

AutoModpack es la excepción: va en `mods/` del servidor y cada jugador lo instala con el `.mrpack`; nunca viaja dentro del pack. `server-fabric/automodpack/server.conf` hace que el pack de los jugadores salga solo de `host-modpack/main/`, así que lo que está en `mods/` del servidor no se les envía.

`instalar_servidor.py` recuerda lo que instaló (`server-fabric/.dolfin-instalados.json`) y borra los mods que ya no están en el pack. Los jars que pongas a mano no los toca.

## Mod propio: creepers (Dolfin Creepers)

Código en `mods-src/creepers/`. Añade variantes de creeper: fuego, hielo, eléctrico, saltarín, mini, multiplicador, tóxico, gigante y rey.

- Las texturas se diseñan con `python scripts/texturas_creepers.py` (también genera `mods-src/creepers/vista_previa_texturas.png`).
- `scripts/actualizar-mod-creepers.bat` lo compila y lo instala en el servidor (con el servidor apagado). Los jugadores reciben la versión nueva de AutoModpack al conectarse.
- Sube `version` en `mods-src/creepers/gradle.properties` en cada cambio: si el nombre del jar no cambia, el instalador no puede distinguir las versiones.

## Releases

`.github/workflows/release.yml` compila nuestros mods y ejecuta `scripts/crear_release.py`, que deja en `dist/`:

- `DolfinServer-servidor-<versión>.zip` – para el administrador: configuración del servidor, `pack/`, nuestros mods e `instalar.py`. No trae `eula.txt`, `ops.json` ni `whitelist.json`, así que se puede descomprimir encima de un servidor existente.
- `DolfinServer-<versión>.mrpack` – para los jugadores: Minecraft, Fabric y AutoModpack. Se importa en Prism Launcher o Modrinth App.
- `notas.md` – instrucciones y lista de mods, que se usa como texto de la release.

Ningún archivo incluye jars de terceros (el repositorio es público): solo sus enlaces de Modrinth y huellas.

En pull requests y en `main`, los archivos quedan como artefactos de la ejecución de la Action. Para publicar una release, crear una etiqueta `v<versión>` sobre `main` y subirla: `jj tag set v1.2.0 -r main && jj git push --tag v1.2.0` (o crear la release con esa etiqueta desde GitHub).

Para probarlo en local: compilar los mods y ejecutar `python scripts/crear_release.py 0.0.0-prueba`.

## Pruebas

- `python scripts/pruebas/arranque_fabric.py ["comando" ...]` arranca el servidor real, ejecuta comandos de comprobación y lo apaga (salida en `server-fabric/prueba_arranque.log`).
- `python scripts/pruebas/prueba_mod_servidor.py` prueba cada variante sin ventanas en `test-fabric/` (puerto 25570, mundo nuevo cada vez).
- `python scripts/pruebas/prueba_saltarin.py` mide cuánto lanza hacia arriba el Creeper Saltarín.
- `python scripts/pruebas/prueba_visual_mod.py` lanza `test-fabric/` y un Minecraft de pruebas (`test-client/`, jugador `Tester`) y guarda capturas de todas las variantes en `test-client/capturas/`. Necesita que nadie esté usando el equipo.
- `python scripts/pruebas/hoja.py [carpeta]` junta las capturas de una prueba en una sola imagen.
