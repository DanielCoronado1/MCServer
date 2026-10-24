# ruff: noqa: F403, F405
"""Rey Creeper (jefe): enorme y podrido, con una corona oxidada clavada en el craneo, la capa real
hecha jirones, costillas abiertas y la quijada cayendose."""
from motor import *

MOB = dict(clave="creeper_rey", grupo="Creepers (mod creepers)", nombre="Rey Creeper", escala=3.0, seed=688)

PIEZAS = dict(cabeza=(10, 10, 10), cuerpo=(10, 14, 6), pata=(5, 8, 5), corona=(11, 4, 11), punta=(1, 3, 1),
              capa=(12, 18, 1), mandibula=(8, 2, 5))

CABEZA = (0, 0, -6)

def partes():
    S = [dict(r="pata", caja=(-2.5, -8, -2.5, 2.5, 0, 2.5), piv=(x, 8, z)) for x in (-3, 3) for z in (-5, 5)]
    S.append(dict(r="cuerpo", caja=(-5, 0, -3, 5, 14, 3), piv=(0, 8, 0)))
    S.append(dict(r="cabeza", caja=(-5, 0, -5, 5, 10, 5), piv=(0, 22, 0.5), rot=CABEZA))
    S.append(dict(r="mandibula", caja=(-4, -2, 0, 4, 0, 5), piv=en((0, 22, 0.5), CABEZA, (0, 1, 0.5)),
                  rot=(CABEZA[0] + 25, 0, CABEZA[2])))
    S.append(dict(r="corona", caja=(-5.5, 8, -5.5, 5.5, 12, 5.5), piv=(0, 22, 0.5), rot=CABEZA))
    for x, z in ((-5, -5), (0, -5), (5, -5), (-5, 0), (5, 0), (-5, 5), (0, 5), (5, 5)):
        S.append(dict(r="punta", caja=(x - 0.5, 12, z - 0.5, x + 0.5, 15, z + 0.5), piv=(0, 22, 0.5), rot=CABEZA))
    S.append(dict(r="capa", caja=(-6, -18, -0.5, 6, 0, 0.5), piv=(0, 21.5, -3.6), rot=(14, 0, 0)))
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], VERDE, base=0.42)
    t.dibujo(cab["front"], [
        ". . . . . . . . . .",
        ". . . . . . . . . .",
        ". k k . . . . k k .",
        ". K K K . . K K K .",
        ". K R K . . K R K .",
        ". K K . . . . K K .",
        ". . . s K K s . . .",
        ". . K W K K W K . .",
        ". . K c c c c K . .",
        ". . . . . . . . . .",
    ], CARA_CREEPER)
    t.mancha(cab["izq"], 6, 4, 2.6, CARNE, 0.4)
    t.dibujo(cab["izq"], [". . . . . . . . . .", ". . . . . . . . . .", ". . . . . H H . . .", ". . . . H H K H . .",
                          ". . . . . H H . . ."], {"H": (HUESO, 3), "K": (NEGRO, 0)})
    man = t.pintar(uv("mandibula"), pz["mandibula"], VERDE, base=0.38)
    t.dibujo(man["front"], ["W c W c c W c W", "c c c c c c c c"], {"W": (HUESO, 3), "c": (CARNE, 1)})
    t.rellenar(man["top"], CARNE, 2)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], VERDE, base=0.42)
    t.mancha(cue["front"], 5, 6.5, 4.6, CARNE, 0.35)
    t.costillas(cue["front"], 1, 3, 4, 4)
    t.costillas(cue["front"], 5, 3, 4, 4)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], VERDE, base=0.38, grad=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.sombra(pa[cara], 2, "top", 2)
        t.garras(pa[cara], 2)
    co = t.pintar(uv("corona"), pz["corona"], ORO, base=0.55, grad=0.5)
    for cara in ("top", "bottom"):  # aro hueco: se ve la cabeza adentro
        x0, y0, rw, rh = co[cara]
        t.dibujo((x0 + 1, y0 + 1), ["X " * (rw - 2)] * (rh - 2), {"X": "X"})
    for cara in ("front", "der", "izq", "back"):
        x0, y0, rw, rh = co[cara]
        for i in range(1, rw - 1, 3):  # oxido y gemas
            t.set(x0 + i, y0 + 1, CARNE if i % 2 else TEAL, 2)
        t.mancha(co[cara], 3, 3, 1.6, CAFE, 0.4, 0)
    t.pintar(uv("punta"), pz["punta"], ORO, base=0.7, grad=0.6)
    ca = t.pintar(uv("capa"), pz["capa"], TELA_ROJA, base=0.5, grad=0.4)
    for cara in ("front", "back", "der", "izq"):  # borde inferior hecho jirones
        x0, y0, rw, rh = ca[cara]
        for i in range(rw):
            for j in range(rh - (1 + (i * 5 + 3) % 4), rh):
                t.color(x0 + i, y0 + j, (0, 0, 0, 0))
        for i in range(rw):
            t.set(x0 + i, y0, ORO, 3)
    t.mancha(ca["back"], 4, 9, 2, CARNE, 0.3, 0)
    t.mancha(ca["back"], 8.5, 5, 1.5, CAFE, 0.3, 0)
