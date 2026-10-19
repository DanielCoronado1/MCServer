"""Arnés unificado para arranque, control y comprobación del servidor Fabric de pruebas."""

from __future__ import annotations

import contextlib
import os
import queue
import re
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import IO


def buscar_java(ruta: str | Path | None = None) -> str:
    """Resuelve la ruta al binario de Java según argumento, JAVA_HOME o PATH."""
    if ruta:
        return str(ruta)
    if "JAVA_HOME" in os.environ:
        binario = "java.exe" if sys.platform == "win32" or os.name == "nt" else "java"
        return str(Path(os.environ["JAVA_HOME"]) / "bin" / binario)
    return "java"


def verificar_instancia(
    directorio: Path | str,
    jar: str = "fabric-server-launch.jar",
) -> Path:
    """Comprueba que el directorio exista, contenga el JAR de Fabric y el EULA esté aceptado."""
    raiz = Path(directorio).resolve()
    if not raiz.is_dir():
        raise SystemExit(f"No existe el directorio de servidor: {raiz}")
    if jar and not (raiz / jar).is_file():
        raise SystemExit(f"Falta el lanzador Fabric ({jar}) en {raiz}")
    eula = raiz / "eula.txt"
    if not eula.is_file() or not re.search(r"^eula=true\s*$", eula.read_text(), re.M):
        raise SystemExit(f"Acepta el EULA de Minecraft en {eula} antes de ejecutar las pruebas.")
    return raiz


class ServidorFabric:
    """Gestiona el ciclo de vida de un servidor Fabric para pruebas de integración.

    Permite arrancar el servidor con parámetros de memoria y JVM configurables,
    captura el log en tiempo real, detecta fallos prematuros, sincroniza comandos
    mediante marcadores de consola y asegura el apagado limpio en un bloque with.
    """

    def __init__(
        self,
        directorio: Path | str,
        *,
        memoria: str = "2G",
        memoria_inicial: str | None = None,
        java: str | Path | None = None,
        jar: str = "fabric-server-launch.jar",
        log: Path | str | None = None,
        jvm_args: list[str] | tuple[str, ...] = (),
        comando_arranque: list[str] | None = None,
        cwd: Path | str | None = None,
        patron_listo: str = r"Done \(",
        timeout_arranque: float = 180.0,
        prefijo_marcador: str = "FABRIC",
        comprobaciones: list[dict] | None = None,
        crear_grupo_proceso: bool = False,
    ) -> None:
        self.directorio = Path(directorio).resolve()
        self.cwd = Path(cwd).resolve() if cwd else self.directorio
        self.java = buscar_java(java)
        self.memoria = memoria
        self.memoria_inicial = memoria_inicial or memoria
        self.jar = jar
        self.jvm_args = list(jvm_args)
        self.comando_arranque = list(comando_arranque) if comando_arranque is not None else None
        self.patron_listo = patron_listo
        self.timeout_arranque = timeout_arranque
        self.prefijo_marcador = prefijo_marcador
        self.crear_grupo_proceso = crear_grupo_proceso

        self.comprobaciones: list[dict] = comprobaciones if comprobaciones is not None else []
        self.lineas: list[str] = []
        self._cola: queue.Queue[str] = queue.Queue()
        self._contador_marcador = 0
        self._archivo_log: IO[str] | None = None
        self._hilo_lector: threading.Thread | None = None
        self.proceso: subprocess.Popen[str] | None = None

        if log:
            ruta_log = Path(log)
            self.ruta_log = ruta_log if ruta_log.is_absolute() else self.directorio / ruta_log
        else:
            self.ruta_log = None

    def __enter__(self) -> ServidorFabric:
        return self.iniciar()

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.detener()

    @property
    def salida(self) -> str:
        """Todo el texto de consola acumulado desde el inicio."""
        return "".join(self.lineas)

    @property
    def codigo_salida(self) -> int | None:
        """Código de salida del subproceso si ya finalizó."""
        return self.proceso.returncode if self.proceso else None

    def iniciar(self) -> ServidorFabric:
        """Lanza el subproceso del servidor y aguarda la señal de preparación."""
        if self.proceso is not None and self.proceso.poll() is None:
            return self

        # Apertura del archivo de transcripción si fue configurado.
        if self.ruta_log:
            self.ruta_log.parent.mkdir(parents=True, exist_ok=True)
            self._archivo_log = self.ruta_log.open("w", encoding="utf-8", errors="replace")

        comando = self._generar_comando()
        flags = 0
        if sys.platform == "win32":
            flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)

        # Inicio del subproceso y el lector de consola en segundo plano.
        self.proceso = subprocess.Popen(
            comando,
            cwd=self.cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
            creationflags=flags,
            start_new_session=self.crear_grupo_proceso and sys.platform != "win32",
        )

        self._hilo_lector = threading.Thread(target=self._leer_consola, daemon=True)
        self._hilo_lector.start()

        if self.patron_listo:
            self.esperar(self.patron_listo, timeout=self.timeout_arranque)
        return self

    def esperar(self, patron: str | re.Pattern, timeout: float = 30.0) -> str:
        """Espera a que una expresión regular aparezca en la consola del servidor.

        Detecta si el proceso muere prematuramente y genera errores informativos
        con el código de salida y la ruta de evidencia.
        """
        limite = time.monotonic() + timeout
        acumulado: list[str] = []
        regex = re.compile(patron) if isinstance(patron, str) else patron

        while time.monotonic() < limite:
            if self.proceso is not None and self.proceso.poll() is not None and self._cola.empty():
                info_log = f"; ver {self.ruta_log}" if self.ruta_log else ""
                raise AssertionError(
                    f"El servidor terminó prematuramente con código {self.proceso.returncode}; "
                    f"esperando {regex.pattern!r}{info_log}"
                )
            try:
                linea = self._cola.get(timeout=0.2)
                acumulado.append(linea)
                texto = "".join(acumulado)
                if regex.search(texto):
                    return texto
            except queue.Empty:
                pass

        info_log = f"; ver {self.ruta_log}" if self.ruta_log else ""
        cola = "".join(self.lineas[-40:]) if self.lineas else ""
        raise AssertionError(
            f"Tiempo de espera agotado ({timeout}s) esperando {regex.pattern!r}{info_log};\n"
            f"Últimas líneas recibidas:\n{cola}"
        )

    def comando(self, texto: str, espera: float = 0.0) -> None:
        """Envía una orden al servidor por stdin y espera el tiempo indicado."""
        if self.proceso is None or self.proceso.stdin is None or self.proceso.poll() is not None:
            raise RuntimeError("El servidor no está activo para recibir comandos")
        self.proceso.stdin.write(texto + "\n")
        self.proceso.stdin.flush()
        if espera > 0:
            time.sleep(espera)

    def comando_y_esperar(
        self, texto: str, esperado: str | None = None, timeout: float = 30.0
    ) -> str:
        """Ejecuta un comando con barrera de sincronización say y valida la respuesta."""
        marcador = f"__{self.prefijo_marcador}_{self._contador_marcador}__"
        self._contador_marcador += 1

        if self.proceso is None or self.proceso.stdin is None or self.proceso.poll() is not None:
            raise RuntimeError("El servidor no está activo para recibir comandos")

        self.proceso.stdin.write(f"{texto}\nsay {marcador}\n")
        self.proceso.stdin.flush()

        salida = self.esperar(re.escape(marcador), timeout=timeout)
        registro = {"command": texto, "expected": esperado, "output": salida}
        self.comprobaciones.append(registro)

        if esperado is not None and not re.search(esperado, salida):
            raise AssertionError(f"Comando {texto!r}: se esperaba {esperado!r}; salida:\n{salida}")
        return salida

    def detener(self, timeout: float = 60.0) -> int:
        """Detiene el servidor ordenadamente con 'stop' o forzando la terminación."""
        if self.proceso is None:
            return 0

        # Intento de detención ordenada con el comando estándar stop.
        if self.proceso.poll() is None:
            try:
                if self.proceso.stdin and not self.proceso.stdin.closed:
                    self.proceso.stdin.write("stop\n")
                    self.proceso.stdin.flush()
                self.proceso.wait(timeout=timeout)
            except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
                self._forzar_cierre()

        if self._hilo_lector and self._hilo_lector.is_alive():
            self._hilo_lector.join(timeout=5.0)

        if self.proceso.stdin and not self.proceso.stdin.closed:
            with contextlib.suppress(OSError):
                self.proceso.stdin.close()
        if self.proceso.stdout and not self.proceso.stdout.closed:
            with contextlib.suppress(OSError):
                self.proceso.stdout.close()

        if self._archivo_log and not self._archivo_log.closed:
            self._archivo_log.close()

        return self.proceso.returncode if self.proceso.returncode is not None else -1

    def _generar_comando(self) -> list[str]:
        if self.comando_arranque is not None:
            return list(self.comando_arranque)
        cmd = [
            self.java,
            f"-Xms{self.memoria_inicial}",
            f"-Xmx{self.memoria}",
        ]
        cmd.extend(self.jvm_args)
        cmd.extend(["-jar", self.jar, "nogui"])
        return cmd

    def _leer_consola(self) -> None:
        if self.proceso is None or self.proceso.stdout is None:
            return
        with contextlib.suppress(ValueError, OSError):
            for linea in self.proceso.stdout:
                self.lineas.append(linea)
                if self._archivo_log:
                    self._archivo_log.write(linea)
                    self._archivo_log.flush()
                self._cola.put(linea)

    def _forzar_cierre(self) -> None:
        if self.proceso is None or self.proceso.poll() is not None:
            return
        try:
            if self.crear_grupo_proceso and sys.platform != "win32":
                import signal

                os.killpg(self.proceso.pid, signal.SIGTERM)
                self.proceso.wait(timeout=10.0)
            else:
                self.proceso.kill()
                self.proceso.wait(timeout=10.0)
        except (OSError, subprocess.TimeoutExpired):
            with contextlib.suppress(OSError):
                self.proceso.kill()
