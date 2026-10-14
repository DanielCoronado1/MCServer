"""
Crea el modpack para los jugadores: modpack/DolfinServer-<version>.mrpack

Se importa con un clic en Prism Launcher o Modrinth App (Importar > archivo .mrpack).
Incluye Fabric para la 26.3, los mods que necesita el jugador (descargados de Modrinth
al instalar) y nuestros propios mods (dentro del archivo).

Uso:  python scripts/crear_modpack.py [version]
"""
import hashlib
import json
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

MINECRAFT = "26.3"
RAIZ = Path(__file__).resolve().parent.parent
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}

# Mods de Modrinth para el jugador: (proyecto, "required" u "optional")
MODS_JUGADOR = [
    ("fabric-api", "required"),     # lo necesitan nuestros mods
    ("sodium", "optional"),         # muchos mas FPS
    ("lithium", "optional"),        # tambien optimiza el juego en un jugador
    ("ferrite-core", "optional"),   # menos memoria
]
# Nuestros mods: se compilan en mods-src/<carpeta>/build/libs
MODS_PROPIOS = ["creepers"]


def pedir(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=CABECERAS), timeout=120) as respuesta:
        return respuesta.read()


def archivo_modrinth(proyecto, entorno_cliente):
    consulta = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([MINECRAFT])})
    version = json.loads(pedir(f"https://api.modrinth.com/v2/project/{proyecto}/version?{consulta}"))[0]
    archivo = next((f for f in version["files"] if f.get("primary")), version["files"][0])
    print(f"  {archivo['filename']}")
    return {
        "path": f"mods/{archivo['filename']}",
        "hashes": {"sha1": archivo["hashes"]["sha1"], "sha512": archivo["hashes"]["sha512"]},
        "env": {"client": entorno_cliente, "server": "unsupported"},
        "downloads": [archivo["url"]],
        "fileSize": archivo["size"],
    }


def cargador_fabric():
    return json.loads(pedir(f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}"))[0]["loader"]["version"]


if __name__ == "__main__":
    version = sys.argv[1] if len(sys.argv) > 1 else "1.0.0"
    print("Mods de Modrinth:")
    indice = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": "DolfinServer",
        "summary": "Modpack oficial de DolfinServer (Fabric 26.3)",
        "files": [archivo_modrinth(proyecto, entorno) for proyecto, entorno in MODS_JUGADOR],
        "dependencies": {"minecraft": MINECRAFT, "fabric-loader": cargador_fabric()},
    }

    salida = RAIZ / "modpack" / f"DolfinServer-{version}.mrpack"
    salida.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as mrpack:
        mrpack.writestr("modrinth.index.json", json.dumps(indice, indent=2))
        print("Mods propios:")
        for carpeta in MODS_PROPIOS:
            jars = [j for j in (RAIZ / "mods-src" / carpeta / "build" / "libs").glob("*.jar")
                    if not j.name.endswith("-sources.jar")]
            if not jars:
                raise FileNotFoundError(f"Compila primero mods-src/{carpeta} (gradlew build)")
            jar = max(jars, key=lambda j: j.stat().st_mtime)
            mrpack.write(jar, f"overrides/mods/{jar.name}")
            print(f"  {jar.name}  sha1 {hashlib.sha1(jar.read_bytes()).hexdigest()[:12]}")
    print("Modpack:", salida)
