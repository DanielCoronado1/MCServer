"""
Diseña las texturas de las variantes de creeper (pixel art 64x32, misma plantilla que el creeper).

Uso:  python tools/generate_creeper_textures.py
Salida: mods/creepers/src/client/resources/assets/dolfincreepers/textures/entity/*.png
        y una vista previa en mods/creepers/vista_previa_texturas.png

Plantilla de la textura (x, y en pixeles):
  Cabeza 8x8x8 en (0,0):   arriba (8,0)  abajo (16,0)  | fila y=8..16: der (0) frente (8) izq (16) atras (24)
  Cuerpo 8x12x4 en (16,16): arriba (20,16) abajo (28,16) | fila y=20..32: der (16) frente (20) izq (28) atras (32)
  Patas 4x6x4 en (0,16):   arriba (4,16) abajo (8,16)   | fila y=20..26: der (0) frente (4) izq (8) atras (12)
"""

import random
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
MOD = RAIZ / "mods" / "creepers"
SALIDA = MOD / "src" / "client" / "resources" / "assets" / "dolfincreepers" / "textures" / "entity"

# Zonas de la textura que usa el modelo (x0, y0, x1, y1)
CABEZA = [(8, 0, 24, 8), (0, 8, 32, 16)]
CUERPO = [(20, 16, 36, 20), (16, 20, 40, 32)]
PATAS = [(4, 16, 12, 20), (0, 20, 16, 26)]
CARA = (8, 8)  # esquina del frente de la cabeza
ESPALDA = (32, 20)  # esquina de la parte de atras del cuerpo (8x12)


def pixeles(zonas):
    for x0, y0, x1, y1 in zonas:
        for y in range(y0, y1):
            for x in range(x0, x1):
                yield x, y


class Lienzo:
    def __init__(self, semilla):
        self.img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        self.azar = random.Random(semilla)

    def pintar(self, x, y, color):
        self.img.putpixel((x, y), tuple(color) + ((255,) if len(color) == 3 else ()))

    def base(self, zonas, paleta, pesos=None):
        """Rellena con colores de la paleta al azar (el moteado tipico del creeper)."""
        for x, y in pixeles(zonas):
            self.pintar(x, y, self.azar.choices(paleta, weights=pesos)[0])

    def motas(self, zonas, color, probabilidad):
        for x, y in pixeles(zonas):
            if self.azar.random() < probabilidad:
                self.pintar(x, y, color)

    def grietas(self, zonas, colores, cantidad, largo):
        """Lineas quebradas (grietas de lava, rayos, vetas...)."""
        todos = list(pixeles(zonas))
        validos = set(todos)
        for _ in range(cantidad):
            x, y = self.azar.choice(todos)
            for paso in range(largo):
                if (x, y) in validos:
                    self.pintar(x, y, colores[0] if paso % 3 else colores[-1])
                x += self.azar.choice([-1, 0, 1])
                y += self.azar.choice([0, 1, 1])

    def limpiar_cara(self, paleta):
        """Repinta el frente de la cabeza solo con la paleta, para que la cara se lea bien."""
        self.base([(CARA[0], CARA[1], CARA[0] + 8, CARA[1] + 8)], paleta)

    def zigzag(self, zonas, color, cantidad, largo):
        """Rayos en zigzag: bajan alternando izquierda y derecha."""
        todos = list(pixeles(zonas))
        validos = set(todos)
        for _ in range(cantidad):
            x, y = self.azar.choice(todos)
            lado = self.azar.choice([-1, 1])
            for paso in range(largo):
                if (x, y) in validos:
                    self.pintar(x, y, color)
                if paso % 2:
                    x += lado
                else:
                    y += 1
                if paso % 4 == 3:
                    lado = -lado

    def rectangulo(self, x0, y0, ancho, alto, color):
        for y in range(y0, y0 + alto):
            for x in range(x0, x0 + ancho):
                self.pintar(x, y, color)

    def cara(self, ojos, boca, estilo="normal", brillo=None):
        """La cara del creeper en el frente de la cabeza (8x8). 'brillo' = centro mas claro."""
        cx, cy = CARA
        if estilo == "grande":  # ojos grandes (Mini)
            puntos_ojos = [
                (1, 1),
                (2, 1),
                (1, 2),
                (2, 2),
                (1, 3),
                (2, 3),
                (5, 1),
                (6, 1),
                (5, 2),
                (6, 2),
                (5, 3),
                (6, 3),
            ]
        elif estilo == "enfadado":  # cejas inclinadas (Rey)
            puntos_ojos = [
                (1, 1),
                (2, 2),
                (1, 2),
                (2, 3),
                (1, 3),
                (6, 1),
                (5, 2),
                (6, 2),
                (5, 3),
                (6, 3),
            ]
        else:
            puntos_ojos = [(1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3)]
        puntos_boca = [
            (3, 4),
            (4, 4),
            (2, 5),
            (3, 5),
            (4, 5),
            (5, 5),
            (2, 6),
            (3, 6),
            (4, 6),
            (5, 6),
            (2, 7),
            (5, 7),
        ]
        for x, y in puntos_ojos:
            self.pintar(cx + x, cy + y, ojos)
        for x, y in puntos_boca:
            self.pintar(cx + x, cy + y, boca)
        if brillo:
            for x, y in [(2, 2), (6, 2), (3, 5), (4, 5)]:
                self.pintar(cx + x, cy + y, brillo)


TODO = CABEZA + CUERPO + PATAS


def fuego():
    l = Lienzo(1)
    l.base(TODO, [(46, 10, 6), (70, 16, 8), (96, 24, 10), (58, 12, 6), (30, 6, 4)], [3, 3, 2, 3, 1])
    l.grietas(TODO, [(255, 120, 20), (255, 210, 60)], 26, 6)  # grietas de lava
    l.motas(TODO, (255, 90, 20), 0.05)
    l.limpiar_cara([(46, 10, 6), (58, 12, 6), (30, 6, 4)])
    l.cara((255, 200, 40), (255, 150, 20), brillo=(255, 250, 170))
    return l


def hielo():
    l = Lienzo(2)
    l.base(TODO, [(176, 218, 240), (150, 198, 232), (206, 234, 250), (122, 170, 214)], [3, 3, 2, 1])
    l.grietas(TODO, [(96, 140, 200), (230, 245, 255)], 14, 5)  # vetas de hielo
    l.motas(TODO, (255, 255, 255), 0.07)  # escarcha
    for x in range(0, 32, 2):  # carambanos bajo la cabeza
        if l.img.getpixel((x, 15))[3]:
            l.pintar(x, 15, (240, 250, 255))
    l.cara((24, 44, 96), (34, 60, 120))
    return l


def saltarin():
    l = Lienzo(3)
    l.base(TODO, [(140, 222, 60), (112, 192, 40), (172, 240, 92), (90, 160, 30)], [3, 3, 2, 2])
    for x, y in pixeles(PATAS):  # muelles en las patas
        if y >= 20 and y % 2 == 0:
            l.pintar(x, y, (225, 225, 225) if x % 2 else (160, 160, 160))
    l.cara((16, 40, 8), (16, 40, 8))
    return l


def mini():
    l = Lienzo(4)
    l.base(TODO, [(96, 230, 96), (70, 200, 70), (140, 245, 140), (54, 170, 54)], [3, 3, 2, 1])
    l.cara((10, 20, 10), (10, 20, 10), estilo="grande")
    # Brillo en los ojos para que se vea "tierno".
    l.pintar(CARA[0] + 1, CARA[1] + 1, (255, 255, 255))
    l.pintar(CARA[0] + 5, CARA[1] + 1, (255, 255, 255))
    return l


def electrico():
    l = Lienzo(5)
    l.base(TODO, [(242, 212, 40), (222, 182, 20), (252, 232, 96), (200, 160, 16)], [3, 3, 2, 1])
    l.zigzag(TODO, (34, 30, 24), 16, 8)  # rayos negros en zigzag
    l.motas(TODO, (255, 255, 230), 0.04)
    l.limpiar_cara([(242, 212, 40), (222, 182, 20), (252, 232, 96)])
    l.cara((20, 20, 20), (20, 20, 20))
    l.pintar(CARA[0] + 1, CARA[1] + 2, (120, 220, 255))  # chispas en los ojos
    l.pintar(CARA[0] + 6, CARA[1] + 2, (120, 220, 255))
    return l


def multiplicador():
    l = Lienzo(6)
    l.base(
        TODO, [(80, 196, 150), (60, 170, 128), (110, 216, 172)], [3, 3, 2]
    )  # verde azulado, como slime
    todos = list(pixeles(TODO))
    validos = set(todos)
    for _ in range(22):  # celulas dividiendose
        x, y = l.azar.choice(todos)
        for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1)]:
            if (x + dx, y + dy) in validos:
                l.pintar(x + dx, y + dy, (28, 110, 84) if (dx + dy) % 2 else (190, 250, 220))
    l.limpiar_cara([(80, 196, 150), (60, 170, 128), (110, 216, 172)])
    l.cara((16, 70, 50), (16, 70, 50))
    return l


def gigante():
    l = Lienzo(7)
    l.base(TODO, [(92, 112, 80), (72, 92, 62), (112, 126, 96), (60, 76, 50)], [3, 3, 2, 2])
    l.grietas(TODO, [(42, 48, 36), (30, 34, 26)], 20, 6)  # piedra agrietada
    todos = list(pixeles(TODO))
    for _ in range(14):  # manchas de musgo
        x, y = l.azar.choice(todos)
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if (x + dx, y + dy) in set(todos) and l.azar.random() < 0.7:
                    l.pintar(x + dx, y + dy, l.azar.choice([(62, 140, 50), (80, 160, 60)]))
    l.limpiar_cara([(92, 112, 80), (72, 92, 62), (112, 126, 96)])
    l.cara((14, 16, 12), (14, 16, 12))
    return l


def toxico():
    l = Lienzo(8)
    l.base(TODO, [(72, 42, 92), (52, 30, 72), (92, 56, 112), (60, 80, 50)], [3, 3, 2, 1])
    todos = list(pixeles(TODO))
    for _ in range(30):  # burbujas toxicas
        x, y = l.azar.choice(todos)
        l.pintar(x, y, (130, 255, 80))
        if (x + 1, y) in set(todos):
            l.pintar(x + 1, y, (80, 200, 50))
    l.cara((150, 255, 90), (120, 230, 70), brillo=(220, 255, 180))
    return l


def rey():
    l = Lienzo(9)
    l.base(TODO, [(34, 96, 34), (44, 116, 44), (26, 74, 26), (56, 132, 50)], [3, 3, 2, 1])
    oro, oro_oscuro, rubi = (236, 192, 40), (180, 132, 20), (210, 30, 40)
    # Corona: las dos filas de arriba de los cuatro lados de la cabeza, con picos y rubies.
    for x in range(0, 32):
        l.pintar(x, 8, oro)
        l.pintar(x, 9, oro_oscuro if x % 4 == 0 else oro)
    for x in range(0, 32, 4):
        l.pintar(x + 1, 9, rubi)
    for x in range(8, 16):  # aro de la corona arriba
        l.pintar(x, 0, oro)
        l.pintar(x, 7, oro)
    for y in range(8):
        l.pintar(8, y, oro)
        l.pintar(15, y, oro)
    # Capa roja en la espalda del cuerpo, con borde dorado.
    ex, ey = ESPALDA
    l.rectangulo(ex, ey, 8, 12, (150, 20, 32))
    for y in range(ey, ey + 12):
        l.pintar(ex, y, oro)
        l.pintar(ex + 7, y, oro)
    for x in range(ex + 1, ex + 7, 2):
        l.pintar(x, ey + 11, (110, 12, 24))
    l.cara((220, 30, 30), (16, 40, 16), estilo="enfadado")
    return l


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
