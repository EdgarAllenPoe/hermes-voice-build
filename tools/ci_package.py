#!/usr/bin/env python3
"""Publish verified binaries only; encrypt pairing material and signing keys.
The recipient PRIVATE key is never uploaded to the runner or repository.
"""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, tempfile, zipfile

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('target', choices=('android', 'firmware'))
    a = p.parse_args()
    out = ROOT / 'publish' / a.target
    out.mkdir(parents=True, exist_ok=True)
    records = sorted((ROOT / 'dist').glob('*/build-result.json'))
    result = json.loads(records[-1].read_text()) if records else {'target': a.target, 'complete': False, 'hardware_tested': False, 'error': 'Build did not reach artifact collection.'}
    result['source_commit'] = os.environ.get('GITHUB_SHA', 'local')
    result['run_id'] = os.environ.get('GITHUB_RUN_ID', 'local')
    (out / 'build-result.json').write_text(json.dumps(result, indent=2) + '\n')
    secrets = []
    header = ROOT / 'firmware/src/device_config.h'
    if header.exists():
        match = re.search(r'#define HVB_PAIRING_CODE ([0-9]{6})U', header.read_text())
        if match:
            secrets.append(match.group(1))
    # Sanitize saved build logs as well as Actions' separately masked log stream.
    for source in (ROOT / 'build-logs').glob('*/*.txt'):
        text = source.read_text(errors='replace')
        for secret in secrets:
            text = text.replace(secret, '[PRIVATE PASSKEY REDACTED]')
        dest = out / 'logs' / source.parent.name / source.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(text)
    with tempfile.TemporaryDirectory() as td:
        archive = Path(td) / 'private.zip'
        private_count = 0
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            if a.target == 'firmware':
                if records and result.get('complete'):
                    for f in records[-1].parent.iterdir():
                        if f.is_file():
                            z.write(f, 'firmware/' + f.name)
                            private_count += 1
                for f in (header, ROOT / 'config/private/pairing-card.txt'):
                    if f.is_file() and secrets:
                        z.write(f, 'private/' + f.name)
                        private_count += 1
                build = ROOT / 'firmware/.pio/build/seeed-xiao-nrf54lm20a'
                for pattern in ('zephyr.dts', '.config'):
                    for f in build.rglob(pattern):
                        z.write(f, 'diagnostics/' + str(f.relative_to(build)))
                        private_count += 1
            else:
                if records and result.get('complete'):
                    for f in records[-1].parent.iterdir():
                        if f.is_file() and f.suffix == '.apk':
                            shutil.copy2(f, out / f.name)
                key = Path(os.environ.get('HVB_DEBUG_KEYSTORE', str(Path.home() / '.android/debug.keystore')))
                if result.get('complete') and not key.is_file():
                    raise RuntimeError('Verified APK lacks its private signing-key backup; refusing publication')
                if key.is_file():
                    z.write(key, 'android/debug.keystore')
                    private_count += 1
            z.writestr('build-result.json', json.dumps(result, indent=2) + '\n')
            z.writestr('README.txt', 'PRIVATE build material. Do not publish. Firmware passkeys and Android signing keys must be retained securely. A complete build is not a hardware acceptance test.\n')
        if private_count:
            dest = out / ('PRIVATE-' + a.target + '.cms')
            subprocess.run(['openssl', 'cms', '-encrypt', '-binary', '-aes-256-cbc', '-in', str(archive), '-outform', 'DER', '-out', str(dest), str(ROOT / 'config/build-recipient.crt.pem')], check=True)
    # Tracked files only: never include generated secrets or build-directory content.
    if (ROOT / '.git').exists():
        subprocess.run(['git', 'archive', '--format=zip', '-o', str(out / 'source-for-run.zip'), 'HEAD'], cwd=ROOT, check=True)
    files = sorted(f for f in out.rglob('*') if f.is_file() and f.name != 'SHA256SUMS.txt')
    (out / 'SHA256SUMS.txt').write_text(''.join(digest(f) + '  ' + str(f.relative_to(out)) + '\n' for f in files))
    print('Packaged ' + a.target + '; complete=' + str(result.get('complete', False)))

if __name__ == '__main__':
    main()
