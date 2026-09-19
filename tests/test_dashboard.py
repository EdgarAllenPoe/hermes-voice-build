import subprocess,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
class DashboardTests(unittest.TestCase):
    def test_health_rules(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac','-d',folder,str(R/'android/app/src/main/java/org/tomstout/hermesvoice/DashboardState.java'),str(R/'tests/DashboardStateTest.java')],check=True)
            subprocess.run(['java','-cp',folder,'org.tomstout.hermesvoice.DashboardStateTest'],check=True,timeout=30)
