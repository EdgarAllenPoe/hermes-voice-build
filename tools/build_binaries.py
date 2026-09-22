#!/usr/bin/env python3
"""Build real APK/firmware outputs with installed vendor tools; never emits placeholders.
Requires Python 3.10+, JDK 17+, Gradle 8.11.1, Android SDK 36/build-tools
35.0.0, and PlatformIO 6.1.16+ plus Git. First builds require Internet.
No device is flashed, app installed, or receiver started by this script.
"""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BOARD = 'seeed-xiao-nrf54lm20a'
FIRMWARE_VERSION = '0.5.0'

def android_version() -> str:
    text = (ROOT / 'android/app/build.gradle').read_text(encoding='utf-8')
    match = re.search(r'versionName\s+[\"\']([^\"\']+)[\"\']', text)
    if not match:
        raise ValueError('Android versionName is missing')
    return match.group(1)



def find_executable(name: str, candidates: list[Path]) -> str | None:
    found = shutil.which(name)
    if found:
        return found
    for item in candidates:
        if item.is_file():
            return str(item)
    return None


def tools_for(target: str) -> tuple[dict[str, str], list[str]]:
    win = os.name == 'nt'
    tools: dict[str, str] = {}
    missing: list[str] = []
    if target in ('all', 'android'):
        java = find_executable('java', [Path(os.environ.get('JAVA_HOME', '/not-set')) / 'bin' / ('java.exe' if win else 'java')])
        gradle = os.environ.get('GRADLE_BIN') or find_executable('gradle', [ROOT / '.tools' / 'gradle-8.11.1' / 'bin' / ('gradle.bat' if win else 'gradle')])
        sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or
                   (str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android' / 'Sdk') if win else str(Path.home() / 'Android' / 'Sdk')))
        signer = sdk / 'build-tools' / '35.0.0' / ('apksigner.bat' if win else 'apksigner')
        if not java:
            missing.append('JDK 17+ (java not found; set JAVA_HOME/PATH).')
        else:
            tools['java'] = java
        if not gradle or not Path(gradle).is_file():
            missing.append('Gradle 8.11.1 (install in .tools or set GRADLE_BIN).')
        else:
            tools['gradle'] = gradle
        if not (sdk / 'platforms' / 'android-36' / 'android.jar').is_file():
            missing.append('Android SDK Platform 36 (set ANDROID_HOME).')
        if not signer.is_file():
            missing.append('Android SDK Build-Tools 35.0.0 (apksigner not found).')
        tools['sdk'] = str(sdk)
        tools['apksigner'] = str(signer)
    if target in ('all', 'firmware'):
        pio = find_executable('pio', [ROOT / '.tools' / 'pio' / ('Scripts/pio.exe' if win else 'bin/pio'),
                                     Path.home() / '.platformio' / 'penv' / ('Scripts/pio.exe' if win else 'bin/pio')])
        if not pio:
            missing.append('PlatformIO Core 6.1.16+ (pio not found).')
        else:
            tools['pio'] = pio
        if not shutil.which('git'):
            missing.append('Git (needed to retrieve the Seeed board support package).')
    return tools, missing


def run(command: list[str], log: Path, env: dict[str, str] | None = None) -> None:
    """Stream compiler diagnostics to the terminal and preserve the same build log."""
    print('\nRunning: ' + subprocess.list2cmdline(command), flush=True)
    log.parent.mkdir(parents=True, exist_ok=True)
    child_env = dict(os.environ if env is None else env)
    child_env.update(PYTHONUTF8='1', PYTHONIOENCODING='utf-8')
    if os.name == 'nt':
        # Scope long-path support to these builds, including vendor Git clones.
        count = int(child_env.get('GIT_CONFIG_COUNT', '0'))
        child_env[f'GIT_CONFIG_KEY_{count}'] = 'core.longpaths'
        child_env[f'GIT_CONFIG_VALUE_{count}'] = 'true'
        child_env['GIT_CONFIG_COUNT'] = str(count + 1)
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen(command, cwd=ROOT, env=child_env, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        assert proc.stdout is not None
        for line in proc.stdout:
            print(line, end='', flush=True)
            output.write(line)
        proc.stdout.close()
        code = proc.wait()
    if code:
        raise RuntimeError(f'Command failed with exit {code}; see {log}')


def verify_hex(path: Path) -> int:
    """Validate Intel HEX checksums/record structure and require real data + EOF."""
    data_bytes = 0
    eof = False
    for number, line in enumerate(path.read_text(encoding='ascii').splitlines(), 1):
        if not line.strip():
            continue
        if eof or not line.startswith(':'):
            raise ValueError(f'Invalid HEX record at line {number}')
        raw = bytes.fromhex(line[1:])
        if len(raw) < 5 or len(raw) != raw[0] + 5 or sum(raw) % 256:
            raise ValueError(f'HEX length/checksum error at line {number}')
        kind = raw[3]
        if kind == 0:
            data_bytes += raw[0]
        elif kind == 1:
            if raw[0] != 0 or raw[1:3] != b'\0\0':
                raise ValueError('Invalid EOF record')
            eof = True
        elif kind in (2, 4):
            if raw[0] != 2:
                raise ValueError('Invalid extended-address record')
        elif kind in (3, 5):
            if raw[0] != 4:
                raise ValueError('Invalid start-address record')
        else:
            raise ValueError(f'Unknown HEX record type {kind}')
    if not eof or not data_bytes:
        raise ValueError('HEX contains no program data or has no EOF')
    return data_bytes


def verify_elf(path: Path) -> None:
    header = path.read_bytes()[:52]
    if len(header) < 52 or header[:4] != b'\x7fELF' or header[4:6] != b'\x01\x01':
        raise ValueError('Expected a 32-bit little-endian ELF file')
    if int.from_bytes(header[18:20], 'little') != 40:
        raise ValueError('ELF is not an ARM target')


def verify_apk_structure(path: Path) -> None:
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
        if not {'AndroidManifest.xml', 'classes.dex'} <= names:
            raise ValueError('APK is missing the compiled manifest or DEX')
        if apk.read('AndroidManifest.xml')[:4] != b'\x03\x00\x08\x00':
            raise ValueError('APK manifest is not compiled Android binary XML')
        if not apk.read('classes.dex').startswith(b'dex\n'):
            raise ValueError('APK classes.dex is not a DEX file')
        if apk.testzip():
            raise ValueError('APK archive has a CRC failure')


def build_android(tools: dict[str, str], out: Path, logs: Path, offline: bool) -> None:
    env = dict(os.environ, ANDROID_HOME=tools['sdk'])
    if env.get('HVB_CI_BUILD') != '1':
        key = Path(env.get('HVB_DEBUG_KEYSTORE') or str(ROOT/'config/private/android-debug.keystore')).expanduser()
        if not key.is_file():
            raise RuntimeError('Personal signing key is missing. Restore the selected backup with tools/restore_identity.py; do not generate a replacement for an installed app.')
        env['HVB_DEBUG_KEYSTORE'] = str(key.resolve())
    # Java in JAVA_HOME must also be visible to Gradle's launcher.
    env['PATH'] = str(Path(tools['java']).parent) + os.pathsep + env.get('PATH', '')
    run([tools['java'], '-version'], logs / 'java-version.txt', env)
    run([tools['gradle'], '--version'], logs / 'gradle-version.txt', env)
    command = [tools['gradle'], '-p', str(ROOT / 'android'), '--no-daemon']
    if offline:
        command.append('--offline')
    # Personal builds retain the selected signing key; CI uses its isolated identity.
    command += [':app:clean', ':app:assembleDebug', ':app:lintDebug']
    try:
        run(command, logs / 'android-build.txt', env)
    finally:
        lint = ROOT/'android/app/build/reports/lint-results-debug.txt'
        if lint.is_file():
            shutil.copy2(lint,logs/'android-lint.txt')
            text=lint.read_text(encoding='utf-8',errors='replace')
            if 'Error:' in text:
                print(text[:40000])

    apk = ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk'
    verify_apk_structure(apk)
    run([tools['apksigner'], 'verify', '--verbose', '--print-certs', str(apk)], logs / 'apk-signature.txt', env)
    shutil.copy2(apk, out / ('Hermes-Voice-' + android_version() + ('-ci.apk' if env.get('HVB_CI_BUILD')=='1' else '-test.apk')))
    # Retain the selected personal keystore for compatible future updates.


def build_firmware(tools: dict[str, str], out: Path, logs: Path, offline: bool) -> None:
    if offline:
        raise RuntimeError('Firmware offline mode is not certified. Build once online, freeze/cache the toolchain, and verify offline rebuilding before relying on it.')
    header = ROOT / 'firmware/src/device_config.h'
    card = ROOT / 'config/private/pairing-card.txt'
    if not header.is_file() or '#error' in header.read_text(encoding='utf-8'):
        run([sys.executable, str(ROOT / 'tools/provision.py')], logs / 'provision.txt')
    elif not card.is_file():
        raise RuntimeError('A private firmware code exists but its pairing card is missing; restore the card rather than rotating an unknown existing code.')
    definition = re.search(r'#define HVB_PAIRING_CODE ([0-9]{6})U',header.read_text(encoding='utf-8'))
    printed = re.search(r'BLE passkey: ([0-9]{6})',card.read_text(encoding='utf-8'))
    if not definition or not printed or definition.group(1)!=printed.group(1):
        raise RuntimeError('Pairing header and card do not match; restore the matching pair before building.')
    run([tools['pio'], '--version'], logs / 'platformio-version.txt')
    run([tools['pio'], 'run', '-d', str(ROOT / 'firmware'), '-e', BOARD, '-t', 'clean'], logs / 'firmware-clean.txt')
    run([tools['pio'], 'run', '-d', str(ROOT / 'firmware'), '-e', BOARD], logs / 'firmware-build.txt')
    build = ROOT / 'firmware/.pio/build' / BOARD
    dts = list(build.rglob('zephyr.dts'))
    if len(dts) != 1:
        raise RuntimeError('Cannot unambiguously identify generated zephyr.dts; inspect the build before flashing.')
    run([sys.executable, str(ROOT / 'tools/check_firmware_dts.py'), str(dts[0])], logs / 'charger-dts-check.txt')
    elf, hexfile, binary = (build / ('firmware.' + suffix) for suffix in ('elf', 'hex', 'bin'))
    for path in (elf, hexfile):
        if not path.is_file() or not path.stat().st_size:
            raise RuntimeError(f'Expected output not found: {path}. Inspect the actual vendor build output; do not rename source files.')
    verify_elf(elf)
    verify_hex(hexfile)
    if not binary.is_file():
        # This pinned Nordic PlatformIO build emits ELF and HEX by default.
        # Convert the actual linked ARM ELF with its installed vendor objcopy;
        # never create a placeholder or rename source/text as a raw image.
        packages = Path(os.environ.get('PLATFORMIO_PACKAGES_DIR') or
                        str(Path(os.environ.get('PLATFORMIO_CORE_DIR') or str(Path.home() / '.platformio')) / 'packages'))
        name = 'arm-none-eabi-objcopy.exe' if os.name == 'nt' else 'arm-none-eabi-objcopy'
        objcopy = packages / 'toolchain-gccarmnoneeabi' / 'bin' / name
        if not objcopy.is_file():
            raise RuntimeError(f'Installed ARM objcopy not found: {objcopy}; cannot derive the optional raw image safely.')
        run([str(objcopy), '--version'], logs / 'arm-objcopy-version.txt')
        run([str(objcopy), '-O', 'binary', str(elf), str(binary)], logs / 'firmware-bin-conversion.txt')
    if not binary.is_file() or not binary.stat().st_size:
        raise RuntimeError('The linked ELF did not produce a nonempty raw firmware image.')
    for path in (elf, hexfile, binary):
        shutil.copy2(path, out / ('Hermes-Voice-XIAO-nRF54LM20A-Sense.' + path.suffix[1:]))
    shutil.copy2(dts[0], out / 'generated-zephyr.dts')
    configs = list(build.rglob('.config'))
    if len(configs) == 1:
        shutil.copy2(configs[0], out / 'generated-zephyr.config')
    shutil.copy2(card, out / 'PRIVATE-pairing-card.txt')
    run([tools['pio'], 'pkg', 'list', '-d', str(ROOT / 'firmware')], logs / 'firmware-package-versions.txt')


def main() -> int:
    # PlatformIO prints Unicode dependency trees even when stdout is a pipe.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, 'reconfigure'):
            stream.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=('all', 'android', 'firmware'), default='all')
    parser.add_argument('--check', action='store_true', help='Check tools only; does not build or change device configuration')
    parser.add_argument('--offline', action='store_true', help='Use cached Android dependencies only; firmware is deliberately not assumed offline-ready')
    args = parser.parse_args()
    if sys.version_info < (3, 10):
        parser.error('Python 3.10+ is required')
    tools, missing = tools_for(args.target)
    if missing:
        print('BUILD BLOCKED: missing prerequisites\n' + '\n'.join('- ' + m for m in missing))
        print('No new APK or firmware binary has been produced.')
        return 2
    if args.check:
        print('Tool paths found. This is not a compile, dependency-cache validation, or hardware test.')
        return 0
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    out = ROOT / 'dist' / stamp
    logs = ROOT / 'build-logs' / stamp
    out.mkdir(parents=True)
    logs.mkdir(parents=True)
    result = {'created_utc': stamp, 'target': args.target, 'complete': False,
              'hardware_tested': False, 'version':FIRMWARE_VERSION if args.target=='firmware' else android_version(),
              'component_versions': {'android':android_version(),'firmware':FIRMWARE_VERSION}, 'identity_profile':'ci-disposable' if os.environ.get('HVB_CI_BUILD')=='1' else 'personal', 'endpoint': 'http://100.99.200.55:8765/v1/voice', 'completed_stages': []}
    code = 1
    try:
        if args.target in ('all', 'android'):
            build_android(tools, out, logs, args.offline)
            result['completed_stages'].append('android-build-and-signature-verification')
        if args.target in ('all', 'firmware'):
            build_firmware(tools, out, logs, args.offline)
            result['completed_stages'].append('firmware-build-and-static-dts-check')
        result['complete'] = True
        code = 0
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as exc:
        result['error'] = str(exc)
        print('\nBUILD FAILED: ' + str(exc), file=sys.stderr)
    finally:
        (out / 'build-result.json').write_text(json.dumps(result, indent=2) + '\n')
        files = sorted(p for p in out.iterdir() if p.is_file())
        (out / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in files))
        print(f'Build record: {out}\nLogs: {logs}')
        print('Compiled does not mean bench-tested. Use USB power first; verify charging before attaching the LiPo.')
    return code


if __name__ == '__main__':
    raise SystemExit(main())
