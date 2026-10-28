"""Motor de texturas y modelos para mobs de DolfinServer (estilo v2).

Un diseño de mob es un modulo de Python con:
    MOB = dict(clave=..., nombre=..., escala=..., seed=...)
    PIEZAS = {"region": (ancho, alto, fondo), ...}   # tamanos de caja en pixeles de modelo
    def partes(): -> lista de dict(r=region, caja=(x0,y0,z0,x1,y1,z1), piv=(x,y,z), rot=(rx,ry,rz))
    def pintar(t, uv, pz): pinta cada region en el Lienzo t (uv(nombre) da su esquina en la textura)
    def relieve(t, uv, pz): opcional, retoca la capa exterior (t.exterior) despues de generarla sola

Capa exterior: cada pieza de 2 px o mas de grosor lleva una segunda caja inflada (como el sombrero y la chaqueta
del jugador) con su propia textura, en el mismo UV que la base. Lo transparente no se ve; lo pintado sobresale.

Coordenadas del modelo: 16 px = 1 bloque, y hacia arriba, +z es el frente del mob (hacia quien lo mira),
+x es la izquierda del mob. Las rotaciones son en grados y se aplican en orden x, z, y alrededor de piv.
"""

import math

from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- rampas (oscuro -> claro, sombras frias y luces calidas)
PIEL = [(30, 40, 44), (44, 62, 52), (62, 86, 60), (86, 112, 70), (118, 138, 86), (154, 166, 112)]
VERDE = [(18, 38, 30), (28, 60, 36), (42, 88, 40), (62, 120, 46), (94, 152, 58), (138, 184, 84)]
CARNE = [(42, 14, 26), (72, 20, 34), (106, 32, 40), (140, 54, 54), (174, 92, 82)]
HUESO = [(88, 80, 76), (136, 124, 108), (182, 170, 144), (220, 210, 182), (244, 238, 216)]
NEGRO = [(8, 6, 10), (20, 16, 22), (34, 28, 36)]
OJO = [(84, 10, 14), (160, 24, 24), (226, 62, 40), (255, 148, 90)]
HONGO = [(56, 16, 22), (104, 28, 30), (154, 48, 38), (198, 82, 50), (232, 128, 78)]
MICELIO = [(104, 98, 94), (146, 140, 128), (186, 180, 164), (220, 214, 198), (244, 240, 228)]
CAFE = [(30, 24, 22), (50, 38, 32), (74, 56, 42), (100, 78, 56), (128, 102, 72)]
DENIM = [(20, 28, 50), (30, 44, 74), (44, 64, 102), (64, 90, 130), (94, 122, 158)]
AMARILLO = [(66, 40, 16), (124, 80, 20), (184, 130, 28), (228, 182, 52), (252, 226, 122)]
HIERRO = [(40, 40, 48), (76, 76, 88), (118, 118, 128), (166, 166, 174), (212, 212, 218)]
MADERA = [(46, 28, 18), (76, 50, 30), (108, 76, 44), (140, 102, 60), (168, 130, 80)]
AMATISTA = [(40, 20, 64), (76, 38, 110), (118, 68, 166), (164, 110, 212), (212, 168, 246)]
CUADROS = [(48, 14, 18), (86, 24, 28), (128, 38, 38), (166, 62, 52), (198, 98, 78)]
TRAJE = [(80, 84, 98), (126, 130, 144), (172, 176, 186), (212, 214, 220), (238, 238, 242)]
NARANJA = [(78, 28, 12), (138, 56, 18), (198, 94, 32), (234, 136, 54), (252, 184, 108)]
ORO = [(70, 46, 12), (130, 92, 24), (190, 146, 48), (232, 196, 92), (252, 236, 170)]
CARBON = [(10, 8, 10), (24, 20, 22), (40, 32, 32), (60, 48, 44), (84, 68, 58)]
MAGMA = [(90, 14, 8), (160, 40, 10), (220, 90, 20), (250, 150, 40), (255, 214, 110)]
HIELO = [
    (40, 60, 96),
    (70, 104, 146),
    (112, 152, 190),
    (160, 196, 224),
    (214, 236, 250),
    (244, 252, 255),
]
LIMA = [(26, 46, 30), (44, 78, 32), (76, 116, 36), (116, 154, 44), (160, 190, 70), (206, 222, 120)]
TEAL = [(14, 40, 48), (22, 66, 70), (34, 98, 92), (54, 132, 114), (90, 170, 140), (140, 206, 172)]
PURPURA = [(26, 14, 38), (44, 22, 62), (66, 34, 86), (92, 52, 110), (124, 78, 138), (160, 110, 166)]
TOXICO = [(30, 60, 14), (60, 110, 20), (110, 170, 30), (170, 220, 60), (220, 250, 130)]
QUITINA = [(10, 10, 22), (22, 20, 40), (38, 32, 62), (58, 48, 86), (84, 70, 112), (120, 104, 146)]
ACIDO = [(30, 50, 10), (70, 100, 14), (130, 160, 20), (190, 214, 50), (236, 248, 130)]
CIAN = [(16, 48, 70), (24, 90, 120), (40, 140, 170), (90, 196, 214), (170, 236, 244)]
REGOLITO = [
    (38, 34, 36),
    (62, 56, 54),
    (92, 84, 76),
    (124, 114, 100),
    (160, 150, 130),
    (196, 186, 164),
]
TELA_ROJA = [(40, 8, 14), (76, 14, 22), (116, 22, 30), (156, 40, 40), (190, 72, 62)]
PALIDO = [(70, 58, 70), (112, 94, 104), (154, 134, 136), (194, 176, 168), (226, 212, 196)]
ALGA = [(14, 34, 26), (22, 56, 34), (36, 82, 42), (58, 110, 52), (90, 140, 70)]
PAJA = [(80, 60, 20), (130, 100, 36), (180, 146, 60), (214, 186, 96), (240, 222, 150)]
CUERO = [(28, 16, 12), (52, 30, 20), (82, 50, 30), (114, 74, 44), (146, 102, 64)]


# ---------------------------------------------------------------- geometria
def regiones(u, v, w, h, d):
    """Caras de una caja en el layout estandar de Minecraft: (x, y, ancho, alto)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "der": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "izq": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def rotar(p, rot):
    x, y, z = p
    ax, ay, az = (math.radians(a) for a in rot)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    return (x, y, z)


def en(piv, rot, local):
    """Punto local de una pieza rotada, en coordenadas del modelo (para encadenar codo, rodilla, etc.)."""
    return tuple(a + b for a, b in zip(piv, rotar(local, rot), strict=True))


def caja_centrada(tam, y0=0):
    """Caja centrada en x/z que crece hacia arriba desde y0 (util para puas y cristales)."""
    w, h, d = tam
    return (-w / 2, y0, -d / 2, w / 2, y0 + h, d / 2)


HUMANOIDE = dict(
    cabeza=(8, 8, 8), cuerpo=(8, 12, 4), brazo_d=(4, 12, 4), brazo_i=(4, 12, 4), pierna=(4, 12, 4)
)


def humanoide(brazo_d=(-90, 0, 0), brazo_i=(-90, 0, 0), cabeza=(0, 0, 0), pierna_i="pierna"):
    """Esqueleto de zombie vanilla (piernas, cuerpo, cabeza, brazos al frente). Los hijos de la cabeza
    (cascos, hongos) van con piv=(0, 24, 0) y la misma rot que la cabeza."""
    return [
        dict(r="pierna", caja=(-2, -12, -2, 2, 0, 2), piv=(-2, 12, 0)),
        dict(r=pierna_i, caja=(-2, -12, -2, 2, 0, 2), piv=(2, 12, 0)),
        dict(r="cuerpo", caja=(-4, 0, -2, 4, 12, 2), piv=(0, 12, 0)),
        dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=(0, 24, 0), rot=cabeza),
        dict(r="brazo_d", caja=(-2, -10, -2, 2, 2, 2), piv=(-6, 22, 0), rot=brazo_d),
        dict(r="brazo_i", caja=(-2, -10, -2, 2, 2, 2), piv=(6, 22, 0), rot=brazo_i),
    ]


CREEPER = dict(cabeza=(8, 8, 8), cuerpo=(8, 12, 4), pata=(4, 6, 4))


def creeper(cabeza=(0, 0, 0), cuerpo=(0, 0, 0)):
    """Esqueleto de creeper vanilla: 4 patas cortas, cuerpo y cabeza. Los hijos de la cabeza van con
    piv=(0, 18, 0) y la misma rot que la cabeza; los del cuerpo con piv=(0, 6, 0) y la rot del cuerpo."""
    S = [
        dict(r="pata", caja=(-2, -6, -2, 2, 0, 2), piv=(x, 6, z)) for x in (-2, 2) for z in (-4, 4)
    ]
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 12, 2), piv=(0, 6, 0), rot=cuerpo))
    S.append(
        dict(
            r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=en((0, 6, 0), cuerpo, (0, 12, 0)), rot=cabeza
        )
    )
    return S


CARA_CREEPER = {
    "K": (NEGRO, 0),
    "k": -2,
    "s": -1,
    "R": (OJO, 2),
    "r": (OJO, 1),
    "W": (HUESO, 3),
    "c": (CARNE, 1),
    "B": (HUESO, 4),
}


def empacar(piezas, ancho=128):
    """Acomoda las regiones en la textura; devuelve {region: (u, v)} y el alto de la textura."""
    x = y = fila = 0
    uvs = {}
    for nombre, (w, h, d) in sorted(piezas.items(), key=lambda kv: -(kv[1][2] + kv[1][1])):
        rw, rh = 2 * (d + w), d + h
        if x + rw > ancho:
            x, y, fila = 0, y + fila, 0
        uvs[nombre] = (x, y)
        x += rw
        fila = max(fila, rh)
    alto = y + fila
    return uvs, (64 if alto <= 64 else 128)


# ---------------------------------------------------------------- pintura
def ruido(seed, esc):
    """Ruido de valor suave (bilineal + smoothstep), determinista."""

    def g(ix, iy):
        h = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        return (h & 0xFFFF) / 0xFFFF

    def f(x, y):
        x, y = x / esc, y / esc
        ix, iy = math.floor(x), math.floor(y)
        fx, fy = x - ix, y - iy
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = g(ix, iy) + (g(ix + 1, iy) - g(ix, iy)) * fx
        b = g(ix, iy + 1) + (g(ix + 1, iy + 1) - g(ix, iy + 1)) * fx
        return a + (b - a) * fy

    return f


class Lienzo:
    def __init__(self, w, h, seed):
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.exterior = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.idx = {}
        self.detalle = set()  # pixeles pintados encima del material base (heridas, hueso, ropa...)
        self.seed = seed

    def set(self, x, y, rampa, i, detalle=True):
        i = max(0, min(len(rampa) - 1, round(i)))
        self.im.putpixel((x, y), rampa[i] + (255,))
        self.idx[(x, y)] = (rampa, i)
        if detalle:
            self.detalle.add((x, y))
        else:
            self.detalle.discard((x, y))

    def color(self, x, y, c):
        self.im.putpixel((x, y), c + (255,) if len(c) == 3 else c)
        self.idx.pop((x, y), None)
        self.detalle.add((x, y))

    def ajustar(self, x, y, delta):
        """Aclara (+) u oscurece (-) un pixel dentro de su propia rampa."""
        if (x, y) in self.idx:
            rampa, i = self.idx[(x, y)]
            self.set(x, y, rampa, i + delta, detalle=(x, y) in self.detalle)

    def sobresale(self, x, y, rampa=None, i=None):
        """Pone un pixel en la capa exterior: el mismo color de la base o (rampa, indice)."""
        if rampa is None:
            self.exterior.putpixel((x, y), self.im.getpixel((x, y)))
        else:
            i = max(0, min(len(rampa) - 1, round(i)))
            self.exterior.putpixel((x, y), rampa[i] + (255,))

    def hundir(self, rect):
        """Borra la capa exterior en un rectangulo (x, y, ancho, alto) de la textura."""
        x0, y0, rw, rh = rect
        for j in range(rh):
            for i in range(rw):
                self.exterior.putpixel((x0 + i, y0 + j), (0, 0, 0, 0))

    def pintar(self, uv, tam, rampa, base=0.55, var=0.35, grad=0.3, esc=2.5):
        """Material base de una caja: luz arriba, sombra abajo, borde superior iluminado. Devuelve las caras."""
        self.seed += 1
        n1, n2 = ruido(self.seed, esc), ruido(self.seed + 99, 1.0)
        reg = regiones(*uv, *tam)
        for cara, (x0, y0, rw, rh) in reg.items():
            for j in range(rh):
                for i in range(rw):
                    n = n1(x0 + i, y0 + j) * 0.7 + n2(x0 + i, y0 + j) * 0.3 - 0.5
                    if cara == "top":
                        v = base + 0.15 + var * n
                    elif cara == "bottom":
                        v = base - 0.25 + var * n * 0.6
                    else:
                        t = j / max(1, rh - 1)
                        v = base + var * n - grad * (t - 0.35)
                        if j == 0:
                            v += 0.12
                        if i in (0, rw - 1):
                            v -= 0.05
                    self.set(x0 + i, y0 + j, rampa, v * (len(rampa) - 1), detalle=False)
        return reg

    def dibujo(self, rect, filas, leyenda):
        """Pixel art a mano. '.' deja lo que habia; cada letra es (rampa, indice), un color RGB,
        'X' (transparente) o un entero (aclarar/oscurecer lo que hay)."""
        x0, y0 = rect[0], rect[1]
        for j, fila in enumerate(filas):
            for i, ch in enumerate(fila.split()):
                if ch == ".":
                    continue
                val = leyenda[ch]
                if val == "X":
                    self.color(x0 + i, y0 + j, (0, 0, 0, 0))
                elif isinstance(val, int):
                    self.ajustar(x0 + i, y0 + j, val)
                elif isinstance(val[0], list):
                    self.set(x0 + i, y0 + j, val[0], val[1])
                else:
                    self.color(x0 + i, y0 + j, val)

    def rellenar(self, rect, rampa, i):
        x0, y0, rw, rh = rect
        for j in range(rh):
            for k in range(rw):
                self.set(x0 + k, y0 + j, rampa, i)

    def mancha(self, rect, cx, cy, radio, rampa, base=0.6, borde=-1):
        """Mancha organica de otro material (herida, hongo, cristal...) con borde sombreado."""
        self.seed += 1
        n = ruido(self.seed, 1.6)
        x0, y0, rw, rh = rect
        for j in range(rh):
            for i in range(rw):
                d = math.hypot(i + 0.5 - cx, j + 0.5 - cy) + (n(i, j) - 0.5) * 2.2
                if d < radio:
                    v = base + (n(i * 2, j * 2) - 0.5) * 0.4 - (j + 0.5 - cy) / max(radio, 1) * 0.15
                    self.set(
                        x0 + i,
                        y0 + j,
                        rampa,
                        v * (len(rampa) - 1) + (borde if d > radio - 1 else 0),
                    )

    def sombra(self, rect, filas, desde="top", fuerza=2):
        """Oclusion: oscurece las filas pegadas a otra pieza (cuello, cadera, axila)."""
        x0, y0, rw, rh = rect
        for k in range(filas):
            j = k if desde == "top" else rh - 1 - k
            for i in range(rw):
                self.ajustar(x0 + i, y0 + j, -max(1, fuerza - k))

    def costillas(self, rect, x, y, ancho, filas):
        """Costillas con volumen: hueso iluminado arriba, carne en sombra entre medio."""
        for k in range(filas):
            for i in range(ancho):
                self.set(
                    rect[0] + x + i,
                    rect[1] + y + k * 2,
                    HUESO,
                    3 + (i in (1, 2)) - (i == ancho - 1),
                )
                self.set(rect[0] + x + i, rect[1] + y + k * 2 + 1, CARNE, 1 + (i % 3 == 0))

    def columna(self, rect, x, hasta=None):
        """Vertebras expuestas de 2 px de ancho en la cara indicada."""
        x0, y0, rw, rh = rect
        for j in range(0, (hasta or rh) - 1, 2):
            self.set(x0 + x, y0 + j, HUESO, 3)
            self.set(x0 + x + 1, y0 + j, HUESO, 2)
            self.set(x0 + x, y0 + j + 1, HUESO, 1)
            self.set(x0 + x + 1, y0 + j + 1, HUESO, 1)

    def garras(self, rect, filas=1):
        """Ultimas filas de una extremidad: garras/nudillos de hueso alternados con sombra."""
        x0, y0, rw, rh = rect
        for k in range(filas):
            for i in range(rw):
                self.set(x0 + i, y0 + rh - 1 - k, HUESO, (3 - k) if i % 2 == 0 else 0)

    def hueso(self, reg):
        """Pua o cuerno de hueso: claro en la punta, oscuro en la base."""
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = reg[cara]
            for j in range(rh):
                for i in range(rw):
                    self.set(x0 + i, y0 + j, HUESO, 4 - j * 3 / max(1, rh - 1) - (i == rw - 1))
        self.rellenar(reg["top"], HUESO, 4)
        self.rellenar(reg["bottom"], HUESO, 1)

    def cristal(self, reg, rampa):
        """Cristal facetado: arista clara a la izquierda, oscura a la derecha."""
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = reg[cara]
            for j in range(rh):
                for i in range(rw):
                    idx = 2 + (i == 0) - (i == rw - 1) + (j < 2) - (j == rh - 1)
                    self.set(x0 + i, y0 + j, rampa, idx)
        self.rellenar(reg["top"], rampa, 4)
        self.rellenar(reg["bottom"], rampa, 1)


# ---------------------------------------------------------------- capa exterior (relieve)
# Lo que se hunde en la carne se queda en la base: cuencas, bocas, ojos y heridas abiertas.
HUNDIDO = (NEGRO, OJO, CARNE)
UMBRAL_GRUMO = 0.6


def inflado(tam):
    """Cuanto sobresale la capa exterior de una caja (px de modelo): 0.5 como el sombrero del jugador, 0.25 en
    piezas delgadas como sus mangas y 0 en piezas de 1 px (puas, dedos), que no llevan capa.
    El mod usa la misma regla en sus capas exteriores (CapaExterior.java)."""
    grosor = min(tam)
    return 0.5 if grosor >= 4 else 0.25 if grosor >= 2 else 0.0


def relieve_auto(t, uvs, piezas):
    """Capa exterior generada desde la pintura: el detalle que no es hundido sobresale un tono mas claro y el
    material base suelta grumos de podredumbre. Bajo cada grumo de una cara lateral oscurece la base, para que
    el relieve proyecte sombra tambien en el juego, donde no hay oclusion entre capas."""
    grumo = ruido(t.seed + 7, 2.8)
    for nombre, tam in piezas.items():
        if not inflado(tam):
            continue
        for cara, (x0, y0, rw, rh) in regiones(*uvs[nombre], *tam).items():
            for j in range(rh):
                for i in range(rw):
                    x, y = x0 + i, y0 + j
                    if t.im.getpixel((x, y))[3] == 0:
                        continue
                    rampa, k = t.idx.get((x, y), (None, 0))
                    if (x, y) in t.detalle:
                        if rampa is None:
                            t.sobresale(x, y)
                        elif rampa not in HUNDIDO:
                            t.sobresale(x, y, rampa, k + 1)
                    elif rampa is not None and rampa not in HUNDIDO and grumo(x, y) > UMBRAL_GRUMO:
                        t.sobresale(x, y, rampa, k + (cara == "top"))
                        lateral = cara not in ("top", "bottom")
                        if lateral and j + 1 < rh and grumo(x, y + 1) <= UMBRAL_GRUMO:
                            t.ajustar(x, y + 1, -1)


def construir(mod):
    """Pinta la textura base y la capa exterior y arma el modelo de un modulo de diseno.
    Devuelve (textura, modelo, exterior); cada parte trae 'inflado', lo que sobresale su caja exterior."""
    uvs, th = empacar(mod.PIEZAS)
    t = Lienzo(128, th, mod.MOB["seed"])
    mod.pintar(t, lambda n: uvs[n], mod.PIEZAS)
    relieve_auto(t, uvs, mod.PIEZAS)
    if hasattr(mod, "relieve"):
        mod.relieve(t, lambda n: uvs[n], mod.PIEZAS)
    modelo = [
        dict(
            r=p["r"],
            uv=uvs[p["r"]],
            caja=tuple(p["caja"]),
            piv=tuple(p.get("piv", (0, 0, 0))),
            rot=tuple(p.get("rot", (0, 0, 0))),
            inflado=inflado(mod.PIEZAS[p["r"]]),
        )
        for p in mod.partes()
    ]
    for p in modelo:
        x0, y0, z0, x1, y1, z1 = p["caja"]
        esperado = mod.PIEZAS[p["r"]]
        real = (round(x1 - x0), round(y1 - y0), round(z1 - z0))
        if real != tuple(esperado):
            raise ValueError(
                f"{mod.MOB['clave']}: la caja de '{p['r']}' mide {real} pero PIEZAS dice {esperado}"
            )
    return t.im, modelo, t.exterior


# ---------------------------------------------------------------- vista previa (render ortografico con PIL)
def _caras(parte, g=0.0):
    """Caras de la caja de una parte; con g > 0, la caja inflada g px por lado con el mismo UV."""
    x0, y0, z0, x1, y1, z1 = parte["caja"]
    reg = regiones(*parte["uv"], round(x1 - x0), round(y1 - y0), round(z1 - z0))
    x0, y0, z0, x1, y1, z1 = x0 - g, y0 - g, z0 - g, x1 + g, y1 + g, z1 + g
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    return [
        (reg["front"], (0, 0, 1), lambda s, t: (x0 + s * w, y1 - t * h, z1)),
        (reg["back"], (0, 0, -1), lambda s, t: (x1 - s * w, y1 - t * h, z0)),
        (reg["izq"], (1, 0, 0), lambda s, t: (x1, y1 - t * h, z1 - s * d)),
        (reg["der"], (-1, 0, 0), lambda s, t: (x0, y1 - t * h, z0 + s * d)),
        (reg["top"], (0, 1, 0), lambda s, t: (x0 + s * w, y1, z0 + t * d)),
        (reg["bottom"], (0, -1, 0), lambda s, t: (x0 + s * w, y0, z1 - t * d)),
    ]


def render(modelo, tex, yaw, pitch, tam=(520, 560), margen=30, exterior=None):
    """Render ortografico; con exterior (textura de la capa exterior) dibuja tambien las cajas infladas."""
    polis = []
    capas = [(parte, tex, 0.0) for parte in modelo]
    if exterior is not None:
        capas += [(parte, exterior, parte["inflado"]) for parte in modelo if parte.get("inflado")]
    for parte, img, g in capas:
        piv, rot = parte["piv"], parte["rot"]

        def mundo(p, piv=piv, rot=rot):
            local = tuple(a + b for a, b in zip(rotar(p, rot), piv, strict=True))
            return rotar(rotar(local, (0, yaw, 0)), (pitch, 0, 0))

        for (u, v, w, h), n, f in _caras(parte, g):
            nn = rotar(rotar(rotar(n, rot), (0, yaw, 0)), (pitch, 0, 0))
            if nn[2] <= 1e-6:
                continue
            luz = 0.55 + 0.45 * max(0, nn[2] * 0.6 + nn[1] * 0.5 + nn[0] * 0.25)
            for j in range(h):
                for i in range(w):
                    c = img.getpixel((u + i, v + j))
                    if c[3] == 0:
                        continue
                    pts = [
                        mundo(f(a / w, b / h))
                        for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))
                    ]
                    polis.append(
                        (
                            sum(p[2] for p in pts) / 4,
                            pts,
                            tuple(min(255, int(k * luz)) for k in c[:3]),
                        )
                    )
    polis.sort(key=lambda p: p[0])
    xs = [p[0] for _, pts, _ in polis for p in pts]
    ys = [p[1] for _, pts, _ in polis for p in pts]
    escala = min(
        (tam[0] - 2 * margen) / (max(xs) - min(xs)), (tam[1] - 2 * margen) / (max(ys) - min(ys))
    )
    cx = tam[0] / 2 - (min(xs) + max(xs)) / 2 * escala
    cy = tam[1] / 2 + (min(ys) + max(ys)) / 2 * escala
    im = Image.new("RGBA", tam, (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    for _, pts, col in polis:
        dr.polygon([(cx + p[0] * escala, cy - p[1] * escala) for p in pts], fill=col, outline=col)
    return im


FONDO = (24, 24, 30, 255)


def ampliar(tex, k, fondo=(40, 40, 46, 255)):
    """Textura ampliada k veces con 1 px de rejilla entre pixeles; lo transparente queda del color de fondo."""
    tg = Image.new("RGBA", (tex.size[0] * k, tex.size[1] * k), fondo)
    for y in range(tex.size[1]):
        for x in range(tex.size[0]):
            c = tex.getpixel((x, y))
            if c[3]:
                tg.paste(c[:3] + (255,), (x * k, y * k, x * k + k - 1, y * k + k - 1))
    return tg


def lamina(titulo, modelo, tex, archivo, exterior=None):
    """Lamina de revision: frente 3/4, espalda 3/4, la textura ampliada y debajo la de la capa exterior."""
    f1, f2 = ImageFont.load_default(size=30), ImageFont.load_default(size=18)
    vistas = [
        (render(modelo, tex, 32, 14, exterior=exterior), "frente 3/4"),
        (render(modelo, tex, 212, 14, exterior=exterior), "espalda 3/4"),
    ]
    k = 4 if exterior is not None and tex.size[1] > 64 else 8 if exterior is None else 5
    texturas = [(ampliar(tex, k), f"textura {tex.size[0]}x{tex.size[1]} (ampliada x{k})")]
    if exterior is not None:
        texturas.append(
            (ampliar(exterior, k, (30, 30, 36, 255)), "capa exterior (lo pintado sobresale)")
        )
    alto_t = sum(tg.size[1] + 40 for tg, _ in texturas)
    ancho_v = sum(v.size[0] for v, _ in vistas) + 20
    ancho_t = max(tg.size[0] for tg, _ in texturas)
    im = Image.new("RGBA", (40 + ancho_v + 40 + ancho_t + 40, 90 + max(560, alto_t) + 60), FONDO)
    dr = ImageDraw.Draw(im)
    dr.text((40, 25), titulo, fill=(235, 235, 235), font=f1)
    x = 40
    for v, etiqueta in vistas:
        im.alpha_composite(v, (x, 90))
        dr.text((x, 90 + v.size[1] + 10), etiqueta, fill=(170, 170, 180), font=f2)
        x += v.size[0] + 20
    y = 90
    for tg, etiqueta in texturas:
        im.alpha_composite(tg, (x + 20, y))
        dr.text((x + 20, y + tg.size[1] + 8), etiqueta, fill=(170, 170, 180), font=f2)
        y += tg.size[1] + 40
    im.save(archivo)
