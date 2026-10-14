# DolfinServer

Servidor **Paper 26.3** (build 178) con plugins propios. Los jugadores entran con Minecraft Java 26.3 normal, sin instalar mods.

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Plataforma | Paper + plugins propios | El jugador no descarga nada; es la API más fácil de aprender |
| Versión | 26.3 (Java 25) | La más reciente en el canal estable |
| Memoria | 10 GB (`server/start.bat`) | El límite es el procesador, no la RAM; dar más no mejora |
| Jugadores | máx. 25, lista blanca activa | Capacidad realista del i7-1185G7 |
| Visuales propios | Display entities, partículas y resource pack automático | Funcionan sin mods en el cliente |

## Carpetas

- `server/` – el servidor (`start.bat` lo arranca).
- `plugins-src/` – código de los plugins (Gradle). Cada plugin es una subcarpeta.
- `resourcepack/` – texturas, modelos y sonidos propios.
- `scripts/` – `deploy-plugins.bat` compila y copia los plugins al servidor.
- `backups/` – copias del mundo.

## Ciclo de trabajo

1. Editar el código en `plugins-src/<plugin>/src/main/java/...`
2. Ejecutar `scripts/deploy-plugins.bat`
3. Reiniciar el servidor (escribir `stop` en la consola y volver a abrir `start.bat`)

## Primer arranque

1. Abrir `server/start.bat`. El servidor crea `eula.txt` y se detiene.
2. Leer el EULA de Minecraft (https://aka.ms/MinecraftEULA), y si estás de acuerdo, cambiar `eula=false` por `eula=true` en `server/eula.txt`.
3. Volver a abrir `start.bat`.
4. En la consola: `whitelist add <tu_nombre>` y `op <tu_nombre>`.
5. En Minecraft: Multijugador → Añadir servidor → `localhost`.

## Plugins

- **creepers** (DolfinCreepers): cada explosión de creeper suelta 3 fuegos artificiales de colores al azar, que no hacen daño.
- **sky-animations** (DolfinCielo): `/apocalipsis iniciar|detener` (solo operadores). Inspirado en el video de referencia: una mancha oscura que crece hasta cubrir el cielo con un remolino rojo, un rayo rojo que se abre en una grieta con bordes de neón y un Ender Dragon que asoma la cabeza. Dura alrededor de 1 minuto y se ajusta en `server/plugins/DolfinCielo/config.yml`.
  - Las imágenes son sprites del resource pack (`ItemDisplay` con `item_model` `dolfin:*`). El dragón es un Ender Dragon real sin IA, invulnerable, que no rompe bloques ni golpea.
  - El color real del cielo no se puede cambiar sin mods: el remolino es un disco gigante sobre la zona, rodeado por una pared de nubes.
- **resource-pack** (DolfinPack): sirve `server/plugins/DolfinPack/pack.zip` por HTTP (puerto 8081) y lo envía a cada jugador al entrar. `/dolfinpack recargar` lo reenvía sin reiniciar. Para jugadores por internet hay que abrir el puerto 8081 y poner la IP pública en `host` de su `config.yml`.

- **creepers** (DolfinCreepers): además de los fuegos artificiales, añade variantes que aparecen de forma natural (las probabilidades están en su `config.yml`): hielo, eléctrico, saltarín, multiplicador (suelta mini creepers) y gigante. Para probarlas: `/creeper invocar <variante> [cantidad] [jugador]`.
- **mantenimiento** (DolfinMantenimiento): copia el mundo cada 60 min en `backups/` (guarda las 24 últimas) y reinicia el servidor a las 05:00, avisando antes a los jugadores. `/respaldo` hace una copia en el momento y `/reinicio <min|cancelar>` programa o cancela un reinicio. Hay que arrancar el servidor con `server/iniciar.bat` para que vuelva a encenderse solo.

## Plugins de terceros

Se descargan de Modrinth con verificación SHA-512: `python scripts/instalar_plugins.py <carpeta>`.

| Plugin | Estado en 26.3 |
|---|---|
| LuckPerms 5.5.71, GriefPrevention 16.18.7, Chunky 1.5.3, DiscordSRV 1.30.5 | Funcionan |
| EssentialsX 2.22.0 | Funciona, pero avisa de que la versión no está soportada oficialmente |
| spark | Viene incluido en Paper (`/spark`) |
| CoreProtect 24.1 | **No arranca en 26.3**: hay que esperar a que publiquen una versión nueva |
| DiscordSRV | Necesita el token de un bot en `plugins/DiscordSRV/config.yml` |

## Banco de pruebas

`test-server/` y `test-client/` son una copia del servidor (offline, puerto 25566) y un Minecraft 26.3 con el jugador `Tester`. Con `python scripts/pruebas/banco.py todo` se recompila todo, se lanza el evento y se guardan capturas en `test-client/capturas/`. Las capturas necesitan que nadie esté usando el equipo.

## Resource pack

- Código fuente en `resourcepack/`. Las texturas y modelos se generan con `python scripts/generar_texturas.py`.
- `scripts/deploy-plugins.bat` comprime el pack y lo copia al servidor junto con los plugins.
