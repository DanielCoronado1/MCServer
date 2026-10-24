"""Prueba visual de las skins y su capa exterior: arranca test-server y el cliente de pruebas.

Pone cada mob de los mods (creepers, zombies, Rey Zombie y Kepler) quieto en su propio lugar y lo fotografía
de frente y de espalda en 3/4, con el mismo ángulo que las láminas del skill skins-mobs, para compararlos.
Uso (desde la raíz, con JAVA_HOME en un JDK 25): python -m tests.manual.prueba_visual_skins [mob ...]
Capturas en runtime/test-client/capturas/skins-<fecha>/  (necesita que nadie use el equipo)
"""

import math
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
MODS = ("creepers", "zombies", "space")
CREEPERS = ("fuego", "hielo", "saltarin", "mini", "electrico", "multiplicador", "toxico", "gigante", "rey")
ZOMBIES = (
    "minero", "ladron", "bombero", "abismo", "escudero", "hambriento", "griton", "bruja",
    "granjero", "congelado", "jinete", "bilioso", "enano", "contagiado", "mimo", "explorador",
)
KEPLER = ("parasito", "acechador", "gusano", "centinela", "zombi_espacial", "esqueleto_espacial", "creeper_espacial")
# (distancia de la cámara, altura del punto que mira) en bloques; el resto usa NORMAL.
NORMAL = (2.0, 0.95)
ENCUADRE = {
    "dolfincreepers:gigante_creeper": (5.5, 1.8),
    "dolfincreepers:rey_creeper": (7.5, 2.4),
    "dolfincreepers:mini_creeper": (1.8, 0.5),
    "dolfincreepers:rey_zombie": (5.0, 1.8),
    "dolfinspace:parasito": (1.6, 0.25),
    "dolfinspace:gusano": (2.0, 0.3),
    "dolfinspace:centinela": (2.4, 0.9),
    "dolfinspace:acechador": (3.0, 0.6),
}
# Mobs que solo existen en otra dimensión (el Rey Zombie se borra fuera de las dungeons).
DIMENSION = {"dolfincreepers:rey_zombie": "dolfincreepers:dungeons"}
ANGULO = 32  # grados desde el frente, como motor.lamina()
SEPARACION = 12
COLUMNAS = 7


def mobs() -> list[str]:
    return (
        [f"dolfincreepers:{v}_creeper" for v in CREEPERS]
        + ["dolfincreepers:rey_zombie"]
        + [f"dolfinzombies:{z}_zombie" for z in ZOMBIES]
        + [f"dolfinspace:{k}" for k in KEPLER]
    )


def jar(mod: str) -> Path:
    return max(
        (p for p in (RAIZ / "mods" / mod / "build/libs").glob("*.jar") if not p.stem.endswith("sources")),
        key=lambda p: p.stat().st_mtime,
    )


def preparar() -> None:
    jars = [jar(mod) for mod in MODS]
    shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
    for viejo in (SERVIDOR / "mods").glob("dolfin*.jar"):
        viejo.unlink()
    mods_cliente = CLIENTE / "mods"
    shutil.rmtree(mods_cliente, ignore_errors=True)
    mods_cliente.mkdir(parents=True, exist_ok=True)
    for j in jars:
        shutil.copy2(j, SERVIDOR / "mods" / j.name)
        shutil.copy2(j, mods_cliente / j.name)
    for api in (RAIZ / "runtime/server/mods").glob("fabric-api-*.jar"):
        shutil.copy2(api, mods_cliente / api.name)


def lanzar_cliente() -> None:
    banco.cerrar_cliente()
    banco.preparar_cliente(PUERTO)
    javaw = buscar_java().replace("java.exe", "javaw.exe").replace("/bin/java", "/bin/javaw")
    with open(CLIENTE / "lanzador.log", "w", encoding="utf-8") as log:
        subprocess.Popen(
            [sys.executable, "-m", "portablemc", "--main-dir", str(CLIENTE), "start", "fabric:26.3",
             "--jvm", javaw, "-u", "Tester", "--resolution", "1280x720", "-s", "127.0.0.1", "-p", str(PUERTO)],
            stdout=log,
            stderr=subprocess.STDOUT,
            creationflags=banco.SIN_VENTANA,
        )


def lugar(i: int) -> tuple[int, int]:
    return ((i % COLUMNAS) - COLUMNAS // 2) * SEPARACION, 12 + (i // COLUMNAS) * SEPARACION


def camara(x: float, z: float, mob: str, angulo: float) -> str:
    """Posición y giro del jugador para mirar al mob desde 'angulo' grados de su frente. Sin IA, el cuerpo del mob
    queda mirando al sur (+z) aunque se le pida otra rotación, así que el frente está del lado +z."""
    distancia, centro = ENCUADRE.get(mob, NORMAL)
    a = math.radians(angulo)
    px, pz = x + 0.5 + distancia * math.sin(a), z + 0.5 + distancia * math.cos(a)
    ojo = 1.62
    pies = -60 + centro - ojo + distancia * math.tan(math.radians(14))  # mira 14 grados hacia abajo
    yaw = math.degrees(math.atan2(-(x + 0.5 - px), z + 0.5 - pz))
    return f"{px:.2f} {pies:.2f} {pz:.2f} {yaw:.1f} 14"


def en(mob: str, comando: str) -> str:
    """El comando, ejecutado en la dimensión donde vive ese mob."""
    return f"execute in {DIMENSION.get(mob, 'minecraft:overworld')} run {comando}"


def main() -> None:
    elegidos = [m for m in mobs() if not sys.argv[1:] or any(f in m for f in sys.argv[1:])]
    carpeta = CLIENTE / "capturas" / ("skins-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    carpeta.mkdir(parents=True, exist_ok=True)
    preparar()

    servidor = ServidorFabric(SERVIDOR, memoria="2G", log="prueba_visual.log", timeout_arranque=600.0)
    servidor.iniciar()
    print("Servidor listo. Lanzando el cliente con Fabric...")
    lanzar_cliente()
    try:
        servidor.esperar("Tester joined the game", timeout=900.0)
    except AssertionError:
        servidor.detener()
        raise SystemExit("El cliente no se conectó (mira runtime/test-client/lanzador.log)") from None
    time.sleep(12)

    reglas = ("advance_time false", "advance_weather false", "mob_griefing false", "spawn_mobs false",
              "send_command_feedback false", "log_admin_commands false")
    for regla in reglas:
        servidor.comando(f"gamerule {regla}")
    for dimension in {"minecraft:overworld", *DIMENSION.values()}:
        servidor.comando(f"execute in {dimension} run forceload add -48 -16 48 80", 2)
        servidor.comando(f"execute in {dimension} run fill -48 -61 0 48 -61 72 minecraft:smooth_stone", 1)
    servidor.comando("time set 6000")
    servidor.comando("weather clear")
    servidor.comando("gamemode spectator Tester")

    for i, mob in enumerate(elegidos):
        x, z = lugar(i)
        servidor.comando(en(
            mob,
            f"summon {mob} {x + 0.5} -60 {z + 0.5} "
            "{NoAI:1b,NoGravity:1b,Silent:1b,IsBaby:0b,Rotation:[0f,0f],PersistenceRequired:1b}",
        ))
    time.sleep(3)
    servidor.comando("kill @e[type=minecraft:slime]")
    servidor.comando("clear Tester")
    banco.ventana_minecraft()
    time.sleep(2)

    for i, mob in enumerate(elegidos):
        x, z = lugar(i)
        nombre = mob.split(":")[1]
        for vista, angulo in (("frente", ANGULO), ("espalda", 180 + ANGULO)):
            servidor.comando(en(mob, f"tp Tester {camara(x, z, mob, angulo)}"), 1.5)
            banco.foto(carpeta / f"{i + 1:02d}_{nombre}_{vista}.jpg")
        print("  ", nombre)

    servidor.detener()
    banco.cerrar_cliente()
    print(carpeta)


if __name__ == "__main__":
    main()
