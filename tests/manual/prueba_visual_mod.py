"""
Prueba visual del mod de creepers: arranca test-fabric (puerto 25570) y un Minecraft
Fabric de pruebas con el mod, coloca a todas las variantes quietas y hace capturas.

Uso: python tests/manual/prueba_visual_mod.py
Capturas en test-client/capturas/mod-<fecha>/  (necesita que nadie use el equipo)
"""
import shutil
import os
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

import banco

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR = RAIZ / "runtime/test-server"
CLIENTE = banco.CLIENTE
PUERTO = 25570
JAVA = Path(os.environ["JAVA_HOME"]) / "bin"

# (variante, x, z) en fila delante del jugador; los grandes, mas atras.
FILA = [("fuego", -9, 10), ("hielo", -6, 10), ("saltarin", -3, 10), ("mini", 0, 10), ("electrico", 3, 10),
        ("multiplicador", 6, 10), ("toxico", 9, 10), ("gigante", -6, 20), ("rey", 6, 24)]


def preparar():
    jar = max((RAIZ / "mods/creepers/build/libs").glob("dolfincreepers-*[0-9].jar"), key=lambda j: j.stat().st_mtime)
    # Servidor: mundo nuevo y ultima version del mod.
    shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
    for viejo in (SERVIDOR / "mods").glob("dolfincreepers-*.jar"):
        viejo.unlink()
    shutil.copy2(jar, SERVIDOR / "mods" / jar.name)
    # Cliente: solo Fabric API y nuestro mod.
    mods = CLIENTE / "mods"
    shutil.rmtree(mods, ignore_errors=True)
    mods.mkdir()
    shutil.copy2(jar, mods / jar.name)
    for api in (RAIZ / "runtime/server/mods").glob("fabric-api-*.jar"):
        shutil.copy2(api, mods / api.name)


class Servidor:
    def __init__(self):
        self.lineas = []
        self.proceso = subprocess.Popen([str(JAVA / "java.exe"), "-Xms2G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
                                        cwd=SERVIDOR, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                                        creationflags=subprocess.CREATE_NO_WINDOW)
        threading.Thread(target=self._leer, daemon=True).start()

    def _leer(self):
        for linea in self.proceso.stdout:
            self.lineas.append(linea)

    def esperar(self, texto, segundos):
        limite = time.time() + segundos
        while time.time() < limite:
            if any(texto in l for l in self.lineas):
                return True
            time.sleep(1)
        return False

    def __call__(self, comando, espera=0.3):
        self.proceso.stdin.write(comando + "\n")
        self.proceso.stdin.flush()
        time.sleep(espera)

    def parar(self):
        self("stop", 0)
        self.proceso.wait(120)
        (SERVIDOR / "prueba_visual.log").write_text("".join(self.lineas), encoding="utf-8")


def lanzar_cliente():
    banco.cerrar_cliente()
    banco.preparar_cliente(PUERTO)
    with open(CLIENTE / "lanzador.log", "w", encoding="utf-8") as log:
        subprocess.Popen([sys.executable, "-m", "portablemc", "--main-dir", str(CLIENTE), "start", "fabric:26.3",
                          "--jvm", str(JAVA / "javaw.exe"), "-u", "Tester", "--resolution", "1280x720",
                          "-s", "127.0.0.1", "-p", str(PUERTO)],
                         stdout=log, stderr=subprocess.STDOUT, creationflags=banco.SIN_VENTANA)


if __name__ == "__main__":
    carpeta = CLIENTE / "capturas" / ("mod-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    carpeta.mkdir(parents=True)
    preparar()
    servidor = Servidor()
    if not servidor.esperar("Done (", 600):
        raise SystemExit("El servidor no arranco")
    print("Servidor listo. Lanzando el cliente con Fabric...")
    lanzar_cliente()
    if not servidor.esperar("Tester joined the game", 600):
        servidor.parar()
        raise SystemExit("El cliente no se conecto (mira test-client/lanzador.log)")
    time.sleep(12)

    servidor("op Tester")
    servidor("forceload add -32 -32 32 32", 1)
    for regla in ("advance_time false", "advance_weather false", "mob_griefing false"):
        servidor(f"gamerule {regla}")
    servidor("time set 6000")
    servidor("weather clear")
    servidor("difficulty normal")
    servidor("gamemode creative Tester")
    # Suelo de piedra pulida para que se vean bien.
    servidor("fill -16 -61 -6 16 -61 30 minecraft:smooth_stone", 1)
    for variante, x, z in FILA:
        # NoAI: se quedan quietos mirando al jugador (al norte).
        servidor(f"summon dolfincreepers:{variante}_creeper {x} -60 {z} {{NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b}}")
    time.sleep(2)

    vistas = [("01_todos", "0 -57 -4 0 12")]
    for i, (variante, x, z) in enumerate(FILA, start=2):
        distancia = {"gigante": 7, "rey": 10}.get(variante, 3)
        altura = {"gigante": -58, "rey": -57}.get(variante, -59)
        vistas.append((f"{i:02d}_{variante}", f"{x} {altura} {z - distancia} 0 10"))
    vistas.append(("11_rey_espalda", "6 -57 34 180 10"))
    vistas.append(("12_todos_espalda", "0 -57 38 180 15"))

    for nombre, posicion in vistas:
        servidor(f"tp Tester {posicion}", 1.5)
        banco.foto(carpeta / f"{nombre}.jpg")
        print("  ", nombre)

    servidor.parar()
    banco.cerrar_cliente()
    print(carpeta)
