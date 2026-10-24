# ruff: noqa: F403, F405
"""Gusano de regolito (Kepler): asoma del suelo arqueado sobre el jugador. Placas de roca gris con carne
entre los anillos y una boca circular llena de dientes en espiral. No tiene ojos."""

from motor import *

MOB = dict(
    clave="gusano_regolito",
    grupo="Kepler (mod space)",
    nombre="Gusano de regolito",
    escala=1.0,
    seed=833,
)

PIEZAS = dict(
    anillo1=(10, 4, 10),
    anillo2=(9, 4, 9),
    anillo3=(8, 4, 8),
    anillo4=(8, 4, 8),
    anillo5=(7, 4, 7),
    cabeza=(8, 6, 8),
    escombro=(3, 2, 3),
    escombro_chico=(2, 1, 2),
)


def partes():
    S = []
    piv, rot = (0, 0, 0), (0, 0, 0)
    for n, inclina in (
        ("anillo1", 4),
        ("anillo2", 10),
        ("anillo3", 16),
        ("anillo4", 20),
        ("anillo5", 22),
    ):
        w = PIEZAS[n][0]
        rot = (rot[0] + inclina, 0, 0)
        S.append(dict(r=n, caja=(-w / 2, 0, -w / 2, w / 2, 4, w / 2), piv=piv, rot=rot))
        piv = en(piv, rot, (0, 3.8, 0))
    rot = (rot[0] + 26, 0, 0)  # la cabeza se dobla hacia el jugador
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 6, 4), piv=piv, rot=rot))
    for x, z, r in ((-6.5, 2, 15), (6, -1, -20), (-2, -6.5, 10), (5, 5.5, -8)):
        S.append(
            dict(r="escombro", caja=(-1.5, 0, -1.5, 1.5, 2, 1.5), piv=(x, 0, z), rot=(0, 0, r))
        )
    for x, z in ((-7.5, -4), (8, 3), (2, 7.5)):
        S.append(dict(r="escombro_chico", caja=(-1, 0, -1, 1, 1, 1), piv=(x, 0, z)))
    return S


BOCA = [
    ". . W W W W . .",
    ". W c c c c W .",
    "W c K W W K c W",
    "W c W K K W c W",
    "W c W K K W c W",
    "W c K W W K c W",
    ". W c c c c W .",
    ". . W W W W . .",
]


def pintar(t, uv, pz):
    for n in ("anillo1", "anillo2", "anillo3", "anillo4", "anillo5"):
        an = t.pintar(uv(n), pz[n], REGOLITO, base=0.5, grad=0.5)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = an[cara]
            for i in range(rw):  # carne humeda entre anillo y anillo
                t.set(x0 + i, y0 + rh - 1, CARNE, 1 + (i % 3 == 0))
        t.rellenar(an["top"], CARNE, 1)
    cab = t.pintar(uv("cabeza"), pz["cabeza"], REGOLITO, base=0.55, grad=0.4)
    t.dibujo(
        cab["top"], BOCA, {"W": (HUESO, 3), "c": (CARNE, 2), "K": (NEGRO, 0)}
    )  # la boca mira hacia el frente
    for cara in ("front", "der", "izq", "back"):
        x0, y0, rw, rh = cab[cara]
        for i in range(rw):
            t.set(x0 + i, y0 + rh - 1, CARNE, 1)
            if i % 2 == 0:
                t.set(x0 + i, y0, HUESO, 2)  # dientes asomando por el borde
    for n in ("escombro", "escombro_chico"):
        t.pintar(uv(n), pz[n], REGOLITO, base=0.45)
