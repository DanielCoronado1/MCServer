# ruff: noqa: F403, F405
"""Bilioso: una masa hinchada de bilis, tan gorda que no lo puedes empujar. La panza reventada deja ver la
bilis verde, tiene pustulas por todo el cuerpo y la cabeza se le hundio entre los hombros. Al morir revienta."""
from motor import *
from _zombies import *

MOB = dict(clave="bilioso_zombie", grupo="Zombies (mod zombies)", nombre="Bilioso", escala=1.25, seed=912)

PIEZAS = dict(cabeza=(7, 7, 7), panza=(12, 11, 9), pecho=(10, 6, 7), brazo=(4, 11, 4), pierna=(5, 8, 5),
              pustula=(2, 2, 2), pustula_grande=(3, 3, 3))

def partes():
    S = [dict(r="pierna", caja=(-2.5, -8, -2.5, 2.5, 0, 2.5), piv=(sx * 3, 8, 0), rot=(0, 0, sx * -6)) for sx in (-1, 1)]
    S.append(dict(r="panza", caja=(-6, 0, -4, 6, 11, 5), piv=(0, 7, 0)))
    S.append(dict(r="pecho", caja=(-5, 0, -3.5, 5, 6, 3.5), piv=(0, 17.5, -0.5), rot=(10, 0, 0)))
    S.append(dict(r="cabeza", caja=(-3.5, 0, -3.5, 3.5, 7, 3.5), piv=(0, 21.5, 1.5), rot=(6, 0, -8)))
    for sx in (-1, 1):
        S.append(dict(r="brazo", caja=(-2, -10, -2, 2, 1, 2), piv=(sx * 7, 21, 0), rot=(-40, 0, sx * 18)))
    for reg, piv in (("pustula_grande", (-5, 14, 4.2)), ("pustula", (5.8, 10, 2)), ("pustula", (3, 20, 2.8)),
                     ("pustula_grande", (4, 15, -4.2)), ("pustula", (-4.5, 21.5, -2.5)), ("pustula", (-6.3, 8, -1))):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg], -PIEZAS[reg][1] / 2), piv=piv))
    return S

PIEL_B = [(30, 44, 22), (48, 70, 30), (72, 98, 40), (100, 126, 52), (134, 156, 70), (170, 186, 98)]

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL_B, base=0.5)
    t.dibujo(cab["front"], [
        ". . . . . . .",
        "K K . . . K K",
        "K R . . . R K",
        ". s . . . s .",
        ". K v v v K .",
        ". K v V v K .",
        ". . . v . . .",
    ], dict(CARA, v=(TOXICO, 2), V=(TOXICO, 4)))  # vomitando bilis
    pa = t.pintar(uv("panza"), pz["panza"], PIEL_B, base=0.5, grad=0.45)
    t.mancha(pa["front"], 6, 5, 4.4, CARNE, 0.35)
    t.mancha(pa["front"], 6, 5.5, 3, TOXICO, 0.55, -1)  # bilis
    t.dibujo(pa["front"], [". . . . . . . . . . . ."] * 2 + [". . . x . . . . x . . .", ". . x . . . . . . x . ."],
             {"x": (NEGRO, 1)})  # puntos de sutura reventados
    for cara in ("der", "izq", "back"):
        t.mancha(pa[cara], pa[cara][2] / 2, 5, 2, TOXICO, 0.45)
    pe = t.pintar(uv("pecho"), pz["pecho"], PIEL_B, base=0.5)
    t.mancha(pe["front"], 3, 3, 1.6, CARNE, 0.4)
    t.sombra(pe["front"], 1, "bottom", 2)
    br = t.pintar(uv("brazo"), pz["brazo"], PIEL_B, base=0.45)
    for cara in LADOS:
        t.mancha(br[cara], 2, 4, 1.2, TOXICO, 0.5, 0)
        t.garras(br[cara])
    pi = t.pintar(uv("pierna"), pz["pierna"], CAFE, base=0.4)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 3)
        t.garras(pi[cara])
    for n in ("pustula", "pustula_grande"):
        pu = t.pintar(uv(n), pz[n], TOXICO, base=0.6, var=0.2, grad=0.5)
        t.rellenar(pu["top"], TOXICO, 4)
