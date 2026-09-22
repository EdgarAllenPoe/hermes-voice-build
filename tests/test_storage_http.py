import concurrent.futures,http.client,json,tempfile,threading,time,unittest,uuid
from pathlib import Path
from hvbridge.audio import make_container
from hvbridge.storage import Store,Conflict,QueueFull
from hvbridge.server import server,validate_bind
class StorageTests(unittest.TestCase):
 def setUp(self):self.temp=tempfile.TemporaryDirectory();self.path=Path(self.temp.name)/'q.db';self.s=Store(self.path);self.audio=make_container([200]*320)
 def tearDown(self):self.temp.cleanup()
 def test_durable_reopen(self):
  mid,sha,dup=self.s.ingest(self.audio);self.assertFalse(dup);self.assertEqual(Store(self.path).get(mid)['audio'],self.audio)
 def test_duplicate(self):
  self.s.ingest(self.audio);self.assertTrue(self.s.ingest(self.audio)[2]);self.assertEqual(len(self.s.rows()),1)
 def test_concurrent_duplicate(self):
  with concurrent.futures.ThreadPoolExecutor(max_workers=6) as e:results=list(e.map(lambda _:self.s.ingest(self.audio),range(20)))
  self.assertEqual(sum(not r[2] for r in results),1);self.assertEqual(len(self.s.rows()),1)
 def test_collision(self):
  mid,_,_=self.s.ingest(self.audio)
  with self.assertRaises(Conflict):self.s.ingest(make_container([301]*320,mid))
 def test_quota(self):
  s=Store(Path(self.temp.name)/'small.db',100)
  with self.assertRaises(QueueFull):s.ingest(self.audio)
 def test_claim_once(self):
  self.s.ingest(self.audio);self.assertIsNotNone(self.s.claim('queued','transcribing'));self.assertIsNone(self.s.claim('queued','transcribing'))
 def test_recover_interrupted_transcription(self):
  mid,_,_=self.s.ingest(self.audio);self.s.claim('queued','transcribing');self.s.recover();self.assertEqual(self.s.get(mid)['state'],'queued')
 def test_recover_uncertain_delivery(self):
  mid,_,_=self.s.ingest(self.audio);self.s.set(mid,'delivering',transcript='test');self.s.recover();self.assertEqual(self.s.get(mid)['state'],'uncertain')
  with self.assertRaises(ValueError):self.s.retry(mid)
  self.s.retry(mid,True);self.assertEqual(self.s.get(mid)['state'],'ready')
 def test_review_gate(self):
  mid,_,_=self.s.ingest(self.audio)
  with self.assertRaises(ValueError):self.s.approve(mid)
  self.s.set(mid,'review',transcript='idea');self.s.approve(mid);self.assertEqual(self.s.get(mid)['state'],'ready')
 def test_receipt_after_purge(self):
  mid,_,_=self.s.ingest(self.audio);self.s.set(mid,'done')
  with self.s.connect() as db:db.execute('UPDATE messages SET updated=? WHERE id=?',(time.time()-20*86400,mid))
  self.assertEqual(self.s.purge_audio(7),1);self.assertIsNone(self.s.get(mid)['audio']);self.assertTrue(self.s.ingest(self.audio)[2])
class HttpTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.t=tempfile.TemporaryDirectory();cls.s=Store(Path(cls.t.name)/'q.db');cls.token='test-only-'+('q'*40);cls.http=server(cls.s,'127.0.0.1',0,cls.token);cls.port=cls.http.server_port
  cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True);cls.thread.start()
 @classmethod
 def tearDownClass(cls):cls.http.shutdown();cls.http.server_close();cls.thread.join();cls.t.cleanup()
 def request(self,body,headers=None,path='/v1/voice',method='POST'):
  h={'Authorization':'Bearer '+self.token,'Content-Type':'application/octet-stream'};h.update(headers or {})
  c=http.client.HTTPConnection('127.0.0.1',self.port,timeout=5);c.request(method,path,body,h);r=c.getresponse();code=r.status;data=json.loads(r.read());c.close();return code,data
 def test_accepted_and_duplicate(self):
  b=make_container([1]*320);status,a=self.request(b);self.assertEqual(status,202);self.assertTrue(a['accepted']);self.assertEqual(self.request(b)[1]['duplicate'],True)
 def test_auth_rejected(self):self.assertEqual(self.request(make_container([1]*320),{'Authorization':'Bearer wrong'})[0],401)
 def test_bad_crc(self):
  b=bytearray(make_container([1]*320));b[-1]^=1;self.assertEqual(self.request(bytes(b))[0],400)
 def test_wrong_content_type(self):self.assertEqual(self.request(b'x'*100,{'Content-Type':'text/plain'})[0],415)
 def test_unknown_path(self):self.assertEqual(self.request(b'x'*100,path='/else')[0],404)
 def test_empty(self):self.assertEqual(self.request(b'')[0],413)
 def test_health(self):self.assertEqual(self.request(None,path='/health',method='GET')[0],200)
 def test_bind_guard(self):
  for host in ('0.0.0.0','192.168.1.4','8.8.8.8'):
   with self.assertRaises(ValueError):validate_bind(host)
  validate_bind('127.0.0.1');validate_bind('100.80.1.2')

 def test_processing_status_authenticated_and_content_free(self):
  audio=make_container([400]*320);mid,digest,_=self.s.ingest(audio);self.s.set(mid,'done',transcript='private transcript',result='private result')
  code,data=self.request(None,path='/v1/messages/'+mid,method='GET');self.assertEqual(code,200);self.assertEqual(data['state'],'done');self.assertEqual(data['sha256'],digest)
  self.assertNotIn('transcript',data);self.assertNotIn('audio',data);self.assertNotIn('result',data)
  self.assertEqual(self.request(None,{'Authorization':'Bearer wrong'},path='/v1/messages/'+mid,method='GET')[0],401)
 def test_processing_status_unknown_and_invalid_ids(self):
  self.assertEqual(self.request(None,path='/v1/messages/'+str(uuid.uuid4()),method='GET')[0],404)
  for value in ('../../private','bad-id','0'*36):self.assertEqual(self.request(None,path='/v1/messages/'+value,method='GET')[0],400)
