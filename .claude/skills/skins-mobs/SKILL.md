---
name: skins-mobs
description: Diseña skins, texturas y modelos 3D de mobs para DolfinServer (Minecraft 26.3 / Fabric) con el estilo tétrico propio del server, genera vistas previas y un visor 3D, y los integra al mod. Úsalo siempre que se hable de skins, texturas o el aspecto de un mob (creepers, zombies, esqueletos, arañas, jefes, mobs nuevos de una épica como la espacial), de rediseñar o mejorar cómo se ve un mob, de variantes "mutadas", de crear un mob nuevo que necesite verse de alguna forma, o de pasar un diseño de mob al mod, aunque el usuario no diga "skin" ni "textura".
---

# Skins de mobs de DolfinServer

Este skill convierte una idea de mob en un diseño revisable (textura PNG, lámina de vistas y visor 3D interactivo)
y, cuando el usuario lo aprueba, en código del mod. Todo el arte se genera con código: un motor de texturas en
Python (`scripts/motor.py`) y un archivo de diseño por mob en `tools/skins/mobs/`.

## El distintivo: siempre tétricos

Todos los mobs del server se ven **un poco tétricos**: mutados, podridos, inquietantes. Es la identidad visual que
el dueño del server eligió y la vara con la que juzga cada diseño. Los referentes son el **creeper gigante** y el
**zombie gigante** (`tools/skins/mobs/creeper_gigante.py`, `zombie_gigante.py`). Un mob "simpático", limpio o
caricaturesco no cumple, por bien dibujado que esté. Tétrico no es gore explícito: es la sensación de que algo
salió mal con esa criatura.

Antes de diseñar, lee `references/estilo-tetrico.md`: explica qué lo hace tétrico, la paleta y el checklist
que usarás para criticar tu propio resultado.

## Flujo de trabajo

### 1. Entender el pedido
Aclara lo mínimo: ¿mob nuevo o rediseño de uno existente? ¿Qué base tiene (humanoide, creeper, cuadrúpedo)?
¿Escala normal o jefe/gigante? ¿Es solo concepto o va al mod? Si el usuario dice "sorpréndeme", propón tú el
concepto en 2-3 líneas antes de dibujarlo. Si algo es ambiguo y cambia mucho el diseño, pregunta.

### 2. Concepto tétrico
Define en una frase **qué era** el mob y **qué le pasó** (la mutación). De ahí salen:
- Una o dos ideas fuertes de **silueta** que rompan la forma vanilla (patas de araña, brazo gigante,
  postura encorvada, algo que le brota del cuerpo). La silueta es lo que el jugador reconoce de lejos.
- **Restos de su vida anterior** (ropa rota, casco, herramienta clavada) que cuentan la historia.
- La **cara**: ojos rojos o muertos en cuencas negras y una boca con dientes.

### 3. Escribir el diseño
Crea `tools/skins/mobs/<clave>.py`. Copia la estructura del ejemplo más parecido:
- **Humanoide con accesorios:** `minero_zombie.py`, `granjero_zombie.py` o `zombie_astronauta.py`, que usan
  `humanoide()` y `HUMANOIDE`. Los zombies del mod comparten ayudantes en `tools/skins/mobs/_zombies.py`
  (`manga`, `pantalon`, `botas`, `jirones`, `aro_hueco`).
- **Esqueleto propio con articulaciones:** `creeper_gigante.py` o `zombie_gigante.py`, que encadenan
  hombro → codo y cadera → rodilla con `en()`.

El módulo define `MOB` (con `clave`, `grupo` para el menú del visor, `nombre`, `escala`, `seed`), `PIEZAS`, `partes()` y `pintar(t, uv, pz)`. La documentación de cada función está en
`scripts/motor.py`. Ahí están las coordenadas, las rampas de color y las herramientas de pintura (`pintar`,
`dibujo`, `mancha`, `sombra`, `costillas`, `columna`, `garras`, `hueso`, `cristal`). Usa las rampas que ya existen
antes de inventar colores. Si necesitas un material nuevo, agrega su rampa al motor siguiendo la regla de la guía
de estilo.

Cada pieza de 2 px o más lleva una **capa exterior** con relieve (como el sombrero y la chaqueta del jugador):
una caja inflada con su propia textura. Se genera sola desde la pintura. El hueso, el cristal, la ropa y los grumos
de la piel sobresalen; las cuencas, las bocas y las heridas (`NEGRO`, `OJO`, `CARNE`) quedan hundidas. Si un mob
la necesita distinta, define `relieve(t, uv, pz)` y retócala con `t.sobresale(...)` o `t.hundir(...)`.

### 4. Generar y autocriticar
```bash
python .claude/skills/skins-mobs/scripts/generar.py <clave>
```
Genera `runtime/skins/<clave>/` con `<clave>.png`, `<clave>_exterior.png` (la capa exterior), `preview.png` y
`modelo.json`, además de `runtime/skins/visor.html` con todos los mobs (con un interruptor para la capa exterior). `runtime/` está en `.gitignore`, así que solo se versiona el diseño fuente.
Para revisar varios de un vistazo: `python .claude/skills/skins-mobs/scripts/hoja.py <salida.png> <clave> ...`
(frente y espalda de cada uno). Si una lámina va a revisión del equipo, cópiala a `docs/previews/<fecha>/`.

**Mira `preview.png` con Read antes de mostrarla.** Revísala contra el checklist de la guía de estilo y corrige
lo que falle: piezas flotando o atravesadas, silueta confusa, cara que no se lee o demasiados colores. Itera
hasta que pase. Esto evita mostrarle al usuario cosas a medias.

### 5. Mostrar y pedir aprobación
Envía la lámina con SendUserFile y abre `runtime/skins/visor.html` en el navegador de la app para que el usuario
pueda girar el modelo. El visor tiene la opción "Tamaño real", que muestra la escala junto a un jugador. Explica
el concepto en pocas líneas y pregunta si lo ajustas o lo pasas al mod. **No toques el mod sin aprobación
explícita**: un cambio de modelo afecta al cliente de todos los jugadores.

### 6. Integrar al mod (solo con aprobación)
Sigue `references/integracion-mod.md`. En resumen: copiar el PNG a los assets del mod, generar el
`createBodyLayer()` con `scripts/exportar_java.py`, registrar la capa del modelo y el renderer, ajustar la hitbox
y probarlo en `test-client`.

## Límites que conviene contarle al usuario cuando apliquen
- Minecraft solo dibuja **cubos**. Las curvas se sugieren girando piezas y con la textura.
- **16 px = 1 bloque.** Se puede subir la resolución, pero rompe el estilo vanilla. Mantenla salvo que lo pidan.
- El arte se genera con código. Para retoques finos, Blockbench sigue siendo la herramienta estándar.
- Animaciones (caminar, atacar) y partes que brillan requieren código o librerías extra. El visor muestra el
  modelo en pose fija.
