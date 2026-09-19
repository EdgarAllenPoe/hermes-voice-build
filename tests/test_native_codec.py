"""Host-compiled C codec tests. Does not compile Zephyr or exercise MCU peripherals."""
import ctypes,math,os,random,subprocess,tempfile,unittest
from pathlib import Path
from hvbridge.audio import encode_frame,decode_frame
ROOT=Path(__file__).resolve().parents[1]
class Gate(ctypes.Structure):_fields_=[('frames',ctypes.c_uint),('voiced_run',ctypes.c_uint),('silence',ctypes.c_uint),('heard',ctypes.c_int)]
class NativeCodecTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.t=tempfile.TemporaryDirectory();lib=Path(cls.t.name)/'codec.so'
  config=Path(cls.t.name)/'config.c'
  config.write_text('#include "codec.h"\nunsigned test_vad_threshold(void){return HVB_VAD_THRESHOLD;}\n'
   '#ifdef _WIN32\n#include <stddef.h>\n'
   'void *memset(void *buf,int value,size_t count){unsigned char *p=buf;while(count--)*p++=(unsigned char)value;return buf;}\n#endif\n')
  flags=['-std=c11','-Wall','-Wextra','-Werror','-shared']
  if os.name=='nt':
   # This integer-only library needs no Windows SDK or DLL startup runtime.
   flags += ['-nostdlib','-fno-builtin','-fno-stack-protector','-Xlinker','/NOENTRY']
   for name in ('hvb_encode_frame','hvb_decode_frame','hvb_gate_update','test_vad_threshold'):
    flags += ['-Xlinker','/EXPORT:'+name]
  else:flags.append('-fPIC')
  subprocess.run([os.environ.get('CC','gcc'),*flags,'-I',str(ROOT/'firmware/src'),str(ROOT/'firmware/src/codec.c'),str(config),'-o',str(lib)],check=True)
  cls.c=ctypes.CDLL(str(lib));cls.PCM=ctypes.c_int16*320;cls.BYTES=ctypes.c_uint8*164
  cls.c.test_vad_threshold.restype=ctypes.c_uint
  cls.threshold=cls.c.test_vad_threshold()
  cls.c.hvb_encode_frame.argtypes=[ctypes.POINTER(ctypes.c_int16),ctypes.POINTER(ctypes.c_uint8)];cls.c.hvb_encode_frame.restype=None
  cls.c.hvb_decode_frame.argtypes=[ctypes.POINTER(ctypes.c_uint8),ctypes.POINTER(ctypes.c_int16)];cls.c.hvb_decode_frame.restype=None
  cls.c.hvb_gate_update.argtypes=[ctypes.POINTER(Gate),ctypes.POINTER(ctypes.c_int16),ctypes.c_uint];cls.c.hvb_gate_update.restype=ctypes.c_int
 @classmethod
 def tearDownClass(cls):
  if os.name=='nt':
   import _ctypes
   _ctypes.FreeLibrary(cls.c._handle)
  cls.t.cleanup()
 def test_encoder_matches_python(self):
  r=random.Random(2026);vectors=[[0]*320,[32767]*320,[-32768]*320,[int(12000*math.sin(i*.1)) for i in range(320)]]+[[r.randint(-32768,32767) for _ in range(320)] for _ in range(30)]
  for pcm in vectors:
   out=self.BYTES();self.c.hvb_encode_frame(self.PCM(*pcm),out);self.assertEqual(bytes(out),encode_frame(pcm))
 def test_decoder_matches_python(self):
  for n in range(10):
   b=encode_frame([int(14000*math.sin(i*.05*(n+1))) for i in range(320)]);out=self.PCM();self.c.hvb_decode_frame(self.BYTES(*b),out);self.assertEqual(list(out),decode_frame(b))
 def test_gate_no_speech(self):
  g=Gate();sil=self.PCM(*([0]*320));results=[self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold) for _ in range(250)];self.assertEqual(results[-1],2);self.assertFalse(any(results[:-1]))
 def test_gate_speech_then_silence(self):
  g=Gate();voice=self.PCM(*([2000,-2000]*160));sil=self.PCM(*([0]*320))
  for _ in range(10):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),0)
  for _ in range(99):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),1)
 def test_gate_limit(self):
  g=Gate();voice=self.PCM(*([2000,-2000]*160))
  for _ in range(2999):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),3)
 def test_gate_rejects_dc_offset(self):
  g=Gate();dc=self.PCM(*([10000]*320))
  for _ in range(250):v=self.c.hvb_gate_update(ctypes.byref(g),dc,self.threshold)
  self.assertEqual(v,2)

 def test_board_level_speech_survives_no_speech_timeout(self):
  # Synthetic quiet speech and pauses at levels measured on a Sense board.
  # This contains no captured microphone audio.
  g=Gate();noise=self.PCM(*([5,-5]*160))
  voice=self.PCM(*[int(100*math.sin(2*math.pi*200*i/16000)) for i in range(320)])
  for _ in range(50):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),noise,self.threshold),0)
  for _ in range(6):
   for _ in range(30):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),0)
   for _ in range(20):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),noise,self.threshold),0)
  self.assertTrue(g.heard)
  for _ in range(79):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),noise,self.threshold),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),noise,self.threshold),1)
 def test_board_noise_and_short_click_do_not_count_as_speech(self):
  g=Gate();noise=self.PCM(*([1008,992]*160));click=self.PCM(*([1800,200]*160))
  for i in range(250):
   frame=click if 50<=i<52 else noise
   result=self.c.hvb_gate_update(ctypes.byref(g),frame,self.threshold)
   self.assertEqual(result,2 if i==249 else 0)
  self.assertFalse(g.heard)


 def test_pause_longer_than_old_timeout_stays_in_same_recording(self):
  g=Gate();voice=self.PCM(*([2000,-2000]*160));sil=self.PCM(*([0]*320))
  for _ in range(3):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),0)
  # 1.5 seconds used to stop at 1.2 seconds; now it must keep recording.
  for _ in range(75):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),0)
  for _ in range(10):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),0)
  for _ in range(99):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),1)

 def test_maximum_duration_wins_over_silence(self):
  g=Gate();voice=self.PCM(*([2000,-2000]*160));sil=self.PCM(*([0]*320))
  for _ in range(2900):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),voice,self.threshold),0)
  for _ in range(99):self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),0)
  self.assertEqual(self.c.hvb_gate_update(ctypes.byref(g),sil,self.threshold),3)
