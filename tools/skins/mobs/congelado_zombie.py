# ruff: noqa: F403, F405
"""Congelado: murio de frio y el hielo lo sigue comiendo. Un brazo atrapado en un bloque de hielo, cristales
reventandole los hombros, carambanos colgando de la mandibula y escarcha en los ojos. Congela lo que toca."""

from _zombies import *
from motor import *

MOB = dict(
    clave="congelado_zombie",
    grupo="Zombies (mod zombies)",
    nombre="Congelado",
    escala=1.0,
    seed=910,
)

PIEZAS = dict(
    HUMANOIDE,
    bloque=(6, 7, 6),
    esquirla=(2, 8, 2),
    esquirla_chica=(2, 5, 2),
    carambano=(1, 4, 1),
    bufanda=(9, 2, 5),
)

CABEZA = (6, 0, -6)


def partes():
    S = humanoide(brazo_d=(-85, 0, 4), brazo_i=(-60, 0, -8), cabeza=CABEZA)
    p_mano = en((-6, 22, 0), (-85, 0, 4), (0, -7, 0))
    S.append(dict(r="bloque", caja=(-3, -3.5, -3, 3, 3.5, 3), piv=p_mano, rot=(-85, 0, 4)))
    for reg, piv, rot in (
        ("esquirla", (5.5, 24, 0), (0, 0, -28)),
        ("esquirla_chica", (4.2, 24, 1.5), (25, 0, -50)),
        ("esquirla", (-1.5, 18, -1.6), (-45, 0, 10)),
        ("esquirla_chica", (2, 15, -1.6), (-60, 0, -20)),
        ("esquirla_chica", (-5.5, 24, -1), (-15, 0, 35)),
    ):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg]), piv=piv, rot=rot))
    for x in (-2.5, 0, 2):
        S.append(
            dict(
                r="carambano",
                caja=(-0.5, -4, -0.5, 0.5, 0, 0.5),
                piv=en((0, 24, 0), CABEZA, (x, 0, 3.6)),
                rot=(0, 0, 0),
            )
        )
    S.append(dict(r="bufanda", caja=(-4.5, -1, -2.5, 4.5, 1, 2.5), piv=(0, 23.5, 0)))
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], HIELO, base=0.45, var=0.25)
    t.dibujo(
        cab["front"],
        [
            ". h . . . h . .",
            ". . . . . . . .",
            "K K K . . K K K",
            "K B K . . K B K",
            ". K s . . s K .",
            ". . s . . s . .",
            ". K W K K W K .",
            ". c K K K K c .",
        ],
        CARA,
    )  # ojos cubiertos de escarcha
    t.mancha(cab["top"], 4, 4, 3.4, HIELO, 0.85, 0)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], DENIM, base=0.4)
    t.mancha(cue["front"], 2, 2, 2.4, HIELO, 0.8, 0)
    t.mancha(cue["front"], 6, 9, 2, HIELO, 0.75, 0)
    t.mancha(cue["back"], 3, 5, 3, HIELO, 0.8, 0)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], HIELO, base=0.45, var=0.25)
        manga(t, br, DENIM, 5, 1)
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], HIELO, base=0.4, var=0.25)
    pantalon(t, pi, DENIM, idx=1)
    for cara in LADOS:
        t.mancha(pi[cara], 2, 10, 2, HIELO, 0.8, 0)  # congelado desde el suelo
    bl = t.pintar(uv("bloque"), pz["bloque"], HIELO, base=0.7, var=0.15, grad=0.3)
    for cara in ("front", "der", "izq", "back", "top", "bottom"):
        x0, y0, rw, rh = bl[cara]
        for i in range(rw):
            t.set(x0 + i, y0, HIELO, 5)
        for j in range(rh):
            t.set(x0, y0 + j, HIELO, 5)
        t.mancha(bl[cara], rw / 2, rh / 2, 1.6, PIEL, 0.45, 0)  # la mano adentro, borrosa
        t.dibujo((x0 + 1, y0 + 1), [". b", "b ."], {"b": (HIELO, 5)})
    for n in ("esquirla", "esquirla_chica", "carambano"):
        t.cristal(regiones(*uv(n), *pz[n]), HIELO)
    bu = t.pintar(uv("bufanda"), pz["bufanda"], TELA_ROJA, base=0.45)
    for cara in LADOS:
        t.mancha(bu[cara], 3, 1, 1.4, HIELO, 0.8, 0)
