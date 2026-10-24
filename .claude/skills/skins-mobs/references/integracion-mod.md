# Integrar un diseño al mod

Solo después de que el usuario apruebe el diseño. Versiones del proyecto: Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.163.0+26.3, Java 25, mapeos oficiales de Mojang (ver `gradle.properties` de la raíz y de cada mod).

Antes de escribir código, **lee cómo lo hace hoy el mod de destino** y sigue su patrón. Las APIs de render de
Minecraft cambian seguido entre versiones; el código que ya compila en el repo es la mejor referencia.

## Cómo están armados los mods hoy

Los mods viven en `mods/<mod>/` y se compilan desde el `build.gradle` de la raíz.

- **`mods/zombies`** (`dolfinzombies`): 16 tipos en `ZombieKind.java`, con su habilidad en `kinds/*Zombie.java`.
  `client/KindZombieRenderer.java` dibuja cada tipo con varias capas de textura en
  `assets/dolfinzombies/textures/entity/`:
  - `<tipo>_zombie.png`: skin de humanoide de 64×64 sobre el modelo de zombie vanilla.
  - `<tipo>_zombie_accessories.png`: accesorios (sombreros, sacos, escudos…) con **su propia malla**,
    definida en `client/ZombieAttachments.java`.
  - `<tipo>_zombie_accessories_emissive.png` y `<tipo>_zombie_eyes.png`: partes que brillan y ojos (solo algunos).
  - **Mimo** y **contagiado** usan `PlayerLikeZombieRenderer`, que se pone la skin del jugador muerto.
  - Esas texturas **no se dibujan a mano**: las genera `tools/generate_zombie_assets.py` con `tools/zombie_textures.py`.
- **`mods/creepers`** (`dolfincreepers`): las 9 variantes en `Variante.java`, el renderer en
  `client/DolfinCreepersClient.java` y el jefe Rey Zombie en `dungeon/jefe/ReyZombie.java`.
- **`mods/space`** (`dolfinspace`): los mobs de Kepler en `mobs/` (`Parasite`, `Stalker`, `Worm`, `Sentinel`,
  `SpaceZombie`, `SpaceSkeleton`, `SpaceCreeper`) y su render en `client/mobs/`. Las texturas están en
  `assets/dolfinspace/textures/entity/`.

## Correspondencia diseño → mod

El diseño del skill es **un modelo completo**: cuerpo y accesorios en una sola textura empacada. Para cada mod:

- **Zombies:** separa el diseño. El cuerpo humanoide (cabeza, cuerpo, brazos, piernas) va a la skin de 64×64
  con el layout de jugador. El resto (cascos, sacos, escudos, cristales…) va a la malla de
  `ZombieAttachments` y a `_accessories`. Si un diseño cambia el esqueleto (hambriento flaco, bilioso gordo,
  ladrón encorvado), eso ya no es una skin: es un modelo nuevo. Avísale al usuario antes.
  Lo ideal es portar el diseño al generador existente (`zombie_textures.py`) para que todo siga
  saliendo de un solo script.
- **Creepers y Kepler:** si el diseño usa la geometría vanilla, basta con la textura. Si agrega piezas, hace
  falta un modelo propio (ver pasos).

## Pasos para un modelo propio

1. **Modelo**: genera el método con
   ```bash
   python .claude/skills/skins-mobs/scripts/exportar_java.py runtime/skins/<clave>/modelo.json
   ```
   Pégalo en una clase de modelo nueva que siga el patrón del mod de destino. El script convierte las
   coordenadas: el motor usa y hacia arriba y el frente en +z; Minecraft usa y hacia abajo con los pies en 24 y
   el frente en −z. Las piezas quedan como hijas directas de la raíz, cada una con su pivote y rotación.
2. **Textura**: copia `runtime/skins/<clave>/<clave>.png` a la carpeta de texturas del mod, con el nombre que
   use el renderer.
3. **Capa y renderer**: registra la capa del modelo y el renderer igual que los demás del mismo mod.
   El usuario pidió **sin aura** en los creepers gigantes: no agregues la capa de carga eléctrica.
4. **Animación mínima**: en `setupAnim`, agrega al menos el balanceo de patas o brazos al caminar.
   Sin eso, el mob se desliza como una estatua. Mantén las rotaciones base del diseño y suma el balanceo encima.
5. **Hitbox**: el modelo nuevo puede ser más ancho o alto que la hitbox actual. Compara con el visor
   ("Tamaño real") y ajusta el `EntityType` o la escala. Avisa al usuario, porque cambia el gameplay.
6. **Probar**: compila con `gradlew` desde la raíz, corre los tests (`python -m unittest discover -s tests`) y mira
   el mob en el cliente de prueba de frente y de espalda. Si una cara de la textura queda invertida o rotada,
   lo más probable es el mapeo de caras: compara con una pieza simple como la cabeza.
7. **Commit**: solo si el usuario lo pide.
