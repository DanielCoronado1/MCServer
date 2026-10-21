"""Rutas base, lectura del packwiz, descubrimiento de jars propios y descargas HTTP."""

import json
import tomllib
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, NamedTuple

RAIZ = Path(__file__).resolve().parent.parent
PACK = RAIZ / "pack"
DIST = RAIZ / "dist"
PLANTILLAS = RAIZ / "server"
SERVIDOR_DEFECTO = RAIZ / "runtime" / "server"
CABECERAS = {"User-Agent": "DolfinServer-setup/1.0"}


class DatosPack(NamedTuple):
    """Metadatos completos de un modpack gestionado por packwiz."""

    pack: dict
    indice: dict
    mods: list[dict]

    @property
    def nombre(self) -> str:
        return self.pack.get("name", "")

    @property
    def version(self) -> str:
        return self.pack.get("version", "")

    @property
    def minecraft(self) -> str:
        return self.pack.get("versions", {}).get("minecraft", "")

    @property
    def fabric(self) -> str:
        return self.pack.get("versions", {}).get("fabric", "")

    @property
    def automodpack(self) -> dict | None:
        """Localiza el metafile del mod automodpack en el pack."""
        for mod in self.mods:
            if mod.get("filename", "").startswith("automodpack-"):
                return mod
        return None


def leer_pack(origen: Path | None = None) -> DatosPack:
    """Lee pack.toml, su archivo de índice y los metadatos de cada mod."""
    dir_pack = Path(origen) if origen is not None else PACK
    if dir_pack.is_file():
        archivo_pack = dir_pack
        dir_pack = dir_pack.parent
    else:
        archivo_pack = dir_pack / "pack.toml"

    pack = tomllib.loads(archivo_pack.read_text(encoding="utf-8"))
    archivo_indice = dir_pack / pack["index"]["file"]
    indice = tomllib.loads(archivo_indice.read_text(encoding="utf-8"))

    # Parsea cada metafile declarado en el índice
    mods = [
        tomllib.loads((dir_pack / f["file"]).read_text(encoding="utf-8"))
        for f in indice.get("files", [])
        if f.get("metafile")
    ]
    return DatosPack(pack=pack, indice=indice, mods=mods)


def mods_propios(raiz: Path | None = None) -> list[Path]:
    """Descubre jars de mods propios: custom-mods/ en release, mods/*/build/libs en repo."""
    r = Path(raiz) if raiz is not None else RAIZ
    custom = r / "custom-mods"
    if custom.is_dir():
        return sorted(custom.glob("*.jar"))

    mods_dir = r / "mods"
    if not mods_dir.is_dir():
        return []

    # Recorre proyectos Gradle bajo mods/
    jars = []
    for proyecto in sorted(mods_dir.iterdir()):
        if not (
            (proyecto / "build.gradle").is_file()
            or (proyecto / "src/main/resources/fabric.mod.json").is_file()
        ):
            continue
        jars.append(jar_mod(proyecto, raiz=r))
    return jars


def jar_mod(nombre_o_directorio: str | Path, raiz: Path | None = None) -> Path:
    """Localiza el jar compilado más reciente de un mod, excluyendo -sources y -dev."""
    r = Path(raiz) if raiz is not None else RAIZ
    carpeta = Path(nombre_o_directorio)
    if not carpeta.is_absolute():
        candidata = r / carpeta
        carpeta = candidata if candidata.is_dir() else r / "mods" / carpeta

    libs = carpeta / "build" / "libs"
    candidatos = (
        [j for j in libs.glob("*.jar") if not j.stem.endswith(("-sources", "-dev"))]
        if libs.is_dir()
        else []
    )
    if not candidatos:
        rel = carpeta.relative_to(r) if carpeta.is_relative_to(r) else carpeta
        raise FileNotFoundError(f"Compila primero {rel.as_posix()} (gradlew build)")
    return max(candidatos, key=lambda j: j.stat().st_mtime)


def metadatos_mod_jar(jar: Path) -> dict[str, Any]:
    """Extrae fabric.mod.json empaquetado en el jar del mod."""
    with zipfile.ZipFile(jar) as archivo:
        return json.loads(archivo.read("fabric.mod.json"))


def plantillas_servidor(raiz: Path | None = None) -> list[Path]:
    """Lista los archivos de plantilla bajo server/; nunca incluye estado runtime."""
    r = Path(raiz) if raiz is not None else RAIZ
    dir_plantillas = r / "server"
    if not dir_plantillas.is_dir():
        return []
    return sorted(p for p in dir_plantillas.rglob("*") if p.is_file())


def pedir_json(url: str, cabeceras: dict[str, str] | None = None, timeout: int = 120) -> Any:
    """Descarga una URL HTTP devolviendo su respuesta deserializada desde JSON."""
    return json.loads(pedir(url, cabeceras=cabeceras, timeout=timeout).decode("utf-8"))


def pedir(url: str, cabeceras: dict[str, str] | None = None, timeout: int = 120) -> bytes:
    """Descarga los bytes de una URL HTTP enviando el User-Agent del proyecto."""
    headers = dict(CABECERAS)
    if cabeceras:
        headers.update(cabeceras)
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as respuesta:
        return respuesta.read()
