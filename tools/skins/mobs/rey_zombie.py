# ruff: noqa: F403, F405
"""Rey Zombie (jefe de la Cripta Real): un rey muerto hace siglos, alto y consumido, con la corona clavada
al craneo, media cara en hueso, brazos que le llegan a las rodillas y la tunica real podrida."""
from motor import *

MOB = dict(clave="rey_zombie", grupo="Jefes de dungeon", nombre="Rey Zombie", escala=1.6, seed=711)

PIEZAS = dict(cabeza=(8, 8, 8), corona=(9, 3, 9), punta=(1, 3, 1), cuerpo=(8, 13, 4), faldon=(9, 7, 5),
              brazo=(3, 16, 3), pierna=(3, 13, 3), capa=(10, 22, 1), clavo=(1, 1, 3))

CABEZA = (12, 0, 8)

def partes():
    S = [dict(r="pierna", caja=(-1.5, -13, -1.5, 1.5, 0, 1.5), piv=(sx * 2, 13, 0), rot=(0, 0, sx * -3))
         for sx in (-1, 1)]
    S.append(dict(r="faldon", caja=(-4.5, -6, -2.5, 4.5, 1, 2.5), piv=(0, 13, 0)))
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 13, 2), piv=(0, 13, 0), rot=(6, 0, 0)))
    p_cab = en((0, 13, 0), (6, 0, 0), (0, 13, 0.5))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=p_cab, rot=CABEZA))
    S.append(dict(r="corona", caja=(-4.5, 6, -4.5, 4.5, 9, 4.5), piv=p_cab, rot=CABEZA))
    for x, z in ((-4, -4), (0, -4), (4, -4), (-4, 4), (0, 4), (4, 4), (-4, 0), (4, 0)):
        S.append(dict(r="punta", caja=(x - 0.5, 9, z - 0.5, x + 0.5, 12, z + 0.5), piv=p_cab, rot=CABEZA))
    S.append(dict(r="clavo", caja=(3.5, 7, -1.5, 4.5, 8, 1.5), piv=p_cab, rot=CABEZA))
    # un brazo cuelga hasta la rodilla, el otro se estira hacia el jugador
    S.append(dict(r="brazo", caja=(-1.5, -15, -1.5, 1.5, 1, 1.5), piv=en((0, 13, 0), (6, 0, 0), (-5.5, 12, 0)), rot=(-8, 0, -6)))
    S.append(dict(r="brazo", caja=(-1.5, -15, -1.5, 1.5, 1, 1.5), piv=en((0, 13, 0), (6, 0, 0), (5.5, 12, 0)), rot=(-75, 0, 10)))
    S.append(dict(r="capa", caja=(-5, -22, -0.5, 5, 0, 0.5), piv=en((0, 13, 0), (6, 0, 0), (0, 12.5, -2.6)), rot=(12, 0, 0)))
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". . . . H H H .",
        "s K K . H K K H",
        ". K R . H K r H",
        ". s . . H H K H",
        ". . K K H W H .",
        ". . K c W K W .",
        ". . . c . W . .",
    ], {"K": (NEGRO, 0), "R": (OJO, 2), "r": (OJO, 1), "H": (HUESO, 3), "W": (HUESO, 4), "c": (CARNE, 1), "s": -1})
    t.dibujo(cab["izq"], [". . . . . . . ."] + ["H H H H H . . ."] * 3 + ["H H H . . . . ."], {"H": (HUESO, 2)})
    t.sombra(cab["front"], 1, "top", 2)
    co = t.pintar(uv("corona"), pz["corona"], ORO, base=0.5, grad=0.5)
    for cara in ("top", "bottom"):
        x0, y0, rw, rh = co[cara]
        t.dibujo((x0 + 1, y0 + 1), ["X " * (rw - 2)] * (rh - 2), {"X": "X"})
    for cara in ("front", "der", "izq", "back"):
        t.mancha(co[cara], 2.5, 2, 1.4, CAFE, 0.45, 0)
        x0, y0, rw, rh = co[cara]
        t.set(x0 + rw // 2, y0 + 1, CARNE, 2)
    t.pintar(uv("punta"), pz["punta"], ORO, base=0.65, grad=0.6)
    t.pintar(uv("clavo"), pz["clavo"], HIERRO, base=0.4)

    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], TELA_ROJA, base=0.4)
    t.mancha(cue["front"], 4, 5.5, 3.6, CARNE, 0.35)
    t.costillas(cue["front"], 2, 3, 4, 3)
    x0, y0, rw, rh = cue["front"]
    for j in range(rh):  # ribete dorado de la tunica, roto donde esta la herida
        if not 2 <= j <= 8:
            t.set(x0, y0 + j, ORO, 2); t.set(x0 + rw - 1, y0 + j, ORO, 2)
    t.sombra(cue["front"], 2, "top", 2)
    fa = t.pintar(uv("faldon"), pz["faldon"], TELA_ROJA, base=0.35, grad=0.4)
    for cara in ("front", "back", "der", "izq"):
        x0, y0, rw, rh = fa[cara]
        for i in range(rw):
            t.set(x0 + i, y0, ORO, 2)
            for j in range(rh - (1 + (i * 3 + 1) % 3), rh):
                t.color(x0 + i, y0 + j, (0, 0, 0, 0))
    br = t.pintar(uv("brazo"), pz["brazo"], PIEL, base=0.45)
    for cara in ("front", "der", "izq", "back"):
        x0, y0, rw, rh = br[cara]
        for i in range(rw):
            for j in range(5 - (i % 2)):  # manga real deshilachada
                t.set(x0 + i, y0 + j, TELA_ROJA, 2 + (j == 0))
        t.dibujo((x0, y0 + 9), ["H . .", "H H ."], {"H": (HUESO, 3)})
        t.garras(br[cara], 2)
    pi = t.pintar(uv("pierna"), pz["pierna"], PIEL, base=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.sombra(pi[cara], 3, "top", 2)
        t.garras(pi[cara])
    ca = t.pintar(uv("capa"), pz["capa"], TELA_ROJA, base=0.45, grad=0.45)
    for cara in ("front", "back", "der", "izq"):
        x0, y0, rw, rh = ca[cara]
        for i in range(rw):
            t.set(x0 + i, y0, ORO, 3)
            for j in range(rh - (1 + (i * 7 + 2) % 5), rh):
                t.color(x0 + i, y0 + j, (0, 0, 0, 0))
    t.mancha(ca["back"], 3, 12, 1.8, CAFE, 0.3, 0)
    t.mancha(ca["back"], 7, 6, 1.4, CAFE, 0.3, 0)
    t.dibujo((ca["back"][0] + 6, ca["back"][1] + 15), ["X .", "X X", ". X"], {"X": "X"})  # agujero
