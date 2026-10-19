# #8 — Presentación de los 16 zombies

## Recursos reproducibles

`uv run tools/generate_zombie_assets.py` instala dependencias Python declaradas (Pillow 12.1.0, soundfile 0.13.1), crea arte y audio originales sin descargas de contenido. Produce skins RGBA 64×64 `textures/entity/<id>_zombie.png`, atlas independientes para accesorios, capas emisivas para Minero, Bombero, Ahogado, Enano, Contagiado y Explorador, y overlay facial del Mimo. No redistribuye texturas ni sonidos de Minecraft.

Los 16 huevos tienen sprites 16×16 y modelos propios, con dos colores distintos por variante. Minecraft 26.3 ya no incluye `template_spawn_egg`; se usa `minecraft:item/generated` con sprites coloreados. Se conservan nombres de entidad/huevo en en_us, es_mx, es_es y es_ar y se agregan subtítulos de los cuatro sonidos.

`issue-08-skins.png` muestra únicamente UV frontales ampliadas; no representa una captura del juego ni los accesorios 3D.

## Render

- `KindZombieRenderer` selecciona la textura por tipo; `ZombieAttachments` añade geometría animada con atlas propios: casco/linterna/pico del Minero, capucha/costal, relieve carbonizado, algas/percebes, yelmo/escudo con calavera verde, mandíbula del Hambriento, mandíbula/ojos saltones del Gritón, sombrero con hongo/frascos, sombrero de paja/horca, carámbanos/pieles, sombrero de vaquero/rollo de silla, vientre del Bilioso y casco/vela/barba/pico del Enano.
- Hambriento se estrecha y Bilioso se ensancha en render. Enano conserva escala 0.5 del snapshot del servidor, sin aplicar el tamaño dos veces. El caballo/araña y su silla funcional pertenecen a #5.
- Ojos, grietas y venas se dibujan en paso emisivo a luz completa; la linterna y vela tienen su propio paso emisivo en la geometría del accesorio. Venas pulsan. El Explorador no dibuja cuerpo oculto, aunque el observador tenga visión nocturna; siempre dibuja ojos rojos. Revelado usa transparencia 60%. No se consulta ningún efecto del observador.
- `PlayerLikeZombieRenderer` usa `PlayerModel` normal/slim, capas externas, objetos en mano y armadura equipada. Obtiene la skin del perfil sincronizado con la caché vanilla; conserva un máximo de 128 proveedores por renderer. Contagiado recibe tinte verde y venas pulsantes. Mimo añade ojos negros y sonrisa sobre la skin copiada. Sin perfil (huevo/summon), ambos usan sus skins propias.
- Humo, bilis y vaho son partículas cosméticas de cliente cada 10 ticks, sin búsquedas en el mundo.

## Contrato con #5 / #7

Este checkout aún construye Ahogado, Ladrón, Mimo y Contagiado como `ConfiguredZombie`; no contiene sus mecánicas. Se expone una API explícita para integrar sus puntos de éxito sin inferir robos desde daño:

```java
// Al crear un Mimo o convertir un Contagiado a partir de un jugador:
zombie.copyPlayerAppearance(player);
// Después de aceptar el agarre / después de transferir efectivamente el objeto:
zombie.playGrabSound();
zombie.playStealSound();
```

`copyPlayerAppearance` copia perfil con propiedades y armadura, sincroniza por entity data y guarda en `DolfinPlayerAppearance` mediante el codec vanilla. Los objetos copiados permanecen en slots vanilla; su persistencia/sincronización ya corresponde al juego. No añade encantamientos, transacciones de inventario, muertes ni conversiones de #7. Los hooks de sonido comprueban tipo e interruptor de habilidad. **La llamada automática desde las mecánicas de #5/#7 queda pendiente de integración cuando esas clases estén disponibles.**

## Sonidos y observabilidad

`ZombieSounds` registra `griton_scream`, `bilioso_burst`, `abismo_grab`, `ladron_steal`; `sounds.json` enlaza cuatro OGG Vorbis mono 22050 Hz de 0.45–1.25 s y subtítulos en los cuatro idiomas. Audio sintetizado con semilla fija; el generador normaliza el serial Ogg y recalcula CRC para evitar diferencias aleatorias. Gritón lo reproduce en su llamada; Bilioso lo usa al morir cuando `nube` está activa. Los otros dos disponen de los hooks anteriores para sus habilidades.

Logs: INFO `event=zombie_sounds_registered`; DEBUG `event=zombie_sound`, `zombie_appearance_copied`, `zombie_appearance_loaded`, `zombie_appearance_saved`, y los eventos de #6. La ausencia de perfil usa un fallback esperado; la caché vanilla resuelve skins sin acceso asíncrono al mundo.

## Validación y límites

- `JAVA_HOME=/Users/emi0x7d1/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home ./gradlew build --no-daemon` desde `mods/zombies`: BUILD SUCCESSFUL, 38 tests; compilación principal y cliente.
- `ZombieAssetsTest`: las 16 skins son distintas y 64×64; las 4 traducciones incluyen entidades, huevos y subtítulos; todos los modelos de huevo resuelven su sprite; seis capas emisivas transparentes; cuatro OGG mono con CRC de cada página y cabecera Vorbis válidos.
- Regeneración: SHA-256 idéntico para los 98 archivos de assets en dos ejecuciones consecutivas. `ffprobe` verifica mono/Vorbis/22050 Hz y `ffmpeg` decodifica los cuatro archivos sin errores. Inspección visual de la lámina UV.
- Seguimiento #6: caché de parámetros por entidad, selección del jugador visible más cercano entre candidatos, y expiración exacta de daño a 60 ticks; sólo luz/sombra continúan muestreándose cada 20 ticks.
- Sin cliente ni servidor lanzados, conforme a convenciones. Pendientes: reconocimiento a 20 bloques, clipping de accesorios/armaduras, brillo/transparencia con shaders, sonido subjetivo/volumen, skin autenticada de jugador real, monturas y llamada de hooks desde #5/#7. Compilar y validar archivos no demuestra esa aceptación visual ni MSPT.
