#!/usr/bin/env python3
"""Verify the downloaded bundle before editing or provisioning it."""
import hashlib,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1];manifest=root/'SHA256SUMS.txt';bad=[];count=0
for line in manifest.read_text().splitlines():
 digest,name=line.split('  ',1);path=root/name;count+=1
 if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:bad.append(name)
if bad:print('Changed or missing files:\n'+'\n'.join(bad));sys.exit(1)
print(f'Verified {count} files. Expected changes after provisioning or editing will invalidate these original hashes.')
