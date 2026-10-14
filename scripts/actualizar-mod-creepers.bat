@echo off
REM Instala la ultima version compilada del mod de creepers en el servidor
REM Fabric y en tu Minecraft (TLauncher, carpeta .minecraft\mods).
REM ANTES: cierra Minecraft y apaga el servidor (Ctrl+C en su cuenta atras).
cd /d "%~dp0.."

set NUEVO=
for %%f in (modpack\mods-para-jugadores\dolfincreepers-*.jar) do set NUEVO=%%f
if "%NUEVO%"=="" (
  echo No encuentro el mod en modpack\mods-para-jugadores
  pause
  exit /b 1
)

for %%d in ("server-fabric\mods" "%APPDATA%\.minecraft\mods") do (
  del /q "%%~d\dolfincreepers-*.jar" 2>nul
  if exist "%%~d\dolfincreepers-*.jar" (
    echo No se pudo borrar la version vieja en %%~d: cierra Minecraft y el servidor.
    pause
    exit /b 1
  )
  copy /y "%NUEVO%" "%%~d\" >nul
  echo Actualizado: %%~d
)
echo.
echo Listo. Ya puedes abrir el servidor (server-fabric\iniciar.bat) y Minecraft.
pause
