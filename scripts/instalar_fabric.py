"""
Prepara el servidor Fabric: descarga el lanzador oficial y los mods del servidor
desde Modrinth (verificando la huella SHA-512 de cada uno).

Uso:  python scripts/instalar_fabric.py
Resultado: server-fabric/fabric-server-launch.jar y server-fabric/mods/*.jar

Siempre descarga la ultima version de cada mod para la 26.3. Para actualizar:
borra server-fabric/mods/ (salvo tus propios mods) y vuelve a ejecutarlo.
"""
import hashlib
import json
import urllib.parse
import urllib.request
from pathlib import Path

MINECRAFT = "26.3"
RAIZ = Path(__file__).resolve().parent.parent
SERVIDOR = RAIZ / "server-fabric"
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}

# Mods que van SOLO en el servidor (los jugadores no los necesitan).
MODS_SERVIDOR = [
    "fabric-api",               # base que usan casi todos los mods
    # --- Optimizacion ---
    "lithium",                  # fisica, IA y ticks mas eficientes (sin cambiar el juego)
    "c2me-fabric",              # generacion y carga de chunks en varios hilos
    "ferrite-core",             # menos uso de memoria
    "scalablelux",              # calculo de luz mas rapido
    "vmp-fabric",               # mejor rendimiento con muchos jugadores conectados
    # --- Administracion ---
    "luckperms",                # rangos y permisos
    "chunky",                   # pregenerar el mapa
    "spark",                    # ver que causa lag
    "ledger",                   # registro de bloques y deshacer destrozos (como CoreProtect)
    "open-parties-and-claims",  # proteger terrenos y grupos
    "essential-commands",       # /home, /tpa, /spawn...
]


def pedir(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=CABECERAS), timeout=120) as respuesta:
        return respuesta.read()


def ultima_version(proyecto):
    consulta = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([MINECRAFT])})
    versiones = json.loads(pedir(f"https://api.modrinth.com/v2/project/{proyecto}/version?{consulta}"))
    if not versiones:
        raise LookupError(f"{proyecto} no tiene version para Fabric {MINECRAFT}")
    return versiones[0]


def descargar_mod(proyecto, carpeta, instalados=None):
    """Descarga el mod y, antes, sus dependencias obligatorias (p. ej. librerias de Kotlin)."""
    instalados = set() if instalados is None else instalados
    if proyecto in instalados:
        return None
    instalados.add(proyecto)
    version = ultima_version(proyecto)
    for dependencia in version.get("dependencies", []):
        if dependencia["dependency_type"] == "required" and dependencia.get("project_id"):
            descargar_mod(dependencia["project_id"], carpeta, instalados)
    archivo = next((f for f in version["files"] if f.get("primary")), version["files"][0])
    if (carpeta / archivo["filename"]).exists():
        print(f"  {archivo['filename']:55}  ya estaba")
        return archivo["filename"]
    datos = pedir(archivo["url"])
    if hashlib.sha512(datos).hexdigest() != archivo["hashes"]["sha512"]:
        raise ValueError(f"La huella de {archivo['filename']} no coincide")
    (carpeta / archivo["filename"]).write_bytes(datos)
    print(f"  {archivo['filename']:55} {len(datos) // 1024:>6} KB  sha512 OK")
    return archivo["filename"]


def lanzador():
    cargador = json.loads(pedir(f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}"))[0]["loader"]["version"]
    instalador = json.loads(pedir("https://meta.fabricmc.net/v2/versions/installer"))[0]["version"]
    url = f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}/{cargador}/{instalador}/server/jar"
    (SERVIDOR / "fabric-server-launch.jar").write_bytes(pedir(url))
    print(f"  fabric-server-launch.jar (loader {cargador}, installer {instalador})")


if __name__ == "__main__":
    mods = SERVIDOR / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    print("Lanzador de Fabric:")
    lanzador()
    print("Mods del servidor (y sus dependencias):")
    instalados = set()
    for proyecto in MODS_SERVIDOR:
        descargar_mod(proyecto, mods, instalados)
