"""Arranca el servidor Fabric, ejecuta comandos y guarda el log de comprobación.

Uso: python tests/manual/arranque_fabric.py ["comando" ...]
"""

import sys
from pathlib import Path

from tests.manual.servidor import ServidorFabric

SERVIDOR = Path(__file__).resolve().parents[2] / "runtime" / "server"


def main() -> None:
    # Arranca la instancia real con 4 GB y vuelca el log directamente en el archivo esperado.
    with ServidorFabric(
        SERVIDOR,
        memoria="4G",
        log="prueba_arranque.log",
        timeout_arranque=600.0,
    ) as servidor:
        for cmd in sys.argv[1:]:
            servidor.comando(cmd, espera=2.0)

    # Imprime resumen de eventos relevantes y errores registrados durante el arranque.
    print(
        "".join(
            linea
            for linea in servidor.lineas
            if any(p in linea for p in ("ERROR", "WARN", "Exception", "Done (", "Loading", "mods"))
        )
    )


if __name__ == "__main__":
    main()
