import subprocess,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
class JavaRelayTests(unittest.TestCase):
    def test_production_transfer_controller_with_simulated_recorder(self):
        with tempfile.TemporaryDirectory() as t:
            j=R/'android/app/src/main/java/org/tomstout/hermesvoice'
            names=('Wire.java','TransferEngine.java','UploadPolicy.java','RecorderInfo.java')
            subprocess.run(['javac','-d',t,*[str(j/n) for n in names],str(R/'tests/RelayEngineTest.java')],check=True)
            subprocess.run(['java','-cp',t,'org.tomstout.hermesvoice.RelayEngineTest'],check=True,timeout=90)
