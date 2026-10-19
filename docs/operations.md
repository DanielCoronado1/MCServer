# Operación y versiones

[← Volver a DolfinServer](../README.md)

## Instalar y actualizar

Ejecuta los comandos desde la raíz del repositorio o de la versión descomprimida. Instala con `python3 scripts/install_server.py`; usa `--server-dir /ruta/absoluta` para elegir otro destino, incluido un servidor administrado por Crafty. **Detén ese servidor antes de instalar.**

La instalación crea la configuración que falta sin sobrescribir ajustes locales. Revisa y aplica manualmente los cambios posteriores de las plantillas. La lista de nombres se combina con el JSON del servidor y conserva a los jugadores existentes. La aceptación de la EULA y los operadores son estado local: no se incluyen en las versiones. En una instalación nueva, lee y acepta la EULA en `runtime/server/eula.txt` antes de arrancar.

Los valores incluidos son 25 jugadores, lista de acceso activada, heap máximo de 10 GB, distancia de visión 8 y distancia de simulación 6. Ajústalos a tu equipo. `online-mode=false` es la política de la configuración existente; si migras a cuentas autenticadas, revisa también los datos de identidad de los jugadores.

AutoModpack distribuye los mods de cliente desde `automodpack/host-modpack/main/`. Los mods exclusivos del servidor permanecen en `mods/` dentro del entorno de ejecución. Comparte con los jugadores la huella del certificado que imprime AutoModpack para que verifiquen la primera conexión.

## Preparar una versión

Compila cada proyecto de `mods/` y ejecuta:

```sh
python3 scripts/build_release.py 0.0.0-test
```

En `dist/` se generan un ZIP del servidor, un `.mrpack` de cliente con los metadatos de descarga de Fabric y AutoModpack, y las notas `notas.md`. El ZIP contiene plantillas, metadatos fijados de Packwiz, mods propios compilados, instalador, configuración de despliegue y documentación. No incluye mundos, operadores, aceptación de la EULA ni UUID generados. Los mods de terceros se descargan y verifican durante la instalación.

Descomprime las actualizaciones en la raíz existente del repositorio o de la versión, detén el servidor y vuelve a ejecutar el instalador. El estado de ejecución se mantiene separado. Haz una copia de seguridad antes de actualizar Minecraft, mods o Crafty.

GitHub Actions valida Packwiz, compila todos los mods propios, ejecuta las pruebas de herramientas y empaqueta artefactos en pushes y pull requests. Una etiqueta `v<versión>` publica una versión en GitHub. Los jugadores importan el `.mrpack` en Prism Launcher o Modrinth App, se conectan, verifican la huella del servidor, aceptan los mods y reinician cuando AutoModpack lo solicita.

## Migrar la estructura anterior

El antiguo entorno `server-fabric/` ahora es `runtime/server/`; las plantillas versionadas están en `server/`. Con el servidor detenido, respalda y copia los mundos, mods, configuración, operadores, lista de acceso, EULA, certificados y `.dolfin-instalados.json` a `runtime/server/` antes de instalar. No los sobrescribas con las plantillas. `mods-src/` ahora es `mods/`; `scripts/pruebas/` pasó a `tests/manual/`. Los comandos actuales son `scripts/install_server.py` y `scripts/build_release.py`.
