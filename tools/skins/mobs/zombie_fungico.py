# ruff: noqa: F403, F405
"""Zombie fungico: un hongo enorme le brota de la cabeza ladeada, micelio comiendose medio rostro y un brazo,
hongos en el hombro y repisas en la espalda."""

from motor import *

MOB = dict(
    clave="zombie_fungico", grupo="Conceptos extra", nombre="Zombie fúngico", escala=1.0, seed=101
)

PIEZAS = dict(
    HUMANOIDE,
    tallo=(3, 4, 3),
    sombrero=(12, 3, 12),
    tallo_chico=(2, 3, 2),
    sombrero_chico=(6, 2, 6),
    sombrero_mini=(4, 2, 4),
    repisa=(4, 1, 3),
)

CABEZA = (0, 0, 12)


def partes():
    S = humanoide(brazo_d=(-85, 0, 0), brazo_i=(-35, 0, -6), cabeza=CABEZA)
    S += [
        dict(r="tallo", caja=(-0.5, 7.5, -1.5, 2.5, 11.5, 1.5), piv=(0, 24, 0), rot=CABEZA),
        dict(r="sombrero", caja=(-5, 11, -6, 7, 14, 6), piv=(0, 24, 0), rot=CABEZA),
    ]
    p, r = (-5, 24, 0), (0, 0, 20)
    S += [
        dict(r="tallo_chico", caja=(-1, 0, -1, 1, 3, 1), piv=p, rot=r),
        dict(r="sombrero_chico", caja=(-3, 0, -3, 3, 2, 3), piv=en(p, r, (0, 3, 0)), rot=r),
    ]
    p, r = (1.5, 18, -2), (-55, 0, 0)
    S += [
        dict(r="tallo_chico", caja=(-1, 0, -1, 1, 3, 1), piv=p, rot=r),
        dict(r="sombrero_mini", caja=(-2, 0, -2, 2, 2, 2), piv=en(p, r, (0, 3, 0)), rot=r),
    ]
    for x, y in ((-1.5, 14.5), (1.5, 20.5), (-1, 22.5)):
        S.append(dict(r="repisa", caja=(-2, 0, -3, 2, 1, 0), piv=(x, y, -2)))
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.55)
    t.mancha(cab["top"], 3, 4, 4.5, MICELIO, 0.7)
    t.mancha(cab["der"], 5, 1, 4, MICELIO, 0.65)
    t.dibujo(
        cab["front"],
        [
            "M M m M m . . .",
            "m M m m n . . .",
            "m M M n . s s .",
            "n m m s . K R s",
            ". n s . . s s .",
            ". . s K K K s .",
            ". . K W c W K .",
            ". . . K K K . .",
        ],
        {
            "M": (MICELIO, 4),
            "m": (MICELIO, 3),
            "n": (MICELIO, 1),
            "s": -1,
            "K": (NEGRO, 0),
            "R": (OJO, 2),
            "W": (HUESO, 3),
            "c": (CARNE, 2),
        },
    )
    t.sombra(cab["front"], 1, "bottom", 1)

    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CAFE, base=0.55)
    t.mancha(cue["front"], 6.5, 1.5, 3.2, MICELIO, 0.65)
    t.mancha(cue["front"], 2.5, 7.5, 2.6, PIEL, 0.5)
    t.dibujo(
        cue["front"],
        [". . . . . . . ."] * 6
        + [". . H c . . . .", ". c H c . . r .", ". . H . . r R .", ". . . . . . r ."],
        {"H": (HUESO, 3), "c": (CARNE, 1), "r": (HONGO, 2), "R": (HONGO, 4)},
    )
    t.mancha(cue["back"], 4, 6, 4, MICELIO, 0.6)
    t.sombra(cue["front"], 2, "top", 2)

    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.55)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = br[cara]
            for i in range(rw):  # manga rota
                for j in range(3 + (i + rw) % 2):
                    t.set(x0 + i, y0 + j, CAFE, 2 + (j == 0) - (j > 2))
            t.sombra(br[cara], 1, "bottom", 1)
            if n == "brazo_i":
                t.mancha(br[cara], 2, 7, 3.4, MICELIO, 0.6)

    pi = t.pintar(uv("pierna"), pz["pierna"], DENIM, base=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.sombra(pi[cara], 2, "top", 2)
        x0, y0, rw, rh = pi[cara]
        for i in range(rw):
            t.set(x0 + i, y0 + rh - 1, PIEL, 1)

    for n in ("tallo", "tallo_chico"):
        t.pintar(uv(n), pz[n], MICELIO, base=0.6, grad=0.4)
    for n in ("sombrero", "sombrero_chico", "sombrero_mini"):
        so = t.pintar(uv(n), pz[n], HONGO, base=0.6, grad=0.5)
        x0, y0, rw, rh = so["top"]
        for cx, cy, r in (
            (rw * 0.3, rh * 0.3, 1.5),
            (rw * 0.7, rh * 0.45, 1.3),
            (rw * 0.4, rh * 0.75, 1.2),
            (rw * 0.8, rh * 0.8, 0.9),
        ):
            if r < rw / 3:
                t.mancha(so["top"], cx, cy, r + 0.4, MICELIO, 0.85, 0)
        for i in range(rw):
            t.ajustar(x0 + i, y0, -1)
            t.ajustar(x0 + i, y0 + rh - 1, -1)
        for j in range(rh):
            t.ajustar(x0, y0 + j, -1)
            t.ajustar(x0 + rw - 1, y0 + j, -1)
        x0, y0, rw, rh = so["bottom"]  # laminillas
        for j in range(rh):
            for i in range(rw):
                t.set(x0 + i, y0 + j, MICELIO, 1 + ((i + j) % 2))
    rep = t.pintar(uv("repisa"), pz["repisa"], CAFE, base=0.7)
    x0, y0, rw, rh = rep["top"]
    for i in range(rw):
        t.set(x0 + i, y0 + rh - 1, MICELIO, 3)
