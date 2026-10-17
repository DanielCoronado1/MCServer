@echo off
REM ============================================================
REM  DolfinServer - servidor Fabric 26.3 (con reinicio automatico)
REM  -Xms/-Xmx: memoria para el servidor (10 GB es suficiente
REM  para 10-25 jugadores; subir mas no mejora y puede dar tirones)
REM  El resto son los ajustes de memoria recomendados para servidores.
REM
REM  Cuando el servidor se apaga (reinicio diario o "stop") vuelve a
REM  arrancar solo a los 10 segundos. Para apagarlo del todo, pulsa
REM  Ctrl+C durante esa cuenta atras (o cierra esta ventana).
REM ============================================================
cd /d "%~dp0"
title DolfinServer (Fabric)

:arrancar
java -Xms10G -Xmx10G ^
 -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled ^
 -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC ^
 -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 ^
 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 ^
 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 ^
 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 ^
 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 ^
 -jar fabric-server-launch.jar nogui

echo.
echo El servidor se detuvo. Vuelve a arrancar en 10 segundos...
echo (pulsa Ctrl+C para no reiniciarlo)
timeout /t 10
goto arrancar
