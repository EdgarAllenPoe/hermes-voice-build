"""HVB1 wire container and independent 20 ms IMA-ADPCM frames.
This is NOT a WAV/IMA block format. Decode to PCM WAV before other tools use it.
All integer fields are little endian; UUID bytes use canonical network ordering.
"""
from __future__ import annotations
import struct, uuid, zlib, wave
from dataclasses import dataclass
from pathlib import Path
HEADER_SIZE=64
FRAME_SAMPLES=320
FRAME_BYTES=164
MAX_FRAMES=3000
MAX_FILE=HEADER_SIZE+FRAME_BYTES*MAX_FRAMES
STEPS=(7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767)
INDEX=(-1,-1,-1,-1,2,4,6,8)
@dataclass(frozen=True)
class Header:
    message_id: str
    samples: int
    payload_bytes: int
    crc32: int
    flags: int
    uptime_ms: int
    sequence: int

def read_header(data: bytes) -> Header:
    if len(data)<64 or data[:4]!=b'HVB1': raise ValueError('Not an HVB1 container')
    codec,channels,header,rate,samples,size,crc=struct.unpack_from('<BBHIIII',data,4)
    if (codec,channels,header,rate)!=(1,1,64,16000): raise ValueError('Unsupported audio format')
    if not 0<samples<=960000 or samples%320: raise ValueError('Invalid sample count')
    if size!=samples//320*164: raise ValueError('Invalid payload size')
    flags,uptime,seq=struct.unpack_from('<IIQ',data,40)
    if flags & ~3 or data[56:64]!=bytes(8): raise ValueError('Unsupported flags/reserved fields')
    mid=uuid.UUID(bytes=data[24:40])
    if mid.int==0: raise ValueError('Zero message ID')
    return Header(str(mid),samples,size,crc,flags,uptime,seq)

def validate(data: bytes) -> Header:
    h=read_header(data)
    if len(data)!=64+h.payload_bytes or len(data)>MAX_FILE: raise ValueError('Truncated or oversized recording')
    if zlib.crc32(data[64:]) & 0xffffffff != h.crc32: raise ValueError('Audio checksum mismatch')
    for start in range(64,len(data),164):
        if data[start+2]>88 or data[start+3]!=0 or data[start+163]&0xf0:
            raise ValueError('Malformed ADPCM frame')
    return h

def decode_frame(frame: bytes) -> list[int]:
    if len(frame)!=164 or frame[2]>88 or frame[3]!=0 or frame[-1]&0xf0:
        raise ValueError('Malformed ADPCM frame')
    pred,index=struct.unpack_from('<hB',frame)
    out=[pred]
    for i in range(319):
        nib=(frame[4+i//2] >> (4*(i%2))) & 15
        step=STEPS[index];delta=step>>3
        if nib&1: delta+=step>>2
        if nib&2: delta+=step>>1
        if nib&4: delta+=step
        pred=max(-32768,min(32767,pred+(-delta if nib&8 else delta)))
        index=max(0,min(88,index+INDEX[nib&7]));out.append(pred)
    return out

def encode_frame(samples: list[int]) -> bytes:
    if len(samples)!=320 or any(not -32768<=v<=32767 for v in samples):
        raise ValueError('A frame needs 320 signed 16-bit samples')
    out=bytearray(164);struct.pack_into('<hBB',out,0,samples[0],0,0)
    pred=samples[0];index=0
    for i,sample in enumerate(samples[1:]):
        delta=sample-pred;nib=8 if delta<0 else 0;delta=abs(delta)
        step=STEPS[index];recon=step>>3
        if delta>=step: nib|=4;delta-=step;recon+=step
        if delta>=step>>1: nib|=2;delta-=step>>1;recon+=step>>1
        if delta>=step>>2: nib|=1;recon+=step>>2
        pred=max(-32768,min(32767,pred+(-recon if nib&8 else recon)))
        index=max(0,min(88,index+INDEX[nib&7]))
        out[4+i//2]|=nib << (4*(i%2))
    return bytes(out)

def make_container(pcm: list[int], mid: str|None=None, flags: int=0) -> bytes:
    if not pcm: raise ValueError('No audio')
    pcm=list(pcm)+[0]*((-len(pcm))%320)
    if len(pcm)>960000: raise ValueError('Audio exceeds 60 seconds')
    payload=b''.join(encode_frame(pcm[i:i+320]) for i in range(0,len(pcm),320))
    hdr=bytearray(64);hdr[:4]=b'HVB1'
    struct.pack_into('<BBHIIII',hdr,4,1,1,64,16000,len(pcm),len(payload),zlib.crc32(payload)&0xffffffff)
    hdr[24:40]=uuid.UUID(mid).bytes if mid else uuid.uuid4().bytes
    struct.pack_into('<IIQ',hdr,40,flags,0,1)
    data=bytes(hdr)+payload;validate(data);return data

def to_wav(data: bytes, path: str|Path) -> Header:
    h=validate(data)
    with wave.open(str(path),'wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(16000)
        for start in range(64,len(data),164):
            f.writeframesraw(struct.pack('<320h',*decode_frame(data[start:start+164])))
    return h
