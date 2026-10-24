# ruff: noqa: F403, F405
"""Escudero: un soldado que nunca solto el escudo; ahora esta clavado a su antebrazo con clavos. Yelmo
oxidado con rendija, cota de malla podrida y flechas clavadas en la espalda de batallas que perdio."""
from motor import *
from _zombies import *

MOB = dict(clave="escudero_zombie", grupo="Zombies (mod zombies)", nombre="Escudero", escala=1.0, seed=905)

PIEZAS = dict(HUMANOIDE, yelmo=(9, 9, 9), escudo=(10, 13, 1), flecha=(1, 1, 8), pluma=(1, 2, 2))

def partes():
    S = humanoide(brazo_d=(-75, 0, 4), brazo_i=(-70, 0, -30), cabeza=(8, 0, 0))
    S.append(dict(r="yelmo", caja=(-4.5, -0.5, -4.5, 4.5, 8.5, 4.5), piv=(0, 24, 0), rot=(8, 0, 0)))
    p_ante = en((6, 22, 0), (-70, 0, -30), (0, -6, 0))
    S.append(dict(r="escudo", caja=(-5, -6.5, 2, 5, 6.5, 3), piv=p_ante, rot=(0, 0, 0)))
    for x, y, rx, rz in ((-1.5, 19, -70, 10), (2, 15, -60, -15), (0, 21, -80, 0)):
        S.append(dict(r="flecha", caja=(-0.5, -0.5, -8, 0.5, 0.5, 0), piv=(x, y, -1.5), rot=(rx + 70, 0, rz)))
        S.append(dict(r="pluma", caja=(-0.5, -1, -9, 0.5, 1, -7), piv=(x, y, -1.5), rot=(rx + 70, 0, rz)))
    return S

def malla(t, rect, rampa=HIERRO):
    x0, y0, rw, rh = rect
    for j in range(rh):
        for i in range(rw):
            if (i + j) % 2 == 0 and j % 2 == 0:  # anillos sutiles, sin tablero de ajedrez
                t.ajustar(x0 + i, y0 + j, -1)

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    ye = t.pintar(uv("yelmo"), pz["yelmo"], HIERRO, base=0.5, grad=0.45)
    t.dibujo(ye["front"], [
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". K K K K K K K .",
        ". K R K K K R K .",
        ". . . . K . . . .",
        ". . o . K . o . .",
        ". o . . K . . o .",
        ". . . . . . . . .",
    ], {"K": (NEGRO, 0), "R": (OJO, 2), "o": (CUERO, 2)})  # rendija con ojos rojos y oxido
    for cara in ("front", "der", "izq", "back", "top"):
        t.mancha(ye[cara], 6, 2, 1.6, CUERO, 0.45, 0)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], HIERRO, base=0.42)
    for cara in LADOS:
        malla(t, cue[cara])
    t.dibujo(cue["front"], [". . . . . . . ."] * 3 + [". . . t t . . .", ". . t t t t . .", ". . t c c t . .",
                                                    ". . t c c t . ."] + [". . . . . . . ."] * 4 + ["a a a a a a a a"],
             {"t": (TELA_ROJA, 2), "c": (CARNE, 1), "a": (CUERO, 2)})  # tabardo roto con la herida
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.45)
        manga(t, br, HIERRO, 6, 1)
        for cara in LADOS:
            malla(t, (br[cara][0], br[cara][1], br[cara][2], 6))
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], CUERO, base=0.45)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, HIERRO, 4)
    es = t.pintar(uv("escudo"), pz["escudo"], MADERA, base=0.5, grad=0.3)
    t.dibujo(es["back"], [  # la cara que mira al jugador (la del frente del brazo)
        "m m m m m m m m m m",
        "m . . . . . . . . m",
        "m . . r r r r . . m",
        "m . r r . . r r . m",
        "m . r . c . . r . m",
        "m . . . . . . . . m",
        "m . . . . . . . . m",
        "m . . . . . c . . m",
        "m . r . . . . r . m",
        "m . r r . . r r . m",
        "m . . r r r r . . m",
        "m . . . . . . . . m",
        "m m m m m m m m m m",
    ], {"m": (HIERRO, 2), "r": (TELA_ROJA, 2), "c": (HIERRO, 4)})  # emblema raspado y clavos
    t.dibujo(es["front"], [". . . . . . . . . ."] * 5 + [". . . c . . c . . .", ". . . c . . c . . ."],
             {"c": (HIERRO, 4)})  # clavos que lo sujetan al brazo
    t.pintar(uv("flecha"), pz["flecha"], MADERA, base=0.55, grad=0)
    t.pintar(uv("pluma"), pz["pluma"], HUESO, base=0.6)
