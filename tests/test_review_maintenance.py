import json, sqlite3, tempfile, time, unittest
from pathlib import Path
from hvbridge.audio import make_container
from hvbridge.storage import Store
from hvbridge.maintenance import cleanup, plan

class ReviewMaintenanceTests(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory(); self.addCleanup(self.t.cleanup)
        self.root=Path(self.t.name);self.s=Store(self.root/'queue.sqlite3')
        self.mid=self.s.ingest(make_container([123]*320))[0]
        self.s.set(self.mid,'review',transcript='Original words')
        self.work=self.root/'work';self.folder=self.work/self.mid;self.folder.mkdir(parents=True)
    def old(self,mid):
        with self.s.connect() as db:
            db.execute('UPDATE messages SET updated=? WHERE id=?',(time.time()-10*86400,mid))
    def test_edits_preserve_first_transcript(self):
        self.s.edit_transcript(self.mid,'Correct words')
        self.s.edit_transcript(self.mid,'Final words')
        r=self.s.review(self.mid)
        self.assertEqual(r['original_transcript'],'Original words')
        self.assertEqual(r['transcript'],'Final words');self.assertEqual(r['state'],'review')
    def test_cannot_edit_approved_or_empty(self):
        with self.assertRaises(ValueError):self.s.edit_transcript(self.mid,' ')
        self.s.approve(self.mid)
        with self.assertRaises(ValueError):self.s.edit_transcript(self.mid,'Late change')
    def test_reject_preserves_audio_and_receipt(self):
        self.s.reject(self.mid);self.assertIsNotNone(self.s.get(self.mid)['audio'])
        with self.assertRaises(ValueError):self.s.approve(self.mid)
        with self.assertRaises(ValueError):self.s.retry(self.mid)
    def test_fair_claim_does_not_starve_ready(self):
        self.s.approve(self.mid);self.old(self.mid)
        other=self.s.ingest(make_container([456]*320))[0]
        self.assertEqual(self.s.claim_work()['id'],self.mid)
        self.assertEqual(self.s.claim_work()['id'],other)
    def test_fair_claim_does_not_starve_transcription(self):
        other=self.s.ingest(make_container([456]*320))[0];self.old(other)
        self.s.approve(self.mid)
        self.assertEqual(self.s.claim_work()['id'],other)
    def test_cleanup_preview_changes_nothing(self):
        self.s.reject(self.mid);self.old(self.mid);(self.folder/'audio.wav').write_bytes(b'private')
        result=cleanup(self.s,self.work,7)
        self.assertFalse(result['applied']);self.assertEqual(len(result['items']),1)
        self.assertTrue((self.folder/'audio.wav').exists());self.assertIsNotNone(self.s.get(self.mid)['audio'])
    @unittest.skipIf(__import__('os').name=='nt','POSIX worker lock')
    def test_cleanup_retains_receipts_and_unknown_files(self):
        audio=self.s.get(self.mid)['audio'];self.s.reject(self.mid);self.old(self.mid)
        (self.folder/'audio.wav').write_bytes(b'private');(self.folder/'keep.txt').write_text('keep')
        cleanup(self.s,self.work,7,True)
        self.assertFalse((self.folder/'audio.wav').exists());self.assertTrue((self.folder/'keep.txt').exists())
        self.assertTrue(self.s.ingest(audio)[2]);self.assertIsNone(self.s.get(self.mid)['audio'])
    @unittest.skipIf(__import__('os').name=='nt','POSIX worker lock')
    def test_cleanup_refuses_running_worker(self):
        from hvbridge.maintenance import worker_lock
        with worker_lock(self.work):
            with self.assertRaisesRegex(ValueError,'Stop the worker'):
                cleanup(self.s,self.work,7,True)
    def test_active_or_recent_work_is_never_cleaned(self):
        self.old(self.mid);self.assertEqual(plan(self.s,self.work,7),[])
        self.s.reject(self.mid);self.assertEqual(plan(self.s,self.work,7),[])
    def test_storage_report(self):
        r=self.s.report();self.assertGreater(r['audio_bytes'],0)
        self.assertEqual(r['states']['review'],1);self.assertGreater(r['free_disk_bytes'],0)
    def test_migration_is_repeatable_and_preserves_queue(self):
        before=self.s.get(self.mid)['audio'];s=Store(self.s.path);s=Store(self.s.path)
        self.assertEqual(s.get(self.mid)['audio'],before)
        with s.connect() as db:self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],2)
