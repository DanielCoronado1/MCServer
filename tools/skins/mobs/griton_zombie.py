# ruff: noqa: F403, F405
"""Griton: la cabeza echada hacia atras y la boca abierta de forma imposible, la mandibula colgando hasta el
pecho y la garganta desgarrada de tanto gritar. Su grito llama a todos los zombies cercanos."""

from _zombies import *
from motor import *

MOB = dict(
    clave="griton_zombie", grupo="Zombies (mod zombies)", nombre="Gritón", escala=1.0, seed=907
)

PIEZAS = dict(HUMANOIDE, mandibula=(8, 2, 7), cuello=(4, 3, 4))

CABEZA = (-40, 0, 6)


def partes():
    S = humanoide(brazo_d=(-30, 0, 25), brazo_i=(-30, 0, -25), cabeza=CABEZA)
    for p in S:
        if p["r"] == "cabeza":
            p["piv"] = (0, 26.5, 0)  # cuello estirado
    S.append(dict(r="cuello", caja=(-2, -0.5, -2, 2, 2.5, 2), piv=(0, 24, 0)))
    S.append(
        dict(
            r="mandibula",
            caja=(-4, -2, -2, 4, 0, 5),
            piv=en((0, 26.5, 0), CABEZA, (0, 0.5, -1)),
            rot=(CABEZA[0] + 70, 0, CABEZA[2]),
        )
    )
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.42)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            "K K K . . K K K",
            "K R K . . K R K",
            ". K . . . . K .",
            ". . c K K c . .",
            ". W K K K K W .",
            "W K K C C K K W",
            "K K K K K K K K",
        ],
        CARA,
    )  # ojos desorbitados, boca abierta hasta abajo
    t.rellenar(cab["bottom"], CARNE, 1)
    t.dibujo(cab["bottom"], ["W . W . . W . W"], {"W": (HUESO, 3)})
    man = t.pintar(uv("mandibula"), pz["mandibula"], PIEL, base=0.38)
    t.dibujo(
        man["front"], ["W W . W W . W W", "c c c c c c c c"], {"W": (HUESO, 3), "c": (CARNE, 1)}
    )
    t.rellenar(man["top"], CARNE, 2)
    t.dibujo(
        man["top"],
        [". . . . . . . .", ". . . c c . . .", ". . c C C c . .", ". . . c c . . ."],
        {"c": (CARNE, 3), "C": (CARNE, 4)},
    )  # lengua
    cu = t.pintar(uv("cuello"), pz["cuello"], PIEL, base=0.38)
    t.rellenar(cu["front"], CARNE, 1)
    t.dibujo(
        cu["front"], [". H H .", "c . . c", ". c c ."], {"H": (HUESO, 2), "c": (CARNE, 2)}
    )  # garganta abierta
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], PIEL, base=0.42)
    pantalon(t, cue, PURPURA, roto_desde=None, idx=1)
    t.mancha(cue["front"], 4, 2.5, 3, CARNE, 0.35)  # la sangre le cayo desde la garganta
    t.dibujo(
        cue["front"],
        [". . . . . . . ."] * 3 + [". . . c . c . .", ". . . c . . . .", ". . . c . . . ."],
        {"c": (CARNE, 1)},
    )
    t.costillas(cue["front"], 2, 6, 4, 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.42)
        manga(t, br, PURPURA, 4, 1)
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], PIEL, base=0.4)
    pantalon(t, pi, CARBON, roto_desde=9, idx=2)
    for cara in LADOS:
        t.garras(pi[cara])
