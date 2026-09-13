# PlatformIO adapter compatibility fix

The third cloud firmware attempt reached generated DTS and Kconfig, then failed in the installed Zephyr PlatformIO adapter before any application compilation:

`AttributeError: <class 'SCons.Node.FS.File'> object has no attribute 'insert'`

The traceback identifies `scripts/platformio/platformio-build.py`, line 2148, at `env.Append(LIBS=lib_dep["lib_path"])`. SCons attempts to insert into the supplied File node rather than append a sequence. The project PRE script wraps that same node in a one-element list. It neither omits the library nor changes security, device-tree, or charger settings.

The fix is restricted to framework version `3.40400.260428`, requires exactly one matching source statement, and records before/after SHA-256 values in the compiler log. An unexpected package or statement stops the build for review. The original public source bundle does not contain this integration fix.

The generated DTS and actual Kconfig from run 3 passed the strengthened local preflight, including secure randomness, authenticated BLE, power-rail voltages, charger initialization order and partition boundaries. This did not make the unsuccessful compiler invocation a successful firmware build, and it does not establish physical board behavior.
