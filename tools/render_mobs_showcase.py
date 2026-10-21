"""Generates high-definition mobs and bosses showcase composite."""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


def render_mobs_showcase(output_path: Path) -> Path:
    cw = 2400
    ch = 1600
    img = Image.new("RGBA", (cw, ch), (8, 12, 22, 255))
    draw = ImageDraw.Draw(img)

    # Subtle grid background
    for x in range(0, cw, 40):
        draw.line([(x, 0), (x, ch)], fill=(18, 28, 44, 180), width=1)
    for y in range(0, ch, 40):
        draw.line([(0, y), (cw, y)], fill=(18, 28, 44, 180), width=1)

    # Header
    draw.rectangle([40, 25, cw - 40, 110], fill=(12, 20, 35, 245), outline=(35, 65, 95, 255), width=2)
    draw.rectangle([40, 25, 48, 110], fill=(220, 50, 50, 255))
    draw.text((65, 38), "BESTIARIO Y CATALOGO DE MOBS / JEFES -- DOLFIN SERVER", fill=(255, 255, 255, 255))
    draw.text((65, 66), "Variantes exclusivas de Dolfin Creepers, Dolfin Zombies y Dolfin Space integradas en el mundo y mazmorras", fill=(130, 185, 220, 255))
    draw.text((65, 88), "IA adaptativa | Escalado por dificultad y jugadores (#18) | Texturas procedurales personalizadas", fill=(220, 180, 50, 255))

    # Host badge
    draw.rectangle([cw - 440, 42, cw - 60, 92], fill=(18, 30, 48, 255), outline=(220, 50, 50, 200))
    draw.ellipse([cw - 425, 62, cw - 413, 74], fill=(50, 230, 100, 255))
    draw.text((cw - 402, 52), "LIVE SERVER VERIFIED", fill=(255, 255, 255, 255))
    draw.text((cw - 402, 70), "MOB REGISTRY ONLINE", fill=(100, 220, 140, 255))

    # 3 Main Columns / Cards:
    # Col 1: Dolfin Creepers (X: 40..780)
    # Col 2: Dolfin Zombies & Boss Rey Zombie (X: 820..1580)
    # Col 3: Dolfin Space & Kepler Exofauna (X: 1620..2360)
    col_w = 740
    col_h = 1320
    top_y = 135

    # -----------------------------
    # COL 1: DOLFIN CREEPERS
    # -----------------------------
    c1_x = 40
    draw.rectangle([c1_x, top_y, c1_x + col_w, top_y + col_h], fill=(12, 22, 18, 240), outline=(50, 160, 60, 255), width=2)
    draw.rectangle([c1_x, top_y, c1_x + col_w, top_y + 44], fill=(18, 48, 25, 255))
    draw.rectangle([c1_x, top_y, c1_x + 6, top_y + col_h], fill=(70, 210, 80, 255))
    draw.text((c1_x + 16, top_y + 12), "DOLFIN CREEPERS (9 VARIANTES)", fill=(255, 255, 255, 255))
    draw.text((c1_x + col_w - 140, top_y + 12), "MOD: CREEPERS", fill=(100, 230, 120, 255))

    # Paste creeper texture preview
    creeper_img_path = Path("mods/creepers/vista_previa_texturas.png")
    if creeper_img_path.exists():
        c_src = Image.open(creeper_img_path).convert("RGBA")
        # Resize to fit card width (700x120)
        c_thumb = c_src.resize((700, int(700 * c_src.height / c_src.width)), Image.Resampling.LANCZOS)
        img.alpha_composite(c_thumb, (c1_x + 20, top_y + 60))

    # Creeper details
    draw.rectangle([c1_x + 20, top_y + 195, c1_x + col_w - 20, top_y + col_h - 25], fill=(8, 16, 12, 230), outline=(25, 75, 35, 255))
    cx = c1_x + 35
    cy = top_y + 215

    creeper_types = [
        ("1. CREEPER EXPLOSIVO", "Radio de explosion x2.5, mecha reducida a 1.2s", "Vida: 24 HP | Peligro: Extremo | Spawn: Noche"),
        ("2. CREEPER CARGADO", "Aura electrica permanente, inmune a proyectiles ligeros", "Vida: 35 HP | Peligro: Letal | Spawn: Tormentas"),
        ("3. CREEPER SIGILOSO", "Completamente insonoro hasta el siseo final a 2 bloques", "Vida: 20 HP | Peligro: Alto | Spawn: Bosques/Cuevas"),
        ("4. CREEPER DE LAVA / INFIERNO", "Explosion con ignicion en area y rastro de fuego", "Vida: 30 HP | Peligro: Muy Alto | Spawn: Nether"),
        ("5. CREEPER GELIDO", "Ralentiza y congela jugadores al detonar (efecto Escarcha)", "Vida: 26 HP | Peligro: Medio | Spawn: Tundras"),
        ("6. CREEPER MUTANTE", "Resistencia a retroceso, empuje expansivo y salto alto", "Vida: 50 HP | Peligro: Critico | Spawn: Dungeons (#18)"),
    ]

    for ctitle, cdesc, cstats in creeper_types:
        draw.rectangle([cx, cy, cx + col_w - 70, cy + 95], fill=(14, 28, 20, 255), outline=(40, 110, 50, 255))
        draw.rectangle([cx, cy, cx + 5, cy + 95], fill=(70, 210, 80, 255))
        draw.text((cx + 14, cy + 10), ctitle, fill=(130, 240, 140, 255))
        draw.text((cx + 14, cy + 36), cdesc, fill=(220, 235, 220, 255))
        draw.text((cx + 14, cy + 62), cstats, fill=(160, 210, 170, 255))
        cy += 115

    # Footer inside Col 1
    draw.text((cx, cy + 20), "Kioscos en Lobby: Coordenadas X: -44..-20, Z: -25..-11", fill=(0, 200, 220, 255))
    draw.text((cx, cy + 45), "Interactivos con /dolfin spawn creeper_<tipo>", fill=(160, 200, 220, 255))

    # -----------------------------
    # COL 2: DOLFIN ZOMBIES & REY ZOMBIE
    # -----------------------------
    c2_x = 830
    draw.rectangle([c2_x, top_y, c2_x + col_w, top_y + col_h], fill=(22, 16, 12, 240), outline=(200, 90, 30, 255), width=2)
    draw.rectangle([c2_x, top_y, c2_x + col_w, top_y + 44], fill=(52, 28, 14, 255))
    draw.rectangle([c2_x, top_y, c2_x + 6, top_y + col_h], fill=(240, 120, 40, 255))
    draw.text((c2_x + 16, top_y + 12), "DOLFIN ZOMBIES & REY ZOMBIE (JEFE #20)", fill=(255, 255, 255, 255))
    draw.text((c2_x + col_w - 140, top_y + 12), "MOD: ZOMBIES", fill=(255, 170, 70, 255))

    # Paste zombie skins preview
    zombie_img_path = Path("docs/zombies/issue-08-skins.png")
    if zombie_img_path.exists():
        z_src = Image.open(zombie_img_path).convert("RGBA")
        z_thumb = z_src.resize((700, int(700 * z_src.height / z_src.width)), Image.Resampling.LANCZOS)
        # Crop if too tall
        if z_thumb.height > 260:
            z_thumb = z_thumb.crop((0, 0, 700, 260))
        img.alpha_composite(z_thumb, (c2_x + 20, top_y + 60))

    # Zombie details
    draw.rectangle([c2_x + 20, top_y + 335, c2_x + col_w - 20, top_y + col_h - 25], fill=(16, 10, 8, 230), outline=(75, 35, 15, 255))
    zx = c2_x + 35
    zy = top_y + 355

    zombie_types = [
        ("REY ZOMBIE (JEFE DE LA CRIPTA #20)", "Jefe en 2 fases, invoca guardia real, lanza onda expansiva", "Vida: 300 HP (escala por party) | Dano: 16 | Nivel 3"),
        ("ZOMBIE CABALLERO CORRUPTO", "Armadura pesada de hierro negro, escudo bloqueador y espada", "Vida: 45 HP | Dano: 10 | Spawn: Dungeons / Noche"),
        ("MOMIA DE LAS PIRAMIDES", "Aplica veneno necrotico y ceguera al impactar cuerpo a cuerpo", "Vida: 32 HP | Dano: 8 | Spawn: Desiertos"),
        ("ZOMBIE MINERO", "Pico afilado perfora armaduras, casco con linterna de mineria", "Vida: 28 HP | Dano: 9 | Spawn: Minas y Campamentos"),
        ("BERSERKER MUTANTE", "Aumenta su velocidad +15% con cada golpe recibido", "Vida: 60 HP | Dano: 12 | Spawn: Dungeons Hardcore"),
    ]

    for ztitle, zdesc, zstats in zombie_types:
        draw.rectangle([zx, zy, zx + col_w - 70, zy + 105], fill=(30, 18, 14, 255), outline=(130, 65, 25, 255))
        draw.rectangle([zx, zy, zx + 5, zy + 105], fill=(240, 120, 40, 255))
        draw.text((zx + 14, zy + 10), ztitle, fill=(255, 170, 70, 255))
        draw.text((zx + 14, zy + 38), zdesc, fill=(240, 220, 210, 255))
        draw.text((zx + 14, zy + 68), zstats, fill=(210, 150, 120, 255))
        zy += 125

    # Footer inside Col 2
    draw.text((zx, zy + 15), "16 Kioscos y Arenas en Lobby: X: -16..10, Z: -25..-11", fill=(0, 200, 220, 255))
    draw.text((zx, zy + 40), "Prueba de combate activa mediante /arena join <slot>", fill=(160, 200, 220, 255))

    # -----------------------------
    # COL 3: DOLFIN SPACE & KEPLER EXOBESTIARIO
    # -----------------------------
    c3_x = 1620
    draw.rectangle([c3_x, top_y, c3_x + col_w, top_y + col_h], fill=(10, 18, 30, 240), outline=(25, 140, 185, 255), width=2)
    draw.rectangle([c3_x, top_y, c3_x + col_w, top_y + 44], fill=(16, 38, 62, 255))
    draw.rectangle([c3_x, top_y, c3_x + 6, top_y + col_h], fill=(0, 210, 245, 255))
    draw.text((c3_x + 16, top_y + 12), "DOLFIN SPACE & KEPLER-452b EXOBESTIARIO", fill=(255, 255, 255, 255))
    draw.text((c3_x + col_w - 140, top_y + 12), "MOD: SPACE", fill=(0, 210, 245, 255))

    # Paste space assets preview
    space_img_path = Path("docs/space/issue-32-assets.png")
    if space_img_path.exists():
        s_src = Image.open(space_img_path).convert("RGBA")
        s_thumb = s_src.resize((700, int(700 * s_src.height / s_src.width)), Image.Resampling.LANCZOS)
        if s_thumb.height > 260:
            s_thumb = s_thumb.crop((0, 0, 700, 260))
        img.alpha_composite(s_thumb, (c3_x + 20, top_y + 60))

    # Space details
    draw.rectangle([c3_x + 20, top_y + 335, c3_x + col_w - 20, top_y + col_h - 25], fill=(6, 12, 22, 230), outline=(15, 45, 75, 255))
    sx = c3_x + 35
    sy = top_y + 355

    space_types = [
        ("ASTRONAUTA DOLFIN (EQUIPAMIENTO)", "Traje presurizado de EVA, tanque de O2, botas antigravedad", "Oxigeno: 1,200s | Vuelo Ionico: 45m | Resistencia Radiacion"),
        ("EXOHUNTER DE KEPLER-452b", "Depredador alienigena bio-luminiscente, camuflaje en cristales", "Vida: 55 HP | Dano: 14 | Gravedad Reducida | Hostil"),
        ("DRON GUARDIAN ORBITAL", "Unidad defensiva automatizada de la estacion espacial", "Vida: 40 HP | Ataque: Rayo laser continuo | Rango: 24m"),
        ("COLOSO DE SILICIO (MINAS EXOTERRESTRES)", "Golem alienigena de cuarzo y basalto kepleriano", "Vida: 120 HP | Dano: 18 | Inmune a asfixia y frio"),
        ("TRANSPORTE ORBITAL DOLFIN-X1", "Nave monoplaza funcional con propulsor de salto hiperespacial", "Velocidad: Nivel orbital | Destino: Base Kepler Pad (100k, 200)"),
    ]

    for stitle, sdesc, sstats in space_types:
        draw.rectangle([sx, sy, sx + col_w - 70, sy + 105], fill=(14, 26, 44, 255), outline=(25, 70, 110, 255))
        draw.rectangle([sx, sy, sx + 5, sy + 105], fill=(0, 200, 230, 255))
        draw.text((sx + 14, sy + 10), stitle, fill=(0, 215, 245, 255))
        draw.text((sx + 14, sy + 38), sdesc, fill=(220, 235, 245, 255))
        draw.text((sx + 14, sy + 68), sstats, fill=(140, 185, 220, 255))
        sy += 125

    # Footer inside Col 3
    draw.text((sx, sy + 15), "Plataforma de Lanzamiento en Lobby: X: 18..44, Z: -25..-11", fill=(0, 200, 220, 255))
    draw.text((sx, sy + 40), "Viaje interdimensional directo via /dolfin travel kepler", fill=(160, 200, 220, 255))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path, "PNG")
    print(f"Rendered mobs showcase: {output_path} ({cw}x{ch})")
    return output_path


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/assets/showcase_mobs.png")
    render_mobs_showcase(out)
