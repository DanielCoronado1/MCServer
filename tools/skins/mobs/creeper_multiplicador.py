# ruff: noqa: F403, F405
"""Creeper multiplicador: le brotan crias de creeper del cuerpo, cabecitas a medio formar que asoman del
hombro, la espalda y la sien. De ahi salen los minis."""

from motor import *

MOB = dict(
    clave="creeper_multiplicador",
    grupo="Creepers (mod creepers)",
    nombre="Creeper multiplicador",
    escala=1.0,
    seed=666,
)

PIEZAS = dict(CREEPER, cria=(4, 4, 4), cria_chica=(3, 3, 3), bulto=(3, 3, 3))


def partes():
    S = creeper(cabeza=(0, 0, 4))
    for reg, piv, rot in (
        ("cria", (-4.2, 15.5, 0.8), (8, 0, 32)),
        ("cria_chica", (4.2, 23, 0.5), (0, 0, -38)),
        ("cria", (0.8, 12, -2.4), (-62, 0, 8)),
        ("cria_chica", (-1.5, 8, -2.2), (-70, 0, -12)),
        ("bulto", (3.6, 11, 0.5), (0, 0, -25)),
    ):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg]), piv=piv, rot=rot))
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], TEAL, base=0.5)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". k . . . . . .",
            ". K K . . K K .",
            ". K R . . K R .",
            ". K . K K . K .",
            ". . K W W K . .",
            ". K c c c c K .",
            ". K W K K W K .",
        ],
        CARA_CREEPER,
    )
    t.mancha(cab["izq"], 5, 3, 2.2, CARNE, 0.45)  # donde brota la cria de la sien
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], TEAL, base=0.45)
    for cara, cx, cy in (("der", 2, 3), ("back", 4, 6), ("back", 3, 10), ("izq", 2, 6)):
        t.mancha(cue[cara], cx, cy, 2.4, CARNE, 0.45)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], TEAL, base=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.garras(pa[cara])
    cara_cria = {4: [". . . .", "K . . K", ". K K .", ". K K ."], 3: ["K . K", ". K .", ". K ."]}
    for n in ("cria", "cria_chica"):
        cr = t.pintar(uv(n), pz[n], TEAL, base=0.6)
        t.dibujo(cr["front"], cara_cria[pz[n][0]], {"K": (NEGRO, 0)})
        t.rellenar(cr["bottom"], CARNE, 1)
        for cara in ("der", "izq", "back"):
            t.sombra(cr[cara], 1, "bottom", 2)
    bu = t.pintar(uv("bulto"), pz["bulto"], CARNE, base=0.5)
    t.mancha(bu["top"], 1.5, 1.5, 1, TEAL, 0.6, 0)
