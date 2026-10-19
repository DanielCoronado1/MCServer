# DolfinSpace asset credits

Original DolfinServer Space artwork and sound assets contributed for #25–#32 are dedicated to **CC0 1.0 Universal** (https://creativecommons.org/publicdomain/zero/1.0/). This dedication applies to the assets listed here, not the Java code or the Minecraft game.

- Original multipart Scout/BASICA, Cargo/CARGA and Interceptor/RAPIDA geometry: `ShipDesign.java` and `SpaceshipRenderer.java`; no external meshes, Blockbench libraries or GeckoLib dependencies.
- Ship atlases, engine flame, ship inventory/held icons, industrial blocks and items, preview sheet: `tools/space/generate_assets.py`.
- Kepler block/item textures: `tools/space/generate_assets.py` and `tools/space/resources.py`.
- Mob skin textures and spawn egg icons: `tools/space/generate_assets.py`; original pixel art mapped onto Minecraft's built-in renderer geometry. Minecraft geometry and engine remain Mojang/Microsoft property; they are referenced, not redistributed as extracted assets.
- Foundation regolito and Earth sky illustration: original procedural assets from #25.
- `cockpit_hum.ogg`, `check_beep.ogg`, `fuel_alarm.ogg`, `engine.ogg`, `parasite.ogg`: original mathematical waveform synthesis in `tools/space/generate_assets.py`, encoded as Ogg Vorbis using ffmpeg. No sampled recordings or external audio.
- Preview typography is rasterized using the host's Arial font; no font file is redistributed.
- Standard Minecraft model parents, particles and shader/render functions are referenced by identifier under the game's existing assets. No missing custom resource requires a downloaded texture pack.

Pillow and ffmpeg are build-time tools only and are not bundled with the mod.
