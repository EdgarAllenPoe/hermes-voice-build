"""Run the actual firmware button controller against deterministic event times."""
import ctypes,subprocess,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
class Button(ctypes.Structure):
    _fields_=[('first',ctypes.c_int64),('last_down',ctypes.c_int64),('held_since',ctypes.c_int64),
              ('held',ctypes.c_bool),('released',ctypes.c_bool),('tentative',ctypes.c_bool),
              ('pair_shown',ctypes.c_bool),('forget_shown',ctypes.c_bool)]
class ButtonTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();lib=Path(cls.tmp.name)/'button.so'
        subprocess.run(['gcc','-std=c11','-Wall','-Wextra','-Werror','-shared','-fPIC',
                        str(R/'firmware/src/button.c'),'-o',str(lib)],check=True)
        cls.c=ctypes.CDLL(str(lib))
        cls.c.hvb_button_edge.argtypes=[ctypes.POINTER(Button),ctypes.c_int64,ctypes.c_bool,ctypes.c_bool]
        cls.c.hvb_button_poll.argtypes=[ctypes.POINTER(Button),ctypes.c_int64,ctypes.c_bool]
    @classmethod
    def tearDownClass(cls):cls.tmp.cleanup()
    def setUp(self):
        self.b=Button();self.c.hvb_button_init(ctypes.byref(self.b))
    def edge(self,t,down=True,recording=False):
        return self.c.hvb_button_edge(ctypes.byref(self.b),t,down,recording)
    def poll(self,t,recording=False):
        return self.c.hvb_button_poll(ctypes.byref(self.b),t,recording)
    def test_single_click_cancels(self):
        self.assertEqual(self.edge(0),1);self.edge(50,False)
        self.assertEqual(self.poll(501),4);self.assertFalse(self.b.tentative)
    def test_double_click_starts(self):
        self.edge(0);self.edge(70,False);self.assertEqual(self.edge(150),2)
    def test_bounce_cannot_start(self):
        self.edge(0);self.edge(5,False);self.assertEqual(self.edge(12),0)
        self.assertEqual(self.poll(501),4)
    def test_late_second_click_is_new_tentative_capture(self):
        self.edge(0);self.edge(70,False);self.assertEqual(self.edge(501),1)
        self.assertEqual(self.b.first,501)
    def test_manual_stop(self):
        self.assertEqual(self.edge(100,True,True),3)
    def test_hold_opens_pairing_once(self):
        self.edge(0);self.assertEqual(self.poll(501),4)
        self.assertEqual(self.poll(1501),5);self.assertEqual(self.poll(1600),0)
    def test_long_hold_forgets_once(self):
        self.edge(0);self.poll(1501);self.assertEqual(self.poll(10001),6)
        self.assertEqual(self.poll(11000),0)
    def test_release_cancels_hold(self):
        self.edge(0);self.edge(100,False);self.poll(501)
        self.assertEqual(self.poll(10001),0)
    def test_hold_never_pairs_during_recording(self):
        self.edge(0);self.assertEqual(self.poll(2000,True),0)
    def test_exact_double_click_boundary(self):
        self.edge(0);self.edge(80,False);self.assertEqual(self.edge(500),2)
