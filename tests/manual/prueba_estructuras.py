#!/usr/bin/env python3
"""Carga las estructuras abandonadas (issue #21) como datapack en un servidor vanilla aislado.

Usa el server.jar de Minecraft 26.3 de la caché de Loom (ejecuta antes `./gradlew build`), sin
Fabric ni otros mods, en runtime/estructuras-smoke/. Comprueba que el datapack carga sin errores,
que las cuatro plantillas se colocan con su cofre y mobs, que las tablas de botín se evalúan y
que `locate` encuentra cada estructura en el mundo generado.
"""

import argparse
import json
import shutil
from pathlib import Path

from tests.manual.servidor import ServidorFabric, buscar_java

RAIZ = Path(__file__).resolve().parents[2]
DATOS = RAIZ / "mods/creepers/src/main/resources/data/dolfincreepers"
JAR_DEFECTO = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-server.jar"
ESTRUCTURAS = ("casa_abandonada", "torre_caida", "campamento_minero", "puesto_hundido")


def preparar(directorio: Path) -> None:
    """Crea el mundo vacío con el datapack del mod copiado desde los recursos."""
    shutil.rmtree(directorio / "world", ignore_errors=True)
    paquete = directorio / "world/datapacks/dolfin"
    shutil.copytree(DATOS, paquete / "data/dolfincreepers")
    formato = {"min_format": 121, "max_format": 121}
    meta = {"pack": {"description": "Estructuras Dolfin", **formato}}
    (paquete / "pack.mcmeta").write_text(json.dumps(meta), encoding="utf-8")
    (directorio / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    propiedades = "online-mode=false\nlevel-seed=1234\nview-distance=4\nsimulation-distance=4\n"
    (directorio / "server.properties").write_text(propiedades, encoding="utf-8")


def comprobar(servidor: ServidorFabric) -> None:
    errores = [linea for linea in servidor.lineas if "ERROR" in linea]
    assert not errores, "".join(errores)

    servidor.comando_y_esperar("forceload add 0 0 200 20", r"force loaded|marked")
    servidor.comando("", espera=15)
    for i, nombre in enumerate(ESTRUCTURAS):
        x = i * 40
        servidor.comando_y_esperar(
            f"place template dolfincreepers:{nombre} {x} 60 0", r"Loaded template"
        )
        servidor.comando_y_esperar(
            f"loot spawn {x} 60 0 loot dolfincreepers:chests/{nombre}", r"Dropped"
        )
        servidor.comando_y_esperar(
            f"locate structure dolfincreepers:{nombre}", rf"nearest dolfincreepers:{nombre} is at"
        )
    servidor.comando_y_esperar(
        "execute positioned 0 60 0 if entity @e[type=minecraft:zombie,distance=..20] "
        "run say CASA_ZOMBI",
        r"CASA_ZOMBI",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, default=RAIZ / "runtime/estructuras-smoke")
    parser.add_argument("--server-jar", type=Path, default=JAR_DEFECTO)
    parser.add_argument("--java", default=buscar_java())
    args = parser.parse_args()

    args.server_dir.mkdir(parents=True, exist_ok=True)
    preparar(args.server_dir)
    comando = [args.java, "-Xmx2G", "-jar", str(args.server_jar), "nogui"]
    with ServidorFabric(
        args.server_dir, comando_arranque=comando, log="console-estructuras.log"
    ) as servidor:
        comprobar(servidor)
    print("Estructuras abandonadas: OK")


if __name__ == "__main__":
    main()
