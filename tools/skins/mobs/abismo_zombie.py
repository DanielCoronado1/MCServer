# ruff: noqa: F403, F405
"""Ahogado del abismo: hinchado y verdoso de tanto tiempo bajo el agua, con algas colgando, percebes en la
piel, ojos de pez que brillan y una cadena oxidada en la muneca con la que te arrastra al fondo."""

from _zombies import *
from motor import *

MOB = dict(
    clave="abismo_zombie",
    grupo="Zombies (mod zombies)",
    nombre="Ahogado del abismo",
    escala=1.0,
    seed=904,
)

PIEZAS = dict(
    HUMANOIDE,
    alga=(1, 9, 1),
    alga_corta=(1, 5, 1),
    cadena=(1, 8, 2),
    ancla=(5, 6, 1),
    mandibula=(8, 2, 6),
)

CABEZA = (-14, 0, 10)  # la cabeza cae hacia atras, la boca abierta


def partes():
    S = humanoide(brazo_d=(-115, 0, -6), brazo_i=(-70, 0, 10), cabeza=CABEZA)
    S.append(
        dict(
            r="mandibula",
            caja=(-4, -2, -2, 4, 0, 4),
            piv=en((0, 24, 0), CABEZA, (0, 0.5, 2)),
            rot=(CABEZA[0] + 30, 0, CABEZA[2]),
        )
    )
    for x, z, reg in (
        (-3, 3.5, "alga"),
        (2, 4, "alga_corta"),
        (3.5, -2, "alga"),
        (-1, -4, "alga_corta"),
    ):
        S.append(
            dict(
                r=reg,
                caja=(-0.5, -PIEZAS[reg][1], -0.5, 0.5, 0, 0.5),
                piv=en((0, 24, 0), CABEZA, (x, 8, z)),
                rot=(0, 0, 4),
            )
        )
    for x, z in ((-4.5, 1), (4.5, -1)):  # algas colgando de los hombros
        S.append(dict(r="alga", caja=(-0.5, -9, -0.5, 0.5, 0, 0.5), piv=(x, 24, z), rot=(0, 0, -x)))
    p_muneca = en((-6, 22, 0), (-115, 0, -6), (0, -9.5, 0))
    S.append(dict(r="cadena", caja=(-0.5, -8, -1, 0.5, 0, 1), piv=p_muneca, rot=(8, 0, 6)))
    S.append(
        dict(
            r="ancla",
            caja=(-2.5, -6, -0.5, 2.5, 0, 0.5),
            piv=en(p_muneca, (8, 0, 6), (0, -7.5, 0)),
            rot=(8, 0, 6),
        )
    )
    return S


def percebes(t, rect, puntos):
    for cx, cy in puntos:
        t.dibujo((rect[0] + cx, rect[1] + cy), ["b B", "B b"], {"b": (HUESO, 1), "B": (HUESO, 3)})


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], TEAL, base=0.45)
    t.dibujo(
        cab["front"],
        [
            ". a . . a . . .",
            ". . . . . . . .",
            ". K K . . K K .",
            "K E e . . e E K",
            ". K K . . K K .",
            ". . s . . s . .",
            ". . K K K K . .",
            ". W . W W . W .",
        ],
        dict(CARA, E=(CIAN, 4), e=(CIAN, 2), a=(ALGA, 2)),
    )  # ojos de pez, brillando
    percebes(t, cab["izq"], ((2, 3), (5, 5)))
    t.mancha(cab["top"], 4, 4, 3.2, ALGA, 0.45)
    man = t.pintar(uv("mandibula"), pz["mandibula"], TEAL, base=0.4)
    t.dibujo(
        man["front"], ["W . W . . W . W", "c c c c c c c c"], {"W": (HUESO, 3), "c": (CARNE, 1)}
    )
    t.rellenar(man["top"], CARNE, 1)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], TEAL, base=0.45)
    t.dibujo(
        cue["front"],
        [". . . . . . . ."] * 2
        + [
            "a a a a a a a a",
            ". . . . . . . .",
            "a a a a a a a a",
            ". . . . . . . .",
            "a a a a a a a a",
        ]
        + [". . . . . . . ."] * 5,
        {"a": (HUESO, 2)},
    )  # camiseta de marinero a rayas, desteñida
    t.mancha(cue["front"], 4.5, 9, 2.4, CARNE, 0.4)
    percebes(t, cue["front"], ((1, 1), (5, 6)))
    percebes(t, cue["back"], ((2, 3), (4, 8)))
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], TEAL, base=0.45)
        manga(t, br, DENIM, 3, 1)
        percebes(t, br["izq"], ((1, 6),))
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], TEAL, base=0.4)
    pantalon(t, pi, DENIM, roto_desde=7, idx=1)
    for cara in LADOS:
        t.garras(pi[cara])
    for n in ("alga", "alga_corta"):
        t.pintar(uv(n), pz[n], ALGA, base=0.55, grad=0.6)
    ca = t.pintar(uv("cadena"), pz["cadena"], HIERRO, base=0.4, grad=0)
    for cara in LADOS:
        x0, y0, rw, rh = ca[cara]
        for j in range(rh):  # eslabones con oxido
            for i in range(rw):
                t.set(x0 + i, y0 + j, CUERO if j % 3 == 2 else HIERRO, 2 if j % 3 != 1 else 1)
    an = t.pintar(uv("ancla"), pz["ancla"], HIERRO, base=0.35, grad=0.3)
    for cara in ("front", "back"):
        t.dibujo(
            an[cara],
            ["X X a X X", "X X a X X", "X X a X X", "a X a X a", "a a a a a", "X a a a X"],
            {"X": "X", "a": (HIERRO, 2)},
        )
        t.mancha(an[cara], 2.5, 4, 1.2, CUERO, 0.5, 0)
