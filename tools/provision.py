#!/usr/bin/env python3
"""Generate an ignored private BLE pairing header and its matching local card."""
import argparse
import os
from pathlib import Path
import secrets


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--force', action='store_true', help='rotate code; re-pair afterward')
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    header = root / 'firmware/src/device_config.h'
    folder = root / 'config/private'
    card = folder / 'pairing-card.txt'
    if not args.force:
        if card.exists():
            parser.error('Already provisioned. Keep the existing code, or explicitly use --force to rotate it.')
        if header.exists() and '#error' not in header.read_text(encoding='utf-8'):
            parser.error('A private header exists without its pairing card. Restore the card, or explicitly use --force to rotate it.')
    os.umask(0o077)
    folder.mkdir(parents=True, exist_ok=True, mode=0o700)
    code = 100000 + secrets.randbelow(900000)
    header.write_text(
        '#ifndef HVB_DEVICE_CONFIG_H\n#define HVB_DEVICE_CONFIG_H\n'
        '/* Private generated value: do not publish. */\n'
        f'#define HVB_PAIRING_CODE {code}U\n#endif\n',
        encoding='utf-8', newline='\n')
    header.chmod(0o600)
    card.write_text(
        'Hermes Voice Button - PRIVATE PAIRING CARD\n'
        f'BLE passkey: {code}\n'
        'Hold recorder button 1.5 seconds to open a 60-second pairing window.\n'
        'Enter this passkey in Android Bluetooth pairing.\n'
        'A 10-second hold erases the old Bluetooth bond, not recordings.\n'
        'This is NOT the server bearer token.\n',
        encoding='utf-8', newline='\n')
    card.chmod(0o600)
    print(f'Generated firmware configuration and {card}. Print the card privately; do not post it in chat.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
