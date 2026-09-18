"""Regression checks for the first-board Bluetooth settings mount failure."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('dts_checker', ROOT/'tools/check_firmware_dts.py')
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class NvsGeometryTests(unittest.TestCase):
    def settings(self, page=4096, multiple=1, count=8):
        return dict(CONFIG_SPI_NOR_FLASH_LAYOUT_PAGE_SIZE=str(page),
                    CONFIG_SETTINGS_NVS_SECTOR_SIZE_MULT=str(multiple),
                    CONFIG_SETTINGS_NVS_SECTOR_COUNT=str(count))

    def test_working_pairing_partition(self):
        CHECK.validate_nvs_geometry(self.settings(), 0x80000)

    def test_vendor_64k_default_is_rejected_before_flashing(self):
        with self.assertRaisesRegex(ValueError, '4096-byte'):
            CHECK.validate_nvs_geometry(self.settings(page=65536), 0x80000)

    def test_sector_multiplier_cannot_overflow_nvs_field(self):
        with self.assertRaisesRegex(ValueError, '16-bit'):
            CHECK.validate_nvs_geometry(self.settings(multiple=16), 0x80000)

    def test_invalid_sector_geometry(self):
        for multiple in (0, -1, 3):
            with self.subTest(multiple=multiple), self.assertRaises(ValueError):
                CHECK.validate_nvs_geometry(self.settings(multiple=multiple), 0x80000)

    def test_storage_requires_gc_space_and_partition_bounds(self):
        for count in (0, 1, 129):
            with self.subTest(count=count), self.assertRaises(ValueError):
                CHECK.validate_nvs_geometry(self.settings(count=count), 0x80000)

    def test_missing_configuration_is_rejected(self):
        with self.assertRaises(ValueError):
            CHECK.validate_nvs_geometry({}, 0x80000)
