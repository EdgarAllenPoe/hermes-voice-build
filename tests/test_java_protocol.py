import subprocess,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
class JavaProtocolTests(unittest.TestCase):
 def test_java_matches_fixture_and_endpoint_rules(self):
  with tempfile.TemporaryDirectory() as t:
   j=R/'android/app/src/main/java/org/tomstout/hermesvoice'
   subprocess.run(['javac','-d',t,str(j/'Wire.java'),str(j/'Endpoint.java'),str(R/'tests/WireTest.java')],check=True)
   subprocess.run(['java','-cp',t,'WireTest',str(R/'fixtures/synthetic-tone.hvb')],check=True)
