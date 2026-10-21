"""Coherencia de las estructuras abandonadas (issue #21): referencias, plantillas y generador."""

import gzip
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tests.manual.space_nbt import nbt

RAIZ = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ / "tools/estructuras"))

import generate_estructuras as generador  # noqa: E402
from plantilla import DATA_VERSION, Byte, Plantilla, serializar  # noqa: E402

DATOS = RAIZ / "mods/creepers/src/main/resources/data/dolfincreepers"
NOMBRES = [e.nombre for e in generador.ESTRUCTURAS]


def leer_json(ruta: Path):
    return json.loads(ruta.read_text(encoding="utf-8"))


def leer_plantilla(nombre: str):
    return nbt(gzip.decompress((DATOS / "structure" / f"{nombre}.nbt").read_bytes()))


def sin_namespace(ident: str) -> str:
    return ident.partition(":")[2]


class ReferenciasTest(unittest.TestCase):
    def test_cada_cadena_de_ids_existe(self):
        for nombre in NOMBRES:
            with self.subTest(nombre):
                conjunto = leer_json(DATOS / "worldgen/structure_set" / f"{nombre}.json")
                [entrada] = conjunto["structures"]
                estructura = leer_json(
                    DATOS / "worldgen/structure" / f"{sin_namespace(entrada['structure'])}.json"
                )
                pool = leer_json(
                    DATOS
                    / "worldgen/template_pool"
                    / f"{sin_namespace(estructura['start_pool'])}.json"
                )
                for elemento in pool["elements"]:
                    plantilla = sin_namespace(elemento["element"]["location"])
                    self.assertTrue((DATOS / "structure" / f"{plantilla}.nbt").is_file())
                tag = estructura["biomes"].removeprefix("#")
                self.assertTrue(
                    (DATOS / "tags/worldgen/biome" / f"{sin_namespace(tag)}.json").is_file()
                )

    def test_cofres_apuntan_a_tablas_existentes(self):
        for nombre in NOMBRES:
            with self.subTest(nombre):
                cofres = [
                    b["nbt"]["LootTable"]
                    for b in leer_plantilla(nombre)["blocks"]
                    if b.get("nbt", {}).get("id") == "minecraft:chest"
                ]
                self.assertTrue(cofres, "la plantilla no tiene cofre con botín")
                for tabla in cofres:
                    self.assertTrue(
                        (DATOS / "loot_table" / f"{sin_namespace(tabla)}.json").is_file()
                    )

    def test_tablas_de_botin_son_ligeras(self):
        for nombre in NOMBRES:
            with self.subTest(nombre):
                tabla = leer_json(DATOS / "loot_table/chests" / f"{nombre}.json")
                self.assertEqual("minecraft:chest", tabla["type"])
                maximo = sum(
                    pool["rolls"]["max"] if isinstance(pool["rolls"], dict) else pool["rolls"]
                    for pool in tabla["pools"]
                )
                self.assertLessEqual(maximo, 4)

    def test_semillas_y_sets_distintos(self):
        sales = [e.salt for e in generador.ESTRUCTURAS]
        self.assertEqual(len(sales), len(set(sales)))
        for e in generador.ESTRUCTURAS:
            self.assertGreaterEqual(e.spacing, e.separation + 1)


class PlantillasTest(unittest.TestCase):
    def test_formato_nbt(self):
        for nombre in NOMBRES:
            with self.subTest(nombre):
                datos = leer_plantilla(nombre)
                self.assertEqual(DATA_VERSION, datos["DataVersion"])
                ancho, alto, largo = datos["size"]
                self.assertTrue(all("id" in entrada for entrada in datos["palette"]))
                for bloque in datos["blocks"]:
                    x, y, z = bloque["pos"]
                    self.assertTrue(0 <= x < ancho and 0 <= y < alto and 0 <= z < largo)
                    self.assertLess(bloque["state"], len(datos["palette"]))

    def test_campamento_queda_entre_y0_y_y40(self):
        estructura = leer_json(DATOS / "worldgen/structure/campamento_minero.json")
        altura = estructura["start_height"]
        alto = leer_plantilla("campamento_minero")["size"][1]
        # La pieza se apoya con su capa y=1 en start_height, así que ocupa [start - 1, start + alto - 2]
        self.assertGreaterEqual(altura["min_inclusive"]["absolute"] - 1, 0)
        self.assertLessEqual(altura["max_inclusive"]["absolute"] + alto - 2, 40)
        self.assertEqual("underground_structures", estructura["step"])

    def test_estructuras_con_mobs(self):
        for nombre in NOMBRES:
            with self.subTest(nombre):
                datos = leer_plantilla(nombre)
                generadores = [b for b in datos["blocks"] if b.get("nbt", {}).get("SpawnData")]
                mobs = [e for e in datos["entities"] if e["nbt"]["id"] != "minecraft:minecart"]
                self.assertTrue(generadores or mobs)

    def test_escritor_nbt_y_validacion_de_limites(self):
        p = Plantilla(2, 2, 2, semilla=1)
        p.poner(0, 0, 0, "oak_stairs[facing=east]")
        p.entidad("minecraft:zombie", 1, 1, 1)
        datos = nbt(gzip.decompress(serializar(p.a_nbt())))
        self.assertEqual(
            {"id": "minecraft:oak_stairs", "properties": {"facing": "east"}}, datos["palette"][0]
        )
        self.assertEqual(1, datos["entities"][0]["nbt"]["PersistenceRequired"])
        with self.assertRaises(ValueError):
            p.poner(2, 0, 0, "stone")
        self.assertEqual(1, int(Byte(1)))

    def test_vallas_y_barrotes_conectan_con_vecinos(self):
        p = Plantilla(3, 1, 1, semilla=1)
        p.poner(0, 0, 0, "stone")
        p.poner(1, 0, 0, "oak_fence")
        datos = p.a_nbt()
        valla = next(e for e in datos["palette"] if e["id"] == "minecraft:oak_fence")
        self.assertEqual("true", valla["properties"]["west"])
        self.assertEqual("false", valla["properties"]["east"])


class GeneradorTest(unittest.TestCase):
    def test_los_archivos_versionados_coinciden_con_el_generador(self):
        with tempfile.TemporaryDirectory() as temporal:
            destino = Path(temporal)
            with patch.object(generador, "DATOS", destino):
                generador.main()
            generados = sorted(p.relative_to(destino) for p in destino.rglob("*") if p.is_file())
            self.assertEqual(
                len(NOMBRES) * 6, len(generados), "cada estructura genera seis archivos"
            )
            for relativa in generados:
                with self.subTest(relativa=str(relativa)):
                    self.assertEqual(
                        (destino / relativa).read_bytes(), (DATOS / relativa).read_bytes()
                    )


if __name__ == "__main__":
    unittest.main()
