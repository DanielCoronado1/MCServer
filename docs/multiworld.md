# Estación Dolfin: mundos y demostraciones

`dolfinlobby` mantiene el lobby, las exhibiciones y el resguardo de supervivencia en el servidor. Cada conexión acaba en el lobby después de que Minecraft termine de registrar al jugador. Los marcos verde y azul conducen a supervivencia y exhibición. `/lobby` cancela la actividad y vuelve al lobby; no requiere operador.

## Instalación y migración

Java 25, Minecraft 26.3, Fabric Loader 0.19.5 y Fabric API 0.163.0+26.3. Multiworld permanece fijado a **1.14.2 / OwgBsqTu**, incluido Fantasy 0.8.4+26.3. Sus portales están rotos en esta versión; los activadores pertenecen al proyecto. No instalar Multiworld Inventories ni iCommonLib para intentar sustituir el protocolo de sesiones.

```sh
JAVA_HOME=/ruta/al/jdk25 ./gradlew build
python3 scripts/install_server.py --server-dir /ruta/al/servidor
```

Durante el mantenimiento autorizado, respaldar mundos, jugadores, configuración, manifiestos e inventarios antes de instalar. El instalador conserva configuración existente; comprobar las dos distribuciones y retirar artefactos antiguos mediante su manifiesto. Servidor: Lobby **1.0.0**, Space **1.2.0**, Zombies **1.1.0**, Creepers y Multiworld. AutoModpack: los mismos Creepers, Space y Zombies; **no** Lobby, Multiworld ni el JAR de pruebas.

Con el servidor arrancado y sin jugadores activos, ejecutar desde consola:

```text
dolfinlobby provisionar
dolfinlobby instalar
```

`provisionar` conserva mundos existentes; solo construye las cubiertas de mundos creados por esa invocación. No reconstruye ni reemplaza datos de dimensiones que fallaron al cargar. `instalar` realiza la migración funcional explícita de zonas reservadas, abre marcos, coloca 25 controles y señalética, prepara plataforma/outpost y cambia únicamente la dificultad nativa del showcase a NORMAL. El lobby sigue pacífico. Rechaza reconstruir mientras haya sesiones de combate o Space. No modifica la dificultad ni reglas de supervivencia.

La instalación usa APIs internas limitadas: registra showcase como origen de lanzamiento en Space y como mundo de demostración en Zombies. No hace falta ampliar el allowlist de generación natural de Zombies, conceder permisos Multiworld, cambiar Essential Commands/LuckPerms ni alterar `server.properties`. Los comandos ajenos a `/lobby` y `/exhibicion` quedan bloqueados durante una sesión aislada, incluyendo variantes con namespace. En supervivencia conservan sus permisos y comportamiento previos.

La configuración `config/dolfin-lobby.json` mantiene esquema 1, IDs `dolfin:lobby` y `dolfin:showcase`, semilla 7340291. Los IDs son fijos y requieren reinicio para cambios: cambiar un ID no renombra un mapa. Kepler continúa siendo **dolfinspace:kepler**, una sola dimensión. Una configuración o resguardo incompatible impide abrir el servidor y conserva los archivos para reparación; no desactiva silenciosamente el aislamiento.

## Uso ordinario

- Lobby: marco verde a supervivencia, azul a exhibición.
- Exhibición: pulsar cualquiera de los 25 botones inicia un combate privado con equipo desechable; las variantes se invocan deliberadamente y no dependen del bioma. Hay nueve Creepers y dieciséis Zombies.
- `/exhibicion lista` explica el catálogo; `/exhibicion probar <1–25>` activa la misma estación. `/exhibicion volver` reinicia y regresa al salón.
- Space: botón de preparación, cabina y despegue real a Kepler. Allí, volver a la nave y despegar para regresar. `/exhibicion despegar` es el control alternativo accesible. El retorno termina la sesión, elimina su nave y limpia el equipo. Hay una nave de demostración a la vez; los demás jugadores pueden usar hasta dieciséis arenas independientes.
- `/lobby` siempre cancela la actividad. Una muerte de demostración se cancela, limpia la sesión y vuelve al lobby. Muerte, drops y reaparición de supervivencia mantienen la ruta ordinaria.

El Jinete recibe su inicialización completa y montura; Bruja/Gritón tienen un aliado. Hay piscina profunda, madera y cultivos desechables para las habilidades. El Granjero activa cultivos/animales únicamente en la entidad etiquetada de demostración; la configuración ordinaria permanece intacta. Mimo usa la apariencia del participante. Contagiado muestra infección y cura; la conversión/custodia al morir no toma inventario de supervivencia porque se cancela la muerte de demostración.

## Estado y recuperación

| Archivo | Contrato |
| --- | --- |
| `<level-name>/dolfin-lobby/provisioning.json` | Propiedad del constructor inicial, esquema 1, trazado base 1; nunca guarda jugadores |
| `dolfin-lobby/layout-2.ready` | Instalación funcional completada |
| `dolfin-lobby/layout-generation.txt` | Generación UUID de señalética; rechaza etiquetas antiguas que terminan de cargar tarde |
| `dolfin-lobby/sessions/<UUID>.nbt` | Resguardo autoritativo, esquema 1: NBT completo de supervivencia, dimensión/posición/orientación, fase, arena y UUID exacto de nave |
| Datos guardados de Space `demo_cancellations` | Tombstones persistentes por UUID exacto de nave cancelada |

El resguardo se escribe mediante reemplazo atómico antes de vaciar el inventario. Se cierran menús y se devuelven cursor/crafting antes de capturar. Se conservan equipo y componentes anidados, XP, tanque equipado, Ender, hambre, salud, efectos y datos de reaparición. Las estadísticas, avances y recetas no se conceden en demostración. Se bloquean contenedores, transferencias a entidades, drops y recogida; los ítems/XP del espacio protegido se descartan.

Al regresar a supervivencia se restaura el resguardo, se realiza el traslado autorizado a la dimensión original, se guarda el jugador y finalmente se elimina el resguardo. Una interrupción anterior repite la misma restauración. Una reconexión conserva el resguardo y vuelve al lobby; no captura encima equipo de demostración. JOIN ocurre antes de registrar al jugador en su mundo: se resguarda inmediatamente pero el traslado espera al final del tick. Las transiciones rápidas también se serializan por jugador/tick.

Space guarda `homeDimension`, cuyo valor ausente en naves antiguas equivale a `minecraft:overworld`; conserva coordenadas, carga y combustible. La demostración solo autoriza su vuelo legítimo hacia Kepler. El regreso de un resguardo originalmente capturado en Kepler también se autoriza específicamente. La recuperación de una nave de supervivencia queda pausada mientras su ocupante está aislado y se reanuda al elegir supervivencia. Cancelar una demo no selecciona todas las naves del dueño: persiste y vacía únicamente su UUID de sesión, aun si está descargada.

## Coordenadas y límites para decoración

- Cubiertas de lobby/showcase: `x=-48..48`, `z=-32..32`, suelo `y=128`, pared `y=129..135`; llegada `(0.5,129,20.5)`.
- Marcos de lobby: centros `x=-20/+20,z=-3`; bocas abiertas `x=centro±2,y=129..133`. Activador accesible delante: `abs(z+1)<2`, `abs(x-centro)<2.5`, `y<134`.
- Salida showcase: `z>28,abs(x)<3`. No mover estas áreas sin actualizar el router.
- Botones Creepers: `x=-44+(índice%4)*6,z=-25+(índice/4)*7,y=130`, índice 0–8. Zombies: `x=-16+(índice%4)*6`, mismo z/y, índice 0–15. Soporte sólido en y129. Señalética en y132.
- Plataforma Space: `x=28..32,z=-16..-12,y=128`, centro `(30,128,-14)`; botón `(30,130,-5)`.
- Arenas privadas: centro `(128+slot*64,128,0)`, slot 0–15. Límite exterior `centroX±12,z±12`, subsuelo bedrock y techo y141. La sesión permite jugador y122..145. Piscina revestida `x=centroX+5..9,z=-7..-3,y=122..128`. Solo interior `centroX±9,z±9,y=128..135` admite modificaciones de mobs; el revestimiento bajo agua permanece protegido. Reiniciar descarta entidades del cubo reservado y reconstruye el circuito antes de usarlo.
- Outpost real de Kepler: plataforma `(100000,200,100000)`, llegada `(100000.5,201,100008.5)`, cubierta `x/z=99984..100016`. Reservado/protegido: `abs(x-100000)<=20,abs(z-100000)<=20,y=180..260`. No construir supervivencia en esa reserva.
- Lobby/showcase completos quedan protegidos; también el outpost incluso para visitas de supervivencia. El jugador de demo no puede salir del outpost a pie. Despegue/descenso legítimos conservan su vehículo.

Etiquetas del proyecto: `dolfin_function_label`, `dolfin_outpost_label`, `dolfin_layout_<UUID>`. La migración carga los límites antes de retirar sus etiquetas y también descarta generaciones obsoletas al cargarlas. No usar estos tags para decoración ajena. Construir mediante `StationProtection.edit` dentro de una herramienta explícita; los cambios de bloques externos se rechazan incluso si provienen de explosiones. `WorldProvisioner` y `StationLayout` son los únicos constructores normales. `instalar` no repinta toda la cubierta: conserva ediciones ajenas a controles y áreas reservadas.

## Validación reproducible

```sh
JAVA_HOME=/ruta/al/jdk25 ./gradlew build :mods:lobby:fixtureJar
# Instalar una instancia NUEVA aislada; añadir manualmente el JAR build/fixtures al mods de pruebas.
python3 -m tests.manual.prueba_lobby_funcional \
  --server-dir /ruta/al/servidor-aislado --java /ruta/al/jdk25/bin/java
```

Exige IP 127.0.0.1 y offline solo en esa instancia. El JAR fixture se genera fuera de `build/libs`, no forma parte de instalación/distribución normal. Usa dos `ServerPlayer` reales registrados por `PlayerList` con conexiones que absorben paquetes; entrega explícitamente el aviso PlayerLoaded cuando prueba daño. Verifica estado, comandos, activadores, inventarios, catálogo, vuelos reales y recuperación. Solo la matriz de fallos/reinicios inyecta journals de fase. El arnés reinicia para COUNTDOWN/ASCENT/TRANSITION/DESCENT y provoca un arranque rechazado por esquema incompatible, conservando los bytes originales. No sustituye renderizado real del cliente ni clics en cabina. La prueba base anterior describe la etapa fundacional y no pretende operar mapas funcionales protegidos.
