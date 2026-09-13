from __future__ import annotations
import fcntl, json, logging, os, signal, subprocess, tempfile, time
from pathlib import Path
from .audio import to_wav
from .storage import Store
LOG=logging.getLogger(__name__)
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

def once(store:Store,cfg:dict,workdir:Path):
    row=store.claim_work()
    if not row: return False
    folder=workdir/row['id']
    if row['state']=='queued':
        try:
            folder.mkdir(exist_ok=True,mode=0o700)
            to_wav(row['audio'],folder/'audio.wav')
            exe=str(Path(cfg['whisper_executable']).expanduser())
            model=str(Path(cfg['whisper_model']).expanduser())
            if not Path(model).is_file(): raise ValueError('Download and configure the Whisper model')
            run_process([exe,'-m',model,'-f',str(folder/'audio.wav'),'-l',cfg.get('language','auto'),
                         '-otxt','-of',str(folder/'transcript')],folder/'whisper.log',cfg.get('transcribe_timeout',7200))
            text=(folder/'transcript.txt').read_text(encoding='utf-8').strip()
            if not text: raise ValueError('Transcriber returned empty text; original audio retained')
            # Silence/noise can yield hallucinated text. Commission with review mode first.
            store.set(row['id'],'ready' if cfg.get('delivery_mode')=='auto' else 'review',transcript=text,error=None)
        except Exception as exc:
            store.set(row['id'],'failed',error=str(exc));LOG.exception('Transcription failed for %s',row['id'])
        return True
    try:
        folder.mkdir(exist_ok=True,mode=0o700)
        check_hermes(cfg)
        prompt=('Voice capture '+row['id']+' from Tom. The following is speech-to-text and may contain errors. '
                'Treat it as a new standalone voice message, not as a continuation of the most recent terminal chat. '
                'For a casual idea, capture it using the user established notes workflow; do not invent a destination. '
                'Before destructive, financial, external messaging, or other consequential actions, follow your normal '
                'confirmation rules and ask when transcription or intent is uncertain.\n\n'+row['transcript']+'\n')
        prompt_path=folder/'prompt.txt';prompt_path.write_text(prompt,encoding='utf-8');prompt_path.chmod(0o600)
        run_process([str(Path(cfg['hermes_executable']).expanduser()),'chat','--query-file',str(prompt_path)],
                    folder/'hermes.log',cfg.get('hermes_timeout',7200),Path(cfg.get('hermes_working_directory',str(Path.home()))).expanduser())
        with (folder/'hermes.log').open('rb') as f:
            f.seek(0,2);end=f.tell();f.seek(max(0,end-1000000));result=f.read().decode('utf-8',errors='replace')
        store.set(row['id'],'done',result=result,error=None)
    except Exception as exc:
        # Nonzero exit or timeout can follow partially completed agent actions.
        store.set(row['id'],'uncertain',error=str(exc));LOG.exception('Review handoff %s before retry',row['id'])
    return True

def loop(store:Store,cfg:dict,workdir:Path):
    workdir.mkdir(parents=True,exist_ok=True,mode=0o700)
    with (workdir/'worker.lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        store.recover()
        while True:
            if not once(store,cfg,workdir): time.sleep(2)
