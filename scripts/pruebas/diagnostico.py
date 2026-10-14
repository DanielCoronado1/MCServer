"""Coloca sprites de prueba delante de Tester y hace una captura (para aislar problemas de render).

Uso: python scripts/pruebas/diagnostico.py
"""
import time

import banco
import rcon

COMANDOS = [
    "apocalipsis detener",
    "time set 6000",
    "kill @e[type=text_display]",
    "kill @e[type=item_display]",
    "tp Tester 0 -55 0 180 0",
    # Letra del remolino (fuente dolfin:cielo)
    'summon text_display -4 -55 -10 {text:{text:"",font:"dolfin:cielo"},background:0,'
    'brightness:{sky:15,block:15}}',
    # Grieta (item_model dolfin:grieta)
    'summon item_display 4 -55 -10 {item:{id:"paper",count:1,components:{"minecraft:item_model":"dolfin:grieta"}},'
    "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[3f,6f,1f]}}",
    # Texto normal de control
    'summon text_display 0 -52 -10 {text:{text:"control"}}',
    # Dragon quieto (como el del evento), mirando al sur (hacia Tester)
    "kill @e[type=ender_dragon]",
    "summon ender_dragon 0 -50 -30 {NoAI:1b,Silent:1b,Invulnerable:1b,Rotation:[0f,0f]}",
]

if __name__ == "__main__":
    for comando, respuesta in zip(COMANDOS, rcon.enviar(*COMANDOS)):
        print(">", comando[:60], "->", respuesta[:120])
    time.sleep(2)
    banco.foto(banco.CLIENTE / "diagnostico.jpg")
    print(banco.CLIENTE / "diagnostico.jpg")
