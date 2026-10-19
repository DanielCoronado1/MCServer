"""Crea los archivos de una release en dist/:

  DolfinServer-servidor-<version>.zip   para el administrador: plantillas server/, docs y deploy/,
                                        pack/ (packwiz), nuestros mods y scripts/install_server.py, que
                                        descarga el resto de mods de Modrinth.
  DolfinServer-<version>.mrpack         para los jugadores: Fabric + AutoModpack. El resto
                                        de mods se lo envia el servidor al conectarse.
  notas.md                              texto de la release (instrucciones y lista de mods)

Con --space crea el bundle independiente de DolfinSpace:
  DolfinSpace-<version>.zip             jar del mod de espacio, manifiesto y documentacion.

No incluye mods de terceros dentro de los archivos (el repositorio es publico): solo sus
enlaces de descarga y huellas.

Uso:  python scripts/build_release.py <version> [--space]
"""

import hashlib
import json
import sys
import zipfile
from pathlib import Path

import proyecto

RAIZ = Path(__file__).resolve().parent.parent
PACK = RAIZ / "pack"
DIST = RAIZ / "dist"
CABECERAS = proyecto.CABECERAS


def crear_zip_servidor(version, pack, indice, propios):
    carpeta = f"DolfinServer-servidor-{version}"
    salida = DIST / f"{carpeta}.zip"
    escritos = set()

    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as zip_:

        def escribir(origen, destino):
            if destino not in escritos and origen.is_file():
                escritos.add(destino)
                zip_.write(origen, destino)

        for ruta in archivos_del_servidor():
            escribir(ruta, f"{carpeta}/{ruta.relative_to(RAIZ).as_posix()}")
        escribir(PACK / "custom-mods.toml", f"{carpeta}/pack/custom-mods.toml")
        escribir(RAIZ / "CREDITS.md", f"{carpeta}/CREDITS.md")
        escribir(PACK / "pack.toml", f"{carpeta}/pack/pack.toml")
        escribir(PACK / pack["index"]["file"], f"{carpeta}/pack/{pack['index']['file']}")
        for archivo in indice["files"]:
            escribir(PACK / archivo["file"], f"{carpeta}/pack/{archivo['file']}")
        for jar in propios:
            escribir(jar, f"{carpeta}/custom-mods/{jar.name}")
        escribir(RAIZ / "scripts" / "install_server.py", f"{carpeta}/scripts/install_server.py")
        escribir(RAIZ / "scripts" / "proyecto.py", f"{carpeta}/scripts/proyecto.py")
        for ruta in [
            RAIZ / "README.md",
            *sorted((RAIZ / "docs").rglob("*.md")),
            *sorted(
                p
                for p in (RAIZ / "deploy").rglob("*")
                if p.suffix in {".yaml", ".py", ".ps1", ".sh"}
            ),
        ]:
            escribir(ruta, f"{carpeta}/{ruta.relative_to(RAIZ).as_posix()}")
    return salida


def crear_bundle_space(version, raiz=None):
    r = Path(raiz) if raiz is not None else RAIZ
    jar = proyecto.jar_mod("space", raiz=r)
    metadata = proyecto.metadatos_mod_jar(jar)
    if metadata.get("id") != "dolfinspace":
        raise ValueError(f"El jar {jar.name} no contiene el mod dolfinspace")

    pack, _, mods = proyecto.leer_pack(r / "pack")
    manifest = {
        "version": version,
        "mod": metadata,
        "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "pack": pack,
        "dependencies": mods,
        "clientAcceptance": "pending clean-client and multiplayer acceptance; see docs/espacio/PRUEBAS.md",
    }
    salida = (r / "dist") / f"DolfinSpace-{version}.zip"
    salida.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(jar, f"custom-mods/{jar.name}")
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        for p in [
            r / "CREDITS.md",
            *sorted((r / "pack").rglob("*.toml")),
            *sorted((r / "docs/space").glob("*")),
            *sorted((r / "docs/espacio").glob("*")),
        ]:
            if p.is_file():
                z.write(p, p.relative_to(r).as_posix())
    return salida


def crear_mrpack(version, pack, automodpack):
    archivo = next(f for f in automodpack["files"] if f["primary"])
    indice = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": pack["name"],
        "summary": f"Fabric {pack['versions']['minecraft']} + AutoModpack: el servidor te envia el resto de mods",
        "files": [
            {
                "path": f"mods/{archivo['filename']}",
                "hashes": {
                    "sha1": archivo["hashes"]["sha1"],
                    "sha512": archivo["hashes"]["sha512"],
                },
                "env": {"client": "required", "server": "unsupported"},
                "downloads": [archivo["url"]],
                "fileSize": archivo["size"],
            }
        ],
        "dependencies": {
            "minecraft": pack["versions"]["minecraft"],
            "fabric-loader": pack["versions"]["fabric"],
        },
    }
    salida = DIST / f"{pack['name']}-{version}.mrpack"
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as mrpack:
        mrpack.writestr("modrinth.index.json", json.dumps(indice, indent=2))
    return salida


def crear_notas(version, pack, mods, propios, automodpack):
    minecraft, cargador = pack["versions"]["minecraft"], pack["versions"]["fabric"]
    enlace_amp = f"https://modrinth.com/mod/automodpack/version/{automodpack['id']}"
    lado = {"server": "solo servidor", "client": "solo jugadores", "both": "servidor y jugadores"}
    filas = [
        f"| {m['name']} | `{m['filename']}` | {lado[m['side']]} |"
        for m in sorted(mods, key=lambda m: m["name"].lower())
    ]
    filas += [f"| (nuestro) | `{jar.name}` | servidor y jugadores |" for jar in propios]
    texto = (
        f"""## Jugadores

1. Descarga `{pack["name"]}-{version}.mrpack` e importalo en **Prism Launcher** o **Modrinth App** (Importar > archivo). Instala Minecraft {minecraft}, Fabric {cargador} y AutoModpack.
   - Con otro launcher: instala Fabric {cargador} para Minecraft {minecraft} y pon [AutoModpack {automodpack["version_number"]}]({enlace_amp}) en la carpeta `mods`.
2. Abre el juego y conectate al servidor. AutoModpack te muestra la huella del certificado del servidor: comprueba que coincide con la que publica el administrador.
3. Acepta la descarga. Nuestros mods no estan en Modrinth, asi que te pide confirmar ese riesgo aparte.
4. Reinicia el juego cuando te lo pida y vuelve a conectarte. Las proximas actualizaciones se descargan solas al abrir el juego.

## Administrador del servidor

Necesita Java 25 y Python 3.11 o mas reciente.

- **Servidor nuevo:** descomprime `DolfinServer-servidor-{version}.zip`, ejecuta `python3 scripts/install_server.py`, acepta la EULA en `runtime/server/eula.txt` y abre `runtime/server/start.bat` (o `start.sh`).
- **Actualizar:** apaga el servidor, descomprime el zip encima de la carpeta del servidor (no trae `eula.txt`, `ops.json` ni `whitelist.json`, asi que no se pisan), ejecuta `python3 scripts/install_server.py` y arranca. Si cambio la version de AutoModpack, los jugadores se actualizan solos al conectarse.

`scripts/install_server.py` descarga cada mod de Modrinth y verifica su huella. Los mods que van a los jugadores quedan en `automodpack/host-modpack/main/mods/`, y AutoModpack se los envia al conectarse.

## Mods ({pack["name"]} {version}, Minecraft {minecraft}, Fabric {cargador})

| Mod | Archivo | Lado |
|---|---|---|
"""
        + "\n".join(filas)
        + "\n"
    )
    salida = DIST / "notas.md"
    salida.write_text(texto, encoding="utf-8")
    return salida


def archivos_del_servidor():
    """Plantillas del servidor versionadas; delega en el módulo de proyecto."""
    return proyecto.plantillas_servidor(RAIZ)


def pedir_json(url):
    """Consulta JSON HTTP utilizando User-Agent y timeout del proyecto."""
    return proyecto.pedir_json(url)


def leer_pack():
    """Lee pack.toml, índice y metafiles desde pack/."""
    return proyecto.leer_pack(PACK)


def mods_propios():
    """Descubre jars de mods propios compilados en mods/*/build/libs."""
    return proyecto.mods_propios(RAIZ)


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if "-h" in args or "--help" in args:
        sys.exit(__doc__)
    es_space = "--space" in args
    if es_space:
        args.remove("--space")
    if len(args) != 1:
        sys.exit(__doc__)
    version = args[0]

    DIST.mkdir(exist_ok=True)
    if es_space:
        salida = crear_bundle_space(version)
        print(salida.relative_to(RAIZ))
        return

    pack, indice, mods = leer_pack()
    propios = mods_propios()
    amp = next(m for m in mods if m["filename"].startswith("automodpack-"))
    automodpack = pedir_json(
        f"https://api.modrinth.com/v2/version/{amp['update']['modrinth']['version']}"
    )

    for salida in (
        crear_zip_servidor(version, pack, indice, propios),
        crear_mrpack(version, pack, automodpack),
        crear_notas(version, pack, mods, propios, automodpack),
    ):
        print(salida.relative_to(RAIZ))


if __name__ == "__main__":
    main()
