# ruff: noqa: F403, F405
"""Parasito trepador (Kepler): una mano palida de ocho dedos largos con articulaciones, un saco venoso
latiendo encima y una cola enroscada. Es lo que se te pega a la cabeza."""
from motor import *

MOB = dict(clave="parasito_trepador", grupo="Kepler (mod space)", nombre="Parásito trepador", escala=1.0, seed=811)

PIEZAS = dict(cuerpo=(6, 3, 8), saco=(4, 3, 5), dedo=(1, 5, 1), punta=(1, 6, 1), cola=(2, 2, 6), cola2=(1, 1, 6))

def partes():
    S = [dict(r="cuerpo", caja=(-3, 0, -4, 3, 3, 4), piv=(0, 4, 0)),
         dict(r="saco", caja=(-2, 0, -2.5, 2, 3, 2.5), piv=(0, 6.6, -0.5), rot=(-6, 0, 0))]
    for sx in (-1, 1):
        for k in range(4):
            cadera = (sx * 2.8, 5.5, 3 - k * 2.1)
            r_d = ((1.5 - k) * 14, 0, -sx * 68)
            nudillo = en(cadera, r_d, (0, 4.6, 0))
            S.append(dict(r="dedo", caja=(-0.5, 0, -0.5, 0.5, 5, 0.5), piv=cadera, rot=r_d))
            S.append(dict(r="punta", caja=(-0.5, -6, -0.5, 0.5, 0, 0.5), piv=nudillo, rot=((1.5 - k) * 10, 0, sx * 22)))
    r_c = (28, 0, 0)
    S.append(dict(r="cola", caja=(-1, -1, -6, 1, 1, 0), piv=(0, 5, -3.8), rot=r_c))
    S.append(dict(r="cola2", caja=(-0.5, -0.5, -6, 0.5, 0.5, 0), piv=en((0, 5, -3.8), r_c, (0, 0, -5.6)), rot=(65, 0, 0)))
    return S

def pintar(t, uv, pz):
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], PALIDO, base=0.55)
    t.mancha(cue["top"], 3, 6, 2, CARNE, 0.45)
    x0, y0, rw, rh = cue["bottom"]  # boca ventral: lo que se pega al casco
    t.rellenar(cue["bottom"], CARNE, 1)
    t.dibujo((x0, y0 + 1), [". W . W W . ", "W K K K K W", "W K c c K W", "W K K K K W", ". W . W W ."],
             {"W": (HUESO, 3), "K": (NEGRO, 0), "c": (CARNE, 3)})
    t.dibujo(cue["front"], ["K . K K . K", ". . . . . .", ". . . . . ."], {"K": (NEGRO, 1)})
    sa = t.pintar(uv("saco"), pz["saco"], CARNE, base=0.6, var=0.25, grad=0.45)
    for cara in ("top", "der", "izq", "front", "back"):
        t.mancha(sa[cara], sa[cara][2] / 2, sa[cara][3] / 2, 1.1, PALIDO, 0.55, 0)
    for n in ("dedo", "punta"):
        de = t.pintar(uv(n), pz[n], PALIDO, base=0.55, grad=0.5)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = de[cara]
            if n == "dedo":
                t.set(x0, y0 + rh - 1, HUESO, 2)  # nudillo
            else:
                t.set(x0, y0 + rh - 1, NEGRO, 1); t.set(x0, y0 + rh - 2, HUESO, 3)  # una
    for n in ("cola", "cola2"):
        co = t.pintar(uv(n), pz[n], PALIDO, base=0.5)
        for cara in ("top", "der", "izq"):
            x0, y0, rw, rh = co[cara]
            for j in range(0, rh, 2):
                for i in range(rw):
                    t.ajustar(x0 + i, y0 + j, -1)
