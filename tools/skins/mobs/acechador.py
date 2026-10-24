# ruff: noqa: F403, F405
"""Acechador (Kepler): arana alienigena de quitina negra-morada con un racimo de ojos rojos, colmillos de
hueso y un abdomen hinchado con una calavera palida dibujada en el lomo."""
from motor import *

MOB = dict(clave="acechador", grupo="Kepler (mod space)", nombre="Acechador", escala=1.0, seed=822)

PIEZAS = dict(torax=(6, 4, 6), cabeza=(5, 4, 4), abdomen=(8, 7, 10), muslo=(2, 11, 2), canilla=(2, 15, 2),
              colmillo=(1, 3, 1))

def partes():
    S = [dict(r="torax", caja=(-3, -2, -3, 3, 2, 3), piv=(0, 11, 0)),
         dict(r="cabeza", caja=(-2.5, -2, 0, 2.5, 2, 4), piv=(0, 11, 2.8), rot=(14, 0, 0)),
         dict(r="abdomen", caja=(-4, -3, -10, 4, 4, 0), piv=(0, 12, -2.5), rot=(22, 0, 0))]
    for sx in (-1, 1):
        S.append(dict(r="colmillo", caja=(-0.5, -3, -0.5, 0.5, 0, 0.5), piv=(sx * 1.1, 9.6, 6.4), rot=(25, 0, sx * 15)))
        for k in range(4):
            cadera = (sx * 2.6, 11.5, 2 - k * 1.5)
            r_m = ((k - 1.5) * -34, 0, -sx * 68)
            rodilla = en(cadera, r_m, (0, 10.6, 0))
            S.append(dict(r="muslo", caja=(-1, 0, -1, 1, 11, 1), piv=cadera, rot=r_m))
            S.append(dict(r="canilla", caja=(-1, -15, -1, 1, 0, 1), piv=rodilla, rot=((k - 1.5) * -16, 0, sx * 18)))
    return S

def pintar(t, uv, pz):
    ca = t.pintar(uv("cabeza"), pz["cabeza"], QUITINA, base=0.45)
    t.dibujo(ca["front"], ["R . R . R", ". r R r .", "r . . . r", ". K K K ."],
             {"R": (OJO, 3), "r": (OJO, 2), "K": (NEGRO, 0)})
    t.dibujo(ca["top"], [". r . r .", ". . R . .", ". . . . .", ". . . . ."], {"R": (OJO, 3), "r": (OJO, 2)})
    to = t.pintar(uv("torax"), pz["torax"], QUITINA, base=0.5)
    t.sombra(to["front"], 1, "bottom", 2)
    ab = t.pintar(uv("abdomen"), pz["abdomen"], QUITINA, base=0.45)
    t.dibujo(ab["top"], [  # calavera palida en el lomo
        ". . . . . . . .", ". . P P P P . .", ". P P P P P P .", ". P K P P K P .", ". P K P P K P .",
        ". . P P P P . .", ". . P K K P . .", ". . P . . P . .", ". . . . . . . .", ". . . . . . . ."],
        {"P": (PALIDO, 3), "K": (QUITINA, 0)})
    for cara in ("der", "izq"):
        x0, y0, rw, rh = ab[cara]
        for i in range(0, rw, 3):  # costuras de carne entre placas
            for j in range(rh):
                t.set(x0 + i, y0 + j, CARNE, 1)
    t.mancha(ab["back"], 4, 4, 1.6, CARNE, 0.4)
    for n in ("muslo", "canilla"):
        pa = t.pintar(uv(n), pz[n], QUITINA, base=0.5, grad=0.4)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = pa[cara]
            if n == "muslo":
                t.set(x0, y0 + rh - 1, CARNE, 1); t.set(x0 + 1, y0 + rh - 1, CARNE, 1)
            else:
                t.garras(pa[cara], 2)
    t.hueso(regiones(*uv("colmillo"), *pz["colmillo"]))
