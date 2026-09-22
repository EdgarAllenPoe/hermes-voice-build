"""Durable SQLite queue. Upload acknowledgement means COMMIT, not agent completion."""
from __future__ import annotations
import hashlib, sqlite3, time, shutil
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
            columns = {row[1] for row in db.execute('PRAGMA table_info(messages)')}
            if 'original_transcript' not in columns:
                db.execute('ALTER TABLE messages ADD COLUMN original_transcript TEXT')
            db.execute('CREATE TABLE IF NOT EXISTS message_timings(id TEXT NOT NULL,stage TEXT NOT NULL,milliseconds INTEGER NOT NULL,PRIMARY KEY(id,stage))')
            db.execute('PRAGMA user_version=3')
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
        self.wake()
        return h.message_id,digest,False
    def claim_work(self):
        # Oldest eligible transition wins, so neither transcription nor approved
        # delivery can starve under a continuous stream of the other.
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute("SELECT * FROM messages WHERE state IN ('queued','ready') "
                             "ORDER BY updated,created,id LIMIT 1").fetchone()
            if not row:
                return None
            next_state = 'transcribing' if row['state'] == 'queued' else 'delivering'
            db.execute('UPDATE messages SET state=?,updated=?,attempts=attempts+1 WHERE id=?',
                       (next_state,time.time(),row['id']))
            return dict(row)

    def wake(self):
        from .wakeup import notify
        notify(self.path)
    def timing(self,mid,stage,seconds):
        if stage not in {'transcribe','hermes','setup','queue_wait','total'}:raise ValueError('Unknown timing stage')
        with self.connect() as db:
            db.execute('INSERT OR REPLACE INTO message_timings VALUES(?,?,?)',(mid,stage,max(0,round(seconds*1000))))
    def claim_delivery(self):
        # One delivery lane preserves capture order while the transcription lane runs ahead.
        # Review/failed/uncertain messages require an operator and do not block newer work.
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            if db.execute("SELECT 1 FROM messages WHERE state='delivering' LIMIT 1").fetchone():return None
            row=db.execute("SELECT * FROM messages WHERE state IN ('queued','transcribing','ready') ORDER BY created,id LIMIT 1").fetchone()
            if not row or row['state']!='ready':return None
            db.execute("UPDATE messages SET state='delivering',updated=?,attempts=attempts+1 WHERE id=?",(time.time(),row['id']))
            return dict(row)

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
        self.wake()
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
    def review(self,mid:str):
        row = self.get(mid)
        if not row:
            raise ValueError('Unknown message')
        return {k: row[k] for k in ('id','state','transcript','original_transcript','error','result')}

    def edit_transcript(self,mid:str,text:str):
        text = text.strip()
        if not text or len(text.encode('utf-8')) > 64000:
            raise ValueError('Transcript must contain 1 to 64000 UTF-8 bytes')
        with self.connect() as db:
            cur = db.execute("UPDATE messages SET original_transcript=COALESCE(original_transcript,transcript),"
                             "transcript=?,updated=? WHERE id=? AND state='review'",
                             (text,time.time(),mid))
            if not cur.rowcount:
                raise ValueError('Only a message awaiting review can be edited')

    def reject(self,mid:str):
        with self.connect() as db:
            cur = db.execute("UPDATE messages SET state='rejected',updated=?,error='Rejected by operator' "
                             "WHERE id=? AND state='review'",(time.time(),mid))
            if not cur.rowcount:
                raise ValueError('Only a message awaiting review can be rejected')
        self.wake()

    def report(self):
        with self.connect() as db:
            states = {r[0]:r[1] for r in db.execute('SELECT state,COUNT(*) FROM messages GROUP BY state')}
            audio = db.execute('SELECT COALESCE(SUM(length(audio)),0) FROM messages').fetchone()[0]
        total = sum(p.stat().st_size for p in self.path.parent.rglob('*')
                    if p.is_file() and not p.is_symlink())
        return dict(states=states,audio_bytes=audio,quota_bytes=self.quota,
                    state_directory_bytes=total,free_disk_bytes=shutil.disk_usage(self.path.parent).free)

    def cleanup_candidates(self,days:int):
        if days < 1:
            raise ValueError('Retention must be at least one day')
        with self.connect() as db:
            return [dict(r) for r in db.execute(
                "SELECT id,state,length(audio) AS audio_bytes FROM messages "
                "WHERE state IN ('done','rejected') AND updated<? ORDER BY created",
                (time.time()-days*86400,))]

    def clear_completed(self,mid:str):
        with self.connect() as db:
            db.execute("UPDATE messages SET audio=NULL,transcript=NULL,original_transcript=NULL,result=NULL "
                       "WHERE id=? AND state IN ('done','rejected')",(mid,))

    def approve(self,mid:str):
        with self.connect() as db:
            cur=db.execute("UPDATE messages SET state='ready',updated=? WHERE id=? AND state='review'",(time.time(),mid))
            if not cur.rowcount: raise ValueError('Message is not awaiting review')
        self.wake()

    def retry(self,mid:str,allow_uncertain=False):
        with self.connect() as db:
            row=db.execute('SELECT state,transcript FROM messages WHERE id=?',(mid,)).fetchone()
            if not row: raise ValueError('Unknown message')
            if row['state']=='uncertain' and not allow_uncertain:
                raise ValueError('Possible agent side effects. Review and explicitly allow uncertain retry.')
            if row['state'] not in ('failed','uncertain'): raise ValueError('Message is not retryable')
            db.execute('UPDATE messages SET state=?,error=NULL,updated=? WHERE id=?',
                       ('ready' if row['transcript'] else 'queued',time.time(),mid))
        self.wake()

    def purge_audio(self,days:int):
        if days<1: raise ValueError('Retention must be at least one day')
        with self.connect() as db:
            cur=db.execute("UPDATE messages SET audio=NULL WHERE state='done' AND updated<? AND audio IS NOT NULL",
                           (time.time()-days*86400,))
            return cur.rowcount

    def delivery_status(self,mid:str):
        """Authenticated receipt status; deliberately excludes audio and agent output."""
        with self.connect() as db:
            row=db.execute('SELECT id,sha256,state,created,updated FROM messages WHERE id=?',(mid,)).fetchone()
            if not row:return None
            result=dict(row)
            timings={r[0]:r[1] for r in db.execute('SELECT stage,milliseconds FROM message_timings WHERE id=?',(mid,))}
            if timings:result['timings_ms']=timings
            return result
