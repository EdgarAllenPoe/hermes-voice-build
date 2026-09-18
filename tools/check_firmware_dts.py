#!/usr/bin/env python3
"""Check the generated DTS and Kconfig before publishing a firmware image.
Static checks do not replace USB-only commissioning or battery measurements.
The pinned Seeed BSP removed the unconnected power_en node; BUCK2/vsys_3v3
is the real always-on system supply. See BUILD_NOTES.md for vendor provenance.
"""
import argparse
import re
from pathlib import Path


def validate_nvs_geometry(values: dict[str, str], partition_size: int) -> None:
    page = int(values.get('CONFIG_SPI_NOR_FLASH_LAYOUT_PAGE_SIZE', '-1'), 0)
    multiple = int(values.get('CONFIG_SETTINGS_NVS_SECTOR_SIZE_MULT', '-1'), 0)
    count = int(values.get('CONFIG_SETTINGS_NVS_SECTOR_COUNT', '-1'), 0)
    if page != 4096:
        raise ValueError('SPI NOR layout must use 4096-byte sectors for pairing storage')
    sector = page * multiple
    if multiple < 1 or sector > 65535 or sector & (sector - 1):
        raise ValueError('NVS sector size must be a power of two that fits its 16-bit field')
    if count < 2 or sector * count > partition_size:
        raise ValueError('NVS needs at least two sectors within the settings partition')


def validate(dts: str, config: str) -> None:
    text = re.sub(r'/\*.*?\*/', '', dts, flags=re.S)
    def need(ok, message):
        if not ok:
            raise ValueError(message)
    def leaf(label):
        matches = re.findall(r'\b' + re.escape(label) + r'\s*:\s*[^{}]*\{([^{}]*)\}', text, re.S)
        need(len(matches) == 1, 'Expected one leaf node labelled ' + label)
        return matches[0]
    def cells(block, key):
        m = re.search(r'(?<![\w,-])' + re.escape(key) + r'\s*=\s*<([^>]*)>', block)
        need(m is not None, 'Missing numeric property ' + key)
        return [int(x, 0) for x in m[1].split()]
    def scalar(block, key, expected):
        need(cells(block, key) == [expected], f'{key} must be {expected}')
    chargers = re.findall(r'[^{}]*\{([^{}]*compatible\s*=\s*"nordic,npm1300-charger"[^{}]*)\}', text, re.S)
    need(len(chargers) == 1, 'Expected exactly one charger node')
    charger = chargers[0]
    need(re.search(r'status\s*=\s*"okay"', charger), 'Charger must be active')
    for key, value in {'current-microamp':100000, 'term-microvolt':4200000,
                       'vbus-limit-microamp':500000, 'thermistor-ohms':10000}.items():
        scalar(charger, key, value)
    need('charging-enable;' in charger, 'Charging must be enabled at the verified profile')
    system = leaf('vsys_3v3')
    need('regulator-always-on;' in system, 'System BUCK2 must remain always on')
    for rail in (system, leaf('dmic_vdd')):
        scalar(rail, 'regulator-min-microvolt', 3300000)
        scalar(rail, 'regulator-max-microvolt', 3300000)
    mic = leaf('dmic_vdd')
    need('regulator-boot-on;' not in mic and 'regulator-always-on;' not in mic,
         'Microphone rail must remain application controlled')
    need(cells(leaf('hvb_audio'), 'reg') == [0, 0x780000], 'Audio partition changed')
    need(cells(leaf('hvb_settings'), 'reg') == [0x780000, 0x80000], 'Settings partition changed')
    need(re.search(r'zephyr,settings-partition\s*=\s*&hvb_settings\s*;', text), 'Wrong settings partition selected')
    need(re.search(r'gpios\s*=\s*<\s*&gpio1\s+(?:0x0|0)\s+(?:0x11|17)\s*>', leaf('hvb_button')),
         'Capture button must be P1.00, active low with pull-up')
    for label in ('pdm20', 'py25q64', 'bt_hci_controller'):
        need(re.search(r'\b' + label + r'\s*:', text), 'Missing exact-board label ' + label)
    need('voice-button' in text, 'Missing capture-button alias')
    values = dict(re.findall(r'^(CONFIG_\w+)=(.+)$', config, re.M))
    validate_nvs_geometry(values, cells(leaf('hvb_settings'), 'reg')[1])
    for symbol in ('SENSOR', 'REGULATOR', 'ENTROPY_GENERATOR', 'CSPRNG_ENABLED',
                   'HARDWARE_DEVICE_CS_GENERATOR', 'BT_SMP', 'BT_SMP_SC_ONLY',
                   'BT_FIXED_PASSKEY', 'BT_SMP_APP_PAIRING_ACCEPT', 'BT_SETTINGS',
                   'AUDIO_DMIC', 'SPI_NOR', 'SETTINGS_NVS'):
        need(values.get('CONFIG_' + symbol) == 'y', symbol + ' must be enabled')
    for symbol in ('TEST_RANDOM_GENERATOR', 'TEST_CSPRNG_GENERATOR'):
        need(values.get('CONFIG_' + symbol) != 'y', 'Insecure test random generator selected')
    sensor = int(values.get('CONFIG_SENSOR_INIT_PRIORITY', '-1'))
    common = int(values.get('CONFIG_REGULATOR_NPM13XX_COMMON_INIT_PRIORITY', '-1'))
    regulator = int(values.get('CONFIG_REGULATOR_NPM13XX_INIT_PRIORITY', '-1'))
    need(0 <= sensor < common < regulator, 'Charger init must precede PMIC regulator initialization')
    need(values.get('CONFIG_BT_MAX_CONN') == '1' and values.get('CONFIG_BT_MAX_PAIRED') == '1',
         'Firmware must use the single-phone connection/bond configuration')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dts', type=Path)
    parser.add_argument('--config', type=Path, help='Defaults to .config beside generated zephyr.dts')
    args = parser.parse_args()
    config = args.config or args.dts.parent / '.config'
    try:
        validate(args.dts.read_text(), config.read_text())
    except (OSError, ValueError) as exc:
        parser.exit(1, f'PREFLIGHT FAILED: {exc}\nDo not attach the LiPo until corrected and measured.\n')
    print('Generated DTS/Kconfig checks passed: 100 mA / 4.20 V charger, 3.3 V rails, partition ranges, NVS sector geometry, P1.00 button, secure RNG/BLE, and charger-before-regulator initialization.')
    print('This is not a physical charger, microphone, Bluetooth, or battery test.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
