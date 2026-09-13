"""Tests of the narrow patch helper using a fake installed-package directory.
These do not execute SCons, a target compiler, or hardware.
"""
import contextlib
import io
import json
from pathlib import Path
import runpy
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'firmware/patch_pio_zephyr.py'
OLD = 'env.Append(LIBS=lib_dep["lib_path"])'
NEW = 'env.Append(LIBS=[lib_dep["lib_path"]])'

class Environment:
    def __init__(self, path=None, clean=False):
        self.path = path
        self.clean = clean
    def IsCleanTarget(self):
        return self.clean
    def PioPlatform(self):
        if self.clean:
            raise AssertionError('Clean-only invocation must not need a framework')
        return self
    def get_package_dir(self, name):
        assert name == 'framework-zephyr-nrf54lm20'
        return self.path

class AdapterPatchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.metadata = self.root / 'package.json'
        self.metadata.write_text(json.dumps({'version': '3.40400.260428'}))
        self.source = self.root / 'scripts/platformio/platformio-build.py'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('# surrounding source\n' + OLD + '\n')
    def invoke(self, env=None):
        with contextlib.redirect_stdout(io.StringIO()):
            return runpy.run_path(str(SCRIPT), init_globals={
                'Import': lambda name: None,
                'env': env or Environment(str(self.root)),
            })
    def test_clean_needs_no_framework(self):
        self.invoke(Environment(clean=True))
        self.assertIn(OLD, self.source.read_text())
    def test_wraps_only_the_library_argument(self):
        self.invoke()
        self.assertEqual(self.source.read_text(), '# surrounding source\n' + NEW + '\n')
    def test_repeat_is_idempotent(self):
        self.invoke()
        first = self.source.read_bytes()
        self.invoke()
        self.assertEqual(first, self.source.read_bytes())
    def test_missing_framework_stops_normal_build(self):
        with self.assertRaisesRegex(RuntimeError, 'not installed'):
            self.invoke(Environment())
    def test_unknown_version_stops(self):
        self.metadata.write_text(json.dumps({'version': 'future-unreviewed-version'}))
        with self.assertRaisesRegex(RuntimeError, 'different framework version'):
            self.invoke()
    def test_ambiguous_source_stops(self):
        self.source.write_text(OLD + '\n' + OLD + '\n')
        with self.assertRaisesRegex(RuntimeError, 'Unexpected'):
            self.invoke()
    def test_unrecognized_source_stops(self):
        self.source.write_text('# Unrecognized adapter\n')
        with self.assertRaisesRegex(RuntimeError, 'Unexpected'):
            self.invoke()

if __name__ == '__main__':
    unittest.main()
