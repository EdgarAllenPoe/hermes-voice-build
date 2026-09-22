from __future__ import annotations
import fcntl, json, logging, os, signal, subprocess, tempfile, time, threading
from pathlib import Path
from .audio import to_wav
from .storage import Store
LOG=logging.getLogger(__name__)
_checked_hermes=set()
def ensure_hermes(cfg):
    exe=Path(cfg['hermes_executable']).expanduser();st=exe.stat();key=(str(exe),st.st_mtime_ns,st.st_size)
    if key not in _checked_hermes:check_hermes(cfg);_checked_hermes.add(key)

def run_process(argv:list[str],out:Path,timeout:int,cwd:Path|None=None):
    if not argv or not Path(argv[0]).is_absolute(): raise ValueError('Executable must be an absolute path')
    with out.open('wb') as log:
        proc=subprocess.Popen(argv,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,
                              cwd=cwd,start_new_session=True)
        try: code=proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid,signal.SIGTERM)
            try: proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid,signal.SIGKILL);proc.wait()
            raise TimeoutError('Subprocess timed out; review logs')
    if code: raise RuntimeError(f'Process exited {code}; see {out.name}')
def check_hermes(cfg):
    exe=Path(cfg['hermes_executable']).expanduser()
    if not exe.is_file(): raise ValueError('Hermes executable is not configured')
    result=subprocess.run([str(exe),'chat','--help'],capture_output=True,text=True,timeout=30)
    if result.returncode or '--query-file' not in result.stdout+result.stderr:
        raise ValueError('Installed Hermes must support: hermes chat --query-file FILE')

def once(store:Store,cfg:dict,workdir:Path,lane=None):
    row=store.claim('queued','transcribing') if lane=='transcribe' else store.claim_delivery() if lane=='deliver' else store.claim_work()
    if not row: return False
    folder=workdir/row['id'];stage_start=time.monotonic()
    if row['state']=='queued':
        try:
            store.timing(row['id'],'queue_wait',time.time()-row['created'])
            folder.mkdir(exist_ok=True,mode=0o700)
            to_wav(row['audio'],folder/'audio.wav')
            exe=str(Path(cfg['whisper_executable']).expanduser())
            model=str(Path(cfg['whisper_model']).expanduser())
            if not Path(model).is_file(): raise ValueError('Download and configure the Whisper model')
            run_process([exe,'-m',model,'-f',str(folder/'audio.wav'),'-l',cfg.get('language','auto'),
                         '-t',str(cfg.get('whisper_threads',4)),'-otxt','-of',str(folder/'transcript')],folder/'whisper.log',cfg.get('transcribe_timeout',7200))
            text=(folder/'transcript.txt').read_text(encoding='utf-8').strip()
            if not text: raise ValueError('Transcriber returned empty text; original audio retained')
            # Silence/noise can yield hallucinated text. Commission with review mode first.
            store.timing(row['id'],'transcribe',time.monotonic()-stage_start)
            store.set(row['id'],'ready' if cfg.get('delivery_mode')=='auto' else 'review',transcript=text,error=None)
        except Exception as exc:
            store.set(row['id'],'failed',error=str(exc));LOG.exception('Transcription failed for %s',row['id'])
        return True
    try:
        folder.mkdir(exist_ok=True,mode=0o700)
        ensure_hermes(cfg)
        prompt=('Voice capture '+row['id']+' from Tom. Process this as a standalone voice message. '
                'Follow the hermes-voice-intake skill and existing outcome.json contract. '
                'For a clear simple note, use the established destination and minimum necessary tools; '
                'avoid unrelated research, old conversations, or extra planning. For complex requests use the full workflow. '
                'Keep normal approval and transcription-uncertainty checks. Never repeat an action or Telegram delivery.\n\n'
                +row['transcript']+'\n')
        prompt_path=folder/'prompt.txt';prompt_path.write_text(prompt,encoding='utf-8');prompt_path.chmod(0o600)
        store.timing(row['id'],'setup',time.monotonic()-stage_start);stage_start=time.monotonic()
        run_process([str(Path(cfg['hermes_executable']).expanduser()),'chat','--query-file',str(prompt_path)],
                    folder/'hermes.log',cfg.get('hermes_timeout',7200),Path(cfg.get('hermes_working_directory',str(Path.home()))).expanduser())
        with (folder/'hermes.log').open('rb') as f:
            f.seek(0,2);end=f.tell();f.seek(max(0,end-1000000));result=f.read().decode('utf-8',errors='replace')
        store.timing(row['id'],'hermes',time.monotonic()-stage_start)
        store.timing(row['id'],'total',time.time()-row['created'])
        store.set(row['id'],'done',result=result,error=None)
    except Exception as exc:
        # Nonzero exit or timeout can follow partially completed agent actions.
        store.set(row['id'],'uncertain',error=str(exc));LOG.exception('Review handoff %s before retry',row['id'])
    return True

def loop(store:Store,cfg:dict,workdir:Path,stop=None):
    from .wakeup import WakeHub
    stop=stop or threading.Event();workdir.mkdir(parents=True,exist_ok=True,mode=0o700)
    with (workdir/'worker.lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        store.recover()
        with WakeHub(store.path) as wake:
            def run(lane,event):
                while not stop.is_set():
                    event.clear()
                    try:
                        if once(store,cfg,workdir,lane):continue
                    except Exception:LOG.exception('Worker lane %s failed',lane)
                    event.wait(30) # Recovery fallback if a local wakeup was lost.
            threads=[threading.Thread(target=run,args=(lane,event),name='voice-'+lane,daemon=True)
                     for lane,event in zip(('transcribe','deliver'),wake.events)]
            for thread in threads:thread.start()
            previous={}
            def terminate(signum,frame):
                stop.set()
                for event in wake.events:event.set()
            if threading.current_thread() is threading.main_thread():
                for sig in (signal.SIGTERM,signal.SIGINT):previous[sig]=signal.signal(sig,terminate)
            try:
                while not stop.wait(1):pass
            finally:
                for event in wake.events:event.set()
                for thread in threads:thread.join()
                for sig,handler in previous.items():signal.signal(sig,handler)
