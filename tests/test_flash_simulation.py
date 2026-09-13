"""Tests actual storage.c against simulated NOR; not hardware or a Zephyr build."""
import ctypes,subprocess,tempfile,unittest
from pathlib import Path
from hvbridge.audio import encode_frame,validate
R=Path(__file__).resolve().parents[1]
class FlashSimulationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.t=tempfile.TemporaryDirectory();lib=Path(cls.t.name)/'flash.so';shim=R/'tests/host_shim'
  subprocess.run(['gcc','-std=c11','-shared','-fPIC','-pthread','-I'+str(shim),str(shim/'storage_harness.c'),'-o',str(lib)],check=True)
  cls.c=ctypes.CDLL(str(lib));cls.B=staticmethod(lambda n:(ctypes.c_ubyte*n));cls.frame=cls.B(164)(*encode_frame([2000,-2000]*160))
 @classmethod
 def tearDownClass(cls):cls.t.cleanup()
 def setUp(self):self.c.test_clean();self.assertEqual(self.c.hvb_store_init(),0)
 def recording(self):
  header=self.B(64)();s=self.c.hvb_store_begin(header);self.assertGreaterEqual(s,0);self.assertEqual(self.c.hvb_store_append(s,self.frame),0);self.assertEqual(self.c.hvb_store_finish(s,0),0);return s
 def test_committed_audio_roundtrips(self):
  s=self.recording();b=self.B(228)();self.assertEqual(self.c.hvb_store_read(s,0,b,228),0);self.assertEqual(validate(bytes(b)).samples,320)
 def test_reboot_retains_commit(self):
  self.recording();self.c.test_reboot();self.assertEqual(self.c.hvb_store_init(),0);self.assertEqual(self.c.hvb_store_count(),1)
 def test_interrupted_uncommitted_not_exposed(self):
  s=self.c.hvb_store_begin(self.B(64)());self.c.hvb_store_append(s,self.frame);self.c.test_reboot();self.c.hvb_store_init();self.assertEqual(self.c.hvb_store_count(),0)
 def test_header_commit_failure_not_exposed(self):
  s=self.c.hvb_store_begin(self.B(64)());self.c.hvb_store_append(s,self.frame);self.c.test_fail_after(1);self.assertNotEqual(self.c.hvb_store_finish(s,0),0);self.c.test_reboot();self.c.hvb_store_init();self.assertEqual(self.c.hvb_store_count(),0)
 def test_wrong_id_cannot_delete(self):
  s=self.recording();self.assertNotEqual(self.c.hvb_store_ack(s,self.B(16)()),0);self.assertEqual(self.c.hvb_store_count(),1)
 def test_ack_retained_across_interrupted_erase(self):
  s=self.recording();meta=self.B(20)();self.assertEqual(self.c.hvb_store_oldest(meta),s);self.assertEqual(self.c.hvb_store_ack(s,meta),0)
  for _ in range(25):self.c.hvb_store_gc_step()
  self.c.test_reboot();self.c.hvb_store_init();self.assertEqual(self.c.hvb_store_count(),0)
  for _ in range(128):self.c.hvb_store_gc_step()
  self.assertEqual(self.c.test_state(s),0)
 def test_full_queue_does_not_overwrite(self):
  for _ in range(15):self.recording()
  self.assertLess(self.c.hvb_store_begin(self.B(64)()),0);self.assertEqual(self.c.hvb_store_count(),15)
 def test_read_bounds(self):
  s=self.recording();self.assertNotEqual(self.c.hvb_store_read(s,227,self.B(2)(),2),0)
