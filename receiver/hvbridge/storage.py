"""Durable SQLite queue. Upload acknowledgement means COMMIT, not agent completion."""
from __future__ import annotations
import hashlib, sqlite3, time
from contextlib import contextmanager
from pathlib import Path
from .audio import validate
class Conflict(ValueError): pass
class QueueFull(ValueError): pass
class Store:
    def __init__(self,path: str|Path,quota_bytes:int=100*1024*1024):
        self.path=Path(path);self.quota=quota_bytes
        self.path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        with self.connect() as db:
            db.executescript("""CREATE TABLE IF NOT EXISTS messages(
              id TEXT PRIMARY KEY, sha256 TEXT NOT NULL, audio BLOB,
              state TEXT NOT NULL, created REAL NOT NULL, updated REAL NOT NULL,
              transcript TEXT, result TEXT, error TEXT, flags INTEGER NOT NULL,
              attempts INTEGER NOT NULL DEFAULT 0);
              CREATE INDEX IF NOT EXISTS queue_state ON messages(state,created);""")
        self.path.chmod(0o600)
    @contextmanager
    def connect(self):
        db=sqlite3.connect(self.path,timeout=30)
        db.row_factory=sqlite3.Row
        db.execute('PRAGMA journal_mode=WAL');db.execute('PRAGMA synchronous=FULL')
        try:
            with db:
                yield db
        finally:
            db.close()
    def ingest(self,data:bytes):
        h=validate(data);digest=hashlib.sha256(data).hexdigest();now=time.time()
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT sha256,state FROM messages WHERE id=?',(h.message_id,)).fetchone()
            if row:
                if row['sha256']!=digest: raise Conflict('ID exists with different audio')
                return h.message_id,digest,True
            used=db.execute('SELECT COALESCE(SUM(length(audio)),0) FROM messages').fetchone()[0]
            if used+len(data)>self.quota: raise QueueFull('Server queue quota reached')
            db.execute('INSERT INTO messages(id,sha256,audio,state,created,updated,flags) VALUES(?,?,?,?,?,?,?)',
                       (h.message_id,digest,data,'queued',now,now,h.flags))
        return h.message_id,digest,False
    def claim(self,state:str,next_state:str):
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT * FROM messages WHERE state=? ORDER BY created,id LIMIT 1',(state,)).fetchone()
            if row:
                db.execute('UPDATE messages SET state=?,updated=?,attempts=attempts+1 WHERE id=?',
                           (next_state,time.time(),row['id']))
                return dict(row)
        return None
    def set(self,mid:str,state:str,**values):
        if set(values)-{'transcript','result','error'}: raise ValueError('Invalid update field')
        parts=['state=?','updated=?']+[f'{k}=?' for k in values]
        with self.connect() as db:
            db.execute('UPDATE messages SET '+','.join(parts)+' WHERE id=?',
                       [state,time.time(),*values.values(),mid])
    def recover(self):
        with self.connect() as db:
            db.execute("UPDATE messages SET state='queued',error='Transcription interrupted; safe retry' WHERE state='transcribing'")
            db.execute("UPDATE messages SET state='uncertain',error='Agent handoff interrupted; review before retry' WHERE state='delivering'")
    def rows(self):
        with self.connect() as db:
            return [dict(r) for r in db.execute('SELECT id,state,created,updated,flags,error FROM messages ORDER BY created')]
    def get(self,mid:str):
        with self.connect() as db:
            r=db.execute('SELECT * FROM messages WHERE id=?',(mid,)).fetchone()
            return dict(r) if r else None
    def approve(self,mid:str):
        with self.connect() as db:
            cur=db.execute("UPDATE messages SET state='ready',updated=? WHERE id=? AND state='review'",(time.time(),mid))
            if not cur.rowcount: raise ValueError('Message is not awaiting review')
    def retry(self,mid:str,allow_uncertain=False):
        with self.connect() as db:
            row=db.execute('SELECT state,transcript FROM messages WHERE id=?',(mid,)).fetchone()
            if not row: raise ValueError('Unknown message')
            if row['state']=='uncertain' and not allow_uncertain:
                raise ValueError('Possible agent side effects. Review and explicitly allow uncertain retry.')
            if row['state'] not in ('failed','uncertain'): raise ValueError('Message is not retryable')
            db.execute('UPDATE messages SET state=?,error=NULL,updated=? WHERE id=?',
                       ('ready' if row['transcript'] else 'queued',time.time(),mid))
    def purge_audio(self,days:int):
        if days<1: raise ValueError('Retention must be at least one day')
        with self.connect() as db:
            cur=db.execute("UPDATE messages SET audio=NULL WHERE state='done' AND updated<? AND audio IS NOT NULL",
                           (time.time()-days*86400,))
            return cur.rowcount
