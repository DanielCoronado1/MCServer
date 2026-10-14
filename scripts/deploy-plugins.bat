@echo off
REM Compila todos los plugins y los copia a server\plugins.
REM Despues reinicia el servidor (o escribe "stop" y vuelve a abrir start.bat).
cd /d "%~dp0..\plugins-src"
call gradlew.bat deploy
pause
