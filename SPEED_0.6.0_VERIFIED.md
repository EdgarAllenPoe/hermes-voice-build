# Android 0.6.0 and firmware 0.5.0 speed verification

Recorded September 22, 2026. This update implements the five speed recommendations described in [the guide](docs/18-speed-improvements.md). [The full handoff prompt](docs/HERMES-SPEED-OPTIMIZATION-PROMPT.md) covers the remaining changes inside Hermes's own intake workflow.

## Delivered and verified

- Personal Android release 0.6.0, version code 8, package `org.tomstout.hermesvoice`, non-debuggable. Installed in place on the Pixel 9 Pro XL using the retained signing identity; application data and pairing preserved.
- Firmware 0.5.0 installed on the personal XIAO nRF54LM20A Sense. Existing pairing retained; no mass erase, bond reset, or recording-flash erase. All 15 slots were free before programming.
- Server worker/storage/wakeup modules deployed with English `base.en` transcription and six CPU threads. Configuration, source and SQLite backup retained at `~/.local/share/hermes-voice/backups/20260922T115221Z-speed`. Both services active after restart. The old transcription model remains available.
- Tom confirmed: **the second press saved the recording and Telegram received it**. The server recorded the manual-stop flag and a `done` result. The phone remained connected in notification mode with zero recorder queue, 15 free slots, no pending phone audio, and zero microphone/audio/flash/button error counters.

## Measurements

| Stage | Latest live recording |
|---|---:|
| Bluetooth transfer, 23,680 bytes | 1.645 seconds |
| Server queue wait | 0.298 seconds |
| Transcription | 5.098 seconds |
| First compatibility check and launcher setup | 2.944 seconds |
| Hermes agent and Telegram launcher | 41.994 seconds |
| Server receipt to done | 51.148 seconds |

The server's `total` timing was 51.008 seconds, sampled just before the final database update. Timing boundaries and scheduling account for the difference from receipt-to-done above. Bluetooth is separate from the server total.

The live Whisper log reported 3.570 seconds loading the model and 4.858 seconds total CLI time. Five isolated short-recording comparisons took 1.4–1.7 seconds each, with the same normalized words as their previous transcripts. One earlier baseline used 10.575 seconds for transcription. These are limited comparisons, not an accuracy guarantee. No baseline Bluetooth duration was available. An earlier different live message took 39.14 seconds overall, including 25.45 seconds in Hermes. **An overall latency reduction is not established by these two different messages.** The largest measured delay is now in Hermes, and its detailed workflow optimization is handed off through the prompt.

## Build and test evidence

- Pinned Seeed firmware build passed: 201,532-byte image footprint, 81,024 bytes RAM reported. All 201,520 bytes explicitly present in Intel HEX matched direct readback; twelve unused alignment bytes were excluded.
- The first application reload had correct readback but failed to start. Reprogramming the same image with the CPU halted, a post-write wait, and halted read verification restored operation. The underlying transient cause is not established. Final boot, BLE connection and the real button-to-Telegram test passed. No verification algorithm ran against a running CPU's RAM.
- Android release build and lint passed: zero errors, 13 warnings. The separate CI/debug APK and instrumentation APK built successfully.
- 24 Android device tests passed using separate CI packages; both test packages were removed afterward. The personal relay was returned to the foreground.
- Java protocol checks and 25 relay simulations passed, including full-length burst transfers, callbacks arriving in either order, stale tokens, duplicate packets, offset gaps, durable-commit failure, legacy recovery and lost acknowledgements. Recorder feature compatibility checks passed.
- 63 server worker, HTTP/storage, review/migration and button checks passed against synthetic data in an isolated Linux directory. The 18 manifest, packaging and prebuilt-flash checks also passed. Final results are recorded in local build logs.

All CRC, authentication, durable commit-before-acknowledgement, UUID deduplication and uncertain-handoff safeguards remain. The server timing migration is additive. Existing failed/uncertain messages were not replayed.

## Artifact identity

Personal APK SHA-256: `255a0a0b099c4be9cad0d707b2711bd5030ea7afc6dc5af31be9e84cd0cda874`.

Signing certificate SHA-256: `c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2`.

Private firmware HEX SHA-256: `662eac82652e775b78986e77556635d98387078baf33b2835ef2a2f4d6d382fa`.

Firmware/pairing material and the signed APK remain in the private delivery kit outside the source repository. Private inputs and diagnostic logs are ignored by Git.

## Remaining acceptance work

Long-duration screen-lock/Doze, radio-range and interference, repeated real disconnect/resume, queue saturation and a full battery-only/charge cycle still need physical acceptance testing. This release preserves the configurable two-second automatic silence save; the second press avoids that wait. Whisper model loading and Hermes latency remain variable. OpenVINO was not added because the existing runtime and smaller model provided the measured transcription improvement without another inference runtime.
