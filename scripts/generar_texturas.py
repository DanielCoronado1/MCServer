"""
Genera las texturas del evento /apocalipsis para el resource pack.

Uso:  python scripts/generar_texturas.py
Escribe los PNG en resourcepack/assets/dolfin/textures/item/

Texturas:
  vortice.png      remolino rojo y negro (el disco gigante del cielo)
  nubes_pared.png  pared de nubes rojas que tapa el horizonte
  grieta.png       grieta vertical con bordes de neon (animada, 4 cuadros)
  rayo.png         rayos rojos que parpadean (animada, 6 cuadros)
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
TEXTURAS = RAIZ / "resourcepack" / "assets" / "dolfin" / "textures"
SALIDA = TEXTURAS / "item"
FUENTE = TEXTURAS / "font"
rng = np.random.default_rng(1337)


# ----------------------------------------------------------------------------
#  Utilidades
# ----------------------------------------------------------------------------

def ruido(ancho, alto, octavas=5, base=4, semilla=None):
    """Ruido fractal suave en [0, 1] (capas de ruido aleatorio ampliadas)."""
    g = np.random.default_rng(semilla) if semilla is not None else rng
    total = np.zeros((alto, ancho), np.float32)
    peso_total = 0.0
    for o in range(octavas):
        celdas = base * 2 ** o
        capa = g.random((celdas, celdas)).astype(np.float32)
        img = Image.fromarray((capa * 255).astype(np.uint8)).resize((ancho, alto), Image.BICUBIC)
        peso = 0.5 ** o
        total += np.asarray(img, np.float32) / 255.0 * peso
        peso_total += peso
    total /= peso_total
    return (total - total.min()) / (total.max() - total.min() + 1e-6)


def muestrear(img, xs, ys):
    """Lectura bilineal de una imagen 2D en coordenadas (xs, ys) arbitrarias."""
    h, w = img.shape
    xs = np.clip(xs, 0, w - 1.001)
    ys = np.clip(ys, 0, h - 1.001)
    x0 = xs.astype(int)
    y0 = ys.astype(int)
    fx = xs - x0
    fy = ys - y0
    a = img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx
    b = img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


def paleta_infernal(v):
    """0 = negro, 0.55 = rojo oscuro, 0.85 = rojo vivo, 1 = rojo anaranjado."""
    puntos = np.array([0.0, 0.35, 0.6, 0.85, 1.0])
    colores = np.array([
        [4, 0, 0],
        [40, 0, 2],
        [130, 4, 8],
        [225, 20, 20],
        [255, 110, 70],
    ], np.float32)
    rgb = np.stack([np.interp(v, puntos, colores[:, c]) for c in range(3)], axis=-1)
    return rgb


def guardar(nombre, rgba, animacion=None, carpeta=SALIDA):
    carpeta.mkdir(parents=True, exist_ok=True)
    ruta = carpeta / nombre
    Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8)).save(ruta, optimize=True)
    if animacion:
        (carpeta / (nombre + ".mcmeta")).write_text(json.dumps({"animation": animacion}, indent=2))
    print("  ", ruta.relative_to(RAIZ))


# ----------------------------------------------------------------------------
#  1. Remolino (disco del cielo)
# ----------------------------------------------------------------------------

# Las imagenes de fuente no pueden pasar de 256x256 (Minecraft las descarta sin avisar).
def vortice(t=256):
    y, x = np.mgrid[0:t, 0:t].astype(np.float32)
    dx = (x - t / 2) / (t / 2)
    dy = (y - t / 2) / (t / 2)
    r = np.sqrt(dx ** 2 + dy ** 2)
    ang = np.arctan2(dy, dx)

    # Torcemos un ruido de nubes alrededor del centro: mas giro cerca del centro.
    nubes = ruido(t, t, octavas=6, base=3)
    giro = 5.5 * (1 - np.clip(r, 0, 1)) ** 1.6
    a2 = ang + giro
    sx = t / 2 + np.cos(a2) * r * t / 2
    sy = t / 2 + np.sin(a2) * r * t / 2
    torcido = muestrear(nubes, sx, sy)

    # Brazos de la espiral.
    brazos = 0.5 + 0.5 * np.sin(3 * ang + 9 * r + torcido * 4)
    brazos = brazos ** 1.6                          # brazos mas finos y marcados
    v = 0.5 * torcido + 0.5 * brazos
    v = v * np.clip(r * 2.5, 0.55, 1)              # el centro algo mas oscuro
    v = np.clip((v - 0.1) * 1.25, 0, 1)

    # Borde: un aro rojo mas brillante e irregular que se desvanece.
    borde_ruido = ruido(t, t, octavas=4, base=6)
    limite = 0.86 + (borde_ruido - 0.5) * 0.16
    aro = np.exp(-((r - limite + 0.05) / 0.05) ** 2)
    v = np.clip(v + aro * 0.3, 0, 0.88)            # aro rojo, sin llegar al naranja
    alfa = np.clip((limite - r) / 0.07, 0, 1) * 255

    rgb = paleta_infernal(v)
    guardar("vortice.png", np.dstack([rgb, alfa]), carpeta=FUENTE)


# ----------------------------------------------------------------------------
#  2. Pared de nubes del horizonte
# ----------------------------------------------------------------------------

def nubes_pared(ancho=256, alto=128):
    n = ruido(ancho, alto, octavas=6, base=3)
    estirado = np.asarray(Image.fromarray((n * 255).astype(np.uint8)).resize((ancho, alto // 2)).resize((ancho, alto), Image.BICUBIC), np.float32) / 255
    v = 0.6 * n + 0.4 * estirado
    altura = np.linspace(1, 0, alto)[:, None]      # 1 arriba, 0 abajo
    v = np.clip(v * (0.55 + 0.45 * altura) + 0.05, 0, 1)
    rgb = paleta_infernal(v * 0.9)

    # Sin desvanecer los lados: los paneles se solapan un poco y asi no quedan costuras.
    alfa = np.clip(0.55 + 0.45 * altura + (n - 0.5) * 0.3, 0, 1) * 255
    guardar("nubes_pared.png", np.dstack([rgb, alfa]), carpeta=FUENTE)


# ----------------------------------------------------------------------------
#  3. Grieta vertical (animada)
# ----------------------------------------------------------------------------

def linea_quebrada(n, amplitud, pasos, semilla):
    """Una linea en zigzag suave de n puntos (para el camino de la grieta)."""
    g = np.random.default_rng(semilla)
    nudos = g.uniform(-1, 1, pasos + 1) * amplitud
    return np.interp(np.linspace(0, pasos, n), np.arange(pasos + 1), nudos)


def grieta(ancho=128, alto=512, cuadros=4):
    y, x = np.mgrid[0:alto, 0:ancho].astype(np.float32)
    fy = np.arange(alto) / (alto - 1)

    centro = ancho / 2 + linea_quebrada(alto, 10, 14, 7)          # la grieta serpentea
    forma = np.sin(np.pi * fy) ** 0.75                             # mas ancha en el medio
    dientes = linea_quebrada(alto, 1, 60, 8)                       # bordes dentados
    semi = 30 * forma * (1 + 0.25 * dientes) + 0.8
    dist = np.abs(x - centro[:, None]) - semi[:, None]             # <0 dentro, >0 fuera

    interior_ruido = ruido(ancho, alto, octavas=5, base=4)
    interior = np.stack([
        12 + 30 * interior_ruido,
        8 + 14 * interior_ruido,
        40 + 50 * interior_ruido,
    ], axis=-1)

    hojas = []
    for c in range(cuadros):
        pulso = 0.8 + 0.2 * np.sin(2 * np.pi * c / cuadros)
        temblor = linea_quebrada(alto, 0.7, 80, 100 + c)[:, None]
        d = dist + temblor

        nucleo = np.exp(-(d / 1.6) ** 2)                            # linea casi blanca
        neon = np.exp(-(np.maximum(d, 0) / (9 * pulso)))            # resplandor rojo hacia fuera
        dentro = (d < 0).astype(np.float32)

        rgb = interior * dentro[..., None]
        rojo = np.array([255, 25, 25], np.float32)
        blanco = np.array([255, 215, 215], np.float32)
        rgb = rgb * (1 - neon[..., None] * (1 - dentro[..., None])) + rojo * (neon * (1 - dentro))[..., None]
        borde_interno = np.exp(-(np.minimum(d, 0) / 4.5) ** 2) * dentro
        rgb = rgb * (1 - borde_interno[..., None] * 0.9) + rojo * (borde_interno * 0.9)[..., None]
        rgb = rgb * (1 - nucleo[..., None]) + blanco * nucleo[..., None]

        alfa = np.maximum(dentro, neon * 0.95 * pulso)
        alfa = np.maximum(alfa, nucleo) * 255
        # Puntas superior e inferior que se desvanecen.
        alfa *= np.clip(np.minimum(fy, 1 - fy) / 0.03, 0, 1)[:, None]
        hojas.append(np.dstack([rgb * pulso + (1 - pulso) * rgb * 0.7, alfa]))

    guardar("grieta.png", np.vstack(hojas),
            animacion={"frametime": 3, "interpolate": True, "width": ancho, "height": alto})


# ----------------------------------------------------------------------------
#  4. Rayos (animados, con cuadros vacios para que parpadeen)
# ----------------------------------------------------------------------------

def dibujar_rayo(ancho, alto, semilla):
    g = np.random.default_rng(semilla)
    capa = Image.new("L", (ancho, alto), 0)
    from PIL import ImageDraw
    dib = ImageDraw.Draw(capa)

    def rama(x, y, largo_restante, grosor, direccion):
        while largo_restante > 0 and y < alto:
            paso = g.uniform(8, 20)
            nx = float(np.clip(x + g.uniform(-14, 14) + direccion * 4, 10, ancho - 10))
            ny = y + paso
            dib.line([(x, y), (nx, ny)], fill=255, width=int(grosor))
            if g.random() < 0.12 and grosor > 1.5:
                rama(nx, ny, largo_restante * 0.4, grosor * 0.6, g.choice([-1, 1]))
            x, y = nx, ny
            largo_restante -= paso

    rama(ancho / 2, 0, alto, 4, 0)
    nucleo = np.asarray(capa, np.float32) / 255
    brillo = np.asarray(capa.filter(ImageFilter.GaussianBlur(5)), np.float32) / 255
    brillo = np.clip(brillo * 3, 0, 1)

    rgb = np.zeros((alto, ancho, 3), np.float32)
    rgb[...] = [255, 20, 20]
    rgb = rgb * (1 - nucleo[..., None]) + np.array([255, 220, 220]) * nucleo[..., None]
    alfa = np.maximum(nucleo, brillo * 0.8) * 255
    return np.dstack([rgb, alfa])


def rayo(ancho=64, alto=256):
    vacio = np.zeros((alto, ancho, 4), np.float32)
    hojas = [dibujar_rayo(ancho, alto, s) for s in (21, 22, 23)] + [vacio, vacio, vacio]
    # Orden de cuadros: rayo, apagado, otro rayo... (0-2 = rayos, 3-5 = vacio)
    orden = [0, 3, 0, 4, 1, 1, 5, 3, 2, 4, 5, 3, 0, 2, 4, 5, 1, 3]
    guardar("rayo.png", np.vstack(hojas),
            animacion={"frametime": 2, "width": ancho, "height": alto, "frames": orden})


# ----------------------------------------------------------------------------
#  Modelos: cada textura va en un "plano" (un cuadrado de 1x1 bloque que brilla)
# ----------------------------------------------------------------------------

# Sprites que se muestran con ItemDisplay (admiten texturas animadas).
PLANOS = ["grieta", "rayo"]
# Sprites que se muestran como "letras" de una fuente con TextDisplay: el texto no
# recibe el sombreado de los objetos, asi que se ven igual de brillantes desde
# cualquier angulo (ideal para el disco del cielo, que mira hacia abajo).
# caracter -> textura en textures/font/. Los usa el plugin (EventoApocalipsis.java).
LETRAS = {"": "nubes_pared"}
ALTO_LETRA = 128   # alto de cada imagen en "pixeles de texto" (1 px = 0.025 bloques)
ASCENSO = 61       # con este valor la imagen queda centrada en la entidad (alto/2 - 3)
# El remolino se parte en 8x8 baldosas ( ... , por filas de arriba abajo).
# Asi cada baldosa calcula su propia niebla; una sola imagen gigante sale toda gris.
BALDOSAS = 8
LETRA_BALDOSA = 0xE100
ALTO_BALDOSA = 32
FORMATO_PACK = 97  # Minecraft 26.3


def escribir_json(ruta, datos):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, indent=2), encoding="utf-8")


def modelos():
    pack = RAIZ / "resourcepack"
    escribir_json(pack / "pack.mcmeta", {"pack": {
        "description": "DolfinServer - texturas del servidor",
        "min_format": FORMATO_PACK,
        "max_format": FORMATO_PACK,
    }})
    for nombre in PLANOS:
        # Definicion del item: el plugin usa item_model "dolfin:<nombre>".
        escribir_json(pack / "assets/dolfin/items" / f"{nombre}.json",
                      {"model": {"type": "minecraft:model", "model": f"dolfin:item/{nombre}"}})
        # Modelo: un plano vertical con la textura por las dos caras.
        escribir_json(pack / "assets/dolfin/models/item" / f"{nombre}.json", {
            "textures": {"t": f"dolfin:item/{nombre}", "particle": f"dolfin:item/{nombre}"},
            "elements": [{
                "from": [0, 0, 8], "to": [16, 16, 8],
                "shade": False, "light_emission": 15,
                "faces": {
                    "north": {"uv": [16, 0, 0, 16], "texture": "#t"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#t"},
                },
            }],
        })
    filas = ["".join(chr(LETRA_BALDOSA + fila * BALDOSAS + col) for col in range(BALDOSAS))
             for fila in range(BALDOSAS)]
    escribir_json(pack / "assets/dolfin/font/cielo.json", {"providers": [
        {"type": "bitmap", "file": "dolfin:font/vortice.png",
         "ascent": ALTO_BALDOSA // 2 - 3, "height": ALTO_BALDOSA, "chars": filas},
    ] + [
        {"type": "bitmap", "file": f"dolfin:font/{textura}.png",
         "ascent": ASCENSO, "height": ALTO_LETRA, "chars": [letra]}
        for letra, textura in LETRAS.items()
    ]})
    print("   modelos, fuente y pack.mcmeta")


if __name__ == "__main__":
    import shutil
    # Empezamos de cero para que no queden archivos viejos dentro del pack.
    shutil.rmtree(RAIZ / "resourcepack" / "assets" / "dolfin", ignore_errors=True)
    print("Generando texturas en", TEXTURAS.relative_to(RAIZ))
    vortice()
    nubes_pared()
    grieta()
    rayo()
    modelos()
    print("Listo.")
