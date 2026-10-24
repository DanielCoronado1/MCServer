# ruff: noqa: F403, F405
"""Bruja zombie: la que remienda a los demas zombies. Tiene el cuerpo cosido a pedazos con hilo negro,
sombrero torcido con un ojo de vidrio colgando, nariz larga y frascos de pocion verde al cinto."""
from motor import *
from _zombies import *

MOB = dict(clave="bruja_zombie", grupo="Zombies (mod zombies)", nombre="Bruja zombie", escala=1.0, seed=908)

PIEZAS = dict(HUMANOIDE, ala=(12, 1, 12), copa=(7, 5, 7), punta=(4, 4, 4), nariz=(2, 3, 2), frasco=(2, 3, 2),
              tapon=(1, 1, 1), tunica=(9, 7, 5))

CABEZA = (4, 0, 10)

def partes():
    S = humanoide(brazo_d=(-95, 0, 10), brazo_i=(-40, 0, -6), cabeza=CABEZA)
    for reg, caja, rot in (("ala", (-6, 7.5, -6, 6, 8.5, 6), (0, 0, 0)), ("copa", (-3.5, 8.5, -3.5, 3.5, 13.5, 3.5), (-6, 0, 0)),
                           ("nariz", (-1, 1.5, 4, 1, 4.5, 6), (0, 0, 0))):
        S.append(dict(r=reg, caja=caja, piv=(0, 24, 0), rot=(CABEZA[0] + rot[0], 0, CABEZA[2])))
    S.append(dict(r="punta", caja=(-2, 0, -2, 2, 4, 2), piv=en((0, 24, 0), (CABEZA[0] - 6, 0, CABEZA[2]), (0, 13, -1)),
                  rot=(CABEZA[0] - 35, 0, CABEZA[2] + 10)))
    S.append(dict(r="tunica", caja=(-4.5, -6, -2.5, 4.5, 1, 2.5), piv=(0, 13, 0)))
    for x, z in ((-3.5, 2.6), (-0.5, 2.8), (3.8, 1.5)):
        S.append(dict(r="frasco", caja=(-1, -3, -1, 1, 0, 1), piv=(x, 12.5, z), rot=(5, 0, x * 2)))
        S.append(dict(r="tapon", caja=(-0.5, 0, -0.5, 0.5, 1, 0.5), piv=(x, 12.5, z), rot=(5, 0, x * 2)))
    return S

COSTURA = {"x": (NEGRO, 1), "c": (CARNE, 2)}

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". . . . . . . .",
        "K K . . . x K K",
        "B K . . x . R K",
        ". . . . x . . .",
        ". s . x . . s .",
        ". K W K K W K .",
        ". . K K K K . .",
    ], dict(CARA, **COSTURA))  # costura cruzando la cara, un ojo de vidrio
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], PURPURA, base=0.45)
    t.dibujo(cue["front"], [
        ". . . . . . . .",
        ". . . p p . . .",
        ". . p x p p . .",
        ". . p p x p . .",
        ". . p x p p . .",
        ". . . p x . . .",
    ], dict(COSTURA, p=(PIEL, 3)))  # parche de piel ajena cosido al pecho
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.45)
        manga(t, br, PURPURA, 5, 2)
        for cara in LADOS:  # el brazo izquierdo es de otro cuerpo: cosido en el codo
            x0, y0, rw, rh = br[cara]
            for i in range(rw):
                t.set(x0 + i, y0 + 7, NEGRO, 1 if i % 2 == 0 else 0)
            if n == "brazo_i":
                for j in range(8, rh):
                    for i in range(rw):
                        t.ajustar(x0 + i, y0 + j, 1)
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], PURPURA, base=0.35)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, CUERO, 2)
    tu = t.pintar(uv("tunica"), pz["tunica"], PURPURA, base=0.4, grad=0.4)
    jirones(t, tu)
    for cara in LADOS:
        x0, y0, rw, rh = tu[cara]
        for i in range(rw):
            t.set(x0 + i, y0, CUERO, 2)  # cinturon
    for n in ("ala", "copa", "punta"):
        so = t.pintar(uv(n), pz[n], PURPURA, base=0.32, grad=0.3)
    x0, y0, rw, rh = regiones(*uv("copa"), *pz["copa"])["front"]
    for i in range(rw):
        t.set(x0 + i, y0 + rh - 1, TOXICO, 2)  # cinta verde
    t.dibujo((x0 + 2, y0 + 1), ["B .", "x ."], {"B": (HUESO, 4), "x": (NEGRO, 0)})  # un ojo colgando del sombrero
    t.pintar(uv("nariz"), pz["nariz"], PIEL, base=0.5)
    fr = t.pintar(uv("frasco"), pz["frasco"], TOXICO, base=0.6, var=0.2, grad=0.5)
    for cara in LADOS:
        x0, y0, rw, rh = fr[cara]
        t.set(x0, y0, HUESO, 4)
    t.pintar(uv("tapon"), pz["tapon"], MADERA, base=0.5)
