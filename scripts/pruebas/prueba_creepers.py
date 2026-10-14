"""
Prueba visual de las variantes de creeper: invoca cada una delante de Tester
y hace capturas (aura y explosion).

Uso: python scripts/pruebas/prueba_creepers.py [variante ...]
"""
import sys
import time
from datetime import datetime

import banco
import rcon

VARIANTES = sys.argv[1:] or ["hielo", "electrico", "saltarin", "multiplicador", "gigante"]

if __name__ == "__main__":
    carpeta = banco.CLIENTE / "capturas" / ("creepers-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    carpeta.mkdir(parents=True)
    rcon.enviar("difficulty normal", "time set 6000", "weather clear", "gamemode survival Tester",
                "effect give Tester resistance infinite 4 true", "effect give Tester regeneration infinite 4 true",
                "kill @e[type=creeper]")
    for numero, variante in enumerate(VARIANTES, start=1):
        # Cada prueba en un sitio distinto, sobre agua para ver el hielo.
        x = numero * 40
        rcon.enviar(f"fill {x - 6} -61 {x - 14} {x + 6} -61 {x - 2} water",
                    f"tp Tester {x} -60 {x} 180 10")
        time.sleep(1.5)
        print(rcon.enviar(f"creeper invocar {variante} 1 Tester")[0].replace("§", "").strip())
        time.sleep(1.2)
        banco.foto(carpeta / f"{numero}a_{variante}_aura.jpg")
        # Esperamos a que se acerque y explote, haciendo fotos seguidas.
        for i in range(10):
            time.sleep(0.6)
            banco.foto(carpeta / f"{numero}b_{variante}_{i:02d}.jpg")
        rcon.enviar("kill @e[type=creeper]")
    print(carpeta)
