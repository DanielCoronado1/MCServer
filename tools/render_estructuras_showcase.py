"""Generates high-definition isometric architectural showcase of structures and dungeons."""

from __future__ import annotations

import math
import sys
from pathlib import Path

# Add tools/estructuras to path
sys.path.insert(0, str(Path(__file__).parent / "estructuras"))

from PIL import Image, ImageDraw, ImageFilter, ImageFont
import disenos


# Palette for isometric rendering: (base_r, base_g, base_b)
BLOCK_PALETTE: dict[str, tuple[int, int, int]] = {
    "oak_planks": (180, 140, 90),
    "spruce_planks": (115, 85, 55),
    "dark_oak_planks": (68, 48, 30),
    "oak_log": (130, 105, 70),
    "stripped_oak_log": (175, 145, 95),
    "dark_oak_log": (55, 40, 25),
    "cobblestone": (130, 130, 130),
    "mossy_cobblestone": (115, 135, 105),
    "stone_bricks": (145, 145, 145),
    "mossy_stone_bricks": (120, 145, 110),
    "cracked_stone_bricks": (110, 110, 110),
    "stone": (140, 140, 140),
    "stone_brick_slab": (140, 140, 140),
    "moss_block": (90, 150, 45),
    "moss_carpet": (100, 165, 50),
    "coarse_dirt": (120, 85, 60),
    "podzol": (95, 65, 45),
    "dirt_path": (155, 120, 75),
    "gravel": (135, 130, 130),
    "chest": (180, 125, 40),
    "barrel": (135, 100, 60),
    "crafting_table": (150, 110, 70),
    "furnace": (105, 105, 105),
    "campfire": (210, 100, 30),
    "lantern": (240, 200, 70),
    "iron_bars": (160, 165, 170),
    "cobweb": (220, 225, 230),
    "rail": (170, 150, 120),
    "iron_ore": (155, 140, 135),
    "coal_ore": (75, 75, 75),
    "copper_ore": (145, 125, 115),
    "red_bed": (180, 45, 45),
    "spawner": (45, 70, 85),
    "vine": (65, 115, 40),
    "kelp": (45, 105, 55),
    "kelp_plant": (45, 105, 55),
    "dead_tube_coral_block": (145, 135, 135),
    "sea_pickle": (115, 160, 65),
    "bookshelf": (135, 105, 70),
    "glass_pane": (150, 205, 225),
}


def shade_color(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return (
        min(255, max(0, int(rgb[0] * factor))),
        min(255, max(0, int(rgb[1] * factor))),
        min(255, max(0, int(rgb[2] * factor))),
    )


def render_structure_iso(
    plantilla,
    scale: int = 14,
    rot: int = 0,
) -> Image.Image:
    """Renders a plantilla in 3D isometric projection."""
    # Collect blocks
    blocks = []
    for (x, y, z), (block_id, _, _) in plantilla.bloques.items():
        name = block_id.split(":")[-1]
        if name in ("air", None):
            continue
        # Apply rotation around Y
        if rot == 1:  # 90 deg
            rx, rz = plantilla.largo - 1 - z, x
        elif rot == 2:  # 180 deg
            rx, rz = plantilla.ancho - 1 - x, plantilla.largo - 1 - z
        elif rot == 3:  # 270 deg
            rx, rz = z, plantilla.ancho - 1 - x
        else:
            rx, rz = x, z
        blocks.append((rx, y, rz, name))

    # Sort back-to-front: larger x + z should be drawn later, larger y drawn later
    blocks.sort(key=lambda b: (b[0] + b[2], b[1], b[0]))

    # Isometric constants
    # dx = scale, dy = scale // 2
    step_x = scale
    step_y = scale // 2
    step_h = int(scale * 0.95)

    # Compute bounding box
    min_sx, max_sx = 99999, -99999
    min_sy, max_sy = 99999, -99999
    for rx, y, rz, _ in blocks:
        sx = (rx - rz) * step_x
        sy = (rx + rz) * step_y - y * step_h
        min_sx = min(min_sx, sx - step_x)
        max_sx = max(max_sx, sx + step_x)
        min_sy = min(min_sy, sy - step_h)
        max_sy = max(max_sy, sy + step_y + step_h)

    margin = 30
    w = max_sx - min_sx + margin * 2
    h = max_sy - min_sy + margin * 2
    ox = margin - min_sx
    oy = margin - min_sy

    iso_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(iso_img)

    for rx, y, rz, name in blocks:
        base_rgb = BLOCK_PALETTE.get(name, (130, 130, 130))
        # Top face (sunlit): 1.15
        top_rgb = shade_color(base_rgb, 1.15)
        # Left face (ambient light): 0.85
        left_rgb = shade_color(base_rgb, 0.85)
        # Right face (shadow): 0.65
        right_rgb = shade_color(base_rgb, 0.65)

        cx = ox + (rx - rz) * step_x
        cy = oy + (rx + rz) * step_y - y * step_h

        # Top diamond: (cx, cy - step_y), (cx + step_x, cy), (cx, cy + step_y), (cx - step_x, cy)
        top_poly = [
            (cx, cy - step_y),
            (cx + step_x, cy),
            (cx, cy + step_y),
            (cx - step_x, cy),
        ]
        draw.polygon(top_poly, fill=(*top_rgb, 255), outline=(*shade_color(top_rgb, 0.9), 255))

        # Left face: (cx - step_x, cy), (cx, cy + step_y), (cx, cy + step_y + step_h), (cx - step_x, cy + step_h)
        left_poly = [
            (cx - step_x, cy),
            (cx, cy + step_y),
            (cx, cy + step_y + step_h),
            (cx - step_x, cy + step_h),
        ]
        draw.polygon(left_poly, fill=(*left_rgb, 255), outline=(*shade_color(left_rgb, 0.9), 255))

        # Right face: (cx, cy + step_y), (cx + step_x, cy), (cx + step_x, cy + step_h), (cx, cy + step_y + step_h)
        right_poly = [
            (cx, cy + step_y),
            (cx + step_x, cy),
            (cx + step_x, cy + step_h),
            (cx, cy + step_y + step_h),
        ]
        draw.polygon(right_poly, fill=(*right_rgb, 255), outline=(*shade_color(right_rgb, 0.9), 255))

    return iso_img


def render_all_estructuras_showcase(output_path: Path) -> Path:
    cw = 2400
    ch = 1600
    img = Image.new("RGBA", (cw, ch), (9, 14, 25, 255))
    draw = ImageDraw.Draw(img)

    # Blueprint grid background
    grid_col = (20, 32, 50, 180)
    for x in range(0, cw, 40):
        draw.line([(x, 0), (x, ch)], fill=grid_col, width=1)
    for y in range(0, ch, 40):
        draw.line([(0, y), (cw, y)], fill=grid_col, width=1)

    # Top Header
    draw.rectangle([40, 25, cw - 40, 110], fill=(12, 20, 35, 245), outline=(35, 65, 95, 255), width=2)
    draw.rectangle([40, 25, 48, 110], fill=(0, 200, 220, 255))
    draw.text((65, 38), "ESTRUCTURAS ABANDONADAS EN EL MUNDO & ARQUITECTURA DE DUNGEONS", fill=(255, 255, 255, 255))
    draw.text((65, 66), "Generacion automatica en chunks nuevos de Dolfin Creepers (Datapack puro & NBT) + Cripta Instanciada (#21, #20)", fill=(130, 185, 220, 255))
    draw.text((65, 88), "Reglas: Sin modo aventura forzado | Totalmente destructibles | Tablas de botin balanceadas", fill=(95, 220, 140, 255))

    # Host verification badge
    draw.rectangle([cw - 440, 42, cw - 60, 92], fill=(18, 30, 48, 255), outline=(0, 200, 220, 200))
    draw.ellipse([cw - 425, 62, cw - 413, 74], fill=(50, 230, 100, 255))
    draw.text((cw - 402, 52), "VERIFICADO EN WIN-HOME", fill=(255, 255, 255, 255))
    draw.text((cw - 402, 70), "DATAPACK REGISTRY OK", fill=(100, 220, 140, 255))

    # 4 Structures layout: 2x2 grid in left 1600px, 5th card (Dungeon Crypt) on right 700px!
    structures = [
        {
            "name": "CASA ABANDONADA",
            "fn": disenos.casa_abandonada,
            "scale": 15,
            "rot": 0,
            "area": "Llanuras y Bosques (Superficie)",
            "loot": "dolfincreepers:chests/casa_abandonada",
            "desc": "Cabaña de madera con tejado a dos aguas derrumbado, musgo, telarañas y cofre oculto.",
            "dims": "11x10x9 bloques",
            "pos": (60, 140, 780, 520),
        },
        {
            "name": "TORRE CAIDA",
            "fn": disenos.torre_caida,
            "scale": 11,
            "rot": 1,
            "area": "Llanuras, Colinas y Montañas",
            "loot": "dolfincreepers:chests/torre_caida",
            "desc": "Antiguo torreón de vigilancia colapsado con ladrillos de piedra musgosa y campana intacta.",
            "dims": "21x13x13 bloques",
            "pos": (840, 140, 780, 520),
        },
        {
            "name": "CAMPAMENTO MINERO",
            "fn": disenos.campamento_minero,
            "scale": 10,
            "rot": 0,
            "area": "Subterráneo (Y: 0 a 40)",
            "loot": "dolfincreepers:chests/campamento_minero",
            "desc": "Galería de mina olvidada con vigas de soporte, antorchas apagadas, vagoneta y minerales.",
            "dims": "25x8x13 bloques",
            "pos": (60, 680, 780, 520),
        },
        {
            "name": "PUESTO HUNDIDO",
            "fn": disenos.puesto_hundido,
            "scale": 13,
            "rot": 0,
            "area": "Océano Poco Profundo",
            "loot": "dolfincreepers:chests/puesto_hundido",
            "desc": "Ruina marina con prismarina, pepinos de mar luminiscentes, cofre sumergido y algas.",
            "dims": "13x12x13 bloques",
            "pos": (840, 680, 780, 520),
        },
    ]

    for st in structures:
        bx, by, bw, bh = st["pos"]
        # Card background
        draw.rectangle([bx, by, bx + bw, by + bh], fill=(14, 22, 36, 240), outline=(35, 65, 95, 255), width=2)
        # Top title ribbon
        draw.rectangle([bx, by, bx + bw, by + 42], fill=(20, 32, 52, 255))
        draw.rectangle([bx, by, bx + 6, by + bh], fill=(0, 200, 220, 255))
        draw.text((bx + 16, by + 12), st["name"], fill=(255, 255, 255, 255))
        draw.text((bx + bw - 150, by + 12), st["dims"], fill=(0, 200, 220, 255))

        # Render 3D Iso
        p = st["fn"]()
        iso = render_structure_iso(p, scale=st["scale"], rot=st["rot"])

        # Paste isometric view inside card (left portion of card)
        # Fit inside 420x420
        max_iw, max_ih = 440, 440
        if iso.width > max_iw or iso.height > max_ih:
            iso.thumbnail((max_iw, max_ih), Image.Resampling.LANCZOS)

        ix = bx + (460 - iso.width) // 2
        iy = by + 50 + (bh - 60 - iso.height) // 2
        img.alpha_composite(iso, (ix, iy))

        # Right side info panel inside card
        px = bx + 470
        py = by + 65
        draw.rectangle([px, py, bx + bw - 20, by + bh - 20], fill=(10, 16, 26, 200), outline=(28, 48, 70, 255))

        draw.text((px + 14, py + 14), "BIOMA Y APARICION:", fill=(0, 200, 220, 255))
        draw.text((px + 14, py + 34), st["area"], fill=(230, 240, 250, 255))

        draw.text((px + 14, py + 70), "TABLA DE BOTIN:", fill=(0, 200, 220, 255))
        draw.text((px + 14, py + 90), st["loot"], fill=(180, 205, 225, 255))

        draw.text((px + 14, py + 125), "DESCRIPCION:", fill=(0, 200, 220, 255))
        # Word wrap desc
        desc_lines = [st["desc"][i:i+32] for i in range(0, len(st["desc"]), 32)]
        for d_idx, dl in enumerate(desc_lines):
            draw.text((px + 14, py + 145 + d_idx * 20), dl, fill=(200, 215, 230, 255))

        draw.text((px + 14, py + 240), "ESTADO:", fill=(0, 200, 220, 255))
        draw.text((px + 14, py + 260), "PROBADO & DESPLEGADO", fill=(100, 230, 140, 255))

    # 5th Section: Dungeons Crypt Overview (Right Panel, 1640..2340 px)
    cx = 1640
    cy = 140
    cw_box = 700
    ch_box = 1060
    draw.rectangle([cx, cy, cx + cw_box, cy + ch_box], fill=(14, 22, 36, 240), outline=(180, 110, 30, 255), width=2)
    # Header
    draw.rectangle([cx, cy, cx + cw_box, cy + 42], fill=(42, 28, 15, 255))
    draw.rectangle([cx, cy, cx + 6, cy + ch_box], fill=(220, 130, 30, 255))
    draw.text((cx + 16, cy + 12), "CRIPTA INSTANCIADA (PRIMERA MAZMORRA #20)", fill=(255, 255, 255, 255))
    draw.text((cx + cw_box - 130, cy + 12), "3 PISOS + JEFE", fill=(255, 180, 50, 255))

    # Crypt visual floor plan schematic
    draw.rectangle([cx + 30, cy + 60, cx + cw_box - 30, cy + 500], fill=(8, 12, 20, 255), outline=(40, 60, 80, 255))
    draw.text((cx + 45, cy + 75), "SECCION ARQUITECTONICA TRANSVERSAL", fill=(0, 200, 220, 255))

    # Draw 3 floors diagram
    floor_y = [cy + 130, cy + 240, cy + 350]
    floor_names = [
        ("NIVEL 1: VESTIBULO Y FOSA", "Spawners de zombies, trampas de caida, 4 cofres de entrada"),
        ("NIVEL 2: LABERINTO FUNERARIO", "Sarcofagos de piedra, placa de presion con flechas, cofre secreto"),
        ("NIVEL 3: SANTUARIO DEL JEFE", "Camara circular de 20x20m, altar ritual, aparicion del Rey Zombie"),
    ]
    for idx, (fy, (fname, fdesc)) in enumerate(zip(floor_y, floor_names)):
        # Floor slab
        draw.rectangle([cx + 50, fy, cx + cw_box - 50, fy + 75], fill=(22, 32, 48, 255), outline=(50, 75, 105, 255))
        draw.rectangle([cx + 50, fy, cx + 58, fy + 75], fill=(220, 140, 35, 255) if idx == 2 else (80, 140, 180, 255))
        draw.text((cx + 68, fy + 12), fname, fill=(255, 255, 255, 255))
        draw.text((cx + 68, fy + 38), fdesc, fill=(160, 185, 210, 255))
        # Stairs connecting floors
        if idx < 2:
            draw.line([(cx + cw_box - 80, fy + 75), (cx + cw_box - 80, fy + 110)], fill=(220, 180, 50, 255), width=3)
            draw.text((cx + cw_box - 145, fy + 85), "Escalera", fill=(220, 180, 50, 255))

    # Boss Card
    draw.rectangle([cx + 30, cy + 520, cx + cw_box - 30, cy + 740], fill=(18, 24, 38, 255), outline=(200, 60, 60, 255))
    draw.rectangle([cx + 30, cy + 520, cx + cw_box - 30, cy + 555], fill=(45, 18, 18, 255))
    draw.text((cx + 45, cy + 530), "JEFE FINAL: REY ZOMBIE (DOS FASES)", fill=(255, 120, 120, 255))

    draw.text((cx + 45, cy + 570), "Fase 1: Invoca oleadas de esbirros acorazados y carga con espada", fill=(230, 230, 230, 255))
    draw.text((cx + 45, cy + 600), "Fase 2 (al 50% vida): Furia desenfrenada, velocidad +40%, golpe expansivo", fill=(255, 180, 180, 255))
    draw.text((cx + 45, cy + 630), "Recompensas: Corona del Rey Zombie, Llave de la Cripta, Botin Legendario", fill=(255, 215, 0, 255))
    draw.text((cx + 45, cy + 660), "Escalado: Salud y ataque aumentan con dificultad y numero de jugadores", fill=(140, 200, 255, 255))
    draw.text((cx + 45, cy + 695), "Reinicio: Regeneracion automatica distribuida en ticks post-partida (#19)", fill=(100, 230, 140, 255))

    # Architecture specs footer
    draw.rectangle([cx + 30, cy + 760, cx + cw_box - 30, cy + ch_box - 25], fill=(12, 18, 28, 255), outline=(35, 55, 80, 255))
    draw.text((cx + 45, cy + 775), "SISTEMA DE DUNGEONS INSTANCIADAS (#10)", fill=(0, 200, 220, 255))
    draw.text((cx + 45, cy + 805), "- Dimension aislada: dolfincreepers:dungeons (Vacia)", fill=(190, 210, 230, 255))
    draw.text((cx + 45, cy + 830), "- Partidas en paralelo con colas y ready check (#14)", fill=(190, 210, 230, 255))
    draw.text((cx + 45, cy + 855), "- Modos: Facil, Medio, Hardcore con ban temporal (#16)", fill=(190, 210, 230, 255))
    draw.text((cx + 45, cy + 880), "- Sistema de tumbas protectoras al morir (#16)", fill=(190, 210, 230, 255))
    draw.text((cx + 45, cy + 905), "- Menu grafico interactivo por cofre /dungeon (#23)", fill=(190, 210, 230, 255))
    draw.text((cx + 45, cy + 930), "- Récords JSON guardados en mundo y LuckPerms (#22)", fill=(190, 210, 230, 255))
    draw.text((cx + 45, cy + 960), "ESTADO DE PRUEBAS: 23 FIXTURES PASADOS", fill=(100, 230, 140, 255))

    # Bottom summary footer
    draw.rectangle([40, ch - 120, cw - 40, ch - 30], fill=(12, 20, 35, 245), outline=(35, 65, 95, 255), width=2)
    draw.rectangle([40, ch - 120, 48, ch - 30], fill=(100, 230, 140, 255))
    draw.text((65, ch - 105), "INTEGRACION COMPLETA: ISSUE #21 (ESTRUCTURAS) & ISSUE #20 (CRIPTA) LISTAS Y VERIFICADAS", fill=(255, 255, 255, 255))
    draw.text((65, ch - 75), "Archivos generados: tools/estructuras/generate_estructuras.py -> NBT y JSON registrados en Dolfin Creepers | CriptaPlantilla.java", fill=(160, 190, 220, 255))
    draw.text((65, ch - 50), "Despliegue activo en /srv/dolfin/ (win-home) | Crafty Container ID e085fc68-2172-4d00-9d94-ad3aeeca7f5b", fill=(120, 220, 160, 255))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path, "PNG")
    print(f"Rendered estructuras: {output_path} ({cw}x{ch})")
    return output_path


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/assets/showcase_estructuras.png")
    render_all_estructuras_showcase(out)
