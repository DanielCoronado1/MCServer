"""Generates high-definition aerial showcase render of the Dolfin Space Station deck."""

from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


def render_aerial_showcase(slices_path: Path, output_path: Path) -> Path:
    data = json.loads(slices_path.read_text(encoding="utf-8"))
    showcase = data.get("dolfin:showcase") or data.get("dolfin:lobby")
    if not showcase:
        raise ValueError("No showcase or lobby data found in slices")

    # Dimensions: 97 cols (X: -48..48), 65 rows (Z: -32..32)
    cols = 97
    rows = 65
    block_size = 18

    cw = 2400
    ch = 1600
    img = Image.new("RGBA", (cw, ch), (7, 11, 20, 255))
    draw = ImageDraw.Draw(img)

    # 1. Starfield background with subtle space nebula
    rng = random.Random(42)
    # Background gradient
    for y in range(ch):
        factor = y / ch
        r = int(5 + factor * 8)
        g = int(8 + factor * 12)
        b = int(16 + factor * 22)
        draw.line([(0, y), (cw, y)], fill=(r, g, b, 255))

    # Nebula soft clouds
    nebula = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    ndraw = ImageDraw.Draw(nebula)
    for _ in range(18):
        cx = rng.randint(200, cw - 200)
        cy = rng.randint(200, ch - 200)
        rad = rng.randint(180, 420)
        col = rng.choice([
            (25, 60, 110, 28),
            (20, 90, 105, 24),
            (60, 35, 95, 22),
            (15, 115, 130, 20),
        ])
        ndraw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=col)
    nebula = nebula.filter(ImageFilter.GaussianBlur(80))
    img = Image.alpha_composite(img, nebula)
    draw = ImageDraw.Draw(img)

    # Stars
    for _ in range(650):
        sx = rng.randint(0, cw - 1)
        sy = rng.randint(0, ch - 1)
        bright = rng.randint(120, 255)
        sc = rng.choice([
            (bright, bright, bright),
            (bright - 20, bright, 255),
            (255, bright, bright - 30),
            (180, 240, 255),
        ])
        sz = 1 if rng.random() > 0.15 else 2
        draw.rectangle([sx, sy, sx + sz - 1, sy + sz - 1], fill=(*sc, rng.randint(140, 255)))

    # Station coordinates offset
    station_w = cols * block_size
    station_h = rows * block_size
    ox = (cw - station_w) // 2
    oy = (ch - station_h) // 2 + 25

    # 2. Emissive bloom layer for sea lanterns
    lantern_positions = []
    layer_128 = showcase.get("128", [])
    layer_129 = showcase.get("129", [])
    layer_130 = showcase.get("130", [])
    layer_134 = showcase.get("134", [])
    layer_135 = showcase.get("135", [])

    for r_idx in range(min(rows, len(layer_128))):
        row = layer_128[r_idx]
        for c_idx in range(min(cols, len(row))):
            b = row[c_idx]
            if "sea_lantern" in b:
                lantern_positions.append((ox + c_idx * block_size + block_size // 2,
                                         oy + r_idx * block_size + block_size // 2))

    for r_idx in range(min(rows, len(layer_134))):
        row = layer_134[r_idx]
        for c_idx in range(min(cols, len(row))):
            b = row[c_idx]
            if "sea_lantern" in b:
                lantern_positions.append((ox + c_idx * block_size + block_size // 2,
                                         oy + r_idx * block_size + block_size // 2))

    # Glow layer
    glow_img = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_img)
    for lx, ly in lantern_positions:
        # Outer soft halo
        gdraw.ellipse([lx - 38, ly - 38, lx + 38, ly + 38], fill=(60, 220, 230, 45))
        # Mid halo
        gdraw.ellipse([lx - 22, ly - 22, lx + 22, ly + 22], fill=(120, 245, 255, 75))
        # Core
        gdraw.ellipse([lx - 10, ly - 10, lx + 10, ly + 10], fill=(210, 255, 255, 140))
    glow_img = glow_img.filter(ImageFilter.GaussianBlur(14))

    # 3. Outer platform drop shadow onto void
    shadow_img = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow_img)
    sdraw.rectangle([ox - 14, oy - 14, ox + station_w + 14, oy + station_h + 14], fill=(0, 0, 0, 160))
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(24))
    img = Image.alpha_composite(img, shadow_img)
    draw = ImageDraw.Draw(img)

    # 4. Render Base Deck (Layer 128)
    color_map = {
        "smooth_quartz": ((232, 238, 242), (210, 218, 224)),
        "quartz_bricks": ((195, 208, 215), (170, 185, 192)),
        "quartz_pillar": ((238, 244, 248), (218, 226, 232)),
        "polished_deepslate": ((45, 54, 65), (32, 39, 48)),
        "cyan_concrete": ((25, 150, 165), (18, 120, 132)),
        "lime_concrete": ((125, 196, 50), (100, 165, 38)),
        "orange_concrete": ((222, 125, 35), (185, 100, 24)),
        "yellow_concrete": ((230, 185, 60), (195, 152, 42)),
        "sea_lantern": ((190, 255, 245), (140, 235, 225)),
        "bloque_plataforma": ((60, 75, 90), (45, 58, 70)),
        "diamond_block": ((75, 240, 245), (45, 210, 220)),
        "stone_button": ((160, 172, 182), (130, 142, 152)),
    }

    # Station outer frame backdrop
    draw.rectangle([ox - 4, oy - 4, ox + station_w + 4, oy + station_h + 4], fill=(22, 28, 38, 255), outline=(50, 65, 82, 255), width=2)

    for r_idx in range(min(rows, len(layer_128))):
        row = layer_128[r_idx]
        for c_idx in range(min(cols, len(row))):
            block_raw = row[c_idx]
            block_name = block_raw.split(":")[-1]
            bx = ox + c_idx * block_size
            by = oy + r_idx * block_size

            if block_name == "air":
                # Dark space station grid / open floor
                draw.rectangle([bx, by, bx + block_size, by + block_size], fill=(12, 18, 28, 255))
                continue

            # Look up colors
            cm = color_map.get(block_name, ((150, 150, 160), (120, 120, 130)))
            fill_c, border_c = cm

            # Block body
            draw.rectangle([bx, by, bx + block_size, by + block_size], fill=(*fill_c, 255))
            # Subtle block grid seam
            draw.rectangle([bx, by, bx + block_size, by + block_size], outline=(*border_c, 200), width=1)

            # Special block detailing
            if block_name == "sea_lantern":
                # Bright lantern center
                draw.rectangle([bx + 3, by + 3, bx + block_size - 4, by + block_size - 4], fill=(240, 255, 255, 255))
            elif block_name == "quartz_bricks":
                # Mini brick seams
                draw.line([(bx, by + block_size // 2), (bx + block_size, by + block_size // 2)], fill=(*border_c, 160))
                draw.line([(bx + block_size // 2, by), (bx + block_size // 2, by + block_size // 2)], fill=(*border_c, 160))
            elif block_name == "polished_deepslate":
                # Dark beveled borders
                draw.rectangle([bx + 1, by + 1, bx + block_size - 2, by + block_size - 2], outline=(60, 72, 88, 255))

    # 5. Render Walls, Glass & Observation Perimeter (Layers 129..134)
    glass_layer = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    gldraw = ImageDraw.Draw(glass_layer)

    for r_idx in range(min(rows, len(layer_130))):
        row = layer_130[r_idx]
        for c_idx in range(min(cols, len(row))):
            block_name = row[c_idx].split(":")[-1]
            bx = ox + c_idx * block_size
            by = oy + r_idx * block_size
            if block_name == "glass":
                # Semi-transparent cyan observation window
                gldraw.rectangle([bx, by, bx + block_size, by + block_size],
                                 fill=(85, 175, 215, 120), outline=(130, 215, 245, 190), width=1)
                gldraw.line([(bx + 2, by + 2), (bx + block_size - 3, by + block_size - 3)],
                            fill=(210, 245, 255, 90), width=1)
            elif block_name == "quartz_pillar":
                # Solid white pillar top
                draw.rectangle([bx, by, bx + block_size, by + block_size], fill=(245, 250, 252, 255), outline=(180, 195, 205, 255))
                draw.ellipse([bx + 2, by + 2, bx + block_size - 3, by + block_size - 3], outline=(205, 218, 226, 255))

    img = Image.alpha_composite(img, glass_layer)
    draw = ImageDraw.Draw(img)

    # 6. Roof Arches & Crossbeams (Layer 135) with drop shadow
    roof_shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    rsdraw = ImageDraw.Draw(roof_shadow)
    roof_layer = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    rldraw = ImageDraw.Draw(roof_layer)

    for r_idx in range(min(rows, len(layer_135))):
        row = layer_135[r_idx]
        for c_idx in range(min(cols, len(row))):
            block_name = row[c_idx].split(":")[-1]
            if block_name != "air":
                bx = ox + c_idx * block_size
                by = oy + r_idx * block_size
                # Drop shadow offset (+5, +7)
                rsdraw.rectangle([bx + 5, by + 7, bx + block_size + 5, by + block_size + 7], fill=(0, 0, 0, 95))
                # Structural arch
                rldraw.rectangle([bx, by, bx + block_size, by + block_size],
                                 fill=(235, 242, 248, 245), outline=(175, 190, 200, 255), width=1)

    roof_shadow = roof_shadow.filter(ImageFilter.GaussianBlur(6))
    img = Image.alpha_composite(img, roof_shadow)
    img = Image.alpha_composite(img, roof_layer)
    draw = ImageDraw.Draw(img)

    # 7. Blend Sea Lantern Emissive Glow on top of deck
    img = Image.alpha_composite(img, glow_img)
    draw = ImageDraw.Draw(img)

    # 8. Coordinate Rulers & Framing
    ruler_color = (100, 135, 165, 220)
    grid_color = (40, 60, 80, 80)

    # X axis ruler marks
    for x_val in range(-48, 49, 16):
        c_idx = x_val + 48
        rx = ox + c_idx * block_size
        draw.line([(rx, oy - 18), (rx, oy - 6)], fill=ruler_color, width=2)
        draw.line([(rx, oy + station_h + 6), (rx, oy + station_h + 18)], fill=ruler_color, width=2)
        draw.line([(rx, oy), (rx, oy + station_h)], fill=grid_color, width=1)
        draw.text((rx - 12, oy - 32), f"X:{x_val:+d}", fill=(160, 200, 230, 255))
        draw.text((rx - 12, oy + station_h + 20), f"X:{x_val:+d}", fill=(160, 200, 230, 255))

    # Z axis ruler marks
    for z_val in range(-32, 33, 16):
        r_idx = z_val + 32
        ry = oy + r_idx * block_size
        draw.line([(ox - 18, ry), (ox - 6, ry)], fill=ruler_color, width=2)
        draw.line([(ox + station_w + 6, ry), (ox + station_w + 18, ry)], fill=ruler_color, width=2)
        draw.line([(ox, ry), (ox + station_w, ry)], fill=grid_color, width=1)
        draw.text((ox - 56, ry - 7), f"Z:{z_val:+d}", fill=(160, 200, 230, 255))
        draw.text((ox + station_w + 22, ry - 7), f"Z:{z_val:+d}", fill=(160, 200, 230, 255))

    # 9. Tactical Sector Callout Badges & Annotations
    def draw_badge(bx: int, by: int, title: str, subtitle: str, color_accent: tuple[int, int, int]):
        pad_x = 16
        bw = 290
        bh = 58
        # Card background
        draw.rectangle([bx, by, bx + bw, by + bh], fill=(14, 22, 34, 240), outline=(*color_accent, 220), width=2)
        # Left accent stripe
        draw.rectangle([bx, by, bx + 6, by + bh], fill=(*color_accent, 255))
        # Text
        draw.text((bx + 14, by + 10), title, fill=(255, 255, 255, 255))
        draw.text((bx + 14, by + 30), subtitle, fill=(170, 195, 220, 255))

    # Badges
    # Lime wing (Creeper exhibits)
    draw_badge(ox + 40, oy + 40, "ALA DE CREEPERS", "9 Kioscos Interactivos", (125, 196, 50))
    # Connector line
    draw.line([(ox + 185, oy + 98), (ox + 220, oy + 180)], fill=(125, 196, 50, 200), width=2)
    draw.ellipse([ox + 217, oy + 177, ox + 223, oy + 183], fill=(125, 196, 50, 255))

    # Orange wing (Zombie exhibits & arenas)
    draw_badge(ox + 490, oy + 40, "ALA DE ZOMBIES", "16 Kioscos y Arenas PVE", (222, 125, 35))
    draw.line([(ox + 635, oy + 98), (ox + 650, oy + 180)], fill=(222, 125, 35, 200), width=2)
    draw.ellipse([ox + 647, oy + 177, ox + 653, oy + 183], fill=(222, 125, 35, 255))

    # Cyan wing (Space launchpad & Kepler shuttle)
    draw_badge(ox + 1380, oy + 40, "PLATAFORMA ESPACIAL", "Lanzadera a Kepler-452b", (25, 180, 200))
    draw.line([(ox + 1525, oy + 98), (ox + 1480, oy + 240)], fill=(25, 180, 200, 200), width=2)
    draw.ellipse([ox + 1477, oy + 237, ox + 1483, oy + 243], fill=(25, 180, 200, 255))

    # Central Departure Portals
    draw_badge(ox + 720, oy + station_h - 90, "TERMINAL DE VIAJE", "Supervivencia | Creativo", (0, 240, 220))
    draw.line([(ox + 865, oy + station_h - 90), (ox + 865, oy + station_h - 130)], fill=(0, 240, 220, 200), width=2)

    # 10. Header Title HUD
    # Header bar
    draw.rectangle([40, 25, cw - 40, 95], fill=(10, 16, 26, 235), outline=(35, 60, 85, 255), width=2)
    draw.rectangle([40, 25, 48, 95], fill=(0, 215, 245, 255))

    # Title
    draw.text((65, 36), "ESTACION ESPACIAL DOLFIN -- VISTA AEREA CENITAL (LOBBY & SHOWCASE)", fill=(255, 255, 255, 255))
    draw.text((65, 62), "Dimension: dolfin:showcase / dolfin:lobby | Nivel Y=128..136 | Resolucion de Bloque: 18px/bloque", fill=(130, 185, 220, 255))

    # Server badge on right of header
    draw.rectangle([cw - 420, 38, cw - 60, 82], fill=(18, 30, 48, 255), outline=(0, 215, 245, 180))
    draw.ellipse([cw - 405, 54, cw - 395, 64], fill=(45, 230, 100, 255))
    draw.text((cw - 385, 45), "SSH WIN-HOME LIVE", fill=(255, 255, 255, 255))
    draw.text((cw - 385, 63), "CRAFTY PORT 25565: ONLINE", fill=(100, 220, 140, 255))

    # 11. Footer Telemetry Box (Lower Left)
    draw.rectangle([40, ch - 120, 520, ch - 30], fill=(10, 16, 26, 235), outline=(35, 60, 85, 255), width=2)
    draw.rectangle([40, ch - 120, 46, ch - 30], fill=(0, 180, 210, 255))
    draw.text((58, ch - 110), "TELEMETRIA DE SERVIDOR VERIFICADA", fill=(0, 215, 245, 255))
    draw.text((58, ch - 90), "Dimensiones: lobby, showcase, dungeons, kepler", fill=(180, 205, 225, 255))
    draw.text((58, ch - 72), "Entidades: 35 Text Displays de senalizacion activos", fill=(180, 205, 225, 255))
    draw.text((58, ch - 54), "Area Cubierta: 97x65 bloques (6,305 m2 utiles)", fill=(180, 205, 225, 255))

    # 12. Compass / Downlink HUD (Lower Right)
    draw.rectangle([cw - 360, ch - 120, cw - 40, ch - 30], fill=(10, 16, 26, 235), outline=(35, 60, 85, 255), width=2)
    draw.text((cw - 340, ch - 110), "CAMARA ORBITAL #01", fill=(0, 215, 245, 255))
    draw.text((cw - 340, ch - 90), "Inclinacion: -90 deg (Nadir Cenital)", fill=(180, 205, 225, 255))
    draw.text((cw - 340, ch - 72), "Altitud Orbital: Y=265m", fill=(180, 205, 225, 255))
    draw.text((cw - 340, ch - 54), "Optica: Ortografica Sin Distorsion", fill=(180, 205, 225, 255))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path, "PNG")
    print(f"Rendered: {output_path} ({cw}x{ch})")
    return output_path


if __name__ == "__main__":
    import sys
    slices = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("runtime/stage-integrated/station-block-slices.json")
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("docs/assets/showcase_lobby_aereo.png")
    render_aerial_showcase(slices, out)
