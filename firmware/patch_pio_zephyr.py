"""Narrow compatibility fix for the pinned PlatformIO Zephyr/SCons adapter.

An actual target build fails before compilation at
    env.Append(LIBS=lib_dep["lib_path"])
with AttributeError: SCons.Node.FS.File has no attribute insert. The adapter
passes a single File node where this append operation requires a list. Keep
the same library node and ordering, but wrap it in a one-element list.
No library, hardware configuration, warning, or validation is removed.

This PRE script runs after PlatformIO installs framework packages. It stops
rather than silently patching a different version or unmatched source.
"""
from pathlib import Path
import hashlib
import json

Import('env')
platform = env.PioPlatform()
package = platform.get_package_dir('framework-zephyr-nrf54lm20')
if not package:
    raise RuntimeError('Expected nRF54LM20 Zephyr framework is not installed')
root = Path(package)
metadata = json.loads((root / 'package.json').read_text())
if str(metadata.get('version')) != '3.40400.260428':
    raise RuntimeError('Review the SCons adapter fix for this different framework version')
path = root / 'scripts/platformio/platformio-build.py'
text = path.read_text()
old = 'env.Append(LIBS=lib_dep["lib_path"])'
new = 'env.Append(LIBS=[lib_dep["lib_path"]])'
before = hashlib.sha256(text.encode()).hexdigest()
if text.count(old) == 1 and new not in text:
    text = text.replace(old, new, 1)
    path.write_text(text)
    state = 'applied'
elif old not in text and text.count(new) == 1:
    state = 'already applied'
else:
    raise RuntimeError('Unexpected Zephyr library-append source; inspect rather than guessing')
after = hashlib.sha256(text.encode()).hexdigest()
print('Hermes adapter fix: list-wrap library File node (' + state + ')')
print('Adapter SHA256 before=' + before + ' after=' + after)
