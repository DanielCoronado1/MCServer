# ruff: noqa: F403, F405
"""Creeper de hielo: cadaver congelado con ojos lechosos, carambanos colgando de la boca y esquirlas de
hielo reventandole la espalda y el hombro."""

from motor import *

MOB = dict(
    clave="creeper_hielo",
    grupo="Creepers (mod creepers)",
    nombre="Creeper de hielo",
    escala=1.0,
    seed=622,
)

PIEZAS = dict(CREEPER, esquirla=(3, 11, 3), esquirla_chica=(2, 7, 2), carambano=(1, 4, 1))


def partes():
    S = creeper(cabeza=(4, 0, -5))
    for reg, piv, rot in (
        ("esquirla", (-1, 14, -1.6), (-40, 0, 15)),
        ("esquirla_chica", (1.8, 10, -1.6), (-55, 0, -20)),
        ("esquirla_chica", (3.4, 17, 0), (0, 0, -35)),
        ("esquirla", (1, 17.5, -1), (-20, 0, -10)),
    ):
        S.append(dict(r=reg, caja=caja_centrada(PIEZAS[reg]), piv=piv, rot=rot))
    for x in (-3, -0.5, 2.5):  # cuelgan de la mandibula
        S.append(
            dict(
                r="carambano", caja=(-0.5, -4, -0.5, 0.5, 0, 0.5), piv=(x, 18, 3.6), rot=(4, 0, -5)
            )
        )
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], HIELO, base=0.55, var=0.25)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". k . . . . k .",
            ". K K . . K K .",
            ". K B . . B K .",
            ". . s K K s . .",
            ". K B K K B K .",
            ". K c c c c K .",
            ". K B K K B K .",
        ],
        dict(CARA_CREEPER, B=(HIELO, 5)),
    )
    t.mancha(cab["izq"], 4, 5, 2, CARNE, 0.55)  # carne congelada bajo una grieta
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], HIELO, base=0.5, var=0.25)
    t.mancha(cue["front"], 4, 6, 3, CARNE, 0.5)
    t.costillas(cue["front"], 2, 4, 4, 3)
    t.mancha(cue["back"], 3.5, 5, 3.2, HIELO, 0.25)  # donde revientan las esquirlas
    t.columna(cue["back"], 3)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], HIELO, base=0.45, var=0.25, grad=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.garras(pa[cara])
    for n in ("esquirla", "esquirla_chica"):
        t.cristal(regiones(*uv(n), *pz[n]), HIELO)
    t.cristal(regiones(*uv("carambano"), *pz["carambano"]), HIELO)
