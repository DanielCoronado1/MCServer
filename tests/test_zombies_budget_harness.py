"""Headless tests for #9's evidence parsing, acceptance gate, and ServidorFabric harness."""

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

from tests.manual.servidor import ServidorFabric, buscar_java, verificar_instancia

PATH = Path(__file__).resolve().parent / "manual/prueba_zombies_presupuesto.py"
spec = importlib.util.spec_from_file_location("zombies_budget_harness", PATH)
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)


class BudgetHarnessTest(unittest.TestCase):
    def test_numeric_snbt_and_tick_query(self):
        self.assertEqual(
            harness.nbt_number(
                "[Server thread/INFO]: Miner has the following entity data: 24.0f\n"
            ),
            24,
        )
        self.assertAlmostEqual(
            harness.nbt_number("Miner has the following entity data: 0.207d\n"), 0.207
        )
        self.assertEqual(
            harness.mspt(
                "Target tick rate: 20 per second.\nAverage time per tick: 3.2ms (Target: 50.0ms)"
            ),
            3.2,
        )
        with self.assertRaises(AssertionError):
            harness.nbt_number("No entity was found")
        with self.assertRaises(AssertionError):
            harness.mspt("Unknown command: tick query")

    def test_budget_needs_real_complete_load_and_baseline(self):
        window = {
            "frames": 100,
            "minEntities": 25,
            "minNearPlayer": 25,
            "entityBudgetPassed": True,
        }
        self.assertTrue(harness.evaluate(1.5, 3.5, [window] * 3)["passed"])
        self.assertFalse(harness.evaluate(1.5, 3.6, [window] * 3)["passed"])
        self.assertFalse(harness.evaluate(1.5, 2, [])["passed"])
        self.assertFalse(harness.evaluate(1.5, 2, [window | {"minNearPlayer": 0}] * 3)["passed"])
        self.assertFalse(
            harness.evaluate(1.5, 2, [window | {"entityBudgetPassed": False}] * 3)["passed"]
        )

    def test_all_sixteen_kinds_and_all_five_attributes(self):
        self.assertEqual(len(set(harness.KINDS)), 16)
        self.assertEqual(
            set(harness.ATTRS),
            {"max_health", "attack_damage", "movement_speed", "armor", "scale"},
        )


class ServidorFabricTest(unittest.TestCase):
    def test_buscar_java_y_generacion_comando(self):
        self.assertEqual(buscar_java("/custom/bin/java"), "/custom/bin/java")
        servidor = ServidorFabric(
            "/tmp",
            memoria="4G",
            memoria_inicial="1G",
            jvm_args=["-Dtest=1"],
            jar="server.jar",
            java="/bin/java",
        )
        cmd = servidor._generar_comando()
        self.assertEqual(
            cmd, ["/bin/java", "-Xms1G", "-Xmx4G", "-Dtest=1", "-jar", "server.jar", "nogui"]
        )

    def test_verificar_instancia_requiere_eula_y_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            with self.assertRaises(SystemExit):
                verificar_instancia(path)
            (path / "fabric-server-launch.jar").touch()
            with self.assertRaises(SystemExit):
                verificar_instancia(path)
            (path / "eula.txt").write_text("eula=true\n")
            self.assertEqual(verificar_instancia(path), path.resolve())

    def test_ciclo_servidor_y_marcadores(self):
        script = (
            "import sys\n"
            "print('Starting mock server', flush=True)\n"
            "print('Done (1.234s)! For help, type \"help\"', flush=True)\n"
            "for line in sys.stdin:\n"
            "    line = line.strip()\n"
            "    if line == 'stop':\n"
            "        break\n"
            "    print(f'echo: {line}', flush=True)\n"
        )
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            log_path = path / "mock.log"
            checks = []
            with ServidorFabric(
                path,
                comando_arranque=[sys.executable, "-c", script],
                log=log_path,
                timeout_arranque=5.0,
                comprobaciones=checks,
            ) as s:
                self.assertIn("Done (", s.salida)
                out = s.comando_y_esperar("hola", esperado=r"echo: hola")
                self.assertIn("echo: hola", out)
                self.assertEqual(len(checks), 1)
                self.assertEqual(checks[0]["command"], "hola")
            self.assertEqual(s.codigo_salida, 0)
            self.assertTrue(log_path.is_file())
            self.assertIn("Done (", log_path.read_text())

    def test_deteccion_salida_temprana(self):
        script = "import sys; sys.exit(42)\n"
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            s = ServidorFabric(
                path,
                comando_arranque=[sys.executable, "-c", script],
                timeout_arranque=5.0,
            )
            try:
                with self.assertRaises(AssertionError) as ctx:
                    s.iniciar()
                self.assertIn(
                    "El servidor terminó prematuramente con código 42", str(ctx.exception)
                )
            finally:
                s.detener()


if __name__ == "__main__":
    unittest.main()
