from __future__ import annotations
import argparse, json, logging, os
from pathlib import Path
from .storage import Store
from .server import server

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description='Hermes Voice Button bridge')
    p.add_argument('--config',required=True,type=Path)
    sub=p.add_subparsers(dest='command',required=True)
    for cmd in ['serve','work','status','check','storage']: sub.add_parser(cmd)
    for cmd in ['approve','retry','export','show','reject','edit']:
        s=sub.add_parser(cmd);s.add_argument('id')
        if cmd=='retry': s.add_argument('--allow-uncertain',action='store_true')
        if cmd=='edit': s.add_argument('--transcript-file',required=True,type=Path)
        if cmd=='export': s.add_argument('output',type=Path)
    g=sub.add_parser('purge-audio');g.add_argument('--older-than-days',type=int,required=True)
    g=sub.add_parser('cleanup');g.add_argument('--older-than-days',type=int,required=True)
    g.add_argument('--apply',action='store_true',help='Delete completed work after stopping worker; default previews')
    a=p.parse_args();cfg=json.loads(a.config.expanduser().read_text())
    logging.basicConfig(level=logging.INFO,format='%(asctime)s %(levelname)s %(message)s')
    state=Path(cfg['state_directory']).expanduser();state.mkdir(parents=True,exist_ok=True,mode=0o700)
    store=Store(state/'queue.sqlite3',cfg.get('quota_bytes',104857600))
    if a.command=='serve':
        token=Path(cfg['token_file']).expanduser().read_text().strip()
        server(store,cfg['bind'],int(cfg['port']),token).serve_forever()
    elif a.command=='work':
        from .worker import loop
        loop(store,cfg,state/'work')
    elif a.command=='status':
        print(json.dumps(store.rows(),indent=2))
    elif a.command=='storage': print(json.dumps(store.report(),indent=2))
    elif a.command=='show': print(json.dumps(store.review(a.id),ensure_ascii=False,indent=2))
    elif a.command=='edit': store.edit_transcript(a.id,a.transcript_file.read_text(encoding='utf-8'))
    elif a.command=='reject': store.reject(a.id)
    elif a.command=='cleanup':
        from .maintenance import cleanup
        print(json.dumps(cleanup(store,state/'work',a.older_than_days,a.apply),indent=2))
    elif a.command=='approve': store.approve(a.id)
    elif a.command=='retry': store.retry(a.id,a.allow_uncertain)
    elif a.command=='purge-audio': print('Recordings purged:',store.purge_audio(a.older_than_days))
    elif a.command=='export':
        row=store.get(a.id)
        if not row or not row['audio']: raise ValueError('No retained recording for that ID')
        from .audio import to_wav
        to_wav(row['audio'],a.output)
    elif a.command=='check':
        from .worker import check_hermes
        check_hermes(cfg)
        for key in ['whisper_executable','whisper_model','token_file']:
            if not Path(cfg[key]).expanduser().is_file(): raise ValueError(f'Missing {key}')
        print('Configured local files and Hermes query-file support found. No voice message executed.')
if __name__=='__main__': main()
