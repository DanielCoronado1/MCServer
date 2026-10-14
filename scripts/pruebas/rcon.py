"""
Cliente RCON minimo para enviar comandos a la consola del servidor de pruebas.

Uso:  python scripts/pruebas/rcon.py "comando" ["otro comando" ...]
Lee el puerto y la contrasena de test-server/server.properties.
"""
import socket
import struct
import sys
from pathlib import Path

PROPIEDADES = Path(__file__).resolve().parents[2] / "test-server" / "server.properties"


def leer_config():
    datos = {}
    for linea in PROPIEDADES.read_text(encoding="utf-8").splitlines():
        if "=" in linea and not linea.startswith("#"):
            clave, valor = linea.split("=", 1)
            datos[clave.strip()] = valor.strip()
    return int(datos["rcon.port"]), datos["rcon.password"]


def _paquete(id_, tipo, texto):
    cuerpo = struct.pack("<ii", id_, tipo) + texto.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(cuerpo)) + cuerpo


def _recibir(sock):
    largo = struct.unpack("<i", _exacto(sock, 4))[0]
    datos = _exacto(sock, largo)
    id_, tipo = struct.unpack("<ii", datos[:8])
    return id_, datos[8:-2].decode("utf-8", "replace")


def _exacto(sock, n):
    datos = b""
    while len(datos) < n:
        trozo = sock.recv(n - len(datos))
        if not trozo:
            raise ConnectionError("RCON cerro la conexion")
        datos += trozo
    return datos


def enviar(*comandos):
    puerto, clave = leer_config()
    respuestas = []
    with socket.create_connection(("127.0.0.1", puerto), timeout=10) as sock:
        sock.sendall(_paquete(1, 3, clave))
        id_, _ = _recibir(sock)
        if id_ == -1:
            raise PermissionError("Contrasena RCON incorrecta")
        for i, comando in enumerate(comandos, start=2):
            sock.sendall(_paquete(i, 2, comando))
            respuestas.append(_recibir(sock)[1])
    return respuestas


if __name__ == "__main__":
    for comando, respuesta in zip(sys.argv[1:], enviar(*sys.argv[1:])):
        print(f"> {comando}\n{respuesta}")
