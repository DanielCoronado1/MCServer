"""Headless tests for #9's evidence parsing and fail-closed acceptance gate."""
import importlib.util
from pathlib import Path
import unittest

PATH = Path(__file__).resolve().parents[1] / 'scripts/pruebas/prueba_zombies.py'
spec = importlib.util.spec_from_file_location('zombies_budget_harness', PATH)
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)

class BudgetHarnessTest(unittest.TestCase):
    def test_numeric_snbt_and_tick_query(self):
        self.assertEqual(harness.nbt_number('[Server thread/INFO]: Miner has the following entity data: 24.0f\n'), 24)
        self.assertAlmostEqual(harness.nbt_number('Miner has the following entity data: 0.207d\n'), .207)
        self.assertEqual(harness.mspt('Target tick rate: 20 per second.\nAverage time per tick: 3.2ms (Target: 50.0ms)'), 3.2)
        with self.assertRaises(AssertionError): harness.nbt_number('No entity was found')
        with self.assertRaises(AssertionError): harness.mspt('Unknown command: tick query')

    def test_budget_needs_real_complete_load_and_baseline(self):
        window = {'frames': 100, 'minEntities': 25, 'minNearPlayer': 25, 'entityBudgetPassed': True}
        self.assertTrue(harness.evaluate(1.5, 3.5, [window] * 3)['passed'])
        self.assertFalse(harness.evaluate(1.5, 3.6, [window] * 3)['passed'])
        self.assertFalse(harness.evaluate(1.5, 2, [])['passed'])
        self.assertFalse(harness.evaluate(1.5, 2, [window | {'minNearPlayer': 0}] * 3)['passed'])
        self.assertFalse(harness.evaluate(1.5, 2, [window | {'entityBudgetPassed': False}] * 3)['passed'])

    def test_all_sixteen_kinds_and_all_five_attributes(self):
        self.assertEqual(len(set(harness.KINDS)), 16)
        self.assertEqual(set(harness.ATTRS), {'max_health', 'attack_damage', 'movement_speed', 'armor', 'scale'})

if __name__ == '__main__': unittest.main()
