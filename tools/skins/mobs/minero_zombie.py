# ruff: noqa: F403, F405
"""Minero: murio en un derrumbe y el pico se le fundio con el antebrazo. Casco abollado con la lampara
rota, overol cubierto de hollin y la cara medio aplastada. Rompe bloques con el brazo-pico."""

from _zombies import *
from motor import *

MOB = dict(
    clave="minero_zombie", grupo="Zombies (mod zombies)", nombre="Minero", escala=1.0, seed=901
)

PIEZAS = dict(
    HUMANOIDE, casco=(9, 4, 9), ala=(10, 1, 10), lampara=(3, 3, 2), pico=(1, 12, 2), roca=(3, 3, 3)
)

CABEZA = (6, 0, -8)


def partes():
    S = humanoide(brazo_d=(-100, 0, 6), brazo_i=(-60, 0, -10), cabeza=CABEZA)
    S += [
        dict(r="casco", caja=(-4.5, 5, -4.5, 4.5, 9, 4.5), piv=(0, 24, 0), rot=CABEZA),
        dict(r="ala", caja=(-5, 5, -5, 5, 6, 5), piv=(0, 24, 0), rot=CABEZA),
        dict(r="lampara", caja=(-1.5, 6, 4.5, 1.5, 9, 6.5), piv=(0, 24, 0), rot=CABEZA),
    ]
    # la cabeza del pico sale del puno derecho, atravesada
    p_puno = en((-6, 22, 0), (-100, 0, 6), (0, -10, 0))
    S.append(dict(r="pico", caja=(-0.5, -6, -1, 0.5, 6, 1), piv=p_puno, rot=(-10, 0, 6)))
    S.append(
        dict(r="roca", caja=(-1.5, 0, -1.5, 1.5, 3, 1.5), piv=(3.5, 22.5, -1), rot=(0, 0, -20))
    )  # escombro en el hombro
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". . . . . . . .",
            ". k . . . . k .",
            "K K K . . K c .",
            "K R K . . c C c",
            ". s . s s . c .",
            ". . K K K K . .",
            ". . K W . W . .",
        ],
        CARA,
    )  # el lado derecho de la cara esta aplastado
    t.mancha(cab["front"], 2, 6.5, 1.6, CARBON, 0.55, 0)
    cas = t.pintar(uv("casco"), pz["casco"], AMARILLO, base=0.55, grad=0.35)
    for cara in ("front", "der", "izq", "back"):
        t.mancha(cas[cara], 2, 2, 1.3, CARBON, 0.5, 0)  # abolladuras con hollin
    t.dibujo(
        cas["izq"],
        [". . . . . . . . .", ". . K . . . . . .", ". K . K . . . . ."],
        {"K": (CARBON, 1)},
    )
    t.pintar(uv("ala"), pz["ala"], AMARILLO, base=0.45)
    lam = t.pintar(uv("lampara"), pz["lampara"], HIERRO, base=0.4)
    t.dibujo(
        lam["front"],
        ["a a a", "a K a", "a b a"],
        {"a": (HIERRO, 2), "K": (NEGRO, 1), "b": (HUESO, 4)},
    )  # vidrio roto
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], DENIM, base=0.45)
    t.dibujo(
        cue["front"], [". a . . . . a .", ". a . . . . a .", ". a . . . . a ."], {"a": (CUERO, 2)}
    )
    t.mancha(cue["front"], 3, 8, 2.4, CARBON, 0.45, 0)
    t.mancha(cue["front"], 5.5, 4, 2, CARNE, 0.4)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.5)
        manga(t, br, CUADROS, 4)
        if n == "brazo_d":  # el pico entra por la mano: hueso y metal en vez de dedos
            for cara in LADOS:
                x0, y0, rw, rh = br[cara]
                for i in range(rw):
                    t.set(x0 + i, y0 + rh - 1, HIERRO, 2)
                    t.set(x0 + i, y0 + rh - 2, HUESO, 2 + (i % 2))
                    t.set(x0 + i, y0 + rh - 3, CARNE, 1)
        else:
            manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], DENIM, base=0.45)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, CUERO)
    pic = t.pintar(uv("pico"), pz["pico"], HIERRO, base=0.55, grad=0.5)
    t.mancha(pic["der"], 1, 2, 1.6, CARNE, 0.35, 0)  # sangre seca en la punta
    t.pintar(uv("roca"), pz["roca"], REGOLITO, base=0.45)
