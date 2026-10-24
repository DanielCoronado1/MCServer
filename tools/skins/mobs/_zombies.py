# ruff: noqa: F403, F405
"""Ayudantes compartidos por los disenos del mod zombies (no es un mob: generar.py salta los archivos con "_")."""

from motor import *

CARA = {
    "K": (NEGRO, 0),
    "k": -2,
    "s": -1,
    "h": 1,
    "R": (OJO, 2),
    "r": (OJO, 1),
    "W": (HUESO, 3),
    "B": (HUESO, 4),
    "c": (CARNE, 1),
    "C": (CARNE, 2),
}

LADOS = ("front", "der", "izq", "back")


def manga(t, reg, rampa, filas, idx=2):
    """Tela en las filas de arriba de cada lado, con borde roto irregular."""
    for cara in LADOS:
        x0, y0, rw, rh = reg[cara]
        for i in range(rw):
            for j in range(min(rh, filas - ((i * 5 + len(cara)) % 3 == 0))):
                t.set(x0 + i, y0 + j, rampa, idx + (j == 0) - (j == filas - 1))


def pantalon(t, reg, rampa, roto_desde=None, idx=2):
    """Tela en todas las filas; si roto_desde, la pierna asoma desde esa fila con borde irregular."""
    for cara in LADOS:
        x0, y0, rw, rh = reg[cara]
        for i in range(rw):
            hasta = rh if roto_desde is None else roto_desde + (i * 7 + len(cara)) % 3
            for j in range(min(rh, hasta)):
                t.set(x0 + i, y0 + j, rampa, idx - (j == hasta - 1))
        t.sombra(reg[cara], 2, "top", 2)


def botas(t, reg, rampa, filas=3):
    for cara in LADOS:
        x0, y0, rw, rh = reg[cara]
        for j in range(rh - filas, rh):
            for i in range(rw):
                t.set(x0 + i, y0 + j, rampa, 2 - (j == rh - 1) + (j == rh - filas))


def manos(t, reg):
    for cara in LADOS:
        t.garras(reg[cara])


def jirones(t, reg, filas=3):
    """Borde inferior hecho jirones (pixeles transparentes) en los lados de una pieza de tela."""
    for cara in LADOS:
        x0, y0, rw, rh = reg[cara]
        for i in range(rw):
            for j in range(rh - (1 + (i * 5 + 3) % filas), rh):
                t.color(x0 + i, y0 + j, (0, 0, 0, 0))


def aro_hueco(t, reg):
    """Para sombreros y cascos que rodean la cabeza: vacia el centro de arriba y abajo."""
    for cara in ("top", "bottom"):
        x0, y0, rw, rh = reg[cara]
        t.dibujo((x0 + 1, y0 + 1), ["X " * (rw - 2)] * (rh - 2), {"X": "X"})
