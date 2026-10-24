# ruff: noqa: F403, F405
"""Contagiado: un jugador que no se salvo. Todavia lleva la ropa de Steve, pero la infeccion morada le sale de
la mordida del cuello, le revienta el hombro y la espalda en brotes, y le esta comiendo un ojo."""

from _zombies import *
from motor import *

MOB = dict(
    clave="contagiado_zombie",
    grupo="Zombies (mod zombies)",
    nombre="Contagiado",
    escala=1.0,
    seed=914,
)

PIEZAS = dict(HUMANOIDE, brote=(3, 4, 3), brote_chico=(2, 3, 2))

CABEZA = (8, 0, -10)

CAMISA = [(10, 50, 60), (16, 80, 92), (24, 112, 124), (40, 146, 156), (80, 180, 186)]


def partes():
    S = humanoide(brazo_d=(-95, 0, 4), brazo_i=(-75, 0, -6), cabeza=CABEZA)
    for reg, piv, rot in (
        ("brote", (-5, 23.5, 0.5), (0, 0, 30)),
        ("brote_chico", (-3.5, 24, 1.5), (25, 0, 55)),
        ("brote", (1, 18, -1.8), (-50, 0, -10)),
        ("brote_chico", (-2, 15, -1.8), (-60, 0, 25)),
        ("brote_chico", (2.5, 13.5, -1.8), (-70, 0, -30)),
    ):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg], -1), piv=piv, rot=rot))
    return S


def infeccion(t, rect, puntos):
    for cx, cy, r in puntos:
        t.mancha(rect, cx, cy, r, AMATISTA, 0.5, -1)


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.5)
    t.dibujo(
        cab["front"],
        [
            "M M M M M M M M",
            "M . . . . . . M",
            ". K K . . A A .",
            ". B K . . A a .",
            ". . . s s . A .",
            ". . K . . K . .",
            ". . K K K K . .",
            ". . . . . . . .",
        ],
        dict(CARA, M=(CAFE, 1), A=(AMATISTA, 3), a=(AMATISTA, 4)),
    )  # pelo de Steve, el ojo comido por la infeccion
    t.rellenar(regiones(*uv("cabeza"), *pz["cabeza"])["top"], CAFE, 1)
    for cara in ("der", "izq", "back"):
        x0, y0, rw, rh = cab[cara]
        for i in range(rw):
            for j in range(2 + (i % 2)):
                t.set(x0 + i, y0 + j, CAFE, 1)
    infeccion(t, cab["izq"], ((3, 6.5, 2.6),))  # la mordida
    t.dibujo(
        cab["izq"],
        [". . . . . . . ."] * 5 + [". . W . W . . .", ". . c C c . . .", ". . W . W . . ."],
        {"W": (HUESO, 3), "c": (CARNE, 1), "C": (CARNE, 2)},
    )
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CAMISA, base=0.5)
    infeccion(t, cue["front"], ((6, 1.5, 2.6), (2, 9, 1.6)))
    infeccion(t, cue["back"], ((3, 4, 3.4), (5, 9, 2)))
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.5)
        manga(t, br, CAMISA, 4, 2)
        if n == "brazo_d":
            for cara in LADOS:
                infeccion(t, br[cara], ((2, 2, 2.2),))
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], DENIM, base=0.45)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, CARBON, 2)
    infeccion(t, pi["front"], ((1.5, 8, 1.4),))
    for n in ("brote", "brote_chico"):
        br = t.pintar(uv(n), pz[n], AMATISTA, base=0.6, var=0.25, grad=0.6)
        t.rellenar(br["top"], AMATISTA, 4)
        for cara in LADOS:
            x0, y0, rw, rh = br[cara]
            t.set(x0, y0 + rh - 1, CARNE, 1)
