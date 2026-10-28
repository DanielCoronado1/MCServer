# ruff: noqa: F403, F405
"""Creeper de fuego calcinado: costra de carbon partida por grietas de magma, craneo abierto que hierve
y dos chimeneas de basalto en la espalda."""

from motor import *

MOB = dict(
    clave="creeper_fuego",
    grupo="Creepers (mod creepers)",
    nombre="Creeper de fuego",
    escala=1.0,
    seed=611,
)

PIEZAS = dict(CREEPER, chimenea=(2, 5, 2), costra=(3, 2, 3))


def partes():
    S = creeper(cabeza=(0, 0, 6))
    for sx, y in ((-1, 15), (1, 12)):
        S.append(
            dict(
                r="chimenea",
                caja=caja_centrada(PIEZAS["chimenea"]),
                piv=(sx * 2, y, -2),
                rot=(-28, 0, sx * -15),
            )
        )
    S.append(dict(r="costra", caja=(-1.5, 0, -1.5, 1.5, 2, 1.5), piv=(-3, 26, 1), rot=(0, 0, 6)))
    return S


GRIETAS_FRENTE = [
    ". . . . . . . .",
    ". m . . . . . .",
    ". M m . . . . .",
    ". . M . . . m .",
    ". . m . . m M .",
    ". . . M m M . .",
    ". . . . M . . .",
    ". . . . m . . .",
    ". . . m M . . .",
    ". . m . . M . .",
    ". m . . . m . .",
    ". . . . . . . .",
]


def pintar(t, uv, pz):
    lava = {"m": (MAGMA, 2), "M": (MAGMA, 4)}
    cab = t.pintar(uv("cabeza"), pz["cabeza"], CARBON, base=0.55)
    t.dibujo(
        cab["front"],
        [
            ". . . . . . . .",
            ". k . . . . k .",
            ". K K . . K K .",
            ". K M . . M K .",
            ". . s K K s . .",
            ". K m K K m K .",
            ". K M M M M K .",
            ". K m K K m K .",
        ],
        dict(CARA_CREEPER, **lava),
    )
    t.mancha(cab["top"], 4, 4, 3.6, MAGMA, 0.75, -2)  # craneo abierto, hirviendo
    t.dibujo(
        cab["der"],
        [". . . . . . . .", ". . . . m . . .", ". . . m M . . .", ". . m . . . . ."],
        lava,
    )
    t.dibujo(
        cab["back"],
        [". . . . . . . .", ". . m M . . . .", ". . . . M m . .", ". . . . . . m ."],
        lava,
    )
    cue = t.pintar(uv("cuerpo"), pz["cuerpo"], CARBON, base=0.5)
    t.dibujo(cue["front"], GRIETAS_FRENTE, lava)
    t.dibujo(cue["back"], [r[::-1] for r in GRIETAS_FRENTE], lava)
    for cara in ("der", "izq"):
        t.mancha(cue[cara], 2, 8, 1.5, MAGMA, 0.6, -1)
    t.sombra(cue["front"], 2, "top", 2)
    pa = t.pintar(uv("pata"), pz["pata"], CARBON, base=0.45, grad=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.dibujo(pa[cara], [". . . .", ". . . .", ". m . .", ". M . .", "m . . .", ". . . ."], lava)
        t.garras(pa[cara])
    ch = t.pintar(uv("chimenea"), pz["chimenea"], CARBON, base=0.6, grad=0.5)
    t.rellenar(ch["top"], MAGMA, 4)
    co = t.pintar(uv("costra"), pz["costra"], CARBON, base=0.6)
    t.rellenar(co["bottom"], MAGMA, 3)
