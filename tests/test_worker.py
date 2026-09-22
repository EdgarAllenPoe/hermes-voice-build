import os,sys,tempfile,unittest
from pathlib import Path
from hvbridge.audio import make_container
from hvbridge.storage import Store
from hvbridge.worker import once,run_process,check_hermes
class WorkerTests(unittest.TestCase):
 def setUp(self):
  self.t=tempfile.TemporaryDirectory();self.p=Path(self.t.name);self.s=Store(self.p/'q.db');self.work=self.p/'work';self.work.mkdir()
  self.model=self.p/'fake-model';self.model.write_text('FAKE TEST MODEL, NOT FOR PRODUCTION')
  self.whisper=self.executable('fake-whisper',"import pathlib,sys\na=sys.argv\npathlib.Path(a[a.index('-of')+1]+'.txt').write_text('A test idea; $(touch SHOULD_NOT_EXIST)')\n")
  self.hermes=self.executable('fake-hermes',"import pathlib,sys\nif '--help' in sys.argv: print('--query-file FILE');sys.exit(0)\np=pathlib.Path(sys.argv[sys.argv.index('--query-file')+1]);print(p.read_text())\n")
  self.cfg={'whisper_executable':str(self.whisper),'whisper_model':str(self.model),'hermes_executable':str(self.hermes),'hermes_working_directory':str(self.p),'delivery_mode':'review'}
  self.mid=self.s.ingest(make_container([100]*320))[0]
 def tearDown(self):self.t.cleanup()
 def executable(self,name,body):
  p=self.p/name;p.write_text('#!'+sys.executable+'\n'+body);p.chmod(0o700);return p
 def test_review_never_invokes_agent(self):
  self.assertTrue(once(self.s,self.cfg,self.work));self.assertEqual(self.s.get(self.mid)['state'],'review');self.assertFalse(once(self.s,self.cfg,self.work));self.assertFalse((self.work/self.mid/'hermes.log').exists())
 def test_approved_safe_argv(self):
  once(self.s,self.cfg,self.work);self.s.approve(self.mid);once(self.s,self.cfg,self.work);r=self.s.get(self.mid);self.assertEqual(r['state'],'done');self.assertIn('$(touch',r['result']);self.assertFalse((self.p/'SHOULD_NOT_EXIST').exists())
 def test_auto(self):
  self.cfg['delivery_mode']='auto';once(self.s,self.cfg,self.work);self.assertEqual(self.s.get(self.mid)['state'],'ready');once(self.s,self.cfg,self.work);self.assertEqual(self.s.get(self.mid)['state'],'done')
 def test_agent_failure_uncertain(self):
  self.cfg['hermes_executable']=str(self.executable('broken-agent',"import sys\nif '--help' in sys.argv:print('--query-file');sys.exit(0)\nsys.exit(4)\n"))
  once(self.s,self.cfg,self.work);self.s.approve(self.mid);once(self.s,self.cfg,self.work);self.assertEqual(self.s.get(self.mid)['state'],'uncertain')
 def test_missing_model_keeps_audio(self):
  self.cfg['whisper_model']=str(self.p/'absent');once(self.s,self.cfg,self.work);r=self.s.get(self.mid);self.assertEqual(r['state'],'failed');self.assertIsNotNone(r['audio'])
 def test_timeout(self):
  slow=self.executable('slow-test',"import time\ntime.sleep(10)\n")
  with self.assertRaises(TimeoutError):run_process([str(slow)],self.p/'timeout.log',1)
 def test_reject_relative_executable(self):
  with self.assertRaises(ValueError):run_process(['echo','test'],self.p/'a.log',1)
 def test_hermes_capability_check(self):
  self.cfg['hermes_executable']=str(self.executable('old-agent',"print('wrong CLI')\n"))
  with self.assertRaises(ValueError):check_hermes(self.cfg)

 def test_processing_timings_are_metadata_only(self):
  self.cfg['delivery_mode']='auto';once(self.s,self.cfg,self.work);once(self.s,self.cfg,self.work)
  status=self.s.delivery_status(self.mid);self.assertEqual(status['state'],'done');self.assertIn('transcribe',status['timings_ms']);self.assertIn('hermes',status['timings_ms']);self.assertNotIn('transcript',status)
 def test_delivery_waits_for_earlier_transcription(self):
  import uuid
  second=self.s.ingest(make_container([100]*320,mid=str(uuid.uuid4())))[0]
  self.s.set(second,'ready',transcript='second');self.assertIsNone(self.s.claim_delivery())
  self.s.set(self.mid,'ready',transcript='first');self.assertEqual(self.s.claim_delivery()['id'],self.mid);self.assertIsNone(self.s.claim_delivery())
 def test_pipeline_transcribes_while_hermes_is_busy(self):
  import threading,time,uuid
  from hvbridge.worker import loop
  marker=self.p/'agent-started';release=self.p/'release-agent'
  body="import pathlib,sys,time\nif '--help' in sys.argv:print('--query-file');sys.exit(0)\npathlib.Path("+repr(str(marker))+").touch()\nwhile not pathlib.Path("+repr(str(release))+").exists():time.sleep(.01)\nprint('synthetic completed')\n"
  self.cfg['hermes_executable']=str(self.executable('waiting-agent',body));self.cfg['delivery_mode']='auto'
  stop=threading.Event();worker=threading.Thread(target=loop,args=(self.s,self.cfg,self.work,stop));worker.start()
  def wait_for(test):
   until=time.monotonic()+8
   while not test():
    if time.monotonic()>until:raise AssertionError('Pipeline timed out')
    time.sleep(.02)
  try:
   wait_for(marker.exists)
   second=self.s.ingest(make_container([200]*320,mid=str(uuid.uuid4())))[0]
   wait_for(lambda:self.s.get(second)['state']=='ready')
   self.assertEqual(self.s.get(self.mid)['state'],'delivering');self.assertFalse((self.work/second/'hermes.log').exists())
   release.touch();wait_for(lambda:self.s.get(second)['state']=='done')
  finally:release.touch();stop.set();self.s.wake();worker.join(10)
  self.assertFalse(worker.is_alive())
