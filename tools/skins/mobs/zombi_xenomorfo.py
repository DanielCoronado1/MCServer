# ruff: noqa: F403, F405
"""Zombi xenomorfo (Kepler): un humano infectado a medio convertir. El craneo se le alargo hacia atras,
la quitina le cubre medio cuerpo, le salen tubos dorsales y una cola; un brazo todavia es humano."""
from motor import *

MOB = dict(clave="zombi_xenomorfo", grupo="Kepler (mod space)", nombre="Zombi xenomorfo", escala=1.0, seed=855)

PIEZAS = dict({k: v for k, v in HUMANOIDE.items() if k != "brazo_i"}, craneo=(6, 5, 9), brazo_x=(3, 14, 3), tubo=(2, 6, 2), cola=(2, 2, 9), cola2=(1, 1, 9))

CABEZA = (10, 0, -6)

def partes():
    S = humanoide(brazo_d=(-80, 0, 0), brazo_i=(-95, 0, 0), cabeza=CABEZA)
    S = [p for p in S if p["r"] != "brazo_i"]
    S.append(dict(r="brazo_x", caja=(-1.5, -12, -1.5, 1.5, 2, 1.5), piv=(5.6, 22, 0), rot=(-100, 0, -8)))
    S.append(dict(r="craneo", caja=(-3, 3, -11, 3, 8, -2), piv=(0, 24, 0), rot=(CABEZA[0] + 18, 0, CABEZA[2])))
    for sx in (-1, 1):
        for k, y in enumerate((22, 18)):
            S.append(dict(r="tubo", caja=caja_centrada(PIEZAS["tubo"]), piv=(sx * 2.2, y, -2), rot=(-45 - k * 15, 0, sx * -25)))
    r_c = (-58, 0, 0)
    S.append(dict(r="cola", caja=(-1, -1, -9, 1, 1, 0), piv=(0, 13, -2), rot=r_c))
    S.append(dict(r="cola2", caja=(-0.5, -0.5, -9, 0.5, 0.5, 0), piv=en((0, 13, -2), r_c, (0, 0, -8.6)), rot=(-20, 0, 0)))
    return S

def quitina(t, rect, desde_x, hasta_x):
    x0, y0, rw, rh = rect
    n = ruido(t.seed, 1.4)
    for j in range(rh):
        for i in range(desde_x, min(hasta_x, rw)):
            t.set(x0 + i, y0 + j, QUITINA, 2.5 + (n(i, j) - 0.5) * 2 - (j % 4 == 3))

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], QUITINA, base=0.45)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". . . . . . . .",
        "s s . . . . . .",
        "P K . . . . . .",
        "P R s . . . . .",
        "P s . W W W W .",
        ". W K K K K K W",
        ". . W K W W K .",
    ], {"P": (PIEL, 3), "s": (PIEL, 2), "K": (NEGRO, 0), "R": (OJO, 2), "W": (HUESO, 4)})
    cr = t.pintar(uv("craneo"), pz["craneo"], QUITINA, base=0.55, grad=0.45)
    for cara in ("der", "izq", "top"):
        x0, y0, rw, rh = cr[cara]
        for i in range(0, rw, 3):
            for j in range(rh):
                t.ajustar(x0 + i, y0 + j, -1)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], DENIM, base=0.35)  # restos del traje
    quitina(t, cue["front"], 3, 8)
    quitina(t, cue["back"], 0, 8)
    t.costillas(cue["front"], 3, 2, 5, 3)
    t.mancha(cue["front"], 1.5, 8, 1.6, PIEL, 0.5)
    t.sombra(cue["front"], 2, "top", 2)
    br = t.pintar(uv("brazo_d"), pz["brazo_d"], PIEL, base=0.5)  # el brazo humano que queda
    for cara in ("front", "der", "izq", "back"):
        x0, y0, rw, rh = br[cara]
        for i in range(rw):
            for j in range(3 + (i % 2)):
                t.set(x0 + i, y0 + j, DENIM, 2 + (j == 0))
        t.mancha(br[cara], 2, 9, 1.6, QUITINA, 0.45)
    bx = t.pintar(uv("brazo_x"), pz["brazo_x"], QUITINA, base=0.5)
    for cara in ("front", "der", "izq", "back"):
        t.garras(bx[cara], 3)
    pi = t.pintar(uv("pierna"), pz["pierna"], DENIM, base=0.35)
    for cara in ("front", "der", "izq", "back"):
        quitina(t, pi[cara], 0, 2)
        t.sombra(pi[cara], 2, "top", 2)
        t.garras(pi[cara])
    for n in ("tubo",):
        tu = t.pintar(uv(n), pz[n], QUITINA, base=0.6, grad=0.6)
        t.rellenar(tu["top"], NEGRO, 0)
    for n in ("cola", "cola2"):
        co = t.pintar(uv(n), pz[n], QUITINA, base=0.5)
        for cara in ("top", "der", "izq"):
            x0, y0, rw, rh = co[cara]
            for j in range(0, rh, 2):
                for i in range(rw):
                    t.ajustar(x0 + i, y0 + j, -1)
