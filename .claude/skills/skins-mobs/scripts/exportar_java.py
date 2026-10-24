"""Convierte un modelo.json (salida de generar.py) en los metodos Java createBodyLayer() y createExteriorLayer().

createExteriorLayer() es la capa exterior: las mismas piezas infladas con CubeDeformation, que se dibujan con la
textura <clave>_exterior.png (ver CapaExterior.java en los mods).

Uso: python .claude/skills/skins-mobs/scripts/exportar_java.py runtime/skins/<clave>/modelo.json

Conversion de coordenadas (motor -> Minecraft): el motor usa y hacia arriba con los pies en y=0 y el frente en +z;
Minecraft usa y hacia abajo con los pies en y=24 y el frente en -z. Es un giro de 180 grados sobre x, asi que:
  y_mc = 24 - y,  z_mc = -z,  xRot = rx,  yRot = -ry,  zRot = -rz  (en radianes)
Minecraft rota en orden Z-Y-X y el motor en X-Z-Y: coinciden mientras ry = 0, que es lo que usan los disenos.
"""

import json
import math
import sys


def f(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return (s if s not in ("-0", "") else "0") + "F"


def main():
    with open(sys.argv[1], encoding="utf-8") as entrada:
        m = json.load(entrada)
    if any(
        abs(p["rot"][1]) > 1e-6 and (abs(p["rot"][0]) > 1e-6 or abs(p["rot"][2]) > 1e-6)
        for p in m["partes"]
    ):
        print(
            "// AVISO: hay piezas con rotacion en y combinada con x/z; revisa el orden de rotacion a mano."
        )
    capa(m, "createBodyLayer", exterior=False)
    capa(m, "createExteriorLayer", exterior=True)


def capa(m, metodo, exterior):
    tw, th = m["textura"]
    print(
        f"\t/** Generado por el skill skins-mobs desde {m['clave']}/modelo.json. No editar a mano: regenerar. */"
    )
    print(f"\tpublic static LayerDefinition {metodo}() {{")
    print("\t\tMeshDefinition malla = new MeshDefinition();")
    print("\t\tPartDefinition raiz = malla.getRoot();")
    usados = {}
    for p in m["partes"]:
        x0, y0, z0, x1, y1, z1 = p["caja"]
        px, py, pz = p["piv"]
        rx, ry, rz = (math.radians(a) for a in p["rot"])
        n = usados.get(p["r"], 0)
        usados[p["r"]] = n + 1
        # mismos nombres en las dos capas para que la animacion encuentre las mismas piezas
        nombre = p["r"] if n == 0 else f"{p['r']}_{n + 1}"
        g = p.get("inflado", 0) if exterior else 0
        cubo = (
            f".addBox({f(x0)}, {f(-y1)}, {f(-z1)}, {f(x1 - x0)}, {f(y1 - y0)}, {f(z1 - z0)}"
            + (f", new CubeDeformation({f(g)})" if g else "")
            + ")"
        )
        if exterior and not g:
            cubo = ""  # pieza de 1 px: sin capa exterior, pero queda el hueso para la animacion
        print(
            f'\t\traiz.addOrReplaceChild("{nombre}", CubeListBuilder.create().texOffs({p["uv"][0]}, {p["uv"][1]})'
            f"{cubo},"
        )
        print(
            f"\t\t\t\tPartPose.offsetAndRotation({f(px)}, {f(24 - py)}, {f(-pz)}, {f(rx)}, {f(-ry)}, {f(-rz)}));"
        )
    print(f"\t\treturn LayerDefinition.create(malla, {tw}, {th});")
    print("\t}")


if __name__ == "__main__":
    main()
