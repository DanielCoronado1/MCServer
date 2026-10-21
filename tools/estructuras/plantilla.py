"""Constructor de plantillas de estructura (.nbt) y escritor NBT mínimo, solo biblioteca estándar."""

from __future__ import annotations

import gzip
import io
import random
import struct
from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import Any

# DataVersion de Minecraft 26.3 (version.json del servidor: world_version)
DATA_VERSION = 5023

AIRE = "minecraft:air"

# Familias de bloques cuyas conexiones laterales se calculan al cerrar la plantilla
CONECTABLES = ("_fence", "_pane", "iron_bars")

# Bloques que no ofrecen cara completa a la que conectarse
NO_SOLIDOS = (
    "air",
    "stairs",
    "slab",
    "torch",
    "chest",
    "ladder",
    "door",
    "trapdoor",
    "button",
    "plate",
    "carpet",
    "cobweb",
    "lantern",
    "rail",
    "vine",
    "coral",
    "kelp",
    "seagrass",
    "pickle",
    "short_grass",
    "tall_grass",
    "bush",
    "pot",
    "campfire",
    "cauldron",
    "bed",
    "spawner",
    "banner",
    "sign",
)

DIRECCIONES = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


class Byte(int):
    """Entero NBT de 8 bits (tag 1)."""


class Float(float):
    """Decimal NBT de 32 bits (tag 5)."""


class Double(float):
    """Decimal NBT de 64 bits (tag 6)."""


# Orden relevante: bool y Byte antes que int, Float antes que float
TIPOS_NBT = (
    (bool, 1),
    (Byte, 1),
    (int, 3),
    (Float, 5),
    (float, 6),
    (str, 8),
    (list, 9),
    (tuple, 9),
    (dict, 10),
)


def serializar(raiz: dict[str, Any]) -> bytes:
    """Codifica un compound raíz sin nombre y lo comprime con gzip, como los .nbt de estructura."""
    buffer = io.BytesIO()
    buffer.write(bytes([10]))
    _escribir_cadena(buffer, "")
    _escribir_carga(buffer, raiz)
    return gzip.compress(buffer.getvalue(), mtime=0)


def _tipo(valor: Any) -> int:
    for clase, etiqueta in TIPOS_NBT:
        if isinstance(valor, clase):
            return etiqueta
    raise TypeError(f"Tipo NBT no soportado: {type(valor).__name__}")


def _escribir_cadena(buffer: io.BytesIO, texto: str) -> None:
    datos = texto.encode("utf-8")
    buffer.write(struct.pack(">H", len(datos)) + datos)


def _escribir_carga(buffer: io.BytesIO, valor: Any) -> None:
    tipo = _tipo(valor)
    if tipo == 1:
        buffer.write(struct.pack(">b", int(valor)))
    elif tipo == 3:
        buffer.write(struct.pack(">i", valor))
    elif tipo == 5:
        buffer.write(struct.pack(">f", valor))
    elif tipo == 6:
        buffer.write(struct.pack(">d", valor))
    elif tipo == 8:
        _escribir_cadena(buffer, valor)
    elif tipo == 9:
        _escribir_lista(buffer, valor)
    else:
        for nombre, hijo in valor.items():
            buffer.write(bytes([_tipo(hijo)]))
            _escribir_cadena(buffer, nombre)
            _escribir_carga(buffer, hijo)
        buffer.write(bytes([0]))


def _escribir_lista(buffer: io.BytesIO, elementos: Sequence[Any]) -> None:
    if not elementos:
        buffer.write(struct.pack(">Bi", 0, 0))
        return
    tipos = {_tipo(elemento) for elemento in elementos}
    if len(tipos) != 1:
        raise TypeError("Una lista NBT debe contener un único tipo")
    buffer.write(struct.pack(">Bi", tipos.pop(), len(elementos)))
    for elemento in elementos:
        _escribir_carga(buffer, elemento)


def parsear_bloque(especificacion: str) -> tuple[str, dict[str, str]]:
    """Convierte 'oak_stairs[facing=east,half=top]' en (id completo, propiedades)."""
    nombre, _, resto = especificacion.partition("[")
    propiedades = {}
    for par in filter(None, resto.rstrip("]").split(",")):
        clave, _, valor = par.partition("=")
        propiedades[clave.strip()] = valor.strip()
    return (nombre if ":" in nombre else f"minecraft:{nombre}"), propiedades


@dataclass
class Plantilla:
    """Volumen de bloques con entidades y bloques-entidad que se vuelca a un .nbt de estructura.

    Las coordenadas son locales (x, y, z) y deben caer dentro de (ancho, alto, largo). Una posición
    sin bloque no se coloca, de modo que el terreno o el agua existentes se conservan. La capa y=0
    sustituye al suelo; el primer nivel transitable es y=1, igual que en las piezas de Mojang.
    """

    ancho: int
    alto: int
    largo: int
    semilla: int
    bloques: dict[tuple[int, int, int], tuple[str, dict[str, str], dict | None]] = field(
        default_factory=dict
    )
    entidades: list[dict[str, Any]] = field(default_factory=list)

    def __post_init__(self) -> None:
        self.azar = random.Random(self.semilla)

    def poner(self, x: int, y: int, z: int, bloque: str | None, nbt: dict | None = None) -> None:
        """Coloca un bloque (o lo quita si es None) validando que esté dentro del volumen."""
        if not (0 <= x < self.ancho and 0 <= y < self.alto and 0 <= z < self.largo):
            raise ValueError(f"({x}, {y}, {z}) fuera de {self.ancho}x{self.alto}x{self.largo}")
        if bloque is None:
            self.bloques.pop((x, y, z), None)
            return
        nombre, propiedades = parsear_bloque(bloque)
        self.bloques[x, y, z] = (nombre, propiedades, nbt)

    def rellenar(
        self,
        desde: tuple[int, int, int],
        hasta: tuple[int, int, int],
        bloque: str | None,
        probabilidad: float = 1.0,
    ) -> None:
        """Rellena el cubo inclusivo con un bloque, cada celda con la probabilidad dada."""
        for x, y, z in cubo(desde, hasta):
            if probabilidad >= 1.0 or self.azar.random() < probabilidad:
                self.poner(x, y, z, bloque)

    def mezclar(
        self,
        celdas: Sequence[tuple[int, int, int]],
        opciones: Sequence[tuple[str | None, float]],
    ) -> None:
        """Pone en cada celda un bloque elegido por peso; None deja la celda sin bloque."""
        bloques, pesos = zip(*opciones, strict=True)
        for x, y, z in celdas:
            self.poner(x, y, z, self.azar.choices(bloques, pesos)[0])

    def obtener(self, x: int, y: int, z: int) -> str | None:
        """Id del bloque en la celda, o None si está vacía."""
        entrada = self.bloques.get((x, y, z))
        return entrada[0] if entrada else None

    def cofre(self, x: int, y: int, z: int, mirando: str, tabla: str) -> None:
        """Cofre orientado hacia `mirando` con tabla de botín propia."""
        self.poner(
            x,
            y,
            z,
            f"chest[facing={mirando},type=single]",
            {"id": "minecraft:chest", "LootTable": tabla},
        )

    def generador(self, x: int, y: int, z: int, mob: str, cantidad: int = 2) -> None:
        """Generador de mobs ligero: pocos mobs a la vez y solo con un jugador cerca."""
        entidad = {"entity": {"id": mob}}
        self.poner(
            x,
            y,
            z,
            "spawner",
            {
                "id": "minecraft:mob_spawner",
                "Delay": 20,
                "MinSpawnDelay": 400,
                "MaxSpawnDelay": 1200,
                "SpawnCount": cantidad,
                "MaxNearbyEntities": cantidad + 2,
                "RequiredPlayerRange": 14,
                "SpawnRange": 3,
                "SpawnData": entidad,
                "SpawnPotentials": [{"data": entidad, "weight": 1}],
            },
        )

    def entidad(self, id_entidad: str, x: int, y: int, z: int, extra: dict | None = None) -> None:
        """Mob persistente (no se despawnea) en el centro de la celda indicada."""
        posicion = [x + 0.5, float(y), z + 0.5]
        nbt = {
            "id": id_entidad,
            "Pos": [Double(valor) for valor in posicion],
            "Rotation": [Float(self.azar.uniform(0, 360)), Float(0)],
            "PersistenceRequired": Byte(1),
            **(extra or {}),
        }
        self.entidades.append(
            {"nbt": nbt, "blockPos": [x, y, z], "pos": [Double(valor) for valor in posicion]}
        )

    def a_nbt(self) -> dict[str, Any]:
        """Compound raíz con DataVersion, tamaño, paleta, bloques y entidades."""
        self._conectar()
        paleta: dict[tuple, int] = {}
        bloques = []
        for (x, y, z), (nombre, propiedades, nbt) in sorted(
            self.bloques.items(), key=lambda par: (par[0][1], par[0][0], par[0][2])
        ):
            clave = (nombre, tuple(sorted(propiedades.items())))
            estado = paleta.setdefault(clave, len(paleta))
            bloque = {"pos": [x, y, z], "state": estado}
            if nbt:
                bloque["nbt"] = nbt
            bloques.append(bloque)
        return {
            "DataVersion": DATA_VERSION,
            "size": [self.ancho, self.alto, self.largo],
            "palette": [_entrada_paleta(nombre, props) for nombre, props in paleta],
            "blocks": bloques,
            "entities": self.entidades,
        }

    def capa(self, y: int) -> str:
        """Dibujo ASCII de una capa horizontal, útil para revisar el diseño sin abrir el juego."""
        simbolos: dict[str, str] = {}
        filas = []
        for z in range(self.largo):
            fila = ""
            for x in range(self.ancho):
                nombre = self.obtener(x, y, z)
                if nombre is None:
                    fila += "."
                else:
                    fila += simbolos.setdefault(nombre, chr(ord("A") + len(simbolos) % 26))
            filas.append(fila)
        leyenda = ", ".join(f"{s}={n.removeprefix('minecraft:')}" for n, s in simbolos.items())
        return "\n".join(filas) + f"\n{leyenda}"

    def _conectar(self) -> None:
        # Las piezas de jigsaw no recalculan formas, así que fijamos las conexiones aquí
        for (x, y, z), (nombre, propiedades, _) in list(self.bloques.items()):
            if not nombre.endswith(CONECTABLES):
                continue
            for direccion, (dx, dz) in DIRECCIONES.items():
                vecino = self.obtener(x + dx, y, z + dz)
                conecta = vecino is not None and (vecino == nombre or es_solido(vecino))
                propiedades.setdefault(direccion, "true" if conecta else "false")


def _entrada_paleta(nombre: str, propiedades: tuple) -> dict[str, Any]:
    entrada: dict[str, Any] = {"id": nombre}
    if propiedades:
        entrada["properties"] = dict(propiedades)
    return entrada


def es_solido(nombre: str) -> bool:
    """Aproxima si un bloque ofrece una cara completa (para conectar vallas y cristales)."""
    return not any(parte in nombre for parte in NO_SOLIDOS)


def cubo(desde: tuple[int, int, int], hasta: tuple[int, int, int]):
    """Todas las celdas del cubo inclusivo entre dos esquinas, en cualquier orden."""
    xs, ys, zs = (range(min(a, b), max(a, b) + 1) for a, b in zip(desde, hasta, strict=True))
    return [(x, y, z) for x in xs for y in ys for z in zs]


def escribir(plantilla: Plantilla, ruta) -> None:
    """Guarda la plantilla como .nbt comprimido, creando las carpetas necesarias."""
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_bytes(serializar(plantilla.a_nbt()))
