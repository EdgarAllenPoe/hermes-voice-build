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

 def test_empty_manual_stop_releases_slot(self):
  slot=self.c.hvb_store_begin(self.B(64)());self.assertEqual(self.c.hvb_store_finish(slot,2),0)
  self.assertEqual(self.c.test_state(slot),3)
  for _ in range(128):self.c.hvb_store_gc_step()
  self.assertEqual(self.c.test_state(slot),0)
 def test_corrupt_committed_audio_is_quarantined_and_next_delivered(self):
  bad=self.recording();good=self.recording();self.c.test_flip(bad,64)
  self.c.test_reboot();self.assertEqual(self.c.hvb_store_init(),0)
  self.assertEqual(self.c.test_state(bad),5)
  self.assertEqual(self.c.hvb_store_oldest(self.B(20)()),good)
 def test_skip_does_not_ack_or_erase(self):
  first=self.recording();second=self.recording()
  self.assertEqual(self.c.hvb_store_next(self.B(20)(),1<<first),second)
  self.assertEqual(self.c.hvb_store_count(),2)
  self.assertEqual(self.c.hvb_store_oldest(self.B(20)()),first)
 def test_torn_header_preserves_other_recordings(self):
  good=self.recording();slot=self.c.hvb_store_begin(self.B(64)());self.c.hvb_store_append(slot,self.frame)
  self.c.test_torn_write(11);self.assertNotEqual(self.c.hvb_store_finish(slot,0),0)
  self.c.test_reboot();self.assertEqual(self.c.hvb_store_init(),0)
  self.assertEqual(self.c.hvb_store_count(),1);self.assertEqual(self.c.hvb_store_oldest(self.B(20)()),good)
 def test_torn_payload_not_committed(self):
  slot=self.c.hvb_store_begin(self.B(64)());self.c.test_torn_write(17)
  self.assertNotEqual(self.c.hvb_store_append(slot,self.frame),0);self.c.hvb_store_abort(slot)
  self.c.test_reboot();self.assertEqual(self.c.hvb_store_init(),0);self.assertEqual(self.c.hvb_store_count(),0)
 def test_erase_failure_is_visible_and_preserved(self):
  slot=self.recording();meta=self.B(20)();self.c.hvb_store_oldest(meta);self.c.hvb_store_ack(slot,meta)
  self.c.test_erase_error(1);self.c.hvb_store_gc_step()
  diag=self.B(6)();self.c.hvb_store_diagnostics(diag)
  self.assertEqual(self.c.test_state(slot),5);self.assertGreater(diag[0],0);self.assertEqual(diag[4],1)

 def test_inventory_and_guarded_operator_delete_persist_after_reboot(self):
  slot=self.recording();inventory=self.B(454)();self.assertEqual(self.c.hvb_store_inventory(inventory),34)
  self.assertEqual(bytes(inventory[:4]),bytes([1,1,0,0]));self.assertEqual(inventory[4],slot)
  wrong=self.B(16)();self.assertNotEqual(self.c.hvb_store_delete(slot,wrong),0);self.assertEqual(self.c.hvb_store_count(),1)
  recording_id=self.B(16)(*inventory[6:22]);self.assertEqual(self.c.hvb_store_delete(slot,recording_id),0)
  self.c.test_reboot();self.assertEqual(self.c.hvb_store_init(),0);self.assertEqual(self.c.hvb_store_count(),0)
 def test_delete_cannot_remove_active_or_reused_slot(self):
  slot=self.c.hvb_store_begin(self.B(64)());self.assertNotEqual(self.c.hvb_store_delete(slot,self.B(16)()),0)
  self.assertEqual(self.c.test_state(slot),2)
 def test_operator_can_discard_quarantined_slot(self):
  slot=self.recording();self.c.test_flip(slot,64);self.c.test_reboot();self.c.hvb_store_init();inventory=self.B(454)();self.c.hvb_store_inventory(inventory)
  self.assertEqual(inventory[5],1);self.assertEqual(self.c.hvb_store_delete(slot,self.B(16)(*inventory[6:22])),0)
  self.c.test_reboot();self.c.hvb_store_init();self.assertEqual(self.c.hvb_store_count(),0);self.assertNotEqual(self.c.test_state(slot),5)
