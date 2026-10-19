"""Prueba sin ventanas del mod de creepers en runtime/test-server/ (puerto 25570).

Mundo nuevo en cada ejecución. Para cada variante, de una en una y en el mismo sitio:
la invoca, enciende su mecha y comprueba su explosión y efectos específicos.

Uso: python tests/manual/prueba_mod_servidor.py
"""

import shutil
from pathlib import Path

from tests.manual.servidor import ServidorFabric

RAIZ = Path(__file__).resolve().parents[2]
SERVIDOR = RAIZ / "runtime/test-server"
VARIANTES = [
    "fuego",
    "hielo",
    "saltarin",
    "mini",
    "electrico",
    "multiplicador",
    "gigante",
    "toxico",
    "rey",
]


def preparar_entorno() -> None:
    """Prepara un mundo limpio y copia el último JAR compilado del mod."""
    shutil.rmtree(SERVIDOR / "world", ignore_errors=True)
    for viejo in (SERVIDOR / "mods").glob("dolfincreepers-*.jar"):
        viejo.unlink()
    jars = list((RAIZ / "mods/creepers/build/libs").glob("dolfincreepers-*[0-9].jar"))
    if not jars:
        raise SystemExit(
            "No se encontró el JAR compilado de creepers. Compila primero con gradlew build."
        )
    jar = max(jars, key=lambda j: j.stat().st_mtime)
    (SERVIDOR / "mods").mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, SERVIDOR / "mods" / jar.name)


def main() -> None:
    preparar_entorno()
    with ServidorFabric(
        SERVIDOR,
        memoria="2G",
        log="prueba.log",
        timeout_arranque=600.0,
    ) as servidor:
        servidor.comando("forceload add 0 0", espera=1.0)
        servidor.comando("gamerule mob_griefing false")

        for variante in VARIANTES:
            tipo = f"dolfincreepers:{variante}_creeper"
            servidor.comando("kill @e[type=!player]", espera=0.5)
            servidor.comando(f"say ===== {variante}")
            servidor.comando(
                f"summon {tipo} 8 -60 8 {{ignited:1b}}", espera=4.0
            )  # 4s para detonación
            servidor.comando(f"say {variante} sigue vivo?")
            servidor.comando(f"execute if entity @e[type={tipo}]")
            if variante == "multiplicador":
                servidor.comando("say minis que solto:")
                servidor.comando("execute if entity @e[type=dolfincreepers:mini_creeper]")
            if variante == "toxico":
                servidor.comando("say nube toxica:")
                servidor.comando("execute if entity @e[type=minecraft:area_effect_cloud]")
            if variante == "rey":
                servidor.comando("say vida del rey:")
                servidor.comando(f"data get entity @e[type={tipo},limit=1] Health")

    # Diagnóstico y filtrado de eventos en chat del servidor.
    texto = servidor.salida
    inicio = texto.find("=====")
    if inicio != -1:
        print(
            "\n".join(
                linea.split("]: ", 1)[-1].replace("[Not Secure] [Server] ", "")
                for linea in texto[inicio:].splitlines()
                if "System chat" in linea or "[Server]" in linea
            )
        )
    errores = [
        linea
        for linea in servidor.lineas
        if ("ERROR" in linea or "Exception" in linea)
        and "No key layers" not in linea
        and "spark" not in linea
    ]
    print("Errores:", len(errores), *errores[:5], sep="\n")


if __name__ == "__main__":
    main()
