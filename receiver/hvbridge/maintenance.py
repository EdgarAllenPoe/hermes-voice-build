"""Explicit cleanup of completed work; receipt IDs and hashes always survive."""
from contextlib import contextmanager
from pathlib import Path
import uuid

WORK_FILES = ('audio.wav','transcript.txt','prompt.txt','whisper.log','hermes.log')

@contextmanager
def worker_lock(workdir):
    import fcntl
    workdir.mkdir(parents=True,exist_ok=True,mode=0o700)
    with (workdir/'worker.lock').open('a') as lock:
        try:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('Stop the worker service before applying cleanup') from None
        yield

def plan(store,workdir,days):
    root = Path(workdir).resolve()
    result = []
    for row in store.cleanup_candidates(days):
        mid = row['id']
        if str(uuid.UUID(mid)) != mid:
            raise ValueError('Unexpected recording directory ID')
        folder = root/mid
        if folder.is_symlink() or folder.resolve().parent != root:
            raise ValueError('Refusing an unsafe recording directory')
        paths = [folder/n for n in WORK_FILES if (folder/n).exists()]
        if any(p.is_symlink() or not p.is_file() for p in paths):
            raise ValueError('Refusing an unsafe recording file')
        result.append(dict(id=mid,state=row['state'],audio_bytes=row['audio_bytes'] or 0,
                           files=[str(p) for p in paths],
                           file_bytes=sum(p.stat().st_size for p in paths)))
    return result

def cleanup(store,workdir,days,apply=False):
    if not apply:
        return {'applied':False,'items':plan(store,workdir,days)}
    with worker_lock(Path(workdir)):
        items = plan(store,workdir,days)
        for item in items:
            # Validate the entire plan before touching any files. Only terminal
            # states are eligible and they cannot be retried or approved.
            for name in item['files']:
                Path(name).unlink(missing_ok=True)
            store.clear_completed(item['id'])
        return {'applied':True,'items':items,
                'note':'Receipts retained. SQLite free pages and backups are not securely erased.'}
