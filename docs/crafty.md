# Crafty Controller

[← Volver a DolfinServer](../README.md)

Un panel web para gestionar el ciclo de vida del servidor. El despliegue de `deploy/crafty/compose.yaml` usa la imagen oficial de Crafty 4.11.0 y directorios persistentes dentro de `runtime/crafty/`, excluido de Git. Publica HTTPS en **8443** y Minecraft en **25565**. AutoModpack utiliza el puerto de Minecraft con la configuración HOLEPUNCH incluida.

Requiere Docker Engine con Compose en un host Linux, incluida una máquina virtual Linux. La documentación de Crafty no admite almacenar mundos con Docker Desktop para Windows; para esa opción, usa Crafty nativo. Consulta las referencias de [instalación con Docker](https://docs.craftycontrol.com/pages/getting-started/installation/docker/) e [importación de servidores](https://docs.craftycontrol.com/pages/user-guide/server-creation/minecraft/).

El despliegue de este proyecto en Windows usa Docker Engine dentro de Ubuntu WSL2: consulta la [guía del host Windows](windows-host.md).

## Primera instalación

1. Compila los mods propios y ejecuta `python3 scripts/install_server.py`. Revisa la configuración y acepta la EULA en `runtime/server/eula.txt`. Detén cualquier servidor que esté ejecutándose directamente.
2. Arranca el panel desde la raíz del repositorio o de la versión:

   ```sh
   docker compose -f deploy/crafty/compose.yaml up -d
   docker compose -f deploy/crafty/compose.yaml logs -f
   ```

3. Abre `https://localhost:8443` o la dirección del host. El certificado inicial es autofirmado. Las credenciales iniciales están en `runtime/crafty/config/default-creds.txt`; cambia la contraseña desde el panel y mantén ese directorio privado.
4. En el host Linux de Docker, ejecuta `python3 deploy/crafty/configure_server.py` como root. Lee las credenciales iniciales privadas, importa `runtime/server/` mediante la API de Crafty y un ZIP temporal, selecciona Java 25 y activa el arranque automático y la detección de fallos. Conserva la aceptación existente de la EULA y arranca el servidor. En otras arquitecturas, indica la ruta de Java 25 de la imagen mediante `--java`.
5. Comprueba en el panel que el servidor usa Java 25, heap inicial de 2 GB y máximo de 10 GB, arranque automático y detección de fallos. Crafty gestiona el ciclo de vida: debe ejecutar Java directamente, sin el lanzador de Windows que reinicia el proceso.
6. Arranca desde Crafty y verifica que la consola llega a `Done`, la lista de acceso está completa y un jugador puede conectarse y descargar los mods.

Ejecuta una sola copia del servidor en el puerto 25565. Tras importar, el mundo y la configuración activos están en `runtime/crafty/servers/<server-id>/`; `runtime/server/` queda como fuente de importación conservada. Protege el acceso al panel mediante el firewall del host o una red privada.

## Actualizaciones y copias de seguridad

Detén el servidor desde Crafty antes de instalar mods en su **directorio administrado**:

```sh
python3 scripts/install_server.py --server-dir runtime/crafty/servers/<server-id>
```

Después arráncalo desde Crafty. Revisa los cambios de plantillas por separado; el instalador conserva los ajustes activos. Configura las copias del mundo en el panel. Para recuperar el controlador, respalda todo `runtime/crafty/`, incluidas su configuración, base de datos y servidores administrados. `docker compose -f deploy/crafty/compose.yaml down` detiene el controlador y conserva los datos de los directorios montados.

La versión de la imagen está fijada para revisar las actualizaciones. Cambia la etiqueta deliberadamente, respalda el estado y ejecuta `docker compose -f deploy/crafty/compose.yaml pull`, seguido de `docker compose -f deploy/crafty/compose.yaml up -d`. Comprueba que Java 25 sigue disponible.
