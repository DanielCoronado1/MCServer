# ruff: noqa: F403, F405
"""Centinela flotante (Kepler): un ojo gigante de carne palida que flota, con un halo de cristales
clavados alrededor y tentaculos colgando. Dispara desde lejos y nunca parpadea."""
from motor import *

MOB = dict(clave="centinela_flotante", grupo="Kepler (mod space)", nombre="Centinela flotante", escala=1.0, seed=844)

PIEZAS = dict(ojo=(8, 8, 8), parpado=(9, 2, 9), cristal=(2, 6, 2), cristal_chico=(2, 4, 2), tentaculo=(1, 9, 1),
              tentaculo_corto=(1, 6, 1))

def partes():
    base = 12
    S = [dict(r="ojo", caja=(-4, 0, -4, 4, 8, 4), piv=(0, base, 0)),
         dict(r="parpado", caja=(-4.5, 6.5, -4.5, 4.5, 8.5, 4.5), piv=(0, base, 0), rot=(6, 0, 0))]
    for k, (x, z) in enumerate(((-4.6, 2), (4.6, 1), (-3.5, -4), (3.5, -4), (0, -4.8), (-4.6, -1.5))):
        reg = "cristal" if k % 2 == 0 else "cristal_chico"
        sx = -1 if x < 0 else 1
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg]), piv=(x, base + 3 + (k % 3), z),
                      rot=(-30 if z < 0 else 15, 0, -sx * 50)))
    for k, (x, z) in enumerate(((-2, 1.5), (2, 1.5), (0, -1.5), (-2.5, -2), (2.5, -2.5), (0.5, 2.5))):
        reg = "tentaculo" if k % 2 == 0 else "tentaculo_corto"
        largo = PIEZAS[reg][1]
        S.append(dict(r=reg, caja=(-0.5, -largo, -0.5, 0.5, 0, 0.5), piv=(x, base + 0.5, z),
                      rot=(z * 4, 0, -x * 5)))
    return S

def pintar(t, uv, pz):
    ojo = t.pintar(uv("ojo"), pz["ojo"], PALIDO, base=0.55)
    t.dibujo(ojo["front"], [
        ". . . . . . . .",
        ". k K K K K k .",
        ". K W W W W K .",
        "K W I R R I W K",
        "K W R K K R W K",
        ". K W I I W K .",
        ". k K K K K k .",
        ". . c . . c . .",
    ], {"K": (NEGRO, 0), "k": -2, "W": (HUESO, 4), "R": (OJO, 2), "I": (OJO, 1), "c": (CARNE, 1)})
    for cara in ("der", "izq", "back", "top"):
        t.mancha(ojo[cara], 3, 4, 1.8, CARNE, 0.4)
        t.mancha(ojo[cara], 6, 2, 1.2, CARNE, 0.35)
    t.rellenar(ojo["bottom"], CARNE, 1)
    pa = t.pintar(uv("parpado"), pz["parpado"], PALIDO, base=0.4, grad=0.4)
    x0, y0, rw, rh = pa["front"]
    for i in range(rw):
        t.set(x0 + i, y0 + rh - 1, CARNE, 1)
    for n in ("cristal", "cristal_chico"):
        t.cristal(regiones(*uv(n), *pz[n]), CIAN)
    for n in ("tentaculo", "tentaculo_corto"):
        te = t.pintar(uv(n), pz[n], PALIDO, base=0.5, grad=0.8)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = te[cara]
            for j in range(rh - 3, rh):
                t.set(x0, y0 + j, CARNE, 2 - (j == rh - 1))
