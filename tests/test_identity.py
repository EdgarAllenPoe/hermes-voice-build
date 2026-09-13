import hashlib,importlib.util,tempfile,unittest,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('restore_identity',R/'tools/restore_identity.py')
M=importlib.util.module_from_spec(spec);spec.loader.exec_module(M)
class IdentityTests(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory();self.addCleanup(self.t.cleanup)
        self.root=Path(self.t.name)/'repo';self.root.mkdir()
        self.backup=Path(self.t.name)/'backup.zip'
        code='3'*6
        self.members={'android/debug.keystore':b'SYNTHETIC TEST KEY',
            'recorder/device_config.h':('#define HVB_PAIRING_CODE '+code+'U\n').encode(),
            'recorder/pairing-card.txt':('BLE passkey: '+code+'\n').encode()}
        self.zip()
    def zip(self,bad_hash=False):
        with zipfile.ZipFile(self.backup,'w') as z:
            for n,b in self.members.items():z.writestr('backup/'+n,b)
            z.writestr('backup/SHA256SUMS.txt',''.join(
                ('0'*64 if bad_hash else hashlib.sha256(b).hexdigest())+'  '+n+'\n'
                for n,b in self.members.items()))
            z.writestr('backup/recovery/private.key.pem','MUST NOT BE EXTRACTED')
    def test_preview_does_not_write(self):
        self.assertEqual(len(M.restore(self.backup,self.root)),3)
        self.assertEqual(list(self.root.iterdir()),[])
    def test_restore_is_idempotent_and_excludes_recovery_key(self):
        self.assertEqual(len(M.restore(self.backup,self.root,True)),3)
        self.assertEqual(M.restore(self.backup,self.root,True),[])
        self.assertEqual(len([p for p in self.root.rglob('*') if p.is_file()]),3)
    def test_different_identity_is_not_overwritten(self):
        p=self.root/'config/private/android-debug.keystore';p.parent.mkdir(parents=True);p.write_bytes(b'KEEP ME')
        with self.assertRaises(ValueError):M.restore(self.backup,self.root,True)
        self.assertEqual(p.read_bytes(),b'KEEP ME')
    def test_bad_checksum_is_rejected_before_writes(self):
        self.zip(True)
        with self.assertRaises(ValueError):M.restore(self.backup,self.root,True)
        self.assertEqual(list(self.root.iterdir()),[])
    def test_mismatched_pairing_card_is_rejected(self):
        self.members['recorder/pairing-card.txt']=b'wrong';self.zip()
        with self.assertRaises(ValueError):M.restore(self.backup,self.root,True)
        self.assertEqual(list(self.root.iterdir()),[])
