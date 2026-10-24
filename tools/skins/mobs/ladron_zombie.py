# ruff: noqa: F403, F405
"""Ladron: encorvado y escurridizo, con un panuelo negro que le tapa la boca cosida, brazos demasiado
largos con dedos de hueso y un saco al hombro que se mueve, lleno de lo que le robo a los jugadores."""

from _zombies import *
from motor import *

MOB = dict(
    clave="ladron_zombie", grupo="Zombies (mod zombies)", nombre="Ladrón", escala=1.0, seed=902
)

PIEZAS = dict(
    cabeza=(8, 8, 8),
    capucha=(9, 9, 9),
    cuerpo=(8, 12, 4),
    brazo_largo=(3, 15, 3),
    pierna=(4, 12, 4),
    saco=(6, 7, 5),
    cuerda=(1, 10, 1),
)


def partes():
    r_t = (18, 0, 0)  # encorvado
    S = [
        dict(r="pierna", caja=(-2, -12, -2, 2, 0, 2), piv=(sx * 2, 12, 0), rot=(sx * 6, 0, 0))
        for sx in (-1, 1)
    ]
    S.append(dict(r="cuerpo", caja=(-4, 0, -2, 4, 12, 2), piv=(0, 12, 0), rot=r_t))
    p_cab, r_c = en((0, 12, 0), r_t, (0, 12, 0.5)), (-4, 0, 0)
    S.append(dict(r="cabeza", caja=(-4, 0, -4, 4, 8, 4), piv=p_cab, rot=r_c))
    S.append(dict(r="capucha", caja=(-4.5, -0.5, -4.5, 4.5, 8.5, 4.5), piv=p_cab, rot=r_c))
    for sx, r_b in ((-1, (-55, 0, 8)), (1, (-20, 0, -10))):
        S.append(
            dict(
                r="brazo_largo",
                caja=(-1.5, -14, -1.5, 1.5, 1, 1.5),
                piv=en((0, 12, 0), r_t, (sx * 5.5, 11, 0)),
                rot=r_b,
            )
        )
    S.append(
        dict(
            r="saco",
            caja=(-3, -3.5, -5, 3, 3.5, 0),
            piv=en((0, 12, 0), r_t, (1, 8, -2)),
            rot=(r_t[0] - 10, 0, -12),
        )
    )
    S.append(
        dict(
            r="cuerda",
            caja=(-0.5, 0, -0.5, 0.5, 10, 0.5),
            piv=en((0, 12, 0), r_t, (-3, 2, 2.2)),
            rot=(r_t[0], 0, -42),
        )
    )
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". . . . . . . .",
            ". k . . . . k .",
            "K K R . . R K K",
            ". s . . . . s .",
            "N N N N N N N N",
            "N n N N N N n N",
            "N N n N N n N N",
        ],
        dict(CARA, N=(NEGRO, 1), n=(NEGRO, 2)),
    )  # panuelo negro tapando la boca
    ca = t.pintar(uv("capucha"), pz["capucha"], CARBON, base=0.45)
    x0, y0, rw, rh = ca["front"]  # hueco de la cara: solo se ven los ojos
    t.dibujo((x0 + 1, y0 + 1), ["X " * 7] * 8, {"X": "X"})
    t.dibujo((ca["bottom"][0] + 1, ca["bottom"][1] + 1), ["X " * 7] * 7, {"X": "X"})
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CARBON, base=0.5)
    t.dibujo(
        cue["front"],
        [
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            "a a a a a a a a",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        ],
        {"a": (CUERO, 2)},
    )
    t.mancha(cue["front"], 2.5, 8.5, 2, CARNE, 0.4)
    t.sombra(cue["front"], 2, "top", 2)
    br = t.pintar(uv("brazo_largo"), pz["brazo_largo"], PIEL, base=0.45)
    manga(t, br, CARBON, 6)
    for cara in LADOS:  # dedos largos de hueso
        x0, y0, rw, rh = br[cara]
        for j in range(rh - 4, rh):
            for i in range(rw):
                t.set(x0 + i, y0 + j, HUESO, 3 - (j == rh - 1) * 2 if i != 1 else 0)
    pi = t.pintar(uv("pierna"), pz["pierna"], CARBON, base=0.4)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
    botas(t, pi, CUERO, 2)
    sa = t.pintar(uv("saco"), pz["saco"], CAFE, base=0.55, grad=0.45)
    t.dibujo(
        sa["top"],
        [". . . . . .", ". o o . . .", ". o O . d .", ". . . d D .", ". . . . . ."],
        {"o": (ORO, 2), "O": (ORO, 4), "d": (CIAN, 3), "D": (CIAN, 4)},
    )  # oro y diamantes asomando
    t.dibujo(
        sa["back"],
        [
            ". . . . . .",
            ". . . . . .",
            ". . a . . .",
            ". a . . a .",
            ". . . a . .",
            ". . . . . .",
            ". . . . . .",
        ],
        {"a": (CAFE, 0)},
    )  # costuras
    t.mancha(sa["der"], 2.5, 4, 1.4, CARNE, 0.35, 0)  # una mancha que no deberia estar ahi
    t.pintar(uv("cuerda"), pz["cuerda"], CAFE, base=0.4, grad=0)
