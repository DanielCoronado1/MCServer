# Host Windows con arranque automático

[← Volver a DolfinServer](../README.md)

## Estructura del despliegue

En el despliegue documentado, el repositorio de Windows está en `C:\Users\emi0x7d1\Projects\MCServer`. Docker Engine se ejecuta en **Ubuntu WSL2**. El repositorio Linux está en `/srv/dolfin`; sus datos se guardan en el sistema de archivos Linux de la distribución. El `runtime/server/` original de Windows se conserva como copia de migración. Tras importar, el mundo activo está en `/srv/dolfin/runtime/crafty/servers/<server-id>/`.

## Arranque y recuperación

- La tarea **DolfinServer-WSL** del Programador de tareas arranca al encender Windows con la identidad S4U del propietario de la distribución y privilegios elevados. No necesita contraseña guardada ni inicio de sesión interactivo.
- `deploy/windows/start-crafty.ps1` inicia Docker, aplica el despliegue de Compose, actualiza el reenvío de puertos a la dirección actual de WSL y mantiene WSL activo. Reintenta ante fallos de WSL; el Programador de tareas también reinicia la tarea si su proceso falla.
- El servicio systemd de Docker está habilitado. El contenedor de Crafty usa `restart: always` y dispone de 120 segundos para apagarse correctamente.
- Minecraft tiene **auto start** y **crash detection** activados en Crafty, Java 25 y heap inicial de 2 GB y máximo de 10 GB. Crafty comprueba los fallos periódicamente y deja de reintentar tras varios fallos consecutivos. Si detienes el servidor desde Crafty, queda detenido hasta que lo arranques explícitamente o se reinicie el controlador.
- Windows reenvía TCP 25565 y 8443 a WSL. El firewall permite conexiones de Minecraft y restringe el panel a direcciones LAN/Tailscale.

Los registros del host están en `runtime/host/startup.log` del repositorio Windows; los datos y registros de Crafty, en `runtime/crafty/` del repositorio Linux. La configuración documentada de `.wslconfig` limita WSL a 12 GB de RAM y 4 GB de intercambio; la suspensión con alimentación de corriente estaba desactivada. Comprueba estos valores en el equipo actual antes de asumirlos.

## Preparar otro equipo

Usa PowerShell elevado, virtualización WSL2 habilitada y una distribución Ubuntu propiedad de la cuenta que ejecutará la tarea. Habilitar estas funciones puede requerir reiniciar. Configura `[boot] systemd=true` en `/etc/wsl.conf` de Ubuntu y reinicia esa distribución. Reserva memoria para Windows al dimensionar WSL, el heap y el controlador.

Compila e instala el servidor y detenlo antes de copiar su estado. En Ubuntu, como root, ejecuta:

```sh
bash /mnt/c/Users/<nombre>/Projects/MCServer/deploy/windows/provision-wsl.sh /mnt/c/Users/<nombre>/Projects/MCServer
```

El script instala Docker desde su repositorio oficial de Ubuntu y copia el entorno conservado a Linux solo si todavía no existe uno allí. Nunca sobrescribas un mundo administrado activo con la copia de migración.

Ejecuta `python3 /srv/dolfin/deploy/crafty/configure_server.py` como root en Ubuntu. Usa las credenciales privadas generadas para importar el servidor con la EULA ya aceptada y activar el arranque automático y la detección de fallos. No imprime contraseñas ni tokens. La ruta predeterminada de Java corresponde a la imagen amd64; usa `--java` para otras arquitecturas. Tras cambiar la contraseña de administración, configura la instancia desde el panel en lugar del asistente de aprovisionamiento inicial.

Finalmente, desde el repositorio Windows en PowerShell elevado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/windows/install-startup.ps1
```

El instalador registra e inicia la tarea de arranque, habilita IP Helper y crea las reglas del firewall. Este despliegue no usa Docker Desktop ni necesita iniciarlo al entrar a Windows. Reinicia una vez para comprobar que la tarea y ambos servicios arrancan sin iniciar sesión en el escritorio.

## Operación diaria

Abre `https://<host-Windows>:8443`. Las credenciales iniciales están en `/srv/dolfin/runtime/crafty/config/default-creds.txt`, dentro de Ubuntu; mantenlas privadas y cámbialas desde Crafty.

Para ejecutar Docker desde Windows:

```powershell
wsl -d Ubuntu -u root -- docker compose -f /srv/dolfin/deploy/crafty/compose.yaml ps
```

Detén y arranca Minecraft desde Crafty. Antes del mantenimiento, deshabilita y detén la tarea de arranque y luego detén Crafty con Compose; de lo contrario, el supervisor podría volver a levantar los servicios. Al terminar, habilita e inicia la tarea. Antes de reiniciar Windows, detén Minecraft de forma ordenada desde el panel para guardar el mundo.

Para actualizar el código, actualiza el repositorio Windows y avanza el repositorio Linux desde su origen de Windows sin reescribir su historial. Para actualizar mods, usa Python 3.11+ y apunta al **directorio administrado de Linux**. En el host Ubuntu 22.04 documentado, Python 3.13 de Windows puede ejecutar `scripts/install_server.py --server-dir \\wsl.localhost\Ubuntu\srv\dolfin\runtime\crafty\servers\<server-id>` desde el repositorio Windows con Minecraft detenido. Aplica los cambios de plantillas por separado.

Respalda todo `runtime/crafty/` de Linux, incluidas configuración, base de datos y mundos administrados. Mantén los datos de ejecución de ambos repositorios fuera de Git.

Referencias: [Docker Engine en Ubuntu](https://docs.docker.com/engine/install/ubuntu/), [systemd en WSL](https://learn.microsoft.com/en-us/windows/wsl/systemd) e [instalación de Crafty con Docker](https://docs.craftycontrol.com/pages/getting-started/installation/docker/).
