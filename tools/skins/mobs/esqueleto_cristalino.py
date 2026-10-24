# ruff: noqa: F403, F405
"""Esqueleto cristalino (Kepler): huesos invadidos por cristal cian que le revienta por la cuenca del ojo,
el hombro, la columna y el antebrazo. El cristal crece desde adentro del hueso."""
from motor import *

MOB = dict(clave="esqueleto_cristalino", grupo="Kepler (mod space)", nombre="Esqueleto cristalino", escala=1.0, seed=866)

PIEZAS = dict(cabeza=(8, 8, 8), cuerpo=(8, 12, 4), hueso_brazo=(2, 12, 2), hueso_pierna=(2, 12, 2),
              cristal=(3, 8, 3), cristal_chico=(2, 5, 2))

def partes():
    S = [dict(r="hueso_pierna", caja=(-1, -12, -1, 1, 0, 1), piv=(sx * 2, 12, 0), rot=(0, 0, sx * -2)) for sx in (-1, 1)]
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 12, 2), piv=(0, 12, 0), rot=(5, 0, 0)))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=(0, 24, 0.5), rot=(-4, 0, 10)))
    S.append(dict(r="hueso_brazo", caja=(-1, -12, -1, 1, 0, 1), piv=(-5, 22, 0), rot=(-90, 0, 6)))
    S.append(dict(r="hueso_brazo", caja=(-1, -12, -1, 1, 0, 1), piv=(5, 22, 0), rot=(-85, 0, -22)))
    for reg, piv, rot in (("cristal", (-5.5, 23.5, 0), (0, 0, 30)), ("cristal_chico", (-4, 23.5, 1.5), (25, 0, 55)),
                          ("cristal", (0.5, 18, -1.6), (-50, 0, -10)), ("cristal_chico", (-1, 13.5, -1.6), (-65, 0, 20)),
                          ("cristal_chico", (1.6, 28.5, 4.2), (60, 0, -30)), ("cristal_chico", en((5, 22, 0), (-85, 0, -22), (0.8, -7, 0)), (-40, 0, -60))):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg]), piv=piv, rot=rot))
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], HUESO, base=0.6, var=0.25)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". . . . . . . .",
        ". K K . . C C .",
        ". K K . . C c .",
        ". . . K K . . .",
        ". . . . . . . .",
        ". K W K W K W .",
        ". . K . K . . .",
    ], {"K": (NEGRO, 0), "W": (HUESO, 4), "C": (CIAN, 4), "c": (CIAN, 2)})
    t.mancha(cab["top"], 6, 6, 2, CIAN, 0.5)
    t.sombra(cab["front"], 1, "top", 1)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], HUESO, base=0.55, var=0.25)
    for cara in ("front", "back"):  # caja toracica: costillas con vacio negro entre medio
        x0, y0, rw, rh = cue[cara]
        for j in range(1, rh - 3):
            for i in range(1, rw - 1):
                if j % 2 == 0 and i not in (3, 4):
                    t.set(x0 + i, y0 + j, NEGRO, 1)
        t.set(x0 + 3, y0 + rh - 1, HUESO, 2)
    t.mancha(cue["back"], 4, 5, 2, CIAN, 0.45)
    for n in ("hueso_brazo", "hueso_pierna"):
        hb = t.pintar(uv(n), pz[n], HUESO, base=0.6, var=0.25)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = hb[cara]
            for j in (5, 6, 7):  # vetas de cristal dentro del hueso
                t.set(x0 + (j % 2), y0 + j, CIAN, 2 + (j == 6))
            t.sombra(hb[cara], 1, "top", 2)
    for n in ("cristal", "cristal_chico"):
        t.cristal(regiones(*uv(n), *pz[n]), CIAN)
