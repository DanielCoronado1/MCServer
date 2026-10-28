# ruff: noqa: F403, F405
"""Zombie astronauta (epica SPC-0): casco con el visor dorado roto y la cara podrida asomando,
mochila de oxigeno con tubo, traje roto en el pecho y en una pierna."""

from motor import *

MOB = dict(
    clave="zombie_astronauta",
    grupo="Kepler (mod space)",
    nombre="Zombie astronauta",
    escala=1.0,
    seed=303,
)

PIEZAS = dict(
    HUMANOIDE,
    pierna_i=(4, 12, 4),
    casco=(10, 10, 10),
    antena=(1, 4, 1),
    mochila=(6, 8, 3),
    tubo=(1, 6, 1),
)

CABEZA = (6, 0, 0)


def partes():
    S = humanoide(brazo_d=(-80, 0, 0), brazo_i=(-98, 0, 0), cabeza=CABEZA, pierna_i="pierna_i")
    S += [
        dict(r="casco", caja=(-5, -1, -5, 5, 9, 5), piv=(0, 24, 0), rot=CABEZA),
        dict(r="antena", caja=(3, 9, -2, 4, 13, -1), piv=(0, 24, 0), rot=CABEZA),
        dict(r="mochila", caja=(-3, 14, -5, 3, 22, -2)),
        dict(r="tubo", caja=(-0.5, 0, -0.5, 0.5, 6, 0.5), piv=(2, 21.5, -3.5), rot=(30, 0, 0)),
    ]
    return S


def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.5)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". s . . . . s .",
            "s K K . . K K s",
            ". K R . . W K .",
            ". . . s s . . .",
            ". . K K K K . .",
            ". . K W W c . .",
            ". . . . . . . .",
        ],
        {"K": (NEGRO, 0), "R": (OJO, 2), "W": (HUESO, 3), "c": (CARNE, 2), "s": -1},
    )

    cas = t.pintar(uv("casco"), pz["casco"], TRAJE, base=0.65, var=0.15, grad=0.35)
    t.dibujo(
        cas["front"],
        [  # visor roto: X deja ver la cara de adentro
            ". . . . . . . . . .",
            ". . . . . . . . . .",
            ". m m m m m m m m .",
            ". m X X v X X O O m",
            ". m X X X X o O O m",
            ". m v X X X X o O m",
            ". m X X X X X o O m",
            ". m m m m m m m m .",
        ],
        {"m": (HIERRO, 2), "X": "X", "v": (ORO, 3), "o": (ORO, 2), "O": (ORO, 3)},
    )
    for cara in ("izq", "der", "back"):
        x0, y0, rw, rh = cas[cara]
        for i in range(rw):
            t.set(x0 + i, y0 + 6, NARANJA, 3)
            t.set(x0 + i, y0 + 7, NARANJA, 2)
    x0, y0, rw, rh = cas["der"]
    t.dibujo((x0 + 3, y0 + 2), ["n n n", "n b n", "n n n"], {"n": (NARANJA, 2), "b": (DENIM, 3)})
    t.pintar(uv("antena"), pz["antena"], HIERRO, base=0.6)

    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], TRAJE, base=0.62, var=0.15)
    t.dibujo(
        cue["front"],
        [
            ". . . . . . . .",
            ". . . . . . . .",
            ". . p p p p . .",
            ". . p r g y . .",
            ". . p p p p . .",
            ". . . . . . . .",
            ". s s . . . . .",
            "s H H c . . . .",
            "s c c c s . . .",
            ". H H c . . . .",
            "n n n n n n n n",
        ],
        {
            "p": (HIERRO, 1),
            "r": (230, 50, 40),
            "g": (70, 210, 100),
            "y": (240, 200, 60),
            "s": (PIEL, 2),
            "H": (HUESO, 3),
            "c": (CARNE, 2),
            "n": (NARANJA, 2),
        },
    )
    t.sombra(cue["front"], 2, "top", 2)
    moc = t.pintar(uv("mochila"), pz["mochila"], HIERRO, base=0.45)
    t.dibujo(
        moc["back"],
        [
            "t t . . t t",
            "T t . . T t",
            "T t d d T t",
            "T t d r T t",
            "T t d d T t",
            "T t . . T t",
            "T t . . T t",
            "t t . . t t",
        ],
        {"t": (TRAJE, 2), "T": (TRAJE, 3), "d": (NEGRO, 1), "r": (230, 50, 40)},
    )
    t.pintar(uv("tubo"), pz["tubo"], HIERRO, base=0.35, grad=0)

    for n in ("brazo_d", "brazo_i"):
        br = t.pintar(uv(n), pz[n], TRAJE, base=0.62, var=0.15)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = br[cara]
            for i in range(rw):
                t.set(x0 + i, y0 + 3, NARANJA, 3)
                for j in range(rh - 3, rh):  # guantes
                    t.set(x0 + i, y0 + j, HIERRO, 2 - (j == rh - 1) + (j == rh - 3))
    for n in ("pierna", "pierna_i"):
        pi = t.pintar(uv(n), pz[n], TRAJE, base=0.6, var=0.15)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = pi[cara]
            for i in range(rw):
                t.set(x0 + i, y0 + 5, HIERRO, 2)
                t.set(x0 + i, y0 + 6, HIERRO, 1)  # rodillera
                for j in range(rh - 3, rh):  # botas
                    t.set(x0 + i, y0 + j, HIERRO, 1 - (j == rh - 1) + (j == rh - 3))
            t.sombra(pi[cara], 2, "top", 2)
        if n == "pierna_i":  # traje roto
            t.mancha(pi["front"], 2, 2.5, 2.4, PIEL, 0.5)
            t.dibujo(pi["front"], [". . . .", ". . c .", ". c . .", ". c . ."], {"c": (CARNE, 1)})
            t.mancha(pi["izq"], 1.5, 2, 1.8, PIEL, 0.5)
