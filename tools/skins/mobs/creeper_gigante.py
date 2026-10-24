# ruff: noqa: F403, F405
"""Creeper gigante mutado: patas de arana con rodilla, costillas expuestas, columna de puas y tumores en los hombros."""

from motor import *

MOB = dict(
    clave="creeper_gigante",
    grupo="Creepers (mod creepers)",
    nombre="Creeper gigante mutado",
    escala=2.0,
    seed=404,
)

PIEZAS = dict(
    cabeza=(8, 8, 8),
    cuerpo=(8, 14, 4),
    muslo=(3, 10, 3),
    canilla=(3, 20, 3),
    pua=(2, 4, 2),
    tumor=(4, 4, 4),
)


def partes():
    S = []
    # patas de arana: el muslo sale hacia afuera y arriba, la canilla baja al suelo
    for sx, sz in ((-1, 1), (1, 1), (-1, -1), (1, -1)):
        cadera, rot_m = (sx * 3, 16, sz * 1), (sz * 35, 0, -sx * 62)
        rodilla = en(cadera, rot_m, (0, 9.5, 0))
        S.append(dict(r="muslo", caja=(-1.5, 0, -1.5, 1.5, 10, 1.5), piv=cadera, rot=rot_m))
        S.append(
            dict(
                r="canilla",
                caja=(-1.5, -20, -1.5, 1.5, 0, 1.5),
                piv=rodilla,
                rot=(-sz * 12, 0, sx * 10),
            )
        )
        S.append(dict(r="pua", caja=(-1, 0, -1, 1, 4, 1), piv=rodilla, rot=(sz * 20, 0, -sx * 25)))
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 14, 2), piv=(0, 15, 0), rot=(8, 0, 0)))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=(0, 28.5, 1.5), rot=(12, 0, 0)))
    for y, ang in ((18, -40), (22, -45), (26, -50)):
        S.append(dict(r="pua", caja=(-1, 0, -1, 1, 4, 1), piv=(0, y, -2.4), rot=(ang, 0, 0)))
    for sx in (-1, 1):
        S.append(
            dict(r="tumor", caja=(-2, -2, -2, 2, 2, 2), piv=(sx * 4.6, 26, 0), rot=(0, 0, sx * 15))
        )
        S.append(
            dict(r="pua", caja=(-1, 0, -1, 1, 4, 1), piv=(sx * 4.8, 27.5, 0), rot=(0, 0, sx * -35))
        )
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], VERDE, base=0.55, esc=1.8)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". k . . . . k .",
            ". K K . . K K .",
            ". K R . . R K .",
            ". . s K K s . .",
            ". K W K K W K .",
            ". K c c c c K .",
            ". K W K K W K .",
        ],
        {"K": (NEGRO, 0), "k": -2, "s": -1, "R": (OJO, 2), "W": (HUESO, 3), "c": (CARNE, 1)},
    )
    t.mancha(cab["top"], 5, 3, 2.2, CARNE, 0.45)
    t.mancha(cab["der"], 2, 5, 1.8, CARNE, 0.4)
    t.sombra(cab["front"], 1, "bottom", 1)

    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], VERDE, base=0.5, esc=1.8)
    t.mancha(cue["front"], 4, 6.5, 4.2, CARNE, 0.35)
    t.costillas(cue["front"], 2, 3, 4, 4)
    t.sombra(cue["front"], 2, "top", 2)
    t.mancha(cue["back"], 4, 7, 3, CARNE, 0.3)
    t.columna(cue["back"], 3)
    for cara in ("der", "izq"):
        t.mancha(cue[cara], 2, 10, 1.6, CARNE, 0.4)

    mus = t.pintar(uv("muslo"), pz["muslo"], VERDE, base=0.5, grad=0.2, esc=1.8)
    for cara in ("front", "der", "izq", "back"):
        t.sombra(mus[cara], 2, "top", 2)
    can = t.pintar(uv("canilla"), pz["canilla"], VERDE, base=0.5, grad=0.7, esc=1.8)
    for cara in ("front", "der", "izq", "back"):
        t.sombra(can[cara], 2, "top", 2)
        t.garras(can[cara], 2)

    t.hueso(regiones(*uv("pua"), *pz["pua"]))
    tum = t.pintar(uv("tumor"), pz["tumor"], CARNE, base=0.65, grad=0.4)
    t.mancha(tum["top"], 2, 2, 1.2, VERDE, 0.5, 0)
