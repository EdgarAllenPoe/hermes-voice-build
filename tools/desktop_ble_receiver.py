#!/usr/bin/env python3
"""Optional Linux BLE bench receiver. Requires Bleak, not used by the Android APK.
Pair/bond through bluetoothctl first. Stop the phone relay while bench testing.
ACK only follows full validation and file+directory fsync on this computer.
"""
import argparse, asyncio, os, struct, sys, uuid
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'receiver'))
from hvbridge.audio import validate,MAX_FILE
U=lambda n:f'58ef{n:04x}-35c8-4c31-89aa-81f763051da1'
async def main(address:str,out:Path):
 try:from bleak import BleakClient
 except ImportError:raise SystemExit('Install the optional dependency: python -m pip install bleak (cache it before offline work)')
 out.mkdir(parents=True,exist_ok=True,mode=0o700)
 async with BleakClient(address,timeout=30) as c:
  while True:
   await c.write_gatt_char(U(2),b'\x01',response=True);meta=bytes(await c.read_gatt_char(U(3)))
   if len(meta)!=20:raise ValueError('Invalid metadata')
   mid=str(uuid.UUID(bytes=meta[:16]));size=struct.unpack_from('<I',meta,16)[0]
   if size==0:print('No queued recordings');return
   if not 228<=size<=MAX_FILE:raise ValueError('Invalid length')
   blob=bytearray()
   while len(blob)<size:
    await c.write_gatt_char(U(2),b'\x02'+struct.pack('<I',len(blob)),response=True)
    chunk=bytes(await c.read_gatt_char(U(4)))
    if not 0<len(chunk)<=180 or len(blob)+len(chunk)>size:raise ValueError('Invalid chunk')
    blob+=chunk
   if validate(bytes(blob)).message_id!=mid:raise ValueError('ID mismatch')
   path=out/(mid+'.hvb');tmp=path.with_suffix('.tmp')
   with tmp.open('wb') as f:f.write(blob);f.flush();os.fsync(f.fileno())
   if path.exists() and path.read_bytes()!=blob:raise ValueError('Local ID collision; not acknowledging')
   os.replace(tmp,path);fd=os.open(out,os.O_RDONLY)
   try:os.fsync(fd)
   finally:os.close(fd)
   await c.write_gatt_char(U(2),b'\x03'+uuid.UUID(mid).bytes,response=True);print('Durably received:',path)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('address');p.add_argument('--out',type=Path,default=Path('received'));a=p.parse_args();os.umask(0o077);asyncio.run(main(a.address,a.out))
