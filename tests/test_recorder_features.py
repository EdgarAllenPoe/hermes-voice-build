import math,struct,subprocess,tempfile,unittest
from pathlib import Path
from hvbridge.audio import make_container,decode_frame
R=Path(__file__).resolve().parents[1]
class RecorderFeatureTests(unittest.TestCase):
 def test_catalog_extended_status_and_playback(self):
  with tempfile.TemporaryDirectory() as tmp:
   folder=Path(tmp);audio=make_container([int(9000*math.sin(i*.08)) for i in range(320*6)]);(folder/'audio.hvb').write_bytes(audio)
   pcm=[]
   for pos in range(64,len(audio),164):pcm.extend(decode_frame(audio[pos:pos+164]))
   (folder/'expected.pcm').write_bytes(struct.pack('<'+'h'*len(pcm),*pcm))
   source=R/'android/app/src/main/java/org/tomstout/hermesvoice'
   subprocess.run(['javac','-d',tmp,*[str(source/n) for n in ('Wire.java','RecorderInfo.java','RecorderInventory.java','AudioDecoder.java')],str(R/'tests/RecorderFeaturesTest.java')],check=True)
   subprocess.run(['java','-cp',tmp,'org.tomstout.hermesvoice.RecorderFeaturesTest',str(folder/'audio.hvb'),str(folder/'expected.pcm')],check=True,timeout=30)
