#!/usr/bin/env python3
"""Verify and optionally flash an explicitly selected prebuilt XIAO nRF54LM20A Sense HEX.

No compilation, key rotation, downloads, or hardware access in the default mode.
Requires an installed Seeed platform and its PlatformIO OpenOCD 3.1200.x package.
The vendor write/verify/reset sequence was exercised on one board on 2026-09-18;
see docs/12-first-flash.md for evidence and remaining acceptance tests.
Based on the exact vendor target/loader at commit
1ec1287f8e4bc4067a6fd593991e36875aef989f; see docs/Hermes-Voice-Hardware-Guide.docx.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
IMAGE = 'Hermes-Voice-XIAO-nRF54LM20A-Sense.hex'
BOARD_CONFIG_BLOB = 'bf7c2d86b5ca890d8d59b7b0cea1b507e2601459'
OPENOCD_CONFIG_BLOB = '9f457875cbc2f1ddc73819c3badd2cfdac102278'
CONFIRM = 'FLASH XIAO nRF54LM20A SENSE'


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_blob(path: Path) -> str:
    # Git may have checked out CRLF on Windows. Compare original normalized text.
    data = path.read_bytes().replace(b'\r\n', b'\n')
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def check_firmware(root: Path, folder: Path) -> Path:
    folder=folder.resolve()
    manifest = folder / 'SHA256SUMS.txt'
    require(manifest.is_file(), 'Missing firmware SHA256SUMS.txt; extract the entire kit.')
    names = set()
    for line in manifest.read_text().splitlines():
        if not line.strip():
            continue
        digest, name = line.split(None, 1)
        name = name.lstrip(' *')
        path = (folder / name).resolve()
        require(path.is_relative_to(folder.resolve()), 'Unsafe firmware manifest path.')
        require(path.is_file() and sha256(path) == digest, 'Firmware checksum mismatch: ' + name)
        names.add(name)
    require({IMAGE, 'generated-zephyr.dts', 'generated-zephyr.config', 'build-result.json'} <= names,
            'The firmware manifest does not cover the required image and configuration.')
    result = json.loads((folder / 'build-result.json').read_text())
    require(result.get('complete') is True and result.get('target') in ('all','firmware'),
            'The firmware compiler build is incomplete.')
    spec = importlib.util.spec_from_file_location('hvb_dts_check', root / 'tools/check_firmware_dts.py')
    require(spec is not None and spec.loader is not None, 'Missing device-tree checker.')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.validate((folder / 'generated-zephyr.dts').read_text(),
                    (folder / 'generated-zephyr.config').read_text())
    return folder / IMAGE


def find_platform(core: Path, explicit: Path | None = None) -> Path:
    candidates = [explicit] if explicit else sorted((core / 'platforms').glob('*'))
    found = []
    for candidate in candidates:
        if candidate is None:
            continue
        cfg = candidate / 'builder/board_build/nrf/nrf54lm20a.cfg'
        board = candidate / 'boards/seeed-xiao-nrf54lm20a.json'
        if cfg.is_file() and board.is_file():
            if git_blob(cfg) == OPENOCD_CONFIG_BLOB and git_blob(board) == BOARD_CONFIG_BLOB:
                found.append(candidate)
    require(bool(found), 'Exact vendor board/loader configuration not found. Install the pinned Seeed platform described in docs/02-firmware.md.')
    return found[0]


def find_openocd(core: Path, explicit: Path | None = None) -> tuple[Path, Path]:
    root = explicit or core / 'packages/tool-openocd'
    meta = root / 'package.json'
    require(meta.is_file(), 'Install platformio/tool-openocd@~3.1200.0, or provide --openocd-package PATH.')
    version = str(json.loads(meta.read_text()).get('version', ''))
    require(version.startswith('3.1200.'), 'OpenOCD package differs from the vendor 3.1200.x baseline; review before using it.')
    program = root / 'bin' / ('openocd.exe' if os.name == 'nt' else 'openocd')
    script_roots = [root / 'openocd/scripts', root / 'scripts', root / 'share/openocd/scripts']
    scripts = next((p for p in script_roots if (p / 'interface/cmsis-dap.cfg').is_file()), None)
    require(program.is_file() and scripts is not None, 'Incomplete OpenOCD package or missing CMSIS-DAP scripts.')
    return program, scripts


def tcl_path(path: Path) -> str:
    text = path.resolve().as_posix()
    require(not any(c in text for c in '{}\r\n'), 'Move the kit to a folder without braces or newline characters.')
    return '{' + text + '}'


def upload_command(program: Path, scripts: Path, platform: Path, image: Path) -> list[str]:
    # Vendor config already selects CMSIS-DAP/SWD. No separate interface is needed.
    # Explicitly REMOVE the vendor examine-fail auto-mass-erase hook before init.
    return [str(program), '-s', str(scripts),
            '-f', str(platform / 'builder/board_build/nrf/nrf54lm20a.cfg'),
            '-c', 'gdb_port disabled', '-c', 'tcl_port disabled', '-c', 'telnet_port disabled',
            '-c', 'nrf54lm20a.cpu configure -event examine-fail {}',
            '-c', 'init', '-c', 'reset halt',
            '-c', 'nrf54lm20a-load ' + tcl_path(image),
            '-c', 'verify_image ' + tcl_path(image),
            '-c', 'reset run', '-c', 'shutdown']


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',type=Path,required=True,help='Exact timestamped dist folder containing image, configuration and hashes')
    parser.add_argument('--flash', action='store_true', help='After typed confirmation, overwrite the connected recorder application and verify it.')
    parser.add_argument('--core-dir', type=Path, default=Path(os.environ.get('PLATFORMIO_CORE_DIR', str(Path.home() / '.platformio'))))
    parser.add_argument('--platform', type=Path, help='Installed Seeed platform directory, only needed for nonstandard locations.')
    parser.add_argument('--openocd-package', type=Path, help='Installed tool-openocd directory, only needed for nonstandard locations.')
    args = parser.parse_args(argv)
    image = check_firmware(ROOT,args.bundle)
    print('Firmware image, hashes, and generated DTS/Kconfig checks passed.')
    print('This is a static check, not a physical microphone/charger/Bluetooth test.')
    if not args.flash:
        print('CHECK ONLY: no software downloaded, no device accessed, nothing flashed.')
        return 0
    platform = find_platform(args.core_dir, args.platform)
    program, scripts = find_openocd(args.core_dir, args.openocd_package)
    command = upload_command(program, scripts, platform, image)
    print('\nDisconnect the LiPo and all other debug probes. Connect ONLY the exact XIAO nRF54LM20A Sense by a USB data cable, with its antenna attached.')
    print('This replaces its application. On first boot the application claims external flash for its recordings; preserve any previous valuable data first.')
    print('Automatic mass erase/recovery is disabled. Stop and diagnose a locked/unrecognized board; do not substitute a different target.')
    print('The vendor write/verify/reset sequence passed on one board; see docs/12-first-flash.md. Functional acceptance is still required.')
    print('Command: ' + subprocess.list2cmdline(command))
    if not sys.stdin.isatty():
        raise ValueError('Flashing requires an interactive terminal; confirmation cannot be piped.')
    if input('Type ' + CONFIRM + ' to proceed: ').strip() != CONFIRM:
        print('Cancelled; no hardware accessed.')
        return 1
    env = dict(os.environ, OPENOCD_INTERFACE='cmsis-dap')
    subprocess.run(command, check=True, env=env, timeout=300)
    print('Uploader exited successfully after image verification and reset. Now perform the USB-only acceptance tests; do not attach the battery yet.')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, subprocess.SubprocessError, EOFError) as exc:
        print('STOP: ' + str(exc), file=sys.stderr)
        raise SystemExit(2)
