# ruff: noqa: F403, F405
"""Enano: un minero enano con la vela del casco derretida sobre la cara (la cera le tapo un ojo), la barba
apelmazada de sangre seca y el cuerpo rechoncho. Cabe por huecos de un bloque."""

from _zombies import *
from motor import *

MOB = dict(
    clave="enano_zombie", grupo="Zombies (mod zombies)", nombre="Enano", escala=0.6, seed=913
)

PIEZAS = dict(
    cabeza=(8, 8, 8),
    casco=(9, 3, 9),
    vela=(2, 4, 2),
    llama=(1, 2, 1),
    barba=(6, 6, 2),
    cuerpo=(8, 7, 5),
    brazo=(3, 8, 3),
    pierna=(3, 5, 3),
)

CABEZA = (4, 0, -8)


def partes():
    S = [dict(r="pierna", caja=(-1.5, -5, -1.5, 1.5, 0, 1.5), piv=(sx * 2, 5, 0)) for sx in (-1, 1)]
    S.append(dict(r="cuerpo", caja=(-4, 0, -2.5, 4, 7, 2.5), piv=(0, 5, 0)))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=(0, 12, 0.5), rot=CABEZA))
    S.append(dict(r="casco", caja=(-4.5, 5.5, -4.5, 4.5, 8.5, 4.5), piv=(0, 12, 0.5), rot=CABEZA))
    S.append(dict(r="vela", caja=(-1, 8.5, -1, 1, 12.5, 1), piv=(0, 12, 0.5), rot=CABEZA))
    S.append(dict(r="llama", caja=(-0.5, 12.5, -0.5, 0.5, 14.5, 0.5), piv=(0, 12, 0.5), rot=CABEZA))
    S.append(dict(r="barba", caja=(-3, -5, 3.5, 3, 1, 5.5), piv=(0, 12, 0.5), rot=CABEZA))
    for sx, r_b in ((-1, (-70, 0, 10)), (1, (-15, 0, -14))):
        S.append(
            dict(r="brazo", caja=(-1.5, -7, -1.5, 1.5, 1, 1.5), piv=(sx * 5.5, 11, 0), rot=r_b)
        )
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    t.dibujo(
        cab["front"],
        [
            ". . . . . w . .",
            ". . . . w w w .",
            "K K K . w w w w",
            "K R K . w w w .",
            ". s . . . w . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        ],
        dict(CARA, w=(HUESO, 4)),
    )  # cera derretida tapando un ojo
    cas = t.pintar(uv("casco"), pz["casco"], HIERRO, base=0.45, grad=0.35)
    for cara in ("front", "der", "izq", "back"):
        t.mancha(cas[cara], 4, 1, 1.4, HUESO, 0.9, 0)  # cera chorreando por el casco
    ve = t.pintar(uv("vela"), pz["vela"], HUESO, base=0.75, grad=0.4)
    t.rellenar(ve["top"], NEGRO, 1)
    t.pintar(uv("llama"), pz["llama"], MAGMA, base=0.85, grad=0.6)
    ba = t.pintar(uv("barba"), pz["barba"], CAFE, base=0.62, grad=0.35)
    for cara in ("front", "der", "izq"):
        t.mancha(ba[cara], ba[cara][2] / 2 + 1, 4, 1.0, CARNE, 0.3, 0)
    jirones(t, ba, 2)
    t.dibujo(
        ba["front"], ["K W K K W K", ". . . . . ."], {"K": (NEGRO, 0), "W": (HUESO, 3)}
    )  # boca dentro de la barba
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CUERO, base=0.45)
    for cara in LADOS:
        x0, y0, rw, rh = cue[cara]
        for i in range(rw):
            t.set(x0 + i, y0 + 4, CAFE, 1)  # cinturon
        t.set(x0 + rw // 2, y0 + 4, ORO, 3)
    t.mancha(cue["front"], 2, 2, 1.4, CARNE, 0.4)
    t.sombra(cue["front"], 2, "top", 2)
    br = t.pintar(uv("brazo"), pz["brazo"], PIEL, base=0.45)
    manga(t, br, CUERO, 3, 2)
    manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], CAFE, base=0.4)
    botas(t, pi, HIERRO, 2)
