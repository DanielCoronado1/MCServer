# ruff: noqa: F403, F405
"""Bombero: el incendio lo alcanzo y nunca dejo de arder por dentro. Casco de bombero derretido, tanque
de aire reventado en la espalda que escupe brasas y la carne calcinada con grietas de fuego. Quema bloques."""

from _zombies import *
from motor import *

MOB = dict(
    clave="bombero_zombie", grupo="Zombies (mod zombies)", nombre="Bombero", escala=1.0, seed=903
)

PIEZAS = dict(
    HUMANOIDE,
    casco=(9, 4, 10),
    cresta=(1, 3, 6),
    visera=(9, 1, 3),
    tanque=(4, 9, 3),
    boquilla=(2, 2, 2),
)

CABEZA = (0, 0, 6)


def partes():
    S = humanoide(brazo_d=(-85, 0, 0), brazo_i=(-92, 0, 0), cabeza=CABEZA)
    S += [
        dict(r="casco", caja=(-4.5, 5, -5, 4.5, 9, 5), piv=(0, 24, 0), rot=CABEZA),
        dict(r="cresta", caja=(-0.5, 9, -3, 0.5, 12, 3), piv=(0, 24, 0), rot=CABEZA),
        dict(
            r="visera",
            caja=(-4.5, 5, -7, 4.5, 6, -4),
            piv=(0, 24, 0),
            rot=(CABEZA[0] - 15, 0, CABEZA[2]),
        ),
        dict(r="tanque", caja=(-2, 13, -5, 2, 22, -2), rot=(0, 0, 0)),
        dict(r="boquilla", caja=(-1, -1, -1, 1, 1, 1), piv=(-1.5, 22.5, -3.5), rot=(-30, 0, 20)),
    ]
    return S


def fuego(t, rect, puntos):
    for cx, cy, r in puntos:
        t.mancha(rect, cx, cy, r, MAGMA, 0.65, -1)


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], CARBON, base=0.55)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". . . . . . . .",
            ". k . . . . k .",
            "K K K . . K K K",
            "K M K . . K M K",
            ". s . m m . s .",
            ". . K K K K . .",
            ". . K m M m . .",
        ],
        dict(CARA, M=(MAGMA, 4), m=(MAGMA, 2)),
    )  # ojos y boca con brasa adentro
    fuego(t, cab["der"], ((5, 5, 1.6),))
    cas = t.pintar(uv("casco"), pz["casco"], TELA_ROJA, base=0.55, grad=0.4)
    for cara in ("front", "der", "izq", "back"):  # derretido: chorreones negros
        x0, y0, rw, rh = cas[cara]
        for i in range(0, rw, 3):
            for j in range(rh - 1 - (i % 2), rh):
                t.set(x0 + i, y0 + j, CARBON, 1)
    t.dibujo(
        cas["front"],
        [". . . . . . . . .", ". . . o o o . . .", ". . . o K o . . .", ". . . o o o . . ."],
        {"o": (ORO, 2), "K": (CARBON, 1)},
    )  # placa con el numero quemado
    t.pintar(uv("cresta"), pz["cresta"], TELA_ROJA, base=0.6)
    t.pintar(uv("visera"), pz["visera"], CARBON, base=0.45)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CAFE, base=0.4)
    for cara in ("front", "back"):
        x0, y0, rw, rh = cue[cara]
        for i in range(rw):  # cinta reflectante sucia
            t.set(x0 + i, y0 + 7, AMARILLO, 2 + (i % 3 == 0))
            t.set(x0 + i, y0 + 8, AMARILLO, 1)
    fuego(t, cue["front"], ((3, 3, 2.2), (6, 10, 1.6)))
    fuego(t, cue["back"], ((5, 3, 1.8),))
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], CARBON, base=0.5)
        manga(t, br, CAFE, 7, 1)
        for cara in LADOS:
            x0, y0, rw, rh = br[cara]
            for i in range(rw):
                t.set(x0 + i, y0 + 4, AMARILLO, 2)
        fuego(t, br["front"], ((2, 9, 1.3),))
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], CAFE, base=0.4)
    for cara in LADOS:
        x0, y0, rw, rh = pi[cara]
        for i in range(rw):
            t.set(x0 + i, y0 + 7, AMARILLO, 2)
        t.sombra(pi[cara], 2, "top", 2)
    fuego(t, pi["front"], ((2, 4, 1.3),))
    botas(t, pi, CARBON)
    ta = t.pintar(uv("tanque"), pz["tanque"], TELA_ROJA, base=0.5, grad=0.4)
    fuego(t, ta["back"], ((2, 3, 1.8),))  # reventado
    t.dibujo(
        ta["back"],
        [
            ". . . .",
            ". . . .",
            ". K K .",
            ". K M .",
            ". . K .",
            ". . . .",
            ". . . .",
            ". . . .",
            ". . . .",
        ],
        {"K": (CARBON, 0), "M": (MAGMA, 4)},
    )
    bo = t.pintar(uv("boquilla"), pz["boquilla"], HIERRO, base=0.5)
    t.rellenar(bo["top"], MAGMA, 3)
