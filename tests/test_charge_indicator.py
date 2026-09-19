"""Exercise the production C charging indicator with realistic PMIC samples."""
import ctypes
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ChargeIndicatorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        lib = Path(cls.tmp.name)/'charge_indicator.so'
        flags = ['-std=c11', '-Wall', '-Wextra', '-Werror', '-shared']
        if os.name == 'nt':
            flags += ['-nostdlib', '-fno-builtin', '-fno-stack-protector',
                      '-Xlinker', '/NOENTRY', '-Xlinker', '/EXPORT:hvb_charge_indicator']
        else:
            flags.append('-fPIC')
        subprocess.run([os.environ.get('CC', 'gcc'), *flags,
                        str(ROOT/'firmware/src/charge_indicator.c'), '-o', str(lib)], check=True)
        cls.lib = ctypes.CDLL(str(lib))
        cls.indicate = cls.lib.hvb_charge_indicator
        cls.indicate.argtypes = [ctypes.c_bool, ctypes.c_uint8, ctypes.c_int32,
                                 ctypes.c_int64, ctypes.c_uint8, ctypes.c_uint8, ctypes.c_uint32]
        cls.indicate.restype = ctypes.c_bool

    @classmethod
    def tearDownClass(cls):
        if os.name == 'nt':
            import _ctypes
            _ctypes.FreeLibrary(cls.lib._handle)
        cls.tmp.cleanup()

    def led(self, valid=True, bus=0x21, mv=4023, ua=104370, status=0x09, error=0, limit=100000):
        return self.indicate(valid, bus, mv, ua, status, error, limit)

    def test_observed_battery_sample_is_charging(self):
        self.assertTrue(self.led())

    def test_trickle_and_taper_boundary(self):
        self.assertTrue(self.led(mv=2800, ua=10000, status=0x05))
        self.assertTrue(self.led(mv=4200, ua=10000, status=0x11))
        self.assertFalse(self.led(mv=4200, ua=9999, status=0x11))

    def test_completed_charge_turns_off_from_current(self):
        self.assertFalse(self.led(mv=4200, ua=0, status=0x03))
        self.assertFalse(self.led(mv=4200, ua=-100, status=0x03))

    def test_d00_complete_flag_does_not_override_measured_current(self):
        self.assertTrue(self.led(status=0x0B))
        self.assertFalse(self.led(mv=4200, ua=0, status=0x09))

    def test_usb_removed_and_battery_absent(self):
        self.assertFalse(self.led(bus=0))
        self.assertFalse(self.led(mv=0))
        self.assertFalse(self.led(mv=1999))
        self.assertFalse(self.led(mv=4200, ua=0))
        self.assertFalse(self.led(ua=-50000))

    def test_fault_pause_and_invalid_measurement_clear_indicator(self):
        self.assertFalse(self.led(valid=False))
        for error in (1, 2, 4, 8, 16, 32, 64):
            with self.subTest(error=error):
                self.assertFalse(self.led(error=error))
        self.assertFalse(self.led(status=0x49))
        for bus in (0x25, 0x29, 0x31):
            with self.subTest(bus=bus):
                self.assertFalse(self.led(bus=bus))

    def test_current_limited_usb_can_still_charge(self):
        self.assertTrue(self.led(bus=0x23, ua=15000))

    def test_threshold_tracks_configured_current(self):
        self.assertTrue(self.led(limit=200000, ua=20000))
        self.assertFalse(self.led(limit=200000, ua=19999))
        self.assertFalse(self.led(limit=0))
