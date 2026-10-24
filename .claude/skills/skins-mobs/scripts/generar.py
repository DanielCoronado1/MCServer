"""Genera textura, lamina de revision, modelo JSON y el visor 3D de los disenos de mobs.

Uso (desde la raiz del repo):
    python .claude/skills/skins-mobs/scripts/generar.py                 # todos los disenos
    python .claude/skills/skins-mobs/scripts/generar.py zombie_fungico  # solo algunos (por clave o archivo)
Opciones: --mobs <carpeta de disenos> (tools/skins/mobs)  --out <carpeta de salida> (runtime/skins, ignorada por git)
"""

import argparse
import base64
import importlib.util
import io
import json
import os
import sys
import unicodedata

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
import motor  # noqa: E402


def cargar(ruta):
    nombre = os.path.splitext(os.path.basename(ruta))[0]
    spec = importlib.util.spec_from_file_location("mob_" + nombre, ruta)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def b64(im):
    buf = io.BytesIO()
    im.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def sin_tildes(s):
    # la fuente por defecto de Pillow no trae tildes
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "solo", nargs="*", help="claves o archivos de disenos a generar (vacio = todos)"
    )
    ap.add_argument("--mobs", default="tools/skins/mobs")
    ap.add_argument("--out", default="runtime/skins")
    a = ap.parse_args()
    # Ayudantes compartidos: archivos que empiezan con "_".
    sys.path.insert(0, os.path.abspath(a.mobs))

    archivos = sorted(f for f in os.listdir(a.mobs) if f.endswith(".py") and not f.startswith("_"))
    datos = {}
    for f in archivos:
        mod = cargar(os.path.join(a.mobs, f))
        clave = mod.MOB["clave"]
        tex, modelo = motor.construir(mod)
        datos[clave] = dict(
            nombre=mod.MOB["nombre"],
            grupo=mod.MOB.get("grupo", "Otros"),
            escala=mod.MOB.get("escala", 1.0),
            tex=b64(tex),
            tw=tex.size[0],
            th=tex.size[1],
            partes=modelo,
        )
        if a.solo and clave not in a.solo and os.path.splitext(f)[0] not in a.solo:
            continue
        carpeta = os.path.join(a.out, clave)
        os.makedirs(carpeta, exist_ok=True)
        tex.save(os.path.join(carpeta, clave + ".png"))
        motor.lamina(
            sin_tildes(mod.MOB["nombre"]), modelo, tex, os.path.join(carpeta, "preview.png")
        )
        with open(os.path.join(carpeta, "modelo.json"), "w", encoding="utf-8") as salida:
            json.dump(
                dict(
                    clave=clave,
                    nombre=mod.MOB["nombre"],
                    escala=mod.MOB.get("escala", 1.0),
                    textura=[tex.size[0], tex.size[1]],
                    partes=modelo,
                ),
                salida,
                indent=1,
                ensure_ascii=False,
            )
        print(f"{clave}: {carpeta}")

    with open(os.path.join(AQUI, "visor_plantilla.html"), encoding="utf-8") as entrada:
        plantilla = entrada.read()
    visor = os.path.join(a.out, "visor.html")
    with open(visor, "w", encoding="utf-8") as salida:
        salida.write(plantilla.replace("__DATOS__", json.dumps(datos)))
    print(f"visor: {visor} ({len(datos)} mobs)")


if __name__ == "__main__":
    main()
