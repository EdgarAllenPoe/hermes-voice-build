"""Host-only checks for the configured source and binary verification helpers."""
import importlib.util
import contextlib
import io
import os
import sys
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('build_binaries', ROOT / 'tools/build_binaries.py')
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)

class BuildPackageTests(unittest.TestCase):
    def test_endpoint_configuration(self):
        cfg = json.loads((ROOT / 'config/build-targets.json').read_text())
        text = (ROOT / 'android/app/src/main/java/org/tomstout/hermesvoice/Settings.java').read_text()
        self.assertEqual(cfg['endpoint'], 'http://100.99.200.55:8765/v1/voice')
        self.assertIn(cfg['endpoint'], text)
        self.assertEqual(json.loads((ROOT / 'receiver/config.tomstout.json').read_text())['bind'], '100.99.200.55')

    def test_exact_board(self):
        cfg = json.loads((ROOT / 'config/build-targets.json').read_text())
        self.assertEqual(cfg['platformio_environment'], BUILDER.BOARD)
        self.assertIn('board = ' + BUILDER.BOARD, (ROOT / 'firmware/platformio.ini').read_text())
        self.assertEqual(cfg['zephyr_board'], 'xiao_nrf54lm20a/nrf54lm20a/cpuapp')

    def test_hex_checksums(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'fixture.hex'
            p.write_text(':0400000001020304F2\n:00000001FF\n')
            self.assertEqual(BUILDER.verify_hex(p), 4)
            p.write_text(':0400000001020304F1\n:00000001FF\n')
            with self.assertRaises(ValueError): BUILDER.verify_hex(p)

    def test_hex_needs_program_and_eof(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'fixture.hex'
            for contents in (':00000001FF\n', ':0400000001020304F2\n', 'not firmware'):
                p.write_text(contents)
                with self.assertRaises(ValueError): BUILDER.verify_hex(p)

    def test_elf_needs_arm(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'fixture.elf'
            header = bytearray(52); header[:6] = b'\x7fELF\x01\x01'; header[18] = 40
            p.write_bytes(header); BUILDER.verify_elf(p)
            header[18] = 62; p.write_bytes(header)
            with self.assertRaises(ValueError): BUILDER.verify_elf(p)

    def test_renamed_source_zip_is_not_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'fixture.apk'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr('AndroidManifest.xml', '<manifest/>')
                z.writestr('classes.dex', 'source code is not DEX')
            with self.assertRaises(ValueError): BUILDER.verify_apk_structure(p)


    def test_build_logs_preserve_unicode_with_legacy_child_encoding(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            log = Path(tmp)/'unicode.txt'
            env = dict(os.environ, PYTHONIOENCODING='ascii')
            BUILDER.run([sys.executable, '-c', 'print(chr(0x2514))'], log, env)
            self.assertEqual(log.read_text(encoding='utf-8'), chr(0x2514)+'\n')

    @unittest.skipUnless(os.name == 'nt', 'Windows Git path handling')
    def test_vendor_git_commands_get_long_path_support(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()):
            log = Path(tmp)/'git.txt'
            BUILDER.run(['git', 'config', '--get', 'core.longpaths'], log)
            self.assertEqual(log.read_text(encoding='utf-8').strip(), 'true')
