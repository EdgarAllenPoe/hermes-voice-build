import json,unittest,xml.etree.ElementTree as E
from pathlib import Path
R=Path(__file__).resolve().parents[1];A='{http://schemas.android.com/apk/res/android}'
class ManifestTests(unittest.TestCase):
 def test_no_phone_microphone_or_assistant(self):
  tree=E.parse(R/'android/app/src/main/AndroidManifest.xml');perms={x.get(A+'name') for x in tree.findall('uses-permission')}
  for forbidden in ('android.permission.RECORD_AUDIO','android.permission.BIND_VOICE_INTERACTION','android.permission.BIND_ACCESSIBILITY_SERVICE','android.permission.ACCESS_FINE_LOCATION'):self.assertNotIn(forbidden,perms)
 def test_review_default(self):self.assertEqual(json.loads((R/'receiver/config.example.json').read_text())['delivery_mode'],'review')
 def test_no_default_pairing_secret(self):self.assertIn('#error',(R/'firmware/src/device_config.h').read_text())
