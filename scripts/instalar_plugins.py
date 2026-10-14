"""
Descarga los plugins de terceros desde Modrinth (fuente oficial), verifica su
huella SHA-512 y los deja en una carpeta.

Uso:  python scripts/instalar_plugins.py <carpeta_plugins>
Por ejemplo: python scripts/instalar_plugins.py test-server/plugins
"""
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

# proyecto de Modrinth -> version exacta (probadas con Paper 26.3)
PLUGINS = {
    "coreprotect": "24.1",
    "luckperms": "v5.5.71-bukkit",
    "essentialsx": "2.22.0",
    "griefprevention": "16.18.7",
    "chunky": "1.5.3",
    "discordsrv": "1.30.5",
}
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}


def pedir(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=CABECERAS), timeout=60) as respuesta:
        return respuesta.read()


def instalar(destino: Path):
    destino.mkdir(parents=True, exist_ok=True)
    for proyecto, version in PLUGINS.items():
        versiones = json.loads(pedir(f"https://api.modrinth.com/v2/project/{proyecto}/version"))
        # Misma version puede existir para Fabric, Forge...: nos quedamos con la de Paper/Bukkit.
        elegida = next(v for v in versiones if v["version_number"] == version
                       and {"paper", "bukkit", "spigot"} & set(v["loaders"]))
        archivo = next(f for f in elegida["files"] if f.get("primary")) if any(
            f.get("primary") for f in elegida["files"]) else elegida["files"][0]
        datos = pedir(archivo["url"])
        if hashlib.sha512(datos).hexdigest() != archivo["hashes"]["sha512"]:
            raise ValueError(f"La huella de {archivo['filename']} no coincide: descarga corrupta")
        nombre = "GriefPrevention-" + version + ".jar" if archivo["filename"] == "GriefPrevention.jar" else archivo["filename"]
        (destino / nombre).write_bytes(datos)
        print(f"  {nombre:40} {len(datos) // 1024:>6} KB  sha512 OK")


if __name__ == "__main__":
    instalar(Path(sys.argv[1]))
