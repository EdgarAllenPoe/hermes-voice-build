"""Compile the real button controller and exercise tap/hold/bounce event traces."""
import ctypes,os,subprocess,tempfile,unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
NONE,WAKE,START,STOP,PAIR,FORGET=range(6)
class Button(ctypes.Structure):
    _fields_=[('held_since',ctypes.c_int64),('last_up',ctypes.c_int64),
              ('held',ctypes.c_bool),('release_pending',ctypes.c_bool),
              ('tentative',ctypes.c_bool),('hold_allowed',ctypes.c_bool),
              ('pair_shown',ctypes.c_bool),('forget_shown',ctypes.c_bool)]
class ButtonTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();lib=Path(cls.tmp.name)/'button.so'
        flags=['-std=c11','-Wall','-Wextra','-Werror','-shared']
        if os.name=='nt':
            flags+=['-nostdlib','-fno-builtin','-fno-stack-protector','-Xlinker','/NOENTRY']
            for name in ('hvb_button_init','hvb_button_edge','hvb_button_poll'):
                flags+=['-Xlinker','/EXPORT:'+name]
        else:flags.append('-fPIC')
        subprocess.run([os.environ.get('CC','gcc'),*flags,str(R/'firmware/src/button.c'),'-o',str(lib)],check=True)
        cls.c=ctypes.CDLL(str(lib))
        cls.c.hvb_button_init.argtypes=[ctypes.POINTER(Button)]
        cls.c.hvb_button_init.restype=None
        cls.c.hvb_button_edge.argtypes=[ctypes.POINTER(Button),ctypes.c_int64,ctypes.c_bool,ctypes.c_bool]
        cls.c.hvb_button_poll.argtypes=[ctypes.POINTER(Button),ctypes.c_int64,ctypes.c_bool]
    @classmethod
    def tearDownClass(cls):
        if os.name=='nt':
            import _ctypes
            _ctypes.FreeLibrary(cls.c._handle)
        cls.tmp.cleanup()
    def setUp(self):
        self.b=Button();self.c.hvb_button_init(ctypes.byref(self.b))
    def edge(self,t,down=True,recording=False):
        return self.c.hvb_button_edge(ctypes.byref(self.b),t,down,recording)
    def poll(self,t,recording=False):
        return self.c.hvb_button_poll(ctypes.byref(self.b),t,recording)
    def tap(self):
        self.assertEqual(self.edge(0),WAKE)
        self.assertEqual(self.edge(80,False),NONE)
        self.assertEqual(self.poll(140),START)
    def test_single_tap_starts_after_debounced_release(self):
        self.assertEqual(self.edge(0),WAKE);self.edge(80,False)
        self.assertEqual(self.poll(139),NONE);self.assertEqual(self.poll(140),START)
        self.assertFalse(self.b.tentative);self.assertEqual(self.poll(141),NONE)
    def test_no_old_double_click_wait(self):
        self.edge(0);self.edge(40,False)
        self.assertEqual(self.poll(100),START)
    def test_one_second_press_is_still_a_single_tap(self):
        self.edge(0);self.assertEqual(self.poll(501),NONE);self.edge(1000,False)
        self.assertEqual(self.poll(1060),START)
    def test_press_bounce_does_not_start_or_restart_warmup(self):
        self.edge(0);self.edge(5,False);self.assertEqual(self.edge(12),NONE)
        self.assertEqual(self.poll(72),NONE);self.edge(90,False)
        self.assertEqual(self.poll(150),START)
    def test_queued_bounce_survives_delayed_audio_loop(self):
        self.edge(0);self.edge(5,False);self.edge(12);self.edge(80,False)
        self.assertEqual(self.poll(200),START);self.assertEqual(self.poll(220,True),NONE)
    def test_release_bounce_requires_final_stable_release(self):
        self.edge(0);self.edge(80,False);self.edge(90);self.edge(95,False)
        self.assertEqual(self.poll(140),NONE);self.assertEqual(self.poll(155),START)
    def test_duplicate_down_is_ignored(self):
        self.edge(0);self.assertEqual(self.edge(100),NONE);self.edge(200,False)
        self.assertEqual(self.poll(260),START)
    def test_stray_release_does_nothing(self):
        self.edge(80,False);self.assertEqual(self.poll(200),NONE)
    def test_duplicate_release_does_not_extend_debounce(self):
        self.edge(0);self.edge(80,False);self.edge(100,False)
        self.assertEqual(self.poll(140),START)
    def test_second_tap_stops_recording(self):
        self.tap();self.assertEqual(self.edge(250,recording=True),STOP)
        self.edge(330,False);self.assertEqual(self.poll(390),NONE)
    def test_stop_press_bounce_does_not_restart_recording(self):
        self.assertEqual(self.edge(0,recording=True),STOP)
        self.edge(5,False);self.assertEqual(self.edge(12),NONE);self.edge(80,False)
        self.assertEqual(self.poll(140),NONE);self.assertFalse(self.b.tentative)
    def test_holding_stop_never_pairs_or_forgets(self):
        self.edge(0,recording=True)
        self.assertEqual(self.poll(1500),NONE);self.assertEqual(self.poll(10000),NONE)
        self.edge(10100,False);self.assertEqual(self.poll(10160),NONE)
    def test_hold_opens_pairing_at_boundary_only_once(self):
        self.edge(0);self.assertEqual(self.poll(1499),NONE)
        self.assertEqual(self.poll(1500),PAIR);self.assertEqual(self.poll(1600),NONE)
        self.assertFalse(self.b.tentative)
    def test_release_after_pairing_does_not_record(self):
        self.edge(0);self.poll(1500);self.edge(1600,False)
        self.assertEqual(self.poll(1660),NONE)
    def test_hold_forgets_at_boundary_only_once(self):
        self.edge(0);self.poll(1500);self.assertEqual(self.poll(9999),NONE)
        self.assertEqual(self.poll(10000),FORGET);self.assertEqual(self.poll(11000),NONE)
        self.edge(11100,False);self.assertEqual(self.poll(11160),NONE)
    def test_delayed_poll_does_not_pair_after_forget(self):
        self.edge(0);self.assertEqual(self.poll(10000),FORGET)
        self.assertEqual(self.poll(11000),NONE)
    def test_hold_classified_on_release_when_poll_was_delayed(self):
        self.edge(0);self.edge(1500,False);self.assertEqual(self.poll(1560),PAIR)
        self.assertEqual(self.poll(2000),NONE)
    def test_long_hold_classified_on_release_when_poll_was_delayed(self):
        self.edge(0);self.edge(10000,False);self.assertEqual(self.poll(10060),FORGET)
    def test_pair_hold_then_next_tap_starts(self):
        self.edge(0);self.poll(1500);self.edge(1600,False);self.poll(1660)
        self.assertEqual(self.edge(1800),WAKE);self.edge(1880,False)
        self.assertEqual(self.poll(1940),START)
    def test_auto_stop_then_next_tap_starts(self):
        self.tap()
        self.assertEqual(self.edge(5000,recording=False),WAKE);self.edge(5080,False)
        self.assertEqual(self.poll(5140),START)
    def test_failed_microphone_warmup_cannot_start(self):
        self.edge(0);self.b.tentative=False;self.edge(80,False)
        self.assertEqual(self.poll(140),NONE)
