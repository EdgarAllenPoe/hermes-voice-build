#!/usr/bin/env python3
"""Refresh the public snapshot from the staged Git index after reviewing git add.
Refreshes FILE_INDEX.md and SHA256SUMS.txt; the checksum file excludes itself.
"""
import hashlib,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def git(*args):
    return subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),*args],cwd=ROOT)
def main():
    names=git('ls-files','-z').decode().split('\0')
    names=sorted(n for n in names if n)
    (ROOT/'FILE_INDEX.md').write_text('# Public source file index\n\n'+
        '\n'.join('- '+n for n in names)+'\n',encoding='utf-8',newline='\n')
    subprocess.run(['git','-c','safe.directory='+ROOT.as_posix(),'add','--','FILE_INDEX.md'],cwd=ROOT,check=True)
    lines=[]
    for name in names:
        if name=='SHA256SUMS.txt':continue
        content=git('show',':'+name)
        lines.append(hashlib.sha256(content).hexdigest()+'  '+name)
    (ROOT/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
    print('Updated snapshot for '+str(len(lines))+' staged public files; stage SHA256SUMS.txt next.')
if __name__=='__main__':main()
