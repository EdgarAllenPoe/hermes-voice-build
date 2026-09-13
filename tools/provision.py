#!/usr/bin/env python3
"""Generate a private BLE pairing code locally; never ships a universal passkey."""
import argparse, os, secrets
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--force',action='store_true',help='rotate code; re-pair afterward');a=p.parse_args()
r=Path(__file__).resolve().parents[1];header=r/'firmware/src/device_config.h';folder=r/'config/private';folder.mkdir(parents=True,exist_ok=True,mode=0o700)
card=folder/'pairing-card.txt'
if card.exists() and not a.force: p.error('Already provisioned. Keep existing code, or explicitly use --force to rotate it.')
os.umask(0o077);code=100000+secrets.randbelow(900000)
header.write_text(f'#ifndef HVB_DEVICE_CONFIG_H\n#define HVB_DEVICE_CONFIG_H\n/* Private generated value: do not publish. */\n#define HVB_PAIRING_CODE {code}U\n#endif\n')
header.chmod(0o600)
card.write_text(f'Hermes Voice Button — PRIVATE PAIRING CARD\nBLE passkey: {code}\nHold recorder button 1.5 seconds to open a 60-second pairing window.\nEnter this passkey in Android Bluetooth pairing.\nA 10-second hold erases the old Bluetooth bond, not recordings.\nThis is NOT the server bearer token.\n')
card.chmod(0o600);print(f'Generated firmware configuration and {card}. Print the card privately; do not post it in chat.')
