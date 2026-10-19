"""
Instala o actualiza el servidor Fabric a partir de pack/ (la lista de mods de packwiz):
descarga el lanzador de Fabric y las versiones exactas de cada mod, verificando su huella.

  - Mods con side "server" o "both"  -> mods/
  - Mods con side "client" o "both"  -> automodpack/host-modpack/main/mods/
    (AutoModpack se los envia a los jugadores al conectarse)
  - Nuestros mods                    -> a las dos carpetas

Uso:
  python3 scripts/install_server.py [--server-dir /path/to/server]

Server templates live in server/; runtime files default to runtime/server/.
Custom jars come from mods/*/build/libs in a checkout or custom-mods/ in a release.

Los mods que instalo una version anterior y ya no estan en el pack se borran. Los que
pongas tu a mano en mods/ no se tocan (pero mejor anadelos al pack con packwiz).
Necesita Python 3.11 o mas reciente.
"""

import argparse
import hashlib
import json
import re
import shutil
import tomllib
import urllib.request
import uuid
from pathlib import Path

INSTALADOR_FABRIC = "1.1.2"  # version del instalador de Fabric que genera el lanzador
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}

RAIZ = Path(__file__).resolve().parent.parent
PACK = RAIZ / "pack"
PLANTILLAS = RAIZ / "server"
SERVIDOR = RAIZ / "runtime" / "server"


def mods_propios():
    # Releases contain built jars; checkouts contain independent Gradle projects.
    if (RAIZ / "custom-mods").is_dir():
        return sorted((RAIZ / "custom-mods").glob("*.jar"))
    jars = []
    for proyecto in sorted((RAIZ / "mods").iterdir()):
        if not (proyecto / "build.gradle").is_file():
            continue
        candidatos = [
            j
            for j in (proyecto / "build" / "libs").glob("*.jar")
            if not j.stem.endswith(("-sources", "-dev"))
        ]
        if not candidatos:
            raise FileNotFoundError(f"Compila primero {proyecto.relative_to(RAIZ)} (gradlew build)")
        jars.append(max(candidatos, key=lambda j: j.stat().st_mtime))
    return jars


def preparar_servidor():
    """Seed missing configuration only; preserve the operator's live settings."""
    SERVIDOR.mkdir(parents=True, exist_ok=True)
    for origen in sorted(PLANTILLAS.rglob("*")):
        if not origen.is_file() or origen.name == "whitelist.txt":
            continue
        destino = SERVIDOR / origen.relative_to(PLANTILLAS)
        if not destino.exists():
            destino.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(origen, destino)
    eula = SERVIDOR / "eula.txt"
    if not eula.exists():
        eula.write_text("# https://aka.ms/MinecraftEULA\neula=false\n", encoding="utf-8")


def sincronizar_whitelist():
    """Merge names into Minecraft's UUID-based file without removing existing players."""
    propiedades = {}
    for linea in (SERVIDOR / "server.properties").read_text(encoding="utf-8-sig").splitlines():
        if "=" in linea and not linea.startswith("#"):
            clave, valor = linea.split("=", 1)
            propiedades[clave.strip()] = valor.strip()
    offline = propiedades.get("online-mode", "true").lower() == "false"
    ruta = SERVIDOR / "whitelist.json"
    entradas = json.loads(ruta.read_text(encoding="utf-8")) if ruta.exists() else []
    nombres = (PLANTILLAS / "whitelist.txt").read_text(encoding="utf-8").splitlines()
    cache = SERVIDOR / "usercache.json"
    perfiles = json.loads(cache.read_text(encoding="utf-8")) if cache.exists() else []
    for nombre in nombres:
        nombre = nombre.strip()
        if not nombre or nombre.startswith("#"):
            continue
        if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", nombre):
            raise ValueError(f"Nombre de Minecraft invalido: {nombre}")
        if offline:
            # Java UUID.nameUUIDFromBytes uses MD5 with UUID version 3 bits.
            identificador = str(
                uuid.UUID(bytes=hashlib.md5(f"OfflinePlayer:{nombre}".encode()).digest(), version=3)
            )
        else:
            perfil = next((p for p in perfiles if p["name"].lower() == nombre.lower()), None)
            if perfil is None:
                raise ValueError(
                    f"Perfil online desconocido: {nombre}. Ejecuta 'whitelist add {nombre}' en consola primero."
                )
            identificador = perfil["uuid"]
        entradas = [e for e in entradas if e["name"].lower() != nombre.lower()]
        entradas.append({"uuid": identificador, "name": nombre})
    ruta.write_text(json.dumps(entradas, indent=2) + "\n", encoding="utf-8")


MODS_SERVIDOR = Path("mods")
MODS_JUGADOR = Path("automodpack/host-modpack/main/mods")
REGISTRO = SERVIDOR / ".dolfin-instalados.json"


def pedir(url):
    with urllib.request.urlopen(
        urllib.request.Request(url, headers=CABECERAS), timeout=120
    ) as respuesta:
        return respuesta.read()


def leer_pack():
    """Devuelve (pack.toml, [metadatos de cada mod])."""
    pack = tomllib.loads((PACK / "pack.toml").read_text(encoding="utf-8"))
    indice = tomllib.loads((PACK / pack["index"]["file"]).read_text(encoding="utf-8"))
    mods = [
        tomllib.loads((PACK / f["file"]).read_text(encoding="utf-8"))
        for f in indice["files"]
        if f.get("metafile")
    ]
    return pack, mods


def huella_correcta(datos, descarga):
    return hashlib.new(descarga["hash-format"], datos).hexdigest() == descarga["hash"]


def instalar_mod(mod, destino):
    if destino.exists() and huella_correcta(destino.read_bytes(), mod["download"]):
        return
    datos = pedir(mod["download"]["url"])
    if not huella_correcta(datos, mod["download"]):
        raise ValueError(
            f"La huella de {mod['filename']} no coincide: descarga corrupta o manipulada"
        )
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_bytes(datos)
    print(f"  {destino.relative_to(SERVIDOR)}")


def main():
    propios = mods_propios()
    preparar_servidor()
    sincronizar_whitelist()
    pack, mods = leer_pack()
    anterior = (
        json.loads(REGISTRO.read_text(encoding="utf-8")) if REGISTRO.exists() else {"archivos": []}
    )

    lanzador = f"{pack['versions']['fabric']}/{INSTALADOR_FABRIC}"
    if anterior.get("lanzador") != lanzador or not (SERVIDOR / "fabric-server-launch.jar").exists():
        print(
            f"Lanzador de Fabric {pack['versions']['minecraft']} (cargador {pack['versions']['fabric']})"
        )
        url = (
            f"https://meta.fabricmc.net/v2/versions/loader/{pack['versions']['minecraft']}/"
            f"{pack['versions']['fabric']}/{INSTALADOR_FABRIC}/server/jar"
        )
        (SERVIDOR / "fabric-server-launch.jar").write_bytes(pedir(url))

    print(f"Mods de {pack['name']} {pack['version']}:")
    instalados = []
    for mod in mods:
        carpetas = []
        if mod["side"] in ("server", "both"):
            carpetas.append(MODS_SERVIDOR)
        # AutoModpack nunca se envia dentro del pack: cada jugador lo instala por su cuenta.
        if mod["side"] in ("client", "both") and not mod["filename"].startswith("automodpack-"):
            carpetas.append(MODS_JUGADOR)
        for carpeta in carpetas:
            instalar_mod(mod, SERVIDOR / carpeta / mod["filename"])
            instalados.append((carpeta / mod["filename"]).as_posix())

    print("Nuestros mods:")
    for jar in propios:
        for carpeta in (MODS_SERVIDOR, MODS_JUGADOR):
            (SERVIDOR / carpeta).mkdir(parents=True, exist_ok=True)
            shutil.copy2(jar, SERVIDOR / carpeta / jar.name)
            instalados.append((carpeta / jar.name).as_posix())
        print(f"  {jar.name}")

    for viejo in set(anterior["archivos"]) - set(instalados):
        if (SERVIDOR / viejo).exists():
            (SERVIDOR / viejo).unlink()
            print(f"  borrado {viejo}")

    REGISTRO.write_text(
        json.dumps({"lanzador": lanzador, "archivos": sorted(instalados)}, indent=2),
        encoding="utf-8",
    )
    print("Listo. Si el servidor estaba encendido, reinicialo para cargar los cambios.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Install Fabric and the locked modpack; stop the server first."
    )
    parser.add_argument(
        "--server-dir",
        type=Path,
        default=SERVIDOR,
        help="Runtime directory (e.g. Crafty's managed server)",
    )
    args = parser.parse_args()
    SERVIDOR = args.server_dir.resolve()
    REGISTRO = SERVIDOR / ".dolfin-instalados.json"
    main()
