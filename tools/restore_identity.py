#!/usr/bin/env python3
"""Restore only the existing app key and recorder pairing pair from a private backup.
Default previews. Never restores the recovery private key or silently rotates identity.
"""
import argparse, hashlib, os, re, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MEMBERS={
    'android/debug.keystore':'config/private/android-debug.keystore',
    'recorder/device_config.h':'firmware/src/device_config.h',
    'recorder/pairing-card.txt':'config/private/pairing-card.txt',
}
def restore(backup,root=ROOT,apply=False):
    root=Path(root).resolve()
    with zipfile.ZipFile(backup) as z:
        names=z.namelist()
        prefixes=[n[:-len('SHA256SUMS.txt')] for n in names if n.endswith('SHA256SUMS.txt')]
        if len(prefixes)!=1:raise ValueError('Backup must have one checksum manifest')
        prefix=prefixes[0]
        checks={}
        for line in z.read(prefix+'SHA256SUMS.txt').decode('utf-8').splitlines():
            digest,name=line.split('  ',1);checks[name.replace('\\','/')]=digest
        blobs={}
        for member,dest in MEMBERS.items():
            data=z.read(prefix+member)
            if hashlib.sha256(data).hexdigest()!=checks.get(member):
                raise ValueError('Backup checksum mismatch for '+member)
            blobs[dest]=data
    header=blobs['firmware/src/device_config.h'].decode('utf-8')
    card=blobs['config/private/pairing-card.txt'].decode('utf-8')
    code=re.search(r'#define HVB_PAIRING_CODE ([0-9]{6})U',header)
    if not code or 'BLE passkey: '+code.group(1) not in card:
        raise ValueError('Backup pairing header and card do not match')
    changes=[]
    for dest,data in blobs.items():
        path=root/dest
        if path.resolve().is_relative_to(root) is False:raise ValueError('Unsafe destination')
        if path.exists() and path.read_bytes()!=data:
            placeholder=dest.endswith('device_config.h') and b'#error' in path.read_bytes()
            if not placeholder:raise ValueError('Existing private identity differs; refusing overwrite: '+dest)
        if not path.exists() or path.read_bytes()!=data:changes.append(dest)
    if apply:
        os.umask(0o077)
        for dest in changes:
            path=root/dest;path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
            tmp=path.with_name(path.name+'.restore-tmp')
            with tmp.open('xb') as f:
                f.write(blobs[dest]);f.flush();os.fsync(f.fileno())
            tmp.chmod(0o600);os.replace(tmp,path)
    return changes
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--backup',type=Path,required=True)
    p.add_argument('--apply',action='store_true')
    a=p.parse_args();changes=restore(a.backup,apply=a.apply)
    print(('Restored' if a.apply else 'Would restore')+' '+str(len(changes))+' private identity files. No secrets displayed.')
if __name__=='__main__':main()
