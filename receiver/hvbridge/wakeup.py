"""Local post-commit wakeups; SQLite remains the durable source of truth."""
import os,socket,stat,threading
from pathlib import Path

def socket_path(database):return str(Path(database).parent/'worker-wakeup.sock')
def notify(database):
    try:
        with socket.socket(socket.AF_UNIX,socket.SOCK_DGRAM) as s:
            s.setblocking(False);s.sendto(b'w',socket_path(database))
    except (OSError,AttributeError):pass # A stopped worker catches up from its durable queue.

class WakeHub:
    def __init__(self,database):
        self.path=Path(socket_path(database));self.events=[threading.Event(),threading.Event()];self.closed=False
    def __enter__(self):
        # Caller holds worker.lock, so there is no second live receiver to unlink.
        if self.path.exists():
            if not stat.S_ISSOCK(self.path.lstat().st_mode):raise ValueError('Unexpected wakeup path; refusing to replace it')
            self.path.unlink()
        self.sock=socket.socket(socket.AF_UNIX,socket.SOCK_DGRAM);self.sock.bind(str(self.path));os.chmod(self.path,0o600);self.sock.settimeout(1)
        self.thread=threading.Thread(target=self.listen,name='voice-wakeup',daemon=True);self.thread.start();return self
    def listen(self):
        while not self.closed:
            try:self.sock.recv(32)
            except socket.timeout:continue
            except OSError:return
            for event in self.events:event.set()
    def __exit__(self,*args):
        self.closed=True;self.sock.close()
        for event in self.events:event.set()
        self.thread.join(2);self.path.unlink(missing_ok=True)
