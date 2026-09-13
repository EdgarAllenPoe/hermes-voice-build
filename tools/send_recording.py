#!/usr/bin/env python3
"""Send a recording to a private receiver. Does not change its review/auto setting."""
import argparse, hashlib, ipaddress, json, sys, urllib.request, urllib.parse
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'receiver'))
from hvbridge.audio import validate
p=argparse.ArgumentParser(description=__doc__);p.add_argument('file',type=Path);p.add_argument('--url',required=True);p.add_argument('--token-file',required=True,type=Path);a=p.parse_args()
u=urllib.parse.urlsplit(a.url)
secure=u.scheme=='https' and (u.hostname or '').endswith('.ts.net') and u.port in (443,None)
try:addr=ipaddress.ip_address(u.hostname or '');private=addr.is_loopback or addr in ipaddress.ip_network('100.64.0.0/10')
except ValueError:private=False
if u.path!='/v1/voice' or u.query or u.fragment or u.username or not (secure or u.scheme=='http' and private and u.port==8765):p.error('Use your private /v1/voice endpoint on HTTPS, or loopback/Tailscale HTTP port 8765')
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise ValueError('Redirect refused; token not forwarded')
try:
 data=a.file.read_bytes();h=validate(data);token=a.token_file.expanduser().read_text().strip()
 req=urllib.request.Request(a.url,data=data,headers={'Authorization':'Bearer '+token,'Content-Type':'application/octet-stream'},method='POST')
 with urllib.request.build_opener(NoRedirect).open(req,timeout=30) as r:
  out=json.load(r)
  if r.status!=202 or out.get('id')!=h.message_id or not out.get('accepted') or out.get('sha256')!=hashlib.sha256(data).hexdigest():raise ValueError('Invalid server acknowledgement')
  print(json.dumps(out,indent=2))
except Exception as e:p.exit(1,f'Not acknowledged: {e}\n')
