# ruff: noqa: F403, F405
"""Jinete: un vaquero ahorcado. La soga todavia le cuelga del cuello partido (la cabeza ladeada en un angulo
imposible), sombrero con agujeros de bala, chaleco de cuero y la cartuchera vacia. Llega montado."""
from motor import *
from _zombies import *

MOB = dict(clave="jinete_zombie", grupo="Zombies (mod zombies)", nombre="Jinete", escala=1.0, seed=911)

PIEZAS = dict(HUMANOIDE, ala=(12, 1, 12), copa=(7, 4, 7), soga=(1, 9, 1), nudo=(3, 3, 3), cartuchera=(3, 4, 2),
              poncho=(10, 5, 6))

CABEZA = (0, 0, 38)  # cuello roto

def partes():
    S = humanoide(brazo_d=(-80, 0, 0), brazo_i=(-20, 0, -8), cabeza=CABEZA)
    S += [dict(r="ala", caja=(-6, 7.5, -6, 6, 8.5, 6), piv=(0, 24, 0), rot=CABEZA),
          dict(r="copa", caja=(-3.5, 8.5, -3.5, 3.5, 12.5, 3.5), piv=(0, 24, 0), rot=CABEZA),
          dict(r="nudo", caja=(-1.5, -1.5, -1.5, 1.5, 1.5, 1.5), piv=(2.5, 24.5, 1.5)),
          dict(r="soga", caja=(-0.5, -9, -0.5, 0.5, 0, 0.5), piv=(2.5, 24, 2.5), rot=(-10, 0, 8)),
          dict(r="poncho", caja=(-5, -5, -3, 5, 0, 3), piv=(0, 24, 0)),
          dict(r="cartuchera", caja=(-1.5, -4, -1, 1.5, 0, 1), piv=(4.2, 13, 0), rot=(0, 0, -6))]
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.4)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". . . . . . . .",
        "K K . . . K K .",
        "R K . . . K R .",
        ". s . . . s . .",
        ". . . . . . . .",
        ". K W K K W c .",
        ". . K K K c C c",
    ], CARA)  # la lengua morada asomando del lado del cuello roto
    t.dibujo(cab["izq"], [". . . . . . . ."] * 6 + ["m m m m m m m m", "c m c m c m c m"],
             {"m": (CARNE, 0), "c": (CARNE, 1)})  # la marca de la soga
    ala = t.pintar(uv("ala"), pz["ala"], CUERO, base=0.5)
    t.dibujo((ala["top"][0] + 2, ala["top"][1] + 8), ["X ."], {"X": "X"})
    co = t.pintar(uv("copa"), pz["copa"], CUERO, base=0.5)
    t.dibujo(co["front"], [". . . . . . .", ". . . . X . .", ". . . . . . .", "a a a a a a a"],
             {"X": "X", "a": (HUESO, 2)})  # agujero de bala y cinta
    t.dibujo(co["izq"], [". . . . . . .", ". X . . . . .", ". . . . . . .", "a a a a a a a"],
             {"X": "X", "a": (HUESO, 2)})
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CUADROS, base=0.45)
    t.dibujo(cue["front"], ["v v . . . . v v"] * 9 + ["a a a b a a a a", ". . . . . . . .", ". . . . . . . ."],
             {"v": (CUERO, 2), "a": (CUERO, 1), "b": (ORO, 3)})  # chaleco y cinturon con hebilla
    t.mancha(cue["front"], 4, 5, 1.6, CARNE, 0.4)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.4)
        manga(t, br, CUADROS, 7, 2)
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], DENIM, base=0.4)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, CUERO, 4)
    for cara in ("front", "back"):  # espuelas
        x0, y0, rw, rh = regiones(*uv("pierna"), *pz["pierna"])[cara]
        t.set(x0 + rw - 1, y0 + rh - 2, ORO, 3)
    t.pintar(uv("soga"), pz["soga"], PAJA, base=0.45, grad=0.2)
    t.pintar(uv("nudo"), pz["nudo"], PAJA, base=0.45)
    po = t.pintar(uv("poncho"), pz["poncho"], TELA_ROJA, base=0.4, grad=0.3)
    aro_hueco(t, po)
    jirones(t, po, 2)
    for cara in LADOS:
        x0, y0, rw, rh = po[cara]
        for i in range(0, rw, 2):
            t.set(x0 + i, y0 + 1, HUESO, 2)
    ca = t.pintar(uv("cartuchera"), pz["cartuchera"], CUERO, base=0.45)
    t.rellenar(ca["top"], NEGRO, 0)  # vacia
