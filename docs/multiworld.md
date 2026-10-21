# Base de la estación Dolfin

Esta base se prepara mediante `/dolfinlobby provisionar` desde consola u operador de nivel 2. El arranque **no** crea ni reconstruye mundos. Las rutas de jugadores siguen cerradas: no hay `/lobby`, teletransporte automático al conectar ni portales activos hasta implementar aislamiento y recuperación de sesiones. Los marcos de cristal son indicadores de destino, sin activadores.

## Versiones y distribución

- Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Java 25.
- [Multiworld 1.14.2 / OwgBsqTu](https://modrinth.com/mod/multiworld/version/OwgBsqTu), bundle Fabric fijado por SHA-512 en `pack/mods/multiworld.pw.toml`.
- El bundle declara `environment="*"`, pero el proyecto upstream clasifica cliente opcional/servidor requerido. El artefacto 26.3 es funcional en servidor sin instalarlo en el cliente: `side="server"` evita distribuir una dependencia cliente innecesaria.
- El JAR anidado `Multiworld-Fabric-26.3.jar` exige Fabric API e incluye Fantasy 0.8.4+26.3. El instalador ya fija Fabric API; no se añade una segunda copia de Fantasy.
- Multiworld Inventories (`tXvf0aoF`) e iCommonLib (`SVKv1SZo`) son dependencias **opcionales** del registro de Modrinth. No se instalan: la sesión del proyecto tendrá que coordinar inventario, viajes y recuperación de Space como una sola operación.
- La nota oficial de esta versión dice `Known Bugs: Portals Currently broken`. Añadir iCommonLib no constituye una solución demostrada para 26.3. La etapa funcional debe implementar activadores controlados dentro de `dolfinlobby`, sin depender de los portales upstream.
- Licencias declaradas: proyecto Multiworld LGPL-3.0-or-later; JAR 26.3 LGPL v3; envoltura del bundle CC0-1.0; Fantasy LGPL v3. Se redistribuyen los JAR originales sin modificación, con sus metadatos/licencias incluidos.
- `dolfinlobby` es un mod **solo servidor**. Creepers, Zombies y Space siguen llegando tanto al servidor como a AutoModpack. El instalador descubre módulos construidos por el Gradle raíz aunque no tengan `build.gradle` propio y respeta `environment` de cada JAR propio.

## Instalación y preparación

Desde una copia de trabajo, con un JDK 25 seleccionado:

```sh
JAVA_HOME=/ruta/al/jdk25 ./gradlew build
python3 scripts/install_server.py --server-dir /ruta/al/servidor-aislado
```

Acepta el EULA en esa instancia, elige una dirección/puerto libre y arranca Fabric. En su consola:

```text
dolfinlobby provisionar
execute in dolfin:lobby run say Lobby disponible
```

No uses `mw create` desde la consola en esta versión: Multiworld lo rechaza porque requiere un jugador. El módulo usa directamente `MultiworldMod.createConfigAndWorld`, con el mismo guardado nativo de Multiworld, contra la API del JAR 26.3 fijado en Gradle. No se simulan jugadores ni se empaqueta otra copia de Multiworld.

## Identificadores y estado

| Recurso | Valor predeterminado |
| --- | --- |
| Configuración | `config/dolfin-lobby.json` |
| Versión de configuración | `schemaVersion: 1` |
| Lobby | `dolfin:lobby` |
| Exhibición | `dolfin:showcase` |
| Semilla de nuevos mundos | `7340291` |
| Supervivencia existente | `minecraft:overworld`, `level-name=world` intacto |
| Kepler existente | `dolfinspace:kepler`, no se crea ninguna copia |
| Manifiesto de construcción | `<level-name>/dolfin-lobby/provisioning.json` |
| Datos de mundos | `<level-name>/dimensions/dolfin/{lobby,showcase}/` |
| Configuración nativa Multiworld | `<directorio del mundo>/multiworld-world.yml` |
| Versión del trazado | `worlds["<id>"]: 1` en el manifiesto |

Solo se aceptan identificadores `dolfin:<nombre>` distintos, con nombre formado por letras ASCII minúsculas, números, `_` o `-`. Cambiar configuración requiere reinicio; no existe recarga parcial. Una configuración inválida o versión desconocida bloquea la preparación y registra `event=lobby_disabled` sin modificar mapas. Los valores por defecto se escriben únicamente si falta el archivo.

`provisionar` comprueba cada dimensión cargada: si ya existe, conserva todos sus datos sin reconstruir, mover spawn ni añadir etiquetas. Si el manifiesto registra un mundo que Multiworld no cargó, o existe su directorio de datos sin cargar, falla sin reemplazarlo. Un mundo existente sin entrada en el manifiesto también se conserva; no se adopta ni decora silenciosamente. Una interrupción durante construcción puede dejar un mapa parcial: inspeccionarlo y repararlo explícitamente, nunca borrarlo para reintentar a ciegas.

El manifiesto pertenece al guardado, no al directorio global de configuración. Se guarda después de construir y vaciar los chunks. Versiones desconocidas requieren migración manual; no hay migración destructiva automática. Cambiar IDs después de preparar mapas **no** los renombra: los anteriores siguen existiendo. Mantener los IDs fijos; una migración deliberada requiere copia de seguridad de config, manifiesto y directorios Multiworld.

## Plano provisional y contratos para la etapa funcional

Los dos mundos son planos y de dificultad pacífica para que la base no genere combates sin aislamiento. Las futuras demostraciones necesitarán dificultad adecuada en exhibición y controles propios de generación, límites, reinicio y botín; no cambiar supervivencia. El catálogo actual contiene 9 variantes de Creepers y 16 de Zombies. Zombies conserva `mundosPermitidos=["minecraft:overworld"]`; las demostraciones deben invocarse y administrarse deliberadamente.

- Cubierta: `x=-48..48`, `z=-32..32`, suelo en `y=128`, paredes de cristal `y=129..135`, acceso seguro en `(0.5,129,20.5)`. Spawn de Multiworld fijado a `(0,129,20)`.
- Lobby: marco de supervivencia centrado en `(-20,131,-3)`; marco de exhibición en `(20,131,-3)`. Los activadores futuros deben situarse en el espacio accesible delante del cristal o reemplazar el relleno mediante una edición explícita.
- Exhibición: ala Creepers `x=-40..-20,z=-24..-4`; Zombies `x=-10..10,z=-24..-4`; Space `x=20..40,z=-24..-4`. Llegada y circulación en la mitad `z>=0`.
- Etiquetas vanilla `minecraft:text_display`, etiqueta de entidad `dolfin_station_label`; texto íntegramente en español. Este tag permite actualizar señalética sin afectar otras entidades.
- `DolfinLobby`: registro de comandos y ciclo de vida. `LobbyConfig`: IDs/config y claves de dimensión. `WorldProvisioner`: construcción inicial y manifiesto. Todo bajo `mods/lobby/src/main/java/com/dolfin/lobby/`.

La implementación de sesiones, inventario aislado, protección y rutas controladas debe vivir en **este módulo servidor**. Añadir APIs mínimas de viaje/cancelación en Space cuando sean necesarias; conservar allí la lógica de naves y Kepler. No activar rutas ordinarias hasta que el aislamiento persistente, la salida `/lobby`, muerte/reconexión y la cancelación de recuperación de vuelo funcionen juntos.

Aún **no existe un esquema de sesiones**: no hay jugadores/datos inventados que migrar. La etapa 2 debe definir un almacenamiento versionado dentro de `<level-name>/dolfin-lobby/`, con UUID del jugador, fase de sesión, instantánea de supervivencia, origen/destino y nave de demostración, más recuperación idempotente al arrancar. El manifiesto de construcción no debe reutilizarse para inventarios o sesiones. Evitar depender de restauraciones separadas de Multiworld Inventories que no conocen vuelos pendientes.

El trazado inicial nunca se reconstruye al repetir el comando. Para las siguientes etapas, editar mapas existentes con una operación explícita y respaldada; si hace falta automatizarla, añadir una migración de trazado versionada que compruebe la versión anterior y preserve cambios del operador. No subir la versión y volver a ejecutar el constructor inicial sobre mapas ya preparados.

## Comprobación reproducible

```sh
python3 -m unittest tests.test_tooling
python3 -m tests.manual.prueba_multiworld_base \
  --server-dir /ruta/al/servidor-aislado \
  --java /ruta/al/jdk25/bin/java
```

El segundo comando **escribe bloques de prueba** y exige `server-ip=127.0.0.1`, IDs predeterminados y una instancia instalada aislada. Arranca el pack completo tres veces, prepara cuatro veces, cambia bloques dentro de ambas cubiertas y supervivencia, comprueba suelo/paredes/etiquetas/ediciones después de reiniciar, comprueba manifiesto estable y configuración de supervivencia conservada, y apaga cada proceso limpiamente. No es una prueba de interacción visual, permisos de jugadores, viajes, combate ni inventario; esos límites siguen pendientes de las etapas sucesoras.

## Requisitos de instalación en un servidor existente

El instalador siembra archivos faltantes y conserva config ya existente. Instalar el JAR no cambia permisos de LuckPerms, Essential Commands, mundos permitidos de Zombies ni rutas de Space. Antes de habilitar la experiencia final habrá que migrar explícitamente los ajustes vivos correspondientes y verificar las dos distribuciones de mods. La base no requiere alterar `server.properties` de supervivencia ni conceder permisos Multiworld a jugadores ordinarios.
