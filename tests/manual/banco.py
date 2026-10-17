"""
Utilidades del cliente de pruebas (Minecraft 26.3 con el jugador offline "Tester"):
preparar su configuracion, cerrarlo y hacer capturas de su ventana.

Cliente de pruebas: test-client/ (no toca tu .minecraft).
Capturas:           test-client/capturas/
"""
import struct
import subprocess
from pathlib import Path

from PIL import ImageGrab

CLIENTE = Path(__file__).resolve().parents[2] / "runtime/test-client"
SIN_VENTANA = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS


# ----------------------------------------------------------------------------
#  Cliente
# ----------------------------------------------------------------------------

def _nbt_texto(nombre, valor):
    datos = valor.encode("utf-8")
    return b"\x08" + struct.pack(">H", len(nombre)) + nombre.encode() + struct.pack(">H", len(datos)) + datos


def preparar_cliente(puerto):
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
