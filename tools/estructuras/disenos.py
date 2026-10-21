"""Diseños procedurales de las cuatro estructuras abandonadas (plantillas deterministas)."""

from __future__ import annotations

import math
from collections.abc import Callable

from plantilla import Plantilla, cubo

NAMESPACE = "dolfincreepers"

# Mezclas de bloques desgastados: (bloque, peso); None deja un hueco
LADRILLOS = (
    ("stone_bricks", 45),
    ("mossy_stone_bricks", 22),
    ("cracked_stone_bricks", 20),
    ("cobblestone", 6),
    ("mossy_cobblestone", 4),
    (None, 3),
)
TABLONES = (("oak_planks", 70), ("spruce_planks", 10), ("stripped_oak_log[axis=x]", 8), (None, 12))
PIEDRA_BASE = (("cobblestone", 50), ("mossy_cobblestone", 35), ("coarse_dirt", 10), (None, 5))


def tabla_botin(estructura: str) -> str:
    """Id de la tabla de botín del cofre de una estructura."""
    return f"{NAMESPACE}:chests/{estructura}"


def casa_abandonada() -> Plantilla:
    """Casa de madera con tejado a dos aguas, medio derrumbada y cubierta de musgo (11x10x9)."""
    p = Plantilla(11, 10, 9, semilla=2101)
    _casa_estructura(p)
    _casa_tejado(p)
    _casa_interior(p)
    _casa_exterior(p)
    return p


def _casa_estructura(p: Plantilla) -> None:
    # Suelo de entrada y cimiento de piedra bajo las paredes
    p.mezclar(cubo((1, 0, 1), (9, 0, 7)), PIEDRA_BASE)
    p.mezclar(
        cubo((2, 0, 2), (8, 0, 6)),
        (("oak_planks", 60), ("moss_block", 15), ("coarse_dirt", 10), ("podzol", 5), (None, 10)),
    )
    # Volumen interior despejado, de suelo a cumbrera
    p.rellenar((2, 1, 2), (8, 4, 6), "air")
    # Paredes: zócalo de piedra, tablones y postes de esquina
    perimetro = [c for c in cubo((1, 2, 1), (9, 4, 7)) if _en_borde(c, 1, 9, 1, 7)]
    p.mezclar(perimetro, TABLONES)
    zocalo = [c for c in cubo((1, 1, 1), (9, 1, 7)) if _en_borde(c, 1, 9, 1, 7)]
    p.mezclar(zocalo, (("cobblestone", 50), ("mossy_cobblestone", 40), ("oak_planks", 10)))
    for x, z in ((1, 1), (9, 1), (1, 7), (9, 7)):
        p.rellenar((x, 1, z), (x, 4, z), "oak_log[axis=y]")
    p.rellenar((1, 4, 1), (9, 4, 1), "oak_planks", 0.8)
    # Ventanas con cristales rotos y puerta a medio caer
    for x, z in ((3, 1), (7, 1), (3, 7), (7, 7), (1, 4), (9, 4)):
        for y in (2, 3):
            p.poner(x, y, z, "glass_pane" if p.azar.random() < 0.45 else None)
    p.poner(5, 1, 7, "oak_door[facing=south,half=lower,hinge=left,open=true]")
    p.poner(5, 2, 7, "oak_door[facing=south,half=upper,hinge=left,open=true]")
    p.poner(5, 3, 7, "oak_planks")


def _casa_tejado(p: Plantilla) -> None:
    # Tejado a dos aguas con cumbrera en x y hastiales de tablón
    material = (("spruce_stairs", 80), ("mossy_cobblestone_stairs", 8), (None, 12))
    for z in range(9):
        altura = 4 + min(z, 8 - z)
        for x in range(11):
            if z == 4:
                p.poner(x, altura, z, "spruce_planks")
                continue
            sentido = "south" if z < 4 else "north"
            bloque = p.azar.choices(*zip(*material, strict=True))[0]
            p.poner(x, altura, z, bloque and f"{bloque}[facing={sentido},half=bottom]")
        if 1 < z < 7:
            for x in (1, 9):
                p.mezclar(cubo((x, 5, z), (x, altura - 1, z)), TABLONES)
            p.rellenar((2, 5, z), (8, altura - 1, z), "air")
    # Esquina derrumbada: agujero circular en el tejado y los hastiales
    for x, y, z in cubo((0, 4, 0), (10, 8, 8)):
        if p.obtener(x, y, z) and math.hypot(x - 8, z - 2) < 2.4:
            p.poner(x, y, z, None if y > 4 or p.azar.random() < 0.5 else "cobweb")


def _casa_interior(p: Plantilla) -> None:
    # Cofre de botín, mesa, cama y cocina ruinosas
    p.cofre(2, 1, 2, "south", tabla_botin("casa_abandonada"))
    p.poner(2, 1, 3, "crafting_table")
    p.poner(2, 1, 4, "furnace[facing=east,lit=false]")
    p.poner(2, 1, 6, "cauldron")
    p.poner(5, 1, 4, "oak_fence")
    p.poner(5, 2, 4, "oak_pressure_plate")
    p.poner(4, 1, 4, "oak_stairs[facing=west,half=bottom]")
    p.poner(6, 1, 4, "oak_stairs[facing=east,half=bottom]" if p.azar.random() < 0.6 else None)
    p.poner(8, 1, 6, "red_bed[facing=north,part=foot]")
    p.poner(8, 1, 5, "red_bed[facing=north,part=head]")
    for x in (5, 6, 7):
        for y in (1, 2):
            p.poner(x, y, 2, "bookshelf" if p.azar.random() < 0.5 else None)
    p.poner(3, 1, 6, "potted_dead_bush")
    # Telarañas, musgo y hierba que se cuelan por el suelo
    for x, y, z in ((2, 4, 2), (8, 4, 6), (2, 4, 6), (3, 3, 2)):
        p.poner(x, y, z, "cobweb")
    for x, _, z in cubo((2, 1, 2), (8, 1, 6)):
        if p.obtener(x, 1, z) is None and p.azar.random() < 0.2:
            p.poner(x, 1, z, p.azar.choice(("moss_carpet", "moss_carpet", "short_grass")))
    # Dos zombis que se quedaron en la casa, lejos del agujero del tejado
    p.entidad("minecraft:zombie", 3, 1, 5)
    p.entidad("minecraft:zombie", 4, 1, 3)


def _casa_exterior(p: Plantilla) -> None:
    p.poner(5, 0, 8, "dirt_path")
    p.poner(4, 0, 8, "dirt_path" if p.azar.random() < 0.5 else None)
    # Enredaderas trepando por las paredes norte y este
    for x in range(2, 9):
        for y in (2, 3):
            if p.azar.random() < 0.22:
                p.poner(x, y, 0, "vine[south=true]")
    for z in range(2, 7):
        for y in (2, 3):
            if p.azar.random() < 0.2:
                p.poner(10, y, z, "vine[west=true]")


def torre_caida() -> Plantilla:
    """Base de torre de piedra en pie y su mitad superior tumbada hacia el este (21x13x13)."""
    p = Plantilla(21, 13, 13, semilla=2102)
    centro = (4, 6)
    _torre_base(p, centro)
    _torre_tumbada(p)
    _torre_escombros(p)
    return p


def _torre_base(p: Plantilla, centro: tuple[int, int]) -> None:
    cx, cz = centro
    anillo, interior = [], []
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if 2.5 <= d <= 3.7:
                anillo.append((x, z))
            elif d < 2.5:
                interior.append((x, z))
    # Suelo de planta baja y vacío interior (techo bajo solo en la mitad oeste)
    p.mezclar([(x, 0, z) for x, z in anillo + interior], LADRILLOS[:-1])
    for x, z in interior:
        p.rellenar((x, 1, z), (x, 3, z), "air")
        p.poner(x, 4, z, "stone_brick_slab[type=bottom]" if _techo_firme(p, x, cx) else None)
    # Muros: más altos al oeste, rotos hacia el este por donde cayó la parte superior
    for x, z in anillo:
        altura = round(9 - 6 * max(0.0, (x - cx) / 3.7) + p.azar.uniform(-1.2, 1.2))
        p.mezclar([(x, y, z) for y in range(1, max(altura, 2) + 1)], LADRILLOS)
    # Puerta en arco al sur y saeteras al oeste
    p.rellenar((cx, 1, cz + 3), (cx, 2, cz + 3), "air")
    for y in (5, 6):
        p.poner(cx - 3, y, cz, "air")
    # Planta baja: cofre, generador de esqueletos y telarañas
    p.cofre(cx - 1, 1, cz - 2, "south", tabla_botin("torre_caida"))
    p.generador(cx - 1, 1, cz + 1, "minecraft:skeleton")
    for x, y, z in ((cx - 2, 3, cz), (cx - 1, 3, cz - 2), (cx - 1, 2, cz + 2), (cx, 1, cz + 1)):
        p.poner(x, y, z, "cobweb")


def _techo_firme(p: Plantilla, x: int, cx: int) -> bool:
    return x <= cx and p.azar.random() < 0.8


def _torre_tumbada(p: Plantilla) -> None:
    # Cilindro hueco tumbado a lo largo de x, con el extremo lejano irregular
    cy, cz = 3, 6
    for x in range(8, 20):
        for y in range(7):
            for z in range(3, 10):
                d = math.hypot(y - cy, z - cz)
                if not 2.5 <= d <= 3.7:
                    continue
                fin = 19 - p.azar.randint(0, 3) - (2 if y > cy else 0)
                rotura = 0.25 if y > cy else 0.08
                if x > fin or p.azar.random() < rotura or (x < 10 and p.azar.random() < 0.3):
                    continue
                p.poner(x, y, z, p.azar.choices(*zip(*LADRILLOS[:-1], strict=True))[0])
    # Telarañas y musgo dentro del tubo caído
    for x in range(10, 18):
        if p.azar.random() < 0.4:
            p.poner(x, 1, 6, p.azar.choice(("cobweb", "moss_carpet")))
            p.poner(x, 0, 6, "mossy_cobblestone")


def _torre_escombros(p: Plantilla) -> None:
    # Escombros caídos entre la base y el tubo, y alrededor del derrumbe
    piezas = (
        "cobblestone",
        "mossy_cobblestone",
        "stone_brick_slab[type=bottom]",
        "cracked_stone_bricks",
        "gravel",
    )
    for x in range(6, 12):
        for z in range(1, 12):
            if p.obtener(x, 0, z) is None and p.azar.random() < 0.3:
                p.poner(x, 0, z, p.azar.choice(piezas[:2] + piezas[3:]))
                if p.azar.random() < 0.3:
                    p.poner(x, 1, z, p.azar.choice(piezas))


def campamento_minero() -> Plantilla:
    """Sala excavada en la roca con túneles apuntalados, vías, hoguera y botín (25x8x13)."""
    p = Plantilla(25, 8, 13, semilla=2103)
    _mina_roca(p)
    _mina_soportes(p)
    _mina_campamento(p)
    _mina_menas_y_telas(p)
    return p


def _mina_roca(p: Plantilla) -> None:
    # Cáscara de piedra elipsoidal con la sala vaciada dentro, más dos túneles laterales
    centro = (12, 1, 6)
    for celda in cubo((0, 0, 0), (24, 7, 12)):
        if _elipsoide(celda, centro, (8.6, 5.8, 6.3)) < 1:
            p.poner(*celda, "stone" if p.azar.random() < 0.9 else "cobblestone")
    for celda in cubo((0, 0, 0), (24, 7, 12)):
        if celda[1] > 0 and _elipsoide(celda, centro, (7.6, 4.8, 5.3)) < 1:
            p.poner(*celda, "air")
    suelo = [c for c in cubo((4, 0, 2), (20, 0, 10)) if p.obtener(*c)]
    p.mezclar(suelo, (("stone", 40), ("gravel", 30), ("coarse_dirt", 15), ("cobblestone", 15)))
    for lado in ((0, 3), (21, 24)):
        p.rellenar((lado[0], 0, 4), (lado[1], 4, 8), "stone")
        p.rellenar((lado[0], 1, 5), (lado[1], 3, 7), "air")
        p.rellenar((lado[0], 0, 5), (lado[1], 0, 7), "gravel")
    # Bocas que unen cada túnel con la sala
    for x in (4, 20):
        p.rellenar((x, 1, 5), (x, 3, 7), "air")


def _mina_soportes(p: Plantilla) -> None:
    # Marcos de madera en las bocas de los túneles y dos vigas dentro de la sala
    for x in (3, 21):
        for z in (4, 8):
            p.rellenar((x, 1, z), (x, 3, z), "oak_log[axis=y]")
        p.rellenar((x, 4, 4), (x, 4, 8), "oak_planks", 0.85)
    for x in (9, 15):
        for z in (3, 9):
            p.rellenar((x, 1, z), (x, 3, z), "oak_log[axis=y]", 0.9)
        p.rellenar((x, 4, 3), (x, 4, 9), "oak_log[axis=z]", 0.7)
        p.poner(x, 3, 6, "lantern[hanging=true]" if p.obtener(x, 4, 6) else None)
    # Vía rota de punta a punta con una vagoneta olvidada
    for x in range(1, 24):
        if p.azar.random() < 0.72 and p.obtener(x, 1, 6) in (None, "minecraft:air"):
            p.poner(x, 1, 6, "rail[shape=east_west]")
    p.entidad("minecraft:minecart", 13, 1, 6)


def _mina_campamento(p: Plantilla) -> None:
    # Hoguera apagada con troncos de asiento, mesa de trabajo y suministros
    p.poner(12, 1, 3, "campfire[lit=false,facing=north]")
    p.poner(10, 1, 3, "oak_log[axis=x]")
    p.poner(14, 1, 3, "hay_block[axis=x]")
    p.poner(9, 1, 2, "crafting_table")
    p.poner(16, 1, 9, "barrel[facing=up]")
    p.cofre(18, 1, 8, "west", tabla_botin("campamento_minero"))
    p.poner(8, 1, 10, "oak_trapdoor[facing=north,half=bottom,open=false]")
    # Generador de arañas de cueva rodeado de telarañas
    p.generador(17, 1, 3, "minecraft:cave_spider", 3)
    for celda in ((16, 1, 3), (17, 2, 3), (17, 1, 4), (18, 1, 4)):
        p.poner(*celda, "cobweb")


def _mina_menas_y_telas(p: Plantilla) -> None:
    # Vetas visibles en la roca de la sala y telarañas en el techo
    menas = ("coal_ore", "coal_ore", "iron_ore", "copper_ore", "gravel")
    for x, y, z in cubo((4, 1, 1), (20, 7, 11)):
        if p.obtener(x, y, z) != "minecraft:stone":
            continue
        vecinos = [(x + dx, y + dy, z + dz) for dx, dy, dz in _VECINOS]
        expuesta = any(p.obtener(*v) == "minecraft:air" for v in vecinos if _dentro(p, v))
        if expuesta and p.azar.random() < 0.09:
            p.poner(x, y, z, p.azar.choice(menas))
        if p.obtener(x, y - 1, z) == "minecraft:air" and p.azar.random() < 0.07:
            p.poner(x, y - 1, z, "cobweb")


_VECINOS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def _dentro(p: Plantilla, celda: tuple[int, int, int]) -> bool:
    x, y, z = celda
    return 0 <= x < p.ancho and 0 <= y < p.alto and 0 <= z < p.largo


def _elipsoide(celda, centro, radios) -> float:
    return sum(((c - o) / r) ** 2 for c, o, r in zip(celda, centro, radios, strict=True))


def puesto_hundido() -> Plantilla:
    """Puesto de vigilancia de madera y piedra hundido sobre una plataforma (13x12x13)."""
    p = Plantilla(13, 12, 13, semilla=2104)
    _puesto_plataforma(p)
    _puesto_caseta(p)
    _puesto_decoracion(p)
    return p


def _puesto_plataforma(p: Plantilla) -> None:
    # Plataforma de piedra que sustituye al fondo marino, con postes de amarre en las esquinas
    mezcla = (
        ("stone_bricks", 40),
        ("mossy_stone_bricks", 30),
        ("cracked_stone_bricks", 15),
        ("dark_oak_planks", 15),
    )
    p.mezclar(cubo((1, 0, 1), (11, 0, 11)), mezcla)
    for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
        p.rellenar((x, 1, z), (x, p.azar.randint(3, 5), z), "dark_oak_log[axis=y]")
    for x in range(2, 11):
        for z in (1, 11):
            p.poner(x, 1, z, "dark_oak_fence" if p.azar.random() < 0.55 else None)
    for z in range(2, 11):
        for x in (1, 11):
            p.poner(x, 1, z, "dark_oak_fence" if p.azar.random() < 0.55 else None)


def _puesto_caseta(p: Plantilla) -> None:
    # Caseta de 7x7 sin aire en las paredes: el interior queda inundado
    paredes = [c for c in cubo((3, 1, 3), (9, 5, 9)) if _en_borde(c, 3, 9, 3, 9)]
    p.mezclar(paredes, (("dark_oak_planks", 60), ("mossy_stone_bricks", 20), (None, 20)))
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9)):
        p.rellenar((x, 1, z), (x, 5, z), "dark_oak_log[axis=y]")
    for x, z in ((6, 3), (6, 9), (3, 6), (9, 6)):
        p.rellenar((x, 3, z), (x, 4, z), "iron_bars", 0.8)
    p.rellenar((6, 1, 9), (6, 2, 9), None)
    # Techo casi desplomado: solo quedan vigas sueltas
    for z in (3, 6, 9):
        p.rellenar((3, 6, z), (9, 6, z), "dark_oak_log[axis=x]", 0.55)
    # Interior: botín, provisiones y dos ahogados
    p.cofre(4, 1, 4, "east", tabla_botin("puesto_hundido"))
    p.poner(8, 1, 4, "barrel[facing=up]")
    p.poner(8, 1, 8, "barrel[facing=up]" if p.azar.random() < 0.5 else None)
    p.poner(5, 1, 8, "dark_oak_stairs[facing=north,half=bottom]")
    p.entidad("minecraft:drowned", 6, 1, 6)
    p.entidad("minecraft:drowned", 5, 1, 7)


def _puesto_decoracion(p: Plantilla) -> None:
    # Corales muertos, pepinos de mar y algas sobre la plataforma y alrededor
    corales = (
        "dead_tube_coral_block",
        "dead_brain_coral_block",
        "dead_horn_coral_fan",
        "dead_bubble_coral_fan",
        "sea_pickle[pickles=3]",
        "sea_pickle[pickles=1]",
    )
    for x, y, z in cubo((1, 1, 1), (11, 1, 11)):
        libre = p.obtener(x, y, z) is None and not (3 <= x <= 9 and 3 <= z <= 9)
        if libre and p.azar.random() < 0.12:
            p.poner(x, y, z, p.azar.choice(corales))
    for x, z in ((0, 3), (12, 8), (4, 12), (8, 0), (0, 10)):
        alto = p.azar.randint(2, 4)
        p.rellenar((x, 1, z), (x, alto - 1, z), "kelp_plant")
        p.poner(x, alto, z, "kelp[age=12]")


def _en_borde(celda: tuple[int, int, int], x0: int, x1: int, z0: int, z1: int) -> bool:
    return celda[0] in (x0, x1) or celda[2] in (z0, z1)


# Registro de plantillas por nombre de archivo
DISENOS: dict[str, Callable[[], Plantilla]] = {
    "casa_abandonada": casa_abandonada,
    "torre_caida": torre_caida,
    "campamento_minero": campamento_minero,
    "puesto_hundido": puesto_hundido,
}
