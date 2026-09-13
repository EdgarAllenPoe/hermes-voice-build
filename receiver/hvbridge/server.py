from __future__ import annotations
import hmac, ipaddress, json, logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from .audio import MAX_FILE
from .storage import Store,Conflict,QueueFull
LOG=logging.getLogger(__name__)
def validate_bind(host:str):
    addr=ipaddress.ip_address(host)
    if not (addr.is_loopback or (addr.version==4 and addr in ipaddress.ip_network('100.64.0.0/10'))):
        raise ValueError('Bind only to loopback or the server actual Tailscale IPv4, never 0.0.0.0')
def server(store:Store,host:str,port:int,token:str):
    validate_bind(host)
    if len(token)<32 or token.startswith('REPLACE'): raise ValueError('Generate a real bearer token first')
    class Handler(BaseHTTPRequestHandler):
        protocol_version='HTTP/1.1'
        def setup(self):
            super().setup();self.connection.settimeout(20)
        def reply(self,status,obj):
            data=json.dumps(obj,separators=(',',':')).encode()
            self.send_response(status);self.send_header('Content-Type','application/json')
            self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store')
            self.send_header('Connection','close');self.end_headers();self.wfile.write(data)
            self.close_connection=True
        def authed(self):
            return hmac.compare_digest(self.headers.get('Authorization',''),f'Bearer {token}')
        def do_GET(self):
            if not self.authed(): return self.reply(401,{'error':'unauthorized'})
            if self.path!='/health': return self.reply(404,{'error':'not found'})
            return self.reply(200,{'status':'ok','protocol':'HVB1'})
        def do_POST(self):
            if not self.authed(): return self.reply(401,{'error':'unauthorized'})
            if self.path!='/v1/voice': return self.reply(404,{'error':'not found'})
            if self.headers.get('Transfer-Encoding'): return self.reply(400,{'error':'chunked request not supported'})
            if self.headers.get_content_type()!='application/octet-stream':
                return self.reply(415,{'error':'send application/octet-stream'})
            lengths=self.headers.get_all('Content-Length',[])
            if len(lengths)!=1: return self.reply(411,{'error':'one Content-Length required'})
            try: size=int(lengths[0])
            except ValueError: return self.reply(400,{'error':'bad length'})
            if not 64<size<=MAX_FILE: return self.reply(413,{'error':'recording too large or empty'})
            try:
                data=self.rfile.read(size)
                if len(data)!=size: return self.reply(400,{'error':'incomplete body'})
                mid,digest,duplicate=store.ingest(data)
                self.reply(202,{'id':mid,'sha256':digest,'accepted':True,'duplicate':duplicate})
            except Conflict as exc: self.reply(409,{'error':str(exc)})
            except QueueFull as exc: self.reply(507,{'error':str(exc)})
            except (ValueError,TimeoutError) as exc: self.reply(400,{'error':str(exc)})
            except Exception:
                LOG.exception('Receive failure');self.reply(500,{'error':'not acknowledged; retry later'})
        def log_message(self,fmt,*args):
            # Never log bearer headers, transcripts or recordings.
            LOG.info('%s %s',self.address_string(),fmt%args)
    class HTTP(ThreadingHTTPServer):
        daemon_threads=True
        request_queue_size=8
    return HTTP((host,port),Handler)
