"""
Mide cuanto lanza hacia arriba el Creeper Saltarin: pone una vaca a su lado,
enciende la mecha y anota la altura de la vaca cada cuarto de segundo.

Uso: python scripts/pruebas/prueba_saltarin.py
"""
import re
import shutil
import subprocess
import threading
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR = RAIZ / "test-fabric"
JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"

shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
for viejo in (SERVIDOR / "mods").glob("dolfincreepers-*.jar"):
    viejo.unlink()
jar = max((RAIZ / "mods-src/creepers/build/libs").glob("dolfincreepers-*[0-9].jar"), key=lambda j: j.stat().st_mtime)
shutil.copy2(jar, SERVIDOR / "mods" / jar.name)

proceso = subprocess.Popen([JAVA, "-Xms2G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
                           cwd=SERVIDOR, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, encoding="utf-8", errors="replace", creationflags=subprocess.CREATE_NO_WINDOW)
lineas = []
listo = threading.Event()


def leer():
    for linea in proceso.stdout:
        lineas.append(linea)
        if "Done (" in linea:
            listo.set()


def consola(comando, espera=0.25):
    proceso.stdin.write(comando + "\n")
    proceso.stdin.flush()
    time.sleep(espera)


threading.Thread(target=leer, daemon=True).start()
listo.wait(600)
consola("forceload add 0 0", 1)
consola("summon minecraft:cow 9 -60 8 {Invulnerable:1b}")  # sin NoAI: con NoAI no aplica la fisica
consola("summon dolfincreepers:saltarin_creeper 8 -60 8 {ignited:1b}")
for _ in range(24):
    consola("data get entity @e[type=minecraft:cow,limit=1] Pos[1]")
consola("data get entity @e[type=minecraft:cow,limit=1] Pos[1]", 3)
consola("stop", 0)
proceso.wait(120)

alturas = [float(m) for m in re.findall(r"entity data: (-?[\d.]+)d", "".join(lineas))]
suelo = -60
print("Alturas de la vaca (bloques sobre el suelo):", [round(a - suelo, 1) for a in alturas])
print("Maxima:", round(max(alturas) - suelo, 1) if alturas else "sin datos")
