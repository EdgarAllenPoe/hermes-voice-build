# Version 0.3 prehardware test report

Recorded September 13, 2026. This report supersedes the software-test status in earlier reports; those files remain historical records. Physical hardware acceptance is still outstanding.

## Executed results

| Check | Result | Evidence |
|---|---|---|
| Complete Linux host suite | 121 tests passed in 5.497 seconds | [Host run 34761101794](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34761101794), commit 884890e |
| Android database instrumentation | 6 tests passed on an API 35 emulator | [Android run 34761101788](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34761101788), commit 884890e |
| Android target build and lint | Successful; APK structure and signature verified | [Build run 34761101766](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34761101766), Android job 103734004398 |
| Exact-board firmware compile and generated configuration | Successful | [Build run 34761101766](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34761101766), firmware job 103734004466, commit 884890e |
| Real public speech comparison | Original: 0 word errors / 22 reference words; compressed: 0 / 22 | [Speech run 34759970448](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34759970448) |
| Explicit prebuilt bundle checker | 7 tests passed on Windows and within the Linux suite; original delivered bundle passed check-only verification | tests/test_flash_prebuilt.py; no device accessed |
| Printable Word guide | DOCX ZIP/XML integrity, wiring labels and table widths checked; wiring diagram visually inspected | Final page-by-page print-layout check is pending permission to use the installed LibreOffice on Windows |

The Android build has nonfatal lint warnings; a successful lint task is not a claim of zero warnings. Build artifacts use the disposable CI identity. At the September 13 checkpoint, no personal version 0.3 APK or firmware had been delivered, installed or flashed. The [September 14 APK build record](APK_v0.3_VERIFIED.md) documents the subsequently built and locally signed personal APK. The original private version 0.2 binaries remain unchanged.

## What the host tests exercise

- The actual C codec, energy gate, flash store and button controller, compiled against small host shims. Tests cover debounce and hold boundaries, torn writes, corrupt committed recordings, erase failure, empty captures and preserving skipped recordings.
- Sixteen simulated relay scenarios run the production Java TransferEngine and real temporary files. They include interrupted/resumed transfers, lost ACKs, duplicate durable receipts, commit failure, corrupt-message skip, legacy compatibility, full 15-slot queues and a 60-second recording. These scenarios are included within the 121-test unittest count, not 16 additional unittest cases.
- Receiver ingestion, authenticated HTTP, durable receipts, review gating, transcript correction and rejection, fair work scheduling, explicit cleanup, worker locking, path checks and uncertain-delivery recovery.
- Restoring a matching synthetic identity backup without rotating it, build-input checks, endpoint policy, diagnostic decoding and bounded upload retry classification.
- Preparing original/compressed speech comparison files and computing word errors without invoking Hermes.

The worker's intentional failure tests print expected tracebacks. The suite's final result is OK. The Linux suite uses Python 3.12 and JDK 17 on Ubuntu 24.04; physical hardware and the user's Hermes instance are not used.

## What the emulator established

The real Android QueueDb tests verify schema 1 to 2 migration retains audio, committed audio survives reopening, duplicate receipts persist after audio is cleared, held recordings do not block later eligible uploads, retry timing works, and WAL retains FULL synchronization after writes and reopening.

The initial FULL test failed: a PRAGMA issued during onConfigure was overridden by Android's WAL configuration. The fix sets the synchronous mode through SQLiteDatabase.OpenParams for the connection pool. The test remained enabled and passed after the fix. This is evidence from Android SQLite, not a desktop database substitute.

The test package is isolated as org.tomstout.hermesvoice.ci. Setup refuses to clear a database outside that package. API 35 emulation does not establish real Bluetooth behavior or the intended phone's Android 17 locked-screen behavior.

## Speech benchmark scope

The workflow builds upstream whisper.cpp v1.7.6, uses its public JFK sample and tiny.en, and evaluates the original PCM against HVB1 ADPCM output. Its artifact includes the report, source/executable/model/input hashes and the upstream source revision. Both versions transcribed this one sample without word errors.

The production preparation script selects the small model. This limited tiny.en result does not establish accuracy for small, the user's voice, dates/names, background noise or the recorder microphone. The workshop guide explains how to run a private corpus through the same evaluator. No real user voice was recorded and no benchmark transcript was dispatched to Hermes.

## Remaining physical acceptance

Use the [workshop guide](docs/HARDWARE-GUIDE.md) and its Word copy for assembly, programming and measurements. Still required: actual board/antenna/switch verification, USB programming and boot, microphone and first-word capture, BLE reconnect and queue-full behavior, phone lock/reboot/Doze behavior, private network delivery, the intended Hermes command/destination, charger current/termination, cell temperature observations, battery runtime and enclosure performance.

Software quarantine preserves corrupt recordings and reduces capacity; a physical recovery/erase procedure has not been validated. Receipt deduplication cannot guarantee exactly-once effects in an external agent after an uncertain interruption. Commission with delivery_mode set to review.
