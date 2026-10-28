# ruff: noqa: F403, F405
"""Granjero: medio espantapajaros. Sombrero de paja roto, un saco de arpillera cosido sobre media cara,
paja saliendo del cuello y las mangas, y la horqueta que lo mato atravesandole el pecho. Arrasa cultivos."""

from _zombies import *
from motor import *

MOB = dict(
    clave="granjero_zombie", grupo="Zombies (mod zombies)", nombre="Granjero", escala=1.0, seed=909
)

PIEZAS = dict(
    HUMANOIDE,
    sombrero=(10, 2, 10),
    copa=(6, 3, 6),
    paja=(1, 4, 1),
    mango=(1, 18, 1),
    dientes=(5, 1, 1),
)

CABEZA = (0, 0, -12)


def partes():
    S = humanoide(brazo_d=(-90, 0, 0), brazo_i=(-80, 0, 0), cabeza=CABEZA)
    S += [
        dict(r="sombrero", caja=(-5, 7, -5, 5, 9, 5), piv=(0, 24, 0), rot=CABEZA),
        dict(r="copa", caja=(-3, 9, -3, 3, 12, 3), piv=(0, 24, 0), rot=CABEZA),
    ]
    for x, z, rz in (
        (-3, 2, 30),
        (-1, -2.5, 15),
        (2.5, 2, -25),
        (3, -1.5, -40),
    ):  # paja saliendo del cuello
        S.append(
            dict(r="paja", caja=(-0.5, 0, -0.5, 0.5, 4, 0.5), piv=(x, 23.5, z), rot=(z * 6, 0, rz))
        )
    for sx in (-1, 1):  # y de las mangas
        p = en((sx * 6, 22, 0), (-90 + (sx > 0) * 10, 0, 0), (0, -9.5, 0))
        S.append(dict(r="paja", caja=(-0.5, -4, -0.5, 0.5, 0, 0.5), piv=p, rot=(-150, 0, sx * 25)))
    # la horqueta entra por el pecho y sale por la espalda
    r_h = (-80, 0, 12)
    S.append(dict(r="mango", caja=(-0.5, -9, -0.5, 0.5, 9, 0.5), piv=(-1, 17, 0), rot=r_h))
    S.append(
        dict(
            r="dientes",
            caja=(-2.5, -0.5, -0.5, 2.5, 0.5, 0.5),
            piv=en((-1, 17, 0), r_h, (0, -9.5, 0)),
            rot=r_h,
        )
    )
    for k in (-2, 0, 2):
        S.append(
            dict(
                r="paja",
                caja=(-0.5, -4, -0.5, 0.5, 0, 0.5),
                piv=en(en((-1, 17, 0), r_h, (0, -9.5, 0)), r_h, (k, 0, 0)),
                rot=r_h,
            )
        )
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.45)
    t.dibujo(
        cab["front"],
        [
            ". . . . a a a a",
            ". . . . a a a a",
            ". k . . x a a a",
            "K K K . a K K a",
            "K R K . x K K a",
            ". s . . a a a a",
            ". K W K x a a a",
            ". . K K a x a x",
        ],
        dict(CARA, a=(PAJA, 2), x=(NEGRO, 1)),
    )  # media cara tapada con arpillera cosida, ojos de boton arrancados
    t.mancha(cab["izq"], 4, 4, 4, PAJA, 0.45)
    t.pintar(uv("sombrero"), pz["sombrero"], PAJA, base=0.6)
    so = regiones(*uv("sombrero"), *pz["sombrero"])
    t.dibujo((so["top"][0] + 6, so["top"][1] + 6), ["X X", "X X"], {"X": "X"})  # agujero en el ala
    co = t.pintar(uv("copa"), pz["copa"], PAJA, base=0.6)
    for cara in LADOS:
        x0, y0, rw, rh = co[cara]
        for i in range(rw):
            t.set(x0 + i, y0 + rh - 1, TELA_ROJA, 2)
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CUADROS, base=0.5)
    for cara in LADOS:
        x0, y0, rw, rh = cue[cara]
        for j in range(rh):
            for i in range(rw):
                if i % 3 == 1 or j % 3 == 1:
                    t.ajustar(x0 + i, y0 + j, -1)
    t.dibujo(
        cue["front"],
        [
            ". o . . . . o .",
            ". o . . . . o .",
            ". o . . . . o .",
            ". o o o o o o .",
            ". o o o o o o .",
        ]
        + ["o o o o o o o o"] * 7,
        {"o": (DENIM, 2)},
    )
    t.mancha(cue["front"], 3, 5.5, 2, CARNE, 0.35)  # donde entra la horqueta
    t.mancha(cue["back"], 3.5, 5.5, 1.8, CARNE, 0.35)
    t.sombra(cue["front"], 2, "top", 2)
    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.45)
        manga(t, br, CUADROS, 10, 2)
        manos(t, br)
    pi = t.pintar(uv("pierna"), pz["pierna"], DENIM, base=0.45)
    for cara in LADOS:
        t.sombra(pi[cara], 2, "top", 2)
        t.mancha(pi[cara], 2, 6, 1.4, CAFE, 0.4, 0)  # barro
    botas(t, pi, CUERO)
    t.pintar(uv("paja"), pz["paja"], PAJA, base=0.65, grad=0.4)
    t.pintar(uv("mango"), pz["mango"], MADERA, base=0.55, grad=0.1)
    t.pintar(uv("dientes"), pz["dientes"], HIERRO, base=0.45)
