"""Prueba visual del mod de creepers: arranca test-server y cliente de pruebas.

Coloca a todas las variantes quietas y hace capturas.
Uso: python tests/manual/prueba_visual_mod.py
Capturas en test-client/capturas/mod-<fecha>/  (necesita que nadie use el equipo)
"""

import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

from tests.manual import banco
from tests.manual.servidor import ServidorFabric, buscar_java

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR = RAIZ / "runtime/test-server"
CLIENTE = banco.CLIENTE
PUERTO = 25570

# (variante, x, z) en fila delante del jugador; los grandes, mas atras.
FILA = [
    ("fuego", -9, 10),
    ("hielo", -6, 10),
    ("saltarin", -3, 10),
    ("mini", 0, 10),
    ("electrico", 3, 10),
    ("multiplicador", 6, 10),
    ("toxico", 9, 10),
    ("gigante", -6, 20),
    ("rey", 6, 24),
]


def preparar() -> None:
    jar = max(
        (RAIZ / "mods/creepers/build/libs").glob("dolfincreepers-*[0-9].jar"),
        key=lambda j: j.stat().st_mtime,
    )
    # Servidor: mundo nuevo y ultima version del mod.
    shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
    for viejo in (SERVIDOR / "mods").glob("dolfincreepers-*.jar"):
        viejo.unlink()
    shutil.copy2(jar, SERVIDOR / "mods" / jar.name)
    # Cliente: solo Fabric API y nuestro mod.
    mods = CLIENTE / "mods"
    shutil.rmtree(mods, ignore_errors=True)
    mods.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, mods / jar.name)
    for api in (RAIZ / "runtime/server/mods").glob("fabric-api-*.jar"):
        shutil.copy2(api, mods / api.name)


def lanzar_cliente() -> None:
    banco.cerrar_cliente()
    banco.preparar_cliente(PUERTO)
    javaw = buscar_java().replace("java.exe", "javaw.exe").replace("/bin/java", "/bin/javaw")
    with open(CLIENTE / "lanzador.log", "w", encoding="utf-8") as log:
        subprocess.Popen(
            [
                sys.executable,
                "-m",
                "portablemc",
                "--main-dir",
                str(CLIENTE),
                "start",
                "fabric:26.3",
                "--jvm",
                javaw,
                "-u",
                "Tester",
                "--resolution",
                "1280x720",
                "-s",
                "127.0.0.1",
                "-p",
                str(PUERTO),
            ],
            stdout=log,
            stderr=subprocess.STDOUT,
            creationflags=banco.SIN_VENTANA,
        )


def main() -> None:
    carpeta = CLIENTE / "capturas" / ("mod-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    carpeta.mkdir(parents=True, exist_ok=True)
    preparar()

    servidor = ServidorFabric(
        SERVIDOR,
        memoria="2G",
        log="prueba_visual.log",
        timeout_arranque=600.0,
    )
    servidor.iniciar()
    print("Servidor listo. Lanzando el cliente con Fabric...")
    lanzar_cliente()

    try:
        servidor.esperar("Tester joined the game", timeout=600.0)
    except AssertionError:
        servidor.detener()
        raise SystemExit("El cliente no se conectó (mira test-client/lanzador.log)") from None

    time.sleep(12)

    servidor.comando("op Tester")
    servidor.comando("forceload add -32 -32 32 32", 1)
    for regla in ("advance_time false", "advance_weather false", "mob_griefing false"):
        servidor.comando(f"gamerule {regla}")
    servidor.comando("time set 6000")
    servidor.comando("weather clear")
    servidor.comando("difficulty normal")
    servidor.comando("gamemode creative Tester")
    # Suelo de piedra pulida para que se vean bien.
    servidor.comando("fill -16 -61 -6 16 -61 30 minecraft:smooth_stone", 1)
    for variante, x, z in FILA:
        # NoAI: se quedan quietos mirando al jugador (al norte).
        servidor.comando(
            f"summon dolfincreepers:{variante}_creeper {x} -60 {z} {{NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b}}"
        )
    time.sleep(2)

    vistas = [("01_todos", "0 -57 -4 0 12")]
    for i, (variante, x, z) in enumerate(FILA, start=2):
        distancia = {"gigante": 7, "rey": 10}.get(variante, 3)
        altura = {"gigante": -58, "rey": -57}.get(variante, -59)
        vistas.append((f"{i:02d}_{variante}", f"{x} {altura} {z - distancia} 0 10"))
    vistas.append(("11_rey_espalda", "6 -57 34 180 10"))
    vistas.append(("12_todos_espalda", "0 -57 38 180 15"))

    for nombre, posicion in vistas:
        servidor.comando(f"tp Tester {posicion}", 1.5)
        banco.foto(carpeta / f"{nombre}.jpg")
        print("  ", nombre)

    servidor.detener()
    banco.cerrar_cliente()
    print(carpeta)


if __name__ == "__main__":
    main()
