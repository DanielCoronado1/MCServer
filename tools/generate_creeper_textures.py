"""
Diseña las texturas de las variantes de creeper (pixel art 64x32, misma plantilla que el creeper).

Uso:  python tools/generate_creeper_textures.py
Salida: mods/creepers/src/client/resources/assets/dolfincreepers/textures/entity/*.png
        y una vista previa en mods/creepers/vista_previa_texturas.png

Plantilla de la textura (x, y en pixeles):
  Cabeza 8x8x8 en (0,0):   arriba (8,0)  abajo (16,0)  | fila y=8..16: der (0) frente (8) izq (16) atras (24)
  Cuerpo 8x12x4 en (16,16): arriba (20,16) abajo (28,16) | fila y=20..32: der (16) frente (20) izq (28) atras (32)
  Patas 4x6x4 en (0,16):   arriba (4,16) abajo (8,16)   | fila y=20..26: der (0) frente (4) izq (8) atras (12)

Cada textura parte de una piel sombreada con una rampa de cuatro tonos (manchas agrupadas, luz
desde arriba) y encima se dibujan los detalles propios de la variante. La cara (frente de la
cabeza) queda protegida para que los detalles nunca la tapen.
"""

import random
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
MOD = RAIZ / "mods" / "creepers"
SALIDA = MOD / "src" / "client" / "resources" / "assets" / "dolfincreepers" / "textures" / "entity"


@dataclass(frozen=True)
class Cara:
    """Una cara de una caja del modelo en la textura: rectangulo (x0, y0, x1, y1) y orientacion."""

    x0: int
    y0: int
    x1: int
    y1: int
    tipo: str  # "arriba", "abajo" o "lado"

    @property
    def ancho(self):
        return self.x1 - self.x0

    @property
    def alto(self):
        return self.y1 - self.y0

    def pixeles(self):
        for y in range(self.y0, self.y1):
            for x in range(self.x0, self.x1):
                yield x, y


def caja(x, y, ancho, alto, fondo):
    """Caras de una caja del modelo con origen UV (x, y), en el orden en que las coloca Minecraft:
    arriba, abajo, derecha, frente, izquierda y atras."""
    fila = y + fondo
    return [
        Cara(x + fondo, y, x + fondo + ancho, fila, "arriba"),
        Cara(x + fondo + ancho, y, x + fondo + 2 * ancho, fila, "abajo"),
        Cara(x, fila, x + fondo, fila + alto, "lado"),
        Cara(x + fondo, fila, x + fondo + ancho, fila + alto, "lado"),
        Cara(x + fondo + ancho, fila, x + 2 * fondo + ancho, fila + alto, "lado"),
        Cara(x + 2 * fondo + ancho, fila, x + 2 * (fondo + ancho), fila + alto, "lado"),
    ]


CABEZA = caja(0, 0, 8, 8, 8)
CUERPO = caja(16, 16, 8, 12, 4)
PATAS = caja(0, 16, 4, 6, 4)
TODAS = CABEZA + CUERPO + PATAS
LADOS = [c for c in TODAS if c.tipo == "lado"]
ARRIBAS = [c for c in TODAS if c.tipo == "arriba"]
FRENTE = CABEZA[3]  # frente de la cabeza: la cara del creeper
PECHO = CUERPO[3]
ESPALDA = CUERPO[5]

# Caras del creeper (8x8). E ojo, e interior del ojo, M boca, m interior de la boca,
# B ceja, H brillo, C mejilla; '.' deja la piel.
CARA_NORMAL = (
    "........",
    "........",
    ".EE..EE.",
    ".Ee..eE.",
    "...MM...",
    "..MmmM..",
    "..MmmM..",
    "..M..M..",
)
CARA_TIERNA = (
    "........",
    ".HE..HE.",
    ".EE..EE.",
    ".Ee..eE.",
    "...MM...",
    "C.MmmM.C",
    "..MmmM..",
    "..M..M..",
)
CARA_ENFADADA = (
    "........",
    ".B....B.",
    ".EB..BE.",
    ".Ee..eE.",
    "...MM...",
    "..MmmM..",
    "..MmmM..",
    "..M..M..",
)


class Lienzo:
    """Textura 64x32 en construccion con su propio generador aleatorio (resultado reproducible)."""

    def __init__(self, semilla):
        self.img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        self.azar = random.Random(semilla)
        self.validos = {p for c in TODAS for p in c.pixeles()}
        self.cara = set(FRENTE.pixeles())

    def pintar(self, x, y, color):
        self.img.putpixel((x, y), tuple(color) + ((255,) if len(color) == 3 else ()))

    def libre(self, x, y):
        """Pixel usado por el modelo y fuera de la cara."""
        return (x, y) in self.validos and (x, y) not in self.cara

    def detalle(self, x, y, color):
        """Pinta solo si el pixel es libre; los detalles nunca tapan la cara."""
        if self.libre(x, y):
            self.pintar(x, y, color)

    def rectangulo(self, x0, y0, ancho, alto, color):
        for y in range(y0, y0 + alto):
            for x in range(x0, x0 + ancho):
                self.pintar(x, y, color)

    def punto_en(self, cara, margen=0):
        """Un pixel al azar dentro de una cara, dejando 'margen' pixeles a cada lado."""
        x = self.azar.randint(cara.x0 + margen, max(cara.x0 + margen, cara.x1 - 1 - margen))
        y = self.azar.randint(cara.y0 + margen, max(cara.y0 + margen, cara.y1 - 1 - margen))
        return x, y


def piel(lienzo, rampa, pesos=(0.18, 0.32, 0.32, 0.18)):
    """
    Rellena todo el modelo con la rampa de colores (de oscuro a claro).

    Usa ruido suavizado dentro de cada cara para formar manchas como el creeper original, suma
    luz desde arriba (caras de arriba mas claras, de abajo mas oscuras, lados que oscurecen hacia
    abajo) y reparte los tonos por percentiles para que toda la rampa aparezca siempre.
    """
    valores = {}
    for cara in TODAS:
        valores.update(_ruido_cara(lienzo, cara))
    orden = sorted(valores, key=valores.get)
    limites = _limites(len(orden), pesos)
    for posicion, (x, y) in enumerate(orden):
        tono = sum(posicion >= limite for limite in limites)
        lienzo.pintar(x, y, rampa[tono])


def _ruido_cara(lienzo, cara):
    """Ruido de una cara suavizado con sus vecinos y con la luz de su orientacion."""
    crudo = {p: lienzo.azar.random() for p in cara.pixeles()}
    luz = {"arriba": 0.12, "abajo": -0.15}
    valores = {}
    for (x, y), v in crudo.items():
        vecinos = [crudo[q] for q in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)) if q in crudo]
        suave = (2 * v + sum(vecinos)) / (2 + len(vecinos))
        degradado = luz.get(cara.tipo, 0.08 - 0.16 * (y - cara.y0) / max(1, cara.alto - 1))
        valores[x, y] = suave + degradado
    return valores


def _limites(total, pesos):
    acumulado, limites = 0.0, []
    for peso in pesos[:-1]:
        acumulado += peso
        limites.append(round(acumulado * total))
    return limites


def rostro(lienzo, plantilla, colores):
    """Dibuja la cara (plantilla 8x8) en el frente de la cabeza; 'colores' traduce cada letra."""
    for fila, linea in enumerate(plantilla):
        for columna, letra in enumerate(linea):
            if letra in colores:
                lienzo.pintar(FRENTE.x0 + columna, FRENTE.y0 + fila, colores[letra])


def grieta(lienzo, cara, largo, nucleo, halo=None):
    """Linea quebrada que baja por una cara (lava, vetas); 'halo' tiñe sus bordes."""
    x, y = lienzo.punto_en(cara, 1)
    trazo = []
    for _ in range(largo):
        if not (cara.x0 <= x < cara.x1 and cara.y0 <= y < cara.y1):
            break
        trazo.append((x, y))
        x += lienzo.azar.choice([-1, 0, 0, 1])
        y += lienzo.azar.choice([0, 1, 1])
    if halo:
        for px, py in trazo:
            for q in ((px - 1, py), (px + 1, py)):
                if q not in trazo:
                    lienzo.detalle(*q, halo)
    for i, (px, py) in enumerate(trazo):
        lienzo.detalle(px, py, nucleo[i % len(nucleo)])


def rayo(lienzo, cara, color, brillo, chispa):
    """Rayo que baja en tramos verticales con saltos de lado, con un brillo a su derecha y una
    chispa en la punta."""
    x, y = lienzo.punto_en(cara, 1)
    y = cara.y0 + lienzo.azar.randint(0, 1)
    while True:
        for _ in range(lienzo.azar.randint(2, 3)):
            if y >= cara.y1 - 1:
                lienzo.detalle(x, y, chispa)
                return
            lienzo.detalle(x, y, color)
            lienzo.detalle(x + 1, y, brillo)
            y += 1
        paso = lienzo.azar.choice([-1, 1])
        if not cara.x0 <= x + 2 * paso < cara.x1:
            paso = -paso
        for _ in range(2):
            x += paso
            lienzo.detalle(x, y, color)


def goteo(lienzo, cara, color, punta, maximo):
    """Chorreones que caen desde el borde de arriba de un lado, con una gota distinta al final."""
    for x in range(cara.x0, cara.x1):
        largo = lienzo.azar.choice([0, 1, 1, 2, max(2, maximo // 2), maximo])
        for y in range(cara.y0, cara.y0 + largo):
            lienzo.detalle(x, y, color)
        if largo > 1:
            lienzo.detalle(x, cara.y0 + largo, punta)


def cristal(lienzo, x, y, centro, brazos):
    """Cristal de escarcha en cruz."""
    lienzo.detalle(x, y, centro)
    for q in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
        lienzo.detalle(*q, brazos)


def celula(lienzo, x, y, colores):
    """Celula de 4x4 con las esquinas redondeadas; 'colores' = (membrana, interior, nucleo)."""
    membrana, interior, nucleo = colores
    for dy in range(4):
        for dx in range(4):
            if dx in (0, 3) and dy in (0, 3):
                continue
            borde = dx in (0, 3) or dy in (0, 3)
            lienzo.detalle(x + dx, y + dy, membrana if borde else interior)
    lienzo.detalle(x + 1, y + 1, nucleo)


def fuego():
    lienzo = Lienzo(1)
    piel(lienzo, [(30, 12, 10), (50, 20, 14), (72, 30, 20), (98, 44, 28)])
    lava = [(255, 150, 30), (255, 110, 20), (255, 220, 90)]
    for cara in LADOS:  # grietas de lava con borde al rojo
        for _ in range(max(1, cara.ancho // 4)):
            grieta(lienzo, cara, lienzo.azar.randint(3, 7), lava, (130, 34, 14))
    for cara in ARRIBAS:  # brasas
        for _ in range(3):
            lienzo.detalle(*lienzo.punto_en(cara), (255, 200, 70))
    for x in range(0, 16):  # pies al rojo vivo
        lienzo.detalle(x, 25, (150, 40, 16) if x % 3 else (230, 90, 20))
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (255, 130, 20), "e": (255, 230, 120), "M": (210, 70, 10), "m": (255, 180, 50)},
    )
    return lienzo


def hielo():
    lienzo = Lienzo(2)
    piel(lienzo, [(92, 146, 204), (134, 186, 230), (176, 216, 244), (218, 240, 255)])
    for cara in ARRIBAS:  # nieve arriba que escarcha el borde de los lados
        for x, y in cara.pixeles():
            if lienzo.azar.random() < 0.55:
                lienzo.detalle(x, y, (240, 250, 255))
    for cara in LADOS:
        goteo(lienzo, cara, (236, 248, 255), (200, 230, 250), 3)
    for cara in LADOS:  # cristales y reflejos diagonales
        if cara.ancho >= 8:
            cristal(lienzo, *lienzo.punto_en(cara, 2), (255, 255, 255), (198, 232, 252))
        x, y = lienzo.punto_en(cara, 1)
        for i in range(3):
            lienzo.detalle(x + i, y + 2 - i, (232, 246, 255))
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (24, 44, 98), "e": (70, 120, 190), "M": (24, 44, 98), "m": (12, 24, 62)},
    )
    return lienzo


def electrico():
    lienzo = Lienzo(5)
    piel(lienzo, [(190, 140, 16), (222, 178, 24), (244, 208, 44), (255, 236, 118)])
    for cara in LADOS:  # rayos oscuros con chispa azul
        if cara.ancho >= 4:
            rayo(lienzo, cara, (44, 36, 30), (255, 250, 210), (110, 220, 255))
    for cara in ARRIBAS:
        lienzo.detalle(*lienzo.punto_en(cara), (150, 235, 255))
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (24, 22, 20), "e": (110, 220, 255), "M": (24, 22, 20), "m": (44, 40, 34)},
    )
    return lienzo


def saltarin():
    lienzo = Lienzo(3)
    piel(lienzo, [(76, 148, 26), (106, 184, 38), (140, 216, 58), (180, 240, 100)])
    muelle = [(214, 218, 226), (150, 156, 168), (96, 100, 112)]
    for cara in PATAS[2:]:  # muelles en las patas, con brillo en un borde
        for x, y in cara.pixeles():
            if y > cara.y0:
                tono = 2 if (y - cara.y0) % 2 == 0 else (0 if x == cara.x0 else 1)
                lienzo.pintar(x, y, muelle[tono])
    abajo = PATAS[1]
    lienzo.rectangulo(abajo.x0, abajo.y0, abajo.ancho, abajo.alto, muelle[1])
    flecha = (52, 110, 18)
    for inicio in (22, 27):  # flechas hacia arriba en la espalda
        for i in range(4):
            lienzo.detalle(ESPALDA.x0 + 3 - i, inicio + i, flecha)
            lienzo.detalle(ESPALDA.x0 + 4 + i, inicio + i, flecha)
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (18, 42, 8), "e": (34, 70, 14), "M": (18, 42, 8), "m": (10, 26, 4)},
    )
    return lienzo


def mini():
    lienzo = Lienzo(4)
    piel(lienzo, [(56, 170, 54), (80, 202, 74), (110, 228, 98), (156, 246, 142)])
    for cara in ARRIBAS:  # manchitas claras de cria
        for _ in range(2):
            lienzo.detalle(*lienzo.punto_en(cara), (196, 255, 180))
    rostro(
        lienzo,
        CARA_TIERNA,
        {
            "E": (12, 24, 12),
            "e": (30, 56, 30),
            "H": (255, 255, 255),
            "M": (12, 24, 12),
            "m": (120, 40, 50),
            "C": (255, 150, 168),
        },
    )
    return lienzo


def multiplicador():
    lienzo = Lienzo(6)
    piel(lienzo, [(42, 136, 106), (62, 166, 128), (86, 196, 152), (128, 222, 184)])
    colores = (24, 96, 72), (176, 242, 212), (40, 130, 96)
    for cara in LADOS + ARRIBAS:  # celulas, algunas dividiendose en dos
        if cara.ancho < 8 or cara.alto < 4:
            continue
        x, y = lienzo.punto_en(cara)
        x, y = min(x, cara.x1 - 4), min(y, cara.y1 - 4)
        celula(lienzo, x, y, colores)
        if lienzo.azar.random() < 0.5 and x + 7 <= cara.x1:
            celula(lienzo, x + 3, y, colores)
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (14, 66, 48), "e": (176, 242, 212), "M": (14, 66, 48), "m": (8, 42, 30)},
    )
    return lienzo


def toxico():
    lienzo = Lienzo(8)
    piel(lienzo, [(44, 22, 62), (62, 34, 84), (84, 48, 108), (108, 66, 132)])
    baba = [(96, 200, 50), (124, 230, 66), (176, 255, 110)]
    for cara in ARRIBAS:  # charco toxico arriba que chorrea por los lados
        for x, y in cara.pixeles():
            if lienzo.azar.random() < 0.4:
                lienzo.detalle(x, y, lienzo.azar.choice(baba[:2]))
    for cara in LADOS:
        goteo(lienzo, cara, baba[0], baba[2], 4)
    for cara in LADOS:  # burbujas
        x, y = lienzo.punto_en(cara, 1)
        lienzo.detalle(x, y, (200, 255, 150))
        lienzo.detalle(x + 1, y + 1, baba[0])
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (124, 240, 70), "e": (220, 255, 170), "M": (80, 180, 40), "m": (160, 255, 100)},
    )
    return lienzo


def gigante():
    lienzo = Lienzo(7)
    piel(lienzo, [(66, 74, 60), (86, 96, 76), (106, 116, 94), (130, 138, 114)])
    mortero = (46, 52, 42)
    for cara in LADOS:  # bloques de piedra grandes, acordes a su tamano
        for x, y in cara.pixeles():
            fila = (y - cara.y0) // 4
            junta = (x - cara.x0 + 3 * (fila % 2)) % 6 == 5
            if (y - cara.y0) % 4 == 3 or junta:
                lienzo.detalle(x, y, mortero)
    for cara in LADOS:
        if cara.ancho >= 8:
            grieta(lienzo, cara, 5, [(36, 40, 32)])
    musgo = [(58, 120, 44), (78, 150, 56), (98, 172, 66)]
    for cara in ARRIBAS:  # musgo arriba que cuelga por los lados
        for x, y in cara.pixeles():
            if lienzo.azar.random() < 0.75:
                lienzo.detalle(x, y, lienzo.azar.choice(musgo))
    for cara in LADOS:
        goteo(lienzo, cara, musgo[0], musgo[2], 4)
    rostro(
        lienzo,
        CARA_NORMAL,
        {"E": (14, 16, 12), "e": (34, 40, 30), "M": (14, 16, 12), "m": (24, 28, 20)},
    )
    return lienzo


def rey():
    lienzo = Lienzo(9)
    piel(lienzo, [(22, 68, 26), (32, 90, 34), (44, 112, 44), (62, 136, 56)])
    oro = [(150, 104, 16), (212, 164, 32), (246, 210, 70)]
    corona(lienzo, oro)
    capa(lienzo, oro)
    banda(lienzo, oro)
    for x in range(0, 16):  # punos dorados en las patas
        lienzo.pintar(x, 25, oro[1] if x % 4 else oro[0])
    rostro(
        lienzo,
        CARA_ENFADADA,
        {
            "E": (230, 30, 30),
            "e": (255, 150, 90),
            "B": (10, 30, 12),
            "M": (12, 34, 14),
            "m": (120, 14, 20),
        },
    )
    return lienzo


def corona(lienzo, oro):
    """Aro de oro con rubies en el borde de arriba de la cabeza y terciopelo rojo encima."""
    rubi = (210, 28, 44)
    for x in range(0, 32):
        lienzo.pintar(x, 8, oro[2] if x % 4 in (1, 2) else oro[1])
        if x % 4 == 0:
            lienzo.detalle(x, 9, oro[0])
    for x in (3, 11, 12, 19, 27):
        lienzo.pintar(x, 8, rubi)
    tapa = CABEZA[0]
    lienzo.rectangulo(tapa.x0, tapa.y0, tapa.ancho, tapa.alto, (130, 18, 30))
    for x, y in tapa.pixeles():
        if x in (tapa.x0, tapa.x1 - 1) or y in (tapa.y0, tapa.y1 - 1):
            lienzo.pintar(x, y, oro[1] if (x + y) % 2 else oro[2])
    lienzo.rectangulo(tapa.x0 + 3, tapa.y0 + 3, 2, 2, oro[2])  # joya central
    lienzo.pintar(tapa.x0 + 3, tapa.y0 + 3, rubi)


def capa(lienzo, oro):
    """Capa roja con pliegues y cuello de armiño en la espalda; asoma por los costados."""
    rojo = [(96, 10, 22), (140, 18, 32), (176, 30, 44)]
    for cara in (ESPALDA, CUERPO[2], CUERPO[4]):
        for x, y in cara.pixeles():
            if cara is ESPALDA or y >= 26:
                pliegue = (x - cara.x0) % 3
                lienzo.pintar(x, y, rojo[2] if pliegue == 1 else rojo[1 if pliegue else 0])
    for y in range(ESPALDA.y0, ESPALDA.y1):
        lienzo.pintar(ESPALDA.x0, y, oro[1])
        lienzo.pintar(ESPALDA.x1 - 1, y, oro[1])
    for x in range(ESPALDA.x0, ESPALDA.x1):  # armiño: blanco con motas negras
        for y in (20, 21):
            lienzo.pintar(x, y, (24, 22, 26) if (x + 2 * y) % 4 == 0 else (236, 232, 224))


def banda(lienzo, oro):
    """Banda real en diagonal sobre el pecho, con borde de oro."""
    for i in range(PECHO.alto):
        x, y = PECHO.x0 + 1 + (i * 5) // 11, PECHO.y0 + i
        lienzo.pintar(x - 1, y, oro[1])
        lienzo.pintar(x, y, (176, 30, 44))
        lienzo.pintar(x + 1, y, (140, 18, 32))
        lienzo.pintar(x + 2, y, oro[0])


VARIANTES = {
    "fuego_creeper": fuego,
    "hielo_creeper": hielo,
    "saltarin_creeper": saltarin,
    "mini_creeper": mini,
    "electrico_creeper": electrico,
    "multiplicador_creeper": multiplicador,
    "gigante_creeper": gigante,
    "toxico_creeper": toxico,
    "rey_creeper": rey,
}


def vista_previa(texturas):
    """Dibuja cada creeper visto de frente y de espaldas (cabeza, cuerpo y patas), ampliado."""
    escala, ancho = 8, 20
    hoja = Image.new("RGBA", (ancho * escala * len(texturas), 30 * escala), (40, 44, 52, 255))
    for i, img in enumerate(texturas.values()):
        frente = Image.new("RGBA", (ancho, 30), (0, 0, 0, 0))
        frente.paste(img.crop((8, 8, 16, 16)), (1, 0))  # cabeza frente
        frente.paste(img.crop((20, 20, 28, 32)), (1, 8))  # cuerpo frente
        frente.paste(img.crop((4, 20, 8, 26)), (1, 20))  # patas frente
        frente.paste(img.crop((4, 20, 8, 26)), (5, 20))
        frente.paste(img.crop((24, 8, 32, 16)), (11, 0))  # cabeza atras
        frente.paste(img.crop((32, 20, 40, 32)), (11, 8))  # cuerpo atras
        frente.paste(img.crop((12, 20, 16, 26)), (11, 20))
        frente.paste(img.crop((12, 20, 16, 26)), (15, 20))
        hoja.alpha_composite(
            frente.resize((ancho * escala, 30 * escala), Image.NEAREST), (i * ancho * escala, 0)
        )
    hoja.save(MOD / "vista_previa_texturas.png")


if __name__ == "__main__":
    SALIDA.mkdir(parents=True, exist_ok=True)
    texturas = {}
    for nombre, disenar in VARIANTES.items():
        texturas[nombre] = disenar().img
        texturas[nombre].save(SALIDA / f"{nombre}.png")
        print("  ", nombre)
    vista_previa(texturas)
    print("Vista previa:", MOD / "vista_previa_texturas.png")
