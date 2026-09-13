#!/usr/bin/env python3
import argparse, json, struct, sys, wave
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'receiver'))
from hvbridge.audio import make_container,to_wav,validate
p=argparse.ArgumentParser(description='Convert HVB1 to/from 16 kHz mono PCM WAV, or inspect its header.')
s=p.add_subparsers(dest='op',required=True)
for op in ('encode','decode','inspect'):
 q=s.add_parser(op);q.add_argument('input',type=Path)
 if op!='inspect':q.add_argument('output',type=Path)
a=p.parse_args()
try:
 if a.op=='encode':
  with wave.open(str(a.input),'rb') as f:
   if (f.getframerate(),f.getnchannels(),f.getsampwidth(),f.getcomptype())!=(16000,1,2,'NONE'):raise ValueError('Input must be uncompressed 16 kHz mono 16-bit WAV')
   if f.getnframes()>960000:raise ValueError('Input exceeds 60 seconds')
   b=f.readframes(f.getnframes())
  a.output.write_bytes(make_container(list(struct.unpack('<'+'h'*(len(b)//2),b))))
 elif a.op=='decode':to_wav(a.input.read_bytes(),a.output)
 else:print(json.dumps(validate(a.input.read_bytes()).__dict__,indent=2))
except Exception as e:p.exit(1,f'Error: {e}\n')
