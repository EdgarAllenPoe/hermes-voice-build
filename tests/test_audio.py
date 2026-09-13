import math,random,struct,tempfile,unittest,uuid,wave,zlib
from pathlib import Path
from hvbridge.audio import *
class AudioTests(unittest.TestCase):
 def test_roundtrip_silence(self):
  pcm=[0]*320;b=make_container(pcm);self.assertEqual(decode_frame(b[64:]),pcm);self.assertEqual(validate(b).samples,320)
 def test_header_fields(self):
  mid=str(uuid.uuid4());h=validate(make_container([5]*320,mid,3));self.assertEqual(h.message_id,mid);self.assertEqual(h.flags,3)
 def test_padding(self):self.assertEqual(validate(make_container([1]*321)).samples,640)
 def test_random_extremes(self):
  r=random.Random(44)
  for n in range(20):
   x=[r.randint(-32768,32767) for _ in range(320)];b=encode_frame(x);self.assertEqual(len(b),164);self.assertEqual(len(decode_frame(b)),320)
 def test_crc_rejection(self):
  b=bytearray(make_container([100]*320));b[-1]^=1
  with self.assertRaises(ValueError):validate(bytes(b))
 def test_truncation(self):
  with self.assertRaises(ValueError):validate(make_container([1]*320)[:-1])
 def test_reserved_rejection(self):
  b=bytearray(make_container([100]*320));b[56]=1
  with self.assertRaises(ValueError):validate(bytes(b))
 def test_bad_frame_even_with_crc(self):
  b=bytearray(make_container([100]*320));b[66]=99;struct.pack_into('<I',b,20,zlib.crc32(b[64:]))
  with self.assertRaises(ValueError):validate(bytes(b))
 def test_zero_id(self):
  with self.assertRaises(ValueError):make_container([1]*320,str(uuid.UUID(int=0)))
 def test_empty(self):
  with self.assertRaises(ValueError):make_container([])
 def test_oversize(self):
  with self.assertRaises(ValueError):make_container([0]*960001)
 def test_wav(self):
  with tempfile.TemporaryDirectory() as t:
   p=Path(t)/'out.wav';to_wav(make_container([100]*640),p)
   with wave.open(str(p),'rb') as f:self.assertEqual((f.getframerate(),f.getnchannels(),f.getsampwidth(),f.getnframes()),(16000,1,2,640))
