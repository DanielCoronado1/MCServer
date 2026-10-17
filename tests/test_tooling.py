"""Exercise installation and release layout without network or a running Minecraft."""
import hashlib
import importlib.util
import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


def load_script(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ToolingTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for directory in ('server', 'pack', 'scripts', 'docs', 'deploy'):
            shutil.copytree(ROOT / directory, self.root / directory)
        shutil.copy2(ROOT / 'README.md', self.root / 'README.md')
        self.installer = load_script('installer', self.root / 'scripts/install_server.py')
        self.release = load_script('release', self.root / 'scripts/build_release.py')
        self.installer.preparar_servidor()

    def test_templates_preserve_live_configuration_and_eula(self):
        runtime = self.installer.SERVIDOR
        self.assertIn('eula=false', (runtime / 'eula.txt').read_text())
        (runtime / 'server.properties').write_text('online-mode=false\nmotd=local\n')
        (runtime / 'eula.txt').write_text('eula=true\n')
        self.installer.preparar_servidor()
        self.assertIn('motd=local', (runtime / 'server.properties').read_text())
        self.assertEqual('eula=true\n', (runtime / 'eula.txt').read_text())
        self.assertFalse((runtime / 'whitelist.txt').exists())

    def test_whitelist_merge_is_idempotent_and_preserves_existing_players(self):
        runtime = self.installer.SERVIDOR
        existing = {'name': 'a', 'uuid': '52428a0e-1e30-3cb1-976c-e728b2614047'}
        (runtime / 'whitelist.json').write_text(json.dumps([existing]))
        self.installer.sincronizar_whitelist()
        first = (runtime / 'whitelist.json').read_text()
        self.installer.sincronizar_whitelist()
        self.assertEqual(first, (runtime / 'whitelist.json').read_text())
        entries = json.loads(first)
        self.assertIn(existing, entries)
        self.assertEqual({'a', 'emi2k01', 'emi0x7d1', 'reginatoor'}, {e['name'] for e in entries})
        # Known Java offline UUID for the manual test player.
        (self.installer.PLANTILLAS / 'whitelist.txt').write_text('Tester\n')
        self.installer.sincronizar_whitelist()
        self.assertIn({'name': 'Tester', 'uuid': 'f3d28cb0-7225-3cb1-baeb-2dadd2be89ae'},
                      json.loads((runtime / 'whitelist.json').read_text()))

    def test_online_mode_requires_resolved_identity_without_partial_write(self):
        runtime = self.installer.SERVIDOR
        (runtime / 'server.properties').write_text('online-mode=true\n')
        (runtime / 'whitelist.json').write_text('[]\n')
        with self.assertRaisesRegex(ValueError, 'whitelist add'):
            self.installer.sincronizar_whitelist()
        self.assertEqual('[]\n', (runtime / 'whitelist.json').read_text())

    def test_download_hash_failure_does_not_replace_existing_file(self):
        target = self.installer.SERVIDOR / 'mods/test.jar'
        target.parent.mkdir()
        target.write_bytes(b'previous jar')
        mod = {'filename': 'test.jar', 'download': {
            'hash-format': 'sha512', 'hash': hashlib.sha512(b'expected').hexdigest(), 'url': 'https://example.invalid/mod'}}
        with patch.object(self.installer, 'pedir', return_value=b'corrupt'):
            with self.assertRaisesRegex(ValueError, 'huella'):
                self.installer.instalar_mod(mod, target)
        self.assertEqual(b'previous jar', target.read_bytes())

    def test_release_roundtrip_installs_both_sides_and_keeps_manual_jars(self):
        custom = self.root / 'fixture-custom.jar'
        custom.write_bytes(b'custom mod fixture')
        self.release.DIST.mkdir()
        pack, index, _ = self.release.leer_pack()
        archive = self.release.crear_zip_servidor('test', pack, index, [custom])
        extracted = self.root / 'extracted'
        with zipfile.ZipFile(archive) as zipped:
            self.assertIsNone(zipped.testzip())
            names = zipped.namelist()
            self.assertTrue(any(n.endswith('deploy/crafty/compose.yaml') for n in names))
            self.assertTrue(any(n.endswith('docs/crafty.md') for n in names))
            self.assertTrue(any(n.endswith('deploy/windows/install-startup.ps1') for n in names))
            self.assertTrue(any(n.endswith('deploy/crafty/configure_server.py') for n in names))
            self.assertFalse(any('/runtime/' in n or n.endswith(('ops.json', 'whitelist.json', 'eula.txt')) for n in names))
            zipped.extractall(extracted)
        release_root = extracted / 'DolfinServer-servidor-test'
        installer = load_script('extracted_installer', release_root / 'scripts/install_server.py')
        data = b'mod fixture'
        fixtures = [dict(name=side, filename=f'{side}.jar', side=side, download={
            'url': f'https://example.invalid/{side}', 'hash-format': 'sha512',
            'hash': hashlib.sha512(data).hexdigest()}) for side in ('server', 'client', 'both')]
        installer.preparar_servidor()
        mods = installer.SERVIDOR / 'mods'
        mods.mkdir()
        (mods / 'old.jar').write_bytes(b'old managed jar')
        (mods / 'manual.jar').write_bytes(b'operator jar')
        installer.REGISTRO.write_text(json.dumps({'archivos': ['mods/old.jar']}))
        with patch.object(installer, 'leer_pack', return_value=(pack, fixtures)), patch.object(installer, 'pedir', return_value=data):
            installer.main()
        self.assertEqual({'manual.jar', 'server.jar', 'both.jar', custom.name}, {p.name for p in mods.iterdir()})
        client = installer.SERVIDOR / installer.MODS_JUGADOR
        self.assertEqual({'client.jar', 'both.jar', custom.name}, {p.name for p in client.iterdir()})
        self.assertEqual(3, len(json.loads((installer.SERVIDOR / 'whitelist.json').read_text())))
        self.assertTrue((installer.SERVIDOR / 'fabric-server-launch.jar').exists())


if __name__ == '__main__':
    unittest.main()
