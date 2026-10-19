"""Mide cuánto lanza hacia arriba el Creeper Saltarín.

Pone una vaca a su lado, enciende la mecha y anota la altura de la vaca cada cuarto de segundo.

Uso: python tests/manual/prueba_saltarin.py
"""

import re
import shutil
from pathlib import Path

from tests.manual.servidor import ServidorFabric

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR = RAIZ / "runtime/test-server"


def preparar_entorno() -> None:
    """Restaura el mundo de pruebas y actualiza el mod de creepers."""
    shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
    for viejo in (SERVIDOR / "mods").glob("dolfincreepers-*.jar"):
        viejo.unlink()
    jars = list((RAIZ / "mods/creepers/build/libs").glob("dolfincreepers-*[0-9].jar"))
    if not jars:
        raise SystemExit(
            "No se encontró el JAR compilado de creepers. Compila primero con gradlew build."
        )
    jar = max(jars, key=lambda j: j.stat().st_mtime)
    (SERVIDOR / "mods").mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, SERVIDOR / "mods" / jar.name)


def main() -> None:
    preparar_entorno()
    with ServidorFabric(
        SERVIDOR,
        memoria="2G",
        timeout_arranque=600.0,
    ) as servidor:
        servidor.comando("forceload add 0 0", espera=1.0)
        # Sin NoAI para permitir que aplique la física del empuje
        servidor.comando("summon minecraft:cow 9 -60 8 {Invulnerable:1b}")
        servidor.comando("summon dolfincreepers:saltarin_creeper 8 -60 8 {ignited:1b}")
        for _ in range(24):
            servidor.comando("data get entity @e[type=minecraft:cow,limit=1] Pos[1]", espera=0.25)
        servidor.comando("data get entity @e[type=minecraft:cow,limit=1] Pos[1]", espera=3.0)

    # Extracción de alturas registradas en la consola.
    alturas = [float(m) for m in re.findall(r"entity data: (-?[\d.]+)d", servidor.salida)]
    suelo = -60
    print("Alturas de la vaca (bloques sobre el suelo):", [round(a - suelo, 1) for a in alturas])
    print("Maxima:", round(max(alturas) - suelo, 1) if alturas else "sin datos")


if __name__ == "__main__":
    main()
