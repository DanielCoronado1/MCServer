# ruff: noqa: F403, F405
"""Creeper toxico: carne morada en descomposicion, ampollas verdes reventando, dos sacos de gas hinchados
en la espalda y la boca chorreando veneno."""

from motor import *

MOB = dict(
    clave="creeper_toxico",
    grupo="Creepers (mod creepers)",
    nombre="Creeper tóxico",
    escala=1.0,
    seed=677,
)

PIEZAS = dict(CREEPER, saco=(5, 5, 4), saco_chico=(3, 4, 3))


def partes():
    S = creeper(cabeza=(6, 0, 3))
    S += [
        dict(r="saco", caja=(-2.5, -2.5, -4, 2.5, 2.5, 0), piv=(-1, 14, -1.6), rot=(10, 0, 10)),
        dict(r="saco_chico", caja=(-1.5, -2, -3, 1.5, 2, 0), piv=(2, 9.5, -1.6), rot=(-10, 0, -12)),
    ]
    return S


def ampollas(t, rect, puntos):
    for cx, cy in puntos:
        t.mancha(rect, cx, cy, 1.4, TOXICO, 0.65, -1)


def pintar(t, uv, pz):
    veneno = {"v": (TOXICO, 2), "V": (TOXICO, 4)}
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PURPURA, base=0.5)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". k . . . . k .",
            ". K K . . K K .",
            ". K V . . V K .",
            ". . s K K s . .",
            ". K v K K v K .",
            ". K v v v v K .",
            ". K v K K V K .",
        ],
        dict(CARA_CREEPER, **veneno),
    )
    ampollas(t, cab["top"], ((2, 2), (6, 5)))
    ampollas(t, cab["izq"], ((5, 3),))
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], PURPURA, base=0.45)
    t.dibujo(
        cue["front"],
        [
            ". . v . . V . .",
            ". . v . . v . .",
            ". . V . . v . .",
            ". . . . . v . .",
            ". . . . . V . .",
        ],
        veneno,
    )  # chorreando desde la boca
    ampollas(t, cue["front"], ((2, 8), (6, 10)))
    ampollas(t, cue["der"], ((2, 5),))
    t.mancha(cue["back"], 4, 6, 3, CARNE, 0.4)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], PURPURA, base=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.garras(pa[cara])
    for n in ("saco", "saco_chico"):
        sa = t.pintar(uv(n), pz[n], TOXICO, base=0.5, var=0.25, grad=0.5)
        for cara in ("top", "back", "der", "izq"):
            t.mancha(sa[cara], sa[cara][2] / 2, sa[cara][3] / 2, 1.2, PURPURA, 0.5, 0)
        t.rellenar(sa["front"], CARNE, 1)
