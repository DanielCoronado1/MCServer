# ruff: noqa: F403, F405
"""Mini creeper: una cria deforme, con la cabeza enorme ladeada sobre un cuerpecito, la boca cosida y
un ojo hundido mas grande que el otro."""
from motor import *

MOB = dict(clave="creeper_mini", grupo="Creepers (mod creepers)", nombre="Mini creeper", escala=0.45, seed=644)

PIEZAS = dict(cabeza=(8, 8, 8), cuerpo=(4, 5, 3), pata=(2, 3, 2))

def partes():
    S = [dict(r="pata", caja=(-1, -3, -1, 1, 0, 1), piv=(x, 3, z)) for x in (-1, 1) for z in (-1, 1)]
    S.append(dict(r="cuerpo", caja=(-2, 0, -1.5, 2, 5, 1.5), piv=(0, 3, 0)))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=(0, 7.6, 0.5), rot=(10, 0, 16)))
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], VERDE, base=0.6)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        "K K K . . . . .",
        "K K K . . . k .",
        "K K K . . K r .",
        ". . s s . . . .",
        ". W c W c W c .",
        ". c W c W c W .",
        ". . . . . . . .",
    ], CARA_CREEPER)
    t.mancha(cab["top"], 5.5, 2.5, 2.4, CARNE, 0.45)  # fontanela abierta
    t.dibujo(cab["top"], [". . . . . . . .", ". . . . W . . .", ". . . W . W . .", ". . . . W . . ."],
             {"W": (HUESO, 3)})
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], VERDE, base=0.5)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], VERDE, base=0.45)
    for cara in ("front", "der", "izq", "back"):
        t.garras(pa[cara])
