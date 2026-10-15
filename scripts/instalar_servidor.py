"""
Instala o actualiza el servidor Fabric a partir de pack/ (la lista de mods de packwiz):
descarga el lanzador de Fabric y las versiones exactas de cada mod, verificando su huella.

  - Mods con side "server" o "both"  -> mods/
  - Mods con side "client" o "both"  -> automodpack/host-modpack/main/mods/
    (AutoModpack se los envia a los jugadores al conectarse)
  - Nuestros mods                    -> a las dos carpetas

Uso:
  python scripts/instalar_servidor.py   en el repositorio: instala en server-fabric/
                                        (nuestros mods salen de mods-src/*/build/libs)
  python instalar.py                    en el zip de una release: instala en esa misma carpeta

Los mods que instalo una version anterior y ya no estan en el pack se borran. Los que
pongas tu a mano en mods/ no se tocan (pero mejor anadelos al pack con packwiz).
Necesita Python 3.11 o mas reciente.
"""
import hashlib
import json
import shutil
import tomllib
import urllib.request
from pathlib import Path

INSTALADOR_FABRIC = "1.1.2"  # version del instalador de Fabric que genera el lanzador
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}

AQUI = Path(__file__).resolve().parent
if (AQUI / "pack").is_dir():
    # Zip de una release: pack/ y mods-propios/ estan junto a este script.
    PACK = AQUI / "pack"
    SERVIDOR = AQUI
    PROPIOS = sorted((AQUI / "mods-propios").glob("*.jar"))
else:
    RAIZ = AQUI.parent
    PACK = RAIZ / "pack"
    SERVIDOR = RAIZ / "server-fabric"
    PROPIOS = []
    for proyecto in sorted((RAIZ / "mods-src").iterdir()):
        jars = [j for j in (proyecto / "build" / "libs").glob("*.jar") if not j.stem.endswith(("-sources", "-dev"))]
        if not jars:
            raise FileNotFoundError(f"Compila primero {proyecto.relative_to(RAIZ)} (gradlew build)")
        PROPIOS.append(max(jars, key=lambda j: j.stat().st_mtime))

MODS_SERVIDOR = Path("mods")
MODS_JUGADOR = Path("automodpack/host-modpack/main/mods")
REGISTRO = SERVIDOR / ".dolfin-instalados.json"


def pedir(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=CABECERAS), timeout=120) as respuesta:
        return respuesta.read()


def leer_pack():
    """Devuelve (pack.toml, [metadatos de cada mod])."""
    pack = tomllib.loads((PACK / "pack.toml").read_text(encoding="utf-8"))
    indice = tomllib.loads((PACK / pack["index"]["file"]).read_text(encoding="utf-8"))
    mods = [tomllib.loads((PACK / f["file"]).read_text(encoding="utf-8"))
            for f in indice["files"] if f.get("metafile")]
    return pack, mods


def huella_correcta(datos, descarga):
    return hashlib.new(descarga["hash-format"], datos).hexdigest() == descarga["hash"]


def instalar_mod(mod, destino):
    if destino.exists() and huella_correcta(destino.read_bytes(), mod["download"]):
        return
    datos = pedir(mod["download"]["url"])
    if not huella_correcta(datos, mod["download"]):
        raise ValueError(f"La huella de {mod['filename']} no coincide: descarga corrupta o manipulada")
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_bytes(datos)
    print(f"  {destino.relative_to(SERVIDOR)}")


def main():
    pack, mods = leer_pack()
    anterior = json.loads(REGISTRO.read_text(encoding="utf-8")) if REGISTRO.exists() else {"archivos": []}

    lanzador = f"{pack['versions']['fabric']}/{INSTALADOR_FABRIC}"
    if anterior.get("lanzador") != lanzador or not (SERVIDOR / "fabric-server-launch.jar").exists():
        print(f"Lanzador de Fabric {pack['versions']['minecraft']} (cargador {pack['versions']['fabric']})")
        url = (f"https://meta.fabricmc.net/v2/versions/loader/{pack['versions']['minecraft']}/"
               f"{pack['versions']['fabric']}/{INSTALADOR_FABRIC}/server/jar")
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
    for jar in PROPIOS:
        for carpeta in (MODS_SERVIDOR, MODS_JUGADOR):
            (SERVIDOR / carpeta).mkdir(parents=True, exist_ok=True)
            shutil.copy2(jar, SERVIDOR / carpeta / jar.name)
            instalados.append((carpeta / jar.name).as_posix())
        print(f"  {jar.name}")

    for viejo in set(anterior["archivos"]) - set(instalados):
        if (SERVIDOR / viejo).exists():
            (SERVIDOR / viejo).unlink()
            print(f"  borrado {viejo}")

    REGISTRO.write_text(json.dumps({"lanzador": lanzador, "archivos": sorted(instalados)}, indent=2), encoding="utf-8")
    print("Listo. Si el servidor estaba encendido, reinicialo para cargar los cambios.")


if __name__ == "__main__":
    main()
