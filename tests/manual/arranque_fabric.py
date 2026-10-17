"""
Arranca el servidor Fabric, espera a que termine de cargar, ejecuta unos comandos
de comprobacion y lo apaga. Guarda toda la salida en runtime/server/prueba_arranque.log.

Uso: python tests/manual/arranque_fabric.py ["comando" ...]
"""
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

SERVIDOR = Path(__file__).resolve().parents[2] / "runtime" / "server"
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if "JAVA_HOME" in os.environ else "java"

proceso = subprocess.Popen([JAVA, "-Xms4G", "-Xmx4G", "-jar", "fabric-server-launch.jar", "nogui"],
                           cwd=SERVIDOR, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
lineas = []
listo = threading.Event()


def leer():
    for linea in proceso.stdout:
        lineas.append(linea)
        if "Done (" in linea:
            listo.set()


threading.Thread(target=leer, daemon=True).start()
if not listo.wait(600):
    proceso.kill()
    print("No termino de arrancar en 10 minutos")
for comando in sys.argv[1:]:
    proceso.stdin.write(comando + "\n")
    proceso.stdin.flush()
    time.sleep(2)
proceso.stdin.write("stop\n")
proceso.stdin.flush()
proceso.wait(120)
(SERVIDOR / "prueba_arranque.log").write_text("".join(lineas), encoding="utf-8")
print("".join(l for l in lineas if any(p in l for p in ("ERROR", "WARN", "Exception", "Done (", "Loading", "mods"))))
