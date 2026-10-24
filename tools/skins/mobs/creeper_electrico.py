# ruff: noqa: F403, F405
"""Creeper electrico: un experimento fallido. Pararrayos clavados en el craneo y el lomo, pernos en el
cuello, quemaduras en forma de rayo y los ojos blancos, fundidos."""

from motor import *

MOB = dict(
    clave="creeper_electrico",
    grupo="Creepers (mod creepers)",
    nombre="Creeper eléctrico",
    escala=1.0,
    seed=655,
)

PIEZAS = dict(CREEPER, varilla=(1, 9, 1), varilla_corta=(1, 6, 1), perno=(2, 2, 2))


def partes():
    S = creeper(cabeza=(0, 0, -4))
    S += [
        dict(r="varilla", caja=(-0.5, 0, -0.5, 0.5, 9, 0.5), piv=(-1.5, 23, -1), rot=(-15, 0, 25)),
        dict(
            r="varilla_corta", caja=(-0.5, 0, -0.5, 0.5, 6, 0.5), piv=(2, 24, 1.5), rot=(10, 0, -20)
        ),
        dict(r="varilla", caja=(-0.5, 0, -0.5, 0.5, 9, 0.5), piv=(1, 9, -1.5), rot=(-60, 0, -15)),
    ]
    for sx in (-1, 1):
        S.append(dict(r="perno", caja=(-1, -1, -1, 1, 1, 1), piv=(sx * 4.4, 19, 0)))
    return S


RAYO = [
    ". . . . . . . .",
    ". . . . . k . .",
    ". . . . k . . .",
    ". . . k k . . .",
    ". . . . k . . .",
    ". . . k . . . .",
    ". . k . . . . .",
]


def pintar(t, uv, pz):
    quema = {"k": (CARBON, 1), "q": (CARBON, 2)}
    cab = t.pintar(uv("cabeza"), pz["cabeza"], AMARILLO, base=0.42)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". k . . . . k .",
            ". K K . . K K .",
            ". K B . . B K .",
            ". . s K K s . .",
            ". K W K K W K .",
            ". K q q q q K .",
            ". K W K K W K .",
        ],
        dict(CARA_CREEPER, q=(CARBON, 2)),
    )
    t.mancha(cab["top"], 2.5, 5, 2.2, CARBON, 0.5)
    t.mancha(cab["top"], 6, 3, 1.8, CARNE, 0.5)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], AMARILLO, base=0.4)
    t.dibujo(cue["front"], RAYO, quema)
    t.dibujo((cue["back"][0], cue["back"][1] + 4), [r[::-1] for r in RAYO], quema)
    t.mancha(cue["front"], 4, 9.5, 2.4, CARNE, 0.4)
    t.costillas(cue["front"], 2, 8, 4, 2)
    t.mancha(cue["back"], 5, 4, 2, CARBON, 0.5)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], AMARILLO, base=0.35, grad=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.mancha(pa[cara], 2, 5, 1.6, CARBON, 0.4, 0)
        t.garras(pa[cara])
    for n in ("varilla", "varilla_corta"):
        va = t.pintar(uv(n), pz[n], HIERRO, base=0.6, grad=0.6)
        t.rellenar(va["top"], (HIERRO), 4)
    t.pintar(uv("perno"), pz["perno"], HIERRO, base=0.5)
