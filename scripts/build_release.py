"""
Crea los archivos de una release en dist/:

  DolfinServer-servidor-<version>.zip   para el administrador: plantillas server/, docs y deploy/,
                                        pack/ (packwiz), nuestros mods y scripts/install_server.py, que
                                        descarga el resto de mods de Modrinth.
  DolfinServer-<version>.mrpack         para los jugadores: Fabric + AutoModpack. El resto
                                        de mods se lo envia el servidor al conectarse.
  notas.md                              texto de la release (instrucciones y lista de mods)

No incluye mods de terceros dentro de los archivos (el repositorio es publico): solo sus
enlaces de descarga y huellas.

Uso:  python scripts/build_release.py <version>   (antes: gradlew build en cada mods/*)
"""
import json
import sys
import tomllib
import urllib.request
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PACK = RAIZ / "pack"
DIST = RAIZ / "dist"
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}


def pedir_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=CABECERAS), timeout=120) as respuesta:
        return json.loads(respuesta.read())


def leer_pack():
    pack = tomllib.loads((PACK / "pack.toml").read_text(encoding="utf-8"))
    indice = tomllib.loads((PACK / pack["index"]["file"]).read_text(encoding="utf-8"))
    mods = [tomllib.loads((PACK / f["file"]).read_text(encoding="utf-8"))
            for f in indice["files"] if f.get("metafile")]
    return pack, indice, mods


def mods_propios():
    jars = []
    for proyecto in sorted((RAIZ / "mods").iterdir()):
        if not (proyecto / "build.gradle").is_file():
            continue
        candidatos = [j for j in (proyecto / "build" / "libs").glob("*.jar")
                      if not j.stem.endswith(("-sources", "-dev"))]
        if not candidatos:
            raise FileNotFoundError(f"Compila primero mods/{proyecto.name} (gradlew build)")
        jars.append(max(candidatos, key=lambda j: j.stat().st_mtime))
    return jars


def archivos_del_servidor():
    """Only templates are packaged; runtime state is never a release input."""
    return sorted(p for p in (RAIZ / "server").rglob("*") if p.is_file())


def crear_zip_servidor(version, pack, indice, propios):
    carpeta = f"DolfinServer-servidor-{version}"
    salida = DIST / f"{carpeta}.zip"
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as zip_:
        for ruta in archivos_del_servidor():
            zip_.write(ruta, f"{carpeta}/{ruta.relative_to(RAIZ).as_posix()}")
        zip_.write(PACK / "pack.toml", f"{carpeta}/pack/pack.toml")
        zip_.write(PACK / pack["index"]["file"], f"{carpeta}/pack/{pack['index']['file']}")
        for archivo in indice["files"]:
            zip_.write(PACK / archivo["file"], f"{carpeta}/pack/{archivo['file']}")
        for jar in propios:
            zip_.write(jar, f"{carpeta}/custom-mods/{jar.name}")
        zip_.write(RAIZ / "scripts" / "install_server.py", f"{carpeta}/scripts/install_server.py")
        for ruta in [RAIZ / "README.md", *sorted((RAIZ / "docs").rglob("*.md")),
                     *sorted(p for p in (RAIZ / "deploy").rglob("*")
                             if p.suffix in {".yaml", ".py", ".ps1", ".sh"})]:
            zip_.write(ruta, f"{carpeta}/{ruta.relative_to(RAIZ).as_posix()}")
    return salida


def crear_mrpack(version, pack, automodpack):
    archivo = next(f for f in automodpack["files"] if f["primary"])
    indice = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": pack["name"],
        "summary": f"Fabric {pack['versions']['minecraft']} + AutoModpack: el servidor te envia el resto de mods",
        "files": [{
            "path": f"mods/{archivo['filename']}",
            "hashes": {"sha1": archivo["hashes"]["sha1"], "sha512": archivo["hashes"]["sha512"]},
            "env": {"client": "required", "server": "unsupported"},
            "downloads": [archivo["url"]],
            "fileSize": archivo["size"],
        }],
        "dependencies": {"minecraft": pack["versions"]["minecraft"], "fabric-loader": pack["versions"]["fabric"]},
    }
    salida = DIST / f"{pack['name']}-{version}.mrpack"
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as mrpack:
        mrpack.writestr("modrinth.index.json", json.dumps(indice, indent=2))
    return salida


def crear_notas(version, pack, mods, propios, automodpack):
    minecraft, cargador = pack["versions"]["minecraft"], pack["versions"]["fabric"]
    enlace_amp = f"https://modrinth.com/mod/automodpack/version/{automodpack['id']}"
    lado = {"server": "solo servidor", "client": "solo jugadores", "both": "servidor y jugadores"}
    filas = [f"| {m['name']} | `{m['filename']}` | {lado[m['side']]} |" for m in sorted(mods, key=lambda m: m["name"].lower())]
    filas += [f"| (nuestro) | `{jar.name}` | servidor y jugadores |" for jar in propios]
    texto = f"""## Jugadores

1. Descarga `{pack['name']}-{version}.mrpack` e importalo en **Prism Launcher** o **Modrinth App** (Importar > archivo). Instala Minecraft {minecraft}, Fabric {cargador} y AutoModpack.
   - Con otro launcher: instala Fabric {cargador} para Minecraft {minecraft} y pon [AutoModpack {automodpack['version_number']}]({enlace_amp}) en la carpeta `mods`.
2. Abre el juego y conectate al servidor. AutoModpack te muestra la huella del certificado del servidor: comprueba que coincide con la que publica el administrador.
3. Acepta la descarga. Nuestros mods no estan en Modrinth, asi que te pide confirmar ese riesgo aparte.
4. Reinicia el juego cuando te lo pida y vuelve a conectarte. Las proximas actualizaciones se descargan solas al abrir el juego.

## Administrador del servidor

Necesita Java 25 y Python 3.11 o mas reciente.

- **Servidor nuevo:** descomprime `DolfinServer-servidor-{version}.zip`, ejecuta `python3 scripts/install_server.py`, acepta la EULA en `runtime/server/eula.txt` y abre `runtime/server/start.bat` (o `start.sh`).
- **Actualizar:** apaga el servidor, descomprime el zip encima de la carpeta del servidor (no trae `eula.txt`, `ops.json` ni `whitelist.json`, asi que no se pisan), ejecuta `python3 scripts/install_server.py` y arranca. Si cambio la version de AutoModpack, los jugadores se actualizan solos al conectarse.

`scripts/install_server.py` descarga cada mod de Modrinth y verifica su huella. Los mods que van a los jugadores quedan en `automodpack/host-modpack/main/mods/`, y AutoModpack se los envia al conectarse.

## Mods ({pack['name']} {version}, Minecraft {minecraft}, Fabric {cargador})

| Mod | Archivo | Lado |
|---|---|---|
""" + "\n".join(filas) + "\n"
    salida = DIST / "notas.md"
    salida.write_text(texto, encoding="utf-8")
    return salida


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    version = sys.argv[1]
    pack, indice, mods = leer_pack()
    propios = mods_propios()
    amp = next(m for m in mods if m["filename"].startswith("automodpack-"))
    automodpack = pedir_json(f"https://api.modrinth.com/v2/version/{amp['update']['modrinth']['version']}")

    DIST.mkdir(exist_ok=True)
    for salida in (crear_zip_servidor(version, pack, indice, propios),
                   crear_mrpack(version, pack, automodpack),
                   crear_notas(version, pack, mods, propios, automodpack)):
        print(salida.relative_to(RAIZ))


if __name__ == "__main__":
    main()
