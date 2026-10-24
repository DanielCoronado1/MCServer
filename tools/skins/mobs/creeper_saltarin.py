# ruff: noqa: F403, F405
"""Creeper saltarin: las patas de atras mutaron en ancas de rana enormes con la rodilla expuesta,
agazapado hacia adelante como a punto de saltarte encima."""
from motor import *

MOB = dict(clave="creeper_saltarin", grupo="Creepers (mod creepers)", nombre="Creeper saltarín", escala=0.75, seed=633)

PIEZAS = dict(cabeza=(8, 8, 8), cuerpo=(8, 12, 4), pata_del=(3, 7, 3), anca=(3, 9, 3), canilla=(3, 13, 3),
              pie=(3, 1, 6))

def partes():
    S = []
    r_c = (22, 0, 0)
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 12, 2), piv=(0, 6, -1), rot=r_c))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=en((0, 6, -1), r_c, (0, 11.5, 1)), rot=(-8, 0, 0)))
    for sx in (-1, 1):
        S.append(dict(r="pata_del", caja=(-1.5, -7, -1.5, 1.5, 0, 1.5), piv=(sx * 2.5, 7, 1.5), rot=(10, 0, sx * -8)))
        cadera, r_a = (sx * 4, 8, -2), (-55, 0, sx * -12)
        rodilla = en(cadera, r_a, (0, 9, 0))
        r_s = (-28, 0, sx * 6)
        S.append(dict(r="anca", caja=(-1.5, 0, -1.5, 1.5, 9, 1.5), piv=cadera, rot=r_a))
        S.append(dict(r="canilla", caja=(-1.5, -13, -1.5, 1.5, 0, 1.5), piv=rodilla, rot=r_s))
        S.append(dict(r="pie", caja=(-1.5, -1, -1, 1.5, 0, 5), piv=en(rodilla, r_s, (0, -13, 0))))
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], LIMA, base=0.5)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". k . . . . k .",
        "K K K . . K K K",
        "K R K . . K R K",
        ". . s K K s . .",
        ". K W K K W K .",
        ". K c c c c K .",
        ". K W K K W K .",
    ], CARA_CREEPER)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], LIMA, base=0.5)
    t.mancha(cue["back"], 4, 8, 3.2, CARNE, 0.4)
    t.columna(cue["back"], 3)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("pata_del", "anca", "canilla"):
        pa = t.pintar(uv(n), pz[n], LIMA, base=0.45, grad=0.35)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = pa[cara]
            if n == "canilla":  # tendones tensos como resortes (el sello del saltarin)
                for j in range(2, rh - 2, 3):
                    for i in range(rw):
                        t.set(x0 + i, y0 + j, HUESO, 2 + (i == 1))
            if n == "anca":  # rodilla de hueso asomando
                t.dibujo((x0, y0), [". . ."] * 7 + ["c H c", "H H H"], {"H": (HUESO, 3), "c": (CARNE, 1)})
            t.sombra(pa[cara], 1, "top", 2)
            if n == "pata_del":
                t.garras(pa[cara])
    pie = t.pintar(uv("pie"), pz["pie"], LIMA, base=0.35)
    t.garras(pie["front"])
