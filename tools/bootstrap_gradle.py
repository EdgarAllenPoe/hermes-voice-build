#!/usr/bin/env python3
"""Download the fixed official Gradle distribution and verify its published SHA256."""
import hashlib,re,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
VERSION='8.11.1'
def main():
    folder=ROOT/'.tools';folder.mkdir(exist_ok=True)
    url=f'https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip'
    expected=urllib.request.urlopen(url+'.sha256',timeout=30).read(256).decode().strip()
    if not re.fullmatch('[0-9a-fA-F]{64}',expected):raise ValueError('Invalid official checksum')
    archive=folder/f'gradle-{VERSION}-bin.zip'
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest()!=expected.lower():
        urllib.request.urlretrieve(url,archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected.lower():raise ValueError('Gradle checksum mismatch')
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            if not (folder/info.filename).resolve().is_relative_to(folder.resolve()):
                raise ValueError('Unsafe archive member')
        z.extractall(folder)
    print('Verified Gradle '+VERSION+' under '+str(folder))
    print('PowerShell: $env:GRADLE_BIN=(Resolve-Path .tools/gradle-8.11.1/bin/gradle.bat).Path')
if __name__=='__main__':main()
