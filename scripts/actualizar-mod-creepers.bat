@echo off
REM Compila el mod de creepers y lo instala en el servidor Fabric (junto con
REM los mods de pack/). AutoModpack se lo envia a los jugadores al conectarse.
REM ANTES: apaga el servidor (Ctrl+C en su cuenta atras).
cd /d "%~dp0..\mods-src\creepers"
call gradlew.bat build || (pause & exit /b 1)
cd /d "%~dp0.."
python scripts\instalar_servidor.py || (pause & exit /b 1)
echo.
echo Listo. Ya puedes abrir el servidor (server-fabric\iniciar.bat).
pause
