"""
Prueba sin ventanas del mod de creepers en test-fabric/ (puerto 25570).
Mundo nuevo en cada ejecucion. Para cada variante, de una en una y en el mismo sitio:
la invoca, enciende su mecha y comprueba que exploto (o que sobrevivio, el Rey) y su efecto.

Uso: python tests/manual/prueba_mod_servidor.py
"""
import shutil
import os
import subprocess
import threading
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR = RAIZ / "runtime/test-server"
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if "JAVA_HOME" in os.environ else "java"
VARIANTES = ["fuego", "hielo", "saltarin", "mini", "electrico", "multiplicador", "gigante", "toxico", "rey"]

# Mundo limpio y la ultima version del mod.
shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
for viejo in (SERVIDOR / "mods").glob("dolfincreepers-*.jar"):
    viejo.unlink()
jar = max((RAIZ / "mods/creepers/build/libs").glob("dolfincreepers-*[0-9].jar"), key=lambda j: j.stat().st_mtime)
shutil.copy2(jar, SERVIDOR / "mods" / jar.name)

proceso = subprocess.Popen([JAVA, "-Xms2G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
                           cwd=SERVIDOR, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, encoding="utf-8", errors="replace", creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
lineas = []
listo = threading.Event()


def leer():
    for linea in proceso.stdout:
        lineas.append(linea)
        if "Done (" in linea:
            listo.set()


def consola(comando, espera=0.4):
    proceso.stdin.write(comando + "\n")
    proceso.stdin.flush()
    time.sleep(espera)


threading.Thread(target=leer, daemon=True).start()
if not listo.wait(600):
    proceso.kill()
    raise SystemExit("El servidor no arranco:\n" + "".join(lineas[-40:]))

consola("forceload add 0 0", 1)
consola("gamerule mob_griefing false")
for variante in VARIANTES:
    tipo = f"dolfincreepers:{variante}_creeper"
    consola("kill @e[type=!player]", 0.5)
    consola(f"say ===== {variante}")
    consola(f"summon {tipo} 8 -60 8 {{ignited:1b}}", 4)   # 4 s: le da tiempo a explotar
    consola(f"say {variante} sigue vivo?")
    consola(f"execute if entity @e[type={tipo}]")
    if variante == "multiplicador":
        consola("say minis que solto:")
        consola("execute if entity @e[type=dolfincreepers:mini_creeper]")
    if variante == "toxico":
        consola("say nube toxica:")
        consola("execute if entity @e[type=minecraft:area_effect_cloud]")
    if variante == "rey":
        consola("say vida del rey:")
        consola(f"data get entity @e[type={tipo},limit=1] Health")
consola("stop", 0)
proceso.wait(120)

texto = "".join(lineas)
(SERVIDOR / "prueba.log").write_text(texto, encoding="utf-8")
inicio = texto.find("=====")
print("\n".join(l.split("]: ", 1)[-1].replace("[Not Secure] [Server] ", "")
                for l in texto[inicio:].splitlines() if "System chat" in l or "[Server]" in l))
errores = [l for l in lineas if ("ERROR" in l or "Exception" in l) and "No key layers" not in l and "spark" not in l]
print("Errores:", len(errores), *errores[:5], sep="\n")
