# ruff: noqa: F403, F405
"""Explorador nocturno: alto y flaco, envuelto en una capa con capucha tan oscura que solo le ves los ojos rojos.
La capa se deshace en jirones transparentes porque se vuelve invisible; garras largas y un farol apagado."""

from _zombies import *
from motor import *

MOB = dict(
    clave="explorador_zombie",
    grupo="Zombies (mod zombies)",
    nombre="Explorador nocturno",
    escala=1.0,
    seed=916,
)

PIEZAS = dict(
    cabeza=(8, 8, 8),
    capucha=(9, 10, 9),
    cuerpo=(8, 13, 4),
    capa=(10, 22, 6),
    brazo=(3, 15, 3),
    pierna=(3, 14, 3),
    farol=(3, 4, 3),
    asa=(1, 3, 1),
)

NOCHE = [(6, 8, 16), (12, 16, 30), (20, 26, 44), (32, 40, 62), (48, 58, 84), (70, 82, 108)]


def partes():
    S = [
        dict(r="pierna", caja=(-1.5, -14, -1.5, 1.5, 0, 1.5), piv=(sx * 2, 14, 0)) for sx in (-1, 1)
    ]
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 13, 2), piv=(0, 14, 0), rot=(10, 0, 0)))
    p_cab, r_c = en((0, 14, 0), (10, 0, 0), (0, 13, 0.5)), (14, 0, 0)
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=p_cab, rot=r_c))
    S.append(dict(r="capucha", caja=(-4.5, -0.5, -4.5, 4.5, 9.5, 4.5), piv=p_cab, rot=r_c))
    S.append(
        dict(
            r="capa",
            caja=(-5, -22, -3, 5, 0, 3),
            piv=en((0, 14, 0), (10, 0, 0), (0, 13, 0)),
            rot=(6, 0, 0),
        )
    )
    for sx, r_b in ((-1, (-70, 0, 6)), (1, (-25, 0, -8))):
        S.append(
            dict(
                r="brazo",
                caja=(-1.5, -14, -1.5, 1.5, 1, 1.5),
                piv=en((0, 14, 0), (10, 0, 0), (sx * 5.5, 12, 0)),
                rot=r_b,
            )
        )
    p_mano = en(en((0, 14, 0), (10, 0, 0), (5.5, 12, 0)), (-25, 0, -8), (0, -13.5, 0))
    S.append(dict(r="asa", caja=(-0.5, -3, -0.5, 0.5, 0, 0.5), piv=p_mano))
    S.append(dict(r="farol", caja=(-1.5, -7, -1.5, 1.5, -3, 1.5), piv=p_mano))
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], NOCHE, base=0.3)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". R R . . R R .",
            ". r R . . R r .",
            ". . . . . . . .",
            ". . W . W . W .",
            ". . . . . . . .",
        ],
        dict(CARA, R=(OJO, 3), r=(OJO, 2), W=(HUESO, 1)),
    )  # solo los ojos y un brillo de dientes
    ca = t.pintar(uv("capucha"), pz["capucha"], NOCHE, base=0.45)
    t.dibujo((ca["front"][0] + 1, ca["front"][1] + 1), ["X " * 7] * 9, {"X": "X"})
    t.dibujo((ca["bottom"][0] + 1, ca["bottom"][1] + 1), ["X " * 7] * 7, {"X": "X"})
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], NOCHE, base=0.35)
    t.costillas(cue["front"], 2, 3, 4, 3)
    t.sombra(cue["front"], 2, "top", 2)
    cp = t.pintar(uv("capa"), pz["capa"], NOCHE, base=0.45, grad=0.4)
    aro_hueco(t, cp)
    for cara in LADOS:  # se desvanece: cuanto mas abajo, mas huecos
        x0, y0, rw, rh = cp[cara]
        for j in range(rh // 2, rh):
            for i in range(rw):
                if (i * 7 + j * 3) % 11 < (j - rh // 2) * 11 // (rh - rh // 2):
                    t.color(x0 + i, y0 + j, (0, 0, 0, 0))
    x0, y0, rw, rh = cp["front"]  # la capa esta abierta al frente
    t.dibujo((x0 + 2, y0), ["X " * 6] * rh, {"X": "X"})
    br = t.pintar(uv("brazo"), pz["brazo"], NOCHE, base=0.4)
    for cara in LADOS:
        x0, y0, rw, rh = br[cara]
        for j in range(rh - 4, rh):  # garras largas
            for i in range(rw):
                t.set(x0 + i, y0 + j, HUESO, 2 - (j == rh - 1) * 2 if i != 1 else 0)
    pi = t.pintar(uv("pierna"), pz["pierna"], NOCHE, base=0.3)
    for cara in LADOS:
        t.garras(pi[cara])
    fa = t.pintar(uv("farol"), pz["farol"], HIERRO, base=0.35)
    for cara in LADOS:
        x0, y0, rw, rh = fa[cara]
        t.dibujo((x0, y0 + 1), [". K .", ". K ."], {"K": (NEGRO, 1)})  # vidrio apagado
    t.pintar(uv("asa"), pz["asa"], HIERRO, base=0.4)
