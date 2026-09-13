> **Historical record:** The original text below describes an earlier authoring session. Both targets subsequently compiled in run `34752997335`; see [README.md](README.md) and [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for current status. Physical hardware acceptance remains outstanding.

> **v0.2 SOURCE-ONLY UPDATE:** No APK, HEX, BIN, or ELF was built in this session. Read `START_HERE_BUILD_STATUS.md` first. The text below is retained from the v0.1 source package; old print-file references are not included in this update.

# Test report — September 12, 2026

## Actual result

**56 tests passed**, in 13.392 seconds in the final recorded run. The complete console output is in `docs/host-tests.txt`. Error traces inside deliberately failing worker scenarios are expected test output; the final unittest result is `OK`.

```sh
bash tools/run_tests.sh
```

Environment: Linux; Python 3.13.5; GCC 14.2.0; javac 21.0.11. The Android project's chosen build baseline is JDK 17; compiling portable Java classes under JDK 21 here is not an Android target build.

## Coverage

| Area | What was executed |
|---|---|
| Audio/container | Header and size validation, ADPCM round-trip, corruption rejection and fixture consistency |
| Receiver storage and HTTP | Durable SQLite ingestion, quotas, conflicting/repeated UUIDs, authenticated upload and response semantics |
| Worker | Fake local transcription/agent executables, review gating, timeouts, restart recovery and uncertain delivery behavior |
| Native C codec | Actual codec/gate compiled with GCC and exercised on host data |
| Raw flash storage | Actual `storage.c` compiled against a small test-only Zephyr shim and simulated NOR flash; power-interruption, ACK, erase and queue-full cases |
| Portable Java | Actual `Wire.java` and `Endpoint.java` compiled and executed against the shared fixture and 15 endpoint/protocol assertions |
| Project checks | Manifest constraints, no shared default pairing secret, and selected configuration checks |

The simulated flash is not a Nordic controller or a physical flash device. The fake worker executables do not exercise the user's Hermes installation or any actual Whisper model. The fixture is a synthetic tone, not speech.

## Not executed

- Android SDK/Gradle APK build, Android lint or physical-device installation.
- Seeed/Nordic/Zephyr target build, flashing, target linking or Bluetooth controller execution.
- Microphone recording, charge-current/termination measurement, battery runtime, RF range or first-word timing.
- Android companion wake while locked, Doze/reboot/force-stop behavior, actual Tailscale routing or HTTPS configuration.
- Real speech recognition or delivery into the user's existing Hermes installation.
- Full security audit, adversarial fuzzing, commercial safety certification, or fit validation of a printed enclosure.

The Android SDK, Gradle distribution, Nordic/Seeed target toolchain, board and phone were not available in the authoring environment. These gaps are explicitly covered by the commissioning checklists; passing host tests is not a claim that every component is ready to deploy unchanged.

## Reproducing

Run the suite on an untouched extracted package with Python, GCC and a JDK available. `tools/provision.py` intentionally replaces the no-secret guard in `device_config.h`; the test that checks an unprovisioned package will then no longer apply. Keep an untouched copy for reproducing this published test result. Tests create temporary databases, programs and fixtures; they do not use a real agent or change tailnet settings.
