"""
Banco de pruebas visual del evento /apocalipsis.

  python scripts/pruebas/banco.py servidor   compila, copia plugins y pack, (re)inicia el servidor de pruebas
  python scripts/pruebas/banco.py cliente    (re)lanza Minecraft 26.3 con el jugador offline "Tester"
  python scripts/pruebas/banco.py capturar   lanza el evento y guarda capturas de cada fase
  python scripts/pruebas/banco.py todo       las tres cosas seguidas

Servidor de pruebas: test-server/ (puerto 25566, offline, solo local).
Cliente de pruebas:  test-client/ (no toca tu .minecraft).
Capturas:            test-client/capturas/<fecha>/
"""
import shutil
import struct
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

from PIL import ImageGrab

sys.path.insert(0, str(Path(__file__).parent))
import rcon  # noqa: E402

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR_REAL = RAIZ / "server"
SERVIDOR = RAIZ / "test-server"
CLIENTE = RAIZ / "test-client"
JAVA = Path(r"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin")
PUERTO = 25566
JUGADOR = "Tester"
SIN_VENTANA = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS

# (segundo desde el inicio del evento, yaw, pitch, nombre). yaw 180 = mirar al norte.
GUION = [
    (2, 180, -35, "01_inicio"),
    (6, 180, -55, "02_mancha"),
    (10, 180, -70, "03_extendiendose"),
    (12.5, 180, -89, "04_remolino_arriba"),
    (13, 180, -25, "05_horizonte"),
    (13.5, 180, -32, "06_rayo"),
    (16, 180, -32, "07_grieta_abriendo"),
    (19, 180, -32, "08_grieta_abierta"),
    (22, 180, -32, "09_dragon_saliendo"),
    (25, 180, -32, "10_dragon_fuera"),
    (30, 150, -30, "11_dragon_lado"),
    (45, 180, -32, "12_cierre"),
    (52, 180, -60, "13_encogiendo"),
    (60, 180, -40, "14_final"),
]


# ----------------------------------------------------------------------------
#  Servidor
# ----------------------------------------------------------------------------

def servidor_vivo():
    try:
        rcon.enviar("list")
        return True
    except OSError:
        return False


def servidor():
    print("Compilando plugins...")
    subprocess.run(["cmd", "/c", str(RAIZ / "plugins-src" / "gradlew.bat"), "deploy", "-q"],
                   cwd=RAIZ / "plugins-src", check=True)

    if servidor_vivo():
        print("Deteniendo servidor de pruebas...")
        rcon.enviar("stop")
        for _ in range(60):
            time.sleep(1)
            if not servidor_vivo():
                break
        time.sleep(3)

    plugins = SERVIDOR / "plugins"
    for jar in (SERVIDOR_REAL / "plugins").glob("*.jar"):
        shutil.copy2(jar, plugins / jar.name)
    shutil.copy2(SERVIDOR_REAL / "plugins/DolfinPack/pack.zip", plugins / "DolfinPack/pack.zip")
    shutil.copy2(RAIZ / "plugins-src/sky-animations/src/main/resources/config.yml", plugins / "DolfinCielo/config.yml")

    print("Iniciando servidor de pruebas...")
    with open(SERVIDOR / "consola.log", "w", encoding="utf-8") as log:
        subprocess.Popen([str(JAVA / "java.exe"), "-Xms3G", "-Xmx3G", "-jar", "paper.jar", "--nogui"],
                         cwd=SERVIDOR, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                         creationflags=SIN_VENTANA)
    for _ in range(120):
        time.sleep(1)
        if servidor_vivo():
            print("Servidor listo.")
            return
    raise TimeoutError("El servidor de pruebas no arranco (mira test-server/consola.log)")


# ----------------------------------------------------------------------------
#  Cliente
# ----------------------------------------------------------------------------

def _nbt_texto(nombre, valor):
    datos = valor.encode("utf-8")
    return b"\x08" + struct.pack(">H", len(nombre)) + nombre.encode() + struct.pack(">H", len(datos)) + datos


def preparar_cliente(puerto=PUERTO):
    CLIENTE.mkdir(exist_ok=True)
    opciones = CLIENTE / "options.txt"
    ajustes = {
        "onboardAccessibility": "false",
        "skipMultiplayerWarning": "true",
        "joinedFirstServer": "true",
        "tutorialStep": "none",
        "pauseOnLostFocus": "false",
        "renderDistance": "8",
        "guiScale": "2",
        "fov": "0.25",
        "narrator": "0",
        "soundCategory_master": "0.0",
    }
    if opciones.exists():
        for linea in opciones.read_text(encoding="utf-8").splitlines():
            clave = linea.split(":", 1)[0]
            ajustes.setdefault(clave, linea.split(":", 1)[1] if ":" in linea else "")
    opciones.write_text("\n".join(f"{k}:{v}" for k, v in ajustes.items()) + "\n", encoding="utf-8")

    # servers.dat con el servidor de pruebas y "aceptar texturas" activado,
    # para que el pack se instale sin preguntar.
    servidor = (b"\x0a\x00\x00"  # compound sin nombre (elemento de la lista)
                + _nbt_texto("name", "Pruebas")
                + _nbt_texto("ip", f"127.0.0.1:{puerto}")
                + b"\x01" + struct.pack(">H", 14) + b"acceptTextures" + b"\x01"
                + b"\x00")
    lista = b"\x09" + struct.pack(">H", 7) + b"servers" + b"\x0a" + struct.pack(">i", 1) + servidor[3:]
    (CLIENTE / "servers.dat").write_bytes(b"\x0a\x00\x00" + lista + b"\x00")


def cerrar_cliente():
    subprocess.run(["powershell", "-NoProfile", "-Command",
                    "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | "
                    "? { $_.CommandLine -like '*test-client*' } | % { Stop-Process -Id $_.ProcessId -Force }"],
                   check=False)


def cliente():
    cerrar_cliente()
    preparar_cliente()
    print("Lanzando Minecraft de pruebas...")
    with open(CLIENTE / "lanzador.log", "w", encoding="utf-8") as log:
        subprocess.Popen([sys.executable, "-m", "portablemc", "--main-dir", str(CLIENTE), "start", "26.3",
                          "--jvm", str(JAVA / "javaw.exe"), "-u", JUGADOR, "--resolution", "1280x720",
                          "-s", "127.0.0.1", "-p", str(PUERTO)],
                         stdout=log, stderr=subprocess.STDOUT, creationflags=SIN_VENTANA)
    for _ in range(240):
        time.sleep(1)
        if JUGADOR in rcon.enviar("list")[0]:
            print("Tester conectado. Esperando a que cargue el pack...")
            time.sleep(20)
            return
    raise TimeoutError("El cliente no se conecto (mira test-client/lanzador.log)")


# ----------------------------------------------------------------------------
#  Capturas
# ----------------------------------------------------------------------------

def ventana_minecraft():
    """Devuelve el rectangulo (izq, arriba, der, abajo) del area de juego y la trae al frente."""
    import ctypes
    from ctypes import wintypes
    user32 = ctypes.windll.user32
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
    encontrada = []

    def revisar(hwnd, _):
        largo = user32.GetWindowTextLengthW(hwnd)
        titulo = ctypes.create_unicode_buffer(largo + 1)
        user32.GetWindowTextW(hwnd, titulo, largo + 1)
        if titulo.value.startswith("Minecraft") and user32.IsWindowVisible(hwnd):
            encontrada.append(hwnd)
        return True

    user32.EnumWindows(ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)(revisar), 0)
    if not encontrada:
        return None
    hwnd = encontrada[0]
    user32.ShowWindow(hwnd, 9)          # restaurar si esta minimizada
    user32.SetForegroundWindow(hwnd)
    rect = wintypes.RECT()
    user32.GetClientRect(hwnd, ctypes.byref(rect))
    origen = wintypes.POINT(0, 0)
    user32.ClientToScreen(hwnd, ctypes.byref(origen))
    return origen.x, origen.y, origen.x + rect.right, origen.y + rect.bottom


def foto(ruta):
    zona = ventana_minecraft()
    imagen = ImageGrab.grab(bbox=zona, all_screens=True) if zona else ImageGrab.grab()
    imagen.thumbnail((1280, 1280))
    imagen.save(ruta, quality=85)


def capturar():
    if JUGADOR not in rcon.enviar("list")[0]:
        raise RuntimeError("Tester no esta conectado: ejecuta primero 'cliente'")
    carpeta = CLIENTE / "capturas" / datetime.now().strftime("%Y%m%d-%H%M%S")
    carpeta.mkdir(parents=True)

    rcon.enviar("apocalipsis detener", "time set 6000", "weather clear",
                f"gamemode spectator {JUGADOR}", f"tp {JUGADOR} 0 -55 0 180 -35")
    time.sleep(3)
    rcon.enviar(f"apocalipsis iniciar {JUGADOR}")
    inicio = time.time()

    print("Capturando", len(GUION), "fases...")
    for segundo, yaw, pitch, nombre in GUION:
        espera = inicio + segundo - time.time()
        if espera > 0:
            time.sleep(espera)
        rcon.enviar(f"tp {JUGADOR} 0 -55 0 {yaw} {pitch}")
        time.sleep(0.35)
        foto(carpeta / f"{nombre}.jpg")
        print(f"  {segundo:>5}s  {nombre}")

    entidades = rcon.enviar("execute if entity @e[type=item_display]", "execute if entity @e[type=ender_dragon]")
    print("  Al final quedan:", " | ".join(entidades))
    print("Capturas en", carpeta)


if __name__ == "__main__":
    accion = sys.argv[1] if len(sys.argv) > 1 else "todo"
    if accion in ("servidor", "todo"):
        servidor()
    if accion in ("cliente", "todo"):
        cliente()
    if accion in ("capturar", "todo"):
        capturar()
