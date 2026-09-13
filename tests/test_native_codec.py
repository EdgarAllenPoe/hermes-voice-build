"""Host-compiled C codec tests. Does not compile Zephyr or exercise MCU peripherals."""
import ctypes,math,random,subprocess,tempfile,unittest
from pathlib import Path
from hvbridge.audio import encode_frame,decode_frame
ROOT=Path(__file__).resolve().parents[1]
class Gate(ctypes.Structure):_fields_=[('frames',ctypes.c_uint),('voiced_run',ctypes.c_uint),('silence',ctypes.c_uint),('heard',ctypes.c_int)]
class NativeCodecTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.t=tempfile.TemporaryDirectory();lib=Path(cls.t.name)/'codec.so'
  subprocess.run(['gcc','-std=c11','-Wall','-Wextra','-Werror','-shared','-fPIC',str(ROOT/'firmware/src/codec.c'),'-o',str(lib)],check=True)
  cls.c=ctypes.CDLL(str(lib));cls.PCM=ctypes.c_int16*320;cls.BYTES=ctypes.c_uint8*164
  cls.c.hvb_encode_frame.argtypes=[ctypes.POINTER(ctypes.c_int16),ctypes.POINTER(ctypes.c_uint8)];cls.c.hvb_encode_frame.restype=None
  cls.c.hvb_decode_frame.argtypes=[ctypes.POINTER(ctypes.c_uint8),ctypes.POINTER(ctypes.c_int16)];cls.c.hvb_decode_frame.restype=None
  cls.c.hvb_gate_update.argtypes=[ctypes.POINTER(Gate),ctypes.POINTER(ctypes.c_int16),ctypes.c_uint];cls.c.hvb_gate_update.restype=ctypes.c_int
 @classmethod
 def tearDownClass(cls):cls.t.cleanup()
 def test_encoder_matches_python(self):
  r=random.Random(2026);vectors=[[0]*320,[32767]*320,[-32768]*320,[int(12000*math.sin(i*.1)) for i in range(320)]]+[[r.randint(-32768,32767) for _ in range(320)] for _ in range(30)]
  for pcm in vectors:
   out=self.BYTES();self.c.hvb_encode_frame(self.PCM(*pcm),out);self.assertEqual(bytes(out),encode_frame(pcm))
 def test_decoder_matches_python(self):
  for n in range(10):
   b=encode_frame([int(14000*math.sin(i*.05*(n+1))) for i in range(320)]);out=self.PCM();self.c.hvb_decode_frame(self.BYTES(*b),out);self.assertEqual(list(out),decode_frame(b))
 def test_gate_no_speech(self):
  g=Gate();sil=self.PCM(*([0]*320));results=[self.c.hvb_gate_update(ctypes.byref(g),sil,300) for _ in range(250)];self.assertEqual(results[-1],2);self.assertFalse(any(results[:-1]))
 def test_gate_speech_then_silence(self):
  g=Gate();voice=self.PCM(*([2000,-2000]*160));sil=self.PCM(*([0]*320))
  for _ in range(10):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,300),0)
  for _ in range(59):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,300),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,300),1)
 def test_gate_limit(self):
  g=Gate();voice=self.PCM(*([2000,-2000]*160))
  for _ in range(2999):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,300),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,300),3)
 def test_gate_rejects_dc_offset(self):
  g=Gate();dc=self.PCM(*([10000]*320))
  for _ in range(250):v=self.c.hvb_gate_update(ctypes.byref(g),dc,300)
  self.assertEqual(v,2)
