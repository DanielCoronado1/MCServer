"""Hoja de contactos: frente y espalda 3/4 de varios mobs ya generados, en una sola imagen.

Uso (desde la raiz del repo, despues de generar.py):
    python .claude/skills/skins-mobs/scripts/hoja.py runtime/skins/hoja.png minero_zombie ladron_zombie ...
Opcion: --dir <carpeta con las salidas de generar.py> (runtime/skins)
"""
import argparse
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motor  # noqa: E402

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("salida")
    ap.add_argument("claves", nargs="+")
    ap.add_argument("--dir", default="runtime/skins")
    a = ap.parse_args()

    celdas = []
    for c in a.claves:
        m = json.load(open(os.path.join(a.dir, c, "modelo.json"), encoding="utf-8"))
        tex = Image.open(os.path.join(a.dir, c, c + ".png")).convert("RGBA")
        partes = [dict(p, uv=tuple(p["uv"]), caja=tuple(p["caja"]), piv=tuple(p["piv"]), rot=tuple(p["rot"]))
                  for p in m["partes"]]
        celdas.append((c, motor.render(partes, tex, 32, 14, (340, 380)), motor.render(partes, tex, 212, 14, (340, 380))))
    im = Image.new("RGBA", (700 * 2, 420 * ((len(celdas) + 1) // 2)), motor.FONDO)
    dr = ImageDraw.Draw(im)
    f = ImageFont.load_default(size=20)
    for k, (c, frente, espalda) in enumerate(celdas):
        x, y = (k % 2) * 700, (k // 2) * 420
        im.alpha_composite(frente, (x + 10, y + 35))
        im.alpha_composite(espalda, (x + 350, y + 35))
        dr.text((x + 15, y + 8), c, fill=(230, 230, 230), font=f)
    im.save(a.salida)
    print(a.salida)

if __name__ == "__main__":
    main()
