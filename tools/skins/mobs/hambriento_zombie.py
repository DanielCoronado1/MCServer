# ruff: noqa: F403, F405
"""Hambriento: consumido hasta el hueso, con la panza abierta y vacia, brazos flacos y larguisimos y una
mandibula que se le desencaja para comer lo que sea. Te contagia su hambre."""
from motor import *
from _zombies import *

MOB = dict(clave="hambriento_zombie", grupo="Zombies (mod zombies)", nombre="Hambriento", escala=1.0, seed=906)

PIEZAS = dict(cabeza=(8, 7, 8), mandibula=(7, 3, 6), cuerpo=(7, 12, 3), brazo=(2, 15, 2), pierna=(3, 12, 3))

CABEZA = (14, 0, 0)

def partes():
    r_t = (12, 0, 0)
    S = [dict(r="pierna", caja=(-1.5, -12, -1.5, 1.5, 0, 1.5), piv=(sx * 1.8, 12, 0), rot=(0, 0, sx * -3)) for sx in (-1, 1)]
    S.append(dict(r="cuerpo", caja=(-3.5, 0, -1.5, 3.5, 12, 1.5), piv=(0, 12, 0), rot=r_t))
    p_cab = en((0, 12, 0), r_t, (0, 12, 0.5))
    S.append(dict(r="cabeza", caja=(-4, 1, -4, 4, 8, 4), piv=p_cab, rot=CABEZA))
    S.append(dict(r="mandibula", caja=(-3.5, -3, -1, 3.5, 0, 5), piv=en(p_cab, CABEZA, (0, 1.5, -1)), rot=(CABEZA[0] + 38, 0, 0)))
    for sx, r_b in ((-1, (-85, 0, 6)), (1, (-60, 0, -12))):
        S.append(dict(r="brazo", caja=(-1, -14, -1, 1, 1, 1), piv=en((0, 12, 0), r_t, (sx * 4.5, 11, 0)), rot=r_b))
    return S

PIEL_AM = [(40, 38, 24), (68, 64, 36), (100, 96, 52), (134, 128, 72), (168, 160, 98), (200, 192, 132)]

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL_AM, base=0.5)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". k . . . . k .",
        "K K K . . K K K",
        "K R K . . K r K",
        ". K s . . s K .",
        ". s . K K . s .",
        ". c W . . W c .",
    ], CARA)  # pomulos marcados, sin labios
    t.dibujo(cab["izq"], [". . . . . . . .", ". . . . . . . .", ". H H . . . . .", ". H H . . . . ."], {"H": (HUESO, 3)})
    man = t.pintar(uv("mandibula"), pz["mandibula"], PIEL_AM, base=0.45)
    t.dibujo(man["front"], ["W c W c W c W", "c c c c c c c", ". . . . . . ."], {"W": (HUESO, 3), "c": (CARNE, 1)})
    t.rellenar(man["top"], CARNE, 1)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], PIEL_AM, base=0.45)
    t.costillas(cue["front"], 1, 1, 5, 3)
    t.dibujo(cue["front"], [". . . . . . ."] * 7 + [". K K K K K .", ". K c K c K .", ". . K K K . .",
                                                   ". . . . . . .", "a a a a a a a"],
             {"K": (NEGRO, 0), "c": (CARNE, 1), "a": (CAFE, 2)})  # panza abierta y vacia
    t.columna(cue["back"], 3)
    t.sombra(cue["front"], 1, "top", 2)
    br = t.pintar(uv("brazo"), pz["brazo"], PIEL_AM, base=0.45)
    for cara in LADOS:
        x0, y0, rw, rh = br[cara]
        t.set(x0, y0 + 7, HUESO, 3); t.set(x0 + 1, y0 + 7, HUESO, 2)  # codo puntiagudo
        t.garras(br[cara], 2)
    pi = t.pintar(uv("pierna"), pz["pierna"], PIEL_AM, base=0.4)
    pantalon(t, pi, CAFE, roto_desde=5, idx=1)
    for cara in LADOS:
        t.garras(pi[cara])
