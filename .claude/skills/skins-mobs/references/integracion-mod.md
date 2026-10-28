# Integrar un diseño al mod

Solo después de que el usuario apruebe el diseño. Versiones del proyecto: Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.163.0+26.3, Java 25, mapeos oficiales de Mojang (ver `gradle.properties` de la raíz y de cada mod).

Antes de escribir código, **lee cómo lo hace hoy el mod de destino** y sigue su patrón. Las APIs de render de
Minecraft cambian seguido entre versiones; el código que ya compila en el repo es la mejor referencia.

## Cómo están armados los mods hoy

Los mods viven en `mods/<mod>/` y se compilan desde el `build.gradle` de la raíz.

- **`mods/zombies`** (`dolfinzombies`): 16 tipos en `ZombieKind.java`, con su habilidad en `kinds/*Zombie.java`.
  `client/KindZombieRenderer.java` dibuja cada tipo con su modelo de diseño (`DesignModel`), su textura
  `<tipo>_zombie.png`, la capa exterior y, en algunos, los ojos brillantes (`_ojos.png`). Los accesorios
  (cascos, sacos, escudos…) son piezas del diseño.
  - **Mimo** y **contagiado** usan `PlayerLikeZombieRenderer`: muestran su diseño hasta copiar la skin de un jugador.
  - `tools/generate_zombie_assets.py` ya no escribe la skin. Sigue generando huevos, sonidos, idiomas y los
    atlas antiguos `_accessories`/`_eyes`, que los tests de assets todavía piden.
- **`mods/creepers`** (`dolfincreepers`): las 9 variantes en `Variante.java`, el renderer en
  `client/DolfinCreepersClient.java` y el jefe Rey Zombie en `dungeon/jefe/ReyZombie.java`.
- **`mods/space`** (`dolfinspace`): los mobs de Kepler en `mobs/` (`Parasite`, `Stalker`, `Worm`, `Sentinel`,
  `SpaceZombie`, `SpaceSkeleton`, `SpaceCreeper`) y su render en `client/mobs/`. Las texturas están en
  `assets/dolfinspace/textures/entity/`.

## Cómo llega un diseño al juego

Los 33 mobs del mod se dibujan con **su propio modelo**, igual al del visor. No usan los modelos vanilla.

1. `python tools/skins/integrate_existing.py` exporta cada diseño a su mod:
   - `textures/entity/<mob>.png`: la textura del diseño, sin remuestrear.
   - `<mob>_exterior.png`: la capa exterior con relieve.
   - `<mob>_ojos.png`: solo los píxeles de la rampa `OJO`, que se dibujan a plena luz.
   - `dolfin/modelos/<mob>.json`: el modelo en coordenadas de Minecraft (y hacia abajo, pies en 24, frente en −z),
     con las rotaciones convertidas exactas aunque combinen ejes.

   Para agregar un mob, súmalo a `targets()`.
2. **Huesos y animación.** El integrador agrupa las piezas en huesos según su rol:
   - `cabeza`/`ojo` mira.
   - Las patas (`pierna`, `pata`, `muslo`…) se balancean en fases alternas.
   - Los brazos se mecen.
   - Colas, tentáculos, algas y capas cuelgan y se mecen.

   Una pieza sigue al hueso cuya pieza contiene su pivote: el casco va con la cabeza y la canilla con el muslo.
   El tronco (`cuerpo`, `panza`, `torax`…) queda fijo. Si un diseño nuevo usa otro nombre para una pata o la
   cabeza, agrégalo a las tuplas `HEADS`/`LEGS`/`ARMS`/`DANGLING`/`TRUNK`.
3. **Modelo genérico.** `ModeloDiseno` (creepers) y `DesignModel` (zombies, space) leen ese JSON y arman tres
   mallas:
   - la base;
   - la exterior, cada caja inflada con `CubeDeformation` según `inflado()` del motor;
   - el aura de carga de los creepers.

   Los brazos sostienen objetos (`ArmedModel`).
4. **Renderers.** Los renderers conservan lo del vanilla:
   - El creeper se hincha, parpadea y lleva el aura al cargarse. Los gigantes van **sin aura**, como pidió el
     usuario.
   - Los zombies conservan los objetos en la mano, los ojos brillantes y la invisibilidad del explorador.
   - Mimo y contagiado muestran su diseño hasta copiar a un jugador.

   La escala del mob la da su atributo `SCALE`. El renderer solo corrige la diferencia con la `escala` del
   diseño.
5. **Hitbox.** Ajústala al tamaño del diseño: el integrador imprime alto y ancho en bloques. Avisa al usuario,
   porque cambia el gameplay.
6. **Probar en el juego.** Corre `python -m tests.manual.prueba_visual_skins [mob]` con `JAVA_HOME` en un JDK 25.
   Abre el servidor de pruebas (`runtime/test-server`) y un cliente aparte con portablemc (`runtime/test-client`),
   pone cada mob quieto y lo fotografía de frente y de espalda con el ángulo del visor. Las capturas quedan en
   `runtime/test-client/capturas/skins-<fecha>/`. No uses el equipo mientras corre. Compara cada captura con el
   visor antes de dar el trabajo por hecho.
7. **Commit**: solo si el usuario lo pide.
