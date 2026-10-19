"""Junta las capturas de una prueba en una sola imagen (hoja.jpg) para revisarlas de un vistazo.

Uso: python tests/manual/hoja.py [carpeta]   (por defecto, la prueba mas reciente)
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw

CAPTURAS = Path(__file__).resolve().parents[2] / "runtime/test-client" / "capturas"

carpeta = Path(sys.argv[1]) if len(sys.argv) > 1 else max(CAPTURAS.iterdir())
fotos = sorted(p for p in carpeta.glob("*.jpg") if p.name != "hoja.jpg")
ancho, alto, columnas = 480, 270, 3
filas = (len(fotos) + columnas - 1) // columnas
hoja = Image.new("RGB", (ancho * columnas, alto * filas), "black")
dibujo = ImageDraw.Draw(hoja)
for i, ruta in enumerate(fotos):
    x, y = (i % columnas) * ancho, (i // columnas) * alto
    hoja.paste(Image.open(ruta).resize((ancho, alto)), (x, y))
    dibujo.rectangle([x, y, x + 200, y + 18], fill="black")
    dibujo.text((x + 4, y + 3), ruta.stem, fill="yellow")
hoja.save(carpeta / "hoja.jpg", quality=88)
print(carpeta / "hoja.jpg")
