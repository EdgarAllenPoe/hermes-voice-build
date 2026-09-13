#!/usr/bin/env python3
"""Compare original and HVB1-compressed speech using local whisper.cpp.
No network access, upload, receiver invocation, or Hermes execution.
Inputs and generated reports may contain private speech; keep them out of Git.
"""
import argparse, hashlib, json, os, re, shutil, struct, subprocess, sys, time, wave
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'receiver'))
from hvbridge.audio import make_container,to_wav

def words(text):
    return re.findall(r"\w+(?:['â€™]\w+)?",text.casefold(),flags=re.UNICODE)
def word_errors(reference,hypothesis):
    a,b=words(reference),words(hypothesis)
    previous=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        current=[i]
        for j,y in enumerate(b,1):
            current.append(min(current[-1]+1,previous[j]+1,previous[j-1]+(x!=y)))
        previous=current
    errors=previous[-1]
    return dict(reference_words=len(a),errors=errors,wer=errors/len(a) if a else None,
                unexpected_words=len(b) if not a else 0)

def pcm(path):
    with wave.open(str(path),'rb') as f:
        if (f.getnchannels(),f.getsampwidth(),f.getframerate(),f.getcomptype())!=(1,2,16000,'NONE'):
            raise ValueError('Use mono 16000 Hz 16-bit PCM WAV: '+str(path))
        n=f.getnframes()
        if not 0<n<=960000:raise ValueError('Use recordings between one sample and 60 seconds')
        data=f.readframes(n)
        if len(data)!=n*2:raise ValueError('Truncated WAV')
    return list(struct.unpack('<'+str(n)+'h',data))

def evaluate(manifest,out,executable=None,model=None,timeout=600):
    manifest=Path(manifest).resolve();out=Path(out).resolve()
    entries=json.loads(manifest.read_text(encoding='utf-8'))
    if not isinstance(entries,list) or not entries:raise ValueError('Manifest must be a nonempty list')
    if bool(executable)!=bool(model):raise ValueError('Provide both Whisper executable and model')
    if out.exists():raise ValueError('Output already exists; choose a new run directory')
    os.umask(0o077);out.mkdir(parents=True,mode=0o700)
    report={'transcription_run':bool(executable),'hermes_invoked':False,'cases':[]}
    if executable:
        executable=Path(executable).expanduser().resolve();model=Path(model).expanduser().resolve()
        if not executable.is_file() or not model.is_file():raise ValueError('Whisper executable/model missing')
        report.update(executable_sha256=hashlib.sha256(executable.read_bytes()).hexdigest(),
                      model_sha256=hashlib.sha256(model.read_bytes()).hexdigest())
    for index,entry in enumerate(entries):
        source=(manifest.parent/entry['wav']).resolve();samples=pcm(source)
        folder=out/str(index+1);folder.mkdir()
        original=folder/'original.wav';shutil.copyfile(source,original)
        encoded=make_container(samples);(folder/'recording.hvb').write_bytes(encoded)
        compressed=folder/'compressed.wav';to_wav(encoded,compressed)
        row={'name':entry['name'],'reference':entry['reference'],'seconds':len(samples)/16000,
             'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
             'clipped_samples':sum(abs(s)>=32767 for s in samples),'variants':{}}
        for name,wav in [('original',original),('compressed',compressed)]:
            if not executable:continue
            prefix=folder/name;started=time.monotonic()
            with (folder/(name+'.log')).open('wb') as log:
                subprocess.run([str(executable),'-m',str(model),'-f',str(wav),'-l',entry.get('language','en'),
                                '-otxt','-of',str(prefix)],stdout=log,stderr=subprocess.STDOUT,
                               stdin=subprocess.DEVNULL,timeout=timeout,check=True)
            transcript=prefix.with_suffix('.txt').read_text(encoding='utf-8').strip()
            row['variants'][name]=dict(text=transcript,elapsed_seconds=time.monotonic()-started,
                                      **word_errors(entry['reference'],transcript))
        report['cases'].append(row)
    (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return report

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('manifest',type=Path);p.add_argument('--out',required=True,type=Path)
    p.add_argument('--whisper',type=Path);p.add_argument('--model',type=Path)
    a=p.parse_args();report=evaluate(a.manifest,a.out,a.whisper,a.model)
    print('Prepared '+str(len(report['cases']))+' speech cases. Transcription run: '+str(report['transcription_run']))
    print('Private report: '+str(a.out/'report.json')+'. A completed evaluation is not an accuracy guarantee.')
if __name__=='__main__':main()
