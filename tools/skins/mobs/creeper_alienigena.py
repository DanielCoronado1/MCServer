# ruff: noqa: F403, F405
"""Creeper alienigena (Kepler): piel negra-azulada con glandulas de acido brillando en el lomo, cuatro patas
de insecto, mandibulas y cuatro ojos. Su explosion deja charcos de acido."""

from motor import *

MOB = dict(
    clave="creeper_alienigena",
    grupo="Kepler (mod space)",
    nombre="Creeper alienígena",
    escala=1.0,
    seed=877,
)

PIEZAS = dict(
    cabeza=(8, 8, 8),
    cuerpo=(8, 11, 4),
    muslo=(2, 6, 2),
    canilla=(2, 8, 2),
    mandibula=(1, 3, 2),
    glandula=(3, 3, 3),
    glandula_chica=(2, 2, 2),
)


def partes():
    S = [dict(r="cuerpo", caja=(-4, 0, -2, 4, 11, 2), piv=(0, 7, 0), rot=(4, 0, 0))]
    p_cab = en((0, 7, 0), (4, 0, 0), (0, 11, 0))
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=p_cab, rot=(-6, 0, 0)))
    for sx in (-1, 1):
        S.append(
            dict(
                r="mandibula",
                caja=(-0.5, -3, 0, 0.5, 0, 2),
                piv=(sx * 2.5, p_cab[1] + 0.5, 3.8),
                rot=(20, 0, sx * 20),
            )
        )
        for sz in (-1, 1):
            cadera, r_m = (sx * 3, 8, sz * 1.2), (sz * 30, 0, -sx * 60)
            rodilla = en(cadera, r_m, (0, 5.6, 0))
            S.append(dict(r="muslo", caja=(-1, 0, -1, 1, 6, 1), piv=cadera, rot=r_m))
            S.append(
                dict(
                    r="canilla", caja=(-1, -8, -1, 1, 0, 1), piv=rodilla, rot=(-sz * 10, 0, sx * 12)
                )
            )
    for reg, piv, rot in (
        ("glandula", (-1.5, 15, -2.4), (-25, 0, 10)),
        ("glandula_chica", (2, 11, -2.2), (-30, 0, -15)),
        ("glandula", (1.5, 18.5, -1.8), (-15, 0, -10)),
        ("glandula_chica", (-2, 9.5, -2.2), (-35, 0, 20)),
    ):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg], -1), piv=piv, rot=rot))
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], QUITINA, base=0.5)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". A . . . . A .",
            ". K K . . K K .",
            ". K A . . A K .",
            ". . s K K s . .",
            ". K a K K a K .",
            ". K a a a a K .",
            ". K . K K . K .",
        ],
        dict(CARA_CREEPER, A=(ACIDO, 4), a=(ACIDO, 2)),
    )
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], QUITINA, base=0.45)
    t.dibujo(
        cue["front"],
        [". . a . . . . .", ". . a . . . . .", ". . A . . . . .", ". . . . . . . ."],
        {"a": (ACIDO, 2), "A": (ACIDO, 3)},
    )  # baba de acido
    for cara in ("back", "der", "izq"):
        t.mancha(cue[cara], cue[cara][2] / 2, 5, 1.8, ACIDO, 0.45)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("muslo", "canilla"):
        pa = t.pintar(uv(n), pz[n], QUITINA, base=0.5, grad=0.4)
        if n == "canilla":
            for cara in ("front", "der", "izq", "back"):
                t.garras(pa[cara], 2)
    t.hueso(regiones(*uv("mandibula"), *pz["mandibula"]))
    for n in ("glandula", "glandula_chica"):
        gl = t.pintar(uv(n), pz[n], ACIDO, base=0.6, var=0.25, grad=0.5)
        t.rellenar(gl["bottom"], QUITINA, 1)
