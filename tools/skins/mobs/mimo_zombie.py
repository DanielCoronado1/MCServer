# ruff: noqa: F403, F405
"""Mimo: aparece donde murio un jugador e imita sus gestos. El maquillaje blanco se le esta cayendo a pedazos y
debajo hay carne podrida; la sonrisa pintada es demasiado ancha, llora tinta negra y empuja una pared invisible."""

from _zombies import *
from motor import *

MOB = dict(clave="mimo_zombie", grupo="Zombies (mod zombies)", nombre="Mimo", escala=1.0, seed=915)

PIEZAS = dict(HUMANOIDE, boina=(9, 2, 9), rabito=(1, 1, 1), dedos=(4, 3, 1))

CABEZA = (0, 0, 14)


def partes():
    S = humanoide(brazo_d=(-150, 0, 10), brazo_i=(-140, 0, -14), cabeza=CABEZA)
    S += [
        dict(
            r="boina",
            caja=(-4.5, 7.5, -4.5, 4.5, 9.5, 4.5),
            piv=(0, 24, 0),
            rot=(CABEZA[0], 0, CABEZA[2] - 12),
        ),
        dict(
            r="rabito",
            caja=(-0.5, 9.5, -0.5, 0.5, 10.5, 0.5),
            piv=(0, 24, 0),
            rot=(CABEZA[0], 0, CABEZA[2] - 12),
        ),
    ]
    for x, rot in (
        (-6, (-150, 0, 10)),
        (6, (-140, 0, -14)),
    ):  # manos abiertas contra la pared invisible
        S.append(
            dict(
                r="dedos",
                caja=(-2, -3, -0.5, 2, 0, 0.5),
                piv=en((x, 22, 0), rot, (0, -10, 1.5)),
                rot=(rot[0] + 90, 0, rot[2]),
            )
        )
    return S


BLANCO = [(120, 116, 118), (170, 166, 166), (210, 206, 202), (234, 230, 226), (250, 248, 244)]


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], BLANCO, base=0.75, var=0.15)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". K . . . . K .",
            "K K K . . K K K",
            "K R K . . K R K",
            ". t . p p . t .",
            "c . p . . p . c",
            "c W K K K K W c",
            ". c c W W c c .",
        ],
        dict(CARA, t=(NEGRO, 1), p=(PIEL, 2)),
    )  # sonrisa pintada hasta las orejas; donde se cae el maquillaje, piel
    t.dibujo(
        cab["front"],
        [". . . . . . . ."] * 4 + [". t . . . . t .", ". t . . . . . .", ". . . . . . . ."],
        {"t": (NEGRO, 1)},
    )  # lagrimas de tinta
    for cara, cx, cy in (("izq", 3, 3), ("der", 5, 5), ("top", 2, 6)):
        t.mancha(cab[cara], cx, cy, 2, PIEL, 0.45)
    t.pintar(uv("boina"), pz["boina"], CARBON, base=0.5)
    t.pintar(uv("rabito"), pz["rabito"], CARBON, base=0.5)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], BLANCO, base=0.7, var=0.15)
    for cara in LADOS:
        x0, y0, rw, rh = cue[cara]
        for j in range(0, rh - 2, 2):
            for i in range(rw):
                t.set(x0 + i, y0 + j, CARBON, 1)  # rayas
        if cara in ("front", "back"):
            for j in range(rh):
                t.set(x0 + 1, y0 + j, TELA_ROJA, 2)
                t.set(x0 + rw - 2, y0 + j, TELA_ROJA, 2)  # tirantes
    t.mancha(cue["front"], 4, 6, 1.8, CARNE, 0.35)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], BLANCO, base=0.7, var=0.15)
        for cara in LADOS:
            x0, y0, rw, rh = br[cara]
            for j in range(0, 7, 2):
                for i in range(rw):
                    t.set(x0 + i, y0 + j, CARBON, 1)
            for j in range(8, rh):  # guantes blancos con los dedos rotos
                for i in range(rw):
                    t.set(x0 + i, y0 + j, BLANCO, 3 + (j == 8))
            t.set(x0 + 1, y0 + rh - 1, CARNE, 1)
    pi = t.pintar(uv("pierna"), pz["pierna"], CARBON, base=0.4)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, NEGRO, 2)
    de = t.pintar(uv("dedos"), pz["dedos"], BLANCO, base=0.75, var=0.1)
    for cara in ("front", "back"):
        t.dibujo(de[cara], [". . . .", ". X . X", "X . X ."], {"X": "X"})  # dedos separados
        t.dibujo(de[cara], ["c . . .", ". . . .", ". . . ."], {"c": (CARNE, 1)})
