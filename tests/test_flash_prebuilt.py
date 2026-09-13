import hashlib,importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
R=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('flash_prebuilt',R/'tools/flash_prebuilt.py')
M=importlib.util.module_from_spec(spec);spec.loader.exec_module(M)
class PrebuiltTests(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory();self.addCleanup(self.t.cleanup)
        self.root=Path(self.t.name);self.bundle=self.root/'bundle';self.bundle.mkdir()
        (self.root/'tools').mkdir();(self.root/'tools/check_firmware_dts.py').write_text('def validate(dts,config):\\n assert dts == "DTS" and config == "CONFIG"\\n'.replace('\\n','\n'))
        for name,data in {M.IMAGE:'HEX','generated-zephyr.dts':'DTS','generated-zephyr.config':'CONFIG',
                          'build-result.json':json.dumps(dict(complete=True,target='all'))}.items():
            (self.bundle/name).write_text(data)
        self.manifest()
    def manifest(self):
        entries=[p for p in self.bundle.iterdir() if p.name!='SHA256SUMS.txt']
        (self.bundle/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in entries))
    def test_explicit_complete_bundle_checks_without_hardware(self):
        with patch.object(M.subprocess,'run',side_effect=AssertionError('No hardware')):
            self.assertEqual(M.check_firmware(self.root,self.bundle),self.bundle/M.IMAGE)
    def test_changed_image_is_rejected(self):
        (self.bundle/M.IMAGE).write_text('CHANGED')
        with self.assertRaisesRegex(ValueError,'checksum'):M.check_firmware(self.root,self.bundle)
    def test_incomplete_build_is_rejected(self):
        (self.bundle/'build-result.json').write_text('{"complete":false,"target":"firmware"}');self.manifest()
        with self.assertRaisesRegex(ValueError,'incomplete'):M.check_firmware(self.root,self.bundle)
    def test_manifest_cannot_escape_bundle(self):
        (self.bundle/'SHA256SUMS.txt').write_text('0'*64+'  ../outside\n')
        with self.assertRaisesRegex(ValueError,'Unsafe'):M.check_firmware(self.root,self.bundle)
    def test_configuration_must_be_covered_by_manifest(self):
        (self.bundle/'generated-zephyr.config').unlink();self.manifest()
        with self.assertRaisesRegex(ValueError,'required'):M.check_firmware(self.root,self.bundle)
    def test_auto_mass_erase_disabled_before_init(self):
        command=M.upload_command(Path('openocd'),Path('scripts'),Path('platform'),Path('image.hex'))
        self.assertLess(command.index('nrf54lm20a.cpu configure -event examine-fail {}'),command.index('init'))
        self.assertTrue(any(x.startswith('verify_image ') for x in command))
    def test_tcl_braces_rejected(self):
        with self.assertRaises(ValueError):M.tcl_path(Path('bad{path}.hex'))
