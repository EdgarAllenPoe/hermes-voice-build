"""Exercise provisioning and Git exclusion in disposable repositories only."""
import importlib.util
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('provision_test_builder', ROOT / 'tools/build_binaries.py')
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class StopBeforeTargetBuild(Exception):
    pass


class ProvisionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'tools').mkdir()
        (self.root / 'firmware/src').mkdir(parents=True)
        for name in ['.gitignore', 'tools/provision.py', 'firmware/src/device_config.example.h']:
            shutil.copyfile(ROOT / name, self.root / name)
        self.header = self.root / 'firmware/src/device_config.h'
        self.card = self.root / 'config/private/pairing-card.txt'
        self.git('init', '--quiet', '--initial-branch=main')
        self.git('add', '--all')

    def git(self, *args):
        return subprocess.check_output(
            ['git', '-c', 'safe.directory=' + self.root.as_posix(), *args], cwd=self.root)

    def provision(self, *args):
        return subprocess.run(
            [sys.executable, '-B', str(self.root / 'tools/provision.py'), *args],
            cwd=self.root, capture_output=True, text=True, encoding='utf-8')

    def assert_matched_private_files(self):
        definition = re.search(r'#define HVB_PAIRING_CODE ([0-9]{6})U', self.header.read_text())
        self.assertIsNotNone(definition)
        self.assertIn('BLE passkey: ' + definition.group(1), self.card.read_text())

    def test_provisioned_header_and_card_stay_out_of_git_add_all(self):
        result = self.provision()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_matched_private_files()
        self.git('add', '--all')
        tracked = self.git('ls-files').decode().splitlines()
        self.assertNotIn('firmware/src/device_config.h', tracked)
        self.assertNotIn('config/private/pairing-card.txt', tracked)
        self.assertIn('firmware/src/device_config.example.h', tracked)
        ignored = self.git('check-ignore', 'firmware/src/device_config.h', 'config/private/pairing-card.txt')
        self.assertEqual(len(ignored.decode().splitlines()), 2)

    def test_existing_configuration_is_preserved_without_force(self):
        self.assertEqual(self.provision().returncode, 0)
        before = (self.header.read_bytes(), self.card.read_bytes())
        self.assertNotEqual(self.provision().returncode, 0)
        self.assertEqual((self.header.read_bytes(), self.card.read_bytes()), before)

    def test_missing_card_does_not_silently_rotate_the_header(self):
        self.assertEqual(self.provision().returncode, 0)
        before = self.header.read_bytes()
        self.card.unlink()
        result = self.provision()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Restore the card', result.stderr)
        self.assertEqual(self.header.read_bytes(), before)
        self.assertFalse(self.card.exists())

    def test_explicit_force_can_recreate_a_matched_configuration(self):
        self.assertEqual(self.provision().returncode, 0)
        self.card.unlink()
        self.assertEqual(self.provision('--force').returncode, 0)
        self.assert_matched_private_files()

    def test_build_helper_provisions_when_fresh_checkout_has_no_header(self):
        def run(command, log, env=None):
            if command[0] == sys.executable:
                subprocess.run(command, cwd=self.root, check=True, capture_output=True)
            else:
                raise StopBeforeTargetBuild()
        with patch.object(BUILDER, 'ROOT', self.root), patch.object(BUILDER, 'run', side_effect=run):
            with self.assertRaises(StopBeforeTargetBuild):
                BUILDER.build_firmware({'pio': 'not-a-real-pio'}, self.root / 'dist', self.root / 'logs', False)
        self.assert_matched_private_files()

    def test_build_helper_refuses_a_missing_pairing_card(self):
        self.assertEqual(self.provision().returncode, 0)
        before = self.header.read_bytes()
        self.card.unlink()
        with patch.object(BUILDER, 'ROOT', self.root), patch.object(BUILDER, 'run') as run:
            with self.assertRaisesRegex(RuntimeError, 'pairing card is missing'):
                BUILDER.build_firmware({'pio': 'not-a-real-pio'}, self.root / 'dist', self.root / 'logs', False)
            run.assert_not_called()
        self.assertEqual(self.header.read_bytes(), before)

    def test_build_helper_keeps_an_existing_matched_configuration(self):
        self.assertEqual(self.provision().returncode, 0)
        before = (self.header.read_bytes(), self.card.read_bytes())
        with patch.object(BUILDER, 'ROOT', self.root), patch.object(BUILDER, 'run', side_effect=StopBeforeTargetBuild) as run:
            with self.assertRaises(StopBeforeTargetBuild):
                BUILDER.build_firmware({'pio': 'not-a-real-pio'}, self.root / 'dist', self.root / 'logs', False)
            self.assertEqual(run.call_args.args[0], ['not-a-real-pio', '--version'])
        self.assertEqual((self.header.read_bytes(), self.card.read_bytes()), before)

    def test_gitignore_blocks_private_outputs_but_allows_public_fixtures(self):
        ignored = ['.env', '.env.local', 'receiver/token', 'config/private/key.pem',
                   'backup/private-key.pem', 'signing.jks', 'firmware/copy.hex',
                   'firmware/copy.bin', 'firmware/copy.elf', 'android/copy.apk',
                   'PRIVATE-pairing-card.txt', 'recording.wav', 'recording.hvb',
                   'receiver/state/queue.sqlite3', 'queue.sqlite3-wal', 'queue.db']
        public = ['.env.example', 'config/build-recipient.crt.pem',
                  'fixtures/synthetic-tone.hvb', 'fixtures/synthetic-tone.wav',
                  'firmware/src/device_config.example.h', 'receiver/config.example.json']
        for name in ignored + public:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists(): path.write_text('synthetic test content')
        self.git('add', '--all')
        tracked = self.git('ls-files').decode().splitlines()
        for name in ignored:
            with self.subTest(ignored=name): self.assertNotIn(name, tracked)
        for name in public:
            with self.subTest(public=name): self.assertIn(name, tracked)

    def test_real_repository_does_not_track_generated_pairing_header(self):
        if not (ROOT / '.git').exists():
            self.skipTest('Source archive has no Git index to inspect')
        tracked = subprocess.check_output(
            ['git', '-c', 'safe.directory=' + ROOT.as_posix(), 'ls-files', '--',
             'firmware/src/device_config.h', 'config/private/'], cwd=ROOT)
        self.assertEqual(tracked, b'')


if __name__ == '__main__':
    unittest.main()
